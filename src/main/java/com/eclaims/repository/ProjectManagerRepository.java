package com.eclaims.repository;
import com.eclaims.entity.ProjectManager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface ProjectManagerRepository extends JpaRepository<ProjectManager, String> {
    List<ProjectManager> findAllByOrderByManagerName();
}
