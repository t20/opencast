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

import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.util.Preconditions;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;

/**
 * @see GoogleServicesFactory
 *
 * @author John Crossman
 */
public class GoogleHttpTransport extends HttpTransport {

  /**
   * All valid request methods as specified in {@link HttpURLConnection#setRequestMethod}, sorted in
   * ascending alphabetical order.
   */
  private static final String[] SUPPORTED_METHODS = {HttpMethods.DELETE,
          HttpMethods.GET,
          HttpMethods.HEAD,
          HttpMethods.OPTIONS,
          HttpMethods.POST,
          HttpMethods.PUT,
          HttpMethods.TRACE};
  static {
    Arrays.sort(SUPPORTED_METHODS);
  }

  private final HasStatusCodes forceStatusCodes;

  public GoogleHttpTransport(final HasStatusCodes forceStatusCodes) {
    this.forceStatusCodes = forceStatusCodes;
  }

  @Override
  public boolean supportsMethod(final String method) {
    return Arrays.binarySearch(SUPPORTED_METHODS, method) >= 0;
  }

  @Override
  protected GoogleHttpRequest buildRequest(final String method, final String url) throws IOException {
    Preconditions.checkArgument(supportsMethod(method), "HTTP method %s not supported", method);
    URL connUrl = new URL(url);
    URLConnection conn = connUrl.openConnection();
    HttpURLConnection connection = (HttpURLConnection) conn;
    connection.setRequestMethod(method);
    return new GoogleHttpRequest(connection, forceStatusCodes);
  }

}
