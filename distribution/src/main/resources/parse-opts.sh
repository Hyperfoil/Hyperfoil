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

# Code taken from https://stackoverflow.com/questions/7334754/correct-way-to-check-java-version-from-bash-script
jdk_version() {
  local result
  local java_cmd
  if [[ -n $(type -p java) ]]
  then
    java_cmd=java
  elif [[ (-n "$JAVA_HOME") && (-x "$JAVA_HOME/bin/java") ]]
  then
    java_cmd="$JAVA_HOME/bin/java"
  fi
  local IFS=$'\n'
  # remove \r for Cygwin
  local lines=$("$java_cmd" -Xms32M -Xmx32M -version 2>&1 | tr '\r' '\n')
  if [[ -z $java_cmd ]]
  then
    result=no_java
  else
    for line in $lines; do
      if [[ (-z $result) && ($line = *"version \""*) ]]
      then
        local ver=$(echo $line | sed -e 's/.*version "\(.*\)"\(.*\)/\1/; 1q')
        # on macOS, sed doesn't support '?'
        if [[ $ver = "1."* ]]
        then
          result=$(echo $ver | sed -e 's/1\.\([0-9]*\)\(.*\)/\1/; 1q')
        else
          result=$(echo $ver | sed -e 's/\([0-9]*\)\(.*\)/\1/; 1q')
        fi
      fi
    done
  fi
  echo "$result"
}

if [ -z "$NO_JAVA_CHECK" ]; then
  JAVA_VERSION="$(jdk_version)"
  if [ $JAVA_VERSION = "no_java" ]; then
    echo "Cannot find Java. Hyperfoil requires Java 11 or newer."
    echo "If you want to skip this check please export NO_JAVA_CHECK=true"
    exit 1;
  elif [ $JAVA_VERSION -lt 11 ]; then
    echo "Found Java $JAVA_VERSION but Hyperfoil requires Java 11 or newer."
    echo "If you want to skip this check please export NO_JAVA_CHECK=true"
    exit 1;
  fi
fi