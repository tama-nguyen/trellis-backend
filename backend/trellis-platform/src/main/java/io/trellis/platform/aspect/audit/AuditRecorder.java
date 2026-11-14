package io.trellis.platform.aspect.audit;

/** Persists audit entries. Must join the caller's transaction and never open its own. */
public interface AuditRecorder {
    void record(AuditEntry entry);
}
