package com.nirikshan.controller;

import com.nirikshan.model.CheckIn;
import com.nirikshan.model.User;
import com.nirikshan.model.UserRole;
import com.nirikshan.repository.CheckInRepository;
import com.nirikshan.repository.UserRepository;
import com.nirikshan.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckInControllerTest {
    @Mock CheckInRepository checkIns;
    @Mock UserRepository users;
    @Mock MessageChannel messageChannel;
    private CurrentUser current;
    private SimpMessagingTemplate messaging;
    private CheckInController controller;

    @BeforeEach
    void setUp() {
        current = new CurrentUser(users);
        when(messageChannel.send(any())).thenReturn(true);
        messaging = new SimpMessagingTemplate(messageChannel);
        controller = new CheckInController(checkIns, users, current, messaging);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void triggerCreatesPendingCheckInForEachActiveSecurityStaffMember() {
        User admin = user("Admin", UserRole.ADMIN, true);
        User first = user("Asha", UserRole.SECURITY, true);
        User inactive = user("Inactive", UserRole.SECURITY, false);
        authenticate("admin@example.com", admin);
        when(users.findAllByRoleOrderByCreatedAtAsc(UserRole.SECURITY)).thenReturn(List.of(first, inactive));
        when(checkIns.save(any(CheckIn.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<CheckInController.CheckInResponse> result = controller.trigger();

        assertEquals(1, result.size());
        assertEquals("Asha", result.get(0).staffName());
        assertEquals("pending", result.get(0).status());
    }

    @Test
    void staffConfirmationSetsResponseTimeAndConfirmedStatus() {
        User staff = user("Asha", UserRole.SECURITY, true);
        CheckIn checkIn = new CheckIn();
        checkIn.setStaffName("Asha");
        authenticate("asha@example.com", staff);
        when(checkIns.findFirstByStaffNameIgnoreCaseAndStatusOrderByTriggeredAtDesc("Asha", "pending")).thenReturn(Optional.of(checkIn));
        when(checkIns.save(checkIn)).thenReturn(checkIn);

        CheckInController.CheckInResponse result = controller.confirm("Asha");

        assertEquals("confirmed", result.status());
        assertNotNull(result.respondedAt());
    }

    private static User user(String name, UserRole role, boolean active) {
        User user = new User();
        user.setName(name);
        user.setRole(role);
        user.setActive(active);
        return user;
    }

    private void authenticate(String email, User user) {
        when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, null));
    }
}
