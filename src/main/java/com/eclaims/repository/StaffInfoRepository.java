package com.eclaims.repository;
import com.eclaims.entity.StaffInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface StaffInfoRepository extends JpaRepository<StaffInfo, String> {
    List<StaffInfo> findByApproverIdOrderByName(String approverId);
    List<StaffInfo> findAllByOrderByName();
}
