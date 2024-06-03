package org.telegram.messenger.utils;

import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

import org.telegram.messenger.CodeHighlighting;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaDataController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.QuoteSpan;
import org.telegram.ui.Components.URLSpanReplacement;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.util.ArrayDeque;
import java.util.ArrayList;

public class CopyUtilities {

    private final static int TYPE_SPOILER = 0;
    private final static int TYPE_MONO = 1;
    private final static int TYPE_QUOTE = 2;
    private final static int TYPE_COLLAPSE = 3;

    public static Spannable fromHTML(String html) {
        Spanned spanned;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                spanned = Html.fromHtml("<inject>" + html + "</inject>", Html.FROM_HTML_MODE_COMPACT, null, new HTMLTagAttributesHandler(new HTMLTagHandler()));
            } else {
                spanned = Html.fromHtml("<inject>" + html + "</inject>", null, new HTMLTagAttributesHandler(new HTMLTagHandler()));
            }
        } catch (Exception e) {
            FileLog.e("Html.fromHtml", e);
            return null;
        }
        if (spanned == null) {
            return null;
        }

        Object[] spans = spanned.getSpans(0, spanned.length(), Object.class);
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>(spans.length);
        ArrayList<ParsedSpan> codes = new ArrayList<>();
        ArrayList<ParsedSpan> quotes = new ArrayList<>();
        for (int i = 0; i < spans.length; ++i) {
            Object span = spans[i];
            int start = spanned.getSpanStart(span);
            int end = spanned.getSpanEnd(span);
            if (span instanceof StyleSpan) {
                int style = ((StyleSpan) span).getStyle();
                if ((style & Typeface.BOLD) > 0) {
                    entities.add(setEntityStartEnd(new TLRPC.TL_messageEntityBold(), start, end));
                }
                if ((style & Typeface.ITALIC) > 0) {
                    entities.add(setEntityStartEnd(new TLRPC.TL_messageEntityItalic(), start, end));
                }
            } else if (span instanceof UnderlineSpan) {
                entities.add(setEntityStartEnd(new TLRPC.TL_messageEntityUnderline(), start, end));
            } else if (span instanceof StrikethroughSpan) {
                entities.add(setEntityStartEnd(new TLRPC.TL_messageEntityStrike(), start, end));
            } else if (span instanceof ParsedSpan) {
                ParsedSpan parsedSpan = (ParsedSpan) span;
                if (parsedSpan.type == TYPE_SPOILER) {
                    entities.add(setEntityStartEnd(new TLRPC.TL_messageEntitySpoiler(), start, end));
                } else if (parsedSpan.type == TYPE_MONO) {
                    if (!TextUtils.isEmpty(parsedSpan.lng)) {
                        codes.add(parsedSpan);
                    } else {
                        entities.add(setEntityStartEnd(new TLRPC.TL_messageEntityPre(), start, end));
                    }
                } else if (parsedSpan.type == TYPE_QUOTE || parsedSpan.type == TYPE_COLLAPSE) {
                    quotes.add(parsedSpan);
                }
            } else if (span instanceof AnimatedEmojiSpan) {
                TLRPC.TL_messageEntityCustomEmoji entity = new TLRPC.TL_messageEntityCustomEmoji();
                entity.document_id = ((AnimatedEmojiSpan) span).documentId;
                entity.document = ((AnimatedEmojiSpan) span).document;
                entities.add(setEntityStartEnd(entity, start, end));
            }
        }

        SpannableStringBuilder spannable = new SpannableStringBuilder(spanned.toString());
        MediaDataController.addTextStyleRuns(entities, spannable, spannable);
        for (int i = 0; i < spans.length; ++i) {
            Object span = spans[i];
            if (span instanceof URLSpan) {
                int start = spanned.getSpanStart(span);
                int end = spanned.getSpanEnd(span);
                String text = spanned.subSequence(start, end).toString();
                String url = ((URLSpan) span).getURL();
                if (text.equals(url)) {
                    spannable.setSpan(new URLSpan(url), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    spannable.setSpan(new URLSpanReplacement(url), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        MediaDataController.addAnimatedEmojiSpans(entities, spannable, null);
        for (int i = 0; i < codes.size(); ++i) {
            ParsedSpan span = codes.get(i);
            final int start = spanned.getSpanStart(span);
            final int end = spanned.getSpanEnd(span);
            spannable.setSpan(new CodeHighlighting.Span(true, 0, null, span.lng, spannable.subSequence(start, end).toString()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//            CodeHighlighting.highlight(
//                spannable,
//                spanned.getSpanStart(span),
//                spanned.getSpanEnd(span),
//                span.lng,
//                0,
//                null,
//                false
//            );
        }
        for (int i = 0; i < quotes.size(); ++i) {
            ParsedSpan span = quotes.get(i);
            QuoteSpan.putQuoteToEditable(spannable, spanned.getSpanStart(span), spanned.getSpanEnd(span), span.type == TYPE_COLLAPSE);
        }
        return spannable;
    }

    private static TLRPC.MessageEntity setEntityStartEnd(TLRPC.MessageEntity entity, int spanStart, int spanEnd) {
        entity.offset = spanStart;
        entity.length = spanEnd - spanStart;
        return entity;
    }

    public static class HTMLTagAttributesHandler implements Html.TagHandler, ContentHandler {
        public interface TagHandler {
            boolean handleTag(boolean opening, String tag, Editable output, Attributes attributes);
        }

        public static String getValue(Attributes attributes, String name) {
            for (int i = 0, n = attributes.getLength(); i < n; ++i) {
                if (name.equals(attributes.getLocalName(i))) {
                    return attributes.getValue(i);
                }
            }
            return null;
        }

        private final TagHandler handler;
        private ContentHandler wrapped;
        private Editable text;
        private ArrayDeque<Boolean> tagStatus = new ArrayDeque<>();

        private HTMLTagAttributesHandler(TagHandler handler) {
            this.handler = handler;
        }

        @Override
        public void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader) {
            if (wrapped == null) {
                text = output;
                wrapped = xmlReader.getContentHandler();
                xmlReader.setContentHandler(this);
                tagStatus.addLast(Boolean.FALSE);
            }
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            boolean isHandled = handler.handleTag(true, localName, text, attributes);
            tagStatus.addLast(isHandled);
            if (!isHandled) {
                wrapped.startElement(uri, localName, qName, attributes);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (!tagStatus.removeLast()) {
                wrapped.endElement(uri, localName, qName);
            }
            handler.handleTag(false, localName, text, null);
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            wrapped.setDocumentLocator(locator);
        }

        @Override
        public void startDocument() throws SAXException {
            wrapped.startDocument();
        }

        @Override
        public void endDocument() throws SAXException {
            wrapped.endDocument();
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            wrapped.startPrefixMapping(prefix, uri);
        }

        @Override
        public void endPrefixMapping(String prefix) throws SAXException {
            wrapped.endPrefixMapping(prefix);
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            wrapped.characters(ch, start, length);
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
            wrapped.ignorableWhitespace(ch, start, length);
        }

        @Override
        public void processingInstruction(String target, String data) throws SAXException {
            wrapped.processingInstruction(target, data);
        }

        @Override
        public void skippedEntity(String name) throws SAXException {
            wrapped.skippedEntity(name);
        }
    }

    private static class HTMLTagHandler implements HTMLTagAttributesHandler.TagHandler {

        @Override
        public boolean handleTag(boolean opening, String tag, Editable output, Attributes attributes) {
            if (tag.startsWith("animated-emoji")) {
                if (opening) {
                    String documentIdString = HTMLTagAttributesHandler.getValue(attributes, "data-document-id");
                    if (documentIdString != null) {
                        long documentId = Long.parseLong(documentIdString);
                        output.setSpan(new AnimatedEmojiSpan(documentId, null), output.length(), output.length(), Spanned.SPAN_MARK_MARK);
                        return true;
                    }
                } else {
                    AnimatedEmojiSpan obj = getLast(output, AnimatedEmojiSpan.class);
                    if (obj != null) {
                        int where = output.getSpanStart(obj);
                        output.removeSpan(obj);
                        if (where != output.length()) {
                            output.setSpan(obj, where, output.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        return true;
                    }
                }
            } else if (tag.equals("spoiler")) {
                if (opening) {
                    output.setSpan(new ParsedSpan(TYPE_SPOILER), output.length(), output.length(), Spanned.SPAN_MARK_MARK);
                    return true;
                } else {
                    ParsedSpan obj = getLast(output, ParsedSpan.class, TYPE_SPOILER);
                    if (obj != null) {
                        int where = output.getSpanStart(obj);
                        output.removeSpan(obj);
                        if (where != output.length()) {
                            output.setSpan(obj, where, output.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        return true;
                    }
                }
            } else if (tag.equals("pre")) {
                if (opening) {
                    String lang = HTMLTagAttributesHandler.getValue(attributes, "lang");
                    output.setSpan(new ParsedSpan(TYPE_MONO, lang), output.length(), output.length(), Spanned.SPAN_MARK_MARK);
                    return true;
                } else {
                    ParsedSpan obj = getLast(output, ParsedSpan.class, TYPE_MONO);
                    if (obj != null) {
                        int where = output.getSpanStart(obj);
                        output.removeSpan(obj);
                        if (where != output.length()) {
                            output.setSpan(obj, where, output.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        return true;
                    }
                }
            } else if (tag.equals("blockquote")) {
                if (opening) {
                    output.setSpan(new ParsedSpan(TYPE_QUOTE), output.length(), output.length(), Spanned.SPAN_MARK_MARK);
                    return true;
                } else {
                    ParsedSpan obj = getLast(output, ParsedSpan.class, TYPE_QUOTE);
                    if (obj != null) {
                        int where = output.getSpanStart(obj);
                        output.removeSpan(obj);
                        if (where != output.length()) {
                            output.setSpan(obj, where, output.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        return true;
                    }
                }
            } else if (tag.equals("details")) {
                if (opening) {
                    output.setSpan(new ParsedSpan(TYPE_COLLAPSE), output.length(), output.length(), Spanned.SPAN_MARK_MARK);
                    return true;
                } else {
                    ParsedSpan obj = getLast(output, ParsedSpan.class, TYPE_COLLAPSE);
                    if (obj != null) {
                        int where = output.getSpanStart(obj);
                        output.removeSpan(obj);
                        if (where != output.length()) {
                            output.setSpan(obj, where, output.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        return true;
                    }
                }
            }
            return false;
        }

        private <T> T getLast(Editable text, Class<T> kind) {
            T[] objs = text.getSpans(0, text.length(), kind);
            if (objs.length == 0) {
                return null;
            } else {
                for (int i = objs.length; i > 0; i--) {
                    if (text.getSpanFlags(objs[i - 1]) == Spannable.SPAN_MARK_MARK) {
                        return objs[i - 1];
                    }
                }
                return null;
            }
        }

        private <T extends ParsedSpan> T getLast(Editable text, Class<T> kind, int type) {
            T[] objs = text.getSpans(0, text.length(), kind);
            if (objs.length == 0) {
                return null;
            } else {
                for (int i = objs.length; i > 0; i--) {
                    if (text.getSpanFlags(objs[i - 1]) == Spannable.SPAN_MARK_MARK && objs[i - 1].type == type) {
                        return objs[i - 1];
                    }
                }
                return null;
            }
        }
    }

    private static class ParsedSpan {
        final int type;
        final String lng;

        private ParsedSpan(int type) {
            this.type = type;
            this.lng = null;
        }
        private ParsedSpan(int type, String lng) {
            this.type = type;
            this.lng = lng;
        }
    }
}
