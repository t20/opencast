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

/**
 * @author John Crossman
 */
public class VideoMetadata {

  private final String title;
  private final String description;
  private final HasYouTubePrivacyStatus youTubePrivacy;
  private final String[] tags;

  /**
   * @param title may not be {@code null}.
   * @param description may be {@code null}.
   * @param youTubePrivacy may not be {@code null}.
   * @param tags may be {@code null}.
   */
  public VideoMetadata(final String title, final String description, final HasYouTubePrivacyStatus youTubePrivacy, final String... tags) {
    this.title = title;
    this.description = description;
    this.youTubePrivacy = youTubePrivacy;
    this.tags = tags;
  }

  /**
   * The video's title.
   * The value will not be {@code null}.
   */
  public String getTitle() {
    return title;
  }

  /**
   * The video's description.
   * The value may be {@code null}.
   */
  public String getDescription() {
    return description;
  }

  /**
   * @see com.google.api.services.youtube.model.VideoStatus#setPrivacyStatus(String)
   * @return will not be {@code null}
   */
  public HasYouTubePrivacyStatus getYouTubePrivacy() {
    return youTubePrivacy;
  }

  /**
   * @see com.google.api.services.youtube.model.VideoSnippet#getTags()
   * @return may be {@code null}
   */
  public String[] getTags() {
    return tags;
  }

}
