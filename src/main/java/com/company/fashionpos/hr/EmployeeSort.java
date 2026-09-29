package com.company.fashionpos.hr;

import java.util.Comparator;

final class EmployeeSort {

  private EmployeeSort() {}

  static Comparator<Employee> order(String sort) {
    if ("joiningDate,desc".equalsIgnoreCase(sort)) {
      return Comparator.comparing(
              Employee::getJoiningDate, Comparator.nullsLast(Comparator.naturalOrder()))
          .reversed()
          .thenComparing(Employee::getLastName)
          .thenComparing(Employee::getFirstName);
    }
    if ("joiningDate,asc".equalsIgnoreCase(sort)) {
      return Comparator.comparing(
              Employee::getJoiningDate, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(Employee::getLastName)
          .thenComparing(Employee::getFirstName);
    }
    if ("employeeNumber,desc".equalsIgnoreCase(sort)) {
      return Comparator.comparing(Employee::getEmployeeNumber).reversed();
    }
    if ("employeeNumber,asc".equalsIgnoreCase(sort)) {
      return Comparator.comparing(Employee::getEmployeeNumber);
    }
    return Comparator.comparing(Employee::getLastName)
        .thenComparing(Employee::getFirstName)
        .thenComparing(Employee::getEmployeeNumber);
  }
}
