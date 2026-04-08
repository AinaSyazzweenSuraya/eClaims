package com.eclaims.repository;
import com.eclaims.entity.PanelMedical;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface PanelMedicalRepository extends JpaRepository<PanelMedical, String> {
    List<PanelMedical> findByIsActiveTrueOrderByMedicalClinic();
}
