package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.find;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.text.TextUtils;
import org.telegram.messenger.AndroidUtilities;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import java.util.Arrays;
import io.noties.markwon.html.HtmlTag;
import io.noties.markwon.html.MarkwonHtmlParser;
import io.noties.markwon.html.MarkwonHtmlParserImpl;
import ru.noties.jlatexmath.JLatexMathDrawable;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MarkdownParser {

    private static final java.util.regex.Pattern FOOTNOTE_DEF =
            java.util.regex.Pattern.compile("^\\[\\^([^\\]]+)\\]:[ \\t]*(.*)$");
    private static final java.util.regex.Pattern FOOTNOTE_REF =
            java.util.regex.Pattern.compile("\\[\\^([^\\]]+)\\]");
    private static final java.util.regex.Pattern ORDERED_MARKER =
            java.util.regex.Pattern.compile("^(\\d+)[.)]\\s");

    private static final int MAX_RICH_TEXT_LEN = 8192;
    private static final int MAX_FILE_SIZE = 64 * 1024;

    public static boolean isMarkdown(MessageObject msg) {
        if (msg == null) return false;
        return isExtensionMarkdown(msg.getExtension()) || isMimeMarkdown(msg.getMimeType());
    }

    public static boolean isExtensionMarkdown(String ext) {
        return (
            "md".equalsIgnoreCase(ext) ||
            "mkd".equalsIgnoreCase(ext) ||
            "mdwn".equalsIgnoreCase(ext) ||
            "mkdn".equalsIgnoreCase(ext) ||
            "mdown".equalsIgnoreCase(ext) ||
            "markdown".equalsIgnoreCase(ext)
        );
    }

    public static boolean isMimeMarkdown(String mime) {
        if (mime == null) return false;
        final String lmime = mime.toLowerCase();
        return (
            lmime.startsWith("text/markdown") ||
            lmime.startsWith("text/x-markdown") ||
            lmime.startsWith("text/x-web-markdown")
        );
    }

    public static TLRPC.WebPage fromMarkdown(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) return null;
        final TLRPC.Document document = messageObject.getDocument();
        if (document == null) return null;

        File file = null;
        if (!TextUtils.isEmpty(messageObject.messageOwner.attachPath)) {
            file = new File(messageObject.messageOwner.attachPath);
        }
        if (file == null || !file.exists()) {
            file = FileLoader.getInstance(messageObject.currentAccount).getPathToMessage(messageObject.messageOwner, true);
        }
        if (file == null || !file.exists()) {
            file = FileLoader.getInstance(messageObject.currentAccount).getPathToMessage(messageObject.messageOwner, true, true);
        }
        if (file == null || !file.exists()) return null;
        if (file.length() > MAX_FILE_SIZE) return null;

        String filename = null;
        final TLRPC.TL_documentAttributeFilename attr1 = find(document.attributes, TLRPC.TL_documentAttributeFilename.class);
        if (attr1 != null) filename = attr1.file_name;

        final TLRPC.TL_webPage webpage = new TLRPC.TL_webPage();

        webpage.url = filename == null ? "" : filename;
        webpage.display_url = filename == null ? "" : filename;
        if (!TextUtils.isEmpty(filename)) {
            webpage.flags |= TLObject.FLAG_2;
            webpage.title = filename;
        }

        final TLRPC.TL_page page = new TLRPC.TL_page();
        page.local = file;
        page.url = webpage.url;
        try {
            String fileText;
            try (FileInputStream fis = new FileInputStream(file)) {
                final byte[] bytes = new byte[(int) file.length()];
                fis.read(bytes);
                fileText = new String(bytes, StandardCharsets.UTF_8);
            }
            if (fileText.length() > MAX_FILE_SIZE) return null;

            final java.util.LinkedHashMap<String, String> footnotes = new java.util.LinkedHashMap<>();
            fileText = extractFootnoteDefs(fileText, footnotes);
            fileText = rewriteFootnoteRefs(fileText);

            final java.util.List<Extension> extensions = Arrays.asList(
                StrikethroughExtension.create(),
                TablesExtension.create()
            );
            final io.noties.markwon.inlineparser.MarkwonInlineParserPlugin inlinePlugin =
                io.noties.markwon.inlineparser.MarkwonInlineParserPlugin.create();
            final io.noties.markwon.ext.latex.JLatexMathPlugin latexPlugin =
                io.noties.markwon.ext.latex.JLatexMathPlugin.create(
                    AndroidUtilities.dp(18),
                    b -> b.inlinesEnabled(true)
                );
            latexPlugin.configure(new io.noties.markwon.MarkwonPlugin.Registry() {
                @Override
                public <P extends io.noties.markwon.MarkwonPlugin> P require(Class<P> plugin) {
                    if (plugin == io.noties.markwon.inlineparser.MarkwonInlineParserPlugin.class) {
                        @SuppressWarnings("unchecked") final P p = (P) inlinePlugin;
                        return p;
                    }
                    throw new IllegalStateException("plugin not registered: " + plugin);
                }
                @Override
                public <P extends io.noties.markwon.MarkwonPlugin> void require(
                        Class<P> plugin,
                        io.noties.markwon.MarkwonPlugin.Action<? super P> action) {
                    action.apply(require(plugin));
                }
            });
            inlinePlugin.factoryBuilder().addInlineProcessor(new SingleDollarLatexInlineProcessor());
            final Parser.Builder parserBuilder = Parser.builder().extensions(extensions);
            inlinePlugin.configureParser(parserBuilder);
            latexPlugin.configureParser(parserBuilder);
            final Parser parser = parserBuilder.build();
            final java.util.ArrayDeque<String> orderedMarkers = scanOrderedListMarkers(fileText);
            final BlockVisitor blockVisitor = new BlockVisitor(page.blocks, orderedMarkers);
            parser.parse(fileText).accept(blockVisitor);
            blockVisitor.finish();
            appendFootnotes(parser, page.blocks, footnotes);
            if (blockVisitor.title != null) {
                final String titleText = richTextToString(blockVisitor.title);
                if (!TextUtils.isEmpty(titleText)) {
                    webpage.flags |= TLObject.FLAG_2;
                    webpage.title = titleText;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }

        webpage.flags |= TLObject.FLAG_10;
        webpage.cached_page = page;

        return webpage;
    }

    public static class SingleDollarLatexInlineProcessor extends io.noties.markwon.inlineparser.InlineProcessor {
        private static final java.util.regex.Pattern RE =
            java.util.regex.Pattern.compile("\\$([^\\s\\$][^\\$]*?)(?<!\\s)\\$(?![0-9])");

        @Override public char specialCharacter() { return '$'; }

        @Override
        protected org.commonmark.node.Node parse() {
            final String m = match(RE);
            if (m == null) return null;
            final io.noties.markwon.ext.latex.JLatexMathNode node =
                new io.noties.markwon.ext.latex.JLatexMathNode();
            node.latex(m.substring(1, m.length() - 1));
            return node;
        }
    }

    private static TLRPC.TL_textLatex makeLatex(String raw) {
        final TLRPC.TL_textLatex out = new TLRPC.TL_textLatex();
        out.raw = raw == null ? "" : raw.trim();
        try {
            final JLatexMathDrawable drawable =
                JLatexMathDrawable.builder(out.raw)
                    .textSize(AndroidUtilities.dp(20))
                    .build();
            final int w = drawable.getIntrinsicWidth();
            final int h = drawable.getIntrinsicHeight();
            if (w > 0 && h > 0) {
                final Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
                drawable.setBounds(0, 0, w, h);
                drawable.draw(new Canvas(bm));
                out.w = w;
                out.h = h;
                try {
                    out.depth = drawable.icon().getIconDepth();
                } catch (Throwable t) {
                    FileLog.e(t);
                }
                out.bitmap = bm;
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return out;
    }

    private static java.util.ArrayDeque<String> scanOrderedListMarkers(String source) {
        final java.util.ArrayDeque<String> q = new java.util.ArrayDeque<>();
        boolean inFence = false;
        String fence = null;
        for (String line : source.split("\n", -1)) {
            int lead = 0;
            while (lead < line.length() && lead < 3 && line.charAt(lead) == ' ') lead++;
            final String body = line.substring(lead);
            if (inFence) {
                if (body.startsWith(fence)) {
                    inFence = false;
                    fence = null;
                }
                continue;
            }
            if (body.startsWith("```")) { inFence = true; fence = "```"; continue; }
            if (body.startsWith("~~~")) { inFence = true; fence = "~~~"; continue; }

            final java.util.regex.Matcher m = ORDERED_MARKER.matcher(line);
            if (m.find()) q.add(m.group(1));
        }
        return q;
    }

    private static String extractFootnoteDefs(String source, java.util.LinkedHashMap<String, String> defs) {
        final String[] lines = source.split("\n", -1);
        final StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            final java.util.regex.Matcher m = FOOTNOTE_DEF.matcher(lines[i]);
            if (m.matches()) {
                final String id = m.group(1);
                final StringBuilder body = new StringBuilder(m.group(2));
                int j = i + 1;
                while (j < lines.length) {
                    final String l = lines[j];
                    if (l.startsWith("    ") || l.startsWith("\t")) {
                        body.append('\n').append(l.startsWith("\t") ? l.substring(1) : l.substring(4));
                        j++;
                    } else if (l.trim().isEmpty()) {
                        int k = j + 1;
                        while (k < lines.length && lines[k].trim().isEmpty()) k++;
                        if (k < lines.length && (lines[k].startsWith("    ") || lines[k].startsWith("\t"))) {
                            body.append('\n');
                            j++;
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                }
                defs.put(id, body.toString().trim());
                i = j;
            } else {
                out.append(lines[i]);
                if (i < lines.length - 1) out.append('\n');
                i++;
            }
        }
        return out.toString();
    }

    private static String rewriteFootnoteRefs(String source) {
        final java.util.regex.Matcher m = FOOTNOTE_REF.matcher(source);
        final StringBuffer sb = new StringBuffer();
        while (m.find()) {
            final String id = m.group(1);
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
                "<sup>[\\[" + id + "\\]](#fn-" + id + ")</sup>"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static void appendFootnotes(Parser parser, ArrayList<TLRPC.PageBlock> blocks,
                                         java.util.LinkedHashMap<String, String> defs) {
        if (defs.isEmpty()) return;

        final TLRPC.TL_pageBlockDetails details = new TLRPC.TL_pageBlockDetails();
        details.title = bold(LocaleController.getString(R.string.InstantViewReferences));

        for (java.util.Map.Entry<String, String> entry : defs.entrySet()) {
            final String id = entry.getKey();
            final String body = entry.getValue();

            final ArrayList<TLRPC.PageBlock> defBlocks = new ArrayList<>();
            final BlockVisitor defVisitor = new BlockVisitor(defBlocks);
            parser.parse(body).accept(defVisitor);
            defVisitor.finish();

            final TLRPC.RichText combined = first(combineParagraphs(defBlocks));
            final TLRPC.TL_textAnchor anchor = new TLRPC.TL_textAnchor();
            anchor.name = "fn-" + id;
            anchor.text = combined;

            final TLRPC.TL_pageBlockParagraph p = new TLRPC.TL_pageBlockParagraph();
            p.text = concat(bold(id + ". "), anchor);
            details.blocks.add(p);
        }

        blocks.add(details);
    }

    private static TLRPC.RichText combineParagraphs(ArrayList<TLRPC.PageBlock> blocks) {
        final TLRPC.TL_textConcat concat = new TLRPC.TL_textConcat();
        for (TLRPC.PageBlock b : blocks) {
            TLRPC.RichText text = null;
            if (b instanceof TLRPC.TL_pageBlockParagraph) text = ((TLRPC.TL_pageBlockParagraph) b).text;
            else if (b instanceof TLRPC.TL_pageBlockHeader) text = ((TLRPC.TL_pageBlockHeader) b).text;
            else if (b instanceof TLRPC.TL_pageBlockSubheader) text = ((TLRPC.TL_pageBlockSubheader) b).text;
            else if (b instanceof TLRPC.TL_pageBlockTitle) text = ((TLRPC.TL_pageBlockTitle) b).text;
            if (text == null || text instanceof TLRPC.TL_textEmpty) continue;
            if (!concat.texts.isEmpty()) concat.texts.add(plain("\n\n"));
            concat.texts.add(text);
        }
        if (concat.texts.isEmpty()) return new TLRPC.TL_textEmpty();
        if (concat.texts.size() == 1) return concat.texts.get(0);
        return concat;
    }

    private static TLRPC.RichText richTextOf(Node node, TLRPC.PageBlock block) {
        final RichTextParser p = new RichTextParser(block);
        node.accept(p);
        return materializeStyles(pairHtml(p.getText()));
    }

    private static TLRPC.RichText pairHtml(TLRPC.RichText rt) {
        if (rt == null) return null;
        if (rt instanceof TLRPC.TL_textConcat) {
            final TLRPC.TL_textConcat concat = (TLRPC.TL_textConcat) rt;
            for (int i = 0; i < concat.texts.size(); i++) {
                concat.texts.set(i, pairHtml(concat.texts.get(i)));
            }
            return pairHtmlConcat(concat);
        }
        TLRPC.RichText cursor = rt;
        while (cursor != null && cursor.text != null) {
            if (cursor.text instanceof TLRPC.TL_textConcat) {
                cursor.text = pairHtml(cursor.text);
                break;
            }
            cursor = cursor.text;
        }
        return rt;
    }

    private static TLRPC.RichText pairHtmlConcat(TLRPC.TL_textConcat concat) {
        final StringBuilder buf = new StringBuilder();
        final List<TLRPC.RichText> pieces = new ArrayList<>();
        final List<int[]> ranges = new ArrayList<>();
        final MarkwonHtmlParser parser = MarkwonHtmlParserImpl.create();

        for (TLRPC.RichText piece : concat.texts) {
            if (piece instanceof TLRPC.TL_textPlain && looksLikeHtmlTag(((TLRPC.TL_textPlain) piece).text)) {
                final int before = buf.length();
                try {
                    parser.processFragment(buf, ((TLRPC.TL_textPlain) piece).text);
                } catch (Throwable t) {
                    FileLog.e(t);
                    buf.append(((TLRPC.TL_textPlain) piece).text);
                }
                final int after = buf.length();
                if (after > before) {
                    pieces.add(plain(buf.substring(before, after)));
                    ranges.add(new int[]{before, after});
                }
            } else {
                final String visible = richTextToString(piece);
                final int before = buf.length();
                buf.append(visible);
                final int after = buf.length();
                pieces.add(piece);
                ranges.add(new int[]{before, after});
            }
        }

        final List<HtmlTag> tags = new ArrayList<>();
        try {
            parser.flushInlineTags(buf.length(), list -> tags.addAll(list));
        } catch (Throwable t) {
            FileLog.e(t);
        }
        try {
            parser.flushBlockTags(buf.length(), list -> flattenBlocks(list, tags));
        } catch (Throwable t) {
            FileLog.e(t);
        }

        Collections.sort(tags, Comparator.comparingInt(a -> a.end() - a.start()));

        for (HtmlTag tag : tags) {
            if (!tag.isClosed()) continue;
            final int tagStart = tag.start();
            final int tagEnd = tag.end();
            int firstIdx = -1, lastIdx = -1;
            for (int i = 0; i < ranges.size(); i++) {
                final int s = ranges.get(i)[0];
                final int e = ranges.get(i)[1];
                if (s >= tagStart && e <= tagEnd) {
                    if (firstIdx == -1) firstIdx = i;
                    lastIdx = i;
                }
            }
            if (firstIdx == -1) continue;
            final TLRPC.RichText inner;
            if (firstIdx == lastIdx) {
                inner = pieces.get(firstIdx);
            } else {
                final TLRPC.TL_textConcat sub = new TLRPC.TL_textConcat();
                for (int i = firstIdx; i <= lastIdx; i++) sub.texts.add(pieces.get(i));
                inner = sub;
            }
            final TLRPC.RichText wrapped = wrapByTag(tag.name(), inner);
            for (int i = lastIdx; i >= firstIdx; i--) {
                pieces.remove(i);
                ranges.remove(i);
            }
            pieces.add(firstIdx, wrapped);
            ranges.add(firstIdx, new int[]{tagStart, tagEnd});
        }

        if (pieces.isEmpty()) return new TLRPC.TL_textEmpty();
        if (pieces.size() == 1) {
            final TLRPC.RichText only = pieces.get(0);
            if (only instanceof TLRPC.TL_textPlain || only instanceof TLRPC.TL_textEmpty) {
                return only;
            }
        }
        final TLRPC.TL_textConcat result = new TLRPC.TL_textConcat();
        result.texts.addAll(pieces);
        return result;
    }

    private static void flattenBlocks(List<HtmlTag.Block> roots, List<HtmlTag> out) {
        for (HtmlTag.Block t : roots) {
            out.add(t);
            flattenBlocks(t.children(), out);
        }
    }

    private static boolean looksLikeHtmlTag(String s) {
        if (s == null || s.length() < 2) return false;
        return s.charAt(0) == '<' && s.charAt(s.length() - 1) == '>';
    }

    /** Internal-only RichText subclass; never escapes richTextOf. */
    private static final class TextStyle extends TLRPC.RichText {
        int styleFlags;
    }

    private static int flagFor(String name) {
        if (name == null) return 0;
        switch (name.toLowerCase()) {
            case "u": case "ins":            return org.telegram.ui.ArticleViewer.TEXT_FLAG_UNDERLINE;
            case "mark":                     return org.telegram.ui.ArticleViewer.TEXT_FLAG_MARKED;
            case "sub":                      return org.telegram.ui.ArticleViewer.TEXT_FLAG_SUB;
            case "sup":                      return org.telegram.ui.ArticleViewer.TEXT_FLAG_SUP;
            case "del": case "s": case "strike": return org.telegram.ui.ArticleViewer.TEXT_FLAG_STRIKE;
            case "b": case "strong":         return org.telegram.ui.ArticleViewer.TEXT_FLAG_MEDIUM;
            case "i": case "em":             return org.telegram.ui.ArticleViewer.TEXT_FLAG_ITALIC;
            case "code": case "tt":          return org.telegram.ui.ArticleViewer.TEXT_FLAG_MONO;
            default:                         return 0;
        }
    }

    private static TLRPC.RichText wrapByTag(String name, TLRPC.RichText inner) {
        final int flag = flagFor(name);
        if (flag == 0) return inner;
        if (inner instanceof TextStyle) {
            ((TextStyle) inner).styleFlags |= flag;
            return inner;
        }
        final TextStyle ts = new TextStyle();
        ts.styleFlags = flag;
        ts.text = inner;
        return ts;
    }

    private static TLRPC.RichText materializeStyles(TLRPC.RichText rt) {
        if (rt == null) return null;
        if (rt instanceof TLRPC.TL_textConcat) {
            final TLRPC.TL_textConcat concat = (TLRPC.TL_textConcat) rt;
            for (int i = 0; i < concat.texts.size(); i++) {
                concat.texts.set(i, materializeStyles(concat.texts.get(i)));
            }
            return concat;
        }
        if (rt instanceof TextStyle) {
            final TextStyle ts = (TextStyle) rt;
            TLRPC.RichText cur = materializeStyles(ts.text);
            final int f = ts.styleFlags;
            if ((f & org.telegram.ui.ArticleViewer.TEXT_FLAG_MONO)      != 0) cur = wrapStyle(new TLRPC.TL_textFixed(),       cur);
            if ((f & org.telegram.ui.ArticleViewer.TEXT_FLAG_STRIKE)    != 0) cur = wrapStyle(new TLRPC.TL_textStrike(),      cur);
            if ((f & org.telegram.ui.ArticleViewer.TEXT_FLAG_UNDERLINE) != 0) cur = wrapStyle(new TLRPC.TL_textUnderline(),   cur);
            if ((f & org.telegram.ui.ArticleViewer.TEXT_FLAG_MARKED)    != 0) cur = wrapStyle(new TLRPC.TL_textMarked(),      cur);
            if ((f & org.telegram.ui.ArticleViewer.TEXT_FLAG_SUB)       != 0) cur = wrapStyle(new TLRPC.TL_textSubscript(),   cur);
            if ((f & org.telegram.ui.ArticleViewer.TEXT_FLAG_SUP)       != 0) cur = wrapStyle(new TLRPC.TL_textSuperscript(), cur);
            if ((f & org.telegram.ui.ArticleViewer.TEXT_FLAG_ITALIC)    != 0) cur = wrapStyle(new TLRPC.TL_textItalic(),      cur);
            if ((f & org.telegram.ui.ArticleViewer.TEXT_FLAG_MEDIUM)    != 0) cur = wrapStyle(new TLRPC.TL_textBold(),        cur);
            return cur;
        }
        if (rt.text != null) rt.text = materializeStyles(rt.text);
        return rt;
    }

    private static TLRPC.RichText wrapStyle(TLRPC.RichText wrapper, TLRPC.RichText inner) {
        wrapper.text = inner;
        return wrapper;
    }

    private static TLRPC.RichText plain(String s) {
        final TLRPC.TL_textPlain p = new TLRPC.TL_textPlain();
        p.text = s == null ? "" : s;
        return p;
    }

    private static TLRPC.RichText bold(String s) {
        final TLRPC.TL_textBold p = new TLRPC.TL_textBold();
        p.text = plain(s);
        return p;
    }

    private static TLRPC.RichText concat(TLRPC.RichText ...texts) {
        final TLRPC.TL_textConcat c = new TLRPC.TL_textConcat();
        for (TLRPC.RichText t : texts)
            c.texts.add(t);
        return c;
    }

    private static int richTextLength(TLRPC.RichText rt) {
        if (rt == null || rt instanceof TLRPC.TL_textEmpty) return 0;
        if (rt instanceof TLRPC.TL_textPlain) {
            final String s = ((TLRPC.TL_textPlain) rt).text;
            return s == null ? 0 : s.length();
        }
        if (rt instanceof TLRPC.TL_textConcat) {
            int sum = 0;
            for (TLRPC.RichText child : rt.texts) sum += richTextLength(child);
            return sum;
        }
        return richTextLength(rt.text);
    }

    private static TLRPC.RichText first(TLRPC.RichText rt) {
        if (rt == null) return null;
        if (richTextLength(rt) <= MAX_RICH_TEXT_LEN) return rt;
        final String s = richTextToString(rt);
        return plain(s.substring(0, Math.min(s.length(), MAX_RICH_TEXT_LEN)));
    }

    private static java.util.List<TLRPC.RichText> split(TLRPC.RichText rt) {
        if (rt == null) return java.util.Collections.singletonList(plain(""));
        if (richTextLength(rt) <= MAX_RICH_TEXT_LEN) return java.util.Collections.singletonList(rt);
        final String s = richTextToString(rt);
        final java.util.List<TLRPC.RichText> out = new ArrayList<>();
        int pos = 0;
        while (pos < s.length()) {
            if (s.length() - pos <= MAX_RICH_TEXT_LEN) {
                out.add(plain(s.substring(pos)));
                break;
            }
            final int hardEnd = pos + MAX_RICH_TEXT_LEN;
            int cut = s.lastIndexOf('\n', hardEnd - 1);
            if (cut <= pos) cut = s.lastIndexOf(' ', hardEnd - 1);
            int skip = 0;
            if (cut <= pos) {
                cut = hardEnd;
            } else {
                skip = 1;
            }
            out.add(plain(s.substring(pos, cut)));
            pos = cut + skip;
        }
        return out;
    }

    public static String richTextToString(TLRPC.RichText text) {
        if (text == null || text instanceof TLRPC.TL_textEmpty) return "";
        if (text instanceof TLRPC.TL_textPlain) return ((TLRPC.TL_textPlain) text).text;
        if (text instanceof TLRPC.TL_textConcat) {
            final StringBuilder sb = new StringBuilder();
            for (TLRPC.RichText child : text.texts) {
                sb.append(richTextToString(child));
            }
            return sb.toString();
        }
        return richTextToString(text.text);
    }

    public static class BlockVisitor extends AbstractVisitor {

        public final ArrayList<TLRPC.PageBlock> blocks;
        public TLRPC.RichText title;

        private static final class Item {
            final TLRPC.PageBlock block;
            final int start;
            final int end;
            Item(TLRPC.PageBlock block, int start, int end) {
                this.block = block;
                this.start = start;
                this.end = end;
            }
        }

        private final List<Item> items = new ArrayList<>();
        private final StringBuilder synth = new StringBuilder();
        private final MarkwonHtmlParser htmlParser = MarkwonHtmlParserImpl.create();
        private final java.util.ArrayDeque<String> orderedMarkers;

        public BlockVisitor(ArrayList<TLRPC.PageBlock> blocks) {
            this(blocks, new java.util.ArrayDeque<>());
        }

        public BlockVisitor(ArrayList<TLRPC.PageBlock> blocks, java.util.ArrayDeque<String> orderedMarkers) {
            this.blocks = blocks;
            this.orderedMarkers = orderedMarkers;
        }

        private void emit(TLRPC.PageBlock b) {
            final int s = synth.length();
            synth.append((char) 1);
            items.add(new Item(b, s, synth.length()));
        }

        public void finish() {
            final List<HtmlTag.Block> rootTags = new ArrayList<>();
            try {
                htmlParser.flushBlockTags(synth.length(), rootTags::addAll);
            } catch (Throwable t) {
                FileLog.e(t);
            }
            final List<HtmlTag.Block> tags = new ArrayList<>();
            flattenBlockTags(rootTags, tags);

            final java.util.Map<Integer, Item> sentinelByPos = new java.util.HashMap<>();
            for (Item it : items) sentinelByPos.put(it.start, it);

            final java.util.TreeSet<Integer> boundaries = new java.util.TreeSet<>();
            boundaries.add(0);
            boundaries.add(synth.length());
            for (Integer p : sentinelByPos.keySet()) {
                boundaries.add(p);
                boundaries.add(p + 1);
            }
            for (HtmlTag.Block t : tags) {
                boundaries.add(t.start());
                boundaries.add(t.end());
            }

            final List<Item> sliced = new ArrayList<>();
            Integer prev = null;
            for (Integer point : boundaries) {
                if (prev != null && point > prev) {
                    final int from = prev;
                    final int to = point;
                    if (to - from == 1 && sentinelByPos.containsKey(from)) {
                        sliced.add(sentinelByPos.get(from));
                    } else {
                        final String segment = synth.substring(from, to);
                        final String trimmed = segment.trim();
                        if (!trimmed.isEmpty()) {
                            final TLRPC.TL_pageBlockParagraph p = new TLRPC.TL_pageBlockParagraph();
                            p.text = first(plain(trimmed));
                            sliced.add(new Item(p, from, to));
                        }
                    }
                }
                prev = point;
            }

            Collections.sort(tags, (a, b) -> {
                final int c = Integer.compare(a.start(), b.start());
                return c != 0 ? c : Integer.compare(b.end(), a.end());
            });

            final Scope root = new Scope(null, 0, Integer.MAX_VALUE);
            final java.util.ArrayDeque<Scope> stack = new java.util.ArrayDeque<>();
            stack.push(root);

            int tagIdx = 0;
            for (Item item : sliced) {
                while (tagIdx < tags.size() && tags.get(tagIdx).start() <= item.start) {
                    final HtmlTag.Block tag = tags.get(tagIdx++);
                    if (tag.end() < item.start) continue;
                    while (stack.peek() != root && stack.peek().end <= tag.start()) stack.pop();
                    final Scope scope = new Scope(tag, tag.start(), tag.end());
                    stack.peek().children.add(scope);
                    stack.push(scope);
                }
                while (stack.peek() != root && stack.peek().end <= item.start) stack.pop();
                if (item.block != null) {
                    stack.peek().children.add(item);
                }
            }

            materialize(root.children, blocks);
        }

        private static void flattenBlockTags(List<HtmlTag.Block> roots, List<HtmlTag.Block> out) {
            for (HtmlTag.Block t : roots) {
                out.add(t);
                flattenBlockTags(t.children(), out);
            }
        }

        private static final class Scope {
            final HtmlTag.Block tag;
            final int start;
            final int end;
            final List<Object> children = new ArrayList<>(); // Item or Scope
            Scope(HtmlTag.Block tag, int start, int end) {
                this.tag = tag;
                this.start = start;
                this.end = end;
            }
        }

        private static final int MAX_SCOPE_DEPTH = 64;

        private void materialize(List<Object> children, List<TLRPC.PageBlock> out) {
            materialize(children, out, 0);
        }

        private void materialize(List<Object> children, List<TLRPC.PageBlock> out, int depth) {
            for (Object c : children) {
                if (c instanceof Item) {
                    final TLRPC.PageBlock b = ((Item) c).block;
                    if (b != null) out.add(b);
                } else if (c instanceof Scope) {
                    wrapScope((Scope) c, out, depth);
                }
            }
        }

        private void wrapScope(Scope scope, List<TLRPC.PageBlock> out, int depth) {
            final String name = scope.tag.name() == null ? "" : scope.tag.name().toLowerCase();
            if (depth >= MAX_SCOPE_DEPTH) {
                materialize(scope.children, out, depth + 1);
                return;
            }
            switch (name) {
                case "details": {
                    final TLRPC.TL_pageBlockDetails d = new TLRPC.TL_pageBlockDetails();
                    d.open = scope.tag.attributes() != null && scope.tag.attributes().containsKey("open");
                    d.title = new TLRPC.TL_textEmpty();
                    final List<TLRPC.PageBlock> body = new ArrayList<>();
                    for (Object c : scope.children) {
                        if (c instanceof Scope && "summary".equalsIgnoreCase(((Scope) c).tag.name())) {
                            d.title = scopeToRichText((Scope) c);
                        } else if (c instanceof Item) {
                            final TLRPC.PageBlock ib = ((Item) c).block;
                            if (ib != null) body.add(ib);
                        } else if (c instanceof Scope) {
                            wrapScope((Scope) c, body, depth + 1);
                        }
                    }
                    d.blocks.addAll(body);
                    out.add(d);
                    break;
                }
                case "summary":
                    break;
                case "p":
                case "div":
                case "section":
                case "article":
                case "main":
                case "header":
                case "footer":
                case "aside":
                case "nav":
                default:
                    materialize(scope.children, out, depth + 1);
                    break;
            }
        }

        private TLRPC.RichText scopeToRichText(Scope scope) {
            final StringBuilder sb = new StringBuilder();
            collectText(scope.children, sb);
            final String s = sb.toString().trim();
            return s.isEmpty() ? new TLRPC.TL_textEmpty() : plain(s);
        }

        private void collectText(List<Object> children, StringBuilder sb) {
            for (Object c : children) {
                if (c instanceof Item) {
                    final TLRPC.PageBlock b = ((Item) c).block;
                    if (b instanceof TLRPC.TL_pageBlockParagraph) {
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(richTextToString(((TLRPC.TL_pageBlockParagraph) b).text));
                    } else if (b instanceof TLRPC.TL_pageBlockHeader) {
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(richTextToString(((TLRPC.TL_pageBlockHeader) b).text));
                    } else if (b instanceof TLRPC.TL_pageBlockSubheader) {
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(richTextToString(((TLRPC.TL_pageBlockSubheader) b).text));
                    }
                } else if (c instanceof Scope) {
                    collectText(((Scope) c).children, sb);
                }
            }
        }

        @Override
        public void visit(Heading heading) {
            final TLRPC.RichText text = first(richTextOf(heading, null));
            if (items.isEmpty()) {
                title = text;
            }
            if (heading.getLevel() == 1) {
                final TLRPC.TL_pageBlockTitle b = new TLRPC.TL_pageBlockTitle();
                b.text = text;
                emit(b);
            } else if (heading.getLevel() == 2) {
                final TLRPC.TL_pageBlockHeader b = new TLRPC.TL_pageBlockHeader();
                b.text = text;
                emit(b);
            } else {
                final TLRPC.TL_pageBlockSubheader b = new TLRPC.TL_pageBlockSubheader();
                b.text = text;
                emit(b);
            }
        }

        @Override
        public void visit(Paragraph paragraph) {
            for (TLRPC.RichText text : split(richTextOf(paragraph, null))) {
                final TLRPC.TL_pageBlockParagraph b = new TLRPC.TL_pageBlockParagraph();
                b.text = text;
                emit(b);
            }
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            for (TLRPC.RichText text : split(richTextOf(blockQuote, null))) {
                final TLRPC.TL_pageBlockBlockquote b = new TLRPC.TL_pageBlockBlockquote();
                b.text = text;
                emit(b);
            }
        }

        @Override
        public void visit(ThematicBreak thematicBreak) {
            emit(new TLRPC.TL_pageBlockDivider());
        }

        @Override
        public void visit(FencedCodeBlock fencedCodeBlock) {
            final TLRPC.TL_pageBlockPreformatted b = new TLRPC.TL_pageBlockPreformatted();
            b.text = first(plain(fencedCodeBlock.getLiteral()));
            b.language = fencedCodeBlock.getInfo() == null ? "" : fencedCodeBlock.getInfo();
            emit(b);
        }

        @Override
        public void visit(IndentedCodeBlock indentedCodeBlock) {
            final TLRPC.TL_pageBlockPreformatted b = new TLRPC.TL_pageBlockPreformatted();
            b.text = first(plain(indentedCodeBlock.getLiteral()));
            b.language = "";
            emit(b);
        }

        @Override
        public void visit(BulletList bulletList) {
            final TLRPC.TL_pageBlockList block = new TLRPC.TL_pageBlockList();
            for (Node child = bulletList.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof ListItem) {
                    final int checkbox = stripCheckboxPrefix(child);
                    if (checkbox >= 0) {
                        final TLRPC.TL_pageListItemCheckbox item = new TLRPC.TL_pageListItemCheckbox();
                        item.checked = checkbox == 1;
                        item.text = first(richTextOf(child, block));
                        block.items.add(item);
                    } else {
                        final TLRPC.TL_pageListItemText item = new TLRPC.TL_pageListItemText();
                        item.text = first(richTextOf(child, block));
                        block.items.add(item);
                    }
                }
            }
            emit(block);
        }

        @Override
        public void visit(OrderedList orderedList) {
            final TLRPC.TL_pageBlockOrderedList block = new TLRPC.TL_pageBlockOrderedList();
            final boolean useSource = orderedList.getParent() instanceof org.commonmark.node.Document;
            int n = orderedList.getStartNumber();
            for (Node child = orderedList.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof ListItem) {
                    final String marker = useSource && !orderedMarkers.isEmpty()
                        ? orderedMarkers.poll() : String.valueOf(n++);
                    final int checkbox = stripCheckboxPrefix(child);
                    if (checkbox >= 0) {
                        final TLRPC.TL_pageListOrderedItemCheckbox item = new TLRPC.TL_pageListOrderedItemCheckbox();
                        item.checked = checkbox == 1;
                        item.num = marker;
                        item.text = first(richTextOf(child, block));
                        block.items.add(item);
                    } else {
                        final TLRPC.TL_pageListOrderedItemText item = new TLRPC.TL_pageListOrderedItemText();
                        item.num = marker;
                        item.text = first(richTextOf(child, block));
                        block.items.add(item);
                    }
                }
            }
            emit(block);
        }

        private static int stripCheckboxPrefix(Node listItem) {
            final Node firstBlock = listItem.getFirstChild();
            if (!(firstBlock instanceof Paragraph)) return -1;
            final Node firstInline = firstBlock.getFirstChild();
            if (!(firstInline instanceof Text)) return -1;
            final Text text = (Text) firstInline;
            final String s = text.getLiteral();
            if (s == null || s.length() < 3) return -1;
            final int state;
            if (s.charAt(0) == '[' && s.charAt(2) == ']') {
                final char inner = s.charAt(1);
                if (inner == ' ') state = 0;
                else if (inner == 'x' || inner == 'X') state = 1;
                else return -1;
            } else {
                return -1;
            }
            int strip = 3;
            if (s.length() > strip && s.charAt(strip) == ' ') strip++;
            text.setLiteral(s.substring(strip));
            return state;
        }

        @Override
        public void visit(HtmlBlock htmlBlock) {
            final String literal = htmlBlock.getLiteral();
            if (literal == null) return;
            try {
                htmlParser.processFragment(synth, literal);
            } catch (Throwable t) {
                FileLog.e(t);
                synth.append(literal);
            }
        }

        @Override
        public void visit(CustomBlock customBlock) {
            if (customBlock instanceof TableBlock) {
                emit(buildTable((TableBlock) customBlock));
            } else if (customBlock instanceof io.noties.markwon.ext.latex.JLatexMathBlock) {
                final TLRPC.TL_pageBlockParagraph b = new TLRPC.TL_pageBlockParagraph();
                b.text = makeLatex(((io.noties.markwon.ext.latex.JLatexMathBlock) customBlock).latex());
                emit(b);
            } else {
                super.visit(customBlock);
            }
        }

        private TLRPC.TL_pageBlockTable buildTable(TableBlock table) {
            final TLRPC.TL_pageBlockTable b = new TLRPC.TL_pageBlockTable();
            b.bordered = true;
            b.title = new TLRPC.TL_textEmpty();
            for (Node section = table.getFirstChild(); section != null; section = section.getNext()) {
                final boolean header = section instanceof TableHead;
                if (!header && !(section instanceof TableBody)) continue;
                for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                    if (row instanceof TableRow) {
                        b.rows.add(buildTableRow((TableRow) row, header));
                    }
                }
            }
            return b;
        }

        private TLRPC.TL_pageTableRow buildTableRow(TableRow row, boolean header) {
            final TLRPC.TL_pageTableRow r = new TLRPC.TL_pageTableRow();
            for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                if (cell instanceof TableCell) {
                    r.cells.add(buildTableCell((TableCell) cell, header));
                }
            }
            return r;
        }

        private TLRPC.TL_pageTableCell buildTableCell(TableCell cell, boolean header) {
            final TLRPC.TL_pageTableCell c = new TLRPC.TL_pageTableCell();
            c.header = header || cell.isHeader();
            final TableCell.Alignment align = cell.getAlignment();
            if (align == TableCell.Alignment.CENTER) {
                c.align_center = true;
            } else if (align == TableCell.Alignment.RIGHT) {
                c.align_right = true;
            }
            c.text = first(richTextOf(cell, null));
            c.flags |= TLObject.FLAG_7;
            return c;
        }
    }

    public static class RichTextParser extends AbstractVisitor {

        private static final int MAX_BLOCK_DEPTH = 64;
        private int blockDepth;

        private final TLRPC.PageBlock block;
        private TLRPC.TL_textConcat current = new TLRPC.TL_textConcat();

        public RichTextParser(TLRPC.PageBlock block) {
            this.block = block;
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            if (blockDepth >= MAX_BLOCK_DEPTH) return;
            blockDepth++;
            try { visitChildren(blockQuote); } finally { blockDepth--; }
        }

        @Override
        public void visit(BulletList bulletList) {
            if (blockDepth >= MAX_BLOCK_DEPTH) return;
            blockDepth++;
            try { visitChildren(bulletList); } finally { blockDepth--; }
        }

        @Override
        public void visit(OrderedList orderedList) {
            if (blockDepth >= MAX_BLOCK_DEPTH) return;
            blockDepth++;
            try { visitChildren(orderedList); } finally { blockDepth--; }
        }

        @Override
        public void visit(ListItem listItem) {
            if (blockDepth >= MAX_BLOCK_DEPTH) return;
            blockDepth++;
            try { visitChildren(listItem); } finally { blockDepth--; }
        }

        public TLRPC.RichText getText() {
            return collapse(current);
        }

        private static TLRPC.RichText collapse(TLRPC.TL_textConcat t) {
            if (t.texts.isEmpty()) return new TLRPC.TL_textEmpty();
            if (t.texts.size() == 1) return t.texts.get(0);
            return t;
        }

        private void append(TLRPC.RichText text) {
            current.texts.add(text);
        }

        private TLRPC.RichText collectChildren(Node node) {
            final TLRPC.TL_textConcat parent = current;
            current = new TLRPC.TL_textConcat();
            visitChildren(node);
            final TLRPC.RichText result = collapse(current);
            current = parent;
            return result;
        }

        @Override
        public void visit(Paragraph paragraph) {
            if (!current.texts.isEmpty()) {
                append(plain("\n\n"));
            }
            visitChildren(paragraph);
        }

        @Override
        public void visit(Text text) {
            append(plain(text.getLiteral()));
        }

        @Override
        public void visit(Emphasis emphasis) {
            final TLRPC.TL_textItalic it = new TLRPC.TL_textItalic();
            it.text = collectChildren(emphasis);
            append(it);
        }

        @Override
        public void visit(StrongEmphasis strongEmphasis) {
            final TLRPC.TL_textBold b = new TLRPC.TL_textBold();
            b.text = collectChildren(strongEmphasis);
            append(b);
        }

        @Override
        public void visit(Code code) {
            final TLRPC.TL_textFixed f = new TLRPC.TL_textFixed();
            f.text = plain(code.getLiteral());
            append(f);
        }

        @Override
        public void visit(Link link) {
            String url = link.getDestination() == null ? "" : link.getDestination();
            url = url.trim();

            if (url.startsWith("mailto:")) {
                final TLRPC.TL_textEmail email = new TLRPC.TL_textEmail();
                email.text = collectChildren(link);
                email.email = url.substring(7);
                append(email);
            } else if (url.startsWith("tel:")) {
                final TLRPC.TL_textPhone phone = new TLRPC.TL_textPhone();
                phone.text = collectChildren(link);
                phone.phone = url.substring(4);
                append(phone);
            } else {
                final TLRPC.TL_textUrl u = new TLRPC.TL_textUrl();
                u.text = collectChildren(link);
                u.url = url;
                append(u);
            }
        }

        @Override
        public void visit(Image image) {
            append(collectChildren(image));
        }

        @Override
        public void visit(HardLineBreak hardLineBreak) {
            append(plain("\n"));
        }

        @Override
        public void visit(SoftLineBreak softLineBreak) {
            append(plain(block instanceof TLRPC.TL_pageBlockBlockquote ? "\n" : " "));
        }

        @Override
        public void visit(HtmlInline htmlInline) {
            append(plain(htmlInline.getLiteral()));
        }

        @Override
        public void visit(CustomNode customNode) {
            if (customNode instanceof Strikethrough) {
                final TLRPC.TL_textStrike s = new TLRPC.TL_textStrike();
                s.text = collectChildren(customNode);
                append(s);
            } else if (customNode instanceof io.noties.markwon.ext.latex.JLatexMathNode) {
                append(makeLatex(((io.noties.markwon.ext.latex.JLatexMathNode) customNode).latex()));
            } else {
                super.visit(customNode);
            }
        }

        @Override
        public void visit(CustomBlock customBlock) {
            if (customBlock instanceof io.noties.markwon.ext.latex.JLatexMathBlock) {
                if (!current.texts.isEmpty()) append(plain("\n"));
                append(makeLatex(((io.noties.markwon.ext.latex.JLatexMathBlock) customBlock).latex()));
                append(plain("\n"));
            } else {
                super.visit(customBlock);
            }
        }
    }

}
