package com.company.fashionpos.sales;

import com.company.fashionpos.branch.BranchService;
import com.company.fashionpos.shared.security.AuthenticatedUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HotelSalesSummaryService {
  private final JdbcTemplate jdbc;
  private final BranchService branches;

  public HotelSalesSummaryService(JdbcTemplate jdbc, BranchService branches) {
    this.jdbc = jdbc;
    this.branches = branches;
  }

  @Transactional(readOnly = true)
  public HotelSalesSummary summary(AuthenticatedUser user, UUID branchId) {
    if (branchId != null) branches.requireBranchAccess(user, branchId);

    List<UUID> scopedBranches;
    if (branchId != null) {
      scopedBranches = List.of(branchId);
    } else if (!user.branchIds().isEmpty()) {
      scopedBranches = user.branchIds().stream().toList();
    } else if (user.isCashier() || user.isHotelSalesManager()) {
      return new HotelSalesSummary(java.math.BigDecimal.ZERO, 0, 0);
    } else {
      scopedBranches = List.of();
    }

    String branchFilter = "";
    List<Object> parameters = new ArrayList<>();
    parameters.add(user.organizationId());
    if (!scopedBranches.isEmpty()) {
      branchFilter =
          " and o.branch_id in ("
              + String.join(",", java.util.Collections.nCopies(scopedBranches.size(), "?"))
              + ")";
      parameters.addAll(scopedBranches);
    }
    return jdbc.queryForObject(
        """
        select coalesce(sum(o.total),0) as total_amount, count(*) as transactions,
          coalesce(sum(coalesce(lines.items_sold,0)),0) as items_sold
        from hotel_orders o
        left join (
          select order_id,sum(quantity) as items_sold
          from hotel_order_lines group by order_id
        ) lines on lines.order_id=o.id
        where o.organization_id=? and o.status='CLOSED'
        """
            + branchFilter,
        (rs, row) ->
            new HotelSalesSummary(
                rs.getBigDecimal("total_amount"),
                rs.getLong("transactions"),
                rs.getLong("items_sold")),
        parameters.toArray());
  }
}
