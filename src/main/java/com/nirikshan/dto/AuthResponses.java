package com.nirikshan.dto;
import com.nirikshan.model.UserRole;
import java.time.Instant;
public final class AuthResponses { private AuthResponses(){}
 public record UserInfo(Long id,String email,String name,UserRole role,boolean mustChangePassword,Long assignedZoneId,String assignedZoneName,Instant createdAt,boolean active,boolean protectedAdmin){}
 public record LoginResult(String token,UserInfo user){}
 public record CreatedUser(UserInfo user,String temporaryPassword){}
 public record InstructionInfo(Long id,String message,Long targetZoneId,Long targetSecurityUserId,Instant createdAt){}
 public record SecurityAlertInfo(Long id,Long zoneId,String zoneName,double latitude,double longitude,Instant timestamp,String message,String severity,boolean resolved,String source){}
}
