package org.telegram.ui.Stars;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class BagRandomizer<T> {

    private final List<T> bag;
    private final List<T> shuffledBag;
    private int currentIndex;
    private final Random random;
    private boolean reshuffleIfEnd = true;

    private T next;

    public BagRandomizer(List<T> items) {
        if (items == null) items = new ArrayList<>();
        this.bag = new ArrayList<>(items);
        this.shuffledBag = new ArrayList<>(this.bag);
        this.currentIndex = 0;
        this.random = new Random();
        reshuffle();
        next();
    }

    @Nullable
    public T next() {
        if (this.bag.isEmpty()) return null;
        T result = next;
        if (currentIndex >= shuffledBag.size()) {
            if (reshuffleIfEnd) {
                reshuffle();
            } else {
                currentIndex = 0;
            }
        }
        next = shuffledBag.get(currentIndex++);
        return result;
    }

    public void setReshuffleIfEnd(boolean reshuffleIfEnd) {
        this.reshuffleIfEnd = reshuffleIfEnd;
    }

    @Nullable
    public T getNext() {
        return next;
    }

    public void reset() {
        currentIndex = 0;
        next();
    }

    private void reshuffle() {
        Collections.shuffle(shuffledBag, random);
        currentIndex = 0;
    }

}
