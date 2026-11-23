package com.meld.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Durable queue worker settings (ARCHITECTURE.md §10).
 */
@Component
@ConfigurationProperties(prefix = "meld.execution")
public class ExecutionProperties {

    /** Tests disable the scheduler and drive the worker explicitly. */
    private boolean workerEnabled = true;
    private long pollIntervalMs = 500;
    private long heartbeatIntervalMs = 10_000;
    private Duration leaseDuration = Duration.ofSeconds(30);
    private int maxPerPoll = 5;
    private int maxNodesPerExecution = 100;

    public boolean isWorkerEnabled() { return workerEnabled; }
    public void setWorkerEnabled(boolean workerEnabled) { this.workerEnabled = workerEnabled; }

    public long getPollIntervalMs() { return pollIntervalMs; }
    public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }

    public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) { this.heartbeatIntervalMs = heartbeatIntervalMs; }

    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = leaseDuration; }

    public int getMaxPerPoll() { return maxPerPoll; }
    public void setMaxPerPoll(int maxPerPoll) { this.maxPerPoll = maxPerPoll; }

    public int getMaxNodesPerExecution() { return maxNodesPerExecution; }
    public void setMaxNodesPerExecution(int maxNodesPerExecution) { this.maxNodesPerExecution = maxNodesPerExecution; }
}
