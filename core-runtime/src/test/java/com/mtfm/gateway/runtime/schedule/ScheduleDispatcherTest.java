package com.mtfm.gateway.runtime.schedule;

import com.mtfm.gateway.runtime.metrics.CountingGatewayMetrics;
import com.mtfm.gateway.spi.metrics.GatewayMetrics;
import com.mtfm.gateway.spi.port.DeviceScheduleRegistry.ScheduledFunction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleDispatcherTest {

    @Test
    void firesAfterInterval() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        ScheduleDispatcher dispatcher = new ScheduleDispatcher(20);
        dispatcher.setHandler((deviceId, functionId) -> latch.countDown());
        dispatcher.start();
        dispatcher.replace("dev-1", List.of(new ScheduledFunction("fn.poll", 20)));
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        dispatcher.close();
    }

    @Test
    void replaceClearsPreviousJobs() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        ScheduleDispatcher dispatcher = new ScheduleDispatcher(20);
        dispatcher.setHandler((deviceId, functionId) -> hits.incrementAndGet());
        dispatcher.start();
        dispatcher.replace("dev-1", List.of(new ScheduledFunction("fn.poll", 20)));
        dispatcher.replace("dev-1", List.of());
        Thread.sleep(120);
        assertEquals(0, hits.get());
        dispatcher.close();
    }

    @Test
    void skipsIntervalBelowMinimum() {
        ScheduleDispatcher dispatcher = new ScheduleDispatcher(1000);
        dispatcher.replace("dev-1", List.of(new ScheduledFunction("fn.poll", 20)));
        assertEquals(0, dispatcher.size());
        dispatcher.close();
    }

    @Test
    void skipsWhenReplyStillAwaiting() throws Exception {
        CountingGatewayMetrics metrics = new CountingGatewayMetrics();
        AtomicInteger hits = new AtomicInteger();
        CountDownLatch skipped = new CountDownLatch(1);
        ScheduleDispatcher dispatcher = new ScheduleDispatcher(20);
        dispatcher.setMetrics(metrics);
        dispatcher.setOccupancy((deviceId, functionId) -> {
            skipped.countDown();
            return true;
        });
        dispatcher.setHandler((deviceId, functionId) -> hits.incrementAndGet());
        dispatcher.start();
        dispatcher.replace("dev-1", List.of(new ScheduledFunction("fn.poll", 20)));
        assertTrue(skipped.await(2, TimeUnit.SECONDS));
        Thread.sleep(40);
        assertEquals(0, hits.get());
        assertTrue(metrics.get("schedule.skip." + GatewayMetrics.SCHEDULE_SKIP_INFLIGHT) >= 1);
        dispatcher.close();
    }
}
