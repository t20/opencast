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

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonParser;
import com.google.api.client.json.JsonToken;
import com.google.api.services.youtube.model.Video;
import org.apache.commons.lang.StringUtils;
import org.joda.time.Period;
import org.joda.time.format.ISOPeriodFormat;
import org.joda.time.format.PeriodFormatter;
import org.opencastproject.google.youtube.RecordingAccessRights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

/**
 * @author John Crossman
 */
public final class GoogleUtils {

  /** Videos with no associated series will be mad private in YouTube */
  public static final RecordingAccessRights ACCESS_RIGHTS_DEFAULT = RecordingAccessRights.studentsOnlyAccessRights;
  private static final Logger logger = LoggerFactory.getLogger(GoogleUtils.class);

  private GoogleUtils() {
  }

  /**
   * See https://en.wikipedia.org/wiki/ISO_8601#Durations
   * @param video null not allowed
   * @return number of seconds calculated from {@link com.google.api.services.youtube.model.VideoContentDetails#getDuration()}
   */
  public static Integer getDurationSeconds(final Video video) {
    final int seconds;
    if (video == null || video.getContentDetails() == null) {
      seconds = 0;
    } else {
      final PeriodFormatter formatter = ISOPeriodFormat.standard();
      final Period period = formatter.parsePeriod(video.getContentDetails().getDuration());
      seconds = period.toStandardSeconds().getSeconds();
    }
    return seconds;
  }

  /**
   * Match incoming arg with {@link RecordingAccessRights#name()}
   * @param propertyAccessRights null allowed
   * @return when incoming arg is null we return {@link RecordingAccessRights#studentsOnlyAccessRights}
   */
  public static RecordingAccessRights findByPropertyAccessRights(final String propertyAccessRights) {
    return findByPropertyAccessRights(propertyAccessRights, ACCESS_RIGHTS_DEFAULT);
  }

  /**
   * Match incoming arg with {@link RecordingAccessRights#name()}
   * @param propertyAccessRights null allowed
   * @param defaultValue return this value when property is invalid
   * @return when incoming arg is null we return {@link RecordingAccessRights#studentsOnlyAccessRights}
   */
  public static RecordingAccessRights findByPropertyAccessRights(final String propertyAccessRights,
          final RecordingAccessRights defaultValue) {
    RecordingAccessRights accessRights = defaultValue;
    for (final RecordingAccessRights next : RecordingAccessRights.values()) {
      if (StringUtils.equalsIgnoreCase(next.name(), StringUtils.trimToNull(propertyAccessRights))) {
        accessRights = next;
        break;
      }
    }
    return accessRights;
  }

  public static StagedBackOff getStagedBackOff(final Hashtable properties) {
    final String retryPolicy = get(properties, GoogleKey.retryPolicy, false);
    return getStagedBackOff(retryPolicy);
  }

  public static StagedBackOff getStagedBackOff(final String retryPolicy) {
    final List<StagedBackOffPolicy> policyList = new ArrayList<>();
    if (!StringUtils.isEmpty(retryPolicy)) {
      String[] policies = retryPolicy.split(",");
      for (String policy : policies) {
        final Logger logger = LoggerFactory.getLogger(GoogleServicesFactory.class);
        try {
          final String[] params = policy.split(":");
          if (params.length == 4) {
            final String description = params[0];
            final long retryStageDelay = Long.parseLong(params[1]);
            final long retryWaitTime = Long.parseLong(params[2]);
            final int retryRepetitions = Integer.parseInt(params[3]);
            policyList.add(new StagedBackOffPolicy(description, retryStageDelay, retryWaitTime, retryRepetitions));
          } else {
            logger.warn("Missing parameters, stage  " + policy + " will be ignored");
          }
        } catch (NumberFormatException e) {
          logger.warn("Invalid number, stage " + policy + " will be ignored");
        }
      }
    }
    return new StagedBackOff(policyList);
  }

  public static String get(final Dictionary properties, final ConfigKey configKey, final boolean required) {
    final Object value = properties.get(configKey.getKey());
    return value == null ? null : trimAndVerify(value instanceof String ? (String) value : value.toString(), required);
  }

  /**
   * Disciplined way of getting required properties.
   *
   * @param configKey may not be {@code null}
   * @param required when true, and property result is null, we throw {@link java.lang.IllegalArgumentException}
   * @return associated value or null
   */
  public static String get(final Properties properties, final ConfigKey configKey, final boolean required) {
    return trimAndVerify(properties.getProperty(configKey.getKey()), required);
  }

  private static String trimAndVerify(final String value, boolean required) {
    final String trimmed = StringUtils.trimToNull(value);
    if (required && trimmed == null) {
      throw new IllegalArgumentException("Null or blank value for YouTube-related property");
    }
    return trimmed;
  }

  /**
   * @param jsonFactory never null
   * @param stream never null
   * @return code property in JSON
   * @throws IOException when bad things happen.
   */
  static Integer extractGoogleJsonCode(final JsonFactory jsonFactory, final InputStream stream) throws IOException {
    Integer jsonCode = null;
    JsonParser jsonParser = null;
    try {
      if (stream != null) {
        jsonParser = jsonFactory.createJsonParser(stream);
        jsonParser.skipToKey("code");
        if (jsonParser.getCurrentToken() != JsonToken.END_OBJECT) {
          jsonCode = jsonParser.parseAndClose(Integer.class);
        }
      }
    } catch (final IllegalArgumentException e) {
      logger.warn("Failed to extract 'code' from Google JSON response: " + e.getMessage());
    } finally {
      if (jsonParser != null) {
        jsonParser.close();
      }
    }
    return jsonCode;
  }

  /**
   * @param dictionary null not allowed
   * @return Same key/value pairs in a different type of object.
   */
  public static Properties toProperties(final Dictionary dictionary) {
    final Properties p = new Properties();
    final Enumeration keys = dictionary.keys();
    while (keys.hasMoreElements()) {
      final Object keyObject = keys.nextElement();
      p.setProperty(keyObject.toString(), dictionary.get(keyObject).toString());
    }
    return p;
  }

}
