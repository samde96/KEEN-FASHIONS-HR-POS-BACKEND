package com.company.fashionpos.branch;

import com.company.fashionpos.shared.security.AuthenticatedUserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {

  private final BranchService branchService;
  private final AuthenticatedUserService authenticatedUserService;

  public BranchController(
      BranchService branchService, AuthenticatedUserService authenticatedUserService) {
    this.branchService = branchService;
    this.authenticatedUserService = authenticatedUserService;
  }

  @GetMapping
  public List<BranchResponse> list(Principal principal) {
    return branchService.listAuthorizedBranches(authenticatedUserService.current(principal));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BranchResponse create(@Valid @RequestBody BranchRequest request, Principal principal) {
    return branchService.create(authenticatedUserService.current(principal), request);
  }

  @PutMapping("/{branchId}")
  public BranchResponse update(
      @PathVariable UUID branchId, @Valid @RequestBody BranchRequest request, Principal principal) {
    return branchService.update(authenticatedUserService.current(principal), branchId, request);
  }

  @DeleteMapping("/{branchId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@PathVariable UUID branchId, Principal principal) {
    branchService.deactivate(authenticatedUserService.current(principal), branchId);
  }
}
