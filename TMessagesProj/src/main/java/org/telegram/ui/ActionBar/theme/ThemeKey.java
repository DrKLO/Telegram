package org.telegram.ui.ActionBar.theme;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.telegram.tgnet.TLRPC;

public class ThemeKey {
    public final String emoticon;
    public final String giftSlug;

    private ThemeKey(String emoji, String giftSlug) {
        this.emoticon = emoji;
        this.giftSlug = giftSlug;
    }

    public static ThemeKey ofEmoticon(String emoji) {
        return new ThemeKey(emoji, null);
    }

    public static ThemeKey ofGiftSlug(String slug) {
        return new ThemeKey(null, slug);
    }

    public static ThemeKey of(TLRPC.TL_theme theme) {
        return new ThemeKey(theme.emoticon, null);
    }

    public static TLRPC.InputChatTheme toInputTheme(ThemeKey key) {
        if (key != null && !TextUtils.isEmpty(key.emoticon)) {
            TLRPC.Tl_inputChatTheme inputChatTheme = new TLRPC.Tl_inputChatTheme();
            inputChatTheme.emoticon = key.emoticon;
            return inputChatTheme;
        }
        if (key != null && !TextUtils.isEmpty(key.giftSlug)) {
            TLRPC.Tl_inputChatThemeUniqueGift inputChatTheme = new TLRPC.Tl_inputChatThemeUniqueGift();
            inputChatTheme.slug = key.giftSlug;
            return inputChatTheme;
        }

        return new TLRPC.Tl_inputChatThemeEmpty();
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(emoticon) && TextUtils.isEmpty(giftSlug);
    }

    public String toSavedString() {
        if (giftSlug != null) {
            return "gift_" + giftSlug;
        }
        if (emoticon != null) {
            return "emoticon_" + emoticon;
        }
        return null;
    }

    public static ThemeKey fromSavedString(String string) {
        if (string == null) {
            return null;
        }

        if (string.startsWith("gift_")) {
            return new ThemeKey(null, string.substring(5));
        }
        if (string.startsWith("emoticon_")) {
            return new ThemeKey(string.substring(9), null);
        }
        if (!TextUtils.isEmpty(string)) {
            return new ThemeKey(string, null);
        }

        return null;
    }

    @Nullable
    public static ThemeKey of(TLRPC.ChatTheme theme) {
        if (theme instanceof TLRPC.TL_chatTheme) {
            return new ThemeKey(((TLRPC.TL_chatTheme) theme).emoticon, null);
        } else if (theme instanceof TLRPC.TL_chatThemeUniqueGift) {
            return new ThemeKey(null, ((TLRPC.TL_chatThemeUniqueGift) theme).gift.slug);
        }
        return null;
    }

    @Nullable
    public static ThemeKey of(TLRPC.InputChatTheme theme) {
        if (theme instanceof TLRPC.Tl_inputChatTheme) {
            return new ThemeKey(((TLRPC.Tl_inputChatTheme) theme).emoticon, null);
        } else if (theme instanceof TLRPC.Tl_inputChatThemeUniqueGift) {
            return new ThemeKey(null, ((TLRPC.Tl_inputChatThemeUniqueGift) theme).slug);
        }
        return null;
    }

    @Override
    public int hashCode() {
        final int hash1 = emoticon != null ? emoticon.hashCode() : 0;
        final int hash2 = giftSlug != null ? giftSlug.hashCode() : 0;

        return hash1 ^ hash2;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof ThemeKey) {
            ThemeKey key = (ThemeKey) obj;
            return TextUtils.equals(this.emoticon, key.emoticon) && TextUtils.equals(this.giftSlug, key.giftSlug);
        }

        return false;
    }

    public static boolean equals(@Nullable ThemeKey key1, @Nullable ThemeKey key2) {
        if (key1 == key2) {
            return true;
        }

        if (key1 == null || key2 == null) {
            return false;
        }

        return key1.equals(key2);
    }
}
