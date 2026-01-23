package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stars.StarsIntroActivity.formatTON;
import static org.telegram.ui.Stars.StarsIntroActivity.replaceDiamond;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.tlutils.AmountUtils;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ButtonSpan;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.TableView;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.TextHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Stars.BalanceCloud;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.TON.TONIntroActivity;

import java.util.ArrayList;

public class StakedDiceSheet extends BottomSheetWithRecyclerListView {

    private final BalanceCloud balanceCloud;

    private LinearLayout topView;
    private LinearLayout editView;

    @Override
    protected boolean isTouchOutside(float x, float y) {
        if (x >= balanceCloud.getX() && x <= balanceCloud.getX() + balanceCloud.getWidth() && y >= balanceCloud.getY() && y <= balanceCloud.getY() + balanceCloud.getHeight())
            return false;
        return super.isTouchOutside(x, y);
    }

    public StakedDiceSheet(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider, Utilities.Callback<Long> send) {
        super(context, null, true, false, false, ActionBarType.FADING, resourcesProvider);
        this.currentAccount = currentAccount;

        topPadding = 0.2f;
        smoothKeyboardAnimationEnabled = true;
        smoothKeyboardByBottom = true;

        balanceCloud = new BalanceCloud(context, currentAccount, AmountUtils.Currency.TON, resourcesProvider);
        balanceCloud.setScaleX(0.6f);
        balanceCloud.setScaleY(0.6f);
        balanceCloud.setAlpha(0.0f);
        container.addView(balanceCloud, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 48, 0, 0));
        ScaleStateListAnimator.apply(balanceCloud);
        balanceCloud.setOnClickListener(v -> {
            new StarsIntroActivity.StarsOptionsSheet(context, resourcesProvider).show();
        });

        final TLRPC.EmojiGameInfo stakeDiceInfo = MessagesController.getInstance(currentAccount).stakeDiceInfo;
        if (!(stakeDiceInfo instanceof TLRPC.TL_emojiGameDiceInfo)) return;
        final TLRPC.TL_emojiGameDiceInfo info = (TLRPC.TL_emojiGameDiceInfo) stakeDiceInfo;

        topView = new LinearLayout(context);
        topView.setOrientation(LinearLayout.VERTICAL);
        topView.setPadding(dp(16), dp(20), dp(16), dp(4));
        topView.setClipChildren(false);
        topView.setClipToPadding(false);

        ImageView topImageView = new ImageView(context);
        topImageView.setImageResource(R.drawable.dice6);
        topView.addView(topImageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));

        TextView titleView = TextHelper.makeTextView(context, 20, Theme.key_dialogTextBlack, true);
        titleView.setGravity(Gravity.CENTER);
        SpannableStringBuilder title = new SpannableStringBuilder(getString(R.string.StakeDiceTitle));
        title.append(" ");
        int betaIndex = title.length();
        title.append(getString(R.string.StakeDiceTitleBeta));
        title.setSpan(new ReplacementSpan() {
            final Text text = new Text(getString(R.string.StakeDiceTitleBeta), 12, AndroidUtilities.bold());
            final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
                return (int) (dp(16) + this.text.getCurrentWidth());
            }
            @Override
            public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
                final float cy = (top + bottom) / 2.0f + dp(1);
                bgPaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
                AndroidUtilities.rectTmp.set(x, cy - dp(9), x + dp(16) + this.text.getCurrentWidth(), cy + dp(9));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(9), dp(9), bgPaint);
                this.text.draw(canvas, x + dp(8), cy, 0xFFFFFFFF, 1.0f);
            }
        }, betaIndex, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        titleView.setText(title);
        topView.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 32, 0, 32, 8));

        TextView subtitleView = TextHelper.makeTextView(context, 14, Theme.key_dialogTextBlack, false);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setText(getString(R.string.StakeDiceText));
        topView.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 32, 0, 32, 12));

        LinearLayout tableLayout = new LinearLayout(context);
        tableLayout.setOrientation(LinearLayout.VERTICAL);

        TextView tableHeaderView = TextHelper.makeTextView(context, 14, Theme.key_windowBackgroundWhiteBlueHeader, true);
        tableHeaderView.setText(getString(R.string.StakeDiceReturns));
        tableLayout.addView(tableHeaderView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        TableView table = new TableView(context, resourcesProvider);
        tableLayout.addView(table, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        TableRow row1 = new TableRow(context);
        table.addView(row1);
        TableRow row2 = new TableRow(context);
        table.addView(row2);

        final int[] drawables = new int[] {
            R.drawable.dice1, R.drawable.dice2, R.drawable.dice3,
            R.drawable.dice4, R.drawable.dice5, R.drawable.dice6,
            R.drawable.dice6
        };
        Utilities.Callback2Return<Integer, Float, TableView.TableRowContent> cell = (n, x) -> {

            final LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);

            final LinearLayout imageLayout = new LinearLayout(context);
            layout.addView(imageLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

            final ImageView imageView = new ImageView(context);
            imageView.setImageResource(drawables[n - 1]);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageLayout.addView(imageView, LayoutHelper.createLinear(24, 24));

            if (n == 7) {
                for (int i = 0; i < 2; ++i) {
                    final ImageView imageView2 = new ImageView(context);
                    imageView2.setImageResource(drawables[n - 1]);
                    imageView2.setScaleType(ImageView.ScaleType.CENTER);
                    imageLayout.addView(imageView2, LayoutHelper.createLinear(24, 24));
                }
            }

            final TextView textView = new TextView(context);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            textView.setText("x" + (x <= 0 ? "0" : x));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textView.setGravity(Gravity.CENTER);
            layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));

            return new TableView.TableRowContent(table, layout, false);
        };

        if (info.params.size() == 7) {
            row1.addView(cell.run(1, info.params.get(0) / 1000f), new TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            row1.addView(cell.run(2, info.params.get(1) / 1000f), new TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            row1.addView(cell.run(3, info.params.get(2) / 1000f), new TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            row1.addView(cell.run(4, info.params.get(3) / 1000f), new TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

            row2.addView(cell.run(5, info.params.get(4) / 1000f), new TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            row2.addView(cell.run(6, info.params.get(5) / 1000f), new TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            row2.addView(cell.run(7, info.params.get(6) / 1000f),  new TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2f));
        }

        TextView tableInfoView = TextHelper.makeTextView(context, 14, Theme.key_windowBackgroundWhiteGrayText, false);
        tableInfoView.setGravity(Gravity.CENTER);
        {
            SpannableStringBuilder dice6Span = new SpannableStringBuilder("🎲");
            ColoredImageSpan span = new ColoredImageSpan(R.drawable.dice6);
            span.recolorDrawable = false;
            span.setScale(0.8f, 0.8f);
            dice6Span.setSpan(span, 0, dice6Span.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            CharSequence infoText = getString(R.string.StakeDiceReturnsInfo);
            infoText = AndroidUtilities.replaceMultipleCharSequence("🎲", infoText, dice6Span);
            tableInfoView.setText(infoText);
        }
        tableLayout.addView(tableInfoView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 16));

        topView.addView(tableLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 0, 8, 0));

        editView = new LinearLayout(context);
        editView.setOrientation(LinearLayout.VERTICAL);
        editView.setPadding(dp(42), dp(0), dp(42), dp(7));
        editView.setClipToPadding(false);

        final EditTextBoldCursor editText = new EditTextBoldCursor(context);
        final OutlineTextContainerView editTextContainer = new OutlineTextContainerView(context, resourcesProvider);
        editTextContainer.setForceForceUseCenter(true);
        editTextContainer.setText(getString(R.string.StakeDicePlaceholder));
        editTextContainer.setLeftPadding(dp(14 + 22));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editText.setBackground(null);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setMaxLines(1);
        int padding = AndroidUtilities.dp(16);
        editText.setPadding(dp(6), padding, padding, padding);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        editText.setTypeface(Typeface.DEFAULT);
        editText.setSelectAllOnFocus(true);
        editText.setHighlightColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight, resourcesProvider));
        editText.setHandlesColor(Theme.getColor(Theme.key_chat_TextSelectionCursor, resourcesProvider));
        editText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        editText.setOnFocusChangeListener((v, hasFocus) -> editTextContainer.animateSelection(hasFocus, !TextUtils.isEmpty(editText.getText())));
        LinearLayout editTextLayout = new LinearLayout(context);
        editTextLayout.setOrientation(LinearLayout.HORIZONTAL);
        ImageView starImage = new ImageView(context);
        starImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        starImage.setImageResource(R.drawable.diamond);
        editTextLayout.addView(starImage, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.LEFT | Gravity.CENTER_VERTICAL, 14, 0, 0, 0));
        editTextLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1, Gravity.FILL));
        editTextContainer.attachEditText(editText);
        editTextContainer.addView(editTextLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        editView.addView(editTextContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView subPriceView = new TextView(context);
        subPriceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        subPriceView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        editTextContainer.addView(subPriceView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 14, 0));

        long initialTon = 1_000_000_000L;
        if (info.prev_stake > 0) {
            initialTon = info.prev_stake;
        }
        editText.setText(formatTON(initialTon));
        subPriceView.setAlpha(1.0f);
        subPriceView.setText("≈" + BillingController.getInstance().formatCurrency((long) (initialTon / 1_000_000_000.0 * MessagesController.getInstance(currentAccount).config.tonUsdRate.get() * 100), "USD", 2));

        int[] shakeDp = new int[] { 2 };
        editTextContainer.animateSelection(false, !TextUtils.isEmpty(editText.getText()));
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            private boolean ignore;
            @Override
            public void afterTextChanged(Editable s) {
                if (ignore) return;

                double ton = 0;
                try {
                    ton = TextUtils.isEmpty(s) ? 0 : Double.parseDouble(s.toString());
                    if (ton > MessagesController.getInstance(currentAccount).tonStakeddiceStakeAmountMax / 1_000_000_000.0) {
                        ignore = true;
                        editText.setText(Double.toString(ton = MessagesController.getInstance(currentAccount).tonStakeddiceStakeAmountMax / 1_000_000_000.0));
                        editText.setSelection(editText.getText().length());
                        AndroidUtilities.shakeViewSpring(editTextContainer, shakeDp[0] = -shakeDp[0]);
                    } else if (ton > 0 && ton < MessagesController.getInstance(currentAccount).tonStakeddiceStakeAmountMin / 1_000_000_000.0) {
                        ignore = true;
                        editText.setText(Double.toString(ton = MessagesController.getInstance(currentAccount).tonStakeddiceStakeAmountMin / 1_000_000_000.0));
                        editText.setSelection(editText.getText().length());
                        AndroidUtilities.shakeViewSpring(editTextContainer, shakeDp[0] = -shakeDp[0]);
                    }
                } catch (Exception e) {
                    ignore = true;
                    editText.setText(ton <= 0 ? "" : Double.toString(ton));
                    editText.setSelection(editText.getText().length());
                }
                ignore = false;

                editTextContainer.animateSelection(editText.isFocused(), !TextUtils.isEmpty(editText.getText()));

                if (ton == 0) {
                    subPriceView.animate().alpha(0).start();
                    subPriceView.setText("");
                } else {
                    subPriceView.animate().alpha(1f).start();
                    subPriceView.setText("≈" + BillingController.getInstance().formatCurrency((long) (ton * MessagesController.getInstance(currentAccount).config.tonUsdRate.get() * 100), "USD", 2));
                }
            }
        });

        Utilities.CallbackReturn<Long, View> preset = ton -> {
            TextView textView = new TextView(context);
            textView.setGravity(Gravity.CENTER);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
            textView.setBackground(Theme.createRoundRectDrawable(dp(13), Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), 0.15f)));
            textView.setText(replaceDiamond(formatTON(ton) + " 💎", 0.75f));
            ScaleStateListAnimator.apply(textView);
            textView.setOnClickListener(v -> {
                editText.setText(formatTON(ton));
                editText.setSelection(editText.getText().length());
            });
            return textView;
        };

        long[] presets = MessagesController.getInstance(currentAccount).tonStakediceStakeSuggestedAmounts;
        for (int i = 0; i < Utilities.divCeil(presets.length, 3); ++i) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < Math.min(3, presets.length - i * 3); ++j) {
                row.addView(preset.run(presets[i*3 + j]), LayoutHelper.createLinear(0, 26, 1, Gravity.FILL_VERTICAL, 0, 0, j == 2 ? 0 : 6, 0));
            }
            editView.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 7, 0, 0));
        }

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
        SpannableStringBuilder sb = new SpannableStringBuilder("🎲");
        ColoredImageSpan rollImg = new ColoredImageSpan(R.drawable.mini_roll);
        rollImg.setTranslateY(dp(1));
        sb.setSpan(rollImg, 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("  ").append(getString(R.string.StakeDiceButton));
        button.setText(sb, false);
        button.setOnClickListener(v -> {
            final Editable s = editText.getText();
            double ton = 0;
            try {
                ton = TextUtils.isEmpty(s) ? 0 : Double.parseDouble(s.toString());
            } catch (Exception e) {
                return;
            }

            if (ton > MessagesController.getInstance(currentAccount).tonStakeddiceStakeAmountMax / 1_000_000_000.0) {
                editText.setText(Double.toString(ton = MessagesController.getInstance(currentAccount).tonStakeddiceStakeAmountMax / 1_000_000_000.0));
                editText.setSelection(editText.getText().length());
                AndroidUtilities.shakeViewSpring(editTextContainer, shakeDp[0] = -shakeDp[0]);
                return;
            } else if (!TextUtils.isEmpty(s) && ton < MessagesController.getInstance(currentAccount).tonStakeddiceStakeAmountMin / 1_000_000_000.0) {
                editText.setText(Double.toString(ton = MessagesController.getInstance(currentAccount).tonStakeddiceStakeAmountMin / 1_000_000_000.0));
                editText.setSelection(editText.getText().length());
                AndroidUtilities.shakeViewSpring(editTextContainer, shakeDp[0] = -shakeDp[0]);
                return;
            }

            final StarsController sc = StarsController.getInstance(currentAccount, true);
            if (sc.balance.toDouble() < ton) {
                new TONIntroActivity.StarsNeededSheet(
                    context,
                    resourcesProvider,
                    AmountUtils.Amount.fromNano((long) (ton * 1_000_000_000L), AmountUtils.Currency.TON),
                    true,
                    () -> {}
                );
                return;
            }

            send.run((long) (ton * 1_000_000_000L));
            dismiss();
        });

        FrameLayout buttonContainer = new FrameLayout(context);
        buttonContainer.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 16, 0, 16, 10));
        containerView.addView(buttonContainer, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(68));

        adapter.update(false);
    }

    private boolean isOpenAnimationEnd;

    @Override
    public void onOpenAnimationEnd() {
        super.onOpenAnimationEnd();
        isOpenAnimationEnd = true;
        checkBalanceCloudVisibility();
    }

    @Override
    public void onDismissAnimationStart() {
        super.onDismissAnimationStart();
        isOpenAnimationEnd = false;
        checkBalanceCloudVisibility();
    }

    private boolean balanceCloudVisible;

    @Override
    protected void onContainerTranslationYChanged(float translationY) {
        super.onContainerTranslationYChanged(translationY);
        checkBalanceCloudVisibility();
    }

    private void checkBalanceCloudVisibility() {
        final boolean balanceCloudVisible = isOpenAnimationEnd && !isDismissed() && !isKeyboardVisible();
        if (this.balanceCloudVisible != balanceCloudVisible)  {
            this.balanceCloudVisible = balanceCloudVisible;
            if (balanceCloud != null) {
                balanceCloud.setEnabled(balanceCloudVisible);
                balanceCloud.setClickable(balanceCloudVisible);
                balanceCloud.animate()
                    .scaleX(balanceCloudVisible ? 1f : 0.6f)
                    .scaleY(balanceCloudVisible ? 1f : 0.6f)
                    .alpha(balanceCloudVisible ? 1f : 0f)
                    .setDuration(180L)
                    .start();
            }
        }
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.StakeDiceTitle);
    }

    private UniversalAdapter adapter;
    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, this::fillItems, resourcesProvider);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (topView != null) {
            items.add(UItem.asCustom(topView));
        }
        if (editView != null) {
            items.add(UItem.asCustom(editView));
        }
    }

    public static void showStakeToast(BaseFragment fragment, int value, long stake, Utilities.Callback<Long> send) {
        if (fragment == null) fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) return;

        final BaseFragment f = fragment;

        final TLRPC.EmojiGameInfo stakeDiceInfo = MessagesController.getInstance(f.getCurrentAccount()).stakeDiceInfo;
        if (!(stakeDiceInfo instanceof TLRPC.TL_emojiGameDiceInfo)) {
            return;
        }
        final TLRPC.TL_emojiGameDiceInfo info = (TLRPC.TL_emojiGameDiceInfo) stakeDiceInfo;
        stake = info.prev_stake;

        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getContext(), fragment.getResourceProvider());

        layout.imageView.setScaleX(1.25f);
        layout.imageView.setScaleY(1.25f);
        if (value == 1) {
            layout.imageView.setImageResource(R.drawable.dice1);
        } else if (value == 2) {
            layout.imageView.setImageResource(R.drawable.dice2);
        } else if (value == 3) {
            layout.imageView.setImageResource(R.drawable.dice3);
        } else if (value == 4) {
            layout.imageView.setImageResource(R.drawable.dice4);
        } else if (value == 5) {
            layout.imageView.setImageResource(R.drawable.dice5);
        } else if (value == 6) {
            layout.imageView.setImageResource(R.drawable.dice6);
        } else {
            layout.imageView.setScaleX(0.8f);
            layout.imageView.setScaleY(0.8f);
            layout.imageView.setImageDrawable(Emoji.getEmojiBigDrawable("🎲"));
        }

        final SpannableStringBuilder sb = new SpannableStringBuilder(getString(R.string.StakeDiceToast));
        sb.append(StarsIntroActivity.formatTON(stake));
        sb.append("  ").append(ButtonSpan.make("change", () -> {
            new StakedDiceSheet(f.getContext(), f.getCurrentAccount(), f.getResourceProvider(), send).show();
        }, fragment.getResourceProvider()));
        AndroidUtilities.removeFromParent(layout.textView);
        layout.textView = new ButtonSpan.TextViewButtons(f.getContext());
        layout.textView.setSingleLine();
        layout.textView.setTypeface(Typeface.SANS_SERIF);
        layout.textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        layout.textView.setEllipsize(TextUtils.TruncateAt.END);
        layout.textView.setPadding(0, dp(8), 0, dp(8));
        layout.addView(layout.textView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 56, 0, 16, 0));
        layout.textView.setText(replaceDiamond(sb));
        layout.textView.setLinkTextColor(Theme.getColor(Theme.key_undo_cancelColor, f.getResourceProvider()));
        layout.setTextColor(Theme.getColor(Theme.key_undo_infoColor, f.getResourceProvider()));

        final long finalStake = stake;
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(2);
        layout.setButton(
            new Bulletin.UndoButton(fragment.getContext(), true, fragment.getResourceProvider())
                .setText(getString(R.string.StakeDiceToastButton))
                .setUndoAction(() -> send.run(finalStake))
        );
        BulletinFactory.of(fragment).create(layout, Bulletin.DURATION_LONG).show();
    }

}
