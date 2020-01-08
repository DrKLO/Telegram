/*
 * This is the source code of Wallet for Android v. 1.0.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Copyright Nikolai Kudashov, 2019.
 */

package org.telegram.ui.Wallet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TonController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.PollEditTextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BiometricPromtHelper;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TypefaceSpan;

import java.net.URLEncoder;

import androidx.core.widget.NestedScrollView;
import drinkless.org.ton.TonApi;

public class WalletActionSheet extends BottomSheet {

    public static final int TYPE_SEND = 0;
    public static final int TYPE_INVOICE = 1;
    public static final int TYPE_TRANSACTION = 2;

    public static final int SEND_ACTIVITY_RESULT_CODE = 33;

    private int currentType;

    private ListAdapter listAdapter;
    private NestedScrollView scrollView;
    private LinearLayout linearLayout;
    private ActionBar actionBar;
    private View actionBarShadow;
    private View shadow;
    private BiometricPromtHelper biometricPromtHelper;

    private Drawable gemDrawable;
    private boolean inLayout;

    private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private BaseFragment parentFragment;

    private int scrollOffsetY;
    private AnimatorSet actionBarAnimation;
    private AnimatorSet shadowAnimation;

    private long amountValue;
    private long currentDate;
    private long currentStorageFee;
    private long currentTransactionFee;
    private String commentString = "";
    private String recipientString = "";
    private String walletAddress;
    private boolean hasWalletInBack = true;
    private boolean wasFirstAttach;
    private long currentBalance = -1;

    private int titleRow;
    private int recipientHeaderRow;
    private int recipientRow;
    private int sendBalanceRow;
    private int amountHeaderRow;
    private int amountRow;
    private int commentRow;
    private int commentHeaderRow;
    private int balanceRow;
    private int dateHeaderRow;
    private int dateRow;
    private int invoiceInfoRow;
    private int rowCount;

    private WalletActionSheetDelegate delegate;

    private static final int MAX_COMMENT_LENGTH = 500;

    public interface WalletActionSheetDelegate {
        default void openSendToAddress(String address) {

        }

        default void openInvoice(String url, long amount) {

        }

        default void openQrReader() {

        }
    }

    public static class ByteLengthFilter implements InputFilter {

        private final int mMax;

        public ByteLengthFilter(int max) {
            mMax = max;
        }

        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            int keep = mMax - (dest.toString().getBytes().length - (dend - dstart));
            if (keep <= 0) {
                return "";
            } else if (keep >= end - start) {
                return null;
            } else {
                keep += start;
                try {
                    return new String(source.toString().getBytes(), start, keep, "UTF-8");
                } catch (Exception ignore) {
                    return "";
                }
            }
        }

        public int getMax() {
            return mMax;
        }
    }

    @SuppressWarnings("FieldCanBeLocal")
    private class TitleCell extends FrameLayout {

        private TextView titleView;

        public TitleCell(Context context) {
            super(context);

            titleView = new TextView(getContext());
            titleView.setLines(1);
            titleView.setSingleLine(true);
            titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(15), AndroidUtilities.dp(22), AndroidUtilities.dp(8));
            titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            titleView.setGravity(Gravity.CENTER_VERTICAL);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 60));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60), MeasureSpec.EXACTLY));
        }

        public void setText(String text) {
            titleView.setText(text);
        }
    }

    public class BalanceCell extends FrameLayout {

        private SimpleTextView valueTextView;
        private TextView yourBalanceTextView;

        public BalanceCell(Context context) {
            super(context);

            valueTextView = new SimpleTextView(context);
            valueTextView.setTextSize(30);
            valueTextView.setRightDrawable(R.drawable.gem);
            valueTextView.setRightDrawableScale(0.8f);
            valueTextView.setDrawablePadding(AndroidUtilities.dp(7));
            valueTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            valueTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            valueTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 18, 0, 0));

            yourBalanceTextView = new TextView(context);
            yourBalanceTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            yourBalanceTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
            yourBalanceTextView.setLineSpacing(AndroidUtilities.dp(4), 1);
            yourBalanceTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            addView(yourBalanceTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 59, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.EXACTLY));
        }

        public void setBalance(long value, long storageFee, long transactionFee) {
            SpannableStringBuilder stringBuilder = new SpannableStringBuilder((value > 0 ? "+" : "") + TonController.formatCurrency(value));
            int index = TextUtils.indexOf(stringBuilder, '.');
            if (index >= 0) {
                stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), AndroidUtilities.dp(22)), index + 1, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            valueTextView.setText(stringBuilder);

            if (storageFee != 0 || transactionFee != 0) {
                StringBuilder builder = new StringBuilder();
                if (transactionFee != 0) {
                    builder.append(LocaleController.formatString("WalletTransactionFee", R.string.WalletTransactionFee, TonController.formatCurrency(transactionFee)));
                }
                if (storageFee != 0) {
                    if (builder.length() != 0) {
                        builder.append('\n');
                    }
                    builder.append(LocaleController.formatString("WalletStorageFee", R.string.WalletStorageFee, TonController.formatCurrency(storageFee)));
                }
                yourBalanceTextView.setText(builder);
                yourBalanceTextView.setVisibility(VISIBLE);
            } else {
                yourBalanceTextView.setVisibility(INVISIBLE);
            }
        }
    }

    @SuppressWarnings("FieldCanBeLocal")
    private class SendAddressCell extends PollEditTextCell {

        private ImageView qrButton;
        private ImageView copyButton;

        public SendAddressCell(Context context) {
            super(context, null);

            EditTextBoldCursor editText = getTextView();
            editText.setSingleLine(false);
            editText.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editText.setMinLines(2);
            editText.setTypeface(Typeface.DEFAULT);
            editText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            addTextWatcher(new TextWatcher() {

                private boolean ignoreTextChange;
                private boolean isPaste;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    isPaste = after >= 24;
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (ignoreTextChange) {
                        return;
                    }
                    String str = s.toString();
                    if (isPaste && str.toLowerCase().startsWith("ton://transfer")) {
                        ignoreTextChange = true;
                        parseTonUrl(s, str);
                        ignoreTextChange = false;
                    } else {
                        recipientString = str;
                    }
                }
            });

            if (currentType == TYPE_SEND) {
                editText.setBackground(Theme.createEditTextDrawable(context, true));

                qrButton = new ImageView(context);
                qrButton.setImageResource(R.drawable.wallet_qr);
                qrButton.setScaleType(ImageView.ScaleType.CENTER);
                qrButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader), PorterDuff.Mode.MULTIPLY));
                qrButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarWhiteSelector), 6));
                addView(qrButton, LayoutHelper.createFrame(48, 48, Gravity.TOP | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 6, 0, 6, 0));
                qrButton.setOnClickListener(v -> {
                    AndroidUtilities.hideKeyboard(getCurrentFocus());
                    delegate.openQrReader();
                });
                editText.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(60) : 0, AndroidUtilities.dp(13), LocaleController.isRTL ? 0 : AndroidUtilities.dp(60), AndroidUtilities.dp(8));
            } else {
                editText.setFocusable(false);
                editText.setEnabled(false);
                editText.setTypeface(Typeface.MONOSPACE);
                editText.setPadding(0, AndroidUtilities.dp(13), 0, AndroidUtilities.dp(10));

                copyButton = new ImageView(context);
                copyButton.setImageResource(R.drawable.msg_copy);
                copyButton.setScaleType(ImageView.ScaleType.CENTER);
                copyButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader), PorterDuff.Mode.MULTIPLY));
                copyButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarWhiteSelector), 6));
                addView(copyButton, LayoutHelper.createFrame(48, 48, Gravity.TOP | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 6, 10, 6, 0));
                copyButton.setOnClickListener(v -> {
                    AndroidUtilities.addToClipboard("ton://transfer/" + recipientString.replace("\n", ""));
                    Toast.makeText(v.getContext(), LocaleController.getString("WalletTransactionAddressCopied", R.string.WalletTransactionAddressCopied), Toast.LENGTH_SHORT).show();
                });
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (qrButton != null) {
                measureChildWithMargins(qrButton, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }
            if (copyButton != null) {
                measureChildWithMargins(copyButton, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }
        }
    }

    public WalletActionSheet(BaseFragment fragment, int type, String address) {
        super(fragment.getParentActivity(), true);
        walletAddress = address;
        if (walletAddress == null) {
            walletAddress = TonController.getInstance(currentAccount).getWalletAddress(UserConfig.getInstance(currentAccount).tonPublicKey);
        }
        currentType = type;
        parentFragment = fragment;
        init(fragment.getParentActivity());
    }

    public WalletActionSheet(BaseFragment fragment, String address, String recipient, String comment, long amount, long date, long storageFee, long transactionFee) {
        super(fragment.getParentActivity(), false);
        walletAddress = address;
        recipientString = recipient;
        commentString = comment;
        amountValue = amount;
        currentDate = date;
        currentType = TYPE_TRANSACTION;
        currentStorageFee = storageFee;
        currentTransactionFee = transactionFee;
        parentFragment = fragment;
        init(fragment.getParentActivity());
    }

    private void init(Context context) {
        updateRows();

        gemDrawable = context.getResources().getDrawable(R.drawable.gem);

        FrameLayout frameLayout = new FrameLayout(context) {

            private RectF rect = new RectF();
            private boolean ignoreLayout;

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY && actionBar.getAlpha() == 0.0f) {
                    dismiss();
                    return true;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return !isDismissed() && super.onTouchEvent(event);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int totalHeight = MeasureSpec.getSize(heightMeasureSpec);
                if (Build.VERSION.SDK_INT >= 21) {
                    ignoreLayout = true;
                    setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
                    ignoreLayout = false;
                }
                int availableHeight = totalHeight - getPaddingTop();

                int availableWidth = MeasureSpec.getSize(widthMeasureSpec) - backgroundPaddingLeft * 2;

                LayoutParams layoutParams = (LayoutParams) actionBarShadow.getLayoutParams();
                layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();

                ignoreLayout = true;

                int padding;
                int contentSize = AndroidUtilities.dp(80);

                int count = listAdapter.getItemCount();
                for (int a = 0; a < count; a++) {
                    View view = listAdapter.createView(context, a);
                    view.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    contentSize += view.getMeasuredHeight();
                }
                if (contentSize < availableHeight) {
                    padding = availableHeight - contentSize;
                } else {
                    if (currentType == TYPE_TRANSACTION) {
                        padding = availableHeight / 5;
                    } else {
                        padding = 0;
                    }
                }
                if (scrollView.getPaddingTop() != padding) {
                    int diff = scrollView.getPaddingTop() - padding;
                    scrollView.setPadding(0, padding, 0, 0);
                }
                ignoreLayout = false;
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                inLayout = true;
                super.onLayout(changed, l, t, r, b);
                inLayout = false;
                updateLayout(false);
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                int top = scrollOffsetY - backgroundPaddingTop;

                int height = getMeasuredHeight() + AndroidUtilities.dp(30) + backgroundPaddingTop;
                float rad = 1.0f;

                float r = AndroidUtilities.dp(12);
                if (top + backgroundPaddingTop < r) {
                    rad = 1.0f - Math.min(1.0f, (r - top - backgroundPaddingTop) / r);
                }

                if (Build.VERSION.SDK_INT >= 21) {
                    top += AndroidUtilities.statusBarHeight;
                    height -= AndroidUtilities.statusBarHeight;
                }

                shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
                shadowDrawable.draw(canvas);

                if (rad != 1.0f) {
                    backgroundPaint.setColor(Theme.getColor(Theme.key_dialogBackground));
                    rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                    canvas.drawRoundRect(rect, r * rad, r * rad, backgroundPaint);
                }

                int color1 = Theme.getColor(Theme.key_dialogBackground);
                int finalColor = Color.argb((int) (255 * actionBar.getAlpha()), (int) (Color.red(color1) * 0.8f), (int) (Color.green(color1) * 0.8f), (int) (Color.blue(color1) * 0.8f));
                backgroundPaint.setColor(finalColor);
                canvas.drawRect(backgroundPaddingLeft, 0, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaint);
            }
        };
        frameLayout.setWillNotDraw(false);
        containerView = frameLayout;
        setApplyTopPadding(false);
        setApplyBottomPadding(false);

        if (currentType == TYPE_SEND) {
            biometricPromtHelper = new BiometricPromtHelper(parentFragment);
        }

        listAdapter = new ListAdapter();

        scrollView = new NestedScrollView(context) {

            private View focusingView;

            @Override
            public void requestChildFocus(View child, View focused) {
                focusingView = focused;
                super.requestChildFocus(child, focused);
            }

            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
                if (linearLayout.getTop() != getPaddingTop()) {
                    return 0;
                }
                int delta = super.computeScrollDeltaToGetChildRectOnScreen(rect);
                int currentViewY = focusingView.getTop() - getScrollY() + rect.top + delta;
                int diff = ActionBar.getCurrentActionBarHeight() - currentViewY;
                if (diff > 0) {
                    delta -= diff + AndroidUtilities.dp(10);
                }
                return delta;
            }
        };
        scrollView.setClipToPadding(false);
        scrollView.setVerticalScrollBarEnabled(false);
        frameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 80));
        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> updateLayout(!inLayout));

        for (int a = 0, N = listAdapter.getItemCount(); a < N; a++) {
            View view = listAdapter.createView(context, a);
            linearLayout.addView(view, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            if (currentType == TYPE_TRANSACTION && a == commentRow) {
                view.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                view.setOnClickListener(v -> {
                    AndroidUtilities.addToClipboard(commentString);
                    Toast.makeText(v.getContext(), LocaleController.getString("TextCopied", R.string.TextCopied), Toast.LENGTH_SHORT).show();
                });
                view.setOnLongClickListener(v -> {
                    AndroidUtilities.addToClipboard(commentString);
                    Toast.makeText(v.getContext(), LocaleController.getString("TextCopied", R.string.TextCopied), Toast.LENGTH_SHORT).show();
                    return true;
                });
            }
        }

        actionBar = new ActionBar(context) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                containerView.invalidate();
            }
        };
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(Theme.getColor(Theme.key_dialogTextBlack), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_dialogButtonSelector), false);
        actionBar.setTitleColor(Theme.getColor(Theme.key_dialogTextBlack));
        actionBar.setOccupyStatusBar(false);
        actionBar.setAlpha(0.0f);
        if (currentType == TYPE_INVOICE) {
            actionBar.setTitle(LocaleController.getString("WalletCreateInvoiceTitle", R.string.WalletCreateInvoiceTitle));
        } else if (currentType == TYPE_TRANSACTION) {
            actionBar.setTitle(LocaleController.getString("WalletTransaction", R.string.WalletTransaction));
        } else {
            actionBar.setTitle(LocaleController.getString("WalletSendGrams", R.string.WalletSendGrams));
        }
        containerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    dismiss();
                }
            }
        });

        actionBarShadow = new View(context);
        actionBarShadow.setAlpha(0.0f);
        actionBarShadow.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        containerView.addView(actionBarShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1));

        shadow = new View(context);
        shadow.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        shadow.setAlpha(0.0f);
        containerView.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 80));

        TextView buttonTextView = new TextView(context);
        buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        if (currentType == TYPE_TRANSACTION) {
            buttonTextView.setText(LocaleController.getString("WalletTransactionSendGrams", R.string.WalletTransactionSendGrams));
        } else if (currentType == TYPE_SEND) {
            buttonTextView.setText(LocaleController.getString("WalletSendGrams", R.string.WalletSendGrams));
        } else {
            buttonTextView.setText(LocaleController.getString("WalletCreateInvoiceTitle", R.string.WalletCreateInvoiceTitle));
        }
        buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        frameLayout.addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 42, Gravity.LEFT | Gravity.BOTTOM, 16, 16, 16, 16));
        buttonTextView.setOnClickListener(v -> {
            if (currentType == TYPE_TRANSACTION) {
                delegate.openSendToAddress(recipientString.replace("\n", ""));
                dismiss();
            } else if (currentType == TYPE_SEND) {
                int codePoints = recipientString.codePointCount(0, recipientString.length());
                if (codePoints != 48 || !TonController.getInstance(currentAccount).isValidWalletAddress(recipientString)) {
                    onFieldError(recipientRow);
                    return;
                }
                if (amountValue <= 0 || amountValue > currentBalance) {
                    onFieldError(amountRow);
                    return;
                }
                if (walletAddress.replace("\n", "").equals(recipientString.replace("\n", ""))) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
                    builder.setTitle(LocaleController.getString("Wallet", R.string.Wallet));
                    builder.setMessage(LocaleController.getString("WalletSendSameWalletText", R.string.WalletSendSameWalletText));
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    builder.setPositiveButton(LocaleController.getString("WalletSendSameWalletProceed", R.string.WalletSendSameWalletProceed), (dialog, which) -> doSend());
                    builder.show();
                    return;
                }
                doSend();
            } else if (currentType == TYPE_INVOICE) {
                if (amountValue <= 0) {
                    onFieldError(amountRow);
                    return;
                }
                String url = "ton://transfer/" + walletAddress + "/?amount=" + amountValue;
                if (!TextUtils.isEmpty(commentString)) {
                    try {
                        url += "&text=" + URLEncoder.encode(commentString, "UTF-8").replaceAll("\\+", "%20");
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                dismiss();
                delegate.openInvoice(url, amountValue);
            }
        });
    }

    public void setRecipientString(String value, boolean hasWallet) {
        recipientString = value;
        hasWalletInBack = hasWallet;
        if (scrollView != null) {
            View view = linearLayout.getChildAt(recipientRow);
            if (view != null) {
                listAdapter.onBindViewHolder(view, recipientRow, listAdapter.getItemViewType(recipientRow));
            }
        }
    }

    public void parseTonUrl(Editable s, String url) {
        try {
            Uri uri = Uri.parse(url);
            String path = uri.getPath();
            String text = uri.getQueryParameter("text");
            String amount = uri.getQueryParameter("amount");
            if (!TextUtils.isEmpty(path) && path.length() > 1) {
                recipientString = path.replace("/", "");
                if (s == null && scrollView != null) {
                    View view = linearLayout.getChildAt(recipientRow);
                    if (view != null) {
                        listAdapter.onBindViewHolder(view, recipientRow, listAdapter.getItemViewType(recipientRow));
                    }
                }
            }
            if (!TextUtils.isEmpty(text)) {
                commentString = text;
                if (scrollView != null) {
                    View view = linearLayout.getChildAt(commentRow);
                    if (view != null) {
                        listAdapter.onBindViewHolder(view, commentRow, listAdapter.getItemViewType(commentRow));
                    }
                }
            }
            if (!TextUtils.isEmpty(amount)) {
                amountValue = Utilities.parseLong(amount);
            }
            if (scrollView != null) {
                View view = linearLayout.getChildAt(amountRow);
                if (view != null) {
                    if (!TextUtils.isEmpty(amount)) {
                        listAdapter.onBindViewHolder(view, amountRow, listAdapter.getItemViewType(amountRow));
                    }
                    PollEditTextCell pollEditTextCell = (PollEditTextCell) view;
                    EditTextBoldCursor editText = pollEditTextCell.getTextView();
                    editText.setSelection(editText.length());
                    editText.requestFocus();
                    AndroidUtilities.showKeyboard(editText);
                }
            }
            if (s != null) {
                s.replace(0, s.length(), recipientString);
            }
        } catch (Exception ignore) {

        }
    }

    public void onPause() {
        if (biometricPromtHelper != null) {
            biometricPromtHelper.onPause();
        }
    }

    public void onResume() {

    }

    public void setDelegate(WalletActionSheetDelegate walletActionSheetDelegate) {
        delegate = walletActionSheetDelegate;
    }

    private void updateLayout(boolean animated) {
        View child = scrollView.getChildAt(0);
        int top = child.getTop() - scrollView.getScrollY();
        int newOffset = 0;
        if (top >= 0) {
            newOffset = top;
        }
        boolean show = newOffset <= 0;
        if (show && actionBar.getTag() == null || !show && actionBar.getTag() != null) {
            actionBar.setTag(show ? 1 : null);
            if (actionBarAnimation != null) {
                actionBarAnimation.cancel();
                actionBarAnimation = null;
            }
            if (animated) {
                actionBarAnimation = new AnimatorSet();
                actionBarAnimation.setDuration(180);
                actionBarAnimation.playTogether(
                        ObjectAnimator.ofFloat(actionBar, View.ALPHA, show ? 1.0f : 0.0f),
                        ObjectAnimator.ofFloat(actionBarShadow, View.ALPHA, show ? 1.0f : 0.0f));
                actionBarAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        actionBarAnimation = null;
                    }
                });
                actionBarAnimation.start();
            } else {
                actionBar.setAlpha(show ? 1.0f : 0.0f);
                actionBarShadow.setAlpha(show ? 1.0f : 0.0f);
            }
        }
        if (scrollOffsetY != newOffset) {
            scrollOffsetY = newOffset;
            containerView.invalidate();
        }

        int b = child.getBottom();
        int h = scrollView.getMeasuredHeight();
        show = child.getBottom() - scrollView.getScrollY() > scrollView.getMeasuredHeight();
        if (show && shadow.getTag() == null || !show && shadow.getTag() != null) {
            shadow.setTag(show ? 1 : null);
            if (shadowAnimation != null) {
                shadowAnimation.cancel();
                shadowAnimation = null;
            }
            if (animated) {
                shadowAnimation = new AnimatorSet();
                shadowAnimation.setDuration(180);
                shadowAnimation.playTogether(ObjectAnimator.ofFloat(shadow, View.ALPHA, show ? 1.0f : 0.0f));
                shadowAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        shadowAnimation = null;
                    }
                });
                shadowAnimation.start();
            } else {
                shadow.setAlpha(show ? 1.0f : 0.0f);
            }
        }
    }

    @Override
    public void dismiss() {
        AndroidUtilities.hideKeyboard(getCurrentFocus());
        super.dismiss();
    }

    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == SEND_ACTIVITY_RESULT_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                dismiss();
                parentFragment.presentFragment(new WalletPasscodeActivity(false, null, walletAddress, recipientString, amountValue, commentString, hasWalletInBack));
            }
        }
    }

    private void doSend() {
        AlertDialog progressDialog = new AlertDialog(getContext(), 3);
        progressDialog.setCanCacnel(false);
        progressDialog.show();
        TonController.getInstance(currentAccount).getSendFee(walletAddress, recipientString, amountValue, commentString, fee -> {
            progressDialog.dismiss();

            Context context = getContext();
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(LocaleController.getString("WalletConfirmation", R.string.WalletConfirmation));
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("WalletConfirmationText", R.string.WalletConfirmationText, TonController.formatCurrency(amountValue))));

            FrameLayout frameLayout = new FrameLayout(context);
            frameLayout.setClipToPadding(false);

            TextView addressValueTextView = new TextView(context);
            addressValueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            addressValueTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmono.ttf"));
            addressValueTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            addressValueTextView.setGravity(Gravity.CENTER);
            addressValueTextView.setPadding(AndroidUtilities.dp(3), AndroidUtilities.dp(9), AndroidUtilities.dp(5), AndroidUtilities.dp(9));
            addressValueTextView.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(3), Theme.getColor(Theme.key_wallet_addressConfirmBackground)));
            StringBuilder stringBuilder = new StringBuilder(recipientString);
            stringBuilder.insert(stringBuilder.length() / 2, " \n ");
            stringBuilder.insert(0, " ");
            stringBuilder.insert(stringBuilder.length(), " ");
            addressValueTextView.setText(stringBuilder);
            frameLayout.addView(addressValueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            int height = 64;

            if (fee > 0) {
                TextView feeTextView = new TextView(context);
                feeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                feeTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
                feeTextView.setGravity(Gravity.CENTER);
                feeTextView.setText(LocaleController.formatString("WalletFee", R.string.WalletFee, TonController.formatCurrency(fee)));
                frameLayout.addView(feeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 67, 0, 0));
                height += 34;
            }
            builder.setView(frameLayout, height);

            builder.setPositiveButton(LocaleController.getString("WalletConfirm", R.string.WalletConfirm).toUpperCase(), (dialogInterface, i) -> {
                if (parentFragment == null || parentFragment.getParentActivity() == null) {
                    return;
                }
                switch (TonController.getInstance(currentAccount).getKeyProtectionType()) {
                    case TonController.KEY_PROTECTION_TYPE_LOCKSCREEN: {
                        if (Build.VERSION.SDK_INT >= 23) {
                            KeyguardManager keyguardManager = (KeyguardManager) ApplicationLoader.applicationContext.getSystemService(Context.KEYGUARD_SERVICE);
                            Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(LocaleController.getString("Wallet", R.string.Wallet), LocaleController.getString("WalletSendConfirmCredentials", R.string.WalletSendConfirmCredentials));
                            parentFragment.getParentActivity().startActivityForResult(intent, SEND_ACTIVITY_RESULT_CODE);
                        }
                        break;
                    }
                    case TonController.KEY_PROTECTION_TYPE_BIOMETRIC: {
                        biometricPromtHelper.promtWithCipher(TonController.getInstance(currentAccount).getCipherForDecrypt(), LocaleController.getString("WalletSendConfirmCredentials", R.string.WalletSendConfirmCredentials), (cipher) -> {
                            dismiss();
                            parentFragment.presentFragment(new WalletPasscodeActivity(false, cipher, walletAddress, recipientString, amountValue, commentString, hasWalletInBack));
                        });
                        break;
                    }
                    case TonController.KEY_PROTECTION_TYPE_NONE: {
                        AndroidUtilities.hideKeyboard(getCurrentFocus());
                        parentFragment.presentFragment(new WalletPasscodeActivity(true, null, walletAddress, recipientString, amountValue, commentString, hasWalletInBack));
                        dismiss();
                        break;
                    }
                }
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            builder.show();
        });
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    private void updateRows() {
        rowCount = 0;
        recipientHeaderRow = -1;
        recipientRow = -1;
        amountHeaderRow = -1;
        amountRow = -1;
        commentRow = -1;
        commentHeaderRow = -1;
        balanceRow = -1;
        dateRow = -1;
        invoiceInfoRow = -1;
        dateHeaderRow = -1;
        sendBalanceRow = -1;

        titleRow = rowCount++;
        if (currentType == TYPE_INVOICE) {
            invoiceInfoRow = rowCount++;
            amountHeaderRow = rowCount++;
            amountRow = rowCount++;
            commentRow = rowCount++;
        } else if (currentType == TYPE_TRANSACTION) {
            balanceRow = rowCount++;
            recipientHeaderRow = rowCount++;
            recipientRow = rowCount++;
            dateHeaderRow = rowCount++;
            dateRow = rowCount++;
            if (!TextUtils.isEmpty(commentString)) {
                commentHeaderRow = rowCount++;
                commentRow = rowCount++;
            }
        } else {
            recipientHeaderRow = rowCount++;
            recipientRow = rowCount++;
            amountHeaderRow = rowCount++;
            amountRow = rowCount++;
            sendBalanceRow = rowCount++;
            commentRow = rowCount++;
        }
    }

    private void onFieldError(int row) {
        View view = linearLayout.getChildAt(row);
        if (view == null) {
            return;
        }
        AndroidUtilities.shakeView(view, 2, 0);
        try {
            Vibrator v = (Vibrator) parentFragment.getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(200);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void setTextLeft(View cell) {
        if (currentType == TYPE_TRANSACTION) {
            return;
        }
        if (cell instanceof PollEditTextCell) {
            PollEditTextCell textCell = (PollEditTextCell) cell;
            int left = MAX_COMMENT_LENGTH - commentString.getBytes().length;
            if (left <= MAX_COMMENT_LENGTH - MAX_COMMENT_LENGTH * 0.7f) {
                textCell.setText2(String.format("%d", left));
                SimpleTextView textView = textCell.getTextView2();
                String key = left < 0 ? Theme.key_windowBackgroundWhiteRedText5 : Theme.key_windowBackgroundWhiteGrayText3;
                textView.setTextColor(Theme.getColor(key));
                textView.setTag(key);
            } else {
                textCell.setText2("");
            }
        } else if (cell instanceof TextInfoPrivacyCell) {
            if (currentBalance >= 0 && currentType == TYPE_SEND) {
                TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) cell;
                String key = amountValue > currentBalance ? Theme.key_windowBackgroundWhiteRedText5 : Theme.key_windowBackgroundWhiteBlueHeader;
                privacyCell.getTextView().setTag(key);
                privacyCell.getTextView().setTextColor(Theme.getColor(key));
            }
        }
    }

    private class ListAdapter {

        public int getItemCount() {
            return rowCount;
        }

        public void onBindViewHolder(View itemView, int position, int type) {
            switch (type) {
                case 0: {
                    HeaderCell cell = (HeaderCell) itemView;
                    if (position == recipientHeaderRow) {
                        if (currentType == TYPE_TRANSACTION) {
                            if (amountValue > 0) {
                                cell.setText(LocaleController.getString("WalletTransactionSender", R.string.WalletTransactionSender));
                            } else {
                                cell.setText(LocaleController.getString("WalletTransactionRecipient", R.string.WalletTransactionRecipient));
                            }
                        } else {
                            cell.setText(LocaleController.getString("WalletSendRecipient", R.string.WalletSendRecipient));
                        }
                    } else if (position == commentHeaderRow) {
                        cell.setText(LocaleController.getString("WalletTransactionComment", R.string.WalletTransactionComment));
                    } else if (position == dateHeaderRow) {
                        cell.setText(LocaleController.getString("WalletDate", R.string.WalletDate));
                    } else if (position == amountHeaderRow) {
                        cell.setText(LocaleController.getString("WalletAmount", R.string.WalletAmount));
                    }
                    break;
                }
                case 1: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) itemView;
                    if (position == invoiceInfoRow) {
                        cell.setText(LocaleController.getString("WalletInvoiceInfo", R.string.WalletInvoiceInfo));
                    }
                    break;
                }
                case 3: {
                    PollEditTextCell textCell = (PollEditTextCell) itemView;
                    if (position == dateRow) {
                        textCell.setTextAndHint(LocaleController.getInstance().formatterStats.format(currentDate * 1000), "", false);
                    } else if (position == commentRow) {
                        textCell.setTextAndHint(commentString, LocaleController.getString("WalletComment", R.string.WalletComment), false);
                    }
                    break;
                }
                case 4: {
                    PollEditTextCell textCell = (PollEditTextCell) itemView;
                    textCell.setText(amountValue != 0 ? TonController.formatCurrency(amountValue) : "", true);
                    break;
                }
                case 6: {
                    SendAddressCell textCell = (SendAddressCell) itemView;
                    textCell.setTextAndHint(recipientString, LocaleController.getString("WalletEnterWalletAddress", R.string.WalletEnterWalletAddress), false);
                    break;
                }
                case 7: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) itemView;
                    if (position == sendBalanceRow) {
                        TonApi.GenericAccountState state = TonController.getInstance(currentAccount).getCachedAccountState();
                        if (state != null) {
                            cell.setText(LocaleController.formatString("WalletSendBalance", R.string.WalletSendBalance, TonController.formatCurrency(currentBalance = TonController.getBalance(state))));
                        }
                    }
                    break;
                }
                case 8: {
                    BalanceCell cell = (BalanceCell) itemView;
                    cell.setBalance(amountValue, currentStorageFee, currentTransactionFee);
                    break;
                }
                case 9: {
                    TitleCell cell = (TitleCell) itemView;
                    if (position == titleRow) {
                        if (currentType == TYPE_INVOICE) {
                            cell.setText(LocaleController.getString("WalletCreateInvoiceTitle", R.string.WalletCreateInvoiceTitle));
                        } else if (currentType == TYPE_TRANSACTION) {
                            cell.setText(LocaleController.getString("WalletTransaction", R.string.WalletTransaction));
                        } else if (currentType == TYPE_SEND) {
                            cell.setText(LocaleController.getString("WalletSendGrams", R.string.WalletSendGrams));
                        }
                    }
                    break;
                }
            }
        }

        public View createView(Context context, int position) {
            int viewType = getItemViewType(position);
            View view;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(context, false, 21, 12, false);
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(context);
                    break;
                case 3: {
                    PollEditTextCell cell = new PollEditTextCell(context, null) {
                        @Override
                        protected void onAttachedToWindow() {
                            super.onAttachedToWindow();
                            setTextLeft(this);
                        }
                    };
                    EditTextBoldCursor editText = cell.getTextView();
                    if (currentType == TYPE_TRANSACTION) {
                        editText.setEnabled(false);
                        editText.setFocusable(false);
                        editText.setClickable(false);
                    } else {
                        editText.setBackground(Theme.createEditTextDrawable(context, true));
                        editText.setPadding(0, AndroidUtilities.dp(14), AndroidUtilities.dp(37), AndroidUtilities.dp(14));
                        cell.createErrorTextView();
                        editText.setFilters(new InputFilter[]{new ByteLengthFilter(MAX_COMMENT_LENGTH)});
                        cell.addTextWatcher(new TextWatcher() {

                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                            }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) {

                            }

                            @Override
                            public void afterTextChanged(Editable s) {
                                if (cell.getTag() != null) {
                                    return;
                                }
                                commentString = s.toString();
                                View view = linearLayout.getChildAt(commentRow);
                                if (view != null) {
                                    setTextLeft(view);
                                }
                            }
                        });
                    }
                    view = cell;
                    break;
                }
                case 4: {
                    PollEditTextCell cell = new PollEditTextCell(context, null) {
                        @Override
                        protected boolean drawDivider() {
                            return false;
                        }

                        @Override
                        protected void onEditTextDraw(EditTextBoldCursor editText, Canvas canvas) {
                            int left = 0;
                            int top = AndroidUtilities.dp(7);
                            Layout layout;
                            if (editText.length() > 0) {
                                layout = editText.getLayout();
                            } else {
                                layout = editText.getHintLayoutEx();
                            }
                            if (layout != null) {
                                left = (int) Math.ceil(layout.getLineWidth(0)) + AndroidUtilities.dp(6);
                            }
                            if (left != 0) {
                                float scale = 0.74f;
                                gemDrawable.setBounds(left, top, left + (int) (gemDrawable.getIntrinsicWidth() * scale), top + (int) (gemDrawable.getIntrinsicHeight() * scale));
                                gemDrawable.draw(canvas);
                            }
                        }

                        @Override
                        protected void onAttachedToWindow() {
                            super.onAttachedToWindow();
                            if (!wasFirstAttach && currentType != TYPE_TRANSACTION) {
                                if (recipientString.codePointCount(0, recipientString.length()) == 48) {
                                    getTextView().requestFocus();
                                }
                                wasFirstAttach = true;
                            }
                        }
                    };
                    EditTextBoldCursor editText = cell.getTextView();
                    cell.setShowNextButton(true);
                    editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    editText.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
                    editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 30);
                    editText.setBackground(Theme.createEditTextDrawable(context, true));
                    editText.setImeOptions(editText.getImeOptions() | EditorInfo.IME_ACTION_NEXT);
                    editText.setCursorSize(AndroidUtilities.dp(30));
                    editText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder("0.0");
                    stringBuilder.setSpan(new RelativeSizeSpan(0.73f), stringBuilder.length() - 1, stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    editText.setHintText(stringBuilder);
                    editText.setInputType(InputType.TYPE_CLASS_PHONE);
                    editText.setOnEditorActionListener((v, actionId, event) -> {
                        if (actionId == EditorInfo.IME_ACTION_NEXT) {
                            View commentView = linearLayout.getChildAt(commentRow);
                            if (commentView != null) {
                                PollEditTextCell editTextCell = (PollEditTextCell) commentView;
                                editTextCell.getTextView().requestFocus();
                            }
                            return true;
                        }
                        return false;
                    });
                    cell.addTextWatcher(new TextWatcher() {

                        private boolean ignoreTextChange;
                        private boolean adding;
                        private RelativeSizeSpan sizeSpan = new RelativeSizeSpan(0.73f);

                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                            adding = count == 0 && after == 1;
                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {

                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            if (ignoreTextChange || editText.getTag() != null) {
                                return;
                            }
                            int selection = editText.getSelectionStart();
                            ignoreTextChange = true;
                            int dotsCount = 0;
                            for (int a = 0; a < s.length(); a++) {
                                char c = s.charAt(a);
                                if (c == ',' || c == '#' || c == '*') {
                                    s.replace(a, a + 1, ".");
                                    c = '.';
                                }
                                if (c == '.' && dotsCount == 0) {
                                    dotsCount++;
                                } else if (c < '0' || c > '9') {
                                    s.delete(a, a + 1);
                                    a--;
                                }
                            }
                            if (s.length() > 0 && s.charAt(0) == '.') {
                                s.insert(0, "0");
                            }
                            if (adding && s.length() == 1 && s.charAt(0) == '0') {
                                s.replace(0, s.length(), "0.");
                            }
                            int index = TextUtils.indexOf(s, '.');
                            if (index >= 0) {
                                if (s.length() - index > 10) {
                                    s.delete(index + 10, s.length());
                                }
                                if (index > 9) {
                                    int countToDelete = index - 9;
                                    s.delete(9, 9 + countToDelete);
                                    index -= countToDelete;
                                }
                                String start = s.subSequence(0, index).toString();
                                String end = s.subSequence(index + 1, s.length()).toString();
                                amountValue = Utilities.parseLong(start) * 1000000000L + (int) (Utilities.parseLong(end) * Math.pow(10, 9 - end.length()));
                                s.setSpan(sizeSpan, index + 1, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            } else {
                                if (s.length() > 9) {
                                    s.delete(9, s.length());
                                }
                                amountValue = Utilities.parseLong(s.toString()) * 1000000000L;
                            }
                            ignoreTextChange = false;
                            View view = linearLayout.getChildAt(sendBalanceRow);
                            if (view != null) {
                                setTextLeft(view);
                            }
                        }
                    });
                    view = cell;
                    break;
                }
                case 6: {
                    view = new SendAddressCell(context);
                    break;
                }
                case 7: {
                    view = new TextInfoPrivacyCell(context) {
                        @Override
                        protected void onAttachedToWindow() {
                            super.onAttachedToWindow();
                            setTextLeft(this);
                        }
                    };
                    break;
                }
                case 8: {
                    view = new BalanceCell(context);
                    break;
                }
                case 9:
                default: {
                    view = new TitleCell(context);
                    break;
                }
            }
            onBindViewHolder(view, position, viewType);
            return view;
        }

        public int getItemViewType(int position) {
            if (position == recipientHeaderRow || position == commentHeaderRow || position == dateHeaderRow || position == amountHeaderRow) {
                return 0;
            } else if (position == invoiceInfoRow) {
                return 1;
            } else if (position == commentRow || position == dateRow) {
                return 3;
            } else if (position == amountRow) {
                return 4;
            } else if (position == recipientRow) {
                return 6;
            } else if (position == sendBalanceRow) {
                return 7;
            } else if (position == balanceRow) {
                return 8;
            } else {
                return 9;
            }
        }
    }
}
