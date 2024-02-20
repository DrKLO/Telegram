package org.telegram.ui.Components.Premium.boosts.cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CombinedDrawable;

@SuppressLint("ViewConstructor")
public class AddChannelCell extends FrameLayout {

    private final SimpleTextView textView;
    private final ImageView imageView;
    private final Theme.ResourcesProvider resourcesProvider;

    public AddChannelCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        textView = new SimpleTextView(context);
        textView.setTextSize(16);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
        textView.setTag(Theme.key_windowBackgroundWhiteBlueHeader);
        addView(textView);

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(imageView);

        textView.setText(LocaleController.getString("BoostingAddChannelOrGroup", R.string.BoostingAddChannelOrGroup));

        Drawable drawable1 = getResources().getDrawable(R.drawable.poll_add_circle);
        Drawable drawable2 = getResources().getDrawable(R.drawable.poll_add_plus);
        drawable1.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_switchTrackChecked, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        drawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_checkboxCheck, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        CombinedDrawable combinedDrawable = new CombinedDrawable(drawable1, drawable2);
        imageView.setImageDrawable(combinedDrawable);
        setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        textView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(71 + 23), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
        imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
        setMeasuredDimension(width, AndroidUtilities.dp(50));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int height = bottom - top;
        int width = right - left;

        int viewLeft;
        int viewTop = (height - textView.getTextHeight()) / 2;
        if (LocaleController.isRTL) {
            viewLeft = getMeasuredWidth() - textView.getMeasuredWidth() - AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? 68 : 23);
        } else {
            viewLeft = AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? 68 : 23);
        }
        textView.layout(viewLeft, viewTop, viewLeft + textView.getMeasuredWidth(), viewTop + textView.getMeasuredHeight());

        viewLeft = !LocaleController.isRTL ? AndroidUtilities.dp(24) : width - imageView.getMeasuredWidth() - AndroidUtilities.dp(24);
        imageView.layout(viewLeft, 0, viewLeft + imageView.getMeasuredWidth(), imageView.getMeasuredHeight());
    }
}
