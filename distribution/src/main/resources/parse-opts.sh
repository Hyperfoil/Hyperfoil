#!/bin/bash

DEBUG_MODE="${DEBUG:-false}"
DEBUG_PORT="${DEBUG_PORT:-8000}"
GREP="grep"
if [ "$#" -gt 0 ]; then
    case "$1" in
      --debug)
          DEBUG_MODE=true
          if [ -n "$2" ] && [ "$2" = `echo "$2" | sed 's/-//'` ]; then
              DEBUG_PORT=$2
              shift
          fi
          shift
          ;;
    esac
fi

# Set debug settings if not already set
if [ "$DEBUG_MODE" = "true" ]; then
    DEBUG_OPT=`echo $JAVA_OPTS | $GREP "\-agentlib:jdwp"`
    if [ "x$DEBUG_OPT" = "x" ]; then
        JAVA_OPTS="$JAVA_OPTS -agentlib:jdwp=transport=dt_socket,address=$DEBUG_PORT,server=y,suspend=n"
    else
        echo "Debug already enabled in JAVA_OPTS, ignoring --debug argument"
    fi
fi

ROOT=$(dirname $0)/..
CP=$(find $ROOT/lib $ROOT/extensions | tr '\n' ':')
JAVA_OPTS="$JAVA_OPTS \
   -Djava.net.preferIPv4Stack=true \
   -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4j2LogDelegateFactory"
