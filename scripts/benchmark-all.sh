#!/bin/bash
# Runs all the benchmarks.
#

true \
${REPORTS_HOME:=$1} \
${REPORTS_HOME:=`pwd`/report} \
${WORKSPACE:=$2} \
${WORKSPACE:=`pwd`/workspace}

export REPORTS_HOME
export WORKSPACE

base=`dirname "$0"`
"${base}/benchmark-activemq.sh"
"${base}/benchmark-apollo.sh"
"${base}/benchmark-hornetq.sh"
"${base}/benchmark-rabbitmq.sh"

