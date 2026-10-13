package com.biowatchdog.service;

import com.biowatchdog.model.ScoreResponse;
import com.biowatchdog.model.TelemetryEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TelemetryWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(TelemetryWebSocketHandler.class);
    private final ObjectMapper mapper;
    private final SessionWindowStore store;
    private final RiskPolicyService policy;
    private final Map<String, String> sessionToId = new ConcurrentHashMap<>();

    public TelemetryWebSocketHandler(ObjectMapper mapper, SessionWindowStore store, RiskPolicyService policy) {
        this.mapper = mapper;
        this.store = store;
        this.policy = policy;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String requested = queryParam(session.getUri(), "sessionId");
        String id;
        try { id = UUID.fromString(requested).toString(); }
        catch (Exception ex) {
            session.close(CloseStatus.BAD_DATA.withReason("sessionId must be a UUID"));
            return;
        }
        sessionToId.put(session.getId(), id);
        session.sendMessage(new TextMessage(mapper.writeValueAsString(Map.of("type", "CONNECTED", "sessionId", id, "windowSize", store.windowSize()))));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String id = sessionToId.get(session.getId());
        if (id == null) { session.close(CloseStatus.BAD_DATA); return; }
        if (message.getPayloadLength() > 4096) { session.close(CloseStatus.TOO_BIG); return; }
        try {
            TelemetryEvent event = mapper.readValue(message.getPayload(), TelemetryEvent.class);
            validate(event);
            List<TelemetryEvent> events = store.add(id, event);
            ScoreResponse response = policy.evaluate(id, events, store.windowSize());
            session.sendMessage(new TextMessage(mapper.writeValueAsString(response)));
        } catch (IllegalArgumentException ex) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(Map.of("type", "ERROR", "message", ex.getMessage()))));
        } catch (Exception ex) {
            log.warn("Rejected malformed telemetry message: {}", ex.getMessage());
            session.sendMessage(new TextMessage(mapper.writeValueAsString(Map.of("type", "ERROR", "message", "Malformed event"))));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String id = sessionToId.remove(session.getId());
        if (id != null) store.remove(id);
    }

    private void validate(TelemetryEvent event) {
        if (event == null || event.type() == null || event.eventTime() <= 0) throw new IllegalArgumentException("type and positive eventTime are required");
        long now = System.currentTimeMillis();
        if (event.eventTime() < now - 60_000 || event.eventTime() > now + 10_000) throw new IllegalArgumentException("eventTime outside allowed clock window");
        switch (event.type()) {
            case "KEY" -> {
                if (event.dwellMs() == null || event.flightMs() == null || !finiteRange(event.dwellMs(), 0, 2000) || !finiteRange(event.flightMs(), 0, 5000))
                    throw new IllegalArgumentException("KEY requires dwellMs [0,2000] and flightMs [0,5000]");
                if (event.x() != null || event.y() != null) throw new IllegalArgumentException("KEY event may not contain pointer fields");
            }
            case "POINTER" -> {
                if (event.x() == null || event.y() == null || !finiteRange(event.x(), 0, 1) || !finiteRange(event.y(), 0, 1))
                    throw new IllegalArgumentException("POINTER requires normalized x/y in [0,1]");
            }
            default -> throw new IllegalArgumentException("Unsupported event type");
        }
    }

    private boolean finiteRange(Double value, double min, double max) {
        return value != null && Double.isFinite(value) && value >= min && value <= max;
    }

    private String queryParam(URI uri, String name) {
        if (uri == null) return null;
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(name);
    }
}
