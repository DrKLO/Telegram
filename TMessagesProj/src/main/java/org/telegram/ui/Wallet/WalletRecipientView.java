package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Wallet.WalletImportFragment.formatAddressTwoLines;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import java.util.Arrays;
import java.util.Random;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.ScaleStateListAnimator;

public class WalletRecipientView extends FrameLayout {
    private final Theme.ResourcesProvider resourcesProvider;
    private final FrameLayout content;
    private final AvatarDrawable addressInfoAvatarDrawable = new AvatarDrawable();
    private final BackupImageView addressInfoImage;
    private final TextView addressInfoName;
    private final AddressView addressInfoAddress;
    private final ImageView addressInfoIcon;
    private TLRPC.User recipientUser;
    private String address;

    public WalletRecipientView(Context context, Theme.ResourcesProvider resourcesProvider,
                               Runnable onOpenRecipient, Runnable onAddressCopied) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        content = new FrameLayout(context);
        content.setDuplicateParentStateEnabled(true);
        content.setPivotX(0);
        content.setPivotY(0);
        addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        content.setBackground(Theme.createRadSelectorDrawable(
            getThemedColor(Theme.key_windowBackgroundGray),
            Theme.blendOver(getThemedColor(Theme.key_windowBackgroundGray), getThemedColor(Theme.key_listSelector)),
            dp(32), dp(32)
        ));
        ScaleStateListAnimator.apply(this, .02f, 1.2f);
        addressInfoImage = new BackupImageView(context);
        addressInfoImage.setRoundRadius(dp(18));
        content.addView(addressInfoImage, LayoutHelper.createFrame(34, 34, Gravity.LEFT | Gravity.CENTER_VERTICAL, 15, 0, 0, 0));

        addressInfoName = new TextView(context);
        addressInfoName.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        addressInfoName.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        addressInfoName.setSingleLine();
        addressInfoName.setEllipsize(TextUtils.TruncateAt.END);
        addressInfoName.setTypeface(AndroidUtilities.bold());
        content.addView(addressInfoName, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 60, 7, 14 + 24 + 14, 0));

        addressInfoAddress = new AddressView(context);
        content.addView(addressInfoAddress, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 60, 26, 14 + 24 + 14, 0));

        addressInfoIcon = new ImageView(context);
        addressInfoIcon.setImageResource(R.drawable.msg2_help);
        addressInfoIcon.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayText6), PorterDuff.Mode.SRC_IN));
        addressInfoIcon.setScaleType(ImageView.ScaleType.CENTER);
        addressInfoIcon.setScaleX(0.9f);
        addressInfoIcon.setScaleY(0.9f);
        content.addView(addressInfoIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 14, 0));

        setOnClickListener(v -> {
            if (TextUtils.isEmpty(address)) return;
            if (recipientUser == null) return;

            final AlertDialog[] dialog = new AlertDialog[1];

            final LinearLayout layout = new LinearLayout(getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(dp(18), 0, dp(18), 0);

            if (recipientUser != null) {
                final String name = UserObject.getForcedFirstName(recipientUser);
                final LinearLayout top = new LinearLayout(getContext());
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setPadding(dp(4), 0, dp(4), 0);

                final BackupImageView topImageView = new BackupImageView(getContext());
                topImageView.setRoundRadius(dp(15));
                final AvatarDrawable topAvatarDrawable = new AvatarDrawable();
                topAvatarDrawable.setInfo(recipientUser);
                topImageView.setForUserOrChat(recipientUser, topAvatarDrawable);
                top.addView(topImageView, LayoutHelper.createLinear(30, 30, Gravity.CENTER_VERTICAL));
                final TextView title = new TextView(getContext());
                title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                title.setText(LocaleController.formatString(R.string.WalletUserWallet, name));
                title.setTypeface(AndroidUtilities.bold());
                title.setPadding(dp(4), 0, dp(4), 0);
                top.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 9, 0, 0, 0));
                layout.addView(top, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

                final LinkSpanDrawable.LinksTextView description = new LinkSpanDrawable.LinksTextView(getContext());
                description.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                description.setLinkTextColor(getThemedColor(Theme.key_chat_messageLinkIn));
                description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                description.setPadding(dp(4), 0, dp(4), 0);
                description.setText(AndroidUtilities.replaceSingleTag(LocaleController.formatString(R.string.WalletAddressLinkedToUser, UserObject.getUserName(recipientUser)), () -> {
                    dialog[0].dismiss();
                    onOpenRecipient.run();
                }));
                layout.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 14, 0, 0));
            }

            final FrameLayout addressView = new FrameLayout(getContext());
            addressView.setPadding(dp(4), 0, dp(4), 0);
            final TextView addressText = new TextView(getContext());
            addressText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addressText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            addressText.setText(formatAddressTwoLines(address));
            addressText.setPadding(dp(10), dp(12), dp(10), dp(12));
            addressText.setTypeface(AndroidUtilities.getTypeface("fonts/rmono.ttf"));
            addressText.setBackground(Theme.createRadSelectorDrawable(getThemedColor(Theme.key_windowBackgroundGray), Theme.blendOver(getThemedColor(Theme.key_windowBackgroundGray), getThemedColor(Theme.key_listSelector)), 10, 10));
            addressText.setGravity(Gravity.CENTER);
            addressView.addView(addressText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));
            layout.addView(addressView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 14, 0, 0));
            ScaleStateListAnimator.apply(addressText, .02f, 1.2f);
            addressText.setOnClickListener(w -> {
                dialog[0].dismiss();
                AndroidUtilities.addToClipboard(address);
                onAddressCopied.run();
            });

            dialog[0] = new AlertDialog.Builder(getContext(), resourcesProvider)
                .setView(layout)
                .setPositiveButton(getString(R.string.OK), null)
                .show();
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final FrameLayout.LayoutParams addressParams = (FrameLayout.LayoutParams) addressInfoAddress.getLayoutParams();
        final int requiredWidth = (int) Math.ceil(addressInfoAddress.getAddressWidth()) + 1
                + addressParams.leftMargin + addressParams.rightMargin;
        setMeasuredDimension(resolveSize(Math.max(getMeasuredWidth(), requiredWidth), widthMeasureSpec), getMeasuredHeight());
        final int contentWidth = Math.max(getMeasuredWidth(), requiredWidth);
        final float scale = getMeasuredWidth() / (float) contentWidth;
        content.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
        content.setScaleX(scale);
        content.setScaleY(scale);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int contentTop = Math.round((getMeasuredHeight() - content.getMeasuredHeight() * content.getScaleY()) / 2f);
        content.layout(0, contentTop, content.getMeasuredWidth(), contentTop + content.getMeasuredHeight());
    }

    public float getContentBottom() {
        return content.getY() + content.getPivotY() + (content.getHeight() - content.getPivotY()) * content.getScaleY();
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public void setRecipient(TLRPC.User user, String address) {
        final boolean recipientChanged = (recipientUser == null ? 0 : recipientUser.id) != (user == null ? 0 : user.id);
        this.recipientUser = user;
        this.address = address;
        setClickable(!TextUtils.isEmpty(address));
        if (recipientUser != null) {
            setClickable(true);
            setVisibility(View.VISIBLE);
            addressInfoAvatarDrawable.setInfo(recipientUser);
            addressInfoImage.setForUserOrChat(recipientUser, addressInfoAvatarDrawable);
            addressInfoName.setText(UserObject.getUserName(recipientUser));
            if (!TextUtils.isEmpty(address)) {
                addressInfoAddress.setAddress(address, recipientChanged);
                addressInfoIcon.setVisibility(View.VISIBLE);
            } else {
                addressInfoAddress.setAddress(null, recipientChanged);
                addressInfoIcon.setVisibility(View.GONE);
            }
        } else if (!TextUtils.isEmpty(address)) {
            setClickable(false);
            setVisibility(View.VISIBLE);
            addressInfoImage.setImageDrawable(new CombinedDrawable(
                Theme.createCircleDrawable(dp(34), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)),
                getContext().getResources().getDrawable(R.drawable.menu_gram_24).mutate()
            ));
            addressInfoName.setText(getString(R.string.WalletGramWalletAddress));
            addressInfoAddress.setAddress(address, recipientChanged);
            addressInfoIcon.setVisibility(View.GONE);
        } else {
            setClickable(false);
            setVisibility(View.GONE);
            addressInfoAddress.setAddress(null, true);
        }
    }

    private class AddressView extends View {
        private static final int COLUMN_DELAY = 18;
        private static final int SYMBOL_CHANGE_INTERVAL = 50;
        private static final int LAND_DURATION = 560;
        private static final String MATRIX_SYMBOLS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final char[] symbol = new char[1];
        private final Random random = new Random();
        private final int[] shadeSteps = new int[48];
        private final float[] shades = new float[48];
        private final float characterWidth;
        private final float lineHeight;
        private final float baseline;
        private final float textHeight;
        private final Paint.FontMetrics metrics;
        private String value;
        private float elapsed;
        private boolean pendingReveal;
        private ValueAnimator animator;

        AddressView(Context context) {
            super(context);
            paint.setTypeface(AndroidUtilities.getTypeface("fonts/rmono.ttf"));
            paint.setTextSize(dp(12));
            characterWidth = paint.measureText("U");
            metrics = paint.getFontMetrics();
            baseline = -metrics.top;
            lineHeight = metrics.descent - metrics.ascent;
            textHeight = metrics.bottom - metrics.top + lineHeight;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setContentDescription(getString(R.string.Loading));
        }

        void setAddress(String address, boolean restart) {
            address = TextUtils.isEmpty(address) ? null : address;
            if (!restart && TextUtils.equals(value, address)) {
                return;
            }
            stopAnimator();
            value = address;
            elapsed = 0;
            Arrays.fill(shadeSteps, -1);
            pendingReveal = value != null;
            setContentDescription(value == null ? getString(R.string.Loading) : value);
            requestLayout();
            startReveal();
            invalidate();
        }

        float getAddressWidth() {
            int count = value == null ? 24 : Math.min(24, value.length());
            return characterWidth * (count + Math.max(0, (count - 1) / 4));
        }

        private void startReveal() {
            if (!pendingReveal || !isAttachedToWindow() || !isShown()) {
                return;
            }
            pendingReveal = false;
            final int duration = getRevealDuration();
            if (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) {
                elapsed = duration;
                invalidate();
                return;
            }
            animator = ValueAnimator.ofFloat(0, duration);
            animator.setDuration(duration);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(animation -> {
                elapsed = (float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        private int getRevealDuration() {
            final int lastColumn = Math.max(0, Math.min(24, value.length()) - 1);
            return (lastColumn + lastColumn / 4) * COLUMN_DELAY + LAND_DURATION;
        }

        private void stopAnimator() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            startReveal();
        }

        @Override
        protected void onDetachedFromWindow() {
            stopAnimator();
            if (value != null && !pendingReveal) {
                elapsed = getRevealDuration();
            }
            super.onDetachedFromWindow();
        }

        @Override
        protected void onVisibilityChanged(View changedView, int visibility) {
            super.onVisibilityChanged(changedView, visibility);
            if (isShown()) {
                startReveal();
            } else if (animator != null) {
                stopAnimator();
                elapsed = value == null ? 0 : getRevealDuration();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(resolveSize((int) Math.ceil(getAddressWidth()), widthMeasureSpec),
                    resolveSize((int) Math.ceil(textHeight), heightMeasureSpec));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final int grayColor = getThemedColor(Theme.key_windowBackgroundWhiteGrayText6);
            final int blackColor = getThemedColor(Theme.key_windowBackgroundWhiteBlackText);
            final int count = value == null ? 48 : Math.min(48, value.length());
            for (int group = 0; group * 4 < count; group++) {
                final int first = group * 4;
                final int row = first / 24;
                final float x = (group % 6) * 5 * characterWidth;
                final float y = baseline + row * lineHeight;
                final float groupTime = elapsed - (group % 6) * 5 * COLUMN_DELAY;
                final float barAlpha = value == null ? 1 : 1 - clamp(groupTime / 180f);
                if (barAlpha > 0) {
                    paint.setColor(grayColor);
                    paint.setAlpha(Math.round(paint.getAlpha() * .22f * barAlpha));
                    final float centerY = y + (metrics.ascent + metrics.descent) / 2;
                    canvas.drawRoundRect(x, centerY - dp(2),
                            x + Math.min(4, count - first) * characterWidth, centerY + dp(2),
                            dp(2), dp(2), paint);
                }
                if (value == null) {
                    continue;
                }
                for (int j = 0; j < 4 && first + j < count; j++) {
                    final int index = first + j;
                    // Use the horizontal position, including group gaps, for both rows.
                    final int column = (group % 6) * 5 + j;
                    final float time = elapsed - column * COLUMN_DELAY;
                    if (time <= 0) {
                        continue;
                    }
                    final float progress = clamp(time / LAND_DURATION);
                    // Underdamped spring: drop into place, overshoot, then settle.
                    final float displacement = progress >= 1 ? 0 :
                            (float) (Math.exp(-8 * progress) * Math.cos(12 * progress));
                    final int symbolStep = (int) (time / SYMBOL_CHANGE_INTERVAL);
                    final boolean scrambling = time < 260;
                    symbol[0] = scrambling ? MATRIX_SYMBOLS.charAt(
                            (index * 17 + symbolStep * 13) % MATRIX_SYMBOLS.length()) : value.charAt(index);
                    if (scrambling && shadeSteps[index] != symbolStep) {
                        shadeSteps[index] = symbolStep;
                        shades[index] = random.nextFloat();
                    }
                    // Hold each random shade for the same interval as its scrambled symbol.
                    paint.setColor(scrambling ? ColorUtils.blendARGB(grayColor, blackColor, shades[index])
                            : (group + row) % 2 == 0 ? grayColor : blackColor);
                    paint.setAlpha(Math.round(paint.getAlpha() * clamp(time / 100f)));
                    final int save = canvas.save();
                    final float rowBoundary = baseline + metrics.descent + dp(1);
                    canvas.clipRect(0, row == 0 ? 0 : rowBoundary,
                            getWidth(), row == 0 ? rowBoundary : getHeight());
                    canvas.drawText(symbol, 0, 1, x + j * characterWidth,
                            y - dp(24) * displacement, paint);
                    canvas.restoreToCount(save);
                }
            }
        }

        private float clamp(float value) {
            return Math.max(0, Math.min(1, value));
        }
    }

}
