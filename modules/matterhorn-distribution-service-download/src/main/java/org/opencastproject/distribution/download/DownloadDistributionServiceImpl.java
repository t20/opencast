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
package org.opencastproject.distribution.download;

import static java.lang.String.format;
import static org.opencastproject.util.EqualsUtil.ne;
import static org.opencastproject.util.HttpUtil.waitForResource;
import static org.opencastproject.util.PathSupport.path;
import static org.opencastproject.util.RequireUtil.notNull;

import org.opencastproject.distribution.api.DistributionException;
import org.opencastproject.distribution.api.DistributionService;
import org.opencastproject.distribution.api.DownloadDistributionService;
import org.opencastproject.job.api.AbstractJobProducer;
import org.opencastproject.job.api.Job;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageElementParser;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.MediaPackageParser;
import org.opencastproject.security.api.OrganizationDirectoryService;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.TrustedHttpClient;
import org.opencastproject.security.api.UserDirectoryService;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.util.FileSupport;
import org.opencastproject.util.LoadUtil;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.UrlSupport;
import org.opencastproject.util.data.Effect;
import org.opencastproject.util.data.functions.Misc;
import org.opencastproject.workspace.api.Workspace;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

/**
 * Distributes media to the local media delivery directory.
 */
public class DownloadDistributionServiceImpl extends AbstractJobProducer
        implements DistributionService, DownloadDistributionService, ManagedService {

  /** Logging facility */
  private static final Logger logger = LoggerFactory.getLogger(DownloadDistributionServiceImpl.class);

  /** List of available operations on jobs */
  private enum Operation {
    Distribute, Retract
  }

  /** Receipt type */
  public static final String JOB_TYPE = "org.opencastproject.distribution.download";

  /** Default distribution directory */
  public static final String DEFAULT_DISTRIBUTION_DIR = "opencast" + File.separator + "static";

  /** Timeout in millis for checking distributed file request */
  private static final long TIMEOUT = 60000L;

  /** The load on the system introduced by creating a distribute job */
  public static final float DEFAULT_DISTRIBUTE_JOB_LOAD = 0.1f;

  /** The load on the system introduced by creating a retract job */
  public static final float DEFAULT_RETRACT_JOB_LOAD = 1.0f;

  /** The key to look for in the service configuration file to override the {@link DEFAULT_DISTRIBUTE_JOB_LOAD} */
  public static final String DISTRIBUTE_JOB_LOAD_KEY = "job.load.download.distribute";

  /** The key to look for in the service configuration file to override the {@link DEFAULT_RETRACT_JOB_LOAD} */
  public static final String RETRACT_JOB_LOAD_KEY = "job.load.download.retract";

  /** The load on the system introduced by creating a distribute job */
  private float distributeJobLoad = DEFAULT_DISTRIBUTE_JOB_LOAD;

  /** The load on the system introduced by creating a retract job */
  private float retractJobLoad = DEFAULT_RETRACT_JOB_LOAD;

  /** Interval time in millis for checking distributed file request */
  private static final long INTERVAL = 300L;

  /** Path to the distribution directory */
  protected File distributionDirectory = null;

  /** this media download service's base URL */
  protected String serviceUrl = null;

  /** The remote service registry */
  protected ServiceRegistry serviceRegistry = null;

  /** The workspace reference */
  protected Workspace workspace = null;

  /** The security service */
  protected SecurityService securityService = null;

  /** The user directory service */
  protected UserDirectoryService userDirectoryService = null;

  /** The organization directory service */
  protected OrganizationDirectoryService organizationDirectoryService = null;

  /** The trusted HTTP client */
  private TrustedHttpClient trustedHttpClient;

  /**
   * Creates a new instance of the download distribution service.
   */
  public DownloadDistributionServiceImpl() {
    super(JOB_TYPE);
  }

  /**
   * Activate method for this OSGi service implementation.
   *
   * @param cc
   *          the OSGi component context
   */
  protected void activate(ComponentContext cc) {
    serviceUrl = cc.getBundleContext().getProperty("org.opencastproject.download.url");
    if (serviceUrl == null)
      throw new IllegalStateException("Download url must be set (org.opencastproject.download.url)");
    logger.info("Download url is {}", serviceUrl);

    String ccDistributionDirectory = cc.getBundleContext().getProperty("org.opencastproject.download.directory");
    if (ccDistributionDirectory == null)
      throw new IllegalStateException("Distribution directory must be set (org.opencastproject.download.directory)");
    this.distributionDirectory = new File(ccDistributionDirectory);
    logger.info("Download distribution directory is {}", distributionDirectory);
  }

  @Override
  public Job distribute(String channelId, MediaPackage mediapackage, String elementId)
          throws DistributionException, MediaPackageException {
    return distribute(channelId, mediapackage, elementId, true);
  }

  @Override
  public Job distribute(String channelId, MediaPackage mediapackage, String elementId, boolean checkAvailability)
          throws DistributionException, MediaPackageException {
    notNull(mediapackage, "mediapackage");
    notNull(elementId, "elementId");
    notNull(channelId, "channelId");
    try {
      return serviceRegistry.createJob(
              JOB_TYPE,
              Operation.Distribute.toString(),
              Arrays.asList(channelId, MediaPackageParser.getAsXml(mediapackage), elementId,
                      Boolean.toString(checkAvailability)), distributeJobLoad);
    } catch (ServiceRegistryException e) {
      throw new DistributionException("Unable to create a job", e);
    }
  }

  /**
   * Distribute a Mediapackage element to the download distribution service.
   *
   * @param mediapackage
   *          The media package that contains the element to distribute.
   * @param elementId
   *          The id of the element that should be distributed contained within the media package.
   * @param checkAvailability
   *          Check the availability of the distributed element via http.
   * @return A reference to the MediaPackageElement that has been distributed.
   * @throws DistributionException
   *           Thrown if the parent directory of the MediaPackageElement cannot be created, if the MediaPackageElement
   *           cannot be copied or another unexpected exception occurs.
   */
  public MediaPackageElement[] distributeElement(String channelId, MediaPackage mediapackage, String elementId,
          boolean checkAvailability) throws DistributionException {
    notNull(mediapackage, "mediapackage");
    notNull(elementId, "elementId");
    notNull(channelId, "channelId");

    final String mediapackageId = mediapackage.getIdentifier().compact();
    final MediaPackageElement element = mediapackage.getElementById(elementId);

    // Make sure the element exists
    if (mediapackage.getElementById(elementId) == null)
      throw new IllegalStateException(format("No element %s found in mediapackage %s", elementId, mediapackageId));

    try {
      File source;
      try {
        source = workspace.get(element.getURI());
      } catch (NotFoundException e) {
        throw new DistributionException("Unable to find " + element.getURI() + " in the workspace", e);
      } catch (IOException e) {
        throw new DistributionException("Error loading " + element.getURI() + " from the workspace", e);
      }

      // Try to find a duplicated element source
      try {
        source = findDuplicatedElementSource(source, mediapackageId);
      } catch (IOException e) {
        logger.warn("Unable to find duplicated source {}: {}", source, ExceptionUtils.getMessage(e));
      }

      File destination = getDistributionFile(channelId, mediapackage, element);

      // Put the file in place
      try {
        FileUtils.forceMkdir(destination.getParentFile());
      } catch (IOException e) {
        throw new DistributionException("Unable to create " + destination.getParentFile(), e);
      }
      logger.info(format("Distributing %s@%s for publication channel %s to %s", elementId, mediapackageId, channelId,
              destination));

      try {
        FileSupport.link(source, destination, true);
      } catch (IOException e) {
        throw new DistributionException(format("Unable to copy %s tp %s", source, destination), e);
      }

      // Create a representation of the distributed file in the mediapackage
      MediaPackageElement distributedElement = (MediaPackageElement) element.clone();
      try {
        distributedElement.setURI(getDistributionUri(channelId, mediapackageId, element));
      } catch (URISyntaxException e) {
        throw new DistributionException("Distributed element produces an invalid URI", e);
      }
      distributedElement.setIdentifier(null);

      logger.info(format("Finished distributing element %s@%s for publication channel %s", elementId, mediapackageId,
              channelId));
      final URI uri = distributedElement.getURI();
      if (checkAvailability) {
        logger.info("Checking availability of distributed artifact {} at {}", distributedElement, uri);
        waitForResource(trustedHttpClient, uri, HttpServletResponse.SC_OK, TIMEOUT, INTERVAL)
                .fold(Misc.<Exception, Void> chuck(), new Effect.X<Integer>() {
                  @Override
                  public void xrun(Integer status) throws Exception {
                    if (ne(status, HttpServletResponse.SC_OK)) {
                      logger.warn("Attempt to access distributed file {} returned code {}", uri, status);
                      throw new DistributionException("Unable to load distributed file " + uri.toString());
                    }
                  }
                });
      }
      return new MediaPackageElement[] { distributedElement };
    } catch (Exception e) {
      logger.warn("Error distributing " + element, e);
      if (e instanceof DistributionException) {
        throw (DistributionException) e;
      } else {
        throw new DistributionException(e);
      }
    }
  }

  @Override
  public Job retract(String channelId, MediaPackage mediapackage, String elementId) throws DistributionException {
    notNull(mediapackage, "mediapackage");
    notNull(elementId, "elementId");
    notNull(channelId, "channelId");
    try {
      return serviceRegistry.createJob(JOB_TYPE, Operation.Retract.toString(),
              Arrays.asList(channelId, MediaPackageParser.getAsXml(mediapackage), elementId), retractJobLoad);
    } catch (ServiceRegistryException e) {
      throw new DistributionException("Unable to create a job", e);
    }
  }

  /**
   * Retract a media package element from the distribution channel. The retracted element must not necessarily be the
   * one given as parameter <code>elementId</code>. Instead, the element's distribution URI will be calculated. This way
   * you are able to retract elements by providing the "original" element here.
   *
   * @param channelId
   *          the channel id
   * @param mediapackage
   *          the mediapackage
   * @param elementId
   *          the element identifier
   * @return the retracted element or <code>null</code> if the element was not retracted
   * @throws org.opencastproject.distribution.api.DistributionException
   *           in case of an error
   */
  protected MediaPackageElement[] retractElement(String channelId, MediaPackage mediapackage, String elementId)
          throws DistributionException {
    notNull(mediapackage, "mediapackage");
    notNull(elementId, "elementId");
    notNull(channelId, "channelId");

    // Make sure the element exists
    MediaPackageElement element = mediapackage.getElementById(elementId);
    if (element == null)
      throw new IllegalStateException("No element " + elementId + " found in mediapackage");

    String mediapackageId = mediapackage.getIdentifier().compact();
    try {
      final File elementFile = getDistributionFile(channelId, mediapackage, element);
      final File mediapackageDir = getMediaPackageDirectory(channelId, mediapackage);
      // Does the file exist? If not, the current element has not been distributed to this channel
      // or has been removed otherwise
      if (!elementFile.exists()) {
        logger.info(
                format("Element %s@%s has already been removed or has never been distributed for publication channel %s",
                        elementId, mediapackageId, channelId));
        return new MediaPackageElement[] { element };
      }

      logger.info("Retracting element {} from {}", element, elementFile);

      // Try to remove the file and its parent folder representing the mediapackage element id
      FileUtils.forceDelete(elementFile.getParentFile());
      if (mediapackageDir.isDirectory() && mediapackageDir.list().length == 0)
        FileSupport.delete(mediapackageDir);

      logger.info(format("Finished retracting element %s@%s for publication channel %s", elementId, mediapackageId,
              channelId));
      return new MediaPackageElement[] { element };
    } catch (Exception e) {
      logger.warn(
              format("Error retracting element %s@%s for publication channel %s", elementId, mediapackageId, channelId),
              e);
      if (e instanceof DistributionException) {
        throw (DistributionException) e;
      } else {
        throw new DistributionException(e);
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.job.api.AbstractJobProducer#process(org.opencastproject.job.api.Job)
   */
  @Override
  protected String process(Job job) throws Exception {
    Operation op = null;
    String operation = job.getOperation();
    List<String> arguments = job.getArguments();
    try {
      op = Operation.valueOf(operation);
      String channelId = arguments.get(0);
      MediaPackage mediapackage = MediaPackageParser.getFromXml(arguments.get(1));
      String elementId = arguments.get(2);
      switch (op) {
        case Distribute:
          Boolean checkAvailability = Boolean.parseBoolean(arguments.get(3));
          MediaPackageElement[] distributedElement = distributeElement(channelId, mediapackage, elementId,
                  checkAvailability);
          return (distributedElement != null)
                  ? MediaPackageElementParser.getArrayAsXml(Arrays.asList(distributedElement)) : null;
        case Retract:
          MediaPackageElement[] retractedElement = retractElement(channelId, mediapackage, elementId);
          return (retractedElement != null) ? MediaPackageElementParser.getArrayAsXml(Arrays.asList(retractedElement))
                  : null;
        default:
          throw new IllegalStateException("Don't know how to handle operation '" + operation + "'");
      }
    } catch (IllegalArgumentException e) {
      throw new ServiceRegistryException("This service can't handle operations of type '" + op + "'", e);
    } catch (IndexOutOfBoundsException e) {
      throw new ServiceRegistryException("This argument list for operation '" + op + "' does not meet expectations", e);
    } catch (Exception e) {
      throw new ServiceRegistryException("Error handling operation '" + op + "'", e);
    }
  }

  /**
   * Try to find the same file being already distributed in one of the other channels
   *
   * @param source
   *          the source file
   * @param mpId
   *          the element's mediapackage id
   * @return the found duplicated file or the given source if nothing has been found
   * @throws IOException
   *           if an I/O error occurs
   */
  private File findDuplicatedElementSource(final File source, final String mpId) throws IOException {
    String orgId = securityService.getOrganization().getId();
    final Path rootPath = Paths.get(distributionDirectory.getAbsolutePath(), orgId);

    if (!Files.exists(rootPath))
      return source;

    List<Path> mediaPackageDirectories = new ArrayList<>();
    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(rootPath)) {
      for (Path path : directoryStream) {
        Path mpDir = path.resolve(mpId);
        if (Files.exists(mpDir)) {
          mediaPackageDirectories.add(mpDir);
        }
      }
    }

    if (mediaPackageDirectories.isEmpty())
      return source;

    final long size = Files.size(source.toPath());

    final File[] result = new File[1];
    for (Path p : mediaPackageDirectories) {
      Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          if (attrs.isDirectory())
            return FileVisitResult.CONTINUE;

          if (size != attrs.size())
            return FileVisitResult.CONTINUE;

          try (InputStream is1 = Files.newInputStream(source.toPath()); InputStream is2 = Files.newInputStream(file)) {
            if (!IOUtils.contentEquals(is1, is2))
              return FileVisitResult.CONTINUE;
          }
          result[0] = file.toFile();
          return FileVisitResult.TERMINATE;
        }
      });
      if (result[0] != null)
        break;
    }
    if (result[0] != null)
      return result[0];

    return source;
  }

  /**
   * Gets the destination file to copy the contents of a mediapackage element.
   *
   * @return The file to copy the content to
   */
  protected File getDistributionFile(String channelId, MediaPackage mp, MediaPackageElement element) {
    final String uriString = element.getURI().toString().split("\\?")[0];
    final String directoryName = distributionDirectory.getAbsolutePath();
    final String orgId = securityService.getOrganization().getId();
    if (uriString.startsWith(serviceUrl)) {
      String[] splitUrl = uriString.substring(serviceUrl.length() + 1).split("/");
      if (splitUrl.length < 5) {
        logger.warn(
                format("Malformed URI %s. Must be of format .../{orgId}/{channelId}/{mediapackageId}/{elementId}/{fileName}."
                        + " Trying URI without channelId", uriString));
        return new File(path(directoryName, orgId, splitUrl[1], splitUrl[2], splitUrl[3]));
      } else {
        return new File(path(directoryName, orgId, splitUrl[1], splitUrl[2], splitUrl[3], splitUrl[4]));
      }
    }
    return new File(path(directoryName, orgId, channelId, mp.getIdentifier().compact(), element.getIdentifier(),
            FilenameUtils.getName(uriString)));
  }

  /**
   * Gets the directory containing the distributed files for this mediapackage.
   *
   * @return the filesystem directory
   */
  protected File getMediaPackageDirectory(String channelId, MediaPackage mp) {
    final String orgId = securityService.getOrganization().getId();
    return new File(distributionDirectory, path(orgId, channelId, mp.getIdentifier().compact()));
  }

  /**
   * Gets the URI for the element to be distributed.
   *
   * @param mediaPackageId
   *          the mediapackage identifier
   * @param element
   *          The mediapackage element being distributed
   * @return The resulting URI after distribution
   * @throws URISyntaxException
   *           if the concrete implementation tries to create a malformed uri
   */
  protected URI getDistributionUri(String channelId, String mediaPackageId, MediaPackageElement element)
          throws URISyntaxException {
    String elementId = element.getIdentifier();
    String fileName = FilenameUtils.getName(element.getURI().toString());
    String orgId = securityService.getOrganization().getId();
    String destinationURI = UrlSupport.concat(serviceUrl, orgId, channelId, mediaPackageId, elementId, fileName);
    return new URI(destinationURI);
  }

  /**
   * Callback for the OSGi environment to set the workspace reference.
   *
   * @param workspace
   *          the workspace
   */
  protected void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
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
   * Callback for setting the trusted HTTP client.
   *
   * @param trustedHttpClient
   *          the trusted HTTP client to set
   */
  public void setTrustedHttpClient(TrustedHttpClient trustedHttpClient) {
    this.trustedHttpClient = trustedHttpClient;
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

  @Override
  public void updated(@SuppressWarnings("rawtypes") Dictionary properties) throws ConfigurationException {
    distributeJobLoad = LoadUtil.getConfiguredLoadValue(properties, DISTRIBUTE_JOB_LOAD_KEY,
            DEFAULT_DISTRIBUTE_JOB_LOAD, serviceRegistry);
    retractJobLoad = LoadUtil.getConfiguredLoadValue(properties, RETRACT_JOB_LOAD_KEY, DEFAULT_RETRACT_JOB_LOAD,
            serviceRegistry);
  }

}
