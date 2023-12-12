package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CheckBoxSquare;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;

public class RecurrentPaymentsAcceptCell extends FrameLayout {
    private LinkSpanDrawable.LinkCollector links;

    private TextView textView;
    private CheckBoxSquare checkBox;

    public RecurrentPaymentsAcceptCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        checkBox = new CheckBoxSquare(context, false);
        checkBox.setDuplicateParentStateEnabled(false);
        checkBox.setFocusable(false);
        checkBox.setFocusableInTouchMode(false);
        checkBox.setClickable(false);
        addView(checkBox, LayoutHelper.createFrame(18, 18, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 21, 0, 21, 0));

        textView = new LinkSpanDrawable.LinksTextView(context, links = new LinkSpanDrawable.LinkCollector(this), resourcesProvider);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setMaxLines(2);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 16 : 58, 21, LocaleController.isRTL ? 58 : 16, 21));

        setWillNotDraw(false);
    }

    public TextView getTextView() {
        return textView;
    }

    public CheckBoxSquare getCheckBox() {
        return checkBox;
    }

    public void setText(CharSequence text) {
        textView.setText(text);
    }

    public void setChecked(boolean checked) {
        checkBox.setChecked(checked, true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (links != null) {
            canvas.save();
            canvas.translate(textView.getLeft(), textView.getTop());
            if (links.draw(canvas)) {
                invalidate();
            }
            canvas.restore();
        }
    }
}
