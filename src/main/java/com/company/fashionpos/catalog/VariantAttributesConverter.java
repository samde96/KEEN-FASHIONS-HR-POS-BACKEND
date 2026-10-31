package com.company.fashionpos.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;

@Converter
public class VariantAttributesConverter implements AttributeConverter<Map<String, String>, String> {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(Map<String, String> value) {
    try {
      return JSON.writeValueAsString(value == null ? Map.of() : value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid variant attributes", e);
    }
  }

  @Override
  public Map<String, String> convertToEntityAttribute(String value) {
    try {
      return value == null ? Map.of() : JSON.readValue(value, new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid variant attributes", e);
    }
  }
}
