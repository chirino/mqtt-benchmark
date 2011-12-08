#!/bin/bash
#
# This shell script automates running the stomp-benchmark [1] against the
# Apache Apollo project [2].
#
# [1]: http://github.com/chirino/stomp-benchmark
# [2]: http://activemq.apache.org/apollo
#

APOLLO_VERSION=1.0-beta6
APOLLO_DOWNLOAD='https://repository.apache.org/content/repositories/orgapacheactivemq-299/org/apache/activemq/apache-apollo/1.0-beta6/apache-apollo-1.0-beta6-unix-distro.tar.gz'
BENCHMARK_HOME=~/benchmark
. benchmark-setup.sh

#
# Install the apollo distro
#
if [ ! -d "${BENCHMARK_HOME}/apache-apollo-${APOLLO_VERSION}" ]; then
  cd ${BENCHMARK_HOME}
  wget "$APOLLO_DOWNLOAD"
  tar -zxvf apache-apollo-*.tar.gz
  rm -rf apache-apollo-*.tar.gz
fi
if [ ! -d "${BENCHMARK_HOME}/apollo-${APOLLO_VERSION}" ]; then
  cd "${BENCHMARK_HOME}"
  "${BENCHMARK_HOME}/apache-apollo-${APOLLO_VERSION}/bin/apollo" create "apollo-${APOLLO_VERSION}"
fi

#
# Sanity Cleanup
kilall -9 java 2> /dev/null
kilall -9 apollo 2> /dev/null
rm -rf ${BENCHMARK_HOME}/apollo-${APOLLO_VERSION}/data/*
rm -rf ${BENCHMARK_HOME}/apollo-${APOLLO_VERSION}/tmp/*
rm -rf ${BENCHMARK_HOME}/apollo-${APOLLO_VERSION}/log/*


#
# Start the broker
#
rm "${BENCHMARK_HOME}/apollo-${APOLLO_VERSION}/log/console.log" 2> /dev/null
"${BENCHMARK_HOME}/apollo-${APOLLO_VERSION}/bin/apollo-broker" run 2>&1 > "${BENCHMARK_HOME}/apollo-${APOLLO_VERSION}/log/console.log" &
APOLLO_PID=$!
echo "Started Apollo with PID: ${APOLLO_PID}"
sleep 5
cat ${BENCHMARK_HOME}/apollo-${APOLLO_VERSION}/log/console.log

#
# Run the benchmark
#
cd ${BENCHMARK_HOME}/stomp-benchmark
sbt run --login admin --passcode password reports/apache-apollo-${APOLLO_VERSION}.json

# Kill the apollo
kill -9 ${APOLLO_PID}
