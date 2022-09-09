#!/bin/bash

export LOG_FILE="${TMPDIR:-/tmp}/hyperfoil/hyperfoil.log"
export LOG_LEVEL="TRACE"
source $(dirname $0)/parse-opts.sh
# To make identification easier by tools as pgrep/pkill that have cmdline length limit
# we'll add the harmless system option -Dio.hyperfoil.standalone
java -Dio.hyperfoil.standalone $@ -cp $CP $JAVA_OPTS io.hyperfoil.Hyperfoil\$Standalone
