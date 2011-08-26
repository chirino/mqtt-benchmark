# Stomp Benchmark

A benchmarking tool for [Stomp](http://stomp.github.com) servers.

## Build Prep

* Install [sbt 0.7.7](http://code.google.com/p/simple-build-tool/downloads/detail?name=sbt-launch-0.7.7.jar&can=2&q=)
  and follow the [setup instructions](http://code.google.com/p/simple-build-tool/wiki/Setup) but instead 
  of setting up the sbt script to use `sbt-launch.jar "$@"` please use `sbt-launch.jar "$*"` instead.
  
* run: `sbt update` in the stomp-benchmark directory

## Running the Benchmark

The benchmark assumes that a Stomp 1.0 server is running on the local host on port 61613.
Use the `sbt run` command to execute the benchmark.  Run `sbt run --help` to get a listing
of all the command line arguments that the benchmark supports.

For each broker you are benchmarking you will typically execute:

    sbt run reports/foo-3.2.json

The benchmarking tool will then execute a large number of predefined 
usage scenarios and gather the performance metrics for each.  Those metrics
will get stored in a `reports/foo-3.2.json` file.  

## Updating the Report

The `reports/report.html` file can load and display the results of multiple benchmark runs.
You can updated which benchmark results are displayed by the report.html by editing
it and updating to the line which defines the `products` variable (around line 34).

      var products = [
        'apollo-1.0-SNAPSHOT', 
        'activemq-5.4.2'
      ];


Note: To display the results of `reports/report.html`, first you need to serve all the files from the same domain. In google chrome, if you use file:///, the same origin policy wont allow to load the results.
You can relax that restriction in Chrome by starting it with the `--allow-file-access-from-files` argument.  On OS X you can do that with the following command: `open -a 'Google Chrome' --args --allow-file-access-from-files`

### Running against Apollo 1.0-beta1

[Apollo](http://activemq.apache.org/apollo) is a new Stomp based 
message server from good folks at the [Apache ActiveMQ](http://activemq.apache.org/) 
project.

1. Follow the [getting started guide](http://activemq.apache.org/apollo/versions/1.0-beta1/website/documentation/getting-started.html) 
to install, setup, and start the server.

2. Run the benchmark with the admin credentials.  Example:

    sbt run --login admin --passcode password reports/ubuntu-intel-2600k/apollo-1.0-beta1.json

### Running against ActiveMQ 5.5.0

[ActiveMQ 5.5.0](http://activemq.apache.org) was the first Stomp Server implementation and as
such is sometimes considered to be the reference implementation for Stomp 1.0.

1. Update the `conf/activemq.xml` configuration file and add in the Stomp transport connector:

    <transportConnector name="stomp+nio" uri="stomp+nio://0.0.0.0:61613?transport.closeAsync=false"/>

2. Start the server by running:

    ./bin/activemq console

3. Run the benchmark using the default options.  Example:

    sbt run reports/ubuntu-intel-2600k/activemq-5.5.0.json

### Running against HornetQ 2.2.0.Final

[HornetQ](http://www.jboss.org/hornetq) provides native Stomp 1.0 Support.

1. Update the `config/stand-alone/non-clustered/hornetq-configuration.xml` file add 
stomp acceptor:
    <acceptor name="stomp-acceptor">
      <factory-class>org.hornetq.core.remoting.impl.netty.NettyAcceptorFactory</factory-class>
      <param key="protocol"  value="stomp"/>
      <param key="host"  value="0.0.0.0"/>
      <param key="port"  value="61613"/>
    </acceptor>      

2. Add the queues and topics used by the load test by updating the 
`config/stand-alone/non-clustered/hornetq-jms.xml` with the following:

    <queue name="loadq-0"><entry name="/queue/loadq-0"/></queue>
    <queue name="loadq-1"><entry name="/queue/loadq-1"/></queue>
    <queue name="loadq-2"><entry name="/queue/loadq-2"/></queue>
    <queue name="loadq-3"><entry name="/queue/loadq-3"/></queue>
    <queue name="loadq-4"><entry name="/queue/loadq-4"/></queue>
    <queue name="loadq-5"><entry name="/queue/loadq-5"/></queue>
    <queue name="loadq-6"><entry name="/queue/loadq-6"/></queue>
    <queue name="loadq-7"><entry name="/queue/loadq-7"/></queue>
    <queue name="loadq-8"><entry name="/queue/loadq-8"/></queue>
    <queue name="loadq-9"><entry name="/queue/loadq-9"/></queue>

    <topic name="loadt-0"><entry name="/topic/loadt-0"/></topic>
    <topic name="loadt-1"><entry name="/topic/loadt-1"/></topic>
    <topic name="loadt-2"><entry name="/topic/loadt-2"/></topic>
    <topic name="loadt-3"><entry name="/topic/loadt-3"/></topic>
    <topic name="loadt-4"><entry name="/topic/loadt-4"/></topic>
    <topic name="loadt-5"><entry name="/topic/loadt-5"/></topic>
    <topic name="loadt-6"><entry name="/topic/loadt-6"/></topic>
    <topic name="loadt-7"><entry name="/topic/loadt-7"/></topic>
    <topic name="loadt-8"><entry name="/topic/loadt-8"/></topic>
    <topic name="loadt-9"><entry name="/topic/loadt-9"/></topic>

3. Start the server by running:

    cd bin
    ./run.sh

4. Run the benchmark with the `--topic-prefix` and `--queue-prefix` options.  For
example:

    sbt run --topic-prefix=jms.topic. --queue-prefix=jms.queue. reports/ubuntu-intel-2600k/hornetq-2.2.0.Final.json

### Running against RabbitMQ 2.4.1

[RabbitMQ](http://www.rabbitmq.com/) is an erlang based message server..

1. Install RabbitMQ and the [Stomp Plugin](http://www.rabbitmq.com/plugins.html#rabbitmq-stomp).

2. Run the benchmark with the default guest credentatials and the --persistent-header option.  Example:

    sbt run --login guest --passcode guest reports/ubuntu-intel-2600k/rabbitmq-2.4.1.json


## Running a Custom Scenario

If there is a particular scenario you want to manually execute against the 
broker, you can do so by starting up the Scala interactive interpreter by
running `sbt console`

Then at the console you execute:

    scala> val scenario = new com.github.stomp.benchmark.NonBlockingScenario
    scenario: com.github.stomp.benchmark.Scenario = 
    --------------------------------------
    Scenario Settings
    --------------------------------------
      host                  = 127.0.0.1
      port                  = 61613
      destination_type      = queue
      destination_count     = 1
      destination_name      = load
      sample_interval (ms)  = 1000
  
      --- Producer Properties ---
      producers             = 1
      message_size          = 1024
      persistent            = false
      sync_send             = false
      content_length        = true
      producer_sleep (ms)   = 0
      headers               = List()
  
      --- Consumer Properties ---
      consumers             = 1
      consumer_sleep (ms)   = 0
      ack                   = auto
      selector              = null
      durable               = false

This creates a new NonBlockingScenario object which you can adjust it's properties and
then run by executing `scenario.run`.  For example, to run 10 producers and no
consumer on a topic, you would update the scenario object properties as follows:

    scala> scenario.producers = 10

    scala> scenario.consumers = 0

    scala> scenario.destination_type = "topic"
    
When you actually run the scenario, you it will report back the throughput metrics.
Press enter to stop the run.

    scala> scenario.run                                          
    --------------------------------------
    Scenario Settings
    --------------------------------------
      host                  = 127.0.0.1
      port                  = 61613
      destination_type      = topic
      destination_count     = 1
      destination_name      = load
      sample_interval (ms)  = 1000
  
      --- Producer Properties ---
      producers             = 10
      message_size          = 1024
      persistent            = false
      sync_send             = false
      content_length        = true
      producer_sleep (ms)   = 0
      headers               = List()
  
      --- Consumer Properties ---
      consumers             = 0
      consumer_sleep (ms)   = 0
      ack                   = auto
      selector              = null
      durable               = false
    --------------------------------------
         Running: Press ENTER to stop
    --------------------------------------

    Producer total: 345,362, rate: 345,333.688 per second
    Producer total: 725,058, rate: 377,908.125 per second
    Producer total: 1,104,673, rate: 379,252.813 per second
    Producer total: 1,479,280, rate: 373,913.750 per second
    ... <enter pressed> ...
    
    scala>

## Running a custom scenario from XML file and display it using the generic_report.html

Is it possible to run stomp-benchmark using scenarios defined in XML files providing the option `--scenario-file`

Also, to be able to display this scenario results using generic_report.html, you need to provide the option `--new-json`

The scenario file has a "scenarios" root element. Inside, first you define some information that will be displayed on the report. 
Afterwards, you can place a common section, defining values for some properties to be used in all the scenarios (exceptions can be made, redefining the value in a lower level).

After the common section, you can define one or more groups, and give them a name. Also, it can have a description, and a common section as before.

For simple groups, you can just start defining scenarios.

Then you can define one or more scenarios, and give them a name (internal name) and a label (it will be displayed in the report). You can also define a common section here.

Then, you just have to create one or more clients sections, and define the properties for this clients. All clientes in one scenario will run in parallel, but scenarios will run in sequence.

For more complex groups, you can define variables inside a loop section, and give different values to each variable. All the possible combinations of the values for each variable will be generated,
and a scenario for each combination will be generated using a scenario template. A scenario template is defined as a normal scenario, but you can use placeholders like ${variable_name} that will be substituded with the real value.

The use of multiple scenario templates is supported in stomp-benchmark, but only the first one will be displayed using the generic_report.html. Also note, that if more than 1 variable is defined, a table will be used to display the results.
The odd variables (in definition order) will be horizontal headers, the even ones, vertical headers.

The last thing to note, is that for the properties producer_sleep and consumer_sleep, a sleep function can be used instead of a value. For example you could define:

    <producer_sleep>
        <range end="10000">sleep</range>
        <range end="15000">0</range>
        <range end="70000"><burst fast="0" slow="100" duration="500" period="10000" /></range>
        <range end="80000">sleep</range>
    </producer_sleep>

That means, form 0ms until 10000ms, don't send any message. From 10000ms to 15000ms, send as fast as possible. From 15000ms to 70000ms, send in bursts, sometimes fast, someties slow.
The fast value is the sleep time when it's in a burst, the slow value is the sleep time when it isn't in a burst, the duration of the burst, and period is the period of time when, in average, a burst should occur.
So, in this case, in average, every 10 seconds we will have a burst of 0.5 seconds, sending as fast as possible. The rest of the time, is sending slow.

There are some properties that can only be defined in the common sections: sample_interval, sample_count, drain, blocking_io and warm_up_count.

These properties can be defined anywhere: login, passcode, host, port, producers, consumers,
destination_type, destination_name, consumer_prefix, queue_prefix, topic_prefix, message_size, content_length,
drain_timeout, persistent, durable, sync_send, ack, messages_per_connection, producers_per_sample, consumers_per_sample,
selector, producer_sleep, consumer_sleep

An example from default_scenarios.xml:

    <scenarios>
        <broker_name>Test Broker</broker_name>
        <description>This is the general description for the scenarios in this file.</description>
        <platform_name>Test Platform</platform_name>
        <platform_desc>Platform description</platform_desc>
        <common>
            <sample_interval>1000</sample_interval>
            <blocking_io>false</blocking_io>
            <warm_up_count>3</warm_up_count>
            <drain>true</drain>
        </common>
        <group name="Persistent Queue Load/Unload - Non Persistent Queue Load">
            <description>
                Persistent Queue Load/Unload - Non Persistent Queue Load
            </description>
            <common>
                <sample_count>30</sample_count>
                <destination_type>queue</destination_type>
                <destination_count>1</destination_count>
                <destination_name>load_me_up</destination_name>
            </common>
            <scenario name="non_persistent_queue_load" label="Non Persistent Queue Load">
                <clients name="20b_1a_1queue_0">
                    <producers>1</producers>
                    <consumers>0</consumers>
                    <message_size>20</message_size>
                    <persistent>false</persistent>
                    <sync_send>false</sync_send>
                </clients>
            </scenario>
            <scenario name="persistent_queue_load" label="Persistent Queue Load">
                <common>
                    <drain>false</drain>
                </common>
                <clients name="20b_1p_1queue_0">
                    <producers>1</producers>
                    <consumers>0</consumers>
                    <message_size>20</message_size>
                    <persistent>true</persistent>
                    <sync_send>true</sync_send>
                </clients>
            </scenario>
            <scenario name="persistent_queue_unload" label="Persistent Queue Unload">
                <clients name="20b_0_1queue_1">
                    <producers>0</producers>
                    <consumers>1</consumers>
                </clients>
            </scenario>
        </group>
        <group name="Fast and Slow Consumers">
            <loop>
                <var name="destination_type" label="Destination type">
                    <value label="Queue">queue</value>
                    <value label="Topic">topic</value>
                </var>
            </loop>
            <description>
                Scenario with fast and slow consumers
            </description>
            <scenario name="fast_slow_consumers_${destination_type}" label="Fast and Slow consumers on a ${destination_type}">
                <common>
                    <sample_count>15</sample_count>
                    <destination_type>${destination_type}</destination_type>
                    <destination_count>1</destination_count>
                </common>
                <clients name="20b_1a_1${destination_type}_1fast">
                    <producers>1</producers>
                    <consumers>1</consumers>
                    <message_size>20</message_size>
                    <persistent>false</persistent>
                    <sync_send>false</sync_send>
                </clients>
                <clients name="20b_1a_1${destination_type}_1slow">
                    <producers>0</producers>
                    <consumers>1</consumers>
                    <consumer_sleep>100</consumer_sleep>
                </clients>
            </scenario>
        </group>
    <scenarios>

To display the results on generic_report.html, first you need to serve all the files from the same domain. In google chrome, if you use file:///, the same origin policy wont allow to load the results.
You can relax that restriction in Chrome by starting it with the `--allow-file-access-from-files` argument.  On OS X you can do that with the following command: `open -a 'Google Chrome' --args --allow-file-access-from-files`

Then, you can create different directories for the different platforms you have and copy the json files to each directory.

Finally, you need to modify the generic_report.html to include the names of your json files (without extension) in the products array, and the names of the platform directories in the platforms array.
Platform is an array of arrays, each element is an array where the first element it's the name of the directory, and the second, the name we want to display in the report.

Now, you can just open generic_report.html to see the results.
