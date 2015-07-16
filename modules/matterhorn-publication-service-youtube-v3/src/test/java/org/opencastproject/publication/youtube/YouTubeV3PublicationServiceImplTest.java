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

import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.Video;
import org.junit.Before;
import org.junit.Test;
import org.opencastproject.google.GoogleServicesFactory;
import org.opencastproject.google.youtube.UploadProgressListener;
import org.opencastproject.google.youtube.VideoUpload;
import org.opencastproject.google.youtube.YouTubeAPIVersion3Service;
import org.opencastproject.google.youtube.YouTubePlaylist;
import org.opencastproject.job.api.JaxbJob;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageBuilderImpl;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.Track;
import org.opencastproject.mediapackage.track.TrackImpl;
import org.opencastproject.publication.api.PublicationException;
import org.opencastproject.security.api.OrganizationDirectoryService;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.UserDirectoryService;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.workspace.api.Workspace;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;

/**
 * @author John Crossman
 */
public class YouTubeV3PublicationServiceImplTest {

  private YouTubeV3PublicationServiceImpl service;
  private YouTubeAPIVersion3Service youTubeService;
  private OrganizationDirectoryService orgDirectory;
  private SecurityService security;
  private ServiceRegistry registry;
  private UserDirectoryService userDirectoryService;
  private Workspace workspace;

  @Before
  public void before() throws Exception {
    youTubeService = createMock(YouTubeAPIVersion3Service.class);
    youTubeService.initialize(anyObject(GoogleServicesFactory.class));
    expectLastCall();
    orgDirectory = createMock(OrganizationDirectoryService.class);
    security = createMock(SecurityService.class);
    registry = createMock(ServiceRegistry.class);
    userDirectoryService = createMock(UserDirectoryService.class);
    workspace = createMock(Workspace.class);
    //
    service = new YouTubeV3PublicationServiceImpl();
    service.setYouTubeService(youTubeService);
    service.setOrganizationDirectoryService(orgDirectory);
    service.setSecurityService(security);
    service.setServiceRegistry(registry);
    service.setUserDirectoryService(userDirectoryService);
    service.setWorkspace(workspace);
  }

  @Test
  public void testPublishNewPlaylist() throws PublicationException, MediaPackageException, URISyntaxException, IOException, ServiceRegistryException {
    expect(youTubeService.createPlaylist(anyObject(YouTubePlaylist.class))).andReturn(new Playlist()).once();
    expect(youTubeService.addVideoToMyChannel(anyObject(VideoUpload.class), anyObject(UploadProgressListener.class))).andReturn(new Video()).once();
    expect(youTubeService.addPlaylistItem(anyObject(String.class), anyObject(String.class))).andReturn(new PlaylistItem()).once();
    expect(registry.createJob(anyObject(String.class), anyObject(String.class), anyObject(List.class))).andReturn(new JaxbJob()).once();
    replay(youTubeService, orgDirectory, security, registry, userDirectoryService, workspace);
    final MediaPackage mediaPackage = new MediaPackageBuilderImpl().createNew();
    final Track track = new TrackImpl();
    mediaPackage.add(track);
    service.publish(mediaPackage, track);
  }

}
