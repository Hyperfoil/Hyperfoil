#!/bin/bash

source $(dirname $0)/parse-opts.sh
java $@ -cp $CP $JAVA_OPTS io.hyperfoil.Hyperfoil\$Controller
