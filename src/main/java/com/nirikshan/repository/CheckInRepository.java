package com.nirikshan.repository;

import com.nirikshan.model.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CheckInRepository extends JpaRepository<CheckIn, Integer> {
    List<CheckIn> findAllByOrderByTriggeredAtDesc();
    Optional<CheckIn> findFirstByStaffNameIgnoreCaseAndStatusOrderByTriggeredAtDesc(String staffName, String status);
}
