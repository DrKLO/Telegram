package org.telegram.utils.glass.positions;

import android.graphics.RectF;

import androidx.annotation.NonNull;

import java.util.ArrayList;

public class GlassPositionsArray {

    private final ArrayList<RectF> positions;
    private int size;

    public GlassPositionsArray() {
        positions = new ArrayList<>();
    }

    public GlassPositionsArray(int initialCapacity) {
        positions = new ArrayList<>(initialCapacity);
        for (int a = 0; a < initialCapacity; a++) {
            positions.add(new RectF());
        }
    }

    @NonNull
    public RectF add(@NonNull RectF rect) {
        return add(rect.left, rect.top, rect.right, rect.bottom);
    }

    @NonNull
    public RectF add(float left, float top, float right, float bottom) {
        final RectF result;

        if (size < positions.size()) {
            result = positions.get(size);
            result.set(left, top, right, bottom);
        } else {
            result = new RectF(left, top, right, bottom);
            positions.add(result);
        }

        size++;
        return result;
    }

    @NonNull
    public RectF get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(
                    "index=" + index + ", size=" + size
            );
        }
        return positions.get(index);
    }

    public void clear() {
        size = 0;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int capacity() {
        return positions.size();
    }

    public void removeUnordered(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(
                    "index=" + index + ", size=" + size
            );
        }

        final int last = size - 1;
        final RectF removed = positions.get(index);

        if (index != last) {
            positions.set(index, positions.get(last));
            positions.set(last, removed);
        }

        size = last;
    }

    public void outset(float dx, float dy) {
        int i = 0;

        while (i < size) {
            final RectF rect = positions.get(i);
            rect.inset(-dx, -dy);

            if (rect.isEmpty()) {
                removeUnordered(i);
            } else {
                i++;
            }
        }
    }

    public void clip(float left, float top, float right, float bottom) {
        int i = 0;

        while (i < size) {
            final RectF rect = positions.get(i);

            if (rect.right <= left ||
                    rect.bottom <= top ||
                    rect.left >= right ||
                    rect.top >= bottom) {
                removeUnordered(i);
                continue;
            }

            if (rect.left < left) {
                rect.left = left;
            }
            if (rect.top < top) {
                rect.top = top;
            }
            if (rect.right > right) {
                rect.right = right;
            }
            if (rect.bottom > bottom) {
                rect.bottom = bottom;
            }

            i++;
        }
    }

    public void set(GlassPositionsArray other) {
        if (this == other) {
            return;
        }

        clear();
        final int count = other.size;
        for (int i = 0; i < count; i++) {
            add(other.positions.get(i));
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof GlassPositionsArray)) {
            return false;
        }

        GlassPositionsArray other = (GlassPositionsArray) obj;
        if (size != other.size) {
            return false;
        }

        for (int i = 0; i < size; i++) {
            if (!positions.get(i).equals(other.positions.get(i))) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = 1;

        for (int i = 0; i < size; i++) {
            result = 31 * result + positions.get(i).hashCode();
        }

        return result;
    }
}