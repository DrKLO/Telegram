/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.messenger.utils.CopyUtilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

import java.util.List;

public class EditTextCaption extends EditTextBoldCursor {

    private static final int ACCESSIBILITY_ACTION_SHARE = 0x10000000;

    private String caption;
    private StaticLayout captionLayout;
    private int userNameLength;
    private int xOffset;
    private int yOffset;
    private int triesCount = 0;
    private boolean copyPasteShowed;
    private int hintColor;
    private EditTextCaptionDelegate delegate;
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private boolean allowTextEntitiesIntersection;
    private float offsetY;
    private int lineCount;
    private boolean isInitLineCount;
    private final Theme.ResourcesProvider resourcesProvider;

    public interface EditTextCaptionDelegate {
        void onSpansChanged();
    }

    public EditTextCaption(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (lineCount != getLineCount()) {
                    if (!isInitLineCount && getMeasuredWidth() > 0) {
                        onLineCountChanged(lineCount, getLineCount());
                    }
                    lineCount = getLineCount();
                }
            }
        });
        setClipToPadding(true);
    }

    protected void onLineCountChanged(int oldLineCount, int newLineCount) {

    }

    public void setCaption(String value) {
        if ((caption == null || caption.length() == 0) && (value == null || value.length() == 0) || caption != null && caption.equals(value)) {
            return;
        }
        caption = value;
        if (caption != null) {
            caption = caption.replace('\n', ' ');
        }
        requestLayout();
    }

    public void setDelegate(EditTextCaptionDelegate editTextCaptionDelegate) {
        delegate = editTextCaptionDelegate;
    }

    public void setAllowTextEntitiesIntersection(boolean value) {
        allowTextEntitiesIntersection = value;
    }

    public void makeSelectedBold() {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_BOLD;
        applyTextStyleToSelection(new TextStyleSpan(run));
    }

    public void makeSelectedSpoiler() {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
        applyTextStyleToSelection(new TextStyleSpan(run));
    }

    public void makeSelectedItalic() {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_ITALIC;
        applyTextStyleToSelection(new TextStyleSpan(run));
    }

    public void makeSelectedMono() {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_MONO;
        applyTextStyleToSelection(new TextStyleSpan(run));
    }

    public void makeSelectedStrike() {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_STRIKE;
        applyTextStyleToSelection(new TextStyleSpan(run));
    }

    public void makeSelectedUnderline() {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_UNDERLINE;
        applyTextStyleToSelection(new TextStyleSpan(run));
    }

    public void makeSelectedUrl() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
        builder.setTitle(LocaleController.getString("CreateLink", R.string.CreateLink));

        final EditTextBoldCursor editText = new EditTextBoldCursor(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64), MeasureSpec.EXACTLY));
            }
        };
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setText("http://");
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintText(LocaleController.getString("URL", R.string.URL));
        editText.setHeaderHintColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
        editText.setSingleLine(true);
        editText.setFocusable(true);
        editText.setTransformHintToHeader(true);
        editText.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated), getThemedColor(Theme.key_text_RedRegular));
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setBackgroundDrawable(null);
        editText.requestFocus();
        editText.setPadding(0, 0, 0, 0);
        builder.setView(editText);

        final int start;
        final int end;
        if (selectionStart >= 0 && selectionEnd >= 0) {
            start = selectionStart;
            end = selectionEnd;
            selectionStart = selectionEnd = -1;
        } else {
            start = getSelectionStart();
            end = getSelectionEnd();
        }

        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
            Editable editable = getText();
            CharacterStyle[] spans = editable.getSpans(start, end, CharacterStyle.class);
            if (spans != null && spans.length > 0) {
                for (int a = 0; a < spans.length; a++) {
                    CharacterStyle oldSpan = spans[a];
                    if (!(oldSpan instanceof AnimatedEmojiSpan)) {
                        int spanStart = editable.getSpanStart(oldSpan);
                        int spanEnd = editable.getSpanEnd(oldSpan);
                        editable.removeSpan(oldSpan);
                        if (spanStart < start) {
                            editable.setSpan(oldSpan, spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        if (spanEnd > end) {
                            editable.setSpan(oldSpan, end, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }
                }
            }
            try {
                editable.setSpan(new URLSpanReplacement(editText.getText().toString()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception ignore) {

            }
            if (delegate != null) {
                delegate.onSpansChanged();
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show().setOnShowListener(dialog -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });
        if (editText != null) {
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) editText.getLayoutParams();
            if (layoutParams != null) {
                if (layoutParams instanceof FrameLayout.LayoutParams) {
                    ((FrameLayout.LayoutParams) layoutParams).gravity = Gravity.CENTER_HORIZONTAL;
                }
                layoutParams.rightMargin = layoutParams.leftMargin = AndroidUtilities.dp(24);
                layoutParams.height = AndroidUtilities.dp(36);
                editText.setLayoutParams(layoutParams);
            }
            editText.setSelection(0, editText.getText().length());
        }
    }

    public void makeSelectedRegular() {
        applyTextStyleToSelection(null);
    }

    public void setSelectionOverride(int start, int end) {
        selectionStart = start;
        selectionEnd = end;
    }

    private void applyTextStyleToSelection(TextStyleSpan span) {
        int start;
        int end;
        if (selectionStart >= 0 && selectionEnd >= 0) {
            start = selectionStart;
            end = selectionEnd;
            selectionStart = selectionEnd = -1;
        } else {
            start = getSelectionStart();
            end = getSelectionEnd();
        }
        MediaDataController.addStyleToText(span, start, end, getText(), allowTextEntitiesIntersection);
        if (delegate != null) {
            delegate.onSpansChanged();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (Build.VERSION.SDK_INT < 23 && !hasWindowFocus && copyPasteShowed) {
            return;
        }
        try {
            super.onWindowFocusChanged(hasWindowFocus);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    protected void onContextMenuOpen() {

    }

    protected void onContextMenuClose() {

    }

    private ActionMode.Callback overrideCallback(final ActionMode.Callback callback) {
        ActionMode.Callback wrap = new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                copyPasteShowed = true;
                onContextMenuOpen();
                return callback.onCreateActionMode(mode, menu);
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return callback.onPrepareActionMode(mode, menu);
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (performMenuAction(item.getItemId())) {
                    mode.finish();
                    return true;
                }
                try {
                    return callback.onActionItemClicked(mode, item);
                } catch (Exception ignore) {

                }
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                copyPasteShowed = false;
                onContextMenuClose();
                callback.onDestroyActionMode(mode);
            }
        };
        if (Build.VERSION.SDK_INT >= 23) {
            return new ActionMode.Callback2() {
                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    return wrap.onCreateActionMode(mode, menu);
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return wrap.onPrepareActionMode(mode, menu);
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return wrap.onActionItemClicked(mode, item);
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    wrap.onDestroyActionMode(mode);
                }

                @Override
                public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
                    if (callback instanceof ActionMode.Callback2) {
                        ((ActionMode.Callback2) callback).onGetContentRect(mode, view, outRect);
                    } else {
                        super.onGetContentRect(mode, view, outRect);
                    }
                }
            };
        } else {
            return wrap;
        }
    }

    private boolean performMenuAction(int itemId) {
        if (itemId == R.id.menu_regular) {
            makeSelectedRegular();
            return true;
        } else if (itemId == R.id.menu_bold) {
            makeSelectedBold();
            return true;
        } else if (itemId == R.id.menu_italic) {
            makeSelectedItalic();
            return true;
        } else if (itemId == R.id.menu_mono) {
            makeSelectedMono();
            return true;
        } else if (itemId == R.id.menu_link) {
            makeSelectedUrl();
            return true;
        } else if (itemId == R.id.menu_strike) {
            makeSelectedStrike();
            return true;
        } else if (itemId == R.id.menu_underline) {
            makeSelectedUnderline();
            return true;
        } else if (itemId == R.id.menu_spoiler) {
            makeSelectedSpoiler();
            return true;
        }
        return false;
    }

    @Override
    public ActionMode startActionMode(final ActionMode.Callback callback, int type) {
        return super.startActionMode(overrideCallback(callback), type);
    }

    @Override
    public ActionMode startActionMode(final ActionMode.Callback callback) {
        return super.startActionMode(overrideCallback(callback));
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            isInitLineCount = getMeasuredWidth() == 0 && getMeasuredHeight() == 0;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (isInitLineCount) {
                lineCount = getLineCount();
            }
            isInitLineCount = false;
        } catch (Exception e) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(51));
            FileLog.e(e);
        }

        captionLayout = null;

        if (caption != null && caption.length() > 0) {
            CharSequence text = getText();
            if (text.length() > 1 && text.charAt(0) == '@') {
                int index = TextUtils.indexOf(text, ' ');
                if (index != -1) {
                    TextPaint paint = getPaint();
                    CharSequence str = text.subSequence(0, index + 1);
                    int size = (int) Math.ceil(paint.measureText(text, 0, index + 1));
                    int width = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
                    userNameLength = str.length();
                    CharSequence captionFinal = TextUtils.ellipsize(caption, paint, width - size, TextUtils.TruncateAt.END);
                    xOffset = size;
                    try {
                        captionLayout = new StaticLayout(captionFinal, getPaint(), width - size, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                        if (captionLayout.getLineCount() > 0) {
                            xOffset += -captionLayout.getLineLeft(0);
                        }
                        yOffset = (getMeasuredHeight() - captionLayout.getLineBottom(0)) / 2 + AndroidUtilities.dp(0.5f);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        }
    }

    public String getCaption() {
        return caption;
    }

    @Override
    public void setHintColor(int value) {
        super.setHintColor(value);
        hintColor = value;
        invalidate();
    }

    public void setOffsetY(float offset) {
        this.offsetY = offset;
        invalidate();
    }

    public float getOffsetY() {
        return offsetY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.translate(0, offsetY);
        super.onDraw(canvas);
        try {
            if (captionLayout != null && userNameLength == length()) {
                Paint paint = getPaint();
                int oldColor = getPaint().getColor();
                paint.setColor(hintColor);
                canvas.save();
                canvas.translate(xOffset, yOffset);
                captionLayout.draw(canvas);
                canvas.restore();
                paint.setColor(oldColor);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        canvas.restore();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        final AccessibilityNodeInfoCompat infoCompat = AccessibilityNodeInfoCompat.wrap(info);
        if (!TextUtils.isEmpty(caption)) {
            infoCompat.setHintText(caption);
        }
        final List<AccessibilityNodeInfoCompat.AccessibilityActionCompat> actions = infoCompat.getActionList();
        for (int i = 0, size = actions.size(); i < size; i++) {
            final AccessibilityNodeInfoCompat.AccessibilityActionCompat action = actions.get(i);
            if (action.getId() == ACCESSIBILITY_ACTION_SHARE) {
                infoCompat.removeAction(action);
                break;
            }
        }
        if (hasSelection()) {
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_spoiler, LocaleController.getString("Spoiler", R.string.Spoiler)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_bold, LocaleController.getString("Bold", R.string.Bold)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_italic, LocaleController.getString("Italic", R.string.Italic)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_mono, LocaleController.getString("Mono", R.string.Mono)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_strike, LocaleController.getString("Strike", R.string.Strike)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_underline, LocaleController.getString("Underline", R.string.Underline)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_link, LocaleController.getString("CreateLink", R.string.CreateLink)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_regular, LocaleController.getString("Regular", R.string.Regular)));
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        return performMenuAction(action) || super.performAccessibilityAction(action, arguments);
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.paste) {
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clipData = clipboard.getPrimaryClip();
            if (clipData != null && clipData.getItemCount() == 1 && clipData.getDescription().hasMimeType("text/html")) {
                try {
                    String html = clipData.getItemAt(0).getHtmlText();
                    Spannable pasted = CopyUtilities.fromHTML(html);
                    Emoji.replaceEmoji(pasted, getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false, null);
                    AnimatedEmojiSpan[] spans = pasted.getSpans(0, pasted.length(), AnimatedEmojiSpan.class);
                    if (spans != null) {
                        for (int k = 0; k < spans.length; ++k) {
                            spans[k].applyFontMetrics(getPaint().getFontMetricsInt(), AnimatedEmojiDrawable.getCacheTypeForEnterView());
                        }
                    }
                    int start = Math.max(0, getSelectionStart());
                    int end   = Math.min(getText().length(), getSelectionEnd());
                    setText(getText().replace(start, end, pasted));
                    setSelection(start + pasted.length(), start + pasted.length());
                    return true;
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } else if (id == android.R.id.copy) {
            int start = Math.max(0, getSelectionStart());
            int end = Math.min(getText().length(), getSelectionEnd());
            try {
                AndroidUtilities.addToClipboard(getText().subSequence(start, end));
                return true;
            } catch (Exception e) {

            }
        } else if (id == android.R.id.cut) {
            int start = Math.max(0, getSelectionStart());
            int end = Math.min(getText().length(), getSelectionEnd());
            try {
                AndroidUtilities.addToClipboard(getText().subSequence(start, end));
                SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
                if (start != 0) {
                    stringBuilder.append(getText().subSequence(0, start));
                }
                if (end != getText().length()) {
                    stringBuilder.append(getText().subSequence(end, getText().length()));
                }
                setText(stringBuilder);
                setSelection(start, start);
                return true;
            } catch (Exception e) {

            }
        }
        return super.onTextContextMenuItem(id);
    }
}
