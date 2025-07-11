package me.telegraphy.android;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.drinkless.td.libcore.telegram.TdApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TdApiMessageConverter {

    public static TLRPC.User convertUser(TdApi.User user) {
        if (user == null) {
            return null;
        }
        TLRPC.TL_user tlUser = new TLRPC.TL_user();
        tlUser.id = user.id;
        tlUser.first_name = user.firstName;
        tlUser.last_name = user.lastName;
        tlUser.username = user.username;
        tlUser.phone = user.phoneNumber;
        tlUser.photo = convertUserProfilePhoto(user.profilePhoto);
        tlUser.status = convertUserStatus(user.status);
        tlUser.bot = user.type instanceof TdApi.UserTypeBot;
        tlUser.verified = user.isVerified;
        tlUser.premium = user.isPremium;
        tlUser.access_hash = user.accessHash; // TDLib User object doesn't usually expose access_hash for others.
                                         // This might be an issue if old code relies on it.
                                         // For self, it might be available or not needed with TDLib.
        // TODO: Map other fields like restrictionReason, isScam, isSupport, etc.
        // tlUser.restriction_reason = user.restrictionReason;
        // tlUser.scam = user.isScam;
        // tlUser.support = user.isSupport;
        return tlUser;
    }

    public static TLRPC.Chat convertChat(TdApi.Chat chat) {
        if (chat == null) {
            return null;
        }

        TLRPC.Chat tlChat;
        TdApi.ChatType type = chat.type;

        if (type instanceof TdApi.ChatTypePrivate) {
            // This case should ideally be handled by creating a TLRPC.User
            // and the dialog representing a private chat with that user.
            // However, if a TLRPC.Chat object is strictly needed for a private chat context:
            TLRPC.TL_chatPrivate tlPrivateChat = new TLRPC.TL_chatPrivate();
            tlPrivateChat.id = ((TdApi.ChatTypePrivate) type).userId;
            // title, photo etc. would come from the TdApi.User object associated with userId
            tlChat = tlPrivateChat;
             // Note: TdApi.Chat for private chats usually only contains the other user's ID.
            // The actual user details are fetched separately.
        } else if (type instanceof TdApi.ChatTypeBasicGroup) {
            TLRPC.TL_chat tlBasicGroup = new TLRPC.TL_chat();
            tlBasicGroup.id = ((TdApi.ChatTypeBasicGroup) type).basicGroupId;
            tlBasicGroup.title = chat.title;
            tlBasicGroup.photo = convertChatPhotoInfo(chat.photo);
            tlBasicGroup.participants_count = chat.memberCount; // Approximate
            // TODO: Map other basic group fields (date, version, migrated_to)
            // tlBasicGroup.date = chat. // TdApi.Chat doesn't directly provide 'date'
            // tlBasicGroup.version = chat. // TdApi.Chat doesn't directly provide 'version'
            // tlBasicGroup.migrated_to = // Needs to be fetched from full chat info if applicable
            tlChat = tlBasicGroup;
        } else if (type instanceof TdApi.ChatTypeSupergroup) {
            TdApi.ChatTypeSupergroup supergroupType = (TdApi.ChatTypeSupergroup) type;
            if (supergroupType.isChannel) {
                TLRPC.TL_channel tlChannel = new TLRPC.TL_channel();
                tlChannel.id = supergroupType.supergroupId;
                tlChannel.title = chat.title;
                tlChannel.photo = convertChatPhotoInfo(chat.photo);
                tlChannel.username = chat.username; // Available if public
                tlChannel.verified = chat.isVerified;
                // TODO: Map other channel fields (access_hash, date, version, restrictions)
                // tlChannel.access_hash = chat. // Not directly available in TdApi.Chat for general listing
                // tlChannel.date = chat. // Not directly available
                // tlChannel.megagroup = !supergroupType.isChannel; // This is incorrect, it's a channel
                tlChannel.megagroup = true; // Actually, TdApi combines channels and supergroups. isChannel distinguishes.
                                           // For TLRPC, if it's a supergroup, megagroup is true.
                                           // If it's a broadcast channel, megagroup is false.
                tlChannel.gigagroup = chat.isGigagroup;

                // Permissions mapping is complex and needs TdApi.ChatPermissions
                // tlChannel.banned_rights = convertChatPermissions(chat.permissions);
                // tlChannel.admin_rights = ...
                tlChat = tlChannel;
            } else {
                TLRPC.TL_channel tlSupergroup = new TLRPC.TL_channel(); // Use TL_channel for supergroups too in TLRPC
                tlSupergroup.id = supergroupType.supergroupId;
                tlSupergroup.title = chat.title;
                tlSupergroup.photo = convertChatPhotoInfo(chat.photo);
                tlSupergroup.username = chat.username;
                tlSupergroup.verified = chat.isVerified;
                tlSupergroup.megagroup = true;
                tlSupergroup.gigagroup = chat.isGigagroup;
                // TODO: Map other supergroup fields
                tlChat = tlSupergroup;
            }

        } else if (type instanceof TdApi.ChatTypeSecret) {
            TLRPC.TL_chatEncrypted tlSecretChat = new TLRPC.TL_chatEncrypted();
            tlSecretChat.id = ((TdApi.ChatTypeSecret) type).secretChatId;
            // Other fields like user_id, access_hash, date, key_fingerprint, etc.
            // need to be fetched from TdApi.SecretChat
            tlChat = tlSecretChat;
        }
        else {
            return null; // Unknown chat type
        }

        // Common fields
        // tlChat.id is set above based on type
        // tlChat.title = chat.title; // Set above
        // tlChat.photo = convertChatPhotoInfo(chat.photo); // Set above
        // tlChat.participants_count = chat.memberCount; // Set above for basic group

        // Note: Many fields in TLRPC.Chat (like admin_rights, banned_rights, default_banned_rights, date, version)
        // are not directly available in TdApi.Chat and would require fetching TdApi.SupergroupFullInfo,
        // TdApi.BasicGroupFullInfo, or TdApi.ChatPermissions.
        // This basic conversion is for list displays or initial objects.

        return tlChat;
    }


    public static TLRPC.UserProfilePhoto convertUserProfilePhoto(TdApi.ProfilePhoto photo) {
        if (photo == null) {
            return new TLRPC.TL_userProfilePhotoEmpty();
        }
        TLRPC.TL_userProfilePhoto userProfilePhoto = new TLRPC.TL_userProfilePhoto();
        userProfilePhoto.photo_id = photo.id; // This ID might not map directly if used elsewhere
        userProfilePhoto.photo_small = convertFile(photo.small);
        userProfilePhoto.photo_big = convertFile(photo.big);
        // userProfilePhoto.dc_id = photo.small.remote.id; // TdApi.File doesn't have dc_id
        return userProfilePhoto;
    }

    public static TLRPC.ChatPhoto convertChatPhotoInfo(TdApi.ChatPhotoInfo photoInfo) {
        if (photoInfo == null) {
            return new TLRPC.TL_chatPhotoEmpty();
        }
        TLRPC.TL_chatPhoto chatPhoto = new TLRPC.TL_chatPhoto();
        // chatPhoto.photo_id = ? // TdApi.ChatPhotoInfo doesn't have a direct photo_id like TLRPC
        chatPhoto.photo_small = convertFile(photoInfo.small);
        chatPhoto.photo_big = convertFile(photoInfo.big);
        // chatPhoto.dc_id = photoInfo.small.remote.id; // TdApi.File doesn't have dc_id
        return chatPhoto;
    }

    public static TLRPC.FileLocation convertFile(TdApi.File file) {
        if (file == null || file.remote == null) { // Check remote as local might be empty before download
            return null; // Or new TLRPC.TL_fileLocationUnavailable();
        }
        // This is a lossy conversion as TdApi.File is much richer.
        // TLRPC.FileLocation is more of a pointer.
        // The key challenge is that TdApi uses file IDs (file.id) and server-side remote file IDs (file.remote.id)
        // while TLRPC often expects volume_id, local_id, secret.
        // For display purposes, we might not need a full TLRPC.FileLocation if ImageLoader can handle TdApi.File directly or via path.
        // If direct TLRPC.FileLocation is needed for old systems, this mapping will be tricky and potentially incomplete.

        TLRPC.TL_fileLocationToBeDeprecated tlFileLocation = new TLRPC.TL_fileLocationToBeDeprecated();
        // Attempting a loose mapping. This is highly dependent on how the rest of the system uses these fields.
        // It's very likely that the old system's FileLoader won't work with these directly.
        try {
            if (file.remote.id != null && !file.remote.id.isEmpty()) {
                // Try to parse volume and local_id if the remote_id is in a specific format. This is a guess.
                String[] parts = file.remote.id.split("_");
                if (parts.length >= 2) {
                    tlFileLocation.volume_id = Long.parseLong(parts[0]);
                    tlFileLocation.local_id = Integer.parseInt(parts[1]);
                }
            }
        } catch (NumberFormatException e) {
            // Could not parse, leave them as 0 or handle error
        }
        // tlFileLocation.secret = ? // Not available in TdApi.File
        // tlFileLocation.dc_id = ? // Not available in TdApi.File (dc_id is part of TdApi.RemoteFileLocation which isn't here)

        // If the file is local, we might use the path
        // if (file.local != null && file.local.path != null && !file.local.path.isEmpty()) {
        //    // Potentially store the path in a custom field or handle it differently
        // }

        return tlFileLocation; // This is likely insufficient for full compatibility.
    }

    public static TLRPC.UserStatus convertUserStatus(TdApi.UserStatus status) {
        if (status == null) {
            return new TLRPC.TL_userStatusEmpty();
        }
        if (status instanceof TdApi.UserStatusOnline) {
            TLRPC.TL_userStatusOnline statusOnline = new TLRPC.TL_userStatusOnline();
            statusOnline.expires = ((TdApi.UserStatusOnline) status).expires;
            return statusOnline;
        } else if (status instanceof TdApi.UserStatusOffline) {
            TLRPC.TL_userStatusOffline statusOffline = new TLRPC.TL_userStatusOffline();
            statusOffline.was_online = ((TdApi.UserStatusOffline) status).wasOnline;
            return statusOffline;
        } else if (status instanceof TdApi.UserStatusRecently) {
            return new TLRPC.TL_userStatusRecently();
        } else if (status instanceof TdApi.UserStatusLastWeek) {
            return new TLRPC.TL_userStatusLastWeek();
        } else if (status instanceof TdApi.UserStatusLastMonth) {
            return new TLRPC.TL_userStatusLastMonth();
        }
        return new TLRPC.TL_userStatusEmpty();
    }

    public static TLRPC.Message convertMessage(TdApi.Message message, int currentUserId) {
        if (message == null) {
            return null;
        }

        TLRPC.TL_message tlMessage = new TLRPC.TL_message();
        tlMessage.id = message.id;
        tlMessage.peer_id = convertPeer(message.chatId); // This needs proper mapping based on chatId

        if (message.senderId instanceof TdApi.MessageSenderUser) {
            TLRPC.TL_peerUser fromPeer = new TLRPC.TL_peerUser();
            fromPeer.user_id = ((TdApi.MessageSenderUser) message.senderId).userId;
            tlMessage.from_id = fromPeer;
        } else if (message.senderId instanceof TdApi.MessageSenderChat) {
             TLRPC.TL_peerChannel fromChannel = new TLRPC.TL_peerChannel(); // Or TL_peerChat for basic groups
             fromChannel.channel_id = ((TdApi.MessageSenderChat) message.senderId).chatId; // This is problematic as TL_peerChannel expects positive ID
                                                                                             // and TDLib uses positive chat_id for channels/supergroups
             // This conversion needs to be more robust based on actual chat type
             tlMessage.from_id = fromChannel;
        }


        tlMessage.date = message.date;
        tlMessage.out = message.isOutgoing;
        // tlMessage.mentioned = message.isMentioned; // TdApi.Message doesn't have this directly, comes from TdApi.MessageContent
        tlMessage.media_unread = !message.isRead; // Approximation
        tlMessage.silent = message.isSilent;
        tlMessage.post = message.isChannelPost;
        // tlMessage.from_scheduled = message.isFromScheduled; // TdApi.Message doesn't have this
        tlMessage.legacy = false; // TDLib messages are not legacy
        tlMessage.edit_hide = message.editDate > 0 && !message.canBeEdited; // Approximation
        tlMessage.pinned = message.isPinned;

        if (message.replyTo instanceof TdApi.MessageReplyToMessage) {
            tlMessage.reply_to = new TLRPC.TL_messageReplyHeader();
            tlMessage.reply_to.reply_to_msg_id = ((TdApi.MessageReplyToMessage)message.replyTo).messageId;
            tlMessage.reply_to.reply_to_peer_id = convertPeer(((TdApi.MessageReplyToMessage)message.replyTo).chatId);
            // TODO: reply_to_top_id if it's a reply in a thread
            tlMessage.flags |= 8; // Set reply flag
        }

        // Content conversion is the most complex part
        convertMessageContent(message.content, tlMessage, message);

        // TODO: Forwarding info
        if (message.forwardInfo != null) {
            tlMessage.fwd_from = convertForwardInfo(message.forwardInfo);
            tlMessage.flags |= 4;
        }

        // TODO: Reply markup
        if (message.replyMarkup != null) {
            tlMessage.reply_markup = convertReplyMarkup(message.replyMarkup);
            tlMessage.flags |= 64;
        }

        // TODO: Entities (from message.content if it's text)

        // TODO: Views, forwards, replies (these are often part of full message info)
        // tlMessage.views = message.views;
        // tlMessage.forwards = message.forwards;
        // tlMessage.replies = convertMessageReplies(message.replyInfo);

        // tlMessage.edit_date = message.editDate;
        // tlMessage.post_author = message.authorSignature;
        // tlMessage.grouped_id = message.mediaAlbumId; // If part of an album

        return tlMessage;
    }

    public static TLRPC.Peer convertPeer(long chatId) {
        // TDLib uses positive chat IDs for supergroups/channels and users.
        // TLRPC uses negative for chats/channels.
        // This needs context about what `chatId` represents (is it a user, basic group, supergroup?)
        // For now, a placeholder. This needs to be resolved with more context from TdApi.getChat(chatId)

        // Placeholder logic, assuming positive IDs are users, negative are chats
        // This is NOT how TDLib works. TDLib chat_id is always positive.
        // You need to call TdApi.getChat(chatId) to know its type.
        if (chatId > 0) { // This condition is wrong for TDLib chat_id
            TLRPC.TL_peerUser peerUser = new TLRPC.TL_peerUser();
            peerUser.user_id = chatId;
            return peerUser;
        } else {
            // Need to distinguish between Chat and Channel
            TLRPC.TL_peerChannel peerChannel = new TLRPC.TL_peerChannel();
            peerChannel.channel_id = -chatId; // Assuming negative means channel/chat
            return peerChannel;
        }
    }

    public static TLRPC.MessageFwdHeader convertForwardInfo(TdApi.MessageForwardInfo forwardInfo) {
        if (forwardInfo == null) return null;

        TLRPC.TL_messageFwdHeader fwdHeader = new TLRPC.TL_messageFwdHeader();
        fwdHeader.date = forwardInfo.date;

        if (forwardInfo.origin instanceof TdApi.MessageForwardOriginUser) {
            fwdHeader.from_id = new TLRPC.TL_peerUser();
            fwdHeader.from_id.user_id = ((TdApi.MessageForwardOriginUser) forwardInfo.origin).senderUserId;
        } else if (forwardInfo.origin instanceof TdApi.MessageForwardOriginChat) {
            fwdHeader.from_id = new TLRPC.TL_peerChannel(); // Or TL_peerChat
            // This part is tricky, TdApi.MessageForwardOriginChat has senderChatId
            // which needs to be resolved to its type (basic group/supergroup)
            // For now, assume channel
            ((TLRPC.TL_peerChannel)fwdHeader.from_id).channel_id = ((TdApi.MessageForwardOriginChat) forwardInfo.origin).senderChatId;
        } else if (forwardInfo.origin instanceof TdApi.MessageForwardOriginChannel) {
            fwdHeader.from_id = new TLRPC.TL_peerChannel();
            ((TLRPC.TL_peerChannel)fwdHeader.from_id).channel_id = ((TdApi.MessageForwardOriginChannel) forwardInfo.origin).chatId;
            fwdHeader.channel_post = ((TdApi.MessageForwardOriginChannel) forwardInfo.origin).messageId;
            fwdHeader.post_author = ((TdApi.MessageForwardOriginChannel) forwardInfo.origin).authorSignature;
            fwdHeader.flags |= 4; // set post_author flag if present
        }
        // TODO: Handle other forward origin types like MessageForwardOriginHiddenUser
        // TODO: fwdHeader.saved_from_peer and saved_from_msg_id for forwards from "Saved Messages"

        return fwdHeader;
    }

    public static TLRPC.ReplyMarkup convertReplyMarkup(TdApi.ReplyMarkup replyMarkup) {
        if (replyMarkup == null) return null;

        if (replyMarkup instanceof TdApi.ReplyMarkupInlineKeyboard) {
            TdApi.ReplyMarkupInlineKeyboard inlineKeyboard = (TdApi.ReplyMarkupInlineKeyboard) replyMarkup;
            TLRPC.TL_replyInlineMarkup tlInlineMarkup = new TLRPC.TL_replyInlineMarkup();
            tlInlineMarkup.rows = new ArrayList<>();
            for (TdApi.InlineKeyboardButton[] row : inlineKeyboard.rows) {
                TLRPC.TL_keyboardButtonRow tlRow = new TLRPC.TL_keyboardButtonRow();
                tlRow.buttons = new ArrayList<>();
                for (TdApi.InlineKeyboardButton button : row) {
                    tlRow.buttons.add(convertInlineKeyboardButton(button));
                }
                tlInlineMarkup.rows.add(tlRow);
            }
            return tlInlineMarkup;
        } else if (replyMarkup instanceof TdApi.ReplyMarkupShowKeyboard) {
            TdApi.ReplyMarkupShowKeyboard showKeyboard = (TdApi.ReplyMarkupShowKeyboard) replyMarkup;
            TLRPC.TL_replyKeyboardMarkup tlKeyboardMarkup = new TLRPC.TL_replyKeyboardMarkup();
            tlKeyboardMarkup.rows = new ArrayList<>();
            for (TdApi.KeyboardButton[] row : showKeyboard.rows) {
                TLRPC.TL_keyboardButtonRow tlRow = new TLRPC.TL_keyboardButtonRow();
                tlRow.buttons = new ArrayList<>();
                for (TdApi.KeyboardButton button : row) {
                    tlRow.buttons.add(convertKeyboardButton(button));
                }
                tlKeyboardMarkup.rows.add(tlRow);
            }
            tlKeyboardMarkup.resize = showKeyboard.resizeKeyboard;
            tlKeyboardMarkup.single_use = showKeyboard.oneTime;
            tlKeyboardMarkup.selective = showKeyboard.isPersonal;
            tlKeyboardMarkup.placeholder = showKeyboard.inputFieldPlaceholder;
            return tlKeyboardMarkup;
        } else if (replyMarkup instanceof TdApi.ReplyMarkupRemoveKeyboard) {
            TLRPC.TL_replyKeyboardHide tlHide = new TLRPC.TL_replyKeyboardHide();
            tlHide.selective = ((TdApi.ReplyMarkupRemoveKeyboard) replyMarkup).isPersonal;
            return tlHide;
        } else if (replyMarkup instanceof TdApi.ReplyMarkupForceReply) {
            TLRPC.TL_replyKeyboardForceReply tlForceReply = new TLRPC.TL_replyKeyboardForceReply();
            tlForceReply.single_use = ((TdApi.ReplyMarkupForceReply) replyMarkup).isPersonal; // TDLib's is_personal seems to map to selective
            tlForceReply.selective = ((TdApi.ReplyMarkupForceReply) replyMarkup).isPersonal;
            tlForceReply.placeholder = ((TdApi.ReplyMarkupForceReply) replyMarkup).inputFieldPlaceholder;
            return tlForceReply;
        }
        return null;
    }

    public static TLRPC.KeyboardButton convertInlineKeyboardButton(TdApi.InlineKeyboardButton button) {
        TLRPC.KeyboardButton tlButton = null;
        TdApi.InlineKeyboardButtonType type = button.type;

        if (type instanceof TdApi.InlineKeyboardButtonTypeUrl) {
            TLRPC.TL_keyboardButtonUrl tlUrlButton = new TLRPC.TL_keyboardButtonUrl();
            tlUrlButton.text = button.text;
            tlUrlButton.url = ((TdApi.InlineKeyboardButtonTypeUrl) type).url;
            tlButton = tlUrlButton;
        } else if (type instanceof TdApi.InlineKeyboardButtonTypeCallback) {
            TLRPC.TL_keyboardButtonCallback tlCallbackButton = new TLRPC.TL_keyboardButtonCallback();
            tlCallbackButton.text = button.text;
            tlCallbackButton.data = ((TdApi.InlineKeyboardButtonTypeCallback) type).data;
            tlButton = tlCallbackButton;
        } else if (type instanceof TdApi.InlineKeyboardButtonTypeSwitchInline) {
            TLRPC.TL_keyboardButtonSwitchInline tlSwitchInlineButton = new TLRPC.TL_keyboardButtonSwitchInline();
            tlSwitchInlineButton.text = button.text;
            tlSwitchInlineButton.query = ((TdApi.InlineKeyboardButtonTypeSwitchInline) type).query;
            tlSwitchInlineButton.same_peer = ((TdApi.InlineKeyboardButtonTypeSwitchInline)type).targetChat instanceof TdApi.TargetChatCurrent;
            // TODO: peer_types for newer switch_inline options
            tlButton = tlSwitchInlineButton;
        }
        // TODO: Add other InlineKeyboardButtonType conversions (e.g., LoginUrl, Buy, UserProfile, WebApp)
        else {
            // Fallback for unhandled types
            TLRPC.TL_keyboardButtonCallback tlUnknownButton = new TLRPC.TL_keyboardButtonCallback();
            tlUnknownButton.text = button.text;
            tlUnknownButton.data = new byte[0]; // Empty data
            tlButton = tlUnknownButton;
        }
        return tlButton;
    }

    public static TLRPC.KeyboardButton convertKeyboardButton(TdApi.KeyboardButton button) {
        TLRPC.KeyboardButton tlButton = null;
        TdApi.KeyboardButtonType type = button.type;

        if (type instanceof TdApi.KeyboardButtonTypeText) {
            TLRPC.TL_keyboardButton tlTextButton = new TLRPC.TL_keyboardButton();
            tlTextButton.text = button.text;
            tlButton = tlTextButton;
        } else if (type instanceof TdApi.KeyboardButtonTypeRequestPhoneNumber) {
            TLRPC.TL_keyboardButtonRequestPhone tlRequestPhoneButton = new TLRPC.TL_keyboardButtonRequestPhone();
            tlRequestPhoneButton.text = button.text;
            tlButton = tlRequestPhoneButton;
        } else if (type instanceof TdApi.KeyboardButtonTypeRequestLocation) {
            TLRPC.TL_keyboardButtonRequestGeoLocation tlRequestLocationButton = new TLRPC.TL_keyboardButtonRequestGeoLocation();
            tlRequestLocationButton.text = button.text;
            tlButton = tlRequestLocationButton;
        }
        // TODO: Add other KeyboardButtonType conversions (e.g., RequestPoll, WebApp)
        else {
            TLRPC.TL_keyboardButton tlUnknownButton = new TLRPC.TL_keyboardButton();
            tlUnknownButton.text = button.text; // Default text
            tlButton = tlUnknownButton;
        }
        return tlButton;
    }


    public static void convertMessageContent(TdApi.MessageContent content, TLRPC.TL_message tlMessage, TdApi.Message originalTdMessage) {
        if (content instanceof TdApi.MessageText) {
            TdApi.MessageText messageText = (TdApi.MessageText) content;
            tlMessage.message = messageText.text.text;
            tlMessage.entities = convertTextEntities(messageText.text.entities);
            if (messageText.webPage != null) {
                tlMessage.media = new TLRPC.TL_messageMediaWebPage();
                ((TLRPC.TL_messageMediaWebPage)tlMessage.media).webpage = convertWebPage(messageText.webPage);
                tlMessage.flags |= 512; // has_media flag
            }
        } else if (content instanceof TdApi.MessagePhoto) {
            TdApi.MessagePhoto messagePhoto = (TdApi.MessagePhoto) content;
            TLRPC.TL_messageMediaPhoto mediaPhoto = new TLRPC.TL_messageMediaPhoto();
            mediaPhoto.photo = convertPhoto(messagePhoto.photo);
            mediaPhoto.ttl_seconds = messagePhoto.ttl;
            tlMessage.media = mediaPhoto;
            tlMessage.flags |= 512;
            if (messagePhoto.caption != null) {
                tlMessage.message = messagePhoto.caption.text; // Caption goes into message field for media
                tlMessage.entities = convertTextEntities(messagePhoto.caption.entities);
            }
        } else if (content instanceof TdApi.MessageVideo) {
            TdApi.MessageVideo messageVideo = (TdApi.MessageVideo) content;
            TLRPC.TL_messageMediaDocument mediaVideo = new TLRPC.TL_messageMediaDocument(); // Videos are often represented as documents
            mediaVideo.document = convertVideo(messageVideo.video);
            mediaVideo.ttl_seconds = messageVideo.ttl;
            tlMessage.media = mediaVideo;
            tlMessage.flags |= 512;
            if (messageVideo.caption != null) {
                tlMessage.message = messageVideo.caption.text;
                tlMessage.entities = convertTextEntities(messageVideo.caption.entities);
            }
        } else if (content instanceof TdApi.MessageAnimation) {
            TdApi.MessageAnimation messageAnimation = (TdApi.MessageAnimation) content;
            TLRPC.TL_messageMediaDocument mediaAnimation = new TLRPC.TL_messageMediaDocument();
            mediaAnimation.document = convertAnimation(messageAnimation.animation);
            tlMessage.media = mediaAnimation;
            tlMessage.flags |= 512;
            if (messageAnimation.caption != null) {
                tlMessage.message = messageAnimation.caption.text;
                tlMessage.entities = convertTextEntities(messageAnimation.caption.entities);
            }
        } else if (content instanceof TdApi.MessageAudio) {
            TdApi.MessageAudio messageAudio = (TdApi.MessageAudio) content;
            TLRPC.TL_messageMediaDocument mediaAudio = new TLRPC.TL_messageMediaDocument();
            mediaAudio.document = convertAudio(messageAudio.audio);
            tlMessage.media = mediaAudio;
            tlMessage.flags |= 512;
            if (messageAudio.caption != null) {
                tlMessage.message = messageAudio.caption.text;
                tlMessage.entities = convertTextEntities(messageAudio.caption.entities);
            }
        } else if (content instanceof TdApi.MessageVoiceNote) {
            TdApi.MessageVoiceNote messageVoiceNote = (TdApi.MessageVoiceNote) content;
            TLRPC.TL_messageMediaDocument mediaVoice = new TLRPC.TL_messageMediaDocument();
            mediaVoice.document = convertVoiceNote(messageVoiceNote.voiceNote);
            tlMessage.media = mediaVoice;
            tlMessage.flags |= 512;
            if (messageVoiceNote.caption != null) {
                tlMessage.message = messageVoiceNote.caption.text;
                tlMessage.entities = convertTextEntities(messageVoiceNote.caption.entities);
            }
        } else if (content instanceof TdApi.MessageDocument) {
            TdApi.MessageDocument messageDocument = (TdApi.MessageDocument) content;
            TLRPC.TL_messageMediaDocument mediaDocument = new TLRPC.TL_messageMediaDocument();
            mediaDocument.document = convertDocument(messageDocument.document);
            tlMessage.media = mediaDocument;
            tlMessage.flags |= 512;
            if (messageDocument.caption != null) {
                tlMessage.message = messageDocument.caption.text;
                tlMessage.entities = convertTextEntities(messageDocument.caption.entities);
            }
        } else if (content instanceof TdApi.MessageSticker) {
            TdApi.MessageSticker messageSticker = (TdApi.MessageSticker) content;
            TLRPC.TL_messageMediaDocument mediaSticker = new TLRPC.TL_messageMediaDocument(); // Stickers are documents
            mediaSticker.document = convertSticker(messageSticker.sticker);
            tlMessage.media = mediaSticker;
            tlMessage.flags |= 512;
            // Stickers don't have captions in TLRPC.MessageMediaDocument
        } else if (content instanceof TdApi.MessageContact) {
            TdApi.MessageContact messageContact = (TdApi.MessageContact) content;
            TLRPC.TL_messageMediaContact mediaContact = new TLRPC.TL_messageMediaContact();
            mediaContact.phone_number = messageContact.contact.phoneNumber;
            mediaContact.first_name = messageContact.contact.firstName;
            mediaContact.last_name = messageContact.contact.lastName;
            mediaContact.user_id = messageContact.contact.userId;
            mediaContact.vcard = messageContact.contact.vcard;
            tlMessage.media = mediaContact;
            tlMessage.flags |= 512;
        } else if (content instanceof TdApi.MessageLocation) {
            TdApi.MessageLocation messageLocation = (TdApi.MessageLocation) content;
            TLRPC.TL_messageMediaGeo mediaGeo = new TLRPC.TL_messageMediaGeo();
            mediaGeo.geo = new TLRPC.TL_geoPoint();
            mediaGeo.geo.lat = messageLocation.location.latitude;
            mediaGeo.geo._long = messageLocation.location.longitude;
            mediaGeo.geo.accuracy_radius = (int)messageLocation.location.horizontalAccuracy; // Possible precision loss
            // TODO: heading, proximity_notification_radius for live locations
            tlMessage.media = mediaGeo;
            tlMessage.flags |= 512;
        } else if (content instanceof TdApi.MessageChatJoinByLink) {
            tlMessage.action = new TLRPC.TL_messageActionChatJoinedByLink();
            // ((TLRPC.TL_messageActionChatJoinedByLink)tlMessage.action).inviter_id = ((TdApi.MessageChatJoinByLink) content).inviterUserId; // Not directly available in TdApi.MessageChatJoinByLink
            tlMessage.flags |= 256; // has_action flag
        } else if (content instanceof TdApi.MessageChatAddMembers) {
            TdApi.MessageChatAddMembers addMembers = (TdApi.MessageChatAddMembers)content;
            TLRPC.TL_messageActionChatAddUser action = new TLRPC.TL_messageActionChatAddUser();
            action.users = new ArrayList<>();
            for(long memberId : addMembers.memberUserIds) {
                action.users.add(memberId);
            }
            tlMessage.action = action;
            tlMessage.flags |= 256;
        } else if (content instanceof TdApi.MessageChatDeleteMember) {
            TdApi.MessageChatDeleteMember deleteMember = (TdApi.MessageChatDeleteMember)content;
            TLRPC.TL_messageActionChatDeleteUser action = new TLRPC.TL_messageActionChatDeleteUser();
            action.user_id = deleteMember.userId;
            tlMessage.action = action;
            tlMessage.flags |= 256;
        }
        // TODO: Add conversions for ALL TdApi.MessageContent types
        // (MessageVideoNote, MessageExpiredPhoto, MessageExpiredVideo, MessageGame, MessagePoll, MessageDice, etc.)
        else {
            tlMessage.message = "[Unsupported message content type: " + content.getClass().getSimpleName() + "]";
        }
    }

    public static TLRPC.Photo convertPhoto(TdApi.Photo photo) {
        if (photo == null) return null;
        TLRPC.TL_photo tlPhoto = new TLRPC.TL_photo();
        tlPhoto.id = photo.id; // This ID might not map directly
        tlPhoto.access_hash = 0; // Not available in TdApi.Photo generally
        tlPhoto.date = 0; // Not directly available, usually comes with message
        tlPhoto.sizes = new ArrayList<>();
        for (TdApi.PhotoSize tdSize : photo.sizes) {
            TLRPC.PhotoSize tlSize = convertPhotoSize(tdSize);
            if (tlSize != null) {
                tlPhoto.sizes.add(tlSize);
            }
        }
        // TODO: video_sizes if it's an animated profile photo
        return tlPhoto;
    }

    public static TLRPC.PhotoSize convertPhotoSize(TdApi.PhotoSize tdSize) {
        if (tdSize == null) return null;
        TLRPC.TL_photoSize tlSize = new TLRPC.TL_photoSize();
        tlSize.type = tdSize.type;
        tlSize.location = convertFile(tdSize.photo);
        tlSize.w = tdSize.width;
        tlSize.h = tdSize.height;
        // tlSize.size = tdSize.photo.size; // Not always accurate as tdSize.photo.size is expected size
        return tlSize;
    }

    public static TLRPC.Document convertVideo(TdApi.Video video) {
        if (video == null) return null;
        TLRPC.TL_document tlDocument = new TLRPC.TL_document();
        tlDocument.id = video.video.id; // TDLib internal file ID
        // tlDocument.access_hash = 0; // Not available
        // tlDocument.file_reference = video.video.remote.uniqueId.getBytes(); // Or some other stable reference
        tlDocument.date = 0; // Not directly available
        tlDocument.mime_type = video.mimeType;
        tlDocument.size = video.video.expectedSize > 0 ? video.video.expectedSize : video.video.size;

        TLRPC.TL_documentAttributeVideo attributeVideo = new TLRPC.TL_documentAttributeVideo();
        attributeVideo.duration = video.duration;
        attributeVideo.w = video.width;
        attributeVideo.h = video.height;
        attributeVideo.supports_streaming = video.supportsStreaming;
        // attributeVideo.round_message = false; // For regular videos
        tlDocument.attributes.add(attributeVideo);

        TLRPC.TL_documentAttributeFilename attributeFilename = new TLRPC.TL_documentAttributeFilename();
        attributeFilename.file_name = video.fileName;
        tlDocument.attributes.add(attributeFilename);

        if (video.thumbnail != null) {
            tlDocument.thumbs.add(convertPhotoSize(convertThumbnailToPhotoSize(video.thumbnail)));
        }
        // tlDocument.dc_id = 0; // Not directly available
        return tlDocument;
    }

    public static TLRPC.Document convertAnimation(TdApi.Animation animation) {
        if (animation == null) return null;
        TLRPC.TL_document tlDocument = new TLRPC.TL_document();
        tlDocument.id = animation.animation.id;
        tlDocument.mime_type = animation.mimeType;
        tlDocument.size = animation.animation.expectedSize > 0 ? animation.animation.expectedSize : animation.animation.size;

        TLRPC.TL_documentAttributeAnimated attributeAnimated = new TLRPC.TL_documentAttributeAnimated();
        tlDocument.attributes.add(attributeAnimated);

        TLRPC.TL_documentAttributeVideo attributeVideo = new TLRPC.TL_documentAttributeVideo();
        attributeVideo.duration = animation.duration;
        attributeVideo.w = animation.width;
        attributeVideo.h = animation.height;
        tlDocument.attributes.add(attributeVideo);

        TLRPC.TL_documentAttributeFilename attributeFilename = new TLRPC.TL_documentAttributeFilename();
        attributeFilename.file_name = animation.fileName;
        tlDocument.attributes.add(attributeFilename);

        if (animation.thumbnail != null) {
            tlDocument.thumbs.add(convertPhotoSize(convertThumbnailToPhotoSize(animation.thumbnail)));
        }
        return tlDocument;
    }

    public static TLRPC.Document convertAudio(TdApi.Audio audio) {
        if (audio == null) return null;
        TLRPC.TL_document tlDocument = new TLRPC.TL_document();
        tlDocument.id = audio.audio.id;
        tlDocument.mime_type = audio.mimeType;
        tlDocument.size = audio.audio.expectedSize > 0 ? audio.audio.expectedSize : audio.audio.size;

        TLRPC.TL_documentAttributeAudio attributeAudio = new TLRPC.TL_documentAttributeAudio();
        attributeAudio.duration = audio.duration;
        attributeAudio.title = audio.title;
        attributeAudio.performer = audio.performer;
        // attributeAudio.voice = false; // For music
        tlDocument.attributes.add(attributeAudio);

        TLRPC.TL_documentAttributeFilename attributeFilename = new TLRPC.TL_documentAttributeFilename();
        attributeFilename.file_name = audio.fileName;
        tlDocument.attributes.add(attributeFilename);

        if (audio.albumCoverThumbnail != null) {
             tlDocument.thumbs.add(convertPhotoSize(convertThumbnailToPhotoSize(audio.albumCoverThumbnail)));
        }
        return tlDocument;
    }

    public static TLRPC.Document convertVoiceNote(TdApi.VoiceNote voiceNote) {
        if (voiceNote == null) return null;
        TLRPC.TL_document tlDocument = new TLRPC.TL_document();
        tlDocument.id = voiceNote.voice.id;
        tlDocument.mime_type = voiceNote.mimeType;
        tlDocument.size = voiceNote.voice.expectedSize > 0 ? voiceNote.voice.expectedSize : voiceNote.voice.size;

        TLRPC.TL_documentAttributeAudio attributeAudio = new TLRPC.TL_documentAttributeAudio();
        attributeAudio.duration = voiceNote.duration;
        attributeAudio.voice = true;
        attributeAudio.waveform = voiceNote.waveform;
        tlDocument.attributes.add(attributeAudio);

        // Voice notes usually don't have filenames in the same way other documents do
        return tlDocument;
    }

    public static TLRPC.Document convertDocument(TdApi.Document document) {
        if (document == null) return null;
        TLRPC.TL_document tlDocument = new TLRPC.TL_document();
        tlDocument.id = document.document.id;
        tlDocument.mime_type = document.mimeType;
        tlDocument.size = document.document.expectedSize > 0 ? document.document.expectedSize : document.document.size;

        TLRPC.TL_documentAttributeFilename attributeFilename = new TLRPC.TL_documentAttributeFilename();
        attributeFilename.file_name = document.fileName;
        tlDocument.attributes.add(attributeFilename);

        if (document.thumbnail != null) {
             tlDocument.thumbs.add(convertPhotoSize(convertThumbnailToPhotoSize(document.thumbnail)));
        }
        // TODO: Other attributes like video if it's a video document
        return tlDocument;
    }

    public static TLRPC.Document convertSticker(TdApi.Sticker sticker) {
        if (sticker == null) return null;
        TLRPC.TL_document tlDocument = new TLRPC.TL_document(); // Stickers are a type of document
        tlDocument.id = sticker.sticker.id;
        tlDocument.mime_type = sticker.type instanceof TdApi.StickerTypeStatic ? "image/webp" : "application/x-tgsticker"; // Approximate
        tlDocument.size = sticker.sticker.expectedSize > 0 ? sticker.sticker.expectedSize : sticker.sticker.size;

        TLRPC.TL_documentAttributeSticker attributeSticker = new TLRPC.TL_documentAttributeSticker();
        attributeSticker.alt = sticker.emoji;
        // attributeSticker.stickerset = convertInputStickerSet(sticker.setId); // Needs TdApi.StickerSetInfo
        tlDocument.attributes.add(attributeSticker);

        TLRPC.TL_documentAttributeImageSize attributeImageSize = new TLRPC.TL_documentAttributeImageSize();
        attributeImageSize.w = sticker.width;
        attributeImageSize.h = sticker.height;
        tlDocument.attributes.add(attributeImageSize);

        if (sticker.thumbnail != null) {
            tlDocument.thumbs.add(convertPhotoSize(convertThumbnailToPhotoSize(sticker.thumbnail)));
        }
        return tlDocument;
    }

    public static TdApi.PhotoSize convertThumbnailToPhotoSize(TdApi.Thumbnail thumbnail) {
        if (thumbnail == null) return null;
        TdApi.PhotoSize photoSize = new TdApi.PhotoSize();
        photoSize.type = thumbnail.format.toString(); // This is not a direct mapping to 'a', 'b', 'c' types
        photoSize.photo = thumbnail.file;
        photoSize.width = thumbnail.width;
        photoSize.height = thumbnail.height;
        return photoSize;
    }

    public static ArrayList<TLRPC.MessageEntity> convertTextEntities(TdApi.TextEntity[] entities) {
        if (entities == null) return new ArrayList<>();
        ArrayList<TLRPC.MessageEntity> tlEntities = new ArrayList<>(entities.length);
        for (TdApi.TextEntity entity : entities) {
            TLRPC.MessageEntity tlEntity = null;
            TdApi.TextEntityType type = entity.type;
            if (type instanceof TdApi.TextEntityTypeMention) {
                TLRPC.TL_messageEntityMention tlMention = new TLRPC.TL_messageEntityMention();
                // UserId is not available in TdApi.TextEntityTypeMention, needs separate resolution
                tlEntity = tlMention;
            } else if (type instanceof TdApi.TextEntityTypeHashtag) {
                tlEntity = new TLRPC.TL_messageEntityHashtag();
            } else if (type instanceof TdApi.TextEntityTypeBotCommand) {
                tlEntity = new TLRPC.TL_messageEntityBotCommand();
            } else if (type instanceof TdApi.TextEntityTypeUrl) {
                tlEntity = new TLRPC.TL_messageEntityUrl();
            } else if (type instanceof TdApi.TextEntityTypeEmailAddress) {
                tlEntity = new TLRPC.TL_messageEntityEmail();
            } else if (type instanceof TdApi.TextEntityTypeBold) {
                tlEntity = new TLRPC.TL_messageEntityBold();
            } else if (type instanceof TdApi.TextEntityTypeItalic) {
                tlEntity = new TLRPC.TL_messageEntityItalic();
            } else if (type instanceof TdApi.TextEntityTypeUnderline) {
                tlEntity = new TLRPC.TL_messageEntityUnderline();
            } else if (type instanceof TdApi.TextEntityTypeStrikethrough) {
                tlEntity = new TLRPC.TL_messageEntityStrike();
            } else if (type instanceof TdApi.TextEntityTypeSpoiler) {
                tlEntity = new TLRPC.TL_messageEntitySpoiler();
            } else if (type instanceof TdApi.TextEntityTypeCode) {
                tlEntity = new TLRPC.TL_messageEntityCode();
            } else if (type instanceof TdApi.TextEntityTypePre) {
                tlEntity = new TLRPC.TL_messageEntityPre();
                ((TLRPC.TL_messageEntityPre)tlEntity).language = ((TdApi.TextEntityTypePre) type).language;
            } else if (type instanceof TdApi.TextEntityTypePreCode) {
                tlEntity = new TLRPC.TL_messageEntityPre(); // Map to Pre as well
                ((TLRPC.TL_messageEntityPre)tlEntity).language = ((TdApi.TextEntityTypePreCode) type).language;
            } else if (type instanceof TdApi.TextEntityTypeTextUrl) {
                TLRPC.TL_messageEntityTextUrl tlTextUrl = new TLRPC.TL_messageEntityTextUrl();
                tlTextUrl.url = ((TdApi.TextEntityTypeTextUrl) type).url;
                tlEntity = tlTextUrl;
            } else if (type instanceof TdApi.TextEntityTypeMentionName) {
                TLRPC.TL_messageEntityMentionName tlMentionName = new TLRPC.TL_messageEntityMentionName();
                tlMentionName.user_id = ((TdApi.TextEntityTypeMentionName) type).userId;
                tlEntity = tlMentionName;
            } else if (type instanceof TdApi.TextEntityTypeCustomEmoji) {
                TLRPC.TL_messageEntityCustomEmoji tlCustomEmoji = new TLRPC.TL_messageEntityCustomEmoji();
                tlCustomEmoji.document_id = ((TdApi.TextEntityTypeCustomEmoji)type).customEmojiId;
                tlEntity = tlCustomEmoji;
            }
            // TODO: Add other TextEntityType conversions

            if (tlEntity != null) {
                tlEntity.offset = entity.offset;
                tlEntity.length = entity.length;
                tlEntities.add(tlEntity);
            }
        }
        return tlEntities;
    }

    public static TLRPC.WebPage convertWebPage(TdApi.WebPage webPage) {
        if (webPage == null) return new TLRPC.TL_webPageEmpty(); // Or null based on usage

        if (webPage.type != null && "telegram_story".equals(webPage.type)) {
             // Special handling for stories, might need more context or be handled by MessageObject
        }

        TLRPC.TL_webPage tlWebPage = new TLRPC.TL_webPage();
        tlWebPage.id = webPage.id;
        tlWebPage.url = webPage.url;
        tlWebPage.display_url = webPage.displayUrl;
        tlWebPage.type = webPage.type;
        tlWebPage.site_name = webPage.siteName;
        tlWebPage.title = webPage.title;
        if (webPage.description != null) {
            tlWebPage.description = webPage.description.text;
        }
        tlWebPage.photo = convertPhoto(webPage.photo);
        tlWebPage.embed_url = webPage.embedUrl;
        tlWebPage.embed_type = webPage.embedType;
        tlWebPage.embed_width = webPage.embedWidth;
        tlWebPage.embed_height = webPage.embedHeight;
        tlWebPage.duration = webPage.duration;
        tlWebPage.author = webPage.author;
        tlWebPage.document = convertDocument(webPage.document); // If it's a document preview
        // TODO: tlWebPage.cached_page = convertPageBlocks(webPage.instantViewVersion); // Complex conversion
        tlWebPage.flags = 0; // Needs to be set based on available fields
        if (tlWebPage.type != null) tlWebPage.flags |= 1;
        if (tlWebPage.site_name != null) tlWebPage.flags |= 2;
        if (tlWebPage.title != null) tlWebPage.flags |= 4;
        if (tlWebPage.description != null) tlWebPage.flags |= 8;
        if (tlWebPage.photo != null) tlWebPage.flags |= 16;
        // ... and so on for other flags

        return tlWebPage;
    }
}
