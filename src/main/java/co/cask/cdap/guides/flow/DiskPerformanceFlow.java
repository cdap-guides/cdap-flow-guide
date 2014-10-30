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
