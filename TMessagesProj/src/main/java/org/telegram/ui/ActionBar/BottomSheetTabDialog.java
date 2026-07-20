package org.telegram.ui.ActionBar;

import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

public class BottomSheetTabDialog extends Dialog {

    public static BottomSheetTabsOverlay.Sheet checkSheet(BottomSheetTabsOverlay.Sheet sheet) {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) return sheet;
        if (AndroidUtilities.isTablet() || sheet.hadDialog() || AndroidUtilities.hasDialogOnTop(fragment)) {
            final BottomSheetTabDialog dialog = new BottomSheetTabDialog(sheet);
            if (sheet.setDialog(dialog)) {
                dialog.windowView.putView();
                return sheet;
            }
        }
        return sheet;
    }

    public final BottomSheetTabsOverlay.Sheet sheet;
    public final BottomSheetTabsOverlay.SheetView sheetView;

    public final WindowView windowView;
    public final View navigationBar;
    public final Paint navigationBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BottomSheetTabDialog(BottomSheetTabsOverlay.Sheet sheet) {
        super(sheet.getWindowView().getContext(), R.style.TransparentDialog);

        this.sheet = sheet;
        this.sheetView = sheet.getWindowView();

        navigationBar = new View(getContext()) {
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                canvas.drawRect(0,0,getWidth(),getHeight(),navigationBarPaint);
            }
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.navigationBarHeight);
                setTranslationY(AndroidUtilities.navigationBarHeight);
            }
        };
        navigationBarPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));

        setContentView(windowView = new WindowView(sheetView), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        windowView.addView(navigationBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        windowView.setClipToPadding(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
        window.setWindowAnimations(R.style.DialogNoAnimation);

        WindowManager.LayoutParams params = window.getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.dimAmount = 0;
        params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        window.setAttributes(params);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.setStatusBarColor(Color.TRANSPARENT);
        }

        windowView.setFitsSystemWindows(true);
        windowView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        windowView.setPadding(0, 0, 0, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            windowView.setOnApplyWindowInsetsListener((v, insets) -> {
                v.setPadding(0, 0, 0, insets.getSystemWindowInsetBottom());
                if (Build.VERSION.SDK_INT >= 30) {
                    return WindowInsets.CONSUMED;
                } else {
                    return insets.consumeSystemWindowInsets();
                }
            });
        }
    }

    public void updateNavigationBarColor() {
        final int color = sheet.getNavigationBarColor(Theme.getColor(Theme.key_windowBackgroundGray));
        navigationBarPaint.setColor(color);
        navigationBar.invalidate();
        AndroidUtilities.setNavigationBarColor(this, color);
        AndroidUtilities.setLightNavigationBar(this, AndroidUtilities.computePerceivedBrightness(color) >= .721f);
        LaunchActivity.instance.checkSystemBarColors(true, true, true);
    }

    public static class WindowView extends FrameLayout implements BottomSheetTabsOverlay.SheetView {

        public final BottomSheetTabsOverlay.SheetView sheetView;

        public WindowView(BottomSheetTabsOverlay.SheetView sheetView) {
            super(sheetView.getContext());
            this.sheetView = sheetView;
        }

        public void putView() {
            View view = (View) sheetView;
            AndroidUtilities.removeFromParent(view);
            addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        }

        @Override
        public void setDrawingFromOverlay(boolean value) {
            sheetView.setDrawingFromOverlay(value);
        }

        @Override
        public RectF getRect() {
            return sheetView.getRect();
        }

        @Override
        public float drawInto(Canvas canvas, RectF finalRect, float progress, RectF clipRect, float alpha, boolean opening) {
            return sheetView.drawInto(canvas, finalRect, progress, clipRect, alpha, opening);
        }

    }

    private boolean attached;
    public void attach() {
        if (attached) return;
        attached = true;
        try {
            super.show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void detach() {
        sheet.setDialog(null);
        if (!attached) return;
        attached = false;
        try {
            super.dismiss();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void dismiss() {
        sheet.dismiss(false);
    }

}
