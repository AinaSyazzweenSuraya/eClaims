package com.eclaims.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "EN_TBL_MAST_STAFF_CLAIM_INFO")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class StaffClaimInfo {
    @Id @Column(name = "StaffID", length = 50) private String staffId;
    @Column(name = "MealWeekdaysEntiledPerYear", precision=13,scale=2) private BigDecimal mealWeekdaysEntitled;
    @Column(name = "MealWeekdaysClaimed",        precision=13,scale=2) private BigDecimal mealWeekdaysClaimed;
    @Column(name = "MealWeekdaysBalance",         precision=13,scale=2) private BigDecimal mealWeekdaysBalance;
    @Column(name = "MealHolidayEntiledPerYear",  precision=13,scale=2) private BigDecimal mealHolidayEntitled;
    @Column(name = "MealHolidayClaimed",         precision=13,scale=2) private BigDecimal mealHolidayClaimed;
    @Column(name = "MealHolidayBalance",         precision=13,scale=2) private BigDecimal mealHolidayBalance;
    @Column(name = "PhoneEntiledPerYear",        precision=13,scale=2) private BigDecimal phoneEntitled;
    @Column(name = "PhoneClaimed",               precision=13,scale=2) private BigDecimal phoneClaimed;
    @Column(name = "PhoneBalance",               precision=13,scale=2) private BigDecimal phoneBalance;
    @Column(name = "MileageEntiledPerYear",      precision=13,scale=2) private BigDecimal mileageEntitled;
    @Column(name = "MileageClaimed",             precision=13,scale=2) private BigDecimal mileageClaimed;
    @Column(name = "MileageBalance",             precision=13,scale=2) private BigDecimal mileageBalance;
    @Column(name = "PanelMedicalEntiledPerYear", precision=13,scale=2) private BigDecimal panelMedicalEntitled;
    @Column(name = "PanelMedicalClaimed",        precision=13,scale=2) private BigDecimal panelMedicalClaimed;
    @Column(name = "PanelMedicalBalance",        precision=13,scale=2) private BigDecimal panelMedicalBalance;
    @Column(name = "NonPanelMedicalEntiledPerYear",precision=13,scale=2) private BigDecimal nonPanelMedicalEntitled;
    @Column(name = "NonPanelMedicalClaimed",     precision=13,scale=2) private BigDecimal nonPanelMedicalClaimed;
    @Column(name = "NonPanelMedicalBalance",     precision=13,scale=2) private BigDecimal nonPanelMedicalBalance;
    @Column(name = "DentalAndOpticalEntiledPerYear",precision=13,scale=2) private BigDecimal dentalOpticalEntitled;
    @Column(name = "DentalAndOpticalClaimed",    precision=13,scale=2) private BigDecimal dentalOpticalClaimed;
    @Column(name = "DentalAndOpticalBalance",    precision=13,scale=2) private BigDecimal dentalOpticalBalance;
    @Column(name = "PetrolEntiledPerYear",       precision=13,scale=2) private BigDecimal petrolEntitled;
    @Column(name = "PetrolClaimed",              precision=13,scale=2) private BigDecimal petrolClaimed;
    @Column(name = "PetrolBalance",              precision=13,scale=2) private BigDecimal petrolBalance;
    @Column(name = "BusinessEntertainmentEntiledPerYear",precision=13,scale=2) private BigDecimal bizEntEntitled;
    @Column(name = "BusinessEntertainmentClaimed",precision=13,scale=2) private BigDecimal bizEntClaimed;
    @Column(name = "BusinessEntertainmentBalance",precision=13,scale=2) private BigDecimal bizEntBalance;
    @Column(name = "TollEntiledPerYear",         precision=13,scale=2) private BigDecimal tollEntitled;
    @Column(name = "TollClaimed",                precision=13,scale=2) private BigDecimal tollClaimed;
    @Column(name = "TollBalance",                precision=13,scale=2) private BigDecimal tollBalance;
    @Column(name = "OthersEntiledPerYear",       precision=13,scale=2) private BigDecimal othersEntitled;
    @Column(name = "OthersClaimed",              precision=13,scale=2) private BigDecimal othersClaimed;
    @Column(name = "OthersBalance",              precision=13,scale=2) private BigDecimal othersBalance;
    @Column(name = "TravelEntiledPerYear",       precision=13,scale=2) private BigDecimal travelEntitled;
    @Column(name = "TravelClaimed",              precision=13,scale=2) private BigDecimal travelClaimed;
    @Column(name = "TravelBalance",              precision=13,scale=2) private BigDecimal travelBalance;
    @Column(name = "OpticalEntiledPerYear", precision=13,scale=2) private BigDecimal opticalEntitled;
    @Column(name = "OpticalClaimed",        precision=13,scale=2) private BigDecimal opticalClaimed;
    @Column(name = "OpticalBalance",        precision=13,scale=2) private BigDecimal opticalBalance;

}
