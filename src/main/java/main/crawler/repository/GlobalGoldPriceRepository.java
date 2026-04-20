package main.crawler.repository;

import main.crawler.module.entity.GlobalGoldPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GlobalGoldPriceRepository extends JpaRepository<GlobalGoldPrice, UUID> {
    Optional<GlobalGoldPrice> findFirstByDate(LocalDate date);
}

