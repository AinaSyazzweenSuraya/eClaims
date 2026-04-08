package com.eclaims.repository;

import com.eclaims.entity.ClaimFormRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface ClaimFormRowRepository extends JpaRepository<ClaimFormRow, Integer> {

    List<ClaimFormRow> findByWorkflowIdOrderByCreatedDate(String workflowId);

    @Modifying
    @Transactional
    void deleteByWorkflowId(String workflowId);

    @Query("SELECT DISTINCT r.workflowId FROM ClaimFormRow r " +
           "WHERE r.projectManagerId = :pmId AND r.pmStatus = 'Pending'")
    List<String> findWorkflowIdsPendingForPm(String pmId);

    List<ClaimFormRow> findByWorkflowIdAndProjectManagerId(String workflowId, String pmId);
}
