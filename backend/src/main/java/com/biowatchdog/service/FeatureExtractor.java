package com.biowatchdog.service;

import com.biowatchdog.model.TelemetryEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Fixed 16-feature mapping; this order is shared with ml/train_siamese.py. */
@Component
public class FeatureExtractor {
    public static final int FEATURE_COUNT = 16;

    public float[] extract(List<TelemetryEvent> events) {
        List<Double> dwells = new ArrayList<>(), flights = new ArrayList<>(), xs = new ArrayList<>(), ys = new ArrayList<>();
        for (TelemetryEvent event : events) {
            if ("KEY".equals(event.type())) {
                if (event.dwellMs() != null) dwells.add(event.dwellMs());
                if (event.flightMs() != null) flights.add(event.flightMs());
            } else if ("POINTER".equals(event.type())) {
                if (event.x() != null) xs.add(event.x());
                if (event.y() != null) ys.add(event.y());
            }
        }
        float[] result = new float[FEATURE_COUNT];
        putStats(result, 0, dwells);
        putStats(result, 4, flights);
        putStats(result, 8, xs);
        putStats(result, 12, ys);
        return result;
    }

    private void putStats(float[] out, int offset, List<Double> values) {
        if (values.isEmpty()) return;
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        out[offset] = (float) mean;
        out[offset + 1] = (float) Math.sqrt(variance);
        out[offset + 2] = (float) min;
        out[offset + 3] = (float) max;
    }
}
