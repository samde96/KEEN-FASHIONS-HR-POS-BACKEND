package com.company.fashionpos.operations;

import com.company.fashionpos.branch.BranchService;
import com.company.fashionpos.catalog.ProductRepository;
import com.company.fashionpos.inventory.InventoryRepository;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleReversalService {
  public record Request(
      @NotBlank @Pattern(regexp = "REFUND|CANCELLATION") String kind,
      @NotBlank @Size(max = 500) String reason) {}

  private final JdbcTemplate jdbc;
  private final BranchService branches;
  private final ProductRepository products;
  private final InventoryRepository inventory;

  public SaleReversalService(
      JdbcTemplate jdbc,
      BranchService branches,
      ProductRepository products,
      InventoryRepository inventory) {
    this.jdbc = jdbc;
    this.branches = branches;
    this.products = products;
    this.inventory = inventory;
  }

  public List<Map<String, Object>> list(AuthenticatedUser user) {
    return jdbc
        .queryForList(
            "select * from sale_reversals where organization_id=? order by created_at desc",
            user.organizationId())
        .stream()
        .filter(
            r -> user.branchIds().isEmpty() || user.branchIds().contains((UUID) r.get("branch_id")))
        .toList();
  }

  @Transactional
  public Map<String, Object> reverse(AuthenticatedUser user, UUID saleId, Request request) {
    var sales =
        jdbc.queryForList(
            "select * from sales where id=? and organization_id=? for update",
            saleId,
            user.organizationId());
    if (sales.isEmpty()) throw new EntityNotFoundException("Sale not found");
    var sale = sales.get(0);
    UUID branch = (UUID) sale.get("branch_id");
    branches.requireBranchAccess(user, branch);
    var existing = jdbc.queryForList("select * from sale_reversals where sale_id=?", saleId);
    if (!existing.isEmpty()) {
      if (!existing.get(0).get("kind").equals(request.kind()))
        throw new IllegalArgumentException("Sale has already been reversed");
      return existing.get(0);
    }
    for (var line :
        jdbc.queryForList(
            "select product_id,quantity from sale_lines where sale_id=? order by product_id",
            saleId)) {
      UUID product = (UUID) line.get("product_id");
      products.findForMutation(product, user.organizationId()).orElseThrow();
      var item = inventory.findForMutation(user.organizationId(), branch, product).orElseThrow();
      item.addStock(((Number) line.get("quantity")).intValue());
      inventory.save(item);
    }
    jdbc.update(
        "insert into sale_reversals (id,organization_id,branch_id,sale_id,user_id,kind,reason,amount,tax_amount) values (?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        user.organizationId(),
        branch,
        saleId,
        user.userId(),
        request.kind(),
        request.reason().trim(),
        sale.get("total_amount"),
        sale.get("tax_amount"));
    return jdbc.queryForMap("select * from sale_reversals where sale_id=?", saleId);
  }
}
