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

import static com.google.api.client.util.BackOff.STOP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.util.BackOff;

import org.junit.Before;
import org.junit.Test;
import org.opencastproject.util.data.Tuple;
import org.osgi.service.cm.ConfigurationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Properties;

public class StagedBackOffTest {

  private List<StagedBackOffPolicy> policies;
  private static final long MINUTES_IN_MILLISECONDS = 60000;

  private final String retryPolicy = "First failure:0:0:1,Second failure:900000:0:1,Third failure:3600000:0:1,Fourth failure:0:7200000:12";
  private final int totalExpectedTries = 19;

  @Before
  public void setUp() throws Exception {
    policies = new ArrayList<StagedBackOffPolicy>();
  }

  @Test
  public void testNextBackOffMillisReturnsStopWhenDefaultPolicyImplicitlyUsed() throws IOException {
    final BackOff aNullPolicy = new StagedBackOff(null);
    assertEquals(STOP, aNullPolicy.nextBackOffMillis());
    final List<StagedBackOffPolicy> list = Collections.emptyList();
    final BackOff aEmptyList = new StagedBackOff(list);
    assertEquals(STOP, aEmptyList.nextBackOffMillis());
  }

  @Test
  public void testNextBackOffMillisReturnsRetryStageDelayBeforeRetryWaitTime() throws IOException {
    final long retryStageDelay = 0;
    final long retryWaitTime = 1000;
    final int retryRepetitions = 2;
    policies.add(new StagedBackOffPolicy("Stage One", retryStageDelay, retryWaitTime, retryRepetitions));
    final BackOff aBackOff = new StagedBackOff(policies);
    assertEquals(retryStageDelay, aBackOff.nextBackOffMillis());
  }

  @Test
  public void testNextBackOffMillisCalledRetryRepetitionsTimes() throws IOException {
    final long retryStageDelay = 0;
    final long retryWaitTime = 1000;
    final int retryRepetitions = 2;
    policies.add(new StagedBackOffPolicy("Stage One", retryStageDelay, retryWaitTime, retryRepetitions));
    final BackOff aBackOff = new StagedBackOff(policies);
    aBackOff.nextBackOffMillis(); // first value returned for each stage is always retryStageDelay
    for (int i = 0; i < retryRepetitions; i++) {
      assertEquals(retryWaitTime, aBackOff.nextBackOffMillis());
    }
  }

  @Test
  public void testTotalWaitTimeIsEqualToRetryStageDelayPlusRetryRepetitionsTimesRetryWaitTime() throws IOException {
    final long retryStageDelay = 1;
    final long retryWaitTime = 1000;
    final int retryRepetitions = 3;
    policies.add(new StagedBackOffPolicy("Stage One", retryStageDelay, retryWaitTime, retryRepetitions));
    final BackOff aBackOff = new StagedBackOff(policies);
    long totalActualWaitTime = aBackOff.nextBackOffMillis();
    for (int i = 0; i < retryRepetitions; i++) {
      totalActualWaitTime = totalActualWaitTime + aBackOff.nextBackOffMillis();
    }
    assertEquals(3001, totalActualWaitTime);
  }

  @Test
  public void testNextBackOffMillisReturnsAcrossTwoPoliciesInNaturalOrderBeforeStopping() throws IOException {
    final long retryStageDelay = 1;
    final long retryWaitTime = 1000;
    policies.add(new StagedBackOffPolicy("Stage One", retryStageDelay, retryWaitTime, 2));
    policies.add(new StagedBackOffPolicy("Stage Three", retryStageDelay, retryWaitTime, 3));
    final BackOff aBackOff = new StagedBackOff(policies);
    assertEquals(retryStageDelay, aBackOff.nextBackOffMillis());
    assertEquals(retryWaitTime, aBackOff.nextBackOffMillis());
    assertEquals(retryWaitTime, aBackOff.nextBackOffMillis());
    assertEquals(retryStageDelay, aBackOff.nextBackOffMillis());
    assertEquals(retryWaitTime, aBackOff.nextBackOffMillis());
    assertEquals(retryWaitTime, aBackOff.nextBackOffMillis());
    assertEquals(retryWaitTime, aBackOff.nextBackOffMillis());
    assertEquals(STOP, aBackOff.nextBackOffMillis());
  }

  @Test
  public void testAllNonDefaultPolicyTypes() throws IOException {
    int expectedOncePerPolicy = 0;
    for (int i = 1; i < 5; i++) {
      policies.add(new StagedBackOffPolicy("A Description",0, 0, 0));
      expectedOncePerPolicy++;
    }
    final BackOff aBackOff = new StagedBackOff(policies);
    int countDownFromExpectedOncePerPolicy = expectedOncePerPolicy;
    while (aBackOff.nextBackOffMillis() != STOP) {
      assertNotEquals(0, countDownFromExpectedOncePerPolicy--);
    }
  }

  @Test
  public void testResetStartsAllOverAgain() throws IOException {
    int expectedOncePerPolicy = 0;
    for (int i = 1; i < 5; i++) {
      policies.add(new StagedBackOffPolicy("A Description",0, 0, 0));
      expectedOncePerPolicy++;
    }
    final BackOff aBackOff = new StagedBackOff(policies);
    int countDownFromExpectedOncePerPolicy = expectedOncePerPolicy;
    while (aBackOff.nextBackOffMillis() != STOP) {
      assertNotEquals(0, countDownFromExpectedOncePerPolicy--);
    }
    aBackOff.reset();
    countDownFromExpectedOncePerPolicy = expectedOncePerPolicy;
    while (aBackOff.nextBackOffMillis() != STOP) {
      assertNotEquals(0, countDownFromExpectedOncePerPolicy--);
    }
  }


  /**
   * First failure - immediately retry
   * Second failure - retry in 15 minutes
   * Third failure - retry in 1 hour.
   * Fourth failure - retry in 24 hours.
   * If there is a fifth failure, fail the job entirely.
   * @throws IOException
   */
  @Test
  public void testYouTubeRetryAlgorithm() throws IOException {
    final long firstFailure = 0;
    final long secondFailure = 15 * MINUTES_IN_MILLISECONDS;
    final long thirdFailure =  60 * MINUTES_IN_MILLISECONDS;
    final long fourthFailure = 24 * 60 * MINUTES_IN_MILLISECONDS;
    final long fifthFailure = STOP;

    policies.add(new StagedBackOffPolicy("", firstFailure, secondFailure, 1));
    policies.add(new StagedBackOffPolicy("", thirdFailure, fourthFailure, 1));

    final BackOff aBackOff = new StagedBackOff(policies);

    assertEquals(firstFailure, aBackOff.nextBackOffMillis());
    assertEquals(secondFailure, aBackOff.nextBackOffMillis());
    assertEquals(thirdFailure, aBackOff.nextBackOffMillis());
    assertEquals(fourthFailure, aBackOff.nextBackOffMillis());
    assertEquals(fifthFailure, aBackOff.nextBackOffMillis());
  }

  @Test
  public void testYouTubeRetry() throws IOException {
    final StagedBackOff backOff = getBackOff(retryPolicy);
    assertEquals(4, backOff.getPolicies().size());
    // First failure
    assertEquals(totalExpectedTries, backOff.getStageStack().size());
    assertEquals((long) 0, backOff.nextBackOffMillis());
    assertEquals((long) 0, backOff.nextBackOffMillis());
    // Second failure
    assertEquals(totalExpectedTries - 2, backOff.getStageStack().size());
    assertEquals((long) 900000, backOff.nextBackOffMillis());
    assertEquals((long) 0, backOff.nextBackOffMillis());
    // Third failure
    assertEquals(totalExpectedTries - 4, backOff.getStageStack().size());
    assertEquals((long) 3600000, backOff.nextBackOffMillis());
    assertEquals((long) 0, backOff.nextBackOffMillis());
    // Third failure
    assertEquals(totalExpectedTries - 6, backOff.getStageStack().size());
    assertEquals((long) 0, backOff.nextBackOffMillis());
    for (int index = 1; index <= 12; index++) {
      assertEquals((long) 7200000, backOff.nextBackOffMillis());
    }
    assertEquals(0, backOff.getStageStack().size());
  }

  @Test
  public void testYouTubeRetryReset() throws IOException {
    final StagedBackOff backOff = getBackOff(retryPolicy);
    assertEquals(4, backOff.getPolicies().size());
    // First failure
    assertEquals(totalExpectedTries, backOff.getStageStack().size());
    backOff.nextBackOffMillis();
    backOff.nextBackOffMillis();
    backOff.nextBackOffMillis();
    backOff.nextBackOffMillis();
    assertEquals(totalExpectedTries - 4, backOff.getStageStack().size());
    backOff.reset();
    assertEquals(totalExpectedTries, backOff.getStageStack().size());
  }

  @Test
  public void testYouTubeRetryDefaultConfig() throws IOException {
    final Properties p = new Properties();
    p.setProperty("org.opencastproject.publication.youtube.retryPolicy",
            "First failure:0:0:1,Second failure:900000:0:1,Third failure:3600000:0:1,Fourth failure:7200000:7200000:12");
    final StagedBackOff backOff = GoogleUtils.getStagedBackOff(p);
    //
    assertEquals(4, backOff.getPolicies().size());
    int index = 0;
    for (final StagedBackOffPolicy policy : backOff.getPolicies()) {
      final String description = policy.getDescription();
      assertTrue(description.contains(" failure"));
      // The internals of StagedBackOff maintain policies list in reverse order
      final long retryStageDelay = policy.getRetryStageDelay();
      final long retryWaitTime = policy.getRetryWaitTime();
      final int retryRepetitions = policy.getRetryRepetitions();
      switch (index) {
        case 0:
          assertEquals(7200000, retryStageDelay);
          assertEquals(7200000, retryWaitTime);
          assertEquals(12, retryRepetitions);
          break;
        case 1:
          assertEquals(3600000, retryStageDelay);
          assertEquals(0, retryWaitTime);
          assertEquals(1, retryRepetitions);
          break;
        case 2:
          assertEquals(900000, retryStageDelay);
          assertEquals(0, retryWaitTime);
          assertEquals(1, retryRepetitions);
          break;
        case 3:
          assertEquals(0, retryStageDelay);
          assertEquals(0, retryWaitTime);
          assertEquals(1, retryRepetitions);
          break;
        default:
          fail("Unexpected number of policies");
      }
      index++;
    }
  }

  @Test
  public void testNoYouTubeRetry() throws IOException {
    testNoYouTubeRetry(null);
    testNoYouTubeRetry("   ");
  }

  @Test
  public void testNullRetryPropertyInvokesDefaultPolicy() throws ConfigurationException, IOException {
    assertEquals(STOP, getBackOff(null).nextBackOffMillis());
    assertEquals(STOP, getBackOff("  ").nextBackOffMillis());
  }

  @Test
  public void testUpdatedConfiguration() throws ConfigurationException, IOException {
    final StagedBackOff policy = getBackOff("First failure:0:0:0,Second failure:900000:0:0,Third failure:3600000:0:0,Fourth failure:86400000:0:0");
    assertEquals(0, policy.nextBackOffMillis());
    assertEquals(900000, policy.nextBackOffMillis());
    assertEquals(3600000, policy.nextBackOffMillis());
    assertEquals(86400000, policy.nextBackOffMillis());
    assertEquals(STOP, policy.nextBackOffMillis());
  }

  @Test
  public void testInvalidRetryPropertyIsSkipped() throws ConfigurationException, IOException {
    final StagedBackOff policy = getBackOff("Good property:0:0:0,Invalid property - missing param:0:0,Invalid property - number format:3600000:o:0,Good property:86400000:0:0");
    assertEquals(0, policy.nextBackOffMillis());
    assertEquals(86400000, policy.nextBackOffMillis());
    assertEquals(STOP, policy.nextBackOffMillis());
  }

  private void testNoYouTubeRetry(final String retryPolicy) throws IOException {
    final StagedBackOff backOff = getBackOff(retryPolicy);
    assertEquals(BackOff.STOP, backOff.nextBackOffMillis());
    //
    assertEquals(1, backOff.getPolicies().size());
    final StagedBackOffPolicy policy = backOff.getPolicies().get(0);
    assertTrue(policy.getDescription().contains("Default"));
    assertEquals(0, policy.getRetryRepetitions());
    //
    final Deque<Tuple<String, Long>> stageStack = backOff.getStageStack();
    assertTrue(stageStack.isEmpty());
  }

  private StagedBackOff getBackOff(final String retryPolicy) {
    final Properties properties = new Properties();
    if (retryPolicy != null) {
      properties.put(GoogleKey.retryPolicy.getKey(), retryPolicy);
    }
    return GoogleUtils.getStagedBackOff(properties);
  }

}
