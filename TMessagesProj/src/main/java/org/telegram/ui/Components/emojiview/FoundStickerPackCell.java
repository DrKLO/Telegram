package org.telegram.ui.Components.emojiview;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class FoundStickerPackCell extends FrameLayout implements FactorAnimator.Target, Theme.Colorable {
    private Drawable bgSelected;
    private final Theme.ResourcesProvider resourcesProvider;
    private final StickerEmojiCell stickerView;
    private final TextView textView;

    private final BoolAnimator isSelected = new BoolAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, 380);

    public FoundStickerPackCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        stickerView = new StickerEmojiCell(context, false, resourcesProvider);
        addView(stickerView, LayoutHelper.createFrame(45, 45, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 8, 0, 0));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
        textView.setGravity(Gravity.CENTER);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setSingleLine();
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 6, 0, 6, 5));

        updateColors();
    }

    public void setPack(TLRPC.TL_messages_stickerSet pack) {
        textView.setText(pack.set.short_name);
        stickerView.setSticker(!pack.documents.isEmpty() ? pack.documents.get(0) : null, null, null, null, false);
    }

    public void setPack(TLRPC.StickerSetCovered pack, TLRPC.Document document) {
        textView.setText(pack.set.short_name);
        stickerView.setSticker(document, null, null, null, false);
    }

    public boolean isSelected() {
        return isSelected.getValue();
    }

    public void setSelected(boolean isSelected, boolean animated) {
        if (isSelected && bgSelected == null) {
            bgSelected = Theme.createRoundRectDrawable(dp(10), ColorUtils.setAlphaComponent(
                Theme.getColor(Theme.key_glass_defaultIcon, resourcesProvider),
                (int) (255 * 0.1f)));
        }

        if (this.isSelected.getValue() == isSelected && !animated) {
            return;
        }
        this.isSelected.setValue(isSelected, animated);
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (bgSelected != null) {
            bgSelected.setAlpha((int) (255 * factor));
        }
        invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (bgSelected != null && isSelected.getFloatValue() > 0) {
            bgSelected.setBounds(0, 0, getWidth(), getHeight());
            DrawableUtils.drawWithScale(canvas, bgSelected, lerp(0.9f, 1, isSelected.getFloatValue()));
        }
        super.dispatchDraw(canvas);
    }

    @Override
    public void updateColors() {
        if (bgSelected != null) {
            bgSelected = Theme.createRoundRectDrawable(dp(10), ColorUtils.setAlphaComponent(
                Theme.getColor(Theme.key_glass_defaultIcon, resourcesProvider),
                (int) (255 * 0.1f)));
            bgSelected.setAlpha((int) (255 * isSelected.getFloatValue()));
        }
        textView.setTextColor(ColorUtils.setAlphaComponent(
            Theme.getColor(Theme.key_glass_defaultIcon, resourcesProvider),
            (int) (255 * 0.9f)));
    }
}
