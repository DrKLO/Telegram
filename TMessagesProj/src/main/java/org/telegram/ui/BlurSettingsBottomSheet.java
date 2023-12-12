package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

public class BlurSettingsBottomSheet extends BottomSheet {

    BaseFragment fragment;

    public static float saturation = 1f;
    public static float blurRadius = 1f;
    public static float blurAlpha = 1f - Color.alpha(Theme.getColor(Theme.key_chat_BlurAlpha)) / 255f;
    SizeNotifierFrameLayout contentView;


    public static void show(BaseFragment fragment) {
        new BlurSettingsBottomSheet(fragment).show();
    }

    private BlurSettingsBottomSheet(BaseFragment fragment) {
        super(fragment.getParentActivity(), false);
        this.fragment = fragment;
        if (fragment.getFragmentView() instanceof SizeNotifierFrameLayout) {
            contentView = (SizeNotifierFrameLayout) fragment.getFragmentView();
        }
        Context context = fragment.getParentActivity();
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        TextView saturationTextView = new TextView(context);
        saturationTextView.setText("Saturation " + (saturation * 5));
        saturationTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        saturationTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        saturationTextView.setLines(1);
        saturationTextView.setMaxLines(1);
        saturationTextView.setSingleLine(true);
        saturationTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
        linearLayout.addView(saturationTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 21, 13, 21, 0));

        SeekBarView seekBar = new SeekBarView(context);
        seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                saturation = progress;
                saturationTextView.setText("Saturation " + (progress * 5));
                contentView.invalidateBlurredViews();
                contentView.invalidateBlur();
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {

            }
        });
        seekBar.setReportChanges(true);
        linearLayout.addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 5, 4, 5, 0));


        TextView alphaTextView = new TextView(context);
        alphaTextView.setText("Alpha " + blurAlpha);
        alphaTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        alphaTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        alphaTextView.setLines(1);
        alphaTextView.setMaxLines(1);
        alphaTextView.setSingleLine(true);
        alphaTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
        linearLayout.addView(alphaTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 21, 13, 21, 0));

        SeekBarView seekBar3 = new SeekBarView(context);
        seekBar3.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                alphaTextView.setText("Alpha " + blurAlpha);
                blurAlpha = progress;
                contentView.invalidateBlur();
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {

            }
        });
        seekBar3.setReportChanges(true);
        linearLayout.addView(seekBar3, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 5, 4, 5, 0));



        TextView radiusTextView = new TextView(context);
        radiusTextView.setText("Blur Radius");
        radiusTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        radiusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        radiusTextView.setLines(1);
        radiusTextView.setMaxLines(1);
        radiusTextView.setSingleLine(true);
        radiusTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
        linearLayout.addView(radiusTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 21, 13, 21, 0));

        SeekBarView seekBar2 = new SeekBarView(context);
        seekBar2.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                blurRadius = progress;
                contentView.invalidateBlur();
                contentView.invalidateBlurredViews();
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {
                contentView.invalidateBlurredViews();
            }
        });
        seekBar2.setReportChanges(true);
        linearLayout.addView(seekBar2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 5, 4, 5, 0));

        linearLayout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                seekBar.setProgress(saturation);
                seekBar2.setProgress(blurRadius);
                seekBar3.setProgress(blurAlpha);
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(linearLayout);
        setCustomView(scrollView);
    }

    public static void onThemeApplyed() {
        blurAlpha = 1f - Color.alpha(Theme.getColor(Theme.key_chat_BlurAlpha, null, true)) / 255f;
    }
}
