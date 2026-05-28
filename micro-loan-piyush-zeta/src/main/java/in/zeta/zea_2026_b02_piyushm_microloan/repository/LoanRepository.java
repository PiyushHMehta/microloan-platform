package in.zeta.zea_2026_b02_piyushm_microloan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Loan;
import in.zeta.zea_2026_b02_piyushm_microloan.enums.LoanStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanRepository extends JpaRepository<Loan, UUID>,
        JpaSpecificationExecutor<Loan> {

    Optional<Loan> findByApplicationId(UUID applicationId);

    boolean existsByBorrowerIdAndStatusIn(UUID borrowerId, List<LoanStatus> statuses);
}
