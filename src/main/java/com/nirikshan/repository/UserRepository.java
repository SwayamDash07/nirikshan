package com.nirikshan.repository;
import com.nirikshan.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UserRepository extends JpaRepository<User,Long>{
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "assignedZone") Optional<User> findByEmailIgnoreCase(String email);
 boolean existsByEmailIgnoreCase(String email);
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "assignedZone") List<User> findAllByOrderByCreatedAtDesc();
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "assignedZone") List<User> findAllByRoleOrderByCreatedAtAsc(com.nirikshan.model.UserRole role);
 List<User> findAllByCreatedByAdmin(User admin);
 Optional<User> findFirstByRoleOrderByCreatedAtAsc(com.nirikshan.model.UserRole role);
 long countByAssignedZoneIdAndRoleAndActiveTrue(Long zoneId, com.nirikshan.model.UserRole role);
}
