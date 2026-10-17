package org.telegram.ui.Stars;

/** A monotonic deadline shared by paid reactions and the offline demonstration. */
public final class PaidReactionUndo {
    public static final long DURATION_MS = 20_000;
    private long deadline;
    private boolean started;
    private boolean finished;

    public void restart(long now) {
        if (finished) return;
        started = true;
        deadline = now + DURATION_MS;
    }

    public long remaining(long now) {
        return finished ? 0 : started ? Math.max(0, deadline - now) : DURATION_MS;
    }

    public boolean commit(long now) {
        if (!started || finished || remaining(now) > 0) return false;
        finished = true;
        return true;
    }

    public boolean cancel() {
        if (finished) return false;
        finished = true;
        return true;
    }
}
