package org.telegram.ui.Components.conference.message;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.voip.GroupCallMessage;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;

public class GroupCallMessagesListView extends RecyclerView {
    private static final int FADE_HEIGHT = 16;

    private final GroupCallMessagesAdapter adapter;
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private RenderNode renderNode;
    private float renderNodeScale;
    private View blurRoot;
    private Delegate delegate;
    private GroupCallMessageCell.Delegate cellDelegate;

    public GroupCallMessagesListView(@NonNull Context context) {
        super(context);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        maskPaint.setShader(new LinearGradient(0, 0, 0, dp(FADE_HEIGHT),
            0x00000000, 0xFF000000, Shader.TileMode.CLAMP));

        setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, true) {
            @Override
            public boolean canScrollVertically() {
                return false;
            }
        });
        addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                outRect.top = dp(6);
            }
        });
        setAdapter(adapter = new GroupCallMessagesAdapter() {
            @NonNull
            @Override
            public GroupCallMessageCell.VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                GroupCallMessageCell.VH vh = super.onCreateViewHolder(parent, viewType);
                vh.cell.setRenderNode(blurRoot, renderNode, renderNodeScale);
                vh.cell.setDelegate(cellDelegate);
                return vh;
            }
        });

        DefaultItemAnimator itemAnimator = createItemAnimator();
        setItemAnimator(itemAnimator);
    }

    @NonNull
    private DefaultItemAnimator createItemAnimator() {
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected float animateByScale(View view) {
                return 0.6f;
            }

            @Override
            public void onAddFinished(RecyclerView.ViewHolder item) {
                super.onAddFinished(item);

                GroupCallMessage message = adapter.getMessage(item.getAdapterPosition());
                if (message != null && message.visibleReaction != null && item.itemView instanceof GroupCallMessageCell) {
                    if (delegate != null) {
                        delegate.showReaction((GroupCallMessageCell) item.itemView, message.visibleReaction);
                    }
                }
            }
        };
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(320);
        return itemAnimator;
    }

    private int visibleHeight;

    public void setVisibleHeight(int visibleHeight) {
        if (this.visibleHeight != visibleHeight) {
            this.visibleHeight = visibleHeight;
            invalidate();
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        final int top = getMeasuredHeight() - visibleHeight;
        final int fadeH = dp(FADE_HEIGHT);
        final int fadeY = top + fadeH;
        final int bottom = getMeasuredHeight();
        final int width = getMeasuredWidth();

        if (fadeY < getMinChildY()) {
            super.dispatchDraw(canvas);
            return;
        }

        final int save = canvas.saveLayer(0, top, width, fadeY, null);
        canvas.clipRect(0, top, width, fadeY);
        clipTop = top;
        clipBottom = fadeY;
        super.dispatchDraw(canvas);
        canvas.translate(0, top);
        canvas.drawRect(0, 0, width, fadeH, maskPaint);
        canvas.restoreToCount(save);

        canvas.save();
        canvas.clipRect(0, fadeY, width, bottom);
        clipTop = fadeY;
        clipBottom = getMeasuredHeight();
        super.dispatchDraw(canvas);
        canvas.restore();

        clipTop = Integer.MIN_VALUE;
        clipBottom = Integer.MIN_VALUE;
    }

    private int clipTop = Integer.MIN_VALUE;
    private int clipBottom = Integer.MIN_VALUE;

    @Override
    public boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (clipTop != Integer.MIN_VALUE && (child.getY() + child.getHeight()) < clipTop) {
            return true;
        }
        if (clipBottom != Integer.MIN_VALUE && (child.getY()) > clipBottom) {
            return true;
        }

        return super.drawChild(canvas, child, drawingTime);
    }

    private float getMinChildY() {
        float minY = Integer.MAX_VALUE;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            View v = getChildAt(a);
            if (v.getVisibility() == VISIBLE) {
                minY = Math.min(minY, v.getY());
            }
        }
        return minY;
    }

    public void setRenderNode(RenderNode renderNode, float scale) {
        this.renderNode = renderNode;
        this.renderNodeScale = scale;
    }

    public void setBlurRoot(View blurRoot) {
        this.blurRoot = blurRoot;
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void setClickCellDelegate(GroupCallMessageCell.Delegate delegate) {
        this.cellDelegate = delegate;
    }

    public interface Delegate {
        void showReaction(GroupCallMessageCell cell, ReactionsLayoutInBubble.VisibleReaction reaction);
    }

    @Override
    public void setTranslationY(float translationY) {
        if (getTranslationY() != translationY) {
            super.setTranslationY(translationY);
            invalidate();
            for (int a = 0, N = getChildCount(); a < N; a++) {
                getChildAt(a).invalidate();
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            boolean hitChild = false;
            final int x = (int) ev.getX();
            final int y = (int) ev.getY();
            final int top = getMeasuredHeight() - visibleHeight;
            if (y < top) {
                return false;
            }

            for (int a = 0, N = getChildCount(); a < N; a++) {
                View child = getChildAt(a);
                if (child instanceof GroupCallMessageCell) {
                    GroupCallMessageCell cell = (GroupCallMessageCell) child;
                    if (cell.getVisibility() == VISIBLE) {
                        if (cell.isInsideBubble(x - child.getX(), y - child.getY())) {
                            hitChild = true;
                            break;
                        }
                    }
                }
            }
            if (!hitChild) {
                return false;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    public void setGroupCall(int currentAccount, TLRPC.InputGroupCall call) {
        adapter.setGroupCall(currentAccount, call);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        adapter.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        adapter.detach();
    }
}
