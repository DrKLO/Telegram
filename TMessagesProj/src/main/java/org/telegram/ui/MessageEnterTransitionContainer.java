package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.telegram.messenger.NotificationCenter;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class MessageEnterTransitionContainer extends View {

    private ArrayList<Transition> transitions = new ArrayList<>();
    private final int currentAccount;

    Runnable hideRunnable = () -> setVisibility(View.GONE);

    public MessageEnterTransitionContainer(Context context, int currentAccount) {
        super(context);
        this.currentAccount = currentAccount;
    }

    public interface Transition {
        void onDraw(Canvas canvas);
    }

    void addTransition(Transition transition) {
        transitions.add(transition);
        checkVisibility();
    }

    void removeTransition(Transition transition) {
        transitions.remove(transition);
        checkVisibility();
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
}
