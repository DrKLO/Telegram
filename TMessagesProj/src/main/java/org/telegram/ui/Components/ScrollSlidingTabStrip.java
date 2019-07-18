/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public class ScrollSlidingTabStrip extends HorizontalScrollView {

    public interface ScrollSlidingTabStripDelegate {
        void onPageSelected(int page);
    }

    private LinearLayout.LayoutParams defaultTabLayoutParams;
    private LinearLayout.LayoutParams defaultExpandLayoutParams;
    private LinearLayout tabsContainer;
    private ScrollSlidingTabStripDelegate delegate;

    private boolean shouldExpand;

    private int tabCount;

    private int currentPosition;
    private boolean animateFromPosition;
    private float startAnimationPosition;
    private float positionAnimationProgress;
    private long lastAnimationTime;

    private Paint rectPaint;

    private int indicatorColor = 0xff666666;
    private int underlineColor = 0x1a000000;
    private int indicatorHeight;

    private int scrollOffset = AndroidUtilities.dp(52);
    private int underlineHeight = AndroidUtilities.dp(2);
    private int dividerPadding = AndroidUtilities.dp(12);
    private int tabPadding = AndroidUtilities.dp(24);

    private int lastScrollX = 0;

    public ScrollSlidingTabStrip(Context context) {
        super(context);

        setFillViewport(true);
        setWillNotDraw(false);

        setHorizontalScrollBarEnabled(false);
        tabsContainer = new LinearLayout(context);
        tabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabsContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        addView(tabsContainer);

        rectPaint = new Paint();
        rectPaint.setAntiAlias(true);
        rectPaint.setStyle(Style.FILL);

        defaultTabLayoutParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(52), LayoutHelper.MATCH_PARENT);
        defaultExpandLayoutParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0F);
    }

    public void setDelegate(ScrollSlidingTabStripDelegate scrollSlidingTabStripDelegate) {
        delegate = scrollSlidingTabStripDelegate;
    }

    public void removeTabs() {
        tabsContainer.removeAllViews();
        tabCount = 0;
        currentPosition = 0;
        animateFromPosition = false;
    }

    public void selectTab(int num) {
        if (num < 0 || num >= tabCount) {
            return;
        }
        View tab = tabsContainer.getChildAt(num);
        tab.performClick();
    }

    public TextView addIconTabWithCounter(Drawable drawable) {
        final int position = tabCount++;
        FrameLayout tab = new FrameLayout(getContext());
        tab.setFocusable(true);
        tabsContainer.addView(tab);

        ImageView imageView = new ImageView(getContext());
        imageView.setImageDrawable(drawable);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        tab.setOnClickListener(v -> delegate.onPageSelected(position));
        tab.addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        tab.setSelected(position == currentPosition);

        TextView textView = new TextView(getContext());
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        textView.setTextColor(Theme.getColor(Theme.key_chat_emojiPanelBadgeText));
        textView.setGravity(Gravity.CENTER);
        textView.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(9), Theme.getColor(Theme.key_chat_emojiPanelBadgeBackground)));
        textView.setMinWidth(AndroidUtilities.dp(18));
        textView.setPadding(AndroidUtilities.dp(5), 0, AndroidUtilities.dp(5), AndroidUtilities.dp(1));
        tab.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 18, Gravity.TOP | Gravity.LEFT, 26, 6, 0, 0));

        return textView;
    }

    public ImageView addIconTab(Drawable drawable) {
        final int position = tabCount++;
        ImageView tab = new ImageView(getContext());
        tab.setFocusable(true);
        tab.setImageDrawable(drawable);
        tab.setScaleType(ImageView.ScaleType.CENTER);
        tab.setOnClickListener(v -> delegate.onPageSelected(position));
        tabsContainer.addView(tab);
        tab.setSelected(position == currentPosition);

        return tab;
    }

    public void addStickerTab(TLRPC.Chat chat) {
        final int position = tabCount++;
        FrameLayout tab = new FrameLayout(getContext());
        tab.setFocusable(true);
        tab.setOnClickListener(v -> delegate.onPageSelected(position));
        tabsContainer.addView(tab);
        tab.setSelected(position == currentPosition);
        BackupImageView imageView = new BackupImageView(getContext());
        imageView.setLayerNum(1);
        imageView.setRoundRadius(AndroidUtilities.dp(15));

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(14));
        avatarDrawable.setInfo(chat);
        imageView.setImage(ImageLocation.getForChat(chat, false), "50_50", avatarDrawable, chat);

        imageView.setAspectFit(true);
        tab.addView(imageView, LayoutHelper.createFrame(30, 30, Gravity.CENTER));
    }

    public View addStickerTab(TLObject thumb, TLRPC.Document sticker, Object parentObject) {
        final int position = tabCount++;
        FrameLayout tab = new FrameLayout(getContext());
        tab.setTag(thumb);
        tab.setTag(R.id.parent_tag, parentObject);
        tab.setTag(R.id.object_tag, sticker);
        tab.setFocusable(true);
        tab.setOnClickListener(v -> delegate.onPageSelected(position));
        tabsContainer.addView(tab);
        tab.setSelected(position == currentPosition);
        BackupImageView imageView = new BackupImageView(getContext());
        imageView.setLayerNum(1);
        imageView.setAspectFit(true);
        tab.addView(imageView, LayoutHelper.createFrame(30, 30, Gravity.CENTER));

        return tab;
    }

    public void updateTabStyles() {
        for (int i = 0; i < tabCount; i++) {
            View v = tabsContainer.getChildAt(i);
            if (shouldExpand) {
                v.setLayoutParams(defaultExpandLayoutParams);
            } else {
                v.setLayoutParams(defaultTabLayoutParams);
            }
        }
    }

    private void scrollToChild(int position) {
        if (tabCount == 0 || tabsContainer.getChildAt(position) == null) {
            return;
        }
        int newScrollX = tabsContainer.getChildAt(position).getLeft();
        if (position > 0) {
            newScrollX -= scrollOffset;
        }
        int currentScrollX = getScrollX();
        if (newScrollX != lastScrollX) {
            if (newScrollX < currentScrollX) {
                lastScrollX = newScrollX;
                smoothScrollTo(lastScrollX, 0);
            } else if (newScrollX + scrollOffset > currentScrollX + getWidth() - scrollOffset * 2) {
                lastScrollX = newScrollX - getWidth() + scrollOffset * 3;
                smoothScrollTo(lastScrollX, 0);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        setImages();
    }

    public void setImages() {
        int tabSize = AndroidUtilities.dp(52);
        int start = getScrollX() / tabSize;
        int end = Math.min(tabsContainer.getChildCount(), start + (int) Math.ceil(getMeasuredWidth() / (float) tabSize) + 1);

        for (int a = start; a < end; a++) {
            View child = tabsContainer.getChildAt(a);
            Object object = child.getTag();
            Object parentObject = child.getTag(R.id.parent_tag);
            TLRPC.Document sticker = (TLRPC.Document) child.getTag(R.id.object_tag);
            ImageLocation imageLocation;

            if (object instanceof TLRPC.Document) {
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
                imageLocation = ImageLocation.getForDocument(thumb, sticker);
            } else if (object instanceof TLRPC.PhotoSize) {
                TLRPC.PhotoSize thumb = (TLRPC.PhotoSize) object;
                imageLocation = ImageLocation.getForSticker(thumb, sticker);
            } else {
                continue;
            }
            if (imageLocation == null) {
                continue;
            }
            BackupImageView imageView = (BackupImageView) ((FrameLayout) child).getChildAt(0);
            if (object instanceof TLRPC.Document && MessageObject.isAnimatedStickerDocument(sticker)) {
                imageView.setImage(ImageLocation.getForDocument(sticker), "30_30", imageLocation, null, 0, parentObject);
            } else if (imageLocation.lottieAnimation) {
                imageView.setImage(imageLocation, "30_30", "tgs", null, parentObject);
            } else {
                imageView.setImage(imageLocation, null, "webp", null, parentObject);
            }
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);

        int tabSize = AndroidUtilities.dp(52);
        int oldStart = oldl / tabSize;
        int newStart = l / tabSize;

        int count = (int) Math.ceil(getMeasuredWidth() / (float) tabSize) + 1;
        int start = Math.max(0, Math.min(oldStart, newStart));
        int end = Math.min(tabsContainer.getChildCount(), Math.max(oldStart, newStart) + count);

        for (int a = start; a < end; a++) {
            View child = tabsContainer.getChildAt(a);
            if (child == null) {
                continue;
            }
            Object object = child.getTag();
            Object parentObject = child.getTag(R.id.parent_tag);
            TLRPC.Document sticker = (TLRPC.Document) child.getTag(R.id.object_tag);
            ImageLocation imageLocation;
            if (object instanceof TLRPC.Document) {
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
                imageLocation = ImageLocation.getForDocument(thumb, sticker);
            } else if (object instanceof TLRPC.PhotoSize) {
                TLRPC.PhotoSize thumb = (TLRPC.PhotoSize) object;
                imageLocation = ImageLocation.getForSticker(thumb, sticker);
            } else {
                continue;
            }
            if (imageLocation == null) {
                continue;
            }
            BackupImageView imageView = (BackupImageView) ((FrameLayout) child).getChildAt(0);
            if (a < newStart || a >= newStart + count) {
                imageView.setImageDrawable(null);
            } else {
                if (object instanceof TLRPC.Document && MessageObject.isAnimatedStickerDocument(sticker)) {
                    imageView.setImage(ImageLocation.getForDocument(sticker), "30_30", imageLocation, null, 0, parentObject);
                } else if (imageLocation.lottieAnimation) {
                    imageView.setImage(imageLocation, "30_30", "tgs", null, parentObject);
                } else {
                    imageView.setImage(imageLocation, null, "webp", null, parentObject);
                }
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (isInEditMode() || tabCount == 0) {
            return;
        }

        final int height = getHeight();

        if (underlineHeight > 0) {
            rectPaint.setColor(underlineColor);
            canvas.drawRect(0, height - underlineHeight, tabsContainer.getWidth(), height, rectPaint);
        }

        if (indicatorHeight >= 0) {
            View currentTab = tabsContainer.getChildAt(currentPosition);
            float lineLeft = 0;
            int width = 0;
            if (currentTab != null) {
                lineLeft = currentTab.getLeft();
                width = currentTab.getMeasuredWidth();
            }
            if (animateFromPosition) {
                long newTime = SystemClock.uptimeMillis();
                long dt = newTime - lastAnimationTime;
                lastAnimationTime = newTime;

                positionAnimationProgress += dt / 150.0f;
                if (positionAnimationProgress >= 1.0f) {
                    positionAnimationProgress = 1.0f;
                    animateFromPosition = false;
                }
                lineLeft = startAnimationPosition + (lineLeft - startAnimationPosition) * CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(positionAnimationProgress);
                invalidate();
            }

            rectPaint.setColor(indicatorColor);
            if (indicatorHeight == 0) {
                canvas.drawRect(lineLeft, 0, lineLeft + width, height, rectPaint);
            } else {
                canvas.drawRect(lineLeft, height - indicatorHeight, lineLeft + width, height, rectPaint);
            }
        }
    }

    public void setShouldExpand(boolean value) {
        shouldExpand = value;
        requestLayout();
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public void cancelPositionAnimation() {
        animateFromPosition = false;
        positionAnimationProgress = 1.0f;
    }

    public void onPageScrolled(int position, int first) {
        if (currentPosition == position) {
            return;
        }

        View currentTab = tabsContainer.getChildAt(currentPosition);
        if (currentTab != null) {
            startAnimationPosition = currentTab.getLeft();
            positionAnimationProgress = 0.0f;
            animateFromPosition = true;
            lastAnimationTime = SystemClock.uptimeMillis();
        } else {
            animateFromPosition = false;
        }
        currentPosition = position;
        if (position >= tabsContainer.getChildCount()) {
            return;
        }
        positionAnimationProgress = 0.0f;
        for (int a = 0; a < tabsContainer.getChildCount(); a++) {
            tabsContainer.getChildAt(a).setSelected(a == position);
        }
        if (first == position && position > 1) {
            scrollToChild(position - 1);
        } else {
            scrollToChild(position);
        }
        invalidate();
    }

    public void setIndicatorHeight(int value) {
        indicatorHeight = value;
        invalidate();
    }

    public void setIndicatorColor(int value) {
        indicatorColor = value;
        invalidate();
    }

    public void setUnderlineColor(int value) {
        underlineColor = value;
        invalidate();
    }

    public void setUnderlineColorResource(int resId) {
        underlineColor = getResources().getColor(resId);
        invalidate();
    }

    public void setUnderlineHeight(int value) {
        underlineHeight = value;
        invalidate();
    }
}
