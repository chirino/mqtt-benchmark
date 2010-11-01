# Stomp Benchmark

A benchmarking tool for [Stomp](http://stomp.github.com) servers.

# Build Setup

* Install [sbt][http://code.google.com/p/simple-build-tool/wiki/Setup]
* run: `sbt update` in the stomp-benchmark project directory

# Running the Benchmark

The benchmark assumes that a Stomp 1.0 server is running on the local host on port 61613.
Use the `sbt run` command to execute the benchmark.  Run `sbt run --help` to get a listing
of all the command line arguments that the benchmark supports.

For each broker you are benchmarking you will typically execute:

    sbt run foo-3.2

Where foo-3.2 is the Stomp server product and version you are benchmarking.  The benchmarking
tool will generate a `foo-3.2.json` file.  

# Visualizing

The `report.html` file can load and display the results of multiple benchmark runs.
You can updated which benchmark results are displayed by the report.html by editing
it and updating to the line which defines the `broker_names` variable (around line 28).

    var broker_names = ['foo-3.2', 'cheese-1.0']

The above example will cause the report to display the `foo-3.2.json` and `cheese-1.0.json` files.

