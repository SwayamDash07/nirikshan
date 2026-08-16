package com.nirikshan.dto;
import com.nirikshan.model.UserRole;
import jakarta.validation.constraints.*;
public final class AuthRequests { private AuthRequests(){}
 public record Signup(@Email @NotBlank String email,@NotBlank @Size(min=8) String password,@NotBlank String name){}
 public record Login(@Email @NotBlank String email,@NotBlank String password){}
 public record ChangePassword(@NotBlank String currentPassword,@NotBlank @Size(min=8) String newPassword){}
 public record CreateUser(@NotBlank String name,@Email @NotBlank String email,@NotNull UserRole role,Long assignedZoneId){}
 public record UpdateAdmin(@Size(min=1) String name,@Size(min=8) String newPassword){}
 public record AdminPasswordChange(@NotBlank @Size(min=8) String newPassword){}
 public record Instruction(@NotBlank @Size(max=2000) String message,Long targetZoneId,Long targetSecurityUserId,Long recommendationId,Long alertId){}
}
