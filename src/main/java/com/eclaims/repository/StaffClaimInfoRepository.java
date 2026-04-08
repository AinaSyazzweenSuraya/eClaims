package com.eclaims.repository;
import com.eclaims.entity.StaffClaimInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface StaffClaimInfoRepository extends JpaRepository<StaffClaimInfo, String> {}
