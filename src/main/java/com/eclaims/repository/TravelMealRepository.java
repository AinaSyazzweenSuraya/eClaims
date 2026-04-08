package com.eclaims.repository;
import com.eclaims.entity.TravelMeal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface TravelMealRepository extends JpaRepository<TravelMeal, String> {
    List<TravelMeal> findAllByOrderByDescription();
}
