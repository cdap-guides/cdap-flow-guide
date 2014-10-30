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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

/**
 * Handler with a single endpoint that returns disks that should be replaced soon.
 */
public class DiskPerformanceHTTPHandler extends AbstractHttpServiceHandler {
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
  static final String NAME = "DiskPerformanceService";

  @UseDataSet("slowDisks")
  private KeyValueTable slowDisksTable;

  @Path("slowdisks")
  @GET
  public void getSlowDisks(HttpServiceRequest request, HttpServiceResponder responder) {
    Iterator<KeyValue<byte[], byte[]>> slowDisksScan = slowDisksTable.scan(null, null);
    Map<String, String> slowDisks = Maps.newHashMap();
    while (slowDisksScan.hasNext()) {
      KeyValue<byte[], byte[]> slowDisk = slowDisksScan.next();
      String diskId = Bytes.toString(slowDisk.getKey());
      long troubleTime = Bytes.toLong(slowDisk.getValue());
      String troubleTimeStr = DATE_FORMAT.format(new Date(troubleTime));
      slowDisks.put(diskId, troubleTimeStr);
    }
    responder.sendJson(200, slowDisks);
  }
}
