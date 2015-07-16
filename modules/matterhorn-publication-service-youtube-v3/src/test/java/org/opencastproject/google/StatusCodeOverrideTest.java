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

import com.google.api.client.testing.http.javanet.MockHttpURLConnection;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author John Crossman
 */
public class StatusCodeOverrideTest {

  @Test
  public void testTurnConfigsOnAndOff() throws IOException {
    final MockHttpURLConnection mock = new MockHttpURLConnection(new URL("http://goo.com"));
    // Begin in normal mode
    final Properties p = getProperties(null, null);
    final GoogleHttpRequest request = new GoogleHttpRequest(mock, new MyStatusCodeOverride(p));
    assertFalse(request.execute() instanceof ForceFailureLowLevelHttpResponse);
    // Add configs and enter force-failure mode
    updateProperties(p, "410", "503");
    assertTrue(request.execute() instanceof ForceFailureLowLevelHttpResponse);
    // Remove configs and return to normal mode
    updateProperties(p, null, null);
    assertFalse(request.execute() instanceof ForceFailureLowLevelHttpResponse);
  }

  @Test
  public void testBothValid() {
    final HasStatusCodes code = new MyStatusCodeOverride(getProperties(" 410", "503"));
    assertEquals(410, code.getResponseStatus().intValue());
    assertEquals(503, code.getJsonCode().intValue());
  }

  @Test
  public void testOneValidOneNull() {
    final HasStatusCodes code = new MyStatusCodeOverride(getProperties(" 500   ", "  "));
    assertEquals(500, code.getResponseStatus().intValue());
    assertNull(code.getJsonCode());
  }

  @Test
  public void testNull() {
    final HasStatusCodes code = new MyStatusCodeOverride(getProperties(null, null));
    assertNull(code.getResponseStatus());
    assertNull(code.getJsonCode());
  }

  @Test
  public void testBlank() {
    final HasStatusCodes code = new MyStatusCodeOverride(getProperties("", "   "));
    assertNull(code.getResponseStatus());
    assertNull(code.getJsonCode());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testUnsupportedNumber() {
    new MyStatusCodeOverride(getProperties(" 200  ", null)).getResponseStatus();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testUnsupportedString() {
    new MyStatusCodeOverride(getProperties("200", null)).getResponseStatus();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testErrantSpace() {
    new MyStatusCodeOverride(getProperties("200", "50 3")).getResponseStatus();
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testNonNumeric() {
    new MyStatusCodeOverride(getProperties("200", " x503")).getResponseStatus();
  }

  private Properties getProperties(final String responseStatus, final String jsonCode) {
    return updateProperties(new Properties(), responseStatus, jsonCode);
  }
  private Properties updateProperties(final Properties p, final String responseStatus,
          final String jsonCode) {
    updateProperty(p, ForceFailureType.forceFailureYouTubeAPI, responseStatus);
    updateProperty(p, ForceFailureType.forceGoogleErrorJSON, jsonCode);
    return p;
  }

  private void updateProperty(final Properties p, final ForceFailureType type, final String value) {
    if (value == null) {
      p.remove(type.getKey());
    } else {
      p.put(type.getKey(), value);
    }
  }

  private class MyStatusCodeOverride extends StatusCodeOverride {

    public MyStatusCodeOverride(final Properties properties) {
      super(properties);
    }

  }

}
