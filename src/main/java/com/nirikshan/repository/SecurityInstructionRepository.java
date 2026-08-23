package com.nirikshan.repository;
import com.nirikshan.model.SecurityInstruction;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface SecurityInstructionRepository extends JpaRepository<SecurityInstruction,Long>{
 @Query("select i from SecurityInstruction i where i.targetSecurityUser.id = :userId or i.targetSecurityUser is null and (i.targetZone.id = :zoneId or i.targetZone is null) order by i.createdAt desc") List<SecurityInstruction> relevant(Long userId, Long zoneId);
 @Query("select i from SecurityInstruction i where i.targetSecurityUser.id = :userId or i.targetSecurityUser is null order by i.createdAt desc") List<SecurityInstruction> relevantForAdmin(Long userId);
 @Modifying @Query("delete from SecurityInstruction i where i.createdBy.id = :userId") int deleteByCreatedById(Long userId);
}
