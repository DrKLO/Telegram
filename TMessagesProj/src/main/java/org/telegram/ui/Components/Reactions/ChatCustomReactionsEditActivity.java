package org.telegram.ui.Components.Reactions;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceTags;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Components.Reactions.ReactionsUtils.addReactionToEditText;
import static org.telegram.ui.Components.Reactions.ReactionsUtils.createAnimatedEmojiSpan;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.SlideIntChooseView;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.SelectAnimatedEmojiDialog;

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
    private LinearLayout switchLayout;
    private LinearLayout contentLayout;
    private CustomReactionEditText editText;
    private SlideIntChooseView slideView;
    private TextCheckCell paidCheckCell;
    private UpdateReactionsButton actionButton;
    private ScrollView scrollView;

    private final HashMap<Long, AnimatedEmojiSpan> selectedEmojisMap = new LinkedHashMap<>();
    private final List<Long> selectedEmojisIds = new ArrayList<>();
    private final HashMap<Long, AnimatedEmojiSpan> initialSelectedEmojis = new LinkedHashMap<>();
    private final List<TLRPC.TL_availableReaction> allAvailableReactions = new ArrayList<>();
    private boolean initialPaid;

    private final int maxReactionsCount = getMessagesController().boostsChannelLevelMax;
    private boolean emojiKeyboardVisible = false;
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
        getNotificationCenter().addObserver(this, NotificationCenter.reactionsDidLoad);
        allAvailableReactions.addAll(getMediaDataController().getEnabledReactionsList());
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
        return super.onFragmentCreate();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(R.string.Reactions));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (!checkChangesBeforeExit()) {
                        finishFragment();
                    }
                }
            }
        });

        FrameLayout rootLayout = new FrameLayout(context);
        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);

        contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);

        scrollView.addView(contentLayout);

        enableReactionsCell = new TextCheckCell(context);
        enableReactionsCell.setHeight(56);
        enableReactionsCell.setBackgroundColor(Theme.getColor(enableReactionsCell.isChecked() ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
        enableReactionsCell.setTypeface(AndroidUtilities.bold());
        enableReactionsCell.setColors(Theme.key_windowBackgroundCheckText, Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
        enableReactionsCell.setOnClickListener(v -> {
            if (enableReactionsCell.isChecked() && paidCheckCell != null && paidCheckCell.isChecked()) {
                toggleStarsEnabled();
            }
            setCheckedEnableReactionCell(enableReactionsCell.isChecked() ? SELECT_TYPE_NONE : SELECT_TYPE_SOME, enableReactionsCell.isChecked() ? false : paid, true);
        });
        contentLayout.addView(enableReactionsCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context);
        infoCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        infoCell.setTopPadding(12);
        infoCell.setBottomPadding(16);
        infoCell.setText(LocaleController.getString(R.string.ReactionAddEmojiFromAnyPack));
        contentLayout.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        HeaderCell headerCell = new HeaderCell(context);
        headerCell.setText(LocaleController.getString(R.string.AvailableReactions));
        headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        headerCell.setTextSize(15);
        headerCell.setTopMargin(14);

        switchLayout = new LinearLayout(context);
        switchLayout.setOrientation(LinearLayout.VERTICAL);

        contentLayout.addView(switchLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        switchLayout.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

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

        switchLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LayoutTransition layoutTransition = new LayoutTransition();
        layoutTransition.setDuration(200);
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
        switchLayout.setLayoutTransition(layoutTransition);

        TextInfoPrivacyCell infoCell2 = new TextInfoPrivacyCell(context);
        infoCell2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        infoCell2.setText(AndroidUtilities.replaceSingleTag(
                getString(R.string.ReactionCreateOwnPack),
                Theme.key_chat_messageLinkIn, 0,
                () -> Browser.openUrl(getContext(), "https://t.me/stickers"),
                getResourceProvider()
        ));
        switchLayout.addView(infoCell2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        HeaderCell headerCell1 = new HeaderCell(context, resourceProvider);
        headerCell1.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        headerCell1.setText(getString(R.string.MaximumReactionsHeader));
        switchLayout.addView(headerCell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        slideView = new SlideIntChooseView(context, resourceProvider);
        slideView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        if (info instanceof TLRPC.TL_chatFull ? (info.flags & 1048576) != 0 : (info.flags2 & 8192) != 0) {
            currentReactionsCount = reactionsCount = info.reactions_limit;
        } else {
            currentReactionsCount = reactionsCount = getMessagesController().reactionsUniqMax;
        }
        slideView.set(reactionsCount, SlideIntChooseView.Options.make(0, "MaximumReactionsValue", 1, getMessagesController().reactionsUniqMax), value -> {
            reactionsCount = value;
        });
        switchLayout.addView(slideView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextInfoPrivacyCell infoCell3 = new TextInfoPrivacyCell(context);
        infoCell3.setTopPadding(12);
        infoCell3.setBottomPadding(16);
        infoCell3.setText(LocaleController.getString(R.string.MaximumReactionsInfo));
        switchLayout.addView(infoCell3, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (info.paid_media_allowed) {
            paidCheckCell = new TextCheckCell(context);
            paidCheckCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            paidCheckCell.setTextAndCheck(LocaleController.getString(R.string.ChannelEnablePaidReactions), false, false);
            switchLayout.addView(paidCheckCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            paidCheckCell.setOnClickListener(v -> {
                toggleStarsEnabled();
            });

            infoCell = new TextInfoPrivacyCell(context);
            infoCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
            infoCell.setTopPadding(12);
            infoCell.setBottomPadding(70);
            infoCell.setText(AndroidUtilities.withLearnMore(LocaleController.getString(R.string.ChannelEnablePaidReactionsInfo), () -> {
                Browser.openUrl(getContext(), LocaleController.getString(R.string.ChannelEnablePaidReactionsInfoLink));
            }));
            switchLayout.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            infoCell3.setBottomPadding(70);
        }

        actionButton = new UpdateReactionsButton(context, getResourceProvider());
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
        rootLayout.addView(actionButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 13, 0, 13, 13));
        rootLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        bottomDialogLayout = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (emojiKeyboardVisible && changed) {
                    //support screen rotation
                    actionButton.setTranslationY(-bottomDialogLayout.getMeasuredHeight());
                    updateScrollViewMarginBottom(bottomDialogLayout.getMeasuredHeight());
                    scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                }
            }
        };

        bottomDialogLayout.setVisibility(View.INVISIBLE);
        rootLayout.addView(bottomDialogLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

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

            protected void onEmojiSelected(View view, Long documentId, TLRPC.Document document, Integer until) {
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
                        selectedEmojisIds.add(selectionEnd, documentId);
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
        selectAnimatedEmojiDialog.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
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
        if (checkChangesBeforeExit()) {
            return false;
        }
        return super.canBeginSlide();
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (isOpen && selectedType != SELECT_TYPE_NONE) {
            editText.setFocusableInTouchMode(true);
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
            enableReactionsCell.setBackgroundColor(clr);
        }

        this.selectedType = selectType;

        if (selectType == SELECT_TYPE_SOME || selectType == SELECT_TYPE_ALL || paid) {
            switchLayout.setVisibility(View.VISIBLE);
            actionButton.setVisibility(View.VISIBLE);
            if (animated) {
                actionButton.animate().setListener(null).cancel();
                switchLayout.animate().setListener(null).cancel();
                switchLayout.animate().alpha(1f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        editText.setFocusableInTouchMode(true);
                    }
                }).start();
                actionButton.animate().alpha(1f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
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
                actionButton.animate().setListener(null).cancel();
                switchLayout.animate().setListener(null).cancel();
                actionButton.animate().alpha(0f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        actionButton.setVisibility(View.INVISIBLE);
                    }
                }).start();
                switchLayout.animate().alpha(0f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        editText.setFocusableInTouchMode(false);
                        switchLayout.setVisibility(View.INVISIBLE);
                    }
                }).start();
            } else {
                switchLayout.setVisibility(View.INVISIBLE);
                actionButton.setVisibility(View.INVISIBLE);
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
    public boolean onBackPressed() {
        if (closeKeyboard()) {
            return false;
        }
        if (checkChangesBeforeExit()) {
            return false;
        }
        return super.onBackPressed();
    }

    private boolean checkChangesBeforeExit() {
        boolean hasChanges = !selectedEmojisMap.keySet().equals(initialSelectedEmojis.keySet());
        if (boostsStatus != null && boostsStatus.level < selectedCustomReactions) {
            hasChanges = false;
        }
        if (initialPaid != paid) {
            hasChanges = true;
        }
        if (hasChanges) {
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
        if (!emojiKeyboardVisible) {
            emojiKeyboardVisible = true;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
            updateScrollViewMarginBottom(bottomDialogLayout.getMeasuredHeight());
            bottomDialogLayout.setVisibility(View.VISIBLE);
            bottomDialogLayout.setTranslationY(bottomDialogLayout.getMeasuredHeight());
            bottomDialogLayout.animate().setListener(null).cancel();
            bottomDialogLayout.animate().translationY(0).withLayer().setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).setUpdateListener(animation -> {
                actionButton.setTranslationY(-(float) animation.getAnimatedValue() * bottomDialogLayout.getMeasuredHeight());
            }).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
                    scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                }
            }).start();
        }
    }

    private boolean closeKeyboard() {
        if (emojiKeyboardVisible) {
            emojiKeyboardVisible = false;
            if (isClearFocusNotWorking()) {
                switchLayout.setFocusableInTouchMode(true);
                switchLayout.requestFocus();
            } else {
                editText.clearFocus();
            }
            updateScrollViewMarginBottom(0);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
            bottomDialogLayout.animate().setListener(null).cancel();
            bottomDialogLayout.animate().translationY(bottomDialogLayout.getMeasuredHeight()).setDuration(350).withLayer().setInterpolator(CubicBezierInterpolator.DEFAULT).setUpdateListener(animation -> {
                actionButton.setTranslationY(-(1f - (float) animation.getAnimatedValue()) * bottomDialogLayout.getMeasuredHeight());
            }).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
                    bottomDialogLayout.setVisibility(View.INVISIBLE);
                    if (isClearFocusNotWorking()) {
                        switchLayout.setFocusableInTouchMode(false);
                    }
                }
            }).start();
            return true;
        }
        return false;
    }

    private boolean isClearFocusNotWorking() {
        return Build.MODEL.toLowerCase().startsWith("zte") && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P;
    }

    private void updateScrollViewMarginBottom(int margin) {
        ViewGroup.MarginLayoutParams marginLayoutParams = ((ViewGroup.MarginLayoutParams) scrollView.getLayoutParams());
        marginLayoutParams.bottomMargin = margin;
        scrollView.setLayoutParams(marginLayoutParams);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {

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
