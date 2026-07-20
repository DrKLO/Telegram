package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AIEditorAlert;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.MessageSendPreview;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

import ru.noties.jlatexmath.JLatexMathDrawable;

public class ChatAttachAlertRichLayout extends ChatAttachAlert.AttachAlertLayout implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private final RichEditorListView listView;

    private RichEditorToolbar toolbar;
    private RichCommandSuggestions commandSuggestions;
    private EmojiView emojiView;
    private boolean emojiViewVisible;
    private boolean emojiSearchOpened;
    private int emojiPadding;
    private RichEditText emojiTargetEditText;
    private int emojiTargetSelection;
    private static final int TOOLBAR_BOTTOM_INSET = 8 + 44 + 8;
    private ItemOptions menu;

    private int currentItemTop;

    private static final int DEFAULT_ATTACH_LAYOUTS =
        (1 << ChatAttachAlert.LAYOUT_TYPE_PHOTO) |
        (1 << ChatAttachAlert.LAYOUT_TYPE_MUSIC) |
        (1 << ChatAttachAlert.LAYOUT_TYPE_LOCATION);

    public ChatAttachAlertRichLayout(
        ChatAttachAlert alert,
        Context context,
        int currentAccount,
        Theme.ResourcesProvider resourcesProvider
    ) {
        super(alert, context, resourcesProvider);
        this.currentAccount = currentAccount;

        occupyStatusBar = true;
        occupyNavigationBar = true;

        listView = new RichEditorListView(context, currentAccount, resourcesProvider, new RichEditorListView.Delegate() {
            @Override public ItemOptions makeMenu(View anchor) { return menu = ItemOptions.makeOptions(ChatAttachAlertRichLayout.this, resourcesProvider, anchor, false, false, true); }
            @Override public void onSelectionChanged() { updateFormattingPanel(); updateToolbarBlockType(); }
            @Override public void onContentChanged() { updateSendButtonLoading(); updateSendButtonLocked(); scheduleLimitCheck(); }
            @Override public void onHistoryChanged() { updateHistoryButtons(); updateSendButtonLocked(); }
            @Override public void onOpenAttachRequest(int a, int b) { openAttach(a, b); }
            @Override public void onOpenLocationRequest(BlockRow row) { openLocationPicker(row); }
            @Override public void onSlashSuggest(RichTextCell cell, String query) {
                if (commandSuggestions == null) {
                    commandSuggestions = new RichCommandSuggestions(anchor -> menu = ItemOptions.makeOptions(ChatAttachAlertRichLayout.this, resourcesProvider, anchor, false, false, true), resourcesProvider);
                }
                commandSuggestions.update(cell, query);
            }
            @Override public void onListScrolled(int dy) { parentAlert.updateLayout(ChatAttachAlertRichLayout.this, true, dy); updateToolbarTopOffset(); updateAttachRaise(); }
            @Override public void onListLayoutUpdated() {
                final int old = currentItemTop;
                if (getCurrentItemTop() != old) parentAlert.updateLayout(ChatAttachAlertRichLayout.this, true, 0);
                updateToolbarTopOffset();
                updateAttachRaise();
            }
            @Override public void makeEditTextFocusable(RichEditText et, boolean showKeyboard) { parentAlert.makeFocusable(et, showKeyboard); }
            @Override public void onReorderStart() { if (toolbar != null) toolbar.onReorderStart(); }
            @Override public boolean onReorderMove(float screenX, float screenY) { return toolbar != null && toolbar.onReorderMove(screenX, screenY); }
            @Override public void onReorderEnd() { if (toolbar != null) toolbar.onReorderEnd(); }
        });
        listView.setAllowTapAboveContent(false);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        addView(listView.getOverlayView(), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        listView.seedEmptyArticle();
        listView.resetHistoryBaseline();

        setFocusable(true);
        setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= 26) {
            setDefaultFocusHighlightEnabled(false);
        }
        setBackground(null);
        setForeground(null);

        toolbar = new RichEditorToolbar(context, toolbarDelegate);
        toolbar.setBackVisible(false);
        toolbar.setTopGradientVisible(false);
        updateSendButtonLocked();
        addView(toolbar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        updateHistoryButtons();
        updateToolbarBlockType();
        updateAttachButtons(false);

        getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> updateToolbarBlockType());
    }

    private final RichEditorToolbar.Delegate toolbarDelegate = new RichEditorToolbar.Delegate() {
        @Override public Theme.ResourcesProvider getResourcesProvider() { return resourcesProvider; }
        @Override public void onBack() {}
        @Override public void onUndo() { listView.undo(); }
        @Override public void onRedo() { listView.redo(); }
        @Override public void onEmoji() { toggleEmojiPopup(); }
        @Override public void onAi() {
            new RichAIComposeSheet(getContext(), currentAccount, resourcesProvider, richMessage -> listView.addRichMessage(richMessage)).show();
        }
        @Override public void onAttach() { listView.pendingMediaRow = null; openAttach(DEFAULT_ATTACH_LAYOUTS, 0); }
        @Override public void onSend() { sendSelectedItems(true, 0, 0, 0, false); }
        @Override public boolean onSendLongClick(View anchor) { return showSendPreview(anchor); }
        @Override public void onBlockButton(int flag, View anchor) { onBlockButtonClicked(flag, anchor); }
        @Override public void onFormatting(int styleFlag) { listView.onFormattingClicked(styleFlag); }
        @Override public void onLink() { listView.onLinkClicked(); }
        @Override public void onDate() { listView.onDateClicked(); }
        @Override public void onMath() { listView.onMathClicked(); }
        @Override public void onQuote() { listView.toggleQuoteOnSelection(); updateFormattingButtons(); }
        @Override public void onAiStyle() {
            final RichEditorListView.SelectionEdit edit = listView.beginSelectionEdit();
            if (edit == null) return;
            final TL_iv.RichMessage rich = edit.extractRichMessage();
            if (rich == null || rich.blocks.isEmpty()) return;
            new AIEditorAlert(getContext(), resourcesProvider)
                .setText(rich)
                .setOnUseRich(edit::replaceWith)
                .show();
        }
    };

    private void updateToolbarTopOffset() {
        if (toolbar == null) return;
        final int minTop = AndroidUtilities.statusBarHeight + (ActionBar.getCurrentActionBarHeight() - dp(44)) / 2 - dp(8);
        toolbar.setTopButtonsOffset(Math.max(minTop, firstItemTopRaw()));
    }

    private int firstItemTopRaw() {
        if (listView.getChildCount() <= 0) return listView.getPaddingTop();
        int top = Integer.MAX_VALUE;
        for (int i = 0; i < listView.getChildCount(); i++) {
            final View child = listView.getChildAt(i);
            if (listView.getChildAdapterPosition(child) >= 0 && child.getTop() < top) {
                top = child.getTop();
            }
        }
        return top == Integer.MAX_VALUE ? listView.getPaddingTop() : top;
    }

    private void updateHistoryButtons() {
        if (toolbar != null) {
            toolbar.setHistoryEnabled(listView.canUndo(), listView.canRedo());
        }
    }

    private boolean checkDiscard() {
        if (listView == null) return true;
        if (listView.hasAnyText()) {
            new AlertDialog.Builder(getContext(), resourcesProvider)
                .setTitle(getString(R.string.ArticleSaveDraftTitle))
                .setMessage(getString(R.string.ArticleSaveDraftMessage))
                .setNegativeButton(getString(R.string.Delete), (di, w) -> {
                    parentAlert.dismiss();
                })
                .setPositiveButton(getString(R.string.Save), (di, w) -> {
                    persistDraft();
                    parentAlert.dismiss();
                })
                .makeRed(AlertDialog.BUTTON_NEGATIVE)
                .show();
            return false;
        }
        return true;
    }

    private boolean persistDraft() {
        if (!(parentAlert.baseFragment instanceof ChatActivity)) return false;
        final ChatActivity chatActivity = (ChatActivity) parentAlert.baseFragment;
        if (!listView.canUndo()) return false;
        final TL_iv.RichMessage rich = listView.buildDraftRichMessage();
        AccountInstance.getInstance(currentAccount).getMediaDataController().saveDraft(
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
        if (chatActivity.getChatActivityEnterView() != null) {
            chatActivity.getChatActivityEnterView().setRichDraftPreview(rich);
        }
        return true;
    }

    @Override
    public boolean onDismissWithTouchOutside() {
        if (!checkDiscard()) {
            return false;
        }
        return super.onDismissWithTouchOutside();
    }

    @Override
    public boolean onDismiss() {
        if (listView != null) {
            listView.clearContent();
        }
        return super.onDismiss();
    }

    private void onBlockButtonClicked(int flag, View v) {
        final BlockRow row = listView.findFocusedRow();
        switch (flag) {
            case RichEditorToolbar.BLOCK_TEXT:
                showTextTypeMenu(row, v);
                break;
            case RichEditorToolbar.BLOCK_LIST:
                showListMenu(row, v);
                break;
            case RichEditorToolbar.BLOCK_TABLE:
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
                break;
            case RichEditorToolbar.BLOCK_MATH: {
                final TL_iv.pageBlockMath math = row != null && row.block instanceof TL_iv.pageBlockMath ? (TL_iv.pageBlockMath) row.block : null;
                showEditLatexSheet(getContext(), math == null || TextUtils.isEmpty(math.source) ? "" : math.source, source -> {
                    if (math != null) {
                        math.source = source;
                        listView.adapter.update(false);
                    } else {
                        final TL_iv.pageBlockMath newMath = new TL_iv.pageBlockMath();
                        newMath.source = source;
                        listView.addBlock(newMath);
                    }
                }, resourcesProvider);
                break;
            }
            case RichEditorToolbar.BLOCK_DETAILS:
                listView.insertDetails();
                break;
        }
    }

    private void showTextTypeMenu(BlockRow row, View v) {
        if (menu != null) {
            menu.dismiss();
        }
        final ItemOptions o = ItemOptions.makeOptions(this, resourcesProvider, v, true).dontFocus();
        final ItemOptions headers = o.makeSwipeback();
        final boolean premiumLock = !MessagesController.getInstance(currentAccount).richEditorAllowed() && !UserConfig.getInstance(currentAccount).isPremium();

        headers.add(R.drawable.ic_ab_back, getString(R.string.Back), () -> o.closeSwipeback());
        headers.addGap();
        addHeadingItem(headers, row, new TL_iv.pageBlockHeading1(), R.drawable.iv_h1, getString(R.string.ArticleHeading1), SharedConfig.fontSize + 2, o);
        addHeadingItem(headers, row, new TL_iv.pageBlockHeading2(), R.drawable.iv_h2, getString(R.string.ArticleHeading2), SharedConfig.fontSize + 1, o);
        addHeadingItem(headers, row, new TL_iv.pageBlockHeading3(), R.drawable.iv_h3, getString(R.string.ArticleHeading3), SharedConfig.fontSize, o);
        addHeadingItem(headers, row, new TL_iv.pageBlockHeading4(), R.drawable.iv_h4, getString(R.string.ArticleHeading4), SharedConfig.fontSize - 1, o);
        addHeadingItem(headers, row, new TL_iv.pageBlockHeading5(), R.drawable.iv_h5, getString(R.string.ArticleHeading5), SharedConfig.fontSize - 2, o);
        addHeadingItem(headers, row, new TL_iv.pageBlockHeading6(), R.drawable.iv_h6, getString(R.string.ArticleHeading6), SharedConfig.fontSize - 3, o);

        o.addChecked(row != null && RichEditorListView.isHeading(row.block), new RichEditor.RequiresPremiumDrawable(getContext(), R.drawable.iv_h1).setPremium(premiumLock), getString(R.string.ArticleHeading), () -> o.openSwipeback(headers));
        o.addChecked(row != null && row.block instanceof TL_iv.pageBlockParagraph, R.drawable.iv_text, getString(R.string.ArticleText), () -> listView.turnIntoKeepList(row, new TL_iv.pageBlockParagraph()));
        o.addChecked(row != null && row.block instanceof TL_iv.pageBlockBlockquote, R.drawable.iv_quote, getString(R.string.ArticleQuote), () -> listView.turnInto(row, RichEditorListView.newBlockquote(), 0, 0, false, false));
        o.addChecked(row != null && row.block instanceof TL_iv.pageBlockPullquote, new RichEditor.RequiresPremiumDrawable(getContext(), R.drawable.iv_pullquote).setPremium(premiumLock), getString(R.string.ArticlePullquote), () -> listView.turnInto(row, RichEditorListView.newPullquote(), 0, 0, false, false));
        o.addChecked(row != null && row.block instanceof TL_iv.pageBlockPreformatted, R.drawable.iv_code, getString(R.string.ArticleCode), () -> listView.turnIntoKeepList(row, new TL_iv.pageBlockPreformatted()));
        o.addChecked(row != null && row.block instanceof TL_iv.pageBlockFooter, new RichEditor.RequiresPremiumDrawable(getContext(), R.drawable.iv_footer).setPremium(premiumLock), getString(R.string.ArticleFooter), () -> listView.turnIntoKeepList(row, new TL_iv.pageBlockFooter()));
        menu = o.show();
    }

    private void addHeadingItem(ItemOptions headers, BlockRow row, TL_iv.PageBlock block, int icon, String label, int textSize, ItemOptions root) {
        headers.addChecked(row != null && row.block.getClass() == block.getClass(), icon, label, () -> { listView.turnIntoKeepList(row, block); root.dismiss(); });
        headers.getLast().textView.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
        headers.getLast().textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
    }

    private void showListMenu(BlockRow row, View v) {
        if (menu != null) {
            menu.dismiss();
        }
        final ItemOptions o = ItemOptions.makeOptions(this, resourcesProvider, v).dontFocus();
        o
            .addChecked(row == null || !row.isInList(), R.drawable.field_carret_empty, getString(R.string.ArticleNone), () -> listView.turnIntoList(row, 0))
            .addChecked(row != null && row.isInList() && !row.isChecklist() && !row.isOrdered(), R.drawable.iv_list, getString(R.string.ArticleListBulleted), () -> listView.turnIntoList(row, 1))
            .addChecked(row != null && row.isInList() && !row.isChecklist() && row.isOrdered(), R.drawable.iv_ordered_list, getString(R.string.ArticleListNumbered), () -> listView.turnIntoList(row, 2))
            .addChecked(row != null && row.isInList() && row.isChecklist() && !row.isOrdered(), R.drawable.iv_todo, getString(R.string.ArticleListTodo), () -> listView.turnIntoList(row, 3))
            .addChecked(row != null && row.block instanceof TL_iv.pageBlockDetails, R.drawable.iv_details, getString(R.string.ArticleToggleBlock), listView::insertDetails);
        final boolean canIndent = listView.canIndentSelection();
        final boolean canOutdent = listView.canOutdentSelection();
        if (canIndent || canOutdent) {
            o.addGap();
            if (canIndent) o.add(R.drawable.iv_list_tab, getString(R.string.ArticleIndent), () -> { listView.indentSelection(false); o.dismiss(); });
            if (canOutdent) o.add(R.drawable.iv_list_untab, getString(R.string.ArticleOutdent), () -> { listView.indentSelection(true); o.dismiss(); });
        }
        menu = o.forceTop(true).show();
    }

    private void updateFormattingPanel() {
        if (toolbar == null) return;
        final boolean show = listView.isInSelectionMode() && listView.selectionHasInlineFormattable();
        toolbar.showFormattingPanel(show, true);
        if (show) updateFormattingButtons();
    }

    private void updateFormattingButtons() {
        final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = listView.getTextSelectionHelper();
        if (toolbar == null || textSelectionHelper == null || !textSelectionHelper.isInSelectionMode()) return;
        toolbar.setQuoteState(listView.isSelectionQuoted());
        if (listView.isTableSelection()) { updateFormattingButtonsTable(); return; }
        if (listView.isCaptionSelection()) { updateFormattingButtonsCaption(); return; }
        final int sCell = textSelectionHelper.getStartCell();
        final int eCell = textSelectionHelper.getEndCell();
        final int sOff = textSelectionHelper.getStartOffset();
        final int eOff = textSelectionHelper.getEndOffset();
        final boolean valid = sCell >= 0 && eCell >= 0 && eCell >= sCell && eCell < listView.itemRows.size();
        int mask = 0;
        if (valid) {
            for (final int flag : STYLE_FLAGS) {
                if (listView.isStyleFullyApplied(flag, sCell, sOff, eCell, eOff)) mask |= flag;
            }
        }
        toolbar.setFormattingState(mask,
            valid && listView.isLinkApplied(sCell, sOff, eCell, eOff),
            valid && listView.isDateApplied(sCell, sOff, eCell, eOff),
            valid && sCell == eCell,
            !listView.isSelectionAllHeadings());
    }

    private void updateFormattingButtonsTable() {
        final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = listView.getTextSelectionHelper();
        final int pos = textSelectionHelper.getStartCell();
        final int sChild = textSelectionHelper.getStartChildPosition();
        final int eChild = textSelectionHelper.getEndChildPosition();
        final int sOff = textSelectionHelper.getStartOffset();
        final int eOff = textSelectionHelper.getEndOffset();
        int mask = 0;
        for (final int flag : STYLE_FLAGS) {
            if (listView.isStyleFullyAppliedTable(flag, pos, sChild, sOff, eChild, eOff)) mask |= flag;
        }
        final boolean single = sChild == eChild;
        final RichEditText et = single ? listView.tableEditText(pos, sChild) : null;
        final int from = Math.max(0, Math.min(sOff, eOff));
        final int to = et == null ? 0 : Math.max(0, Math.min(Math.max(sOff, eOff), et.length()));
        toolbar.setFormattingState(mask,
            et != null && from < to && RichTextStyle.hasLink(et.getText(), from, to),
            et != null && from < to && RichTextStyle.hasDate(et.getText(), from, to),
            single,
            true);
    }

    private void updateFormattingButtonsCaption() {
        final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = listView.getTextSelectionHelper();
        final int pos = textSelectionHelper.getStartCell();
        final RichEditText et = listView.captionEditText(pos);
        final int sOff = textSelectionHelper.getStartOffset();
        final int eOff = textSelectionHelper.getEndOffset();
        final int from = et == null ? 0 : Math.max(0, Math.min(Math.min(sOff, eOff), et.length()));
        final int to = et == null ? 0 : Math.max(0, Math.min(Math.max(sOff, eOff), et.length()));
        int mask = 0;
        if (et != null && from < to) {
            for (final int flag : STYLE_FLAGS) {
                if ((et.getCurrentStyle(from, to) & flag) != 0) mask |= flag;
            }
        }
        toolbar.setFormattingState(mask,
            et != null && from < to && RichTextStyle.hasLink(et.getText(), from, to),
            et != null && from < to && RichTextStyle.hasDate(et.getText(), from, to),
            true,
            true);
    }

    private static final int[] STYLE_FLAGS = {
        RichTextStyle.BOLD, RichTextStyle.ITALIC, RichTextStyle.UNDERLINE, RichTextStyle.STRIKE,
        RichTextStyle.SPOILER, RichTextStyle.MONO, RichTextStyle.SUBSCRIPT, RichTextStyle.SUPERSCRIPT
    };

    private void updateToolbarBlockType() {
        if (toolbar == null) return;
        final BlockRow row;
        final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = listView.getTextSelectionHelper();
        if (textSelectionHelper != null && textSelectionHelper.isInSelectionMode()) {
            final int sCell = textSelectionHelper.getStartCell();
            final int eCell = textSelectionHelper.getEndCell();
            row = sCell == eCell ? listView.rowForCell(sCell) : null;
        } else {
            row = listView.findFocusedRow();
        }
        int type;
        if (listView.findFocusedTableCell() != null) {
            type = RichEditorToolbar.BLOCK_TABLE;
        } else if (row == null) {
            type = 0;
        } else if (row.isChecklist() || row.isInList() || row.isOrdered() || row.block instanceof TL_iv.pageBlockDetails) {
            type = RichEditorToolbar.BLOCK_LIST;
        } else if (row.block instanceof TL_iv.pageBlockTable) {
            type = RichEditorToolbar.BLOCK_TABLE;
        } else if (row.block instanceof TL_iv.pageBlockMath) {
            type = RichEditorToolbar.BLOCK_MATH;
        } else if (RichEditorListView.isHeading(row.block)
            || row.block instanceof TL_iv.pageBlockParagraph
            || row.block instanceof TL_iv.pageBlockPreformatted
            || row.block instanceof TL_iv.pageBlockBlockquote
            || row.block instanceof TL_iv.pageBlockPullquote) {
            type = RichEditorToolbar.BLOCK_TEXT;
        } else {
            type = 0;
        }
        int icon = 0;
        if (row != null) {
            if (type == RichEditorToolbar.BLOCK_TEXT) {
                if (row.block instanceof TL_iv.pageBlockHeading1) icon = R.drawable.iv_h1;
                else if (row.block instanceof TL_iv.pageBlockHeading2) icon = R.drawable.iv_h2;
                else if (row.block instanceof TL_iv.pageBlockHeading3) icon = R.drawable.iv_h3;
                else if (row.block instanceof TL_iv.pageBlockHeading4) icon = R.drawable.iv_h4;
                else if (row.block instanceof TL_iv.pageBlockHeading5) icon = R.drawable.iv_h5;
                else if (row.block instanceof TL_iv.pageBlockHeading6) icon = R.drawable.iv_h6;
                else if (row.block instanceof TL_iv.pageBlockPreformatted) icon = R.drawable.iv_code;
                else if (row.block instanceof TL_iv.pageBlockBlockquote) icon = R.drawable.iv_quote;
                else if (row.block instanceof TL_iv.pageBlockPullquote) icon = R.drawable.iv_pullquote;
                else if (row.block instanceof TL_iv.pageBlockFooter) icon = R.drawable.iv_footer;
            } else if (type == RichEditorToolbar.BLOCK_LIST) {
                if (row.isChecklist()) icon = R.drawable.iv_todo;
                else if (row.isOrdered()) icon = R.drawable.iv_ordered_list;
            }
        }
        toolbar.setSelectedBlockType(type, icon);
    }

    public TextSelectionHelper.ArticleTextSelectionHelper getTextSelectionHelper() {
        return listView.getTextSelectionHelper();
    }

    private boolean sendButtonShown;

    private void updateSendButton(boolean animated) {
        updateAttachButtons(animated);
    }

    private void updateAttachButtons(boolean animated) {
        final boolean hide = listView.hasAnyText() || emojiViewVisible;
        parentAlert.setTypeButtonsHidden(hide, animated);
        attachRaise = attachRaiseTarget(hide);
        layoutBottomPanels();
        if (attachButtonsShown == hide) {
            attachButtonsShown = !hide;
            requestLayout();
        }
    }

    private int attachRaiseTarget(boolean hidden) {
        return hidden || parentAlert.pinnedToTop ? 0 : parentAlert.getTypeButtonsHeight();
    }

    private void updateAttachRaise() {
        final int raise = attachRaiseTarget(listView.hasAnyText() || emojiViewVisible);
        if (attachRaise != raise) {
            attachRaise = raise;
            layoutBottomPanels();
        }
    }

    private boolean attachButtonsShown = true;
    private int attachRaise;
    private boolean keyboardVisible;

    private int bottomNavInset() {
        return keyboardVisible || emojiPadding > 0 ? 0 : AndroidUtilities.navigationBarHeight;
    }

    private int emojiVisibleHeight() {
        return emojiSearchOpened ? dp(245) : emojiPadding;
    }

    private int lastAttachRise;
    private void layoutBottomPanels() {
        final int emojiVisible = emojiVisibleHeight();
        if (emojiView != null) {
            emojiView.setTranslationY(emojiViewVisible ? (emojiPadding - emojiVisible) + (emojiSearchOpened ? -parentAlert.currentPanTranslationY : 0) : 0);
        }
        if (toolbar != null) {
            float inset = emojiViewVisible ? emojiVisible : bottomNavInset();
            if (!emojiViewVisible || emojiSearchOpened) {
                inset += parentAlert.currentPanTranslationY;
            }
            toolbar.getBottomContainer().animate().cancel();
            toolbar.getBottomContainer().setTranslationY(-inset);

            float gradientInset = emojiViewVisible ? emojiVisible : 0;
            if (!emojiViewVisible || emojiSearchOpened) {
                gradientInset += parentAlert.currentPanTranslationY;
            }
            toolbar.setBottomGradientTranslationY(-gradientInset);

            if (lastAttachRise != attachRaise) {
                toolbar.getBottomInnerContainer().animate()
                    .translationY(-(lastAttachRise = attachRaise))
                    .setDuration(320)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .start();
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        if (emojiSearchOpened) {
            closeEmojiSearch();
            return false;
        }
        if (emojiViewVisible) {
            hideEmojiPopup();
            return false;
        }
        if (listView.deselectIfAny()) {
            return false;
        }
        if (!checkDiscard()) {
            return true;
        }
        return super.onBackPressed();
    }

    @Override
    public int needsActionBar() {
        return 0;
    }

    @Override
    public boolean shouldHideBottomButtons() {
        return !listView.hasAnyText();
    }

    @Override
    public boolean disableBottomFade() {
        return true;
    }

    @Override
    public int getListTopPadding() {
        return listView.getPaddingTop() - AndroidUtilities.statusBarHeight - ActionBar.getCurrentActionBarHeight();
    }

    @Override
    public int getCurrentItemTop() {
        if (listView.getChildCount() <= 0) {
            listView.setTopGlowOffset(currentItemTop = listView.getPaddingTop());
            return Integer.MAX_VALUE;
        }
        boolean hadFirstChild = false;
        int top = Integer.MAX_VALUE;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            final View child = listView.getChildAt(i);
            final int position = listView.getChildAdapterPosition(child);
            if (position == 0) {
                hadFirstChild = true;
            }
            if (position >= 0 && child.getTop() < top) {
                top = child.getTop();
            }
        }
        if (top == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        listView.setTopGlowOffset(Math.max(0, top));
        final int adjusted = top - AndroidUtilities.statusBarHeight;
        int newOffset = dp(7);
        if (adjusted >= dp(7) && hadFirstChild) {
            newOffset = adjusted;
        }
        return currentItemTop = newOffset;
    }

    @Override
    public int getFirstOffset() {
        return getListTopPadding() + dp(56);
    }

    private boolean ignoreLayout;

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    public void onPreMeasure(int availableWidth, int availableHeight) {
        final boolean wasKeyboardVisible = keyboardVisible;
        keyboardVisible = parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight() > dp(20);
        if (!keyboardVisible && wasKeyboardVisible) {
            if (menu != null) {
                menu.dismiss();
                menu = null;
            }
        }
        int paddingTop;
        if (keyboardVisible || emojiPadding > dp(20)) {
            paddingTop = dp(52);
            parentAlert.setAllowNestedScroll(false);
        } else {
            if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                paddingTop = (int) (availableHeight / 3.5f);
            } else {
                paddingTop = (availableHeight / 5 * 2);
            }
            paddingTop -= dp(52);
            if (paddingTop < 0) {
                paddingTop = 0;
            }
            parentAlert.setAllowNestedScroll(true);
        }
        paddingTop += AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight();

        final int attachButtonsExtra = (listView.hasAnyText() || emojiViewVisible) ? 0 : parentAlert.getTypeButtonsHeight();
        final int paddingBottom = bottomNavInset() + dp(TOOLBAR_BOTTOM_INSET + 50) + attachButtonsExtra + emojiPadding;
        if (listView.getPaddingTop() != paddingTop || listView.getPaddingBottom() != paddingBottom) {
            ignoreLayout = true;
            listView.setPaddingWithoutRequestLayout(0, paddingTop, 0, paddingBottom);
            ignoreLayout = false;
        }
        updateToolbarTopOffset();
    }

    @Override
    public void scrollToTop() {
        listView.smoothScrollToPosition(0);
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        parentAlert.getSheetContainer().invalidate();
        invalidate();
    }

    @Override
    public void onPanTransitionStart(boolean keyboardVisible, int contentHeight) {
        super.onPanTransitionStart(keyboardVisible, contentHeight);
        this.keyboardVisible = keyboardVisible;
        layoutBottomPanels();
        if (keyboardVisible && emojiViewVisible && !emojiSearchOpened) {
            hideEmojiPopup();
        }
        updateToolbarTopOffset();
    }

    @Override
    public void onContainerTranslationUpdated(float currentPanTranslationY) {
        super.onContainerTranslationUpdated(currentPanTranslationY);
        layoutBottomPanels();
    }

    @Override
    public void onPanTransitionEnd() {
        super.onPanTransitionEnd();
        keyboardVisible = parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight() > dp(20);
        layoutBottomPanels();
        updateToolbarTopOffset();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (listView.textSelectionHelper.isInSelectionMode() && listView.textSelectionOverlay.onTouchEvent(ev)) {
            return true;
        }
        final int top = (emojiSearchOpened && emojiView != null ? (int) emojiView.getY() : getHeight() - emojiPadding) - dp(8 + 44 + 8) - attachRaise;
        if (ev.getAction() == MotionEvent.ACTION_DOWN && emojiViewVisible && ev.getY() < top) {
            hideEmojiPopup();
        }
        if ((ev.getAction() != MotionEvent.ACTION_DOWN || (ev.getY() > dp(8 + 44 + 8) && ev.getY() < top))
            && listView.textSelectionOverlay.checkOnTap(ev)) {
            ev.setAction(MotionEvent.ACTION_CANCEL);
        }
        if (ev.getY() < top && listView.handleSelectionTouch(ev)) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getKeyCode() == KeyEvent.KEYCODE_S && ev.isCtrlPressed()) {
            saveDraftWithBulletin();
            return true;
        }
        if (listView.handleKeyEvent(ev)) return true;
        return super.dispatchKeyEvent(ev);
    }

    private void saveDraftWithBulletin() {
        if (!(parentAlert.baseFragment instanceof ChatActivity)) return;
        if (!listView.canUndo()) return;
        if (persistDraft()) {
            BulletinFactory.of(toolbar, resourcesProvider)
                .createSimpleBulletin(R.raw.contact_check, getString(R.string.RichEditorDraftSaved))
                .show();
        }
    }

    @Override
    public void onShow(ChatAttachAlert.AttachAlertLayout previousLayout) {
        parentAlert.actionBar.setTitle("");
        listView.adapter.update(false);
        updateAttachButtons(false);
        post(this::updateToolbarTopOffset);
    }

    @Override
    public void onHide() {
        if (commandSuggestions != null) commandSuggestions.hide();
        if (emojiViewVisible) hideEmojiPopup();
        if (sendButtonShown) {
            sendButtonShown = false;
            parentAlert.showSendButtonOnly(false, true);
        }
    }

    public boolean sendSelectedItems(boolean notify, int scheduleDate, int scheduleRepeatPeriod, long effectId, boolean invertMedia) {
        if (isSendLocked()) {
            showConversionSheet();
            return false;
        }
        if (!listView.hasAnyText()) {
            return false;
        }
        if (listView.hasPendingUploads()) {
            return false;
        }
        if (!listView.isWithinLimits()) {
            updateSendButtonEnabled();
            return false;
        }
        if (!MessagesController.getInstance(currentAccount).richEditorAllowed()) {
            // non-premium user with non-lossy content: send it as a simple message via the composer
            if (parentAlert.baseFragment instanceof ChatActivity) {
                final ChatActivityEnterView enterView = ((ChatActivity) parentAlert.baseFragment).getChatActivityEnterView();
                if (enterView != null) {
                    enterView.sendConvertedRichAsSimple(listView.toSimpleMessage(), notify, scheduleDate, scheduleRepeatPeriod);
                    parentAlert.dismiss(true);
                    return true;
                }
            }
            return false;
        }
        ArrayList<TL_iv.PageBlock> sendBlocks = listView.flattenRowsToBlocks();
        if (sendBlocks.isEmpty()) {
            return false;
        }
        ArrayList<TLRPC.Photo> sendPhotos = listView.collectPhotos();
        ArrayList<TLRPC.Document> sendDocs = listView.collectDocuments();
        long monoForumPeerId = 0;
        MessageObject replyToMsg = null;
        MessageObject replyToTopMsg = null;
        String quickReplyShortcut = null;
        int quickReplyShortcutId = 0;
        if (parentAlert.baseFragment instanceof ChatActivity) {
            ChatActivity ca = (ChatActivity) parentAlert.baseFragment;
            replyToMsg = ca.getReplyMessage();
            replyToTopMsg = ca.getThreadMessage();
            monoForumPeerId = ca.getSendMonoForumPeerId();
            quickReplyShortcutId = ca.getQuickReplyId();
        }
        SendMessagesHelper.prepareSendingArticle(
            AccountInstance.getInstance(parentAlert.currentAccount),
            sendBlocks,
            sendPhotos,
            sendDocs,
            null,
            false,
            parentAlert.getDialogId(),
            replyToMsg,
            replyToTopMsg,
            notify,
            scheduleDate,
            scheduleRepeatPeriod,
            quickReplyShortcut,
            quickReplyShortcutId,
            effectId,
            monoForumPeerId,
            0
        );
        parentAlert.dismiss(true);
        return true;
    }

    private MessageSendPreview messageSendPreview;

    private boolean showSendPreview(View view) {
        if (!UserConfig.getInstance(currentAccount).isPremium()) {
            new PremiumFeatureBottomSheet(parentAlert.baseFragment, getContext(), currentAccount, PremiumPreviewFragment.PREMIUM_FEATURE_RICH_EDITOR, true).show();
            return true;
        }
        if (!listView.hasAnyText()) return false;
        if (listView.hasPendingUploads()) return false;
        if (!listView.isWithinLimits()) { updateSendButtonEnabled(); return false; }

        final ArrayList<TL_iv.PageBlock> previewBlocks = listView.flattenRowsToBlocks();
        if (previewBlocks.isEmpty()) return false;

        final ChatActivity chatActivity = parentAlert.baseFragment instanceof ChatActivity ? (ChatActivity) parentAlert.baseFragment : null;

        if (messageSendPreview != null) {
            messageSendPreview.dismiss(false);
            messageSendPreview = null;
        }
        messageSendPreview = new MessageSendPreview(getContext(), resourcesProvider);
        messageSendPreview.setOnDismissListener(di -> messageSendPreview = null);

        final long dialogId = parentAlert.getDialogId();
        final MessageObject replyToMsg = chatActivity != null ? chatActivity.getReplyMessage() : null;

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

        final ChatActivityEnterView.SendButton sendButton = toolbar.getSendButton();
        sendButton.setScaleX(1.0f);
        sendButton.setScaleY(1.0f);
        final ChatActivityEnterView.SendButton previewSendButton = messageSendPreview.setSendButton(sendButton, true, v -> {
            sendSelectedItems(true, 0, 0, 0, false);
            if (messageSendPreview != null) {
                messageSendPreview.dismiss(true);
                messageSendPreview = null;
            }
        });
        if (previewSendButton != null) {
            previewSendButton.setBackground(RichEditor.withShadow(Theme.createRoundRectDrawable(dp(22), getThemedColor(Theme.key_featuredStickers_addButton))));
            messageSendPreview.setSendButtonWidth(dp(44));
        }

        ItemOptions options = ItemOptions.makeOptions(this, resourcesProvider, sendButton);

        final boolean self = chatActivity != null && UserObject.isUserSelf(chatActivity.getCurrentUser());
        final boolean scheduleButtonValue = chatActivity != null && chatActivity.canScheduleMessage();
        if (scheduleButtonValue) {
            options.add(R.drawable.msg_calendar2, getString(self ? R.string.SetReminder : R.string.ScheduleMessage), () -> {
                AlertsCreator.createScheduleDatePickerDialog(parentAlert.baseFragment.getParentActivity(), dialogId, new AlertsCreator.ScheduleDatePickerDelegate() {
                    @Override
                    public void didSelectDate(boolean notify, int scheduleDate, int scheduleRepeatPeriod) {
                        sendSelectedItems(notify, scheduleDate, scheduleRepeatPeriod, 0, false);
                        if (messageSendPreview != null) {
                            messageSendPreview.dismissInstant();
                            messageSendPreview = null;
                        }
                    }
                }, resourcesProvider);
            });

            if (!self && dialogId > 0) {
                options.add(R.drawable.msg_online, getString(R.string.SendWhenOnline), () -> {
                    sendSelectedItems(true, 0x7FFFFFFE, 0, 0, false);
                    if (messageSendPreview != null) {
                        messageSendPreview.dismiss(false);
                        messageSendPreview = null;
                    }
                });
            }
        }

        if (!self) {
            options.add(R.drawable.input_notify_off, getString(R.string.SendWithoutSound), () -> {
                sendSelectedItems(false, 0, 0, 0, false);
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

    private void openLocationPicker(BlockRow row) {
        if (parentAlert.baseFragment == null) return;
        if (row == null || !(row.block instanceof TL_iv.pageBlockMap)) return;
        if (!AndroidUtilities.isMapsInstalled(parentAlert.baseFragment)) return;
        final ChatAttachAlert pickerAlert = new ChatAttachAlert(getContext(), parentAlert.baseFragment, false, false, false, null);
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
            updateSendButton(true);
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

    private void openAttach(int allowedLayouts, int initialLayoutType) {
        if (parentAlert.baseFragment == null) return;
        final ChatAttachAlert alert = new ChatAttachAlert(getContext(), parentAlert.baseFragment, false, false, true, resourcesProvider);
        alert.setDelegate(new ChatAttachAlert.ChatAttachViewDelegate() {
            @Override
            public void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long effectId, boolean invertMedia, boolean forceDocument, long payStars) {
                if (button == 7 || button == 8) {
                    final java.util.HashMap<Object, Object> photos = alert.getPhotoLayout().getSelectedPhotos();
                    final ArrayList<Object> order = alert.getPhotoLayout().getSelectedPhotosOrder();
                    final BlockRow target = listView.pendingMediaRow;
                    listView.pendingMediaRow = null;
                    for (int a = 0; a < order.size(); a++) {
                        final Object object = photos.get(order.get(a));
                        if (object instanceof org.telegram.messenger.MediaController.PhotoEntry) {
                            if (target != null) {
                                listView.addMediaToRow(target, (org.telegram.messenger.MediaController.PhotoEntry) object);
                            } else {
                                listView.attachMedia((org.telegram.messenger.MediaController.PhotoEntry) object);
                            }
                            break;
                        }
                    }
                }
                listView.pendingMediaRow = null;
                alert.dismiss(true);
            }
            @Override public void didSelectBot(TLRPC.User user) {}
            @Override public void onCameraOpened() {}
            @Override public boolean needEnterComment() { return false; }
            @Override public void doOnIdle(Runnable runnable) { NotificationCenter.getInstance(currentAccount).doOnIdle(runnable); }
        });
        alert.getPhotoLayout().loadGalleryPhotos();
        alert.setMaxSelectedPhotos(1, true);
        alert.enablePollAttachMode(allowedLayouts);
        alert.setLocationActivityDelegate((location, live, notify, scheduleDate, payStars) -> {
            if (location == null || location.geo == null) { alert.dismiss(true); return; }
            final TL_iv.pageBlockMap map = new TL_iv.pageBlockMap();
            map.geo = location.geo;
            map.zoom = 15;
            map.w = 600;
            map.h = 400;
            listView.addBlock(map);
            updateSendButton(true);
            alert.dismiss(true);
        });
        alert.setAudioSelectDelegate((audios, caption, notify, scheduleDate, scheduleRepeatPeriod, effectId, invertMedia, payStars) -> {
            if (audios != null && !audios.isEmpty()) listView.attachAudio(audios.get(0));
            alert.dismiss(true);
        });
        alert.init();
        if (initialLayoutType != 0) alert.openAttachLayoutForType(initialLayoutType);
        alert.setFocusable(true);
        alert.show();
    }

    public void onExternalMediaPicked(Intent data) {
        if (data == null || data.getData() == null) return;
        listView.attachExternalMedia(data.getData());
    }

    private void updateSendButtonLoading() {
        if (toolbar != null) {
            toolbar.setSendLoading(listView.hasPendingUploads());
        }
        updateSendButton(true);
    }

    private final Runnable limitCheckRunnable = this::updateSendButtonEnabled;

    private void scheduleLimitCheck() {
        AndroidUtilities.cancelRunOnUIThread(limitCheckRunnable);
        AndroidUtilities.runOnUIThread(limitCheckRunnable, 1000);
    }

    private void updateSendButtonEnabled() {
        if (toolbar != null) toolbar.setSendEnabled(listView.isWithinLimits());
    }

    private void updateSendButtonLocked() {
        if (toolbar != null) {
            final boolean premiumLocked = !MessagesController.getInstance(currentAccount).richEditorAllowed()
                && !UserConfig.getInstance(currentAccount).isPremium();
            toolbar.getSendButton().setLocked(premiumLocked && listView.isLossy());
            toolbar.setPremiumLocked(premiumLocked);
        }
    }

    private boolean isSendLocked() {
        return !MessagesController.getInstance(currentAccount).richEditorAllowed()
            && !UserConfig.getInstance(currentAccount).isPremium()
            && listView.isLossy();
    }

    private void showConversionSheet() {
        RichEditor.openConversionSheet(getContext(), listView::convertToSimple, () -> {
            if (!UserConfig.getInstance(currentAccount).isPremium()) {
                new PremiumFeatureBottomSheet(parentAlert.baseFragment, getContext(), currentAccount, PremiumPreviewFragment.PREMIUM_FEATURE_RICH_EDITOR, true).show();
            }
        }, resourcesProvider);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        updateSendButtonLocked();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.currentUserPremiumStatusChanged) {
            updateSendButtonLocked();
        }
    }

    private int getEmojiPanelHeight() {
        int h = parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight();
        if (h <= 0) {
            h = MessagesController.getGlobalEmojiSettings().getInt(
                AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? "kbd_height_land3" : "kbd_height", dp(200));
        }
        if (h <= 0) h = dp(200);
        return h + AndroidUtilities.navigationBarHeight;
    }

    private void createEmojiView() {
        if (emojiView != null) return;
        emojiView = new EmojiView(parentAlert.baseFragment, true, false, false, getContext(), true, null, parentAlert.sizeNotifierFrameLayout, true, resourcesProvider, false);
        emojiView.setVisibility(GONE);
        emojiView.fixBottomTabContainerTranslation = false;
        emojiView.setBottomInset(AndroidUtilities.navigationBarHeight);
        emojiView.hideBottomTabContainerBackground();
        emojiView.setDelegate(new EmojiView.EmojiViewDelegate() {
            @Override
            public void onSearchOpenClose(int type) {
                if (type != 0) {
                    final RichEditText focused = listView.getFocusedEditTextOrNull();
                    if (focused != null) {
                        emojiTargetEditText = focused;
                        emojiTargetSelection = Math.max(0, focused.getSelectionEnd());
                    }
                }
                emojiSearchOpened = type != 0;
                layoutBottomPanels();
            }
            @Override
            public boolean isSearchOpened() {
                return emojiSearchOpened;
            }
            @Override
            public boolean onBackspace() {
                final RichEditText et = resolveEmojiTarget();
                if (et == null || et.length() == 0) return false;
                et.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                return true;
            }
            @Override
            public void onEmojiSelected(String symbol) {
                final RichEditText et = resolveEmojiTarget();
                if (et == null) return;
                final int i = resolveEmojiTargetOffset(et);
                try {
                    final CharSequence cs = Emoji.replaceEmoji(symbol, et.getPaint().getFontMetricsInt(), false, null);
                    et.setText(et.getText().insert(i, cs));
                    final int j = i + cs.length();
                    et.setSelection(j, j);
                    if (et == emojiTargetEditText) emojiTargetSelection = j;
                } catch (Exception ignore) {}
            }
            @Override
            public void onCustomEmojiSelected(long documentId, TLRPC.Document document, String emoticon, boolean isRecent) {
                final RichEditText et = resolveEmojiTarget();
                if (et == null) return;
                final int i = resolveEmojiTargetOffset(et);
                try {
                    final SpannableString spannable = new SpannableString(emoticon == null ? "😀" : emoticon);
                    final AnimatedEmojiSpan span = document != null
                        ? new AnimatedEmojiSpan(document, et.getPaint().getFontMetricsInt())
                        : new AnimatedEmojiSpan(documentId, et.getPaint().getFontMetricsInt());
                    span.cacheType = AnimatedEmojiDrawable.getCacheTypeForEnterView();
                    spannable.setSpan(span, 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    et.setText(et.getText().insert(i, spannable));
                    final int j = i + spannable.length();
                    et.setSelection(j, j);
                    if (et == emojiTargetEditText) emojiTargetSelection = j;
                } catch (Exception ignore) {}
            }
        });
        addView(emojiView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, getEmojiPanelHeight(), Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
    }

    private RichEditText resolveEmojiTarget() {
        final RichEditText focused = listView.getFocusedEditTextOrNull();
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

    private void toggleEmojiPopup() {
        if (emojiViewVisible) {
            final RichEditText et = listView.findFocusedEditText();
            if (et != null) {
                et.requestEditFocus();
                AndroidUtilities.showKeyboard(et);
            }
            hideEmojiPopup(true);
        } else {
            showEmojiPopup();
        }
    }

    private void showEmojiPopup() {
        createEmojiView();
        final int h = getEmojiPanelHeight();
        final android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) emojiView.getLayoutParams();
        lp.height = h;
        emojiView.setLayoutParams(lp);
        emojiView.setTranslationY(0);
        emojiView.setVisibility(VISIBLE);
        emojiViewVisible = true;
        emojiPadding = h;
        final RichEditText et = listView.findFocusedEditText();
        if (et != null) AndroidUtilities.hideKeyboard(et);
        if (toolbar != null) toolbar.setEmojiOpened(true);
        updateAttachButtons(false);
        requestLayout();
    }

    private void hideEmojiPopup() {
        hideEmojiPopup(false);
    }

    private void hideEmojiPopup(boolean keepKeyboard) {
        if (emojiSearchOpened) {
            emojiSearchOpened = false;
            if (emojiView != null) {
                emojiView.closeSearch(false);
                if (!keepKeyboard) emojiView.hideSearchKeyboard();
            }
        }
        emojiTargetEditText = null;
        if (emojiView != null) {
            emojiView.setTranslationY(0);
            emojiView.setVisibility(GONE);
        }
        emojiViewVisible = false;
        emojiPadding = 0;
        if (toolbar != null) toolbar.setEmojiOpened(false);
        updateAttachButtons(false);
        requestLayout();
    }

    private void closeEmojiSearch() {
        if (!emojiSearchOpened) return;
        emojiSearchOpened = false;
        if (emojiView != null) {
            emojiView.closeSearch(false);
            emojiView.hideSearchKeyboard();
        }
        layoutBottomPanels();
    }

    @Override
    public void onHidden() {

    }

    @Override
    public void onDestroy() {
        if (messageSendPreview != null) {
            messageSendPreview.dismissInstant();
            messageSendPreview = null;
        }
        if (commandSuggestions != null) {
            commandSuggestions.hide();
        }
        if (listView != null) {
            listView.clearContent();
        }
        if (emojiView != null) {
            emojiView.onDestroy();
        }
    }

    public static void showEditLatexSheet(Context context, String initialSource, Utilities.Callback<String> whenDone, Theme.ResourcesProvider resourcesProvider) {
        final BottomSheet.Builder b = new BottomSheet.Builder(context, true, resourcesProvider);

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        final String[] source = new String[] { initialSource == null ? "" : initialSource };
        final ImageView previewView = new ImageView(context);
        previewView.setPadding(dp(4), dp(4), dp(4), dp(4));
        previewView.setBackground(Theme.createRoundRectDrawable(dp(8), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), .05f)));
        final FrameLayout previewContainer = new FrameLayout(context);
        previewContainer.addView(previewView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        final HorizontalScrollView previewScroll = new HorizontalScrollView(context);
        previewScroll.setHorizontalScrollBarEnabled(false);
        previewScroll.setClipToPadding(false);
        previewScroll.setFillViewport(true);
        previewScroll.setVisibility(View.GONE);
        previewScroll.addView(previewContainer, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(previewScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 12, 2, 12, 0));

        final ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider).setRound();

        final boolean[] sentDone = new boolean[] { false };
        final boolean[] error = new boolean[] { false };
        final int[] shiftDp = new int[] { 6 };
        final Utilities.Callback2<String, Utilities.Callback2<Bitmap, Boolean>> generate = (src, done) -> {
            Utilities.themeQueue.postRunnable(() -> {
                boolean err = false;
                Bitmap bitmap = null;
                try {
                    final JLatexMathDrawable drawable =
                            JLatexMathDrawable.builder(source[0])
                                    .textSize(dp(26))
                                    .build();
                    final int w = drawable.getIntrinsicWidth();
                    final int h = drawable.getIntrinsicHeight();
                    if (w > 0 && h > 0) {
                        final Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
                        drawable.setBounds(0, 0, w, h);
                        drawable.draw(new Canvas(bm));
                        bitmap = bm;
                    } else {
                        err = true;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    err = true;
                }
                if (err) {
                    try {
                        final JLatexMathDrawable drawable =
                                JLatexMathDrawable.builder(getString(R.string.ArticleLatexError))
                                        .textSize(dp(26))
                                        .build();
                        final int w = drawable.getIntrinsicWidth();
                        final int h = drawable.getIntrinsicHeight();
                        if (w > 0 && h > 0) {
                            final Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
                            drawable.setBounds(0, 0, w, h);
                            drawable.draw(new Canvas(bm));
                            bitmap = bm;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                final boolean finalError = err;
                final Bitmap finalBitmap = bitmap;
                AndroidUtilities.runOnUIThread(() -> {
                    done.run(finalBitmap, finalError);
                });
            });
        };
        final Runnable update = () -> {
            if (TextUtils.isEmpty(source[0].trim())) {
                previewScroll.setVisibility(View.GONE);
                button.setEnabled(false);
                return;
            }
            final boolean wasError = error[0];
            final String currentSource = source[0];
            generate.run(currentSource, (bitmap, err) -> {
                if (!TextUtils.equals(currentSource, source[0])) return;
                if (err) {
                    previewView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_text_RedBold, resourcesProvider), PorterDuff.Mode.SRC_IN));
                    if (!wasError) {
                        AndroidUtilities.shakeViewSpring(previewView, shiftDp[0] = -shiftDp[0]);
                    }
                } else {
                    previewView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
                }
                if (bitmap != null) {
                    previewView.setImageBitmap(bitmap);
                }
                button.setEnabled(!err);
                previewScroll.setVisibility(bitmap != null ? View.VISIBLE : View.GONE);
                error[0] = err;
            });
        };

        final EditTextCell editCell = new EditTextCell(context, getString(R.string.ArticleLatexEquation), true, false, -1, resourcesProvider);
        editCell.editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editCell.editText.setMaxLines(5);
        editCell.setBackground(Theme.createRoundRectDrawable(dp(24), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
        editCell.setText(source[0]);
        editCell.editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                source[0] = s.toString();
                update.run();
            }
        });
        layout.addView(editCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 12, 8, 12, 0));

        button.setText(getString(R.string.Done));
        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.FILL_HORIZONTAL, 12, 12, 12, 12));

        update.run();

        b.setCustomView(layout);
        b.setOnPreDismissListener(d -> {
            editCell.editText.clearFocus();
            AndroidUtilities.hideKeyboard(editCell.editText);

            if (!sentDone[0] && !error[0] && !TextUtils.equals(initialSource, source[0])) {
                sentDone[0] = true;
                whenDone.run(source[0]);
            }
        });
        final BottomSheet sheet = b.show();
        sheet.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        sheet.fixNavigationBar(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        button.setOnClickListener(v -> {
            if (!button.isEnabled()) return;
            if (!sentDone[0]) {
                sentDone[0] = true;
                whenDone.run(source[0]);
            }
            sheet.dismiss();
        });

        AndroidUtilities.runOnUIThread(() -> {
            editCell.editText.requestFocus();
            AndroidUtilities.showKeyboard(editCell.editText);
        }, 200);
    }

}
