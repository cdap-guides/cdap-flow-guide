/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package co.cask.cdap.guides.flow;

import co.cask.cdap.api.app.AbstractApplication;
import co.cask.cdap.api.data.stream.Stream;
import co.cask.cdap.api.dataset.lib.KeyValueTable;

/**
 * This is a simple example that tracks disk performance for many disks across a company. It receives disk read
 * times from a Stream, tracking how often each disk reported a slow read time, and uses a service to expose an
 * HTTP endpoint to highlight disks that may need to be replaced soon.
 */
public class DiskPerformanceApp extends AbstractApplication {
  static final String APP_NAME = "DiskPerformanceApp";
  static final String STREAM_NAME = "diskReads";

  @Override
  public void configure() {
    setName(APP_NAME);
    addStream(new Stream(STREAM_NAME));
    createDataset("slowDiskReads", KeyValueTable.class);
    createDataset("slowDisks", KeyValueTable.class);
    addFlow(new DiskPerformanceFlow());
    addService(DiskPerformanceHTTPHandler.NAME, new DiskPerformanceHTTPHandler());
  }
}

