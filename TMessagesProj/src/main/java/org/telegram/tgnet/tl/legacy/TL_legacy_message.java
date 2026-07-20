package org.telegram.tgnet.tl.legacy;

import android.text.TextUtils;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

public class TL_legacy_message {
    private TL_legacy_message() {

    }

    public static class TL_message_layer226 extends TLRPC.TL_message {
        public static final int constructor = 0x95ef6f2b;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_1);
            mentioned = hasFlag(flags, FLAG_4);
            media_unread = hasFlag(flags, FLAG_5);
            silent = hasFlag(flags, FLAG_13);
            post = hasFlag(flags, FLAG_14);
            from_scheduled = hasFlag(flags, FLAG_18);
            legacy = hasFlag(flags, FLAG_19);
            edit_hide = hasFlag(flags, FLAG_21);
            pinned = hasFlag(flags, FLAG_24);
            noforwards = hasFlag(flags, FLAG_26);
            invert_media = hasFlag(flags, FLAG_27);
            flags2 = stream.readInt32(exception);
            offline = hasFlag(flags2, FLAG_1);
            video_processing_pending = hasFlag(flags2, FLAG_4);
            paid_suggested_post_stars = hasFlag(flags2, FLAG_8);
            paid_suggested_post_ton = hasFlag(flags2, FLAG_9);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_8)) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_29)) {
                from_boosts_applied = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_12)) {
                from_rank = stream.readString(exception);
            }
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                via_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_0)) {
                via_business_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_19)) {
                guestchat_via_from = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_9)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (media != null) {
                    ttl = media.ttl_seconds;
                }
                if (media != null && !TextUtils.isEmpty(media.captionLegacy)) {
                    message = media.captionLegacy;
                }
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                views = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                forwards = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies = TLRPC.MessageReplies.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_15)) {
                edit_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_16)) {
                post_author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_17)) {
                grouped_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions = TLRPC.MessageReactions.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_22)) {
                restriction_reason = Vector.deserialize(stream, TLRPC.RestrictionReason::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_25)) {
                ttl_period = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_30)) {
                quick_reply_shortcut_id = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_2)) {
                effect = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_3)) {
                factcheck = TLRPC.TL_factCheck.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags2, FLAG_5)) {
                report_delivery_until_date = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_6)) {
                paid_message_stars = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_7)) {
                suggested_post = TLRPC.SuggestedPost.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags2, FLAG_10)) {
                schedule_repeat_period = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_11)) {
                summary_from_language = stream.readString(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, out);
            flags = setFlag(flags, FLAG_4, mentioned);
            flags = setFlag(flags, FLAG_5, media_unread);
            flags = setFlag(flags, FLAG_13, silent);
            flags = setFlag(flags, FLAG_14, post);
            flags = setFlag(flags, FLAG_18, from_scheduled);
            flags = setFlag(flags, FLAG_19, legacy);
            flags = setFlag(flags, FLAG_21, edit_hide);
            flags = setFlag(flags, FLAG_24, pinned);
            flags = setFlag(flags, FLAG_26, noforwards);
            flags = setFlag(flags, FLAG_27, invert_media);
            stream.writeInt32(flags);
            flags2 = setFlag(flags2, FLAG_1, offline);
            flags2 = setFlag(flags2, FLAG_4, video_processing_pending);
            flags2 = setFlag(flags2, FLAG_7, suggested_post != null);
            flags2 = setFlag(flags2, FLAG_8, paid_suggested_post_stars);
            flags2 = setFlag(flags2, FLAG_9, paid_suggested_post_ton);
            flags2 = setFlag(flags2, FLAG_12, from_rank != null);
            flags2 = setFlag(flags2, FLAG_19, guestchat_via_from != null);
            stream.writeInt32(flags2);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_8)) {
                from_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_29)) {
                stream.writeInt32(from_boosts_applied);
            }
            if (hasFlag(flags2, FLAG_12)) {
                stream.writeString(from_rank);
            }
            peer_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt64(via_bot_id);
            }
            if (hasFlag(flags2, FLAG_0)) {
                stream.writeInt64(via_business_bot_id);
            }
            if (hasFlag(flags2, FLAG_19)) {
                guestchat_via_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to.serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_9)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(views);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(forwards);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_15)) {
                stream.writeInt32(edit_date);
            }
            if (hasFlag(flags, FLAG_16)) {
                stream.writeString(post_author);
            }
            if (hasFlag(flags, FLAG_17)) {
                stream.writeInt64(grouped_id);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_22)) {
                Vector.serialize(stream, restriction_reason);
            }
            if (hasFlag(flags, FLAG_25)) {
                stream.writeInt32(ttl_period);
            }
            if (hasFlag(flags, FLAG_30)) {
                stream.writeInt32(quick_reply_shortcut_id);
            }
            if (hasFlag(flags2, FLAG_2)) {
                stream.writeInt64(effect);
            }
            if (hasFlag(flags2, FLAG_3)) {
                factcheck.serializeToStream(stream);
            }
            if (hasFlag(flags2, FLAG_5)) {
                stream.writeInt32(report_delivery_until_date);
            }
            if (hasFlag(flags2, FLAG_6)) {
                stream.writeInt64(paid_message_stars);
            }
            if (hasFlag(flags2, FLAG_7)) {
                suggested_post.serializeToStream(stream);
            }
            if (hasFlag(flags2, FLAG_10)) {
                stream.writeInt32(schedule_repeat_period);
            }
            if (hasFlag(flags2, FLAG_11)) {
                stream.writeString(summary_from_language);
            }
            writeAttachPath(stream);
        }
    }
    public static class TL_message_layer224 extends TLRPC.TL_message {
        public static final int constructor = 0x3AE56482;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_1);
            mentioned = hasFlag(flags, FLAG_4);
            media_unread = hasFlag(flags, FLAG_5);
            silent = hasFlag(flags, FLAG_13);
            post = hasFlag(flags, FLAG_14);
            from_scheduled = hasFlag(flags, FLAG_18);
            legacy = hasFlag(flags, FLAG_19);
            edit_hide = hasFlag(flags, FLAG_21);
            pinned = hasFlag(flags, FLAG_24);
            noforwards = hasFlag(flags, FLAG_26);
            invert_media = hasFlag(flags, FLAG_27);
            flags2 = stream.readInt32(exception);
            offline = hasFlag(flags2, FLAG_1);
            video_processing_pending = hasFlag(flags2, FLAG_4);
            paid_suggested_post_stars = hasFlag(flags2, FLAG_8);
            paid_suggested_post_ton = hasFlag(flags2, FLAG_9);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_8)) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_29)) {
                from_boosts_applied = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_12)) {
                from_rank = stream.readString(exception);
            }
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                via_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_0)) {
                via_business_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_9)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (media != null) {
                    ttl = media.ttl_seconds;
                }
                if (media != null && !TextUtils.isEmpty(media.captionLegacy)) {
                    message = media.captionLegacy;
                }
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                views = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                forwards = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies = TLRPC.MessageReplies.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_15)) {
                edit_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_16)) {
                post_author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_17)) {
                grouped_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions = TLRPC.MessageReactions.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_22)) {
                restriction_reason = Vector.deserialize(stream, TLRPC.RestrictionReason::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_25)) {
                ttl_period = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_30)) {
                quick_reply_shortcut_id = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_2)) {
                effect = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_3)) {
                factcheck = TLRPC.TL_factCheck.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags2, FLAG_5)) {
                report_delivery_until_date = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_6)) {
                paid_message_stars = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_7)) {
                suggested_post = TLRPC.SuggestedPost.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags2, FLAG_10)) {
                schedule_repeat_period = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_11)) {
                summary_from_language = stream.readString(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, out);
            flags = setFlag(flags, FLAG_4, mentioned);
            flags = setFlag(flags, FLAG_5, media_unread);
            flags = setFlag(flags, FLAG_13, silent);
            flags = setFlag(flags, FLAG_14, post);
            flags = setFlag(flags, FLAG_18, from_scheduled);
            flags = setFlag(flags, FLAG_19, legacy);
            flags = setFlag(flags, FLAG_21, edit_hide);
            flags = setFlag(flags, FLAG_24, pinned);
            flags = setFlag(flags, FLAG_26, noforwards);
            flags = setFlag(flags, FLAG_27, invert_media);
            stream.writeInt32(flags);
            flags2 = setFlag(flags2, FLAG_1, offline);
            flags2 = setFlag(flags2, FLAG_4, video_processing_pending);
            flags2 = setFlag(flags2, FLAG_7, suggested_post != null);
            flags2 = setFlag(flags2, FLAG_8, paid_suggested_post_stars);
            flags2 = setFlag(flags2, FLAG_9, paid_suggested_post_ton);
            flags2 = setFlag(flags2, FLAG_12, from_rank != null);
            stream.writeInt32(flags2);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_8)) {
                from_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_29)) {
                stream.writeInt32(from_boosts_applied);
            }
            if (hasFlag(flags2, FLAG_12)) {
                stream.writeString(from_rank);
            }
            peer_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt64(via_bot_id);
            }
            if (hasFlag(flags2, FLAG_0)) {
                stream.writeInt64(via_business_bot_id);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to.serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_9)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(views);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(forwards);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_15)) {
                stream.writeInt32(edit_date);
            }
            if (hasFlag(flags, FLAG_16)) {
                stream.writeString(post_author);
            }
            if (hasFlag(flags, FLAG_17)) {
                stream.writeInt64(grouped_id);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_22)) {
                Vector.serialize(stream, restriction_reason);
            }
            if (hasFlag(flags, FLAG_25)) {
                stream.writeInt32(ttl_period);
            }
            if (hasFlag(flags, FLAG_30)) {
                stream.writeInt32(quick_reply_shortcut_id);
            }
            if (hasFlag(flags2, FLAG_2)) {
                stream.writeInt64(effect);
            }
            if (hasFlag(flags2, FLAG_3)) {
                factcheck.serializeToStream(stream);
            }
            if (hasFlag(flags2, FLAG_5)) {
                stream.writeInt32(report_delivery_until_date);
            }
            if (hasFlag(flags2, FLAG_6)) {
                stream.writeInt64(paid_message_stars);
            }
            if (hasFlag(flags2, FLAG_7)) {
                suggested_post.serializeToStream(stream);
            }
            if (hasFlag(flags2, FLAG_10)) {
                stream.writeInt32(schedule_repeat_period);
            }
            if (hasFlag(flags2, FLAG_11)) {
                stream.writeString(summary_from_language);
            }
            writeAttachPath(stream);
        }
    }
    public static class TL_message_layer222 extends TLRPC.TL_message {
        public static final int constructor = 0x9cb490e9;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_1);
            mentioned = hasFlag(flags, FLAG_4);
            media_unread = hasFlag(flags, FLAG_5);
            silent = hasFlag(flags, FLAG_13);
            post = hasFlag(flags, FLAG_14);
            from_scheduled = hasFlag(flags, FLAG_18);
            legacy = hasFlag(flags, FLAG_19);
            edit_hide = hasFlag(flags, FLAG_21);
            pinned = hasFlag(flags, FLAG_24);
            noforwards = hasFlag(flags, FLAG_26);
            invert_media = hasFlag(flags, FLAG_27);
            flags2 = stream.readInt32(exception);
            offline = hasFlag(flags2, FLAG_1);
            video_processing_pending = hasFlag(flags2, FLAG_4);
            paid_suggested_post_stars = hasFlag(flags2, FLAG_8);
            paid_suggested_post_ton = hasFlag(flags2, FLAG_9);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_8)) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_29)) {
                from_boosts_applied = stream.readInt32(exception);
            }
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                via_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_0)) {
                via_business_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_9)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (media != null) {
                    ttl = media.ttl_seconds;
                }
                if (media != null && !TextUtils.isEmpty(media.captionLegacy)) {
                    message = media.captionLegacy;
                }
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                views = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                forwards = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies = TLRPC.MessageReplies.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_15)) {
                edit_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_16)) {
                post_author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_17)) {
                grouped_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions = TLRPC.MessageReactions.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_22)) {
                restriction_reason = Vector.deserialize(stream, TLRPC.RestrictionReason::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_25)) {
                ttl_period = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_30)) {
                quick_reply_shortcut_id = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_2)) {
                effect = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_3)) {
                factcheck = TLRPC.TL_factCheck.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags2, FLAG_5)) {
                report_delivery_until_date = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_6)) {
                paid_message_stars = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_7)) {
                suggested_post = TLRPC.SuggestedPost.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags2, FLAG_10)) {
                schedule_repeat_period = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_11)) {
                summary_from_language = stream.readString(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, out);
            flags = setFlag(flags, FLAG_4, mentioned);
            flags = setFlag(flags, FLAG_5, media_unread);
            flags = setFlag(flags, FLAG_13, silent);
            flags = setFlag(flags, FLAG_14, post);
            flags = setFlag(flags, FLAG_18, from_scheduled);
            flags = setFlag(flags, FLAG_19, legacy);
            flags = setFlag(flags, FLAG_21, edit_hide);
            flags = setFlag(flags, FLAG_24, pinned);
            flags = setFlag(flags, FLAG_26, noforwards);
            flags = setFlag(flags, FLAG_27, invert_media);
            stream.writeInt32(flags);
            flags2 = setFlag(flags2, FLAG_1, offline);
            flags2 = setFlag(flags2, FLAG_4, video_processing_pending);
            flags2 = setFlag(flags2, FLAG_7, suggested_post != null);
            flags2 = setFlag(flags2, FLAG_8, paid_suggested_post_stars);
            flags2 = setFlag(flags2, FLAG_9, paid_suggested_post_ton);
            stream.writeInt32(flags2);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_8)) {
                from_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_29)) {
                stream.writeInt32(from_boosts_applied);
            }
            peer_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt64(via_bot_id);
            }
            if (hasFlag(flags2, FLAG_0)) {
                stream.writeInt64(via_business_bot_id);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to.serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_9)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(views);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(forwards);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_15)) {
                stream.writeInt32(edit_date);
            }
            if (hasFlag(flags, FLAG_16)) {
                stream.writeString(post_author);
            }
            if (hasFlag(flags, FLAG_17)) {
                stream.writeInt64(grouped_id);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_22)) {
                Vector.serialize(stream, restriction_reason);
            }
            if (hasFlag(flags, FLAG_25)) {
                stream.writeInt32(ttl_period);
            }
            if (hasFlag(flags, FLAG_30)) {
                stream.writeInt32(quick_reply_shortcut_id);
            }
            if (hasFlag(flags2, FLAG_2)) {
                stream.writeInt64(effect);
            }
            if (hasFlag(flags2, FLAG_3)) {
                factcheck.serializeToStream(stream);
            }
            if (hasFlag(flags2, FLAG_5)) {
                stream.writeInt32(report_delivery_until_date);
            }
            if (hasFlag(flags2, FLAG_6)) {
                stream.writeInt64(paid_message_stars);
            }
            if (hasFlag(flags2, FLAG_7)) {
                suggested_post.serializeToStream(stream);
            }
            if (hasFlag(flags2, FLAG_10)) {
                stream.writeInt32(schedule_repeat_period);
            }
            if (hasFlag(flags2, FLAG_11)) {
                stream.writeString(summary_from_language);
            }
            writeAttachPath(stream);
        }
    }
    public static class TL_message_layer220 extends TLRPC.TL_message {
        public static final int constructor = 0xb92f76cf;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_1);
            mentioned = hasFlag(flags, FLAG_4);
            media_unread = hasFlag(flags, FLAG_5);
            silent = hasFlag(flags, FLAG_13);
            post = hasFlag(flags, FLAG_14);
            from_scheduled = hasFlag(flags, FLAG_18);
            legacy = hasFlag(flags, FLAG_19);
            edit_hide = hasFlag(flags, FLAG_21);
            pinned = hasFlag(flags, FLAG_24);
            noforwards = hasFlag(flags, FLAG_26);
            invert_media = hasFlag(flags, FLAG_27);
            flags2 = stream.readInt32(exception);
            offline = hasFlag(flags2, FLAG_1);
            video_processing_pending = hasFlag(flags2, FLAG_4);
            paid_suggested_post_stars = hasFlag(flags2, FLAG_8);
            paid_suggested_post_ton = hasFlag(flags2, FLAG_9);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_8)) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_29)) {
                from_boosts_applied = stream.readInt32(exception);
            }
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                via_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_0)) {
                via_business_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_9)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (media != null) {
                    ttl = media.ttl_seconds;
                }
                if (media != null && !TextUtils.isEmpty(media.captionLegacy)) {
                    message = media.captionLegacy;
                }
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                views = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                forwards = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies = TLRPC.MessageReplies.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_15)) {
                edit_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_16)) {
                post_author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_17)) {
                grouped_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions = TLRPC.MessageReactions.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_22)) {
                restriction_reason = Vector.deserialize(stream, TLRPC.RestrictionReason::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_25)) {
                ttl_period = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_30)) {
                quick_reply_shortcut_id = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_2)) {
                effect = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_3)) {
                factcheck = TLRPC.TL_factCheck.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags2, FLAG_5)) {
                report_delivery_until_date = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_6)) {
                paid_message_stars = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_7)) {
                suggested_post = TLRPC.SuggestedPost.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags2, FLAG_10)) {
                schedule_repeat_period = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, out);
            flags = setFlag(flags, FLAG_4, mentioned);
            flags = setFlag(flags, FLAG_5, media_unread);
            flags = setFlag(flags, FLAG_13, silent);
            flags = setFlag(flags, FLAG_14, post);
            flags = setFlag(flags, FLAG_18, from_scheduled);
            flags = setFlag(flags, FLAG_19, legacy);
            flags = setFlag(flags, FLAG_21, edit_hide);
            flags = setFlag(flags, FLAG_24, pinned);
            flags = setFlag(flags, FLAG_26, noforwards);
            flags = setFlag(flags, FLAG_27, invert_media);
            stream.writeInt32(flags);
            flags2 = setFlag(flags2, FLAG_1, offline);
            flags2 = setFlag(flags2, FLAG_4, video_processing_pending);
            flags2 = setFlag(flags2, FLAG_7, suggested_post != null);
            flags2 = setFlag(flags2, FLAG_8, paid_suggested_post_stars);
            flags2 = setFlag(flags2, FLAG_9, paid_suggested_post_ton);
            stream.writeInt32(flags2);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_8)) {
                from_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_29)) {
                stream.writeInt32(from_boosts_applied);
            }
            peer_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt64(via_bot_id);
            }
            if (hasFlag(flags2, FLAG_0)) {
                stream.writeInt64(via_business_bot_id);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to.serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_9)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(views);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(forwards);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_15)) {
                stream.writeInt32(edit_date);
            }
            if (hasFlag(flags, FLAG_16)) {
                stream.writeString(post_author);
            }
            if (hasFlag(flags, FLAG_17)) {
                stream.writeInt64(grouped_id);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_22)) {
                Vector.serialize(stream, restriction_reason);
            }
            if (hasFlag(flags, FLAG_25)) {
                stream.writeInt32(ttl_period);
            }
            if (hasFlag(flags, FLAG_30)) {
                stream.writeInt32(quick_reply_shortcut_id);
            }
            if (hasFlag(flags2, FLAG_2)) {
                stream.writeInt64(effect);
            }
            if (hasFlag(flags2, FLAG_3)) {
                factcheck.serializeToStream(stream);
            }
            if (hasFlag(flags2, FLAG_5)) {
                stream.writeInt32(report_delivery_until_date);
            }
            if (hasFlag(flags2, FLAG_6)) {
                stream.writeInt64(paid_message_stars);
            }
            if (hasFlag(flags2, FLAG_7)) {
                suggested_post.serializeToStream(stream);
            }
            if (hasFlag(flags2, FLAG_10)) {
                stream.writeInt32(schedule_repeat_period);
            }
            writeAttachPath(stream);
        }
    }
    public static class TL_message_layer216 extends TLRPC.TL_message {
        public static final int constructor = 0x9815cec8;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_1);
            mentioned = hasFlag(flags, FLAG_4);
            media_unread = hasFlag(flags, FLAG_5);
            silent = hasFlag(flags, FLAG_13);
            post = hasFlag(flags, FLAG_14);
            from_scheduled = hasFlag(flags, FLAG_18);
            legacy = hasFlag(flags, FLAG_19);
            edit_hide = hasFlag(flags, FLAG_21);
            pinned = hasFlag(flags, FLAG_24);
            noforwards = hasFlag(flags, FLAG_26);
            invert_media = hasFlag(flags, FLAG_27);
            flags2 = stream.readInt32(exception);
            offline = hasFlag(flags2, FLAG_1);
            video_processing_pending = hasFlag(flags2, FLAG_4);
            paid_suggested_post_stars = hasFlag(flags2, FLAG_8);
            paid_suggested_post_ton = hasFlag(flags2, FLAG_9);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_8)) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_29)) {
                from_boosts_applied = stream.readInt32(exception);
            }
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                via_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_0)) {
                via_business_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_9)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (media != null) {
                    ttl = media.ttl_seconds;
                }
                if (media != null && !TextUtils.isEmpty(media.captionLegacy)) {
                    message = media.captionLegacy;
                }
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                views = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                forwards = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies = TLRPC.MessageReplies.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_15)) {
                edit_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_16)) {
                post_author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_17)) {
                grouped_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions = TLRPC.MessageReactions.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_22)) {
                restriction_reason = Vector.deserialize(stream, TLRPC.RestrictionReason::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_25)) {
                ttl_period = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_30)) {
                quick_reply_shortcut_id = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_2)) {
                effect = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_3)) {
                factcheck = TLRPC.TL_factCheck.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags2, FLAG_5)) {
                report_delivery_until_date = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_6)) {
                paid_message_stars = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_7)) {
                suggested_post = TLRPC.SuggestedPost.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, out);
            flags = setFlag(flags, FLAG_4, mentioned);
            flags = setFlag(flags, FLAG_5, media_unread);
            flags = setFlag(flags, FLAG_13, silent);
            flags = setFlag(flags, FLAG_14, post);
            flags = setFlag(flags, FLAG_18, from_scheduled);
            flags = setFlag(flags, FLAG_19, legacy);
            flags = setFlag(flags, FLAG_21, edit_hide);
            flags = setFlag(flags, FLAG_24, pinned);
            flags = setFlag(flags, FLAG_26, noforwards);
            flags = setFlag(flags, FLAG_27, invert_media);
            stream.writeInt32(flags);
            flags2 = setFlag(flags2, FLAG_1, offline);
            flags2 = setFlag(flags2, FLAG_4, video_processing_pending);
            flags2 = setFlag(flags2, FLAG_7, suggested_post != null);
            flags2 = setFlag(flags2, FLAG_8, paid_suggested_post_stars);
            flags2 = setFlag(flags2, FLAG_9, paid_suggested_post_ton);
            stream.writeInt32(flags2);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_8)) {
                from_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_29)) {
                stream.writeInt32(from_boosts_applied);
            }
            peer_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt64(via_bot_id);
            }
            if (hasFlag(flags2, FLAG_0)) {
                stream.writeInt64(via_business_bot_id);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to.serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_9)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(views);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(forwards);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_15)) {
                stream.writeInt32(edit_date);
            }
            if (hasFlag(flags, FLAG_16)) {
                stream.writeString(post_author);
            }
            if (hasFlag(flags, FLAG_17)) {
                stream.writeInt64(grouped_id);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_22)) {
                Vector.serialize(stream, restriction_reason);
            }
            if (hasFlag(flags, FLAG_25)) {
                stream.writeInt32(ttl_period);
            }
            if (hasFlag(flags, FLAG_30)) {
                stream.writeInt32(quick_reply_shortcut_id);
            }
            if (hasFlag(flags2, FLAG_2)) {
                stream.writeInt64(effect);
            }
            if (hasFlag(flags2, FLAG_3)) {
                factcheck.serializeToStream(stream);
            }
            if (hasFlag(flags2, FLAG_5)) {
                stream.writeInt32(report_delivery_until_date);
            }
            if (hasFlag(flags2, FLAG_6)) {
                stream.writeInt64(paid_message_stars);
            }
            if ((hasFlag(flags2, FLAG_7))) {
                suggested_post.serializeToStream(stream);
            }
            writeAttachPath(stream);
        }
    }
    public static class TL_message_layer205 extends TLRPC.TL_message {
        public static final int constructor = 0xeabcdd4d;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_1);
            mentioned = hasFlag(flags, FLAG_4);
            media_unread = hasFlag(flags, FLAG_5);
            silent = hasFlag(flags, FLAG_13);
            post = hasFlag(flags, FLAG_14);
            from_scheduled = hasFlag(flags, FLAG_18);
            legacy = hasFlag(flags, FLAG_19);
            edit_hide = hasFlag(flags, FLAG_21);
            pinned = hasFlag(flags, FLAG_24);
            noforwards = hasFlag(flags, FLAG_26);
            invert_media = hasFlag(flags, FLAG_27);
            flags2 = stream.readInt32(exception);
            offline = hasFlag(flags2, FLAG_1);
            video_processing_pending = hasFlag(flags2, FLAG_4);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_8)) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_29)) {
                from_boosts_applied = stream.readInt32(exception);
            }
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                via_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_0)) {
                via_business_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_9)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (media != null) {
                    ttl = media.ttl_seconds;
                }
                if (media != null && !TextUtils.isEmpty(media.captionLegacy)) {
                    message = media.captionLegacy;
                }
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                views = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                forwards = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies = TLRPC.MessageReplies.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_15)) {
                edit_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_16)) {
                post_author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_17)) {
                grouped_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions = TLRPC.MessageReactions.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_22)) {
                restriction_reason = Vector.deserialize(stream, TLRPC.RestrictionReason::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_25)) {
                ttl_period = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_30)) {
                quick_reply_shortcut_id = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_2)) {
                effect = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_3)) {
                factcheck = TLRPC.TL_factCheck.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags2, FLAG_5)) {
                report_delivery_until_date = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_6)) {
                paid_message_stars = stream.readInt64(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, out);
            flags = setFlag(flags, FLAG_4, mentioned);
            flags = setFlag(flags, FLAG_5, media_unread);
            flags = setFlag(flags, FLAG_13, silent);
            flags = setFlag(flags, FLAG_14, post);
            flags = setFlag(flags, FLAG_18, from_scheduled);
            flags = setFlag(flags, FLAG_19, legacy);
            flags = setFlag(flags, FLAG_21, edit_hide);
            flags = setFlag(flags, FLAG_24, pinned);
            flags = setFlag(flags, FLAG_26, noforwards);
            flags = setFlag(flags, FLAG_27, invert_media);
            stream.writeInt32(flags);
            flags2 = setFlag(flags2, FLAG_1, offline);
            flags2 = setFlag(flags2, FLAG_4, video_processing_pending);
            stream.writeInt32(flags2);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_8)) {
                from_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_29)) {
                stream.writeInt32(from_boosts_applied);
            }
            peer_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt64(via_bot_id);
            }
            if (hasFlag(flags2, FLAG_0)) {
                stream.writeInt64(via_business_bot_id);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to.serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_9)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(views);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(forwards);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_15)) {
                stream.writeInt32(edit_date);
            }
            if (hasFlag(flags, FLAG_16)) {
                stream.writeString(post_author);
            }
            if (hasFlag(flags, FLAG_17)) {
                stream.writeInt64(grouped_id);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_22)) {
                Vector.serialize(stream, restriction_reason);
            }
            if (hasFlag(flags, FLAG_25)) {
                stream.writeInt32(ttl_period);
            }
            if (hasFlag(flags, FLAG_30)) {
                stream.writeInt32(quick_reply_shortcut_id);
            }
            if (hasFlag(flags2, FLAG_2)) {
                stream.writeInt64(effect);
            }
            if (hasFlag(flags2, FLAG_3)) {
                factcheck.serializeToStream(stream);
            }
            if (hasFlag(flags2, FLAG_5)) {
                stream.writeInt32(report_delivery_until_date);
            }
            if (hasFlag(flags2, FLAG_6)) {
                stream.writeInt64(paid_message_stars);
            }
            writeAttachPath(stream);
        }
    }
    public static class TL_message_layer199 extends TLRPC.TL_message {
        public static final int constructor = 0x96fdbbe9;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_1);
            mentioned = hasFlag(flags, FLAG_4);
            media_unread = hasFlag(flags, FLAG_5);
            silent = hasFlag(flags, FLAG_13);
            post = hasFlag(flags, FLAG_14);
            from_scheduled = hasFlag(flags, FLAG_18);
            legacy = hasFlag(flags, FLAG_19);
            edit_hide = hasFlag(flags, FLAG_21);
            pinned = hasFlag(flags, FLAG_24);
            noforwards = hasFlag(flags, FLAG_26);
            invert_media = hasFlag(flags, FLAG_27);
            flags2 = stream.readInt32(exception);
            offline = hasFlag(flags2, FLAG_1);
            video_processing_pending = hasFlag(flags2, FLAG_4);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_8)) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_29)) {
                from_boosts_applied = stream.readInt32(exception);
            }
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                via_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_0)) {
                via_business_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_9)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (media != null) {
                    ttl = media.ttl_seconds;
                }
                if (media != null && !TextUtils.isEmpty(media.captionLegacy)) {
                    message = media.captionLegacy;
                }
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                views = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                forwards = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies = TLRPC.MessageReplies.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_15)) {
                edit_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_16)) {
                post_author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_17)) {
                grouped_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions = TLRPC.MessageReactions.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_22)) {
                restriction_reason = Vector.deserialize(stream, TLRPC.RestrictionReason::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_25)) {
                ttl_period = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_30)) {
                quick_reply_shortcut_id = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_2)) {
                effect = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_3)) {
                factcheck = TLRPC.TL_factCheck.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags2, FLAG_5)) {
                report_delivery_until_date = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, out);
            flags = setFlag(flags, FLAG_4, mentioned);
            flags = setFlag(flags, FLAG_5, media_unread);
            flags = setFlag(flags, FLAG_13, silent);
            flags = setFlag(flags, FLAG_14, post);
            flags = setFlag(flags, FLAG_18, from_scheduled);
            flags = setFlag(flags, FLAG_19, legacy);
            flags = setFlag(flags, FLAG_21, edit_hide);
            flags = setFlag(flags, FLAG_24, pinned);
            flags = setFlag(flags, FLAG_26, noforwards);
            flags = setFlag(flags, FLAG_27, invert_media);
            stream.writeInt32(flags);
            flags2 = setFlag(flags2, FLAG_1, offline);
            flags2 = setFlag(flags2, FLAG_4, video_processing_pending);
            stream.writeInt32(flags2);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_8)) {
                from_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_29)) {
                stream.writeInt32(from_boosts_applied);
            }
            peer_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt64(via_bot_id);
            }
            if (hasFlag(flags2, FLAG_0)) {
                stream.writeInt64(via_business_bot_id);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to.serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_9)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(views);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(forwards);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_15)) {
                stream.writeInt32(edit_date);
            }
            if (hasFlag(flags, FLAG_16)) {
                stream.writeString(post_author);
            }
            if (hasFlag(flags, FLAG_17)) {
                stream.writeInt64(grouped_id);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_22)) {
                Vector.serialize(stream, restriction_reason);
            }
            if (hasFlag(flags, FLAG_25)) {
                stream.writeInt32(ttl_period);
            }
            if (hasFlag(flags, FLAG_30)) {
                stream.writeInt32(quick_reply_shortcut_id);
            }
            if (hasFlag(flags2, FLAG_2)) {
                stream.writeInt64(effect);
            }
            if (hasFlag(flags2, FLAG_3)) {
                factcheck.serializeToStream(stream);
            }
            if (hasFlag(flags2, FLAG_5)) {
                stream.writeInt32(report_delivery_until_date);
            }
            writeAttachPath(stream);
        }
    }
    public static class TL_message_layer195 extends TLRPC.TL_message {
        public static final int constructor = 0x94345242;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_1);
            mentioned = hasFlag(flags, FLAG_4);
            media_unread = hasFlag(flags, FLAG_5);
            silent = hasFlag(flags, FLAG_13);
            post = hasFlag(flags, FLAG_14);
            from_scheduled = hasFlag(flags, FLAG_18);
            legacy = hasFlag(flags, FLAG_19);
            edit_hide = hasFlag(flags, FLAG_21);
            pinned = hasFlag(flags, FLAG_24);
            noforwards = hasFlag(flags, FLAG_26);
            invert_media = hasFlag(flags, FLAG_27);
            flags2 = stream.readInt32(exception);
            offline = hasFlag(flags2, FLAG_1);
            video_processing_pending = hasFlag(flags2, FLAG_4);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_8)) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_29)) {
                from_boosts_applied = stream.readInt32(exception);
            }
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                via_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_0)) {
                via_business_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_9)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (media != null) {
                    ttl = media.ttl_seconds;
                }
                if (media != null && !TextUtils.isEmpty(media.captionLegacy)) {
                    message = media.captionLegacy;
                }
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                views = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                forwards = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies = TLRPC.MessageReplies.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_15)) {
                edit_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_16)) {
                post_author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_17)) {
                grouped_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions = TLRPC.MessageReactions.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_22)) {
                restriction_reason = Vector.deserialize(stream, TLRPC.RestrictionReason::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_25)) {
                ttl_period = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_30)) {
                quick_reply_shortcut_id = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_2)) {
                effect = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_3)) {
                factcheck = TLRPC.TL_factCheck.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, out);
            flags = setFlag(flags, FLAG_4, mentioned);
            flags = setFlag(flags, FLAG_5, media_unread);
            flags = setFlag(flags, FLAG_13, silent);
            flags = setFlag(flags, FLAG_14, post);
            flags = setFlag(flags, FLAG_18, from_scheduled);
            flags = setFlag(flags, FLAG_19, legacy);
            flags = setFlag(flags, FLAG_21, edit_hide);
            flags = setFlag(flags, FLAG_24, pinned);
            flags = setFlag(flags, FLAG_26, noforwards);
            flags = setFlag(flags, FLAG_27, invert_media);
            stream.writeInt32(flags);
            flags2 = setFlag(flags2, FLAG_1, offline);
            flags2 = setFlag(flags2, FLAG_4, video_processing_pending);
            stream.writeInt32(flags2);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_8)) {
                from_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_29)) {
                stream.writeInt32(from_boosts_applied);
            }
            peer_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt64(via_bot_id);
            }
            if (hasFlag(flags2, FLAG_0)) {
                stream.writeInt64(via_business_bot_id);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to.serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_9)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(views);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(forwards);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_15)) {
                stream.writeInt32(edit_date);
            }
            if (hasFlag(flags, FLAG_16)) {
                stream.writeString(post_author);
            }
            if (hasFlag(flags, FLAG_17)) {
                stream.writeInt64(grouped_id);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_22)) {
                Vector.serialize(stream, restriction_reason);
            }
            if (hasFlag(flags, FLAG_25)) {
                stream.writeInt32(ttl_period);
            }
            if (hasFlag(flags, FLAG_30)) {
                stream.writeInt32(quick_reply_shortcut_id);
            }
            if (hasFlag(flags2, FLAG_2)) {
                stream.writeInt64(effect);
            }
            if (hasFlag(flags2, FLAG_3)) {
                factcheck.serializeToStream(stream);
            }
            writeAttachPath(stream);
        }
    }
    public static class TL_message_layer180 extends TLRPC.TL_message {
        public static final int constructor = 0xbde09c2e;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_1);
            mentioned = hasFlag(flags, FLAG_4);
            media_unread = hasFlag(flags, FLAG_5);
            silent = hasFlag(flags, FLAG_13);
            post = hasFlag(flags, FLAG_14);
            from_scheduled = hasFlag(flags, FLAG_18);
            legacy = hasFlag(flags, FLAG_19);
            edit_hide = hasFlag(flags, FLAG_21);
            pinned = hasFlag(flags, FLAG_24);
            noforwards = hasFlag(flags, FLAG_26);
            invert_media = hasFlag(flags, FLAG_27);
            flags2 = stream.readInt32(exception);
            offline = hasFlag(flags2, FLAG_1);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_8)) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_29)) {
                from_boosts_applied = stream.readInt32(exception);
            }
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                via_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_0)) {
                via_business_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_9)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (media != null) {
                    ttl = media.ttl_seconds;
                }
                if (media != null && !TextUtils.isEmpty(media.captionLegacy)) {
                    message = media.captionLegacy;
                }
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                views = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                forwards = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies = TLRPC.MessageReplies.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_15)) {
                edit_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_16)) {
                post_author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_17)) {
                grouped_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions = TLRPC.MessageReactions.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_22)) {
                restriction_reason = Vector.deserialize(stream, TLRPC.RestrictionReason::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_25)) {
                ttl_period = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_30)) {
                quick_reply_shortcut_id = stream.readInt32(exception);
            }
            if (hasFlag(flags2, FLAG_2)) {
                effect = stream.readInt64(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, out);
            flags = setFlag(flags, FLAG_4, mentioned);
            flags = setFlag(flags, FLAG_5, media_unread);
            flags = setFlag(flags, FLAG_13, silent);
            flags = setFlag(flags, FLAG_14, post);
            flags = setFlag(flags, FLAG_18, from_scheduled);
            flags = setFlag(flags, FLAG_19, legacy);
            flags = setFlag(flags, FLAG_21, edit_hide);
            flags = setFlag(flags, FLAG_24, pinned);
            flags = setFlag(flags, FLAG_26, noforwards);
            flags = setFlag(flags, FLAG_27, invert_media);
            stream.writeInt32(flags);
            flags2 = setFlag(flags2, FLAG_1, offline);
            stream.writeInt32(flags2);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_8)) {
                from_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_29)) {
                stream.writeInt32(from_boosts_applied);
            }
            peer_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt64(via_bot_id);
            }
            if (hasFlag(flags2, FLAG_0)) {
                stream.writeInt64(via_business_bot_id);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to.serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_9)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(views);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(forwards);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_15)) {
                stream.writeInt32(edit_date);
            }
            if (hasFlag(flags, FLAG_16)) {
                stream.writeString(post_author);
            }
            if (hasFlag(flags, FLAG_17)) {
                stream.writeInt64(grouped_id);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_22)) {
                Vector.serialize(stream, restriction_reason);
            }
            if (hasFlag(flags, FLAG_25)) {
                stream.writeInt32(ttl_period);
            }
            if (hasFlag(flags, FLAG_30)) {
                stream.writeInt32(quick_reply_shortcut_id);
            }
            if (hasFlag(flags2, FLAG_2)) {
                stream.writeInt64(effect);
            }
            writeAttachPath(stream);
        }
    }
    public static class TL_message_layer179 extends TLRPC.TL_message {
        public static final int constructor = 0x2357bf25;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_1);
            mentioned = hasFlag(flags, FLAG_4);
            media_unread = hasFlag(flags, FLAG_5);
            silent = hasFlag(flags, FLAG_13);
            post = hasFlag(flags, FLAG_14);
            from_scheduled = hasFlag(flags, FLAG_18);
            legacy = hasFlag(flags, FLAG_19);
            edit_hide = hasFlag(flags, FLAG_21);
            pinned = hasFlag(flags, FLAG_24);
            noforwards = hasFlag(flags, FLAG_26);
            invert_media = hasFlag(flags, FLAG_27);
            flags2 = stream.readInt32(exception);
            offline = hasFlag(flags2, FLAG_1);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_8)) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_29)) {
                from_boosts_applied = stream.readInt32(exception);
            }
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                via_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags2, FLAG_0)) {
                via_business_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_9)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (media != null) {
                    ttl = media.ttl_seconds;
                }
                if (media != null && !TextUtils.isEmpty(media.captionLegacy)) {
                    message = media.captionLegacy;
                }
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                views = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                forwards = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies = TLRPC.MessageReplies.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_15)) {
                edit_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_16)) {
                post_author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_17)) {
                grouped_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions = TLRPC.MessageReactions.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_22)) {
                restriction_reason = Vector.deserialize(stream, TLRPC.RestrictionReason::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_25)) {
                ttl_period = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_30)) {
                quick_reply_shortcut_id = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, out);
            flags = setFlag(flags, FLAG_4, mentioned);
            flags = setFlag(flags, FLAG_5, media_unread);
            flags = setFlag(flags, FLAG_13, silent);
            flags = setFlag(flags, FLAG_14, post);
            flags = setFlag(flags, FLAG_18, from_scheduled);
            flags = setFlag(flags, FLAG_19, legacy);
            flags = setFlag(flags, FLAG_21, edit_hide);
            flags = setFlag(flags, FLAG_24, pinned);
            flags = setFlag(flags, FLAG_26, noforwards);
            flags = setFlag(flags, FLAG_27, invert_media);
            stream.writeInt32(flags);
            flags2 = setFlag(flags2, FLAG_1, offline);
            stream.writeInt32(flags2);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_8)) {
                from_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_29)) {
                stream.writeInt32(from_boosts_applied);
            }
            peer_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt64(via_bot_id);
            }
            if (hasFlag(flags2, FLAG_0)) {
                stream.writeInt64(via_business_bot_id);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to.serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_9)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(views);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(forwards);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_15)) {
                stream.writeInt32(edit_date);
            }
            if (hasFlag(flags, FLAG_16)) {
                stream.writeString(post_author);
            }
            if (hasFlag(flags, FLAG_17)) {
                stream.writeInt64(grouped_id);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_22)) {
                Vector.serialize(stream, restriction_reason);
            }
            if (hasFlag(flags, FLAG_25)) {
                stream.writeInt32(ttl_period);
            }
            if (hasFlag(flags, FLAG_30)) {
                stream.writeInt32(quick_reply_shortcut_id);
            }
            writeAttachPath(stream);
        }
    }
    public static class TL_message_layer176 extends TLRPC.TL_message {
        public static final int constructor = 0xa66c7efc;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_1);
            mentioned = hasFlag(flags, FLAG_4);
            media_unread = hasFlag(flags, FLAG_5);
            silent = hasFlag(flags, FLAG_13);
            post = hasFlag(flags, FLAG_14);
            from_scheduled = hasFlag(flags, FLAG_18);
            legacy = hasFlag(flags, FLAG_19);
            edit_hide = hasFlag(flags, FLAG_21);
            pinned = hasFlag(flags, FLAG_24);
            noforwards = hasFlag(flags, FLAG_26);
            invert_media = hasFlag(flags, FLAG_27);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_8)) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_29)) {
                from_boosts_applied = stream.readInt32(exception);
            }
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                via_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_9)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (media != null) {
                    ttl = media.ttl_seconds;
                }
                if (media != null && !TextUtils.isEmpty(media.captionLegacy)) {
                    message = media.captionLegacy;
                }
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                views = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                forwards = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies = TLRPC.MessageReplies.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_15)) {
                edit_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_16)) {
                post_author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_17)) {
                grouped_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions = TLRPC.MessageReactions.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_22)) {
                restriction_reason = Vector.deserialize(stream, TLRPC.RestrictionReason::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_25)) {
                ttl_period = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_30)) {
                quick_reply_shortcut_id = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, out);
            flags = setFlag(flags, FLAG_4, mentioned);
            flags = setFlag(flags, FLAG_5, media_unread);
            flags = setFlag(flags, FLAG_13, silent);
            flags = setFlag(flags, FLAG_14, post);
            flags = setFlag(flags, FLAG_18, from_scheduled);
            flags = setFlag(flags, FLAG_19, legacy);
            flags = setFlag(flags, FLAG_21, edit_hide);
            flags = setFlag(flags, FLAG_24, pinned);
            flags = setFlag(flags, FLAG_26, noforwards);
            flags = setFlag(flags, FLAG_27, invert_media);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_8)) {
                from_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_29)) {
                stream.writeInt32(from_boosts_applied);
            }
            peer_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt64(via_bot_id);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to.serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_9)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(views);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(forwards);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_15)) {
                stream.writeInt32(edit_date);
            }
            if (hasFlag(flags, FLAG_16)) {
                stream.writeString(post_author);
            }
            if (hasFlag(flags, FLAG_17)) {
                stream.writeInt64(grouped_id);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_22)) {
                Vector.serialize(stream, restriction_reason);
            }
            if (hasFlag(flags, FLAG_25)) {
                stream.writeInt32(ttl_period);
            }
            if (hasFlag(flags, FLAG_30)) {
                stream.writeInt32(quick_reply_shortcut_id);
            }
            writeAttachPath(stream);
        }
    }
    public static class TL_message_layer175 extends TLRPC.TL_message {
        public static final int constructor = 0x1e4c8a69;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_1);
            mentioned = hasFlag(flags, FLAG_4);
            media_unread = hasFlag(flags, FLAG_5);
            silent = hasFlag(flags, FLAG_13);
            post = hasFlag(flags, FLAG_14);
            from_scheduled = hasFlag(flags, FLAG_18);
            legacy = hasFlag(flags, FLAG_19);
            edit_hide = hasFlag(flags, FLAG_21);
            pinned = hasFlag(flags, FLAG_24);
            noforwards = hasFlag(flags, FLAG_26);
            invert_media = hasFlag(flags, FLAG_27);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_8)) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_29)) {
                from_boosts_applied = stream.readInt32(exception);
            }
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                via_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_9)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (media != null) {
                    ttl = media.ttl_seconds;
                }
                if (media != null && !TextUtils.isEmpty(media.captionLegacy)) {
                    message = media.captionLegacy;
                }
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                views = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                forwards = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies = TLRPC.MessageReplies.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_15)) {
                edit_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_16)) {
                post_author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_17)) {
                grouped_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions = TLRPC.MessageReactions.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_22)) {
                restriction_reason = Vector.deserialize(stream, TLRPC.RestrictionReason::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_25)) {
                ttl_period = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, out);
            flags = setFlag(flags, FLAG_4, mentioned);
            flags = setFlag(flags, FLAG_5, media_unread);
            flags = setFlag(flags, FLAG_13, silent);
            flags = setFlag(flags, FLAG_14, post);
            flags = setFlag(flags, FLAG_18, from_scheduled);
            flags = setFlag(flags, FLAG_19, legacy);
            flags = setFlag(flags, FLAG_21, edit_hide);
            flags = setFlag(flags, FLAG_24, pinned);
            flags = setFlag(flags, FLAG_26, noforwards);
            flags = setFlag(flags, FLAG_27, invert_media);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_8)) {
                from_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_29)) {
                stream.writeInt32(from_boosts_applied);
            }
            peer_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt64(via_bot_id);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to.serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_9)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(views);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(forwards);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_15)) {
                stream.writeInt32(edit_date);
            }
            if (hasFlag(flags, FLAG_16)) {
                stream.writeString(post_author);
            }
            if (hasFlag(flags, FLAG_17)) {
                stream.writeInt64(grouped_id);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_22)) {
                Vector.serialize(stream, restriction_reason);
            }
            if (hasFlag(flags, FLAG_25)) {
                stream.writeInt32(ttl_period);
            }
            writeAttachPath(stream);
        }
    }
    public static class TL_message_layer173 extends TLRPC.TL_message {
        public static final int constructor = 0x76bec211;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_1);
            mentioned = hasFlag(flags, FLAG_4);
            media_unread = hasFlag(flags, FLAG_5);
            silent = hasFlag(flags, FLAG_13);
            post = hasFlag(flags, FLAG_14);
            from_scheduled = hasFlag(flags, FLAG_18);
            legacy = hasFlag(flags, FLAG_19);
            edit_hide = hasFlag(flags, FLAG_21);
            pinned = hasFlag(flags, FLAG_24);
            noforwards = hasFlag(flags, FLAG_26);
            invert_media = hasFlag(flags, FLAG_27);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_8)) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                via_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_9)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (media != null) {
                    ttl = media.ttl_seconds;
                }
                if (media != null && !TextUtils.isEmpty(media.captionLegacy)) {
                    message = media.captionLegacy;
                }
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                views = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                forwards = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies = TLRPC.MessageReplies.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_15)) {
                edit_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_16)) {
                post_author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_17)) {
                grouped_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions = TLRPC.MessageReactions.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_22)) {
                restriction_reason = Vector.deserialize(stream, TLRPC.RestrictionReason::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_25)) {
                ttl_period = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, out);
            flags = setFlag(flags, FLAG_4, mentioned);
            flags = setFlag(flags, FLAG_5, media_unread);
            flags = setFlag(flags, FLAG_13, silent);
            flags = setFlag(flags, FLAG_14, post);
            flags = setFlag(flags, FLAG_18, from_scheduled);
            flags = setFlag(flags, FLAG_19, legacy);
            flags = setFlag(flags, FLAG_21, edit_hide);
            flags = setFlag(flags, FLAG_24, pinned);
            flags = setFlag(flags, FLAG_26, noforwards);
            flags = setFlag(flags, FLAG_27, invert_media);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_8)) {
                from_id.serializeToStream(stream);
            }
            peer_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_28)) {
                saved_peer_id.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_2)) {
                fwd_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt64(via_bot_id);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to.serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_9)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(views);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(forwards);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_15)) {
                stream.writeInt32(edit_date);
            }
            if (hasFlag(flags, FLAG_16)) {
                stream.writeString(post_author);
            }
            if (hasFlag(flags, FLAG_17)) {
                stream.writeInt64(grouped_id);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_22)) {
                Vector.serialize(stream, restriction_reason);
            }
            if (hasFlag(flags, FLAG_25)) {
                stream.writeInt32(ttl_period);
            }
            writeAttachPath(stream);
        }
    }
    public static class TL_message_layer169 extends TLRPC.TL_message {
        public static final int constructor = 0x38116ee0;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_1);
            mentioned = hasFlag(flags, FLAG_4);
            media_unread = hasFlag(flags, FLAG_5);
            silent = hasFlag(flags, FLAG_13);
            post = hasFlag(flags, FLAG_14);
            from_scheduled = hasFlag(flags, FLAG_18);
            legacy = hasFlag(flags, FLAG_19);
            edit_hide = hasFlag(flags, FLAG_21);
            pinned = hasFlag(flags, FLAG_24);
            noforwards = hasFlag(flags, FLAG_26);
            invert_media = hasFlag(flags, FLAG_27);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_8)) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_2)) {
                fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                via_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_9)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (media != null) {
                    ttl = media.ttl_seconds;
                }
                if (media != null && !TextUtils.isEmpty(media.captionLegacy)) {
                    message = media.captionLegacy;
                }
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                views = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                forwards = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies = TLRPC.MessageReplies.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_15)) {
                edit_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_16)) {
                post_author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_17)) {
                grouped_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions = TLRPC.MessageReactions.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_22)) {
                restriction_reason = Vector.deserialize(stream, TLRPC.RestrictionReason::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_25)) {
                ttl_period = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, out);
            flags = setFlag(flags, FLAG_4, mentioned);
            flags = setFlag(flags, FLAG_5, media_unread);
            flags = setFlag(flags, FLAG_13, silent);
            flags = setFlag(flags, FLAG_14, post);
            flags = setFlag(flags, FLAG_18, from_scheduled);
            flags = setFlag(flags, FLAG_19, legacy);
            flags = setFlag(flags, FLAG_21, edit_hide);
            flags = setFlag(flags, FLAG_24, pinned);
            flags = setFlag(flags, FLAG_26, noforwards);
            flags = setFlag(flags, FLAG_27, invert_media);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_8)) {
                from_id.serializeToStream(stream);
            }
            peer_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_2)) {
                fwd_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt64(via_bot_id);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to.serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_9)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(views);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(forwards);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_15)) {
                stream.writeInt32(edit_date);
            }
            if (hasFlag(flags, FLAG_16)) {
                stream.writeString(post_author);
            }
            if (hasFlag(flags, FLAG_17)) {
                stream.writeInt64(grouped_id);
            }
            if (hasFlag(flags, FLAG_20)) {
                reactions.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_22)) {
                Vector.serialize(stream, restriction_reason);
            }
            if (hasFlag(flags, FLAG_25)) {
                stream.writeInt32(ttl_period);
            }
            writeAttachPath(stream);
        }
    }
    public static class TL_message_layer135 extends TLRPC.TL_message {
        public static final int constructor = 0x85d6cbe2;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_1);
            mentioned = hasFlag(flags, FLAG_4);
            media_unread = hasFlag(flags, FLAG_5);
            silent = hasFlag(flags, FLAG_13);
            post = hasFlag(flags, FLAG_14);
            from_scheduled = hasFlag(flags, FLAG_18);
            legacy = hasFlag(flags, FLAG_19);
            edit_hide = hasFlag(flags, FLAG_21);
            pinned = hasFlag(flags, FLAG_24);
            noforwards = hasFlag(flags, FLAG_26);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_8)) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_2)) {
                fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                via_bot_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_9)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (media != null) {
                    ttl = media.ttl_seconds;
                }
                if (media != null && !TextUtils.isEmpty(media.captionLegacy)) {
                    message = media.captionLegacy;
                }
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                views = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                forwards = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies = TLRPC.MessageReplies.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_15)) {
                edit_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_16)) {
                post_author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_17)) {
                grouped_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_22)) {
                restriction_reason = Vector.deserialize(stream, TLRPC.RestrictionReason::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_25)) {
                ttl_period = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, out);
            flags = setFlag(flags, FLAG_4, mentioned);
            flags = setFlag(flags, FLAG_5, media_unread);
            flags = setFlag(flags, FLAG_13, silent);
            flags = setFlag(flags, FLAG_14, post);
            flags = setFlag(flags, FLAG_18, from_scheduled);
            flags = setFlag(flags, FLAG_19, legacy);
            flags = setFlag(flags, FLAG_21, edit_hide);
            flags = setFlag(flags, FLAG_24, pinned);
            flags = setFlag(flags, FLAG_26, noforwards);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_8)) {
                from_id.serializeToStream(stream);
            }
            peer_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_2)) {
                fwd_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt64(via_bot_id);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to.serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_9)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(views);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(forwards);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_15)) {
                stream.writeInt32(edit_date);
            }
            if (hasFlag(flags, FLAG_16)) {
                stream.writeString(post_author);
            }
            if (hasFlag(flags, FLAG_17)) {
                stream.writeInt64(grouped_id);
            }
            if (hasFlag(flags, FLAG_22)) {
                Vector.serialize(stream, restriction_reason);
            }
            if (hasFlag(flags, FLAG_25)) {
                stream.writeInt32(ttl_period);
            }
            writeAttachPath(stream);
        }
    }
    public static class TL_message_layer131 extends TLRPC.TL_message {
        public static final int constructor = 0xbce383d2;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_1);
            mentioned = hasFlag(flags, FLAG_4);
            media_unread = hasFlag(flags, FLAG_5);
            silent = hasFlag(flags, FLAG_13);
            post = hasFlag(flags, FLAG_14);
            from_scheduled = hasFlag(flags, FLAG_18);
            legacy = hasFlag(flags, FLAG_19);
            edit_hide = hasFlag(flags, FLAG_21);
            pinned = hasFlag(flags, FLAG_24);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_8)) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_2)) {
                fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                via_bot_id = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_9)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (media != null) {
                    ttl = media.ttl_seconds; //custom
                }
                if (media != null && !TextUtils.isEmpty(media.captionLegacy)) {
                    message = media.captionLegacy;
                }
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                views = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                forwards = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies = TLRPC.MessageReplies.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_15)) {
                edit_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_16)) {
                post_author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_17)) {
                grouped_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_22)) {
                restriction_reason = Vector.deserialize(stream, TLRPC.RestrictionReason::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_25)) {
                ttl_period = stream.readInt32(exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, out);
            flags = setFlag(flags, FLAG_4, mentioned);
            flags = setFlag(flags, FLAG_5, media_unread);
            flags = setFlag(flags, FLAG_13, silent);
            flags = setFlag(flags, FLAG_14, post);
            flags = setFlag(flags, FLAG_18, from_scheduled);
            flags = setFlag(flags, FLAG_19, legacy);
            flags = setFlag(flags, FLAG_21, edit_hide);
            flags = setFlag(flags, FLAG_24, pinned);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_8)) {
                from_id.serializeToStream(stream);
            }
            peer_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_2)) {
                fwd_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt32((int) via_bot_id);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to.serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_9)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(views);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(forwards);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_15)) {
                stream.writeInt32(edit_date);
            }
            if (hasFlag(flags, FLAG_16)) {
                stream.writeString(post_author);
            }
            if (hasFlag(flags, FLAG_17)) {
                stream.writeInt64(grouped_id);
            }
            if (hasFlag(flags, FLAG_22)) {
                Vector.serialize(stream, restriction_reason);
            }
            if (hasFlag(flags, FLAG_25)) {
                stream.writeInt32(ttl_period);
            }
            writeAttachPath(stream); //custom
        }
    }
    public static class TL_message_layer123 extends TLRPC.TL_message {
        public static final int constructor = 0x58ae39c9;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_1);
            mentioned = hasFlag(flags, FLAG_4);
            media_unread = hasFlag(flags, FLAG_5);
            silent = hasFlag(flags, FLAG_13);
            post = hasFlag(flags, FLAG_14);
            from_scheduled = hasFlag(flags, FLAG_18);
            legacy = hasFlag(flags, FLAG_19);
            edit_hide = hasFlag(flags, FLAG_21);
            pinned = hasFlag(flags, FLAG_24);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_8)) {
                from_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_2)) {
                fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                via_bot_id = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to = TLRPC.MessageReplyHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_9)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (media != null) {
                    ttl = media.ttl_seconds; //custom
                }
                if (media != null && !TextUtils.isEmpty(media.captionLegacy)) {
                    message = media.captionLegacy;
                }
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                views = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                forwards = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies = TLRPC.MessageReplies.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_15)) {
                edit_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_16)) {
                post_author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_17)) {
                grouped_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_22)) {
                restriction_reason = Vector.deserialize(stream, TLRPC.RestrictionReason::TLdeserialize, exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, out);
            flags = setFlag(flags, FLAG_4, mentioned);
            flags = setFlag(flags, FLAG_5, media_unread);
            flags = setFlag(flags, FLAG_13, silent);
            flags = setFlag(flags, FLAG_14, post);
            flags = setFlag(flags, FLAG_18, from_scheduled);
            flags = setFlag(flags, FLAG_19, legacy);
            flags = setFlag(flags, FLAG_21, edit_hide);
            flags = setFlag(flags, FLAG_24, pinned);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_8)) {
                from_id.serializeToStream(stream);
            }
            peer_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_2)) {
                fwd_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt32((int) via_bot_id);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to.serializeToStream(stream);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_9)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(views);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(forwards);
            }
            if (hasFlag(flags, FLAG_23)) {
                replies.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_15)) {
                stream.writeInt32(edit_date);
            }
            if (hasFlag(flags, FLAG_16)) {
                stream.writeString(post_author);
            }
            if (hasFlag(flags, FLAG_17)) {
                stream.writeInt64(grouped_id);
            }
            if (hasFlag(flags, FLAG_22)) {
                Vector.serialize(stream, restriction_reason);
            }
            writeAttachPath(stream);
        }
    }
    public static class TL_message_layer118 extends TLRPC.TL_message {
        public static final int constructor = 0xf52e6b7f;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            out = hasFlag(flags, FLAG_1);
            mentioned = hasFlag(flags, FLAG_4);
            media_unread = hasFlag(flags, FLAG_5);
            silent = hasFlag(flags, FLAG_13);
            post = hasFlag(flags, FLAG_14);
            from_scheduled = hasFlag(flags, FLAG_18);
            legacy = hasFlag(flags, FLAG_19);
            edit_hide = hasFlag(flags, FLAG_21);
            id = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_8)) {
                from_id = new TLRPC.TL_peerUser();
                from_id.user_id = stream.readInt32(exception);
            }
            peer_id = TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_2)) {
                fwd_from = TLRPC.MessageFwdHeader.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_11)) {
                via_bot_id = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_3)) {
                reply_to = new TLRPC.TL_messageReplyHeader();
                reply_to.flags |= 16;
                reply_to.reply_to_msg_id = stream.readInt32(exception);
            }
            date = stream.readInt32(exception);
            message = stream.readString(exception);
            if (hasFlag(flags, FLAG_9)) {
                media = TLRPC.MessageMedia.TLdeserialize(stream, stream.readInt32(exception), exception);
                if (media != null) {
                    ttl = media.ttl_seconds; //custom
                }
                if (media != null && !TextUtils.isEmpty(media.captionLegacy)) {
                    message = media.captionLegacy;
                }
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup = TLRPC.ReplyMarkup.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (hasFlag(flags, FLAG_7)) {
                entities = Vector.deserialize(stream, TLRPC.MessageEntity::TLdeserialize, exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                views = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_10)) {
                forwards = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_15)) {
                edit_date = stream.readInt32(exception);
            }
            if (hasFlag(flags, FLAG_16)) {
                post_author = stream.readString(exception);
            }
            if (hasFlag(flags, FLAG_17)) {
                grouped_id = stream.readInt64(exception);
            }
            if (hasFlag(flags, FLAG_22)) {
                restriction_reason = Vector.deserialize(stream, TLRPC.RestrictionReason::TLdeserialize, exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_1, out);
            flags = setFlag(flags, FLAG_4, mentioned);
            flags = setFlag(flags, FLAG_5, media_unread);
            flags = setFlag(flags, FLAG_13, silent);
            flags = setFlag(flags, FLAG_14, post);
            flags = setFlag(flags, FLAG_18, from_scheduled);
            flags = setFlag(flags, FLAG_19, legacy);
            flags = setFlag(flags, FLAG_21, edit_hide);
            stream.writeInt32(flags);
            stream.writeInt32(id);
            if (hasFlag(flags, FLAG_8)) {
                stream.writeInt32((int) from_id.user_id);
            }
            peer_id.serializeToStream(stream);
            if (hasFlag(flags, FLAG_2)) {
                fwd_from.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_11)) {
                stream.writeInt32((int) via_bot_id);
            }
            if (hasFlag(flags, FLAG_3)) {
                stream.writeInt32(reply_to.reply_to_msg_id);
            }
            stream.writeInt32(date);
            stream.writeString(message);
            if (hasFlag(flags, FLAG_9)) {
                media.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_6)) {
                reply_markup.serializeToStream(stream);
            }
            if (hasFlag(flags, FLAG_7)) {
                Vector.serialize(stream, entities);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(views);
            }
            if (hasFlag(flags, FLAG_10)) {
                stream.writeInt32(forwards);
            }
            if (hasFlag(flags, FLAG_15)) {
                stream.writeInt32(edit_date);
            }
            if (hasFlag(flags, FLAG_16)) {
                stream.writeString(post_author);
            }
            if (hasFlag(flags, FLAG_17)) {
                stream.writeInt64(grouped_id);
            }
            if (hasFlag(flags, FLAG_22)) {
                Vector.serialize(stream, restriction_reason);
            }
            writeAttachPath(stream);
        }
    }
}
