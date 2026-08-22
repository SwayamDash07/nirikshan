package com.nirikshan.controller;

import com.nirikshan.model.CheckIn;
import com.nirikshan.model.User;
import com.nirikshan.model.UserRole;
import com.nirikshan.repository.CheckInRepository;
import com.nirikshan.repository.UserRepository;
import com.nirikshan.security.CurrentUser;
import jakarta.transaction.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CheckInController {
    private final CheckInRepository checkIns;
    private final UserRepository users;
    private final CurrentUser current;
    private final SimpMessagingTemplate messaging;

    public CheckInController(CheckInRepository checkIns, UserRepository users, CurrentUser current, SimpMessagingTemplate messaging) {
        this.checkIns = checkIns;
        this.users = users;
        this.current = current;
        this.messaging = messaging;
    }

    @GetMapping("/admin/check-ins")
    public List<CheckInResponse> list() {
        requireAdmin();
        return checkIns.findAllByOrderByTriggeredAtDesc().stream().map(CheckInController::response).toList();
    }

    @PostMapping("/admin/check-ins")
    @Transactional
    public List<CheckInResponse> trigger() {
        requireAdmin();
        List<User> staff = users.findAllByRoleOrderByCreatedAtAsc(UserRole.SECURITY).stream().filter(User::isActive).toList();
        if (staff.isEmpty()) throw new IllegalArgumentException("No active security staff members are available for check-in");
        List<CheckIn> created = staff.stream().map(user -> {
            CheckIn checkIn = new CheckIn();
            checkIn.setStaffName(user.getName());
            return checkIn;
        }).map(checkIns::save).toList();
        List<CheckInResponse> responses = created.stream().map(CheckInController::response).toList();
        responses.forEach(item -> messaging.convertAndSend("/topic/check-ins", item));
        return responses;
    }

    @GetMapping("/check-ins/staff/{staffName}")
    public CheckInResponse staffCheckIn(@PathVariable String staffName) {
        User staff = requireStaff(staffName);
        return checkIns.findFirstByStaffNameIgnoreCaseAndStatusOrderByTriggeredAtDesc(staff.getName(), "pending")
                .map(CheckInController::response)
                .orElse(null);
    }

    @PostMapping("/check-ins/staff/{staffName}/confirm")
    @Transactional
    public CheckInResponse confirm(@PathVariable String staffName) {
        User staff = requireStaff(staffName);
        CheckIn checkIn = checkIns.findFirstByStaffNameIgnoreCaseAndStatusOrderByTriggeredAtDesc(staff.getName(), "pending").orElse(null);
        if (checkIn == null) return null;
        checkIn.setRespondedAt(Instant.now());
        checkIn.setStatus("confirmed");
        CheckInResponse response = response(checkIns.save(checkIn));
        messaging.convertAndSend("/topic/check-ins", response);
        return response;
    }

    private User requireAdmin() {
        User user = current.get();
        if (user.getRole() != UserRole.ADMIN || !user.isActive()) throw new IllegalArgumentException("Administrator access is required");
        return user;
    }

    private User requireStaff(String staffName) {
        User user = current.get();
        if (user.getRole() != UserRole.SECURITY || !user.isActive() || !user.getName().equalsIgnoreCase(staffName.trim())) {
            throw new IllegalArgumentException("This check-in does not belong to the signed-in staff member");
        }
        return user;
    }

    private static CheckInResponse response(CheckIn checkIn) {
        return new CheckInResponse(checkIn.getId(), checkIn.getStaffName(), checkIn.getTriggeredAt(), checkIn.getRespondedAt(), checkIn.getStatus());
    }

    public record CheckInResponse(Integer id, String staffName, Instant triggeredAt, Instant respondedAt, String status) {}
}
