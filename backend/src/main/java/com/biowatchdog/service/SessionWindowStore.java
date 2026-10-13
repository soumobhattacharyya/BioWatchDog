package com.biowatchdog.service;

import com.biowatchdog.model.TelemetryEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory bounded ring buffers only; telemetry is not written to disk. */
@Component
public class SessionWindowStore {
    private final Map<String, ArrayDeque<TelemetryEvent>> sessions = new ConcurrentHashMap<>();
    private final int windowSize;

    public SessionWindowStore(@Value("${biowatchdog.window-size:120}") int windowSize) {
        if (windowSize < 10 || windowSize > 2000) throw new IllegalArgumentException("window-size must be in [10, 2000]");
        this.windowSize = windowSize;
    }

    public synchronized List<TelemetryEvent> add(String sessionId, TelemetryEvent event) {
        ArrayDeque<TelemetryEvent> queue = sessions.computeIfAbsent(sessionId, ignored -> new ArrayDeque<>());
        queue.addLast(event);
        while (queue.size() > windowSize) queue.removeFirst();
        return List.copyOf(queue);
    }

    public List<TelemetryEvent> snapshot(String sessionId) {
        ArrayDeque<TelemetryEvent> queue = sessions.get(sessionId);
        if (queue == null) return List.of();
        synchronized (this) { return new ArrayList<>(queue); }
    }

    public int count(String sessionId) {
        ArrayDeque<TelemetryEvent> queue = sessions.get(sessionId);
        if (queue == null) return 0;
        synchronized (this) { return queue.size(); }
    }

    public int sessionCount() { return sessions.size(); }
    public int windowSize() { return windowSize; }
    public void remove(String sessionId) { sessions.remove(sessionId); }
}
