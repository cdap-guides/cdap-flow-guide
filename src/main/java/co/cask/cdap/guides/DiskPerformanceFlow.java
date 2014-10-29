package co.cask.cdap.guides;

import co.cask.cdap.api.flow.Flow;
import co.cask.cdap.api.flow.FlowSpecification;

/**
 * Flow that reads disk metrics from a stream, and tracks which disks are slow.
 */
public class DiskPerformanceFlow implements Flow {

  @Override
  public FlowSpecification configure() {
    return FlowSpecification.Builder.with()
      .setName("DiskPerformanceFlow")
      .setDescription("A flow that tracks how often each disk is slow")
      .withFlowlets()
        .add("parser", new Parser())
        // start with 2 instances of the tracker
        .add("tracker", new Tracker(), 2)
      .connect()
        .fromStream("diskReads").to("parser")
        .from("parser").to("tracker")
      .build();
  }
}
