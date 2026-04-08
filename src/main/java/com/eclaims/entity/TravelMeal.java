package com.eclaims.entity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity @Table(name = "EN_TBL_MAST_TRAVEL_MEAL")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TravelMeal {
    @Id @Column(name = "MealID", length = 50) private String mealId;
    @Column(name = "Description", length = 150) private String description;
    @Column(name = "Percentage_Allowance", length = 100) private String percentageAllowance;
}
