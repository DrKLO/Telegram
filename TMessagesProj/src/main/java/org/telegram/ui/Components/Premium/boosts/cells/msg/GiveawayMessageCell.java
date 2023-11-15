package org.telegram.ui.Components.Premium.boosts.cells.msg;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceTags;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.StateSet;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.Premium.boosts.BoostDialogs;
import org.telegram.ui.Components.StaticLayoutEx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class GiveawayMessageCell {

    private static final Map<Integer, String> monthsToEmoticon = new HashMap<>();

    static {
        monthsToEmoticon.put(1, 1 + "\u20E3");
        monthsToEmoticon.put(3, 2 + "\u20E3");
        monthsToEmoticon.put(6, 3 + "\u20E3");
        monthsToEmoticon.put(12, 4 + "\u20E3");
        monthsToEmoticon.put(24, 5 + "\u20E3");
    }

    private ImageReceiver[] avatarImageReceivers;
    private AvatarDrawable[] avatarDrawables;
    private final ChatMessageCell parentView;
    private final ImageReceiver giftReceiver;

    private CharSequence[] chatTitles = new CharSequence[10];
    private TLRPC.Chat[] chats = new TLRPC.Chat[10];
    private float[] chatTitleWidths = new float[10];
    private boolean[] needNewRow = new boolean[10];
    private Rect[] clickRect = new Rect[10];
    private boolean[] avatarVisible;
    private int measuredHeight = 0;
    private int measuredWidth = 0;
    private int topHeight;
    private int bottomHeight;
    private int countriesHeight;
    private String counterStr;
    private int diffTextWidth;
    private StaticLayout topLayout;
    private StaticLayout bottomLayout;
    private StaticLayout countriesLayout;

    private final TextPaint counterTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint chatTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint countriesTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint counterBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chatBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint saveLayerPaint = new Paint();
    private final Paint clipRectPaint = new Paint();
    private final RectF countRect = new RectF();
    private final RectF chatRect = new RectF();
    private final Rect counterTextBounds = new Rect();
    private final Rect containerRect = new Rect();
    private final int[] pressedState = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};

    private int selectorColor;
    private Drawable selectorDrawable;
    private MessageObject messageObject;
    private int pressedPos = -1;
    private boolean isButtonPressed = false;
    private boolean isContainerPressed = false;

    public GiveawayMessageCell(ChatMessageCell parentView) {
        this.parentView = parentView;
        giftReceiver = new ImageReceiver(parentView);
        giftReceiver.setAllowLoadingOnAttachedOnly(true);

        clipRectPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        counterTextPaint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        counterTextPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        counterTextPaint.setTextSize(dp(12));
        counterTextPaint.setTextAlign(Paint.Align.CENTER);
        chatTextPaint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        chatTextPaint.setTextSize(dp(13));
        countriesTextPaint.setTextSize(dp(13));
        textPaint.setTextSize(dp(14));
    }

    public boolean checkMotionEvent(MotionEvent event) {
        if (messageObject == null || !messageObject.isGiveaway()) {
            return false;
        }

        int x = (int) event.getX();
        int y = (int) event.getY();

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
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
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
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
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {

        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (isButtonPressed) {
                setButtonPressed(false);
            }
            isButtonPressed = false;
            isContainerPressed = false;
        }
        return false;
    }

    public void setButtonPressed(boolean pressed) {
        if (messageObject == null || !messageObject.isGiveaway() || selectorDrawable == null) {
            return;
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
        topLayout = null;
        bottomLayout = null;
        countriesLayout = null;
        measuredHeight = 0;
        measuredWidth = 0;
        if (!messageObject.isGiveaway()) {
            return;
        }
        this.messageObject = messageObject;
        createImages();
        setGiftImage(messageObject);
        TLRPC.TL_messageMediaGiveaway giveaway = (TLRPC.TL_messageMediaGiveaway) messageObject.messageOwner.media;
        checkArraysLimits(giveaway.channels.size());

        int giftSize = AndroidUtilities.dp(148);
        int maxWidth;
        if (AndroidUtilities.isTablet()) {
            maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(80);
        } else {
            maxWidth = parentWidth - AndroidUtilities.dp(80);
        }

        CharSequence giveawayPrizes = replaceTags(getString("BoostingGiveawayPrizes", R.string.BoostingGiveawayPrizes));
        SpannableStringBuilder topStringBuilder = new SpannableStringBuilder(giveawayPrizes);
        topStringBuilder.setSpan(new RelativeSizeSpan(1.05f), 0, giveawayPrizes.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        topStringBuilder.append("\n");
        SpannableStringBuilder subTitleBuilder = new SpannableStringBuilder();
        subTitleBuilder.append(replaceTags(formatPluralStringComma("BoostingGiveawayMsgInfoPlural1", giveaway.quantity)));
        subTitleBuilder.append("\n");
        subTitleBuilder.append(replaceTags(formatPluralString("BoostingGiveawayMsgInfoPlural2", giveaway.quantity, LocaleController.formatPluralString("BoldMonths", giveaway.months))));
        CharSequence subTitle = subTitleBuilder;

        topStringBuilder.append(subTitleBuilder);
        topStringBuilder.append("\n\n");

        topStringBuilder.setSpan(new RelativeSizeSpan(0.5f), topStringBuilder.length() - 1, topStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        CharSequence participants = replaceTags(getString("BoostingGiveawayMsgParticipants", R.string.BoostingGiveawayMsgParticipants));
        topStringBuilder.append(participants);
        topStringBuilder.setSpan(new RelativeSizeSpan(1.05f), giveawayPrizes.length() + subTitle.length() + 2, giveawayPrizes.length() + subTitle.length() + 3 + participants.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        topStringBuilder.append("\n");

        if (giveaway.only_new_subscribers) {
            topStringBuilder.append(formatPluralString("BoostingGiveawayMsgNewSubsPlural", giveaway.channels.size()));
        } else {
            topStringBuilder.append(formatPluralString("BoostingGiveawayMsgAllSubsPlural", giveaway.channels.size()));
        }

        CharSequence dateTitle = replaceTags(getString("BoostingWinnersDate", R.string.BoostingWinnersDate));
        SpannableStringBuilder bottomStringBuilder = new SpannableStringBuilder(dateTitle);
        bottomStringBuilder.setSpan(new RelativeSizeSpan(1.05f), 0, dateTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Date date = new Date(giveaway.until_date * 1000L);
        String monthTxt = LocaleController.getInstance().formatterGiveawayCard.format(date);
        String timeTxt = LocaleController.getInstance().formatterDay.format(date);
        bottomStringBuilder.append("\n");
        bottomStringBuilder.append(formatString("formatDateAtTime", R.string.formatDateAtTime, monthTxt, timeTxt));

        topLayout = StaticLayoutEx.createStaticLayout(topStringBuilder, textPaint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, AndroidUtilities.dp(2), false, TextUtils.TruncateAt.END, maxWidth, 10);
        bottomLayout = StaticLayoutEx.createStaticLayout(bottomStringBuilder, textPaint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, AndroidUtilities.dp(2), false, TextUtils.TruncateAt.END, maxWidth, 10);

        int maxRowLength = 0;
        for (int a = 0; a < topLayout.getLineCount(); ++a) {
            maxRowLength = (int) Math.max(maxRowLength, Math.ceil(topLayout.getLineWidth(a)));
        }
        for (int a = 0; a < bottomLayout.getLineCount(); ++a) {
            maxRowLength = (int) Math.max(maxRowLength, Math.ceil(bottomLayout.getLineWidth(a)));
        }

        if (maxRowLength < dp(180)) {
            maxRowLength = dp(180);
        }

        if (giveaway.countries_iso2.size() > 0) {
            List<CharSequence> countriesWithFlags = new ArrayList<>();
            for (String iso2 : giveaway.countries_iso2) {
                String countryName = (new Locale("", iso2)).getDisplayCountry(Locale.getDefault());
                String flag = LocaleController.getLanguageFlag(iso2);
                SpannableStringBuilder builder = new SpannableStringBuilder();
                if (flag != null) {
                    builder.append(flag).append("\u00A0");
                }
                builder.append(countryName);
                countriesWithFlags.add(builder);
            }
            if (!countriesWithFlags.isEmpty()) {
                CharSequence txt = replaceTags(formatString("BoostingGiveAwayFromCountries", R.string.BoostingGiveAwayFromCountries, TextUtils.join(", ", countriesWithFlags)));
                txt = Emoji.replaceEmoji(txt, countriesTextPaint.getFontMetricsInt(), false);
                countriesLayout = StaticLayoutEx.createStaticLayout(txt, countriesTextPaint, maxRowLength, Layout.Alignment.ALIGN_CENTER, 1.0f, 0, false, TextUtils.TruncateAt.END, maxRowLength, 10);
            }
        }

        int oldMaxWidth = maxWidth;
        maxWidth = Math.min(maxRowLength + dp(38), maxWidth);
        maxWidth = Math.max(forwardedNameWidth, maxWidth);
        diffTextWidth = maxWidth - oldMaxWidth;

        giftReceiver.setImageCoords((maxWidth / 2f) - (giftSize / 2f), AndroidUtilities.dp(42) - giftSize / 2f, giftSize, giftSize);

        topHeight = topLayout.getLineBottom(topLayout.getLineCount() - 1);
        bottomHeight = bottomLayout.getLineBottom(bottomLayout.getLineCount() - 1);
        countriesHeight = countriesLayout != null ? (countriesLayout.getLineBottom(countriesLayout.getLineCount() - 1) + dp(4 + 8)) : 0;

        measuredHeight += topHeight;
        measuredHeight += countriesHeight;
        measuredHeight += bottomHeight;
        measuredHeight += dp(32 + 96); //gift
        measuredWidth = maxWidth;

        counterStr = "x" + giveaway.quantity;
        counterTextPaint.getTextBounds(counterStr, 0, counterStr.length(), counterTextBounds);

        Arrays.fill(avatarVisible, false);

        float oneRowTotalWidth = 0;
        measuredHeight += dp(24 + 6);

        List<Long> visibleChannels = new ArrayList<>(giveaway.channels.size());
        for (Long channel : giveaway.channels) {
            TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(channel);
            if (chat != null) {
                visibleChannels.add(channel);
            }
        }

        for (int i = 0; i < visibleChannels.size(); i++) {
            long channelId = visibleChannels.get(i);
            TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(channelId);

            if (chat != null) {
                avatarVisible[i] = true;
                chats[i] = chat;
                CharSequence text = Emoji.replaceEmoji(chat.title, chatTextPaint.getFontMetricsInt(), dp(14), false);
                chatTitles[i] = TextUtils.ellipsize(text, chatTextPaint, maxWidth * 0.8f, TextUtils.TruncateAt.END);
                chatTitleWidths[i] = chatTextPaint.measureText(chatTitles[i], 0, chatTitles[i].length());
                float oneRowWidth = chatTitleWidths[i] + dp(24 + 6 + 12);
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
                avatarDrawables[i].setInfo(chat);
                avatarImageReceivers[i].setForUserOrChat(chat, avatarDrawables[i]);
                avatarImageReceivers[i].setImageCoords(0, 0, dp(24), dp(24));
            } else {
                chats[i] = null;
                avatarVisible[i] = false;
                chatTitles[i] = "";
                needNewRow[i] = false;
                chatTitleWidths[i] = dp(20);
                avatarDrawables[i].setInfo(channelId, "", "");
            }
        }
    }

    private int getChatColor(TLRPC.Chat chat, Theme.ResourcesProvider resourcesProvider) {
        final int color;
        int colorId = (chat.flags2 & 64) != 0 ? chat.color : (int) (chat.id % 7);
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
        if (messageObject == null || !messageObject.isGiveaway()) {
            return;
        }

        if (selectorDrawable == null) {
            selectorDrawable = Theme.createRadSelectorDrawable(selectorColor = Theme.getColor(Theme.key_listSelector), 12, 12);
        }

        textPaint.setColor(Theme.chat_msgTextPaint.getColor());
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
        canvas.drawText(counterStr, countRect.centerX(), countRect.centerY() + dp(4), counterTextPaint);
        canvas.restore();

        canvas.translate(0, dp(32 + 96));
        y += dp(32 + 96);

        canvas.save();
        canvas.translate(diffTextWidth / 2f, 0);
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
                    rowWidth += chatTitleWidths[k] + dp(24 + 6 + 12);
                    k++;
                } while (k < avatarVisible.length && !needNewRow[k] && avatarVisible[k]);

                float marginItemsLeft = centerX - (rowWidth / 2f);
                canvas.translate(marginItemsLeft, 0);
                int xRow = x + (int) (marginItemsLeft);

                k = i;

                do {
                    int chatColor = getChatColor(chats[k], resourcesProvider);
                    if (pressedPos >= 0 && pressedPos == k) {
                        selectedChatColor = chatColor;
                    }
                    chatTextPaint.setColor(chatColor);
                    chatBgPaint.setColor(chatColor);
                    chatBgPaint.setAlpha(25);
                    avatarImageReceivers[k].draw(canvas);
                    canvas.drawText(chatTitles[k], 0, chatTitles[k].length(), dp(24 + 6), dp(16), chatTextPaint);
                    chatRect.set(0, 0, chatTitleWidths[k] + dp(24 + 6 + 12), dp(24));
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
            selectorDrawable.draw(canvas);
        }
    }

    public void onDetachedFromWindow() {
        giftReceiver.onDetachedFromWindow();
        if (avatarImageReceivers != null) {
            for (ImageReceiver avatarImageReceiver : avatarImageReceivers) {
                avatarImageReceiver.onDetachedFromWindow();
            }
        }
    }

    public void onAttachedToWindow() {
        giftReceiver.onAttachedToWindow();
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
            chatTitles = Arrays.copyOf(chatTitles, channelsCount);
            chatTitleWidths = Arrays.copyOf(chatTitleWidths, channelsCount);
            needNewRow = Arrays.copyOf(needNewRow, channelsCount);
            clickRect = Arrays.copyOf(clickRect, channelsCount);
            chats = Arrays.copyOf(chats, channelsCount);

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

    private void setGiftImage(MessageObject messageObject) {
        TLRPC.TL_messageMediaGiveaway giveaway = (TLRPC.TL_messageMediaGiveaway) messageObject.messageOwner.media;
        TLRPC.TL_messages_stickerSet set;
        TLRPC.Document document = null;

        String packName = UserConfig.getInstance(UserConfig.selectedAccount).premiumGiftsStickerPack;
        if (packName == null) {
            MediaDataController.getInstance(UserConfig.selectedAccount).checkPremiumGiftStickers();
            return;
        }
        set = MediaDataController.getInstance(UserConfig.selectedAccount).getStickerSetByName(packName);
        if (set == null) {
            set = MediaDataController.getInstance(UserConfig.selectedAccount).getStickerSetByEmojiOrName(packName);
        }
        if (set != null) {
            int months = giveaway.months;
            String monthsEmoticon = monthsToEmoticon.get(months);
            for (TLRPC.TL_stickerPack pack : set.packs) {
                if (Objects.equals(pack.emoticon, monthsEmoticon)) {
                    for (long id : pack.documents) {
                        for (TLRPC.Document doc : set.documents) {
                            if (doc.id == id) {
                                document = doc;
                                break;
                            }
                        }
                        if (document != null) {
                            break;
                        }
                    }
                }
                if (document != null) {
                    break;
                }
            }
            if (document == null && !set.documents.isEmpty()) {
                document = set.documents.get(0);
            }
        }
        if (document != null) {
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document.thumbs, Theme.key_emptyListPlaceholder, 0.2f);
            if (svgThumb != null) {
                svgThumb.overrideWidthAndHeight(512, 512);
            }
            giftReceiver.setImage(ImageLocation.getForDocument(document), "160_160_firstframe", svgThumb, "tgs", set, 1);
        } else {
            MediaDataController.getInstance(UserConfig.selectedAccount).loadStickersByEmojiOrName(packName, false, set == null);
        }
    }
}
