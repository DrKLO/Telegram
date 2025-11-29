package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.GiftAuctionController;
import org.telegram.messenger.R;
import org.telegram.messenger.utils.CountdownTimer;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BlurredFrameLayout;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Gifts.ActiveAuctionsSheet;
import org.telegram.ui.Gifts.AuctionBidSheet;

import java.util.ArrayList;
import java.util.List;

public class ActiveGiftAuctionsHintCell extends BlurredFrameLayout implements GiftAuctionController.OnActiveAuctionsUpdateListeners {
    private final int currentAccount;


    private final AnimatedTextView titleTextView;
    private final AnimatedTextView messageTextView;
    private final CountDown timerView;

    public ActiveGiftAuctionsHintCell(@NonNull Context context, SizeNotifierFrameLayout sizeNotifierFrameLayout, int currentAccount) {
        super(context, sizeNotifierFrameLayout);
        this.currentAccount = currentAccount;


        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        titleTextView = new AnimatedTextView(context);
        titleTextView.setTextSize(dp(14));
        titleTextView.setTypeface(AndroidUtilities.bold());
        layout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 18));

        messageTextView = new AnimatedTextView(context);
        messageTextView.setTextSize(dp(13));
        layout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 17));

        timerView = new CountDown(context, currentAccount);
        timerView.updateTimer(299);

        addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 16, 0, 92, 0));
        addView(timerView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 11, 0));

        updateColors();


        setOnClickListener(this::onClick);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY));
    }

    public void updateColors() {
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        messageTextView.setTextColor(Theme.getColor(isOutbid ? Theme.key_text_RedBold : Theme.key_windowBackgroundWhiteGrayText));
        invalidate();
    }



    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        GiftAuctionController.getInstance(currentAccount).subscribeToActiveAuctionsUpdates(this);
        List<GiftAuctionController.Auction> auctions = GiftAuctionController.getInstance(currentAccount).getActiveAuctions();
        onActiveAuctionsUpdate(auctions);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        GiftAuctionController.getInstance(currentAccount).unsubscribeFromActiveAuctionsUpdates(this);
    }



    private List<GiftAuctionController.Auction> activeAuctions = new ArrayList<>();

    @Override
    public void onActiveAuctionsUpdate(List<GiftAuctionController.Auction> auctions) {
        activeAuctions = new ArrayList<>(auctions);

        if (activeAuctions.size() == 1) {
            int nextRoundAt = 0;
            for (GiftAuctionController.Auction auction : auctions) {
                if (auction.auctionStateActive != null) {
                    nextRoundAt = Math.max(nextRoundAt, auction.auctionStateActive.next_round_at);
                }
            }
            timerView.start(nextRoundAt);
        } else {
            timerView.stop();
            timerView.textView.setText(getString(R.string.Gift2AuctionPriceView), true);
        }

        update(true);
    }

    private void update(boolean animated) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();

        final int count = activeAuctions.size();
        if (count == 0) {
            return;
        }

        boolean outbid = false;

        for (int a = 0; a < count; a++) {
            final GiftAuctionController.Auction auction = activeAuctions.get(a);
            if (auction.giftDocumentId != 0) {
                ssb.append("*");
                ssb.setSpan(
                    new AnimatedEmojiSpan(auction.giftDocumentId, titleTextView.getPaint().getFontMetricsInt()),
                    ssb.length() - 1, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            GiftAuctionController.Auction.BidStatus status = auction.getBidStatus();
            outbid |= status == GiftAuctionController.Auction.BidStatus.OUTBID
                || status == GiftAuctionController.Auction.BidStatus.RETURNED;
        }
        ssb.append(' ');
        ssb.append(count == 1 ?
            getString(R.string.Gift2ActiveAuctionsActiveAuctionTitle):
            formatString(R.string.Gift2ActiveAuctionsActiveAuctionsTitle, count));

        titleTextView.setText(ssb, animated);

        isOutbid = false;
        if (outbid) {
            messageTextView.setText(getString(R.string.Gift2ActiveAuctionsActiveStatusOutbid));
            isOutbid = true;
        } else {
            if (count > 1) {
                messageTextView.setText(getString(R.string.Gift2ActiveAuctionsActiveStatusWinningAll));
            } else {
                final GiftAuctionController.Auction auction = activeAuctions.get(0);
                final int myPlace = auction.getApproximatedMyPlace();

                String str;
                if (myPlace == 1) {
                    str = getString(R.string.Gift2ActiveAuctionsActiveStatusWinning1Place);
                } else if (myPlace == 2) {
                    str = getString(R.string.Gift2ActiveAuctionsActiveStatusWinning2Place);
                } else if (myPlace == 3) {
                    str = getString(R.string.Gift2ActiveAuctionsActiveStatusWinning3Place);
                } else {
                    str = formatString(R.string.Gift2ActiveAuctionsActiveStatusWinningOtherPlace, myPlace);
                }

                messageTextView.setText(formatString(R.string.Gift2ActiveAuctionsActiveStatusWinningOne, str));
            }
        }

        updateColors();
    }

    private boolean isOutbid;

    private void onClick(View ignoredView) {
        if (activeAuctions.size() == 1) {
            new AuctionBidSheet(getContext(), null, null, activeAuctions.get(0)).show();
        } else {
            new ActiveAuctionsSheet(getContext(), null).show();
        }
    }


    @SuppressLint("ViewConstructor")
    public static class CountDown extends FrameLayout {
        public final AnimatedTextView.AnimatedTextDrawable textView;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final CountdownTimer timer = new CountdownTimer(this::updateTimer);
        private final Drawable drawable;
        private final int currentAccount;
        private int endTime;

        public CountDown(Context context, int currentAccount) {
            super(context);
            this.currentAccount = currentAccount;

            drawable = context.getResources().getDrawable(R.drawable.filled_gift_sell_24).mutate();
            textView = new AnimatedTextView.AnimatedTextDrawable();
            textView.setOverrideFullWidth(AndroidUtilities.displaySize.x);
            textView.setCallback(this);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setTextSize(dp(14));
            textView.setTextColor(Color.WHITE);
            textView.setGravity(Gravity.LEFT);

            fillPaint.setShader(new LinearGradient(0, 0, dp(72), 0, new int[]{0xff329bde, 0xff66c1fb}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP));
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == textView || super.verifyDrawable(who);
        }

        public void start(int endTime) {
            this.endTime = endTime;

            if (isAttachedToWindow()) {
                final int diff = Math.max(0, endTime - ConnectionsManager.getInstance(currentAccount).getCurrentTime());
                timer.start(diff);
                updateTimer(diff);
            }
        }

        public void stop() {
            endTime = 0;
            timer.stop();
        }

        private void updateTimer(long value) {
            if (value == 0) {
                textView.setText(getString(R.string.Gift2AuctionPriceView));
            } else {
                textView.setText(AndroidUtilities.formatDurationNoHours((int) value, false), isAttachedToWindow());
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            start(endTime);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            timer.stop();
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            final int l = getMeasuredWidth() - dp(8) - (int) textView.getCurrentWidth();

            final int o = l - dp(30);
            canvas.save();
            canvas.translate(o, 0);
            canvas.drawRoundRect(0, 0, getWidth() - o, getHeight(), dp(14), dp(14), fillPaint);
            canvas.restore();

            textView.setBounds(l, 0, getMeasuredWidth() - dp(8), getMeasuredHeight() - dp(1));
            textView.draw(canvas);

            drawable.setBounds(l + dp(8 - 30), dp(5), l + dp(8 + 18 - 30), dp(5 + 18));
            drawable.draw(canvas);

            super.dispatchDraw(canvas);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(LayoutHelper.measureSpecExactlyDp(172), LayoutHelper.measureSpecExactlyDp(28));
        }
    }
}
