package com.biowatchdog;

import com.biowatchdog.model.TelemetryEvent;
import com.biowatchdog.service.SessionWindowStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionWindowStoreTest {
    @Test
    void bufferIsBoundedAndKeepsNewestEvents() {
        SessionWindowStore store = new SessionWindowStore(10);
        for (int i = 0; i < 13; i++) store.add("session", new TelemetryEvent("POINTER", i + 1, null, null, 0.5, 0.5));
        assertEquals(10, store.count("session"));
        assertEquals(4, store.snapshot("session").get(0).eventTime());
        assertEquals(13, store.snapshot("session").get(9).eventTime());
        store.remove("session");
        assertEquals(0, store.sessionCount());
    }
}
