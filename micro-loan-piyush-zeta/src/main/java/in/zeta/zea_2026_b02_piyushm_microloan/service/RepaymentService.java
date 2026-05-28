package in.zeta.zea_2026_b02_piyushm_microloan.service;

import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.*;
import lombok.RequiredArgsConstructor;
import in.zeta.spectra.capture.SpectraLogger;
import olympus.trace.OlympusSpectra;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.repayment.RepaymentRequest;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.repayment.RepaymentResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Installment;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Loan;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Repayment;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.InstallmentStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.LoanStatus;
import in.zeta.zea_2026_b02_piyushm_microloan.mapper.RepaymentMapper;
import in.zeta.zea_2026_b02_piyushm_microloan.notification.NotificationDispatcher;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.BorrowerRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.InstallmentRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.LoanRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.repository.RepaymentRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepaymentService {

    private static final SpectraLogger log = OlympusSpectra.getLogger(RepaymentService.class);

    private final RepaymentRepository repaymentRepository;
    private final InstallmentRepository installmentRepository;
    private final LoanRepository loanRepository;
    private final BorrowerRepository borrowerRepository;
    private final LoanService loanService;
    private final RepaymentMapper repaymentMapper;
    private final NotificationDispatcher notificationDispatcher;

    /**
     * Process a payment against a loan with FIFO allocation across unpaid installments.
     * Pessimistic-locks the unpaid installments to serialize concurrent payments.
     */
    @Transactional
    public RepaymentResponse processPayment(RepaymentRequest dto) {
        Loan loan = fetchAndGuardLoan(dto.getLoanId());
        List<Installment> unpaid = installmentRepository.findUnpaidByLoanIdForUpdate(dto.getLoanId());
        checkOverpayment(unpaid, dto.getAmount());

        String reference = generatePaymentReference();
        Repayment savedRepayment = repaymentMapper.toEntity(dto, reference);

        allocateFifo(unpaid, dto.getAmount());
        int paidCount = countFullyPaidInstallments(unpaid);

        loan.setTotalPaid(loan.getTotalPaid().add(dto.getAmount()));
        loanRepository.saveAndFlush(loan);
        loanService.recalculateStatus(dto.getLoanId());

        Loan reloaded = loanRepository.findById(dto.getLoanId()).orElseThrow();
        BigDecimal balanceAfter = reloaded.getTotalPayable().subtract(reloaded.getTotalPaid());
        savedRepayment.setBalanceAfter(balanceAfter);
        saveRepayment(savedRepayment);

        log.info("Repayment processed").attr("loanId", dto.getLoanId()).attr("amount", dto.getAmount())
                .attr("ref", savedRepayment.getPaymentReference()).attr("paidInstallments", paidCount).log();
        dispatchRepaymentNotification(savedRepayment);

        return repaymentMapper.toResponse(savedRepayment, reloaded.getStatus(), balanceAfter);
    }

    @Transactional(readOnly = true)
    public List<RepaymentResponse> getByLoanId(UUID loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOAN_001));

        return repaymentRepository.findByLoanIdOrderByPaidAtAsc(loanId).stream()
                .map(r -> repaymentMapper.toResponse(r, loan.getStatus(), r.getBalanceAfter()))
                .toList();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Loan fetchAndGuardLoan(UUID loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOAN_001));
        if (loan.getStatus() == LoanStatus.CLOSED || loan.getStatus() == LoanStatus.KFS_PENDING) {
            throw new InvalidStateException(ErrorCode.REP_002);
        }
        return loan;
    }

    private void checkOverpayment(List<Installment> unpaid, BigDecimal amount) {
        BigDecimal remainingBalance = unpaid.stream()
                .map(i -> i.getTotalDue().subtract(i.getAmountPaid()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(remainingBalance) > 0) {
            throw new BusinessException(ErrorCode.REP_003);
        }
    }

    private String generatePaymentReference() {
        return "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private void saveRepayment(Repayment repayment) {
        try {
            repaymentRepository.saveAndFlush(repayment);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException(ErrorCode.REP_004);
        }
    }

    private void allocateFifo(List<Installment> unpaid, BigDecimal paymentAmount) {
        BigDecimal remaining = paymentAmount;
        for (Installment inst : unpaid) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal allocation = computeAllocation(inst, remaining);
            applyAllocationToInstallment(inst, allocation);
            remaining = remaining.subtract(allocation);
        }
        installmentRepository.saveAll(unpaid);
    }

    private int countFullyPaidInstallments(List<Installment> installments) {
        return (int) installments.stream()
                .filter(i -> i.getStatus() == InstallmentStatus.PAID)
                .count();
    }

    private BigDecimal computeAllocation(Installment inst, BigDecimal remaining) {
        return remaining.min(inst.getTotalDue().subtract(inst.getAmountPaid()));
    }

    private void applyAllocationToInstallment(Installment inst, BigDecimal allocation) {
        inst.setAmountPaid(inst.getAmountPaid().add(allocation));
        if (inst.getAmountPaid().compareTo(inst.getTotalDue()) == 0) {
            inst.setStatus(InstallmentStatus.PAID);
            inst.setPaidAt(LocalDateTime.now());
        } else if (inst.getAmountPaid().compareTo(BigDecimal.ZERO) > 0
                && inst.getStatus() != InstallmentStatus.OVERDUE) {
            inst.setStatus(InstallmentStatus.PARTIAL);
        }
    }

    private void dispatchRepaymentNotification(Repayment repayment) {
        loanRepository.findById(repayment.getLoanId()).ifPresent(loan ->
            borrowerRepository.findById(loan.getBorrowerId()).ifPresent(b -> {
                if (b.getEmail() == null || b.getEmail().isBlank()) return;
                Map<String, Object> payload = new HashMap<>();
                payload.put("loanId", repayment.getLoanId());
                payload.put("borrowerId", b.getBorrowerId());
                payload.put("email", b.getEmail());
                payload.put("paymentAmount", repayment.getAmount());
                payload.put("paymentReference", repayment.getPaymentReference());
                notificationDispatcher.dispatchRepaymentMade(
                        b.getEmail(), repayment.getAmount(),
                        repayment.getLoanId(), repayment.getPaymentReference(), payload);
            })
        );
    }
}
