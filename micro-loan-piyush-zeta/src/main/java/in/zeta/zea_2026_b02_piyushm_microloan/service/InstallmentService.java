package in.zeta.zea_2026_b02_piyushm_microloan.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ErrorCode;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.InstallmentResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.mapper.InstallmentMapper;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.InstallmentRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.LoanRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InstallmentService {

    private final LoanRepository loanRepository;
    private final InstallmentRepository installmentRepository;
    private final InstallmentMapper installmentMapper;

    @Transactional(readOnly = true)
    public List<InstallmentResponse> getByLoanId(UUID loanId) {
        if (!loanRepository.existsById(loanId)) {
            throw new ResourceNotFoundException(ErrorCode.LOAN_001);
        }

        return installmentRepository.findByLoanIdOrderByInstallmentNoAsc(loanId).stream()
                .map(installmentMapper::toResponse)
                .toList();
    }
}
