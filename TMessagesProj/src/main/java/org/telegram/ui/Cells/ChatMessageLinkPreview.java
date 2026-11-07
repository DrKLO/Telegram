/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.MessageObject.getMedia;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.StateSet;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.utils.tlutils.TLKeyboardHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.ReplyMessageLine;
import org.telegram.ui.Components.URLSpanBotCommand;
import org.telegram.ui.Components.spoilers.SpoilerEffect;
import org.telegram.ui.Stories.recorder.DominantColors;

class ChatMessageLinkPreview {

    private final ChatMessageCell cell;

    Drawable gradientDrawable;
    int photosCountWidth;
    int authorLayoutWidth, authorLayoutLeft;
    int titleLayoutWidth, titleLayoutLeft;
    StaticLayout photosCountLayout;
    StaticLayout authorLayout;
    int imageBackgroundColor;
    float imageBackgroundIntensity;
    int imageBackgroundGradientColor1;
    int imageBackgroundGradientColor2;
    int imageBackgroundGradientColor3;
    int imageBackgroundGradientRotation = 45;
    LinearGradient gradientShader;
    MotionBackgroundDrawable motionBackgroundDrawable;
    int imageBackgroundSideWidth;
    private LoadingDrawable instantButtonLoading;
    private Path instantLinkArrowPath;
    private Paint instantLinkArrowPaint;
    boolean pressed;
    boolean gamePressed;
    ButtonBounce bounce;
    Drawable selector;

    ChatMessageLinkPreview(ChatMessageCell cell) {
        this.cell = cell;
    }

    void drawPreview(Canvas canvas, float alpha) {
        if (!cell.currentMessageObject.isSponsored() && !cell.currentMessageObject.isUnsupported() && !cell.hasLinkPreview && !cell.hasGamePreview && !cell.hasInvoicePreview) {
            return;
        }
        int restoreCount = canvas.getSaveCount();

        final int backgroundWidth = (int) (cell.backgroundWidth + (cell.transitionParams != null ? - cell.transitionParams.deltaLeft + cell.transitionParams.deltaRight : 0));
        final float right = cell.getBackgroundDrawableRight() + (cell.transitionParams != null ? cell.transitionParams.deltaRight : 0) - dp(10 + (cell.currentMessageObject.isOutOwner() && !cell.mediaBackground && !cell.drawPinnedBottom ? 6 : 0)) - cell.getExtraTextX();

        int startY;
        int linkPreviewYDiff = 0;
        int linkX;
        if (cell.hasGamePreview) {
            startY = dp(14) + cell.namesOffset;
            linkX = cell.unmovedTextX - dp(10);
        } else if (cell.hasInvoicePreview) {
            startY = dp(14) + cell.namesOffset;
            linkX = cell.unmovedTextX + dp(1);
        } else if (cell.currentMessageObject.isSponsored()) {
            startY = cell.linkPreviewY - dp(2);
            linkX = cell.unmovedTextX + dp(1);
        } else {
            if (cell.currentMessageObject.isOutOwner()) {
                linkX = cell.backgroundDrawableLeft + dp(12) + cell.getExtraTextX();
                if (cell.currentMessageObject.type == MessageObject.TYPE_EMOJIS) {
                    linkX -= Math.max(0, linkX + Math.max(cell.replyNameWidth, cell.replyTextWidth) + dp(14) - AndroidUtilities.displaySize.x);
                }
            } else {
                if (cell.mediaBackground) {
                    linkX = cell.backgroundDrawableLeft + dp(12) + cell.getExtraTextX();
                } else {
                    linkX = cell.backgroundDrawableLeft + dp(cell.drawPinnedBottom ? 12 : 18) + cell.getExtraTextX();
                }
            }
            linkX += cell.transitionParams.deltaLeft;
            linkX -= dp(1.33f);

            startY = cell.linkPreviewY;
            if (cell.transitionParams.animateLinkPreviewY) {
                final int animatedStartY = AndroidUtilities.lerp(cell.transitionParams.animateFromLinkPreviewY, startY, cell.transitionParams.animateChangeProgress);
                linkPreviewYDiff = animatedStartY - startY;
                startY = animatedStartY;
            }
        }

        final int width = (int) (right - linkX);
        int linkPreviewY = startY;
        int smallImageStartY = 0;

        int linkPreviewHeight = cell.linkPreviewHeight;
        if (cell.transitionParams != null && cell.transitionParams.animateLinkPreviewHeight) {
            linkPreviewHeight = AndroidUtilities.lerp(cell.transitionParams.animateFromLinkPreviewHeight, linkPreviewHeight, cell.transitionParams.animateChangeProgress);
        }

        if (cell.linkLine == null) {
            cell.linkLine = new ReplyMessageLine(cell);
        }
        Theme.chat_replyNamePaint.setColor(cell.linkLine.check(cell.currentMessageObject, cell.currentUser, cell.currentChat, cell.resourcesProvider, ReplyMessageLine.TYPE_LINK));
        cell.linkLine.setEmojiAlpha(cell.drawInstantViewType == 23 || cell.drawInstantViewType == 24 || cell.drawInstantViewType == 28 ? 0.5f : 1f);

        final boolean drawPhotoImageBeforeBefore = cell.currentMessageObject.isSponsored() && !cell.isSmallImage;
        final boolean drawPhotoImageBefore = !drawPhotoImageBeforeBefore && cell.drawInstantView && (cell.drawInstantViewType != 26 && cell.drawInstantViewType != 9 && cell.drawInstantViewType != 2 && cell.drawInstantViewType != 13 && cell.drawInstantViewType != 11 && cell.drawInstantViewType != 25 && cell.drawInstantViewType != 1 && cell.drawInstantViewType != 29 && cell.drawInstantViewType != 18 && cell.drawInstantViewType != 22) || cell.drawInstantViewType == 6 && imageBackgroundColor != 0;
        final boolean drawPhotoImageAfter = !drawPhotoImageBeforeBefore && (!cell.drawInstantView || cell.drawInstantViewType == 9 || cell.drawInstantViewType == 2 || cell.drawInstantViewType == 11 || cell.drawInstantViewType == 25 || cell.drawInstantViewType == 13 || cell.drawInstantViewType == 1 || cell.drawInstantViewType == 29 || cell.drawInstantViewType == 18 || cell.drawInstantViewType == 22 || cell.drawInstantViewType == cell.INSTANT_BUTTON_TYPE_PROFILE || cell.isSmallImage || cell.drawInstantViewType == 26);

        boolean restore = false;
        boolean drawInstantButtonInside = false;
        boolean loading = cell.delegate != null && cell.delegate.isProgressLoading(cell, ChatActivity.PROGRESS_INSTANT);
        if (!cell.hasInvoicePreview && !cell.currentMessageObject.isGiveawayOrGiveawayResults() && !cell.currentMessageObject.isUnsupported()) {
            drawInstantButtonInside = true;

            if (bounce == null) {
                bounce = new ButtonBounce(cell, 2.0f, 2.0f);
            }
            canvas.save();
            final float s = bounce.getScale(0.01f);
            canvas.scale(s, s, linkX + width / 2f, linkPreviewY + (linkPreviewHeight + (drawInstantButtonInside && cell.drawInstantView ? dp(42) : 0)) / 2f);
            restore = true;

            AndroidUtilities.rectTmp.set(linkX, linkPreviewY - dp(6), linkX + width, linkPreviewY + linkPreviewHeight + (drawInstantButtonInside && cell.drawInstantView ? dp(42) : 0));
            cell.linkLine.setLoading(loading);
            float rad = (float) Math.floor(SharedConfig.bubbleRadius / (cell.currentMessageObject.isSponsored() ? 2f : 3f));
            cell.linkLine
                .offsetEmoji(0, drawPhotoImageBefore ? (1f - cell.isSmallImage()) * (dp(18) + cell.photoImage.getImageHeight() + (cell.siteNameLayout != null ? cell.siteNameLayout.getLineBottom(cell.siteNameLayout.getLineCount() - 1) : 0)) : 0)
                .drawBackground(canvas, AndroidUtilities.rectTmp, rad, rad, rad, alpha);

            int rippleColor = cell.linkLine.getBackgroundColor();
            if (selector == null) {
                selector = Theme.createRadSelectorDrawable(cell.linkPreviewSelectorColor = rippleColor, 0, 0);
                selector.setCallback(cell);
                Theme.setMaskDrawableRad(selector, 3, 6, 6, 3);
                selector.setCallback(cell);
            } else if (cell.linkPreviewSelectorColor != rippleColor) {
                Theme.setSelectorDrawableColor(selector, cell.linkPreviewSelectorColor = rippleColor, true);
            }
            selector.setBounds((int) AndroidUtilities.rectTmp.left, (int) AndroidUtilities.rectTmp.top, (int) AndroidUtilities.rectTmp.right, (int) AndroidUtilities.rectTmp.bottom);
            selector.draw(canvas);

            cell.linkLine.drawLine(canvas, AndroidUtilities.rectTmp, alpha);
            cell.linkLine.drawLoadingBackground(canvas, AndroidUtilities.rectTmp, rad, rad, rad, alpha);
        }

        if (cell.drawPhotoImage && drawPhotoImageBeforeBefore) {
            if (linkPreviewY != startY) {
                linkPreviewY += dp(2);
            }

            final int photoWidth = width - dp(17);
            if (cell.isSmallImage) {
                cell.photoImage.setImageX(linkX + width - cell.photoImage.getImageWidth() - dp(6));
                cell.photoImage.setImageY(smallImageStartY);
            } else {
                cell.photoImage.setImageX(linkX + (cell.hasInvoicePreview ? -dp(6.3f) : dp(10)));
                cell.photoImage.setImageY(linkPreviewY);
            }
            if (cell.transitionParams != null && cell.transitionParams.animateSmallImage) {
                if (!cell.isSmallImage && cell.documentAttachType != cell.DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                    cell.photoImage.setImageWidth((int) AndroidUtilities.lerp(cell.transitionParams.photoImageFromWidth, photoWidth, cell.transitionParams.animateChangeProgress));
                }
                cell.photoImage.setImageX(AndroidUtilities.lerp(cell.transitionParams.photoImageFromCenterX, cell.photoImage.getCenterX(), cell.transitionParams.animateChangeProgress) - cell.photoImage.getImageWidth() / 2f);
                cell.photoImage.setImageY(AndroidUtilities.lerp(cell.transitionParams.photoImageFromCenterY, cell.photoImage.getCenterY(), cell.transitionParams.animateChangeProgress) - cell.photoImage.getImageHeight() / 2f);
            } else if (!cell.isSmallImage && cell.documentAttachType != cell.DOCUMENT_ATTACH_TYPE_ROUND && cell.documentAttachType != cell.DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                cell.photoImage.setImageWidth(photoWidth);
            }
            if (!cell.isSmallImage && cell.drawImageButton) {
                int size = dp(48);
                cell.buttonX = cell.buttonX = (int) (cell.photoImage.getImageX() + (cell.photoImage.getImageWidth() - size) / 2.0f);
                cell.buttonY = cell.buttonY = (int) (cell.photoImage.getImageY() + (cell.photoImage.getImageHeight() - size) / 2.0f);
                cell.radialProgress.setProgressRect((int) cell.buttonX, (int) cell.buttonY, (int) cell.buttonX + size, (int) cell.buttonY + size);
            }
            if (cell.isRoundVideo && MediaController.getInstance().isPlayingMessage(cell.currentMessageObject) && MediaController.getInstance().isVideoDrawingReady() && canvas.isHardwareAccelerated()) {
                cell.imageDrawn = true;
                cell.drawTime = true;
            } else {
                if (cell.delegate == null || cell.delegate.getPinchToZoomHelper() == null || !cell.delegate.getPinchToZoomHelper().isInOverlayModeFor(cell)) {
                    if (alpha != 1f) {
                        cell.photoImage.setAlpha(alpha);
                        if (cell.allowDrawPhotoImage()) {
                            cell.imageDrawn = cell.photoImage.draw(canvas);
                        } else {
                            cell.imageDrawn = true;
                        }
                        if (cell.currentMessageObject.hasMediaSpoilers()) {
                            cell.blurredPhotoImage.setAlpha(alpha);
                            cell.drawBlurredPhoto(canvas);
                            cell.blurredPhotoImage.setAlpha(1f);
                        }
                        cell.photoImage.setAlpha(1f);
                    } else {
                        if (cell.allowDrawPhotoImage()) {
                            cell.imageDrawn = cell.photoImage.draw(canvas);
                        } else {
                            cell.imageDrawn = true;
                        }
                        if (cell.currentMessageObject.hasMediaSpoilers()) {
                            cell.drawBlurredPhoto(canvas);
                        }
                    }
                }
            }
            linkPreviewY += cell.photoImage.getImageHeight() + dp(6);
        }

        if (cell.siteNameLayout != null) {
            if (cell.titleLabelLayout != null) {
                linkPreviewY += dp(1);
            }
            smallImageStartY = linkPreviewY - dp(1);
            int wasAlpha = Theme.chat_replyNamePaint.getAlpha();
            if (alpha != 1f) {
                Theme.chat_replyNamePaint.setAlpha((int) (alpha * wasAlpha));
            }
            canvas.save();
            float x = (Math.abs(cell.siteNameLeft) > 1 ? width - dp(cell.hasInvoicePreview ? 0 : 20) - cell.siteNameWidth - cell.siteNameLeft : 0) + (cell.hasInvoicePreview ? 0 : dp(10));
            canvas.translate(linkX + x, linkPreviewY - dp(1));
            SpoilerEffect.layoutDrawMaybe(cell.siteNameLayout, canvas);
            canvas.restore();

            if (cell.titleLabelLayout != null) {
                cell.titleLabelX = linkX + x + cell.siteNameLayoutWidth + dp(12);
                cell.titleLabelY = linkPreviewY;
                cell.rect.set(cell.titleLabelX - dp(6), cell.titleLabelY - dp(2), cell.titleLabelX + cell.titleLabelLayoutWidth + dp(6), cell.titleLabelY + cell.titleLabelLayoutHeight + dp(2));

                if (cell.titleLabelBounce == null) {
                    cell.titleLabelBounce = new ButtonBounce(cell);
                }
                float scale = cell.titleLabelBounce.getScale(0.1f);
                boolean scaleRestore = scale != 1;
                if (scaleRestore) {
                    canvas.save();
                    canvas.scale(scale, scale, cell.rect.centerX(), cell.rect.centerY());
                }
                Theme.chat_instantViewPaint.setColor(Theme.chat_replyNamePaint.getColor());
                int wasInstantViewAlpha = Theme.chat_instantViewPaint.getAlpha();
                Theme.chat_instantViewPaint.setAlpha((int) (wasAlpha * .11f));
                canvas.drawRoundRect(cell.rect, cell.rect.height(), cell.rect.height(), Theme.chat_instantViewPaint);
                Theme.chat_instantViewPaint.setAlpha(wasInstantViewAlpha);

                Theme.chat_titleLabelTextPaint.setColor(Theme.chat_replyNamePaint.getColor());
                Theme.chat_titleLabelTextPaint.setAlpha(Theme.chat_replyNamePaint.getAlpha());
                canvas.save();
                canvas.translate(cell.titleLabelX, cell.titleLabelY);
                cell.titleLabelLayout.draw(canvas);
                canvas.restore();
                if (scaleRestore) {
                    canvas.restore();
                }
            }
            linkPreviewY += cell.siteNameLayout.getLineBottom(cell.siteNameLayout.getLineCount() - 1);
            Theme.chat_replyNamePaint.setAlpha(wasAlpha);
        }
        if ((cell.hasGamePreview || cell.hasInvoicePreview) && cell.currentMessageObject.textHeight(cell.transitionParams) != 0) {
            startY += cell.currentMessageObject.textHeight(cell.transitionParams) + dp(4);
            linkPreviewY += cell.currentMessageObject.textHeight(cell.transitionParams) + dp(4);
        }

        if (cell.drawPhotoImage && drawPhotoImageBefore) {
            if (cell.isSmallImage) {
                if (cell.transitionParams != null && cell.transitionParams.animateSmallImage) {
                    float diff = (linkPreviewY != startY ? dp(2) : 0) + cell.transitionParams.photoImageFromHeight + dp(6);
                    linkPreviewY += diff * (1f - cell.transitionParams.animateChangeProgress);
                }
            } else {
                float yProgress = 1f;
                if (cell.transitionParams != null && cell.transitionParams.animateSmallImage) {
                    yProgress = cell.transitionParams.animateChangeProgress;
                }
                if (linkPreviewY != startY) {
                    linkPreviewY += yProgress * dp(2);
                }
                int photoWidth = width - dp(17);
                float tx = 0;
                if (!cell.isSmallImage && cell.drawInstantViewType == 17) {
                    AndroidUtilities.rectTmp2.set(linkX + dp(10), linkPreviewY, linkX + dp(10) + photoWidth, linkPreviewY + (int) cell.photoImage.getImageHeight());
                    AndroidUtilities.rectTmp.set(AndroidUtilities.rectTmp2);
                    if (gradientDrawable == null) {
                        if (cell.currentPhotoObject != null && cell.currentPhotoObject.gradientTopColor != 0 && cell.currentPhotoObject.gradientBottomColor != 0) {
                            gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{cell.currentPhotoObject.gradientTopColor, cell.currentPhotoObject.gradientBottomColor});
                        } else if (cell.photoImage.getBitmap() != null) {
                            int[] colors = DominantColors.getColorsSync(false, cell.photoImage.getBitmap(), Theme.isCurrentThemeDark());
                            gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
                            if (cell.currentPhotoObject != null) {
                                cell.currentPhotoObject.gradientTopColor = colors[0];
                                cell.currentPhotoObject.gradientBottomColor = colors[1];
                            }
                        }
                    }
                    if (gradientDrawable != null) {
                        canvas.save();
                        if (cell.drillHolePath != null) {
                            cell.drillHolePath.rewind();
                        } else {
                            cell.drillHolePath = new Path();
                        }
                        cell.drillHolePath.addRoundRect(AndroidUtilities.rectTmp, dp(4), dp(4), Path.Direction.CW);
                        canvas.clipPath(cell.drillHolePath);
                        gradientDrawable.setBounds(AndroidUtilities.rectTmp2);
                        gradientDrawable.draw(canvas);
                        canvas.restore();
                    }
                    tx = (cell.instantWidth - cell.photoImage.getImageWidth() - dp(10) - (cell.transitionParams == null ? 0 : (cell.currentMessageObject.isOutOwner() ? cell.transitionParams.deltaLeft : -cell.transitionParams.deltaRight))) / 2f;
                }
                if (cell.imageBackgroundSideColor != 0) {
                    int x = linkX + dp(10);
                    cell.photoImage.setImageX(tx + x + (imageBackgroundSideWidth - cell.photoImage.getImageWidth()) / 2);
                    cell.photoImage.setImageY(linkPreviewY);
                    cell.rect.set(x, cell.photoImage.getImageY(), x + imageBackgroundSideWidth, cell.photoImage.getImageY2());
                    Theme.chat_instantViewPaint.setColor(ColorUtils.setAlphaComponent(cell.imageBackgroundSideColor, (int) (255 * alpha)));
                    canvas.drawRoundRect(cell.rect, dp(4), dp(4), Theme.chat_instantViewPaint);
                } else {
                    cell.photoImage.setImageX(tx + linkX + dp(10));
                    cell.photoImage.setImageY(linkPreviewY);
                }
                if (cell.drawInstantViewType != 17) {
                    if (cell.transitionParams != null && cell.transitionParams.animateSmallImage) {
                        if (!cell.isSmallImage && cell.documentAttachType != cell.DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                            cell.photoImage.setImageWidth((int) AndroidUtilities.lerp(cell.transitionParams.photoImageFromWidth, photoWidth, cell.transitionParams.animateChangeProgress));
                        }
                        cell.photoImage.setImageX(AndroidUtilities.lerp(cell.transitionParams.photoImageFromCenterX, cell.photoImage.getCenterX(), cell.transitionParams.animateChangeProgress) - cell.photoImage.getImageWidth() / 2f);
                        cell.photoImage.setImageY(AndroidUtilities.lerp(cell.transitionParams.photoImageFromCenterY, cell.photoImage.getCenterY(), cell.transitionParams.animateChangeProgress) - cell.photoImage.getImageHeight() / 2f);
                    } else if (!cell.isSmallImage && cell.documentAttachType != cell.DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                        cell.photoImage.setImageWidth(photoWidth);
                    }
                }
                if (imageBackgroundColor != 0) {
                    cell.rect.set(cell.photoImage.getImageX(), cell.photoImage.getImageY(), cell.photoImage.getImageX2(), cell.photoImage.getImageY2());
                    if (imageBackgroundGradientColor1 != 0) {
                        if (imageBackgroundGradientColor2 != 0) {
                            if (motionBackgroundDrawable == null) {
                                motionBackgroundDrawable = new MotionBackgroundDrawable(imageBackgroundColor, imageBackgroundGradientColor1, imageBackgroundGradientColor2, imageBackgroundGradientColor3, true);
                                if (imageBackgroundIntensity < 0) {
                                    cell.photoImage.setGradientBitmap(motionBackgroundDrawable.getBitmap());
                                }
                                if (!cell.photoImage.hasImageSet()) {
                                    motionBackgroundDrawable.setRoundRadius(dp(4));
                                }
                            }
                        } else {
                            if (gradientShader == null) {
                                Rect r = BackgroundGradientDrawable.getGradientPoints(AndroidUtilities.getWallpaperRotation(imageBackgroundGradientRotation, false), (int) cell.rect.width(), (int) cell.rect.height());
                                gradientShader = new LinearGradient(r.left, r.top, r.right, r.bottom, new int[]{imageBackgroundColor, imageBackgroundGradientColor1}, null, Shader.TileMode.CLAMP);
                            }
                            Theme.chat_instantViewPaint.setShader(gradientShader);
                            if (alpha != 1f) {
                                Theme.chat_instantViewPaint.setAlpha((int) (255 * alpha));
                            }
                        }
                    } else {
                        Theme.chat_instantViewPaint.setShader(null);
                        Theme.chat_instantViewPaint.setColor(imageBackgroundColor);
                        if (alpha != 1f) {
                            Theme.chat_instantViewPaint.setAlpha((int) (255 * alpha));
                        }
                    }
                    if (motionBackgroundDrawable != null) {
                        motionBackgroundDrawable.setBounds((int) cell.rect.left, (int) cell.rect.top, (int) cell.rect.right, (int) cell.rect.bottom);
                        motionBackgroundDrawable.draw(canvas);
                    } else if (cell.imageBackgroundSideColor != 0) {
                        canvas.drawRect(cell.photoImage.getImageX(), cell.photoImage.getImageY(), cell.photoImage.getImageX2(), cell.photoImage.getImageY2(), Theme.chat_instantViewPaint);
                    } else {
                        canvas.drawRoundRect(cell.rect, dp(4), dp(4), Theme.chat_instantViewPaint);
                    }
                    Theme.chat_instantViewPaint.setShader(null);
                    Theme.chat_instantViewPaint.setAlpha(255);
                }
                if (cell.drawPhotoImage && cell.drawInstantView && cell.drawInstantViewType != 9) {
                    if (cell.drawImageButton) {
                        int size = dp(48);
                        cell.buttonX = cell.buttonX = (int) (cell.photoImage.getImageX() + (cell.photoImage.getImageWidth() - size) / 2.0f);
                        cell.buttonY = cell.buttonY = (int) (cell.photoImage.getImageY() + (cell.photoImage.getImageHeight() - size) / 2.0f);
                        cell.radialProgress.setProgressRect((int) cell.buttonX, (int) cell.buttonY, (int) cell.buttonX + size, (int) cell.buttonY + size);
                    }
                    if (cell.delegate == null || cell.delegate.getPinchToZoomHelper() == null || !cell.delegate.getPinchToZoomHelper().isInOverlayModeFor(cell)) {
                        if (alpha != 1f) {
                            cell.photoImage.setAlpha(alpha);
                            if (cell.allowDrawPhotoImage()) {
                                cell.imageDrawn = cell.photoImage.draw(canvas);
                            } else {
                                cell.imageDrawn = true;
                            }
                            if (cell.currentMessageObject.hasMediaSpoilers()) {
                                cell.blurredPhotoImage.setAlpha(alpha);
                                cell.drawBlurredPhoto(canvas);
                                cell.blurredPhotoImage.setAlpha(1f);
                            }
                            cell.photoImage.setAlpha(1f);
                        } else {
                            if (cell.allowDrawPhotoImage()) {
                                cell.imageDrawn = cell.photoImage.draw(canvas);
                            } else {
                                cell.imageDrawn = true;
                            }
                            if (cell.currentMessageObject.hasMediaSpoilers()) {
                                cell.drawBlurredPhoto(canvas);
                            }
                        }
                    }
                }
                if (cell.transitionParams != null && cell.transitionParams.animateChange) {
                    linkPreviewY += yProgress * (cell.transitionParams.animateToImageH + dp(6));
                } else {
                    linkPreviewY += yProgress * (cell.photoImage.getImageHeight() + dp(6));
                }
            }
        }

        if (cell.currentMessageObject.isOutOwner()) {
            Theme.chat_replyNamePaint.setColor(ColorUtils.setAlphaComponent(cell.getThemedColor(Theme.key_chat_messageTextOut), (int) (255 * alpha)));
            Theme.chat_replyTextPaint.setColor(ColorUtils.setAlphaComponent(cell.getThemedColor(Theme.key_chat_messageTextOut), (int) (255 * alpha)));
        } else {
            Theme.chat_replyNamePaint.setColor(ColorUtils.setAlphaComponent(cell.getThemedColor(Theme.key_chat_messageTextIn), (int) (255 * alpha)));
            Theme.chat_replyTextPaint.setColor(ColorUtils.setAlphaComponent(cell.getThemedColor(Theme.key_chat_messageTextIn), (int) (255 * alpha)));
        }
        if (cell.titleLayout != null) {
            float oy = 0;
            if (linkPreviewY != startY) {
                linkPreviewY += dp(4);
                oy = dp(1);
            }
            if (smallImageStartY == 0) {
                smallImageStartY = linkPreviewY - dp(1);
            }
            canvas.save();
            canvas.translate(linkX + dp(10) + (Math.abs(titleLayoutLeft) > 1 ? width - dp(20) - titleLayoutWidth - titleLayoutLeft : 0), linkPreviewY - dp(3) + (!cell.transitionParams.animateLinkPreviewY ? -cell.transitionParams.deltaTop + cell.transitionParams.deltaBottom : 0));
            TextPaint paint = cell.titleLayout.getPaint();
            int wasAlpha = paint.getAlpha();
            paint.setAlpha((int) (wasAlpha * alpha));
            SpoilerEffect.layoutDrawMaybe(cell.titleLayout, canvas);
            paint.setAlpha(wasAlpha);
            canvas.restore();
            linkPreviewY += cell.titleLayout.getLineBottom(cell.titleLayout.getLineCount() - 1);
        }

        if (authorLayout != null) {
            if (linkPreviewY != startY) {
                linkPreviewY += dp(2);
            }
            if (smallImageStartY == 0) {
                smallImageStartY = linkPreviewY - dp(1);
            }
            canvas.save();
            canvas.translate(linkX + dp(10) + (Math.abs(authorLayoutLeft) > 1 ? width - dp(20) - authorLayoutWidth - authorLayoutLeft : 0), linkPreviewY - dp(3) + (!cell.transitionParams.animateLinkPreviewY ? -cell.transitionParams.deltaTop + cell.transitionParams.deltaBottom : 0));
            TextPaint paint = authorLayout.getPaint();
            int wasAlpha = paint.getAlpha();
            paint.setAlpha((int) (wasAlpha * alpha));
            SpoilerEffect.layoutDrawMaybe(authorLayout, canvas);
            paint.setAlpha(wasAlpha);
            canvas.restore();
            linkPreviewY += authorLayout.getLineBottom(authorLayout.getLineCount() - 1);
        }

        if (cell.descriptionLayout != null) {
            if (linkPreviewY != startY) {
                linkPreviewY += dp(2);
            }
            if (smallImageStartY == 0) {
                smallImageStartY = linkPreviewY - dp(1);
            }
            cell.descriptionY = linkPreviewY - dp(3);
            canvas.save();
            canvas.translate(linkX + dp(10) + (Math.abs(cell.descriptionLayoutLeft) > 1 ? width - dp(20) - cell.descriptionLayoutWidth - cell.descriptionLayoutLeft : 0), cell.descriptionY + (!cell.transitionParams.animateLinkPreviewY ? -cell.transitionParams.deltaTop + cell.transitionParams.deltaBottom : 0));
            if (cell.linkBlockNum == -10) {
                if (cell.links.draw(canvas)) {
                    cell.invalidate();
                }
            }
            cell.drawProgressLoadingLink(canvas, -2);
            if (cell.delegate != null && cell.delegate.getTextSelectionHelper() != null && cell.getDelegate().getTextSelectionHelper().isSelected(cell.currentMessageObject)) {
                cell.delegate.getTextSelectionHelper().drawDescription(cell.currentMessageObject.isOutOwner(), cell.descriptionLayout, canvas);
            }
            TextPaint paint = cell.descriptionLayout.getPaint();
            if (drawInstantButtonInside && cell.currentMessageObject != null && !cell.currentMessageObject.isOutOwner() && cell.linkLine != null) {
                paint.linkColor = cell.linkLine.getColor();
            }
            int wasAlpha = paint.getAlpha();
            paint.setAlpha((int) (wasAlpha * alpha));
            SpoilerEffect.layoutDrawMaybe(cell.descriptionLayout, canvas);
            paint.setAlpha(wasAlpha);
            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, cell.descriptionLayout, cell.animatedEmojiDescriptionStack, 0, null, 0, 0, 0, 1f, cell.getAdaptiveEmojiColorFilter(1, cell.descriptionLayout.getPaint().getColor()));
            canvas.restore();
            paint.linkColor = cell.getThemedColor(cell.currentMessageObject.isOutOwner() ? Theme.key_chat_messageLinkOut : Theme.key_chat_messageLinkIn);
            linkPreviewY += cell.descriptionLayout.getLineBottom(cell.descriptionLayout.getLineCount() - 1);
        }

        if (cell.drawPhotoImage && drawPhotoImageAfter) {
            if (linkPreviewY != startY) {
                linkPreviewY += dp(2);
            }

            final int photoWidth = width - dp(17);
            if (cell.isSmallImage) {
                cell.photoImage.setImageX(linkX + width - cell.photoImage.getImageWidth() - dp(6));
                cell.photoImage.setImageY(smallImageStartY);
            } else {
                cell.photoImage.setImageX(linkX + (cell.hasInvoicePreview ? -dp(6.3f) : dp(10)));
                cell.photoImage.setImageY(linkPreviewY);
            }
            if (cell.transitionParams != null && cell.transitionParams.animateSmallImage) {
                if (!cell.isSmallImage && cell.documentAttachType != cell.DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                    cell.photoImage.setImageWidth((int) AndroidUtilities.lerp(cell.transitionParams.photoImageFromWidth, photoWidth, cell.transitionParams.animateChangeProgress));
                }
                cell.photoImage.setImageX(AndroidUtilities.lerp(cell.transitionParams.photoImageFromCenterX, cell.photoImage.getCenterX(), cell.transitionParams.animateChangeProgress) - cell.photoImage.getImageWidth() / 2f);
                cell.photoImage.setImageY(AndroidUtilities.lerp(cell.transitionParams.photoImageFromCenterY, cell.photoImage.getCenterY(), cell.transitionParams.animateChangeProgress) - cell.photoImage.getImageHeight() / 2f);
            } else if (!cell.isSmallImage && cell.documentAttachType != cell.DOCUMENT_ATTACH_TYPE_ROUND && cell.documentAttachType != cell.DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                cell.photoImage.setImageWidth(photoWidth);
            }
            if (!cell.isSmallImage && cell.drawImageButton) {
                int size = dp(48);
                cell.buttonX = cell.buttonX = (int) (cell.photoImage.getImageX() + (cell.photoImage.getImageWidth() - size) / 2.0f);
                cell.buttonY = cell.buttonY = (int) (cell.photoImage.getImageY() + (cell.photoImage.getImageHeight() - size) / 2.0f);
                cell.radialProgress.setProgressRect((int) cell.buttonX, (int) cell.buttonY, (int) cell.buttonX + size, (int) cell.buttonY + size);
            }
            if (cell.isRoundVideo && MediaController.getInstance().isPlayingMessage(cell.currentMessageObject) && MediaController.getInstance().isVideoDrawingReady() && canvas.isHardwareAccelerated()) {
                cell.imageDrawn = true;
                cell.drawTime = true;
            } else {
                if (cell.delegate == null || cell.delegate.getPinchToZoomHelper() == null || !cell.delegate.getPinchToZoomHelper().isInOverlayModeFor(cell)) {
                    if (alpha != 1f) {
                        cell.photoImage.setAlpha(alpha);
                        if (cell.allowDrawPhotoImage()) {
                            cell.imageDrawn = cell.photoImage.draw(canvas);
                        } else {
                            cell.imageDrawn = true;
                        }
                        if (cell.currentMessageObject.hasMediaSpoilers()) {
                            cell.blurredPhotoImage.setAlpha(alpha);
                            cell.drawBlurredPhoto(canvas);
                            cell.blurredPhotoImage.setAlpha(1f);
                        }
                        cell.photoImage.setAlpha(1f);
                    } else {
                        if (cell.allowDrawPhotoImage()) {
                            cell.imageDrawn = cell.photoImage.draw(canvas);
                        } else {
                            cell.imageDrawn = true;
                        }
                        if (cell.currentMessageObject.hasMediaSpoilers()) {
                            cell.drawBlurredPhoto(canvas);
                        }
                    }
                }
            }
        }
        if (cell.documentAttachType == cell.DOCUMENT_ATTACH_TYPE_VIDEO || cell.documentAttachType == cell.DOCUMENT_ATTACH_TYPE_GIF) {
            cell.videoButtonX = (int) (cell.photoImage.getImageX() + dp(8));
            cell.videoButtonY = (int) (cell.photoImage.getImageY() + dp(8));
            cell.videoRadialProgress.setProgressRect(cell.videoButtonX, cell.videoButtonY, cell.videoButtonX + dp(24), cell.videoButtonY + dp(24));
        }
        Paint timeBackgroundPaint = cell.getThemedPaint(Theme.key_paint_chatTimeBackground);
        if (photosCountLayout != null && cell.photoImage.getVisible()) {
            int x = (int) (cell.photoImage.getImageX() + cell.photoImage.getImageWidth() - dp(8) - photosCountWidth);
            int y = (int) (cell.photoImage.getImageY() + cell.photoImage.getImageHeight() - dp(19));
            cell.rect.set(x - dp(4), y - dp(1.5f), x + photosCountWidth + dp(4), y + dp(14.5f));
            int oldAlpha = timeBackgroundPaint.getAlpha();
            timeBackgroundPaint.setAlpha((int) (oldAlpha * cell.controlsAlpha));
            Theme.chat_durationPaint.setAlpha((int) (255 * cell.controlsAlpha));
            canvas.drawRoundRect(cell.rect, dp(4), dp(4), timeBackgroundPaint);
            timeBackgroundPaint.setAlpha(oldAlpha);
            canvas.save();
            canvas.translate(x, y);
            photosCountLayout.draw(canvas);
            canvas.restore();
            Theme.chat_durationPaint.setAlpha(255);
        }
        if (cell.videoInfoLayout != null && (!cell.drawPhotoImage || cell.photoImage.getVisible()) && cell.imageBackgroundSideColor == 0) {
            int x;
            int y;
            if (cell.hasGamePreview || cell.hasInvoicePreview || cell.documentAttachType == cell.DOCUMENT_ATTACH_TYPE_WALLPAPER) {
                if (cell.drawPhotoImage) {
                    x = (int) (cell.photoImage.getImageX() + dp(8.5f));
                    y = (int) (cell.photoImage.getImageY() + dp(6));
                    int height = dp(cell.documentAttachType == cell.DOCUMENT_ATTACH_TYPE_WALLPAPER ? 14.5f : 16.5f);
                    cell.rect.set(x - dp(4), y - dp(1.5f), x + cell.durationWidth + dp(4), y + height);
                    canvas.drawRoundRect(cell.rect, dp(4), dp(4), timeBackgroundPaint);
                } else {
                    x = linkX;
                    y = linkPreviewY;
                }
            } else {
                x = (int) (cell.photoImage.getImageX() + cell.photoImage.getImageWidth() - dp(8) - cell.durationWidth);
                y = (int) (cell.photoImage.getImageY() + cell.photoImage.getImageHeight() - dp(19));
                cell.rect.set(x - dp(4), y - dp(1.5f), x + cell.durationWidth + dp(4), y + dp(14.5f));
                canvas.drawRoundRect(cell.rect, dp(4), dp(4), cell.getThemedPaint(Theme.key_paint_chatTimeBackground));
            }

            canvas.save();
            canvas.translate(x, y);
            if (cell.hasInvoicePreview) {
                if (cell.drawPhotoImage) {
                    Theme.chat_shipmentPaint.setColor(cell.getThemedColor(Theme.key_chat_previewGameText));
                } else {
                    if (cell.currentMessageObject.isOutOwner()) {
                        Theme.chat_shipmentPaint.setColor(cell.getThemedColor(Theme.key_chat_messageTextOut));
                    } else {
                        Theme.chat_shipmentPaint.setColor(cell.getThemedColor(Theme.key_chat_messageTextIn));
                    }
                }
            }
            cell.videoInfoLayout.draw(canvas);
            canvas.restore();
        }
        cell.drawVideoTimestamps(canvas, cell.linkLine.getColor());
        cell.giveawayMessageCell.draw(canvas, cell.namesOffset, linkX, cell.resourcesProvider);
        cell.giveawayResultsMessageCell.draw(canvas, cell.namesOffset, linkX, cell.resourcesProvider);

        if (cell.drawInstantView) {
            Drawable instantDrawable;
            if (cell.currentMessageObject.isOutOwner()) {
                final int color = cell.getThemedColor(Theme.key_chat_outPreviewInstantText);
                if (cell.instantDrawable == null) {
                    if (cell.drawInstantViewType == 27) {
                        cell.instantDrawable = cell.getContext().getResources().getDrawable(R.drawable.mini_call_join).mutate();
                    } else {
                        cell.instantDrawable = cell.getThemedDrawable(Theme.key_drawable_msgOutInstant);
                    }
                    cell.instantDrawable.setColorFilter(new PorterDuffColorFilter(cell.instantDrawableColor = color, PorterDuff.Mode.SRC_IN));
                }
                if (cell.instantDrawableColor != color) {
                    cell.instantDrawable.setColorFilter(new PorterDuffColorFilter(cell.instantDrawableColor = color, PorterDuff.Mode.SRC_IN));
                }
                instantDrawable = cell.instantDrawable;
                Theme.chat_instantViewPaint.setColor(color);
                Theme.chat_instantViewButtonPaint.setColor(Theme.multAlpha(color, .10f));
                if (instantButtonLoading != null) {
                    instantButtonLoading.setColors(
                        Theme.multAlpha(color, .1f),
                        Theme.multAlpha(color, .3f),
                        Theme.multAlpha(color, .3f),
                        Theme.multAlpha(color, 1.2f)
                    );
                }
            } else {
                int color = cell.getThemedColor(Theme.key_chat_inPreviewInstantText);
                if (cell.linkLine != null && !cell.currentMessageObject.isUnsupported()) {
                    color = cell.linkLine.getColor();
                }
                if (cell.instantDrawable == null) {
                    if (cell.drawInstantViewType == 16) {
                        cell.instantDrawable = cell.getContext().getResources().getDrawable(R.drawable.mini_external_link).mutate();
                    } else if (cell.drawInstantViewType == 27) {
                        cell.instantDrawable = cell.getContext().getResources().getDrawable(R.drawable.mini_call_join).mutate();
                    } else {
                        cell.instantDrawable = cell.getContext().getResources().getDrawable(R.drawable.msg_instant).mutate();
                    }
                    cell.instantDrawable.setColorFilter(new PorterDuffColorFilter(cell.instantDrawableColor = color, PorterDuff.Mode.SRC_IN));
                }
                if (cell.instantDrawableColor != color) {
                    cell.instantDrawable.setColorFilter(new PorterDuffColorFilter(cell.instantDrawableColor = color, PorterDuff.Mode.SRC_IN));
                }
                instantDrawable = cell.instantDrawable;
                Theme.chat_instantViewPaint.setColor(color);
                Theme.chat_instantViewButtonPaint.setColor(Theme.multAlpha(color, .10f));
                if (instantButtonLoading != null) {
                    instantButtonLoading.setColors(
                        Theme.multAlpha(color, .1f),
                        Theme.multAlpha(color, .3f),
                        Theme.multAlpha(color, .3f),
                        Theme.multAlpha(color, 1.2f)
                    );
                }
            }
            if (drawInstantButtonInside) {
                int instantY = startY + linkPreviewHeight + dp(14);
                int wasAlpha = Theme.chat_instantViewPaint.getAlpha();
                Theme.chat_instantViewPaint.setAlpha((int) (wasAlpha * .18f));
                canvas.drawRect(linkX + dp(3 + 7), startY + linkPreviewHeight + dp(2), linkX + width - dp(7), startY + linkPreviewHeight + dp(2) + Math.max(1, dp(0.66f)), Theme.chat_instantViewPaint);
                Theme.chat_instantViewPaint.setAlpha(wasAlpha);
                final float textWidth = dp(cell.drawInstantViewType == 27 ? 20 : cell.drawInstantViewType == 0 ? 15 : 0) + cell.instantViewLayoutWidth;
                float l = linkX + (width - textWidth) / 2f;
                if (cell.drawInstantViewType == 0) {
                    cell.setDrawableBounds(instantDrawable, l, instantY + dp(1), dp(9), dp(13));
                    instantDrawable.setAlpha((int) (0xFF * alpha));
                    instantDrawable.draw(canvas);
                    l += dp(15);
                } else if (cell.drawInstantViewType == 27) {
                    cell.setDrawableBounds(instantDrawable, l, instantY, instantDrawable.getIntrinsicWidth(), instantDrawable.getIntrinsicHeight());
                    instantDrawable.setAlpha((int) (0xFF * alpha));
                    instantDrawable.draw(canvas);
                    l += dp(20);
                } else if (cell.drawInstantViewType == 16) {
                    l -= dp(11f);
                    cell.setDrawableBounds(instantDrawable, l + textWidth + dp(4f), instantY - dp(2f), dp(18), dp(18));
                    instantDrawable.setAlpha((int) (0xFF * alpha));
                    instantDrawable.draw(canvas);
                }
                if (cell.instantViewLayout != null) {
                    canvas.save();
                    canvas.translate(l - cell.instantViewLayoutLeft, instantY);
                    SpoilerEffect.layoutDrawMaybe(cell.instantViewLayout, canvas);
                    canvas.restore();
                }
            } else {
                int instantY = startY + linkPreviewHeight + dp(cell.currentMessageObject.isUnsupported() ? -5 : 10);
                if (instantButtonLoading != null && !loading && !instantButtonLoading.isDisappeared() && !instantButtonLoading.isDisappearing()) {
                    instantButtonLoading.disappear();
                }
                if (instantButtonLoading == null && loading) {
                    instantButtonLoading = new LoadingDrawable();
                    instantButtonLoading.strokePaint.setStrokeWidth(dp(1.25f));
                    instantButtonLoading.setAppearByGradient(true);
                } else if (instantButtonLoading != null && loading && (instantButtonLoading.isDisappeared() || instantButtonLoading.isDisappearing())) {
                    instantButtonLoading.reset();
                    instantButtonLoading.resetDisappear();
                }
                cell.instantButtonRect.set(linkX, instantY, linkX + cell.instantWidth, instantY + dp(36));
                float scale = cell.instantButtonBounce.getScale(.02f);
                boolean scaleRestore = scale != 1;
                if (scaleRestore) {
                    canvas.save();
                    canvas.scale(scale, scale, cell.instantButtonRect.centerX(), cell.instantButtonRect.centerY());
                }
                cell.selectorDrawableMaskType[0] = 0;
                cell.createSelectorDrawable(0);
                cell.selectorDrawable[0].setBounds(linkX, instantY, linkX + cell.instantWidth, instantY + dp(36));
                cell.selectorDrawable[0].draw(canvas);
                if (instantButtonLoading != null && !instantButtonLoading.isDisappeared()) {
                    instantButtonLoading.setBounds(cell.instantButtonRect);
                    instantButtonLoading.setRadiiDp(6);
                    instantButtonLoading.draw(canvas);
                    cell.invalidate();
                }
                canvas.drawRoundRect(cell.instantButtonRect, dp(6), dp(6), Theme.chat_instantViewButtonPaint);
                if (cell.drawInstantViewType == 0) {
                    cell.setDrawableBounds(instantDrawable, cell.instantTextLeftX + cell.instantTextX + linkX - dp(15), instantY + dp(11.5f), dp(9), dp(13));
                    instantDrawable.setAlpha((int) (0xFF * alpha));
                    instantDrawable.draw(canvas);
                } else if (cell.drawInstantViewType == 16) {
                    if (instantLinkArrowPaint == null) {
                        instantLinkArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        instantLinkArrowPaint.setStyle(Paint.Style.STROKE);
                        instantLinkArrowPaint.setStrokeCap(Paint.Cap.ROUND);
                        instantLinkArrowPaint.setStrokeJoin(Paint.Join.ROUND);
                    }
                    instantLinkArrowPaint.setColor(Theme.chat_instantViewPaint.getColor());
                    instantLinkArrowPaint.setStrokeWidth(AndroidUtilities.dpf2(1.333f));
                    if (instantLinkArrowPath == null) {
                        instantLinkArrowPath = new Path();
                    } else {
                        instantLinkArrowPath.rewind();
                    }
                    instantLinkArrowPath.moveTo(cell.instantButtonRect.right - dp(6 + 5), cell.instantButtonRect.top + dp(6));
                    instantLinkArrowPath.lineTo(cell.instantButtonRect.right - dp(6), cell.instantButtonRect.top + dp(6));
                    instantLinkArrowPath.lineTo(cell.instantButtonRect.right - dp(6), cell.instantButtonRect.top + dp(6 + 5));
                    instantLinkArrowPath.moveTo(cell.instantButtonRect.right - AndroidUtilities.dpf2(6 + 5.66f), cell.instantButtonRect.top + dp(6 + 5.66f));
                    instantLinkArrowPath.lineTo(cell.instantButtonRect.right - dp(6), cell.instantButtonRect.top + dp(6));
                    canvas.drawPath(instantLinkArrowPath, instantLinkArrowPaint);
                }
                if (cell.instantViewLayout != null) {
                    canvas.save();
                    canvas.translate(linkX + cell.instantTextX, instantY + dp(10.5f));
                    SpoilerEffect.layoutDrawMaybe(cell.instantViewLayout, canvas);
                    canvas.restore();
                }
                if (scaleRestore) {
                    canvas.restore();
                }
            }
        }

        canvas.restoreToCount(restoreCount);
    }

    boolean checkMotionEvent(MotionEvent event) {
        if (cell.currentMessageObject.type != MessageObject.TYPE_TEXT && cell.currentMessageObject.type != MessageObject.TYPE_STORY_MENTION || !cell.hasLinkPreview) {
            return false;
        }
        int x = (int) cell.getEventX(event);
        int y = (int) cell.getEventY(event);

        if (x >= cell.unmovedTextX && x <= cell.unmovedTextX + cell.backgroundWidth - dp(14) && y >= cell.linkPreviewY && y <= cell.linkPreviewY + cell.linkPreviewHeight + dp(8 + (cell.drawInstantView ? 46 : 0))) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (cell.descriptionLayout != null && y >= cell.descriptionY && !cell.currentMessageObject.preview) {
                    try {
                        int checkX = x - (cell.unmovedTextX + dp(10) + cell.descriptionX);
                        int checkY = y - cell.descriptionY;
                        if (checkY <= cell.descriptionLayout.getHeight()) {
                            final int line = cell.descriptionLayout.getLineForVertical(checkY);
                            final int off = cell.descriptionLayout.getOffsetForHorizontal(line, checkX);

                            final float left = cell.descriptionLayout.getLineLeft(line);
                            if (left <= checkX && left + cell.descriptionLayout.getLineWidth(line) >= checkX) {
                                Spannable buffer = (Spannable) (cell.currentMessageObject.isSponsored() ? cell.currentMessageObject.messageText : cell.currentMessageObject.linkDescription);
                                ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
                                boolean ignore = false;
                                if (link.length == 0 || link[0] instanceof URLSpanBotCommand && !URLSpanBotCommand.enabled) {
                                    ignore = true;
                                }
                                if (!ignore && !AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
                                    if (cell.pressedLink == null || cell.pressedLink.getSpan() != link[0]) {
                                        cell.links.removeLink(cell.pressedLink);
                                        cell.pressedLink = new LinkSpanDrawable(link[0], cell.resourcesProvider, x, y, cell.spanSupportsLongPress(link[0]));
                                        cell.pressedLink.setColor(cell.hasLinkPreview && cell.linkLine != null && cell.currentMessageObject != null && !cell.currentMessageObject.isOutOwner() ? Theme.multAlpha(cell.linkLine.getColor(), .10f) : cell.getThemedColor(cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outLinkSelectBackground : Theme.key_chat_linkSelectBackground));
                                        cell.linkBlockNum = -10;
                                        cell.pressedLinkType = 2;
                                        cell.startCheckLongPress();
                                        try {
                                            LinkPath path = cell.pressedLink.obtainNewPath();
                                            int[] pos = cell.getRealSpanStartAndEnd(buffer, cell.pressedLink.getSpan());
                                            path.setCurrentLayout(cell.descriptionLayout, pos[0], 0);
                                            cell.descriptionLayout.getSelectionPath(pos[0], pos[1], path);
                                        } catch (Exception e) {
                                            FileLog.e(e);
                                        }
                                        cell.links.addLink(cell.pressedLink, 2);
                                    }
                                    cell.invalidate();
                                    return true;
                                }
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (cell.pressedLink == null) {
                    int side = dp(48);
                    boolean area2 = false;
                    if (cell.miniButtonState >= 0) {
                        int offset = dp(27);
                        area2 = x >= cell.buttonX + offset && x <= cell.buttonX + offset + side && y >= cell.buttonY + offset && y <= cell.buttonY + offset + side;
                    }
                    if (area2) {
                        cell.miniButtonPressed = 1;
                        cell.invalidate();
                        return true;
                    } else if (cell.drawVideoImageButton && cell.buttonState != -1 && x >= cell.videoButtonX && x <= cell.videoButtonX + dp(26 + 8) + Math.max(cell.infoWidth, cell.docTitleWidth) && y >= cell.videoButtonY && y <= cell.videoButtonY + dp(30)) {
                        cell.videoButtonPressed = 1;
                        cell.invalidate();
                        return true;
                    } else if (cell.drawPhotoImage && cell.currentMessageObject.sponsoredMedia != null && cell.photoImage.isInsideImage(x, y) || cell.drawPhotoImage && cell.drawImageButton && (cell.buttonState != -1 || cell.documentAttachType == cell.DOCUMENT_ATTACH_TYPE_VIDEO) && (!cell.checkOnlyButtonPressed && cell.photoImage.isInsideImage(x, y) || x >= cell.buttonX && x <= cell.buttonX + dp(48) && y >= cell.buttonY && y <= cell.buttonY + dp(48) && cell.radialProgress.getIcon() != MediaActionDrawable.ICON_NONE)) {
                        cell.buttonPressed = 1;
                        cell.invalidate();
                        if (cell.currentMessageObject.sponsoredMedia != null && (MessageObject.isGifDocument(cell.currentMessageObject.sponsoredMedia.document) || cell.currentMessageObject.sponsoredMedia.photo != null)) {
                            if (cell.selectorDrawable[0] != null && cell.selectorDrawable[0].getBounds().contains(x, y)) {
                                cell.selectorDrawable[0].setHotspot(x, y);
                                cell.selectorDrawable[0].setState(cell.pressedState);
                            }
                            if (selector != null && selector.getBounds().contains(x, y)) {
                                selector.setHotspot(x, y);
                                selector.setState(cell.pressedState);
                            }
                            cell.setInstantButtonPressed(true);
                            if (bounce != null) {
                                bounce.setPressed(true);
                            }
                        }
                        return true;
                    } else {
                        cell.instantPressed = true;
                        cell.selectorDrawableMaskType[0] = 0;
                        if (cell.selectorDrawable[0] != null && cell.selectorDrawable[0].getBounds().contains(x, y)) {
                            cell.selectorDrawable[0].setHotspot(x, y);
                            cell.selectorDrawable[0].setState(cell.pressedState);
                        }
                        if (selector != null && selector.getBounds().contains(x, y)) {
                            selector.setHotspot(x, y);
                            selector.setState(cell.pressedState);
                        }
                        cell.setInstantButtonPressed(true);
                        if (bounce != null) {
                            bounce.setPressed(true);
                        }
                        cell.invalidate();
                        return true;
                    }
//                    else if (documentAttachType != DOCUMENT_ATTACH_TYPE_DOCUMENT && drawPhotoImage && photoImage.isInsideImage(x, y)) {
//                        linkPreviewPressed = true;
//                        TLRPC.WebPage webPage = MessageObject.getMedia(currentMessageObject.messageOwner).webpage;
//                        if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF && buttonState == -1 && SharedConfig.isAutoplayGifs() && (photoImage.getAnimation() == null || !TextUtils.isEmpty(webPage.embed_url))) {
//                            linkPreviewPressed = false;
//                            return false;
//                        }
//                        return true;
//                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (cell.instantPressed) {
                    if (cell.documentAttachType == cell.DOCUMENT_ATTACH_TYPE_ROUND) {
                        if (!MediaController.getInstance().isPlayingMessage(cell.currentMessageObject) || MediaController.getInstance().isMessagePaused()) {
                            cell.delegate.needPlayMessage(cell, cell.currentMessageObject, false);
                        } else {
                            MediaController.getInstance().pauseMessage(cell.currentMessageObject);
                        }
                    } else if (cell.drawInstantView || cell.currentMessageObject != null && cell.currentMessageObject.currentEvent != null && cell.currentMessageObject.currentEvent.action instanceof TLRPC.TL_channelAdminLogEventActionEditMessage) {
                        if (cell.delegate != null) {
                            cell.delegate.didPressInstantButton(cell, cell.drawInstantViewType);
                        }
                    } else if (cell.drawPhotoImage && (cell.documentAttachType == cell.DOCUMENT_ATTACH_TYPE_GIF || cell.documentAttachType == cell.DOCUMENT_ATTACH_TYPE_VIDEO || authorLayout == null && cell.titleLayout == null && cell.descriptionLayout == null && cell.siteNameLayout == null)) {
                        if (cell.delegate != null) {
                            cell.delegate.didPressImage(cell, cell.lastTouchX, cell.lastTouchY, true);
                        }
                    } else if (cell.currentMessageObject != null && !cell.currentMessageObject.preview) {
                        TLRPC.WebPage webPage = MessageObject.getMedia(cell.currentMessageObject.messageOwner).webpage;
                        if (webPage != null && !TextUtils.isEmpty(webPage.embed_url)/* && TextUtils.isEmpty(WebPlayerView.getYouTubeVideoId(webPage.embed_url))*/) {
                            if (cell.delegate != null) {
                                cell.delegate.needOpenWebView(cell.currentMessageObject, webPage.embed_url, webPage.site_name, webPage.title, webPage.url, webPage.embed_width, webPage.embed_height);
                            }
                        } else if (webPage != null) {
                            if (cell.delegate != null) {
                                cell.delegate.didPressWebPage(cell, webPage, webPage.url, MessageObject.getMedia(cell.currentMessageObject.messageOwner).safe);
                            } else {
                                Browser.openUrl(cell.getContext(), webPage.url);
                            }
                        }
                    }
                    cell.playSoundEffect(SoundEffectConstants.CLICK);
                    if (cell.selectorDrawable[0] != null) {
                        cell.selectorDrawable[0].setState(StateSet.NOTHING);
                    }
                    if (selector != null) {
                        selector.setState(StateSet.NOTHING);
                    }
                    if (bounce != null) {
                        bounce.setPressed(false);
                    }
                    cell.setInstantButtonPressed(cell.instantPressed = false);
                    cell.invalidate();
                } else if (cell.pressedLinkType == 2 || cell.buttonPressed != 0 || cell.miniButtonPressed != 0 || cell.videoButtonPressed != 0 || pressed) {
                    if (cell.videoButtonPressed == 1) {
                        cell.videoButtonPressed = 0;
                        cell.playSoundEffect(SoundEffectConstants.CLICK);
                        cell.didPressButton(true, true);
                        cell.invalidate();
                    } else if (cell.buttonPressed != 0) {
                        cell.buttonPressed = 0;
                        cell.playSoundEffect(SoundEffectConstants.CLICK);
                        if (cell.drawVideoImageButton || cell.buttonState == -1 && cell.documentAttachType == cell.DOCUMENT_ATTACH_TYPE_VIDEO || cell.currentMessageObject.sponsoredMedia != null) {
                            cell.didClickedImage();
                        } else {
                            cell.didPressButton(true, false);
                        }
                        cell.invalidate();
                    } else if (cell.miniButtonPressed != 0) {
                        cell.miniButtonPressed = 0;
                        cell.playSoundEffect(SoundEffectConstants.CLICK);
                        cell.didPressMiniButton(true);
                        cell.invalidate();
                    } else if (cell.pressedLink != null) {
                        if (cell.pressedLink.getSpan() instanceof URLSpan) {
                            cell.delegate.didPressUrl(cell, cell.pressedLink.getSpan(), false);
                        } else if (cell.pressedLink.getSpan() instanceof ClickableSpan) {
                            ((ClickableSpan) cell.pressedLink.getSpan()).onClick(cell);
                        }
                        cell.resetPressedLink(2);
                    } else if (cell.pressedEmoji != null && cell.delegate.didPressAnimatedEmoji(cell, cell.pressedEmoji)) {
                        cell.pressedEmoji = null;
                        cell.resetPressedLink(2);
                    } else {
                        if (cell.documentAttachType == cell.DOCUMENT_ATTACH_TYPE_GIF && cell.drawImageButton) {
                            if (cell.buttonState == -1) {
                                if (SharedConfig.isAutoplayGifs() && !cell.currentMessageObject.isRepostPreview) {
                                    cell.delegate.didPressImage(cell, cell.lastTouchX, cell.lastTouchY, false);
                                } else {
                                    cell.buttonState = 2;
                                    cell.currentMessageObject.gifState = 1;
                                    cell.photoImage.setAllowStartAnimation(false);
                                    cell.photoImage.stopAnimation();
                                    cell.radialProgress.setIcon(cell.getIconForCurrentState(), false, true);
                                    cell.invalidate();
                                    cell.playSoundEffect(SoundEffectConstants.CLICK);
                                }
                            } else if (cell.buttonState == 2 || cell.buttonState == 0) {
                                cell.didPressButton(true, false);
                                cell.playSoundEffect(SoundEffectConstants.CLICK);
                            }
                        } else if (!cell.currentMessageObject.preview) {
                            TLRPC.WebPage webPage = MessageObject.getMedia(cell.currentMessageObject.messageOwner).webpage;
                            if (webPage != null && !TextUtils.isEmpty(webPage.embed_url)) {
                                cell.delegate.needOpenWebView(cell.currentMessageObject, webPage.embed_url, webPage.site_name, webPage.title, webPage.url, webPage.embed_width, webPage.embed_height);
                            } else if (cell.buttonState == -1 || cell.buttonState == 3) {
                                cell.delegate.didPressImage(cell, cell.lastTouchX, cell.lastTouchY, false);
                                cell.playSoundEffect(SoundEffectConstants.CLICK);
                            } else if (webPage != null) {
                                if (cell.delegate != null) {
                                    cell.delegate.didPressWebPage(cell, webPage, webPage.url, MessageObject.getMedia(cell.currentMessageObject.messageOwner).safe);
                                } else {
                                    Browser.openUrl(cell.getContext(), webPage.url);
                                }
                            }
                        }
                        cell.resetPressedLink(2);
                        return true;
                    }
                } else if (!cell.hadLongPress) {
                    cell.hadLongPress = false;
                    cell.resetPressedLink(2);
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (cell.instantButtonPressed) {
                    if (cell.selectorDrawable[0] != null) {
                        cell.selectorDrawable[0].setHotspot(x, y);
                    }
                    if (selector != null) {
                        selector.setHotspot(x, y);
                    }
                }
            }
        }
        return false;
    }

    boolean checkGameMotionEvent(MotionEvent event) {
        if (!cell.hasGamePreview) {
            return false;
        }
        int x = (int) cell.getEventX(event);
        int y = (int) cell.getEventY(event);

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (cell.drawPhotoImage && cell.drawImageButton && cell.buttonState != -1 && x >= cell.buttonX && x <= cell.buttonX + dp(48) && y >= cell.buttonY && y <= cell.buttonY + dp(48) && cell.radialProgress.getIcon() != MediaActionDrawable.ICON_NONE) {
                cell.buttonPressed = 1;
                cell.invalidate();
                return true;
            } else if (cell.drawPhotoImage && cell.photoImage.isInsideImage(x, y)) {
                gamePressed = true;
                return true;
            } else if (cell.descriptionLayout != null && y >= cell.descriptionY) {
                try {
                    x -= cell.unmovedTextX + dp(10) + cell.descriptionX;
                    y -= cell.descriptionY;
                    final int line = cell.descriptionLayout.getLineForVertical(y);
                    final int off = cell.descriptionLayout.getOffsetForHorizontal(line, x);

                    final float left = cell.descriptionLayout.getLineLeft(line);
                    if (left <= x && left + cell.descriptionLayout.getLineWidth(line) >= x) {
                        Spannable buffer = (Spannable) cell.currentMessageObject.linkDescription;
                        ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
                        boolean ignore = false;
                        if (link.length == 0 || link[0] instanceof URLSpanBotCommand && !URLSpanBotCommand.enabled) {
                            ignore = true;
                        }
                        if (!ignore && !AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
                            if (cell.pressedLink == null || cell.pressedLink.getSpan() != link[0]) {
                                cell.links.removeLink(cell.pressedLink);
                                cell.pressedLink = new LinkSpanDrawable(link[0], cell.resourcesProvider, x, y, cell.spanSupportsLongPress(link[0]));
                                cell.pressedLink.setColor(cell.getThemedColor(cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outLinkSelectBackground : Theme.key_chat_linkSelectBackground));
                                cell.linkBlockNum = -10;
                                cell.pressedLinkType = 2;
                                try {
                                    LinkPath path = cell.pressedLink.obtainNewPath();
                                    int[] pos = cell.getRealSpanStartAndEnd(buffer, cell.pressedLink.getSpan());
                                    path.setCurrentLayout(cell.descriptionLayout, pos[0], 0);
                                    cell.descriptionLayout.getSelectionPath(pos[0], pos[1], path);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                                cell.links.addLink(cell.pressedLink, 2);
                            }
                            cell.invalidate();
                            return true;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (cell.pressedLinkType == 2 || gamePressed || cell.buttonPressed != 0) {
                if (cell.buttonPressed != 0) {
                    cell.buttonPressed = 0;
                    cell.playSoundEffect(SoundEffectConstants.CLICK);
                    cell.didPressButton(true, false);
                    cell.invalidate();
                } else if (cell.pressedLink != null) {
                    if (cell.pressedLink.getSpan() instanceof URLSpan) {
                        Browser.openUrl(cell.getContext(), ((URLSpan) cell.pressedLink.getSpan()).getURL());
                    } else if (cell.pressedLink.getSpan() instanceof ClickableSpan) {
                        ((ClickableSpan) cell.pressedLink.getSpan()).onClick(cell);
                    }
                    cell.resetPressedLink(2);
                } else {
                    gamePressed = false;
                    for (int a = 0; a < cell.botButtons.size(); a++) {
                        BotButton button = cell.botButtons.get(a);
                        if (TLKeyboardHelper.isType(button.button, TL_keyboard.TL_inlineButtonTypeGame.class)) {
                            cell.playSoundEffect(SoundEffectConstants.CLICK);
                            cell.delegate.didPressBotButton(cell, button.button);
                            cell.invalidate();
                            break;
                        }
                    }
                    cell.resetPressedLink(2);
                    return true;
                }
            } else {
                cell.resetPressedLink(2);
            }
        }
        return false;
    }
}
