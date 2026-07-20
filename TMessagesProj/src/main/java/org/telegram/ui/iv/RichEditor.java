package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.GradientClip;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.StickersActivity;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.MessageSendPreview;
import org.telegram.ui.Components.AIEditorAlert;
import org.telegram.ui.Components.AiButtonDrawable;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.ChatActivityEnterViewAnimatedIconView;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.chat.ChatInputViewsContainer;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.HashMap;

public class RichEditor extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private CharSequence initialText;
    private TL_iv.RichMessage initialRichMessage;
    private int initialSelectionStart = -1;
    private int initialSelectionEnd = -1;

    public RichEditor(CharSequence text) {
        initialText = text;
    }

    public RichEditor setInitialSelection(int start, int end) {
        initialSelectionStart = start;
        initialSelectionEnd = end;
        return this;
    }

    public RichEditor(TL_iv.RichMessage richMessage) {
        initialRichMessage = richMessage;
    }

    private boolean convertToSimpleOnOpen;
    public RichEditor convertToSimpleOnOpen() {
        convertToSimpleOnOpen = true;
        return this;
    }

    private String initialHtml;
    private CharSequence initialHtmlBefore, initialHtmlAfter;
    public RichEditor(String html, boolean isHtml) {
        initialHtml = isHtml ? html : null;
    }

    public RichEditor setHtmlSurrounding(CharSequence before, CharSequence after) {
        initialHtmlBefore = before;
        initialHtmlAfter = after;
        return this;
    }

    public RichEditor setEditing(MessageObject messageObject) {
        editingMessageObject = messageObject;
        return this;
    }

    private MessageObject editingMessageObject;
    private ChatInputViewsContainer animateInputView;
    private ChatActivityEnterView animateEnterView;
    private final Rect tempRect = new Rect();
    private BlurredBackgroundDrawable animateInputBackground;
    private RectF animateFromRect;
    private boolean animatingOpen;
    private int[] location = new int[2];
    private int[] animateEnterViewFrom, animateEnterViewTo;
    private float animateOpenProgress = 1.0f;
    public RichEditor animateFrom(ChatActivity chatActivity) {
        animateInputView = chatActivity.chatInputViewsContainer;
        animateEnterView = chatActivity.getChatActivityEnterView();
        return this;
    }

    private void updateAnimatingLocations() {
        animateInputView.getLocationInWindow(location);
        if (animateFromRect == null) animateFromRect = new RectF();
        animateFromRect = new RectF(animateInputBackground.getBounds());
        animateFromRect.offset(location[0], location[1]);

        if (animateEnterViewFrom == null) animateEnterViewFrom = new int[2];
        animateEnterView.getLocationInWindow(animateEnterViewFrom);
        if (animateEnterViewTo == null) animateEnterViewTo = new int[2];
        animateEnterViewTo[0] = listView.getPaddingLeft();
        animateEnterViewTo[1] = listView.getPaddingTop();
        animateEnterViewTo[0] -= animateEnterView.messageEditText.getX() - dp(16);
    }

    @Override
    public AnimatorSet onCustomTransitionAnimation(boolean isOpen, Runnable callback) {
        if (!isOpen) {
            if (!persistedDraftOnEnd) {
                persistDraft();
                persistedDraftOnEnd = true;
            }
        }
        if (!AndroidUtilities.isTablet() && animateInputView != null && animateEnterView != null) {
            final AnimatorSet animatorSet = new AnimatorSet();

            animateInputBackground = animateInputView.blurredBackgroundDrawable;
            animateInputView.drawInputBackground = false;
            animateInputView.invalidate();
            animateEnterView.setAlpha(0.0f);
            animateEnterView.sendButtonContainer.setVisibility(View.INVISIBLE);

            updateAnimatingLocations();

            final ValueAnimator va = ValueAnimator.ofFloat(
                animateOpenProgress = isOpen ? 0.0f : 1.0f,
                isOpen ? 1.0f : 0.0f
            );
            animatingOpen = true;
            container.invalidate();

//            topGradient.setVisibility(View.INVISIBLE);
//            bottomGradient.setVisibility(View.INVISIBLE);

            va.addUpdateListener(a -> {
                animateOpenProgress = (float) a.getAnimatedValue();
                updateAnimatingLocations();
                listView.setTranslationX(lerp(animateEnterViewFrom[0] - animateEnterViewTo[0], 0, animateOpenProgress));
                listView.setTranslationY(lerp(animateEnterViewFrom[1] - animateEnterViewTo[1], 0, animateOpenProgress));
                container.invalidate();
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animatingOpen = false;
                    animateEnterView.setAlpha(1.0f);
                    animateEnterView.sendButtonContainer.setVisibility(View.VISIBLE);
//                    topGradient.setVisibility(View.VISIBLE);
//                    bottomGradient.setVisibility(View.VISIBLE);
                    animateInputBackground.setRadius(dp(ChatInputViewsContainer.INPUT_BUBBLE_RADIUS));
                    animateInputBackground.setAlpha(0xFF);
                    animateInputView.drawInputBackground = true;
                    animateInputView.invalidate();
                    callback.run();
                }
            });
            if (!isOpen) {
                animatorSet.playTogether(
                    va,
                    ObjectAnimator.ofFloat(topPanel, View.ALPHA, 0f),
                    ObjectAnimator.ofFloat(topPanel, View.TRANSLATION_Y, -dp(16)),
                    ObjectAnimator.ofFloat(bottomInnerContainer, View.ALPHA, 0f),
                    ObjectAnimator.ofFloat(bottomInnerContainer, View.TRANSLATION_Y, dp(16), 0),
                    ObjectAnimator.ofFloat(listView, View.ALPHA, 1f, 0f),
                    ObjectAnimator.ofFloat(topGradient, View.ALPHA, 1f, 0f),
                    ObjectAnimator.ofFloat(bottomGradient, View.ALPHA, 1f, 0f)
                );
            } else {
                animatorSet.playTogether(
                    va,
                    ObjectAnimator.ofFloat(topPanel, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(topPanel, View.TRANSLATION_Y, -dp(16), 0f),
                    ObjectAnimator.ofFloat(bottomInnerContainer, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(bottomInnerContainer, View.TRANSLATION_Y, dp(16), 0f),
                    ObjectAnimator.ofFloat(listView, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(topGradient, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(bottomGradient, View.ALPHA, 0f, 1f)
                );
            }

            animatorSet.setDuration(420);
            animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            container.post(animatorSet::start);

            return animatorSet;
        }
        return super.onCustomTransitionAnimation(isOpen, callback);
    }

    @Override
    protected boolean hideKeyboardOnShow() {
        return false;
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        if (isOpen && !backward && initialSelectionStart >= 0 && listView != null) {
            final int start = initialSelectionStart;
            final int end = initialSelectionEnd;
            initialSelectionStart = initialSelectionEnd = -1;
            listView.applyInitialSelection(start, end);
        } else if (isOpen && !backward && (initialRichMessage != null || initialHtml != null) && listView != null) {
            listView.focusForDraft();
        }
    }

    private ChatActivity chatActivity;
    private Runnable onSentCallback;
    private Runnable onClearedCallback;
    private boolean sent;

    private ArrayList<Button> premiumButtons = new ArrayList<>();

    public RichEditor setChatActivity(ChatActivity chatActivity) {
        this.chatActivity = chatActivity;
        return this;
    }

    public RichEditor setOnCleared(Runnable onClearedCallback) {
        this.onClearedCallback = onClearedCallback;
        return this;
    }

    public RichEditor setOnSent(Runnable onSentCallback) {
        this.onSentCallback = onSentCallback;
        return this;
    }

    private SizeNotifierFrameLayout container;
    private RichEditorListView listView;

    private View topGradient, bottomGradient;

    private FrameLayout topPanel;
    private ImageView backButton;
    private LinearLayout historyButtons;
    private ImageView undoButton;
    private ImageView redoButton;

    private FrameLayout bulletinContainer;
    private FrameLayout bottomContainer;
    private FrameLayout bottomInnerContainer;
    private LinearLayout bottomPanel;

    private ChatActivityEnterViewAnimatedIconView emojiButton;
    private ImageView aiButton;
    private HorizontalScrollView blocksScrollView;
    private LinearLayout blocksLayout;
    private final ArrayList<Button> blockButtons = new ArrayList<>();
    private ImageView addButton;

    private LinearLayout formattingPanel;
    private LinearLayout formattingPanelLayout;
    private HorizontalScrollView formattingScrollView;
    private int formattingScrollMaxWidth = Integer.MAX_VALUE;
    private final ArrayList<Button> formattingButtons = new ArrayList<>();
    private LinearLayout formattingLayout1;
    private LinearLayout formattingLayout2;
    private LinearLayout formattingLayout3;
    private Button aiStyleButton;
    private Button linkButton;
    private Button dateButton;
    private Button mathButton;
    private Button quoteButton;
    private FrameLayout trashPanel;
    private RLottieImageView trashPanelIcon;
    private ItemOptions currentMenuVisible;

    private ChatActivityEnterView.SendButton sendButton;

    private RichCommandSuggestions commandSuggestions;

    private EmojiView emojiView;
    private boolean emojiViewVisible;
    private boolean emojiSearchOpened;
    private int emojiPadding;
    private int keyboardHeight;
    private int keyboardHeightLand;
    private boolean keyboardVisible;
    private SizeNotifierFrameLayout.SizeNotifierFrameLayoutDelegate sizeDelegate;

    @Override
    public View createView(Context context) {
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);
        premiumButtons.clear();

        container = new SizeNotifierFrameLayout(context) {
            private boolean touchStartedInBottomPanel;

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (listView.textSelectionHelper.isInSelectionMode() && listView.textSelectionOverlay.onTouchEvent(ev)) {
                    return true;
                }
                final int dismissBoundary = emojiSearchOpened && emojiView != null
                    ? (int) emojiView.getY()
                    : getHeight() - dp(8 + 44 + 8) - Math.max(Math.max(emojiPadding, bottomInset), imeInset);
                if (ev.getAction() == MotionEvent.ACTION_DOWN && emojiViewVisible && ev.getY() < dismissBoundary) {
                    hideEmojiPopup(true);
                }
                if ((ev.getAction() != MotionEvent.ACTION_DOWN || ev.getY() > getPaddingTop() + dp(8 + 44 + 8) && ev.getY() < dismissBoundary) &&
                    listView.textSelectionOverlay.checkOnTap(ev)
                ) {
                    ev.setAction(MotionEvent.ACTION_CANCEL);
                }
                if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                    final int bottomPanelTop = getHeight() - dp(8 + 44 + 8) - Math.max(Math.max(emojiPadding, bottomInset), imeInset);
                    touchStartedInBottomPanel = bottomPanel.getVisibility() == View.VISIBLE && ev.getY() >= bottomPanelTop;
                }
                if (!touchStartedInBottomPanel && listView.handleSelectionTouch(ev)) {
                    return true;
                }
                return super.dispatchTouchEvent(ev);
            }

            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_S && event.isCtrlPressed()) {
                    saveDraftWithBulletin();
                    return true;
                }
                if (listView.handleKeyEvent(event)) return true;
                return super.dispatchKeyEvent(event);
            }

            private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path clipPath = new Path();
            private final RectF rect = new RectF();
            @Override
            protected void dispatchDraw(Canvas canvas) {
                bgPaint.setColor(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhite), animateOpenProgress));
                if (animatingOpen && animateInputBackground != null) {
                    rect.set(0, 0, getWidth(), getHeight());
                    rect.inset(-dp(7), -dp(7));
                    final float rad = lerp(dpf2(ChatInputViewsContainer.INPUT_BUBBLE_RADIUS), 0, animateOpenProgress);
                    lerp(animateFromRect, rect, animateOpenProgress, rect);

                    tempRect.set(animateInputBackground.getBounds());
                    animateInputBackground.setBounds(
                        (int) rect.left, (int) rect.top,
                        (int) rect.right, (int) rect.bottom
                    );
                    animateInputBackground.setRadius(rad);
                    animateInputBackground.setAlpha((int) (0xFF * (1.0f - animateOpenProgress)));
                    animateInputBackground.draw(canvas);
                    animateInputBackground.setBounds(tempRect);

                    rect.inset(dp(7), dp(7));
                    canvas.drawRoundRect(rect, rad, rad, bgPaint);

                    if (animateEnterView != null) {
                        canvas.save();
                        canvas.translate(
                            lerp(animateEnterViewFrom[0], animateEnterViewTo[0], animateOpenProgress),
                            lerp(animateEnterViewFrom[1], animateEnterViewTo[1], animateOpenProgress)
                        );
                        canvas.saveLayerAlpha(0, 0, animateEnterView.getWidth(), animateEnterView.getHeight(), (int) (0xFF * (1.0f - animateOpenProgress)), Canvas.ALL_SAVE_FLAG);
                        animateEnterView.draw(canvas);
                        canvas.restore();
                        canvas.restore();

                        canvas.save();
                        canvas.translate(
                            lerp(rect.right, bottomContainer.getX() + bottomInnerContainer.getX() + bottomPanel.getX() + sendButton.getX() + sendButton.getWidth(), animateOpenProgress) - animateEnterView.sendButtonContainer.getWidth(),
                            lerp(rect.bottom, bottomContainer.getY() + bottomInnerContainer.getY() + bottomPanel.getY() + sendButton.getY() + sendButton.getHeight(), animateOpenProgress) - animateEnterView.sendButtonContainer.getHeight()
                        );
                        canvas.saveLayerAlpha(-dp(6), -dp(6), animateEnterView.sendButtonContainer.getWidth(), animateEnterView.sendButtonContainer.getHeight(), (int) (0xFF * (1.0f - animateOpenProgress)), Canvas.ALL_SAVE_FLAG);
                        animateEnterView.sendButtonContainer.draw(canvas);
                        canvas.restore();
                        canvas.restore();
                    }

                    canvas.save();
//                    clipPath.rewind();
//                    clipPath.addRoundRect(rect, rad, rad, Path.Direction.CW);
//                    canvas.clipPath(clipPath);
                    super.dispatchDraw(canvas);
                    canvas.restore();
                } else {
                    canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
                    super.dispatchDraw(canvas);
                }
            }
        };
        setHasOwnBackground(true);
        container.setFocusable(true);
        container.setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= 26) {
            container.setDefaultFocusHighlightEnabled(false);
        }

        keyboardHeight = MessagesController.getGlobalEmojiSettings().getInt("kbd_height", dp(200));
        keyboardHeightLand = MessagesController.getGlobalEmojiSettings().getInt("kbd_height_land3", dp(200));
        sizeDelegate = this::onKeyboardSizeChanged;
        container.addDelegate(sizeDelegate);

        listView = new RichEditorListView(context, currentAccount, getResourceProvider(), new RichEditorListView.Delegate() {
            @Override
            public ItemOptions makeMenu(View anchor) { return ItemOptions.makeOptions(RichEditor.this, anchor); }
            @Override
            public void onSelectionChanged() {
                updateBottomPanel(listView.isInSelectionMode() && listView.selectionHasInlineFormattable() ? BOTTOM_PANEL_FORMATTING : BOTTOM_PANEL_TOOLBAR, true);
                updateFormattingButtons();
                updateBlockButtons();
            }
            @Override
            public void onContentChanged() { updateSendButtonLoading(); updateSendButtonLock(); scheduleLimitCheck(); }
            @Override
            public void onHistoryChanged() { updateHistoryButtons(); updateSendButtonLock(); }
            @Override
            public void onOpenAttachRequest(int a, int b) { openAttach(a, b); }
            @Override
            public void onOpenLocationRequest(BlockRow row) { openLocationPicker(row); }
            @Override
            public void onSlashSuggest(RichTextCell cell, String query) {
                if (commandSuggestions == null) commandSuggestions = new RichCommandSuggestions(anchor -> ItemOptions.makeOptions(RichEditor.this, anchor), getResourceProvider());
                commandSuggestions.update(cell, query);
            }
            @Override
            public void onListScrolled(int dy) {}
            @Override
            public void onListLayoutUpdated() {}
            @Override
            public void makeEditTextFocusable(RichEditText et, boolean showKeyboard) {}
            @Override
            public void onReorderStart() {
                reorderSavedPanelType = bottomPanelType;
                setTrashHovered(false, false);
                updateBottomPanel(BOTTOM_PANEL_TRASH, true);
            }
            @Override
            public boolean onReorderMove(float screenX, float screenY) {
                final boolean over = isOverTrash(screenY);
                setTrashHovered(over, true);
                return over;
            }
            @Override
            public void onReorderEnd() {
                setTrashHovered(false, true);
                updateBottomPanel(reorderSavedPanelType == BOTTOM_PANEL_TRASH ? BOTTOM_PANEL_TOOLBAR : reorderSavedPanelType, true);
            }
        });
        container.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        container.addView(listView.getOverlayView(), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (initialRichMessage != null) {
            listView.loadRichMessage(initialRichMessage);
        } else if (initialHtml != null) {
            listView.loadHtml(initialHtmlBefore, initialHtml, initialHtmlAfter);
        } else if (initialText != null) {
            listView.setInitialText(initialText);
        }
        listView.resetHistoryBaseline();

        topGradient = new View(context);
        topGradient.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int [] {
            getThemedColor(Theme.key_windowBackgroundWhite),
            Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhite), 0.0f)
        }));
        container.addView(topGradient, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8 + 44 + 16, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        bottomGradient = new View(context);
        bottomGradient.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int [] {
                Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhite), 0.0f),
                getThemedColor(Theme.key_windowBackgroundWhite)
        }));
        container.addView(bottomGradient, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8 + 44 + 16, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        topPanel = new FrameLayout(context);
        topPanel.setClipChildren(false);
        topPanel.setClipToPadding(false);
        container.addView(topPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8 + 44 + 16, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        backButton = new ImageView(context);
        backButton.setImageResource(R.drawable.ic_ab_back);
        backButton.setScaleType(ImageView.ScaleType.CENTER);
        backButton.setBackground(withShadow(Theme.createRadSelectorDrawable(getThemedColor(Theme.key_windowBackgroundWhite), Theme.blendOver(getThemedColor(Theme.key_windowBackgroundWhite), getThemedColor(Theme.key_listSelector)), dp(22), dp(22))));
        backButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        ScaleStateListAnimator.apply(backButton);
        backButton.setContentDescription(getString(R.string.AccDescrGoBack));
        backButton.setOnClickListener(v -> {
            if (!listView.deselectIfAny()) finishFragment();
        });
        topPanel.addView(backButton, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.LEFT, 8, 8, 8, 8));

        historyButtons = new LinearLayout(context);
        historyButtons.setOrientation(LinearLayout.HORIZONTAL);
        historyButtons.setBackground(withShadow(Theme.createRoundRectDrawable(dp(22), getThemedColor(Theme.key_windowBackgroundWhite))));
        topPanel.addView(historyButtons, LayoutHelper.createFrame(82, 44, Gravity.TOP | Gravity.RIGHT, 8, 8, 8, 8));

        undoButton = new ImageView(context);
        undoButton.setImageResource(R.drawable.iv_undo);
        undoButton.setScaleType(ImageView.ScaleType.CENTER);
        undoButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        undoButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        ScaleStateListAnimator.apply(undoButton);
        undoButton.setContentDescription(getString(R.string.Undo));
        undoButton.setOnClickListener(v -> listView.undo());
        historyButtons.addView(undoButton, LayoutHelper.createLinear(41, 41, Gravity.CENTER_VERTICAL));

        redoButton = new ImageView(context);
        redoButton.setImageResource(R.drawable.iv_redo);
        redoButton.setScaleType(ImageView.ScaleType.CENTER);
        redoButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        redoButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        ScaleStateListAnimator.apply(redoButton);
        redoButton.setContentDescription(getString(R.string.Redo));
        redoButton.setOnClickListener(v -> listView.redo());
        historyButtons.addView(redoButton, LayoutHelper.createLinear(41, 41, Gravity.CENTER_VERTICAL));

        bottomContainer = new FrameLayout(context);
        bottomContainer.setClipChildren(false);
        bottomContainer.setClipToPadding(false);
        container.addView(bottomContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8 + 44 + 8 + 100, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        bottomInnerContainer = new FrameLayout(context);
        bottomInnerContainer.setClipChildren(false);
        bottomInnerContainer.setClipToPadding(false);
        bottomContainer.addView(bottomInnerContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8 + 44 + 8 + 100, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        bottomPanel = new LinearLayout(context);
        bottomPanel.setClipToPadding(false);
        bottomPanel.setClipChildren(false);
        bottomPanel.setPadding(dp(8), dp(8), dp(8), dp(8));
        bottomInnerContainer.addView(bottomPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8 + 44 + 8, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        bulletinContainer = new FrameLayout(context);
        bottomInnerContainer.addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, 8 + 44 + 8));

        emojiButton = new ChatActivityEnterViewAnimatedIconView(context, 24);
        emojiButton.setPadding(dp(10), dp(10), dp(10), dp(10));
        emojiButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        emojiButton.setBackground(withShadow(Theme.createRadSelectorDrawable(getThemedColor(Theme.key_windowBackgroundWhite), Theme.blendOver(getThemedColor(Theme.key_windowBackgroundWhite), getThemedColor(Theme.key_listSelector)), dp(22), dp(22))));
        emojiButton.setState(ChatActivityEnterViewAnimatedIconView.State.SMILE, false);
        bottomPanel.addView(emojiButton, LayoutHelper.createLinear(44, 44, 0, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
        ScaleStateListAnimator.apply(emojiButton);
        emojiButton.setContentDescription(getString(R.string.AccDescrEmojiButton));
        emojiButton.setOnClickListener(v -> toggleEmojiPopup());

        aiButton = new ImageView(context);
        aiButton.setImageDrawable(new AiButtonDrawable(context));
        aiButton.setScaleType(ImageView.ScaleType.CENTER);
        aiButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        aiButton.setBackground(withShadow(Theme.createRadSelectorDrawable(getThemedColor(Theme.key_windowBackgroundWhite), Theme.blendOver(getThemedColor(Theme.key_windowBackgroundWhite), getThemedColor(Theme.key_listSelector)), dp(22), dp(22))));
        bottomPanel.addView(aiButton, LayoutHelper.createLinear(44, 44, 0, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
        ScaleStateListAnimator.apply(aiButton);
        aiButton.setContentDescription(getString(R.string.AIEditor));
        aiButton.setOnClickListener(v -> {
            if (listView.isInSelectionMode()) {
                onAiStyleSelection();
            } else {
                new RichAIComposeSheet(getContext(), currentAccount, getResourceProvider(), richMessage -> listView.addRichMessage(richMessage)).show();
            }
        });

        final FrameLayout blocksContainer2 = new FrameLayout(context);
        blocksContainer2.setClipToPadding(false);
        blocksContainer2.setClipChildren(false);

        final FrameLayout blocksContainer = new FrameLayout(context);
        blocksContainer.setBackground(withShadow(Theme.createRoundRectDrawable(dp(22), getThemedColor(Theme.key_windowBackgroundWhite))));
        blocksContainer2.addView(blocksContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 44, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        blocksScrollView = new HorizontalScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                final int mode = MeasureSpec.getMode(widthMeasureSpec);
                final int available = MeasureSpec.getSize(widthMeasureSpec);
                if (mode == MeasureSpec.EXACTLY) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    return;
                }
                super.onMeasure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.UNSPECIFIED), heightMeasureSpec);
                final int content = getMeasuredWidth();
                final int width = mode == MeasureSpec.AT_MOST ? Math.min(content, available) : content;
                setMeasuredDimension(width, getMeasuredHeight());
            }
        };
        blocksScrollView.setClipToOutline(true);
        blocksScrollView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(22));
            }
        });
        blocksLayout = new LinearLayout(context);
        blocksLayout.setPadding(dp(2), 0, dp(2), 0);
        blocksLayout.setOrientation(LinearLayout.HORIZONTAL);
        blocksScrollView.addView(blocksLayout);
        blocksContainer.addView(blocksScrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        addBlockButton(R.drawable.iv_text, 1).setOnClickListener(v -> {
            if (currentMenuVisible != null) {
                currentMenuVisible.dismiss();
                currentMenuVisible = null;
            }
            final boolean premiumLock = !MessagesController.getInstance(currentAccount).richEditorAllowed() && !UserConfig.getInstance(currentAccount).isPremium();
            final BlockRow row = listView.findFocusedRow();
            final ItemOptions o = ItemOptions.makeOptions(RichEditor.this, v, true).dontFocus();
            final ItemOptions headers = o.makeSwipeback();

            headers.add(R.drawable.ic_ab_back, getString(R.string.Back), () -> o.closeSwipeback());
            headers.addGap();

            headers.addChecked(row != null && row.block instanceof TL_iv.pageBlockHeading1, R.drawable.iv_h1, getString(R.string.ArticleHeading1), () -> { listView.turnIntoKeepList(row, new TL_iv.pageBlockHeading1()); o.dismiss(); });
            headers.getLast().textView.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
            headers.getLast().textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SharedConfig.fontSize + 2);

            headers.addChecked(row != null && row.block instanceof TL_iv.pageBlockHeading2, R.drawable.iv_h2, getString(R.string.ArticleHeading2), () -> { listView.turnIntoKeepList(row, new TL_iv.pageBlockHeading2()); o.dismiss(); });
            headers.getLast().textView.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
            headers.getLast().textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SharedConfig.fontSize + 1);

            headers.addChecked(row != null && row.block instanceof TL_iv.pageBlockHeading3, R.drawable.iv_h3, getString(R.string.ArticleHeading3), () -> { listView.turnIntoKeepList(row, new TL_iv.pageBlockHeading3()); o.dismiss(); });
            headers.getLast().textView.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
            headers.getLast().textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SharedConfig.fontSize);

            headers.addChecked(row != null && row.block instanceof TL_iv.pageBlockHeading4, R.drawable.iv_h4, getString(R.string.ArticleHeading4), () -> { listView.turnIntoKeepList(row, new TL_iv.pageBlockHeading4()); o.dismiss(); });
            headers.getLast().textView.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
            headers.getLast().textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SharedConfig.fontSize - 1);

            headers.addChecked(row != null && row.block instanceof TL_iv.pageBlockHeading5, R.drawable.iv_h5, getString(R.string.ArticleHeading5), () -> { listView.turnIntoKeepList(row, new TL_iv.pageBlockHeading5()); o.dismiss(); });
            headers.getLast().textView.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
            headers.getLast().textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SharedConfig.fontSize - 2);

            headers.addChecked(row != null && row.block instanceof TL_iv.pageBlockHeading6, R.drawable.iv_h6, getString(R.string.ArticleHeading6), () -> { listView.turnIntoKeepList(row, new TL_iv.pageBlockHeading6()); o.dismiss(); });
            headers.getLast().textView.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
            headers.getLast().textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SharedConfig.fontSize - 3);

            o.addChecked(row != null && RichEditorListView.isHeading(row.block), new RequiresPremiumDrawable(context, R.drawable.iv_h).setPremium(premiumLock).setCutoutColorKey(Theme.key_actionBarDefaultSubmenuBackground), getString(R.string.ArticleHeading), () -> o.openSwipeback(headers));
            o.getLast().textView.setTypeface(AndroidUtilities.bold());
            o.getLast().setPadding(dp(LocaleController.isRTL ? 18 : 9), 0, dp(LocaleController.isRTL ? 9 : 18), 0);

            o.addChecked(row != null && row.block instanceof TL_iv.pageBlockParagraph, R.drawable.iv_text2, getString(R.string.ArticleText), () -> { listView.turnIntoKeepList(row, new TL_iv.pageBlockParagraph()); });
            o.addChecked(row != null && row.block instanceof TL_iv.pageBlockBlockquote, R.drawable.iv_quote, getString(R.string.ArticleQuote), () -> { listView.turnInto(row, RichEditorListView.newBlockquote(), 0, 0, false, false); });
            o.addChecked(row != null && row.block instanceof TL_iv.pageBlockPullquote, new RequiresPremiumDrawable(context, R.drawable.iv_pullquote).setPremium(premiumLock).setCutoutColorKey(Theme.key_actionBarDefaultSubmenuBackground), getString(R.string.ArticlePullquote), () -> { listView.turnInto(row, RichEditorListView.newPullquote(), 0, 0, false, false); });
            o.getLast().setPadding(dp(LocaleController.isRTL ? 18 : 9), 0, dp(LocaleController.isRTL ? 9 : 18), 0);
            o.addChecked(row != null && row.block instanceof TL_iv.pageBlockPreformatted, R.drawable.iv_code, getString(R.string.ArticleCode), () -> { listView.turnIntoKeepList(row, new TL_iv.pageBlockPreformatted()); });;
            o.addChecked(row != null && row.block instanceof TL_iv.pageBlockFooter, new RequiresPremiumDrawable(context, R.drawable.iv_footer).setPremium(premiumLock).setCutoutColorKey(Theme.key_actionBarDefaultSubmenuBackground), getString(R.string.ArticleFooter), () -> { listView.turnIntoKeepList(row, new TL_iv.pageBlockFooter()); });;
            o.getLast().setPadding(dp(LocaleController.isRTL ? 18 : 9), 0, dp(LocaleController.isRTL ? 9 : 18), 0);
            currentMenuVisible = o.show();
        });
        addBlockButton(R.drawable.iv_lists, 2, true).setOnClickListener(v -> {
            if (currentMenuVisible != null) {
                currentMenuVisible.dismiss();
                currentMenuVisible = null;
            }
            final ItemOptions o = ItemOptions.makeOptions(RichEditor.this, v).dontFocus();
            final BlockRow row = listView.findFocusedRow();
            o
                .addChecked(row == null || !row.isInList(), R.drawable.field_carret_empty, getString(R.string.ArticleNone), () -> listView.turnIntoList(row, 0))
                .addChecked(row != null && row.isInList() && !row.isChecklist() && !row.isOrdered(), R.drawable.iv_list, getString(R.string.ArticleListBulletedList), () -> listView.turnIntoList(row, 1))
                .addChecked(row != null && row.isInList() && !row.isChecklist() && row.isOrdered(), R.drawable.iv_ordered_list, getString(R.string.ArticleListNumberedList), () -> listView.turnIntoList(row, 2))
                .addChecked(row != null && row.isInList() && row.isChecklist() && !row.isOrdered(), R.drawable.iv_todo, getString(R.string.ArticleListChecklist), () -> listView.turnIntoList(row, 3))
                .addChecked(row != null && row.block instanceof TL_iv.pageBlockDetails, R.drawable.iv_details, getString(R.string.ArticleToggleBlock), listView::insertDetails);
            final boolean canIndent = listView.canIndentSelection();
            final boolean canOutdent = listView.canOutdentSelection();
            if (canIndent || canOutdent) {
                o.addGap();
                if (canIndent) {
                    o.add(R.drawable.iv_list_tab, getString(R.string.ArticleIndent), () -> { listView.indentSelection(false); o.dismiss(); });
                }
                if (canOutdent) {
                    o.add(R.drawable.iv_list_untab, getString(R.string.ArticleOutdent), () -> { listView.indentSelection(true); o.dismiss(); });
                }
            }
            currentMenuVisible = o.forceTop(true).show();
        });
        addBlockButton(R.drawable.iv_table, 4, true).setOnClickListener(v -> {
            if (currentMenuVisible != null) {
                currentMenuVisible.dismiss();
                currentMenuVisible = null;
            }
            RichTableCell table = listView.activeCellSelectionTable;
            if (table == null) {
                final RichTableCell focused = listView.findFocusedTableCell();
                if (focused != null && focused.getModel() != null) {
                    final TL_iv.pageTableCell cur = listView.focusedCellOf(focused);
                    if (cur != null) {
                        listView.enterCellSelectionMode(focused, cur);
                        table = focused;
                    }
                }
            }
            if (table != null && table.getModel() != null && table.hasCellSelection()) {
                listView.showTableCellMenu(table);
            } else {
                listView.addBlock(RichTextCell.newEmptyTable(2, 2));
            }
        });
        addBlockButton(R.drawable.iv_math, 7, true).setOnClickListener(v -> {
            if (currentMenuVisible != null) {
                currentMenuVisible.dismiss();
                currentMenuVisible = null;
            }
            final BlockRow row = listView.findFocusedRow();
            final TL_iv.pageBlockMath math = row != null && row.block instanceof TL_iv.pageBlockMath ? (TL_iv.pageBlockMath) row.block : null;
            ChatAttachAlertRichLayout.showEditLatexSheet(getContext(), math == null || TextUtils.isEmpty(math.source) ? "" : math.source, source -> {
                if (math != null) {
                    math.source = source;
                    listView.adapter.update(false);
                } else {
                    final TL_iv.pageBlockMath newMath = new TL_iv.pageBlockMath();
                    newMath.source = source;
                    listView.addBlock(newMath);
                }
            }, getResourceProvider());
        });

        bottomPanel.addView(blocksContainer2, LayoutHelper.createLinear(0, 44, 1f));

        addButton = new ImageView(context);
        addButton.setImageResource(R.drawable.outline_poll_attach_24);
        addButton.setScaleType(ImageView.ScaleType.CENTER);
        addButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        addButton.setBackground(withShadow(Theme.createRadSelectorDrawable(getThemedColor(Theme.key_windowBackgroundWhite), Theme.blendOver(getThemedColor(Theme.key_windowBackgroundWhite), getThemedColor(Theme.key_listSelector)), dp(22), dp(22))));
        bottomPanel.addView(addButton, LayoutHelper.createLinear(44, 44, 0, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 8, 0, 0, 0));
        ScaleStateListAnimator.apply(addButton);
        addButton.setContentDescription(getString(R.string.AccDescrAttachButton));
        addButton.setOnClickListener(v -> {
            listView.pendingMediaRow = null;
            openAttach();
        });

        formattingPanel = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                final int avail = MeasureSpec.getSize(widthMeasureSpec);
                int reserved = getPaddingLeft() + getPaddingRight();
                if (formattingLayout1 != null) {
                    formattingLayout1.measure(
                        MeasureSpec.makeMeasureSpec(avail, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(dp(44), MeasureSpec.EXACTLY));
                    final ViewGroup.MarginLayoutParams lp1 = (ViewGroup.MarginLayoutParams) formattingLayout1.getLayoutParams();
                    reserved += formattingLayout1.getMeasuredWidth() + lp1.leftMargin + lp1.rightMargin;
                }
                if (formattingLayout2 != null) {
                    formattingLayout2.measure(
                        MeasureSpec.makeMeasureSpec(avail, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(dp(44), MeasureSpec.EXACTLY));
                    final ViewGroup.MarginLayoutParams lp2 = (ViewGroup.MarginLayoutParams) formattingLayout2.getLayoutParams();
                    reserved += formattingLayout2.getMeasuredWidth() + lp2.leftMargin + lp2.rightMargin;
                }
                if (formattingLayout3 != null) {
                    formattingLayout3.measure(
                            MeasureSpec.makeMeasureSpec(avail, MeasureSpec.AT_MOST),
                            MeasureSpec.makeMeasureSpec(dp(44), MeasureSpec.EXACTLY));
                    final ViewGroup.MarginLayoutParams lp2 = (ViewGroup.MarginLayoutParams) formattingLayout3.getLayoutParams();
                    reserved += formattingLayout3.getMeasuredWidth() + lp2.leftMargin + lp2.rightMargin;
                }
                formattingScrollMaxWidth = Math.max(0, avail - reserved);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        formattingPanel.setOrientation(LinearLayout.HORIZONTAL);
        formattingPanel.setClipToPadding(false);
        formattingPanel.setClipChildren(false);
        formattingPanel.setPadding(dp(8), dp(8), dp(8), dp(8));
        bottomContainer.addView(formattingPanel, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 8 + 44 + 8, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));

        trashPanel = new FrameLayout(context);
        trashPanel.setClipChildren(false);
        trashPanel.setClipToPadding(false);
        trashPanel.setPadding(dp(8), dp(8), dp(8), dp(8));
        bottomContainer.addView(trashPanel, LayoutHelper.createFrame(8 + 64 + 8, 8 + 44 + 8, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));

        trashPanelIcon = new RLottieImageView(context);
        trashPanelIcon.setAnimation(R.raw.group_pip_delete_icon, dp(16), dp(16));
        final RLottieDrawable trashDrawable = trashPanelIcon.getAnimatedDrawable();
        if (trashDrawable != null) {
            trashDrawable.setPlayInDirectionOfCustomEndFrame(true);
            trashDrawable.setAutoRepeat(0);
            trashDrawable.setCustomEndFrame(0);
        }
        trashPanelIcon.setScaleType(ImageView.ScaleType.CENTER);
        trashPanelIcon.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        trashPanelIcon.setBackground(withShadow(Theme.createRoundRectDrawable(dp(22), getThemedColor(Theme.key_windowBackgroundWhite))));
        trashPanel.addView(trashPanelIcon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        final FrameLayout formattingStylesContainer = new FrameLayout(context);
        formattingStylesContainer.setBackground(withShadow(Theme.createRoundRectDrawable(dp(22), getThemedColor(Theme.key_windowBackgroundWhite))));
        formattingPanel.addView(formattingStylesContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 44));

        formattingScrollView = new HorizontalScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                final int mode = MeasureSpec.getMode(widthMeasureSpec);
                if (mode == MeasureSpec.EXACTLY) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    return;
                }
                super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.UNSPECIFIED), heightMeasureSpec);
                final int content = getMeasuredWidth();
                int cap = formattingScrollMaxWidth;
                if (mode == MeasureSpec.AT_MOST) {
                    cap = Math.min(cap, MeasureSpec.getSize(widthMeasureSpec));
                }
                setMeasuredDimension(Math.min(content, cap), getMeasuredHeight());
            }
            private final GradientClip clip = new GradientClip();
            private final AnimatedFloat leftGradientAlpha = new AnimatedFloat(this, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
            private final AnimatedFloat rightGradientAlpha = new AnimatedFloat(this, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                final float left = leftGradientAlpha.set(canScrollHorizontally(-1));
                final float right = rightGradientAlpha.set(canScrollHorizontally(1));
                if (left > 0 || right > 0) {
                    canvas.saveLayerAlpha(getScrollX(), 0, getScrollX() + getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
                }
                super.dispatchDraw(canvas);
                if (left > 0 || right > 0) {
                    canvas.save();
                    if (left > 0) {
                        AndroidUtilities.rectTmp.set(getScrollX(), 0, getScrollX() + dp(48), getHeight());
                        clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.LEFT, left);
                    }
                    if (right > 0) {
                        AndroidUtilities.rectTmp.set(getScrollX() + getWidth() - dp(48), 0, getScrollX() + getWidth(), getHeight());
                        clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.RIGHT, right);
                    }
                    canvas.restore();
                }
            }
        };
        formattingScrollView.setHorizontalScrollBarEnabled(false);
        formattingScrollView.setClipToOutline(true);
        formattingScrollView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(22));
            }
        });
        formattingStylesContainer.addView(formattingScrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        formattingPanelLayout = new LinearLayout(context);
        formattingPanelLayout.setOrientation(LinearLayout.HORIZONTAL);
        formattingPanelLayout.setPadding(dp(2), 0, dp(2), 0);
        formattingScrollView.addView(formattingPanelLayout, new FrameLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        addFormattingButton(context, R.drawable.formatting_bold, RichTextStyle.BOLD);
        addFormattingButton(context, R.drawable.formatting_italic, RichTextStyle.ITALIC);
        addFormattingButton(context, R.drawable.formatting_underline, RichTextStyle.UNDERLINE);
        addFormattingButton(context, R.drawable.formatting_strikethrough, RichTextStyle.STRIKE);
        addFormattingButton(context, R.drawable.formatting_spoiler, RichTextStyle.SPOILER);
        addFormattingButton(context, R.drawable.iv_code, RichTextStyle.MONO);
        addFormattingButton(context, R.drawable.formatting_marked, RichTextStyle.MARKED, true);
        addFormattingButton(context, R.drawable.iv_sub, RichTextStyle.SUBSCRIPT, true);
        addFormattingButton(context, R.drawable.iv_super, RichTextStyle.SUPERSCRIPT, true);

        quoteButton = new Button(context, R.drawable.iv_quote, getResourceProvider());
        quoteButton.setContentDescription(getString(R.string.Quote));
        quoteButton.setOnClickListener(v -> { listView.toggleQuoteOnSelection(); updateFormattingButtons(); });
        formattingPanelLayout.addView(quoteButton, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL, formattingPanelLayout.getChildCount() > 0 ? 2 : 0, 0, 0, 0));

        formattingLayout2 = new LinearLayout(context);
        formattingLayout2.setOrientation(LinearLayout.HORIZONTAL);
        formattingLayout2.setPadding(dp(2), 0, dp(2), 0);
        formattingLayout2.setBackground(withShadow(Theme.createRoundRectDrawable(dp(22), getThemedColor(Theme.key_windowBackgroundWhite))));
        formattingPanel.addView(formattingLayout2, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 44, Gravity.BOTTOM, 8, 0, 0, 0));

        linkButton = new Button(context, R.drawable.media_link_24, getResourceProvider());
        linkButton.setContentDescription(getString(R.string.CreateLink));
        linkButton.setOnClickListener(v -> listView.onLinkClicked());
        formattingLayout2.addView(linkButton, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL));
        dateButton = new Button(context, R.drawable.msg_calendar2, getResourceProvider());
        dateButton.setContentDescription(getString(R.string.AccDescrIVInsertDate));
        dateButton.setOnClickListener(v -> listView.onDateClicked());
        formattingLayout2.addView(dateButton, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL));

        formattingLayout3 = new LinearLayout(context);
        formattingLayout3.setOrientation(LinearLayout.HORIZONTAL);
        formattingLayout3.setPadding(dp(2), 0, dp(2), 0);
        formattingLayout3.setBackground(withShadow(Theme.createRoundRectDrawable(dp(22), getThemedColor(Theme.key_windowBackgroundWhite))));
        formattingPanel.addView(formattingLayout3, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 44, Gravity.BOTTOM, 8, 0, 0, 0));

        mathButton = new Button(context, R.drawable.iv_math, getResourceProvider());
        mathButton.setPremium();
        premiumButtons.add(mathButton);
        mathButton.setContentDescription(getString(R.string.AccDescrIVFormula));
        mathButton.setOnClickListener(v -> listView.onMathClicked());
        formattingLayout3.addView(mathButton, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL));

        formattingLayout1 = new LinearLayout(context);
        formattingLayout1.setOrientation(LinearLayout.HORIZONTAL);
        formattingLayout1.setPadding(dp(2), 0, dp(2), 0);
        formattingLayout1.setBackground(withShadow(Theme.createRoundRectDrawable(dp(22), getThemedColor(Theme.key_windowBackgroundWhite))));
        formattingPanel.addView(formattingLayout1, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 44, Gravity.BOTTOM, 0, 0, 8, 0));

        aiStyleButton = new Button(context, 0, getResourceProvider());
        aiStyleButton.setImageDrawable(new AiButtonDrawable(context));
        aiStyleButton.setContentDescription(getString(R.string.AIEditor));
        aiStyleButton.setOnClickListener(v -> onAiStyleSelection());
        formattingLayout1.addView(aiStyleButton, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL));

        final int sendIcon = editingMessageObject != null ? R.drawable.input_done : (isInScheduleMode() ? R.drawable.input_schedule : R.drawable.send_plane_24);
        sendButton = new ChatActivityEnterView.SendButton(context, sendIcon, getResourceProvider(), true) {
            @Override
            public boolean isOpen() {
                return sendButtonLoading || super.isOpen();
            }
            @Override
            public boolean isInScheduleMode() {
                return RichEditor.this.isInScheduleMode();
            }
        };
        sendButton.setBackground(withShadow(Theme.createRoundRectDrawable(dp(22), getThemedColor(Theme.key_chat_messagePanelSend))));
        ScaleStateListAnimator.apply(sendButton);
        bottomPanel.addView(sendButton, LayoutHelper.createLinear(44, 44, 0, Gravity.RIGHT, 8, 0, 0, 0));
        sendButton.setContentDescription(getString(R.string.Send));
        sendButton.setOnClickListener(v -> sendMessage());
        sendButton.setOnLongClickListener(this::onSendLongClick);
        updateSendButtonLock();

        container.setPadding(0, AndroidUtilities.statusBarHeight, 0, 0);
        checkUI_listViewPadding();
        updateBottomPanel(BOTTOM_PANEL_TOOLBAR, false);

        updateHistoryButtons();

        container.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> updateBlockButtons());

        updatePremiumButtons();

        if (convertToSimpleOnOpen) {
            listView.convertToSimple();
            convertToSimpleOnOpen = false;
        }

        return fragmentView = container;
    }

    private static final int BOTTOM_PANEL_TOOLBAR = 0;
    private static final int BOTTOM_PANEL_FORMATTING = 1;
    private static final int BOTTOM_PANEL_TRASH = 2;

    private int reorderSavedPanelType = BOTTOM_PANEL_TOOLBAR;
    private boolean trashHovered;

    private boolean isOverTrash(float screenY) {
        if (trashPanel == null) return false;
        final int[] loc = new int[2];
        trashPanel.getLocationOnScreen(loc);
        return screenY >= loc[1];
    }

    private void setTrashHovered(boolean hovered, boolean animated) {
        if (trashHovered == hovered && animated) return;
        trashHovered = hovered;
        final float scale = hovered ? 1.15f : 1.0f;
        if (animated) {
            trashPanelIcon.animate()
                .scaleX(scale).scaleY(scale)
                .setDuration(180)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .start();
        } else {
            trashPanelIcon.animate().cancel();
            trashPanelIcon.setScaleX(scale);
            trashPanelIcon.setScaleY(scale);
        }
        trashPanelIcon.setColorFilter(new PorterDuffColorFilter(getThemedColor(hovered ? Theme.key_text_RedBold : Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        final RLottieDrawable d = trashPanelIcon.getAnimatedDrawable();
        if (d != null) {
            if (hovered) {
                if (d.getCurrentFrame() > 34) d.setCurrentFrame(0, false);
                d.setCustomEndFrame(33);
            } else {
                d.setCustomEndFrame(0);
            }
            d.start();
        }
    }

    private int bottomPanelType = -1;
    private void updateBottomPanel(int type, boolean animated) {
        if (bottomPanelType == type) return;
        bottomPanelType = type;
        if (animated) {
            bottomPanel.setVisibility(View.VISIBLE);
            bottomPanel.animate()
                .alpha(bottomPanelType == BOTTOM_PANEL_TOOLBAR ? 1.0f : 0.0f)
                .scaleX(bottomPanelType == BOTTOM_PANEL_TOOLBAR ? 1.0f : 0.8f)
                .scaleY(bottomPanelType == BOTTOM_PANEL_TOOLBAR ? 1.0f : 0.8f)
                .translationY(bottomPanelType == BOTTOM_PANEL_TOOLBAR ? 0 : dp(30))
                .setDuration(420)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .withEndAction(() -> {
                    if (bottomPanelType != BOTTOM_PANEL_TOOLBAR) bottomPanel.setVisibility(View.GONE);
                })
                .start();
            formattingPanel.setVisibility(View.VISIBLE);
            formattingPanel.animate()
                .alpha(bottomPanelType == BOTTOM_PANEL_FORMATTING ? 1.0f : 0.0f)
                .scaleX(bottomPanelType == BOTTOM_PANEL_FORMATTING ? 1.0f : 0.8f)
                .scaleY(bottomPanelType == BOTTOM_PANEL_FORMATTING ? 1.0f : 0.8f)
                .translationY(bottomPanelType == BOTTOM_PANEL_FORMATTING ? 0 : dp(30))
                .setDuration(420)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .withEndAction(() -> {
                    if (bottomPanelType != BOTTOM_PANEL_FORMATTING) formattingPanel.setVisibility(View.GONE);
                })
                .start();
            trashPanel.setVisibility(View.VISIBLE);
            trashPanel.animate()
                .alpha(bottomPanelType == BOTTOM_PANEL_TRASH ? 1.0f : 0.0f)
                .scaleX(bottomPanelType == BOTTOM_PANEL_TRASH ? 1.0f : 0.8f)
                .scaleY(bottomPanelType == BOTTOM_PANEL_TRASH ? 1.0f : 0.8f)
                .setDuration(420)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .withEndAction(() -> {
                    if (bottomPanelType != BOTTOM_PANEL_TRASH) trashPanel.setVisibility(View.GONE);
                })
                .start();
        } else {
            bottomPanel.setVisibility(type == BOTTOM_PANEL_TOOLBAR ? View.VISIBLE : View.GONE);
            bottomPanel.setAlpha(type == BOTTOM_PANEL_TOOLBAR ? 1.0f : 0.0f);
            bottomPanel.setScaleX(type == BOTTOM_PANEL_TOOLBAR ? 1.0f : 0.8f);
            bottomPanel.setScaleY(type == BOTTOM_PANEL_TOOLBAR ? 1.0f : 0.8f);
            bottomPanel.setTranslationY(type == BOTTOM_PANEL_TOOLBAR ? 0 : dp(30));
            formattingPanel.setVisibility(type == BOTTOM_PANEL_FORMATTING ? View.VISIBLE : View.GONE);
            formattingPanel.setAlpha(type == BOTTOM_PANEL_FORMATTING ? 1.0f : 0.0f);
            formattingPanel.setScaleX(type == BOTTOM_PANEL_FORMATTING ? 1.0f : 0.8f);
            formattingPanel.setScaleY(type == BOTTOM_PANEL_FORMATTING ? 1.0f : 0.8f);
            formattingPanel.setTranslationY(type == BOTTOM_PANEL_FORMATTING ? 0 : dp(30));
            trashPanel.setVisibility(type == BOTTOM_PANEL_TRASH ? View.VISIBLE : View.GONE);
            trashPanel.setAlpha(type == BOTTOM_PANEL_TRASH ? 1.0f : 0.0f);
            trashPanel.setScaleX(type == BOTTOM_PANEL_TRASH ? 1.0f : 0.8f);
            trashPanel.setScaleY(type == BOTTOM_PANEL_TRASH ? 1.0f : 0.8f);
        }
    }

    public static class DraggingDrawable extends Drawable {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final AnimatedFloat animatedDragging = new AnimatedFloat(this::invalidateSelf, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);

        private boolean dragging;

        public DraggingDrawable(int color) {
            setColor(color);
        }

        public void setColor(int color) {
            paint.setColor(color);
        }

        public void setDragging(boolean dragging) {
            if (this.dragging == dragging) return;
            this.dragging = dragging;
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final float dragging = animatedDragging.set(this.dragging);
            if (dragging <= 0) return;
            paint.setAlpha((int) (alpha * dragging));
            paint.setShadowLayer(dragging * dp(12), 0, dp(3), Theme.multAlpha(0x30000000, dragging));
            final Rect bounds = getBounds();
            final float horizPadding = dp(8) * dragging;
            final float vertPadding = dp(0) * dragging;
            final float radius = dp(12) * dragging;
            canvas.drawRoundRect(
                bounds.left + horizPadding,
                bounds.top + vertPadding,
                bounds.right - horizPadding,
                bounds.bottom - vertPadding + dp(6) * dragging,
                radius, radius,
                paint
            );
        }

        private int alpha = 0xFF;
        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }
        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }

    private Button addBlockButton(int icon, int flag) {
        return addBlockButton(icon, flag, false);
    }

    private Button addBlockButton(int icon, int flag, boolean premium) {
        final Button button = new Button(blocksLayout.getContext(), icon, getResourceProvider());
        if (premium) {
            button.setPremium();
            premiumButtons.add(button);
        }
        button.setTag(flag);
        button.setContentDescription(blockButtonContentDescription(flag));
        blockButtons.add(button);
        blocksLayout.addView(button, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL, blocksLayout.getChildCount() == 0 ? 0 : 2, 0, 0, 0));
        return button;
    }

    static String blockButtonContentDescription(int flag) {
        switch (flag) {
            case 1: return getString(R.string.AccDescrIVTextStyle);
            case 2: return getString(R.string.AccDescrIVListStyle);
            case 4: return getString(R.string.AccDescrIVTable);
            case 7: return getString(R.string.AccDescrIVFormula);
            case 9: return getString(R.string.AccDescrIVDetails);
            default: return null;
        }
    }

    private void updateBlockButtons() {
        final BlockRow row;
        final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = listView.getTextSelectionHelper();
        if (textSelectionHelper != null && textSelectionHelper.isInSelectionMode()) {
            final int sCell = textSelectionHelper.getStartCell();
            final int eCell = textSelectionHelper.getEndCell();
            if (sCell == eCell) {
                row = listView.rowForCell(sCell);
            } else {
                row = null;
            }
        } else {
            row = listView.findFocusedRow();
        }
        int type;
        if (listView.findFocusedTableCell() != null) {
            type = 4;
        } else if (row == null) {
            type = 0;
        } else if (row.isChecklist() || row.isInList() || row.isOrdered() || row.block instanceof TL_iv.pageBlockDetails) {
            type = 2;
        } else if (row.block instanceof TL_iv.pageBlockTable) {
            type = 4;
        } else if (row.block instanceof TL_iv.pageBlockDivider) {
            type = 8;
        } else if (
            row.block instanceof TL_iv.pageBlockHeading1 ||
            row.block instanceof TL_iv.pageBlockHeading2 ||
            row.block instanceof TL_iv.pageBlockHeading3 ||
            row.block instanceof TL_iv.pageBlockHeading4 ||
            row.block instanceof TL_iv.pageBlockHeading5 ||
            row.block instanceof TL_iv.pageBlockHeading6 ||
            row.block instanceof TL_iv.pageBlockParagraph ||
            row.block instanceof TL_iv.pageBlockPreformatted ||
            row.block instanceof TL_iv.pageBlockBlockquote ||
            row.block instanceof TL_iv.pageBlockPullquote ||
            row.block instanceof TL_iv.pageBlockFooter
        ) {
            type = 1;
        } else if (
            row.block instanceof TL_iv.pageBlockPhoto ||
            row.block instanceof TL_iv.pageBlockVideo ||
            row.block instanceof TL_iv.pageBlockCollage ||
            row.block instanceof TL_iv.pageBlockSlideshow
        ) {
            type = 3;
        } else if (row.block instanceof TL_iv.pageBlockAudio) {
            type = 5;
        } else if (row.block instanceof TL_iv.pageBlockMap) {
            type = 6;
        } else if (row.block instanceof TL_iv.pageBlockMath) {
            type = 7;
        } else {
            type = 0;
        }
        for (final Button button : blockButtons) {
            final int flag = (Integer) button.getTag();
            button.setSelected(type == flag);
            if (type == flag) {
                button.setEnabled(true);
                if (row == null) {
                    button.resetIcon();
                } else if (type == 1) {
                    if (row.block instanceof TL_iv.pageBlockHeading1) button.updateIcon(R.drawable.iv_h1);
                    else if (row.block instanceof TL_iv.pageBlockHeading2) button.updateIcon(R.drawable.iv_h2);
                    else if (row.block instanceof TL_iv.pageBlockHeading3) button.updateIcon(R.drawable.iv_h3);
                    else if (row.block instanceof TL_iv.pageBlockHeading4) button.updateIcon(R.drawable.iv_h4);
                    else if (row.block instanceof TL_iv.pageBlockHeading5) button.updateIcon(R.drawable.iv_h5);
                    else if (row.block instanceof TL_iv.pageBlockHeading6) button.updateIcon(R.drawable.iv_h6);
                    else if (row.block instanceof TL_iv.pageBlockPreformatted) button.updateIcon(R.drawable.iv_code);
                    else if (row.block instanceof TL_iv.pageBlockBlockquote) button.updateIcon(R.drawable.iv_quote);
                    else if (row.block instanceof TL_iv.pageBlockPullquote) button.updateIcon(R.drawable.iv_pullquote);
                    else if (row.block instanceof TL_iv.pageBlockFooter) button.updateIcon(R.drawable.iv_footer);
                    else button.resetIcon();
                } else if (type == 2) {
                    if (row.isChecklist()) button.updateIcon(R.drawable.iv_todo);
                    else if (row.isOrdered()) button.updateIcon(R.drawable.iv_ordered_list);
                    else button.resetIcon();
                } else if (type == 8) {
                    button.updateIcon(R.drawable.iv_details);
                } else {
                    button.resetIcon();
                }
            } else {
                button.setEnabled(type != 4);
                button.resetIcon();
            }
        }
    }

    private void addFormattingButton(Context context, int icon, int styleFlag) {
        addFormattingButton(context, icon, styleFlag, false);
    }

    private void addFormattingButton(Context context, int icon, int styleFlag, boolean premium) {
        final Button button = new Button(context, icon, getResourceProvider());
        if (premium) {
            button.setPremium();
            premiumButtons.add(button);
        }
        button.setTag(styleFlag);
        button.setContentDescription(formattingButtonContentDescription(styleFlag));
        button.setOnClickListener(v -> listView.onFormattingClicked(styleFlag));
        formattingButtons.add(button);
        formattingPanelLayout.addView(button, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL, formattingPanelLayout.getChildCount() > 0 ? 2 : 0, 0, 0, 0));
    }

    static String formattingButtonContentDescription(int styleFlag) {
        if (styleFlag == RichTextStyle.BOLD) return getString(R.string.Bold);
        if (styleFlag == RichTextStyle.ITALIC) return getString(R.string.Italic);
        if (styleFlag == RichTextStyle.UNDERLINE) return getString(R.string.Underline);
        if (styleFlag == RichTextStyle.STRIKE) return getString(R.string.Strike);
        if (styleFlag == RichTextStyle.SPOILER) return getString(R.string.Spoiler);
        if (styleFlag == RichTextStyle.MONO) return getString(R.string.Mono);
        if (styleFlag == RichTextStyle.MARKED) return getString(R.string.Highlight);
        if (styleFlag == RichTextStyle.SUBSCRIPT) return getString(R.string.Subscript);
        if (styleFlag == RichTextStyle.SUPERSCRIPT) return getString(R.string.Superscript);
        return null;
    }

    private void updateFormattingButtons() {
        final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = listView.getTextSelectionHelper();
        if (formattingButtons.isEmpty() || textSelectionHelper == null || !textSelectionHelper.isInSelectionMode()) {
            return;
        }
        if (quoteButton != null) quoteButton.setSelected(listView.isSelectionQuoted());
        if (listView.isTableSelection()) { updateFormattingButtonsTable(); return; }
        if (listView.isCaptionSelection()) { updateFormattingButtonsCaption(); return; }
        final int sCell = textSelectionHelper.getStartCell();
        final int eCell = textSelectionHelper.getEndCell();
        final int sOff = textSelectionHelper.getStartOffset();
        final int eOff = textSelectionHelper.getEndOffset();
        final boolean valid = sCell >= 0 && eCell >= 0 && eCell >= sCell && eCell < listView.itemRows.size();
        for (final Button button : formattingButtons) {
            final int styleFlag = (Integer) button.getTag();
            button.setSelected(valid && listView.isStyleFullyApplied(styleFlag, sCell, sOff, eCell, eOff));
        }
        setBoldEnabled(!listView.isSelectionAllHeadings());
        if (linkButton != null) {
            linkButton.setSelected(valid && listView.isLinkApplied(sCell, sOff, eCell, eOff));
        }
        if (dateButton != null) {
            dateButton.setSelected(valid && listView.isDateApplied(sCell, sOff, eCell, eOff));
        }
        setInlineButtonsEnabled(valid && sCell == eCell);
    }

    private void setBoldEnabled(boolean enabled) {
        for (final Button button : formattingButtons) {
            final int styleFlag = (Integer) button.getTag();
            if (styleFlag == RichTextStyle.BOLD) {
                button.setEnabled(enabled);
            }
        }
    }

    private void setInlineButtonsEnabled(boolean enabled) {
        if (linkButton != null) linkButton.setEnabled(enabled);
        if (dateButton != null) dateButton.setEnabled(enabled);
        if (mathButton != null) mathButton.setEnabled(enabled);
    }

    private void onAiStyleSelection() {
        final RichEditorListView.SelectionEdit edit = listView.beginSelectionEdit();
        if (edit == null) return;
        final TL_iv.RichMessage rich = edit.extractRichMessage();
        if (rich == null || rich.blocks.isEmpty()) return;
        new AIEditorAlert(getContext(), getResourceProvider())
            .setText(rich)
            .setOnUseRich(edit::replaceWith)
            .show();
    }

    private void updateFormattingButtonsTable() {
        final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = listView.getTextSelectionHelper();
        final int pos = textSelectionHelper.getStartCell();
        final int sChild = textSelectionHelper.getStartChildPosition();
        final int eChild = textSelectionHelper.getEndChildPosition();
        final int sOff = textSelectionHelper.getStartOffset();
        final int eOff = textSelectionHelper.getEndOffset();
        for (final Button button : formattingButtons) {
            final int styleFlag = (Integer) button.getTag();
            button.setSelected(listView.isStyleFullyAppliedTable(styleFlag, pos, sChild, sOff, eChild, eOff));
        }
        final boolean single = sChild == eChild;
        final RichEditText et = single ? listView.tableEditText(pos, sChild) : null;
        final int from = Math.max(0, Math.min(sOff, eOff));
        final int to = et == null ? 0 : Math.max(0, Math.min(Math.max(sOff, eOff), et.length()));
        if (linkButton != null) {
            linkButton.setSelected(et != null && from < to && RichTextStyle.hasLink(et.getText(), from, to));
        }
        if (dateButton != null) {
            dateButton.setSelected(et != null && from < to && RichTextStyle.hasDate(et.getText(), from, to));
        }
        setBoldEnabled(true);
        setInlineButtonsEnabled(single);
    }

    private void updateFormattingButtonsCaption() {
        final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = listView.getTextSelectionHelper();
        final int pos = textSelectionHelper.getStartCell();
        final RichEditText et = listView.captionEditText(pos);
        final int sOff = textSelectionHelper.getStartOffset();
        final int eOff = textSelectionHelper.getEndOffset();
        final int from = et == null ? 0 : Math.max(0, Math.min(Math.min(sOff, eOff), et.length()));
        final int to = et == null ? 0 : Math.max(0, Math.min(Math.max(sOff, eOff), et.length()));
        for (final Button button : formattingButtons) {
            final int styleFlag = (Integer) button.getTag();
            button.setSelected(et != null && from < to && (et.getCurrentStyle(from, to) & styleFlag) != 0);
        }
        if (linkButton != null) {
            linkButton.setSelected(et != null && from < to && RichTextStyle.hasLink(et.getText(), from, to));
        }
        if (dateButton != null) {
            dateButton.setSelected(et != null && from < to && RichTextStyle.hasDate(et.getText(), from, to));
        }
        setBoldEnabled(true);
        setInlineButtonsEnabled(true);
    }

    public static class Button extends ImageView implements Theme.Colorable {

        private int startIcon, currentIcon;
        private boolean premium;
        private boolean premiumLocked;
        private int roundRadius = 20;
        private int backgroundColorKey = Theme.key_windowBackgroundWhite;
        private Theme.ResourcesProvider resourcesProvider;
        private boolean enabled = true;
        public Button(Context context, int icon, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            startIcon = currentIcon = icon;
            this.resourcesProvider = resourcesProvider;

            if (icon != 0) {
                setImageResource(icon);
            }
            setScaleType(ScaleType.CENTER);
            ScaleStateListAnimator.apply(this);

            updateColors();
        }

        public Button setPremium() {
            premium = true;
            setImageDrawable(wrapPremium(currentIcon));
            return this;
        }

        public void setPremiumLocked(boolean locked) {
            premiumLocked = locked;
            if (getDrawable() instanceof RequiresPremiumDrawable) {
                ((RequiresPremiumDrawable) getDrawable()).setPremium(locked);
            }
        }

        private RequiresPremiumDrawable wrapPremium(int icon) {
            return new RequiresPremiumDrawable(getContext(), icon)
                .setCutoutColorKey(backgroundColorKey)
                .setPremium(premiumLocked);
        }

        private boolean selected;
        public void setSelected(boolean selected) {
            if (this.selected == selected) return;
            this.selected = selected;
            updateColors();
        }

        private boolean accent = true;
        public Button setAccent(boolean accent) {
            if (this.accent == accent) return this;
            this.accent = accent;
            updateColors();
            return this;
        }

        public void setEnabled(boolean enabled) {
            if (this.enabled == enabled) return;
            setClickable(enabled);
            animate()
                .alpha((this.enabled = enabled) ? 1.0f : 0.5f)
                .setDuration(320)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .start();
        }

        public void updateIcon(int newIcon) {
            if (currentIcon == newIcon) return;
            currentIcon = newIcon;
            if (premium) {
                AndroidUtilities.updateImageViewImageAnimated(this, wrapPremium(newIcon));
            } else {
                AndroidUtilities.updateImageViewImageAnimated(this, newIcon);
            }
        }

        public void resetIcon() {
            updateIcon(startIcon);
        }

        public Button setRoundRadius(int roundRadius) {
            this.roundRadius = roundRadius;
            updateColors();
            return this;
        }

        public Button setBackgroundColorKey(int key) {
            if (this.backgroundColorKey == key) return this;
            this.backgroundColorKey = key;
            updateColors();
            return this;
        }

        @Override
        public void updateColors() {
            if (selected) {
                final int color = Theme.getColor(accent ? Theme.key_featuredStickers_addButton : Theme.key_windowBackgroundWhiteBlackText);
                setBackground(Theme.createRadSelectorDrawable(Theme.blendOver(Theme.getColor(backgroundColorKey, resourcesProvider), Theme.multAlpha(color, .10f)), Theme.multAlpha(color, .10f), dp(roundRadius), dp(roundRadius)));
                setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            } else {
                setBackground(Theme.createRadSelectorDrawable(Theme.getColor(backgroundColorKey, resourcesProvider), Theme.getColor(Theme.key_listSelector, resourcesProvider), dp(roundRadius), dp(roundRadius)));
                setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
            }
        }
    }

    public static Drawable withShadow(Drawable inner) {
        return new ShadowWrapperDrawable(inner);
    }

    public static class RequiresPremiumDrawable extends Drawable {

        private final Context context;
        public final Drawable icon;
        private Drawable premiumIconCutout;
        private int premiumIconCutoutColorKey = Theme.key_windowBackgroundWhite;
        private int premiumIconCutoutColor;
        private Drawable premiumIcon;
        public boolean showPremiumIcon = true;

        public RequiresPremiumDrawable(Context context, int iconResId) {
            this(context, context.getResources().getDrawable(iconResId).mutate());
        }

        public RequiresPremiumDrawable(Context context, Drawable icon) {
            this.context = context;
            this.icon = icon;
        }

        public RequiresPremiumDrawable setPremium(boolean premium) {
            if (showPremiumIcon == premium) return this;
            showPremiumIcon = premium;
            invalidateSelf();
            return this;
        }

        public RequiresPremiumDrawable setCutoutColorKey(int colorKey) {
            premiumIconCutoutColorKey = colorKey;
            return this;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final Rect bounds = getBounds();
            final int cx = bounds.centerX(), cy = bounds.centerY();
            icon.setBounds(
                cx - icon.getIntrinsicWidth() / 2,
                cy - icon.getIntrinsicHeight() / 2,
                cx + icon.getIntrinsicWidth() / 2,
                cy + icon.getIntrinsicHeight() / 2
            );
            icon.draw(canvas);

            if (showPremiumIcon) {
                final int pcx = cx + dp(9), pcy = cy + dp(9);
                final int cutoutColor = Theme.getColor(premiumIconCutoutColorKey);
                if (premiumIconCutout == null) {
                    premiumIconCutout = context.getResources().getDrawable(R.drawable.star_premium_cutout).mutate();
                    premiumIconCutout.setColorFilter(new PorterDuffColorFilter(premiumIconCutoutColor = cutoutColor, PorterDuff.Mode.SRC_IN));
                }
                if (cutoutColor != premiumIconCutoutColor) {
                    premiumIconCutout.setColorFilter(new PorterDuffColorFilter(premiumIconCutoutColor = cutoutColor, PorterDuff.Mode.SRC_IN));
                }
                if (premiumIcon == null) {
                    premiumIcon = context.getResources().getDrawable(R.drawable.star_premium).mutate();
                }
                premiumIconCutout.setBounds(pcx - dp(9), pcy - dp(9), pcx + dp(9), pcy + dp(9));
                premiumIconCutout.draw(canvas);
                premiumIcon.setBounds(pcx - dp(9), pcy - dp(9), pcx + dp(9), pcy + dp(9));
                premiumIcon.draw(canvas);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            icon.setAlpha(alpha);
        }
        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            icon.setColorFilter(colorFilter);
        }
        @Override
        public int getOpacity() {
            return icon.getOpacity();
        }
        @Override
        public int getIntrinsicHeight() {
            return Math.max(dp(38), icon.getIntrinsicHeight());
        }
        @Override
        public int getIntrinsicWidth() {
            return Math.max(dp(38), icon.getIntrinsicWidth());
        }
    }

    private static class ShadowWrapperDrawable extends Drawable implements Drawable.Callback {
        private final Drawable inner;
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shadowPath = new Path();
        private final RectF rectF = new RectF();
        private final Outline outline = new Outline();
        private boolean pathDirty = true;

        ShadowWrapperDrawable(Drawable inner) {
            this.inner = inner;
            inner.setCallback(this);
            shadowPaint.setColor(0x00000000);
            if (Theme.isCurrentThemeDark()) {
                shadowPaint.setShadowLayer(dp(12), 0, dp(4), Theme.multAlpha(0xFF000000, .30f));
            } else {
                shadowPaint.setShadowLayer(dp(12), 0, dp(4), Theme.multAlpha(0xFF000000, .10f));
            }
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            inner.setBounds(bounds);
            pathDirty = true;
        }

        @Override
        public boolean isStateful() {
            return inner.isStateful();
        }

        @Override
        protected boolean onStateChange(int[] state) {
            return inner.setState(state);
        }

        @Override
        public void jumpToCurrentState() {
            inner.jumpToCurrentState();
        }

        @Override
        public void setHotspot(float x, float y) {
            inner.setHotspot(x, y);
        }

        @Override
        public void setHotspotBounds(int left, int top, int right, int bottom) {
            inner.setHotspotBounds(left, top, right, bottom);
        }

        @Override
        public void invalidateDrawable(@NonNull Drawable who) {
            invalidateSelf();
        }

        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            scheduleSelf(what, when);
        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            unscheduleSelf(what);
        }

        private void rebuildPath() {
            shadowPath.reset();
            rectF.set(getBounds());
            float radius = -1f;
            if (Build.VERSION.SDK_INT >= 24) {
                try {
                    inner.getOutline(outline);
                    radius = outline.getRadius();
                } catch (Throwable ignore) {}
            }
            if (radius > 0f) {
                shadowPath.addRoundRect(rectF, radius, radius, Path.Direction.CW);
            } else {
                shadowPath.addRect(rectF, Path.Direction.CW);
            }
            pathDirty = false;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (pathDirty) rebuildPath();
            canvas.drawPath(shadowPath, shadowPaint);
            inner.draw(canvas);
        }

        @Override
        public void setAlpha(int alpha) {
            inner.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            inner.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private void updateHistoryButtons() {
        boolean canUndo = listView.canUndo();
        boolean canRedo = listView.canRedo();
        if (undoButton != null) {
            undoButton.setEnabled(canUndo);
            undoButton.setAlpha(canUndo ? 1f : 0.35f);
        }
        if (redoButton != null) {
            redoButton.setEnabled(canRedo);
            redoButton.setAlpha(canRedo ? 1f : 0.35f);
        }
    }

    private static final int DEFAULT_ATTACH_LAYOUTS =
        (1 << ChatAttachAlert.LAYOUT_TYPE_PHOTO) |
        (1 << ChatAttachAlert.LAYOUT_TYPE_MUSIC) |
        (1 << ChatAttachAlert.LAYOUT_TYPE_LOCATION);

    private void openAttach() {
        openAttach(DEFAULT_ATTACH_LAYOUTS, 0);
    }
    private void openAttach(int allowedLayouts, int initialLayoutType) {
        listView.pendingInsertRow = listView.findFocusedRow();
        ChatAttachAlert chatAttachAlert = new ChatAttachAlert(getContext(), this, false, false, true, getResourceProvider());
        chatAttachAlert.setDelegate(new ChatAttachAlert.ChatAttachViewDelegate() {
            @Override
            public void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long effectId, boolean invertMedia, boolean forceDocument, long payStars) {
                if (button == 7 || button == 8) {
                    HashMap<Object, Object> photos = chatAttachAlert.getPhotoLayout().getSelectedPhotos();
                    ArrayList<Object> order = chatAttachAlert.getPhotoLayout().getSelectedPhotosOrder();
                    final BlockRow target = listView.pendingMediaRow;
                    listView.pendingMediaRow = null;
                    for (int a = 0; a < order.size(); a++) {
                        Object object = photos.get(order.get(a));
                        if (object instanceof MediaController.PhotoEntry) {
                            if (target != null) {
                                listView.addMediaToRow(target, (MediaController.PhotoEntry) object);
                            } else {
                                listView.attachMedia((MediaController.PhotoEntry) object);
                            }
                            break;
                        }
                    }
                }
                listView.pendingMediaRow = null;
                chatAttachAlert.dismiss(true);
            }
            @Override
            public void didSelectBot(TLRPC.User user) {}
            @Override
            public void onCameraOpened() {}
            @Override
            public boolean needEnterComment() {
                return false;
            }
            @Override
            public void doOnIdle(Runnable runnable) {
                NotificationCenter.getInstance(getCurrentAccount()).doOnIdle(runnable);
            }
        });

        chatAttachAlert.getPhotoLayout().loadGalleryPhotos();

        chatAttachAlert.setMaxSelectedPhotos(1, true);
        chatAttachAlert.enablePollAttachMode(allowedLayouts);
        chatAttachAlert.setLocationActivityDelegate((location, live, notify, scheduleDate, payStars) -> {
            if (location == null || location.geo == null) {
                chatAttachAlert.dismiss(true);
                return;
            }
            TL_iv.pageBlockMap map = new TL_iv.pageBlockMap();
            map.geo = location.geo;
            map.zoom = 15;
            map.w = 600;
            map.h = 400;
            listView.addBlock(map);
            chatAttachAlert.dismiss(true);
        });
        chatAttachAlert.setAudioSelectDelegate((audios, caption, notify, scheduleDate, scheduleRepeatPeriod, effectId, invertMedia, payStars) -> {
            if (audios != null && !audios.isEmpty()) {
                listView.attachAudio(audios.get(0));
            }
            chatAttachAlert.dismiss(true);
        });
        chatAttachAlert.init();
        if (initialLayoutType != 0) {
            chatAttachAlert.openAttachLayoutForType(initialLayoutType);
        }
        chatAttachAlert.setFocusable(true);
        chatAttachAlert.show();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && (requestCode == 1 || requestCode == 14)) {
            if (data == null || data.getData() == null) return;
            listView.attachExternalMedia(data.getData());
            return;
        }
        super.onActivityResultFragment(requestCode, resultCode, data);
    }

    private boolean sendButtonLoading;

    private void updateSendButtonLoading() {
        if (sendButton == null) return;
        sendButtonLoading = listView.hasPendingUploads();
        sendButton.setLoading(sendButtonLoading, ChatActivityEnterView.SendButton.INFINITE_LOADING);
        sendButton.invalidate();
    }

    private final Runnable limitCheckRunnable = this::updateSendButtonEnabled;

    private void scheduleLimitCheck() {
        AndroidUtilities.cancelRunOnUIThread(limitCheckRunnable);
        AndroidUtilities.runOnUIThread(limitCheckRunnable, 1000);
    }

    private void updateSendButtonEnabled() {
        if (sendButton == null) return;
        final boolean enabled = listView.isWithinLimits();
        sendButton.setEnabled(enabled);
        sendButton.animate().alpha(enabled ? 1.0f : 0.5f).setDuration(150).start();
    }

    private boolean isSendLocked() {
        return !MessagesController.getInstance(currentAccount).richEditorAllowed()
            && !UserConfig.getInstance(currentAccount).isPremium()
            && listView.isLossy();
    }

    private void updateSendButtonLock() {
        if (sendButton == null) return;
        sendButton.setLocked(isSendLocked());
    }

    private void showConversionSheet() {
        openConversionSheet(getContext(), listView::convertToSimple, () -> {
            if (!UserConfig.getInstance(currentAccount).isPremium()) {
                showDialog(new PremiumFeatureBottomSheet(this, PremiumPreviewFragment.PREMIUM_FEATURE_RICH_EDITOR, true));
            }
        }, getResourceProvider());
    }

    private void openLocationPicker(BlockRow row) {
        if (row == null || !(row.block instanceof TL_iv.pageBlockMap)) return;
        if (!AndroidUtilities.isMapsInstalled(this)) return;
        final ChatAttachAlert pickerAlert = new ChatAttachAlert(getContext(), this, false, false, false, getResourceProvider());
        pickerAlert.setDelegate(new ChatAttachAlert.ChatAttachViewDelegate() {
            @Override
            public void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long effectId, boolean invertMedia, boolean forceDocument, long payStars) {}
        });
        pickerAlert.setLocationPicker();
        pickerAlert.setLocationActivityDelegate((location, live, notify, scheduleDate, payStars) -> {
            if (location == null || location.geo == null) return;
            if (listView.history != null) listView.history.flush();
            final TL_iv.pageBlockMap map = (TL_iv.pageBlockMap) row.block;
            map.geo = location.geo;
            map.zoom = 15;
            if (map.w <= 0 || map.h <= 0) {
                map.w = 600;
                map.h = 400;
            }
            if (listView.history != null) listView.history.record();
            pickerAlert.dismiss(true);
            listView.post(() -> {
                View v = listView.findViewByItemObject(row);
                if (v instanceof RichMapCell) {
                    ((RichMapCell) v).bind(row, listView.getMapDelegate());
                } else {
                    listView.adapter.update(false);
                }
            });
        });
        pickerAlert.init();
        pickerAlert.show();
    }

    private boolean isInScheduleMode() {
        return editingMessageObject == null && chatActivity != null && chatActivity.isInScheduleMode();
    }

    private void sendMessage() {
        if (isSendLocked()) {
            showConversionSheet();
            return;
        }
        if (isInScheduleMode()) {
            AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), chatActivity.getDialogId(), new AlertsCreator.ScheduleDatePickerDelegate() {
                @Override
                public void didSelectDate(boolean notify, int scheduleDate, int scheduleRepeatPeriod) {
                    sendMessage(notify, scheduleDate, scheduleRepeatPeriod);
                }
            }, getResourceProvider());
            return;
        }
        sendMessage(true, 0, 0);
    }

    private void sendMessage(boolean notify, int scheduleDate, int scheduleRepeatPeriod) {
        if (isSendLocked()) {
            showConversionSheet();
            return;
        }
        if (chatActivity == null) return;
        if (!listView.hasAnyText()) return;
        if (listView.hasPendingUploads()) return;
        if (!listView.isWithinLimits()) { updateSendButtonEnabled(); return; }
        if (!MessagesController.getInstance(currentAccount).richEditorAllowed()) {
            final ChatActivityEnterView enterView = chatActivity.getChatActivityEnterView();
            if (enterView == null) return;
            sent = true;
            if (onSentCallback != null) {
                onSentCallback.run();
            }
            enterView.sendConvertedRichAsSimple(listView.toSimpleMessage(), notify, scheduleDate, scheduleRepeatPeriod);
            finishFragment();
            return;
        }
        sent = true;
        ArrayList<TL_iv.PageBlock> sendBlocks = listView.flattenRowsToBlocks();
        if (sendBlocks.isEmpty()) return;
        ArrayList<TLRPC.Photo> sendPhotos = listView.collectPhotos();
        ArrayList<TLRPC.Document> sendDocs = listView.collectDocuments();
        final long dialogId = chatActivity.getDialogId();
        final MessageObject replyToMsg = chatActivity.getReplyMessage();
        final MessageObject replyToTopMsg = chatActivity.getThreadMessage();
        final long monoForumPeerId = chatActivity.getSendMonoForumPeerId();
        final String quickReplyShortcut = chatActivity.quickReplyShortcut;
        final int quickReplyShortcutId = chatActivity.getQuickReplyId();
        final MessageObject editing = editingMessageObject;
        final ArrayList<TL_iv.PageBlock> blocks = sendBlocks;
        final Runnable doSend = () -> {
            if (editing != null) {
                SendMessagesHelper.prepareEditingArticle(
                    AccountInstance.getInstance(currentAccount),
                    editing,
                    blocks,
                    sendPhotos,
                    sendDocs,
                    null,
                    false,
                    chatActivity
                );
            } else {
                SendMessagesHelper.prepareSendingArticle(
                    AccountInstance.getInstance(currentAccount),
                    blocks,
                    sendPhotos,
                    sendDocs,
                    null,
                    false,
                    dialogId,
                    replyToMsg,
                    replyToTopMsg,
                    notify,
                    scheduleDate,
                    scheduleRepeatPeriod,
                    quickReplyShortcut,
                    quickReplyShortcutId,
                    0,
                    monoForumPeerId,
                    0
                );
            }
        };
        if (onSentCallback != null) {
            onSentCallback.run();
        }
        if (scheduleDate != 0 && editing == null) {
            pendingSend = doSend;
            finishFragment();
        } else {
            doSend.run();
            finishFragment();
        }
    }

    private Runnable pendingSend;

    private MessageSendPreview messageSendPreview;

    private boolean onSendLongClick(View view) {
        if (chatActivity == null || editingMessageObject != null) return false;
        if (chatActivity.isInScheduleMode()) return false;
        if (!listView.hasAnyText()) return false;
        if (listView.hasPendingUploads()) return false;
        if (!listView.isWithinLimits()) { updateSendButtonEnabled(); return false; }

        final ArrayList<TL_iv.PageBlock> previewBlocks = listView.flattenRowsToBlocks();
        if (previewBlocks.isEmpty()) return false;

        if (messageSendPreview != null) {
            messageSendPreview.dismiss(false);
            messageSendPreview = null;
        }
        messageSendPreview = new MessageSendPreview(getContext(), getResourceProvider());
        messageSendPreview.setOnDismissListener(di -> messageSendPreview = null);

        final long dialogId = chatActivity.getDialogId();
        final MessageObject replyToMsg = chatActivity.getReplyMessage();

        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = 0;
        message.out = true;
        message.peer_id = MessagesController.getInstance(currentAccount).getPeer(dialogId);
        message.from_id = MessagesController.getInstance(currentAccount).getPeer(UserConfig.getInstance(currentAccount).getClientUserId());
        message.flags2 |= TLObject.FLAG_13;
        message.rich_message = new TL_iv.RichMessage();
        message.rich_message.blocks = previewBlocks;
        message.rich_message.photos = listView.collectPhotos();
        message.rich_message.documents = listView.collectDocuments();
        if (replyToMsg != null && !replyToMsg.isTopicMainMessage) {
            TLRPC.TL_messageReplyHeader reply_to = new TLRPC.TL_messageReplyHeader();
            reply_to.flags |= 16;
            reply_to.reply_to_msg_id = replyToMsg.getId();
            message.reply_to = reply_to;
        }

        MessageObject messageObject = new MessageObject(currentAccount, message, false, false);
        if (replyToMsg != null && !replyToMsg.isTopicMainMessage) {
            messageObject.replyMessageObject = replyToMsg;
        }
        messageObject.sendPreview = true;
        messageObject.isOutOwnerCached = true;
        messageObject.generateLayout(null);
        messageObject.notime = true;

        ArrayList<MessageObject> messages = new ArrayList<>();
        messages.add(messageObject);
        messageSendPreview.setMessageObjects(messages);
        sendButton.setScaleX(1.0f);
        sendButton.setScaleY(1.0f);
        final ChatActivityEnterView.SendButton previewSendButton = messageSendPreview.setSendButton(sendButton, true, v -> {
            sendMessage();
            if (messageSendPreview != null) {
                messageSendPreview.dismiss(true);
                messageSendPreview = null;
            }
        });
        if (previewSendButton != null) {
            previewSendButton.setBackground(withShadow(Theme.createRoundRectDrawable(dp(22), getThemedColor(Theme.key_featuredStickers_addButton))));
            messageSendPreview.setSendButtonWidth(dp(44));
        }

        ItemOptions options = ItemOptions.makeOptions(this, sendButton);

        final boolean self = UserObject.isUserSelf(chatActivity.getCurrentUser());
        final boolean scheduleButtonValue = chatActivity.canScheduleMessage();
        if (scheduleButtonValue) {
            options.add(R.drawable.msg_calendar2, getString(self ? R.string.SetReminder : R.string.ScheduleMessage), () -> {
                AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), dialogId, new AlertsCreator.ScheduleDatePickerDelegate() {
                    @Override
                    public void didSelectDate(boolean notify, int scheduleDate, int scheduleRepeatPeriod) {
                        sendMessage(notify, scheduleDate, scheduleRepeatPeriod);
                        if (messageSendPreview != null) {
                            messageSendPreview.dismissInstant();
                            messageSendPreview = null;
                        }
                    }
                }, getResourceProvider());
            });

            if (!self && dialogId > 0) {
                options.add(R.drawable.msg_online, getString(R.string.SendWhenOnline), () -> {
                    sendMessage(true, 0x7FFFFFFE, 0);
                    if (messageSendPreview != null) {
                        messageSendPreview.dismiss(false);
                        messageSendPreview = null;
                    }
                });
            }
        }

        if (!self) {
            options.add(R.drawable.input_notify_off, getString(R.string.SendWithoutSound), () -> {
                sendMessage(false, 0, 0);
                if (messageSendPreview != null) {
                    messageSendPreview.dismiss(true);
                    messageSendPreview = null;
                }
            });
        }
        options.setupSelectors();
        messageSendPreview.setItemOptions(options);

        messageSendPreview.show();

        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {}

        return true;
    }

    private void saveDraftWithBulletin() {
        if (persistDraft()) {
            BulletinFactory.of(bulletinContainer, getResourceProvider())
                .createSimpleBulletin(R.raw.contact_check, getString(R.string.RichEditorDraftSaved))
                .show();
        }
    }

    private boolean persistDraft() {
        if (chatActivity == null) return false;
        if (editingMessageObject != null) return false;
        if (!listView.canUndo()) return false;
        TL_iv.RichMessage rich = sent ? null : listView.buildDraftRichMessage();
        if (rich == null && onClearedCallback != null) {
            onClearedCallback.run();
        }
        final ChatActivityEnterView enterView = chatActivity.getChatActivityEnterView();
        if (rich != null && !sent && listView.isSimpleConvertible() && enterView != null) {
            enterView.applyConvertedSimpleDraft(listView.toSimpleMessage());
            return true;
        }
        getMediaDataController().saveDraft(
            chatActivity.getDialogId(),
            chatActivity.getDraftThreadId(),
            "",
            null,
            null,
            null,
            null,
            0,
            false,
            false,
            rich
        );
        if (enterView != null) {
            enterView.setRichDraftPreview(rich);
        }
        return true;
    }

    private void toggleEmojiPopup() {
        if (emojiViewVisible) {
            openKeyboardFromPopup();
        } else {
            showEmojiPopup();
        }
    }

    private void showEmojiPopup() {
        createEmojiView();
        int h = getEmojiPanelHeight();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) emojiView.getLayoutParams();
        if (lp == null) {
            lp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, h, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        } else {
            lp.height = h;
        }
        lp.bottomMargin = bottomInset;
        emojiView.setLayoutParams(lp);
        emojiView.setVisibility(View.VISIBLE);
        emojiViewVisible = true;
        emojiPadding = h + bottomInset;

        RichEditText et = listView.findFocusedEditText();
        if (et != null) {
            AndroidUtilities.hideKeyboard(et);
        }
        applyEmojiPadding();
        emojiButton.setState(ChatActivityEnterViewAnimatedIconView.State.KEYBOARD, true);
    }

    private void hideEmojiPopup(boolean animated) {
        if (emojiSearchOpened) {
            emojiSearchOpened = false;
            if (emojiView != null) {
                emojiView.closeSearch(false);
                emojiView.hideSearchKeyboard();
            }
        }
        if (emojiSearchAnimator != null) {
            emojiSearchAnimator.cancel();
            emojiSearchAnimator = null;
        }
        emojiSearchProgress = 0f;
        emojiTargetEditText = null;
        if (emojiView != null) {
            emojiView.setTranslationY(0);
            emojiView.setVisibility(View.GONE);
        }
        if (emojiViewVisible || emojiPadding != 0) {
            emojiViewVisible = false;
            emojiPadding = 0;
            applyEmojiPadding();
        }
        if (emojiButton != null) {
            emojiButton.setState(ChatActivityEnterViewAnimatedIconView.State.SMILE, animated);
        }
    }

    private void openKeyboardFromPopup() {
        hideEmojiPopup(true);
        RichEditText et = listView.findFocusedEditText();
        if (et != null) {
            et.requestEditFocus();
            AndroidUtilities.showKeyboard(et);
        }
    }

    private int getEmojiPanelHeight() {
        int h = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight;
        if (h <= 0) h = dp(200);
        return h;
    }

    private void applyEmojiPadding() {
        checkUI_listViewPadding();
    }

    private ValueAnimator emojiSearchAnimator;
    private float emojiSearchProgress;

    private void animateEmojiSearch(boolean open) {
        if (emojiSearchAnimator != null) {
            emojiSearchAnimator.cancel();
            emojiSearchAnimator = null;
        }
        final float to = open ? 1f : 0f;
        emojiSearchAnimator = ValueAnimator.ofFloat(emojiSearchProgress, to);
        emojiSearchAnimator.addUpdateListener(a -> {
            emojiSearchProgress = (float) a.getAnimatedValue();
            applyEmojiSearchOffset();
        });
        emojiSearchAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        emojiSearchAnimator.setDuration(250);
        emojiSearchAnimator.start();
    }

    private void closeEmojiSearch() {
        if (!emojiSearchOpened) return;
        emojiSearchOpened = false;
        if (emojiView != null) {
            emojiView.closeSearch(false);
            emojiView.hideSearchKeyboard();
        }
        animateEmojiSearch(false);
    }

    private int getExpandedEmojiHeight() {
        if (container == null) return getEmojiPanelHeight();
        int h = container.getMeasuredHeight() - container.getPaddingTop() - dp(240) - bottomInset;
        return Math.max(getEmojiPanelHeight(), h);
    }

    private void applyEmojiSearchOffset() {
        if (emojiView == null) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) emojiView.getLayoutParams();
        if (lp == null) return;
        final int normal = getEmojiPanelHeight();
        final int expanded = getExpandedEmojiHeight();
        final int h = Math.round(normal + (expanded - normal) * emojiSearchProgress);
        if (lp.height != h) {
            lp.height = h;
            emojiView.setLayoutParams(lp);
        }
    }

    private RichEditText emojiTargetEditText;
    private int emojiTargetSelection;

    private RichEditText resolveEmojiTarget() {
        RichEditText focused = listView.getFocusedEditTextOrNull();
        if (focused != null) {
            emojiTargetEditText = focused;
            emojiTargetSelection = Math.max(0, focused.getSelectionEnd());
            return focused;
        }
        return emojiTargetEditText != null ? emojiTargetEditText : listView.findFocusedEditText();
    }

    private int resolveEmojiTargetOffset(RichEditText et) {
        if (et == emojiTargetEditText && listView.getFocusedEditTextOrNull() != et) {
            return Math.min(emojiTargetSelection, et.length());
        }
        return Math.max(0, et.getSelectionEnd());
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    private int bottomInset;
    private int imeInset;

    @Override
    public WindowInsetsCompat onInsetsInternal(@NonNull View view, @NonNull WindowInsetsCompat windowInsets) {
        final Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.statusBars());
        imeInset = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        final int keyboardContentHeight = imeInset - bars.bottom;
        final boolean wasKeyboardVisible = keyboardVisible;
        keyboardVisible = keyboardContentHeight > dp(20);
        if (keyboardVisible && keyboardContentHeight > dp(50) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet()) {
            if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                keyboardHeightLand = keyboardContentHeight;
                MessagesController.getGlobalEmojiSettings().edit().putInt("kbd_height_land3", keyboardHeightLand).commit();
            } else {
                keyboardHeight = keyboardContentHeight;
                MessagesController.getGlobalEmojiSettings().edit().putInt("kbd_height", keyboardHeight).commit();
            }
        }
        if (keyboardVisible && !wasKeyboardVisible && emojiViewVisible && !emojiSearchOpened) {
            hideEmojiPopup(false);
        }
        if (!keyboardVisible && wasKeyboardVisible && currentMenuVisible != null) {
            currentMenuVisible.dismiss();
            currentMenuVisible = null;
        }
        onInsets(bars.left, bars.top, bars.right, bars.bottom);
        return WindowInsetsCompat.CONSUMED;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        bottomInset = bottom;
        FrameLayout.LayoutParams bottomGradientLayoutParams = (FrameLayout.LayoutParams) bottomGradient.getLayoutParams();
        bottomGradientLayoutParams.height = dp(8 + 44 + 16) + bottomInset;
        bottomGradient.setLayoutParams(bottomGradientLayoutParams);
        checkUI_listViewPadding();
    }

    private void checkUI_listViewPadding() {
        if (emojiView != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) emojiView.getLayoutParams();
            if (lp != null && lp.bottomMargin != bottomInset) {
                lp.bottomMargin = bottomInset;
                emojiView.setLayoutParams(lp);
            }
        }
        applyEmojiSearchOffset();
        final int bottom = Math.max(Math.max(emojiPadding, bottomInset), imeInset);
        listView.setPadding(0, dp(60), 0, dp(8 + 44 + 8 + 50) + bottom);
        listView.setInsets(bottomInset, imeInset, emojiPadding);
        bottomContainer.setTranslationY(-bottom);
        bottomGradient.setTranslationY(-bottom + bottomInset);
    }

    private void createEmojiView() {
        if (emojiView != null) return;
        emojiView = new EmojiView(this, true, false, false, getContext(), true, null, container, true, getResourceProvider(), false);
        emojiView.setVisibility(View.GONE);
        emojiView.fixBottomTabContainerTranslation = false;
        emojiView.setDelegate(new EmojiView.EmojiViewDelegate() {
            @Override
            public void onSearchOpenClose(int type) {
                if (type != 0) {
                    RichEditText focused = listView.getFocusedEditTextOrNull();
                    if (focused != null) {
                        emojiTargetEditText = focused;
                        emojiTargetSelection = Math.max(0, focused.getSelectionEnd());
                    }
                }
                emojiSearchOpened = type != 0;
                animateEmojiSearch(emojiSearchOpened);
            }

            @Override
            public boolean isSearchOpened() {
                return emojiSearchOpened;
            }

            @Override
            public void onStickersSettingsClick() {
                presentFragment(new StickersActivity(MediaDataController.TYPE_IMAGE, null));
            }

            @Override
            public void onEmojiSettingsClick(ArrayList<TLRPC.TL_messages_stickerSet> frozenEmojiPacks) {
                presentFragment(new StickersActivity(MediaDataController.TYPE_EMOJIPACKS, frozenEmojiPacks));
            }

            @Override
            public boolean onBackspace() {
                RichEditText et = resolveEmojiTarget();
                if (et == null || et.length() == 0) return false;
                et.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                return true;
            }

            @Override
            public void onEmojiSelected(String symbol) {
                RichEditText et = resolveEmojiTarget();
                if (et == null) return;
                int i = resolveEmojiTargetOffset(et);
                try {
                    CharSequence cs = Emoji.replaceEmoji(symbol, et.getPaint().getFontMetricsInt(), false, null);
                    et.setText(et.getText().insert(i, cs));
                    int j = i + cs.length();
                    et.setSelection(j, j);
                    if (et == emojiTargetEditText) emojiTargetSelection = j;
                } catch (Exception ignore) {}
            }

            @Override
            public void onCustomEmojiSelected(long documentId, TLRPC.Document document, String emoticon, boolean isRecent) {
                RichEditText et = resolveEmojiTarget();
                if (et == null) return;
                int i = resolveEmojiTargetOffset(et);
                try {
                    SpannableString spannable = new SpannableString(emoticon == null ? "😀" : emoticon);
                    AnimatedEmojiSpan span = document != null
                        ? new AnimatedEmojiSpan(document, et.getPaint().getFontMetricsInt())
                        : new AnimatedEmojiSpan(documentId, et.getPaint().getFontMetricsInt());
                    span.cacheType = AnimatedEmojiDrawable.getCacheTypeForEnterView();
                    spannable.setSpan(span, 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    et.setText(et.getText().insert(i, spannable));
                    int j = i + spannable.length();
                    et.setSelection(j, j);
                    if (et == emojiTargetEditText) emojiTargetSelection = j;
                } catch (Exception ignore) {}
            }
        });
        int insertIndex = container.indexOfChild(bottomPanel);
        if (insertIndex < 0) insertIndex = container.getChildCount();
        FrameLayout.LayoutParams lp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, getEmojiPanelHeight(), Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        lp.bottomMargin = bottomInset;
        container.addView(emojiView, insertIndex, lp);
    }

    private void onKeyboardSizeChanged(int height, boolean isWidthGreater) {

    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (emojiSearchOpened) {
            closeEmojiSearch();
            return false;
        }
        if (emojiViewVisible) {
            hideEmojiPopup(true);
            return false;
        }
        if (listView.deselectIfAny()) {
            return false;
        }
        return super.onBackPressed(invoked);
    }

    private void updatePremiumButtons() {
        final boolean premiumLock = !MessagesController.getInstance(currentAccount).richEditorAllowed() && !UserConfig.getInstance(currentAccount).isPremium();
        for (Button b : premiumButtons) {
            b.setPremiumLocked(premiumLock);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.currentUserPremiumStatusChanged) {
            updateSendButtonLock();
            updatePremiumButtons();
        }
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        return super.onFragmentCreate();
    }

    private boolean persistedDraftOnEnd;

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        if (!persistedDraftOnEnd) {
            persistDraft();
            persistedDraftOnEnd = true;
        }
        if (pendingSend != null) {
            final Runnable send = pendingSend;
            pendingSend = null;
            AndroidUtilities.runOnUIThread(send);
        }
        if (messageSendPreview != null) {
            messageSendPreview.dismissInstant();
            messageSendPreview = null;
        }
        if (listView != null) {
            listView.destroy();
        }
        if (commandSuggestions != null) {
            commandSuggestions.hide();
        }
        super.onFragmentDestroy();
        if (emojiView != null) {
            emojiView.onDestroy();
        }
        if (container != null && sizeDelegate != null) {
            container.removeDelegate(sizeDelegate);
        }
    }

    public static BottomSheet openConversionSheet(Context context, Runnable convert, Runnable openPremium, Theme.ResourcesProvider resourcesProvider) {
        final BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        b.setCustomView(layout);

        final ImageView topIcon = new ImageView(context);
        topIcon.setImageResource(R.drawable.large_article);
        topIcon.setScaleType(ImageView.ScaleType.CENTER);
        topIcon.setBackground(Theme.createCircleDrawable(dp(80), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
        layout.addView(topIcon, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 18, 0, 0));

        final TextView titleTextView = new TextView(context);
        titleTextView.setText(getString(R.string.ArticleConversionTitle));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setGravity(Gravity.CENTER);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        layout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 25, 16, 25, 0));

        final TextView textView = new TextView(context);
        textView.setText(AndroidUtilities.replaceTags(getString(R.string.ArticleConversionText)));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 25, 11, 25, 0));

        final ButtonWithCounterView button1 = new ButtonWithCounterView(context, resourcesProvider).setRound();
        button1.setText(getString(R.string.ArticleConversionSubscribe));
        layout.addView(button1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 14, 31, 14, 0));

        final ButtonWithCounterView button2 = new ButtonWithCounterView(context, false, resourcesProvider).setRound();
        button2.setText(getString(R.string.ArticleConversionConvert));
        layout.addView(button2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 14, 2, 14, 6));

        final BottomSheet sheet = b.show();

        button1.setOnClickListener(v -> {
            sheet.dismiss();
            openPremium.run();
        });
        button2.setOnClickListener(v -> {
            sheet.dismiss();
            convert.run();
        });

        return sheet;
    }
}
