package com.game.agent.ingestion.collector;

import com.game.agent.common.metadata.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

@Component
public class FileCollector implements ContentSource {

    private static final Logger log = LoggerFactory.getLogger(FileCollector.class);
    private static final java.util.Set<String> SUPPORTED_EXTENSIONS = java.util.Set.of(".md", ".txt");

    @Override
    public SourceType sourceType() {
        return SourceType.OFFICIAL;
    }

    @Override
    public java.util.List<RawContent> fetch() {
        throw new UnsupportedOperationException("FileCollector requires explicit files. Use collectFile(MultipartFile) instead.");
    }

    @Override
    public boolean supportsUrl(String url) {
        return false;
    }

    public Optional<RawContent> collectFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return Optional.empty();
        }

        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')).toLowerCase() : "";
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            log.warn("Unsupported file format: {}", ext);
            return Optional.empty();
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line).append("\n");
            }

            String title = filename.replace(ext, "");
            return Optional.of(new RawContent(
                    title,
                    body.toString().trim(),
                    null,
                    Instant.now(),
                    null
            ));
        } catch (Exception e) {
            log.error("Error reading file: {}", filename, e);
            return Optional.empty();
        }
    }
}
