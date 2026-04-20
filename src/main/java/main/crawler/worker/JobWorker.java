package main.crawler.worker;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import main.crawler.service.CentralBankPolicyRateService;
import main.crawler.service.GoldPriceService;
import main.crawler.service.OilPriceService;
import main.crawler.service.SilverPriceService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobWorker {
    private final CentralBankPolicyRateService centralBankPolicyRateService;
    private final GoldPriceService goldPriceService;
    private final SilverPriceService silverPriceService;
    private final OilPriceService oilPriceService;

    @PostConstruct
    public void run() {
//        log.info("JobWorker started");
        List<String> countries = List.of("D.CN", "D.RU");
        for (String country : countries) {
            try {
                centralBankPolicyRateService.saveCentralBankPolicyRateToCsv(country);
            } catch (Exception e) {
                log.error("Error while writing central bank CSV for {}", country, e);
            }
        }

        try {
            centralBankPolicyRateService.loadFedJsonFileAndWriteCsv();
        } catch (Exception e) {
            log.error("Error while writing Fed CSV", e);
        }

        goldPriceService.saveDataByCode("68");
        silverPriceService.saveDataByCode("40032");
        oilPriceService.saveDataByCode("8849");
        log.info("JobWorker finished");
    }
}
