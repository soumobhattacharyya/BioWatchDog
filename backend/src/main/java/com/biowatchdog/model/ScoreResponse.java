package com.biowatchdog.model;

public record ScoreResponse(
        String sessionId,
        String status,
        String message,
        int eventCount,
        int windowSize,
        Double similarity,
        String action
) {}
