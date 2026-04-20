package main.crawler.module.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Table(name = "central_bank_policy_rates")
//@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class CentralBankPolicyRate {
    @Id
    private UUID id;
    private String country;
    private String currency;
    private Double value;
    private LocalDate date;
    @CreationTimestamp
    private LocalDateTime createdAt;
    private String createdBy = "crawler";
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    private String updatedBy = "crawler";
}
