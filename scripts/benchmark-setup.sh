#!/bin/bash
mkdir -p ${BENCHMARK_HOME}

#
# Install SBT
#
if [ ! -f "${BENCHMARK_HOME}/bin/sbt" ] ; then
  mkdir ~/.ivy2 2> /dev/null
  mkdir "${BENCHMARK_HOME}/bin" 2> /dev/null
  cd "${BENCHMARK_HOME}/bin"
  wget http://simple-build-tool.googlecode.com/files/sbt-launch-0.7.4.jar
  echo '#!/bin/sh
  java -server -Xmx4G -XX:MaxPermSize=500m -jar ${BENCHMARK_HOME}/bin/sbt-launch-0.7.4.jar "$*"
  ' > ${BENCHMARK_HOME}/bin/sbt
  chmod a+x "${BENCHMARK_HOME}/bin/sbt"
fi

#
# Install git so we can get and run the stomp-benchmark
#
which git > /dev/null
if [ $? -ne 0 ] ; then
  cd "${BENCHMARK_HOME}"
  echo "Installing git..."
  sudo yum install -y git
fi 

if [ ! -d "${BENCHMARK_HOME}/stomp-benchmark" ] ; then
  cd "${BENCHMARK_HOME}"
  git clone git://github.com/chirino/stomp-benchmark.git
  cd stomp-benchmark
  sbt update
else 
  cd "${BENCHMARK_HOME}/stomp-benchmark"
  git pull
  sbt update
fi

kilall -9 java erl epmd apollo 2> /dev/null
