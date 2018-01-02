/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanBotCommand;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.URLSpanNoUnderlineBold;
import org.telegram.ui.Components.URLSpanReplacement;
import org.telegram.ui.Components.URLSpanUserMention;

import java.io.File;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageObject {

    public static final int MESSAGE_SEND_STATE_SENDING = 1;
    public static final int MESSAGE_SEND_STATE_SENT = 0;
    public static final int MESSAGE_SEND_STATE_SEND_ERROR = 2;

    public long localGroupId;
    public TLRPC.Message messageOwner;
    public CharSequence messageText;
    public CharSequence linkDescription;
    public CharSequence caption;
    public MessageObject replyMessageObject;
    public int type = 1000;
    private int isRoundVideoCached;
    public long eventId;
    public int contentType;
    public String dateKey;
    public String monthKey;
    public boolean deleted;
    public float audioProgress;
    public float gifState;
    public int audioProgressSec;
    public boolean isDateObject;
    public ArrayList<TLRPC.PhotoSize> photoThumbs;
    public ArrayList<TLRPC.PhotoSize> photoThumbs2;
    public VideoEditedInfo videoEditedInfo;
    public boolean viewsReloaded;
    public int wantedBotKeyboardWidth;
    public boolean attachPathExists;
    public boolean mediaExists;
    public boolean resendAsIs;
    public String customReplyName;
    public boolean useCustomPhoto;
    public StringBuilder botButtonsLayout;

    public TLRPC.TL_channelAdminLogEvent currentEvent;

    public boolean forceUpdate;

    public int lastLineWidth;
    public int textWidth;
    public int textHeight;
    public boolean hasRtl;
    public float textXOffset;

    private boolean layoutCreated;
    private int generatedWithMinSize;

    public static Pattern urlPattern;

    public static class TextLayoutBlock {
        public StaticLayout textLayout;
        public float textYOffset;
        public int charactersOffset;
        public int charactersEnd;
        public int height;
        public int heightByOffset;
        public byte directionFlags;

        public boolean isRtl() {
            return (directionFlags & 1) != 0 && (directionFlags & 2) == 0;
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
        public float siblingHeights[];

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
        public ArrayList<MessageObject> messages = new ArrayList<>();
        public ArrayList<GroupedMessagePosition> posArray = new ArrayList<>();
        public HashMap<MessageObject, GroupedMessagePosition> positions = new HashMap<>();

        private int maxSizeWidth = 800;
        private int firstSpanAdditionalSize = 200;

        private class MessageGroupedLayoutAttempt {

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

        private float multiHeight(float array[], int start, int end) {
            float sum = 0;
            for (int a = start; a < end; a++) {
                sum += array[a];
            }
            return maxSizeWidth / sum;
        }

        public void calculate() {
            posArray.clear();
            positions.clear();
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
            hasSibling = false;

            for (int a = 0; a < count; a++) {
                MessageObject messageObject = messages.get(a);
                if (a == 0) {
                    isOut = messageObject.isOutOwner();
                    needShare = !isOut && (
                            messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.saved_from_peer != null ||
                                    messageObject.messageOwner.from_id > 0 && (messageObject.messageOwner.to_id.channel_id != 0 || messageObject.messageOwner.to_id.chat_id != 0 ||
                                            messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGame || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaInvoice)
                    );
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
                        position2.set(0, 0, 1, 1, width, secondHeight, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM);
                        position3.set(1, 1, 1, 1, width, secondHeight, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                        maxX = 1;
                    }
                } else if (count == 4) {
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
                        h = Math.min(maxSizeHeight - h0, h);
                        h /= maxSizeHeight;
                        position2.set(0, 0, 1, 1, w0, h, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM);
                        position3.set(1, 1, 1, 1, w1, h, POSITION_FLAG_BOTTOM);
                        position4.set(2, 2, 1, 1, w2, h, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                        maxX = 2;
                    } else {
                        int w = Math.max(minWidth, Math.round(maxSizeHeight / (1.0f / position2.aspectRatio + 1.0f / position3.aspectRatio + 1.0f / posArray.get(3).aspectRatio)));
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
                float croppedRatios[] = new float[posArray.size()];
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
                        pos.set(k, k, i, i, width, lineHeight / maxSizeHeight, flags);
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
                if (!isOut && messageObject.needDrawAvatar()) {
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
    }

    private static final int LINES_PER_BLOCK = 10;

    public ArrayList<TextLayoutBlock> textLayoutBlocks;

    public MessageObject(TLRPC.Message message, AbstractMap<Integer, TLRPC.User> users, boolean generateLayout) {
        this(message, users, null, generateLayout);
    }

    public MessageObject(TLRPC.Message message, AbstractMap<Integer, TLRPC.User> users, AbstractMap<Integer, TLRPC.Chat> chats, boolean generateLayout) {
        this(message, users, chats, generateLayout, 0);
    }

    public MessageObject(TLRPC.Message message, AbstractMap<Integer, TLRPC.User> users, AbstractMap<Integer, TLRPC.Chat> chats, boolean generateLayout, long eid) {
        Theme.createChatResources(null, true);

        messageOwner = message;
        eventId = eid;

        if (message.replyMessage != null) {
            replyMessageObject = new MessageObject(message.replyMessage, users, chats, false);
        }

        TLRPC.User fromUser = null;
        if (message.from_id > 0) {
            if (users != null) {
                fromUser = users.get(message.from_id);
            }
            if (fromUser == null) {
                fromUser = MessagesController.getInstance().getUser(message.from_id);
            }
        }

        if (message instanceof TLRPC.TL_messageService) {
            if (message.action != null) {
                if (message.action instanceof TLRPC.TL_messageActionCustomAction) {
                    messageText = message.action.message;
                } else if (message.action instanceof TLRPC.TL_messageActionChatCreate) {
                    if (isOut()) {
                        messageText = LocaleController.getString("ActionYouCreateGroup", R.string.ActionYouCreateGroup);
                    } else {
                        messageText = replaceWithLink(LocaleController.getString("ActionCreateGroup", R.string.ActionCreateGroup), "un1", fromUser);
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                    if (message.action.user_id == message.from_id) {
                        if (isOut()) {
                            messageText = LocaleController.getString("ActionYouLeftUser", R.string.ActionYouLeftUser);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionLeftUser", R.string.ActionLeftUser), "un1", fromUser);
                        }
                    } else {
                        TLRPC.User whoUser = null;
                        if (users != null) {
                            whoUser = users.get(message.action.user_id);
                        }
                        if (whoUser == null) {
                            whoUser = MessagesController.getInstance().getUser(message.action.user_id);
                        }
                        if (isOut()) {
                            messageText = replaceWithLink(LocaleController.getString("ActionYouKickUser", R.string.ActionYouKickUser), "un2", whoUser);
                        } else if (message.action.user_id == UserConfig.getClientUserId()) {
                            messageText = replaceWithLink(LocaleController.getString("ActionKickUserYou", R.string.ActionKickUserYou), "un1", fromUser);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionKickUser", R.string.ActionKickUser), "un2", whoUser);
                            messageText = replaceWithLink(messageText, "un1", fromUser);
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatAddUser) {
                    int singleUserId = messageOwner.action.user_id;
                    if (singleUserId == 0 && messageOwner.action.users.size() == 1) {
                        singleUserId = messageOwner.action.users.get(0);
                    }
                    if (singleUserId != 0) {
                        TLRPC.User whoUser = null;
                        if (users != null) {
                            whoUser = users.get(singleUserId);
                        }
                        if (whoUser == null) {
                            whoUser = MessagesController.getInstance().getUser(singleUserId);
                        }
                        if (singleUserId == message.from_id) {
                            if (message.to_id.channel_id != 0 && !isMegagroup()) {
                                messageText = LocaleController.getString("ChannelJoined", R.string.ChannelJoined);
                            } else {
                                if (message.to_id.channel_id != 0 && isMegagroup()) {
                                    if (singleUserId == UserConfig.getClientUserId()) {
                                        messageText = LocaleController.getString("ChannelMegaJoined", R.string.ChannelMegaJoined);
                                    } else {
                                        messageText = replaceWithLink(LocaleController.getString("ActionAddUserSelfMega", R.string.ActionAddUserSelfMega), "un1", fromUser);
                                    }
                                } else if (isOut()) {
                                    messageText = LocaleController.getString("ActionAddUserSelfYou", R.string.ActionAddUserSelfYou);
                                } else {
                                    messageText = replaceWithLink(LocaleController.getString("ActionAddUserSelf", R.string.ActionAddUserSelf), "un1", fromUser);
                                }
                            }
                        } else {
                            if (isOut()) {
                                messageText = replaceWithLink(LocaleController.getString("ActionYouAddUser", R.string.ActionYouAddUser), "un2", whoUser);
                            } else if (singleUserId == UserConfig.getClientUserId()) {
                                if (message.to_id.channel_id != 0) {
                                    if (isMegagroup()) {
                                        messageText = replaceWithLink(LocaleController.getString("MegaAddedBy", R.string.MegaAddedBy), "un1", fromUser);
                                    } else {
                                        messageText = replaceWithLink(LocaleController.getString("ChannelAddedBy", R.string.ChannelAddedBy), "un1", fromUser);
                                    }
                                } else {
                                    messageText = replaceWithLink(LocaleController.getString("ActionAddUserYou", R.string.ActionAddUserYou), "un1", fromUser);
                                }
                            } else {
                                messageText = replaceWithLink(LocaleController.getString("ActionAddUser", R.string.ActionAddUser), "un2", whoUser);
                                messageText = replaceWithLink(messageText, "un1", fromUser);
                            }
                        }
                    } else {
                        if (isOut()) {
                            messageText = replaceWithLink(LocaleController.getString("ActionYouAddUser", R.string.ActionYouAddUser), "un2", message.action.users, users);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionAddUser", R.string.ActionAddUser), "un2", message.action.users, users);
                            messageText = replaceWithLink(messageText, "un1", fromUser);
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatJoinedByLink) {
                    if (isOut()) {
                        messageText = LocaleController.getString("ActionInviteYou", R.string.ActionInviteYou);
                    } else {
                        messageText = replaceWithLink(LocaleController.getString("ActionInviteUser", R.string.ActionInviteUser), "un1", fromUser);
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatEditPhoto) {
                    if (message.to_id.channel_id != 0 && !isMegagroup()) {
                        messageText = LocaleController.getString("ActionChannelChangedPhoto", R.string.ActionChannelChangedPhoto);
                    } else {
                        if (isOut()) {
                            messageText = LocaleController.getString("ActionYouChangedPhoto", R.string.ActionYouChangedPhoto);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionChangedPhoto", R.string.ActionChangedPhoto), "un1", fromUser);
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatEditTitle) {
                    if (message.to_id.channel_id != 0 && !isMegagroup()) {
                        messageText = LocaleController.getString("ActionChannelChangedTitle", R.string.ActionChannelChangedTitle).replace("un2", message.action.title);
                    } else {
                        if (isOut()) {
                            messageText = LocaleController.getString("ActionYouChangedTitle", R.string.ActionYouChangedTitle).replace("un2", message.action.title);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionChangedTitle", R.string.ActionChangedTitle).replace("un2", message.action.title), "un1", fromUser);
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
                    if (message.to_id.channel_id != 0 && !isMegagroup()) {
                        messageText = LocaleController.getString("ActionChannelRemovedPhoto", R.string.ActionChannelRemovedPhoto);
                    } else {
                        if (isOut()) {
                            messageText = LocaleController.getString("ActionYouRemovedPhoto", R.string.ActionYouRemovedPhoto);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionRemovedPhoto", R.string.ActionRemovedPhoto), "un1", fromUser);
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionTTLChange) {
                    if (message.action.ttl != 0) {
                        if (isOut()) {
                            messageText = LocaleController.formatString("MessageLifetimeChangedOutgoing", R.string.MessageLifetimeChangedOutgoing, LocaleController.formatTTLString(message.action.ttl));
                        } else {
                            messageText = LocaleController.formatString("MessageLifetimeChanged", R.string.MessageLifetimeChanged, UserObject.getFirstName(fromUser), LocaleController.formatTTLString(message.action.ttl));
                        }
                    } else {
                        if (isOut()) {
                            messageText = LocaleController.getString("MessageLifetimeYouRemoved", R.string.MessageLifetimeYouRemoved);
                        } else {
                            messageText = LocaleController.formatString("MessageLifetimeRemoved", R.string.MessageLifetimeRemoved, UserObject.getFirstName(fromUser));
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                    String date;
                    long time = ((long) message.date) * 1000;
                    if (LocaleController.getInstance().formatterDay != null && LocaleController.getInstance().formatterYear != null) {
                        date = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().formatterYear.format(time), LocaleController.getInstance().formatterDay.format(time));
                    } else {
                        date = "" + message.date;
                    }
                    TLRPC.User to_user = UserConfig.getCurrentUser();
                    if (to_user == null) {
                        if (users != null) {
                            to_user = users.get(messageOwner.to_id.user_id);
                        }
                        if (to_user == null) {
                            to_user = MessagesController.getInstance().getUser(messageOwner.to_id.user_id);
                        }
                    }
                    String name = to_user != null ? UserObject.getFirstName(to_user) : "";
                    messageText = LocaleController.formatString("NotificationUnrecognizedDevice", R.string.NotificationUnrecognizedDevice, name, date, message.action.title, message.action.address);
                } else if (message.action instanceof TLRPC.TL_messageActionUserJoined) {
                    messageText = LocaleController.formatString("NotificationContactJoined", R.string.NotificationContactJoined, UserObject.getUserName(fromUser));
                } else if (message.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                    messageText = LocaleController.formatString("NotificationContactNewPhoto", R.string.NotificationContactNewPhoto, UserObject.getUserName(fromUser));
                } else if (message.action instanceof TLRPC.TL_messageEncryptedAction) {
                    if (message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionScreenshotMessages) {
                        if (isOut()) {
                            messageText = LocaleController.formatString("ActionTakeScreenshootYou", R.string.ActionTakeScreenshootYou);
                        } else {
                            messageText = replaceWithLink(LocaleController.getString("ActionTakeScreenshoot", R.string.ActionTakeScreenshoot), "un1", fromUser);
                        }
                    } else if (message.action.encryptedAction instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                        TLRPC.TL_decryptedMessageActionSetMessageTTL action = (TLRPC.TL_decryptedMessageActionSetMessageTTL) message.action.encryptedAction;
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
                } else if (message.action instanceof TLRPC.TL_messageActionScreenshotTaken) {
                    if (isOut()) {
                        messageText = LocaleController.formatString("ActionTakeScreenshootYou", R.string.ActionTakeScreenshootYou);
                    } else {
                        messageText = replaceWithLink(LocaleController.getString("ActionTakeScreenshoot", R.string.ActionTakeScreenshoot), "un1", fromUser);
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionCreatedBroadcastList) {
                    messageText = LocaleController.formatString("YouCreatedBroadcastList", R.string.YouCreatedBroadcastList);
                } else if (message.action instanceof TLRPC.TL_messageActionChannelCreate) {
                    if (isMegagroup()) {
                        messageText = LocaleController.getString("ActionCreateMega", R.string.ActionCreateMega);
                    } else {
                        messageText = LocaleController.getString("ActionCreateChannel", R.string.ActionCreateChannel);
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatMigrateTo) {
                    messageText = LocaleController.getString("ActionMigrateFromGroup", R.string.ActionMigrateFromGroup);
                } else if (message.action instanceof TLRPC.TL_messageActionChannelMigrateFrom) {
                    messageText = LocaleController.getString("ActionMigrateFromGroup", R.string.ActionMigrateFromGroup);
                } else if (message.action instanceof TLRPC.TL_messageActionPinMessage) {
                    generatePinMessageText(fromUser, fromUser == null && chats != null ? chats.get(message.to_id.channel_id) : null);
                } else if (message.action instanceof TLRPC.TL_messageActionHistoryClear) {
                    messageText = LocaleController.getString("HistoryCleared", R.string.HistoryCleared);
                } else if (message.action instanceof TLRPC.TL_messageActionGameScore) {
                    generateGameMessageText(fromUser);
                } else if (message.action instanceof TLRPC.TL_messageActionPhoneCall) {
                    TLRPC.TL_messageActionPhoneCall call = (TLRPC.TL_messageActionPhoneCall) messageOwner.action;
                    boolean isMissed = call.reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed;
                    if (messageOwner.from_id == UserConfig.getClientUserId()) {
                        if (isMissed) {
                            messageText = LocaleController.getString("CallMessageOutgoingMissed", R.string.CallMessageOutgoingMissed);
                        }else {
                            messageText = LocaleController.getString("CallMessageOutgoing", R.string.CallMessageOutgoing);
                        }
                    } else {
                        if (isMissed) {
                            messageText = LocaleController.getString("CallMessageIncomingMissed", R.string.CallMessageIncomingMissed);
                        } else if(call.reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy) {
                            messageText = LocaleController.getString("CallMessageIncomingDeclined", R.string.CallMessageIncomingDeclined);
                        } else {
                            messageText = LocaleController.getString("CallMessageIncoming", R.string.CallMessageIncoming);
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
                } else if (message.action instanceof TLRPC.TL_messageActionPaymentSent) {
                    TLRPC.User user = null;
                    int uid = (int) getDialogId();
                    if (users != null) {
                        fromUser = users.get(uid);
                    }
                    if (fromUser == null) {
                        fromUser = MessagesController.getInstance().getUser(uid);
                    }
                    generatePaymentSentMessageText(user);
                }
            }
        } else if (!isMediaEmpty()) {
            if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
                messageText = LocaleController.getString("AttachPhoto", R.string.AttachPhoto);
            } else if (isVideo() || message.media instanceof TLRPC.TL_messageMediaDocument && message.media.document instanceof TLRPC.TL_documentEmpty && message.media.ttl_seconds != 0) {
                messageText = LocaleController.getString("AttachVideo", R.string.AttachVideo);
            } else if (isVoice()) {
                messageText = LocaleController.getString("AttachAudio", R.string.AttachAudio);
            } else if (isRoundVideo()) {
                messageText = LocaleController.getString("AttachRound", R.string.AttachRound);
            } else if (message.media instanceof TLRPC.TL_messageMediaGeo || message.media instanceof TLRPC.TL_messageMediaVenue) {
                messageText = LocaleController.getString("AttachLocation", R.string.AttachLocation);
            } else if (message.media instanceof TLRPC.TL_messageMediaGeoLive) {
                messageText = LocaleController.getString("AttachLiveLocation", R.string.AttachLiveLocation);
            } else if (message.media instanceof TLRPC.TL_messageMediaContact) {
                messageText = LocaleController.getString("AttachContact", R.string.AttachContact);
            } else if (message.media instanceof TLRPC.TL_messageMediaGame) {
                messageText = message.message;
            } else if (message.media instanceof TLRPC.TL_messageMediaInvoice) {
                messageText = message.media.description;
            } else if (message.media instanceof TLRPC.TL_messageMediaUnsupported) {
                messageText = LocaleController.getString("UnsupportedMedia", R.string.UnsupportedMedia);
            } else if (message.media instanceof TLRPC.TL_messageMediaDocument) {
                if (isSticker()) {
                    String sch = getStrickerChar();
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
                    String name = FileLoader.getDocumentFileName(message.media.document);
                    if (name != null && name.length() > 0) {
                        messageText = name;
                    } else {
                        messageText = LocaleController.getString("AttachDocument", R.string.AttachDocument);
                    }
                }
            }
        } else {
            messageText = message.message;
        }
        if (messageText == null) {
            messageText = "";
        }

        setType();
        measureInlineBotButtons();

        Calendar rightNow = new GregorianCalendar();
        rightNow.setTimeInMillis((long) (messageOwner.date) * 1000);
        int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
        int dateYear = rightNow.get(Calendar.YEAR);
        int dateMonth = rightNow.get(Calendar.MONTH);
        dateKey = String.format("%d_%02d_%02d", dateYear, dateMonth, dateDay);
        monthKey = String.format("%d_%02d", dateYear, dateMonth);

        if (messageOwner.message != null && messageOwner.id < 0 && messageOwner.message.length() > 6 && (isVideo() || isNewGif() || isRoundVideo())) {
            videoEditedInfo = new VideoEditedInfo();
            if (!videoEditedInfo.parseString(messageOwner.message)) {
                videoEditedInfo = null;
            } else {
                videoEditedInfo.roundVideo = isRoundVideo();
            }
        }

        generateCaption();
        if (generateLayout) {
            TextPaint paint;
            if (messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                paint = Theme.chat_msgGameTextPaint;
            } else {
                paint = Theme.chat_msgTextPaint;
            }
            int[] emojiOnly = MessagesController.getInstance().allowBigEmoji ? new int[1] : null;
            messageText = Emoji.replaceEmoji(messageText, paint.getFontMetricsInt(), AndroidUtilities.dp(20), false, emojiOnly);
            if (emojiOnly != null && emojiOnly[0] >= 1 && emojiOnly[0] <= 3) {
                TextPaint emojiPaint;
                int size;
                switch (emojiOnly[0]) {
                    case 1:
                        emojiPaint = Theme.chat_msgTextPaintOneEmoji;
                        size = AndroidUtilities.dp(32);
                        break;
                    case 2:
                        emojiPaint = Theme.chat_msgTextPaintTwoEmoji;
                        size = AndroidUtilities.dp(28);
                        break;
                    case 3:
                    default:
                        emojiPaint = Theme.chat_msgTextPaintThreeEmoji;
                        size = AndroidUtilities.dp(24);
                        break;
                }
                Emoji.EmojiSpan[] spans = ((Spannable) messageText).getSpans(0, messageText.length(), Emoji.EmojiSpan.class);
                if (spans != null && spans.length > 0) {
                    for (int a = 0; a < spans.length; a++) {
                        spans[a].replaceFontMetrics(emojiPaint.getFontMetricsInt(), size);
                    }
                }
            }
            generateLayout(fromUser);
        }
        layoutCreated = generateLayout;
        generateThumbs(false);
        checkMediaExistance();
    }

    private void createDateArray(TLRPC.TL_channelAdminLogEvent event, ArrayList<MessageObject> messageObjects, HashMap<String, ArrayList<MessageObject>> messagesByDays) {
        ArrayList<MessageObject> dayArray = messagesByDays.get(dateKey);
        if (dayArray == null) {
            dayArray = new ArrayList<>();
            messagesByDays.put(dateKey, dayArray);
            TLRPC.TL_message dateMsg = new TLRPC.TL_message();
            dateMsg.message = LocaleController.formatDateChat(event.date);
            dateMsg.id = 0;
            dateMsg.date = event.date;
            MessageObject dateObj = new MessageObject(dateMsg, null, false);
            dateObj.type = 10;
            dateObj.contentType = 1;
            dateObj.isDateObject = true;
            messageObjects.add(dateObj);
        }
    }

    public MessageObject(TLRPC.TL_channelAdminLogEvent event, ArrayList<MessageObject> messageObjects, HashMap<String, ArrayList<MessageObject>> messagesByDays, TLRPC.Chat chat, int[] mid) {
        TLRPC.User fromUser = null;
        if (event.user_id > 0) {
            if (fromUser == null) {
                fromUser = MessagesController.getInstance().getUser(event.user_id);
            }
        }
        currentEvent = event;

        Calendar rightNow = new GregorianCalendar();
        rightNow.setTimeInMillis((long) (event.date) * 1000);
        int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
        int dateYear = rightNow.get(Calendar.YEAR);
        int dateMonth = rightNow.get(Calendar.MONTH);
        dateKey = String.format("%d_%02d_%02d", dateYear, dateMonth, dateDay);
        monthKey = String.format("%d_%02d", dateYear, dateMonth);

        TLRPC.Peer to_id = new TLRPC.TL_peerChannel();
        to_id.channel_id = chat.id;

        TLRPC.Message message = null;
        if (event.action instanceof TLRPC.TL_channelAdminLogEventActionChangeTitle) {
            String title = ((TLRPC.TL_channelAdminLogEventActionChangeTitle) event.action).new_value;
            if (chat.megagroup) {
                messageText = replaceWithLink(LocaleController.formatString("EventLogEditedGroupTitle", R.string.EventLogEditedGroupTitle, title), "un1", fromUser);
            } else {
                messageText = replaceWithLink(LocaleController.formatString("EventLogEditedChannelTitle", R.string.EventLogEditedChannelTitle, title), "un1", fromUser);
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionChangePhoto) {
            messageOwner = new TLRPC.TL_messageService();
            if (event.action.new_photo instanceof TLRPC.TL_chatPhotoEmpty) {
                messageOwner.action = new TLRPC.TL_messageActionChatDeletePhoto();
                if (chat.megagroup) {
                    messageText = replaceWithLink(LocaleController.getString("EventLogRemovedWGroupPhoto", R.string.EventLogRemovedWGroupPhoto), "un1", fromUser);
                } else {
                    messageText = replaceWithLink(LocaleController.getString("EventLogRemovedChannelPhoto", R.string.EventLogRemovedChannelPhoto), "un1", fromUser);
                }
            } else {
                messageOwner.action = new TLRPC.TL_messageActionChatEditPhoto();
                messageOwner.action.photo = new TLRPC.TL_photo();
                TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize();
                photoSize.location = event.action.new_photo.photo_small;
                photoSize.type = "s";
                photoSize.w = photoSize.h = 80;
                messageOwner.action.photo.sizes.add(photoSize);
                photoSize = new TLRPC.TL_photoSize();
                photoSize.location = event.action.new_photo.photo_big;
                photoSize.type = "m";
                photoSize.w = photoSize.h = 640;
                messageOwner.action.photo.sizes.add(photoSize);

                if (chat.megagroup) {
                    messageText = replaceWithLink(LocaleController.getString("EventLogEditedGroupPhoto", R.string.EventLogEditedGroupPhoto), "un1", fromUser);
                } else {
                    messageText = replaceWithLink(LocaleController.getString("EventLogEditedChannelPhoto", R.string.EventLogEditedChannelPhoto), "un1", fromUser);
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
            messageOwner = new TLRPC.TL_messageService();
            messageOwner.action = new TLRPC.TL_messageActionChatAddUser();
            TLRPC.User whoUser = MessagesController.getInstance().getUser(event.action.participant.user_id);
            if (event.action.participant.user_id == messageOwner.from_id) {
                if (chat.megagroup) {
                    messageText = replaceWithLink(LocaleController.getString("EventLogGroupJoined", R.string.EventLogGroupJoined), "un1", fromUser);
                } else {
                    messageText = replaceWithLink(LocaleController.getString("EventLogChannelJoined", R.string.EventLogChannelJoined), "un1", fromUser);
                }
            } else {
                messageText = replaceWithLink(LocaleController.getString("EventLogAdded", R.string.EventLogAdded), "un2", whoUser);
                messageText = replaceWithLink(messageText, "un1", fromUser);
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionParticipantToggleAdmin) {
            messageOwner = new TLRPC.TL_message();

            TLRPC.User whoUser = MessagesController.getInstance().getUser(event.action.prev_participant.user_id);
            String str = LocaleController.getString("EventLogPromoted", R.string.EventLogPromoted);
            int offset = str.indexOf("%1$s");
            StringBuilder rights = new StringBuilder(String.format(str, getUserName(whoUser, messageOwner.entities, offset)));
            rights.append("\n");

            TLRPC.TL_channelAdminRights o = event.action.prev_participant.admin_rights;
            TLRPC.TL_channelAdminRights n = event.action.new_participant.admin_rights;
            if (o == null) {
                o = new TLRPC.TL_channelAdminRights();
            }
            if (n == null) {
                n = new TLRPC.TL_channelAdminRights();
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
            if (chat.megagroup) {
                if (o.ban_users != n.ban_users) {
                    rights.append('\n').append(n.ban_users ? '+' : '-').append(' ');
                    rights.append(LocaleController.getString("EventLogPromotedBanUsers", R.string.EventLogPromotedBanUsers));
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
            messageText = rights.toString();
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionParticipantToggleBan) {
            messageOwner = new TLRPC.TL_message();
            TLRPC.User whoUser = MessagesController.getInstance().getUser(event.action.prev_participant.user_id);
            TLRPC.TL_channelBannedRights o = event.action.prev_participant.banned_rights;
            TLRPC.TL_channelBannedRights n = event.action.new_participant.banned_rights;
            if (chat.megagroup && (n == null || !n.view_messages || n != null && o != null && n.until_date != o.until_date)) {
                StringBuilder rights;
                StringBuilder bannedDuration;
                if (n != null && !AndroidUtilities.isBannedForever(n.until_date)) {
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
                    o = new TLRPC.TL_channelBannedRights();
                }
                if (n == null) {
                    n = new TLRPC.TL_channelBannedRights();
                }
                if (o.view_messages != n.view_messages) {
                    if (!added) {
                        rights.append('\n');
                        added = true;
                    }
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
                if (o.embed_links != n.embed_links) {
                    if (!added) {
                        rights.append('\n');
                    }
                    rights.append('\n').append(!n.embed_links ? '+' : '-').append(' ');
                    rights.append(LocaleController.getString("EventLogRestrictedSendEmbed", R.string.EventLogRestrictedSendEmbed));
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
            if (event.action.message instanceof TLRPC.TL_messageEmpty) {
                messageText = replaceWithLink(LocaleController.getString("EventLogUnpinnedMessages", R.string.EventLogUnpinnedMessages), "un1", fromUser);
            } else {
                messageText = replaceWithLink(LocaleController.getString("EventLogPinnedMessages", R.string.EventLogPinnedMessages), "un1", fromUser);
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
            messageText = replaceWithLink(LocaleController.getString("EventLogDeletedMessages", R.string.EventLogDeletedMessages), "un1", fromUser);
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
            message.from_id = event.user_id;
            message.to_id = to_id;
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
            message.from_id = event.user_id;
            message.to_id = to_id;
            message.date = event.date;
            if (!TextUtils.isEmpty(newLink)) {
                message.message = "https://" + MessagesController.getInstance().linkPrefix + "/" + newLink;
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
                message.media.webpage.description = "https://" + MessagesController.getInstance().linkPrefix + "/" + ((TLRPC.TL_channelAdminLogEventActionChangeUsername) event.action).prev_value;
            } else {
                message.media = new TLRPC.TL_messageMediaEmpty();
            }
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionEditMessage) {
            message = new TLRPC.TL_message();
            message.out = false;
            message.unread = false;
            message.from_id = event.user_id;
            message.to_id = to_id;
            message.date = event.date;
            TLRPC.Message newMessage = ((TLRPC.TL_channelAdminLogEventActionEditMessage) event.action).new_message;
            TLRPC.Message oldMessage = ((TLRPC.TL_channelAdminLogEventActionEditMessage) event.action).prev_message;
            if (newMessage.media != null && !(newMessage.media instanceof TLRPC.TL_messageMediaEmpty) && !(newMessage.media instanceof TLRPC.TL_messageMediaWebPage) && TextUtils.isEmpty(newMessage.message)) {
                messageText = replaceWithLink(LocaleController.getString("EventLogEditedCaption", R.string.EventLogEditedCaption), "un1", fromUser);
                message.media = newMessage.media;
                message.media.webpage = new TLRPC.TL_webPage();
                message.media.webpage.site_name = LocaleController.getString("EventLogOriginalCaption", R.string.EventLogOriginalCaption);
                if (TextUtils.isEmpty(oldMessage.media.caption)) {
                    message.media.webpage.description = LocaleController.getString("EventLogOriginalCaptionEmpty", R.string.EventLogOriginalCaptionEmpty);
                } else {
                    message.media.webpage.description = oldMessage.media.caption;
                }
            } else {
                messageText = replaceWithLink(LocaleController.getString("EventLogEditedMessages", R.string.EventLogEditedMessages), "un1", fromUser);
                message.message = newMessage.message;
                message.media = new TLRPC.TL_messageMediaWebPage();
                message.media.webpage = new TLRPC.TL_webPage();
                message.media.webpage.site_name = LocaleController.getString("EventLogOriginalMessages", R.string.EventLogOriginalMessages);
                message.media.webpage.description = oldMessage.message;
            }
            message.reply_markup = newMessage.reply_markup;
            message.media.webpage.flags = 10;
            message.media.webpage.display_url = "";
            message.media.webpage.url = "";
        } else if (event.action instanceof TLRPC.TL_channelAdminLogEventActionChangeStickerSet) {
            TLRPC.InputStickerSet newStickerset = ((TLRPC.TL_channelAdminLogEventActionChangeStickerSet) event.action).new_stickerset;
            TLRPC.InputStickerSet oldStickerset = ((TLRPC.TL_channelAdminLogEventActionChangeStickerSet) event.action).new_stickerset;
            if (newStickerset == null || newStickerset instanceof TLRPC.TL_inputStickerSetEmpty) {
                messageText = replaceWithLink(LocaleController.getString("EventLogRemovedStickersSet", R.string.EventLogRemovedStickersSet), "un1", fromUser);
            } else {
                messageText = replaceWithLink(LocaleController.getString("EventLogChangedStickersSet", R.string.EventLogChangedStickersSet), "un1", fromUser);
            }
        } else {
            messageText = "unsupported " + event.action;
        }
        if (messageOwner == null) {
            messageOwner = new TLRPC.TL_messageService();
        }
        messageOwner.message = messageText.toString();
        messageOwner.from_id = event.user_id;
        messageOwner.date = event.date;
        messageOwner.id = mid[0]++;
        eventId = event.id;
        //messageOwner.out = event.user_id == UserConfig.getClientUserId();
        messageOwner.out = false;
        messageOwner.to_id = new TLRPC.TL_peerChannel();
        messageOwner.to_id.channel_id = chat.id;
        messageOwner.unread = false;
        if (chat.megagroup) {
            messageOwner.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
        }

        if (event.action.message != null && !(event.action.message instanceof TLRPC.TL_messageEmpty)) {
            message = event.action.message;
        }

        if (message != null) {
            message.out = false;
            message.id = mid[0]++;
            message.reply_to_msg_id = 0;
            message.flags = message.flags &~ TLRPC.MESSAGE_FLAG_EDITED;
            if (chat.megagroup) {
                message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
            }
            MessageObject messageObject = new MessageObject(message, null, null, true, eventId);
            if (messageObject.contentType >= 0) {
                createDateArray(event, messageObjects, messagesByDays);
                messageObjects.add(messageObjects.size() - 1, messageObject);
            } else {
                contentType = -1;
            }
        }
        if (contentType >= 0) {
            createDateArray(event, messageObjects, messagesByDays);
            messageObjects.add(messageObjects.size() - 1, this);
        } else {
            return;
        }

        if (messageText == null) {
            messageText = "";
        }

        setType();
        measureInlineBotButtons();

        if (messageOwner.message != null && messageOwner.id < 0 && messageOwner.message.length() > 6 && (isVideo() || isNewGif() || isRoundVideo())) {
            videoEditedInfo = new VideoEditedInfo();
            if (!videoEditedInfo.parseString(messageOwner.message)) {
                videoEditedInfo = null;
            } else {
                videoEditedInfo.roundVideo = isRoundVideo();
            }
        }

        generateCaption();

        TextPaint paint;
        if (messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
            paint = Theme.chat_msgGameTextPaint;
        } else {
            paint = Theme.chat_msgTextPaint;
        }
        int[] emojiOnly = MessagesController.getInstance().allowBigEmoji ? new int[1] : null;
        messageText = Emoji.replaceEmoji(messageText, paint.getFontMetricsInt(), AndroidUtilities.dp(20), false, emojiOnly);
        if (emojiOnly != null && emojiOnly[0] >= 1 && emojiOnly[0] <= 3) {
            TextPaint emojiPaint;
            int size;
            switch (emojiOnly[0]) {
                case 1:
                    emojiPaint = Theme.chat_msgTextPaintOneEmoji;
                    size = AndroidUtilities.dp(32);
                    break;
                case 2:
                    emojiPaint = Theme.chat_msgTextPaintTwoEmoji;
                    size = AndroidUtilities.dp(28);
                    break;
                case 3:
                default:
                    emojiPaint = Theme.chat_msgTextPaintThreeEmoji;
                    size = AndroidUtilities.dp(24);
                    break;
            }
            Emoji.EmojiSpan[] spans = ((Spannable) messageText).getSpans(0, messageText.length(), Emoji.EmojiSpan.class);
            if (spans != null && spans.length > 0) {
                for (int a = 0; a < spans.length; a++) {
                    spans[a].replaceFontMetrics(emojiPaint.getFontMetricsInt(), size);
                }
            }
        }
        generateLayout(fromUser);
        layoutCreated = true;
        generateThumbs(false);
        checkMediaExistance();
    }

    private String getUserName(TLRPC.User user, ArrayList<TLRPC.MessageEntity> entities, int offset) {
        String name;
        if (user == null) {
            name = "";
        } else {
            name = ContactsController.formatName(user.first_name, user.last_name);
        }
        if (offset >= 0) {
            TLRPC.TL_messageEntityMentionName entity = new TLRPC.TL_messageEntityMentionName();
            entity.user_id = user.id;
            entity.offset = offset;
            entity.length = name.length();
            entities.add(entity);
        }
        if (!TextUtils.isEmpty(user.username)) {
            if (offset >= 0) {
                TLRPC.TL_messageEntityMentionName entity = new TLRPC.TL_messageEntityMentionName();
                entity.user_id = user.id;
                entity.offset = offset + name.length() + 2;
                entity.length = user.username.length() + 1;
                entities.add(entity);
            }
            return String.format("%1$s (@%2$s)", name, user.username);
        }
        return name;
    }

    public void applyNewText() {
        if (TextUtils.isEmpty(messageOwner.message)) {
            return;
        }
        TLRPC.User fromUser = null;
        if (isFromUser()) {
            fromUser = MessagesController.getInstance().getUser(messageOwner.from_id);
        }
        messageText = messageOwner.message;
        TextPaint paint;
        if (messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
            paint = Theme.chat_msgGameTextPaint;
        } else {
            paint = Theme.chat_msgTextPaint;
        }
        messageText = Emoji.replaceEmoji(messageText, paint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
        generateLayout(fromUser);
    }

    public void generateGameMessageText(TLRPC.User fromUser) {
        if (fromUser == null) {
            if (messageOwner.from_id > 0) {
                fromUser = MessagesController.getInstance().getUser(messageOwner.from_id);
            }
        }
        TLRPC.TL_game game = null;
        if (replyMessageObject != null && replyMessageObject.messageOwner.media != null && replyMessageObject.messageOwner.media.game != null) {
            game = replyMessageObject.messageOwner.media.game;
        }
        if (game == null) {
            if (fromUser != null && fromUser.id == UserConfig.getClientUserId()) {
                messageText = LocaleController.formatString("ActionYouScored", R.string.ActionYouScored, LocaleController.formatPluralString("Points", messageOwner.action.score));
            } else {
                messageText = replaceWithLink(LocaleController.formatString("ActionUserScored", R.string.ActionUserScored, LocaleController.formatPluralString("Points", messageOwner.action.score)), "un1", fromUser);
            }
        } else {
            if (fromUser != null && fromUser.id == UserConfig.getClientUserId()) {
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
            fromUser = MessagesController.getInstance().getUser((int) getDialogId());
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
            if (messageOwner.from_id > 0) {
                fromUser = MessagesController.getInstance().getUser(messageOwner.from_id);
            }
            if (fromUser == null) {
                chat = MessagesController.getInstance().getChat(messageOwner.to_id.channel_id);
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
            } else if (replyMessageObject.isSticker()) {
                messageText = replaceWithLink(LocaleController.getString("ActionPinnedSticker", R.string.ActionPinnedSticker), "un1", fromUser != null ? fromUser : chat);
            } else if (replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                messageText = replaceWithLink(LocaleController.getString("ActionPinnedFile", R.string.ActionPinnedFile), "un1", fromUser != null ? fromUser : chat);
            } else if (replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo) {
                messageText = replaceWithLink(LocaleController.getString("ActionPinnedGeo", R.string.ActionPinnedGeo), "un1", fromUser != null ? fromUser : chat);
            } else if (replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                messageText = replaceWithLink(LocaleController.getString("ActionPinnedGeoLive", R.string.ActionPinnedGeoLive), "un1", fromUser != null ? fromUser : chat);
            } else if (replyMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                messageText = replaceWithLink(LocaleController.getString("ActionPinnedContact", R.string.ActionPinnedContact), "un1", fromUser != null ? fromUser : chat);
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
                messageText = replaceWithLink(LocaleController.formatString("ActionPinnedText", R.string.ActionPinnedText, mess), "un1", fromUser != null ? fromUser : chat);
            } else {
                messageText = replaceWithLink(LocaleController.getString("ActionPinnedNoText", R.string.ActionPinnedNoText), "un1", fromUser != null ? fromUser : chat);
            }
        }
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

    private MessageObject getMessageObjectForBlock(TLRPC.WebPage webPage, TLRPC.PageBlock pageBlock) {
        TLRPC.TL_message message = null;
        if (pageBlock instanceof TLRPC.TL_pageBlockPhoto) {
            TLRPC.Photo photo = getPhotoWithId(webPage, pageBlock.photo_id);
            if (photo == webPage.photo) {
                return this;
            }
            message = new TLRPC.TL_message();
            message.media = new TLRPC.TL_messageMediaPhoto();
            message.media.photo = photo;
        } else if (pageBlock instanceof TLRPC.TL_pageBlockVideo) {
            TLRPC.Document document = getDocumentWithId(webPage, pageBlock.video_id);
            if (document == webPage.document) {
                return this;
            }
            message = new TLRPC.TL_message();
            message.media = new TLRPC.TL_messageMediaDocument();
            message.media.document = getDocumentWithId(webPage, pageBlock.video_id);
        }
        message.message = "";
        message.id = Utilities.random.nextInt();
        message.date = messageOwner.date;
        message.to_id = messageOwner.to_id;
        message.out = messageOwner.out;
        message.from_id = messageOwner.from_id;
        return new MessageObject(message, null, false);
    }

    public ArrayList<MessageObject> getWebPagePhotos(ArrayList<MessageObject> array, ArrayList<TLRPC.PageBlock> blocksToSearch) {
        TLRPC.WebPage webPage = messageOwner.media.webpage;
        ArrayList<MessageObject> messageObjects = array == null ? new ArrayList<MessageObject>() : array;
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

    public void measureInlineBotButtons() {
        wantedBotKeyboardWidth = 0;
        if (!(messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup)) {
            return;
        }
        Theme.createChatResources(null, true);
        if (botButtonsLayout == null) {
            botButtonsLayout = new StringBuilder();
        } else {
            botButtonsLayout.setLength(0);
        }
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
    }

    public void setType() {
        int oldType = type;
        if (messageOwner instanceof TLRPC.TL_message || messageOwner instanceof TLRPC.TL_messageForwarded_old2) {
            if (isMediaEmpty()) {
                type = 0;
                if (TextUtils.isEmpty(messageText) && eventId == 0) {
                    messageText = "Empty message";
                }
            } else if (messageOwner.media.ttl_seconds != 0 && (messageOwner.media.photo instanceof TLRPC.TL_photoEmpty || messageOwner.media.document instanceof TLRPC.TL_documentEmpty)) {
                contentType = 1;
                type = 10;
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                type = 1;
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaGeo || messageOwner.media instanceof TLRPC.TL_messageMediaVenue || messageOwner.media instanceof TLRPC.TL_messageMediaGeoLive) {
                type = 4;
            } else if (isRoundVideo()) {
                type = 5;
            } else if (isVideo()) {
                type = 3;
            } else if (isVoice()) {
                type = 2;
            } else if (isMusic()) {
                type = 14;
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                type = 12;
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaUnsupported) {
                type = 0;
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                if (messageOwner.media.document != null && messageOwner.media.document.mime_type != null) {
                    if (isGifDocument(messageOwner.media.document)) {
                        type = 8;
                    } else if (messageOwner.media.document.mime_type.equals("image/webp") && isSticker()) {
                        type = 13;
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
            generateThumbs(false);
        }
    }

    public boolean checkLayout() {
        if (type != 0 || messageOwner.to_id == null || messageText == null || messageText.length() == 0) {
            return false;
        }
        if (layoutCreated) {
            int newMinSize = AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() : AndroidUtilities.displaySize.x;
            if (Math.abs(generatedWithMinSize - newMinSize) > AndroidUtilities.dp(52)) {
                layoutCreated = false;
            }
        }
        if (!layoutCreated) {
            layoutCreated = true;
            TLRPC.User fromUser = null;
            if (isFromUser()) {
                fromUser = MessagesController.getInstance().getUser(messageOwner.from_id);
            }
            TextPaint paint;
            if (messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                paint = Theme.chat_msgGameTextPaint;
            } else {
                paint = Theme.chat_msgTextPaint;
            }
            messageText = Emoji.replaceEmoji(messageText, paint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
            generateLayout(fromUser);
            return true;
        }
        return false;
    }

    public String getMimeType() {
        if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
            return messageOwner.media.document.mime_type;
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaInvoice) {
            TLRPC.TL_webDocument photo = ((TLRPC.TL_messageMediaInvoice) messageOwner.media).photo;
            if (photo != null) {
                return photo.mime_type;
            }
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
            return "image/jpeg";
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
            if (messageOwner.media.webpage.document != null) {
                return messageOwner.media.document.mime_type;
            } else if (messageOwner.media.webpage.photo != null) {
                return "image/jpeg";
            }
        }
        return "";
    }

    public static boolean isGifDocument(TLRPC.Document document) {
        return document != null && document.thumb != null && document.mime_type != null && (document.mime_type.equals("image/gif") || isNewGifDocument(document));
    }

    public static boolean isRoundVideoDocument(TLRPC.Document document) {
        if (document != null && document.mime_type != null && document.mime_type.equals("video/mp4")) {
            int width = 0;
            int height = 0;
            boolean round = false;
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                    width = attribute.w;
                    height = attribute.w;
                    round = attribute.round_message;
                }
            }
            if (round && width <= 1280 && height <= 1280) {
                return true;
            }
        }
        return false;
    }

    public static boolean isNewGifDocument(TLRPC.Document document) {
        if (document != null && document.mime_type != null && document.mime_type.equals("video/mp4")) {
            int width = 0;
            int height = 0;
            boolean animated = false;
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeAnimated) {
                    animated = true;
                } else if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                    width = attribute.w;
                    height = attribute.w;
                }
            }
            if (animated && width <= 1280 && height <= 1280) {
                return true;
            }
        }
        return false;
    }

    public void generateThumbs(boolean update) {
        if (messageOwner instanceof TLRPC.TL_messageService) {
            if (messageOwner.action instanceof TLRPC.TL_messageActionChatEditPhoto) {
                if (!update) {
                    photoThumbs = new ArrayList<>(messageOwner.action.photo.sizes);
                } else if (photoThumbs != null && !photoThumbs.isEmpty()) {
                    for (int a = 0; a < photoThumbs.size(); a++) {
                        TLRPC.PhotoSize photoObject = photoThumbs.get(a);
                        for (int b = 0; b < messageOwner.action.photo.sizes.size(); b++) {
                            TLRPC.PhotoSize size = messageOwner.action.photo.sizes.get(b);
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
            }
        } else if (messageOwner.media != null && !(messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)) {
            if (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                if (!update || photoThumbs != null && photoThumbs.size() != messageOwner.media.photo.sizes.size()) {
                    photoThumbs = new ArrayList<>(messageOwner.media.photo.sizes);
                } else if (photoThumbs != null && !photoThumbs.isEmpty()) {
                    for (int a = 0; a < photoThumbs.size(); a++) {
                        TLRPC.PhotoSize photoObject = photoThumbs.get(a);
                        for (int b = 0; b < messageOwner.media.photo.sizes.size(); b++) {
                            TLRPC.PhotoSize size = messageOwner.media.photo.sizes.get(b);
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
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                if (!(messageOwner.media.document.thumb instanceof TLRPC.TL_photoSizeEmpty)) {
                    if (!update) {
                        photoThumbs = new ArrayList<>();
                        photoThumbs.add(messageOwner.media.document.thumb);
                    } else if (photoThumbs != null && !photoThumbs.isEmpty() && messageOwner.media.document.thumb != null) {
                        TLRPC.PhotoSize photoObject = photoThumbs.get(0);
                        photoObject.location = messageOwner.media.document.thumb.location;
                        photoObject.w = messageOwner.media.document.thumb.w;
                        photoObject.h = messageOwner.media.document.thumb.h;
                    }
                }
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
                if (messageOwner.media.game.document != null) {
                    if (!(messageOwner.media.game.document.thumb instanceof TLRPC.TL_photoSizeEmpty)) {
                        if (!update) {
                            photoThumbs = new ArrayList<>();
                            photoThumbs.add(messageOwner.media.game.document.thumb);
                        } else if (photoThumbs != null && !photoThumbs.isEmpty() && messageOwner.media.game.document.thumb != null) {
                            TLRPC.PhotoSize photoObject = photoThumbs.get(0);
                            photoObject.location = messageOwner.media.game.document.thumb.location;
                        }
                    }
                }
                if (messageOwner.media.game.photo != null) {
                    if (!update || photoThumbs2 == null) {
                        photoThumbs2 = new ArrayList<>(messageOwner.media.game.photo.sizes);
                    } else if (!photoThumbs2.isEmpty()) {
                        for (int a = 0; a < photoThumbs2.size(); a++) {
                            TLRPC.PhotoSize photoObject = photoThumbs2.get(a);
                            for (int b = 0; b < messageOwner.media.game.photo.sizes.size(); b++) {
                                TLRPC.PhotoSize size = messageOwner.media.game.photo.sizes.get(b);
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
                }
                if (photoThumbs == null && photoThumbs2 != null) {
                    photoThumbs = photoThumbs2;
                    photoThumbs2 = null;
                }
            } else if (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
                if (messageOwner.media.webpage.photo != null) {
                    if (!update || photoThumbs == null) {
                        photoThumbs = new ArrayList<>(messageOwner.media.webpage.photo.sizes);
                    } else if (!photoThumbs.isEmpty()) {
                        for (int a = 0; a < photoThumbs.size(); a++) {
                            TLRPC.PhotoSize photoObject = photoThumbs.get(a);
                            for (int b = 0; b < messageOwner.media.webpage.photo.sizes.size(); b++) {
                                TLRPC.PhotoSize size = messageOwner.media.webpage.photo.sizes.get(b);
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
                } else if (messageOwner.media.webpage.document != null) {
                    if (!(messageOwner.media.webpage.document.thumb instanceof TLRPC.TL_photoSizeEmpty)) {
                        if (!update) {
                            photoThumbs = new ArrayList<>();
                            photoThumbs.add(messageOwner.media.webpage.document.thumb);
                        } else if (photoThumbs != null && !photoThumbs.isEmpty() && messageOwner.media.webpage.document.thumb != null) {
                            TLRPC.PhotoSize photoObject = photoThumbs.get(0);
                            photoObject.location = messageOwner.media.webpage.document.thumb.location;
                        }
                    }
                }
            }
        }
    }

    public CharSequence replaceWithLink(CharSequence source, String param, ArrayList<Integer> uids, AbstractMap<Integer, TLRPC.User> usersDict) {
        int start = TextUtils.indexOf(source, param);
        if (start >= 0) {
            SpannableStringBuilder names = new SpannableStringBuilder("");
            for (int a = 0; a < uids.size(); a++) {
                TLRPC.User user = null;
                if (usersDict != null) {
                    user = usersDict.get(uids.get(a));
                }
                if (user == null) {
                    user = MessagesController.getInstance().getUser(uids.get(a));
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

    public CharSequence replaceWithLink(CharSequence source, String param, TLObject object) {
        int start = TextUtils.indexOf(source, param);
        if (start >= 0) {
            String name;
            String id;
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
            } else {
                name = "";
                id = "0";
            }
            name = name.replace('\n', ' ');
            SpannableStringBuilder builder = new SpannableStringBuilder(TextUtils.replace(source, new String[]{param}, new String[]{name}));
            builder.setSpan(new URLSpanNoUnderlineBold("" + id), start, start + name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
            ext = messageOwner.media.document.mime_type;
        }
        if (ext == null) {
            ext = "";
        }
        ext = ext.toUpperCase();
        return ext;
    }

    public String getFileName() {
        if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
            return FileLoader.getAttachFileName(messageOwner.media.document);
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

    public int getFileType() {
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
            if ((c == '@' || c == '#' || c == '/') && i == 0 || i != 0 && (message.charAt(i - 1) == ' ' || message.charAt(i - 1) == '\n')) {
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
        if (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageOwner.media.webpage instanceof TLRPC.TL_webPage && messageOwner.media.webpage.description != null) {
            linkDescription = Spannable.Factory.getInstance().newSpannable(messageOwner.media.webpage.description);
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaGame && messageOwner.media.game.description != null) {
            linkDescription = Spannable.Factory.getInstance().newSpannable(messageOwner.media.game.description);
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaInvoice && messageOwner.media.description != null) {
            linkDescription = Spannable.Factory.getInstance().newSpannable(messageOwner.media.description);
        }
        if (linkDescription != null) {
            if (containsUrls(linkDescription)) {
                try {
                    Linkify.addLinks((Spannable) linkDescription, Linkify.WEB_URLS);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            linkDescription = Emoji.replaceEmoji(linkDescription, Theme.chat_msgTextPaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
        }
    }

    public void generateCaption() {
        if (caption != null || isRoundVideo()) {
            return;
        }
        if (messageOwner.media != null && !TextUtils.isEmpty(messageOwner.media.caption)) {
            caption = Emoji.replaceEmoji(messageOwner.media.caption, Theme.chat_msgTextPaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
            if (containsUrls(caption)) {
                try {
                    Linkify.addLinks((Spannable) caption, Linkify.WEB_URLS | Linkify.PHONE_NUMBERS);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                addUsernamesAndHashtags(isOutOwner(), caption, true);
            }
        }
    }

    private static void addUsernamesAndHashtags(boolean isOut, CharSequence charSequence, boolean botCommands) {
        try {
            if (urlPattern == null) {
                urlPattern = Pattern.compile("(^|\\s)/[a-zA-Z@\\d_]{1,255}|(^|\\s)@[a-zA-Z\\d_]{1,32}|(^|\\s)#[\\w\\.]+");
            }
            Matcher matcher = urlPattern.matcher(charSequence);
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                if (charSequence.charAt(start) != '@' && charSequence.charAt(start) != '#' && charSequence.charAt(start) != '/') {
                    start++;
                }
                URLSpanNoUnderline url = null;
                if (charSequence.charAt(start) == '/') {
                    if (botCommands) {
                        url = new URLSpanBotCommand(charSequence.subSequence(start, end).toString(), isOut);
                    }
                } else {
                    url = new URLSpanNoUnderline(charSequence.subSequence(start, end).toString());
                }
                if (url != null) {
                    ((Spannable) charSequence).setSpan(url, start, end, 0);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public boolean hasValidGroupId() {
        return getGroupId() != 0 && photoThumbs != null && !photoThumbs.isEmpty();
    }

    public long getGroupId() {
        return localGroupId != 0 ? localGroupId : messageOwner.grouped_id;
    }

    public static void addLinks(boolean isOut, CharSequence messageText) {
        addLinks(isOut, messageText, true);
    }

    public static void addLinks(boolean isOut, CharSequence messageText, boolean botCommands) {
        if (messageText instanceof Spannable && containsUrls(messageText)) {
            if (messageText.length() < 200) {
                try {
                    Linkify.addLinks((Spannable) messageText, Linkify.WEB_URLS | Linkify.PHONE_NUMBERS);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else {
                try {
                    Linkify.addLinks((Spannable) messageText, Linkify.WEB_URLS);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            addUsernamesAndHashtags(isOut, messageText, botCommands);
        }
    }

    public void generateLayout(TLRPC.User fromUser) {
        if (type != 0 || messageOwner.to_id == null || TextUtils.isEmpty(messageText)) {
            return;
        }

        generateLinkDescription();
        textLayoutBlocks = new ArrayList<>();
        textWidth = 0;

        boolean hasEntities;
        if (messageOwner.send_state != MESSAGE_SEND_STATE_SENT) {
            hasEntities = false;
            for (int a = 0; a < messageOwner.entities.size(); a++) {
                if (!(messageOwner.entities.get(a) instanceof TLRPC.TL_inputMessageEntityMentionName)) {
                    hasEntities = true;
                    break;
                }
            }
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
            addLinks(isOutOwner(), messageText);
        } else {
            if (messageText instanceof Spannable && messageText.length() < 200) {
                try {
                    Linkify.addLinks((Spannable) messageText, Linkify.PHONE_NUMBERS);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        }

        boolean hasUrls = false;
        if (messageText instanceof Spannable) {
            Spannable spannable = (Spannable) messageText;
            int count = messageOwner.entities.size();
            URLSpan[] spans = spannable.getSpans(0, messageText.length(), URLSpan.class);
            if (spans != null && spans.length > 0) {
                hasUrls = true;
            }
            for (int a = 0; a < count; a++) {
                TLRPC.MessageEntity entity = messageOwner.entities.get(a);
                if (entity.length <= 0 || entity.offset < 0 || entity.offset >= messageOwner.message.length()) {
                    continue;
                } else if (entity.offset + entity.length > messageOwner.message.length()) {
                    entity.length = messageOwner.message.length() - entity.offset;
                }
                if (entity instanceof TLRPC.TL_messageEntityBold ||
                        entity instanceof TLRPC.TL_messageEntityItalic ||
                        entity instanceof TLRPC.TL_messageEntityCode ||
                        entity instanceof TLRPC.TL_messageEntityPre ||
                        entity instanceof TLRPC.TL_messageEntityMentionName ||
                        entity instanceof TLRPC.TL_inputMessageEntityMentionName) {
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
                if (entity instanceof TLRPC.TL_messageEntityBold) {
                    spannable.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (entity instanceof TLRPC.TL_messageEntityItalic) {
                    spannable.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/ritalic.ttf")), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (entity instanceof TLRPC.TL_messageEntityCode || entity instanceof TLRPC.TL_messageEntityPre) {
                    spannable.setSpan(new URLSpanMono(spannable, entity.offset, entity.offset + entity.length, isOutOwner()), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (entity instanceof TLRPC.TL_messageEntityMentionName) {
                    spannable.setSpan(new URLSpanUserMention("" + ((TLRPC.TL_messageEntityMentionName) entity).user_id, isOutOwner()), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (entity instanceof TLRPC.TL_inputMessageEntityMentionName) {
                    spannable.setSpan(new URLSpanUserMention("" + ((TLRPC.TL_inputMessageEntityMentionName) entity).user_id.user_id, isOutOwner()), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (!useManualParse) {
                    String url = messageOwner.message.substring(entity.offset, entity.offset + entity.length);
                    if (entity instanceof TLRPC.TL_messageEntityBotCommand) {
                        spannable.setSpan(new URLSpanBotCommand(url, isOutOwner()), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else if (entity instanceof TLRPC.TL_messageEntityHashtag || entity instanceof TLRPC.TL_messageEntityMention) {
                        spannable.setSpan(new URLSpanNoUnderline(url), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else if (entity instanceof TLRPC.TL_messageEntityEmail) {
                        spannable.setSpan(new URLSpanReplacement("mailto:" + url), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else if (entity instanceof TLRPC.TL_messageEntityUrl) {
                        hasUrls = true;
                        if (!url.toLowerCase().startsWith("http") && !url.toLowerCase().startsWith("tg://")) {
                            spannable.setSpan(new URLSpan("http://" + url), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else {
                            spannable.setSpan(new URLSpan(url), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    } else if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                        spannable.setSpan(new URLSpanReplacement(entity.url), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
        }

        int maxWidth;
        boolean needShare = eventId == 0 && !isOutOwner() && (
                messageOwner.fwd_from != null && (messageOwner.fwd_from.saved_from_peer != null || messageOwner.fwd_from.from_id != 0 || messageOwner.fwd_from.channel_id != 0) ||
                messageOwner.from_id > 0 && (messageOwner.to_id.channel_id != 0 || messageOwner.to_id.chat_id != 0 || messageOwner.media instanceof TLRPC.TL_messageMediaGame || messageOwner.media instanceof TLRPC.TL_messageMediaInvoice)
        );
        generatedWithMinSize = AndroidUtilities.isTablet() ? AndroidUtilities.getMinTabletSide() : AndroidUtilities.displaySize.x;
        maxWidth = generatedWithMinSize - AndroidUtilities.dp(needShare || eventId != 0 ? 132 : 80);
        if (fromUser != null && fromUser.bot || (isMegagroup() || messageOwner.fwd_from != null && messageOwner.fwd_from.channel_id != 0) && !isOut()) {
            maxWidth -= AndroidUtilities.dp(20);
        }
        if (messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
            maxWidth -= AndroidUtilities.dp(10);
        }

        StaticLayout textLayout;

        TextPaint paint;
        if (messageOwner.media instanceof TLRPC.TL_messageMediaGame) {
            paint = Theme.chat_msgGameTextPaint;
        } else {
            paint = Theme.chat_msgTextPaint;
        }

        try {
            if (hasUrls && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
        int linesCount = textLayout.getLineCount();

        int blocksCount = (int) Math.ceil((float) linesCount / LINES_PER_BLOCK);
        int linesOffset = 0;
        float prevOffset = 0;

        for (int a = 0; a < blocksCount; a++) {
            int currentBlockLinesCount = Math.min(LINES_PER_BLOCK, linesCount - linesOffset);
            TextLayoutBlock block = new TextLayoutBlock();

            if (blocksCount == 1) {
                block.textLayout = textLayout;
                block.textYOffset = 0;
                block.charactersOffset = 0;
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
                    if (hasUrls && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        block.textLayout = StaticLayout.Builder.obtain(messageText, startCharacter, endCharacter, paint, maxWidth + AndroidUtilities.dp(2))
                                .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                                .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                .build();
                    } else {
                        block.textLayout = new StaticLayout(messageText, startCharacter, endCharacter, paint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
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

            textLayoutBlocks.add(block);

            float lastLeft;
            try {
                lastLeft = block.textLayout.getLineLeft(currentBlockLinesCount - 1);
                if (a == 0) {
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
            int lastLineWidthWithLeft;
            int linesMaxWidthWithLeft;

            if (a == blocksCount - 1) {
                lastLineWidth = linesMaxWidth;
            }

            linesMaxWidthWithLeft = lastLineWidthWithLeft = (int) Math.ceil(lastLine + lastLeft);

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

                    if (lineWidth > maxWidth + 20) {
                        lineWidth = maxWidth;
                    }

                    try {
                        lineLeft = block.textLayout.getLineLeft(n);
                    } catch (Exception e) {
                        FileLog.e(e);
                        lineLeft = 0;
                    }

                    if (lineLeft > 0) {
                        textXOffset = Math.min(textXOffset, lineLeft);
                        block.directionFlags |= 1;
                        hasRtl = true;
                    } else {
                        block.directionFlags |= 2;
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
                    hasRtl = blocksCount != 1;
                    block.directionFlags |= 1;
                } else {
                    block.directionFlags |= 2;
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
        if (!messageOwner.out || messageOwner.from_id <= 0 || messageOwner.post) {
            return false;
        }
        if (messageOwner.fwd_from == null) {
            return true;
        }
        int selfUserId = UserConfig.getClientUserId();
        if (getDialogId() == selfUserId) {
            return messageOwner.fwd_from.from_id == selfUserId || messageOwner.fwd_from.saved_from_peer != null && messageOwner.fwd_from.saved_from_peer.user_id == selfUserId;
        }
        return messageOwner.fwd_from.saved_from_peer == null || messageOwner.fwd_from.saved_from_peer.user_id == selfUserId;
    }

    public boolean needDrawAvatar() {
        return isFromUser() || eventId != 0 || messageOwner.fwd_from != null && messageOwner.fwd_from.saved_from_peer != null;
    }

    public boolean isFromUser() {
        return messageOwner.from_id > 0 && !messageOwner.post;
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

    public static int getMessageSize(TLRPC.Message message) {
        if (message.media != null && message.media.document != null) {
            return message.media.document.size;
        }
        return 0;
    }

    public int getSize() {
        return getMessageSize(messageOwner);
    }

    public long getIdWithChannel() {
        long id = messageOwner.id;
        if (messageOwner.to_id != null && messageOwner.to_id.channel_id != 0) {
            id |= ((long) messageOwner.to_id.channel_id) << 32;
        }
        return id;
    }

    public int getChannelId() {
        if (messageOwner.to_id != null) {
            return messageOwner.to_id.channel_id;
        }
        return 0;
    }

    public static boolean shouldEncryptPhotoOrVideo(TLRPC.Message message) {
        return message instanceof TLRPC.TL_message && (message.media instanceof TLRPC.TL_messageMediaPhoto || message.media instanceof TLRPC.TL_messageMediaDocument) && message.media.ttl_seconds != 0 ||
                message instanceof TLRPC.TL_message_secret && (message.media instanceof TLRPC.TL_messageMediaPhoto || isVideoMessage(message)) && message.ttl > 0 && message.ttl <= 60;
    }

    public boolean shouldEncryptPhotoOrVideo() {
        return shouldEncryptPhotoOrVideo(messageOwner);
    }

    public static boolean isSecretPhotoOrVideo(TLRPC.Message message) {
        return message instanceof TLRPC.TL_message && (message.media instanceof TLRPC.TL_messageMediaPhoto || message.media instanceof TLRPC.TL_messageMediaDocument) && message.media.ttl_seconds != 0 ||
                message instanceof TLRPC.TL_message_secret && (message.media instanceof TLRPC.TL_messageMediaPhoto || isRoundVideoMessage(message) || isVideoMessage(message)) && message.ttl > 0 && message.ttl <= 60;
    }

    public boolean isSecretPhoto() {
        return messageOwner instanceof TLRPC.TL_message && (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto || messageOwner.media instanceof TLRPC.TL_messageMediaDocument) && messageOwner.media.ttl_seconds != 0 ||
                messageOwner instanceof TLRPC.TL_message_secret && ((messageOwner.media instanceof TLRPC.TL_messageMediaPhoto || isVideo()) && messageOwner.ttl > 0 && messageOwner.ttl <= 60 || isRoundVideo());
    }

    public boolean isSecretMedia() {
        return messageOwner instanceof TLRPC.TL_message && (messageOwner.media instanceof TLRPC.TL_messageMediaPhoto || messageOwner.media instanceof TLRPC.TL_messageMediaDocument) && messageOwner.media.ttl_seconds != 0 ||
                messageOwner instanceof TLRPC.TL_message_secret && ((messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) && messageOwner.ttl > 0 && messageOwner.ttl <= 60 || isVoice() || isRoundVideo() || isVideo());
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

    public boolean isMegagroup() {
        return isMegagroup(messageOwner);
    }

    public boolean isSavedFromMegagroup() {
        if (messageOwner.fwd_from != null && messageOwner.fwd_from.saved_from_peer != null && messageOwner.fwd_from.saved_from_peer.channel_id != 0) {
            TLRPC.Chat chat = MessagesController.getInstance().getChat(messageOwner.fwd_from.saved_from_peer.channel_id);
            return ChatObject.isMegagroup(chat);
        }
        return false;
    }

    public static boolean isMegagroup(TLRPC.Message message) {
        return (message.flags & TLRPC.MESSAGE_FLAG_MEGAGROUP) != 0;
    }

    public static boolean isOut(TLRPC.Message message) {
        return message.out;
    }

    public long getDialogId() {
        return getDialogId(messageOwner);
    }

    public static long getDialogId(TLRPC.Message message) {
        if (message.dialog_id == 0 && message.to_id != null) {
            if (message.to_id.chat_id != 0) {
                if (message.to_id.chat_id < 0) {
                    message.dialog_id = AndroidUtilities.makeBroadcastId(message.to_id.chat_id);
                } else {
                    message.dialog_id = -message.to_id.chat_id;
                }
            } else if (message.to_id.channel_id != 0) {
                message.dialog_id = -message.to_id.channel_id;
            } else if (isOut(message)) {
                message.dialog_id = message.to_id.user_id;
            } else {
                message.dialog_id = message.from_id;
            }
        }
        return message.dialog_id;
    }

    public boolean isSending() {
        return messageOwner.send_state == MESSAGE_SEND_STATE_SENDING && messageOwner.id < 0;
    }

    public boolean isSendError() {
        return messageOwner.send_state == MESSAGE_SEND_STATE_SEND_ERROR && messageOwner.id < 0;
    }

    public boolean isSent() {
        return messageOwner.send_state == MESSAGE_SEND_STATE_SENT || messageOwner.id > 0;
    }

    public int getSecretTimeLeft() {
        int secondsLeft = messageOwner.ttl;
        if (messageOwner.destroyTime != 0) {
            secondsLeft = Math.max(0, messageOwner.destroyTime - ConnectionsManager.getInstance().getCurrentTime());
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
        TLRPC.Document document;
        if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
            return FileLoader.getDocumentFileName(messageOwner.media.document);
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
            return FileLoader.getDocumentFileName(messageOwner.media.webpage.document);
        }
        return "";
    }

    public static boolean isStickerDocument(TLRPC.Document document) {
        if (document != null) {
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    return true;
                }
            }
        }
        return false;
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

    public static boolean isVoiceWebDocument(TLRPC.TL_webDocument webDocument) {
        return webDocument != null && webDocument.mime_type.equals("audio/ogg");
    }

    public static boolean isImageWebDocument(TLRPC.TL_webDocument webDocument) {
        return webDocument != null && webDocument.mime_type.startsWith("image/");
    }

    public static boolean isVideoWebDocument(TLRPC.TL_webDocument webDocument) {
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
        }
        return false;
    }

    public static boolean isVideoDocument(TLRPC.Document document) {
        if (document != null) {
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
            return isVideo && !isAnimated;
        }
        return false;
    }

    public TLRPC.Document getDocument() {
        if (messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
            return messageOwner.media.webpage.document;
        }
        return messageOwner.media != null ? messageOwner.media.document : null;
    }

    public static boolean isStickerMessage(TLRPC.Message message) {
        return message.media != null && message.media.document != null && isStickerDocument(message.media.document);
    }

    public static boolean isMaskMessage(TLRPC.Message message) {
        return message.media != null && message.media.document != null && isMaskDocument(message.media.document);
    }

    public static boolean isMusicMessage(TLRPC.Message message) {
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return isMusicDocument(message.media.webpage.document);
        }
        return message.media != null && message.media.document != null && isMusicDocument(message.media.document);
    }

    public static boolean isGifMessage(TLRPC.Message message) {
        return message.media != null && message.media.document != null && isGifDocument(message.media.document);
    }

    public static boolean isRoundVideoMessage(TLRPC.Message message) {
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return isRoundVideoDocument(message.media.webpage.document);
        }
        return message.media != null && message.media.document != null && isRoundVideoDocument(message.media.document);
    }

    public static boolean isPhoto(TLRPC.Message message) {
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return message.media.webpage.photo instanceof TLRPC.TL_photo;
        }
        return message.media instanceof TLRPC.TL_messageMediaPhoto;
    }

    public static boolean isVoiceMessage(TLRPC.Message message) {
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return isVoiceDocument(message.media.webpage.document);
        }
        return message.media != null && message.media.document != null && isVoiceDocument(message.media.document);
    }

    public static boolean isNewGifMessage(TLRPC.Message message) {
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return isNewGifDocument(message.media.webpage.document);
        }
        return message.media != null && message.media.document != null && isNewGifDocument(message.media.document);
    }

    public static boolean isLiveLocationMessage(TLRPC.Message message) {
        return message.media instanceof TLRPC.TL_messageMediaGeoLive;
    }

    public static boolean isVideoMessage(TLRPC.Message message) {
        if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
            return isVideoDocument(message.media.webpage.document);
        }
        return message.media != null && message.media.document != null && isVideoDocument(message.media.document);
    }

    public static boolean isGameMessage(TLRPC.Message message) {
        return message.media instanceof TLRPC.TL_messageMediaGame;
    }

    public static boolean isInvoiceMessage(TLRPC.Message message) {
        return message.media instanceof TLRPC.TL_messageMediaInvoice;
    }

    public static TLRPC.InputStickerSet getInputStickerSet(TLRPC.Message message) {
        if (message.media != null && message.media.document != null) {
            for (TLRPC.DocumentAttribute attribute : message.media.document.attributes) {
                if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    if (attribute.stickerset instanceof TLRPC.TL_inputStickerSetEmpty) {
                        return null;
                    }
                    return attribute.stickerset;
                }
            }
        }
        return null;
    }

    public String getStrickerChar() {
        if (messageOwner.media != null && messageOwner.media.document != null) {
            for (TLRPC.DocumentAttribute attribute : messageOwner.media.document.attributes) {
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
        } else if (type == 5) {
            return AndroidUtilities.roundMessageSize;
        } else if (type == 13) {
            float maxHeight = AndroidUtilities.displaySize.y * 0.4f;
            float maxWidth;
            if (AndroidUtilities.isTablet()) {
                maxWidth = AndroidUtilities.getMinTabletSide() * 0.5f;
            } else {
                maxWidth = AndroidUtilities.displaySize.x * 0.5f;
            }
            int photoHeight = 0;
            int photoWidth = 0;
            for (TLRPC.DocumentAttribute attribute : messageOwner.media.document.attributes) {
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
                photoHeight = (int)maxHeight;
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
                if (isSecretPhoto()) {
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

    public String getStickerEmoji() {
        for (int a = 0; a < messageOwner.media.document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = messageOwner.media.document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                return attribute.alt != null && attribute.alt.length() > 0 ? attribute.alt : null;
            }
        }
        return null;
    }

    public boolean isSticker() {
        if (type != 1000) {
            return type == 13;
        }
        return isStickerMessage(messageOwner);
    }

    public boolean isMask() {
        return isMaskMessage(messageOwner);
    }

    public boolean isMusic() {
        return isMusicMessage(messageOwner);
    }

    public boolean isVoice() {
        return isVoiceMessage(messageOwner);
    }

    public boolean isVideo() {
        return isVideoMessage(messageOwner);
    }

    public boolean isLiveLocation() {
        return isLiveLocationMessage(messageOwner);
    }

    public boolean isGame() {
        return isGameMessage(messageOwner);
    }

    public boolean isInvoice() {
        return isInvoiceMessage(messageOwner);
    }

    public boolean isRoundVideo() {
        if (isRoundVideoCached == 0) {
            isRoundVideoCached = type == 5 || isRoundVideoMessage(messageOwner) ? 1 : 2;
        }
        return isRoundVideoCached == 1;
    }

    public boolean hasPhotoStickers() {
        return messageOwner.media != null && messageOwner.media.photo != null && messageOwner.media.photo.has_stickers;
    }

    public boolean isGif() {
        return isGifMessage(messageOwner);
    }

    public boolean isWebpageDocument() {
        return messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageOwner.media.webpage.document != null && !isGifDocument(messageOwner.media.webpage.document);
    }

    public boolean isNewGif() {
        return messageOwner.media != null && isNewGifDocument(messageOwner.media.document);
    }

    public String getMusicTitle() {
        return getMusicTitle(true);
    }

    public String getMusicTitle(boolean unknown) {
        TLRPC.Document document;
        if (type == 0) {
            document = messageOwner.media.webpage.document;
        } else {
            document = messageOwner.media.document;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                if (attribute.voice) {
                    if (!unknown) {
                        return null;
                    }
                    return LocaleController.formatDateAudio(messageOwner.date);
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
                    return LocaleController.formatDateAudio(messageOwner.date);
                }
            }
        }
        return "";
    }

    public int getDuration() {
        TLRPC.Document document;
        if (type == 0) {
            document = messageOwner.media.webpage.document;
        } else {
            document = messageOwner.media.document;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                return attribute.duration;
            } else if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                return attribute.duration;
            }
        }
        return 0;
    }

    public String getMusicAuthor() {
        return getMusicAuthor(true);
    }

    public String getMusicAuthor(boolean unknown) {
        TLRPC.Document document;
        if (type == 0) {
            document = messageOwner.media.webpage.document;
        } else {
            document = messageOwner.media.document;
        }
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
                if (isOutOwner() || messageOwner.fwd_from != null && messageOwner.fwd_from.from_id == UserConfig.getClientUserId()) {
                    return LocaleController.getString("FromYou", R.string.FromYou);
                }
                TLRPC.User user = null;
                TLRPC.Chat chat = null;
                if (messageOwner.fwd_from != null && messageOwner.fwd_from.channel_id != 0) {
                    chat = MessagesController.getInstance().getChat(messageOwner.fwd_from.channel_id);
                } else if (messageOwner.fwd_from != null && messageOwner.fwd_from.from_id != 0) {
                    user = MessagesController.getInstance().getUser(messageOwner.fwd_from.from_id);
                } else if (messageOwner.from_id < 0) {
                    chat = MessagesController.getInstance().getChat(-messageOwner.from_id);
                } else {
                    user = MessagesController.getInstance().getUser(messageOwner.from_id);
                }
                if (user != null) {
                    return UserObject.getUserName(user);
                } else if (chat != null) {
                    return chat.title;
                }
            }
        }
        return "";
    }

    public TLRPC.InputStickerSet getInputStickerSet() {
        return getInputStickerSet(messageOwner);
    }

    public boolean isForwarded() {
        return isForwardedMessage(messageOwner);
    }

    public boolean needDrawForwarded() {
        return (messageOwner.flags & TLRPC.MESSAGE_FLAG_FWD) != 0 && messageOwner.fwd_from != null && messageOwner.fwd_from.saved_from_peer == null && UserConfig.getClientUserId() != getDialogId();
    }

    public static boolean isForwardedMessage(TLRPC.Message message) {
        return (message.flags & TLRPC.MESSAGE_FLAG_FWD) != 0 && message.fwd_from != null;
    }

    public boolean isReply() {
        return !(replyMessageObject != null && replyMessageObject.messageOwner instanceof TLRPC.TL_messageEmpty) && (messageOwner.reply_to_msg_id != 0 || messageOwner.reply_to_random_id != 0) && (messageOwner.flags & TLRPC.MESSAGE_FLAG_REPLY) != 0;
    }

    public boolean isMediaEmpty() {
        return isMediaEmpty(messageOwner);
    }

    public static boolean isMediaEmpty(TLRPC.Message message) {
        return message == null || message.media == null || message.media instanceof TLRPC.TL_messageMediaEmpty || message.media instanceof TLRPC.TL_messageMediaWebPage;
    }

    public boolean canEditMessage(TLRPC.Chat chat) {
        return canEditMessage(messageOwner, chat);
    }

    public boolean canEditMessageAnytime(TLRPC.Chat chat) {
        return canEditMessageAnytime(messageOwner, chat);
    }

    public static boolean canEditMessageAnytime(TLRPC.Message message, TLRPC.Chat chat) {
        if (message == null || message.to_id == null || message.media != null && (isRoundVideoDocument(message.media.document) || isStickerDocument(message.media.document)) || message.action != null && !(message.action instanceof TLRPC.TL_messageActionEmpty) || isForwardedMessage(message) || message.via_bot_id != 0 || message.id < 0) {
            return false;
        }
        if (message.from_id == message.to_id.user_id && message.from_id == UserConfig.getClientUserId() && !isLiveLocationMessage(message)) {
            return true;
        }
        if (chat == null && message.to_id.channel_id != 0) {
            chat = MessagesController.getInstance().getChat(message.to_id.channel_id);
            if (chat == null) {
                return false;
            }
        }
        if (message.out && chat != null && chat.megagroup && (chat.creator || chat.admin_rights != null && chat.admin_rights.pin_messages)) {
            return true;
        }
        //
        return false;
    }

    public static boolean canEditMessage(TLRPC.Message message, TLRPC.Chat chat) {
        if (message == null || message.to_id == null || message.media != null && (isRoundVideoDocument(message.media.document) || isStickerDocument(message.media.document)) || message.action != null && !(message.action instanceof TLRPC.TL_messageActionEmpty) || isForwardedMessage(message) || message.via_bot_id != 0 || message.id < 0) {
            return false;
        }
        if (message.from_id == message.to_id.user_id && message.from_id == UserConfig.getClientUserId() && !isLiveLocationMessage(message)) {
            return true;
        }
        if (chat == null && message.to_id.channel_id != 0) {
            chat = MessagesController.getInstance().getChat(message.to_id.channel_id);
            if (chat == null) {
                return false;
            }
        }
        if (message.out && chat != null && chat.megagroup && (chat.creator || chat.admin_rights != null && chat.admin_rights.pin_messages)) {
            return true;
        }
        if (Math.abs(message.date - ConnectionsManager.getInstance().getCurrentTime()) > MessagesController.getInstance().maxEditTime) {
            return false;
        }
        if (message.to_id.channel_id == 0) {
            return (message.out || message.from_id == UserConfig.getClientUserId()) && (message.media instanceof TLRPC.TL_messageMediaPhoto ||
                    message.media instanceof TLRPC.TL_messageMediaDocument && !isStickerMessage(message) ||
                    message.media instanceof TLRPC.TL_messageMediaEmpty ||
                    message.media instanceof TLRPC.TL_messageMediaWebPage ||
                    message.media == null);
        }
        if (chat.megagroup && message.out || !chat.megagroup && (chat.creator || chat.admin_rights != null && (chat.admin_rights.edit_messages || message.out)) && message.post) {
            if (message.media instanceof TLRPC.TL_messageMediaPhoto ||
                    message.media instanceof TLRPC.TL_messageMediaDocument && !isStickerMessage(message) ||
                    message.media instanceof TLRPC.TL_messageMediaEmpty ||
                    message.media instanceof TLRPC.TL_messageMediaWebPage ||
                    message.media == null) {
                return true;
            }
        }
        return false;
    }

    public boolean canDeleteMessage(TLRPC.Chat chat) {
        return eventId == 0 && canDeleteMessage(messageOwner, chat);
    }

    public static boolean canDeleteMessage(TLRPC.Message message, TLRPC.Chat chat) {
        if (message.id < 0) {
            return true;
        }
        if (chat == null && message.to_id.channel_id != 0) {
            chat = MessagesController.getInstance().getChat(message.to_id.channel_id);
        }
        if (ChatObject.isChannel(chat)) {
            return message.id != 1 && (chat.creator || chat.admin_rights != null && (chat.admin_rights.delete_messages || message.out) || chat.megagroup && message.out && message.from_id > 0);
        }
        return isOut(message) || !ChatObject.isChannel(chat);
    }

    public String getForwardedName() {
        if (messageOwner.fwd_from != null) {
            if (messageOwner.fwd_from.channel_id != 0) {
                TLRPC.Chat chat = MessagesController.getInstance().getChat(messageOwner.fwd_from.channel_id);
                if (chat != null) {
                    return chat.title;
                }
            } else if (messageOwner.fwd_from.from_id != 0) {
                TLRPC.User user = MessagesController.getInstance().getUser(messageOwner.fwd_from.from_id);
                if (user != null) {
                    return UserObject.getUserName(user);
                }
            }
        }
        return null;
    }

    public int getFromId() {
        if (messageOwner.fwd_from != null && messageOwner.fwd_from.saved_from_peer != null) {
            if (messageOwner.fwd_from.saved_from_peer.user_id != 0) {
                if (messageOwner.fwd_from.from_id != 0) {
                    return messageOwner.fwd_from.from_id;
                } else {
                    return messageOwner.fwd_from.saved_from_peer.user_id;
                }
            } else if (messageOwner.fwd_from.saved_from_peer.channel_id != 0) {
                if (isSavedFromMegagroup() && messageOwner.fwd_from.from_id != 0) {
                    return messageOwner.fwd_from.from_id;
                } else {
                    return -messageOwner.fwd_from.saved_from_peer.channel_id;
                }
            } else if (messageOwner.fwd_from.saved_from_peer.chat_id != 0) {
                if (messageOwner.fwd_from.from_id != 0) {
                    return messageOwner.fwd_from.from_id;
                } else {
                    return -messageOwner.fwd_from.saved_from_peer.chat_id;
                }
            }
        } else if (messageOwner.from_id != 0) {
            return messageOwner.from_id;
        } else if (messageOwner.post) {
            return messageOwner.to_id.channel_id;
        }
        return 0;
    }

    public void checkMediaExistance() {
        File cacheFile = null;
        attachPathExists = false;
        mediaExists = false;
        if (type == 1) {
            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, AndroidUtilities.getPhotoSize());
            if (currentPhotoObject != null) {
                File file = FileLoader.getPathToMessage(messageOwner);
                if (isSecretPhoto()) {
                    mediaExists = new File(file.getAbsolutePath() + ".enc").exists();
                }
                if (!mediaExists) {
                    mediaExists = file.exists();
                }
            }
        } else if (type == 8 || type == 3 || type == 9 || type == 2 || type == 14 || type == 5) {
            if (messageOwner.attachPath != null && messageOwner.attachPath.length() > 0) {
                File f = new File(messageOwner.attachPath);
                attachPathExists = f.exists();
            }
            if (!attachPathExists) {
                File file = FileLoader.getPathToMessage(messageOwner);
                if (type == 3 && isSecretPhoto()) {
                    mediaExists = new File(file.getAbsolutePath() + ".enc").exists();
                }
                if (!mediaExists) {
                    mediaExists = file.exists();
                }
            }
        } else {
            TLRPC.Document document = getDocument();
            if (document != null) {
                mediaExists = FileLoader.getPathToAttach(document).exists();
            } else if (type == 0) {
                TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, AndroidUtilities.getPhotoSize());
                if (currentPhotoObject == null) {
                    return;
                }
                if (currentPhotoObject != null) {
                    mediaExists = FileLoader.getPathToAttach(currentPhotoObject, true).exists();
                }
            }
        }
    }
}
