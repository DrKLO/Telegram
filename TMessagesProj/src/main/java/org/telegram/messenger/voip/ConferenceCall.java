package org.telegram.messenger.voip;

import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.SparseIntArray;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_phone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Keep
public class ConferenceCall {

    @Keep
    public static final class CallParticipant {
        long user_id;
        long public_key_id;
        int permissions;

        @NonNull
        @Override
        public String toString() {
            return "CallParticipant{user_id=" + user_id + ", public_key_id=" + public_key_id + "}";
        }
    }

    @Keep
    public static final class CallState {
        int height;
        CallParticipant[] participants;

        public CallParticipant find(long user_id) {
            CallParticipant p = null;
            for (int i = 0; i < participants.length; ++i) {
                if (participants[i].user_id == user_id) {
                    p = participants[i];
                    break;
                }
            }
            return p;
        }

        @NonNull
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("CallState{height=" + height + ", participants=[");
            for (int i = 0; i < participants.length; ++i) {
                if (i > 0) sb.append(", ");
                if (participants[i] == null) {
                    sb.append("null");
                } else {
                    sb.append(participants[i].toString());
                }
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    private static String blockStr(byte[] block) {
        return "Block{" + Utilities.bytesToHex(block) + "}";
    }

    @Keep
    public static final class CallVerificationState {
        int height;
        byte[] emoji_hash;

        @NonNull
        @Override
        public String toString() {
            return "CallVerificationState{height=" + height + ", emoji_hash=" + (emoji_hash == null ? null : "{" + Utilities.bytesToHex(emoji_hash) + "}") + "}";
        }
    }

    @Keep
    public static final class CallVerificationWords {
        int height;
        String[] words;

        @NonNull
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("CallVerificationWords{height=" + height + ", words=[");
            for (int i = 0; i < words.length; ++i) {
                if (i > 0) sb.append(", ");
                sb.append(words[i]);
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    public static native long key_generate_temporary_private_key();
    public static native byte[] key_to_public_key(long private_key);
    public static native long key_from_public_key(byte[] public_key);

    public static native byte[] call_create_zero_block(long private_key_id, CallState initial_state);
    public static native byte[] call_create_self_add_block(long private_key_id, byte[] previous_block, CallParticipant self);
    public static native long call_create(long user_id, long private_key_id, byte[] last_block);

    public static native byte[] call_create_change_state_block(long call_id, CallState new_state);

    public static native int call_get_height(long call_id);
    public static native CallState call_apply_block(long call_id, byte[] block);
    public static native CallState call_get_state(long call_id);
    public static native String call_describe(long call_id);
    public static native String call_describe_block(byte[] block);
    public static native String call_describe_message(byte[] message);

    public static native CallVerificationState call_get_verification_state(long call_id);
    public static native CallVerificationState call_receive_inbound_message(long call_id, byte[] message);

    public static native byte[][] call_pull_outbound_messages(long call_id);

    public static native CallVerificationWords call_get_verification_words(long call_id);

    public static native void call_destroy(long call_id);
    public static native void call_destroy_all();

    public static final int PERMISSION_ADD = 1;
    public static final int PERMISSION_REMOVE = 2;

    public boolean destroyed;
    public boolean joined;
    private int currentAccount;
    private long my_user_id;

    private long my_private_key_id;
    private byte[] my_public_key;
    private long my_public_key_id;

    private byte[] zero_block;
    private byte[] last_block;

    private CallState state;
    private long call_id = -1;

    public TLRPC.GroupCall groupCall;
    public TLRPC.InputGroupCall inputGroupCall;

    private String[] lastVerificationEmojis;

    private void checkEmojiHash() {
        final String[] currentEmojis = getVerificationEmojis();
        if (!eq(currentEmojis, lastVerificationEmojis)) {
            this.lastVerificationEmojis = currentEmojis;
            AndroidUtilities.runOnUIThread(() -> {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.conferenceEmojiUpdated);
            });
        }
    }

    public final HashSet<Long> joiningBlockchainParticipants = new HashSet<>();
    private HashSet<Long> lastParticipants = null;

    private void checkParticipants() {
        HashSet<Long> currentParticipants = null;
        if (call_id >= 0) {
            try {
                final CallState state = call_get_state(call_id);
                if (state != null && state.participants.length > 0) {
                    currentParticipants = new HashSet<>();
                    for (int i = 0; i < state.participants.length; ++i) {
                        currentParticipants.add(state.participants[i].user_id);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (!eq(currentParticipants, lastParticipants)) {
            if (this.lastParticipants != null && currentParticipants != null) {
                for (long id : currentParticipants) {
                    if (!this.lastParticipants.contains(id)) {
                        joiningBlockchainParticipants.add(id);
                    }
                }
                final Iterator<Long> it = joiningBlockchainParticipants.iterator();
                while (it.hasNext()) {
                    final long id = it.next();
                    if (this.lastParticipants.contains(id)) {
                        it.remove();
                    }
                }
            } else {
                joiningBlockchainParticipants.clear();
            }
            this.lastParticipants = currentParticipants;
            AndroidUtilities.runOnUIThread(() -> {
                if (groupCall == null) {
                    return;
                }
                final VoIPService voip = VoIPService.getSharedInstance();
                if (voip == null || voip.groupCall == null || voip.groupCall.call == null || voip.groupCall.call.id != groupCall.id) {
                    return;
                }
                updateParticipants(voip.groupCall.sortedParticipants, false);
                voip.groupCall.shadyLeftParticipants.clear();
                voip.groupCall.shadyLeftParticipants.addAll(voip.conference.getShadyLeftParticipants(voip.groupCall.sortedParticipants));
                voip.groupCall.shadyJoinParticipants.clear();
                voip.groupCall.shadyJoinParticipants.addAll(voip.conference.getShadyJoiningParticipants(voip.groupCall.sortedParticipants));
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.groupCallUpdated, 0L, groupCall.id, false);
            });
        }
    }

    private boolean eq(String[] a, String[] b) {
        if (a == b) return true;
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; ++i)
            if (!TextUtils.equals(a[i], b[i]))
                return false;
        return true;
    }

    private boolean eq(HashSet<Long> a, HashSet<Long> b) {
        if (a == b) return true;
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (long id : a)
            if (!b.contains(id))
                return false;
        return true;
    }

    private String[] getVerificationEmojis() {
        if (call_id < 0) return null;
        byte[] emoji_hash = null;
        try {
            emoji_hash = call_get_verification_state(call_id).emoji_hash;
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (emoji_hash == null) return null;
        if (emoji_hash.length > 32) { // TODO
            byte[] new_emoji_hash = new byte[32];
            System.arraycopy(emoji_hash, 0, new_emoji_hash, 0, 32);
            emoji_hash = new_emoji_hash;
        }
        return EncryptionKeyEmojifier.emojifyForCall(emoji_hash);
    }

    public String[] getEmojis() {
        return lastVerificationEmojis;
    }

    public ConferenceCall(int currentAccount, long my_user_id) {
        this.currentAccount = currentAccount;
        this.my_user_id = my_user_id;
        init();
    }

    private void init() {
        my_private_key_id = key_generate_temporary_private_key();
        my_public_key = key_to_public_key(my_private_key_id);
        my_public_key_id = key_from_public_key(my_public_key);

        state = new CallState();
        state.height = 1;
        state.participants = new CallParticipant[1];
        state.participants[0] = new CallParticipant();
        state.participants[0].user_id = my_user_id;
        state.participants[0].public_key_id = my_public_key_id;
        state.participants[0].permissions = PERMISSION_ADD | PERMISSION_REMOVE;
    }

    protected void gotCallId(long call_id) {

    }

    public byte[] getMyPublicKey() {
        return my_public_key;
    }

    public void requestLastBlock(Runnable done) {
        final long time = System.currentTimeMillis();
        final TL_phone.getGroupCallChainBlocks req = new TL_phone.getGroupCallChainBlocks();
        req.call = inputGroupCall;
        req.sub_chain_id = 0;
        req.offset = -1;
        req.limit = 1;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            processUpdates(-1, time, res, err);
            if (done != null) {
                done.run();
            }
        }));
    }

    private boolean processUpdates(Integer requested_offset, Long requestTime, TLObject res, TLRPC.TL_error err) {
        boolean hadBlocks = false;
        if (res instanceof TLRPC.Updates) {
            final TLRPC.Updates u = (TLRPC.Updates) res;
            for (TLRPC.TL_updateGroupCallChainBlocks update : MessagesController.findUpdatesAndRemove(u, TLRPC.TL_updateGroupCallChainBlocks.class)) {
                final boolean thisHadBlocks = applyUpdate(requested_offset, update, false, requestTime);
                if (thisHadBlocks) hadBlocks = true;
            }
            Utilities.stageQueue.postRunnable(() -> {
                MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) res, false);
            });
        }
        return hadBlocks;
    }

    public byte[] getLastBlock() {
        return last_block;
    }

    public byte[] generateAddSelfBlock() {
        final CallParticipant self = new CallParticipant();
        self.user_id = my_user_id;
        self.public_key_id = my_public_key_id;
        self.permissions = PERMISSION_ADD | PERMISSION_REMOVE;

        if (last_block == null) {
            state = new CallState();
            state.height = 1;
            state.participants = new CallParticipant[1];
            state.participants[0] = self;

            zero_block = call_create_zero_block(my_private_key_id, state);
            last_block = zero_block;

            FileLog.d("[tde2e] call_create_zero_block(" + my_private_key_id + ", " + state + ")");
        } else {
            try {
                final byte[] new_block = call_create_self_add_block(my_private_key_id, last_block, self);
                FileLog.d("[tde2e] call_create_self_add_block(" + my_private_key_id + ", " + blockStr(last_block) + ", " + state + ") = " + blockStr(new_block));
                FileLog.d("[tde2e] call_create_self_add_block last_block=" + call_describe_block(last_block) + " new_block=" + call_describe_block(new_block));
                last_block = new_block;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return last_block;
    }

    public CallVerificationWords getVerificationWords() {
        return call_get_verification_words(call_id);
    }

    public CallVerificationState getVerificationState() {
        return call_get_verification_state(call_id);
    }

    private final int[] last_offset = new int[]{ -1, -1 };
//    private final long[] blockchainUpToDateTime = new long[]{0, 0};
    private final LongSparseArray<byte[]>[] blocksQueue = new LongSparseArray[]{new LongSparseArray(), new LongSparseArray()};

    private void readQueue(int sub_chain) {
        if (destroyed) {
            FileLog.d("[tde2e] conference.readQueue("+sub_chain+") but destroyed!");
            return;
        }
        if (sub_chain != 0 && call_id < 0) {
            FileLog.d("[tde2e] conference.readQueue("+sub_chain+") but there is no call yet!");
            return;
        }
        if (last_offset[sub_chain] == -1) {
            FileLog.d("[tde2e] conference.readQueue("+sub_chain+") but last_offset == -1!");
            return;
        }

        int index = Math.max(0, last_offset[sub_chain]);
        FileLog.d("[tde2e] {subchain: "+sub_chain+"} processing blocks queue from " + index );
        while (true) {
            byte[] block = blocksQueue[sub_chain].get(index);
            if (block == null) {
                FileLog.d("[tde2e] {subchain: "+sub_chain+"} got into hole (might be the end) in " + sub_chain + " subchain at #" + index + ", when our last_offset[" + sub_chain + "] = " + last_offset[sub_chain]);
                last_offset[sub_chain] = index;
                break;
            } else {
                try {
                    FileLog.d("[tde2e] {subchain: " + sub_chain + "} processing #" + index + " block from queue");
                    blocksQueue[sub_chain].remove(index);
                    if (call_id < 0) {
                        FileLog.d("[tde2e] #" + index + " call_create block=" + call_describe_block(block));
                        call_id = call_create(my_user_id, my_private_key_id, last_block = block);
                        gotCallId(call_id);
                    } else {
                        if (sub_chain == 0) {
                            if (index > call_get_height(call_id)) {
                                FileLog.d("[tde2e] #" + index + " call_apply_block block=" + call_describe_block(block));
                                FileLog.d("[tde2e] #" + index + " call_apply_block(" + call_id + ", " + blockStr(block) + ") = " + call_apply_block(call_id, block));
                            } else {
                                FileLog.d("[tde2e] #" + index + " block from queue is under call's height!");
                            }
                        } else if (sub_chain == 1) {
                            FileLog.d("[tde2e] #" + index + " call_receive_inbound_message message=" + call_describe_message(block));
                            FileLog.d("[tde2e] #" + index + " call_receive_inbound_message(" + call_id + ", " + blockStr(block) + ") = " + call_receive_inbound_message(call_id, block));
                        }
                    }
                    index++;
                    last_offset[sub_chain] = index;
                } catch (Exception e) {
                    FileLog.e("[tde2e] {subchain: " + sub_chain + "} #" + index + " block got into error: ", e);
                    return;
                }
            }
        }
    }

    public boolean applyUpdate(Integer requested_offset, TLRPC.TL_updateGroupCallChainBlocks update, boolean allowForcePoll, Long requestTime) {
        if (destroyed) {
            FileLog.d("[tde2e] conference.applyUpdate but destroyed!");
            return false;
        }
        if (update == null) return false;
        if (groupCall == null) {
            FileLog.d("[tde2e] received updateGroupCallChainBlocks but we dont have groupcall yet!");
            return false;
        }
        if (update.call.id != groupCall.id) {
            FileLog.d("[tde2e] received updateGroupCallChainBlocks for " + update.call.id + " but we have " + groupCall.id);
            return false;
        }
        FileLog.d("[tde2e] received update with " + update.blocks.size() + " blocks for " + update.sub_chain_id + " subchain, next_offset=" + update.next_offset + " requested_offset=" + requested_offset + (requestTime != null ? " in " + (System.currentTimeMillis() - requestTime) + "ms" : ""));

//        boolean created_call = false;
        final int sub_chain = update.sub_chain_id;
        final int next_offset = update.next_offset;
        if (sub_chain == 0 || sub_chain == 1) {
            for (int i = 0; i < update.blocks.size(); ++i) {
                final byte[] block = update.blocks.get(i);
                final int index = next_offset - update.blocks.size() + i;

                if (requested_offset != null && requested_offset == -1) {
                    // we requested last block here only
                    if (sub_chain == 0) {
                        last_block = block;
                    }
                } else if (index >= last_offset[sub_chain]) {
                    FileLog.d("[tde2e] {subchain: "+sub_chain+"} put #" + index + " into queue");
                    blocksQueue[sub_chain].put(index, block);
                } else {
                    FileLog.d("[tde2e] {subchain: "+sub_chain+"} received #" + index + " that was already processed from queue");
                }
            }

            if (last_offset[sub_chain] == -1) {
                if (requested_offset != null && requested_offset == 0) {
                    last_offset[sub_chain] = next_offset - update.blocks.size();
                } else if (requested_offset != null && requested_offset == -1) {
                    FileLog.d("[tde2e] no offset, but we were asking for last block anyway");
//                } else if (requested_offset == null) {
//                    FileLog.e("[tde2e] received update where we can't know what the start offset is of " + sub_chain + " sub chain (we requested " + requested_offset + ")");
//                    FileLog.d("[tde2e] assuming this is first chain blocks");
//                    last_offset[sub_chain] = next_offset - update.blocks.size();
                } else {
                    FileLog.e("[tde2e] received update where we can't know what the start offset is of " + sub_chain + " sub chain (we requested " + requested_offset + ")");
                }
            }

            if (last_offset[sub_chain] != -1) {
                final boolean had_call = call_id >= 0;
                readQueue(sub_chain);
                final boolean has_call = call_id >= 0;
                if (sub_chain == 0 && !had_call && has_call) {
                    readQueue(1);
                }
            }
        }
        if (sub_chain == 1) {
            pull_outbound();
        }
        checkEmojiHash();
        checkParticipants();
        if (allowForcePoll && update.blocks.size() > 0) {
            forcePoll();
        }
        return update.blocks.size() > 0;
    }

    private void pull_outbound() {
        if (destroyed) {
            FileLog.d("[tde2e] conference.pull_outbound but destroyed!");
            return;
        }
        if (call_id < 0) return;

        boolean hadOutgoingBlocks = false;
        try {
            byte[][] out_blocks = call_pull_outbound_messages(call_id);
            FileLog.d("[tde2e] call_pull_outbound_messages(" + call_id + ") = " + out_blocks.length + " blocks");
            for (int i = 0; i < out_blocks.length; ++i) {
                final TL_phone.sendConferenceCallBroadcast req = new TL_phone.sendConferenceCallBroadcast();
                req.call = inputGroupCall;
                req.block = out_blocks[i];
                FileLog.d("[tde2e] pull outbound block to server!");
                FileLog.d("[tde2e] call_pull_outbound_messages(" + call_id + ")[" + i + "] = " + call_describe_message(out_blocks[i]));
                final long time = System.currentTimeMillis();
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    processUpdates(null, time, res, err);
                }), ConnectionsManager.RequestFlagInvokeAfter);
                hadOutgoingBlocks = true;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        try {
            FileLog.d("[tde2e] state = " + call_get_verification_state(call_id));
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            FileLog.d("[tde2e] call_describe("+call_id+"): " + call_describe(call_id));
        } catch (Exception e) {
            FileLog.e(e);
        }

        checkEmojiHash();
        checkParticipants();
        if (hadOutgoingBlocks) {
            forcePoll();
        }
    }

    private long getPollTimeout() {
        if (getVerificationEmojis() == null) {
            return 1000L;
        } else {
            return 5_000L;
        }
    }

    private final Runnable pollRunnable = this::poll;
    private final int[] pollRequestId = new int[2];
    private boolean polling;
    private void poll() {
        if (destroyed) {
            FileLog.d("[tde2e] conference.poll but destroyed!");
            return;
        }
        if (!joined) {
            FileLog.d("[tde2e] conference.poll but not joined!");
            return;
        }
        polling = true;
        final AtomicInteger received = new AtomicInteger(0);
        final AtomicBoolean hadBlocks = new AtomicBoolean(false);
        for (int a = 0; a < 2; ++a) {
            final TL_phone.getGroupCallChainBlocks req = new TL_phone.getGroupCallChainBlocks();
            req.call = inputGroupCall;
            req.sub_chain_id = a;
            req.offset = Math.max(0, last_offset[a]);
            req.limit = 10;
            FileLog.d("[tde2e] requesting getGroupCallChainBlocks sub_chain_id=" + req.sub_chain_id + " offset=" + req.offset + " limit=10");
            final long time = System.currentTimeMillis();
            pollRequestId[a] = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (processUpdates(req.offset, time, res, err))
                    hadBlocks.set(true);
                if (received.incrementAndGet() == 2) {
                    polling = false;
                    if (hadBlocks.get()) {
                        forcePoll();
                    } else {
                        AndroidUtilities.cancelRunOnUIThread(pollRunnable);
                        AndroidUtilities.runOnUIThread(pollRunnable, getPollTimeout());
                    }
                }
            }));
        }
        if (call_id >= 0) {
            try {
                FileLog.d("[tde2e] state = " + call_get_verification_state(call_id));
            } catch (Exception e) {
                FileLog.e(e);
            }
            try {
                FileLog.d("[tde2e] call_describe("+call_id+"): " + call_describe(call_id));
                FileLog.d("[tde2e] call users:\n " + TextUtils.join("\n ", Arrays.stream(call_get_state(call_id).participants).map(p -> "["+p.user_id+"]: " + DialogObject.getName(p.user_id)).collect(Collectors.toSet())));
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        checkEmojiHash();
        checkParticipants();
    }

    public void forcePoll() {
        if (destroyed) {
            FileLog.d("[tde2e] conference.forcePoll but destroyed!");
            return;
        }
        if (!joined) {
            FileLog.d("[tde2e] conference.forcePoll but not joined!");
            return;
        }
        if (polling) return;
        AndroidUtilities.cancelRunOnUIThread(pollRunnable);
        AndroidUtilities.runOnUIThread(pollRunnable);
    }

    public void updateParticipants(ArrayList<TLRPC.GroupCallParticipant> participants, boolean forcePoll) {
        if (destroyed) {
            FileLog.d("[tde2e] conference.updateParticipants but destroyed!");
            return;
        }
        for (TLRPC.GroupCallParticipant p : participants) {
            joiningBlockchainParticipants.remove(DialogObject.getPeerDialogId(p.peer));
        }
        final Set<Long> leftParticipants = getShadyLeftParticipants(participants);
        if (!leftParticipants.isEmpty()) {
            try {
                final CallState state = call_get_state(call_id);
                final CallState newState = new CallState();
                newState.height = state.height + 1;
                final ArrayList<CallParticipant> newParticipants = new ArrayList<>();
                for (int i = 0; i < state.participants.length; ++i) {
                    if (leftParticipants.contains(state.participants[i].user_id))
                        continue;
                    final CallParticipant participant = new CallParticipant();
                    participant.user_id = state.participants[i].user_id;
                    participant.public_key_id = state.participants[i].public_key_id;
                    participant.permissions = state.participants[i].permissions;
                    newParticipants.add(participant);
                }
                newState.participants = newParticipants.toArray(new CallParticipant[0]);
                FileLog.d("[tde2e] call_create_change_state_block from " + this.state + " to " + newState);
                final byte[] new_block = call_create_change_state_block(call_id, newState);
                FileLog.d("[tde2e] call_create_change_state_block returns " + call_describe_block(new_block));
                this.state = newState;

                final TL_phone.deleteConferenceCallParticipants req = new TL_phone.deleteConferenceCallParticipants();
                req.only_left = true;
                req.call = inputGroupCall;
                req.block = new_block;
                req.ids.addAll(leftParticipants);

                final long time = System.currentTimeMillis();
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    processUpdates(null, time, res, err);
                }));
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (forcePoll) {
            forcePoll();
        }
    }

    public void kick(long userId) {
        if (destroyed) {
            FileLog.d("[tde2e] conference.kick but destroyed!");
            return;
        }
        if (call_id < 0) return;
        if (!getBlockchainParticipants().contains(userId)) return;

        final CallState state = call_get_state(call_id);
        final CallState newState = new CallState();
        newState.height = state.height + 1;
        final ArrayList<CallParticipant> newParticipants = new ArrayList<>();
        for (int i = 0; i < state.participants.length; ++i) {
            if (userId == state.participants[i].user_id)
                continue;
            final CallParticipant participant = new CallParticipant();
            participant.user_id = state.participants[i].user_id;
            participant.public_key_id = state.participants[i].public_key_id;
            participant.permissions = state.participants[i].permissions;
            newParticipants.add(participant);
        }
        newState.participants = newParticipants.toArray(new CallParticipant[0]);
        FileLog.d("[tde2e] kick: call_create_change_state_block from " + this.state + " to " + newState);
        final byte[] new_block = call_create_change_state_block(call_id, newState);
        FileLog.d("[tde2e] kick: call_create_change_state_block returns " + call_describe_block(new_block));
        this.state = newState;

        final TL_phone.deleteConferenceCallParticipants req = new TL_phone.deleteConferenceCallParticipants();
        req.kick = true;
        req.call = inputGroupCall;
        req.block = new_block;
        req.ids.add(userId);

        final long time = System.currentTimeMillis();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            processUpdates(null, time, res, err);
        }));
    }

    public HashSet<Long> getBlockchainParticipants() {
        final HashSet<Long> participants = new HashSet<>();
        if (destroyed) {
            FileLog.d("[tde2e] conference.getBlockchainParticipants but destroyed!");
            return participants;
        }
        if (call_id < 0)
            return participants;
        CallState state = null;
        try {
            state = call_get_state(call_id);
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (state != null) {
            for (int i = 0; i < state.participants.length; ++i) {
                participants.add(state.participants[i].user_id);
            }
        }
        return participants;
    }

    public HashSet<Long> getShadyJoiningParticipants(final ArrayList<TLRPC.GroupCallParticipant> participants) {
        final HashSet<Long> leftParticipants = new HashSet<>();
        if (destroyed) {
            FileLog.d("[tde2e] conference.getShadyJoiningParticipants but destroyed!");
            return leftParticipants;
        }
        if (call_id < 0)
            return leftParticipants;
        CallState state = null;
        try {
            state = call_get_state(call_id);
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (state != null) {
            for (int i = 0; i < state.participants.length; ++i) {
                final long user_id = state.participants[i].user_id;
                TLRPC.GroupCallParticipant p = null;
                for (int j = 0; j < (participants == null ? 0 : participants.size()); ++j) {
                    final long did = DialogObject.getPeerDialogId(participants.get(j).peer);
                    if (user_id == did) {
                        p = participants.get(j);
                        break;
                    }
                }
                if (p == null && user_id != my_user_id && joiningBlockchainParticipants.contains(user_id)) {
                    leftParticipants.add(user_id);
                }
            }
        }
        return leftParticipants;
    }

    public HashSet<Long> getShadyLeftParticipants(final ArrayList<TLRPC.GroupCallParticipant> participants) {
        final HashSet<Long> leftParticipants = new HashSet<>();
        if (destroyed) {
            FileLog.d("[tde2e] conference.getShadyLeftParticipants but destroyed!");
            return leftParticipants;
        }
        if (call_id < 0)
            return leftParticipants;
        CallState state = null;
        try {
            state = call_get_state(call_id);
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (state != null) {
            for (int i = 0; i < state.participants.length; ++i) {
                final long user_id = state.participants[i].user_id;
                TLRPC.GroupCallParticipant p = null;
                for (int j = 0; j < (participants == null ? 0 : participants.size()); ++j) {
                    final long did = DialogObject.getPeerDialogId(participants.get(j).peer);
                    if (user_id == did) {
                        p = participants.get(j);
                        break;
                    }
                }
                if (p == null && user_id != my_user_id && !joiningBlockchainParticipants.contains(user_id)) {
                    leftParticipants.add(user_id);
                }
            }
        }
        return leftParticipants;
    }

    public void reset() {
        AndroidUtilities.cancelRunOnUIThread(pollRunnable);
        if (call_id != -1) {
            for (int a = 0; a < 2; ++a) {
                if (pollRequestId[a] != 0) {
                    ConnectionsManager.getInstance(currentAccount).cancelRequest(pollRequestId[a], true);
                    pollRequestId[a] = 0;
                }
            }

            call_destroy(call_id);
            FileLog.d("[tde2e] call_destroy(" + call_id + ")");
            call_id = -1;
        }
        last_offset[0] = -1;
        last_offset[1] = -1;
        blocksQueue[0].clear();
        blocksQueue[1].clear();
        init();
    }

    public void joined() {
        joined = true;
    }

    public void destroy() {
        destroyed = true;
        reset();
    }

    public long getCallId() {
        return call_id;
    }


}
