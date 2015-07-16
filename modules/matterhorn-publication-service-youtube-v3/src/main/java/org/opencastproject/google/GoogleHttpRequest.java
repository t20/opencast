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

import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.util.Preconditions;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;

/**
 * @see GoogleServicesFactory
 * @author John Crossman
 */
public class GoogleHttpRequest extends LowLevelHttpRequest {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final HttpURLConnection connection;

  private final HasStatusCodes forceStatusCodes;
  /**
   * @param connection HTTP URL connection
   */
  GoogleHttpRequest(final HttpURLConnection connection, final HasStatusCodes forceStatusCodes) {
    this.connection = connection;
    this.forceStatusCodes = forceStatusCodes;
    connection.setInstanceFollowRedirects(false);
  }

  @Override
  public void addHeader(final String name, final String value) {
    connection.addRequestProperty(name, value);
  }

  @Override
  public void setTimeout(final int connectTimeout, final int readTimeout) {
    connection.setReadTimeout(readTimeout);
    connection.setConnectTimeout(connectTimeout);
  }

  @Override
  public LowLevelHttpResponse execute() throws IOException {
    final LowLevelHttpResponse forceFailureResponse = getForceFailureResponse();
    if (forceFailureResponse != null) {
      return forceFailureResponse;
    }
    final HttpURLConnection connection = this.connection;
    // write content
    if (getStreamingContent() != null) {
      String contentType = getContentType();
      if (contentType != null) {
        addHeader("Content-Type", contentType);
      }
      String contentEncoding = getContentEncoding();
      if (contentEncoding != null) {
        addHeader("Content-Encoding", contentEncoding);
      }
      long contentLength = getContentLength();
      if (contentLength >= 0) {
        addHeader("Content-Length", Long.toString(contentLength));
      }
      String requestMethod = connection.getRequestMethod();
      if ("POST".equals(requestMethod) || "PUT".equals(requestMethod)) {
        connection.setDoOutput(true);
        // see http://developer.android.com/reference/java/net/HttpURLConnection.html
        if (contentLength >= 0 && contentLength <= Integer.MAX_VALUE) {
          connection.setFixedLengthStreamingMode((int) contentLength);
        } else {
          connection.setChunkedStreamingMode(0);
        }
        OutputStream out = connection.getOutputStream();
        try {
          getStreamingContent().writeTo(out);
        } finally {
          out.close();
        }
      } else {
        // cannot call setDoOutput(true) because it would change a GET method to POST
        // for HEAD, OPTIONS, DELETE, or TRACE it would throw an exceptions
        Preconditions.checkArgument(
                contentLength == 0, "%s with non-zero content length is not supported", requestMethod);
      }
    }
    // connect
    boolean successfulConnection = false;
    try {
      connection.connect();
      GoogleHttpResponse response = new GoogleHttpResponse(connection);
      final String message = "Google response status=" + response.getStatusCode() + " where URL=" + connection.getURL();
      logger.warn(message);
      successfulConnection = true;
      return response;
    } finally {
      if (!successfulConnection) {
        connection.disconnect();
      }
    }
  }

  private LowLevelHttpResponse getForceFailureResponse() {
    LowLevelHttpResponse response = null;
    Integer statusCode = forceStatusCodes.getResponseStatus();
    Integer jsonCode = forceStatusCodes.getJsonCode();
    if (statusCode != null || jsonCode != null) {
      statusCode = statusCode == null ? HttpStatus.SC_OK : statusCode;
      jsonCode = jsonCode == null ? HttpStatus.SC_OK : jsonCode;
      final byte[] content = getMockGoogleJsonContent(jsonCode);
      response = new ForceFailureLowLevelHttpResponse(statusCode, content);
    }
    return response;
  }

  private byte[] getMockGoogleJsonContent(final int jsonCode) {
    final String message = "Mock Google JSON content";
    final StringBuilder b = new StringBuilder("\n\n{ \"code\": ").append(jsonCode);
    if (!HttpStatusCodes.isSuccess(jsonCode)) {
      b.append(", \"errors\": [ { \"domain\": \"global\", \"message\": \"");
    }
    return b.append(message)
            .append("\", \"reason\": \"")
            .append(ForceFailureType.forceGoogleErrorJSON)
            .append("\"  } ], \"message\": \"")
            .append(message)
            .append("\" }\n\n").toString().getBytes();
  }

}
