package org.telegram.utils.pip;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Splits a rectangle into a mosaic of tiles for several videos. Pure geometry, no Android.
 *
 * <p>A layout is a set of rows of tiles (or columns: the same thing transposed). Each video
 * fills its tile with a center crop. Two extremes exist for every split into rows:
 * <ul>
 *   <li>"justified": tile widths follow the videos' aspects and row heights follow the rows'
 *       total aspect, so nothing is cropped, but tiles in short rows get much larger;</li>
 *   <li>"equal": every row gets a height proportional to its number of tiles and equal widths,
 *       so every tile has exactly the same area, but videos are cropped.</li>
 * </ul>
 * Row heights are searched between the two ({@link #BLEND_STEPS} steps in log space); inside a
 * row widths follow the aspects only within {@link #FLEX} of an equal share. The layout with the
 * smallest cost wins:
 * <ul>
 *   <li>the share of each video that is cropped away, {@code crop = 1 - min(a, c) / max(a, c)}
 *       for a video of aspect {@code a} in a tile of aspect {@code c}, as
 *       {@code crop + 1.5 crop²}: one badly cropped face is worse than a little crop everywhere;</li>
 *   <li>tile areas: the largest one at most {@link #AREA_RATIO_LIMIT} times the smallest one
 *       (a practically hard constraint), and a small {@code ln²(area / mean)} inside it;</li>
 *   <li>tiles without a known aspect (an avatar) slightly prefer squares;</li>
 *   <li>a free container (the PiP window) slightly prefers to be close to a square;</li>
 *   <li>stability: tiles moved away from their previous order, a changed container aspect.</li>
 * </ul>
 * Searched: every split of the tiles into rows (all of them up to {@link #MAX_FULL_ENUMERATION}
 * tiles, balanced splits beyond that), rows and columns, the previous order and the order sorted
 * by aspect (similar videos share a row). For a free container the aspect is the best one for
 * each candidate (clamped to the range), or the previous one.
 */
public final class MosaicLayout {
    /** Aspect of a tile with no video (or not known yet). */
    public static final float UNKNOWN = 0f;

    /** Max ratio between a tile's width and an equal share of its row: sqrt of the area limit. */
    static final float FLEX = 1.22f;
    /** "Roughly equal": the largest tile is at most this much larger than the smallest one. */
    static final float AREA_RATIO_LIMIT = 1.5f;
    /** Beyond the limit, per ln of the excess: practically a hard constraint. */
    static final float AREA_LIMIT_WEIGHT = 8f;
    /** Inside the limit: equal areas are still a bit better. */
    static final float AREA_WEIGHT = 0.3f;
    static final float UNKNOWN_WEIGHT = 0.3f;
    static final float ORDER_WEIGHT = 0.05f;
    static final float ASPECT_CHANGE_WEIGHT = 0.08f;
    static final float COMPACT_WEIGHT = 0.4f;
    /** Cropping one tile a lot is worse than cropping all of them a little. */
    static final float CROP_SQUARE_WEIGHT = 1.5f;
    static final int BLEND_STEPS = 8;
    static final int MAX_FULL_ENUMERATION = 8;

    public static final class Result {
        /** Container width / height. */
        public float aspect;
        /** Per input item: left, top, right, bottom in 0..1 of the container. */
        public float[] rects;
        /** Input indices in the visual order (row by row, or column by column). */
        public int[] order;
        public boolean columns;
        public int[] counts;
        public float cost;

        @Override
        public String toString() {
            return (columns ? "columns " : "rows ") + Arrays.toString(counts) + " aspect=" + aspect + " cost=" + cost + " order=" + Arrays.toString(order);
        }
    }

    private MosaicLayout() {}

    public static Result layout(float[] aspects, float minAspect, float maxAspect, int[] previousRank, float previousAspect) {
        return layout(aspects, null, minAspect, maxAspect, previousRank, previousAspect);
    }

    /**
     * @param aspects         width / height of each video, {@link #UNKNOWN} for none
     * @param cropWeights     how bad cropping is for each video (1 for a camera, more for a shared
     *                        screen, where text is lost); null for all 1
     * @param minAspect       container aspect range; equal values for a fixed container
     * @param previousRank    per item its position in the previous visual order, -1 for a new item;
     *                        null for no previous layout
     * @param previousAspect  container aspect of the previous layout, 0 for none
     */
    public static Result layout(float[] aspects, float[] cropWeights, float minAspect, float maxAspect, int[] previousRank, float previousAspect) {
        final int n = aspects.length;
        final Result best = new Result();
        if (n == 0) {
            best.aspect = clamp(previousAspect > 0 ? previousAspect : 1f, minAspect, maxAspect);
            best.rects = new float[0];
            best.order = new int[0];
            best.counts = new int[0];
            return best;
        }
        best.cost = Float.MAX_VALUE;

        final int[] stable = stableOrder(previousRank, n);
        final int[][] orders = candidateOrders(aspects, stable);
        final int[][] compositions = compositions(n);
        final float[] inverted = new float[n];
        for (int i = 0; i < n; i++) {
            inverted[i] = aspects[i] > 0 ? 1f / aspects[i] : UNKNOWN;
        }

        final float[] weights = new float[n];
        for (int i = 0; i < n; i++) {
            weights[i] = cropWeights != null ? cropWeights[i] : 1f;
        }
        final Evaluator rows = new Evaluator(aspects, weights, minAspect, maxAspect, previousAspect);
        final Evaluator columns = new Evaluator(inverted, weights, 1f / maxAspect, 1f / minAspect, previousAspect > 0 ? 1f / previousAspect : 0);
        for (int[] order : orders) {
            final float orderCost = ORDER_WEIGHT * movedCount(order, stable);
            for (int[] counts : compositions) {
                rows.evaluate(order, counts, orderCost, best, false);
                if (counts.length > 1) {
                    columns.evaluate(order, counts, orderCost, best, true);
                }
            }
        }
        return best;
    }

    /** Rows in a container of aspect C x 1 (columns: the same with inverted aspects). */
    private static final class Evaluator {
        final float[] aspects;
        final float[] weights;
        final float minAspect, maxAspect, previousAspect;
        final boolean free;

        final float[] share;      // per tile (by position): width share of its row
        final float[] rowNatural; // per row: height that crops nothing, for a container of width 1
        final float[] rowHeight;
        final float[] candidates = new float[3];

        Evaluator(float[] aspects, float[] weights, float minAspect, float maxAspect, float previousAspect) {
            this.aspects = aspects;
            this.weights = weights;
            this.minAspect = minAspect;
            this.maxAspect = maxAspect;
            this.previousAspect = previousAspect;
            this.free = maxAspect > minAspect;
            share = new float[aspects.length];
            rowNatural = new float[aspects.length];
            rowHeight = new float[aspects.length];
        }

        void evaluate(int[] order, int[] counts, float baseCost, Result best, boolean transposed) {
            final int n = order.length;
            final int rows = counts.length;
            // width shares inside rows and the natural row heights
            float naturalSum = 0;
            for (int r = 0, index = 0; r < rows; index += counts[r], r++) {
                final float rowAspect = rowShares(order, index, counts[r]);
                rowNatural[r] = 1f / rowAspect;
                naturalSum += rowNatural[r];
            }
            for (int step = 0; step <= BLEND_STEPS; step++) {
                final float t = (float) step / BLEND_STEPS;
                float sum = 0;
                for (int r = 0; r < rows; r++) {
                    final float natural = rowNatural[r] / naturalSum;
                    final float equal = (float) counts[r] / n;
                    rowHeight[r] = (float) (Math.pow(natural, 1 - t) * Math.pow(equal, t));
                    sum += rowHeight[r];
                }
                for (int r = 0; r < rows; r++) {
                    rowHeight[r] /= sum;
                }
                int candidateCount = 0;
                if (free) {
                    candidates[candidateCount++] = clamp(bestContainerAspect(order, counts), minAspect, maxAspect);
                    if (previousAspect >= minAspect && previousAspect <= maxAspect) {
                        candidates[candidateCount++] = previousAspect;
                    }
                } else {
                    candidates[candidateCount++] = minAspect;
                }
                for (int c = 0; c < candidateCount; c++) {
                    final float containerAspect = candidates[c];
                    final float cost = baseCost + containerCost(containerAspect) + tilesCost(order, counts, containerAspect);
                    if (cost < best.cost - 1e-5f) {
                        save(best, cost, order, counts, containerAspect, transposed);
                    }
                }
            }
        }

        /** Width shares of one row's tiles; returns the row's aspect when nothing is cropped (height 1). */
        private float rowShares(int[] order, int from, int count) {
            double logSum = 0;
            int known = 0;
            for (int j = 0; j < count; j++) {
                final float a = aspects[order[from + j]];
                if (a > 0) {
                    logSum += Math.log(a);
                    known++;
                }
            }
            final float mean = known > 0 ? (float) Math.exp(logSum / known) : 1f;
            float sum = 0;
            for (int j = 0; j < count; j++) {
                final float a = aspects[order[from + j]];
                final float weight = a > 0 ? clamp(a / mean, 1f / FLEX, FLEX) : 1f;
                share[from + j] = weight;
                sum += weight;
            }
            for (int j = 0; j < count; j++) {
                share[from + j] /= sum;
            }
            return mean * sum;
        }

        /** Container aspect that balances cropping of the current row heights (geometric mean). */
        private float bestContainerAspect(int[] order, int[] counts) {
            double logSum = 0, weightSum = 0;
            int index = 0;
            for (int r = 0; r < counts.length; r++) {
                for (int j = 0; j < counts[r]; j++, index++) {
                    final float a = aspects[order[index]];
                    // tile aspect = C * share / height; weighted by how bad cropping is
                    final float weight = a > 0 ? weights[order[index]] : UNKNOWN_WEIGHT;
                    logSum += weight * Math.log((a > 0 ? a : 1f) * rowHeight[r] / share[index]);
                    weightSum += weight;
                }
            }
            return (float) Math.exp(logSum / weightSum);
        }

        private float containerCost(float containerAspect) {
            float cost = 0;
            final float logAspect = (float) Math.log(containerAspect);
            if (previousAspect > 0) {
                cost += ASPECT_CHANGE_WEIGHT * Math.abs(logAspect - (float) Math.log(previousAspect));
            }
            if (free) {
                cost += COMPACT_WEIGHT * logAspect * logAspect;
            }
            return cost;
        }

        private float tilesCost(int[] order, int[] counts, float containerAspect) {
            final int n = order.length;
            float cost = 0;
            float minArea = Float.MAX_VALUE, maxArea = 0;
            int index = 0;
            for (int r = 0; r < counts.length; r++) {
                final float height = rowHeight[r];
                for (int j = 0; j < counts[r]; j++, index++) {
                    final float area = share[index] * height;
                    minArea = Math.min(minArea, area);
                    maxArea = Math.max(maxArea, area);
                    final float a = aspects[order[index]];
                    final float c = containerAspect * share[index] / height;
                    if (a > 0) {
                        final float crop = 1f - Math.min(a, c) / Math.max(a, c);
                        cost += weights[order[index]] * (crop + CROP_SQUARE_WEIGHT * crop * crop);
                    } else {
                        cost += UNKNOWN_WEIGHT * (1f - Math.min(1f, c) / Math.max(1f, c));
                    }
                    final float deviation = (float) Math.log(area * n);
                    cost += AREA_WEIGHT * deviation * deviation;
                }
            }
            final float excess = (float) Math.log(maxArea / minArea / AREA_RATIO_LIMIT);
            if (excess > 0) {
                cost += AREA_LIMIT_WEIGHT * excess;
            }
            return cost;
        }

        private void save(Result best, float cost, int[] order, int[] counts, float containerAspect, boolean transposed) {
            final int n = order.length;
            best.cost = cost;
            best.columns = transposed;
            best.order = order.clone();
            best.counts = counts.clone();
            best.aspect = transposed ? 1f / containerAspect : containerAspect;
            best.rects = new float[n * 4];
            int index = 0;
            float top = 0;
            for (int r = 0; r < counts.length; r++) {
                final float bottom = r == counts.length - 1 ? 1f : top + rowHeight[r];
                float left = 0;
                for (int j = 0; j < counts[r]; j++, index++) {
                    final float right = j == counts[r] - 1 ? 1f : left + share[index];
                    final int item = order[index];
                    if (transposed) {
                        best.rects[item * 4] = top;
                        best.rects[item * 4 + 1] = left;
                        best.rects[item * 4 + 2] = bottom;
                        best.rects[item * 4 + 3] = right;
                    } else {
                        best.rects[item * 4] = left;
                        best.rects[item * 4 + 1] = top;
                        best.rects[item * 4 + 2] = right;
                        best.rects[item * 4 + 3] = bottom;
                    }
                    left = right;
                }
                top = bottom;
            }
        }
    }

    /** Old items in their previous order, then new ones in input order. */
    private static int[] stableOrder(int[] previousRank, int n) {
        final Integer[] items = new Integer[n];
        for (int i = 0; i < n; i++) {
            items[i] = i;
        }
        if (previousRank != null) {
            Arrays.sort(items, (a, b) -> {
                final int ra = previousRank[a] < 0 ? Integer.MAX_VALUE : previousRank[a];
                final int rb = previousRank[b] < 0 ? Integer.MAX_VALUE : previousRank[b];
                return ra != rb ? Integer.compare(ra, rb) : Integer.compare(a, b);
            });
        }
        final int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = items[i];
        }
        return order;
    }

    /** The stable order and the stable order sorted by aspect, both ways (unknown counts as square). */
    private static int[][] candidateOrders(float[] aspects, int[] stable) {
        final int n = stable.length;
        if (n <= 1) {
            return new int[][]{stable};
        }
        final Integer[] items = new Integer[n];
        for (int i = 0; i < n; i++) {
            items[i] = stable[i];
        }
        Arrays.sort(items, (a, b) -> Float.compare(logAspect(aspects[a]), logAspect(aspects[b])));
        final int[] ascending = new int[n];
        final int[] descending = new int[n];
        for (int i = 0; i < n; i++) {
            ascending[i] = items[i];
            descending[n - 1 - i] = items[i];
        }
        if (Arrays.equals(ascending, stable)) {
            return new int[][]{stable, descending};
        } else if (Arrays.equals(descending, stable)) {
            return new int[][]{stable, ascending};
        }
        return new int[][]{stable, ascending, descending};
    }

    private static float logAspect(float a) {
        return a > 0 ? (float) Math.log(a) : 0f;
    }

    private static int movedCount(int[] order, int[] stable) {
        int moved = 0;
        for (int i = 0; i < order.length; i++) {
            if (order[i] != stable[i]) {
                moved++;
            }
        }
        return moved;
    }

    /** Tile counts per row: every composition of n for small n, balanced ones otherwise. */
    static int[][] compositions(int n) {
        if (n <= MAX_FULL_ENUMERATION) {
            final int total = 1 << (n - 1);
            final int[][] result = new int[total][];
            for (int mask = 0; mask < total; mask++) {
                final int[] counts = new int[Integer.bitCount(mask) + 1];
                int row = 0, count = 1;
                for (int bit = 0; bit < n - 1; bit++) {
                    if ((mask & (1 << bit)) != 0) {
                        counts[row++] = count;
                        count = 1;
                    } else {
                        count++;
                    }
                }
                counts[row] = count;
                result[mask] = counts;
            }
            return result;
        }
        final ArrayList<int[]> result = new ArrayList<>();
        for (int rows = 1; rows <= n; rows++) {
            final int base = n / rows, rest = n % rows;
            final int[] first = new int[rows];
            final int[] last = new int[rows];
            for (int r = 0; r < rows; r++) {
                first[r] = base + (r < rest ? 1 : 0);
                last[r] = base + (r >= rows - rest ? 1 : 0);
            }
            result.add(first);
            if (rest != 0) {
                result.add(last);
            }
        }
        return result.toArray(new int[0][]);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
