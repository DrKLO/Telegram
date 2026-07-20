package me.vkryl.core;

import java.util.ArrayList;
import java.util.Collections;

public class ArrayUtils {
    public static <T extends Comparable<T>> boolean removeSorted(ArrayList<T> list, T element) {
        int index = Collections.binarySearch(list, element);
        if (index >= 0) {
            list.remove(index);
            return true;
        } else {
            return false;
        }
    }

    public static <T extends Comparable<T>> int addSorted(ArrayList<T> list, T element) {
        int index = Collections.binarySearch(list, element);
        if (index >= 0) {
            throw new IllegalArgumentException("Element already exists in list");
        }
        int at = -index - 1;
        list.add(at, element);
        return at;
    }
}
