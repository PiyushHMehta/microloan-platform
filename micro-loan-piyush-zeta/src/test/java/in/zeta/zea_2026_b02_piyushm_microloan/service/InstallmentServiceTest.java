package in.zeta.zea_2026_b02_piyushm_microloan.service;

import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.InstallmentResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Installment;
import in.zeta.zea_2026_b02_piyushm_microloan.mapper.InstallmentMapper;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.InstallmentRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.LoanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstallmentServiceTest {
    @Mock LoanRepository loanRepository;
    @Mock InstallmentRepository installmentRepository;
    @Mock InstallmentMapper installmentMapper;
    @InjectMocks InstallmentService installmentService;

    UUID loanId;
    Installment installment;
    InstallmentResponse response;

    @BeforeEach
    void setUp() {
        loanId = UUID.randomUUID();
        installment = new Installment();
        response = new InstallmentResponse();
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException if loan does not exist")
    void throwsIfLoanNotFound() {
        when(loanRepository.existsById(loanId)).thenReturn(false);
        assertThatThrownBy(() -> installmentService.getByLoanId(loanId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Loan not found");
    }

    @Test
    @DisplayName("Returns mapped installments if loan exists")
    void returnsInstallmentsIfLoanExists() {
        when(loanRepository.existsById(loanId)).thenReturn(true);
        when(installmentRepository.findByLoanIdOrderByInstallmentNoAsc(loanId)).thenReturn(List.of(installment));
        when(installmentMapper.toResponse(installment)).thenReturn(response);
        List<InstallmentResponse> result = installmentService.getByLoanId(loanId);
        assertThat(result).containsExactly(response);
    }
}
