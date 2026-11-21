/* Telegram for Android. GPL v2 or later. */
package org.telegram.ui.Components;

/** Android-independent scroll accounting and geometry used by the actual coordinator. */
public final class RecyclerPagerModel {
    private RecyclerPagerModel() {}

    /** Scroll operations used by the pure model; positive dy moves content towards its end. */
    public interface ScrollHost {
        /** Returns nonnegative pixels until the boundary reaches the tabs, or positive infinity. */
        float distanceToTabs();
        /** Scrolls the common list and returns actual signed consumption, bounded by dy. */
        int scrollCommon(int dy);
        /** Scrolls the current page and returns actual signed consumption, bounded by dy. */
        int scrollPage(int dy);
    }

    /**
     * Routes distance before native source scrolling and returns signed parent consumption.
     * commonSource identifies the source; the host must never scroll that same list recursively.
     */
    public static int preScroll(ScrollHost host, boolean commonSource, int dy) {
        if (commonSource) {
            // Only the OTHER RecyclerView is scrolled from a nested callback.
            boolean pageConsumesFirst = dy < 0 || host.distanceToTabs() <= 0.5f;
            if (pageConsumesFirst) {
                return host.scrollPage(dy);
            }
            return 0;
        }
        if (dy > 0) {
            float distanceToPin = host.distanceToTabs();
            int commonRequest = Float.isInfinite(distanceToPin)
                    ? dy : Math.min(dy, Math.max(0, Math.round(distanceToPin)));
            return host.scrollCommon(commonRequest);
        }
        return 0;
    }

    /** Routes the remainder after source scrolling; sourceConsumed excludes pre-scroll consumption. */
    public static int postScroll(ScrollHost host, boolean commonSource, int sourceConsumed, int unconsumed) {
        // If common consumed nothing, preScroll already tried the page. Do not retry its edge.
        if (commonSource && sourceConsumed > 0 && unconsumed > 0) {
            return host.scrollPage(unconsumed);
        }
        if (!commonSource && unconsumed < 0) {
            return host.scrollCommon(unconsumed);
        }
        return 0;
    }

    /** Returns common-header collapse in pixels, limited by actual page scrolling and hide geometry. */
    public static float collapse(float pageOffset, float pinTop, float hiddenTop) {
        float maximumCollapse = Math.max(0f, pinTop - hiddenTop);
        return Math.max(0f, Math.min(pageOffset, maximumCollapse));
    }

    /** Returns downward page displacement while the common boundary is below the tabs' pin. */
    public static float pageTranslation(float boundaryTop, float pinTop) {
        return Math.max(0f, boundaryTop - pinTop);
    }

    /**
     * Reusable presentation of one page switch; it never scrolls a list. The coordinator restores
     * the current page's resting geometry after commit or cancellation. All distances are pixels.
     */
    public static final class Transition {
        private float sourceBoundary;
        private float sourceTail;
        private float targetTail;
        private float pin;
        private float progress;
        private boolean sourceHidden;

        /** Captures source geometry and resets progress; an expanded header keeps its current height. */
        public void begin(float boundaryTop, float pageOffset, float pinTop, float hiddenTop) {
            sourceBoundary = boundaryTop;
            pin = pinTop;
            sourceTail = boundaryTop - collapse(pageOffset, pinTop, hiddenTop);
            // Switching pages never collapses an expanded common header.
            targetTail = Math.max(boundaryTop, pinTop);
            sourceHidden = sourceTail <= hiddenTop + 0.5f;
            progress = 0f;
        }

        /** Sets finite progress, clamped to [0, 1]; decreasing values support gesture cancellation. */
        public void setProgress(float value) {
            progress = Math.max(0f, Math.min(1f, value));
        }
        /** Returns current normalized progress. */
        public float getProgress() {
            return progress;
        }
        /** Reports whether the common content was fully hidden when begin() was called. */
        public boolean wasCommonHidden() {
            return sourceHidden;
        }
        /** Returns the common content's drawn bottom in root coordinates. */
        public float commonTail() {
            return sourceHidden ? targetTail : sourceTail + (targetTail - sourceTail) * progress;
        }
        /** Returns the tabs' top in root coordinates, never above the pin. */
        public float tabsTop() {
            return Math.max(pin, commonTail());
        }
        /** One shared layer. Only a previously hidden header enters with the incoming page. */
        public float commonTranslationX(float incomingPageX) {
            return sourceHidden ? incomingPageX : 0f;
        }
        /** Returns the incoming list's vertical translation relative to its padded resting position. */
        public float incomingPageTranslation() {
            return tabsTop() - pin;
        }
        /** Returns the source list's vertical translation while its native scroll position stays intact. */
        public float outgoingPageTranslation() {
            return pageTranslation(sourceBoundary, pin) + (sourceHidden ? 0f : commonTail() - sourceTail);
        }
    }
}
