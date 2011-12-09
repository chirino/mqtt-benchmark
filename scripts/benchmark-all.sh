#!/bin/bash
#
#

base=`dirname "$0"`
"${base}/benchmark-activemq.sh"
"${base}/benchmark-apollo.sh"
"${base}/benchmark-hornetq.sh"
"${base}/benchmark-rabbitmq.sh"

