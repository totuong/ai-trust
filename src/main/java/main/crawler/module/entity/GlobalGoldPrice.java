package main.crawler.module.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "global_gold_prices")
public class GlobalGoldPrice {
    @Id
    private UUID id;

    private Double value;

    private LocalDate date; // stored as yyyy-MM-dd

    private String currency = "USD";

    @CreationTimestamp
    private LocalDateTime createdAt;

    private String createdBy = "crawler";

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private String updatedBy = "crawler";
}

