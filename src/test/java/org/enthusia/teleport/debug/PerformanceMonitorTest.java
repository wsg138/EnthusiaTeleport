package org.enthusia.teleport.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class PerformanceMonitorTest {
    @Test
    void ignoresInvalidKeysAndZeroAmounts() {
        PerformanceMonitor monitor = new PerformanceMonitor(null);

        monitor.add(null, 1L);
        monitor.add("", 1L);
        monitor.add("   ", 1L);
        monitor.add("valid", 0L);

        assertEquals(0L, monitor.get("valid"));
        assertEquals(0, monitor.snapshot().size());
        assertEquals("", monitor.summary());
    }

    @Test
    void incrementsAndSignedAddsAccumulatePerCounter() {
        PerformanceMonitor monitor = new PerformanceMonitor(null);

        monitor.increment("teleport.success");
        monitor.increment("teleport.success");
        monitor.add("teleport.success", 3L);
        monitor.add("teleport.success", -1L);

        assertEquals(4L, monitor.get("teleport.success"));
        assertEquals(0L, monitor.get("missing"));
    }

    @Test
    void snapshotAndSummaryAreDeterministicallySortedByKey() {
        PerformanceMonitor monitor = new PerformanceMonitor(null);
        monitor.add("zeta", 2L);
        monitor.add("alpha", 1L);
        monitor.add("middle", 3L);

        assertEquals(List.of("alpha", "middle", "zeta"), List.copyOf(monitor.snapshot().keySet()));
        assertEquals("alpha=1, middle=3, zeta=2", monitor.summary());
    }
}
