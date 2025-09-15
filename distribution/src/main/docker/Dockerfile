FROM registry.access.redhat.com/ubi9/openjdk-21
USER root
RUN microdnf install -y less vim && curl -s -L https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64 -o /usr/bin/jq && chmod a+x /usr/bin/jq
EXPOSE 8090
ENV EDITOR=vim HYPERFOIL_CONTAINER=true
COPY target/distribution/bin /deployment/bin/
COPY target/distribution/templates /deployment/templates
COPY target/distribution/examples /home/default/examples/
COPY target/distribution/lib /deployment/lib
RUN mkdir /deployment/extensions
RUN chown -R default /deployment
USER default
# executable permissions in bin/*.sh are lost somewhere
RUN chmod a+x /deployment/bin/*.sh
ENTRYPOINT [ "/deployment/bin/entrypoint.sh" ]
