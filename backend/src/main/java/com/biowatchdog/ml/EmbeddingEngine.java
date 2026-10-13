package com.biowatchdog.ml;

/** Returns an embedding for a feature vector. Null means the model is unavailable. */
public interface EmbeddingEngine extends AutoCloseable {
    float[] embed(float[] features);
    boolean isAvailable();
    @Override default void close() {}
}
