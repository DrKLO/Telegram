package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.RichMessageLayout;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Editable horizontal row of page buttons. Rendering is delegated to RichMessageLayout.RichButton. */
public class RichButtonRowCell extends RichBlockCell implements Theme.Colorable {

    public static final int MAX_BUTTONS = 8;

    public interface Delegate {
        void onAddButton(BlockRow row, View anchor);
        void onEditButton(BlockRow row, int index, View anchor);
        void onCycleButtonStyle(BlockRow row, int index);
    }

    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    private final HorizontalScrollView scrollView;
    private final LinearLayout buttonsLayout;
    private final RichEditor.Button addButton;
    private final TextView emptyAddButton;
    private final ArrayList<ButtonView> buttonViews = new ArrayList<>();
    private Delegate delegate;

    public RichButtonRowCell(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;
        setBlockPadding(dp(16), dp(4), dp(16), dp(4));

        scrollView = new HorizontalScrollView(context);
        scrollView.setHorizontalScrollBarEnabled(false);
        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        scrollView.addView(buttonsLayout, new HorizontalScrollView.LayoutParams(
            LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
        addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL_HORIZONTAL | Gravity.CENTER_VERTICAL));

        addButton = new RichEditor.Button(context, R.drawable.msg_add, resourcesProvider).setRoundRadius(19);
        addButton.setSelected(true);
        addButton.setContentDescription(org.telegram.messenger.LocaleController.getString(R.string.Add));
        addButton.setOnClickListener(v -> {
            if (delegate != null && currentRow != null) delegate.onAddButton(currentRow, v);
        });
        addView(addButton, LayoutHelper.createFrame(38, 38, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        emptyAddButton = new TextView(context);
        emptyAddButton.setText(org.telegram.messenger.LocaleController.getString(R.string.RichEditorAddButton));
        emptyAddButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyAddButton.setTypeface(AndroidUtilities.bold());
        emptyAddButton.setGravity(Gravity.CENTER);
        emptyAddButton.setCompoundDrawablePadding(dp(7));
        emptyAddButton.setPadding(dp(15), 0, dp(15), 0);
        emptyAddButton.setOnClickListener(v -> {
            if (delegate != null && currentRow != null) delegate.onAddButton(currentRow, v);
        });
        addView(emptyAddButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 38, Gravity.CENTER));
        updateAddButtonColors();
    }

    public void bind(BlockRow row, Delegate delegate) {
        currentRow = row;
        this.delegate = delegate;
        bindBlockInset(row);
        rebuildButtons();
    }

    private void rebuildButtons() {
        buttonsLayout.removeAllViews();
        buttonViews.clear();
        final TL_iv.pageBlockButtonRow row = currentRow != null && currentRow.block instanceof TL_iv.pageBlockButtonRow
            ? (TL_iv.pageBlockButtonRow) currentRow.block : null;
        final int count = row == null || row.buttons == null ? 0 : row.buttons.size();
        for (int i = 0; i < count; i++) {
            final ButtonView buttonView = new ButtonView(getContext(), row.buttons.get(i), i);
            buttonViews.add(buttonView);
            buttonsLayout.addView(buttonView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, i == 0 ? 0 : 7, 0, 0, 0));
        }
        final boolean canAdd = count < MAX_BUTTONS;
        scrollView.setVisibility(count > 0 ? VISIBLE : GONE);
        emptyAddButton.setVisibility(count == 0 ? VISIBLE : GONE);
        addButton.setVisibility(count > 0 && canAdd ? VISIBLE : GONE);
        requestLayout();
    }

    @Override
    public void updateColors() {
        addButton.updateColors();
        updateAddButtonColors();
        rebuildButtons();
    }

    private void updateAddButtonColors() {
        final int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        final int base = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        final int normal = Theme.blendOver(base, Theme.multAlpha(accent, .10f));
        emptyAddButton.setTextColor(accent);
        emptyAddButton.setBackground(Theme.createRadSelectorDrawable(
            normal, Theme.multAlpha(accent, .16f), dp(19), dp(19)));
        final Drawable icon = getContext().getResources().getDrawable(R.drawable.msg_add).mutate();
        icon.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
        emptyAddButton.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int count = buttonViews.size();
        final boolean showAdd = count > 0 && count < MAX_BUTTONS;
        final int addReserve = showAdd ? dp(45) : 0;
        final FrameLayout.LayoutParams scrollParams = (FrameLayout.LayoutParams) scrollView.getLayoutParams();
        if (scrollParams.rightMargin != addReserve) {
            scrollParams.rightMargin = addReserve;
            scrollView.setLayoutParams(scrollParams);
        }
        if (count > 0) {
            final int contentWidth = Math.max(0,
                width - getPaddingLeft() - getPaddingRight() - addReserve);
            layoutButtonWidths(contentWidth);
        }
        final int contentHeight = count > 0
            ? buttonViews.get(0).getContentHeight()
            : dp(18 + SharedConfig.fontSize) + dp(8);
        final int height = getPaddingTop() + contentHeight + getPaddingBottom();
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    private void layoutButtonWidths(int rowWidth) {
        final int count = buttonViews.size();
        if (count == 0) return;
        final int available = Math.max(0, rowWidth - dp(7) * (count - 1));
        final int[] widths = new int[count];
        int preferred = 0;
        for (int i = 0; i < count; i++) {
            widths[i] = buttonViews.get(i).getPreferredWidth();
            preferred += widths[i];
        }
        if (preferred <= available) {
            stretchWidths(widths, available);
        } else {
            squeezeWidths(widths, available, preferred);
        }
        for (int i = 0; i < count; i++) {
            buttonViews.get(i).setButtonWidth(widths[i]);
        }
    }

    private void stretchWidths(int[] widths, int available) {
        final boolean[] fixed = new boolean[widths.length];
        int flexible = widths.length;
        int rest = available;
        boolean changed = true;
        while (changed && flexible > 0) {
            changed = false;
            final int share = rest / flexible;
            for (int i = 0; i < widths.length; i++) {
                if (!fixed[i] && widths[i] > share) {
                    fixed[i] = true;
                    rest -= widths[i];
                    flexible--;
                    changed = true;
                }
            }
        }
        if (flexible <= 0) return;
        final int share = rest / flexible;
        int extra = rest - share * flexible;
        for (int i = 0; i < widths.length; i++) {
            if (!fixed[i]) widths[i] = share + (extra-- > 0 ? 1 : 0);
        }
    }

    private void squeezeWidths(int[] widths, int available, int preferred) {
        int shrinkable = 0;
        for (int i = 0; i < widths.length; i++) {
            shrinkable += widths[i] - buttonViews.get(i).getMinWidth();
        }
        if (shrinkable <= 0) {
            for (int i = 0; i < widths.length; i++) widths[i] = buttonViews.get(i).getMinWidth();
            return;
        }
        final int total = Math.min(preferred - available, shrinkable);
        int taken = 0;
        for (int i = 0; i < widths.length; i++) {
            final int room = widths[i] - buttonViews.get(i).getMinWidth();
            int cut = i == widths.length - 1 ? total - taken
                : (int) ((long) total * room / shrinkable);
            cut = Math.min(cut, room);
            widths[i] -= cut;
            taken += cut;
        }
    }

    public boolean isPressOnButton(float localX, float localY) {
        final int[] cellLocation = new int[2];
        getLocationOnScreen(cellLocation);
        final float screenX = cellLocation[0] + localX;
        final float screenY = cellLocation[1] + localY;
        if (isPointInside(addButton, screenX, screenY) || isPointInside(emptyAddButton, screenX, screenY)) {
            return true;
        }
        for (ButtonView button : buttonViews) {
            if (isPointInside(button, screenX, screenY)) return true;
        }
        return false;
    }

    private static boolean isPointInside(View view, float screenX, float screenY) {
        if (view.getVisibility() != VISIBLE) return false;
        final int[] location = new int[2];
        view.getLocationOnScreen(location);
        return screenX >= location[0] && screenX <= location[0] + view.getWidth()
            && screenY >= location[1] && screenY <= location[1] + view.getHeight();
    }

    private class ButtonView extends View {
        private final RichMessageLayout.RichButton button;
        private final int index;
        private boolean pressed;
        private boolean longPressed;
        private final Runnable longPressRunnable;

        ButtonView(Context context, TL_keyboard.PageButton pageButton, int index) {
            super(context);
            this.index = index;
            button = RichMessageLayout.createEditorPageButton(
                currentAccount, Math.max(dp(240), AndroidUtilities.displaySize.x - dp(32)),
                resourcesProvider, pageButton, this::invalidate);
            longPressRunnable = () -> {
                if (!pressed || delegate == null || currentRow == null) return;
                longPressed = true;
                button.setPressed(false);
                try {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } catch (Exception ignore) {}
                delegate.onEditButton(currentRow, index, this);
            };
            button.width = button.getPreferredWidth();
            setContentDescription(RichTextStyle.plainOf(pageButton.text));
            setClickable(true);
            setLongClickable(true);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(button.width, getContentHeight());
        }

        int getContentHeight() {
            return button.getHeight() + dp(8);
        }

        int getPreferredWidth() {
            return Math.max(dp(34), button.getPreferredWidth());
        }

        int getMinWidth() {
            return Math.max(dp(34), button.getMinWidth());
        }

        void setButtonWidth(int width) {
            button.width = Math.max(dp(34), width);
            final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) getLayoutParams();
            if (params != null && params.width != button.width) {
                params.width = button.width;
                setLayoutParams(params);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.translate(0, (getHeight() - button.getHeight()) / 2f);
            button.draw(canvas);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pressed = true;
                    longPressed = false;
                    button.setPressed(true);
                    AndroidUtilities.runOnUIThread(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (event.getX() < 0 || event.getY() < 0 || event.getX() > getWidth() || event.getY() > getHeight()) {
                        pressed = false;
                        button.setPressed(false);
                        AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    pressed = false;
                    button.setPressed(false);
                    AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                    return true;
                case MotionEvent.ACTION_UP:
                    final boolean click = pressed && !longPressed;
                    pressed = false;
                    button.setPressed(false);
                    AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                    if (click && delegate != null && currentRow != null) {
                        delegate.onCycleButtonStyle(currentRow, index);
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            button.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
            button.detach(this);
            super.onDetachedFromWindow();
        }
    }

    public static final class Factory extends UItem.UItemFactory<RichButtonRowCell> {
        static { setup(new Factory()); }

        @Override
        public RichButtonRowCell createView(Context context, RecyclerListView listView, int currentAccount,
                                            int classGuid, Theme.ResourcesProvider resourcesProvider) {
            final RichButtonRowCell cell = new RichButtonRowCell(context, currentAccount, resourcesProvider);
            cell.setBackground(new RichEditor.DraggingDrawable(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
            return cell;
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                             UniversalRecyclerView listView) {
            ((RichButtonRowCell) view).bind((BlockRow) item.object, (Delegate) item.object2);
        }

        public static UItem of(BlockRow row, Delegate delegate) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.object = row;
            item.object2 = delegate;
            return item;
        }

        @Override
        public boolean isClickable() {
            return false;
        }
    }
}
