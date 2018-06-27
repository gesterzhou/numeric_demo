package examples;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

public class Person implements Serializable {
  private String name;
  private int revenue;
  private float revenue_float;
  private double revenue_double;
  private long revenue_long;

  public Person() {}

  public Person(String name, String email, String address) {
    this.name = name;
  }

  public Person(int idx) {
    this.name = createName(idx);
    this.revenue = idx*1000;
    this.revenue_float = idx*1000.0f;
    this.revenue_double = idx*1000.0;
    this.revenue_long = idx*1000L;
  }

  static String createName(int idx) {
    return "Tom"+idx+" Zhou";
  }

  public String getName() {
    return name;
  }


  @Override
  public String toString() {
    final StringBuffer sb = new StringBuffer("Person{");
    sb.append("name='").append(name).append('\'');
    sb.append(", revenue=").append(revenue);
    sb.append(", revenue_float=").append(revenue_float);
    sb.append('}');
    return sb.toString();
  }
}
