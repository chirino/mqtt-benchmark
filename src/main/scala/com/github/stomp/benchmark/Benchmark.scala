/**
 * Copyright (C) 2009-2011 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.stomp.benchmark

import scala.collection.mutable.HashMap
import scala.xml.{XML, NodeSeq, Node}
import scala.util.control.Exception.catching

import org.osgi.service.command.CommandSession
import java.io.{PrintStream, FileOutputStream, File}
import org.apache.felix.gogo.commands.basic.DefaultActionPreparator
import collection.JavaConversions
import java.lang.{String, Class}
import org.apache.felix.gogo.commands.{CommandException, Action, Option => option, Argument => argument, Command => command}
import javax.management.remote.rmi._RMIConnection_Stub

object Benchmark {
  def main(args: Array[String]):Unit = {
    val session = new CommandSession {
      def getKeyboard = System.in
      def getConsole = System.out
      def put(p1: String, p2: AnyRef) = {}
      def get(p1: String) = null
      def format(p1: AnyRef, p2: Int) = throw new UnsupportedOperationException
      def execute(p1: CharSequence) = throw new UnsupportedOperationException
      def convert(p1: Class[_], p2: AnyRef) = throw new UnsupportedOperationException
      def close = {}
    }

    val action = new Benchmark()
    val p = new DefaultActionPreparator
    try {
      if( p.prepare(action, session, JavaConversions.asJavaList(args.toList)) ) {
        action.execute(session)
      }
    } catch {
      case x:CommandException=>
        println(x.getMessage)
        System.exit(-1);
    }
  }
}

@command(scope="stomp", name = "benchmark", description = "The Stomp benchmarking tool")
class Benchmark extends Action {

  @option(name = "--broker_name", description = "The name of the broker being benchmarked.")
  var broker_name:String = _

  @option(name = "--host", description = "server host name")
  var host = "127.0.0.1"
  @option(name = "--port", description = "server port")
  var port = 61613

  @option(name = "--login", description = "login name to connect with")
  var login:String = null
  @option(name = "--passcode", description = "passcode to connect with")
  var passcode:String = null

  @option(name = "--sample-count", description = "number of samples to take")
  var sample_count = 15
  @option(name = "--sample-interval", description = "number of milli seconds that data is collected.")
  var sample_interval = 1000
  @option(name = "--warm-up-count", description = "number of warm up samples to ignore")
  var warm_up_count = 3

  @argument(index=0, name = "out", description = "The file to store benchmark metrics in", required=true)
  var out:File = _

  @option(name = "--enable-topics", description = "enable benchmarking the topic scenarios")
  var enable_topics = true
  @option(name = "--enable-queues", description = "enable benchmarking the queue scenarios")
  var enable_queues = true
  @option(name = "--enable-persistent", description = "enable benchmarking the persistent scenarios")
  var enable_persistence = true

  @option(name = "--scenario-connection-scale", description = "enable the connection scale scenarios")
  var scenario_connection_scale = false

  @option(name = "--scenario-connection-scale-rate", description = "How many connection to add after each sample")
  var scenario_connection_scale_rate = 50
  @option(name = "--scenario-connection-max-samples", description = "The maximum number of sample to take in the connection scale scenario")
  var scenario_connection_scale_max_samples = 100

  @option(name = "--scenario-producer-throughput", description = "enable the producer throughput scenarios")
  var scenario_producer_throughput = true
  @option(name = "--scenario-queue-loading", description = "enable the queue load/unload scenarios")
  var scenario_queue_loading = true
  @option(name = "--scenario-partitioned", description = "enable the partitioned load scenarios")
  var scenario_partitioned = true
  @option(name = "--scenario-fan-in-out", description = "enable the fan in/fan out scenarios")
  var scenario_fan_in_out = true
  @option(name = "--scenario-durable-subs", description = "enable the durable subscription scenarios")
  var scenario_durable_subs = false
  @option(name = "--scenario-selector", description = "enable the selector based scenarios")
  var scenario_selector = false
  @option(name = "--scenario-slow-consumer", description = "enable the slow consumer scenarios")
  var scenario_slow_consumer = false

  @option(name = "--scenario-file", description = "uses a scenario defined in an XML file instead of the default ones")
  var scenario_file:File = _

  @option(name = "--queue-prefix", description = "prefix used for queue destiantion names.")
  var queue_prefix = "/queue/"
  @option(name = "--topic-prefix", description = "prefix used for topic destiantion names.")
  var topic_prefix = "/topic/"
  @option(name = "--blocking-io", description = "Should the clients use blocking io.")
  var blocking_io = false
  @option(name = "--drain-timeout", description = "How long to wait for a drain to timeout in ms.")
  var drain_timeout = 3000L

  @option(name = "--persistent-header", description = "The header to set on persistent messages to make them persistent.")
  var persistent_header = "persistent:true"

  @option(name = "--messages-per-connection", description = "The number of messages that are sent before the client reconnect.")
  var messages_per_connection = -1L

  @option(name = "--display-errors", description = "Should errors get dumped to the screen when they occur?")
  var display_errors = false

  var samples = HashMap[String, List[(Long,Long)]]()



  def json_format(value:Option[List[String]]):String = {
    value.map { json_format _ }.getOrElse("null")
  }

  def json_format(value:List[String]):String = {
    "[ "+value.mkString(",")+" ]"
  }

  def execute(session: CommandSession): AnyRef = {
    if( broker_name == null ) {
      broker_name = out.getName.stripSuffix(".json")
    }

    println("===================================================================")
    println("Benchmarking %s at: %s:%d".format(broker_name, host, port))
    println("===================================================================")


    if( scenario_file == null ) {
      run_benchmarks
    } else {
      load_and_run_benchmarks
    }

    val os = new PrintStream(new FileOutputStream(out))
    os.println("{")
    os.println("""  "benchmark_settings": {""")
    os.println("""    "broker_name": "%s",""".format(broker_name))
    os.println("""    "host": "%s",""".format(host))
    os.println("""    "port": %d,""".format(port))
    os.println("""    "sample_count": %d,""".format(sample_count))
    os.println("""    "sample_interval": %d,""".format(sample_interval))
    os.println("""    "warm_up_count": %d,""".format(warm_up_count))
    os.println("""    "scenario_connection_scale_rate": %d""".format(scenario_connection_scale_rate))
    os.println("""  },""")
    os.println(samples.map { case (name, sample)=>
      """  "%s": %s""".format(name, json_format(sample.map(x=> "[%d,%d]".format(x._1,x._2))))
    }.mkString(",\n"))
    os.println("}")

    os.close
    println("===================================================================")
    println("Stored: "+out)
    println("===================================================================")
    null
  }

  private def benchmark(name:String, drain:Boolean=true, sc:Int=sample_count, is_done: (List[Scenario])=>Boolean = null, blocking:Boolean=blocking_io)(init_func: (Scenario)=>Unit ):Unit = {
    multi_benchmark(List(name), drain, sc, is_done, blocking) { scenarios =>
      init_func(scenarios.head)
    }
  }

  private def multi_benchmark(names:List[String], drain:Boolean=true, sc:Int=sample_count, is_done: (List[Scenario])=>Boolean = null, blocking:Boolean=blocking_io)(init_func: (List[Scenario])=>Unit ):Unit = {
    val scenarios:List[Scenario] = names.map { name=>
      val scenario = if(blocking) new BlockingScenario else new NonBlockingScenario
      scenario.name = name
      scenario.sample_interval = sample_interval
      scenario.host = host
      scenario.port = port
      scenario.login = login
      scenario.passcode = passcode
      scenario.queue_prefix = queue_prefix
      scenario.topic_prefix = topic_prefix
      scenario.drain_timeout = drain_timeout
      scenario.persistent_header = persistent_header
      scenario.display_errors = display_errors
      scenario
    }

    init_func(scenarios)

    scenarios.foreach{ scenario=>
      scenario.destination_name = if( scenario.destination_type == "queue" ) {
       "loadq"
      } else if( scenario.destination_type == "topic" ) {
       "loadt"
      } else {
        scenario.destination_name
      }
    }

    print("scenario  : %s ".format(names.mkString(" and ")))

    def with_load[T](s:List[Scenario])(proc: => T):T = {
      s.headOption match {
        case Some(senario) =>
          senario.with_load {
            with_load(s.drop(1)) {
              proc
            }
          }
        case None =>
          proc
      }
    }

    Thread.currentThread.setPriority(Thread.MAX_PRIORITY)
    val sample_set = with_load(scenarios) {
      for( i <- 0 until warm_up_count ) {
        Thread.sleep(sample_interval)
        print(".")
      }
      scenarios.foreach(_.collection_start)

      if( is_done!=null ) {
        while( !is_done(scenarios) ) {
          print(".")
          Thread.sleep(sample_interval)
          scenarios.foreach(_.collection_sample)
        }

      } else {
        var remaining = sc
        while( remaining > 0 ) {
          print(".")
          Thread.sleep(sample_interval)
          scenarios.foreach(_.collection_sample)
          remaining-=1
        }
      }


      println(".")
      scenarios.foreach{ scenario=>
        val collected = scenario.collection_end
        collected.foreach{ x=>
          if( !x._1.startsWith("e_") || x._2.find( _._2 != 0 ).isDefined ) {
            println("%s samples: %s".format(x._1, json_format(x._2.map(_._2.toString))) )
          }
        }
        samples ++= collected
      }
    }
    Thread.currentThread.setPriority(Thread.NORM_PRIORITY)

    if( drain) {
      scenarios.headOption.foreach( _.drain )
    }
  }

  trait sleepFunction {

    protected val SLEEP = -500

    protected var init_time: Long = 0

    def init(time: Long) { init_time = time }

    def now() = { System.currentTimeMillis() - init_time }

    def apply() = 0

    /* Sleeps for short periods of time (fast) or long ones (slow) in bursts */
    def burstSleep(slow: Int, fast: Int, duration: Int, period: Int) = {
      new Function1[Long, Int] {
        var burstLeft: Long = 0
        var previousTime: Long = 0
        def apply(time: Long) = {
          if (time != previousTime) {
            if (burstLeft > 0) {
              burstLeft -= time-previousTime
              if(burstLeft < 0){
                burstLeft = 0
              }
            } else {
              if (util.Random.nextInt(period) == 0) {
                burstLeft = duration
              }
            }
            previousTime = time
          }
          if (burstLeft > 0) fast else slow
        }
      }
    }
  }

  private def mlabel(size:Int) = if((size%1024)==0) (size/1024)+"k" else size+"b"
  private def plabel(persistent:Boolean) = if(persistent) "p" else ""
  private def slabel(sync_send:Boolean) = if(sync_send) "" else "a"

  def run_benchmarks = {


    val persistence_values = if (enable_persistence) {
      List(false, true)
    } else {
      List(false)
    }

    var destination_types = List[String]()
    if( enable_queues ) {
      destination_types ::= "queue"
    }
    if( enable_topics ) {
      destination_types ::= "topic"
    }


    if(scenario_connection_scale ) {

      for( messages_per_connection <- List(-1)) {

        /** this test keeps going until we start getting a large number of errors */
        var remaining = scenario_connection_scale_max_samples
        def is_done(scenarios:List[Scenario]):Boolean = {
          remaining -= 1;
          var errors = 0L
          scenarios.foreach( _.error_samples.lastOption.foreach( errors+= _._2 ) )
          return errors >= scenario_connection_scale_rate || remaining <= 0
        }

        benchmark("20b_Xa%s_1queue_1".format(messages_per_connection)+"m", true, 0, is_done, false) { scenario=>
          scenario.message_size = 20
          scenario.producers = 0
          scenario.messages_per_connection = messages_per_connection
          scenario.producers_per_sample = scenario_connection_scale_rate
          scenario.producer_sleep = 1000
          scenario.persistent = false
          scenario.sync_send = false
          scenario.destination_count = 1
          scenario.destination_type = "queue"
          scenario.consumers = 1
        }
      }
    }

    // Setup a scenario /w fast and slow consumers
    if(scenario_slow_consumer) {
      for( dt <- destination_types) {
        multi_benchmark(List("20b_1a_1%s_1fast".format(dt), "20b_0_1%s_1slow".format(dt))) {
          case List(fast:Scenario, slow:Scenario) =>
            fast.message_size = 20
            fast.producers = 1
            fast.persistent = false
            fast.sync_send = false
            fast.destination_count = 1
            fast.destination_type = dt
            fast.consumers = 1

            slow.producers = 0
            slow.destination_count = 1
            slow.destination_type = dt
            slow.consumer_sleep = 100 // He can only process 10 /sec
            slow.consumers = 1
          case _ =>
        }
      }
    }

    // Setup selecting consumers on 1 destination.
    if( scenario_selector ) {
      for( dt <- destination_types) {
        multi_benchmark(List("20b_color_2a_1%s_0".format(dt), "20b_0_1%s_1_red".format(dt), "20b_0_1%s_1_blue".format(dt))) {
          case List(producer:Scenario, red:Scenario, blue:Scenario) =>
            producer.message_size = 20
            producer.producers = 2
            producer.headers = Array(Array("color:red"), Array("color:blue"))
            producer.persistent = false
            producer.sync_send = false
            producer.destination_count = 1
            producer.destination_type = dt
            producer.consumers = 0

            red.producers = 0
            red.destination_count = 1
            red.destination_type = dt
            red.selector = "color='red'"
            red.consumers = 1

            blue.producers = 0
            blue.destination_count = 1
            blue.destination_type = dt
            blue.selector = "color='blue'"
            blue.consumers = 1
          case _ =>
        }
      }
    }

    if( enable_topics && scenario_producer_throughput ) {
      // Benchmark for figuring out the max producer throughput
      for( size <- List(20, 1024, 1024 * 256) ) {
        val name = "%s_1a_1topic_0".format(mlabel(size))
        benchmark(name) { g=>
          g.message_size = size
          g.producers = 1
          g.persistent = false
          g.sync_send = false
          g.destination_count = 1
          g.destination_type = "topic"
          g.consumers = 0
        }
      }
    }

    // Benchmark for the queue parallel load scenario
    if( scenario_partitioned ) {

      val message_sizes = List(20, 1024, 1024 * 256)
      val destinations = List(1, 5, 10)

      for( persistent <- persistence_values; destination_type <- destination_types ; size <- message_sizes  ; load <- destinations ) {
        val name = "%s_%d%s%s_%d%s_%d".format(mlabel(size), load, plabel(persistent), slabel(persistent), load, destination_type, load)
        benchmark(name) { g=>
          g.message_size = size
          g.producers = load
          g.persistent = persistent
          g.sync_send = persistent
          g.destination_count = load
          g.destination_type = destination_type
          g.consumers = load
        }
      }
    }

    if( scenario_fan_in_out  ) {
      val client_count = List(1, 5, 10)
      val message_sizes = List(20)
      
      for( persistent <- persistence_values; destination_type <- destination_types ; size <- message_sizes  ; consumers <- client_count; producers <- client_count ) {
        if( !(consumers == 1 && producers == 1) ) {
          val name = "%s_%d%s%s_1%s_%d".format(mlabel(size), producers, plabel(persistent), slabel(persistent), destination_type, consumers)
          benchmark(name) { g=>
            g.message_size = size
            g.producers = producers
            g.persistent = persistent
            g.sync_send = persistent
            g.destination_count = 1
            g.destination_type = destination_type
            g.consumers = consumers
          }
        }
      }
    }

    if( enable_topics && scenario_durable_subs) {
      // Benchmark for durable subscriptions on topics
      for( persistent <- persistence_values ; size <- List(1024)  ; load <- List(5, 20) ) {
        val name = "%s_1%s%s_1topic_%dd".format(mlabel(size), plabel(persistent), slabel(persistent), load)
        benchmark(name) { g=>
          g.message_size = size
          g.producers = 1
          g.persistent = persistent
          g.sync_send = persistent
          g.destination_count = 1
          g.destination_type = "topic"
          g.consumers = load
          g.durable = true
        }
      }
    }

    if( enable_persistence && scenario_queue_loading ) {
      for( persistent <- List(false, true)) {
        val size = 20

        // Benchmark queue loading
        val name = "%s_1%s%s_1queue_0".format(mlabel(size), plabel(persistent), slabel(persistent))
        benchmark(name, false, 30) { g=>
          g.message_size = 20
          g.producers = 1
          g.sync_send = persistent
          g.persistent = persistent
          g.destination_count = 1
          g.destination_type = "queue"
          g.consumers = 0
          g.destination_name = "load_me_up"
        }

        // Benchmark unloading
        if(persistent) {
          val name = "%s_0_1queue_1".format(mlabel(size))
          benchmark(name, true, 30) { g=>
            g.producers = 0
            g.destination_count = 1
            g.destination_type = "queue"
            g.consumers = 1
            g.destination_name = "load_me_up"
          }
        }

      }
    }

  }

  def load_and_run_benchmarks = { 

    def getStringValue(property_name: String, ns_xml: NodeSeq): Option[String] = {
      val value = ns_xml \ property_name
      if (value.length == 1) Some(value.text.trim) else None
    }

    def getIntValue(property_name: String, ns_xml: NodeSeq): Option[Int] = {
      val value = getStringValue(property_name, ns_xml)
      try {
        value.map((x:String) => x.toInt)
      } catch {
        case e: NumberFormatException => throw new Exception("Error in XML scenario, not integer provided: " + value.getOrElse("\"\""))
      }
    }

    def getBooleanValue(property_name: String, ns_xml: NodeSeq): Option[Boolean] = {
      val value = getStringValue(property_name, ns_xml)
      try {
        value.map((x:String) => x.toBoolean)
      } catch {
        case e: NumberFormatException => throw new Exception("Error in XML scenario, not integer provided: " + value.getOrElse("\"\""))
      }
    }

    def getStringValueCascade (property_name: String, global_common_xml: NodeSeq = NodeSeq.Empty, scenario_common_xml: NodeSeq = NodeSeq.Empty, clients_xml: NodeSeq = NodeSeq.Empty): Option[String] = {
      var value: Option[String] = None
      value = getStringValue(property_name, global_common_xml).orElse(value)
      value = getStringValue(property_name, scenario_common_xml).orElse(value)
      getStringValue(property_name, clients_xml).orElse(value)
    }

    def getIntValueCascade (property_name: String, global_common_xml: NodeSeq = NodeSeq.Empty, scenario_common_xml: NodeSeq = NodeSeq.Empty, clients_xml: NodeSeq = NodeSeq.Empty): Option[Int] = {
      var value: Option[Int] = None
      value = getIntValue(property_name, global_common_xml).orElse(value)
      value = getIntValue(property_name, scenario_common_xml).orElse(value)
      getIntValue(property_name, clients_xml).orElse(value)
    }

    def getBooleanValueCascade (property_name: String, global_common_xml: NodeSeq = NodeSeq.Empty, scenario_common_xml: NodeSeq = NodeSeq.Empty, clients_xml: NodeSeq = NodeSeq.Empty): Option[Boolean] = {
      var value: Option[Boolean] = None
      value = getBooleanValue(property_name, global_common_xml).orElse(value)
      value = getBooleanValue(property_name, scenario_common_xml).orElse(value)
      getBooleanValue(property_name, clients_xml).orElse(value)
    }

    def getPropertySleep(property_name: String, clients_xml: NodeSeq): sleepFunction = { 
      val format_catcher = catching(classOf[NumberFormatException])
      val property_sleep_nodeset = clients_xml \ property_name
      val property_sleep_value: Option[Int] = format_catcher.opt(property_sleep_nodeset.text.toInt)
      if (property_sleep_nodeset.length == 1 && property_sleep_value.isDefined) {
        new sleepFunction { override def apply() = property_sleep_value.get }
      } else if ((property_sleep_nodeset \ "range").length > 0) {
        new sleepFunction {
          var ranges: List[Tuple2[Int, (Long) => Int]] = Nil 
          for (range_node <- property_sleep_nodeset \ "range") {
            val range_value =  format_catcher.opt(range_node.text.toInt)
            val range_end =  getIntValue("@end", range_node)
            val range_burst = range_node \ "burst"
            if (range_node.text == "sleep") {
              ranges :+= Tuple2(range_end.get, (time: Long) => SLEEP)
            } else if (range_value.isDefined) {
              ranges :+= Tuple2(range_end.get, (time: Long) => range_value.get)
            } else if (range_burst.length == 1) {
              var (slow, fast, duration, period) = (100, 0 , 1, 10)
              slow = getIntValue("@slow", range_burst).getOrElse(slow)
              fast = getIntValue("@fast", range_burst).getOrElse(fast)
              duration = getIntValue("@duration", range_burst).getOrElse(duration)
              period = getIntValue("@period", range_burst).getOrElse(period)
              ranges :+= Tuple2(range_end.get, burstSleep(slow, fast, duration, period))
            } else {
              throw new Exception("Error in XML scenario, unsuported sleep function: "+range_node.text)
            }
          }

          override def apply() = {
            val n = now
            val r = ranges.find( r => n < r._1 )
            if (r.isDefined) {
                r.get._2(n)
            } else {
                SLEEP
            }
          }
        }
      } else {
        new sleepFunction { override def apply() = 0 }
      }
    }

    val scenarios_xml = XML.loadFile(scenario_file)
    val global_common_xml = scenarios_xml \ "common"

    for (scenario_xml <- scenarios_xml \ "scenario") {
      val scenario_common_xml = scenario_xml \ "common"
      val names = (scenario_xml \ "clients").map( client => (client \ "@name").text ).toList 
      val sc = getIntValueCascade("sample_count", global_common_xml, scenario_common_xml).getOrElse(sample_count)
      val drain = getBooleanValueCascade("drain", global_common_xml, scenario_common_xml).getOrElse(false)
      val blocking = getBooleanValueCascade("blocking_io", global_common_xml, scenario_common_xml).getOrElse(blocking_io)
      warm_up_count = getIntValueCascade("warm_up_count", global_common_xml, scenario_common_xml).getOrElse(warm_up_count)
      sample_interval = getIntValueCascade("sample_interval", global_common_xml, scenario_common_xml).getOrElse(sample_interval)

      multi_benchmark(names = names, drain = drain, sc = sc, blocking = blocking) { scenarios =>
        for (scenario <- scenarios) {
            val clients_xml = (scenario_xml \ "clients").filter( clients => (clients \ "@name").text == scenario.name )

            scenario.login = getStringValueCascade("login", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.login)
            scenario.passcode = getStringValueCascade("passcode", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.passcode)
            scenario.host = getStringValueCascade("host", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.host)
            scenario.port = getIntValueCascade("port", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.port)
            scenario.producers = getIntValueCascade("producers", global_common_xml, scenario_common_xml, clients_xml).getOrElse(0)
            scenario.consumers = getIntValueCascade("consumers", global_common_xml, scenario_common_xml, clients_xml).getOrElse(0)
            scenario.destination_type = getStringValueCascade("destination_type", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.destination_type)
            scenario.destination_name = getStringValueCascade("destination_name", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.destination_name)

            scenario.consumer_prefix = getStringValueCascade("consumer_prefix", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.consumer_prefix)
            scenario.queue_prefix = getStringValueCascade("queue_prefix", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.queue_prefix)
            scenario.topic_prefix = getStringValueCascade("topic_prefix", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.topic_prefix)
            scenario.message_size = getIntValueCascade("message_size", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.message_size)
            scenario.content_length = getBooleanValueCascade("content_length", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.content_length)
            scenario.drain_timeout = getIntValueCascade("drain_timeout", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.drain_timeout.toInt).toLong
            scenario.persistent = getBooleanValueCascade("persistent", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.persistent)
            scenario.durable = getBooleanValueCascade("durable", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.durable)
            scenario.sync_send = getBooleanValueCascade("sync_send", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.sync_send)
            scenario.ack = getStringValueCascade("ack", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.ack)
            scenario.messages_per_connection = getIntValueCascade("messages_per_connection", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.messages_per_connection.toInt).toLong
            scenario.producers_per_sample = getIntValueCascade("producers_per_sample", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.producers_per_sample)
            scenario.consumers_per_sample = getIntValueCascade("consumers_per_sample", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.consumers_per_sample)
            scenario.selector = getStringValueCascade("selector", global_common_xml, scenario_common_xml, clients_xml).getOrElse(scenario.selector)

            scenario.producer_sleep = getPropertySleep("producer_sleep", clients_xml)
            scenario.consumer_sleep = getPropertySleep("consumer_sleep", clients_xml)
        }
      }
    }
  }
}
