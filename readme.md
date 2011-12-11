# Stomp Benchmark

A benchmarking tool for [Stomp 1.o](http://stomp.github.com) servers.
The benchmark covers a wide variety of common usage scenarios.

# Just looking for the Results?

The numbers look different depending on the Hardware and OS they are run on:

* [EC2 High-CPU Extra Large Instance](http://hiramchirino.com/stomp-benchmark/ec2-c1.xlarge/)
* [Ubuntu 11.10 and 2600k Intel CPU](http://hiramchirino.com/stomp-benchmark/ubuntu-2600k/)

## Servers Currently Benchmarked

* Apache ActiveMQ
* Apache ActiveMQ Apollo
* RabbitMQ
* HornetQ

## Running the Benchmark

Just run:

    ./bin/benchmark-all
    
or one of the server specific benchmark scripts like:

    ./bin/benchmark-activemq

Tested to work on:

* Ubuntu 11.10
* Amazon Linux
* OS X

The benchmark report will be stored in the `reports/$(hostname)` directory.

## Running Custom Scenarios

See the [custom-scenario.md ][custom-scenario.md] file for more information
on how to configure other benchmarking scenarios.
