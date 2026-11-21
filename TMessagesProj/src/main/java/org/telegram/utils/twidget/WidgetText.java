package org.telegram.utils.twidget;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.Layout;
import android.text.TextPaint;

import org.json.JSONArray;

import java.util.ArrayList;

/**
 * The text a widget made selectable (`user-select: text`, widget-sandbox v0.1.1, docs/sdk-v1.md §5.10), as
 * TwidgetCore lists it (Renderer::selectable_text): paragraphs of lines, each character's position measured
 * with the font it is drawn with. The chat selects it like message text through {@link #layout}s, which
 * answer the {@link Layout} calls TextSelectionHelper makes. Built on the widget's worker, then immutable.
 */
public final class WidgetText {

    /** One paragraph: its text, and its lines in device px from the paragraph's origin (left, top). */
    public static final class Paragraph {
        final String text;
        /** Where the paragraph sits in the widget, device px. */
        public final int left, top, width, height;
        final int[] lineStart, lineTop;  // lineStart has a last entry: the text's length; lineTop the bottom
        final float[][] xs;              // per line: the x of each character's start, and of the line's end
        private Layout layout;

        Paragraph(String text, int left, int top, int width, int height, int[] lineStart, int[] lineTop, float[][] xs) {
            this.text = text;
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.lineStart = lineStart;
            this.lineTop = lineTop;
            this.xs = xs;
        }

        /** UI: the selection helper's view of the paragraph. */
        public Layout layout() {
            if (layout == null) layout = new ParagraphLayout(this);
            return layout;
        }
    }

    public final ArrayList<Paragraph> paragraphs;

    private WidgetText(ArrayList<Paragraph> paragraphs) {
        this.paragraphs = paragraphs;
    }

    /** Measures a run's text: the advances of its UTF-16 chars in device px, with the font it is drawn with. */
    interface Fonts {
        Paint deviceFont(int id);
    }

    /**
     * Worker: the model of Renderer::selectable_text_json ([[paragraph, x, y, w, h, baseline, font, letter
     * spacing, word spacing, text, [a, b, c, d, e, f]], …], CSS px, the matrix where the run shows); null when
     * nothing is selectable.
     */
    static WidgetText parse(String json, Fonts fonts, float density) throws Exception {
        JSONArray runs = new JSONArray(json);
        if (runs.length() == 0) return null;
        ArrayList<Paragraph> paragraphs = new ArrayList<>();
        int i = 0;
        while (i < runs.length()) {
            int id = runs.getJSONArray(i).optInt(0);
            int end = i;
            while (end < runs.length() && runs.getJSONArray(end).optInt(0) == id) end++;
            Paragraph p = paragraph(runs, i, end, fonts, density);
            if (p != null) paragraphs.add(p);
            i = end;
        }
        return paragraphs.isEmpty() ? null : new WidgetText(paragraphs);
    }

    private static final class Line {
        float top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE, right;
        final StringBuilder text = new StringBuilder();
        final ArrayList<Float> xs = new ArrayList<>();
    }

    private static Paragraph paragraph(JSONArray runs, int from, int to, Fonts fonts, float density) throws Exception {
        ArrayList<Line> lines = new ArrayList<>();
        Line line = null;
        float left = Float.MAX_VALUE, top = Float.MAX_VALUE, right = -Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        float[] widths = new float[64];
        for (int i = from; i < to; i++) {
            JSONArray run = runs.getJSONArray(i);
            float x = (float) run.optDouble(1) * density, y = (float) run.optDouble(2) * density;
            float w = (float) run.optDouble(3) * density, h = (float) run.optDouble(4) * density;
            float letter = (float) run.optDouble(7) * density, word = (float) run.optDouble(8) * density;
            String text = run.optString(9);
            if (text.isEmpty()) continue;
            // Where the run shows (CSS transforms, its layer's track when painted): a move and a scale lay the
            // lines out there; a turned or skewed run is selected where layout put it.
            float scale = 1;
            JSONArray m = run.optJSONArray(10);
            if (m != null && m.length() == 6 && Math.abs(m.optDouble(1)) < 1e-4 && Math.abs(m.optDouble(2)) < 1e-4
                    && m.optDouble(0) > 0 && m.optDouble(3) > 0) {
                float a = (float) m.optDouble(0), d = (float) m.optDouble(3);
                x = a * x + (float) m.optDouble(4) * density;
                y = d * y + (float) m.optDouble(5) * density;
                w *= a;
                h *= d;
                letter *= a;
                word *= a;
                scale = a;
            }
            if (w <= 0 && h <= 0) {
                // A <br>: the line ends with it.
                if (line != null) {
                    line.text.append('\n');
                    line.xs.add(line.right);
                    line = null;
                }
                continue;
            }
            // A run whose middle is within the line so far is on it; anything else starts a new line.
            float middle = y + h / 2;
            if (line == null || middle < line.top || middle > line.bottom) {
                if (line != null) endLine(line);
                line = new Line();
                lines.add(line);
            } else if (x - line.right > 1 && !endsWithSpace(line.text) && !Character.isWhitespace(text.charAt(0))) {
                // Words apart on one line with nothing between them in the text: they copy with a space.
                line.text.append(' ');
                line.xs.add(line.right);
            }
            line.top = Math.min(line.top, y);
            line.bottom = Math.max(line.bottom, y + h);
            Paint paint = fonts.deviceFont(run.optInt(6));
            if (widths.length < text.length()) widths = new float[text.length()];
            if (paint != null) paint.getTextWidths(text, widths);
            float cx = x;
            for (int c = 0; c < text.length(); c++) {
                line.text.append(text.charAt(c));
                line.xs.add(cx);
                if (paint == null) {
                    cx = x + w * (c + 1) / text.length();
                } else {
                    cx += widths[c] * scale + (Character.isLowSurrogate(text.charAt(c)) ? 0 : letter);
                    if (text.charAt(c) == ' ' || text.charAt(c) == '\u00A0') cx += word;
                }
            }
            line.right = Math.max(cx, x + w);
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, line.right);
            bottom = Math.max(bottom, y + h);
        }
        // The last line ends the paragraph: nothing reads on from it.
        if (lines.isEmpty()) return null;
        StringBuilder text = new StringBuilder();
        int[] lineStart = new int[lines.size() + 1];
        int[] lineTop = new int[lines.size() + 1];
        float[][] xs = new float[lines.size()][];
        int ox = (int) Math.floor(left), oy = (int) Math.floor(top);
        for (int l = 0; l < lines.size(); l++) {
            Line it = lines.get(l);
            lineStart[l] = text.length();
            text.append(it.text);
            lineTop[l] = Math.round(it.top) - oy;
            float[] positions = new float[it.xs.size() + 1];
            for (int c = 0; c < it.xs.size(); c++) positions[c] = it.xs.get(c) - ox;
            positions[it.xs.size()] = it.right - ox;
            xs[l] = positions;
        }
        lineStart[lines.size()] = text.length();
        lineTop[lines.size()] = Math.round(bottom) - oy;
        // Lines meet: a line ends where the next begins, so a press between them still finds one.
        for (int l = 1; l < lines.size(); l++) lineTop[l] = Math.max(lineTop[l - 1], lineTop[l]);
        return new Paragraph(text.toString(), ox, oy, (int) Math.ceil(right) - ox, lineTop[lines.size()], lineStart, lineTop, xs);
    }

    /** A wrapped line reads on into the next with a space, which belongs to the line it ends. */
    private static void endLine(Line line) {
        if (line.text.length() > 0 && !endsWithSpace(line.text)) {
            line.text.append(' ');
            line.xs.add(line.right);
        }
    }

    private static boolean endsWithSpace(CharSequence text) {
        return text.length() > 0 && Character.isWhitespace(text.charAt(text.length() - 1));
    }

    private static final TextPaint PAINT = new TextPaint();

    /**
     * A {@link Layout} over a paragraph's measured lines: the calls TextSelectionHelper makes (lines, offsets,
     * horizontal positions, the selection path) answered from them. It draws nothing: the widget draws the text.
     */
    private static final class ParagraphLayout extends Layout {
        private final Paragraph p;

        ParagraphLayout(Paragraph p) {
            super(p.text, PAINT, Math.max(1, p.width), Alignment.ALIGN_NORMAL, 1, 0);
            this.p = p;
        }

        @Override
        public int getLineCount() {
            return p.xs.length;
        }

        @Override
        public int getLineTop(int line) {
            return p.lineTop[Math.max(0, Math.min(line, p.xs.length))];
        }

        @Override
        public int getLineDescent(int line) {
            return 0;
        }

        @Override
        public int getLineStart(int line) {
            return p.lineStart[Math.max(0, Math.min(line, p.xs.length))];
        }

        @Override
        public int getParagraphDirection(int line) {
            return DIR_LEFT_TO_RIGHT;
        }

        @Override
        public boolean getLineContainsTab(int line) {
            return false;
        }

        @Override
        public Directions getLineDirections(int line) {
            return null;
        }

        @Override
        public int getTopPadding() {
            return 0;
        }

        @Override
        public int getBottomPadding() {
            return 0;
        }

        @Override
        public int getEllipsisStart(int line) {
            return 0;
        }

        @Override
        public int getEllipsisCount(int line) {
            return 0;
        }

        @Override
        public boolean isRtlCharAt(int offset) {
            return false;
        }

        @Override
        public int getLineForOffset(int offset) {
            for (int l = 0; l < p.xs.length; l++) {
                if (offset < p.lineStart[l + 1]) return l;
            }
            return p.xs.length - 1;
        }

        @Override
        public float getPrimaryHorizontal(int offset) {
            int line = getLineForOffset(offset);
            float[] xs = p.xs[line];
            int index = Math.max(0, Math.min(offset - p.lineStart[line], xs.length - 1));
            return xs[index];
        }

        @Override
        public float getSecondaryHorizontal(int offset) {
            return getPrimaryHorizontal(offset);
        }

        @Override
        public int getOffsetForHorizontal(int line, float horiz) {
            line = Math.max(0, Math.min(line, p.xs.length - 1));
            float[] xs = p.xs[line];
            int count = p.lineStart[line + 1] - p.lineStart[line];
            int best = 0;
            for (int c = 0; c <= count && c < xs.length; c++) {
                // The nearer edge of the character under the point.
                if (c < count && horiz >= (xs[c] + xs[c + 1]) / 2) best = c + 1;
            }
            // A line ends before the space that wraps it into the next.
            if (best == count && count > 0 && line < p.xs.length - 1 && Character.isWhitespace(p.text.charAt(p.lineStart[line] + count - 1))) best--;
            return p.lineStart[line] + best;
        }

        @Override
        public float getLineLeft(int line) {
            return p.xs[Math.max(0, Math.min(line, p.xs.length - 1))][0];
        }

        @Override
        public float getLineRight(int line) {
            float[] xs = p.xs[Math.max(0, Math.min(line, p.xs.length - 1))];
            return xs[xs.length - 1];
        }

        @Override
        public float getLineMax(int line) {
            return getLineRight(line);
        }

        @Override
        public float getLineWidth(int line) {
            return getLineRight(line) - getLineLeft(line);
        }

        @Override
        public void getSelectionPath(int start, int end, Path dest) {
            dest.reset();
            if (start == end) return;
            if (end < start) {
                int t = start;
                start = end;
                end = t;
            }
            int first = getLineForOffset(start), last = getLineForOffset(end);
            for (int l = first; l <= last; l++) {
                float from = l == first ? getPrimaryHorizontal(start) : getLineLeft(l);
                float to = l == last ? getPrimaryHorizontal(end) : getLineRight(l);
                if (to > from) dest.addRect(from, getLineTop(l), to, getLineTop(l + 1), Path.Direction.CW);
            }
        }

        @Override
        public void draw(Canvas canvas) {
        }

        @Override
        public void draw(Canvas canvas, Path highlight, Paint highlightPaint, int cursorOffsetVertical) {
        }
    }
}
