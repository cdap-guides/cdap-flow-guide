package co.cask.cdap.guides;

import co.cask.cdap.api.annotation.ProcessInput;
import co.cask.cdap.api.flow.flowlet.AbstractFlowlet;
import co.cask.cdap.api.flow.flowlet.OutputEmitter;
import co.cask.cdap.api.flow.flowlet.StreamEvent;
import com.google.common.base.Charsets;

/**
 * Receives disk metrics as stream events, and outputs the disk ID if the disk operation was slow.
 */
public class Parser extends AbstractFlowlet {
  private static final long SLOW_THRESHOLD = 1000;

  private OutputEmitter<String> out;

  @ProcessInput
  public void process(StreamEvent diskMetrics) {
    String event = Charsets.UTF_8.decode(diskMetrics.getBody()).toString();
    // events are expected to have the following format:
    // diskId operationTime(in microsec)
    String[] fields = event.split(" ", 2);
    String diskId = fields[0];
    long readTime = Long.parseLong(fields[1]);
    if (readTime > SLOW_THRESHOLD) {
      out.emit(diskId, "diskId", diskId);
    }
  }
}
