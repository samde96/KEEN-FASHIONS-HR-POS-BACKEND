package com.company.fashionpos.staff;

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
@RequestMapping("/api/v1")
public class StaffController {

  private final StaffService staffService;
  private final AuthenticatedUserService authenticatedUserService;

  public StaffController(
      StaffService staffService, AuthenticatedUserService authenticatedUserService) {
    this.staffService = staffService;
    this.authenticatedUserService = authenticatedUserService;
  }

  @GetMapping("/roles")
  public List<RoleResponse> listRoles(Principal principal) {
    return staffService.listRoles(authenticatedUserService.current(principal));
  }

  @GetMapping("/permissions")
  public List<PermissionResponse> listPermissions() {
    return staffService.listPermissions();
  }

  @PostMapping("/permissions")
  @ResponseStatus(HttpStatus.CREATED)
  public PermissionResponse createPermission(@Valid @RequestBody PermissionRequest request) {
    return staffService.createPermission(request);
  }

  @PutMapping("/permissions/{permissionId}")
  public PermissionResponse updatePermission(
      @PathVariable UUID permissionId, @Valid @RequestBody PermissionRequest request) {
    return staffService.updatePermission(permissionId, request);
  }

  @PostMapping("/roles")
  @ResponseStatus(HttpStatus.CREATED)
  public RoleResponse createRole(@Valid @RequestBody RoleRequest request, Principal principal) {
    return staffService.createRole(authenticatedUserService.current(principal), request);
  }

  @PutMapping("/roles/{roleId}")
  public RoleResponse updateRole(
      @PathVariable UUID roleId, @Valid @RequestBody RoleRequest request, Principal principal) {
    return staffService.updateRole(authenticatedUserService.current(principal), roleId, request);
  }

  @DeleteMapping("/roles/{roleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteRole(@PathVariable UUID roleId, Principal principal) {
    staffService.deleteRole(authenticatedUserService.current(principal), roleId);
  }

  @GetMapping("/users")
  public List<UserResponse> listUsers(Principal principal) {
    return staffService.listUsers(authenticatedUserService.current(principal));
  }

  @PostMapping("/users")
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse createUser(@Valid @RequestBody UserRequest request, Principal principal) {
    return staffService.createUser(authenticatedUserService.current(principal), request);
  }

  @PutMapping("/users/{userId}")
  public UserResponse updateUser(
      @PathVariable UUID userId, @Valid @RequestBody UserRequest request, Principal principal) {
    return staffService.updateUser(authenticatedUserService.current(principal), userId, request);
  }

  @DeleteMapping("/users/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void disableUser(@PathVariable UUID userId, Principal principal) {
    staffService.disableUser(authenticatedUserService.current(principal), userId);
  }
}
