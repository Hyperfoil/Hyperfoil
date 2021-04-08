FROM registry.access.redhat.com/ubi8/openjdk-11
USER root
RUN dnf install -y less vim && curl -s -L https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64 -o /usr/bin/jq && chmod a+x /usr/bin/jq
EXPOSE 8090
ENV EDITOR=vim HYPERFOIL_CONTAINER=true
COPY maven/bin /deployment/bin/
COPY maven/templates /deployment/templates
COPY maven/examples /home/jboss/examples/
ADD maven/hyperfoil-*-deps.tar /deployment/
ADD maven/hyperfoil-*-hyperfoil.tar /deployment/
RUN mkdir /deployment/extensions
RUN chown -R jboss /deployment
USER jboss
# executable permissions in bin/*.sh are lost somewhere
RUN chmod a+x /deployment/bin/*.sh
ENTRYPOINT [ "/deployment/bin/entrypoint.sh" ]
