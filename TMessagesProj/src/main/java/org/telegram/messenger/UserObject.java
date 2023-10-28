/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.tgnet.TLRPC;

public class UserObject {

    public static final long REPLY_BOT = 1271266957L;

    public static boolean isDeleted(TLRPC.User user) {
        return user == null || user instanceof TLRPC.TL_userDeleted_old2 || user instanceof TLRPC.TL_userEmpty || user.deleted;
    }

    public static boolean isContact(TLRPC.User user) {
        return user != null && (user instanceof TLRPC.TL_userContact_old2 || user.contact || user.mutual_contact);
    }

    public static boolean isUserSelf(TLRPC.User user) {
        return user != null && (user instanceof TLRPC.TL_userSelf_old3 || user.self);
    }

    public static boolean isReplyUser(TLRPC.User user) {
        return user != null && (user.id == 708513 || user.id == REPLY_BOT);
    }

    public static boolean isReplyUser(long did) {
        return did == 708513 || did == REPLY_BOT;
    }

    @NonNull
    public static String getUserName(TLRPC.User user) {
        if (user == null || isDeleted(user)) {
            return LocaleController.getString("HiddenName", R.string.HiddenName);
        }
        String name = ContactsController.formatName(user.first_name, user.last_name);
        return name.length() != 0 || TextUtils.isEmpty(user.phone) ? name : PhoneFormat.getInstance().format("+" + user.phone);
    }

    public static String getPublicUsername(TLRPC.User user, boolean editable) {
        if (user == null) {
            return null;
        }
        if (!TextUtils.isEmpty(user.username)) {
            return user.username;
        }
        if (user.usernames != null) {
            for (int i = 0; i < user.usernames.size(); ++i) {
                TLRPC.TL_username u = user.usernames.get(i);
                if (u != null && (u.active && !editable || u.editable) && !TextUtils.isEmpty(u.username)) {
                    return u.username;
                }
            }
        }
        return null;
    }

    public static String getPublicUsername(TLRPC.User user) {
        return getPublicUsername(user, false);
    }

    public static boolean hasPublicUsername(TLRPC.User user, String username) {
        if (user == null || username == null) {
            return false;
        }
        if (username.equalsIgnoreCase(user.username)) {
            return true;
        }
        if (user.usernames != null) {
            for (int i = 0; i < user.usernames.size(); ++i) {
                TLRPC.TL_username u = user.usernames.get(i);
                if (u != null && u.active && username.equalsIgnoreCase(u.username)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String getFirstName(TLRPC.User user) {
        return getFirstName(user, true);
    }

    public static String getFirstName(TLRPC.User user, boolean allowShort) {
        if (user == null || isDeleted(user)) {
            return "DELETED";
        }
        String name = user.first_name;
        if (TextUtils.isEmpty(name)) {
            name = user.last_name;
        } else if (!allowShort && name.length() <= 2) {
            return ContactsController.formatName(user.first_name, user.last_name);
        }
        return !TextUtils.isEmpty(name) ? name : LocaleController.getString("HiddenName", R.string.HiddenName);
    }

    public static boolean hasPhoto(TLRPC.User user) {
        return user != null && user.photo != null && !(user.photo instanceof TLRPC.TL_userProfilePhotoEmpty);
    }

    public static TLRPC.UserProfilePhoto getPhoto(TLRPC.User user) {
        return hasPhoto(user) ? user.photo : null;
    }

    public static boolean hasFallbackPhoto(TLRPC.UserFull userInfo) {
        return userInfo != null && userInfo.fallback_photo != null && !(userInfo.fallback_photo instanceof TLRPC.TL_photoEmpty);
    }

    public static Long getEmojiStatusDocumentId(TLRPC.User user) {
        if (user == null) {
            return null;
        }
        return getEmojiStatusDocumentId(user.emoji_status);
    }

    public static Long getEmojiStatusDocumentId(TLRPC.EmojiStatus emojiStatus) {
        if (emojiStatus == null) {
            return null;
        }
        if (emojiStatus instanceof TLRPC.TL_emojiStatus)
            return ((TLRPC.TL_emojiStatus) emojiStatus).document_id;
        if (emojiStatus instanceof TLRPC.TL_emojiStatusUntil) {
            TLRPC.TL_emojiStatusUntil untilStatus = (TLRPC.TL_emojiStatusUntil) emojiStatus;
            if (untilStatus.until > (int) (System.currentTimeMillis() / 1000)) {
                return untilStatus.document_id;
            }
        }
        return null;
    }

    public static boolean isService(long user_id) {
        return user_id == 333000 || user_id == 777000 || user_id == 42777;
    }
}
