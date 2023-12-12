package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.spoilers.SpoilersTextView;

// TextView with both Spoilers, Links and Emojis (not animated though)
public class EffectsTextView extends SpoilersTextView {
    public interface OnLinkPress {
        public void run(ClickableSpan span);
    }

    private boolean isCustomLinkCollector;
    private LinkSpanDrawable.LinkCollector links;
    private Theme.ResourcesProvider resourcesProvider;

    private LinkSpanDrawable<ClickableSpan> pressedLink;

    private LinkSpanDrawable.LinksTextView.OnLinkPress onPressListener;
    private LinkSpanDrawable.LinksTextView.OnLinkPress onLongPressListener;

    private boolean disablePaddingsOffset;
    private boolean disablePaddingsOffsetX;
    private boolean disablePaddingsOffsetY;

    public EffectsTextView(Context context) {
        this(context, null);
    }

    public EffectsTextView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, true);
        this.isCustomLinkCollector = false;
        this.links = new LinkSpanDrawable.LinkCollector(this);
        this.resourcesProvider = resourcesProvider;
    }

    public EffectsTextView(Context context, LinkSpanDrawable.LinkCollector customLinkCollector, Theme.ResourcesProvider resourcesProvider) {
        super(context, true);
        this.isCustomLinkCollector = true;
        this.links = customLinkCollector;
        this.resourcesProvider = resourcesProvider;
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false);
        super.setText(text, type);
    }

    public void setDisablePaddingsOffset(boolean disablePaddingsOffset) {
        this.disablePaddingsOffset = disablePaddingsOffset;
    }

    public void setDisablePaddingsOffsetX(boolean disablePaddingsOffsetX) {
        this.disablePaddingsOffsetX = disablePaddingsOffsetX;
    }

    public void setDisablePaddingsOffsetY(boolean disablePaddingsOffsetY) {
        this.disablePaddingsOffsetY = disablePaddingsOffsetY;
    }

    public void setOnLinkPressListener(LinkSpanDrawable.LinksTextView.OnLinkPress listener) {
        onPressListener = listener;
    }

    public void setOnLinkLongPressListener(LinkSpanDrawable.LinksTextView.OnLinkPress listener) {
        onLongPressListener = listener;
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
        final float left = getLayout().getLineLeft(line);
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
    public boolean onTouchEvent(MotionEvent event) {
        if (links != null) {
            Layout textLayout = getLayout();
            ClickableSpan span;
            if ((span = hit((int) event.getX(), (int) event.getY())) != null) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    final LinkSpanDrawable link = new LinkSpanDrawable<ClickableSpan>(span, resourcesProvider, event.getX(), event.getY());
                    pressedLink = link;
                    links.addLink(pressedLink);
                    Spannable buffer = new SpannableString(textLayout.getText());
                    int start = buffer.getSpanStart(pressedLink.getSpan());
                    int end = buffer.getSpanEnd(pressedLink.getSpan());
                    LinkPath path = pressedLink.obtainNewPath();
                    path.setCurrentLayout(textLayout, start, getPaddingTop());
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
        return pressedLink != null || super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!isCustomLinkCollector) {
            canvas.save();
            if (!disablePaddingsOffset) {
                canvas.translate(disablePaddingsOffsetX ? 0 : getPaddingLeft(), disablePaddingsOffsetY ? 0 : getPaddingTop());
            }
            if (links.draw(canvas)) {
                invalidate();
            }
            canvas.restore();
        }
        super.onDraw(canvas);
    }
}
