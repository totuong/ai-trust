package main.crawler.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import main.crawler.module.data.CentralBankPolicyRateRaw;
import main.crawler.module.entity.CentralBankPolicyRate;
import main.crawler.payload.response.ApiResponse;
import main.crawler.repository.CentralBankPolicyRateRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CentralBankPolicyRateService {
    private final CentralBankPolicyRateRepository centralBankPolicyRateRepository;
    private final HttpService httpService;
    private final ObjectMapper objectMapper;
    String url = "https://data.bis.org/api/v0/timeseries/chart";

    @Transactional
    public void saveCentralBankPolicyRate(String  seriesKey) {
        if(seriesKey == null || seriesKey.isEmpty()||(!"D.CN".equals(seriesKey) && !"D.RU".equals(seriesKey))) {
            log.warn("Series key is null or empty, skipping central bank policy rate fetch");
            return;
        }

        Map<String, Object> requestBody = Map.of(
                "timeseries", new Object[]{
                        Map.of(
                                "df_id", "BIS,WS_CBPOL,1.0",
                                "series_key", seriesKey
                        )
                }
        );
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("Accept", "*/*");
        httpHeaders.set("Accept-Language", "en-US,en;q=0.7");
        httpHeaders.set("Connection", "keep-alive");
        httpHeaders.set("Content-Type", "application/json");
        httpHeaders.set("Origin", "https://data.bis.org");
        httpHeaders.set("Referer", "https://data.bis.org/topics/CBPOL/BIS,WS_CBPOL,1.0/D.CN");
        httpHeaders.set("Sec-Fetch-Dest", "empty");
        httpHeaders.set("Sec-Fetch-Mode", "cors");
        httpHeaders.set("Sec-Fetch-Site", "same-origin");
        httpHeaders.set("Sec-GPC", "1");
        httpHeaders.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36");
        httpHeaders.set("sec-ch-ua", "\"Brave\";v=\"147\", \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"147\"");
        httpHeaders.set("sec-ch-ua-mobile", "?0");
        httpHeaders.set("sec-ch-ua-platform", "\"Windows\"");
        httpHeaders.set("Cookie", "_pk_id.2.c8eb=4cfc501523bf0377.1775400996.; d95a117a4d9bd88166edd369fac646eb=06ebc469b7229ec7e6211275166119a4; 9a4bc18a3f14f4d8272cc1899de32dc6=1e357e11fc9afe6a35205b0779e620f5; _pk_ref.2.c8eb=%5B%22%22%2C%22%22%2C1775986396%2C%22https%3A%2F%2Fgemini.google.com%2F%22%5D; _pk_ses.2.c8eb=1");

        ApiResponse apiResponse = httpService.post(url, httpHeaders, Map.of(), requestBody);

        // Safely convert the response data to CentralBankPolicyRateRaw.
        CentralBankPolicyRateRaw centralBankPolicyRateRaw = new CentralBankPolicyRateRaw();
        try {
            var dataNode = apiResponse.getData();
            if (dataNode == null || dataNode.isNull()) {
                log.warn("API returned null data for central bank policy rate");
                return;
            } else if (dataNode.isArray()) {
                var listType = objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, CentralBankPolicyRateRaw.MutilData.class);
                java.util.List<CentralBankPolicyRateRaw.MutilData> list = objectMapper.convertValue(dataNode, listType);
                centralBankPolicyRateRaw.setData(list);
            } else {
                if (dataNode.has("data")) {
                    centralBankPolicyRateRaw = objectMapper.convertValue(dataNode, CentralBankPolicyRateRaw.class);
                } else {
                    CentralBankPolicyRateRaw.MutilData m = objectMapper.convertValue(dataNode, CentralBankPolicyRateRaw.MutilData.class);
                    centralBankPolicyRateRaw.setData(java.util.List.of(m));
                }
            }
        } catch (Exception e) {
            log.error("Failed to convert API response data to CentralBankPolicyRateRaw", e);
            throw new RuntimeException(e);
        }

        // Map the first available MutilData into the entity and save.
        if (centralBankPolicyRateRaw.getData() == null || centralBankPolicyRateRaw.getData().isEmpty()) {
            log.warn("No data items to map to CentralBankPolicyRate");
            return;
        }

        CentralBankPolicyRateRaw.MutilData first = centralBankPolicyRateRaw.getData().get(0);
        List<CentralBankPolicyRate> entitiesToSave = new ArrayList<>();
        log.info("Total data points to map: {}", first.getY().size());
        Double lastValue = null;
        for (int i = 0; i < first.getY().size() - 1; i++) {
            log.info("Mapping data point {}: date={}, value={}", i, first.getX().get(i), first.getY().get(i));
            CentralBankPolicyRate entity = new CentralBankPolicyRate();
            entity.setId(UUID.randomUUID());
            // name -> country (if available)
            if (first.getName() != null) {
                if(seriesKey.equals("D.CN")) {
                    entity.setCountry("China");
                    entity.setCurrency("CNY");
                } else if(seriesKey.equals("D.RU")) {
                    entity.setCountry("Russia");
                    entity.setCurrency("RUB");
                }

            }
            // y -> take last value as the latest
            Double value = first.getY().get(i);
            if (value != null) {
                lastValue = value;
            } else {
                value = lastValue;
            }

            entity.setValue(value);
            LocalDateTime dateTime = first.getX().get(i);
            LocalDate date = dateTime.toLocalDate();
            entity.setDate(date);
            entitiesToSave.add(entity);
        }

        // Save entity
        centralBankPolicyRateRepository.saveAll(entitiesToSave);

    }

    // Load local fed.json file (data/fed.json) and save records to central_bank_policy_rates
    @Transactional
    public void loadFedJsonFileAndSave() {
        Path path = Paths.get("data", "fed.json");
        if (!Files.exists(path)) {
            log.warn("fed.json not found at {}", path.toAbsolutePath());
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(Files.newBufferedReader(path, StandardCharsets.UTF_8));
            JsonNode results = root.has("result") ? root.get("result") : root;
            if (results == null || results.isNull()) {
                log.warn("fed.json does not contain 'result' or is empty");
                return;
            }
            if (!results.isArray()) {
                log.warn("fed.json 'result' is not an array; nothing to save");
                return;
            }

            List<CentralBankPolicyRate> toSave = new ArrayList<>();
            for (JsonNode item : results) {
                try {
                    CentralBankPolicyRate e = new CentralBankPolicyRate();
                    e.setId(UUID.randomUUID());

                    // country (map 'US' -> 'United States')
                    if (item.has("country") && !item.get("country").isNull()) {
                        String country = objectMapper.convertValue(item.get("country"), String.class);
                        if ("US".equalsIgnoreCase(country) || "USA".equalsIgnoreCase(country)) {
                            e.setCountry("United States");
                        } else {
                            e.setCountry(country);
                        }
                    }

                    if (item.has("currency") && !item.get("currency").isNull()) {
                        e.setCurrency(objectMapper.convertValue(item.get("currency"), String.class));
                    }

                    // value: prefer actualRaw, then actual
                    if (item.has("actualRaw") && !item.get("actualRaw").isNull()) {
                        e.setValue(item.get("actualRaw").asDouble());
                    } else if (item.has("actual") && !item.get("actual").isNull()) {
                        e.setValue(item.get("actual").asDouble());
                    }

                    // date: parse ISO offset date-time like 2016-03-16T18:00:00.000Z
                    if (item.has("date") && !item.get("date").isNull()) {
                        String dateStr = objectMapper.convertValue(item.get("date"), String.class);
                        try {
                            OffsetDateTime odt = OffsetDateTime.parse(dateStr);
                            e.setDate(odt.toLocalDate());
                        } catch (Exception ex) {
                            // fallback: try LocalDate.parse
                            try {
                                LocalDate ld = LocalDate.parse(dateStr);
                                e.setDate(ld);
                            } catch (Exception ex2) {
                                String idVal = item.has("id") && !item.get("id").isNull() ? objectMapper.convertValue(item.get("id"), String.class) : "<unknown>";
                                log.warn("Could not parse date '{}' for fed.json item id={}", dateStr, idVal);
                            }
                        }
                    }

                    toSave.add(e);
                } catch (Exception ex) {
                    log.error("Failed to map one fed.json item to entity", ex);
                }
            }

            if (!toSave.isEmpty()) {
                List<CentralBankPolicyRate> toSaveAll = new ArrayList<>(toSave);
                toSave.sort((e1, e2) -> {
                    if (e1.getDate() == null && e2.getDate() == null) return 0;
                    if (e1.getDate() == null) return 1;
                    if (e2.getDate() == null) return -1;
                    return e1.getDate().compareTo(e2.getDate());
                });
                for (int i = 1; i < toSave.size()-1; i++) {
                    LocalDate date1 = toSave.get(i-1).getDate();
                    LocalDate date2 = toSave.get(i).getDate();
                    LocalDate currentCheckDate = date1.plusDays(1);
                    while (date2 != null && currentCheckDate.isBefore(date2)) {

                        CentralBankPolicyRate temp = new CentralBankPolicyRate();
                        temp.setId(UUID.randomUUID());
                        temp.setCountry(toSave.get(i-1).getCountry());
                        temp.setCurrency(toSave.get(i-1).getCurrency());
                        temp.setValue(toSave.get(i-1).getValue());
                        temp.setDate(currentCheckDate);
                        toSaveAll.add(temp);
                        currentCheckDate = currentCheckDate.plusDays(1);
                    }
                }
                toSaveAll.sort((e1, e2) -> {
                    if (e1.getDate() == null && e2.getDate() == null) return 0;
                    if (e1.getDate() == null) return 1;
                    if (e2.getDate() == null) return -1;
                    return e1.getDate().compareTo(e2.getDate());
                });
                centralBankPolicyRateRepository.saveAll(toSaveAll);
                log.info("Saved {} records from {}", toSave.size(), path);
            } else {
                log.info("No Fed records to save from {}", path);
            }

        } catch (IOException e) {
            log.error("Failed to read or parse {}", path.toAbsolutePath(), e);
        }
    }

    // New: fetch BIS timeseries for a seriesKey and write CSV with only the latest data point
    public void saveCentralBankPolicyRateToCsv(String seriesKey) {
        if (seriesKey == null || seriesKey.isEmpty()) {
            log.warn("Series key is null or empty, skipping central bank CSV fetch");
            return;
        }

        Map<String, Object> requestBody = Map.of(
                "timeseries", new Object[]{
                        Map.of(
                                "df_id", "BIS,WS_CBPOL,1.0",
                                "series_key", seriesKey
                        )
                }
        );
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("Accept", "*/*");
        httpHeaders.set("Accept-Language", "en-US,en;q=0.7");
        httpHeaders.set("Connection", "keep-alive");
        httpHeaders.set("Content-Type", "application/json");
        httpHeaders.set("Origin", "https://data.bis.org");
        httpHeaders.set("Referer", "https://data.bis.org/topics/CBPOL/BIS,WS_CBPOL,1.0/" + seriesKey);
        httpHeaders.set("Sec-Fetch-Dest", "empty");
        httpHeaders.set("Sec-Fetch-Mode", "cors");
        httpHeaders.set("Sec-Fetch-Site", "same-origin");
        httpHeaders.set("Sec-GPC", "1");
        httpHeaders.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36");

        ApiResponse apiResponse = httpService.post(url, httpHeaders, Map.of(), requestBody);
        if (apiResponse == null || apiResponse.getData() == null) {
            log.warn("No data returned for seriesKey {}", seriesKey);
            return;
        }

        CentralBankPolicyRateRaw centralBankPolicyRateRaw = new CentralBankPolicyRateRaw();
        try {
            var dataNode = apiResponse.getData();
            if (dataNode.isArray()) {
                var listType = objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, CentralBankPolicyRateRaw.MutilData.class);
                java.util.List<CentralBankPolicyRateRaw.MutilData> list = objectMapper.convertValue(dataNode, listType);
                centralBankPolicyRateRaw.setData(list);
            } else {
                if (dataNode.has("data")) {
                    centralBankPolicyRateRaw = objectMapper.convertValue(dataNode, CentralBankPolicyRateRaw.class);
                } else {
                    CentralBankPolicyRateRaw.MutilData m = objectMapper.convertValue(dataNode, CentralBankPolicyRateRaw.MutilData.class);
                    centralBankPolicyRateRaw.setData(java.util.List.of(m));
                }
            }
        } catch (Exception e) {
            log.error("Failed to convert API response data to CentralBankPolicyRateRaw for seriesKey {}", seriesKey, e);
            return;
        }

        if (centralBankPolicyRateRaw.getData() == null || centralBankPolicyRateRaw.getData().isEmpty()) {
            log.warn("No data items to map to CentralBankPolicyRate for {}", seriesKey);
            return;
        }

        CentralBankPolicyRateRaw.MutilData first = centralBankPolicyRateRaw.getData().get(0);
        // find the last non-null value index
        int lastIdx = -1;
        for (int i = first.getY().size() - 1; i >= 0; i--) {
             Double v = null;
             try { v = first.getY().get(i); } catch (Exception ignored) {}
             if (v != null) {
                 // ensure corresponding date exists
                 if (first.getX() != null && first.getX().size() > i && first.getX().get(i) != null) {
                     lastIdx = i;
                     break;
                 }
             }
        }

        if (lastIdx < 0) {
            log.warn("No non-null value found for seriesKey {}", seriesKey);
            return;
        }

        Double value = first.getY().get(lastIdx);
        LocalDate date = first.getX().get(lastIdx).toLocalDate();

        CentralBankPolicyRate e = new CentralBankPolicyRate();
        e.setId(UUID.randomUUID());
        e.setDate(date);
        e.setValue(value);
        if (first.getName() != null) {
            String name = objectMapper.convertValue(first.getName(), String.class);
            if ("D.CN".equals(seriesKey)) { e.setCountry("China"); e.setCurrency("CNY"); }
            else if ("D.RU".equals(seriesKey)) { e.setCountry("Russia"); e.setCurrency("RUB"); }
            else { e.setCountry(name); }
        }

        // write single-row CSV
        try {
            Path dataDir = Paths.get("data"); if (!Files.exists(dataDir)) Files.createDirectories(dataDir);
            Path csvPath = dataDir.resolve("central_" + seriesKey + ".csv");
            try (java.io.BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
                writer.write("date,value,country,currency"); writer.newLine();
                String line = String.format("%s,%.6f,%s,%s", e.getDate(), e.getValue() == null ? Double.NaN : e.getValue(), e.getCountry() == null ? "" : e.getCountry(), e.getCurrency() == null ? "" : e.getCurrency());
                writer.write(line); writer.newLine();
            }
            log.info("Wrote latest record for {} -> {}", seriesKey, csvPath.toAbsolutePath());
        } catch (IOException ex) {
            log.error("Failed to write CSV for seriesKey {}", seriesKey, ex);
        }
    }

    // New: read fed.json and write CSV data/central_fed.csv containing only the most recent record
    public void loadFedJsonFileAndWriteCsv() {
        Path path = Paths.get("data", "fed.json");
        if (!Files.exists(path)) { log.warn("fed.json not found at {}", path.toAbsolutePath()); return; }
        try {
            JsonNode root = objectMapper.readTree(Files.newBufferedReader(path, StandardCharsets.UTF_8));
            JsonNode results = root.has("result") ? root.get("result") : root;
            if (results == null || !results.isArray()) { log.warn("fed.json result missing or not array"); return; }

            JsonNode lastNode = null;
            // iterate from end to find the last item with a parsable date and a value
            for (int i = results.size() - 1; i >= 0; i--) {
                JsonNode item = results.get(i);
                if (item == null || item.isNull()) continue;
                // check for date and value
                if ((item.has("actualRaw") && !item.get("actualRaw").isNull()) || (item.has("actual") && !item.get("actual").isNull())) {
                    if (item.has("date") && !item.get("date").isNull()) {
                        lastNode = item;
                        break;
                    }
                }
            }

            if (lastNode == null) { log.info("No valid Fed record found in fed.json"); return; }

            // map to entity-like fields
            String country = null, currency = null;
            Double value = null;
            LocalDate date = null;
            try {
                if (lastNode.has("country") && !lastNode.get("country").isNull()) {
                    country = objectMapper.convertValue(lastNode.get("country"), String.class);
                    if ("US".equalsIgnoreCase(country) || "USA".equalsIgnoreCase(country)) country = "United States";
                }
                if (lastNode.has("currency") && !lastNode.get("currency").isNull()) currency = objectMapper.convertValue(lastNode.get("currency"), String.class);
                if (lastNode.has("actualRaw") && !lastNode.get("actualRaw").isNull()) value = lastNode.get("actualRaw").asDouble();
                else if (lastNode.has("actual") && !lastNode.get("actual").isNull()) value = lastNode.get("actual").asDouble();
                if (lastNode.has("date") && !lastNode.get("date").isNull()) {
                    String dateStr = objectMapper.convertValue(lastNode.get("date"), String.class);
                    try { date = OffsetDateTime.parse(dateStr).toLocalDate(); } catch (Exception ex) { try { date = LocalDate.parse(dateStr); } catch (Exception e2) { log.warn("Could not parse fed.json date '{}'", dateStr); } }
                }
            } catch (Exception ex) {
                log.error("Failed to extract fields from last Fed node", ex);
                return;
            }

            if (date == null) { log.warn("Last Fed record has no parsable date, skipping"); return; }

            // write single-row CSV
            try {
                Path dataDir = Paths.get("data"); if (!Files.exists(dataDir)) Files.createDirectories(dataDir);
                Path csvPath = dataDir.resolve("central_fed.csv");
                try (java.io.BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
                    writer.write("date,value,country,currency"); writer.newLine();
                    String line = String.format("%s,%.6f,%s,%s", date, value == null ? Double.NaN : value, country == null ? "" : country, currency == null ? "" : currency);
                    writer.write(line); writer.newLine();
                }
                log.info("Wrote latest Fed record to {}", csvPath.toAbsolutePath());
            } catch (IOException e) {
                log.error("Failed to write Fed CSV", e);
            }

        } catch (IOException e) { log.error("Failed to read or parse {}", path.toAbsolutePath(), e); }
    }

    // ...existing loadFedJsonFileAndSave() remains as-is for DB saving if needed...

}
