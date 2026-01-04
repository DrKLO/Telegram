package org.telegram.ui.Components.spoilers;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Region;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.LoadingDrawable;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class SpoilersTextView extends TextView implements TextSelectionHelper.SimpleSelectabeleView {
    private SpoilersClickDetector clickDetector;
    protected List<SpoilerEffect> spoilers = new ArrayList<>();
    private Stack<SpoilerEffect> spoilersPool = new Stack<>();
    private boolean isSpoilersRevealed;
    private Path path = new Path();
    private Paint xRefPaint;
    public boolean allowClickSpoilers = true;

    public int cacheType = AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES;
    private AnimatedEmojiSpan.EmojiGroupedSpans animatedEmoji;
    private boolean useAlphaForEmoji = true;

    private final LinkSpanDrawable.LinkCollector links;
    private Theme.ResourcesProvider resourcesProvider;

    public SpoilersTextView(Context context) {
        this(context, null);
    }

    public SpoilersTextView(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, true, resourcesProvider);
    }

    public SpoilersTextView(Context context, boolean revealOnClick) {
        this(context, revealOnClick, null);
    }

    private boolean clearLinkOnLongPress = true;
    public void setClearLinkOnLongPress(boolean clear) {
        this.clearLinkOnLongPress = clear;
    }

    public void clearLinks() {
        links.clear();
    }

    public SpoilersTextView(Context context, boolean revealOnClick, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.links = new LinkSpanDrawable.LinkCollector(this);
        this.resourcesProvider = resourcesProvider;

        clickDetector = new SpoilersClickDetector(this, spoilers, (eff, x, y) -> {
            if (isSpoilersRevealed || !revealOnClick) return;

            eff.setOnRippleEndCallback(()->post(()->{
                isSpoilersRevealed = true;
                invalidateSpoilers();
            }));

            float rad = (float) Math.sqrt(Math.pow(getWidth(), 2) + Math.pow(getHeight(), 2));
            for (SpoilerEffect ef : spoilers)
                ef.startRipple(x, y, rad);
        });
    }

    private CharacterStyle currentLinkLoading;
    public void setLoading(CharacterStyle span) {
        if (currentLinkLoading != span) {
            links.clearLoading(true);
            currentLinkLoading = span;
            LoadingDrawable drawable = LinkSpanDrawable.LinkCollector.makeLoading(getLayout(), span, getPaddingTop());
            if (drawable != null) {
                final int color = Theme.getColor(Theme.key_chat_linkSelectBackground, resourcesProvider);
                drawable.setColors(
                    Theme.multAlpha(color, .8f),
                    Theme.multAlpha(color, 1.3f),
                    Theme.multAlpha(color, 1f),
                    Theme.multAlpha(color, 4f)
                );
                drawable.strokePaint.setStrokeWidth(AndroidUtilities.dpf2(1.25f));
                links.addLoading(drawable);
            }
        }
    }

    public void setOnLinkPressListener(LinkSpanDrawable.LinksTextView.OnLinkPress listener) {
        onPressListener = listener;
    }

    public void setOnLinkLongPressListener(LinkSpanDrawable.LinksTextView.OnLinkPress listener) {
        onLongPressListener = listener;
    }

    public int overrideLinkColor() {
        return Theme.getColor(Theme.key_chat_linkSelectBackground, resourcesProvider);
    }

    protected LinkSpanDrawable.LinksTextView.OnLinkPress onPressListener;
    protected LinkSpanDrawable.LinksTextView.OnLinkPress onLongPressListener;

    private LinkSpanDrawable<ClickableSpan> pressedLink;

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (links != null) {
            Layout textLayout = getLayout();
            ClickableSpan span;
            if ((span = hit((int) event.getX(), (int) event.getY())) != null) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    final LinkSpanDrawable link = new LinkSpanDrawable<ClickableSpan>(span, resourcesProvider, event.getX(), event.getY());
                    link.setColor(overrideLinkColor());
                    pressedLink = link;
                    links.addLink(pressedLink);
                    Spannable buffer = new SpannableString(textLayout.getText());
                    int start = buffer.getSpanStart(pressedLink.getSpan());
                    int end = buffer.getSpanEnd(pressedLink.getSpan());
                    LinkPath path = pressedLink.obtainNewPath();
                    path.setCurrentLayout(textLayout, start, disablePaddingInLinks ? 0 : getPaddingTop());
                    textLayout.getSelectionPath(start, end, path);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (onLongPressListener != null && pressedLink == link) {
                            onLongPressListener.run(span);
                            pressedLink = null;
                            links.clear();
                        }
                    }, ViewConfiguration.getLongPressTimeout());
                    return true;
                }
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                links.clear();
                if (pressedLink != null && pressedLink.getSpan() == span) {
                    if (onPressListener != null) {
                        onPressListener.run(pressedLink.getSpan());
                    } else if (pressedLink.getSpan() != null) {
                        pressedLink.getSpan().onClick(this);
                    }
                    pressedLink = null;
                    return true;
                }
                pressedLink = null;
            }
            if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                links.clear();
                pressedLink = null;
            }
        }
        if (pressedLink != null) {
            return true;
        }
        if (allowClickSpoilers && clickDetector.onTouchEvent(event))
            return true;
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        isSpoilersRevealed = false;
        super.setText(text, type);
    }

    public void setUseAlphaForEmoji(boolean useAlphaForEmoji) {
        this.useAlphaForEmoji = useAlphaForEmoji;
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        invalidateSpoilers();
        updateAnimatedEmoji(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateSpoilers();
    }

    private ColorFilter animatedEmojiColorFilter;
    @Override
    public void setTextColor(int color) {
        super.setTextColor(color);
        animatedEmojiColorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    protected boolean disablePaddingInLinks = true;
    private boolean disablePaddingsOffset;
    private boolean disablePaddingsOffsetX;
    private boolean disablePaddingsOffsetY;
    public void setDisablePaddingsOffset(boolean disablePaddingsOffset) {
        this.disablePaddingsOffset = disablePaddingsOffset;
    }

    public void setDisablePaddingsOffsetX(boolean disablePaddingsOffsetX) {
        this.disablePaddingsOffsetX = disablePaddingsOffsetX;
    }

    public void setDisablePaddingsOffsetY(boolean disablePaddingsOffsetY) {
        this.disablePaddingsOffsetY = disablePaddingsOffsetY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int pl = getPaddingLeft(), pt = getPaddingTop();

        canvas.save();
        if (!disablePaddingsOffset) {
            canvas.translate(disablePaddingsOffsetX ? 0 : pl, disablePaddingsOffsetY ? 0 : pt);
        }
        if (links != null && links.draw(canvas)) {
            invalidate();
        }
        canvas.restore();

        canvas.save();
        path.rewind();
        for (SpoilerEffect eff : spoilers) {
            Rect bounds = eff.getBounds();
            path.addRect(bounds.left + pl, bounds.top + pt, bounds.right + pl, bounds.bottom + pt, Path.Direction.CW);
        }
        canvas.clipPath(path, Region.Op.DIFFERENCE);
        Emoji.emojiDrawingUseAlpha = useAlphaForEmoji;
        super.onDraw(canvas);
        Emoji.emojiDrawingUseAlpha = true;
        canvas.restore();

        canvas.save();
        canvas.clipPath(path);
        path.rewind();
        if (!spoilers.isEmpty()) {
            spoilers.get(0).getRipplePath(path);
        }
        canvas.clipPath(path);
        super.onDraw(canvas);
        canvas.restore();

        updateAnimatedEmoji(false);
        if (animatedEmoji != null) {
            canvas.save();
            canvas.translate(pl, pt);
            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, getLayout(), animatedEmoji, 0, spoilers, 0, getHeight(), 0, 1f, animatedEmojiColorFilter);
            canvas.restore();
        }

        if (!spoilers.isEmpty()) {
            boolean useAlphaLayer = spoilers.get(0).getRippleProgress() != -1;
            if (useAlphaLayer) {
                canvas.saveLayer(0, 0, getMeasuredWidth(), getMeasuredHeight(), null, canvas.ALL_SAVE_FLAG);
            } else {
                canvas.save();
            }
            canvas.translate(pl, pt + dp(2));
            for (SpoilerEffect eff : spoilers) {
                eff.setColor(getPaint().getColor());
                eff.draw(canvas);
            }

            if (useAlphaLayer) {
                path.rewind();
                spoilers.get(0).getRipplePath(path);
                if (xRefPaint == null) {
                    xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    xRefPaint.setColor(0xff000000);
                    xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                }
                canvas.drawPath(path, xRefPaint);
            }
            canvas.restore();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimatedEmoji(true);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        invalidateSpoilers();
    }

    private Layout lastLayout = null;
    private int lastTextLength;
    public void updateAnimatedEmoji(boolean force) {
        int newTextLength = (getLayout() == null || getLayout().getText() == null) ? 0 : getLayout().getText().length();
        if (force || lastLayout != getLayout() || lastTextLength != newTextLength) {
            animatedEmoji = AnimatedEmojiSpan.update(cacheType, this, animatedEmoji, getLayout());
            lastLayout = getLayout();
            lastTextLength = newTextLength;
        }
    }

    private void invalidateSpoilers() {
        if (spoilers == null) return; // Check for a super constructor
        spoilersPool.addAll(spoilers);
        spoilers.clear();

        if (isSpoilersRevealed) {
            invalidate();
            return;
        }

        Layout layout = getLayout();
        if (layout != null && getText() instanceof Spanned) {
            SpoilerEffect.addSpoilers(this, spoilersPool, spoilers);
        }
        invalidate();
    }


    public ClickableSpan hit(int x, int y) {
        Layout textLayout = getLayout();
        if (textLayout == null) {
            return null;
        }
        x -= getPaddingLeft();
        y -= getPaddingTop();
        final int line = textLayout.getLineForVertical(y);
        final int off = textLayout.getOffsetForHorizontal(line, x);
        final float left = textLayout.getLineLeft(line);
        if (left <= x && left + textLayout.getLineWidth(line) >= x && y >= 0 && y <= textLayout.getHeight()) {
            Spannable buffer = new SpannableString(textLayout.getText());
            ClickableSpan[] spans = buffer.getSpans(off, off, ClickableSpan.class);
            if (spans.length != 0 && !AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
                return spans[0];
            }
        }
        return null;
    }

    @Override
    public Layout getStaticTextLayout() {
        return getLayout();
    }
}
