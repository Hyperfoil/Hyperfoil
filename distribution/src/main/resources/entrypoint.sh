#!/bin/bash

DIR=$(dirname $0)
case ${1,,} in
"cli")
  shift
  $DIR/cli.sh $@ ;;
"standalone")
  shift
  $DIR/standalone.sh $@ ;;
"wrk")
  shift
  $DIR/wrk.sh $@ ;;
"wrk2")
  shift
  $DIR/wrk2.sh $@ ;;
"sh" | "bash")
  bash ;;
*)
  $DIR/controller.sh $@ ;;
esac
