package com.eclaims.dto;
import lombok.*;
import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class StaffClaimBalanceDto {
    private String claimId;
    private String claimTitle;
    private BigDecimal entitled;
    private BigDecimal claimed;
    private BigDecimal balance;
}
