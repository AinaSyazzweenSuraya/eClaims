package com.eclaims.dto;
import com.eclaims.entity.StaffInfo;
import com.eclaims.entity.Department;
import com.eclaims.entity.Company;
import lombok.*;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DashboardDto {
    private StaffInfo staffInfo;
    private String departmentName;
    private String companyName;
    private long totalClaims;
    private long draftClaims;
    private long pendingClaims;
    private long approvedClaims;
    private List<ClaimFormDto> recentClaims;
    private List<StaffClaimBalanceDto> balances;
}
