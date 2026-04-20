package main.crawler.payload.response;

import lombok.Data;
import tools.jackson.databind.JsonNode;

@Data
public class ApiResponse {
    private JsonNode data;
}
