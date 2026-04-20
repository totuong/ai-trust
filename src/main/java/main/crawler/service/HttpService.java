package main.crawler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import main.crawler.payload.response.ApiResponse;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class HttpService {

    private final RestTemplate restTemplate;

    public ApiResponse get(String url, HttpHeaders httpHeaders, Map<String, String> params) {
        try {
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            log.info("url: {} params: {} ", url, params);
            url = buildUrlWithParams(url, params);
            ResponseEntity<ApiResponse> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(httpHeaders), ApiResponse.class);
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Error while sending GET request", e);
        }

    }

    public String getString(String url, HttpHeaders httpHeaders, Map<String, String> params) {
        try {
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            log.info("url: {} params: {} ", url, params);
            url = buildUrlWithParams(url, params);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(httpHeaders), String.class);
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Error while sending GET request", e);
        }

    }

    public ApiResponse post(String url, HttpHeaders httpHeaders, Map<String, String> params, Map<String, Object> requestBody) {
        try {
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            log.info("url: {} params: {} requestBody: {}", url, params, requestBody);
            HttpEntity<Object> entity = new HttpEntity<>(requestBody, httpHeaders);
            url = buildUrlWithParams(url, params);
            ResponseEntity<ApiResponse> response = restTemplate.exchange(url, HttpMethod.POST, entity, ApiResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Error while sending POST request");
            throw new RuntimeException("Error while sending GET request", e);
        }

    }

    private String buildUrlWithParams(String url, Map<String, String> params) {
        if(params == null || params.isEmpty()) {
            return url;
        }
        url = url + "?" + params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
        return url;
    }

    public String getData(String url, String code,  Long from, Long to) {
        Map<String, String> param = new HashMap<>();
        param.put("symbol", code);
        param.put("resolution", "D");
        param.put("from", String.valueOf(from/1000));
        param.put("to", String.valueOf(to/1000));
        HttpHeaders httpHeaders = new HttpHeaders();

        httpHeaders.set("accept", "*/*");
        httpHeaders.set("accept-language", "en-US,en;q=0.6");
        httpHeaders.set("content-type", "text/plain");
        httpHeaders.set("Connection", "keep-alive");
        httpHeaders.set("origin", "https://tvc-invdn-cf-com.investing.com");
        httpHeaders.set("priority", "u=1, i");
        httpHeaders.set("User-Agent","PostmanRuntime/7.51.1");
//        httpHeaders.set("Host","PostmanRuntime/7.51.1");
        httpHeaders.set("referer", "https://tvc-invdn-cf-com.investing.com/");
        httpHeaders.set("sec-ch-ua", "\"Brave\";v=\"147\", \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"147\"");
        httpHeaders.set("sec-ch-ua-mobile", "?0");
        httpHeaders.set("sec-ch-ua-platform", "\"Windows\"");
        httpHeaders.set("sec-fetch-dest", "empty");
        httpHeaders.set("Cookie", "__cf_bm=_ua_1bc2INS4zdwvVmCUN8AGS.0kZOBnRClgX.JvvK0-1776697520-1.0.1.1-GdXs5WPSTqDoTK.xGtsXroRR1tyyUGdrK.xpS8s4jxBP4kBCXXq1la.vkiiKzlNz9cGIP6NK0IFlaY9EgrN5n48FFkJxmpdKoBYmq98Uv44DOHiD3G1sDg5UNZh1ySG.; inudid=953f47a6501b092fb70aea550f88b4ab; udid=953f47a6501b092fb70aea550f88b4ab");
        return getString(url,httpHeaders, param);

    }
}
