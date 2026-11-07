package org.telegram.ui.Components.Reactions;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.AndroidUtilities.replaceTags;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Components.Reactions.ReactionsUtils.addReactionToEditText;
import static org.telegram.ui.Components.Reactions.ReactionsUtils.createAnimatedEmojiSpan;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.SlideIntChooseView;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.SelectAnimatedEmojiDialog;
import org.telegram.utils.glass.BaseFragmentSimpleGlass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class ChatCustomReactionsEditActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public final static int SELECT_TYPE_NONE = 2,
            SELECT_TYPE_SOME = 1,
            SELECT_TYPE_ALL = 0;

    private boolean paid;

    private SelectAnimatedEmojiDialog selectAnimatedEmojiDialog;
    private FrameLayout bottomDialogLayout;
    private BackSpaceButtonView backSpaceButtonView;
    private TextCheckCell enableReactionsCell;
    private TextInfoPrivacyCell reactionsInfoCell;
    private CustomReactionEditText editText;
    private SlideIntChooseView slideView;
    private TextCheckCell paidCheckCell;
    private UpdateReactionsButton actionButton;
    private FrameLayout actionButtonContainer;
    private ImageView actionButtonContainerGradient;
    private int keyboardHeight;
    private int bottomInset;
    private final Paint bottomInsetPaint = new Paint();
    private UniversalRecyclerView scrollView;
    private final ArrayList<View> reactionSettingsViews = new ArrayList<>();
    private final ArrayList<Boolean> reactionSettingsShadows = new ArrayList<>();

    private final HashMap<Long, AnimatedEmojiSpan> selectedEmojisMap = new LinkedHashMap<>();
    private final List<Long> selectedEmojisIds = new ArrayList<>();
    private final HashMap<Long, AnimatedEmojiSpan> initialSelectedEmojis = new LinkedHashMap<>();
    private final List<TLRPC.TL_availableReaction> allAvailableReactions = new ArrayList<>();
    private boolean initialPaid;

    private final int maxReactionsCount = getMessagesController().boostsChannelLevelMax;
    private boolean emojiKeyboardVisible = false;
    private boolean allowShowEmojiKeyboard;
    private BoolAnimator emojiKeyboardAnimator;
    private final TLRPC.ChatFull info;
    private final long chatId;
    private int currentReactionsCount, reactionsCount;
    private TLRPC.Chat currentChat;
    private TL_stories.TL_premium_boostsStatus boostsStatus;
    private int selectedCustomReactions;
    private int selectedType = -1;
    private boolean isPaused;
    private final Runnable checkAfterFastDeleteRunnable = () -> checkMaxCustomReactions(false);

    public ChatCustomReactionsEditActivity(long chatId, TLRPC.ChatFull info) {
        super();
        this.chatId = chatId;
        this.info = info;
    }

    private void addReactionSettingsView(View view, boolean shadow) {
        reactionSettingsViews.add(view);
        reactionSettingsShadows.add(shadow);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (enableReactionsCell == null || reactionsInfoCell == null) return;

        items.add(UItem.asCustom(1, enableReactionsCell));
        items.add(UItem.asCustomShadow(2, reactionsInfoCell));

        if (selectedType != SELECT_TYPE_SOME && selectedType != SELECT_TYPE_ALL && !paid) return;
        for (int i = 0; i < reactionSettingsViews.size(); i++) {
            final View view = reactionSettingsViews.get(i);
            items.add(reactionSettingsShadows.get(i) ?
                    UItem.asCustomShadow(100 + i, view) :
                    UItem.asCustom(100 + i, view));
        }
    }

    @Override
    public boolean onFragmentCreate() {
        currentChat = getMessagesController().getChat(chatId);
        if (currentChat == null) {
            currentChat = MessagesStorage.getInstance(currentAccount).getChatSync(chatId);
            if (currentChat != null) {
                getMessagesController().putChat(currentChat, true);
            } else {
                return false;
            }
        }

        if (info == null) {
            return false;
        }

        getMessagesController().getBoostsController().getBoostsStats(-chatId, boostsStatus -> {
            this.boostsStatus = boostsStatus;
            boolean hasChanges = !selectedEmojisMap.keySet().equals(initialSelectedEmojis.keySet());
            if (hasChanges) {
                checkMaxCustomReactions(false);
            }
        });
        getNotificationCenter().addWeakObserver(this, NotificationCenter.reactionsDidLoad);
        allAvailableReactions.addAll(getMediaDataController().getEnabledReactionsList());
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
        getNotificationCenter().addWeakObserver(this, NotificationCenter.dialogDeleted);
        return super.onFragmentCreate();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setTitle(LocaleController.getString(R.string.Reactions));
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);

        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (!checkChangesBeforeExit(true)) {
                        finishFragment();
                    }
                }
            }
        });

        scrollView = new UniversalRecyclerView(this, this::fillItems, null, null);
        scrollView.setSections(true);
        scrollView.setSectionsDrawBackground(true);

        FrameLayout rootLayout = new FrameLayout(context) {
            final AdjustPanLayoutHelper adjustPanLayoutHelper = new AdjustPanLayoutHelper(this) {

                @Override
                protected void onTransitionStart(boolean keyboardVisible, int contentHeight) {
                    actionButtonContainer.setVisibility(View.VISIBLE);
                    actionButtonContainer.animate().alpha(!keyboardVisible ? 1.0f : 0.0f).withEndAction(() -> {
                        if (keyboardVisible) {
                            actionButtonContainer.setVisibility(View.INVISIBLE);
                        }
                    }).start();
                }

                @Override
                protected void onTransitionEnd() {

                }

                @Override
                protected void onPanTranslationUpdate(float y, float progress, boolean keyboardVisible) {
                    if (getParentLayout() != null && getParentLayout().isPreviewOpenAnimationInProgress()) {
                        return;
                    }
                }

                @Override
                protected boolean applyTranslation() {
                    return false;
                }

                @Override
                protected boolean heightAnimationEnabled() {
                    INavigationLayout actionBarLayout = getParentLayout();
                    if (inPreviewMode || AndroidUtilities.isTablet() || inBubbleMode || AndroidUtilities.isInMultiwindow || actionBarLayout == null) {
                        return false;
                    }
                    return true;
                }
            };

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                adjustPanLayoutHelper.onAttach();
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                adjustPanLayoutHelper.onDetach();
            }
        };
        rootLayout.setFocusableInTouchMode(true);
        rootLayout.requestFocus();

        enableReactionsCell = new TextCheckCell(context);
        enableReactionsCell.setHeight(56);
        ThemeBind.backgroundColor(enableReactionsCell, enableReactionsCell.isChecked() ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked);
        enableReactionsCell.setTypeface(AndroidUtilities.bold());
        enableReactionsCell.setColors(Theme.key_windowBackgroundCheckText, Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
        enableReactionsCell.setOnClickListener(v -> {
            if (enableReactionsCell.isChecked() && paidCheckCell != null && paidCheckCell.isChecked()) {
                toggleStarsEnabled();
            }
            setCheckedEnableReactionCell(enableReactionsCell.isChecked() ? SELECT_TYPE_NONE : SELECT_TYPE_SOME, enableReactionsCell.isChecked() ? false : paid, true);
        });
        reactionsInfoCell = new TextInfoPrivacyCell(context, 12, resourceProvider);
        ThemeBind.textColor(reactionsInfoCell.getTextView(), Theme.key_windowBackgroundWhiteGrayText4);
        reactionsInfoCell.setTopPadding(12);
        reactionsInfoCell.setBottomPadding(16);
        reactionsInfoCell.setText(LocaleController.getString(R.string.ReactionAddEmojiFromAnyPack));

        HeaderCell headerCell = new HeaderCell(context);
        headerCell.setText(LocaleController.getString(R.string.AvailableReactions));
        ThemeBind.backgroundColor(headerCell, Theme.key_windowBackgroundWhite);
        headerCell.setTextSize(15);
        headerCell.setTopMargin(14);

        addReactionSettingsView(headerCell, false);

        editText = new CustomReactionEditText(context, getResourceProvider(), maxReactionsCount) {
            @Override
            protected void onLineCountChanged(int oldLineCount, int newLineCount) {
                if (newLineCount > oldLineCount) {
                    scrollView.smoothScrollBy(0, dp(30));
                }
            }

            @Override
            public boolean onTextContextMenuItem(int id) {
                if (id == R.id.menu_delete || id == android.R.id.cut) {
                    return deleteSelectedEmojis();
                } else if (id == android.R.id.paste || id == android.R.id.copy) {
                    return false;
                }
                return super.onTextContextMenuItem(id);
            }
        };
        editText.setOnFocused(this::showKeyboard);

        addReactionSettingsView(editText, false);

        TextInfoPrivacyCell infoCell2 = new TextInfoPrivacyCell(context, 12, resourceProvider);
        ThemeBind.textColor(infoCell2.getTextView(), Theme.key_windowBackgroundWhiteGrayText4);
        infoCell2.setText(AndroidUtilities.replaceSingleTag(
                getString(R.string.ReactionCreateOwnPack),
                Theme.key_chat_messageLinkIn, 0,
                () -> Browser.openUrl(getContext(), "https://t.me/stickers"),
                getResourceProvider()
        ));
        addReactionSettingsView(infoCell2, true);

        HeaderCell headerCell1 = new HeaderCell(context, resourceProvider);
        headerCell1.setText(getString(R.string.MaximumReactionsHeader));
        addReactionSettingsView(headerCell1, false);

        slideView = new SlideIntChooseView(context, resourceProvider);
        if (info instanceof TLRPC.TL_chatFull ? (info.flags & 1048576) != 0 : (info.flags2 & 8192) != 0) {
            currentReactionsCount = reactionsCount = info.reactions_limit;
        } else {
            currentReactionsCount = reactionsCount = getMessagesController().reactionsUniqMax;
        }
        slideView.set(reactionsCount, SlideIntChooseView.Options.make(0, "MaximumReactionsValue", 1, getMessagesController().reactionsUniqMax), value -> {
            reactionsCount = value;
        });
        addReactionSettingsView(slideView, false);
        TextInfoPrivacyCell infoCell3 = new TextInfoPrivacyCell(context, 12, resourceProvider);
        infoCell3.setTopPadding(12);
        infoCell3.setBottomPadding(16);
        infoCell3.setText(LocaleController.getString(R.string.MaximumReactionsInfo));
        addReactionSettingsView(infoCell3, true);

        if (info.paid_media_allowed) {
            paidCheckCell = new TextCheckCell(context);
            paidCheckCell.setTextAndCheck(LocaleController.getString(R.string.ChannelEnablePaidReactions), false, false);
            addReactionSettingsView(paidCheckCell, false);
            paidCheckCell.setOnClickListener(v -> {
                toggleStarsEnabled();
            });

            TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context, 12, resourceProvider);
            ThemeBind.textColor(infoCell.getTextView(), Theme.key_windowBackgroundWhiteGrayText4);
            infoCell.setTopPadding(12);
            infoCell.setBottomPadding(70);
            infoCell.setText(AndroidUtilities.withLearnMore(LocaleController.getString(R.string.ChannelEnablePaidReactionsInfo), () -> {
                Browser.openUrl(getContext(), LocaleController.getString(R.string.ChannelEnablePaidReactionsInfoLink));
            }));
            addReactionSettingsView(infoCell, true);
        } else {
            infoCell3.setBottomPadding(70);
        }

        actionButtonContainer = new FrameLayout(context);

        actionButtonContainerGradient = new ImageView(context);
        actionButtonContainerGradient.setImageResource(R.drawable.gradient_bottom);
        actionButtonContainerGradient.setScaleType(ImageView.ScaleType.FIT_XY);
        ThemeBind.colorFilter(actionButtonContainerGradient, Theme.key_windowBackgroundGray, resourceProvider, PorterDuff.Mode.SRC_ATOP);
        actionButtonContainer.addView(actionButtonContainerGradient, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        actionButton = new UpdateReactionsButton(context, getResourceProvider());
        actionButton.setRound();
        actionButton.setDefaultState();
        actionButton.setOnClickListener(v -> {
            if (actionButton.isLoading()) {
                return;
            }

            if (boostsStatus != null && boostsStatus.level < selectedCustomReactions) {
                ReactionsUtils.showLimitReachedDialogForReactions(-chatId, selectedCustomReactions, boostsStatus);
                return;
            }

            Boolean allowPaid = null;
            if (paidCheckCell != null && info.paid_media_allowed) {
                allowPaid = paidCheckCell.isChecked();
            }

            actionButton.setLoading(true);
            getMessagesController().setCustomChatReactions(chatId, selectedType, grabReactions(false), currentReactionsCount = reactionsCount, allowPaid, error -> {
                if (isFinishing()) {
                    return;
                }
                actionButton.setLoading(false);
                if (error.text.equals("CHAT_NOT_MODIFIED")) {
                    finishFragment();
                } else {
                    Runnable runnable = () -> {
                        if (boostsStatus != null && error.text.equals("BOOSTS_REQUIRED")) {
                            ReactionsUtils.showLimitReachedDialogForReactions(-chatId, selectedCustomReactions, boostsStatus);
                        } else {
                            String errText = error.text;
                            if (error.text.equals("REACTIONS_TOO_MANY")) {
                                errText = formatPluralString("ReactionMaxCountError", maxReactionsCount);
                            }
                            BulletinFactory.of(ChatCustomReactionsEditActivity.this).createErrorBulletin(errText).show();
                        }
                    };
                    AndroidUtilities.runOnUIThread(runnable, boostsStatus == null ? 200 : 0);
                }
            }, this::finishFragment);
        });
        rootLayout.addView(scrollView);
        rootLayout.addView(actionButtonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 13 + 48 + 13, Gravity.BOTTOM, 0, 0, 0, 0));
        actionButtonContainer.addView(actionButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 13, 13, 13, 13));
        updateActionButtonInsets();

        bottomDialogLayout = new FrameLayout(context) {
            {
                setWillNotDraw(false);
            }

            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                super.onDraw(canvas);
                if (bottomInset > 0) {
                    canvas.drawRect(
                            0.0f, getHeight() - bottomInset,
                            getWidth(), getHeight(),
                            bottomInsetPaint
                    );
                }
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (emojiKeyboardVisible && changed) {
                    //support screen rotation
                    setEmojiKeyboardProgress(emojiKeyboardAnimator.getFloatValue());
//                    scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                }
            }
        };
        bottomDialogLayout.setVisibility(View.INVISIBLE);
        rootLayout.addView(bottomDialogLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        emojiKeyboardAnimator = new BoolAnimator(0, new FactorAnimator.Target() {
            @Override
            public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
                setEmojiKeyboardProgress(factor);
            }

            @Override
            public void onFactorChangeFinished(int id, float finalFactor, FactorAnimator callee) {
                bottomDialogLayout.setLayerType(View.LAYER_TYPE_NONE, null);
                if (finalFactor == 0.0f && !emojiKeyboardVisible) {
                    bottomDialogLayout.setVisibility(View.INVISIBLE);
                    if (isClearFocusNotWorking()) {
                        scrollView.setFocusableInTouchMode(false);
                    }
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
            }
        }, CubicBezierInterpolator.DEFAULT, 350);
        updateBottomInsets();
        updateListViewParams();

        int i = 0;
        if (info.available_reactions instanceof TLRPC.TL_chatReactionsAll) {
            SpannableStringBuilder editable = new SpannableStringBuilder();
            for (TLRPC.TL_availableReaction availableReaction : allAvailableReactions) {
                addReactionToEditText(availableReaction, selectedEmojisMap, selectedEmojisIds, editable, selectAnimatedEmojiDialog, editText.getFontMetricsInt());
                i++;
                if (i >= maxReactionsCount) {
                    break;
                }
            }
            editText.append(editable);
            setCheckedEnableReactionCell(SELECT_TYPE_ALL, paid, false);
        } else if (info.available_reactions instanceof TLRPC.TL_chatReactionsSome) {
            TLRPC.TL_chatReactionsSome reactionsSome = (TLRPC.TL_chatReactionsSome) info.available_reactions;
            SpannableStringBuilder editable = new SpannableStringBuilder();
            for (TLRPC.Reaction reaction : reactionsSome.reactions) {
                if (reaction instanceof TLRPC.TL_reactionEmoji) {
                    TLRPC.TL_reactionEmoji reactionEmoji = ((TLRPC.TL_reactionEmoji) reaction);
                    TLRPC.TL_availableReaction availableReaction = getMediaDataController().getReactionsMap().get(reactionEmoji.emoticon);
                    if (availableReaction == null) {
                        continue;
                    }
                    addReactionToEditText(availableReaction, selectedEmojisMap, selectedEmojisIds, editable, selectAnimatedEmojiDialog, editText.getFontMetricsInt());
                    i++;
                } else if (reaction instanceof TLRPC.TL_reactionCustomEmoji) {
                    addReactionToEditText((TLRPC.TL_reactionCustomEmoji) reaction, selectedEmojisMap, selectedEmojisIds, editable, selectAnimatedEmojiDialog, editText.getFontMetricsInt());
                    i++;
                }
                if (i >= maxReactionsCount) {
                    break;
                }
            }
            editText.append(editable);
            setCheckedEnableReactionCell(SELECT_TYPE_SOME, paid, false);
        } else if (info.available_reactions instanceof TLRPC.TL_chatReactionsNone && (info != null && info.paid_media_allowed && info.paid_reactions_available)) {
            setCheckedEnableReactionCell(SELECT_TYPE_NONE, paid, false);
        } else if (info.available_reactions instanceof TLRPC.TL_chatReactionsNone) {
            SpannableStringBuilder editable = new SpannableStringBuilder();
            for (TLRPC.TL_availableReaction availableReaction : allAvailableReactions) {
                addReactionToEditText(availableReaction, selectedEmojisMap, selectedEmojisIds, editable, selectAnimatedEmojiDialog, editText.getFontMetricsInt());
                i++;
                if (i >= maxReactionsCount) {
                    break;
                }
            }
            editText.append(editable);
            setCheckedEnableReactionCell(SELECT_TYPE_NONE, paid, false);
        }

        enableReactionsCell.setTextAndCheck(LocaleController.getString(R.string.EnableReactions), selectedType != SELECT_TYPE_NONE || paid, false);
        editText.addReactionsSpan();

        if (info.paid_media_allowed && info.paid_reactions_available) {
            toggleStarsEnabled();
        }
        initialSelectedEmojis.putAll(selectedEmojisMap);
        initialPaid = paid;

        fragmentView = rootLayout;
        return rootLayout;
    }

    @Override
    public void onThemeColorsChanged() {
        try {
            super.onThemeColorsChanged();
            if (enableReactionsCell != null && enableReactionsCell.getBackground() instanceof ColorDrawable) {
                enableReactionsCell.setBackgroundColor(Theme.getColor(enableReactionsCell.isChecked() ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void initSelectAnimatedEmojiDialog() {
        if (selectAnimatedEmojiDialog != null) {
            return;
        }
        int accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider());
        selectAnimatedEmojiDialog = new SelectAnimatedEmojiDialog(this, getContext(), false, null, SelectAnimatedEmojiDialog.TYPE_CHAT_REACTIONS, false, getResourceProvider(), 16, accentColor) {

            private boolean firstLayout = true;

            {
                setDrawBackground(false);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (firstLayout) {
                    firstLayout = false;
                    selectAnimatedEmojiDialog.onShow(null);
                }
            }

            protected void onEmojiSelected(View view, Long documentId, TLRPC.Document document, TL_stars.TL_starGiftUnique gift, Integer until) {
                if (selectedEmojisMap.containsKey(documentId)) {
                    selectedEmojisIds.remove(documentId);
                    AnimatedEmojiSpan removedSpan = selectedEmojisMap.remove(documentId);
                    removedSpan.setRemoved(() -> {
                        SpannableStringBuilder spanned = new SpannableStringBuilder(editText.getText());
                        AnimatedEmojiSpan[] spans = spanned.getSpans(0, spanned.length(), AnimatedEmojiSpan.class);
                        for (AnimatedEmojiSpan span : spans) {
                            if (span == removedSpan) {
                                int selectionEnd = editText.getEditTextSelectionEnd();
                                int spanEnd = spanned.getSpanEnd(span);
                                int spanStart = spanned.getSpanStart(span);
                                editText.getText().delete(spanStart, spanEnd);
                                int spanDiff = spanEnd - spanStart;
                                editText.setSelection(spanEnd <= selectionEnd ? selectionEnd - spanDiff : selectionEnd);
                                break;
                            }
                        }
                    });
                    animateChangesInNextRows(removedSpan);
                    selectAnimatedEmojiDialog.setMultiSelected(documentId, true);
                    checkMaxCustomReactions(false);
                } else {
                    if (selectedEmojisMap.size() - (selectedEmojisMap.containsKey(-1L) ? 1 : 0) >= maxReactionsCount) {
                        BulletinFactory.of(ChatCustomReactionsEditActivity.this).createErrorBulletin(formatPluralString("ReactionMaxCountError", maxReactionsCount)).show();
                        return;
                    }
                    try {
                        int selectionEnd = editText.getEditTextSelectionEnd();
                        SpannableString spannable = new SpannableString("b");
                        AnimatedEmojiSpan span = createAnimatedEmojiSpan(document, documentId, editText.getFontMetricsInt());
                        span.cacheType = AnimatedEmojiDrawable.getCacheTypeForEnterView();
                        span.setAdded();
                        selectedEmojisIds.add(MathUtils.clamp(selectionEnd, 0, selectedEmojisIds.size()), documentId);
                        selectedEmojisMap.put(documentId, span);
                        spannable.setSpan(span, 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        editText.getText().insert(selectionEnd, spannable);
                        editText.setSelection(selectionEnd + spannable.length());
                        selectAnimatedEmojiDialog.setMultiSelected(documentId, true);
                        checkMaxCustomReactions(true);
                        animateChangesInNextRows(span);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        };

        selectAnimatedEmojiDialog.setAnimationsEnabled(false);
        selectAnimatedEmojiDialog.setClipChildren(false);
        ThemeBind.backgroundColor(selectAnimatedEmojiDialog, Theme.key_windowBackgroundWhite);
        bottomDialogLayout.addView(selectAnimatedEmojiDialog, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        backSpaceButtonView = new BackSpaceButtonView(getContext(), getResourceProvider());
        backSpaceButtonView.setOnBackspace(isFast -> {
            if (deleteSelectedEmojis()) {
                return;
            }
            int selectionEnd = editText.getEditTextSelectionEnd();
            SpannableStringBuilder spanned = new SpannableStringBuilder(editText.getText());
            AnimatedEmojiSpan[] spans = spanned.getSpans(0, spanned.length(), AnimatedEmojiSpan.class);
            for (AnimatedEmojiSpan span : spans) {
                int removedSpanEnd = spanned.getSpanEnd(span);
                if (removedSpanEnd == selectionEnd) {
                    selectedEmojisMap.remove(span.documentId);
                    selectedEmojisIds.remove(span.documentId);
                    selectAnimatedEmojiDialog.unselect(span.documentId);
                    if (span.documentId == -1 && paidCheckCell != null) {
                        paidCheckCell.setChecked(false);
                        editText.setMaxLength(maxReactionsCount);
                    }
                    if (isFast) {
                        editText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                        AndroidUtilities.cancelRunOnUIThread(checkAfterFastDeleteRunnable);
                        AndroidUtilities.runOnUIThread(checkAfterFastDeleteRunnable, 350);
                    } else {
                        span.setRemoved(() -> {
                            Editable editable = editText.getText();
                            int spanStart = editable.getSpanStart(span);
                            int spanEnd = editable.getSpanEnd(span);
                            int spanDiff = spanEnd - spanStart;
                            if (spanStart == -1 || spanEnd == -1) {
                                return;
                            }
                            editText.getText().delete(spanStart, spanEnd);
                            editText.setSelection(Math.min(selectionEnd - spanDiff, editText.getText().length()));
                        });
                        animateChangesInNextRows(span);
                        checkMaxCustomReactions(false);
                    }
                    break;
                }
            }
        });
        bottomDialogLayout.addView(backSpaceButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 8, 8));
        for (Long selectedEmojisId : selectedEmojisIds) {
            selectAnimatedEmojiDialog.setMultiSelected(selectedEmojisId, false);
        }
    }

    private void animateChangesInNextRows(AnimatedEmojiSpan actionSpan) {
        Editable editable = editText.getText();
        Layout layout = editText.getLayout();
        int deleteLine = layout.getLineForOffset(editable.getSpanStart(actionSpan));
        int nextLine = deleteLine + 1;
        if (nextLine < layout.getLineCount()) {
            int newLineStart = layout.getLineStart(nextLine);
            AnimatedEmojiSpan[] spans = editable.getSpans(newLineStart, editable.length(), AnimatedEmojiSpan.class);
            for (AnimatedEmojiSpan span : spans) {
                span.setAnimateChanges();
            }
        }
    }

    private boolean deleteSelectedEmojis() {
        int selectionEnd = editText.getEditTextSelectionEnd();
        int selectionStart = editText.getEditTextSelectionStart();
        SpannableStringBuilder spanned = new SpannableStringBuilder(editText.getText());
        if (editText.hasSelection()) {
            AnimatedEmojiSpan[] spans = spanned.getSpans(selectionStart, selectionEnd, AnimatedEmojiSpan.class);
            for (AnimatedEmojiSpan span : spans) {
                selectedEmojisMap.remove(span.documentId);
                selectedEmojisIds.remove(span.documentId);
                selectAnimatedEmojiDialog.unselect(span.documentId);
            }
            editText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            checkMaxCustomReactions(false);
            return true;
        }
        return false;
    }

    @Override
    public boolean canBeginSlide() {
        if (checkChangesBeforeExit(true)) {
            return false;
        }
        return super.canBeginSlide();
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (isOpen && selectedType != SELECT_TYPE_NONE) {
            fragmentView.setFocusableInTouchMode(true);
            fragmentView.requestFocus();
            editText.setFocusableInTouchMode(true);
        }
        if (isOpen) {
            allowShowEmojiKeyboard = true;
        }
        if (isOpen && !backward) {
            initSelectAnimatedEmojiDialog();
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512), 200);
        }
    }

    private void setCheckedEnableReactionCell(int selectType, boolean paid, boolean animated) {
        if (selectedType == selectType && this.paid == paid) {
            return;
        }
        this.paid = paid;

        boolean checked = selectType == SELECT_TYPE_SOME || selectType == SELECT_TYPE_ALL || paid;
        enableReactionsCell.setChecked(checked);
        int clr = Theme.getColor(checked ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked);
        if (animated) {
            if (checked) {
                enableReactionsCell.setBackgroundColorAnimated(true, clr);
            } else {
                enableReactionsCell.setBackgroundColorAnimatedReverse(clr);
            }
        } else {
            ThemeBind.backgroundColor(enableReactionsCell, checked ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked);
        }

        this.selectedType = selectType;

        if (selectType == SELECT_TYPE_SOME || selectType == SELECT_TYPE_ALL || paid) {
            actionButtonContainer.setVisibility(View.VISIBLE);
            scrollView.adapter.update(animated);
            if (animated) {
                actionButtonContainer.animate().setListener(null).cancel();
                actionButtonContainer.animate().alpha(1f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                scrollView.post(() -> editText.setFocusableInTouchMode(true));
                if (selectedEmojisMap.isEmpty()) {
                    selectAnimatedEmojiDialog.clearSelectedDocuments();
                    editText.setText("");
                    int i = 0;
                    SpannableStringBuilder editable = new SpannableStringBuilder();
                    for (TLRPC.TL_availableReaction availableReaction : allAvailableReactions) {
                        addReactionToEditText(availableReaction, selectedEmojisMap, selectedEmojisIds, editable, selectAnimatedEmojiDialog, editText.getFontMetricsInt());
                        i++;
                        if (i >= maxReactionsCount) {
                            break;
                        }
                    }
                    editText.append(editable);
                    editText.addReactionsSpan();
                    selectAnimatedEmojiDialog.notifyDataSetChanged();
                    checkMaxCustomReactions(false);
                }
            }
        } else {
            if (animated) {
                closeKeyboard();
                actionButtonContainer.animate().setListener(null).cancel();
                actionButtonContainer.animate().alpha(0f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        actionButtonContainer.setVisibility(View.INVISIBLE);
                    }
                }).start();
                editText.setFocusableInTouchMode(false);
                scrollView.adapter.update(true);
            } else {
                actionButtonContainer.setVisibility(View.INVISIBLE);
                editText.setFocusableInTouchMode(false);
                scrollView.adapter.update(false);
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        AndroidUtilities.cancelRunOnUIThread(checkAfterFastDeleteRunnable);
        if (selectedType == SELECT_TYPE_NONE && reactionsCount != currentReactionsCount) {
            getMessagesController().setCustomChatReactions(chatId, selectedType, grabReactions(false), reactionsCount, null, null, null);
        }
        getNotificationCenter().removeObserver(this, NotificationCenter.reactionsDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.dialogDeleted);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isPaused) {
            isPaused = false;
            editText.setFocusable(true);
            editText.setFocusableInTouchMode(true);
            if (emojiKeyboardVisible) {
                editText.removeReactionsSpan(false);
                AndroidUtilities.runOnUIThread(() -> editText.requestFocus(), 250);
            }
        }
    }

    @Override
    public void onPause() {
        isPaused = true;
        editText.setFocusable(false);
        super.onPause();
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (emojiKeyboardVisible) {
            if (invoked) closeKeyboard();
            return false;
        }
        if (checkChangesBeforeExit(invoked)) {
            return false;
        }
        return super.onBackPressed(invoked);
    }

    private boolean checkChangesBeforeExit(boolean invoked) {
        boolean hasChanges = !selectedEmojisMap.keySet().equals(initialSelectedEmojis.keySet());
        if (boostsStatus != null && boostsStatus.level < selectedCustomReactions) {
            hasChanges = false;
        }
        if (initialPaid != paid) {
            hasChanges = true;
        }
        if (invoked && hasChanges) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), getResourceProvider());
            builder.setTitle(getString("UnsavedChanges", R.string.UnsavedChanges));
            String text = getString("ReactionApplyChangesDialog", R.string.ReactionApplyChangesDialog);
            builder.setMessage(text);
            builder.setPositiveButton(getString("ApplyTheme", R.string.ApplyTheme), (dialogInterface, i) -> {
                actionButton.performClick();
            });
            builder.setNegativeButton(getString(R.string.Discard), (dialogInterface, i) -> finishFragment());
            builder.show();
        }
        return hasChanges;
    }

    private void checkMaxCustomReactions(boolean withToast) {
        if (boostsStatus == null) {
            return;
        }
        if (selectedType == SELECT_TYPE_ALL) {
            selectedType = SELECT_TYPE_SOME;
        }
        selectedCustomReactions = grabReactions(true).size();
        if (boostsStatus.level < selectedCustomReactions) {
            if (withToast) {
                CharSequence text = replaceTags(formatPluralString("ReactionReachLvlForReactionShort", selectedCustomReactions, selectedCustomReactions));
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.chats_infotip, text)
                        .show();
            }
            actionButton.setLvlRequiredState(selectedCustomReactions);
        } else {
            actionButton.removeLvlRequiredState();
        }
    }

    private List<TLRPC.Reaction> grabReactions(boolean onlyCustom) {
        List<TLRPC.Reaction> reactions = new ArrayList<>();
        List<TLRPC.Reaction> customReactions = new ArrayList<>();
        for (Long documentId : selectedEmojisIds) {
            if (documentId == -1) continue;

            boolean isReactionEmoji = false;
            for (TLRPC.TL_availableReaction availableReaction : allAvailableReactions) {
                if (documentId == availableReaction.activate_animation.id) {
                    TLRPC.TL_reactionEmoji emojiReaction = new TLRPC.TL_reactionEmoji();
                    emojiReaction.emoticon = availableReaction.reaction;
                    reactions.add(emojiReaction);
                    isReactionEmoji = true;
                    break;
                }
            }

            if (!isReactionEmoji) {
                TLRPC.TL_reactionCustomEmoji customEmoji = new TLRPC.TL_reactionCustomEmoji();
                customEmoji.document_id = documentId;
                reactions.add(customEmoji);
                customReactions.add(customEmoji);
            }
        }
        if (onlyCustom) {
            return customReactions;
        }
        return reactions;
    }

    private void showKeyboard() {
        if (!allowShowEmojiKeyboard) {
            return;
        }
        if (!emojiKeyboardVisible) {
            emojiKeyboardVisible = true;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
            bottomDialogLayout.setVisibility(View.VISIBLE);
            bottomDialogLayout.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            setEmojiKeyboardProgress(emojiKeyboardAnimator.getFloatValue());
            emojiKeyboardAnimator.setValue(true, true);
        }
    }

    private boolean closeKeyboard() {
        if (emojiKeyboardVisible) {
            emojiKeyboardVisible = false;
            if (isClearFocusNotWorking()) {
                scrollView.setFocusableInTouchMode(true);
                scrollView.requestFocus();
            } else {
                editText.clearFocus();
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
            bottomDialogLayout.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            emojiKeyboardAnimator.setValue(false, true);
            return true;
        }
        return false;
    }

    private boolean isClearFocusNotWorking() {
        return Build.MODEL.toLowerCase().startsWith("zte") && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Nullable
    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return scrollView;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        bottomInset = bottom;
        updateActionButtonInsets();
        updateBottomInsets();
        updateListViewParams();
    }

    private void updateListViewParams() {
        if (scrollView == null) return;

        final float keyboardProgress = emojiKeyboardAnimator != null ? emojiKeyboardAnimator.getFloatValue() : 0.0f;
        final int buttonHeight = dp(12 + 48 + 12);
        final int keyboardHeight = Math.max(0, bottomDialogLayout.getMeasuredHeight() - mSystemInsets.bottom) + dp(12);
        final int paddingBottom = lerp(buttonHeight, keyboardHeight, keyboardProgress);

        BaseFragmentSimpleGlass.setListViewParams(
                scrollView,
                mSystemInsets.top,
                mSystemInsets.bottom,
                ActionBar.getCurrentActionBarHeight(),
                paddingBottom
        );
    }

    private void updateActionButtonInsets() {
        if (actionButtonContainer == null || actionButton == null) return;
        final float keyboardProgress = emojiKeyboardAnimator != null ? emojiKeyboardAnimator.getFloatValue() : 0.0f;
        final int buttonBottomInset = Math.round(bottomInset * (1.0f - keyboardProgress));

        final ViewGroup.LayoutParams containerLayoutParams = actionButtonContainer.getLayoutParams();
        containerLayoutParams.height = dp(13 + 48 + 13) + buttonBottomInset;
        actionButtonContainer.setLayoutParams(containerLayoutParams);

        final FrameLayout.LayoutParams buttonLayoutParams =
                (FrameLayout.LayoutParams) actionButton.getLayoutParams();
        buttonLayoutParams.bottomMargin = dp(13) + buttonBottomInset;
        actionButton.setLayoutParams(buttonLayoutParams);
    }

    private void setEmojiKeyboardProgress(float progress) {
        final float keyboardProgress = MathUtils.clamp(progress, 0.0f, 1.0f);
        if (bottomDialogLayout != null && actionButtonContainer != null) {
            bottomDialogLayout.setTranslationY((1.0f - keyboardProgress) * bottomDialogLayout.getMeasuredHeight());
            actionButtonContainer.setTranslationY(-keyboardProgress * bottomDialogLayout.getMeasuredHeight());
        }
        updateActionButtonInsets();
        updateListViewParams();
    }

    private void updateBottomInsets() {
        if (bottomDialogLayout != null) {
            bottomInsetPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
            bottomDialogLayout.setPadding(0, 0, 0, bottomInset);
            bottomDialogLayout.invalidate();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.dialogDeleted) {
            long dialogId = (long) args[0];
            if (dialogId == -this.chatId) {
                if (parentLayout != null && parentLayout.getLastFragment() == this) {
                    finishFragment();
                } else {
                    removeSelfFromStack();
                }
            }
        }
    }

    public void toggleStarsEnabled() {
        if (paidCheckCell.isChecked()) {
            paidCheckCell.setChecked(false);
            selectedEmojisIds.remove(-1L);
            AnimatedEmojiSpan removedSpan = selectedEmojisMap.remove(-1L);
            if (removedSpan != null) {
                removedSpan.setRemoved(() -> {
                    SpannableStringBuilder spanned = new SpannableStringBuilder(editText.getText());
                    AnimatedEmojiSpan[] spans = spanned.getSpans(0, spanned.length(), AnimatedEmojiSpan.class);
                    for (AnimatedEmojiSpan span : spans) {
                        if (span == removedSpan) {
                            int selectionEnd = editText.getEditTextSelectionEnd();
                            int spanEnd = spanned.getSpanEnd(span);
                            int spanStart = spanned.getSpanStart(span);
                            editText.getText().delete(spanStart, spanEnd);
                            int spanDiff = spanEnd - spanStart;
                            editText.setSelection(spanEnd <= selectionEnd ? selectionEnd - spanDiff : selectionEnd);
                            break;
                        }
                    }
                });
            }
            animateChangesInNextRows(removedSpan);
            selectAnimatedEmojiDialog.setMultiSelected(-1L, true);
            checkMaxCustomReactions(false);
            editText.setMaxLength(maxReactionsCount);
            setCheckedEnableReactionCell(selectedType, paid, true);
        } else {
            paidCheckCell.setChecked(true);
            try {
                editText.setMaxLength(maxReactionsCount + 1);
                SpannableString spannable = new SpannableString("b");
                AnimatedEmojiSpan span = new AnimatedEmojiSpan(-1, null) {
                    private final Bitmap bitmap = Bitmap.createBitmap(dp(24), dp(24), Bitmap.Config.ARGB_8888);
                    {
                        final Drawable drawable = getContext().getResources().getDrawable(R.drawable.star_small_inner).mutate();
                        drawable.setBounds(0, 0, dp(24), dp(24));
                        drawable.draw(new Canvas(bitmap));
                    }
                    @Override
                    public void draw(@NonNull Canvas canvas, CharSequence charSequence, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
                        super.draw(canvas, charSequence, start, end, x, top, y, bottom, paint);
                        canvas.save();
                        canvas.translate(x, (top + bottom) / 2f - dp(12));
                        canvas.scale(extraScale, extraScale, x + dp(12), dp(12));
                        canvas.drawBitmap(bitmap, 0, 0, null);
                        canvas.restore();
                    }
                    @Override
                    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
                        return super.getSize(paint, text, start, end, fm) + dp(5);
                    }
                };
                span.cacheType = AnimatedEmojiDrawable.getCacheTypeForEnterView();
                span.setAdded();
                selectedEmojisIds.add(0, -1L);
                selectedEmojisMap.put(-1L, span);
                spannable.setSpan(span, 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                editText.getText().insert(0, spannable);
                selectAnimatedEmojiDialog.setMultiSelected(-1L, true);
                checkMaxCustomReactions(true);
                animateChangesInNextRows(span);
            } catch (Exception e) {
                FileLog.e(e);
            }
            setCheckedEnableReactionCell(selectedType, true, true);
        }
        editText.updateAnimatedEmoji(true);
    }
}
