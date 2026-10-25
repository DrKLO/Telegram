package org.telegram.messenger;

import android.text.Layout;
import android.text.Spanned;
import android.text.TextUtils;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.iv.RichTextStyle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;

/**
 * Quoting a fragment of a rich message: quote_rich is a list of RichText fragments, one per quoted text node
 * of the original in document order. The first fragment may be a suffix of its node, the last one a prefix,
 * the ones in between are the original nodes as they are.
 */
public class RichQuote {

    public static final int MAX_FRAGMENTS = 32;

    public final ArrayList<TL_iv.RichText> fragments;
    public final int offset;

    private RichQuote(ArrayList<TL_iv.RichText> fragments, int offset) {
        this.fragments = fragments;
        this.offset = offset;
    }

    /* ---------- quotable nodes ---------- */

    public static ArrayList<TL_iv.RichText> collectNodes(TL_iv.RichMessage message) {
        final ArrayList<TL_iv.RichText> out = new ArrayList<>();
        if (message != null) {
            collect(message.blocks, out);
        }
        return out;
    }

    private static void collect(ArrayList<TL_iv.PageBlock> blocks, ArrayList<TL_iv.RichText> out) {
        if (blocks == null) return;
        for (TL_iv.PageBlock block : blocks) {
            collect(block, out);
        }
    }

    private static void collect(TL_iv.PageBlock block, ArrayList<TL_iv.RichText> out) {
        if (
            block instanceof TL_iv.pageBlockParagraph ||
            block instanceof TL_iv.pageBlockHeading1 || block instanceof TL_iv.pageBlockHeading2 ||
            block instanceof TL_iv.pageBlockHeading3 || block instanceof TL_iv.pageBlockHeading4 ||
            block instanceof TL_iv.pageBlockHeading5 || block instanceof TL_iv.pageBlockHeading6 ||
            block instanceof TL_iv.pageBlockFooter || block instanceof TL_iv.pageBlockThinking ||
            block instanceof TL_iv.pageBlockPreformatted
        ) {
            add(block.text, out);
        } else if (block instanceof TL_iv.pageBlockMath) {
            final String source = ((TL_iv.pageBlockMath) block).source;
            if (!TextUtils.isEmpty(source)) {
                final TL_iv.textMath math = new TL_iv.textMath();
                math.source = source;
                out.add(math);
            }
        } else if (block instanceof TL_iv.pageBlockBlockquote) {
            add(block.text, out);
            add(((TL_iv.pageBlockBlockquote) block).caption, out);
        } else if (block instanceof TL_iv.pageBlockBlockquoteBlocks) {
            collect(((TL_iv.pageBlockBlockquoteBlocks) block).blocks, out);
            add(((TL_iv.pageBlockBlockquoteBlocks) block).caption, out);
        } else if (block instanceof TL_iv.pageBlockPullquote) {
            add(block.text, out);
            add(((TL_iv.pageBlockPullquote) block).caption, out);
        } else if (block instanceof TL_iv.pageBlockList) {
            for (TL_iv.PageListItem item : ((TL_iv.pageBlockList) block).items) {
                if (item instanceof TL_iv.TL_pageListItemText) {
                    add(((TL_iv.TL_pageListItemText) item).text, out);
                } else if (item instanceof TL_iv.TL_pageListItemBlocks) {
                    collect(((TL_iv.TL_pageListItemBlocks) item).blocks, out);
                }
            }
        } else if (block instanceof TL_iv.pageBlockOrderedList) {
            for (TL_iv.PageListOrderedItem item : ((TL_iv.pageBlockOrderedList) block).items) {
                if (item instanceof TL_iv.TL_pageListOrderedItemText) {
                    add(((TL_iv.TL_pageListOrderedItemText) item).text, out);
                } else if (item instanceof TL_iv.TL_pageListOrderedItemBlocks) {
                    collect(((TL_iv.TL_pageListOrderedItemBlocks) item).blocks, out);
                }
            }
        } else if (block instanceof TL_iv.pageBlockTable) {
            final TL_iv.pageBlockTable table = (TL_iv.pageBlockTable) block;
            add(table.title, out);
            for (TL_iv.pageTableRow row : table.rows) {
                for (TL_iv.pageTableCell cell : row.cells) {
                    add(cell.text, out);
                }
            }
        } else if (block instanceof TL_iv.pageBlockDetails) {
            add(((TL_iv.pageBlockDetails) block).title, out);
            collect(((TL_iv.pageBlockDetails) block).blocks, out);
        } else if (
            block instanceof TL_iv.pageBlockPhoto || block instanceof TL_iv.pageBlockVideo ||
            block instanceof TL_iv.pageBlockAudio || block instanceof TL_iv.pageBlockMap
        ) {
            addCaption(block.caption, out);
        } else if (block instanceof TL_iv.pageBlockCollage) {
            collect(((TL_iv.pageBlockCollage) block).items, out);
            addCaption(block.caption, out);
        } else if (block instanceof TL_iv.pageBlockSlideshow) {
            collect(((TL_iv.pageBlockSlideshow) block).items, out);
            addCaption(block.caption, out);
        }
    }

    private static void addCaption(TL_iv.PageCaption caption, ArrayList<TL_iv.RichText> out) {
        if (caption == null) return;
        add(caption.text, out);
        add(caption.credit, out);
    }

    private static void add(TL_iv.RichText text, ArrayList<TL_iv.RichText> out) {
        if (text != null && flatLength(text) > 0) {
            out.add(text);
        }
    }

    /* ---------- flat text ---------- */

    public static String flatText(TL_iv.RichText text) {
        final StringBuilder sb = new StringBuilder();
        appendFlat(text, sb, null);
        return sb.toString();
    }

    public static int flatLength(TL_iv.RichText text) {
        return flatText(text).length();
    }

    /** Fragments joined with \n, as plain text with entities, for displaying the quote. */
    public static String flatText(ArrayList<TL_iv.RichText> fragments, ArrayList<TLRPC.MessageEntity> entities) {
        final StringBuilder sb = new StringBuilder();
        if (fragments != null) {
            for (int i = 0; i < fragments.size(); ++i) {
                if (i > 0) sb.append('\n');
                appendFlat(fragments.get(i), sb, entities);
            }
        }
        return sb.toString();
    }

    private static void appendFlat(TL_iv.RichText text, StringBuilder sb, ArrayList<TLRPC.MessageEntity> entities) {
        if (text == null || text instanceof TL_iv.textEmpty || text instanceof TL_iv.textImage) {
            return;
        }
        if (text instanceof TL_iv.textPlain) {
            final String s = ((TL_iv.textPlain) text).text;
            if (s != null) sb.append(s);
            return;
        }
        if (text instanceof TL_iv.textMath) {
            final String s = ((TL_iv.textMath) text).source;
            if (s != null) sb.append(s);
            return;
        }
        final int start = sb.length();
        if (text instanceof TL_iv.textCustomEmoji) {
            final TL_iv.textCustomEmoji emoji = (TL_iv.textCustomEmoji) text;
            sb.append(TextUtils.isEmpty(emoji.alt) ? "😀" : emoji.alt);
            if (entities != null) {
                final TLRPC.TL_messageEntityCustomEmoji entity = new TLRPC.TL_messageEntityCustomEmoji();
                entity.document_id = emoji.document_id;
                addEntity(entities, entity, start, sb.length());
            }
            return;
        }
        if (text instanceof TL_iv.textConcat) {
            for (TL_iv.RichText child : text.texts) {
                appendFlat(child, sb, entities);
            }
            return;
        }
        if (text instanceof TL_iv.textDiff) {
            final TL_iv.textDiff diff = (TL_iv.textDiff) text;
            appendFlat(RichTextStyle.isEmpty(diff.text) ? diff.old_text : diff.text, sb, entities);
            return;
        }
        appendFlat(text.text, sb, entities);
        if (entities == null) return;
        final TLRPC.MessageEntity entity;
        if (text instanceof TL_iv.textBold) {
            entity = new TLRPC.TL_messageEntityBold();
        } else if (text instanceof TL_iv.textItalic) {
            entity = new TLRPC.TL_messageEntityItalic();
        } else if (text instanceof TL_iv.textUnderline) {
            entity = new TLRPC.TL_messageEntityUnderline();
        } else if (text instanceof TL_iv.textStrike) {
            entity = new TLRPC.TL_messageEntityStrike();
        } else if (text instanceof TL_iv.textFixed) {
            entity = new TLRPC.TL_messageEntityCode();
        } else if (text instanceof TL_iv.textSpoiler) {
            entity = new TLRPC.TL_messageEntitySpoiler();
        } else if (text instanceof TL_iv.textUrl) {
            entity = new TLRPC.TL_messageEntityTextUrl();
            entity.url = ((TL_iv.textUrl) text).url;
        } else {
            entity = null;
        }
        if (entity != null) {
            addEntity(entities, entity, start, sb.length());
        }
    }

    private static void addEntity(ArrayList<TLRPC.MessageEntity> entities, TLRPC.MessageEntity entity, int start, int end) {
        if (end <= start) return;
        entity.offset = start;
        entity.length = end - start;
        entities.add(entity);
    }

    /** Fills quote_text and quote_entities from quote_rich, so the reply header can be displayed like a plain quote. */
    public static void fillFlat(TLRPC.MessageReplyHeader header) {
        if (header == null || header.quote_rich == null || header.quote_text != null || ApplicationLoader.isAndroidTestEnvironment()) {
            return;
        }
        final ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        header.quote_text = flatText(header.quote_rich, entities);
        header.flags |= 64;
        header.quote_entities = entities;
        if (!entities.isEmpty()) {
            header.flags |= 128;
        }
    }

    /* ---------- building from a selection ---------- */

    private static class Position {
        final RichMessageLayout.QuoteRootSpan root;
        final int offset;
        final CharSequence text;
        final int textStart;

        Position(RichMessageLayout.QuoteRootSpan root, int offset, CharSequence text, int textStart) {
            this.root = root;
            this.offset = offset;
            this.text = text;
            this.textStart = textStart;
        }
    }

    public static boolean canQuote(MessageObject messageObject) {
        return (
            messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.rich_message != null &&
            messageObject.richLayout != null && messageObject.richLayout.richMessage == messageObject.messageOwner.rich_message
        );
    }

    /** start and end are offsets in RichMessageLayout.joinedText */
    public static RichQuote fromSelection(MessageObject messageObject, int start, int end) {
        if (!canQuote(messageObject)) {
            return null;
        }
        final RichMessageLayout layout = messageObject.richLayout;
        final int maxLength = MessagesController.getInstance(messageObject.currentAccount).quoteLengthMax;

        final ArrayList<TL_iv.RichText> nodes = collectNodes(layout.richMessage);
        final IdentityHashMap<TL_iv.RichText, Integer> indices = new IdentityHashMap<>();
        for (int i = 0; i < nodes.size(); ++i) {
            indices.put(nodes.get(i), i);
        }

        final Position from = resolve(layout, Math.min(start, end), true, indices);
        Position to = resolve(layout, Math.max(start, end), false, indices);
        if (from == null || to == null) {
            return null;
        }
        final int fromIndex = indices.get(from.root.text);
        int toIndex = indices.get(to.root.text);
        if (fromIndex > toIndex || fromIndex == toIndex && from.offset >= to.offset) {
            return null;
        }

        final Sliced first = Sliced.of(layout, from);
        final Sliced last = fromIndex == toIndex ? first : Sliced.of(layout, to);
        if (first == null || last == null) {
            return null;
        }
        final Range range = new Range(layout, nodes, fromIndex, from.offset, first, toIndex, to.offset, last);

        if (range.toIndex - range.fromIndex + 1 > MAX_FRAGMENTS) {
            range.setEnd(range.fromIndex + MAX_FRAGMENTS - 1, Integer.MAX_VALUE);
        }
        // limit the length: cut the selection at the fragment where it's exceeded
        for (int attempt = 0; attempt < 8 && range.toIndex >= range.fromIndex; ++attempt) {
            int length = 0;
            boolean exceeded = false;
            for (int i = range.fromIndex; i <= range.toIndex; ++i) {
                final String flat = flatText(range.fragment(i));
                final int len = flat.codePointCount(0, flat.length());
                if (length + len > maxLength) {
                    final int remaining = maxLength - length;
                    // the limit is in flat text, the cut is in formatted text: math and buttons are a single placeholder there
                    final int cut = remaining <= 0 ? -1 : range.formattedOffset(i, flat.offsetByCodePoints(0, remaining));
                    if (remaining <= 0 || cut <= range.startOffset(i) || i == range.toIndex && cut >= range.toOffset) {
                        range.setEnd(i - 1, Integer.MAX_VALUE);
                    } else {
                        range.setEnd(i, cut);
                    }
                    exceeded = true;
                    break;
                }
                length += len;
            }
            if (!exceeded) break;
        }
        if (range.toIndex < range.fromIndex) {
            return null;
        }

        final ArrayList<TL_iv.RichText> fragments = new ArrayList<>();
        for (int i = range.fromIndex; i <= range.toIndex; ++i) {
            final TL_iv.RichText fragment = withoutButtons(range.fragment(i));
            if (fragment != null && flatLength(fragment) > 0) {
                fragments.add(fragment);
            }
        }
        if (fragments.isEmpty() || fragments.size() > MAX_FRAGMENTS) {
            return null;
        }

        int offset = 0;
        for (int i = 0; i < fromIndex; ++i) {
            offset += flatLength(nodes.get(i)) + 1;
        }
        final TL_iv.RichText prefix = first.slice(0, from.offset);
        if (prefix != null) {
            offset += flatLength(prefix);
        }

        return new RichQuote(fragments, offset);
    }

    /* ---------- finding a quote ---------- */

    /**
     * Finds a quote in a rich message: quote is the flat text of the quote fragments joined with \n (as filled into quote_text),
     * which is a substring of the flat texts of all the quotable nodes joined with \n.
     * Returns [start, end) in RichMessageLayout.joinedText or null.
     */
    public static int[] findQuote(MessageObject messageObject, String quote, int quoteOffset) {
        if (messageObject == null || messageObject.richLayout == null || TextUtils.isEmpty(quote)) {
            return null;
        }
        final RichMessageLayout layout = messageObject.richLayout;
        final ArrayList<TL_iv.RichText> nodes = collectNodes(layout.richMessage);
        if (nodes.isEmpty()) {
            return null;
        }
        final int[] nodeStarts = new int[nodes.size()];
        final StringBuilder all = new StringBuilder();
        for (int i = 0; i < nodes.size(); ++i) {
            if (i > 0) all.append('\n');
            nodeStarts[i] = all.length();
            appendFlat(nodes.get(i), all, null);
        }
        final int start = MessageObject.findQuoteStart(all.toString(), quote, quoteOffset);
        if (start < 0) {
            return null;
        }
        final int end = start + quote.length();

        final IdentityHashMap<TL_iv.RichText, int[]> displayed = findDisplayed(layout);
        int startNode = -1, endNode = -1;
        for (int i = 0; i < nodes.size(); ++i) {
            final int nodeEnd = nodeStarts[i] + flatLength(nodes.get(i));
            if (startNode < 0 && start < nodeEnd && displayed.containsKey(nodes.get(i))) {
                startNode = i;
            }
            if (nodeStarts[i] < end && displayed.containsKey(nodes.get(i))) {
                endNode = i;
            }
        }
        if (startNode < 0 || endNode < 0 || startNode > endNode) {
            return null;
        }
        final int[] startPosition = displayed.get(nodes.get(startNode));
        final int[] endPosition = displayed.get(nodes.get(endNode));
        final int startOffset = flatToFormatted(layout, nodes.get(startNode), startPosition[2], Math.max(0, start - nodeStarts[startNode]), false);
        final int endOffset = flatToFormatted(layout, nodes.get(endNode), endPosition[2], Math.min(flatLength(nodes.get(endNode)), end - nodeStarts[endNode]), true);
        final int joinedStart = startPosition[0] + Math.min(startOffset, startPosition[1] - startPosition[0]);
        final int joinedEnd = endPosition[0] + Math.min(endOffset, endPosition[1] - endPosition[0]);
        if (joinedEnd <= joinedStart) {
            return null;
        }
        return new int[] { joinedStart, joinedEnd };
    }

    /** whether the flat text of a quote is still in the message, see findQuote() */
    public static boolean containsQuote(TL_iv.RichMessage message, String quote) {
        if (message == null || TextUtils.isEmpty(quote)) {
            return false;
        }
        final ArrayList<TL_iv.RichText> nodes = collectNodes(message);
        final StringBuilder all = new StringBuilder();
        for (int i = 0; i < nodes.size(); ++i) {
            if (i > 0) all.append('\n');
            appendFlat(nodes.get(i), all, null);
        }
        return all.indexOf(quote) >= 0;
    }

    /** Collapsed details blocks hiding a part of the quote, see findQuote() */
    public static ArrayList<RichMessageLayout.RichDetailsBlock> findCollapsedDetails(MessageObject messageObject, String quote, int quoteOffset) {
        final ArrayList<RichMessageLayout.RichDetailsBlock> result = new ArrayList<>();
        if (messageObject == null || messageObject.richLayout == null || TextUtils.isEmpty(quote)) {
            return result;
        }
        final RichMessageLayout layout = messageObject.richLayout;
        final ArrayList<TL_iv.RichText> nodes = collectNodes(layout.richMessage);
        final int[] nodeStarts = new int[nodes.size()];
        final StringBuilder all = new StringBuilder();
        for (int i = 0; i < nodes.size(); ++i) {
            if (i > 0) all.append('\n');
            nodeStarts[i] = all.length();
            appendFlat(nodes.get(i), all, null);
        }
        final int start = MessageObject.findQuoteStart(all.toString(), quote, quoteOffset);
        if (start < 0) {
            return result;
        }
        final int end = start + quote.length();

        // root node -> block containing it, visible or not
        final IdentityHashMap<TL_iv.RichText, RichMessageLayout.RichBlock> blocks = new IdentityHashMap<>();
        for (RichMessageLayout.RichBlock block : layout.blocks) {
            final TextSelectionHelper.TextLayoutBlock[] texts = block.getText();
            if (texts == null) continue;
            for (TextSelectionHelper.TextLayoutBlock tb : texts) {
                final CharSequence text = getText(tb);
                if (!(text instanceof Spanned)) continue;
                for (RichMessageLayout.QuoteRootSpan span : ((Spanned) text).getSpans(0, text.length(), RichMessageLayout.QuoteRootSpan.class)) {
                    if (!blocks.containsKey(span.text)) {
                        blocks.put(span.text, block);
                    }
                }
            }
        }
        for (int i = 0; i < nodes.size(); ++i) {
            final int nodeEnd = nodeStarts[i] + flatLength(nodes.get(i));
            if (nodeEnd <= start || nodeStarts[i] >= end) continue;
            final RichMessageLayout.RichBlock block = blocks.get(nodes.get(i));
            if (block == null || block.isVisible()) continue;
            for (RichMessageLayout.RichDetailsBlock d = block.parentDetails; d != null; d = d.parentDetails) {
                if (!d.isOpen() && !result.contains(d)) {
                    result.add(d);
                }
            }
        }
        return result;
    }

    /** root node -> [start, end, flags] of its text in RichMessageLayout.joinedText */
    private static IdentityHashMap<TL_iv.RichText, int[]> findDisplayed(RichMessageLayout layout) {
        final IdentityHashMap<TL_iv.RichText, int[]> result = new IdentityHashMap<>();
        for (int i = 0; i < layout.textBlocks.size(); ++i) {
            final CharSequence text = getText(layout.textBlocks.get(i));
            if (!(text instanceof Spanned)) continue;
            final Spanned spanned = (Spanned) text;
            final int blockStart = layout.textBlockCharOffsets.get(i);
            for (RichMessageLayout.QuoteRootSpan span : spanned.getSpans(0, text.length(), RichMessageLayout.QuoteRootSpan.class)) {
                if (result.containsKey(span.text)) continue;
                result.put(span.text, new int[] { blockStart + spanned.getSpanStart(span), blockStart + spanned.getSpanEnd(span), span.flags });
            }
        }
        return result;
    }

    /** converts an offset in the flat text of a node to an offset in its formatted text */
    private static int flatToFormatted(RichMessageLayout layout, TL_iv.RichText node, int flags, int flatOffset, boolean isEnd) {
        final IdentityHashMap<TL_iv.RichText, int[]> ranges = new IdentityHashMap<>();
        final CharSequence formatted = layout.formatTextRecording(node, flags, ranges);
        final int[] position = new int[] { 0 };
        final int result = flatToFormatted(node, ranges, flatOffset, isEnd, position);
        return result < 0 ? formatted.length() : result;
    }

    private static int flatToFormatted(TL_iv.RichText text, IdentityHashMap<TL_iv.RichText, int[]> ranges, int flatOffset, boolean isEnd, int[] position) {
        if (text == null || text instanceof TL_iv.textEmpty || text instanceof TL_iv.textImage) {
            return -1;
        }
        final int[] range = ranges.get(text);
        if (text instanceof TL_iv.textConcat) {
            for (TL_iv.RichText child : text.texts) {
                final int result = flatToFormatted(child, ranges, flatOffset, isEnd, position);
                if (result >= 0) return result;
            }
            return -1;
        }
        if (text instanceof TL_iv.textDiff) {
            final TL_iv.textDiff diff = (TL_iv.textDiff) text;
            return flatToFormatted(RichTextStyle.isEmpty(diff.text) ? diff.old_text : diff.text, ranges, flatOffset, isEnd, position);
        }
        final boolean atomic = text instanceof TL_iv.textPlain || text instanceof TL_iv.textMath || text instanceof TL_iv.textCustomEmoji || text instanceof TL_iv.textButton;
        if (!atomic) {
            return flatToFormatted(text.text, ranges, flatOffset, isEnd, position);
        }
        final int length = flatLength(text);
        final int local = flatOffset - position[0];
        position[0] += length;
        if (range == null || (isEnd ? local > length : local >= length)) {
            return -1;
        }
        if (text instanceof TL_iv.textPlain && range[1] - range[0] == length) {
            return range[0] + Math.max(0, local);
        }
        // math, emoji and buttons aren't cut: either the start or the end of the node
        return !isEnd || local <= 0 ? range[0] : range[1];
    }

    private static class Range {
        final RichMessageLayout layout;
        final ArrayList<TL_iv.RichText> nodes;
        final int fromIndex, fromOffset;
        final Sliced first;
        int toIndex, toOffset;
        Sliced last;

        Range(RichMessageLayout layout, ArrayList<TL_iv.RichText> nodes, int fromIndex, int fromOffset, Sliced first, int toIndex, int toOffset, Sliced last) {
            this.layout = layout;
            this.nodes = nodes;
            this.fromIndex = fromIndex;
            this.fromOffset = fromOffset;
            this.first = first;
            this.toIndex = toIndex;
            this.toOffset = toOffset;
            this.last = last;
        }

        void setEnd(int index, int offset) {
            if (index != toIndex) {
                last = null;
            }
            toIndex = index;
            toOffset = offset;
        }

        int startOffset(int i) {
            return i == fromIndex ? fromOffset : 0;
        }

        TL_iv.RichText fragment(int i) {
            final int from = startOffset(i);
            final int to = i == toIndex ? toOffset : Integer.MAX_VALUE;
            if (from == 0 && to == Integer.MAX_VALUE) {
                return nodes.get(i);
            }
            return slicer(i).slice(from, to);
        }

        /** converts an offset in the flat text of fragment(i) to an offset in the formatted text of nodes[i], atomic nodes are left out */
        int formattedOffset(int i, int flatOffset) {
            final Sliced slicer = slicer(i);
            if (i == fromIndex && fromOffset > 0) {
                final TL_iv.RichText prefix = first.slice(0, fromOffset);
                if (prefix != null) {
                    flatOffset += flatLength(prefix);
                }
            }
            return flatToFormatted(layout, nodes.get(i), slicer.flags, flatOffset, false);
        }

        Sliced slicer(int i) {
            if (i == fromIndex) return first;
            if (i == toIndex) {
                if (last == null) {
                    last = Sliced.ofNode(layout, nodes.get(i));
                }
                return last;
            }
            return Sliced.ofNode(layout, nodes.get(i));
        }
    }

    private static Position resolve(RichMessageLayout layout, int position, boolean forward, IdentityHashMap<TL_iv.RichText, Integer> indices) {
        final int count = layout.textBlocks.size();
        final int step = forward ? 1 : -1;
        int i = forward ? 0 : count - 1;
        // find the text block containing the position
        for (; forward ? i < count : i >= 0; i += step) {
            final int blockStart = layout.textBlockCharOffsets.get(i);
            final CharSequence text = getText(layout.textBlocks.get(i));
            final int blockEnd = blockStart + (text == null ? 0 : text.length());
            if (forward ? blockEnd > position : blockStart < position) {
                break;
            }
        }
        boolean firstBlock = true;
        for (; forward ? i < count : i >= 0; i += step, firstBlock = false) {
            final int blockStart = layout.textBlockCharOffsets.get(i);
            final CharSequence text = getText(layout.textBlocks.get(i));
            if (!(text instanceof Spanned)) continue;
            final Spanned spanned = (Spanned) text;
            final int local = firstBlock ? Math.max(0, Math.min(text.length(), position - blockStart)) : (forward ? 0 : text.length());
            final RichMessageLayout.QuoteRootSpan[] spans = spanned.getSpans(0, text.length(), RichMessageLayout.QuoteRootSpan.class);
            if (spans == null || spans.length == 0) continue;
            Arrays.sort(spans, (a, b) -> forward ? spanned.getSpanStart(a) - spanned.getSpanStart(b) : spanned.getSpanEnd(b) - spanned.getSpanEnd(a));
            for (RichMessageLayout.QuoteRootSpan span : spans) {
                if (!indices.containsKey(span.text)) continue;
                final int spanStart = spanned.getSpanStart(span);
                final int spanEnd = spanned.getSpanEnd(span);
                if (forward ? spanEnd > local : spanStart < local) {
                    final int offset = forward ? Math.max(local, spanStart) - spanStart : Math.min(local, spanEnd) - spanStart;
                    return new Position(span, offset, text, spanStart);
                }
            }
        }
        return null;
    }

    private static CharSequence getText(TextSelectionHelper.TextLayoutBlock block) {
        final Layout layout = block == null ? null : block.getLayout();
        return layout == null ? null : layout.getText();
    }

    /* ---------- slicing ---------- */

    private static class Sliced {
        final TL_iv.RichText root;
        final IdentityHashMap<TL_iv.RichText, int[]> ranges;
        final int length;
        final int flags;

        Sliced(TL_iv.RichText root, IdentityHashMap<TL_iv.RichText, int[]> ranges, int length, int flags) {
            this.root = root;
            this.ranges = ranges;
            this.length = length;
            this.flags = flags;
        }

        static Sliced of(RichMessageLayout layout, Position position) {
            final IdentityHashMap<TL_iv.RichText, int[]> ranges = new IdentityHashMap<>();
            final CharSequence formatted = layout.formatTextRecording(position.root.text, position.root.flags, ranges);
            // the laid out text must be exactly what we've just formatted, otherwise the offsets don't match
            final int spanLength = ((Spanned) position.text).getSpanEnd(position.root) - position.textStart;
            if (formatted.length() != spanLength || !TextUtils.regionMatches(formatted, 0, position.text, position.textStart, spanLength)) {
                FileLog.e("RichQuote: laid out text doesn't match the formatted one");
                return null;
            }
            return new Sliced(position.root.text, ranges, formatted.length(), position.root.flags);
        }

        static Sliced ofNode(RichMessageLayout layout, TL_iv.RichText node) {
            final IdentityHashMap<TL_iv.RichText, int[]> ranges = new IdentityHashMap<>();
            final CharSequence formatted = layout.formatTextRecording(node, 0, ranges);
            return new Sliced(node, ranges, formatted.length(), 0);
        }

        TL_iv.RichText slice(int from, int to) {
            from = Math.max(0, from);
            to = Math.min(length, to);
            if (from >= to) return null;
            if (from == 0 && to == length) return root;
            return slice(root, from, to);
        }

        private TL_iv.RichText slice(TL_iv.RichText text, int from, int to) {
            if (text == null) return null;
            final int[] range = ranges.get(text);
            if (range == null) return null;
            final int start = range[0], end = range[1];
            if (start == end || end <= from || start >= to) {
                return null;
            }
            if (from <= start && end <= to) {
                return text;
            }
            if (text instanceof TL_iv.textPlain) {
                final String s = ((TL_iv.textPlain) text).text;
                if (s == null || s.length() != end - start) {
                    return text;
                }
                int a = Math.max(from, start) - start;
                int b = Math.min(to, end) - start;
                // don't cut surrogate pairs
                if (a > 0 && a < s.length() && Character.isLowSurrogate(s.charAt(a))) a--;
                if (b > 0 && b < s.length() && Character.isLowSurrogate(s.charAt(b))) b++;
                final TL_iv.textPlain plain = new TL_iv.textPlain();
                plain.text = s.substring(a, b);
                return plain;
            }
            if (text instanceof TL_iv.textConcat) {
                final ArrayList<TL_iv.RichText> children = new ArrayList<>();
                for (TL_iv.RichText child : text.texts) {
                    final TL_iv.RichText sliced = slice(child, from, to);
                    if (sliced != null) {
                        children.add(sliced);
                    }
                }
                if (children.isEmpty()) return null;
                if (children.size() == 1) return children.get(0);
                final TL_iv.textConcat concat = new TL_iv.textConcat();
                concat.texts = children;
                return concat;
            }
            if (
                text instanceof TL_iv.textMath || text instanceof TL_iv.textCustomEmoji ||
                text instanceof TL_iv.textDate || text instanceof TL_iv.textDiff ||
                text instanceof TL_iv.textButton || text.text == null
            ) {
                // can't be cut: either whole node or nothing
                return text;
            }
            final TL_iv.RichText child = slice(text.text, from, to);
            if (child == null) return null;
            final TL_iv.RichText copy = copy(text);
            if (copy == null) return null;
            copy.text = child;
            return copy;
        }
    }

    private static TL_iv.RichText copy(TL_iv.RichText text) {
        try {
            return TLObject.deepCopy(text, TL_iv.RichText::TLdeserialize);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    /** textButton can't be sent in a quote, its label is quoted as plain text instead */
    private static TL_iv.RichText withoutButtons(TL_iv.RichText text) {
        if (text == null || !hasButtons(text)) {
            return text;
        }
        if (text instanceof TL_iv.textButton) {
            final TL_iv.textPlain plain = new TL_iv.textPlain();
            plain.text = RichMessageLayout.getString(text.text);
            return plain;
        }
        if (text instanceof TL_iv.textConcat) {
            final TL_iv.textConcat concat = new TL_iv.textConcat();
            for (TL_iv.RichText child : text.texts) {
                concat.texts.add(withoutButtons(child));
            }
            return concat;
        }
        final TL_iv.RichText copy = copy(text);
        if (copy == null) return null;
        copy.text = withoutButtons(text.text);
        return copy;
    }

    private static boolean hasButtons(TL_iv.RichText text) {
        if (text == null) return false;
        if (text instanceof TL_iv.textButton) return true;
        if (text instanceof TL_iv.textConcat) {
            for (TL_iv.RichText child : text.texts) {
                if (hasButtons(child)) return true;
            }
            return false;
        }
        return hasButtons(text.text);
    }

    public static ArrayList<TL_iv.RichText> copyList(ArrayList<TL_iv.RichText> list) {
        return list == null ? null : new ArrayList<>(list);
    }

    public static boolean equals(ArrayList<TL_iv.RichText> a, ArrayList<TL_iv.RichText> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return TextUtils.equals(flatText(a, null), flatText(b, null));
    }
}
