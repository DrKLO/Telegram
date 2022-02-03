/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Base64;

import androidx.collection.LongSparseArray;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.spoilers.SpoilerEffect;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanBotCommand;
import org.telegram.ui.Components.URLSpanBrowser;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.URLSpanNoUnderlineBold;
import org.telegram.ui.Components.URLSpanReplacement;
import org.telegram.ui.Components.URLSpanUserMention;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.net.URLEncoder;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageObject {

    public static final int MESSAGE_SEND_STATE_SENT = 0;
    public static final int MESSAGE_SEND_STATE_SENDING = 1;
    public static final int MESSAGE_SEND_STATE_SEND_ERROR = 2;
    public static final int MESSAGE_SEND_STATE_EDITING = 3;

    public static final int TYPE_PHOTO = 1;
    public static final int TYPE_VIDEO = 3;
    public static final int TYPE_GEO = 4; // TL_messageMediaGeo, TL_messageMediaVenue, TL_messageMediaGeoLive
    public static final int TYPE_ROUND_VIDEO = 5;
    public static final int TYPE_STICKER = 13;
    public static final int TYPE_ANIMATED_STICKER = 15;
    public static final int TYPE_POLL = 17;

    public int localType;
    public String localName;
    public String localUserName;
    public long localGroupId;
    public long localSentGroupId;
    public boolean localChannel;
    public boolean localSupergroup;
    public Boolean cachedIsSupergroup;
    public boolean localEdit;
    public TLRPC.Message messageOwner;
    public TLRPC.Document emojiAnimatedSticker;
    public String emojiAnimatedStickerColor;
    public CharSequence messageText;
    public CharSequence linkDescription;
    public CharSequence caption;
    public MessageObject replyMessageObject;
    public int type = 1000;
    public long reactionsLastCheckTime;
    public String customName;
    public boolean reactionsChanged;
    private int isRoundVideoCached;
    public long eventId;
    public int contentType;
    public String dateKey;
    public String monthKey;
    public boolean deleted;
    public float audioProgress;
    public float forceSeekTo = -1;
    public int audioProgressMs;
    public float bufferedProgress;
    public float gifState;
    public int audioProgressSec;
    public int audioPlayerDuration;
    public boolean isDateObject;
    public TLObject photoThumbsObject;
    public TLObject photoThumbsObject2;
    public ArrayList<TLRPC.PhotoSize> photoThumbs;
    public ArrayList<TLRPC.PhotoSize> photoThumbs2;
    public VideoEditedInfo videoEditedInfo;
    public boolean shouldRemoveVideoEditedInfo;
    public boolean viewsReloaded;
    public boolean pollVisibleOnScreen;
    public long pollLastCheckTime;
    public int wantedBotKeyboardWidth;
    public boolean attachPathExists;
    public boolean mediaExists;
    public boolean resendAsIs;
    public String customReplyName;
    public boolean useCustomPhoto;
    public StringBuilder botButtonsLayout;
    public boolean isRestrictedMessage;
    public long loadedFileSize;

    public boolean isSpoilersRevealed;
    public byte[] sponsoredId;
    public int sponsoredChannelPost;
    public TLRPC.ChatInvite sponsoredChatInvite;
    public String sponsoredChatInviteHash;

    public String botStartParam;

    public boolean animateComments;

    public boolean loadingCancelled;

    public int stableId;

    public boolean wasUnread;

    public boolean hadAnimationNotReadyLoading;

    public boolean cancelEditing;

    public boolean scheduled;
    public boolean preview;

    public ArrayList<TLRPC.TL_pollAnswer> checkedVotes;

    public CharSequence editingMessage;
    public ArrayList<TLRPC.MessageEntity> editingMessageEntities;
    public boolean editingMessageSearchWebPage;

    public String previousMessage;
    public TLRPC.MessageMedia previousMedia;
    public ArrayList<TLRPC.MessageEntity> previousMessageEntities;
    public String previousAttachPath;

    public SvgHelper.SvgDrawable pathThumb;
    public BitmapDrawable strippedThumb;

    public int currentAccount;

    public TLRPC.TL_channelAdminLogEvent currentEvent;

    public boolean forceUpdate;

    public int lastLineWidth;
    public int textWidth;
    public int textHeight;
    public boolean hasRtl;
    public float textXOffset;
    public int linesCount;

    public SendAnimationData sendAnimationData;

    private int emojiOnlyCount;
    private boolean layoutCreated;
    private int generatedWithMinSize;
    private float generatedWithDensity;
    public boolean wasJustSent;

    public static Pattern urlPattern;
    public static Pattern instagramUrlPattern;
    public static Pattern videoTimeUrlPattern;

    public CharSequence vCardData;

    public ArrayList<String> highlightedWords;
    public String messageTrimmedToHighlight;
    public int parentWidth;

    public ImageLocation mediaThumb;
    public ImageLocation mediaSmallThumb;

    static final String[] excludeWords = new String[] {
            " vs. ",
            " vs ",
            " versus ",
            " ft. ",
            " ft ",
            " featuring ",
            " feat. ",
            " feat ",
            " presents ",
            " pres. ",
            " pres ",
            " and ",
            " & ",
            " . "
    };
    public Drawable customAvatarDrawable;

    public static boolean hasUnreadReactions(TLRPC.Message message) {
        if (message == null) {
            return false;
        }
        return hasUnreadReactions(message.reactions);
    }

    public static boolean hasUnreadReactions(TLRPC.TL_messageReactions reactions) {
        if (reactions == null) {
            return false;
        }
        for (int i = 0; i < reactions.recent_reactions.size(); i++) {
            if (reactions.recent_reactions.get(i).unread) {
                return true;
            }
        }
        return false;
    }

    public int getEmojiOnlyCount() {
        return emojiOnlyCount;
    }

    public boolean shouldDrawReactionsInLayout() {
        return getDialogId() < 0;
    }

    public TLRPC.TL_messagePeerReaction getRandomUnreadReaction() {
        if (messageOwner.reactions == null || messageOwner.reactions.recent_reactions == null || messageOwner.reactions.recent_reactions.isEmpty()) {
            return null;
        }
        return messageOwner.reactions.recent_reactions.get(0);
    }

    public void markReactionsAsRead() {
        if (messageOwner.reactions == null || messageOwner.reactions.recent_reactions == null) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < messageOwner.reactions.recent_reactions.size(); i++) {
            if (messageOwner.reactions.recent_reactions.get(i).unread) {
                messageOwner.reactions.recent_reactions.get(i).unread = false;
                changed = true;
            }
        }
        if (changed) {
            MessagesStorage.getInstance(currentAccount).markMessageReactionsAsRead(messageOwner.dialog_id, messageOwner.id, true);
        }
    }

    public static class SendAnimationData {
        public float x;
        public float y;
        public float width;
        public float height;
        public float currentScale;
        public float currentX;
        public float currentY;
        public float timeAlpha;
    }

    public static class VCardData {

        private String company;
        private ArrayList<String> emails = new ArrayList<>();
        private ArrayList<String> phones = new ArrayList<>();

        public static CharSequence parse(String data) {
            try {
                VCardData currentData = null;
                boolean finished = false;
                BufferedReader bufferedReader = new BufferedReader(new StringReader(data));

                String line;
                String originalLine;
                String pendingLine = null;
                while ((originalLine = line = bufferedReader.readLine()) != null) {
                    if (originalLine.startsWith("PHOTO")) {
                        continue;
                    } else {
                        if (originalLine.indexOf(':') >= 0) {
                            if (originalLine.startsWith("BEGIN:VCARD")) {
                                currentData = new VCardData();
                            } else if (originalLine.startsWith("END:VCARD")) {
                                if (currentData != null) {
                                    finished = true;
                                }
                            }
                        }
                    }
                    if (pendingLine != null) {
                        pendingLine += line;
                        line = pendingLine;
                        pendingLine = null;
                    }
                    if (line.contains("=QUOTED-PRINTABLE") && line.endsWith("=")) {
                        pendingLine = line.substring(0, line.length() - 1);
                        continue;
                    }
                    int idx = line.indexOf(":");
                    String[] args;
                    if (idx >= 0) {
                        args = new String[]{
                                line.substring(0, idx),
                                line.substring(idx + 1).trim()
                        };
                    } else {
                        args = new String[]{line.trim()};
                    }
                    if (args.length < 2 || currentData == null) {
                        continue;
                    }
                    if (args[0].startsWith("ORG")) {
                        String nameEncoding = null;
                        String nameCharset = null;
                        String[] params = args[0].split(";");
                        for (String param : params) {
                            String[] args2 = param.split("=");
                            if (args2.length != 2) {
                                continue;
                            }
                            if (args2[0].equals("CHARSET")) {
                                nameCharset = args2[1];
                            } else if (args2[0].equals("ENCODING")) {
                                nameEncoding = args2[1];
                            }
                        }
                        currentData.company = args[1];
                        if (nameEncoding != null && nameEncoding.equalsIgnoreCase("QUOTED-PRINTABLE")) {
                            byte[] bytes = AndroidUtilities.decodeQuotedPrintable(AndroidUtilities.getStringBytes(currentData.company));
                            if (bytes != null && bytes.length != 0) {
                                currentData.company = new String(bytes, nameCharset);
                            }
                        }
                        currentData.company = currentData.company.replace(';', ' ');
                    } else if (args[0].startsWith("TEL")) {
                        if (args[1].length() > 0) {
                            currentData.phones.add(args[1]);
                        }
                    } else if (args[0].startsWith("EMAIL")) {
                        String email = args[1];
                        if (email.length() > 0) {
                            currentData.emails.add(email);
                        }
                    }
                }
                try {
                    bufferedReader.close();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (finished) {
                    StringBuilder result = new StringBuilder();
                    for (int a = 0; a < currentData.phones.size(); a++) {
                        if (result.length() > 0) {
                            result.append('\n');
                        }
                        String phone = currentData.phones.get(a);
                        if (phone.contains("#") || phone.contains("*")) {
                            result.append(phone);
                        } else {
                            result.append(PhoneFormat.getInstance().format(phone));
                        }
                    }
                    for (int a = 0; a < currentData.emails.size(); a++) {
                        if (result.length() > 0) {
                            result.append('\n');
                        }
                        result.append(PhoneFormat.getInstance().format(currentData.emails.get(a)));
                    }
                    if (!TextUtils.isEmpty(currentData.company)) {
                        if (result.length() > 0) {
                            result.append('\n');
                        }
                        result.append(currentData.company);
                    }
                    return result;
                }
            } catch (Throwable ignore) {

            }
            return null;
        }
    }

    public static class TextLayoutBlock {
        public final static int FLAG_RTL = 1, FLAG_NOT_RTL = 2;

        public AtomicReference<Layout> spoilersPatchedTextLayout = new AtomicReference<>();
        public StaticLayout textLayout;
        public float textYOffset;
        public int charactersOffset;
        public int charactersEnd;
        public int height;
        public int heightByOffset;
        public byte directionFlags;
        public List<SpoilerEffect> spoilers = new ArrayList<>();

        public boolean isRtl() {
            return (directionFlags & FLAG_RTL) != 0 && (directionFlags & FLAG_NOT_RTL) == 0;
        }
    }

    public static final int POSITION_FLAG_LEFT = 1;
    public static final int POSITION_FLAG_RIGHT = 2;
    public static final int POSITION_FLAG_TOP = 4;
    public static final int POSITION_FLAG_BOTTOM = 8;

    public static class GroupedMessagePosition {
        public byte minX;
        public byte maxX;
        public byte minY;
        public byte maxY;
        public int pw;
        public float ph;
        public float aspectRatio;
        public boolean last;
        public int spanSize;
        public int leftSpanOffset;
        public boolean edge;
        public int flags;
        public float[] siblingHeights;

        public void set(int minX, int maxX, int minY, int maxY, int w, float h, int flags) {
            this.minX = (byte) minX;
            this.maxX = (byte) maxX;
            this.minY = (byte) minY;
            this.maxY = (byte) maxY;
            this.pw = w;
            this.spanSize = w;
            this.ph = h;
            this.flags = (byte) flags;
        }
    }

    public static class GroupedMessages {
        public long groupId;
        public boolean hasSibling;
        public boolean hasCaption;
        public ArrayList<MessageObject> messages = new ArrayList<>();
        public ArrayList<GroupedMessagePosition> posArray = new ArrayList<>();
        public HashMap<MessageObject, GroupedMessagePosition> positions = new HashMap<>();
        public boolean isDocuments;

        private int maxSizeWidth = 800;

        public final TransitionParams transitionParams = new TransitionParams();

        private static class MessageGroupedLayoutAttempt {

            public int[] lineCounts;
            public float[] heights;

            public MessageGroupedLayoutAttempt(int i1, int i2, float f1, float f2) {
                lineCounts = new int[] {i1, i2};
                heights = new float[] {f1, f2};
            }

            public MessageGroupedLayoutAttempt(int i1, int i2, int i3, float f1, float f2, float f3) {
                lineCounts = new int[] {i1, i2, i3};
                heights = new float[] {f1, f2, f3};
            }

            public MessageGroupedLayoutAttempt(int i1, int i2, int i3, int i4, float f1, float f2, float f3, float f4) {
                lineCounts = new int[] {i1, i2, i3, i4};
                heights = new float[] {f1, f2, f3, f4};
            }
        }

        private float multiHeight(float[] array, int start, int end) {
            float sum = 0;
            for (int a = start; a < end; a++) {
                sum += array[a];
            }
            return maxSizeWidth / sum;
        }

        public void calculate() {
            posArray.clear();
            positions.clear();

            maxSizeWidth = 800;
            int firstSpanAdditionalSize = 200;

            int count = messages.size();
            if (count <= 1) {
                return;
            }

            float maxSizeHeight = 814.0f;
            StringBuilder proportions = new StringBuilder();
            float averageAspectRatio = 1.0f;
            boolean isOut = false;
            int maxX = 0;
            boolean forceCalc = false;
            boolean needShare = false;
            boolean isMusic = false;
            hasSibling = false;

            hasCaption = false;

            for (int a = 0; a < count; a++) {
                MessageObject messageObject = messages.get(a);
                if (a == 0) {
                    isOut = messageObject.isOutOwner();
                    needShare = !isOut && (
                            messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.saved_from_peer != null ||
                            messageObject.messageOwner.from_id instanceof TLRPC.TL_peerUser && (messageObject.messageOwner.peer_id.channel_id != 0 || messageObject.messageOwner.peer_id.chat_id != 0 ||
                            messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice)
                    );
                    if (messageObject.isMusic() || messageObject.isDocument()) {
                        isDocuments = true;
                    }
                }
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize());
                GroupedMessagePosition position = new GroupedMessagePosition();
                position.last = a == count - 1;
                position.aspectRatio = photoSize == null ? 1.0f : photoSize.w / (float) photoSize.h;

                if (position.aspectRatio > 1.2f) {
                    proportions.append("w");
                } else if (position.aspectRatio < 0.8f) {
                    proportions.append("n");
                } else {
                    proportions.append("q");
                }

                averageAspectRatio += position.aspectRatio;

                if (position.aspectRatio > 2.0f) {
                    forceCalc = true;
                }

                positions.put(messageObject, position);
                posArray.add(position);

                if (messageObject.caption != null) {
                    hasCaption = true;
                }
            }
            if (isDocuments) {
                for (int a = 0; a < count; a++) {
                    GroupedMessagePosition pos = posArray.get(a);
                    pos.flags |= POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT;
                    if (a == 0) {
                        pos.flags |= POSITION_FLAG_TOP;
                    } else if (a == count - 1) {
                        pos.flags |= POSITION_FLAG_BOTTOM;
                        pos.last = true;
                    }
                    pos.edge = true;
                    pos.aspectRatio = 1.0f;
                    pos.minX = 0;
                    pos.maxX = 0;
                    pos.minY = (byte) a;
                    pos.maxY = (byte) a;
                    pos.spanSize = 1000;
                    pos.pw = maxSizeWidth;
                    pos.ph = 100;
                }
                return;
            }

            if (needShare) {
                maxSizeWidth -= 50;
                firstSpanAdditionalSize += 50;
            }

            int minHeight = AndroidUtilities.dp(120);
            int minWidth = (int) (AndroidUtilities.dp(120) / (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / (float) maxSizeWidth));
            int paddingsWidth = (int) (AndroidUtilities.dp(40) / (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / (float) maxSizeWidth));

            float maxAspectRatio = maxSizeWidth / maxSizeHeight;
            averageAspectRatio = averageAspectRatio / count;

            float minH = AndroidUtilities.dp(100) / maxSizeHeight;

            if (!forceCalc && (count == 2 || count == 3 || count == 4)) {
                if (count == 2) {
                    GroupedMessagePosition position1 = posArray.get(0);
                    GroupedMessagePosition position2 = posArray.get(1);
                    String pString = proportions.toString();
                    if (pString.equals("ww") && averageAspectRatio > 1.4 * maxAspectRatio && position1.aspectRatio - position2.aspectRatio < 0.2) {
                        float height = Math.round(Math.min(maxSizeWidth / position1.aspectRatio, Math.min(maxSizeWidth / position2.aspectRatio, maxSizeHeight / 2.0f))) / maxSizeHeight;
                        position1.set(0, 0, 0, 0, maxSizeWidth, height, POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);
                        position2.set(0, 0, 1, 1, maxSizeWidth, height, POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                    } else if (pString.equals("ww") || pString.equals("qq")) {
                        int width = maxSizeWidth / 2;
                        float height = Math.round(Math.min(width / position1.aspectRatio, Math.min(width / position2.aspectRatio, maxSizeHeight))) / maxSizeHeight;
                        position1.set(0, 0, 0, 0, width, height, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);
                        position2.set(1, 1, 0, 0, width, height, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);
                        maxX = 1;
                    } else {
                        int secondWidth = (int) Math.max(0.4f * maxSizeWidth, Math.round((maxSizeWidth / position1.aspectRatio / (1.0f / position1.aspectRatio + 1.0f / position2.aspectRatio))));
                        int firstWidth = maxSizeWidth - secondWidth;
                        if (firstWidth < minWidth) {
                            int diff = minWidth - firstWidth;
                            firstWidth = minWidth;
                            secondWidth -= diff;
                        }

                        float height = Math.min(maxSizeHeight, Math.round(Math.min(firstWidth / position1.aspectRatio, secondWidth / position2.aspectRatio))) / maxSizeHeight;
                        position1.set(0, 0, 0, 0, firstWidth, height, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);
                        position2.set(1, 1, 0, 0, secondWidth, height, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);
                        maxX = 1;
                    }
                } else if (count == 3) {
                    GroupedMessagePosition position1 = posArray.get(0);
                    GroupedMessagePosition position2 = posArray.get(1);
                    GroupedMessagePosition position3 = posArray.get(2);
                    if (proportions.charAt(0) == 'n') {
                        float thirdHeight = Math.min(maxSizeHeight * 0.5f, Math.round(position2.aspectRatio * maxSizeWidth / (position3.aspectRatio + position2.aspectRatio)));
                        float secondHeight = maxSizeHeight - thirdHeight;
                        int rightWidth = (int) Math.max(minWidth, Math.min(maxSizeWidth * 0.5f, Math.round(Math.min(thirdHeight * position3.aspectRatio, secondHeight * position2.aspectRatio))));

                        int leftWidth = Math.round(Math.min(maxSizeHeight * position1.aspectRatio + paddingsWidth, maxSizeWidth - rightWidth));
                        position1.set(0, 0, 0, 1, leftWidth, 1.0f, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);

                        position2.set(1, 1, 0, 0, rightWidth, secondHeight / maxSizeHeight, POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);

                        position3.set(0, 1, 1, 1, rightWidth, thirdHeight / maxSizeHeight, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                        position3.spanSize = maxSizeWidth;

                        position1.siblingHeights = new float[] {thirdHeight / maxSizeHeight, secondHeight / maxSizeHeight};

                        if (isOut) {
                            position1.spanSize = maxSizeWidth - rightWidth;
                        } else {
                            position2.spanSize = maxSizeWidth - leftWidth;
                            position3.leftSpanOffset = leftWidth;
                        }
                        hasSibling = true;
                        maxX = 1;
                    } else {
                        float firstHeight = Math.round(Math.min(maxSizeWidth / position1.aspectRatio, (maxSizeHeight) * 0.66f)) / maxSizeHeight;
                        position1.set(0, 1, 0, 0, maxSizeWidth, firstHeight, POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);

                        int width = maxSizeWidth / 2;
                        float secondHeight = Math.min(maxSizeHeight - firstHeight, Math.round(Math.min(width / position2.aspectRatio, width / position3.aspectRatio))) / maxSizeHeight;
                        if (secondHeight < minH) {
                            secondHeight = minH;
                        }
                        position2.set(0, 0, 1, 1, width, secondHeight, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM);
                        position3.set(1, 1, 1, 1, width, secondHeight, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                        maxX = 1;
                    }
                } else {
                    GroupedMessagePosition position1 = posArray.get(0);
                    GroupedMessagePosition position2 = posArray.get(1);
                    GroupedMessagePosition position3 = posArray.get(2);
                    GroupedMessagePosition position4 = posArray.get(3);
                    if (proportions.charAt(0) == 'w') {
                        float h0 = Math.round(Math.min(maxSizeWidth / position1.aspectRatio, maxSizeHeight * 0.66f)) / maxSizeHeight;
                        position1.set(0, 2, 0, 0, maxSizeWidth, h0, POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);

                        float h = Math.round(maxSizeWidth / (position2.aspectRatio + position3.aspectRatio + position4.aspectRatio));
                        int w0 = (int) Math.max(minWidth, Math.min(maxSizeWidth * 0.4f, h * position2.aspectRatio));
                        int w2 = (int) Math.max(Math.max(minWidth, maxSizeWidth * 0.33f), h * position4.aspectRatio);
                        int w1 = maxSizeWidth - w0 - w2;
                        if (w1 < AndroidUtilities.dp(58)) {
                            int diff = AndroidUtilities.dp(58) - w1;
                            w1 = AndroidUtilities.dp(58);
                            w0 -= diff / 2;
                            w2 -= (diff - diff / 2);
                        }
                        h = Math.min(maxSizeHeight - h0, h);
                        h /= maxSizeHeight;
                        if (h < minH) {
                            h = minH;
                        }
                        position2.set(0, 0, 1, 1, w0, h, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM);
                        position3.set(1, 1, 1, 1, w1, h, POSITION_FLAG_BOTTOM);
                        position4.set(2, 2, 1, 1, w2, h, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                        maxX = 2;
                    } else {
                        int w = Math.max(minWidth, Math.round(maxSizeHeight / (1.0f / position2.aspectRatio + 1.0f / position3.aspectRatio + 1.0f / position4.aspectRatio)));
                        float h0 = Math.min(0.33f, Math.max(minHeight, w / position2.aspectRatio) / maxSizeHeight);
                        float h1 = Math.min(0.33f, Math.max(minHeight, w / position3.aspectRatio) / maxSizeHeight);
                        float h2 = 1.0f - h0 - h1;
                        int w0 = Math.round(Math.min(maxSizeHeight * position1.aspectRatio + paddingsWidth, maxSizeWidth - w));

                        position1.set(0, 0, 0, 2, w0, h0 + h1 + h2, POSITION_FLAG_LEFT | POSITION_FLAG_TOP | POSITION_FLAG_BOTTOM);

                        position2.set(1, 1, 0, 0, w, h0, POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);

                        position3.set(0, 1, 1, 1, w, h1, POSITION_FLAG_RIGHT);
                        position3.spanSize = maxSizeWidth;

                        position4.set(0, 1, 2, 2, w, h2, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                        position4.spanSize = maxSizeWidth;

                        if (isOut) {
                            position1.spanSize = maxSizeWidth - w;
                        } else {
                            position2.spanSize = maxSizeWidth - w0;
                            position3.leftSpanOffset = w0;
                            position4.leftSpanOffset = w0;
                        }
                        position1.siblingHeights = new float[] {h0, h1, h2};
                        hasSibling = true;
                        maxX = 1;
                    }
                }
            } else {
                float[] croppedRatios = new float[posArray.size()];
                for (int a = 0; a < count; a++) {
                    if (averageAspectRatio > 1.1f) {
                        croppedRatios[a] = Math.max(1.0f, posArray.get(a).aspectRatio);
                    } else {
                        croppedRatios[a] = Math.min(1.0f, posArray.get(a).aspectRatio);
                    }
                    croppedRatios[a] = Math.max(0.66667f, Math.min(1.7f, croppedRatios[a]));
                }

                int firstLine;
                int secondLine;
                int thirdLine;
                int fourthLine;
                ArrayList<MessageGroupedLayoutAttempt> attempts = new ArrayList<>();
                for (firstLine = 1; firstLine < croppedRatios.length; firstLine++) {
                    secondLine = croppedRatios.length - firstLine;
                    if (firstLine > 3 || secondLine > 3) {
                        continue;
                    }
                    attempts.add(new MessageGroupedLayoutAttempt(firstLine, secondLine, multiHeight(croppedRatios, 0, firstLine), multiHeight(croppedRatios, firstLine, croppedRatios.length)));
                }

                for (firstLine = 1; firstLine < croppedRatios.length - 1; firstLine++) {
                    for (secondLine = 1; secondLine < croppedRatios.length - firstLine; secondLine++) {
                        thirdLine = croppedRatios.length - firstLine - secondLine;
                        if (firstLine > 3 || secondLine > (averageAspectRatio < 0.85f ? 4 : 3) || thirdLine > 3) {
                            continue;
                        }
                        attempts.add(new MessageGroupedLayoutAttempt(firstLine, secondLine, thirdLine, multiHeight(croppedRatios, 0, firstLine), multiHeight(croppedRatios, firstLine, firstLine + secondLine), multiHeight(croppedRatios, firstLine + secondLine, croppedRatios.length)));
                    }
                }

                for (firstLine = 1; firstLine < croppedRatios.length - 2; firstLine++) {
                    for (secondLine = 1; secondLine < croppedRatios.length - firstLine; secondLine++) {
                        for (thirdLine = 1; thirdLine < croppedRatios.length - firstLine - secondLine; thirdLine++) {
                            fourthLine = croppedRatios.length - firstLine - secondLine - thirdLine;
                            if (firstLine > 3 || secondLine > 3 || thirdLine > 3 || fourthLine > 3) {
                                continue;
                            }
                            attempts.add(new MessageGroupedLayoutAttempt(firstLine, secondLine, thirdLine, fourthLine, multiHeight(croppedRatios, 0, firstLine), multiHeight(croppedRatios, firstLine, firstLine + secondLine), multiHeight(croppedRatios, firstLine + secondLine, firstLine + secondLine + thirdLine), multiHeight(croppedRatios, firstLine + secondLine + thirdLine, croppedRatios.length)));
                        }
                    }
                }

                MessageGroupedLayoutAttempt optimal = null;
                float optimalDiff = 0.0f;
                float maxHeight = maxSizeWidth / 3 * 4;
                for (int a = 0; a < attempts.size(); a++) {
                    MessageGroupedLayoutAttempt attempt = attempts.get(a);
                    float height = 0;
                    float minLineHeight = Float.MAX_VALUE;
                    for (int b = 0; b < attempt.heights.length; b++){
                        height += attempt.heights[b];
                        if (attempt.heights[b] < minLineHeight) {
                            minLineHeight = attempt.heights[b];
                        }
                    }

                    float diff = Math.abs(height - maxHeight);
                    if (attempt.lineCounts.length > 1) {
                        if (attempt.lineCounts[0] > attempt.lineCounts[1] || (attempt.lineCounts.length > 2 && attempt.lineCounts[1] > attempt.lineCounts[2]) || (attempt.lineCounts.length > 3 && attempt.lineCounts[2] > attempt.lineCounts[3])) {
                            diff *= 1.2f;
                        }
                    }

                    if (minLineHeight < minWidth) {
                        diff *= 1.5f;
                    }

                    if (optimal == null || diff < optimalDiff) {
                        optimal = attempt;
                        optimalDiff = diff;
                    }
                }
                if (optimal == null) {
                    return;
                }

                int index = 0;
                float y = 0.0f;

                for (int i = 0; i < optimal.lineCounts.length; i++) {
                    int c = optimal.lineCounts[i];
                    float lineHeight = optimal.heights[i];
                    int spanLeft = maxSizeWidth;
                    GroupedMessagePosition posToFix = null;
                    maxX = Math.max(maxX, c - 1);
                    for (int k = 0; k < c; k++) {
                        float ratio = croppedRatios[index];
                        int width = (int) (ratio * lineHeight);
                        spanLeft -= width;
                        GroupedMessagePosition pos = posArray.get(index);
                        int flags = 0;
                        if (i == 0) {
                            flags |= POSITION_FLAG_TOP;
                        }
                        if (i == optimal.lineCounts.length - 1) {
                            flags |= POSITION_FLAG_BOTTOM;
                        }
                        if (k == 0) {
                            flags |= POSITION_FLAG_LEFT;
                            if (isOut) {
                                posToFix = pos;
                            }
                        }
                        if (k == c - 1) {
                            flags |= POSITION_FLAG_RIGHT;
                            if (!isOut) {
                                posToFix = pos;
                            }
                        }
                        pos.set(k, k, i, i, width, Math.max(minH, lineHeight / maxSizeHeight), flags);
                        index++;
                    }
                    posToFix.pw += spanLeft;
                    posToFix.spanSize += spanLeft;
                    y += lineHeight;
                }
            }
            int avatarOffset = 108;
            for (int a = 0; a < count; a++) {
                GroupedMessagePosition pos = posArray.get(a);
                if (isOut) {
                    if (pos.minX == 0) {
                        pos.spanSize += firstSpanAdditionalSize;
                    }
                    if ((pos.flags & POSITION_FLAG_RIGHT) != 0) {
                        pos.edge = true;
                    }
                } else {
                    if (pos.maxX == maxX || (pos.flags & POSITION_FLAG_RIGHT) != 0) {
                        pos.spanSize += firstSpanAdditionalSize;
                    }
                    if ((pos.flags & POSITION_FLAG_LEFT) != 0) {
                        pos.edge = true;
                    }
                }
                MessageObject messageObject = messages.get(a);
                if (!isOut && messageObject.needDrawAvatarInternal()) {
                    if (pos.edge) {
                        if (pos.spanSize != 1000) {
                            pos.spanSize += avatarOffset;
                        }
                        pos.pw += avatarOffset;
                    } else if ((pos.flags & POSITION_FLAG_RIGHT) != 0) {
                        if (pos.spanSize != 1000) {
                            pos.spanSize -= avatarOffset;
                        } else if (pos.leftSpanOffset != 0) {
                            pos.leftSpanOffset += avatarOffset;
                        }
                    }
                }
            }
        }

        public MessageObject findPrimaryMessageObject() {
            return findMessageWithFlags(MessageObject.POSITION_FLAG_TOP | MessageObject.POSITION_FLAG_LEFT);
        }

        public MessageObject findMessageWithFlags(int flags) {
            if (!messages.isEmpty() && positions.isEmpty()) {
                calculate();
            }
            for (int i = 0; i < messages.size(); i++) {
                MessageObject object = messages.get(i);
                MessageObject.GroupedMessagePosition position = positions.get(object);
                if (position != null  && (position.flags & (flags)) == flags) {
                    return object;
                }
            }
            return null;
        }

        public static class TransitionParams {
            public int left;
            public int top;
            public int right;
            public int bottom;

            public float offsetLeft;
            public float offsetTop;
            public float offsetRight;
            public float offsetBottom;

            public boolean drawBackgroundForDeletedItems;
            public boolean backgroundChangeBounds;

            public boolean pinnedTop;
            public boolean pinnedBotton;

            public ChatMessageCell cell;
            public float captionEnterProgress = 1f;
            public boolean drawCaptionLayout;
            public boolean isNewGroup;

            public void reset() {
                captionEnterProgress = 1f;
                offsetBottom = 0;
                offsetTop = 0;
                offsetRight = 0;
                offsetLeft = 0;
                backgroundChangeBounds = false;
            }
        }
    }

    private static final int LINES_PER_BLOCK = 10;

    public ArrayList<TextLayoutBlock> textLayoutBlocks;

    public MessageObject(int accountNum, TLRPC.Message message, String formattedMessage, String name, String userName, boolean localMessage, boolean isChannel, boolean supergroup, boolean edit) {
        localType = localMessage ? 2 : 1;
        currentAccount = accountNum;
        localName = name;
        localUserName = userName;
        messageText = formattedMessage;
        messageOwner = message;
        localChannel = isChannel;
        localSupergroup = supergroup;
        localEdit = edit;
    }

    public MessageObject(int accountNum, TLRPC.Message message, AbstractMap<Long, TLRPC.User> users, boolean generateLayout, boolean checkMediaExists) {
        this(accountNum, message, users, null, generateLayout, checkMediaExists);
    }

    public MessageObject(int accountNum, TLRPC.Message message, LongSparseArray<TLRPC.User> users, boolean generateLayout, boolean checkMediaExists) {
        this(accountNum, message, users, null, generateLayout, checkMediaExists);
    }

    public MessageObject(int accountNum, TLRPC.Message message, boolean generateLayout, boolean checkMediaExists) {
        this(accountNum, message, null, null, null, null, null, generateLayout, checkMediaExists, 0);
    }

    public MessageObject(int accountNum, TLRPC.Message message, MessageObject replyToMessage, boolean generateLayout, boolean checkMediaExists) {
        this(accountNum, message, replyToMessage, null, null, null, null, generateLayout, checkMediaExists, 0);
    }

    public MessageObject(int accountNum, TLRPC.Message message, AbstractMap<Long, TLRPC.User> users, AbstractMap<Long, TLRPC.Chat> chats, boolean generateLayout, boolean checkMediaExists) {
        this(accountNum, message, users, chats, generateLayout, checkMediaExists, 0);
    }

    public MessageObject(int accountNum, TLRPC.Message message, LongSparseArray<TLRPC.User> users, LongSparseArray<TLRPC.Chat> chats, boolean generateLayout, boolean checkMediaExists) {
        this(accountNum, message, null, null, null, users, chats, generateLayout, checkMediaExists, 0);
    }

    public MessageObject(int accountNum, TLRPC.Message message, AbstractMap<Long, TLRPC.User> users, AbstractMap<Long, TLRPC.Chat> chats, boolean generateLayout, boolean checkMediaExists, long eid) {
        this(accountNum, message, null, users, chats, null, null, generateLayout, checkMediaExists, eid);
    }

    public MessageObject(int accountNum, TLRPC.Message message, MessageObject replyToMessage, AbstractMap<Long, TLRPC.User> users, AbstractMap<Long, TLRPC.Chat> chats, LongSparseArray<TLRPC.User> sUsers, LongSparseArray<TLRPC.Chat> sChats, boolean generateLayout, boolean checkMediaExists, long eid) {
        Theme.createCommonMessageResources();

        currentAccount = accountNum;
        messageOwner = message;
        replyMessageObject = replyToMessage;
        eventId = eid;
        wasUnread = !messageOwner.out && messageOwner.unread;

        if (message.replyMessage != null) {
            replyMessageObject = new MessageObject(currentAccount, message.replyMessage, null, users, chats, sUsers, sChats, false, checkMediaExists, eid);
        }

        TLRPC.User fromUser = null;
        if (message.from_id instanceof TLRPC.TL_peerUser) {
            fromUser = getUser(users, sUsers, message.from_id.user_id);
        }

        updateMessageText(users, chats, sUsers, sChats);
        setType();
        measureInlineBotButtons();

        Calendar rightNow = new GregorianCalendar();
        rightNow.setTimeInMillis((long) (messageOwner.date) * 1000);
        int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
        int dateYear = rightNow.get(Calendar.YEAR);
        int dateMonth = rightNow.get(Calendar.MONTH);
        dateKey = String.format("%d_%02d_%02d", dateYear, dateMonth, dateDay);
        monthKey = String.format("%d_%02d", dateYear, dateMonth);

        createMessageSendInfo();
        generateCaption();
        if (generateLayout) {
            TextPaint paint;
            if (messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                paint = Theme.chat_msgGameTextPaint;
            } else {
                paint = Theme.chat_msgTextPaint;
            }
            int[] emojiOnly = allowsBigEmoji() ? new int[1] : null;
            messageText = Emoji.replaceEmoji(messageText, paint.getFontMetricsInt(), AndroidUtilities.dp(20), false, emojiOnly);
            checkEmojiOnly(emojiOnly);
            emojiAnimatedSticker = null;
            if (emojiOnlyCount == 1 && !(message.media instanceof TLRPC.TL_messageMediaWebPage) && !(message.media instanceof TLRPC.TL_messageMediaInvoice) && message.entities.isEmpty()) {
                CharSequence emoji = messageText;
                int index;
                if ((index = TextUtils.indexOf(emoji, "\uD83C\uDFFB")) >= 0) {
                    emojiAnimatedStickerColor = "_c1";
                    emoji = emoji.subSequence(0, index);
                } else if ((index = TextUtils.indexOf(emoji, "\uD83C\uDFFC")) >= 0) {
                    emojiAnimatedStickerColor = "_c2";
                    emoji = emoji.subSequence(0, index);
                } else if ((index = TextUtils.indexOf(emoji, "\uD83C\uDFFD")) >= 0) {
                    emojiAnimatedStickerColor = "_c3";
                    emoji = emoji.subSequence(0, index);
                } else if ((index = TextUtils.indexOf(emoji, "\uD83C\uDFFE")) >= 0) {
                    emojiAnimatedStickerColor = "_c4";
                    emoji = emoji.subSequence(0, index);
                } else if ((index = TextUtils.indexOf(emoji, "\uD83C\uDFFF")) >= 0) {
                    emojiAnimatedStickerColor = "_c5";
                    emoji = emoji.subSequence(0, index);
                } else {
                    emojiAnimatedStickerColor = "";
                }
                if (!TextUtils.isEmpty(emojiAnimatedStickerColor) && index + 2 < messageText.length()) {
                    emoji = emoji.toString() + messageText.subSequence(index + 2, messageText.length()).toString();
                }
                if (TextUtils.isEmpty(emojiAnimatedStickerColor) || EmojiData.emojiColoredMap.contains(emoji.toString())) {
                    emojiAnimatedSticker = MediaDataController.getInstance(currentAccount).getEmojiAnimatedSticker(emoji);
                }
            }
            if (emojiAnimatedSticker == null) {
                generateLayout(fromUser);
            } else {
                type = 1000;
                if (isSticker()) {
                    type = TYPE_STICKER;
                } else if (isAnimatedSticker()) {
                    type = TYPE_ANIMATED_STICKER;
                }
            }
            createPathThumb();
        }
        layoutCreated = generateLayout;
        generateThumbs(false);
        if (checkMediaExists) {
            checkMediaExistance();
        }
    }

    private void createPathThumb() {
        TLRPC.Document document = getDocument();
        if (document == null) {
            return;
        }
        pathThumb = DocumentObject.getSvgThumb(document, Theme.key_chat_serviceBackground, 1.0f);
    }

    public void createStrippedThumb() {
        if (photoThumbs == null || SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_HIGH) {
            return;
        }
        try {
            for (int a = 0, N = photoThumbs.size(); a < N; a++) {
                TLRPC.PhotoSize photoSize = photoThumbs.get(a);
                if (photoSize instanceof TLRPC.TL_photoStrippedSize) {
                    strippedThumb = new BitmapDrawable(ImageLoader.getStrippedPhotoBitmap(photoSize.bytes, "b"));
                    break;
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void createDateArray(int accountNum, TLRPC.TL_channelAdminLogEvent event, ArrayList<MessageObject> messageObjects, HashMap<String, ArrayList<MessageObject>> messagesByDays, boolean addToEnd) {
        ArrayList<MessageObject> dayArray = messagesByDays.get(dateKey);
        if (dayArray == null) {
            dayArray = new ArrayList<>();
            messagesByDays.put(dateKey, dayArray);
            TLRPC.TL_message dateMsg = new TLRPC.TL_message();
            dateMsg.message = LocaleController.formatDateChat(event.date);
            dateMsg.id = 0;
            dateMsg.date = event.date;
            MessageObject dateObj = new MessageObject(accountNum, dateMsg, false, false);
            dateObj.type = 10;
            dateObj.contentType = 1;
            dateObj.isDateObject = true;
            if (addToEnd) {
                messageObjects.add(0, dateObj);
            } else {
                messageObjects.add(dateObj);
            }
        }
    }

    public void checkForScam() {

    }

    private void checkEmojiOnly(int[] emojiOnly) {
        if (emojiOnly != null && emojiOnly[0] >= 1 && emojiOnly[0] <= 3) {
            TextPaint emojiPaint;
            int size;
            switch (emojiOnly[0]) {
                case 1:
                    emojiPaint = Theme.chat_msgTextPaintOneEmoji;
                    size = AndroidUtilities.dp(32);
                    emojiOnlyCount = 1;
                    break;
                case 2:
                    emojiPaint = Theme.chat_msgTextPaintTwoEmoji;
                    size = AndroidUtilities.dp(28);
                    emojiOnlyCount = 2;
                    break;
                case 3:
                default:
                    emojiPaint = Theme.chat_msgTextPaintThreeEmoji;
                    size = AndroidUtilities.dp(24);
                    emojiOnlyCount = 3;
                    break;
            }
            Emoji.EmojiSpan[] spans = ((Spannable) messageText).getSpans(0, messageText.length(), Emoji.EmojiSpan.class);
            if (spans != null && spans.length > 0) {
                for (int a = 0; a < spans.length; a++) {
                    spans[a].replaceFontMetrics(emojiPaint.getFontMetricsInt(), size);
                }
            }
        }
    }

    public MessageObject(int accountNum, TLRPC.TL_channelAdminLogEvent event, ArrayList<MessageObject> messageObjects, HashMap<String, ArrayList<MessageObject>> messagesByDays, TLRPC.Chat chat, int[] mid, boolean addToEnd) {
        currentEvent = event;
        currentAccount = accountNum;

        TLRPC.User fromUser = null;
        if (event.user_id > 0) {
            fromUser = MessagesController.getInstance(currentAccount).getUser(event.user_id);
        }

        Calendar rightNow = new GregorianCalendar();
        rightNow.setTimeInMillis((long) (event.date) * 1000);
        int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
        int dateYear = rightNow.get(Calendar.YEAR);
        int dateMonth = rightNow.get(Calendar.MONTH);
        dateKey = String.format("%d_%02d_%02d", dateYear, dateMonth, dateDay);
        monthKey = String.format("%d_%02d", dateYear, dateMonth);

        TLRPC.Peer peer_id = new TLRPC.TL_peerChannel();
        peer_id.channel_id = chat.id;

        TLRPC.Message message = null;
        if (event.action instanceof TLRPC.TL_channelAdminLogEventActionChangeTitle) {
            String title = ((TLRPC.TL_channelAdminLogEventActionChangeTitle) event.action).new_value;
            if (chat.megagroup) {
                messageText = replaceWithLink(LocaleController.formatString("EventLogEditedGroupTitle", R.string.EventLogEditedGroupTitle, title), "un1", fromUser);
            } else {
                messageText = replaceWithLink(LocaleController.formatString("EventLogEditedChannelTitle", R.string.EventLogEditedChannelTitle, title), "un1", fromUser);
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionChangePhoto) {
            TLRPC.TL_channelAdminLogEventActionChangePhoto action = (TLRPC.TL_channelAdminLogEventActionChangePhoto) event.action;
            messageOwner = new TLRPC.TL_messageService();
            if (action.new_photo instanceof TLRPC.TL_photoEmpty) {
                messageOwner.action = new TLRPC.TL_messageActionChatDeletePhoto();
                if (chat.megagroup) {
                    messageText = replaceWithLink(LocaleController.getString("EventLogRemovedWGroupPhoto", R.string.EventLogRemovedWGroupPhoto), "un1", fromUser);
                } else {
                    messageText = replaceWithLink(LocaleController.getString("EventLogRemovedChannelPhoto", R.string.EventLogRemovedChannelPhoto), "un1", fromUser);
                }
            } else {
                messageOwner.action = new TLRPC.TL_messageActionChatEditPhoto();
                messageOwner.action.photo = action.new_photo;

                if (chat.megagroup) {
                    if (isVideoAvatar()) {
                        messageText = replaceWithLink(LocaleController.getString("EventLogEditedGroupVideo", R.string.EventLogEditedGroupVideo), "un1", fromUser);
                    } else {
                        messageText = replaceWithLink(LocaleController.getString("EventLogEditedGroupPhoto", R.string.EventLogEditedGroupPhoto), "un1", fromUser);
                    }
                } else {
                    if (isVideoAvatar()) {
                        messageText = replaceWithLink(LocaleController.getString("EventLogEditedChannelVideo", R.string.EventLogEditedChannelVideo), "un1", fromUser);
                    } else {
                        messageText = replaceWithLink(LocaleController.getString("EventLogEditedChannelPhoto", R.string.EventLogEditedChannelPhoto), "un1", fromUser);
                    }
                }
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionParticipantJoin) {
            if (chat.megagroup) {
                messageText = replaceWithLink(LocaleController.getString("EventLogGroupJoined", R.string.EventLogGroupJoined), "un1", fromUser);
            } else {
                messageText = replaceWithLink(LocaleController.getString("EventLogChannelJoined", R.string.EventLogChannelJoined), "un1", fromUser);
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionParticipantLeave) {
            messageOwner = new TLRPC.TL_messageService();
            messageOwner.action = new TLRPC.TL_messageActionChatDeleteUser();
            messageOwner.action.user_id = event.user_id;
            if (chat.megagroup) {
                messageText = replaceWithLink(LocaleController.getString("EventLogLeftGroup", R.string.EventLogLeftGroup), "un1", fromUser);
            } else {
                messageText = replaceWithLink(LocaleController.getString("EventLogLeftChannel", R.string.EventLogLeftChannel), "un1", fromUser);
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionParticipantInvite) {
            TLRPC.TL_channelAdminLogEventActionParticipantInvite action = (TLRPC.TL_channelAdminLogEventActionParticipantInvite) event.action;
            messageOwner = new TLRPC.TL_messageService();
            messageOwner.action = new TLRPC.TL_messageActionChatAddUser();
            long peerId = getPeerId(action.participant.peer);
            TLObject whoUser;
            if (peerId > 0) {
                whoUser = MessagesController.getInstance(currentAccount).getUser(peerId);
            } else {
                whoUser = MessagesController.getInstance(currentAccount).getChat(-peerId);
            }
            if (messageOwner.from_id instanceof TLRPC.TL_peerUser && peerId == messageOwner.from_id.user_id) {
                if (chat.megagroup) {
                    messageText = replaceWithLink(LocaleController.getString("EventLogGroupJoined", R.string.EventLogGroupJoined), "un1", fromUser);
                } else {
                    messageText = replaceWithLink(LocaleController.getString("EventLogChannelJoined", R.string.EventLogChannelJoined), "un1", fromUser);
                }
            } else {
                messageText = replaceWithLink(LocaleController.getString("EventLogAdded", R.string.EventLogAdded), "un2", whoUser);
                messageText = replaceWithLink(messageText, "un1", fromUser);
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionParticipantToggleAdmin ||
                event.action instanceof TLRPC.TL_channelAdminLogEventActionParticipantToggleBan && ((TLRPC.TL_channelAdminLogEventActionParticipantToggleBan) event.action).prev_participant instanceof TLRPC.TL_channelParticipantAdmin && ((TLRPC.TL_channelAdminLogEventActionParticipantToggleBan) event.action).new_participant instanceof TLRPC.TL_channelParticipant) {
            TLRPC.ChannelParticipant prev_participant;
            TLRPC.ChannelParticipant new_participant;
            if (event.action instanceof TLRPC.TL_channelAdminLogEventActionParticipantToggleAdmin) {
                TLRPC.TL_channelAdminLogEventActionParticipantToggleAdmin action = (TLRPC.TL_channelAdminLogEventActionParticipantToggleAdmin) event.action;
                prev_participant = action.prev_participant;
                new_participant = action.new_participant;
            } else {
                TLRPC.TL_channelAdminLogEventActionParticipantToggleBan action = (TLRPC.TL_channelAdminLogEventActionParticipantToggleBan) event.action;
                prev_participant = action.prev_participant;
                new_participant = action.new_participant;
            }
            messageOwner = new TLRPC.TL_message();
            long peerId = MessageObject.getPeerId(prev_participant.peer);
            TLObject whoUser;
            if (peerId > 0) {
                whoUser = MessagesController.getInstance(currentAccount).getUser(peerId);
            } else {
                whoUser = MessagesController.getInstance(currentAccount).getUser(-peerId);
            }
            StringBuilder rights;
            if (!(prev_participant instanceof TLRPC.TL_channelParticipantCreator) && new_participant instanceof TLRPC.TL_channelParticipantCreator) {
                String str = LocaleController.getString("EventLogChangedOwnership", R.string.EventLogChangedOwnership);
                int offset = str.indexOf("%1$s");
                rights = new StringBuilder(String.format(str, getUserName(whoUser, messageOwner.entities, offset)));
            } else {
                TLRPC.TL_chatAdminRights o = prev_participant.admin_rights;
                TLRPC.TL_chatAdminRights n = new_participant.admin_rights;
                if (o == null) {
                    o = new TLRPC.TL_chatAdminRights();
                }
                if (n == null) {
                    n = new TLRPC.TL_chatAdminRights();
                }
                String str;
                if (n.other) {
                    str = LocaleController.getString("EventLogPromotedNoRights", R.string.EventLogPromotedNoRights);
                } else {
                    str = LocaleController.getString("EventLogPromoted", R.string.EventLogPromoted);
                }
                int offset = str.indexOf("%1$s");
                rights = new StringBuilder(String.format(str, getUserName(whoUser, messageOwner.entities, offset)));
                rights.append("\n");
                if (!TextUtils.equals(prev_participant.rank, new_participant.rank)) {
                    if (TextUtils.isEmpty(new_participant.rank)) {
                        rights.append('\n').append('-').append(' ');
                        rights.append(LocaleController.getString("EventLogPromotedRemovedTitle", R.string.EventLogPromotedRemovedTitle));
                    } else {
                        rights.append('\n').append('+').append(' ');
                        rights.append(LocaleController.formatString("EventLogPromotedTitle", R.string.EventLogPromotedTitle, new_participant.rank));
                    }
                }
                if (o.change_info != n.change_info) {
                    rights.append('\n').append(n.change_info ? '+' : '-').append(' ');
                    rights.append(chat.megagroup ? LocaleController.getString("EventLogPromotedChangeGroupInfo", R.string.EventLogPromotedChangeGroupInfo) : LocaleController.getString("EventLogPromotedChangeChannelInfo", R.string.EventLogPromotedChangeChannelInfo));
                }
                if (!chat.megagroup) {
                    if (o.post_messages != n.post_messages) {
                        rights.append('\n').append(n.post_messages ? '+' : '-').append(' ');
                        rights.append(LocaleController.getString("EventLogPromotedPostMessages", R.string.EventLogPromotedPostMessages));
                    }
                    if (o.edit_messages != n.edit_messages) {
                        rights.append('\n').append(n.edit_messages ? '+' : '-').append(' ');
                        rights.append(LocaleController.getString("EventLogPromotedEditMessages", R.string.EventLogPromotedEditMessages));
                    }
                }
                if (o.delete_messages != n.delete_messages) {
                    rights.append('\n').append(n.delete_messages ? '+' : '-').append(' ');
                    rights.append(LocaleController.getString("EventLogPromotedDeleteMessages", R.string.EventLogPromotedDeleteMessages));
                }
                if (o.add_admins != n.add_admins) {
                    rights.append('\n').append(n.add_admins ? '+' : '-').append(' ');
                    rights.append(LocaleController.getString("EventLogPromotedAddAdmins", R.string.EventLogPromotedAddAdmins));
                }
                if (o.anonymous != n.anonymous) {
                    rights.append('\n').append(n.anonymous ? '+' : '-').append(' ');
                    rights.append(LocaleController.getString("EventLogPromotedSendAnonymously", R.string.EventLogPromotedSendAnonymously));
                }
                if (chat.megagroup) {
                    if (o.ban_users != n.ban_users) {
                        rights.append('\n').append(n.ban_users ? '+' : '-').append(' ');
                        rights.append(LocaleController.getString("EventLogPromotedBanUsers", R.string.EventLogPromotedBanUsers));
                    }
                    if (o.manage_call != n.manage_call) {
                        rights.append('\n').append(n.manage_call ? '+' : '-').append(' ');
                        rights.append(LocaleController.getString("EventLogPromotedManageCall", R.string.EventLogPromotedManageCall));
                    }
                }
                if (o.invite_users != n.invite_users) {
                    rights.append('\n').append(n.invite_users ? '+' : '-').append(' ');
                    rights.append(LocaleController.getString("EventLogPromotedAddUsers", R.string.EventLogPromotedAddUsers));
                }
                if (chat.megagroup) {
                    if (o.pin_messages != n.pin_messages) {
                        rights.append('\n').append(n.pin_messages ? '+' : '-').append(' ');
                        rights.append(LocaleController.getString("EventLogPromotedPinMessages", R.string.EventLogPromotedPinMessages));
                    }
                }
            }
            messageText = rights.toString();
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionDefaultBannedRights) {
            TLRPC.TL_channelAdminLogEventActionDefaultBannedRights bannedRights = (TLRPC.TL_channelAdminLogEventActionDefaultBannedRights) event.action;
            messageOwner = new TLRPC.TL_message();

            TLRPC.TL_chatBannedRights o = bannedRights.prev_banned_rights;
            TLRPC.TL_chatBannedRights n = bannedRights.new_banned_rights;
            StringBuilder rights = new StringBuilder(LocaleController.getString("EventLogDefaultPermissions", R.string.EventLogDefaultPermissions));
            boolean added = false;
            if (o == null) {
                o = new TLRPC.TL_chatBannedRights();
            }
            if (n == null) {
                n = new TLRPC.TL_chatBannedRights();
            }
            if (o.send_messages != n.send_messages) {
                rights.append('\n');
                added = true;
                rights.append('\n').append(!n.send_messages ? '+' : '-').append(' ');
                rights.append(LocaleController.getString("EventLogRestrictedSendMessages", R.string.EventLogRestrictedSendMessages));
            }
            if (o.send_stickers != n.send_stickers || o.send_inline != n.send_inline || o.send_gifs != n.send_gifs || o.send_games != n.send_games) {
                if (!added) {
                    rights.append('\n');
                    added = true;
                }
                rights.append('\n').append(!n.send_stickers ? '+' : '-').append(' ');
                rights.append(LocaleController.getString("EventLogRestrictedSendStickers", R.string.EventLogRestrictedSendStickers));
            }
            if (o.send_media != n.send_media) {
                if (!added) {
                    rights.append('\n');
                    added = true;
                }
                rights.append('\n').append(!n.send_media ? '+' : '-').append(' ');
                rights.append(LocaleController.getString("EventLogRestrictedSendMedia", R.string.EventLogRestrictedSendMedia));
            }
            if (o.send_polls != n.send_polls) {
                if (!added) {
                    rights.append('\n');
                    added = true;
                }
                rights.append('\n').append(!n.send_polls ? '+' : '-').append(' ');
                rights.append(LocaleController.getString("EventLogRestrictedSendPolls", R.string.EventLogRestrictedSendPolls));
            }
            if (o.embed_links != n.embed_links) {
                if (!added) {
                    rights.append('\n');
                    added = true;
                }
                rights.append('\n').append(!n.embed_links ? '+' : '-').append(' ');
                rights.append(LocaleController.getString("EventLogRestrictedSendEmbed", R.string.EventLogRestrictedSendEmbed));
            }

            if (o.change_info != n.change_info) {
                if (!added) {
                    rights.append('\n');
                    added = true;
                }
                rights.append('\n').append(!n.change_info ? '+' : '-').append(' ');
                rights.append(LocaleController.getString("EventLogRestrictedChangeInfo", R.string.EventLogRestrictedChangeInfo));
            }
            if (o.invite_users != n.invite_users) {
                if (!added) {
                    rights.append('\n');
                    added = true;
                }
                rights.append('\n').append(!n.invite_users ? '+' : '-').append(' ');
                rights.append(LocaleController.getString("EventLogRestrictedInviteUsers", R.string.EventLogRestrictedInviteUsers));
            }
            if (o.pin_messages != n.pin_messages) {
                if (!added) {
                    rights.append('\n');
                }
                rights.append('\n').append(!n.pin_messages ? '+' : '-').append(' ');
                rights.append(LocaleController.getString("EventLogRestrictedPinMessages", R.string.EventLogRestrictedPinMessages));
            }
            messageText = rights.toString();
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionParticipantToggleBan) {
            TLRPC.TL_channelAdminLogEventActionParticipantToggleBan action = (TLRPC.TL_channelAdminLogEventActionParticipantToggleBan) event.action;
            messageOwner = new TLRPC.TL_message();
            long peerId = getPeerId(action.prev_participant.peer);
            TLObject whoUser;
            if (peerId > 0) {
                whoUser = MessagesController.getInstance(currentAccount).getUser(peerId);
            } else {
                whoUser = MessagesController.getInstance(currentAccount).getChat(-peerId);
            }
            TLRPC.TL_chatBannedRights o = action.prev_participant.banned_rights;
            TLRPC.TL_chatBannedRights n = action.new_participant.banned_rights;
            if (chat.megagroup && (n == null || !n.view_messages || o != null && n.until_date != o.until_date)) {
                StringBuilder rights;
                StringBuilder bannedDuration;
                if (n != null && !AndroidUtilities.isBannedForever(n)) {
                    bannedDuration = new StringBuilder();
                    int duration = n.until_date - event.date;
                    int days = duration / 60 / 60 / 24;
                    duration -= days * 60 * 60 * 24;
                    int hours = duration / 60 / 60;
                    duration -= hours * 60 * 60;
                    int minutes = duration / 60;
                    int count = 0;
                    for (int a = 0; a < 3; a++) {
                        String addStr = null;
                        if (a == 0) {
                            if (days != 0) {
                                addStr = LocaleController.formatPluralString("Days", days);
                                count++;
                            }
                        } else if (a == 1) {
                            if (hours != 0) {
                                addStr = LocaleController.formatPluralString("Hours", hours);
                                count++;
                            }
                        } else {
                            if (minutes != 0) {
                                addStr = LocaleController.formatPluralString("Minutes", minutes);
                                count++;
                            }
                        }
                        if (addStr != null) {
                            if (bannedDuration.length() > 0) {
                                bannedDuration.append(", ");
                            }
                            bannedDuration.append(addStr);
                        }
                        if (count == 2) {
                            break;
                        }
                    }
                } else {
                    bannedDuration = new StringBuilder(LocaleController.getString("UserRestrictionsUntilForever", R.string.UserRestrictionsUntilForever));
                }
                String str = LocaleController.getString("EventLogRestrictedUntil", R.string.EventLogRestrictedUntil);
                int offset = str.indexOf("%1$s");
                rights = new StringBuilder(String.format(str, getUserName(whoUser, messageOwner.entities, offset), bannedDuration.toString()));
                boolean added = false;
                if (o == null) {
                    o = new TLRPC.TL_chatBannedRights();
                }
                if (n == null) {
                    n = new TLRPC.TL_chatBannedRights();
                }
                if (o.view_messages != n.view_messages) {
                    rights.append('\n');
                    added = true;
                    rights.append('\n').append(!n.view_messages ? '+' : '-').append(' ');
                    rights.append(LocaleController.getString("EventLogRestrictedReadMessages", R.string.EventLogRestrictedReadMessages));
                }
                if (o.send_messages != n.send_messages) {
                    if (!added) {
                        rights.append('\n');
                        added = true;
                    }
                    rights.append('\n').append(!n.send_messages ? '+' : '-').append(' ');
                    rights.append(LocaleController.getString("EventLogRestrictedSendMessages", R.string.EventLogRestrictedSendMessages));
                }
                if (o.send_stickers != n.send_stickers ||
                        o.send_inline != n.send_inline ||
                        o.send_gifs != n.send_gifs ||
                        o.send_games != n.send_games) {
                    if (!added) {
                        rights.append('\n');
                        added = true;
                    }
                    rights.append('\n').append(!n.send_stickers ? '+' : '-').append(' ');
                    rights.append(LocaleController.getString("EventLogRestrictedSendStickers", R.string.EventLogRestrictedSendStickers));
                }
                if (o.send_media != n.send_media) {
                    if (!added) {
                        rights.append('\n');
                        added = true;
                    }
                    rights.append('\n').append(!n.send_media ? '+' : '-').append(' ');
                    rights.append(LocaleController.getString("EventLogRestrictedSendMedia", R.string.EventLogRestrictedSendMedia));
                }
                if (o.send_polls != n.send_polls) {
                    if (!added) {
                        rights.append('\n');
                        added = true;
                    }
                    rights.append('\n').append(!n.send_polls ? '+' : '-').append(' ');
                    rights.append(LocaleController.getString("EventLogRestrictedSendPolls", R.string.EventLogRestrictedSendPolls));
                }
                if (o.embed_links != n.embed_links) {
                    if (!added) {
                        rights.append('\n');
                        added = true;
                    }
                    rights.append('\n').append(!n.embed_links ? '+' : '-').append(' ');
                    rights.append(LocaleController.getString("EventLogRestrictedSendEmbed", R.string.EventLogRestrictedSendEmbed));
                }

                if (o.change_info != n.change_info) {
                    if (!added) {
                        rights.append('\n');
                        added = true;
                    }
                    rights.append('\n').append(!n.change_info ? '+' : '-').append(' ');
                    rights.append(LocaleController.getString("EventLogRestrictedChangeInfo", R.string.EventLogRestrictedChangeInfo));
                }
                if (o.invite_users != n.invite_users) {
                    if (!added) {
                        rights.append('\n');
                        added = true;
                    }
                    rights.append('\n').append(!n.invite_users ? '+' : '-').append(' ');
                    rights.append(LocaleController.getString("EventLogRestrictedInviteUsers", R.string.EventLogRestrictedInviteUsers));
                }
                if (o.pin_messages != n.pin_messages) {
                    if (!added) {
                        rights.append('\n');
                    }
                    rights.append('\n').append(!n.pin_messages ? '+' : '-').append(' ');
                    rights.append(LocaleController.getString("EventLogRestrictedPinMessages", R.string.EventLogRestrictedPinMessages));
                }
                messageText = rights.toString();
            } else {
                String str;
                if (n != null && (o == null || n.view_messages)) {
                    str = LocaleController.getString("EventLogChannelRestricted", R.string.EventLogChannelRestricted);
                } else {
                    str = LocaleController.getString("EventLogChannelUnrestricted", R.string.EventLogChannelUnrestricted);
                }
                int offset = str.indexOf("%1$s");
                messageText = String.format(str, getUserName(whoUser, messageOwner.entities, offset));
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionUpdatePinned) {
            TLRPC.TL_channelAdminLogEventActionUpdatePinned action = (TLRPC.TL_channelAdminLogEventActionUpdatePinned) event.action;
            message = action.message;
            if (fromUser != null && fromUser.id == 136817688 && action.message.fwd_from != null && action.message.fwd_from.from_id instanceof TLRPC.TL_peerChannel) {
                TLRPC.Chat channel = MessagesController.getInstance(currentAccount).getChat(action.message.fwd_from.from_id.channel_id);
                if (action.message instanceof TLRPC.TL_messageEmpty || !action.message.pinned) {
                    messageText = replaceWithLink(LocaleController.getString("EventLogUnpinnedMessages", R.string.EventLogUnpinnedMessages), "un1", channel);
                } else {
                    messageText = replaceWithLink(LocaleController.getString("EventLogPinnedMessages", R.string.EventLogPinnedMessages), "un1", channel);
                }
            } else {
                if (action.message instanceof TLRPC.TL_messageEmpty || !action.message.pinned) {
                    messageText = replaceWithLink(LocaleController.getString("EventLogUnpinnedMessages", R.string.EventLogUnpinnedMessages), "un1", fromUser);
                } else {
                    messageText = replaceWithLink(LocaleController.getString("EventLogPinnedMessages", R.string.EventLogPinnedMessages), "un1", fromUser);
                }
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionStopPoll) {
            TLRPC.TL_channelAdminLogEventActionStopPoll action = (TLRPC.TL_channelAdminLogEventActionStopPoll) event.action;
            message = action.message;
            if (message.media instanceof TLRPC.TL_messageMediaPoll && ((TLRPC.TL_messageMediaPoll) message.media).poll.quiz) {
                messageText = replaceWithLink(LocaleController.getString("EventLogStopQuiz", R.string.EventLogStopQuiz), "un1", fromUser);
            } else {
                messageText = replaceWithLink(LocaleController.getString("EventLogStopPoll", R.string.EventLogStopPoll), "un1", fromUser);
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionToggleSignatures) {
            if (((TLRPC.TL_channelAdminLogEventActionToggleSignatures) event.action).new_value) {
                messageText = replaceWithLink(LocaleController.getString("EventLogToggledSignaturesOn", R.string.EventLogToggledSignaturesOn), "un1", fromUser);
            } else {
                messageText = replaceWithLink(LocaleController.getString("EventLogToggledSignaturesOff", R.string.EventLogToggledSignaturesOff), "un1", fromUser);
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionToggleInvites) {
            if (((TLRPC.TL_channelAdminLogEventActionToggleInvites) event.action).new_value) {
                messageText = replaceWithLink(LocaleController.getString("EventLogToggledInvitesOn", R.string.EventLogToggledInvitesOn), "un1", fromUser);
            } else {
                messageText = replaceWithLink(LocaleController.getString("EventLogToggledInvitesOff", R.string.EventLogToggledInvitesOff), "un1", fromUser);
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionDeleteMessage) {
            message = ((TLRPC.TL_channelAdminLogEventActionDeleteMessage) event.action).message;
            messageText = replaceWithLink(LocaleController.getString("EventLogDeletedMessages", R.string.EventLogDeletedMessages), "un1", fromUser);
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionChangeLinkedChat) {
            long newChatId = ((TLRPC.TL_channelAdminLogEventActionChangeLinkedChat) event.action).new_value;
            long oldChatId = ((TLRPC.TL_channelAdminLogEventActionChangeLinkedChat) event.action).prev_value;
            if (chat.megagroup) {
                if (newChatId == 0) {
                    TLRPC.Chat oldChat = MessagesController.getInstance(currentAccount).getChat(oldChatId);
                    messageText = replaceWithLink(LocaleController.getString("EventLogRemovedLinkedChannel", R.string.EventLogRemovedLinkedChannel), "un1", fromUser);
                    messageText = replaceWithLink(messageText, "un2", oldChat);
                } else {
                    TLRPC.Chat newChat = MessagesController.getInstance(currentAccount).getChat(newChatId);
                    messageText = replaceWithLink(LocaleController.getString("EventLogChangedLinkedChannel", R.string.EventLogChangedLinkedChannel), "un1", fromUser);
                    messageText = replaceWithLink(messageText, "un2", newChat);
                }
            } else {
                if (newChatId == 0) {
                    TLRPC.Chat oldChat = MessagesController.getInstance(currentAccount).getChat(oldChatId);
                    messageText = replaceWithLink(LocaleController.getString("EventLogRemovedLinkedGroup", R.string.EventLogRemovedLinkedGroup), "un1", fromUser);
                    messageText = replaceWithLink(messageText, "un2", oldChat);
                } else {
                    TLRPC.Chat newChat = MessagesController.getInstance(currentAccount).getChat(newChatId);
                    messageText = replaceWithLink(LocaleController.getString("EventLogChangedLinkedGroup", R.string.EventLogChangedLinkedGroup), "un1", fromUser);
                    messageText = replaceWithLink(messageText, "un2", newChat);
                }
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionTogglePreHistoryHidden) {
            if (((TLRPC.TL_channelAdminLogEventActionTogglePreHistoryHidden) event.action).new_value) {
                messageText = replaceWithLink(LocaleController.getString("EventLogToggledInvitesHistoryOff", R.string.EventLogToggledInvitesHistoryOff), "un1", fromUser);
            } else {
                messageText = replaceWithLink(LocaleController.getString("EventLogToggledInvitesHistoryOn", R.string.EventLogToggledInvitesHistoryOn), "un1", fromUser);
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionChangeAbout) {
            messageText = replaceWithLink(chat.megagroup ? LocaleController.getString("EventLogEditedGroupDescription", R.string.EventLogEditedGroupDescription) : LocaleController.getString("EventLogEditedChannelDescription", R.string.EventLogEditedChannelDescription), "un1", fromUser);
            message = new TLRPC.TL_message();
            message.out = false;
            message.unread = false;
            message.from_id = new TLRPC.TL_peerUser();
            message.from_id.user_id = event.user_id;
            message.peer_id = peer_id;
            message.date = event.date;
            message.message = ((TLRPC.TL_channelAdminLogEventActionChangeAbout) event.action).new_value;
            if (!TextUtils.isEmpty(((TLRPC.TL_channelAdminLogEventActionChangeAbout) event.action).prev_value)) {
                message.media = new TLRPC.TL_messageMediaWebPage();
                message.media.webpage = new TLRPC.TL_webPage();
                message.media.webpage.flags = 10;
                message.media.webpage.display_url = "";
                message.media.webpage.url = "";
                message.media.webpage.site_name = LocaleController.getString("EventLogPreviousGroupDescription", R.string.EventLogPreviousGroupDescription);
                message.media.webpage.description = ((TLRPC.TL_channelAdminLogEventActionChangeAbout) event.action).prev_value;
            } else {
                message.media = new TLRPC.TL_messageMediaEmpty();
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionChangeTheme) {
            messageText = replaceWithLink(chat.megagroup ? LocaleController.getString("EventLogEditedGroupTheme", R.string.EventLogEditedGroupTheme) : LocaleController.getString("EventLogEditedChannelTheme", R.string.EventLogEditedChannelTheme), "un1", fromUser);
            message = new TLRPC.TL_message();
            message.out = false;
            message.unread = false;
            message.from_id = new TLRPC.TL_peerUser();
            message.from_id.user_id = event.user_id;
            message.peer_id = peer_id;
            message.date = event.date;
            message.message = ((TLRPC.TL_channelAdminLogEventActionChangeTheme) event.action).new_value;
            if (!TextUtils.isEmpty(((TLRPC.TL_channelAdminLogEventActionChangeTheme) event.action).prev_value)) {
                message.media = new TLRPC.TL_messageMediaWebPage();
                message.media.webpage = new TLRPC.TL_webPage();
                message.media.webpage.flags = 10;
                message.media.webpage.display_url = "";
                message.media.webpage.url = "";
                message.media.webpage.site_name = LocaleController.getString("EventLogPreviousGroupTheme", R.string.EventLogPreviousGroupTheme);
                message.media.webpage.description = ((TLRPC.TL_channelAdminLogEventActionChangeTheme) event.action).prev_value;
            } else {
                message.media = new TLRPC.TL_messageMediaEmpty();
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionChangeUsername) {
            String newLink = ((TLRPC.TL_channelAdminLogEventActionChangeUsername) event.action).new_value;
            if (!TextUtils.isEmpty(newLink)) {
                messageText = replaceWithLink(chat.megagroup ? LocaleController.getString("EventLogChangedGroupLink", R.string.EventLogChangedGroupLink) : LocaleController.getString("EventLogChangedChannelLink", R.string.EventLogChangedChannelLink), "un1", fromUser);
            } else {
                messageText = replaceWithLink(chat.megagroup ? LocaleController.getString("EventLogRemovedGroupLink", R.string.EventLogRemovedGroupLink) : LocaleController.getString("EventLogRemovedChannelLink", R.string.EventLogRemovedChannelLink), "un1", fromUser);
            }
            message = new TLRPC.TL_message();
            message.out = false;
            message.unread = false;
            message.from_id = new TLRPC.TL_peerUser();
            message.from_id.user_id = event.user_id;
            message.peer_id = peer_id;
            message.date = event.date;
            if (!TextUtils.isEmpty(newLink)) {
                message.message = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/" + newLink;
            } else {
                message.message = "";
            }
            TLRPC.TL_messageEntityUrl url = new TLRPC.TL_messageEntityUrl();
            url.offset = 0;
            url.length = message.message.length();
            message.entities.add(url);
            if (!TextUtils.isEmpty(((TLRPC.TL_channelAdminLogEventActionChangeUsername) event.action).prev_value)) {
                message.media = new TLRPC.TL_messageMediaWebPage();
                message.media.webpage = new TLRPC.TL_webPage();
                message.media.webpage.flags = 10;
                message.media.webpage.display_url = "";
                message.media.webpage.url = "";
                message.media.webpage.site_name = LocaleController.getString("EventLogPreviousLink", R.string.EventLogPreviousLink);
                message.media.webpage.description = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/" + ((TLRPC.TL_channelAdminLogEventActionChangeUsername) event.action).prev_value;
            } else {
                message.media = new TLRPC.TL_messageMediaEmpty();
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionEditMessage) {
            message = new TLRPC.TL_message();
            message.out = false;
            message.unread = false;
            message.peer_id = peer_id;
            message.date = event.date;
            TLRPC.Message newMessage = ((TLRPC.TL_channelAdminLogEventActionEditMessage) event.action).new_message;
            TLRPC.Message oldMessage = ((TLRPC.TL_channelAdminLogEventActionEditMessage) event.action).prev_message;
            if (newMessage != null && newMessage.from_id != null) {
                message.from_id = newMessage.from_id;
            } else {
                message.from_id = new TLRPC.TL_peerUser();
                message.from_id.user_id = event.user_id;
            }
            if (newMessage.media != null && !(newMessage.media instanceof TLRPC.TL_messageMediaEmpty) && !(newMessage.media instanceof TLRPC.TL_messageMediaWebPage)/* && TextUtils.isEmpty(newMessage.message)*/) {
                boolean changedCaption;
                boolean changedMedia;
                if (!TextUtils.equals(newMessage.message, oldMessage.message)) {
                    changedCaption = true;
                } else {
                    changedCaption = false;
                }
                if (newMessage.media.getClass() != oldMessage.media.getClass() ||
                        newMessage.media.photo != null && oldMessage.media.photo != null && newMessage.media.photo.id != oldMessage.media.photo.id ||
                        newMessage.media.document != null && oldMessage.media.document != null && newMessage.media.document.id != oldMessage.media.document.id) {
                    changedMedia = true;
                } else {
                    changedMedia = false;
                }
                if (changedMedia && changedCaption) {
                    messageText = replaceWithLink(LocaleController.getString("EventLogEditedMediaCaption", R.string.EventLogEditedMediaCaption), "un1", fromUser);
                } else if (changedCaption) {
                    messageText = replaceWithLink(LocaleController.getString("EventLogEditedCaption", R.string.EventLogEditedCaption), "un1", fromUser);
                } else {
                    messageText = replaceWithLink(LocaleController.getString("EventLogEditedMedia", R.string.EventLogEditedMedia), "un1", fromUser);
                }
                message.media = newMessage.media;
                if (changedCaption) {
                    message.media.webpage = new TLRPC.TL_webPage();
                    message.media.webpage.site_name = LocaleController.getString("EventLogOriginalCaption", R.string.EventLogOriginalCaption);
                    if (TextUtils.isEmpty(oldMessage.message)) {
                        message.media.webpage.description = LocaleController.getString("EventLogOriginalCaptionEmpty", R.string.EventLogOriginalCaptionEmpty);
                    } else {
                        message.media.webpage.description = oldMessage.message;
                    }
                }
            } else {
                messageText = replaceWithLink(LocaleController.getString("EventLogEditedMessages", R.string.EventLogEditedMessages), "un1", fromUser);
                if (newMessage.action instanceof TLRPC.TL_messageActionGroupCall) {
                    message = newMessage;
                    message.media = new TLRPC.TL_messageMediaEmpty();
                } else {
                    message.message = newMessage.message;
                    message.media = new TLRPC.TL_messageMediaWebPage();
                    message.media.webpage = new TLRPC.TL_webPage();
                    message.media.webpage.site_name = LocaleController.getString("EventLogOriginalMessages", R.string.EventLogOriginalMessages);
                    if (TextUtils.isEmpty(oldMessage.message)) {
                        message.media.webpage.description = LocaleController.getString("EventLogOriginalCaptionEmpty", R.string.EventLogOriginalCaptionEmpty);
                    } else {
                        message.media.webpage.description = oldMessage.message;
                    }
                }
            }
            message.reply_markup = newMessage.reply_markup;
            if (message.media.webpage != null) {
                message.media.webpage.flags = 10;
                message.media.webpage.display_url = "";
                message.media.webpage.url = "";
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionChangeStickerSet) {
            TLRPC.InputStickerSet newStickerset = ((TLRPC.TL_channelAdminLogEventActionChangeStickerSet) event.action).new_stickerset;
            TLRPC.InputStickerSet oldStickerset = ((TLRPC.TL_channelAdminLogEventActionChangeStickerSet) event.action).new_stickerset;
            if (newStickerset == null || newStickerset instanceof TLRPC.TL_inputStickerSetEmpty) {
                messageText = replaceWithLink(LocaleController.getString("EventLogRemovedStickersSet", R.string.EventLogRemovedStickersSet), "un1", fromUser);
            } else {
                messageText = replaceWithLink(LocaleController.getString("EventLogChangedStickersSet", R.string.EventLogChangedStickersSet), "un1", fromUser);
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionChangeLocation) {
            TLRPC.TL_channelAdminLogEventActionChangeLocation location = (TLRPC.TL_channelAdminLogEventActionChangeLocation) event.action;
            if (location.new_value instanceof TLRPC.TL_channelLocationEmpty) {
                messageText = replaceWithLink(LocaleController.getString("EventLogRemovedLocation", R.string.EventLogRemovedLocation), "un1", fromUser);
            } else {
                TLRPC.TL_channelLocation channelLocation = (TLRPC.TL_channelLocation) location.new_value;
                messageText = replaceWithLink(LocaleController.formatString("EventLogChangedLocation", R.string.EventLogChangedLocation, channelLocation.address), "un1", fromUser);
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionToggleSlowMode) {
            TLRPC.TL_channelAdminLogEventActionToggleSlowMode slowMode = (TLRPC.TL_channelAdminLogEventActionToggleSlowMode) event.action;
            if (slowMode.new_value == 0) {
                messageText = replaceWithLink(LocaleController.getString("EventLogToggledSlowmodeOff", R.string.EventLogToggledSlowmodeOff), "un1", fromUser);
            } else {
                String string;
                if (slowMode.new_value < 60) {
                    string = LocaleController.formatPluralString("Seconds", slowMode.new_value);
                } else if (slowMode.new_value < 60 * 60) {
                    string = LocaleController.formatPluralString("Minutes", slowMode.new_value / 60);
                } else {
                    string = LocaleController.formatPluralString("Hours", slowMode.new_value / 60 / 60);
                }
                messageText = replaceWithLink(LocaleController.formatString("EventLogToggledSlowmodeOn", R.string.EventLogToggledSlowmodeOn, string), "un1", fromUser);
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionStartGroupCall) {
            if (ChatObject.isChannel(chat) && (!chat.megagroup || chat.gigagroup)) {
                messageText = replaceWithLink(LocaleController.getString("EventLogStartedLiveStream", R.string.EventLogStartedLiveStream), "un1", fromUser);
            } else {
                messageText = replaceWithLink(LocaleController.getString("EventLogStartedVoiceChat", R.string.EventLogStartedVoiceChat), "un1", fromUser);
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionDiscardGroupCall) {
            if (ChatObject.isChannel(chat) && (!chat.megagroup || chat.gigagroup)) {
                messageText = replaceWithLink(LocaleController.getString("EventLogEndedLiveStream", R.string.EventLogEndedLiveStream), "un1", fromUser);
            } else {
                messageText = replaceWithLink(LocaleController.getString("EventLogEndedVoiceChat", R.string.EventLogEndedVoiceChat), "un1", fromUser);
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionParticipantMute) {
            TLRPC.TL_channelAdminLogEventActionParticipantMute action = (TLRPC.TL_channelAdminLogEventActionParticipantMute) event.action;
            long id = getPeerId(action.participant.peer);
            TLObject object;
            if (id > 0) {
                object = MessagesController.getInstance(currentAccount).getUser(id);
            } else {
                object = MessagesController.getInstance(currentAccount).getChat(-id);
            }
            messageText = replaceWithLink(LocaleController.getString("EventLogVoiceChatMuted", R.string.EventLogVoiceChatMuted), "un1", fromUser);
            messageText = replaceWithLink(messageText, "un2", object);
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionParticipantUnmute) {
            TLRPC.TL_channelAdminLogEventActionParticipantUnmute action = (TLRPC.TL_channelAdminLogEventActionParticipantUnmute) event.action;
            long id = getPeerId(action.participant.peer);
            TLObject object;
            if (id > 0) {
                object = MessagesController.getInstance(currentAccount).getUser(id);
            } else {
                object = MessagesController.getInstance(currentAccount).getChat(-id);
            }
            messageText = replaceWithLink(LocaleController.getString("EventLogVoiceChatUnmuted", R.string.EventLogVoiceChatUnmuted), "un1", fromUser);
            messageText = replaceWithLink(messageText, "un2", object);
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionToggleGroupCallSetting) {
            TLRPC.TL_channelAdminLogEventActionToggleGroupCallSetting action = (TLRPC.TL_channelAdminLogEventActionToggleGroupCallSetting) event.action;
            if (action.join_muted) {
                messageText = replaceWithLink(LocaleController.getString("EventLogVoiceChatNotAllowedToSpeak", R.string.EventLogVoiceChatNotAllowedToSpeak), "un1", fromUser);
            } else {
                messageText = replaceWithLink(LocaleController.getString("EventLogVoiceChatAllowedToSpeak", R.string.EventLogVoiceChatAllowedToSpeak), "un1", fromUser);
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionParticipantJoinByInvite) {
            TLRPC.TL_channelAdminLogEventActionParticipantJoinByInvite action = (TLRPC.TL_channelAdminLogEventActionParticipantJoinByInvite) event.action;
            messageText = replaceWithLink(LocaleController.getString("ActionInviteUser", R.string.ActionInviteUser), "un1", fromUser);
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionToggleNoForwards) {
            TLRPC.TL_channelAdminLogEventActionToggleNoForwards action = (TLRPC.TL_channelAdminLogEventActionToggleNoForwards) event.action;
            boolean isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
            if (action.new_value) {
                if (isChannel) {
                    messageText = replaceWithLink(LocaleController.getString("ActionForwardsRestrictedChannel", R.string.ActionForwardsRestrictedChannel), "un1", fromUser);
                } else {
                    messageText = replaceWithLink(LocaleController.getString("ActionForwardsRestrictedGroup", R.string.ActionForwardsRestrictedGroup), "un1", fromUser);
                }
            } else {
                if (isChannel) {
                    messageText = replaceWithLink(LocaleController.getString("ActionForwardsEnabledChannel", R.string.ActionForwardsEnabledChannel), "un1", fromUser);
                } else {
                    messageText = replaceWithLink(LocaleController.getString("ActionForwardsEnabledGroup", R.string.ActionForwardsEnabledGroup), "un1", fromUser);
                }
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionExportedInviteDelete) {
            TLRPC.TL_channelAdminLogEventActionExportedInviteDelete action = (TLRPC.TL_channelAdminLogEventActionExportedInviteDelete) event.action;
            messageText = replaceWithLink(LocaleController.formatString("ActionDeletedInviteLinkClickable", R.string.ActionDeletedInviteLinkClickable), "un1", fromUser);
            messageText = replaceWithLink(messageText, "un2", action.invite);
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionExportedInviteRevoke) {
            TLRPC.TL_channelAdminLogEventActionExportedInviteRevoke action = (TLRPC.TL_channelAdminLogEventActionExportedInviteRevoke) event.action;
            messageText = replaceWithLink(LocaleController.formatString("ActionRevokedInviteLinkClickable", R.string.ActionRevokedInviteLinkClickable, action.invite.link), "un1", fromUser);
            messageText = replaceWithLink(messageText, "un2", action.invite);
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionExportedInviteEdit) {
            TLRPC.TL_channelAdminLogEventActionExportedInviteEdit action = (TLRPC.TL_channelAdminLogEventActionExportedInviteEdit) event.action;
            if (action.prev_invite.link != null &&  action.prev_invite.link.equals(action.new_invite.link)){
                messageText = replaceWithLink(LocaleController.formatString("ActionEditedInviteLinkToSameClickable", R.string.ActionEditedInviteLinkToSameClickable), "un1", fromUser);
            } else {
                messageText = replaceWithLink(LocaleController.formatString("ActionEditedInviteLinkClickable", R.string.ActionEditedInviteLinkClickable), "un1", fromUser);
            }
            messageText = replaceWithLink(messageText, "un2", action.prev_invite);
            messageText = replaceWithLink(messageText, "un3", action.new_invite);
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionParticipantVolume) {
            TLRPC.TL_channelAdminLogEventActionParticipantVolume action = (TLRPC.TL_channelAdminLogEventActionParticipantVolume) event.action;
            long id = getPeerId(action.participant.peer);
            TLObject object;
            if (id > 0) {
                object = MessagesController.getInstance(currentAccount).getUser(id);
            } else {
                object = MessagesController.getInstance(currentAccount).getChat(-id);
            }
            double vol = ChatObject.getParticipantVolume(action.participant) / 100.0;
            messageText = replaceWithLink(LocaleController.formatString("ActionVolumeChanged", R.string.ActionVolumeChanged,  (int) (vol > 0 ? Math.max(vol, 1) : 0)), "un1", fromUser);
            messageText = replaceWithLink(messageText, "un2", object);
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionChangeHistoryTTL) {
            TLRPC.TL_channelAdminLogEventActionChangeHistoryTTL action = (TLRPC.TL_channelAdminLogEventActionChangeHistoryTTL) event.action;
            if (!chat.megagroup) {
                if (action.new_value != 0) {
                    messageText = LocaleController.formatString("ActionTTLChannelChanged", R.string.ActionTTLChannelChanged, LocaleController.formatTTLString(action.new_value));
                } else {
                    messageText = LocaleController.getString("ActionTTLChannelDisabled", R.string.ActionTTLChannelDisabled);
                }
            } else if (action.new_value == 0) {
                messageText = replaceWithLink(LocaleController.getString("ActionTTLDisabled", R.string.ActionTTLDisabled), "un1", fromUser);
            } else {
                String time;
                if (action.new_value > 24 * 60 * 60) {
                    time = LocaleController.formatPluralString("Days", action.new_value / (24 * 60 * 60));
                } else if (action.new_value >= 60 * 60) {
                    time = LocaleController.formatPluralString("Hours", action.new_value / (60 * 60));
                } else if (action.new_value >= 60) {
                    time = LocaleController.formatPluralString("Minutes", action.new_value / 60);
                } else {
                    time = LocaleController.formatPluralString("Seconds", action.new_value);
                }
                messageText = replaceWithLink(LocaleController.formatString("ActionTTLChanged", R.string.ActionTTLChanged, time), "un1", fromUser);
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionParticipantJoinByRequest) {
            TLRPC.TL_channelAdminLogEventActionParticipantJoinByRequest action = (TLRPC.TL_channelAdminLogEventActionParticipantJoinByRequest) event.action;
            messageText = replaceWithLink(LocaleController.getString("JoinedViaInviteLinkApproved", R.string.JoinedViaInviteLinkApproved), "un1", fromUser);
            messageText = replaceWithLink(messageText, "un2", action.invite);
            messageText = replaceWithLink(messageText, "un3", MessagesController.getInstance(currentAccount).getUser(action.approved_by));
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionSendMessage) {
            message = ((TLRPC.TL_channelAdminLogEventActionSendMessage) event.action).message;
            messageText = replaceWithLink(LocaleController.getString("EventLogSendMessages", R.string.EventLogSendMessages), "un1", fromUser);
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionChangeAvailableReactions) {
            String oldReactions = TextUtils.join(", ", ((TLRPC.TL_channelAdminLogEventActionChangeAvailableReactions) event.action).prev_value);
            String newReactions = TextUtils.join(", ", ((TLRPC.TL_channelAdminLogEventActionChangeAvailableReactions) event.action).new_value);
            messageText = replaceWithLink(LocaleController.formatString("ActionReactionsChanged", R.string.ActionReactionsChanged, oldReactions, newReactions), "un1", fromUser);
        } else {
            messageText = "unsupported " + event.action;
        }

        if (messageOwner == null) {
            messageOwner = new TLRPC.TL_messageService();
        }
        messageOwner.message = messageText.toString();
        messageOwner.from_id = new TLRPC.TL_peerUser();
        messageOwner.from_id.user_id = event.user_id;
        messageOwner.date = event.date;
        messageOwner.id = mid[0]++;
        eventId = event.id;
        messageOwner.out = false;
        messageOwner.peer_id = new TLRPC.TL_peerChannel();
        messageOwner.peer_id.channel_id = chat.id;
        messageOwner.unread = false;
        MediaController mediaController = MediaController.getInstance();

        if (message instanceof TLRPC.TL_messageEmpty) {
            message = null;
        }

        if (message != null) {
            message.out = false;
            message.id = mid[0]++;
            message.flags &=~ TLRPC.MESSAGE_FLAG_REPLY;
            message.reply_to = null;
            message.flags = message.flags &~ TLRPC.MESSAGE_FLAG_EDITED;
            MessageObject messageObject = new MessageObject(currentAccount, message, null, null, true, true, eventId);
            if (messageObject.contentType >= 0) {
                if (mediaController.isPlayingMessage(messageObject)) {
                    MessageObject player = mediaController.getPlayingMessageObject();
                    messageObject.audioProgress = player.audioProgress;
                    messageObject.audioProgressSec = player.audioProgressSec;
                }
                createDateArray(currentAccount, event, messageObjects, messagesByDays, addToEnd);
                if (addToEnd) {
                    messageObjects.add(0, messageObject);
                } else {
                    messageObjects.add(messageObjects.size() - 1, messageObject);
                }
            } else {
                contentType = -1;
            }
        }
        if (contentType >= 0) {
            createDateArray(currentAccount, event, messageObjects, messagesByDays, addToEnd);
            if (addToEnd) {
                messageObjects.add(0, this);
            } else {
                messageObjects.add(messageObjects.size() - 1, this);
            }
        } else {
            return;
        }

        if (messageText == null) {
            messageText = "";
        }

        setType();
        measureInlineBotButtons();
        generateCaption();

        TextPaint paint;
        if (messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
            paint = Theme.chat_msgGameTextPaint;
        } else {
            paint = Theme.chat_msgTextPaint;
        }
        int[] emojiOnly = allowsBigEmoji() ? new int[1] : null;
        messageText = Emoji.replaceEmoji(messageText, paint.getFontMetricsInt(), AndroidUtilities.dp(20), false, emojiOnly);
        checkEmojiOnly(emojiOnly);
        if (mediaController.isPlayingMessage(this)) {
            MessageObject player = mediaController.getPlayingMessageObject();
            audioProgress = player.audioProgress;
            audioProgressSec = player.audioProgressSec;
        }
        generateLayout(fromUser);
        layoutCreated = true;
        generateThumbs(false);
        checkMediaExistance();
    }

    private String getUserName(TLObject object, ArrayList<TLRPC.MessageEntity> entities, int offset) {
        String name;
        String username;
        long id;
        if (object == null) {
            name = "";
            username = null;
            id = 0;
        } else if (object instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) object;
            if (user.deleted) {
                name = LocaleController.getString("HiddenName", R.string.HiddenName);
            } else {
                name = ContactsController.formatName(user.first_name, user.last_name);
            }
            username = user.username;
            id = user.id;
        } else {
            TLRPC.Chat chat = (TLRPC.Chat) object;
            name = chat.title;
            username = chat.username;
            id = -chat.id;
        }
        if (offset >= 0) {
            TLRPC.TL_messageEntityMentionName entity = new TLRPC.TL_messageEntityMentionName();
            entity.user_id = id;
            entity.offset = offset;
            entity.length = name.length();
            entities.add(entity);
        }
        if (!TextUtils.isEmpty(username)) {
            if (offset >= 0) {
                TLRPC.TL_messageEntityMentionName entity = new TLRPC.TL_messageEntityMentionName();
                entity.user_id = id;
                entity.offset = offset + name.length() + 2;
                entity.length = username.length() + 1;
                entities.add(entity);
            }
            return String.format("%1$s (@%2$s)", name, username);
        }
        return name;
    }

    public void applyNewText() {
        if (TextUtils.isEmpty(messageOwner.message)) {
            return;
        }
        TLRPC.User fromUser = null;
        if (isFromUser()) {
            fromUser = MessagesController.getInstance(currentAccount).getUser(messageOwner.from_id.user_id);
        }
        messageText = messageOwner.message;
        TextPaint paint;
        if (messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
            paint = Theme.chat_msgGameTextPaint;
        } else {
            paint = Theme.chat_msgTextPaint;
        }
        int[] emojiOnly = allowsBigEmoji() ? new int[1] : null;
        messageText = Emoji.replaceEmoji(messageText, paint.getFontMetricsInt(), AndroidUtilities.dp(20), false, emojiOnly);
        checkEmojiOnly(emojiOnly);
        generateLayout(fromUser);
    }

    private boolean allowsBigEmoji() {
        if (!SharedConfig.allowBigEmoji) {
            return false;
        }
        if (messageOwner == null || messageOwner.peer_id == null || messageOwner.peer_id.channel_id == 0 && messageOwner.peer_id.chat_id == 0) {
            return true;
        }
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(messageOwner.peer_id.channel_id != 0 ? messageOwner.peer_id.channel_id : messageOwner.peer_id.chat_id);
        return chat != null && chat.gigagroup || (!ChatObject.isActionBanned(chat, ChatObject.ACTION_SEND_STICKERS) || ChatObject.hasAdminRights(chat));
    }

    public void generateGameMessageText(TLRPC.User fromUser) {
        if (fromUser == null && isFromUser()) {
            fromUser = MessagesController.getInstance(currentAccount).getUser(messageOwner.from_id.user_id);
        }
        TLRPC.TL_game game = null;
        if (replyMessageObject != null && replyMessageObject.messageOwner.media != null && replyMessageObject.messageOwner.media.game != null) {
            game = replyMessageObject.messageOwner.media.game;
        }
        if (game == null) {
            if (fromUser != null && fromUser.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                messageText = LocaleController.formatString("ActionYouScored", R.string.ActionYouScored, LocaleController.formatPluralString("Points", messageOwner.action.score));
            } else {
                messageText = replaceWithLink(LocaleController.formatString("ActionUserScored", R.string.ActionUserScored, LocaleController.formatPluralString("Points", messageOwner.action.score)), "un1", fromUser);
            }
        } else {
            if (fromUser != null && fromUser.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                messageText = LocaleController.formatString("ActionYouScoredInGame", R.string.ActionYouScoredInGame, LocaleController.formatPluralString("Points", messageOwner.action.score));
            } else {
                messageText = replaceWithLink(LocaleController.formatString("ActionUserScoredInGame", R.string.ActionUserScoredInGame, LocaleController.formatPluralString("Points", messageOwner.action.score)), "un1", fromUser);
            }
            messageText = replaceWithLink(messageText, "un2", game);
        }
    }

    public boolean hasValidReplyMessageObject() {
        return !(replyMessageObject == null || replyMessageObject.messageOwner instanceof TLRPC.TL_messageEmpty || replyMessageObject.messageOwner.action instanceof TLRPC.TL_messageActionHistoryClear);
    }

    public void generatePaymentSentMessageText(TLRPC.User fromUser) {
        if (fromUser == null) {
            fromUser = MessagesController.getInstance(currentAccount).getUser(getDialogId());
        }
        String name;
        if (fromUser != null) {
            name = UserObject.getFirstName(fromUser);
        } else {
            name = "";
        }
        if (replyMessageObject != null && replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) {
            messageText = LocaleController.formatString("PaymentSuccessfullyPaid", R.string.PaymentSuccessfullyPaid, LocaleController.getInstance().formatCurrencyString(messageOwner.action.total_amount, messageOwner.action.currency), name, replyMessageObject.messageOwner.media.title);
        } else {
            messageText = LocaleController.formatString("PaymentSuccessfullyPaidNoItem", R.string.PaymentSuccessfullyPaidNoItem, LocaleController.getInstance().formatCurrencyString(messageOwner.action.total_amount, messageOwner.action.currency), name);
        }
    }

    public void generatePinMessageText(TLRPC.User fromUser, TLRPC.Chat chat) {
        if (fromUser == null && chat == null) {
            if (isFromUser()) {
                fromUser = MessagesController.getInstance(currentAccount).getUser(messageOwner.from_id.user_id);
            }
            if (fromUser == null) {
                if (messageOwner.peer_id instanceof TLRPC.TL_peerChannel) {
                    chat = MessagesController.getInstance(currentAccount).getChat(messageOwner.peer_id.channel_id);
                } else if (messageOwner.peer_id instanceof TLRPC.TL_peerChat) {
                    chat = MessagesController.getInstance(currentAccount).getChat(messageOwner.peer_id.chat_id);
                }
            }
        }
        if (replyMessageObject == null || replyMessageObject.messageOwner instanceof TLRPC.TL_messageEmpty || replyMessageObject.messageOwner.action instanceof TLRPC.TL_messageActionHistoryClear) {
            messageText = replaceWithLink(LocaleController.getString("ActionPinnedNoText", R.string.ActionPinnedNoText), "un1", fromUser != null ? fromUser : chat);
        } else {
            if (replyMessageObject.isMusic()) {
                messageText = replaceWithLink(LocaleController.getString("ActionPinnedMusic", R.string.ActionPinnedMusic), "un1", fromUser != null ? fromUser : chat);
            } else if (replyMessageObject.isVideo()) {
                messageText = replaceWithLink(LocaleController.getString("ActionPinnedVideo", R.string.ActionPinnedVideo), "un1", fromUser != null ? fromUser : chat);
            } else if (replyMessageObject.isGif()) {
                messageText = replaceWithLink(LocaleController.getString("ActionPinnedGif", R.string.ActionPinnedGif), "un1", fromUser != null ? fromUser : chat);
            } else if (replyMessageObject.isVoice()) {
                messageText = replaceWithLink(LocaleController.getString("ActionPinnedVoice", R.string.ActionPinnedVoice), "un1", fromUser != null ? fromUser : chat);
            } else if (replyMessageObject.isRoundVideo()) {
                messageText = replaceWithLink(LocaleController.getString("ActionPinnedRound", R.string.ActionPinnedRound), "un1", fromUser != null ? fromUser : chat);
            } else if ((replyMessageObject.isSticker() || replyMessageObject.isAnimatedSticker()) && !replyMessageObject.isAnimatedEmoji()) {
                messageText = replaceWithLink(LocaleController.getString("ActionPinnedSticker", R.string.ActionPinnedSticker), "un1", fromUser != null ? fromUser : chat);
            } else if (replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                messageText = replaceWithLink(LocaleController.getString("ActionPinnedFile", R.string.ActionPinnedFile), "un1", fromUser != null ? fromUser : chat);
            } else if (replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo) {
                messageText = replaceWithLink(LocaleController.getString("ActionPinnedGeo", R.string.ActionPinnedGeo), "un1", fromUser != null ? fromUser : chat);
            } else if (replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                messageText = replaceWithLink(LocaleController.getString("ActionPinnedGeoLive", R.string.ActionPinnedGeoLive), "un1", fromUser != null ? fromUser : chat);
            } else if (replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                messageText = replaceWithLink(LocaleController.getString("ActionPinnedContact", R.string.ActionPinnedContact), "un1", fromUser != null ? fromUser : chat);
            } else if (replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                if (((TLRPC.TL_messageMediaPoll) replyMessageObject.messageOwner.media).poll.quiz) {
                    messageText = replaceWithLink(LocaleController.getString("ActionPinnedQuiz", R.string.ActionPinnedQuiz), "un1", fromUser != null ? fromUser : chat);
                } else {
                    messageText = replaceWithLink(LocaleController.getString("ActionPinnedPoll", R.string.ActionPinnedPoll), "un1", fromUser != null ? fromUser : chat);
                }
            } else if (replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                messageText = replaceWithLink(LocaleController.getString("ActionPinnedPhoto", R.string.ActionPinnedPhoto), "un1", fromUser != null ? fromUser : chat);
            } else if (replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                messageText = replaceWithLink(LocaleController.formatString("ActionPinnedGame", R.string.ActionPinnedGame, "\uD83C\uDFAE " + replyMessageObject.messageOwner.media.game.title), "un1", fromUser != null ? fromUser : chat);
                messageText = Emoji.replaceEmoji(messageText, Theme.chat_msgTextPaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
            } else if (replyMessageObject.messageText != null && replyMessageObject.messageText.length() > 0) {
                CharSequence mess = replyMessageObject.messageText;
                if (mess.length() > 20) {
                    mess = mess.subSequence(0, 20) + "...";
                }
                mess = Emoji.replaceEmoji(mess, Theme.chat_msgTextPaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
                MediaDataController.addTextStyleRuns(replyMessageObject, (Spannable) mess);
                messageText = replaceWithLink(AndroidUtilities.formatSpannable(LocaleController.getString("ActionPinnedText", R.string.ActionPinnedText), mess), "un1", fromUser != null ? fromUser : chat);
            } else {
                messageText = replaceWithLink(LocaleController.getString("ActionPinnedNoText", R.string.ActionPinnedNoText), "un1", fromUser != null ? fromUser : chat);
            }
        }
    }

    public static void updateReactions(TLRPC.Message message, TLRPC.TL_messageReactions reactions) {
        if (message == null || reactions == null) {
            return;
        }
        if (reactions.min && message.reactions != null) {
            for (int a = 0, N = message.reactions.results.size(); a < N; a++) {
                TLRPC.TL_reactionCount reaction = message.reactions.results.get(a);
                if (reaction.chosen) {
                    for (int b = 0, N2 = reactions.results.size(); b < N2; b++) {
                        TLRPC.TL_reactionCount newReaction = reactions.results.get(b);
                        if (reaction.reaction.equals(newReaction.reaction)) {
                            newReaction.chosen = true;
                            break;
                        }
                    }
                    break;
                }
            }
        }
        message.reactions = reactions;
        message.flags |= 1048576;
    }

    public boolean hasReactions() {
        return messageOwner.reactions != null && !messageOwner.reactions.results.isEmpty();
    }

    public static void updatePollResults(TLRPC.TL_messageMediaPoll media, TLRPC.PollResults results) {
        if (media == null || results == null) {
            return;
        }
        if ((results.flags & 2) != 0) {
            ArrayList<byte[]> chosen = null;
            byte[] correct = null;
            if (results.min && media.results.results != null) {
                for (int b = 0, N2 = media.results.results.size(); b < N2; b++) {
                    TLRPC.TL_pollAnswerVoters answerVoters = media.results.results.get(b);
                    if (answerVoters.chosen) {
                        if (chosen == null) {
                            chosen = new ArrayList<>();
                        }
                        chosen.add(answerVoters.option);
                    }
                    if (answerVoters.correct) {
                        correct = answerVoters.option;
                    }
                }
            }
            media.results.results = results.results;
            if (chosen != null || correct != null) {
                for (int b = 0, N2 = media.results.results.size(); b < N2; b++) {
                    TLRPC.TL_pollAnswerVoters answerVoters = media.results.results.get(b);
                    if (chosen != null) {
                        for (int a = 0, N = chosen.size(); a < N; a++) {
                            if (Arrays.equals(answerVoters.option, chosen.get(a))) {
                                answerVoters.chosen = true;
                                chosen.remove(a);
                                break;
                            }
                        }
                        if (chosen.isEmpty()) {
                            chosen = null;
                        }
                    }
                    if (correct != null && Arrays.equals(answerVoters.option, correct)) {
                        answerVoters.correct = true;
                        correct = null;
                    }
                    if (chosen == null && correct == null) {
                        break;
                    }
                }
            }
            media.results.flags |= 2;
        }
        if ((results.flags & 4) != 0) {
            media.results.total_voters = results.total_voters;
            media.results.flags |= 4;
        }
        if ((results.flags & 8) != 0) {
            media.results.recent_voters = results.recent_voters;
            media.results.flags |= 8;
        }
        if ((results.flags & 16) != 0) {
            media.results.solution = results.solution;
            media.results.solution_entities = results.solution_entities;
            media.results.flags |= 16;
        }
    }

    public boolean isPollClosed() {
        if (type != TYPE_POLL) {
            return false;
        }
        return ((TLRPC.TL_messageMediaPoll) messageOwner.media).poll.closed;
    }

    public boolean isQuiz() {
        if (type != TYPE_POLL) {
            return false;
        }
        return ((TLRPC.TL_messageMediaPoll) messageOwner.media).poll.quiz;
    }

    public boolean isPublicPoll() {
        if (type != TYPE_POLL) {
            return false;
        }
        return ((TLRPC.TL_messageMediaPoll) messageOwner.media).poll.public_voters;
    }

    public boolean isPoll() {
        return type == TYPE_POLL;
    }

    public boolean canUnvote() {
        if (type != TYPE_POLL) {
            return false;
        }
        TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) messageOwner.media;
        if (mediaPoll.results == null || mediaPoll.results.results.isEmpty() || mediaPoll.poll.quiz) {
            return false;
        }
        for (int a = 0, N = mediaPoll.results.results.size(); a < N; a++) {
            TLRPC.TL_pollAnswerVoters answer = mediaPoll.results.results.get(a);
            if (answer.chosen) {
                return true;
            }
        }
        return false;
    }

    public boolean isVoted() {
        if (type != TYPE_POLL) {
            return false;
        }
        TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) messageOwner.media;
        if (mediaPoll.results == null || mediaPoll.results.results.isEmpty()) {
            return false;
        }
        for (int a = 0, N = mediaPoll.results.results.size(); a < N; a++) {
            TLRPC.TL_pollAnswerVoters answer = mediaPoll.results.results.get(a);
            if (answer.chosen) {
                return true;
            }
        }
        return false;
    }

    public boolean isSponsored() {
        return sponsoredId != null;
    }

    public long getPollId() {
        if (type != TYPE_POLL) {
            return 0;
        }
        return ((TLRPC.TL_messageMediaPoll) messageOwner.media).poll.id;
    }

    private TLRPC.Photo getPhotoWithId(TLRPC.WebPage webPage, long id) {
        if (webPage == null || webPage.cached_page == null) {
            return null;
        }
        if (webPage.photo != null && webPage.photo.id == id) {
            return webPage.photo;
        }
        for (int a = 0; a < webPage.cached_page.photos.size(); a++) {
            TLRPC.Photo photo = webPage.cached_page.photos.get(a);
            if (photo.id == id) {
                return photo;
            }
        }
        return null;
    }

    private TLRPC.Document getDocumentWithId(TLRPC.WebPage webPage, long id) {
        if (webPage == null || webPage.cached_page == null) {
            return null;
        }
        if (webPage.document != null && webPage.document.id == id) {
            return webPage.document;
        }
        for (int a = 0; a < webPage.cached_page.documents.size(); a++) {
            TLRPC.Document document = webPage.cached_page.documents.get(a);
            if (document.id == id) {
                return document;
            }
        }
        return null;
    }

    public boolean isSupergroup() {
        if (localSupergroup) {
            return true;
        }
        if (cachedIsSupergroup != null) {
            return cachedIsSupergroup;
        }
        if (messageOwner.peer_id != null && messageOwner.peer_id.channel_id != 0) {
            TLRPC.Chat chat = getChat(null, null, messageOwner.peer_id.channel_id);
            if (chat != null) {
                return (cachedIsSupergroup = chat.megagroup);
            } else {
                return false;
            }
        } else {
            cachedIsSupergroup = false;
        }
        return false;
    }

    private MessageObject getMessageObjectForBlock(TLRPC.WebPage webPage, TLRPC.PageBlock pageBlock) {
        TLRPC.TL_message message = null;
        if (pageBlock instanceof TLRPC.TL_pageBlockPhoto) {
            TLRPC.TL_pageBlockPhoto pageBlockPhoto = (TLRPC.TL_pageBlockPhoto) pageBlock;
            TLRPC.Photo photo = getPhotoWithId(webPage, pageBlockPhoto.photo_id);
            if (photo == webPage.photo) {
                return this;
            }
            message = new TLRPC.TL_message();
            message.media = new TLRPC.TL_messageMediaPhoto();
            message.media.photo = photo;
        } else if (pageBlock instanceof TLRPC.TL_pageBlockVideo) {
            TLRPC.TL_pageBlockVideo pageBlockVideo = (TLRPC.TL_pageBlockVideo) pageBlock;
            TLRPC.Document document = getDocumentWithId(webPage, pageBlockVideo.video_id);
            if (document == webPage.document) {
                return this;
            }
            message = new TLRPC.TL_message();
            message.media = new TLRPC.TL_messageMediaDocument();
            message.media.document = getDocumentWithId(webPage, pageBlockVideo.video_id);
        }
        message.message = "";
        message.realId = getId();
        message.id = Utilities.random.nextInt();
        message.date = messageOwner.date;
        message.peer_id = messageOwner.peer_id;
        message.out = messageOwner.out;
        message.from_id = messageOwner.from_id;
        return new MessageObject(currentAccount, message, false, true);
    }

    public ArrayList<MessageObject> getWebPagePhotos(ArrayList<MessageObject> array, ArrayList<TLRPC.PageBlock> blocksToSearch) {
        ArrayList<MessageObject> messageObjects = array == null ? new ArrayList<>() : array;
        if (messageOwner.media == null || messageOwner.media.webpage == null) {
            return messageObjects;
        }
        TLRPC.WebPage webPage = messageOwner.media.webpage;
        if (webPage.cached_page == null) {
            return messageObjects;
        }
        ArrayList<TLRPC.PageBlock> blocks = blocksToSearch == null ? webPage.cached_page.blocks : blocksToSearch;
        for (int a = 0; a < blocks.size(); a++) {
            TLRPC.PageBlock block = blocks.get(a);
            if (block instanceof TLRPC.TL_pageBlockSlideshow) {
                TLRPC.TL_pageBlockSlideshow slideshow = (TLRPC.TL_pageBlockSlideshow) block;
                for (int b = 0; b < slideshow.items.size(); b++) {
                    messageObjects.add(getMessageObjectForBlock(webPage, slideshow.items.get(b)));
                }
            } else if (block instanceof TLRPC.TL_pageBlockCollage) {
                TLRPC.TL_pageBlockCollage slideshow = (TLRPC.TL_pageBlockCollage) block;
                for (int b = 0; b < slideshow.items.size(); b++) {
                    messageObjects.add(getMessageObjectForBlock(webPage, slideshow.items.get(b)));
                }
            }
        }
        return messageObjects;
    }

    public void createMessageSendInfo() {
        if (messageOwner.message != null && (messageOwner.id < 0 || isEditing()) && messageOwner.params != null) {
            String param;
            if ((param = messageOwner.params.get("ve")) != null && (isVideo() || isNewGif() || isRoundVideo())) {
                videoEditedInfo = new VideoEditedInfo();
                if (!videoEditedInfo.parseString(param)) {
                    videoEditedInfo = null;
                } else {
                    videoEditedInfo.roundVideo = isRoundVideo();
                }
            }
            if (messageOwner.send_state == MESSAGE_SEND_STATE_EDITING && (param = messageOwner.params.get("prevMedia")) != null) {
                SerializedData serializedData = new SerializedData(Base64.decode(param, Base64.DEFAULT));
                int constructor = serializedData.readInt32(false);
                previousMedia = TLRPC.MessageMedia.TLdeserialize(serializedData, constructor, false);
                previousMessage = serializedData.readString(false);
                previousAttachPath = serializedData.readString(false);
                int count = serializedData.readInt32(false);
                previousMessageEntities = new ArrayList<>(count);
                for (int a = 0; a < count; a++) {
                    constructor = serializedData.readInt32(false);
                    TLRPC.MessageEntity entity = TLRPC.MessageEntity.TLdeserialize(serializedData, constructor, false);
                    previousMessageEntities.add(entity);
                }
                serializedData.cleanup();
            }
        }
    }

    public void measureInlineBotButtons() {
        if (isRestrictedMessage) {
            return;
        }
        wantedBotKeyboardWidth = 0;
        if (messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup || messageOwner.reactions != null && !messageOwner.reactions.results.isEmpty()) {
            Theme.createCommonMessageResources();
            if (botButtonsLayout == null) {
                botButtonsLayout = new StringBuilder();
            } else {
                botButtonsLayout.setLength(0);
            }
        }

        if (messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) {
            for (int a = 0; a < messageOwner.reply_markup.rows.size(); a++) {
                TLRPC.TL_keyboardButtonRow row = messageOwner.reply_markup.rows.get(a);
                int maxButtonSize = 0;
                int size = row.buttons.size();
                for (int b = 0; b < size; b++) {
                    TLRPC.KeyboardButton button = row.buttons.get(b);
                    botButtonsLayout.append(a).append(b);
                    CharSequence text;
                    if (button instanceof TLRPC.TL_keyboardButtonBuy && (messageOwner.media.flags & 4) != 0) {
                        text = LocaleController.getString("PaymentReceipt", R.string.PaymentReceipt);
                    } else {
                        text = Emoji.replaceEmoji(button.text, Theme.chat_msgBotButtonPaint.getFontMetricsInt(), AndroidUtilities.dp(15), false);
                    }
                    StaticLayout staticLayout = new StaticLayout(text, Theme.chat_msgBotButtonPaint, AndroidUtilities.dp(2000), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    if (staticLayout.getLineCount() > 0) {
                        float width = staticLayout.getLineWidth(0);
                        float left = staticLayout.getLineLeft(0);
                        if (left < width) {
                            width -= left;
                        }
                        maxButtonSize = Math.max(maxButtonSize, (int) Math.ceil(width) + AndroidUtilities.dp(4));
                    }
                }
                wantedBotKeyboardWidth = Math.max(wantedBotKeyboardWidth, (maxButtonSize + AndroidUtilities.dp(12)) * size + AndroidUtilities.dp(5) * (size - 1));
            }
        } else if (messageOwner.reactions != null) {
            int size = messageOwner.reactions.results.size();
            for (int a = 0; a < size; a++) {
                TLRPC.TL_reactionCount reactionCount = messageOwner.reactions.results.get(a);
                int maxButtonSize = 0;
                botButtonsLayout.append(0).append(a);
                CharSequence text = Emoji.replaceEmoji(String.format("%d %s", reactionCount.count, reactionCount.reaction), Theme.chat_msgBotButtonPaint.getFontMetricsInt(), AndroidUtilities.dp(15), false);
                StaticLayout staticLayout = new StaticLayout(text, Theme.chat_msgBotButtonPaint, AndroidUtilities.dp(2000), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                if (staticLayout.getLineCount() > 0) {
                    float width = staticLayout.getLineWidth(0);
                    float left = staticLayout.getLineLeft(0);
                    if (left < width) {
                        width -= left;
                    }
                    maxButtonSize = Math.max(maxButtonSize, (int) Math.ceil(width) + AndroidUtilities.dp(4));
                }
                wantedBotKeyboardWidth = Math.max(wantedBotKeyboardWidth, (maxButtonSize + AndroidUtilities.dp(12)) * size + AndroidUtilities.dp(5) * (size - 1));
            }
        }
    }

    public boolean isVideoAvatar() {
        return messageOwner.action != null && messageOwner.action.photo != null && !messageOwner.action.photo.video_sizes.isEmpty();
    }

    public boolean isFcmMessage() {
        return localType != 0;
    }

    private TLRPC.User getUser(AbstractMap<Long, TLRPC.User> users, LongSparseArray<TLRPC.User> sUsers, long userId) {
        TLRPC.User user = null;
        if (users != null) {
            user = users.get(userId);
        } else if (sUsers != null) {
            user = sUsers.get(userId);
        }
        if (user == null) {
            user = MessagesController.getInstance(currentAccount).getUser(userId);
        }
        return user;
    }

    private TLRPC.Chat getChat(AbstractMap<Long, TLRPC.Chat> chats, LongSparseArray<TLRPC.Chat> sChats, long chatId) {
        TLRPC.Chat chat = null;
        if (chats != null) {
            chat = chats.get(chatId);
        } else if (sChats != null) {
            chat = sChats.get(chatId);
        }
        if (chat == null) {
            chat = MessagesController.getInstance(currentAccount).getChat(chatId);
        }
        return chat;
    }

    private void updateMessageText(AbstractMap<Long, TLRPC.User> users, AbstractMap<Long, TLRPC.Chat> chats, LongSparseArray<TLRPC.User> sUsers, LongSparseArray<TLRPC.Chat> sChats) {
        TLRPC.User fromUser = null;
        TLRPC.Chat fromChat = null;
        if (messageOwner.from_id instanceof TLRPC.TL_peerUser) {
            fromUser = getUser(users, sUsers, messageOwner.from_id.user_id);
        } else if (messageOwner.from_id instanceof TLRPC.TL_peerChannel) {
            fromChat = getChat(chats, sChats, messageOwner.from_id.channel_id);
        }
        TLObject fromObject = fromUser != null ? fromUser : fromChat;

        if (messageOwner instanceof TLRPC.TL_messageService) {
            if (messageOwner.action != null) {
                if (messageOwner.action instanceof TLRPC.TL_messageActionGroupCallScheduled) {
                    TLRPC.TL_messageActionGroupCallScheduled action = (TLRPC.TL_messageActionGroupCallScheduled) messageOwner.action;
                    if (messageOwner.peer_id instanceof TLRPC.TL_peerChat || isSupergroup()) {
                        messageText = LocaleController.formatString("ActionGroupCallScheduled", R.string.ActionGroupCallScheduled, LocaleController.formatStartsTime(action.schedule_date, 3, false));
                    } else {
                        messageText = LocaleController.formatString("ActionChannelCallScheduled", R.string.ActionChannelCallScheduled, LocaleController.formatStartsTime(action.schedule_date, 3, false));
                    }
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionGroupCall) {
                    if (messageOwner.action.duration != 0) {
                        String time;
                        int days = messageOwner.action.duration / (3600 * 24);
                        if (days > 0) {
                            time = LocaleController.formatPluralString("Days", days);
                        } else {
                            int hours = messageOwner.action.duration / 3600;
                            if (hours > 0) {
                                time = LocaleController.formatPluralString("Hours", hours);
                            } else {
                                int minutes = messageOwner.action.duration / 60;
                                if (minutes > 0) {
                                    time = LocaleController.formatPluralString("Minutes", minutes);
                                } else {
                                    time = LocaleController.formatPluralString("Seconds", messageOwner.action.duration);
                                }
                            }
                        }

                        if (messageOwner.peer_id instanceof TLRPC.TL_peerChat || isSupergroup()) {
                            if (isOut()) {
                                messageText = LocaleController.formatString("ActionGroupCallEndedByYou", R.string.ActionGroupCallEndedByYou, time);
                            } else {
                                messageText = replaceWithLink(LocaleController.formatString("ActionGroupCallEndedBy", R.string.ActionGroupCallEndedBy, time), "un1", fromObject);
                            }
                        } else {
                            messageText = LocaleController.formatString("ActionChannelCallEnded", R.string.ActionChannelCallEnded, time);
                        }
                    } else {
                        if (messageOwner.peer_id instanceof TLRPC.TL_peerChat || isSupergroup()) {
                            if (isOut()) {
                                messageText = LocaleController.getString("ActionGroupCallStartedByYou", R.string.ActionGroupCallStartedByYou);
                            } else {
                                messageText = replaceWithLink(LocaleController.getString("ActionGroupCallStarted", R.string.ActionGroupCallStarted), "un1", fromObject);
                            }
                        } else {
                            messageText = LocaleController.getString("ActionChannelCallJustStarted", R.string.ActionChannelCallJustStarted);
                        }
                    }
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionInviteToGroupCall) {
                    long singleUserId = messageOwner.action.user_id;
                    if (singleUserId == 0 && messageOwner.action.users.size() == 1) {
                        singleUserId = messageOwner.action.users.get(0);
                    }
                    if (singleUserId != 0) {
                        TLRPC.User whoUser = getUser(users, sUsers, singleUserId);

                        if (isOut()) {
                            messageText = replaceWithLink(LocaleController.getString("ActionGroupCallYouInvited", R.string.ActionGroupCallYouInvited), "un2", whoUser);
                        } else if (singleUserId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                            messageText = replaceWithLink(LocaleController.getString("ActionGroupCallInvitedYou", R.string.ActionGroupCallInvitedYou), "un1", fromObject);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionGroupCallInvited", R.string.ActionGroupCallInvited), "un2", whoUser);
                            messageText = replaceWithLink(messageText, "un1", fromObject);
                        }
                    } else {
                        if (isOut()) {
                            messageText = replaceWithLink(LocaleController.getString("ActionGroupCallYouInvited", R.string.ActionGroupCallYouInvited), "un2", messageOwner.action.users, users, sUsers);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionGroupCallInvited", R.string.ActionGroupCallInvited), "un2", messageOwner.action.users, users, sUsers);
                            messageText = replaceWithLink(messageText, "un1", fromObject);
                        }
                    }
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionGeoProximityReached) {
                    TLRPC.TL_messageActionGeoProximityReached action = (TLRPC.TL_messageActionGeoProximityReached) messageOwner.action;
                    long fromId = getPeerId(action.from_id);
                    TLObject from;
                    if (fromId > 0) {
                        from = getUser(users, sUsers, fromId);
                    } else {
                        from = getChat(chats, sChats, -fromId);
                    }
                    long toId = getPeerId(action.to_id);
                    long selfUserId = UserConfig.getInstance(currentAccount).getClientUserId();
                    if (toId == selfUserId) {
                        messageText = replaceWithLink(LocaleController.formatString("ActionUserWithinRadius", R.string.ActionUserWithinRadius, LocaleController.formatDistance(action.distance, 2)), "un1", from);
                    } else {
                        TLObject to;
                        if (toId > 0) {
                            to = getUser(users, sUsers, toId);
                        } else {
                            to = getChat(chats, sChats, -toId);
                        }
                        if (fromId == selfUserId) {
                            messageText = replaceWithLink(LocaleController.formatString("ActionUserWithinYouRadius", R.string.ActionUserWithinYouRadius, LocaleController.formatDistance(action.distance, 2)), "un1", to);
                        } else {
                            messageText = replaceWithLink(LocaleController.formatString("ActionUserWithinOtherRadius", R.string.ActionUserWithinOtherRadius, LocaleController.formatDistance(action.distance, 2)), "un2", to);
                            messageText = replaceWithLink(messageText, "un1", from);
                        }
                    }
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionCustomAction) {
                    messageText = messageOwner.action.message;
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionChatCreate) {
                    if (isOut()) {
                        messageText = LocaleController.getString("ActionYouCreateGroup", R.string.ActionYouCreateGroup);
                    } else {
                        messageText = replaceWithLink(LocaleController.getString("ActionCreateGroup", R.string.ActionCreateGroup), "un1", fromObject);
                    }
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                    if (isFromUser() && messageOwner.action.user_id == messageOwner.from_id.user_id) {
                        if (isOut()) {
                            messageText = LocaleController.getString("ActionYouLeftUser", R.string.ActionYouLeftUser);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionLeftUser", R.string.ActionLeftUser), "un1", fromObject);
                        }
                    } else {
                        TLRPC.User whoUser = getUser(users, sUsers, messageOwner.action.user_id);
                        if (isOut()) {
                            messageText = replaceWithLink(LocaleController.getString("ActionYouKickUser", R.string.ActionYouKickUser), "un2", whoUser);
                        } else if (messageOwner.action.user_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                            messageText = replaceWithLink(LocaleController.getString("ActionKickUserYou", R.string.ActionKickUserYou), "un1", fromObject);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionKickUser", R.string.ActionKickUser), "un2", whoUser);
                            messageText = replaceWithLink(messageText, "un1", fromObject);
                        }
                    }
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionChatAddUser) {
                    long singleUserId = messageOwner.action.user_id;
                    if (singleUserId == 0 && messageOwner.action.users.size() == 1) {
                        singleUserId = messageOwner.action.users.get(0);
                    }
                    if (singleUserId != 0) {
                        TLRPC.User whoUser = getUser(users, sUsers, singleUserId);
                        TLRPC.Chat chat = null;
                        if (messageOwner.peer_id.channel_id != 0) {
                            chat = getChat(chats, sChats, messageOwner.peer_id.channel_id);
                        }
                        if (messageOwner.from_id != null && singleUserId == messageOwner.from_id.user_id) {
                            if (ChatObject.isChannel(chat) && !chat.megagroup) {
                                messageText = LocaleController.getString("ChannelJoined", R.string.ChannelJoined);
                            } else {
                                if (messageOwner.peer_id.channel_id != 0) {
                                    if (singleUserId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                                        messageText = LocaleController.getString("ChannelMegaJoined", R.string.ChannelMegaJoined);
                                    } else {
                                        messageText = replaceWithLink(LocaleController.getString("ActionAddUserSelfMega", R.string.ActionAddUserSelfMega), "un1", fromObject);
                                    }
                                } else if (isOut()) {
                                    messageText = LocaleController.getString("ActionAddUserSelfYou", R.string.ActionAddUserSelfYou);
                                } else {
                                    messageText = replaceWithLink(LocaleController.getString("ActionAddUserSelf", R.string.ActionAddUserSelf), "un1", fromObject);
                                }
                            }
                        } else {
                            if (isOut()) {
                                messageText = replaceWithLink(LocaleController.getString("ActionYouAddUser", R.string.ActionYouAddUser), "un2", whoUser);
                            } else if (singleUserId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                                if (messageOwner.peer_id.channel_id != 0) {
                                    if (chat != null && chat.megagroup) {
                                        messageText = replaceWithLink(LocaleController.getString("MegaAddedBy", R.string.MegaAddedBy), "un1", fromObject);
                                    } else {
                                        messageText = replaceWithLink(LocaleController.getString("ChannelAddedBy", R.string.ChannelAddedBy), "un1", fromObject);
                                    }
                                } else {
                                    messageText = replaceWithLink(LocaleController.getString("ActionAddUserYou", R.string.ActionAddUserYou), "un1", fromObject);
                                }
                            } else {
                                messageText = replaceWithLink(LocaleController.getString("ActionAddUser", R.string.ActionAddUser), "un2", whoUser);
                                messageText = replaceWithLink(messageText, "un1", fromObject);
                            }
                        }
                    } else {
                        if (isOut()) {
                            messageText = replaceWithLink(LocaleController.getString("ActionYouAddUser", R.string.ActionYouAddUser), "un2", messageOwner.action.users, users, sUsers);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionAddUser", R.string.ActionAddUser), "un2", messageOwner.action.users, users, sUsers);
                            messageText = replaceWithLink(messageText, "un1", fromObject);
                        }
                    }
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByLink) {
                    if (isOut()) {
                        messageText = LocaleController.getString("ActionInviteYou", R.string.ActionInviteYou);
                    } else {
                        messageText = replaceWithLink(LocaleController.getString("ActionInviteUser", R.string.ActionInviteUser), "un1", fromObject);
                    }
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionChatEditPhoto) {
                    TLRPC.Chat chat = messageOwner.peer_id != null && messageOwner.peer_id.channel_id != 0 ? getChat(chats, sChats, messageOwner.peer_id.channel_id) : null;
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        if (isVideoAvatar()) {
                            messageText = LocaleController.getString("ActionChannelChangedVideo", R.string.ActionChannelChangedVideo);
                        } else {
                            messageText = LocaleController.getString("ActionChannelChangedPhoto", R.string.ActionChannelChangedPhoto);
                        }
                    } else {
                        if (isOut()) {
                            if (isVideoAvatar()) {
                                messageText = LocaleController.getString("ActionYouChangedVideo", R.string.ActionYouChangedVideo);
                            } else {
                                messageText = LocaleController.getString("ActionYouChangedPhoto", R.string.ActionYouChangedPhoto);
                            }
                        } else {
                            if (isVideoAvatar()) {
                                messageText = replaceWithLink(LocaleController.getString("ActionChangedVideo", R.string.ActionChangedVideo), "un1", fromObject);
                            } else {
                                messageText = replaceWithLink(LocaleController.getString("ActionChangedPhoto", R.string.ActionChangedPhoto), "un1", fromObject);
                            }
                        }
                    }
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionChatEditTitle) {
                    TLRPC.Chat chat = messageOwner.peer_id != null && messageOwner.peer_id.channel_id != 0 ? getChat(chats, sChats, messageOwner.peer_id.channel_id) : null;
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        messageText = LocaleController.getString("ActionChannelChangedTitle", R.string.ActionChannelChangedTitle).replace("un2", messageOwner.action.title);
                    } else {
                        if (isOut()) {
                            messageText = LocaleController.getString("ActionYouChangedTitle", R.string.ActionYouChangedTitle).replace("un2", messageOwner.action.title);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionChangedTitle", R.string.ActionChangedTitle).replace("un2", messageOwner.action.title), "un1", fromObject);
                        }
                    }
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
                    TLRPC.Chat chat = messageOwner.peer_id != null && messageOwner.peer_id.channel_id != 0 ? getChat(chats, sChats, messageOwner.peer_id.channel_id) : null;
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        messageText = LocaleController.getString("ActionChannelRemovedPhoto", R.string.ActionChannelRemovedPhoto);
                    } else {
                        if (isOut()) {
                            messageText = LocaleController.getString("ActionYouRemovedPhoto", R.string.ActionYouRemovedPhoto);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionRemovedPhoto", R.string.ActionRemovedPhoto), "un1", fromObject);
                        }
                    }
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionTTLChange) {
                    if (messageOwner.action.ttl != 0) {
                        if (isOut()) {
                            messageText = LocaleController.formatString("MessageLifetimeChangedOutgoing", R.string.MessageLifetimeChangedOutgoing, LocaleController.formatTTLString(messageOwner.action.ttl));
                        } else {
                            messageText = LocaleController.formatString("MessageLifetimeChanged", R.string.MessageLifetimeChanged, UserObject.getFirstName(fromUser), LocaleController.formatTTLString(messageOwner.action.ttl));
                        }
                    } else {
                        if (isOut()) {
                            messageText = LocaleController.getString("MessageLifetimeYouRemoved", R.string.MessageLifetimeYouRemoved);
                        } else {
                            messageText = LocaleController.formatString("MessageLifetimeRemoved", R.string.MessageLifetimeRemoved, UserObject.getFirstName(fromUser));
                        }
                    }
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionSetMessagesTTL) {
                    TLRPC.TL_messageActionSetMessagesTTL action = (TLRPC.TL_messageActionSetMessagesTTL) messageOwner.action;
                    TLRPC.Chat chat = messageOwner.peer_id != null && messageOwner.peer_id.channel_id != 0 ? getChat(chats, sChats, messageOwner.peer_id.channel_id) : null;
                    if (chat != null && !chat.megagroup) {
                        if (action.period != 0) {
                            messageText = LocaleController.formatString("ActionTTLChannelChanged", R.string.ActionTTLChannelChanged, LocaleController.formatTTLString(action.period));
                        } else {
                            messageText = LocaleController.getString("ActionTTLChannelDisabled", R.string.ActionTTLChannelDisabled);
                        }
                    } else if (action.period != 0) {
                        if (isOut()) {
                            messageText = LocaleController.formatString("ActionTTLYouChanged", R.string.ActionTTLYouChanged, LocaleController.formatTTLString(action.period));
                        } else {
                            messageText = replaceWithLink(LocaleController.formatString("ActionTTLChanged", R.string.ActionTTLChanged, LocaleController.formatTTLString(action.period)), "un1", fromObject);
                        }
                    } else {
                        if (isOut()) {
                            messageText = LocaleController.getString("ActionTTLYouDisabled", R.string.ActionTTLYouDisabled);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionTTLDisabled", R.string.ActionTTLDisabled), "un1", fromObject);
                        }
                    }
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                    String date;
                    long time = ((long) messageOwner.date) * 1000;
                    if (LocaleController.getInstance().formatterDay != null && LocaleController.getInstance().formatterYear != null) {
                        date = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().formatterYear.format(time), LocaleController.getInstance().formatterDay.format(time));
                    } else {
                        date = "" + messageOwner.date;
                    }
                    TLRPC.User to_user = UserConfig.getInstance(currentAccount).getCurrentUser();
                    if (to_user == null) {
                        to_user = getUser(users, sUsers, messageOwner.peer_id.user_id);
                    }
                    String name = to_user != null ? UserObject.getFirstName(to_user) : "";
                    messageText = LocaleController.formatString("NotificationUnrecognizedDevice", R.string.NotificationUnrecognizedDevice, name, date, messageOwner.action.title, messageOwner.action.address);
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionUserJoined || messageOwner.action instanceof TLRPC.TL_messageActionContactSignUp) {
                    messageText = LocaleController.formatString("NotificationContactJoined", R.string.NotificationContactJoined, UserObject.getUserName(fromUser));
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                    messageText = LocaleController.formatString("NotificationContactNewPhoto", R.string.NotificationContactNewPhoto, UserObject.getUserName(fromUser));
                } else if (messageOwner.action instanceof TLRPC.TL_messageEncryptedAction) {
                    if (messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages) {
                        if (isOut()) {
                            messageText = LocaleController.formatString("ActionTakeScreenshootYou", R.string.ActionTakeScreenshootYou);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionTakeScreenshoot", R.string.ActionTakeScreenshoot), "un1", fromObject);
                        }
                    } else if (messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                        TLRPC.TL_decryptedMessageActionSetMessageTTL action = (TLRPC.TL_decryptedMessageActionSetMessageTTL) messageOwner.action.encryptedAction;
                        if (action.ttl_seconds != 0) {
                            if (isOut()) {
                                messageText = LocaleController.formatString("MessageLifetimeChangedOutgoing", R.string.MessageLifetimeChangedOutgoing, LocaleController.formatTTLString(action.ttl_seconds));
                            } else {
                                messageText = LocaleController.formatString("MessageLifetimeChanged", R.string.MessageLifetimeChanged, UserObject.getFirstName(fromUser), LocaleController.formatTTLString(action.ttl_seconds));
                            }
                        } else {
                            if (isOut()) {
                                messageText = LocaleController.getString("MessageLifetimeYouRemoved", R.string.MessageLifetimeYouRemoved);
                            } else {
                                messageText = LocaleController.formatString("MessageLifetimeRemoved", R.string.MessageLifetimeRemoved, UserObject.getFirstName(fromUser));
                            }
                        }
                    }
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionScreenshotTaken) {
                    if (isOut()) {
                        messageText = LocaleController.formatString("ActionTakeScreenshootYou", R.string.ActionTakeScreenshootYou);
                    } else {
                        messageText = replaceWithLink(LocaleController.getString("ActionTakeScreenshoot", R.string.ActionTakeScreenshoot), "un1", fromObject);
                    }
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionCreatedBroadcastList) {
                    messageText = LocaleController.formatString("YouCreatedBroadcastList", R.string.YouCreatedBroadcastList);
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionChannelCreate) {
                    TLRPC.Chat chat = messageOwner.peer_id != null && messageOwner.peer_id.channel_id != 0 ? getChat(chats, sChats, messageOwner.peer_id.channel_id) : null;
                    if (ChatObject.isChannel(chat) && chat.megagroup) {
                        messageText = LocaleController.getString("ActionCreateMega", R.string.ActionCreateMega);
                    } else {
                        messageText = LocaleController.getString("ActionCreateChannel", R.string.ActionCreateChannel);
                    }
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionChatMigrateTo) {
                    messageText = LocaleController.getString("ActionMigrateFromGroup", R.string.ActionMigrateFromGroup);
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionChannelMigrateFrom) {
                    messageText = LocaleController.getString("ActionMigrateFromGroup", R.string.ActionMigrateFromGroup);
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionPinMessage) {
                    TLRPC.Chat chat;
                    if (fromUser == null) {
                        chat = getChat(chats, sChats, messageOwner.peer_id.channel_id);
                    } else {
                        chat = null;
                    }
                    generatePinMessageText(fromUser, chat);
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionHistoryClear) {
                    messageText = LocaleController.getString("HistoryCleared", R.string.HistoryCleared);
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionGameScore) {
                    generateGameMessageText(fromUser);
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall) {
                    TLRPC.TL_messageActionPhoneCall call = (TLRPC.TL_messageActionPhoneCall) messageOwner.action;
                    boolean isMissed = call.reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed;
                    if (isFromUser() && messageOwner.from_id.user_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                        if (isMissed) {
                            if (call.video) {
                                messageText = LocaleController.getString("CallMessageVideoOutgoingMissed", R.string.CallMessageVideoOutgoingMissed);
                            } else {
                                messageText = LocaleController.getString("CallMessageOutgoingMissed", R.string.CallMessageOutgoingMissed);
                            }
                        } else {
                            if (call.video) {
                                messageText = LocaleController.getString("CallMessageVideoOutgoing", R.string.CallMessageVideoOutgoing);
                            } else {
                                messageText = LocaleController.getString("CallMessageOutgoing", R.string.CallMessageOutgoing);
                            }
                        }
                    } else {
                        if (isMissed) {
                            if (call.video) {
                                messageText = LocaleController.getString("CallMessageVideoIncomingMissed", R.string.CallMessageVideoIncomingMissed);
                            } else {
                                messageText = LocaleController.getString("CallMessageIncomingMissed", R.string.CallMessageIncomingMissed);
                            }
                        } else if (call.reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy) {
                            if (call.video) {
                                messageText = LocaleController.getString("CallMessageVideoIncomingDeclined", R.string.CallMessageVideoIncomingDeclined);
                            } else {
                                messageText = LocaleController.getString("CallMessageIncomingDeclined", R.string.CallMessageIncomingDeclined);
                            }
                        } else {
                            if (call.video) {
                                messageText = LocaleController.getString("CallMessageVideoIncoming", R.string.CallMessageVideoIncoming);
                            } else {
                                messageText = LocaleController.getString("CallMessageIncoming", R.string.CallMessageIncoming);
                            }
                        }
                    }
                    if (call.duration > 0) {
                        String duration = LocaleController.formatCallDuration(call.duration);
                        messageText = LocaleController.formatString("CallMessageWithDuration", R.string.CallMessageWithDuration, messageText, duration);
                        String _messageText = messageText.toString();
                        int start = _messageText.indexOf(duration);
                        if (start != -1) {
                            SpannableString sp = new SpannableString(messageText);
                            int end = start + duration.length();
                            if (start > 0 && _messageText.charAt(start - 1) == '(') {
                                start--;
                            }
                            if (end < _messageText.length() && _messageText.charAt(end) == ')') {
                                end++;
                            }
                            sp.setSpan(new TypefaceSpan(Typeface.DEFAULT), start, end, 0);
                            messageText = sp;
                        }
                    }
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionPaymentSent) {
                    TLRPC.User user = getUser(users, sUsers, getDialogId());
                    generatePaymentSentMessageText(user);
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionBotAllowed) {
                    String domain = ((TLRPC.TL_messageActionBotAllowed) messageOwner.action).domain;
                    String text = LocaleController.getString("ActionBotAllowed", R.string.ActionBotAllowed);
                    int start = text.indexOf("%1$s");
                    SpannableString str = new SpannableString(String.format(text, domain));
                    if (start >= 0) {
                        str.setSpan(new URLSpanNoUnderlineBold("http://" + domain), start, start + domain.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    messageText = str;
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionSecureValuesSent) {
                    TLRPC.TL_messageActionSecureValuesSent valuesSent = (TLRPC.TL_messageActionSecureValuesSent) messageOwner.action;
                    StringBuilder str = new StringBuilder();
                    for (int a = 0, size = valuesSent.types.size(); a < size; a++) {
                        TLRPC.SecureValueType type = valuesSent.types.get(a);
                        if (str.length() > 0) {
                            str.append(", ");
                        }
                        if (type instanceof TLRPC.TL_secureValueTypePhone) {
                            str.append(LocaleController.getString("ActionBotDocumentPhone", R.string.ActionBotDocumentPhone));
                        } else if (type instanceof TLRPC.TL_secureValueTypeEmail) {
                            str.append(LocaleController.getString("ActionBotDocumentEmail", R.string.ActionBotDocumentEmail));
                        } else if (type instanceof TLRPC.TL_secureValueTypeAddress) {
                            str.append(LocaleController.getString("ActionBotDocumentAddress", R.string.ActionBotDocumentAddress));
                        } else if (type instanceof TLRPC.TL_secureValueTypePersonalDetails) {
                            str.append(LocaleController.getString("ActionBotDocumentIdentity", R.string.ActionBotDocumentIdentity));
                        } else if (type instanceof TLRPC.TL_secureValueTypePassport) {
                            str.append(LocaleController.getString("ActionBotDocumentPassport", R.string.ActionBotDocumentPassport));
                        } else if (type instanceof TLRPC.TL_secureValueTypeDriverLicense) {
                            str.append(LocaleController.getString("ActionBotDocumentDriverLicence", R.string.ActionBotDocumentDriverLicence));
                        } else if (type instanceof TLRPC.TL_secureValueTypeIdentityCard) {
                            str.append(LocaleController.getString("ActionBotDocumentIdentityCard", R.string.ActionBotDocumentIdentityCard));
                        } else if (type instanceof TLRPC.TL_secureValueTypeUtilityBill) {
                            str.append(LocaleController.getString("ActionBotDocumentUtilityBill", R.string.ActionBotDocumentUtilityBill));
                        } else if (type instanceof TLRPC.TL_secureValueTypeBankStatement) {
                            str.append(LocaleController.getString("ActionBotDocumentBankStatement", R.string.ActionBotDocumentBankStatement));
                        } else if (type instanceof TLRPC.TL_secureValueTypeRentalAgreement) {
                            str.append(LocaleController.getString("ActionBotDocumentRentalAgreement", R.string.ActionBotDocumentRentalAgreement));
                        } else if (type instanceof TLRPC.TL_secureValueTypeInternalPassport) {
                            str.append(LocaleController.getString("ActionBotDocumentInternalPassport", R.string.ActionBotDocumentInternalPassport));
                        } else if (type instanceof TLRPC.TL_secureValueTypePassportRegistration) {
                            str.append(LocaleController.getString("ActionBotDocumentPassportRegistration", R.string.ActionBotDocumentPassportRegistration));
                        } else if (type instanceof TLRPC.TL_secureValueTypeTemporaryRegistration) {
                            str.append(LocaleController.getString("ActionBotDocumentTemporaryRegistration", R.string.ActionBotDocumentTemporaryRegistration));
                        }
                    }
                    TLRPC.User user = null;
                    if (messageOwner.peer_id != null) {
                        user = getUser(users, sUsers, messageOwner.peer_id.user_id);
                    }
                    messageText = LocaleController.formatString("ActionBotDocuments", R.string.ActionBotDocuments, UserObject.getFirstName(user), str.toString());
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionSetChatTheme) {
                    String emoticon = ((TLRPC.TL_messageActionSetChatTheme) messageOwner.action).emoticon;
                    String userName = UserObject.getFirstName(fromUser);
                    boolean isUserSelf = UserObject.isUserSelf(fromUser);
                    if (TextUtils.isEmpty(emoticon)) {
                        messageText = isUserSelf
                                ? LocaleController.formatString("ChatThemeDisabledYou", R.string.ChatThemeDisabledYou)
                                : LocaleController.formatString("ChatThemeDisabled", R.string.ChatThemeDisabled, userName, emoticon);
                    } else {
                        messageText = isUserSelf
                                ? LocaleController.formatString("ChatThemeChangedYou", R.string.ChatThemeChangedYou, emoticon)
                                : LocaleController.formatString("ChatThemeChangedTo", R.string.ChatThemeChangedTo, userName, emoticon);
                    }
                } else if (messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByRequest) {
                    if (UserObject.isUserSelf(fromUser)) {
                        boolean isChannel = ChatObject.isChannelAndNotMegaGroup(messageOwner.peer_id.channel_id, currentAccount);
                        messageText = isChannel
                                ? LocaleController.getString("RequestToJoinChannelApproved", R.string.RequestToJoinChannelApproved)
                                : LocaleController.getString("RequestToJoinGroupApproved", R.string.RequestToJoinGroupApproved);
                    } else {
                        messageText = replaceWithLink(LocaleController.getString("UserAcceptedToGroupAction", R.string.UserAcceptedToGroupAction), "un1", fromObject);
                    }
                }
            }
        } else {
            isRestrictedMessage = false;
            String restrictionReason = MessagesController.getRestrictionReason(messageOwner.restriction_reason);
            if (!TextUtils.isEmpty(restrictionReason)) {
                messageText = restrictionReason;
                isRestrictedMessage = true;
            } else if (!isMediaEmpty()) {
                if (messageOwner.media instanceof TLRPC.TL_messageMediaDice) {
                    messageText = getDiceEmoji();
                } else if (messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                    if (((TLRPC.TL_messageMediaPoll) messageOwner.media).poll.quiz) {
                        messageText = LocaleController.getString("QuizPoll", R.string.QuizPoll);
                    } else {
                        messageText = LocaleController.getString("Poll", R.string.Poll);
                    }
                } else if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                    if (messageOwner.media.ttl_seconds != 0 && !(messageOwner instanceof TLRPC.TL_message_secret)) {
                        messageText = LocaleController.getString("AttachDestructingPhoto", R.string.AttachDestructingPhoto);
                    } else {
                        messageText = LocaleController.getString("AttachPhoto", R.string.AttachPhoto);
                    }
                } else if (isVideo() || messageOwner.media instanceof TLRPC.TL_messageMediaDocument && getDocument() instanceof TLRPC.TL_documentEmpty && messageOwner.media.ttl_seconds != 0) {
                    if (messageOwner.media.ttl_seconds != 0 && !(messageOwner instanceof TLRPC.TL_message_secret)) {
                        messageText = LocaleController.getString("AttachDestructingVideo", R.string.AttachDestructingVideo);
                    } else {
                        messageText = LocaleController.getString("AttachVideo", R.string.AttachVideo);
                    }
                } else if (isVoice()) {
                    messageText = LocaleController.getString("AttachAudio", R.string.AttachAudio);
                } else if (isRoundVideo()) {
                    messageText = LocaleController.getString("AttachRound", R.string.AttachRound);
                } else if (messageOwner.media instanceof TLRPC.TL_messageMediaGeo || messageOwner.media instanceof TLRPC.TL_messageMediaVenue) {
                    messageText = LocaleController.getString("AttachLocation", R.string.AttachLocation);
                } else if (messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                    messageText = LocaleController.getString("AttachLiveLocation", R.string.AttachLiveLocation);
                } else if (messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                    messageText = LocaleController.getString("AttachContact", R.string.AttachContact);
                    if (!TextUtils.isEmpty(messageOwner.media.vcard)) {
                        vCardData = VCardData.parse(messageOwner.media.vcard);
                    }
                } else if (messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                    messageText = messageOwner.message;
                } else if (messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) {
                    messageText = messageOwner.media.description;
                } else if (messageOwner.media instanceof TLRPC.TL_messageMediaUnsupported) {
                    messageText = LocaleController.getString("UnsupportedMedia", R.string.UnsupportedMedia);
                } else if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                    if (isSticker() || isAnimatedStickerDocument(getDocument(), true)) {
                        String sch = getStickerChar();
                        if (sch != null && sch.length() > 0) {
                            messageText = String.format("%s %s", sch, LocaleController.getString("AttachSticker", R.string.AttachSticker));
                        } else {
                            messageText = LocaleController.getString("AttachSticker", R.string.AttachSticker);
                        }
                    } else if (isMusic()) {
                        messageText = LocaleController.getString("AttachMusic", R.string.AttachMusic);
                    } else if (isGif()) {
                        messageText = LocaleController.getString("AttachGif", R.string.AttachGif);
                    } else {
                        String name = FileLoader.getDocumentFileName(getDocument());
                        if (!TextUtils.isEmpty(name)) {
                            messageText = name;
                        } else {
                            messageText = LocaleController.getString("AttachDocument", R.string.AttachDocument);
                        }
                    }
                }
            } else {
                if (messageOwner.message != null) {
                    try {
                        if (messageOwner.message.length() > 200) {
                            messageText = AndroidUtilities.BAD_CHARS_MESSAGE_LONG_PATTERN.matcher(messageOwner.message).replaceAll("\u200C");
                        } else {
                            messageText = AndroidUtilities.BAD_CHARS_MESSAGE_PATTERN.matcher(messageOwner.message).replaceAll("\u200C");
                        }
                    } catch (Throwable e) {
                        messageText = messageOwner.message;
                    }
                } else {
                    messageText = messageOwner.message;
                }
            }
        }

        if (messageText == null) {
            messageText = "";
        }
    }

    public void setType() {
        int oldType = type;
        type = 1000;
        isRoundVideoCached = 0;
        if (messageOwner instanceof TLRPC.TL_message || messageOwner instanceof TLRPC.TL_messageForwarded_old2) {
            if (isRestrictedMessage) {
                type = 0;
            } else if (emojiAnimatedSticker != null) {
                if (isSticker()) {
                    type = TYPE_STICKER;
                } else {
                    type = TYPE_ANIMATED_STICKER;
                }
            } else if (isMediaEmpty()) {
                type = 0;
                if (TextUtils.isEmpty(messageText) && eventId == 0) {
                    messageText = "Empty message";
                }
            } else if (messageOwner.media.ttl_seconds != 0 && (messageOwner.media.photo instanceof TLRPC.TL_photoEmpty || getDocument() instanceof TLRPC.TL_documentEmpty)) {
                contentType = 1;
                type = 10;
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaDice) {
                type = TYPE_ANIMATED_STICKER;
                if (messageOwner.media.document == null) {
                    messageOwner.media.document = new TLRPC.TL_document();
                    messageOwner.media.document.file_reference = new byte[0];
                    messageOwner.media.document.mime_type = "application/x-tgsdice";
                    messageOwner.media.document.dc_id = Integer.MIN_VALUE;
                    messageOwner.media.document.id = Integer.MIN_VALUE;
                    TLRPC.TL_documentAttributeImageSize attributeImageSize = new TLRPC.TL_documentAttributeImageSize();
                    attributeImageSize.w = 512;
                    attributeImageSize.h = 512;
                    messageOwner.media.document.attributes.add(attributeImageSize);
                }
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                type = TYPE_PHOTO;
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaGeo || messageOwner.media instanceof TLRPC.TL_messageMediaVenue || messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                type = TYPE_GEO;
            } else if (isRoundVideo()) {
                type = TYPE_ROUND_VIDEO;
            } else if (isVideo()) {
                type = TYPE_VIDEO;
            } else if (isVoice()) {
                type = 2;
            } else if (isMusic()) {
                type = 14;
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                type = 12;
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaPoll) {
                type = TYPE_POLL;
                checkedVotes = new ArrayList<>();
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaUnsupported) {
                type = 0;
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                TLRPC.Document document = getDocument();
                if (document != null && document.mime_type != null) {
                    if (isGifDocument(document, hasValidGroupId())) {
                        type = 8;
                    } else if (isSticker()) {
                        type = TYPE_STICKER;
                    } else if (isAnimatedSticker()) {
                        type = TYPE_ANIMATED_STICKER;
                    } else {
                        type = 9;
                    }
                } else {
                    type = 9;
                }
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                type = 0;
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) {
                type = 0;
            }
        } else if (messageOwner instanceof TLRPC.TL_messageService) {
            if (messageOwner.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                type = 0;
            } else if (messageOwner.action instanceof TLRPC.TL_messageActionChatEditPhoto || messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                contentType = 1;
                type = 11;
            } else if (messageOwner.action instanceof TLRPC.TL_messageEncryptedAction) {
                if (messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages || messageOwner.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                    contentType = 1;
                    type = 10;
                } else {
                    contentType = -1;
                    type = -1;
                }
            } else if (messageOwner.action instanceof TLRPC.TL_messageActionHistoryClear) {
                contentType = -1;
                type = -1;
            } else if (messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall) {
                type = 16;
            } else {
                contentType = 1;
                type = 10;
            }
        }
        if (oldType != 1000 && oldType != type) {
            updateMessageText(MessagesController.getInstance(currentAccount).getUsers(), MessagesController.getInstance(currentAccount).getChats(), null, null);
            generateThumbs(false);
        }
    }

    public boolean checkLayout() {
        if (type != 0 || messageOwner.peer_id == null || messageText == null || messageText.length() == 0) {
            return false;
        }
        if (layoutCreated) {
            int newMinSize = AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() : AndroidUtilities.displaySize.x;
            if (Math.abs(generatedWithMinSize - newMinSize) > AndroidUtilities.dp(52) || generatedWithDensity != AndroidUtilities.density) {
                layoutCreated = false;
            }
        }
        if (!layoutCreated) {
            layoutCreated = true;
            TLRPC.User fromUser = null;
            if (isFromUser()) {
                fromUser = MessagesController.getInstance(currentAccount).getUser(messageOwner.from_id.user_id);
            }
            TextPaint paint;
            if (messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                paint = Theme.chat_msgGameTextPaint;
            } else {
                paint = Theme.chat_msgTextPaint;
            }
            int[] emojiOnly = allowsBigEmoji() ? new int[1] : null;
            messageText = Emoji.replaceEmoji(messageText, paint.getFontMetricsInt(), AndroidUtilities.dp(20), false, emojiOnly);
            checkEmojiOnly(emojiOnly);
            generateLayout(fromUser);
            return true;
        }
        return false;
    }

    public void resetLayout() {
        layoutCreated = false;
    }

    public String getMimeType() {
        TLRPC.Document document = getDocument();
        if (document != null) {
            return document.mime_type;
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) {
            TLRPC.WebDocument photo = ((TLRPC.TL_messageMediaInvoice) messageOwner.media).photo;
            if (photo != null) {
                return photo.mime_type;
            }
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
            return "image/jpeg";
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
            if (messageOwner.media.webpage.photo != null) {
                return "image/jpeg";
            }
        }
        return "";
    }

    public boolean canPreviewDocument() {
        return canPreviewDocument(getDocument());
    }

    public static boolean isAnimatedStickerDocument(TLRPC.Document document) {
        return document != null && document.mime_type.equals("video/webm");
    }
    public static boolean isGifDocument(WebFile document) {
        return document != null && (document.mime_type.equals("image/gif") || isNewGifDocument(document));
    }

    public static boolean isGifDocument(TLRPC.Document document) {
        return isGifDocument(document, false);
    }

    public static boolean isGifDocument(TLRPC.Document document, boolean hasGroup) {
        return document != null && document.mime_type != null && (document.mime_type.equals("image/gif") && !hasGroup || isNewGifDocument(document));
    }

    public static boolean isDocumentHasThumb(TLRPC.Document document) {
        if (document == null || document.thumbs.isEmpty()) {
            return false;
        }
        for (int a = 0, N = document.thumbs.size(); a < N; a++) {
            TLRPC.PhotoSize photoSize = document.thumbs.get(a);
            if (photoSize != null && !(photoSize instanceof TLRPC.TL_photoSizeEmpty) && !(photoSize.location instanceof TLRPC.TL_fileLocationUnavailable)) {
                return true;
            }
        }
        return false;
    }

    public static boolean canPreviewDocument(TLRPC.Document document) {
        if (document != null && document.mime_type != null) {
            String mime = document.mime_type.toLowerCase();
            if (isDocumentHasThumb(document) && (mime.equals("image/png") || mime.equals("image/jpg") || mime.equals("image/jpeg")) || (Build.VERSION.SDK_INT >= 26 && (mime.equals("image/heic")))) {
                for (int a = 0; a < document.attributes.size(); a++) {
                    TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                    if (attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                        TLRPC.TL_documentAttributeImageSize size = (TLRPC.TL_documentAttributeImageSize) attribute;
                        return size.w < 6000 && size.h < 6000;
                    }
                }
            } else if (BuildVars.DEBUG_PRIVATE_VERSION) {
                String fileName = FileLoader.getDocumentFileName(document);
                if (fileName.startsWith("tg_secret_sticker") && fileName.endsWith("json")) {
                    return true;
                } else if (fileName.endsWith(".svg")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isRoundVideoDocument(TLRPC.Document document) {
        if (document != null && "video/mp4".equals(document.mime_type)) {
            int width = 0;
            int height = 0;
            boolean round = false;
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                    width = attribute.w;
                    height = attribute.h;
                    round = attribute.round_message;
                }
            }
            if (round && width <= 1280 && height <= 1280) {
                return true;
            }
        }
        return false;
    }

    public static boolean isNewGifDocument(WebFile document) {
        if (document != null && "video/mp4".equals(document.mime_type)) {
            int width = 0;
            int height = 0;
            //boolean animated = false;
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeAnimated) {
                    //animated = true;
                } else if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                    width = attribute.w;
                    height = attribute.h;
                }
            }
            if (/*animated && */width <= 1280 && height <= 1280) {
                return true;
            }
        }
        return false;
    }

    public static boolean isNewGifDocument(TLRPC.Document document) {
        if (document != null && "video/mp4".equals(document.mime_type)) {
            int width = 0;
            int height = 0;
            boolean animated = false;
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeAnimated) {
                    animated = true;
                } else if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                    width = attribute.w;
                    height = attribute.h;
                }
            }
            if (animated && width <= 1280 && height <= 1280) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSystemSignUp(MessageObject message) {
        return message != null && message.messageOwner instanceof TLRPC.TL_messageService && ((TLRPC.TL_messageService)message.messageOwner).action instanceof TLRPC.TL_messageActionContactSignUp;
    }

    public void generateThumbs(boolean update) {
        if (messageOwner instanceof TLRPC.TL_messageService) {
            if (messageOwner.action instanceof TLRPC.TL_messageActionChatEditPhoto) {
                TLRPC.Photo photo = messageOwner.action.photo;
                if (!update) {
                    photoThumbs = new ArrayList<>(photo.sizes);
                } else if (photoThumbs != null && !photoThumbs.isEmpty()) {
                    for (int a = 0; a < photoThumbs.size(); a++) {
                        TLRPC.PhotoSize photoObject = photoThumbs.get(a);
                        for (int b = 0; b < photo.sizes.size(); b++) {
                            TLRPC.PhotoSize size = photo.sizes.get(b);
                            if (size instanceof TLRPC.TL_photoSizeEmpty) {
                                continue;
                            }
                            if (size.type.equals(photoObject.type)) {
                                photoObject.location = size.location;
                                break;
                            }
                        }
                    }
                }
                if (photo.dc_id != 0 && photoThumbs != null) {
                    for (int a = 0, N = photoThumbs.size(); a < N; a++) {
                        TLRPC.FileLocation location = photoThumbs.get(a).location;
                        if (location == null) {
                            continue;
                        }
                        location.dc_id = photo.dc_id;
                        location.file_reference = photo.file_reference;
                    }
                }
                photoThumbsObject = messageOwner.action.photo;
            }
        } else if (emojiAnimatedSticker != null) {
            if (TextUtils.isEmpty(emojiAnimatedStickerColor) && isDocumentHasThumb(emojiAnimatedSticker)) {
                if (!update || photoThumbs == null) {
                    photoThumbs = new ArrayList<>();
                    photoThumbs.addAll(emojiAnimatedSticker.thumbs);
                } else if (!photoThumbs.isEmpty()) {
                    updatePhotoSizeLocations(photoThumbs, emojiAnimatedSticker.thumbs);
                }
                photoThumbsObject = emojiAnimatedSticker;
            }
        } else if (messageOwner.media != null && !(messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
            if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                TLRPC.Photo photo = messageOwner.media.photo;
                if (!update || photoThumbs != null && photoThumbs.size() != photo.sizes.size()) {
                    photoThumbs = new ArrayList<>(photo.sizes);
                } else if (photoThumbs != null && !photoThumbs.isEmpty()) {
                    for (int a = 0; a < photoThumbs.size(); a++) {
                        TLRPC.PhotoSize photoObject = photoThumbs.get(a);
                        if (photoObject == null) {
                            continue;
                        }
                        for (int b = 0; b < photo.sizes.size(); b++) {
                            TLRPC.PhotoSize size = photo.sizes.get(b);
                            if (size == null || size instanceof TLRPC.TL_photoSizeEmpty) {
                                continue;
                            }
                            if (size.type.equals(photoObject.type)) {
                                photoObject.location = size.location;
                                break;
                            } else if ("s".equals(photoObject.type) && size instanceof TLRPC.TL_photoStrippedSize) {
                                photoThumbs.set(a, size);
                                break;
                            }
                        }
                    }
                }
                photoThumbsObject = messageOwner.media.photo;
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                TLRPC.Document document = getDocument();
                if (isDocumentHasThumb(document)) {
                    if (!update || photoThumbs == null) {
                        photoThumbs = new ArrayList<>();
                        photoThumbs.addAll(document.thumbs);
                    } else if (!photoThumbs.isEmpty()) {
                        updatePhotoSizeLocations(photoThumbs, document.thumbs);
                    }
                    photoThumbsObject = document;
                }
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                TLRPC.Document document = messageOwner.media.game.document;
                if (document != null) {
                    if (isDocumentHasThumb(document)) {
                        if (!update) {
                            photoThumbs = new ArrayList<>();
                            photoThumbs.addAll(document.thumbs);
                        } else if (photoThumbs != null && !photoThumbs.isEmpty()) {
                            updatePhotoSizeLocations(photoThumbs, document.thumbs);
                        }
                        photoThumbsObject = document;
                    }
                }
                TLRPC.Photo photo = messageOwner.media.game.photo;
                if (photo != null) {
                    if (!update || photoThumbs2 == null) {
                        photoThumbs2 = new ArrayList<>(photo.sizes);
                    } else if (!photoThumbs2.isEmpty()) {
                        updatePhotoSizeLocations(photoThumbs2, photo.sizes);
                    }
                    photoThumbsObject2 = photo;
                }
                if (photoThumbs == null && photoThumbs2 != null) {
                    photoThumbs = photoThumbs2;
                    photoThumbs2 = null;
                    photoThumbsObject = photoThumbsObject2;
                    photoThumbsObject2 = null;
                }
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
                TLRPC.Photo photo = messageOwner.media.webpage.photo;
                TLRPC.Document document = messageOwner.media.webpage.document;
                if (photo != null) {
                    if (!update || photoThumbs == null) {
                        photoThumbs = new ArrayList<>(photo.sizes);
                    } else if (!photoThumbs.isEmpty()) {
                        updatePhotoSizeLocations(photoThumbs, photo.sizes);
                    }
                    photoThumbsObject = photo;
                } else if (document != null) {
                    if (isDocumentHasThumb(document)) {
                        if (!update) {
                            photoThumbs = new ArrayList<>();
                            photoThumbs.addAll(document.thumbs);
                        } else if (photoThumbs != null && !photoThumbs.isEmpty()) {
                            updatePhotoSizeLocations(photoThumbs, document.thumbs);
                        }
                        photoThumbsObject = document;
                    }
                }
            }
        }
    }

    private static void updatePhotoSizeLocations(ArrayList<TLRPC.PhotoSize> o, ArrayList<TLRPC.PhotoSize> n) {
        for (int a = 0, N = o.size(); a < N; a++) {
            TLRPC.PhotoSize photoObject = o.get(a);
            if (photoObject == null) {
                continue;
            }
            for (int b = 0, N2 = n.size(); b < N2; b++) {
                TLRPC.PhotoSize size = n.get(b);
                if (size instanceof TLRPC.TL_photoSizeEmpty || size instanceof TLRPC.TL_photoCachedSize || size == null) {
                    continue;
                }
                if (size.type.equals(photoObject.type)) {
                    photoObject.location = size.location;
                    break;
                }
            }
        }
    }

    public CharSequence replaceWithLink(CharSequence source, String param, ArrayList<Long> uids, AbstractMap<Long, TLRPC.User> usersDict, LongSparseArray<TLRPC.User> sUsersDict) {
        int start = TextUtils.indexOf(source, param);
        if (start >= 0) {
            SpannableStringBuilder names = new SpannableStringBuilder("");
            for (int a = 0; a < uids.size(); a++) {
                TLRPC.User user = null;
                if (usersDict != null) {
                    user = usersDict.get(uids.get(a));
                } else if (sUsersDict != null) {
                    user = sUsersDict.get(uids.get(a));
                }
                if (user == null) {
                    user = MessagesController.getInstance(currentAccount).getUser(uids.get(a));
                }
                if (user != null) {
                    String name = UserObject.getUserName(user);
                    start = names.length();
                    if (names.length() != 0) {
                        names.append(", ");
                    }
                    names.append(name);
                    names.setSpan(new URLSpanNoUnderlineBold("" + user.id), start, start + name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            return TextUtils.replace(source, new String[]{param}, new CharSequence[]{names});
        }
        return source;
    }

    public static CharSequence replaceWithLink(CharSequence source, String param, TLObject object) {
        int start = TextUtils.indexOf(source, param);
        if (start >= 0) {
            String name;
            String id;
            TLObject spanObject = null;
            if (object instanceof TLRPC.User) {
                name = UserObject.getUserName((TLRPC.User) object);
                id = "" + ((TLRPC.User) object).id;
            } else if (object instanceof TLRPC.Chat) {
                name = ((TLRPC.Chat) object).title;
                id = "" + -((TLRPC.Chat) object).id;
            } else if (object instanceof TLRPC.TL_game) {
                TLRPC.TL_game game = (TLRPC.TL_game) object;
                name = game.title;
                id = "game";
            } else if (object instanceof TLRPC.TL_chatInviteExported) {
                TLRPC.TL_chatInviteExported invite  = (TLRPC.TL_chatInviteExported) object;
                name = invite.link;
                id = "invite";
                spanObject = invite;
            } else {
                name = "";
                id = "0";
            }
            name = name.replace('\n', ' ');
            SpannableStringBuilder builder = new SpannableStringBuilder(TextUtils.replace(source, new String[]{param}, new String[]{name}));
            URLSpanNoUnderlineBold span = new URLSpanNoUnderlineBold("" + id);
            span.setObject(spanObject);
            builder.setSpan(span, start, start + name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return builder;
        }
        return source;
    }

    public String getExtension() {
        String fileName = getFileName();
        int idx = fileName.lastIndexOf('.');
        String ext = null;
        if (idx != -1) {
            ext = fileName.substring(idx + 1);
        }
        if (ext == null || ext.length() == 0) {
            ext = getDocument().mime_type;
        }
        if (ext == null) {
            ext = "";
        }
        ext = ext.toUpperCase();
        return ext;
    }

    public String getFileName() {
        return getFileName(messageOwner);
    }

    public static String getFileName(TLRPC.Message messageOwner) {
        if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
            return FileLoader.getAttachFileName(getDocument(messageOwner));
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
            ArrayList<TLRPC.PhotoSize> sizes = messageOwner.media.photo.sizes;
            if (sizes.size() > 0) {
                TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
                if (sizeFull != null) {
                    return FileLoader.getAttachFileName(sizeFull);
                }
            }
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
            return FileLoader.getAttachFileName(messageOwner.media.webpage.document);
        }
        return "";
    }

    public int getMediaType() {
        if (isVideo()) {
            return FileLoader.MEDIA_DIR_VIDEO;
        } else if (isVoice()) {
            return FileLoader.MEDIA_DIR_AUDIO;
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
            return FileLoader.MEDIA_DIR_DOCUMENT;
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
            return FileLoader.MEDIA_DIR_IMAGE;
        }
        return FileLoader.MEDIA_DIR_CACHE;
    }

    private static boolean containsUrls(CharSequence message) {
        if (message == null || message.length() < 2 || message.length() > 1024 * 20) {
            return false;
        }

        int length = message.length();

        int digitsInRow = 0;
        int schemeSequence = 0;
        int dotSequence = 0;

        char lastChar = 0;

        for (int i = 0; i < length; i++) {
            char c = message.charAt(i);

            if (c >= '0' && c <= '9') {
                digitsInRow++;
                if (digitsInRow >= 6) {
                    return true;
                }
                schemeSequence = 0;
                dotSequence = 0;
            } else if (!(c != ' ' && digitsInRow > 0)) {
                digitsInRow = 0;
            }
            if ((c == '@' || c == '#' || c == '/' || c == '$') && i == 0 || i != 0 && (message.charAt(i - 1) == ' ' || message.charAt(i - 1) == '\n')) {
                return true;
            }
            if (c == ':') {
                if (schemeSequence == 0) {
                    schemeSequence = 1;
                } else {
                    schemeSequence = 0;
                }
            } else if (c == '/') {
                if (schemeSequence == 2) {
                    return true;
                }
                if (schemeSequence == 1) {
                    schemeSequence++;
                } else {
                    schemeSequence = 0;
                }
            } else if (c == '.') {
                if (dotSequence == 0 && lastChar != ' ') {
                    dotSequence++;
                } else {
                    dotSequence = 0;
                }
            } else if (c != ' ' && lastChar == '.' && dotSequence == 1) {
                return true;
            } else {
                dotSequence = 0;
            }
            lastChar = c;
        }
        return false;
    }

    public void generateLinkDescription() {
        if (linkDescription != null) {
            return;
        }
        int hashtagsType = 0;
        if (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageOwner.media.webpage instanceof TLRPC.TL_webPage && messageOwner.media.webpage.description != null) {
            linkDescription = Spannable.Factory.getInstance().newSpannable(messageOwner.media.webpage.description);
            String siteName = messageOwner.media.webpage.site_name;
            if (siteName != null) {
                siteName = siteName.toLowerCase();
            }
            if ("instagram".equals(siteName)) {
                hashtagsType = 1;
            } else if ("twitter".equals(siteName)) {
                hashtagsType = 2;
            }
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaGame && messageOwner.media.game.description != null) {
            linkDescription = Spannable.Factory.getInstance().newSpannable(messageOwner.media.game.description);
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaInvoice && messageOwner.media.description != null) {
            linkDescription = Spannable.Factory.getInstance().newSpannable(messageOwner.media.description);
        }
        if (!TextUtils.isEmpty(linkDescription)) {
            if (containsUrls(linkDescription)) {
                try {
                    AndroidUtilities.addLinks((Spannable) linkDescription, Linkify.WEB_URLS);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            linkDescription = Emoji.replaceEmoji(linkDescription, Theme.chat_msgTextPaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
            if (hashtagsType != 0) {
                if (!(linkDescription instanceof Spannable)) {
                    linkDescription = new SpannableStringBuilder(linkDescription);
                }
                addUrlsByPattern(isOutOwner(), linkDescription, false, hashtagsType, 0, false);
            }
        }
    }

    public void generateCaption() {
        if (caption != null || isRoundVideo()) {
            return;
        }
        if (!isMediaEmpty() && !(messageOwner.media instanceof TLRPC.TL_messageMediaGame) && !TextUtils.isEmpty(messageOwner.message)) {
            caption = Emoji.replaceEmoji(messageOwner.message, Theme.chat_msgTextPaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);

            boolean hasEntities;
            if (messageOwner.send_state != MESSAGE_SEND_STATE_SENT) {
                hasEntities = false;
            } else {
                hasEntities = !messageOwner.entities.isEmpty();
            }

            boolean useManualParse = !hasEntities && (
                    eventId != 0 ||
                            messageOwner.media instanceof TLRPC.TL_messageMediaPhoto_old ||
                            messageOwner.media instanceof TLRPC.TL_messageMediaPhoto_layer68 ||
                            messageOwner.media instanceof TLRPC.TL_messageMediaPhoto_layer74 ||
                            messageOwner.media instanceof TLRPC.TL_messageMediaDocument_old ||
                            messageOwner.media instanceof TLRPC.TL_messageMediaDocument_layer68 ||
                            messageOwner.media instanceof TLRPC.TL_messageMediaDocument_layer74 ||
                            isOut() && messageOwner.send_state != MESSAGE_SEND_STATE_SENT ||
                            messageOwner.id < 0);

            if (useManualParse) {
                if (containsUrls(caption)) {
                    try {
                        AndroidUtilities.addLinks((Spannable) caption, Linkify.WEB_URLS | Linkify.PHONE_NUMBERS);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                addUrlsByPattern(isOutOwner(), caption, true, 0, 0, true);
            }

            addEntitiesToText(caption, useManualParse);
            if (isVideo()) {
                addUrlsByPattern(isOutOwner(), caption, true, 3, getDuration(), false);
            } else if (isMusic() || isVoice()) {
                addUrlsByPattern(isOutOwner(), caption, true, 4, getDuration(), false);
            }
        }
    }

    public static void addUrlsByPattern(boolean isOut, CharSequence charSequence, boolean botCommands, int patternType, int duration, boolean check) {
        try {
            Matcher matcher;
            if (patternType == 3 || patternType == 4) {
                if (videoTimeUrlPattern == null) {
                    videoTimeUrlPattern = Pattern.compile("\\b(?:(\\d{1,2}):)?(\\d{1,3}):([0-5][0-9])\\b");
                }
                matcher = videoTimeUrlPattern.matcher(charSequence);
            } else if (patternType == 1) {
                if (instagramUrlPattern == null) {
                    instagramUrlPattern = Pattern.compile("(^|\\s|\\()@[a-zA-Z\\d_.]{1,32}|(^|\\s|\\()#[\\w.]+");
                }
                matcher = instagramUrlPattern.matcher(charSequence);
            } else {
                if (urlPattern == null) {
                    urlPattern = Pattern.compile("(^|\\s)/[a-zA-Z@\\d_]{1,255}|(^|\\s|\\()@[a-zA-Z\\d_]{1,32}|(^|\\s|\\()#[^0-9][\\w.]+|(^|\\s)\\$[A-Z]{3,8}([ ,.]|$)");
                }
                matcher = urlPattern.matcher(charSequence);
            }
            Spannable spannable = (Spannable) charSequence;
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                URLSpanNoUnderline url = null;
                if (patternType == 3 || patternType == 4) {
                    URLSpan[] spans = spannable.getSpans(start, end, URLSpan.class);
                    if (spans != null && spans.length > 0) {
                        continue;
                    }
                    int count = matcher.groupCount();
                    int s1 = matcher.start(1);
                    int e1 = matcher.end(1);
                    int s2 = matcher.start(2);
                    int e2 = matcher.end(2);
                    int s3 = matcher.start(3);
                    int e3 = matcher.end(3);
                    int minutes = Utilities.parseInt(charSequence.subSequence(s2, e2));
                    int seconds = Utilities.parseInt(charSequence.subSequence(s3, e3));
                    int hours = s1 >= 0 && e1 >= 0 ? Utilities.parseInt(charSequence.subSequence(s1, e1)) : -1;
                    seconds += minutes * 60;
                    if (hours > 0) {
                        seconds += hours * 60 * 60;
                    }
                    if (seconds > duration) {
                        continue;
                    }
                    if (patternType == 3) {
                        url = new URLSpanNoUnderline("video?" + seconds);
                    } else {
                        url = new URLSpanNoUnderline("audio?" + seconds);
                    }
                } else {
                    char ch = charSequence.charAt(start);
                    if (patternType != 0) {
                        if (ch != '@' && ch != '#') {
                            start++;
                        }
                        ch = charSequence.charAt(start);
                        if (ch != '@' && ch != '#') {
                            continue;
                        }
                    } else {
                        if (ch != '@' && ch != '#' && ch != '/' && ch != '$') {
                            start++;
                        }
                    }
                    if (patternType == 1) {
                        if (ch == '@') {
                            url = new URLSpanNoUnderline("https://instagram.com/" + charSequence.subSequence(start + 1, end).toString());
                        } else {
                            url = new URLSpanNoUnderline("https://www.instagram.com/explore/tags/" + charSequence.subSequence(start + 1, end).toString());
                        }
                    } else if (patternType == 2) {
                        if (ch == '@') {
                            url = new URLSpanNoUnderline("https://twitter.com/" + charSequence.subSequence(start + 1, end).toString());
                        } else {
                            url = new URLSpanNoUnderline("https://twitter.com/hashtag/" + charSequence.subSequence(start + 1, end).toString());
                        }
                    } else {
                        if (charSequence.charAt(start) == '/') {
                            if (botCommands) {
                                url = new URLSpanBotCommand(charSequence.subSequence(start, end).toString(), isOut ? 1 : 0);
                            }
                        } else {
                            url = new URLSpanNoUnderline(charSequence.subSequence(start, end).toString());
                        }
                    }
                }
                if (url != null) {
                    if (check) {
                        ClickableSpan[] spans = spannable.getSpans(start, end, ClickableSpan.class);
                        if (spans != null && spans.length > 0) {
                            spannable.removeSpan(spans[0]);
                        }
                    }
                    spannable.setSpan(url, start, end, 0);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static int[] getWebDocumentWidthAndHeight(TLRPC.WebDocument document) {
        if (document == null) {
            return null;
        }
        for (int a = 0, size = document.attributes.size(); a < size; a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                return new int[]{attribute.w, attribute.h};
            } else if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                return new int[]{attribute.w, attribute.h};
            }
        }
        return null;
    }

    public static int getWebDocumentDuration(TLRPC.WebDocument document) {
        if (document == null) {
            return 0;
        }
        for (int a = 0, size = document.attributes.size(); a < size; a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                return attribute.duration;
            } else if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                return attribute.duration;
            }
        }
        return 0;
    }

    public static int[] getInlineResultWidthAndHeight(TLRPC.BotInlineResult inlineResult) {
        int[] result = getWebDocumentWidthAndHeight(inlineResult.content);
        if (result == null) {
            result = getWebDocumentWidthAndHeight(inlineResult.thumb);
            if (result == null) {
                result = new int[]{0, 0};
            }
        }
        return result;
    }

    public static int getInlineResultDuration(TLRPC.BotInlineResult inlineResult) {
        int result = getWebDocumentDuration(inlineResult.content);
        if (result == 0) {
            result = getWebDocumentDuration(inlineResult.thumb);
        }
        return result;
    }

    public boolean hasValidGroupId() {
        return getGroupId() != 0 && (photoThumbs != null && !photoThumbs.isEmpty() || isMusic() || isDocument());
    }

    public long getGroupIdForUse() {
        return localSentGroupId != 0 ? localSentGroupId : messageOwner.grouped_id;
    }

    public long getGroupId() {
        return localGroupId != 0 ? localGroupId : getGroupIdForUse();
    }

    public static void addLinks(boolean isOut, CharSequence messageText) {
        addLinks(isOut, messageText, true, false);
    }

    public static void addLinks(boolean isOut, CharSequence messageText, boolean botCommands, boolean check) {
        addLinks(isOut, messageText, botCommands, check, false);
    }

    public static void addLinks(boolean isOut, CharSequence messageText, boolean botCommands, boolean check, boolean internalOnly) {
        if (messageText instanceof Spannable && containsUrls(messageText)) {
            if (messageText.length() < 1000) {
                try {
                    AndroidUtilities.addLinks((Spannable) messageText, Linkify.WEB_URLS | Linkify.PHONE_NUMBERS, internalOnly);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else {
                try {
                    AndroidUtilities.addLinks((Spannable) messageText, Linkify.WEB_URLS, internalOnly);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            addUrlsByPattern(isOut, messageText, botCommands, 0, 0, check);
        }
    }

    public void resetPlayingProgress() {
        audioProgress = 0.0f;
        audioProgressSec = 0;
        bufferedProgress = 0.0f;
    }

    private boolean addEntitiesToText(CharSequence text, boolean useManualParse) {
        return addEntitiesToText(text, false, useManualParse);
    }

    public boolean addEntitiesToText(CharSequence text, boolean photoViewer, boolean useManualParse) {
        if (isRestrictedMessage) {
            ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
            TLRPC.TL_messageEntityItalic entityItalic = new TLRPC.TL_messageEntityItalic();
            entityItalic.offset = 0;
            entityItalic.length = text.length();
            entities.add(entityItalic);
            return addEntitiesToText(text, entities, isOutOwner(), true, photoViewer, useManualParse);
        } else {
            return addEntitiesToText(text, messageOwner.entities, isOutOwner(), true, photoViewer, useManualParse);
        }
    }

    public static boolean addEntitiesToText(CharSequence text, ArrayList<TLRPC.MessageEntity> entities, boolean out, boolean usernames, boolean photoViewer, boolean useManualParse) {
        if (!(text instanceof Spannable)) {
            return false;
        }
        Spannable spannable = (Spannable) text;
        URLSpan[] spans = spannable.getSpans(0, text.length(), URLSpan.class);
        boolean hasUrls = spans != null && spans.length > 0;
        if (entities.isEmpty()) {
            return hasUrls;
        }

        byte t;
        if (photoViewer) {
            t = 2;
        } else if (out) {
            t = 1;
        } else {
            t = 0;
        }

        ArrayList<TextStyleSpan.TextStyleRun> runs = new ArrayList<>();
        ArrayList<TLRPC.MessageEntity> entitiesCopy = new ArrayList<>(entities);

        Collections.sort(entitiesCopy, (o1, o2) -> {
            if (o1.offset > o2.offset) {
                return 1;
            } else if (o1.offset < o2.offset) {
                return -1;
            }
            return 0;
        });
        for (int a = 0, N = entitiesCopy.size(); a < N; a++) {
            TLRPC.MessageEntity entity = entitiesCopy.get(a);
            if (entity.length <= 0 || entity.offset < 0 || entity.offset >= text.length()) {
                continue;
            } else if (entity.offset + entity.length > text.length()) {
                entity.length = text.length() - entity.offset;
            }

            if (!useManualParse || entity instanceof TLRPC.TL_messageEntityBold ||
                    entity instanceof TLRPC.TL_messageEntityItalic ||
                    entity instanceof TLRPC.TL_messageEntityStrike ||
                    entity instanceof TLRPC.TL_messageEntityUnderline ||
                    entity instanceof TLRPC.TL_messageEntityBlockquote ||
                    entity instanceof TLRPC.TL_messageEntityCode ||
                    entity instanceof TLRPC.TL_messageEntityPre ||
                    entity instanceof TLRPC.TL_messageEntityMentionName ||
                    entity instanceof TLRPC.TL_inputMessageEntityMentionName ||
                    entity instanceof TLRPC.TL_messageEntityTextUrl ||
                    entity instanceof TLRPC.TL_messageEntitySpoiler) {
                if (spans != null && spans.length > 0) {
                    for (int b = 0; b < spans.length; b++) {
                        if (spans[b] == null) {
                            continue;
                        }
                        int start = spannable.getSpanStart(spans[b]);
                        int end = spannable.getSpanEnd(spans[b]);
                        if (entity.offset <= start && entity.offset + entity.length >= start || entity.offset <= end && entity.offset + entity.length >= end) {
                            spannable.removeSpan(spans[b]);
                            spans[b] = null;
                        }
                    }
                }
            }

            TextStyleSpan.TextStyleRun newRun = new TextStyleSpan.TextStyleRun();
            newRun.start = entity.offset;
            newRun.end = newRun.start + entity.length;
            TLRPC.MessageEntity urlEntity = null;
            if (entity instanceof TLRPC.TL_messageEntitySpoiler) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_SPOILER;
            } else if (entity instanceof TLRPC.TL_messageEntityStrike) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_STRIKE;
            } else if (entity instanceof TLRPC.TL_messageEntityUnderline) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_UNDERLINE;
            } else if (entity instanceof TLRPC.TL_messageEntityBlockquote) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_QUOTE;
            } else if (entity instanceof TLRPC.TL_messageEntityBold) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_BOLD;
            } else if (entity instanceof TLRPC.TL_messageEntityItalic) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_ITALIC;
            } else if (entity instanceof TLRPC.TL_messageEntityCode || entity instanceof TLRPC.TL_messageEntityPre) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_MONO;
            } else if (entity instanceof TLRPC.TL_messageEntityMentionName) {
                if (!usernames) {
                    continue;
                }
                newRun.flags = TextStyleSpan.FLAG_STYLE_MENTION;
                newRun.urlEntity = entity;
            } else if (entity instanceof TLRPC.TL_inputMessageEntityMentionName) {
                if (!usernames) {
                    continue;
                }
                newRun.flags = TextStyleSpan.FLAG_STYLE_MENTION;
                newRun.urlEntity = entity;
            } else {
                if (useManualParse && !(entity instanceof TLRPC.TL_messageEntityTextUrl)) {
                    continue;
                }
                if ((entity instanceof TLRPC.TL_messageEntityUrl || entity instanceof TLRPC.TL_messageEntityTextUrl) && Browser.isPassportUrl(entity.url)) {
                    continue;
                }
                if (entity instanceof TLRPC.TL_messageEntityMention && !usernames) {
                    continue;
                }
                newRun.flags = TextStyleSpan.FLAG_STYLE_URL;
                newRun.urlEntity = entity;
            }

            for (int b = 0, N2 = runs.size(); b < N2; b++) {
                TextStyleSpan.TextStyleRun run = runs.get(b);

                if (newRun.start > run.start) {
                    if (newRun.start >= run.end) {
                        continue;
                    }

                    if (newRun.end < run.end) {
                        TextStyleSpan.TextStyleRun r = new TextStyleSpan.TextStyleRun(newRun);
                        r.merge(run);
                        b++;
                        N2++;
                        runs.add(b, r);

                        r = new TextStyleSpan.TextStyleRun(run);
                        r.start = newRun.end;
                        b++;
                        N2++;
                        runs.add(b, r);
                    } else {
                        TextStyleSpan.TextStyleRun r = new TextStyleSpan.TextStyleRun(newRun);
                        r.merge(run);
                        r.end = run.end;
                        b++;
                        N2++;
                        runs.add(b, r);
                    }

                    int temp = newRun.start;
                    newRun.start = run.end;
                    run.end = temp;
                } else {
                    if (run.start >= newRun.end) {
                        continue;
                    }
                    int temp = run.start;
                    if (newRun.end == run.end) {
                        run.merge(newRun);
                    } else if (newRun.end < run.end) {
                        TextStyleSpan.TextStyleRun r = new TextStyleSpan.TextStyleRun(run);
                        r.merge(newRun);
                        r.end = newRun.end;
                        b++;
                        N2++;
                        runs.add(b, r);

                        run.start = newRun.end;
                    } else {
                        TextStyleSpan.TextStyleRun r = new TextStyleSpan.TextStyleRun(newRun);
                        r.start = run.end;
                        b++;
                        N2++;
                        runs.add(b, r);

                        run.merge(newRun);
                    }
                    newRun.end = temp;
                }
            }
            if (newRun.start < newRun.end) {
                runs.add(newRun);
            }
        }

        int count = runs.size();
        for (int a = 0; a < count; a++) {
            TextStyleSpan.TextStyleRun run = runs.get(a);

            boolean setRun = false;
            String url = run.urlEntity != null ? TextUtils.substring(text, run.urlEntity.offset, run.urlEntity.offset + run.urlEntity.length) : null;
            if (run.urlEntity instanceof TLRPC.TL_messageEntityBotCommand) {
                spannable.setSpan(new URLSpanBotCommand(url, t, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (run.urlEntity instanceof TLRPC.TL_messageEntityHashtag || run.urlEntity instanceof TLRPC.TL_messageEntityMention || run.urlEntity instanceof TLRPC.TL_messageEntityCashtag) {
                spannable.setSpan(new URLSpanNoUnderline(url, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (run.urlEntity instanceof TLRPC.TL_messageEntityEmail) {
                spannable.setSpan(new URLSpanReplacement("mailto:" + url, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (run.urlEntity instanceof TLRPC.TL_messageEntityUrl) {
                hasUrls = true;
                String lowerCase = url.toLowerCase();
                if (!lowerCase.contains("://")) {
                    spannable.setSpan(new URLSpanBrowser("http://" + url, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    spannable.setSpan(new URLSpanBrowser(url, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else if (run.urlEntity instanceof TLRPC.TL_messageEntityBankCard) {
                hasUrls = true;
                spannable.setSpan(new URLSpanNoUnderline("card:" + url, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (run.urlEntity instanceof TLRPC.TL_messageEntityPhone) {
                hasUrls = true;
                String tel = PhoneFormat.stripExceptNumbers(url);
                if (url.startsWith("+")) {
                    tel = "+" + tel;
                }
                spannable.setSpan(new URLSpanBrowser("tel:" + tel, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (run.urlEntity instanceof TLRPC.TL_messageEntityTextUrl) {
                spannable.setSpan(new URLSpanReplacement(run.urlEntity.url, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (run.urlEntity instanceof TLRPC.TL_messageEntityMentionName) {
                spannable.setSpan(new URLSpanUserMention("" + ((TLRPC.TL_messageEntityMentionName) run.urlEntity).user_id, t, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (run.urlEntity instanceof TLRPC.TL_inputMessageEntityMentionName) {
                spannable.setSpan(new URLSpanUserMention("" + ((TLRPC.TL_inputMessageEntityMentionName) run.urlEntity).user_id.user_id, t, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if ((run.flags & TextStyleSpan.FLAG_STYLE_MONO) != 0) {
                spannable.setSpan(new URLSpanMono(spannable, run.start, run.end, t, run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                setRun = true;
                spannable.setSpan(new TextStyleSpan(run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (!setRun && (run.flags & TextStyleSpan.FLAG_STYLE_SPOILER) != 0) {
                spannable.setSpan(new TextStyleSpan(run), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return hasUrls;
    }

    public boolean needDrawShareButton() {
        if (preview) {
            return false;
        } else if (scheduled) {
            return false;
        } else if (eventId != 0) {
            return false;
        } else if (messageOwner.noforwards) {
            return false;
        } else if (messageOwner.fwd_from != null && !isOutOwner() && messageOwner.fwd_from.saved_from_peer != null && getDialogId() == UserConfig.getInstance(currentAccount).getClientUserId()) {
            return true;
        } else if (type == TYPE_STICKER || type == TYPE_ANIMATED_STICKER) {
            return false;
        } else if (messageOwner.fwd_from != null && messageOwner.fwd_from.from_id instanceof TLRPC.TL_peerChannel && !isOutOwner()) {
            return true;
        } else if (isFromUser()) {
            if (messageOwner.media instanceof TLRPC.TL_messageMediaEmpty || messageOwner.media == null || messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && !(messageOwner.media.webpage instanceof TLRPC.TL_webPage)) {
                return false;
            }
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(messageOwner.from_id.user_id);
            if (user != null && user.bot) {
                return true;
            }
            if (!isOut()) {
                if (messageOwner.media instanceof TLRPC.TL_messageMediaGame || messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) {
                    return true;
                }
                TLRPC.Chat chat = messageOwner.peer_id != null && messageOwner.peer_id.channel_id != 0 ? getChat(null, null, messageOwner.peer_id.channel_id) : null;
                if (ChatObject.isChannel(chat) && chat.megagroup) {
                    return chat.username != null && chat.username.length() > 0 && !(messageOwner.media instanceof TLRPC.TL_messageMediaContact) && !(messageOwner.media instanceof TLRPC.TL_messageMediaGeo);
                }
            }
        } else if (messageOwner.from_id instanceof TLRPC.TL_peerChannel || messageOwner.post) {
            if (isSupergroup()) {
                return false;
            }
            if (messageOwner.peer_id.channel_id != 0 && (messageOwner.via_bot_id == 0 && messageOwner.reply_to == null || type != TYPE_STICKER && type != TYPE_ANIMATED_STICKER)) {
                return true;
            }
        }
        return false;
    }

    public boolean isYouTubeVideo() {
        return messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageOwner.media.webpage != null && !TextUtils.isEmpty(messageOwner.media.webpage.embed_url) && "YouTube".equals(messageOwner.media.webpage.site_name);
    }

    public int getMaxMessageTextWidth() {
        int maxWidth = 0;
        if (AndroidUtilities.isTablet() && eventId != 0) {
            generatedWithMinSize = AndroidUtilities.dp(530);
        } else {
            generatedWithMinSize = AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() : getParentWidth();
        }
        generatedWithDensity = AndroidUtilities.density;
        if (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageOwner.media.webpage != null && "telegram_background".equals(messageOwner.media.webpage.type)) {
            try {
                Uri uri = Uri.parse(messageOwner.media.webpage.url);
                String segment = uri.getLastPathSegment();
                if (uri.getQueryParameter("bg_color") != null) {
                    maxWidth = AndroidUtilities.dp(220);
                } else if (segment.length() == 6 || segment.length() == 13 && segment.charAt(6) == '-') {
                    maxWidth = AndroidUtilities.dp(200);
                }
            } catch (Exception ignore) {

            }
        } else if (isAndroidTheme()) {
            maxWidth = AndroidUtilities.dp(200);
        }
        if (maxWidth == 0) {
            maxWidth = generatedWithMinSize - AndroidUtilities.dp(needDrawAvatarInternal() && !isOutOwner() && !messageOwner.isThreadMessage ? 132 : 80);
            if (needDrawShareButton() && !isOutOwner()) {
                maxWidth -= AndroidUtilities.dp(10);
            }
            if (messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                maxWidth -= AndroidUtilities.dp(10);
            }
        }
        return maxWidth;
    }

    public void generateLayout(TLRPC.User fromUser) {
        if (type != 0 || messageOwner.peer_id == null || TextUtils.isEmpty(messageText)) {
            return;
        }

        generateLinkDescription();
        textLayoutBlocks = new ArrayList<>();
        textWidth = 0;

        boolean hasEntities;
        if (messageOwner.send_state != MESSAGE_SEND_STATE_SENT) {
            hasEntities = false;
        } else {
            hasEntities = !messageOwner.entities.isEmpty();
        }

        boolean useManualParse = !hasEntities && (
                eventId != 0 ||
                messageOwner instanceof TLRPC.TL_message_old ||
                messageOwner instanceof TLRPC.TL_message_old2 ||
                messageOwner instanceof TLRPC.TL_message_old3 ||
                messageOwner instanceof TLRPC.TL_message_old4 ||
                messageOwner instanceof TLRPC.TL_messageForwarded_old ||
                messageOwner instanceof TLRPC.TL_messageForwarded_old2 ||
                messageOwner instanceof TLRPC.TL_message_secret ||
                messageOwner.media instanceof TLRPC.TL_messageMediaInvoice ||
                isOut() && messageOwner.send_state != MESSAGE_SEND_STATE_SENT ||
                messageOwner.id < 0 || messageOwner.media instanceof TLRPC.TL_messageMediaUnsupported);

        if (useManualParse) {
            addLinks(isOutOwner(), messageText, true, true);
        } else {
//            if (messageText instanceof Spannable && messageText.length() < 1000) {
//                try {
//                    AndroidUtilities.addLinks((Spannable) messageText, Linkify.PHONE_NUMBERS);
//                } catch (Throwable e) {
//                    FileLog.e(e);
//                }
//            }
        }
        if (isYouTubeVideo() || replyMessageObject != null && replyMessageObject.isYouTubeVideo()) {
            addUrlsByPattern(isOutOwner(), messageText, false, 3, Integer.MAX_VALUE, false);
        } else if (replyMessageObject != null) {
            if (replyMessageObject.isVideo()) {
                addUrlsByPattern(isOutOwner(), messageText, false, 3, replyMessageObject.getDuration(), false);
            } else if (replyMessageObject.isMusic() || replyMessageObject.isVoice()) {
                addUrlsByPattern(isOutOwner(), messageText, false, 4, replyMessageObject.getDuration(), false);
            }
        }

        boolean hasUrls = addEntitiesToText(messageText, useManualParse);

        int maxWidth = getMaxMessageTextWidth();

        StaticLayout textLayout;

        TextPaint paint;
        if (messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
            paint = Theme.chat_msgGameTextPaint;
        } else {
            paint = Theme.chat_msgTextPaint;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                textLayout = StaticLayout.Builder.obtain(messageText, 0, messageText.length(), paint, maxWidth)
                        .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                        .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .build();
            } else {
                textLayout = new StaticLayout(messageText, paint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }

        textHeight = textLayout.getHeight();
        linesCount = textLayout.getLineCount();

        int blocksCount;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            blocksCount = 1;
        } else {
            blocksCount = (int) Math.ceil((float) linesCount / LINES_PER_BLOCK);
        }
        int linesOffset = 0;
        float prevOffset = 0;

        for (int a = 0; a < blocksCount; a++) {
            int currentBlockLinesCount;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                currentBlockLinesCount = linesCount;
            } else {
                currentBlockLinesCount = Math.min(LINES_PER_BLOCK, linesCount - linesOffset);
            }
            TextLayoutBlock block = new TextLayoutBlock();

            if (blocksCount == 1) {
                block.textLayout = textLayout;
                block.textYOffset = 0;
                block.charactersOffset = 0;
                block.charactersEnd = textLayout.getText().length();

                if (emojiOnlyCount != 0) {
                    switch (emojiOnlyCount) {
                        case 1:
                            textHeight -= AndroidUtilities.dp(5.3f);
                            block.textYOffset -= AndroidUtilities.dp(5.3f);
                            break;
                        case 2:
                            textHeight -= AndroidUtilities.dp(4.5f);
                            block.textYOffset -= AndroidUtilities.dp(4.5f);
                            break;
                        case 3:
                            textHeight -= AndroidUtilities.dp(4.2f);
                            block.textYOffset -= AndroidUtilities.dp(4.2f);
                            break;
                    }

                }
                block.height = textHeight;
            } else {
                int startCharacter = textLayout.getLineStart(linesOffset);
                int endCharacter = textLayout.getLineEnd(linesOffset + currentBlockLinesCount - 1);
                if (endCharacter < startCharacter) {
                    continue;
                }
                block.charactersOffset = startCharacter;
                block.charactersEnd = endCharacter;
                try {
                    SpannableStringBuilder sb = SpannableStringBuilder.valueOf(messageText.subSequence(startCharacter, endCharacter));
                    if (hasUrls && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        block.textLayout = StaticLayout.Builder.obtain(sb, 0, sb.length(), paint, maxWidth + AndroidUtilities.dp(2))
                                .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                                .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                .build();
                    } else {
                        block.textLayout = new StaticLayout(sb, 0, sb.length(), paint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    }

                    block.textYOffset = textLayout.getLineTop(linesOffset);
                    if (a != 0) {
                        block.height = (int) (block.textYOffset - prevOffset);
                    }
                    block.height = Math.max(block.height, block.textLayout.getLineBottom(block.textLayout.getLineCount() - 1));
                    prevOffset = block.textYOffset;
                } catch (Exception e) {
                    FileLog.e(e);
                    continue;
                }
                if (a == blocksCount - 1) {
                    currentBlockLinesCount = Math.max(currentBlockLinesCount, block.textLayout.getLineCount());
                    try {
                        textHeight = Math.max(textHeight, (int) (block.textYOffset + block.textLayout.getHeight()));
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
            block.spoilers.clear();
            if (!isSpoilersRevealed) {
                SpoilerEffect.addSpoilers(null, block.textLayout, null, block.spoilers);
            }

            textLayoutBlocks.add(block);

            float lastLeft;
            try {
                lastLeft = block.textLayout.getLineLeft(currentBlockLinesCount - 1);
                if (a == 0 && lastLeft >= 0) {
                    textXOffset = lastLeft;
                }
            } catch (Exception e) {
                lastLeft = 0;
                if (a == 0) {
                    textXOffset = 0;
                }
                FileLog.e(e);
            }

            float lastLine;
            try {
                lastLine = block.textLayout.getLineWidth(currentBlockLinesCount - 1);
            } catch (Exception e) {
                lastLine = 0;
                FileLog.e(e);
            }

            int linesMaxWidth = (int) Math.ceil(lastLine);
            if (linesMaxWidth > maxWidth + 80) {
                linesMaxWidth = maxWidth;
            }
            int lastLineWidthWithLeft;
            int linesMaxWidthWithLeft;

            if (a == blocksCount - 1) {
                lastLineWidth = linesMaxWidth;
            }

            linesMaxWidthWithLeft = lastLineWidthWithLeft = (int) Math.ceil(linesMaxWidth + Math.max(0, lastLeft));

            if (currentBlockLinesCount > 1) {
                boolean hasNonRTL = false;
                float textRealMaxWidth = 0, textRealMaxWidthWithLeft = 0, lineWidth, lineLeft;
                for (int n = 0; n < currentBlockLinesCount; n++) {
                    try {
                        lineWidth = block.textLayout.getLineWidth(n);
                    } catch (Exception e) {
                        FileLog.e(e);
                        lineWidth = 0;
                    }

                    try {
                        lineLeft = block.textLayout.getLineLeft(n);
                    } catch (Exception e) {
                        FileLog.e(e);
                        lineLeft = 0;
                    }

                    if (lineWidth > maxWidth + 20) {
                        lineWidth = maxWidth;
                        lineLeft = 0;
                    }

                    if (lineLeft > 0) {
                        textXOffset = Math.min(textXOffset, lineLeft);
                        block.directionFlags |= TextLayoutBlock.FLAG_RTL;
                        hasRtl = true;
                    } else {
                        block.directionFlags |= TextLayoutBlock.FLAG_NOT_RTL;
                    }

                    try {
                        if (!hasNonRTL && lineLeft == 0 && block.textLayout.getParagraphDirection(n) == Layout.DIR_LEFT_TO_RIGHT) {
                            hasNonRTL = true;
                        }
                    } catch (Exception ignore) {
                        hasNonRTL = true;
                    }

                    textRealMaxWidth = Math.max(textRealMaxWidth, lineWidth);
                    textRealMaxWidthWithLeft = Math.max(textRealMaxWidthWithLeft, lineWidth + lineLeft);
                    linesMaxWidth = Math.max(linesMaxWidth, (int) Math.ceil(lineWidth));
                    linesMaxWidthWithLeft = Math.max(linesMaxWidthWithLeft, (int) Math.ceil(lineWidth + lineLeft));
                }
                if (hasNonRTL) {
                    textRealMaxWidth = textRealMaxWidthWithLeft;
                    if (a == blocksCount - 1) {
                        lastLineWidth = lastLineWidthWithLeft;
                    }
                } else if (a == blocksCount - 1) {
                    lastLineWidth = linesMaxWidth;
                }
                textWidth = Math.max(textWidth, (int) Math.ceil(textRealMaxWidth));
            } else {
                if (lastLeft > 0) {
                    textXOffset = Math.min(textXOffset, lastLeft);
                    if (textXOffset == 0) {
                        linesMaxWidth += lastLeft;
                    }
                    hasRtl = blocksCount != 1;
                    block.directionFlags |= TextLayoutBlock.FLAG_RTL;
                } else {
                    block.directionFlags |= TextLayoutBlock.FLAG_NOT_RTL;
                }

                textWidth = Math.max(textWidth, Math.min(maxWidth, linesMaxWidth));
            }

            linesOffset += currentBlockLinesCount;
        }
    }

    public boolean isOut() {
        return messageOwner.out;
    }

    public boolean isOutOwner() {
        if (preview) {
            return true;
        }
        TLRPC.Chat chat = messageOwner.peer_id != null && messageOwner.peer_id.channel_id != 0 ? getChat(null, null, messageOwner.peer_id.channel_id) : null;
        if (!messageOwner.out || !(messageOwner.from_id instanceof TLRPC.TL_peerUser) && (!(messageOwner.from_id instanceof TLRPC.TL_peerChannel) || ChatObject.isChannel(chat) && !chat.megagroup) || messageOwner.post) {
            return false;
        }
        if (messageOwner.fwd_from == null) {
            return true;
        }
        long selfUserId = UserConfig.getInstance(currentAccount).getClientUserId();
        if (getDialogId() == selfUserId) {
            return messageOwner.fwd_from.from_id instanceof TLRPC.TL_peerUser && messageOwner.fwd_from.from_id.user_id == selfUserId && (messageOwner.fwd_from.saved_from_peer == null || messageOwner.fwd_from.saved_from_peer.user_id == selfUserId)
                    || messageOwner.fwd_from.saved_from_peer != null && messageOwner.fwd_from.saved_from_peer.user_id == selfUserId && (messageOwner.fwd_from.from_id == null || messageOwner.fwd_from.from_id.user_id == selfUserId);
        }
        return messageOwner.fwd_from.saved_from_peer == null || messageOwner.fwd_from.saved_from_peer.user_id == selfUserId;
    }

    public boolean needDrawAvatar() {
        if (customAvatarDrawable != null) {
            return true;
        }
        if (isSponsored() && isFromChat()) {
            return true;
        }
        return !isSponsored() && (isFromUser() || isFromGroup() || eventId != 0 || messageOwner.fwd_from != null && messageOwner.fwd_from.saved_from_peer != null);
    }

    private boolean needDrawAvatarInternal() {
        if (customAvatarDrawable != null) {
            return true;
        }
        return !isSponsored() && (isFromChat() && isFromUser() || isFromGroup() || eventId != 0 || messageOwner.fwd_from != null && messageOwner.fwd_from.saved_from_peer != null);
    }

    public boolean isFromChat() {
        if (getDialogId() == UserConfig.getInstance(currentAccount).clientUserId) {
            return true;
        }
        TLRPC.Chat chat = messageOwner.peer_id != null && messageOwner.peer_id.channel_id != 0 ? getChat(null, null, messageOwner.peer_id.channel_id) : null;
        if (ChatObject.isChannel(chat) && chat.megagroup || messageOwner.peer_id != null && messageOwner.peer_id.chat_id != 0) {
            return true;
        }
        if (messageOwner.peer_id != null && messageOwner.peer_id.channel_id != 0) {
            return chat != null && chat.megagroup;
        }
        return false;
    }

    public static long getFromChatId(TLRPC.Message message) {
        return getPeerId(message.from_id);
    }

    public static long getPeerId(TLRPC.Peer peer) {
        if (peer == null) {
            return 0;
        }
        if (peer instanceof TLRPC.TL_peerChat) {
            return -peer.chat_id;
        } else if (peer instanceof TLRPC.TL_peerChannel) {
            return -peer.channel_id;
        } else {
            return peer.user_id;
        }
    }

    public long getFromChatId() {
        return getFromChatId(messageOwner);
    }

    public long getChatId() {
        if (messageOwner.peer_id instanceof TLRPC.TL_peerChat) {
            return messageOwner.peer_id.chat_id;
        } else if (messageOwner.peer_id instanceof TLRPC.TL_peerChannel) {
            return messageOwner.peer_id.channel_id;
        }
        return 0;
    }

    public boolean isFromUser() {
        return messageOwner.from_id instanceof TLRPC.TL_peerUser && !messageOwner.post;
    }

    public boolean isFromGroup() {
        TLRPC.Chat chat = messageOwner.peer_id != null && messageOwner.peer_id.channel_id != 0 ? getChat(null, null, messageOwner.peer_id.channel_id) : null;
        return messageOwner.from_id instanceof TLRPC.TL_peerChannel && ChatObject.isChannel(chat) && chat.megagroup;
    }

    public boolean isForwardedChannelPost() {
        return messageOwner.from_id instanceof TLRPC.TL_peerChannel && messageOwner.fwd_from != null && messageOwner.fwd_from.channel_post != 0 && messageOwner.fwd_from.saved_from_peer instanceof TLRPC.TL_peerChannel && messageOwner.from_id.channel_id == messageOwner.fwd_from.saved_from_peer.channel_id;
    }

    public boolean isUnread() {
        return messageOwner.unread;
    }

    public boolean isContentUnread() {
        return messageOwner.media_unread;
    }

    public void setIsRead() {
        messageOwner.unread = false;
    }

    public int getUnradFlags() {
        return getUnreadFlags(messageOwner);
    }

    public static int getUnreadFlags(TLRPC.Message message) {
        int flags = 0;
        if (!message.unread) {
            flags |= 1;
        }
        if (!message.media_unread) {
            flags |= 2;
        }
        return flags;
    }

    public void setContentIsRead() {
        messageOwner.media_unread = false;
    }

    public int getId() {
        return messageOwner.id;
    }

    public int getRealId() {
        return messageOwner.realId != 0 ? messageOwner.realId : messageOwner.id;
    }

    public static int getMessageSize(TLRPC.Message message) {
        TLRPC.Document document;
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            document = message.media.webpage.document;
        } else if (message.media instanceof TLRPC.TL_messageMediaGame) {
            document = message.media.game.document;
        } else {
            document = message.media != null ? message.media.document : null;
        }
        if (document != null) {
            return document.size;
        }
        return 0;
    }

    public int getSize() {
        return getMessageSize(messageOwner);
    }

    public static void fixMessagePeer(ArrayList<TLRPC.Message> messages, long channelId) {
        if (messages == null || messages.isEmpty() || channelId == 0) {
            return;
        }
        for (int a = 0; a < messages.size(); a++) {
            TLRPC.Message message = messages.get(a);
            if (message instanceof TLRPC.TL_messageEmpty) {
                message.peer_id = new TLRPC.TL_peerChannel();
                message.peer_id.channel_id = channelId;
            }
        }
    }

    public long getChannelId() {
        return getChannelId(messageOwner);
    }

    public static long getChannelId(TLRPC.Message message) {
        if (message.peer_id != null) {
            return message.peer_id.channel_id;
        }
        return 0;
    }

    public static boolean shouldEncryptPhotoOrVideo(TLRPC.Message message) {
        if (message instanceof TLRPC.TL_message_secret) {
            return (message.media instanceof TLRPC.TL_messageMediaPhoto || isVideoMessage(message)) && message.ttl > 0 && message.ttl <= 60;
        } else {
            return (message.media instanceof TLRPC.TL_messageMediaPhoto || message.media instanceof TLRPC.TL_messageMediaDocument) && message.media.ttl_seconds != 0;
        }
    }

    public boolean shouldEncryptPhotoOrVideo() {
        return shouldEncryptPhotoOrVideo(messageOwner);
    }

    public static boolean isSecretPhotoOrVideo(TLRPC.Message message) {
        if (message instanceof TLRPC.TL_message_secret) {
            return (message.media instanceof TLRPC.TL_messageMediaPhoto || isRoundVideoMessage(message) || isVideoMessage(message)) && message.ttl > 0 && message.ttl <= 60;
        } else if (message instanceof TLRPC.TL_message) {
            return (message.media instanceof TLRPC.TL_messageMediaPhoto || message.media instanceof TLRPC.TL_messageMediaDocument) && message.media.ttl_seconds != 0;
        }
        return false;
    }

    public static boolean isSecretMedia(TLRPC.Message message) {
        if (message instanceof TLRPC.TL_message_secret) {
            return (message.media instanceof TLRPC.TL_messageMediaPhoto || isRoundVideoMessage(message) || isVideoMessage(message)) && message.media.ttl_seconds != 0;
        } else if (message instanceof TLRPC.TL_message) {
            return (message.media instanceof TLRPC.TL_messageMediaPhoto || message.media instanceof TLRPC.TL_messageMediaDocument) && message.media.ttl_seconds != 0;
        }
        return false;
    }

    public boolean needDrawBluredPreview() {
        if (messageOwner instanceof TLRPC.TL_message_secret) {
            int ttl = Math.max(messageOwner.ttl, messageOwner.media.ttl_seconds);
            return ttl > 0 && ((messageOwner.media instanceof TLRPC.TL_messageMediaPhoto || isVideo() || isGif()) && ttl <= 60 || isRoundVideo());
        } else if (messageOwner instanceof TLRPC.TL_message) {
            return (messageOwner.media != null && messageOwner.media.ttl_seconds != 0) && (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto || messageOwner.media instanceof TLRPC.TL_messageMediaDocument);
        }
        return false;
    }

    public boolean isSecretMedia() {
        if (messageOwner instanceof TLRPC.TL_message_secret) {
            return (((messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) || isGif()) && messageOwner.ttl > 0 && messageOwner.ttl <= 60 || isVoice() || isRoundVideo() || isVideo());
        } else if (messageOwner instanceof TLRPC.TL_message) {
            return (messageOwner.media != null && messageOwner.media.ttl_seconds != 0) && (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto || messageOwner.media instanceof TLRPC.TL_messageMediaDocument);
        }
        return false;
    }

    public static void setUnreadFlags(TLRPC.Message message, int flag) {
        message.unread = (flag & 1) == 0;
        message.media_unread = (flag & 2) == 0;
    }

    public static boolean isUnread(TLRPC.Message message) {
        return message.unread;
    }

    public static boolean isContentUnread(TLRPC.Message message) {
        return message.media_unread;
    }

    public boolean isSavedFromMegagroup() {
        if (messageOwner.fwd_from != null && messageOwner.fwd_from.saved_from_peer != null && messageOwner.fwd_from.saved_from_peer.channel_id != 0) {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(messageOwner.fwd_from.saved_from_peer.channel_id);
            return ChatObject.isMegagroup(chat);
        }
        return false;
    }

    public static boolean isOut(TLRPC.Message message) {
        return message.out;
    }

    public long getDialogId() {
        return getDialogId(messageOwner);
    }

    public boolean canStreamVideo() {
        TLRPC.Document document = getDocument();
        if (document == null || document instanceof TLRPC.TL_documentEncrypted) {
            return false;
        }
        if (SharedConfig.streamAllVideo) {
            return true;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                return attribute.supports_streaming;
            }
        }
        if (SharedConfig.streamMkv && "video/x-matroska".equals(document.mime_type)) {
            return true;
        }
        return false;
    }

    public static long getDialogId(TLRPC.Message message) {
        if (message.dialog_id == 0 && message.peer_id != null) {
            if (message.peer_id.chat_id != 0) {
                message.dialog_id = -message.peer_id.chat_id;
            } else if (message.peer_id.channel_id != 0) {
                message.dialog_id = -message.peer_id.channel_id;
            } else if (message.from_id == null || isOut(message)) {
                message.dialog_id = message.peer_id.user_id;
            } else {
                message.dialog_id = message.from_id.user_id;
            }
        }
        return message.dialog_id;
    }

    public boolean isSending() {
        return messageOwner.send_state == MESSAGE_SEND_STATE_SENDING && messageOwner.id < 0;
    }

    public boolean isEditing() {
        return messageOwner.send_state == MESSAGE_SEND_STATE_EDITING && messageOwner.id > 0;
    }

    public boolean isEditingMedia() {
        if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
            return messageOwner.media.photo.id == 0;
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
            return messageOwner.media.document.dc_id == 0;
        }
        return false;
    }

    public boolean isSendError() {
        return messageOwner.send_state == MESSAGE_SEND_STATE_SEND_ERROR && messageOwner.id < 0 || scheduled && messageOwner.id > 0 && messageOwner.date < ConnectionsManager.getInstance(currentAccount).getCurrentTime() - 60;
    }

    public boolean isSent() {
        return messageOwner.send_state == MESSAGE_SEND_STATE_SENT || messageOwner.id > 0;
    }

    public int getSecretTimeLeft() {
        int secondsLeft = messageOwner.ttl;
        if (messageOwner.destroyTime != 0) {
            secondsLeft = Math.max(0, messageOwner.destroyTime - ConnectionsManager.getInstance(currentAccount).getCurrentTime());
        }
        return secondsLeft;
    }

    public String getSecretTimeString() {
        if (!isSecretMedia()) {
            return null;
        }
        int secondsLeft = getSecretTimeLeft();
        String str;
        if (secondsLeft < 60) {
            str = secondsLeft + "s";
        } else {
            str = secondsLeft / 60 + "m";
        }
        return str;
    }

    public String getDocumentName() {
        return FileLoader.getDocumentFileName(getDocument());
    }

    public static boolean isWebM(TLRPC.Document document) {
        return document != null && "video/webm".equals(document.mime_type);
    }

    public static boolean isVideoSticker(TLRPC.Document document) {
        return document != null && isVideoStickerDocument(document);
    }

    public boolean isVideoSticker() {
        return getDocument() != null && isVideoStickerDocument(getDocument());
    }

    public static boolean isStickerDocument(TLRPC.Document document) {
        if (document != null) {
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    return "image/webp".equals(document.mime_type) || "video/webm".equals(document.mime_type);
                }
            }
        }
        return false;
    }

    public static boolean isVideoStickerDocument(TLRPC.Document document) {
        if (document != null) {
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    return "video/webm".equals(document.mime_type);
                }
            }
        }
        return false;
    }

    public static boolean isStickerHasSet(TLRPC.Document document) {
        if (document != null) {
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeSticker && attribute.stickerset != null && !(attribute.stickerset instanceof TLRPC.TL_inputStickerSetEmpty)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isAnimatedStickerDocument(TLRPC.Document document, boolean allowWithoutSet) {
        if (document != null && ("application/x-tgsticker".equals(document.mime_type) && !document.thumbs.isEmpty() || "application/x-tgsdice".equals(document.mime_type))) {
            if (allowWithoutSet) {
                return true;
            }
            for (int a = 0, N = document.attributes.size(); a < N; a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    return attribute.stickerset instanceof TLRPC.TL_inputStickerSetShortName;
                }
            }
        }
        return false;
    }

    public static boolean canAutoplayAnimatedSticker(TLRPC.Document document) {
        return isAnimatedStickerDocument(document, true) && SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW;
    }

    public static boolean isMaskDocument(TLRPC.Document document) {
        if (document != null) {
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeSticker && attribute.mask) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isVoiceDocument(TLRPC.Document document) {
        if (document != null) {
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                    return attribute.voice;
                }
            }
        }
        return false;
    }

    public static boolean isVoiceWebDocument(WebFile webDocument) {
        return webDocument != null && webDocument.mime_type.equals("audio/ogg");
    }

    public static boolean isImageWebDocument(WebFile webDocument) {
        return webDocument != null && !isGifDocument(webDocument) && webDocument.mime_type.startsWith("image/");
    }

    public static boolean isVideoWebDocument(WebFile webDocument) {
        return webDocument != null && webDocument.mime_type.startsWith("video/");
    }

    public static boolean isMusicDocument(TLRPC.Document document) {
        if (document != null) {
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                    return !attribute.voice;
                }
            }
            if (!TextUtils.isEmpty(document.mime_type)) {
                String mime = document.mime_type.toLowerCase();
                if (mime.equals("audio/flac") || mime.equals("audio/ogg") || mime.equals("audio/opus") || mime.equals("audio/x-opus+ogg")) {
                    return true;
                } else if (mime.equals("application/octet-stream") && FileLoader.getDocumentFileName(document).endsWith(".opus")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static TLRPC.VideoSize getDocumentVideoThumb(TLRPC.Document document) {
        if (document == null || document.video_thumbs.isEmpty()) {
            return null;
        }
        return document.video_thumbs.get(0);
    }

    public static boolean isVideoDocument(TLRPC.Document document) {
        if (document == null) {
            return false;
        }
        boolean isAnimated = false;
        boolean isVideo = false;
        int width = 0;
        int height = 0;
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                if (attribute.round_message) {
                    return false;
                }
                isVideo = true;
                width = attribute.w;
                height = attribute.h;
            } else if (attribute instanceof TLRPC.TL_documentAttributeAnimated) {
                isAnimated = true;
            }
        }
        if (isAnimated && (width > 1280 || height > 1280)) {
            isAnimated = false;
        }
        if (SharedConfig.streamMkv && !isVideo && "video/x-matroska".equals(document.mime_type)) {
            isVideo = true;
        }
        return isVideo && !isAnimated;
    }

    public TLRPC.Document getDocument() {
        if (emojiAnimatedSticker != null) {
            return emojiAnimatedSticker;
        }
        return getDocument(messageOwner);
    }

    public static TLRPC.Document getDocument(TLRPC.Message message) {
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return message.media.webpage.document;
        } else if (message.media instanceof TLRPC.TL_messageMediaGame) {
            return message.media.game.document;
        }
        return message.media != null ? message.media.document : null;
    }

    public static TLRPC.Photo getPhoto(TLRPC.Message message) {
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return message.media.webpage.photo;
        }
        return message.media != null ? message.media.photo : null;
    }

    public static boolean isStickerMessage(TLRPC.Message message) {
        return message.media != null && isStickerDocument(message.media.document);
    }

    public static boolean isAnimatedStickerMessage(TLRPC.Message message) {
        boolean isSecretChat = DialogObject.isEncryptedDialog(message.dialog_id);
        if (isSecretChat && message.stickerVerified != 1) {
            return false;
        }
        return message.media != null && isAnimatedStickerDocument(message.media.document, !isSecretChat || message.out);
    }

    public static boolean isLocationMessage(TLRPC.Message message) {
        return message.media instanceof TLRPC.TL_messageMediaGeo || message.media instanceof TLRPC.TL_messageMediaGeoLive || message.media instanceof TLRPC.TL_messageMediaVenue;
    }

    public static boolean isMaskMessage(TLRPC.Message message) {
        return message.media != null && isMaskDocument(message.media.document);
    }

    public static boolean isMusicMessage(TLRPC.Message message) {
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return isMusicDocument(message.media.webpage.document);
        }
        return message.media != null && isMusicDocument(message.media.document);
    }

    public static boolean isGifMessage(TLRPC.Message message) {
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return isGifDocument(message.media.webpage.document);
        }
        return message.media != null && isGifDocument(message.media.document, message.grouped_id != 0);
    }

    public static boolean isRoundVideoMessage(TLRPC.Message message) {
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return isRoundVideoDocument(message.media.webpage.document);
        }
        return message.media != null && isRoundVideoDocument(message.media.document);
    }

    public static boolean isPhoto(TLRPC.Message message) {
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return message.media.webpage.photo instanceof TLRPC.TL_photo && !(message.media.webpage.document instanceof TLRPC.TL_document);
        }
        return message.media instanceof TLRPC.TL_messageMediaPhoto;
    }

    public static boolean isVoiceMessage(TLRPC.Message message) {
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return isVoiceDocument(message.media.webpage.document);
        }
        return message.media != null && isVoiceDocument(message.media.document);
    }

    public static boolean isNewGifMessage(TLRPC.Message message) {
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return isNewGifDocument(message.media.webpage.document);
        }
        return message.media != null && isNewGifDocument(message.media.document);
    }

    public static boolean isLiveLocationMessage(TLRPC.Message message) {
        return message.media instanceof TLRPC.TL_messageMediaGeoLive;
    }

    public static boolean isVideoMessage(TLRPC.Message message) {
        if (message.media != null && isVideoSticker(message.media.document)) {
            return false;
        }
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return isVideoDocument(message.media.webpage.document);
        }
        return message.media != null && isVideoDocument(message.media.document);
    }

    public static boolean isGameMessage(TLRPC.Message message) {
        return message.media instanceof TLRPC.TL_messageMediaGame;
    }

    public static boolean isInvoiceMessage(TLRPC.Message message) {
        return message.media instanceof TLRPC.TL_messageMediaInvoice;
    }

    public static TLRPC.InputStickerSet getInputStickerSet(TLRPC.Message message) {
        TLRPC.Document document = getDocument(message);
        if (document != null) {
            return getInputStickerSet(document);
        }
        return null;
    }

    public static TLRPC.InputStickerSet getInputStickerSet(TLRPC.Document document) {
        if (document == null) {
            return null;
        }
        for (int a = 0, N = document.attributes.size(); a < N; a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                if (attribute.stickerset instanceof TLRPC.TL_inputStickerSetEmpty) {
                    return null;
                }
                return attribute.stickerset;
            }
        }
        return null;
    }

    public static long getStickerSetId(TLRPC.Document document) {
        if (document == null) {
            return -1;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                if (attribute.stickerset instanceof TLRPC.TL_inputStickerSetEmpty) {
                    return -1;
                }
                return attribute.stickerset.id;
            }
        }
        return -1;
    }

    public static String getStickerSetName(TLRPC.Document document) {
        if (document == null) {
            return null;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                if (attribute.stickerset instanceof TLRPC.TL_inputStickerSetEmpty) {
                    return null;
                }
                return attribute.stickerset.short_name;
            }
        }
        return null;
    }

    public String getStickerChar() {
        TLRPC.Document document = getDocument();
        if (document != null) {
            for (TLRPC.DocumentAttribute attribute : document.attributes) {
                if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    return attribute.alt;
                }
            }
        }
        return null;
    }

    public int getApproximateHeight() {
        if (type == 0) {
            int height = textHeight + (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageOwner.media.webpage instanceof TLRPC.TL_webPage ? AndroidUtilities.dp(100) : 0);
            if (isReply()) {
                height += AndroidUtilities.dp(42);
            }
            return height;
        } else if (type == 2) {
            return AndroidUtilities.dp(72);
        } else if (type == 12) {
            return AndroidUtilities.dp(71);
        } else if (type == 9) {
            return AndroidUtilities.dp(100);
        } else if (type == 4) {
            return AndroidUtilities.dp(114);
        } else if (type == 14) {
            return AndroidUtilities.dp(82);
        } else if (type == 10) {
            return AndroidUtilities.dp(30);
        } else if (type == 11) {
            return AndroidUtilities.dp(50);
        } else if (type == TYPE_ROUND_VIDEO) {
            return AndroidUtilities.roundMessageSize;
        } else if (type == TYPE_STICKER || type == TYPE_ANIMATED_STICKER) {
            float maxHeight = AndroidUtilities.displaySize.y * 0.4f;
            float maxWidth;
            if (AndroidUtilities.isTablet()) {
                maxWidth = AndroidUtilities.getMinTabletSide() * 0.5f;
            } else {
                maxWidth = AndroidUtilities.displaySize.x * 0.5f;
            }
            int photoHeight = 0;
            int photoWidth = 0;
            TLRPC.Document document = getDocument();
            for (int a = 0, N = document.attributes.size(); a < N; a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                    photoWidth = attribute.w;
                    photoHeight = attribute.h;
                    break;
                }
            }
            if (photoWidth == 0) {
                photoHeight = (int) maxHeight;
                photoWidth = photoHeight + AndroidUtilities.dp(100);
            }
            if (photoHeight > maxHeight) {
                photoWidth *= maxHeight / photoHeight;
                photoHeight = (int) maxHeight;
            }
            if (photoWidth > maxWidth) {
                photoHeight *= maxWidth / photoWidth;
            }
            return photoHeight + AndroidUtilities.dp(14);
        } else {
            int photoHeight;
            int photoWidth;

            if (AndroidUtilities.isTablet()) {
                photoWidth = (int) (AndroidUtilities.getMinTabletSide() * 0.7f);
            } else {
                photoWidth = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.7f);
            }
            photoHeight = photoWidth + AndroidUtilities.dp(100);
            if (photoWidth > AndroidUtilities.getPhotoSize()) {
                photoWidth = AndroidUtilities.getPhotoSize();
            }
            if (photoHeight > AndroidUtilities.getPhotoSize()) {
                photoHeight = AndroidUtilities.getPhotoSize();
            }
            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, AndroidUtilities.getPhotoSize());

            if (currentPhotoObject != null) {
                float scale = (float) currentPhotoObject.w / (float) photoWidth;
                int h = (int) (currentPhotoObject.h / scale);
                if (h == 0) {
                    h = AndroidUtilities.dp(100);
                }
                if (h > photoHeight) {
                    h = photoHeight;
                } else if (h < AndroidUtilities.dp(120)) {
                    h = AndroidUtilities.dp(120);
                }
                if (needDrawBluredPreview()) {
                    if (AndroidUtilities.isTablet()) {
                        h = (int) (AndroidUtilities.getMinTabletSide() * 0.5f);
                    } else {
                        h = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f);
                    }
                }
                photoHeight = h;
            }
            return photoHeight + AndroidUtilities.dp(14);
        }
    }

    private int getParentWidth() {
        return (preview && parentWidth > 0) ? parentWidth : AndroidUtilities.displaySize.x;
    }

    public String getStickerEmoji() {
        TLRPC.Document document = getDocument();
        if (document == null) {
            return null;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                return attribute.alt != null && attribute.alt.length() > 0 ? attribute.alt : null;
            }
        }
        return null;
    }

    public boolean isVideoCall() {
        return messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall && messageOwner.action.video;
    }

    public boolean isAnimatedEmoji() {
        return emojiAnimatedSticker != null;
    }

    public boolean isDice() {
        return messageOwner.media instanceof TLRPC.TL_messageMediaDice;
    }

    public String getDiceEmoji() {
        if (!isDice()) {
            return null;
        }
        TLRPC.TL_messageMediaDice messageMediaDice = (TLRPC.TL_messageMediaDice) messageOwner.media;
        if (TextUtils.isEmpty(messageMediaDice.emoticon)) {
            return "\uD83C\uDFB2";
        }
        return messageMediaDice.emoticon.replace("\ufe0f", "");
    }

    public int getDiceValue() {
        if (messageOwner.media instanceof TLRPC.TL_messageMediaDice) {
            return ((TLRPC.TL_messageMediaDice) messageOwner.media).value;
        }
        return -1;
    }

    public boolean isSticker() {
        if (type != 1000) {
            return type == TYPE_STICKER;
        }
        return isStickerDocument(getDocument()) || isVideoSticker(getDocument());
    }

    public boolean isAnimatedSticker() {
        if (type != 1000) {
            return type == TYPE_ANIMATED_STICKER;
        }
        boolean isSecretChat = DialogObject.isEncryptedDialog(getDialogId());
        if (isSecretChat && messageOwner.stickerVerified != 1) {
            return false;
        }
        return isAnimatedStickerDocument(getDocument(), emojiAnimatedSticker != null || !isSecretChat || isOut());
    }

    public boolean isAnyKindOfSticker() {
        return type == TYPE_STICKER || type == TYPE_ANIMATED_STICKER;
    }

    public boolean shouldDrawWithoutBackground() {
        return type == TYPE_STICKER || type == TYPE_ANIMATED_STICKER || type == TYPE_ROUND_VIDEO;
    }

    public boolean isLocation() {
        return isLocationMessage(messageOwner);
    }

    public boolean isMask() {
        return isMaskMessage(messageOwner);
    }

    public boolean isMusic() {
        return isMusicMessage(messageOwner);
    }

    public boolean isDocument() {
        return getDocument() != null && !isVideo() && !isMusic() && !isVoice() && !isAnyKindOfSticker();
    }

    public boolean isVoice() {
        return isVoiceMessage(messageOwner);
    }

    public boolean isVideo() {
        return isVideoMessage(messageOwner);
    }

    public boolean isPhoto() {
        return isPhoto(messageOwner);
    }

    public boolean isLiveLocation() {
        return isLiveLocationMessage(messageOwner);
    }

    public boolean isExpiredLiveLocation(int date) {
        return messageOwner.date + messageOwner.media.period <= date;
    }

    public boolean isGame() {
        return isGameMessage(messageOwner);
    }

    public boolean isInvoice() {
        return isInvoiceMessage(messageOwner);
    }

    public boolean isRoundVideo() {
        if (isRoundVideoCached == 0) {
            isRoundVideoCached = type == TYPE_ROUND_VIDEO || isRoundVideoMessage(messageOwner) ? 1 : 2;
        }
        return isRoundVideoCached == 1;
    }

    public boolean shouldAnimateSending() {
        return isSending() && (type == MessageObject.TYPE_ROUND_VIDEO || isVoice() || (isAnyKindOfSticker() && sendAnimationData != null)  || (messageText != null && sendAnimationData != null));
    }

    public boolean hasAttachedStickers() {
        if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
            return messageOwner.media.photo != null && messageOwner.media.photo.has_stickers;
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
            return isDocumentHasAttachedStickers(messageOwner.media.document);
        }
        return false;
    }

    public static boolean isDocumentHasAttachedStickers(TLRPC.Document document) {
        if (document != null) {
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeHasStickers) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isGif() {
        return isGifMessage(messageOwner);
    }

    public boolean isWebpageDocument() {
        return messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageOwner.media.webpage.document != null && !isGifDocument(messageOwner.media.webpage.document);
    }

    public boolean isWebpage() {
        return messageOwner.media instanceof TLRPC.TL_messageMediaWebPage;
    }

    public boolean isNewGif() {
        return messageOwner.media != null && isNewGifDocument(getDocument());
    }

    public boolean isAndroidTheme() {
        if (messageOwner.media != null && messageOwner.media.webpage != null && !messageOwner.media.webpage.attributes.isEmpty()) {
            for (int b = 0, N2 = messageOwner.media.webpage.attributes.size(); b < N2; b++) {
                TLRPC.TL_webPageAttributeTheme attribute = messageOwner.media.webpage.attributes.get(b);
                ArrayList<TLRPC.Document> documents = attribute.documents;
                for (int a = 0, N = documents.size(); a < N; a++) {
                    TLRPC.Document document = documents.get(a);
                    if ("application/x-tgtheme-android".equals(document.mime_type)) {
                        return true;
                    }
                }
                if (attribute.settings != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getMusicTitle() {
        return getMusicTitle(true);
    }

    public String getMusicTitle(boolean unknown) {
        TLRPC.Document document = getDocument();
        if (document != null) {
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                    if (attribute.voice) {
                        if (!unknown) {
                            return null;
                        }
                        return LocaleController.formatDateAudio(messageOwner.date, true);
                    }
                    String title = attribute.title;
                    if (title == null || title.length() == 0) {
                        title = FileLoader.getDocumentFileName(document);
                        if (TextUtils.isEmpty(title) && unknown) {
                            title = LocaleController.getString("AudioUnknownTitle", R.string.AudioUnknownTitle);
                        }
                    }
                    return title;
                } else if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                    if (attribute.round_message) {
                        return LocaleController.formatDateAudio(messageOwner.date, true);
                    }
                }
            }
            String fileName = FileLoader.getDocumentFileName(document);
            if (!TextUtils.isEmpty(fileName)) {
                return fileName;
            }
        }
        return LocaleController.getString("AudioUnknownTitle", R.string.AudioUnknownTitle);
    }

    public int getDuration() {
        TLRPC.Document document = getDocument();
        if (document == null) {
            return 0;
        }
        if (audioPlayerDuration > 0) {
            return audioPlayerDuration;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                return attribute.duration;
            } else if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                return attribute.duration;
            }
        }
        return audioPlayerDuration;
    }

    public String getArtworkUrl(boolean small) {
        TLRPC.Document document = getDocument();
        if (document != null) {
            if ("audio/ogg".equals(document.mime_type)) {
                return null;
            }
            for (int i = 0, N = document.attributes.size(); i < N; i++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(i);
                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                    if (attribute.voice) {
                        return null;
                    } else {
                        String performer = attribute.performer;
                        String title = attribute.title;
                        if (!TextUtils.isEmpty(performer)) {
                            for (int a = 0; a < excludeWords.length; a++) {
                                performer = performer.replace(excludeWords[a], " ");
                            }
                        }
                        if (TextUtils.isEmpty(performer) && TextUtils.isEmpty(title)) {
                            return null;
                        }
                        try {
                            return "athumb://itunes.apple.com/search?term=" + URLEncoder.encode(performer + " - " + title, "UTF-8") + "&entity=song&limit=4" + (small ? "&s=1" : "");
                        } catch (Exception ignore) {

                        }
                    }
                }
            }
        }
        return null;
    }

    public String getMusicAuthor() {
        return getMusicAuthor(true);
    }

    public String getMusicAuthor(boolean unknown) {
        TLRPC.Document document = getDocument();
        if (document != null) {
            boolean isVoice = false;
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                    if (attribute.voice) {
                        isVoice = true;
                    } else {
                        String performer = attribute.performer;
                        if (TextUtils.isEmpty(performer) && unknown) {
                            performer = LocaleController.getString("AudioUnknownArtist", R.string.AudioUnknownArtist);
                        }
                        return performer;
                    }
                } else if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                    if (attribute.round_message) {
                        isVoice = true;
                    }
                }
                if (isVoice) {
                    if (!unknown) {
                        return null;
                    }
                    if (isOutOwner() || messageOwner.fwd_from != null && messageOwner.fwd_from.from_id instanceof TLRPC.TL_peerUser && messageOwner.fwd_from.from_id.user_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                        return LocaleController.getString("FromYou", R.string.FromYou);
                    }
                    TLRPC.User user = null;
                    TLRPC.Chat chat = null;
                    if (messageOwner.fwd_from != null && messageOwner.fwd_from.from_id instanceof TLRPC.TL_peerChannel) {
                        chat = MessagesController.getInstance(currentAccount).getChat(messageOwner.fwd_from.from_id.channel_id);
                    } else if (messageOwner.fwd_from != null && messageOwner.fwd_from.from_id instanceof TLRPC.TL_peerChat) {
                        chat = MessagesController.getInstance(currentAccount).getChat(messageOwner.fwd_from.from_id.chat_id);
                    } else if (messageOwner.fwd_from != null && messageOwner.fwd_from.from_id instanceof TLRPC.TL_peerUser) {
                        user = MessagesController.getInstance(currentAccount).getUser(messageOwner.fwd_from.from_id.user_id);
                    } else if (messageOwner.fwd_from != null && messageOwner.fwd_from.from_name != null) {
                        return messageOwner.fwd_from.from_name;
                    } else if (messageOwner.from_id instanceof TLRPC.TL_peerChat) {
                        chat = MessagesController.getInstance(currentAccount).getChat(messageOwner.from_id.chat_id);
                    } else if (messageOwner.from_id instanceof TLRPC.TL_peerChannel) {
                        chat = MessagesController.getInstance(currentAccount).getChat(messageOwner.from_id.channel_id);
                    } else if (messageOwner.from_id == null && messageOwner.peer_id.channel_id != 0) {
                        chat = MessagesController.getInstance(currentAccount).getChat(messageOwner.peer_id.channel_id);
                    } else {
                        user = MessagesController.getInstance(currentAccount).getUser(messageOwner.from_id.user_id);
                    }
                    if (user != null) {
                        return UserObject.getUserName(user);
                    } else if (chat != null) {
                        return chat.title;
                    }
                }
            }
        }
        return LocaleController.getString("AudioUnknownArtist", R.string.AudioUnknownArtist);
    }

    public TLRPC.InputStickerSet getInputStickerSet() {
        return getInputStickerSet(messageOwner);
    }

    public boolean isForwarded() {
        return isForwardedMessage(messageOwner);
    }

    public boolean needDrawForwarded() {
        return (messageOwner.flags & TLRPC.MESSAGE_FLAG_FWD) != 0 && messageOwner.fwd_from != null && !messageOwner.fwd_from.imported && (messageOwner.fwd_from.saved_from_peer == null || messageOwner.fwd_from.from_id instanceof TLRPC.TL_peerChannel && messageOwner.fwd_from.saved_from_peer.channel_id != messageOwner.fwd_from.from_id.channel_id) && UserConfig.getInstance(currentAccount).getClientUserId() != getDialogId();
    }

    public static boolean isForwardedMessage(TLRPC.Message message) {
        return (message.flags & TLRPC.MESSAGE_FLAG_FWD) != 0 && message.fwd_from != null;
    }

    public boolean isReply() {
        return !(replyMessageObject != null && replyMessageObject.messageOwner instanceof TLRPC.TL_messageEmpty) && messageOwner.reply_to != null && (messageOwner.reply_to.reply_to_msg_id != 0 || messageOwner.reply_to.reply_to_random_id != 0) && (messageOwner.flags & TLRPC.MESSAGE_FLAG_REPLY) != 0;
    }

    public boolean isMediaEmpty() {
        return isMediaEmpty(messageOwner);
    }

    public boolean isMediaEmptyWebpage() {
        return isMediaEmptyWebpage(messageOwner);
    }

    public static boolean isMediaEmpty(TLRPC.Message message) {
        return message == null || message.media == null || message.media instanceof TLRPC.TL_messageMediaEmpty || message.media instanceof TLRPC.TL_messageMediaWebPage;
    }

    public static boolean isMediaEmptyWebpage(TLRPC.Message message) {
        return message == null || message.media == null || message.media instanceof TLRPC.TL_messageMediaEmpty;
    }

    public boolean hasReplies() {
        return messageOwner.replies != null && messageOwner.replies.replies > 0;
    }

    public boolean canViewThread() {
        if (messageOwner.action != null) {
            return false;
        }
        return hasReplies() || replyMessageObject != null && replyMessageObject.messageOwner.replies != null || getReplyTopMsgId() != 0;
    }

    public boolean isComments() {
        return messageOwner.replies != null && messageOwner.replies.comments;
    }

    public boolean isLinkedToChat(long chatId) {
        return messageOwner.replies != null && (chatId == 0 || messageOwner.replies.channel_id == chatId);
    }

    public int getRepliesCount() {
        return messageOwner.replies != null ? messageOwner.replies.replies : 0;
    }

    public boolean canEditMessage(TLRPC.Chat chat) {
        return canEditMessage(currentAccount, messageOwner, chat, scheduled);
    }

    public boolean canEditMessageScheduleTime(TLRPC.Chat chat) {
        return canEditMessageScheduleTime(currentAccount, messageOwner, chat);
    }

    public boolean canForwardMessage() {
        return !(messageOwner instanceof TLRPC.TL_message_secret) && !needDrawBluredPreview() && !isLiveLocation() && type != 16 && !isSponsored() && !messageOwner.noforwards;
    }

    public boolean canEditMedia() {
        if (isSecretMedia()) {
            return false;
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
            return true;
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
            return !isVoice() && !isSticker() && !isAnimatedSticker() && !isRoundVideo();
        }
        return false;
    }

    public boolean canEditMessageAnytime(TLRPC.Chat chat) {
        return canEditMessageAnytime(currentAccount, messageOwner, chat);
    }

    public static boolean canEditMessageAnytime(int currentAccount, TLRPC.Message message, TLRPC.Chat chat) {
        if (message == null || message.peer_id == null || message.media != null && (isRoundVideoDocument(message.media.document) || isStickerDocument(message.media.document) || isAnimatedStickerDocument(message.media.document, true)) || message.action != null && !(message.action instanceof TLRPC.TL_messageActionEmpty) || isForwardedMessage(message) || message.via_bot_id != 0 || message.id < 0) {
            return false;
        }
        if (message.from_id instanceof TLRPC.TL_peerUser && message.from_id.user_id == message.peer_id.user_id && message.from_id.user_id == UserConfig.getInstance(currentAccount).getClientUserId() && !isLiveLocationMessage(message)) {
            return true;
        }
        if (chat == null && message.peer_id.channel_id != 0) {
            chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(message.peer_id.channel_id);
            if (chat == null) {
                return false;
            }
        }
        if (ChatObject.isChannel(chat) && !chat.megagroup && (chat.creator || chat.admin_rights != null && chat.admin_rights.edit_messages)) {
            return true;
        }
        if (message.out && chat != null && chat.megagroup && (chat.creator || chat.admin_rights != null && chat.admin_rights.pin_messages || chat.default_banned_rights != null && !chat.default_banned_rights.pin_messages)) {
            return true;
        }
        //
        return false;
    }

    public static boolean canEditMessageScheduleTime(int currentAccount, TLRPC.Message message, TLRPC.Chat chat) {
        if (chat == null && message.peer_id.channel_id != 0) {
            chat = MessagesController.getInstance(currentAccount).getChat(message.peer_id.channel_id);
            if (chat == null) {
                return false;
            }
        }
        if (!ChatObject.isChannel(chat) || chat.megagroup || chat.creator) {
            return true;
        }
        if (chat.admin_rights != null && (chat.admin_rights.edit_messages || message.out)) {
            return true;
        }
        return false;
    }

    public static boolean canEditMessage(int currentAccount, TLRPC.Message message, TLRPC.Chat chat, boolean scheduled) {
        if (scheduled && message.date < ConnectionsManager.getInstance(currentAccount).getCurrentTime() - 60) {
            return false;
        }
        if (chat != null && (chat.left || chat.kicked) && (!chat.megagroup || !chat.has_link)) {
            return false;
        }
        if (message == null || message.peer_id == null || message.media != null && (isRoundVideoDocument(message.media.document) || isStickerDocument(message.media.document) || isAnimatedStickerDocument(message.media.document, true) || isLocationMessage(message)) || message.action != null && !(message.action instanceof TLRPC.TL_messageActionEmpty) || isForwardedMessage(message) || message.via_bot_id != 0 || message.id < 0) {
            return false;
        }
        if (message.from_id instanceof TLRPC.TL_peerUser && message.from_id.user_id == message.peer_id.user_id && message.from_id.user_id == UserConfig.getInstance(currentAccount).getClientUserId() && !isLiveLocationMessage(message) && !(message.media instanceof TLRPC.TL_messageMediaContact)) {
            return true;
        }
        if (chat == null && message.peer_id.channel_id != 0) {
            chat = MessagesController.getInstance(currentAccount).getChat(message.peer_id.channel_id);
            if (chat == null) {
                return false;
            }
        }
        if (message.media != null && !(message.media instanceof TLRPC.TL_messageMediaEmpty) && !(message.media instanceof TLRPC.TL_messageMediaPhoto) && !(message.media instanceof TLRPC.TL_messageMediaDocument) && !(message.media instanceof TLRPC.TL_messageMediaWebPage)) {
            return false;
        }
        if (ChatObject.isChannel(chat) && !chat.megagroup && (chat.creator || chat.admin_rights != null && chat.admin_rights.edit_messages)) {
            return true;
        }
        if (message.out && chat != null && chat.megagroup && (chat.creator || chat.admin_rights != null && chat.admin_rights.pin_messages || chat.default_banned_rights != null && !chat.default_banned_rights.pin_messages)) {
            return true;
        }
        if (!scheduled && Math.abs(message.date - ConnectionsManager.getInstance(currentAccount).getCurrentTime()) > MessagesController.getInstance(currentAccount).maxEditTime) {
            return false;
        }
        if (message.peer_id.channel_id == 0) {
            return (message.out || message.from_id instanceof TLRPC.TL_peerUser && message.from_id.user_id == UserConfig.getInstance(currentAccount).getClientUserId()) && (message.media instanceof TLRPC.TL_messageMediaPhoto ||
                    message.media instanceof TLRPC.TL_messageMediaDocument && !isStickerMessage(message) && !isAnimatedStickerMessage(message) ||
                    message.media instanceof TLRPC.TL_messageMediaEmpty ||
                    message.media instanceof TLRPC.TL_messageMediaWebPage ||
                    message.media == null);
        }
        if (chat != null && chat.megagroup && message.out || chat != null && !chat.megagroup && (chat.creator || chat.admin_rights != null && (chat.admin_rights.edit_messages || message.out && chat.admin_rights.post_messages)) && message.post) {
            if (message.media instanceof TLRPC.TL_messageMediaPhoto ||
                    message.media instanceof TLRPC.TL_messageMediaDocument && !isStickerMessage(message) && !isAnimatedStickerMessage(message) ||
                    message.media instanceof TLRPC.TL_messageMediaEmpty ||
                    message.media instanceof TLRPC.TL_messageMediaWebPage ||
                    message.media == null) {
                return true;
            }
        }
        return false;
    }

    public boolean canDeleteMessage(boolean inScheduleMode, TLRPC.Chat chat) {
        return eventId == 0 && sponsoredId == null && canDeleteMessage(currentAccount, inScheduleMode, messageOwner, chat);
    }

    public static boolean canDeleteMessage(int currentAccount, boolean inScheduleMode, TLRPC.Message message, TLRPC.Chat chat) {
        if (message == null) {
            return false;
        }
        if (ChatObject.isChannelAndNotMegaGroup(chat) && message.action instanceof TLRPC.TL_messageActionChatJoinedByRequest) {
            return false;
        }
        if (message.id < 0) {
            return true;
        }
        if (chat == null && message.peer_id.channel_id != 0) {
            chat = MessagesController.getInstance(currentAccount).getChat(message.peer_id.channel_id);
        }
        if (ChatObject.isChannel(chat)) {
            if (inScheduleMode && !chat.megagroup) {
                return chat.creator || chat.admin_rights != null && (chat.admin_rights.delete_messages || message.out);
            }
            if (message.out && message instanceof TLRPC.TL_messageService) {
                return message.id != 1 && ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_DELETE_MESSAGES);
            }
            return inScheduleMode || message.id != 1 && (chat.creator || chat.admin_rights != null && (chat.admin_rights.delete_messages || message.out && (chat.megagroup || chat.admin_rights.post_messages)) || chat.megagroup && message.out);
        }
        return inScheduleMode || isOut(message) || !ChatObject.isChannel(chat);
    }

    public String getForwardedName() {
        if (messageOwner.fwd_from != null) {
            if (messageOwner.fwd_from.from_id instanceof TLRPC.TL_peerChannel) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(messageOwner.fwd_from.from_id.channel_id);
                if (chat != null) {
                    return chat.title;
                }
            } else if (messageOwner.fwd_from.from_id instanceof TLRPC.TL_peerChat) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(messageOwner.fwd_from.from_id.chat_id);
                if (chat != null) {
                    return chat.title;
                }
            } else if (messageOwner.fwd_from.from_id instanceof TLRPC.TL_peerUser) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(messageOwner.fwd_from.from_id.user_id);
                if (user != null) {
                    return UserObject.getUserName(user);
                }
            } else if (messageOwner.fwd_from.from_name != null) {
                return messageOwner.fwd_from.from_name;
            }
        }
        return null;
    }

    public int getReplyMsgId() {
        return messageOwner.reply_to != null ? messageOwner.reply_to.reply_to_msg_id : 0;
    }

    public int getReplyTopMsgId() {
        return messageOwner.reply_to != null ? messageOwner.reply_to.reply_to_top_id : 0;
    }

    public static long getReplyToDialogId(TLRPC.Message message) {
        if (message.reply_to == null) {
            return 0;
        }
        if (message.reply_to.reply_to_peer_id != null) {
            return getPeerId(message.reply_to.reply_to_peer_id);
        }
        return MessageObject.getDialogId(message);
    }

    public int getReplyAnyMsgId() {
        if (messageOwner.reply_to != null) {
            if (messageOwner.reply_to.reply_to_top_id != 0) {
                return messageOwner.reply_to.reply_to_top_id;
            } else {
                return messageOwner.reply_to.reply_to_msg_id;
            }
        }
        return 0;
    }

    public boolean isPrivateForward() {
        return messageOwner.fwd_from != null && !TextUtils.isEmpty(messageOwner.fwd_from.from_name);
    }

    public boolean isImportedForward() {
        return messageOwner.fwd_from != null && messageOwner.fwd_from.imported;
    }

    public long getSenderId() {
        if (messageOwner.fwd_from != null && messageOwner.fwd_from.saved_from_peer != null) {
            if (messageOwner.fwd_from.saved_from_peer.user_id != 0) {
                if (messageOwner.fwd_from.from_id instanceof TLRPC.TL_peerUser) {
                    return messageOwner.fwd_from.from_id.user_id;
                } else {
                    return messageOwner.fwd_from.saved_from_peer.user_id;
                }
            } else if (messageOwner.fwd_from.saved_from_peer.channel_id != 0) {
                if (isSavedFromMegagroup() && messageOwner.fwd_from.from_id instanceof TLRPC.TL_peerUser) {
                    return messageOwner.fwd_from.from_id.user_id;
                } else if (messageOwner.fwd_from.from_id instanceof TLRPC.TL_peerChannel) {
                    return -messageOwner.fwd_from.from_id.channel_id;
                } else if (messageOwner.fwd_from.from_id instanceof TLRPC.TL_peerChat) {
                    return -messageOwner.fwd_from.from_id.chat_id;
                } else {
                    return -messageOwner.fwd_from.saved_from_peer.channel_id;
                }
            } else if (messageOwner.fwd_from.saved_from_peer.chat_id != 0) {
                if (messageOwner.fwd_from.from_id instanceof TLRPC.TL_peerUser) {
                    return messageOwner.fwd_from.from_id.user_id;
                } else if (messageOwner.fwd_from.from_id instanceof TLRPC.TL_peerChannel) {
                    return -messageOwner.fwd_from.from_id.channel_id;
                } else if (messageOwner.fwd_from.from_id instanceof TLRPC.TL_peerChat) {
                    return -messageOwner.fwd_from.from_id.chat_id;
                } else {
                    return -messageOwner.fwd_from.saved_from_peer.chat_id;
                }
            }
        } else if (messageOwner.from_id instanceof TLRPC.TL_peerUser) {
            return messageOwner.from_id.user_id;
        } else if (messageOwner.from_id instanceof TLRPC.TL_peerChannel) {
            return -messageOwner.from_id.channel_id;
        } else if (messageOwner.from_id instanceof TLRPC.TL_peerChat) {
            return -messageOwner.from_id.chat_id;
        } else if (messageOwner.post) {
            return messageOwner.peer_id.channel_id;
        }
        return 0;
    }

    public boolean isWallpaper() {
        return messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageOwner.media.webpage != null && "telegram_background".equals(messageOwner.media.webpage.type);
    }

    public boolean isTheme() {
        return messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageOwner.media.webpage != null && "telegram_theme".equals(messageOwner.media.webpage.type);
    }

    public int getMediaExistanceFlags() {
        int flags = 0;
        if (attachPathExists) {
            flags |= 1;
        }
        if (mediaExists) {
            flags |= 2;
        }
        return flags;
    }

    public void applyMediaExistanceFlags(int flags) {
        if (flags == -1) {
            checkMediaExistance();
        } else {
            attachPathExists = (flags & 1) != 0;
            mediaExists = (flags & 2) != 0;
        }
    }

    public void checkMediaExistance() {
        File cacheFile = null;
        attachPathExists = false;
        mediaExists = false;
        if (type == TYPE_PHOTO) {
            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, AndroidUtilities.getPhotoSize());
            if (currentPhotoObject != null) {
                File file = FileLoader.getPathToMessage(messageOwner);
                if (needDrawBluredPreview()) {
                    mediaExists = new File(file.getAbsolutePath() + ".enc").exists();
                }
                if (!mediaExists) {
                    mediaExists = file.exists();
                }
            }
        }
        if (!mediaExists && type == 8 || type == 3 || type == 9 || type == 2 || type == 14 || type == TYPE_ROUND_VIDEO) {
            if (messageOwner.attachPath != null && messageOwner.attachPath.length() > 0) {
                File f = new File(messageOwner.attachPath);
                attachPathExists = f.exists();
            }
            if (!attachPathExists) {
                File file = FileLoader.getPathToMessage(messageOwner);
                if (type == 3 && needDrawBluredPreview()) {
                    mediaExists = new File(file.getAbsolutePath() + ".enc").exists();
                }
                if (!mediaExists) {
                    mediaExists = file.exists();
                }
            }
        }
        if (!mediaExists) {
            TLRPC.Document document = getDocument();
            if (document != null) {
                if (isWallpaper()) {
                    mediaExists = FileLoader.getPathToAttach(document, true).exists();
                } else {
                    mediaExists = FileLoader.getPathToAttach(document).exists();
                }
            } else if (type == 0) {
                TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, AndroidUtilities.getPhotoSize());
                if (currentPhotoObject == null) {
                    return;
                }
                mediaExists = FileLoader.getPathToAttach(currentPhotoObject, true).exists();
            } else if (type == 11) {
                TLRPC.Photo photo = messageOwner.action.photo;
                if (photo == null || photo.video_sizes.isEmpty()) {
                    return;
                }
                mediaExists = FileLoader.getPathToAttach(photo.video_sizes.get(0), true).exists();
            }
        }
    }

    public void setQuery(String query) {
        if (TextUtils.isEmpty(query)) {
            return;
        }
        ArrayList<String> foundWords = new ArrayList<>();
        query = query.trim().toLowerCase();
        String[] queryWord = query.split("\\P{L}+");

        ArrayList<String> searchForWords = new ArrayList<>();
        if (!TextUtils.isEmpty(messageOwner.message)) {
            String message = messageOwner.message.trim().toLowerCase();
            if (message.contains(query) && !foundWords.contains(query)) {
                foundWords.add(query);
                handleFoundWords(foundWords, queryWord);
                return;
            }
            String[] words = message.split("\\P{L}+");
            searchForWords.addAll(Arrays.asList(words));
        }
        if (getDocument() != null) {
            String fileName = FileLoader.getDocumentFileName(getDocument()).toLowerCase();
            if (fileName.contains(query) && !foundWords.contains(query)) {
                foundWords.add(query);
            }
            String[] words = fileName.split("\\P{L}+");
            searchForWords.addAll(Arrays.asList(words));
        }

        if (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageOwner.media.webpage instanceof TLRPC.TL_webPage) {
            TLRPC.WebPage webPage = messageOwner.media.webpage;
            String title = webPage.title;
            if (title == null) {
                title = webPage.site_name;
            }
            if (title != null) {
                title = title.toLowerCase();
                if (title.contains(query) && !foundWords.contains(query)) {
                    foundWords.add(query);
                }
                String[] words = title.split("\\P{L}+");
                searchForWords.addAll(Arrays.asList(words));
            }
        }

        String musicAuthor = getMusicAuthor();
        if (musicAuthor != null) {
            musicAuthor = musicAuthor.toLowerCase();
            if (musicAuthor.contains(query) && !foundWords.contains(query)) {
                foundWords.add(query);
            }
            String[] words = musicAuthor.split("\\P{L}+");
            searchForWords.addAll(Arrays.asList(words));
        }
        for (int k = 0; k < queryWord.length; k++) {
            String currentQuery = queryWord[k];
            if (currentQuery.length() < 2) {
                continue;
            }
            for (int i = 0; i < searchForWords.size(); i++) {
                if (foundWords.contains(searchForWords.get(i))) {
                    continue;
                }
                String word = searchForWords.get(i);
                int startIndex = word.indexOf(currentQuery.charAt(0));
                if (startIndex < 0) {
                    continue;
                }
                int l = Math.max(currentQuery.length(), word.length());
                if (startIndex != 0) {
                    word = word.substring(startIndex);
                }
                int min = Math.min(currentQuery.length(), word.length());
                int count = 0;
                for (int j = 0; j < min; j++) {
                    if (word.charAt(j) == currentQuery.charAt(j)) {
                        count++;
                    } else {
                        break;
                    }
                }
                if (count / (float) l >= 0.5) {
                    foundWords.add(searchForWords.get(i));
                }
            }
        }
        handleFoundWords(foundWords, queryWord);
    }

    private void handleFoundWords(ArrayList<String> foundWords, String[] queryWord) {
        if (!foundWords.isEmpty()) {
            boolean foundExactly = false;
            for (int i = 0; i < foundWords.size(); i++) {
                for (int j = 0; j < queryWord.length; j++) {
                    if (foundWords.get(i).contains(queryWord[j])) {
                        foundExactly = true;
                        break;
                    }
                }
                if (foundExactly) {
                    break;
                }
            }
            if (foundExactly) {
                for (int i = 0; i < foundWords.size(); i++) {
                    boolean findMatch = false;
                    for (int j = 0; j < queryWord.length; j++) {
                        if (foundWords.get(i).contains(queryWord[j])) {
                            findMatch = true;
                            break;
                        }
                    }
                    if (!findMatch) {
                        foundWords.remove(i--);
                    }
                }
                if (foundWords.size() > 0) {
                    Collections.sort(foundWords, (s, s1) -> s1.length() - s.length());
                    String s = foundWords.get(0);
                    foundWords.clear();
                    foundWords.add(s);
                }
            }
            highlightedWords = foundWords;
            if (messageOwner.message != null) {
                String str = messageOwner.message.replace('\n', ' ').replaceAll(" +", " ").trim();
                int lastIndex = str.length();
                int startHighlightedIndex = str.toLowerCase().indexOf(foundWords.get(0));
                int maxSymbols = 200;
                if (startHighlightedIndex < 0) {
                    startHighlightedIndex = 0;
                }
                if (lastIndex > maxSymbols) {
                    int newStart = Math.max(0, startHighlightedIndex - maxSymbols / 2);
                    str = str.substring(newStart, Math.min(lastIndex, startHighlightedIndex - newStart + startHighlightedIndex + maxSymbols / 2));
                }
                messageTrimmedToHighlight = str;
            }
        }
    }

    public void createMediaThumbs() {
        if (isVideo()) {
            TLRPC.Document document = getDocument();
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 50);
            TLRPC.PhotoSize qualityThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
            mediaThumb = ImageLocation.getForDocument(qualityThumb, document);
            mediaSmallThumb = ImageLocation.getForDocument(thumb, document);
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && messageOwner.media.photo != null && !photoThumbs.isEmpty()) {
            TLRPC.PhotoSize currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, 50);
            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, 320, false, currentPhotoObjectThumb, false);
            mediaThumb = ImageLocation.getForObject(currentPhotoObject, photoThumbsObject);
            mediaSmallThumb = ImageLocation.getForObject(currentPhotoObjectThumb, photoThumbsObject);
        }
    }

    public boolean hasHighlightedWords() {
        return highlightedWords != null && !highlightedWords.isEmpty();
    }

    public boolean equals(MessageObject obj) {
        return getId() == obj.getId() && getDialogId() == obj.getDialogId();
    }

    public boolean isReactionsAvailable() {
        return !isEditing() && !isSponsored() && isSent() && messageOwner.action == null;
    }

    public boolean selectReaction(String reaction, boolean fromDoubleTap) {
        if (messageOwner.reactions == null) {
            messageOwner.reactions = new TLRPC.TL_messageReactions();
            messageOwner.reactions.can_see_list = isFromGroup() || isFromUser();
        }

        TLRPC.TL_reactionCount choosenReaction = null;
        TLRPC.TL_reactionCount newReaction = null;
        for (int i = 0; i < messageOwner.reactions.results.size(); i++) {
            if (messageOwner.reactions.results.get(i).chosen) {
                choosenReaction = messageOwner.reactions.results.get(i);
            }
            if (messageOwner.reactions.results.get(i).reaction.equals(reaction)) {
                newReaction = messageOwner.reactions.results.get(i);
            }
        }

        if (choosenReaction != null && (choosenReaction == newReaction || fromDoubleTap)) {
            choosenReaction.chosen = false;
            choosenReaction.count--;
            if (choosenReaction.count <= 0) {
                messageOwner.reactions.results.remove(choosenReaction);
            }
            if (messageOwner.reactions.can_see_list) {
                for (int i = 0; i <  messageOwner.reactions.recent_reactions.size(); i++) {
                    if (MessageObject.getPeerId(messageOwner.reactions.recent_reactions.get(i).peer_id) == UserConfig.getInstance(currentAccount).getClientUserId()) {
                        messageOwner.reactions.recent_reactions.remove(i);
                        i--;
                    }
                }
            }
            reactionsChanged = true;
            return false;
        }

        if (choosenReaction != null) {
            choosenReaction.chosen = false;
            choosenReaction.count--;
            if (choosenReaction.count <= 0) {
                messageOwner.reactions.results.remove(choosenReaction);
            }
            if (messageOwner.reactions.can_see_list) {
                for (int i = 0; i <  messageOwner.reactions.recent_reactions.size(); i++) {
                    if (MessageObject.getPeerId(messageOwner.reactions.recent_reactions.get(i).peer_id) == UserConfig.getInstance(currentAccount).getClientUserId()) {
                        messageOwner.reactions.recent_reactions.remove(i);
                        i--;
                    }
                }
            }
        }
        if (newReaction == null) {
            newReaction = new TLRPC.TL_reactionCount();
            newReaction.reaction = reaction;
            messageOwner.reactions.results.add(newReaction);
        }

        newReaction.chosen = true;
        newReaction.count++;

        if (messageOwner.reactions.can_see_list) {
            TLRPC.TL_messagePeerReaction action = new TLRPC.TL_messagePeerReaction();
            messageOwner.reactions.recent_reactions.add(0, action);
            action.peer_id = new TLRPC.TL_peerUser();
            action.peer_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
            action.reaction = reaction;
        }
        reactionsChanged = true;
        return true;
    }
}
