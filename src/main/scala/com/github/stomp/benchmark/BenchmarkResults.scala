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

import scala.collection.mutable.LinkedHashMap
import scala.collection.mutable.StringBuilder

class BenchmarkResults {
  var description: String = ""
  var groups: List[GroupResults] = Nil
  
  def to_json(level: Int = 0): String = {
    var sb = new StringBuilder()
    
    val indent = "    " * level
    
    sb ++= indent + "{\n"
    sb ++= indent + "    \"description\": \"" + description + "\",\n"
    sb ++= indent + "    \"groups\": [\n"
    groups.foreach( group => {
      sb ++= group.to_json(level + 2)
    })
    sb ++= indent + "    ]\n"
    sb ++= indent + "}\n"
    
    sb.toString
  }
}

class GroupResults {
  var name: String = ""
  var description: String = ""
  //var loop = new LinkedHashMap[String, List[(String, String)]]() // FIXME Maybe a List is better
  var scenarios: List[ScenarioResults] = Nil
  
  def to_json(level: Int = 0): String = {
    var sb = new StringBuilder()
    
    val indent = "    " * level
    
    sb ++= indent + "{\n"
    sb ++= indent + "    \"name\": \"" + name + "\",\n"
    sb ++= indent + "    \"description\": \"" + description + "\",\n"
    sb ++= indent + "    \"scenarios\": [\n"
    scenarios.foreach( scenario => {
      sb ++= scenario.to_json(level + 2)
    })
    sb ++= indent + "    ]\n"
    sb ++= indent + "},\n"
    
    sb.toString
  }
}

abstract class ScenarioResults {
   def to_json(level: Int): String; 
}

/*class LoopResults extends ScenarioResults {
  var scenarios = new LinkedHashMap[String, ScenarioResults]() // FIXME Maybe a List is better
  
  def to_json(level: Int = 0): String = {
  
  }
}*/

class SingleScenarioResults extends ScenarioResults {
  var name: String = ""
  var clients: List[ClientResults] = Nil
  
  def to_json(level: Int = 0): String = {
    var sb = new StringBuilder()
    
    val indent = "    " * level
    
    sb ++= indent + "{\n"
    sb ++= indent + "    \"name\": \"" + name + "\",\n"
    sb ++= indent + "    \"clients\": [\n"
    clients.foreach( client => {
      sb ++= client.to_json(level + 2)
    })
    sb ++= indent + "    ]\n"
    sb ++= indent + "},\n"
    
    sb.toString
  }
}

class ClientResults {
  var name: String = ""
  var settings: List[(String, String)] = Nil
  var producers_data: List[(Long,Long)] = Nil
  var consumers_data: List[(Long,Long)] = Nil
  var error_data: List[(Long,Long)] = Nil
  
  def to_json(level: Int = 0): String = {
    var sb = new StringBuilder()
    
    val indent = "    " * level
    
    sb ++= indent + "{\n"
    sb ++= indent + "    \"name\": \"" + name + "\",\n"
    sb ++= indent + "    \"settings\": [\n"
    settings.foreach( setting => {
      sb ++= indent + "        \"" + setting._1 + "\": \"" + setting._2 + "\",\n"
    })
    sb ++= indent + "    ]\n"
    sb ++= indent + "    \"data\": [\n"
    sb ++= indent + "        \"producers\": [ " + producers_data.map(x=> "[%d,%d]".format(x._1,x._2)).mkString(",") + " ],\n"
    sb ++= indent + "        \"consumers\": [ " + consumers_data.map(x=> "[%d,%d]".format(x._1,x._2)).mkString(",") + " ],\n"
    sb ++= indent + "        \"error\": [ " + error_data.map(x=> "[%d,%d]".format(x._1,x._2)).mkString(",") + " ]\n"
    sb ++= indent + "    ]\n"
    sb ++= indent + "},\n"
    
    sb.toString
  }
}
