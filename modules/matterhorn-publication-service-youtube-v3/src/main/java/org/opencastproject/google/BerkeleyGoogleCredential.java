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
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.util.BackOff;
import com.google.api.client.util.BackOffUtils;
import com.google.api.client.util.Sleeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author John Crossman
 */
public class BerkeleyGoogleCredential extends GoogleCredential {

  /** logger instance */
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  /** Back-off policy. */
  private final BackOff backOff;

  /** Defines if back-off is required based on an abnormal HTTP response. */
  private final HttpBackOffUnsuccessfulResponseHandler.BackOffRequired backOffRequired;

  /** Sleeper. */
  private Sleeper sleeper = Sleeper.DEFAULT;

  public BerkeleyGoogleCredential(final GoogleCredential.Builder builder, final BackOff backOff) {
    super(builder);
    this.backOff = backOff;
    this.backOffRequired = new BackOffRequiredImpl(builder.getJsonFactory());
  }

  @Override
  public boolean handleResponse(final HttpRequest request, final HttpResponse response, final boolean supportsRetry) {
    boolean handled = super.handleResponse(request, response, supportsRetry);
    if (!handled && supportsRetry && backOffRequired.isRequired(response)) {
      try {
        handled = BackOffUtils.next(sleeper, backOff);
      } catch (final IOException e) {
        logger.warn("Error when invoking " + backOff, e);
      } catch (final InterruptedException e) {
        logger.warn("Error when invoking " + backOff, e);
      }
    }
    return handled;
  }

  BackOff getBackOff() {
    return backOff;
  }

}
