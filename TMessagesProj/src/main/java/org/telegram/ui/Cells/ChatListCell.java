package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadioButton;

public class ChatListCell extends LinearLayout {

    private class ListView extends FrameLayout {

        private RadioButton button;
        private boolean isThreeLines;
        private RectF rect = new RectF();
        private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        public ListView(Context context, boolean threeLines) {
            super(context);
            setWillNotDraw(false);

            isThreeLines = threeLines;
            setContentDescription(threeLines ? LocaleController.getString(R.string.ChatListExpanded) : LocaleController.getString(R.string.ChatListDefault));

            textPaint.setTextSize(AndroidUtilities.dp(13));

            button = new RadioButton(context) {
                @Override
                public void invalidate() {
                    super.invalidate();
                    ListView.this.invalidate();
                }
            };
            button.setSize(AndroidUtilities.dp(20));
            addView(button, LayoutHelper.createFrame(22, 22, Gravity.RIGHT | Gravity.TOP, 0, 26, 10, 0));
            button.setChecked(isThreeLines && SharedConfig.useThreeLinesLayout || !isThreeLines && !SharedConfig.useThreeLinesLayout, false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int color = Theme.getColor(Theme.key_switchTrack);
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);

            button.setColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_radioBackgroundChecked));

            rect.set(AndroidUtilities.dp(1), AndroidUtilities.dp(1), getMeasuredWidth() - AndroidUtilities.dp(1), AndroidUtilities.dp(73));
            Theme.chat_instantViewRectPaint.setColor(Color.argb((int) (43 * button.getProgress()), r, g, b));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), Theme.chat_instantViewRectPaint);

            rect.set(0, 0, getMeasuredWidth(), AndroidUtilities.dp(74));
            Theme.dialogs_onlineCirclePaint.setColor(Color.argb((int) (31 * (1.0f - button.getProgress())), r, g, b));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), Theme.dialogs_onlineCirclePaint);

            String text = isThreeLines ? LocaleController.getString(R.string.ChatListExpanded) : LocaleController.getString(R.string.ChatListDefault);
            int width = (int) Math.ceil(textPaint.measureText(text));

            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            canvas.drawText(text, (getMeasuredWidth() - width) / 2, AndroidUtilities.dp(96), textPaint);

            for (int a = 0; a < 2; a++) {
                int cy = AndroidUtilities.dp(a == 0 ? 21 : 53);
                Theme.dialogs_onlineCirclePaint.setColor(Color.argb(a == 0 ? 204 : 90, r, g, b));
                canvas.drawCircle(AndroidUtilities.dp(22), cy, AndroidUtilities.dp(11), Theme.dialogs_onlineCirclePaint);

                for (int i = 0; i < (isThreeLines ? 3 : 2); i++) {
                    Theme.dialogs_onlineCirclePaint.setColor(Color.argb(i == 0 ? 204 : 90, r, g, b));
                    if (isThreeLines) {
                        rect.set(AndroidUtilities.dp(41), cy - AndroidUtilities.dp(8.3f - i * 7), getMeasuredWidth() - AndroidUtilities.dp(i == 0 ? 72 : 48), cy - AndroidUtilities.dp(8.3f - 3 - i * 7));
                        canvas.drawRoundRect(rect, AndroidUtilities.dpf2(1.5f), AndroidUtilities.dpf2(1.5f), Theme.dialogs_onlineCirclePaint);
                    } else {
                        rect.set(AndroidUtilities.dp(41), cy - AndroidUtilities.dp(7 - i * 10), getMeasuredWidth() - AndroidUtilities.dp(i == 0 ? 72 : 48), cy - AndroidUtilities.dp(7 - 4 - i * 10));
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);
                    }
                }
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName(RadioButton.class.getName());
            info.setChecked(button.isChecked());
            info.setCheckable(true);
            info.setContentDescription(isThreeLines ? LocaleController.getString(R.string.ChatListExpanded) : LocaleController.getString(R.string.ChatListDefault));
        }
    }

    private ListView[] listView = new ListView[2];

    public ChatListCell(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(10), AndroidUtilities.dp(21), 0);

        for (int a = 0; a < listView.length; a++) {
            boolean isThreeLines = a == 1;
            listView[a] = new ListView(context, isThreeLines);
            addView(listView[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0.5f, a == 1 ? 10 : 0, 0, 0, 0));
            listView[a].setOnClickListener(v -> {
                for (int b = 0; b < 2; b++) {
                    listView[b].button.setChecked(listView[b] == v, true);
                }
                didSelectChatType(isThreeLines);
            });
        }
    }

    protected void didSelectChatType(boolean threeLines) {

    }

    @Override
    public void invalidate() {
        super.invalidate();
        for (int a = 0; a < listView.length; a++) {
            listView[a].invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(123), MeasureSpec.EXACTLY));
    }
}
