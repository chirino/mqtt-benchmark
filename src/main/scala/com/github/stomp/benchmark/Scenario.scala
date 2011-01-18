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
import org.fusesource.hawtdispatch._
import java.nio.channels.{SelectionKey, SocketChannel}
import java.nio.ByteBuffer
import java.util.concurrent.{CountDownLatch, TimeUnit}

object Scenario {
  
  val MESSAGE_ID:Array[Byte] = "message-id"
  val NEWLINE = '\n'.toByte
  val NANOS_PER_SECOND = NANOSECONDS.convert(1, SECONDS)
  
  implicit def toBytes(value: String):Array[Byte] = value.getBytes("UTF-8")

  def o[T](value:T):Option[T] = value match {
    case null => None
    case x => Some(x)
  }

//  def main(args:Array[String]):Unit = {
//    val s = new Scenario()
//    s.message_size = 20
//    s.run
//  }
}

trait Scenario {
  import Scenario._

  var login:String = _
  var passcode:String = _

  var producer_sleep = 0
  var consumer_sleep = 0
  var producers = 1
  var producers_per_sample = 0
  var producers_disconnect = false
  var consumers = 1
  var consumers_per_sample = 0
  var sample_interval = 1000
  var host = "127.0.0.1"
  var port = 61613
  var buffer_size = 32*1024
  var message_size = 1024
  var content_length=true
  var persistent = false
  var persistent_header = "persistent:true"
  var sync_send = false
  var headers = Array[Array[String]]()
  var ack = "auto"
  var selector:String = null
  var durable = false

  var destination_type = "queue"
  var destination_name = "load"
  var destination_count = 1

  val producer_counter = new AtomicLong()
  val consumer_counter = new AtomicLong()
  val error_counter = new AtomicLong()
  val done = new AtomicBoolean()

  var queue_prefix = "/queue/"
  var topic_prefix = "/topic/"
  var name = "custom"

  var drain_timeout = 2000L

  def run() = {
    print(toString)
    println("--------------------------------------")
    println("     Running: Press ENTER to stop")
    println("--------------------------------------")
    println("")

    with_load {

      // start a sampling client...
      val sample_thread = new Thread() {
        override def run() = {
          
          def print_rate(name: String, periodCount:Long, totalCount:Long, nanos: Long) = {

            val rate_per_second: java.lang.Float = ((1.0f * periodCount / nanos) * NANOS_PER_SECOND)
            println("%s total: %,d, rate: %,.3f per second".format(name, totalCount, rate_per_second))
          }

          try {
            var start = System.nanoTime
            var total_producer_count = 0L
            var total_consumer_count = 0L
            var total_error_count = 0L
            collection_start
            while( !done.get ) {
              Thread.sleep(sample_interval)
              val end = System.nanoTime
              collection_sample
              val samples = collection_end
              samples.get("p_custom").foreach { case List(count:Long) =>
                total_producer_count += count
                print_rate("Producer", count, total_producer_count, end - start)
              }
              samples.get("c_custom").foreach { case List(count:Long) =>
                total_consumer_count += count
                print_rate("Consumer", count, total_consumer_count, end - start)
              }
              samples.get("e_custom").foreach { case List(count:Long) =>
                if( count!= 0 ) {
                  total_error_count += count
                  print_rate("Error", count, total_error_count, end - start)
                }
              }
              start = end
            }
          } catch {
            case e:InterruptedException =>
          }
        }
      }
      sample_thread.start()

      System.in.read()
      done.set(true)

      sample_thread.interrupt
      sample_thread.join
    }

  }

  override def toString() = {
    "--------------------------------------\n"+
    "Scenario Settings\n"+
    "--------------------------------------\n"+
    "  host                  = "+host+"\n"+
    "  port                  = "+port+"\n"+
    "  destination_type      = "+destination_type+"\n"+
    "  queue_prefix          = "+queue_prefix+"\n"+
    "  topic_prefix          = "+topic_prefix+"\n"+
    "  destination_count     = "+destination_count+"\n" +
    "  destination_name      = "+destination_name+"\n" +
    "  sample_interval (ms)  = "+sample_interval+"\n" +
    "  \n"+
    "  --- Producer Properties ---\n"+
    "  producers             = "+producers+"\n"+
    "  message_size          = "+message_size+"\n"+
    "  persistent            = "+persistent+"\n"+
    "  sync_send             = "+sync_send+"\n"+
    "  content_length        = "+content_length+"\n"+
    "  producer_sleep (ms)   = "+producer_sleep+"\n"+
    "  headers               = "+headers+"\n"+
    "  \n"+
    "  --- Consumer Properties ---\n"+
    "  consumers             = "+consumers+"\n"+
    "  consumer_sleep (ms)   = "+consumer_sleep+"\n"+
    "  ack                   = "+ack+"\n"+
    "  selector              = "+selector+"\n"+
    "  durable               = "+durable+"\n"+
    ""

  }

  protected def destination(i:Int) = destination_type match {
    case "queue" => queue_prefix+destination_name+"-"+(i%destination_count)
    case "topic" => topic_prefix+destination_name+"-"+(i%destination_count)
    case _ => throw new Exception("Unsuported destination type: "+destination_type)
  }

  protected def headers_for(i:Int) = {
    if ( headers.isEmpty ) {
      Array[String]()
    } else {
      headers(i%headers.size)
    }
  }

  var producer_samples:Option[ListBuffer[Long]] = None
  var consumer_samples:Option[ListBuffer[Long]] = None
  var error_samples = ListBuffer[Long]()

  def collection_start: Unit = {
    producer_counter.set(0)
    consumer_counter.set(0)
    error_counter.set(0)

    producer_samples = if (producers > 0 || producers_per_sample>0 ) {
      Some(ListBuffer[Long]())
    } else {
      None
    }
    consumer_samples = if (consumers > 0 || consumers_per_sample>0 ) {
      Some(ListBuffer[Long]())
    } else {
      None
    }
  }

  def collection_end: Map[String, scala.List[Long]] = {
    var rc = Map[String, List[Long]]()
    producer_samples.foreach{ samples =>
      rc += "p_"+name -> samples.toList
      samples.clear
    }
    consumer_samples.foreach{ samples =>
      rc += "c_"+name -> samples.toList
      samples.clear
    }
    rc += "e_"+name -> error_samples.toList
    error_samples.clear
    rc
  }

  trait Client {
    def start():Unit
    def shutdown():Unit
  }

  var producer_clients = List[Client]()
  var consumer_clients = List[Client]()

  def with_load[T](func: =>T ):T = {
    done.set(false)

    for (i <- 0 until producers) {
      val client = createProducer(i)
      producer_clients ::= client
      client.start()
    }

    for (i <- 0 until consumers) {
      val client = createConsumer(i)
      consumer_clients ::= client
      client.start()
    }

    try {
      func
    } finally {
      done.set(true)
      // wait for the threads to finish..
      for( client <- consumer_clients ) {
        client.shutdown
      }
      consumer_clients = List()
      for( client <- producer_clients ) {
        client.shutdown
      }
      producer_clients = List()
    }
  }

  def drain = {
    done.set(false)
    if( destination_type=="queue" || durable==true ) {
      print("draining")
      consumer_counter.set(0)
      var consumer_clients = List[Client]()
      for (i <- 0 until destination_count) {
        val client = createConsumer(i)
        consumer_clients ::= client
        client.start()
      }

      // Keep sleeping until we stop draining messages.
      var drained = 0L
      try {
        Thread.sleep(drain_timeout);
        def done() = {
          val c = consumer_counter.getAndSet(0)
          drained += c
          c == 0
        }
        while( !done ) {
          print(".")
          Thread.sleep(drain_timeout);
        }
      } finally {
        done.set(true)
        for( client <- consumer_clients ) {
          client.shutdown
        }
        println(". (drained %d)".format(drained))
      }
    }
  }


  def collection_sample: Unit = {

    producer_samples.foreach(_ += producer_counter.getAndSet(0))
    consumer_samples.foreach(_ += consumer_counter.getAndSet(0))
    error_samples += error_counter.getAndSet(0)

    // we might need to increment number the producers..
    for (i <- 0 until producers_per_sample) {
      val client = createProducer(producer_clients.length)
      producer_clients ::= client
      client.start()
    }

    // we might need to increment number the consumers..
    for (i <- 0 until consumers_per_sample) {
      val client = createConsumer(consumer_clients.length)
      consumer_clients ::= client
      client.start()
    }

  }
  
  def createProducer(i:Int):Client
  def createConsumer(i:Int):Client

}


/**
 * Simulates load on the a stomp broker using standard blocking IO
 */
class BlockingScenario extends Scenario {

  import Scenario._

  var client_stack_size = 1024*500;

  def createProducer(i:Int) = {
    new ProducerClient(i)
  }

  def createConsumer(i:Int) = {
    new ConsumerClient(i)
  }

  class BlockingClient extends Thread(Thread.currentThread.getThreadGroup, null, "client", client_stack_size) with Client {

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
        val data = new String(rc)
        if( data.startsWith("ERROR") ) {
          println(data)
        }
        throw new Exception("Expected "+expect)
      }
      rc
    }

    def connect(proc: =>Unit ) = {
      try {
        open(host, port)
        write("CONNECT\n%s%s\n".format(
          o(login).map("login:%s\n".format(_)).getOrElse(""),
          o(passcode).map("passcode:%s\n".format(_)).getOrElse("")
        ))
        receive ("CONNECTED")
        proc
      } catch {
        case e: Throwable =>
          if(!done.get) {
            println("failure occured: "+e)
            error_counter.incrementAndGet
            try {
              Thread.sleep(1000)
            } catch {
              case _ => // ignore
            }
          }
      } finally {
        try {
          close()
        } catch {
          case ignore: Throwable =>
        }
      }
    }

    def shutdown = {
      interrupt
      close
      join
    }

  }

  class ProducerClient(val id: Int) extends BlockingClient {
    val name: String = "producer " + id
    val content = ("SEND\n" +
              "destination:"+destination(id)+"\n"+
               { if(persistent) persistent_header+"\n" else "" } +
               { if(sync_send) "receipt:xxx\n" else "" } +
               { headers_for(id).foldLeft("") { case (sum, v)=> sum+v+"\n" } } +
               { if(content_length) "content-length:"+message_size+"\n" else "" } +
              "\n"+message(name)).getBytes("UTF-8")


    override def run() {
      while (!done.get) {
        connect {
          var i =0
          while (!done.get) {
            write(content)
            if( sync_send ) {
              // waits for the reply..
              skip
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

  class ConsumerClient(val id: Int) extends BlockingClient {
    val name: String = "producer " + id

    override def run() {
      while (!done.get) {
        connect {
          write(
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
          val msg = receive()
          val start = index_of(msg, MESSAGE_ID)
          assert( start >= 0 )
          val end = msg.indexOf("\n", start)
          val msgId = msg.slice(start+MESSAGE_ID.length+1, end)
          write("""
ACK
message-id:""", msgId,"""

""")

        } else {
          skip
        }
        consumer_counter.incrementAndGet()
        Thread.sleep(consumer_sleep)
      }
    }
  }

}


/**
 * Simulates load on the a stomp broker using non blocking io.
 */
class NonBlockingScenario extends Scenario {

  import Scenario._

  def createProducer(i:Int) = {
    new ProducerClient(i)
  }
  def createConsumer(i:Int) = {
    new ConsumerClient(i)
  }
  
  trait NonBlockingClient  extends Client {

    protected var queue = createQueue("client")
    protected var channel:SocketChannel = _
    protected val read_buffer = ByteBuffer.allocate(buffer_size)
    protected var read_source:DispatchSource = _
    protected var write_source:DispatchSource = _

    var is_shutdown = false
    val has_shutdown = new CountDownLatch(1)
    def reconnect_action:Unit

    def start = {
      queue ^ {
        reconnect_action
      }
    }

    def queue_check = assert(getCurrentQueue == queue)

    def on_failure(e:Throwable) = {
      queue_check
      if(done.get) {
        shutdown_action
      } else {
        close
        error_counter.incrementAndGet
        queue.after(1000, TimeUnit.MILLISECONDS) {
          if(!done.get) {
            reconnect_action
          }
        }
      }
    }

    def open(host: String, port: Int)(func: =>Unit) = {
      queue_check
      try {
        channel = SocketChannel.open
        channel.configureBlocking(false)
        val source: DispatchSource = createSource(channel, SelectionKey.OP_CONNECT, queue)

        def finishConnect = {
          try {
            if (channel!=null && !channel.isConnected ) {
              if( channel.finishConnect ) {
                source.release
                read_source = createSource(channel, SelectionKey.OP_READ, queue)
                write_source = createSource(channel, SelectionKey.OP_WRITE, queue)
                write_source.setEventHandler(^{
                  if(write_source!=null) {
                    write_source.suspend; flush
                  }
                })
                read_buffer.clear.flip
                try {
                  channel.socket.setSoLinger(true, 0)
                  channel.socket.setTcpNoDelay(false)
                } catch { case x => // ignore
                }
                func
              } else {
                throw new Exception("Connect timed out")
              }
            }
          } catch {
            case e:Exception=>
              on_failure(e)
          }
        }
        source.setEventHandler(^{
          finishConnect
        })
        source.resume

        // This should cause a connect timeout after 5 seconds.
        queue.after(5, TimeUnit.SECONDS) {
          finishConnect
        }

        channel.connect(new InetSocketAddress(host, port))

      } catch {
        case e:Throwable =>
          on_failure(e)
      }
    }

    protected def shutdown_action = {
      queue_check
      if( !is_shutdown ) {
        is_shutdown = true
        close()
        has_shutdown.countDown
      }
    }

    def close() = {
      queue_check
      if( read_source!=null ) {
        read_source.release
        read_source = null
      }
      if( write_source!=null ) {
        write_source.release
        write_source = null
      }
      if( channel!=null ) {
        channel.close
        channel = null
      }
    }

    var write_stream = new ByteArrayOutputStream(buffer_size*2)
    var pending_write:ByteBuffer = _
    var on_flushed: ()=>Unit = _

    def _write(data:Array[Byte]):Boolean = {
      queue_check
      if( write_stream.size > buffer_size ) {
        return false
      } else {
        write_stream.write(data)
        write_stream.write(0)
        write_stream.write('\n')
        if( write_stream.size > buffer_size ) {
          flush
        }
        return true;
      }
    }
    
    def write(data:Array[Byte])(func: =>Unit):Unit = {
      def do_it:Unit = {
        on_flushed = null
        if( !_write(data) ) {
          on_flushed = ()=>{ do_it }
        } else {
          flush
          func
        }
      }
      do_it
    }

    def flush:Unit = {
      queue_check
      try {
        while(pending_write!=null || write_stream.size()!=0 ) {
          if( pending_write!=null ) {
            channel.write(pending_write)
            if( pending_write.hasRemaining ) {
              if( write_source.isSuspended ) {
                write_source.resume
              }
              return
            } else {
              pending_write = null
            }
          }
          if( pending_write==null && write_stream.size()!=0  ) {
            pending_write = ByteBuffer.wrap(write_stream.toByteArray)
            write_stream.reset
          }
        }
        if(on_flushed!=null) {
          on_flushed()
        }
      } catch {
        case e:Throwable =>
          on_failure(e)
          return
      }
    }


    def skip(func: =>Unit):Unit = {
      queue_check
      def fill:Unit = {
        if(channel==null) {
          return
        }
        while(true) {
          if( !read_buffer.hasRemaining ) {
            try {
              read_buffer.clear
              val c = channel.read(read_buffer)
              read_buffer.flip
              if( c == -1 ) {
                on_failure(new IOException("Server disconnected"))
                return
              }
              if( c == 0 ) {
                read_source.setEventHandler(^{
                  if( read_source!=null ) {
                    read_source.suspend
                    fill
                  }
                })
                read_source.resume
                return
              }
            } catch {
              case e:Exception=>
                on_failure(e)
            }
          }
          while( read_buffer.hasRemaining ) {
            if( read_buffer.get==0 ) {
              func
              return
            }
          }
        }
      }
      fill
    }

    def receive(func: Array[Byte]=>Unit) = {
      queue_check
      var start = true;
      val buffer = new ByteArrayOutputStream()
      def fill:Unit = {
        if(channel==null) {
          return
        }
        if( !read_buffer.hasRemaining ) {
          try {
            read_buffer.clear
            val c = channel.read(read_buffer)
            if( c == -1 ) {
              on_failure(new IOException("Server disconnected"))
              return
            }
            if( c == 0 ) {
              read_source.setEventHandler(^{
                if( read_source!=null ) {
                  read_source.suspend
                  fill
                }
              })
              read_source.resume
            }
            read_buffer.flip
          } catch {
            case e:Exception=>
              on_failure(e)
          }
        }
        while( read_buffer.hasRemaining ) {
          val c = read_buffer.get
          if( c==0 ) {
            func(buffer.toByteArray)
            return
          }
          if( !start || c!= NEWLINE) {
            start = false
            buffer.write(c)
          }
        }
      }
      fill
    }

    def expecting(expect:String)(func: Array[Byte]=>Unit):Unit = {
      queue_check
      receive { rc=>
        if( !rc.startsWith(expect) ) {
          val data = new String(rc)
          if( data.startsWith("ERROR") ) {
            println(data)
          }
          on_failure(new Exception("Expected "+expect))
        } else {
          func(rc)
        }
      }
    }

    def connect(proc: =>Unit) = {
      queue_check
      if( !done.get ) {
        open(host, port) {
          write("CONNECT\n%s%s\n".format(
            o(login).map("login:%s\n".format(_)).getOrElse(""),
            o(passcode).map("passcode:%s\n".format(_)).getOrElse("")
          )) {
            expecting("CONNECTED") { frame =>
              proc
            }
          }
        }
      }
    }

    def shutdown = {
      queue {
        shutdown_action
      }
      has_shutdown.await()
    }

    def name:String

  }

  class ProducerClient(val id: Int) extends NonBlockingClient {
    val name: String = "producer " + id
    queue.setLabel(name)
    val message_frame:Array[Byte] = "SEND\n" +
              "destination:"+destination(id)+"\n"+
               { if(persistent) persistent_header+"\n" else "" } +
               { if(sync_send) "receipt:xxx\n" else "" } +
               { headers_for(id).foldLeft("") { case (sum, v)=> sum+v+"\n" } } +
               { if(content_length) "content-length:"+message_size+"\n" else "" } +
              "\n"+message(name)

    override def reconnect_action = {
      connect {
        write_action
      }
    }

    def write_action:Unit = {
      on_flushed = null
      if(done.get) {
        shutdown_action
      } else {
        if( !_write(message_frame) ) {
          on_flushed = ()=>{ write_action }
        } else {
          if( sync_send ) {
            flush
            skip {
              write_completed_action
            }
          } else {
            write_completed_action
          }
        }
      }
    }

    def write_completed_action:Unit = {
      if(done.get) {
        shutdown_action
      } else {
        producer_counter.incrementAndGet()
        if(producers_disconnect) {
          close
        }
        if(producer_sleep > 0) {
          queue.after(producer_sleep, TimeUnit.MILLISECONDS) {
            if(producers_disconnect) {
              reconnect_action
            } else {
              write_action
            }
          }
        } else {
          queue {
            if(producers_disconnect) {
              reconnect_action
            } else {
              write_action
            }
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

  class ConsumerClient(val id: Int) extends NonBlockingClient {
    val name: String = "consumer " + id
    queue.setLabel(name)
    val clientAck = ack == "client"

    override def reconnect_action = {
      connect {
        write("""|SUBSCRIBE
                 |id:%s
                 |ack:%s
                 |destination:%s
                 |%s%s
                 |""".stripMargin.format(
              "sub-"+id,
              ack,
              destination(id),
              if(!durable) {""} else {"persistent:true\n"},
              if(selector==null) {""} else {"selector: "+selector+"\n"}
            )
        ) {
          receive_action
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

    def receive_action:Unit = {

      def receive_completed = {
        consumer_counter.incrementAndGet()
        if( consumer_sleep>0 ) {
          queue.after(consumer_sleep, TimeUnit.MILLISECONDS) {
            receive_action
          }
        } else {
          queue {
            receive_action
          }
        }
      }

      if( clientAck ) {
        receive { msg=>
          val start = index_of(msg, MESSAGE_ID)
          assert( start >= 0 )
          val end = msg.indexOf("\n", start)
          val msgId = msg.slice(start+MESSAGE_ID.length+1, end)
          write("""|ACK
                   |message-id:%s
                   |
                   |""".stripMargin.format(msgId)) {
            receive_completed
          }
        }

      } else {
        skip {
          receive_completed
        }
      }
    }
  }

}
