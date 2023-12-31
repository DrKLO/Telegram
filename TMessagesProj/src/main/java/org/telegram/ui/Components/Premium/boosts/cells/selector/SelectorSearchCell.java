package org.telegram.ui.Components.Premium.boosts.cells.selector;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.GroupCreateSpan;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@SuppressLint("ViewConstructor")
public class SelectorSearchCell extends ScrollView {

    private final Theme.ResourcesProvider resourcesProvider;
    private EditTextBoldCursor editText;
    private int hintTextWidth;
    public SpansContainer spansContainer;
    private int selectedCount;
    public ArrayList<GroupCreateSpan> allSpans = new ArrayList<>();
    private GroupCreateSpan currentDeletingSpan;
    private Runnable updateHeight;
    private boolean ignoreTextChange;
    private Utilities.Callback<String> onSearchTextChange;

    public EditTextBoldCursor getEditText() {
        return editText;
    }

    public SelectorSearchCell(Context context, Theme.ResourcesProvider resourcesProvider, Runnable updateHeight) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.updateHeight = updateHeight;

        setVerticalScrollBarEnabled(false);
        AndroidUtilities.setScrollViewEdgeEffectColor(this, Theme.getColor(Theme.key_windowBackgroundWhite));

        spansContainer = new SpansContainer(context);
        addView(spansContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        editText = new EditTextBoldCursor(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (currentDeletingSpan != null) {
                    currentDeletingSpan.cancelDeleteAnimation();
                    currentDeletingSpan = null;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!AndroidUtilities.showKeyboard(this)) {
                        fullScroll(View.FOCUS_DOWN);
                        clearFocus();
                        requestFocus();
                    }
                }
                return super.onTouchEvent(event);
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            editText.setRevealOnFocusHint(false);
        }
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, resourcesProvider));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        editText.setCursorColor(Theme.getColor(Theme.key_groupcreate_cursor, resourcesProvider));
        editText.setHandlesColor(Theme.getColor(Theme.key_groupcreate_cursor, resourcesProvider));
        editText.setCursorWidth(1.5f);
        editText.setInputType(InputType.TYPE_TEXT_VARIATION_FILTER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setSingleLine(true);
        editText.setBackgroundDrawable(null);
        editText.setVerticalScrollBarEnabled(false);
        editText.setHorizontalScrollBarEnabled(false);
        editText.setTextIsSelectable(false);
        editText.setPadding(0, 0, 0, 0);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        spansContainer.addView(editText);
        editText.setHintText(LocaleController.getString("Search", R.string.Search));
        hintTextWidth = (int) editText.getPaint().measureText(LocaleController.getString("Search", R.string.Search));
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (ignoreTextChange) {
                    return;
                }
                if (onSearchTextChange != null && s != null) {
                    onSearchTextChange.run(s.toString());
                }
            }
        });
    }

    public void setHintText(String text, boolean animated) {
        editText.setHintText(text, animated);
    }

    private final AnimatedFloat topGradientAlpha = new AnimatedFloat(this, 0, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final LinearGradient topGradient = new LinearGradient(0, 0, 0, dp(8), new int[]{0xff000000, 0x00000000}, new float[]{0, 1}, Shader.TileMode.CLAMP);
    private final Paint topGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix topGradientMatrix = new Matrix();

    private final AnimatedFloat bottomGradientAlpha = new AnimatedFloat(this, 0, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final LinearGradient bottomGradient = new LinearGradient(0, 0, 0, dp(8), new int[]{0x00000000, 0xff000000}, new float[]{0, 1}, Shader.TileMode.CLAMP);
    private final Paint bottomGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix bottomGradientMatrix = new Matrix();

    {
        topGradientPaint.setShader(topGradient);
        topGradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        bottomGradientPaint.setShader(bottomGradient);
        bottomGradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    public void updateSpans(boolean animated, HashSet<Long> selectedIds, Runnable afterDelete, List<TLRPC.TL_help_country> countries) {
        final MessagesController messagesController = MessagesController.getInstance(UserConfig.selectedAccount);
        final ArrayList<GroupCreateSpan> toDelete = new ArrayList<>();
        final ArrayList<GroupCreateSpan> toAdd = new ArrayList<>();

        for (int i = 0; i < allSpans.size(); ++i) {
            GroupCreateSpan span = allSpans.get(i);
            if (!selectedIds.contains(span.getUid())) {
                toDelete.add(span);
            }
        }

        for (long id : selectedIds) {
            boolean found = false;
            for (int j = 0; j < allSpans.size(); ++j) {
                GroupCreateSpan span = allSpans.get(j);
                if (span.getUid() == id) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                TLObject obj;
                if (id >= 0) {
                    obj = messagesController.getUser(id);
                } else {
                    obj = messagesController.getChat(-id);
                }
                if (countries != null) {
                    for (TLRPC.TL_help_country country : countries) {
                        long countryHash = country.default_name.hashCode();
                        if (countryHash == id) {
                            obj = country;
                            break;
                        }
                    }
                }
                if (obj == null) {
                    continue;
                }
                GroupCreateSpan span = new GroupCreateSpan(getContext(), obj, null, true, resourcesProvider);
                span.setOnClickListener(v -> {
                    onDeleteSpanClicked(v, selectedIds, afterDelete);
                });
                toAdd.add(span);
            }
        }

        if (!toDelete.isEmpty() || !toAdd.isEmpty()) {
            spansContainer.updateSpans(toDelete, toAdd, animated);
        }

        editText.setOnKeyListener(new View.OnKeyListener() {

            private boolean wasEmpty;

            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        wasEmpty = editText.length() == 0;
                    } else if (event.getAction() == KeyEvent.ACTION_UP && wasEmpty && !allSpans.isEmpty()) {
                        GroupCreateSpan span = allSpans.get(allSpans.size() - 1);
                        onDeleteSpanClicked(span, selectedIds, afterDelete);
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void onDeleteSpanClicked(View view, HashSet<Long> selectedIds, Runnable afterDelete) {
        if (!allSpans.contains(view)) {
            return;
        }
        GroupCreateSpan deletingSpan = (GroupCreateSpan) view;
        if (deletingSpan.isDeleting()) {
            currentDeletingSpan = null;
            spansContainer.removeSpan(deletingSpan);
            long deletingId = deletingSpan.getUid();
            selectedIds.remove(deletingId);
            afterDelete.run();
        } else {
            if (currentDeletingSpan != null) {
                currentDeletingSpan.cancelDeleteAnimation();
                currentDeletingSpan = null;
            }
            currentDeletingSpan = deletingSpan;
            deletingSpan.startDeleteAnimation();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final int y = getScrollY();

        canvas.saveLayerAlpha(0, y, getWidth(), y + getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
        super.dispatchDraw(canvas);

        canvas.save();

        float alpha = topGradientAlpha.set(canScrollVertically(-1));
        topGradientMatrix.reset();
        topGradientMatrix.postTranslate(0, y);
        topGradient.setLocalMatrix(topGradientMatrix);
        topGradientPaint.setAlpha((int) (0xFF * alpha));
        canvas.drawRect(0, y, getWidth(), y + dp(8), topGradientPaint);

        alpha = bottomGradientAlpha.set(canScrollVertically(1));
        bottomGradientMatrix.reset();
        bottomGradientMatrix.postTranslate(0, y + getHeight() - dp(8));
        bottomGradient.setLocalMatrix(bottomGradientMatrix);
        bottomGradientPaint.setAlpha((int) (0xFF * alpha));
        canvas.drawRect(0, y + getHeight() - dp(8), getWidth(), y + getHeight(), bottomGradientPaint);

        canvas.restore();

        canvas.restore();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev);
    }

    public void setText(CharSequence text) {
        ignoreTextChange = true;
        editText.setText(text);
        ignoreTextChange = false;
    }

    public void setOnSearchTextChange(Utilities.Callback<String> listener) {
        this.onSearchTextChange = listener;
    }

    private boolean ignoreScrollEvent;
    private int fieldY;
    public float containerHeight;
    public int resultContainerHeight;
    private int prevResultContainerHeight;

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        if (ignoreScrollEvent) {
            ignoreScrollEvent = false;
            return false;
        }
        rectangle.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());
        rectangle.top += fieldY + AndroidUtilities.dp(20);
        rectangle.bottom += fieldY + AndroidUtilities.dp(50);
        return super.requestChildRectangleOnScreen(child, rectangle, immediate);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(150), MeasureSpec.AT_MOST)
        );
    }

    public void setContainerHeight(float value) {
        containerHeight = value;
        if (spansContainer != null) {
            spansContainer.requestLayout();
        }
    }

    protected Animator getContainerHeightAnimator(float newHeight) {
        ValueAnimator animator = ValueAnimator.ofFloat(this.containerHeight, newHeight);
        animator.addUpdateListener(anm -> setContainerHeight((float) anm.getAnimatedValue()));
        return animator;
    }

    private boolean scroll;

    public void scrollToBottom() {
        scroll = true;
    }

    public class SpansContainer extends ViewGroup {

        private AnimatorSet currentAnimation;
        private boolean animationStarted;
        private ArrayList<View> animAddingSpans = new ArrayList<>();
        private ArrayList<View> animRemovingSpans = new ArrayList<>();
        private ArrayList<Animator> animators = new ArrayList<>();
        private View addingSpan;
        private final ArrayList<View> removingSpans = new ArrayList<>();

        private final int padDp = 14;
        private final int padYDp = 4;
        private final int padXDp = 6;
        private final int heightDp = 28; // 32;

        public SpansContainer(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int count = getChildCount();
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int maxWidth = width - AndroidUtilities.dp(padDp * 2);
            int currentLineWidth = 0;
            int y = AndroidUtilities.dp(10);
            int allCurrentLineWidth = 0;
            int allY = AndroidUtilities.dp(10);
            int x;
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                if (!(child instanceof GroupCreateSpan)) {
                    continue;
                }
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(heightDp), MeasureSpec.EXACTLY));
                boolean isRemoving = removingSpans.contains(child);
                if (!isRemoving && currentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    y += child.getMeasuredHeight() + dp(padYDp);
                    currentLineWidth = 0;
                }
                if (allCurrentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    allY += child.getMeasuredHeight() + AndroidUtilities.dp(padYDp);
                    allCurrentLineWidth = 0;
                }
                x = AndroidUtilities.dp(padDp) + currentLineWidth;
                if (!animationStarted) {
                    if (isRemoving) {
                        child.setTranslationX(AndroidUtilities.dp(padDp) + allCurrentLineWidth);
                        child.setTranslationY(allY);
                    } else if (!removingSpans.isEmpty()) {
                        if (child.getTranslationX() != x) {
                            animators.add(ObjectAnimator.ofFloat(child, View.TRANSLATION_X, x));
                        }
                        if (child.getTranslationY() != y) {
                            animators.add(ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, y));
                        }
                    } else {
                        child.setTranslationX(x);
                        child.setTranslationY(y);
                    }
                }
                if (!isRemoving) {
                    currentLineWidth += child.getMeasuredWidth() + AndroidUtilities.dp(padXDp);
                }
                allCurrentLineWidth += child.getMeasuredWidth() + AndroidUtilities.dp(padXDp);
            }
            int minWidth;
            if (AndroidUtilities.isTablet()) {
                minWidth = AndroidUtilities.dp(530 - padDp * 2 - padXDp * 2 - 57 * 2) / 3;
            } else {
                minWidth = (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(padDp * 2 + padXDp * 2 + 57 * 2)) / 3;
            }
            if (maxWidth - currentLineWidth < minWidth) {
                currentLineWidth = 0;
                y += AndroidUtilities.dp(heightDp + 8);
            }
            if (maxWidth - allCurrentLineWidth < minWidth) {
                allY += AndroidUtilities.dp(heightDp + 8);
            }
            editText.measure(MeasureSpec.makeMeasureSpec(maxWidth - currentLineWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(heightDp), MeasureSpec.EXACTLY));
            editText.setHintVisible(editText.getMeasuredWidth() > hintTextWidth, true);
            if (!animationStarted) {
                int currentHeight = allY + AndroidUtilities.dp(heightDp + 10);
                int fieldX = currentLineWidth + AndroidUtilities.dp(16);
                fieldY = y;
                if (currentAnimation != null) {
                    int resultHeight = y + AndroidUtilities.dp(heightDp + 10);
                    resultContainerHeight = resultHeight;
                    if (containerHeight != resultHeight) {
                        animators.add(getContainerHeightAnimator(resultHeight));
                    }
                    if (editText.getTranslationX() != fieldX) {
                        animators.add(ObjectAnimator.ofFloat(editText, View.TRANSLATION_X, fieldX));
                    }
                    if (editText.getTranslationY() != fieldY) {
                        animators.add(ObjectAnimator.ofFloat(editText, View.TRANSLATION_Y, fieldY));
                    }
                    editText.setAllowDrawCursor(false);
                    currentAnimation.playTogether(animators);
                    currentAnimation.setDuration(180);
                    currentAnimation.setInterpolator(new LinearInterpolator());
                    currentAnimation.start();
                    animationStarted = true;
                    if (updateHeight != null) {
                        updateHeight.run();
                    }
                } else {
                    containerHeight = resultContainerHeight = currentHeight;
                    editText.setTranslationX(fieldX);
                    editText.setTranslationY(fieldY);
                    if (updateHeight != null) {
                        updateHeight.run();
                    }
                    if (scroll) {
                        post(() -> fullScroll(View.FOCUS_DOWN));
                        scroll = false;
                    }
                }
                prevResultContainerHeight = resultContainerHeight;
            } else if (currentAnimation != null) {
                if (!ignoreScrollEvent && removingSpans.isEmpty()) {
                    editText.bringPointIntoView(editText.getSelectionStart());
                }
                if (scroll) {
                    fullScroll(View.FOCUS_DOWN);
                    scroll = false;
                }
            }
            setMeasuredDimension(width, (int) containerHeight);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int count = getChildCount();
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
            }
        }

        public void removeSpan(final GroupCreateSpan span) {
            ignoreScrollEvent = true;
            allSpans.remove(span);
            span.setOnClickListener(null);

            setupEndValues();
            animationStarted = false;
            currentAnimation = new AnimatorSet();
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    removeView(span);
                    removingSpans.clear();
                    currentAnimation = null;
                    animationStarted = false;
                    editText.setAllowDrawCursor(true);
                    if (updateHeight != null) {
                        updateHeight.run();
                    }
                    if (scroll) {
                        fullScroll(View.FOCUS_DOWN);
                        scroll = false;
                    }
                }
            });
            removingSpans.clear();
            removingSpans.add(span);
            animAddingSpans.clear();
            animRemovingSpans.clear();
            animAddingSpans.add(span);
            animators.clear();
            animators.add(ObjectAnimator.ofFloat(span, View.SCALE_X, 1.0f, 0.01f));
            animators.add(ObjectAnimator.ofFloat(span, View.SCALE_Y, 1.0f, 0.01f));
            animators.add(ObjectAnimator.ofFloat(span, View.ALPHA, 1.0f, 0.0f));
            requestLayout();
        }

        public void updateSpans(ArrayList<GroupCreateSpan> toDelete, ArrayList<GroupCreateSpan> toAdd, boolean animated) {
            ignoreScrollEvent = true;

            allSpans.removeAll(toDelete);
            allSpans.addAll(toAdd);

            removingSpans.clear();
            removingSpans.addAll(toDelete);

            for (int i = 0; i < toDelete.size(); ++i) {
                toDelete.get(i).setOnClickListener(null);
            }

            setupEndValues();
            if (animated) {
                animationStarted = false;
                currentAnimation = new AnimatorSet();
                currentAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        for (int i = 0; i < toDelete.size(); ++i) {
                            removeView(toDelete.get(i));
                        }
                        addingSpan = null;
                        removingSpans.clear();
                        currentAnimation = null;
                        animationStarted = false;
                        editText.setAllowDrawCursor(true);
                        if (updateHeight != null) {
                            updateHeight.run();
                        }
                        if (scroll) {
                            fullScroll(View.FOCUS_DOWN);
                            scroll = false;
                        }
                    }
                });
                animators.clear();
                animAddingSpans.clear();
                animRemovingSpans.clear();
                for (int i = 0; i < toDelete.size(); ++i) {
                    GroupCreateSpan span = toDelete.get(i);
                    animRemovingSpans.add(span);
                    animators.add(ObjectAnimator.ofFloat(span, View.SCALE_X, 1.0f, 0.01f));
                    animators.add(ObjectAnimator.ofFloat(span, View.SCALE_Y, 1.0f, 0.01f));
                    animators.add(ObjectAnimator.ofFloat(span, View.ALPHA, 1.0f, 0.0f));
                }
                for (int i = 0; i < toAdd.size(); ++i) {
                    GroupCreateSpan addingSpan = toAdd.get(i);
                    animAddingSpans.add(addingSpan);
                    animators.add(ObjectAnimator.ofFloat(addingSpan, View.SCALE_X, 0.01f, 1.0f));
                    animators.add(ObjectAnimator.ofFloat(addingSpan, View.SCALE_Y, 0.01f, 1.0f));
                    animators.add(ObjectAnimator.ofFloat(addingSpan, View.ALPHA, 0.0f, 1.0f));
                }
            } else {
                for (int i = 0; i < toDelete.size(); ++i) {
                    removeView(toDelete.get(i));
                }
                addingSpan = null;
                removingSpans.clear();
                currentAnimation = null;
                animationStarted = false;
                editText.setAllowDrawCursor(true);
            }
            for (int i = 0; i < toAdd.size(); ++i) {
                addView(toAdd.get(i));
            }
            requestLayout();
        }

        public void removeAllSpans(boolean animated) {
            ignoreScrollEvent = true;

            ArrayList<GroupCreateSpan> spans = new ArrayList<>(allSpans);
            removingSpans.clear();
            removingSpans.addAll(allSpans);
            allSpans.clear();

            for (int i = 0; i < spans.size(); ++i) {
                spans.get(i).setOnClickListener(null);
            }

            setupEndValues();
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
                        editText.setAllowDrawCursor(true);
                        if (updateHeight != null) {
                            updateHeight.run();
                        }
                        if (scroll) {
                            fullScroll(View.FOCUS_DOWN);
                            scroll = false;
                        }
                    }
                });
                animators.clear();
                animAddingSpans.clear();
                animRemovingSpans.clear();
                for (int i = 0; i < spans.size(); ++i) {
                    GroupCreateSpan span = spans.get(i);
                    animAddingSpans.add(span);
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
                editText.setAllowDrawCursor(true);
            }
            requestLayout();
        }

        private void setupEndValues() {
            if (currentAnimation != null) {
                currentAnimation.cancel();
            }
            for (int i = 0; i < animAddingSpans.size(); ++i) {
                animAddingSpans.get(i).setScaleX(1f);
                animAddingSpans.get(i).setScaleY(1f);
                animAddingSpans.get(i).setAlpha(1f);
            }
            for (int i = 0; i < animRemovingSpans.size(); ++i) {
                animRemovingSpans.get(i).setScaleX(0f);
                animRemovingSpans.get(i).setScaleY(0f);
                animRemovingSpans.get(i).setAlpha(0f);
            }
            animAddingSpans.clear();
            animRemovingSpans.clear();
        }
    }
}