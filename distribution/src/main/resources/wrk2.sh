#!/bin/bash

source $(dirname $0)/parse-opts.sh
ARGS=("$@")
[ $# -eq 0 ] && ARGS=("--help")
java -cp $CP $JAVA_OPTS io.hyperfoil.cli.commands.Wrk2 "${ARGS[@]}"
