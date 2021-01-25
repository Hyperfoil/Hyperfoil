#!/bin/bash

DIR=$(dirname $0)
case ${1,,} in
"cli")
  shift
  $DIR/cli.sh $@ ;;
"standalone")
  shift
  $DIR/standalone.sh $@ ;;
"sh" | "bash")
  bash ;;
*)
  $DIR/controller.sh $@ ;;
esac
