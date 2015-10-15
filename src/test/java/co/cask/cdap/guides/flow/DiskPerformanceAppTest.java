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

import co.cask.cdap.api.metrics.RuntimeMetrics;
import co.cask.cdap.test.ApplicationManager;
import co.cask.cdap.test.FlowManager;
import co.cask.cdap.test.ServiceManager;
import co.cask.cdap.test.StreamManager;
import co.cask.cdap.test.TestBase;
import co.cask.common.http.HttpRequest;
import co.cask.common.http.HttpRequests;
import co.cask.common.http.HttpResponse;
import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.Assert;
import org.junit.Test;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class DiskPerformanceAppTest extends TestBase {
  private static final Gson GSON = new Gson();

  @Test
  public void test() throws Exception {
    // Deploy the application
    ApplicationManager appManager = deployApplication(DiskPerformanceApp.class);

    // Start the flow
    FlowManager flowManager = appManager.getFlowManager(DiskPerformanceFlow.NAME);
    flowManager.start();

    try {
      StreamManager streamManager = getStreamManager(DiskPerformanceApp.STREAM_NAME);
      // disk1 has 3 slow reads, which will classify it as a slow disk
      streamManager.send("disk1 100");
      streamManager.send("disk1 999");
      streamManager.send("disk1 1000");
      // slow reads
      streamManager.send("disk1 1001");
      streamManager.send("disk1 5000");
      streamManager.send("disk1 10000");
      // disk2 has 2 slow reads, which will not classify it as a slow disk
      streamManager.send("disk2 100");
      streamManager.send("disk2 1000");
      // slow reads
      streamManager.send("disk2 5000");
      streamManager.send("disk2 10000");

      RuntimeMetrics countMetrics = flowManager.getFlowletMetrics(DetectorFlowlet.NAME);
      // 10 events should be processed by the detector flowlet
      countMetrics.waitForProcessed(10, 3, TimeUnit.SECONDS);
      // 7 events should be processed by the tracker flowlet, since 5 out of 10 events were slow disk reads
      countMetrics = flowManager.getFlowletMetrics(TrackerFlowlet.NAME);
      countMetrics.waitForProcessed(5, 3, TimeUnit.SECONDS);


      // Start service and verify
      ServiceManager serviceManager = appManager.getServiceManager(DiskPerformanceHTTPHandler.NAME);
      serviceManager.start();
      serviceManager.waitForStatus(true);
      try {
        URL serviceUrl = serviceManager.getServiceURL();

        URL url = new URL(serviceUrl, "slowdisks");
        HttpRequest request = HttpRequest.get(url).build();
        HttpResponse response = HttpRequests.execute(request);
        Assert.assertEquals(200, response.getResponseCode());
        Map<String, String> slowDisks = GSON.fromJson(response.getResponseBodyAsString(Charsets.UTF_8),
                                                      new TypeToken<Map<String, String>>() {}.getType());
        // disk1 should be classified as slow and disk2 should not.
        Assert.assertEquals(1, slowDisks.size());
        Assert.assertTrue(slowDisks.containsKey("disk1"));
      } finally {
        serviceManager.stop();
        serviceManager.waitForStatus(false);
      }
    } finally {
      flowManager.stop();
    }
  }
}
