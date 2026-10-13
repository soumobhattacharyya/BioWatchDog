package com.biowatchdog.service;

import com.biowatchdog.ml.EmbeddingEngine;
import com.biowatchdog.model.ScoreResponse;
import com.biowatchdog.model.TelemetryEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RiskPolicyService {
    private final FeatureExtractor extractor;
    private final EmbeddingEngine engine;
    private final int minEvents;
    private final double stepUpThreshold;

    public RiskPolicyService(FeatureExtractor extractor, EmbeddingEngine engine,
                             @Value("${biowatchdog.min-events:30}") int minEvents,
                             @Value("${biowatchdog.step-up-threshold:0.70}") double stepUpThreshold) {
        this.extractor = extractor;
        this.engine = engine;
        this.minEvents = minEvents;
        this.stepUpThreshold = stepUpThreshold;
    }

    public ScoreResponse evaluate(String sessionId, List<TelemetryEvent> events, int windowSize) {
        if (events.size() < minEvents) {
            return new ScoreResponse(sessionId, "INSUFFICIENT_DATA", "Collect more interaction events to evaluate this window.", events.size(), windowSize, null, "CONTINUE_COLLECTING");
        }
        if (!engine.isAvailable()) {
            return new ScoreResponse(sessionId, "MODEL_UNAVAILABLE", "No compatible trained ONNX encoder is loaded; no similarity score was fabricated.", events.size(), windowSize, null, "USE_NORMAL_AUTHENTICATION");
        }
        float[] vector = extractor.extract(events);
        float[] embedding = engine.embed(vector);
        if (embedding == null) {
            return new ScoreResponse(sessionId, "MODEL_UNAVAILABLE", "ONNX inference failed; continue with normal authentication.", events.size(), windowSize, null, "USE_NORMAL_AUTHENTICATION");
        }
        // Enrolled-profile comparison is intentionally not implemented in the MVP. Avoid presenting a one-sided embedding as a match score.
        return new ScoreResponse(sessionId, "PROFILE_REQUIRED", "Encoder ran, but no enrolled reference embedding is configured. Similarity is not yet available.", events.size(), windowSize, null, "USE_NORMAL_AUTHENTICATION");
    }
}
