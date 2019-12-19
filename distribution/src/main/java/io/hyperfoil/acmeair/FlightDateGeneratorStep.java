package io.hyperfoil.acmeair;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Random;

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
public class FlightDateGeneratorStep implements Step, ResourceUtilizer {
    private static final Logger log = LoggerFactory.getLogger(FlightDateGeneratorStep.class);
    private Access fromDate;
    private Access returnDate;
   private FlightDates dates;

    public FlightDateGeneratorStep(String fromDate, String returnDate) {
        this.fromDate = SessionFactory.access(fromDate);
        this.returnDate = SessionFactory.access(returnDate);
    }

    @Override
    public boolean invoke(Session session) {

        dates = getFlightDates();

        log.debug("From Date ###" +  dates.getFromDate() + "###");
        log.debug("Retrun Date ###" +  dates.getReturnDate() + "###");
        fromDate.setObject(session, dates.getFromDate());
        returnDate.setObject(session, dates.getReturnDate());
        return true;
    }

    @Override
    public void reserve(Session session) {
        fromDate.declareObject(session);
        returnDate.declareObject(session);
    }

    private static class FlightDates  implements Serializable {
        private String fromDate;
        private String returnDate;

        private void setFromDate(String fromDate) {
            this.fromDate = fromDate;
        }
        private void setReturnDate(String returnDate) {
            this.returnDate = returnDate;
        }

        private String getFromDate() {
            return fromDate;
        }

        private String getReturnDate() {
            return returnDate;
        }
    }

    public FlightDates getFlightDates() {

        FlightDates dates = new FlightDates();

        SimpleDateFormat date_format = new SimpleDateFormat(
                "EEE MMM dd 00:00:00 z yyyy");
           Calendar fromDate = Calendar.getInstance();
           fromDate.add(Calendar.DATE, new Random().nextInt(6));
            dates.setFromDate(date_format.format(fromDate.getTime()));


            Calendar returnDate = Calendar.getInstance();
            returnDate.add(Calendar.DATE, new Random().nextInt(7) + 6);
            dates.setReturnDate(date_format.format(returnDate.getTime()));

            return dates;
    }

    @MetaInfServices(StepBuilder.class)
    @Name("flightDates")
    public static class Builder extends BaseStepBuilder<Builder> implements InitFromParam<Builder> {
        private String fromDate;
        private String returnDate;

        @Override
        public Builder init(String param) {
            return this;
        }

        public Builder fromDate(String fromDate) {
            this.fromDate = fromDate;
            return this;
        }

        public Builder returnDate(String returnDate) {
            this.returnDate = returnDate;
            return this;
        }

        @Override
        public List<Step> build() {
            if (fromDate == null) {
                throw new BenchmarkDefinitionException("Missing one of the required attributes!");
            }
            return Collections.singletonList(new FlightDateGeneratorStep(fromDate, returnDate));
        }
    }
}
