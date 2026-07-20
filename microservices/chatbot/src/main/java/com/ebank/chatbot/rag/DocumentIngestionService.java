package com.ebank.chatbot.rag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Loads the banking-procedures knowledge base into the pgvector store at startup.
 *
 * <p>Pipeline (classic RAG ingestion / ETL):
 * <pre>
 *   markdown files  →  Document  →  TokenTextSplitter (chunks)  →  embed  →  pgvector
 * </pre>
 *
 * <p>Idempotent: if the vector store already holds rows (e.g. after a restart, or a
 * shared DB), ingestion is skipped so documents are not duplicated. Controlled by
 * {@code chatbot.rag.ingest-on-startup} (default {@code true}). Any failure (for
 * example a missing embedding API key) is logged but never blocks startup — the
 * service still boots and serves /actuator/health.
 */
@Component
public class DocumentIngestionService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    private static final String DOCS_LOCATION = "classpath:/rag-docs/*.md";

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final boolean ingestOnStartup;

    public DocumentIngestionService(
            VectorStore vectorStore,
            JdbcTemplate jdbcTemplate,
            @org.springframework.beans.factory.annotation.Value("${chatbot.rag.ingest-on-startup:true}") boolean ingestOnStartup) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.ingestOnStartup = ingestOnStartup;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!ingestOnStartup) {
            log.info("[rag] ingestion disabled (chatbot.rag.ingest-on-startup=false)");
            return;
        }
        try {
            if (alreadyPopulated()) {
                log.info("[rag] vector store already populated — skipping ingestion");
                return;
            }
            List<Document> documents = loadDocuments();
            if (documents.isEmpty()) {
                log.warn("[rag] no documents found under {}", DOCS_LOCATION);
                return;
            }
            List<Document> chunks = new TokenTextSplitter().apply(documents);
            log.info("[rag] ingesting {} documents → {} chunks into pgvector", documents.size(), chunks.size());
            vectorStore.add(chunks);
            log.info("[rag] ingestion complete");
        } catch (Exception e) {
            // Most common cause: embedding model not configured (no API key). Degrade gracefully.
            log.warn("[rag] ingestion skipped due to error: {}. "
                    + "The chat endpoint will still work but procedure answers will lack RAG context.",
                    e.getMessage());
        }
    }

    private boolean alreadyPopulated() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Integer.class);
            return count != null && count > 0;
        } catch (Exception e) {
            // Table not created yet → treat as empty.
            return false;
        }
    }

    private List<Document> loadDocuments() throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(DOCS_LOCATION);
        List<Document> documents = new ArrayList<>();
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            try (var in = resource.getInputStream()) {
                String content = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                if (content.isBlank()) {
                    continue;
                }
                documents.add(new Document(content, Map.of(
                        "source", filename == null ? "unknown" : filename,
                        "category", "banking-procedure")));
            }
        }
        return documents;
    }
}
