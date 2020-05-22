FROM registry.redhat.io/openjdk/openjdk-11-rhel8
USER root
RUN yum install -y less && curl -s -L https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64 -o /usr/bin/jq && chmod a+x /usr/bin/jq
USER jboss
EXPOSE 8090
COPY maven /deployment/
CMD /deployment/bin/controller.sh
