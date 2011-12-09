#!/bin/bash
#
# This shell script creates the html index file which displays
# results of the benchmarks.
#

true \
${REPORTS_HOME:=$1} \
${REPORTS_HOME:=`pwd`/report}

dir=`dirname "$0"`
cd "${REPORTS_HOME}"
LOGS=`ls *.log 2> /dev/null` 
files=`ls *.json | sed 's|.json|",|' | sed 's|^|"|'`
files=`echo $files | sed 's|,$||'` #trim the trailing comma
cd - > /dev/null

cat "${dir}/../reports/template.html" | sed "s|PRODUCT_LIST|$files|" > "${REPORTS_HOME}/index.html"
mkdir -p "${REPORTS_HOME}/resources"
cp "${dir}/../reports/resources/"* "${REPORTS_HOME}/resources"

if [ ! -f "${REPORTS_HOME}/server-info.html" ] ; then
  
  cat > "${REPORTS_HOME}/server-info.html" <<EOF
  <h2>Machine Details</h2>
  <p>
  The box used to produce these benchmark reults was:
  <pre>$(uname -v)</pre>
  </p>
  <h2>Console logs of the benchmarked servers</h2>
  <ul>
EOF

  for f in $LOGS ; do
  cat >> "${REPORTS_HOME}/server-info.html" <<EOF
  <li><a href="$f">$f</li>
EOF
done

  cat >> "${REPORTS_HOME}/server-info.html" <<EOF
  </ul>
EOF

fi
