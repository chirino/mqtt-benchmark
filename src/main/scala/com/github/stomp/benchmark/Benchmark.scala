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

object Benchmark {

  var host = "127.0.0.1"
  var port = 61613
  var sample_count = 5
  var samples = HashMap[String, SampleSet]()

  var benchmark_producer_throughput = false
  var benchmark_queue_loading = false
  var benchmark_durable_subs = false
  var benchmark_topics = false
  var benchmark_peristence = false
  var benchmark_queues = true

  private def mlabel(size:Int) = if((size%1024)==0) (size/1024)+"k" else size+"b"
  private def plabel(persistent:Boolean) = if(persistent) "p" else ""
  private def slabel(sync_send:Boolean) = if(sync_send) "" else "a"

  protected def create_generator = new LoadGenerator

  private def benchmark(name:String, drain:Boolean=true)(init_func: (LoadGenerator)=>Unit ) = {
    val generator = create_generator
    generator.sample_interval = 1000
    generator.host = host
    generator.port = port
    generator.destination_name = name
    init_func(generator)
    println("===================================================================")
    println("Benchmarking case: "+name)
    println("===================================================================")
    val sample_set = generator.collect_samples(sample_count)
    println("result: "+sample_set)
    samples += name -> sample_set
    if( drain) {
      println("draining...")
      generator.drain
    }
  }

  def main(args:Array[String]):Unit = {
    // TODO: parse args to change options.
    run
  }

  def run = {

    val persistence_values = if (benchmark_peristence) {
      List(false, true)
    } else {
      List(false)
    }

    if( benchmark_topics && benchmark_producer_throughput ) {
      // Benchmark for figuring out the max producer throughput
      for( size <- List(20, 1024, 1024 * 256) ) {
        val name = "%s_1_1topic_0".format(mlabel(size))
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

    // Benchmark for the general use cases
    if( benchmark_topics ) {
      for( size <- List(20, 1024, 1024 * 256)  ; load <- List(1, 5, 10); destination_type <- List("topic"); persistent <- persistence_values) {
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
    if( benchmark_queues ) {
      for( size <- List(20, 1024, 1024 * 256)  ; load <- List(1, 5, 10); destination_type <- List("queue"); persistent <- persistence_values) {
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

    if( benchmark_topics && benchmark_durable_subs) {
      // Benchmark for durable subscriptions on topics
      for( size <- List(1024)  ; load <- List(5, 20); persistent <- persistence_values) {
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

    if( benchmark_peristence && benchmark_queue_loading ) {
      for( sync_send <- List(false, true)) {
        val size = 20

        // Benchmark persistent queue loading
        val name = "%s_1%s%s_1queue_0".format(mlabel(size), plabel(true), slabel(sync_send))
        benchmark(name, false) { g=>
          g.message_size = 20
          g.producers = 1
          g.sync_send = sync_send
          g.persistent = true
          g.destination_count = 1
          g.destination_type = "queue"
          g.consumers = 0
          g.destination_name = "load_me_up"
        }

        // Benchmark persistent queue un-loading
        if(sync_send) {
          val name = "%s_1queue_1".format(mlabel(size))
          benchmark(name) { g=>
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