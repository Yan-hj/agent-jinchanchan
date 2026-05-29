package com.game.agent.ingestion.collector;

import com.game.agent.common.metadata.SourceType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * B站内容采集器。
 * <p>
 * 通过 HTTP + Jsoup 抓取 B站页面（服务端渲染，不需要浏览器），提取视频标题、简介、作者等信息。
 * 支持两种抓取方式：
 * 1. collect(url)：抓取单个视频页
 * 2. collectFromSpacePage(spaceUrl, creatorName)：抓取 UP 主空间页，批量解析视频列表
 * <p>
 * 注意：B站的空间页 HTML 结构可能会改版，如果 parseSpacePage 解析不到视频卡片需要调整 CSS 选择器。
 */
@Component
public class BilibiliContentSource implements ContentSource {

    private static final Logger log = LoggerFactory.getLogger(BilibiliContentSource.class);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String BILIBILI_HOST = "www.bilibili.com";

    // 模拟浏览器 UA，避免被 B站反爬拦截
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public SourceType sourceType() {
        return SourceType.SOCIAL;
    }

    @Override
    public List<RawContent> fetch() {
        throw new UnsupportedOperationException(
                "BilibiliContentSource requires specific video URLs. Use collect(String url) instead.");
    }

    @Override
    public boolean supportsUrl(String url) {
        if (url == null) return false;
        return url.contains(BILIBILI_HOST) && url.contains("/video/");
    }

    /**
     * 抓取单个 B 站视频页。
     * 解析 OG 元数据（og:title, og:description, author）提取视频信息。
     */
    public Optional<RawContent> collect(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "https://www.bilibili.com/")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("B站视频页返回非200: {} (status={})", url, response.statusCode());
                return Optional.empty();
            }

            return Optional.of(parseVideoPage(url, response.body()));
        } catch (Exception e) {
            log.error("B站视频页抓取失败: {}", url, e);
            return Optional.empty();
        }
    }

    /**
     * 抓取 UP 主空间页，解析视频列表。
     * 适用于批量抓取场景，如 "红莲"、"神超" 等金铲铲 UP 主。
     */
    public List<RawContent> collectFromSpacePage(String spaceUrl, String creatorName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(spaceUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return List.of();

            return parseSpacePage(response.body(), creatorName);
        } catch (Exception e) {
            log.error("B站空间页抓取失败: {}", spaceUrl, e);
            return List.of();
        }
    }

    /**
     * 解析视频页 HTML，提取标题、描述、作者等信息。
     * 优先使用 Open Graph 元数据，fallback 到 DOM 选择器。
     */
    private RawContent parseVideoPage(String url, String html) {
        Document doc = Jsoup.parse(html);

        String title = metaContent(doc, "og:title");
        if (title == null || title.isBlank()) {
            title = doc.select("h1.video-title").text();
        }

        String description = metaContent(doc, "description");
        if (description == null || description.isBlank()) {
            description = metaContent(doc, "og:description");
        }

        String author = metaContent(doc, "author");
        if (author == null || author.isBlank()) {
            author = doc.select("a.username").text();
        }

        String keywords = metaContent(doc, "keywords");

        StringBuilder body = new StringBuilder();
        if (title != null) body.append(title).append("。");
        if (description != null) body.append(description);
        if (keywords != null) body.append(" 标签:").append(keywords);

        return new RawContent(
                title != null ? title : "B站视频",
                body.toString(),
                url,
                Instant.now(),
                author != null && !author.isBlank() ? author : "未知UP主"
        );
    }

    /**
     * 解析 UP 主空间页 HTML，提取视频卡片列表。
     * 选择器支持多种空间页模板样式。
     */
    private List<RawContent> parseSpacePage(String html, String creatorName) {
        Document doc = Jsoup.parse(html);
        List<RawContent> results = new ArrayList<>();

        for (Element card : doc.select("div.video-card, li.small-item, div.cube-card")) {
            Element link = card.selectFirst("a[href*=/video/]");
            if (link == null) continue;

            String href = link.attr("href");
            String fullUrl = href.startsWith("//") ? "https:" + href
                    : href.startsWith("/") ? "https://www.bilibili.com" + href : href;

            String title = link.attr("title");
            if (title.isBlank()) {
                title = card.select(".title").text();
            }

            Element lengthEl = card.selectFirst(".length");
            String duration = lengthEl != null ? lengthEl.text() : "";

            String desc = creatorName + " " + duration + " " + title;
            results.add(new RawContent(
                    title, desc, fullUrl, Instant.now(), creatorName));
        }

        log.info("从B站空间页解析到 {} 个视频 (UP主: {})", results.size(), creatorName);
        return results;
    }

    /** 读取 HTML meta 标签内容 */
    private String metaContent(Document doc, String property) {
        Element meta = doc.selectFirst("meta[property=" + property + "]");
        if (meta == null) {
            meta = doc.selectFirst("meta[name=" + property + "]");
        }
        return meta != null ? meta.attr("content") : null;
    }
}
