package com.biowatchdog.ml;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** ONNX encoder adapter. Missing/unloadable models are reported as unavailable, never scored heuristically. */
@Component
public class OnnxEmbeddingEngine implements EmbeddingEngine {
    private static final Logger log = LoggerFactory.getLogger(OnnxEmbeddingEngine.class);
    private final OrtEnvironment environment;
    private OrtSession session;
    private String inputName;

    public OnnxEmbeddingEngine(@Value("${biowatchdog.model-path:}") String modelPath) {
        this.environment = OrtEnvironment.getEnvironment();
        if (modelPath == null || modelPath.isBlank()) {
            log.warn("BIOWATCHDOG_MODEL_PATH is not configured; model scoring is disabled.");
            return;
        }
        try {
            Path path = Path.of(modelPath).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                log.warn("Configured ONNX model file does not exist: {}", path);
                return;
            }
            this.session = environment.createSession(path.toString(), new OrtSession.SessionOptions());
            if (session.getInputInfo().size() != 1) {
                log.error("Expected ONNX encoder to have one input; found {}", session.getInputInfo().size());
                session.close();
                session = null;
                return;
            }
            this.inputName = session.getInputNames().iterator().next();
            log.info("Loaded ONNX encoder from {}", path);
        } catch (Exception ex) {
            log.error("Could not load ONNX model. Scoring remains disabled: {}", ex.getMessage());
            close();
        }
    }

    @Override
    public synchronized float[] embed(float[] features) {
        if (!isAvailable() || features == null || features.length == 0) return null;
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment,
                FloatBuffer.wrap(features), new long[]{1, features.length});
             OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
            Object value = result.get(0).getValue();
            if (value instanceof float[][] matrix && matrix.length == 1 && matrix[0].length > 0) {
                return matrix[0].clone();
            }
            log.error("ONNX output must be float[1][embeddingSize]. Got {}", value == null ? "null" : value.getClass().getName());
            return null;
        } catch (OrtException ex) {
            log.error("ONNX inference failed: {}", ex.getMessage());
            return null;
        }
    }

    @Override public boolean isAvailable() { return session != null; }

    @PreDestroy
    @Override
    public synchronized void close() {
        if (session != null) {
            try { session.close(); } catch (OrtException ex) { log.warn("Error closing ONNX session: {}", ex.getMessage()); }
            session = null;
        }
    }
}
