package com.game.agent.ingestion.pipeline;

public record IngestionProgress(
        int total,
        int fetched,
        int chunked,
        int embedded,
        int indexed
) {
    public static IngestionProgress initial(int total) {
        return new IngestionProgress(total, 0, 0, 0, 0);
    }

    public IngestionProgress withFetched(int fetched) {
        return new IngestionProgress(total, fetched, chunked, embedded, indexed);
    }

    public IngestionProgress withChunked(int chunked) {
        return new IngestionProgress(total, fetched, chunked, embedded, indexed);
    }

    public IngestionProgress withEmbedded(int embedded) {
        return new IngestionProgress(total, fetched, chunked, embedded, indexed);
    }

    public IngestionProgress withIndexed(int indexed) {
        return new IngestionProgress(total, fetched, chunked, embedded, indexed);
    }
}
