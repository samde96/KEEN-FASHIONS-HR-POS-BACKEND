package com.company.fashionpos.shared.audit;

import com.company.fashionpos.shared.security.AuthenticatedUser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditEventService {

  private final JdbcTemplate jdbcTemplate;

  public AuditEventService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public void record(
      AuthenticatedUser user,
      UUID branchId,
      String action,
      String targetType,
      UUID targetId,
      Map<String, Object> metadata) {
    jdbcTemplate.update(
        """
        insert into audit_events (
          id,
          organization_id,
          branch_id,
          actor_user_id,
          action,
          target_type,
          target_id,
          metadata_json,
          created_at
        )
        values (?, ?, ?, ?, ?, ?, ?, cast(? as json), now())
        """,
        UUID.randomUUID(),
        user.organizationId(),
        branchId,
        user.userId(),
        action,
        targetType,
        targetId,
        toJson(metadata));
  }

  private String toJson(Map<String, Object> metadata) {
    Map<String, Object> safeMetadata = metadata == null ? Map.of() : new LinkedHashMap<>(metadata);
    StringBuilder json = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, Object> entry : safeMetadata.entrySet()) {
      if (!first) {
        json.append(',');
      }
      first = false;
      json.append('"').append(escape(entry.getKey())).append('"').append(':');
      Object value = entry.getValue();
      if (value == null) {
        json.append("null");
      } else {
        json.append('"').append(escape(String.valueOf(value))).append('"');
      }
    }
    json.append('}');
    return json.toString();
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
