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

/**
 * 知识库入库流水线。
 * <p>
 * 接收 RawContent（文本 + metadata），依次执行：
 * 1. 分块（DocumentChunker 按 512 字符切块）
 * 2. Embedding（调用 DashScope text-embedding-v4 向量化，可通过配置关闭）
 * 3. 写入 ES VectorStore
 * <p>
 * 整个流程是异步的（CompletableFuture），IngestionService 通过 whenComplete 监听完成状态。
 * embedEnabled=false 时跳过 embedding 步骤，直接写入（适用于只做 BM25 全文检索的场景）。
 */
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

    /**
     * 执行入库流水线：分块 → embedding → 写入 ES。
     *
     * @param raw              原始内容（标题、正文、URL、作者等）
     * @param metadata         元数据（source_type, confidence_score 等）
     * @param progressUpdater  进度回调（用于更新任务状态）
     * @return CompletableFuture，完成后返回入库的 Document 列表
     */
    public CompletableFuture<List<Document>> execute(
            RawContent raw, Map<String, Object> metadata, Consumer<IngestionProgress> progressUpdater) {

        progressUpdater.accept(IngestionProgress.initial(1));

        // 第 1 步：分块
        return CompletableFuture.supplyAsync(() -> {
            List<Document> chunks = chunker.chunk(raw, metadata);
            IngestionProgress p = IngestionProgress.initial(1)
                    .withFetched(1).withChunked(chunks.size());
            progressUpdater.accept(p);
            return chunks;
        }).thenComposeAsync(chunks -> {
            // 第 2-3 步：embedding → 写入
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

    /**
     * 批量 embedding 后写入 ES VectorStore。
     */
    private CompletableFuture<List<Document>> embedAndIndex(
            List<Document> chunks, Consumer<IngestionProgress> progressUpdater) {

        return CompletableFuture.supplyAsync(() -> {
            // 逐条调用 embedding 模型
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

            // 批量写入 ES
            vectorStore.add(embedded);

            p = p.withIndexed(embedded.size());
            progressUpdater.accept(p);

            return embedded;
        });
    }
}
