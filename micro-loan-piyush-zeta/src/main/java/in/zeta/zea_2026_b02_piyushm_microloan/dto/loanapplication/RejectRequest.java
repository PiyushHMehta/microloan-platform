package in.zeta.zea_2026_b02_piyushm_microloan.dto.loanapplication;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectRequest {

    @NotBlank(message = "Reviewed by is required")
    private String reviewedBy;

    @NotBlank(message = "Rejection reason is required")
    private String rejectionReason;
}
