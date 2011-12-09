#!/bin/bash
#
# This shell script automates running the stomp-benchmark [1] against the
# Apache ActiveMQ project [2].
#
# [1]: http://github.com/chirino/stomp-benchmark
# [2]: http://activemq.apache.org
#

ACTIVEMQ_VERSION=5.5.1
ACTIVEMQ_DOWNLOAD="http://www.apache.org/dist/activemq/apache-activemq/${ACTIVEMQ_VERSION}/apache-activemq-${ACTIVEMQ_VERSION}-bin.tar.gz"
BENCHMARK_HOME=~/benchmark
. `dirname "$0"`/benchmark-setup.sh

#
# Install the apollo distro
#
ACTIVEMQ_HOME="${BENCHMARK_HOME}/apache-activemq-${ACTIVEMQ_VERSION}"
if [ ! -d "${ACTIVEMQ_HOME}" ]; then
  cd ${BENCHMARK_HOME}
  wget "$ACTIVEMQ_DOWNLOAD"
  tar -zxvf apache-activemq-${ACTIVEMQ_VERSION}-bin.tar.gz
  rm -rf apache-activemq-${ACTIVEMQ_VERSION}-bin.tar.gz
fi


#
# Sanity Cleanup
rm -rf ${ACTIVEMQ_HOME}/data/*


#
# Start the broker
#
CONSOLE_LOG="${ACTIVEMQ_HOME}/console.log"
rm "${CONSOLE_LOG}" 2> /dev/null
"${ACTIVEMQ_HOME}/bin/activemq" console "xbean:file:${ACTIVEMQ_HOME}/conf/activemq-stomp.xml" 2>&1 > "${CONSOLE_LOG}" &
ACTIVEMQ_PID=$!
echo "Started ActiveMQ with PID: ${ACTIVEMQ_PID}"
sleep 5
cat ${CONSOLE_LOG}

#
# Run the benchmark
#
cd ${BENCHMARK_HOME}/stomp-benchmark
sbt run reports/activemq-${ACTIVEMQ_VERSION}.json

# Kill the broker
kill -9 ${ACTIVEMQ_PID}
