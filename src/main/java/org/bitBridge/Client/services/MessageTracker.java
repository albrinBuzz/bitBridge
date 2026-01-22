package org.bitBridge.Client.services;

import java.util.concurrent.atomic.AtomicInteger;

public class MessageTracker {
    private final AtomicInteger totalSent = new AtomicInteger(0);
    private final AtomicInteger totalDelivered = new AtomicInteger(0);

    public void trackNewMessage() {
        totalSent.incrementAndGet();
    }

    public void markAsDelivered() {
        totalDelivered.incrementAndGet();
    }

    // Getters necesarios para el MixedStressTestRunner
    public int getTotalSent() { return totalSent.get(); }
    public int getTotalDelivered() { return totalDelivered.get(); }

    public String getFullStats() {
        int sent = totalSent.get();
        int delivered = totalDelivered.get();
        int pending = sent - delivered;
        double efficiency = (sent > 0) ? (delivered * 100.0 / sent) : 0;

        return String.format("Sent: %d | Delivered: %d | Pending: %d | Efficiency: %.2f%%",
                sent, delivered, pending, efficiency);
    }
}