package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Region;
import android.os.SystemClock;
import android.graphics.LinearGradient;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.WebFile;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_wallet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.UploadingDotsSpannable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class TransactionCell extends FrameLayout implements Theme.Colorable {

    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final ImageView iconView;
    private final BackupImageView avatarView;
    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView dateView;
    private final TextView amountView;
    private final Drawable gramDrawable;
    private final Drawable giftDrawable;
    private final WalletTransactionAmountView transactionAmount;
    private final Paint pendingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path avatarCutout = new Path();
    private final GradientDrawable rowBackground = new GradientDrawable();
    private float pendingProgress;
    private float landingPress;
    private SpringAnimation pressSpring;
    private final WalletSendParticles sparks = new WalletSendParticles();
    private long clockStart;
    private ValueAnimator pendingAnimator;
    private float pendingTarget;
    private boolean liveAmountEligible;
    private boolean sending;
    private boolean flightHeld;
    private boolean sentRipplePlayed;
    private boolean sentRipplePending;
    private TL_wallet.walletTransaction boundTransaction;
    private TL_wallet.walletTransaction boundSource;

    private final LinearLayout nftLayout;
    private final BackupImageView nftImageView;
    private final LinearLayout nftTextLayout;
    private final TextView nftTitle;
    private final TextView nftText;

    private boolean needDivider;
    private boolean pending;
    private boolean incoming;
    private boolean expandedAddress;

    public TransactionCell(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        addView(iconView, LayoutHelper.createFrame(
                46, 46,
                Gravity.LEFT | Gravity.TOP,
                13, 12, 0, 15
        ));

        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(dp(23));
        addView(avatarView, LayoutHelper.createFrame(
                46, 46,
                Gravity.LEFT | Gravity.TOP,
                13, 12, 0, 15
        ));

        final LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setClipChildren(false);
        contentLayout.setClipToPadding(false);
        contentLayout.setOrientation(LinearLayout.HORIZONTAL);
        addView(contentLayout, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.FILL,
                71, 0, 20, 0
        ));

        final LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setGravity(Gravity.CENTER_VERTICAL);
        contentLayout.addView(textLayout, LayoutHelper.createLinear(
                0,
                LayoutHelper.WRAP_CONTENT,
                1.0f
        ));

        titleView = new TextView(context);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titleView.setIncludeFontPadding(false);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setMaxWidth(dp(120));
        textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 18, 0, 9, 0, 0));

        subtitleView = new TextView(context);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setIncludeFontPadding(false);
        subtitleView.setGravity(Gravity.CENTER_VERTICAL);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 16, 0, 2, 0, 0));

        dateView = new TextView(context);
        dateView.setSingleLine(true);
        dateView.setEllipsize(TextUtils.TruncateAt.END);
        dateView.setIncludeFontPadding(false);
        dateView.setGravity(Gravity.CENTER_VERTICAL);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textLayout.addView(dateView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 16, 0, 2, 0, 10));

        nftLayout = new LinearLayout(context);
        nftLayout.setOrientation(LinearLayout.HORIZONTAL);
        textLayout.addView(nftLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, -2, 0, 8));

        nftImageView = new BackupImageView(context);
        nftImageView.setRoundRadius(dp(8));
        nftLayout.addView(nftImageView, LayoutHelper.createLinear(40, 40, Gravity.LEFT | Gravity.TOP, 2, 2, 6, 2));

        nftTextLayout = new LinearLayout(context);
        nftTextLayout.setOrientation(LinearLayout.VERTICAL);
        nftLayout.addView(nftTextLayout, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, Gravity.LEFT | Gravity.TOP, 0, 2, 10, 2));

        nftTitle = new TextView(context);
        nftTitle.setTypeface(AndroidUtilities.bold());
        nftTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        nftTextLayout.addView(nftTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

        nftText = new TextView(context);
        nftText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        nftTextLayout.addView(nftText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

        transactionAmount = new WalletTransactionAmountView(context);
        amountView = transactionAmount.amount;
        gramDrawable = context.getResources().getDrawable(R.drawable.wallet_gram_small).mutate();
        gramDrawable.setBounds(0, 0, dp(16), dp(16));
        giftDrawable = context.getResources().getDrawable(R.drawable.wallet_nft).mutate();
        giftDrawable.setBounds(0, 0, dp(16), dp(16));
        contentLayout.addView(transactionAmount, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.MATCH_PARENT, 0, Gravity.TOP | Gravity.RIGHT, 8, 0, 0, 0));
        setClipChildren(false);
        setClipToPadding(false);
        rowBackground.setCornerRadius(dp(16));
        setBackground(rowBackground);

        updateColors();
    }

    private void setIconColor(IconBackgroundColors colors) {
        final ShapeDrawable iconBg = new ShapeDrawable(new OvalShape());
        iconBg.setShaderFactory(new ShapeDrawable.ShaderFactory() {
            @Override
            public Shader resize(int width, int height) {
                return new LinearGradient(
                        0, 0, 0, height,
                        colors.top, colors.bottom,
                        Shader.TileMode.CLAMP
                );
            }
        });
        iconView.setBackground(iconBg);
    }

    public void set(TL_wallet.walletTransaction transaction, SmartWalletAddressFormatter addressFormatter, boolean divider) {
        final TL_wallet.walletTransaction source = Factory.sourceOf(transaction);
        final boolean sameTransaction = boundTransaction != null && Factory.sameTransaction(boundTransaction, transaction);
        if (!sameTransaction) {
            flightHeld = false;
            sentRipplePlayed = false;
            sentRipplePending = false;
            clockStart = SystemClock.uptimeMillis();
            cancelLanding();
            sparks.clear();
        }
        boundSource = source;
        boundTransaction = transaction;
        sending = transaction.pending && !transaction.failed;
        liveAmountEligible = !transaction.incoming && !transaction.key_change && transaction.nft == null;
        pending = transaction.pending || transaction.failed;
        incoming = transaction.incoming;

        if (transaction.key_change) {
            avatarView.setVisibility(View.GONE);
            setIconColor(IconBackgroundColors.GRAY);
            iconView.setImageResource(R.drawable.wallet_transaction_key);
            titleView.setText(getString(R.string.WalletKeyUpdate));
            subtitleView.setText(TextUtils.isEmpty(transaction.peer.address) ? getString(R.string.WalletUnknown) : addressFormatter.format(transaction.peer.address));
        } else if (transaction.peer instanceof TL_wallet.walletTransactionPeerUser) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(((TL_wallet.walletTransactionPeerUser) transaction.peer).user_id);
            if (user != null) {
                avatarView.setVisibility(View.VISIBLE);
                avatarDrawable.setInfo(user);
                avatarView.setForUserOrChat(user, avatarDrawable);
                titleView.setText(UserObject.getUserName(user));
            } else {
                avatarView.setVisibility(View.GONE);
                setIconColor(incoming ? IconBackgroundColors.BLUE : IconBackgroundColors.GREEN);
                iconView.setImageResource(incoming ? R.drawable.wallet_transaction_in : R.drawable.wallet_transaction_out);
                titleView.setText(addressFormatter.format(transaction.peer.address));
            }
        } else if (transaction.peer instanceof TL_wallet.walletTransactionPeerChannel) {
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(transaction.peer.channel_id);
            if (chat != null) {
                avatarView.setVisibility(View.VISIBLE);
                avatarDrawable.setInfo(currentAccount, chat);
                avatarView.setForUserOrChat(chat, avatarDrawable);
                titleView.setText(chat.title);
            } else {
                avatarView.setVisibility(View.GONE);
                setIconColor(incoming ? IconBackgroundColors.BLUE : IconBackgroundColors.GREEN);
                iconView.setImageResource(incoming ? R.drawable.wallet_transaction_in : R.drawable.wallet_transaction_out);
                titleView.setText(!TextUtils.isEmpty(transaction.peer.domain) ? transaction.peer.domain : addressFormatter.format(transaction.peer.address));
            }
        } else if (transaction.peer instanceof TL_wallet.walletTransactionPeerTelegramGift) {
            avatarView.setVisibility(View.GONE);
            setIconColor(incoming ? IconBackgroundColors.GREEN : IconBackgroundColors.BLUE);
            iconView.setImageResource(incoming ? R.drawable.wallet_transaction_in : R.drawable.wallet_transaction_out);
            titleView.setText(getString(R.string.WalletTransactionTelegram));
        } else if (transaction.peer instanceof TL_wallet.walletTransactionPeerOnramp) {
            final TL_wallet.walletTransactionPeerOnramp peer = (TL_wallet.walletTransactionPeerOnramp) transaction.peer;
            avatarView.setVisibility(View.GONE);
            setIconColor(IconBackgroundColors.BLUE);
            if ("walt".equalsIgnoreCase(peer.provider_name) || "wallet".equalsIgnoreCase(peer.provider_name)) {
                iconView.setImageResource(R.drawable.wallet_transaction_walt);
                titleView.setText(getString(R.string.WalletTransactionTopUpCrypto));
            } else {
                iconView.setImageResource(R.drawable.wallet_transaction_topup);
                titleView.setText(getString(R.string.WalletTransactionTopUp));
            }
            subtitleView.setText(CurrenciesController.capitalizeFirstLetter(peer.provider_name, Locale.getDefault()));
        } else {
            // Plain address peers have no avatar: outgoing transfers use the green icon like gift transfers.
            avatarView.setVisibility(View.GONE);
            setIconColor(incoming ? IconBackgroundColors.BLUE : IconBackgroundColors.GREEN);
            iconView.setImageResource(incoming ? R.drawable.wallet_transaction_in : R.drawable.wallet_transaction_out);
            if (!TextUtils.isEmpty(transaction.peer.domain)) {
                titleView.setText(transaction.peer.domain);
            } else {
                titleView.setText(TextUtils.isEmpty(transaction.peer.address) ? getString(R.string.WalletUnknown) : addressFormatter.format(transaction.peer.address));
            }
        }
        final String address = transaction.peer.address;
        expandedAddress = address != null && TextUtils.equals(titleView.getText(), addressFormatter.format(address)) && titleView.length() > 9;
        titleView.setSingleLine(!expandedAddress);
        titleView.setEllipsize(expandedAddress ? null : TextUtils.TruncateAt.MIDDLE);
        titleView.setMaxWidth(expandedAddress ? Integer.MAX_VALUE : dp(120));
        titleView.getLayoutParams().height = expandedAddress ? LayoutHelper.WRAP_CONTENT : dp(18);
        titleView.requestLayout();
        if (transaction.failed) {
            subtitleView.setText(getString(R.string.WalletFailedTransfer));
        } else if (transaction.key_change || transaction.peer instanceof TL_wallet.walletTransactionPeerOnramp) {

        } else if (transaction.peer instanceof TL_wallet.walletTransactionPeerTelegramGift && ((TL_wallet.walletTransactionPeerTelegramGift) transaction.peer).own_profile) {
            subtitleView.setText(incoming ? getString(R.string.WalletMovedFromProfile) : getString(R.string.WalletMovedToProfile));
        } else if (transaction.nft != null) {
            subtitleView.setText(incoming ? getString(R.string.WalletIncomingTransferNft) : getString(R.string.WalletOutgoingTransferNft));
        } else {
            subtitleView.setText(incoming ? getString(R.string.WalletIncomingTransfer) : getString(R.string.WalletOutgoingTransfer));
        }
        if (transaction.pending) {
            dateView.setText(UploadingDotsSpannable.replace(getString(R.string.WalletSending), dateView));
        } else {
            dateView.setText(transaction.date == 0 ? "" : LocaleController.formatShortDateTime(transaction.date));
        }

        if (transaction.nft != null) {
            nftLayout.setVisibility(View.VISIBLE);
            final int thumbColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
            final Drawable thumbIcon = getContext().getResources().getDrawable(R.drawable.wallet_nft_placeholder).mutate();
            thumbIcon.setColorFilter(new PorterDuffColorFilter(thumbColor, PorterDuff.Mode.SRC_IN));
            final CombinedDrawable thumb = new CombinedDrawable(Theme.createRoundRectDrawable(dp(8), Theme.multAlpha(thumbColor, 0.10f)), thumbIcon);
            final TLRPC.WebDocument imageDocument = transaction.nft.image_small != null ? transaction.nft.image_small : transaction.nft.image != null ? transaction.nft.image : transaction.nft.image_small;
            if (imageDocument != null) {
                final ImageLocation image = ImageLocation.getForWebFile(WebFile.createWithWebDocument(imageDocument));
                nftImageView.setImage(image, "46_46", thumb, transaction.nft);
            } else {
                nftImageView.setImageDrawable(thumb);
            }
            nftTitle.setText(transaction.nft.name);
            nftText.setText(LocaleController.getString(transaction.nft.isPhoneNumber() ? R.string.WalletCollectibleNumber : transaction.nft.isUsername() ? R.string.WalletCollectibleUsername : transaction.nft.isGift() ? R.string.WalletCollectibleGift : R.string.WalletCollectible));
        } else {
            nftLayout.setVisibility(View.GONE);
            nftImageView.clearImage();
        }

        if (transaction.amount == 0 && transaction.key_change) {
            amountView.setVisibility(View.GONE);
        } else if (transaction.nft != null) {
            amountView.setVisibility(View.VISIBLE);
            amountView.setCompoundDrawablesRelative(null, null, giftDrawable, null);
            if (incoming) {
                giftDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_color_green, resourcesProvider), PorterDuff.Mode.SRC_IN));
                amountView.setText(LocaleController.getString(R.string.WalletIncomingCollectibleAmount));
            } else {
                giftDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), PorterDuff.Mode.SRC_IN));
                amountView.setText(LocaleController.getString(R.string.WalletOutgoingCollectibleAmount));
            }
        } else {
            amountView.setVisibility(View.VISIBLE);
            amountView.setCompoundDrawablesRelative(null, null, gramDrawable, null);
            amountView.setText(formatAmount(incoming, transaction.amount));
        }

        transactionAmount.setVisibility(amountView.getVisibility());
        transactionAmount.setAmount(amountView.getText(), transaction.nft != null ? giftDrawable : gramDrawable);
        transactionAmount.setFlying(flightHeld);
        animatePending(sending || flightHeld ? 1f : 0f, sameTransaction);
        needDivider = divider;
        updateColors();
        invalidate();
    }

    public static class SmartWalletAddressFormatter {
        private final HashSet<String> addresses = new HashSet<>();
        private final HashMap<String, String> formattedAddresses = new HashMap<>();

        public void add(String address) {
            if (address != null && !address.isEmpty()) {
                addresses.add(address);
            }
        }

        public void done() {
            formattedAddresses.clear();
            resolve(new ArrayList<>(addresses), 4);
        }

        private void resolve(List<String> addresses, int length) {
            final HashMap<String, ArrayList<String>> groups = new HashMap<>();
            for (String address : addresses) {
                final String shortened = shorten(address, length);
                ArrayList<String> group = groups.get(shortened);
                if (group == null) {
                    groups.put(shortened, group = new ArrayList<>());
                }
                group.add(address);
            }
            for (ArrayList<String> group : groups.values()) {
                if (group.size() == 1) {
                    final String address = group.get(0);
                    formattedAddresses.put(address, shorten(address, length));
                } else {
                    // Expand every colliding address, independent of insertion order.
                    resolve(group, length + 1);
                }
            }
        }

        private static String shorten(String address, int length) {
            if (address.length() <= length * 2 + 1) return address;
            return address.substring(0, length) + "…" + address.substring(address.length() - length);
        }

        public void clear() {
            addresses.clear();
            formattedAddresses.clear();
        }

        public String format(String address) {
            if (address == null) return null;
            final String formatted = formattedAddresses.get(address);
            // An address outside the finalized set has not been checked for collisions.
            return formatted == null ? address : formatted;
        }
    }

    @Override
    public void updateColors() {
        updatePendingBackground();
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        dateView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        final int amountColor = pending && !sending
                ? Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider)
                : incoming
                  ? Theme.getColor(Theme.key_color_green, resourcesProvider)
                  : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        transactionAmount.setColor(amountColor);
        nftLayout.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider)));
        nftTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        nftText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
    }

    boolean isRaised() { return pendingProgress > 0 || sparks.isActive(); }

    private void updatePendingBackground() {
        rowBackground.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), pendingProgress));
    }

    boolean matches(TL_wallet.walletTransaction transaction) {
        return boundTransaction != null && Factory.sameTransaction(boundTransaction, transaction);
    }

    WalletDiamondView getPendingDiamond() { return transactionAmount.getDiamond(); }

    void holdForFlight(boolean hold) {
        flightHeld = hold;
        transactionAmount.setFlying(hold);
        animatePending(sending || hold ? 1f : 0f, !hold);
    }

    private void animatePending(float target, boolean animated) {
        if (animated && pendingAnimator != null && pendingTarget == target) return;
        pendingTarget = target;
        if (pendingAnimator != null) {
            pendingAnimator.cancel();
            pendingAnimator = null;
        }
        // Confirmation can bind a temporarily detached RecyclerView child. Keep
        // the diamond until attachment so its completion is visible on screen.
        if (animated && target == 0 && pendingProgress > 0 && !isAttachedToWindow()) {
            return;
        }
        if (!animated || pendingProgress == target) {
            setPendingProgress(target);
            return;
        }
        if (target == 0 && pendingProgress > 0 && boundTransaction != null && !boundTransaction.failed) {
            WalletDiamondView gem = getPendingDiamond();
            if (gem != null) {
                android.graphics.RectF at = WalletSendTransition.bounds(gem, this);
                sparks.celebrate(at.centerX(), at.centerY());
                sentRipplePending = !sentRipplePlayed && liveAmountEligible;
            }
        }
        pendingAnimator = ValueAnimator.ofFloat(pendingProgress, target);
        pendingAnimator.setDuration(260);
        pendingAnimator.setInterpolator(new PathInterpolator(.4f, 0f, .2f, 1f));
        pendingAnimator.addUpdateListener(a -> setPendingProgress((float) a.getAnimatedValue()));
        pendingAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) { pendingAnimator = null; }
        });
        pendingAnimator.start();
    }

    private void setPendingProgress(float value) {
        pendingProgress = value;
        updatePendingBackground();
        applyLandingPress();
        setElevation(dp(8) * value);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            setOutlineAmbientShadowColor(0x590B2A4A);
            setOutlineSpotShadowColor(0x590B2A4A);
        }
        transactionAmount.setPendingProgress(liveAmountEligible ? value : 0f);
        invalidate();
    }

    void playLanding(float velocityDpPerSecond) {
        cancelLanding();
        // A velocity impulse starts at the current position. One damped spring
        // absorbs the impact continuously, without a separate push-down tween.
        pressSpring = new SpringAnimation(new FloatValueHolder(0));
        pressSpring.setSpring(new SpringForce(0).setDampingRatio(.65f).setStiffness(200f));
        // This value is normalized, not pixels. The default one-pixel threshold
        // would consider the spring at rest before the impact becomes visible.
        pressSpring.setMinimumVisibleChange(.001f);
        pressSpring.setStartVelocity(Math.max(20f, Math.min(36f, velocityDpPerSecond * .04f)));
        pressSpring.addUpdateListener((a, value, velocity) -> {
            landingPress = value;
            applyLandingPress();
        });
        pressSpring.start();
    }

    private void applyLandingPress() {
        float scale = 1f + .035f * pendingProgress;
        setScaleX(scale * (1f + .012f * landingPress));
        setScaleY(scale * (1f - .04f * landingPress));
        setTranslationY(dp(4) * landingPress);
        transactionAmount.setLandingPress(landingPress);
    }

    private void cancelLanding() {
        if (pressSpring != null) { pressSpring.cancel(); pressSpring = null; }
        landingPress = 0;
        applyLandingPress();
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (pendingProgress <= 0 || child != avatarView && child != iconView) {
            return super.drawChild(canvas, child, drawingTime);
        }
        int save = canvas.save();
        avatarCutout.rewind();
        avatarCutout.addCircle(dp(52), dp(51), dp(10) * pendingProgress, Path.Direction.CW);
        canvas.clipPath(avatarCutout, Region.Op.DIFFERENCE);
        boolean result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(save);
        return result;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (sentRipplePending && isAttachedToWindow() && getWindowVisibility() == VISIBLE
                && getRootView() instanceof ViewGroup) {
            final WalletDiamondView gem = getPendingDiamond();
            if (gem != null && gem.getWidth() > 0 && gem.getHeight() > 0) {
                sentRipplePending = false;
                sentRipplePlayed = true;
                // Wait for drawing: binding may run while the row is detached or
                // before RecyclerView has positioned it in the window.
                android.graphics.RectF origin = WalletSendTransition.bounds(gem, (ViewGroup) getRootView());
                LaunchActivity.makeRipple(origin.centerX(), origin.centerY(), 1.5f);
            }
        }
        sparks.draw(canvas);
        if (sparks.isActive()) postInvalidateOnAnimation();
        if (pendingProgress <= 0) return;
        final float p = pendingProgress;
        final long time = SystemClock.uptimeMillis() - clockStart;
        final int blue = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        pendingPaint.setShader(null);
        pendingPaint.setStyle(Paint.Style.FILL);
        pendingPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        pendingPaint.setAlpha(Math.round(255 * p));
        canvas.drawCircle(dp(52), dp(51), dp(8) * p, pendingPaint);
        pendingPaint.setColor(blue);
        pendingPaint.setAlpha(Math.round(255 * p));
        pendingPaint.setStrokeWidth(dp(1.6f));
        pendingPaint.setStrokeCap(Paint.Cap.ROUND);
        pendingPaint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(dp(52), dp(51), dp(7) * p, pendingPaint);
        drawClockHand(canvas, (time % 1000) * .36f, dp(4.6f) * p);
        drawClockHand(canvas, (time % 6000) * .06f + 120, dp(3.2f) * p);
        final float wave = (time % 1600) / 1600f;
        final float band = getWidth() * .5f;
        final float x = -band + wave * (getWidth() + 2 * band);
        pendingPaint.setShader(new LinearGradient(x - band / 2, 0, x + band / 2, 0,
                new int[]{blue & 0x00ffffff, blue, 0xFF8FE6FF, blue & 0x00ffffff},
                new float[]{0f, .45f, .6f, 1f}, Shader.TileMode.CLAMP));
        pendingPaint.setAlpha(Math.round(150 * p * (float) Math.sin(wave * Math.PI)));
        pendingPaint.setStrokeWidth(dp(1.5f));
        canvas.drawRoundRect(dp(1), dp(1), getWidth() - dp(1), getHeight() - dp(1), dp(16), dp(16), pendingPaint);
        pendingPaint.setShader(null);
        if (isAttachedToWindow() && isShown() && getWindowVisibility() == VISIBLE) postInvalidateOnAnimation();
    }

    private void drawClockHand(Canvas canvas, float degrees, float length) {
        final double angle = Math.toRadians(degrees - 90);
        canvas.drawLine(dp(52), dp(51), dp(52) + (float) Math.cos(angle) * length,
                dp(51) + (float) Math.sin(angle) * length, pendingPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelLanding();
        sparks.clear();
        if (pendingAnimator != null) {
            pendingAnimator.cancel();
            pendingAnimator = null;
        }
        flightHeld = false;
        transactionAmount.setFlying(false);
        // RecyclerView can detach/rebind the same row during a confirmation diff.
        // Preserve its visual progress rather than snapping to the new model state.
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animatePending(sending || flightHeld ? 1f : 0f, true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            heightMeasureSpec
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (needDivider && pendingProgress < 1f) {
            pendingPaint.setStyle(Paint.Style.FILL);
            pendingPaint.setColor(Theme.dividerPaint.getColor());
            pendingPaint.setAlpha(Math.round(Theme.dividerPaint.getAlpha() * (1f - pendingProgress)));
            canvas.drawRect(
                    LocaleController.isRTL ? 0 : dp(71),
                    getHeight() - 1,
                    getWidth() - (LocaleController.isRTL ? dp(71) : 0),
                    getHeight(),
                    pendingPaint
            );
        }
    }

    private static CharSequence formatAmount(boolean incoming, long grams) {
        final long gramsAbs = Math.abs(grams);
        final SpannableStringBuilder amount = GramWallet.formatGramsScaled(gramsAbs, 11.0f / 14.0f);
        if (gramsAbs != 0 && incoming) {
            amount.insert(0, "+");
        } else if (gramsAbs != 0 && !incoming) {
            amount.insert(0, "–");
        }
        return amount;
    }

    public static class Factory extends UItem.UItemFactory<TransactionCell> {
        static { setup(new Factory()); }

        private static class TransactionSnapshot extends TL_wallet.walletTransaction {
            final TL_wallet.walletTransaction source;

            TransactionSnapshot(TL_wallet.walletTransaction source) {
                this.source = source;
            }
        }

        static TL_wallet.walletTransaction sourceOf(TL_wallet.walletTransaction transaction) {
            return transaction instanceof TransactionSnapshot ? ((TransactionSnapshot) transaction).source : transaction;
        }

        static boolean sameTransaction(TL_wallet.walletTransaction a, TL_wallet.walletTransaction b) {
            final TL_wallet.walletTransaction sourceA = sourceOf(a);
            final TL_wallet.walletTransaction sourceB = sourceOf(b);
            // Snapshots keep old presentation state, but identity must also use the
            // live local object: confirmation adds its ID/hashes before history
            // replaces it with the server object, sometimes between two UI diffs.
            return sourceA == sourceB
                    || GramWallet.transactionMatches(a, b)
                    || GramWallet.transactionMatches(sourceA, sourceB);
        }

        @Override
        public TransactionCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new TransactionCell(context, currentAccount, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((TransactionCell) view).set((TL_wallet.walletTransaction) item.object, (SmartWalletAddressFormatter) item.object2, divider);
        }

        public static UItem of(TL_wallet.walletTransaction transaction, SmartWalletAddressFormatter addressFormatter) {
            final UItem item = UItem.ofFactory(Factory.class);
            // RPC and history updates mutate transactions in place. Keep the old row's
            // state independent so the diff sees pending, fee, and peer changes.
            final TL_wallet.walletTransaction snapshot = new TransactionSnapshot(transaction);
            snapshot.set(transaction);
            snapshot.id = transaction.id;
            snapshot.random_id = transaction.random_id;
            snapshot.gasless = transaction.gasless;
            snapshot.gaslessMessageBodyHash = transaction.gaslessMessageBodyHash;
            snapshot.normalMessageBodyHash = transaction.normalMessageBodyHash;
            snapshot.nft = transaction.nft;
            item.object = snapshot;
            item.object2 = addressFormatter;
            // Snapshot the text: the shared formatter may change before the next diff.
            item.text = transaction.peer == null ? null : addressFormatter.format(transaction.peer.address);
            return item;
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            if (a.object instanceof TL_wallet.walletTransaction && b.object instanceof TL_wallet.walletTransaction) {
                final TL_wallet.walletTransaction A = (TL_wallet.walletTransaction) a.object;
                final TL_wallet.walletTransaction B = (TL_wallet.walletTransaction) b.object;
                return sameTransaction(A, B);
            }
            return false;
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            if (a.object instanceof TL_wallet.walletTransaction && b.object instanceof TL_wallet.walletTransaction) {
                final TL_wallet.walletTransaction A = (TL_wallet.walletTransaction) a.object;
                final TL_wallet.walletTransaction B = (TL_wallet.walletTransaction) b.object;
                return A.equals(B);
            }
            return false;
        }

        @Override
        public boolean isClickable() {
            return true;
        }
    }

}
