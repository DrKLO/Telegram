package org.telegram.messenger;

public class SegmentTree {

    private Node[] heap;
    private long[] array;

    public SegmentTree(long[] array) {
        this.array = array;
        if (array.length < 30) {
            return;
        }
        //The max size of this array is about 2 * 2 ^ log2(n) + 1
        int size = (int) (2 * Math.pow(2.0, Math.floor((Math.log(array.length) / Math.log(2.0)) + 1)));
        heap = new Node[size];
        build(1, 0, array.length);
    }

    private void build(int v, int from, int size) {
        heap[v] = new Node();
        heap[v].from = from;
        heap[v].to = from + size - 1;

        if (size == 1) {
            heap[v].sum = array[from];
            heap[v].max = array[from];
            heap[v].min = array[from];
        } else {
            //Build childs
            build(2 * v, from, size / 2);
            build(2 * v + 1, from + size / 2, size - size / 2);

            heap[v].sum = heap[2 * v].sum + heap[2 * v + 1].sum;
            //max = max of the children
            heap[v].max = Math.max(heap[2 * v].max, heap[2 * v + 1].max);
            heap[v].min = Math.min(heap[2 * v].min, heap[2 * v + 1].min);
        }
    }

    public long rMaxQ(int from, int to) {
        if (array.length < 30) {
            long max = Long.MIN_VALUE;
            if (from < 0) from = 0;
            if (to > array.length - 1) to = array.length - 1;
            for (int i = from; i <= to; i++) {
                if (array[i] > max) max = array[i];
            }
            return max;
        }
        return rMaxQ(1, from, to);
    }

    private long rMaxQ(int v, int from, int to) {
        Node n = heap[v];
        //If you did a range update that contained this node, you can infer the Min value without going down the tree
        if (n.pendingVal != null && contains(n.from, n.to, from, to)) {
            return n.pendingVal;
        }

        if (contains(from, to, n.from, n.to)) {
            return heap[v].max;
        }

        if (intersects(from, to, n.from, n.to)) {
            propagate(v);
            final long leftMin = rMaxQ(2 * v, from, to);
            final long rightMin = rMaxQ(2 * v + 1, from, to);

            return Math.max(leftMin, rightMin);
        }

        return 0;
    }

    public long rMinQ(int from, int to) {
        if (array.length < 30) {
            long min = Long.MAX_VALUE;
            if (from < 0) from = 0;
            if (to > array.length - 1) to = array.length - 1;
            for (int i = from; i <= to; i++) {
                if (array[i] < min) min = array[i];
            }
            return min;
        }
        return rMinQ(1, from, to);
    }

    private long rMinQ(int v, int from, int to) {
        Node n = heap[v];
        //If you did a range update that contained this node, you can infer the Min value without going down the tree
        if (n.pendingVal != null && contains(n.from, n.to, from, to)) {
            return n.pendingVal;
        }

        if (contains(from, to, n.from, n.to)) {
            return heap[v].min;
        }

        if (intersects(from, to, n.from, n.to)) {
            propagate(v);
            long leftMin = rMinQ(2 * v, from, to);
            long rightMin = rMinQ(2 * v + 1, from, to);

            return Math.min(leftMin, rightMin);
        }

        return Integer.MAX_VALUE;
    }

    private void propagate(int v) {
        Node n = heap[v];

        if (n.pendingVal != null) {
            change(heap[2 * v], n.pendingVal);
            change(heap[2 * v + 1], n.pendingVal);
            n.pendingVal = null;
        }
    }

    private void change(Node n, int value) {
        n.pendingVal = value;
        n.sum = n.size() * value;
        n.max = value;
        n.min = value;
        array[n.from] = value;

    }

    private boolean contains(int from1, int to1, int from2, int to2) {
        return from2 >= from1 && to2 <= to1;
    }

    private boolean intersects(int from1, int to1, int from2, int to2) {
        return from1 <= from2 && to1 >= from2   //  (.[..)..] or (.[...]..)
                || from1 >= from2 && from1 <= to2; // [.(..]..) or [..(..)..
    }

    static class Node {
        long sum;
        long max;
        long min;

        Integer pendingVal = null;
        int from;
        int to;

        int size() {
            return to - from + 1;
        }
    }
}