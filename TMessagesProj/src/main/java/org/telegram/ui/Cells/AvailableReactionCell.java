package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

public class AvailableReactionCell extends FrameLayout {
    private TextView textView;
    private BackupImageView imageView;
    private Switch switchView;
    private CheckBox2 checkBox;
    private View overlaySelectorView;
    public TLRPC.TL_availableReaction react;

    public AvailableReactionCell(@NonNull Context context, boolean checkbox) {
        super(context);

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity(LayoutHelper.getAbsoluteGravityStart() | Gravity.CENTER_VERTICAL);
        addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 81, 0, 91, 0));

        imageView = new BackupImageView(context);
        imageView.setAspectFit(true);
        imageView.setLayerNum(1);
        addView(imageView, LayoutHelper.createFrameRelatively(32, 32, Gravity.START | Gravity.CENTER_VERTICAL, 23, 0, 0, 0));

        if (checkbox) {
            checkBox = new CheckBox2(context, 26, null);
            checkBox.setDrawUnchecked(false);
            checkBox.setColor(null, null, Theme.key_radioBackgroundChecked);
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
    public void bind(TLRPC.TL_availableReaction react, boolean checked) {
        boolean animated = false;
        if (react != null && this.react != null && react.reaction.equals(this.react.reaction)) {
            animated = true;
        }
        this.react = react;
        textView.setText(react.title);
        SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(react.static_icon, Theme.key_windowBackgroundGray, 1.0f);
        imageView.setImage(ImageLocation.getForDocument(react.static_icon), "50_50", "webp", svgThumb, react);
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
}