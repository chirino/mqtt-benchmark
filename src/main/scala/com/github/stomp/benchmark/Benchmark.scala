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
import scala.xml.{XML, NodeSeq}
import scala.util.control.Exception.catching

import java.io.{PrintStream, FileOutputStream, File}
import collection.JavaConversions
import java.lang.{String, Class}

import org.apache.felix.gogo.commands.basic.DefaultActionPreparator
import org.apache.felix.service.command.CommandSession
import org.apache.felix.gogo.commands.{CommandException, Action, Option => option, Argument => argument, Command => command}

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
  
  // Helpers needed to diferenciate between default value and not set on the CLI value for primitive values
  def toIntOption(x: java.lang.Integer): Option[Int] = if(x!=null) Some(x.intValue) else None
  def toLongOption(x: java.lang.Long): Option[Long] = if(x!=null) Some(x.longValue) else None
  def toBooleanOption(x: java.lang.Boolean): Option[Boolean] = if(x!=null) Some(x.booleanValue) else None

  @option(name = "--broker_name", description = "The name of the broker being benchmarked.")
  var cl_broker_name:String = _
  var broker_name = FlexibleProperty(default = None, high_priority = () => Option(cl_broker_name))

  @option(name = "--host", description = "server host name")
  var cl_host: String = _
  var host = FlexibleProperty(default = Some("127.0.0.1"), high_priority = () => Option(cl_host))
  @option(name = "--port", description = "server port")
  var cl_port: java.lang.Integer = _
  var port = FlexibleProperty(default = Some(61613), high_priority = () => toIntOption(cl_port))

  @option(name = "--login", description = "login name to connect with")
  var cl_login:String = _
  var login = FlexibleProperty(default = None, high_priority = () => Option(cl_login))
  @option(name = "--passcode", description = "passcode to connect with")
  var cl_passcode:String = _
  var passcode = FlexibleProperty(default = None, high_priority = () => Option(cl_passcode))

  @option(name = "--sample-count", description = "number of samples to take")
  var cl_sample_count: java.lang.Integer = _
  var sample_count = FlexibleProperty(default = Some(15), high_priority = () => toIntOption(cl_sample_count))
  @option(name = "--sample-interval", description = "number of milli seconds that data is collected.")
  var cl_sample_interval: java.lang.Integer = _
  var sample_interval = FlexibleProperty(default = Some(1000), high_priority = () => toIntOption(cl_sample_interval))
  @option(name = "--warm-up-count", description = "number of warm up samples to ignore")
  var cl_warm_up_count: java.lang.Integer = _
  var warm_up_count = FlexibleProperty(default = Some(3), high_priority = () => toIntOption(cl_warm_up_count))

  @argument(index=0, name = "out", description = "The file to store benchmark metrics in", required=true)
  var cl_out: File = _
  var out = FlexibleProperty(default = None, high_priority = () => Option(cl_out))

  @option(name = "--enable-topics", description = "enable benchmarking the topic scenarios")
  var cl_enable_topics: java.lang.Boolean = _
  var enable_topics = FlexibleProperty(default = Some(true), high_priority = () => toBooleanOption(cl_enable_topics))
  @option(name = "--enable-queues", description = "enable benchmarking the queue scenarios")
  var cl_enable_queues: java.lang.Boolean = _
  var enable_queues = FlexibleProperty(default = Some(true), high_priority = () => toBooleanOption(cl_enable_queues))
  @option(name = "--enable-persistent", description = "enable benchmarking the persistent scenarios")
  var cl_enable_persistence: java.lang.Boolean = _
  var enable_persistence = FlexibleProperty(default = Some(true), high_priority = () => toBooleanOption(cl_enable_persistence))

  @option(name = "--scenario-connection-scale", description = "enable the connection scale scenarios")
  var cl_scenario_connection_scale: java.lang.Boolean = _
  var scenario_connection_scale = FlexibleProperty(default = Some(false), high_priority = () => toBooleanOption(cl_scenario_connection_scale))

  @option(name = "--scenario-connection-scale-rate", description = "How many connection to add after each sample")
  var cl_scenario_connection_scale_rate: java.lang.Integer = _
  var scenario_connection_scale_rate = FlexibleProperty(default = Some(50), high_priority = () => toIntOption(cl_scenario_connection_scale_rate))
  @option(name = "--scenario-connection-max-samples", description = "The maximum number of sample to take in the connection scale scenario")
  var cl_scenario_connection_scale_max_samples: java.lang.Integer = _
  var scenario_connection_scale_max_samples = FlexibleProperty(default = Some(100), high_priority = () => toIntOption(cl_scenario_connection_scale_max_samples))

  @option(name = "--scenario-producer-throughput", description = "enable the producer throughput scenarios")
  var cl_scenario_producer_throughput: java.lang.Boolean = _
  var scenario_producer_throughput = FlexibleProperty(default = Some(true), high_priority = () => toBooleanOption(cl_scenario_producer_throughput))
  @option(name = "--scenario-queue-loading", description = "enable the queue load/unload scenarios")
  var cl_scenario_queue_loading: java.lang.Boolean = _
  var scenario_queue_loading = FlexibleProperty(default = Some(true), high_priority = () => toBooleanOption(cl_scenario_queue_loading))
  @option(name = "--scenario-partitioned", description = "enable the partitioned load scenarios")
  var cl_scenario_partitioned: java.lang.Boolean = _
  var scenario_partitioned = FlexibleProperty(default = Some(true), high_priority = () => toBooleanOption(cl_scenario_partitioned))
  @option(name = "--scenario-fan-in-out", description = "enable the fan in/fan out scenarios")
  var cl_scenario_fan_in_out: java.lang.Boolean = _
  var scenario_fan_in_out = FlexibleProperty(default = Some(true), high_priority = () => toBooleanOption(cl_scenario_fan_in_out))
  @option(name = "--scenario-durable-subs", description = "enable the durable subscription scenarios")
  var cl_scenario_durable_subs: java.lang.Boolean = _
  var scenario_durable_subs = FlexibleProperty(default = Some(false), high_priority = () => toBooleanOption(cl_scenario_durable_subs))
  @option(name = "--scenario-selector", description = "enable the selector based scenarios")
  var cl_scenario_selector: java.lang.Boolean = _
  var scenario_selector = FlexibleProperty(default = Some(false), high_priority = () => toBooleanOption(cl_scenario_selector))
  @option(name = "--scenario-slow-consumer", description = "enable the slow consumer scenarios")
  var cl_scenario_slow_consumer: java.lang.Boolean = _
  var scenario_slow_consumer = FlexibleProperty(default = Some(false), high_priority = () => toBooleanOption(cl_scenario_slow_consumer))

  @option(name = "--scenario-file", description = "uses a scenario defined in an XML file instead of the default ones")
  var cl_scenario_file: File = _
  var scenario_file = FlexibleProperty(default = None, high_priority = () => Option(cl_scenario_file))

  @option(name = "--queue-prefix", description = "prefix used for queue destiantion names.")
  var cl_queue_prefix: String = _
  var queue_prefix = FlexibleProperty(default = Some("/queue/"), high_priority = () => Option(cl_queue_prefix))
  @option(name = "--topic-prefix", description = "prefix used for topic destiantion names.")
  var cl_topic_prefix: String = _
  var topic_prefix = FlexibleProperty(default = Some("/topic/"), high_priority = () => Option(cl_topic_prefix))
  @option(name = "--blocking-io", description = "Should the clients use blocking io.")
  var cl_blocking_io: java.lang.Boolean = _
  var blocking_io = FlexibleProperty(default = Some(false), high_priority = () => toBooleanOption(cl_blocking_io))
  @option(name = "--drain-timeout", description = "How long to wait for a drain to timeout in ms.")
  var cl_drain_timeout: java.lang.Long = _
  var drain_timeout = FlexibleProperty(default = Some(3000L), high_priority = () => toLongOption(cl_drain_timeout))

  @option(name = "--persistent-header", description = "The header to set on persistent messages to make them persistent.")
  var cl_persistent_header: String = _
  var persistent_header = FlexibleProperty(default = Some("persistent:true"), high_priority = () => Option(cl_persistent_header))

  @option(name = "--messages-per-connection", description = "The number of messages that are sent before the client reconnect.")
  var cl_messages_per_connection: java.lang.Long = _
  var messages_per_connection = FlexibleProperty(default = Some(-1L), high_priority = () => toLongOption(cl_messages_per_connection))

  @option(name = "--display-errors", description = "Should errors get dumped to the screen when they occur?")
  var cl_display_errors: java.lang.Boolean = _
  var display_errors = FlexibleProperty(default = Some(false), high_priority = () => toBooleanOption(cl_display_errors))
  
  @option(name = "--new-json", description = "Generate the new json format including more information")
  var cl_new_json: java.lang.Boolean = _
  var new_json = FlexibleProperty(default = Some(false), high_priority = () => toBooleanOption(cl_new_json))

  var samples = HashMap[String, List[(Long,Long)]]()
  var benchmark_results = new BenchmarkResults()



  def json_format(value:Option[List[String]]):String = {
    value.map { json_format _ }.getOrElse("null")
  }

  def json_format(value:List[String]):String = {
    "[ "+value.mkString(",")+" ]"
  }

  def execute(session: CommandSession): AnyRef = {
    
    FlexibleProperty.init_all()
    
    broker_name.set_default(out.get.getName.stripSuffix(".json"))

    println("===================================================================")
    println("Benchmarking %s at: %s:%d".format(broker_name.get, host.get, port.get))
    println("===================================================================")


    if( scenario_file.getOption.isEmpty ) {
      run_benchmarks
    } else {
      load_and_run_benchmarks
    }
    
    out.get.getParentFile.mkdirs
    val os = new PrintStream(new FileOutputStream(out.get))
    
    if( scenario_file.getOption.isEmpty || (!new_json.get)) {
      os.println("{")
      os.println("""  "benchmark_settings": {""")
      os.println("""    "broker_name": "%s",""".format(broker_name.get))
      os.println("""    "host": "%s",""".format(host.get))
      os.println("""    "port": %d,""".format(port.get))
      os.println("""    "sample_count": %d,""".format(sample_count.get))
      os.println("""    "sample_interval": %d,""".format(sample_interval.get))
      os.println("""    "warm_up_count": %d,""".format(warm_up_count.get))
      os.println("""    "scenario_connection_scale_rate": %d""".format(scenario_connection_scale_rate.get))
      os.println("""  },""")
      os.println(samples.map { case (name, sample)=>
        """  "%s": %s""".format(name, json_format(sample.map(x=> "[%d,%d]".format(x._1,x._2))))
      }.mkString(",\n"))
      os.println("}")
    } else {
      os.print(benchmark_results.to_json())
    }

    os.close
    println("===================================================================")
    println("Stored: "+out.get)
    println("===================================================================")
    null
  }

  private def benchmark(name:String, drain:Boolean=true, sc:Int=sample_count.get, is_done: (List[Scenario])=>Boolean = null, blocking:Boolean=blocking_io.get)(init_func: (Scenario)=>Unit ):Unit = {
    multi_benchmark(List(name), drain, sc, is_done, blocking) { scenarios =>
      init_func(scenarios.head)
    }
  }

  private def multi_benchmark(names:List[String], drain:Boolean=true, sc:Int=sample_count.get, is_done: (List[Scenario])=>Boolean = null, blocking:Boolean=blocking_io.get, results: HashMap[String, ClientResults] = HashMap.empty)(init_func: (List[Scenario])=>Unit ):Unit = {
    val scenarios:List[Scenario] = names.map { name=>
      val scenario = if(blocking) new BlockingScenario else new NonBlockingScenario
      scenario.name = name
      scenario.sample_interval = sample_interval.get
      scenario.host = host.get
      scenario.port = port.get
      scenario.login = login.getOption
      scenario.passcode = passcode.getOption
      scenario.queue_prefix = queue_prefix.get
      scenario.topic_prefix = topic_prefix.get
      scenario.drain_timeout = drain_timeout.get
      scenario.persistent_header = persistent_header.get
      scenario.display_errors = display_errors.get
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
      for( i <- 0 until warm_up_count.get ) {
        Thread.sleep(sample_interval.get)
        print(".")
      }
      scenarios.foreach(_.collection_start)

      if( is_done!=null ) {
        while( !is_done(scenarios) ) {
          print(".")
          Thread.sleep(sample_interval.get)
          scenarios.foreach(_.collection_sample)
        }

      } else {
        var remaining = sc
        while( remaining > 0 ) {
          print(".")
          Thread.sleep(sample_interval.get)
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
            
            if (results.contains(scenario.name)) {
              // Copy the scenario results to the results structure
              val client_results = results(scenario.name)
              client_results.producers_data = collected.getOrElse("p_"+scenario.name, Nil)
              client_results.consumers_data = collected.getOrElse("c_"+scenario.name, Nil)
              client_results.error_data = collected.getOrElse("e_"+scenario.name, Nil)
              if ( client_results.error_data.foldLeft(0L)((a,x) => a + x._2) == 0 ) {
                // If there are no errors, we keep an empty list
                client_results.error_data = Nil
              }
            }
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


    val persistence_values = if (enable_persistence.get) {
      List(false, true)
    } else {
      List(false)
    }

    var destination_types = List[String]()
    if( enable_queues.get ) {
      destination_types ::= "queue"
    }
    if( enable_topics.get ) {
      destination_types ::= "topic"
    }


    if(scenario_connection_scale.get ) {

      for( messages_per_connection <- List(-1)) {

        /** this test keeps going until we start getting a large number of errors */
        var remaining = scenario_connection_scale_max_samples.get
        def is_done(scenarios:List[Scenario]):Boolean = {
          remaining -= 1;
          var errors = 0L
          scenarios.foreach( _.error_samples.lastOption.foreach( errors+= _._2 ) )
          return errors >= scenario_connection_scale_rate.get || remaining <= 0
        }

        benchmark("20b_Xa%s_1queue_1".format(messages_per_connection)+"m", true, 0, is_done, false) { scenario=>
          scenario.message_size = 20
          scenario.producers = 0
          scenario.messages_per_connection = messages_per_connection
          scenario.producers_per_sample = scenario_connection_scale_rate.get
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
    if(scenario_slow_consumer.get) {
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
    if( scenario_selector.get ) {
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

    if( enable_topics.get && scenario_producer_throughput.get ) {
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
    if( scenario_partitioned.get ) {

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

    if( scenario_fan_in_out.get  ) {
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

    if( enable_topics.get && scenario_durable_subs.get) {
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

    if( enable_persistence.get && scenario_queue_loading.get ) {
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
    
    var producers = FlexibleProperty[Int]() 
    var consumers = FlexibleProperty[Int]()
    var destination_type = FlexibleProperty[String]()
    var destination_name = FlexibleProperty[String]()
    var consumer_prefix = FlexibleProperty[String]()
    
    var message_size = FlexibleProperty[Int]()
    var content_length = FlexibleProperty[Boolean]()
    
    var drain = FlexibleProperty[Boolean](default = Some(false))
    var persistent = FlexibleProperty[Boolean]()
    var durable = FlexibleProperty[Boolean]()
    var sync_send = FlexibleProperty[Boolean]()

    var ack = FlexibleProperty[String]()
    
    var producers_per_sample = FlexibleProperty[Int]()
    var consumers_per_sample = FlexibleProperty[Int]()

    var headers = FlexibleProperty[Array[Array[String]]](default = Some(Array[Array[String]]()))
    var selector = FlexibleProperty[String]()
    
    var producer_sleep = FlexibleProperty[sleepFunction](default = Some(new sleepFunction { override def apply() = 0 }))
    var consumer_sleep = FlexibleProperty[sleepFunction](default = Some(new sleepFunction { override def apply() = 0 }))

    def getStringValue(property_name: String, ns_xml: NodeSeq, vars: Map[String, String] = Map.empty[String, String]): Option[String] = {
      val value = ns_xml \ property_name
      if (value.length == 1) Some(substituteVariables(value.text.trim, vars)) else None
    }

    def getIntValue(property_name: String, ns_xml: NodeSeq, vars: Map[String, String] = Map.empty[String, String]): Option[Int] = {
      val value = getStringValue(property_name, ns_xml, vars)
      try {
        value.map((x:String) => x.toInt)
      } catch {
        case e: NumberFormatException => throw new Exception("Error in XML scenario, not integer provided: " + value.getOrElse("\"\""))
      }
    }

    def getBooleanValue(property_name: String, ns_xml: NodeSeq, vars: Map[String, String] = Map.empty[String, String]): Option[Boolean] = {
      val value = getStringValue(property_name, ns_xml, vars)
      try {
        value.map((x:String) => x.toBoolean)
      } catch {
        case e: NumberFormatException => throw new Exception("Error in XML scenario, not boolean provided: " + value.getOrElse("\"\""))
      }
    }

    def getPropertySleep(property_name: String, clients_xml: NodeSeq, vars: Map[String, String] = Map.empty[String, String]): Option[sleepFunction] = { 
      val format_catcher = catching(classOf[NumberFormatException])
      val property_sleep_nodeset = clients_xml \ property_name
      val property_sleep_value: Option[Int] = format_catcher.opt(property_sleep_nodeset.text.toInt)
      if (property_sleep_nodeset.length == 1 && property_sleep_value.isDefined) {
        Some(new sleepFunction { override def apply() = property_sleep_value.get })
      } else if ((property_sleep_nodeset \ "range").length > 0) {
        Some(new sleepFunction {
          var ranges: List[Tuple2[Int, (Long) => Int]] = Nil 
          for (range_node <- property_sleep_nodeset \ "range") {
            val range_value =  format_catcher.opt(range_node.text.toInt)
            val range_end =  getIntValue("@end", range_node, vars)
            val range_burst = range_node \ "burst"
            if (range_node.text == "sleep") {
              ranges :+= Tuple2(range_end.get, (time: Long) => SLEEP)
            } else if (range_value.isDefined) {
              ranges :+= Tuple2(range_end.get, (time: Long) => range_value.get)
            } else if (range_burst.length == 1) {
              var (slow, fast, duration, period) = (100, 0 , 1, 10)
              slow = getIntValue("@slow", range_burst, vars).getOrElse(slow)
              fast = getIntValue("@fast", range_burst, vars).getOrElse(fast)
              duration = getIntValue("@duration", range_burst, vars).getOrElse(duration)
              period = getIntValue("@period", range_burst, vars).getOrElse(period)
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
        })
      } else {
        None
      }
    }
    
    def getPropertyHeaders(property_name: String, ns_xml: NodeSeq, vars: Map[String, String] = Map.empty[String, String]): Option[Array[Array[String]]] = {
      val headers = ns_xml \ property_name
      if (headers.length == 1) {
        Some((headers(0) \ "client_type") map { client_type =>
          (client_type \ "header") map { header =>
            substituteVariables(header.text.trim, vars)
          } toArray
        } toArray)
      } else {
        None
      }
      
      //Some() else None
    }
    
    def push_properties(node: NodeSeq, vars: Map[String, String] = Map.empty[String, String]) {
      sample_count.push(getIntValue("sample_count", node, vars))
      drain.push(getBooleanValue("drain", node, vars))
      blocking_io.push(getBooleanValue("blocking_io", node, vars))
      warm_up_count.push(getIntValue("warm_up_count", node, vars))
      sample_interval.push(getIntValue("sample_interval", node, vars))
      
      login.push(getStringValue("login", node, vars))
      passcode.push(getStringValue("passcode", node, vars))
      host.push(getStringValue("host", node, vars))
      port.push(getIntValue("port", node, vars))
      producers.push(getIntValue("producers", node, vars))
      consumers.push(getIntValue("consumers", node, vars))
      destination_type.push(getStringValue("destination_type", node, vars))
      destination_name.push(getStringValue("destination_name", node, vars))

      consumer_prefix.push(getStringValue("consumer_prefix", node, vars))
      queue_prefix.push(getStringValue("queue_prefix", node, vars))
      topic_prefix.push(getStringValue("topic_prefix", node, vars))
      message_size.push(getIntValue("message_size", node, vars))
      content_length.push(getBooleanValue("content_length", node, vars))
      drain_timeout.push(getIntValue("drain_timeout", node, vars).map(_.toLong))
      persistent.push(getBooleanValue("persistent", node, vars))
      durable.push(getBooleanValue("durable", node, vars))
      sync_send.push(getBooleanValue("sync_send", node, vars))
      ack.push(getStringValue("ack", node, vars))
      messages_per_connection.push(getIntValue("messages_per_connection", node, vars).map(_.toLong))
      producers_per_sample.push(getIntValue("producers_per_sample", node, vars))
      consumers_per_sample.push(getIntValue("consumers_per_sample", node, vars))
      
      headers.push(getPropertyHeaders("headers", node, vars))
      selector.push(getStringValue("selector", node, vars))

      producer_sleep.push(getPropertySleep("producer_sleep", node, vars))
      consumer_sleep.push(getPropertySleep("consumer_sleep", node, vars))
    }
    
    def pop_properties() {
      sample_count.pop()
      drain.pop()
      blocking_io.pop()
      warm_up_count.pop()
      sample_interval.pop()
      
      login.pop()
      passcode.pop()
      host.pop()
      port.pop()
      producers.pop()
      consumers.pop()
      destination_type.pop()
      destination_name.pop()

      consumer_prefix.pop()
      queue_prefix.pop()
      topic_prefix.pop()
      message_size.pop()
      content_length.pop()
      drain_timeout.pop()
      persistent.pop()
      durable.pop()
      sync_send.pop()
      ack.pop()
      messages_per_connection.pop()
      producers_per_sample.pop()
      consumers_per_sample.pop()
      
      headers.pop()
      selector.pop()
      
      producer_sleep.pop()
      consumer_sleep.pop()
    }
    
    /** This fucntion generates a list of tuples, each of them containing the
      * variables to be replaced in the scenario template and the SingleScenarioResults
      * object that will keep the results for this scenario.
      * 
      * The list is generated from a list of variables and posible values, and
      * the parent of the ScenarioResults tree structure. The ScenarioResults
      * objects are linked properly. */
    def combineLoopVariables(loop_vars: List[LoopVariable], parent: LoopScenarioResults): List[(Map[String, String], SingleScenarioResults)] = loop_vars match {
      case LoopVariable(name, _, values) :: Nil => values map { v =>
        var scenario_results = new SingleScenarioResults()
        parent.scenarios :+= (v.label, scenario_results)
        (Map(name -> v.value), scenario_results)
      }
      case LoopVariable(name, _, values) :: tail => {
        values flatMap { lv =>
          var scenario_results = new LoopScenarioResults()
          parent.scenarios :+= (lv.label, scenario_results)
          val combined_tail = combineLoopVariables(tail, scenario_results)
          combined_tail map { vv => (vv._1 + (name -> lv.value), vv._2) } 
        }
      }
      case _ => Nil
    }
    
    def substituteVariables(orig: String, vars: Map[String, String]): String = {
      var modified = orig
      for ((key, value) <- vars) {
        modified = modified.replaceAll("\\$\\{"+key+"\\}", value)
      }
      modified
    }

    val scenarios_xml = XML.loadFile(scenario_file.get)
    
    val global_common_xml = scenarios_xml \ "common"
    push_properties(global_common_xml)
    
    benchmark_results.description = getStringValue("description", scenarios_xml).getOrElse("")
    
    for (group_xml <- scenarios_xml \ "group") {
      
      val group_common_xml = group_xml \ "common"
      push_properties(group_common_xml)
      
      var group_results = new GroupResults()
      benchmark_results.groups :+= group_results
      group_results.name = getStringValue("@name", group_xml).get
      group_results.description = getStringValue("description", group_xml).getOrElse("")
      
      // Parse the loop variables
      var loop_vars = (group_xml \ "loop" \ "var") map { var_xml =>
        val values = (var_xml \ "value") map { value_xml =>
          val value = value_xml.text
          var label = (value_xml \ "@label").text
          label = if (label == "") value else label // If there is no label, we use the value
          val description = (value_xml \ "@description").text
          LoopValue(value, label, description)
        } toList 
        val name = (var_xml \ "@name").text
        var label = (var_xml \ "@label").text
        label = if (label == "") name else label // If there is no label, we use the name
        LoopVariable(name, label, values)
      } toList
      
      group_results.loop = loop_vars
      
      for (scenario_xml <- group_xml \ "scenario") {
        
        // If there are no loop variables, we just have one empty map and a SingleScenarioResults
        // Otherwise, we combine the diferent values of the loop variables and generate a ScenarioResults tree
        val variables_and_result_list = if (loop_vars.isEmpty) {
          val scenario_results = new SingleScenarioResults()
          group_results.scenarios :+= scenario_results
          List((Map.empty[String, String], scenario_results)) 
        } else {
          val scenario_results = new LoopScenarioResults()
          group_results.scenarios :+= scenario_results
          combineLoopVariables(loop_vars, scenario_results)
        }
        
        for (variables_and_result <- variables_and_result_list) {
          
          val vars = variables_and_result._1
          val scenario_results = variables_and_result._2
          
          val scenario_common_xml = scenario_xml \ "common"
          push_properties(scenario_common_xml, vars)
          
          scenario_results.name = substituteVariables(getStringValue("@name", scenario_xml, vars).get, vars)
          
          val names = (scenario_xml \ "clients").map( client => substituteVariables((client \ "@name").text, vars) ).toList
          
          var scenario_client_results = new HashMap[String, ClientResults]()
          
          multi_benchmark(names = names, drain = drain.get, results = scenario_client_results) { scenarios =>
            for (scenario <- scenarios) {
              val clients_xml = (scenario_xml \ "clients").filter( clients => substituteVariables((clients \ "@name").text, vars) == scenario.name )
              push_properties(clients_xml, vars)
              
              var client_results = new ClientResults()
              scenario_results.clients :+= client_results
              client_results.name = getStringValue("@name", clients_xml, vars).get
              
              scenario_client_results += (scenario.name -> client_results) // To be able to fill the results from multi_benchmark
              
              // Load all the properties in the scenario
              scenario.login = login.getOption()
              scenario.passcode = passcode.getOption()
              scenario.host = host.getOrElse(scenario.host)
              scenario.port = port.getOrElse(scenario.port)
              scenario.producers = producers.getOrElse(0)
              scenario.consumers = consumers.getOrElse(0)
              scenario.destination_type = destination_type.getOrElse(scenario.destination_type)
              scenario.destination_name = destination_name.getOrElse(scenario.destination_name)
    
              scenario.consumer_prefix = consumer_prefix.getOrElse(scenario.consumer_prefix)
              scenario.queue_prefix = queue_prefix.getOrElse(scenario.queue_prefix)
              scenario.topic_prefix = topic_prefix.getOrElse(scenario.topic_prefix)
              scenario.message_size = message_size.getOrElse(scenario.message_size)
              scenario.content_length = content_length.getOrElse(scenario.content_length)
              scenario.drain_timeout = drain_timeout.getOrElse(scenario.drain_timeout)
              scenario.persistent = persistent.getOrElse(scenario.persistent)
              scenario.durable = durable.getOrElse(scenario.durable)
              scenario.sync_send = sync_send.getOrElse(scenario.sync_send)
              scenario.ack = ack.getOrElse(scenario.ack)
              scenario.messages_per_connection = messages_per_connection.getOrElse(scenario.messages_per_connection)
              scenario.producers_per_sample = producers_per_sample.getOrElse(scenario.producers_per_sample)
              scenario.consumers_per_sample = consumers_per_sample.getOrElse(scenario.consumers_per_sample)
              
              scenario.headers = headers.get
              scenario.selector = selector.getOrElse(scenario.selector)
                
              scenario.producer_sleep = producer_sleep.get
              scenario.consumer_sleep = consumer_sleep.get
              
              // Copy the scenario settings to the results
              client_results.settings = scenario.settings
              
              pop_properties()
            }
          }
          pop_properties()
        }
      }
      pop_properties()
    }
    pop_properties()
  }
}
