package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
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
import org.telegram.tgnet.TLObject;
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

    public final static int EMOJI_TYPE = 0;
    public final static int REACTIONS_TYPE = 1;
    public final static int EMOJI_STICKER_TYPE = 2;
    public final static int SINGLE_REACTION_TYPE = 3;
    int type;

    private class BoldAndAccent extends CharacterStyle {
        @Override
        public void updateDrawState(TextPaint textPaint) {
            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            int wasAlpha = textPaint.getAlpha();
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider));
            textPaint.setAlpha(wasAlpha);
        }
    }

    public MessageContainsEmojiButton(int currentAccount, Context context, Theme.ResourcesProvider resourcesProvider, @NonNull ArrayList<TLRPC.InputStickerSet> inputStickerSets, int type) {
        super(context);

        this.currentAccount = currentAccount;
        this.type = type;

        setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 0, 6));

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(AndroidUtilities.dp(13));
        textPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));

        if (inputStickerSets.size() > 1) {
            String string;
            if (type == EMOJI_TYPE) {
                string = LocaleController.formatPluralString("MessageContainsEmojiPacks", inputStickerSets.size());
            } else {
                string = LocaleController.formatPluralString("MessageContainsReactionsPacks", inputStickerSets.size());
            }
            mainText = AndroidUtilities.replaceTags(string);
            Spannable spannable = (Spannable) mainText;
            TypefaceSpan[] bold = spannable.getSpans(0, mainText.length(), TypefaceSpan.class);
            for (int i = 0; bold != null && i < bold.length; ++i) {
                int start = spannable.getSpanStart(bold[i]);
                int end = spannable.getSpanEnd(bold[i]);
                spannable.removeSpan(bold[i]);
                spannable.setSpan(new BoldAndAccent(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } else if (inputStickerSets.size() == 1) {
            String string;
            if (type == EMOJI_TYPE) {
                string = LocaleController.getString("MessageContainsEmojiPack", R.string.MessageContainsEmojiPack);
            } else if (type == SINGLE_REACTION_TYPE) {
                string = LocaleController.getString("MessageContainsReactionPack", R.string.MessageContainsReactionPack);
            } else {
                string = LocaleController.getString("MessageContainsReactionsPack", R.string.MessageContainsReactionsPack);
            }
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
                    emojiDrawable.setColorFilter(Theme.getAnimatedEmojiColorFilter(resourcesProvider));
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
                    loadingDrawable.colorKey1 = Theme.key_actionBarDefaultSubmenuBackground;
                    loadingDrawable.colorKey2 = Theme.key_listSelector;
                    loadingDrawable.setRadiiDp(4);
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
        if (width <= 0) {
            return 0;
        }
        if (mainText != lastMainTextText || lastMainTextWidth != width) {
            if (mainText != null) {
                mainTextLayout = new StaticLayout(mainText, 0, mainText.length(), textPaint, Math.max(width, 0), Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
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
                    loadingBoundsFrom.set(lastLineMargin, lastLineTop, (int) (lastLineMargin + lwidth), (int) bottom);
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
    public boolean checkWidth = true;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setPadding(AndroidUtilities.dp(13), AndroidUtilities.dp(8), AndroidUtilities.dp(13), AndroidUtilities.dp(8));
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (checkWidth) {
            if (lastWidth > 0) {
                width = Math.min(width, lastWidth);
            }
        }
        lastWidth = width;
        int contentWidth = width - getPaddingLeft() - getPaddingRight();
        if (contentWidth < 0) {
            contentWidth = 0;
        }
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
        if (emojiDrawable != null) {
            emojiDrawable.addView(this);
        }
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.groupStickersDidLoad);
    }
}