package in.zeta.zea_2026_b02_piyushm_microloan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Repayment;

import java.util.List;
import java.util.UUID;

public interface RepaymentRepository extends JpaRepository<Repayment, UUID> {

    List<Repayment> findByLoanIdOrderByPaidAtAsc(UUID loanId);
}
