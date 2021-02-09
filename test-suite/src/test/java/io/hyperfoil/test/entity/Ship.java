package io.hyperfoil.test.entity;

import java.util.ArrayList;
import java.util.Collection;

public class Ship {
   private String name;
   private long dwt;
   private Collection<CrewMember> crew = new ArrayList<>();

   public Ship(String name) {
      this.name = name;
   }

   public Ship dwt(long dwt) {
      this.dwt = dwt;
      return this;
   }

   public Ship addCrew(CrewMember crewMember) {
      crew.add(crewMember);
      return this;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public long getDwt() {
      return dwt;
   }

   public void setDwt(long dwt) {
      this.dwt = dwt;
   }

   public Collection<CrewMember> getCrew() {
      return crew;
   }

   public void setCrew(Collection<CrewMember> crew) {
      this.crew = crew;
   }
}
