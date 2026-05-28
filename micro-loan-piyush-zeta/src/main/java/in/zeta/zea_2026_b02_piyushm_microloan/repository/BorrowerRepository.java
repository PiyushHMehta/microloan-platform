package in.zeta.zea_2026_b02_piyushm_microloan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import in.zeta.zea_2026_b02_piyushm_microloan.entity.Borrower;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BorrowerRepository extends JpaRepository<Borrower, UUID>,
        JpaSpecificationExecutor<Borrower> {

    Optional<Borrower> findByPhoneNumber(String phoneNumber);
}
