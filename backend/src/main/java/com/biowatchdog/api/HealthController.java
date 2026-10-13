package com.biowatchdog.api;

import com.biowatchdog.ml.EmbeddingEngine;
import com.biowatchdog.service.SessionWindowStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    private final EmbeddingEngine engine;
    private final SessionWindowStore store;

    public HealthController(EmbeddingEngine engine, SessionWindowStore store) {
        this.engine = engine;
        this.store = store;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "modelAvailable", engine.isAvailable(), "windowSize", store.windowSize());
    }

    @GetMapping("/api/sessions")
    public Map<String, Object> sessions() {
        return Map.of("activeDemoSessions", store.sessionCount(), "note", "In-memory session metadata only; not suitable as a production monitoring endpoint.");
    }
}
