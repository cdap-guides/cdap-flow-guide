/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.guides.flow;

import co.cask.cdap.api.flow.Flow;
import co.cask.cdap.api.flow.FlowSpecification;

/**
 * Flow that reads disk IO operations from a stream, and tracks which disks are slow.
 */
public class DiskPerformanceFlow implements Flow {
  static final String NAME = "DiskPerformanceFlow";

  @Override
  public FlowSpecification configure() {
    return FlowSpecification.Builder.with()
      .setName(NAME)
      .setDescription("Tracks slow disks using I/O ops stats")
      .withFlowlets()
        .add(DetectorFlowlet.NAME, new DetectorFlowlet())
        // start with 2 instances of the tracker
        .add(TrackerFlowlet.NAME, new TrackerFlowlet(), 2)
      .connect()
        .fromStream(DiskPerformanceApp.STREAM_NAME).to(DetectorFlowlet.NAME)
        .from(DetectorFlowlet.NAME).to(TrackerFlowlet.NAME)
      .build();
  }
}
