package org.telegram.ui.iv;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;

import org.telegram.messenger.CodeHighlighting;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.Components.QuoteSpan;
import org.telegram.ui.Components.TextStyleSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RichMessageConvert {

    public static TL_iv.RichMessage fromCharSequence(CharSequence text) {
        final TL_iv.RichMessage msg = new TL_iv.RichMessage();
        msg.blocks = blocksFromCharSequence(text);
        return msg;
    }

    public static ArrayList<TL_iv.PageBlock> blocksFromCharSequence(CharSequence text) {
        final ArrayList<TL_iv.PageBlock> blocks = new ArrayList<>();
        if (text == null) {
            blocks.add(emptyParagraph());
            return blocks;
        }
        final Spanned sp = text instanceof Spanned ? (Spanned) text : null;
        final int[] lineStarts = lineStarts(text);
        final int n = text.length();

        int i = 0;
        while (i < lineStarts.length) {
            final int lineStart = lineStarts[i];
            final int lineEnd = i + 1 < lineStarts.length ? lineStarts[i + 1] - 1 : n;

            final CodeHighlighting.Span code = sp == null ? null : blockSpan(sp, lineStart, lineEnd, CodeHighlighting.Span.class);
            final QuoteSpan quote = sp == null || code != null ? null : blockSpan(sp, lineStart, lineEnd, QuoteSpan.class);

            if (code == null && quote == null) {
                blocks.add(paragraph(text.subSequence(lineStart, lineEnd)));
                i++;
                continue;
            }

            final Object owner = code != null ? code : quote;
            int blockEnd = lineEnd;
            int j = i + 1;
            while (j < lineStarts.length) {
                final int ns = lineStarts[j];
                final int ne = j + 1 < lineStarts.length ? lineStarts[j + 1] - 1 : n;
                final Object here = code != null
                    ? blockSpan(sp, ns, ne, CodeHighlighting.Span.class)
                    : blockSpan(sp, ns, ne, QuoteSpan.class);
                if (here != owner) break;
                blockEnd = ne;
                j++;
            }

            final CharSequence content = text.subSequence(lineStart, blockEnd);
            if (code != null) {
                final TL_iv.pageBlockPreformatted pre = new TL_iv.pageBlockPreformatted();
                pre.text = RichTextStyle.fromSpannable(content);
                pre.language = code.lng == null ? "" : code.lng;
                blocks.add(pre);
            } else {
                final TL_iv.pageBlockBlockquote bq = new TL_iv.pageBlockBlockquote();
                bq.text = RichTextStyle.fromSpannable(content);
                bq.caption = new TL_iv.textEmpty();
                blocks.add(bq);
            }
            i = j;
        }

        if (blocks.isEmpty()) blocks.add(emptyParagraph());
        return blocks;
    }

    private static TL_iv.pageBlockParagraph paragraph(CharSequence line) {
        final TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
        p.text = RichTextStyle.fromSpannable(line);
        return p;
    }

    private static TL_iv.pageBlockParagraph emptyParagraph() {
        final TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
        p.text = new TL_iv.textEmpty();
        return p;
    }

    private static int[] lineStarts(CharSequence text) {
        final ArrayList<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') starts.add(i + 1);
        }
        final int[] out = new int[starts.size()];
        for (int i = 0; i < out.length; i++) out[i] = starts.get(i);
        return out;
    }

    private static <T> T blockSpan(Spanned sp, int start, int end, Class<T> type) {
        final T[] spans = sp.getSpans(start, Math.max(start, end), type);
        for (T span : spans) {
            final int s = sp.getSpanStart(span);
            final int e = sp.getSpanEnd(span);
            if (s <= start && e >= end) return span;
        }
        return null;
    }

    public static CharSequence toCharSequence(TL_iv.RichMessage msg) {
        return blocksToCharSequence(msg == null ? null : msg.blocks);
    }

    public static CharSequence blocksToCharSequence(List<TL_iv.PageBlock> blocks) {
        final ArrayList<CharSequence> parts = new ArrayList<>();
        collectBlocks(parts, blocks);
        return join(parts);
    }

    private static void collectBlocks(ArrayList<CharSequence> out, List<TL_iv.PageBlock> blocks) {
        if (blocks == null) return;
        for (TL_iv.PageBlock block : blocks) {
            final CharSequence cs = renderBlock(block);
            if (cs != null) out.add(cs);
        }
    }

    private static CharSequence renderBlock(TL_iv.PageBlock block) {
        if (block == null) return null;

        if (block instanceof TL_iv.pageBlockPreformatted) {
            final SpannableStringBuilder sb = new SpannableStringBuilder(RichTextStyle.toSpannable(block.text, block));
            final String lng = ((TL_iv.pageBlockPreformatted) block).language;
            sb.setSpan(new CodeHighlighting.Span(true, 0, null, lng, sb.toString()), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return sb;
        }

        if (block instanceof TL_iv.pageBlockBlockquote || block instanceof TL_iv.pageBlockPullquote) {
            final TL_iv.RichText caption = block instanceof TL_iv.pageBlockBlockquote
                ? ((TL_iv.pageBlockBlockquote) block).caption
                : ((TL_iv.pageBlockPullquote) block).caption;
            return quoted(RichTextStyle.toSpannable(block.text, block), caption);
        }

        if (block instanceof TL_iv.pageBlockBlockquoteBlocks) {
            final TL_iv.pageBlockBlockquoteBlocks q = (TL_iv.pageBlockBlockquoteBlocks) block;
            final ArrayList<CharSequence> inner = new ArrayList<>();
            collectBlocks(inner, q.blocks);
            return quoted(join(inner), q.caption);
        }

        if (block instanceof TL_iv.pageBlockDetails) {
            final TL_iv.pageBlockDetails d = (TL_iv.pageBlockDetails) block;
            final ArrayList<CharSequence> parts = new ArrayList<>();
            final CharSequence title = bold(RichTextStyle.toSpannable(d.title));
            if (!TextUtils.isEmpty(title)) parts.add(title);
            collectBlocks(parts, d.blocks);
            return parts.isEmpty() ? null : join(parts);
        }

        if (block instanceof TL_iv.pageBlockList) {
            return renderList(((TL_iv.pageBlockList) block).items);
        }
        if (block instanceof TL_iv.pageBlockOrderedList) {
            return renderOrderedList(((TL_iv.pageBlockOrderedList) block).items);
        }

        if (block instanceof TL_iv.pageBlockTable) {
            return renderTable((TL_iv.pageBlockTable) block);
        }

        if (block instanceof TL_iv.pageBlockMath) {
            final String source = ((TL_iv.pageBlockMath) block).source;
            if (TextUtils.isEmpty(source)) return null;
            return mono(new SpannableStringBuilder(source));
        }

        if (block instanceof TL_iv.pageBlockDivider) {
            return "——————————";
        }

        if (isHeading(block)) {
            final CharSequence t = bold(RichTextStyle.toSpannable(block.text, block));
            return TextUtils.isEmpty(t) ? null : t;
        }

        if (block instanceof TL_iv.pageBlockAuthorDate) {
            return RichTextStyle.toSpannable(((TL_iv.pageBlockAuthorDate) block).author);
        }

        if (block instanceof TL_iv.pageBlockParagraph
                || block instanceof TL_iv.pageBlockFooter
                || block instanceof TL_iv.pageBlockKicker
                || block instanceof TL_iv.pageBlockThinking) {
            return RichTextStyle.toSpannable(block.text, block);
        }

        return captionText(block);
    }

    private static CharSequence renderList(ArrayList<TL_iv.PageListItem> items) {
        if (items == null || items.isEmpty()) return null;
        final ArrayList<CharSequence> lines = new ArrayList<>();
        for (TL_iv.PageListItem item : items) {
            final CharSequence text = listItemText(item);
            if (text == null) continue;
            final String prefix = item.checkbox ? (item.checked ? "☑  " : "☐  ") : "•  ";
            lines.add(prefixed(prefix, text));
        }
        return lines.isEmpty() ? null : join(lines);
    }

    private static CharSequence renderOrderedList(ArrayList<TL_iv.PageListOrderedItem> items) {
        if (items == null || items.isEmpty()) return null;
        final ArrayList<CharSequence> lines = new ArrayList<>();
        int counter = 1;
        for (TL_iv.PageListOrderedItem item : items) {
            final CharSequence text = orderedItemText(item);
            if (text == null) { counter++; continue; }
            final String num = !TextUtils.isEmpty(item.num) ? item.num : String.valueOf(counter);
            lines.add(prefixed(num + ".  ", text));
            counter++;
        }
        return lines.isEmpty() ? null : join(lines);
    }

    private static CharSequence renderTable(TL_iv.pageBlockTable table) {
        final ArrayList<CharSequence> lines = new ArrayList<>();
        final CharSequence title = bold(RichTextStyle.toSpannable(table.title));
        if (!TextUtils.isEmpty(title)) lines.add(title);
        if (table.rows != null) {
            for (TL_iv.pageTableRow row : table.rows) {
                if (row.cells == null || row.cells.isEmpty()) continue;
                final SpannableStringBuilder line = new SpannableStringBuilder();
                for (int c = 0; c < row.cells.size(); c++) {
                    if (c > 0) line.append("  |  ");
                    line.append(RichTextStyle.toSpannable(row.cells.get(c).text));
                }
                lines.add(line);
            }
        }
        return lines.isEmpty() ? null : join(lines);
    }

    private static CharSequence listItemText(TL_iv.PageListItem item) {
        if (item instanceof TL_iv.TL_pageListItemText) {
            return RichTextStyle.toSpannable(((TL_iv.TL_pageListItemText) item).text);
        }
        if (item instanceof TL_iv.TL_pageListItemBlocks) {
            return blocksToCharSequence(((TL_iv.TL_pageListItemBlocks) item).blocks);
        }
        return null;
    }

    private static CharSequence orderedItemText(TL_iv.PageListOrderedItem item) {
        if (item instanceof TL_iv.TL_pageListOrderedItemText) {
            return RichTextStyle.toSpannable(((TL_iv.TL_pageListOrderedItemText) item).text);
        }
        if (item instanceof TL_iv.TL_pageListOrderedItemBlocks) {
            return blocksToCharSequence(((TL_iv.TL_pageListOrderedItemBlocks) item).blocks);
        }
        return null;
    }

    private static CharSequence captionText(TL_iv.PageBlock block) {
        if (block.caption == null) return null;
        final SpannableStringBuilder sb = new SpannableStringBuilder();
        final CharSequence text = RichTextStyle.toSpannable(block.caption.text);
        if (!TextUtils.isEmpty(text)) sb.append(text);
        final CharSequence credit = RichTextStyle.toSpannable(block.caption.credit);
        if (!TextUtils.isEmpty(credit)) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(credit);
        }
        return sb.length() > 0 ? sb : null;
    }

    private static CharSequence quoted(CharSequence text, TL_iv.RichText caption) {
        final SpannableStringBuilder sb = new SpannableStringBuilder(text == null ? "" : text);
        final CharSequence author = RichTextStyle.toSpannable(caption);
        if (!TextUtils.isEmpty(author)) {
            if (sb.length() > 0) sb.append('\n');
            sb.append("— ").append(author);
        }
        if (sb.length() == 0) return null;
        QuoteSpan.putQuote(sb, 0, sb.length(), false);
        return sb;
    }

    private static CharSequence prefixed(String prefix, CharSequence text) {
        final SpannableStringBuilder sb = new SpannableStringBuilder(prefix);
        sb.append(text == null ? "" : text);
        return sb;
    }

    private static CharSequence bold(CharSequence text) {
        return styled(text, TextStyleSpan.FLAG_STYLE_BOLD);
    }

    private static CharSequence mono(CharSequence text) {
        return styled(text, TextStyleSpan.FLAG_STYLE_MONO);
    }

    private static CharSequence styled(CharSequence text, int flags) {
        if (TextUtils.isEmpty(text)) return text;
        final SpannableStringBuilder sb = new SpannableStringBuilder(text);
        final TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags = flags;
        sb.setSpan(new TextStyleSpan(run), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    private static CharSequence join(ArrayList<CharSequence> parts) {
        final SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(parts.get(i));
        }
        return sb;
    }

    public static boolean isLossy(List<BlockRow> rows) {
        return isLossy(rows, null);
    }

    public static boolean isLossy(List<BlockRow> rows, Map<Long, TL_iv.RichText> quoteAuthors) {
        if (rows == null) return false;
        for (BlockRow r : rows) {
            if (rowLossy(r, quoteAuthors)) return true;
        }
        return false;
    }

    private static boolean rowLossy(BlockRow r, Map<Long, TL_iv.RichText> quoteAuthors) {
        if (r == null) return false;
        if (r.detailsEnd) return true;
        if (r.level > 0) return true;
        final TL_iv.PageBlock b = r.block;
        if (b instanceof TL_iv.pageBlockPullquote) return true;
        final boolean quoteLeaf = b instanceof TL_iv.pageBlockBlockquote;
        if (r.quoteIds.size() + (quoteLeaf ? 1 : 0) > 1) return true;
        if (quoteLeaf && hasText(captionOf(b))) return true;
        if (quoteAuthors != null && !r.quoteIds.isEmpty()) {
            for (Long qid : r.quoteIds) {
                if (hasText(quoteAuthors.get(qid))) return true;
            }
        }
        final boolean simple = b instanceof TL_iv.pageBlockParagraph
            || b instanceof TL_iv.pageBlockPreformatted
            || quoteLeaf;
        if (!simple) return true;
        return inlineLossy(b.text);
    }

    private static TL_iv.RichText captionOf(TL_iv.PageBlock b) {
        if (b instanceof TL_iv.pageBlockBlockquote) return ((TL_iv.pageBlockBlockquote) b).caption;
        if (b instanceof TL_iv.pageBlockPullquote) return ((TL_iv.pageBlockPullquote) b).caption;
        return null;
    }

    private static boolean hasText(TL_iv.RichText rt) {
        return rt != null && !(rt instanceof TL_iv.textEmpty) && !RichTextStyle.plainOf(rt).isEmpty();
    }

    public static CharSequence rowsToCharSequence(List<BlockRow> rows) {
        final ArrayList<CharSequence> units = new ArrayList<>();
        int i = 0;
        while (rows != null && i < rows.size()) {
            final BlockRow r = rows.get(i);
            if (!r.quoteIds.isEmpty()) {
                final long qid = r.quoteIds.get(0);
                final SpannableStringBuilder q = new SpannableStringBuilder();
                int j = i;
                while (j < rows.size() && !rows.get(j).quoteIds.isEmpty() && rows.get(j).quoteIds.get(0) == qid) {
                    if (j > i) q.append('\n');
                    q.append(renderLeaf(rows.get(j)));
                    j++;
                }
                if (q.length() > 0) QuoteSpan.putQuote(q, 0, q.length(), false);
                units.add(q);
                i = j;
            } else if (isQuoteLeaf(r.block)) {
                final SpannableStringBuilder q = new SpannableStringBuilder(RichTextStyle.toSpannable(r.block.text, r.block));
                if (q.length() > 0) QuoteSpan.putQuote(q, 0, q.length(), false);
                units.add(q);
                i++;
            } else {
                units.add(renderLeaf(r));
                i++;
            }
        }
        return join(units);
    }

    public static CharSequence rowsToSimpleMessage(List<BlockRow> rows) {
        final SpannableStringBuilder sb = new SpannableStringBuilder(rowsToCharSequence(rows));
        final int n = sb.length();
        RichTextStyle.setStyle(sb, 0, n, RichTextStyle.MARKED, false);
        RichTextStyle.setStyle(sb, 0, n, RichTextStyle.SUBSCRIPT, false);
        RichTextStyle.setStyle(sb, 0, n, RichTextStyle.SUPERSCRIPT, false);
        for (MathSpan m : sb.getSpans(0, n, MathSpan.class)) {
            sb.removeSpan(m);
        }
        return sb;
    }

    private static boolean inlineLossy(TL_iv.RichText rt) {
        if (rt == null
                || rt instanceof TL_iv.textEmpty
                || rt instanceof TL_iv.textPlain
                || rt instanceof TL_iv.textCustomEmoji) {
            return false;
        }
        if (rt instanceof TL_iv.textConcat) {
            for (TL_iv.RichText child : ((TL_iv.textConcat) rt).texts) {
                if (inlineLossy(child)) return true;
            }
            return false;
        }
        if (rt instanceof TL_iv.textMarked
                || rt instanceof TL_iv.textSubscript
                || rt instanceof TL_iv.textSuperscript
                || rt instanceof TL_iv.textMath) {
            return true;
        }
        return inlineLossy(rt.text);
    }

    private static boolean isQuoteLeaf(TL_iv.PageBlock b) {
        return b instanceof TL_iv.pageBlockBlockquote || b instanceof TL_iv.pageBlockPullquote;
    }

    private static CharSequence renderLeaf(BlockRow r) {
        final CharSequence cs = RichTextStyle.toSpannable(r.block == null ? null : r.block.text, r.block);
        if (r.block instanceof TL_iv.pageBlockPreformatted) {
            final SpannableStringBuilder sb = new SpannableStringBuilder(cs);
            final String lng = ((TL_iv.pageBlockPreformatted) r.block).language;
            sb.setSpan(new CodeHighlighting.Span(true, 0, null, lng, sb.toString()), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return sb;
        }
        return cs;
    }

    private static boolean isHeading(TL_iv.PageBlock b) {
        return b instanceof TL_iv.pageBlockTitle
            || b instanceof TL_iv.pageBlockSubtitle
            || b instanceof TL_iv.pageBlockHeader
            || b instanceof TL_iv.pageBlockSubheader
            || b instanceof TL_iv.pageBlockHeading1
            || b instanceof TL_iv.pageBlockHeading2
            || b instanceof TL_iv.pageBlockHeading3
            || b instanceof TL_iv.pageBlockHeading4
            || b instanceof TL_iv.pageBlockHeading5
            || b instanceof TL_iv.pageBlockHeading6;
    }
}
