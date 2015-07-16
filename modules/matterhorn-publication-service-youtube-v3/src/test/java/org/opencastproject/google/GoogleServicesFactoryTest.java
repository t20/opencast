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

import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author John Crossman
 */
public class GoogleServicesFactoryTest {

  @Test(expected = IllegalArgumentException.class)
  public void testGetWhenRequired() {
    GoogleUtils.get(new Properties(), GoogleKey.clientSecretsV3, true);
  }

  @Test
  public void testNull() {
    assertNull(GoogleUtils.get(new Properties(), GoogleKey.clientSecretsV3, false));
  }

  @Test
  public void testGet() {
    final String value = "value";
    final String keyString = "org.opencastproject.publication.youtube." + GoogleKey.clientSecretsV3.name();
    final Properties properties = new Properties();
    properties.put(keyString, value);
    assertEquals(value, GoogleUtils.get(properties, GoogleKey.clientSecretsV3, true));
    assertEquals(value, GoogleUtils.get(properties, GoogleKey.clientSecretsV3, false));
    assertEquals(value, GoogleUtils.get(properties, GoogleKey.clientSecretsV3, false));
  }

}
