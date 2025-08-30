package org.spring_ai_mcp_server_sse.stocks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class StockService {

    private final RestClient http = RestClient.builder()
            .baseUrl("https://www.alphavantage.co")
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${alphaVantage.apiKey:demo}")
    private String apiKey;

    @Tool(description = "Get stock price for a company or ticker symbol. Examples: AAPL, MSFT, TSLA, INFY, RDSA")
    public String getStockPrice(String companyOrSymbol) {
        try {
            String symbol = resolveSymbol(companyOrSymbol);
            if (symbol == null) {
                return "I couldn't find a symbol for \"" + companyOrSymbol + "\".";
            }

            String json = http.get()
                    .uri(uri -> uri.path("/query")
                            .queryParam("function", "GLOBAL_QUOTE")
                            .queryParam("symbol", symbol)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .body(String.class);

            log.info("Response Stock JSON: " + json);

            if (json == null) return "No stock data found for " + symbol + ".";

            JsonNode q = mapper.readTree(json).path("Global Quote");
            String price = q.path("05. price").asText(null);
            String change = q.path("09. change").asText(null);
            String changePct = q.path("10. change percent").asText(null);
            String day = q.path("07. latest trading day").asText(null);

            if (price == null || price.isBlank()) {
                JsonNode note = mapper.readTree(json).get("Note");
                if (note != null) return "Rate limit hit. Try again in a minute.";
                return "No live price available for " + symbol + " right now.";
            }

            StringBuilder out = new StringBuilder(symbol.toUpperCase())
                    .append(" price is ").append(price);
            if (change != null && !change.isBlank() && changePct != null && !changePct.isBlank()) {
                out.append(" (").append(change.startsWith("-") ? "" : "+")
                        .append(change).append(", ").append(changePct).append(")");
            }
            if (day != null && !day.isBlank()) out.append(" • ").append(day);

            return out.toString();

        } catch (Exception e) {
            return "Error fetching stock price: " + e.getMessage();
        }
    }

    private String resolveSymbol(String input) {
        String s = input == null ? "" : input.trim();

        // If it already looks like a ticker (e.g., AAPL, BRK.B), just return it
        if (s.matches("(?i)[A-Z\\.\\-]{1,10}")) return s.toUpperCase();

        try {
            String json = http.get()
                    .uri(uri -> uri.path("/query")
                            .queryParam("function", "SYMBOL_SEARCH")
                            .queryParam("keywords", s)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .body(String.class);

            if (json == null) return null;

            JsonNode matches = mapper.readTree(json).path("bestMatches");
            if (!matches.isArray() || matches.size() == 0) return null;

            return text(matches.get(0), "1. symbol");

        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null ? null : v.asText();
    }
}