package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;

public class ChatScrimPopupContainerLayout extends LinearLayout {

    private ReactionsContainerLayout reactionsLayout;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupWindowLayout;
    private View bottomView;
    private int maxHeight;
    private float popupLayoutLeftOffset;
    private float progressToSwipeBack;
    private float bottomViewYOffset;
    private float expandSize;
    private float bottomViewReactionsOffset;

    public ChatScrimPopupContainerLayout(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxHeight != 0) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
        }
        if (reactionsLayout != null && popupWindowLayout != null) {
            reactionsLayout.getLayoutParams().width = LayoutHelper.WRAP_CONTENT;
            ((LayoutParams) reactionsLayout.getLayoutParams()).rightMargin = 0;
            popupLayoutLeftOffset = 0;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            int maxWidth = reactionsLayout.getMeasuredWidth();
            if (popupWindowLayout.getSwipeBack() != null && popupWindowLayout.getSwipeBack().getMeasuredWidth() > maxWidth) {
                maxWidth = popupWindowLayout.getSwipeBack().getMeasuredWidth();
            }
            if (popupWindowLayout.getMeasuredWidth() > maxWidth) {
                maxWidth = popupWindowLayout.getMeasuredWidth();
            }
            if (reactionsLayout.showCustomEmojiReaction()) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
            }
            reactionsLayout.measureHint();

            int reactionsLayoutTotalWidth = reactionsLayout.getTotalWidth();
            View menuContainer = popupWindowLayout.getSwipeBack() != null ? popupWindowLayout.getSwipeBack().getChildAt(0) : popupWindowLayout.getChildAt(0);
            int maxReactionsLayoutWidth = menuContainer.getMeasuredWidth() + dp(16) + dp(16) + dp(36);
            int hintTextWidth = reactionsLayout.getHintTextWidth();
            if (hintTextWidth > maxReactionsLayoutWidth) {
                maxReactionsLayoutWidth = hintTextWidth;
            } else if (maxReactionsLayoutWidth > maxWidth) {
                maxReactionsLayoutWidth = maxWidth;
            }
            reactionsLayout.bigCircleOffset = dp(36);
            if (reactionsLayout.showCustomEmojiReaction()) {
                reactionsLayout.getLayoutParams().width = reactionsLayoutTotalWidth;
                reactionsLayout.bigCircleOffset = Math.max(reactionsLayoutTotalWidth - menuContainer.getMeasuredWidth() - dp(36), dp(36));
            } else if (reactionsLayoutTotalWidth > maxReactionsLayoutWidth) {
                int maxFullCount = ((maxReactionsLayoutWidth - dp(16)) / dp(36)) + 1;
                int newWidth = maxFullCount * dp(36) + dp(8);
                if (hintTextWidth + dp(24) > newWidth) {
                    newWidth = hintTextWidth + dp(24);
                }
                if (newWidth > reactionsLayoutTotalWidth || maxFullCount == reactionsLayout.getItemsCount()) {
                    newWidth = reactionsLayoutTotalWidth;
                }
                reactionsLayout.getLayoutParams().width = newWidth;
            } else {
                reactionsLayout.getLayoutParams().width = LayoutHelper.WRAP_CONTENT;
            }
            int widthDiff = 0;
            if (reactionsLayout.getMeasuredWidth() != maxWidth || !reactionsLayout.showCustomEmojiReaction()) {
                if (popupWindowLayout.getSwipeBack() != null) {
                    widthDiff = popupWindowLayout.getSwipeBack().getMeasuredWidth() - popupWindowLayout.getSwipeBack().getChildAt(0).getMeasuredWidth();
                }
                if (reactionsLayout.getLayoutParams().width != LayoutHelper.WRAP_CONTENT && reactionsLayout.getLayoutParams().width + widthDiff > maxWidth) {
                    widthDiff = maxWidth - reactionsLayout.getLayoutParams().width + dp(8);
                }
                if (widthDiff < 0) {
                    widthDiff = 0;
                }
                ((LayoutParams) reactionsLayout.getLayoutParams()).rightMargin = widthDiff;
                popupLayoutLeftOffset = 0;
                updatePopupTranslation();
            } else {
                popupLayoutLeftOffset = (maxWidth - menuContainer.getMeasuredWidth()) * 0.25f;
                reactionsLayout.bigCircleOffset -= popupLayoutLeftOffset;
                if (reactionsLayout.bigCircleOffset < dp(36)) {
                    popupLayoutLeftOffset = 0;
                    reactionsLayout.bigCircleOffset = dp(36);
                }
                updatePopupTranslation();
            }
            if (bottomView != null) {
                if (reactionsLayout.showCustomEmojiReaction()) {
                    bottomView.getLayoutParams().width = menuContainer.getMeasuredWidth() + dp(16);
                    updatePopupTranslation();
                } else {
                    bottomView.getLayoutParams().width = LayoutHelper.MATCH_PARENT;
                }
                if (popupWindowLayout.getSwipeBack() != null) {
                    ((LayoutParams) bottomView.getLayoutParams()).rightMargin = widthDiff + dp(36);
                } else {
                    ((LayoutParams) bottomView.getLayoutParams()).rightMargin = dp(36);
                }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
        maxHeight = getMeasuredHeight();
    }

    private void updatePopupTranslation() {
        float x = (1f - progressToSwipeBack) * popupLayoutLeftOffset;
        popupWindowLayout.setTranslationX(x);
        if (bottomView != null) {
            bottomView.setTranslationX(x);
        }
    }

    public void applyViewBottom(FrameLayout bottomView) {
        this.bottomView = bottomView;
    }

    public void setReactionsLayout(ReactionsContainerLayout reactionsLayout) {
        this.reactionsLayout = reactionsLayout;
        if (reactionsLayout != null) {
            reactionsLayout.setChatScrimView(this);
        }
    }

    public void setPopupWindowLayout(ActionBarPopupWindow.ActionBarPopupWindowLayout popupWindowLayout) {
        this.popupWindowLayout = popupWindowLayout;
        popupWindowLayout.setOnSizeChangedListener(() -> {
            if (bottomView != null) {
                bottomViewYOffset = popupWindowLayout.getVisibleHeight() - popupWindowLayout.getMeasuredHeight();
                updateBottomViewPosition();
            }
        });
        if (popupWindowLayout.getSwipeBack() != null) {
            popupWindowLayout.getSwipeBack().addOnSwipeBackProgressListener((layout, toProgress, progress) -> {
                if (bottomView != null) {
                    bottomView.setAlpha(1f - progress);
                }
                progressToSwipeBack = progress;
                updatePopupTranslation();
            });
        }
    }

    private void updateBottomViewPosition() {
        if (bottomView != null) {
            bottomView.setTranslationY(bottomViewYOffset + expandSize + bottomViewReactionsOffset);
        }
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
    }

    public void setExpandSize(float expandSize) {
        popupWindowLayout.setTranslationY(expandSize);
        this.expandSize = expandSize;
        updateBottomViewPosition();
    }

    public void setPopupAlpha(float alpha) {
        popupWindowLayout.setAlpha(alpha);
        if (bottomView != null) {
            bottomView.setAlpha(alpha);
        }
    }

    public void setReactionsTransitionProgress(float v) {
        popupWindowLayout.setReactionsTransitionProgress(v);
        if (bottomView != null) {
            bottomView.setAlpha(v);
            float scale = 0.5f + v * 0.5f;
            bottomView.setPivotX(bottomView.getMeasuredWidth());
            bottomView.setPivotY(0);
            bottomViewReactionsOffset = -popupWindowLayout.getMeasuredHeight() * (1f - v);
            updateBottomViewPosition();
            bottomView.setScaleX(scale);
            bottomView.setScaleY(scale);
        }
    }
}
