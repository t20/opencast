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
package org.opencastproject.google.youtube;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * All playlists have same seriesId but that does not impact equals test.
 * @author John Crossman
 */
public class YouTubePlaylistTest {

  private final String seriesId = "2014D9876";

  private final YouTubePlaylist withId = new YouTubePlaylist("1", "title 1", null,
          YouTubePrivacyStatus.publicStatus, seriesId, new String[0]);

  private final YouTubePlaylist sameId = new YouTubePlaylist(withId.getYouTubePlaylistId(), "diff title",
          "diff description", YouTubePrivacyStatus.publicStatus, seriesId, new String[0]);

  private final YouTubePlaylist withoutId = new YouTubePlaylist(null, "title 2", null,
          YouTubePrivacyStatus.unlistedStatus, seriesId, new String[] { "foo" });

  @Test
  public void testEquals() {
    assertTrue(withId.equals(sameId));
  }

  @Test
  public void testNotEquals() {
    assertFalse(withId.equals(withoutId));
  }

  @Test
  public void testMapContains() {
    final Map<YouTubePlaylist, String> map = new HashMap<YouTubePlaylist, String>();
    map.put(withoutId, "Foo");
    map.put(sameId, "Bar");
    assertTrue(map.containsKey(withId));
  }

}
