package com.meld.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the execution worker's polling loop. The worker itself checks
 * {@code meld.execution.worker-enabled} so tests can drive it deterministically.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
