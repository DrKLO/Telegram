package org.telegram.ui.Components.Premium.boosts.cells.msg;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceTags;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getPluralString;
import static org.telegram.messenger.LocaleController.getString;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.RelativeSizeSpan;
import android.util.StateSet;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.boosts.BoostDialogs;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GiveawayResultsMessageCell {

    private ImageReceiver[] avatarImageReceivers;
    private AvatarDrawable[] avatarDrawables;
    private final ChatMessageCell parentView;
    private ImageReceiver giftReceiver;
    private RLottieDrawable giftDrawable;

    private CharSequence[] userTitles;
    private TLRPC.User[] users;
    private float[] userTitleWidths;
    private boolean[] needNewRow;
    private Rect[] clickRect;
    private boolean[] avatarVisible;
    private int measuredHeight = 0;
    private int measuredWidth = 0;

    private int titleHeight;
    private int topHeight;
    private int bottomHeight;
    private int countriesHeight;
    private Drawable counterIcon;
    private String counterStr;
    private int diffTextWidth;

    private StaticLayout titleLayout;
    private StaticLayout topLayout;
    private StaticLayout bottomLayout;
    private StaticLayout countriesLayout;

    private TextPaint counterTextPaint;
    private TextPaint counterStarsTextPaint;
    private TextPaint chatTextPaint;
    private TextPaint textPaint;
    private TextPaint textDividerPaint;
    private TextPaint countriesTextPaint;
    private Paint counterBgPaint;
    private Paint chatBgPaint;

    private Paint saveLayerPaint;
    private Paint clipRectPaint;
    private RectF countRect;
    private RectF chatRect;
    private Rect counterTextBounds;
    private Rect containerRect;
    private int[] pressedState;

    private int selectorColor;
    private Drawable selectorDrawable;
    private MessageObject messageObject;
    private boolean isStars;
    private int pressedPos = -1;
    private boolean isButtonPressed = false;
    private boolean isContainerPressed = false;
    private SpannableStringBuilder topStringBuilder;
    private int subTitleMarginTop;
    private int subTitleMarginLeft;
    private LinkSpanDrawable.LinkCollector links;

    public GiveawayResultsMessageCell(ChatMessageCell parentView) {
        this.parentView = parentView;
    }

    private void init() {
        if (counterTextPaint != null) {
            return;
        }
        counterTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        counterStarsTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        chatTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textDividerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        countriesTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        counterBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        chatBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        saveLayerPaint = new Paint();
        clipRectPaint = new Paint();
        countRect = new RectF();
        chatRect = new RectF();
        counterTextBounds = new Rect();
        containerRect = new Rect();
        pressedState = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};

        userTitles = new CharSequence[10];
        users = new TLRPC.User[10];
        userTitleWidths = new float[10];
        needNewRow = new boolean[10];
        clickRect = new Rect[10];

        giftReceiver = new ImageReceiver(parentView);
        giftReceiver.setAllowLoadingOnAttachedOnly(true);

        clipRectPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        counterTextPaint.setTypeface(AndroidUtilities.bold());
        counterTextPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        counterTextPaint.setTextSize(dp(12));
        counterTextPaint.setTextAlign(Paint.Align.CENTER);
        counterStarsTextPaint.setTypeface(AndroidUtilities.bold());
        counterStarsTextPaint.setTextSize(dp(12));
        counterStarsTextPaint.setTextAlign(Paint.Align.CENTER);
        counterStarsTextPaint.setColor(0xFFFFFFFF);
        chatTextPaint.setTypeface(AndroidUtilities.bold());
        chatTextPaint.setTextSize(dp(13));
        countriesTextPaint.setTextSize(dp(13));
        textPaint.setTextSize(dp(14));
        textDividerPaint.setTextSize(dp(14));
        textDividerPaint.setTextAlign(Paint.Align.CENTER);
    }

    public boolean checkMotionEvent(MotionEvent event) {
        if (messageObject == null || !messageObject.isGiveawayResults()) {
            return false;
        }

        if (links == null) {
            links = new LinkSpanDrawable.LinkCollector(parentView);
        }

        int action = event.getAction();
        int x = (int) event.getX();
        int y = (int) event.getY();

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            if (topStringBuilder != null && topLayout != null && (y - subTitleMarginTop) > 0) {
                int line = topLayout.getLineForVertical(y - subTitleMarginTop - dp(10));
                int off = topLayout.getOffsetForHorizontal(line, x - subTitleMarginLeft);
                ClickableSpan[] link = topStringBuilder.getSpans(off, off, ClickableSpan.class);
                if (link.length != 0) {
                    if (action == MotionEvent.ACTION_UP) {
                        links.clear();
                        link[0].onClick(parentView);
                    } else {
                        LinkSpanDrawable<ClickableSpan> pressedLink = new LinkSpanDrawable<>(link[0], null, x, y);
                        links.addLink(pressedLink);
                        try {
                            int start = topStringBuilder.getSpanStart(link[0]);
                            LinkPath path = pressedLink.obtainNewPath();
                            path.setCurrentLayout(topLayout, start, subTitleMarginLeft, subTitleMarginTop);
                            topLayout.getSelectionPath(start, topStringBuilder.getSpanEnd(link[0]), path);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    return true;
                } else {
                    links.clear();
                }
                parentView.invalidate();
            }
        }

        if (action == MotionEvent.ACTION_DOWN) {
            for (int i = 0; i < clickRect.length; i++) {
                Rect rect = clickRect[i];
                if (rect.contains(x, y)) {
                    pressedPos = i;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        selectorDrawable.setHotspot(x, y);
                    }
                    isButtonPressed = true;
                    setButtonPressed(true);
                    return true;
                }
            }
            if (containerRect.contains(x, y)) {
                isContainerPressed = true;
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP) {
            if (isButtonPressed) {
                if (parentView.getDelegate() != null) {
                    parentView.getDelegate().didPressGiveawayChatButton(parentView, pressedPos);
                }
                parentView.playSoundEffect(SoundEffectConstants.CLICK);
                setButtonPressed(false);
                isButtonPressed = false;
            }
            if (isContainerPressed) {
                isContainerPressed = false;
                BoostDialogs.showBulletinAbout(messageObject);
            }
        } else if (action == MotionEvent.ACTION_MOVE) {

        } else if (action == MotionEvent.ACTION_CANCEL) {
            links.clear();
            if (isButtonPressed) {
                setButtonPressed(false);
            }
            isButtonPressed = false;
            isContainerPressed = false;
        }
        return false;
    }

    public void setButtonPressed(boolean pressed) {
        if (messageObject == null || !messageObject.isGiveawayResults() || selectorDrawable == null) {
            return;
        }
        if (links != null) {
            links.clear();
        }
        if (pressed) {
            selectorDrawable.setCallback(new Drawable.Callback() {
                @Override
                public void invalidateDrawable(@NonNull Drawable who) {
                    parentView.invalidate();
                }

                @Override
                public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
                    parentView.invalidate();
                }

                @Override
                public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
                    parentView.invalidate();
                }
            });
            selectorDrawable.setState(pressedState);
            parentView.invalidate();
        } else {
            selectorDrawable.setState(StateSet.NOTHING);
            parentView.invalidate();
        }
    }

    public void setMessageContent(MessageObject messageObject, int parentWidth, int forwardedNameWidth) {
        this.messageObject = null;
        titleLayout = null;
        topLayout = null;
        bottomLayout = null;
        countriesLayout = null;
        measuredHeight = 0;
        measuredWidth = 0;
        isStars = false;
        if (!messageObject.isGiveawayResults()) {
            return;
        }
        this.messageObject = messageObject;
        init();
        createImages();
        setGiftImage();
        TLRPC.TL_messageMediaGiveawayResults giveaway = (TLRPC.TL_messageMediaGiveawayResults) messageObject.messageOwner.media;
        checkArraysLimits(giveaway.winners.size());

        int giftSize = AndroidUtilities.dp(90);
        int maxWidth = AndroidUtilities.dp(230);

        CharSequence winnersSelected = replaceTags(getString("BoostingGiveawayResultsMsgWinnersSelected", R.string.BoostingGiveawayResultsMsgWinnersSelected));
        SpannableStringBuilder titleStringBuilder = new SpannableStringBuilder(winnersSelected);
        titleStringBuilder.setSpan(new RelativeSizeSpan(1.05f), 0, winnersSelected.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        topStringBuilder = new SpannableStringBuilder();
        String subTitleText = getPluralString("BoostingGiveawayResultsMsgWinnersTitle", giveaway.winners_count);
        SpannableStringBuilder subTitleWithLink = AndroidUtilities.replaceSingleTag(
                subTitleText,
                Theme.key_chat_messageLinkIn, 0,
                () -> AndroidUtilities.runOnUIThread(() -> {
                    if (messageObject.getDialogId() == -giveaway.channel_id) {
                        parentView.getDelegate().didPressReplyMessage(parentView, giveaway.launch_msg_id);
                    } else {
                        Bundle bundle = new Bundle();
                        bundle.putLong("chat_id", giveaway.channel_id);
                        bundle.putInt("message_id", giveaway.launch_msg_id);
                        LaunchActivity.getLastFragment().presentFragment(new ChatActivity(bundle));
                    }
                })
        );
        topStringBuilder.append(AndroidUtilities.replaceCharSequence("%1$d", subTitleWithLink, replaceTags("**" + giveaway.winners_count + "**")));

        topStringBuilder.append("\n\n");
        topStringBuilder.setSpan(new RelativeSizeSpan(0.4f), topStringBuilder.length() - 1, topStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        CharSequence winners = replaceTags(getPluralString("BoostingGiveawayResultsMsgWinners", giveaway.winners_count));
        topStringBuilder.append(winners);
        topStringBuilder.setSpan(new RelativeSizeSpan(1.05f), subTitleWithLink.length() + 2, subTitleWithLink.length() + 2 + winners.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        SpannableStringBuilder bottomStringBuilder = new SpannableStringBuilder();
        if (giveaway.winners_count != giveaway.winners.size()) {
            bottomStringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayResultsMsgAllAndMoreWinners", giveaway.winners_count - giveaway.winners.size())));
            bottomStringBuilder.setSpan(new RelativeSizeSpan(1.05f), 0, bottomStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            bottomStringBuilder.append("\n");
        }
        isStars = (giveaway.flags & 32) != 0;
        if (isStars) {
            bottomStringBuilder.append(LocaleController.formatPluralStringSpaced("BoostingStarsGiveawayResultsMsgAllWinnersReceivedLinks", (int) giveaway.stars));
        } else {
            bottomStringBuilder.append(LocaleController.getString(R.string.BoostingGiveawayResultsMsgAllWinnersReceivedLinks));
        }

        titleLayout = StaticLayoutEx.createStaticLayout(titleStringBuilder, textPaint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, AndroidUtilities.dp(2), false, TextUtils.TruncateAt.END, maxWidth, 10);
        topLayout = StaticLayoutEx.createStaticLayout(topStringBuilder, textPaint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, AndroidUtilities.dp(2), false, TextUtils.TruncateAt.END, maxWidth, 10);
        bottomLayout = StaticLayoutEx.createStaticLayout(bottomStringBuilder, textPaint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, AndroidUtilities.dp(3), false, TextUtils.TruncateAt.END, maxWidth, 10);

        int oldMaxWidth = maxWidth;
        maxWidth = Math.max(forwardedNameWidth, maxWidth);
        diffTextWidth = maxWidth - oldMaxWidth;

        giftReceiver.setImageCoords((maxWidth / 2f) - (giftSize / 2f), AndroidUtilities.dp(70) - giftSize / 2f, giftSize, giftSize);

        titleHeight = titleLayout.getLineBottom(titleLayout.getLineCount() - 1) + dp(5);
        topHeight = titleHeight + topLayout.getLineBottom(topLayout.getLineCount() - 1);
        bottomHeight = bottomLayout.getLineBottom(bottomLayout.getLineCount() - 1);
        countriesHeight = countriesLayout != null ? (countriesLayout.getLineBottom(countriesLayout.getLineCount() - 1) + dp(4 + 8)) : 0;

        measuredHeight += topHeight;
        measuredHeight += countriesHeight;
        measuredHeight += bottomHeight;
        measuredHeight += dp(32 + 96); //gift
        measuredWidth = maxWidth;

        if (isStars) {
            if (counterIcon == null) {
                counterIcon = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.filled_giveaway_stars).mutate();
            }
            counterStr = LocaleController.formatNumber((int) giveaway.stars, ',');
        } else {
            counterIcon = null;
            counterStr = "x" + giveaway.winners_count;
        }
        counterTextPaint.getTextBounds(counterStr, 0, counterStr.length(), counterTextBounds);
        if (isStars) {
            counterTextBounds.right += dp(20);
        }

        Arrays.fill(avatarVisible, false);

        float oneRowTotalWidth = 0;
        measuredHeight += dp(24 + 6);

        List<Long> visibleChannels = new ArrayList<>(giveaway.winners.size());
        for (Long uid : giveaway.winners) {
            TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(uid);
            if (user != null) {
                visibleChannels.add(uid);
            }
        }

        for (int i = 0; i < visibleChannels.size(); i++) {
            long uid = visibleChannels.get(i);
            TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(uid);

            if (user != null) {
                avatarVisible[i] = true;
                users[i] = user;
                CharSequence text = Emoji.replaceEmoji(UserObject.getUserName(user), chatTextPaint.getFontMetricsInt(), false);
                userTitles[i] = TextUtils.ellipsize(text, chatTextPaint, maxWidth * 0.8f, TextUtils.TruncateAt.END);
                userTitleWidths[i] = chatTextPaint.measureText(userTitles[i], 0, userTitles[i].length());
                float oneRowWidth = userTitleWidths[i] + dp(24 + 6 + 10);
                oneRowTotalWidth += oneRowWidth;
                if (i > 0) {
                    needNewRow[i] = oneRowTotalWidth > maxWidth * 0.9f;
                    if (needNewRow[i]) {
                        oneRowTotalWidth = oneRowWidth;
                        measuredHeight += dp(24 + 6);
                    }
                } else {
                    needNewRow[i] = false;
                }
                avatarDrawables[i].setInfo(user);
                avatarImageReceivers[i].setForUserOrChat(user, avatarDrawables[i]);
                avatarImageReceivers[i].setImageCoords(0, 0, dp(24), dp(24));
            } else {
                users[i] = null;
                avatarVisible[i] = false;
                userTitles[i] = "";
                needNewRow[i] = false;
                userTitleWidths[i] = dp(20);
                avatarDrawables[i].setInfo(uid, "", "");
            }
        }
    }

    private int getUserColor(TLRPC.User user, Theme.ResourcesProvider resourcesProvider) {
        if (messageObject.isOutOwner()) {
            return Theme.getColor(Theme.key_chat_outPreviewInstantText, resourcesProvider);
        }
        final int color;
        int colorId = UserObject.getColorId(user);
        if (colorId < 7) {
            color = Theme.getColor(Theme.keys_avatar_nameInMessage[colorId], resourcesProvider);
        } else {
            MessagesController.PeerColors peerColors = MessagesController.getInstance(UserConfig.selectedAccount).peerColors;
            MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(colorId);
            if (peerColor != null) {
                color = peerColor.getColor1();
            } else {
                color = Theme.getColor(Theme.keys_avatar_nameInMessage[0], resourcesProvider);
            }
        }
        return color;
    }

    public void draw(Canvas canvas, int marginTop, int marginLeft, Theme.ResourcesProvider resourcesProvider) {
        if (messageObject == null || !messageObject.isGiveawayResults()) {
            return;
        }

        if (selectorDrawable == null) {
            selectorDrawable = Theme.createRadSelectorDrawable(selectorColor = Theme.getColor(Theme.key_listSelector), 12, 12);
            selectorDrawable.setCallback(parentView);
        }

        textPaint.setColor(Theme.chat_msgTextPaint.getColor());
        textDividerPaint.setColor(Theme.getColor(Theme.key_dialogTextGray2));
        countriesTextPaint.setColor(Theme.chat_msgTextPaint.getColor());

        if (messageObject.isOutOwner()) {
            chatTextPaint.setColor(Theme.getColor(Theme.key_chat_outPreviewInstantText, resourcesProvider));
            counterBgPaint.setColor(Theme.getColor(Theme.key_chat_outPreviewInstantText, resourcesProvider));
            chatBgPaint.setColor(Theme.getColor(Theme.key_chat_outReplyLine, resourcesProvider));
        } else {
            chatTextPaint.setColor(Theme.getColor(Theme.key_chat_inPreviewInstantText, resourcesProvider));
            counterBgPaint.setColor(Theme.getColor(Theme.key_chat_inPreviewInstantText, resourcesProvider));
            chatBgPaint.setColor(Theme.getColor(Theme.key_chat_inReplyLine, resourcesProvider));
        }

        if (isStars) {
            counterBgPaint.setColor(Theme.getColor(Theme.key_starsGradient1, resourcesProvider));
        }

        int x = 0, y = 0;
        canvas.save();
        x = marginLeft - dp(4);
        y = marginTop;
        canvas.translate(x, y);
        containerRect.set(x, y, getMeasuredWidth() + x, getMeasuredHeight() + y);

        canvas.saveLayer(0, 0, getMeasuredWidth(), getMeasuredHeight(), saveLayerPaint, Canvas.ALL_SAVE_FLAG);
        giftReceiver.draw(canvas);

        float centerX = getMeasuredWidth() / 2f;
        float centerY = dp(106);
        int textWidth = counterTextBounds.width() + dp(12);
        int textHeight = counterTextBounds.height() + dp(10);
        countRect.set(
                centerX - ((textWidth + dp(2)) / 2f),
                centerY - ((textHeight + dp(2)) / 2f),
                centerX + ((textWidth + dp(2)) / 2f),
                centerY + ((textHeight + dp(2)) / 2f)
        );
        canvas.drawRoundRect(countRect, dp(11), dp(11), clipRectPaint);
        countRect.set(
                centerX - ((textWidth) / 2f),
                centerY - ((textHeight) / 2f),
                centerX + ((textWidth) / 2f),
                centerY + ((textHeight) / 2f)
        );
        canvas.drawRoundRect(countRect, dp(10), dp(10), counterBgPaint);
        if (counterIcon != null) {
            final float s = .58f;
            counterIcon.setBounds((int) countRect.left + dp(5), (int) countRect.centerY() - dp(12 * s), (int) countRect.left + dp(5 + 28 * s), (int) countRect.centerY() + dp(12 * s));
            counterIcon.draw(canvas);
        }
        canvas.drawText(counterStr, countRect.centerX() + dp(isStars ? 8 : 0), countRect.centerY() + dp(4), isStars ? counterStarsTextPaint : counterTextPaint);
        canvas.restore();

        canvas.translate(0, dp(32 + 96));
        y += dp(32 + 96);
        subTitleMarginTop = y + titleHeight;
        subTitleMarginLeft = (int) (x + diffTextWidth / 2f);

        canvas.save();
        canvas.translate(diffTextWidth / 2f, 0);
        titleLayout.draw(canvas);
        canvas.translate(0, titleHeight);

        topLayout.draw(canvas);
        canvas.restore();
        canvas.translate(0, topHeight + dp(6));
        y += topHeight + dp(6);

        int selectedChatColor = 0;
        int i = 0;
        while (i < avatarVisible.length) {
            if (avatarVisible[i]) {
                canvas.save();
                int k = i;
                float rowWidth = 0;

                do {
                    rowWidth += userTitleWidths[k] + dp(24 + 6 + 10);
                    k++;
                } while (k < avatarVisible.length && !needNewRow[k] && avatarVisible[k]);

                float marginItemsLeft = centerX - (rowWidth / 2f);
                canvas.translate(marginItemsLeft, 0);
                int xRow = x + (int) (marginItemsLeft);

                k = i;

                do {
                    int chatColor = getUserColor(users[k], resourcesProvider);
                    if (pressedPos >= 0 && pressedPos == k) {
                        selectedChatColor = chatColor;
                    }
                    chatTextPaint.setColor(chatColor);
                    chatBgPaint.setColor(chatColor);
                    chatBgPaint.setAlpha(25);
                    avatarImageReceivers[k].draw(canvas);
                    canvas.drawText(userTitles[k], 0, userTitles[k].length(), dp(24 + 6), dp(16), chatTextPaint);
                    chatRect.set(0, 0, userTitleWidths[k] + dp(24 + 6 + 10), dp(24));
                    canvas.drawRoundRect(chatRect, dp(12), dp(12), chatBgPaint);

                    clickRect[k].set(xRow, y, (int) (xRow + chatRect.width()), y + dp(24));

                    canvas.translate(chatRect.width() + dp(6), 0);
                    xRow += chatRect.width() + dp(6);

                    k++;
                } while (k < avatarVisible.length && !needNewRow[k] && avatarVisible[k]);

                i = k;

                canvas.restore();
                canvas.translate(0, dp(24 + 6));
                y += dp(24 + 6);
            } else {
                i++;
            }
        }

        if (countriesLayout != null) {
            canvas.save();
            canvas.translate((measuredWidth - countriesLayout.getWidth()) / 2f, dp(4));
            countriesLayout.draw(canvas);
            canvas.restore();
            canvas.translate(0, countriesHeight);
        }

        canvas.translate(0, dp(6));
        canvas.save();
        canvas.translate(diffTextWidth / 2f, 0);
        bottomLayout.draw(canvas);
        canvas.restore();
        canvas.restore();
        if (pressedPos >= 0) {
            int rippleColor = Theme.multAlpha(selectedChatColor, Theme.isCurrentThemeDark() ? 0.12f : 0.10f);
            if (selectorColor != rippleColor) {
                Theme.setSelectorDrawableColor(selectorDrawable, selectorColor = rippleColor, true);
            }
            selectorDrawable.setBounds(clickRect[pressedPos]);
            selectorDrawable.setCallback(parentView);
//            selectorDrawable.draw(canvas);
        }

        if (links != null && links.draw(canvas)) {
            parentView.invalidate();
        }
    }

    public void onDetachedFromWindow() {
        if (giftReceiver != null) {
            giftReceiver.onDetachedFromWindow();
        }
        if (avatarImageReceivers != null) {
            for (ImageReceiver avatarImageReceiver : avatarImageReceivers) {
                avatarImageReceiver.onDetachedFromWindow();
            }
        }
    }

    public void onAttachedToWindow() {
        if (giftReceiver != null) {
            giftReceiver.onAttachedToWindow();
        }
        if (avatarImageReceivers != null) {
            for (ImageReceiver avatarImageReceiver : avatarImageReceivers) {
                avatarImageReceiver.onAttachedToWindow();
            }
        }
    }

    public int getMeasuredHeight() {
        return measuredHeight;
    }

    public int getMeasuredWidth() {
        return measuredWidth;
    }

    private void createImages() {
        if (avatarImageReceivers != null) {
            return;
        }

        avatarImageReceivers = new ImageReceiver[10];
        avatarDrawables = new AvatarDrawable[10];
        avatarVisible = new boolean[10];
        for (int a = 0; a < avatarImageReceivers.length; a++) {
            avatarImageReceivers[a] = new ImageReceiver(parentView);
            avatarImageReceivers[a].setAllowLoadingOnAttachedOnly(true);
            avatarImageReceivers[a].setRoundRadius(AndroidUtilities.dp(12));
            avatarDrawables[a] = new AvatarDrawable();
            avatarDrawables[a].setTextSize(AndroidUtilities.dp(18));
            clickRect[a] = new Rect();
        }
    }

    private void checkArraysLimits(int channelsCount) {
        if (avatarImageReceivers.length < channelsCount) {
            int oldLength = avatarImageReceivers.length;
            avatarImageReceivers = Arrays.copyOf(avatarImageReceivers, channelsCount);
            avatarDrawables = Arrays.copyOf(avatarDrawables, channelsCount);
            avatarVisible = Arrays.copyOf(avatarVisible, channelsCount);
            userTitles = Arrays.copyOf(userTitles, channelsCount);
            userTitleWidths = Arrays.copyOf(userTitleWidths, channelsCount);
            needNewRow = Arrays.copyOf(needNewRow, channelsCount);
            clickRect = Arrays.copyOf(clickRect, channelsCount);
            users = Arrays.copyOf(users, channelsCount);

            for (int i = oldLength - 1; i < channelsCount; i++) {
                avatarImageReceivers[i] = new ImageReceiver(parentView);
                avatarImageReceivers[i].setAllowLoadingOnAttachedOnly(true);
                avatarImageReceivers[i].setRoundRadius(AndroidUtilities.dp(12));
                avatarDrawables[i] = new AvatarDrawable();
                avatarDrawables[i].setTextSize(AndroidUtilities.dp(18));
                clickRect[i] = new Rect();
            }
        }
    }

    private void setGiftImage() {
        giftReceiver.setAllowStartLottieAnimation(false);
        if (giftDrawable == null) {
            giftDrawable = new RLottieDrawable(R.raw.giveaway_results, "" + R.raw.giveaway_results, dp(120), dp(120));
        }
        giftReceiver.setImageBitmap(giftDrawable);
    }
}
