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

import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.Video;
import org.opencastproject.google.GoogleServicesFactory;

import java.io.IOException;
import java.util.List;

/**
 * Provides convenient access to {@link com.google.api.services.youtube.YouTube} service.
 *
 * @author John Crossman
 */
public interface YouTubeAPIVersion3Service {

  /**
   * Configure the underlying {@link com.google.api.services.youtube.YouTube} instance.
   *
   * @param googleServicesFactory
   *          may not be {@code null}
   * @throws IOException
   *           when configuration files not found.
   */
  void initialize(GoogleServicesFactory googleServicesFactory) throws IOException;

  /**
   * Get video by id.
   *
   * @param videoId
   *          may not be {@code null}
   * @return null when not found.
   * @throws IOException
   */
  Video getVideoById(String videoId) throws IOException;

  /**
   * All playlists owned by configured account.
   *
   * @return will not be {@code null}
   * @throws IOException
   *           when lookup fails.
   */
  List<Playlist> getAllPlaylists() throws IOException;

  /**
   * Get playlist by playlist id.
   *
   * @param playlistId
   *          may not be {@code null}
   * @return null when not found.
   * @throws IOException
   *           when lookup fails.
   */
  Playlist getPlaylistById(String playlistId) throws IOException;

  /**
   * Find YouTube playlist by id.
   *
   * @param playlistId
   *          may not be {@code null}
   * @return will not be {@code null}
   * @throws IOException
   *           when lookup fails.
   */
  List<PlaylistItem> getPlaylistItems(String playlistId) throws IOException;

  /**
   * Upload a video to predefined YouTube channel.
   *
   * @param videoUpload
   *          may not be {@code null}
   * @param progressListener
   *          may not be {@code null}
   * @return YouTube object with non-null id.
   * @throws IOException
   *           when transaction fails.
   */
  Video addVideoToMyChannel(VideoUpload videoUpload, UploadProgressListener progressListener) throws IOException;

  /**
   * Update title and description of an existing YouTube video.
   * @param video Null not allowed
   * @throws IOException when bad things happen
   */
  void updateVideoMetadata(VideoPublished video) throws IOException;

  /**
   * Add a previously uploaded video to specified YouTube playlist.
   *
   * @param playlistId
   *          may not be {@code null}
   * @param videoId
   *          may not be {@code null}
   * @return YouTube object which describes mapping, with non-null id.
   * @throws IOException
   */
  PlaylistItem addPlaylistItem(String playlistId, String videoId) throws IOException;

  /**
   * Creates YouTube Playlist and adds it to the authorized account.
   *
   * @param youTubePlaylist
   *          may not be {@code null}
   */
  Playlist createPlaylist(YouTubePlaylist youTubePlaylist) throws IOException;

  /**
   * Remove a previously uploaded video from YouTube.
   *
   * @param videoId
   *          may not be {@code null}
   * @param playlistIds
   *          may be {@code null}
   * @throws java.io.IOException
   *           when transaction fails.
   */
  void deleteVideo(String videoId, String... playlistIds) throws IOException;

  /**
   * Remove a previously uploaded video from YouTube.
   *
   * @param id
   *          may not be {@code null}
   * @throws java.io.IOException
   *           when transaction fails.
   */
  void deletePlaylist(final String id) throws IOException;

}
