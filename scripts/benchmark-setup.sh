#!/bin/bash
mkdir -p ${WORKSPACE}
mkdir -p ${REPORTS_HOME}

#
# Install SBT
#
if [ ! -f "${WORKSPACE}/bin/sbt" ] ; then
  mkdir ~/.ivy2 2> /dev/null
  mkdir "${WORKSPACE}/bin" 2> /dev/null
  cd "${WORKSPACE}/bin"
  wget http://simple-build-tool.googlecode.com/files/sbt-launch-0.7.4.jar
  cat > ${WORKSPACE}/bin/sbt <<EOF
#!/bin/sh
java -server -Xmx2G -XX:MaxPermSize=500m -jar ${WORKSPACE}/bin/sbt-launch-0.7.4.jar "\$*"
EOF
  chmod a+x "${WORKSPACE}/bin/sbt"
fi

#
# Install git so we can get and run the stomp-benchmark
#
which git > /dev/null
if [ $? -ne 0 ] ; then
  cd "${WORKSPACE}"
  echo "Installing git..."
  sudo yum install -y git
fi 

if [ ! -d "${WORKSPACE}/stomp-benchmark" ] ; then
  cd "${WORKSPACE}"
  git clone git://github.com/chirino/stomp-benchmark.git
  cd stomp-benchmark
else 
  cd "${WORKSPACE}/stomp-benchmark"
  git pull
fi
"${WORKSPACE}/bin/sbt" update
  
