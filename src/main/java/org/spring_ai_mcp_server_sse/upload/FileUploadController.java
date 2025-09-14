package org.spring_ai_mcp_server_sse.upload;

import lombok.extern.slf4j.Slf4j;
import org.spring_ai_mcp_server_sse.etl.EtlService;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:8080", "http://127.0.0.1:8080"})
public class FileUploadController {

    private final EtlService etlService;
    private final Path tempUploadDir;

    public FileUploadController(EtlService etlService) {
        this.etlService = etlService;
        this.tempUploadDir = Paths.get(System.getProperty("java.io.tmpdir"), "violet-uploads");
        try {
            Files.createDirectories(tempUploadDir);
        } catch (IOException e) {
            log.error("Failed to create upload directory: {}", e.getMessage());
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> uploadFile(
            @RequestPart("file") Mono<FilePart> filePartMono,
            @RequestPart(value = "conversationId", required = false) String conversationId) {
        
        return filePartMono.flatMap(filePart -> {
            String originalFilename = filePart.filename();
            
            if (originalFilename == null || originalFilename.isBlank()) {
                return Mono.just(ResponseEntity.badRequest()
                        .body(Map.<String, Object>of("error", "Invalid filename", "success", false)));
            }
            
            log.info("Received file upload: {} for conversation: {}", originalFilename, conversationId);
            
            // Create unique temp filename
            String uniqueFilename = UUID.randomUUID() + "-" + originalFilename;
            Path destinationPath = tempUploadDir.resolve(uniqueFilename);
            
            // Save the file
            return DataBufferUtils.write(filePart.content(), destinationPath)
                    .then(Mono.fromCallable(() -> {
                        try {
                            long fileSize = Files.size(destinationPath);
                            
                            return ResponseEntity.ok(Map.<String, Object>of(
                                    "success", true,
                                    "message", "File uploaded successfully",
                                    "filename", originalFilename,
                                    "tempFilename", uniqueFilename,
                                    "size", fileSize,
                                    "contentType", "application/octet-stream",
                                    "conversationId", conversationId != null ? conversationId : ""
                            ));
                        } catch (IOException e) {
                            log.error("Error getting file size: {}", e.getMessage());
                            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                    .body(Map.<String, Object>of("error", "Upload failed: " + e.getMessage(), "success", false));
                        }
                    }))
                    .onErrorResume(error -> {
                        log.error("File upload failed: {}", error.getMessage());
                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.<String, Object>of("error", "Upload failed: " + error.getMessage(), "success", false)));
                    });
        }).switchIfEmpty(Mono.just(ResponseEntity.badRequest()
                .body(Map.<String, Object>of("error", "No file provided", "success", false))));
    }

    /**
     * Get supported file types
     */
    @GetMapping("/supported-types")
    public Mono<ResponseEntity<Map<String, Object>>> getSupportedTypes() {
        return Mono.just(ResponseEntity.ok(Map.<String, Object>of(
                "supportedExtensions", new String[]{".txt", ".pdf", ".docx", ".pptx", ".xlsx", ".md", ".html", ".doc"},
                "description", "Supported document formats for processing"
        )));
    }
}