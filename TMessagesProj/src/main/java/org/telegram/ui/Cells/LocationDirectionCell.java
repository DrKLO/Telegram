package org.telegram.ui.Cells;

import org.telegram.messenger.FileLog;
import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.BadWayToMakeButtonRound;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

@SuppressWarnings("FieldCanBeLocal")
public class LocationDirectionCell extends FrameLayout implements Theme.Colorable {

    private final Theme.ResourcesProvider resourcesProvider;
    private SimpleTextView buttonTextView;
    private FrameLayout frameLayout;

    public LocationDirectionCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        frameLayout = new FrameLayout(context);
        frameLayout.setBackground(Theme.AdaptiveRipple.filledRect(getThemedColor(Theme.key_featuredStickers_addButton), 8));
        addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP, 16, 10, 16, 0));

        buttonTextView = new SimpleTextView(context);
        buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setDrawablePadding(AndroidUtilities.dp(8));
        ThemeBind.textColor(buttonTextView, Theme.key_featuredStickers_buttonText, resourcesProvider);
        buttonTextView.setTextSize(14);
        buttonTextView.setText(LocaleController.getString(R.string.Directions));
        buttonTextView.setLeftDrawable(R.drawable.filled_directions);
        buttonTextView.setTypeface(AndroidUtilities.bold());
        frameLayout.addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        BadWayToMakeButtonRound.round(frameLayout);
        ScaleStateListAnimator.apply(frameLayout, 0.02f, 1.2f);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(73), MeasureSpec.EXACTLY));
    }

    public void setOnButtonClick(OnClickListener onButtonClick) {
        frameLayout.setOnClickListener(onButtonClick);
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private int lastButtonColor;
    private boolean lastButtonColorDark;

    @Override
    public void updateColors() {
        try {
            final int color = getThemedColor(Theme.key_featuredStickers_addButton);
            if (frameLayout != null && (color != lastButtonColor || lastButtonColorDark != Theme.isCurrentThemeDark())) {
                lastButtonColorDark = Theme.isCurrentThemeDark(); // the ripple shade depends on dark / light
                lastButtonColor = color;
                // recolor in place (content + adaptive ripple) instead of a new drawable per frame
                Theme.setSelectorDrawableColor(frameLayout.getBackground(), color, false);
                Theme.setSelectorDrawableColor(frameLayout.getBackground(), Theme.AdaptiveRipple.calcRippleColor(color), true);
                frameLayout.invalidate();
            }
            invalidate();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
