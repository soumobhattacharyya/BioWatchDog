package com.biowatchdog;

import com.biowatchdog.model.TelemetryEvent;
import com.biowatchdog.service.FeatureExtractor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FeatureExtractorTest {
    @Test
    void createsSixteenFeaturesAndSummarizesKeyboardTiming() {
        FeatureExtractor extractor = new FeatureExtractor();
        float[] features = extractor.extract(List.of(
                new TelemetryEvent("KEY", 1, 100.0, 50.0, null, null),
                new TelemetryEvent("KEY", 2, 200.0, 150.0, null, null),
                new TelemetryEvent("POINTER", 3, null, null, 0.2, 0.4),
                new TelemetryEvent("POINTER", 4, null, null, 0.8, 0.6)
        ));
        assertEquals(16, features.length);
        assertEquals(150.0f, features[0], 0.001f);
        assertEquals(50.0f, features[1], 0.001f);
        assertEquals(100.0f, features[2], 0.001f);
        assertEquals(200.0f, features[3], 0.001f);
        assertEquals(0.5f, features[8], 0.001f);
    }

    @Test
    void emptyEventsReturnZeros() {
        float[] features = new FeatureExtractor().extract(List.of());
        assertEquals(16, features.length);
        for (float value : features) assertEquals(0f, value);
    }
}
