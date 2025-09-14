package org.spring_ai_mcp_server_sse.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocumentSearchService {

    private final VectorStore vectorStore;

    public DocumentSearchService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Tool(description = """
        Search through uploaded documents to find relevant information.
        Use this when the user asks questions about uploaded files or requests information that might be in their documents.
        For broad requests like 'read contents' or 'summarize', use general terms like 'document', 'information', or 'content'.
        """)
    public String searchDocuments(String query, String conversationId) {
        log.info("🔍 SEARCH TOOL CALLED: query='{}', conversationId='{}'", query, conversationId);
        
        if (query == null || query.isBlank()) {
            return "Please provide a search query.";
        }

        if (conversationId == null || conversationId.isBlank()) {
            log.warn("No conversation ID provided for document search, searching all documents");
        }

        try {
            log.info("Searching documents for query: '{}' in conversation: {}", query, conversationId);

            // For broad content requests, try multiple search terms
            List<Document> allResults = new ArrayList<>();
            if (query.toLowerCase().contains("content") || query.toLowerCase().contains("summary") || 
                query.toLowerCase().contains("read") || query.toLowerCase().contains("tell")) {
                // Use broad search terms for full document content
                log.info("📖 Broad content request detected, using comprehensive search");
                allResults.addAll(vectorStore.similaritySearch("document"));
                allResults.addAll(vectorStore.similaritySearch("information"));
                allResults.addAll(vectorStore.similaritySearch("the"));
            } else {
                // Use the specific query
                allResults.addAll(vectorStore.similaritySearch(query));
            }
            
            // Remove duplicates by document ID
            allResults = new ArrayList<>(allResults.stream()
                    .collect(Collectors.toMap(
                            Document::getId,
                            doc -> doc,
                            (existing, replacement) -> existing))
                    .values());
            
            log.info("📊 Found {} total documents before conversation filter", allResults.size());
            
            // Filter by conversation ID if provided
            List<Document> results = allResults;
            if (conversationId != null && !conversationId.isBlank()) {
                results = allResults.stream()
                    .filter(doc -> {
                        String docConvId = (String) doc.getMetadata().get("conversation_id");
                        log.debug("🔗 Comparing conversation IDs: doc='{}' vs search='{}", docConvId, conversationId);
                        return conversationId.equals(docConvId);
                    })
                    .collect(Collectors.toList());
            }
            
            log.info("📋 Found {} documents after conversation filter", results.size());
            
            // Limit to top 3 results for better response formatting (less truncation)
            if (results.size() > 3) {
                results = results.subList(0, 3);
            }

            if (results.isEmpty()) {
                if (conversationId != null && !conversationId.isBlank()) {
                    return "I couldn't find any relevant information in your uploaded documents for this query. " +
                           "Make sure you've uploaded documents and they contain information related to your question.";
                } else {
                    return "No relevant documents found for this query.";
                }
            }

            log.info("Found {} relevant document chunks", results.size());

            // Format the results with more content
            StringBuilder response = new StringBuilder();
            response.append("Based on your uploaded documents, here's what I found:\n\n");

            for (int i = 0; i < results.size(); i++) {
                Document doc = results.get(i);
                String fileName = doc.getMetadata().getOrDefault("original_filename", "Unknown file").toString();
                String content = doc.getText();

                assert content != null;
                log.info("📄 Document {}: {} chars from file '{}'", i+1, content.length(), fileName);

                // For content requests, show more text (up to 1500 chars)
                boolean isContentRequest = query.toLowerCase().contains("content") || 
                                         query.toLowerCase().contains("summary") || 
                                         query.toLowerCase().contains("read");
                
                int maxLength = isContentRequest ? 1500 : 800;
                if (content.length() > maxLength) {
                    content = content.substring(0, maxLength) + "...";
                }

                response.append(String.format("📄 **%s**\n", fileName));
                response.append(content);
                response.append("\n");
                
                if (i < results.size() - 1) {
                    response.append("\n---\n\n");
                }
            }

            return response.toString();

        } catch (Exception e) {
            log.error("Error searching documents: {}", e.getMessage(), e);
            return "Error searching documents: " + e.getMessage();
        }
    }

    @Tool(description = """
        List all documents that have been uploaded and processed in the current conversation.
        Useful for showing the user what files are available for querying.
        """)
    public String listUploadedDocuments(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return "No conversation ID provided.";
        }

        try {
            log.info("Listing documents for conversation: {}", conversationId);

            // Try multiple generic queries to get broader document coverage
            List<Document> allResults = new ArrayList<>();
            try {
                // Use broad terms that are likely to match most documents
                allResults.addAll(vectorStore.similaritySearch("the"));
                allResults.addAll(vectorStore.similaritySearch("and"));
                allResults.addAll(vectorStore.similaritySearch("information"));
            } catch (Exception e) {
                log.warn("Error getting documents with generic search: {}", e.getMessage());
                // Fallback to a simple search
                allResults.addAll(vectorStore.similaritySearch("content"));
            }
            
            // Remove duplicates by document ID
            allResults = new ArrayList<>(allResults.stream()
                    .collect(Collectors.toMap(
                            Document::getId,
                            doc -> doc,
                            (existing, replacement) -> existing))
                    .values());
            
            // Filter by conversation ID
            List<Document> results = allResults.stream()
                    .filter(doc -> conversationId.equals(doc.getMetadata().get("conversation_id")))
                    .toList();

            if (results.isEmpty()) {
                return "No documents have been uploaded and processed in this conversation yet. " +
                       "Upload a document using the upload button above to get started!";
            }

            // Get unique filenames
            List<String> uniqueFiles = results.stream()
                    .map(doc -> doc.getMetadata().getOrDefault("original_filename", "Unknown").toString())
                    .distinct()
                    .toList();

            StringBuilder response = new StringBuilder();
            response.append("📚 **Documents available in this conversation:**\n\n");

            for (int i = 0; i < uniqueFiles.size(); i++) {
                response.append(String.format("%d. %s\n", i + 1, uniqueFiles.get(i)));
            }

            response.append(String.format("\nTotal: %d document(s) with %d text chunks processed.", 
                    uniqueFiles.size(), results.size()));
            response.append("\n\nYou can now ask me questions about any of these documents!");

            return response.toString();

        } catch (Exception e) {
            log.error("Error listing documents: {}", e.getMessage(), e);
            return "Error listing documents: " + e.getMessage();
        }
    }
    
    @Tool(description = """
        Get the full contents of uploaded documents in the current conversation.
        Use this when the user specifically asks to read, show, or get the contents of their uploaded files.
        """)
    public String getDocumentContents(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return "No conversation ID provided.";
        }
        
        try {
            log.info("📜 GET CONTENTS TOOL CALLED: conversationId='{}'", conversationId);
            
            // Use multiple broad queries to get all document content
            List<Document> allResults = new ArrayList<>();
            allResults.addAll(vectorStore.similaritySearch("document"));
            allResults.addAll(vectorStore.similaritySearch("information"));
            allResults.addAll(vectorStore.similaritySearch("content"));
            allResults.addAll(vectorStore.similaritySearch("the"));
            
            // Remove duplicates by document ID
            allResults = new ArrayList<>(allResults.stream()
                    .collect(Collectors.toMap(
                            Document::getId,
                            doc -> doc,
                            (existing, replacement) -> existing))
                    .values());
            
            // Filter by conversation ID
            List<Document> results = allResults.stream()
                    .filter(doc -> conversationId.equals(doc.getMetadata().get("conversation_id")))
                    .toList();
            
            log.info("📋 Found {} document chunks for conversation", results.size());
            
            if (results.isEmpty()) {
                return "No documents found in this conversation. Please upload a document first.";
            }
            
            // Group by filename and show full content
            StringBuilder response = new StringBuilder();
            response.append("📄 **Complete Document Contents:**\n\n");
            
            // Get unique filenames
            List<String> uniqueFiles = results.stream()
                    .map(doc -> doc.getMetadata().getOrDefault("original_filename", "Unknown").toString())
                    .distinct()
                    .toList();
            
            for (String filename : uniqueFiles) {
                response.append(String.format("📄 **%s**\n\n", filename));
                
                // Get all chunks for this file
                List<Document> fileChunks = results.stream()
                        .filter(doc -> filename.equals(doc.getMetadata().get("original_filename")))
                        .toList();
                
                // Combine all chunks for full content
                for (Document chunk : fileChunks) {
                    response.append(chunk.getText());
                    response.append("\n");
                }
                
                response.append("\n---\n\n");
            }
            
            return response.toString();
            
        } catch (Exception e) {
            log.error("Error getting document contents: {}", e.getMessage(), e);
            return "Error retrieving document contents: " + e.getMessage();
        }
    }
}
