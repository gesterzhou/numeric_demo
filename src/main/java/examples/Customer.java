package examples;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class Customer implements Serializable {
  private String name;
  private int revenue;
  private Collection<Person> contacts;

  public Customer() {}

  public void addContact(Person contact) {
    this.contacts.add(contact);
  }

  public Customer(int idx) {
    this.name = Person.createName(idx);
    this.revenue = 1000 * idx;
    this.contacts = Collections.singleton(new Person(idx));
  }

  public String getName() {
    return name;
  }

  public double getRevenue() {
    return revenue;
  }

  public Collection<Person> getContacts() {
    return contacts;
  }

  @Override
  public String toString() {
    final StringBuffer sb = new StringBuffer("Customer{");
    sb.append("name='").append(name).append('\'');
    sb.append(", revenue=").append(revenue);
    sb.append(", contacts='").append(contacts).append('\'');
    sb.append('}');
    return sb.toString();
  }

}
