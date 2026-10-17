package org.telegram.features;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.OfflineDemo;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.tgnet.tl.TL_phone;
import org.telegram.ui.Stars.StarsPrivacyMockApi;

/** Supplies data to the real Telegram screens; never creates or saves an authenticated user. */
final class OfflineTelegramData implements OfflineDemo.DataSource {
    static final long CHANNEL = 7770001;
    private static final long DUROV_CHANNEL = 7770002;
    private static final long TELEGRAM_CHANNEL = 7770003;
    private final TLRPC.TL_user user = new TLRPC.TL_user();
    private final TLRPC.TL_channel channel = new TLRPC.TL_channel();
    private final TLRPC.TL_channelFull full = new TLRPC.TL_channelFull();
    private final SharedPreferences receipts;
    private final TLRPC.TL_channel durov;
    private final TLRPC.TL_channel telegram;
    private final int sampleDate;

    OfflineTelegramData(Context context) {
        receipts = context.getSharedPreferences("native_demo_receipts", 0);
        int now = (int) (System.currentTimeMillis() / 1000);
        sampleDate = receipts.getInt("sample_date", now);
        if (!receipts.contains("sample_date")) receipts.edit().putInt("sample_date", sampleDate).commit();
        durov = sampleChannel(context, DUROV_CHANNEL, "Pavel Durov", "durov");
        telegram = sampleChannel(context, TELEGRAM_CHANNEL, "Telegram", "telegram");
        user.id = 8880000;
        user.first_name = "Features Lab";
        user.last_name = "";
        user.phone = "";
        user.self = true;
        user.status = new TLRPC.TL_userStatusRecently();
        user.photo = new TLRPC.TL_userProfilePhotoEmpty();
        channel.id = CHANNEL;
        channel.title = "Design Notes";
        channel.broadcast = true;
        channel.creator = true;
        channel.access_hash = 1;
        channel.flags |= 8192;
        channel.photo = new TLRPC.TL_chatPhotoEmpty();
        channel.date = 1792227600;
        full.id = CHANNEL;
        full.about = "Offline demo · sample posts and Star reactions. No real payments.";
        full.chat_photo = new TLRPC.TL_photoEmpty();
        full.notify_settings = new TLRPC.TL_peerNotifySettings();
        full.exported_invite = new TLRPC.TL_chatInviteExported();
        full.exported_invite.link = "";
        full.participants_count = 128;
        full.read_inbox_max_id = 101;
        full.read_outbox_max_id = 101;
        full.paid_media_allowed = true;
        full.paid_reactions_available = receipts.getBoolean("paid_reactions_enabled", true);
        full.available_reactions = new TLRPC.TL_chatReactionsNone();
        full.flags |= 1 << 30;
        full.reactions_limit = 11;
        full.flags2 |= 8192;
    }

    void seed() {
        MessagesController controller = MessagesController.getInstance(0);
        controller.starsLocked = false;
        controller.loadAppConfig(true);
        org.telegram.ui.Stars.StarsController.getInstance(0).getBalance();
        controller.putUser(user, true);
        controller.putChat(channel, true);
        controller.putChat(durov, true);
        controller.putChat(telegram, true);
        controller.putChatFull(full);
        controller.getStoriesController().putStories(-CHANNEL, stories());
        controller.processLoadedDialogs((TLRPC.messages_Dialogs) respond(0, new TLRPC.TL_messages_getDialogs()), null, null, 0, 0, 20, 0, true, false, false);
        UserConfig.getInstance(0).syncContacts = false;
        UserConfig.getInstance(0).showCallsTab = false;
    }

    @Override public TLRPC.User self() { return user; }

    private TLRPC.TL_message post() {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = 101;
        message.date = (int) (System.currentTimeMillis() / 1000) - 3600;
        message.message = "Small details make a difference.\n\nA quieter way to support the channels you love.";
        message.peer_id = new TLRPC.TL_peerChannel();
        message.peer_id.channel_id = CHANNEL;
        message.from_id = message.peer_id;
        message.post = true;
        message.media = new TLRPC.TL_messageMediaEmpty();
        message.reactions = new TLRPC.TL_messageReactions();
        message.flags |= (1 << 8) | (1 << 9) | (1 << 20);
        message.reactions.flags |= 1 << 4;
        TLRPC.TL_reactionCount stars = new TLRPC.TL_reactionCount();
        stars.reaction = new TLRPC.TL_reactionPaid();
        stars.count = 125 + (int) receipts.getLong("post_total", 0);
        stars.chosen = true;
        message.reactions.results.add(stars);
        for (int i = 0; i < 3; i++) {
            TLRPC.TL_messageReactor reactor = new TLRPC.TL_messageReactor();
            reactor.peer_id = new TLRPC.TL_peerUser();
            reactor.peer_id.user_id = 8880001 + i;
            reactor.count = 50 - 10 * i;
            message.reactions.top_reactors.add(reactor);
        }
        return message;
    }

    private void peers(java.util.ArrayList<TLRPC.User> users, java.util.ArrayList<TLRPC.Chat> chats) {
        users.add(user);
        chats.add(channel);
        chats.add(durov);
        chats.add(telegram);
        for (int i = 0; i < 3; i++) {
            TLRPC.TL_user donor = new TLRPC.TL_user();
            donor.id = 8880001 + i;
            donor.first_name = new String[]{"Alex", "Sam", "Robin"}[i];
            users.add(donor);
        }
    }

    private TL_stories.PeerStories stories() {
        TL_stories.TL_peerStories result = new TL_stories.TL_peerStories();
        result.peer = new TLRPC.TL_peerChannel();
        result.peer.channel_id = CHANNEL;
        TL_stories.TL_storyItem item = new TL_stories.TL_storyItem();
        item.id = 201;
        item.dialogId = -CHANNEL;
        item.date = (int) (System.currentTimeMillis() / 1000) - 60;
        item.expire_date = item.date + 86400;
        item.caption = "Offline live demo · sample Stars";
        item.isPublic = true;
        TLRPC.TL_messageMediaVideoStream media = new TLRPC.TL_messageMediaVideoStream();
        media.call = new TLRPC.TL_inputGroupCall();
        media.call.id = 777201;
        media.call.access_hash = 1;
        media.rtmp_stream = true;
        item.media = media;
        result.stories.add(item);
        return result;
    }

    @Override public TLObject respond(int account, TLObject request) {
        if (account != 0) return null;
        if (request instanceof TLRPC.TL_help_getAppConfig) {
            TLRPC.TL_help_appConfig result = new TLRPC.TL_help_appConfig();
            TLRPC.TL_jsonObject config = new TLRPC.TL_jsonObject();
            TLRPC.TL_jsonObjectValue option = new TLRPC.TL_jsonObjectValue();
            option.key = "stars_purchase_blocked";
            TLRPC.TL_jsonBool value = new TLRPC.TL_jsonBool();
            value.value = false;
            option.value = value;
            config.value.add(option);
            result.hash = 1;
            result.config = config;
            return result;
        }

        if (request instanceof TL_stories.TL_stories_getPeerStories) {
            TL_stories.TL_stories_peerStories result = new TL_stories.TL_stories_peerStories();
            peers(result.users, result.chats);
            result.stories = stories();
            return result;
        }
        if (request instanceof TL_stories.TL_stories_getAllStories) {
            TL_stories.TL_stories_allStories result = new TL_stories.TL_stories_allStories();
            peers(result.users, result.chats);
            result.state = "offline";
            result.count = 1;
            result.stealth_mode = new TL_stories.TL_storiesStealthMode();
            result.peer_stories.add(stories());
            return result;
        }
        if (request instanceof TL_phone.getGroupCall) {
            TL_phone.groupCall result = new TL_phone.groupCall();
            peers(result.users, result.chats);
            TLRPC.TL_groupCall call = new TLRPC.TL_groupCall();
            call.id = 777201;
            call.access_hash = 1;
            call.title = "Design Notes · Offline demo";
            call.rtmp_stream = true;
            call.messages_enabled = true;
            call.participants_count = 128;
            result.call = call;
            return result;
        }
        if (request instanceof TL_phone.getGroupCallStars) {
            TL_phone.groupCallStars result = new TL_phone.groupCallStars();
            peers(result.users, result.chats);
            result.total_stars = 125 + receipts.getLong("live_total", 0);
            return result;
        }

        if (request instanceof TL_stars.TL_payments_getStarsTopupOptions) {
            Vector<TL_stars.TL_starsTopupOption> result = new Vector<>(TL_stars.TL_starsTopupOption::TLdeserialize);
            for (int i = 0; i < 3; i++) {
                TL_stars.TL_starsTopupOption option = new TL_stars.TL_starsTopupOption();
                option.stars = new long[]{50, 100, 250}[i];
                option.amount = new long[]{99, 199, 499}[i];
                option.currency = "EUR";
                result.objects.add(option);
            }
            return result;
        }
        if (request instanceof TL_stars.TL_payments_getStarsStatus || request instanceof TL_stars.TL_payments_getStarsTransactions || request instanceof TL_stars.TL_getStarsSubscriptions) {
            TL_stars.TL_payments_starsStatus result = new TL_stars.TL_payments_starsStatus();
            boolean ton = request instanceof TL_stars.TL_payments_getStarsStatus && ((TL_stars.TL_payments_getStarsStatus) request).ton
                || request instanceof TL_stars.TL_payments_getStarsTransactions && ((TL_stars.TL_payments_getStarsTransactions) request).ton;
            result.balance = TL_stars.StarsAmount.ofStars(ton ? 0 : 965);
            peers(result.users, result.chats);
            if (!ton) {
                try {
                    JSONArray saved = new JSONArray(receipts.getString("receipts", "[]"));
                    for (int i = 0; i < saved.length(); i++) {
                        JSONObject row = saved.getJSONObject(i);
                        TL_stars.StarsTransaction receipt = row.optBoolean("private", true)
                            ? new StarsPrivacyMockApi.RedactedTransaction(row.getString("id"), row.getInt("date"), TL_stars.StarsAmount.ofStars(row.getLong("amount")))
                            : transaction(row.getString("id"), row.getLong("amount"), row.getInt("date"));
                        receipt.amount.nanos = row.optInt("nanos");
                        if (!(receipt instanceof StarsPrivacyMockApi.RedactedTransaction) && !row.optBoolean("post", true)) {
                            receipt.reaction = false;
                            receipt.msg_id = 0;
                        }
                        result.history.add(0, receipt);
                        result.balance.amount += row.getLong("amount");
                    }
                } catch (org.json.JSONException error) { throw new IllegalStateException(error); }
                // Illustrative transactions, never an assertion of payments to these channels.
                result.history.add(transaction("FL-20261017-0001", -25, sampleDate, DUROV_CHANNEL, 101));
                result.history.add(transaction("FL-20261017-0002", -10, sampleDate, TELEGRAM_CHANNEL, 202));
                if (request instanceof TL_stars.TL_getStarsSubscriptions) result.history.clear();
                if (request instanceof TL_stars.TL_payments_getStarsTransactions) {
                    TL_stars.TL_payments_getStarsTransactions filter = (TL_stars.TL_payments_getStarsTransactions) request;
                    result.history.removeIf(t -> filter.inbound && t.amount.amount < 0 || filter.outbound && t.amount.amount > 0);
                }
            }
            return result;
        }
        if (request instanceof TLRPC.TL_messages_getDialogs) {
            TLRPC.TL_messages_dialogs result = new TLRPC.TL_messages_dialogs();
            peers(result.users, result.chats);
            result.messages.add(post());
            TLRPC.TL_dialog dialog = new TLRPC.TL_dialog();
            dialog.id = -CHANNEL;
            dialog.flags = 1;
            dialog.pts = 1;
            dialog.peer = new TLRPC.TL_peerChannel();
            dialog.peer.channel_id = CHANNEL;
            dialog.top_message = 101;
            dialog.read_inbox_max_id = 101;
            dialog.read_outbox_max_id = 101;
            dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
            result.dialogs.add(dialog);
            return result;
        }
        if (request instanceof TLRPC.TL_messages_getHistory || request instanceof TLRPC.TL_channels_getMessages || request instanceof TLRPC.TL_messages_getMessages) {
            TLRPC.TL_messages_channelMessages result = new TLRPC.TL_messages_channelMessages();
            peers(result.users, result.chats);
            result.messages.add(post());
            result.count = 1;
            result.pts = 1;
            return result;
        }
        if (request instanceof TLRPC.TL_messages_setChatAvailableReactions) {
            TLRPC.TL_messages_setChatAvailableReactions change = (TLRPC.TL_messages_setChatAvailableReactions) request;
            full.available_reactions = change.available_reactions;
            full.reactions_limit = change.reactions_limit;
            if ((change.flags & 2) != 0) {
                full.paid_reactions_available = change.paid_enabled;
                receipts.edit().putBoolean("paid_reactions_enabled", change.paid_enabled).commit();
            }
            return new TLRPC.TL_updates();
        }
        if (request instanceof TLRPC.TL_channels_getFullChannel) {
            TLRPC.TL_messages_chatFull result = new TLRPC.TL_messages_chatFull();
            peers(result.users, result.chats);
            result.full_chat = full;
            return result;
        }
        return null;
    }

    private TL_stars.StarsTransaction transaction(String id, long amount, int date) {
        return transaction(id, amount, date, CHANNEL, 101);
    }

    private TL_stars.StarsTransaction transaction(String id, long amount, int date, long channelId, int postId) {
        TL_stars.TL_starsTransaction transaction = new TL_stars.TL_starsTransaction();
        transaction.id = id;
        transaction.date = date;
        transaction.amount = TL_stars.StarsAmount.ofStars(amount);
        transaction.peer = new TL_stars.TL_starsTransactionPeer();
        transaction.peer.peer = new TLRPC.TL_peerChannel();
        transaction.peer.peer.channel_id = channelId;
        transaction.reaction = true;
        transaction.flags |= 256;
        transaction.msg_id = postId;
        return StarsPrivacyMockApi.project(0, transaction);
    }

    private static TLRPC.TL_channel sampleChannel(Context context, long id, String name, String username) {
        TLRPC.TL_channel chat = new TLRPC.TL_channel();
        chat.id = id;
        chat.title = name;
        chat.username = username;
        chat.broadcast = true;
        chat.flags |= 64;
        chat.photo = new TLRPC.TL_chatPhoto();
        // Reuse Telegram's native avatar loading path with a bundled, offline placeholder.
        try (java.io.InputStream input = context.getAssets().open("demo/" + username + ".jpg")) {
            chat.photo.strippedBitmap = new android.graphics.drawable.BitmapDrawable(context.getResources(),
                android.graphics.BitmapFactory.decodeStream(input));
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Missing offline channel avatar", error);
        }
        return chat;
    }

    @Override public void recordTransaction(int account, TL_stars.StarsTransaction transaction, boolean live) {
        try {
            JSONArray saved = new JSONArray(receipts.getString("receipts", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                if (saved.getJSONObject(i).getString("id").equals(transaction.id)) return;
            }
            transaction = StarsPrivacyMockApi.project(account, transaction);
            JSONObject row = new JSONObject();
            row.put("id", transaction.id);
            row.put("date", transaction.date);
            row.put("amount", transaction.amount.amount);
            row.put("nanos", transaction.amount.nanos);
            row.put("private", transaction instanceof StarsPrivacyMockApi.RedactedTransaction);
            if (!(transaction instanceof StarsPrivacyMockApi.RedactedTransaction)) row.put("post", transaction.reaction);
            saved.put(row);
            // Model public server totals separately, without linking a private receipt
            // to a post or stream in the stored transaction rows.
            String total = live ? "live_total" : "post_total";
            receipts.edit().putString("receipts", saved.toString())
                .putLong(total, receipts.getLong(total, 0) - transaction.amount.amount).commit();
            long balance = 965;
            for (int i = 0; i < saved.length(); i++) balance += saved.getJSONObject(i).getLong("amount");
            org.telegram.ui.Stars.StarsController.getInstance(account).updateBalance(TL_stars.StarsAmount.ofStars(balance));
        } catch (org.json.JSONException error) { throw new IllegalStateException(error); }
    }
}
