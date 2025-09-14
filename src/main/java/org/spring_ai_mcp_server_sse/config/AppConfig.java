package org.spring_ai_mcp_server_sse.config;

import org.spring_ai_mcp_server_sse.image.ImageGenerationService;
import org.spring_ai_mcp_server_sse.search.BraveSearchService;
import org.spring_ai_mcp_server_sse.stocks.StockService;
import org.spring_ai_mcp_server_sse.utils.TimeUtilsService;
import org.spring_ai_mcp_server_sse.etl.EtlService;
import org.spring_ai_mcp_server_sse.rag.DocumentSearchService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public ToolCallbackProvider tools(
            StockService stockService,
            BraveSearchService braveSearchService,
            TimeUtilsService timeUtilsService,
            ImageGenerationService imageGenerationService,
            EtlService etlService,
            DocumentSearchService documentSearchService
    ) {
        return MethodToolCallbackProvider
                .builder()
                .toolObjects(
                        stockService,
                        braveSearchService,
                        timeUtilsService,
                        imageGenerationService,
                        etlService,
                        documentSearchService
                )
                .build();
    }
}
