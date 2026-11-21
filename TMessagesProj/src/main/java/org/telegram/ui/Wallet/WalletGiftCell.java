package org.telegram.ui.Wallet;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.tgnet.tl.TL_wallet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Gifts.GiftSheet;

/** Profile gift rendering with wallet sticker state, without changing profile interactions. */
class WalletGiftCell extends GiftSheet.GiftCell {
    TL_wallet.nftItem nft;
    WalletGiftStickers board;
    // The press bounce is drawn by the cell itself rather than set as scaleX/Y: the board owns the view's scale while
    // a gift is picked up or flies home, and ItemOptions draws its scrim copy through draw()/customDraw(), which skip scaleX/Y.
    private float drawnScale = 1;
    private boolean wasPressed;
    private ValueAnimator drawnScaleAnimator;

    WalletGiftCell(Context context, int account, Theme.ResourcesProvider provider) {
        super(context, account, provider);
        setStateListAnimator(null);
        // Without a background the hardware path calls dispatchDraw() directly and the drawn scale is never applied.
        setWillNotDraw(false);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (isPressed() == wasPressed) return;
        wasPressed = isPressed();
        animateDrawnScale(wasPressed ? .96f : 1, wasPressed ? 80 : 350, wasPressed ? null : new OvershootInterpolator(1.5f));
    }

    /** Moves the view's scale into the drawn one and eases it to 1, so the menu's copy of the cell doesn't jump. */
    void releaseIntoDrawnScale(long duration) {
        animate().cancel();
        setDrawnScale(getScaleX() * drawnScale);
        setScaleX(1); setScaleY(1);
        animateDrawnScale(1, duration, null);
    }

    /** Moves the drawn press scale into the view's scale, for the board to animate from there. */
    void takeDrawnScale() {
        if (drawnScaleAnimator != null) drawnScaleAnimator.cancel();
        if (drawnScale == 1) return;
        setScaleX(getScaleX() * drawnScale); setScaleY(getScaleY() * drawnScale);
        setDrawnScale(1);
    }

    private void animateDrawnScale(float to, long duration, TimeInterpolator interpolator) {
        if (drawnScaleAnimator != null) drawnScaleAnimator.cancel();
        drawnScaleAnimator = ValueAnimator.ofFloat(drawnScale, to);
        drawnScaleAnimator.addUpdateListener(a -> setDrawnScale((float) a.getAnimatedValue()));
        if (interpolator != null) drawnScaleAnimator.setInterpolator(interpolator);
        drawnScaleAnimator.setDuration(duration);
        drawnScaleAnimator.start();
    }

    private void setDrawnScale(float scale) {
        drawnScale = scale;
        invalidate();
        // Hidden under the menu's scrim, the cell's own invalidate is skipped; the copy still needs a frame.
        if (getParent() instanceof View) ((View) getParent()).invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        if (drawnScale == 1) { super.draw(canvas); return; }
        canvas.save();
        canvas.scale(drawnScale, drawnScale, getWidth() / 2f, getHeight() / 2f);
        super.draw(canvas);
        canvas.restore();
    }

    @Override
    public void customDraw(View view, Canvas canvas, float width, float height, float progress) {
        if (drawnScale == 1) { super.customDraw(view, canvas, width, height, progress); return; }
        canvas.save();
        canvas.scale(drawnScale, drawnScale, width / 2f, height / 2f);
        super.customDraw(view, canvas, width, height, progress);
        canvas.restore();
    }

    /** The learn hint lifts the gift off its tile: the board draws it in place of the cell's own image, under the ribbon. */
    void setHintDemo(boolean on) {
        final float alpha = on ? 0 : 1;
        if (imageView.getAlpha() != alpha) imageView.setAlpha(alpha);
        invalidate();
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        final boolean result = super.drawChild(canvas, child, drawingTime);
        if (child == card && board != null) board.drawHintDemo(canvas, this);
        return result;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (board != null) board.bind(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (board != null) board.unbind(this);
        super.onDetachedFromWindow();
    }

    public static class Factory extends UItem.UItemFactory<WalletGiftCell> {
        static { setup(new Factory()); }
        @Override
        public WalletGiftCell createView(Context context, RecyclerListView list, int account, int guid, Theme.ResourcesProvider provider) {
            return new WalletGiftCell(context, account, provider);
        }
        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView list) {
            WalletGiftCell cell = (WalletGiftCell) view;
            if (cell.board != null) cell.board.unbind(cell);
            if (cell.drawnScaleAnimator != null) cell.drawnScaleAnimator.cancel();
            cell.drawnScale = 1;
            cell.imageView.setAlpha(1);
            cell.nft = (TL_wallet.nftItem) item.object;
            cell.board = (WalletGiftStickers) item.object2;
            TL_stars.SavedStarGift gift = new TL_stars.SavedStarGift();
            gift.gift = cell.nft.gift;
            cell.setWalletStickerState(0, false);
            cell.setStarsGift(gift, true, false);
            if (cell.board != null) cell.board.bind(cell);
        }
        public static UItem of(TL_wallet.nftItem nft, WalletGiftStickers board) {
            UItem item = UItem.ofFactory(Factory.class).setSpanCount(1);
            item.object = nft;
            item.object2 = board;
            return item;
        }
        @Override
        public boolean equals(UItem a, UItem b) {
            return android.text.TextUtils.equals(((TL_wallet.nftItem) a.object).address, ((TL_wallet.nftItem) b.object).address);
        }
        @Override
        public boolean isClickable() { return true; }
    }
}
