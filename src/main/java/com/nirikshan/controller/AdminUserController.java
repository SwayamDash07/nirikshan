package com.nirikshan.controller;

import com.nirikshan.dto.AuthRequests.*;
import com.nirikshan.dto.AuthResponses.*;
import com.nirikshan.model.*;
import com.nirikshan.repository.*;
import com.nirikshan.security.CurrentUser;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/admin")
public class AdminUserController {
    private final UserRepository users;
    private final ZoneRepository zones;
    private final SecurityInstructionRepository instructions;
    private final CurrentUser current;
    private final PasswordEncoder passwords;
    private final SecureRandom random = new SecureRandom();

    public AdminUserController(UserRepository users, ZoneRepository zones, SecurityInstructionRepository instructions, CurrentUser current, PasswordEncoder passwords) {
        this.users = users;
        this.zones = zones;
        this.instructions = instructions;
        this.current = current;
        this.passwords = passwords;
    }

    @PostMapping("/users")
    public CreatedUser create(@Valid @RequestBody CreateUser request) {
        User actor = current.get();
        if (request.role() == UserRole.CITIZEN) throw new IllegalArgumentException("Admins can create SECURITY or ADMIN accounts only");
        if (request.role() == UserRole.ADMIN && !isMainAdmin(actor)) throw new IllegalArgumentException("Only the main administrator can create administrators");

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) throw new IllegalArgumentException("An account already exists for this email");
        Zone zone = null;
        if (request.role() == UserRole.SECURITY) {
            if (request.assignedZoneId() == null) throw new IllegalArgumentException("Security accounts require an assigned zone");
            zone = zones.findById(request.assignedZoneId()).orElseThrow(() -> new IllegalArgumentException("Assigned zone was not found"));
        }

        String temporaryPassword = temporaryPassword();
        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setRole(request.role());
        user.setAssignedZone(zone);
        user.setCreatedByAdmin(actor);
        user.setProtectedAdmin(false);
        user.setMustChangePassword(true);
        user.setPasswordHash(passwords.encode(temporaryPassword));
        users.save(user);
        return new CreatedUser(AuthController.info(user), temporaryPassword);
    }

    @GetMapping("/users")
    public List<UserInfo> list() {
        return users.findAllByOrderByCreatedAtDesc().stream().map(AuthController::info).toList();
    }

    @GetMapping("/admins")
    public List<UserInfo> listAdmins() {
        requireMainAdmin();
        return users.findAllByRoleOrderByCreatedAtAsc(UserRole.ADMIN).stream().map(AuthController::info).toList();
    }

    @PatchMapping("/admins/{id}")
    public UserInfo updateAdmin(@PathVariable Long id, @Valid @RequestBody UpdateAdmin request) {
        requireMainAdmin();
        User user = adminTarget(id);
        if (request.name() == null && request.newPassword() == null) throw new IllegalArgumentException("Provide a name or a new password");
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isBlank()) throw new IllegalArgumentException("Name cannot be blank");
            user.setName(name);
        }
        if (request.newPassword() != null) user.setPasswordHash(passwords.encode(request.newPassword()));
        users.save(user);
        return AuthController.info(user);
    }

    @DeleteMapping("/admins/{id}")
    @Transactional
    public void deleteAdmin(@PathVariable Long id) {
        User actor = requireMainAdmin();
        User user = adminTarget(id);
        users.findAllByCreatedByAdmin(user).forEach(child -> child.setCreatedByAdmin(actor));
        users.flush();
        instructions.deleteByCreatedById(user.getId());
        users.delete(user);
    }

    @DeleteMapping("/users/{id}")
    public void deactivate(@PathVariable Long id) {
        User user = users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getId().equals(current.get().getId())) throw new IllegalArgumentException("You cannot deactivate your own account");
        if (user.getRole() == UserRole.ADMIN) throw new IllegalArgumentException("Administrators must be managed from administrator settings");
        user.setActive(false);
        users.save(user);
    }

    @PostMapping("/instructions")
    public InstructionInfo instruction(@Valid @RequestBody Instruction request) {
        SecurityInstruction instruction = new SecurityInstruction();
        instruction.setMessage(request.message());
        instruction.setCreatedBy(current.get());
        if (request.targetZoneId() != null) instruction.setTargetZone(zones.findById(request.targetZoneId()).orElseThrow(() -> new IllegalArgumentException("Zone not found")));
        if (request.targetSecurityUserId() != null) {
            User user = users.findById(request.targetSecurityUserId()).orElseThrow(() -> new IllegalArgumentException("Security user not found"));
            if (user.getRole() != UserRole.SECURITY) throw new IllegalArgumentException("Instructions can target security personnel only");
            instruction.setTargetSecurityUser(user);
        }
        instructions.save(instruction);
        return new InstructionInfo(instruction.getId(), instruction.getMessage(), instruction.getTargetZone() == null ? null : instruction.getTargetZone().getId(), instruction.getTargetSecurityUser() == null ? null : instruction.getTargetSecurityUser().getId(), instruction.getCreatedAt());
    }

    private User requireMainAdmin() {
        User actor = current.get();
        if (!isMainAdmin(actor)) throw new IllegalArgumentException("Only the main administrator can manage administrators");
        return actor;
    }

    private boolean isMainAdmin(User user) {
        if (user.isProtectedAdmin()) return true;
        User firstAdmin = users.findFirstByRoleOrderByCreatedAtAsc(UserRole.ADMIN).orElse(null);
        if (firstAdmin != null && firstAdmin.getId().equals(user.getId())) {
            user.setProtectedAdmin(true);
            users.save(user);
            return true;
        }
        return false;
    }

    private User adminTarget(Long id) {
        User user = users.findById(id).orElseThrow(() -> new IllegalArgumentException("Administrator not found"));
        if (user.getRole() != UserRole.ADMIN) throw new IllegalArgumentException("The selected account is not an administrator");
        if (isMainAdmin(user)) throw new IllegalArgumentException("The main administrator cannot be changed or deleted");
        return user;
    }

    private String temporaryPassword() {
        byte[] bytes = new byte[9];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "!";
    }
}
