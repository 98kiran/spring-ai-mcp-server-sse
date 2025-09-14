package org.spring_ai_mcp_server_sse.etl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.ai.tool.annotation.Tool;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.UUID;

@Slf4j
@Service
public class EtlService {

    private final VectorStore vectorStore;
    private final String documentFolderPath;
    private final boolean loadDocumentsOnStartup;
    private final List<String> allowedExtensions = List.of(".txt", ".pdf", ".docx", ".pptx", ".xlsx", ".md", ".html", ".doc");
    private final Path tempUploadDir;

    public EtlService(
            VectorStore vectorStore,
            @Value("${etl.documents.folder-path:NOT_FOUND}") String documentFolderPath,
            @Value("${etl.documents.load-on-startup:false}") boolean loadDocumentsOnStartup
    ) {
        this.vectorStore = vectorStore;
        this.documentFolderPath = documentFolderPath;
        this.loadDocumentsOnStartup = loadDocumentsOnStartup;

        // Create temp directory for file uploads if it doesn't exist
        this.tempUploadDir = Paths.get(System.getProperty("java.io.tmpdir"), "violet-uploads");
        try {
            Files.createDirectories(tempUploadDir);
            log.info("Created temporary upload directory: {}", tempUploadDir);
        } catch (IOException e) {
            log.error("Failed to create temporary upload directory: {}", e.getMessage());
        }
    }

    /**
     * Load documents into vector database from configured path on startup
     */
    public void loadDocumentsIntoVectorDatabase() {
        log.info("documentFolderPath: {}", documentFolderPath);
        log.info("loadDocumentsOnStartup: {}", loadDocumentsOnStartup);
        if (!loadDocumentsOnStartup) {
            log.info("Skipping document load on startup (Tika runner).");
            return;
        }

        final List<Document> allRawDocuments = new ArrayList<>();

        try {
            allRawDocuments.addAll(processDocumentSource(documentFolderPath));
        } catch (IOException e) {
            log.error("Tika Runner: Error during scanning: {}", e.getMessage(), e);
            return;
        }

        processAndStoreDocuments(allRawDocuments);
    }

    /**
     * Process and store a single file uploaded by a user
     *
     * @param conversationId The conversation ID to associate with this file
     * @param originalFileName The original name of the uploaded file
     * @param tempFileName The temporary filename (with UUID prefix) in the upload directory
     * @param fileType The content type of the file
     * @return A summary of the processing result
     */
    @Tool(description = "Process a file that has been uploaded by the user and add it to the knowledge base. Use the temp filename for processing.")
    public String processUploadedFile(String conversationId, String originalFileName, String tempFileName, String fileType) {
        log.info("🔥 ETL TOOL CALLED: Starting to process uploaded file");
        log.info("📋 Parameters: conversationId={}, originalFileName={}, tempFileName={}, fileType={}", 
                conversationId, originalFileName, tempFileName, fileType);
        
        if (conversationId == null || conversationId.isBlank()) {
            log.error("❌ Error: No conversation ID provided");
            return "Error: No conversation ID provided";
        }

        if (tempFileName == null || tempFileName.isBlank()) {
            log.error("❌ Error: No temp filename provided");
            return "Error: No temp filename provided";
        }

        // Look for the temp file in our upload directory
        Path filePath = tempUploadDir.resolve(tempFileName);
        log.info("📁 Looking for temp file at: {}", filePath);
        
        if (!Files.exists(filePath)) {
            log.error("❌ File not found at path: {}", filePath);
            return "Error: Could not find the uploaded file. Please try uploading it again.";
        }
        
        log.info("✅ Found temp file, starting processing...");

        try {
            log.info("🔧 Creating FileSystemResource from: {}", filePath);
            FileSystemResource resource = new FileSystemResource(filePath);
            
            log.info("📖 Processing resource with Tika...");
            // Use original filename for metadata, but process from temp location
            List<Document> documents = processResource(resource, originalFileName);
            log.info("📄 Extracted {} documents from file", documents.size());

            if (documents.isEmpty()) {
                log.warn("⚠️ No content extracted from file");
                return "The file was processed, but no content could be extracted. Please try with a different file.";
            }

            log.info("🏷️ Adding metadata to {} documents...", documents.size());
            // Add conversation-specific metadata
            for (Document doc : documents) {
                doc.getMetadata().put("conversation_id", conversationId);
                doc.getMetadata().put("upload_time", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
                doc.getMetadata().put("file_type", fileType);
                doc.getMetadata().put("original_filename", originalFileName);
                doc.getMetadata().put("temp_filename", tempFileName);
            }

            log.info("⚡ Processing and storing documents in vector database...");
            // Process and store the documents
            int chunks = processAndStoreDocuments(documents);
            
            String result = String.format("✅ Successfully processed file '%s' and added %d chunks to the knowledge base. You can now ask questions about this document.", originalFileName, chunks);
            log.info("🎉 ETL COMPLETED: {}", result);
            return result;

        } catch (Exception e) {
            log.error("💥 ERROR processing uploaded file {} (temp: {}): {}", originalFileName, tempFileName, e.getMessage(), e);
            return "Error processing the file: " + e.getMessage();
        } finally {
            // Clean up the temp file after processing
            log.info("🧹 Cleaning up temp file: {}", filePath);
            try {
                Files.deleteIfExists(filePath);
                log.info("✅ Temp file deleted successfully");
            } catch (IOException e) {
                log.warn("⚠️ Could not delete temporary file: {}", filePath);
            }
        }
    }

    /**
     * Process a document source (classpath or filesystem) and extract documents
     */
    private List<Document> processDocumentSource(String sourcePath) throws IOException {
        final List<Document> extractedDocuments = new ArrayList<>();

        // === A) CLASSPATH MODE ===
        if (sourcePath != null && sourcePath.startsWith("classpath:")) {
            String base = sourcePath.substring("classpath:".length());
            if (base.startsWith("/")) base = base.substring(1);  // normalize
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

            // scan everything under the folder (any depth)
            Resource[] resources = resolver.getResources("classpath*:" + base + "/**/*");
            if (resources.length == 0) {
                log.warn("Tika Runner: No resources found on classpath under '{}'", base);
            } else {
                log.info("Tika Runner: Found {} classpath resources, filtering by extension", resources.length);
                for (Resource res : resources) {
                    String name = res.getFilename();
                    if (name == null) continue;
                    String lower = name.toLowerCase();
                    if (allowedExtensions.stream().noneMatch(lower::endsWith)) continue;

                    List<Document> docs = processResource(res, name);
                    extractedDocuments.addAll(docs);
                }
            }
            // === B) FILESYSTEM MODE ===
        } else {
            String folder = sourcePath;
            File baseDir = new File(folder);
            if (!baseDir.exists() || !baseDir.isDirectory()) {
                log.error("Tika Runner: Document folder path does not exist or is not a directory: {}", folder);
                return extractedDocuments;
            }
            log.info("Tika Runner: Starting ETL from filesystem folder: {}", folder);

            try (Stream<Path> paths = Files.walk(Paths.get(folder))) {
                List<Path> files = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String f = p.getFileName().toString().toLowerCase();
                            return allowedExtensions.stream().anyMatch(f::endsWith);
                        })
                        .toList();

                if (files.isEmpty()) {
                    log.warn("Tika Runner: No files found in directory: {} with extensions: {}", folder, String.join(", ", allowedExtensions));
                } else {
                    log.info("Tika Runner: Found {} files to process", files.size());
                    for (Path p : files) {
                        FileSystemResource res = new FileSystemResource(p);
                        List<Document> docs = processResource(res, p.getFileName().toString());
                        extractedDocuments.addAll(docs);
                    }
                }
            }
        }

        return extractedDocuments;
    }

    /**
     * Process a single resource using Tika
     */
    private List<Document> processResource(Resource resource, String filename) {
        List<Document> docs = new ArrayList<>();

        try {
            TikaDocumentReader tika = new TikaDocumentReader(resource);
            List<Document> extractedDocs = tika.get();

            if (extractedDocs != null && !extractedDocs.isEmpty()) {
                for (Document d : extractedDocs) {
                    // Add metadata
                    d.getMetadata().putIfAbsent("source_uri", safeUri(resource));
                    d.getMetadata().putIfAbsent("file_name", filename);
                    d.getMetadata().putIfAbsent("processor", "tika");
                    d.getMetadata().putIfAbsent("id", UUID.randomUUID().toString());
                }
                docs.addAll(extractedDocs);
                log.info("Tika Runner: Extracted {} documents from resource: {}", extractedDocs.size(), filename);
            } else {
                log.warn("Tika Runner: No documents extracted from resource: {}", filename);
            }
        } catch (Exception e) {
            log.error("Tika Runner: Error processing resource {}: {}", filename, e.getMessage());
        }

        return docs;
    }

    /**
     * Process and store documents in the vector database
     *
     * @param documents The documents to process and store
     * @return The number of chunks stored
     */
    private int processAndStoreDocuments(List<Document> documents) {
        if (documents.isEmpty()) {
            log.warn("No documents to process");
            return 0;
        }

        log.info("Processing {} raw documents", documents.size());

        // === Token Splitting ===
        TokenTextSplitter textSplitter = new TokenTextSplitter(1000, 50, 10, 10000, true);
        log.info("Applying text splitter to {} raw documents", documents.size());
        List<Document> chunkedDocuments = textSplitter.apply(documents);
        log.info("Split documents into {} chunks", chunkedDocuments.size());

        if (chunkedDocuments.isEmpty()) {
            log.warn("No document chunks to add to vector store after splitting.");
            return 0;
        }

        // === Log IDs ===
        List<String> documentIdsToLog = new ArrayList<>(chunkedDocuments.size());
        for (Document chunk : chunkedDocuments) {
            documentIdsToLog.add(
                    chunk.getId() + " (source: " + chunk.getMetadata().getOrDefault("file_name", "unknown") + ")"
            );
        }

        log.info("Adding {} document chunks to vector store: {}",
                chunkedDocuments.size(), vectorStore.getClass().getSimpleName());
        vectorStore.add(chunkedDocuments);
        log.info("Successfully added {} document chunks to vector store", chunkedDocuments.size());

        // Log a sample of the document IDs
        log.info(" --- Document IDs added to Vector Store ---");
        int count = 0;
        for (String idLine : documentIdsToLog) {
            log.info(idLine);
            count++;
            if (count >= 20 && documentIdsToLog.size() > 25) {
                log.info("... And ({} more document IDs)", (documentIdsToLog.size() - count));
                break;
            }
        }
        log.info(" -------------End of Document IDs----------- ");

        return chunkedDocuments.size();
    }


private String safeUri(Resource res) {
    try { return res.getURI().toString(); } catch (Exception ignored) { return "classpath"; }
}
}
