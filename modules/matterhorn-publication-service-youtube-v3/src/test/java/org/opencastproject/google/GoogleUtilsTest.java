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
package org.opencastproject.google;

import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoContentDetails;
import org.junit.Test;
import org.opencastproject.google.youtube.RecordingAccessRights;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * @author John Crossman
 */
public class GoogleUtilsTest {

  @Test
  public void testGetDurationSeconds() {
    final VideoContentDetails details = new VideoContentDetails();
    details.setDuration("PT15M51S");
    final Video video = new Video();
    video.setContentDetails(details);
    assertEquals(15 * 60 + 51, GoogleUtils.getDurationSeconds(video).intValue());
  }

  @Test
  public void testFindByPropertyAccessRightsDefault() {
    assertSame(RecordingAccessRights.studentsOnlyAccessRights,
            GoogleUtils.findByPropertyAccessRights(null));
    assertSame(RecordingAccessRights.studentsOnlyAccessRights,
            GoogleUtils.findByPropertyAccessRights("foo"));
  }

  @Test
  public void testFindByPropertyAccessRights() {
    assertSame(RecordingAccessRights.studentsOnlyAccessRights,
            GoogleUtils.findByPropertyAccessRights(RecordingAccessRights.studentsOnlyAccessRights.name()));
    assertSame(RecordingAccessRights.publicAccessRights,
            GoogleUtils.findByPropertyAccessRights(RecordingAccessRights.publicAccessRights.name()));
  }

  @Test
  public void testFindByPropertyAccessRightsTrimValue() {
    assertSame(RecordingAccessRights.studentsOnlyAccessRights,
            GoogleUtils.findByPropertyAccessRights("  STUDENTSONLYACCESSRIGHTS "));
  }

  @Test
  public void testFindByPropertyAccessRightsWithDefault() {
    assertNull(GoogleUtils.findByPropertyAccessRights("  xxx ", null));
    assertSame(RecordingAccessRights.publicAccessRights,
            GoogleUtils.findByPropertyAccessRights("  xxx ", RecordingAccessRights.publicAccessRights));
    assertSame(RecordingAccessRights.studentsOnlyAccessRights,
            GoogleUtils.findByPropertyAccessRights("  STUDENTSONLYACCESSRIGHTS ",
                    RecordingAccessRights.publicAccessRights));
  }

}
