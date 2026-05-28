package in.zeta.zea_2026_b02_piyushm_microloan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.LoanApplication;

import java.util.UUID;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID>,
        JpaSpecificationExecutor<LoanApplication> {
}
