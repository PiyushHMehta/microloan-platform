package in.zeta.zea_2026_b02_piyushm_microloan.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Installment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface InstallmentRepository extends JpaRepository<Installment, UUID> {

    List<Installment> findByLoanIdOrderByInstallmentNoAsc(UUID loanId);

    List<Installment> findByLoanId(UUID loanId);

    /**
     * Pessimistic write lock on unpaid installments — used during FIFO repayment
     * allocation to serialize concurrent payments on the same loan.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i FROM Installment i
             WHERE i.loanId = :loanId
               AND i.status IN (
                   in.zeta.zea_2026_b02_piyushm_microloan.enums.InstallmentStatus.PENDING,
                   in.zeta.zea_2026_b02_piyushm_microloan.enums.InstallmentStatus.PARTIAL,
                   in.zeta.zea_2026_b02_piyushm_microloan.enums.InstallmentStatus.OVERDUE)
             ORDER BY i.installmentNo ASC
            """)
    List<Installment> findUnpaidByLoanIdForUpdate(@Param("loanId") UUID loanId);

    /**
     * Sum of (totalDue - amountPaid) across all unpaid installments for a loan.
     * Source-of-truth for remaining balance and overpayment validation.
     */
    @Query("""
            SELECT COALESCE(SUM(i.totalDue - i.amountPaid), 0)
              FROM Installment i
             WHERE i.loanId = :loanId
               AND i.status IN (
                   in.zeta.zea_2026_b02_piyushm_microloan.enums.InstallmentStatus.PENDING,
                   in.zeta.zea_2026_b02_piyushm_microloan.enums.InstallmentStatus.PARTIAL,
                   in.zeta.zea_2026_b02_piyushm_microloan.enums.InstallmentStatus.OVERDUE)
            """)
    BigDecimal sumRemainingByLoanId(@Param("loanId") UUID loanId);
    /**
     * Finds overdue candidates: installments with due_date < today and status PENDING or PARTIAL.
     * Paginated to avoid full-table load. Ordered by loan_id for grouping efficiency.
     * Used by OverdueDetectionService scheduler.
     * NOTE: When migrating to Ganymede, replace with native query via Ganymede relational store client.
     */
    @Query("""
            SELECT i FROM Installment i
             WHERE i.dueDate < :today
               AND i.status IN (
                   in.zeta.zea_2026_b02_piyushm_microloan.enums.InstallmentStatus.PENDING,
                   in.zeta.zea_2026_b02_piyushm_microloan.enums.InstallmentStatus.PARTIAL)
             ORDER BY i.loanId ASC, i.installmentNo ASC
            """)
    Page<Installment> findOverdueCandidates(@Param("today") LocalDate today, Pageable pageable);
}
