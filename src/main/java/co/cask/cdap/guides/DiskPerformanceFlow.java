package co.cask.cdap.guides;

import co.cask.cdap.api.flow.Flow;
import co.cask.cdap.api.flow.FlowSpecification;

/**
 * Flow that reads disk IO operations from a stream, and tracks which disks are slow.
 */
public class DiskPerformanceFlow implements Flow {

  @Override
  public FlowSpecification configure() {
    return FlowSpecification.Builder.with()
      .setName("DiskPerformanceFlow")
      .setDescription("Tracks slow disks using I/O ops stats")
      .withFlowlets()
        .add("slowReadDetector", new DetectorFlowlet())
        // start with 2 instances of the tracker
        .add("slowDiskTracker", new TrackerFlowlet(), 2)
      .connect()
        .fromStream("diskReads").to("slowReadDetector")
        .from("slowReadDetector").to("slowDiskTracker")
      .build();
  }
}
