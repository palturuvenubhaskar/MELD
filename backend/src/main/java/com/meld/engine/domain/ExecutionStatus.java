package com.meld.engine.domain;

/**
 * Execution lifecycle states per PRD.md §9 and ARCHITECTURE.md §8.
 * MVP states: PENDING → RUNNING → COMPLETED
 * Other MVP transitions: PENDING → CANCELLED, RUNNING → FAILED, RUNNING → CANCELLED
 */
public enum ExecutionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    WAITING_FOR_APPROVAL // P1
}