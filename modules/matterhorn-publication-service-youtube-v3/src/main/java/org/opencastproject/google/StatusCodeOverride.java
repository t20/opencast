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
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * @author John Crossman
 */
public class StatusCodeOverride implements HasStatusCodes {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  protected final Properties properties;

  public StatusCodeOverride(final Properties properties) {
    this.properties = properties;
  }

  @Override
  public Integer getJsonCode() {
    return getCode(ForceFailureType.forceGoogleErrorJSON);
  }

  @Override
  public Integer getResponseStatus() {
    return getCode(ForceFailureType.forceFailureYouTubeAPI);
  }

  protected final Integer getCode(final ForceFailureType type) {
    final String key = type.getKey();
    final String value = StringUtils.trimToNull(properties.getProperty(key));
    final Integer code;
    if (value == null) {
      code = null;
    } else {
      code = NumberUtils.toInt(value, -1);
      final int min = HttpStatusCodes.STATUS_CODE_MULTIPLE_CHOICES;
      if (code < min) {
        final String message = type.name()
                .concat(" has illegal value = ")
                .concat(value)
                .concat(". The property must be numeric and greater than  ")
                .concat(Integer.toString(min));
        throw new UnsupportedOperationException(message);
      } else {
        logger.warn("Force failure in YouTube API: " + key + '=' + value);
      }
    }
    return code;
  }

}
