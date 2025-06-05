package org.telegram.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.SeekBarView;

public class GLIconSettingsView extends LinearLayout {

    public static float smallStarsSize = 1f;

    public GLIconSettingsView(Context context, GLIconRenderer mRenderer) {
        super(context);
        setOrientation(VERTICAL);

        TextView saturationTextView = new TextView(context);
        saturationTextView.setText("Spectral top ");
        saturationTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        saturationTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        saturationTextView.setLines(1);
        saturationTextView.setMaxLines(1);
        saturationTextView.setSingleLine(true);
        saturationTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
        addView(saturationTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 21, 13, 21, 0));

        SeekBarView seekBar = new SeekBarView(context);
        seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                if (mRenderer.model != null) {
                    mRenderer.model.spec1 = 2 * progress;
                }
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {

            }
        });
        seekBar.setProgress(mRenderer.model == null ? 0 : mRenderer.model.spec1 / 2);
        seekBar.setReportChanges(true);
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 5, 4, 5, 0));


        saturationTextView = new TextView(context);
        saturationTextView.setText("Spectral bottom ");
        saturationTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        saturationTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        saturationTextView.setLines(1);
        saturationTextView.setMaxLines(1);
        saturationTextView.setSingleLine(true);
        saturationTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
        addView(saturationTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 21, 13, 21, 0));

        seekBar = new SeekBarView(context);
        seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                if (mRenderer.model != null) {
                    mRenderer.model.spec2 = 2 * progress;
                }
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {

            }
        });
        seekBar.setProgress(mRenderer.model == null ? 0 : mRenderer.model.spec2 / 2);
        seekBar.setReportChanges(true);
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 5, 4, 5, 0));


        saturationTextView = new TextView(context);
        saturationTextView.setText("Setup spec color");
        saturationTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        saturationTextView.setLines(1);
        saturationTextView.setGravity(Gravity.CENTER);
        saturationTextView.setMaxLines(1);
        saturationTextView.setSingleLine(true);
        saturationTextView.setTextColor(Theme.getColor((Theme.key_featuredStickers_buttonText)));
        saturationTextView.setBackground(Theme.AdaptiveRipple.filledRect(Theme.getColor(Theme.key_featuredStickers_addButton), 4));
        saturationTextView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ColorPicker colorPicker = new ColorPicker(context, false, new ColorPicker.ColorPickerDelegate() {

                    @Override
                    public void setColor(int color, int num, boolean applyNow) {
                        if (mRenderer.model != null) {
                            mRenderer.model.specColor = color;
                        }
                    }
                }) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(300), MeasureSpec.EXACTLY));
                    }
                };
                colorPicker.setColor(mRenderer.model != null ? mRenderer.model.specColor : 0, 0);
                colorPicker.setType(-1, true, 1, 1, false, 0, false);
                BottomSheet bottomSheet = new BottomSheet(context, false);
                bottomSheet.setCustomView(colorPicker);
                bottomSheet.setDimBehind(false);
                bottomSheet.show();
            }
        });
        addView(saturationTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_VERTICAL, 16, 0, 16, 0));


        saturationTextView = new TextView(context);
        saturationTextView.setText("Diffuse ");
        saturationTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        saturationTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        saturationTextView.setLines(1);
        saturationTextView.setMaxLines(1);
        saturationTextView.setSingleLine(true);
        saturationTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
        addView(saturationTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 21, 13, 21, 0));

        seekBar = new SeekBarView(context);
        seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                if (mRenderer.model != null) {
                    mRenderer.model.diffuse = progress;
                }
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {

            }
        });
        seekBar.setProgress(mRenderer.model == null ? 0 : mRenderer.model.diffuse);
        seekBar.setReportChanges(true);
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 5, 4, 5, 0));

        saturationTextView = new TextView(context);
        saturationTextView.setText("Normal map spectral");
        saturationTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        saturationTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        saturationTextView.setLines(1);
        saturationTextView.setMaxLines(1);
        saturationTextView.setSingleLine(true);
        saturationTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
        addView(saturationTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 21, 13, 21, 0));

        seekBar = new SeekBarView(context);
        seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                if (mRenderer.model != null) {
                    mRenderer.model.normalSpec = 2 * progress;
                }
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {

            }
        });
        seekBar.setProgress(mRenderer.model == null ? 0 : mRenderer.model.normalSpec / 2);
        seekBar.setReportChanges(true);
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 5, 4, 5, 0));


        saturationTextView = new TextView(context);
        saturationTextView.setText("Setup normal spec color");
        saturationTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        saturationTextView.setLines(1);
        saturationTextView.setGravity(Gravity.CENTER);
        saturationTextView.setMaxLines(1);
        saturationTextView.setSingleLine(true);
        saturationTextView.setTextColor(Theme.getColor((Theme.key_featuredStickers_buttonText)));
        saturationTextView.setBackground(Theme.AdaptiveRipple.filledRect(Theme.getColor(Theme.key_featuredStickers_addButton), 4));
        // addView(saturationTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 21, 13, 21, 0));
        saturationTextView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ColorPicker colorPicker = new ColorPicker(context, false, new ColorPicker.ColorPickerDelegate() {

                    @Override
                    public void setColor(int color, int num, boolean applyNow) {
                        if (num == 0) {
                            if (mRenderer.model != null) {
                                mRenderer.model.normalSpecColor = color;
                            }
                        }
                    }
                }) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(300), MeasureSpec.EXACTLY));
                    }
                };
                colorPicker.setColor(mRenderer.model == null ? 0 : mRenderer.model.normalSpecColor, 0);
                colorPicker.setType(-1, true, 1, 1, false, 0, false);
                BottomSheet bottomSheet = new BottomSheet(context, false);
                bottomSheet.setCustomView(colorPicker);
                bottomSheet.setDimBehind(false);
                bottomSheet.show();
            }
        });
        addView(saturationTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_VERTICAL, 16, 0, 16, 0));



        saturationTextView = new TextView(context);
        saturationTextView.setText("Small starts size");
        saturationTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        saturationTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        saturationTextView.setLines(1);
        saturationTextView.setMaxLines(1);
        saturationTextView.setSingleLine(true);
        saturationTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
        addView(saturationTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 21, 13, 21, 0));

        seekBar = new SeekBarView(context);
        seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                smallStarsSize = progress * 2;
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {

            }
        });
        seekBar.setProgress(smallStarsSize / 2);
        seekBar.setReportChanges(true);
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 5, 4, 5, 0));

    }

}
