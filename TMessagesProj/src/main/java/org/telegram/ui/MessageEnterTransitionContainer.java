package org.telegram.ui;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.NotificationCenter;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class MessageEnterTransitionContainer extends View {

    private ArrayList<Transition> transitions = new ArrayList<>();
    private final int currentAccount;
    private final ViewGroup parent;

    Runnable hideRunnable = () -> setVisibility(View.GONE);

    public MessageEnterTransitionContainer(ViewGroup parent, int currentAccount) {
        super(parent.getContext());
        this.parent = parent;
        this.currentAccount = currentAccount;
    }

    public interface Transition {
        void onDraw(Canvas canvas);
    }

    void addTransition(Transition transition) {
        transitions.add(transition);
        checkVisibility();
        parent.invalidate();
    }

    void removeTransition(Transition transition) {
        transitions.remove(transition);
        checkVisibility();
        parent.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (transitions.isEmpty()) {
            return;
        }
        for (int i = 0; i < transitions.size(); i++) {
            transitions.get(i).onDraw(canvas);
        }
    }

    private void checkVisibility() {
        if (transitions.isEmpty() && getVisibility() != View.GONE) {
            NotificationCenter.getInstance(currentAccount).removeDelayed(hideRunnable);
            NotificationCenter.getInstance(currentAccount).doOnIdle(hideRunnable);
        } else if (!transitions.isEmpty() && getVisibility() != View.VISIBLE) {
            NotificationCenter.getInstance(currentAccount).removeDelayed(hideRunnable);
            setVisibility(View.VISIBLE);
        }
    }

    public boolean isRunning() {
        return transitions.size() > 0;
    }
}
