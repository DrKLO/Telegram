package org.telegram.ui.Components.Premium;

import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class PremiumStickersPreviewRecycler extends RecyclerListView implements NotificationCenter.NotificationCenterDelegate, PagerHeaderView {

    private final ArrayList<TLRPC.Document> premiumStickers = new ArrayList<>();
    LinearLayoutManager layoutManager;
    boolean firstMeasure = true;
    boolean firstDraw = true;
    private final int currentAccount;

    boolean hasSelectedView;
    boolean haptic;
    Runnable autoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!autoPlayEnabled) {
                return;
            }
            if (!sortedView.isEmpty()) {
                StickerView stickerView = sortedView.get(sortedView.size() - 1);
                int adapterPosition = getChildAdapterPosition(stickerView);
                if (adapterPosition >= 0) {
                    adapterPosition++;
                    View view = layoutManager.findViewByPosition(adapterPosition);
                    if (view != null) {
                        haptic = false;
                        drawEffectForView(view, true);
                        smoothScrollBy(0, view.getTop() - (getMeasuredHeight() - view.getMeasuredHeight()) / 2, AndroidUtilities.overshootInterpolator);
                    }
                }
            }
            scheduleAutoScroll();
        }
    };

    CubicBezierInterpolator interpolator = new CubicBezierInterpolator(0, 0.5f, 0.5f, 1f);
    ArrayList<StickerView> sortedView = new ArrayList<>();
    Comparator<StickerView> comparator = (o1, o2) -> (int) (o1.progress * 100 - o2.progress * 100);

    View oldSelectedView;
    private boolean checkEffect;
    private int size;
    int selectStickerOnNextLayout = -1;

    public PremiumStickersPreviewRecycler(Context context, int currentAccount) {
        super(context);
        this.currentAccount = currentAccount;
        setLayoutManager(layoutManager = new LinearLayoutManager(context));
        setAdapter(new Adapter());
        setClipChildren(false);

        setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (recyclerView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING) {
                    drawEffectForView(null, true);
                }
                invalidate();
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    haptic = true;
                }
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    StickerView scrollToView = null;
                    for (int i = 0; i < recyclerView.getChildCount(); i++) {
                        StickerView view = (StickerView) getChildAt(i);
                        if (scrollToView == null || view.progress > scrollToView.progress) {
                            scrollToView = view;
                        }
                    }
                    if (scrollToView != null) {
                        drawEffectForView(scrollToView, true);
                        haptic = false;
                        smoothScrollBy(0, scrollToView.getTop() - (getMeasuredHeight() - scrollToView.getMeasuredHeight()) / 2, AndroidUtilities.overshootInterpolator);
                    }
                    scheduleAutoScroll();
                } else {
                    AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
                }
            }
        });

        setOnItemClickListener((view, position) -> {
            if (view != null) {
                drawEffectForView(view, true);
                haptic = false;
                smoothScrollBy(0, view.getTop() - (getMeasuredHeight() - view.getMeasuredHeight()) / 2, AndroidUtilities.overshootInterpolator);
            }
        });

        MediaDataController.getInstance(currentAccount).preloadPremiumPreviewStickers();
        setStickers();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if (MeasureSpec.getSize(heightSpec) > MeasureSpec.getSize(widthSpec)) {
            size = MeasureSpec.getSize(widthSpec);
        } else {
            size = MeasureSpec.getSize(heightSpec);
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    private void scheduleAutoScroll() {
        if (!autoPlayEnabled) {
            return;
        }
        AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
        AndroidUtilities.runOnUIThread(autoScrollRunnable, 2700);
    }

    private void drawEffectForView(View view, boolean animated) {
        hasSelectedView = view != null;
        for (int i = 0; i < getChildCount(); i++) {
            StickerView child = (StickerView) getChildAt(i);
            if (child == view) {
                child.setDrawImage(true, true, animated);
            } else {
                child.setDrawImage(!hasSelectedView, false, animated);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (firstMeasure && !premiumStickers.isEmpty() && getChildCount() > 0) {
            firstMeasure = false;
            AndroidUtilities.runOnUIThread(() -> {
                int startPosition = Integer.MAX_VALUE >> 1;
                startPosition -= startPosition % premiumStickers.size();
                layoutManager.scrollToPositionWithOffset(selectStickerOnNextLayout = startPosition, (getMeasuredHeight() - getChildAt(0).getMeasuredHeight()) >> 1);
                drawEffectForView(null, false);
            });
        }
        if (selectStickerOnNextLayout > 0) {
            ViewHolder holder = findViewHolderForAdapterPosition(selectStickerOnNextLayout);
            if (holder != null) {
                drawEffectForView(holder.itemView, false);
            }
            selectStickerOnNextLayout = -1;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!isVisible) {
            return;
        }
        sortedView.clear();
        for (int i = 0; i < getChildCount(); i++) {
            StickerView child = (StickerView) getChildAt(i);
            float cY = (getMeasuredHeight() >> 1) + child.getMeasuredHeight();
            float p = (child.getTop() + child.getMeasuredHeight() + (child.getMeasuredHeight() >> 1)) / cY;
            if (p > 1) {
                p = 2 - p;
            }
            p = Utilities.clamp(p, 1f, 0f);
            child.progress = p;
            child.view.setTranslationX(-getMeasuredWidth() * 2f * (1f - interpolator.getInterpolation(p)));
            sortedView.add(child);
        }
        Collections.sort(sortedView, comparator);
        if ((firstDraw || checkEffect) && sortedView.size() > 0 && !premiumStickers.isEmpty()) {
            drawEffectForView(oldSelectedView = sortedView.get(sortedView.size() - 1), !firstDraw);
            firstDraw = false;
            checkEffect = false;
        } else if (oldSelectedView != sortedView.get(sortedView.size() - 1)) {
            oldSelectedView = sortedView.get(sortedView.size() - 1);
            if (haptic) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
        }
        for (int i = 0; i < sortedView.size(); i++) {
            canvas.save();
            canvas.translate(sortedView.get(i).getX(), sortedView.get(i).getY());
            sortedView.get(i).draw(canvas);
            canvas.restore();
        }
    }

    @Override
    public boolean drawChild(Canvas canvas, View child, long drawingTime) {
        return true;
    }

    boolean isVisible;

    @Override
    public void setOffset(float translationX) {
        float p = Math.abs(translationX / (float) getMeasuredWidth());
        boolean localVisible = p < 1f;
        if (isVisible != localVisible) {
            isVisible = localVisible;
            invalidate();
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = new StickerView(parent.getContext());
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (premiumStickers.isEmpty()) {
                return;
            }
            StickerView stickerView = (StickerView) holder.itemView;
            stickerView.setSticker(premiumStickers.get(position % premiumStickers.size()));
            stickerView.setDrawImage(!hasSelectedView, false, false);
        }

        @Override
        public int getItemCount() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.premiumStickersPreviewLoaded);
        scheduleAutoScroll();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.premiumStickersPreviewLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.premiumStickersPreviewLoaded) {
            setStickers();
        }
    }

    private void setStickers() {
        premiumStickers.clear();
        premiumStickers.addAll(MediaDataController.getInstance(currentAccount).premiumPreviewStickers);
        getAdapter().notifyDataSetChanged();
        invalidate();
    }

    private class StickerView extends FrameLayout {

        public float progress;
        View view;
        ImageReceiver centerImage;
        ImageReceiver effectImage;
        boolean drawEffect;
        boolean animateImage = true;
        private float effectProgress;
        private float animateImageProgress;
        TLRPC.Document document;
        boolean update;

        public StickerView(Context context) {
            super(context);
            view = new View(context) {

                @Override
                public void draw(Canvas canvas) {
                    super.draw(canvas);
                    if (update) {
                        SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 0.5f);
                        centerImage.setImage(ImageLocation.getForDocument(document), null, svgThumb, "webp", null, 1);
                        if (MessageObject.isPremiumSticker(document)) {
                            effectImage.setImage(ImageLocation.getForDocument(MessageObject.getPremiumStickerAnimation(document), document), "140_140", null, null, "tgs", null, 1);
                        }
                    }

                    if (drawEffect) {
                        if (effectProgress == 0f) {
                            effectProgress = 1f;
                            if (effectImage.getLottieAnimation() != null) {
                                effectImage.getLottieAnimation().setCurrentFrame(0, false);
                            }
                        }
                        if (effectImage.getLottieAnimation() != null) {
                            effectImage.getLottieAnimation().start();
                        }
                        if (effectImage.getLottieAnimation() != null && effectImage.getLottieAnimation().isLastFrame() && autoPlayEnabled) {
                            AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
                            AndroidUtilities.runOnUIThread(autoScrollRunnable, 0);
                        }
                    } else {
                        if (effectImage.getLottieAnimation() != null) {
                            effectImage.getLottieAnimation().stop();
                        }
                    }

                    if (animateImage) {
                        if (centerImage.getLottieAnimation() != null) {
                            centerImage.getLottieAnimation().start();
                        }
                    } else {
                        if (centerImage.getLottieAnimation() != null) {
                            centerImage.getLottieAnimation().stop();
                        }
                    }

                    if (animateImage && animateImageProgress != 1f) {
                        animateImageProgress += 16f / 150f;
                        invalidate();
                    } else if (!animateImage && animateImageProgress != 0) {
                        animateImageProgress -= 16f / 150f;
                        invalidate();
                    }
                    animateImageProgress = Utilities.clamp(animateImageProgress, 1f, 0f);

                    if (drawEffect && effectProgress != 1f) {
                        effectProgress += 16f / 150f;
                        invalidate();
                    } else if (!drawEffect && effectProgress != 0) {
                        effectProgress -= 16f / 150f;
                        invalidate();
                    }
                    effectProgress = Utilities.clamp(effectProgress, 1f, 0f);

                    float smallImageSize = PremiumStickersPreviewRecycler.this.size * 0.45f;
                    float size = smallImageSize * 1.499267f;
                    float x = getMeasuredWidth() - size;
                    float y = (getMeasuredHeight() - size) / 2f;
                    centerImage.setImageCoords(size - smallImageSize - size * 0.02f + x, (size - smallImageSize) / 2f + y, smallImageSize, smallImageSize);
                    centerImage.setAlpha(0.3f + 0.7f * animateImageProgress);
                    centerImage.draw(canvas);

                    if (effectProgress != 0) {
                        effectImage.setImageCoords(x, y, size, size);
                        effectImage.setAlpha(effectProgress);
                        effectImage.draw(canvas);
                    }
                }
            };
            centerImage = new ImageReceiver(view);
            effectImage = new ImageReceiver(view);
            centerImage.setAllowStartAnimation(false);
            effectImage.setAllowStartAnimation(false);
            setClipChildren(false);
            addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int size = (int) (PremiumStickersPreviewRecycler.this.size * 0.6f);
            view.getLayoutParams().width = view.getLayoutParams().height = size - AndroidUtilities.dp(16);
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (size * 0.7f), MeasureSpec.EXACTLY));
        }

        public void setSticker(TLRPC.Document document) {
            this.document = document;
            update = true;
        }

        public void setDrawImage(boolean animateImage, boolean drawEffect, boolean animated) {
            if (this.drawEffect != drawEffect) {
                this.drawEffect = drawEffect;
                if (!animated) {
                    effectProgress = drawEffect ? 1f : 0f;
                }
                view.invalidate();
            }
            if (this.animateImage != animateImage) {
                this.animateImage = animateImage;
                if (!animated) {
                    animateImageProgress = animateImage ? 1f : 0f;
                }
                view.invalidate();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            centerImage.onAttachedToWindow();
            effectImage.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            centerImage.onDetachedFromWindow();
            effectImage.onDetachedFromWindow();
        }
    }

    boolean autoPlayEnabled;

    public void setAutoPlayEnabled(boolean b) {
        if (autoPlayEnabled != b) {
            autoPlayEnabled = b;
            if (autoPlayEnabled) {
                scheduleAutoScroll();
                checkEffect = true;
                invalidate();
            } else {
                AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
                drawEffectForView(null, true);
            }
        }
    }
}
