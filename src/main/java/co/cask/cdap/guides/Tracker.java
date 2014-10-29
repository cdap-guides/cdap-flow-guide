package co.cask.cdap.guides;

import co.cask.cdap.api.annotation.Batch;
import co.cask.cdap.api.annotation.HashPartition;
import co.cask.cdap.api.annotation.ProcessInput;
import co.cask.cdap.api.annotation.UseDataSet;
import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.dataset.lib.KeyValueTable;
import co.cask.cdap.api.flow.flowlet.AbstractFlowlet;

/**
 * Receives the id of a disk that recorded a slow read, updating how many times the disk has been slow and
 * storing the disk id in a separate dataset if it has been slow very often, indicating that it may be time to replace
 * the disk.
 */
public class Tracker extends AbstractFlowlet {
  // intentionally set very low for illustrative purposes
  private static final long FLAG_THRESHOLD = 3;

  @UseDataSet("slowDiskReads")
  private KeyValueTable slowDiskReadsTable;

  @UseDataSet("slowDisks")
  private KeyValueTable slowDisksTable;

  @ProcessInput
  @Batch(10)
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
