#!/bin/bash
#
# This shell script automates running the stomp-benchmark [1] against the
# RabbitMQ project [2].
#
# [1]: http://github.com/chirino/stomp-benchmark
# [2]: http://www.rabbitmq.com/
#

RABBITMQ_VERSION=2.7.0
RABBITMQ_DOWNLOAD="http://www.rabbitmq.com/releases/rabbitmq-server/v${RABBITMQ_VERSION}/rabbitmq-server-generic-unix-${RABBITMQ_VERSION}.tar.gz"
BENCHMARK_HOME=~/benchmark
. `dirname "$0"`/benchmark-setup.sh

which erl > /dev/null
if [ $? -ne 0 ] ; then
  cd "${BENCHMARK_HOME}"
  echo "Installing erlang...."
  sudo yum install -y erlang
fi 

#
# Install the distro
#
RABBITMQ_HOME="${BENCHMARK_HOME}/rabbitmq_server-${RABBITMQ_VERSION}"
if [ ! -d "${RABBITMQ_HOME}" ]; then
  cd ${BENCHMARK_HOME}
  wget "$RABBITMQ_DOWNLOAD"
  tar -zxvf rabbitmq-server-generic-unix-${RABBITMQ_VERSION}.tar.gz
  rm -rf rabbitmq-server-generic-unix-${RABBITMQ_VERSION}.tar.gz
fi

RABBITMQ_BASE="${BENCHMARK_HOME}/rabbitmq-${RABBITMQ_VERSION}"
mkdir -p "${RABBITMQ_BASE}"

#
# Sanity Cleanup
rm -rf "${RABBITMQ_HOME}/*"

#
# Start the server
#s
CONSOLE_LOG="${RABBITMQ_BASE}/console.log"
rm "${CONSOLE_LOG}" 2> /dev/null

#
# Rabbit config
export RABBITMQ_NODENAME=rabbit
export RABBITMQ_SERVER_ERL_ARGS=
export RABBITMQ_CONFIG_FILE="${RABBITMQ_BASE}/config"
export RABBITMQ_LOG_BASE="${RABBITMQ_BASE}/logs"
export RABBITMQ_MNESIA_BASE="${RABBITMQ_BASE}/mnesia"
export RABBITMQ_ENABLED_PLUGINS_FILE="${RABBITMQ_BASE}/plugins"
export RABBITMQ_SERVER_START_ARGS=

"${RABBITMQ_HOME}/sbin/rabbitmq-plugins" enable rabbitmq_stomp
"${RABBITMQ_HOME}/sbin/rabbitmq-server" 2>&1 > "${CONSOLE_LOG}" &
RABBITMQ_PID=$!
echo "Started RabbitMQ with PID: ${RABBITMQ_PID}"
sleep 5
cat "${CONSOLE_LOG}"

#
# Run the benchmark
#
cd ${BENCHMARK_HOME}/stomp-benchmark
sbt run --login guest --passcode guest reports/rabbitmq-${RABBITMQ_VERSION}.json

# Kill the server
kill -9 ${RABBITMQ_PID}
