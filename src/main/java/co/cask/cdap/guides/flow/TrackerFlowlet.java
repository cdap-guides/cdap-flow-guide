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

import co.cask.cdap.api.annotation.Batch;
import co.cask.cdap.api.annotation.HashPartition;
import co.cask.cdap.api.annotation.ProcessInput;
import co.cask.cdap.api.annotation.UseDataSet;
import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.dataset.lib.KeyValueTable;
import co.cask.cdap.api.flow.flowlet.AbstractFlowlet;

/**
 * Receives the ID of a disk that recorded a slow read, updating how many times the disk has been slow and
 * storing the disk ID in a separate dataset if it has been slow very often, indicating that it may be time to replace
 * the disk.
 */
public class TrackerFlowlet extends AbstractFlowlet {
  // intentionally set very low for illustrative purposes
  private static final long FLAG_THRESHOLD = 3;
  static final String NAME = "slowDiskTracker";

  @UseDataSet("slowDiskReads")
  private KeyValueTable slowDiskReadsTable;

  @UseDataSet("slowDisks")
  private KeyValueTable slowDisksTable;

  @ProcessInput
  @Batch(100)
  @HashPartition("diskId")
  public void process(String diskId) {
    byte[] countAsBytes = slowDiskReadsTable.read(diskId);
    long slowCount = countAsBytes == null ? 0 : Bytes.toLong(countAsBytes);
    slowCount++;
    slowDiskReadsTable.write(diskId, Bytes.toBytes(slowCount));
    if (slowCount == FLAG_THRESHOLD) {
      slowDisksTable.write(diskId, Bytes.toBytes(System.currentTimeMillis()));
    }
  }
}
