package com.game.agent.api.controller;

import com.game.agent.common.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @Autowired
    private ApplicationAvailability availability;

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> status = Map.of(
                "status", "UP",
                "liveness", availability.getLivenessState().equals(LivenessState.CORRECT),
                "readiness", availability.getReadinessState().equals(ReadinessState.ACCEPTING_TRAFFIC),
                "uptime_seconds", java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 1000
        );
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
