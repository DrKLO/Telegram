package org.telegram.messenger;

import android.graphics.Paint;
import android.graphics.Path;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class DocumentObject {

    public static class ThemeDocument extends TLRPC.TL_document {

        public TLRPC.ThemeSettings themeSettings;
        public TLRPC.Document wallpaper;
        public Theme.ThemeInfo baseTheme;
        public Theme.ThemeAccent accent;

        public ThemeDocument(TLRPC.ThemeSettings settings) {
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

    public static boolean containsPhotoSizeType(ArrayList<TLRPC.PhotoSize> sizes, String type) {
        if (type == null)
            return false;
        for (int a = 0, N = sizes.size(); a < N; a++) {
            TLRPC.PhotoSize photoSize = sizes.get(a);
            if (type.equalsIgnoreCase(photoSize.type))
                return true;
        }
        return false;
    }

    public static SvgHelper.SvgDrawable getSvgThumb(ArrayList<TLRPC.PhotoSize> sizes, int colorKey, float alpha) {
        return getSvgThumb(sizes, colorKey, alpha, false);
    }

    public static SvgHelper.SvgDrawable getSvgThumb(ArrayList<TLRPC.PhotoSize> sizes, int colorKey, float alpha, boolean usePhotoSize) {
        int w = 512;
        int h = 512;
        TLRPC.TL_photoPathSize photoPathSize = null;
        for (int a = 0, N = sizes.size(); a < N; a++) {
            TLRPC.PhotoSize photoSize = sizes.get(a);
            if (photoSize instanceof TLRPC.TL_photoPathSize) {
                photoPathSize = (TLRPC.TL_photoPathSize) photoSize;
            } else if (photoSize instanceof TLRPC.TL_photoSize && usePhotoSize) {
                w = photoSize.w;
                h = photoSize.h;
            }
        }
        if (photoPathSize != null && w != 0 && h != 0) {
            SvgHelper.SvgDrawable pathThumb = SvgHelper.getDrawableByPath(photoPathSize.svgPath, w, h);
            if (pathThumb != null) {
                pathThumb.setupGradient(colorKey, alpha, false);
            }
            return pathThumb;
        }
        return null;
    }

    public static SvgHelper.SvgDrawable getCircleThumb(float radius, int colorKey, float alpha) {
        return getCircleThumb(radius, colorKey, null, alpha);
    }

    public static SvgHelper.SvgDrawable getCircleThumb(float radius, int colorKey, Theme.ResourcesProvider resourcesProvider, float alpha) {
        try {
            SvgHelper.SvgDrawable drawable = new SvgHelper.SvgDrawable();
            SvgHelper.Circle circle = new SvgHelper.Circle(256, 256, radius * 512);
            drawable.commands.add(circle);
            drawable.paints.put(circle, new Paint(Paint.ANTI_ALIAS_FLAG));
            drawable.width = 512;
            drawable.height = 512;
            drawable.setupGradient(colorKey, alpha, false);
            return drawable;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static SvgHelper.SvgDrawable getSvgThumb(TLRPC.Document document, int colorKey, float alpha) {
        return getSvgThumb(document, colorKey, alpha, 1.0f, null);
    }

    public static SvgHelper.SvgDrawable getSvgRectThumb(int colorKey, float alpha) {
        Path path = new Path();
        path.addRect(0, 0, 512, 512, Path.Direction.CW);
        path.close();
        SvgHelper.SvgDrawable drawable = new SvgHelper.SvgDrawable();
        drawable.commands.add(path);
        drawable.paints.put(path, new Paint(Paint.ANTI_ALIAS_FLAG));
        drawable.width = 512;
        drawable.height = 512;
        drawable.setupGradient(colorKey, alpha, false);
        return drawable;
    }

    public static SvgHelper.SvgDrawable getSvgThumb(TLRPC.Document document, int colorKey, float alpha, float zoom, Theme.ResourcesProvider resourcesProvider) {
        if (document == null) {
            return null;
        }
        SvgHelper.SvgDrawable pathThumb = null;
        for (int b = 0, N2 = document.thumbs.size(); b < N2; b++) {
            TLRPC.PhotoSize size = document.thumbs.get(b);
            if (size instanceof TLRPC.TL_photoPathSize) {
                int w = 512, h = 512;
                for (int a = 0, N = document.attributes.size(); a < N; a++) {
                    TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                    if (
                        attribute instanceof TLRPC.TL_documentAttributeImageSize ||
                        attribute instanceof TLRPC.TL_documentAttributeVideo
                    ) {
                        w = attribute.w;
                        h = attribute.h;
                        break;
                    }
                }
                if (w != 0 && h != 0) {
                    pathThumb = SvgHelper.getDrawableByPath(((TLRPC.TL_photoPathSize) size).svgPath, (int) (w * zoom), (int) (h * zoom));
                    if (pathThumb != null) {
                        pathThumb.setupGradient(colorKey, resourcesProvider, alpha, false);
                    }
                }
                break;
            }
        }
        return pathThumb;
    }

    public static SvgHelper.SvgDrawable getSvgThumb(int resourceId, int colorKey, float alpha) {
        SvgHelper.SvgDrawable pathThumb = SvgHelper.getDrawable(resourceId, 0xffff0000);
        if (pathThumb != null) {
            pathThumb.setupGradient(colorKey, alpha, false);
        }
        return pathThumb;
    }
}
