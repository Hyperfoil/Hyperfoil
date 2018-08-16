package io.sailrocket.core.test;

import java.util.ArrayList;
import java.util.Collection;

public class Fleet {
   private String name;
   private Collection<String> bases = new ArrayList<>();
   private Collection<Ship> ships = new ArrayList<>();

   public Fleet(String name) {
      this.name = name;
   }

   public Fleet addShip(Ship ship) {
      ships.add(ship);
      return this;
   }

   public Fleet addBase(String base) {
      bases.add(base);
      return this;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public Collection<String> getBases() {
      return bases;
   }

   public void setBases(Collection<String> bases) {
      this.bases = bases;
   }

   public Collection<Ship> getShips() {
      return ships;
   }

   public void setShips(Collection<Ship> ships) {
      this.ships = ships;
   }
}
