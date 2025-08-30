package org.spring_ai_mcp_server_sse;

import org.spring_ai_mcp_server_sse.stocks.StockService;
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
    public ToolCallbackProvider stockTools(StockService stockService) {
        return MethodToolCallbackProvider
                .builder()
                .toolObjects(stockService)
                .build();
    }

}
