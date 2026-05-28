package in.zeta.zea_2026_b02_piyushm_microloan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.LoanProductFrequency;

import java.util.List;
import java.util.UUID;

public interface LoanProductFrequencyRepository extends JpaRepository<LoanProductFrequency, UUID> {

    List<LoanProductFrequency> findByLoanProductProductId(UUID productId);

    void deleteByLoanProductProductId(UUID productId);
}
