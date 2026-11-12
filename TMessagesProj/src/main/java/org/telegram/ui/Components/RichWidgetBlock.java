package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.RichMessageLayout;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ChatMessageTransitionParams;
import org.telegram.ui.Cells.TextSelectionHelper;

import java.util.concurrent.atomic.AtomicInteger;
import org.telegram.utils.twidget.WidgetMedia;
import org.telegram.utils.twidget.WidgetSandboxMessage;
import org.telegram.utils.twidget.WidgetSandboxSession;
import org.telegram.utils.twidget.WidgetSandboxTheme;
import org.telegram.utils.twidget.WidgetText;

/**
 * The widget of a widget message (`messageMediaWidget`), laid out by
 * {@link WidgetMedia} as a rich message's embed. The {@link WidgetSandboxMessage} behind it and its
 * size come from WidgetMedia; this block only draws it, forwards touches and reports when it is shown
 * and when its height changes.
 *
 * <p>Like a photo, the widget spans the whole bubble: it bleeds into the text paddings on both
 * sides and, first in the message, starts at the bubble's top, clipped to its corners.
 *
 * <p>May be built on any thread, like the rest of the layout.
 */
public final class RichWidgetBlock extends RichMessageLayout.RichBlock
        implements WidgetSandboxPlayer.Host {
    public final TL_iv.pageBlockEmbed embed;
    /** Null for an embed this block cannot run (its payload does not unpack). */
    public WidgetSandboxMessage message;
    public final boolean first;
    // WidgetSandboxTheme#signature of the theme passed last: another one when drawn passes it again.
    private int themeSignature;
    private final Path clip = new Path();
    private final float[] radii = new float[8];
    private static final AtomicInteger serials = new AtomicInteger();
    /** Newer layouts of a message get larger serials: the newest attached block runs the widget. */
    public final int serial = serials.incrementAndGet();
    private WidgetSandboxPlayer player;
    // UI: this block runs the widget (WidgetMedia#attach); otherwise it only shows its frames.
    private boolean running;
    /**
     * A widget's first frames come before its scripts have built it (a short page, then the full one some
     * tens of ms later): until it has loaded its height is not taken. The first loaded frame's is, at once;
     * for a while after that a height change waits until the height holds, so the bubble moves once to
     * where it stays, or not at all when that is the height it already had.
     */
    private static final long SETTLE_MS = 1500, SETTLE_DELAY_MS = 200;
    private long runSince;
    // UI: since run(), the bubble has taken a height of the loaded widget (onWidgetResized).
    private boolean loadedHeight;
    private final Runnable settledResize = this::onSettled;
    private int laidOutHeight, bleedLeft;
    // Where laidOutHeight came from (WidgetMedia#heightSource), for the height log.
    private String heightSource;
    // The text the widget made selectable (user-select: text), as the shown frame lays it out, and its
    // blocks for the chat's text selection (RichMessageLayout#textBlocks).
    private WidgetText selectable;
    private TextSelectionHelper.TextLayoutBlock[] texts;
    // The height changed while away: the cell, if it was not bound again, is measured anew.
    private boolean resizedAway;
    /**
     * An animated change of this widget's own height (relayout): while the cell's bubble animates to it, the
     * frame stays pinned to the edge the widget anchored (docs/sdk-v1.md §5.6). The animation starts with the
     * next layout; none within RESIZE_ANIMATION_WAIT_MS, the change was not animated.
     */
    private static final long RESIZE_ANIMATION_WAIT_MS = 250;
    private long resizeAnimationSince;
    private boolean resizeAnimating;
    // The cell draws the message time over the widget's bottom corner (docs/sdk-v1.md §5.12): the widget is the
    // bubble's bottom, its bottom corners follow the bubble's. Where the frame was last drawn, in block px.
    private boolean timeOver;
    private float drawnBottom;

    /** An embed this block can show: a widget message's (WidgetMedia#richMessage). */
    public static boolean supports(TL_iv.pageBlockEmbed embed) {
        return embed != null
                && embed.html != null
                && WidgetMedia.isWidgetUrl(embed.url);
    }

    /** The content width a block for `embed` takes of `maxWidth`: less for a widget of a fixed width. */
    public static int contentWidth(TL_iv.pageBlockEmbed embed, int maxWidth) {
        if (!WidgetMedia.isWidgetUrl(embed.url)) return maxWidth;
        return WidgetMedia.widgetWidthPx(embed.url, maxWidth + 2 * bleed()) - 2 * bleed();
    }

    /**
     * How far the widget reaches into each side's text padding: the padding (9 dp from the bubble
     * plus ChatMessageCell's extra text inset) less the 2 dp photos keep, see RichMediaBlock.
     */
    public static int bleed() {
        int extra = SharedConfig.bubbleRadius >= 15 ? dp(2) : SharedConfig.bubbleRadius >= 11 ? dp(1) : 0;
        return dp(9) + extra - dp(2);
    }

    public RichWidgetBlock(
            RichMessageLayout root, Rect padding, int maxWidth, TL_iv.pageBlockEmbed embed,
            boolean first) {
        super(root, padding, maxWidth);
        this.embed = embed;
        this.first = first;
        this.message = WidgetMedia.find(embed.url);
        laidOutHeight = contentHeight();
        heightSource = WidgetMedia.heightSource(embed.url, message, widthPx());
        // No height for this width yet: the widget runs off screen for it before the cell shows it.
        if (heightSource.equals("placeholder") || heightSource.startsWith("cache of width")) WidgetMedia.measure(embed.url, widthPx(), root.isOut());
        // A new layout of a message already shown (its width, text size or theme changed).
        RichWidgetBlock previous = WidgetMedia.registered(embed.url);
        if (previous != null && previous != this && previous.laidOutHeight != laidOutHeight) {
            logHeight("new layout, width " + css(previous.widthPx()) + " -> " + css(widthPx()), previous.laidOutHeight, previous.heightSource, false);
        }
        WidgetMedia.register(embed.url, this);
        estimateSafeArea();
    }

    /**
     * The time over the widget as this layout's message expects it, before a cell lays it out ({@link #setSafeArea}):
     * a widget runs off screen to be measured with the band already in its height. No caption below the widget
     * (none, or shown above it) and a bubble: the time's band and the corner of the time alone (no views, no
     * reactions), the cell's backing as ChatMessageCell#drawTimeInternal draws it over media.
     */
    private void estimateSafeArea() {
        org.telegram.messenger.MessageObject object = root.messageObject;
        if (message == null || object == null || object.messageOwner == null) return;
        boolean over = !(object.isCompactMode() && !object.isOutOwner())
                && (android.text.TextUtils.isEmpty(object.messageOwner.message) || object.messageOwner.invert_media);
        if (!over) {
            message.setSafeArea(0, 0, false);
            return;
        }
        // The cell's own time text (ChatMessageCell#measureTime): "today at …" in Saved Messages, the edit date.
        boolean edited = !object.scheduled && !object.messageOwner.edit_hide
                && ((object.messageOwner.flags & org.telegram.tgnet.TLRPC.MESSAGE_FLAG_EDITED) != 0 || object.isEditing());
        String time = ChatMessageCell.timeString(object, object, edited, object.messageOwner.edit_date);
        Paint paint = Theme.chat_timePaint;
        if (paint == null) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTextSize(dp(12));
        }
        int width = (int) Math.ceil(paint.measureText(time));
        // A channel post's views, as measureTime adds them.
        if ((object.messageOwner.flags & org.telegram.tgnet.TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0 && Theme.chat_msgInViewsDrawable != null) {
            String views = LocaleController.formatShortNumber(Math.max(1, object.messageOwner.views), null);
            Drawable icon = Theme.chat_msgInViewsDrawable;
            width += (int) Math.ceil(paint.measureText(views)) + icon.getIntrinsicWidth() * (paint.getTextSize() - dp(2)) / icon.getIntrinsicHeight() + dp(10);
        }
        message.setSafeArea(cssCeil(timeBand()), cssCeil(timeCorner(width, object.isOutOwner())), false);
    }

    /** The height of the time's band at the widget's bottom, in px: its backing (17 dp) and the 6 dp under it. */
    public static int timeBand() {
        return dp(23);
    }

    /**
     * The width of the corner the time takes, in px: the backing over media (the time and its padding, the ticks of an
     * outgoing message), the 6 dp right of it and 4 dp left of it, so the widget's row does not touch it.
     */
    public static int timeCorner(int timeWidth, boolean out) {
        return dp(6) + timeWidth + 2 * timePadding() + (out ? dp(20) : 0) + dp(4);
    }

    /** The backing's padding on each side of the time (ChatMessageCell#drawTimeInternal over media). */
    public static int timePadding() {
        return dp(SharedConfig.bubbleRadius >= 10 ? 6 : 4);
    }

    /**
     * UI: the cell laid the message out (docs/sdk-v1.md §5.12): the band and the corner of its time over the widget,
     * in px, both 0 when the time is not over it. The widget hears of a change only.
     */
    public void setSafeArea(int bottom, int corner) {
        timeOver = bottom > 0;
        if (message != null) message.setSafeArea(cssCeil(bottom), cssCeil(corner), true);
    }

    /** px as whole CSS px, rounded up: the widget keeps clear of all of it. */
    private static float cssCeil(int px) {
        return (float) Math.ceil(px / AndroidUtilities.density - 0.01f);
    }

    /** The right edge of the widget's frame as last drawn, in the layout's px. */
    public float drawnRight() {
        return padding.left - bleedLeft + widthPx();
    }

    /** The bottom edge of the widget's frame as last drawn (with the bubble's animation), in the layout's px. */
    public float drawnBottom() {
        return padding.top + drawnBottom;
    }

    /** The widget's width in px: the content width and both bleeds. */
    public int widthPx() {
        return maxWidth + 2 * bleed();
    }

    private static boolean shownInList(View cell) {
        if (!(cell.getParent() instanceof androidx.recyclerview.widget.RecyclerView)) return false;
        View list = (View) cell.getParent();
        return cell.getBottom() > list.getPaddingTop() && cell.getTop() < list.getHeight() - list.getPaddingBottom();
    }

    /** The edge the widget anchored stays put when the bubble's height changes (WidgetMedia#anchorBottom). */
    private boolean anchorBottom() {
        return WidgetMedia.anchorBottom(embed.url, message);
    }

    private int contentHeight() {
        return WidgetMedia.contentHeightPx(embed.url, message, widthPx());
    }

    /**
     * The layout keeps the height it was given until the chat lays the message out again
     * ({@link #onWidgetResized}): frames of another height are clipped meanwhile.
     */
    @Override
    public int getHeight() {
        return padding.top + laidOutHeight + padding.bottom;
    }

    /**
     * UI: the height changed while no cell showed this block. The layout takes it now, so the
     * next bind measures the message at its new height without animating.
     */
    public void onResizedAway(String reason) {
        if (view != null) {
            // Shown before its cached heights were read, with nothing drawn yet: the placeholder takes the
            // cached height now, before the first frame, rather than jumping once the widget is up.
            boolean drawn = message != null && message.heightPx() > 0;
            if (!drawn && contentHeight() != laidOutHeight) relayout(false, "on screen: " + reason);
            return;
        }
        int height = contentHeight();
        if (height == laidOutHeight) return;
        int from = laidOutHeight;
        String fromSource = heightSource;
        laidOutHeight = height;
        heightSource = WidgetMedia.heightSource(embed.url, message, widthPx());
        logHeight("away: " + reason, from, fromSource, false);
        root.reposition();
        if (root.messageObject != null) root.messageObject.forceUpdate = true;
        resizedAway = true;
    }

    @Override
    protected void onAttachedToWindow() {
        if (message == null) message = WidgetMedia.find(embed.url);
        if (message == null) return;
        if (player == null) player = new WidgetSandboxPlayer(this, AndroidUtilities.density);
        // A cell animating to a new layout of this message attaches the old one too: only the newest
        // runs the widget (its width, visibility, frame callbacks), the other shows the frame it has.
        if (WidgetMedia.attach(embed.url, this)) run();
        else player.preview(message);
    }

    /** UI: this block now runs the widget. */
    private void run() {
        running = true;
        runSince = android.os.SystemClock.uptimeMillis();
        loadedHeight = message.hasLoadedFrame();
        message.setWidthPx(widthPx());
        updateTheme();
        player.bind(message);
        player.setVisible(true);
        WidgetMedia.register(embed.url, this);
        WidgetMedia.onShown(message);
        // A cell kept from before a resize away was not measured again: relayout it, unanimated.
        boolean stale = resizedAway && root.messageObject != null && root.messageObject.forceUpdate;
        resizedAway = false;
        if (stale || contentHeight() != laidOutHeight)
            AndroidUtilities.runOnUIThread(() -> relayout(false, stale ? "shown after a change away" : "shown, height differs"));
    }

    @Override
    protected void onDetachedFromWindow() {
        if (player == null) return;
        stop();
        player.unbind();
        // Once this block let the widget go, another one still attached may run it.
        WidgetMedia.detach(embed.url, this);
    }

    private void stop() {
        if (!running) return;
        running = false;
        AndroidUtilities.cancelRunOnUIThread(settledResize);
        player.setVisible(false);
        player.unbind();
        if (message != null) WidgetMedia.onHidden(message);
    }

    /** UI: the widget's engine process died (WidgetMedia): it starts afresh as when shown, from its last frame. */
    public void restartWidget() {
        if (!running || message == null) return;
        stop();
        run();
    }

    /** UI: a newer layout of the message took the widget: this one keeps showing its frames only. */
    public void demote() {
        if (!running || player == null) return;
        stop();
        player.preview(message);
        if (view != null) view.invalidate();
    }

    /** UI: the newer layout left: this one, still attached, runs the widget again. */
    public void promote() {
        if (running || view == null || player == null || message == null) return;
        player.unbind();
        run();
        view.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (player == null) return;
        if (themeSignature != WidgetSandboxTheme.signature(resourcesProvider(), root.isOut())) updateTheme();
        // The paddings are known when drawn; the widget was laid out for bleed() on both sides.
        bleedLeft = isInQuote() ? 0 : Math.max(0, Math.min(bleed(), root.padLeft - dp(2)));
        int width = widthPx();
        // While the bubble animates to a new height of the widget, the frame keeps to the bubble's drawn bounds
        // and moves with its anchored edge: the bottom for `bottom`, the top for `top`.
        float top = 0, bottom = laidOutHeight, shift = 0;
        ChatMessageTransitionParams transition = resizeAnimationSince != 0 && root.getCell() != null ? root.getCell().getTransitionParams() : null;
        if (transition != null && transition.animateBackgroundBoundsInner) {
            resizeAnimating = true;
            top += transition.deltaTop;
            bottom += transition.deltaBottom;
            shift = anchorBottom() ? transition.deltaBottom : transition.deltaTop;
        } else if (transition != null && (resizeAnimating || android.os.SystemClock.uptimeMillis() - resizeAnimationSince > RESIZE_ANIMATION_WAIT_MS)) {
            resizeAnimationSince = 0;
            resizeAnimating = false;
        }
        // A frame of a height the bubble has not taken yet (onWidgetResized waits for it to hold) keeps to the
        // anchored edge too: a `bottom` one shows its bottom at the bubble's.
        if (anchorBottom()) shift += laidOutHeight - contentHeight();
        drawnBottom = bottom;
        canvas.save();
        canvas.translate(-bleedLeft, 0);
        clip.rewind();
        cornerRadii();
        AndroidUtilities.rectTmp.set(0, top, width, bottom);
        clip.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.translate(0, shift);
        if (player.draw(canvas, width) && view != null) view.postInvalidateOnAnimation();
        canvas.restore();
    }

    /**
     * The chat's theme for the widget: the palette of the bubble it sits in, an outgoing one's when the
     * user sent or forwarded it, and the text tokens (docs/sdk-v1.md §5.1). Passed again on every change of
     * what it is built from (onDraw).
     */
    private void updateTheme() {
        Theme.ResourcesProvider provider = resourcesProvider();
        themeSignature = WidgetSandboxTheme.signature(provider, root.isOut());
        message.setTheme(WidgetSandboxTheme.build(provider, root.isOut()));
    }

    private Theme.ResourcesProvider resourcesProvider() {
        ChatMessageCell cell = root.getCell();
        return cell != null ? cell.getResourcesProvider() : null;
    }

    // ── text selection (docs/sdk-v1.md §5.10) ──

    @Override
    protected TextSelectionHelper.TextLayoutBlock[] getText() {
        return texts;
    }

    /**
     * UI: the shown frame's selectable text became the message's text blocks, so the chat selects and copies
     * it as it does message text: through the bubble's menu and the selection handles.
     */
    private void syncText() {
        WidgetSandboxSession.Frame frame = message == null ? null : message.frame();
        WidgetText text = frame == null ? null : frame.text;
        if (text == selectable) return;
        selectable = text;
        if (text == null) {
            texts = null;
        } else {
            texts = new TextSelectionHelper.TextLayoutBlock[text.paragraphs.size()];
            for (int i = 0; i < texts.length; i++) texts[i] = new ParagraphBlock(text.paragraphs.get(i));
        }
        root.reposition();
    }

    /** A paragraph of the widget's selectable text where the widget draws it, in the message layout. */
    private final class ParagraphBlock implements TextSelectionHelper.TextLayoutBlock {
        private final WidgetText.Paragraph paragraph;

        ParagraphBlock(WidgetText.Paragraph paragraph) {
            this.paragraph = paragraph;
        }

        @Override
        public android.text.Layout getLayout() {
            return paragraph.layout();
        }

        @Override
        public int getX() {
            return layoutX - bleedLeft + paragraph.left;
        }

        @Override
        public int getY() {
            return layoutY + paragraph.top;
        }

        @Override
        public int getRow() {
            return layoutRow;
        }
    }

    /** The view drawing this block, or null. */
    public View hostView() {
        return view;
    }

    /** Photo corners (RichMediaBlock): the bubble's radius on top when first, small elsewhere. */
    private void cornerRadii() {
        int radius = SharedConfig.bubbleRadius > 2 ? dp(SharedConfig.bubbleRadius - 2) : dp(SharedConfig.bubbleRadius);
        int near = Math.min(dp(3), radius);
        float topLeft = first && !root.hasNameOffset() && (root.isOut() || !root.isPinnedTop()) ? radius : near;
        float topRight = first && (!root.isOut() || !root.isPinnedTop()) ? radius : near;
        radii[0] = radii[1] = topLeft;
        radii[2] = radii[3] = topRight;
        // With the time over it the widget ends the bubble: the corner away from the tail follows the bubble's.
        float bottomLeft = timeOver && root.isOut() ? radius : near;
        float bottomRight = timeOver && !root.isOut() ? radius : near;
        radii[4] = radii[5] = bottomRight;
        radii[6] = radii[7] = bottomLeft;
    }

    /**
     * A mouse hovering the cell, in this block's coordinates (ChatMessageCell#onHoverEvent): the widget's :hover and
     * cursor while it is over the widget's area.
     */
    public void onHoverEvent(MotionEvent event) {
        if (player == null || !running) return;
        event.offsetLocation(-padding.left + bleedLeft, -padding.top);
        try {
            boolean inside = event.getX() >= 0 && event.getX() < widthPx() && event.getY() >= 0 && event.getY() < laidOutHeight;
            player.onHoverEvent(event, inside);
        } finally {
            event.offsetLocation(padding.left - bleedLeft, padding.top);
        }
    }

    @Override
    public void setCursor(String cursor) {
        if (view != null && android.os.Build.VERSION.SDK_INT >= 24) Cursors.set(view, cursor);
    }

    /** CSS cursors as Android's pointer icons (API 24); null for the default. */
    @androidx.annotation.RequiresApi(24)
    private static final class Cursors {
        static void set(View view, String cursor) {
            int type;
            switch (cursor) {
                case "pointer": type = android.view.PointerIcon.TYPE_HAND; break;
                case "text": type = android.view.PointerIcon.TYPE_TEXT; break;
                case "vertical-text": type = android.view.PointerIcon.TYPE_VERTICAL_TEXT; break;
                case "crosshair": type = android.view.PointerIcon.TYPE_CROSSHAIR; break;
                case "move":
                case "all-scroll": type = android.view.PointerIcon.TYPE_ALL_SCROLL; break;
                case "grab": type = android.view.PointerIcon.TYPE_GRAB; break;
                case "grabbing": type = android.view.PointerIcon.TYPE_GRABBING; break;
                case "help": type = android.view.PointerIcon.TYPE_HELP; break;
                case "wait":
                case "progress": type = android.view.PointerIcon.TYPE_WAIT; break;
                case "copy": type = android.view.PointerIcon.TYPE_COPY; break;
                case "alias": type = android.view.PointerIcon.TYPE_ALIAS; break;
                case "cell": type = android.view.PointerIcon.TYPE_CELL; break;
                case "context-menu": type = android.view.PointerIcon.TYPE_CONTEXT_MENU; break;
                case "not-allowed":
                case "no-drop": type = android.view.PointerIcon.TYPE_NO_DROP; break;
                case "zoom-in": type = android.view.PointerIcon.TYPE_ZOOM_IN; break;
                case "zoom-out": type = android.view.PointerIcon.TYPE_ZOOM_OUT; break;
                case "ew-resize":
                case "col-resize":
                case "e-resize":
                case "w-resize": type = android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW; break;
                case "ns-resize":
                case "row-resize":
                case "n-resize":
                case "s-resize": type = android.view.PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW; break;
                case "nesw-resize":
                case "ne-resize":
                case "sw-resize": type = android.view.PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW; break;
                case "nwse-resize":
                case "nw-resize":
                case "se-resize": type = android.view.PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW; break;
                case "none": type = android.view.PointerIcon.TYPE_NULL; break;
                default:
                    view.setPointerIcon(null);
                    return;
            }
            view.setPointerIcon(android.view.PointerIcon.getSystemIcon(view.getContext(), type));
        }
    }

    @Override
    protected boolean onTouchEvent(MotionEvent event) {
        if (player == null || !running) return false;
        if (WidgetMedia.isPreview(embed.url)) return false;
        event.offsetLocation(bleedLeft, 0);
        try {
            return player.onTouchEvent(event);
        } finally {
            event.offsetLocation(-bleedLeft, 0);
        }
    }

    @Override
    public boolean isHorizontallyDragging() {
        // The widget keeps this press from the message menu: it claimed holds, or it has a drag or a hold.
        return player != null && running && player.blocksMessageMenu();
    }

    /** A horizontal drag of the current press may still be the bubble's swipe-to-reply (docs/sdk-v1.md §5.5). */
    public boolean mayGiveSwipe() {
        return player != null && running && player.mayGiveSwipe();
    }

    /** MotionEvent#getDownTime of the last press the widget got, 0 for none. */
    public long lastDownTime() {
        return player != null ? player.lastDownTime() : 0;
    }

    private void relayout(boolean animated, String reason) {
        int height = contentHeight();
        boolean changed = height != laidOutHeight;
        int from = laidOutHeight;
        String fromSource = heightSource;
        laidOutHeight = height;
        heightSource = WidgetMedia.heightSource(embed.url, message, widthPx());
        if (changed) logHeight(reason, from, fromSource, animated);
        if (changed) root.reposition();
        if (changed && animated) {
            resizeAnimationSince = android.os.SystemClock.uptimeMillis();
            resizeAnimating = false;
        }
        if (root.messageObject != null) root.messageObject.forceUpdate = true;
        ChatMessageCell cell = root.getCell();
        ChatMessageCell.ChatMessageCellDelegate delegate = root.getDelegate();
        // The edge the widget anchored stays put (docs/sdk-v1.md §2.12, §5.6). The chat, laid out from its
        // bottom, keeps the bubble's bottom as when any message changes height (`bottom`); for `top` it keeps
        // the cell's top, and the messages below move.
        if (view != null && cell != null && delegate != null) {
            // Only a cell the chat's list shows has a top to keep (a preview's has none); one laid out off screen
            // changes height as any message does, and what is on screen stays.
            boolean keepTop = changed && !anchorBottom() && shownInList(cell);
            if (animated) delegate.forceUpdate(cell, keepTop, keepTop);
            else delegate.forceUpdateNoAnimation(cell, keepTop, keepTop);
        } else if (view != null) view.requestLayout();
    }

    // ── WidgetSandboxPlayer.Host ──

    /** On screen: the widget changed its own height (a tap, a timer): animated, as Telegram does. */
    @Override
    public void onWidgetResized() {
        AndroidUtilities.cancelRunOnUIThread(settledResize);
        if (!loadedHeight) {
            if (!message.hasLoadedFrame()) {
                // Before its scripts: the height waits for the loaded page, or holds if the widget never loads.
                AndroidUtilities.runOnUIThread(settledResize, SETTLE_MS);
                return;
            }
            loadedHeight = true;
            runSince = android.os.SystemClock.uptimeMillis();
            if (contentHeight() != laidOutHeight) relayout(true, "on screen, loaded");
            return;
        }
        if (android.os.SystemClock.uptimeMillis() - runSince < SETTLE_MS) {
            AndroidUtilities.runOnUIThread(settledResize, SETTLE_DELAY_MS);
            return;
        }
        if (contentHeight() != laidOutHeight) relayout(true, "on screen");
    }

    /** The height held after the widget started: the one change it made, if any. */
    private void onSettled() {
        if (running && contentHeight() != laidOutHeight) relayout(true, "on screen, settled after start");
    }

    /**
     * Logs a change of the bubble's height (BuildVars.LOGS_ENABLED): the message, the trigger, the
     * heights and where each came from (WidgetMedia#heightSource), and the widget's state then.
     */
    private void logHeight(String reason, int from, String fromSource, boolean animated) {
        if (!BuildVars.LOGS_ENABLED) return;
        long age = running ? android.os.SystemClock.uptimeMillis() - runSince : -1;
        long touch = player != null ? player.msSinceTouch() : -1;
        FileLog.d("widget height " + embed.url.substring(WidgetMedia.URL_PREFIX.length())
                + ": " + reason
                + ", " + css(from) + " (" + fromSource + ") -> " + css(laidOutHeight) + " (" + heightSource + ")"
                + ", width " + css(widthPx())
                + (animated ? ", animated" : "")
                + (view != null ? ", on screen" : ", off screen")
                + (age >= 0 ? ", running " + age + " ms" : ", not running")
                + (message != null ? ", session " + message.sessions() + (message.hasSnapshot() ? " (snapshot)" : "")
                        + (message.scriptsRunning() ? "" : ", scripts held") : ", no widget yet")
                + (touch >= 0 && touch < 5000 ? ", touched " + touch + " ms ago" : ""));
    }

    private static int css(int px) {
        return Math.round(px / AndroidUtilities.density);
    }

    @Override
    public void invalidateWidget() {
        syncText();
        if (view != null) view.invalidate();
    }

    @Override
    public void onBubbleHold() {
        ChatMessageCell cell = root.getCell();
        if (cell != null) cell.onWidgetHold();
    }

    @Override
    public void disallowParentIntercept(boolean disallow) {
        requestDisallowParentIntercept(disallow);
    }
}
