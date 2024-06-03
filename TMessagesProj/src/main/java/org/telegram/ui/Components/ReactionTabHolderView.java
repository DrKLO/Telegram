package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;

public class ReactionTabHolderView extends FrameLayout {
    private Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Path path = new Path();
    private RectF rect = new RectF();
    private float radius = AndroidUtilities.dp(32);

    private BackupImageView reactView;
    private ImageView iconView;
    private TextView counterView;
    View overlaySelectorView;
    private float outlineProgress;
    Drawable drawable;

    private int count;
    private ReactionsLayoutInBubble.VisibleReaction reaction;

    public ReactionTabHolderView(@NonNull Context context) {
        super(context);

        overlaySelectorView = new View(context);
        addView(overlaySelectorView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        iconView = new ImageView(context);
        drawable = ContextCompat.getDrawable(context, R.drawable.msg_reactions_filled).mutate();
        iconView.setImageDrawable(drawable);
        addView(iconView, LayoutHelper.createFrameRelatively(24, 24, Gravity.START | Gravity.CENTER_VERTICAL, 8, 0, 8, 0));

        reactView = new BackupImageView(context);
        addView(reactView, LayoutHelper.createFrameRelatively(24, 24, Gravity.START | Gravity.CENTER_VERTICAL, 8, 0, 8, 0));

        counterView = new TextView(context);
        counterView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        counterView.setTextColor(Theme.getColor(Theme.key_avatar_nameInMessageBlue));
        counterView.setTypeface(AndroidUtilities.bold());
        addView(counterView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 40, 0, 8, 0));

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(AndroidUtilities.dp(1));

        setWillNotDraw(false);

        setOutlineProgress(outlineProgress);
    }

    public void setOutlineProgress(float outlineProgress) {
        this.outlineProgress = outlineProgress;
        int backgroundSelectedColor = Theme.getColor(Theme.key_chat_inReactionButtonBackground);
        int backgroundColor = ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_chat_inReactionButtonBackground), 0x10);

        int textSelectedColor = Theme.getColor(Theme.key_chat_inReactionButtonTextSelected);
        int textColor = Theme.getColor(Theme.key_chat_inReactionButtonText);
        int textFinalColor = ColorUtils.blendARGB(textColor, textSelectedColor, outlineProgress);

        bgPaint.setColor(ColorUtils.blendARGB(backgroundColor, backgroundSelectedColor, outlineProgress));
        counterView.setTextColor(textFinalColor);
        drawable.setColorFilter(new PorterDuffColorFilter(textFinalColor, PorterDuff.Mode.MULTIPLY));

        if (outlineProgress == 1f) {
            overlaySelectorView.setBackground(Theme.createSimpleSelectorRoundRectDrawable((int) radius, Color.TRANSPARENT, ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_chat_inReactionButtonTextSelected), (int) (0.3f * 255))));
        } else if (outlineProgress == 0) {
            overlaySelectorView.setBackground(Theme.createSimpleSelectorRoundRectDrawable((int) radius, Color.TRANSPARENT, ColorUtils.setAlphaComponent(backgroundSelectedColor, (int) (0.3f * 255))));
        }
        invalidate();
    }

    public void setCounter(int count) {
        this.count = count;
        counterView.setText(String.format("%s", LocaleController.formatShortNumber(count, null)));
        iconView.setVisibility(VISIBLE);
        reactView.setVisibility(GONE);
    }

    public void setCounter(int currentAccount, TLRPC.ReactionCount counter) {
        this.count = counter.count;
        counterView.setText(String.format("%s", LocaleController.formatShortNumber(counter.count, null)));
        ReactionsLayoutInBubble.VisibleReaction counterReaction = ReactionsLayoutInBubble.VisibleReaction.fromTL(counter.reaction);
        reaction = counterReaction;
        if (reaction.emojicon != null) {
            for (TLRPC.TL_availableReaction r : MediaDataController.getInstance(currentAccount).getReactionsList()) {
                if (r.reaction.equals(reaction.emojicon)) {
                    SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(r.static_icon, Theme.key_windowBackgroundGray, 1.0f);
                    reactView.setImage(ImageLocation.getForDocument(r.center_icon), "40_40_lastreactframe", "webp", svgThumb, r);
                    reactView.setVisibility(VISIBLE);
                    iconView.setVisibility(GONE);
                    break;
                }
            }
        } else {
            reactView.setAnimatedEmojiDrawable(new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, currentAccount, reaction.documentId));
            reactView.setVisibility(VISIBLE);
            iconView.setVisibility(GONE);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        rect.set(0, 0, getWidth(), getHeight());

        canvas.drawRoundRect(rect, radius, radius, bgPaint);
        super.dispatchDraw(canvas);

//        outlinePaint.setAlpha((int) (outlineProgress * 0xFF));
//        float w = outlinePaint.getStrokeWidth();
//        rect.set(w, w, getWidth() - w, getHeight() - w);
//        canvas.drawRoundRect(rect, radius, radius, outlinePaint);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
        info.setClickable(true);
        if (outlineProgress > .5) {
            info.setSelected(true);
        }
        if (reaction != null) {
            info.setText(LocaleController.formatPluralString("AccDescrNumberOfPeopleReactions", count, reaction));
        } else {
            info.setText(LocaleController.formatPluralString("ReactionsCount", count));
        }
    }
}