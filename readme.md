# MQTT Benchmark

A benchmarking tool for [MQTT 1.0](http://mqtt.github.com) servers.
The benchmark covers a wide variety of common usage scenarios.

# Just looking for the Results?

The numbers look different depending on the Hardware and OS they are run on:

* [Amazon Linux: EC2 High-CPU Extra Large Instance](http://hiramchirino.com/mqtt-benchmark/ec2-c1.xlarge/index.html)
* [Ubuntu 11.10: Quad-Core 2600k Intel CPU (3.4 GHz)](http://hiramchirino.com/mqtt-benchmark/ubuntu-2600k/index.html)
* [OS X: 2 x Quad-Core Intel Xeon (3 GHz)](http://hiramchirino.com/mqtt-benchmark/osx-8-core/index.html)

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

## Running the Benchmark on an EC2 Amazon Linux 64 bit AMI

If you want to run the benchmark on EC2, we recommend using at least the
c1.xlarge instance type.  Once you have the instance started just execute
the following commands on the instance:

    sudo yum install -y screen
    curl https://nodeload.github.com/chirino/mqtt-benchmark/tarball/master | tar -zxv 
    mv chirino-mqtt-benchmark-* mqtt-benchmark
    screen ./mqtt-benchmark/bin/benchmark-all

The results will be stored in the ~/reports directory.

## Running Custom Scenarios

See the [custom-scenario.md ](https://github.com/chirino/mqtt-benchmark/blob/master/custom-scenario.md) file for more information
on how to configure other benchmarking scenarios.
