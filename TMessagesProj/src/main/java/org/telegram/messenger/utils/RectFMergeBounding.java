package org.telegram.messenger.utils;

import android.graphics.RectF;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RectFMergeBounding {

    private static final float EPS = 1e-4f;

    /**
     * Merges overlapping rectangles from positions[0..count-1] into bounding boxes,
     * stores them in 'output' and sorts the result by:
     *   1) top (ascending)
     *   2) left (ascending if top is equal)
     *
     * 'positions' is not modified.
     * 'output' is used as a reusable buffer and may be larger than the result.
     *
     * @param positions source rectangles (input only)
     * @param count     number of valid items in 'positions' to process
     * @param output    buffer for output rectangles (will be reused/extended)
     *
     * @return number of resulting rectangles; valid ones are output[0 .. resultCount-1]
     */
    public static int mergeOverlapping(
            List<RectF> positions,
            int count,
            List<RectF> output
    ) {
        if (positions == null || count <= 0) {
            return 0;
        }
        if (count > positions.size()) {
            count = positions.size();
        }

        // Ensure 'output' has at least 'count' RectF instances
        for (int i = output.size(); i < count; i++) {
            output.add(new RectF());
        }

        // Copy positions → output[0..count-1]
        for (int i = 0; i < count; i++) {
            RectF src = positions.get(i);
            RectF dst = output.get(i);
            if (src != null) {
                dst.set(src);
            } else {
                dst.set(0f, 0f, 0f, 0f);
            }
        }

        int activeCount = count;

        // Greedy merging with restart
        boolean merged;
        do {
            merged = false;

            outer:
            for (int i = 0; i < activeCount; i++) {
                RectF a = output.get(i);

                for (int j = i + 1; j < activeCount; j++) {
                    RectF b = output.get(j);

                    if (intersectsOrTouches(a, b)) {
                        // a = union(a, b)
                        if (b.left   < a.left)   a.left   = b.left;
                        if (b.top    < a.top)    a.top    = b.top;
                        if (b.right  > a.right)  a.right  = b.right;
                        if (b.bottom > a.bottom) a.bottom = b.bottom;

                        // Remove b by moving last active into its place
                        int last = activeCount - 1;
                        if (j != last) {
                            output.get(j).set(output.get(last));
                        }
                        activeCount--;

                        merged = true;
                        // restart outer loop
                        break outer;
                    }
                }
            }
        } while (merged);

        // Mark unused rectangles so they go to the end after sorting
        for (int i = activeCount; i < output.size(); i++) {
            RectF r = output.get(i);
            r.top = Float.MAX_VALUE;
            r.left = Float.MAX_VALUE;
            // right/bottom don't matter for sorting
        }

        // Sort entire output list; valid ones (with smallest top/left)
        // will end up at [0 .. activeCount-1]
        Collections.sort(output, RECT_COMPARATOR);

        return activeCount;
    }

    /**
     * Intersection test (touching edges counts as overlapping).
     */
    private static boolean intersectsOrTouches(RectF a, RectF b) {
        return a.left   <= b.right  + EPS &&
                a.right  >= b.left   - EPS &&
                a.top    <= b.bottom + EPS &&
                a.bottom >= b.top    - EPS;
    }

    private static final Comparator<RectF> RECT_COMPARATOR = (a, b) -> {
        // Compare by top (y)
        if (Math.abs(a.top - b.top) > EPS) {
            return (a.top < b.top) ? -1 : 1;
        }
        // If top is (almost) equal, compare by left (x)
        if (Math.abs(a.left - b.left) > EPS) {
            return (a.left < b.left) ? -1 : 1;
        }
        return 0;
    };
}
