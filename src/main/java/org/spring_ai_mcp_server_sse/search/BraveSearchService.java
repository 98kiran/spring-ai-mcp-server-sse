package org.spring_ai_mcp_server_sse.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class BraveSearchService {

    private final RestClient http = RestClient.builder()
            .baseUrl("https://api.search.brave.com")
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${brave.apiKey}")
    private String apiKey;

    @Tool(description = """
        Search the web with Brave. Use ONLY when the user asks to look something up online
        (e.g., "search", "find", "look up", "latest on ...").
        Returns the top results with titles and links.
        """)
    public String braveSearch(String query) {
        try {
            String q = query == null ? "" : query.trim();
            if (q.isEmpty()) {
                return "Please provide what you want me to search for.";
            }
            if (apiKey == null || apiKey.isBlank()) {
                return "Brave Search API key is not configured.";
            }

            // Build request
            String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8);
            String uri = "/res/v1/web/search?q=" + encoded
                    + "&count=5"          // number of results (1–20 per Brave docs)
                    + "&safesearch=moderate"; // off|moderate|strict

            log.info("Calling Brave search for: {}", q);
            String json = http.get()
                    .uri(uri)
                    .header("Accept", "application/json")
                    .header("X-Subscription-Token", apiKey)
                    .retrieve()
                    .body(String.class);

            log.info("Brave response: {}", truncate(json, 800));

            if (json == null) return "No results.";
            JsonNode root = mapper.readTree(json);

            // Handle API error payloads gracefully
            if (root.has("error")) {
                return "Brave API error: " + root.path("error").asText();
            }

            JsonNode web = root.path("web").path("results");
            if (!web.isArray() || web.size() == 0) {
                return "No results for: " + q;
            }

            List<String> lines = new ArrayList<>();
            int n = Math.min(5, web.size());
            for (int i = 0; i < n; i++) {
                JsonNode r = web.get(i);
                String title = text(r, "title");
                String url   = text(r, "url");
                String desc  = text(r, "description");
                if (title == null) title = url != null ? url : "Result " + (i + 1);
                lines.add("- " + title + " — " + (url == null ? "" : url)
                        + (desc == null || desc.isBlank() ? "" : ("\n  " + clip(desc, 240))));
            }

            return String.join("\n", lines);

        } catch (Exception e) {
            log.warn("Brave search failed", e);
            return "Error performing Brave search: " + e.getMessage();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null ? null : v.asText();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}