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

import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.json.JsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author John Crossman
 */
public class BackOffRequiredImpl implements HttpBackOffUnsuccessfulResponseHandler.BackOffRequired {

  private static final int STATUS_CODE_RETRY_THRESHOLD = 500;

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final JsonFactory jsonFactory;

  public BackOffRequiredImpl(final JsonFactory jsonFactory) {
    this.jsonFactory = jsonFactory;
  }

  public boolean isRequired(final HttpResponse response) {
    final int statusCode = response.getStatusCode();
    boolean required = false;
    if (HttpStatusCodes.isSuccess(statusCode)) {
      logger.debug("HTTP response status " + statusCode  + " (i.e., success) means Google JSON is not inspected");
    } else {
      required = isYouTubeRetryRequired(statusCode);
      if (!required) {
        try {
          final InputStream content = response.getContent();
          final Integer jsonErrorCode = GoogleUtils.extractGoogleJsonCode(jsonFactory, content);
          required = isYouTubeRetryRequired(jsonErrorCode);
        } catch (final IOException e) {
          logger.warn("Failed to get content from Google response object", e);
        }
      }
    }
    return required;
  }

  private boolean isYouTubeRetryRequired(final Integer code) {
    return code != null && code >= STATUS_CODE_RETRY_THRESHOLD;
  }

}
