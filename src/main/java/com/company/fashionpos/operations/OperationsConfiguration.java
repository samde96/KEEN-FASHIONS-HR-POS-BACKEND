package com.company.fashionpos.operations;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OperationsConfiguration {
  @Bean
  public Clock operationsClock() {
    return Clock.systemUTC();
  }
}
