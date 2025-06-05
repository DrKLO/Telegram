package org.telegram.messenger.utils;

import android.text.Spanned;
import android.text.TextUtils;

import org.telegram.messenger.CodeHighlighting;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.QuoteSpan;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.URLSpanReplacement;

public class CustomHtml {

    private CustomHtml() { }

    public static String toHtml(Spanned text) {
        StringBuilder out = new StringBuilder();
        toHTML_0_wrapQuote(out, text, 0, text.length());
        return out.toString();
    }

    private static void toHTML_0_wrapQuote(StringBuilder out, Spanned text, int start, int end) {

        int next;
        for (int i = start; i < end; i = next) {
            next = text.nextSpanTransition(i, end, QuoteSpan.class);
            if (next < 0) {
                next = end;
            }
            QuoteSpan[] spans = text.getSpans(i, next, QuoteSpan.class);

            if (spans != null) {
                for (int j = 0; j < spans.length; ++j) {
                    out.append(spans[j].isCollapsing ? "<details>" : "<blockquote>");
                }
            }

            toHTML_1_wrapTextStyle(out, text, i, next);

            if (spans != null) {
                for (int j = spans.length - 1; j >= 0; --j) {
                    out.append(spans[j].isCollapsing ? "</details>" : "</blockquote>");
                }
            }
        }
    }

    private static void toHTML_1_wrapTextStyle(StringBuilder out, Spanned text, int start, int end) {
        int next;
        for (int i = start; i < end; i = next) {
            next = text.nextSpanTransition(i, end, TextStyleSpan.class);
            if (next < 0) {
                next = end;
            }
            TextStyleSpan[] spans = text.getSpans(i, next, TextStyleSpan.class);

            if (spans != null) {
                for (int j = 0; j < spans.length; ++j) {
                    Object spanObject = spans[j];
                    if (spanObject != null) {
                        TextStyleSpan span = (TextStyleSpan) spanObject;
                        int flags = span.getStyleFlags();
                        if ((flags & (TextStyleSpan.FLAG_STYLE_SPOILER | TextStyleSpan.FLAG_STYLE_SPOILER_REVEALED)) > 0) {
                            out.append("<spoiler>");
                        }
                        if ((flags & TextStyleSpan.FLAG_STYLE_BOLD) > 0) {
                            out.append("<b>");
                        }
                        if ((flags & TextStyleSpan.FLAG_STYLE_ITALIC) > 0) {
                            out.append("<i>");
                        }
                        if ((flags & TextStyleSpan.FLAG_STYLE_UNDERLINE) > 0) {
                            out.append("<u>");
                        }
                        if ((flags & TextStyleSpan.FLAG_STYLE_STRIKE) > 0) {
                            out.append("<s>");
                        }
                        if ((flags & TextStyleSpan.FLAG_STYLE_URL) > 0) {
                            if (span.getTextStyleRun() != null && span.getTextStyleRun().urlEntity != null) {
                                out.append("<a href=\"").append(span.getTextStyleRun().urlEntity.url).append("\">");
                            }
                        }
                    } else if (spanObject instanceof URLSpanMono) {
                        out.append("<pre>");
                    }
                }
            }

            toHTML_2_wrapURLReplacements(out, text, i, next);

            if (spans != null) {
                for (int j = 0; j < spans.length; ++j) {
                    TextStyleSpan spanObject = spans[j];
                    if (spanObject != null) {
                        TextStyleSpan span = (TextStyleSpan) spanObject;
                        int flags = span.getStyleFlags();
                        if ((flags & TextStyleSpan.FLAG_STYLE_URL) > 0 && span.getTextStyleRun() != null && span.getTextStyleRun().urlEntity != null) {
                            out.append("</a>");
                        }
                        if ((flags & TextStyleSpan.FLAG_STYLE_STRIKE) > 0) {
                            out.append("</s>");
                        }
                        if ((flags & TextStyleSpan.FLAG_STYLE_UNDERLINE) > 0) {
                            out.append("</u>");
                        }
                        if ((flags & TextStyleSpan.FLAG_STYLE_ITALIC) > 0) {
                            out.append("</i>");
                        }
                        if ((flags & TextStyleSpan.FLAG_STYLE_BOLD) > 0) {
                            out.append("</b>");
                        }
                        if ((flags & (TextStyleSpan.FLAG_STYLE_SPOILER | TextStyleSpan.FLAG_STYLE_SPOILER_REVEALED)) > 0) {
                            out.append("</spoiler>");
                        }
                    }
                }
            }
        }
    }

    private static void toHTML_2_wrapURLReplacements(StringBuilder out, Spanned text, int start, int end) {
        int next;
        for (int i = start; i < end; i = next) {
            next = text.nextSpanTransition(i, end, URLSpanReplacement .class);
            if (next < 0) {
                next = end;
            }
            URLSpanReplacement[] spans = text.getSpans(i, next, URLSpanReplacement.class);

            if (spans != null) {
                for (int j = 0; j < spans.length; ++j) {
                    URLSpanReplacement span = spans[j];
                    out.append("<a href=\"").append(span.getURL()).append("\">");
                }
            }

            toHTML_3_wrapMonoscape(out, text, i, next);

            if (spans != null) {
                for (int j = 0; j < spans.length; ++j) {
                    out.append("</a>");
                }
            }
        }
    }

    private static void toHTML_3_wrapMonoscape(StringBuilder out, Spanned text, int start, int end) {

        int next;
        for (int i = start; i < end; i = next) {
            next = text.nextSpanTransition(i, end, URLSpanMono.class);
            if (next < 0) {
                next = end;
            }
            URLSpanMono[] spans = text.getSpans(i, next, URLSpanMono.class);

            if (spans != null) {
                for (int j = 0; j < spans.length; ++j) {
                    URLSpanMono span = spans[j];
                    if (span != null) {
                        out.append("<pre>");
                    }
                }
            }

            toHTML_4_wrapMonoscape2(out, text, i, next);

            if (spans != null) {
                for (int j = 0; j < spans.length; ++j) {
                    URLSpanMono span = spans[j];
                    if (span != null) {
                        out.append("</pre>");
                    }
                }
            }
        }
    }

    private static void toHTML_4_wrapMonoscape2(StringBuilder out, Spanned text, int start, int end) {

        int next;
        for (int i = start; i < end; i = next) {
            next = text.nextSpanTransition(i, end, CodeHighlighting.Span.class);
            if (next < 0) {
                next = end;
            }
            CodeHighlighting.Span[] spans = text.getSpans(i, next, CodeHighlighting.Span.class);

            if (spans != null) {
                for (int j = 0; j < spans.length; ++j) {
                    CodeHighlighting.Span span = spans[j];
                    if (span != null) {
                        if (TextUtils.isEmpty(span.lng)) {
                            out.append("<pre>");
                        } else {
                            out.append("<pre lang=\"").append(span.lng).append("\">");
                        }
                    }
                }
            }

            toHTML_6_wrapAnimatedEmoji(out, text, i, next);

            if (spans != null) {
                for (int j = 0; j < spans.length; ++j) {
                    CodeHighlighting.Span span = spans[j];
                    if (span != null) {
                        out.append("</pre>");
                    }
                }
            }
        }
    }

    private static void toHTML_6_wrapAnimatedEmoji(StringBuilder out, Spanned text, int start, int end) {
        int next;
        for (int i = start; i < end; i = next) {
            next = text.nextSpanTransition(i, end, AnimatedEmojiSpan.class);
            if (next < 0) {
                next = end;
            }
            AnimatedEmojiSpan[] spans = text.getSpans(i, next, AnimatedEmojiSpan.class);

            if (spans != null) {
                for (int j = 0; j < spans.length; ++j) {
                    AnimatedEmojiSpan span = spans[j];
                    if (span != null && !span.standard) {
                        out.append("<animated-emoji data-document-id=\"" + span.documentId + "\">");
                    }
                }
            }

            toHTML_7_withinStyle(out, text, i, next);

            if (spans != null) {
                for (int j = 0; j < spans.length; ++j) {
                    AnimatedEmojiSpan span = spans[j];
                    if (span != null && !span.standard) {
                        out.append("</animated-emoji>");
                    }
                }
            }
        }
    }

    private static void toHTML_7_withinStyle(StringBuilder out, CharSequence text, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);

            if (c == '\n') {
                out.append("<br>");
            } else if (c == '<') {
                out.append("&lt;");
            } else if (c == '>') {
                out.append("&gt;");
            } else if (c == '&') {
                out.append("&amp;");
            } else if (c >= 0xD800 && c <= 0xDFFF) {
                if (c < 0xDC00 && i + 1 < end) {
                    char d = text.charAt(i + 1);
                    if (d >= 0xDC00 && d <= 0xDFFF) {
                        i++;
                        int codepoint = 0x010000 | (int) c - 0xD800 << 10 | (int) d - 0xDC00;
                        out.append("&#").append(codepoint).append(";");
                    }
                }
            } else if (c > 0x7E || c < ' ') {
                out.append("&#").append((int) c).append(";");
            } else if (c == ' ') {
                while (i + 1 < end && text.charAt(i + 1) == ' ') {
                    out.append("&nbsp;");
                    i++;
                }

                out.append(' ');
            } else {
                out.append(c);
            }
        }
    }
}
