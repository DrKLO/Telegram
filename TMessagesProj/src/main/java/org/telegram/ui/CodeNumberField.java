package org.telegram.ui;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Editable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextWatcher;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.checkerframework.checker.regex.qual.Regex;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.StaticLayoutEx;

public class CodeNumberField extends EditTextBoldCursor {

    float focusedProgress;
    float enterAnimation = 1f;
    float exitAnimation = 1f;
    boolean replaceAnimation;
    Bitmap exitBitmap;
    Canvas exitCanvas;

    ValueAnimator enterAnimator;
    ValueAnimator exitAnimator;

    ActionMode actionMode;

    public CodeNumberField(Context context) {
        super(context);
        setBackground(null);
        setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        setMovementMethod(null);
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                startEnterAnimation(charSequence.length() != 0);
                hideActionMode();
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

    }

    public void startExitAnimation() {
        if (getMeasuredHeight() == 0 || getMeasuredWidth() == 0 || getLayout() == null) {
            return;
        }
        if (exitBitmap == null || exitBitmap.getHeight() != getMeasuredHeight() || exitBitmap.getWidth() != getMeasuredWidth()) {
            if (exitBitmap != null) {
                exitBitmap.recycle();
            }
            exitBitmap = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(), Bitmap.Config.ARGB_8888);
            exitCanvas = new Canvas(exitBitmap);
        }
        exitBitmap.eraseColor(Color.TRANSPARENT);

        StaticLayout staticLayout = new StaticLayout(getText(), getLayout().getPaint(), (int) Math.ceil(getLayout().getPaint().measureText(String.valueOf(getText()))), Layout.Alignment.ALIGN_NORMAL, getLineSpacingMultiplier(), getLineSpacingExtra(), getIncludeFontPadding());
        exitCanvas.save();
        exitCanvas.translate((getMeasuredWidth() - staticLayout.getWidth()) / 2f, (getMeasuredHeight() - staticLayout.getHeight()) / 2f);
        staticLayout.draw(exitCanvas);
        exitCanvas.restore();

        exitAnimation = 0f;
        exitAnimator = ValueAnimator.ofFloat(exitAnimation, 1f);
        exitAnimator.addUpdateListener(valueAnimator1 -> {
            exitAnimation = (float) valueAnimator1.getAnimatedValue();
            invalidate();
            if (getParent() != null) {
                ((ViewGroup) getParent()).invalidate();
            }
        });
        exitAnimator.setDuration(220);
        exitAnimator.start();
    }

    public void startEnterAnimation(boolean replace) {
        replaceAnimation = replace;
        enterAnimation = 0f;
        enterAnimator = ValueAnimator.ofFloat(enterAnimation, 1f);
        enterAnimator.addUpdateListener(valueAnimator1 -> {
            enterAnimation = (float) valueAnimator1.getAnimatedValue();
            invalidate();
            if (getParent() != null) {
                ((ViewGroup) getParent()).invalidate();
            }
        });
        if (!replaceAnimation) {
            enterAnimator.setInterpolator(new OvershootInterpolator(1.5f));
            enterAnimator.setDuration(350);
        } else {
            enterAnimator.setDuration(220);
        }
        enterAnimator.start();
    }

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        ((ViewGroup) getParent()).invalidate();
        return super.requestFocus(direction, previouslyFocusedRect);
    }


    boolean pressed = false;
    float startX = 0;
    float startY = 0;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            pressed = true;
            startX = event.getX();
            startY = event.getY();
        }
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            CodeFieldContainer codeFieldContainer = null;
            if (getParent() instanceof CodeFieldContainer) {
                codeFieldContainer = (CodeFieldContainer) getParent();
            }
            if (event.getAction() == MotionEvent.ACTION_UP && pressed) {
                if (isFocused() && codeFieldContainer != null) {
                    ClipboardManager clipboard = ContextCompat.getSystemService(getContext(), ClipboardManager.class);
                    if (clipboard == null || clipboard.getPrimaryClipDescription() == null) {
                        return false;
                    }
                    clipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
                    ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                    int i = -1;
                    String text = item.getText().toString();
                    try {
                        i = Integer.parseInt(text);
                    } catch (Exception e) {

                    }

                    if (i > 0) {
                        startActionMode(new ActionMode.Callback() {

                            @Override
                            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                                menu.add(Menu.NONE, android.R.id.paste, 0, android.R.string.paste);
                                return true;
                            }

                            @Override
                            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                                return true;
                            }

                            @Override
                            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                                switch (item.getItemId()) {
                                    case android.R.id.paste:
                                        pasteFromClipboard();
                                        hideActionMode();
                                        return true;
                                }
                                return true;
                            }

                            @Override
                            public void onDestroyActionMode(ActionMode mode) {

                            }
                        });
                    }
                } else {
                    requestFocus();
                }
                setSelection(0);
                AndroidUtilities.showKeyboard(this);
            }
            pressed = false;
        }
        return pressed;
    }

    private void pasteFromClipboard() {
        CodeFieldContainer codeFieldContainer = null;
        if (getParent() instanceof CodeFieldContainer) {
            codeFieldContainer = (CodeFieldContainer) getParent();
        }
        if (codeFieldContainer != null) {
            ClipboardManager clipboard = ContextCompat.getSystemService(getContext(), ClipboardManager.class);
            if (clipboard == null) {
                return;
            }
            clipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            int i = -1;
            String text = item.getText().toString();
            try {
                i = Integer.parseInt(text);
            } catch (Exception e) {

            }
            if (i > 0) {
                codeFieldContainer.setText(text, true);
            }
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (!isFocused()) {
            hideActionMode();
        }
    }
}
