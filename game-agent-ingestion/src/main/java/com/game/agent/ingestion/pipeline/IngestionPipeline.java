package com.game.agent.ingestion.pipeline;

import com.game.agent.ingestion.collector.RawContent;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Component
public class IngestionPipeline {

    private final DocumentChunker chunker;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final boolean embedEnabled;

    public IngestionPipeline(
            DocumentChunker chunker,
            EmbeddingModel embeddingModel,
            VectorStore vectorStore,
            @Value("${game-agent.ingestion.pipeline.embed-enabled:true}") boolean embedEnabled) {
        this.chunker = chunker;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.embedEnabled = embedEnabled;
    }

    public CompletableFuture<List<Document>> execute(
            RawContent raw, Map<String, Object> metadata, Consumer<IngestionProgress> progressUpdater) {

        progressUpdater.accept(IngestionProgress.initial(1));

        return CompletableFuture.supplyAsync(() -> {
            List<Document> chunks = chunker.chunk(raw, metadata);
            IngestionProgress p = IngestionProgress.initial(1)
                    .withFetched(1).withChunked(chunks.size());
            progressUpdater.accept(p);
            return chunks;
        }).thenComposeAsync(chunks -> {
            if (!embedEnabled || chunks.isEmpty()) {
                IngestionProgress p = IngestionProgress.initial(1)
                        .withFetched(1).withChunked(chunks.size())
                        .withEmbedded(0).withIndexed(chunks.size());
                progressUpdater.accept(p);
                return CompletableFuture.completedFuture(chunks);
            }
            return embedAndIndex(chunks, progressUpdater);
        });
    }

    private CompletableFuture<List<Document>> embedAndIndex(
            List<Document> chunks, Consumer<IngestionProgress> progressUpdater) {

        return CompletableFuture.supplyAsync(() -> {
            List<Document> embedded = chunks.stream()
                    .peek(doc -> {
                        float[] vector = embeddingModel.embed(doc);
                        if (vector != null) {
                            doc.getMetadata().put("embedding_dim", vector.length);
                        }
                    })
                    .toList();

            var p = IngestionProgress.initial(1)
                    .withFetched(1).withChunked(chunks.size())
                    .withEmbedded(embedded.size());
            progressUpdater.accept(p);

            vectorStore.add(embedded);

            p = p.withIndexed(embedded.size());
            progressUpdater.accept(p);

            return embedded;
        });
    }
}
