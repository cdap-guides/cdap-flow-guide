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

import co.cask.cdap.api.flow.AbstractFlow;

/**
 * Flow that reads disk IO operations from a stream, and tracks which disks are slow.
 */
public class DiskPerformanceFlow extends AbstractFlow {
  static final String NAME = "DiskPerformanceFlow";

  @Override
  public void configure() {
    setName(NAME);
    setDescription("Tracks slow disks using I/O ops stats");
    addFlowlet(DetectorFlowlet.NAME, new DetectorFlowlet());
    // start with 2 instances of the tracker
    addFlowlet(TrackerFlowlet.NAME, new TrackerFlowlet(), 2);
    connectStream(DiskPerformanceApp.STREAM_NAME, DetectorFlowlet.NAME);
    connect(DetectorFlowlet.NAME, TrackerFlowlet.NAME);
  }
}
