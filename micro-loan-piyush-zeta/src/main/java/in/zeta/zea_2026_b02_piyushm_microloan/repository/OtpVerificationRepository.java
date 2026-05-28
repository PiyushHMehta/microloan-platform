package in.zeta.zea_2026_b02_piyushm_microloan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.OtpVerification;

import java.util.Optional;
import java.util.UUID;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {

    @Query("SELECT o FROM OtpVerification o WHERE o.borrowerId = :borrowerId " +
            "AND o.documentType = :documentType AND o.verified = false " +
            "ORDER BY o.createdAt DESC LIMIT 1")
    Optional<OtpVerification> findLatestUnverified(
            @Param("borrowerId") UUID borrowerId,
            @Param("documentType") String documentType);
}
