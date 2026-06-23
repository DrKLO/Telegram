package org.Tajgram.ui.iv;

import static org.Tajgram.messenger.AndroidUtilities.dp;
import static org.Tajgram.messenger.AndroidUtilities.dpf2;
import static org.Tajgram.messenger.LocaleController.getString;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.Tajgram.messenger.AccountInstance;
import org.Tajgram.messenger.AndroidUtilities;
import org.Tajgram.messenger.FileLog;
import org.Tajgram.messenger.MessageObject;
import org.Tajgram.messenger.R;
import org.Tajgram.messenger.SendMessagesHelper;
import org.Tajgram.messenger.Utilities;
import org.Tajgram.tgnet.TLRPC;
import org.Tajgram.tgnet.tl.TL_iv;
import org.Tajgram.ui.ActionBar.ActionBar;
import org.Tajgram.ui.ActionBar.BottomSheet;
import org.Tajgram.ui.Cells.EditTextCell;
import org.Tajgram.ui.ChatActivity;
import org.Tajgram.ui.ActionBar.Theme;
import org.Tajgram.ui.Cells.TextSelectionHelper;
import org.Tajgram.ui.Components.ChatAttachAlert;
import org.Tajgram.ui.Components.ColoredImageSpan;
import org.Tajgram.ui.Components.LayoutHelper;
import org.Tajgram.ui.Components.RecyclerListView;
import org.Tajgram.ui.Components.ScaleStateListAnimator;
import org.Tajgram.ui.Components.UItem;
import org.Tajgram.ui.Components.UniversalAdapter;
import org.Tajgram.ui.Components.UniversalRecyclerView;
import org.Tajgram.ui.Stories.recorder.ButtonWithCounterView;

import java.text.BreakIterator;
import java.util.ArrayList;

import ru.noties.jlatexmath.JLatexMathDrawable;

public class ChatAttachAlertRichLayout extends ChatAttachAlert.AttachAlertLayout {

    private static final String TAG = "RICHED";

    private final int currentAccount;
    private final UniversalRecyclerView listView;
    private final ArrayList<BlockRow> rows = new ArrayList<>();
    private final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper;
    private final TextSelectionHelper.TextSelectionOverlay textSelectionOverlay;

    private final LinearLayout newBlockButtonContainer;
    private final TextView newBlockButton;

    private int currentItemTop;

    private float pressX, pressY;
    private View pressTarget;
    private Runnable longPressRunnable;

    private int restoreFocusCell = -1;
    private int restoreFocusOffset = -1;
    private int restoreFocusChildPosition = 0;

    private RichTableCell activeCellSelectionTable;
    private boolean pressMoved;
    private boolean longPressConsumed;
    private org.Tajgram.ui.ActionBar.ActionBarPopupWindow cellPopupWindow;
    private org.Tajgram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout cellPopupLayout;
    private LinearLayout cellPopupRow;
    private TextView headerAction, mergeAction, unmergeAction, delRowAction, delColAction;

    public ChatAttachAlertRichLayout(
        ChatAttachAlert alert,
        Context context,
        int currentAccount,
        Theme.ResourcesProvider resourcesProvider
    ) {
        super(alert, context, resourcesProvider);
        this.currentAccount = currentAccount;

        rows.add(new BlockRow(new TL_iv.pageBlockHeading1()));
        rows.add(new BlockRow(new TL_iv.pageBlockParagraph()));

        newBlockButtonContainer = new LinearLayout(context);
        newBlockButtonContainer.setOrientation(LinearLayout.HORIZONTAL);
        newBlockButtonContainer.setPadding(dp(10), dp(6), dp(10), dp(6));
        newBlockButton = new TextView(context);
        newBlockButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        newBlockButton.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        newBlockButton.setTypeface(AndroidUtilities.bold());
        newBlockButton.setPadding(dp(6), dp(4), dp(12), dp(4));
        final SpannableStringBuilder newBlockText = new SpannableStringBuilder("+ Add");
        final ColoredImageSpan newBlockIcon = new ColoredImageSpan(R.drawable.poll_add_plus);
        newBlockIcon.spaceScaleX = 0.9f;
        newBlockIcon.translate(0, dpf2(0.4f));
        newBlockText.setSpan(newBlockIcon, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        newBlockButton.setText(newBlockText);
        newBlockButton.setBackground(Theme.createRadSelectorDrawable(getThemedColor(Theme.key_listSelector), 10, 10));
        ScaleStateListAnimator.apply(newBlockButton);
        newBlockButtonContainer.addView(newBlockButton);
        newBlockButton.setOnClickListener(v -> {

        });

        listView = new UniversalRecyclerView(context, currentAccount, 0, this::fillItems, this::onItemClick, null, resourcesProvider) {
            @Override
            protected void onLayoutUpdate() {
                final int oldCurrentItemTop = currentItemTop;
                if (getCurrentItemTop() != oldCurrentItemTop) {
                    parentAlert.updateLayout(ChatAttachAlertRichLayout.this, true, 0);
                }
            }
        };
        listView.adapter.setApplyBackground(false);
        listView.setClipToPadding(false);
        listView.setPadding(0, dp(8), 0, dp(8));
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        textSelectionHelper = new TextSelectionHelper.ArticleTextSelectionHelper();
        textSelectionHelper.setParentView(listView);
        textSelectionHelper.layoutManager = listView.layoutManager;
        textSelectionOverlay = textSelectionHelper.getOverlayView(context);
        AndroidUtilities.removeFromParent(textSelectionOverlay);
        addView(textSelectionOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        setFocusable(true);
        setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= 26) {
            setDefaultFocusHighlightEnabled(false);
        }
        setBackground(null);
        setForeground(null);

        textSelectionHelper.setCallback(new TextSelectionHelper.Callback() {
            @Override
            public void onStateChanged(boolean isSelected) {
                Log.d(TAG, "helper.onStateChanged isSelected=" + isSelected);
                if (isSelected) {
                    if (activeCellSelectionTable != null) {
                        exitCellSelectionMode();
                    }
                    restoreFocusCell = textSelectionHelper.getAnchorCell();
                    restoreFocusOffset = textSelectionHelper.getAnchorOffset();
                    restoreFocusChildPosition = textSelectionHelper.getAnchorChildPosition();
                    setEditTextsLocked(true);
                    requestFocus();
                    Log.d(TAG, "RichLayout requestFocus -> hasFocus=" + hasFocus() + " isFocused=" + isFocused() + " restore=(" + restoreFocusCell + "," + restoreFocusChildPosition + "," + restoreFocusOffset + ")");
                } else {
                    final int cell = restoreFocusCell;
                    final int off = restoreFocusOffset;
                    final int childPos = restoreFocusChildPosition;
                    restoreFocusCell = -1;
                    restoreFocusOffset = -1;
                    restoreFocusChildPosition = 0;
                    setEditTextsLocked(false);
                    if (cell >= 0) {
                        post(() -> restoreFocusAt(cell, childPos, off));
                    }
                }
            }
        });

        listView.addOnScrollListener(new RecyclerListView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                parentAlert.updateLayout(ChatAttachAlertRichLayout.this, true, dy);
                textSelectionHelper.onParentScrolled();
                if (cellPopupWindow != null && cellPopupWindow.isShowing() && activeCellSelectionTable != null) {
                    showOrUpdateCellPopup();
                }
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    textSelectionHelper.stopScrolling();
                }
            }
        });

        buildCellPopup(context);
    }

    private void buildCellPopup(Context context) {
        cellPopupLayout = new org.Tajgram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout(context);
        cellPopupLayout.setPadding(dp(1), dp(1), dp(1), dp(1));
        cellPopupLayout.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.menu_copy));
        cellPopupLayout.setAnimationEnabled(false);
        cellPopupLayout.setShownFromBottom(false);

        cellPopupRow = new LinearLayout(context);
        cellPopupRow.setOrientation(LinearLayout.HORIZONTAL);

        headerAction = makePopupItem(context, "Mark Header", v -> handleCellActionHeader());
        mergeAction = makePopupItem(context, "Merge", v -> handleCellActionMerge());
        unmergeAction = makePopupItem(context, "Unmerge", v -> handleCellActionUnmerge());
        delRowAction = makePopupItem(context, "Delete Row", v -> handleCellActionDeleteRow());
        delColAction = makePopupItem(context, "Delete Column", v -> handleCellActionDeleteCol());
        cellPopupRow.addView(headerAction, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 48));
        cellPopupRow.addView(mergeAction, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 48));
        cellPopupRow.addView(unmergeAction, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 48));
        cellPopupRow.addView(delRowAction, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 48));
        cellPopupRow.addView(delColAction, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 48));
        cellPopupLayout.addView(cellPopupRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 48));

        cellPopupLayout.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));

        cellPopupWindow = new org.Tajgram.ui.ActionBar.ActionBarPopupWindow(cellPopupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        cellPopupWindow.setAnimationEnabled(false);
        cellPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        cellPopupWindow.setOutsideTouchable(true);
    }

    private TextView makePopupItem(Context context, String label, View.OnClickListener click) {
        TextView tv = new TextView(context);
        tv.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 2));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(20), 0, dp(20), 0);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
        tv.setText(label);
        tv.setOnClickListener(click);
        return tv;
    }

    private void handleCellActionHeader() {
        if (activeCellSelectionTable == null) return;
        boolean newState = !activeCellSelectionTable.allSelectedHeader();
        activeCellSelectionTable.applyHeaderToggle(newState);
        exitCellSelectionMode();
        updateSendButton(true);
    }

    private void handleCellActionMerge() {
        if (activeCellSelectionTable == null) return;
        if (activeCellSelectionTable.applyMergeFromSelection()) {
            updateCellActionBar();
            updateSendButton(true);
        }
    }

    private void handleCellActionUnmerge() {
        if (activeCellSelectionTable == null) return;
        if (activeCellSelectionTable.applyUnmergeFromSelection()) {
            updateCellActionBar();
            updateSendButton(true);
        }
    }

    private void handleCellActionDeleteRow() {
        if (activeCellSelectionTable == null) return;
        RichTableCell rtc = activeCellSelectionTable;
        rtc.applyDeleteRowsFromSelection();
        finalizeAfterTableStructureChange(rtc);
    }

    private void handleCellActionDeleteCol() {
        if (activeCellSelectionTable == null) return;
        RichTableCell rtc = activeCellSelectionTable;
        rtc.applyDeleteColumnsFromSelection();
        finalizeAfterTableStructureChange(rtc);
    }

    private void finalizeAfterTableStructureChange(RichTableCell rtc) {
        exitCellSelectionMode();
        if (rtc.isEmpty()) {
            BlockRow row = rtc.getRow();
            if (row != null) {
                int idx = rows.indexOf(row);
                if (idx >= 0) {
                    rows.remove(idx);
                    if (rows.isEmpty()) {
                        rows.add(new BlockRow(new TL_iv.pageBlockParagraph()));
                    }
                    listView.adapter.update(true);
                }
            }
        }
        updateSendButton(true);
    }

    public TextSelectionHelper.ArticleTextSelectionHelper getTextSelectionHelper() {
        return textSelectionHelper;
    }

    private boolean sendButtonShown;

    private final RichDividerCell.Delegate dividerDelegate = this::getTextSelectionHelper;

    private final RichMediaCell.Delegate mediaDelegate = new RichMediaCell.Delegate() {
        @Override public void onMediaPick(BlockRow row) { openMediaPicker(row); }
        @Override public TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper() { return textSelectionHelper; }
    };

    private final RichMapCell.Delegate mapDelegate = new RichMapCell.Delegate() {
        @Override public void onPickLocation(BlockRow row) { openLocationPicker(row); }
        @Override public TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper() { return textSelectionHelper; }
    };

    private final RichMathCell.Delegate mathDelegate = new RichMathCell.Delegate() {
        @Override public void onEditMath(BlockRow row) { openMathEditor(row); }
        @Override public TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper() { return textSelectionHelper; }
    };

    private final RichTableCell.Delegate tableDelegate = new RichTableCell.Delegate() {
        @Override public void onTextChanged(BlockRow row) { updateSendButton(true); }
        @Override public TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper() { return textSelectionHelper; }
        @Override public void onRequestWindowFocusable(RichEditText editText, boolean showKeyboard) { parentAlert.makeFocusable(editText, showKeyboard); }
    };

    private final RichTableCell.CellSelectionListener cellSelectionListener = new RichTableCell.CellSelectionListener() {
        @Override
        public void onCellSelectionChanged(RichTableCell table) {
            if (table == activeCellSelectionTable && !table.hasCellSelection()) {
                exitCellSelectionMode();
            } else {
                updateCellActionBar();
            }
        }
    };

    private void enterCellSelectionMode(RichTableCell table, TL_iv.pageTableCell cell) {
        if (activeCellSelectionTable != null && activeCellSelectionTable != table) {
            activeCellSelectionTable.clearCellSelection();
        }
        activeCellSelectionTable = table;
        table.setCellSelectionListener(cellSelectionListener);
        if (textSelectionHelper.isInSelectionMode()) {
            textSelectionHelper.clear();
        }
        setEditTextsLocked(true);
        table.addCellToSelection(cell);
        updateCellActionBar();
    }

    private void exitCellSelectionMode() {
        if (activeCellSelectionTable != null) {
            activeCellSelectionTable.clearCellSelection();
            activeCellSelectionTable = null;
        }
        setEditTextsLocked(false);
        updateCellActionBar();
    }

    private void updateCellActionBar() {
        if (cellPopupWindow == null) return;
        if (activeCellSelectionTable == null || !activeCellSelectionTable.hasCellSelection()) {
            if (cellPopupWindow.isShowing()) cellPopupWindow.dismiss();
            return;
        }
        java.util.Set<TL_iv.pageTableCell> sel = activeCellSelectionTable.getSelectedCells();
        int n = sel.size();
        boolean canMerge = n >= 2 && computeCanMerge(activeCellSelectionTable, sel);
        boolean canUnmerge = n == 1 && computeHasSpan(sel.iterator().next());
        boolean fullRows = computeSpansFullRows(activeCellSelectionTable, sel);
        boolean fullCols = computeSpansFullColumns(activeCellSelectionTable, sel);
        headerAction.setVisibility(VISIBLE);
        headerAction.setText(activeCellSelectionTable.allSelectedHeader() ? "Unmark Header" : "Mark Header");
        mergeAction.setVisibility(canMerge ? VISIBLE : GONE);
        unmergeAction.setVisibility(canUnmerge ? VISIBLE : GONE);
        delRowAction.setVisibility(fullRows ? VISIBLE : GONE);
        delColAction.setVisibility(fullCols ? VISIBLE : GONE);
        showOrUpdateCellPopup();
    }

    private void showOrUpdateCellPopup() {
        if (cellPopupWindow == null || activeCellSelectionTable == null) return;
        cellPopupRow.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        cellPopupLayout.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int popupW = cellPopupLayout.getMeasuredWidth();
        int popupH = cellPopupLayout.getMeasuredHeight();
        int[] pos = computeCellPopupTopLeft(popupW, popupH);
        if (cellPopupWindow.isShowing()) {
            cellPopupWindow.update(pos[0], pos[1], -1, -1);
        } else {
            cellPopupWindow.showAtLocation(this, Gravity.NO_GRAVITY, pos[0], pos[1]);
            cellPopupWindow.startAnimation();
        }
    }

    private int[] computeCellPopupTopLeft(int popupW, int popupH) {
        java.util.Set<TL_iv.pageTableCell> sel = activeCellSelectionTable.getSelectedCells();
        int minY = Integer.MAX_VALUE;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int[] tmp = new int[2];
        for (TL_iv.pageTableCell c : sel) {
            RichTableCellHost host = activeCellSelectionTable.getGrid().hostForAnchor(c);
            if (host == null) continue;
            host.getLocationOnScreen(tmp);
            if (tmp[1] < minY) minY = tmp[1];
            if (tmp[0] < minX) minX = tmp[0];
            if (tmp[0] + host.getWidth() > maxX) maxX = tmp[0] + host.getWidth();
        }
        if (minY == Integer.MAX_VALUE) {
            activeCellSelectionTable.getLocationOnScreen(tmp);
            minY = tmp[1]; minX = tmp[0]; maxX = tmp[0] + activeCellSelectionTable.getWidth();
        }
        int centerX = (minX + maxX) / 2;
        int x = centerX - popupW / 2;
        int y = minY - popupH - dp(8);
        int screenW = AndroidUtilities.displaySize.x;
        if (x < dp(8)) x = dp(8);
        if (x + popupW > screenW - dp(8)) x = screenW - dp(8) - popupW;
        if (y < dp(8)) y = dp(8);
        return new int[] { x, y };
    }

    private static boolean computeHasSpan(TL_iv.pageTableCell c) {
        return TableModel.spanCol(c) > 1 || TableModel.spanRow(c) > 1;
    }

    private static boolean computeCanMerge(RichTableCell table, java.util.Set<TL_iv.pageTableCell> sel) {
        TableModel m = table.getModel();
        if (m == null) return false;
        int minR = Integer.MAX_VALUE, minC = Integer.MAX_VALUE, maxR = -1, maxC = -1;
        for (TL_iv.pageTableCell c : sel) {
            int ar = m.anchorRowOf(c), ac = m.anchorColOf(c);
            int rs = TableModel.spanRow(c), cs = TableModel.spanCol(c);
            minR = Math.min(minR, ar); minC = Math.min(minC, ac);
            maxR = Math.max(maxR, ar + rs - 1); maxC = Math.max(maxC, ac + cs - 1);
        }
        java.util.Set<TL_iv.pageTableCell> coveredAnchors = new java.util.HashSet<>();
        for (int r = minR; r <= maxR; r++) {
            for (int c = minC; c <= maxC; c++) {
                if (r < 0 || c < 0 || r >= m.rowCount || c >= m.colCount) return false;
                coveredAnchors.add(m.grid[r][c]);
            }
        }
        return coveredAnchors.equals(new java.util.HashSet<>(sel));
    }

    private static boolean computeSpansFullRows(RichTableCell table, java.util.Set<TL_iv.pageTableCell> sel) {
        TableModel m = table.getModel();
        if (m == null) return false;
        java.util.Set<Integer> rowsHit = new java.util.HashSet<>();
        for (TL_iv.pageTableCell c : sel) rowsHit.add(m.anchorRowOf(c));
        if (rowsHit.isEmpty()) return false;
        for (int r : rowsHit) {
            for (int c = 0; c < m.colCount; c++) {
                if (m.anchorR[r][c] != r) return false;
                if (!sel.contains(m.grid[r][c])) return false;
            }
        }
        return true;
    }

    private static boolean computeSpansFullColumns(RichTableCell table, java.util.Set<TL_iv.pageTableCell> sel) {
        TableModel m = table.getModel();
        if (m == null) return false;
        java.util.Set<Integer> colsHit = new java.util.HashSet<>();
        for (TL_iv.pageTableCell c : sel) colsHit.add(m.anchorColOf(c));
        if (colsHit.isEmpty()) return false;
        for (int c : colsHit) {
            for (int r = 0; r < m.rowCount; r++) {
                if (m.anchorC[r][c] != c) return false;
                if (!sel.contains(m.grid[r][c])) return false;
            }
        }
        return true;
    }

    private final RichTextCell.Delegate cellDelegate = new RichTextCell.Delegate() {
        @Override public void onEnter(BlockRow row) { onCellEnter(row); }
        @Override public void onBackspace(BlockRow row) { onCellBackspace(row); }
        @Override public boolean onBackspaceAtStart(BlockRow row) { return onCellBackspaceAtStart(row); }
        @Override public void onTextChanged(BlockRow row) { updateSendButton(true); }
        @Override public void onTransform(BlockRow row, TL_iv.PageBlock newBlock, int newLevel, int newNum) { transformRow(row, newBlock, newLevel, newNum); }
        @Override public TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper() { return textSelectionHelper; }
        @Override public boolean onIndent(BlockRow row, boolean outdent) { return onCellIndent(row, outdent); }
        @Override public void onRequestWindowFocusable(RichEditText editText, boolean showKeyboard) { parentAlert.makeFocusable(editText, showKeyboard); }
    };

    private boolean onCellBackspaceAtStart(BlockRow row) {
        int idx = rows.indexOf(row);
        if (idx <= 0) return false;
        BlockRow prev = rows.get(idx - 1);
        if (prev.block instanceof TL_iv.pageBlockDivider) {
            rows.remove(idx - 1);
            renumberAllRuns();
            listView.adapter.update(true);
            listView.post(() -> focusRow(row));
            return true;
        }
        return false;
    }

    private void transformRow(BlockRow row, TL_iv.PageBlock newBlock, int newLevel, int newNum) {
        int idx = rows.indexOf(row);
        if (idx < 0) return;
        row.block = newBlock;
        row.level = newLevel;
        row.num = newNum;
        renumberAllRuns();
        BlockRow toFocus;
        if (newBlock instanceof TL_iv.pageBlockTable) {
            if (idx + 1 >= rows.size() || isNonText(rows.get(idx + 1).block)) {
                rows.add(idx + 1, new BlockRow(new TL_iv.pageBlockParagraph()));
            }
            toFocus = row;
        } else if (isNonText(newBlock)) {
            if (isMedia(newBlock) && row.media == null) {
                row.media = new MediaUploadState();
            }
            if (idx + 1 < rows.size() && !isNonText(rows.get(idx + 1).block)) {
                toFocus = rows.get(idx + 1);
            } else {
                BlockRow next = new BlockRow(new TL_iv.pageBlockParagraph());
                rows.add(idx + 1, next);
                toFocus = next;
            }
        } else {
            toFocus = row;
        }
        listView.adapter.update(false);
        if (newBlock instanceof TL_iv.pageBlockMath) {
            listView.post(() -> openMathEditor(row));
            return;
        }
        if (newBlock instanceof TL_iv.pageBlockMap) {
            listView.post(() -> openLocationPicker(row));
            return;
        }
        final BlockRow target = toFocus;
        listView.post(() -> {
            View v = listView.findViewByItemObject(target);
            if (v instanceof RichTextCell) {
                RichTextCell c = (RichTextCell) v;
                c.requestEditFocus();
                int len = c.getEditText().length();
                c.getEditText().setSelection(len);
            } else if (v instanceof RichTableCell) {
                RichTableCell rtc = (RichTableCell) v;
                if (rtc.getGrid().getChildCount() > 0) {
                    View first = rtc.getGrid().getChildAt(0);
                    if (first instanceof RichTableCellHost) {
                        ((RichTableCellHost) first).editText.requestEditFocus();
                    }
                }
            }
        });
    }

    private static boolean isNonText(TL_iv.PageBlock b) {
        return b instanceof TL_iv.pageBlockDivider
            || b instanceof TL_iv.pageBlockPhoto
            || b instanceof TL_iv.pageBlockVideo
            || b instanceof TL_iv.pageBlockMath
            || b instanceof TL_iv.pageBlockMap
            || b instanceof TL_iv.pageBlockTable;
    }

    private static boolean isMedia(TL_iv.PageBlock b) {
        return b instanceof TL_iv.pageBlockPhoto || b instanceof TL_iv.pageBlockVideo;
    }

    private boolean hasAnyText() {
        for (int i = 0; i < rows.size(); i++) {
            BlockRow r = rows.get(i);
            if (!RichTextCell.readPlainText(r.block).isEmpty()) return true;
            if (isMedia(r.block) && r.media != null
                && (r.media.isReady() || r.media.isPending())) return true;
            if (r.block instanceof TL_iv.pageBlockMath && !android.text.TextUtils.isEmpty(((TL_iv.pageBlockMath) r.block).source)) return true;
            if (r.block instanceof TL_iv.pageBlockMap && ((TL_iv.pageBlockMap) r.block).geo != null) return true;
            if (r.block instanceof TL_iv.pageBlockTable && tableHasText((TL_iv.pageBlockTable) r.block)) return true;
        }
        return false;
    }

    private static boolean tableHasText(TL_iv.pageBlockTable t) {
        if (t.rows == null) return false;
        for (int i = 0; i < t.rows.size(); i++) {
            final TL_iv.pageTableRow row = t.rows.get(i);
            for (int j = 0; j < row.cells.size(); j++) {
                if (!TableModel.readPlainText(row.cells.get(j)).isEmpty()) return true;
            }
        }
        return false;
    }

    private boolean hasPendingUploads() {
        for (int i = 0; i < rows.size(); i++) {
            BlockRow r = rows.get(i);
            if (r.media != null && r.media.isPending()) return true;
        }
        return false;
    }

    private void updateSendButton(boolean animated) {
        boolean shouldShow = hasAnyText();
        if (shouldShow == sendButtonShown) return;
        sendButtonShown = shouldShow;
        parentAlert.showSendButtonOnly(shouldShow, animated);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        for (int i = 0; i < rows.size(); i++) {
            BlockRow row = rows.get(i);
            if (row.block instanceof TL_iv.pageBlockDivider) {
                items.add(RichDividerCell.Factory.of(row, dividerDelegate));
            } else if (isMedia(row.block)) {
                items.add(RichMediaCell.Factory.of(row, mediaDelegate));
            } else if (row.block instanceof TL_iv.pageBlockMap) {
                items.add(RichMapCell.Factory.of(row, mapDelegate));
            } else if (row.block instanceof TL_iv.pageBlockMath) {
                items.add(RichMathCell.Factory.of(row, mathDelegate));
            } else if (row.block instanceof TL_iv.pageBlockTable) {
                items.add(RichTableCell.Factory.of(row, tableDelegate));
            } else {
                boolean forceHint = false;
                if (rows.size() == 2 && i == 1 && row.block instanceof TL_iv.pageBlockParagraph && rows.get(0).block instanceof TL_iv.pageBlockHeading1) {
                    forceHint = true;
                }
                items.add(RichTextCell.Factory.of(row, cellDelegate, forceHint));
            }
        }
//        items.add(UItem.asCustom(newBlockButtonContainer));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (view instanceof RichTextCell) {
            ((RichTextCell) view).requestEditFocus();
        }
    }

    private void onCellEnter(BlockRow row) {
        int idx = rows.indexOf(row);
        if (idx < 0) return;
        boolean empty = RichTextCell.readPlainText(row.block).isEmpty();
        if (empty && row.level > 0) {
            cascadeOutdent(idx);
            renumberAllRuns();
            listView.adapter.update(false);
            listView.post(() -> focusRow(row));
            return;
        }
        TL_iv.pageBlockParagraph next = new TL_iv.pageBlockParagraph();
        int nextNum = row.num > 0 ? row.num + 1 : row.num;
        BlockRow nextRow = new BlockRow(next, row.level, nextNum);
        rows.add(idx + 1, nextRow);
        renumberAllRuns();
        listView.adapter.update(true);
        listView.post(() -> focusRow(nextRow));
    }

    private void onCellBackspace(BlockRow row) {
        int idx = rows.indexOf(row);
        if (idx < 0) return;
        if (row.level > 0) {
            cascadeOutdent(idx);
            renumberAllRuns();
            listView.adapter.update(false);
            listView.post(() -> focusRow(row));
            return;
        }
        if (idx <= 0) return;
        BlockRow prev = rows.get(idx - 1);
        rows.remove(idx);
        renumberAllRuns();
        listView.adapter.update(true);
        listView.post(() -> focusRow(prev));
    }

    private boolean onCellIndent(BlockRow row, boolean outdent) {
        int idx = rows.indexOf(row);
        Log.d(TAG, "onCellIndent idx=" + idx + " outdent=" + outdent + " level=" + (row != null ? row.level : -99) + " num=" + (row != null ? row.num : -99));
        if (idx < 0) return false;
        int caret = captureCaret(row);
        if (!indentRow(idx, outdent, false)) return false;
        renumberAllRuns();
        listView.adapter.update(false);
        restoreCaret(row, caret);
        return true;
    }

    private boolean rangeIndent(int sCell, int eCell, boolean outdent) {
        if (sCell < 0 || eCell < sCell || eCell >= rows.size()) return false;
        Log.d(TAG, "rangeIndent sCell=" + sCell + " eCell=" + eCell + " outdent=" + outdent);
        boolean anyChanged = false;
        if (outdent) {
            for (int i = eCell; i >= sCell; i--) {
                if (indentRow(i, true, true)) anyChanged = true;
            }
        } else {
            BlockRow first = rows.get(sCell);
            if (first.level >= 1) {
                if (sCell == 0 || rows.get(sCell - 1).level < first.level) {
                    Log.d(TAG, "  rangeIndent reject: orphan on first row");
                    return false;
                }
            }
            for (int i = sCell; i <= eCell; i++) {
                if (indentRow(i, false, true)) anyChanged = true;
            }
        }
        if (anyChanged) {
            renumberAllRuns();
            listView.adapter.update(false);
        }
        return anyChanged;
    }

    private boolean indentRow(int idx, boolean outdent, boolean skipOrphanCheck) {
        if (idx < 0 || idx >= rows.size()) return false;
        BlockRow row = rows.get(idx);
        if (outdent) {
            if (row.level <= 0) return false;
            cascadeOutdent(idx);
            return true;
        }
        if (row.level == 0) {
            if (!(row.block instanceof TL_iv.pageBlockParagraph)) return false;
            row.level = 1;
            if (idx > 0) {
                BlockRow prev = rows.get(idx - 1);
                row.num = prev.num > 0 ? 1 : 0;
            } else {
                row.num = 0;
            }
            return true;
        }
        if (!skipOrphanCheck) {
            if (idx == 0) return false;
            if (rows.get(idx - 1).level < row.level) return false;
        }
        row.level++;
        return true;
    }

    private void cascadeOutdent(int idx) {
        BlockRow row = rows.get(idx);
        int oldLevel = row.level;
        if (oldLevel <= 0) return;
        int newLevel = oldLevel - 1;
        row.level = newLevel;
        if (newLevel == 0) row.num = 0;
        for (int i = idx + 1; i < rows.size(); i++) {
            BlockRow r = rows.get(i);
            if (r.level <= oldLevel) break;
            r.level--;
        }
    }

    private int captureCaret(BlockRow row) {
        View v = listView.findViewByItemObject(row);
        if (v instanceof RichTextCell) {
            RichTextCell c = (RichTextCell) v;
            if (c.getEditText().isFocused()) {
                return c.getEditText().getSelectionEnd();
            }
        }
        return -1;
    }

    private void restoreCaret(BlockRow row, int caret) {
        if (caret < 0) return;
        listView.post(() -> {
            View v = listView.findViewByItemObject(row);
            if (v instanceof RichTextCell) {
                RichTextCell c = (RichTextCell) v;
                c.requestEditFocus();
                int len = c.getEditText().length();
                c.getEditText().setSelection(Math.max(0, Math.min(caret, len)));
            }
        });
    }

    private void focusRow(BlockRow row) {
        View v = listView.findViewByItemObject(row);
        if (v instanceof RichTextCell) {
            ((RichTextCell) v).requestEditFocus();
        }
    }

    private void restoreFocusAt(int adapterPos, int childPos, int offset) {
        if (adapterPos < 0) return;
        View v = listView.layoutManager.findViewByPosition(adapterPos);
        if (v instanceof RichTextCell) {
            RichTextCell c = (RichTextCell) v;
            c.requestEditFocus();
            int len = c.getEditText().length();
            c.getEditText().setSelection(Math.max(0, Math.min(offset, len)));
        } else if (v instanceof RichTableCell) {
            RichTableCell rtc = (RichTableCell) v;
            TableModel m = rtc.getModel();
            if (m == null) return;
            if (childPos < 0 || childPos >= m.anchors().size()) childPos = 0;
            RichTableCellHost host = rtc.getGrid().hostForAnchor(m.anchors().get(childPos));
            if (host == null) return;
            host.editText.requestEditFocus();
            int len = host.editText.length();
            host.editText.setSelection(Math.max(0, Math.min(offset, len)));
        }
    }

    private void renumberAllRuns() {
        for (int i = 0; i < rows.size(); i++) {
            BlockRow r = rows.get(i);
            if (r.level <= 0 || r.num <= 0) continue;
            int L = r.level;
            int counter = 1;
            for (int j = i - 1; j >= 0; j--) {
                BlockRow p = rows.get(j);
                if (p.level < L) break;
                if (p.level == L) {
                    if (p.num <= 0) break;
                    counter++;
                }
            }
            r.num = counter;
        }
    }

    @Override
    public int needsActionBar() {
        return 1;
    }

    @Override
    public int getListTopPadding() {
        return listView.getPaddingTop();
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
                top = (int) child.getY();
            }
        }
        if (top == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        int newOffset = dp(7);
        if (top >= dp(7) && hadFirstChild) {
            newOffset = top;
        }
        listView.setTopGlowOffset(newOffset);
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
        LayoutParams layoutParams = (LayoutParams) getLayoutParams();
        layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();

        int paddingTop;
        if (parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight() > dp(20)) {
            // keyboard is open: keep a fixed small top inset so the content doesn't jump
            // as availableHeight shrinks — the pan animation handles the upward shift
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
        if (listView.getPaddingTop() != paddingTop || listView.getPaddingBottom() != listPaddingBottom) {
            ignoreLayout = true;
            listView.setPaddingWithoutRequestLayout(0, paddingTop, 0, listPaddingBottom);
            ignoreLayout = false;
        }
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
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (textSelectionHelper.isInSelectionMode() && textSelectionOverlay.onTouchEvent(ev)) {
            return true;
        }
        if (textSelectionOverlay.checkOnTap(ev)) {
            ev.setAction(MotionEvent.ACTION_CANCEL);
        }
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                pressX = ev.getX();
                pressY = ev.getY();
                pressMoved = false;
                longPressConsumed = false;
                pressTarget = findCellUnder((int) pressX, (int) pressY);
                if (pressTarget instanceof TextSelectionHelper.ArticleSelectableView) {
                    if (longPressRunnable != null) removeCallbacks(longPressRunnable);
                    longPressRunnable = () -> {
                        if (pressTarget == null) return;
                        if (textSelectionHelper.isInSelectionMode()) return;
                        if (pressTarget instanceof RichTextCell) {
                            RichTextCell cell = (RichTextCell) pressTarget;
                            int localX = (int) (pressX - cell.getLeft() - listView.getLeft());
                            int localY = (int) (pressY - cell.getTop() - listView.getTop());
                            textSelectionHelper.setMaybeView(localX, localY, cell);
                            textSelectionHelper.trySelect(cell);
                            longPressConsumed = true;
                        } else if (pressTarget instanceof RichTableCell) {
                            RichTableCell cell = (RichTableCell) pressTarget;
                            int localX = (int) (pressX - cell.getLeft() - listView.getLeft());
                            int localY = (int) (pressY - cell.getTop() - listView.getTop());
                            if (cell.isPressOnText(localX, localY)) {
                                textSelectionHelper.setMaybeView(localX, localY, cell);
                                textSelectionHelper.trySelect(cell);
                            } else {
                                TL_iv.pageTableCell tableCell = cell.findCellAt(localX, localY);
                                if (tableCell != null) {
                                    enterCellSelectionMode(cell, tableCell);
                                }
                            }
                            longPressConsumed = true;
                        } else if (pressTarget instanceof RichDividerCell) {
                            textSelectionHelper.selectRangeOf((RichDividerCell) pressTarget, 0, 1);
                            longPressConsumed = true;
                        }
                    };
                    postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                float dx = ev.getX() - pressX;
                float dy = ev.getY() - pressY;
                if (dx * dx + dy * dy > dp(8) * dp(8)) {
                    pressMoved = true;
                    if (longPressRunnable != null) {
                        removeCallbacks(longPressRunnable);
                        longPressRunnable = null;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (longPressRunnable != null) {
                    removeCallbacks(longPressRunnable);
                    longPressRunnable = null;
                }
                if (!pressMoved && !longPressConsumed && activeCellSelectionTable != null) {
                    if (pressTarget == activeCellSelectionTable) {
                        RichTableCell rtc = (RichTableCell) pressTarget;
                        int localX = (int) (ev.getX() - rtc.getLeft() - listView.getLeft());
                        int localY = (int) (ev.getY() - rtc.getTop() - listView.getTop());
                        TL_iv.pageTableCell tCell = rtc.findCellAt(localX, localY);
                        if (tCell != null) {
                            rtc.toggleCellSelection(tCell);
                            pressTarget = null;
                            return true;
                        }
                    } else if (pressTarget != null && pressTarget != activeCellSelectionTable) {
                        exitCellSelectionMode();
                    }
                }
                pressTarget = null;
                longPressConsumed = false;
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                if (longPressRunnable != null) {
                    removeCallbacks(longPressRunnable);
                    longPressRunnable = null;
                }
                pressTarget = null;
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (ev.getAction() == KeyEvent.ACTION_DOWN) {
            int code = ev.getKeyCode();
            boolean shift = ev.isShiftPressed();
            boolean ctrl = ev.isCtrlPressed();
            boolean alt = ev.isAltPressed();
            boolean helperActive = textSelectionHelper.isInSelectionMode();
            Log.d(TAG, "dispatchKeyEvent code=" + code + " shift=" + shift + " ctrl=" + ctrl + " alt=" + alt + " helperActive=" + helperActive + " isFocused=" + isFocused() + " hasFocus=" + hasFocus());

            if (code == KeyEvent.KEYCODE_ESCAPE && helperActive) {
                Log.d(TAG, "ESC -> helper.clear()");
                textSelectionHelper.clear();
                return true;
            }

            if (helperActive) {
                if (ctrl && !shift && code == KeyEvent.KEYCODE_C) { Log.d(TAG, "Ctrl+C copy"); copyHelperSelection(); return true; }
                if (ctrl && !shift && code == KeyEvent.KEYCODE_X) { Log.d(TAG, "Ctrl+X cut"); cutHelperSelection(); return true; }
                if (ctrl && code == KeyEvent.KEYCODE_V) { Log.d(TAG, "Ctrl+V paste"); pasteAtHelperSelection(); return true; }
                if (code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_FORWARD_DEL) {
                    Log.d(TAG, "DEL/BACKSPACE -> deleteHelperSelection");
                    deleteHelperSelection();
                    return true;
                }
                if (code == KeyEvent.KEYCODE_ENTER) {
                    Log.d(TAG, "ENTER -> replace with newline");
                    replaceHelperSelectionWith("\n");
                    return true;
                }
                if (!ctrl && !alt) {
                    int uc = ev.getUnicodeChar(ev.getMetaState());
                    if (uc >= 32) {
                        Log.d(TAG, "printable uc=" + uc + " -> replace");
                        replaceHelperSelectionWith(String.valueOf((char) uc));
                        return true;
                    }
                }
            }

            if (helperActive && shift && isArrowKey(code)) {
                Log.d(TAG, "shift+arrow code=" + code + " -> tryExtendSelectionAcrossCells");
                if (tryExtendSelectionAcrossCells(code, ctrl || alt)) return true;
            }
            if (helperActive && !shift && !ctrl && !alt && isArrowKey(code)) {
                boolean toEnd = code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_DOWN;
                restoreFocusCell = toEnd ? textSelectionHelper.getEndCell() : textSelectionHelper.getStartCell();
                restoreFocusOffset = toEnd ? textSelectionHelper.getEndOffset() : textSelectionHelper.getStartOffset();
                restoreFocusChildPosition = toEnd ? textSelectionHelper.getEndChildPosition() : textSelectionHelper.getStartChildPosition();
                Log.d(TAG, "plain arrow collapse toEnd=" + toEnd + " -> cell=" + restoreFocusCell + " child=" + restoreFocusChildPosition + " off=" + restoreFocusOffset);
                textSelectionHelper.clear();
                return true;
            }
            if (ctrl && code == KeyEvent.KEYCODE_A) {
                Log.d(TAG, "ctrl+a -> tryEscalateSelectAll");
                if (tryEscalateSelectAll()) return true;
            }

            if (!helperActive && !shift && !ctrl && !alt && (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_UP)) {
                if (tryPlainArrowAcrossCells(code)) return true;
            }

            if (!helperActive && !ctrl && !alt && code == KeyEvent.KEYCODE_TAB) {
                View focused = findFocus();
                Log.d(TAG, "outer-pre TAB shift=" + shift + " focused=" + (focused != null ? focused.getClass().getSimpleName() : "null"));
                if (focused instanceof RichEditText) {
                    RichTableCell rtc = findTableCellAncestor(focused);
                    if (rtc != null) {
                        RichTableCellHost host = rtc.findHostContaining(focused);
                        Log.d(TAG, "outer-pre TAB table=" + (rtc != null) + " host=" + (host != null));
                        if (host != null && rtc.moveFocusByTab(host, shift)) return true;
                    }
                }
            }

            if (code == KeyEvent.KEYCODE_TAB && isFocused()) {
                if (helperActive) {
                    int s = textSelectionHelper.getStartCell();
                    int e = textSelectionHelper.getEndCell();
                    Log.d(TAG, "outer TAB shift=" + shift + " helper s=" + s + " e=" + e);
                    if (s >= 0 && e >= s) {
                        if (s == e) {
                            onCellIndent(rows.get(s), shift);
                        } else {
                            rangeIndent(s, e, shift);
                        }
                    }
                } else {
                    BlockRow row = findFocusedRow();
                    Log.d(TAG, "outer TAB shift=" + shift + " focused row=" + (row != null ? rows.indexOf(row) : -1));
                    if (row != null) {
                        onCellIndent(row, shift);
                    }
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(ev);
    }

    private boolean tryPlainArrowAcrossCells(int code) {
        BlockRow focused = findFocusedRow();
        if (focused == null) return false;
        int idx = rows.indexOf(focused);
        if (idx < 0) return false;
        View v = listView.findViewByItemObject(focused);
        if (!(v instanceof RichTextCell)) return false;
        RichTextCell cell = (RichTextCell) v;
        Layout layout = cell.getEditText().getLayout();
        if (layout == null) return false;
        int caret = cell.getEditText().getSelectionEnd();
        int line = layout.getLineForOffset(caret);
        boolean down = code == KeyEvent.KEYCODE_DPAD_DOWN;
        if (down) {
            if (line < layout.getLineCount() - 1) return false;
            int nextIdx = findNextTextRow(idx + 1, +1);
            if (nextIdx < 0) {
                BlockRow para = new BlockRow(new TL_iv.pageBlockParagraph());
                rows.add(para);
                listView.adapter.update(true);
                listView.post(() -> focusRow(para));
            } else {
                BlockRow next = rows.get(nextIdx);
                listView.post(() -> focusRow(next));
            }
            return true;
        } else {
            if (line > 0) return false;
            int prevIdx = findNextTextRow(idx - 1, -1);
            if (prevIdx < 0) return false;
            BlockRow prev = rows.get(prevIdx);
            listView.post(() -> {
                focusRow(prev);
                View pv = listView.findViewByItemObject(prev);
                if (pv instanceof RichTextCell) {
                    RichEditText et = ((RichTextCell) pv).getEditText();
                    et.setSelection(et.length());
                }
            });
            return true;
        }
    }

    private RichTableCell findTableCellAncestor(View v) {
        android.view.ViewParent p = v == null ? null : v.getParent();
        while (p != null) {
            if (p instanceof RichTableCell) return (RichTableCell) p;
            p = p.getParent();
        }
        return null;
    }

    private int findNextTextRow(int start, int step) {
        int i = start;
        while (i >= 0 && i < rows.size()) {
            if (!isNonText(rows.get(i).block)) return i;
            i += step;
        }
        return -1;
    }

    private BlockRow findFocusedRow() {
        for (int i = 0; i < listView.getChildCount(); i++) {
            View c = listView.getChildAt(i);
            if (c instanceof RichTextCell) {
                RichTextCell rc = (RichTextCell) c;
                if (rc.getEditText().isFocused()) return rc.getRow();
            }
        }
        return null;
    }

    private static boolean isArrowKey(int code) {
        return code == KeyEvent.KEYCODE_DPAD_LEFT
            || code == KeyEvent.KEYCODE_DPAD_RIGHT
            || code == KeyEvent.KEYCODE_DPAD_UP
            || code == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    private RichTextCell cellAt(int adapterPos) {
        if (adapterPos < 0) return null;
        View v = listView.layoutManager.findViewByPosition(adapterPos);
        return v instanceof RichTextCell ? (RichTextCell) v : null;
    }

    private View selectableAt(int adapterPos) {
        if (adapterPos < 0) return null;
        return listView.layoutManager.findViewByPosition(adapterPos);
    }

    private int prevTextOffset(int adapterPos) {
        View v = selectableAt(adapterPos);
        if (v instanceof RichTextCell) {
            Layout pl = ((RichTextCell) v).getEditText().getLayout();
            return pl != null ? pl.getText().length() : 0;
        }
        return 0;
    }

    private boolean tryExtendSelectionAcrossCells(int code, boolean byWord) {
        int anchorPos = textSelectionHelper.getAnchorCell();
        int anchorChild = textSelectionHelper.getAnchorChildPosition();
        int anchorOff = textSelectionHelper.getAnchorOffset();
        int sPos = textSelectionHelper.getStartCell();
        int sChild = textSelectionHelper.getStartChildPosition();
        int sOff = textSelectionHelper.getStartOffset();
        int ePos = textSelectionHelper.getEndCell();
        int eChild = textSelectionHelper.getEndChildPosition();
        int eOff = textSelectionHelper.getEndOffset();
        Log.d(TAG, "extend anchor=(" + anchorPos + "," + anchorChild + "," + anchorOff + ") sel=[(" + sPos + "," + sChild + "," + sOff + ")..(" + ePos + "," + eChild + "," + eOff + ")] byWord=" + byWord);

        int curPos, curChild, curOff;
        if (anchorPos == sPos && anchorChild == sChild && anchorOff == sOff) {
            curPos = ePos; curChild = eChild; curOff = eOff;
        } else {
            curPos = sPos; curChild = sChild; curOff = sOff;
        }

        View curView = selectableAt(curPos);
        if (curView == null) { Log.d(TAG, "  selectableAt(" + curPos + ") null, bail"); return false; }

        int newPos = curPos;
        int newChild = curChild;
        int newOff = curOff;

        if (curView instanceof RichTableCell) {
            RichTableCell rtc = (RichTableCell) curView;
            TableModel m = rtc.getModel();
            if (m == null || m.anchors().isEmpty()) return false;
            if (curChild < 0 || curChild >= m.anchors().size()) curChild = 0;
            TL_iv.pageTableCell anchorCell = m.anchors().get(curChild);
            RichTableCellHost host = rtc.getGrid().hostForAnchor(anchorCell);
            Layout layout = host != null ? host.editText.getLayout() : null;
            if (layout == null) return false;
            CharSequence text = layout.getText();
            int textLen = text.length();
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    newOff = byWord ? wordRight(text, curOff) : Math.min(textLen, curOff + 1);
                    if (newOff == curOff && curOff >= textLen) {
                        if (curChild + 1 < m.anchors().size()) {
                            newChild = curChild + 1; newOff = 0;
                        } else if (curPos + 1 < rows.size()) {
                            newPos = curPos + 1; newChild = 0; newOff = 0;
                        }
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    newOff = byWord ? wordLeft(text, curOff) : Math.max(0, curOff - 1);
                    if (newOff == curOff && curOff <= 0) {
                        if (curChild - 1 >= 0) {
                            newChild = curChild - 1;
                            RichTableCellHost prev = rtc.getGrid().hostForAnchor(m.anchors().get(newChild));
                            Layout pl = prev != null ? prev.editText.getLayout() : null;
                            newOff = pl != null ? pl.getText().length() : 0;
                        } else if (curPos - 1 >= 0) {
                            newPos = curPos - 1; newChild = 0;
                            newOff = prevTextOffset(newPos);
                        }
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN: {
                    int line = layout.getLineForOffset(curOff);
                    if (line + 1 < layout.getLineCount()) {
                        float x = layout.getPrimaryHorizontal(curOff);
                        newOff = layout.getOffsetForHorizontal(line + 1, x);
                    } else {
                        int below = findTableAnchorBelow(m, curChild);
                        if (below >= 0) {
                            newChild = below; newOff = 0;
                        } else if (curPos + 1 < rows.size()) {
                            newPos = curPos + 1; newChild = 0; newOff = 0;
                        } else {
                            newOff = textLen;
                        }
                    }
                    break;
                }
                case KeyEvent.KEYCODE_DPAD_UP: {
                    int line = layout.getLineForOffset(curOff);
                    if (line - 1 >= 0) {
                        float x = layout.getPrimaryHorizontal(curOff);
                        newOff = layout.getOffsetForHorizontal(line - 1, x);
                    } else {
                        int above = findTableAnchorAbove(m, curChild);
                        if (above >= 0) {
                            newChild = above;
                            RichTableCellHost prev = rtc.getGrid().hostForAnchor(m.anchors().get(newChild));
                            Layout pl = prev != null ? prev.editText.getLayout() : null;
                            newOff = pl != null ? pl.getText().length() : 0;
                        } else if (curPos - 1 >= 0) {
                            newPos = curPos - 1; newChild = 0;
                            newOff = prevTextOffset(newPos);
                        } else {
                            newOff = 0;
                        }
                    }
                    break;
                }
            }
        } else if (curView instanceof RichTextCell) {
            Layout layout = ((RichTextCell) curView).getEditText().getLayout();
            if (layout == null) return false;
            CharSequence text = layout.getText();
            int textLen = text.length();
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    newOff = byWord ? wordRight(text, curOff) : Math.min(textLen, curOff + 1);
                    if (newOff == curOff && curOff >= textLen && curPos + 1 < rows.size()) {
                        newPos = curPos + 1; newOff = 0;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    newOff = byWord ? wordLeft(text, curOff) : Math.max(0, curOff - 1);
                    if (newOff == curOff && curOff <= 0 && curPos - 1 >= 0) {
                        newPos = curPos - 1;
                        newOff = prevTextOffset(newPos);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN: {
                    int line = layout.getLineForOffset(curOff);
                    if (line + 1 < layout.getLineCount()) {
                        float x = layout.getPrimaryHorizontal(curOff);
                        newOff = layout.getOffsetForHorizontal(line + 1, x);
                    } else if (curPos + 1 < rows.size()) {
                        newPos = curPos + 1; newOff = 0;
                    } else {
                        newOff = textLen;
                    }
                    break;
                }
                case KeyEvent.KEYCODE_DPAD_UP: {
                    int line = layout.getLineForOffset(curOff);
                    if (line - 1 >= 0) {
                        float x = layout.getPrimaryHorizontal(curOff);
                        newOff = layout.getOffsetForHorizontal(line - 1, x);
                    } else if (curPos - 1 >= 0) {
                        newPos = curPos - 1;
                        newOff = prevTextOffset(newPos);
                    } else {
                        newOff = 0;
                    }
                    break;
                }
            }
        } else if (curView instanceof RichDividerCell || curView instanceof RichMediaCell) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (curPos + 1 < rows.size()) {
                        newPos = curPos + 1; newOff = 0;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (curPos - 1 >= 0) {
                        newPos = curPos - 1;
                        newOff = prevTextOffset(newPos);
                    }
                    break;
            }
        } else {
            return false;
        }

        Log.d(TAG, "  -> target=(" + newPos + "," + newChild + "," + newOff + ")");

        if (newPos == curPos && newChild == curChild && newOff == curOff) return true;

        if (newPos != curPos) {
            listView.scrollToPosition(newPos);
        }
        final int targetPos = newPos;
        final int targetChild = newChild;
        final int targetOff = newOff;
        View targetView = selectableAt(targetPos);
        if (!(targetView instanceof TextSelectionHelper.ArticleSelectableView)) {
            Log.d(TAG, "  target not laid out, posting retry");
            post(() -> {
                View t = selectableAt(targetPos);
                if (t instanceof TextSelectionHelper.ArticleSelectableView) {
                    textSelectionHelper.extendSelectionTo((TextSelectionHelper.ArticleSelectableView) t, targetChild, targetOff);
                }
            });
            return true;
        }
        boolean ok = textSelectionHelper.extendSelectionTo((TextSelectionHelper.ArticleSelectableView) targetView, targetChild, targetOff);
        Log.d(TAG, "  extendSelectionTo returned " + ok);
        return ok;
    }

    private int findTableAnchorBelow(TableModel m, int curChild) {
        if (curChild < 0 || curChild >= m.anchors().size()) return -1;
        TL_iv.pageTableCell c = m.anchors().get(curChild);
        int aR = m.anchorRowOf(c);
        int aC = m.anchorColOf(c);
        int targetR = aR + Math.max(1, TableModel.spanRow(c));
        if (targetR >= m.rowCount) return -1;
        TL_iv.pageTableCell tCell = m.grid[targetR][Math.min(aC, m.colCount - 1)];
        return m.flatIndexOfAnchor(tCell);
    }

    private int findTableAnchorAbove(TableModel m, int curChild) {
        if (curChild < 0 || curChild >= m.anchors().size()) return -1;
        TL_iv.pageTableCell c = m.anchors().get(curChild);
        int aR = m.anchorRowOf(c);
        int aC = m.anchorColOf(c);
        int targetR = aR - 1;
        if (targetR < 0) return -1;
        TL_iv.pageTableCell tCell = m.grid[targetR][Math.min(aC, m.colCount - 1)];
        return m.flatIndexOfAnchor(tCell);
    }

    private static int wordRight(CharSequence text, int offset) {
        BreakIterator bi = BreakIterator.getWordInstance();
        bi.setText(text.toString());
        int next = bi.following(Math.min(offset, text.length()));
        return next == BreakIterator.DONE ? text.length() : next;
    }

    private static int wordLeft(CharSequence text, int offset) {
        BreakIterator bi = BreakIterator.getWordInstance();
        bi.setText(text.toString());
        int prev = bi.preceding(Math.max(0, Math.min(offset, text.length())));
        return prev == BreakIterator.DONE ? 0 : prev;
    }

    private boolean tryEscalateSelectAll() {
        if (rows.isEmpty()) return false;
        for (int i = 0; i < rows.size(); i++) {
            textSelectionHelper.cacheText(i, RichTextCell.readPlainText(rows.get(i).block), null);
        }
        textSelectionHelper.selectAllBlocksRange(0, rows.size() - 1);
        Log.d(TAG, "selectAllBlocksRange [0.." + (rows.size() - 1) + "] done");
        return true;
    }

    private void copyHelperSelection() {
        CharSequence text = textSelectionHelper.getSelectedTextPublic();
        if (text == null || text.length() == 0) return;
        AndroidUtilities.addToClipboard(text);
    }

    private void cutHelperSelection() {
        CharSequence text = textSelectionHelper.getSelectedTextPublic();
        if (text != null && text.length() > 0) {
            AndroidUtilities.addToClipboard(text);
        }
        deleteHelperSelection();
    }

    private void pasteAtHelperSelection() {
        ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return;
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence pasted = clip.getItemAt(0).coerceToText(getContext());
        if (pasted == null) pasted = "";

        int sCell = textSelectionHelper.getStartCell();
        int sOff = textSelectionHelper.getStartOffset();
        int eCell = textSelectionHelper.getEndCell();
        int eOff = textSelectionHelper.getEndOffset();

        String[] lines = pasted.toString().split("\n", -1);
        applyEditRange(sCell, sOff, eCell, eOff, lines);
    }

    private void deleteHelperSelection() {
        int sCell = textSelectionHelper.getStartCell();
        int sOff = textSelectionHelper.getStartOffset();
        int eCell = textSelectionHelper.getEndCell();
        int eOff = textSelectionHelper.getEndOffset();
        applyEditRange(sCell, sOff, eCell, eOff, new String[] { "" });
    }

    private void replaceHelperSelectionWith(String s) {
        int sCell = textSelectionHelper.getStartCell();
        int sOff = textSelectionHelper.getStartOffset();
        int eCell = textSelectionHelper.getEndCell();
        int eOff = textSelectionHelper.getEndOffset();
        String[] lines = s.split("\n", -1);
        applyEditRange(sCell, sOff, eCell, eOff, lines);
    }

    private void applyEditRange(int sCell, int sOff, int eCell, int eOff, String[] insertedLines) {
        if (sCell < 0 || eCell < 0 || sCell >= rows.size() || eCell >= rows.size()) return;

        BlockRow startRow = rows.get(sCell);
        BlockRow endRow = rows.get(eCell);

        boolean startInTable = startRow.block instanceof TL_iv.pageBlockTable;
        boolean endInTable = endRow.block instanceof TL_iv.pageBlockTable;
        if (startInTable || endInTable) {
            if (startInTable && endInTable && sCell == eCell) {
                int sChild = textSelectionHelper.getStartChildPosition();
                int eChild = textSelectionHelper.getEndChildPosition();
                applyEditInsideTable(startRow, sChild, sOff, eChild, eOff, insertedLines);
            }
            return;
        }

        String startText = RichTextCell.readPlainText(startRow.block);
        String endText = sCell == eCell ? startText : RichTextCell.readPlainText(endRow.block);
        sOff = Math.max(0, Math.min(sOff, startText.length()));
        eOff = Math.max(0, Math.min(eOff, endText.length()));

        String head = startText.substring(0, sOff);
        String tail = endText.substring(eOff);

        int finalCell;
        int finalOffset;

        if (insertedLines.length <= 1) {
            String inserted = insertedLines.length == 0 ? "" : insertedLines[0];
            RichTextCell.applyTextToBlock(startRow.block, head + inserted + tail);
            if (eCell > sCell) {
                for (int i = eCell; i > sCell; i--) rows.remove(i);
            }
            finalCell = sCell;
            finalOffset = head.length() + inserted.length();
        } else {
            RichTextCell.applyTextToBlock(startRow.block, head + insertedLines[0]);
            if (eCell > sCell) {
                for (int i = eCell; i > sCell; i--) rows.remove(i);
            }
            for (int i = 1; i < insertedLines.length - 1; i++) {
                TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
                applyPlainText(p, insertedLines[i]);
                rows.add(sCell + i, new BlockRow(p, startRow.level, startRow.num > 0 ? startRow.num + i : startRow.num));
            }
            String lastLine = insertedLines[insertedLines.length - 1];
            TL_iv.pageBlockParagraph tailBlock = new TL_iv.pageBlockParagraph();
            applyPlainText(tailBlock, lastLine + tail);
            rows.add(sCell + insertedLines.length - 1, new BlockRow(tailBlock, startRow.level, startRow.num > 0 ? startRow.num + insertedLines.length - 1 : startRow.num));
            finalCell = sCell + insertedLines.length - 1;
            finalOffset = lastLine.length();
        }
        renumberAllRuns();

        textSelectionHelper.clear();
        listView.adapter.update(true);
        updateSendButton(true);

        final int focusCell = finalCell;
        final int focusOffset = finalOffset;
        listView.post(() -> {
            RichTextCell cell = cellAt(focusCell);
            if (cell != null) {
                cell.requestEditFocus();
                int len = cell.getEditText().length();
                cell.getEditText().setSelection(Math.min(focusOffset, len));
            }
        });
    }

    private static void applyPlainText(TL_iv.PageBlock block, String text) {
        TL_iv.textPlain plain = new TL_iv.textPlain();
        plain.text = text;
        if (block instanceof TL_iv.pageBlockParagraph) {
            ((TL_iv.pageBlockParagraph) block).text = plain;
        }
    }

    private void applyEditInsideTable(BlockRow row, int sChild, int sOff, int eChild, int eOff, String[] insertedLines) {
        View v = listView.findViewByItemObject(row);
        if (!(v instanceof RichTableCell)) return;
        RichTableCell rtc = (RichTableCell) v;
        TableModel model = rtc.getModel();
        if (model == null) return;

        int childCount = model.anchors().size();
        if (sChild < 0 || sChild >= childCount || eChild < 0 || eChild >= childCount) return;

        if (sChild > eChild || (sChild == eChild && sOff > eOff)) {
            int tc = sChild; sChild = eChild; eChild = tc;
            int to = sOff; sOff = eOff; eOff = to;
        }

        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < insertedLines.length; i++) {
            if (i > 0) joined.append('\n');
            joined.append(insertedLines[i]);
        }
        String insert = joined.toString();

        TL_iv.pageTableCell startCell = model.anchors().get(sChild);
        TL_iv.pageTableCell endCell = model.anchors().get(eChild);

        TL_iv.pageTableCell focusCell;
        int focusOffset;

        if (sChild == eChild) {
            String text = TableModel.readPlainText(startCell);
            sOff = Math.max(0, Math.min(sOff, text.length()));
            eOff = Math.max(0, Math.min(eOff, text.length()));
            String newText = text.substring(0, sOff) + insert + text.substring(eOff);
            TableModel.applyPlainText(startCell, newText);
            RichTableCellHost host = rtc.getGrid().hostForAnchor(startCell);
            if (host != null) host.editText.setTextSilently(newText);
            focusCell = startCell;
            focusOffset = sOff + insert.length();
        } else {
            String startText = TableModel.readPlainText(startCell);
            sOff = Math.max(0, Math.min(sOff, startText.length()));
            String newStart = startText.substring(0, sOff) + insert;
            TableModel.applyPlainText(startCell, newStart);
            RichTableCellHost startHost = rtc.getGrid().hostForAnchor(startCell);
            if (startHost != null) startHost.editText.setTextSilently(newStart);

            for (int i = sChild + 1; i < eChild; i++) {
                TL_iv.pageTableCell c = model.anchors().get(i);
                TableModel.applyPlainText(c, "");
                RichTableCellHost h = rtc.getGrid().hostForAnchor(c);
                if (h != null) h.editText.setTextSilently("");
            }

            String endText = TableModel.readPlainText(endCell);
            eOff = Math.max(0, Math.min(eOff, endText.length()));
            String newEnd = endText.substring(eOff);
            TableModel.applyPlainText(endCell, newEnd);
            RichTableCellHost endHost = rtc.getGrid().hostForAnchor(endCell);
            if (endHost != null) endHost.editText.setTextSilently(newEnd);

            focusCell = startCell;
            focusOffset = sOff + insert.length();
        }

        textSelectionHelper.clear();
        updateSendButton(true);

        final TL_iv.pageTableCell finalFocusCell = focusCell;
        final int finalFocusOffset = focusOffset;
        listView.post(() -> {
            RichTableCellHost host = rtc.getGrid().hostForAnchor(finalFocusCell);
            if (host == null) return;
            host.editText.requestEditFocus();
            int len = host.editText.length();
            host.editText.setSelection(Math.max(0, Math.min(finalFocusOffset, len)));
        });
    }

    private void setEditTextsLocked(boolean locked) {
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof RichTextCell) {
                ((RichTextCell) child).setLocked(locked);
            } else if (child instanceof RichTableCell) {
                ((RichTableCell) child).setLocked(locked);
            } else if (child instanceof RichDividerCell) {
                child.invalidate();
            }
        }
    }

    private View findCellUnder(int x, int y) {
        int listY = y - listView.getTop();
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (listY >= child.getTop() && listY < child.getBottom() && x >= child.getLeft() && x < child.getRight()) {
                return child;
            }
        }
        return null;
    }

    @Override
    public void onShow(ChatAttachAlert.AttachAlertLayout previousLayout) {
        try {
            parentAlert.actionBar.getTitleTextView().setBuildFullLayout(true);
        } catch (Exception ignore) {}
        parentAlert.actionBar.setTitle("Article");
        listView.adapter.update(false);
    }

    @Override
    public void onHide() {
        if (sendButtonShown) {
            sendButtonShown = false;
            parentAlert.showSendButtonOnly(false, true);
        }
    }

    @Override
    public boolean sendSelectedItems(boolean notify, int scheduleDate, int scheduleRepeatPeriod, long effectId, boolean invertMedia) {
        if (!hasAnyText()) {
            return false;
        }
        if (hasPendingUploads()) {
            return false;
        }
        ArrayList<TL_iv.PageBlock> sendBlocks = flattenRowsToBlocks();
        if (sendBlocks.isEmpty()) {
            return false;
        }
        ArrayList<TLRPC.InputPhoto> sendPhotos = collectInputPhotos();
        ArrayList<TLRPC.InputDocument> sendDocs = collectInputDocuments();
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

    private ArrayList<TLRPC.InputPhoto> collectInputPhotos() {
        ArrayList<TLRPC.InputPhoto> out = new ArrayList<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            BlockRow r = rows.get(i);
            if (r.block instanceof TL_iv.pageBlockPhoto && r.media != null && r.media.isReady() && r.media.photo != null) {
                TLRPC.Photo p = r.media.photo;
                if (!seen.add(p.id)) continue;
                TLRPC.TL_inputPhoto ip = new TLRPC.TL_inputPhoto();
                ip.id = p.id;
                ip.access_hash = p.access_hash;
                ip.file_reference = p.file_reference != null ? p.file_reference : new byte[0];
                out.add(ip);
            }
        }
        return out;
    }

    private ArrayList<TLRPC.InputDocument> collectInputDocuments() {
        ArrayList<TLRPC.InputDocument> out = new ArrayList<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            BlockRow r = rows.get(i);
            if (r.block instanceof TL_iv.pageBlockVideo && r.media != null && r.media.isReady() && r.media.document != null) {
                TLRPC.Document d = r.media.document;
                if (!seen.add(d.id)) continue;
                TLRPC.TL_inputDocument id = new TLRPC.TL_inputDocument();
                id.id = d.id;
                id.access_hash = d.access_hash;
                id.file_reference = d.file_reference != null ? d.file_reference : new byte[0];
                out.add(id);
            }
        }
        return out;
    }

    private ArrayList<TL_iv.PageBlock> flattenRowsToBlocks() {
        Log.d(TAG, "=== flattenRowsToBlocks rows.size=" + rows.size());
        for (int k = 0; k < rows.size(); k++) {
            BlockRow rr = rows.get(k);
            Log.d(TAG, "  row[" + k + "] level=" + rr.level + " num=" + rr.num + " block=" + rr.block.getClass().getSimpleName() + " text='" + RichTextCell.readPlainText(rr.block) + "'");
        }
        ArrayList<TL_iv.PageBlock> out = new ArrayList<>();
        int i = 0;
        while (i < rows.size()) {
            BlockRow r = rows.get(i);
            if (r.level <= 0) {
                if (r.block instanceof TL_iv.pageBlockDivider) {
                    out.add(r.block);
                } else if (r.block instanceof TL_iv.pageBlockPhoto) {
                    if (r.media != null && r.media.isReady() && ((TL_iv.pageBlockPhoto) r.block).photo_id != 0) {
                        if (((TL_iv.pageBlockPhoto) r.block).caption == null) {
                            ((TL_iv.pageBlockPhoto) r.block).caption = new TL_iv.PageCaption();
                            ((TL_iv.pageBlockPhoto) r.block).caption.text = new TL_iv.textEmpty();
                            ((TL_iv.pageBlockPhoto) r.block).caption.credit = new TL_iv.textEmpty();
                        }
                        out.add(r.block);
                    }
                } else if (r.block instanceof TL_iv.pageBlockVideo) {
                    if (r.media != null && r.media.isReady() && ((TL_iv.pageBlockVideo) r.block).video_id != 0) {
                        if (((TL_iv.pageBlockVideo) r.block).caption == null) {
                            ((TL_iv.pageBlockVideo) r.block).caption = new TL_iv.PageCaption();
                            ((TL_iv.pageBlockVideo) r.block).caption.text = new TL_iv.textEmpty();
                            ((TL_iv.pageBlockVideo) r.block).caption.credit = new TL_iv.textEmpty();
                        }
                        out.add(r.block);
                    }
                } else if (r.block instanceof TL_iv.pageBlockMap) {
                    TL_iv.pageBlockMap map = (TL_iv.pageBlockMap) r.block;
                    if (map.geo != null) {
                        if (map.caption == null) {
                            map.caption = new TL_iv.PageCaption();
                            map.caption.text = new TL_iv.textEmpty();
                            map.caption.credit = new TL_iv.textEmpty();
                        }
                        out.add(map);
                    }
                } else if (r.block instanceof TL_iv.pageBlockMath) {
                    if (!android.text.TextUtils.isEmpty(((TL_iv.pageBlockMath) r.block).source)) {
                        out.add(r.block);
                    }
                } else if (r.block instanceof TL_iv.pageBlockTable) {
                    TL_iv.pageBlockTable t = (TL_iv.pageBlockTable) r.block;
                    TableModel.normalizeForSend(t);
                    if (tableHasText(t)) out.add(t);
                } else if (!RichTextCell.readPlainText(r.block).isEmpty()) {
                    out.add(r.block);
                }
                i++;
            } else {
                int[] end = new int[] { i };
                TL_iv.PageBlock list = buildListBlock(i, r.level, r.num > 0, end);
                if (list != null) out.add(list);
                i = end[0];
                if (i <= 0) i = rows.size();
            }
        }
        return out;
    }

    private TL_iv.PageBlock buildListBlock(int from, int targetLevel, boolean ordered, int[] endIdx) {
        Log.d(TAG, "buildListBlock enter from=" + from + " targetLevel=" + targetLevel + " ordered=" + ordered);
        TL_iv.pageBlockOrderedList orderedList = ordered ? new TL_iv.pageBlockOrderedList() : null;
        TL_iv.pageBlockList unorderedList = ordered ? null : new TL_iv.pageBlockList();
        int counter = 1;
        int i = from;
        while (i < rows.size()) {
            BlockRow r = rows.get(i);
            Log.d(TAG, "  buildListBlock target=" + targetLevel + " i=" + i + " r.level=" + r.level + " r.num=" + r.num);
            if (r.level < targetLevel) { Log.d(TAG, "  break: level<target"); break; }
            if (r.level == targetLevel && (r.num > 0) != ordered) { Log.d(TAG, "  break: ordered mismatch"); break; }
            if (r.level > targetLevel) { Log.d(TAG, "  break: level>target (defensive)"); break; }

            String text = RichTextCell.readPlainText(r.block);
            int j = i + 1;
            ArrayList<TL_iv.PageBlock> childLists = new ArrayList<>();
            while (j < rows.size() && rows.get(j).level > targetLevel) {
                int subLevel = rows.get(j).level;
                boolean subOrdered = rows.get(j).num > 0;
                int[] subEnd = new int[] { j };
                Log.d(TAG, "  -> recurse subLevel=" + subLevel + " subOrdered=" + subOrdered + " from j=" + j);
                TL_iv.PageBlock sub = buildListBlock(j, subLevel, subOrdered, subEnd);
                Log.d(TAG, "  <- recurse returned subEnd=" + subEnd[0] + " sub=" + (sub != null ? sub.getClass().getSimpleName() : "null"));
                if (sub != null) childLists.add(sub);
                if (subEnd[0] <= j) { Log.d(TAG, "  child walk break: no progress"); break; }
                j = subEnd[0];
            }
            Log.d(TAG, "  item built: text='" + text + "' childLists.size=" + childLists.size() + " counter=" + counter);

            if (childLists.isEmpty()) {
                TL_iv.textPlain plain = new TL_iv.textPlain();
                plain.text = text;
                if (ordered) {
                    TL_iv.TL_pageListOrderedItemText item = new TL_iv.TL_pageListOrderedItemText();
                    item.num = counter + ".";
                    item.text = plain;
                    orderedList.items.add(item);
                } else {
                    TL_iv.TL_pageListItemText item = new TL_iv.TL_pageListItemText();
                    item.text = plain;
                    unorderedList.items.add(item);
                }
            } else {
                TL_iv.pageBlockParagraph para = new TL_iv.pageBlockParagraph();
                TL_iv.textPlain plain = new TL_iv.textPlain();
                plain.text = text;
                para.text = plain;
                ArrayList<TL_iv.PageBlock> blocks = new ArrayList<>();
                blocks.add(para);
                blocks.addAll(childLists);
                if (ordered) {
                    TL_iv.TL_pageListOrderedItemBlocks item = new TL_iv.TL_pageListOrderedItemBlocks();
                    item.num = counter + ".";
                    item.blocks = blocks;
                    orderedList.items.add(item);
                } else {
                    TL_iv.TL_pageListItemBlocks item = new TL_iv.TL_pageListItemBlocks();
                    item.blocks = blocks;
                    unorderedList.items.add(item);
                }
            }
            counter++;
            i = j;
        }
        endIdx[0] = i;
        if (ordered) {
            return orderedList.items.isEmpty() ? null : orderedList;
        } else {
            return unorderedList.items.isEmpty() ? null : unorderedList;
        }
    }

    private final java.util.IdentityHashMap<BlockRow, RichMediaUploader> uploaders = new java.util.IdentityHashMap<>();

    private void invalidateMediaCellForRow(BlockRow row) {
        View v = listView.findViewByItemObject(row);
        if (v != null) v.invalidate();
    }

    private void openMathEditor(BlockRow row) {
        if (row == null || !(row.block instanceof TL_iv.pageBlockMath)) return;
        final TL_iv.pageBlockMath math = (TL_iv.pageBlockMath) row.block;
        showEditLatexSheet(getContext(), math.source == null ? "" : math.source, source -> {
            math.source = source;
            View v = listView.findViewByItemObject(row);
            if (v instanceof RichMathCell) {
                ((RichMathCell) v).rebuild();
            } else {
                listView.adapter.update(false);
            }
            updateSendButton(true);
        }, resourcesProvider);
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
            final TL_iv.pageBlockMap map = (TL_iv.pageBlockMap) row.block;
            map.geo = location.geo;
            map.zoom = 15;
            if (map.w <= 0 || map.h <= 0) {
                map.w = 600;
                map.h = 400;
            }
            updateSendButton(true);
            pickerAlert.dismiss(true);
            listView.post(() -> {
                View v = listView.findViewByItemObject(row);
                if (v instanceof RichMapCell) {
                    ((RichMapCell) v).bind(row, mapDelegate);
                } else {
                    listView.adapter.update(false);
                }
            });
        });
        pickerAlert.init();
        pickerAlert.show();
    }

    private void openMediaPicker(BlockRow row) {
        if (parentAlert.baseFragment == null) return;
        final ChatAttachAlert pickerAlert = new ChatAttachAlert(getContext(), parentAlert.baseFragment, false, false, false, null);
        pickerAlert.setMaxSelectedPhotos(1, false);
        pickerAlert.setStoryMediaPicker();
        pickerAlert.getPhotoLayout().loadGalleryPhotos();
        pickerAlert.setDelegate(new ChatAttachAlert.ChatAttachViewDelegate() {
            @Override
            public void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long effectId, boolean invertMedia, boolean forceDocument, long payStars) {
                java.util.HashMap<Object, Object> selectedPhotos = pickerAlert.getPhotoLayout().getSelectedPhotos();
                ArrayList<Object> selectedPhotosOrder = pickerAlert.getPhotoLayout().getSelectedPhotosOrder();
                if (!selectedPhotos.isEmpty() && !selectedPhotosOrder.isEmpty()) {
                    Object key = selectedPhotosOrder.get(0);
                    Object entryObj = selectedPhotos.get(key);
                    if (entryObj instanceof org.Tajgram.messenger.MediaController.PhotoEntry) {
                        org.Tajgram.messenger.MediaController.PhotoEntry photoEntry = (org.Tajgram.messenger.MediaController.PhotoEntry) entryObj;
                        Log.d(TAG, "picker.didPressed isVideo=" + photoEntry.isVideo
                            + " path=" + photoEntry.path
                            + " imagePath=" + photoEntry.imagePath
                            + " thumbPath=" + photoEntry.thumbPath
                            + " w=" + photoEntry.width + " h=" + photoEntry.height + " dur=" + photoEntry.duration
                            + " editedInfo=" + (photoEntry.editedInfo != null));
                        String path;
                        if (photoEntry.isVideo) {
                            path = photoEntry.path;
                        } else {
                            path = photoEntry.imagePath != null ? photoEntry.imagePath : photoEntry.path;
                        }
                        if (path != null) {
                            Log.d(TAG, "  startMediaUpload uploadPath=" + path + " imageId=" + photoEntry.imageId);
                            if (photoEntry.isVideo) {
                                startMediaUpload(row, path, photoEntry.thumbPath, photoEntry.imageId, true, photoEntry.width, photoEntry.height, photoEntry.duration);
                            } else {
                                startMediaUpload(row, path, photoEntry.thumbPath, photoEntry.imageId, false, 0, 0, 0);
                            }
                        }
                    }
                }
                pickerAlert.dismiss(true);
            }
        });
        pickerAlert.init();
        pickerAlert.show();
    }

    private void startMediaUpload(BlockRow row, String path, String thumbPath, int imageId, boolean isVideo, int w, int h, int durationSec) {
        RichMediaUploader existing = uploaders.remove(row);
        if (existing != null) existing.cancel();

        if (row.media == null) row.media = new MediaUploadState();
        row.media.state = MediaUploadState.STATE_UPLOADING;
        row.media.isVideo = isVideo;
        row.media.localPath = path;
        row.media.thumbPath = thumbPath;
        row.media.imageId = imageId;
        if (isVideo) {
            row.media.localThumbBitmap = extractFirstFrame(path);
            Log.d(TAG, "extractFirstFrame bitmap=" + (row.media.localThumbBitmap != null ? (row.media.localThumbBitmap.getWidth() + "x" + row.media.localThumbBitmap.getHeight()) : "null"));
        }
        Log.d(TAG, "startMediaUpload row=" + System.identityHashCode(row) + " isVideo=" + isVideo + " path=" + path + " thumb=" + thumbPath + " imageId=" + imageId + " w=" + w + " h=" + h + " dur=" + durationSec);
        row.media.progress = 0f;
        row.media.photo = null;
        row.media.document = null;
        if (isVideo) {
            row.media.width = w;
            row.media.height = h;
            row.media.duration = durationSec;
        }
        if (isVideo && !(row.block instanceof TL_iv.pageBlockVideo)) {
            row.block = new TL_iv.pageBlockVideo();
        } else if (!isVideo && !(row.block instanceof TL_iv.pageBlockPhoto)) {
            row.block = new TL_iv.pageBlockPhoto();
        }

        RichMediaUploader uploader = new RichMediaUploader(currentAccount, path, isVideo, w, h, durationSec, new RichMediaUploader.Listener() {
            @Override
            public void onWidthHeightResolved(int wRes, int hRes) {
                row.media.width = wRes;
                row.media.height = hRes;
                invalidateMediaCellForRow(row);
                listView.adapter.update(false);
            }
            @Override
            public void onProgress(float progress) {
                row.media.progress = progress;
                invalidateMediaCellForRow(row);
            }
            @Override
            public void onPhotoUploaded(TLRPC.Photo photo) {
                row.media.photo = photo;
                row.media.state = MediaUploadState.STATE_DONE;
                if (row.block instanceof TL_iv.pageBlockPhoto) {
                    ((TL_iv.pageBlockPhoto) row.block).photo_id = photo.id;
                }
                uploaders.remove(row);
                invalidateMediaCellForRow(row);
                updateSendButton(true);
            }
            @Override
            public void onVideoUploaded(TLRPC.Document doc) {
                row.media.document = doc;
                row.media.state = MediaUploadState.STATE_DONE;
                if (row.block instanceof TL_iv.pageBlockVideo) {
                    ((TL_iv.pageBlockVideo) row.block).video_id = doc.id;
                }
                uploaders.remove(row);
                invalidateMediaCellForRow(row);
                updateSendButton(true);
            }
            @Override
            public void onError() {
                row.media.state = MediaUploadState.STATE_ERROR;
                uploaders.remove(row);
                invalidateMediaCellForRow(row);
                updateSendButton(true);
            }
        });
        uploaders.put(row, uploader);
        uploader.start();
        invalidateMediaCellForRow(row);
        listView.adapter.update(false);
        updateSendButton(true);
    }

    @Override
    public void onHidden() {

    }

    private static android.graphics.Bitmap extractFirstFrame(String path) {
        android.media.MediaMetadataRetriever r = null;
        try {
            r = new android.media.MediaMetadataRetriever();
            r.setDataSource(path);
            return r.getFrameAtTime(0L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } catch (Throwable t) {
            Log.e(TAG, "extractFirstFrame failed for " + path, t);
            return null;
        } finally {
            try { if (r != null) r.release(); } catch (Throwable ignore) {}
        }
    }

    @Override
    public void onDestroy() {
        for (RichMediaUploader u : uploaders.values()) {
            u.cancel();
        }
        uploaders.clear();
    }

    public static void showEditLatexSheet(Context context, String initialSource, Utilities.Callback<String> whenDone, Theme.ResourcesProvider resourcesProvider) {
        final BottomSheet.Builder b = new BottomSheet.Builder(context, true, resourcesProvider);

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        final String[] source = new String[] { initialSource };
        final ImageView previewView = new ImageView(context);
        previewView.setPadding(dp(4), dp(4), dp(4), dp(4));
        previewView.setBackground(Theme.createRoundRectDrawable(8, Theme.getColor(Theme.key_dialogBackgroundGray, resourcesProvider)));
        layout.addView(previewView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 2, 0, 8));

        final boolean[] error = new boolean[] { false };
        final int[] shiftDp = new int[] { 6 };
        final Runnable update = () -> {
            final boolean wasError = error[0];
            error[0] = false;
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
                    previewView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
                    previewView.setImageBitmap(bm);
                } else {
                    error[0] = true;
                }
            } catch (Exception e) {
                FileLog.e(e);
                error[0] = true;
            }
            if (error[0]) {
                previewView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_text_RedBold, resourcesProvider), PorterDuff.Mode.SRC_IN));
                if (!wasError) {
                    AndroidUtilities.shakeViewSpring(previewView, shiftDp[0] = -shiftDp[0]);
                }
                try {
                    final JLatexMathDrawable drawable =
                        JLatexMathDrawable.builder("Error")
                            .textSize(dp(26))
                            .build();
                    final int w = drawable.getIntrinsicWidth();
                    final int h = drawable.getIntrinsicHeight();
                    if (w > 0 && h > 0) {
                        final Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
                        drawable.setBounds(0, 0, w, h);
                        drawable.draw(new Canvas(bm));
                        previewView.setImageBitmap(bm);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        };
        final Runnable scheduleUpdate = () -> {
            AndroidUtilities.cancelRunOnUIThread(update);
            AndroidUtilities.runOnUIThread(update, 1000);
        };

        final EditTextCell editCell = new EditTextCell(context, "LaTeX Equation", true, false, -1, resourcesProvider);
        editCell.editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editCell.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
        editCell.setText(source[0]);
        editCell.editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                source[0] = s.toString();
//                scheduleUpdate.run();
                update.run();
            }
        });
        layout.addView(editCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 12, 0, 12, 0));

        final ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider).setRound();
        button.setText(getString(R.string.Done));
        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.FILL_HORIZONTAL, 12, 8, 12, 12));

        update.run();

        b.setCustomView(layout);
        final BottomSheet sheet = b.show();
        button.setOnClickListener(v -> {
            whenDone.run(source[0]);
            sheet.dismiss();
        });
    }

}
