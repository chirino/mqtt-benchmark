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

import java.net._
import java.io._
import org.fusesource.hawtdispatch._
import java.nio.channels.{SelectionKey, SocketChannel}
import java.nio.ByteBuffer
import java.util.concurrent.{CountDownLatch, TimeUnit}

/**
 * <p>
 * Simulates load on the a stomp broker using non blocking io.
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
class NonBlockingScenario extends Scenario {

  import Scenario._

  def createProducer(i:Int) = {
    new ProducerClient(i)
  }
  def createConsumer(i:Int) = {
    new ConsumerClient(i)
  }

  trait NonBlockingClient extends Client {

    protected var queue = createQueue("client")

    var message_counter=0L
    var reconnect_delay = 0L

    sealed trait State

    case class INIT() extends State

    case class CONNECTING(host: String, port: Int, on_complete: ()=>Unit) extends State {

      val channel = SocketChannel.open
      ignore_failure(channel.socket.setReceiveBufferSize(buffer_size))
      ignore_failure(channel.socket.setSoLinger(true, 0))
      ignore_failure(channel.socket.setTcpNoDelay(false))
      
      channel.configureBlocking(false)
      val source: DispatchSource = createSource(channel, SelectionKey.OP_CONNECT, queue)
      source.onEvent {
        if ( this == state ) {
          if(done.get) {
            close
          } else {
            try {
              if( channel.finishConnect ) {
                source.cancel
                state = CONNECTED(channel)
                on_complete()
              }
            } catch {
              case e:Exception=>
                on_failure(e)
            }
          }
        }
      }
      source.resume

      def connect() = {
        channel.connect(new InetSocketAddress(host, port))
        // Times out the connect after 5 seconds...
        queue.after(5, TimeUnit.SECONDS) {
          if ( this == state ) {
            source.cancel
            on_failure(new Exception("Connect timed out"))
          }
        }
      }

      // We may need to delay the connection attempt.
      if( reconnect_delay==0 ) {
        connect
      } else {
        queue.after(5, TimeUnit.SECONDS) {
          if ( this == state ) {
            reconnect_delay=0
            connect
          }
        }
      }

      def close() = {
        source.cancel
        channel.close
        state = DISCONNECTED()
      }

      def on_failure(e:Throwable) = {
        if( display_errors ) {
          e.printStackTrace
        }
        error_counter.incrementAndGet
        reconnect_delay = 1000
        close
      }

    }

    case class CONNECTED(val channel:SocketChannel) extends State {

      var write_stream = new ByteArrayOutputStream(buffer_size*2)
      var pending_write:ByteBuffer = _
      var on_flushed: ()=>Unit = null
      var on_fill: ()=>Unit = null

      val read_buffer = ByteBuffer.allocate(buffer_size)
      read_buffer.clear.flip

      val read_source = createSource(channel, SelectionKey.OP_READ, queue)
      read_source.onEvent {
        if(state == this) {
          if(done.get) {
            close
          } else {
            fill
          }
        }
      }

      val write_source = createSource(channel, SelectionKey.OP_WRITE, queue)
      write_source.onEvent {
        if(state == this) {
          if(done.get) {
            close
          } else {
            write_source.suspend; flush
          }
        }
      }

      def close() = {
        state = CLOSING()
        read_source.onCancel {
          write_source.onCancel {
            channel.close
            state = DISCONNECTED()
          }
          write_source.cancel
        }
        read_source.cancel
      }

      def on_failure(e:Throwable) = {
        if( display_errors ) {
          e.printStackTrace
        }
        error_counter.incrementAndGet
        reconnect_delay = 1000
        close
      }

      def offer_write(data:Array[Byte])(func: =>Unit):Boolean = {
        if( write_stream.size > buffer_size ) {
          on_flushed = func _
          false
        } else {
          write_stream.write(data)
          write_stream.write(0)
          write_stream.write('\n')
          if( write_stream.size > buffer_size ) {
            flush
          }
          true
        }
      }

      def flush(func: =>Unit):Unit = {
        on_flushed = func _
        flush
      }

      def flush:Unit = {
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
            val t = on_flushed
            on_flushed = null
            t()
          }
        } catch {
          case e:Throwable =>
            on_failure(e)
            return
        }
      }

      def skip(func: =>Unit):Unit = {
        queue_check
        def do_it:Unit = {
          while( read_buffer.hasRemaining ) {
            if( read_buffer.get==0 ) {
              func
              return
            }
          }
          on_fill = ()=> { do_it }
          refill
        }
        do_it
      }

      def receive(func: Array[Byte]=>Unit) = {
        var start = true;
        val buffer = new ByteArrayOutputStream()

        def do_it:Unit = {
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
          on_fill = ()=> { do_it }
          refill
        }
        do_it
      }


      def refill:Unit = {
        read_buffer.compact
        read_source.resume
        queue {
          fill
        }
      }

      def fill:Unit = {
        if( !read_buffer.hasRemaining ) {
          on_fill()
          return
        }
        try {
          val c = channel.read(read_buffer)
          if( c == -1 ) {
            throw new IOException("Server disconnected")
          }
          if( c > 0 ) {
            read_source.suspend
            read_buffer.flip
            if( on_fill!=null ){
              on_fill()
            }
          }
        } catch {
          case e:Exception=>
            on_failure(e)
        }
      }

    }
    case class CLOSING() extends State

    case class DISCONNECTED() extends State {
      queue {
        if( state==this ){
          if( done.get ) {
            has_shutdown.countDown
          } else {
            reconnect_action
          }
        }
      }
    }

    var state:State = INIT()

    val has_shutdown = new CountDownLatch(1)
    def reconnect_action:Unit

    def on_failure(e:Throwable) = state match {
      case x:CONNECTING => x.on_failure(e)
      case x:CONNECTED => x.on_failure(e)
      case _ =>
    }

    def start = queue {
      state = DISCONNECTED()
    }

    def queue_check = assert(getCurrentQueue == queue)

    def open(host: String, port: Int)(on_complete: =>Unit) = {
      assert ( state.isInstanceOf[DISCONNECTED] )
      queue_check
      state = CONNECTING(host, port, ()=>on_complete)
    }

    def close() = {
      queue_check
      state match {
        case x:CONNECTING => x.close
        case x:CONNECTED => x.close
        case _ =>
      }
    }

    def shutdown = {
      assert(done.get)
      queue {
        close
      }
      has_shutdown.await()
    }

    def offer_write(data:Array[Byte])(func: =>Unit):Boolean = {
      queue_check
      state match {
        case state:CONNECTED => state.offer_write(data)(func)
        case _ => true
      }
    }

    def write(data:Array[Byte])(func: =>Unit):Unit = {
      def retry:Unit = {
        if( offer_write(data)(retry) ) {
          flush(func)
        }
      }
      retry
    }

    def flush(func: =>Unit):Unit = {
      queue_check
      state match {
        case state:CONNECTED => state.flush(func)
        case _ =>
      }
    }

    def skip(func: =>Unit):Unit = {
      queue_check
      state match {
        case state:CONNECTED => state.skip(func)
        case _ =>
      }
    }

    def receive(func: Array[Byte]=>Unit) = {
      queue_check
      state match {
        case state:CONNECTED => state.receive(func)
        case _ =>
      }
    }

    def expecting(expect:String)(func: Array[Byte]=>Unit):Unit = {
      queue_check
      receive { rc=>
        if( !rc.startsWith(expect) ) {
          val data = new String(rc)
          if( data.startsWith("ERROR") && display_errors ) {
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
      def retry:Unit = {
        if(done.get) {
          close
        } else {
          if(producer_sleep >= 0) {
            if (offer_write(message_frame)(retry)) {
              if( sync_send ) {
                flush {
                  skip {
                    producer_counter.incrementAndGet()
                    message_counter += 1
                    write_completed_action
                  }
                }
              } else {
                producer_counter.incrementAndGet()
                message_counter += 1
                write_completed_action
              }
            }
          } else {
              write_completed_action
          }
        }
      }
      retry
    }

    def write_completed_action:Unit = {
      if(done.get) {
        close
      } else {
        val p_sleep = producer_sleep
        if(p_sleep != 0) {
          flush {
            queue.after(math.abs(p_sleep), TimeUnit.MILLISECONDS) {
              if(messages_per_connection > 0 && message_counter >= messages_per_connection  ) {
                message_counter = 0
                close
              } else {
                write_action
              }
            }
          }
        } else {
          queue {
            if(messages_per_connection > 0 && message_counter >= messages_per_connection  ) {
              message_counter = 0
              flush {
                close
              }
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
              consumer_prefix+id,
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
        val c_sleep = consumer_sleep
        if( c_sleep != 0 ) {
          queue.after(math.abs(c_sleep), TimeUnit.MILLISECONDS) {
            receive_action
          }
        } else {
          queue {
            receive_action
          }
        }
      }

      if (consumer_sleep >= 0) {
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
              consumer_counter.incrementAndGet()
              receive_completed
            }
          }

        } else {
          skip {
            consumer_counter.incrementAndGet()
            receive_completed
          }
        }
      } else {
        receive_completed
      }
    }
  }

}
