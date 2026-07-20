package org.telegram.messenger.utils;

import android.view.View;
import android.view.Window;

import java.lang.ref.WeakReference;

public class WindowVisibilityManager {

    private int reasonsToHide;
    private boolean isHidden;
    private final OnVisibilityChangedListener listener;

    public WindowVisibilityManager(Window window) {
        final WeakReference<Window> ref = new WeakReference<>(window);
        this.listener = isVisible -> {
            final Window w = ref.get();
            if (w != null) {
                w.getDecorView().setVisibility(isVisible ? View.VISIBLE : View.GONE);
            }
        };
    }

    public WindowVisibilityManager(OnVisibilityChangedListener listener) {
        this.listener = listener;
    }

    private void setIsHidden(boolean isHidden) {
        if (this.isHidden != isHidden) {
            this.isHidden = isHidden;
            listener.onVisibilityChanged(!isHidden);
        }
    }


    public interface OnVisibilityChangedListener {
        void onVisibilityChanged(boolean isVisible);
    }


    public Controller obtainController() {
        return new ControllerImpl();
    }

    private class ControllerImpl implements Controller {
        private boolean hidden;
        private boolean destroyed;

        @Override
        public void setHidden(boolean hidden) {
            if (this.hidden != hidden && !destroyed) {
                this.hidden = hidden;

                if (hidden) {
                    reasonsToHide++;
                } else {
                    reasonsToHide--;
                }

                setIsHidden(reasonsToHide > 0);
            }
        }

        @Override
        public void destroy() {
            setHidden(false);
            destroyed = true;
        }
    }

    public interface Controller {
        void setHidden(boolean hidden);
        void destroy();
    }
}
