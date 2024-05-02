package org.telegram.ui.Stories;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Scroller;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.StaticLayoutEx;

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
    GradientDrawable gradientDrawable;

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
    private float textWidth;

    public void onCenteredImageTap() {

    }

    private int lastClosestPosition;

    public SelfStoriesPreviewView(Context context) {
        super(context);
        scroller = new Scroller(context, new OvershootInterpolator());
        gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.BLACK, 160)});
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        childPadding = AndroidUtilities.dp(8);
        viewH = (int) (AndroidUtilities.dp(180) / 1.2f);
        viewW = (int) ((viewH / 16f) * 9f);
        float textWidthLocal = viewW - AndroidUtilities.dp(8);
        topPadding = ((AndroidUtilities.dp(180) - viewH) / 2f) + AndroidUtilities.dp(20);
        updateScrollParams();
        if (scrollToPositionInLayout >= 0 && getMeasuredWidth() > 0) {
            lastClosestPosition = -1;
            scrollToPosition(scrollToPositionInLayout, false, false);
            scrollToPositionInLayout = -1;
        }
        if (textWidth != textWidthLocal) {
            textWidth  = textWidthLocal;
            for (int i = 0; i < lastDrawnImageReceivers.size(); i++) {
                lastDrawnImageReceivers.get(i).onBind(lastDrawnImageReceivers.get(i).position);
            }
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
                if (holder.layout != null) {
                    float alpha = 0.7f + 0.3f * k;//k;
                    gradientDrawable.setAlpha((int) (255 * alpha));
                    gradientDrawable.setBounds(
                            (int) holder.receiver.getImageX(), (int) (holder.receiver.getImageY2() - AndroidUtilities.dp(24)), (int) holder.receiver.getImageX2(), (int) holder.receiver.getImageY2() + 2);
                    gradientDrawable.draw(canvas);
                    canvas.save();
                    canvas.translate(holder.receiver.getCenterX() - textWidth / 2f, holder.receiver.getImageY2() - AndroidUtilities.dp(8) - holder.layout.getHeight());
                    holder.paint.setAlpha((int) (255 * alpha));
                    holder.layout.draw(canvas);
                    canvas.restore();
                }
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

        for (int i = 0; i < lastDrawnImageReceivers.size(); i++) {
            lastDrawnImageReceivers.get(i).onBind(lastDrawnImageReceivers.get(i).position);
        }
    }

    public int getClosestPosition() {
        return lastClosestPosition;
    }

    public ImageHolder getCenteredImageReciever() {
        for (int i = 0; i < lastDrawnImageReceivers.size(); i++) {
            if (lastDrawnImageReceivers.get(i).position == lastClosestPosition) {
                return lastDrawnImageReceivers.get(i);
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

    public class ImageHolder {
        ImageReceiver receiver = new ImageReceiver(SelfStoriesPreviewView.this);
        int position;
        StaticLayout layout;
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        SelfStoryViewsView.StoryItemInternal storyItem;

        public ImageHolder() {
            receiver.setAllowLoadingOnAttachedOnly(true);
            receiver.setRoundRadius(AndroidUtilities.dp(6));
            paint.setColor(Color.WHITE);
            paint.setTextSize(AndroidUtilities.dp(13));
        }

        void onBind(int position) {
            if (position < 0 || position >= storyItems.size()) return;
            storyItem = storyItems.get(position);
            if (isAttachedToWindow) {
                receiver.onAttachedToWindow();
            }
            if (storyItem.storyItem != null) {
                StoriesUtilities.setImage(receiver, storyItem.storyItem);
            } else {
                StoriesUtilities.setImage(receiver, storyItem.uploadingStory);
            }
            updateLayout();
        }

        private void updateLayout() {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
            if (storyItem.storyItem != null) {
                formatCounterText(spannableStringBuilder, storyItem.storyItem.views, false);
            }
            if (spannableStringBuilder.length() == 0) {
                layout = null;
            } else {
                layout = StaticLayoutEx.createStaticLayout(spannableStringBuilder, paint, (int) (textWidth + 1), Layout.Alignment.ALIGN_CENTER, 1.0f, 0f, false, null, Integer.MAX_VALUE, 1);
                if (layout.getLineCount() > 1) {
                    spannableStringBuilder = new SpannableStringBuilder("");
                    formatCounterText(spannableStringBuilder, storyItem.storyItem.views, true);
                    layout = StaticLayoutEx.createStaticLayout(spannableStringBuilder, paint, (int) (textWidth + 1), Layout.Alignment.ALIGN_CENTER, 1.0f, 0f, false, null, Integer.MAX_VALUE, 2);
                }
            }
        }

        void onDetach() {
            receiver.onDetachedFromWindow();
        }

        public void draw(Canvas canvas, float alpha, float scale, int x, int y, int width, int height) {
            receiver.setImageCoords(x, y, width, height);
            receiver.setAlpha(alpha);
            receiver.draw(canvas);
            receiver.setAlpha(1f);
            if (layout != null) {
                paint.setAlpha((int) (255 * alpha));
                gradientDrawable.setAlpha((int) (255 * alpha));
                gradientDrawable.setBounds(
                        (int) receiver.getImageX(), (int) (receiver.getImageY2() - AndroidUtilities.dp(24) * scale), (int) receiver.getImageX2(), (int) receiver.getImageY2() + 2);
                gradientDrawable.draw(canvas);
                canvas.save();
                canvas.scale(scale, scale, receiver.getCenterX(), receiver.getImageY2() - AndroidUtilities.dp(8) * scale);
                canvas.translate(receiver.getCenterX() - textWidth / 2f, receiver.getImageY2() - AndroidUtilities.dp(8) * scale - layout.getHeight());
                layout.draw(canvas);
                canvas.restore();
            }
        }

        public void update() {
            updateLayout();
        }
    }

    private void formatCounterText(SpannableStringBuilder spannableStringBuilder, TL_stories.StoryViews storyViews, boolean twoLines) {
        int count = storyViews == null ? 0 : storyViews.views_count;
        if (count > 0) {
            spannableStringBuilder.append("d");
            spannableStringBuilder.setSpan(new ColoredImageSpan(R.drawable.msg_views), spannableStringBuilder.length() - 1, spannableStringBuilder.length(), 0);
            spannableStringBuilder.append(" ").append(
                    AndroidUtilities.formatWholeNumber(count, 0)
            );
            if (storyViews != null && storyViews.reactions_count > 0) {
                spannableStringBuilder.append(twoLines ? "\n" : "  ");
                spannableStringBuilder.append("d");
                spannableStringBuilder.setSpan(new ColoredImageSpan(R.drawable.mini_like_filled), spannableStringBuilder.length() - 1, spannableStringBuilder.length(), 0);
                spannableStringBuilder.append(" ").append(
                        AndroidUtilities.formatWholeNumber(storyViews.reactions_count, 0)
                );
            }
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

    public void update() {
        for (int i = 0; i < lastDrawnImageReceivers.size(); i++) {
            lastDrawnImageReceivers.get(i).update();
        }
    }
}

