package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public class DocumentObject {

    public static class ThemeDocument extends TLRPC.TL_document {

        public TLRPC.TL_themeSettings themeSettings;
        public TLRPC.Document wallpaper;
        public Theme.ThemeInfo baseTheme;
        public Theme.ThemeAccent accent;

        public ThemeDocument(TLRPC.TL_themeSettings settings) {
            themeSettings = settings;
            baseTheme = Theme.getTheme(Theme.getBaseThemeKey(settings));
            accent = baseTheme.createNewAccent(settings);
            if (themeSettings.wallpaper instanceof TLRPC.TL_wallPaper) {
                TLRPC.TL_wallPaper object = (TLRPC.TL_wallPaper) themeSettings.wallpaper;
                wallpaper = object.document;
                id = wallpaper.id;
                access_hash = wallpaper.access_hash;
                file_reference = wallpaper.file_reference;
                user_id = wallpaper.user_id;
                date = wallpaper.date;
                file_name = wallpaper.file_name;
                mime_type = wallpaper.mime_type;
                size = wallpaper.size;
                thumbs = wallpaper.thumbs;
                version = wallpaper.version;
                dc_id = wallpaper.dc_id;
                key = wallpaper.key;
                iv = wallpaper.iv;
                attributes = wallpaper.attributes;
            } else {
                id = Integer.MIN_VALUE;
                dc_id = Integer.MIN_VALUE;
            }
        }
    }
}
