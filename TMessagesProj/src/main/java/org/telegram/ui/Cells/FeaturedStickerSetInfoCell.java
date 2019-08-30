/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColorSpanUnderline;
import org.telegram.ui.Components.LayoutHelper;

public class FeaturedStickerSetInfoCell extends FrameLayout {

    private TextView nameTextView;
    private TextView infoTextView;
    private TextView addButton;
    private TLRPC.StickerSetCovered set;
    private Drawable addDrawable;
    private Drawable delDrawable;

    private boolean drawProgress;
    private float progressAlpha;
    private RectF rect = new RectF();
    private long lastUpdateTime;
    private Paint botProgressPaint;
    private int angle;
    private boolean isInstalled;
    private boolean hasOnClick;
    private boolean isUnread;

    private int currentAccount = UserConfig.selectedAccount;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FeaturedStickerSetInfoCell(Context context, int left) {
        super(context);

        delDrawable = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_delButton), Theme.getColor(Theme.key_featuredStickers_delButtonPressed));
        addDrawable = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed));

        botProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        botProgressPaint.setColor(Theme.getColor(Theme.key_featuredStickers_buttonProgress));
        botProgressPaint.setStrokeCap(Paint.Cap.ROUND);
        botProgressPaint.setStyle(Paint.Style.STROKE);
        botProgressPaint.setStrokeWidth(AndroidUtilities.dp(2));

        nameTextView = new TextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_chat_emojiPanelTrendingTitle));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setSingleLine(true);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, left, 8, 40, 0));

        infoTextView = new TextView(context);
        infoTextView.setTextColor(Theme.getColor(Theme.key_chat_emojiPanelTrendingDescription));
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        infoTextView.setEllipsize(TextUtils.TruncateAt.END);
        infoTextView.setSingleLine(true);
        addView(infoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, left, 30, 100, 0));

        addButton = new TextView(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (drawProgress || !drawProgress && progressAlpha != 0) {
                    botProgressPaint.setAlpha(Math.min(255, (int) (progressAlpha * 255)));
                    int x = getMeasuredWidth() - AndroidUtilities.dp(11);
                    rect.set(x, AndroidUtilities.dp(3), x + AndroidUtilities.dp(8), AndroidUtilities.dp(8 + 3));
                    canvas.drawArc(rect, angle, 220, false, botProgressPaint);
                    invalidate((int) rect.left - AndroidUtilities.dp(2), (int) rect.top - AndroidUtilities.dp(2), (int) rect.right + AndroidUtilities.dp(2), (int) rect.bottom + AndroidUtilities.dp(2));
                    long newTime = System.currentTimeMillis();
                    if (Math.abs(lastUpdateTime - System.currentTimeMillis()) < 1000) {
                        long delta = (newTime - lastUpdateTime);
                        float dt = 360 * delta / 2000.0f;
                        angle += dt;
                        angle -= 360 * (angle / 360);
                        if (drawProgress) {
                            if (progressAlpha < 1.0f) {
                                progressAlpha += delta / 200.0f;
                                if (progressAlpha > 1.0f) {
                                    progressAlpha = 1.0f;
                                }
                            }
                        } else {
                            if (progressAlpha > 0.0f) {
                                progressAlpha -= delta / 200.0f;
                                if (progressAlpha < 0.0f) {
                                    progressAlpha = 0.0f;
                                }
                            }
                        }
                    }
                    lastUpdateTime = newTime;
                    invalidate();
                }
            }
        };
        addButton.setGravity(Gravity.CENTER);
        addButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        addButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        addButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        addView(addButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.RIGHT, 0, 16, 14, 0));

        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60), MeasureSpec.EXACTLY));

        measureChildWithMargins(nameTextView, widthMeasureSpec, addButton.getMeasuredWidth(), heightMeasureSpec, 0);
    }

    public void setAddOnClickListener(OnClickListener onClickListener) {
        hasOnClick = true;
        addButton.setOnClickListener(onClickListener);
    }

    public void setStickerSet(TLRPC.StickerSetCovered stickerSet, boolean unread) {
        setStickerSet(stickerSet, unread, 0, 0);
    }

    public void setStickerSet(TLRPC.StickerSetCovered stickerSet, boolean unread, int index, int searchLength) {
        lastUpdateTime = System.currentTimeMillis();
        if (searchLength != 0) {
            SpannableStringBuilder builder = new SpannableStringBuilder(stickerSet.set.title);
            try {
                builder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), index, index + searchLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception ignore) {

            }
            nameTextView.setText(builder);
        } else {
            nameTextView.setText(stickerSet.set.title);
        }
        infoTextView.setText(LocaleController.formatPluralString("Stickers", stickerSet.set.count));
        isUnread = unread;
        if (hasOnClick) {
            addButton.setVisibility(VISIBLE);
            if (isInstalled = MediaDataController.getInstance(currentAccount).isStickerPackInstalled(stickerSet.set.id)) {
                addButton.setBackgroundDrawable(delDrawable);
                addButton.setText(LocaleController.getString("StickersRemove", R.string.StickersRemove));
            } else {
                addButton.setBackgroundDrawable(addDrawable);
                addButton.setText(LocaleController.getString("Add", R.string.Add));
            }
            addButton.setPadding(AndroidUtilities.dp(17), 0, AndroidUtilities.dp(17), 0);
        } else {
            addButton.setVisibility(GONE);
        }

        set = stickerSet;
    }

    public void setUrl(CharSequence text, int searchLength) {
        if (text != null) {
            SpannableStringBuilder builder = new SpannableStringBuilder(text);
            try {
                builder.setSpan(new ColorSpanUnderline(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), 0, searchLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ColorSpanUnderline(Theme.getColor(Theme.key_chat_emojiPanelTrendingDescription)), searchLength, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception ignore) {

            }
            infoTextView.setText(builder);
        }
    }

    public boolean isInstalled() {
        return isInstalled;
    }

    public void setDrawProgress(boolean value) {
        drawProgress = value;
        lastUpdateTime = System.currentTimeMillis();
        addButton.invalidate();
    }

    public TLRPC.StickerSetCovered getStickerSet() {
        return set;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isUnread) {
            paint.setColor(Theme.getColor(Theme.key_featuredStickers_unread));
            canvas.drawCircle(nameTextView.getRight() + AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(4), paint);
        }
    }
}
