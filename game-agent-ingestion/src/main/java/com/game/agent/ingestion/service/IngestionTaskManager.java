package com.game.agent.ingestion.service;

import com.game.agent.ingestion.model.IngestStatus;
import com.game.agent.ingestion.model.IngestTask;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class IngestionTaskManager {

    private static final Logger log = LoggerFactory.getLogger(IngestionTaskManager.class);
    private final ConcurrentHashMap<String, IngestTask> tasks = new ConcurrentHashMap<>();

    private final int taskTtlMinutes;

    public IngestionTaskManager(
            @Value("${game-agent.ingestion.task-ttl-minutes:60}") int taskTtlMinutes) {
        this.taskTtlMinutes = taskTtlMinutes;
    }

    @PostConstruct
    void startCleanupScheduler() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ingestion-task-cleanup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::cleanupExpiredTasks, taskTtlMinutes, taskTtlMinutes, TimeUnit.MINUTES);
    }

    public IngestTask create(String taskId, String sourceType, String sourceUrl, String documentName) {
        IngestTask task = new IngestTask(
                taskId, IngestStatus.PENDING, sourceType, sourceUrl, documentName,
                null, null, Instant.now(), Instant.now());
        tasks.put(taskId, task);
        return task;
    }

    public void update(IngestTask task) {
        tasks.put(task.taskId(), task);
    }

    public IngestTask get(String taskId) {
        return tasks.get(taskId);
    }

    public List<IngestTask> list(IngestStatus statusFilter) {
        return tasks.values().stream()
                .filter(t -> statusFilter == null || t.status() == statusFilter)
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .toList();
    }

    private void cleanupExpiredTasks() {
        Instant cutoff = Instant.now().minusSeconds(taskTtlMinutes * 60L);
        tasks.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().createdAt().isBefore(cutoff)
                    && entry.getValue().status() != IngestStatus.RUNNING;
            if (expired) {
                log.debug("Cleaned up expired ingest task: {}", entry.getKey());
            }
            return expired;
        });
    }
}
