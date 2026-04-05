package org.telegram.ui.Components.poll;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Editable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.messenger.utils.TextWatcherImpl;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.ChatAttachAlertPollLayout;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class PollAddOptionFieldLayout extends FrameLayout implements ViewTreeObserver.OnPreDrawListener {
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    private static final boolean ALLOW_DRAW_IN_CELL = false; // Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;

    public final EditTextBoldCursor textView;
    private final EmojiButton emojiButton;
    private final PollAttachButton attachButton;
    private final BaseFragment fragment;
    private final FrameLayout.LayoutParams lp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
    private final SimpleTextView limitTextView;

    private PollAttachedMedia attachedMedia;
    public ChatMessageCell cellToWatch;
    private int messageIdToWatch;
    private Runnable onCancel;

    private FrameLayout viewsContainer;
    private ViewWrapper viewsContainerWrapper;
    private final int maxLength;

    public PollAddOptionFieldLayout(BaseFragment fragment, @NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.fragment = fragment;
        this.maxLength = fragment.getMessagesController().config.pollAnswerLengthMax.get();


        textView = new EditTextCaption(context, resourcesProvider) {
            private int lastHeight;

            @Override
            protected int emojiCacheType() {
                return AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW;
            }

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                postOnAnimation(() -> updateCell());
            }

            @Override
            public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                InputConnection conn = super.onCreateInputConnection(outAttrs);
                outAttrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
                return conn;
            }

            /*
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                onEditTextDraw(this, canvas);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (!isEnabled()) {
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    onFieldTouchUp(this);
                }
                return super.onTouchEvent(event);
            }

            @Override
            protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
                super.onFocusChanged(focused, direction, previouslyFocusedRect);
                onEditTextFocusChanged(focused);
            }

            @Override
            public ActionMode startActionMode(ActionMode.Callback callback, int type) {
                ActionMode actionMode = super.startActionMode(callback, type);
                onActionModeStart(this, actionMode);
                return actionMode;
            }

            @Override
            public ActionMode startActionMode(ActionMode.Callback callback) {
                ActionMode actionMode = super.startActionMode(callback);
                onActionModeStart(this, actionMode);
                return actionMode;
            }

            @Override
            public boolean onTextContextMenuItem(int id) {
                if (id == android.R.id.paste) {
                    ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clipData = clipboard.getPrimaryClip();
                    if (clipData != null && clipData.getItemCount() == 1 && AndroidUtilities.charSequenceIndexOf(clipData.getItemAt(0).getText(), "\n") > 0) {
                        CharSequence text = clipData.getItemAt(0).getText();
                        ArrayList<CharSequence> parts = new ArrayList<>();
                        StringBuilder current = new StringBuilder();
                        for (int i = 0; i < text.length(); i++) {
                            char c = text.charAt(i);
                            if (c == '\n') {
                                parts.add(current.toString());
                                current.setLength(0);
                            } else {
                                current.append(c);
                            }
                        }
                        if (!TextUtils.isEmpty(current)) {
                            parts.add(current);
                        }
                        if (onPastedMultipleLines(parts)) {
                            return true;
                        }
                    }
                }
                return super.onTextContextMenuItem(id);
            }
            */
        };
        ((EditTextCaption) textView).setAllowTextEntitiesIntersection(true);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
        textView.setHint(getString(R.string.PollAddAnOptionHint));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setMaxLines(Integer.MAX_VALUE);
        textView.setBackground(null);
        textView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_DONE);
        textView.setInputType(textView.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
        textView.addTextChangedListener(new TextWatcherImpl() {
            @Override
            public void afterTextChanged(Editable s) {
                checkTextLengthLimit();
            }
        });

        emojiButton = new EmojiButton(context);
        emojiButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_stickers_menuSelector, resourcesProvider)));
        ScaleStateListAnimator.apply(emojiButton);

        attachButton = new PollAttachButton(getContext(), resourcesProvider, 36);
        attachButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_stickers_menuSelector, resourcesProvider)));
        attachButton.setOnClickListener(v -> ChatAttachAlertPollLayout.openPollAttachMenu(fragment,
            ChatAttachAlertPollLayout.getStartLayoutForMedia(attachedMedia),
            ChatAttachAlertPollLayout.getAllowedLayoutsForIndex(0), media -> {
                attachedMedia = media;
                attachButton.setAttachedMedia(media, true);
                AndroidUtilities.runOnUIThread(() -> {
                    AndroidUtilities.showKeyboard(textView);
                }, 200);
            }, null));
        ScaleStateListAnimator.apply(attachButton);

        limitTextView = new SimpleTextView(getContext());
        limitTextView.setTextSize(13);
        limitTextView.setGravity(Gravity.CENTER);
        limitTextView.setTranslationY(dp(44));
        limitTextView.setVisibility(View.GONE);

        viewsContainerWrapper = new ViewWrapper(context);
        addView(viewsContainerWrapper, lp);

        viewsContainer = new FrameLayout(context) {
            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }
        };
        viewsContainerWrapper.addView(viewsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        viewsContainer.addView(limitTextView, LayoutHelper.createFrame(54, 24, Gravity.RIGHT | Gravity.TOP));
        viewsContainer.addView(emojiButton, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.LEFT));
        viewsContainer.addView(attachButton, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.RIGHT, 0, 0, 5, 0));
        viewsContainer.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL, 44 - 5, 0, 52 - 5, 0));
        textView.setPadding(dp(5), dp(11), dp(5), dp(11));
    }

    public void drawInCell(Canvas canvas) {
        viewsContainerWrapper.drawInCell(canvas);
    }

    public void doOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    public PollAttachedMedia getAttachedMedia() {
        return attachedMedia;
    }

    public void doOnEmojiClick(Runnable runnable) {
        emojiButton.setOnClickListener(v -> runnable.run());
    }

    public void setAnimatedVisibility(float factor) {
        viewsContainer.setAlpha(factor);
    }


    public void setCellToWatch(ChatMessageCell cellToWatch) {
        this.cellToWatch = cellToWatch;
        this.messageIdToWatch = cellToWatch.getMessageObject().getId();
    }

    public void updateCell() {
        if (cellToWatch != null && cellToWatch.getDelegate() != null) {
            cellToWatch.getDelegate().forceUpdate(cellToWatch, false);
        }
    }

    private void cancel() {
        if (onCancel != null) {
            onCancel.run();
            onCancel = null;
        }
    }

    private final int[] cords = new int[2];
    private final Rect rect = new Rect();

    @Override
    public boolean onPreDraw() {
        if (cellToWatch == null) {
            return true;
        }

        final int messageId = cellToWatch.getMessageObject().getId();
        if (!cellToWatch.isAttachedToWindow() || messageIdToWatch != messageId || !cellToWatch.getPollAddButtonBounds(rect)) {
            cancel();
            return true;
        }

        final int cx, cy;
        cellToWatch.getLocationInWindow(cords);
        cx = cords[0];
        cy = cords[1];

        final int sx, sy;
        getLocationInWindow(cords);
        sx = cords[0];
        sy = cords[1];

        rect.offset(cx - sx, cy - sy);
        final int width = rect.width();
        if (lp.width != width) {
            lp.width = width;
            viewsContainerWrapper.setLayoutParams(lp);
        }

        viewsContainerWrapper.setTranslationX(rect.left);
        viewsContainerWrapper.setTranslationY(rect.top + dp(0.66f));

        return true;
    }

    private ViewTreeObserver observer;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        observer = getViewTreeObserver();
        observer.addOnPreDrawListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (observer != null && observer.isAlive()) {
            observer.removeOnPreDrawListener(this);
        }
        observer = null;
        super.onDetachedFromWindow();
    }


    public void setEmojiKeyboardVisible(boolean isEmojiKeyboardVisible, boolean animated) {
        emojiButton.animatorIsEmojiVisible.setValue(isEmojiKeyboardVisible, animated);
    }

    private int lastColor;

    public void setColor(int color) {
        if (lastColor != color) {
            lastColor = color;

            ColorFilter colorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
            emojiButton.emojiDrawable.setColorFilter(colorFilter);
            emojiButton.keyboardDrawable.setColorFilter(colorFilter);
            attachButton.attachDrawable.setColorFilter(colorFilter);
            textView.setCursorColor(color);
            textView.setHandlesColor(color);
            textView.setHintTextColor(color);
        }
    }

    private static final class EmojiButton extends View {
        private final BoolAnimator animatorIsEmojiVisible = new BoolAnimator(this, CubicBezierInterpolator.EASE_OUT_QUINT, 320);

        private final Drawable emojiDrawable;
        private final Drawable keyboardDrawable;

        public EmojiButton(Context context) {
            super(context);
            this.emojiDrawable = context.getResources().getDrawable(R.drawable.outline_poll_emoji_24).mutate();
            this.keyboardDrawable = context.getResources().getDrawable(R.drawable.input_keyboard).mutate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);

            DrawableUtils.setBounds(emojiDrawable, w / 2f, h / 2f, Gravity.CENTER);
            DrawableUtils.setBounds(keyboardDrawable, w / 2f, h / 2f, Gravity.CENTER);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);

            final float isEmojiVisible = animatorIsEmojiVisible.getFloatValue();
            DrawableUtils.drawWithScale(canvas, emojiDrawable, (1f - isEmojiVisible));
            DrawableUtils.drawWithScale(canvas, keyboardDrawable, isEmojiVisible);
        }
    }

    private void checkTextLengthLimit() {
        final int length = textView.getText().length();
        animatorTextWarnVisibility.setValue(length > (maxLength * 7 / 10), true);
        animatorTextErrorVisibility.setValue(length > maxLength, true);
        limitTextView.setText(Integer.toString(maxLength - length));
    }

    private final BoolAnimator animatorTextWarnVisibility = new BoolAnimator(0, this::checkLimitText, CubicBezierInterpolator.EASE_OUT_QUINT, 380);
    private final BoolAnimator animatorTextErrorVisibility = new BoolAnimator(0, this::checkLimitText, CubicBezierInterpolator.EASE_OUT_QUINT, 380);

    private void checkLimitText(int id, float factor, float fraction, FactorAnimator callee) {
        FragmentFloatingButton.setAnimatedVisibility(limitTextView, animatorTextWarnVisibility.getFloatValue());
        final int color = ColorUtils.blendARGB(
            Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, fragment.getResourceProvider()),
            Theme.getColor(Theme.key_text_RedRegular, fragment.getResourceProvider()),
            animatorTextErrorVisibility.getFloatValue()
        );
        limitTextView.setTextColor(color);
    }

    private static class ViewWrapper extends FrameLayout {

        public ViewWrapper(@NonNull Context context) {
            super(context);
        }

        @Override
        protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
            if (!ALLOW_DRAW_IN_CELL || forceDrawChild) {
                return super.drawChild(canvas, child, drawingTime);
            }
            return false;
        }

        private boolean forceDrawChild;
        public void drawInCell(Canvas canvas) {
            if (ALLOW_DRAW_IN_CELL) {
                forceDrawChild = true;
                super.drawChild(canvas, getChildAt(0), SystemClock.uptimeMillis());
                forceDrawChild = false;
            }
        }
    }
}
