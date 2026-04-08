package com.eclaims.repository;
import com.eclaims.entity.TravelLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface TravelLocationRepository extends JpaRepository<TravelLocation, String> {
    List<TravelLocation> findAllByOrderByLocation();
}
