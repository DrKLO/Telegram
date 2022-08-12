package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ReplacementSpan;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class MessageContainsEmojiButton extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private int currentAccount;
    private Theme.ResourcesProvider resourcesProvider;
    private TLRPC.InputStickerSet inputStickerSet;

    private Rect emojiDrawableBounds = new Rect();
    private AnimatedEmojiDrawable emojiDrawable;

    private boolean loadingDrawableBoundsSet = false;
    private LoadingDrawable loadingDrawable;

    private TextPaint textPaint;

    private CharSequence mainText;
    private StaticLayout mainTextLayout;

    private CharSequence endText;
    private CharSequence secondPartText;
    private StaticLayout secondPartTextLayout;

    private class BoldAndAccent extends CharacterStyle {
        @Override
        public void updateDrawState(TextPaint textPaint) {
            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            int wasAlpha = textPaint.getAlpha();
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider));
            textPaint.setAlpha(wasAlpha);
        }
    }

    public MessageContainsEmojiButton(int currentAccount, Context context, Theme.ResourcesProvider resourcesProvider, @NonNull ArrayList<TLRPC.InputStickerSet> inputStickerSets) {
        super(context);
        this.currentAccount = currentAccount;

        setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 0, 6));

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(AndroidUtilities.dp(13));
        textPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));

        if (inputStickerSets.size() > 1) {
            mainText = AndroidUtilities.replaceTags(LocaleController.formatPluralString("MessageContainsEmojiPacks", inputStickerSets.size()));
            Spannable spannable = (Spannable) mainText;
            TypefaceSpan[] bold = spannable.getSpans(0, mainText.length(), TypefaceSpan.class);
            for (int i = 0; bold != null && i < bold.length; ++i) {
                int start = spannable.getSpanStart(bold[i]);
                int end = spannable.getSpanEnd(bold[i]);
                spannable.removeSpan(bold[i]);
                spannable.setSpan(new BoldAndAccent(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } else if (inputStickerSets.size() == 1) {
            String string = LocaleController.getString("MessageContainsEmojiPack", R.string.MessageContainsEmojiPack);
            String[] parts = string.split("%s");
            if (parts.length <= 1) {
                mainText = string;
            } else {
                String stickerPackName = null;
                TLRPC.Document document = null;
                inputStickerSet = inputStickerSets.get(0);
                if (inputStickerSet != null) {
                    TLRPC.TL_messages_stickerSet stickerSet = MediaDataController.getInstance(currentAccount).getStickerSet(inputStickerSet, false);
                    if (stickerSet != null && stickerSet.set != null) {
                        stickerPackName = stickerSet.set.title;
                        for (int i = 0; stickerSet.documents != null && i < stickerSet.documents.size(); ++i) {
                            if (stickerSet.documents.get(i).id == stickerSet.set.thumb_document_id) {
                                document = stickerSet.documents.get(i);
                                break;
                            }
                        }
                        if (document == null && stickerSet.documents != null && stickerSet.documents.size() > 0) {
                            document = stickerSet.documents.get(0);
                        }
                    }
                }
                if (stickerPackName != null && document != null) {
                    SpannableString emoji = new SpannableString(MessageObject.findAnimatedEmojiEmoticon(document));
                    emoji.setSpan(new AnimatedEmojiSpan(document, textPaint.getFontMetricsInt()) {
                        @Override
                        public void draw(@NonNull Canvas canvas, CharSequence charSequence, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
                            emojiDrawableBounds.set((int) x, (bottom + top - measuredSize) / 2, (int) (x + measuredSize), (bottom + top + measuredSize) / 2);
                        }
                    }, 0, emoji.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    emojiDrawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, document);
                    emojiDrawable.addView(this);

                    SpannableString stickerPack = new SpannableString(stickerPackName);
                    stickerPack.setSpan(new BoldAndAccent(), 0, stickerPack.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    mainText =
                        new SpannableStringBuilder()
                            .append(parts[0])
                            .append(emoji)
                            .append(' ')
                            .append(stickerPack)
                            .append(parts[1]);
                    loadT = 1f;
                    inputStickerSet = null;
                } else {
                    mainText = parts[0];
                    endText = parts[1];
                    loadingDrawable = new LoadingDrawable(resourcesProvider);
                    loadingDrawable.paint.setPathEffect(new CornerPathEffect(AndroidUtilities.dp(4)));
                }
            }
        }
    }

    private int lastLineMargin;
    private int lastLineTop, lastLineHeight;

    private int lastMainTextWidth;
    private CharSequence lastMainTextText;
    private int lastSecondPartTextWidth;
    private CharSequence lastSecondPartText;
    private int updateLayout(int width, boolean full) {
        if (mainText != lastMainTextText || lastMainTextWidth != width) {
            if (mainText != null) {
                mainTextLayout = new StaticLayout(mainText, 0, mainText.length(), textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
                if (loadingDrawable != null && loadingBoundsTo == null) {
                    int lastLine = mainTextLayout.getLineCount() - 1;
                    lastLineMargin = (int) mainTextLayout.getPrimaryHorizontal(mainText.length()) + AndroidUtilities.dp(2);
                    lastLineTop = mainTextLayout.getLineTop(lastLine);
                    float bottom = mainTextLayout.getLineBottom(lastLine);
                    lastLineHeight = (int) bottom - lastLineTop;
                    float lwidth = Math.min(AndroidUtilities.dp(100), mainTextLayout.getWidth() - lastLineMargin);
                    if (loadingBoundsFrom == null) {
                        loadingBoundsFrom = new Rect();
                    }
                    loadingBoundsFrom.set(lastLineMargin, lastLineTop + AndroidUtilities.dp(1.25f), (int) (lastLineMargin + lwidth), (int) bottom + AndroidUtilities.dp(1.25f));
                    loadingDrawable.setBounds(loadingBoundsFrom);
                    loadingDrawableBoundsSet = true;
                }
            } else {
                mainTextLayout = null;
                loadingDrawableBoundsSet = false;
            }
            lastMainTextText = mainText;
            lastMainTextWidth = width;
        }

        if (secondPartText != lastSecondPartText || lastSecondPartTextWidth != width) {
            if (secondPartText != null) {
                secondPartTextLayout = new StaticLayout(secondPartText, 0, secondPartText.length(), textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
            } else {
                secondPartTextLayout = null;
            }
            lastSecondPartText = secondPartText;
            lastSecondPartTextWidth = width;
        }

        return (mainTextLayout == null ? 0 : mainTextLayout.getHeight()) + (int) (secondPartTextLayout != null ? (secondPartTextLayout.getHeight() - lastLineHeight) * (full ? 1 : loadT) : 0);
    }

    private int lastWidth = -1;
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setPadding(AndroidUtilities.dp(13), AndroidUtilities.dp(8), AndroidUtilities.dp(13), AndroidUtilities.dp(8));
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (lastWidth > 0) {
            width = Math.min(width, lastWidth);
        }
        lastWidth = width;
        int contentWidth = width - getPaddingLeft() - getPaddingRight();
        int contentHeight = updateLayout(contentWidth, false);
        int height = contentHeight + getPaddingTop() + getPaddingBottom();
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mainTextLayout != null) {
            canvas.save();
            canvas.translate(getPaddingLeft(), getPaddingTop());
            textPaint.setAlpha(255);
            mainTextLayout.draw(canvas);
            if (loadingDrawable != null && loadingDrawableBoundsSet) {
                loadingDrawable.setAlpha((int) (255 * (1f - loadT)));
                if (loadingBoundsFrom != null && loadingBoundsTo != null) {
                    AndroidUtilities.lerp(loadingBoundsFrom, loadingBoundsTo, loadT, AndroidUtilities.rectTmp2);
                    loadingDrawable.setBounds(AndroidUtilities.rectTmp2);
                }
                loadingDrawable.draw(canvas);
                invalidate();
            }
            if (secondPartTextLayout != null) {
                canvas.save();
                canvas.translate(0, lastLineTop);
                textPaint.setAlpha((int) (255 * loadT));
                secondPartTextLayout.draw(canvas);
                canvas.restore();
            }
            if (emojiDrawable != null) {
                emojiDrawable.setAlpha((int) (255 * loadT));
                emojiDrawable.setBounds(emojiDrawableBounds);
                emojiDrawable.draw(canvas);
            }
            canvas.restore();
        }
    }

    private ValueAnimator loadAnimator;
    private float loadT = 0;
    private Rect loadingBoundsFrom;
    private Rect loadingBoundsTo;

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.groupStickersDidLoad) {
            if (inputStickerSet != null) {
                TLRPC.TL_messages_stickerSet stickerSet = MediaDataController.getInstance(currentAccount).getStickerSet(inputStickerSet, false);
                if (stickerSet == null) {
                    return;
                }
                String stickerPackName = null;
                TLRPC.Document document = null;
                if (stickerSet.set != null) {
                    stickerPackName = stickerSet.set.title;
                    for (int i = 0; stickerSet.documents != null && i < stickerSet.documents.size(); ++i) {
                        if (stickerSet.documents.get(i).id == stickerSet.set.thumb_document_id) {
                            document = stickerSet.documents.get(i);
                            break;
                        }
                    }
                    if (document == null && stickerSet.documents != null && stickerSet.documents.size() > 0) {
                        document = stickerSet.documents.get(0);
                    }
                }
                if (stickerPackName == null || document == null) {
                    return;
                }

                emojiDrawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, document);
                emojiDrawable.addView(this);
                invalidate();

                SpannableString margin = new SpannableString(" ");
                margin.setSpan(new ReplacementSpan() {
                    @Override
                    public int getSize(@NonNull Paint paint, CharSequence charSequence, int i, int i1, @Nullable Paint.FontMetricsInt fontMetricsInt) {
                        return lastLineMargin;
                    }
                    @Override
                    public void draw(@NonNull Canvas canvas, CharSequence charSequence, int i, int i1, float v, int i2, int i3, int i4, @NonNull Paint paint) {}
                }, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                SpannableString emoji = new SpannableString(MessageObject.findAnimatedEmojiEmoticon(document));
                emoji.setSpan(new AnimatedEmojiSpan(document, textPaint.getFontMetricsInt()) {
                    @Override
                    public void draw(@NonNull Canvas canvas, CharSequence charSequence, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
                        emojiDrawableBounds.set((int) x, lastLineTop + (bottom + top - measuredSize) / 2, (int) (x + measuredSize), lastLineTop + (bottom + top + measuredSize) / 2);
                    }
                }, 0, emoji.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                SpannableString stickerPack = new SpannableString(stickerPackName);
                stickerPack.setSpan(new BoldAndAccent(), 0, stickerPack.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                secondPartText =
                    new SpannableStringBuilder()
                        .append(margin)
                        .append(emoji)
                        .append(' ')
                        .append(stickerPack)
                        .append(endText);
                int oldHeight = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
                int newHeight = updateLayout(lastWidth - getPaddingLeft() - getPaddingRight(), true);
                if (loadingBoundsFrom != null && secondPartTextLayout != null) {
                    if (loadingBoundsTo == null) {
                        loadingBoundsTo = new Rect();
                    }
                    float end = secondPartTextLayout.getPrimaryHorizontal(secondPartTextLayout.getLineEnd(0));
                    loadingBoundsTo.set(loadingBoundsFrom.left, loadingBoundsFrom.top, (int) end, loadingBoundsFrom.bottom);
                }
                inputStickerSet = null;

                if (loadAnimator != null) {
                    loadAnimator.cancel();
                }
                boolean remeasure = Math.abs(oldHeight - newHeight) > AndroidUtilities.dp(3);
                loadAnimator = ValueAnimator.ofFloat(loadT, 1f);
                loadAnimator.addUpdateListener(a -> {
                    loadT = (float) a.getAnimatedValue();
                    invalidate();
                    if (remeasure) {
                        requestLayout();
                    }
                });
                loadAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                loadAnimator.setStartDelay(150);
                loadAnimator.setDuration(400);
                loadAnimator.start();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (emojiDrawable != null) {
            emojiDrawable.removeView(this);
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.groupStickersDidLoad);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.groupStickersDidLoad);
    }
}

class LoadingDrawable extends Drawable {

    private Theme.ResourcesProvider resourcesProvider;
    public LoadingDrawable(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
    }

    private long start = -1;
    private LinearGradient gradient;
    private int gradientColor1, gradientColor2;
    private int gradientWidth;

    public Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Path path = new Path();
    private RectF[] rects;

    private void setPathRects(RectF[] rects) {
        this.rects = rects;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (getPaintAlpha() <= 0) {
            return;
        }
        int gwidth = Math.min(AndroidUtilities.dp(400), bounds.width());
        int color1 = Theme.getColor(Theme.key_dialogBackground, resourcesProvider);
        int color2 = Theme.getColor(Theme.key_dialogBackgroundGray, resourcesProvider);
        if (gradient == null || gwidth != gradientWidth || color1 != gradientColor1 || color2 != gradientColor2) {
            gradientWidth = gwidth;
            gradientColor1 = color1;
            gradientColor2 = color2;
            gradient = new LinearGradient(0, 0, gradientWidth, 0, new int[] { gradientColor1, gradientColor2, gradientColor1 }, new float[] { 0f, .67f, 1f }, Shader.TileMode.REPEAT);
            paint.setShader(gradient);
        }

        long now = SystemClock.elapsedRealtime();
        if (start < 0) {
            start = now;
        }
        float offset = gradientWidth - (((now - start) / 1000f * gradientWidth) % gradientWidth);

        canvas.save();
        canvas.clipRect(bounds);
        canvas.translate(-offset, 0);
        path.reset();
        if (rects == null) {
            path.addRect(bounds.left + offset, bounds.top, bounds.right + offset, bounds.bottom, Path.Direction.CW);
        } else {
            for (int i = 0; i < rects.length; ++i) {
                RectF r = rects[i];
                if (r != null) {
                    path.addRect(r.left + offset, r.top, r.right + offset, r.bottom, Path.Direction.CW);
                }
            }
        }
        canvas.drawPath(path, paint);
        canvas.translate(offset, 0);
        canvas.restore();

        invalidateSelf();
    }

    public int getPaintAlpha() {
        return paint.getAlpha();
    }

    @Override
    public void setAlpha(int i) {
        paint.setAlpha(i);
        if (i > 0) {
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
