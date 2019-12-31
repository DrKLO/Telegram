package org.telegram.ui.Components;

public class IntSize {

    public int width;
    public int height;

    public IntSize() {
    }

    public IntSize(IntSize size) {
        this.width = size.width;
        this.height = size.height;
    }

    public IntSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void set(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IntSize intSize = (IntSize) o;

        if (width != intSize.width) return false;
        return height == intSize.height;
    }

    @Override
    public int hashCode() {
        int result = width;
        result = 31 * result + height;
        return result;
    }

    @Override
    public String toString() {
        return "IntSize(" + width + ", " + height + ")";
    }
}
