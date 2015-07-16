/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package org.opencastproject.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.testing.http.HttpTesting;
import com.google.api.client.testing.http.javanet.MockHttpURLConnection;
import com.google.api.client.util.BackOff;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author John Crossman
 */
public class GoogleHttpRequestTest {

  private final MockHttpURLConnection urlConnection = new MockHttpURLConnection(new URL("http://api.google.com"));
  private final GoogleHttpRequest normalRequest;
  private final GoogleHttpRequest forceFailureRequest;
  private final StagedBackOff backOff;


  @Before
  public void before() throws IOException {
    backOff.reset();
  }

  public GoogleHttpRequestTest() throws MalformedURLException {
    final MockCodes forceFailureSettings = new MockCodes(503, 503);
    final MockCodes goodSettings = new MockCodes(null, null);
    normalRequest = new GoogleHttpRequest(urlConnection, goodSettings);
    forceFailureRequest = new GoogleHttpRequest(urlConnection, forceFailureSettings);
    //
    final Properties properties = new Properties();
    properties.put(GoogleKey.retryPolicy.getKey(), "First failure:0:0:1");
    backOff = GoogleUtils.getStagedBackOff(properties);
  }

  @Test
  public void testNormalRequest() throws IOException {
    final LowLevelHttpResponse execute = normalRequest.execute();
    assertFalse(execute instanceof ForceFailureLowLevelHttpResponse);
  }

  @Test
  public void testForceFailureRequest() throws IOException {
    assertTrue(forceFailureRequest.execute() instanceof ForceFailureLowLevelHttpResponse);
    assertTrue(forceFailureRequest.execute() instanceof ForceFailureLowLevelHttpResponse);
  }

  @Test
  public void extractCodeWithMockJsonParser() throws IOException {
    testExtractCode(410, 503);
  }

  @Test
  public void extractCodeWithJacksonParser() throws IOException {
    testExtractCode(200, 204);
  }


  @Test
  public void testBackOffNotRequired() throws IOException {
    expectNoRetry(200, 200);
    // Precedence goes to response status code
    expectNoRetry(200, 503);
    expectNoRetry(302, 302);
    expectNoRetry(404, 404);
  }

  @Test
  public void testSuccessHttpResponseRules() throws IOException {
    expectNoRetry(200, 501);
  }

  @Test
  public void testBackOffRequired500() throws IOException {
    expectRetry(500, 500);
  }

  @Test
  public void testBackOffRequiredConflictingCodes() throws IOException {
    expectRetry(500, 200);
  }

  @Test
  public void testBackOffRequiredWhenJSONCodeIndicatesRetry() throws IOException {
    expectRetry(410, 503);
  }

  private void expectRetry(final int responseStatusCode, final Integer jsonErrorCode) throws IOException {
    assertExpectedRetry(true, responseStatusCode, jsonErrorCode);
  }

  private void expectNoRetry(final int responseStatusCode, final Integer jsonErrorCode) throws IOException {
    assertExpectedRetry(false, responseStatusCode, jsonErrorCode);
  }

  private void assertExpectedRetry(final boolean expectRetry, final int statusCode, final Integer jsonCode)
          throws IOException {
    final String message = "statusCode = " + statusCode + "; jsonCode = " + jsonCode;
    final boolean actual = retryHappened(statusCode, jsonCode);
    assertSame(message, expectRetry, actual);
  }

  private void testExtractCode(final int statusCode, final int jsonCode) throws IOException {
    final MockCodes settings = new MockCodes(statusCode, jsonCode);
    final GoogleHttpRequest request = new GoogleHttpRequest(urlConnection, settings);
    final LowLevelHttpResponse response = request.execute();
    assertEquals(statusCode, response.getStatusCode());
    //
    assertTrue(response instanceof ForceFailureLowLevelHttpResponse);
    final ForceFailureLowLevelHttpResponse forceFailResponse =
            (ForceFailureLowLevelHttpResponse) response;
    final ByteArrayInputStream stream = (ByteArrayInputStream) forceFailResponse.getContent();
    final String content = IOUtils.toString(stream);
    assertNotEquals(HttpStatusCodes.isSuccess(jsonCode), content.contains("error"));
    assertTrue(content.contains("\"code\": " + jsonCode));
  }

  private boolean retryHappened(final int statusCode, final int jsonCode) throws IOException {
    final MockCodes settings = new MockCodes(statusCode, jsonCode);
    final GoogleHttpTransport transport = new GoogleHttpTransport(settings);
    final GoogleCredential.Builder builder = new GoogleCredential.Builder()
            .setJsonFactory(new JacksonFactory())
            .setTransport(transport);
    final HttpRequest request = transport
            .createRequestFactory()
            .buildGetRequest(HttpTesting.SIMPLE_GENERIC_URL);
    final BerkeleyGoogleCredential credential = new BerkeleyGoogleCredential(builder, backOff);
    GoogleHttpRequestInitializer.initializeRequest(request, credential, backOff);
    // IMPORTANT: Use throwException = FALSE in this unit test but NOT in production.
    request.setThrowExceptionOnExecuteError(false);
    request.execute();
    final BackOff backOff = credential.getBackOff();
    assertTrue(backOff instanceof StagedBackOff);
    final StagedBackOff stagedBackOff = (StagedBackOff) backOff;
    return stagedBackOff.getStageStack().isEmpty();
  }

  private class MockCodes implements HasStatusCodes {
    private final Integer statusCode;
    private final Integer jsonCode;

    public MockCodes(final Integer statusCode, final Integer jsonCode) {
      this.statusCode = statusCode;
      this.jsonCode = jsonCode;
    }

    @Override
    public Integer getJsonCode() {
      return jsonCode;
    }

    @Override
    public Integer getResponseStatus() {
      return statusCode;
    }
  }

}
