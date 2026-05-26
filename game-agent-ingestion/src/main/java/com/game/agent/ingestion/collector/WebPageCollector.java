package com.game.agent.ingestion.collector;

import com.game.agent.common.metadata.SourceType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class WebPageCollector implements ContentSource {

    private static final Logger log = LoggerFactory.getLogger(WebPageCollector.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public SourceType sourceType() {
        return SourceType.OFFICIAL;
    }

    @Override
    public List<RawContent> fetch() {
        throw new UnsupportedOperationException(
                "WebPageCollector requires specific URLs to poll. Use collect(String url) instead.");
    }

    @Override
    public boolean supportsUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    public Optional<RawContent> collect(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "GameStrategyAgent/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Failed to fetch URL: {} (status={})", url, response.statusCode());
                return Optional.empty();
            }

            return Optional.of(parseHtml(url, response.body()));
        } catch (Exception e) {
            log.error("Error fetching URL: {}", url, e);
            return Optional.empty();
        }
    }

    private RawContent parseHtml(String url, String html) {
        Document doc = Jsoup.parse(html);

        String title = doc.title();
        doc.select("script, style, nav, footer, header, aside, .sidebar, .nav, .footer, .header").remove();
        String body = doc.body().text().trim();

        return new RawContent(
                title != null ? title : "Untitled",
                body,
                url,
                Instant.now(),
                null
        );
    }
}
