#!/bin/bash
#
# This shell script automates running the stomp-benchmark [1] against the
# Apache Apollo project [2].  This scripts assumes it's it's being run on an
# EC2 Amazon Linux [3] distribution.
#
# [1]: http://github.com/chirino/stomp-benchmark
# [2]: http://activemq.apache.org/apollo
# [3]: http://aws.amazon.com/amazon-linux-ami/
#

#
# Install the apollo snaapshot
#
cd ~
rm apache-apollo-1.0-*-unix-distro.tar.gz
wget 'http://repository.apache.org/service/local/artifact/maven/redirect?r=snapshots&g=org.apache.activemq&a=apache-apollo&v=1.0-SNAPSHOT&e=tar.gz&c=unix-distro'
tar -zxvf apache-apollo-1.0-*-unix-distro.tar.gz
export PATH="${PATH}:~/apache-apollo-1.0-SNAPSHOT/bin"

#
# Create an apollo instance..
#
rm -Rf ~/apollo-broker
apollo create apollo-broker

#
# Install the bdb library
#
cd ~/apache-apollo-1.0-SNAPSHOT/lib
wget http://download.oracle.com/maven/com/sleepycat/je/4.1.6/je-4.1.6.jar
cp ~/apollo-broker/etc/apollo.xml ~/apollo-broker/etc/apollo.xml.bak
# update config to use the bdb store.
sed 's/hawtdb-store/bdb-store/g;' ~/apollo-broker/etc/apollo.xml.bak >  ~/apollo-broker/etc/apollo.xml

#
# Start the apollo broker..
#
nohup ~/apollo-broker/bin/apollo-broker run 2>&1 > ~/apollo-broker/log/console.log &
APOLLO_PID=$!

#
# Install SBT
#
mkdir ~/.ivy2 2> /dev/null
mkdir ~/bin 2> /dev/null
cd ~/bin
wget http://simple-build-tool.googlecode.com/files/sbt-launch-0.7.4.jar
echo '#!/bin/sh
java -server -Xmx4G -XX:MaxPermSize=500m -jar ~/bin/sbt-launch-0.7.4.jar "$*"
' > ~/bin/sbt
chmod a+x sbt

#
# Install git so we can get and run the stomp-benchmark
#
cd ~
sudo yum install -y git
git clone git://github.com/chirino/stomp-benchmark.git
cd stomp-benchmark
sbt update

#
# Run the benchmark
rm apache-apollo-1.0-SNAPSHOT.json
sbt run apache-apollo-1.0-SNAPSHOT

# Kill the apollo
kill -9 ${APOLLO_PID}
