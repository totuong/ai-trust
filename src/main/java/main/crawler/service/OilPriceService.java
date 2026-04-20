package main.crawler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import main.crawler.module.entity.GlobalOilPrice;
import main.crawler.repository.GlobalOilPriceRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class OilPriceService {
    private final GlobalOilPriceRepository globalOilPriceRepository;
    private final ObjectMapper objectMapper;
    private final HttpService httpService;
    String url = "https://tvc4.investing.com/d9cab6dd295388b78ace6a3ebde5919e/1776011211/52/52/110/history";

    // Load local JSON file data/vang_global.json and save to DB (upsert by date)
    public void oilGlobalJsonAndSave() {
        Path path = Paths.get("data", "dau_global.json");
        if (!Files.exists(path)) {
            log.warn("dau_global.json not found at {}", path.toAbsolutePath());
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(Files.newBufferedReader(path, StandardCharsets.UTF_8));
            JsonNode t = root.get("t");
            JsonNode c = root.get("c");
            if (t == null || c == null || !t.isArray() || !c.isArray()) {
                log.warn("vang_global.json missing 't' or 'c' arrays");
                return;
            }

            int len = Math.min(t.size(), c.size());
            List<GlobalOilPrice> toSave = new ArrayList<>();
            for (int i = 0; i < len; i++) {
                try {
                    long epochSeconds = t.get(i).asLong();
                    double value = c.get(i).asDouble();
                    LocalDate date = Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate();

                    Optional<GlobalOilPrice> existingOpt = globalOilPriceRepository.findFirstByDate(date);
                    if (existingOpt.isPresent()) {
                        GlobalOilPrice existing = existingOpt.get();
                        existing.setValue(value);
                        existing.setCurrency("USD");
                        toSave.add(existing);
                    } else {
                        GlobalOilPrice g = new GlobalOilPrice();
                        g.setId(UUID.randomUUID());
                        g.setDate(date);
                        g.setValue(value);
                        g.setCurrency("USD");
                        toSave.add(g);
                    }
                } catch (Exception ex) {
                    log.error("Failed to parse one vang_global.json entry at index {}", i, ex);
                }
            }

            if (!toSave.isEmpty()) {
                List<GlobalOilPrice> allSave = new ArrayList<>(toSave);
                toSave.sort(Comparator.comparing(GlobalOilPrice::getDate)); // sort by date desc
                Double lastValue = toSave.get(0).getValue();
                for (int i = 1; i < toSave.size() - 1; i++) {
                    LocalDate currentDate = toSave.get(i - 1).getDate();
                    LocalDate nextDate = toSave.get(i).getDate();
                    LocalDate checkDate = currentDate.plusDays(1);
                    while (checkDate.isBefore(nextDate)) {
                        GlobalOilPrice g = new GlobalOilPrice();
                        g.setId(UUID.randomUUID());
                        g.setDate(checkDate);
                        g.setValue(lastValue);
                        allSave.add(g);
                        checkDate = checkDate.plusDays(1);
                    }
                    lastValue = toSave.get(i).getValue();
                }
                allSave.sort(Comparator.comparing(GlobalOilPrice::getDate));
                allSave.removeIf(g -> g.getDate() == null || g.getDate().isBefore(LocalDate.of(2016, 1, 1)));
                globalOilPriceRepository.saveAll(allSave);
                log.info("Saved/updated {} gold price records", toSave.size());
            } else {
                log.info("No gold records to save from {}", path);
            }

        } catch (IOException e) {
            log.error("Failed to read or parse vang_global.json", e);
        }
    }


    public void saveDataByCode(String code) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.now();
        Instant nowInstant = now.atZone(zone).toInstant();
        LocalDateTime before = now.minusDays(1);

        JsonNode root = null;
        int attempts = 0;
        while (attempts < 30) {
            try {
                Instant beforeInstant = before.atZone(zone).toInstant();
                String data = httpService.getData(url, code, beforeInstant.toEpochMilli(), nowInstant.toEpochMilli());
                root = objectMapper.readTree(data);
                JsonNode s = root.get("s");
                if (s == null || !"no_data".equals(s.asText())) {
                    break;
                }
                attempts++;
                before = before.minusDays(1);
            } catch (Exception ex) {
                log.error("Failed to fetch or parse remote data for code {} (attempt {})", code, attempts, ex);
                return;
            }
        }

        if (root == null) {
            log.warn("No data fetched for code {} after {} attempts", code, attempts);
            return;
        }

        JsonNode t = root.get("t");
        JsonNode c = root.get("c");
        if (t == null || c == null || !t.isArray() || !c.isArray()) {
            log.warn("remote data missing 't' or 'c' arrays for code {}", code);
            return;
        }

        int len = Math.min(t.size(), c.size());
        List<GlobalOilPrice> toSave = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            try {
                long epochSeconds = t.get(i).asLong();
                double value = c.get(i).asDouble();
                LocalDate date = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate();

                GlobalOilPrice g = new GlobalOilPrice();
                g.setId(UUID.randomUUID());
                g.setDate(date);
                g.setValue(value);
                g.setCurrency("USD");
                toSave.add(g);
            } catch (Exception ex) {
                log.error("Failed to parse one remote entry at index {} for code {}", i, code, ex);
            }
        }

        if (toSave.isEmpty()) {
            log.info("No oil records to process for code {}", code);
            return;
        }

        // Interpolate missing daily records
        List<GlobalOilPrice> allSave = new ArrayList<>(toSave);
        toSave.sort(Comparator.comparing(GlobalOilPrice::getDate));
        Double lastValue = toSave.get(0).getValue();
        for (int i = 1; i < toSave.size(); i++) {
            LocalDate currentDate = toSave.get(i - 1).getDate();
            LocalDate nextDate = toSave.get(i).getDate();
            LocalDate checkDate = currentDate.plusDays(1);
            while (checkDate.isBefore(nextDate)) {
                GlobalOilPrice g = new GlobalOilPrice();
                g.setId(UUID.randomUUID());
                g.setDate(checkDate);
                g.setValue(lastValue);
                g.setCurrency("USD");
                allSave.add(g);
                checkDate = checkDate.plusDays(1);
            }
            lastValue = toSave.get(i).getValue();
        }

        allSave.sort(Comparator.comparing(GlobalOilPrice::getDate));
        allSave.removeIf(g -> g.getDate() == null || g.getDate().isBefore(LocalDate.of(2016, 1, 1)));

        // dedupe by date (keep first occurrence)
        LinkedHashMap<LocalDate, GlobalOilPrice> dedup = new LinkedHashMap<>();
        for (GlobalOilPrice g : allSave) {
            if (g.getDate() == null) continue;
            dedup.putIfAbsent(g.getDate(), g);
        }

        // write CSV instead of saving to DB
        try {
            Path dataDir = Paths.get("data");
            if (!Files.exists(dataDir)) Files.createDirectories(dataDir);
            Path csvPath = dataDir.resolve("oil_" + code + "_" + Instant.now().getEpochSecond() + ".csv");
            try (java.io.BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
                writer.write("date,value,currency");
                writer.newLine();
                for (GlobalOilPrice g : dedup.values()) {
                    String line = String.format("%s,%.6f,%s", g.getDate(), g.getValue() == null ? Double.NaN : g.getValue(), g.getCurrency() == null ? "" : g.getCurrency());
                    writer.write(line);
                    writer.newLine();
                }
            }
            log.info("Wrote {} records to {}", dedup.size(), csvPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write CSV for code {}", code, e);
        }
    }

}
