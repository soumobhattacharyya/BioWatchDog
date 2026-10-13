package com.biowatchdog.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelemetryEvent(
        String type,
        long eventTime,
        Double dwellMs,
        Double flightMs,
        Double x,
        Double y
) {}
