package main.crawler.module.data;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data

public class CentralBankPolicyRateRaw {
    private List<MutilData> data;

    @Data
    public static class MutilData {
        private Object hoverlabel;
        private Object line;
        private Object mode;
        private Object name;
        private List<LocalDateTime> x;
        private Object xperiodalignment;
        private List<Double> y;
    }
}
