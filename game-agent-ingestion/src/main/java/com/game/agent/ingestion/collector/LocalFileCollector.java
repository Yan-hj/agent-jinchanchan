package com.game.agent.ingestion.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Component
public class LocalFileCollector {

    private static final Logger log = LoggerFactory.getLogger(LocalFileCollector.class);
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".md", ".txt");

    public Optional<RawContent> collect(String filePath) {
        Path path = Path.of(filePath);

        if (!Files.exists(path)) {
            log.warn("File not found: {}", filePath);
            return Optional.empty();
        }

        if (!Files.isRegularFile(path)) {
            log.warn("Not a regular file: {}", filePath);
            return Optional.empty();
        }

        String filename = path.getFileName().toString();
        String ext = filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.')).toLowerCase()
                : "";
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            log.warn("Unsupported file format: {}", ext);
            return Optional.empty();
        }

        try {
            String body = Files.readString(path, StandardCharsets.UTF_8);
            String title = filename.replace(ext, "");
            log.info("Local file loaded: {} ({} chars)", filePath, body.length());
            return Optional.of(new RawContent(title, body.trim(), null, Instant.now(), null));
        } catch (IOException e) {
            log.error("Error reading local file: {}", filePath, e);
            return Optional.empty();
        }
    }
}
