package com.eclaims.repository;

import com.eclaims.entity.ClaimAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ClaimAttachmentRepository extends JpaRepository<ClaimAttachment, Integer> {

    List<ClaimAttachment> findByWorkflowIdOrderByDocDate(String workflowId);
    List<ClaimAttachment> findByWorkflowIdAndCategory(String workflowId, String category);

    @Modifying
    @Transactional
    void deleteByWorkflowId(String workflowId);
}
