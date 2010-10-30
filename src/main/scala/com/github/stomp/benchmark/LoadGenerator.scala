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

import java.util.concurrent.atomic._
import java.util.concurrent.TimeUnit._
import java.net._
import java.io._
import scala.collection.mutable.ListBuffer

case class SampleSet(producer_samples:Option[List[Long]], consumer_samples:Option[List[Long]])

//object LoadGenerator {
//  def main(args:Array[String]) = {
//    val g = new LoadGenerator()
//    g.run
//  }
//}

/**
 * Simulates load on the a stomp broker.
 */
class LoadGenerator {

  object Constants {
    val MESSAGE_ID:Array[Byte] = "message-id"
    val NEWLINE = '\n'.toByte
    val NANOS_PER_SECOND = NANOSECONDS.convert(1, SECONDS)
  }

  import Constants._

  implicit def toByteBuffer(value: String) = value.getBytes("UTF-8")

  var producer_sleep = 0
  var consumer_sleep = 0
  var producers = 1
  var consumers = 1
  var sample_interval = 5 * 1000
  var host = "127.0.0.1"
  var port = 61613
  var buffer_size = 64*1204
  var message_size = 1024
  var enable_content_length=true
  var persistent = false
  var sync_send = false
  var headers = List[String]()
  var ack = "auto"
  var selector:String = null
  var durable = false

  var destination_type = "queue"
  var destination_name = "load"
  var destination_count = 1

  val producer_counter = new AtomicLong()
  val consumer_counter = new AtomicLong()
  val done = new AtomicBoolean()

  def destination(i:Int) = "/"+destination_type+"/"+destination_name+"-"+(i%destination_count)

  def with_load[T](func: =>T ):T = {
    done.set(false)
    var producer_threads = List[ProducerThread]()
    for (i <- 0 until producers) {
      val thread = new ProducerThread(i)
      producer_threads ::= thread
      thread.start()
    }

    var consumer_threads = List[ConsumerThread]()
    for (i <- 0 until consumers) {
      val thread = new ConsumerThread(i)
      consumer_threads ::= thread
      thread.start()
    }

    try {
      func
    } finally {
      done.set(true)
      // wait for the threads to finish..
      for( thread <- consumer_threads ) {
        thread.shutdown
      }
      for( thread <- producer_threads ) {
        thread.shutdown
      }
    }
  }

  def drain = {
    if( destination_type=="queue" || durable==true ) {
      print("draining")
      consumer_counter.set(0)
      var consumer_threads = List[ConsumerThread]()
      for (i <- 0 until destination_count) {
        val thread = new ConsumerThread(i)
        consumer_threads ::= thread
        thread.start()
      }

      // Keep sleeping until we stop draining messages.
      try {
        Thread.sleep(1000);
        while( consumer_counter.getAndSet(0)!= 0 ) {
          print(".")
          Thread.sleep(500);
        }
      } finally {
        for( thread <- consumer_threads ) {
          thread.shutdown
        }
        println(".")
      }
    }
  }

  def collect_samples(count:Int):SampleSet = with_load {

    producer_counter.set(0)
    consumer_counter.set(0)

    val producer_samples = if( producers > 0 ) {
      Some(ListBuffer[Long]())
    } else {
      None
    }
    val consumer_samples = if( consumers > 0 ) {
      Some(ListBuffer[Long]())
    } else {
      None
    }

    Thread.currentThread.setPriority(Thread.MAX_PRIORITY)

    var remaining = count
    while( remaining > 0 ) {
      print(".")
      Thread.sleep(sample_interval)
      producer_samples.foreach( _ += producer_counter.getAndSet(0) )
      consumer_samples.foreach( _ += consumer_counter.getAndSet(0) )
      remaining-=1
    }
    println(".")

    Thread.currentThread.setPriority(Thread.NORM_PRIORITY)

    SampleSet(producer_samples.map(_.toList), consumer_samples.map(_.toList))
  }

  /**
   * A simple stomp client used for testing purposes
   */
  class StompClient {

    var socket:Socket = new Socket
    var out:OutputStream = null
    var in:InputStream = null
    val buffer_size = 64*1204

    def open(host: String, port: Int) = {
      socket = new Socket
      socket.connect(new InetSocketAddress(host, port))
      socket.setSoLinger(true, 0)
      out = new BufferedOutputStream(socket.getOutputStream, buffer_size)
      in = new BufferedInputStream(socket.getInputStream, buffer_size)
    }

    def close() = {
      socket.close
    }

    def write(data:Array[Byte]*) = {
      data.foreach(out.write(_))
      out.write(0)
      out.write('\n')
      out.flush
    }

    def skip():Unit = {
      var c = in.read
      while( c >= 0 ) {
        if( c==0 ) {
          return
        }
        c = in.read()
      }
      throw new EOFException()
    }

    def receive():Array[Byte] = {
      var start = true;
      val buffer = new ByteArrayOutputStream()
      var c = in.read
      while( c >= 0 ) {
        if( c==0 ) {
          return buffer.toByteArray
        }
        if( !start || c!= NEWLINE) {
          start = false
          buffer.write(c)
        }
        c = in.read()
      }
      throw new EOFException()
    }

    def receive(expect:Array[Byte]):Array[Byte] = {
      val rc = receive()
      if( !rc.startsWith(expect) ) {
        throw new Exception("Expected "+expect)
      }
      rc
    }

  }

  class ClientSupport extends Thread {

    var client:StompClient=new StompClient()

    def connect(proc: =>Unit ) = {
      try {
        client.open(host, port)
        client.write("""CONNECT

""")
        client.receive("CONNECTED")
        proc
      } catch {
        case e: Throwable =>
          if(!done.get) {
            println("failure occured: "+e)
            try {
              Thread.sleep(1000)
            } catch {
              case _ => // ignore
            }
          }
      } finally {
        try {
          client.close()
        } catch {
          case ignore: Throwable =>
        }
      }
    }

    def shutdown = {
      interrupt
      client.close
      join
    }

  }

  class ProducerThread(val id: Int) extends ClientSupport {
    val name: String = "producer " + id
    val content = ("SEND\n" +
              "destination:"+destination(id)+"\n"+
               { if(persistent) "persistent:true\n" else "" } +
               { if(sync_send) "receipt:xxx\n" else "" } +
               { headers.foldLeft("") { case (sum, v)=> sum+v+"\n" } } +
               { if(enable_content_length) "content-length:"+message_size+"\n" else "" } +
              "\n"+message(name)).getBytes("UTF-8")


    override def run() {
      while (!done.get) {
        connect {
          this.client=client
          var i =0
          while (!done.get) {
            client.write(content)
            if( sync_send ) {
              // waits for the reply..
              client.skip
            }
            producer_counter.incrementAndGet()
            if(producer_sleep > 0) {
              Thread.sleep(producer_sleep)
            }
            i += 1
          }
        }
      }
    }
  }

  def message(name:String) = {
    val buffer = new StringBuffer(message_size)
    buffer.append("Message from " + name+"\n")
    for( i <- buffer.length to message_size ) {
      buffer.append(('a'+(i%26)).toChar)
    }
    var rc = buffer.toString
    if( rc.length > message_size ) {
      rc.substring(0, message_size)
    } else {
      rc
    }
  }

  class ConsumerThread(val id: Int) extends ClientSupport {
    val name: String = "producer " + id

    override def run() {
      while (!done.get) {
        connect {
          client.write(
            "SUBSCRIBE\n" +
             (if(!durable) {""} else {"id:durable:mysub-"+id+"\n"}) +
             (if(selector==null) {""} else {"selector: "+selector+"\n"}) +
             "ack:"+ack+"\n"+
             "destination:"+destination(id)+"\n"+
             "\n")

          receive_loop
        }
      }
    }


    def index_of(haystack:Array[Byte], needle:Array[Byte]):Int = {
      var i = 0
      while( haystack.length >= i+needle.length ) {
        if( haystack.startsWith(needle, i) ) {
          return i
        }
        i += 1
      }
      return -1
    }




    def receive_loop() = {
      val clientAck = ack == "client"
      while (!done.get) {
        if( clientAck ) {
          val msg = client.receive()
          val start = index_of(msg, MESSAGE_ID)
          assert( start >= 0 )
          val end = msg.indexOf("\n", start)
          val msgId = msg.slice(start+MESSAGE_ID.length+1, end)
          client.write("""
ACK
message-id:""", msgId,"""

""")

        } else {
          client.skip
        }
        consumer_counter.incrementAndGet()
        Thread.sleep(consumer_sleep)
      }
    }
  }

  def run() = {

    println("=======================")
    println("Press ENTER to shutdown")
    println("=======================")
    println("")

    with_load {

      // start a sampling thread...
      val sampleThread = new Thread() {
        override def run() = {

          def printRate(name: String, periodCount:Long, totalCount:Long, nanos: Long) = {
            val rate_per_second: java.lang.Float = ((1.0f * periodCount / nanos) * NANOS_PER_SECOND)
            println("%s rate: %,.3f per second, total: %,d".format(name, rate_per_second, totalCount))
          }

          try {
            var totalProducerCount = 0L
            var totalConsumerCount = 0L
            producer_counter.set(0)
            consumer_counter.set(0)
            var start = System.nanoTime()
            while( !done.get ) {
              Thread.sleep(sample_interval)
              val end = System.nanoTime()
              if( producers > 0 ) {
                val count = producer_counter.getAndSet(0)
                totalProducerCount += count
                printRate("Producer", count, totalProducerCount, end - start)
              }
              if( consumers > 0 ) {
                val count = consumer_counter.getAndSet(0)
                totalConsumerCount += count
                printRate("Consumer", count, totalConsumerCount, end - start)
              }
              start = end
            }
          } catch {
            case e:InterruptedException =>
          }
        }
      }
      sampleThread.start()

      System.in.read()
      done.set(true)

      sampleThread.interrupt
      sampleThread.join
    }

    println("=======================")
    println("Shutdown")
    println("=======================")

  }

  override def toString() = {
    "--------------------------------------\n"+
    "StompLoadClient Properties\n"+
    "--------------------------------------\n"+
    "host                  = "+host+"\n"+
    "port                  = "+port+"\n"+
    "destination_type      = "+destination_type+"\n"+
    "destination_count     = "+destination_count+"\n" +
    "destination_name      = "+destination_name+"\n" +
    "sample_interval       = "+sample_interval+"\n" +
    "\n"+
    "--- Producer Properties ---\n"+
    "producers             = "+producers+"\n"+
    "message_size          = "+message_size+"\n"+
    "persistent            = "+persistent+"\n"+
    "enable_sync_send      = "+sync_send+"\n"+
    "enable_content_length = "+enable_content_length+"\n"+
    "producer_sleep        = "+producer_sleep+"\n"+
    "headers               = "+headers+"\n"+
    "\n"+
    "--- Consumer Properties ---\n"+
    "consumers             = "+consumers+"\n"+
    "consumer_sleep        = "+consumer_sleep+"\n"+
    "ack                   = "+ack+"\n"+
    "selector              = "+selector+"\n"+
    "durable               = "+durable+"\n"+
    ""

  }

}
