package in.zeta.zea_2026_b02_piyushm_microloan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Kyc;

import java.util.Optional;
import java.util.UUID;

public interface KycRepository extends JpaRepository<Kyc, UUID> {

    Optional<Kyc> findByBorrowerId(UUID borrowerId);

    Optional<Kyc> findByPanNumber(String panNumber);

    Optional<Kyc> findByAadhaarNumber(String aadhaarNumber);
}
