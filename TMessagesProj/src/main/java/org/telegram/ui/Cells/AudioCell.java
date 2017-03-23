/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.Components.CheckBox;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class AudioCell extends FrameLayout {

    private ImageView playButton;
    private TextView titleTextView;
    private TextView authorTextView;
    private TextView genreTextView;
    private TextView timeTextView;
    private CheckBox checkBox;

    private MediaController.AudioEntry audioEntry;
    private boolean needDivider;
    private static Paint paint;

    private AudioCellDelegate delegate;

    public interface AudioCellDelegate {
        void startedPlayingAudio(MessageObject messageObject);
    }

    public AudioCell(Context context) {
        super(context);

        if (paint == null) {
            paint = new Paint();
            paint.setColor(0xffd9d9d9);
            paint.setStrokeWidth(1);
        }

        playButton = new ImageView(context);
        playButton.setScaleType(ImageView.ScaleType.CENTER);
        addView(playButton, LayoutHelper.createFrame(46, 46, ((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP), LocaleController.isRTL ? 0 : 13, 13, LocaleController.isRTL ? 13 : 0, 0));
        playButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (audioEntry != null) {
                    if (MediaController.getInstance().isPlayingAudio(audioEntry.messageObject) && !MediaController.getInstance().isAudioPaused()) {
                        MediaController.getInstance().pauseAudio(audioEntry.messageObject);
                        playButton.setImageResource(R.drawable.audiosend_play);
                    } else {
                        ArrayList<MessageObject> arrayList = new ArrayList<>();
                        arrayList.add(audioEntry.messageObject);
                        if (MediaController.getInstance().setPlaylist(arrayList, audioEntry.messageObject)) {
                            playButton.setImageResource(R.drawable.audiosend_pause);
                            if (delegate != null) {
                                delegate.startedPlayingAudio(audioEntry.messageObject);
                            }
                        }
                    }
                }
            }
        });

        titleTextView = new TextView(context);
        titleTextView.setTextColor(0xff212121);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setLines(1);
        titleTextView.setMaxLines(1);
        titleTextView.setSingleLine(true);
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        titleTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 50 : 72, 7, LocaleController.isRTL ? 72 : 50, 0));

        genreTextView = new TextView(context);
        genreTextView.setTextColor(0xff8a8a8a);
        genreTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        genreTextView.setLines(1);
        genreTextView.setMaxLines(1);
        genreTextView.setSingleLine(true);
        genreTextView.setEllipsize(TextUtils.TruncateAt.END);
        genreTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(genreTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 50 : 72, 28, LocaleController.isRTL ? 72 : 50, 0));

        authorTextView = new TextView(context);
        authorTextView.setTextColor(0xff8a8a8a);
        authorTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        authorTextView.setLines(1);
        authorTextView.setMaxLines(1);
        authorTextView.setSingleLine(true);
        authorTextView.setEllipsize(TextUtils.TruncateAt.END);
        authorTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(authorTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 50 : 72, 44, LocaleController.isRTL ? 72 : 50, 0));

        timeTextView = new TextView(context);
        timeTextView.setTextColor(0xff999999);
        timeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        timeTextView.setLines(1);
        timeTextView.setMaxLines(1);
        timeTextView.setSingleLine(true);
        timeTextView.setEllipsize(TextUtils.TruncateAt.END);
        timeTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
        addView(timeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 18 : 0, 11, LocaleController.isRTL ? 0 : 18, 0));

        checkBox = new CheckBox(context, R.drawable.round_check2);
        checkBox.setVisibility(VISIBLE);
        checkBox.setColor(0xff29b6f7);
        addView(checkBox, LayoutHelper.createFrame(22, 22, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 18 : 0, 39, LocaleController.isRTL ? 0 : 18, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(72) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setAudio(MediaController.AudioEntry entry, boolean divider, boolean checked) {
        audioEntry = entry;

        titleTextView.setText(audioEntry.title);
        genreTextView.setText(audioEntry.genre);
        authorTextView.setText(audioEntry.author);
        timeTextView.setText(String.format("%d:%02d", audioEntry.duration / 60, audioEntry.duration % 60));
        playButton.setImageResource(MediaController.getInstance().isPlayingAudio(audioEntry.messageObject) && !MediaController.getInstance().isAudioPaused() ? R.drawable.audiosend_pause : R.drawable.audiosend_play);

        needDivider = divider;
        setWillNotDraw(!divider);

        checkBox.setChecked(checked, false);
    }

    public void setChecked(boolean value) {
        checkBox.setChecked(value, true);
    }

    public void setDelegate(AudioCellDelegate audioCellDelegate) {
        delegate = audioCellDelegate;
    }

    public MediaController.AudioEntry getAudioEntry() {
        return audioEntry;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(AndroidUtilities.dp(72), getHeight() - 1, getWidth(), getHeight() - 1, paint);
        }
    }
}
