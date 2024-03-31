/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.text.TextUtils;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
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
                title = LocaleController.getString("RepliesTitle", R.string.RepliesTitle);
                if (avatarDrawable != null) {
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                }
                if (imageReceiver != null) {
                    imageReceiver.setForUserOrChat(null, avatarDrawable);
                }
            } else if (UserObject.isUserSelf(user)) {
                title = LocaleController.getString("SavedMessages", R.string.SavedMessages);
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

    public static String setDialogPhotoTitle(BackupImageView imageView, TLObject dialog) {
        if (imageView != null) {
            return setDialogPhotoTitle(imageView.getImageReceiver(), imageView.getAvatarDrawable(), dialog);
        }
        return setDialogPhotoTitle(null, null, dialog);
    }

    public static String getPublicUsername(TLObject dialog) {
        if (dialog instanceof TLRPC.Chat) {
            return ChatObject.getPublicUsername((TLRPC.Chat) dialog);
        } else if (dialog instanceof TLRPC.User) {
            return UserObject.getPublicUsername((TLRPC.User) dialog);
        }
        return null;
    }

    public static long getEmojiStatusDocumentId(TLRPC.EmojiStatus emojiStatus) {
        if (emojiStatus instanceof TLRPC.TL_emojiStatus) {
            return ((TLRPC.TL_emojiStatus) emojiStatus).document_id;
        } else if (emojiStatus instanceof TLRPC.TL_emojiStatusUntil && ((TLRPC.TL_emojiStatusUntil) emojiStatus).until > (int) (System.currentTimeMillis() / 1000)) {
            return ((TLRPC.TL_emojiStatusUntil) emojiStatus).document_id;
        } else {
            return 0;
        }
    }

    public static int getEmojiStatusUntil(TLRPC.EmojiStatus emojiStatus) {
        if (emojiStatus instanceof TLRPC.TL_emojiStatusUntil && ((TLRPC.TL_emojiStatusUntil) emojiStatus).until > (int) (System.currentTimeMillis() / 1000)) {
            return ((TLRPC.TL_emojiStatusUntil) emojiStatus).until;
        }
        return 0;
    }

    public static boolean emojiStatusesEqual(TLRPC.EmojiStatus a, TLRPC.EmojiStatus b) {
        return getEmojiStatusDocumentId(a) == getEmojiStatusDocumentId(b) && getEmojiStatusUntil(a) == getEmojiStatusUntil(b);
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

}
