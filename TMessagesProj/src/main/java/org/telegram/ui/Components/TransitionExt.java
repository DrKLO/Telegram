package org.telegram.ui.Components;

import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionSet;

public class TransitionExt {

    public static Transition createSimpleTransition() {
        TransitionSet transition = new TransitionSet();
        ChangeBounds changeBounds = new ChangeBounds();
        changeBounds.setDuration(150);
        transition.addTransition(new Fade().setDuration(150)).addTransition(changeBounds);
        transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
        transition.setInterpolator(CubicBezierInterpolator.DEFAULT);
        return transition;
    }
}
