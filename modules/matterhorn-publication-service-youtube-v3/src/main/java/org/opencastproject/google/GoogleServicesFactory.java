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

import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;

import java.io.IOException;
import java.util.Properties;

/**
 * Supports YouTube property management.
 * @author John Crossman
 */
public final class GoogleServicesFactory {

  private YouTube youTube;
  private final Properties properties;

  public GoogleServicesFactory(final Properties properties) {
    this.properties = properties;
  }

  public YouTube getYouTube() throws IOException {
    if (youTube == null) {
      final GoogleHttpRequestInitializer initializer = new GoogleHttpRequestInitializer(properties);
      final GoogleHttpTransport transport = new GoogleHttpTransport(new StatusCodeOverride(properties));
      youTube = new YouTube.Builder(transport, new JacksonFactory(), initializer)
              .setApplicationName("UC Berkeley Webcast")
              .build();
    }
    return youTube;
  }

}
