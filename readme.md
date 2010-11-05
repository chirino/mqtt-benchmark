# Stomp Benchmark

A benchmarking tool for [Stomp](http://stomp.github.com) servers.

## Build Prep

* Install [sbt](http://code.google.com/p/simple-build-tool/wiki/Setup)
* run: `sbt update` in the stomp-benchmark project directory

## Running the Benchmark

The benchmark assumes that a Stomp 1.0 server is running on the local host on port 61613.
Use the `sbt run` command to execute the benchmark.  Run `sbt run --help` to get a listing
of all the command line arguments that the benchmark supports.

For each broker you are benchmarking you will typically execute:

    sbt run foo-3.2

Where foo-3.2 is the Stomp server product and version you are benchmarking.  
The benchmarking tool will then execute a large number of predefined 
usage scenarios and gather the performance metrics for each.  Those metrics
will get stored in a `foo-3.2.json` file.  

## Updating the Report

The `report.html` file can load and display the results of multiple benchmark runs.
You can updated which benchmark results are displayed by the report.html by editing
it and updating to the line which defines the `broker_names` variable (around line 28).

    var broker_names = ['foo-3.2', 'cheese-1.0']

The above example will cause the report to display the `foo-3.2.json` and `cheese-1.0.json` files.

## Running a Custom Scenario

If there is a particular scenario you want to manually execute against the 
broker, you can do so by starting up the Scala interactive interpreter by
running `sbt console`

Then at the console you execute:

    scala> val scenario = new com.github.stomp.benchmark.Scenario
    client: com.github.stomp.benchmark.Scenario = 
    --------------------------------------
    StompLoadClient Properties
    --------------------------------------
    host                  = 127.0.0.1
    port                  = 61613
    destination_type      = queue
    destination_count     = 1
    destination_name      = load
    sample_interval       = 5000

    --- Producer Properties ---
    producers             = 1
    message_size          = 1024
    persistent            = false
    sync_send             = false
    content_length        = true
    producer_sleep        = 0
    headers               = List()

    --- Consumer Properties ---
    consumers             = 1
    consumer_sleep        = 0
    ack                   = auto
    selector              = null
    durable               = false

This creates a new Scenario object which you can adjust it's properties and
then run by executing `scenario.run`.  For example, to run 10 producers and no
consumer on a topic, you would update the scenario object properties as follows:

    scala> client.producers = 10

    scala> client.consumers = 0

    scala> client.destination_type = "topic"
    
When you actually run the scenario, you it will report back the throughput metrics.
Press enter to stop the run.

    scala> scenario.run
    =======================
    Press ENTER to shutdown
    =======================

    Producer rate: 320,458.938 per second, total: 1,602,402
    Producer rate: 373,873.656 per second, total: 3,473,744
    ... <enter press> ...
    =======================
    Shutdown
    =======================
    
    scala>


