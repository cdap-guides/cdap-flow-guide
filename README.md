Real-time data processing with a flow
=====================================
In this guide you will learn how to process data in real-time with the Cask Data Application Platform (CDAP).
You will also learn how easy it is to scale out applications on CDAP.

What You Will Build
-------------------
You will build a CDAP application that processes disk usage information across machines in a large company in order
flag disks that may need to be replaced soon. You will:

-   Build a [Flow](http://docs.cask.co/cdap/current/en/dev-guide.html#flows) to process disk usage data in real-time
-   Use [Datasets](http://docs.cask.co/cdap/current/en/dev-guide.html#datasets) to store the number of slow reads per disk and track slow disks that may need to be replaced soon
-   Build a [Service](http://docs.cask.co/cdap/current/en/dev-guide.html#services) to get slow disks that should be replaced soon


What you will need
------------------
-   [JDK 6 or JDK 7](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
-   [Apache Maven 3.0+](http://maven.apache.org/download.cgi)
-   [CDAP SDK](http://docs.cdap.io/cdap/current/en/getstarted.html#download-and-setup)

Let’s Build It!
---------------
The following sections will guide you through implementing a real-time data processing application from scratch.
If you want to deploy and run the application right away, you can clone the sources from this GitHub repository.
In that case, feel free to skip the following two sections and jump directly to the
[Build and Run Application](#build-and-run-application) section.

### Application Design

In CDAP, you process data in real-time by implementing a Flow.  In this example the flow consists of two flowlets.
The Parser flowlet consumes data from a DiskReadTimes stream, parses the event, and outputs the disk id if the event was a slow disk read.
The Tracker flowlet reads the disk id emitted by the Parser and updates a count for how often that disk has recorded a slow read.
In addition, if that disk has recorded too many slow reads, the disk id is written to a separate dataset that keeps track of all
disks that should soon be replaced.

### Implementation

The recommended way to build a CDAP application from scratch is to use a maven project.
Use the following directory structure (you’ll find contents of the files below):

    ./pom.xml
    ./src/main/java/co/cask/cdap/guide/DiskPerformanceApp.java
    ./src/main/java/co/cask/cdap/guide/DiskPerformanceFlow.java
    ./src/main/java/co/cask/cdap/guide/DiskPerformanceService.java
    ./src/main/java/co/cask/cdap/guide/Parser.java
    ./src/main/java/co/cask/cdap/guide/Tracker.java

First create the application, which contains a stream, flow, and datasets.

``` {.sourceCode .java}
public class DiskPerformanceApp extends AbstractApplication {

  @Override
  public void configure() {
    setName("DiskPerformanceApp");
    addStream(new Stream("diskReads"));
    createDataset("slowDiskReads", KeyValueTable.class);
    createDataset("slowDisks", KeyValueTable.class);
    addFlow(new DiskPerformanceFlow());
    addService("DiskPerformanceService", new DiskPerformanceService());
  }
}
```

Next, we create a Flow, which is composed of two flowlets, the Parser and Tracker.
The parser reads from the stream, and the counter reads from the parser.
We will set the number of Tracker instances to 2.
This means that there will be 2 separate Trackers running, all taking turns reading what the Parser outputs.
You want to do this if a single Parser can output more quickly than a single Tracker can process.

``` {.sourceCode .java}
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
```

Next we create the Parser flowlet, which reads from the stream and outputs the disk id if the event was a slow read.

``` {.sourceCode .java}
public class Parser extends AbstractFlowlet {
  private static final long SLOW_THRESHOLD = 100;

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
      out.emit(diskId);
    }
  }
}
```

Next we create the Tracker flowlet, which reads the output of the Parser flowlet and updates how many times each disk reported a slow read.
If a disk records too many slow reads, the Tracker places it in a separate dataset used to track slow disks that may need to be replaced soon.

``` {.sourceCode .java}
public class Tracker extends AbstractFlowlet {
  // intentionally set very low for illustrative purposes
  private static final long FLAG_THRESHOLD = 3;

  @UseDataSet("slowDiskReads")
  private KeyValueTable slowDiskReadsTable;

  @UseDataSet("slowDisks")
  private KeyValueTable slowDisksTable;

  @ProcessInput
  public void process(String diskId) {
    byte[] countAsBytes = slowDiskReadsTable.read(diskId);
    long slowCount = countAsBytes == null ? 0 : Bytes.toLong(countAsBytes);
    slowDiskReadsTable.write(diskId, Bytes.toBytes(slowCount));
    if (slowCount == FLAG_THRESHOLD) {
      slowDisksTable.write(diskId, Bytes.toBytes(System.currentTimeMillis()));
    }
  }
}
```

Finally, we implement a Service that exposes a RESTful API used to get slow disks that need to be replaced soon:

``` {.sourceCode .java}
public class DiskPerformanceService extends AbstractHttpServiceHandler {
  @UseDataSet("slowDisks")
  private KeyValueTable slowDisksTable;

  @Path("slowDisks")
  @GET
  public void getViews(HttpServiceRequest request, HttpServiceResponder responder) {
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
```

With this, we have a working application!
We can build it, send data to the stream, and send an HTTP request to get slow disks that should be replaced soon.
Before we do that, let’s add a couple enhancements.

Everything that happens in the process method of a flowlet is guaranteed to happen exactly once.
There is overhead in supplying this guarantee, so we may want to process multiple input items in one shot instead of processing them one by one.
With a batch size of 10, we will pay the cost of the overhead just once for every 10 events instead of 10 times for 10 events.
Telling a flowlet to process its input in batches of 10 is as simple as adding the Batch annotation to the process method.  

``` {.sourceCode .java}
public class Tracker extends AbstractFlowlet {
  ...

  @ProcessInput
  @Batch(10)
  public void process(String diskId) {
    ...
  }
}
```

So far, it might sound like batching is always better than not batching.
To understand why this is not always the case, we must go back to the fact that we have 2 Trackers reading the output of the Parser.
By default, each Tracker takes turns reading from the Parser. This strategy is called round-robin.
Suppose the Parser reads two slow disk reads for disk1.  It outputs “disk1” and again outputs “disk1”.
Tracker1 takes the first “disk1” and Tracker2 takes the second “disk1”.
Since both Trackers are running simultaneously, they both read that “disk1” was slow 0 times, they both add one to that count of 0,
then both attempt to write what they think is the new value of 1. This is called a write conflict.
The good news is that CDAP detects this, allows only one write to go through, then replays the entire second event.
For example, CDAP may decide to let Tracker1 go through, which updates the slow count of disk1 to 1.
When Tracker2 tries to write, CDAP will detect the conflict, then replay the event.
Tracker2 reads “disk1” as input, gets the slow count of disk1 which has now been updated to 1, adds 1 to the count, and successfully writes the new value of 2.

Now pretend that we are using batches of 1000 instead of batches of 1.
Tracker1 takes a batch of 1000 and Tracker2 takes a separate batch of 1000.
The chance that Tracker1 has a disk in its batch that also appears in Tracker2’s batch is pretty high.
This means that when they both go to update their counts, only one of their updates will go through, with the other needing to be replayed.
This means the work that one Tracker did will be entirely wasted and retried again,
which is much more costly with a big batch size because everything in the batch must be replayed.
One way to solve this problem is to make sure that no disks that go to Tracker1 will go to Tracker2.
For example, all events for disk1 go to Tracker1 and never go to Tracker2.
This is done by using hash partitioning instead of round-robin.
This is easy in CDAP and can be done in two lines.
When emitting in the Parser, a partition id and key must be given in addition to the data being emitted.

``` {.sourceCode .java}
public class Parser extends AbstractFlowlet {
  ...

  @ProcessInput
  public void process(StreamEvent diskMetrics) {
    ...
    if (readTime > SLOW_THRESHOLD) {
      out.emit(diskId, "diskId", diskId);
    }
  }
}
```

In the Tracker, you simply add the HashPartition annotation with the partition id.

``` {.sourceCode .java}
public class Tracker extends AbstractFlowlet {
  ...

  @ProcessInput
  @Batch(10)
  @HashPartition("diskId")
  public void process(String diskId) {
    ...
  }
}
```

Now we can enjoy the benefits of larger batch sizes without worrying about wasted work due to write conflicts.
With batching and hash partitioning, our Parser and Tracker classes have changed just 3 lines with their final versions below:

``` {.sourceCode .java}
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
    slowDiskReadsTable.write(diskId, Bytes.toBytes(slowCount));
    if (slowCount == FLAG_THRESHOLD) {
      slowDisksTable.write(diskId, Bytes.toBytes(System.currentTimeMillis()));
    }
  }
}
```

Build and Run Application
-------------------------

The DiskPerformanceApp can be built and packaged using standard Apache Maven commands:

    mvn clean package

Note that the remaining commands assume that the ``cdap-cli.sh`` script is available on your PATH.
If this is not the case, please add it:

    export PATH=$PATH:<CDAP home>/bin

If you haven't started already CDAP standalone, start it with the following commands:

    cdap.sh start

We can then deploy the application:

    cdap-cli.sh deploy app target/cdap-flow-guide-1.0.0.jar

Next we start the flow:

    cdap-cli.sh start flow DiskPerformanceApp.DiskPerformanceFlow

Note that there is 1 instance of the parser flowlet running and 2 instances of the Tracker flowlet running:

    cdap-cli.sh get instances flowlet DiskPerformanceApp.DiskPerformanceFlow.parser
    1

    cdap-cli.sh get instances flowlet DiskPerformanceApp.DiskPerformanceFlow.tracker
    2

We can scale out our application and increase the number of tracker flowlets to 4:

    cdap-cli.sh set instances flowlet DiskPerformanceApp.DiskPerformanceFlow.tracker 4

    cdap-cli.sh get instances flowlet DiskPerformanceApp.DiskPerformanceFlow.tracker
    4

Scaling your application is easy in CDAP!
Now we can manually send enough slow disk events to the diskReads stream to get a disk classified as a slow disk:

    cdap-cli.sh send stream diskReads "disk1 1001"
    cdap-cli.sh send stream diskReads "disk1 1001"
    cdap-cli.sh send stream diskReads "disk1 1001"
    cdap-cli.sh send stream diskReads "disk1 1001"

Next we start the service:

    cdap-cli.sh start service DiskPerformanceApp.DiskPerformanceService

The service exposes a REST API that allows us to get all slow disks and the timestamp at which they got flagged as a slow disk.
Make the request and note the output:

    curl http://localhost:10000/v2/apps/DiskPerformanceApp/services/DiskPerformanceService/methods/slowdisks && echo
    {"disk1":1414535740868}

Extend This Example
-------------------
To make this application more useful, you can extend it by:

-   Including disk type in the stream event and categorize a slow read based on the type of disk.
-   Passing your own custom java object through the flowlets instead of a string.
-   Adding an endpoint to the service that can remove a disk from the slowDisks table.
-   Changing the logic so that 1000 normal disk read times counteract a slow disk read.
-   Tracking additional disk metrics, such as write times, and use a combination of factors to determine whether or not a disk belongs in the slowDisks table.

Share and Discuss!
------------------
Have a question? Discuss at the [CDAP User Mailing List](https://groups.google.com/forum/#!forum/cdap-user)
