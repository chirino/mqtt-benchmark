/**
 * Copyright (C) 2009-2010 the original author or authors.
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

import org.osgi.service.command.CommandSession
import java.io.{PrintStream, FileOutputStream, File}
import org.apache.felix.gogo.commands.basic.DefaultActionPreparator
import collection.JavaConversions
import java.lang.{String, Class}
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
      if( p.prepare(action, session, JavaConversions.asList(args.toList)) ) {
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

  @option(name = "--host", description = "server host name")
  var host = "127.0.0.1"
  @option(name = "--port", description = "server port")
  var port = 61613
  @option(name = "--sample-count", description = "number of samples to take")
  var sample_count = 8
  @option(name = "--warm-up-count", description = "number of warm up samples to ignore")
  var warm_up_count = 3

  @argument(index=0, name = "name", description = "name of server being benchmarked", required=true)
  var out:String = _

  @option(name = "--enable-topics", description = "enable benchmarking the topic cases")
  var enable_topics = true
  @option(name = "--enable-queues", description = "enable benchmarking the queue cases")
  var enable_queues = true
  @option(name = "--enable-peristence", description = "enable benchmarking the peristent cases")
  var enable_peristence = true

  @option(name = "--scenario-producer-throughput", description = "")
  var scenario_producer_throughput = true
  @option(name = "--scenario-queue-loading", description = "")
  var scenario_queue_loading = true
  @option(name = "--scenario-partitioned", description = "")
  var scenario_partitioned = true
  @option(name = "--scenario-fan-in-out", description = "")
  var scenario_fan_in_out = true
  @option(name = "--scenario-durable-subs", description = "")
  var scenario_durable_subs = false

  var samples = HashMap[String, SampleSet]()


  def json_format(value:Option[List[Long]]):String = {
    value.map { json_format _ }.getOrElse("null")
  }

  def json_format(value:List[Long]):String = {
    "[ "+value.mkString(",")+" ]"
  }

  def execute(session: CommandSession): AnyRef = {
    val file = new File(out+".json")
    val os = new PrintStream(new FileOutputStream(file))
    println("===================================================================")
    println("Benchmarking Stomp Server at: %s:%d".format(host, port))
    println("===================================================================")

    run_benchmarks

    os.println("{")
    os.println(samples.map { case (name, sample)=>
      """|  "p_%s": %s,
         |  "c_%s": %s""".stripMargin.format(name, json_format(sample.producer_samples), name, json_format(sample.consumer_samples))
    }.mkString(",\n"))
    os.println("}")

    os.close
    println("===================================================================")
    println("Stored: "+file)
    println("===================================================================")
    null
  }

  protected def create_generator = new LoadGenerator

  private def benchmark(name:String, drain:Boolean=true, sc:Int=sample_count)(init_func: (LoadGenerator)=>Unit ) = {
    val generator = create_generator
    generator.sample_interval = 1000
    generator.host = host
    generator.port = port
    init_func(generator)

    generator.destination_name = if( generator.destination_type == "queue" )
       "loadq"
    else
       "loadt"

    print("case  : "+name)
    val sample_set = generator.with_load {
      for( i <- 0 until warm_up_count ) {
        Thread.sleep(generator.sample_interval)
        print(".")
      }
      generator.collect_samples(sc)
    }

    sample_set.producer_samples.foreach(x=> println("producer samples: "+json_format(x)) )
    sample_set.consumer_samples.foreach(x=> println("consumer samples: "+json_format(x)) )

    samples += name -> sample_set
    if( drain) {
      generator.drain
    }
  }

  private def mlabel(size:Int) = if((size%1024)==0) (size/1024)+"k" else size+"b"
  private def plabel(persistent:Boolean) = if(persistent) "p" else ""
  private def slabel(sync_send:Boolean) = if(sync_send) "" else "a"

  def run_benchmarks = {


    val persistence_values = if (enable_peristence) {
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

    // Benchmark for the queue parallel load use cases
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
            g.destination_count = consumers
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

    if( enable_peristence && scenario_queue_loading ) {
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
}