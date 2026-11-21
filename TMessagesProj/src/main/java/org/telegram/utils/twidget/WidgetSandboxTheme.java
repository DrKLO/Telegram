package org.telegram.utils.twidget;

import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

import java.util.Locale;
import org.telegram.ui.Components.RichWidgetBlock;

/**
 * The chat's theme as a widget sees it (widget-sandbox docs/sdk-v1.md §2.3, §2.4): the colour scheme and
 * the 15 Mini Apps palette keys as `#rrggbb`, taken from the bubble the widget sits in, plus the
 * tokens of the default `<button>` and of the message text (the renderer's UA layer, v0.1.1). Serialized as the JSON the native bridge parses into a WidgetTheme.
 */
public final class WidgetSandboxTheme {

    private WidgetSandboxTheme() {
    }

    // A bubble's fill, text, hint, link and accent: an incoming one's, or an outgoing one's for a widget the
    // user sent or forwarded (docs/sdk-v1.md §5.1).
    private static final int[] IN = {Theme.key_chat_inBubble, Theme.key_chat_messageTextIn, Theme.key_chat_inTimeText,
            Theme.key_chat_messageLinkIn, Theme.key_chat_inReplyNameText};
    private static final int[] OUT = {Theme.key_chat_outBubble, Theme.key_chat_messageTextOut, Theme.key_chat_outTimeText,
            Theme.key_chat_messageLinkOut, Theme.key_chat_outReplyNameText};

    /** The theme of a bubble drawn with `resourcesProvider` (null: the app's theme), outgoing when `out`. */
    public static String build(Theme.ResourcesProvider resourcesProvider, boolean out) {
        boolean dark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        int[] keys = out ? OUT : IN;
        try {
            int bubble = opaque(fill(resourcesProvider, out), dark);
            int text = Theme.getColor(keys[1], resourcesProvider);
            int hint = Theme.getColor(keys[2], resourcesProvider);
            int link = Theme.getColor(keys[3], resourcesProvider);
            int accent = Theme.getColor(keys[4], resourcesProvider);
            int button = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
            int buttonText = Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider);
            int secondary = Theme.blendOver(bubble, Theme.multAlpha(accent, .1f));

            JSONObject params = new JSONObject();
            params.put("bg_color", hex(bubble, bubble));
            params.put("text_color", hex(text, bubble));
            params.put("hint_color", hex(hint, bubble));
            params.put("link_color", hex(link, bubble));
            params.put("button_color", hex(button, bubble));
            params.put("button_text_color", hex(buttonText, button));
            params.put("secondary_bg_color", hex(secondary, bubble));
            params.put("accent_text_color", hex(accent, bubble));
            params.put("subtitle_text_color", hex(hint, bubble));
            params.put("destructive_text_color", hex(Theme.getColor(Theme.key_text_RedRegular, resourcesProvider), bubble));
            // No bubble counterpart: the closest colours, so that Mini Apps code works (§2.3).
            params.put("section_bg_color", hex(bubble, bubble));
            params.put("section_header_text_color", hex(accent, bubble));
            params.put("section_separator_color", hex(Theme.multAlpha(text, .12f), bubble));
            params.put("header_bg_color", hex(secondary, bubble));
            params.put("bottom_bar_bg_color", hex(secondary, bubble));

            // The bubble's own buttons are tinted with its accent; text in the accent colour.
            JSONObject buttonTokens = new JSONObject();
            buttonTokens.put("text", hex(accent, bubble));

            JSONObject theme = new JSONObject();
            theme.put("colorScheme", dark ? "dark" : "light");
            theme.put("params", params);
            theme.put("button", buttonTokens);
            theme.put("text", textTokens());
            return theme.toString();
        } catch (Exception e) {
            FileLog.e(e);
            return "{\"colorScheme\":\"" + (dark ? "dark" : "light") + "\"}";
        }
    }

    /**
     * What {@link #build} reads, hashed: a bubble compares it when drawn and passes the theme again when the
     * chat's theme, the night mode or the text size changed (`setTheme` on every change, §5.1).
     */
    public static int signature(Theme.ResourcesProvider resourcesProvider, boolean out) {
        int hash = (resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark()) ? 1 : 0;
        for (int key : out ? OUT : IN) hash = 31 * hash + Theme.getColor(key, resourcesProvider);
        if (out) for (int key : GRADIENT) hash = 31 * hash + gradientColor(key, resourcesProvider);
        hash = 31 * hash + Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        hash = 31 * hash + Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider);
        hash = 31 * hash + Theme.getColor(Theme.key_text_RedRegular, resourcesProvider);
        hash = 31 * hash + SharedConfig.fontSize;
        hash = 31 * hash + (Theme.chat_msgTextPaint != null ? Float.floatToIntBits(Theme.chat_msgTextPaint.getTextSize()) : 0);
        return 31 * hash + RichWidgetBlock.bleed();
    }

    /**
     * The message text's tokens (docs/sdk-v1.md §2.4): the chat's text size in CSS px (a dp), its line height
     * as the message text paint lays lines out, and the text's inset in the bubble: the widget spans the
     * bubble, the message text starts {@link RichWidgetBlock#bleed} in from its side.
     */
    private static JSONObject textTokens() throws Exception {
        JSONObject text = new JSONObject();
        text.put("fontSize", SharedConfig.fontSize);
        TextPaint paint = Theme.chat_msgTextPaint;
        if (paint != null && paint.getTextSize() > 0) {
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float lineHeight = (metrics.descent - metrics.ascent + metrics.leading) / paint.getTextSize();
            text.put("lineHeight", Math.round(Math.max(1, Math.min(3, lineHeight)) * 100) / 100.0);
        }
        int side = Math.round(RichWidgetBlock.bleed() / AndroidUtilities.density);
        text.put("padding", "8px " + side + "px");
        return text;
    }

    /** Only the scheme: the framework's default palette for it (test screens). */
    public static String scheme(boolean dark) {
        return "{\"colorScheme\":\"" + (dark ? "dark" : "light") + "\"}";
    }

    private static final int[] GRADIENT = {Theme.key_chat_outBubbleGradient1, Theme.key_chat_outBubbleGradient2,
            Theme.key_chat_outBubbleGradient3};

    /**
     * The bubble's fill. An outgoing bubble with a gradient (MessageDrawable) has no single colour: the gradient
     * spans the chat's height and moves under the bubble as it scrolls, so the widget's area shows all of it over
     * time; the colour it shows most is their average (docs/sdk-v1.md §5.1), as Theme does for such bubbles.
     */
    private static int fill(Theme.ResourcesProvider resourcesProvider, boolean out) {
        int bubble = Theme.getColor(out ? OUT[0] : IN[0], resourcesProvider);
        if (!out || gradientColor(GRADIENT[0], resourcesProvider) == 0) return bubble;
        int r = Color.red(bubble), g = Color.green(bubble), b = Color.blue(bubble), n = 1;
        for (int key : GRADIENT) {
            int color = gradientColor(key, resourcesProvider);
            if (color == 0) continue;
            r += Color.red(color);
            g += Color.green(color);
            b += Color.blue(color);
            n++;
        }
        return Color.argb(Color.alpha(bubble), r / n, g / n, b / n);
    }

    /**
     * The one colour an outgoing bubble holding a widget is painted in when the theme's outgoing bubbles have a
     * gradient (docs/sdk-v1.md §5.1): the `bg_color` passed, so that a widget's own background meets the bubble
     * with no edge; selected, with the theme's selection overlay over it. Null: the theme's own bubble.
     */
    public static Integer solidOutBubble(Theme.ResourcesProvider resourcesProvider, boolean selected) {
        if (gradientColor(GRADIENT[0], resourcesProvider) == 0) return null;
        boolean dark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        int color = opaque(fill(resourcesProvider, true), dark);
        if (selected) color = Theme.blendOver(color, Theme.getColor(Theme.key_chat_outBubbleGradientSelectedOverlay, resourcesProvider));
        return color;
    }

    /** A gradient colour as MessageDrawable reads it: 0 when the theme has none (no default to fall back to). */
    private static int gradientColor(int key, Theme.ResourcesProvider resourcesProvider) {
        return resourcesProvider != null ? resourcesProvider.getCurrentColor(key) : Theme.getCurrentColor(key);
    }

    private static int opaque(int color, boolean dark) {
        return Theme.blendOver(dark ? 0xFF000000 : 0xFFFFFFFF, color);
    }

    private static String hex(int color, int background) {
        color = Theme.blendOver(background, color);
        return String.format(Locale.US, "#%02x%02x%02x", Color.red(color), Color.green(color), Color.blue(color));
    }
}
