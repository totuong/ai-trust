package main.crawler.repository;

import main.crawler.module.entity.GlobalSilverPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GlobalSilverPriceRepository extends JpaRepository<GlobalSilverPrice, UUID> {
    Optional<GlobalSilverPrice> findFirstByDate(LocalDate date);
}

