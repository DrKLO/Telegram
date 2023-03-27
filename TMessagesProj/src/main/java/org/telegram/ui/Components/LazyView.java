package org.telegram.ui.Components;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class LazyView {
    private View view;
    private int visibility = GONE;

    @NonNull
    public View makeView() {
        return null;
    }

    @Nullable
    public View view() {
        return view;
    }

    @NonNull
    public View forceView() {
        if (view == null) {
            view = makeView();
        }
        return view;
    }

    public void setVisibility(int visibility) {
        this.visibility = visibility;
        if (visibility == VISIBLE && view == null) {
            view = makeView();
        }
        if (view != null) {
            view.setVisibility(visibility);
        }
    }

    public int getVisibility() {
        return visibility;
    }
}
