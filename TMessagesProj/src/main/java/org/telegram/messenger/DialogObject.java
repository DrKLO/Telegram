/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

public class DialogObject {

    public static boolean isChannel(TLRPC.Dialog dialog) {
        return dialog != null && (dialog.flags & 1) != 0;
    }

    public static long makeSecretDialogId(int chatId) {
        return ((long) chatId) << 32;
    }

    public static long makeFolderDialogId(int folderId) {
        return (((long) 2) << 32) | folderId;
    }

    public static boolean isFolderDialogId(long dialogId) {
        int lowerId = (int) dialogId;
        int highId = (int) (dialogId >> 32);
        return lowerId != 0 && highId == 2;
    }

    public static boolean isPeerDialogId(long dialogId) {
        int lowerId = (int) dialogId;
        int highId = (int) (dialogId >> 32);
        return lowerId != 0 && highId != 2 && highId != 1;
    }

    public static boolean isSecretDialogId(long dialogId) {
        return ((int) dialogId) == 0;
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
}
