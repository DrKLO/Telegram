package org.telegram.ui.Components.chat;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextPaint;
import android.util.LongSparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;

import java.util.ArrayList;

public class ChatActivityMessageMetricsView extends View implements ViewTreeObserver.OnPreDrawListener,
        ViewTreeObserver.OnScrollChangedListener, ViewTreeObserver.OnGlobalLayoutListener {
    private static final long GRACE_TIME = 300;
    private static final long MAX_WATCH_TIME = 5 * 60 * 1000; // 5 min
    private static final long USER_ACTIVE_TIME = 15 * 1000; // 15 sec
    private static final long FLUSH_TIME = 5 * 1000; // 5 sec
    private static final long REPEAT_PERIOD = 400;

    private static final String TAG = "ViewMetrics";

    private final boolean DRAW_DEBUG;

    private final RectF viewPortInsets = new RectF();
    private final RectF viewPort = new RectF();
    private long dialogId;
    private int currentAccount;
    private ViewGroup root;
    private ViewGroup list;
    private long lastUserActivityTime;

    public ChatActivityMessageMetricsView(Context context) {
        super(context);
        DRAW_DEBUG = SharedConfig.debugViewMetrics;
    }

    public void init(int currentAccount, long dialogId, ViewGroup root, ViewGroup list) {
        this.dialogId = dialogId;
        this.currentAccount = currentAccount;
        this.root = root;
        this.list = list;
    }

    public void setViewportPadding(float left, float top, float right, float bottom) {
        viewPortInsets.set(left, top, right, bottom);
        invalidateViewPort();
    }

    public void setIsUserActive() {
        lastUserActivityTime = SystemClock.uptimeMillis();
    }

    private void invalidateViewPort() {
        viewPort.set(
            viewPortInsets.left,
            viewPortInsets.top,
            getMeasuredWidth() - viewPortInsets.right,
            getMeasuredHeight() - viewPortInsets.bottom
        );
    }

    public void finish() {
        for (int a = 0, N = watchers.size(); a < N; a++) {
            final MessageWatcher watcher = watchers.valueAt(a);
            if (watcher.visible) {
                pendingMetrics.add(watcher.buildMetrics());
            }
        }
        if (BuildVars.LOGS_ENABLED) {
            Log.d(TAG, "finish");
        }
        watchers.clear();
        flushImpl();
    }

    private Runnable pendingFlush;

    private void flushImpl() {
        if (pendingFlush != null) {
            AndroidUtilities.cancelRunOnUIThread(pendingFlush);
            pendingFlush = null;
        }

        if (!pendingMetrics.isEmpty()) {
            TLRPC.TL_messages_reportReadMetrics req = new TLRPC.TL_messages_reportReadMetrics();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            req.metrics = new ArrayList<>(pendingMetrics);
            ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, null, (r, e) -> {});
            pendingMetrics.clear();
        }
    }


    private static final RectF tmpRect = new RectF();
    private long lastTime;

    private final ArrayList<TLRPC.TL_inputMessageReadMetric> pendingMetrics = new ArrayList<>();

    private void processCurrentFrame() {
        if (root == null || list == null) {
            return;
        }

        final long time = SystemClock.uptimeMillis();
        final long timeDiff = lastTime == 0 ? 0 : (time - lastTime);
        lastTime = time;

        for (int a = 0, N = groupedPositions.size(); a < N; a++) {
            groupedPositions.valueAt(a).set(0, 0, 0, 0);
        }

        for (int a = 0, N = list.getChildCount(); a < N; a++) {
            final View child = list.getChildAt(a);
            if (!ViewPositionWatcher.computeRectInParent(child, root, tmpRect)) {
                continue;
            }

            final MessageObject message;
            if (child instanceof ChatMessageCell) {
                message = ((ChatMessageCell) child).getMessageObject();
            } else if (child instanceof ChatActionCell) {
                message = ((ChatActionCell) child).getMessageObject();
            } else {
                continue;
            }
            if (message == null) {
                continue;
            }

            final long dialogId = message.getDialogId();
            if (dialogId != this.dialogId) {
                continue;
            }

            final int messageId = message.getId();
            final long groupId = message.getGroupId();
            if (dialogId == 0 || messageId <= 0) {
                continue;
            }

            MessageWatcher watcher = watchers.get(messageId);
            if (watcher == null) {
                watcher = new MessageWatcher(messageId, groupId);
                watchers.put(messageId, watcher);
                if (BuildVars.LOGS_ENABLED) {
                    Log.d(TAG, messageId + " " + groupId + " in screen");
                }
            }
            watcher.position.set(tmpRect);

            if (groupId != 0) {
                RectF groupedPosition = groupedPositions.get(groupId);
                if (groupedPosition == null) {
                    groupedPosition = new RectF();
                    groupedPositions.put(groupId, groupedPosition);
                }
                groupedPosition.union(watcher.position);
            }
            watcher.lastUpdateMillis = time;
        }

        for (int N = groupedPositions.size(), a = N - 1;  a >= 0; a--) {
            final RectF position = groupedPositions.valueAt(a);
            if (position.isEmpty()) {
                groupedPositions.removeAt(a);
            }
        }

        for (int a = 0, N = watchers.size(); a < N; a++) {
            final MessageWatcher watcher = watchers.valueAt(a);
            if (watcher.groupId != 0) {
                final RectF position = groupedPositions.get(watcher.groupId);
                if (position != null) {
                    watcher.position.set(position);
                }
            }
        }


        for (int N = watchers.size(), a = N - 1;  a >= 0; a--) {
            final MessageWatcher watcher = watchers.valueAt(a);
            final long messageId = watcher.messageId;
            final long groupId = watcher.groupId;

            final RectF groupedPosition = groupId != 0 ?
                groupedPositions.get(groupId) : null;

            final boolean isVisibleOnScreen = watcher.lastUpdateMillis == time || groupedPosition != null;
            final boolean isVisibleInViewPort = isVisibleOnScreen && RectF.intersects(viewPort,
                groupedPosition != null ? groupedPosition : watcher.position);

            if (isVisibleInViewPort) {
                if (watcher.lastViewMillis != 0) {
                    watcher.visibleTime += timeDiff;
                    if (time - lastUserActivityTime < USER_ACTIVE_TIME) {
                        watcher.activeTime += timeDiff;
                    }
                }
                watcher.lastViewMillis = time;

                final float viewHeight = watcher.position.height();
                final float seenTop = viewPort.top - watcher.position.top;
                final float seenBottom = viewHeight - (watcher.position.bottom - viewPort.bottom);

                watcher.seenTopPx = Math.min(watcher.seenTopPx, MathUtils.clamp(seenTop, 0, viewHeight));
                watcher.seenBottomPx = Math.max(watcher.seenBottomPx, MathUtils.clamp(seenBottom, 0, viewHeight));
                watcher.maxViewPortHeight = Math.max(watcher.maxViewPortHeight, viewPort.height());
                watcher.maxPostTotalHeight = Math.max(watcher.maxPostTotalHeight, viewHeight);

                if (!watcher.visible && watcher.visibleTime > GRACE_TIME) {
                    if (BuildVars.LOGS_ENABLED) {
                        Log.d(TAG, messageId + " " + groupId + " in viewport");
                    }
                    watcher.visible = true;
                }
            }

            if (!isVisibleInViewPort && watcher.visibleTime > 0 && (time - GRACE_TIME) > watcher.lastViewMillis) {
                if (watcher.visible) {
                    pendingMetrics.add(watcher.buildMetrics());
                }
                watchers.removeAt(a);
                if (BuildVars.LOGS_ENABLED) {
                    Log.d(TAG, messageId + " " + groupId + " out of viewport: " + watcher.visibleTime);
                }
            } else if (watcher.visible && watcher.visibleTime > MAX_WATCH_TIME) {
                pendingMetrics.add(watcher.buildMetrics());
                watchers.removeAt(a);
                if (BuildVars.LOGS_ENABLED) {
                    Log.d(TAG, messageId + " " + groupId + " out of time");
                }
            } else if (!watcher.visible && watcher.lastUpdateMillis != time && (watcher.groupId == 0 || groupedPosition == null)) {
                watchers.removeAt(a);
                if (BuildVars.LOGS_ENABLED) {
                    Log.d(TAG, messageId + " " + groupId + " out of screen");
                }
            }
        }

        if (!pendingMetrics.isEmpty() && pendingFlush == null) {
            AndroidUtilities.runOnUIThread(pendingFlush = this::flushImpl, FLUSH_TIME);
        }

        if (DRAW_DEBUG) {
            invalidate();
        }
    }

    private final LongSparseArray<MessageWatcher> watchers = new LongSparseArray<>();
    private final LongSparseArray<RectF> groupedPositions = new LongSparseArray<>();

    private static class MessageWatcher {
        public final int messageId;
        public final long groupId;
        private final RectF position = new RectF();
        private final long viewId;

        private long lastUpdateMillis;
        private long lastViewMillis;

        private long visibleTime;
        private long activeTime;
        private boolean visible;
        private float maxPostTotalHeight = 0;
        private float maxViewPortHeight = 0;

        private float seenTopPx = Float.MAX_VALUE;
        private float seenBottomPx = 0f;

        private MessageWatcher(int messageId, long groupId) {
            this.messageId = messageId;
            this.groupId = groupId;
            this.viewId = Utilities.random.nextLong();
        }

        public TLRPC.TL_inputMessageReadMetric buildMetrics() {
            final TLRPC.TL_inputMessageReadMetric metrics = new TLRPC.TL_inputMessageReadMetric();
            metrics.msg_id = messageId;
            metrics.view_id = viewId;
            metrics.time_in_view_ms = (int) visibleTime;
            metrics.active_time_in_view_ms = (int) activeTime;
            metrics.height_to_viewport_ratio_permille = getHeightToViewportRatioPermille();
            metrics.seen_range_ratio_permille = getSeenRangeRatioPermille();
            return metrics;
        }

        public int getHeightToViewportRatioPermille() {
            if (maxViewPortHeight == 0) {
                return 1000;
            }
            return Math.round((maxPostTotalHeight / maxViewPortHeight) * 1000);
        }

        public int getSeenRangeRatioPermille() {
            if (maxPostTotalHeight == 0 || seenTopPx > seenBottomPx) {
                return 0;
            }
            return Math.round(((seenBottomPx - seenTopPx) / maxPostTotalHeight) * 1000);
        }
    }

    private final Runnable scheduledCheckRunnable = this::scheduledCheck;

    private void scheduledCheck() {
        AndroidUtilities.runOnUIThread(scheduledCheckRunnable, REPEAT_PERIOD);
        processCurrentFrame();
    }


    /* Attach */

    private ViewTreeObserver observer;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastTime = 0;
        observer = getViewTreeObserver();
        observer.addOnPreDrawListener(this);
        observer.addOnGlobalLayoutListener(this);
        observer.addOnScrollChangedListener(this);
        AndroidUtilities.runOnUIThread(scheduledCheckRunnable, REPEAT_PERIOD);
        if (BuildVars.LOGS_ENABLED) {
            Log.d(TAG, "attach");
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (observer != null && observer.isAlive()) {
            observer.removeOnPreDrawListener(this);
            observer.removeOnGlobalLayoutListener(this);
            observer.removeOnScrollChangedListener(this);
        }
        observer = null;
        lastTime = 0;
        AndroidUtilities.cancelRunOnUIThread(scheduledCheckRunnable);
        if (BuildVars.LOGS_ENABLED) {
            Log.d(TAG, "detach");
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        invalidateViewPort();
    }

    private boolean updateInNextDraw;

    @Override
    public void onScrollChanged() {
        updateInNextDraw = true;
    }

    @Override
    public void onGlobalLayout() {
        updateInNextDraw = true;
    }

    @Override
    public boolean onPreDraw() {
        if (updateInNextDraw) {
            processCurrentFrame();
            updateInNextDraw = false;
        }
        return true;
    }

    /* Debug Draw */

    private TextPaint tmpTextPaint;

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (!DRAW_DEBUG) {
            return;
        }

        if (tmpTextPaint == null) {
            tmpTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            tmpTextPaint.setColor(0xFF0000FF);
            tmpTextPaint.setTextSize(dp(10));
        }

        super.onDraw(canvas);
        canvas.drawRect(viewPort, Theme.DEBUG_RED_STROKE);

        for (int a = 0, N = watchers.size(); a < N; a++) {
            final MessageWatcher watcher = watchers.valueAt(a);

            canvas.drawRect(watcher.position, Theme.DEBUG_GREEN_STROKE);

            canvas.save();

            float y = MathUtils.clamp(watcher.position.centerY() - dp(20), viewPort.top - dp(40), viewPort.bottom);
            y = MathUtils.clamp(y, watcher.position.top, watcher.position.bottom - dp(40));

            canvas.translate(watcher.position.left, y);
            canvas.drawRect(0, 0, watcher.position.width(), dp(40), Theme.DEBUG_GREEN_B0);
            canvas.translate(dp(10), dp(16));

            canvas.save();
            canvas.drawText("time_in_view_ms: " + watcher.visibleTime, 0, 0, tmpTextPaint);
            canvas.translate(0, dp(16));
            canvas.drawText("active_time_in_view_ms: " + watcher.activeTime, 0, 0, tmpTextPaint);
            canvas.restore();

            canvas.save();
            canvas.translate(getWidth() / 2f, 0);
            canvas.drawText("height_to_viewport_ratio_permille: " + watcher.getHeightToViewportRatioPermille(), 0, 0, tmpTextPaint);
            canvas.translate(0, dp(16));
            canvas.drawText("seen_range_ratio_permille: " + watcher.getSeenRangeRatioPermille(), 0, 0, tmpTextPaint);
            canvas.restore();

            canvas.restore();
        }
    }
}
