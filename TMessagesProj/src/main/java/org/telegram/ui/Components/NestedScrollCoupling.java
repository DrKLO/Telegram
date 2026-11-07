package org.telegram.ui.Components;

/**
 * Pixel accounting for a fullscreen pager in an outer RecyclerView. No Android dependencies.
 * The source RecyclerView performs its own native step between preScroll and postScroll.
 * Host scroll methods are used ONLY for the other RecyclerView, never for the source.
 */
public final class NestedScrollCoupling {
    public interface Host {
        float viewportTop();
        float outerStopTop();
        float sharedStartTop();
        /** Exact near the start; positive infinity is allowed when item 0 is recycled. */
        float innerOffset();
        int virtualOffset();
        void virtualOffset(int offset);
        int scrollOuter(int dy);
        int scrollInner(int dy);
    }

    private final Host host;

    public NestedScrollCoupling(Host host) {
        this.host = host;
    }

    private int distance(float from, float to) {
        return Math.max(0, Math.round(from - to));
    }

    private int collapse(float top) {
        return distance(host.sharedStartTop(), Math.max(host.outerStopTop(), top));
    }

    public int preScroll(boolean sourceIsOuter, int dy) {
        if (dy == 0) {
            return 0;
        }
        if (!sourceIsOuter) {
            if (dy > 0) {
                // Before tabs pin only the outer list consumes the input.
                return host.scrollOuter(Math.min(dy,
                        distance(host.viewportTop(), host.sharedStartTop())));
            }
            // A short page may not have enough real scroll range for the shared interval.
            // Undo that render-only part before giving a reverse step to the source.
            int release = Math.min(-dy, host.virtualOffset());
            if (release > 0) {
                host.virtualOffset(host.virtualOffset() - release);
                host.scrollOuter(-Math.min(release, collapse(host.viewportTop())));
            }
            return -release;
        }
        if (dy > 0) {
            return distance(host.viewportTop(), host.outerStopTop()) == 0
                    ? host.scrollInner(dy) : 0;
        }

        // Keep the shared prefix until the outer list itself starts expanding.
        int shared = collapse(host.viewportTop());
        float offset = host.innerOffset() + host.virtualOffset();
        int request = Float.isInfinite(offset) ? dy
                : -Math.min(-dy, distance(offset, shared));
        int used = host.scrollInner(request);
        if (used < 0) {
            // item 0 may have been recycled before the step. Now its exact offset is known.
            // Correct only the non-source list, synchronously, before returning consumption.
            float after = host.innerOffset() + host.virtualOffset();
            if (!Float.isInfinite(after) && after < shared) {
                used += host.scrollInner(Math.min(-used, distance(shared, after)));
            }
        }
        return used;
    }

    public int postScroll(boolean sourceIsOuter, int dyConsumed, int dyUnconsumed) {
        if (sourceIsOuter) {
            float top = host.viewportTop();
            int sharedDelta = collapse(top) - collapse(top + dyConsumed);
            if (sharedDelta > 0) {
                int real = host.scrollInner(sharedDelta);
                host.virtualOffset(host.virtualOffset() + sharedDelta - real);
            } else if (sharedDelta < 0) {
                int release = Math.min(-sharedDelta, host.virtualOffset());
                host.virtualOffset(host.virtualOffset() - release);
                host.scrollInner(sharedDelta + release);
            }
            // Mirrored sharedDelta is a projection of dyConsumed, NOT extra consumption.
            return dyUnconsumed > 0
                    && distance(host.viewportTop(), host.outerStopTop()) == 0
                    ? host.scrollInner(dyUnconsumed) : 0;
        }

        if (dyConsumed > 0) {
            host.scrollOuter(Math.min(dyConsumed,
                    distance(host.viewportTop(), host.outerStopTop())));
        } else if (dyConsumed < 0) {
            float offset = host.innerOffset() + host.virtualOffset();
            if (!Float.isInfinite(offset)) {
                int expand = Math.min(-dyConsumed, distance(collapse(host.viewportTop()), offset));
                host.scrollOuter(-expand);
            }
        }
        if (dyUnconsumed > 0) {
            int used = host.scrollOuter(Math.min(dyUnconsumed,
                    distance(host.viewportTop(), host.outerStopTop())));
            host.virtualOffset(host.virtualOffset() + used);
            return used;
        }
        if (dyUnconsumed < 0) {
            return host.scrollOuter(dyUnconsumed);
        }
        return 0;
    }
}
