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

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpBackOffIOExceptionHandler;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import org.json.simple.parser.ParseException;
import org.opencastproject.google.auth.ClientCredentials;
import org.opencastproject.google.auth.OAuth2CredentialFactory;
import org.opencastproject.google.auth.OAuth2CredentialFactoryImpl;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

/**
 * @author John Crossman
 */
public class GoogleHttpRequestInitializer implements HttpRequestInitializer {

  // Note the following three params are purposely NOT configurable from the properties file in the belief that doing
  // so may inadvertently cause problems that would be worse than the problem being addressed.

  // Timeout in milliseconds to establish a connection; the default was 20 seconds, set to 0 for an infinite timeout
  private static final int CONNECT_TIMEOUT = 60000; // One minute

  // Timeout in milliseconds to read data from an established connection; the default was 20 seconds, set to 0 for an infinite timeout
  private static final int READ_TIMEOUT = 60000; // One minute

  // Number of retries that will be allowed to execute before the request will be terminated; the default was 10
  private static final int NUMBER_OF_RETRIES = 100;

  private final ClientCredentials clientCredentials;
  private final StagedBackOff stagedBackOff;

  public GoogleHttpRequestInitializer(final Properties properties) throws IOException {
    final String dataStore = GoogleUtils.get(properties, GoogleKey.credentialDatastore, true);
    clientCredentials = new ClientCredentials();
    clientCredentials.setCredentialDatastore(dataStore);
    final String path = GoogleUtils.get(properties, GoogleKey.clientSecretsV3, true);
    try {
      clientCredentials.setClientSecrets(new File(path));
    } catch (final ParseException e) {
      throw new IOException(e);
    }
    clientCredentials.setDataStoreDirectory(GoogleUtils.get(properties, GoogleKey.dataStore, true));
    stagedBackOff = GoogleUtils.getStagedBackOff(properties);
  }

  @Override
  public void initialize(final HttpRequest request) throws IOException {
    // Custom handler on IOException
    stagedBackOff.reset();
    final OAuth2CredentialFactory factory = new OAuth2CredentialFactoryImpl();
    final Credential credential = factory.getGoogleCredential(clientCredentials, stagedBackOff);
    initializeRequest(request, credential, stagedBackOff);
  }

  static void initializeRequest(final HttpRequest request, final Credential credential,
          final StagedBackOff stagedBackOff) {
    request.setInterceptor(credential);
    request.setUnsuccessfulResponseHandler(credential);
    request.setIOExceptionHandler(new HttpBackOffIOExceptionHandler(stagedBackOff));
    request.setConnectTimeout(CONNECT_TIMEOUT);
    request.setReadTimeout(READ_TIMEOUT);
    request.setNumberOfRetries(NUMBER_OF_RETRIES);
  }

}
