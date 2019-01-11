#!/bin/bash

ROOT=$(dirname $0)/..
CP=$(find $ROOT/lib | tr '\n' ':')
java $@ -cp $CP \
   -Djava.net.preferIPv4Stack=true \
   -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4j2LogDelegateFactory \
   io.sailrocket.SailRocket\$Controller
