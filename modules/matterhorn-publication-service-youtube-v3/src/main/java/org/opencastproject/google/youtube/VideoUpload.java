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

import java.io.File;

/**
 * Represents a YouTube video.
 *
 * @see com.google.api.services.youtube.model.Video
 * @author John Crossman
 */
public class VideoUpload extends VideoMetadata {

  private final File videoFile;

  /**
   * @param title may not be {@code null}.
   * @param description may be {@code null}.
   * @param youTubePrivacy may not be {@code null}.
   * @param videoFile may not be {@code null}.
   * @param tags may be {@code null}.
   */
  public VideoUpload(final String title, final String description, final HasYouTubePrivacyStatus youTubePrivacy, final File videoFile,
          final String[] tags) {
    super(title, description, youTubePrivacy, tags);
    this.videoFile = videoFile;
  }

  public VideoUpload(final VideoMetadata m, final File file) {
    this(m.getTitle(), m.getDescription(), m.getYouTubePrivacy(), file, m.getTags());
  }

  /**
   * @see com.google.api.services.youtube.model.Video
   * @return will not be {@code null}
   */
  public File getVideoFile() {
    return videoFile;
  }

}
