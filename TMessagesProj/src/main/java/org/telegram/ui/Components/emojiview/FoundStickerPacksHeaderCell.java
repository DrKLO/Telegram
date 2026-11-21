package org.telegram.ui.Components.emojiview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import java.util.HashSet;

@SuppressLint("ViewConstructor")
public class FoundStickerPacksHeaderCell extends FrameLayout implements Theme.Colorable {

    private final Theme.ResourcesProvider resourcesProvider;
    private final ImageView backButton;
    private final TextView headerText;

    public FoundStickerPacksHeaderCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        backButton = new ImageView(context);
        backButton.setScaleType(ImageView.ScaleType.CENTER);
        backButton.setImageResource(R.drawable.msg_arrow_back);
        addView(backButton, LayoutHelper.createFrame(48, 48, Gravity.START | Gravity.CENTER_VERTICAL, 6, 0, 0, 0));

        headerText = new TextView(context);
        headerText.setText(LocaleController.getString( R.string.EmojiSearchBackToSearch));
        headerText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        headerText.setTypeface(AndroidUtilities.bold());
        headerText.setSingleLine(true);
        headerText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        addView(headerText, LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
            Gravity.START | Gravity.CENTER_VERTICAL,
            50, 0, 16, 0
        ));

        updateColors();
    }

    public void setOnBackClickListener(OnClickListener listener) {
        backButton.setOnClickListener(listener);
    }

    private int lastGlassIconColor;
    private boolean glassColorsApplied;

    @Override
    public void updateColors() {
        headerText.setTextColor(getGlassIconColor(0.6f));
        final int glassColor = getGlassIconColor(1f);
        if (glassColorsApplied && glassColor == lastGlassIconColor) {
            return; // theme frames: nothing changed, no new filter / drawable
        }
        glassColorsApplied = true;
        lastGlassIconColor = glassColor;
        backButton.setColorFilter(new PorterDuffColorFilter(getGlassIconColor(0.6f), PorterDuff.Mode.MULTIPLY));
        if (backButton.getBackground() != null) {
            Theme.setSelectorDrawableColor(backButton.getBackground(), getGlassIconColor(0.1f), true);
        } else {
            backButton.setBackground(Theme.createSelectorDrawable(getGlassIconColor(0.1f), 1));
        }
    }
    @Override
    public void addColorKeys(HashSet<Integer> colors) {
        colors.add(Theme.key_glass_defaultIcon);
    }

    private int getGlassIconColor(float alpha) {
        return ColorUtils.setAlphaComponent(
                Theme.getColor(Theme.key_glass_defaultIcon, resourcesProvider),
                (int) (255 * alpha));
    }
}