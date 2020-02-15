package io.hyperfoil.acmeair;

import java.util.Collections;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;


/**
 * Example step for <a href="http://hyperfoil.io/quickstart/quickstart8">Custom steps tutorial</a>
 */
public class FlightProcessorStep implements Step, ResourceUtilizer {
    private static final Logger log = LoggerFactory.getLogger(FlightProcessorStep.class);
    private Access fromVar;
    private Access isAvailable;
    private Access toFlightId;
    private Access toFlightSegId;
    private Access retFlightId;
    private Access retFlightSegId;
    private boolean oneWay;
    private FlightContext ctx;

    public FlightProcessorStep(String fromVar, String isAvailable, String toFlightId, String toFlightSegId, String retFlightId, String retFlightSegId, boolean oneWay) {
        this.fromVar = SessionFactory.access(fromVar);
        this.isAvailable = SessionFactory.access(isAvailable);
        this.toFlightId = SessionFactory.access(toFlightId);
        this.toFlightSegId = SessionFactory.access(toFlightSegId);
        this.retFlightId = SessionFactory.access(retFlightId);
        this.retFlightSegId = SessionFactory.access(retFlightSegId);
        this.oneWay = oneWay;
    }

    @Override
    public boolean invoke(Session session) {
        if (!fromVar.isSet(session)) {
            return false;
        }
        String value = fromVar.getObject(session).toString();
        ctx = isFlightAvailable(value, oneWay);

        log.debug("Flight Info ###" +  value + "###");
        log.debug("isAvailable code ###" +  ctx.getIsFlightAvailable() + "###");
        isAvailable.setInt(session, ctx.getIsFlightAvailable());
        toFlightId.setObject(session, ctx.getToFlightId());
        toFlightSegId.setObject(session, ctx.getToFlightSegId());
        retFlightId.setObject(session, ctx.getRetFlightId());
        retFlightSegId.setObject(session, ctx.getRetFlightSegId());
        return true;
    }

    @Override
    public void reserve(Session session) {
        isAvailable.declareInt(session);
        toFlightId.declareObject(session);
        toFlightSegId.declareObject(session);
        retFlightId.declareObject(session);
        retFlightSegId.declareObject(session);
    }

    private class FlightContext {
        private String toFlightId;
        private String toFlightSegId;
        private String retFlightId;
        private String retFlightSegId;
        private int isFlightAvailable;

        private void setToFlightId(String toFlightId) {
            this.toFlightId = toFlightId;
        }

        private void setToFlightSegId(String toFlightSegId) {
            this.toFlightSegId = toFlightSegId;
        }

        private void setRetFlightId(String retFlightId) {
            this.retFlightId = retFlightId;
        }

        private void setRetFlightSegId(String retFlightSegId) {
            this.retFlightSegId = retFlightSegId;
        }

        private void setIsFlightAvailable(int isFlightAvailable) {
            this.isFlightAvailable = isFlightAvailable;
        }

        private String getToFlightId() {
            return toFlightId;
        }

        private String getToFlightSegId() {
            return toFlightSegId;
        }

        private String getRetFlightId() {
            return retFlightId;
        }

        private String getRetFlightSegId() {
            return retFlightSegId;
        }

        private int getIsFlightAvailable() {
            return isFlightAvailable;
        }
    }

    public FlightContext isFlightAvailable(String responseData, boolean oneWay) {

        FlightContext ctx = new FlightContext();

        try {
            JSONObject json = (JSONObject) new JSONParser().parse(responseData);
            JSONArray tripFlights = (JSONArray) json.get("tripFlights");

            if (tripFlights == null || tripFlights.size() == 0) {
                ctx.setIsFlightAvailable(0);
                return ctx;
            }
            for (int counter = 0; counter <= tripFlights.size() - 1; counter++) {
                JSONObject jsonTripFlight = (JSONObject) tripFlights.get(counter);
                JSONArray jsonFlightOptions = (JSONArray) jsonTripFlight.get("flightsOptions");

                if (jsonFlightOptions.size() > 0) {
                    log.debug("Flight available ###" +  jsonFlightOptions.toString() + "###");
                    JSONObject flightOption0 = (JSONObject) jsonFlightOptions.get(0);
                    if (counter == 0) {
                        ctx.setToFlightId((String) flightOption0.get("_id"));
                        ctx.setToFlightSegId((String) flightOption0.get("flightSegmentId"));
                    } else if (counter == 1) {
                        ctx.setRetFlightId((String) flightOption0.get("_id"));
                        ctx.setRetFlightSegId((String) flightOption0.get("flightSegmentId"));
                    }
                    if (oneWay) {
                        ctx.setIsFlightAvailable(1);
                        break;
                    }
                } else {
                    ctx.setIsFlightAvailable(0);
                    return ctx;
                }
            }

            ctx.setIsFlightAvailable(1);
            return ctx;

        } catch (ParseException e) {
            log.debug("responseDataAsString = " + responseData);
            e.printStackTrace();
            ctx.setIsFlightAvailable(0);
            return ctx;
        }
    }

    @MetaInfServices(StepBuilder.class)
    @Name("flightProcessor")
    public static class Builder extends BaseStepBuilder<Builder> implements InitFromParam<Builder> {
        private String fromVar;
        private String isAvailable;
        private String toFlightId;
        private String toFlightSegId;
        private String retFlightId;
        private String retFlightSegId;
        private boolean oneWay;

        @Override
        public Builder init(String param) {
            return fromVar(param).isAvailable(param);
        }

        public Builder fromVar(String fromVar) {
            this.fromVar = fromVar;
            return this;
        }

        public Builder isAvailable(String isAvailable) {
            this.isAvailable = isAvailable;
            return this;
        }

        public Builder toFlightId(String toFlightId) {
            this.toFlightId = toFlightId;
            return this;
        }

        public Builder toFlightSegId(String toFlightSegId) {
            this.toFlightSegId = toFlightSegId;
            return this;
        }

        public Builder retFlightId(String retFlightId) {
            this.retFlightId = retFlightId;
            return this;
        }

        public Builder retFlightSegId(String retFlightSegId) {
            this.retFlightSegId = retFlightSegId;
            return this;
        }


        public Builder oneWay(boolean oneWay) {
            this.oneWay = oneWay;
            return this;
        }

        @Override
        public List<Step> build() {
            if (fromVar == null || isAvailable == null || toFlightId == null || toFlightSegId == null || retFlightId == null || retFlightSegId == null) {
                throw new BenchmarkDefinitionException("Missing one of the required attributes!");
            }
            return Collections.singletonList(new FlightProcessorStep(fromVar, isAvailable, toFlightId, toFlightSegId, retFlightId, retFlightSegId, oneWay));
        }
    }
}
