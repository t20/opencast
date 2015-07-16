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

import com.google.api.client.http.LowLevelHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author John Crossman
 */
class ForceFailureLowLevelHttpResponse extends LowLevelHttpResponse {

  private final byte[] content;
  private final int statusCode;

  ForceFailureLowLevelHttpResponse(final int statusCode, final byte[] content) {
    this.statusCode = statusCode;
    this.content = content;
  }

  @Override
  public InputStream getContent() throws IOException {
    return new ByteArrayInputStream(content);
  }

  @Override
  public String getContentEncoding() throws IOException {
    return "UTF-8";
  }

  @Override
  public long getContentLength() throws IOException {
    return content.length;
  }

  @Override
  public String getContentType() throws IOException {
    return "application/json";
  }

  @Override
  public String getStatusLine() throws IOException {
    return ForceFailureType.forceFailureYouTubeAPI.name();
  }

  @Override
  public int getStatusCode() throws IOException {
    return statusCode;
  }

  @Override
  public String getReasonPhrase() throws IOException {
    return "Force failure in YouTube API call because properties have " + ForceFailureType.forceFailureYouTubeAPI.getKey() + "=" + statusCode;
  }

  @Override
  public int getHeaderCount() throws IOException {
    return 0;
  }

  @Override
  public String getHeaderName(final int index) throws IOException {
    return null;
  }

  @Override
  public String getHeaderValue(final int index) throws IOException {
    return null;
  }

}
