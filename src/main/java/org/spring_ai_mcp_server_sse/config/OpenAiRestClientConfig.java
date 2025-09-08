package org.spring_ai_mcp_server_sse.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenAiRestClientConfig {

    @Bean
    @Primary
    RestClient.Builder openAiRestClientBuilder() {
        var jdk = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .version(HttpClient.Version.HTTP_2)
                .build();

        var factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(Duration.ofSeconds(180));   // <-- enough for image gen

        return RestClient.builder().requestFactory(factory);
    }
}