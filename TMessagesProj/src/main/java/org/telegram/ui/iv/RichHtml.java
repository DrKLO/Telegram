package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;

import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.URLSpanReplacement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RichHtml {

    public static String serialize(List<BlockRow> rows, int from, int to, int sOff, int eOff) {
        return serialize(rows, from, to, sOff, eOff, null);
    }

    public static String serialize(List<BlockRow> rows, int from, int to, int sOff, int eOff,
                                   Map<Long, TL_iv.RichText> authors) {
        StringBuilder out = new StringBuilder();
        ListState ls = new ListState();
        int[] i = { from };
        serializeRange(out, rows, i, to, from, to, sOff, eOff, ls, false, 0, authors);
        ls.closeAll(out);
        return out.toString();
    }

    private static class ListState {
        // stack of open list levels: true = ordered
        final ArrayList<Boolean> stack = new ArrayList<>();

        void sync(StringBuilder out, int level, boolean ordered) {
            while (stack.size() > level) close(out);
            while (stack.size() < level) open(out, ordered);
            if (!stack.isEmpty()) {
                int top = stack.size() - 1;
                if (stack.get(top) != ordered) {
                    close(out);
                    open(out, ordered);
                }
            }
        }

        private void open(StringBuilder out, boolean ordered) {
            out.append(ordered ? "<ol>" : "<ul>");
            stack.add(ordered);
        }

        private void close(StringBuilder out) {
            boolean ordered = stack.remove(stack.size() - 1);
            out.append(ordered ? "</ol>" : "</ul>");
        }

        void closeAll(StringBuilder out) {
            while (!stack.isEmpty()) close(out);
        }
    }

    private static void serializeRange(StringBuilder out, List<BlockRow> rows, int[] i, int end,
                                       int selFrom, int selTo, int sOff, int eOff, ListState ls, boolean inDetails, int quoteDepth,
                                       Map<Long, TL_iv.RichText> authors) {
        while (i[0] <= end) {
            BlockRow row = rows.get(i[0]);
            if (row.detailsEnd) {
                if (inDetails) {
                    return;
                }
                i[0]++;
                continue;
            }
            if (row.quoteIds.size() > quoteDepth) {
                ls.closeAll(out);
                serializeQuote(out, rows, i, end, selFrom, selTo, sOff, eOff, inDetails, quoteDepth, authors);
                continue;
            }
            if (RichEditorListView.isDetailsHeader(row)) {
                ls.closeAll(out);
                serializeDetails(out, rows, i, end, selFrom, selTo, sOff, eOff, quoteDepth, authors);
                continue;
            }
            if (row.level > 0 && isTextBlock(row.block)) {
                ls.sync(out, row.level, row.num > 0);
                out.append("<li>");
                appendInline(out, slicedStyled(row, i[0], selFrom, selTo, sOff, eOff));
                out.append("</li>");
                i[0]++;
                continue;
            }
            ls.closeAll(out);
            serializeLeaf(out, row, i[0], selFrom, selTo, sOff, eOff);
            i[0]++;
        }
    }

    private static void serializeDetails(StringBuilder out, List<BlockRow> rows, int[] i, int end,
                                         int selFrom, int selTo, int sOff, int eOff, int quoteDepth,
                                         Map<Long, TL_iv.RichText> authors) {
        BlockRow header = rows.get(i[0]);
        TL_iv.pageBlockDetails details = (TL_iv.pageBlockDetails) header.block;
        out.append(details.open ? "<details open>" : "<details>");
        out.append("<summary>");
        appendInline(out, slicedStyled(header, i[0], selFrom, selTo, sOff, eOff));
        out.append("</summary>");
        i[0]++;
        ListState inner = new ListState();
        serializeRange(out, rows, i, end, selFrom, selTo, sOff, eOff, inner, true, quoteDepth, authors);
        inner.closeAll(out);
        if (i[0] <= end && i[0] < rows.size() && rows.get(i[0]).detailsEnd) {
            i[0]++;
        }
        out.append("</details>");
    }

    private static void serializeQuote(StringBuilder out, List<BlockRow> rows, int[] i, int end,
                                       int selFrom, int selTo, int sOff, int eOff, boolean inDetails, int quoteDepth,
                                       Map<Long, TL_iv.RichText> authors) {
        long qid = rows.get(i[0]).quoteIds.get(quoteDepth);
        int runEnd = i[0];
        while (runEnd + 1 <= end) {
            BlockRow re = rows.get(runEnd + 1);
            if (re.quoteIds.size() > quoteDepth && re.quoteIds.get(quoteDepth) == qid) runEnd++;
            else break;
        }
        out.append("<blockquote>");
        ListState inner = new ListState();
        serializeRange(out, rows, i, runEnd, selFrom, selTo, sOff, eOff, inner, inDetails, quoteDepth + 1, authors);
        inner.closeAll(out);
        appendAuthorCite(out, authors == null ? null : authorText(authors.get(qid)));
        out.append("</blockquote>");
    }

    private static CharSequence authorText(TL_iv.RichText author) {
        if (author == null || author instanceof TL_iv.textEmpty) return null;
        CharSequence cs = RichTextStyle.toSpannable(author);
        return cs != null && cs.length() > 0 ? cs : null;
    }

    private static void appendAuthorCite(StringBuilder out, CharSequence author) {
        if (author == null || author.length() == 0) return;
        out.append("<cite>");
        appendInline(out, author);
        out.append("</cite>");
    }

    private static void serializeLeaf(StringBuilder out, BlockRow row, int index,
                                      int selFrom, int selTo, int sOff, int eOff) {
        TL_iv.PageBlock b = row.block;
        if (b instanceof TL_iv.pageBlockDivider) {
            out.append("<hr>");
            return;
        }
        if (b instanceof TL_iv.pageBlockTable) {
            serializeTable(out, (TL_iv.pageBlockTable) b);
            return;
        }
        if (b instanceof TL_iv.pageBlockPhoto) {
            serializeSingleMedia(out, "img", ((TL_iv.pageBlockPhoto) b).photo_id, row.media, b);
            return;
        }
        if (b instanceof TL_iv.pageBlockVideo) {
            serializeSingleMedia(out, "video", ((TL_iv.pageBlockVideo) b).video_id, row.media, b);
            return;
        }
        if (b instanceof TL_iv.pageBlockAudio) {
            serializeSingleMedia(out, "audio", ((TL_iv.pageBlockAudio) b).audio_id, row.media, b);
            return;
        }
        if (RichEditorListView.isGallery(b)) {
            serializeGallery(out, b, row);
            return;
        }
        if (b instanceof TL_iv.pageBlockMap) {
            serializeMap(out, (TL_iv.pageBlockMap) b);
            return;
        }
        String tag = blockTag(b);
        if (tag == null) {
            CharSequence cap = captionOf(b);
            if (cap != null && cap.length() > 0) {
                out.append("<p>");
                appendInline(out, cap);
                out.append("</p>");
            }
            return;
        }
        if (b instanceof TL_iv.pageBlockPreformatted) {
            String lang = ((TL_iv.pageBlockPreformatted) b).language;
            if (!TextUtils.isEmpty(lang)) {
                out.append("<pre language=\"").append(escapeAttr(lang)).append("\">");
            } else {
                out.append("<pre>");
            }
            appendInline(out, slicedStyled(row, index, selFrom, selTo, sOff, eOff));
            out.append("</pre>");
            return;
        }
        if (b instanceof TL_iv.pageBlockPullquote) {
            out.append("<blockquote class=\"pull\">");
            appendInline(out, slicedStyled(row, index, selFrom, selTo, sOff, eOff));
            appendAuthorCite(out, authorText(((TL_iv.pageBlockPullquote) b).caption));
            out.append("</blockquote>");
            return;
        }
        if (b instanceof TL_iv.pageBlockBlockquote) {
            out.append("<blockquote>");
            appendInline(out, slicedStyled(row, index, selFrom, selTo, sOff, eOff));
            appendAuthorCite(out, authorText(((TL_iv.pageBlockBlockquote) b).caption));
            out.append("</blockquote>");
            return;
        }
        out.append('<').append(tag).append('>');
        appendInline(out, slicedStyled(row, index, selFrom, selTo, sOff, eOff));
        out.append("</").append(tag).append('>');
    }

    private static CharSequence slicedStyled(BlockRow row, int index, int selFrom, int selTo, int sOff, int eOff) {
        CharSequence styled;
        if (RichEditorListView.isDetailsHeader(row)) {
            styled = RichTextStyle.toSpannable(((TL_iv.pageBlockDetails) row.block).title);
        } else {
            styled = RichTextCell.readStyledText(row.block);
        }
        if (styled == null) styled = "";
        int len = styled.length();
        int s = index == selFrom ? Math.max(0, Math.min(sOff, len)) : 0;
        int e = index == selTo ? Math.max(0, Math.min(eOff, len)) : len;
        if (s > e) { int t = s; s = e; e = t; }
        if (s == 0 && e == len) return styled;
        return styled.subSequence(s, e);
    }

    private static boolean isTextBlock(TL_iv.PageBlock b) {
        return blockTag(b) != null;
    }

    private static String blockTag(TL_iv.PageBlock b) {
        if (b instanceof TL_iv.pageBlockHeading1) return "h1";
        if (b instanceof TL_iv.pageBlockHeading2) return "h2";
        if (b instanceof TL_iv.pageBlockHeading3) return "h3";
        if (b instanceof TL_iv.pageBlockHeading4) return "h4";
        if (b instanceof TL_iv.pageBlockHeading5) return "h5";
        if (b instanceof TL_iv.pageBlockHeading6) return "h6";
        if (b instanceof TL_iv.pageBlockBlockquote) return "blockquote";
        if (b instanceof TL_iv.pageBlockPullquote) return "blockquote";
        if (b instanceof TL_iv.pageBlockPreformatted) return "pre";
        if (b instanceof TL_iv.pageBlockFooter) return "footer";
        if (b instanceof TL_iv.pageBlockParagraph) return "p";
        return null;
    }

    private static CharSequence captionOf(TL_iv.PageBlock b) {
        if (b == null || b.caption == null || b.caption.text == null) return null;
        CharSequence cs = RichTextStyle.toSpannable(b.caption.text);
        return cs != null && cs.length() > 0 ? cs : null;
    }

    private static void serializeTable(StringBuilder out, TL_iv.pageBlockTable t) {
        out.append("<table");
        if (t.bordered) out.append(" border=\"1\"");
        if (t.striped) out.append(" class=\"striped\"");
        out.append('>');
        CharSequence title = t.title != null ? RichTextStyle.toSpannable(t.title) : null;
        if (title != null && title.length() > 0) {
            out.append("<caption>");
            appendInline(out, title);
            out.append("</caption>");
        }
        if (t.rows != null) {
            for (TL_iv.pageTableRow r : t.rows) {
                out.append("<tr>");
                if (r != null && r.cells != null) {
                    for (TL_iv.pageTableCell c : r.cells) {
                        if (c == null) continue;
                        String tag = c.header ? "th" : "td";
                        out.append('<').append(tag);
                        int cs = c.colspan > 1 ? c.colspan : 0;
                        if (cs > 0) out.append(" colspan=\"").append(cs).append('"');
                        int rs = c.rowspan > 1 ? c.rowspan : 0;
                        if (rs > 0) out.append(" rowspan=\"").append(rs).append('"');
                        String al = c.align_right ? "right" : (c.align_center ? "center" : null);
                        if (al != null) out.append(" align=\"").append(al).append('"');
                        String va = c.valign_bottom ? "bottom" : (c.valign_middle ? "middle" : null);
                        if (va != null) out.append(" valign=\"").append(va).append('"');
                        out.append('>');
                        appendInline(out, TableModel.readStyledText(c));
                        out.append("</").append(tag).append('>');
                    }
                }
                out.append("</tr>");
            }
        }
        out.append("</table>");
    }

    private static void serializeSingleMedia(StringBuilder out, String tag, long id, MediaUploadState media, TL_iv.PageBlock b) {
        if (id == 0) return;
        CharSequence cap = captionOf(b);
        boolean fig = cap != null && cap.length() > 0;
        if (fig) out.append("<figure>");
        appendMediaTag(out, tag, id, media, b);
        if (fig) {
            out.append("<figcaption>");
            appendInline(out, cap);
            out.append("</figcaption></figure>");
        }
    }

    private static void appendMediaTag(StringBuilder out, String tag, long id, MediaUploadState media, TL_iv.PageBlock b) {
        out.append('<').append(tag).append(" src=\"").append(id).append('"');
        if (media != null) {
            if (media.width > 0) out.append(" width=\"").append(media.width).append('"');
            if (media.height > 0) out.append(" height=\"").append(media.height).append('"');
        }
        if (b instanceof TL_iv.pageBlockPhoto && ((TL_iv.pageBlockPhoto) b).spoiler) out.append(" data-spoiler=\"1\"");
        if (b instanceof TL_iv.pageBlockVideo && ((TL_iv.pageBlockVideo) b).spoiler) out.append(" data-spoiler=\"1\"");
        out.append(" />");
    }

    private static void serializeGallery(StringBuilder out, TL_iv.PageBlock b, BlockRow row) {
        String cls = b instanceof TL_iv.pageBlockSlideshow ? "slideshow" : "collage";
        out.append("<div class=\"").append(cls).append("\">");
        ArrayList<TL_iv.PageBlock> items = RichEditorListView.galleryItems(b);
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                TL_iv.PageBlock item = items.get(i);
                MediaUploadState m = row.medias != null && i < row.medias.size() ? row.medias.get(i) : null;
                if (item instanceof TL_iv.pageBlockVideo) {
                    long id = ((TL_iv.pageBlockVideo) item).video_id;
                    if (id != 0) appendMediaTag(out, "video", id, m, item);
                } else if (item instanceof TL_iv.pageBlockPhoto) {
                    long id = ((TL_iv.pageBlockPhoto) item).photo_id;
                    if (id != 0) appendMediaTag(out, "img", id, m, item);
                }
            }
        }
        CharSequence cap = captionOf(b);
        if (cap != null && cap.length() > 0) {
            out.append("<figcaption>");
            appendInline(out, cap);
            out.append("</figcaption>");
        }
        out.append("</div>");
    }

    private static void serializeMap(StringBuilder out, TL_iv.pageBlockMap m) {
        CharSequence cap = captionOf(m);
        boolean fig = cap != null && cap.length() > 0;
        if (fig) out.append("<figure>");
        out.append("<location");
        if (m.geo != null) {
            out.append(" lat=\"").append(m.geo.lat).append('"');
            out.append(" long=\"").append(m.geo._long).append('"');
            if (m.geo.access_hash != 0) out.append(" access=\"").append(m.geo.access_hash).append('"');
        }
        if (m.zoom != 0) out.append(" zoom=\"").append(m.zoom).append('"');
        if (m.w != 0) out.append(" w=\"").append(m.w).append('"');
        if (m.h != 0) out.append(" h=\"").append(m.h).append('"');
        out.append(" />");
        if (fig) {
            out.append("<figcaption>");
            appendInline(out, cap);
            out.append("</figcaption></figure>");
        }
    }

    public static String inlineToHtml(CharSequence styled) {
        StringBuilder out = new StringBuilder();
        appendInline(out, styled);
        return out.toString();
    }

    public static String preToHtml(CharSequence styled, String language) {
        final String inner = inlineToHtml(styled);
        if (inner.isEmpty()) return "";
        final StringBuilder out = new StringBuilder();
        if (!TextUtils.isEmpty(language)) {
            out.append("<pre language=\"").append(escapeAttr(language)).append("\">");
        } else {
            out.append("<pre>");
        }
        out.append(inner).append("</pre>");
        return out.toString();
    }

    public static String tableToHtml(TL_iv.pageBlockTable t) {
        if (t == null) return "";
        StringBuilder out = new StringBuilder();
        serializeTable(out, t);
        return out.toString();
    }

    private static void appendInline(StringBuilder out, CharSequence cs) {
        if (cs == null || cs.length() == 0) return;
        if (!(cs instanceof Spanned)) {
            escape(out, cs, 0, cs.length());
            return;
        }
        Spanned sp = (Spanned) cs;
        int n = cs.length();
        for (int pos = 0; pos < n; ) {
            int next = sp.nextSpanTransition(pos, n, CharacterStyle.class);
            int flags = 0;
            for (TextStyleSpan span : sp.getSpans(pos, next, TextStyleSpan.class)) {
                TextStyleSpan.TextStyleRun run = span.getTextStyleRun();
                if (run != null) flags |= run.flags;
            }
            String url = null;
            URLSpanReplacement[] urls = sp.getSpans(pos, next, URLSpanReplacement.class);
            if (urls.length > 0) url = urls[0].getURL();
            long emojiId = 0;
            AnimatedEmojiSpan[] emoji = sp.getSpans(pos, next, AnimatedEmojiSpan.class);
            if (emoji.length > 0 && !emoji[0].standard) emojiId = emoji[0].getDocumentId();

            openInline(out, flags, url, emojiId);
            escape(out, cs, pos, next);
            closeInline(out, flags, url, emojiId);
            pos = next;
        }
    }

    private static void openInline(StringBuilder out, int flags, String url, long emojiId) {
        if ((flags & TextStyleSpan.FLAG_STYLE_SPOILER) != 0) out.append("<spoiler>");
        if ((flags & TextStyleSpan.FLAG_STYLE_BOLD) != 0) out.append("<b>");
        if ((flags & TextStyleSpan.FLAG_STYLE_ITALIC) != 0) out.append("<i>");
        if ((flags & TextStyleSpan.FLAG_STYLE_UNDERLINE) != 0) out.append("<u>");
        if ((flags & TextStyleSpan.FLAG_STYLE_STRIKE) != 0) out.append("<s>");
        if ((flags & TextStyleSpan.FLAG_STYLE_MONO) != 0) out.append("<code>");
        if ((flags & TextStyleSpan.FLAG_STYLE_SUBSCRIPT) != 0) out.append("<sub>");
        if ((flags & TextStyleSpan.FLAG_STYLE_SUPERSCRIPT) != 0) out.append("<sup>");
        if ((flags & TextStyleSpan.FLAG_STYLE_MARKED) != 0) out.append("<mark>");
        if (url != null) out.append("<a href=\"").append(escapeAttr(url)).append("\">");
        if (emojiId != 0) out.append("<animated-emoji data-document-id=\"").append(emojiId).append("\">");
    }

    private static void closeInline(StringBuilder out, int flags, String url, long emojiId) {
        if (emojiId != 0) out.append("</animated-emoji>");
        if (url != null) out.append("</a>");
        if ((flags & TextStyleSpan.FLAG_STYLE_MARKED) != 0) out.append("</mark>");
        if ((flags & TextStyleSpan.FLAG_STYLE_SUPERSCRIPT) != 0) out.append("</sup>");
        if ((flags & TextStyleSpan.FLAG_STYLE_SUBSCRIPT) != 0) out.append("</sub>");
        if ((flags & TextStyleSpan.FLAG_STYLE_MONO) != 0) out.append("</code>");
        if ((flags & TextStyleSpan.FLAG_STYLE_STRIKE) != 0) out.append("</s>");
        if ((flags & TextStyleSpan.FLAG_STYLE_UNDERLINE) != 0) out.append("</u>");
        if ((flags & TextStyleSpan.FLAG_STYLE_ITALIC) != 0) out.append("</i>");
        if ((flags & TextStyleSpan.FLAG_STYLE_BOLD) != 0) out.append("</b>");
        if ((flags & TextStyleSpan.FLAG_STYLE_SPOILER) != 0) out.append("</spoiler>");
    }

    private static void escape(StringBuilder out, CharSequence text, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (c == '\n') out.append("<br>");
            else if (c == '<') out.append("&lt;");
            else if (c == '>') out.append("&gt;");
            else if (c == '&') out.append("&amp;");
            else out.append(c);
        }
    }

    private static String escapeAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static List<BlockRow> parse(String html) {
        return parse(html, null);
    }

    public static List<BlockRow> parse(String html, Map<Long, TL_iv.RichText> outAuthors) {
        ArrayList<BlockRow> rows = new ArrayList<>();
        if (html == null) return rows;
        List<Node> dom = new Parser(html).parse();
        parseBlocks(dom, rows, 0, outAuthors);
        if (rows.isEmpty()) {
            TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
            rows.add(new BlockRow(p));
        }
        return rows;
    }

    private static void parseBlocks(List<Node> nodes, ArrayList<BlockRow> rows, int level, Map<Long, TL_iv.RichText> authors) {
        SpannableStringBuilder pending = null;
        for (Node node : nodes) {
            if (node.isText) {
                if (!isBlank(node.text)) {
                    if (pending == null) pending = new SpannableStringBuilder();
                    pending.append(decode(node.text));
                }
                continue;
            }
            String tag = node.tag;
            if (isInlineTag(tag)) {
                if (pending == null) pending = new SpannableStringBuilder();
                appendInlineNode(pending, node, 0, null, 0);
                continue;
            }
            pending = flushParagraph(rows, pending, level);
            switch (tag) {
                case "p":
                    addText(rows, new TL_iv.pageBlockParagraph(), node, level);
                    break;
                case "div": {
                    String cls = node.attr("class");
                    String low = cls == null ? "" : cls.toLowerCase();
                    if (low.contains("collage")) {
                        addRow(rows, parseGallery(node, false));
                    } else if (low.contains("slideshow")) {
                        addRow(rows, parseGallery(node, true));
                    } else {
                        addText(rows, new TL_iv.pageBlockParagraph(), node, level);
                    }
                    break;
                }
                case "h1": addText(rows, new TL_iv.pageBlockHeading1(), node, level); break;
                case "h2": addText(rows, new TL_iv.pageBlockHeading2(), node, level); break;
                case "h3": addText(rows, new TL_iv.pageBlockHeading3(), node, level); break;
                case "h4": addText(rows, new TL_iv.pageBlockHeading4(), node, level); break;
                case "h5": addText(rows, new TL_iv.pageBlockHeading5(), node, level); break;
                case "h6": addText(rows, new TL_iv.pageBlockHeading6(), node, level); break;
                case "blockquote": {
                    if (hasPullClass(node)) {
                        parsePullquote(node, rows, level);
                    } else {
                        parseBlockquote(node, rows, level, authors);
                    }
                    break;
                }
                case "pre": {
                    TL_iv.pageBlockPreformatted pre = new TL_iv.pageBlockPreformatted();
                    pre.language = firstAttr(node, "language", "lang", "lng");
                    addText(rows, pre, node, level);
                    break;
                }
                case "hr":
                    rows.add(new BlockRow(new TL_iv.pageBlockDivider()));
                    break;
                case "ul":
                case "ol":
                    parseList(node, rows, level, "ol".equals(tag));
                    break;
                case "details":
                    parseDetails(node, rows, level, authors);
                    break;
                case "summary":
                    addText(rows, new TL_iv.pageBlockParagraph(), node, level);
                    break;
                case "footer":
                    addText(rows, new TL_iv.pageBlockFooter(), node, level);
                    break;
                case "table":
                    rows.add(parseTable(node));
                    break;
                case "tr":
                case "td":
                case "th":
                case "tbody":
                case "thead":
                    parseBlocks(node.children, rows, level, authors);
                    break;
                case "img":
                    addRow(rows, buildMediaRow(node));
                    break;
                case "video":
                    addRow(rows, buildMediaRow(node));
                    break;
                case "audio":
                    addRow(rows, buildMediaRow(node));
                    break;
                case "location":
                    addRow(rows, buildMediaRow(node));
                    break;
                case "figure":
                    parseFigure(node, rows, level);
                    break;
                default:
                    if (!node.children.isEmpty()) {
                        parseBlocks(node.children, rows, level, authors);
                    }
                    break;
            }
        }
        flushParagraph(rows, pending, level);
    }

    private static SpannableStringBuilder flushParagraph(ArrayList<BlockRow> rows, SpannableStringBuilder pending, int level) {
        if (pending != null && !isBlank(pending.toString())) {
            TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
            p.text = RichTextStyle.fromSpannable(trim(pending));
            rows.add(new BlockRow(p, level, 0));
        }
        return null;
    }

    private static void addText(ArrayList<BlockRow> rows, TL_iv.PageBlock block, Node node, int level) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendChildrenInline(sb, node, 0, null, 0);
        RichTextCell.applyStyledTextToBlock(block, trim(sb));
        rows.add(new BlockRow(block, level, 0));
    }

    private static void addRow(ArrayList<BlockRow> rows, BlockRow row) {
        if (row != null) rows.add(row);
    }

    private static CharSequence inlineOf(Node node) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendChildrenInline(sb, node, 0, null, 0);
        return trim(sb);
    }

    private static BlockRow parseTable(Node node) {
        TL_iv.pageBlockTable t = new TL_iv.pageBlockTable();
        t.title = new TL_iv.textEmpty();
        t.rows = new ArrayList<>();
        t.bordered = node.has("border");
        String cls = node.attr("class");
        t.striped = cls != null && cls.toLowerCase().contains("striped");
        collectTableRows(node, t);
        if (t.rows.isEmpty()) {
            TL_iv.pageTableRow r = new TL_iv.pageTableRow();
            r.cells = new ArrayList<>();
            r.cells.add(TableModel.newEmptyCell());
            t.rows.add(r);
        }
        return new BlockRow(t);
    }

    private static void collectTableRows(Node node, TL_iv.pageBlockTable t) {
        for (Node c : node.children) {
            if (c.isText) continue;
            switch (c.tag) {
                case "caption":
                    t.title = RichTextStyle.fromSpannable(inlineOf(c));
                    break;
                case "thead":
                case "tbody":
                case "tfoot":
                    collectTableRows(c, t);
                    break;
                case "tr":
                    t.rows.add(parseTableRow(c));
                    break;
            }
        }
    }

    private static TL_iv.pageTableRow parseTableRow(Node tr) {
        TL_iv.pageTableRow row = new TL_iv.pageTableRow();
        row.cells = new ArrayList<>();
        for (Node c : tr.children) {
            if (c.isText || !("td".equals(c.tag) || "th".equals(c.tag))) continue;
            TL_iv.pageTableCell cell = new TL_iv.pageTableCell();
            cell.colspan = parseIntAttr(c.attr("colspan"), 0);
            cell.rowspan = parseIntAttr(c.attr("rowspan"), 0);
            TableModel.applyStyledText(cell, inlineOf(c));
            TableModel.setHeader(cell, "th".equals(c.tag) || c.has("header"));
            String al = c.attr("align");
            if (al == null) al = alignFromStyle(c.attr("style"));
            if ("center".equalsIgnoreCase(al)) TableModel.setAlign(cell, TableModel.ALIGN_CENTER);
            else if ("right".equalsIgnoreCase(al)) TableModel.setAlign(cell, TableModel.ALIGN_RIGHT);
            String va = c.attr("valign");
            if ("middle".equalsIgnoreCase(va)) TableModel.setVAlign(cell, TableModel.VALIGN_MIDDLE);
            else if ("bottom".equalsIgnoreCase(va)) TableModel.setVAlign(cell, TableModel.VALIGN_BOTTOM);
            row.cells.add(cell);
        }
        if (row.cells.isEmpty()) row.cells.add(TableModel.newEmptyCell());
        return row;
    }

    private static String alignFromStyle(String style) {
        if (style == null) return null;
        String s = style.toLowerCase();
        int i = s.indexOf("text-align");
        if (i < 0) return null;
        if (s.indexOf("center", i) >= 0) return "center";
        if (s.indexOf("right", i) >= 0) return "right";
        return null;
    }

    private static BlockRow buildMediaRow(Node node) {
        switch (node.tag) {
            case "img": return buildPhotoOrVideo(node, false);
            case "video": return buildPhotoOrVideo(node, true);
            case "audio": return buildAudio(node);
            case "location": return buildMap(node);
            case "div": {
                String cls = node.attr("class");
                String low = cls == null ? "" : cls.toLowerCase();
                if (low.contains("slideshow")) return parseGallery(node, true);
                if (low.contains("collage")) return parseGallery(node, false);
                return null;
            }
        }
        return null;
    }

    private static BlockRow buildPhotoOrVideo(Node node, boolean video) {
        long key = parseLongAttr(node.attr("src"), 0);
        if (key <= 0) return null;
        TL_iv.PageBlock block = newMediaItemBlock(video, key, node.has("data-spoiler"));
        setEmptyCaption(block);
        return new BlockRow(block);
    }

    private static BlockRow buildAudio(Node node) {
        long key = parseLongAttr(node.attr("src"), 0);
        if (key <= 0) return null;
        TL_iv.pageBlockAudio a = new TL_iv.pageBlockAudio();
        a.audio_id = key;
        setEmptyCaption(a);
        return new BlockRow(a);
    }

    private static BlockRow buildMap(Node node) {
        TL_iv.pageBlockMap m = new TL_iv.pageBlockMap();
        TLRPC.TL_geoPoint geo = new TLRPC.TL_geoPoint();
        geo.lat = parseDoubleAttr(node.attr("lat"), 0);
        geo._long = parseDoubleAttr(node.attr("long"), 0);
        geo.access_hash = parseLongAttr(node.attr("access"), 0);
        m.geo = geo;
        m.zoom = parseIntAttr(node.attr("zoom"), 15);
        m.w = parseIntAttr(node.attr("w"), 600);
        m.h = parseIntAttr(node.attr("h"), 400);
        setEmptyCaption(m);
        return new BlockRow(m);
    }

    private static TL_iv.PageBlock newMediaItemBlock(boolean video, long key, boolean spoiler) {
        if (video) {
            TL_iv.pageBlockVideo v = new TL_iv.pageBlockVideo();
            v.video_id = key > 0 ? key : 0;
            v.spoiler = spoiler;
            return v;
        }
        TL_iv.pageBlockPhoto p = new TL_iv.pageBlockPhoto();
        p.photo_id = key > 0 ? key : 0;
        p.spoiler = spoiler;
        return p;
    }

    private static BlockRow parseGallery(Node node, boolean slideshow) {
        final TL_iv.PageBlock gal = slideshow ? new TL_iv.pageBlockSlideshow() : new TL_iv.pageBlockCollage();
        final ArrayList<TL_iv.PageBlock> items = RichEditorListView.galleryItems(gal);
        CharSequence caption = null;
        for (Node c : node.children) {
            if (c.isText) continue;
            if ("figcaption".equals(c.tag)) { caption = inlineOf(c); continue; }
            final boolean video = "video".equals(c.tag);
            if (!video && !"img".equals(c.tag)) continue;
            final long key = parseLongAttr(c.attr("src"), 0);
            if (key <= 0) continue;
            final TL_iv.PageBlock item = newMediaItemBlock(video, key, c.has("data-spoiler"));
            setEmptyCaption(item);
            items.add(item);
        }
        if (items.isEmpty()) return null;
        if (items.size() == 1) {
            final TL_iv.PageBlock item = items.get(0);
            if (caption != null && caption.length() > 0) setCaption(item, caption);
            return new BlockRow(item);
        }
        setEmptyCaption(gal);
        if (caption != null && caption.length() > 0) setCaption(gal, caption);
        return new BlockRow(gal);
    }

    private static void parseFigure(Node node, ArrayList<BlockRow> rows, int level) {
        BlockRow mediaRow = null;
        CharSequence caption = null;
        for (Node c : node.children) {
            if (c.isText) continue;
            if ("figcaption".equals(c.tag)) {
                caption = inlineOf(c);
            } else if (mediaRow == null) {
                mediaRow = buildMediaRow(c);
            }
        }
        if (mediaRow != null) {
            if (caption != null && caption.length() > 0) setCaption(mediaRow.block, caption);
            rows.add(mediaRow);
        } else if (caption != null && caption.length() > 0) {
            TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
            p.text = RichTextStyle.fromSpannable(caption);
            rows.add(new BlockRow(p, level, 0));
        }
    }

    private static void setCaption(TL_iv.PageBlock b, CharSequence cs) {
        TL_iv.PageCaption c = new TL_iv.PageCaption();
        c.text = RichTextStyle.fromSpannable(cs);
        c.credit = new TL_iv.textEmpty();
        b.caption = c;
    }

    private static void setEmptyCaption(TL_iv.PageBlock b) {
        TL_iv.PageCaption c = new TL_iv.PageCaption();
        c.text = new TL_iv.textEmpty();
        c.credit = new TL_iv.textEmpty();
        b.caption = c;
    }

    private static String firstAttr(Node node, String... names) {
        for (String name : names) {
            final String v = node.attr(name);
            if (v != null) return v;
        }
        return null;
    }

    private static long parseLongAttr(String s, long def) {
        if (s == null) return def;
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }

    private static int parseIntAttr(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static double parseDoubleAttr(String s, double def) {
        if (s == null) return def;
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }

    private static void parseList(Node listNode, ArrayList<BlockRow> rows, int level, boolean ordered) {
        int childLevel = level + 1;
        int counter = 1;
        for (Node li : listNode.children) {
            if (li.isText || !"li".equals(li.tag)) continue;
            SpannableStringBuilder sb = new SpannableStringBuilder();
            ArrayList<Node> nestedLists = new ArrayList<>();
            for (Node c : li.children) {
                if (!c.isText && ("ul".equals(c.tag) || "ol".equals(c.tag))) {
                    nestedLists.add(c);
                } else if (c.isText) {
                    sb.append(decode(c.text));
                } else {
                    appendInlineNode(sb, c, 0, null, 0);
                }
            }
            TL_iv.pageBlockParagraph para = new TL_iv.pageBlockParagraph();
            para.text = RichTextStyle.fromSpannable(trim(sb));
            BlockRow row = new BlockRow(para, childLevel, ordered ? counter : 0);
            row.checkbox = li.has("data-checkbox") || hasCheckboxClass(li);
            row.checked = li.has("data-checked");
            rows.add(row);
            for (Node nl : nestedLists) {
                parseList(nl, rows, childLevel, "ol".equals(nl.tag));
            }
            counter++;
        }
    }

    private static void parseDetails(Node node, ArrayList<BlockRow> rows, int level, Map<Long, TL_iv.RichText> authors) {
        TL_iv.pageBlockDetails details = new TL_iv.pageBlockDetails();
        details.open = node.has("open");
        details.blocks = new ArrayList<>();
        SpannableStringBuilder summary = new SpannableStringBuilder();
        ArrayList<Node> body = new ArrayList<>();
        for (Node c : node.children) {
            if (!c.isText && "summary".equals(c.tag)) {
                appendChildrenInline(summary, c, 0, null, 0);
            } else {
                body.add(c);
            }
        }
        details.title = RichTextStyle.fromSpannable(trim(summary));
        rows.add(new BlockRow(details));
        int childStart = rows.size();
        parseBlocks(body, rows, level, authors);
        if (rows.size() == childStart) {
            rows.add(new BlockRow(new TL_iv.pageBlockParagraph()));
        }
        BlockRow end = new BlockRow(new TL_iv.pageBlockParagraph());
        end.detailsEnd = true;
        rows.add(end);
    }

    private static void parseBlockquote(Node node, ArrayList<BlockRow> rows, int level, Map<Long, TL_iv.RichText> authors) {
        Node cite = null;
        boolean blockChildren = false;
        for (Node c : node.children) {
            if (c.isText || c.tag == null) continue;
            if ("cite".equals(c.tag)) { if (cite == null) cite = c; continue; }
            if (!isInlineTag(c.tag)) blockChildren = true;
        }
        final TL_iv.RichText author = citeAuthor(cite);

        if (!blockChildren) {
            SpannableStringBuilder sb = new SpannableStringBuilder();
            appendChildrenInlineExcept(sb, node, "cite");
            TL_iv.pageBlockBlockquote bq = new TL_iv.pageBlockBlockquote();
            RichTextCell.applyStyledTextToBlock(bq, trim(sb));
            if (author != null) bq.caption = author;
            rows.add(new BlockRow(bq, level, 0));
            return;
        }

        final long qid = RichContainer.newId();
        final int start = rows.size();
        ArrayList<Node> body = new ArrayList<>();
        for (Node c : node.children) {
            if (!c.isText && "cite".equals(c.tag)) continue;
            body.add(c);
        }
        parseBlocks(body, rows, level, authors);
        if (rows.size() == start) {
            rows.add(new BlockRow(new TL_iv.pageBlockParagraph(), level, 0));
        }
        for (int i = start; i < rows.size(); i++) {
            rows.get(i).quoteIds.add(0, qid);
        }
        if (author != null && authors != null) authors.put(qid, author);
    }

    private static void parsePullquote(Node node, ArrayList<BlockRow> rows, int level) {
        Node cite = null;
        for (Node c : node.children) {
            if (!c.isText && "cite".equals(c.tag)) { cite = c; break; }
        }
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendChildrenInlineExcept(sb, node, "cite");
        TL_iv.pageBlockPullquote pq = new TL_iv.pageBlockPullquote();
        RichTextCell.applyStyledTextToBlock(pq, trim(sb));
        final TL_iv.RichText author = citeAuthor(cite);
        if (author != null) pq.caption = author;
        rows.add(new BlockRow(pq, level, 0));
    }

    private static TL_iv.RichText citeAuthor(Node cite) {
        if (cite == null) return null;
        CharSequence cs = inlineOf(cite);
        if (cs == null || cs.length() == 0) return null;
        return RichTextStyle.fromSpannable(cs);
    }

    private static void appendChildrenInlineExcept(SpannableStringBuilder sb, Node node, String skipTag) {
        for (Node c : node.children) {
            if (c.isText) {
                appendStyled(sb, decode(c.text), 0, null, 0);
            } else if (!skipTag.equals(c.tag)) {
                appendInlineNode(sb, c, 0, null, 0);
            }
        }
    }

    private static boolean isInlineTag(String tag) {
        switch (tag) {
            case "b": case "strong": case "i": case "em": case "u": case "s": case "strike": case "del":
            case "code": case "tt": case "spoiler": case "sub": case "sup": case "a": case "br":
            case "span": case "animated-emoji": case "font":
                return true;
        }
        return false;
    }

    private static void appendChildrenInline(SpannableStringBuilder sb, Node node, int flags, String url, long emojiId) {
        for (Node c : node.children) {
            if (c.isText) {
                appendStyled(sb, decode(c.text), flags, url, emojiId);
            } else {
                appendInlineNode(sb, c, flags, url, emojiId);
            }
        }
    }

    private static void appendInlineNode(SpannableStringBuilder sb, Node node, int flags, String url, long emojiId) {
        switch (node.tag) {
            case "br": appendStyled(sb, "\n", flags, url, emojiId); return;
            case "b": case "strong": flags |= RichTextStyle.BOLD; break;
            case "i": case "em": flags |= RichTextStyle.ITALIC; break;
            case "u": flags |= RichTextStyle.UNDERLINE; break;
            case "s": case "strike": case "del": flags |= RichTextStyle.STRIKE; break;
            case "code": case "tt": flags |= RichTextStyle.MONO; break;
            case "spoiler": flags |= RichTextStyle.SPOILER; break;
            case "sub": flags |= RichTextStyle.SUBSCRIPT; break;
            case "sup": flags |= RichTextStyle.SUPERSCRIPT; break;
            case "mark": flags |= RichTextStyle.MARKED; break;
            case "a": {
                String href = node.attr("href");
                if (href != null) url = href;
                break;
            }
            case "animated-emoji": {
                String id = node.attr("data-document-id");
                if (id != null) {
                    try { emojiId = Long.parseLong(id.trim()); } catch (Exception ignore) {}
                }
                break;
            }
        }
        if (node.children.isEmpty() && !node.isText) {
            return;
        }
        appendChildrenInline(sb, node, flags, url, emojiId);
    }

    private static void appendStyled(SpannableStringBuilder sb, CharSequence text, int flags, String url, long emojiId) {
        if (text == null || text.length() == 0) return;
        int start = sb.length();
        sb.append(text);
        int end = sb.length();
        if (emojiId != 0) {
            AnimatedEmojiSpan span = new AnimatedEmojiSpan(emojiId, null);
            sb.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (flags != 0) {
            TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
            run.flags = flags & RichTextStyle.SUPPORTED;
            sb.setSpan(new TextStyleSpan(run, dp(SharedConfig.fontSize)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (url != null) {
            sb.setSpan(new URLSpanReplacement(url), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static boolean hasPullClass(Node node) {
        String c = node.attr("class");
        return c != null && c.toLowerCase().contains("pull");
    }

    private static boolean hasCheckboxClass(Node node) {
        String c = node.attr("class");
        return c != null && c.toLowerCase().contains("checkbox");
    }

    private static boolean isBlank(String s) {
        if (s == null) return true;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != ' ' && ch != '\n' && ch != '\t' && ch != '\r' && ch != ' ') return false;
        }
        return true;
    }

    private static CharSequence trim(SpannableStringBuilder sb) {
        int start = 0, end = sb.length();
        while (start < end && isWs(sb.charAt(start))) start++;
        while (end > start && isWs(sb.charAt(end - 1))) end--;
        if (start == 0 && end == sb.length()) return sb;
        return sb.subSequence(start, end);
    }

    private static boolean isWs(char c) {
        return c == ' ' || c == '\n' || c == '\t' || c == '\r';
    }

    private static String decode(String s) {
        if (s == null) return "";
        if (s.indexOf('&') < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '&') { out.append(c); continue; }
            int semi = s.indexOf(';', i + 1);
            if (semi < 0 || semi - i > 12) { out.append(c); continue; }
            String ent = s.substring(i + 1, semi);
            String rep = entity(ent);
            if (rep != null) {
                out.append(rep);
                i = semi;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String entity(String ent) {
        switch (ent) {
            case "lt": return "<";
            case "gt": return ">";
            case "amp": return "&";
            case "quot": return "\"";
            case "apos": return "'";
            case "nbsp": return " ";
        }
        if (ent.length() > 1 && ent.charAt(0) == '#') {
            try {
                int code = ent.charAt(1) == 'x' || ent.charAt(1) == 'X'
                    ? Integer.parseInt(ent.substring(2), 16)
                    : Integer.parseInt(ent.substring(1));
                return new String(Character.toChars(code));
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    private static class Node {
        String tag;
        boolean isText;
        String text;
        Map<String, String> attrs;
        final ArrayList<Node> children = new ArrayList<>();

        static Node text(String t) { Node n = new Node(); n.isText = true; n.text = t; return n; }
        static Node el(String t) { Node n = new Node(); n.tag = t; return n; }

        String attr(String k) { return attrs == null ? null : attrs.get(k); }
        boolean has(String k) { return attrs != null && attrs.containsKey(k); }
    }

    private static boolean isVoid(String tag) {
        switch (tag) {
            case "br": case "hr": case "img": case "input": case "meta": case "link": case "wbr":
                return true;
        }
        return false;
    }

    private static class Parser {
        final String s;
        int p;

        Parser(String html) { this.s = html == null ? "" : html; }

        List<Node> parse() {
            ArrayList<Node> roots = new ArrayList<>();
            ArrayList<Node> stack = new ArrayList<>();
            while (p < s.length()) {
                char c = s.charAt(p);
                if (c == '<') {
                    if (s.startsWith("<!--", p)) {
                        int e = s.indexOf("-->", p + 4);
                        p = e < 0 ? s.length() : e + 3;
                        continue;
                    }
                    if (p + 1 < s.length() && s.charAt(p + 1) == '!') {
                        int e = s.indexOf('>', p);
                        p = e < 0 ? s.length() : e + 1;
                        continue;
                    }
                    if (p + 1 < s.length() && s.charAt(p + 1) == '/') {
                        int e = s.indexOf('>', p);
                        String name = s.substring(p + 2, e < 0 ? s.length() : e).trim().toLowerCase();
                        p = e < 0 ? s.length() : e + 1;
                        closeTag(stack, name);
                        continue;
                    }
                    int e = findTagEnd(p);
                    if (e < 0) { // malformed: treat as text
                        addText(stack, roots, s.substring(p));
                        break;
                    }
                    String inner = s.substring(p + 1, e);
                    p = e + 1;
                    boolean selfClose = inner.endsWith("/");
                    if (selfClose) inner = inner.substring(0, inner.length() - 1);
                    Node el = parseTag(inner);
                    if (el == null) continue;
                    addChild(stack, roots, el);
                    if (!selfClose && !isVoid(el.tag)) {
                        stack.add(el);
                    }
                } else {
                    int next = s.indexOf('<', p);
                    if (next < 0) next = s.length();
                    addText(stack, roots, s.substring(p, next));
                    p = next;
                }
            }
            return roots;
        }

        private int findTagEnd(int from) {
            boolean inQuote = false;
            char q = 0;
            for (int i = from + 1; i < s.length(); i++) {
                char c = s.charAt(i);
                if (inQuote) {
                    if (c == q) inQuote = false;
                } else if (c == '"' || c == '\'') {
                    inQuote = true; q = c;
                } else if (c == '>') {
                    return i;
                }
            }
            return -1;
        }

        private void closeTag(ArrayList<Node> stack, String name) {
            for (int i = stack.size() - 1; i >= 0; i--) {
                if (stack.get(i).tag.equals(name)) {
                    while (stack.size() > i) stack.remove(stack.size() - 1);
                    return;
                }
            }
        }

        private void addChild(ArrayList<Node> stack, ArrayList<Node> roots, Node node) {
            if (stack.isEmpty()) roots.add(node);
            else stack.get(stack.size() - 1).children.add(node);
        }

        private void addText(ArrayList<Node> stack, ArrayList<Node> roots, String t) {
            if (t.isEmpty()) return;
            addChild(stack, roots, Node.text(t));
        }

        private Node parseTag(String inner) {
            inner = inner.trim();
            if (inner.isEmpty()) return null;
            int i = 0;
            while (i < inner.length() && !isSpace(inner.charAt(i))) i++;
            String name = inner.substring(0, i).toLowerCase();
            if (name.isEmpty()) return null;
            Node el = Node.el(name);
            // attributes
            while (i < inner.length()) {
                while (i < inner.length() && isSpace(inner.charAt(i))) i++;
                if (i >= inner.length()) break;
                int ks = i;
                while (i < inner.length() && inner.charAt(i) != '=' && !isSpace(inner.charAt(i))) i++;
                String key = inner.substring(ks, i).toLowerCase();
                String val = "";
                while (i < inner.length() && isSpace(inner.charAt(i))) i++;
                if (i < inner.length() && inner.charAt(i) == '=') {
                    i++;
                    while (i < inner.length() && isSpace(inner.charAt(i))) i++;
                    if (i < inner.length() && (inner.charAt(i) == '"' || inner.charAt(i) == '\'')) {
                        char q = inner.charAt(i);
                        int vs = ++i;
                        while (i < inner.length() && inner.charAt(i) != q) i++;
                        val = inner.substring(vs, Math.min(i, inner.length()));
                        if (i < inner.length()) i++;
                    } else {
                        int vs = i;
                        while (i < inner.length() && !isSpace(inner.charAt(i))) i++;
                        val = inner.substring(vs, i);
                    }
                }
                if (!key.isEmpty()) {
                    if (el.attrs == null) el.attrs = new HashMap<>();
                    el.attrs.put(key, decode(val));
                }
            }
            return el;
        }

        private boolean isSpace(char c) {
            return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
        }
    }
}
