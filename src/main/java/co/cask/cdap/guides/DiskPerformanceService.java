package co.cask.cdap.guides;

import co.cask.cdap.api.annotation.UseDataSet;
import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.dataset.lib.KeyValue;
import co.cask.cdap.api.dataset.lib.KeyValueTable;
import co.cask.cdap.api.service.http.AbstractHttpServiceHandler;
import co.cask.cdap.api.service.http.HttpServiceRequest;
import co.cask.cdap.api.service.http.HttpServiceResponder;
import com.google.common.collect.Maps;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import java.util.Iterator;
import java.util.Map;

/**
 * Handler with a single endpoint that returns disks that should be replaced soon.
 */
public class DiskPerformanceService extends AbstractHttpServiceHandler {

  @UseDataSet("slowDisks")
  private KeyValueTable slowDisksTable;

  @Path("slowdisks")
  @GET
  public void getSlowDisks(HttpServiceRequest request, HttpServiceResponder responder) {
    Iterator<KeyValue<byte[], byte[]>> slowDisksScan = slowDisksTable.scan(null, null);
    Map<String, Long> slowDisks = Maps.newHashMap();
    while (slowDisksScan.hasNext()) {
      KeyValue<byte[], byte[]> slowDisk = slowDisksScan.next();
      String diskId = Bytes.toString(slowDisk.getKey());
      long troubleTime = Bytes.toLong(slowDisk.getValue());
      slowDisks.put(diskId, troubleTime);
    }
    responder.sendJson(200, slowDisks);
  }
}
