package com.company.fashionpos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FashionPosApplication {

  public static void main(String[] args) {
    RenderDatabaseUrlConfigurer.apply();
    SpringApplication.run(FashionPosApplication.class, args);
  }
}
