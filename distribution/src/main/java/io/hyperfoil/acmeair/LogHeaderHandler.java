package io.hyperfoil.acmeair;




import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.HeaderHandler;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.core.session.SessionFactory;


/**
 * This is a debugging step.
 */
public class LogHeaderHandler implements HeaderHandler {
    private static final Logger log = LoggerFactory.getLogger(LogHeaderHandler.class);

    private final String message;
    private Access var;

    public LogHeaderHandler(String message, String var) {
        this.message = message;
        this.var = SessionFactory.access(var);
    }

    @Override
    public void handleHeader(HttpRequest request, CharSequence header, CharSequence value) {
        log.info("Message : " + message);
        //       log.info("Var : " + var.getVar(request.session).objectValue());
        //       log.info("Var Object : " + var.getObject(request.session));
    }

    // Make this builder loadable as service
    @MetaInfServices(HeaderHandler.Builder.class)
    // This is the step name that will be used in the YAML
    @Name("log")
    public static class Builder implements HeaderHandler.Builder {
        String message;
        String var;

        /**
         * Message format pattern. Use <code>{}</code> to mark the positions for variables in the logged message.
         *
         * @param message Message format pattern.
         * @return Self.
         */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder var(String var) {
            this.var = var;
            return this;
        }

        @Override
        public HeaderHandler build() {
            return new LogHeaderHandler(message, var);
        }
    }


}
