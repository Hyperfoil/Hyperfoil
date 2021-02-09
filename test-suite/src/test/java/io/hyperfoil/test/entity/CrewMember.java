package io.hyperfoil.test.entity;

public class CrewMember {
   private String title;
   private String firstName;
   private String lastName;

   public CrewMember() {
   }

   public CrewMember(String title, String firstName, String lastName) {
      this.title = title;
      this.firstName = firstName;
      this.lastName = lastName;
   }

   public String getTitle() {
      return title;
   }

   public void setTitle(String title) {
      this.title = title;
   }

   public String getFirstName() {
      return firstName;
   }

   public void setFirstName(String firstName) {
      this.firstName = firstName;
   }

   public String getLastName() {
      return lastName;
   }

   public void setLastName(String lastName) {
      this.lastName = lastName;
   }
}
