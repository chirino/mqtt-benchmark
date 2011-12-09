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
. `dirname "$0"`/benchmark-setup.sh

#
# Install the apollo distro
#
APOLLO_HOME="${BENCHMARK_HOME}/apache-apollo-${APOLLO_VERSION}"
if [ ! -d "${APOLLO_HOME}" ]; then
  cd ${BENCHMARK_HOME}
  wget "$APOLLO_DOWNLOAD"
  tar -zxvf apache-apollo-*.tar.gz
  rm -rf apache-apollo-*.tar.gz
fi

APOLLO_BASE="${BENCHMARK_HOME}/apollo-${APOLLO_VERSION}"
if [ ! -d "${APOLLO_BASE}" ]; then
  cd "${BENCHMARK_HOME}"
  "${APOLLO_HOME}/bin/apollo" create "apollo-${APOLLO_VERSION}"
fi

#
# Sanity Cleanup
rm -rf ${APOLLO_BASE}/data/*
rm -rf ${APOLLO_BASE}/tmp/*
rm -rf ${APOLLO_BASE}/log/*


#
# Start the broker
#
rm "${APOLLO_BASE}/log/console.log" 2> /dev/null
"${APOLLO_BASE}/bin/apollo-broker" run 2>&1 > "${APOLLO_BASE}/log/console.log" &
APOLLO_PID=$!
echo "Started Apollo with PID: ${APOLLO_PID}"
sleep 5
cat ${APOLLO_BASE}/log/console.log

#
# Run the benchmark
#
cd ${BENCHMARK_HOME}/stomp-benchmark
sbt run --login admin --passcode password reports/apache-apollo-${APOLLO_VERSION}.json

# Kill the apollo
kill -9 ${APOLLO_PID}
