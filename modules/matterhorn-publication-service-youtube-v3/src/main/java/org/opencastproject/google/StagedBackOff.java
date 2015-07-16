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

import com.google.api.client.util.BackOff;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.opencastproject.util.data.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Concrete strategy corresponding to <code>BackOff</code> retry strategy.
 * Increases the back-off period for each retry attempt following a staged approach designed to
 * accommodate increasingly longer outages.
 *
 * @author Fernando Alvarez
 *
 */
public class StagedBackOff implements BackOff {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final List<StagedBackOffPolicy> policies = new ArrayList<StagedBackOffPolicy>();
  private Deque<Tuple<String, Long>> stageStack = new LinkedList<Tuple<String, Long>>();

  /**
   * Each policy in the list contains the parameters that drive the retry algorithm for that stage.
   *
   * @param policyList if list is null or empty then the default policy will be used.
   */
  public StagedBackOff(final List<StagedBackOffPolicy> policyList) {
    if (CollectionUtils.isEmpty(policyList)) {
      policies.add(StagedBackOffPolicy.getDefaultPolicy());
      logger.warn("Setting StagedBackOff with Default Policy because no other policies were provided");
    } else {
      for (StagedBackOffPolicy policy : policyList) {
        policies.add(policy);
      }
    }
    Collections.reverse(policies);
    reset();
  }

  @Override
  public long nextBackOffMillis() throws IOException {
    if (stageStack.isEmpty()) {
      logger.info("That concludes all stages, will stop retrying now!");
      return STOP;
    }
    Tuple<String, Long> stage =  stageStack.removeFirst();
    final long retryWaitTime = stage.getB();
    final String message;
    if (retryWaitTime == STOP) {
      message = "Retry no more.";
    } else if (retryWaitTime == 0) {
      message = "Retry immediately.";
    } else {
      final String describeWaitTime = String.format("%d min, %d sec",
              TimeUnit.MILLISECONDS.toMinutes(retryWaitTime),
              TimeUnit.MILLISECONDS.toSeconds(retryWaitTime) - TimeUnit.MINUTES
                      .toSeconds(TimeUnit.MILLISECONDS.toMinutes(retryWaitTime))
      );
      message = "Retry again in " + describeWaitTime + '.';
    }
    logger.warn("Google/YouTube retry algorithm (stage=" + stage.getA() + "). " + message);
    return retryWaitTime;
  }

  @Override
  public void reset() {
    stageStack.clear();
    for (final StagedBackOffPolicy policy : policies) {
      pushStage(policy);
    }
  }

  @Override public String toString() {
    return ToStringBuilder.reflectionToString(this);
  }

  /**
   * Package-local for unit test.
   * @return never null
   */
  List<StagedBackOffPolicy> getPolicies() {
    return policies;
  }

  /**
   * Package-local for unit test.
   * @return never null
   */
  Deque<Tuple<String, Long>> getStageStack() {
    return stageStack;
  }

  private void pushStage(final StagedBackOffPolicy policy) {
    for (int i = 0; i < policy.getRetryRepetitions(); i++) {
      stageStack.addFirst(new Tuple<String, Long>(policy.getDescription(), policy.getRetryWaitTime()));
    }
    stageStack.addFirst(new Tuple<String, Long>(policy.getDescription(), policy.getRetryStageDelay()));
  }
}
