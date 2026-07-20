package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AiButtonDrawable;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.ChatActivityEnterViewAnimatedIconView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;

public class RichEditorToolbar extends FrameLayout {

    public static final int BLOCK_TEXT = 1;
    public static final int BLOCK_LIST = 2;
    public static final int BLOCK_TABLE = 4;
    public static final int BLOCK_MATH = 7;
    public static final int BLOCK_DETAILS = 9;

    public interface Delegate {
        Theme.ResourcesProvider getResourcesProvider();
        void onBack();
        void onUndo();
        void onRedo();
        void onEmoji();
        void onAi();
        void onAttach();
        void onSend();
        boolean onSendLongClick(View anchor);
        void onBlockButton(int flag, View anchor);
        void onFormatting(int styleFlag);
        void onLink();
        void onDate();
        void onMath();
        void onQuote();
        void onAiStyle();
    }

    private final Delegate delegate;
    private final Theme.ResourcesProvider resourcesProvider;

    private final View topGradient, bottomGradient;
    private final FrameLayout topPanel;
    private final ImageView backButton;
    private final LinearLayout historyButtons;
    private final ImageView undoButton, redoButton;

    private final FrameLayout bottomContainer;
    private final FrameLayout bottomInnerContainer;
    private final LinearLayout bottomPanel;
    private final ChatActivityEnterViewAnimatedIconView emojiButton;
    private final ImageView aiButton;
    private final HorizontalScrollView blocksScrollView;
    private final LinearLayout blocksLayout;
    private final ImageView addButton;
    private final ChatActivityEnterView.SendButton sendButton;

    private final LinearLayout formattingPanel;
    private final FrameLayout trashPanel;
    private final RLottieImageView trashPanelIcon;
    private final LinearLayout formattingPanelLayout;
    private HorizontalScrollView formattingScrollView;
    private int formattingScrollMaxWidth = Integer.MAX_VALUE;
    private LinearLayout formattingLayout1;
    private LinearLayout formattingLayout2;
    private LinearLayout formattingLayout3;
    private final RichEditor.Button aiStyleButton;
    private final RichEditor.Button linkButton, dateButton, mathButton, quoteButton;

    private final ArrayList<RichEditor.Button> blockButtons = new ArrayList<>();
    private final ArrayList<RichEditor.Button> formattingButtons = new ArrayList<>();
    private final ArrayList<RichEditor.Button> premiumButtons = new ArrayList<>();

    private boolean sendLoading;

    public RichEditorToolbar(Context context, Delegate delegate) {
        super(context);
        this.delegate = delegate;
        this.resourcesProvider = delegate.getResourcesProvider();

        setClipChildren(false);
        setClipToPadding(false);

        topGradient = new View(context);
        topGradient.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{
            color(Theme.key_windowBackgroundWhite),
            Theme.multAlpha(color(Theme.key_windowBackgroundWhite), 0.0f)
        }));
        addView(topGradient, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8 + 44 + 16, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        bottomGradient = new View(context);
        bottomGradient.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{
            Theme.multAlpha(color(Theme.key_windowBackgroundWhite), 0.0f),
            color(Theme.key_windowBackgroundWhite)
        }));
        addView(bottomGradient, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8 + 44 + 16, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        topPanel = new FrameLayout(context);
        topPanel.setClipChildren(false);
        topPanel.setClipToPadding(false);
        addView(topPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        backButton = new ImageView(context);
        backButton.setImageResource(R.drawable.ic_ab_back);
        backButton.setScaleType(ImageView.ScaleType.CENTER);
        backButton.setBackground(RichEditor.withShadow(Theme.createRadSelectorDrawable(color(Theme.key_glass_targetMainTabs), Theme.blendOver(color(Theme.key_glass_targetMainTabs), color(Theme.key_listSelector)), dp(22), dp(22))));
        backButton.setColorFilter(new PorterDuffColorFilter(color(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        ScaleStateListAnimator.apply(backButton);
        backButton.setContentDescription(getString(R.string.AccDescrGoBack));
        backButton.setOnClickListener(v -> delegate.onBack());
        topPanel.addView(backButton, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.LEFT, 8, 8, 8, 8));

        historyButtons = new LinearLayout(context);
        historyButtons.setOrientation(LinearLayout.HORIZONTAL);
        historyButtons.setBackground(RichEditor.withShadow(Theme.createRoundRectDrawable(dp(22), color(Theme.key_glass_targetMainTabs))));
        topPanel.addView(historyButtons, LayoutHelper.createFrame(82, 44, Gravity.TOP | Gravity.RIGHT, 8, 8, 8, 8));

        undoButton = new ImageView(context);
        undoButton.setImageResource(R.drawable.iv_undo);
        undoButton.setScaleType(ImageView.ScaleType.CENTER);
        undoButton.setBackground(Theme.createSelectorDrawable(color(Theme.key_listSelector)));
        undoButton.setColorFilter(new PorterDuffColorFilter(color(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        ScaleStateListAnimator.apply(undoButton);
        undoButton.setContentDescription("Undo");
        undoButton.setOnClickListener(v -> delegate.onUndo());
        historyButtons.addView(undoButton, LayoutHelper.createLinear(41, 41, Gravity.CENTER_VERTICAL));

        redoButton = new ImageView(context);
        redoButton.setImageResource(R.drawable.iv_redo);
        redoButton.setScaleType(ImageView.ScaleType.CENTER);
        redoButton.setBackground(Theme.createSelectorDrawable(color(Theme.key_listSelector)));
        redoButton.setColorFilter(new PorterDuffColorFilter(color(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        ScaleStateListAnimator.apply(redoButton);
        redoButton.setContentDescription("Redo");
        redoButton.setOnClickListener(v -> delegate.onRedo());
        historyButtons.addView(redoButton, LayoutHelper.createLinear(41, 41, Gravity.CENTER_VERTICAL));

        bottomContainer = new FrameLayout(context);
        bottomContainer.setClipChildren(false);
        bottomContainer.setClipToPadding(false);
        addView(bottomContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        bottomInnerContainer = new FrameLayout(context);
        bottomInnerContainer.setClipChildren(false);
        bottomInnerContainer.setClipToPadding(false);
        bottomContainer.addView(bottomInnerContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8 + 44 + 8, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        bottomPanel = new LinearLayout(context);
        bottomPanel.setClipToPadding(false);
        bottomPanel.setClipChildren(false);
        bottomPanel.setPadding(dp(8), dp(8), dp(8), dp(8));
        bottomInnerContainer.addView(bottomPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8 + 44 + 8, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        emojiButton = new ChatActivityEnterViewAnimatedIconView(context, 24);
        emojiButton.setPadding(dp(10), dp(10), dp(10), dp(10));
        emojiButton.setColorFilter(new PorterDuffColorFilter(color(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        emojiButton.setBackground(RichEditor.withShadow(Theme.createRadSelectorDrawable(color(Theme.key_glass_targetMainTabs), Theme.blendOver(color(Theme.key_glass_targetMainTabs), color(Theme.key_listSelector)), dp(22), dp(22))));
        emojiButton.setState(ChatActivityEnterViewAnimatedIconView.State.SMILE, false);
        bottomPanel.addView(emojiButton, LayoutHelper.createLinear(44, 44, 0, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
        ScaleStateListAnimator.apply(emojiButton);
        emojiButton.setContentDescription("Emoji");
        emojiButton.setOnClickListener(v -> delegate.onEmoji());

        aiButton = new ImageView(context);
        aiButton.setImageDrawable(new AiButtonDrawable(context));
        aiButton.setScaleType(ImageView.ScaleType.CENTER);
        aiButton.setColorFilter(new PorterDuffColorFilter(color(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        aiButton.setBackground(RichEditor.withShadow(Theme.createRadSelectorDrawable(color(Theme.key_glass_targetMainTabs), Theme.blendOver(color(Theme.key_glass_targetMainTabs), color(Theme.key_listSelector)), dp(22), dp(22))));
        bottomPanel.addView(aiButton, LayoutHelper.createLinear(44, 44, 0, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
        ScaleStateListAnimator.apply(aiButton);
        aiButton.setContentDescription("AI");
        aiButton.setOnClickListener(v -> delegate.onAi());

        final FrameLayout blocksContainer2 = new FrameLayout(context);
        blocksContainer2.setClipToPadding(false);
        blocksContainer2.setClipChildren(false);

        final FrameLayout blocksContainer = new FrameLayout(context);
        blocksContainer.setBackground(RichEditor.withShadow(Theme.createRoundRectDrawable(dp(22), color(Theme.key_glass_targetMainTabs))));
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

        addBlockButton(R.drawable.iv_text, BLOCK_TEXT, false);
        addBlockButton(R.drawable.iv_lists, BLOCK_LIST, true);
        addBlockButton(R.drawable.iv_table, BLOCK_TABLE, true);
        addBlockButton(R.drawable.iv_math, BLOCK_MATH, true);

        bottomPanel.addView(blocksContainer2, LayoutHelper.createLinear(0, 44, 1f));

        addButton = new ImageView(context);
        addButton.setImageResource(R.drawable.outline_poll_attach_24);
        addButton.setScaleType(ImageView.ScaleType.CENTER);
        addButton.setColorFilter(new PorterDuffColorFilter(color(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        addButton.setBackground(RichEditor.withShadow(Theme.createRadSelectorDrawable(color(Theme.key_glass_targetMainTabs), Theme.blendOver(color(Theme.key_glass_targetMainTabs), color(Theme.key_listSelector)), dp(22), dp(22))));
        bottomPanel.addView(addButton, LayoutHelper.createLinear(44, 44, 0, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 8, 0, 0, 0));
        ScaleStateListAnimator.apply(addButton);
        addButton.setContentDescription("Attach");
        addButton.setOnClickListener(v -> delegate.onAttach());

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
        trashPanelIcon.setColorFilter(new PorterDuffColorFilter(color(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        trashPanelIcon.setBackground(RichEditor.withShadow(Theme.createRoundRectDrawable(dp(22), color(Theme.key_glass_targetMainTabs))));
        trashPanel.addView(trashPanelIcon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        final FrameLayout formattingStylesContainer = new FrameLayout(context);
        formattingStylesContainer.setBackground(RichEditor.withShadow(Theme.createRoundRectDrawable(dp(22), color(Theme.key_glass_targetMainTabs))));
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

        addFormattingButton(R.drawable.formatting_bold, RichTextStyle.BOLD);
        addFormattingButton(R.drawable.formatting_italic, RichTextStyle.ITALIC);
        addFormattingButton(R.drawable.formatting_underline, RichTextStyle.UNDERLINE);
        addFormattingButton(R.drawable.formatting_strikethrough, RichTextStyle.STRIKE);
        addFormattingButton(R.drawable.formatting_spoiler, RichTextStyle.SPOILER);
        addFormattingButton(R.drawable.iv_code, RichTextStyle.MONO);
        addFormattingButton(R.drawable.iv_sub, RichTextStyle.SUBSCRIPT, true);
        addFormattingButton(R.drawable.iv_super, RichTextStyle.SUPERSCRIPT, true);

        quoteButton = new RichEditor.Button(context, R.drawable.iv_quote, resourcesProvider);
        quoteButton.setBackgroundColorKey(Theme.key_glass_targetMainTabs);
        quoteButton.setContentDescription(getString(R.string.Quote));
        quoteButton.setOnClickListener(v -> delegate.onQuote());
        formattingPanelLayout.addView(quoteButton, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL, formattingPanelLayout.getChildCount() == 0 ? 0 : 2, 0, 0, 0));

        formattingLayout2 = new LinearLayout(context);
        formattingLayout2.setOrientation(LinearLayout.HORIZONTAL);
        formattingLayout2.setPadding(dp(2), 0, dp(2), 0);
        formattingLayout2.setBackground(RichEditor.withShadow(Theme.createRoundRectDrawable(dp(22), color(Theme.key_glass_targetMainTabs))));
        formattingPanel.addView(formattingLayout2, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 44, Gravity.BOTTOM, 8, 0, 0, 0));

        linkButton = new RichEditor.Button(context, R.drawable.media_link_24, resourcesProvider);
        linkButton.setBackgroundColorKey(Theme.key_glass_targetMainTabs);
        linkButton.setContentDescription(getString(R.string.CreateLink));
        linkButton.setOnClickListener(v -> delegate.onLink());
        formattingLayout2.addView(linkButton, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL));
        dateButton = new RichEditor.Button(context, R.drawable.msg_calendar2, resourcesProvider);
        dateButton.setBackgroundColorKey(Theme.key_glass_targetMainTabs);
        dateButton.setContentDescription(getString(R.string.AccDescrIVInsertDate));
        dateButton.setOnClickListener(v -> delegate.onDate());
        formattingLayout2.addView(dateButton, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL));

        formattingLayout3 = new LinearLayout(context);
        formattingLayout3.setOrientation(LinearLayout.HORIZONTAL);
        formattingLayout3.setPadding(dp(2), 0, dp(2), 0);
        formattingLayout3.setBackground(RichEditor.withShadow(Theme.createRoundRectDrawable(dp(22), color(Theme.key_glass_targetMainTabs))));
        formattingPanel.addView(formattingLayout3, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 44, Gravity.BOTTOM, 8, 0, 0, 0));

        mathButton = new RichEditor.Button(context, R.drawable.iv_math, resourcesProvider);
        mathButton.setBackgroundColorKey(Theme.key_glass_targetMainTabs);
        mathButton.setPremium();
        premiumButtons.add(mathButton);
        mathButton.setContentDescription(getString(R.string.AccDescrIVFormula));
        mathButton.setOnClickListener(v -> delegate.onMath());
        formattingLayout3.addView(mathButton, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL));

        formattingLayout1 = new LinearLayout(context);
        formattingLayout1.setOrientation(LinearLayout.HORIZONTAL);
        formattingLayout1.setPadding(dp(2), 0, dp(2), 0);
        formattingLayout1.setBackground(RichEditor.withShadow(Theme.createRoundRectDrawable(dp(22), color(Theme.key_glass_targetMainTabs))));
        formattingPanel.addView(formattingLayout1, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 44, Gravity.BOTTOM, 0, 0, 8, 0));

        aiStyleButton = new RichEditor.Button(context, R.drawable.input_ai, resourcesProvider);
        aiStyleButton.setImageDrawable(new AiButtonDrawable(context));
        aiStyleButton.setBackgroundColorKey(Theme.key_glass_targetMainTabs);
        aiStyleButton.setContentDescription(getString(R.string.AIEditor));
        aiStyleButton.setOnClickListener(v -> delegate.onAiStyle());
        formattingLayout1.addView(aiStyleButton, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL));

        sendButton = new ChatActivityEnterView.SendButton(context, R.drawable.send_plane_24, resourcesProvider, true) {
            @Override
            public boolean isOpen() {
                return sendLoading || super.isOpen();
            }
        };
        sendButton.setBackground(RichEditor.withShadow(Theme.createRoundRectDrawable(dp(22), color(Theme.key_chat_messagePanelSend))));
        ScaleStateListAnimator.apply(sendButton);
        bottomPanel.addView(sendButton, LayoutHelper.createLinear(44, 44, 0, Gravity.RIGHT, 8, 0, 0, 0));
        sendButton.setContentDescription("Send");
        sendButton.setOnClickListener(v -> delegate.onSend());
        sendButton.setOnLongClickListener(v -> delegate.onSendLongClick(v));

        updatePanel(PANEL_TOOLBAR, false);
    }

    private RichEditor.Button addBlockButton(int icon, int flag, boolean premium) {
        final RichEditor.Button button = new RichEditor.Button(blocksLayout.getContext(), icon, resourcesProvider);
        button.setBackgroundColorKey(Theme.key_glass_targetMainTabs);
        if (premium) {
            button.setPremium();
            premiumButtons.add(button);
        }
        button.setTag(flag);
        button.setContentDescription(RichEditor.blockButtonContentDescription(flag));
        button.setOnClickListener(v -> delegate.onBlockButton(flag, v));
        blockButtons.add(button);
        blocksLayout.addView(button, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL, blocksLayout.getChildCount() == 0 ? 0 : 2, 0, 0, 0));
        return button;
    }

    private void addFormattingButton(int icon, int styleFlag) {
        addFormattingButton(icon, styleFlag, false);
    }

    private void addFormattingButton(int icon, int styleFlag, boolean premium) {
        final RichEditor.Button button = new RichEditor.Button(getContext(), icon, resourcesProvider);
        button.setBackgroundColorKey(Theme.key_glass_targetMainTabs);
        if (premium) {
            button.setPremium();
            premiumButtons.add(button);
        }
        button.setTag(styleFlag);
        button.setContentDescription(RichEditor.formattingButtonContentDescription(styleFlag));
        button.setOnClickListener(v -> delegate.onFormatting(styleFlag));
        formattingButtons.add(button);
        formattingPanelLayout.addView(button, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL, formattingPanelLayout.getChildCount() == 0 ? 0 : 2, 0, 0, 0));
    }

    public void setPremiumLocked(boolean locked) {
        for (final RichEditor.Button button : premiumButtons) {
            button.setPremiumLocked(locked);
        }
    }

    // ---- public state setters ----

    public void setBackVisible(boolean visible) {
        backButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setTopPanelVisible(boolean visible) {
        topPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        topGradient.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setTopGradientVisible(boolean visible) {
        topGradient.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** Vertical offset (px) of the undo/redo (and back) buttons from the toolbar top. */
    public void setTopButtonsOffset(int px) {
        final FrameLayout.LayoutParams hp = (FrameLayout.LayoutParams) historyButtons.getLayoutParams();
        if (hp.topMargin != px) {
            hp.topMargin = px;
            historyButtons.setLayoutParams(hp);
        }
        final FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) backButton.getLayoutParams();
        if (bp.topMargin != px) {
            bp.topMargin = px;
            backButton.setLayoutParams(bp);
        }
    }

    public void setHistoryEnabled(boolean canUndo, boolean canRedo) {
        undoButton.setEnabled(canUndo);
        undoButton.setAlpha(canUndo ? 1f : 0.35f);
        redoButton.setEnabled(canRedo);
        redoButton.setAlpha(canRedo ? 1f : 0.35f);
    }

    public void setSelectedBlockType(int type) {
        setSelectedBlockType(type, 0);
    }

    public void setSelectedBlockType(int type, int selectedIcon) {
        for (final RichEditor.Button button : blockButtons) {
            final int flag = (Integer) button.getTag();
            final boolean sel = type == flag;
            button.setSelected(sel);
            if (sel && selectedIcon != 0) button.updateIcon(selectedIcon);
            else button.resetIcon();
        }
    }

    public void setFormattingState(int appliedMask, boolean linkApplied, boolean dateApplied, boolean inlineEnabled, boolean boldItalicEnabled) {
        for (final RichEditor.Button button : formattingButtons) {
            final int styleFlag = (Integer) button.getTag();
            button.setSelected((appliedMask & styleFlag) != 0);
            // Headings render in a fixed typeface, so bold/italic don't apply — disable them there.
            if (styleFlag == RichTextStyle.BOLD || styleFlag == RichTextStyle.ITALIC) {
                button.setEnabled(boldItalicEnabled);
            }
        }
        linkButton.setSelected(linkApplied);
        dateButton.setSelected(dateApplied);
        linkButton.setEnabled(inlineEnabled);
        dateButton.setEnabled(inlineEnabled);
        mathButton.setEnabled(inlineEnabled);
    }

    public void setQuoteState(boolean quoted) {
        quoteButton.setSelected(quoted);
    }

    private static final int PANEL_TOOLBAR = 0;
    private static final int PANEL_FORMATTING = 1;
    private static final int PANEL_TRASH = 2;

    private int panelType = -1;
    private int reorderSavedPanelType = PANEL_TOOLBAR;
    private boolean trashHovered;

    public void showFormattingPanel(boolean formatting, boolean animated) {
        if (panelType == PANEL_TRASH) {
            reorderSavedPanelType = formatting ? PANEL_FORMATTING : PANEL_TOOLBAR;
            return;
        }
        updatePanel(formatting ? PANEL_FORMATTING : PANEL_TOOLBAR, animated);
    }

    private void updatePanel(int type, boolean animated) {
        if (panelType == type) return;
        panelType = type;
        if (animated) {
            bottomPanel.setVisibility(View.VISIBLE);
            bottomPanel.animate()
                .alpha(type == PANEL_TOOLBAR ? 1.0f : 0.0f)
                .scaleX(type == PANEL_TOOLBAR ? 1.0f : 0.8f)
                .scaleY(type == PANEL_TOOLBAR ? 1.0f : 0.8f)
                .translationY(type == PANEL_TOOLBAR ? 0 : dp(30))
                .setDuration(420)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .withEndAction(() -> {
                    if (panelType != PANEL_TOOLBAR) bottomPanel.setVisibility(View.GONE);
                })
                .start();
            formattingPanel.setVisibility(View.VISIBLE);
            formattingPanel.animate()
                .alpha(type == PANEL_FORMATTING ? 1.0f : 0.0f)
                .scaleX(type == PANEL_FORMATTING ? 1.0f : 0.8f)
                .scaleY(type == PANEL_FORMATTING ? 1.0f : 0.8f)
                .translationY(type == PANEL_FORMATTING ? 0 : dp(30))
                .setDuration(420)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .withEndAction(() -> {
                    if (panelType != PANEL_FORMATTING) formattingPanel.setVisibility(View.GONE);
                })
                .start();
            trashPanel.setVisibility(View.VISIBLE);
            trashPanel.animate()
                .alpha(type == PANEL_TRASH ? 1.0f : 0.0f)
                .scaleX(type == PANEL_TRASH ? 1.0f : 0.8f)
                .scaleY(type == PANEL_TRASH ? 1.0f : 0.8f)
                .setDuration(420)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .withEndAction(() -> {
                    if (panelType != PANEL_TRASH) trashPanel.setVisibility(View.GONE);
                })
                .start();
        } else {
            bottomPanel.setVisibility(type == PANEL_TOOLBAR ? View.VISIBLE : View.GONE);
            bottomPanel.setAlpha(type == PANEL_TOOLBAR ? 1.0f : 0.0f);
            bottomPanel.setScaleX(type == PANEL_TOOLBAR ? 1.0f : 0.8f);
            bottomPanel.setScaleY(type == PANEL_TOOLBAR ? 1.0f : 0.8f);
            bottomPanel.setTranslationY(type == PANEL_TOOLBAR ? 0 : dp(30));
            formattingPanel.setVisibility(type == PANEL_FORMATTING ? View.VISIBLE : View.GONE);
            formattingPanel.setAlpha(type == PANEL_FORMATTING ? 1.0f : 0.0f);
            formattingPanel.setScaleX(type == PANEL_FORMATTING ? 1.0f : 0.8f);
            formattingPanel.setScaleY(type == PANEL_FORMATTING ? 1.0f : 0.8f);
            formattingPanel.setTranslationY(type == PANEL_FORMATTING ? 0 : dp(30));
            trashPanel.setVisibility(type == PANEL_TRASH ? View.VISIBLE : View.GONE);
            trashPanel.setAlpha(type == PANEL_TRASH ? 1.0f : 0.0f);
            trashPanel.setScaleX(type == PANEL_TRASH ? 1.0f : 0.8f);
            trashPanel.setScaleY(type == PANEL_TRASH ? 1.0f : 0.8f);
        }
    }

    // ---- reorder / trash ----

    public void onReorderStart() {
        reorderSavedPanelType = panelType == PANEL_TRASH ? PANEL_TOOLBAR : panelType;
        setTrashHovered(false, false);
        updatePanel(PANEL_TRASH, true);
    }

    public boolean onReorderMove(float screenX, float screenY) {
        final boolean over = isOverTrash(screenY);
        setTrashHovered(over, true);
        return over;
    }

    public void onReorderEnd() {
        setTrashHovered(false, true);
        updatePanel(reorderSavedPanelType == PANEL_TRASH ? PANEL_TOOLBAR : reorderSavedPanelType, true);
    }

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
        trashPanelIcon.setColorFilter(new PorterDuffColorFilter(color(hovered ? Theme.key_text_RedBold : Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        // Play the lid-open part of the lottie on hover (frames 0..33), reverse back to 0 otherwise,
        // matching StoryRecorder.TrashView.
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

    public void setSendEditing(boolean editing) {
        sendButton.setResourceId(editing ? R.drawable.input_done : R.drawable.send_plane_24);
    }

    public void setSendLoading(boolean loading) {
        if (sendLoading == loading) return;
        sendLoading = loading;
        sendButton.invalidate();
    }

    public void setSendEnabled(boolean enabled) {
        if (sendButton.isEnabled() == enabled) return;
        sendButton.setEnabled(enabled);
        sendButton.animate().alpha(enabled ? 1.0f : 0.5f).setDuration(150).start();
    }

    public void setEmojiOpened(boolean opened) {
        emojiButton.setState(opened ? ChatActivityEnterViewAnimatedIconView.State.KEYBOARD : ChatActivityEnterViewAnimatedIconView.State.SMILE, true);
        emojiButton.setContentDescription(opened ? "Keyboard" : "Emoji");
    }

    public ChatActivityEnterView.SendButton getSendButton() {
        return sendButton;
    }

    public ImageView getAddButton() {
        return addButton;
    }

    public View getEmojiButton() {
        return emojiButton;
    }

    public LinearLayout getBottomPanel() {
        return bottomPanel;
    }

    public FrameLayout getBottomContainer() {
        return bottomContainer;
    }

    public void setBottomGradientTranslationY(float ty) {
        bottomGradient.setTranslationY(ty);
    }

    public FrameLayout getBottomInnerContainer() {
        return bottomInnerContainer;
    }

    private int color(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
