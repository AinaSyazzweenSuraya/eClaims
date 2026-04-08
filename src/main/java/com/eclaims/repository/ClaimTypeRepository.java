package com.eclaims.repository;
import com.eclaims.entity.ClaimType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface ClaimTypeRepository extends JpaRepository<ClaimType, String> {
    List<ClaimType> findAllByOrderByClaimId();
}
