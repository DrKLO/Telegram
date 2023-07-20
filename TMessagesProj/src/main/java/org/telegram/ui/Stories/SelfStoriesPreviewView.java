package org.telegram.ui.Stories;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Scroller;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;

public abstract class SelfStoriesPreviewView extends View {

    public int imagesFromY;
    public int imagesFromW;
    public int imagesFromH;
    Scroller scroller;
    float scrollX;
    float minScroll;
    float maxScroll;

    int childPadding;
    private int viewH;
    private int viewW;
    private boolean isAttachedToWindow;
    private int scrollToPositionInLayout = -1;
    float topPadding;
    float progressToOpen;

    ArrayList<SelfStoryViewsView.StoryItemInternal> storyItems = new ArrayList<>();

    ArrayList<ImageHolder> imageReceiversTmp = new ArrayList<>();
    ArrayList<ImageHolder> lastDrawnImageReceivers = new ArrayList<>();

    GestureDetector gestureDetector = new GestureDetector(new GestureDetector.OnGestureListener() {
        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            scroller.abortAnimation();
            if (scrollAnimator != null) {
                scrollAnimator.removeAllListeners();
                scrollAnimator.cancel();
                scrollAnimator = null;
            }
            checkScroll = false;
            onDragging();
            return true;
        }

        @Override
        public void onShowPress(@NonNull MotionEvent e) {

        }

        @Override
        public boolean onSingleTapUp(@NonNull MotionEvent e) {
            for (int i = 0; i < lastDrawnImageReceivers.size(); i++) {
                ImageHolder holder = lastDrawnImageReceivers.get(i);
                if (lastDrawnImageReceivers.get(i).receiver.getDrawRegion().contains(e.getX(), e.getY())) {
                    if (lastClosestPosition != holder.position) {
                        scrollToPosition(holder.position, true, false);
                    } else {
                        onCenteredImageTap();
                    }
                }
            }
            return false;
        }

        @Override
        public boolean onScroll(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
            scrollX += distanceX;
            if (scrollX < minScroll) {
                scrollX = minScroll;
            }
            if (scrollX > maxScroll) {
                scrollX = maxScroll;
            }
            invalidate();
            return false;
        }

        @Override
        public void onLongPress(@NonNull MotionEvent e) {

        }

        @Override
        public boolean onFling(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            scroller.fling((int) scrollX, 0, (int) -velocityX, 0, (int) minScroll, (int) maxScroll, 0, 0);
            invalidate();
            return false;
        }
    });

    public void onCenteredImageTap() {

    }

    private int lastClosestPosition;

    public SelfStoriesPreviewView(Context context) {
        super(context);
        scroller = new Scroller(context, new OvershootInterpolator());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        childPadding = AndroidUtilities.dp(8);
        viewH = (int) (AndroidUtilities.dp(180) / 1.2f);
        viewW = (int) ((viewH / 16f) * 9f);
        topPadding = ((AndroidUtilities.dp(180) - viewH) / 2f) + AndroidUtilities.dp(20);
        updateScrollParams();
        if (scrollToPositionInLayout >= 0 && getMeasuredWidth() > 0) {
            lastClosestPosition = -1;
            scrollToPosition(scrollToPositionInLayout, false, false);
            scrollToPositionInLayout = -1;
        }
    }

    private void updateScrollParams() {
        minScroll = -(getMeasuredWidth() - viewW) / 2f;
        maxScroll = (viewW + childPadding) * storyItems.size() - childPadding - getMeasuredWidth() + (getMeasuredWidth() - viewW) / 2f;
    }

    boolean checkScroll;
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (scroller.computeScrollOffset()) {
//            if (Math.abs(scrollX - scroller.getCurrX()) < AndroidUtilities.dp(1)) {
//                scroller.abortAnimation();
//            } else {
                scrollX = scroller.getCurrX();
           // }
            invalidate();
            checkScroll = true;
        } else if (checkScroll) {
            scrollToClosest();
        }
        float cx = getMeasuredWidth() / 2f;
        imageReceiversTmp.clear();
        imageReceiversTmp.addAll(lastDrawnImageReceivers);
        lastDrawnImageReceivers.clear();
        int closestPositon = -1;
        float minDistance = Integer.MAX_VALUE;
        for (int i = 0; i < storyItems.size(); i++) {
            float x = -scrollX + (viewW + childPadding) * i;
            float viewCx = x + viewW / 2f;
            float distance = Math.abs(viewCx - cx);
            float s = 1f;
            float k = 0;
            if (distance < viewW) {
                k = 1f - Math.abs(viewCx - cx) / viewW;
                s = 1f + 0.2f * k;
            }
            if (closestPositon == -1 || distance < minDistance) {
                closestPositon = i;
                minDistance = distance;
            }
            if (viewCx - cx < 0) {
                x -= viewW * 0.1f * (1f - k);
            } else {
                x += viewW * 0.1f * (1f - k);
            }
            if (x > getMeasuredWidth() || x + viewW < 0) {
                continue;
            }
            ImageHolder holder = findOrCreateImageReceiver(i, imageReceiversTmp);
            float w = viewW * s;
            float h = viewH * s;
            float imageX = x - (w - viewW) / 2f;
            float imageY = topPadding - (h - viewH) / 2f;
            if (progressToOpen == 0 || i == lastClosestPosition) {
                holder.receiver.setImageCoords(imageX, imageY, w, h);
            } else {
                float fromX = (i - lastClosestPosition) * getMeasuredWidth();
                float fromY = imagesFromY;
                float fromH = imagesFromH;
                float fromW = imagesFromW;
                holder.receiver.setImageCoords(
                        AndroidUtilities.lerp(fromX, imageX, progressToOpen),
                        AndroidUtilities.lerp(fromY, imageY, progressToOpen),
                        AndroidUtilities.lerp(fromW, w, progressToOpen),
                        AndroidUtilities.lerp(fromH, h, progressToOpen)
                );
            }
            if (!(progressToOpen != 1f && i == lastClosestPosition)) {
                holder.receiver.draw(canvas);
            }
            lastDrawnImageReceivers.add(holder);
        }
        if (scrollAnimator == null && lastClosestPosition != closestPositon) {
            lastClosestPosition = closestPositon;
            onClosestPositionChanged(lastClosestPosition);
        }
        for (int i = 0; i < imageReceiversTmp.size(); i++) {
            imageReceiversTmp.get(i).onDetach();
        }
        imageReceiversTmp.clear();
        //  canvas.drawLine(getMeasuredWidth() / 2f, 0, getMeasuredWidth() / 2f, getMeasuredHeight(), new Paint());
    }

    abstract void onDragging();

    public void onClosestPositionChanged(int lastClosestPosition) {

    }

    private void scrollToClosest() {
        if (lastClosestPosition >= 0) {
            scrollToPosition(lastClosestPosition, true, true);
        }
    }

    private ImageHolder findOrCreateImageReceiver(int position, ArrayList<ImageHolder> imageReceivers) {
        for (int i = 0; i < imageReceivers.size(); i++) {
            //TODO change to id
            if (imageReceivers.get(i).position == position) {
                return imageReceivers.remove(i);
            }
        }
        ImageHolder imageHolder = new ImageHolder();
        imageHolder.onBind(position);
        imageHolder.position = position;
        return imageHolder;
    }

    ValueAnimator scrollAnimator;
    public void scrollToPosition(int p, boolean animated, boolean force) {
        if ((lastClosestPosition == p && !force) || getMeasuredHeight() <= 0) {
            return;
        }
        if (lastClosestPosition != p) {
            lastClosestPosition = p;
            onClosestPositionChanged(lastClosestPosition);
        }
        scroller.abortAnimation();
        checkScroll = false;
        if (scrollAnimator != null) {
            scrollAnimator.removeAllListeners();
            scrollAnimator.cancel();
            scrollAnimator = null;
        }
        if (!animated) {
            scrollX = -getMeasuredWidth() / 2f + viewW / 2f + (viewW + childPadding) * p;
            invalidate();
        } else {
            float newScroll = -getMeasuredWidth() / 2f + viewW / 2f + (viewW + childPadding) * p;
            if (newScroll == scrollX) {
                return;
            }
            scrollAnimator = ValueAnimator.ofFloat(scrollX, newScroll);
            scrollAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                    scrollX = (float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            scrollAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    scrollAnimator = null;
                }
            });
            scrollAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            scrollAnimator.setDuration(200);
            scrollAnimator.start();
        }
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        if ((event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) && scroller.isFinished()) {
            scrollToClosest();
        }
        return true;
    }

    public void setItems(ArrayList<SelfStoryViewsView.StoryItemInternal> storyItems, int position) {
        this.storyItems.clear();
        this.storyItems.addAll(storyItems);
        updateScrollParams();
        if (getMeasuredHeight() > 0) {
            scrollToPosition(position, false, false);
        } else {
            scrollToPositionInLayout = position;
        }
    }

    public int getClosestPosition() {
        return lastClosestPosition;
    }

    public ImageReceiver getCenteredImageReciever() {
        for (int i = 0; i < lastDrawnImageReceivers.size(); i++) {
            if (lastDrawnImageReceivers.get(i).position == lastClosestPosition) {
                return lastDrawnImageReceivers.get(i).receiver;
            }
        }
        return null;
    }

    public void abortScroll() {
        scroller.abortAnimation();
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
            scrollAnimator = null;
        }
        scrollToPosition(lastClosestPosition, false, true);
    }

    public float getFinalHeight() {
        return AndroidUtilities.dp(180);
    }

    public void setProgressToOpen(float progressToOpen) {
        if (this.progressToOpen == progressToOpen) {
            return;
        }
        this.progressToOpen = progressToOpen;
        invalidate();
    }

    public void scrollToPositionWithOffset(int position, float positionOffset) {
        scroller.abortAnimation();
        if (Math.abs(positionOffset) > 1) {
            return;
        }
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
            scrollAnimator = null;
        }
        float fromScrollX = -getMeasuredWidth() / 2f + viewW / 2f + (viewW + childPadding) * position;
        float progress = positionOffset;
        float toScrollX;
        if (positionOffset > 0) {
            toScrollX = -getMeasuredWidth() / 2f + viewW / 2f + (viewW + childPadding) * (position + 1);
        } else {
            toScrollX = -getMeasuredWidth() / 2f + viewW / 2f + (viewW + childPadding) * (position - 1);
            progress = -positionOffset;
        }
        if (progress == 0) {
            scrollX = fromScrollX;
        } else {
            scrollX = AndroidUtilities.lerp(fromScrollX, toScrollX, progress);
        }
        checkScroll = false;
        invalidate();
    }

    private class ImageHolder {
        ImageReceiver receiver = new ImageReceiver(SelfStoriesPreviewView.this);
        int position;

        public ImageHolder() {
            receiver.setAllowLoadingOnAttachedOnly(true);
            receiver.setRoundRadius(AndroidUtilities.dp(6));
        }

        void onBind(int position) {
            SelfStoryViewsView.StoryItemInternal storyItem = storyItems.get(position);
            if (isAttachedToWindow) {
                receiver.onAttachedToWindow();
            }
            if (storyItem.storyItem != null) {
                StoriesUtilities.setImage(receiver, storyItem.storyItem);
            } else {
                StoriesUtilities.setImage(receiver, storyItem.uploadingStory);
            }

        }

        void onDetach() {
            receiver.onDetachedFromWindow();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        isAttachedToWindow = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        isAttachedToWindow = false;
        for (int i = 0; i < lastDrawnImageReceivers.size(); i++) {
            lastDrawnImageReceivers.get(i).onDetach();
        }
        lastDrawnImageReceivers.clear();
    }
}

