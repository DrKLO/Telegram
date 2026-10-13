package org.telegram.ui.Wallet;

import org.telegram.messenger.LocaleController;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;

public class WalletWordCell extends FrameLayout {

    private final EditText editText;
    private final TextView numberView;
    private final FrameLayout actionView;
    private final TextView pasteButton;
    private final ImageView overflowButton;
    private final int index;
    private final Theme.ResourcesProvider resourcesProvider;
    private Runnable onTextChangedListener;
    private Runnable onPasteListener;
    private Runnable onErrorClearListener;
    private Runnable onNextListener;

    private PopupWindow tooltip;

    private boolean error;

    public WalletWordCell(Context context, int index, boolean showPaste, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.index = index;
        this.resourcesProvider = resourcesProvider;

        setBackground(Theme.createRoundRectDrawable(dp(16), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider), .50f)));
        setPadding(dp(20), 0, dp(11), 0);

        numberView = new TextView(context);
        numberView.setTextSize(15);
        numberView.setGravity(Gravity.CENTER);
        numberView.setIncludeFontPadding(false);
        numberView.setText((index + 1) + ".");
        numberView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourcesProvider));
        addView(numberView, LayoutHelper.createFrame(22, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        setClipChildren(false);

        editText = new EditText(context);
        editText.setTextSize(16);
        editText.setGravity(Gravity.CENTER_VERTICAL);
        editText.setBackground(null);
        editText.setPadding(0, dp(15), 0, dp(15));
        editText.setIncludeFontPadding(false);
        editText.setSingleLine(true);
        editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        editText.setTypeface(Typeface.DEFAULT);
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        editText.setHint("");
        editText.setOnFocusChangeListener((v, focused) -> {
            if (focused) {
                clearError();
                updateNumberColor();
                showTooltipIfNeeded();
            } else {
                hideTooltip();
                validate();
            }
            updateActionView();
        });
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (error && !s.toString().trim().toLowerCase().matches(".*\\s+.*") && isWordValid()) {
                    clearError();
                }
                updateNumberColor();
                updateActionView();
                if (editText.hasFocus()) {
                    updateTooltip(s.toString().trim().toLowerCase());
                }
                if (onTextChangedListener != null) {
                    onTextChangedListener.run();
                }
            }
        });
        addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, 30, 0, showPaste ? 64 : 8, 0));

        editText.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER);
            if (actionId == EditorInfo.IME_ACTION_NEXT || enter) {
                // Keep focus here until key-up so the next field cannot handle the same press.
                if (event == null || event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    onNextPressed();
                }
                return true;
            }
            return false;
        });

        actionView = new FrameLayout(context);
        addView(actionView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
        if (showPaste) {
            pasteButton = new TextView(context);
            pasteButton.setTextSize(14);
            pasteButton.setTypeface(AndroidUtilities.bold());
            pasteButton.setGravity(Gravity.CENTER);
            pasteButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider));
            pasteButton.setText(LocaleController.getString(R.string.WalletPaste));
            pasteButton.setPadding(dp(12), dp(6), dp(12), dp(6));
            pasteButton.setBackground(Theme.createRoundRectDrawable(dp(28), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
            pasteButton.setOnClickListener(v -> pasteFromClipboard());
            actionView.addView(pasteButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        } else {
            pasteButton = null;
        }

        overflowButton = new ImageView(context);
        overflowButton.setScaleType(ImageView.ScaleType.CENTER);
        overflowButton.setColorFilter(new android.graphics.PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider), android.graphics.PorterDuff.Mode.SRC_IN));
        overflowButton.setImageResource(R.drawable.ic_ab_close);
        overflowButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 3));
        overflowButton.setVisibility(View.GONE);
        overflowButton.setFocusable(false);
        overflowButton.setOnClickListener(v -> {
            editText.setText("");
            editText.setSelection(0);
        });
        actionView.addView(overflowButton, LayoutHelper.createFrame(28, 28, Gravity.CENTER));

        updateNumberColor();
        updateActionView();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(dp(256), MeasureSpec.getSize(widthMeasureSpec)), MeasureSpec.getMode(widthMeasureSpec)), heightMeasureSpec);
    }

    private void updateNumberColor() {
        boolean hasText = error || editText.getText().length() > 0;
        numberView.setTextColor(error
                ? Theme.getColor(Theme.key_text_RedBold, resourcesProvider)
                : hasText
                    ? Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider)
                    : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourcesProvider));
    }

    private void updateActionView() {
        boolean hasText = editText.getText().length() > 0;
        boolean hasClip = hasClipboard();
        if (pasteButton != null) pasteButton.setVisibility(hasText ? View.GONE : (hasClip ? View.VISIBLE : View.GONE));
        if (overflowButton != null) overflowButton.setVisibility(hasText && editText.hasFocus() ? View.VISIBLE : View.GONE);
    }

    private boolean hasClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            return clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null
                    && clipboard.getPrimaryClip().getItemCount() > 0
                    && !TextUtils.isEmpty(clipboard.getPrimaryClip().getItemAt(0).getText());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isWordValid() {
        String w = editText.getText().toString().trim().toLowerCase();
        if (w.isEmpty()) {
            return true;
        }
        for (String word : WalletEngine2.getMnemonicWordlist()) {
            if (word.equals(w)) {
                return true;
            }
        }
        return false;
    }

    private void validate() {
        if (error) {
            return;
        }
        if (isWordValid()) {
            return;
        }
        setError(true);
    }

    private void onNextPressed() {
        String current = editText.getText().toString().trim().toLowerCase();
        if (!current.isEmpty() && isWordValid()) {
            clearError();
            goNext();
            return;
        }
        String first = firstMatch(current);
        if (first != null) {
            editText.setText(first);
            editText.setSelection(first.length());
            clearError();
            goNext();
            return;
        }
        setError(true);
    }

    private String firstMatch(String prefix) {
        if (prefix.isEmpty()) {
            return null;
        }
        for (String w : WalletEngine2.getMnemonicWordlist()) {
            if (w.startsWith(prefix)) {
                return w;
            }
        }
        return null;
    }

    private void goNext() {
        hideTooltip();
        if (onNextListener != null) {
            final Runnable listener = onNextListener;
            editText.post(listener);
        }
    }

    public void setError(boolean hasError) {
        if (error == hasError) {
            return;
        }
        error = hasError;
        if (hasError) {
            setBackground(Theme.createRoundRectDrawable(dp(16), Theme.multAlpha(Theme.getColor(Theme.key_fill_RedNormal, resourcesProvider), 0.24f)));
            numberView.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
            editText.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
            if (overflowButton != null) {
                overflowButton.setColorFilter(new android.graphics.PorterDuffColorFilter(Theme.getColor(Theme.key_text_RedBold, resourcesProvider), android.graphics.PorterDuff.Mode.SRC_IN));
            }
            AndroidUtilities.shakeViewSpring(this);
        } else {
            setBackground(Theme.createRoundRectDrawable(dp(16), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider), .50f)));
            editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            if (overflowButton != null) {
                overflowButton.setColorFilter(new android.graphics.PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider), android.graphics.PorterDuff.Mode.SRC_IN));
            }
            updateNumberColor();
            updateActionView();
        }
        if (onErrorClearListener != null) {
            onErrorClearListener.run();
        }
    }

    private void clearError() {
        setError(false);
    }

    private void showTooltipIfNeeded() {
        String text = editText.getText().toString().trim().toLowerCase();
        if (text.isEmpty()) {
            hideTooltip();
            return;
        }
        updateTooltip(text);
    }

    private LinearLayout tooltipRow;
    private ImageView tooltipArrow;
    private final Rect tooltipVisibleFrame = new Rect();
    private final Rect tooltipVisibleCell = new Rect();
    private final int[] tooltipScreenLocation = new int[2];
    private final int[] tooltipWindowLocation = new int[2];
    private final ViewTreeObserver.OnGlobalLayoutListener tooltipLayoutListener = this::updateTooltipPosition;
    private final ViewTreeObserver.OnScrollChangedListener tooltipScrollListener = this::updateTooltipPosition;
    private int tooltipX, tooltipY, tooltipWidth, tooltipHeight;
    private TextView[] tooltipItems = new TextView[3];

    private void updateTooltip(String prefix) {
        if (!editText.hasFocus() || TextUtils.isEmpty(prefix) || prefix.length() < 1 || WalletEngine2.getMnemonicWordlist().length == 0) {
            hideTooltip();
            return;
        }
        ArrayList<String> matches = new ArrayList<>();
        for (String w : WalletEngine2.getMnemonicWordlist()) {
            if (w.startsWith(prefix) && !w.equals(prefix)) {
                matches.add(w);
                if (matches.size() >= 3) break;
            }
        }
        if (matches.isEmpty()) {
            hideTooltip();
            return;
        }

        Context ctx = getContext();
        if (tooltip == null) {
            tooltip = new PopupWindow(ctx);
            // Let touches outside reach the scroll view without dismissing suggestions.
            tooltip.setOutsideTouchable(false);
            tooltip.setFocusable(false);
            // Non-focusable popups otherwise default to appearing over the IME.
            tooltip.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
            tooltip.setBackgroundDrawable(null);
            tooltip.setElevation(dp(8));

            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setClipToPadding(false);

            tooltipArrow = new ImageView(ctx);
            tooltipArrow.setScaleType(ImageView.ScaleType.FIT_XY);
            tooltipArrow.setColorFilter(new android.graphics.PorterDuffColorFilter(0xcc000000, android.graphics.PorterDuff.Mode.SRC_IN));
            tooltipArrow.setImageResource(R.drawable.wallet_tooltip_arrow);
            root.addView(tooltipArrow, LayoutHelper.createLinear(18, 9, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

            tooltipRow = new LinearLayout(ctx);
            tooltipRow.setOrientation(LinearLayout.HORIZONTAL);
            tooltipRow.setGravity(Gravity.CENTER_VERTICAL);
            tooltipRow.setPadding(dp(3), dp(3), dp(3), dp(3));
            tooltipRow.setClipToPadding(false);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xcc000000);
            bg.setCornerRadius(dp(8));
            tooltipRow.setBackground(bg);

            for (int i = 0; i < 3; i++) {
                TextView item = new TextView(ctx);
                item.setTextSize(14);
                item.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                item.setGravity(Gravity.CENTER);
                item.setIncludeFontPadding(false);
                item.setSingleLine(true);
                item.setEllipsize(android.text.TextUtils.TruncateAt.END);
                item.setMaxWidth(dp(140));
                item.setPadding(dp(14), dp(9), dp(14), dp(9));
                item.setVisibility(View.GONE);
                final int slot = i;
                item.setOnClickListener(v -> {
                    if (tooltipItems[slot] == null || TextUtils.isEmpty(tooltipItems[slot].getText())) {
                        return;
                    }
                    String word = tooltipItems[slot].getText().toString();
                    editText.setText(word);
                    editText.setSelection(word.length());
                    hideTooltip();
                    goNext();
                });
                tooltipItems[i] = item;
                tooltipRow.addView(item, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, i == 0 ? 0 : 12, 0, 0, 0));
            }

            // MATCH_PARENT makes the AT_MOST measurement use only the arrow's width.
            root.addView(tooltipRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            tooltip.setContentView(root);
            tooltip.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
            tooltip.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            tooltip.setOnDismissListener(() -> {
                getViewTreeObserver().removeOnGlobalLayoutListener(tooltipLayoutListener);
                getViewTreeObserver().removeOnScrollChangedListener(tooltipScrollListener);
            });
        }

        for (int i = 0; i < 3; i++) {
            TextView item = tooltipItems[i];
            if (i < matches.size()) {
                String full = matches.get(i);
                SpannableString ss = new SpannableString(full);
                int prefixLen = Math.min(prefix.length(), full.length());
                ss.setSpan(new ForegroundColorSpan(0xffbababa), 0, prefixLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ss.setSpan(new ForegroundColorSpan(0xffffffff), prefixLen, full.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                item.setText(ss);
                item.setBackground(i == 0 ? Theme.createRoundRectDrawable(dp(5), 0x1affffff) : null);
                item.setVisibility(View.VISIBLE);
            } else {
                item.setText("");
                item.setBackground(null);
                item.setVisibility(View.GONE);
            }
        }

        positionTooltip();
    }

    private void updateTooltipPosition() {
        if (tooltip != null && tooltip.isShowing()) {
            positionTooltip();
        }
    }

    private void positionTooltip() {
        getWindowVisibleDisplayFrame(tooltipVisibleFrame);
        getLocationOnScreen(tooltipScreenLocation);
        getLocationInWindow(tooltipWindowLocation);
        if (!getLocalVisibleRect(tooltipVisibleCell)) {
            hideTooltip();
            return;
        }
        tooltipVisibleCell.offset(tooltipScreenLocation[0], tooltipScreenLocation[1]);
        if (!Rect.intersects(tooltipVisibleCell, tooltipVisibleFrame)) {
            hideTooltip();
            return;
        }

        LinearLayout content = (LinearLayout) tooltip.getContentView();
        content.measure(MeasureSpec.makeMeasureSpec(Math.min(dp(260), tooltipVisibleFrame.width()), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int width = content.getMeasuredWidth();
        int height = content.getMeasuredHeight();
        int gap = dp(9);
        boolean above = tooltipScreenLocation[1] + getHeight() + gap + height > tooltipVisibleFrame.bottom;
        if (above && tooltipScreenLocation[1] - gap - height < tooltipVisibleFrame.top) {
            // Neither side can fit without covering the cell or leaving the visible window.
            hideTooltip();
            return;
        }
        int arrowIndex = above ? 1 : 0;
        if (content.indexOfChild(tooltipArrow) != arrowIndex) {
            content.removeView(tooltipArrow);
            content.addView(tooltipArrow, arrowIndex);
            tooltipArrow.setRotation(above ? 180 : 0);
        }

        int screenX = Math.max(tooltipVisibleFrame.left,
                Math.min(tooltipScreenLocation[0] + (getWidth() - width) / 2, tooltipVisibleFrame.right - width));
        int screenY = above ? tooltipScreenLocation[1] - gap - height : tooltipScreenLocation[1] + getHeight() + gap;
        // showAtLocation uses window coordinates; the visible frame excludes the keyboard.
        int x = screenX + tooltipWindowLocation[0] - tooltipScreenLocation[0];
        int y = screenY + tooltipWindowLocation[1] - tooltipScreenLocation[1];
        boolean showing = tooltip.isShowing();
        if (showing) {
            if (tooltipX != x || tooltipY != y || tooltipWidth != width || tooltipHeight != height) {
                tooltip.update(x, y, width, height);
            }
        } else {
            tooltip.setWidth(width);
            tooltip.setHeight(height);
            tooltip.showAtLocation(this, Gravity.TOP | Gravity.LEFT, x, y);
            getViewTreeObserver().addOnGlobalLayoutListener(tooltipLayoutListener);
            getViewTreeObserver().addOnScrollChangedListener(tooltipScrollListener);
        }
        tooltipX = x;
        tooltipY = y;
        tooltipWidth = width;
        tooltipHeight = height;
    }

    private void hideTooltip() {
        if (tooltip != null && tooltip.isShowing()) {
            tooltip.dismiss();
        }
    }

    private void pasteFromClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    editText.setText(text.toString().trim());
                    editText.setSelection(editText.getText().length());
                    if (onPasteListener != null) {
                        onPasteListener.run();
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    public String getWord() {
        return editText.getText().toString().trim();
    }

    public void setText(String text) {
        editText.setText(text);
        editText.setSelection(text.length());
        clearError();
        updateNumberColor();
        updateActionView();
        hideTooltip();
    }

    public boolean hasError() {
        return error;
    }

    public void setOnErrorClearListener(Runnable listener) {
        this.onErrorClearListener = listener;
    }

    public boolean isEmpty() {
        return getWord().isEmpty();
    }

    public void setOnTextChangedListener(Runnable listener) {
        this.onTextChangedListener = listener;
    }

    public void setOnPasteListener(Runnable listener) {
        this.onPasteListener = listener;
    }

    public void setOnNextListener(Runnable listener) {
        this.onNextListener = listener;
    }

    public void requestFieldFocus() {
        editText.requestFocus();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        hideTooltip();
    }
}
