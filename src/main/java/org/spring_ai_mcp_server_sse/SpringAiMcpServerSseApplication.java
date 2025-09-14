package org.spring_ai_mcp_server_sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class SpringAiMcpServerSseApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringAiMcpServerSseApplication.class, args);
		log.info("Violet Chat MCP Server started successfully! Ready to handle file uploads and tool requests.");
	}
}
