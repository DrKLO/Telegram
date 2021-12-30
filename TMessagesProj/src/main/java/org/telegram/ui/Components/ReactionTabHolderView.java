package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public class ReactionTabHolderView extends FrameLayout {
    private Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Path path = new Path();
    private RectF rect = new RectF();
    private float radius = AndroidUtilities.dp(32);

    private BackupImageView reactView;
    private ImageView iconView;
    private TextView counterView;

    private float outlineProgress;

    public ReactionTabHolderView(@NonNull Context context) {
        super(context);

        iconView = new ImageView(context);
        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.msg_reactions_filled).mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_avatar_nameInMessageBlue), PorterDuff.Mode.MULTIPLY));
        iconView.setImageDrawable(drawable);
        addView(iconView, LayoutHelper.createFrameRelatively(24, 24, Gravity.START | Gravity.CENTER_VERTICAL, 8, 0, 8, 0));

        reactView = new BackupImageView(context);
        addView(reactView, LayoutHelper.createFrameRelatively(24, 24, Gravity.START | Gravity.CENTER_VERTICAL, 8, 0, 8, 0));
        counterView = new TextView(context);
        counterView.setTextColor(Theme.getColor(Theme.key_avatar_nameInMessageBlue));
        counterView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addView(counterView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 40, 0, 8, 0));

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(AndroidUtilities.dp(1));
        outlinePaint.setColor(Theme.getColor(Theme.key_avatar_nameInMessageBlue));

        bgPaint.setColor(Theme.getColor(Theme.key_avatar_nameInMessageBlue));
        bgPaint.setAlpha(0x10);

        View overlaySelectorView = new View(context);
        overlaySelectorView.setBackground(Theme.getSelectorDrawable(bgPaint.getColor(), false));
        addView(overlaySelectorView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        setWillNotDraw(false);
    }

    public void setOutlineProgress(float outlineProgress) {
        this.outlineProgress = outlineProgress;
        invalidate();
    }

    public void setCounter(int count) {
        counterView.setText(String.format("%s", LocaleController.formatShortNumber(count, null)));
        iconView.setVisibility(VISIBLE);
        reactView.setVisibility(GONE);
    }

    public void setCounter(int currentAccount, TLRPC.TL_reactionCount counter) {
        counterView.setText(String.format("%s", LocaleController.formatShortNumber(counter.count, null)));
        String e = counter.reaction;
        for (TLRPC.TL_availableReaction r : MediaDataController.getInstance(currentAccount).getReactionsList()) {
            if (r.reaction.equals(e)) {
                SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(r.static_icon, Theme.key_windowBackgroundGray, 1.0f);
                reactView.setImage(ImageLocation.getForDocument(r.static_icon), "50_50", "webp", svgThumb, r);
                reactView.setVisibility(VISIBLE);
                iconView.setVisibility(GONE);
                break;
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int s = canvas.save();
        path.rewind();
        rect.set(0, 0, getWidth(), getHeight());
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.clipPath(path);

        canvas.drawRoundRect(rect, radius, radius, bgPaint);
        super.dispatchDraw(canvas);

        outlinePaint.setAlpha((int) (outlineProgress * 0xFF));
        float w = outlinePaint.getStrokeWidth();
        rect.set(w, w, getWidth() - w, getHeight() - w);
        canvas.drawRoundRect(rect, radius, radius, outlinePaint);
        canvas.restoreToCount(s);
    }
}