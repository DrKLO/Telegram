package org.Tajgram.ui.iv;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.KeyListener;
import android.util.Log;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.inputmethod.EditorInfo;

import org.Tajgram.messenger.AndroidUtilities;
import org.Tajgram.messenger.LocaleController;
import org.Tajgram.messenger.R;
import org.Tajgram.ui.ActionBar.Theme;
import org.Tajgram.ui.Components.EditTextCaption;
import org.Tajgram.ui.Components.TextStyleSpan;
import org.Tajgram.ui.Components.TypefaceSpan;

public class RichEditText extends EditTextCaption {

    public interface Listener {
        default void onEnterPressed(RichEditText editText) {}
        default void onBackspaceOnEmpty(RichEditText editText) {}
        default boolean onBackspaceAtStart(RichEditText editText) { return false; }
        default void onTextChanged(RichEditText editText, Editable text) {}
        default void onSelectionChanged(RichEditText editText, int selStart, int selEnd) {}
        default boolean onTab(RichEditText editText, boolean shift) { return false; }
        default void onRequestWindowFocusable(RichEditText editText, boolean showKeyboard) {}
    }

    private Listener listener;
    private Theme.ResourcesProvider resourcesProvider;
    private boolean ignoreTextChange;
    private KeyListener savedKeyListener;

    public RichEditText(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        this.resourcesProvider = resourcesProvider;

        setBackground(null);
        setCursorWidth(1.5f);
        setGravity(Gravity.TOP | Gravity.START);
        setInputType(getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
        setImeOptions(EditorInfo.IME_ACTION_NEXT);

        setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT && listener != null) {
                listener.onEnterPressed(this);
                return true;
            }
            return false;
        });

        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (ignoreTextChange || listener == null) return;
                // Soft keyboards on a multiline field insert '\n' instead of firing IME_ACTION_NEXT.
                // Each block is a single paragraph, so treat a newline as Enter (new block / trigger).
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

        applyColors();
    }

    @Override
    protected Theme.ResourcesProvider getResourcesProvider() {
        return resourcesProvider;
    }

    @Override
    protected void extendActionMode(ActionMode actionMode, Menu menu) {
        if (menu.findItem(R.id.menu_bold) != null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            menu.removeItem(android.R.id.shareText);
        }
        int order = 6;
        menu.add(R.id.menu_groupbolditalic, R.id.menu_spoiler, order++, LocaleController.getString(R.string.Spoiler));

        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(LocaleController.getString(R.string.Bold));
        stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        menu.add(R.id.menu_groupbolditalic, R.id.menu_bold, order++, stringBuilder);
        stringBuilder = new SpannableStringBuilder(LocaleController.getString(R.string.Italic));
        stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/ritalic.ttf")), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        menu.add(R.id.menu_groupbolditalic, R.id.menu_italic, order++, stringBuilder);
        stringBuilder = new SpannableStringBuilder(LocaleController.getString(R.string.Mono));
        stringBuilder.setSpan(new TypefaceSpan(Typeface.MONOSPACE), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        menu.add(R.id.menu_groupbolditalic, R.id.menu_mono, order++, stringBuilder);
        stringBuilder = new SpannableStringBuilder(LocaleController.getString(R.string.Strike));
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_STRIKE;
        stringBuilder.setSpan(new TextStyleSpan(run), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        menu.add(R.id.menu_groupbolditalic, R.id.menu_strike, order++, stringBuilder);
        stringBuilder = new SpannableStringBuilder(LocaleController.getString(R.string.Underline));
        run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_UNDERLINE;
        stringBuilder.setSpan(new TextStyleSpan(run), 0, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        menu.add(R.id.menu_groupbolditalic, R.id.menu_underline, order++, stringBuilder);
        menu.add(R.id.menu_groupbolditalic, R.id.menu_link, order++, LocaleController.getString(R.string.CreateLink));
        menu.add(R.id.menu_groupbolditalic, R.id.menu_date, order++, LocaleController.getString(R.string.FormattedDate));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setTextSilently(CharSequence text) {
        ignoreTextChange = true;
        setText(text);
        setSelection(length());
        ignoreTextChange = false;
    }

    public void applyColors() {
        setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
        setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        setHandlesColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider));
    }

    public void setLocked(boolean locked) {
        if (locked) {
            if (savedKeyListener == null) {
                savedKeyListener = getKeyListener();
            }
            setKeyListener(null);
            setCursorVisible(false);
            clearFocus();
            setFocusable(false);
            setFocusableInTouchMode(false);
            AndroidUtilities.hideKeyboard(this);
        } else {
            if (savedKeyListener != null) {
                setKeyListener(savedKeyListener);
                savedKeyListener = null;
            }
            setCursorVisible(true);
            setFocusable(true);
            setFocusableInTouchMode(true);
        }
    }

    public void requestEditFocus() {
        // ChatAttachAlert's window starts non-focusable; make it focusable before
        // requesting focus, otherwise the keyboard won't open (same as commentTextView)
        if (listener != null) {
            listener.onRequestWindowFocusable(this, true);
        }
        requestFocus();
        AndroidUtilities.showKeyboard(this);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN && listener != null && isEnabled() && isFocusable()) {
            listener.onRequestWindowFocusable(this, true);
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_TAB) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                Log.d("RICHED", "RichEditText.dispatchKeyEvent TAB shift=" + event.isShiftPressed() + " listener=" + (listener != null));
                if (listener != null) listener.onTab(this, event.isShiftPressed());
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
        if (keyCode == KeyEvent.KEYCODE_ENTER && listener != null) {
            listener.onEnterPressed(this);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (listener != null) {
            listener.onSelectionChanged(this, selStart, selEnd);
        }
    }
}
