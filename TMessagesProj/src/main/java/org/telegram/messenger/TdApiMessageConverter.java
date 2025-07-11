package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;
import org.drinkless.td.libcore.telegram.TdApi;
import android.util.Pair; // Added for FormattedText conversion

import java.util.ArrayList;
import java.util.List;

// Helper class to convert between TLRPC objects (used by legacy Telegram API code)
// and TdApi objects (used by TDLib).
public class TdApiMessageConverter {

    // Prevent instantiation
    private TdApiMessageConverter() {
    }

    public static long getChatId(TLRPC.Peer peer) {
        if (peer == null) return 0;
        if (peer instanceof TLRPC.TL_peerUser) return peer.user_id;
        if (peer instanceof TLRPC.TL_peerChat) return -peer.chat_id; // TDLib uses negative for basic groups
        if (peer instanceof TLRPC.TL_peerChannel) return -peer.channel_id; // TDLib uses negative for channels/supergroups
        return 0;
    }

    public static long getChatId(long peerId) { // Convenience for when peerId is already in TLRPC style
        if (peerId > 0) return peerId; // User
        return -peerId; // Chat or Channel (TDLib uses positive IDs for these)
    }


    // --- Converters from TdApi to TLRPC ---

    public static TLRPC.User toTLRPC(TdApi.User user) {
        if (user == null) return null;
        TLRPC.TL_user tlUser = new TLRPC.TL_user();
        tlUser.id = user.id;
        tlUser.first_name = user.firstName;
        tlUser.last_name = user.lastName;
        tlUser.username = user.username;
        tlUser.phone = user.phoneNumber;
        tlUser.photo = toTLRPC(user.profilePhoto);
        tlUser.status = toTLRPC(user.status);
        tlUser.bot = user.type instanceof TdApi.UserTypeBot;
        if (user.isVerified) tlUser.verified = true;
        if (user.isPremium) tlUser.premium = true;
        // Add other relevant fields and flags
        return tlUser;
    }

    public static TLRPC.Chat toTLRPC(TdApi.Chat chat) {
        if (chat == null) return null;
        if (chat.type instanceof TdApi.ChatTypeBasicGroup) {
            TdApi.ChatTypeBasicGroup basicGroupType = (TdApi.ChatTypeBasicGroup) chat.type;
            TLRPC.TL_chat tlChat = new TLRPC.TL_chat();
            tlChat.id = basicGroupType.basicGroupId; // TDLib uses positive, TLRPC uses negative
            tlChat.title = chat.title;
            // tlChat.participants_count = 0; // Needs BasicGroupFullInfo
            tlChat.date = chat.lastMessage != null ? chat.lastMessage.date : 0; // Approximate
            tlChat.version = 1;
            // tlChat.photo = toTLRPC(chat.photo);
            // tlChat.left = chat.permissions.canSendMessages; // This is not a direct mapping
            return tlChat;
        } else if (chat.type instanceof TdApi.ChatTypeSupergroup) {
            TdApi.ChatTypeSupergroup supergroupType = (TdApi.ChatTypeSupergroup) chat.type;
            TLRPC.TL_channel tlChannel = new TLRPC.TL_channel();
            tlChannel.id = supergroupType.supergroupId; // TDLib uses positive, TLRPC uses negative
            tlChannel.title = chat.title;
            // tlChannel.username = ""; // Needs SupergroupFullInfo for username
            tlChannel.date = chat.lastMessage != null ? chat.lastMessage.date : 0; // Approximate
            tlChannel.version = 1;
            tlChannel.megagroup = supergroupType.isChannel; // isChannel means it's a broadcast channel
            // tlChannel.photo = toTLRPC(chat.photo);
            // tlChannel.left = chat.permissions.canSendMessages;
            return tlChannel;
        }
        return null; // Or handle SecretChat if needed
    }


    public static TLRPC.ChatPhoto toTLRPC(TdApi.ChatPhotoInfo photoInfo) {
        if (photoInfo == null || photoInfo.small == null) return null;
        TLRPC.TL_chatPhoto chatPhoto = new TLRPC.TL_chatPhoto();
        chatPhoto.photo_small = toTLRPC(photoInfo.small);
        if (photoInfo.big != null) {
            chatPhoto.photo_big = toTLRPC(photoInfo.big);
        }
        chatPhoto.dc_id = 0;
        return chatPhoto;
    }

    public static TLRPC.FileLocation toTLRPC(TdApi.File file) {
        if (file == null || file.remote == null || file.remote.id == null) return null;
        // This is a heuristic. TDLib's remote file ID is a string.
        // TLRPC.FileLocation needs volume_id, local_id, secret.
        // We can't perfectly convert without more context or assumptions.
        // For now, let's try to parse if it looks like "volume_local_secret"
        // or just use parts of it. This is very approximate.
        TLRPC.TL_fileLocationToBeDeprecated tlFileLocation = new TLRPC.TL_fileLocationToBeDeprecated();
        try {
            // Attempt to extract some numeric parts if the remote ID is structured
            // This is highly speculative and likely incorrect for robust conversion.
            String[] parts = file.remote.id.split("_");
            if (parts.length > 0) tlFileLocation.local_id = Integer.parseInt(parts[parts.length-1]);
            if (parts.length > 1) tlFileLocation.volume_id = Long.parseLong(parts[0]);

        } catch (NumberFormatException e) {
            // Could not parse, leave as default
        }
        // tlFileLocation.secret = 0; // No direct mapping
        // tlFileLocation.dc_id = 0; // No direct mapping from TdApi.File alone
        return tlFileLocation;
    }


    public static TLRPC.UserProfilePhoto toTLRPC(TdApi.ProfilePhoto profilePhoto) {
        if (profilePhoto == null) return null;
        TLRPC.TL_userProfilePhoto tlUserProfilePhoto = new TLRPC.TL_userProfilePhoto();
        tlUserProfilePhoto.photo_id = profilePhoto.id;
        tlUserProfilePhoto.photo_small = toTLRPC(profilePhoto.small);
        tlUserProfilePhoto.photo_big = toTLRPC(profilePhoto.big);
        tlUserProfilePhoto.dc_id = 0;
        return tlUserProfilePhoto;
    }

    public static TLRPC.UserStatus toTLRPC(TdApi.UserStatus status) {
        if (status == null) return null;
        if (status instanceof TdApi.UserStatusOnline) {
            TLRPC.TL_userStatusOnline tlStatusOnline = new TLRPC.TL_userStatusOnline();
            tlStatusOnline.expires = ((TdApi.UserStatusOnline) status).expires;
            return tlStatusOnline;
        } else if (status instanceof TdApi.UserStatusOffline) {
            TLRPC.TL_userStatusOffline tlStatusOffline = new TLRPC.TL_userStatusOffline();
            tlStatusOffline.was_online = ((TdApi.UserStatusOffline) status).wasOnline;
            return tlStatusOffline;
        } else if (status instanceof TdApi.UserStatusRecently) {
            return new TLRPC.TL_userStatusRecently();
        } else if (status instanceof TdApi.UserStatusLastWeek) {
            return new TLRPC.TL_userStatusLastWeek();
        } else if (status instanceof TdApi.UserStatusLastMonth) {
            return new TLRPC.TL_userStatusLastMonth();
        }
        return new TLRPC.TL_userStatusEmpty();
    }

    public static TLRPC.MessageMedia toTLRPC(TdApi.MessageContent content) {
        if (content == null) return new TLRPC.TL_messageMediaEmpty();

        switch (content.getConstructor()) {
            case TdApi.MessageText.CONSTRUCTOR:
                return new TLRPC.TL_messageMediaEmpty();
            case TdApi.MessagePhoto.CONSTRUCTOR:
                TdApi.MessagePhoto messagePhoto = (TdApi.MessagePhoto) content;
                TLRPC.TL_messageMediaPhoto mediaPhoto = new TLRPC.TL_messageMediaPhoto();
                mediaPhoto.photo = toTLRPC(messagePhoto.photo);
                return mediaPhoto;
            case TdApi.MessageVideo.CONSTRUCTOR:
                TdApi.MessageVideo messageVideo = (TdApi.MessageVideo) content;
                TLRPC.TL_messageMediaDocument mediaVideo = new TLRPC.TL_messageMediaDocument();
                mediaVideo.document = toTLRPC(messageVideo.video);
                return mediaVideo;
            // ... other content types from previous implementation
        }
        return new TLRPC.TL_messageMediaEmpty();
    }

    public static TLRPC.Photo toTLRPC(TdApi.Photo tdPhoto) {
        if (tdPhoto == null) return null;
        TLRPC.TL_photo photo = new TLRPC.TL_photo();
        photo.id = tdPhoto.id;
        photo.has_stickers = tdPhoto.hasStickers;
        photo.access_hash = 0;
        photo.file_reference = new byte[0];
        photo.date = 0; // TDLib's Photo doesn't have a date, might need to get from Message
        photo.sizes = new ArrayList<>();
        if (tdPhoto.sizes != null) {
            for (TdApi.PhotoSize tdSize : tdPhoto.sizes) {
                photo.sizes.add(toTLRPC(tdSize));
            }
        }
        photo.dc_id = 0;
        return photo;
    }

    public static TLRPC.PhotoSize toTLRPC(TdApi.PhotoSize tdPhotoSize) {
        if (tdPhotoSize == null) return null;
        TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize();
        photoSize.type = tdPhotoSize.type;
        photoSize.w = tdPhotoSize.width;
        photoSize.h = tdPhotoSize.height;
        photoSize.location = toTLRPC(tdPhotoSize.photo);
        photoSize.size = tdPhotoSize.photo.size;
        return photoSize;
    }

    public static TLRPC.Document toTLRPC(TdApi.Video tdVideo) {
        if (tdVideo == null) return null;
        TLRPC.TL_document tlDoc = new TLRPC.TL_document();
        tlDoc.id = tdVideo.video.id;
        tlDoc.access_hash = 0;
        tlDoc.file_reference = tdVideo.video.persistentId != null ? tdVideo.video.persistentId.getBytes() : new byte[0];
        tlDoc.date = 0; // Approx
        tlDoc.mime_type = tdVideo.video.mimeType;
        tlDoc.size = tdVideo.video.size;
        if (tdVideo.thumbnail != null) {
            tlDoc.thumbs.add(toTLRPC(tdVideo.thumbnail));
            tlDoc.flags |= 1;
        }
        // tlDoc.dc_id = Integer.parseInt(tdVideo.video.remote.id); // Risky
        TLRPC.TL_documentAttributeVideo videoAttr = new TLRPC.TL_documentAttributeVideo();
        videoAttr.duration = tdVideo.duration;
        videoAttr.w = tdVideo.width;
        videoAttr.h = tdVideo.height;
        videoAttr.supports_streaming = tdVideo.supportsStreaming;
        tlDoc.attributes.add(videoAttr);
        if (tdVideo.video.fileName != null && !tdVideo.video.fileName.isEmpty()) {
            TLRPC.TL_documentAttributeFilename filenameAttribute = new TLRPC.TL_documentAttributeFilename();
            filenameAttribute.file_name = tdVideo.video.fileName;
            tlDoc.attributes.add(filenameAttribute);
        }
        return tlDoc;
    }
    // ... (other toTLRPC methods for Animation, Audio, VoiceNote, Document, Sticker, Thumbnail as previously defined)

    public static TLRPC.Document toTLRPC(TdApi.Animation tdAnimation) {
        if (tdAnimation != null) {
            TLRPC.TL_document tlDocument = new TLRPC.TL_document();
            tlDocument.id = tdAnimation.animation.id;
            tlDocument.access_hash = 0;
            tlDocument.file_reference = tdAnimation.animation.persistentId != null && !tdAnimation.animation.persistentId.isEmpty() ? tdAnimation.animation.persistentId.getBytes() : new byte[0];
            tlDocument.date = 0;
            tlDocument.mime_type = tdAnimation.animation.mimeType;
            tlDocument.size = tdAnimation.animation.size;
            if (tdAnimation.thumbnail != null) {
                tlDocument.thumbs.add(toTLRPC(tdAnimation.thumbnail));
                tlDocument.flags |=1;
            } else {
                 TLRPC.TL_photoSizeEmpty photoSizeEmpty = new TLRPC.TL_photoSizeEmpty();
                 photoSizeEmpty.type = "m"; // Default type
                 tlDocument.thumbs.add(photoSizeEmpty);
                 tlDocument.flags |=1;
            }
            // tlDocument.dc_id = Integer.parseInt(tdAnimation.animation.remote.id); // Risky

            TLRPC.TL_documentAttributeAnimated attributeAnimated = new TLRPC.TL_documentAttributeAnimated();
            tlDocument.attributes.add(attributeAnimated);

            TLRPC.TL_documentAttributeVideo attributeVideo = new TLRPC.TL_documentAttributeVideo();
            attributeVideo.duration = tdAnimation.duration;
            attributeVideo.w = tdAnimation.width;
            attributeVideo.h = tdAnimation.height;
            tlDocument.attributes.add(attributeVideo);
            return tlDocument;
        }
        return null;
    }

    public static TLRPC.Document toTLRPC(TdApi.Audio tdAudio) {
        if (tdAudio != null) {
            TLRPC.TL_document tlDocument = new TLRPC.TL_document();
            tlDocument.id = tdAudio.audio.id;
            tlDocument.access_hash = 0;
            tlDocument.file_reference = tdAudio.audio.persistentId != null && !tdAudio.audio.persistentId.isEmpty() ? tdAudio.audio.persistentId.getBytes() : new byte[0];
            tlDocument.date = 0;
            tlDocument.mime_type = tdAudio.audio.mimeType;
            tlDocument.size = tdAudio.audio.size;
             if (tdAudio.albumCoverThumbnail != null) {
                tlDocument.thumbs.add(toTLRPC(tdAudio.albumCoverThumbnail));
                tlDocument.flags |=1;
            } else {
                 TLRPC.TL_photoSizeEmpty photoSizeEmpty = new TLRPC.TL_photoSizeEmpty();
                 photoSizeEmpty.type = "m";
                 tlDocument.thumbs.add(photoSizeEmpty);
                 tlDocument.flags |=1;
            }
            // tlDocument.dc_id = Integer.parseInt(tdAudio.audio.remote.id); // Risky

            TLRPC.TL_documentAttributeAudio attributeAudio = new TLRPC.TL_documentAttributeAudio();
            attributeAudio.duration = tdAudio.duration;
            attributeAudio.title = tdAudio.title;
            attributeAudio.performer = tdAudio.performer;
            tlDocument.attributes.add(attributeAudio);
            return tlDocument;
        }
        return null;
    }

    public static TLRPC.Document toTLRPC(TdApi.VoiceNote tdVoiceNote) {
        if (tdVoiceNote != null) {
            TLRPC.TL_document tlDocument = new TLRPC.TL_document();
            tlDocument.id = tdVoiceNote.voiceNote.id;
            tlDocument.access_hash = 0;
            tlDocument.file_reference = tdVoiceNote.voiceNote.persistentId != null && !tdVoiceNote.voiceNote.persistentId.isEmpty() ? tdVoiceNote.voiceNote.persistentId.getBytes() : new byte[0];
            tlDocument.date = 0;
            tlDocument.mime_type = tdVoiceNote.voiceNote.mimeType;
            tlDocument.size = tdVoiceNote.voiceNote.size;
            TLRPC.TL_photoSizeEmpty photoSizeEmpty = new TLRPC.TL_photoSizeEmpty(); // Voice notes typically don't have thumbs
            photoSizeEmpty.type = "m";
            tlDocument.thumbs.add(photoSizeEmpty);
            tlDocument.flags |=1;
            // tlDocument.dc_id = Integer.parseInt(tdVoiceNote.voiceNote.remote.id); // Risky

            TLRPC.TL_documentAttributeAudio attributeAudio = new TLRPC.TL_documentAttributeAudio();
            attributeAudio.duration = tdVoiceNote.duration;
            attributeAudio.voice = true;
            if (tdVoiceNote.waveform != null) {
                attributeAudio.waveform = tdVoiceNote.waveform;
            }
            tlDocument.attributes.add(attributeAudio);
            return tlDocument;
        }
        return null;
    }

    public static TLRPC.Document toTLRPC(TdApi.Document tdDocumentMessage) {
         if (tdDocumentMessage != null) {
            TLRPC.TL_document tlDocument = new TLRPC.TL_document();
            tlDocument.id = tdDocumentMessage.document.id;
            tlDocument.access_hash = 0;
            tlDocument.file_reference = tdDocumentMessage.document.persistentId != null && !tdDocumentMessage.document.persistentId.isEmpty() ? tdDocumentMessage.document.persistentId.getBytes() : new byte[0];
            tlDocument.date = 0;
            tlDocument.mime_type = tdDocumentMessage.document.mimeType;
            tlDocument.size = tdDocumentMessage.document.size;
             if (tdDocumentMessage.thumbnail != null) {
                tlDocument.thumbs.add(toTLRPC(tdDocumentMessage.thumbnail));
                tlDocument.flags |=1;
            } else {
                 TLRPC.TL_photoSizeEmpty photoSizeEmpty = new TLRPC.TL_photoSizeEmpty();
                 photoSizeEmpty.type = "m";
                 tlDocument.thumbs.add(photoSizeEmpty);
                 tlDocument.flags |=1;
            }
            // tlDocument.dc_id = Integer.parseInt(tdDocumentMessage.document.remote.id); // Risky

            if (tdDocumentMessage.document.fileName != null && !tdDocumentMessage.document.fileName.isEmpty()) {
                TLRPC.TL_documentAttributeFilename filenameAttribute = new TLRPC.TL_documentAttributeFilename();
                filenameAttribute.file_name = tdDocumentMessage.document.fileName;
                tlDocument.attributes.add(filenameAttribute);
            }
            return tlDocument;
        }
        return null;
    }

    public static TLRPC.Document toTLRPC(TdApi.Sticker tdSticker) {
        if (tdSticker != null) {
            TLRPC.TL_document tlDocument = new TLRPC.TL_document();
            tlDocument.id = tdSticker.sticker.id;
            tlDocument.access_hash = 0;
            tlDocument.file_reference = tdSticker.sticker.persistentId != null && !tdSticker.sticker.persistentId.isEmpty() ? tdSticker.sticker.persistentId.getBytes() : new byte[0];
            tlDocument.date = 0;
            tlDocument.mime_type = tdSticker.sticker.mimeType;
            tlDocument.size = tdSticker.sticker.size;
            if (tdSticker.thumbnail != null) {
                tlDocument.thumbs.add(toTLRPC(tdSticker.thumbnail));
                tlDocument.flags |=1;
            } else {
                 TLRPC.TL_photoSizeEmpty photoSizeEmpty = new TLRPC.TL_photoSizeEmpty();
                 photoSizeEmpty.type = "m";
                 tlDocument.thumbs.add(photoSizeEmpty);
                 tlDocument.flags |=1;
            }
            // tlDocument.dc_id = Integer.parseInt(tdSticker.sticker.remote.id); // Risky

            TLRPC.TL_documentAttributeSticker stickerAttribute = new TLRPC.TL_documentAttributeSticker();
            stickerAttribute.alt = tdSticker.emoji;
            stickerAttribute.stickerset = new TLRPC.TL_inputStickerSetEmpty(); // Placeholder
            tlDocument.attributes.add(stickerAttribute);

            if (tdSticker.type instanceof TdApi.StickerTypeAnimated || tdSticker.type instanceof TdApi.StickerTypeVideo) {
                 TLRPC.TL_documentAttributeImageSize imageSizeAttribute = new TLRPC.TL_documentAttributeImageSize();
                 imageSizeAttribute.w = tdSticker.width;
                 imageSizeAttribute.h = tdSticker.height;
                 tlDocument.attributes.add(imageSizeAttribute);
                 if (tdSticker.type instanceof TdApi.StickerTypeAnimated) {
                    tlDocument.attributes.add(new TLRPC.TL_documentAttributeAnimated());
                 }
            }
            return tlDocument;
        }
        return null;
    }

    public static TLRPC.PhotoSize toTLRPC(TdApi.Thumbnail tdThumbnail) {
        if (tdThumbnail == null) return null;
        TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize();
        photoSize.type = "m"; // Placeholder, TdApi.ThumbnailFormat needs mapping
        switch(tdThumbnail.format.getConstructor()) {
            case TdApi.ThumbnailFormatJpeg.CONSTRUCTOR: photoSize.type = "j"; break;
            case TdApi.ThumbnailFormatPng.CONSTRUCTOR: photoSize.type = "p"; break;
            case TdApi.ThumbnailFormatWebp.CONSTRUCTOR: photoSize.type = "w"; break;
            // Add more if needed
        }
        photoSize.w = tdThumbnail.width;
        photoSize.h = tdThumbnail.height;
        photoSize.location = toTLRPC(tdThumbnail.file);
        photoSize.size = tdThumbnail.file.size;
        return photoSize;
    }


    public static ArrayList<TLRPC.MessageEntity> toTLRPC(TdApi.FormattedText formattedText) {
        if (formattedText == null || formattedText.entities == null) return new ArrayList<>();
        ArrayList<TLRPC.MessageEntity> tlEntities = new ArrayList<>();
        for (TdApi.TextEntity tdEntity : formattedText.entities) {
            TLRPC.MessageEntity tlEntity = toTLRPC(tdEntity, formattedText.text);
            if (tlEntity != null) {
                tlEntities.add(tlEntity);
            }
        }
        return tlEntities;
    }

    public static TLRPC.MessageEntity toTLRPC(TdApi.TextEntity tdEntity, String messageText) {
        if (tdEntity == null) return null;
        TLRPC.MessageEntity tlEntity = null;
        int constructorId = tdEntity.type.getConstructor();

        if (constructorId == TdApi.TextEntityTypeMention.CONSTRUCTOR) {
            tlEntity = new TLRPC.TL_messageEntityMention();
        } else if (constructorId == TdApi.TextEntityTypeHashtag.CONSTRUCTOR) {
            tlEntity = new TLRPC.TL_messageEntityHashtag();
        } else if (constructorId == TdApi.TextEntityTypeBotCommand.CONSTRUCTOR) {
            tlEntity = new TLRPC.TL_messageEntityBotCommand();
        } else if (constructorId == TdApi.TextEntityTypeUrl.CONSTRUCTOR) {
            tlEntity = new TLRPC.TL_messageEntityUrl();
        } else if (constructorId == TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR) {
            tlEntity = new TLRPC.TL_messageEntityEmail();
        } else if (constructorId == TdApi.TextEntityTypeBold.CONSTRUCTOR) {
            tlEntity = new TLRPC.TL_messageEntityBold();
        } else if (constructorId == TdApi.TextEntityTypeItalic.CONSTRUCTOR) {
            tlEntity = new TLRPC.TL_messageEntityItalic();
        } else if (constructorId == TdApi.TextEntityTypeUnderline.CONSTRUCTOR) {
            tlEntity = new TLRPC.TL_messageEntityUnderline();
        } else if (constructorId == TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR) {
            tlEntity = new TLRPC.TL_messageEntityStrike();
        } else if (constructorId == TdApi.TextEntityTypeCode.CONSTRUCTOR) {
            tlEntity = new TLRPC.TL_messageEntityCode();
        } else if (constructorId == TdApi.TextEntityTypePre.CONSTRUCTOR) {
            tlEntity = new TLRPC.TL_messageEntityPre();
            ((TLRPC.TL_messageEntityPre) tlEntity).language = ((TdApi.TextEntityTypePre) tdEntity.type).language;
        } else if (constructorId == TdApi.TextEntityTypePreCode.CONSTRUCTOR) {
             tlEntity = new TLRPC.TL_messageEntityPre();
            ((TLRPC.TL_messageEntityPre) tlEntity).language = ((TdApi.TextEntityTypePreCode) tdEntity.type).language;
        } else if (constructorId == TdApi.TextEntityTypeTextUrl.CONSTRUCTOR) {
            tlEntity = new TLRPC.TL_messageEntityTextUrl();
            ((TLRPC.TL_messageEntityTextUrl) tlEntity).url = ((TdApi.TextEntityTypeTextUrl) tdEntity.type).url;
        } else if (constructorId == TdApi.TextEntityTypeMentionName.CONSTRUCTOR) {
            tlEntity = new TLRPC.TL_messageEntityMentionName();
            ((TLRPC.TL_messageEntityMentionName) tlEntity).user_id = ((TdApi.TextEntityTypeMentionName) tdEntity.type).userId;
        } else if (constructorId == TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR) {
            tlEntity = new TLRPC.TL_messageEntityCustomEmoji();
            ((TLRPC.TL_messageEntityCustomEmoji) tlEntity).document_id = ((TdApi.TextEntityTypeCustomEmoji) tdEntity.type).customEmojiId;
        } else if (constructorId == TdApi.TextEntityTypeSpoiler.CONSTRUCTOR) {
            tlEntity = new TLRPC.TL_messageEntityBlockquote(); // Or new TLRPC.TL_messageEntitySpoiler(); if available
        }

        if (tlEntity != null) {
            tlEntity.offset = tdEntity.offset;
            tlEntity.length = tdEntity.length;
        }
        return tlEntity;
    }

    public static TLRPC.WebPage toTLRPC(TdApi.WebPage tdWebPage) {
        if (tdWebPage == null) return null;
        TLRPC.TL_webPage tlWebPage = new TLRPC.TL_webPage();
        tlWebPage.id = tdWebPage.id;
        tlWebPage.url = tdWebPage.url;
        tlWebPage.display_url = tdWebPage.displayUrl;
        tlWebPage.type = tdWebPage.type;
        tlWebPage.site_name = tdWebPage.siteName;
        tlWebPage.title = tdWebPage.title;
        if (tdWebPage.description != null) {
            tlWebPage.description = tdWebPage.description.text;
        }
        tlWebPage.photo = toTLRPC(tdWebPage.photo);
        tlWebPage.embed_url = tdWebPage.embedUrl;
        tlWebPage.embed_type = tdWebPage.embedType;
        tlWebPage.embed_width = tdWebPage.embedWidth;
        tlWebPage.embed_height = tdWebPage.embedHeight;
        tlWebPage.duration = tdWebPage.duration;
        tlWebPage.author = tdWebPage.author;
        tlWebPage.document = toTLRPC(tdWebPage.document);
        tlWebPage.flags = 0;
        if (tlWebPage.type != null) tlWebPage.flags |= 1;
        // ... set other flags based on available fields
        return tlWebPage;
    }

    public static TLRPC.BotInfo toTLRPC(TdApi.BotInfo tdBotInfo) {
        if (tdBotInfo == null) return null;
        TLRPC.TL_botInfo tlBotInfo = new TLRPC.TL_botInfo();
        tlBotInfo.description = tdBotInfo.description;
        if (tdBotInfo.commands != null) {
            for (TdApi.BotCommand tdCommand : tdBotInfo.commands) {
                TLRPC.TL_botCommand tlCommand = new TLRPC.TL_botCommand();
                tlCommand.command = tdCommand.command;
                tlCommand.description = tdCommand.description;
                tlBotInfo.commands.add(tlCommand);
            }
        }
        // tlBotInfo.menu_button = toTLRPC(tdBotInfo.menuButton); // Requires TdApi.BotMenuButton to TLRPC.BotMenuButton
        return tlBotInfo;
    }

    // --- Converters from TLRPC to TdApi ---

    public static TdApi.TextEntity fromTLRPC(TLRPC.MessageEntity tlEntity) {
        if (tlEntity == null) return null;

        TdApi.TextEntityType entityType = null;
        if (tlEntity instanceof TLRPC.TL_messageEntityBold) {
            entityType = new TdApi.TextEntityTypeBold();
        } else if (tlEntity instanceof TLRPC.TL_messageEntityItalic) {
            entityType = new TdApi.TextEntityTypeItalic();
        } else if (tlEntity instanceof TLRPC.TL_messageEntityCode) {
            entityType = new TdApi.TextEntityTypeCode();
        } else if (tlEntity instanceof TLRPC.TL_messageEntityPre) {
            entityType = new TdApi.TextEntityTypePre(((TLRPC.TL_messageEntityPre) tlEntity).language);
        } else if (tlEntity instanceof TLRPC.TL_messageEntityTextUrl) {
            entityType = new TdApi.TextEntityTypeTextUrl(((TLRPC.TL_messageEntityTextUrl) tlEntity).url);
        } else if (tlEntity instanceof TLRPC.TL_messageEntityMentionName) {
            entityType = new TdApi.TextEntityTypeMentionName(((TLRPC.TL_messageEntityMentionName) tlEntity).user_id);
        } else if (tlEntity instanceof TLRPC.TL_inputMessageEntityMentionName) {
             entityType = new TdApi.TextEntityTypeMentionName(((TLRPC.TL_inputMessageEntityMentionName) tlEntity).user_id.user_id);
        } else if (tlEntity instanceof TLRPC.TL_messageEntityCustomEmoji) {
            entityType = new TdApi.TextEntityTypeCustomEmoji(((TLRPC.TL_messageEntityCustomEmoji) tlEntity).document_id);
        } else if (tlEntity instanceof TLRPC.TL_messageEntitySpoiler || tlEntity instanceof TLRPC.TL_messageEntityBlockquote) {
            entityType = new TdApi.TextEntityTypeSpoiler();
        } else if (tlEntity instanceof TLRPC.TL_messageEntityUnderline) {
            entityType = new TdApi.TextEntityTypeUnderline();
        } else if (tlEntity instanceof TLRPC.TL_messageEntityStrike) {
            entityType = new TdApi.TextEntityTypeStrikethrough();
        } else if (tlEntity instanceof TLRPC.TL_messageEntityUrl) {
             entityType = new TdApi.TextEntityTypeUrl();
        } else if (tlEntity instanceof TLRPC.TL_messageEntityEmail) {
            entityType = new TdApi.TextEntityTypeEmailAddress();
        } else if (tlEntity instanceof TLRPC.TL_messageEntityHashtag) {
            entityType = new TdApi.TextEntityTypeHashtag();
        } else if (tlEntity instanceof TLRPC.TL_messageEntityMention) {
            entityType = new TdApi.TextEntityTypeMention();
        } else if (tlEntity instanceof TLRPC.TL_messageEntityBotCommand) {
            entityType = new TdApi.TextEntityTypeBotCommand();
        }

        if (entityType != null) {
            return new TdApi.TextEntity(tlEntity.offset, tlEntity.length, entityType);
        }
        return null;
    }

    public static ArrayList<TdApi.TextEntity> fromTLRPC(ArrayList<TLRPC.MessageEntity> tlEntities) {
        if (tlEntities == null) return new ArrayList<>();
        ArrayList<TdApi.TextEntity> tdEntities = new ArrayList<>();
        for (TLRPC.MessageEntity tlEntity : tlEntities) {
            TdApi.TextEntity tdEntity = fromTLRPC(tlEntity);
            if (tdEntity != null) {
                tdEntities.add(tdEntity);
            }
        }
        return tdEntities;
    }

    public static TdApi.ReplyMarkup fromTLRPC(TLRPC.ReplyMarkup tlMarkup) {
        if (tlMarkup == null) return null;

        if (tlMarkup instanceof TLRPC.TL_replyKeyboardMarkup) {
            TLRPC.TL_replyKeyboardMarkup tlKeyboardMarkup = (TLRPC.TL_replyKeyboardMarkup) tlMarkup;
            ArrayList<TdApi.KeyboardButton[]> rows = new ArrayList<>();
            for (TLRPC.KeyboardButtonRow tlRow : tlKeyboardMarkup.rows) {
                ArrayList<TdApi.KeyboardButton> rowButtons = new ArrayList<>();
                for (TLRPC.KeyboardButton tlButton : tlRow.buttons) {
                    TdApi.KeyboardButton kb = fromTLRPC(tlButton);
                    if (kb != null) rowButtons.add(kb);
                }
                if (!rowButtons.isEmpty()) {
                    rows.add(rowButtons.toArray(new TdApi.KeyboardButton[0]));
                }
            }
            return new TdApi.ReplyMarkupShowKeyboard(rows.toArray(new TdApi.KeyboardButton[0][0]), tlKeyboardMarkup.resize, tlKeyboardMarkup.single_use, tlKeyboardMarkup.selective, tlKeyboardMarkup.is_persistent, tlKeyboardMarkup.placeholder);
        } else if (tlMarkup instanceof TLRPC.TL_replyInlineMarkup) {
            TLRPC.TL_replyInlineMarkup tlInlineMarkup = (TLRPC.TL_replyInlineMarkup) tlMarkup;
            ArrayList<TdApi.InlineKeyboardButton[]> rows = new ArrayList<>();
            for (TLRPC.KeyboardButtonRow tlRow : tlInlineMarkup.rows) {
                ArrayList<TdApi.InlineKeyboardButton> rowButtons = new ArrayList<>();
                for (TLRPC.KeyboardButton tlButton : tlRow.buttons) {
                    if (tlButton instanceof TLRPC.TL_inlineKeyboardButton) {
                        TdApi.InlineKeyboardButton ikb = fromTLRPC((TLRPC.TL_inlineKeyboardButton)tlButton);
                        if (ikb != null) rowButtons.add(ikb);
                    }
                }
                 if (!rowButtons.isEmpty()) {
                    rows.add(rowButtons.toArray(new TdApi.InlineKeyboardButton[0]));
                }
            }
            return new TdApi.ReplyMarkupInlineKeyboard(rows.toArray(new TdApi.InlineKeyboardButton[0][0]));
        } else if (tlMarkup instanceof TLRPC.TL_replyKeyboardHide) {
            return new TdApi.ReplyMarkupRemoveKeyboard(((TLRPC.TL_replyKeyboardHide) tlMarkup).selective);
        } else if (tlMarkup instanceof TLRPC.TL_replyKeyboardForceReply) {
            TLRPC.TL_replyKeyboardForceReply tlForceReply = (TLRPC.TL_replyKeyboardForceReply) tlMarkup;
            return new TdApi.ReplyMarkupForceReply(tlForceReply.single_use, tlForceReply.selective, tlForceReply.placeholder);
        }
        return null;
    }

    public static TdApi.KeyboardButton fromTLRPC(TLRPC.KeyboardButton tlButton) {
        if (tlButton == null || tlButton.text == null) return null; // Text is mandatory
        TdApi.KeyboardButtonType buttonType = null;

        if (tlButton instanceof TLRPC.TL_keyboardButtonRequestPhone) {
            buttonType = new TdApi.KeyboardButtonTypeRequestPhoneNumber();
        } else if (tlButton instanceof TLRPC.TL_keyboardButtonRequestGeoLocation) {
            buttonType = new TdApi.KeyboardButtonTypeRequestLocation();
        } else if (tlButton instanceof TLRPC.TL_keyboardButtonRequestPoll) {
            TLRPC.TL_keyboardButtonRequestPoll tlPollButton = (TLRPC.TL_keyboardButtonRequestPoll) tlButton;
            TdApi.PollType pollType;
            if (tlPollButton.quiz != null && tlPollButton.quiz) {
                 pollType = new TdApi.PollTypeQuiz(new TdApi.FormattedText("", null), new ArrayList<>()); // Explanation and entities can be null/empty
            } else {
                 pollType = new TdApi.PollTypeRegular(false); // Assuming regular poll, not multiple choice by default
            }
            buttonType = new TdApi.KeyboardButtonTypeRequestPoll(pollType);
        } else if (tlButton instanceof TLRPC.TL_keyboardButtonWebView) {
            buttonType = new TdApi.KeyboardButtonTypeWebView(((TLRPC.TL_keyboardButtonWebView) tlButton).url);
        } else if (tlButton instanceof TLRPC.TL_keyboardButtonRequestPeer) {
            TLRPC.TL_keyboardButtonRequestPeer tlRequestPeer = (TLRPC.TL_keyboardButtonRequestPeer) tlButton;
            TdApi.RequestPeerTypeUser userType = new TdApi.RequestPeerTypeUser(tlRequestPeer.peer_type.user_bot, tlRequestPeer.peer_type.user_premium);
            // Simplified: only converting RequestPeerTypeUser for now. Others would need more logic.
            buttonType = new TdApi.KeyboardButtonTypeRequestPeer(tlRequestPeer.button_id, userType, false);
        } else { // Default to text button
             buttonType = new TdApi.KeyboardButtonTypeText(tlButton.text);
        }
        return new TdApi.KeyboardButton(tlButton.text, buttonType);
    }

     public static TdApi.InlineKeyboardButton fromTLRPC(TLRPC.TL_inlineKeyboardButton tlButton) {
        if (tlButton == null || tlButton.text == null) return null;
        TdApi.InlineKeyboardButtonType buttonType = null;

        if (tlButton.url != null) {
            buttonType = new TdApi.InlineKeyboardButtonTypeUrl(tlButton.url);
        } else if (tlButton.callback_data != null) {
            buttonType = new TdApi.InlineKeyboardButtonTypeCallback(tlButton.callback_data);
        } else if (tlButton.switch_inline_query != null) {
            TdApi.SwitchInlineQueryChosenChatType chatType = null; // More complex conversion if peer_types are used
            buttonType = new TdApi.InlineKeyboardButtonTypeSwitchInline(tlButton.switch_inline_query, chatType);
        } else if (tlButton.user_id != 0) {
            buttonType = new TdApi.InlineKeyboardButtonTypeUserProfiler(tlButton.user_id);
        } else if (tlButton.buy) { // This is a flag on TL_inlineKeyboardButton, not a separate type in TLRPC like others
             buttonType = new TdApi.InlineKeyboardButtonTypeBuy();
        } else if (tlButton.login_url != null) { // login_url is a TL_loginUrl object
            buttonType = new TdApi.InlineKeyboardButtonTypeLoginUrl(tlButton.login_url.url, tlButton.login_url.id, tlButton.login_url.forward_text);
        }  else if (tlButton.web_view_url != null) { // This is a custom field added to TL_inlineKeyboardButton in some forks
             buttonType = new TdApi.InlineKeyboardButtonTypeWebView(tlButton.web_view_url);
        }
        // Note: TdApi has more InlineKeyboardButtonTypes like SwitchInlineQueryCurrentChat, CallbackGame, etc.
        // These would need to be identified from TLRPC.TL_inlineKeyboardButton fields or specific classes if they exist.

        if (buttonType != null) {
            return new TdApi.InlineKeyboardButton(tlButton.text, buttonType);
        }
        return null;
    }


    public static TdApi.LinkPreviewOptions fromTLRPC(TLRPC.WebPage webPage, boolean searchLinks, boolean forceSmallMedia, boolean forceLargeMedia, boolean showAboveText) {
        TdApi.LinkPreviewOptions options = new TdApi.LinkPreviewOptions();
        options.isDisabled = !searchLinks;
        if (searchLinks && webPage != null) { // Only set URL if searchLinks is true and webpage is available
            options.url = webPage.url;
        }
        // If webPage is null AND searchLinks is true, TDLib will attempt to generate a preview for the first link.
        // If options.url is set, TDLib uses that specific URL.
        options.forceSmallMedia = forceSmallMedia;
        options.forceLargeMedia = forceLargeMedia;
        options.showAboveText = showAboveText;
        return options;
    }

    public static Pair<String, ArrayList<TLRPC.MessageEntity>> toTLRPC(TdApi.FormattedText formattedText) {
        if (formattedText == null) {
            return new Pair<>("", new ArrayList<>());
        }
        String text = formattedText.text;
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        if (formattedText.entities != null) {
            for (TdApi.TextEntity tdEntity : formattedText.entities) {
                TLRPC.MessageEntity tlEntity = toTLRPC(tdEntity, text);
                if (tlEntity != null) {
                    entities.add(tlEntity);
                }
            }
        }
        return new Pair<>(text, entities);
    }

    public static TLRPC.MessageReplyHeader toTLRPC(TdApi.MessageReplyTo replyTo) {
        if (replyTo == null) {
            return null;
        }
        TLRPC.TL_messageReplyHeader replyHeader = new TLRPC.TL_messageReplyHeader();
        replyHeader.flags = 0; // Will be set based on content

        if (replyTo instanceof TdApi.MessageReplyToMessage) {
            TdApi.MessageReplyToMessage replyToMessage = (TdApi.MessageReplyToMessage) replyTo;
            replyHeader.reply_to_msg_id = (int) replyToMessage.messageId; // TLRPC uses int
            replyHeader.flags |= 16; // Indicates reply_to_msg_id is set

            // TODO: Handle replyToMessage.chatId for cross-chat replies if needed by setting reply_to_peer_id and flag 1
            // TODO: Handle replyToMessage.quote and map to replyHeader.quote_text, quote_entities, quote_offset with flags 64, 128, 1024

        } else if (replyTo instanceof TdApi.MessageReplyToStory) {
            // TLRPC.MessageReplyHeader doesn't have direct fields for story replies.
            // This might need a custom representation or be handled differently based on how the app uses it.
            // For now, we can set reply_to_msg_id if a story can be treated as a message ID in some context.
            // Or, this might indicate the message itself should be of a story reply type.
            // This part needs clarification based on usage.
            // As a placeholder:
            // replyHeader.reply_to_msg_id = (int) ((TdApi.MessageReplyToStory) replyTo).storyId; // Example
            // replyHeader.flags |= 16;
            // Consider adding a custom field or a different TLObject if story replies are distinct.
        }
        // TODO: Handle reply_to_top_id for forum topic replies
        // if (replyTo.messageThreadId != 0) {
        //    replyHeader.reply_to_top_id = (int)replyTo.messageThreadId;
        //    replyHeader.flags |= 2;
        // }

        return replyHeader;
    }

     public static TLRPC.ReplyMarkup toTLRPC(TdApi.ReplyMarkup tdMarkup) {
        if (tdMarkup == null) {
            return null;
        }

        if (tdMarkup instanceof TdApi.ReplyMarkupShowKeyboard) {
            TdApi.ReplyMarkupShowKeyboard tdShowKeyboard = (TdApi.ReplyMarkupShowKeyboard) tdMarkup;
            TLRPC.TL_replyKeyboardMarkup tlKeyboardMarkup = new TLRPC.TL_replyKeyboardMarkup();
            tlKeyboardMarkup.resize = tdShowKeyboard.isResizeKeyboard;
            tlKeyboardMarkup.single_use = tdShowKeyboard.isOneTime;
            tlKeyboardMarkup.selective = tdShowKeyboard.isPersonal; // TDLib 'isPersonal' seems to map to 'selective'
            tlKeyboardMarkup.placeholder = tdShowKeyboard.inputFieldPlaceholder;
            tlKeyboardMarkup.is_persistent = tdShowKeyboard.isPersistent;


            for (TdApi.KeyboardButton[] tdRow : tdShowKeyboard.rows) {
                TLRPC.TL_keyboardButtonRow tlRow = new TLRPC.TL_keyboardButtonRow();
                for (TdApi.KeyboardButton tdButton : tdRow) {
                    TLRPC.KeyboardButton tlBtn = toTLRPC(tdButton);
                    if (tlBtn != null) tlRow.buttons.add(tlBtn);
                }
                if (!tlRow.buttons.isEmpty()) {
                    tlKeyboardMarkup.rows.add(tlRow);
                }
            }
            return tlKeyboardMarkup;

        } else if (tdMarkup instanceof TdApi.ReplyMarkupInlineKeyboard) {
            TdApi.ReplyMarkupInlineKeyboard tdInlineKeyboard = (TdApi.ReplyMarkupInlineKeyboard) tdMarkup;
            TLRPC.TL_replyInlineMarkup tlInlineMarkup = new TLRPC.TL_replyInlineMarkup();
            for (TdApi.InlineKeyboardButton[] tdRow : tdInlineKeyboard.rows) {
                TLRPC.TL_keyboardButtonRow tlRow = new TLRPC.TL_keyboardButtonRow();
                for (TdApi.InlineKeyboardButton tdButton : tdRow) {
                    TLRPC.TL_inlineKeyboardButton tlBtn = toTLRPC(tdButton);
                     if (tlBtn != null) tlRow.buttons.add(tlBtn);
                }
                 if (!tlRow.buttons.isEmpty()) {
                    tlInlineMarkup.rows.add(tlRow);
                }
            }
            return tlInlineMarkup;

        } else if (tdMarkup instanceof TdApi.ReplyMarkupRemoveKeyboard) {
            TLRPC.TL_replyKeyboardHide tlHide = new TLRPC.TL_replyKeyboardHide();
            tlHide.selective = ((TdApi.ReplyMarkupRemoveKeyboard) tdMarkup).isPersonal;
            return tlHide;

        } else if (tdMarkup instanceof TdApi.ReplyMarkupForceReply) {
            TdApi.ReplyMarkupForceReply tdForceReply = (TdApi.ReplyMarkupForceReply) tdMarkup;
            TLRPC.TL_replyKeyboardForceReply tlForce = new TLRPC.TL_replyKeyboardForceReply();
            tlForce.single_use = tdForceReply.isOneTime;
            tlForce.selective = tdForceReply.isPersonal;
            tlForce.placeholder = tdForceReply.inputFieldPlaceholder;
            return tlForce;
        }
        return null;
    }

    public static TLRPC.KeyboardButton toTLRPC(TdApi.KeyboardButton tdButton) {
        if (tdButton == null) return null;
        TLRPC.KeyboardButton tlBtn = null;

        if (tdButton.type instanceof TdApi.KeyboardButtonTypeText) {
            tlBtn = new TLRPC.TL_keyboardButton();
            tlBtn.text = tdButton.text;
        } else if (tdButton.type instanceof TdApi.KeyboardButtonTypeRequestPhoneNumber) {
            tlBtn = new TLRPC.TL_keyboardButtonRequestPhone();
            tlBtn.text = tdButton.text;
        } else if (tdButton.type instanceof TdApi.KeyboardButtonTypeRequestLocation) {
            tlBtn = new TLRPC.TL_keyboardButtonRequestGeoLocation();
            tlBtn.text = tdButton.text;
        } else if (tdButton.type instanceof TdApi.KeyboardButtonTypeRequestPoll) {
            TLRPC.TL_keyboardButtonRequestPoll tlPollBtn = new TLRPC.TL_keyboardButtonRequestPoll();
            tlPollBtn.text = tdButton.text;
            TdApi.PollType pollType = ((TdApi.KeyboardButtonTypeRequestPoll) tdButton.type).type;
            if (pollType instanceof TdApi.PollTypeQuiz) {
                tlPollBtn.quiz = true;
                tlPollBtn.flags |= 1;
            }
            // TdApi.PollTypeRegular has 'allowMultipleAnswers' which doesn't directly map to TLRPC.TL_keyboardButtonRequestPoll
            tlBtn = tlPollBtn;
        } else if (tdButton.type instanceof TdApi.KeyboardButtonTypeWebView) {
            TLRPC.TL_keyboardButtonWebView tlWebViewBtn = new TLRPC.TL_keyboardButtonWebView();
            tlWebViewBtn.text = tdButton.text;
            tlWebViewBtn.url = ((TdApi.KeyboardButtonTypeWebView) tdButton.type).url;
            tlBtn = tlWebViewBtn;
        }
        // TODO: Add TdApi.KeyboardButtonTypeRequestPeer if needed

        return tlBtn;
    }

    public static TLRPC.TL_inlineKeyboardButton toTLRPC(TdApi.InlineKeyboardButton tdButton) {
        if (tdButton == null) return null;
        TLRPC.TL_inlineKeyboardButton tlInlineBtn = new TLRPC.TL_inlineKeyboardButton();
        tlInlineBtn.text = tdButton.text;

        if (tdButton.type instanceof TdApi.InlineKeyboardButtonTypeUrl) {
            tlInlineBtn.url = ((TdApi.InlineKeyboardButtonTypeUrl) tdButton.type).url;
        } else if (tdButton.type instanceof TdApi.InlineKeyboardButtonTypeCallback) {
            tlInlineBtn.callback_data = ((TdApi.InlineKeyboardButtonTypeCallback) tdButton.type).data;
        } else if (tdButton.type instanceof TdApi.InlineKeyboardButtonTypeSwitchInline) {
            TdApi.InlineKeyboardButtonTypeSwitchInline tdSwitch = (TdApi.InlineKeyboardButtonTypeSwitchInline) tdButton.type;
            tlInlineBtn.switch_inline_query = tdSwitch.query;
            // tlInlineBtn.switch_inline_query_current_chat = tdSwitch.switchInlineQueryChosenChat.allowCurrentUser; // Needs mapping for chosen chat types
        } else if (tdButton.type instanceof TdApi.InlineKeyboardButtonTypeBuy) {
            // TLRPC.TL_inlineKeyboardButtonBuy is a distinct class, not just a type field.
            // This conversion might need adjustment if specific TL_inlineKeyboardButtonBuy fields are required.
            // For now, setting the 'buy' flag if it existed in TLRPC.TL_inlineKeyboardButton.
            // Since TLRPC.TL_inlineKeyboardButton doesn't have a 'buy' flag directly,
            // this implies the original TLRPC object was TLRPC.TL_inlineKeyboardButtonBuy.
            // This toTLRPC method is for general TdApi.InlineKeyboardButton,
            // so a direct "buy" type might be better handled by creating TLRPC.TL_inlineKeyboardButtonBuy directly.
            // For simplicity here, we assume it was a generic inline button that somehow represented "buy".
        } else if (tdButton.type instanceof TdApi.InlineKeyboardButtonTypeLoginUrl) {
            TdApi.InlineKeyboardButtonTypeLoginUrl tdLogin = (TdApi.InlineKeyboardButtonTypeLoginUrl) tdButton.type;
            TLRPC.TL_loginUrl tlLoginUrl = new TLRPC.TL_loginUrl();
            tlLoginUrl.url = tdLogin.url;
            tlLoginUrl.id = tdLogin.id;
            tlLoginUrl.forward_text = tdLogin.forwardText;
            tlInlineBtn.login_url = tlLoginUrl;
        } else if (tdButton.type instanceof TdApi.InlineKeyboardButtonTypeWebView) {
            // TLRPC.TL_inlineKeyboardButton doesn't have a direct web_view_url.
            // This might indicate a custom field in the original TLRPC or a newer type not fully mapped.
            // tlInlineBtn.web_view_url = ((TdApi.InlineKeyboardButtonTypeWebView)tdButton.type).url; // If it existed
        } else if (tdButton.type instanceof TdApi.InlineKeyboardButtonTypeUserProfiler) {
            tlInlineBtn.user_id = ((TdApi.InlineKeyboardButtonTypeUserProfiler)tdButton.type).userId;
        }
        return tlInlineBtn;
    }
}
>>>>>>> REPLACE
[end of TMessagesProj/src/main/java/org/telegram/messenger/TdApiMessageConverter.java]
