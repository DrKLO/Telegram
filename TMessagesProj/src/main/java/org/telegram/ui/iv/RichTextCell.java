package org.Tajgram.ui.iv;

import static org.Tajgram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Editable;
import android.text.Layout;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.Tajgram.messenger.AndroidUtilities;
import org.Tajgram.messenger.R;
import org.Tajgram.tgnet.TLRPC;
import org.Tajgram.tgnet.tl.TL_iv;
import org.Tajgram.ui.ActionBar.FloatingToolbar;
import org.Tajgram.ui.ActionBar.Theme;
import org.Tajgram.ui.Cells.TextSelectionHelper;
import org.Tajgram.ui.Components.LayoutHelper;
import org.Tajgram.ui.Components.RecyclerListView;
import org.Tajgram.ui.Components.UItem;
import org.Tajgram.ui.Components.UniversalAdapter;
import org.Tajgram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class RichTextCell extends FrameLayout implements Theme.Colorable, TextSelectionHelper.ArticleSelectableView {

    public interface Delegate {
        void onEnter(BlockRow row);
        void onBackspace(BlockRow row);
        default boolean onBackspaceAtStart(BlockRow row) { return false; }
        void onTextChanged(BlockRow row);
        void onTransform(BlockRow row, TL_iv.PageBlock newBlock, int newLevel, int newNum);
        TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper();
        default boolean onIndent(BlockRow row, boolean outdent) { return false; }
        default void onRequestWindowFocusable(RichEditText editText, boolean showKeyboard) {}
    }

    private static final int INDENT_DP_PER_LEVEL = 24;
    private static final int BULLET_WIDTH_DP = 28;

    private final Theme.ResourcesProvider resourcesProvider;
    private final LinearLayout row;
    private final View indentSpacer;
    private final TextView bullet;
    private final RichEditText editText;

    private BlockRow currentRow;
    private Delegate delegate;
    private boolean forceHint;
    private boolean hijackingSelection;

    public RichTextCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        indentSpacer = new View(context);
        row.addView(indentSpacer, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT));

        bullet = new TextView(context);
        bullet.setGravity(Gravity.CENTER);
        bullet.setIncludeFontPadding(false);
        bullet.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        row.addView(bullet, LayoutHelper.createLinear(BULLET_WIDTH_DP, LinearLayout.LayoutParams.WRAP_CONTENT));

        editText = new RichEditText(context, resourcesProvider);
        editText.setPadding(dp(2), 0, dp(2), 0);
        editText.setListener(new RichEditText.Listener() {
            @Override
            public void onEnterPressed(RichEditText et) {
                if (delegate == null || currentRow == null) return;
                Transform tr = matchEnterTrigger(et.getText().toString(), currentRow);
                if (tr != null) {
                    delegate.onTransform(currentRow, tr.block, tr.level, tr.num);
                    return;
                }
                delegate.onEnter(currentRow);
            }

            @Override
            public void onBackspaceOnEmpty(RichEditText et) {
                if (delegate != null && currentRow != null) delegate.onBackspace(currentRow);
            }

            @Override
            public boolean onBackspaceAtStart(RichEditText et) {
                if (delegate != null && currentRow != null) return delegate.onBackspaceAtStart(currentRow);
                return false;
            }

            @Override
            public void onTextChanged(RichEditText et, Editable text) {
                if (currentRow == null) return;
                applyTextToBlock(currentRow.block, text.toString());
                if (delegate != null) delegate.onTextChanged(currentRow);
                Transform tr = matchMarkdownTrigger(text.toString(), currentRow);
                if (tr != null && delegate != null) {
                    final BlockRow r = currentRow;
                    final Transform finalTr = tr;
                    post(() -> {
                        if (delegate != null) delegate.onTransform(r, finalTr.block, finalTr.level, finalTr.num);
                    });
                }
            }

            @Override
            public boolean onTab(RichEditText et, boolean shift) {
                if (delegate != null && currentRow != null) return delegate.onIndent(currentRow, shift);
                return false;
            }

            @Override
            public void onRequestWindowFocusable(RichEditText et, boolean showKeyboard) {
                if (delegate != null) delegate.onRequestWindowFocusable(et, showKeyboard);
            }

            @Override
            public void onSelectionChanged(RichEditText et, int selStart, int selEnd) {
                if (hijackingSelection || selStart == selEnd || delegate == null) return;
                final TextSelectionHelper.ArticleTextSelectionHelper helper = delegate.getSelectionHelper();
                if (helper == null) return;
                if (helper.isInSelectionMode() && helper.getSelectedCell() == RichTextCell.this) return;
                final int s = selStart, e = selEnd;
                post(() -> {
                    if (et.length() < e || et.getSelectionStart() == et.getSelectionEnd()) return;
                    if (helper.selectRangeOf(RichTextCell.this, s, e)) {
                        hijackingSelection = true;
                        et.setSelection(e);
                        hijackingSelection = false;
                    }
                });
            }
        });
        editText.setOnFocusChangeListener((v, hasFocus) -> editText.setHint(getHint()));

        row.addView(editText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 16, 6, 16, 0));

        updateColors();
    }

    public void bind(BlockRow row, Delegate delegate, boolean forceHint) {
        this.currentRow = row;
        this.delegate = delegate;
        this.forceHint = forceHint;
        applyStyle(row.block);
        applyListDecoration(row.level, row.num);
        String newText = readPlainText(row.block);
        if (!String.valueOf(editText.getText()).equals(newText)) {
            editText.setTextSilently(newText);
        }
    }

    public BlockRow getRow() {
        return currentRow;
    }

    public RichEditText getEditText() {
        return editText;
    }

    public void requestEditFocus() {
        editText.requestEditFocus();
    }

    public void setLocked(boolean locked) {
        editText.setLocked(locked);
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> out) {
        final Layout layout = editText.getLayout();
        if (layout == null) return;
        final int textX = row.getLeft() + editText.getLeft() + editText.getPaddingLeft();
        final int textY = row.getTop() + editText.getTop() + editText.getPaddingTop();
        out.add(new TextSelectionHelper.TextLayoutBlock() {
            @Override public Layout getLayout() { return layout; }
            @Override public int getX() { return textX; }
            @Override public int getY() { return textY; }
            @Override public int getRow() { return 0; }
        });
    }

    @Override
    public void updateColors() {
        editText.applyColors();
        bullet.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
    }

    private void applyListDecoration(int level, int num) {
        if (level <= 0) {
            indentSpacer.setVisibility(View.GONE);
            bullet.setVisibility(View.GONE);
        } else {
            LinearLayout.LayoutParams slp = (LinearLayout.LayoutParams) indentSpacer.getLayoutParams();
            slp.width = (level - 1) * dp(INDENT_DP_PER_LEVEL);
            indentSpacer.setLayoutParams(slp);
            indentSpacer.setVisibility(level > 1 ? View.VISIBLE : View.GONE);
            bullet.setVisibility(View.VISIBLE);
            bullet.setText(num == 0 ? "•" : (num + "."));
        }
    }

    private String getHint() {
        if (currentRow == null) return null;
        TL_iv.PageBlock block = currentRow.block;
        if (block instanceof TL_iv.pageBlockHeading1) return "Heading 1";
        if (block instanceof TL_iv.pageBlockHeading2) return "Heading 2";
        if (block instanceof TL_iv.pageBlockHeading3) return "Heading 3";
        if (block instanceof TL_iv.pageBlockHeading4) return "Heading 4";
        if (block instanceof TL_iv.pageBlockHeading5) return "Heading 5";
        if (block instanceof TL_iv.pageBlockHeading6) return "Heading 6";
        if (!forceHint) {
            if (!editText.isFocused()) return null;
            if (currentRow.isInList()) return null;
        }
        return "Type something…";
    }

    private void applyStyle(TL_iv.PageBlock block) {
        if (block instanceof TL_iv.pageBlockHeading1) {
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
            editText.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
        } else if (block instanceof TL_iv.pageBlockHeading2) {
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            editText.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
        } else if (block instanceof TL_iv.pageBlockHeading3) {
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            editText.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
        } else if (block instanceof TL_iv.pageBlockHeading4) {
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            editText.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
        } else if (block instanceof TL_iv.pageBlockHeading5) {
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            editText.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
        } else if (block instanceof TL_iv.pageBlockHeading6) {
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            editText.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
        } else {
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            editText.setTypeface(null);
        }
        editText.setHint(getHint());
    }

    private static class Transform {
        final TL_iv.PageBlock block;
        final int level;
        final int num;
        Transform(TL_iv.PageBlock b, int lvl, int n) { block = b; level = lvl; num = n; }
    }

    private static Transform matchMarkdownTrigger(String text, BlockRow row) {
        if (row == null || !(row.block instanceof TL_iv.pageBlockParagraph)) return null;
        if (text == null) return null;
        int n = text.length();
        if (n < 2 || text.charAt(n - 1) != ' ') return null;

        if (text.charAt(0) == '#') {
            int hashes = 0;
            for (int i = 0; i < n - 1; i++) {
                if (text.charAt(i) == '#') hashes++;
                else return null;
            }
            if (hashes < 1 || hashes > 6) return null;
            return new Transform(newHeading(hashes), row.level, row.num);
        }

        if (row.level == 0 && n == 2) {
            char c = text.charAt(0);
            if (c == '-' || c == '*' || c == '+') {
                TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
                applyTextToBlock(p, "");
                return new Transform(p, 1, 0);
            }
        }

        if (row.level == 0 && n == 3 && Character.isDigit(text.charAt(0))) {
            char d = text.charAt(1);
            if (d == '.' || d == ')') {
                TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
                applyTextToBlock(p, "");
                return new Transform(p, 1, 1);
            }
        }

        if (row.level == 0 && n == 4) {
            char c = text.charAt(0);
            if ((c == '-' || c == '*' || c == '_') && text.charAt(1) == c && text.charAt(2) == c) {
                return new Transform(new TL_iv.pageBlockDivider(), 0, 0);
            }
        }

        return null;
    }

    private static Transform matchEnterTrigger(String text, BlockRow row) {
        if (text == null || row == null) return null;
        String t = text.trim();
        if (t.length() == 3) {
            char c = t.charAt(0);
            if ((c == '-' || c == '*' || c == '_') && t.charAt(1) == c && t.charAt(2) == c) {
                return new Transform(new TL_iv.pageBlockDivider(), 0, 0);
            }
        }
        String tl = t.toLowerCase();
        if (tl.length() == 3 && tl.charAt(0) == '/' && tl.charAt(1) == 'h') {
            char d = tl.charAt(2);
            if (d >= '1' && d <= '6') {
                return new Transform(newHeading(d - '0'), row.level, row.num);
            }
        }
        if (tl.equals("/img") || tl.equals("/pic") || tl.equals("/image") || tl.equals("/picture") || tl.equals("/photo")) {
            return new Transform(new TL_iv.pageBlockPhoto(), 0, 0);
        }
        if (tl.equals("/vid") || tl.equals("/video")) {
            return new Transform(new TL_iv.pageBlockVideo(), 0, 0);
        }
        if (tl.equals("/latex") || tl.equals("/equation") || tl.equals("/math")) {
            TL_iv.pageBlockMath math = new TL_iv.pageBlockMath();
            math.source = "";
            return new Transform(math, 0, 0);
        }
        if (tl.equals("/map") || tl.equals("/location") || tl.equals("/loc")) {
            TL_iv.pageBlockMap map = new TL_iv.pageBlockMap();
            map.zoom = 15;
            map.w = 600;
            map.h = 400;
            return new Transform(map, 0, 0);
        }
        if (tl.equals("/table") || tl.startsWith("/table ")) {
            int rowsN = 3, colsN = 3;
            if (tl.length() > 7) {
                String size = tl.substring(7).trim();
                int x = size.indexOf('x');
                if (x < 0) x = size.indexOf('X');
                if (x > 0) {
                    try {
                        rowsN = Math.max(1, Math.min(20, Integer.parseInt(size.substring(0, x).trim())));
                        colsN = Math.max(1, Math.min(20, Integer.parseInt(size.substring(x + 1).trim())));
                    } catch (NumberFormatException ignored) {}
                }
            }
            return new Transform(newEmptyTable(rowsN, colsN), 0, 0);
        }
        return null;
    }

    private static TL_iv.pageBlockTable newEmptyTable(int rowsN, int colsN) {
        TL_iv.pageBlockTable t = new TL_iv.pageBlockTable();
        t.bordered = true;
        t.striped = false;
        t.title = new TL_iv.textEmpty();
        t.rows = new java.util.ArrayList<>();
        for (int r = 0; r < rowsN; r++) {
            TL_iv.pageTableRow row = new TL_iv.pageTableRow();
            row.cells = new java.util.ArrayList<>();
            for (int c = 0; c < colsN; c++) {
                row.cells.add(TableModel.newEmptyCell());
            }
            t.rows.add(row);
        }
        return t;
    }

    private static TL_iv.PageBlock newHeading(int level) {
        switch (level) {
            case 1: return new TL_iv.pageBlockHeading1();
            case 2: return new TL_iv.pageBlockHeading2();
            case 3: return new TL_iv.pageBlockHeading3();
            case 4: return new TL_iv.pageBlockHeading4();
            case 5: return new TL_iv.pageBlockHeading5();
            case 6: return new TL_iv.pageBlockHeading6();
            default: return null;
        }
    }

    static String readPlainText(TL_iv.PageBlock block) {
        TL_iv.RichText rt = extractRichText(block);
        if (rt instanceof TL_iv.textPlain) {
            return ((TL_iv.textPlain) rt).text;
        }
        return "";
    }

    static void applyTextToBlock(TL_iv.PageBlock block, String text) {
        TL_iv.textPlain plain = new TL_iv.textPlain();
        plain.text = text;
        if (block instanceof TL_iv.pageBlockParagraph) {
            ((TL_iv.pageBlockParagraph) block).text = plain;
        } else if (block instanceof TL_iv.pageBlockHeading1) {
            ((TL_iv.pageBlockHeading1) block).text = plain;
        } else if (block instanceof TL_iv.pageBlockHeading2) {
            ((TL_iv.pageBlockHeading2) block).text = plain;
        } else if (block instanceof TL_iv.pageBlockHeading3) {
            ((TL_iv.pageBlockHeading3) block).text = plain;
        } else if (block instanceof TL_iv.pageBlockHeading4) {
            ((TL_iv.pageBlockHeading4) block).text = plain;
        } else if (block instanceof TL_iv.pageBlockHeading5) {
            ((TL_iv.pageBlockHeading5) block).text = plain;
        } else if (block instanceof TL_iv.pageBlockHeading6) {
            ((TL_iv.pageBlockHeading6) block).text = plain;
        }
    }

    private static TL_iv.RichText extractRichText(TL_iv.PageBlock block) {
        if (block instanceof TL_iv.pageBlockParagraph) return ((TL_iv.pageBlockParagraph) block).text;
        if (block instanceof TL_iv.pageBlockHeading1) return ((TL_iv.pageBlockHeading1) block).text;
        if (block instanceof TL_iv.pageBlockHeading2) return ((TL_iv.pageBlockHeading2) block).text;
        if (block instanceof TL_iv.pageBlockHeading3) return ((TL_iv.pageBlockHeading3) block).text;
        if (block instanceof TL_iv.pageBlockHeading4) return ((TL_iv.pageBlockHeading4) block).text;
        if (block instanceof TL_iv.pageBlockHeading5) return ((TL_iv.pageBlockHeading5) block).text;
        if (block instanceof TL_iv.pageBlockHeading6) return ((TL_iv.pageBlockHeading6) block).text;
        return null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            heightMeasureSpec
        );
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        TextSelectionHelper.ArticleTextSelectionHelper helper = delegate != null ? delegate.getSelectionHelper() : null;
        if (helper != null && editText.getLayout() != null) {
            canvas.save();
            canvas.translate(
                row.getLeft() + editText.getLeft() + editText.getPaddingLeft(),
                row.getTop() + editText.getTop() + editText.getPaddingTop()
            );
            helper.draw(canvas, this, 0);
            canvas.restore();
        }
        super.dispatchDraw(canvas);
    }

    public static final class Factory extends UItem.UItemFactory<RichTextCell> {
        static { setup(new Factory()); }

        @Override
        public RichTextCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new RichTextCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            final RichTextCell cell = (RichTextCell) view;
            final BlockRow row = (BlockRow) item.object;
            final Delegate delegate = (Delegate) item.object2;
            final boolean forceHint = item.red;
            cell.bind(row, delegate, forceHint);
        }

        public static UItem of(BlockRow row, Delegate delegate, boolean forceHint) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.object = row;
            item.object2 = delegate;
            item.red = forceHint;
            return item;
        }

        @Override
        public boolean isClickable() {
            return false;
        }
    }
}
