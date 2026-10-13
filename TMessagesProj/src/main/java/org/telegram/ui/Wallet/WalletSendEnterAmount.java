package org.telegram.ui.Wallet;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.DialogInterface;
import android.graphics.Canvas;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.WindowInsetsCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_wallet;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.AlertDialogDecor;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.MessageDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedColor;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.LoadingSpan;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class WalletSendEnterAmount extends UniversalFragment implements NotificationCenter.NotificationCenterDelegate {

    public static class AmountInputView extends FrameLayout {
        public static final long ANIMATION_DURATION_MS = 320;
        public static final float ANIMATION_MOVE_AMPLITUDE = .1f;
        public static final float ANIMATION_START_SCALE = .5f;
        public static final Interpolator ANIMATION_INTERPOLATOR = CubicBezierInterpolator.EASE_OUT_QUINT;

        private final LinearLayout row;
        private final EditTextBoldCursor editText;
        private final WalletDiamondView diamondView;
        private final FrameLayout prefixContainer;
        private final TextView symbolView;
        private final AnimatedTextView currencyView;
        private ValueAnimator prefixAnimator;
        private SpringAnimation symbolWobble, diamondWobble;
        private boolean currencyInitialized, showingCurrency, showingSymbol;
        private int prefixTargetWidth;
        private final Theme.ResourcesProvider resourcesProvider;
        private final boolean walletAmountStyle;
        private final ArrayList<AmountCharacterSpan> characters = new ArrayList<>();
        private ValueAnimator animator;
        private float animationProgress = 1f;
        private float displayWidth;
        private String previousRaw = "";
        private final ArrayList<DisappearingGlyph> disappearing = new ArrayList<>();
        private float emptyFromWidth;
        private float emptyFromOrigin;
        private float animationStartScale = ANIMATION_START_SCALE;
        private String groupingSeparator = ",";
        private String decimalSeparator = ".";
        private boolean settingAmount;

        public AmountInputView(Context context, Theme.ResourcesProvider resourcesProvider) {
            this(context, resourcesProvider, true);
        }

        private AmountInputView(Context context, Theme.ResourcesProvider resourcesProvider, boolean walletAmountStyle) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            this.walletAmountStyle = walletAmountStyle;
            setClipChildren(false);
            setClipToPadding(false);
            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            addView(row, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

            prefixContainer = new FrameLayout(context);
            prefixContainer.setClipChildren(false);
            prefixContainer.setClipToPadding(false);
            // Keep the enlarged texture above its text siblings within this row.
            // Its Z remains local to the row, beneath the Add Comment dialog.
            prefixContainer.setTranslationZ(dp(1));
            row.addView(prefixContainer, LayoutHelper.createLinear(59, 60, Gravity.CENTER_VERTICAL));
            prefixTargetWidth = dp(59);
            // Compose beneath the Add Comment decor dialog and its dim layer.
            diamondView = new WalletDiamondView(context, 60, true);
            diamondView.setInputDrivenRotation();
            prefixContainer.addView(diamondView, LayoutHelper.createFrame(60, 60, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, -5, -2, 0));

            symbolView = new TextView(context);
            symbolView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            symbolView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 44);
            symbolView.setTypeface(AndroidUtilities.getTypeface("fonts/gram.ttf"));
            symbolView.setGravity(Gravity.CENTER_VERTICAL);
            symbolView.setIncludeFontPadding(false);
            symbolView.setSingleLine(true);
            symbolView.setVisibility(INVISIBLE);
            symbolView.setAlpha(0f);
            prefixContainer.addView(symbolView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 60, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            editText = new EditTextBoldCursor(context) {
                private final AnimatedFloat cursorScale = new AnimatedFloat(this, 180, CubicBezierInterpolator.EASE_OUT_QUINT);
                private final AnimatedFloat hintCursor = new AnimatedFloat(this, ANIMATION_DURATION_MS, ANIMATION_INTERPOLATOR);
                private float currentCursorScale = 1f;
                private float hintCursorProgress;

                @Override
                protected void drawCursor(Canvas canvas, GradientDrawable drawable) {
                    if (walletAmountStyle) {
                        drawable.setCornerRadius(dpf2(1.5f));
                        // Fractional glyphs share the integer glyphs' baseline.
                        // Scale the cursor around that baseline as well.
                        int baseline = getLayout().getLineBaseline(0);
                        android.graphics.Rect bounds = drawable.getBounds();
                        int top = Math.round(baseline + (bounds.top - baseline) * currentCursorScale);
                        int bottom = Math.round(baseline + (bounds.bottom - baseline) * currentCursorScale);
                        int left = bounds.left;
                        int width = bounds.width();
                        if (hintCursorProgress > 0f) {
                            // The hint is right-aligned in the visible content area.
                            // Blend into the native caret position as the hint becomes text.
                            float hintLeft = getScrollX() + getWidth() - getCompoundPaddingLeft()
                                    - getCompoundPaddingRight() - getPaint().measureText("0") - width;
                            left = Math.round(left + (hintLeft - left) * hintCursorProgress);
                        }
                        drawable.setBounds(left, top, left + width, bottom);
                    }
                    super.drawCursor(canvas, drawable);
                }

                @Override
                protected void onDraw(Canvas canvas) {
                    if (walletAmountStyle) {
                        CharSequence text = getText();
                        int decimalIndex = Math.max(TextUtils.indexOf(text, '.'), TextUtils.indexOf(text, ','));
                        // Advance even during the hidden phase of the cursor blink.
                        currentCursorScale = cursorScale.set(decimalIndex >= 0 && getSelectionStart() > decimalIndex ? 28f / 44f : 1f);
                        hintCursorProgress = hintCursor.set(length() == 0);
                    }
                    super.onDraw(canvas);
                    if (animationProgress >= 1f) return;
                    Paint paint = getPaint();
                    float size = paint.getTextSize();
                    // onDraw's canvas already accounts for the view's scroll. Match
                    // TextView's layout coordinates without subtracting it again.
                    float origin = getCompoundPaddingLeft();
                    if (length() == 0) {
                        // The empty layout's right-aligned origin differs from the
                        // deleted text's origin. Keep its last visible position.
                        origin += getScrollX() + emptyFromOrigin;
                    } else if (getLayout() != null) {
                        origin += getLayout().getLineLeft(0);
                    }
                    int baseline = getBaseline();
                    for (DisappearingGlyph removed : disappearing) {
                        paint.setTextSize(removed.fraction ? size * 28f / 44f : size);
                        removed.motion.draw(canvas, paint, removed.glyph, origin, baseline);
                    }
                    paint.setTextSize(size);

                }

                @Override
                protected void onSelectionChanged(int start, int end) {
                    super.onSelectionChanged(start, end);
                    if (start != end) finishAnimation();
                }

                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        // Freeze character geometry before Android starts hit testing or
                        // dragging selection handles. Selection itself is entirely native.
                        finishAnimation();
                        if (walletAmountStyle) hintCursor.force(length() == 0);
                    }
                    return super.onTouchEvent(event);
                }
            };
            editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            editText.setHint("0");
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 44);
            editText.setTypeface(AndroidUtilities.getTypeface("fonts/gram.ttf"));
            editText.setIncludeFontPadding(false);
            editText.setSingleLine(true);
            editText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            editText.setBackground(null);
            editText.setPadding(dp(3), 0, dp(3), 0);
            editText.setMinWidth(dp(24));
            editText.setHorizontallyScrolling(true);
            editText.setCursorWidth(walletAmountStyle ? 3 : 2);
            editText.setCursorSize(dp(48));
            int cursorColor = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
            editText.setCursorColor(cursorColor);
            editText.setHandlesColor(cursorColor);
            editText.setHighlightColor(Theme.multAlpha(cursorColor, .3f));
            editText.setAllowDrawCursor(true);
            editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            editText.setKeyListener(DigitsKeyListener.getInstance("0123456789.,"));
            editText.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_DONE);
            row.addView(editText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 60, Gravity.CENTER_VERTICAL));

            currencyView = new AnimatedTextView(context, false, true, false, true, true) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    // Keep the drawable's layout width stable as the prefix resizes;
                    // AnimatedTextView otherwise resets its animation on every measure.
                    super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.AT_MOST), heightMeasureSpec);
                    setMeasuredDimension(resolveSize((int) Math.ceil(getDrawable().getCurrentWidth()), widthMeasureSpec), getMeasuredHeight());
                }

                @Override
                public int getBaseline() {
                    return Math.round((getHeight() - getDrawable().getHeight()) / 2f)
                            - getPaint().getFontMetricsInt().ascent;
                }
            };
            currencyView.setAnimationProperties(.35f, 0, 420, 3.5f, CubicBezierInterpolator.EASE_OUT_QUINT);
            currencyView.setScaleProperty(.2f);
            currencyView.setAllowCancel(true);
            currencyView.getDrawable().setOnWidthUpdatedListener(currencyView::requestLayout);
            currencyView.setTextColor(Theme.getColor(walletAmountStyle ? Theme.key_featuredStickers_addButton : Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            currencyView.setTextSize(dp(28));
            currencyView.setTypeface(AndroidUtilities.getTypeface("fonts/gram.ttf"));
            currencyView.setGravity(Gravity.LEFT);
            currencyView.getDrawable().setIncludeFontPadding(false);
            currencyView.setText(getString(R.string.GramCurrency), false);
            row.addView(currencyView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 60, Gravity.CENTER_VERTICAL, 4, 0, 0, 0));
            setOnClickListener(v -> {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            });

            editText.addTextChangedListener(new TextWatcher() {
                private boolean appendDecimal;
                private boolean addingDecimal;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    if (s.length() > 0 && count == s.length() && after == 0 && editText.getLayout() != null) {
                        emptyFromOrigin = editText.getLayout().getLineLeft(0) - editText.getScrollX();
                    }
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    appendDecimal = count > 0 && TextUtils.equals(s, "0");
                    if (!settingAmount && !addingDecimal && (before != 0 || count != 0)) {
                        diamondView.onAmountEdited(count > 0 && count >= before);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (appendDecimal) {
                        appendDecimal = false;
                        addingDecimal = true;
                        try {
                            s.append('.');
                        } finally {
                            addingDecimal = false;
                        }
                        editText.setSelection(s.length());
                        return;
                    }
                    updateCharacters(s);
                }
            });
        }

        public EditTextBoldCursor getEditText() {
            return editText;
        }

        public void setAmountText(String text) {
            settingAmount = true;
            try {
                editText.setText(text);
            } finally {
                settingAmount = false;
            }
        }

        public WalletDiamondView getDiamondView() {
            return diamondView;
        }

        public AnimatedTextView getCurrencyView() {
            return currencyView;
        }

        public void setIntroProgress(float progress, float revealProgress, boolean finished) {
            final float remaining = 1f - progress;
            final float centerX = getWidth() / 2f - row.getLeft();
            prefixContainer.setTranslationX((centerX - prefixContainer.getLeft()
                    - diamondView.getLeft() - diamondView.getWidth() / 2f) * remaining);
            prefixContainer.setTranslationY((getHeight() / 2f - row.getTop()
                    - prefixContainer.getTop() - diamondView.getTop() - diamondView.getHeight() / 2f) * remaining
                    - dp(36) * (float) Math.sin(Math.PI * progress));
            final float textRight = currencyView.getVisibility() == GONE ? editText.getRight() : currencyView.getRight();
            final float textOffset = (centerX - (editText.getLeft() + textRight) / 2f) * (1f - revealProgress);
            editText.setTranslationX(textOffset);
            currencyView.setTranslationX(textOffset);
            editText.setAlpha(revealProgress);
            currencyView.setAlpha(revealProgress);
            diamondView.setEnabled(finished && !showingCurrency);
            diamondView.setIntroProgress(progress);
        }

        public void setCurrency(GramWallet wallet, boolean inCurrency) {
            final boolean animated = currencyInitialized && isAttachedToWindow() && isShown();
            if (walletAmountStyle) {
                int color = Theme.getColor(inCurrency ? Theme.key_wallet_inputCurrency : Theme.key_featuredStickers_addButton, resourcesProvider);
                symbolView.setTextColor(color);
                currencyView.setTextColor(color, animated);
                editText.setCursorColor(color);
                editText.setHandlesColor(color);
                editText.setHighlightColor(Theme.multAlpha(color, .3f));
            }
            final TL_wallet.currencyRate rate = wallet.currencies.getSelectedCurrencyRate();
            final String code = wallet.currencies.getCurrency();
            final boolean usd = TextUtils.equals(code, "USD");
            final String symbol = rate != null && !TextUtils.isEmpty(rate.symbol) ? rate.symbol : usd ? "$" : code;
            final boolean symbolLeft = inCurrency && (rate != null ? rate.symbolLeft : usd);
            final boolean spaceBetween = rate != null && rate.spaceBetween;

            final boolean prefixChanged = showingCurrency != inCurrency || showingSymbol != symbolLeft
                    || (symbolLeft && !TextUtils.equals(symbolView.getText(), symbol));
            if (symbolLeft) {
                symbolView.setText(symbol);
                symbolView.getLayoutParams().width = (int) Math.ceil(symbolView.getPaint().measureText(symbol));
                symbolView.requestLayout();
            }
            showingCurrency = inCurrency;
            showingSymbol = symbolLeft;
            prefixTargetWidth = !inCurrency ? dp(59) : symbolLeft
                    ? (int) Math.ceil(symbolView.getPaint().measureText(symbol)) + (spaceBetween ? dp(8) : 0) : 0;
            if (prefixChanged || !currencyInitialized) animatePrefix(animated && prefixChanged);
            currencyInitialized = true;
            currencyView.setText(!inCurrency ? getString(R.string.GramCurrency) : symbolLeft ? code : symbol, animated);
            final LinearLayout.LayoutParams currencyParams = (LinearLayout.LayoutParams) currencyView.getLayoutParams();
            currencyParams.leftMargin = !inCurrency || symbolLeft || spaceBetween ? dp(4) : 0;
            currencyView.setLayoutParams(currencyParams);

            String grouping = inCurrency && rate != null
                    ? (rate.thousandsSeparator == null ? "" : rate.thousandsSeparator) : ",";
            String decimal = inCurrency && rate != null && !TextUtils.isEmpty(rate.decimalSeparator)
                    ? rate.decimalSeparator : ".";
            if (!TextUtils.equals(groupingSeparator, grouping) || !TextUtils.equals(decimalSeparator, decimal)) {
                finishAnimation();
                groupingSeparator = grouping;
                decimalSeparator = decimal;
                updateCharacters(editText.getText());
                finishAnimation();
            }
        }

        public void setScaleProperty(float amplitude) {
            animationStartScale = 1f - Math.max(0f, Math.min(1f, amplitude));
        }

        private void animatePrefix(boolean animated) {
            if (prefixAnimator != null) {
                prefixAnimator.removeAllListeners();
                prefixAnimator.cancel();
                prefixAnimator = null;
            }
            if (symbolWobble != null) symbolWobble.cancel();
            if (diamondWobble != null) diamondWobble.cancel();
            if (!animated) {
                finishPrefixTransition();
                return;
            }
            final int fromWidth = prefixContainer.getLayoutParams().width;
            final float fromDiamond = diamondView.getAlpha(), fromSymbol = symbolView.getAlpha();
            final float toDiamond = showingCurrency ? 0f : 1f, toSymbol = showingSymbol ? 1f : 0f;
            if (fromDiamond > 0f || toDiamond > 0f) diamondView.setVisibility(VISIBLE);
            if (fromSymbol > 0f || toSymbol > 0f) symbolView.setVisibility(VISIBLE);
            diamondView.setEnabled(!showingCurrency);
            diamondWobble = wobbleDiamond(showingCurrency ? 24f : -24f);
            symbolWobble = wobblePrefix(symbolView, showingCurrency ? 18f : -18f);
            prefixAnimator = ValueAnimator.ofFloat(0f, 1f);
            prefixAnimator.setDuration(320);
            prefixAnimator.setInterpolator(new LinearInterpolator());
            prefixAnimator.addUpdateListener(a -> {
                float progress = (float) a.getAnimatedValue();
                float move = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(progress);
                // Finish the fade quickly while the sign continues to swing and settle.
                float fade = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(Math.min(1f, progress * 320f / 120f));
                diamondView.setAlpha(fromDiamond + (toDiamond - fromDiamond) * fade);
                symbolView.setAlpha(fromSymbol + (toSymbol - fromSymbol) * fade);
                prefixContainer.getLayoutParams().width = Math.round(fromWidth + (prefixTargetWidth - fromWidth) * move);
                prefixContainer.requestLayout();
            });
            prefixAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    prefixAnimator = null;
                    diamondView.setVisibility(showingCurrency ? INVISIBLE : VISIBLE);
                    symbolView.setVisibility(showingSymbol ? VISIBLE : INVISIBLE);
                }
            });
            prefixAnimator.start();
        }

        private SpringAnimation wobblePrefix(View view, float angle) {
            view.setPivotX(view.getLayoutParams().width / 2f);
            view.setPivotY(0f);
            if (view.getAlpha() < .01f) view.setRotation(angle);
            SpringAnimation spring = new SpringAnimation(view, DynamicAnimation.ROTATION);
            spring.setSpring(new SpringForce(0f).setDampingRatio(.3f).setStiffness(180f));
            spring.setStartVelocity(-angle * 10f);
            spring.start();
            return spring;
        }

        private SpringAnimation wobbleDiamond(float angle) {
            if (diamondView.getAlpha() < .01f) diamondView.setConversionWobble(angle);
            SpringAnimation spring = new SpringAnimation(new FloatValueHolder(diamondView.getConversionWobble()));
            spring.setSpring(new SpringForce(0f).setDampingRatio(.3f).setStiffness(180f));
            spring.setMinimumVisibleChange(.1f);
            spring.setStartVelocity(-angle * 10f);
            spring.addUpdateListener((animation, value, velocity) -> diamondView.setConversionWobble(value));
            spring.start();
            return spring;
        }

        private void finishPrefixTransition() {
            diamondView.setAlpha(showingCurrency ? 0f : 1f);
            symbolView.setAlpha(showingSymbol ? 1f : 0f);
            diamondView.setConversionWobble(0f);
            symbolView.setRotation(0f);
            diamondView.setEnabled(!showingCurrency);
            diamondView.setVisibility(showingCurrency ? INVISIBLE : VISIBLE);
            symbolView.setVisibility(showingSymbol ? VISIBLE : INVISIBLE);
            prefixContainer.getLayoutParams().width = prefixTargetWidth;
            prefixContainer.requestLayout();
        }

        private void updateCharacters(Editable text) {
            String raw = text.toString();
            int start = 0;
            while (start < Math.min(raw.length(), previousRaw.length())
                    && raw.charAt(start) == previousRaw.charAt(start)) start++;
            int suffix = 0;
            while (suffix < Math.min(raw.length(), previousRaw.length()) - start
                    && raw.charAt(raw.length() - suffix - 1) == previousRaw.charAt(previousRaw.length() - suffix - 1)) suffix++;
            int removed = previousRaw.length() - start - suffix;
            int inserted = raw.length() - start - suffix;

            ArrayList<AmountCharacterSpan> previous = new ArrayList<>(characters);
            GlyphMotion previousHint = previous.isEmpty() && previousRaw.isEmpty() && raw.startsWith("0")
                    ? new GlyphMotion(0, 1, 1, 0)
                    : null;
            ArrayList<GlyphMotion> oldGlyphs = new ArrayList<>();
            ArrayList<GlyphMotion> oldCommas = new ArrayList<>();
            ArrayList<String> oldGroupingGlyphs = new ArrayList<>();
            ArrayList<Integer> oldAdvances = new ArrayList<>();
            float previousWidth = 0;
            for (AmountCharacterSpan span : previous) {
                oldGlyphs.add(span.glyphMotion.snapshot());
                oldAdvances.add(span.advance());
                previousWidth += span.advance();
                if (span.commaMotion != null) {
                    oldCommas.add(span.commaMotion.snapshot());
                    oldGroupingGlyphs.add(span.groupingGlyph);
                }
            }
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            for (AmountCharacterSpan span : text.getSpans(0, text.length(), AmountCharacterSpan.class)) {
                text.removeSpan(span);
            }
            characters.clear();
            disappearing.clear();
            int point = Math.max(raw.indexOf('.'), raw.indexOf(','));
            int integerEnd = point < 0 ? raw.length() : point;
            int commaCount = groupingSeparator.isEmpty() ? 0 : Math.max(0, (integerEnd - 1) / 3);
            int commaIndex = 0;
            displayWidth = 0;
            boolean animate = isAttachedToWindow() && editText.getSelectionStart() == editText.getSelectionEnd();
            boolean[] retainedGlyphs = new boolean[previous.size()];
            boolean[] retainedCommas = new boolean[oldCommas.size()];
            int retainedWidth = 0;
            for (int i = 0; i < raw.length(); i++) {
                int oldIndex = i < start ? i : i >= start + inserted ? i - inserted + removed : -1;
                boolean retained = oldIndex >= 0 && oldIndex < previous.size();
                if (retained) retainedGlyphs[oldIndex] = true;
                boolean comma = !groupingSeparator.isEmpty() && i > 0 && i < integerEnd && (integerEnd - i) % 3 == 0;
                AmountCharacterSpan span = new AmountCharacterSpan(i == point ? decimalSeparator : String.valueOf(raw.charAt(i)),
                        comma, point >= 0 && i >= point);
                float commaWidth = comma ? editText.getPaint().measureText(groupingSeparator) : 0;
                span.toAdvance = (int) Math.ceil(span.fullWidth(editText.getPaint()));
                span.fromAdvance = retained ? oldAdvances.get(oldIndex) : 0;
                if (i == 0 && previousHint != null) {
                    span.fromAdvance = span.toAdvance;
                }
                if (walletAmountStyle && i == 0 && previousRaw.isEmpty() && previous.isEmpty()) {
                    span.fromAdvance = (int) Math.ceil(editText.getPaint().measureText("0"));
                }
                retainedWidth += span.fromAdvance;
                span.glyphMotion = new GlyphMotion(retained ? oldGlyphs.get(oldIndex)
                        : i == 0 ? previousHint : null, displayWidth + commaWidth);
                if (comma) {
                    int oldCommaIndex = oldCommas.size() - commaCount + commaIndex++;
                    if (oldCommaIndex >= 0) retainedCommas[oldCommaIndex] = true;
                    span.commaMotion = new GlyphMotion(oldCommaIndex >= 0 ? oldCommas.get(oldCommaIndex) : null, displayWidth);
                }
                characters.add(span);
                displayWidth += span.toAdvance;
                text.setSpan(span, i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (!characters.isEmpty()) {
                characters.get(Math.min(start, characters.size() - 1)).fromAdvance += Math.max(0, Math.round(previousWidth) - retainedWidth);
            }
            if (animate) {
                for (int i = 0; i < previous.size(); i++) {
                    if (!retainedGlyphs[i] && !(raw.isEmpty() && i == 0 && previous.get(i).glyph.equals("0"))) {
                        AmountCharacterSpan old = previous.get(i);
                        disappearing.add(new DisappearingGlyph(old.glyph, old.fraction, oldGlyphs.get(i)));
                    }
                }
                for (int i = 0; i < oldCommas.size(); i++) {
                    if (!retainedCommas[i]) disappearing.add(new DisappearingGlyph(oldGroupingGlyphs.get(i), false, oldCommas.get(i)));
                }
                if (raw.isEmpty() && !previous.isEmpty()) {
                    emptyFromWidth = previousWidth;
                }
            }
            previousRaw = raw;
            animationProgress = animate ? 0f : 1f;
            if (animate) {
                animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setDuration(ANIMATION_DURATION_MS);
                animator.setInterpolator(new LinearInterpolator());
                animator.addUpdateListener(animation -> {
                    animationProgress = (float) animation.getAnimatedValue();
                    refreshCharacterLayout();
                });
                animator.start();
            }
            refreshCharacterLayout();
        }

        private void refreshCharacterLayout() {
            Editable text = editText.getText();
            if (walletAmountStyle) {
                // TextView measures the hint even when text hides it, so remove it
                // while editing to let narrow digits use their actual width.
                CharSequence hint = text.length() == 0 ? "0" : null;
                if (!TextUtils.equals(editText.getHint(), hint)) editText.setHint(hint);
            }
            float currentOffset = 0;
            for (int i = 0; i < characters.size(); i++) {
                AmountCharacterSpan span = characters.get(i);
                span.currentOffset = currentOffset;
                currentOffset += span.advance();
                if (text.getSpanStart(span) >= 0) {
                    text.setSpan(span, text.getSpanStart(span), text.getSpanEnd(span), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            if (text.length() == 0 && animationProgress < 1f) {
                float hintWidth = editText.getPaint().measureText("0");
                editText.setMinWidth(Math.max(dp(24), Math.round(emptyFromWidth + (hintWidth - emptyFromWidth) * movementProgress()) + editText.getPaddingLeft() + editText.getPaddingRight()));
            } else {
                editText.setMinWidth(walletAmountStyle && text.length() > 0
                        ? editText.getPaddingLeft() + editText.getPaddingRight() + dp(3) : dp(24));
            }
            editText.requestLayout();
            editText.invalidate();
        }

        private void finishAnimation() {
            if (animator != null) {
                animator.cancel();
                animator = null;
                animationProgress = 1f;
                refreshCharacterLayout();
            }
        }

        private float movementProgress() {
            return ANIMATION_INTERPOLATOR.getInterpolation(animationProgress);
        }

        private class DisappearingGlyph {
            final String glyph;
            final boolean fraction;
            final GlyphMotion motion;

            DisappearingGlyph(String glyph, boolean fraction, GlyphMotion previous) {
                this.glyph = glyph;
                this.fraction = fraction;
                // Fade at the last visible position instead of sliding into retained digits.
                motion = new GlyphMotion(previous, previous.fromX, true);
            }
        }

        private class GlyphMotion {
            private final float fromX, toX, fromAlpha, fromScale, fromY;
            private final boolean exiting;
            private final long appearanceStart;

            GlyphMotion(GlyphMotion previous, float toX) {
                this(previous, toX, false);
            }

            GlyphMotion(GlyphMotion previous, float toX, boolean exiting) {
                this.exiting = exiting;
                this.toX = toX;
                fromX = previous == null ? toX : previous.fromX;
                // Retained glyphs keep their original appearance timeline while
                // their horizontal position retargets with the amount layout.
                appearanceStart = previous == null || exiting ? SystemClock.uptimeMillis() : previous.appearanceStart;
                float t = previous != null && exiting ? previous.appearanceProgress() : 0f;
                fromAlpha = previous == null ? 0f : previous.fromAlpha + (1f - previous.fromAlpha) * t;
                fromScale = previous == null ? animationStartScale : previous.fromScale + (1f - previous.fromScale) * t;
                fromY = previous == null ? -dpf2(44) * ANIMATION_MOVE_AMPLITUDE : previous.fromY * (1f - t);
            }

            private GlyphMotion(float x, float alpha, float scale, float y) {
                exiting = false;
                fromX = toX = x;
                fromAlpha = alpha;
                fromScale = scale;
                fromY = y;
                appearanceStart = SystemClock.uptimeMillis();
            }

            private GlyphMotion(float x, GlyphMotion previous) {
                exiting = previous.exiting;
                fromX = toX = x;
                fromAlpha = previous.fromAlpha;
                fromScale = previous.fromScale;
                fromY = previous.fromY;
                appearanceStart = previous.appearanceStart;
            }

            private float appearanceProgress() {
                if (animationProgress >= 1f) return 1f;
                float progress = Math.min(1f, (SystemClock.uptimeMillis() - appearanceStart) / (float) ANIMATION_DURATION_MS);
                return ANIMATION_INTERPOLATOR.getInterpolation(progress);
            }

            private float x() {
                return fromX + (toX - fromX) * movementProgress();
            }

            GlyphMotion snapshot() {
                if (animationProgress >= 1f) {
                    return new GlyphMotion(x(), 1f, 1f, 0f);
                }
                return new GlyphMotion(x(), this);
            }

            void draw(Canvas canvas, Paint paint, String glyph, float origin, int baseline) {
                float t = appearanceProgress();
                float opacity = fromAlpha + ((exiting ? 0f : 1f) - fromAlpha) * t;
                if (opacity <= 0) return;
                int alpha = paint.getAlpha();
                float width = paint.measureText(glyph);
                canvas.save();
                canvas.translate(origin + x(), baseline + fromY + ((exiting ? dpf2(44) * ANIMATION_MOVE_AMPLITUDE : 0f) - fromY) * t);
                float scale = fromScale + ((exiting ? animationStartScale : 1f) - fromScale) * t;
                canvas.scale(scale, scale, width / 2f, (paint.ascent() + paint.descent()) / 2f);
                paint.setAlpha(Math.round(alpha * opacity));
                canvas.drawText(glyph, 0, 0, paint);
                paint.setAlpha(alpha);
                canvas.restore();
            }
        }

        private class AmountCharacterSpan extends ReplacementSpan {
            private final String glyph, groupingGlyph;
            private final boolean comma, fraction;
            private float currentOffset;
            private int fromAdvance, toAdvance;
            private GlyphMotion glyphMotion, commaMotion;

            AmountCharacterSpan(String glyph, boolean comma, boolean fraction) {
                this.glyph = glyph;
                this.groupingGlyph = comma ? groupingSeparator : "";
                this.comma = comma;
                this.fraction = fraction;
            }

            private int advance() {
                return Math.round(fromAdvance + (toAdvance - fromAdvance) * movementProgress());
            }

            private float fullWidth(Paint paint) {
                float size = paint.getTextSize();
                if (fraction) paint.setTextSize(size * 28f / 44f);
                float width = paint.measureText(glyph) + paint.measureText(groupingGlyph);
                paint.setTextSize(size);
                return width;
            }

            @Override
            public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
                if (fm != null) paint.getFontMetricsInt(fm);
                return advance();
            }

            @Override
            public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x,
                             int top, int y, int bottom, @NonNull Paint paint) {
                float size = paint.getTextSize();
                if (fraction) paint.setTextSize(size * 28f / 44f);
                float origin = x - currentOffset;
                if (commaMotion != null) commaMotion.draw(canvas, paint, groupingGlyph, origin, y);
                glyphMotion.draw(canvas, paint, glyph, origin, y);
                paint.setTextSize(size);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            row.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
            float scale = Math.min(1f, getMeasuredWidth() / (float) Math.max(1, row.getMeasuredWidth()));
            row.setPivotX(row.getMeasuredWidth() / 2f);
            row.setPivotY(row.getMeasuredHeight() / 2f);
            row.setScaleX(scale);
            row.setScaleY(scale);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            int baseline = editText.getTop() + editText.getBaseline();
            if (symbolView.getVisibility() == VISIBLE) {
                symbolView.setTranslationY(baseline - prefixContainer.getTop() - symbolView.getTop() - symbolView.getBaseline());
            }
            currencyView.setTranslationY(baseline - currencyView.getTop() - currencyView.getBaseline());
        }

        @Override
        protected void onDetachedFromWindow() {
            finishAnimation();
            animatePrefix(false);
            super.onDetachedFromWindow();
        }
    }

    private TL_wallet.walletUserAddress recipient;
    private TLRPC.User recipientUser;
    private String recipientAddress;
    private String recipientDomain;
    private boolean loadingRecipient;

    public String getAddress() {
        // Resolution supplies user metadata; preserve the destination and its bounce flag.
        return recipientAddress;
    }
    public byte[] getPublicKey() {
        if (recipient == null) return null;
        if (recipient.public_key == null) return null;
        for (int i = 0; i < recipient.public_key.length; ++i)
            if (recipient.public_key[i] != 0)
                return recipient.public_key;
        return null;
    }

    private long initAmount = 0;
    private WalletRecipientView addressInfoLayout;
    private LinearLayout center;
    private FrameLayout centerBottomContainer;
    private LinearLayout centerBottom;
    private AmountInputView amountInput;
    private EditTextBoldCursor amountEdit;
    private boolean pendingAmountKeyboard;
    private final Runnable showAmountKeyboard = this::showPendingAmountKeyboard;
    private final ViewTreeObserver.OnWindowFocusChangeListener amountWindowFocusListener = hasFocus -> {
        if (hasFocus && pendingAmountKeyboard && amountEdit != null) {
            amountEdit.removeCallbacks(showAmountKeyboard);
            amountEdit.post(showAmountKeyboard);
        }
    };

    private void showPendingAmountKeyboard() {
        if (!pendingAmountKeyboard || amountEdit == null
                || !amountEdit.isAttachedToWindow() || !amountEdit.hasWindowFocus()) return;
        amountEdit.requestFocus();
        if (AndroidUtilities.showKeyboard(amountEdit)) pendingAmountKeyboard = false;
    }
    private CharSequence currencyExchangeIcon;
    private CharSequence gramAmountIcon;
    private AnimatedTextView usdView;
    private TextView depositFundsView;
    private TextView commentView;
    private LinkSpanDrawable.LinksTextView insufficientFundsView;
    private TextView balanceView;
    private SpannableString feeArrow;
    private TextView feeView;
    private SpannableString feeViewLoading;
    private boolean feeVisible;
    private boolean hasEnteredAmount;
    private float feeVisibilityProgress;
    private ValueAnimator feeVisibilityAnimator;
    private LinearLayout bottomContainer;
    private SpringAnimation introAnimator;
    private float introProgress;
    private float introRevealProgress;
    private ButtonWithCounterView sendButton;
    private long fee = -1;
    private boolean openChat;

    private String comment;
    private boolean comment_encrypted = true;

    private int bottomInset;
    private int keyboardInset;
    private boolean inputInCurrency;

    public WalletSendEnterAmount(TLRPC.User user) {
        this.recipientUser = user;
        this.recipientAddress = null;

        loadingRecipient = true;
        GramWallet.getInstance(currentAccount).resolveWallet(user, true, (w, error) -> {
            loadingRecipient = false;
            if (TextUtils.equals(error, "WALLET_USER_UNAVAILABLE")) {
                // server shows alert already
                finishFragment();
                return;
            }
            recipient = w;
            if (recipient != null) {
                recipientAddress = recipient.address;
            }
            if (listView != null) {
                listView.adapter.update(true);
            }
            updateAmount();
            loadFee();
            updateAddressInfo();
        });
    }

    public WalletSendEnterAmount(String address) {
        this.recipientUser = null;
        this.recipientAddress = address;
        loadFee();

        loadingRecipient = true;
        GramWallet.getInstance(currentAccount).resolveWallet(address, (w, error) -> {
            loadingRecipient = false;
            recipient = w;
            if (w != null && w.user_id != 0) {
                recipientUser = MessagesController.getInstance(currentAccount).getUser(w.user_id);
            }
            if (listView != null) {
                listView.adapter.update(true);
            }
            updateAmount();
            updateAddressInfo();
        });
    }

    public WalletSendEnterAmount setRecipientDomain(String domain) {
        recipientDomain = domain;
        if (actionBar != null) actionBar.setTitle(createTitle());
        return this;
    }

    public WalletSendEnterAmount setAmount(long nanos) {
        initAmount = nanos;
        return this;
    }

    public WalletSendEnterAmount setComment(String comment, boolean encrypted) {
        this.comment = comment;
        this.comment_encrypted = encrypted;
        return this;
    }

    public WalletSendEnterAmount setAllowOpenChat() {
        this.openChat = true;
        return this;
    }

    @Override
    protected CharSequence getTitle() {
        return createTitle();
    }

    @Override
    public View createView(Context context) {
        fragmentView = super.createView(context);
        fragmentView.setFocusableInTouchMode(true);
        listView.setVisibility(View.GONE);
        fragmentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        actionBar.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        actionBar.setCastShadows(false);

        final ActionBarMenu menu = actionBar.createMenu();
        final ActionBarMenuItem otherItem = menu.addItem(1, R.drawable.ic_ab_other);
        otherItem.setContentDescription(getString(R.string.AccDescrMoreOptions));
        otherItem.addSubItem(2, R.drawable.msg_addbot, getString(R.string.WalletDepositFunds));
        otherItem.addSubItem(3, R.drawable.menu_comments, getString(R.string.WalletAddComment));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 2) {
                    WalletActivity.openWalletQRAlert(getContext(), currentAccount, getResourceProvider());
                } else if (id == 3) {
                    openAddCommentAlert();
                }
            }
        });

        final FrameLayout content = (FrameLayout) fragmentView;
        // The swipe-enlarged diamond extends above the center group's bounds.
        content.setClipChildren(false);
        content.setClipToPadding(false);

        addressInfoLayout = new WalletRecipientView(context, resourceProvider,
            () -> presentFragment(ChatActivity.of(recipientUser.id)),
            () -> BulletinFactory.of(this)
                .createSimpleBulletin(R.raw.copy, getString(R.string.WalletAddressCopiedBulletin))
                .show());
        content.addView(addressInfoLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 64, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 16, 5, 16, 0));

        updateAddressInfo();

        center = new LinearLayout(context) {
            private final ViewTreeObserver.OnPreDrawListener fitContent = () -> {
                updateCenterGeometry();
                if (introAnimator != null || introProgress < 1f) updateIntroGeometry();
                return true;
            };

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                // Keep the full height of comments and hints before scaling the group.
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                getViewTreeObserver().addOnPreDrawListener(fitContent);
                getViewTreeObserver().addOnWindowFocusChangeListener(amountWindowFocusListener);
            }

            @Override
            protected void onDetachedFromWindow() {
                getViewTreeObserver().removeOnPreDrawListener(fitContent);
                getViewTreeObserver().removeOnWindowFocusChangeListener(amountWindowFocusListener);
                super.onDetachedFromWindow();
            }
        };
        center.setOrientation(LinearLayout.VERTICAL);
        center.setClipChildren(false);
        center.setClipToPadding(false);
        content.addView(center, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        amountInput = new AmountInputView(context, getResourceProvider(), true);
        if (!amountInput.getDiamondView().canAnimateIntro()) {
            introProgress = introRevealProgress = 1f;
        }
        center.addView(amountInput, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 64, Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                16, 0, 16, 0));
        amountEdit = amountInput.getEditText();
        amountEdit.setFilters(new InputFilter[]{(source, start, end, dest, dstart, dend) -> {
            String value = dest.subSequence(0, dstart).toString()
                    + source.subSequence(start, end) + dest.subSequence(dend, dest.length());
            BigDecimal maxAmount = BigDecimal.valueOf(Long.MAX_VALUE, 9);
            int fractionDigits = 9;
            if (inputInCurrency) {
                final BigDecimal currencyPerGram = getCurrencyPerGram();
                maxAmount = maxAmount.multiply(currencyPerGram);
                fractionDigits = getCurrencyAmountFractionDigits(currencyPerGram);
            }
            final int decimalIndex = Math.max(value.indexOf('.'), value.indexOf(','));
            if (decimalIndex >= 0 && value.length() - decimalIndex - 1 > fractionDigits) {
                return dest.subSequence(dstart, dend);
            }
            if (parseAmount(value).compareTo(maxAmount) > 0) {
                return dest.subSequence(dstart, dend);
            }
            if (start == end) {
                return null;
            }
            boolean hasDecimal = false;
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '.' || c == ',') {
                    if (hasDecimal) {
                        return dest.subSequence(dstart, dend);
                    }
                    hasDecimal = true;
                }
            }
            if (dstart == 0 && (value.startsWith(".") || value.startsWith(","))) {
                return "0" + source.subSequence(start, end);
            }
            return value.startsWith("00") ? dest.subSequence(dstart, dend) : null;
        }});
        amountEdit.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_DONE);
        amountEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE && sendButton.isEnabled()) {
                sendButton.performClick();
                return true;
            }
            return false;
        });
        amountEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateAmount();
            }
        });
        if (initAmount > 0) {
            amountInput.setAmountText(BigDecimal.valueOf(initAmount, 9).stripTrailingZeros().toPlainString());
            amountEdit.setSelection(amountEdit.length());
        }
        currencyExchangeIcon = new SpannableString("⇅");
        final ColoredImageSpan currencyExchangeIconSpan = new ColoredImageSpan(R.drawable.wallet_currency_exchange);
        currencyExchangeIconSpan.setAlpha(0.72f);
        ((SpannableString) currencyExchangeIcon).setSpan(currencyExchangeIconSpan, 0, currencyExchangeIcon.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        gramAmountIcon = new SpannableString("G");
        final ColoredImageSpan gramIconSpan = new ColoredImageSpan(R.drawable.wallet_gram_small);
        gramIconSpan.recolorDrawable = false;
        ((SpannableString) gramAmountIcon).setSpan(gramIconSpan, 0, gramAmountIcon.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        usdView = new AnimatedTextView(context, false, true, true, true, true);
        usdView.setAnimationProperties(.35f, 0, 320, 3.5f, CubicBezierInterpolator.EASE_OUT_QUINT);
        usdView.setScaleProperty(0.25f);
        usdView.setText(getCurrencyPreviewText(GramWallet.getInstance(currentAccount), 0));
        usdView.setTextColor(Theme.blendOver(
                getThemedColor(Theme.key_windowBackgroundWhite),
                getThemedColor(Theme.key_windowBackgroundWhiteGrayText2)
        ));
        usdView.setTextSize(dp(14));
        usdView.setTypeface(AndroidUtilities.getTypeface("fonts/gram.ttf"));
        usdView.setGravity(Gravity.CENTER);
        usdView.setPadding(dp(10), 0, dp(10), 0);
        usdView.setAllowCancel(true);
        final int pillColor = Theme.multAlpha(
                getThemedColor(Theme.key_windowBackgroundWhiteGrayText2),
                0.08f
        );
        usdView.setSizeableBackground(Theme.createRoundRectDrawable(dp(14), pillColor));
        center.addView(usdView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                28,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                0, 8, 0, 0
        ));
        ScaleStateListAnimator.apply(usdView);
        usdView.setOnClickListener(v -> switchInputMode());
        WalletAccessibility.readWithoutIcons(usdView, LocaleController.getString(R.string.AccDescrWalletSwitchCurrency));

        centerBottomContainer = new FrameLayout(context);
        center.addView(centerBottomContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        centerBottom = new LinearLayout(context);
        centerBottom.setOrientation(LinearLayout.VERTICAL);
        centerBottomContainer.addView(centerBottom, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        depositFundsView = new TextView(context);
        depositFundsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        depositFundsView.setTextColor(getThemedColor(Theme.key_featuredStickers_addButton));
        depositFundsView.setBackground(Theme.createRadSelectorDrawable(Theme.multAlpha(getThemedColor(Theme.key_featuredStickers_addButton), .10f), Theme.multAlpha(getThemedColor(Theme.key_featuredStickers_addButton), .25f), 14, 14));
        depositFundsView.setPadding(dp(10), 0, dp(10), 0);
        depositFundsView.setGravity(Gravity.CENTER);
        depositFundsView.setTypeface(AndroidUtilities.bold());
        final SpannableStringBuilder depositFunds = new SpannableStringBuilder(getString(R.string.WalletDepositFunds));
        depositFunds.append(" >");
        depositFunds.setSpan(new ColoredImageSpan(R.drawable.settings_arrow).translate(0, dpf2(0.66f)), depositFunds.length() - 1, depositFunds.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        depositFundsView.setText(depositFunds);
        centerBottomContainer.addView(depositFundsView, LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT,
            28,
            Gravity.TOP | Gravity.CENTER_HORIZONTAL,
            0, 8, 0, 0
        ));
        ScaleStateListAnimator.apply(depositFundsView);
        depositFundsView.setOnClickListener(v -> {
            WalletActivity.openWalletQRAlert(getContext(), currentAccount, getResourceProvider());
        });

        centerBottom.setVisibility(View.INVISIBLE);
        centerBottom.setAlpha(0.0f);
        centerBottom.setScaleX(0.8f);
        centerBottom.setScaleY(0.8f);
        depositFundsView.setVisibility(View.VISIBLE);
        depositFundsView.setAlpha(1.0f);
        depositFundsView.setScaleX(1.0f);
        depositFundsView.setScaleY(1.0f);
        centerBottomVisible = false;

        commentView = new TextView(context);
        final MessageDrawable commentBackground = new MessageDrawable(MessageDrawable.TYPE_TEXT, true, false);
        commentBackground.shadow = false;
        commentBackground.paint.setStyle(Paint.Style.STROKE);
        commentBackground.paint.setStrokeWidth(Math.max(1, dpf2(0.5f)));
        commentBackground.paint.setColor(getThemedColor(Theme.key_wallet_out_commentBorder));
        commentBackground.overrideColor = getThemedColor(Theme.key_wallet_out_commentBorder);
        commentView.setPadding(dp(15), dp(7), dp(19), dp(8));
        commentView.setBackground(commentBackground);
        commentView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        commentView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        commentView.setTypeface(AndroidUtilities.bold());
        commentView.setEllipsize(TextUtils.TruncateAt.END);
        commentView.setMaxLines(4);
        if (TextUtils.isEmpty(comment)) {
            commentView.setVisibility(View.GONE);
        } else {
            commentView.setText(comment);
            commentView.setVisibility(View.VISIBLE);
        }
        centerBottom.addView(commentView, LayoutHelper.createLinear(
            LayoutHelper.WRAP_CONTENT,
            LayoutHelper.WRAP_CONTENT,
            Gravity.TOP | Gravity.CENTER_HORIZONTAL,
            32, 12, 32, 0
        ));
        commentView.setOnClickListener(v -> {
            openAddCommentAlert();
        });
        ScaleStateListAnimator.apply(commentView, .02f, 1.2f);
        updateCenterBottom();

        insufficientFundsView = new LinkSpanDrawable.LinksTextView(context, resourceProvider);
        insufficientFundsView.setTextColor(getThemedColor(Theme.key_text_RedBold));
        insufficientFundsView.setLinkTextColor(getThemedColor(Theme.key_featuredStickers_addButton));
        insufficientFundsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        insufficientFundsView.setGravity(Gravity.CENTER);
        insufficientFundsView.setTypeface(AndroidUtilities.bold());
        insufficientFundsView.setPadding(dp(10), 0, dp(10), 0);
        showError(false, true);
        centerBottom.addView(insufficientFundsView, LayoutHelper.createLinear(
            LayoutHelper.WRAP_CONTENT,
            LayoutHelper.WRAP_CONTENT,
            Gravity.TOP | Gravity.CENTER_HORIZONTAL,
            0, 10, 0, 0
        ));


        bottomContainer = new LinearLayout(context);
        bottomContainer.setOrientation(LinearLayout.VERTICAL);
        bottomContainer.setPadding(dp(16), 0, dp(16), dp(12));
        bottomContainer.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhite), 0.0f),
                        getThemedColor(Theme.key_windowBackgroundWhite),
                        getThemedColor(Theme.key_windowBackgroundWhite)
                }
        ));

        balanceView = new TextView(context);
        balanceView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        balanceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        balanceView.setGravity(Gravity.CENTER);
        bottomContainer.addView(balanceView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 16, 0, 16, 0));

        feeArrow = new SpannableString(">");
        final ColoredImageSpan feeArrowSpan = new ColoredImageSpan(R.drawable.settings_arrow);
        feeArrowSpan.translate(0, dp(1));
        feeArrow.setSpan(feeArrowSpan, 0, feeArrow.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        feeView = new TextView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                setMeasuredDimension(getMeasuredWidth(), Math.round(getMeasuredHeight() * feeVisibilityProgress));
            }
        };
        feeView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        feeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        feeView.setGravity(Gravity.CENTER);
        feeView.setPadding(0, dp(4), 0, dp(4));
        feeView.setVisibility(View.GONE);
        bottomContainer.addView(feeView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 16, 0, 16, 0));
        feeViewLoading = new SpannableString(getString(R.string.Loading));
        feeViewLoading.setSpan(new LoadingSpan(feeView, dp(80)), 0, feeViewLoading.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        sendButton = new ButtonWithCounterView(context, getResourceProvider()).setRound();
        sendButton.setText(LocaleController.getString(R.string.WalletSendGrams));
        sendButton.setEnabled(false);
        sendButton.setOnClickListener(v -> {
            send();
        });
        bottomContainer.addView(sendButton, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                48,
                Gravity.BOTTOM | Gravity.FILL_HORIZONTAL,
                0, 14, 0, 0
        ));
        content.addView(bottomContainer, LayoutHelper.createFrame(
            LayoutHelper.MATCH_PARENT,
            LayoutHelper.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.FILL_HORIZONTAL
        ));
        updateAmount();

        updateIntroGeometry();

        return fragmentView;
    }

    private void startIntroAnimation() {
        if (introProgress >= 1f || introAnimator != null) return;
        introAnimator = new SpringAnimation(new FloatValueHolder(introProgress));
        introAnimator.setSpring(new SpringForce(1f).setDampingRatio(.55f).setStiffness(65f));
        introAnimator.setMinimumVisibleChange(.001f);
        introAnimator.addUpdateListener((animation, value, velocity) -> {
            introProgress = value;
            updateIntroGeometry();
        });
        introAnimator.addEndListener((animation, canceled, value, velocity) -> {
            introAnimator = null;
            introProgress = 1f;
            updateIntroGeometry();
        });
        introAnimator.start();
    }

    private void finishIntroAnimation() {
        if (amountInput != null) amountInput.getDiamondView().whenReady(null);
        if (introAnimator != null) introAnimator.cancel();
        introProgress = 1f;
        if (amountInput != null) updateIntroGeometry();
    }

    private void updateIntroGeometry() {
        final float remaining = 1f - introProgress;
        // Let the flight overshoot, but keep the reveals opaque once they appear.
        introRevealProgress = Math.max(introRevealProgress, Math.max(0f, Math.min(1f, introProgress)));
        final float revealRemaining = 1f - introRevealProgress;
        amountInput.setIntroProgress(introProgress, introRevealProgress, introAnimator == null && introProgress == 1f);
        // The group's center follows the live space above the keyboard. Start the
        // diamond there, then move the amount row into its final place in the group.
        amountInput.setTranslationY((center.getHeight() / 2f - amountInput.getTop()
                - amountInput.getHeight() / 2f) * remaining);
        usdView.setAlpha(introRevealProgress);
        usdView.setTranslationY(dp(16) * revealRemaining);
        centerBottomContainer.setAlpha(introRevealProgress);
        centerBottomContainer.setTranslationY(dp(16) * revealRemaining);
        bottomContainer.setAlpha(introRevealProgress);
        // Keep the IME translation on the parent independent of the intro offsets.
        // Fee alpha remains owned by its visibility animation.
        for (int i = 0; i < bottomContainer.getChildCount(); i++) {
            bottomContainer.getChildAt(i).setTranslationY(dp(24) * revealRemaining);
        }
    }

    private boolean centerBottomVisible;
    private void updateCenterBottom() {
        if (centerBottom == null || depositFundsView == null) return;
        final boolean visible = getGramNanoAmount() > 0 || commentView.getVisibility() == View.VISIBLE;
        if (visible == centerBottomVisible) return;
        centerBottomVisible = visible;
        centerBottom.setVisibility(View.VISIBLE);
        depositFundsView.setVisibility(View.VISIBLE);
        centerBottom.animate().alpha(visible ? 1.0f : 0.0f).scaleX(visible ? 1.0f : 0.8f).scaleY(visible ? 1.0f : 0.8f).withEndAction(() -> {
            centerBottom.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        depositFundsView.animate().alpha(!visible ? 1.0f : 0.0f).scaleX(!visible ? 1.0f : 0.8f).scaleY(!visible ? 1.0f : 0.8f).withEndAction(() -> {
            depositFundsView.setVisibility(!visible ? View.VISIBLE : View.INVISIBLE);
        }).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
    }

    private void updateAddressInfo() {
        if (addressInfoLayout != null) {
            addressInfoLayout.setRecipient(recipientUser, getAddress());
        }
    }

    private void showError(boolean show, boolean insufficientFunds) {
        if (show) {
            if (insufficientFunds) {
                final SpannableStringBuilder sb = new SpannableStringBuilder();
                sb.append(getString(R.string.WalletInsufficientFunds));
                sb.append(" ");
                int from = sb.length();
                sb.append(getString(R.string.WalletDepositFunds));
                sb.append(" >");
                sb.setSpan(new ColoredImageSpan(R.drawable.settings_arrow).setOverrideColor(getThemedColor(Theme.key_featuredStickers_addButton)).translate(0, dpf2(0.66f)), sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View view) {
                        WalletActivity.openWalletQRAlert(getContext(), currentAccount, getResourceProvider());
                    }
                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        ds.setColor(getThemedColor(Theme.key_featuredStickers_addButton));
                        ds.setUnderlineText(false);
                    }
                }, from, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                insufficientFundsView.setText(sb);
            } else {
                insufficientFundsView.setText(LocaleController.formatSpannable(R.string.WalletMinimumAmount, GramWallet.formatGramCapital(MessagesController.getInstance(currentAccount).config.walletTransferMinNanos.get())));
            }
        }
        insufficientFundsView.animate()
            .alpha(show ? 1.0f : 0.0f)
            .scaleX(show ? 1.0f : 0.6f)
            .scaleY(show ? 1.0f : 0.6f)
            .setDuration(320)
            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
            .start();
    }

    private void loadFee() {
        if (!WalletEngine2.isValidRecipientAddress(getAddress())) return;

        fee = -1;
        updateBalance();

        final GramWallet wallet = GramWallet.getInstance(currentAccount);
        if (
            wallet.isGaslessAvailable() &&
            !(GramWallet.addressEquals(wallet.getAddress(), getAddress())) &&
            (recipientUser != null && TextUtils.isEmpty(recipientDomain))
        ) return;
        wallet.emulateSend(getAddress(), 0, comment, comment_encrypted ? getPublicKey() : null, (transaction, error) -> {
            if (transaction != null) {
                fee = transaction.fee;
                updateBalance();
            }
        });
    }

    private void openAddCommentAlert() {
        final Context context = getContext();
        if (context == null) {
            return;
        }

        final boolean adaptive = keyboardInset - bottomInset > dp(20);
        final AlertDialog.Builder builder = adaptive
            ? new AlertDialogDecor.Builder(context, getResourceProvider())
            : new AlertDialog.Builder(context, getResourceProvider());
        builder.setTitle(getString(R.string.WalletAddComment));

        final EditTextBoldCursor editText = new EditTextBoldCursor(context) {
            private int remaining = 960;
            private final AnimatedColor limitColor = new AnimatedColor(this);
            private final AnimatedTextView.AnimatedTextDrawable limit = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
            {
                limit.setAnimationProperties(.2f, 0, 160, CubicBezierInterpolator.EASE_OUT_QUINT);
                limit.setTextSize(dp(15.33f));
                limit.setGravity(Gravity.RIGHT);
                limit.setCallback(this);
            }

            @Override
            protected boolean verifyDrawable(@NonNull Drawable who) {
                return who == limit || super.verifyDrawable(who);
            }

            @Override
            protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
                super.onTextChanged(text, start, lengthBefore, lengthAfter);
                if (limit == null) return;
                remaining = 960 - text.toString().getBytes(StandardCharsets.UTF_8).length;
                limit.cancelAnimation();
                limit.setText(remaining <= 100 ? Integer.toString(remaining) : "", isAttachedToWindow());
                invalidate();
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                limit.setTextColor(limitColor.set(Theme.getColor(
                    remaining <= 0 ? Theme.key_text_RedRegular : Theme.key_dialogSearchHint, WalletSendEnterAmount.this.getResourceProvider())));
                final int right = getScrollX() + getWidth() - getPaddingRight() + dp(48);
                final int bottom = getScrollY() + getHeight();
                limit.setBounds(right - dp(48), bottom - Math.min(dp(44), getHeight()), right, bottom);
                limit.draw(canvas);
            }
        };
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        editText.setHint(getString(R.string.WalletCommentOptionalMessage));
        editText.setText(comment);
        editText.setSelection(editText.length());
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setMaxLines(5);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setLineColors(
            getThemedColor(Theme.key_windowBackgroundWhiteInputField),
            getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated),
            getThemedColor(Theme.key_text_RedRegular)
        );
        editText.setBackground(null);
        editText.setPadding(0, dp(10), dp(48), dp(10));

        final CheckBoxCell publicCommentCell = new CheckBoxCell(context, 1, getResourceProvider());
        publicCommentCell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourceProvider), Theme.RIPPLE_MASK_ROUNDRECT_6DP, dp(12)));
        publicCommentCell.setText(getString(R.string.WalletMakeCommentPublic), "", !comment_encrypted || getPublicKey() == null, false);
        publicCommentCell.setMultiline(true);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) publicCommentCell.getCheckBoxView().getLayoutParams();
        lp.topMargin = 0;
        lp.gravity = (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL;
        publicCommentCell.getCheckBoxView().setLayoutParams(lp);
        publicCommentCell.setPadding(LocaleController.isRTL ? dp(4) : 0, dp(12), LocaleController.isRTL ? 0 : dp(4), dp(12));
        publicCommentCell.setEnabled(getPublicKey() != null);
        publicCommentCell.setOnClickListener(v -> publicCommentCell.setChecked(!publicCommentCell.isChecked(), true));

        final LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(editText, LayoutHelper.createLinear(
            LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 4, 24, 4
        ));
        container.addView(publicCommentCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 0));
        builder.setCustomViewOffset(6);
        builder.setView(container);
        builder.setWidth(dp(292));

        builder.setNegativeButton(getString(R.string.Cancel), (dialog, which) -> {
            dialog.dismiss();
        });
        builder.setPositiveButton(getString(R.string.Add), (dialog, which) -> {
            if (editText.getText().toString().getBytes(StandardCharsets.UTF_8).length > 960) {
                AndroidUtilities.shakeViewSpring(editText, shakeDp = -shakeDp);
                return;
            }
            final String text = editText.getText().toString().trim();
            comment = TextUtils.isEmpty(text) ? null : text;
            comment_encrypted = !publicCommentCell.isChecked();

            if (TextUtils.isEmpty(comment)) {
                commentView.setVisibility(View.GONE);
            } else {
                commentView.setText(comment);
                commentView.setVisibility(View.VISIBLE);
            }
            updateCenterBottom();
            dialog.dismiss();

            loadFee();
        });

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });
        dialog.setOnDismissListener(ignored -> {
            if (amountEdit != null) {
                amountEdit.requestFocus();
                AndroidUtilities.showKeyboard(amountEdit);
            }
        });
        if (adaptive) {
            dialog.showDelayed(250);
        } else {
            dialog.show();
        }
        dialog.setDismissDialogByButtons(false);

        final View positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (positiveButton instanceof TextView && !TextUtils.isEmpty(comment)) {
            final TextView positiveButtonText = (TextView) positiveButton;
            editText.addTextChangedListener(new TextWatcher() {
                private boolean wasEmpty = TextUtils.isEmpty(editText.getText());
                @Override
                public void afterTextChanged(Editable editable) {
                    final boolean isEmpty = TextUtils.isEmpty(editable);
                    if (isEmpty != wasEmpty) {
                        positiveButtonText.setText(getString((wasEmpty = isEmpty) ? R.string.Remove : R.string.Add));
                    }
                }
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
            });
        }
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.walletUpdate);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        pendingAmountKeyboard = false;
        finishIntroAnimation();
        if (chatSendTransition != null) {
            chatSendTransition.cancel();
            chatSendTransition = null;
        }
        if (sendTransition != null) {
            sendTransition.cancel();
            sendTransition = null;
        }
        if (feeVisibilityAnimator != null) {
            feeVisibilityAnimator.cancel();
            feeVisibilityAnimator = null;
        }
        if (amountEdit != null) {
            amountEdit.removeCallbacks(showAmountKeyboard);
        }
        getNotificationCenter().removeObserver(this, NotificationCenter.walletUpdate);
        super.onFragmentDestroy();
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        if (isOpen && !backward && amountInput != null && introProgress < 1f) {
            amountInput.getDiamondView().whenReady(this::startIntroAnimation);
        }
        if (isOpen && !backward && amountEdit != null) {
            amountEdit.removeCallbacks(showAmountKeyboard);
            if (initAmount > 0) {
                amountEdit.setSelection(amountEdit.length());
            } else {
                pendingAmountKeyboard = true;
                amountEdit.requestFocus();
                // Queue after the opening transition is set up, without waiting
                // for it to finish, so the IME and intro move together.
                amountEdit.post(showAmountKeyboard);
            }
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (isOpen && !backward && pendingAmountKeyboard && amountEdit != null) {
            amountEdit.removeCallbacks(showAmountKeyboard);
            amountEdit.post(showAmountKeyboard);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFee();
    }

    @Override
    public void onPause() {
        finishIntroAnimation();
        hideAmountKeyboard();
        super.onPause();
    }

    private void hideAmountKeyboard() {
        pendingAmountKeyboard = false;
        if (amountEdit == null) return;
        amountEdit.removeCallbacks(showAmountKeyboard);
        // Keep the amount field from reopening the IME when authorization closes.
        fragmentView.requestFocus();
        AndroidUtilities.hideKeyboard(amountEdit);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.walletUpdate) {
            updateBalance();
            updateAmount();
        }
    }

    private CharSequence createTitle() {
        return LocaleController.getString(R.string.WalletSendMoneyTo);
    }

    private String shortenAddress(String value) {
        if (TextUtils.isEmpty(value) || value.length() <= 12) {
            return value == null ? "" : value;
        }
        return value.substring(0, 6) + "…" + value.substring(value.length() - 6);
    }

    private DecimalFormat gramsTextFormat;
    private String getGramsText() {
        final long amount = getGramNanoAmount();
        if (gramsTextFormat == null) {
            gramsTextFormat = new DecimalFormat("#,##0.#########", new DecimalFormatSymbols(Locale.US));
        }
        return amount > 0
                ? gramsTextFormat.format(BigDecimal.valueOf(amount, 9))
                : "";
    }

    private String getGramsInputText(long amount) {
        return BigDecimal.valueOf(amount)
                .movePointLeft(9)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private DecimalFormat gramsPreviewFormat;
    private String getGramsPreviewText(long amount) {
        if (gramsPreviewFormat == null) {
            gramsPreviewFormat = new DecimalFormat("#,##0.##", new DecimalFormatSymbols(Locale.US));
            gramsPreviewFormat.setRoundingMode(RoundingMode.HALF_UP);
        }
        return gramsPreviewFormat.format(BigDecimal.valueOf(amount).movePointLeft(9));
    }

    private static final float PREVIEW_FRACTION_SCALE = 12.0f / 14.0f;

    private CharSequence getGramsPreview(long amount) {
        final SpannableStringBuilder result = new SpannableStringBuilder();
        result.append(gramAmountIcon).append(" ");
        final int amountStart = result.length();
        result.append(getGramsPreviewText(amount));
        setPreviewFractionSpan(result, amountStart, result.length(), '.');
        result.append(" ");
        final int gramStart = result.length();
        result.append(getString(R.string.GramCurrency));
        result.setSpan(
                new RelativeSizeSpan(PREVIEW_FRACTION_SCALE),
                gramStart,
                result.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return result.append(" ").append(currencyExchangeIcon);
    }

    private CharSequence getCurrencyPreviewText(GramWallet wallet, long amount) {
        final SpannableStringBuilder result = new SpannableStringBuilder("≈ ");
        final int amountStart = result.length();
        result.append(wallet.formatCurrency(amount));
        setPreviewFractionSpan(result, amountStart, result.length(), getCurrencyDecimalSeparator());
        return result.append(" ").append(currencyExchangeIcon);
    }

    private char getCurrencyDecimalSeparator() {
        final TL_wallet.currencyRate rate = getSelectedCurrencyRate();
        return rate == null || TextUtils.isEmpty(rate.decimalSeparator)
                ? '.'
                : rate.decimalSeparator.charAt(0);
    }

    private static void setPreviewFractionSpan(SpannableStringBuilder text, int start, int end, char decimalSeparator) {
        for (int i = start + 1; i + 1 < end; i++) {
            if (text.charAt(i) != decimalSeparator
                    || !Character.isDigit(text.charAt(i - 1))
                    || !Character.isDigit(text.charAt(i + 1))) {
                continue;
            }
            int fractionEnd = i + 1;
            while (fractionEnd < end && Character.isDigit(text.charAt(fractionEnd))) {
                fractionEnd++;
            }
            text.setSpan(
                    new RelativeSizeSpan(PREVIEW_FRACTION_SCALE),
                    i + 1,
                    fractionEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            return;
        }
    }

    private long getGramNanoAmount() {
        if (amountEdit == null) {
            return 0;
        }
        BigDecimal grams = parseAmount(amountEdit.getText().toString());
        if (inputInCurrency) {
            final BigDecimal currencyPerGram = getCurrencyPerGram();
            if (currencyPerGram.signum() <= 0) {
                return 0;
            }
            grams = grams
                    .divide(currencyPerGram, 9, RoundingMode.DOWN)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        final BigInteger amount = grams.movePointRight(9).toBigInteger();
        return amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                ? Long.MAX_VALUE
                : amount.longValue();
    }

    private void switchInputMode() {
        final BigDecimal currencyPerGram = getCurrencyPerGram();
        if (!inputInCurrency && currencyPerGram.signum() <= 0) {
            return;
        }

        final long amount = getGramNanoAmount();
        inputInCurrency = !inputInCurrency;
        updateInputModeViews();

        final String value;
        if (amount <= 0) {
            value = "";
        } else if (inputInCurrency) {
            value = formatCurrencyInput(amount, currencyPerGram);
        } else {
            value = getGramsInputText(amount);
        }
        amountInput.setAmountText(value);
        amountEdit.setSelection(value.length());
    }

    private void updateInputModeViews() {
        amountInput.setCurrency(GramWallet.getInstance(currentAccount), inputInCurrency);
    }

    private int getCurrencyAmountFractionDigits(BigDecimal currencyPerGram) {
        if (currencyPerGram.signum() <= 0) {
            return 9;
        }
        final BigDecimal nanogramInCurrency = currencyPerGram.movePointLeft(9);
        int fractionDigits = 0;
        BigDecimal step = BigDecimal.ONE.movePointLeft(1);
        while (step.compareTo(nanogramInCurrency) >= 0) {
            fractionDigits++;
            step = step.movePointLeft(1);
        }
        return fractionDigits;
    }

    private String formatCurrencyInput(long nanograms, BigDecimal currencyPerGram) {
        final BigDecimal value = BigDecimal.valueOf(nanograms)
                .multiply(currencyPerGram)
                .movePointLeft(9);
        final int fractionDigits = Math.max(0, Math.min(getCurrencyFractionDigits(), 20));
        BigDecimal rounded = value.setScale(fractionDigits, RoundingMode.HALF_UP);
        if (rounded.signum() == 0 && value.signum() != 0) {
            rounded = value.setScale(Math.min(20, Math.max(fractionDigits, value.scale())), RoundingMode.HALF_UP);
        }
        final int maxFractionDigits = getCurrencyAmountFractionDigits(currencyPerGram);
        if (rounded.scale() > maxFractionDigits) {
            rounded = rounded.setScale(maxFractionDigits, RoundingMode.DOWN);
        }
        final BigDecimal maxAmount = BigDecimal.valueOf(Long.MAX_VALUE, 9).multiply(currencyPerGram);
        if (rounded.compareTo(maxAmount) > 0) {
            rounded = maxAmount.setScale(rounded.scale(), RoundingMode.DOWN);
        }
        return rounded.stripTrailingZeros().toPlainString();
    }

    private BigDecimal getCurrencyPerGram() {
        final GramWallet wallet = GramWallet.getInstance(currentAccount);
        wallet.currencies.getCurrencies();
        final double currencyRate = wallet.currencies.getCurrencyRate();
        final double gramUsdRate = MessagesController.getInstance(currentAccount).config.tonUsdRate.get();
        if (currencyRate <= 0 || gramUsdRate <= 0
                || Double.isNaN(currencyRate) || Double.isInfinite(currencyRate)
                || Double.isNaN(gramUsdRate) || Double.isInfinite(gramUsdRate)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(currencyRate).multiply(BigDecimal.valueOf(gramUsdRate));
    }

    private int getCurrencyFractionDigits() {
        final TL_wallet.currencyRate rate = getSelectedCurrencyRate();
        if (rate != null) {
            return rate.exp;
        }
        return TextUtils.equals(GramWallet.getInstance(currentAccount).currencies.getCurrency(), "USD") ? 2 : 0;
    }

    private TL_wallet.currencyRate getSelectedCurrencyRate() {
        final GramWallet wallet = GramWallet.getInstance(currentAccount);
        final TL_wallet.currencyRates rates = wallet.currencies.getCurrencies();
        if (rates == null) {
            return null;
        }
        for (TL_wallet.currencyRate rate : rates.rates) {
            if (rate != null && TextUtils.equals(rate.currency, wallet.currencies.getCurrency())) {
                return rate;
            }
        }
        return null;
    }

    private void updateAmount() {
        updateCenterBottom();
        if (amountEdit == null || sendButton == null) {
            return;
        }

        final GramWallet wallet = GramWallet.getInstance(currentAccount);
        final long balance = wallet.getBalance();
        final long amount = getGramNanoAmount();
        final long minAmount = MessagesController.getInstance(currentAccount).config.walletTransferMinNanos.get();
        if (amount > 0 && amount < minAmount) {
            final String minimum = inputInCurrency
                    ? formatCurrencyInput(minAmount, getCurrencyPerGram())
                    : getGramsInputText(minAmount);
            amountInput.setAmountText(minimum);
            amountEdit.setSelection(minimum.length());
            AndroidUtilities.shakeViewSpring(amountEdit, shakeDp = -shakeDp);
            return;
        }
        final boolean valid = (
            WalletEngine2.isValidRecipientAddress(getAddress())
            && !loadingRecipient
            && amount > 0
            && amount <= balance
        );

        showError(!TextUtils.isEmpty(amountEdit.getText()) && amount > balance, true);

        final String amountText = getGramsText();
        sendButton.setLoading(loadingRecipient);
        sendButton.setText(TextUtils.isEmpty(amountText) ? LocaleController.getString(R.string.WalletSendGrams) : LocaleController.formatSpannable(R.string.WalletSendAmount, GramWallet.formatGramsWithUnit(amountText)), true);
        sendButton.setEnabled(valid);
        updateBalance();

        updateInputModeViews();
        if (inputInCurrency) {
            usdView.setText(getGramsPreview(amount));
        } else {
            usdView.setText(getCurrencyPreviewText(wallet, amount));
        }
    }

    private int shakeDp = 6;
    private TL_wallet.walletTransaction queuedTransaction;
    private WalletActivity sendDestination;
    private WalletSendTransition sendTransition;
    private ChatActivity chatSendDestination;
    private WalletChatSendTransition chatSendTransition;

    @Override
    public AnimatorSet onCustomTransitionAnimation(boolean isOpen, Runnable callback) {
        if (!isOpen && chatSendDestination != null && queuedTransaction != null
                && queuedTransaction.localMessageId != 0 && amountInput != null
                && amountInput.getDiamondView().canAnimateFlight()
                && fragmentView != null && fragmentView.getParent() instanceof View && getParentLayout() != null) {
            chatSendTransition = new WalletChatSendTransition(getParentLayout().getView(),
                    (View) fragmentView.getParent(), amountInput.getDiamondView(), chatSendDestination,
                    queuedTransaction, callback);
            return chatSendTransition.animator();
        }
        if (!isOpen && sendDestination != null && queuedTransaction != null && amountInput != null
                && fragmentView != null && fragmentView.getParent() instanceof View && getParentLayout() != null) {
            sendTransition = new WalletSendTransition(getParentLayout().getView(),
                    (View) fragmentView.getParent(), amountInput.getDiamondView(), sendDestination,
                    queuedTransaction, callback);
            return sendTransition.animator();
        }
        return super.onCustomTransitionAnimation(isOpen, callback);
    }

    private void send() {
        if (!sendButton.isEnabled() || sendButton.isLoading()) return;
        final GramWallet wallet = GramWallet.getInstance(currentAccount);
        long _amount = getGramNanoAmount();
        final long balance = wallet.getBalance();
        if (loadingRecipient || !WalletEngine2.isValidRecipientAddress(getAddress()) || _amount <= 0) {
            return;
        }
        final long minAmount = MessagesController.getInstance(currentAccount).config.walletTransferMinNanos.get();
        if (_amount < minAmount) {
            final String minimum = inputInCurrency
                    ? formatCurrencyInput(minAmount, getCurrencyPerGram())
                    : getGramsInputText(minAmount);
            amountInput.setAmountText(minimum);
            amountEdit.setSelection(minimum.length());
            AndroidUtilities.shakeViewSpring(amountEdit, shakeDp = -shakeDp);
            return;
        }
        if (_amount > balance) {
            BotWebViewVibrationEffect.APP_ERROR.vibrate();
            AndroidUtilities.shakeViewSpring(insufficientFundsView, shakeDp = -shakeDp);
            return;
        }
        if (fee >= 0 && _amount + fee > balance) {
            _amount -= fee;
        }
        final long amount = _amount;
        hideAmountKeyboard();
        sendButton.setLoading(true);
        queuedTransaction = null;
        if (recipientUser != null && TextUtils.isEmpty(recipientDomain)) {
            wallet.send(recipientUser, recipientAddress, amount, comment, comment_encrypted ? getPublicKey() : null, null, error2 -> {
                if ("PASSCODE_FAILED".equalsIgnoreCase(error2)) {
                    sendButton.setLoading(false);
                    return;
                }
                if (error2 != null) {
                    sendButton.setLoading(false);
                    BulletinFactory.of(WalletSendEnterAmount.this).showForError(error2);
                    return;
                }
                if (openChat) {
                    returnToChat();
                } else {
                    returnToWallet();
                }
            }, transaction -> queuedTransaction = transaction);
        } else {
            final String sendingComment = comment;
            final byte[] sendingCommentPublicKey = comment_encrypted ? getPublicKey() : null;
            final boolean sendingCommentEncrypted = sendingCommentPublicKey != null;
            if (wallet.isGaslessAvailable()) {
                final TL_wallet.walletTransaction transaction = new TL_wallet.walletTransaction();
                transaction.peer = GramWallet.createRecipientPeer(recipientUser, recipientAddress, recipientDomain);
                transaction.comment = sendingComment;
                transaction.comment_encrypted = sendingCommentEncrypted;
                transaction.date = getConnectionsManager().getCurrentTime();
                transaction.fee = Math.max(0, fee);
                transaction.gasless = true;
                transaction.amount = amount;
                AndroidUtilities.hideKeyboard(amountEdit);
                WalletActivity.openTransactionSheet(getContext(), getCurrentAccount(), transaction, (comment, comment_encrypted, done) -> {
                    wallet.send(recipientUser, recipientAddress, amount, comment, comment_encrypted ? getPublicKey() : null, recipientDomain, error2 -> {
                        if ("PASSCODE_FAILED".equalsIgnoreCase(error2)) return;
                        done.run(error2);
                        if (error2 == null) fragmentView.postDelayed(this::returnToWallet, 220);
                    }, queued -> queuedTransaction = queued);
                }, (updatedComment, updatedCommentEncrypted) -> {
                    comment = updatedComment;
                    comment_encrypted = updatedCommentEncrypted;
                    commentView.setText(comment);
                    commentView.setVisibility(TextUtils.isEmpty(comment) ? View.GONE : View.VISIBLE);
                    updateCenterBottom();
                }, getPublicKey(), null, getResourceProvider());
                AndroidUtilities.runOnUIThread(() -> sendButton.setLoading(false), 1000);
            } else {
                wallet.emulateSend(recipientAddress, amount, sendingComment, sendingCommentPublicKey, (transaction, error) -> {
                    if (error != null) {
                        sendButton.setLoading(false);
                        BulletinFactory.of(this).showForError(error);
                        return;
                    }
                    transaction.peer = GramWallet.createRecipientPeer(recipientUser, recipientAddress, recipientDomain);
                    transaction.comment = sendingComment;
                    transaction.comment_encrypted = sendingCommentEncrypted;
                    transaction.date = getConnectionsManager().getCurrentTime();
                    AndroidUtilities.hideKeyboard(amountEdit);
                    WalletActivity.openTransactionSheet(getContext(), getCurrentAccount(), transaction, (comment, comment_encrypted, done) -> {
                        wallet.send(recipientUser, recipientAddress, amount, comment, comment_encrypted ? getPublicKey() : null, recipientDomain, error2 -> {
                            if ("PASSCODE_FAILED".equalsIgnoreCase(error2)) return;
                            done.run(error2);
                            if (error2 == null) fragmentView.postDelayed(this::returnToWallet, 220);
                        }, queued -> queuedTransaction = queued);
                    }, (updatedComment, updatedCommentEncrypted) -> {
                        comment = updatedComment;
                        comment_encrypted = updatedCommentEncrypted;
                        commentView.setText(comment);
                        commentView.setVisibility(TextUtils.isEmpty(comment) ? View.GONE : View.VISIBLE);
                        updateCenterBottom();
                    }, getPublicKey(), null, getResourceProvider());
                    AndroidUtilities.runOnUIThread(() -> sendButton.setLoading(false), 1000);
                });
            }
        }
    }

    private void returnToChat() {
        final INavigationLayout layout = getParentLayout();
        if (layout == null) {
            finishFragment();
            return;
        }
        final long dialogId = recipientUser.id;
        final List<BaseFragment> stack = new ArrayList<>(layout.getFragmentStack());
        if (stack.isEmpty() || stack.get(stack.size() - 1) != this) return;
        for (int i = stack.size() - 2; i >= 0; i--) {
            BaseFragment fragment = stack.get(i);
            if (fragment instanceof ChatActivity && fragment.getCurrentAccount() == currentAccount
                    && ((ChatActivity) fragment).getDialogId() == dialogId
                    && ((ChatActivity) fragment).getChatMode() == 0) {
                chatSendDestination = (ChatActivity) fragment;
                for (int j = stack.size() - 2; j > i; j--) {
                    layout.removeFragmentFromStack(stack.get(j));
                }
                break;
            }
        }
        if (chatSendDestination == null) {
            getNotificationCenter().postNotificationName(NotificationCenter.closeProfileActivity, dialogId, false);
            final ChatActivity chat = ChatActivity.of(dialogId);
            chat.setCurrentAccount(currentAccount);
            if (!layout.addFragmentToStack(chat, layout.getFragmentStack().size() - 1)) {
                finishFragment();
                return;
            }
            chatSendDestination = chat;
        }
        finishFragment();
    }

    private void returnToWallet() {
        final INavigationLayout layout = getParentLayout();
        if (layout == null) {
            finishFragment();
            return;
        }
        final List<BaseFragment> stack = new ArrayList<>(layout.getFragmentStack());
        if (stack.isEmpty() || stack.get(stack.size() - 1) != this) return;
        for (BaseFragment fragment : stack) {
            if (fragment instanceof WalletSendSelectSender) {
                layout.removeFragmentFromStack(fragment);
            }
        }
        final List<BaseFragment> remaining = layout.getFragmentStack();
        if (remaining.size() >= 2 && remaining.get(remaining.size() - 2) instanceof WalletActivity
                && remaining.get(remaining.size() - 2).getCurrentAccount() == currentAccount) {
            sendDestination = (WalletActivity) remaining.get(remaining.size() - 2);
        } else {
            final WalletActivity walletActivity = new WalletActivity();
            walletActivity.setCurrentAccount(currentAccount);
            if (layout.addFragmentToStack(walletActivity, remaining.size() - 1)) {
                sendDestination = walletActivity;
            }
        }
        finishFragment();
    }

    private BigDecimal parseAmount(String value) {
        if (TextUtils.isEmpty(value)) {
            return BigDecimal.ZERO;
        }
        try {
            final BigDecimal amount = new BigDecimal(value.replace(',', '.'));
            return amount.signum() < 0 ? BigDecimal.ZERO : amount;
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private void updateFeeVisibility(boolean visible) {
        if (feeVisible == visible) {
            return;
        }
        feeVisible = visible;
        if (feeVisibilityAnimator != null) {
            feeVisibilityAnimator.cancel();
            feeVisibilityAnimator = null;
        }
        final float target = visible ? 1.0f : 0.0f;
        if (!(feeView.isAttachedToWindow())) {
            setFeeVisibilityProgress(target);
            return;
        }
        feeVisibilityAnimator = ValueAnimator.ofFloat(feeVisibilityProgress, target);
        feeVisibilityAnimator.setDuration(320);
        feeVisibilityAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        feeVisibilityAnimator.addUpdateListener(animation -> setFeeVisibilityProgress((float) animation.getAnimatedValue()));
        feeVisibilityAnimator.start();
    }

    private void setFeeVisibilityProgress(float progress) {
        feeVisibilityProgress = progress;
        feeView.setAlpha(progress);
        feeView.setVisibility(progress > 0 ? View.VISIBLE : View.GONE);
        final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) feeView.getLayoutParams();
        params.topMargin = Math.round(dp(4) * progress);
        feeView.requestLayout();
    }

    private void updateBalance() {
        if (balanceView == null) {
            return;
        }
        final GramWallet wallet = GramWallet.getInstance(currentAccount);
        balanceView.setText(LocaleController.formatSpannable(R.string.WalletBalanceAmount, GramWallet.formatGramsWithUnit(wallet.getBalance(), wallet.getBalance() < 1_000_000_000L)));
        hasEnteredAmount |= amountEdit != null && !TextUtils.isEmpty(amountEdit.getText());
        final boolean showFee = !(wallet.isGaslessAvailable() && !GramWallet.addressEquals(wallet.getAddress(), getAddress())) && hasEnteredAmount;
        if (showFee) {
            if (fee >= 0) {
                feeView.setText(LocaleController.formatSpannable(R.string.WalletNetworkFeeAmount, GramWallet.formatGramsWithUnit(fee)));
            } else {
                feeView.setText(LocaleController.formatSpannable(R.string.WalletNetworkFeeAmount, feeViewLoading));
            }
        }
        updateFeeVisibility(showFee);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public WindowInsetsCompat onInsetsInternal(@NonNull View view, @NonNull WindowInsetsCompat windowInsets) {
        keyboardInset = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        return super.onInsetsInternal(view, windowInsets);
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        bottomInset = bottom;
        updateBottomLayout();
    }

    private void updateCenterGeometry() {
        if (center == null || bottomContainer == null || center.getHeight() <= 0 || bottomContainer.getHeight() <= 0) {
            return;
        }
        float top = fragmentView.getPaddingTop();
        if (addressInfoLayout.getVisibility() == View.VISIBLE) {
            final float recipientBottom = addressInfoLayout.getY() + addressInfoLayout.getPivotY()
                    + (addressInfoLayout.getContentBottom() - addressInfoLayout.getPivotY()) * addressInfoLayout.getScaleY();
            top = Math.max(top, recipientBottom);
        }
        final float bottom = Math.min(fragmentView.getHeight() - fragmentView.getPaddingBottom(), bottomContainer.getY());
        final float availableHeight = Math.max(0, bottom - top);
        final float gap = Math.min(dp(12), availableHeight / 4.0f);
        final float scale = Math.min(1.0f, Math.max(0, availableHeight - gap * 2) / center.getHeight());
        center.setPivotX(center.getWidth() / 2.0f);
        center.setPivotY(center.getHeight() / 2.0f);
        center.setScaleX(scale);
        center.setScaleY(scale);
        center.setTranslationY(top + availableHeight / 2.0f - center.getTop() - center.getHeight() / 2.0f);
    }

    private int lastBottom;
    private void updateBottomLayout() {
        if (bottomContainer == null) {
            return;
        }
        final int bottom = Math.max(0, keyboardInset - bottomInset);
        bottomContainer.setPadding(dp(16), 0, dp(16), dp(12) + bottomInset);
        if (lastBottom != bottom) {
            bottomContainer.animate().cancel();
            bottomContainer.animate().translationY(-bottom).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            lastBottom = bottom;
        }
    }
}
