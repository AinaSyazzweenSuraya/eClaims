package com.eclaims.repository;

import com.eclaims.entity.Approver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApproverRepository extends JpaRepository<Approver, Integer> {
    java.util.Optional<Approver> findByStaffId(String staffId);
}
