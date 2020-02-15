package io.hyperfoil.acmeair;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.HeaderHandler;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.handler.codec.http.HttpHeaderNames;

/**
 * Extract cookies to variable
 */
public class StoreHeaderHandler implements HeaderHandler, ResourceUtilizer {
    private static final Logger log = LoggerFactory.getLogger(StoreHeaderHandler.class);
    private String name;
    private Access toVar;

    public StoreHeaderHandler(String name, String toVar) {
        log.debug("StoreHeaderHandler constructor name param ###" +  name + "###");
        this.name = name;
        this.toVar = SessionFactory.access(toVar);
    }

    // Make this builder loadable as service
    @MetaInfServices(HeaderHandler.Builder.class)
    // This is the step name that will be used in the YAML
    @Name("cookie")
    public static class Builder implements HeaderHandler.Builder {
        private String name;
        private String toVar;

        @Override
        public HeaderHandler build() {
            return new StoreHeaderHandler(name, toVar);
        }

        public Builder name(String param) {
            log.debug("StoreHeaderHandler.Builder.name param ###" +  param + "###");
            this.name = param;
            return this;
        }

        public Builder toVar(String param) {
            log.debug("StoreHeaderHandler.Builder.toVar param ###" +  param + "###");
            this.toVar = param;
            return this;
        }
    }



    @Override
    public void handleHeader(HttpRequest request, CharSequence header, CharSequence value) {
        // TODO Auto-generated method stub

        if (HttpHeaderNames.SET_COOKIE.regionMatches(true, 0, header, 0, Math.min(header.length(), HttpHeaderNames.SET_COOKIE.length()))) {

            log.debug("StoreHeaderHandler.Builder.handleHeader cookie key ###" +  name + "###");
            log.debug("StoreHeaderHandler.Builder.handleHeader header ###" +  header + "###");
            log.debug("StoreHeaderHandler.Builder.handleHeader value ###" +  value + "###");


            if (value.toString().contains(name)) {

                Pattern pattern = Pattern.compile("=(.*);");

                Matcher matcher = pattern.matcher(value);
                if (matcher.find()) {
                    log.debug("StoreHeaderHandler.Builder.handleHeader " + name + " ###" +  matcher.group(1) + "###");
                    toVar.setObject(request.session, matcher.group(1));
                } else {
                    log.debug("StoreHeaderHandler.Builder.handleHeader match not found for " + name + " ###" +  value + "###");
                    toVar.setObject(request.session, "");
                }
            }
        }
    }


    @Override
    public void reserve(Session session) {
        // TODO Auto-generated method stub
        toVar.declareObject(session);
    }
}
