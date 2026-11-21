package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.StaticLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.WebFile;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

class ChatMessageLocation {

    private final ChatMessageCell cell;

    int currentMapProvider;
    Drawable loadingThumb;
    String currentUrl;
    WebFile currentWebFile;
    WebFile lastWebFile;
    private Drawable foreverDrawable;
    private int foreverDrawableColor = 0xFFFFFFFF;

    ChatMessageLocation(ChatMessageCell cell) {
        this.cell = cell;
    }

    void drawOverlay(Canvas canvas) {
        if (cell.docTitleLayout != null) {
            if (cell.currentMessageObject.isOutOwner()) {
                Theme.chat_locationTitlePaint.setColor(cell.getThemedColor(Theme.key_chat_messageTextOut));
                Theme.chat_locationAddressPaint.setColor(cell.getThemedColor(cell.isDrawSelectionBackground() ? Theme.key_chat_outVenueInfoSelectedText : Theme.key_chat_outVenueInfoText));
            } else {
                Theme.chat_locationTitlePaint.setColor(cell.getThemedColor(Theme.key_chat_messageTextIn));
                Theme.chat_locationAddressPaint.setColor(cell.getThemedColor(cell.isDrawSelectionBackground() ? Theme.key_chat_inVenueInfoSelectedText : Theme.key_chat_inVenueInfoText));
            }

            if (MessageObject.getMedia(cell.currentMessageObject.messageOwner) instanceof TLRPC.TL_messageMediaGeoLive) {
                int cy = (int) (cell.photoImage.getImageY2() + dp(30));
                if (!cell.locationExpired || cell.transitionParams.animateLocationIsExpired) {
                    cell.forceNotDrawTime = true;
                    float progress;
                    String text;
                    StaticLayout docTitleLayout = cell.docTitleLayout;
                    StaticLayout infoLayout = cell.infoLayout;
                    float alpha = 1f;
                    boolean forever = false;
                    if (cell.transitionParams.animateLocationIsExpired) {
                        progress = cell.transitionParams.lastDrawLocationExpireProgress;
                        text = cell.transitionParams.lastDrawLocationExpireText;
                        docTitleLayout = cell.transitionParams.lastDrawDocTitleLayout;
                        infoLayout = cell.transitionParams.lastDrawInfoLayout;
                        alpha = 1f - cell.transitionParams.animateChangeProgress;
                    } else if (MessageObject.getMedia(cell.currentMessageObject.messageOwner).period == 0x7fffffff) {
                        forever = true;
                        progress = 1.0f;
                        text = "";
                    } else {
                        progress = 1.0f - Math.abs(ConnectionsManager.getInstance(cell.currentAccount).getCurrentTime() - cell.currentMessageObject.messageOwner.date) / (float) MessageObject.getMedia(cell.currentMessageObject.messageOwner).period;
                        text = LocaleController.formatLocationLeftTime(Math.abs(MessageObject.getMedia(cell.currentMessageObject.messageOwner).period - (ConnectionsManager.getInstance(cell.currentAccount).getCurrentTime() - cell.currentMessageObject.messageOwner.date)));
                    }

                    cell.rect.set(cell.photoImage.getImageX2() - dp(43), cy - dp(15), cell.photoImage.getImageX2() - dp(13), cy + dp(15));
                    if (cell.currentMessageObject.isOutOwner()) {
                        Theme.chat_radialProgress2Paint.setColor(cell.getThemedColor(Theme.key_chat_outInstant));
                        Theme.chat_livePaint.setColor(cell.getThemedColor(Theme.key_chat_outInstant));
                    } else {
                        Theme.chat_radialProgress2Paint.setColor(cell.getThemedColor(Theme.key_chat_inInstant));
                        Theme.chat_livePaint.setColor(cell.getThemedColor(Theme.key_chat_inInstant));
                    }

                    int docTitleAlpha = Theme.chat_locationTitlePaint.getAlpha();
                    int infoAlpha = Theme.chat_locationAddressPaint.getAlpha();
                    int liveAplha = Theme.chat_livePaint.getAlpha();
                    if (alpha != 1f) {
                        Theme.chat_locationTitlePaint.setAlpha((int) (docTitleAlpha * alpha));
                        Theme.chat_locationAddressPaint.setAlpha((int) (infoAlpha * alpha));
                        Theme.chat_livePaint.setAlpha((int) (liveAplha * alpha));
                        canvas.save();
                        canvas.translate(0, -dp(50) * cell.transitionParams.animateChangeProgress);
                    }

                    Theme.chat_radialProgress2Paint.setAlpha((int) (50 * alpha));
                    canvas.drawCircle(cell.rect.centerX(), cell.rect.centerY(), dp(15), Theme.chat_radialProgress2Paint);
                    Theme.chat_radialProgress2Paint.setAlpha((int) (255 * alpha));
                    canvas.drawArc(cell.rect, -90, -360 * progress, false, Theme.chat_radialProgress2Paint);

                    if (forever) {
                        if (foreverDrawable == null) {
                            foreverDrawable = cell.getContext().getResources().getDrawable(R.drawable.filled_location_forever).mutate();
                        }
                        if (Theme.chat_livePaint.getColor() != foreverDrawableColor) {
                            foreverDrawable.setColorFilter(new PorterDuffColorFilter(foreverDrawableColor = Theme.chat_livePaint.getColor(), PorterDuff.Mode.SRC_IN));
                        }
                        foreverDrawable.setBounds(
                            (int) cell.rect.centerX() - foreverDrawable.getIntrinsicWidth() / 2,
                            (int) cell.rect.centerY() - foreverDrawable.getIntrinsicHeight() / 2,
                            (int) cell.rect.centerX() + foreverDrawable.getIntrinsicWidth() / 2,
                            (int) cell.rect.centerY() + foreverDrawable.getIntrinsicHeight() / 2
                        );
                        foreverDrawable.draw(canvas);
                    } else {
                        float w = Theme.chat_livePaint.measureText(text);
                        int len = text.length();
                        final float s2 = (len > 4 ? .75f : (len > 3 ? .85f : 1f));
                        canvas.save();
                        canvas.scale(s2, s2, cell.rect.centerX(), cell.rect.centerY());
                        canvas.drawText(text, cell.rect.centerX() - w / 2, cy + dp(4), Theme.chat_livePaint);
                        canvas.restore();
                    }

                    if (docTitleLayout != null && infoLayout != null) {
                        canvas.save();
                        canvas.translate(cell.photoImage.getImageX() + dp(10), cell.photoImage.getImageY2() + dp(10));
                        docTitleLayout.draw(canvas);
                        canvas.translate(0, dp(23));
                        infoLayout.draw(canvas);
                        canvas.restore();
                    }

                    if (alpha != 1f) {
                        Theme.chat_locationTitlePaint.setAlpha(docTitleAlpha);
                        Theme.chat_locationAddressPaint.setAlpha(infoAlpha);
                        Theme.chat_livePaint.setAlpha(liveAplha);
                        canvas.restore();
                    }

                    cell.transitionParams.lastDrawLocationExpireProgress = progress;
                    cell.transitionParams.lastDrawLocationExpireText = text;
                    cell.transitionParams.lastDrawDocTitleLayout = docTitleLayout;
                    cell.transitionParams.lastDrawInfoLayout = infoLayout;
                } else {
                    cell.transitionParams.lastDrawLocationExpireText = null;
                    cell.transitionParams.lastDrawDocTitleLayout = null;
                    cell.transitionParams.lastDrawInfoLayout = null;
                }

                float progress = cell.photoImage.isCrossfadingWithOldImage() ? 1 : cell.photoImage.getCurrentAlpha();
                if (progress > 0 && (cell.photoImage.hasNotThumb() || cell.photoImage.isCrossfadingWithOldImage())) {
                    int cx = (int) (cell.photoImage.getImageX() + cell.photoImage.getImageWidth() / 2 - dp(31));
                    cy = (int) (cell.photoImage.getImageY() + cell.photoImage.getImageHeight() / 2 - dp(38) - dp(16) * (1f - CubicBezierInterpolator.EASE_OUT_BACK.getInterpolation(progress)));

                    Drawable msgAvatarLiveLocation = cell.sharedResources.getAvatarLiveLocation();
                    cell.setDrawableBounds(msgAvatarLiveLocation, cx, cy);
                    msgAvatarLiveLocation.setAlpha((int) (255 * Math.min(1, progress * 5)));
                    msgAvatarLiveLocation.draw(canvas);

                    cell.locationImageReceiver.setImageCoords(cx + dp(5.0f), cy + dp(5.0f), dp(52), dp(52));
                    cell.locationImageReceiver.setAlpha(Math.min(1, progress * 5));
                    cell.locationImageReceiver.draw(canvas);
                }
            } else {
                canvas.save();
                canvas.translate(cell.photoImage.getImageX() + dp(6), cell.photoImage.getImageY2() + dp(8));
                cell.docTitleLayout.draw(canvas);
                if (cell.infoLayout != null) {
                    canvas.translate(0, dp(21));
                    cell.infoLayout.draw(canvas);
                }
                canvas.restore();
            }
        }
    }

    void drawMapPin(Canvas canvas) {
        Drawable redLocationIcon = cell.sharedResources.getRedLocationIcon();
        int w = (int) (redLocationIcon.getIntrinsicWidth() * 0.8f);
        int h = (int) (redLocationIcon.getIntrinsicHeight() * 0.8f);
        int x = (int) (cell.photoImage.getImageX() + (cell.photoImage.getImageWidth() - w) / 2);
        int y = (int) (cell.photoImage.getImageY() + (cell.photoImage.getImageHeight() / 2 - h) - dp(16) * (1f - CubicBezierInterpolator.EASE_OUT_BACK.getInterpolation(cell.photoImage.getCurrentAlpha())));
        redLocationIcon.setAlpha((int) (255 * Math.min(1, cell.photoImage.getCurrentAlpha() * 5)));
        redLocationIcon.setBounds(x, y, x + w, y + h);
        redLocationIcon.draw(canvas);
        if (cell.photoImage.getCurrentAlpha() < 1) {
            cell.invalidate();
        }
    }
}
