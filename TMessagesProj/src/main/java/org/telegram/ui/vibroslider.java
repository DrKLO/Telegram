package org.telegram.ui;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PopupSwipeBackLayout;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.Switch;

public class vibroslider extends BaseFragment {


    private long duration1 = 50, duration2 = 50, duration3 = 50;
    private int amplitude1 = 50, amplitude2 = 50, amplitude3 = 50;

    @Override
    public View createView(Context context) {
        FrameLayout fragmentView = new FrameLayout(context);
        LinearLayout ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.VERTICAL);
        fragmentView.addView(ll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 16, 16, 16, 16));

        ll.addView(new Slider(context, 0, 100, 50, a -> duration1 = a), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        ll.addView(new Slider(context, 0, 100, 50, a -> duration2 = a), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        ll.addView(new Slider(context, 0, 100, 50, a -> duration3 = a), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        ll.addView(new Slider(context, 0, 255, 50, a -> amplitude1 = a), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        ll.addView(new Slider(context, 0, 255, 50, a -> amplitude2 = a), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        ll.addView(new Slider(context, 0, 255, 50, a -> amplitude3 = a), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        FrameLayout button = new FrameLayout(context);
        button.setBackground(Theme.AdaptiveRipple.filledRect(Theme.key_featuredStickers_addButton, 4));
        button.setOnClickListener(e -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                final Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                VibrationEffect vibrationEffect = VibrationEffect.createWaveform(
                    new long[] { 100, 20, 10 },
                    new int[] { 5, 0, 255 },
                    -1
                );
                vibrator.cancel();
                vibrator.vibrate(vibrationEffect);
            }
        });
        ll.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 4, 80, 4, 4));

        Switch switchView = new Switch(context);
        switchView.setOnClickListener(e -> {
            switchView.setChecked(!switchView.isChecked(), true);
        });
        ll.addView(switchView);

        return fragmentView;
    }

    public class Slider extends FrameLayout {
        private int min;
        private int max;
        private int value;
        private PopupSwipeBackLayout.IntCallback onChange;
        public Slider(Context context, int min, int max, int initialValue, PopupSwipeBackLayout.IntCallback onChange) {
            super(context);
            this.min = min;
            this.max = max;
            this.value = initialValue;
            this.onChange = onChange;

            TextView textView = new TextView(context);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            SeekBarView seekBarView = new SeekBarView(context);
            seekBarView.setReportChanges(true);
            seekBarView.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    value = AndroidUtilities.lerp(min, max, progress);
                    textView.setText(value + "");
                    onChange.run(value);
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {

                }
            });
            textView.setText(value + "");
            seekBarView.setProgress((value - min) / (float) (max - min));
            addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.FILL_HORIZONTAL | Gravity.CENTER_VERTICAL, 24, 0, 0, 0));
        }

    }
}
