package org.telegram.messenger.postdrawcompat;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.R;

import java.util.ArrayList;

public final class ViewOnPostDrawListener {

    private ViewOnPostDrawListener() {
    }

    public static void addListener(View view, OnPostDrawListener listener) {
        if (view == null || listener == null) {
            return;
        }

        if (view.isAttachedToWindow() && view == view.getRootView()) {
            throw new IllegalArgumentException("Cannot add OnPostDrawListener to root view");
        }

        ViewState state = getViewState(view);
        if (state == null) {
            state = new ViewState();

            view.setTag(R.id.tag_view_on_post_draw_state, state);
            view.addOnAttachStateChangeListener(state.attachStateChangeListener);
        }

        if (state.listeners.contains(listener)) {
            return;
        }

        state.listeners.add(listener);

        if (view.isAttachedToWindow()) {
            RootState rootState = attachToRoot(view, state);
            if (rootState != null) {
                rootState.viewOnPostDraw.addOnPostDrawListener(listener);
            }
        }
    }

    public static void removeListener(View view, OnPostDrawListener listener) {
        if (view == null || listener == null) {
            return;
        }

        ViewState state = getViewState(view);
        if (state == null || !state.listeners.remove(listener)) {
            return;
        }

        if (state.rootState != null) {
            state.rootState.viewOnPostDraw.removeOnPostDrawListener(listener);
        }

        if (state.listeners.isEmpty()) {
            state.rootState = null;

            view.removeOnAttachStateChangeListener(state.attachStateChangeListener);
            view.setTag(R.id.tag_view_on_post_draw_state, null);
        }
    }

    private static RootState attachToRoot(View view, ViewState state) {
        if (state.rootState != null) {
            return state.rootState;
        }

        View rootView = view.getRootView();

        if (view == rootView) {
            throw new IllegalArgumentException("Cannot add OnPostDrawListener to root view");
        }

        if (!(rootView instanceof ViewGroup)) {
            return null;
        }

        ViewGroup root = (ViewGroup) rootView;

        RootState rootState = getRootState(root);
        if (rootState == null) {
            ViewOnPostDraw viewOnPostDraw = new ViewOnPostDraw(root.getContext());

            rootState = new RootState(viewOnPostDraw);

            root.setTag(R.id.tag_view_on_post_draw_root_state, rootState);

            if (root instanceof FrameLayout) {
                root.addView(viewOnPostDraw, new FrameLayout.LayoutParams(1, 1, Gravity.CENTER));
            } else {
                root.addView(viewOnPostDraw, new ViewGroup.LayoutParams(1, 1));
            }
        }

        state.rootState = rootState;
        return rootState;
    }

    private static void attachAllListeners(View view, ViewState state) {
        RootState rootState = attachToRoot(view, state);
        if (rootState == null) {
            return;
        }

        for (OnPostDrawListener listener : state.listeners) {
            rootState.viewOnPostDraw.addOnPostDrawListener(listener);
        }
    }

    private static void detachAllListeners(ViewState state) {
        RootState rootState = state.rootState;
        if (rootState == null) {
            return;
        }

        for (OnPostDrawListener listener : state.listeners) {
            rootState.viewOnPostDraw.removeOnPostDrawListener(listener);
        }

        state.rootState = null;
    }

    private static ViewState getViewState(View view) {
        return (ViewState) view.getTag(R.id.tag_view_on_post_draw_state);
    }

    private static RootState getRootState(ViewGroup root) {
        return (RootState) root.getTag(R.id.tag_view_on_post_draw_root_state);
    }

    private static final class ViewState {

        final ArrayList<OnPostDrawListener> listeners = new ArrayList<>();

        RootState rootState;

        final View.OnAttachStateChangeListener attachStateChangeListener =
                new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        ViewState state = getViewState(v);
                        if (state != null) {
                            attachAllListeners(v, state);
                        }
                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        ViewState state = getViewState(v);
                        if (state != null) {
                            detachAllListeners(state);
                        }
                    }
                };
    }

    private static final class RootState {

        final ViewOnPostDraw viewOnPostDraw;

        RootState(ViewOnPostDraw viewOnPostDraw) {
            this.viewOnPostDraw = viewOnPostDraw;
        }
    }
}