package org.pulse.lwre;

import org.junit.Test;
import org.pulse.lwre.metric.Context;
import org.pulse.lwre.metric.Meter;
import org.pulse.lwre.metric.MetricRegistry;
import org.pulse.lwre.metric.Timer;

import static org.junit.Assert.*;

public class MetricRegistryTest {

    @Test
    public void testTimerMetrics() throws InterruptedException {
        MetricRegistry registry = new MetricRegistry();
        Timer timer = registry.timer("testTimer");
        try (Context context = timer.time()) {
            Thread.sleep(100);
        }
        assertEquals(1, timer.getCount());
        assertTrue(timer.getMeanRate() > 0);
        assertTrue(timer.get99thPercentile() > 0);
    }

    @Test
    public void testMeterMetrics() {
        MetricRegistry registry = new MetricRegistry();
        Meter meter = registry.meter("testMeter");
        meter.mark();
        meter.mark(2);
        assertEquals(3, meter.getCount());
        assertTrue(meter.getMeanRate() > 0);
    }

    @Test
    public void testMultipleMetrics() {
        MetricRegistry registry = new MetricRegistry();
        Timer timer1 = registry.timer("timer1");
        Timer timer2 = registry.timer("timer2");
        Meter meter1 = registry.meter("meter1");
        assertEquals(3, registry.getMetrics().size());
        assertTrue(registry.getMetrics().containsKey("timer1"));
        assertTrue(registry.getMetrics().containsKey("timer2"));
        assertTrue(registry.getMetrics().containsKey("meter1"));
    }
}