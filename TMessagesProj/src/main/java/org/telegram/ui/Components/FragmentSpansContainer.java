package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class FragmentSpansContainer extends ScrollView {
    private final int currentAccount;

    public final LongSparseArray<GroupCreateSpan> selectedContacts = new LongSparseArray<>();
    public final ArrayList<GroupCreateSpan> allSpans = new ArrayList<>();
    private final SpansContainer spansContainer;
    private int visualHeight;
    private Delegate delegate;
    private boolean ignoreScrollEvent;
    private int fieldY;

    public FragmentSpansContainer(Context context, int currentAccount) {
        super(context);
        this.currentAccount = currentAccount;
        this.spansContainer = new SpansContainer(context);

        setVerticalScrollBarEnabled(false);

        addView(spansContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    public ViewGroup getSpansContainer() {
        return spansContainer;
    }

    public interface Delegate {
        void onAfterMeasure(int height);
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
        final float h = visualHeight;
        final float y = ev.getY();
        if (action == MotionEvent.ACTION_DOWN && y > h) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        if (ignoreScrollEvent) {
            ignoreScrollEvent = false;
            return false;
        }
        rectangle.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());
        rectangle.top += fieldY + dp(20);
        rectangle.bottom += fieldY + dp(50);
        return super.requestChildRectangleOnScreen(child, rectangle, immediate);
    }

    public void addSpan(final GroupCreateSpan span) {
        spansContainer.addSpan(span);
    }

    public void endAnimation() {
        spansContainer.endAnimation();
    }

    public void removeSpan(final GroupCreateSpan span) {
        spansContainer.removeSpan(span);
    }

    public void removeAllSpans(boolean animated) {
        spansContainer.removeAllSpans(animated);
    }

    private class SpansContainer extends ViewGroup {
        private AnimatorSet currentAnimation;
        private boolean animationStarted;
        private final ArrayList<Animator> animators = new ArrayList<>();
        private View addingSpan;
        private final ArrayList<View> removingSpans = new ArrayList<>();
        private int animationIndex = -1;
        private int containerHeight;
        private int maxTy;

        public SpansContainer(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int count = getChildCount();
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int maxWidth = width - dp(26);
            int currentLineWidth = 0;
            int y = dp(10);
            int allCurrentLineWidth = 0;
            int allY = dp(10);
            int x;

            if (!animationStarted) {
                maxTy = 0;
            }

            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                if (!(child instanceof GroupCreateSpan)) {
                    continue;
                }
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(32), MeasureSpec.EXACTLY));
                boolean isRemoving = removingSpans.contains(child);
                if (!isRemoving && currentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    y += child.getMeasuredHeight() + dp(8);
                    currentLineWidth = 0;
                }
                if (allCurrentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    allY += child.getMeasuredHeight() + dp(8);
                    allCurrentLineWidth = 0;
                }
                x = dp(13) + currentLineWidth;
                if (!animationStarted) {
                    if (isRemoving) {
                        child.setTranslationX(dp(13) + allCurrentLineWidth);
                        child.setTranslationY(allY);
                    } else if (!removingSpans.isEmpty()) {
                        if (child.getTranslationX() != x) {
                            animators.add(ObjectAnimator.ofFloat(child, View.TRANSLATION_X, x));
                        }
                        if (child.getTranslationY() != y) {
                            animators.add(ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, y));
                        }
                        maxTy = Math.max(maxTy, y);
                    } else {
                        child.setTranslationX(x);
                        child.setTranslationY(y);
                        maxTy = Math.max(maxTy, y);
                    }
                }
                if (!isRemoving) {
                    currentLineWidth += child.getMeasuredWidth() + dp(9);
                }
                allCurrentLineWidth += child.getMeasuredWidth() + dp(9);
            }
            int minWidth;
            if (AndroidUtilities.isTablet()) {
                minWidth = dp(530 - 26 - 18 - 57 * 2) / 3;
            } else {
                minWidth = (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - dp(26 + 18 + 57 * 2)) / 3;
            }
            if (maxWidth - currentLineWidth < minWidth) {
                currentLineWidth = 0;
                y += dp(32 + 8);
            }
            if (maxWidth - allCurrentLineWidth < minWidth) {
                allY += dp(32 + 8);
            }
            if (!animationStarted) {
                int currentHeight = allY + dp(32 + 10);
                fieldY = y;
                if (currentAnimation != null) {
                    containerHeight = y + dp(32 + 10);
                    currentAnimation.playTogether(animators);
                    currentAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex);
                            requestLayout();
                        }
                    });
                    animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null);
                    currentAnimation.start();
                    animationStarted = true;
                } else {
                    containerHeight = currentHeight;
                }
            }

            visualHeight = maxTy > 0 ? maxTy + dp(40) : 0;
            setMeasuredDimension(width, containerHeight);
            if (delegate != null) {
                delegate.onAfterMeasure(visualHeight);
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            for (int a = 0, N = getChildCount(); a < N; a++) {
                final View child = getChildAt(a);
                child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
            }
        }

        public void addSpan(final GroupCreateSpan span) {
            allSpans.add(span);
            if (!span.isFlag) {
                selectedContacts.put(span.getUid(), span);
            }

            if (currentAnimation != null && currentAnimation.isRunning()) {
                currentAnimation.setupEndValues();
                currentAnimation.cancel();
            }
            animationStarted = false;
            currentAnimation = new AnimatorSet();
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    addingSpan = null;
                    currentAnimation = null;
                    animationStarted = false;
                }
            });
            currentAnimation.setDuration(150);
            addingSpan = span;
            animators.clear();
            animators.add(ObjectAnimator.ofFloat(addingSpan, View.SCALE_X, 0.01f, 1.0f));
            animators.add(ObjectAnimator.ofFloat(addingSpan, View.SCALE_Y, 0.01f, 1.0f));
            animators.add(ObjectAnimator.ofFloat(addingSpan, View.ALPHA, 0.0f, 1.0f));
            addView(span);
        }

        public void endAnimation() {
            if (currentAnimation != null && currentAnimation.isRunning()) {
                currentAnimation.setupEndValues();
                currentAnimation.cancel();
            }
        }

        public void removeSpan(final GroupCreateSpan span) {
            ignoreScrollEvent = true;
            if (!span.isFlag) {
                selectedContacts.remove(span.getUid());
            }
            allSpans.remove(span);
            span.setOnClickListener(null);

            if (currentAnimation != null) {
                currentAnimation.setupEndValues();
                currentAnimation.cancel();
            }
            animationStarted = false;
            currentAnimation = new AnimatorSet();
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    removeView(span);
                    removingSpans.clear();
                    currentAnimation = null;
                    animationStarted = false;
                }
            });
            currentAnimation.setDuration(150);
            removingSpans.clear();
            removingSpans.add(span);
            animators.clear();
            animators.add(ObjectAnimator.ofFloat(span, View.SCALE_X, 1.0f, 0.01f));
            animators.add(ObjectAnimator.ofFloat(span, View.SCALE_Y, 1.0f, 0.01f));
            animators.add(ObjectAnimator.ofFloat(span, View.ALPHA, 1.0f, 0.0f));
            requestLayout();
        }

        public void removeAllSpans(boolean animated) {
            ignoreScrollEvent = true;

            ArrayList<GroupCreateSpan> spans = new ArrayList<>(allSpans);
            allSpans.clear();

            removingSpans.clear();
            removingSpans.addAll(spans);

            for (int i = 0; i < spans.size(); ++i) {
                spans.get(i).setOnClickListener(null);
            }

            endAnimation();
            if (animated) {
                animationStarted = false;
                currentAnimation = new AnimatorSet();
                currentAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        for (int i = 0; i < spans.size(); ++i) {
                            removeView(spans.get(i));
                        }
                        removingSpans.clear();
                        currentAnimation = null;
                        animationStarted = false;
                    }
                });
                animators.clear();
                for (int i = 0; i < spans.size(); ++i) {
                    GroupCreateSpan span = spans.get(i);
                    animators.add(ObjectAnimator.ofFloat(span, View.SCALE_X, 1.0f, 0.01f));
                    animators.add(ObjectAnimator.ofFloat(span, View.SCALE_Y, 1.0f, 0.01f));
                    animators.add(ObjectAnimator.ofFloat(span, View.ALPHA, 1.0f, 0.0f));
                }
            } else {
                for (int i = 0; i < spans.size(); ++i) {
                    removeView(spans.get(i));
                }
                removingSpans.clear();
                currentAnimation = null;
                animationStarted = false;
            }
            requestLayout();
        }
    }
}
