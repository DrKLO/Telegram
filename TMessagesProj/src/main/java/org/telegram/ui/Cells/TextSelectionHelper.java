package org.telegram.ui.Cells;

import static com.google.zxing.common.detector.MathUtils.distance;
import static org.telegram.ui.ActionBar.FloatingToolbar.STYLE_THEME;
import static org.telegram.ui.ActionBar.Theme.key_chat_inTextSelectionHighlight;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Magnifier;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.FloatingActionMode;
import org.telegram.ui.ActionBar.FloatingToolbar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.RestrictedLanguagesSelectActivity;

import java.util.ArrayList;

public abstract class TextSelectionHelper<Cell extends TextSelectionHelper.SelectableView> {

    protected int textX;
    protected int textY;
    protected int maybeTextX;
    protected int maybeTextY;

    float movingOffsetX;
    float movingOffsetY;

    protected int[] tmpCoord = new int[2];
    private boolean movingHandle;
    protected boolean movingHandleStart;
    private boolean isOneTouch;

    private int longpressDelay;
    private int touchSlop;
    protected PathWithSavedBottom path = new PathWithSavedBottom();

    protected Paint selectionPaint = new Paint();

    protected int capturedX;
    protected int capturedY;
    protected int selectionStart = -1;
    protected int selectionEnd = -1;
    protected int selectedCellId;
    private int topOffset;
    private boolean snap;

    private boolean tryCapture;

    private final ActionMode.Callback textSelectActionCallback = createActionCallback();
    protected final Rect textArea = new Rect();
    protected TextSelectionOverlay textSelectionOverlay;

    private Callback callback;

    protected RecyclerListView parentRecyclerView;
    protected ViewGroup parentView;
    private Magnifier magnifier;
    private float magnifierYanimated;
    private float magnifierY;
    private float magnifierDy;
    private boolean scrolling;
    private boolean scrollDown;
    protected boolean actionsIsShowing;
    private boolean parentIsScrolling;
    protected boolean movingDirectionSettling;

    private RectF startArea = new RectF();
    private RectF endArea = new RectF();

    protected float enterProgress;
    protected float handleViewProgress;
    protected Cell selectedView;
    protected Cell maybeSelectedView;
    private ActionMode actionMode;

    protected boolean multiselect;

    protected final LayoutBlock layoutBlock = new LayoutBlock();

    private int lastX;
    private int lastY;
    private Interpolator interpolator = new OvershootInterpolator();

    protected boolean showActionsAsPopupAlways = false;

    int keyboardSize;

    private Runnable scrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (scrolling && parentRecyclerView != null) {
                int dy;
                if (multiselect && selectedView == null) {
                    dy = AndroidUtilities.dp(8);
                } else if (selectedView != null) {
                    dy = getLineHeight() >> 1;
                } else {
                    return;
                }

                if (!multiselect) {
                    if (scrollDown) {
                        if (selectedView.getBottom() - dy < parentView.getMeasuredHeight()) {
                            dy = selectedView.getBottom() - parentView.getMeasuredHeight();
                        }
                    } else {
                        if (selectedView.getTop() + dy > getParentTopPadding()) {
                            dy = -selectedView.getTop() + getParentTopPadding();
                        }
                    }
                }
                parentRecyclerView.scrollBy(0, scrollDown ? dy : -dy);
                AndroidUtilities.runOnUIThread(this);
            }
        }
    };

    final Runnable startSelectionRunnable = new Runnable() {

        @Override
        public void run() {
            if (maybeSelectedView == null || textSelectionOverlay == null) {
                return;
            }

            Cell oldView = selectedView;
            Cell newView = maybeSelectedView;
            CharSequence text = getText(maybeSelectedView, true);
            if (parentRecyclerView != null) {
                parentRecyclerView.cancelClickRunnables(false);
            }

            int x = capturedX;
            int y = capturedY;
            if (!textArea.isEmpty()) {
                if (x > textArea.right) x = textArea.right - 1;
                if (x < textArea.left) x = textArea.left + 1;
                if (y < textArea.top) y = textArea.top + 1;
                if (y > textArea.bottom) y = textArea.bottom - 1;
            }

            int offset = getCharOffsetFromCord(x, y, maybeTextX, maybeTextY, newView, true);
            if (offset >= text.length()) {
                fillLayoutForOffset(offset, layoutBlock, true);
                if (layoutBlock.layout == null) {
                    selectionStart = selectionEnd = -1;
                    return;
                }
                int endLine = layoutBlock.layout.getLineCount() - 1;
                x -= maybeTextX;
                if (x < layoutBlock.layout.getLineRight(endLine) + AndroidUtilities.dp(4) && x > layoutBlock.layout.getLineLeft(endLine)) {
                    offset = text.length() - 1;
                }
            }
            if (offset >= 0 && offset < text.length() && text.charAt(offset) != '\n') {
                int maybeTextX = TextSelectionHelper.this.maybeTextX;
                int maybeTextY = TextSelectionHelper.this.maybeTextY;
                clear();
                textSelectionOverlay.setVisibility(View.VISIBLE);
                onTextSelected(newView, oldView);
                selectionStart = offset;
                selectionEnd = selectionStart;

                if (text instanceof Spanned) {
                    Emoji.EmojiSpan[] spans = ((Spanned) text).getSpans(0, text.length(), Emoji.EmojiSpan.class);
                    for (Emoji.EmojiSpan emojiSpan : spans) {
                        int s = ((Spanned) text).getSpanStart(emojiSpan);
                        int e = ((Spanned) text).getSpanEnd(emojiSpan);
                        if (offset >= s && offset <= e) {
                            selectionStart = s;
                            selectionEnd = e;
                            break;
                        }
                    }
                }

                if (selectionStart == selectionEnd) {
                    while (selectionStart > 0 && isInterruptedCharacter(text.charAt(selectionStart - 1))) {
                        selectionStart--;
                    }

                    while (selectionEnd < text.length() && isInterruptedCharacter(text.charAt(selectionEnd))) {
                        selectionEnd++;
                    }
                }

                textX = maybeTextX;
                textY = maybeTextY;

                selectedView = newView;
                textSelectionOverlay.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showActions();
                invalidate();

                if (oldView != null) {
                    oldView.invalidate();
                }

                if (callback != null) {
                    callback.onStateChanged(true);
                }

                movingHandle = true;
                movingDirectionSettling = true;
                isOneTouch = true;
                movingOffsetY = 0;
                movingOffsetX = 0;
                onOffsetChanged();
            }
            tryCapture = false;

        }
    };

    public TextSelectionHelper() {
        longpressDelay = ViewConfiguration.getLongPressTimeout();
        touchSlop = ViewConfiguration.get(ApplicationLoader.applicationContext).getScaledTouchSlop();
    }

    public interface OnTranslateListener {
        public void run(CharSequence text, String fromLang, String toLang, Runnable onAlertDismiss);
    }
    private OnTranslateListener onTranslateListener = null;
    public void setOnTranslate(OnTranslateListener listener) {
        onTranslateListener = listener;
    }

    public void setParentView(ViewGroup view) {
        if (view instanceof RecyclerListView) {
            parentRecyclerView = (RecyclerListView) view;
        }
        parentView = view;
    }

    public void setMaybeTextCord(int x, int y) {
        maybeTextX = x;
        maybeTextY = y;
    }

    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                capturedX = (int) event.getX();
                capturedY = (int) event.getY();
                tryCapture = false;
                textArea.inset(-AndroidUtilities.dp(8), -AndroidUtilities.dp(8));
                if (textArea.contains(capturedX, capturedY)) {
                    textArea.inset(AndroidUtilities.dp(8), AndroidUtilities.dp(8));
                    int x = capturedX;
                    int y = capturedY;
                    if (x > textArea.right) x = textArea.right - 1;
                    if (x < textArea.left) x = textArea.left + 1;
                    if (y < textArea.top) y = textArea.top + 1;
                    if (y > textArea.bottom) y = textArea.bottom - 1;

                    int offset = getCharOffsetFromCord(x, y, maybeTextX, maybeTextY, maybeSelectedView, true);
                    CharSequence text = getText(maybeSelectedView, true);
                    if (offset >= text.length()) {
                        fillLayoutForOffset(offset, layoutBlock, true);
                        if (layoutBlock.layout == null) {
                            tryCapture = false;
                            return tryCapture;
                        }
                        int endLine = layoutBlock.layout.getLineCount() - 1;
                        x -= maybeTextX;
                        if (x < layoutBlock.layout.getLineRight(endLine) + AndroidUtilities.dp(4) && x > layoutBlock.layout.getLineLeft(endLine)) {
                            offset = text.length() - 1;
                        }
                    }
                    if (offset >= 0 && offset < text.length() && text.charAt(offset) != '\n') {
                        AndroidUtilities.runOnUIThread(startSelectionRunnable, longpressDelay);
                        tryCapture = true;
                    }
                }
                return tryCapture;

            case MotionEvent.ACTION_MOVE:
                int y = (int) event.getY();
                int x = (int) event.getX();
                int r = (capturedY - y) * (capturedY - y) + (capturedX - x) * (capturedX - x);
                if (r > touchSlop) {
                    AndroidUtilities.cancelRunOnUIThread(startSelectionRunnable);
                    tryCapture = false;
                }
                return tryCapture;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                AndroidUtilities.cancelRunOnUIThread(startSelectionRunnable);
                tryCapture = false;
                return false;
        }
        return false;
    }


    private void hideMagnifier() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            if (magnifier != null) {
                magnifier.dismiss();
                magnifier = null;
            }
        }
    }

    private void showMagnifier(int x) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            if (selectedView == null || isOneTouch || !movingHandle || textSelectionOverlay == null) {
                return;
            }
            int offset = movingHandleStart ? selectionStart : selectionEnd;

            fillLayoutForOffset(offset, layoutBlock);
            StaticLayout layout = layoutBlock.layout;
            if (layout == null) {
                return;
            }

            int line = layout.getLineForOffset(offset);

            int lineHeight = layout.getLineBottom(line) - layout.getLineTop(line);
            int newY = (int) (layout.getLineTop(line) + textY + selectedView.getY()) - lineHeight - AndroidUtilities.dp(8);
            newY += layoutBlock.yOffset;


            if (magnifierY != newY) {
                magnifierY = newY;
                magnifierDy = (newY - magnifierYanimated) / 200f;
            }

            if (magnifier == null) {
                magnifier = new Magnifier(textSelectionOverlay);
                magnifierYanimated = magnifierY;
            }

            if (magnifierYanimated != magnifierY) {
                magnifierYanimated += magnifierDy * 16;
            }

            if (magnifierDy > 0 && magnifierYanimated > magnifierY) {
                magnifierYanimated = magnifierY;
            } else if (magnifierDy < 0 && magnifierYanimated < magnifierY) {
                magnifierYanimated = magnifierY;
            }

            int startLine;
            int endLine;
            if (selectedView instanceof ArticleViewer.BlockTableCell) {
                startLine = (int) selectedView.getX();
                endLine = (int) selectedView.getX() + selectedView.getMeasuredWidth();
            } else {
                startLine = (int) (selectedView.getX() + textX + layout.getLineLeft(line));
                endLine = (int) (selectedView.getX() + textX + layout.getLineRight(line));
            }
            if (x < startLine) {
                x = startLine;
            } else if (x > endLine) {
                x = endLine;
            }
            magnifier.show(
                    x, magnifierYanimated + lineHeight * 1.5f + AndroidUtilities.dp(8)
            );
            magnifier.update();
        }
    }

    protected void showHandleViews() {
        if (handleViewProgress == 1f || textSelectionOverlay == null) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1f);
        animator.addUpdateListener(animation -> {
            handleViewProgress = (float) animation.getAnimatedValue();
            textSelectionOverlay.invalidate();
        });
        animator.setDuration(250);
        animator.start();
    }

    public boolean isSelectionMode() {
        return selectionStart >= 0 && selectionEnd >= 0;
    }

    private ActionBarPopupWindow popupWindow;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout;
    private TextView deleteView;
    private Rect popupRect;

    private void showActions() {
        if (textSelectionOverlay == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!movingHandle && isSelectionMode() && canShowActions()) {
                if (!actionsIsShowing) {
                    if (actionMode == null) {
                        FloatingToolbar floatingToolbar = new FloatingToolbar(textSelectionOverlay.getContext(), textSelectionOverlay, STYLE_THEME, getResourcesProvider());
                        actionMode = new FloatingActionMode(textSelectionOverlay.getContext(), (ActionMode.Callback2) textSelectActionCallback, textSelectionOverlay, floatingToolbar);
                        textSelectActionCallback.onCreateActionMode(actionMode, actionMode.getMenu());
                    }
                    textSelectActionCallback.onPrepareActionMode(actionMode, actionMode.getMenu());
                    actionMode.hide(1);
                }
                AndroidUtilities.cancelRunOnUIThread(hideActionsRunnable);
                actionsIsShowing = true;
            }
        } else {
            if (!showActionsAsPopupAlways) {
                if (actionMode == null && isSelectionMode()) {
                    actionMode = textSelectionOverlay.startActionMode(textSelectActionCallback);
                }
            } else {
                if (!movingHandle && isSelectionMode() && canShowActions()) {
                    if (popupLayout == null) {
                        popupRect = new android.graphics.Rect();
                        popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(textSelectionOverlay.getContext());
                        popupLayout.setPadding(AndroidUtilities.dp(1), AndroidUtilities.dp(1), AndroidUtilities.dp(1), AndroidUtilities.dp(1));
                        popupLayout.setBackgroundDrawable(textSelectionOverlay.getContext().getResources().getDrawable(R.drawable.menu_copy));
                        popupLayout.setAnimationEnabled(false);
                        popupLayout.setOnTouchListener((v, event) -> {
                            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                                if (popupWindow != null && popupWindow.isShowing()) {
                                    v.getHitRect(popupRect);
                                }
                            }
                            return false;
                        });
                        popupLayout.setShownFromBotton(false);

                        deleteView = new TextView(textSelectionOverlay.getContext());
                        deleteView.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 2));
                        deleteView.setGravity(Gravity.CENTER_VERTICAL);
                        deleteView.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
                        deleteView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                        deleteView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                        deleteView.setText(textSelectionOverlay.getContext().getString(android.R.string.copy));
                        deleteView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
                        deleteView.setOnClickListener(v -> {
                            copyText();
                        });
                        popupLayout.addView(deleteView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 48));

                        popupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
                        popupWindow.setAnimationEnabled(false);
                        popupWindow.setAnimationStyle(R.style.PopupContextAnimation);
                        popupWindow.setOutsideTouchable(true);

                        if (popupLayout != null) {
                            popupLayout.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));
                        }
                    }

                    int y = 0;
                    if (selectedView != null) {
                        int lineHeight = -getLineHeight();
                        int[] coords = offsetToCord(selectionStart);
                        y = (int) (coords[1] + textY + selectedView.getY()) + lineHeight / 2 - AndroidUtilities.dp(4);
                        if (y < 0) y = 0;
                    }

                    popupWindow.showAtLocation(textSelectionOverlay, Gravity.TOP, 0, y - AndroidUtilities.dp(48));
                    popupWindow.startAnimation();
                }
            }
        }
    }

    protected boolean canShowActions() {
        return selectedView != null;
    }

    //fast way hide floating action mode for long time
    private final Runnable hideActionsRunnable = new Runnable() {
        @Override
        public void run() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (actionMode != null && !actionsIsShowing) {
                    actionMode.hide(Long.MAX_VALUE);
                    AndroidUtilities.runOnUIThread(hideActionsRunnable, 1000);
                }
            }
        }
    };

    private void hideActions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (actionMode != null && actionsIsShowing) {
                actionsIsShowing = false;
                hideActionsRunnable.run();
            }
            actionsIsShowing = false;
        }
        if (!isSelectionMode() && actionMode != null) {
            actionMode.finish();
            actionMode = null;
        }
        if (popupWindow != null) {
            popupWindow.dismiss();
        }
    }

    public TextSelectionOverlay getOverlayView(Context context) {
        if (textSelectionOverlay == null) {
            textSelectionOverlay = new TextSelectionOverlay(context);
        }
        return textSelectionOverlay;
    }

    public boolean isSelected(MessageObject messageObject) {
        if (messageObject == null) {
            return false;
        }
        return selectedCellId == messageObject.getId();
    }

    public void checkSelectionCancel(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
            cancelTextSelectionRunnable();
        }
    }

    public void cancelTextSelectionRunnable() {
        AndroidUtilities.cancelRunOnUIThread(startSelectionRunnable);
        tryCapture = false;
    }

    public void clear() {
        clear(false);
    }

    public void clear(boolean instant) {
        onExitSelectionMode(instant);
        selectionStart = -1;
        selectionEnd = -1;
        hideMagnifier();
        hideActions();
        invalidate();
        selectedView = null;
        selectedCellId = 0;
        AndroidUtilities.cancelRunOnUIThread(startSelectionRunnable);
        tryCapture = false;
        if (textSelectionOverlay != null) {
            textSelectionOverlay.setVisibility(View.GONE);
        }
        handleViewProgress = 0;
        if (callback != null) {
            callback.onStateChanged(false);
        }
        capturedX = -1;
        capturedY = -1;
        maybeTextX = -1;
        maybeTextY = -1;
        movingOffsetX = 0;
        movingOffsetY = 0;
        movingHandle = false;
    }

    protected void onExitSelectionMode(boolean didAction) {
    }

    public void setCallback(Callback listener) {
        callback = listener;
    }

    public boolean isTryingSelect() {
        return tryCapture;
    }

    public void onParentScrolled() {
        if (isSelectionMode() && textSelectionOverlay != null) {
            parentIsScrolling = true;
            textSelectionOverlay.invalidate();
            hideActions();
        }
    }

    public void stopScrolling() {
        parentIsScrolling = false;
        showActions();
    }

    public static boolean isInterruptedCharacter(char c) {
        return Character.isLetter(c) || Character.isDigit(c) || c == '_';
    }

    public void setTopOffset(int topOffset) {
        this.topOffset = topOffset;
    }

    public class TextSelectionOverlay extends View {

        Paint handleViewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        float pressedX;
        float pressedY;
        long pressedTime = 0;

        Path path = new Path();

        public TextSelectionOverlay(Context context) {
            super(context);
            handleViewPaint.setStyle(Paint.Style.FILL);
        }


        public boolean checkOnTap(MotionEvent event) {
            if (!isSelectionMode() || movingHandle) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    pressedX = event.getX();
                    pressedY = event.getY();
                    pressedTime = System.currentTimeMillis();
                    break;
                case MotionEvent.ACTION_UP:
                    if (System.currentTimeMillis() - pressedTime < 200 && distance((int) pressedX, (int) pressedY, (int) event.getX(), (int) event.getY()) < touchSlop) {
                        hideActions();
                        clear();
                        return true;
                    }
                    break;
            }

            return false;

        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!isSelectionMode()) return false;
            if (event.getPointerCount() > 1) {
                return movingHandle;
            }

            int dx = (int) (lastX - event.getX());
            int dy = (int) (lastY - event.getY());

            lastX = (int) event.getX();
            lastY = (int) event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (movingHandle) {
                        return true;
                    }
                    int x = (int) event.getX();
                    int y = (int) event.getY();
                    if (startArea.contains(x, y)) {
                        pickStartView();
                        if (selectedView == null) {
                            return false;
                        }
                        movingHandle = true;
                        movingHandleStart = true;
                        int[] cords = offsetToCord(selectionStart);


                        float textSizeHalf = getLineHeight() / 2;
                        movingOffsetX = cords[0] + textX + selectedView.getX() - x;
                        movingOffsetY = cords[1] + textY + selectedView.getTop() - y - textSizeHalf;
                        hideActions();
                        return true;
                    }

                    if (endArea.contains(x, y)) {
                        pickEndView();
                        if (selectedView == null) {
                            return false;
                        }
                        movingHandle = true;
                        movingHandleStart = false;
                        int[] cords = offsetToCord(selectionEnd);

                        float textSizeHalf = getLineHeight() / 2;
                        movingOffsetX = cords[0] + textX + selectedView.getX() - x;
                        movingOffsetY = cords[1] + textY + selectedView.getTop() - y - textSizeHalf;
                        showMagnifier(lastX);
                        hideActions();
                        return true;
                    }

                    movingHandle = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (movingHandle) {
                        if (movingHandleStart) {
                            pickStartView();
                        } else {
                            pickEndView();
                        }

                        if (selectedView == null) {
                            return movingHandle;
                        }

                        y = (int) (event.getY() + movingOffsetY);
                        x = (int) (event.getX() + movingOffsetX);

                        boolean viewChanged = selectLayout(x, y);

                        if (selectedView == null) {
                            return true;
                        }

                        if (movingHandleStart) {
                            fillLayoutForOffset(selectionStart, layoutBlock);
                        } else {
                            fillLayoutForOffset(selectionEnd, layoutBlock);
                        }

                        StaticLayout oldTextLayout = layoutBlock.layout;
                        if (oldTextLayout == null) {
                            return true;
                        }
                        float oldYoffset = layoutBlock.yOffset;
                        Cell oldSelectedView = selectedView;

                        y -= selectedView.getTop();
                        x -= selectedView.getX();

                        boolean canScrollDown = event.getY() - touchSlop > parentView.getMeasuredHeight() && (multiselect || selectedView.getBottom() > parentView.getMeasuredHeight());
                        boolean canScrollUp = event.getY() < ((View) parentView.getParent()).getTop() + getParentTopPadding() && (multiselect || selectedView.getTop() < getParentTopPadding());
                        if (canScrollDown || canScrollUp) {
                            if (!scrolling) {
                                scrolling = true;
                                AndroidUtilities.runOnUIThread(scrollRunnable);
                            }
                            scrollDown = canScrollDown;

                            if (canScrollDown) {
                                y = (int) (parentView.getMeasuredHeight() - selectedView.getTop() + movingOffsetY);
                            } else {
                                y = (int) (-selectedView.getTop() + movingOffsetY);
                            }
                        } else {
                            if (scrolling) {
                                scrolling = false;
                                AndroidUtilities.cancelRunOnUIThread(scrollRunnable);
                            }
                        }

                        int newSelection = getCharOffsetFromCord(x, y, textX, textY, selectedView, false);
                        if (newSelection >= 0) {
                            if (movingDirectionSettling) {
                                if (viewChanged) {
                                    return true;
                                } else if (newSelection < selectionStart) {
                                    movingDirectionSettling = false;
                                    movingHandleStart = true;
                                    hideActions();
                                } else if (newSelection > selectionEnd) {
                                    movingDirectionSettling = false;
                                    movingHandleStart = false;
                                    hideActions();
                                } else {
                                    return true;
                                }
                            }
                            if (movingHandleStart) {
                                if (selectionStart != newSelection && canSelect(newSelection)) {
                                    CharSequence text = getText(selectedView, false);

                                    fillLayoutForOffset(newSelection, layoutBlock);
                                    StaticLayout layoutOld = layoutBlock.layout;

                                    fillLayoutForOffset(selectionStart, layoutBlock);
                                    StaticLayout layoutNew = layoutBlock.layout;

                                    if (layoutOld == null || layoutNew == null) {
                                        return true;
                                    }

                                    int nextWhitespace = newSelection;
                                    while (nextWhitespace - 1 >= 0 && isInterruptedCharacter(text.charAt(nextWhitespace - 1))) {
                                        nextWhitespace--;
                                    }

                                    int nextWhitespaceLine = layoutNew.getLineForOffset(nextWhitespace);
                                    int currentLine = layoutNew.getLineForOffset(selectionStart);
                                    int newSelectionLine = layoutNew.getLineForOffset(newSelection);

                                    if (viewChanged || layoutOld != layoutNew || newSelectionLine != layoutNew.getLineForOffset(selectionStart) && newSelectionLine == nextWhitespaceLine) {
                                        jumpToLine(newSelection, nextWhitespace, viewChanged, layoutBlock.yOffset, oldYoffset, oldSelectedView);
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                            textSelectionOverlay.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
                                        }
                                        TextSelectionHelper.this.invalidate();
                                    } else if (Layout.DIR_RIGHT_TO_LEFT == layoutNew.getParagraphDirection(layoutNew.getLineForOffset(newSelection)) || layoutNew.isRtlCharAt(newSelection) || nextWhitespaceLine != currentLine || newSelectionLine != nextWhitespaceLine) {
                                        selectionStart = newSelection;
                                        if (selectionStart > selectionEnd) {
                                            int k = selectionEnd;
                                            selectionEnd = selectionStart;
                                            selectionStart = k;
                                            movingHandleStart = false;
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                            textSelectionOverlay.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
                                        }
                                        TextSelectionHelper.this.invalidate();
                                    } else {
                                        int previousWhitespace = newSelection;
                                        while (previousWhitespace + 1 < text.length() && isInterruptedCharacter(text.charAt(previousWhitespace + 1))) {
                                            previousWhitespace++;
                                        }

                                        int distanseToNextWhitspace = Math.abs(newSelection - nextWhitespace);
                                        int distanseToPreviousWhitespace = Math.abs(newSelection - previousWhitespace);

                                        if (snap) {
                                            snap = dx >= 0;
                                        }
                                        boolean nextCharIsLitter = newSelection - 1 > 0 && isInterruptedCharacter(text.charAt(newSelection - 1));

                                        char newChar;
                                        if (newSelection >= text.length()) {
                                            newSelection = text.length();
                                            newChar = '\n';
                                        } else {
                                            newChar = text.charAt(newSelection);
                                        }

                                        char selectionStartChar;
                                        if (selectionStart >= text.length()) {
                                            selectionStart = text.length();
                                            selectionStartChar = '\n';
                                        } else {
                                            selectionStartChar = text.charAt(selectionStart);
                                        }

                                        if ((newSelection < selectionStart && distanseToNextWhitspace < distanseToPreviousWhitespace) || (newSelection > selectionStart && dx < 0) || !isInterruptedCharacter(newChar) || (isInterruptedCharacter(selectionStartChar) && !snap) || newSelection == 0 || !nextCharIsLitter || selectionStartChar == '\n') {
                                            if (snap && newSelection == 1) {
                                                return true;
                                            }
                                            if (newSelection < selectionStart && isInterruptedCharacter(newChar) && !(isInterruptedCharacter(selectionStartChar) && !snap) && selectionStartChar != '\n') {
                                                selectionStart = nextWhitespace;
                                                snap = true;
                                            } else {
                                                selectionStart = newSelection;
                                            }

                                            if (selectionStart > selectionEnd) {
                                                int k = selectionEnd;
                                                selectionEnd = selectionStart;
                                                selectionStart = k;
                                                movingHandleStart = false;
                                            }
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                                textSelectionOverlay.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
                                            }
                                            TextSelectionHelper.this.invalidate();
                                        }
                                    }
                                }
                            } else {
                                if (newSelection != selectionEnd && canSelect(newSelection)) {

                                    CharSequence text = getText(selectedView, false);

                                    int nextWhitespace = newSelection;
                                    while (nextWhitespace < text.length() && isInterruptedCharacter(text.charAt(nextWhitespace))) {
                                        nextWhitespace++;
                                    }


                                    fillLayoutForOffset(newSelection, layoutBlock);
                                    StaticLayout layoutOld = layoutBlock.layout;

                                    fillLayoutForOffset(selectionEnd, layoutBlock);
                                    StaticLayout layoutNew = layoutBlock.layout;

                                    if (layoutOld == null || layoutNew == null) {
                                        return true;
                                    }

                                    if (newSelection > text.length()) {
                                        newSelection = text.length();
                                    }

                                    int nextWhitespaceLine = layoutNew.getLineForOffset(nextWhitespace);
                                    int currentLine = layoutNew.getLineForOffset(selectionEnd);
                                    int newSelectionLine = layoutNew.getLineForOffset(newSelection);

                                    if (viewChanged || layoutOld != layoutNew || newSelectionLine != layoutNew.getLineForOffset(selectionEnd) && newSelectionLine == nextWhitespaceLine) {
                                        jumpToLine(newSelection, nextWhitespace, viewChanged, layoutBlock.yOffset, oldYoffset, oldSelectedView);
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                            textSelectionOverlay.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
                                        }
                                        TextSelectionHelper.this.invalidate();
                                    } else if (Layout.DIR_RIGHT_TO_LEFT == layoutNew.getParagraphDirection(layoutNew.getLineForOffset(newSelection)) || layoutNew.isRtlCharAt(newSelection) || currentLine != nextWhitespaceLine || newSelectionLine != nextWhitespaceLine) {
                                        selectionEnd = newSelection;
                                        if (selectionStart > selectionEnd) {
                                            int k = selectionEnd;
                                            selectionEnd = selectionStart;
                                            selectionStart = k;
                                            movingHandleStart = true;
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                            textSelectionOverlay.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
                                        }
                                        TextSelectionHelper.this.invalidate();
                                    } else {
                                        int previousWhitespace = newSelection;
                                        while (previousWhitespace - 1 >= 0 && isInterruptedCharacter(text.charAt(previousWhitespace - 1))) {
                                            previousWhitespace--;
                                        }

                                        int distanceToNextWhitespace = Math.abs(newSelection - nextWhitespace);
                                        int distanceToPreviousWhitespace = Math.abs(newSelection - previousWhitespace);

                                        boolean newIsLetter = newSelection - 1 > 0 && isInterruptedCharacter(text.charAt(newSelection - 1));

                                        if (snap) {
                                            snap = dx <= 0;
                                        }
                                        boolean previousIsLetter = selectionEnd > 0 && isInterruptedCharacter(text.charAt(selectionEnd - 1));
                                        if ((newSelection > selectionEnd && distanceToNextWhitespace <= distanceToPreviousWhitespace) || (newSelection < selectionEnd && dx > 0) || !newIsLetter || (previousIsLetter && !snap)) {
                                            if (newSelection > selectionEnd && newIsLetter && !(previousIsLetter && !snap)) {
                                                selectionEnd = nextWhitespace;
                                                snap = true;
                                            } else {
                                                selectionEnd = newSelection;
                                            }
                                            if (selectionStart > selectionEnd) {
                                                int k = selectionEnd;
                                                selectionEnd = selectionStart;
                                                selectionStart = k;
                                                movingHandleStart = true;
                                            }
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                                textSelectionOverlay.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
                                            }
                                            TextSelectionHelper.this.invalidate();
                                        }
                                    }
                                }
                            }

                            onOffsetChanged();
                        }
                        showMagnifier(lastX);
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    hideMagnifier();
                    movingHandle = false;
                    movingDirectionSettling = false;
                    isOneTouch = false;
                    if (isSelectionMode()) {
                        showActions();
                        showHandleViews();
                    }
                    if (scrolling) {
                        scrolling = false;
                        AndroidUtilities.cancelRunOnUIThread(scrollRunnable);
                    }

                    break;
            }
            return movingHandle;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!isSelectionMode()) return;
            int handleViewSize = AndroidUtilities.dp(22);

            int count = 0;
            int top = topOffset;
            pickEndView();
            if (selectedView != null) {
                canvas.save();
                float yOffset = selectedView.getY() + textY;
                float xOffset = selectedView.getX() + textX;
                canvas.translate(xOffset, yOffset);


                handleViewPaint.setColor(getThemedColor(Theme.key_chat_TextSelectionCursor));

                int len = getText(selectedView, false).length();

                if (selectionEnd >= 0 && selectionEnd <= len) {
                    fillLayoutForOffset(selectionEnd, layoutBlock);
                    StaticLayout layout = layoutBlock.layout;
                    if (layout != null) {
                        int end = selectionEnd;
                        int textLen = layout.getText().length();
                        if (end > textLen) {
                            end = textLen;
                        }

                        int line = layout.getLineForOffset(end);
                        float x = layout.getPrimaryHorizontal(end);
                        int y = layout.getLineBottom(line);
                        y += layoutBlock.yOffset;
                        x += layoutBlock.xOffset;

                        if (y + yOffset > top + keyboardSize && y + yOffset < parentView.getMeasuredHeight()) {
                            if (!layout.isRtlCharAt(selectionEnd)) {
                                canvas.save();
                                canvas.translate(x, y);
                                float v = interpolator.getInterpolation(handleViewProgress);
                                canvas.scale(v, v, handleViewSize / 2f, handleViewSize / 2f);
                                path.reset();
                                path.addCircle(handleViewSize / 2f, handleViewSize / 2f, handleViewSize / 2f, Path.Direction.CCW);
                                path.addRect(0, 0, handleViewSize / 2f, handleViewSize / 2f, Path.Direction.CCW);
                                canvas.drawPath(path, handleViewPaint);
                                canvas.restore();
                                endArea.set(
                                        xOffset + x, yOffset + y - handleViewSize,
                                        xOffset + x + handleViewSize, yOffset + y + handleViewSize
                                );
                                endArea.inset(-AndroidUtilities.dp(8), -AndroidUtilities.dp(8));
                                count++;
                            } else {
                                canvas.save();
                                canvas.translate(x - handleViewSize, y);
                                float v = interpolator.getInterpolation(handleViewProgress);
                                canvas.scale(v, v, handleViewSize / 2f, handleViewSize / 2f);
                                path.reset();
                                path.addCircle(handleViewSize / 2f, handleViewSize / 2f, handleViewSize / 2f, Path.Direction.CCW);
                                path.addRect(handleViewSize / 2f, 0, handleViewSize, handleViewSize / 2f, Path.Direction.CCW);
                                canvas.drawPath(path, handleViewPaint);
                                canvas.restore();
                                endArea.set(
                                        xOffset + x - handleViewSize, yOffset + y - handleViewSize,
                                        xOffset + x, yOffset + y + handleViewSize
                                );
                                endArea.inset(-AndroidUtilities.dp(8), -AndroidUtilities.dp(8));
                            }
                        } else {
                            endArea.setEmpty();
                        }
                    }
                }
                canvas.restore();
            }
            pickStartView();
            if (selectedView != null) {
                canvas.save();
                float yOffset = selectedView.getY() + textY;
                float xOffset = selectedView.getX() + textX;
                canvas.translate(xOffset, yOffset);

                int len = getText(selectedView, false).length();

                if (selectionStart >= 0 && selectionStart <= len) {
                    fillLayoutForOffset(selectionStart, layoutBlock);
                    StaticLayout layout = layoutBlock.layout;
                    if (layout != null) {
                        int line = layout.getLineForOffset(selectionStart);
                        float x = layout.getPrimaryHorizontal(selectionStart);

                        int y = layout.getLineBottom(line);
                        y += layoutBlock.yOffset;
                        x += layoutBlock.xOffset;

                        if (y + yOffset > top + keyboardSize && y + yOffset < parentView.getMeasuredHeight()) {
                            if (!layout.isRtlCharAt(selectionStart)) {
                                canvas.save();
                                canvas.translate(x - handleViewSize, y);
                                float v = interpolator.getInterpolation(handleViewProgress);
                                canvas.scale(v, v, handleViewSize / 2f, handleViewSize / 2f);
                                path.reset();
                                path.addCircle(handleViewSize / 2f, handleViewSize / 2f, handleViewSize / 2f, Path.Direction.CCW);
                                path.addRect(handleViewSize / 2f, 0, handleViewSize, handleViewSize / 2f, Path.Direction.CCW);
                                canvas.drawPath(path, handleViewPaint);
                                canvas.restore();
                                startArea.set(
                                        xOffset + x - handleViewSize, yOffset + y - handleViewSize,
                                        xOffset + x, yOffset + y + handleViewSize
                                );
                                startArea.inset(-AndroidUtilities.dp(8), -AndroidUtilities.dp(8));
                                count++;
                            } else {
                                canvas.save();
                                canvas.translate(x, y);
                                float v = interpolator.getInterpolation(handleViewProgress);
                                canvas.scale(v, v, handleViewSize / 2f, handleViewSize / 2f);
                                path.reset();
                                path.addCircle(handleViewSize / 2f, handleViewSize / 2f, handleViewSize / 2f, Path.Direction.CCW);
                                path.addRect(0, 0, handleViewSize / 2f, handleViewSize / 2f, Path.Direction.CCW);
                                canvas.drawPath(path, handleViewPaint);
                                canvas.restore();
                                startArea.set(
                                        xOffset + x, yOffset + y - handleViewSize,
                                        xOffset + x + handleViewSize, yOffset + y + handleViewSize
                                );
                                startArea.inset(-AndroidUtilities.dp(8), -AndroidUtilities.dp(8));
                            }
                        } else {
                            if (y + yOffset > 0 && y + yOffset - getLineHeight() < parentView.getMeasuredHeight()) {
                                count++;
                            }
                            startArea.setEmpty();
                        }
                    }
                }
                canvas.restore();
            }

            if (count != 0) {
                if (movingHandle) {
                    if (!movingHandleStart) {
                        pickEndView();
                    }
                    showMagnifier(lastX);
                    if (magnifierY != magnifierYanimated) {
                        invalidate();
                    }
                }
            }

            if (!parentIsScrolling) {
                showActions();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && actionMode != null) {
                actionMode.invalidateContentRect();
                if (actionMode != null) {
                    ((FloatingActionMode) actionMode).updateViewLocationInWindow();
                }
            }

            if (isOneTouch) {
                invalidate();
            }
        }
    }

    protected void jumpToLine(int newSelection, int nextWhitespace, boolean viewChanged, float newYoffset, float oldYoffset, Cell oldSelectedView) {
        if (movingHandleStart) {
            selectionStart = nextWhitespace;
            if (!viewChanged && selectionStart > selectionEnd) {
                int k = selectionEnd;
                selectionEnd = selectionStart;
                selectionStart = k;
                movingHandleStart = false;
            }
            snap = true;
        } else {
            selectionEnd = nextWhitespace;
            if (!viewChanged && selectionStart > selectionEnd) {
                int k = selectionEnd;
                selectionEnd = selectionStart;
                selectionStart = k;
                movingHandleStart = true;
            }
            snap = true;
        }
    }

    protected boolean canSelect(int newSelection) {
        return newSelection != selectionStart && newSelection != selectionEnd;
    }

    protected boolean selectLayout(int x, int y) {
        return false;
    }

    protected void onOffsetChanged() {

    }

    protected void pickEndView() {

    }

    protected void pickStartView() {

    }

    protected boolean isSelectable(View child) {
        return true;
    }

    public void invalidate() {
        if (selectedView != null) {
            selectedView.invalidate();
        }
        if (textSelectionOverlay != null) {
            textSelectionOverlay.invalidate();
        }
    }

    private static final int TRANSLATE = 3;
    private ActionMode.Callback createActionCallback() {
        final ActionMode.Callback callback = new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(Menu.NONE, android.R.id.copy, 0, android.R.string.copy);
                menu.add(Menu.NONE, android.R.id.selectAll, 1, android.R.string.selectAll);
                menu.add(Menu.NONE, TRANSLATE, 2, LocaleController.getString("TranslateMessage", R.string.TranslateMessage));
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                if (selectedView != null) {
                    CharSequence charSequence = getText(selectedView, false);
                    if (multiselect || selectionStart <= 0 && selectionEnd >= charSequence.length() - 1) {
                        menu.getItem(1).setVisible(false);
                    } else {
                        menu.getItem(1).setVisible(true);
                    }
                }
                if (LanguageDetector.hasSupport() && getSelectedText() != null) {
                    LanguageDetector.detectLanguage(getSelectedText().toString(), lng -> {
                        translateFromLanguage = lng;
                        updateTranslateButton(menu);
                    }, err -> {
                        FileLog.e("mlkit: failed to detect language in selection");
                        FileLog.e(err);
                        translateFromLanguage = null;
                        updateTranslateButton(menu);
                    });
                } else {
                    translateFromLanguage = null;
                    updateTranslateButton(menu);
                }
                return true;
            }

            private String translateFromLanguage = null;
            private void updateTranslateButton(Menu menu) {
                String translateToLanguage = LocaleController.getInstance().getCurrentLocale().getLanguage();
                menu.getItem(2).setVisible(
                    onTranslateListener != null && (
                        (
                            translateFromLanguage != null &&
                            (!translateFromLanguage.equals(translateToLanguage) || translateFromLanguage.equals("und")) &&
                            !RestrictedLanguagesSelectActivity.getRestrictedLanguages().contains(translateFromLanguage)
                        ) || !LanguageDetector.hasSupport()
                    )
                );
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (!isSelectionMode()) {
                    return true;
                }
                switch (item.getItemId()) {
                    case android.R.id.copy:
                        copyText();
                        return true;
                    case android.R.id.selectAll:
                        CharSequence text = getText(selectedView, false);
                        if (text == null) {
                            return true;
                        }
                        selectionStart = 0;
                        selectionEnd = text.length();
                        hideActions();
                        invalidate();
                        showActions();
                        return true;
                    case TRANSLATE:
                        if (onTranslateListener != null) {
                            String translateToLanguage = LocaleController.getInstance().getCurrentLocale().getLanguage();
                            onTranslateListener.run(getSelectedText(), translateFromLanguage, translateToLanguage, () -> showActions());
                        }
                        hideActions();
                        return true;
                    default:
                        clear();
                }
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
                    clear();
                }
            }
        };

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            ActionMode.Callback2 callback2 = new ActionMode.Callback2() {
                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    return callback.onCreateActionMode(mode, menu);
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return callback.onPrepareActionMode(mode, menu);
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return callback.onActionItemClicked(mode, item);
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    callback.onDestroyActionMode(mode);
                }

                @Override
                public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
                    if (!isSelectionMode()) {
                        return;
                    }
                    pickStartView();
                    int x1 = 0;
                    int y1 = 1;
                    if (selectedView != null) {
                        int lineHeight = -getLineHeight();
                        int[] coords = offsetToCord(selectionStart);
                        x1 = coords[0] + textX;
                        y1 = (int) (coords[1] + textY + selectedView.getY()) + lineHeight / 2 - AndroidUtilities.dp(4);
                        if (y1 < 1) y1 = 1;
                    }

                    int x2 = parentView.getWidth();
                    pickEndView();
                    if (selectedView != null) {
                        int[] coords = offsetToCord(selectionEnd);
                        x2 = coords[0] + textX;

                    }
                    outRect.set(
                            Math.min(x1, x2), y1,
                            Math.max(x1, x2), y1 + 1
                    );
                }
            };
            return callback2;
        }
        return callback;
    }

    private void copyText() {
        if (!isSelectionMode()) {
            return;
        }
        CharSequence str = getSelectedText();
        if (str == null) {
            return;
        }
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("label", str);
        clipboard.setPrimaryClip(clip);
        hideActions();
        clear(true);
        if (TextSelectionHelper.this.callback != null) {
            TextSelectionHelper.this.callback.onTextCopied();
        }
    }

    private void translateText() {
        if (!isSelectionMode()) {
            return;
        }
        CharSequence str = getSelectedText();
        if (str == null) {
            return;
        }
    }

    protected CharSequence getSelectedText() {
        CharSequence text = getText(selectedView, false);
        if (text != null) {
            return text.subSequence(selectionStart, selectionEnd);
        }
        return null;
    }

    protected int[] offsetToCord(int offset) {
        fillLayoutForOffset(offset, layoutBlock);

        StaticLayout layout = layoutBlock.layout;
        if (layout == null || offset > layout.getText().length()) {
            return tmpCoord;
        }
        int line = layout.getLineForOffset(offset);
        tmpCoord[0] = (int) (layout.getPrimaryHorizontal(offset) + layoutBlock.xOffset);
        tmpCoord[1] = layout.getLineBottom(line);
        tmpCoord[1] += layoutBlock.yOffset;
        return tmpCoord;
    }

    protected void drawSelection(Canvas canvas, StaticLayout layout, int selectionStart, int selectionEnd) {
        int startLine = layout.getLineForOffset(selectionStart);
        int endLine = layout.getLineForOffset(selectionEnd);
        if (startLine == endLine) {
            drawLine(canvas, layout, startLine, selectionStart, selectionEnd);
        } else {
            int end = layout.getLineEnd(startLine);
            if (layout.getParagraphDirection(startLine) != StaticLayout.DIR_RIGHT_TO_LEFT && end > 0) {
                end--;
                CharSequence text = layout.getText();
                int s = (int) layout.getPrimaryHorizontal(end);
                int e;
                if (layout.isRtlCharAt(end)) {
                    int endIndex = end;
                    while (layout.isRtlCharAt(endIndex)) {
                        if (endIndex == 0) break;
                        endIndex--;
                    }
                    e = layout.getLineForOffset(endIndex) == layout.getLineForOffset(end) ? (int) layout.getPrimaryHorizontal(endIndex + 1) : (int) layout.getLineLeft(startLine);
                } else {
                    e = (int) layout.getLineRight(startLine);
                }
                int l = Math.min(s, e);
                int r = Math.max(s, e);
                if (end > 0 && end < text.length() && !Character.isWhitespace(text.charAt(end - 1))) {
                    canvas.drawRect(l, layout.getLineTop(startLine), r, layout.getLineBottom(startLine), selectionPaint);
                }
            }
            drawLine(canvas, layout, startLine, selectionStart, end);
            drawLine(canvas, layout, endLine, layout.getLineStart(endLine), selectionEnd);
            for (int i = startLine + 1; i < endLine; i++) {
                int s = (int) layout.getLineLeft(i);
                int e = (int) layout.getLineRight(i);
                int l = Math.min(s, e);
                int r = Math.max(s, e);

                canvas.drawRect(l, layout.getLineTop(i), r, layout.getLineBottom(i), selectionPaint);
            }
        }
    }

    private void drawLine(Canvas canvas, StaticLayout layout, int line, int start, int end) {
        layout.getSelectionPath(start, end, path);
        if (path.lastBottom < layout.getLineBottom(line)) {
            int lineBottom = layout.getLineBottom(line);
            int lineTop = layout.getLineTop(line);
            float lineH = lineBottom - lineTop;
            float lineHWithoutSpaсing = path.lastBottom - lineTop;
            canvas.save();
            canvas.scale(1f, lineH / lineHWithoutSpaсing, 0, lineTop);
            canvas.drawPath(path, selectionPaint);
            canvas.restore();
        } else {
            canvas.drawPath(path, selectionPaint);
        }
    }

    private static class LayoutBlock {
        StaticLayout layout;
        float yOffset;
        float xOffset;
    }


    public static class Callback {
        public void onStateChanged(boolean isSelected){};
        public void onTextCopied(){};
    }

    protected void fillLayoutForOffset(int offset, LayoutBlock layoutBlock) {
        fillLayoutForOffset(offset, layoutBlock, false);
    }

    protected abstract CharSequence getText(Cell view, boolean maybe);

    protected abstract int getCharOffsetFromCord(int x, int y, int offsetX, int offsetY, Cell view, boolean maybe);

    protected abstract void fillLayoutForOffset(int offset, LayoutBlock layoutBlock, boolean maybe);

    protected abstract int getLineHeight();

    protected abstract void onTextSelected(Cell newView, Cell oldView);

    public static class ChatListTextSelectionHelper extends TextSelectionHelper<ChatMessageCell> {

        SparseArray<Animator> animatorSparseArray = new SparseArray<>();
        private boolean isDescription;
        private boolean maybeIsDescription;

        public static int TYPE_MESSAGE = 0;
        public static int TYPE_CAPTION = 1;
        public static int TYPE_DESCRIPTION = 2;

        @Override
        protected int getLineHeight() {
            if (selectedView != null && selectedView.getMessageObject() != null) {
                MessageObject object = selectedView.getMessageObject();
                StaticLayout layout = null;
                if (isDescription) {
                    layout = selectedView.getDescriptionlayout();
                } else if (selectedView.hasCaptionLayout()) {
                    layout = selectedView.getCaptionLayout();
                } else if (object.textLayoutBlocks != null) {
                    layout = object.textLayoutBlocks.get(0).textLayout;
                }
                if (layout == null) {
                    return 0;
                }
                int lineHeight = layout.getLineBottom(0) - layout.getLineTop(0);
                return lineHeight;
            }
            return 0;
        }


        public void setMessageObject(ChatMessageCell chatMessageCell) {
            this.maybeSelectedView = chatMessageCell;
            MessageObject messageObject = chatMessageCell.getMessageObject();

            if (maybeIsDescription && chatMessageCell.getDescriptionlayout() != null) {
                textArea.set(
                        maybeTextX, maybeTextY,
                        maybeTextX + chatMessageCell.getDescriptionlayout().getWidth(),
                        maybeTextY + chatMessageCell.getDescriptionlayout().getHeight()
                );
            } else if (chatMessageCell.hasCaptionLayout()) {
                textArea.set(maybeTextX, maybeTextY,
                        maybeTextX + chatMessageCell.getCaptionLayout().getWidth(),
                        maybeTextY + chatMessageCell.getCaptionLayout().getHeight());
            } else if (messageObject != null && messageObject.textLayoutBlocks != null && messageObject.textLayoutBlocks.size() > 0) {
                MessageObject.TextLayoutBlock block = messageObject.textLayoutBlocks.get(messageObject.textLayoutBlocks.size() - 1);
                textArea.set(
                        maybeTextX, maybeTextY,
                        maybeTextX + block.textLayout.getWidth(),
                        (int) (maybeTextY + block.textYOffset + block.textLayout.getHeight())
                );
            }
        }

        @Override
        protected CharSequence getText(ChatMessageCell cell, boolean maybe) {
            if (cell == null || cell.getMessageObject() == null) {
                return null;
            }
            if (maybe ? maybeIsDescription : isDescription) {
                return cell.getDescriptionlayout().getText();
            }
            if (cell.hasCaptionLayout()) {
                return cell.getCaptionLayout().getText();
            }
            return cell.getMessageObject().messageText;
        }

        @Override
        protected void onTextSelected(ChatMessageCell newView, ChatMessageCell oldView) {
            boolean idChanged = oldView == null || (oldView.getMessageObject() != null && oldView.getMessageObject().getId() != newView.getMessageObject().getId());
            selectedCellId = newView.getMessageObject().getId();
            enterProgress = 0;
            isDescription = maybeIsDescription;

            Animator oldAnimator = animatorSparseArray.get(selectedCellId);
            if (oldAnimator != null) {
                oldAnimator.removeAllListeners();
                oldAnimator.cancel();
            }

            ValueAnimator animator = ValueAnimator.ofFloat(0, 1f);
            animator.addUpdateListener(animation -> {
                enterProgress = (float) animation.getAnimatedValue();
                if (textSelectionOverlay != null) {
                    textSelectionOverlay.invalidate();
                }
                if (selectedView != null && selectedView.getCurrentMessagesGroup() == null && idChanged) {
                    selectedView.setSelectedBackgroundProgress(1f - enterProgress);
                }
            });
            animator.setDuration(250);
            animator.start();

            animatorSparseArray.put(selectedCellId, animator);

            if (!idChanged) {
                newView.setSelectedBackgroundProgress(0f);
            }

            SharedConfig.removeTextSelectionHint();
        }


        public void draw(MessageObject messageObject, MessageObject.TextLayoutBlock block, Canvas canvas) {
            if (selectedView == null || selectedView.getMessageObject() == null || isDescription) {
                return;
            }

            MessageObject selectedMessageObject = selectedView.getMessageObject();
            if (selectedMessageObject == null || selectedMessageObject.textLayoutBlocks == null) {
                return;
            }

            if (messageObject.getId() == selectedCellId) {
                int selectionStart = this.selectionStart;
                int selectionEnd = this.selectionEnd;
                if (selectedMessageObject.textLayoutBlocks.size() > 1) {
                    if (selectionStart < block.charactersOffset) {
                        selectionStart = block.charactersOffset;
                    }
                    if (selectionStart > block.charactersEnd) {
                        selectionStart = block.charactersEnd;
                    }
                    if (selectionEnd < block.charactersOffset) {
                        selectionEnd = block.charactersOffset;
                    }
                    if (selectionEnd > block.charactersEnd) {
                        selectionEnd = block.charactersEnd;
                    }
                }

                if (selectionStart != selectionEnd) {
                    if (selectedMessageObject.isOutOwner()) {
                        selectionPaint.setColor(getThemedColor(Theme.key_chat_outTextSelectionHighlight));
                    } else {
                        selectionPaint.setColor(getThemedColor(key_chat_inTextSelectionHighlight));
                    }
                    drawSelection(canvas, block.textLayout, selectionStart, selectionEnd);
                }
            }
        }

        protected int getCharOffsetFromCord(int x, int y, int offsetX, int offsetY, ChatMessageCell cell, boolean maybe) {
            if (cell == null) {
                return 0;
            }

            int line = -1;
            x -= offsetX;
            y -= offsetY;

            StaticLayout lastLayout;
            float yOffset = 0;

            boolean isDescription = maybe ? maybeIsDescription : this.isDescription;
            if (isDescription) {
                lastLayout = cell.getDescriptionlayout();
            } else if (cell.hasCaptionLayout()) {
                lastLayout = cell.getCaptionLayout();
            } else {
                MessageObject.TextLayoutBlock lastBlock = cell.getMessageObject().textLayoutBlocks.get(cell.getMessageObject().textLayoutBlocks.size() - 1);
                lastLayout = lastBlock.textLayout;
                yOffset = lastBlock.textYOffset;
            }

            if (y < 0) {
                y = 1;
            }
            if (y > yOffset + lastLayout.getLineBottom(lastLayout.getLineCount() - 1)) {
                y = (int) (yOffset + lastLayout.getLineBottom(lastLayout.getLineCount() - 1) - 1);
            }

            fillLayoutForCoords(x, y, cell, layoutBlock, maybe);

            if (layoutBlock.layout == null) {
                return -1;
            }

            StaticLayout layout = layoutBlock.layout;
            x -= layoutBlock.xOffset;


            for (int i = 0; i < layout.getLineCount(); i++) {
                if (y > layoutBlock.yOffset + layout.getLineTop(i) && y < layoutBlock.yOffset + layout.getLineBottom(i)) {
                    line = i;
                    break;
                }
            }
            if (line >= 0) {
                return layout.getOffsetForHorizontal(line, x);
            }

            return -1;
        }

        private void fillLayoutForCoords(int x, int y, ChatMessageCell cell, LayoutBlock layoutBlock, boolean maybe) {
            if (cell == null) {
                return;
            }

            MessageObject messageObject = cell.getMessageObject();

            if (maybe ? maybeIsDescription : isDescription) {
                layoutBlock.layout = cell.getDescriptionlayout();
                layoutBlock.yOffset = layoutBlock.xOffset = 0;
                return;
            }
            if (cell.hasCaptionLayout()) {
                layoutBlock.layout = cell.getCaptionLayout();
                layoutBlock.yOffset = layoutBlock.xOffset = 0;
                return;
            }

            for (int i = 0; i < messageObject.textLayoutBlocks.size(); i++) {
                MessageObject.TextLayoutBlock block = messageObject.textLayoutBlocks.get(i);
                if (y >= block.textYOffset && y <= block.textYOffset + block.height) {
                    layoutBlock.layout = block.textLayout;
                    layoutBlock.yOffset = block.textYOffset;
                    layoutBlock.xOffset = -(block.isRtl() ? (int) Math.ceil(messageObject.textXOffset) : 0);
                    return;
                }
            }
        }

        @Override
        protected void fillLayoutForOffset(int offset, LayoutBlock layoutBlock, boolean maybe) {
            ChatMessageCell selectedView = maybe ? maybeSelectedView : this.selectedView;
            if (selectedView == null) {
                layoutBlock.layout = null;
                return;
            }
            MessageObject messageObject = selectedView.getMessageObject();

            if (isDescription) {
                layoutBlock.layout = selectedView.getDescriptionlayout();
                layoutBlock.xOffset = layoutBlock.yOffset = 0;
                return;
            }

            if (selectedView.hasCaptionLayout()) {
                layoutBlock.layout = selectedView.getCaptionLayout();
                layoutBlock.xOffset = layoutBlock.yOffset = 0;
                return;
            }

            if (messageObject.textLayoutBlocks == null) {
                layoutBlock.layout = null;
                return;
            }

            if (messageObject.textLayoutBlocks.size() == 1) {
                layoutBlock.layout = messageObject.textLayoutBlocks.get(0).textLayout;
                layoutBlock.yOffset = 0;
                layoutBlock.xOffset = -(messageObject.textLayoutBlocks.get(0).isRtl() ? (int) Math.ceil(messageObject.textXOffset) : 0);
                return;
            }

            for (int i = 0; i < messageObject.textLayoutBlocks.size(); i++) {
                MessageObject.TextLayoutBlock block = messageObject.textLayoutBlocks.get(i);
                if (offset >= block.charactersOffset && offset <= block.charactersEnd) {
                    layoutBlock.layout = messageObject.textLayoutBlocks.get(i).textLayout;
                    layoutBlock.yOffset = messageObject.textLayoutBlocks.get(i).textYOffset;
                    layoutBlock.xOffset = -(block.isRtl() ? (int) Math.ceil(messageObject.textXOffset) : 0);
                    return;
                }
            }
            layoutBlock.layout = null;
        }

        @Override
        protected void onExitSelectionMode(boolean instant) {
            if (selectedView != null && selectedView.isDrawingSelectionBackground() && !instant) {
                final ChatMessageCell cell = selectedView;
                final int id = selectedView.getMessageObject().getId();
                Animator oldAnimator = animatorSparseArray.get(id);
                if (oldAnimator != null) {
                    oldAnimator.removeAllListeners();
                    oldAnimator.cancel();
                }
                cell.setSelectedBackgroundProgress(0.01f);
                ValueAnimator animator = ValueAnimator.ofFloat(0.01f, 1f);
                animator.addUpdateListener(animation -> {
                    float exit = (float) animation.getAnimatedValue();
                    if (cell.getMessageObject() != null && cell.getMessageObject().getId() == id) {
                        cell.setSelectedBackgroundProgress(exit);
                    }
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        cell.setSelectedBackgroundProgress(0.0f);
                    }
                });
                animator.setDuration(300);
                animator.start();

                animatorSparseArray.put(id, animator);
            }
        }

        public void onChatMessageCellAttached(ChatMessageCell chatMessageCell) {
            if (chatMessageCell.getMessageObject() != null && chatMessageCell.getMessageObject().getId() == selectedCellId) {
                this.selectedView = chatMessageCell;
            }
        }

        public void onChatMessageCellDetached(ChatMessageCell chatMessageCell) {
            if (chatMessageCell.getMessageObject() != null && chatMessageCell.getMessageObject().getId() == selectedCellId) {
                this.selectedView = null;
            }
        }

        public void drawCaption(boolean isOut, StaticLayout captionLayout, Canvas canvas) {
            if (isDescription) {
                return;
            }
            if (isOut) {
                selectionPaint.setColor(getThemedColor(Theme.key_chat_outTextSelectionHighlight));
            } else {
                selectionPaint.setColor(getThemedColor(key_chat_inTextSelectionHighlight));
            }
            drawSelection(canvas, captionLayout, selectionStart, selectionEnd);
        }

        public void drawDescription(boolean isOut, StaticLayout layout, Canvas canvas) {
            if (!isDescription) {
                return;
            }
            if (isOut) {
                selectionPaint.setColor(getThemedColor(Theme.key_chat_outTextSelectionHighlight));
            } else {
                selectionPaint.setColor(getThemedColor(key_chat_inTextSelectionHighlight));
            }
            drawSelection(canvas, layout, selectionStart, selectionEnd);
        }

        @Override
        public void invalidate() {
            super.invalidate();
            if (selectedView != null && selectedView.getCurrentMessagesGroup() != null) {
                parentView.invalidate();
            }
        }

        public void cancelAllAnimators() {
            for (int i = 0; i < animatorSparseArray.size(); i++) {
                Animator animator = animatorSparseArray.get(animatorSparseArray.keyAt(i));
                animator.cancel();
            }
            animatorSparseArray.clear();
        }

        public void setIsDescription(boolean b) {
            maybeIsDescription = b;
        }

        @Override
        public void clear(boolean instant) {
            super.clear(instant);
            isDescription = false;
        }

        public int getTextSelectionType(ChatMessageCell cell) {
            if (isDescription) {
                return TYPE_DESCRIPTION;
            }
            if (cell.hasCaptionLayout()) {
                return TYPE_CAPTION;
            }
            return TYPE_MESSAGE;
        }

        public void updateTextPosition(int textX, int textY) {
            if (this.textX != textX || this.textY != textY) {
                this.textX = textX;
                this.textY = textY;
                invalidate();
            }
        }

        public void checkDataChanged(MessageObject messageObject) {
            if (selectedCellId == messageObject.getId()) {
                clear(true);
            }
        }
    }

    public static class ArticleTextSelectionHelper extends TextSelectionHelper<ArticleSelectableView> {

        int startViewPosition = -1;
        int startViewChildPosition = -1;
        int startViewOffset;

        int endViewPosition = -1;
        int endViewChildPosition = -1;
        int endViewOffset;

        int maybeTextIndex = -1;

        SparseArray<CharSequence> textByPosition = new SparseArray<>();
        SparseArray<CharSequence> prefixTextByPosition = new SparseArray<>();
        SparseIntArray childCountByPosition = new SparseIntArray();

        public LinearLayoutManager layoutManager;

        public ArticleTextSelectionHelper() {
            multiselect = true;
            showActionsAsPopupAlways = true;
        }

        public ArrayList<TextLayoutBlock> arrayList = new ArrayList<>();

        @Override
        protected CharSequence getText(ArticleSelectableView view, boolean maybe) {
            arrayList.clear();
            view.fillTextLayoutBlocks(arrayList);
            int i;
            if (maybe) {
                i = maybeTextIndex;
            } else {
                i = startPeek ? startViewChildPosition : endViewChildPosition;
            }
            if (arrayList.isEmpty() || i < 0) {
                return "";
            }
            return arrayList.get(i).getLayout().getText();
        }


        @Override
        protected int getCharOffsetFromCord(int x, int y, int offsetX, int offsetY, ArticleSelectableView view, boolean maybe) {
            if (view == null) {
                return -1;
            }

            int line = -1;
            x -= offsetX;
            y -= offsetY;

            arrayList.clear();
            view.fillTextLayoutBlocks(arrayList);

            int childIndex;
            if (maybe) {
                childIndex = maybeTextIndex;
            } else {
                childIndex = startPeek ? startViewChildPosition : endViewChildPosition;
            }
            StaticLayout layout = arrayList.get(childIndex).getLayout();
            if (x < 0) {
                x = 1;
            }
            if (y < 0) {
                y = 1;
            }
            if (x > layout.getWidth()) {
                x = layout.getWidth();
            }
            if (y > layout.getLineBottom(layout.getLineCount() - 1)) {
                y = (int) (layout.getLineBottom(layout.getLineCount() - 1) - 1);
            }

            for (int i = 0; i < layout.getLineCount(); i++) {
                if (y > layout.getLineTop(i) && y < layout.getLineBottom(i)) {
                    line = i;
                    break;
                }
            }
            if (line >= 0) {
                return layout.getOffsetForHorizontal(line, x);
            }

            return -1;
        }

        @Override
        protected void fillLayoutForOffset(int offset, LayoutBlock layoutBlock, boolean maybe) {
            arrayList.clear();
            ArticleSelectableView selectedView = maybe ? maybeSelectedView : this.selectedView;
            if (selectedView == null) {
                layoutBlock.layout = null;
                return;
            }
            selectedView.fillTextLayoutBlocks(arrayList);
            if (maybe) {
                layoutBlock.layout = arrayList.get(maybeTextIndex).getLayout();
            } else {
                int index = (startPeek ? startViewChildPosition : endViewChildPosition);
                if (index < 0 || index >= arrayList.size()) {
                    layoutBlock.layout = null;
                    return;
                }
                layoutBlock.layout = arrayList.get(index).getLayout();
            }
            layoutBlock.xOffset = layoutBlock.yOffset = 0;
        }

        @Override
        protected int getLineHeight() {
            if (selectedView == null) {
                return 0;
            } else {
                arrayList.clear();
                selectedView.fillTextLayoutBlocks(arrayList);
                int index = startPeek ? startViewChildPosition : endViewChildPosition;
                if (index < 0 || index >= arrayList.size()) {
                    return 0;
                }
                StaticLayout layout = arrayList.get(index).getLayout();
                int min = Integer.MAX_VALUE;
                for (int i = 0; i < layout.getLineCount(); i++) {
                    int h = layout.getLineBottom(i) - layout.getLineTop(i);
                    if (h < min) min = h;
                }
                return min;
            }
        }

        public void trySelect(View view) {
            if (maybeSelectedView != null) {
                startSelectionRunnable.run();
            }
        }

        public void setMaybeView(int x, int y, View parentView) {
            if (parentView instanceof ArticleSelectableView) {
                capturedX = x;
                capturedY = y;
                maybeSelectedView = (ArticleSelectableView) parentView;
                maybeTextIndex = findClosestLayoutIndex(x, y, maybeSelectedView);
                if (maybeTextIndex < 0) {
                    maybeSelectedView = null;
                } else {
                    maybeTextX = arrayList.get(maybeTextIndex).getX();
                    maybeTextY = arrayList.get(maybeTextIndex).getY();
                }
            }
        }

        private int findClosestLayoutIndex(int x, int y, ArticleSelectableView maybeSelectedView) {
            if (maybeSelectedView instanceof ViewGroup) {
                ViewGroup parent = ((ViewGroup) maybeSelectedView);
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View child = parent.getChildAt(i);
                    if (child instanceof ArticleSelectableView && y > child.getY() && y < child.getY() + child.getHeight()) {
                        return findClosestLayoutIndex((int) (x - child.getX()), (int) (y - child.getY()), (ArticleSelectableView) child);
                    }
                }
            }
            arrayList.clear();
            maybeSelectedView.fillTextLayoutBlocks(arrayList);
            if (arrayList.isEmpty()) {
                return -1;
            } else {
                int minDistance = Integer.MAX_VALUE;
                int minIndex = -1;

                for (int i = arrayList.size() - 1; i >= 0; i--) {
                    TextLayoutBlock block = arrayList.get(i);
                    int top = block.getY();
                    int bottom = top + block.getLayout().getHeight();
                    if (y >= top && y < bottom) {
                        minDistance = 0;
                        minIndex = i;
                        break;
                    }
                    int d = Math.min(Math.abs(y - top), Math.abs(y - bottom));
                    if (d < minDistance) {
                        minDistance = d;
                        minIndex = i;
                    }
                }

                if (minIndex < 0) {
                    return -1;
                }
                int row = arrayList.get(minIndex).getRow();

                if (row > 0) {
                    if (minDistance < AndroidUtilities.dp(24)) {
                        int minDistanceX = Integer.MAX_VALUE;
                        int minIndexX = minIndex;


                        for (int i = arrayList.size() - 1; i >= 0; i--) {
                            TextLayoutBlock block = arrayList.get(i);
                            if (block.getRow() == row) {
                                int left = block.getX();
                                int right = block.getX() + block.getLayout().getWidth();
                                if (x >= left && x <= right) {
                                    return i;
                                } else {
                                    int d = Math.min(Math.abs(x - left), Math.abs(x - right));
                                    if (d < minDistanceX) {
                                        minDistanceX = d;
                                        minIndexX = i;
                                    }
                                }
                            }
                        }
                        return minIndexX;
                    }
                }
                return minIndex;
            }
        }


        public void draw(Canvas canvas, ArticleSelectableView view, int i) {
            selectionPaint.setColor(getThemedColor(key_chat_inTextSelectionHighlight));

            int position = getAdapterPosition(view);
            if (position < 0) {
                return;
            }

            arrayList.clear();
            view.fillTextLayoutBlocks(arrayList);

            if (!arrayList.isEmpty()) {
                TextLayoutBlock layoutBlock = arrayList.get(i);

                int endOffset = endViewOffset;
                int textLen = layoutBlock.getLayout().getText().length();

                if (endOffset > textLen) {
                    endOffset = textLen;
                }
                if (position == startViewPosition && position == endViewPosition) {
                    if (startViewChildPosition == endViewChildPosition && startViewChildPosition == i) {
                        drawSelection(canvas, layoutBlock.getLayout(), startViewOffset, endOffset);
                    } else if (i == startViewChildPosition) {
                        drawSelection(canvas, layoutBlock.getLayout(), startViewOffset, textLen);
                    } else if (i == endViewChildPosition) {
                        drawSelection(canvas, layoutBlock.getLayout(), 0, endOffset);
                    } else if (i > startViewChildPosition && i < endViewChildPosition) {
                        drawSelection(canvas, layoutBlock.getLayout(), 0, textLen);
                    }
                } else if (position == startViewPosition && startViewChildPosition == i) {
                    drawSelection(canvas, layoutBlock.getLayout(), startViewOffset, textLen);
                } else if (position == endViewPosition && endViewChildPosition == i) {
                    drawSelection(canvas, layoutBlock.getLayout(), 0, endOffset);
                } else if (position > startViewPosition && position < endViewPosition || (position == startViewPosition && i > startViewChildPosition) || (position == endViewPosition && i < endViewChildPosition)) {
                    drawSelection(canvas, layoutBlock.getLayout(), 0, textLen);
                }
            }
        }

        private int getAdapterPosition(ArticleSelectableView view) {
            View child = (View) view;
            ViewParent parent = child.getParent();
            while (parent != this.parentView && parent != null) {
                if (parent instanceof View) {
                    child = (View) parent;
                    parent = child.getParent();
                } else {
                    parent = null;
                    break;
                }
            }
            if (parent != null) {
                if (parentRecyclerView != null) {
                    return parentRecyclerView.getChildAdapterPosition(child);
                } else {
                    return parentView.indexOfChild(child);
                }
            }
            return -1;
        }

        public boolean isSelectable(View child) {
            if (child instanceof ArticleSelectableView) {
                arrayList.clear();
                ((ArticleSelectableView) child).fillTextLayoutBlocks(arrayList);
                if (child instanceof ArticleViewer.BlockTableCell) {
                    return true;
                } else {
                    return !arrayList.isEmpty();
                }
            }
            return false;
        }

        @Override
        protected void onTextSelected(ArticleSelectableView newView, ArticleSelectableView oldView) {
            int position = getAdapterPosition(newView);
            if (position < 0) {
                return;
            }

            startViewPosition = endViewPosition = position;
            startViewChildPosition = endViewChildPosition = maybeTextIndex;


            arrayList.clear();
            newView.fillTextLayoutBlocks(arrayList);
            int n = arrayList.size();
            childCountByPosition.put(position, n);
            for (int i = 0; i < n; i++) {
                textByPosition.put(position + (i << 16), arrayList.get(i).getLayout().getText());
                prefixTextByPosition.put(position + (i << 16), arrayList.get(i).getPrefix());
            }
        }

        protected void onNewViewSelected(ArticleSelectableView oldView, ArticleSelectableView newView, int childPosition) {
            int position = getAdapterPosition(newView);
            int oldPosition = -1;
            if (oldView != null) {
                oldPosition = getAdapterPosition(oldView);
            }
            invalidate();

            if (movingDirectionSettling && startViewPosition == endViewPosition) {
                if (position == startViewPosition) {
                    if (childPosition < startViewChildPosition) {
                        startViewChildPosition = childPosition;
                        pickStartView();
                        movingHandleStart = true;
                        startViewOffset = selectionEnd;
                        selectionStart = selectionEnd - 1;
                    } else {
                        endViewChildPosition = childPosition;
                        pickEndView();
                        movingHandleStart = false;
                        endViewOffset = 0;
                    }
                } else if (position < startViewPosition) {
                    startViewPosition = position;
                    startViewChildPosition = childPosition;
                    pickStartView();
                    movingHandleStart = true;
                    startViewOffset = selectionEnd;
                    selectionStart = selectionEnd - 1;
                } else {
                    endViewPosition = position;
                    endViewChildPosition = childPosition;
                    pickEndView();
                    movingHandleStart = false;
                    endViewOffset = 0;
                }
            } else if (movingHandleStart) {
                if (position == oldPosition) {
                    if (childPosition <= endViewChildPosition || position < endViewPosition) {
                        startViewPosition = position;
                        startViewChildPosition = childPosition;
                        pickStartView();
                        startViewOffset = selectionEnd;
                    } else {
                        endViewPosition = position;
                        startViewChildPosition = endViewChildPosition;
                        endViewChildPosition = childPosition;
                        startViewOffset = endViewOffset;
                        pickEndView();
                        endViewOffset = 0;
                        movingHandleStart = false;
                    }
                } else if (position <= endViewPosition) {
                    startViewPosition = position;
                    startViewChildPosition = childPosition;
                    pickStartView();
                    startViewOffset = selectionEnd;
                } else {
                    endViewPosition = position;
                    startViewChildPosition = endViewChildPosition;
                    endViewChildPosition = childPosition;
                    startViewOffset = endViewOffset;
                    pickEndView();
                    endViewOffset = 0;
                    movingHandleStart = false;
                }
            } else {
                if (position == oldPosition) {
                    if (childPosition >= startViewChildPosition || position > startViewPosition) {
                        endViewPosition = position;
                        endViewChildPosition = childPosition;
                        pickEndView();
                        endViewOffset = 0;
                    } else {
                        startViewPosition = position;
                        endViewChildPosition = startViewChildPosition;
                        startViewChildPosition = childPosition;
                        endViewOffset = startViewOffset;
                        pickStartView();
                        movingHandleStart = true;
                        startViewOffset = selectionEnd;
                    }
                } else if (position >= startViewPosition) {
                    endViewPosition = position;
                    endViewChildPosition = childPosition;
                    pickEndView();
                    endViewOffset = 0;
                } else {
                    startViewPosition = position;
                    endViewChildPosition = startViewChildPosition;
                    startViewChildPosition = childPosition;
                    endViewOffset = startViewOffset;
                    pickStartView();
                    movingHandleStart = true;
                    startViewOffset = selectionEnd;
                }

            }

            arrayList.clear();
            newView.fillTextLayoutBlocks(arrayList);
            int n = arrayList.size();
            childCountByPosition.put(position, n);
            for (int i = 0; i < n; i++) {
                textByPosition.put(position + (i << 16), arrayList.get(i).getLayout().getText());
                prefixTextByPosition.put(position + (i << 16), arrayList.get(i).getPrefix());
            }
        }

        boolean startPeek;

        protected void pickEndView() {
            if (!isSelectionMode()) {
                return;
            }
            startPeek = false;
            if (endViewPosition >= 0) {
                ArticleSelectableView view = null;
                if (layoutManager != null) {
                    view = (ArticleSelectableView) layoutManager.findViewByPosition(endViewPosition);
                } else if (endViewPosition < parentView.getChildCount()) {
                    view = (ArticleSelectableView) parentView.getChildAt(endViewPosition);

                }
                if (view == null) {
                    selectedView = null;
                    return;
                }
                selectedView = view;
                if (startViewPosition != endViewPosition) {
                    selectionStart = 0;
                } else if (startViewChildPosition != endViewChildPosition) {
                    selectionStart = 0;
                } else {
                    selectionStart = startViewOffset;
                }

                selectionEnd = endViewOffset;
                CharSequence text = getText(selectedView, false);
                if (selectionEnd > text.length()) {
                    selectionEnd = text.length();
                }

                arrayList.clear();
                selectedView.fillTextLayoutBlocks(arrayList);
                if (!arrayList.isEmpty()) {
                    textX = arrayList.get(endViewChildPosition).getX();
                    textY = arrayList.get(endViewChildPosition).getY();
                }

            }
        }

        protected void pickStartView() {
            if (!isSelectionMode()) {
                return;
            }
            startPeek = true;
            if (startViewPosition >= 0) {
                ArticleSelectableView view = null;
                if (layoutManager != null) {
                    view = (ArticleSelectableView) layoutManager.findViewByPosition(startViewPosition);
                } else if (endViewPosition < parentView.getChildCount()) {
                    view = (ArticleSelectableView) parentView.getChildAt(startViewPosition);
                }
                if (view == null) {
                    selectedView = null;
                    return;
                }
                selectedView = view;
                if (startViewPosition != endViewPosition) {
                    selectionEnd = getText(selectedView, false).length();
                } else if (startViewChildPosition != endViewChildPosition) {
                    selectionEnd = getText(selectedView, false).length();
                } else {
                    selectionEnd = endViewOffset;
                }

                selectionStart = startViewOffset;


                arrayList.clear();
                selectedView.fillTextLayoutBlocks(arrayList);
                if (!arrayList.isEmpty()) {
                    textX = arrayList.get(startViewChildPosition).getX();
                    textY = arrayList.get(startViewChildPosition).getY();
                }
            }
        }

        protected void onOffsetChanged() {
            int position = getAdapterPosition(selectedView);
            int childPosition = startPeek ? startViewChildPosition : endViewChildPosition;
            if (position == startViewPosition && childPosition == startViewChildPosition) {
                startViewOffset = selectionStart;
            }

            if (position == endViewPosition && childPosition == endViewChildPosition) {
                endViewOffset = selectionEnd;
            }
        }

        public void invalidate() {
            super.invalidate();
            for (int i = 0; i < parentView.getChildCount(); i++) {
                parentView.getChildAt(i).invalidate();
            }
        }

        @Override
        public void clear(boolean instant) {
            super.clear(instant);
            startViewPosition = -1;
            endViewPosition = -1;
            startViewChildPosition = -1;
            endViewChildPosition = -1;
            textByPosition.clear();
            childCountByPosition.clear();
        }

        @Override
        protected CharSequence getSelectedText() {
            SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
            for (int i = startViewPosition; i <= endViewPosition; i++) {
                if (i == startViewPosition) {
                    int n = startViewPosition == endViewPosition ? endViewChildPosition : childCountByPosition.get(i) - 1;
                    for (int k = startViewChildPosition; k <= n; k++) {
                        CharSequence text = textByPosition.get(i + (k << 16));
                        if (text == null) {
                            continue;
                        }
                        if (startViewPosition == endViewPosition && k == endViewChildPosition && k == startViewChildPosition) {
                            int e = endViewOffset;
                            int s = startViewOffset;
                            if (e < s) {
                                int tmp = s;
                                s = e;
                                e = tmp;
                            }
                            if (s < text.length()) {
                                if (e > text.length()) e = text.length();
                                stringBuilder.append(text.subSequence(s, e));
                                stringBuilder.append('\n');
                            }
                        } else if (startViewPosition == endViewPosition && k == endViewChildPosition) {
                            CharSequence prefix = prefixTextByPosition.get(i + (k << 16));
                            if (prefix != null) {
                                stringBuilder.append(prefix).append(' ');
                            }
                            int e = endViewOffset;
                            if (e > text.length()) e = text.length();
                            stringBuilder.append(text.subSequence(0, e));
                            stringBuilder.append('\n');
                        } else if (k == startViewChildPosition) {
                            int s = startViewOffset;
                            if (s < text.length()) {
                                stringBuilder.append(text.subSequence(s, text.length()));
                                stringBuilder.append('\n');
                            }
                        } else {
                            CharSequence prefix = prefixTextByPosition.get(i + (k << 16));
                            if (prefix != null) {
                                stringBuilder.append(prefix).append(' ');
                            }
                            stringBuilder.append(text);
                            stringBuilder.append('\n');
                        }
                    }
                } else if (i == endViewPosition) {
                    for (int k = 0; k <= endViewChildPosition; k++) {
                        CharSequence text = textByPosition.get(i + (k << 16));
                        if (text == null) {
                            continue;
                        }
                        if (startViewPosition == endViewPosition && k == endViewChildPosition && k == startViewChildPosition) {
                            int e = endViewOffset;
                            int s = startViewOffset;
                            if (s < text.length()) {
                                if (e > text.length()) e = text.length();
                                stringBuilder.append(text.subSequence(s, e));
                                stringBuilder.append('\n');
                            }
                        } else if (k == endViewChildPosition) {
                            CharSequence prefix = prefixTextByPosition.get(i + (k << 16));
                            if (prefix != null) {
                                stringBuilder.append(prefix).append(' ');
                            }
                            int e = endViewOffset;
                            if (e > text.length()) e = text.length();
                            stringBuilder.append(text.subSequence(0, e));
                            stringBuilder.append('\n');
                        } else {
                            CharSequence prefix = prefixTextByPosition.get(i + (k << 16));
                            if (prefix != null) {
                                stringBuilder.append(prefix).append(' ');
                            }
                            stringBuilder.append(text);
                            stringBuilder.append('\n');
                        }
                    }
                } else {
                    int n = childCountByPosition.get(i);
                    for (int k = startViewChildPosition; k < n; k++) {
                        CharSequence prefix = prefixTextByPosition.get(i + (k << 16));
                        if (prefix != null) {
                            stringBuilder.append(prefix).append(' ');
                        }
                        stringBuilder.append(textByPosition.get(i + (k << 16)));
                        stringBuilder.append('\n');
                    }
                }
            }

            if (stringBuilder.length() > 0) {
                IgnoreCopySpannable[] spans = stringBuilder.getSpans(0, stringBuilder.length() - 1, IgnoreCopySpannable.class);
                for (IgnoreCopySpannable span : spans) {
                    int end = stringBuilder.getSpanEnd(span);
                    int start = stringBuilder.getSpanStart(span);
                    stringBuilder.delete(start, end);

                }
                return stringBuilder.subSequence(0, stringBuilder.length() - 1);
            } else {
                return null;
            }

        }

        @Override
        protected boolean selectLayout(int x, int y) {
            if (!multiselect) {
                return false;
            }
            if (!(y > selectedView.getTop() && y < selectedView.getBottom())) {
                int n = parentView.getChildCount();
                for (int i = 0; i < n; i++) {
                    if (isSelectable(parentView.getChildAt(i))) {
                        ArticleSelectableView child = (ArticleSelectableView) parentView.getChildAt(i);
                        if (y > child.getTop() && y < child.getBottom()) {
                            int index = findClosestLayoutIndex((int) (x - child.getX()), (int) (y - child.getY()), child);
                            if (index >= 0) {
                                onNewViewSelected(selectedView, child, index);
                                selectedView = child;
                                return true;
                            } else {
                                return false;
                            }
                        }
                    }
                }
                return false;
            } else {
                int currentChildPosition = startPeek ? startViewChildPosition : endViewChildPosition;
                int k = findClosestLayoutIndex((int) (x - selectedView.getX()), (int) (y - selectedView.getY()), selectedView);
                if (k != currentChildPosition && k >= 0) {
                    onNewViewSelected(selectedView, selectedView, k);
                    return true;
                }
            }
            return false;
        }

        @Override
        protected boolean canSelect(int newSelection) {
            if (startViewPosition == endViewPosition && startViewChildPosition == endViewChildPosition) {
                return super.canSelect(newSelection);
            }
            return true;
        }

        @Override
        protected void jumpToLine(int newSelection, int nextWhitespace, boolean viewChanged, float newYoffset, float oldYoffset, ArticleSelectableView oldSelectedView) {
            if (viewChanged && oldSelectedView == selectedView && oldYoffset == newYoffset) {
                if (movingHandleStart) {
                    selectionStart = newSelection;
                } else {
                    selectionEnd = newSelection;
                }
            } else {
                super.jumpToLine(newSelection, nextWhitespace, viewChanged, newYoffset, oldYoffset, oldSelectedView);
            }
        }

        @Override
        protected boolean canShowActions() {
            if (layoutManager == null) {
                return true;
            }
            int firstV = layoutManager.findFirstVisibleItemPosition();
            int lastV = layoutManager.findLastVisibleItemPosition();
            if ((firstV >= startViewPosition && firstV <= endViewPosition) || (lastV >= startViewPosition && lastV <= endViewPosition)) {
                return true;
            }
            if (startViewPosition >= firstV && endViewPosition <= lastV) {
                return true;
            }
            return false;
        }
    }


    public interface ArticleSelectableView extends SelectableView {
        void fillTextLayoutBlocks(ArrayList<TextLayoutBlock> blocks);
    }

    public interface SelectableView {
        int getBottom();

        int getTop();

        float getX();

        float getY();

        int getMeasuredWidth();

        void invalidate();
    }

    public interface TextLayoutBlock {
        StaticLayout getLayout();

        int getX();

        int getY();

        int getRow();

        default CharSequence getPrefix() {
            return null;
        }
    }

    public static class IgnoreCopySpannable {

    }

    private static class PathWithSavedBottom extends Path {

        float lastBottom = 0;

        @Override
        public void reset() {
            super.reset();
            lastBottom = 0;
        }

        @Override
        public void addRect(float left, float top, float right, float bottom, Direction dir) {
            super.addRect(left, top, right, bottom, dir);
            if (bottom > lastBottom) {
                lastBottom = bottom;
            }
        }
    }

    public void setKeyboardSize(int keyboardSize) {
        this.keyboardSize = keyboardSize;
        invalidate();
    }

    public int getParentTopPadding() {
        return 0;
    }

    protected int getThemedColor(String key) {
        return Theme.getColor(key);
    }

    protected Theme.ResourcesProvider getResourcesProvider() {
        return null;
    }
}
