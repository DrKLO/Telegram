/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Property;
import android.util.SparseIntArray;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class FilterTabsView extends FrameLayout {

    public int getCurrentTabStableId() {
        return positionToStableId.get(currentPosition, -1);
    }

    public int getStableId(int selectedType) {
        return positionToStableId.get(selectedType, -1);
    }

    public void selectTabWithStableId(int stableId) {
        for (int i = 0; i < tabs.size(); i++) {
            if (positionToStableId.get(i, -1) == stableId) {
                currentPosition = i;
                selectedTabId = positionToId.get(i);
                break;
            }
        }
    }

    public interface FilterTabsViewDelegate {
        void onPageSelected(Tab tab, boolean forward);

        void onPageScrolled(float progress);

        void onSamePageSelected();

        int getTabCounter(int tabId);

        boolean didSelectTab(TabView tabView, boolean selected);

        boolean isTabMenuVisible();

        void onDeletePressed(int id);

        void onPageReorder(int fromId, int toId);

        boolean canPerformActions();
    }

    public class Tab {
        public int id;
        public String title;
        public int titleWidth;
        public int counter;
        public boolean isDefault;
        public boolean isLocked;

        public Tab(int i, String t) {
            id = i;
            title = t;
        }

        public int getWidth(boolean store) {
            int width = titleWidth = (int) Math.ceil(textPaint.measureText(title));
            int c;
            if (store) {
                c = delegate.getTabCounter(id);
                if (c < 0) {
                    c = 0;
                }
                if (store) {
                    counter = c;
                }
            } else {
                c = counter;
            }
            if (c > 0) {
                String counterText = String.format("%d", c);
                int counterWidth = (int) Math.ceil(textCounterPaint.measureText(counterText));
                int countWidth = Math.max(AndroidUtilities.dp(10), counterWidth) + AndroidUtilities.dp(10);
                width += countWidth + AndroidUtilities.dp(6);
            }
            return Math.max(AndroidUtilities.dp(40), width);
        }

        public boolean setTitle(String newTitle) {
            if (TextUtils.equals(title, newTitle)) {
                return false;
            }
            title = newTitle;
            return true;
        }
    }

    public class TabView extends View {

        public ValueAnimator changeAnimator;
        private Tab currentTab;
        private int textHeight;
        private int tabWidth;
        private int currentPosition;
        private RectF rect = new RectF();
        private String currentText;
        private StaticLayout textLayout;
        private int textOffsetX;

        public boolean animateChange;
        public float changeProgress;

        public boolean animateCounterChange;
        private float locIconXOffset;


        float lastTextX;
        float animateFromTextX;
        boolean animateTextX;

        boolean animateTabCounter;
        int lastTabCount = -1;
        int animateFromTabCount;
        StaticLayout inCounter;
        StaticLayout outCounter;
        StaticLayout stableCounter;

        StaticLayout lastTitleLayout;
        String lastTitle;
        private StaticLayout titleAnimateInLayout;
        private StaticLayout titleAnimateOutLayout;
        private StaticLayout titleAnimateStableLayout;
        private boolean animateTextChange;
        private boolean animateTextChangeOut;
        private boolean animateTabWidth;
        private float animateFromWidth;
        private float titleXOffset;
        private int lastTitleWidth;
        private int animateFromTitleWidth;
        private int lastCountWidth;
        private float lastCounterWidth;
        private float animateFromCountWidth;
        private float animateFromCounterWidth;
        private float lastTabWidth;
        private float animateFromTabWidth;
        private float lastWidth;
        private float rotation;
        private float progressToLocked;

        public TabView(Context context) {
            super(context);
        }

        public void setTab(Tab tab, int position) {
            currentTab = tab;
            currentPosition = position;
            setContentDescription(tab.title);
            requestLayout();
        }

        public int getId() {
            return currentTab.id;
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            animateChange = false;
            animateTabCounter = false;
            animateCounterChange = false;
            animateTextChange = false;
            animateTextX = false;
            animateTabWidth = false;
            if (changeAnimator != null) {
                changeAnimator.removeAllListeners();
                changeAnimator.removeAllUpdateListeners();
                changeAnimator.cancel();
                changeAnimator = null;
            }
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = currentTab.getWidth(false) + AndroidUtilities.dp(32) + additionalTabWidth;
            setMeasuredDimension(w, MeasureSpec.getSize(heightMeasureSpec));
        }

        @SuppressLint("DrawAllocation")
        @Override
        protected void onDraw(Canvas canvas) {
            boolean reorderEnabled = true;
            boolean showRemove = !currentTab.isDefault && reorderEnabled;
            if (reorderEnabled && editingAnimationProgress != 0) {
                canvas.save();
                float p = editingAnimationProgress * (currentPosition % 2 == 0 ? 1.0f : -1.0f);
                float s = (float) Math.sin((p + (currentPosition % 2)) * Math.PI * 2.5f);
                float a = (float) (SystemClock.elapsedRealtime() / 400f * Math.PI * (currentPosition % 2 == 0 ? 1.0f : -1.0f));
                canvas.translate(
                    (float) (Math.cos(a) * AndroidUtilities.dp(0.33f) * (currentPosition % 2 == 0 ? 1.0f : -1.0f)),
                    (float) (Math.sin(a) * -AndroidUtilities.dp(0.33f))
                );
                canvas.rotate(1.4f * s, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);
            }
            String key;
            String animateToKey;
            String otherKey;
            String animateToOtherKey;
            String unreadKey;
            String unreadOtherKey;
            int id1;
            int id2;
            if (manualScrollingToId != -1) {
                id1 = manualScrollingToId;
                id2 = selectedTabId;
            } else {
                id1 = selectedTabId;
                id2 = previousId;
            }
            if (currentTab.id == id1) {
                key = activeTextColorKey;
                animateToKey = aActiveTextColorKey;
                otherKey = unactiveTextColorKey;
                animateToOtherKey = aUnactiveTextColorKey;
                unreadKey = Theme.key_chats_tabUnreadActiveBackground;
                unreadOtherKey = Theme.key_chats_tabUnreadUnactiveBackground;
            } else {
                key = unactiveTextColorKey;
                animateToKey = aUnactiveTextColorKey;
                otherKey = activeTextColorKey;
                animateToOtherKey = aUnactiveTextColorKey;
                unreadKey = Theme.key_chats_tabUnreadUnactiveBackground;
                unreadOtherKey = Theme.key_chats_tabUnreadActiveBackground;
            }
            if (animateToKey == null) {
                if ((animatingIndicator || manualScrollingToId != -1) && (currentTab.id == id1 || currentTab.id == id2)) {
                    textPaint.setColor(ColorUtils.blendARGB(Theme.getColor(otherKey), Theme.getColor(key), animatingIndicatorProgress));
                } else {
                    textPaint.setColor(Theme.getColor(key));
                }
            } else {
                int color1 = Theme.getColor(key);
                int color2 = Theme.getColor(animateToKey);
                if ((animatingIndicator || manualScrollingToPosition != -1) && (currentTab.id == id1 || currentTab.id == id2)) {
                    int color3 = Theme.getColor(otherKey);
                    int color4 = Theme.getColor(animateToOtherKey);
                    textPaint.setColor(ColorUtils.blendARGB(ColorUtils.blendARGB(color3, color4, animationValue), ColorUtils.blendARGB(color1, color2, animationValue), animatingIndicatorProgress));
                } else {
                    textPaint.setColor(ColorUtils.blendARGB(color1, color2, animationValue));
                }
            }

            float counterWidth;
            int countWidth;
            String counterText;

            boolean animateCounterEnter = animateFromTabCount == 0 && animateTabCounter;
            boolean animateCounterRemove = animateFromTabCount > 0 && currentTab.counter == 0 && animateTabCounter;
            boolean animateCounterReplace = animateFromTabCount > 0 && currentTab.counter > 0 && animateTabCounter;

            if (currentTab.counter > 0 || animateCounterRemove) {
                if (animateCounterRemove) {
                    counterText = String.format("%d", animateFromTabCount);
                } else {
                    counterText = String.format("%d", currentTab.counter);
                }
                counterWidth = (int) Math.ceil(textCounterPaint.measureText(counterText));
                countWidth = (int) (Math.max(AndroidUtilities.dp(10), counterWidth) + AndroidUtilities.dp(10));
            } else {
                counterText = null;
                counterWidth = 0;
                countWidth = 0;
            }


            if (showRemove && (isEditing || editingStartAnimationProgress != 0)) {
                countWidth = (int) (countWidth + (AndroidUtilities.dp(20) - countWidth) * editingStartAnimationProgress);
            }

            tabWidth = currentTab.titleWidth + ((countWidth != 0 && !animateCounterRemove) ? countWidth + AndroidUtilities.dp(6 * (counterText != null ? 1.0f : editingStartAnimationProgress)) : 0);
            float textX = (getMeasuredWidth() - tabWidth) / 2f;
            if (animateTextX) {
                textX = textX * changeProgress + animateFromTextX * (1f - changeProgress);
            }

            if (!TextUtils.equals(currentTab.title, currentText)) {
                currentText = currentTab.title;
                CharSequence text = Emoji.replaceEmoji(currentText, textPaint.getFontMetricsInt(), AndroidUtilities.dp(15), false);
                textLayout = new StaticLayout(text, textPaint, AndroidUtilities.dp(400), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0, false);
                textHeight = textLayout.getHeight();
                textOffsetX = (int) -textLayout.getLineLeft(0);
            }


            float titleOffsetX = 0;
            if (animateTextChange) {
                titleOffsetX = titleXOffset * (animateTextChangeOut ? changeProgress : 1f - changeProgress);
                if (titleAnimateStableLayout != null) {
                    canvas.save();
                    canvas.translate(textX + textOffsetX + titleOffsetX, (getMeasuredHeight() - textHeight) / 2f + 1);
                    titleAnimateStableLayout.draw(canvas);
                    canvas.restore();
                }
                if (titleAnimateInLayout != null) {
                    canvas.save();
                    int alpha = textPaint.getAlpha();
                    textPaint.setAlpha((int) (alpha * (animateTextChangeOut ? 1f - changeProgress : changeProgress)));
                    canvas.translate(textX + textOffsetX + titleOffsetX, (getMeasuredHeight() - textHeight) / 2f + 1);
                    titleAnimateInLayout.draw(canvas);
                    canvas.restore();
                    textPaint.setAlpha(alpha);
                }
                if (titleAnimateOutLayout != null) {
                    canvas.save();
                    int alpha = textPaint.getAlpha();
                    textPaint.setAlpha((int) (alpha * (animateTextChangeOut ? changeProgress : 1f - changeProgress)));
                    canvas.translate(textX + textOffsetX + titleOffsetX, (getMeasuredHeight() - textHeight) / 2f + 1);
                    titleAnimateOutLayout.draw(canvas);
                    canvas.restore();
                    textPaint.setAlpha(alpha);
                }
            } else {
                if (textLayout != null) {
                    canvas.save();
                    canvas.translate(textX + textOffsetX, (getMeasuredHeight() - textHeight) / 2f + 1);
                    textLayout.draw(canvas);
                    canvas.restore();
                }
            }

            if (animateCounterEnter || counterText != null || showRemove && (isEditing || editingStartAnimationProgress != 0)) {
                if (aBackgroundColorKey == null) {
                    textCounterPaint.setColor(Theme.getColor(backgroundColorKey));
                } else {
                    int color1 = Theme.getColor(backgroundColorKey);
                    int color2 = Theme.getColor(aBackgroundColorKey);
                    textCounterPaint.setColor(ColorUtils.blendARGB(color1, color2, animationValue));
                }
                if (Theme.hasThemeKey(unreadKey) && Theme.hasThemeKey(unreadOtherKey)) {
                    int color1 = Theme.getColor(unreadKey);
                    if ((animatingIndicator || manualScrollingToPosition != -1) && (currentTab.id == id1 || currentTab.id == id2)) {
                        int color3 = Theme.getColor(unreadOtherKey);
                        counterPaint.setColor(ColorUtils.blendARGB(color3, color1, animatingIndicatorProgress));
                    } else {
                        counterPaint.setColor(color1);
                    }
                } else {
                    counterPaint.setColor(textPaint.getColor());
                }

                float x;
                float titleWidth = currentTab.titleWidth;
                if (animateTextChange) {
                    titleWidth = animateFromTitleWidth * (1f - changeProgress) + currentTab.titleWidth * changeProgress;
                }
                if (animateTextChange && titleAnimateOutLayout == null) {
                    x = textX - titleXOffset + titleOffsetX + titleWidth + AndroidUtilities.dp(6);
                } else {
                    x = textX + titleWidth + AndroidUtilities.dp(6);
                }
                int countTop = (getMeasuredHeight() - AndroidUtilities.dp(20)) / 2;

                if (showRemove && (isEditing || editingStartAnimationProgress != 0) && counterText == null) {
                    counterPaint.setAlpha((int) (editingStartAnimationProgress * 255));
                } else {
                    counterPaint.setAlpha(255);
                }


                float w = (animateCounterReplace && animateFromCountWidth != countWidth) ? animateFromCountWidth * (1f - changeProgress) + countWidth * changeProgress : countWidth;
                if (animateCounterReplace) {
                    counterWidth = animateFromCounterWidth * (1f - changeProgress) + counterWidth * changeProgress;
                }
                rect.set(x, countTop, x + w, countTop + AndroidUtilities.dp(20));
                if (animateCounterEnter || animateCounterRemove) {
                    canvas.save();
                    float s = animateCounterEnter ? changeProgress : 1f - changeProgress;
                    canvas.scale(s, s, rect.centerX(), rect.centerY());
                }
                canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, counterPaint);

                if (animateCounterReplace) {
                    float y = countTop;
                    if (inCounter != null) {
                        y += (AndroidUtilities.dp(20) - (inCounter.getLineBottom(0) - inCounter.getLineTop(0))) / 2f;
                    } else if (outCounter != null) {
                        y += (AndroidUtilities.dp(20) - (outCounter.getLineBottom(0) - outCounter.getLineTop(0))) / 2f;
                    } else if (stableCounter != null) {
                        y += (AndroidUtilities.dp(20) - (stableCounter.getLineBottom(0) - stableCounter.getLineTop(0))) / 2f;
                    }
                    float alpha = 1f;
                    if (showRemove) {
                        alpha = (1.0f - editingStartAnimationProgress);
                    }
                    if (inCounter != null) {
                        canvas.save();
                        textCounterPaint.setAlpha((int) (255 * alpha * changeProgress));
                        canvas.translate(rect.left + (rect.width() - counterWidth) / 2, (1f - changeProgress) * AndroidUtilities.dp(15) + y);
                        inCounter.draw(canvas);
                        canvas.restore();
                    }
                    if (outCounter != null) {
                        canvas.save();
                        textCounterPaint.setAlpha((int) (255 * alpha * (1f - changeProgress)));
                        canvas.translate(rect.left + (rect.width() - counterWidth) / 2, changeProgress * -AndroidUtilities.dp(15) + y);
                        outCounter.draw(canvas);
                        canvas.restore();
                    }

                    if (stableCounter != null) {
                        canvas.save();
                        textCounterPaint.setAlpha((int) (255 * alpha));
                        canvas.translate(rect.left + (rect.width() - counterWidth) / 2, y);
                        stableCounter.draw(canvas);
                        canvas.restore();
                    }
                    textCounterPaint.setAlpha(255);
                } else {
                    if (counterText != null) {
                        if (showRemove) {
                            textCounterPaint.setAlpha((int) (255 * (1.0f - editingStartAnimationProgress)));
                        }
                        canvas.drawText(counterText, rect.left + (rect.width() - counterWidth) / 2, countTop + AndroidUtilities.dp(14.5f), textCounterPaint);
                    }
                }

                if (animateCounterEnter || animateCounterRemove) {
                    canvas.restore();
                }
                if (showRemove && (isEditing || editingStartAnimationProgress != 0)) {
                    deletePaint.setColor(textCounterPaint.getColor());
                    deletePaint.setAlpha((int) (255 * editingStartAnimationProgress));
                    int side = AndroidUtilities.dp(3);
                    canvas.drawLine(rect.centerX() - side, rect.centerY() - side, rect.centerX() + side, rect.centerY() + side, deletePaint);
                    canvas.drawLine(rect.centerX() - side, rect.centerY() + side, rect.centerX() + side, rect.centerY() - side, deletePaint);
                }
            }
            if (reorderEnabled && editingAnimationProgress != 0) {
                canvas.restore();
            }

            lastTextX = textX;
            lastTabCount = currentTab.counter;
            lastTitleLayout = textLayout;
            lastTitle = currentText;
            lastTitleWidth = currentTab.titleWidth;
            lastCountWidth = countWidth;
            lastCounterWidth = counterWidth;
            lastTabWidth = tabWidth;
            lastWidth = getMeasuredWidth();

            if (currentTab.isLocked || progressToLocked != 0) {
                if (lockDrawable == null) {
                    lockDrawable = ContextCompat.getDrawable(getContext(), R.drawable.other_lockedfolders);
                }
                if (currentTab.isLocked && progressToLocked != 1f) {
                    progressToLocked += 16 / 150f;
                } else if (!currentTab.isLocked){
                    progressToLocked -= 16 / 150f;
                }
                progressToLocked = Utilities.clamp(progressToLocked, 1f, 0);
                int unactiveColor = Theme.getColor(unactiveTextColorKey);
                if (aUnactiveTextColorKey != null) {
                    unactiveColor = ColorUtils.blendARGB(unactiveColor, Theme.getColor(aUnactiveTextColorKey), animationValue);
                }
                if (lockDrawableColor != unactiveColor) {
                    lockDrawableColor = unactiveColor;
                    lockDrawable.setColorFilter(new PorterDuffColorFilter(unactiveColor, PorterDuff.Mode.MULTIPLY));
                }
                int iconX = (int) ((getMeasuredWidth() - lockDrawable.getIntrinsicWidth()) / 2f + locIconXOffset);
                int iconY = getMeasuredHeight() - AndroidUtilities.dp(12);
                lockDrawable.setBounds(iconX, iconY, iconX + lockDrawable.getIntrinsicWidth(), iconY + lockDrawable.getIntrinsicHeight());
                if (progressToLocked != 1f) {
                    canvas.save();
                    canvas.scale(progressToLocked, progressToLocked, lockDrawable.getBounds().centerX(), lockDrawable.getBounds().centerY());
                    lockDrawable.draw(canvas);
                    canvas.restore();
                } else {
                    lockDrawable.draw(canvas);
                }

            }
        }

        public boolean animateChange() {
            boolean changed = false;
            if (currentTab.counter != lastTabCount) {
                animateTabCounter = true;
                animateFromTabCount = lastTabCount;
                animateFromCountWidth = lastCountWidth;
                animateFromCounterWidth = lastCounterWidth;
                if (animateFromTabCount > 0 && currentTab.counter > 0) {
                    String oldStr = String.valueOf(animateFromTabCount);
                    String newStr = String.valueOf(currentTab.counter);

                    if (oldStr.length() == newStr.length()) {
                        SpannableStringBuilder oldSpannableStr = new SpannableStringBuilder(oldStr);
                        SpannableStringBuilder newSpannableStr = new SpannableStringBuilder(newStr);
                        SpannableStringBuilder stableStr = new SpannableStringBuilder(newStr);
                        for (int i = 0; i < oldStr.length(); i++) {
                            if (oldStr.charAt(i) == newStr.charAt(i)) {
                                oldSpannableStr.setSpan(new EmptyStubSpan(), i, i + 1, 0);
                                newSpannableStr.setSpan(new EmptyStubSpan(), i, i + 1, 0);
                            } else {
                                stableStr.setSpan(new EmptyStubSpan(), i, i + 1, 0);
                            }
                        }

                        int countOldWidth = (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(oldStr));
                        outCounter = new StaticLayout(oldSpannableStr, textCounterPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                        stableCounter = new StaticLayout(stableStr, textCounterPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                        inCounter = new StaticLayout(newSpannableStr, textCounterPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                    } else {
                        int countOldWidth = (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(oldStr));
                        outCounter = new StaticLayout(oldStr, textCounterPaint, countOldWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                        int countNewWidth = (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(newStr));
                        inCounter = new StaticLayout(newStr, textCounterPaint, countNewWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                    }
                }
                changed = true;
            }

            int countWidth;
            String counterText = null;
            if (currentTab.counter > 0) {
                counterText = String.format("%d", currentTab.counter);
                int counterWidth = (int) Math.ceil(textCounterPaint.measureText(counterText));
                countWidth = Math.max(AndroidUtilities.dp(10), counterWidth) + AndroidUtilities.dp(10);
            } else {
                countWidth = 0;
            }
            int tabWidth = currentTab.titleWidth + (countWidth != 0 ? countWidth + AndroidUtilities.dp(6 * (counterText != null ? 1.0f : editingStartAnimationProgress)) : 0);
            int textX = (getMeasuredWidth() - tabWidth) / 2;

            if (textX != lastTextX) {
                animateTextX = true;
                animateFromTextX = lastTextX;
                changed = true;
            }

            if (lastTitle != null && !currentTab.title.equals(lastTitle)) {
                boolean animateOut;
                String maxStr;
                String substring;
                if (lastTitle.length() > currentTab.title.length()) {
                    animateOut = true;
                    maxStr = lastTitle;
                    substring = currentTab.title;
                } else {
                    animateOut = false;
                    maxStr = currentTab.title;
                    substring = lastTitle;
                }
                int startFrom = maxStr.indexOf(substring);
                if (startFrom >= 0) {
                    CharSequence text = Emoji.replaceEmoji(maxStr, textPaint.getFontMetricsInt(), AndroidUtilities.dp(15), false);
                    SpannableStringBuilder inStr = new SpannableStringBuilder(text);
                    SpannableStringBuilder stabeStr = new SpannableStringBuilder(text);
                    if (startFrom != 0) {
                        stabeStr.setSpan(new EmptyStubSpan(), 0, startFrom, 0);
                    }
                    if (startFrom + substring.length() != maxStr.length()) {
                        stabeStr.setSpan(new EmptyStubSpan(), startFrom + substring.length(), maxStr.length(), 0);
                    }
                    inStr.setSpan(new EmptyStubSpan(), startFrom, startFrom + substring.length(), 0);

                    titleAnimateInLayout = new StaticLayout(inStr, textPaint, AndroidUtilities.dp(400), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0, false);
                    titleAnimateStableLayout = new StaticLayout(stabeStr, textPaint, AndroidUtilities.dp(400), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0, false);
                    animateTextChange = true;
                    animateTextChangeOut = animateOut;
                    titleXOffset = startFrom == 0 ? 0 : -titleAnimateStableLayout.getPrimaryHorizontal(startFrom);
                    animateFromTitleWidth = lastTitleWidth;
                    titleAnimateOutLayout = null;
                    changed = true;
                } else {
                    titleAnimateInLayout = new StaticLayout(currentTab.title, textPaint, AndroidUtilities.dp(400), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0, false);
                    titleAnimateOutLayout = new StaticLayout(lastTitle, textPaint, AndroidUtilities.dp(400), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0, false);
                    titleAnimateStableLayout = null;
                    animateTextChange = true;
                    titleXOffset = 0;
                    animateFromTitleWidth = lastTitleWidth;
                    changed = true;
                }
            }

            if (tabWidth != lastTabWidth || getMeasuredWidth() != lastWidth) {
                animateTabWidth = true;
                animateFromTabWidth = lastTabWidth;
                animateFromWidth = lastWidth;
                changed = true;
            }

            return changed;
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setSelected(currentTab != null && selectedTabId != -1 && currentTab.id == selectedTabId);
            info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, LocaleController.getString("AccDescrOpenMenu2", R.string.AccDescrOpenMenu2)));
            } else {
                info.addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
            }
            if (currentTab != null) {
                StringBuilder sb = new StringBuilder();
                sb.append(currentTab.title);
                int unread = currentTab != null ? currentTab.counter : 0;
                if (unread > 0) {
                    sb.append("\n");
                    sb.append(LocaleController.formatPluralString("AccDescrUnreadCount", unread));
                }
                info.setContentDescription(sb);
            }
        }

        public void clearTransitionParams() {
            animateChange = false;
            animateTabCounter = false;
            animateCounterChange = false;
            animateTextChange = false;
            animateTextX = false;
            animateTabWidth = false;
            changeAnimator = null;
            invalidate();
        }

        public void shakeLockIcon(float x, int num) {
            if (num == 6) {
                locIconXOffset = 0;
                return;
            }
            AnimatorSet animatorSet = new AnimatorSet();
            ValueAnimator animator = ValueAnimator.ofFloat(0, AndroidUtilities.dp(x));
            animator.addUpdateListener(animation -> {
                locIconXOffset = (float) animation.getAnimatedValue();
                invalidate();
            });
            animatorSet.playTogether(animator);
            animatorSet.setDuration(50);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    shakeLockIcon(num == 5 ? 0 : -x, num + 1);
                    locIconXOffset = 0;
                    invalidate();
                }
            });
            animatorSet.start();
        }
    }

    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint textCounterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Paint deletePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Paint counterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private ArrayList<Tab> tabs = new ArrayList<>();

    private boolean isEditing;
    private long lastEditingAnimationTime;
    private boolean editingForwardAnimation;
    private float editingAnimationProgress;
    private float editingStartAnimationProgress;

    private AnimatorSet colorChangeAnimator;

    private boolean orderChanged;

    private boolean ignoreLayout;

    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private ListAdapter adapter;

    private FilterTabsViewDelegate delegate;

    private int currentPosition;
    private int selectedTabId = -1;
    private int allTabsWidth;

    private int additionalTabWidth;

    private boolean animatingIndicator;
    private float animatingIndicatorProgress;
    private int manualScrollingToPosition = -1;
    private int manualScrollingToId = -1;

    private int scrollingToChild = -1;
    private GradientDrawable selectorDrawable;

    private String tabLineColorKey = Theme.key_actionBarTabLine;
    private String activeTextColorKey = Theme.key_actionBarTabActiveText;
    private String unactiveTextColorKey = Theme.key_actionBarTabUnactiveText;
    private String selectorColorKey = Theme.key_actionBarTabSelector;
    private String backgroundColorKey = Theme.key_actionBarDefault;
    private String aTabLineColorKey;
    private String aActiveTextColorKey;
    private String aUnactiveTextColorKey;
    private String aBackgroundColorKey;

    private int prevLayoutWidth;

    private boolean invalidated;

    private CubicBezierInterpolator interpolator = CubicBezierInterpolator.EASE_OUT_QUINT;

    private SparseIntArray positionToId = new SparseIntArray(5);
    private SparseIntArray positionToStableId = new SparseIntArray(5);
    private SparseIntArray idToPosition = new SparseIntArray(5);
    private SparseIntArray positionToWidth = new SparseIntArray(5);
    private SparseIntArray positionToX = new SparseIntArray(5);

    private boolean animationRunning;
    private long lastAnimationTime;
    private float animationTime;
    private int previousPosition;
    private int previousId;
    DefaultItemAnimator itemAnimator;
    private Drawable lockDrawable;
    private int lockDrawableColor;

    private Runnable animationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!animatingIndicator) {
                return;
            }
            long newTime = SystemClock.elapsedRealtime();
            long dt = (newTime - lastAnimationTime);
            if (dt > 17) {
                dt = 17;
            }
            animationTime += dt / 200.0f;
            setAnimationIdicatorProgress(interpolator.getInterpolation(animationTime));
            if (animationTime > 1.0f) {
                animationTime = 1.0f;
            }
            if (animationTime < 1.0f) {
                AndroidUtilities.runOnUIThread(animationRunnable);
            } else {
                animatingIndicator = false;
                setEnabled(true);
                if (delegate != null) {
                    delegate.onPageScrolled(1.0f);
                }
            }
        }
    };

    private float animationValue;
    private final Property<FilterTabsView, Float> COLORS = new AnimationProperties.FloatProperty<FilterTabsView>("animationValue") {
        @Override
        public void setValue(FilterTabsView object, float value) {
            animationValue = value;

            int color1 = Theme.getColor(tabLineColorKey);
            int color2 = Theme.getColor(aTabLineColorKey);
            selectorDrawable.setColor(ColorUtils.blendARGB(color1, color2, value));

            listView.invalidateViews();
            listView.invalidate();
            object.invalidate();
        }

        @Override
        public Float get(FilterTabsView object) {
            return animationValue;
        }
    };

    public FilterTabsView(Context context) {
        super(context);
        textCounterPaint.setTextSize(AndroidUtilities.dp(13));
        textCounterPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textPaint.setTextSize(AndroidUtilities.dp(15));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        deletePaint.setStyle(Paint.Style.STROKE);
        deletePaint.setStrokeCap(Paint.Cap.ROUND);
        deletePaint.setStrokeWidth(AndroidUtilities.dp(1.5f));

        selectorDrawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, null);
        float rad = AndroidUtilities.dpf2(3);
        selectorDrawable.setCornerRadii(new float[]{rad, rad, rad, rad, 0, 0, 0, 0});
        selectorDrawable.setColor(Theme.getColor(tabLineColorKey));

        setHorizontalScrollBarEnabled(false);
        listView = new RecyclerListView(context) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                FilterTabsView.this.invalidate();
            }

            @Override
            protected boolean allowSelectChildAtPosition(View child) {
                return FilterTabsView.this.isEnabled() && delegate.canPerformActions();
            }

            @Override
            protected boolean canHighlightChildAt(View child, float x, float y) {
                if (isEditing) {
                    TabView tabView = (TabView) child;
                    int side = AndroidUtilities.dp(6);
                    if (tabView.rect.left - side < x && tabView.rect.right + side > x) {
                        return false;
                    }
                }
                return super.canHighlightChildAt(child, x, y);
            }
        };
        listView.setClipChildren(false);
        itemAnimator = new DefaultItemAnimator() {

            @Override
            public void runPendingAnimations() {
                boolean removalsPending = !mPendingRemovals.isEmpty();
                boolean movesPending = !mPendingMoves.isEmpty();
                boolean changesPending = !mPendingChanges.isEmpty();
                boolean additionsPending = !mPendingAdditions.isEmpty();
                if (removalsPending || movesPending || additionsPending || changesPending) {
                    ValueAnimator valueAnimator = ValueAnimator.ofFloat(0.1f);
                    valueAnimator.addUpdateListener(valueAnimator12 -> {
                        listView.invalidate();
                        invalidate();
                    });
                    valueAnimator.setDuration(getMoveDuration());
                    valueAnimator.start();
                }
                super.runPendingAnimations();
            }

            @Override
            public boolean animateMove(RecyclerView.ViewHolder holder, ItemHolderInfo info, int fromX, int fromY, int toX, int toY) {
                if (holder.itemView instanceof TabView) {
                    final View view = holder.itemView;
                    fromX += (int) holder.itemView.getTranslationX();
                    fromY += (int) holder.itemView.getTranslationY();
                    resetAnimation(holder);
                    int deltaX = toX - fromX;
                    int deltaY = toY - fromY;
                    if (deltaX != 0) {
                        view.setTranslationX(-deltaX);
                    }
                    if (deltaY != 0) {
                        view.setTranslationY(-deltaY);
                    }

                    TabView tabView = (TabView) holder.itemView;
                    boolean animateChange = tabView.animateChange();
                    if (animateChange) {
                        tabView.changeProgress = 0;
                        tabView.animateChange = true;
                        invalidate();
                    }

                    if (deltaX == 0 && deltaY == 0 && !animateChange) {
                        dispatchMoveFinished(holder);
                        return false;
                    }

                    mPendingMoves.add(new MoveInfo(holder, fromX, fromY, toX, toY));
                    return true;
                }
                return super.animateMove(holder, info, fromX, fromY, toX, toY);
            }

            @Override
            protected void animateMoveImpl(RecyclerView.ViewHolder holder, MoveInfo moveInfo) {
                super.animateMoveImpl(holder, moveInfo);
                if (holder.itemView instanceof TabView) {
                    TabView tabView = (TabView) holder.itemView;
                    if (tabView.animateChange) {
                        if (tabView.changeAnimator != null) {
                            tabView.changeAnimator.removeAllListeners();
                            tabView.changeAnimator.removeAllUpdateListeners();
                            tabView.changeAnimator.cancel();
                        }
                        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
                        valueAnimator.addUpdateListener(valueAnimator1 -> {
                            tabView.changeProgress = (float) valueAnimator1.getAnimatedValue();
                            tabView.invalidate();
                        });
                        valueAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                tabView.clearTransitionParams();
                            }
                        });
                        tabView.changeAnimator = valueAnimator;
                        valueAnimator.setDuration(getMoveDuration());
                        valueAnimator.start();
                    }
                }
            }

            @Override
            public void onMoveFinished(RecyclerView.ViewHolder item) {
                super.onMoveFinished(item);
                item.itemView.setTranslationX(0);
                if (item.itemView instanceof TabView) {
                    ((TabView) item.itemView).clearTransitionParams();
                }
            }

            @Override
            public void endAnimation(RecyclerView.ViewHolder item) {
                super.endAnimation(item);
                item.itemView.setTranslationX(0);
                if (item.itemView instanceof TabView) {
                    ((TabView) item.itemView).clearTransitionParams();
                }
            }
        };
        itemAnimator.setDelayAnimations(false);
        listView.setItemAnimator(itemAnimator);
        listView.setSelectorType(8);
        listView.setSelectorRadius(6);
        listView.setSelectorDrawableColor(Theme.getColor(selectorColorKey));
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false) {

            @Override
            public boolean supportsPredictiveItemAnimations() {
                return true;
            }

            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScroller linearSmoothScroller = new LinearSmoothScroller(recyclerView.getContext()) {
                    @Override
                    protected void onTargetFound(View targetView, RecyclerView.State state, Action action) {
                        int dx = calculateDxToMakeVisible(targetView, getHorizontalSnapPreference());
                        if (dx > 0 || dx == 0 && targetView.getLeft() - AndroidUtilities.dp(21) < 0) {
                            dx += AndroidUtilities.dp(60);
                        } else if (dx < 0 || dx == 0 && targetView.getRight() + AndroidUtilities.dp(21) > getMeasuredWidth()) {
                            dx -= AndroidUtilities.dp(60);
                        }

                        final int dy = calculateDyToMakeVisible(targetView, getVerticalSnapPreference());
                        final int distance = (int) Math.sqrt(dx * dx + dy * dy);
                        final int time = Math.max(180, calculateTimeForDeceleration(distance));
                        if (time > 0) {
                            action.update(-dx, -dy, time, mDecelerateInterpolator);
                        }
                    }
                };
                linearSmoothScroller.setTargetPosition(position);
                startSmoothScroll(linearSmoothScroller);
            }

            @Override
            public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
                if (delegate.isTabMenuVisible()) {
                    dx = 0;
                }
                return super.scrollHorizontallyBy(dx, recycler, state);
            }
        });
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new TouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(listView);
        listView.setPadding(AndroidUtilities.dp(7), 0, AndroidUtilities.dp(7), 0);
        listView.setClipToPadding(false);
        listView.setDrawSelectorBehind(true);
        adapter = new ListAdapter(context);
        adapter.setHasStableIds(true);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((view, position, x, y) -> {
            if (!delegate.canPerformActions()) {
                return;
            }
            TabView tabView = (TabView) view;
            if (isEditing) {
                if (position != 0) {
                    int side = AndroidUtilities.dp(6);
                    if (tabView.rect.left - side < x && tabView.rect.right + side > x) {
                        delegate.onDeletePressed(tabView.currentTab.id);
                    }
                }
                return;
            }
            if (position == currentPosition && delegate != null) {
                delegate.onSamePageSelected();
                return;
            }
            scrollToTab(tabView.currentTab, position);
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (!delegate.canPerformActions() || isEditing || !delegate.didSelectTab((TabView) view, position == currentPosition)) {
                return false;
            }
            listView.hideSelector(true);
            return true;
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                invalidate();
            }
        });
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public void setDelegate(FilterTabsViewDelegate filterTabsViewDelegate) {
        delegate = filterTabsViewDelegate;
    }

    public boolean isAnimatingIndicator() {
        return animatingIndicator;
    }

    private void scrollToTab(Tab tab, int position) {
        if (tab.isLocked) {
            if (delegate != null) {
                delegate.onPageSelected(tab, false);
            }
            return;
        }
        boolean scrollingForward = currentPosition < position;
        scrollingToChild = -1;
        previousPosition = currentPosition;
        previousId = selectedTabId;
        currentPosition = position;
        selectedTabId = tab.id;

        if (animatingIndicator) {
            AndroidUtilities.cancelRunOnUIThread(animationRunnable);
            animatingIndicator = false;
        }

        animationTime = 0;
        animatingIndicatorProgress = 0;
        animatingIndicator = true;
        setEnabled(false);

        AndroidUtilities.runOnUIThread(animationRunnable, 16);

        if (delegate != null) {
            delegate.onPageSelected(tab, scrollingForward);
        }
        scrollToChild(position);
    }

    public void selectFirstTab() {
        if (tabs.isEmpty()) {
            return;
        }
        scrollToTab(tabs.get(0), 0);
    }

    public void setAnimationIdicatorProgress(float value) {
        animatingIndicatorProgress = value;
        listView.invalidateViews();
        invalidate();
        if (delegate != null) {
            delegate.onPageScrolled(value);
        }
    }

    public Drawable getSelectorDrawable() {
        return selectorDrawable;
    }

    public RecyclerListView getTabsContainer() {
        return listView;
    }

    public int getNextPageId(boolean forward) {
        return positionToId.get(currentPosition + (forward ? 1 : -1), -1);
    }

    public void removeTabs() {
        tabs.clear();
        positionToId.clear();
        idToPosition.clear();
        positionToWidth.clear();
        positionToX.clear();
        allTabsWidth = 0;
    }

    public boolean hasTab(int id) {
        return idToPosition.get(id, -1) != -1;
    }

    public void resetTabId() {
        selectedTabId = -1;
    }

    public void addTab(int id, int stableId, String text, boolean isDefault, boolean isLocked) {
        int position = tabs.size();
        if (position == 0 && selectedTabId == -1) {
            selectedTabId = id;
        }
        positionToId.put(position, id);
        positionToStableId.put(position, stableId);
        idToPosition.put(id, position);
        if (selectedTabId != -1 && selectedTabId == id) {
            currentPosition = position;
        }

        Tab tab = new Tab(id, text);
        tab.isDefault = isDefault;
        tab.isLocked = isLocked;
        allTabsWidth += tab.getWidth(true) + AndroidUtilities.dp(32);
        tabs.add(tab);
    }

    public void finishAddingTabs(boolean animated) {
        listView.setItemAnimator(animated ? itemAnimator : null);
        adapter.notifyDataSetChanged();
    }

    public void animateColorsTo(String line, String active, String unactive, String selector, String background) {
        if (colorChangeAnimator != null) {
            colorChangeAnimator.cancel();
        }
        aTabLineColorKey = line;
        aActiveTextColorKey = active;
        aUnactiveTextColorKey = unactive;
        aBackgroundColorKey = background;
        selectorColorKey = selector;
        listView.setSelectorDrawableColor(Theme.getColor(selectorColorKey));

        colorChangeAnimator = new AnimatorSet();
        colorChangeAnimator.playTogether(ObjectAnimator.ofFloat(this, COLORS, 0.0f, 1.0f));
        colorChangeAnimator.setDuration(200);
        colorChangeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                tabLineColorKey = aTabLineColorKey;
                backgroundColorKey = aBackgroundColorKey;
                activeTextColorKey = aActiveTextColorKey;
                unactiveTextColorKey = aUnactiveTextColorKey;
                aTabLineColorKey = null;
                aActiveTextColorKey = null;
                aUnactiveTextColorKey = null;
                aBackgroundColorKey = null;
            }
        });
        colorChangeAnimator.start();
    }

    public int getCurrentTabId() {
        return selectedTabId;
    }

    public int getFirstTabId() {
        return positionToId.get(0, 0);
    }

    public String getSelectorColorKey() {
        return selectorColorKey;
    }

    private void updateTabsWidths() {
        positionToX.clear();
        positionToWidth.clear();
        int xOffset = AndroidUtilities.dp(7);
        for (int a = 0, N = tabs.size(); a < N; a++) {
            int tabWidth = tabs.get(a).getWidth(false);
            positionToWidth.put(a, tabWidth);
            positionToX.put(a, xOffset + additionalTabWidth / 2);
            xOffset += tabWidth + AndroidUtilities.dp(32) + additionalTabWidth;
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (child == listView) {
            final int height = getMeasuredHeight();
            selectorDrawable.setAlpha((int) (255 * listView.getAlpha()));
            float indicatorX = 0;
            float indicatorWidth = 0;
            if (animatingIndicator || manualScrollingToPosition != -1) {
                int position = layoutManager.findFirstVisibleItemPosition();
                if (position != RecyclerListView.NO_POSITION) {
                    RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(position);
                    if (holder != null) {
                        int idx1;
                        int idx2;
                        if (animatingIndicator) {
                            idx1 = previousPosition;
                            idx2 = currentPosition;
                        } else {
                            idx1 = currentPosition;
                            idx2 = manualScrollingToPosition;
                        }
                        int prevX = positionToX.get(idx1);
                        int newX = positionToX.get(idx2);
                        int prevW = positionToWidth.get(idx1);
                        int newW = positionToWidth.get(idx2);
                        if (additionalTabWidth != 0) {
                            indicatorX = (int) (prevX + (newX - prevX) * animatingIndicatorProgress) + AndroidUtilities.dp(16);
                        } else {
                            int x = positionToX.get(position);
                            indicatorX = (int) (prevX + (newX - prevX) * animatingIndicatorProgress) - (x - holder.itemView.getLeft()) + AndroidUtilities.dp(16);
                        }
                        indicatorWidth = (int) (prevW + (newW - prevW) * animatingIndicatorProgress);
                    }
                }
            } else {
                RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(currentPosition);
                if (holder != null) {
                    TabView tabView = (TabView) holder.itemView;
                    indicatorWidth = Math.max(AndroidUtilities.dp(40), tabView.animateTabWidth ? tabView.animateFromTabWidth * (1f - tabView.changeProgress) + tabView.tabWidth * tabView.changeProgress : tabView.tabWidth);
                    float viewWidth = tabView.animateTabWidth ? tabView.animateFromWidth * (1f - tabView.changeProgress) + tabView.getMeasuredWidth() * tabView.changeProgress : tabView.getMeasuredWidth();
                    indicatorX = (int) (tabView.getX() + (viewWidth - indicatorWidth) / 2);
                }
            }
            if (indicatorWidth != 0) {
                canvas.save();
                canvas.translate(listView.getTranslationX(), 0);
                canvas.scale(listView.getScaleX(), 1f, listView.getPivotX() + listView.getX(), listView.getPivotY());
                selectorDrawable.setBounds((int) indicatorX, height - AndroidUtilities.dpr(4), (int) (indicatorX + indicatorWidth), height);
                selectorDrawable.draw(canvas);
                canvas.restore();
            }
        }
        long newTime = SystemClock.elapsedRealtime();
        long dt = Math.min(17, newTime - lastEditingAnimationTime);
        lastEditingAnimationTime = newTime;
        boolean invalidate = false;
        if (isEditing || editingAnimationProgress != 0.0f) {
            if (editingForwardAnimation) {
                boolean lessZero = editingAnimationProgress <= 0;
                editingAnimationProgress += dt / 420.0f;
                if (!isEditing && lessZero && editingAnimationProgress >= 0) {
                    editingAnimationProgress = 0;
                }
                if (editingAnimationProgress >= 1.0f) {
                    editingAnimationProgress = 1.0f;
                    editingForwardAnimation = false;
                }
            } else {
                boolean greaterZero = editingAnimationProgress >= 0;
                editingAnimationProgress -= dt / 420.0f;
                if (!isEditing && greaterZero && editingAnimationProgress <= 0) {
                    editingAnimationProgress = 0;
                }
                if (editingAnimationProgress <= -1.0f) {
                    editingAnimationProgress = -1.0f;
                    editingForwardAnimation = true;
                }
            }
            invalidate = true;
        }
        if (isEditing) {
            if (editingStartAnimationProgress < 1.0f) {
                editingStartAnimationProgress += dt / 180.0f;
                if (editingStartAnimationProgress > 1.0f) {
                    editingStartAnimationProgress = 1.0f;
                }
                invalidate = true;
            }
        } else if (!isEditing) {
            if (editingStartAnimationProgress > 0.0f) {
                editingStartAnimationProgress -= dt / 180.0f;
                if (editingStartAnimationProgress < 0.0f) {
                    editingStartAnimationProgress = 0.0f;
                }
                invalidate = true;
            }
        }
        if (invalidate) {
            listView.invalidateViews();
            invalidate();
        }
        return result;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!tabs.isEmpty()) {
            int width = MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(7) - AndroidUtilities.dp(7);
            Tab firstTab = findDefaultTab();
            firstTab.setTitle(LocaleController.getString("FilterAllChats", R.string.FilterAllChats));
            int tabWith = firstTab.getWidth(false);
            firstTab.setTitle(allTabsWidth > width ? LocaleController.getString("FilterAllChatsShort", R.string.FilterAllChatsShort) : LocaleController.getString("FilterAllChats", R.string.FilterAllChats));
            int trueTabsWidth = allTabsWidth - tabWith;
            trueTabsWidth += firstTab.getWidth(false);
            int prevWidth = additionalTabWidth;
            additionalTabWidth = trueTabsWidth < width ? (width - trueTabsWidth) / tabs.size() : 0;
            if (prevWidth != additionalTabWidth) {
                ignoreLayout = true;
                RecyclerView.ItemAnimator animator = listView.getItemAnimator();
                listView.setItemAnimator(null);
                adapter.notifyDataSetChanged();
                listView.setItemAnimator(animator);
                ignoreLayout = false;
            }
            updateTabsWidths();
            invalidated = false;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private Tab findDefaultTab() {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).isDefault) {
                return tabs.get(i);
            }
        }
        return null;
    }

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    private void scrollToChild(int position) {
        if (tabs.isEmpty() || scrollingToChild == position || position < 0 || position >= tabs.size()) {
            return;
        }
        scrollingToChild = position;
        listView.smoothScrollToPosition(position);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (prevLayoutWidth != r - l) {
            prevLayoutWidth = r - l;
            scrollingToChild = -1;
            if (animatingIndicator) {
                AndroidUtilities.cancelRunOnUIThread(animationRunnable);
                animatingIndicator = false;
                setEnabled(true);
                if (delegate != null) {
                    delegate.onPageScrolled(1.0f);
                }
            }
        }
    }

    public void selectTabWithId(int id, float progress) {
        int position = idToPosition.get(id, -1);
        if (position < 0) {
            return;
        }
        if (progress < 0) {
            progress = 0;
        } else if (progress > 1.0f) {
            progress = 1.0f;
        }

        if (progress > 0) {
            manualScrollingToPosition = position;
            manualScrollingToId = id;
        } else {
            manualScrollingToPosition = -1;
            manualScrollingToId = -1;
        }
        animatingIndicatorProgress = progress;
        listView.invalidateViews();
        invalidate();
        scrollToChild(position);

        if (progress >= 1.0f) {
            manualScrollingToPosition = -1;
            manualScrollingToId = -1;
            currentPosition = position;
            selectedTabId = id;
        }
    }

    private int getChildWidth(TextView child) {
        Layout layout = child.getLayout();
        if (layout != null) {
            int w = (int) Math.ceil(layout.getLineWidth(0)) + AndroidUtilities.dp(2);
            if (child.getCompoundDrawables()[2] != null) {
                w += child.getCompoundDrawables()[2].getIntrinsicWidth() + AndroidUtilities.dp(6);
            }
            return w;
        } else {
            return child.getMeasuredWidth();
        }
    }

    public void onPageScrolled(int position, int first) {
        if (currentPosition == position) {
            return;
        }
        currentPosition = position;
        if (position >= tabs.size()) {
            return;
        }
        if (first == position && position > 1) {
            scrollToChild(position - 1);
        } else {
            scrollToChild(position);
        }
        invalidate();
    }

    public boolean isEditing() {
        return isEditing;
    }

    public void setIsEditing(boolean value) {
        isEditing = value;
        editingForwardAnimation = true;
        listView.invalidateViews();
        invalidate();
        if (!isEditing && orderChanged) {
            MessagesStorage.getInstance(UserConfig.selectedAccount).saveDialogFiltersOrder();
            TLRPC.TL_messages_updateDialogFiltersOrder req = new TLRPC.TL_messages_updateDialogFiltersOrder();
            ArrayList<MessagesController.DialogFilter> filters = MessagesController.getInstance(UserConfig.selectedAccount).dialogFilters;
            for (int a = 0, N = filters.size(); a < N; a++) {
                MessagesController.DialogFilter filter = filters.get(a);
                if (filter.isDefault()) {
                    req.order.add(0);
                } else {
                    req.order.add(filter.id);
                }
            }
            MessagesController.getInstance(UserConfig.selectedAccount).lockFiltersInternal();
            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> {
            });
            orderChanged = false;
        }
    }

    public void checkTabsCounter() {
        boolean changed = false;
        for (int a = 0, N = tabs.size(); a < N; a++) {
            Tab tab = tabs.get(a);
            if (tab.counter == delegate.getTabCounter(tab.id) || delegate.getTabCounter(tab.id) < 0) {
                continue;
            }
            changed = true;
            int oldWidth = positionToWidth.get(a);
            int width = tab.getWidth(true);
            if (oldWidth != width || invalidated) {
                invalidated = true;
                requestLayout();
                allTabsWidth = 0;
                findDefaultTab().setTitle(LocaleController.getString("FilterAllChats", R.string.FilterAllChats));
                for (int b = 0; b < N; b++) {
                    allTabsWidth += tabs.get(b).getWidth(true) + AndroidUtilities.dp(32);
                }
                break;
            }
        }
        if (changed) {
            listView.setItemAnimator(itemAnimator);
            adapter.notifyDataSetChanged();
        }
    }

    public void notifyTabCounterChanged(int id) {
        int position = idToPosition.get(id, -1);
        if (position < 0 || position >= tabs.size()) {
            return;
        }
        Tab tab = tabs.get(position);
        if (tab.counter == delegate.getTabCounter(tab.id) || delegate.getTabCounter(tab.id) < 0) {
            return;
        }
        listView.invalidateViews();
        int oldWidth = positionToWidth.get(position);
        int width = tab.getWidth(true);
        if (oldWidth != width || invalidated) {
            invalidated = true;
            requestLayout();
            listView.setItemAnimator(itemAnimator);
            adapter.notifyDataSetChanged();
            allTabsWidth = 0;
            findDefaultTab().setTitle(LocaleController.getString("FilterAllChats", R.string.FilterAllChats));
            for (int b = 0, N = tabs.size(); b < N; b++) {
                allTabsWidth += tabs.get(b).getWidth(true) + AndroidUtilities.dp(32);
            }
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return tabs.size();
        }

        @Override
        public long getItemId(int position) {
            return positionToStableId.get(position);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new TabView(mContext));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            TabView tabView = (TabView) holder.itemView;
            int oldId = tabView.currentTab != null ? tabView.getId() : -1;
            tabView.setTab(tabs.get(position), position);
            if (oldId != tabView.getId()) {
                tabView.progressToLocked = tabView.currentTab.isLocked ? 1f : 0;
            }
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }

        public void swapElements(int fromIndex, int toIndex) {
            int idx1 = fromIndex;
            int idx2 = toIndex;
            int count = tabs.size();
            if (idx1 < 0 || idx2 < 0 || idx1 >= count || idx2 >= count) {
                return;
            }
            ArrayList<MessagesController.DialogFilter> filters = MessagesController.getInstance(UserConfig.selectedAccount).dialogFilters;
            MessagesController.DialogFilter filter1 = filters.get(idx1);
            MessagesController.DialogFilter filter2 = filters.get(idx2);
            int temp = filter1.order;
            filter1.order = filter2.order;
            filter2.order = temp;
            filters.set(idx1, filter2);
            filters.set(idx2, filter1);

            Tab tab1 = tabs.get(fromIndex);
            Tab tab2 = tabs.get(toIndex);
            temp = tab1.id;
            tab1.id = tab2.id;
            tab2.id = temp;

            int fromStableId = positionToStableId.get(fromIndex);
            int toStableId = positionToStableId.get(toIndex);

            positionToStableId.put(fromIndex, toStableId);
            positionToStableId.put(toIndex, fromStableId);

            delegate.onPageReorder(tab2.id, tab1.id);

            if (currentPosition == fromIndex) {
                currentPosition = toIndex;
                selectedTabId = tab1.id;
            } else if (currentPosition == toIndex) {
                currentPosition = fromIndex;
                selectedTabId = tab2.id;
            }

            if (previousPosition == fromIndex) {
                previousPosition = toIndex;
                previousId = tab1.id;
            } else if (previousPosition == toIndex) {
                previousPosition = fromIndex;
                previousId = tab2.id;
            }

            tabs.set(fromIndex, tab2);
            tabs.set(toIndex, tab1);

            updateTabsWidths();

            orderChanged = true;
            listView.setItemAnimator(itemAnimator);
            notifyItemMoved(fromIndex, toIndex);
        }

        public void moveElementToStart(int theIndex) {
            int count = tabs.size();
            if (theIndex < 0 || theIndex >= count) {
                return;
            }
            ArrayList<MessagesController.DialogFilter> filters = MessagesController.getInstance(UserConfig.selectedAccount).dialogFilters;
            int temp = positionToStableId.get(theIndex),
                temp2 = tabs.get(theIndex).id;
            for (int i = theIndex - 1; i >= 0; --i) {
//                notifyItemMoved(i, i + 1);
                positionToStableId.put(i + 1, positionToStableId.get(i));
            }
            MessagesController.DialogFilter filter = filters.remove(theIndex);
            filter.order = 0;
            filters.add(0, filter);
            positionToStableId.put(0, temp);
            tabs.add(0, tabs.remove(theIndex));
            tabs.get(0).id = temp2;
            for (int i = 0; i <= theIndex; ++i) {
                tabs.get(i).id = i;
                filters.get(i).order = i;
            }
            for (int i = 0; i <= theIndex; ++i) {
                if (currentPosition == i) {
                    currentPosition = selectedTabId = i == theIndex ? 0 : i + 1;
                }
                if (previousPosition == i) {
                    previousPosition = previousId = i == theIndex ? 0 : i + 1;
                }
            }
            notifyItemMoved(theIndex, 0);

            delegate.onPageReorder(tabs.get(theIndex).id, temp2);

            updateTabsWidths();

            orderChanged = true;
            listView.setItemAnimator(itemAnimator);
        }
    }

    public class TouchHelperCallback extends ItemTouchHelper.Callback {

        @Override
        public boolean isLongPressDragEnabled() {
            return isEditing;
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if (MessagesController.getInstance(UserConfig.selectedAccount).premiumLocked && (!isEditing || (viewHolder.getAdapterPosition() == 0 && tabs.get(0).isDefault && !UserConfig.getInstance(UserConfig.selectedAccount).isPremium()))) {
                return makeMovementFlags(0, 0);
            }
            return makeMovementFlags(ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
            if (MessagesController.getInstance(UserConfig.selectedAccount).premiumLocked && ((source.getAdapterPosition() == 0 || target.getAdapterPosition() == 0) && !UserConfig.getInstance(UserConfig.selectedAccount).isPremium())) {
                return false;
            }
            adapter.swapElements(source.getAdapterPosition(), target.getAdapterPosition());
            return true;
        }

        private Runnable resetDefaultPosition = () ->  {
            if (UserConfig.getInstance(UserConfig.selectedAccount).isPremium()) {
                return;
            }
            for (int i = 0; i < tabs.size(); ++i) {
                if (tabs.get(i).isDefault && i != 0) {
                    adapter.moveElementToStart(i);
                    listView.scrollToPosition(0);
                    onDefaultTabMoved();
                    break;
                }
            }
        };

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                listView.cancelClickRunnables(false);
                viewHolder.itemView.setPressed(true);
                viewHolder.itemView.setBackgroundColor(Theme.getColor(backgroundColorKey));
            } else {
                AndroidUtilities.cancelRunOnUIThread(resetDefaultPosition);
                AndroidUtilities.runOnUIThread(resetDefaultPosition, 320);
            }
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setPressed(false);
            viewHolder.itemView.setBackground(null);
        }
    }

    public RecyclerListView getListView() {
        return listView;
    }

    public boolean currentTabIsDefault() {
        Tab defaultTab = findDefaultTab();
        if (defaultTab == null) {
            return false;
        }
        return defaultTab.id == selectedTabId;
    }

    public int getDefaultTabId() {
        Tab defaultTab = findDefaultTab();
        if (defaultTab == null) {
            return -1;
        }
        return defaultTab.id;
    }

    public boolean isEmpty() {
        return tabs.isEmpty();
    }

    public boolean isFirstTabSelected() {
        if (tabs.isEmpty()) {
            return true;
        }
        return selectedTabId == tabs.get(0).id;
    }

    public boolean isLocked(int id) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).id == id) {
                return tabs.get(i).isLocked;
            }
        }
        return false;
    }

    public void shakeLock(int id) {
        for (int i = 0; i < listView.getChildCount(); i++) {
            if (listView.getChildAt(i) instanceof TabView) {
                TabView tabView = (TabView) listView.getChildAt(i);
                if (tabView.currentTab.id == id) {
                    tabView.shakeLockIcon(1, 0);
                    tabView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    break;
                }
            }
        }
    }

    protected void onDefaultTabMoved() {

    }

}
