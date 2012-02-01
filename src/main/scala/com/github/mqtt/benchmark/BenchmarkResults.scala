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

import scala.collection.mutable.LinkedHashMap
import scala.collection.mutable.StringBuilder

class BenchmarkResults {
  var broker_name: String = ""
  var description: String = ""
  var platform_name: String = ""
  var platform_desc: String = ""
  var groups: List[GroupResults] = Nil
  
  def to_json(level: Int = 0): String = {
    var sb = new StringBuilder()
    
    val indent = "    " * level
    
    sb ++= indent + "{\n"
    sb ++= indent + "    \"broker_name\": \"" + broker_name + "\",\n"
    sb ++= indent + "    \"description\": \"" + description + "\",\n"
    sb ++= indent + "    \"platform_name\": \"" + platform_name + "\",\n"
    sb ++= indent + "    \"platform_desc\": \"" + platform_desc + "\",\n"
    sb ++= indent + "    \"groups\": [\n"
    sb ++=  groups map { group =>
      group.to_json(level + 2)
    } mkString(",\n")
    sb ++= "\n"
    sb ++= indent + "    ]\n"
    sb ++= indent + "}\n"
    
    sb.toString
  }
}

case class LoopValue(val value: String, val label: String, val description: String)
case class LoopVariable(val name: String, val label: String, val values: List[LoopValue])

class GroupResults {
  var name: String = ""
  var description: String = ""
  var loop: List[LoopVariable] = Nil
  var scenarios: List[ScenarioResults] = Nil
  
  def to_json(level: Int = 0): String = {
    var sb = new StringBuilder()
    
    val indent = "    " * level
    
    sb ++= indent + "{\n"
    sb ++= indent + "    \"name\": \"" + name + "\",\n"
    sb ++= indent + "    \"description\": \"" + description + "\",\n"

    sb ++= indent + "    \"loop\": [\n"
    sb ++=  loop map { loop_var =>
      val values = loop_var.values map { value =>
        indent + "            { \"label\": \"" + value.label + "\", \"description\": \"" + value.description + "\"}"
      } mkString(",\n")
      
      indent + "        [\"" + loop_var.label + "\", [\n" +
      values + "\n" +
      indent + "        ]]"
    } mkString(",\n")
    sb ++= "\n"
    sb ++= indent + "    ],\n"
    
    sb ++= indent + "    \"scenarios\": [\n"
    sb ++=  scenarios map { scenario =>
      scenario.to_json(level + 2)
    } mkString(",\n")
    sb ++= "\n"
    
    sb ++= indent + "    ]\n"
    sb ++= indent + "}"
    
    sb.toString
  }
}

abstract class ScenarioResults {
   def to_json(level: Int): String; 
}

class LoopScenarioResults extends ScenarioResults {
  var scenarios: List[(String, ScenarioResults)] = Nil
  
  def to_json(level: Int = 0): String = {
    var sb = new StringBuilder()
    
    val indent = "    " * level
    
    sb ++= indent + "{\n"

    sb ++=  scenarios map { scenario =>
      indent + "    \"" + scenario._1 + "\": \n" + scenario._2.to_json(level + 2)
    } mkString(",\n")
    sb ++= "\n"
    sb ++= indent + "}"
    
    sb.toString
  }
}

class SingleScenarioResults extends ScenarioResults {
  var name: String = ""
  var label: String = ""
  var clients: List[ClientResults] = Nil
  
  def to_json(level: Int = 0): String = {
    var sb = new StringBuilder()
    
    val indent = "    " * level
    
    sb ++= indent + "{\n"
    sb ++= indent + "    \"name\": \"" + name + "\",\n"
    sb ++= indent + "    \"label\": \"" + label + "\",\n"
    sb ++= indent + "    \"clients\": [\n"
    sb ++=  clients map { client =>
      client.to_json(level + 2)
    } mkString(",\n")
    sb ++= "\n"
    sb ++= indent + "    ]\n"
    sb ++= indent + "}"
    
    sb.toString
  }
}

class ClientResults {
  var name: String = ""
  var settings: List[(String, String)] = Nil
  var producers_data: List[(Long,Long)] = Nil
  var consumers_data: List[(Long,Long)] = Nil
  var request_p90: List[(Long,Long)] = Nil
  var request_p99: List[(Long,Long)] = Nil
  var request_p999: List[(Long,Long)] = Nil
  var error_data: List[(Long,Long)] = Nil
  
  def to_json(level: Int = 0): String = {
    var sb = new StringBuilder()
    
    val indent = "    " * level
    
    sb ++= indent + "{\n"
    sb ++= indent + "    \"name\": \"" + name + "\",\n"
    sb ++= indent + "    \"settings\": {\n"
    sb ++=  settings map { setting =>
      indent + "        \"" + setting._1 + "\": \"" + setting._2 + "\""
    } mkString(",\n")
    sb ++= "\n"
    sb ++= indent + "    },\n"
    sb ++= indent + "    \"data\": {\n"
    sb ++= indent + "        \"producers\": [ " + producers_data.map(x=> "[%d,%d]".format(x._1,x._2)).mkString(",") + " ],\n"
    sb ++= indent + "        \"consumers\": [ " + consumers_data.map(x=> "[%d,%d]".format(x._1,x._2)).mkString(",") + " ],\n"

    if( request_p90 != Nil ) {
      sb ++= indent + "        \"request_p90\": [ " + request_p90.map(x=> "[%d,%d]".format(x._1,x._2)).mkString(",") + " ],\n"
    }
    if( request_p99 != Nil ) {
      sb ++= indent + "        \"request_p99\": [ " + request_p99.map(x=> "[%d,%d]".format(x._1,x._2)).mkString(",") + " ],\n"
    }
    if( request_p999 != Nil ) {
      sb ++= indent + "        \"request_p999\": [ " + request_p999.map(x=> "[%d,%d]".format(x._1,x._2)).mkString(",") + " ],\n"
    }

    sb ++= indent + "        \"error\": [ " + error_data.map(x=> "[%d,%d]".format(x._1,x._2)).mkString(",") + " ]\n"
    sb ++= indent + "    }\n"
    sb ++= indent + "}"
    
    sb.toString
  }
}
