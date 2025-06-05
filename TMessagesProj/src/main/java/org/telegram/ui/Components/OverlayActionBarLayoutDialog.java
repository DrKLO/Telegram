package org.telegram.ui.Components;

import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

public class OverlayActionBarLayoutDialog extends Dialog implements INavigationLayout.INavigationLayoutDelegate {
    private Theme.ResourcesProvider resourcesProvider;
    private INavigationLayout actionBarLayout;
    private FrameLayout frameLayout;
    private PasscodeView passcodeView;

    public OverlayActionBarLayoutDialog(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, R.style.TransparentDialog);
        this.resourcesProvider = resourcesProvider;

        actionBarLayout = INavigationLayout.newLayout(context, false);
        actionBarLayout.setFragmentStack(new ArrayList<>());
        actionBarLayout.presentFragment(new INavigationLayout.NavigationParams(new EmptyFragment()).setNoAnimation(true));
        actionBarLayout.setDelegate(this);

        frameLayout = new FrameLayout(context);
        frameLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frameLayout.addView(actionBarLayout.getView(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        if (AndroidUtilities.isTablet() && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isSmallTablet()) {
            frameLayout.setBackgroundColor(0x99000000);
            frameLayout.setOnClickListener(v -> onBackPressed());
            actionBarLayout.setRemoveActionBarExtraHeight(true);
            VerticalPositionAutoAnimator.attach(actionBarLayout.getView());
        }
        passcodeView = new PasscodeView(context);
        frameLayout.addView(passcodeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        setContentView(frameLayout);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Context context = getContext();
        if (context instanceof ContextWrapper && !(context instanceof LaunchActivity)) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        if (context instanceof LaunchActivity) {
            ((LaunchActivity) context).addOverlayPasscodeView(passcodeView);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        Context context = getContext();
        if (context instanceof ContextWrapper && !(context instanceof LaunchActivity)) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        if (context instanceof LaunchActivity) {
            ((LaunchActivity) context).removeOverlayPasscodeView(passcodeView);
        }
    }

    @Override
    public void onMeasureOverride(int[] measureSpec) {
        if (AndroidUtilities.isTablet() && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isSmallTablet()) {
            measureSpec[0] = View.MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(530), View.MeasureSpec.getSize(measureSpec[0])), View.MeasureSpec.EXACTLY);
            measureSpec[1] = View.MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(528), View.MeasureSpec.getSize(measureSpec[1])), View.MeasureSpec.EXACTLY);
        }
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

        frameLayout.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            frameLayout.setOnApplyWindowInsetsListener((v, insets) -> {
                v.setPadding(0, 0, 0, insets.getSystemWindowInsetBottom());
                return insets;
            });
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int color = Theme.getColor(Theme.key_windowBackgroundWhite, null, true);
            AndroidUtilities.setLightNavigationBar(window, ColorUtils.calculateLuminance(color) >= 0.9);
        }
    }

    public void addFragment(BaseFragment fragment) {
        actionBarLayout.presentFragment(fragment, AndroidUtilities.isTablet() && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isSmallTablet());
    }

    @Override
    public void onBackPressed() {
        if (passcodeView.getVisibility() == View.VISIBLE) {
            if (getOwnerActivity() != null) {
                getOwnerActivity().finish();
            }
            return;
        }
        
        actionBarLayout.onBackPressed();
        if (actionBarLayout.getFragmentStack().size() <= 1) {
            dismiss();
        }
    }

    @Override
    public boolean onPreIme() {
        return false;
    }

    @Override
    public boolean needPresentFragment(BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation, INavigationLayout layout) {
        return true;
    }

    @Override
    public boolean needAddFragmentToStack(BaseFragment fragment, INavigationLayout layout) {
        return true;
    }

    @Override
    public boolean needCloseLastFragment(INavigationLayout layout) {
        if (layout.getFragmentStack().size() <= 1) {
            dismiss();
        }
        return true;
    }

    @Override
    public void onRebuildAllFragments(INavigationLayout layout, boolean last) {}

    private final class EmptyFragment extends BaseFragment {
        @Override
        public View createView(Context context) {
            hasOwnBackground = true;
            actionBar.setAddToContainer(false);
            View v = new View(context);
            v.setBackgroundColor(Color.TRANSPARENT);
            return v;
        }

        @Override
        public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
            if (isOpen && backward) {
                dismiss();
            }
        }
    }
}
