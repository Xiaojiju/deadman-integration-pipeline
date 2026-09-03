package com.mtfm.gateway.runtime.reply;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplyWaiterTest {

    @Test
    void completeRemovesPending() {
        ReplyWaiter waiter = new ReplyWaiter(8, 4, pending -> {
        });
        waiter.start();
        assertTrue(waiter.tryRegister(new ReplyWaiter.Pending(
                "req-1", "door-1", "fn.open", "req-1", "ok", Instant.now().plusSeconds(5))));
        assertEquals("req-1", waiter.complete("door-1", "req-1").orElseThrow().requestId());
        assertTrue(waiter.complete("door-1", "req-1").isEmpty());
        waiter.close();
    }

    @Test
    void timeoutFiresCallback() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ReplyWaiter.Pending> expired = new AtomicReference<>();
        ReplyWaiter waiter = new ReplyWaiter(8, 4, pending -> {
            expired.set(pending);
            latch.countDown();
        });
        waiter.start();
        assertTrue(waiter.tryRegister(new ReplyWaiter.Pending(
                "req-2", "door-1", "fn.open", "req-2", null, Instant.now().plus(Duration.ofMillis(30)))));
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("req-2", expired.get().requestId());
        waiter.close();
    }

    @Test
    void awaitingTracksFunctionOccupancy() {
        ReplyWaiter waiter = new ReplyWaiter(8, 4, pending -> {
        });
        waiter.start();
        assertTrue(waiter.tryRegister(new ReplyWaiter.Pending(
                "req-3", "door-1", "fn.open", "req-3", null, Instant.now().plusSeconds(5))));
        assertTrue(waiter.awaiting("door-1", "fn.open"));
        assertTrue(waiter.complete("door-1", "req-3").isPresent());
        assertFalse(waiter.awaiting("door-1", "fn.open"));
        waiter.close();
    }
}
