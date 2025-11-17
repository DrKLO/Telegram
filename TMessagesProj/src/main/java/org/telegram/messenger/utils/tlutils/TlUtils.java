package org.telegram.messenger.utils.tlutils;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.telegram.tgnet.TLObject;

import org.telegram.messenger.MediaDataController;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;

import java.util.List;

public class TlUtils {

    public static TLRPC.InputPeer getInputPeerFromSendMessageRequest(TLObject request) {
        if (request instanceof TLRPC.TL_messages_sendMessage) {
            return ((TLRPC.TL_messages_sendMessage) request).peer;
        } else if (request instanceof TLRPC.TL_messages_sendMedia) {
            return ((TLRPC.TL_messages_sendMedia) request).peer;
        } else if (request instanceof TLRPC.TL_messages_sendInlineBotResult) {
            return ((TLRPC.TL_messages_sendInlineBotResult) request).peer;
        } else if (request instanceof TLRPC.TL_messages_forwardMessages) {
            return ((TLRPC.TL_messages_forwardMessages) request).to_peer;
        } else if (request instanceof TLRPC.TL_messages_sendMultiMedia) {
            return ((TLRPC.TL_messages_sendMultiMedia) request).peer;
        }
        return null;
    }

    public static TLRPC.InputReplyTo getInputReplyToFromSendMessageRequest(TLObject request) {
        if (request instanceof TLRPC.TL_messages_sendMessage) {
            return ((TLRPC.TL_messages_sendMessage) request).reply_to;
        } else if (request instanceof TLRPC.TL_messages_sendMedia) {
            return ((TLRPC.TL_messages_sendMedia) request).reply_to;
        } else if (request instanceof TLRPC.TL_messages_sendInlineBotResult) {
            return ((TLRPC.TL_messages_sendInlineBotResult) request).reply_to;
        } else if (request instanceof TLRPC.TL_messages_forwardMessages) {
            return ((TLRPC.TL_messages_forwardMessages) request).reply_to;
        } else if (request instanceof TLRPC.TL_messages_sendMultiMedia) {
            return ((TLRPC.TL_messages_sendMultiMedia) request).reply_to;
        }
        return null;
    }

    public static String getMessageFromSendMessageRequest(TLObject request) {
        if (request instanceof TLRPC.TL_messages_sendMessage) {
            return ((TLRPC.TL_messages_sendMessage) request).message;
        } else if (request instanceof TLRPC.TL_messages_sendMedia) {
            return ((TLRPC.TL_messages_sendMedia) request).message;
        } else if (request instanceof TLRPC.TL_messages_sendMultiMedia) {
            final TLRPC.TL_messages_sendMultiMedia messages = (TLRPC.TL_messages_sendMultiMedia) request;
            for (TLRPC.TL_inputSingleMedia m: messages.multi_media) {
                if (!TextUtils.isEmpty(m.message)) {
                    return m.message;
                }
            }
            return null;
        }
        return null;
    }

    public static void setInputReplyToFromSendMessageRequest(TLObject request, TLRPC.InputReplyTo replyTo) {
        if (request instanceof TLRPC.TL_messages_sendMessage) {
            ((TLRPC.TL_messages_sendMessage) request).reply_to = replyTo;
            ((TLRPC.TL_messages_sendMessage) request).flags |= 1;
        } else if (request instanceof TLRPC.TL_messages_sendMedia) {
            ((TLRPC.TL_messages_sendMedia) request).reply_to = replyTo;
            ((TLRPC.TL_messages_sendMedia) request).flags |= 1;
        } else if (request instanceof TLRPC.TL_messages_sendInlineBotResult) {
            ((TLRPC.TL_messages_sendInlineBotResult) request).reply_to = replyTo;
            ((TLRPC.TL_messages_sendInlineBotResult) request).flags |= 1;
        } else if (request instanceof TLRPC.TL_messages_forwardMessages) {
            ((TLRPC.TL_messages_forwardMessages) request).reply_to = replyTo;
            ((TLRPC.TL_messages_forwardMessages) request).flags |= 1;
        } else if (request instanceof TLRPC.TL_messages_sendMultiMedia) {
            ((TLRPC.TL_messages_sendMultiMedia) request).reply_to = replyTo;
            ((TLRPC.TL_messages_sendMultiMedia) request).flags |= 1;
        }
    }

    @Nullable
    public static <T> T findFirstInstance(List<?> list, Class<T> tClass) {
        if (list == null || tClass == null) {
            return null;
        }

        for (Object entry : list) {
            if (tClass.isInstance(entry)) {
                return tClass.cast(entry);
            }
        }
        return null;
    }

    public static long getOrCalculateRandomIdFromSendMessageRequest(TLObject request) {
        if (request instanceof TLRPC.TL_messages_sendMessage) {
            return ((TLRPC.TL_messages_sendMessage) request).random_id;
        } else if (request instanceof TLRPC.TL_messages_sendMedia) {
            return ((TLRPC.TL_messages_sendMedia) request).random_id;
        } else if (request instanceof TLRPC.TL_messages_sendInlineBotResult) {
            return ((TLRPC.TL_messages_sendInlineBotResult) request).random_id;
        } else if (request instanceof TLRPC.TL_messages_forwardMessages) {
            final TLRPC.TL_messages_forwardMessages messages = (TLRPC.TL_messages_forwardMessages) request;
            long hash = 0;
            for (Long l: messages.random_id) {
                hash = MediaDataController.calcHash(hash, l);
            }
            return hash;
        } else if (request instanceof TLRPC.TL_messages_sendMultiMedia) {
            final TLRPC.TL_messages_sendMultiMedia messages = (TLRPC.TL_messages_sendMultiMedia) request;
            long hash = 0;
            for (TLRPC.TL_inputSingleMedia m: messages.multi_media) {
                hash = MediaDataController.calcHash(hash, m.random_id);
            }
            return hash;

        }
        return 0;
    }

    public static boolean isInstance(Object obj, final Class<?>... classes) {
        if (obj == null || classes == null) return false;

        for (Class<?> cls : classes) {
            if (cls.isInstance(obj)) {
                return true;
            }
        }
        return false;
    }

    public static TLRPC.Document getGiftDocument(TL_stars.StarGift gift) {
        TLRPC.Document document = gift.sticker;
        if (gift.attributes != null && document == null)  {
            for (TL_stars.StarGiftAttribute attribute : gift.attributes) {
                if (attribute instanceof TL_stars.starGiftAttributeModel) {
                    document = ((TL_stars.starGiftAttributeModel) attribute).document;
                    break;
                }
            }
        }
        return document;
    }

    public static TLRPC.Document getGiftDocumentPattern(TL_stars.StarGift gift) {
        TLRPC.Document document = gift.sticker;
        if (gift.attributes != null && document == null)  {
            for (TL_stars.StarGiftAttribute attribute : gift.attributes) {
                if (attribute instanceof TL_stars.starGiftAttributePattern) {
                    document = ((TL_stars.starGiftAttributePattern) attribute).document;
                    break;
                }
            }
        }
        return document;
    }

    public static String getThemeEmoticonOrGiftTitle(TLRPC.ChatTheme chatTheme) {
        if (chatTheme instanceof TLRPC.TL_chatTheme) {
            return ((TLRPC.TL_chatTheme) chatTheme).emoticon;
        } else if (chatTheme instanceof TLRPC.TL_chatThemeUniqueGift) {
            return ((TLRPC.TL_chatThemeUniqueGift) chatTheme).gift.title;
        }
        return null;
    }



    public static TLRPC.GroupCall applyGroupCallUpdate(TLRPC.GroupCall oldGroupCall, TLRPC.GroupCall newGroupCall) {
        if (newGroupCall instanceof TLRPC.TL_groupCall && oldGroupCall instanceof TLRPC.TL_groupCall) {
            final TLRPC.TL_groupCall tlNew = (TLRPC.TL_groupCall) newGroupCall;

            if (tlNew.min) {
                final TLRPC.TL_groupCall tlOld = (TLRPC.TL_groupCall) oldGroupCall;

                tlNew.can_change_join_muted = tlOld.can_change_join_muted;
                tlNew.can_start_video = tlOld.can_start_video;
                tlNew.creator = tlOld.creator;
                tlNew.can_change_messages_enabled = tlOld.can_change_messages_enabled;
            }
        }

        return newGroupCall;
    }
}
