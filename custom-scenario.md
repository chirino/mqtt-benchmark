# Stomp Benchmark

A benchmarking tool for [Stomp](http://stomp.github.com) servers.

## Build Prep

* Install [sbt 0.7.7](http://code.google.com/p/simple-build-tool/downloads/detail?name=sbt-launch-0.7.7.jar&can=2&q=)
  and follow the [setup instructions](http://code.google.com/p/simple-build-tool/wiki/Setup) but instead 
  of setting up the sbt script to use `sbt-launch.jar "$@"` please use `sbt-launch.jar "$*"` instead.
  
* run: `sbt update` in the stomp-benchmark directory

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

## Running a custom scenario from XML file

Is it possible to run stomp-benchmark using scenarios defined in XML files providing the option `--scenario-file`

Also, to be able to display this scenario results using generic_report.html, you need to provide the option `--new-json`

The scenario file has a "scenarios" root element. Inside, first you define some information that will be displayed on the report. 
Afterwards, you can place a common section, defining values for some properties to be used in all the scenarios (exceptions can be made, redefining the value in a lower level).

After the common section, you can define one or more groups, and give them a name. Also, it can have a description, and a common section as before.

For simple groups, you can just start defining scenarios.

Then you can define one or more scenarios, and give them a name (internal name) and a label (it will be displayed in the report). You can also define a common section here.

Then, you just have to create one or more clients sections, and define the properties for this clients. All clients in one scenario will run in parallel, but scenarios will run in sequence.

For more complex groups, you can define variables inside a loop section, and give different values to each variable. All the possible combinations of the values for each variable will be generated,
and a scenario for each combination will be generated using a scenario template. A scenario template is defined as a normal scenario, but you can use placeholders like ${variable_name} that will be substituted with the real value.

The use of multiple scenario templates in one group with loop variables is supported in stomp-benchmark, but only the first one will be displayed using the generic_report.html. Also note, that if more than 1 variable is defined, a table will be used to display the results.
The odd variables (in definition order) will be horizontal headers, the even ones, vertical headers.

The last thing to note, is that for the properties producer_sleep and consumer_sleep, message_size and messages_per_connection, instead of providing a single value, different values or functions for different time ranges can be provided.

In a range, you can specify the value to be used up to the millisecond specified in the `end` attribute. The `end` attribute can take positive values, negative values counting from the end, or the word "end". This way, it's possible to write scenarios that are independent from the scenario duration.

For the values, it's possible to provide three different functions: burst (with fast value, slow value, duration of the fast period, and period of bursts), random (with min and max values) and normal(with mean and variance values).

For example you could define:

    <producer_sleep>
        <range end="10000">sleep</range>
        <range end="15000">0</range>
        <range end="-10000"><burst fast="0" slow="100" duration="500" period="10000" /></range>
        <range end="end">sleep</range>
    </producer_sleep>

That means, form 0ms until 10000ms, don't send any message. From 10000ms to 15000ms, send as fast as possible. From 15000ms to 70000ms, send in bursts, sometimes fast, sometimes slow.
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

## Display the results of a custom XML scenario using the generic_report.html    
    
To display the results on generic_report.html, first you need to serve all the files from the same domain. In google chrome, if you use file:///, the same origin policy wont allow to load the results.
You can relax that restriction in Chrome by starting it with the `--allow-file-access-from-files` argument.  On OS X you can do that with the following command: `open -a 'Google Chrome' --args --allow-file-access-from-files`

Please note that, to be able to display the scenario results using generic_report.html, you need to provide the option `--new-json` when you run the benchmark.

Then, you can create different directories for the different platforms you have and copy the json files to each directory.

Finally, you need to modify the generic_report.html to include the names of your json files (without extension) in the products array, and the names of the platform directories in the platforms array.
Platform is an array of arrays, each element is an array where the first element it's the name of the directory, and the second, the name we want to display in the report.

Now, you can just open generic_report.html to see the results.
