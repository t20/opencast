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

import org.apache.commons.lang.StringUtils;

import static com.google.api.client.util.BackOff.STOP;

/**
 * Holds the values needed to drive <code>StagedBackOff</code>.
 *
 * @author Fernando Alvarez
 *
 */
public final class StagedBackOffPolicy {

  public static StagedBackOffPolicy getDefaultPolicy() {
    return new StagedBackOffPolicy();
  }

  private final String description;
  private final long retryStageDelay;
  private final long retryWaitTime;
  private final int retryRepetitions;

  /**
   *
   * @param description describes the stage
   * @param retryStageDelay the milliseconds to wait before attempting retries at this stage
   * @param retryWaitTime the milliseconds between retries
   * @param retryRepetitions the number of times to attempt a retry during this stage
   */
  public StagedBackOffPolicy(final String description, final long retryStageDelay, final long retryWaitTime,
          final int retryRepetitions) {
    this.description = StringUtils.isBlank(description) ? "Anonymous stage" : description;
    this.retryStageDelay = retryStageDelay;
    this.retryWaitTime = retryWaitTime;
    this.retryRepetitions = retryRepetitions;
  }

  private StagedBackOffPolicy() {
    this.description = "Default stage";
    this.retryStageDelay = STOP;
    this.retryWaitTime = STOP;
    this.retryRepetitions = 0;
  }

  public String getDescription() {
    return description;
  }

  public long getRetryStageDelay() {
    return retryStageDelay;
  }

  public long getRetryWaitTime() {
    return retryWaitTime;
  }

  public int getRetryRepetitions() {
    return retryRepetitions;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("StagedBackOffPolicy [description=");
    builder.append(description);
    builder.append(", retryStageDelay=");
    builder.append(retryStageDelay);
    builder.append(", retryWaitTime=");
    builder.append(retryWaitTime);
    builder.append(", retryRepetitions=");
    builder.append(retryRepetitions);
    builder.append("]");
    return builder.toString();
  }
}
