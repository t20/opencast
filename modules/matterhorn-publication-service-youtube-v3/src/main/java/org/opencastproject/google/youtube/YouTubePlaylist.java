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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;

/**
 * @author John Crossman
 */
public class YouTubePlaylist {

  private final String youTubePlaylistId;
  private String title;
  private final String description;
  private final HasYouTubePrivacyStatus youTubePrivacy;
  private final String seriesId;
  private final String[] tags;

  public YouTubePlaylist(final String youTubePlaylistId, final String seriesTitle, final String description,
          final HasYouTubePrivacyStatus youTubePrivacy, final String seriesId, final String[] tags) {
    this.youTubePlaylistId = youTubePlaylistId;
    this.title = seriesTitle;
    this.description = description;
    this.youTubePrivacy = youTubePrivacy;
    this.seriesId = seriesId;
    this.tags = tags;
  }

  public String getYouTubePlaylistId() {
    return youTubePlaylistId;
  }

  public String getSeriesId() {
    return seriesId;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public HasYouTubePrivacyStatus getYouTubePrivacy() {
    return youTubePrivacy;
  }

  public String[] getTags() {
    return tags;
  }

  public void setTitle(final String title) {
    this.title = title;
  }

  @Override
  public int hashCode() {
    return youTubePlaylistId == null ? seriesId.hashCode() : youTubePlaylistId.hashCode();
  }

  @Override
  public boolean equals(final Object o) {
    final boolean equals;
    if (o instanceof YouTubePlaylist) {
      final YouTubePlaylist that = (YouTubePlaylist) o;
      final String thatId = that.getYouTubePlaylistId();
      equals = youTubePlaylistId != null && StringUtils.equals(youTubePlaylistId, thatId);
    } else {
      equals = false;
    }
    return equals;
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this);
  }
}
