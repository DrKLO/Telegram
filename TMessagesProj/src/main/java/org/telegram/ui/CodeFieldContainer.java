package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class CodeFieldContainer extends LinearLayout {
    public final static int TYPE_PASSCODE = 10;

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    float strokeWidth;
    public boolean ignoreOnTextChange;
    public boolean isFocusSuppressed;

    public CodeNumberField[] codeField;

    public CodeFieldContainer(Context context) {
        super(context);
        paint.setStyle(Paint.Style.STROKE);
        setOrientation(HORIZONTAL);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        paint.setStrokeWidth(strokeWidth = AndroidUtilities.dp(1.5f));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof CodeNumberField) {
                CodeNumberField codeField = (CodeNumberField) child;
                if (!isFocusSuppressed) {
                    if (child.isFocused()) {
                        codeField.animateFocusedProgress(1f);
                    } else if (!child.isFocused()) {
                        codeField.animateFocusedProgress(0);
                    }
                }
                float successProgress = codeField.getSuccessProgress();
                int focusClr = ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteInputField), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), codeField.getFocusedProgress());
                int errorClr = ColorUtils.blendARGB(focusClr, Theme.getColor(Theme.key_text_RedBold), codeField.getErrorProgress());
                paint.setColor(ColorUtils.blendARGB(errorClr, Theme.getColor(Theme.key_checkbox), successProgress));
                AndroidUtilities.rectTmp.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
                AndroidUtilities.rectTmp.inset(strokeWidth, strokeWidth);
                if (successProgress != 0) {
                    float offset = -Math.max(0, strokeWidth * (codeField.getSuccessScaleProgress() - 1f));
                    AndroidUtilities.rectTmp.inset(offset, offset);
                }

                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
            }
        }
        super.dispatchDraw(canvas);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child instanceof CodeNumberField) {
            CodeNumberField field = (CodeNumberField) child;
            canvas.save();
            float progress = ((CodeNumberField) child).enterAnimation;
            AndroidUtilities.rectTmp.set(child.getX(), child.getY(), child.getX() + child.getMeasuredWidth(), child.getY() + child.getMeasuredHeight());
            AndroidUtilities.rectTmp.inset(strokeWidth, strokeWidth);
            canvas.clipRect(AndroidUtilities.rectTmp);
            if (field.replaceAnimation) {
                float s = progress * 0.5f + 0.5f;
                child.setAlpha(progress);
                canvas.scale(s, s, field.getX() + field.getMeasuredWidth() / 2f, field.getY() + field.getMeasuredHeight() / 2f);
            } else {
                child.setAlpha(1f);
                canvas.translate(0, child.getMeasuredHeight() * (1f - progress));
            }
            super.drawChild(canvas, child, drawingTime);
            canvas.restore();

            float exitProgress = field.exitAnimation;
            if (exitProgress < 1f) {
                canvas.save();
                float s = (1f - exitProgress) * 0.5f + 0.5f;
                canvas.scale(s, s, field.getX() + field.getMeasuredWidth() / 2f, field.getY() + field.getMeasuredHeight() / 2f);
                bitmapPaint.setAlpha((int) (255 * (1f - exitProgress)));
                canvas.drawBitmap(field.exitBitmap, field.getX(), field.getY(), bitmapPaint);
                canvas.restore();
            }
            return true;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    public void setNumbersCount(int length, int currentType) {
        if (codeField == null || codeField.length != length) {
            if (codeField != null) {
                for (CodeNumberField f : codeField) {
                    removeView(f);
                }
            }
            codeField = new CodeNumberField[length];
            for (int a = 0; a < length; a++) {
                final int num = a;
                codeField[a] = new CodeNumberField(getContext()) {
                    @Override
                    public boolean dispatchKeyEvent(KeyEvent event) {
                        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                            return false;
                        }
                        int keyCode = event.getKeyCode();
                        if (num >= codeField.length) {
                            return false;
                        }
                        if (event.getAction() == KeyEvent.ACTION_UP) {
                            if (keyCode == KeyEvent.KEYCODE_DEL && codeField[num].length() == 1) {
                                codeField[num].startExitAnimation();
                                codeField[num].setText("");
                                return true;
                            } else if (keyCode == KeyEvent.KEYCODE_DEL && codeField[num].length() == 0 && num > 0) {
                                codeField[num - 1].setSelection(codeField[num - 1].length());
                                for (int i = 0; i < num; i++) {
                                    if (i == num - 1) {
                                        codeField[num - 1].requestFocus();
                                    } else {
                                        codeField[i].clearFocus();
                                    }
                                }
                                codeField[num - 1].startExitAnimation();
                                codeField[num - 1].setText("");
                                return true;
                            } else {
                                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                                    String str = Integer.toString(keyCode - KeyEvent.KEYCODE_0);
                                    if (codeField[num].getText() != null && str.equals(codeField[num].getText().toString())) {
                                        if (num >= length - 1) {
                                            processNextPressed();
                                        } else {
                                            codeField[num + 1].requestFocus();
                                        }
                                        return true;
                                    }
                                    if (codeField[num].length() > 0) {
                                        codeField[num].startExitAnimation();
                                    }
                                    codeField[num].setText(str);
                                }
                                return true;
                            }
                        } else {
                            return isFocused();
                        }
                    }
                };

                codeField[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                codeField[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                codeField[a].setMaxLines(1);
                codeField[a].setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                codeField[a].setPadding(0, 0, 0, 0);
                codeField[a].setGravity(Gravity.CENTER);
                if (currentType == 3) {
                    codeField[a].setEnabled(false);
                    codeField[a].setInputType(InputType.TYPE_NULL);
                    codeField[a].setVisibility(GONE);
                } else {
                    codeField[a].setInputType(InputType.TYPE_CLASS_PHONE);
                }
                int width;
                int height;
                int gapSize;
                if (currentType == TYPE_PASSCODE) {
                    width = 42;
                    height = 47;
                    gapSize = 10;
                } else if (currentType == LoginActivity.AUTH_TYPE_MISSED_CALL) {
                    width = 28;
                    height = 34;
                    gapSize = 5;
                } else {
                    width = 34;
                    height = 42;
                    gapSize = 7;
                }
                addView(codeField[a], LayoutHelper.createLinear(width, height, Gravity.CENTER_HORIZONTAL, 0, 0, a != length - 1 ? gapSize: 0, 0));
                codeField[a].addTextChangedListener(new TextWatcher() {

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (ignoreOnTextChange) {
                            return;
                        }
                        int len = s.length();
                        if (len >= 1) {
                            boolean next = false;
                            int n = num;
                            if (len > 1) {
                                String text = s.toString();
                                ignoreOnTextChange = true;
                                for (int a = 0; a < Math.min(length - num, len); a++) {
                                    if (a == 0) {
                                        s.replace(0, len, text.substring(a, a + 1));
                                    } else {
                                        n++;
                                        if (num + a < codeField.length) {
                                            codeField[num + a].setText(text.substring(a, a + 1));
                                        }
                                    }
                                }
                                ignoreOnTextChange = false;
                            }


                            if (n + 1 >= 0 && n + 1 < codeField.length) {
                                codeField[n + 1].setSelection(codeField[n + 1].length());
                                codeField[n + 1].requestFocus();
                            }
                            if ((n == length - 1 || n == length - 2 && len >= 2) && getCode().length() == length) {
                                processNextPressed();
                            }
                        }
                    }
                });
                codeField[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                    if (i == EditorInfo.IME_ACTION_NEXT) {
                        processNextPressed();
                        return true;
                    }
                    return false;
                });
            }
        } else {
            for (int a = 0; a < codeField.length; a++) {
                codeField[a].setText("");
            }
        }
    }

    protected void processNextPressed() {

    }

    public String getCode() {
        if (codeField == null) {
            return "";
        }
        StringBuilder codeBuilder = new StringBuilder();
        for (int a = 0; a < codeField.length; a++) {
            codeBuilder.append(PhoneFormat.stripExceptNumbers(codeField[a].getText().toString()));
        }
        return codeBuilder.toString();
    }

    public void setCode(String savedCode) {
        codeField[0].setText(savedCode);
    }

    public void setText(String code) {
        setText(code, false);
    }

    public void setText(String code, boolean fromPaste) {
        if (codeField == null) {
            return;
        }
        int startFrom = 0;
        if (fromPaste) {
            for (int i = 0; i < codeField.length; i++) {
                if (codeField[i].isFocused()) {
                    startFrom = i;
                    break;
                }
            }
        }
        for (int i = startFrom; i < Math.min(codeField.length, startFrom + code.length()); i++) {
            codeField[i].setText(Character.toString(code.charAt(i - startFrom)));
        }
    }

}
