package main.crawler.repository;

import main.crawler.module.entity.CentralBankPolicyRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CentralBankPolicyRateRepository extends JpaRepository<CentralBankPolicyRate, UUID> {

}
