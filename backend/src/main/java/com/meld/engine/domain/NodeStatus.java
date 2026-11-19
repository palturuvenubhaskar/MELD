package com.meld.engine.domain;

/**
 * Per-node execution states per PRD.md §9, ARCHITECTURE.md §8, DATABASE.md §5.
 * OUTCOME_UNKNOWN is used when a worker cannot confirm whether an external action completed.
 * This state is not eligible for automatic retry; an operator must resolve it first.
 */
public enum NodeStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    OUTCOME_UNKNOWN,
    WAITING_FOR_APPROVAL // P1
}