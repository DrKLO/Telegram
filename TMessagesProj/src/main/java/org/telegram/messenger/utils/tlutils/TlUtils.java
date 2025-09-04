package org.telegram.messenger.utils.tlutils;

import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;

public class TlUtils {
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
}
