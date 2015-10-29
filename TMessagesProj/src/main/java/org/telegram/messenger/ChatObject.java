/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

public class ChatObject {

    public static final int CHAT_TYPE_CHAT = 0;
    public static final int CHAT_TYPE_BROADCAST = 1;
    public static final int CHAT_TYPE_CHANNEL = 2;
    public static final int CHAT_TYPE_USER = 3;

    public static boolean isLeftFromChat(TLRPC.Chat chat) {
        return chat == null || chat instanceof TLRPC.TL_chatForbidden || chat instanceof TLRPC.TL_channelForbidden || (chat.flags & TLRPC.CHAT_FLAG_USER_LEFT) != 0;
    }

    public static boolean isKickedFromChat(TLRPC.Chat chat) {
        return chat == null || chat instanceof TLRPC.TL_chatForbidden || chat instanceof TLRPC.TL_channelForbidden || (chat.flags & TLRPC.CHAT_FLAG_USER_KICKED) != 0;
    }

    public static boolean isNotInChat(TLRPC.Chat chat) {
        return chat == null || chat instanceof TLRPC.TL_chatForbidden || chat instanceof TLRPC.TL_channelForbidden || (chat.flags & TLRPC.CHAT_FLAG_USER_LEFT) != 0 || (chat.flags & TLRPC.CHAT_FLAG_USER_KICKED) != 0;
    }

    public static boolean isChannel(TLRPC.Chat chat) {
        return chat instanceof TLRPC.TL_channel || chat instanceof TLRPC.TL_channelForbidden;
    }

    public static boolean isChannel(int chatId) {
        TLRPC.Chat chat = MessagesController.getInstance().getChat(chatId);
        return chat instanceof TLRPC.TL_channel || chat instanceof TLRPC.TL_channelForbidden;
    }

    public static boolean isCanWriteToChannel(int chatId) {
        TLRPC.Chat chat = MessagesController.getInstance().getChat(chatId);
        return chat != null && ((chat.flags & TLRPC.CHAT_FLAG_ADMIN) != 0 || (chat.flags & TLRPC.CHAT_FLAG_USER_IS_EDITOR) != 0);
    }

    public static boolean canWriteToChat(TLRPC.Chat chat) {
        return !isChannel(chat) || (chat.flags & TLRPC.CHAT_FLAG_ADMIN) != 0 || (chat.flags & TLRPC.CHAT_FLAG_USER_IS_EDITOR) != 0 || (chat.flags & TLRPC.CHAT_FLAG_IS_BROADCAST) == 0;
    }

    public static TLRPC.Chat getChatByDialog(long did) {
        int lower_id = (int) did;
        int high_id = (int) (did >> 32);
        if (lower_id < 0) {
            return MessagesController.getInstance().getChat(-lower_id);
        }
        return null;
    }
}
