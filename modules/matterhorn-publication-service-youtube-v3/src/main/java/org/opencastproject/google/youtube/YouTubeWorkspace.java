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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.opencastproject.google.GoogleUtils;
import org.opencastproject.mediapackage.Catalog;
import org.opencastproject.mediapackage.EName;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mediapackage.MediaPackageElements;
import org.opencastproject.mediapackage.Publication;
import org.opencastproject.metadata.dublincore.DublinCore;
import org.opencastproject.metadata.dublincore.DublinCoreCatalog;
import org.opencastproject.metadata.dublincore.DublinCores;
import org.opencastproject.workspace.api.Workspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * @author John Crossman
 */
public class YouTubeWorkspace {

  /** logger instance */
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private static String[] youTubeVideoTags = new String[] {"uc", "berkeley", "ucberkeley", "webcast.berkeley", "cal"};

  private final Workspace workspace;

  public YouTubeWorkspace(final Workspace workspace) {
    this.workspace = workspace;
  }

  public VideoMetadata getVideoMetadata(final MediaPackage mediaPackage, final String[] tags) {
    final DublinCoreCatalog dcSeries = getCatalog(mediaPackage, MediaPackageElements.SERIES);
    final DublinCoreCatalog dcEpisode = getCatalog(mediaPackage, MediaPackageElements.EPISODE);
    final String episodeName = getEpisodeName(dcEpisode);
    final HasYouTubePrivacyStatus privacyStatus = getPrivacyStatus(dcSeries);
    return new VideoMetadata(episodeName, getEpisodeDescription(dcEpisode, dcSeries), privacyStatus, tags);
  }

  public String[] getYouTubeVideoTags() {
    return youTubeVideoTags;
  }

  public void setYouTubeVideoTags(final String[] youTubeVideoTags) {
    YouTubeWorkspace.youTubeVideoTags = youTubeVideoTags;
  }

  /**
   * Parse DublinCore metadata from the workspace
   *
   * @param catalog
   *          Mediapackage catalog
   * @return Catalog parse from XML
   */
  private DublinCoreCatalog parseDublinCoreCatalog(final Catalog catalog) {
    InputStream is = null;
    try {
      File dcFile = workspace.get(catalog.getURI());
      is = new FileInputStream(dcFile);
      return DublinCores.read(is);
    } catch (Exception e) {
      logger.error("Error loading Dublin Core metadata: {}", e.getMessage());
    } finally {
      IOUtils.closeQuietly(is);
    }
    return null;
  }

  /**
   * Gets the description for the episode of the media package
   *
   * @return the description of the episode
   */
  private String getEpisodeDescription(final DublinCoreCatalog dcEpisode, final DublinCoreCatalog dcSeries) {
    if (dcEpisode == null) {
      return null;
    }
    final StringBuilder description = new StringBuilder();
    if (dcSeries != null) {
      description.append(StringUtils.trimToEmpty(dcSeries.getFirst(DublinCore.PROPERTY_TITLE)));
      final String episodeDescription = dcSeries.getFirst(DublinCore.PROPERTY_DESCRIPTION);
      if (episodeDescription != null) {
        description.append('\n').append(episodeDescription);
      }
    }
    final String episodeLicense = dcEpisode.getFirst(DublinCore.PROPERTY_LICENSE);
    if (episodeLicense != null) {
      description.append('\n').append(episodeLicense);
    }
    return description.toString();
  }

  public VideoPublished extractVideoInformation(final MediaPackage mediaPackage, final String[] tags) {
    final DublinCoreCatalog dcSeries = getCatalog(mediaPackage, MediaPackageElements.SERIES);
    final DublinCoreCatalog dcEpisode = getCatalog(mediaPackage, MediaPackageElements.EPISODE);
    final Publication publication = getYouTubePublication(mediaPackage);
    final String videoId = getVideoId(publication);
    final String description = getEpisodeDescription(dcEpisode, dcSeries);
    final HasYouTubePrivacyStatus youTubePrivacy = getPrivacyStatus(dcSeries);
    return new VideoPublished(videoId, mediaPackage.getTitle(), description, youTubePrivacy, tags);
  }

  private DublinCoreCatalog getCatalog(final MediaPackage mediaPackage, final MediaPackageElementFlavor flavor) {
    final Catalog[] catalogs = mediaPackage.getCatalogs(flavor);
    return ArrayUtils.isEmpty(catalogs) ? null : parseDublinCoreCatalog(catalogs[0]);
  }

  public String getVideoId(final MediaPackage mediaPackage) {
    final Publication publication = getYouTubePublication(mediaPackage);
    return getVideoId(publication);
  }

  private static String getVideoId(final Publication publication) {
    String videoId = null;
    if (publication != null) {
      final String channel = StringUtils.trimToNull(publication.getChannel());
      final String uri = publication.getURI() == null ? null : publication.getURI().toString();
      if (channel != null && uri != null && StringUtils.equalsIgnoreCase(channel, "youtube")) {
        final String separator = "=";
        videoId = uri.contains(separator) ? StringUtils.substringAfterLast(uri, separator) : uri;
      }
    }
    return StringUtils.trimToNull(videoId);
  }

  private static Publication getYouTubePublication(final MediaPackage mediaPackage) {
    Publication publication = null;
    final Publication[] publications = mediaPackage.getPublications();
    if (publications != null) {
      for (final Publication next : publications) {
        final String channel = StringUtils.trimToNull(next.getChannel());
        final String uri = next.getURI() == null ? null : next.getURI().toString();
        if (channel != null && uri != null && StringUtils.equalsIgnoreCase(channel, "youtube")) {
          publication = next;
          break;
        }
      }
    }
    return publication;
  }

  HasYouTubePrivacyStatus getPrivacyStatus(final DublinCoreCatalog dcSeries) {
    final HasYouTubePrivacyStatus youTubePrivacyStatus;
    if (dcSeries == null) {
      youTubePrivacyStatus = GoogleUtils.ACCESS_RIGHTS_DEFAULT.getYouTubePrivacyStatus();
    } else {
      final RecordingAccessRights recordingAccessRights = GoogleUtils
              .findByPropertyAccessRights(getFirst(dcSeries, DublinCore.PROPERTY_ACCESS_RIGHTS));
      youTubePrivacyStatus = recordingAccessRights.getYouTubePrivacyStatus();
    }
    return youTubePrivacyStatus;
  }

  private String getFirst(final DublinCoreCatalog catalog, final EName name) {
    return catalog == null ? null : catalog.getFirst(name);
  }

  private String getEpisodeName(final DublinCoreCatalog dcEpisode) {
    return dcEpisode == null ? null : dcEpisode.getFirst(DublinCore.PROPERTY_TITLE);
  }

}
