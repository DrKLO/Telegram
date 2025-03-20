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

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;

import java.util.ArrayList;

public class DialogObject {

    public static boolean isChannel(TLRPC.Dialog dialog) {
        return dialog != null && (dialog.flags & 1) != 0;
    }

    public static long makeFolderDialogId(int folderId) {
        return 0x2000000000000000L | (long) folderId;
    }

    public static boolean isFolderDialogId(long dialogId) {
        return (dialogId & 0x2000000000000000L) != 0 && (dialogId & 0x8000000000000000L) == 0;
    }

    public static void initDialog(TLRPC.Dialog dialog) {
        if (dialog == null || dialog.id != 0) {
            return;
        }
        if (dialog instanceof TLRPC.TL_dialog) {
            if (dialog.peer == null) {
                return;
            }
            if (dialog.peer.user_id != 0) {
                dialog.id = dialog.peer.user_id;
            } else if (dialog.peer.chat_id != 0) {
                dialog.id = -dialog.peer.chat_id;
            } else {
                dialog.id = -dialog.peer.channel_id;
            }
        } else if (dialog instanceof TLRPC.TL_dialogFolder) {
            TLRPC.TL_dialogFolder dialogFolder = (TLRPC.TL_dialogFolder) dialog;
            dialog.id = makeFolderDialogId(dialogFolder.folder.id);
        }
    }

    public static long getPeerDialogId(TLRPC.Peer peer) {
        if (peer == null) {
            return 0;
        }
        if (peer.user_id != 0) {
            return peer.user_id;
        } else if (peer.chat_id != 0) {
            return -peer.chat_id;
        } else {
            return -peer.channel_id;
        }
    }

    public static long getPeerDialogId(TLRPC.InputPeer peer) {
        if (peer == null) {
            return 0;
        }
        if (peer.user_id != 0) {
            return peer.user_id;
        } else if (peer.chat_id != 0) {
            return -peer.chat_id;
        } else {
            return -peer.channel_id;
        }
    }

    public static long getLastMessageOrDraftDate(TLRPC.Dialog dialog, TLRPC.DraftMessage draftMessage) {
        return draftMessage != null && draftMessage.date >= dialog.last_message_date ? draftMessage.date : dialog.last_message_date;
    }

    public static boolean isChatDialog(long dialogId) {
        return !isEncryptedDialog(dialogId) && !isFolderDialogId(dialogId) && dialogId < 0;
    }

    public static boolean isUserDialog(long dialogId) {
        return !isEncryptedDialog(dialogId) && !isFolderDialogId(dialogId) && dialogId > 0;
    }

    public static boolean isEncryptedDialog(long dialogId) {
        return (dialogId & 0x4000000000000000L) != 0 && (dialogId & 0x8000000000000000L) == 0;
    }

    public static long makeEncryptedDialogId(long chatId) {
        return 0x4000000000000000L | (chatId & 0x00000000ffffffffL);
    }

    public static int getEncryptedChatId(long dialogId) {
        return (int) (dialogId & 0x00000000ffffffffL);
    }

    public static int getFolderId(long dialogId) {
        return (int) dialogId;
    }

    public static String getDialogTitle(TLObject dialog) {
        return setDialogPhotoTitle(null, null, dialog);
    }

    public static String setDialogPhotoTitle(ImageReceiver imageReceiver, AvatarDrawable avatarDrawable, TLObject dialog) {
        String title = "";
        if (dialog instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) dialog;
            if (UserObject.isReplyUser(user)) {
                title = LocaleController.getString(R.string.RepliesTitle);
                if (avatarDrawable != null) {
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                }
                if (imageReceiver != null) {
                    imageReceiver.setForUserOrChat(null, avatarDrawable);
                }
            } else if (UserObject.isUserSelf(user)) {
                title = LocaleController.getString(R.string.SavedMessages);
                if (avatarDrawable != null) {
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                }
                if (imageReceiver != null) {
                    imageReceiver.setForUserOrChat(null, avatarDrawable);
                }
            } else {
                title = UserObject.getUserName(user);
                if (avatarDrawable != null) {
                    avatarDrawable.setInfo(user);
                }
                if (imageReceiver != null) {
                    imageReceiver.setForUserOrChat(dialog, avatarDrawable);
                }
            }
        } else if (dialog instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) dialog;
            title = chat.title;
            if (avatarDrawable != null) {
                avatarDrawable.setInfo(chat);
            }
            if (imageReceiver != null) {
                imageReceiver.setForUserOrChat(dialog, avatarDrawable);
            }
        }
        return title;
    }

    @NonNull
    public static String getName(long dialogId) {
        return getName(UserConfig.selectedAccount, dialogId);
    }

    @NonNull
    public static String getStatus(long dialogId) {
        return getStatus(UserConfig.selectedAccount, dialogId);
    }

    @NonNull
    public static String getName(int currentAccount, long dialogId) {
        return getName(MessagesController.getInstance(currentAccount).getUserOrChat(dialogId));
    }

    @NonNull
    public static String getStatus(int currentAccount, long dialogId) {
        return getStatus(currentAccount, MessagesController.getInstance(currentAccount).getUserOrChat(dialogId));
    }

    @NonNull
    public static String getName(TLObject obj) {
        if (obj instanceof TLRPC.User) {
            return AndroidUtilities.removeRTL(AndroidUtilities.removeDiacritics(UserObject.getUserName((TLRPC.User) obj)));
        } else if (obj instanceof TLRPC.Chat) {
            final TLRPC.Chat chat = (TLRPC.Chat) obj;
            return chat != null ? chat.title : "";
        } else {
            return "";
        }
    }

    @NonNull
    public static String getStatus(int currentAccount, TLObject obj) {
        if (obj instanceof TLRPC.User) {
            return LocaleController.formatUserStatus(currentAccount, (TLRPC.User) obj, null, null);
        } else if (obj instanceof TLRPC.Chat) {
            final TLRPC.Chat chat = (TLRPC.Chat) obj;
            if (chat.participants_count > 1) {
                if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                    return LocaleController.formatPluralStringComma("Subscribers", chat.participants_count);
                } else {
                    return LocaleController.formatPluralStringComma("Members", chat.participants_count);
                }
            } else {
                if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                    return LocaleController.getString(R.string.DiscussChannel);
                } else {
                    return LocaleController.getString(R.string.AccDescrGroup);
                }
            }
        } else {
            return "";
        }
    }

    @NonNull
    public static String getShortName(int currentAccount, long dialogId) {
        return getShortName(MessagesController.getInstance(currentAccount).getUserOrChat(dialogId));
    }

    @NonNull
    public static String getShortName(long dialogId) {
        return getShortName(MessagesController.getInstance(UserConfig.selectedAccount).getUserOrChat(dialogId));
    }

    @NonNull
    public static String getShortName(TLObject obj) {
        if (obj instanceof TLRPC.User) {
            return AndroidUtilities.removeRTL(AndroidUtilities.removeDiacritics(UserObject.getForcedFirstName((TLRPC.User) obj)));
        } else if (obj instanceof TLRPC.Chat) {
            final TLRPC.Chat chat = (TLRPC.Chat) obj;
            return AndroidUtilities.removeRTL(AndroidUtilities.removeDiacritics(chat != null ? chat.title : ""));
        } else {
            return "";
        }
    }

    public static boolean hasPhoto(TLObject obj) {
        if (obj instanceof TLRPC.User) {
            return ((TLRPC.User) obj).photo != null;
        } else if (obj instanceof TLRPC.Chat) {
            return ((TLRPC.Chat) obj).photo != null;
        }
        return false;
    }

    public static String setDialogPhotoTitle(BackupImageView imageView, TLObject dialog) {
        if (imageView != null) {
            return setDialogPhotoTitle(imageView.getImageReceiver(), imageView.getAvatarDrawable(), dialog);
        }
        return setDialogPhotoTitle(null, null, dialog);
    }

    public static String getPublicUsername(TLObject dialog) {
        if (dialog instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) dialog;
            return getPublicUsername(chat.username, chat.usernames, false);
        } else if (dialog instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) dialog;
            return getPublicUsername(user.username, user.usernames, false);
        }
        return null;
    }

    public static String getPublicUsername(TLObject dialog, String query) {
        if (dialog instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) dialog;
            return query == null ? getPublicUsername(chat.username, chat.usernames, false) : getSimilarPublicUsername(chat.username, chat.usernames, query);
        } else if (dialog instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) dialog;
            return query == null ? getPublicUsername(user.username, user.usernames, false) : getSimilarPublicUsername(user.username, user.usernames, query);
        }
        return null;
    }

    public static String getPublicUsername(String username, ArrayList<TLRPC.TL_username> usernames, boolean editable) {
        if (!TextUtils.isEmpty(username) && !editable) {
            return username;
        }
        if (usernames != null) {
            for (int i = 0; i < usernames.size(); ++i) {
                TLRPC.TL_username u = usernames.get(i);
                if (u != null && (u.active && !editable || u.editable) && !TextUtils.isEmpty(u.username)) {
                    return u.username;
                }
            }
        }
        if (!TextUtils.isEmpty(username) && editable && (usernames == null || usernames.size() <= 0)) {
            return username;
        }
        return null;
    }

    public static String getSimilarPublicUsername(String obj_username, ArrayList<TLRPC.TL_username> obj_usernames, String query) {
        double bestSimilarity = -1;
        String bestUsername = null;
        if (obj_usernames != null) {
            for (int i = 0; i < obj_usernames.size(); ++i) {
                TLRPC.TL_username u = obj_usernames.get(i);
                if (u != null && u.active && !TextUtils.isEmpty(u.username)) {
                    double s = bestSimilarity < 0 ? 0 : similarity(u.username, query);
                    if (s > bestSimilarity) {
                        bestSimilarity = s;
                        bestUsername = u.username;
                    }
                }
            }
        }
        if (!TextUtils.isEmpty(obj_username)) {
            double s = bestSimilarity < 0 ? 0 : similarity(obj_username, query);
            if (s > bestSimilarity) {
                bestSimilarity = s;
                bestUsername = obj_username;
            }
        }
        return bestUsername;
    }

    public static double similarity(String s1, String s2) {
        String longer = s1, shorter = s2;
        if (s1.length() < s2.length()) { // longer should always have greater length
            longer = s2; shorter = s1;
        }
        int longerLength = longer.length();
        if (longerLength == 0) { return 1.0; }
        return (longerLength - editDistance(longer, shorter)) / (double) longerLength;
    }

    public static int editDistance(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();

        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0)
                    costs[j] = j;
                else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1))
                            newValue = Math.min(Math.min(newValue, lastValue),
                                    costs[j]) + 1;
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0)
                costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }

    public static boolean isEmojiStatusCollectible(long dialogId) {
        if (dialogId >= 0) {
            final TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(dialogId);
            if (user == null) return false;
            return isEmojiStatusCollectible(user.emoji_status);
        } else {
            final TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-dialogId);
            if (chat == null) return false;
            return isEmojiStatusCollectible(chat.emoji_status);
        }
    }

    public static boolean isEmojiStatusCollectible(TLRPC.EmojiStatus emojiStatus) {
        if (MessagesController.getInstance(UserConfig.selectedAccount).premiumFeaturesBlocked()) {
            return false;
        }
        if (emojiStatus instanceof TLRPC.TL_emojiStatusCollectible) {
            final TLRPC.TL_emojiStatusCollectible status = (TLRPC.TL_emojiStatusCollectible) emojiStatus;
            if ((status.flags & 1) != 0 && status.until <= (int) (System.currentTimeMillis() / 1000)) {
                return false;
            }
            return true;
        }
        return false;
    }

    public static long getEmojiStatusDocumentId(long dialogId) {
        if (dialogId >= 0) {
            final TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(dialogId);
            if (user == null) return 0;
            return getEmojiStatusDocumentId(user.emoji_status);
        } else {
            final TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-dialogId);
            if (chat == null) return 0;
            return getEmojiStatusDocumentId(chat.emoji_status);
        }
    }

    public static long getEmojiStatusDocumentId(TLRPC.EmojiStatus emojiStatus) {
        if (MessagesController.getInstance(UserConfig.selectedAccount).premiumFeaturesBlocked()) {
            return 0;
        }
        if (emojiStatus instanceof TLRPC.TL_emojiStatus) {
            final TLRPC.TL_emojiStatus status = (TLRPC.TL_emojiStatus) emojiStatus;
            if ((status.flags & 1) != 0 && status.until <= (int) (System.currentTimeMillis() / 1000)) {
                return 0;
            }
            return status.document_id;
        } else if (emojiStatus instanceof TLRPC.TL_emojiStatusCollectible) {
            final TLRPC.TL_emojiStatusCollectible status = (TLRPC.TL_emojiStatusCollectible) emojiStatus;
            if ((status.flags & 1) != 0 && status.until <= (int) (System.currentTimeMillis() / 1000)) {
                return 0;
            }
            return status.document_id;
        }
        return 0;
    }

    public static long getEmojiStatusCollectibleId(TLRPC.EmojiStatus emojiStatus) {
        if (MessagesController.getInstance(UserConfig.selectedAccount).premiumFeaturesBlocked()) {
            return 0;
        }
        if (emojiStatus instanceof TLRPC.TL_emojiStatusCollectible) {
            final TLRPC.TL_emojiStatusCollectible status = (TLRPC.TL_emojiStatusCollectible) emojiStatus;
            if ((status.flags & 1) != 0 && status.until <= (int) (System.currentTimeMillis() / 1000)) {
                return 0;
            }
            return status.collectible_id;
        }
        return 0;
    }

    public static int getEmojiStatusUntil(TLRPC.EmojiStatus emojiStatus) {
        if (emojiStatus instanceof TLRPC.TL_emojiStatus) {
            final TLRPC.TL_emojiStatus status = (TLRPC.TL_emojiStatus) emojiStatus;
            if ((status.flags & 1) != 0) {
                return status.until;
            }
        } else if (emojiStatus instanceof TLRPC.TL_emojiStatusCollectible) {
            final TLRPC.TL_emojiStatusCollectible status = (TLRPC.TL_emojiStatusCollectible) emojiStatus;
            if ((status.flags & 1) != 0) {
                return status.until;
            }
        }
        return 0;
    }

    public static TLRPC.EmojiStatus filterEmojiStatus(TLRPC.EmojiStatus emojiStatus) {
        final int until = getEmojiStatusUntil(emojiStatus);
        if (until != 0 && until <= (int) (System.currentTimeMillis() / 1000)) {
            return null;
        }
        return emojiStatus;
    }

    public static boolean emojiStatusesEqual(TLRPC.EmojiStatus a, TLRPC.EmojiStatus b) {
        return (
            getEmojiStatusDocumentId(a) == getEmojiStatusDocumentId(b) &&
            getEmojiStatusCollectibleId(a) == getEmojiStatusCollectibleId(b) &&
            getEmojiStatusUntil(a) == getEmojiStatusUntil(b)
        );
    }

    public static TLRPC.TL_username findUsername(String username, TLRPC.User user) {
        if (user == null) return null;
        return findUsername(username, user.usernames);
    }

    public static TLRPC.TL_username findUsername(String username, TLRPC.Chat chat) {
        if (chat == null) return null;
        return findUsername(username, chat.usernames);
    }

    public static TLRPC.TL_username findUsername(String username, ArrayList<TLRPC.TL_username> usernames) {
        if (usernames == null) return null;
        for (TLRPC.TL_username u : usernames) {
            if (u != null && TextUtils.equals(u.username, username)) {
                return u;
            }
        }
        return null;
    }

    public static TL_bots.botVerification getBotVerification(TLObject object) {
        if (object instanceof TLRPC.UserFull) {
            return ((TLRPC.UserFull) object).bot_verification;
        } else if (object instanceof TLRPC.ChatFull) {
            return ((TLRPC.ChatFull) object).bot_verification;
        } else {
            return null;
        }
    }

    public static long getBotVerificationIcon(TLObject object) {
        if (object instanceof TLRPC.User) {
            return ((TLRPC.User) object).bot_verification_icon;
        } else if (object instanceof TLRPC.Chat) {
            return ((TLRPC.Chat) object).bot_verification_icon;
        } else {
            return 0;
        }
    }

    public static boolean isEmpty(TL_account.RequirementToContact value) {
        return value == null || value instanceof TL_account.requirementToContactEmpty;
    }

    public static boolean isPremiumBlocked(TL_account.RequirementToContact value) {
        return value instanceof TL_account.requirementToContactPremium;
    }

    public static long getMessagesStarsPrice(TL_account.RequirementToContact value) {
        if (value instanceof TL_account.requirementToContactPaidMessages) {
            return ((TL_account.requirementToContactPaidMessages) value).stars_amount;
        }
        return 0;
    }
}
