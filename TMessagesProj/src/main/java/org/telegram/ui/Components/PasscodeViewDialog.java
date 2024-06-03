package org.telegram.ui.Components;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MessageSendPreview;

public class PasscodeViewDialog extends Dialog {

    public final Context context;

    private final FrameLayout windowView;
    public final PasscodeView passcodeView;

    public PasscodeViewDialog(@NonNull Context context) {
        super(context, R.style.TransparentDialog);
        this.context = context;

        windowView = new FrameLayout(context);
        if (Build.VERSION.SDK_INT >= 21) {
            windowView.setFitsSystemWindows(true);
            windowView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                    if (Build.VERSION.SDK_INT >= 30) {
                        return WindowInsets.CONSUMED;
                    } else {
                        return insets.consumeSystemWindowInsets();
                    }
                }
            });
        }

        passcodeView = new PasscodeView(context) {
            @Override
            protected void onHidden() {
                PasscodeViewDialog.super.dismiss();
                if (LaunchActivity.instance == null) return;
                DrawerLayoutContainer drawerLayoutContainer = LaunchActivity.instance.drawerLayoutContainer;
                drawerLayoutContainer.setScaleX(1f);
                drawerLayoutContainer.setScaleY(1f);
            }

            @Override
            protected void onAnimationUpdate(float open) {
                if (LaunchActivity.instance == null) return;
                DrawerLayoutContainer drawerLayoutContainer = LaunchActivity.instance.drawerLayoutContainer;
                drawerLayoutContainer.setScaleX(AndroidUtilities.lerp(1f, 1.25f, open));
                drawerLayoutContainer.setScaleY(AndroidUtilities.lerp(1f, 1.25f, open));
            }
        };
        windowView.addView(passcodeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setWindowAnimations(R.style.DialogNoAnimation);
        setContentView(windowView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WindowManager.LayoutParams params = window.getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.FILL;
        params.dimAmount = 0;
        params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
//        params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        if (!BuildVars.DEBUG_PRIVATE_VERSION) {
            params.flags |= WindowManager.LayoutParams.FLAG_SECURE;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION;
        }
        params.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
        params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        window.setAttributes(params);

        windowView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_VISIBLE);

        AndroidUtilities.setLightNavigationBar(window, false);
    }

    @Override
    public void onBackPressed() {
        if (passcodeView.onBackPressed()) {
            if (LaunchActivity.instance != null) {
                LaunchActivity.instance.moveTaskToBack(true);
            }
        }
    }

    @Override
    public void dismiss() {
        if (passcodeView.onBackPressed()) {
            if (LaunchActivity.instance != null) {
                LaunchActivity.instance.moveTaskToBack(true);
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if (passcodeView.onBackPressed()) {
                if (LaunchActivity.instance != null) {
                    LaunchActivity.instance.moveTaskToBack(true);
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
