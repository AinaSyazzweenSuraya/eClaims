package com.eclaims.repository;

import com.eclaims.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Integer> {

    List<AuditLog> findByWorkflowIdOrderByCreatedDate(String workflowId);

    @Modifying
    @Transactional
    void deleteByWorkflowId(String workflowId);
}
