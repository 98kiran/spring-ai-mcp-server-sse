package org.spring_ai_mcp_server_sse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class StockService {

    private final RestClient http = RestClient.builder()
            .baseUrl("https://www.alphavantage.co")
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    // Put your free API key in application.properties (see below). "demo" works but is very rate-limited.
    @Value("${alphaVantage.apiKey:demo}")
    private String apiKey;

    // ✅ Signature stays the same so your chat flow & controller don't change.
    @Tool(description = "Get USD stock price for a US-listed company (ticker or name). Examples: AAPL, MSFT, Tesla, Nvidia")
    public String getStockPrice(String companyOrSymbol) {
        try {
            String symbol = resolveUsSymbol(companyOrSymbol);
            if (symbol == null) {
                return "I couldn't find a US-listed symbol for \"" + companyOrSymbol + "\".";
            }

            String json = http.get()
                    .uri(uri -> uri.path("/query")
                            .queryParam("function", "GLOBAL_QUOTE")
                            .queryParam("symbol", symbol)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .body(String.class);
            System.out.println("Response Stock JSON: " + json);

            if (json == null) return "No stock data found for " + symbol + ".";

            JsonNode q = mapper.readTree(json).path("Global Quote");
            String price = q.path("05. price").asText(null);
            String change = q.path("09. change").asText(null);
            String changePct = q.path("10. change percent").asText(null);
            String day = q.path("07. latest trading day").asText(null);

            if (price == null || price.isBlank()) {
                // Alpha Vantage returns a "Note" field when throttled; surface that nicely
                JsonNode note = mapper.readTree(json).get("Note");
                if (note != null) return "Rate limit hit. Try again in a minute.";
                return "No live USD price available for " + symbol + " right now.";
            }

            StringBuilder out = new StringBuilder(symbol.toUpperCase())
                    .append(" price is $").append(price);
            if (change != null && !change.isBlank() && changePct != null && !changePct.isBlank()) {
                out.append(" (").append(change.startsWith("-") ? "" : "+")
                        .append(change).append(", ").append(changePct).append(")");
            }
            if (day != null && !day.isBlank()) out.append(" • ").append(day);
            out.append(" USD");
            return out.toString();

        } catch (Exception e) {
            return "Error fetching USD stock price: " + e.getMessage();
        }
    }

    /** Resolve to a US-listed symbol only (USD). Accepts ticker or company name. */
    private String resolveUsSymbol(String input) {
        String s = input == null ? "" : input.trim();
        // If it already looks like a ticker (AAPL, BRK.B, etc.), use it directly
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

            JsonNode matches = new ObjectMapper().readTree(json).path("bestMatches");
            if (!matches.isArray() || matches.size() == 0) return null;

            // Prefer matches where region == "United States"
            for (JsonNode m : matches) {
                if ("United States".equalsIgnoreCase(text(m, "4. region"))) {
                    String sym = text(m, "1. symbol");
                    if (sym != null && !sym.isBlank()) return sym;
                }
            }
            // Fallback: top match symbol
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