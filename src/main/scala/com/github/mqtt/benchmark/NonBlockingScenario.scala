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
package com.github.mqtt.benchmark

import org.fusesource.hawtdispatch._
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.lang.Throwable
import org.fusesource.hawtbuf.Buffer._
import org.fusesource.mqtt.client._
import scala.collection.mutable.HashMap
import java.net.URI
import org.fusesource.hawtbuf.{Buffer, UTF8Buffer, AsciiBuffer}

//object NonBlockingScenario {
//  def main(args:Array[String]):Unit = {
//    val scenario = new com.github.mqtt.benchmark.NonBlockingScenario
//    scenario.login = Some("admin")
//    scenario.passcode = Some("password")
//
//    scenario.port = 61614
//    scenario.protocol = "tls"
//    scenario.key_store_file = Some("/Users/chirino/sandbox/mqtt-benchmark/keystore")
//    scenario.key_store_password = Some("password")
//    scenario.key_password = Some("password")
//
//    scenario.message_size = 20
//    scenario.request_response = false
//    scenario.display_errors = true
//
//    scenario.consumers = 0
//    scenario.run
//  }
//}

/**
 * <p>
 * Simulates load on the a mqtt broker using non blocking io.
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
class NonBlockingScenario extends Scenario {

  def createProducer(i:Int) = {
    if(this.request_response) {
      new RequestingClient((i))
    } else {
      new ProducerClient(i)
    }
  }
  def createConsumer(i:Int) = {
    if(this.request_response) {
      new RespondingClient(i)
    } else {
      new ConsumerClient(i)
    }
  }

  trait NonBlockingClient extends Client {

    protected var queue = createQueue(client_id)

    var message_counter=0L
    var reconnect_delay = 0L

    def client_id:String = null
    def clean = true

    sealed trait State

    case class INIT() extends State

    case class CONNECTING(host: String, port: Int, on_complete: ()=>Unit) extends State {
      
      def connect() = {
        val mqtt = new MQTT()
        mqtt.setDispatchQueue(queue)
        mqtt.setSslContext(ssl_context)
        mqtt.setHost(new URI(protocol+"://" + host + ":" + port))
        mqtt.setClientId(client_id)
        mqtt.setCleanSession(clean)
        mqtt.setReconnectAttemptsMax(0)
        mqtt.setConnectAttemptsMax(0)

        login.foreach(mqtt.setUserName(_))
        passcode.foreach(mqtt.setPassword(_))
        val connection = mqtt.callbackConnection();
        connection.connect(new Callback[Void](){
          def onSuccess(na: Void) {
            state match {
              case x:CONNECTING =>
                state = CONNECTED(connection)
                on_complete()
                connection.resume()
              case _ =>
                connection.disconnect(null)
            }
          }
          def onFailure(value: Throwable) {
            on_failure(value)
          }
        })
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

    case class CONNECTED(val connection:CallbackConnection) extends State {

      connection.listener(new Listener {
        def onConnected() {}
        def onDisconnected() {}
        def onPublish(topic: UTF8Buffer, body: Buffer, ack: Runnable) {
          on_receive(topic, body, ack)
        }

        def onFailure(value: Throwable) {
          on_failure(value)
        }
      })

      def close() = {
        state = CLOSING()
        connection.disconnect(new Callback[Void] {
          def onSuccess(value: Void) {
            state = DISCONNECTED()
          }
          def onFailure(value: Throwable) = onSuccess(null)
        })
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

    def queue_check = queue.assertExecuting()

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

    def receive_suspend = {
      queue_check
      state match {
        case state:CONNECTED => state.connection.suspend()
        case _ =>
      }
    }

    def receive_resume = {
      queue_check
      state match {
        case state:CONNECTED => state.connection.resume()
        case _ =>
      }
    }

    def connection = {
      queue_check
      state match {
        case state:CONNECTED => Some(state.connection)
        case _ => None
      }
    }

    def on_receive(topic: UTF8Buffer, body: Buffer, ack: Runnable) = {
      ack.run()
    }

    def connect(proc: =>Unit) = {
      queue_check
      if( !done.get ) {
        open(host, port) {
          proc
        }
      }
    }

  }

  class ProducerClient(val id: Int) extends NonBlockingClient {

    override def client_id = "producer-"+id

    val message_cache = HashMap.empty[Int, AsciiBuffer]

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
            connection.foreach{ connection=>
              connection.publish(utf8(destination(id)), get_message(), QoS.values()(producer_qos), producer_retain, new Callback[Void](){
                def onSuccess(value: Void) = {
                  producer_counter.incrementAndGet()
                  message_counter += 1
                  write_completed_action
                }
                def onFailure(value: Throwable) = {
                  on_failure(value)
                }
              })
            }
          } else {
            write_completed_action
          }
        }
      }
      retry
    }

    def write_completed_action:Unit = {
      def doit = {
        val m_p_connection = messages_per_connection.toLong
        if(m_p_connection > 0 && message_counter >= m_p_connection) {
          message_counter = 0
          close
        } else {
          write_action
        }
      }

      if(done.get) {
        close
      } else {
        if(producer_sleep != 0) {
          queue.after(math.abs(producer_sleep), TimeUnit.MILLISECONDS) {
            doit
          }
        } else {
          queue { doit }
        }
      }
    }
  
    def get_message() = {
      val m_s = message_size
      
      if(! message_cache.contains(m_s)) {
        message_cache(m_s) = message(client_id, m_s)
      }
      
      message_cache(m_s)
    }
  
    def message(name:String, size:Int) = {
      val buffer = new StringBuffer(size)
      buffer.append("Message from " + name + "\n")
      for( i <- buffer.length to size ) {
        buffer.append(('a'+(i%26)).toChar)
      }
      var rc = buffer.toString
      if( rc.length > size ) {
        rc.substring(0, size)
      } else {
        rc
      }
      ascii(rc)
    }
  
  }

  class ConsumerClient(val id: Int) extends NonBlockingClient {

    override def client_id = "consumer-"+id

    override def clean = clean_session

    override def reconnect_action = {
      connect {
        connection.foreach { connection =>
          connection.subscribe(Array(new Topic(destination(id), QoS.values()(consumer_qos))), null)
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


    override def on_receive(topic: UTF8Buffer, body: Buffer, ack: Runnable) = {
      if( consumer_sleep != 0 && ((consumer_counter.get()%consumer_sleep_modulo) == 0)) {
        if( consumer_qos==0 ) {
          receive_suspend
        }
        queue.after(math.abs(consumer_sleep), TimeUnit.MILLISECONDS) {
          if( consumer_qos==0 ) {
            receive_resume
          }
          process_message(body, ack)
        }
      } else {
        process_message(body, ack)
      }
    }

    def process_message(body: Buffer, ack: Runnable):Unit = {
      ack.run()
      consumer_counter.incrementAndGet()
    }

  }

  class RequestingClient(id: Int) extends ProducerClient(id) {
    override def client_id = "requestor-"+id

    override def reconnect_action = {
      connect {
        connection.foreach { connection =>
          connection.subscribe(Array(new Topic(response_destination(id), QoS.values()(consumer_qos))), null)
        }
        // give the response queue a chance to drain before doing new requests.
        queue.after(1000, TimeUnit.MILLISECONDS) {
          write_action
        }
      }
    }

    var request_start = 0L

    override def write_action:Unit = {
      def retry:Unit = {
        if(done.get) {
          close
        } else {
          if(producer_sleep >= 0) {
            connection.foreach{ connection=>
              var msg = get_message().deepCopy() // we have to copy since we are modifyiing..
              msg.buffer.moveHead(msg.length-4).bigEndianEditor().writeInt(id) // write the last 4 bytes..

              request_start = System.nanoTime()
              connection.publish(utf8(destination(id)), msg, QoS.values()(producer_qos), producer_retain, new Callback[Void](){
                def onSuccess(value: Void) = {
                  // don't do anything.. we complete when
                  // on_receive gets called.
                }
                def onFailure(value: Throwable) = {
                  on_failure(value)
                }
              })
            }
          } else {
            write_completed_action
          }
        }
      }
      retry
    }

    override def on_receive(topic: UTF8Buffer, body: Buffer, ack: Runnable) = {
      if(request_start != 0L) {
        request_times.add(System.nanoTime() - request_start)
        request_start = 0
        producer_counter.incrementAndGet()
        message_counter += 1
        write_completed_action
      }
      ack.run();
    }

  }

  class RespondingClient(id: Int) extends ConsumerClient(id) {

    override def client_id = "responder-"+id

    val EMPTY = new Buffer(0);

    override def process_message(body: Buffer, ack: Runnable) = {
      connection.foreach{ connection=>
        body.moveHead(body.length-4) //lets read the last 4 bytes
        val rid = body.bigEndianEditor().readInt()
        connection.publish(utf8(response_destination(rid)), EMPTY, QoS.values()(producer_qos), producer_retain, new Callback[Void](){
          def onSuccess(value: Void) = {
            ack.run()
            consumer_counter.incrementAndGet()
          }
          def onFailure(value: Throwable) = {
            on_failure(value)
          }
        })
      }
    }
  }
}
