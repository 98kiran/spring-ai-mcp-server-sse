package org.spring_ai_mcp_server_sse;

import org.spring_ai_mcp_server_sse.search.BraveSearchService;
import org.spring_ai_mcp_server_sse.stocks.StockService;
import org.spring_ai_mcp_server_sse.utils.TimeUtilsService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SpringAiMcpServerSseApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringAiMcpServerSseApplication.class, args);
	}

    @Bean
    public ToolCallbackProvider tools(
            StockService stockService,
            BraveSearchService braveSearchService,
            TimeUtilsService timeUtilsService
    ) {
        return MethodToolCallbackProvider
                .builder()
                .toolObjects(
                        stockService,
                        braveSearchService,
                        timeUtilsService
                )
                .build();
    }

}
