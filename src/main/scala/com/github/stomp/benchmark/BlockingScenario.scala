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
import java.net._
import java.io._

/**
 * Simulates load on the a stomp broker using standard blocking IO
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
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
    var message_counter=0L

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
        if( data.startsWith("ERROR") && display_errors) {
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
          login.map("login:%s\n".format(_)).getOrElse(""),
          passcode.map("passcode:%s\n".format(_)).getOrElse("")
        ))
        receive ("CONNECTED")
        proc
      } catch {
        case e: Throwable =>
          if(!done.get) {
            if( display_errors ) {
              e.printStackTrace
            }
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
    
    val message_frame_cache = HashMap.empty[Int, Array[Byte]]
    
    override def run() {
      while (!done.get) {
        connect {
          var reconnect = false
          while (!done.get && !reconnect) {
            val p_sleep = producer_sleep
            if ( p_sleep >= 0 ) {
              write(get_message_frame)
              if( sync_send ) {
                // waits for the reply..
                skip
              }
              producer_counter.incrementAndGet()
              message_counter += 1
              val m_p_connection = messages_per_connection.toLong
              if( m_p_connection > 0 && message_counter >= m_p_connection ) {
                message_counter = 0
                reconnect = true
              }
            }
            if(p_sleep != 0) {
              Thread.sleep(math.abs(p_sleep))
            }
          }
        }
      }
    }
    
    def message(name:String, message_size:Int) = {
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
    
    def get_message_frame():Array[Byte] = {
      val m_s = message_size
      if (! message_frame_cache.contains(m_s)) {
        message_frame_cache(m_s) = "SEND\n" +
        "destination:"+destination(id)+"\n"+
        { if(persistent) persistent_header+"\n" else "" } +
        { if(sync_send) "receipt:xxx\n" else "" } +
        { headers_for(id).foldLeft("") { case (sum, v)=> sum+v+"\n" } } +
        { if(content_length) "content-length:"+m_s+"\n" else "" } +
        "\n"+message(name, m_s).getBytes("UTF-8")
      }
      
      return message_frame_cache(m_s);
    }
  
  }

  class ConsumerClient(val id: Int) extends BlockingClient {
    val name: String = "producer " + id

    override def run() {
      while (!done.get) {
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
          )
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
        val c_sleep = consumer_sleep
        if ( c_sleep >= 0 ) {
          if( clientAck ) {
            val msg = receive()
            val start = index_of(msg, MESSAGE_ID)
            assert( start >= 0 )
            val end = msg.indexOf("\n", start)
            val msgId = msg.slice(start+MESSAGE_ID.length+1, end)
            write("""
ACK
message-  id:""", msgId,"""

""")

          } else {
            skip
          }
          consumer_counter.incrementAndGet()
        }
        if(c_sleep != 0) {
          Thread.sleep(math.abs(c_sleep))
        }
      }
    }
  }

}


