package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.CodeHighlighting;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.FloatingToolbar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.CheckBoxBase;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ReplyMessageLine;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class RichTextCell extends FrameLayout implements Theme.Colorable, TextSelectionHelper.ArticleSelectableView {

    public interface Delegate {
        void onEnter(BlockRow row);
        void onBackspace(BlockRow row);
        default void onQuoteAuthorEnter(BlockRow row) {}
        default boolean onBackspaceAtStart(BlockRow row) { return false; }
        void onTextChanged(BlockRow row);
        default void onTextWillChange(BlockRow row, int removed, int added) {}
        void onTransform(BlockRow row, TL_iv.PageBlock newBlock, int newLevel, int newNum, boolean checkbox, boolean checked);
        default void onCheckboxToggle(BlockRow row, boolean checked) {}
        default void onSpansChanged(BlockRow row) {}
        TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper();
        default boolean onIndent(BlockRow row, boolean outdent) { return false; }
        default void onRequestWindowFocusable(RichEditText editText, boolean showKeyboard) {}
        default void onLockedInsert(CharSequence text) {}
        default void onLanguageClick(BlockRow row, View button) {}
        default boolean onSelectAll(BlockRow row) { return false; }
        default boolean onPaste(BlockRow row, RichEditText editText) { return false; }
        default void onCommand(BlockRow row, int command) {}
        default void onSlashSuggest(RichTextCell cell, String query) {}
    }

    public static final int CMD_NONE = 0;
    public static final int CMD_ATTACH_MUSIC = 1;
    public static final int CMD_ATTACH_LOCATION = 2;
    public static final int CMD_MATH = 3;
    public static final int CMD_ATTACH_PHOTO = 4;
    public static final int CMD_ATTACH_VIDEO = 5;
    public static final int CMD_DETAILS = 6;

    private static final int INDENT_DP_PER_LEVEL = 24;
    private static final int BULLET_WIDTH_DP = 28;
    /** Extra top padding a code (preformatted) cell reserves above its content for the language button. */
    private static final int CODE_TOP_LANG_ROOM_DP = 24;

    private final Theme.ResourcesProvider resourcesProvider;
    private final LinearLayout row;
    private final View indentSpacer;
    private final TextView bullet;
    private final CheckBoxView checkBoxView;
    private final RichEditText editText;
    private final RichEditText authorEditText;
    private boolean hijackingAuthorSelection;
    private final ArrayList<TextSelectionHelper.TextLayoutBlock> tmpBlocks = new ArrayList<>();

    private LinearLayout languageButton;
    private TextView languageButtonText;
    private ImageView languageButtonIcon;

    private BlockRow currentRow;
    private Delegate delegate;
    private boolean forceHint;
    private boolean hijackingSelection;

    private static final long HIGHLIGHT_DEBOUNCE = 100;
    private Runnable highlightScheduled;
    private String highlightedSnapshot;
    private int highlightGeneration;

    private ReplyMessageLine quoteLine;
    private Drawable quoteIcon;

    public RichTextCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setPadding(dp(16), dp(6), dp(16), 0);
        setClipToPadding(false);

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        indentSpacer = new View(context);
        row.addView(indentSpacer, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT));

        bullet = new TextView(context);
        bullet.setGravity(Gravity.CENTER);
        bullet.setIncludeFontPadding(false);
        bullet.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        row.addView(bullet, LayoutHelper.createLinear(BULLET_WIDTH_DP, LinearLayout.LayoutParams.WRAP_CONTENT));

        checkBoxView = new CheckBoxView(context, resourcesProvider);
        checkBoxView.setVisibility(View.GONE);
        checkBoxView.setOnClickListener(v -> {
            if (currentRow == null || !currentRow.checkbox) return;
            currentRow.checked = !currentRow.checked;
            checkBoxView.setChecked(currentRow.checked, true);
            if (delegate != null) delegate.onCheckboxToggle(currentRow, currentRow.checked);
        });
        row.addView(checkBoxView, LayoutHelper.createLinear(BULLET_WIDTH_DP, LinearLayout.LayoutParams.WRAP_CONTENT));

        editText = new RichEditText(context, resourcesProvider);
        editText.setPadding(dp(2), 0, dp(2), 0);
        editText.setListener(new RichEditText.Listener() {
            @Override
            public void onEnterPressed(RichEditText et) {
                if (delegate == null || currentRow == null) return;
                final String text = et.getText().toString();
                delegate.onSlashSuggest(RichTextCell.this, null);
                final String sq = slashQuery(text);
                if (sq != null) {
                    final ArrayList<RichCommand> matched = RichCommand.match(sq);
                    if (!matched.isEmpty()) {
                        selectCommand(matched.get(0));
                        return;
                    }
                }
                int cmd = matchCommand(et.getText().toString());
                if (cmd != CMD_NONE) {
                    delegate.onCommand(currentRow, cmd);
                    return;
                }
                Transform tr = matchEnterTrigger(et.getText().toString(), currentRow);
                if (tr != null) {
                    delegate.onTransform(currentRow, tr.block, tr.level, tr.num, tr.checkbox, tr.checked);
                    return;
                }
                if (currentRow.block instanceof TL_iv.pageBlockPullquote && editText.length() > 0) {
                    ensureAuthorVisibleAndFocus();
                    return;
                }
                delegate.onEnter(currentRow);
            }

            @Override
            public void onBackspaceOnEmpty(RichEditText et) {
                if (delegate != null && currentRow != null) delegate.onBackspace(currentRow);
            }

            @Override
            public boolean onBackspaceAtStart(RichEditText et) {
                if (delegate != null && currentRow != null) return delegate.onBackspaceAtStart(currentRow);
                return false;
            }

            @Override
            public void onTextWillChange(RichEditText et, int removed, int added) {
                if (delegate != null && currentRow != null) delegate.onTextWillChange(currentRow, removed, added);
            }

            @Override
            public void onTextChanged(RichEditText et, Editable text) {
                if (currentRow == null) return;
                applyStyledTextToBlock(currentRow.block, text);
                scheduleHighlight();
                updateAuthorVisibility();
                if (delegate != null) delegate.onTextChanged(currentRow);
                if (delegate != null) delegate.onSlashSuggest(RichTextCell.this, slashQuery(text.toString()));
                int mdCmd = matchMarkdownCommand(text.toString(), currentRow);
                if (mdCmd != CMD_NONE && delegate != null) {
                    final BlockRow r = currentRow;
                    final int finalCmd = mdCmd;
                    post(() -> {
                        if (delegate == null) return;
                        applyTextToBlock(r.block, "");
                        delegate.onCommand(r, finalCmd);
                    });
                } else {
                    Transform tr = matchMarkdownTrigger(text.toString(), currentRow);
                    if (tr != null && delegate != null) {
                        final BlockRow r = currentRow;
                        final Transform finalTr = tr;
                        post(() -> {
                            if (delegate != null) delegate.onTransform(r, finalTr.block, finalTr.level, finalTr.num, finalTr.checkbox, finalTr.checked);
                        });
                    }
                }
                if (showCommandBackground || currentRow != null && currentRow.block instanceof TL_iv.pageBlockPullquote)
                    RichTextCell.this.invalidate();
            }

            @Override
            public boolean onTab(RichEditText et, boolean shift) {
//                if (currentRow != null && currentRow.block instanceof TL_iv.pageBlockPreformatted) {
//                    if (shift) return false;
//                    final Editable text = et.getText();
//                    int start = Math.max(0, Math.min(et.getSelectionStart(), et.getSelectionEnd()));
//                    int end = Math.max(et.getSelectionStart(), et.getSelectionEnd());
//                    if (start < 0 || end < 0) return false;
//                    text.replace(start, end, "\t");
//                    et.setSelection(start + 1);
//                    return true;
//                }
                if (delegate != null && currentRow != null) return delegate.onIndent(currentRow, shift);
                return false;
            }

            @Override
            public void onRequestWindowFocusable(RichEditText et, boolean showKeyboard) {
                if (delegate != null) delegate.onRequestWindowFocusable(et, showKeyboard);
            }

            @Override
            public void onLockedInsert(RichEditText et, CharSequence text) {
                if (delegate != null) delegate.onLockedInsert(text);
            }

            @Override
            public boolean onSelectAll(RichEditText et) {
                if (delegate != null && currentRow != null) return delegate.onSelectAll(currentRow);
                return false;
            }

            @Override
            public boolean onPaste(RichEditText et) {
                if (delegate != null && currentRow != null) return delegate.onPaste(currentRow, et);
                return false;
            }

            @Override
            public void onSelectionChanged(RichEditText et, int selStart, int selEnd) {
                if (hijackingSelection || selStart == selEnd || delegate == null) return;
                final TextSelectionHelper.ArticleTextSelectionHelper helper = delegate.getSelectionHelper();
                if (helper == null) return;
                final int s = selStart, e = selEnd;
                post(() -> {
                    if (et.length() < e || et.getSelectionStart() == et.getSelectionEnd()) return;
                    if (helper.isInSelectionMode()) {
                        hijackingSelection = true;
                        et.setSelection(e);
                        hijackingSelection = false;
                        return;
                    }
                    if (helper.selectRangeOf(RichTextCell.this, s, e)) {
                        hijackingSelection = true;
                        et.setSelection(e);
                        hijackingSelection = false;
                    }
                });
            }
        });
        editText.setDelegate(() -> {
            if (currentRow == null) return;
            applyStyledTextToBlock(currentRow.block, editText.getText());
            if (delegate != null) delegate.onSpansChanged(currentRow);
        });
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            editText.setHint(getHint());
            if (!hasFocus && delegate != null) delegate.onSlashSuggest(this, null);
        });

        row.addView(editText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        authorEditText = new RichEditText(context, resourcesProvider);
        authorEditText.setPadding(dp(2), dp(1), dp(2), dp(1));
        authorEditText.setAllowNewlines(false);
        authorEditText.setInputType(
            InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_FLAG_MULTI_LINE |
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        authorEditText.setListener(new RichEditText.Listener() {
            @Override
            public void onEnterPressed(RichEditText et) {
                if (delegate != null && currentRow != null) delegate.onQuoteAuthorEnter(currentRow);
            }

            @Override
            public void onBackspaceOnEmpty(RichEditText et) {

            }

            @Override
            public boolean onBackspaceAtStart(RichEditText et) {
                editText.requestEditFocus();
                editText.setSelection(editText.length());
                return true;
            }

            @Override
            public void onTextWillChange(RichEditText et, int removed, int added) {
                if (delegate != null && currentRow != null) delegate.onTextWillChange(currentRow, removed, added);
            }

            @Override
            public void onTextChanged(RichEditText et, Editable text) {
                if (currentRow == null) return;
                persistAuthor();
                if (delegate != null) delegate.onTextChanged(currentRow);
                if (currentRow.block instanceof TL_iv.pageBlockPullquote) RichTextCell.this.invalidate();
            }

            @Override
            public void onRequestWindowFocusable(RichEditText et, boolean showKeyboard) {
                if (delegate != null) delegate.onRequestWindowFocusable(et, showKeyboard);
            }

            @Override
            public void onLockedInsert(RichEditText et, CharSequence text) {
                if (delegate != null) delegate.onLockedInsert(text);
            }

            @Override
            public boolean onSelectAll(RichEditText et) {
                if (delegate != null && currentRow != null) return delegate.onSelectAll(currentRow);
                return false;
            }

            @Override
            public void onSelectionChanged(RichEditText et, int selStart, int selEnd) {
                if (hijackingAuthorSelection || selStart == selEnd || delegate == null) return;
                final TextSelectionHelper.ArticleTextSelectionHelper helper = delegate.getSelectionHelper();
                if (helper == null) return;
                final int s = selStart, e = selEnd;
                et.post(() -> {
                    if (et.length() < e || et.getSelectionStart() == et.getSelectionEnd()) return;
                    if (helper.isInSelectionMode()) {
                        hijackingAuthorSelection = true;
                        et.setSelection(e);
                        hijackingAuthorSelection = false;
                        return;
                    }
                    if (helper.selectRangeOf(RichTextCell.this, 1, s, e)) {
                        hijackingAuthorSelection = true;
                        et.setSelection(e);
                        hijackingAuthorSelection = false;
                    }
                });
            }
        });
        authorEditText.setDelegate(() -> {
            if (currentRow == null) return;
            persistAuthor();
            if (delegate != null) delegate.onSpansChanged(currentRow);
        });
        authorEditText.setVisibility(View.GONE);
        addView(authorEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        updateColors();
    }

    private void updateLanguageButton(TL_iv.PageBlock pageBlock, boolean recreate) {
        if (!(pageBlock instanceof TL_iv.pageBlockPreformatted)) {
            if (languageButton != null)
                languageButton.setVisibility(View.GONE);
            return;
        }
        if (languageButton != null && recreate) {
            AndroidUtilities.removeFromParent(languageButton);
            languageButton = null;
        }
        if (languageButton == null) {
            languageButton = new LinearLayout(getContext());
            languageButton.setOrientation(LinearLayout.HORIZONTAL);
            languageButton.setBackground(Theme.createRadSelectorDrawable(
                Theme.getColor(Theme.key_listSelector, resourcesProvider),
                dp(3), dp(3)
            ));
            languageButton.setPadding(dp(6), dp(2), dp(2), dp(2));
            addView(languageButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, -15, -5, 0));

            languageButtonText = new TextView(getContext());
            languageButtonText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            languageButtonText.setGravity(Gravity.CENTER);
            languageButton.addView(languageButtonText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

            languageButtonIcon = new ImageView(getContext());
            languageButtonIcon.setImageResource(R.drawable.arrows_select);
            languageButton.addView(languageButtonIcon, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 0, 0.66f, 0, 0));

            CodeHighlighting.prepare();
            languageButton.setOnClickListener(v -> {
                if (delegate != null) {
                    delegate.onLanguageClick(currentRow, v);
                }
            });
            languageButton.setOnLongClickListener(v -> {
                return true;
            });
        }

        final String language = ((TL_iv.pageBlockPreformatted) pageBlock).language;

        int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        color = Theme.multAlpha(color, TextUtils.isEmpty(language) ? .50f : .75f);

        languageButtonIcon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        languageButtonText.setTextColor(color);
        if (TextUtils.isEmpty(language)) {
            languageButtonText.setText(getString(R.string.ArticleHintLanguage));
        } else {
            languageButtonText.setText(MessageObject.TextLayoutBlock.capitalizeLanguage(language));
        }
        languageButton.setVisibility(View.VISIBLE);
    }

    public void bind(BlockRow row, Delegate delegate, boolean forceHint) {
        this.currentRow = row;
        this.delegate = delegate;
        this.forceHint = forceHint;
        editText.setBlock(row.block);
        applyStyle(row.block);
        applyQuoteInset(row);
        applyListDecoration(row);
        updateLanguageButton(row.block, false);
        String newText = readPlainText(row.block);
        if (!String.valueOf(editText.getText()).equals(newText)) {
            CharSequence styledText = readStyledText(row.block);
            if (RichEditorListView.isHeading(row.block)) {
                final SpannableString sb = new SpannableString(styledText);
                RichTextStyle.setStyle(sb, 0, sb.length(), RichTextStyle.BOLD, false);
                RichTextStyle.setStyle(sb, 0, sb.length(), RichTextStyle.ITALIC, false);
                styledText = sb;
            }
            CharSequence styled = Emoji.replaceEmoji(styledText, editText.getPaint().getFontMetricsInt(), false);
            editText.setTextSilently(styled);
            editText.invalidateEffects();
            highlightedSnapshot = null;
        }
        bindAuthor(row.block);
        scheduleHighlight();
    }

    public void rebindInPlace() {
        if (currentRow == null || delegate == null) return;
        bind(currentRow, delegate, forceHint);
    }

    private void bindAuthor(TL_iv.PageBlock block) {
        if (!isQuoteBlock(block)) {
            authorEditText.setVisibility(View.GONE);
            return;
        }
        ensureCaption(block);
        applyAuthorStyle(block);
        final String newAuthor = readAuthorPlain(block);
        if (!String.valueOf(authorEditText.getText()).equals(newAuthor)) {
            final CharSequence styled = Emoji.replaceEmoji(readAuthorStyled(block), authorEditText.getPaint().getFontMetricsInt(), false);
            authorEditText.setTextSilently(styled);
            authorEditText.invalidateEffects();
        }
        updateAuthorVisibility();
    }

    private void applyAuthorStyle(TL_iv.PageBlock block) {
        authorEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, Math.max(8, SharedConfig.fontSize - 2));
        authorEditText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        authorEditText.setTextColorKey(Theme.key_featuredStickers_addButton);
        authorEditText.setAccentHint(true);
        authorEditText.setHint(getString(R.string.ArticleHintAuthor));
        if (block instanceof TL_iv.pageBlockPullquote) {
            authorEditText.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        } else {
            authorEditText.setGravity(Gravity.START | Gravity.TOP);
        }
    }

    private void updateAuthorVisibility() {
        final boolean quote = currentRow != null && isQuoteBlock(currentRow.block);
        final boolean show = quote && (editText.length() > 0 || authorEditText.length() > 0);
        if (show) {
            if (authorEditText.getVisibility() != View.VISIBLE) {
                authorEditText.setVisibility(View.VISIBLE);
                requestLayout();
            }
        } else if (authorEditText.getVisibility() != View.GONE) {
            if (authorEditText.isFocused()) {
                editText.requestFocus();
            }
            authorEditText.setVisibility(View.GONE);
            requestLayout();
        }
    }

    private void ensureAuthorVisibleAndFocus() {
        if (currentRow != null) ensureCaption(currentRow.block);
        if (authorEditText.getVisibility() != View.VISIBLE) {
            authorEditText.setVisibility(View.VISIBLE);
            requestLayout();
        }
        authorEditText.requestEditFocus();
        authorEditText.setSelection(authorEditText.length());
    }

    private void removeAuthor() {
        if (currentRow != null) {
            setCaption(currentRow.block, new TL_iv.textEmpty());
        }
        editText.requestEditFocus();
        editText.setSelection(editText.length());
        authorEditText.setVisibility(View.GONE);
        requestLayout();
        if (delegate != null && currentRow != null) delegate.onTextChanged(currentRow);
    }

    public void updateLanguage() {
        if (currentRow != null) {
            updateLanguageButton(currentRow.block, true);
            highlightedSnapshot = null;
            scheduleHighlight();
        }
    }

    public BlockRow getRow() {
        return currentRow;
    }

    public RichEditText getEditText() {
        return editText;
    }

    public FloatingToolbar.StyleDelegate getStyleDelegate() {
        return editText;
    }

    public void persistStyle() {
        if (currentRow != null) applyStyledTextToBlock(currentRow.block, editText.getText());
    }

    private boolean isHighlightableCode() {
        return currentRow != null
            && currentRow.block instanceof TL_iv.pageBlockPreformatted
            && !TextUtils.isEmpty(((TL_iv.pageBlockPreformatted) currentRow.block).language);
    }

    private void scheduleHighlight() {
        if (highlightScheduled != null) {
            removeCallbacks(highlightScheduled);
            highlightScheduled = null;
        }
        if (!isHighlightableCode()) {
            highlightGeneration++;
            clearHighlight();
            highlightedSnapshot = null;
            return;
        }
        highlightScheduled = this::runHighlight;
        postDelayed(highlightScheduled, HIGHLIGHT_DEBOUNCE);
    }

    private void runHighlight() {
        highlightScheduled = null;
        if (!isHighlightableCode()) return;
        final String snapshot = editText.getText().toString();
        if (snapshot.equals(highlightedSnapshot)) return;
        final BlockRow row = currentRow;
        final String language = ((TL_iv.pageBlockPreformatted) row.block).language;
        final int generation = ++highlightGeneration;
        CodeHighlighting.highlightEditable(snapshot, language, result -> {
            if (generation != highlightGeneration || currentRow != row) return;
            final Editable editable = editText.getText();
            if (!TextUtils.equals(snapshot, editable)) return;
            applyColorSpans(editable, result);
            highlightedSnapshot = snapshot;
        });
    }

    private void applyColorSpans(Editable editable, SpannableString from) {
        final CodeHighlighting.ColorSpan[] old = editable.getSpans(0, editable.length(), CodeHighlighting.ColorSpan.class);
        for (int i = 0; i < old.length; ++i) {
            editable.removeSpan(old[i]);
        }
        final CodeHighlighting.ColorSpan[] now = from.getSpans(0, from.length(), CodeHighlighting.ColorSpan.class);
        final int len = editable.length();
        for (int i = 0; i < now.length; ++i) {
            final int start = from.getSpanStart(now[i]);
            final int end = from.getSpanEnd(now[i]);
            if (start < 0 || end > len || start >= end) continue;
            editable.setSpan(now[i], start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void clearHighlight() {
        final Editable editable = editText.getText();
        if (editable == null) return;
        final CodeHighlighting.ColorSpan[] spans = editable.getSpans(0, editable.length(), CodeHighlighting.ColorSpan.class);
        for (int i = 0; i < spans.length; ++i) {
            editable.removeSpan(spans[i]);
        }
    }

    public void requestEditFocus() {
        editText.requestEditFocus();
    }

    public void setLocked(boolean locked) {
        editText.setLocked(locked);
        authorEditText.setLocked(locked);
    }

    public void hideActionModes() {
        editText.hideActionMode();
        authorEditText.hideActionMode();
    }

    public void finishActionModes() {
        editText.finishActionMode();
        authorEditText.finishActionMode();
    }

    public boolean isPressOnText(int localX, int localY) {
        if (pressOnLayout(editText, row.getLeft() + editText.getLeft(), row.getTop() + editText.getTop(), localX, localY)) return true;
        if (authorEditText.getVisibility() == View.VISIBLE
            && pressOnLayout(authorEditText, authorEditText.getLeft(), authorEditText.getTop(), localX, localY)) return true;
        return false;
    }

    private static boolean pressOnLayout(RichEditText et, int baseLeft, int baseTop, int localX, int localY) {
        final Layout layout = et.getLayout();
        if (layout == null || et.length() == 0) return false;
        final int textX = localX - (baseLeft + et.getPaddingLeft());
        final int textY = localY - (baseTop + et.getPaddingTop());
        if (textY < 0 || textY >= layout.getHeight()) return false;
        final int line = layout.getLineForVertical(textY);
        if (line < 0 || line >= layout.getLineCount()) return false;
        final int extend = dp(24);
        final int contentWidth = Math.max(0, et.getWidth() - et.getPaddingLeft() - et.getPaddingRight());
        final float left = Math.max(0, layout.getLineLeft(line) - extend);
        final float right = Math.min(contentWidth, layout.getLineRight(line) + extend);
        return textX >= left && textX <= right;
    }

    public boolean isPressOnEmptyEditText(int localX, int localY) {
        if (insideEmpty(editText, row.getLeft() + editText.getLeft(), row.getTop() + editText.getTop(), localX, localY)) return true;
        if (authorEditText.getVisibility() == View.VISIBLE
            && insideEmpty(authorEditText, authorEditText.getLeft(), authorEditText.getTop(), localX, localY)) return true;
        return false;
    }

    private static boolean insideEmpty(RichEditText et, int baseLeft, int baseTop, int localX, int localY) {
        if (et.length() != 0) return false;
        return localX >= baseLeft && localX <= baseLeft + et.getWidth()
            && localY >= baseTop && localY <= baseTop + et.getHeight();
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> out) {
        final Layout layout = editText.getLayout();
        if (layout != null) {
            final int textX = row.getLeft() + editText.getLeft() + editText.getPaddingLeft();
            final int textY = row.getTop() + editText.getTop() + editText.getPaddingTop();
            out.add(new TextSelectionHelper.TextLayoutBlock() {
                @Override public Layout getLayout() { return layout; }
                @Override public int getX() { return textX; }
                @Override public int getY() { return textY; }
                @Override public int getRow() { return 0; }
                @Override public CharSequence getText() { return layout.getText(); }
            });
        }
        if (authorEditText.getVisibility() == View.VISIBLE) {
            final Layout aLayout = authorEditText.getLayout();
            if (aLayout != null) {
                final int ax = authorEditText.getLeft() + authorEditText.getPaddingLeft();
                final int ay = authorEditText.getTop() + authorEditText.getPaddingTop();
                out.add(new TextSelectionHelper.TextLayoutBlock() {
                    @Override public Layout getLayout() { return aLayout; }
                    @Override public int getX() { return ax; }
                    @Override public int getY() { return ay; }
                    @Override public int getRow() { return 1; }
                    @Override public CharSequence getText() { return aLayout.getText(); }
                });
            }
        }
    }

    @Override
    public void updateColors() {
        editText.updateColors();
        if (authorEditText != null) authorEditText.updateColors();
        bullet.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        if (quoteIcon != null) {
            quoteIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), PorterDuff.Mode.SRC_IN));
        }
    }

    private void applyListDecoration(BlockRow row) {
        final int level = row.level;
        if (level <= 0) {
            indentSpacer.setVisibility(View.GONE);
            bullet.setVisibility(View.GONE);
            checkBoxView.setVisibility(View.GONE);
            return;
        }
        LinearLayout.LayoutParams slp = (LinearLayout.LayoutParams) indentSpacer.getLayoutParams();
        slp.width = (level - 1) * dp(INDENT_DP_PER_LEVEL);
        indentSpacer.setLayoutParams(slp);
        indentSpacer.setVisibility(level > 1 ? View.VISIBLE : View.GONE);
        if (row.checkbox) {
            bullet.setVisibility(View.GONE);
            checkBoxView.setVisibility(View.VISIBLE);
            checkBoxView.setChecked(row.checked, false);
        } else {
            checkBoxView.setVisibility(View.GONE);
            bullet.setVisibility(View.VISIBLE);
            bullet.setText(row.num == 0 ? ("•◦▪".charAt((level - 1) % 3) + "") : (row.num + "."));
        }
    }

    private String getHint() {
        if (currentRow == null) return null;
        TL_iv.PageBlock block = currentRow.block;
        if (block instanceof TL_iv.pageBlockHeading1) return getString(currentRow.firstBlock ? R.string.ArticleHintTitle : R.string.ArticleHeading1);
        if (block instanceof TL_iv.pageBlockHeading2) return getString(R.string.ArticleHeading2);
        if (block instanceof TL_iv.pageBlockHeading3) return getString(R.string.ArticleHeading3);
        if (block instanceof TL_iv.pageBlockHeading4) return getString(R.string.ArticleHeading4);
        if (block instanceof TL_iv.pageBlockHeading5) return getString(R.string.ArticleHeading5);
        if (block instanceof TL_iv.pageBlockHeading6) return getString(R.string.ArticleHeading6);
        if (block instanceof TL_iv.pageBlockPreformatted) return getString(R.string.ArticleHintCode);
        if (block instanceof TL_iv.pageBlockBlockquote) return getString(R.string.ArticleHintQuote);
        if (block instanceof TL_iv.pageBlockPullquote) return getString(R.string.ArticleHintQuote);
        if (currentRow.singleParagraph) return getString(R.string.ArticleHintText);
        return null;
    }

    private void applyQuoteInset(BlockRow row) {
        int startI = RichBlockChrome.quoteInset(row);
        int endI = RichBlockChrome.quoteInsetEnd(row);
        if (startI <= 0 && endI <= 0) return;
        final boolean pre = row.block instanceof TL_iv.pageBlockPreformatted;
        int top = getPaddingTop();
        int bottom = getPaddingBottom();
        if (row.quoteFirst) top = RichBlockChrome.quoteTopPad(row) + (pre ? dp(CODE_TOP_LANG_ROOM_DP) : 0);
        if (row.quoteLast) bottom = RichBlockChrome.quoteBottomPad(row);
        if (pre) {
            startI += dp(8);
            endI += dp(8);
        }
        if (RichBlockChrome.rtl()) {
            setPadding(getPaddingLeft() + endI, top, getPaddingRight() + startI, bottom);
        } else {
            setPadding(getPaddingLeft() + startI, top, getPaddingRight() + endI, bottom);
        }
    }

    private void applyStyle(TL_iv.PageBlock block) {
        final int baseSize = SharedConfig.fontSize;
        editText.setCenterEmptyHint(false);
        editText.setHint(getHint());
        editText.setTextColorKey(Theme.key_windowBackgroundWhiteBlackText);
        if (block instanceof TL_iv.pageBlockPreformatted) {
            setPadding(dp(16), dp(6) + dp(CODE_TOP_LANG_ROOM_DP), dp(16), dp(12));
            editText.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            );
            editText.setAllowNewlines(true);
            editText.setSoftEnterNewline(false);
            editText.setGravity(Gravity.TOP | Gravity.START);
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, baseSize);
            editText.setTypeface(Typeface.MONOSPACE);
            editText.setAccentHint(false);
        } else if (block instanceof TL_iv.pageBlockBlockquote) {
            editText.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            );
            editText.setAllowNewlines(false);
            editText.setSoftEnterNewline(false);
            editText.setGravity(Gravity.TOP | Gravity.START);
            setPadding(dp(16 + 12), dp(6 + 8), dp(16 + 8), dp(8));
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, baseSize);
            editText.setTypeface(null);
            editText.setAccentHint(true);
        } else if (block instanceof TL_iv.pageBlockPullquote) {
            editText.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            );
            editText.setAllowNewlines(false);
            editText.setSoftEnterNewline(true);
            editText.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            editText.setCenterEmptyHint(true);
            setPadding(dp(16 + 12 + 30), dp(6 + 8), dp(16 + 12 + 30), dp(8));
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, baseSize);
            editText.setTypeface(AndroidUtilities.getTypeface("fonts/ritalic.ttf"));
            editText.setAccentHint(true);
        } else {
            editText.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            );
            editText.setAllowNewlines(false);
            editText.setSoftEnterNewline(false);
            editText.setGravity(Gravity.TOP | Gravity.START);
            setPadding(dp(16), dp(6), dp(16), dp(0));
            if (block instanceof TL_iv.pageBlockHeading1) {
                editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, baseSize + 2);
                editText.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
            } else if (block instanceof TL_iv.pageBlockHeading2) {
                editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, baseSize + 1);
                editText.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
            } else if (block instanceof TL_iv.pageBlockHeading3) {
                editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, baseSize);
                editText.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
            } else if (block instanceof TL_iv.pageBlockHeading4) {
                editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, baseSize - 1);
                editText.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
            } else if (block instanceof TL_iv.pageBlockHeading5) {
                editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, baseSize - 2);
                editText.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
            } else if (block instanceof TL_iv.pageBlockHeading6) {
                editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, baseSize - 3);
                editText.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
            } else if (block instanceof TL_iv.pageBlockFooter) {
                editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, baseSize - 2);
                editText.setTypeface(null);
                editText.setTextColorKey(Theme.key_chat_inReplyMessageText);
            } else {
                editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, baseSize);
                editText.setTypeface(null);
            }
            editText.setAccentHint(false);
        }
    }

    private static class Transform {
        final TL_iv.PageBlock block;
        final int level;
        final int num;
        final boolean checkbox;
        final boolean checked;
        Transform(TL_iv.PageBlock b, int lvl, int n) { this(b, lvl, n, false, false); }
        Transform(TL_iv.PageBlock b, int lvl, int n, boolean checkbox, boolean checked) {
            block = b; level = lvl; num = n; this.checkbox = checkbox; this.checked = checked;
        }
    }

    private static Transform newChecklistItem(boolean checked) {
        TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
        applyTextToBlock(p, "");
        return new Transform(p, 1, 0, true, checked);
    }

    private static int matchMarkdownCommand(String text, BlockRow row) {
        if (row == null || text == null) return CMD_NONE;
        if (row.level != 0 || !(row.block instanceof TL_iv.pageBlockParagraph)) return CMD_NONE;
        if (text.equals("> ")) return CMD_DETAILS;
        return CMD_NONE;
    }

    private static Transform matchMarkdownTrigger(String text, BlockRow row) {
        if (row == null) return null;
        if (text == null) return null;
        int n = text.length();
        if (n < 2 || text.charAt(n - 1) != ' ') return null;

        boolean isParagraph = row.block instanceof TL_iv.pageBlockParagraph;
        boolean isHeading = row.block instanceof TL_iv.pageBlockHeading1
            || row.block instanceof TL_iv.pageBlockHeading2
            || row.block instanceof TL_iv.pageBlockHeading3
            || row.block instanceof TL_iv.pageBlockHeading4
            || row.block instanceof TL_iv.pageBlockHeading5
            || row.block instanceof TL_iv.pageBlockHeading6;

        if (text.charAt(0) == '#' && (isParagraph || isHeading)) {
            int hashes = 0;
            for (int i = 0; i < n - 1; i++) {
                if (text.charAt(i) == '#') hashes++;
                else return null;
            }
            if (hashes < 1 || hashes > 6) return null;
            return new Transform(newHeading(hashes), row.level, row.num);
        }

        if (!isParagraph) return null;

        if (row.level == 0 && n == 2) {
            char c = text.charAt(0);
            if (c == '-' || c == '*' || c == '+') {
                TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
                applyTextToBlock(p, "");
                return new Transform(p, 1, 0);
            }
            if (c == '|') {
                return new Transform(newBlockquote(), 0, 0);
            }
        }

        if (row.level == 0 && n == 3 && text.charAt(0) == '[' && text.charAt(1) == ']') {
            return newChecklistItem(false);
        }
        if (row.level == 0 && n == 4 && text.charAt(0) == '[' && text.charAt(2) == ']') {
            char m = text.charAt(1);
            if (m == ' ') return newChecklistItem(false);
            if (m == 'x' || m == 'X') return newChecklistItem(true);
        }

        if (row.level == 0 && n == 3 && Character.isDigit(text.charAt(0))) {
            char d = text.charAt(1);
            if (d == '.' || d == ')') {
                TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
                applyTextToBlock(p, "");
                return new Transform(p, 1, 1);
            }
        }

        if (row.level == 0 && n == 4) {
            char c = text.charAt(0);
            if ((c == '-' || c == '*' || c == '_') && text.charAt(1) == c && text.charAt(2) == c) {
                return new Transform(new TL_iv.pageBlockDivider(), 0, 0);
            }
            if (c == '`' && text.charAt(1) == '`' && text.charAt(2) == '`') {
                return new Transform(new TL_iv.pageBlockPreformatted(), 0, 0);
            }
        }

        return null;
    }

    private static Transform matchEnterTrigger(String text, BlockRow row) {
        if (text == null || row == null) return null;
        String t = text.trim();
        if (t.length() == 3) {
            char c = t.charAt(0);
            if ((c == '-' || c == '*' || c == '_') && t.charAt(1) == c && t.charAt(2) == c) {
                return new Transform(new TL_iv.pageBlockDivider(), 0, 0);
            }
        }
        String tl = t.toLowerCase();
        if (tl.length() == 3 && tl.charAt(0) == '/' && tl.charAt(1) == 'h') {
            char d = tl.charAt(2);
            if (d >= '1' && d <= '6') {
                return new Transform(newHeading(d - '0'), row.level, row.num);
            }
        }
        if (tl.equals("/code") || tl.equals("/pre") || tl.equals("/preformatted")) {
            return new Transform(new TL_iv.pageBlockPreformatted(), 0, 0);
        }
        if (tl.equals("/footer")) {
            return new Transform(new TL_iv.pageBlockFooter(), 0, 0);
        }
        if (tl.equals("/quote") || tl.equals("/blockquote")) {
            return new Transform(newBlockquote(), 0, 0);
        }
        if (tl.equals("/pullquote")) {
            return new Transform(newPullquote(), 0, 0);
        }
        if (tl.equals("/table") || tl.startsWith("/table ")) {
            int rowsN = 2, colsN = 2;
            if (tl.length() > 7) {
                String size = tl.substring(7).trim();
                int x = size.indexOf('x');
                if (x < 0) x = size.indexOf('X');
                if (x > 0) {
                    try {
                        rowsN = Math.max(1, Math.min(20, Integer.parseInt(size.substring(0, x).trim())));
                        colsN = Math.max(1, Math.min(20, Integer.parseInt(size.substring(x + 1).trim())));
                    } catch (NumberFormatException ignored) {}
                }
            }
            return new Transform(newEmptyTable(rowsN, colsN), 0, 0);
        }
        return null;
    }

    private static String slashQuery(String text) {
        if (text == null || text.isEmpty() || text.charAt(0) != '/') return null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\n' || c == '\t') return null;
        }
        return text;
    }

    public void selectCommand(RichCommand cmd) {
        if (delegate == null || currentRow == null || cmd == null || cmd.commands.isEmpty()) return;
        for (final String trigger : cmd.commands) {
            final int command = matchCommand(trigger);
            if (command != CMD_NONE) {
                delegate.onCommand(currentRow, command);
                return;
            }
            Transform tr = matchEnterTrigger(trigger, currentRow);
            if (tr == null) tr = matchMarkdownTrigger(trigger + " ", currentRow);
            if (tr != null) {
                delegate.onTransform(currentRow, tr.block, tr.level, tr.num, tr.checkbox, tr.checked);
                return;
            }
        }
    }

    private static int matchCommand(String text) {
        if (text == null) return CMD_NONE;
        String tl = text.trim().toLowerCase();
        if (tl.equals("/img") || tl.equals("/pic") || tl.equals("/image") || tl.equals("/picture") || tl.equals("/photo")) return CMD_ATTACH_PHOTO;
        if (tl.equals("/vid") || tl.equals("/video")) return CMD_ATTACH_VIDEO;
        if (tl.equals("/audio") || tl.equals("/music")) return CMD_ATTACH_MUSIC;
        if (tl.equals("/map") || tl.equals("/location") || tl.equals("/loc")) return CMD_ATTACH_LOCATION;
        if (tl.equals("/latex") || tl.equals("/equation") || tl.equals("/math")) return CMD_MATH;
        if (tl.equals("/toggle") || tl.equals("/details")) return CMD_DETAILS;
        return CMD_NONE;
    }

    public static TL_iv.pageBlockTable newEmptyTable(int rowsN, int colsN) {
        TL_iv.pageBlockTable t = new TL_iv.pageBlockTable();
        t.bordered = true;
        t.striped = false;
        t.title = new TL_iv.textEmpty();
        t.rows = new java.util.ArrayList<>();
        for (int r = 0; r < rowsN; r++) {
            TL_iv.pageTableRow row = new TL_iv.pageTableRow();
            row.cells = new java.util.ArrayList<>();
            for (int c = 0; c < colsN; c++) {
                row.cells.add(TableModel.newEmptyCell());
            }
            t.rows.add(row);
        }
        return t;
    }

    private static TL_iv.PageBlock newHeading(int level) {
        switch (level) {
            case 1: return new TL_iv.pageBlockHeading1();
            case 2: return new TL_iv.pageBlockHeading2();
            case 3: return new TL_iv.pageBlockHeading3();
            case 4: return new TL_iv.pageBlockHeading4();
            case 5: return new TL_iv.pageBlockHeading5();
            case 6: return new TL_iv.pageBlockHeading6();
            default: return null;
        }
    }

    static String readPlainText(TL_iv.PageBlock block) {
        if (block == null) return null;
        return RichTextStyle.plainOf(block.text);
    }

    static CharSequence readStyledText(TL_iv.PageBlock block) {
        if (block == null) return null;
        return RichTextStyle.toSpannable(block.text, block);
    }

    static void applyStyledTextToBlock(TL_iv.PageBlock block, CharSequence styled) {
        setRichText(block, RichTextStyle.fromSpannable(styled));
    }

    static boolean isQuoteBlock(TL_iv.PageBlock block) {
        return block instanceof TL_iv.pageBlockBlockquote || block instanceof TL_iv.pageBlockPullquote;
    }

    static TL_iv.pageBlockBlockquote newBlockquote() {
        final TL_iv.pageBlockBlockquote q = new TL_iv.pageBlockBlockquote();
        q.caption = new TL_iv.textEmpty();
        return q;
    }

    static TL_iv.pageBlockPullquote newPullquote() {
        final TL_iv.pageBlockPullquote q = new TL_iv.pageBlockPullquote();
        q.caption = new TL_iv.textEmpty();
        return q;
    }

    static void ensureCaption(TL_iv.PageBlock block) {
        if (block instanceof TL_iv.pageBlockBlockquote) {
            if (((TL_iv.pageBlockBlockquote) block).caption == null) ((TL_iv.pageBlockBlockquote) block).caption = new TL_iv.textEmpty();
        } else if (block instanceof TL_iv.pageBlockPullquote) {
            if (((TL_iv.pageBlockPullquote) block).caption == null) ((TL_iv.pageBlockPullquote) block).caption = new TL_iv.textEmpty();
        }
    }

    static TL_iv.RichText extractCaption(TL_iv.PageBlock block) {
        if (block instanceof TL_iv.pageBlockBlockquote) return ((TL_iv.pageBlockBlockquote) block).caption;
        if (block instanceof TL_iv.pageBlockPullquote) return ((TL_iv.pageBlockPullquote) block).caption;
        return null;
    }

    static void setCaption(TL_iv.PageBlock block, TL_iv.RichText rt) {
        if (block instanceof TL_iv.pageBlockBlockquote) ((TL_iv.pageBlockBlockquote) block).caption = rt;
        else if (block instanceof TL_iv.pageBlockPullquote) ((TL_iv.pageBlockPullquote) block).caption = rt;
    }

    static String readAuthorPlain(TL_iv.PageBlock block) {
        return RichTextStyle.plainOf(extractCaption(block));
    }

    static CharSequence readAuthorStyled(TL_iv.PageBlock block) {
        return RichTextStyle.toSpannable(extractCaption(block));
    }

    public void persistAuthor() {
        if (currentRow == null || !isQuoteBlock(currentRow.block)) return;
        setCaption(currentRow.block, RichTextStyle.fromSpannable(authorEditText.getText()));
    }

    public RichEditText getAuthorEditText() {
        return authorEditText;
    }

    public boolean isAuthorVisible() {
        return authorEditText.getVisibility() == View.VISIBLE;
    }

    public boolean isAuthorFocused() {
        return authorEditText.isFocused();
    }

    public void focusAuthorEnd() {
        ensureAuthorVisibleAndFocus();
    }

    public void focusAuthorFromBody() {
        final float x = caretX(editText);
        if (authorEditText.getVisibility() != View.VISIBLE) {
            authorEditText.setVisibility(View.VISIBLE);
            requestLayout();
        }
        authorEditText.requestEditFocus();
        authorEditText.post(() -> {
            final Layout l = authorEditText.getLayout();
            int off = authorEditText.length();
            if (l != null) off = l.getOffsetForHorizontal(0, x);
            authorEditText.setSelection(Math.max(0, Math.min(off, authorEditText.length())));
        });
    }

    public void focusBodyFromAuthor() {
        final float x = caretX(authorEditText);
        editText.requestEditFocus();
        editText.post(() -> {
            final Layout l = editText.getLayout();
            int off = editText.length();
            if (l != null) {
                final int line = Math.max(0, l.getLineCount() - 1);
                off = l.getOffsetForHorizontal(line, x);
            }
            editText.setSelection(Math.max(0, Math.min(off, editText.length())));
        });
    }

    private static float caretX(RichEditText et) {
        final Layout l = et.getLayout();
        if (l == null) return 0;
        final int sel = Math.max(0, Math.min(et.getSelectionEnd(), et.length()));
        return l.getPrimaryHorizontal(sel);
    }

    private static void setRichText(TL_iv.PageBlock block, TL_iv.RichText rt) {
        block.text = rt;
    }

    public static void applyTextToBlock(TL_iv.PageBlock block, String text) {
        TL_iv.textPlain plain = new TL_iv.textPlain();
        plain.text = text;
        block.text = plain;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        if (authorEditText.getVisibility() == View.GONE) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
            return;
        }
        final int innerW = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        row.measure(
            MeasureSpec.makeMeasureSpec(innerW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        );
        authorEditText.measure(
            MeasureSpec.makeMeasureSpec(innerW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        );
        setMeasuredDimension(width, getPaddingTop() + row.getMeasuredHeight() + authorEditText.getMeasuredHeight() + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (authorEditText.getVisibility() == View.GONE) {
            super.onLayout(changed, left, top, right, bottom);
            return;
        }
        final int padL = getPaddingLeft();
        final int padT = getPaddingTop();
        row.layout(padL, padT, padL + row.getMeasuredWidth(), padT + row.getMeasuredHeight());
        final int ay = padT + row.getMeasuredHeight();
        authorEditText.layout(padL, ay, padL + authorEditText.getMeasuredWidth(), ay + authorEditText.getMeasuredHeight());
    }

    private boolean showCommandBackground;
    public void setShowCommandBackground(boolean show) {
        if (showCommandBackground == show) return;
        showCommandBackground = show;
        invalidate();
    }

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (currentRow != null && currentRow.block instanceof TL_iv.pageBlockPreformatted) {
            bgPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chat_inCodeBackground, resourcesProvider), .10f));
            final int qStart = RichBlockChrome.quoteInset(currentRow);
            final int qEnd = RichBlockChrome.quoteInsetEnd(currentRow);
            int bgLeft = 0;
            int bgRight = getWidth();
            if (qStart > 0 || qEnd > 0) {
                final int startInset = dp(16) + qStart;
                final int endInset = dp(16) + qEnd;
                bgLeft = RichBlockChrome.rtl() ? endInset : startInset;
                bgRight = getWidth() - (RichBlockChrome.rtl() ? startInset : endInset);
            }
            final int r = bgLeft > 0 || bgRight < getWidth() ? dp(8) : 0;
            canvas.drawRoundRect(bgLeft, dp(6), bgRight, getHeight(), r, r, bgPaint);
        } else if (currentRow != null && currentRow.block instanceof TL_iv.pageBlockBlockquote) {
            if (quoteLine == null) {
                quoteLine = new ReplyMessageLine(this);
                quoteLine.check(null, null, null, resourcesProvider, ReplyMessageLine.TYPE_QUOTE);
            }
            AndroidUtilities.rectTmp.set(dp(16), dp(6), getWidth() - dp(16), getHeight());
            final float rad = (float) Math.floor(SharedConfig.bubbleRadius / 3f);
            quoteLine.drawBackground(canvas, AndroidUtilities.rectTmp, rad, rad, rad, 1.0f);
            quoteLine.drawLine(canvas, AndroidUtilities.rectTmp, 1.0f);
        } else if (currentRow != null && currentRow.block instanceof TL_iv.pageBlockPullquote) {
            if (quoteLine == null) {
                quoteLine = new ReplyMessageLine(this);
                quoteLine.check(null, null, null, resourcesProvider, ReplyMessageLine.TYPE_QUOTE);
            }

            if (quoteIcon == null) {
                quoteIcon = getContext().getResources().getDrawable(R.drawable.mini_quote).mutate();
                quoteIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), PorterDuff.Mode.SRC_IN));
            }

            final Layout layout = editText.getLayout();
            float left = getWidth();
            float right = 0;
            if (layout != null && !TextUtils.isEmpty(layout.getText())) {
                for (int line = 0; line < layout.getLineCount(); ++line) {
                    left = Math.min(left, row.getLeft() + editText.getLeft() + editText.getPaddingLeft() + layout.getLineLeft(line));
                    right = Math.max(right, row.getLeft() + editText.getLeft() + editText.getPaddingLeft() + layout.getLineRight(line));
                }
            } else if (editText.getHint() != null) {
                float hintWidth = editText.getPaint().measureText(editText.getHint().toString());
                left  = Math.min(left, (getWidth() - hintWidth) / 2f + dp(2));
                right = Math.max(right, (getWidth() + hintWidth) / 2f + dp(2));
            }
            if (authorEditText.getVisibility() == View.VISIBLE) {
                final Layout aLayout = authorEditText.getLayout();
                if (aLayout != null && !TextUtils.isEmpty(aLayout.getText())) {
                    for (int line = 0; line < aLayout.getLineCount(); ++line) {
                        left = Math.min(left, authorEditText.getLeft() + authorEditText.getPaddingLeft() + aLayout.getLineLeft(line));
                        right = Math.max(right, authorEditText.getLeft() + authorEditText.getPaddingLeft() + aLayout.getLineRight(line));
                    }
                } else if (authorEditText.getHint() != null) {
                    float hintWidth = authorEditText.getPaint().measureText(authorEditText.getHint().toString());
                    left  = Math.min(left, (getWidth() - hintWidth) / 2f + dp(2));
                    right = Math.max(right, (getWidth() + hintWidth) / 2f + dp(2));
                }
            }
            if (left < right) {
                left -= dp(30);
                right += dp(30);
                final float rad = (float) Math.floor(SharedConfig.bubbleRadius / 2f);
                AndroidUtilities.rectTmp.set(left, dp(6), right, getHeight());
                quoteLine.drawBackground(canvas, AndroidUtilities.rectTmp, rad, rad, rad, 1.0f);

                canvas.save();
                quoteIcon.setBounds((int) left + dp(8), dp(6 + 7), (int) left + dp(8) + quoteIcon.getIntrinsicWidth(), dp(6 + 7) + quoteIcon.getIntrinsicHeight());
                canvas.scale(-1.0f, -1.0f, quoteIcon.getBounds().centerX(), quoteIcon.getBounds().centerY());
                quoteIcon.draw(canvas);
                canvas.restore();

                canvas.save();
                quoteIcon.setBounds((int) right - dp(8) - quoteIcon.getIntrinsicWidth(), getHeight() - dp(7) - quoteIcon.getIntrinsicHeight(), (int) right - dp(8), getHeight() - dp(7));
                canvas.scale(1.0f, -1.0f, quoteIcon.getBounds().centerX(), quoteIcon.getBounds().centerY());
                quoteIcon.draw(canvas);
                canvas.restore();
            }
        }
        if (showCommandBackground) {
            float left = getWidth(), right = 0, top = getHeight(), bottom = 0;
            final Layout layout = editText.getLayout();
            if (layout != null) {
                for (int line = 0; line < layout.getLineCount(); ++line) {
                    top = Math.min(top, getPaddingTop() + editText.getPaddingTop() + layout.getLineTop(line));
                    left = Math.min(left, row.getLeft() + editText.getLeft() + editText.getPaddingLeft() + layout.getLineLeft(line));
                    right = Math.max(right, row.getLeft() + editText.getLeft() + editText.getPaddingLeft() + layout.getLineRight(line));
                    bottom = Math.max(top, getPaddingTop() + editText.getPaddingTop() + layout.getLineBottom(line));
                }
            }
            if (left < right && top < bottom) {
                top -= dp(2);
                left -= dp(4);
                right += dp(4);
                bottom += dp(2);
                bgPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), .05f));
                canvas.drawRoundRect(left, top, right, bottom, dp(4), dp(4), bgPaint);
            }
        }
        TextSelectionHelper.ArticleTextSelectionHelper helper = delegate != null ? delegate.getSelectionHelper() : null;
        if (helper != null) {
            tmpBlocks.clear();
            fillTextLayoutBlocks(tmpBlocks);
            for (int i = 0; i < tmpBlocks.size(); i++) {
                final TextSelectionHelper.TextLayoutBlock b = tmpBlocks.get(i);
                canvas.save();
                canvas.translate(b.getX(), b.getY());
                helper.draw(canvas, this, i);
                canvas.restore();
            }
        }
        super.dispatchDraw(canvas);
    }

    private static class CheckBoxView extends View {
        private final CheckBoxBase checkBox;

        CheckBoxView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            checkBox = new CheckBoxBase(this, 20, resourcesProvider);
            checkBox.setColor(Theme.key_telegram_color, Theme.key_dialogCheckboxSquareDisabled, Theme.key_checkboxCheck);
            checkBox.setBackgroundType(10);
            checkBox.setDrawUnchecked(true);
            checkBox.setCustomRadius(dp(5));
        }

        void setChecked(boolean checked, boolean animated) {
            checkBox.setChecked(checked, animated);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            checkBox.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            checkBox.onDetachedFromWindow();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(24));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int s = dp(20);
            final int x = (getWidth() - s) / 2;
            final int y = (getHeight() - s) / 2;
            checkBox.setBounds(x, y, s, s);
            checkBox.draw(canvas);
        }
    }

    public static final class Factory extends UItem.UItemFactory<RichTextCell> {
        static { setup(new Factory()); }

        @Override
        public RichTextCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            final RichTextCell cell = new RichTextCell(context, resourcesProvider);
            cell.setBackground(new RichEditor.DraggingDrawable(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
            return cell;
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            final RichTextCell cell = (RichTextCell) view;
            final BlockRow row = (BlockRow) item.object;
            final Delegate delegate = (Delegate) item.object2;
            final boolean forceHint = item.red;
            cell.bind(row, delegate, forceHint);
        }

        public static UItem of(BlockRow row, Delegate delegate, boolean forceHint) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.object = row;
            item.object2 = delegate;
            item.red = forceHint;
            return item;
        }

        @Override
        public boolean isClickable() {
            return false;
        }
    }
}
