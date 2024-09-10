/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.graphics.Bitmap;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.SparseArray;

import androidx.annotation.IntDef;
import androidx.collection.LongSparseArray;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.voip.Instance;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.GroupCallActivity;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class ChatObject {

    public static final int CHAT_TYPE_CHAT = 0;
    public static final int CHAT_TYPE_CHANNEL = 2;
    public static final int CHAT_TYPE_USER = 3;
    public static final int CHAT_TYPE_MEGAGROUP = 4;
    public static final int CHAT_TYPE_FORUM = 5;

    public static final int ACTION_PIN = 0;
    public static final int ACTION_CHANGE_INFO = 1;
    public static final int ACTION_BLOCK_USERS = 2;
    public static final int ACTION_INVITE = 3;
    public static final int ACTION_ADD_ADMINS = 4;
    public static final int ACTION_POST = 5;
    public static final int ACTION_SEND = 6;
    public static final int ACTION_SEND_TEXT = 22;
    public static final int ACTION_SEND_MEDIA = 7;
    public static final int ACTION_SEND_STICKERS = 8;
    public static final int ACTION_EMBED_LINKS = 9;
    public static final int ACTION_SEND_POLLS = 10;
    public static final int ACTION_VIEW = 11;
    public static final int ACTION_EDIT_MESSAGES = 12;
    public static final int ACTION_DELETE_MESSAGES = 13;
    public static final int ACTION_MANAGE_CALLS = 14;
    public static final int ACTION_MANAGE_TOPICS = 15;

    public static final int ACTION_SEND_PHOTO = 16;
    public static final int ACTION_SEND_VIDEO = 17;
    public static final int ACTION_SEND_MUSIC = 18;
    public static final int ACTION_SEND_DOCUMENTS = 19;
    public static final int ACTION_SEND_VOICE = 20;
    public static final int ACTION_SEND_ROUND = 21;
    public static final int ACTION_SEND_PLAIN = 22;
    public static final int ACTION_SEND_GIFS = 23;

    public final static int VIDEO_FRAME_NO_FRAME = 0;
    public final static int VIDEO_FRAME_REQUESTING = 1;
    public final static int VIDEO_FRAME_HAS_FRAME = 2;

    private static final int MAX_PARTICIPANTS_COUNT = 5000;

    public static boolean reactionIsAvailable(TLRPC.ChatFull chatInfo, String reaction) {
        if (chatInfo.available_reactions instanceof TLRPC.TL_chatReactionsAll) {
            return true;
        }
        if (chatInfo.available_reactions instanceof TLRPC.TL_chatReactionsSome) {
            TLRPC.TL_chatReactionsSome someReactions = (TLRPC.TL_chatReactionsSome) chatInfo.available_reactions;
            for (int i = 0; i < someReactions.reactions.size(); i++) {
                if (someReactions.reactions.get(i) instanceof TLRPC.TL_reactionEmoji && TextUtils.equals(((TLRPC.TL_reactionEmoji) someReactions.reactions.get(i)).emoticon, reaction)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isForum(int currentAccount, long dialogId) {
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        if (chat != null) {
            return chat.forum;
        }
        return false;
    }

    public static boolean canSendAnyMedia(TLRPC.Chat currentChat) {
        return canSendPhoto(currentChat) || canSendVideo(currentChat) || canSendRoundVideo(currentChat) || canSendVoice(currentChat) || canSendDocument(currentChat) || canSendMusic(currentChat) || canSendStickers(currentChat);
    }

    public static boolean isIgnoredChatRestrictionsForBoosters(TLRPC.ChatFull chatFull) {
        return chatFull != null && chatFull.boosts_unrestrict > 0 && (chatFull.boosts_applied - chatFull.boosts_unrestrict) >= 0;
    }

    public static boolean isIgnoredChatRestrictionsForBoosters(TLRPC.Chat chat) {
        if (chat != null) {
            TLRPC.ChatFull chatFull = MessagesController.getInstance(UserConfig.selectedAccount).getChatFull(chat.id);
            return isIgnoredChatRestrictionsForBoosters(chatFull);
        }
        return false;
    }

    public static boolean isPossibleRemoveChatRestrictionsByBoosts(TLRPC.Chat chat) {
        if (chat != null) {
            TLRPC.ChatFull chatFull = MessagesController.getInstance(UserConfig.selectedAccount).getChatFull(chat.id);
            return isPossibleRemoveChatRestrictionsByBoosts(chatFull);
        }
        return false;
    }

    public static boolean isPossibleRemoveChatRestrictionsByBoosts(TLRPC.ChatFull chatFull) {
        return chatFull != null && chatFull.boosts_unrestrict > 0;
    }

    public static String getAllowedSendString(TLRPC.Chat chat) {
        StringBuilder stringBuilder = new StringBuilder();
        if (ChatObject.canSendPhoto(chat)) {
            stringBuilder.append(LocaleController.getString(R.string.SendMediaPermissionPhotos));
        }
        if (ChatObject.canSendVideo(chat)) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append(LocaleController.getString(R.string.SendMediaPermissionVideos));
        }
        if (ChatObject.canSendStickers(chat)) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append(LocaleController.getString(R.string.SendMediaPermissionStickersGifs));
        }
        if (ChatObject.canSendMusic(chat)) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append(LocaleController.getString(R.string.SendMediaPermissionMusic));
        }
        if (ChatObject.canSendDocument(chat)) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append(LocaleController.getString(R.string.SendMediaPermissionFiles));
        }
        if (ChatObject.canSendVoice(chat)) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append(LocaleController.getString(R.string.SendMediaPermissionVoice));
        }
        if (ChatObject.canSendRoundVideo(chat)) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append(LocaleController.getString(R.string.SendMediaPermissionRound));
        }
        if (ChatObject.canSendEmbed(chat)) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append(LocaleController.getString(R.string.SendMediaEmbededLinks));
        }

        return stringBuilder.toString();
    }

    public static class Call {
        public final static int RECORD_TYPE_AUDIO = 0,
            RECORD_TYPE_VIDEO_PORTAIT = 1,
            RECORD_TYPE_VIDEO_LANDSCAPE = 2;

        @Retention(RetentionPolicy.SOURCE)
        @IntDef({
                RECORD_TYPE_AUDIO,
                RECORD_TYPE_VIDEO_PORTAIT,
                RECORD_TYPE_VIDEO_LANDSCAPE
        })
        public @interface RecordType {}

        public TLRPC.GroupCall call;
        public long chatId;
        public LongSparseArray<TLRPC.TL_groupCallParticipant> participants = new LongSparseArray<>();
        public final ArrayList<TLRPC.TL_groupCallParticipant> sortedParticipants = new ArrayList<>();
        public final ArrayList<VideoParticipant> visibleVideoParticipants = new ArrayList<>();
        public final ArrayList<TLRPC.TL_groupCallParticipant> visibleParticipants = new ArrayList<>();
        public final HashMap<String, Bitmap> thumbs = new HashMap<>();

        private final HashMap<String, VideoParticipant> videoParticipantsCache = new HashMap<>();
        public ArrayList<Long> invitedUsers = new ArrayList<>();
        public HashSet<Long> invitedUsersMap = new HashSet<>();
        public SparseArray<TLRPC.TL_groupCallParticipant> participantsBySources = new SparseArray<>();
        public SparseArray<TLRPC.TL_groupCallParticipant> participantsByVideoSources = new SparseArray<>();
        public SparseArray<TLRPC.TL_groupCallParticipant> participantsByPresentationSources = new SparseArray<>();
        private String nextLoadOffset;
        public boolean membersLoadEndReached;
        public boolean loadingMembers;
        public boolean reloadingMembers;
        public boolean recording;
        public boolean canStreamVideo;
        public int activeVideos;
        public VideoParticipant videoNotAvailableParticipant;
        public VideoParticipant rtmpStreamParticipant;
        public boolean loadedRtmpStreamParticipant;
        public AccountInstance currentAccount;
        public int speakingMembersCount;
        private Runnable typingUpdateRunnable = () -> {
            typingUpdateRunnableScheduled = false;
            checkOnlineParticipants();
            currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.groupCallTypingsUpdated);
        };
        private boolean typingUpdateRunnableScheduled;
        private int lastLoadGuid;
        private HashSet<Integer> loadingGuids = new HashSet<>();
        private ArrayList<TLRPC.TL_updateGroupCallParticipants> updatesQueue = new ArrayList<>();
        private long updatesStartWaitTime;

        public TLRPC.Peer selfPeer;

        private HashSet<Long> loadingUids = new HashSet<>();
        private HashSet<Long> loadingSsrcs = new HashSet<>();

        private Runnable checkQueueRunnable;

        private long lastGroupCallReloadTime;
        private boolean loadingGroupCall;
        private static int videoPointer;

        public final LongSparseArray<TLRPC.TL_groupCallParticipant> currentSpeakingPeers = new LongSparseArray<>();

        private final Runnable updateCurrentSpeakingRunnable = new Runnable() {
            @Override
            public void run() {
                long uptime = SystemClock.uptimeMillis();
                boolean update = false;
                for(int i = 0; i < currentSpeakingPeers.size(); i++) {
                    long key = currentSpeakingPeers.keyAt(i);
                    TLRPC.TL_groupCallParticipant participant = currentSpeakingPeers.get(key);
                    if (uptime - participant.lastSpeakTime >= 500) {
                        update = true;
                        currentSpeakingPeers.remove(key);

                        if (key > 0) {
                            TLRPC.User user = MessagesController.getInstance(currentAccount.getCurrentAccount()).getUser(key);
                            Log.d("GroupCall", "remove from speaking " + key + " " + (user == null ? null : user.first_name));
                        } else {
                            TLRPC.Chat user = MessagesController.getInstance(currentAccount.getCurrentAccount()).getChat(-key);
                            Log.d("GroupCall", "remove from speaking " + key + " " + (user == null ? null : user.title));
                        }
                        i--;
                    }
                }

                if (currentSpeakingPeers.size() > 0) {
                    AndroidUtilities.runOnUIThread(updateCurrentSpeakingRunnable, 550);
                }
                if (update) {
                    currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.groupCallSpeakingUsersUpdated, chatId, call.id, false);
                }
            }
        };

        public void setCall(AccountInstance account, long chatId, TLRPC.TL_phone_groupCall groupCall) {
            this.chatId = chatId;
            currentAccount = account;
            call = groupCall.call;
            recording = call.record_start_date != 0;
            int date = Integer.MAX_VALUE;
            for (int a = 0, N = groupCall.participants.size(); a < N; a++) {
                TLRPC.TL_groupCallParticipant participant = groupCall.participants.get(a);
                participants.put(MessageObject.getPeerId(participant.peer), participant);
                sortedParticipants.add(participant);
                processAllSources(participant, true);
                date = Math.min(date, participant.date);
            }
            sortParticipants();
            nextLoadOffset = groupCall.participants_next_offset;
            loadMembers(true);

            createNoVideoParticipant();
            if (call.rtmp_stream) {
                createRtmpStreamParticipant(Collections.emptyList());
            }
        }

//        public void loadRtmpStreamChannels() {
//            if (call == null || loadedRtmpStreamParticipant) {
//                return;
//            }
//            TLRPC.TL_phone_getGroupCallStreamChannels getGroupCallStreamChannels = new TLRPC.TL_phone_getGroupCallStreamChannels();
//            getGroupCallStreamChannels.call = getInputGroupCall();
//            currentAccount.getConnectionsManager().sendRequest(getGroupCallStreamChannels, (response, error, timestamp) -> {
//                if (response instanceof TLRPC.TL_phone_groupCallStreamChannels) {
//                    TLRPC.TL_phone_groupCallStreamChannels streamChannels = (TLRPC.TL_phone_groupCallStreamChannels) response;
//                    createRtmpStreamParticipant(streamChannels.channels);
//                    loadedRtmpStreamParticipant = true;
//                }
//            }, ConnectionsManager.RequestFlagFailOnServerErrors, ConnectionsManager.ConnectionTypeDownload, call.stream_dc_id);
//        }

        public void createRtmpStreamParticipant(List<TLRPC.TL_groupCallStreamChannel> channels) {
            if (loadedRtmpStreamParticipant && rtmpStreamParticipant != null) {
                return;
            }
            TLRPC.TL_groupCallParticipant participant = rtmpStreamParticipant != null ? rtmpStreamParticipant.participant : new TLRPC.TL_groupCallParticipant();
            participant.peer = new TLRPC.TL_peerChat();
            participant.peer.channel_id = chatId;
            participant.video = new TLRPC.TL_groupCallParticipantVideo();
            TLRPC.TL_groupCallParticipantVideoSourceGroup sourceGroup = new TLRPC.TL_groupCallParticipantVideoSourceGroup();
            sourceGroup.semantics = "SIM";
            for (TLRPC.TL_groupCallStreamChannel channel : channels) {
                sourceGroup.sources.add(channel.channel);
            }
            participant.video.source_groups.add(sourceGroup);
            participant.video.endpoint = "unified";
            participant.videoEndpoint = "unified";
            rtmpStreamParticipant = new VideoParticipant(participant, false, false);

            sortParticipants();
            AndroidUtilities.runOnUIThread(()-> currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.groupCallUpdated, chatId, call.id, false));
        }

        public void createNoVideoParticipant() {
            if (videoNotAvailableParticipant != null) {
                return;
            }
            TLRPC.TL_groupCallParticipant noVideoParticipant = new TLRPC.TL_groupCallParticipant();
            noVideoParticipant.peer = new TLRPC.TL_peerChannel();
            noVideoParticipant.peer.channel_id = chatId;
            noVideoParticipant.muted = true;
            noVideoParticipant.video = new TLRPC.TL_groupCallParticipantVideo();
            noVideoParticipant.video.paused = true;
            noVideoParticipant.video.endpoint = "";

            videoNotAvailableParticipant = new VideoParticipant(noVideoParticipant, false, false);
        }

        public void addSelfDummyParticipant(boolean notify) {
            long selfId = getSelfId();
            if (participants.indexOfKey(selfId) >= 0) {
                return;
            }
            TLRPC.TL_groupCallParticipant selfDummyParticipant = new TLRPC.TL_groupCallParticipant();
            selfDummyParticipant.peer = selfPeer;
            selfDummyParticipant.muted = true;
            selfDummyParticipant.self = true;
            selfDummyParticipant.video_joined = call.can_start_video;
            TLRPC.Chat chat = currentAccount.getMessagesController().getChat(chatId);
            selfDummyParticipant.can_self_unmute = !call.join_muted || ChatObject.canManageCalls(chat);
            selfDummyParticipant.date = currentAccount.getConnectionsManager().getCurrentTime();
            if (ChatObject.canManageCalls(chat) || !ChatObject.isChannel(chat) || chat.megagroup || selfDummyParticipant.can_self_unmute) {
                selfDummyParticipant.active_date = currentAccount.getConnectionsManager().getCurrentTime();
            }
            if (selfId > 0) {
                TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount.getCurrentAccount()).getUserFull(selfId);
                if (userFull != null) {
                    selfDummyParticipant.about = userFull.about;
                }
            } else {
                TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount.getCurrentAccount()).getChatFull(-selfId);
                if (chatFull != null) {
                    selfDummyParticipant.about = chatFull.about;
                }
            }
            participants.put(selfId, selfDummyParticipant);
            sortedParticipants.add(selfDummyParticipant);
            sortParticipants();
            if (notify) {
                currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.groupCallUpdated, chatId, call.id, false);
            }
        }

        public void migrateToChat(TLRPC.Chat chat) {
            chatId = chat.id;
            VoIPService voIPService = VoIPService.getSharedInstance();
            if (voIPService != null && voIPService.getAccount() == currentAccount.getCurrentAccount() && voIPService.getChat() != null && voIPService.getChat().id == -chatId) {
                voIPService.migrateToChat(chat);
            }
        }

        public boolean shouldShowPanel() {
            return call.participants_count > 0 || call.rtmp_stream || isScheduled();
        }

        public boolean isScheduled() {
            return (call.flags & 128) != 0;
        }

        private long getSelfId() {
            long selfId;
            if (selfPeer != null) {
                return MessageObject.getPeerId(selfPeer);
            } else {
                return currentAccount.getUserConfig().getClientUserId();
            }
        }

        private void onParticipantsLoad(ArrayList<TLRPC.TL_groupCallParticipant> loadedParticipants, boolean fromBegin, String reqOffset, String nextOffset, int version, int participantCount) {
            LongSparseArray<TLRPC.TL_groupCallParticipant> old = null;
            long selfId = getSelfId();
            TLRPC.TL_groupCallParticipant oldSelf = participants.get(selfId);
            if (TextUtils.isEmpty(reqOffset)) {
                if (participants.size() != 0) {
                    old = participants;
                    participants = new LongSparseArray<>();
                } else {
                    participants.clear();
                }
                sortedParticipants.clear();
                participantsBySources.clear();
                participantsByVideoSources.clear();
                participantsByPresentationSources.clear();
                loadingGuids.clear();
            }
            nextLoadOffset = nextOffset;
            if (loadedParticipants.isEmpty() || TextUtils.isEmpty(nextLoadOffset)) {
                membersLoadEndReached = true;
            }
            if (TextUtils.isEmpty(reqOffset)) {
                call.version = version;
                call.participants_count = participantCount;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("new participants count " + call.participants_count);
                }
            }
            long time = SystemClock.elapsedRealtime();
            currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.applyGroupCallVisibleParticipants, time);
            boolean hasSelf = false;
            for (int a = 0, N = loadedParticipants.size(); a <= N; a++) {
                TLRPC.TL_groupCallParticipant participant;
                if (a == N) {
                    if (fromBegin && oldSelf != null && !hasSelf) {
                        participant = oldSelf;
                    } else {
                        continue;
                    }
                } else {
                    participant = loadedParticipants.get(a);
                    if (participant.self) {
                        hasSelf = true;
                    }
                }
                TLRPC.TL_groupCallParticipant oldParticipant = participants.get(MessageObject.getPeerId(participant.peer));
                if (oldParticipant != null) {
                    sortedParticipants.remove(oldParticipant);
                    processAllSources(oldParticipant, false);
                    if (oldParticipant.self) {
                        participant.lastTypingDate = oldParticipant.active_date;
                    } else {
                        participant.lastTypingDate = Math.max(participant.active_date, oldParticipant.active_date);
                    }
                    if (time != participant.lastVisibleDate) {
                        participant.active_date = participant.lastTypingDate;
                    }
                } else if (old != null) {
                    oldParticipant = old.get(MessageObject.getPeerId(participant.peer));
                    if (oldParticipant != null) {
                        if (oldParticipant.self) {
                            participant.lastTypingDate = oldParticipant.active_date;
                        } else {
                            participant.lastTypingDate = Math.max(participant.active_date, oldParticipant.active_date);
                        }
                        if (time != participant.lastVisibleDate) {
                            participant.active_date = participant.lastTypingDate;
                        } else {
                            participant.active_date = oldParticipant.active_date;
                        }
                    }
                }
                participants.put(MessageObject.getPeerId(participant.peer), participant);
                sortedParticipants.add(participant);
                processAllSources(participant, true);
            }
            if (call.participants_count < participants.size()) {
                call.participants_count = participants.size();
            }
            sortParticipants();
            currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.groupCallUpdated, chatId, call.id, false);
            setParticiapantsVolume();
        }

        public void loadMembers(boolean fromBegin) {
            if (fromBegin) {
                if (reloadingMembers) {
                    return;
                }
                membersLoadEndReached = false;
                nextLoadOffset = null;
            }
            if (membersLoadEndReached || sortedParticipants.size() > MAX_PARTICIPANTS_COUNT) {
                return;
            }
            if (fromBegin) {
                reloadingMembers = true;
            }
            loadingMembers = true;
            TLRPC.TL_phone_getGroupParticipants req = new TLRPC.TL_phone_getGroupParticipants();
            req.call = getInputGroupCall();
            req.offset = nextLoadOffset != null ? nextLoadOffset : "";
            req.limit = 20;
            currentAccount.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                loadingMembers = false;
                if (fromBegin) {
                    reloadingMembers = false;
                }
                if (response != null) {
                    TLRPC.TL_phone_groupParticipants groupParticipants = (TLRPC.TL_phone_groupParticipants) response;
                    currentAccount.getMessagesController().putUsers(groupParticipants.users, false);
                    currentAccount.getMessagesController().putChats(groupParticipants.chats, false);
                    onParticipantsLoad(groupParticipants.participants, fromBegin, req.offset, groupParticipants.next_offset, groupParticipants.version, groupParticipants.count);
                }
            }));
        }

        private void setParticiapantsVolume() {
            VoIPService voIPService = VoIPService.getSharedInstance();
            if (voIPService != null && voIPService.getAccount() == currentAccount.getCurrentAccount() && voIPService.getChat() != null && voIPService.getChat().id == -chatId) {
                voIPService.setParticipantsVolume();
            }
        }

        public void setTitle(String title) {
            TLRPC.TL_phone_editGroupCallTitle req = new TLRPC.TL_phone_editGroupCallTitle();
            req.call = getInputGroupCall();
            req.title = title;
            currentAccount.getConnectionsManager().sendRequest(req, (response, error) -> {
                if (response != null) {
                    final TLRPC.Updates res = (TLRPC.Updates) response;
                    currentAccount.getMessagesController().processUpdates(res, false);
                }
            });
        }

        public void addInvitedUser(long uid) {
            if (participants.get(uid) != null || invitedUsersMap.contains(uid)) {
                return;
            }
            invitedUsersMap.add(uid);
            invitedUsers.add(uid);
        }

        public void processTypingsUpdate(AccountInstance accountInstance, ArrayList<Long> uids, int date) {
            boolean updated = false;
            ArrayList<Long> participantsToLoad = null;
            long time = SystemClock.elapsedRealtime();
            currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.applyGroupCallVisibleParticipants, time);
            for (int a = 0, N = uids.size(); a < N; a++) {
                Long id = uids.get(a);
                TLRPC.TL_groupCallParticipant participant = participants.get(id);
                if (participant != null) {
                    if (date - participant.lastTypingDate > 10) {
                        if (participant.lastVisibleDate != date) {
                            participant.active_date = date;
                        }
                        participant.lastTypingDate = date;
                        updated = true;
                    }
                } else {
                    if (participantsToLoad == null) {
                        participantsToLoad = new ArrayList<>();
                    }
                    participantsToLoad.add(id);
                }
            }
            if (participantsToLoad != null) {
                loadUnknownParticipants(participantsToLoad, true, null);
            }
            if (updated) {
                sortParticipants();
                currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.groupCallUpdated, chatId, call.id, false);
            }
        }

        private void loadUnknownParticipants(ArrayList<Long> participantsToLoad, boolean isIds, OnParticipantsLoad onLoad) {
            HashSet<Long> set = isIds ? loadingUids : loadingSsrcs;
            for (int a = 0, N = participantsToLoad.size(); a < N; a++) {
                if (set.contains(participantsToLoad.get(a))) {
                    participantsToLoad.remove(a);
                    a--;
                    N--;
                }
            }
            if (participantsToLoad.isEmpty()) {
                return;
            }
            int guid = ++lastLoadGuid;
            loadingGuids.add(guid);
            set.addAll(participantsToLoad);
            TLRPC.TL_phone_getGroupParticipants req = new TLRPC.TL_phone_getGroupParticipants();
            req.call = getInputGroupCall();
            for (int a = 0, N = participantsToLoad.size(); a < N; a++) {
                long uid = participantsToLoad.get(a);
                if (isIds) {
                    if (uid > 0) {
                        TLRPC.TL_inputPeerUser peerUser = new TLRPC.TL_inputPeerUser();
                        peerUser.user_id = uid;
                        req.ids.add(peerUser);
                    } else {
                        TLRPC.Chat chat = currentAccount.getMessagesController().getChat(-uid);
                        TLRPC.InputPeer inputPeer;
                        if (chat == null || ChatObject.isChannel(chat)) {
                            inputPeer = new TLRPC.TL_inputPeerChannel();
                            inputPeer.channel_id = -uid;
                        } else {
                            inputPeer = new TLRPC.TL_inputPeerChat();
                            inputPeer.chat_id = -uid;
                        }
                        req.ids.add(inputPeer);
                    }
                } else {
                    req.sources.add((int) uid);
                }
            }
            req.offset = "";
            req.limit = 100;
            currentAccount.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (!loadingGuids.remove(guid)) {
                    return;
                }
                if (response != null) {
                    TLRPC.TL_phone_groupParticipants groupParticipants = (TLRPC.TL_phone_groupParticipants) response;
                    currentAccount.getMessagesController().putUsers(groupParticipants.users, false);
                    currentAccount.getMessagesController().putChats(groupParticipants.chats, false);
                    for (int a = 0, N = groupParticipants.participants.size(); a < N; a++) {
                        TLRPC.TL_groupCallParticipant participant = groupParticipants.participants.get(a);
                        long pid = MessageObject.getPeerId(participant.peer);
                        TLRPC.TL_groupCallParticipant oldParticipant = participants.get(pid);
                        if (oldParticipant != null) {
                            sortedParticipants.remove(oldParticipant);
                            processAllSources(oldParticipant, false);
                        }
                        participants.put(pid, participant);
                        sortedParticipants.add(participant);
                        processAllSources(participant, true);
                        if (invitedUsersMap.contains(pid)) {
                            Long id = pid;
                            invitedUsersMap.remove(id);
                            invitedUsers.remove(id);
                        }
                    }
                    if (call.participants_count < participants.size()) {
                        call.participants_count = participants.size();
                    }
                    sortParticipants();
                    currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.groupCallUpdated, chatId, call.id, false);
                    if (onLoad != null) {
                        onLoad.onLoad(participantsToLoad);
                    } else {
                        setParticiapantsVolume();
                    }
                }
                set.removeAll(participantsToLoad);
            }));
        }

        private void processAllSources(TLRPC.TL_groupCallParticipant participant, boolean add) {
            if (participant.source != 0) {
                if (add) {
                    participantsBySources.put(participant.source, participant);
                } else {
                    participantsBySources.remove(participant.source);
                }
            }
            for (int c = 0; c < 2; c++) {
                TLRPC.TL_groupCallParticipantVideo data = c == 0 ? participant.video : participant.presentation;
                if (data != null) {
                    if ((data.flags & 2) != 0 && data.audio_source != 0) {
                        if (add) {
                            participantsBySources.put(data.audio_source, participant);
                        } else {
                            participantsBySources.remove(data.audio_source);
                        }
                    }
                    SparseArray<TLRPC.TL_groupCallParticipant> sourcesArray = c == 0 ? participantsByVideoSources : participantsByPresentationSources;
                    for (int a = 0, N = data.source_groups.size(); a < N; a++) {
                        TLRPC.TL_groupCallParticipantVideoSourceGroup sourceGroup = data.source_groups.get(a);
                        for (int b = 0, N2 = sourceGroup.sources.size(); b < N2; b++) {
                            int source = sourceGroup.sources.get(b);
                            if (add) {
                                sourcesArray.put(source, participant);
                            } else {
                                sourcesArray.remove(source);
                            }
                        }
                    }
                    if (add) {
                        if (c == 0) {
                            participant.videoEndpoint = data.endpoint;
                        } else {
                            participant.presentationEndpoint = data.endpoint;
                        }
                    } else {
                        if (c == 0) {
                            participant.videoEndpoint = null;
                        } else {
                            participant.presentationEndpoint = null;
                        }
                    }
                }
            }
        }

        public void processVoiceLevelsUpdate(int[] ssrc, float[] levels, boolean[] voice) {
            boolean updated = false;
            boolean updateCurrentSpeakingList = false;
            int currentTime = currentAccount.getConnectionsManager().getCurrentTime();
            ArrayList<Long> participantsToLoad = null;
            long time = SystemClock.elapsedRealtime();
            long uptime = SystemClock.uptimeMillis();
            currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.applyGroupCallVisibleParticipants, time);
            for (int a = 0; a < ssrc.length; a++) {
                TLRPC.TL_groupCallParticipant participant;
                if (ssrc[a] == 0) {
                    participant = participants.get(getSelfId());
                } else {
                    participant = participantsBySources.get(ssrc[a]);
                }
                if (participant != null) {
                    participant.hasVoice = voice[a];
                    if (voice[a] || time - participant.lastVoiceUpdateTime > 500) {
                        participant.hasVoiceDelayed = voice[a];
                        participant.lastVoiceUpdateTime = time;
                    }
                    long peerId = MessageObject.getPeerId(participant.peer);
                    if (levels[a] > 0.1f) {
                        if (voice[a] && participant.lastTypingDate + 1 < currentTime) {
                            if (time != participant.lastVisibleDate) {
                                participant.active_date = currentTime;
                            }
                            participant.lastTypingDate = currentTime;
                            updated = true;
                        }
                        participant.lastSpeakTime = uptime;
                        participant.amplitude = levels[a];

                        if (currentSpeakingPeers.get(peerId, null) == null) {
                            if (peerId > 0) {
                                TLRPC.User user = MessagesController.getInstance(currentAccount.getCurrentAccount()).getUser(peerId);
                                Log.d("GroupCall", "add to current speaking " + peerId + " " + (user == null ? null : user.first_name));
                            } else {
                                TLRPC.Chat user = MessagesController.getInstance(currentAccount.getCurrentAccount()).getChat(-peerId);
                                Log.d("GroupCall", "add to current speaking " + peerId + " " + (user == null ? null : user.title));
                            }
                            currentSpeakingPeers.put(peerId, participant);
                            updateCurrentSpeakingList = true;
                        }
                    } else {
                        if (uptime - participant.lastSpeakTime >= 500) {
                            if (currentSpeakingPeers.get(peerId, null) != null) {
                                currentSpeakingPeers.remove(peerId);

                                if (peerId > 0) {
                                    TLRPC.User user = MessagesController.getInstance(currentAccount.getCurrentAccount()).getUser(peerId);
                                    Log.d("GroupCall", "remove from speaking " + peerId + " " + (user == null ? null : user.first_name));
                                } else {
                                    TLRPC.Chat user = MessagesController.getInstance(currentAccount.getCurrentAccount()).getChat(-peerId);
                                    Log.d("GroupCall", "remove from speaking " + peerId + " " + (user == null ? null : user.title));
                                }

                                updateCurrentSpeakingList = true;
                            }
                        }
                        participant.amplitude = 0;
                    }
                } else if (ssrc[a] != 0) {
                    if (participantsToLoad == null) {
                        participantsToLoad = new ArrayList<>();
                    }
                    participantsToLoad.add((long) ssrc[a]);
                }
            }
            if (participantsToLoad != null) {
                loadUnknownParticipants(participantsToLoad, false, null);
            }
            if (updated) {
                sortParticipants();
                currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.groupCallUpdated, chatId, call.id, false);
            }
            if (updateCurrentSpeakingList) {
                if (currentSpeakingPeers.size() > 0) {
                    AndroidUtilities.cancelRunOnUIThread(updateCurrentSpeakingRunnable);
                    AndroidUtilities.runOnUIThread(updateCurrentSpeakingRunnable, 550);
                }
                currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.groupCallSpeakingUsersUpdated, chatId, call.id, false);
            }
        }

        public void updateVisibleParticipants() {
            sortParticipants();
            currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.groupCallUpdated, chatId, call.id, false, 0L);
        }

        public void clearVideFramesInfo() {
            for (int i = 0; i < sortedParticipants.size(); i++) {
                sortedParticipants.get(i).hasCameraFrame = VIDEO_FRAME_NO_FRAME;
                sortedParticipants.get(i).hasPresentationFrame = VIDEO_FRAME_NO_FRAME;
                sortedParticipants.get(i).videoIndex = 0;
            }
            sortParticipants();
        }

        public interface OnParticipantsLoad {
            void onLoad(ArrayList<Long> ssrcs);
        }

        public void processUnknownVideoParticipants(int[] ssrc, OnParticipantsLoad onLoad) {
            ArrayList<Long> participantsToLoad = null;
            for (int a = 0; a < ssrc.length; a++) {
                if (participantsBySources.get(ssrc[a]) != null || participantsByVideoSources.get(ssrc[a]) != null || participantsByPresentationSources.get(ssrc[a]) != null) {
                    continue;
                }
                if (participantsToLoad == null) {
                    participantsToLoad = new ArrayList<>();
                }
                participantsToLoad.add((long) ssrc[a]);
            }
            if (participantsToLoad != null) {
                loadUnknownParticipants(participantsToLoad, false, onLoad);
            } else {
                onLoad.onLoad(null);
            }
        }

        private int isValidUpdate(TLRPC.TL_updateGroupCallParticipants update) {
            if (call.version + 1 == update.version || call.version == update.version) {
                return 0;
            } else if (call.version < update.version) {
                return 1;
            } else {
                return 2;
            }
        }

        public void setSelfPeer(TLRPC.InputPeer peer) {
            if (peer == null) {
                selfPeer = null;
            } else {
                if (peer instanceof TLRPC.TL_inputPeerUser) {
                    selfPeer = new TLRPC.TL_peerUser();
                    selfPeer.user_id = peer.user_id;
                } else if (peer instanceof TLRPC.TL_inputPeerChat) {
                    selfPeer = new TLRPC.TL_peerChat();
                    selfPeer.chat_id = peer.chat_id;
                } else {
                    selfPeer = new TLRPC.TL_peerChannel();
                    selfPeer.channel_id = peer.channel_id;
                }
            }
        }

        private void processUpdatesQueue() {
            Collections.sort(updatesQueue, (updates, updates2) -> AndroidUtilities.compare(updates.version, updates2.version));
            if (updatesQueue != null && !updatesQueue.isEmpty()) {
                boolean anyProceed = false;
                for (int a = 0; a < updatesQueue.size(); a++) {
                    TLRPC.TL_updateGroupCallParticipants update = updatesQueue.get(a);
                    int updateState = isValidUpdate(update);
                    if (updateState == 0) {
                        processParticipantsUpdate(update, true);
                        anyProceed = true;
                        updatesQueue.remove(a);
                        a--;
                    } else if (updateState == 1) {
                        if (updatesStartWaitTime != 0 && (anyProceed || Math.abs(System.currentTimeMillis() - updatesStartWaitTime) <= 1500)) {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("HOLE IN GROUP CALL UPDATES QUEUE - will wait more time");
                            }
                            if (anyProceed) {
                                updatesStartWaitTime = System.currentTimeMillis();
                            }
                        } else {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("HOLE IN GROUP CALL UPDATES QUEUE - reload participants");
                            }
                            updatesStartWaitTime = 0;
                            updatesQueue.clear();
                            nextLoadOffset = null;
                            loadMembers(true);
                        }
                        return;
                    } else {
                        updatesQueue.remove(a);
                        a--;
                    }
                }
                updatesQueue.clear();
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("GROUP CALL UPDATES QUEUE PROCEED - OK");
                }
            }
            updatesStartWaitTime = 0;
        }

        private void checkQueue() {
            checkQueueRunnable = null;
            if (updatesStartWaitTime != 0 && (System.currentTimeMillis() - updatesStartWaitTime) >= 1500) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("QUEUE GROUP CALL UPDATES WAIT TIMEOUT - CHECK QUEUE");
                }
                processUpdatesQueue();
            }
            if (!updatesQueue.isEmpty()) {
                AndroidUtilities.runOnUIThread(checkQueueRunnable = this::checkQueue, 1000);
            }
        }

        public void reloadGroupCall() {
            TLRPC.TL_phone_getGroupCall req = new TLRPC.TL_phone_getGroupCall();
            req.call = getInputGroupCall();
            req.limit = 100;
            currentAccount.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_phone_groupCall) {
                    TLRPC.TL_phone_groupCall phoneGroupCall = (TLRPC.TL_phone_groupCall) response;
                    call = phoneGroupCall.call;
                    currentAccount.getMessagesController().putUsers(phoneGroupCall.users, false);
                    currentAccount.getMessagesController().putChats(phoneGroupCall.chats, false);
                    onParticipantsLoad(phoneGroupCall.participants, true, "", phoneGroupCall.participants_next_offset, phoneGroupCall.call.version, phoneGroupCall.call.participants_count);
                }
            }));
        }

        private void loadGroupCall() {
            if (loadingGroupCall || SystemClock.elapsedRealtime() - lastGroupCallReloadTime < 30000) {
                return;
            }
            loadingGroupCall = true;
            TLRPC.TL_phone_getGroupParticipants req = new TLRPC.TL_phone_getGroupParticipants();
            req.call = getInputGroupCall();
            req.offset = "";
            req.limit = 1;
            currentAccount.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                lastGroupCallReloadTime = SystemClock.elapsedRealtime();
                loadingGroupCall = false;
                if (response != null) {
                    TLRPC.TL_phone_groupParticipants res = (TLRPC.TL_phone_groupParticipants) response;
                    currentAccount.getMessagesController().putUsers(res.users, false);
                    currentAccount.getMessagesController().putChats(res.chats, false);
                    if (call.participants_count != res.count) {
                        call.participants_count = res.count;
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("new participants reload count " + call.participants_count);
                        }
                        currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.groupCallUpdated, chatId, call.id, false);
                    }
                }
            }));
        }

        public void processParticipantsUpdate(TLRPC.TL_updateGroupCallParticipants update, boolean fromQueue) {
            if (!fromQueue) {
                boolean versioned = false;
                for (int a = 0, N = update.participants.size(); a < N; a++) {
                    TLRPC.TL_groupCallParticipant participant = update.participants.get(a);
                    if (participant.versioned) {
                        versioned = true;
                        break;
                    }
                }
                if (versioned && call.version + 1 < update.version) {
                    if (reloadingMembers || updatesStartWaitTime == 0 || Math.abs(System.currentTimeMillis() - updatesStartWaitTime) <= 1500) {
                        if (updatesStartWaitTime == 0) {
                            updatesStartWaitTime = System.currentTimeMillis();
                        }
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("add TL_updateGroupCallParticipants to queue " + update.version);
                        }
                        updatesQueue.add(update);
                        if (checkQueueRunnable == null) {
                            AndroidUtilities.runOnUIThread(checkQueueRunnable = this::checkQueue, 1500);
                        }
                    } else {
                        nextLoadOffset = null;
                        loadMembers(true);
                    }
                    return;
                }
                if (versioned && update.version < call.version) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("ignore processParticipantsUpdate because of version");
                    }
                    return;
                }
            }
            boolean reloadCall = false;
            boolean updated = false;
            boolean selfUpdated = false;
            boolean changedOrAdded = false;
            boolean speakingUpdated = false;

            long selfId = getSelfId();
            long time = SystemClock.elapsedRealtime();
            long justJoinedId = 0;
            int lastParticipantDate;
            if (!sortedParticipants.isEmpty()) {
                lastParticipantDate = sortedParticipants.get(sortedParticipants.size() - 1).date;
            } else {
                lastParticipantDate = 0;
            }
            currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.applyGroupCallVisibleParticipants, time);
            for (int a = 0, N = update.participants.size(); a < N; a++) {
                TLRPC.TL_groupCallParticipant participant = update.participants.get(a);
                long pid = MessageObject.getPeerId(participant.peer);
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("process participant " + pid + " left = " + participant.left + " versioned " + participant.versioned + " flags = " + participant.flags + " self = " + selfId + " volume = " + participant.volume);
                }
                TLRPC.TL_groupCallParticipant oldParticipant = participants.get(pid);
                if (participant.left) {
                    if (oldParticipant == null && update.version == call.version) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("unknowd participant left, reload call");
                        }
                        reloadCall = true;
                    }
                    if (oldParticipant != null) {
                        participants.remove(pid);
                        processAllSources(oldParticipant, false);
                        sortedParticipants.remove(oldParticipant);
                        visibleParticipants.remove(oldParticipant);
                        if (currentSpeakingPeers.get(pid, null) != null) {
                            if (pid > 0) {
                                TLRPC.User user = MessagesController.getInstance(currentAccount.getCurrentAccount()).getUser(pid);
                                Log.d("GroupCall", "left remove from speaking " + pid + " " + (user == null ? null : user.first_name));
                            } else {
                                TLRPC.Chat user = MessagesController.getInstance(currentAccount.getCurrentAccount()).getChat(-pid);
                                Log.d("GroupCall", "left remove from speaking " + pid + " " + (user == null ? null : user.title));
                            }
                            currentSpeakingPeers.remove(pid);
                            speakingUpdated = true;
                        }
                        for (int i = 0; i < visibleVideoParticipants.size(); i++) {
                            VideoParticipant videoParticipant = visibleVideoParticipants.get(i);
                            if (MessageObject.getPeerId(videoParticipant.participant.peer) == MessageObject.getPeerId(oldParticipant.peer)) {
                                visibleVideoParticipants.remove(i);
                                i--;
                            }
                        }
                    }
                    call.participants_count--;
                    if (call.participants_count < 0) {
                        call.participants_count = 0;
                    }
                    updated = true;
                } else {
                    if (invitedUsersMap.contains(pid)) {
                        Long id = pid;
                        invitedUsersMap.remove(id);
                        invitedUsers.remove(id);
                    }
                    if (oldParticipant != null) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("new participant, update old");
                        }
                        oldParticipant.muted = participant.muted;
                        if (participant.muted && currentSpeakingPeers.get(pid, null) != null) {
                            currentSpeakingPeers.remove(pid);
                            if (pid > 0) {
                                TLRPC.User user = MessagesController.getInstance(currentAccount.getCurrentAccount()).getUser(pid);
                                Log.d("GroupCall", "muted remove from speaking " + pid + " " + (user == null ? null : user.first_name));
                            } else {
                                TLRPC.Chat user = MessagesController.getInstance(currentAccount.getCurrentAccount()).getChat(-pid);
                                Log.d("GroupCall", "muted remove from speaking " + pid + " " + (user == null ? null : user.title));
                            }
                            speakingUpdated = true;
                        }
                        if (!participant.min) {
                            oldParticipant.volume = participant.volume;
                            oldParticipant.muted_by_you = participant.muted_by_you;
                        } else {
                            if ((participant.flags & 128) != 0 && (oldParticipant.flags & 128) == 0) {
                                participant.flags &=~ 128;
                            }
                            if (participant.volume_by_admin && oldParticipant.volume_by_admin) {
                                oldParticipant.volume = participant.volume;
                            }
                        }
                        oldParticipant.flags = participant.flags;
                        oldParticipant.can_self_unmute = participant.can_self_unmute;
                        oldParticipant.video_joined = participant.video_joined;
                        if (oldParticipant.raise_hand_rating == 0 && participant.raise_hand_rating != 0) {
                            oldParticipant.lastRaiseHandDate = SystemClock.elapsedRealtime();
                        }
                        oldParticipant.raise_hand_rating = participant.raise_hand_rating;
                        oldParticipant.date = participant.date;
                        oldParticipant.lastTypingDate = Math.max(oldParticipant.active_date, participant.active_date);
                        if (time != oldParticipant.lastVisibleDate) {
                            oldParticipant.active_date = oldParticipant.lastTypingDate;
                        }
                        if (oldParticipant.source != participant.source || !isSameVideo(oldParticipant.video, participant.video) || !isSameVideo(oldParticipant.presentation, participant.presentation)) {
                            processAllSources(oldParticipant, false);
                            oldParticipant.video = participant.video;
                            oldParticipant.presentation = participant.presentation;
                            oldParticipant.source = participant.source;
                            processAllSources(oldParticipant, true);
                            participant.presentationEndpoint = oldParticipant.presentationEndpoint;
                            participant.videoEndpoint = oldParticipant.videoEndpoint;
                            participant.videoIndex = oldParticipant.videoIndex;
                        } else if (oldParticipant.video != null && participant.video != null) {
                            oldParticipant.video.paused = participant.video.paused;
                        }
                    } else {
                        if (participant.just_joined) {
                            if (pid != selfId) {
                                justJoinedId = pid;
                            }
                            call.participants_count++;
                            if (update.version == call.version) {
                                reloadCall = true;
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("new participant, just joined, reload call");
                                }
                            } else {
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("new participant, just joined");
                                }
                            }
                        }
                        if (participant.raise_hand_rating != 0) {
                            participant.lastRaiseHandDate = SystemClock.elapsedRealtime();
                        }
                        if (pid == selfId || sortedParticipants.size() < 20 || participant.date <= lastParticipantDate || participant.active_date != 0 || participant.can_self_unmute || !participant.muted || !participant.min || membersLoadEndReached) {
                            sortedParticipants.add(participant);
                        }
                        participants.put(pid, participant);
                        processAllSources(participant, true);
                    }
                    if (pid == selfId && participant.active_date == 0 && (participant.can_self_unmute || !participant.muted)) {
                        participant.active_date = currentAccount.getConnectionsManager().getCurrentTime();
                    }
                    changedOrAdded = true;
                    updated = true;
                }
                if (pid == selfId) {
                    selfUpdated = true;
                }
            }
            if (update.version > call.version) {
                call.version = update.version;
                if (!fromQueue) {
                    processUpdatesQueue();
                }
            }
            if (call.participants_count < participants.size()) {
                call.participants_count = participants.size();
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("new participants count after update " + call.participants_count);
            }
            if (reloadCall) {
                loadGroupCall();
            }
            if (updated) {
                if (changedOrAdded) {
                    sortParticipants();
                }
                currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.groupCallUpdated, chatId, call.id, selfUpdated, justJoinedId);
            }
            if (speakingUpdated) {
                currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.groupCallSpeakingUsersUpdated, chatId, call.id, false);
            }
        }

        private boolean isSameVideo(TLRPC.TL_groupCallParticipantVideo oldVideo, TLRPC.TL_groupCallParticipantVideo newVideo) {
            if (oldVideo == null && newVideo != null || oldVideo != null && newVideo == null) {
                return false;
            }
            if (oldVideo == null || newVideo == null) {
                return true;
            }
            if (!TextUtils.equals(oldVideo.endpoint, newVideo.endpoint)) {
                return false;
            }
            if (oldVideo.source_groups.size() != newVideo.source_groups.size()) {
                return false;
            }
            for (int a = 0, N = oldVideo.source_groups.size(); a < N; a++) {
                TLRPC.TL_groupCallParticipantVideoSourceGroup oldGroup = oldVideo.source_groups.get(a);
                TLRPC.TL_groupCallParticipantVideoSourceGroup newGroup = newVideo.source_groups.get(a);
                if (!TextUtils.equals(oldGroup.semantics, newGroup.semantics)) {
                    return false;
                }
                if (oldGroup.sources.size() != newGroup.sources.size()) {
                    return false;
                }
                for (int b = 0, N2 = oldGroup.sources.size(); b < N2; b++) {
                    if (!newGroup.sources.contains(oldGroup.sources.get(b))) {
                        return false;
                    }
                }
            }
            return true;
        }

        public void processGroupCallUpdate(TLRPC.TL_updateGroupCall update) {
            if (call.version < update.call.version) {
                nextLoadOffset = null;
                loadMembers(true);
            }
            call = update.call;
            TLRPC.TL_groupCallParticipant selfParticipant = participants.get(getSelfId());
            recording = call.record_start_date != 0;
            currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.groupCallUpdated, chatId, call.id, false);
        }

        public TLRPC.TL_inputGroupCall getInputGroupCall() {
            TLRPC.TL_inputGroupCall inputGroupCall = new TLRPC.TL_inputGroupCall();
            inputGroupCall.id = call.id;
            inputGroupCall.access_hash = call.access_hash;
            return inputGroupCall;
        }

        public static boolean videoIsActive(TLRPC.TL_groupCallParticipant participant, boolean presentation, ChatObject.Call call) {
            if (participant == null) {
                return false;
            }
            VoIPService service = VoIPService.getSharedInstance();
            if (service == null) {
                return false;
            }
            if (participant.self) {
                return service.getVideoState(presentation) == Instance.VIDEO_STATE_ACTIVE;
            } else {
                if (call.rtmpStreamParticipant != null && call.rtmpStreamParticipant.participant == participant || call.videoNotAvailableParticipant != null && call.videoNotAvailableParticipant.participant == participant || call.participants.get(MessageObject.getPeerId(participant.peer)) != null) {
                    if (presentation) {
                        return participant.presentation != null;// && participant.hasPresentationFrame == 2;
                    } else {
                        return participant.video != null;// && participant.hasCameraFrame == 2;
                    }
                } else {
                    return false;
                }
            }
        }

        public void sortParticipants() {
            visibleVideoParticipants.clear();
            visibleParticipants.clear();
            TLRPC.Chat chat = currentAccount.getMessagesController().getChat(chatId);
            boolean isAdmin = ChatObject.canManageCalls(chat);

            if (rtmpStreamParticipant != null) {
                visibleVideoParticipants.add(rtmpStreamParticipant);
            }

            long selfId = getSelfId();
            VoIPService service = VoIPService.getSharedInstance();
            TLRPC.TL_groupCallParticipant selfParticipant = participants.get(selfId);
            canStreamVideo = true;//selfParticipant != null && selfParticipant.video_joined || BuildVars.DEBUG_PRIVATE_VERSION;
            boolean allowedVideoCount;
            boolean hasAnyVideo = false;
            activeVideos = 0;
            for (int i = 0, N = sortedParticipants.size(); i < N; i++) {
                TLRPC.TL_groupCallParticipant participant = sortedParticipants.get(i);
                boolean cameraActive = videoIsActive(participant, false, this);
                boolean screenActive = videoIsActive(participant, true, this);
                if (!participant.self && (cameraActive || screenActive)) {
                    activeVideos++;
                }
                if (cameraActive || screenActive) {
                    hasAnyVideo = true;
                    if (canStreamVideo) {
                        if (participant.videoIndex == 0) {
                            if (participant.self) {
                                participant.videoIndex = Integer.MAX_VALUE;
                            } else {
                                participant.videoIndex = ++videoPointer;
                            }
                        }
                    } else {
                        participant.videoIndex = 0;
                    }
                } else if (participant.self || !canStreamVideo || (participant.video == null && participant.presentation == null)) {
                    participant.videoIndex = 0;
                }
            }

            Comparator<TLRPC.TL_groupCallParticipant> comparator = (o1, o2) -> {
                boolean videoActive1 = o1.videoIndex > 0;
                boolean videoActive2 = o2.videoIndex > 0;
                if (videoActive1 && videoActive2) {
                    return o2.videoIndex - o1.videoIndex;
                } else if (videoActive1) {
                    return -1;
                } else if (videoActive2) {
                    return 1;
                }
                if (o1.active_date != 0 && o2.active_date != 0) {
                    return Integer.compare(o2.active_date, o1.active_date);
                } else if (o1.active_date != 0) {
                    return -1;
                } else if (o2.active_date != 0) {
                    return 1;
                }
                if (MessageObject.getPeerId(o1.peer) == selfId) {
                    return -1;
                } else if (MessageObject.getPeerId(o2.peer) == selfId) {
                    return 1;
                }
                if (isAdmin) {
                    if (o1.raise_hand_rating != 0 && o2.raise_hand_rating != 0) {
                        return Long.compare(o2.raise_hand_rating, o1.raise_hand_rating);
                    } else if (o1.raise_hand_rating != 0) {
                        return -1;
                    } else if (o2.raise_hand_rating != 0) {
                        return 1;
                    }
                }
                if (call.join_date_asc) {
                    return Integer.compare(o1.date, o2.date);
                } else {
                    return Integer.compare(o2.date, o1.date);
                }
            };
            try {
                Collections.sort(sortedParticipants, comparator);
            } catch (Exception e) {

            }
            TLRPC.TL_groupCallParticipant lastParticipant = sortedParticipants.isEmpty() ? null : sortedParticipants.get(sortedParticipants.size() - 1);
            if (videoIsActive(lastParticipant, false, this) || videoIsActive(lastParticipant, true, this)) {
                if (call.unmuted_video_count > activeVideos) {
                    activeVideos = call.unmuted_video_count;
                    VoIPService voIPService = VoIPService.getSharedInstance();
                    if (voIPService != null && voIPService.groupCall == this) {
                        if (voIPService.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE || voIPService.getVideoState(true) == Instance.VIDEO_STATE_ACTIVE) {
                            activeVideos--;
                        }
                    }
                }
            }

            if (sortedParticipants.size() > MAX_PARTICIPANTS_COUNT && (!ChatObject.canManageCalls(chat) || lastParticipant.raise_hand_rating == 0)) {
                for (int a = MAX_PARTICIPANTS_COUNT, N = sortedParticipants.size(); a < N; a++) {
                    TLRPC.TL_groupCallParticipant p = sortedParticipants.get(MAX_PARTICIPANTS_COUNT);
                    if (p.raise_hand_rating != 0) {
                        continue;
                    }
                    processAllSources(p, false);
                    participants.remove(MessageObject.getPeerId(p.peer));
                    sortedParticipants.remove(MAX_PARTICIPANTS_COUNT);
                }
            }
            checkOnlineParticipants();

            if (!canStreamVideo && hasAnyVideo && videoNotAvailableParticipant != null) {
                visibleVideoParticipants.add(videoNotAvailableParticipant);
            }

            int wideVideoIndex = 0;
            for (int i = 0; i < sortedParticipants.size(); i++) {
                TLRPC.TL_groupCallParticipant participant = sortedParticipants.get(i);
                if (canStreamVideo && participant.videoIndex != 0) {
                    if (!participant.self && videoIsActive(participant, true, this) && videoIsActive(participant, false, this)) {
                        VideoParticipant videoParticipant = videoParticipantsCache.get(participant.videoEndpoint);
                        if (videoParticipant == null) {
                            videoParticipant = new VideoParticipant(participant, false, true);
                            videoParticipantsCache.put(participant.videoEndpoint, videoParticipant);
                        } else {
                            videoParticipant.participant = participant;
                            videoParticipant.presentation = false;
                            videoParticipant.hasSame = true;
                        }

                        VideoParticipant presentationParticipant = videoParticipantsCache.get(participant.presentationEndpoint);
                        if (presentationParticipant == null) {
                            presentationParticipant = new VideoParticipant(participant, true, true);
                        } else {
                            presentationParticipant.participant = participant;
                            presentationParticipant.presentation = true;
                            presentationParticipant.hasSame = true;
                        }
                        visibleVideoParticipants.add(videoParticipant);
                        if (videoParticipant.aspectRatio > 1f) {
                            wideVideoIndex = visibleVideoParticipants.size() - 1;
                        }
                        visibleVideoParticipants.add(presentationParticipant);
                        if (presentationParticipant.aspectRatio > 1f) {
                            wideVideoIndex = visibleVideoParticipants.size() - 1;
                        }
                    } else {
                        if (participant.self) {
                            if (videoIsActive(participant, true, this)) {
                                visibleVideoParticipants.add(new VideoParticipant(participant, true, false));
                            }
                            if (videoIsActive(participant, false, this)) {
                                visibleVideoParticipants.add(new VideoParticipant(participant, false, false));
                            }
                        } else {
                            boolean presentation = videoIsActive(participant, true, this);

                            VideoParticipant videoParticipant = videoParticipantsCache.get(presentation ? participant.presentationEndpoint : participant.videoEndpoint);
                            if (videoParticipant == null) {
                                videoParticipant = new VideoParticipant(participant, presentation, false);
                                videoParticipantsCache.put(presentation ? participant.presentationEndpoint : participant.videoEndpoint, videoParticipant);
                            } else {
                                videoParticipant.participant = participant;
                                videoParticipant.presentation = presentation;
                                videoParticipant.hasSame = false;
                            }
                            visibleVideoParticipants.add(videoParticipant);
                            if (videoParticipant.aspectRatio > 1f) {
                                wideVideoIndex = visibleVideoParticipants.size() - 1;
                            }
                        }
                    }
                } else {
                    visibleParticipants.add(participant);
                }
            }

            if (!GroupCallActivity.isLandscapeMode && visibleVideoParticipants.size() % 2 == 1) {
                VideoParticipant videoParticipant = visibleVideoParticipants.remove(wideVideoIndex);
                visibleVideoParticipants.add(videoParticipant);
            }
        }

        public boolean canRecordVideo() {
            if (!canStreamVideo) {
                return false;
            }
            VoIPService voIPService = VoIPService.getSharedInstance();
            if (voIPService != null && voIPService.groupCall == this && (voIPService.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE || voIPService.getVideoState(true) == Instance.VIDEO_STATE_ACTIVE)) {
                return true;
            }
            return activeVideos < call.unmuted_video_limit;
        }

        public void saveActiveDates() {
            for (int a = 0, N = sortedParticipants.size(); a < N; a++) {
                TLRPC.TL_groupCallParticipant p = sortedParticipants.get(a);
                p.lastActiveDate = p.active_date;
            }
        }

        private void checkOnlineParticipants() {
            if (typingUpdateRunnableScheduled) {
                AndroidUtilities.cancelRunOnUIThread(typingUpdateRunnable);
                typingUpdateRunnableScheduled = false;
            }
            speakingMembersCount = 0;
            int currentTime = currentAccount.getConnectionsManager().getCurrentTime();
            int minDiff = Integer.MAX_VALUE;
            for (int a = 0, N = sortedParticipants.size(); a < N; a++) {
                TLRPC.TL_groupCallParticipant participant = sortedParticipants.get(a);
                int diff = currentTime - participant.active_date;
                if (diff < 5) {
                    speakingMembersCount++;
                    minDiff = Math.min(diff, minDiff);
                }
                if (Math.max(participant.date, participant.active_date) <= currentTime - 5) {
                    break;
                }
            }
            if (minDiff != Integer.MAX_VALUE) {
                AndroidUtilities.runOnUIThread(typingUpdateRunnable, minDiff * 1000);
                typingUpdateRunnableScheduled = true;
            }
        }

        public void toggleRecord(String title, @RecordType int type) {
            recording = !recording;
            TLRPC.TL_phone_toggleGroupCallRecord req = new TLRPC.TL_phone_toggleGroupCallRecord();
            req.call = getInputGroupCall();
            req.start = recording;
            if (title != null) {
                req.title = title;
                req.flags |= 2;
            }
            if (type == RECORD_TYPE_VIDEO_PORTAIT || type == RECORD_TYPE_VIDEO_LANDSCAPE) {
                req.flags |= 4;
                req.video = true;
                req.video_portrait = type == RECORD_TYPE_VIDEO_PORTAIT;
            }
            currentAccount.getConnectionsManager().sendRequest(req, (response, error) -> {
                if (response != null) {
                    final TLRPC.Updates res = (TLRPC.Updates) response;
                    currentAccount.getMessagesController().processUpdates(res, false);
                }
            });
            currentAccount.getNotificationCenter().postNotificationName(NotificationCenter.groupCallUpdated, chatId, call.id, false);
        }
    }

    public static int getParticipantVolume(TLRPC.TL_groupCallParticipant participant) {
        return ((participant.flags & 128) != 0 ? participant.volume : 10000);
    }

    private static boolean isBannableAction(int action) {
        switch (action) {
            case ACTION_PIN:
            case ACTION_CHANGE_INFO:
            case ACTION_INVITE:
            case ACTION_SEND:
            case ACTION_SEND_MEDIA:
            case ACTION_SEND_STICKERS:
            case ACTION_EMBED_LINKS:
            case ACTION_SEND_POLLS:
            case ACTION_VIEW:
            case ACTION_MANAGE_TOPICS:
            case ACTION_SEND_PHOTO:
            case ACTION_SEND_VIDEO:
            case ACTION_SEND_MUSIC:
            case ACTION_SEND_DOCUMENTS:
            case ACTION_SEND_VOICE:
            case ACTION_SEND_ROUND:
            case ACTION_SEND_PLAIN:
                return true;
        }
        return false;
    }

    private static boolean isAdminAction(int action) {
        switch (action) {
            case ACTION_PIN:
            case ACTION_CHANGE_INFO:
            case ACTION_INVITE:
            case ACTION_ADD_ADMINS:
            case ACTION_POST:
            case ACTION_EDIT_MESSAGES:
            case ACTION_DELETE_MESSAGES:
            case ACTION_BLOCK_USERS:
            case ACTION_MANAGE_TOPICS:
                return true;
        }
        return false;
    }

    private static boolean getBannedRight(TLRPC.TL_chatBannedRights rights, int action) {
        if (rights == null) {
            return false;
        }
        boolean value;
        switch (action) {
            case ACTION_PIN:
                return rights.pin_messages;
            case ACTION_CHANGE_INFO:
                return rights.change_info;
            case ACTION_INVITE:
                return rights.invite_users;
            case ACTION_SEND:
                return rights.send_messages;
            case ACTION_SEND_MEDIA:
                return rights.send_media;
            case ACTION_SEND_STICKERS:
                return rights.send_stickers;
            case ACTION_EMBED_LINKS:
                return rights.embed_links;
            case ACTION_SEND_POLLS:
                return rights.send_polls;
            case ACTION_VIEW:
                return rights.view_messages;
            case ACTION_MANAGE_TOPICS:
                return rights.manage_topics;
            case ACTION_SEND_PHOTO:
                return rights.send_photos;
            case ACTION_SEND_VIDEO:
                return rights.send_videos;
            case ACTION_SEND_MUSIC:
                return rights.send_audios;
            case ACTION_SEND_DOCUMENTS:
                return rights.send_docs;
            case ACTION_SEND_VOICE:
                return rights.send_voices;
            case ACTION_SEND_ROUND:
                return rights.send_roundvideos;
            case ACTION_SEND_PLAIN:
                return rights.send_plain;
        }
        return false;
    }

    public static boolean isActionBannedByDefault(TLRPC.Chat chat, int action) {
        if (chat == null) {
            return false;
        }
        if (getBannedRight(chat.banned_rights, action) && getBannedRight(chat.default_banned_rights, action)) {
            return true;
        }
        return getBannedRight(chat.default_banned_rights, action);
    }

    public static boolean isActionBanned(TLRPC.Chat chat, int action) {
        return chat != null && (getBannedRight(chat.banned_rights, action) || getBannedRight(chat.default_banned_rights, action));
    }

    public static boolean canUserDoAdminAction(TLRPC.TL_chatAdminRights admin_rights, int action) {
        if (admin_rights != null) {
            boolean value;
            switch (action) {
                case ACTION_PIN:
                    value = admin_rights.pin_messages;
                    break;
                case ACTION_MANAGE_TOPICS:
                    value = admin_rights.manage_topics;
                    break;
                case ACTION_CHANGE_INFO:
                    value = admin_rights.change_info;
                    break;
                case ACTION_INVITE:
                    value = admin_rights.invite_users;
                    break;
                case ACTION_ADD_ADMINS:
                    value = admin_rights.add_admins;
                    break;
                case ACTION_POST:
                    value = admin_rights.post_messages;
                    break;
                case ACTION_EDIT_MESSAGES:
                    value = admin_rights.edit_messages;
                    break;
                case ACTION_DELETE_MESSAGES:
                    value = admin_rights.delete_messages;
                    break;
                case ACTION_BLOCK_USERS:
                    value = admin_rights.ban_users;
                    break;
                case ACTION_MANAGE_CALLS:
                    value = admin_rights.manage_call;
                    break;
                default:
                    value = false;
                    break;
            }
            if (value) {
                return true;
            }
        }
        return false;
    }

    public static boolean canUserDoAdminAction(TLRPC.Chat chat, int action) {
        if (chat == null) {
            return false;
        }
        if (chat.creator) {
            return true;
        }
        if (chat.admin_rights != null) {
            boolean value;
            switch (action) {
                case ACTION_PIN:
                    value = chat.admin_rights.pin_messages;
                    break;
                case ACTION_MANAGE_TOPICS:
                    value = chat.admin_rights.manage_topics;
                    break;
                case ACTION_CHANGE_INFO:
                    value = chat.admin_rights.change_info;
                    break;
                case ACTION_INVITE:
                    value = chat.admin_rights.invite_users;
                    break;
                case ACTION_ADD_ADMINS:
                    value = chat.admin_rights.add_admins;
                    break;
                case ACTION_POST:
                    value = chat.admin_rights.post_messages;
                    break;
                case ACTION_EDIT_MESSAGES:
                    value = chat.admin_rights.edit_messages;
                    break;
                case ACTION_DELETE_MESSAGES:
                    value = chat.admin_rights.delete_messages;
                    break;
                case ACTION_BLOCK_USERS:
                    value = chat.admin_rights.ban_users;
                    break;
                case ACTION_MANAGE_CALLS:
                    value = chat.admin_rights.manage_call;
                    break;
                default:
                    value = false;
                    break;
            }
            if (value) {
                return true;
            }
        }
        return false;
    }

    public static boolean canUserDoAction(TLRPC.Chat chat, TLRPC.ChannelParticipant participant, int action) {
        if (chat == null) {
            return true;
        }
        if (participant == null) {
            return false;
        }
        if (canUserDoAdminAction(participant.admin_rights, action)) {
            return true;
        }
        if (getBannedRight(participant.banned_rights, action)) {
            return false;
        }
        if (isBannableAction(action)) {
            if (participant.admin_rights != null && !isAdminAction(action)) {
                return true;
            }
            if (chat.default_banned_rights == null && (
                    chat instanceof TLRPC.TL_chat_layer92 ||
                            chat instanceof TLRPC.TL_chat_old ||
                            chat instanceof TLRPC.TL_chat_old2 ||
                            chat instanceof TLRPC.TL_channel_layer92 ||
                            chat instanceof TLRPC.TL_channel_layer77 ||
                            chat instanceof TLRPC.TL_channel_layer72 ||
                            chat instanceof TLRPC.TL_channel_layer67 ||
                            chat instanceof TLRPC.TL_channel_layer48 ||
                            chat instanceof TLRPC.TL_channel_old)) {
                return true;
            }
            if (chat.default_banned_rights == null || getBannedRight(chat.default_banned_rights, action)) {
                return false;
            }
            return true;
        }
        return false;
    }

    public static boolean canUserDoAction(TLRPC.Chat chat, int action) {
        if (chat == null) {
            return true;
        }
        if (canUserDoAdminAction(chat, action)) {
            return true;
        }
        if (getBannedRight(chat.banned_rights, action)) {
            return false;
        }
        if (isBannableAction(action)) {
            if (chat.admin_rights != null && !isAdminAction(action)) {
                return true;
            }
            if (chat.default_banned_rights == null && (
                    chat instanceof TLRPC.TL_chat_layer92 ||
                    chat instanceof TLRPC.TL_chat_old ||
                    chat instanceof TLRPC.TL_chat_old2 ||
                    chat instanceof TLRPC.TL_channel_layer92 ||
                    chat instanceof TLRPC.TL_channel_layer77 ||
                    chat instanceof TLRPC.TL_channel_layer72 ||
                    chat instanceof TLRPC.TL_channel_layer67 ||
                    chat instanceof TLRPC.TL_channel_layer48 ||
                    chat instanceof TLRPC.TL_channel_old)) {
                return true;
            }
            if (chat.default_banned_rights == null || getBannedRight(chat.default_banned_rights, action)) {
                return false;
            }
            return true;
        }
        return false;
    }

    public static boolean isLeftFromChat(TLRPC.Chat chat) {
        return chat == null || chat instanceof TLRPC.TL_chatEmpty || chat instanceof TLRPC.TL_chatForbidden || chat instanceof TLRPC.TL_channelForbidden || chat.left || chat.deactivated;
    }

    public static boolean isKickedFromChat(TLRPC.Chat chat) {
        return chat == null || chat instanceof TLRPC.TL_chatEmpty || chat instanceof TLRPC.TL_chatForbidden || chat instanceof TLRPC.TL_channelForbidden || chat.kicked || chat.deactivated || chat.banned_rights != null && chat.banned_rights.view_messages;
    }

    public static boolean isNotInChat(TLRPC.Chat chat) {
        return chat == null || chat instanceof TLRPC.TL_chatEmpty || chat instanceof TLRPC.TL_chatForbidden || chat instanceof TLRPC.TL_channelForbidden || chat.left || chat.kicked || chat.deactivated;
    }

    public static boolean isInChat(TLRPC.Chat chat) {
        if (chat == null || chat instanceof TLRPC.TL_chatEmpty || chat instanceof TLRPC.TL_chatForbidden || chat instanceof TLRPC.TL_channelForbidden) {
            return false;
        }
        if (chat.left || chat.kicked || chat.deactivated) {
            return false;
        }
        return true;
    }

    public static boolean canSendAsPeers(TLRPC.Chat chat) {
        return ChatObject.isChannel(chat) && (!chat.megagroup && chat.signatures && ChatObject.hasAdminRights(chat) && ChatObject.canWriteToChat(chat) || chat.megagroup && (ChatObject.isPublic(chat) || chat.has_geo || chat.has_link));
    }

    public static boolean isChannel(TLRPC.Chat chat) {
        return chat instanceof TLRPC.TL_channel || chat instanceof TLRPC.TL_channelForbidden;
    }

    public static boolean isChannelOrGiga(TLRPC.Chat chat) {
        return (chat instanceof TLRPC.TL_channel || chat instanceof TLRPC.TL_channelForbidden) && (!chat.megagroup || chat.gigagroup);
    }

    public static boolean isMegagroup(TLRPC.Chat chat) {
        return (chat instanceof TLRPC.TL_channel || chat instanceof TLRPC.TL_channelForbidden) && chat.megagroup;
    }

    public static boolean isChannelAndNotMegaGroup(TLRPC.Chat chat) {
        return isChannel(chat) && !isMegagroup(chat);
    }

    public static boolean isBoostSupported(TLRPC.Chat chat) {
        return isChannelAndNotMegaGroup(chat) || isMegagroup(chat);
    }

    public static boolean isBoosted(TLRPC.ChatFull chatFull) {
        return chatFull != null && chatFull.boosts_applied > 0;
    }

    public static boolean isForum(TLRPC.Chat chat) {
        return chat != null && chat.forum;
    }

    public static boolean hasStories(TLRPC.Chat chat) {
        return chat != null && MessagesController.getInstance(UserConfig.selectedAccount).getStoriesController().hasStories(-chat.id);
    }

    public static boolean isMegagroup(int currentAccount, long chatId) {
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chatId);
        return ChatObject.isChannel(chat) && chat.megagroup;
    }

    public static boolean hasAdminRights(TLRPC.Chat chat) {
        return chat != null && (chat.creator || chat.admin_rights != null && chat.admin_rights.flags != 0);
    }

    public static boolean canChangeChatInfo(TLRPC.Chat chat) {
        return canUserDoAction(chat, ACTION_CHANGE_INFO);
    }

    public static boolean canAddAdmins(TLRPC.Chat chat) {
        return canUserDoAction(chat, ACTION_ADD_ADMINS);
    }

    public static boolean canBlockUsers(TLRPC.Chat chat) {
        return canUserDoAction(chat, ACTION_BLOCK_USERS);
    }

    public static boolean canManageCalls(TLRPC.Chat chat) {
        return canUserDoAction(chat, ACTION_MANAGE_CALLS);
    }

    public static boolean canSendStickers(TLRPC.Chat chat) {
        if (isIgnoredChatRestrictionsForBoosters(chat)) {
            return true;
        }
        return canUserDoAction(chat, ACTION_SEND_STICKERS);
    }

    public static boolean canSendEmbed(TLRPC.Chat chat) {
        if (isIgnoredChatRestrictionsForBoosters(chat)) {
            return true;
        }
        return canUserDoAction(chat, ACTION_EMBED_LINKS);
    }

    public static boolean canSendPhoto(TLRPC.Chat chat) {
        if (isIgnoredChatRestrictionsForBoosters(chat)) {
            return true;
        }
        return canUserDoAction(chat, ACTION_SEND_PHOTO);
    }

    public static boolean canSendVideo(TLRPC.Chat chat) {
        if (isIgnoredChatRestrictionsForBoosters(chat)) {
            return true;
        }
        return canUserDoAction(chat, ACTION_SEND_VIDEO);
    }

    public static boolean canSendMusic(TLRPC.Chat chat) {
        if (isIgnoredChatRestrictionsForBoosters(chat)) {
            return true;
        }
        return canUserDoAction(chat, ACTION_SEND_MUSIC);
    }

    public static boolean canSendDocument(TLRPC.Chat chat) {
        if (isIgnoredChatRestrictionsForBoosters(chat)) {
            return true;
        }
        return canUserDoAction(chat, ACTION_SEND_DOCUMENTS);
    }

    public static boolean canSendVoice(TLRPC.Chat chat) {
        if (isIgnoredChatRestrictionsForBoosters(chat)) {
            return true;
        }
        return canUserDoAction(chat, ACTION_SEND_VOICE);
    }

    public static boolean canSendRoundVideo(TLRPC.Chat chat) {
        if (isIgnoredChatRestrictionsForBoosters(chat)) {
            return true;
        }
        return canUserDoAction(chat, ACTION_SEND_ROUND);
    }

    public static boolean canSendPolls(TLRPC.Chat chat) {
        if (isIgnoredChatRestrictionsForBoosters(chat)) {
            return true;
        }
        return canUserDoAction(chat, ACTION_SEND_POLLS);
    }

    public static boolean canSendMessages(TLRPC.Chat chat) {
        if (isIgnoredChatRestrictionsForBoosters(chat)) {
            return true;
        }
        return canUserDoAction(chat, ACTION_SEND);
    }

    public static boolean canSendPlain(TLRPC.Chat chat) {
        if (isIgnoredChatRestrictionsForBoosters(chat)) {
            return true;
        }
        return canUserDoAction(chat, ACTION_SEND_PLAIN);
    }

    public static boolean canPost(TLRPC.Chat chat) {
        return canUserDoAction(chat, ACTION_POST);
    }

    public static boolean canAddUsers(TLRPC.Chat chat) {
        return canUserDoAction(chat, ACTION_INVITE);
    }

    public static boolean shouldSendAnonymously(TLRPC.Chat chat) {
        return chat != null && chat.admin_rights != null && chat.admin_rights.anonymous;
    }

    public static long getSendAsPeerId(TLRPC.Chat chat, TLRPC.ChatFull chatFull) {
        return getSendAsPeerId(chat, chatFull, false);
    }

    public static long getSendAsPeerId(TLRPC.Chat chat, TLRPC.ChatFull chatFull, boolean invertChannel) {
        if (chat != null && chatFull != null && chatFull.default_send_as != null) {
            TLRPC.Peer p = chatFull.default_send_as;
            return p.user_id != 0 ? p.user_id : invertChannel ? -p.channel_id : p.channel_id;
        }
        if (chat != null && chat.admin_rights != null && chat.admin_rights.anonymous) {
            return invertChannel ? -chat.id : chat.id;
        }
        if (chat != null && ChatObject.isChannelAndNotMegaGroup(chat) && !chat.signatures) {
            return invertChannel ? -chat.id : chat.id;
        }
        return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
    }

    public static boolean canAddBotsToChat(TLRPC.Chat chat) {
        if (isChannel(chat)) {
            if (chat.megagroup && (chat.admin_rights != null && (chat.admin_rights.post_messages || chat.admin_rights.add_admins) || chat.creator)) {
                return true;
            }
        } else {
            if (chat.migrated_to == null) {
                return true;
            }
        }
        return false;
    }

    public static boolean canPinMessages(TLRPC.Chat chat) {
        return canUserDoAction(chat, ACTION_PIN) || ChatObject.isChannel(chat) && !chat.megagroup && chat.admin_rights != null && chat.admin_rights.edit_messages;
    }

    public static boolean canCreateTopic(TLRPC.Chat chat) {
        return canUserDoAction(chat, ACTION_MANAGE_TOPICS);
    }

    public static boolean canManageTopics(TLRPC.Chat chat) {
        return canUserDoAdminAction(chat, ACTION_MANAGE_TOPICS);
    }

    public static boolean canManageTopic(int currentAccount, TLRPC.Chat chat, TLRPC.TL_forumTopic topic) {
        return canManageTopics(chat) || isMyTopic(currentAccount, topic);
    }
    public static boolean canManageTopic(int currentAccount, TLRPC.Chat chat, long topicId) {
        return canManageTopics(chat) || isMyTopic(currentAccount, chat, topicId);
    }

    public static boolean canDeleteTopic(int currentAccount, TLRPC.Chat chat, long topicId) {
        if (topicId == 1) {
            // general topic can't be deleted
            return false;
        }
        return chat != null && canDeleteTopic(currentAccount, chat, MessagesController.getInstance(currentAccount).getTopicsController().findTopic(chat.id, topicId));
    }
    public static boolean canDeleteTopic(int currentAccount, TLRPC.Chat chat, TLRPC.TL_forumTopic topic) {
        if (topic != null && topic.id == 1) {
            // general topic can't be deleted
            return false;
        }
        return canUserDoAction(chat, ACTION_DELETE_MESSAGES) || isMyTopic(currentAccount, topic) && topic.topMessage != null && topic.topicStartMessage != null && topic.topMessage.id - topic.topicStartMessage.id <= Math.max(1, topic.groupedMessages == null ? 0 : topic.groupedMessages.size()) && MessageObject.peersEqual(topic.from_id, topic.topMessage.from_id);
    }

    public static boolean isMyTopic(int currentAccount, TLRPC.TL_forumTopic topic) {
        return topic != null && (topic.my || topic.from_id instanceof TLRPC.TL_peerUser && topic.from_id.user_id == UserConfig.getInstance(currentAccount).clientUserId);
    }

    public static boolean isMyTopic(int currentAccount, TLRPC.Chat chat, long topicId) {
        return chat != null && chat.forum && isMyTopic(currentAccount, chat.id, topicId);
    }

    public static boolean isMyTopic(int currentAccount, long chatId, long topicId) {
        return isMyTopic(currentAccount, MessagesController.getInstance(currentAccount).getTopicsController().findTopic(chatId, topicId));
    }

    public static boolean isChannel(long chatId, int currentAccount) {
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chatId);
        return chat instanceof TLRPC.TL_channel || chat instanceof TLRPC.TL_channelForbidden;
    }

    public static boolean isChannelAndNotMegaGroup(long chatId, int currentAccount) {
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chatId);
        return isChannelAndNotMegaGroup(chat);
    }

    public static boolean isCanWriteToChannel(long chatId, int currentAccount) {
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chatId);
        return ChatObject.canSendMessages(chat) || chat.megagroup;
    }

    public static boolean canWriteToChat(TLRPC.Chat chat) {
        return !isChannel(chat) || chat.creator || chat.admin_rights != null && chat.admin_rights.post_messages || !chat.broadcast && !chat.gigagroup || chat.gigagroup && ChatObject.hasAdminRights(chat);
    }

    public static String getBannedRightsString(TLRPC.TL_chatBannedRights bannedRights) {
        String currentBannedRights = "";
        currentBannedRights += bannedRights.view_messages ? 1 : 0;
        currentBannedRights += bannedRights.send_messages ? 1 : 0;
        currentBannedRights += bannedRights.send_media ? 1 : 0;
        currentBannedRights += bannedRights.send_stickers ? 1 : 0;
        currentBannedRights += bannedRights.send_gifs ? 1 : 0;
        currentBannedRights += bannedRights.send_games ? 1 : 0;
        currentBannedRights += bannedRights.send_inline ? 1 : 0;
        currentBannedRights += bannedRights.embed_links ? 1 : 0;
        currentBannedRights += bannedRights.send_polls ? 1 : 0;
        currentBannedRights += bannedRights.invite_users ? 1 : 0;
        currentBannedRights += bannedRights.change_info ? 1 : 0;
        currentBannedRights += bannedRights.pin_messages ? 1 : 0;
        currentBannedRights += bannedRights.manage_topics ? 1 : 0;
        currentBannedRights += bannedRights.send_photos ? 1 : 0;
        currentBannedRights += bannedRights.send_videos ? 1 : 0;
        currentBannedRights += bannedRights.send_roundvideos ? 1 : 0;
        currentBannedRights += bannedRights.send_voices ? 1 : 0;
        currentBannedRights += bannedRights.send_audios ? 1 : 0;
        currentBannedRights += bannedRights.send_docs ? 1 : 0;
        currentBannedRights += bannedRights.send_plain ? 1 : 0;
        currentBannedRights += bannedRights.until_date;
        return currentBannedRights;
    }

    public static boolean hasPhoto(TLRPC.Chat chat) {
        return chat != null && chat.photo != null && !(chat.photo instanceof TLRPC.TL_chatPhotoEmpty);
    }

    public static TLRPC.ChatPhoto getPhoto(TLRPC.Chat chat) {
        return hasPhoto(chat) ? chat.photo : null;
    }

    public static String getPublicUsername(TLRPC.Chat chat) {
        return getPublicUsername(chat, false);
    }

    public static String getPublicUsername(TLRPC.Chat chat, boolean editable) {
        if (chat == null) {
            return null;
        }
        if (!TextUtils.isEmpty(chat.username) && !editable) {
            return chat.username;
        }
        if (chat.usernames != null) {
            for (int i = 0; i < chat.usernames.size(); ++i) {
                TLRPC.TL_username u = chat.usernames.get(i);
                if (u != null && (u.active && !editable || u.editable) && !TextUtils.isEmpty(u.username)) {
                    return u.username;
                }
            }
        }
        if (!TextUtils.isEmpty(chat.username) && editable && (chat.usernames == null || chat.usernames.size() <= 0)) {
            return chat.username;
        }
        return null;
    }

    public static boolean hasPublicLink(TLRPC.Chat chat, String username) {
        if (chat == null) {
            return false;
        }
        if (!TextUtils.isEmpty(chat.username)) {
            return chat.username.equalsIgnoreCase(username);
        }
        if (chat.usernames != null) {
            for (int i = 0; i < chat.usernames.size(); ++i) {
                TLRPC.TL_username u = chat.usernames.get(i);
                if (u != null && u.active && !TextUtils.isEmpty(u.username) && u.username.equalsIgnoreCase(username)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isPublic(TLRPC.Chat chat) {
        return !TextUtils.isEmpty(getPublicUsername(chat));
    }

    public static String getRestrictedErrorText(TLRPC.Chat chat, int action) {
        if (action == ACTION_SEND_GIFS) {
            if (chat == null || ChatObject.isActionBannedByDefault(chat, action)) {
                return LocaleController.getString(R.string.GlobalAttachGifRestricted);
            } else if (AndroidUtilities.isBannedForever(chat.banned_rights)) {
                return LocaleController.formatString("AttachGifRestrictedForever", R.string.AttachGifRestrictedForever);
            } else {
                return LocaleController.formatString("AttachGifRestricted", R.string.AttachGifRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date));
            }
        } else if (action == ACTION_SEND_STICKERS) {
            if (chat == null || ChatObject.isActionBannedByDefault(chat, action)) {
                return LocaleController.getString(R.string.GlobalAttachStickersRestricted);
            } else if (AndroidUtilities.isBannedForever(chat.banned_rights)) {
                return LocaleController.formatString("AttachStickersRestrictedForever", R.string.AttachStickersRestrictedForever);
            } else {
                return LocaleController.formatString("AttachStickersRestricted", R.string.AttachStickersRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date));
            }
        } else if (action == ACTION_SEND_PHOTO) {
            if (chat == null || ChatObject.isActionBannedByDefault(chat, action)) {
                return LocaleController.getString(R.string.GlobalAttachPhotoRestricted);
            } else if (AndroidUtilities.isBannedForever(chat.banned_rights)) {
                return LocaleController.formatString("AttachPhotoRestrictedForever", R.string.AttachPhotoRestrictedForever);
            } else {
                return LocaleController.formatString("AttachPhotoRestricted", R.string.AttachPhotoRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date));
            }
        } else if (action == ACTION_SEND_VIDEO) {
            if (chat == null || ChatObject.isActionBannedByDefault(chat, action)) {
                return LocaleController.getString(R.string.GlobalAttachVideoRestricted);
            } else if (AndroidUtilities.isBannedForever(chat.banned_rights)) {
                return LocaleController.formatString("AttachVideoRestrictedForever", R.string.AttachVideoRestrictedForever);
            } else {
                return LocaleController.formatString("AttachVideoRestricted", R.string.AttachVideoRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date));
            }
        } else if (action == ACTION_SEND_DOCUMENTS) {
            if (chat == null || ChatObject.isActionBannedByDefault(chat, action)) {
                return LocaleController.getString(R.string.GlobalAttachDocumentsRestricted);
            } else if (AndroidUtilities.isBannedForever(chat.banned_rights)) {
                return LocaleController.formatString("AttachDocumentsRestrictedForever", R.string.AttachDocumentsRestrictedForever);
            } else {
                return LocaleController.formatString("AttachDocumentsRestricted", R.string.AttachDocumentsRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date));
            }
        } else if (action == ACTION_SEND_MEDIA) {
            if (chat == null || ChatObject.isActionBannedByDefault(chat, action)) {
                return LocaleController.getString(R.string.GlobalAttachMediaRestricted);
            } else if (AndroidUtilities.isBannedForever(chat.banned_rights)) {
                return LocaleController.formatString("AttachMediaRestrictedForever", R.string.AttachMediaRestrictedForever);
            } else {
                return LocaleController.formatString("AttachMediaRestricted", R.string.AttachMediaRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date));
            }
        } else if (action == ACTION_SEND_MUSIC) {
            if (chat == null || ChatObject.isActionBannedByDefault(chat, action)) {
                return LocaleController.getString(R.string.GlobalAttachAudioRestricted);
            } else if (AndroidUtilities.isBannedForever(chat.banned_rights)) {
                return LocaleController.formatString("AttachAudioRestrictedForever", R.string.AttachAudioRestrictedForever);
            } else {
                return LocaleController.formatString("AttachAudioRestricted", R.string.AttachAudioRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date));
            }
        } else if (action == ACTION_SEND_PLAIN) {
            if (chat == null || ChatObject.isActionBannedByDefault(chat, action)) {
                return LocaleController.getString(R.string.GlobalAttachPlainRestricted);
            } else if (AndroidUtilities.isBannedForever(chat.banned_rights)) {
                return LocaleController.formatString("AttachPlainRestrictedForever", R.string.AttachPlainRestrictedForever);
            } else {
                return LocaleController.formatString("AttachPlainRestricted", R.string.AttachPlainRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date));
            }
        } else if (action == ACTION_SEND_ROUND) {
            if (chat == null || ChatObject.isActionBannedByDefault(chat, action)) {
                return LocaleController.getString(R.string.GlobalAttachRoundRestricted);
            } else if (AndroidUtilities.isBannedForever(chat.banned_rights)) {
                return LocaleController.formatString("AttachRoundRestrictedForever", R.string.AttachRoundRestrictedForever);
            } else {
                return LocaleController.formatString("AttachRoundRestricted", R.string.AttachRoundRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date));
            }
        } else if (action == ACTION_SEND_VOICE) {
            if (chat == null || ChatObject.isActionBannedByDefault(chat, action)) {
                return LocaleController.getString(R.string.GlobalAttachVoiceRestricted);
            } else if (AndroidUtilities.isBannedForever(chat.banned_rights)) {
                return LocaleController.formatString("AttachVoiceRestrictedForever", R.string.AttachVoiceRestrictedForever);
            } else {
                return LocaleController.formatString("AttachVoiceRestricted", R.string.AttachVoiceRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date));
            }
        }

        return "";
    }


    public static class VideoParticipant {

        public TLRPC.TL_groupCallParticipant participant;
        public boolean presentation;
        public boolean hasSame;
        public float aspectRatio;// w / h
        public int aspectRatioFromWidth;
        public int aspectRatioFromHeight;

        public VideoParticipant(TLRPC.TL_groupCallParticipant participant, boolean presentation, boolean hasSame) {
            this.participant = participant;
            this.presentation = presentation;
            this.hasSame = hasSame;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            VideoParticipant that = (VideoParticipant) o;
            return presentation == that.presentation && MessageObject.getPeerId(participant.peer) == MessageObject.getPeerId(that.participant.peer);
        }

        public void setAspectRatio(int width, int height, Call call) {
            aspectRatioFromWidth = width;
            aspectRatioFromHeight = height;
            setAspectRatio(width / (float) height, call);
        }

        private void setAspectRatio(float aspectRatio, Call call) {
            if (this.aspectRatio != aspectRatio) {
                this.aspectRatio = aspectRatio;
                if (!GroupCallActivity.isLandscapeMode && call.visibleVideoParticipants.size() % 2 == 1) {
                    call.updateVisibleParticipants();
                }
            }
        }
    }

    public static MessagesController.PeerColor getPeerColorForAvatar(int currentAccount, TLRPC.Chat chat) {
//        if (chat != null && chat.profile_color != null && chat.profile_color.color >= 0 && MessagesController.getInstance(currentAccount).profilePeerColors != null) {
//            return MessagesController.getInstance(currentAccount).profilePeerColors.getColor(chat.profile_color.color);
//        }
        return null;
    }

    public static int getColorId(TLRPC.Chat chat) {
        if (chat == null) return 0;
        if (chat.color != null && (chat.color.flags & 1) != 0) return chat.color.color;
        return (int) (chat.id % 7);
    }

    public static long getEmojiId(TLRPC.Chat chat) {
        if (chat != null && chat.color != null && (chat.color.flags & 2) != 0) return chat.color.background_emoji_id;
        return 0;
    }

    public static int getProfileColorId(TLRPC.Chat chat) {
        if (chat == null) return 0;
        if (chat.profile_color != null && (chat.profile_color.flags & 1) != 0) return chat.profile_color.color;
        return -1;
    }

    public static long getProfileEmojiId(TLRPC.Chat chat) {
        if (chat != null && chat.profile_color != null && (chat.profile_color.flags & 2) != 0) return chat.profile_color.background_emoji_id;
        return 0;
    }

}
