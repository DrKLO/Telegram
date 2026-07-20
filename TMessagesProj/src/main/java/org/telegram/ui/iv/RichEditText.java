package org.telegram.ui.iv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.TextStyleSpan;

public class RichEditText extends EditTextCaption {

    public interface Listener {
        default void onEnterPressed(RichEditText editText) {}
        default void onBackspaceOnEmpty(RichEditText editText) {}
        default boolean onBackspaceAtStart(RichEditText editText) { return false; }
        default void onTextChanged(RichEditText editText, Editable text) {}
        default void onTextWillChange(RichEditText editText, int removed, int added) {}
        default void onSelectionChanged(RichEditText editText, int selStart, int selEnd) {}
        default boolean onTab(RichEditText editText, boolean shift) { return false; }
        default void onRequestWindowFocusable(RichEditText editText, boolean showKeyboard) {}
        default void onLockedInsert(RichEditText editText, CharSequence text) {}
        default boolean onSelectAll(RichEditText editText) { return false; }
        default boolean onPaste(RichEditText editText) { return false; }
    }

    private Listener listener;
    private Theme.ResourcesProvider resourcesProvider;
    private boolean ignoreTextChange;
    private boolean insertingNewline;
    private boolean softEnterNewline;
    private boolean accentHint;
    private boolean locked;
    private boolean allowNewlines;
    private boolean centerEmptyHint;
    private boolean applyingEmptyHint;

    private LinkPath markPath;
    private Paint markPaint;
    private Layout lastMarkLayout;
    private int lastMarkTextLength = -1;
    private boolean markPathDirty = true;
    private final InputFilter lockingFilter = (source, start, end, dest, dstart, dend) -> {
        if (!locked) return null;
        if (ignoreTextChange) return null;
        if (listener != null && source != null && end > start && dstart == dend) {
            listener.onLockedInsert(this, source.subSequence(start, end));
        }
        return dest.subSequence(dstart, dend);
    };

    public TL_iv.PageBlock block;
    public void setBlock(TL_iv.PageBlock block) {
        this.block = block;
    }

    public void setAccentHint(boolean enable) {
        if (accentHint == enable) return;
        accentHint = enable;
        updateColors();
    }

    public void setCenterEmptyHint(boolean enable) {
        if (centerEmptyHint == enable) return;
        centerEmptyHint = enable;
        if (enable) {
            refreshEmptyHintGravity();
        } else {
            final int base = AndroidUtilities.dp(2);
            setPadding(base, getPaddingTop(), base, getPaddingBottom());
        }
    }

    private void refreshEmptyHintGravity() {
        if (!centerEmptyHint) return;
        applyingEmptyHint = true;
        final int base = AndroidUtilities.dp(2);
        final CharSequence hint = getHint();
        if (length() == 0 && getWidth() > 0 && !TextUtils.isEmpty(hint)) {
            final float hintWidth = getPaint().measureText(hint.toString());
            final int extra = Math.max(0, Math.round((getWidth() - base * 2 - hintWidth) / 2f));
            super.setGravity(Gravity.TOP | Gravity.LEFT);
            setPadding(base + extra, getPaddingTop(), base, getPaddingBottom());
        } else {
            super.setGravity(Gravity.CENTER);
            setPadding(base, getPaddingTop(), base, getPaddingBottom());
        }
        applyingEmptyHint = false;
    }

    @Override
    public void setGravity(int gravity) {
        if (!applyingEmptyHint) centerEmptyHint = false;
        super.setGravity(gravity);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        refreshEmptyHintGravity();
    }

    public RichEditText(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        this.resourcesProvider = resourcesProvider;

        setBackground(null);
        setCursorWidth(1.5f);
        setGravity(Gravity.TOP | Gravity.START);
        setInputType(getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
        setImeOptions(EditorInfo.IME_ACTION_NEXT);

        ActionMode.Callback noopCallback = new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) { return false; }
            @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }
            @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) { return false; }
            @Override public void onDestroyActionMode(ActionMode mode) {}
        };
        ActionMode.Callback insertionCallback = new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) { return true; }
            @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                if (length() != 0) return false;
                for (int i = menu.size() - 1; i >= 0; i--) {
                    final int id = menu.getItem(i).getItemId();
                    if (id != android.R.id.paste && id != android.R.id.pasteAsPlainText) {
                        menu.removeItem(id);
                    }
                }
                return true;
            }
            @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) { return false; }
            @Override public void onDestroyActionMode(ActionMode mode) {}
        };
        setCustomSelectionActionModeCallback(noopCallback);
        if (Build.VERSION.SDK_INT >= 23) {
            setCustomInsertionActionModeCallback(insertionCallback);
        }

        setOnLongClickListener(v -> length() != 0);
        updateLongClickForEmpty();

        setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT && listener != null && !allowNewlines) {
                if (softEnterNewline) {
                    insertNewlineAtSelection();
                } else {
                    listener.onEnterPressed(this);
                }
                return true;
            }
            return false;
        });

        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (ignoreTextChange || listener == null) return;
                listener.onTextWillChange(RichEditText.this, count, after);
            }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                markPathDirty = true;
                refreshEmptyHintGravity();
                updateLongClickForEmpty();
            }
            @Override public void afterTextChanged(Editable s) {
                if (ignoreTextChange || listener == null) return;
                if (allowNewlines || insertingNewline || softEnterNewline) {
                    listener.onTextChanged(RichEditText.this, s);
                    return;
                }
                boolean hadNewline = false;
                ignoreTextChange = true;
                for (int i = s.length() - 1; i >= 0; i--) {
                    if (s.charAt(i) == '\n') {
                        s.delete(i, i + 1);
                        hadNewline = true;
                    }
                }
                ignoreTextChange = false;
                if (hadNewline) {
                    listener.onEnterPressed(RichEditText.this);
                    return;
                }
                listener.onTextChanged(RichEditText.this, s);
            }
        });

        updateColors();
    }

    @Override
    protected Theme.ResourcesProvider getResourcesProvider() {
        return resourcesProvider;
    }

    @Override
    protected void extendActionMode(ActionMode actionMode, Menu menu) {

    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setAllowNewlines(boolean allow) {
        this.allowNewlines = allow;
    }

    public void setSoftEnterNewline(boolean enable) {
        this.softEnterNewline = enable;
    }

    @Override
    public void setInputType(int type) {
        final boolean changed = getInputType() != type;
        super.setInputType(type);
        if (changed && isFocused()) {
            final InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.restartInput(this);
        }
    }

    public void setTextSilently(CharSequence text) {
        ignoreTextChange = true;
        setText(text);
        setSelection(length());
        ignoreTextChange = false;
    }

    public void deleteToEndSilently(int from) {
        final Editable e = getText();
        if (e == null || from < 0 || from >= e.length()) return;
        ignoreTextChange = true;
        e.delete(from, e.length());
        ignoreTextChange = false;
    }

    public void appendSilently(CharSequence text) {
        final Editable e = getText();
        if (e == null || text == null || text.length() == 0) return;
        ignoreTextChange = true;
        e.append(text);
        ignoreTextChange = false;
    }

    private int textColorKey = Theme.key_windowBackgroundWhiteBlackText;
    public void setTextColorKey(int textColorKey) {
        this.textColorKey = textColorKey;
        updateColors();
    }

    public void updateColors() {
        setTextColor(Theme.getColor(textColorKey, resourcesProvider));
        setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        setHintTextColor(accentHint ? Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), .50f) : Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
        setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        setHandlesColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider));
    }

    public void setLocked(boolean locked) {
        if (this.locked == locked) return;
        this.locked = locked;
        InputFilter[] cur = getFilters();
        boolean present = false;
        for (InputFilter f : cur) if (f == lockingFilter) { present = true; break; }
        if (locked && !present) {
            InputFilter[] now = new InputFilter[cur.length + 1];
            System.arraycopy(cur, 0, now, 0, cur.length);
            now[cur.length] = lockingFilter;
            setFilters(now);
        }
        setAllowDrawCursor(!locked);
        setCursorVisible(!locked);
    }

    public void requestEditFocus() {
        if (listener != null) {
            listener.onRequestWindowFocusable(this, true);
        }
        requestFocus();
        AndroidUtilities.showKeyboard(this);
    }

    public void requestEditFocusRebuild() {
        finishActionMode();
        if (isFocused()) {
            clearFocus();
        }
        requestEditFocus();
        finishActionMode();
        post(this::finishActionMode);
    }

    public void finishActionMode() {
        if (floatingActionMode != null) {
            try {
                floatingActionMode.finish();
            } catch (Exception ignore) {}
        }
    }

    private float mathDownX, mathDownY;
    private long mathDownTime;
    private int touchSlop;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && listener != null && isEnabled() && isFocusable()) {
            listener.onRequestWindowFocusable(this, true);
        }
        if (!locked) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mathDownX = event.getX();
                mathDownY = event.getY();
                mathDownTime = event.getEventTime();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (touchSlop == 0) touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                final float dx = event.getX() - mathDownX, dy = event.getY() - mathDownY;
                final boolean tap = dx * dx + dy * dy <= touchSlop * touchSlop
                    && event.getEventTime() - mathDownTime < ViewConfiguration.getLongPressTimeout();
                if (tap) {
                    final MathSpan span = mathSpanAt(event.getX(), event.getY());
                    if (span != null) {
                        openMathEditor(span);
                        return true;
                    }
                }
            }
        }
        return super.onTouchEvent(event);
    }

    private MathSpan mathSpanAt(float x, float y) {
        final Layout layout = getLayout();
        final Editable text = getText();
        if (layout == null || text == null || text.length() == 0) return null;
        final int yy = (int) (y - getTotalPaddingTop() + getScrollY());
        final int line = layout.getLineForVertical(yy);
        final float xx = x - getTotalPaddingLeft() + getScrollX();
        if (xx < layout.getLineLeft(line) - AndroidUtilities.dp(2) || xx > layout.getLineRight(line) + AndroidUtilities.dp(2)) {
            return null;
        }
        final int offset = layout.getOffsetForHorizontal(line, xx);
        final int from = Math.max(0, offset - 1);
        final int to = Math.min(text.length(), offset + 1);
        final MathSpan[] spans = text.getSpans(from, to, MathSpan.class);
        for (MathSpan s : spans) {
            final int ss = text.getSpanStart(s), se = text.getSpanEnd(s);
            if (ss < 0 || se < 0) continue;
            final float left = layout.getPrimaryHorizontal(ss);
            final float right = se <= text.length() ? layout.getPrimaryHorizontal(se) : left;
            if (xx >= Math.min(left, right) - AndroidUtilities.dp(2) && xx <= Math.max(left, right) + AndroidUtilities.dp(2)) {
                return s;
            }
        }
        return null;
    }

    private void openMathEditor(MathSpan span) {
        ChatAttachAlertRichLayout.showEditLatexSheet(getContext(), span.source, newSource -> {
            if (TextUtils.isEmpty(newSource)) return;
            final Editable text = getText();
            final int s = text.getSpanStart(span);
            final int e = text.getSpanEnd(span);
            if (s < 0 || e < 0) return;
            final MathSpan ns = MathSpan.create(newSource, getCurrentTextColor(), AndroidUtilities.dp(4 + SharedConfig.fontSize));
            if (ns == null) return;
            final boolean wasLocked = locked;
            if (wasLocked) setLocked(false);
            final SpannableString placeholder = new SpannableString(" ");
            placeholder.setSpan(ns, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            final int f = Math.max(0, Math.min(s, length()));
            final int t = Math.max(f, Math.min(e, length()));
            text.replace(f, t, placeholder);
            setSelection(Math.min(f + 1, length()));
            if (wasLocked) setLocked(true);
        }, resourcesProvider);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_TAB) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (listener != null) listener.onTab(this, event.isShiftPressed());
            }
            return true;
        }
        final int kc = event.getKeyCode();
        if ((kc == KeyEvent.KEYCODE_ENTER || kc == KeyEvent.KEYCODE_NUMPAD_ENTER) && listener != null && !allowNewlines) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                final boolean soft = (event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) != 0;
                if (softEnterNewline && (soft || event.isShiftPressed())) {
                    insertNewlineAtSelection();
                } else {
                    listener.onEnterPressed(this);
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DEL && listener != null) {
            if (length() == 0) {
                listener.onBackspaceOnEmpty(this);
                return true;
            }
            if (getSelectionStart() == 0 && getSelectionEnd() == 0) {
                if (listener.onBackspaceAtStart(this)) return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void insertNewlineAtSelection() {
        int start = Math.max(0, getSelectionStart());
        int end = Math.max(0, getSelectionEnd());
        if (start > end) { final int t = start; start = end; end = t; }
        insertingNewline = true;
        getText().replace(start, end, "\n");
        insertingNewline = false;
        setSelection(start + 1);
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.selectAll && listener != null && listener.onSelectAll(this)) {
            return true;
        }
        if (id == android.R.id.paste && listener != null && listener.onPaste(this)) {
            return true;
        }
        return super.onTextContextMenuItem(id);
    }

    private void updateLongClickForEmpty() {
        setLongClickable(length() == 0);
    }

    @Override
    protected void notifySpansChanged() {
        super.notifySpansChanged();
        markPathDirty = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        buildMarkPath();
        if (markPath != null) {
            if (markPaint == null) {
                markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                markPaint.setPathEffect(LinkPath.getRoundedEffect());
            }
            markPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection, resourcesProvider) & 0x33ffffff);
            canvas.save();
            canvas.translate(getPaddingLeft(), offsetY);
            canvas.drawPath(markPath, markPaint);
            canvas.restore();
        }
        super.onDraw(canvas);
    }

    private void buildMarkPath() {
        final Layout layout = getLayout();
        if (layout == null) {
            markPath = null;
            lastMarkLayout = null;
            lastMarkTextLength = -1;
            return;
        }
        final CharSequence text = layout.getText();
        if (!markPathDirty && layout == lastMarkLayout && text.length() == lastMarkTextLength) {
            return;
        }
        markPathDirty = false;
        lastMarkLayout = layout;
        lastMarkTextLength = text.length();
        markPath = null;
        if (!(text instanceof Spanned)) {
            return;
        }
        final Spanned spanned = (Spanned) text;
        final TextStyleSpan[] spans = spanned.getSpans(0, spanned.length(), TextStyleSpan.class);
        LinkPath path = null;
        for (TextStyleSpan span : spans) {
            final int flags = span.getStyleFlags();
            if ((flags & TextStyleSpan.FLAG_STYLE_MARKED) == 0) continue;
            final int start = spanned.getSpanStart(span);
            final int end = spanned.getSpanEnd(span);
            if (start < 0 || end <= start) continue;
            if (path == null) {
                path = new LinkPath(true);
                path.setAllowReset(false);
            }
            path.setCurrentLayout(layout, start, 0);
            int shift = 0;
            if ((flags & TextStyleSpan.FLAG_STYLE_SUPERSCRIPT) != 0) shift = -AndroidUtilities.dp(6);
            else if ((flags & TextStyleSpan.FLAG_STYLE_SUBSCRIPT) != 0) shift = AndroidUtilities.dp(2);
            path.setBaselineShift(shift != 0 ? shift + AndroidUtilities.dp(shift > 0 ? 5 : -2) : 0);
            layout.getSelectionPath(start, end, path);
        }
        if (path != null) {
            path.setAllowReset(true);
        }
        markPath = path;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (listener != null) {
            listener.onSelectionChanged(this, selStart, selEnd);
        }
    }

    @Override
    public int getCurrentStyle(int start, int end) {
        final Editable editable = getText();
        if (editable == null) return 0;
        start = Math.max(0, start);
        end = Math.min(end, editable.length());
        if (start >= end) return 0;
        return RichTextStyle.stylesFullyCovering(editable, start, end);
    }

    @Override
    public void addStyle(int flag, int start, int end) {
        final Editable editable = getText();
        if (editable == null || start < 0 || end < 0 || start >= end) return;
        end = Math.min(end, editable.length());
        if (start >= end) return;
        RichTextStyle.setStyle(editable, start, end, flag, true, block);
        if ((flag & org.telegram.ui.Components.TextStyleSpan.FLAG_STYLE_SPOILER) != 0) invalidateSpoilers();
        notifySpansChanged();
    }

    @Override
    public void removeStyle(int flag, int start, int end) {
        final Editable editable = getText();
        if (editable == null || start < 0 || end < 0 || start >= end) return;
        end = Math.min(end, editable.length());
        if (start >= end) return;
        RichTextStyle.setStyle(editable, start, end, flag, false, block);
        if ((flag & org.telegram.ui.Components.TextStyleSpan.FLAG_STYLE_SPOILER) != 0) invalidateSpoilers();
        notifySpansChanged();
    }
}
