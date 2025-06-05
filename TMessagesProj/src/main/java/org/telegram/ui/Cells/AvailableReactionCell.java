package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Reactions.ReactionsUtils;
import org.telegram.ui.Components.Switch;

public class AvailableReactionCell extends FrameLayout {
    private SimpleTextView textView;
    private BackupImageView imageView;
    private Switch switchView;
    private CheckBox2 checkBox;
    private View overlaySelectorView;
    public TLRPC.TL_availableReaction react;
    private boolean canLock;
    public boolean locked;

    public AvailableReactionCell(@NonNull Context context, boolean checkbox, boolean canLock) {
        super(context);
        this.canLock = canLock;

        textView = new SimpleTextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(16);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setMaxLines(1);
        textView.setMaxLines(1);
        textView.setGravity(LayoutHelper.getAbsoluteGravityStart() | Gravity.CENTER_VERTICAL);
        addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 81, 0, 61, 0));

        imageView = new BackupImageView(context);
        imageView.setAspectFit(true);
        imageView.setLayerNum(1);
        addView(imageView, LayoutHelper.createFrameRelatively(32, 32, Gravity.START | Gravity.CENTER_VERTICAL, 23, 0, 0, 0));

        if (checkbox) {
            checkBox = new CheckBox2(context, 26, null);
            checkBox.setDrawUnchecked(false);
            checkBox.setColor(-1, -1, Theme.key_radioBackgroundChecked);
            checkBox.setDrawBackgroundAsArc(-1);
            addView(checkBox, LayoutHelper.createFrameRelatively(26, 26, Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 22, 0));
        } else {
            switchView = new Switch(context);
            switchView.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
            addView(switchView, LayoutHelper.createFrameRelatively(37, 20, Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 22, 0));
        }
        overlaySelectorView = new View(context);
        overlaySelectorView.setBackground(Theme.getSelectorDrawable(false));
        addView(overlaySelectorView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (AndroidUtilities.dp(58) + Theme.dividerPaint.getStrokeWidth()), MeasureSpec.EXACTLY));
    }

    /**
     * Binds reaction to the view
     * @param react Reaction to bind
     * @param checked If view should be checked
     */
    public void bind(TLRPC.TL_availableReaction react, boolean checked, int currentAccount) {
        boolean animated = false;
        if (react != null && this.react != null && react.reaction.equals(this.react.reaction)) {
            animated = true;
        }
        this.react = react;
        textView.setText(react.title);
        SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(react.static_icon, Theme.key_windowBackgroundGray, 1.0f);
        imageView.setImage(ImageLocation.getForDocument(react.activate_animation), ReactionsUtils.ACTIVATE_ANIMATION_FILTER, "tgs", svgThumb, react);

        locked = canLock && react.premium && !UserConfig.getInstance(currentAccount).isPremium();
        if (locked) {
            Drawable drawable = ContextCompat.getDrawable(getContext(), R.drawable.other_lockedfolders2);
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_stickers_menu), PorterDuff.Mode.MULTIPLY));
            textView.setRightDrawable(drawable);

        } else {
            textView.setRightDrawable(null);
        }

        setChecked(checked, animated);
    }

    /**
     * Sets view checked
     * @param checked If checked or not
     */
    public void setChecked(boolean checked) {
        setChecked(checked, false);
    }

    /**
     * Sets view checked
     * @param checked If checked or not
     * @param animated If we should animate change
     */
    public void setChecked(boolean checked, boolean animated) {
        if (switchView != null) {
            switchView.setChecked(checked, animated);
        }
        if (checkBox != null) {
            checkBox.setChecked(checked, animated);
        }
    }

    public boolean isChecked() {
        if (switchView != null) {
            return switchView.isChecked();
        }
        if (checkBox != null) {
            return checkBox.isChecked();
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        float w = Theme.dividerPaint.getStrokeWidth();
        int l = 0, r = 0;
        int pad = AndroidUtilities.dp(81);
        if (LocaleController.isRTL) {
            r = pad;
        } else {
            l = pad;
        }

        canvas.drawLine(getPaddingLeft() + l, getHeight() - w, getWidth() - getPaddingRight() - r, getHeight() - w, Theme.dividerPaint);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setEnabled(true);
        info.setClickable(true);
        if (switchView != null) {
            info.setCheckable(true);
            info.setChecked(isChecked());
            info.setClassName("android.widget.Switch");
        } else if (isChecked()) {
            info.setSelected(true);
        }
        info.setContentDescription(textView.getText());
    }
}