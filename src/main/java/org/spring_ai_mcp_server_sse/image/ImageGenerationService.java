package org.spring_ai_mcp_server_sse.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ImageGenerationService {

    private final ImageModel imageModel;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Autowired
    public ImageGenerationService(ImageModel imageModel) {
        this.imageModel = imageModel;
    }

    @Tool(description = """
        Generate an image using OpenAI's DALL-E model. Use when the user asks to create, 
        generate, or make an image (e.g., "create an image of...", "generate a picture of...", 
        "make an image showing...").
        Returns a direct URL to the generated image.
        """)
    public String generateImage(String prompt) {
        try {
            String cleanPrompt = prompt == null ? "" : prompt.trim();
            if (cleanPrompt.isEmpty()) {
                return "Please provide a description of what you want me to generate.";
            }

            if (apiKey == null || apiKey.isBlank()) {
                return "OpenAI API key is not configured.";
            }

            // Debug log to verify API key is being read
            log.info("API Key loaded: {}", apiKey.substring(0, 20) + "..." + apiKey.substring(apiKey.length() - 10));

            log.info("Generating image for prompt: {}", cleanPrompt);

            // Create image options for high-quality generation
            OpenAiImageOptions options = OpenAiImageOptions.builder()
                    .quality("hd")              // High definition quality
                    .style("vivid")             // Vivid style for more dramatic images
                    .width(1024)                // Image width
                    .height(1024)               // Image height
                    .N(1)                       // Generate 1 image
                    .responseFormat("url")      // Return as URL (not base64)
                    .build();

            // Create prompt with options
            ImagePrompt imagePrompt = new ImagePrompt(cleanPrompt, options);

            // Generate image
            ImageResponse response = imageModel.call(imagePrompt);

            if (response.getResults().isEmpty()) {
                return "Failed to generate image. Please try again with a different prompt.";
            }

            // Get the first (and only) generated image
            Image image = response.getResult().getOutput();
            String imageUrl = image.getUrl();

            if (imageUrl == null || imageUrl.isBlank()) {
                return "Image was generated but URL is not available.";
            }

            log.info("Successfully generated image: {}", truncate(imageUrl));

            // Return the direct URL to the generated image
            return imageUrl;

        } catch (Exception e) {
            log.warn("Image generation failed for prompt: {}", prompt, e);

            // Provide more specific error messages based on the exception type
            String errorMessage = "Error generating image: ";
            if (e.getCause() != null && e.getCause().getClass().getSimpleName().contains("ReadTimeoutException")) {
                errorMessage += "Request timed out. DALL-E image generation can take up to 60 seconds. Please try again.";
            } else if (e.getMessage() != null && e.getMessage().contains("ResourceAccessException")) {
                errorMessage += "Network connection issue. Please check your internet connection and try again.";
            } else {
                errorMessage += e.getMessage() + ". Please try again with a different prompt.";
            }

            return errorMessage;
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 100 ? s : s.substring(0, 100) + "…";
    }
}