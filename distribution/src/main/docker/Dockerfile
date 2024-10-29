FROM registry.access.redhat.com/ubi9/openjdk-17
USER root
RUN microdnf install -y less vim && curl -s -L https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64 -o /usr/bin/jq && chmod a+x /usr/bin/jq
EXPOSE 8090
ENV EDITOR=vim HYPERFOIL_CONTAINER=true
COPY maven/bin /deployment/bin/
COPY maven/examples /home/default/examples/
ADD maven/hyperfoil-*-deps.tar /deployment/
ADD maven/hyperfoil-*-hyperfoil.tar /deployment/
RUN mkdir /deployment/extensions
RUN chown -R default /deployment
USER default
# executable permissions in bin/*.sh are lost somewhere
RUN chmod a+x /deployment/bin/*.sh
ENTRYPOINT [ "/deployment/bin/entrypoint.sh" ]
