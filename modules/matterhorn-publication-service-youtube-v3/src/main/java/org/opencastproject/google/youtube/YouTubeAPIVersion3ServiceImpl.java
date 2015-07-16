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

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeRequest;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.PlaylistItemSnippet;
import com.google.api.services.youtube.model.PlaylistListResponse;
import com.google.api.services.youtube.model.PlaylistSnippet;
import com.google.api.services.youtube.model.PlaylistStatus;
import com.google.api.services.youtube.model.ResourceId;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.opencastproject.google.GoogleServicesFactory;
import org.opencastproject.google.GoogleUtils;
import org.opencastproject.util.data.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * @author John Crossman
 */
public class YouTubeAPIVersion3ServiceImpl implements YouTubeAPIVersion3Service {

  private static final int videoTitleLimit = 99;
  private static final int playlistTitleLimit = 59;
  private static final String defaultPlaylistName = "uncategorized";

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private GoogleServicesFactory googleServicesFactory;

  @Override
  public void initialize(final GoogleServicesFactory googleServicesFactory) throws IOException {
    this.googleServicesFactory = googleServicesFactory;
  }

  @Override
  public Video addVideoToMyChannel(final VideoUpload videoUpload, final UploadProgressListener progressListener)
          throws IOException {
    final Video video = new Video();
    final VideoStatus status = new VideoStatus();
    status.setPrivacyStatus(videoUpload.getYouTubePrivacy().getPrivacyStatus());
    video.setStatus(status);
    // Metadata lives in VideoSnippet
    final VideoSnippet snippet = new VideoSnippet();
    final String videoTitle = enforceCharacterLimit(videoUpload.getTitle(), videoTitleLimit,
            DateFormatUtils.ISO_DATE_FORMAT.format(new Date()));
    snippet.setTitle(videoTitle);
    snippet.setDescription(videoUpload.getDescription());
    final String[] tags = videoUpload.getTags();
    if (ArrayUtils.isNotEmpty(tags)) {
      snippet.setTags(Collections.list(tags));
    }
    // Attach metadata to video object.
    video.setSnippet(snippet);
    final YouTube youTube = googleServicesFactory.getYouTube();
    final File videoFile = videoUpload.getVideoFile();
    final BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(videoFile));
    final InputStreamContent mediaContent = new InputStreamContent("video/*", inputStream);
    mediaContent.setLength(videoFile.length());
    //
    final YouTube.Videos.Insert videoInsert = youTube.videos().insert("snippet,statistics,status,contentDetails",
            video, mediaContent);
    final MediaHttpUploader uploader = videoInsert.getMediaHttpUploader();
    uploader.setDirectUploadEnabled(false);
    uploader.setProgressListener(progressListener);
    // What are the attributes of the MediaHttpUploader which determine how the upload will be processed?
    logger.info("MediaHttpUploader initialized with chunk-size = " + uploader.getChunkSize() + (uploader.isDirectUploadEnabled() ? " (Direct Upload Enabled)" : ""));
    return execute(videoInsert);
  }

  @Override
  public void updateVideoMetadata(final VideoPublished video) throws IOException {
    if (StringUtils.isBlank(video.getTitle())) {
      throw new IllegalArgumentException("Title of YouTube video cannot be blank.");
    }
    final String videoId = video.getVideoId();
    final Video youTubeVideo = StringUtils.isBlank(videoId) ? null : getVideoById(videoId);
    if (youTubeVideo == null) {
      logger.warn("Skipping YouTube metadata update because YouTube has no video associated with: " + video);
    } else {
      if (youTubeVideo.getSnippet().getCategoryId() == null) {
        throw new IllegalArgumentException("YouTube snippet.categoryId is null");
      }
      // Title, etc.
      final VideoSnippet snippet = youTubeVideo.getSnippet();
      final String videoTitle = enforceCharacterLimit(video.getTitle(), videoTitleLimit,
              DateFormatUtils.ISO_DATE_FORMAT.format(new Date()));
      snippet.setTitle(videoTitle);
      final String[] tags = video.getTags() == null ? ArrayUtils.EMPTY_STRING_ARRAY : video.getTags();
      snippet.setTags(Collections.list(tags));
      //
      final YouTube youTube = googleServicesFactory.getYouTube();
      final YouTube.Videos.Update update = youTube.videos().update("snippet", youTubeVideo);
      execute(update);
    }
  }

  @Override
  public Playlist createPlaylist(final YouTubePlaylist youTubePlaylist) throws IOException {
    final PlaylistSnippet playlistSnippet = new PlaylistSnippet();
    playlistSnippet.setTitle(enforceCharacterLimit(youTubePlaylist.getTitle(), playlistTitleLimit, defaultPlaylistName));
    playlistSnippet.setDescription(youTubePlaylist.getDescription());
    final String[] tags = youTubePlaylist.getTags();
    if (tags != null && tags.length > 0) {
      playlistSnippet.setTags(Collections.list(tags));
    }
    // Privacy
    final HasYouTubePrivacyStatus youTubePrivacy = youTubePlaylist.getYouTubePrivacy() == null
            ? GoogleUtils.ACCESS_RIGHTS_DEFAULT.getYouTubePrivacyStatus()
            : youTubePlaylist.getYouTubePrivacy();
    final PlaylistStatus playlistStatus = new PlaylistStatus();
    playlistStatus.setPrivacyStatus(youTubePrivacy.getPrivacyStatus());

    // Create playlist with metadata and status.
    final Playlist playlist = new Playlist();
    playlist.setSnippet(playlistSnippet);
    playlist.setStatus(playlistStatus);

    // The first argument tells the API what to return when a successful insert has been executed.
    final YouTube youTube = googleServicesFactory.getYouTube();
    final YouTube.Playlists.Insert command = youTube.playlists().insert("snippet,status", playlist);
    return execute(command);
  }

  @Override
  public PlaylistItem addPlaylistItem(final String playlistId, final String videoId) throws IOException {
    // Resource type (video,playlist,channel) needs to be set along with resource id.
    final ResourceId resourceId = new ResourceId();
    resourceId.setKind("youtube#video");
    resourceId.setVideoId(videoId);

    // Set the required snippet properties.
    final PlaylistItemSnippet playlistItemSnippet = new PlaylistItemSnippet();
    playlistItemSnippet.setTitle("First video in the test playlist");
    playlistItemSnippet.setPlaylistId(playlistId);
    playlistItemSnippet.setResourceId(resourceId);

    // Create the playlist item.
    final PlaylistItem playlistItem = new PlaylistItem();
    playlistItem.setSnippet(playlistItemSnippet);

    // The first argument tells the API what to return when a successful insert has been executed.
    final YouTube youTube = googleServicesFactory.getYouTube();
    final YouTube.PlaylistItems.Insert playlistItemsInsertCommand = youTube.playlistItems().insert(
            "snippet,contentDetails", playlistItem);
    return execute(playlistItemsInsertCommand);
  }

  @Override
  public void deletePlaylist(final String id) throws IOException {
    final YouTube youTube = googleServicesFactory.getYouTube();
    final YouTube.Playlists.Delete deletePlaylist = youTube.playlists().delete(id);
    execute(deletePlaylist);
  }

  @Override
  public void deleteVideo(final String videoId, final String... playlistIds) throws IOException {
    final YouTube youTube = googleServicesFactory.getYouTube();
    if (playlistIds != null) {
      for (final String playlistId : playlistIds) {
        if (StringUtils.isNotBlank(playlistId)) {
          final List<PlaylistItem> list = getPlaylistItemListByVideoId(videoId, playlistId);
          if (list != null) {
            for (final PlaylistItem item : list) {
              execute(youTube.playlistItems().delete(item.getId()));
            }
          }
        }
      }
    }
    final Video videoById = getVideoById(videoId);
    if (videoById == null) {
      logger.warn("According to YouTube, there is no video associated with id = " + videoId);
    } else {
      final YouTube.Videos.Delete deleteVideo = youTube.videos().delete(videoId);
      execute(deleteVideo);
    }
  }

  @Override
  public List<Playlist> getAllPlaylists() throws IOException {
    final YouTube youTube = googleServicesFactory.getYouTube();
    final List<Playlist> playlists = new LinkedList<>();
    String nextPageToken = null;
    do {
      final YouTube.Playlists.List search = youTube
              .playlists()
              .list("contentDetails,snippet")
              .setMine(true);
      search.setPageToken(nextPageToken);
      final PlaylistListResponse response = execute(search);
      playlists.addAll(response.getItems());
      nextPageToken = response.getNextPageToken();

    } while (nextPageToken != null);
    return playlists;
  }

  @Override
  public Video getVideoById(final String videoId) throws IOException {
    final YouTube youTube = googleServicesFactory.getYouTube();
    final YouTube.Videos.List search = youTube.videos().list("id,snippet");
    search.setId(videoId);
    search.setFields("items(id,kind,snippet)");
    final VideoListResponse response = execute(search);
    final List<Video> items = response == null ? null : response.getItems();
    final Video video = items == null || items.isEmpty() ? null : items.get(0);
    if (video == null) {
      logger.warn("YouTube return null video when queried with videoId = " + videoId);
    }
    return video;
  }

  @Override
  public Playlist getPlaylistById(final String playlistId) throws IOException {
    final YouTube youTube = googleServicesFactory.getYouTube();
    final YouTube.Playlists.List search = youTube.playlists().list("id,snippet,status,contentDetails")
            .setId(playlistId)
            .setMaxResults((long) 50)
            .setFields("items(id,kind,snippet,status),nextPageToken,pageInfo,prevPageToken,tokenPagination");
    final PlaylistListResponse playlistItems = execute(search);
    final List<Playlist> list = playlistItems.getItems();
    if (list != null && list.size() > 1) {
      throw new IllegalStateException("Multiple playlists with the same id: " + playlistId);
    }
    return list == null || list.isEmpty() ? null : list.get(0);
  }

  @Override
  public List<PlaylistItem> getPlaylistItems(final String playlistId) throws IOException {
    final YouTube youTube = googleServicesFactory.getYouTube();
    final YouTube.PlaylistItems.List search = youTube.playlistItems().list("snippet,status,contentDetails")
            .setPlaylistId(playlistId)
            .setMaxResults((long) 50)
            .setFields("items(id,kind,snippet,contentDetails/videoId),nextPageToken,tokenPagination");
    final PlaylistItemListResponse playlistItems = execute(search);
    final List<PlaylistItem> list = playlistItems.getItems();
    String nextPageToken = playlistItems.getNextPageToken();
    int loopsRemaining = 10;
    while (nextPageToken != null && loopsRemaining-- > 0) {
      final PlaylistItemListResponse response = execute(search.setPageToken(nextPageToken));
      list.addAll(response.getItems());
    }
    return list;
  }

  private List<PlaylistItem> getPlaylistItemListByVideoId(final String videoId, final String playlistId) throws IOException {
    final YouTube youTube = googleServicesFactory.getYouTube();
    final YouTube.PlaylistItems.List playlistItem = youTube.playlistItems().list(
            "id,contentDetails,snippet").setPlaylistId(playlistId).setVideoId(videoId);
    final PlaylistItemListResponse response = execute(playlistItem);
    return response.getItems();
  }

  /**
   * @see com.google.api.services.youtube.YouTubeRequest#execute()
   * @param command
   *          may not be {@code null}
   * @param <T>
   *          type of request.
   * @return result; may be {@code null}
   * @throws IOException
   *           when transaction fails.
   */
  private <T> T execute(final YouTubeRequest<T> command) throws IOException {
    return command.execute();
  }

  private String enforceCharacterLimit(final String phrase, final int limit, final String fallbackPhrase) {
    final String playlistTitle = StringUtils.isBlank(phrase) ? fallbackPhrase : phrase;
    return StringUtils.left(playlistTitle, limit);
  }

}
