package com.eclaims.repository;

import com.eclaims.entity.ClaimForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClaimFormRepository extends JpaRepository<ClaimForm, String> {

    List<ClaimForm> findByStaffIdOrderByCreatedDateDesc(String staffId);

    Optional<ClaimForm> findByFormId(String formId);

    // Use String status values to match the DB column directly
    List<ClaimForm> findByWfStatusOrderByCreatedDateDesc(String wfStatus);

    List<ClaimForm> findByApproverIdAndWfStatusInOrderByCreatedDateDesc(
        String approverId, List<String> statuses);

    @Query("SELECT c FROM ClaimForm c ORDER BY c.createdDate DESC")
    List<ClaimForm> findAllOrderByDateDesc();
}
