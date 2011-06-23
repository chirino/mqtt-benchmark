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

object FlexibleProperty {
  
  private var properties: List[FlexibleProperty[_]] = Nil
  
  def apply[T](default: Option[T] = None): FlexibleProperty[T] = {
    val p = new FlexibleProperty[T]()
    p.default_value = default
    properties = p :: properties
    return p
  }
  
  def apply[T](default: Option[T], high_priority: () => Option[T]): FlexibleProperty[T] = {
    val p = new FlexibleProperty[T]()
    p.default_value = default
    p.high_priority_function = high_priority
    properties = p :: properties
    return p
  }
  
  def init_all() {
    properties.foreach( _.init() ) 
  }
  
}

class FlexibleProperty[T]() {

  private var default_value: Option[T] = None
  private var high_priority_value: Option[T] = None
  private var values: List[Option[T]] = Nil
  private var high_priority_function: () => Option[T] =  () => None
  
  def init() {
    high_priority_value = high_priority_function()
  }
  
  def set_high_priority(high_priority: T) {
    high_priority_value = Some(high_priority) 
  }
  
  def clear_high_priority() {
    high_priority_value = None
  }
  
  def set_default(default: T) {
    default_value = Some(default) 
  }
  
  def clear_default() {
    default_value = None
  }
  
  def push(value: Option[T]) {
    values = value :: values
  }
  
  def pop(): Option[T] = {
    val h = values.head
    values = values.tail
    h
  }
  
  def getOption(): Option[T] = {
    if (high_priority_value.isDefined) {
      high_priority_value
    } else {
      val filtered_values = values.filter(_.isDefined)
      if (! filtered_values.isEmpty){
        filtered_values.head
      } else {
        default_value
      }
    }
  }
  
  def get(): T = {
    getOption.get
  }
  
  def getOrElse(default: T): T = {
     getOption.getOrElse(default)
  }
  
}
