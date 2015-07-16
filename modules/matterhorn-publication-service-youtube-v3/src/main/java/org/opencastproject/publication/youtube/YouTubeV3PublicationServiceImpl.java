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
package org.opencastproject.publication.youtube;

import org.opencastproject.google.GoogleServicesFactory;
import org.opencastproject.google.GoogleUtils;
import org.opencastproject.google.youtube.RecordingAccessRights;
import org.opencastproject.google.youtube.UploadProgressListener;
import org.opencastproject.google.youtube.VideoMetadata;
import org.opencastproject.google.youtube.VideoUpload;
import org.opencastproject.google.youtube.YouTubeAPIVersion3Service;
import org.opencastproject.google.GoogleKey;
import org.opencastproject.google.youtube.YouTubeAPIVersion3ServiceImpl;
import org.opencastproject.google.youtube.YouTubePlaylist;
import org.opencastproject.google.youtube.YouTubeWorkspace;
import org.opencastproject.job.api.AbstractJobProducer;
import org.opencastproject.job.api.Job;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageElementParser;
import org.opencastproject.mediapackage.MediaPackageParser;
import org.opencastproject.mediapackage.Publication;
import org.opencastproject.mediapackage.PublicationImpl;
import org.opencastproject.mediapackage.Track;
import org.opencastproject.metadata.dublincore.DublinCore;
import org.opencastproject.metadata.dublincore.DublinCoreCatalog;
import org.opencastproject.metadata.dublincore.DublinCoreValue;
import org.opencastproject.publication.api.PublicationException;
import org.opencastproject.publication.api.YouTubePublicationService;
import org.opencastproject.security.api.OrganizationDirectoryService;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.UnauthorizedException;
import org.opencastproject.security.api.UserDirectoryService;
import org.opencastproject.series.api.SeriesException;
import org.opencastproject.series.api.SeriesService;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.util.MimeTypes;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.workspace.api.Workspace;

import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.Video;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Dictionary;
import java.util.List;
import java.util.UUID;

/**
 * Publishes media to a Youtube play list.
 */
public class YouTubeV3PublicationServiceImpl extends AbstractJobProducer implements YouTubePublicationService, ManagedService {

  /** Time to wait between polling for status (milliseconds.) */
  private static final long POLL_MILLISECONDS = 30L * 1000L;

  /** The channel name */
  private static final String CHANNEL_NAME = "youtube";

  /** logger instance */
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  /** The mime-type of the published element */
  private static final String MIME_TYPE = "text/html";

  /** List of available operations on jobs */
  private enum Operation {
    Publish, Retract
  }

  /** workspace instance */
  protected Workspace workspace = null;

  /** workspace instance */
  protected YouTubeWorkspace youTubeWorkspace = null;


  /** The remote service registry */
  protected ServiceRegistry serviceRegistry = null;

  /** The organization directory service */
  private OrganizationDirectoryService organizationDirectoryService;

  /** The user directory service */
  private UserDirectoryService userDirectoryService;

  /** The security service */
  private SecurityService securityService;

  /** Youtube configuration instance */
  private YouTubeAPIVersion3Service youTubeService;

  /** Series metadata */
  private SeriesService seriesService = null;

  /**
   * Create new instance.
   */
  public YouTubeV3PublicationServiceImpl() {
    super(JOB_TYPE);
    logger.debug("Instantiated " + this.getClass().getSimpleName());
  }

  @Override
  public void updated(final Dictionary dictionary) throws ConfigurationException {
    logger.info("Updating configuration for " + this.getClass().getSimpleName());
    youTubeWorkspace.setYouTubeVideoTags(StringUtils.split(GoogleUtils.get(dictionary, GoogleKey.keywords, true), ','));
    try {
      final GoogleServicesFactory servicesFactory = new GoogleServicesFactory(GoogleUtils.toProperties(dictionary));
      final YouTubeAPIVersion3Service service = new YouTubeAPIVersion3ServiceImpl();
      service.initialize(servicesFactory);
      setYouTubeService(service);
    } catch (final IOException e) {
      throw new ConfigurationException("Failed to init YouTube service", getJobType(), e);
    }
  }

  @Override
  public Job publish(final MediaPackage mediaPackage, final Track track) throws PublicationException {
    if (mediaPackage.contains(track)) {
      try {
        final List<String> args = Arrays.asList(MediaPackageParser.getAsXml(mediaPackage), track.getIdentifier());
        return serviceRegistry.createJob(JOB_TYPE, Operation.Publish.toString(), args);
      } catch (ServiceRegistryException e) {
        throw new PublicationException("Unable to create a job for track: " + track.toString(), e);
      }
    } else {
      throw new IllegalArgumentException("Mediapackage does not contain track " + track.getIdentifier());
    }
  }

  /**
   * Publishes the element to the publication channel and returns a reference to the published version of the element.
   *
   * @param job
   *          the associated job
   * @param mediaPackage
   *          the mediapackage
   * @param elementId
   *          the mediapackage element id to publish
   * @return the published element
   * @throws PublicationException
   *           if publication fails
   */
  private Publication publish(final Job job, final MediaPackage mediaPackage, final String elementId) throws PublicationException {
    if (mediaPackage == null) {
      throw new IllegalArgumentException("Mediapackage must be specified");
    } else if (elementId == null) {
      throw new IllegalArgumentException("Mediapackage ID must be specified");
    }
    final MediaPackageElement element = mediaPackage.getElementById(elementId);
    if (element == null) {
      throw new IllegalArgumentException("Mediapackage element must be specified");
    }
    if (element.getIdentifier() == null) {
      throw new IllegalArgumentException("Mediapackage element must have an identifier");
    }
    if (element.getMimeType().toString().matches("text/xml")) {
      throw new IllegalArgumentException("Mediapackage element cannot be XML");
    }
    try {
      // Publication
      final File file = getFileWithURI(element);
      if (file == null) {
        job.setStatus(Job.Status.FAILED);
        return null;
      }
      final VideoMetadata videoMetadata = youTubeWorkspace.getVideoMetadata(mediaPackage, youTubeWorkspace.getYouTubeVideoTags());
      final VideoUpload videoUpload = new VideoUpload(videoMetadata, file);
      final UploadProgressListener progressListener = new UploadProgressListener(videoMetadata.getTitle(), mediaPackage.getSeriesTitle(), file);
      final Video video = youTubeService.addVideoToMyChannel(videoUpload, progressListener);
      final int timeoutMinutes = 60;
      final long startUploadMilliseconds = new Date().getTime();
      while (!progressListener.isComplete()) {
        Thread.sleep(POLL_MILLISECONDS);
        final long howLongWaitingMinutes = (new Date().getTime() - startUploadMilliseconds) / 60000;
        if (howLongWaitingMinutes > timeoutMinutes) {
          throw new PublicationException("Upload to YouTube exceeded " + timeoutMinutes + " minutes for episode " + videoMetadata.getTitle());
        }
      }
      final Integer duration = GoogleUtils.getDurationSeconds(video);
      if (duration <= 0) {
        logger.warn("YouTube (sometimes unreliable) tell us that " + video.getId() + " has zero duration.");
      }
      if (video.getId() == null) {
        throw new IllegalStateException("YouTube video object has null id.");
      }
      final String seriesId = mediaPackage.getSeries();
      if (StringUtils.isNotBlank(seriesId)) {
        addPlaylistItem(video, seriesId, youTubeWorkspace.getYouTubeVideoTags());
      }
      final URL url = new URL("http://www.youtube.com/watch?v=" + video.getId());
      return PublicationImpl.publication(UUID.randomUUID().toString(), CHANNEL_NAME, url.toURI(), MimeTypes.parseMimeType(MIME_TYPE));
    } catch (final Exception e) {
      logger.error("Failed publishing to Youtube", e);
      logger.warn("Error publishing {}, {}", element, e.getMessage());
      if (e instanceof PublicationException) {
        throw (PublicationException) e;
      } else {
        throw new PublicationException("YouTube publish failed on job: " + ToStringBuilder.reflectionToString(job, ToStringStyle.MULTI_LINE_STYLE), e);
      }
    }
  }

  private void addPlaylistItem(final Video video, final String seriesId, final String[] youTubeVideoTags)
          throws IOException, SeriesException, UnauthorizedException, NotFoundException {
    final DublinCoreCatalog series = seriesService.getSeries(seriesId);
    final String youTubePlaylistId = extractYouTubePlaylistId(series);
    // The YouTube playlist id in Matterhorn metadata might be null OR invalid. We handle both cases below.
    Playlist playlist = youTubePlaylistId == null ? null : youTubeService.getPlaylistById(youTubePlaylistId);
    playlist = playlist == null ? createYouTubePlaylist(series, youTubeVideoTags) : playlist;
    if (playlist != null) {
      youTubeService.addPlaylistItem(playlist.getId(), video.getId());
    }
  }

  private Playlist createYouTubePlaylist(final DublinCoreCatalog series, final String[] youTubeVideoTags)
          throws SeriesException, UnauthorizedException {
    final String seriesTitle = series.getFirst(DublinCore.PROPERTY_TITLE);
    final String description = series.getFirst(DublinCore.PROPERTY_DESCRIPTION);
    final String seriesId = series.getFirst(DublinCore.PROPERTY_IDENTIFIER);
    final String propertyAccessRights = series.hasValue(DublinCore.PROPERTY_ACCESS_RIGHTS)
            ? series.getFirst(DublinCore.PROPERTY_ACCESS_RIGHTS)
            : RecordingAccessRights.studentsOnlyAccessRights.name();
    final RecordingAccessRights recordingAccessRights = GoogleUtils.findByPropertyAccessRights(propertyAccessRights);
    final YouTubePlaylist youTubePlaylist = new YouTubePlaylist(null, seriesTitle, description,
            recordingAccessRights.getYouTubePrivacyStatus(), seriesId, youTubeVideoTags);
    final Playlist playlist;
    try {
      playlist = youTubeService.createPlaylist(youTubePlaylist);
      final String publisher = PublisherNamespace.YOUTUBE.encodePlayListAsPublisherUrn(playlist.getId());
      series.add(DublinCore.PROPERTY_PUBLISHER, DublinCoreValue.mk(publisher));
      seriesService.updateSeries(series);
      logger.debug("Created YouTube playlist {} for series {}", playlist.getId(), seriesId);
    } catch (final IOException e) {
      throw new SeriesException("Failed create YouTube playlist for series " + seriesTitle + " (" + seriesId + ')', e);
    }
    return playlist;
  }

  private File getFileWithURI(final MediaPackageElement element) {
    File file = null;
    final URI uri = element.getURI();
    try {
      file = workspace.get(uri);
    } catch (final Exception e) {
      logger.error("Unable to find or open: " + uri, e);
      file = null;
    } finally {
      file = file != null && file.exists() ? file : null;
    }
    return file;
  }

  @Override
  public Job retract(final MediaPackage mediaPackage) throws PublicationException {
    if (mediaPackage == null) {
      throw new IllegalArgumentException("Mediapackage must be specified");
    }
    try {
      final List<String> arguments = new ArrayList<String>();
      arguments.add(MediaPackageParser.getAsXml(mediaPackage));
      return serviceRegistry.createJob(JOB_TYPE, Operation.Retract.toString(), arguments);
    } catch (ServiceRegistryException e) {
      throw new PublicationException("Unable to create a job", e);
    }
  }

  /**
   * Retracts the mediapackage from YouTube.
   *
   * @param job
   *          the associated job
   * @param mediaPackage
   *          the mediapackage
   * @throws PublicationException
   *           if retract did not work
   */
  private Publication retract(final Job job, final MediaPackage mediaPackage) throws PublicationException {
    logger.info("Retract video from YouTube: {}", mediaPackage);
    Publication youtube = null;
    for (Publication publication : mediaPackage.getPublications()) {
      if (CHANNEL_NAME.equals(publication.getChannel())) {
        youtube = publication;
      }
    }
    if (youtube == null) {
      return null;
    }
    try {
      // Make sure video exists at YouTube prior to delete.
      final String videoId = youTubeWorkspace.getVideoId(mediaPackage);
      final DublinCoreCatalog series = seriesService.getSeries(mediaPackage.getSeries());
      final String youTubePlaylistId = series == null ? null : extractYouTubePlaylistId(series);
      if (youTubePlaylistId == null) {
        youTubeService.deleteVideo(videoId);
      } else {
        youTubeService.deleteVideo(videoId, youTubePlaylistId);
      }
    } catch (final Exception e) {
      logger.error("Failure retracting YouTube media {}", e.getMessage());
      throw new PublicationException("YouTube media retract failed on job: "
          + ToStringBuilder.reflectionToString(job, ToStringStyle.MULTI_LINE_STYLE), e);
    }
    return youtube;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.job.api.AbstractJobProducer#process(org.opencastproject.job.api.Job)
   */
  @Override
  protected String process(final Job job) throws Exception {
    Operation op = null;
    try {
      op = Operation.valueOf(job.getOperation());
      final List<String> arguments = job.getArguments();
      final MediaPackage mediapackage = MediaPackageParser.getFromXml(arguments.get(0));
      final Publication publication;
      switch (op) {
        case Publish:
          publication = publish(job, mediapackage, arguments.get(1));
          break;
        case Retract:
          publication = retract(job, mediapackage);
          break;
        default:
          throw new IllegalStateException("Don't know how to handle operation '" + job.getOperation() + "'");
      }
      return (publication == null) ? null : MediaPackageElementParser.getAsXml(publication);
    } catch (final IllegalArgumentException e) {
      throw new ServiceRegistryException("This service can't handle operations of type '" + op + "'", e);
    } catch (final IndexOutOfBoundsException e) {
      throw new ServiceRegistryException("This argument list for operation '" + op + "' does not meet expectations", e);
    } catch (final Exception e) {
      throw new ServiceRegistryException("Error handling operation '" + op + "'", e);
    }
  }

  String extractYouTubePlaylistId(final DublinCoreCatalog seriesCatalog) {
    final PublisherNamespace namespace = PublisherNamespace.YOUTUBE;
    final String publisher = getPublisher(namespace, seriesCatalog);
    final String value = StringUtils.trimToNull(publisher == null
            ? null
            : namespace.parsePlayListFromPublisherUrn(publisher));
    return value == null || StringUtils.equalsIgnoreCase(value, "null") ? null : value;
  }

  private String getPublisher(final PublisherNamespace namespace, final DublinCoreCatalog catalog) {
    String publisher = null;
    final List<DublinCoreValue> publisherList = catalog == null ? null : catalog.get(DublinCore.PROPERTY_PUBLISHER);
    if (publisherList != null) {
      for (final DublinCoreValue value : publisherList) {
        if (namespace.isPublisher(value.getValue())) {
          publisher = value.getValue();
        }
      }
    }
    return publisher;
  }

  /**
   * Callback for the OSGi environment to set the workspace reference.
   *
   * @param workspace
   *          the workspace
   */
  protected void setWorkspace(final Workspace workspace) {
    this.workspace = workspace;
    this.youTubeWorkspace = new YouTubeWorkspace(workspace);
  }

  /**
   * Callback for the OSGi environment to set the service registry reference.
   *
   * @param serviceRegistry
   *          the service registry
   */
  protected void setServiceRegistry(ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.job.api.AbstractJobProducer#getServiceRegistry()
   */
  @Override
  protected ServiceRegistry getServiceRegistry() {
    return serviceRegistry;
  }

  /**
   * Callback for setting the security service.
   *
   * @param securityService
   *          the securityService to set
   */
  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  /**
   * Callback for setting the user directory service.
   *
   * @param userDirectoryService
   *          the userDirectoryService to set
   */
  public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
    this.userDirectoryService = userDirectoryService;
  }

  /**
   * Sets a reference to the organization directory service.
   *
   * @param organizationDirectory
   *          the organization directory
   */
  public void setOrganizationDirectoryService(OrganizationDirectoryService organizationDirectory) {
    this.organizationDirectoryService = organizationDirectory;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.job.api.AbstractJobProducer#getSecurityService()
   */
  @Override
  protected SecurityService getSecurityService() {
    return securityService;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.job.api.AbstractJobProducer#getUserDirectoryService()
   */
  @Override
  protected UserDirectoryService getUserDirectoryService() {
    return userDirectoryService;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.job.api.AbstractJobProducer#getOrganizationDirectoryService()
   */
  @Override
  protected OrganizationDirectoryService getOrganizationDirectoryService() {
    return organizationDirectoryService;
  }

  public void setSeriesService(final SeriesService seriesService) {
    this.seriesService = seriesService;
  }

  public void setYouTubeService(YouTubeAPIVersion3Service youTubeService) {
    this.youTubeService = youTubeService;
  }

  private enum PublisherNamespace {

    YOUTUBE("urn:youtube:com:playlistId:");

    private String urn;

    PublisherNamespace(final String urn) {
      this.urn = urn;
    }

    public String encodePlayListAsPublisherUrn(final String playlistId) {
      if (playlistId == null) {
        throw new IllegalStateException("YouTube playlist object has null id.");
      }
      return urn + playlistId;
    }

    public String parsePlayListFromPublisherUrn(final String encodedPlayListId) {
      final String trimmed = StringUtils.trimToNull(encodedPlayListId);
      return trimmed == null ? null : StringUtils.remove(trimmed, urn);
    }

    public boolean isPublisher(final String encodedPlayListId) {
      return StringUtils.startsWith(encodedPlayListId, urn);
    }

  }

}
