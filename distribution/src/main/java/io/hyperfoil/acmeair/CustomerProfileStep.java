package io.hyperfoil.acmeair;

import java.util.Collections;
import java.util.List;

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
public class CustomerProfileStep implements Step, ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(CustomerProfileStep.class);
   private Access fromVar;
   private Access toVar;

   public CustomerProfileStep(String fromVar, String toVar) {
      this.fromVar = SessionFactory.access(fromVar);
      this.toVar = SessionFactory.access(toVar);
   }

   @Override
   public boolean invoke(Session session) {
      if (!fromVar.isSet(session)) {
         return false;
      }
      String value = fromVar.getObject(session).toString();
      log.debug("value BEFORE ###" +  value + "###");
      log.debug("value AFTER ###" +  processJSonString(value) + "###");

      toVar.setObject(session, processJSonString(value));
      return true;
   }

   @Override
   public void reserve(Session session) {
      toVar.declareObject(session);
   }

   public String processJSonString(String responseDataAsString) {
       try {
           if (responseDataAsString == null)
               return "{}";
           JSONObject json = (JSONObject) new JSONParser().parse(responseDataAsString);
           JSONObject jsonAddress = (JSONObject) json.get("address");
           jsonAddress.put("streetAddress1",updateAddress(jsonAddress.get("streetAddress1").toString()));
           jsonAddress.put("postalCode",updatePostalCode(jsonAddress.get("postalCode").toString()));
           json.put("password", "password");
           return json.toJSONString();

       } catch (ParseException e) {
           e.printStackTrace();
       } catch (NullPointerException e) {
           e.printStackTrace();
           log.info("NullPointerException in UpdateCustomerFunction - ResponseData =" + responseDataAsString);
       } catch (Exception e) {
           e.printStackTrace();
       }
       return null;
   }

   private String updatePostalCode(String str) {
       int postalCode = Integer.parseInt(str);
       if (postalCode > 99999) {
           return "10000";
       } else {
           return (postalCode + 1) + "";
       }
   }

   private String updateAddress(String str) {
       String num = str.substring(0, str.indexOf(" "));
       return (Integer.parseInt(num) + 1) + str.substring(str.indexOf(" "));
   }


   @MetaInfServices(StepBuilder.class)
   @Name("updateProfileData")
   public static class Builder extends BaseStepBuilder<Builder> implements InitFromParam<Builder> {
      private String fromVar;
      private String toVar;

      @Override
      public Builder init(String param) {
         return fromVar(param).toVar(param);
      }

      public Builder fromVar(String fromVar) {
         this.fromVar = fromVar;
         return this;
      }

      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }


      @Override
      public List<Step> build() {
         if (fromVar == null || toVar == null) {
            throw new BenchmarkDefinitionException("Missing one of the required attributes!");
         }
         return Collections.singletonList(new CustomerProfileStep(fromVar, toVar));
      }
   }
}
