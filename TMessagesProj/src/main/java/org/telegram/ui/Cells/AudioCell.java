/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CheckBox;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class AudioCell extends FrameLayout {

    private ImageView playButton;
    private TextView titleTextView;
    private TextView authorTextView;
    private TextView genreTextView;
    private TextView timeTextView;
    private CheckBox checkBox;

    private int currentAccount = UserConfig.selectedAccount;
    private MediaController.AudioEntry audioEntry;
    private boolean needDivider;

    private AudioCellDelegate delegate;

    public interface AudioCellDelegate {
        void startedPlayingAudio(MessageObject messageObject);
    }

    public AudioCell(Context context) {
        super(context);

        playButton = new ImageView(context);
        addView(playButton, LayoutHelper.createFrame(46, 46, ((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP), LocaleController.isRTL ? 0 : 13, 13, LocaleController.isRTL ? 13 : 0, 0));
        playButton.setOnClickListener(v -> {
            if (audioEntry != null) {
                if (MediaController.getInstance().isPlayingMessage(audioEntry.messageObject) && !MediaController.getInstance().isMessagePaused()) {
                    MediaController.getInstance().pauseMessage(audioEntry.messageObject);
                    setPlayDrawable(false);
                } else {
                    ArrayList<MessageObject> arrayList = new ArrayList<>();
                    arrayList.add(audioEntry.messageObject);
                    if (MediaController.getInstance().setPlaylist(arrayList, audioEntry.messageObject)) {
                        setPlayDrawable(true);
                        if (delegate != null) {
                            delegate.startedPlayingAudio(audioEntry.messageObject);
                        }
                    }
                }
            }
        });

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setLines(1);
        titleTextView.setMaxLines(1);
        titleTextView.setSingleLine(true);
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        titleTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 50 : 72, 7, LocaleController.isRTL ? 72 : 50, 0));

        genreTextView = new TextView(context);
        genreTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        genreTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        genreTextView.setLines(1);
        genreTextView.setMaxLines(1);
        genreTextView.setSingleLine(true);
        genreTextView.setEllipsize(TextUtils.TruncateAt.END);
        genreTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(genreTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 50 : 72, 28, LocaleController.isRTL ? 72 : 50, 0));

        authorTextView = new TextView(context);
        authorTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        authorTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        authorTextView.setLines(1);
        authorTextView.setMaxLines(1);
        authorTextView.setSingleLine(true);
        authorTextView.setEllipsize(TextUtils.TruncateAt.END);
        authorTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(authorTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 50 : 72, 44, LocaleController.isRTL ? 72 : 50, 0));

        timeTextView = new TextView(context);
        timeTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        timeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        timeTextView.setLines(1);
        timeTextView.setMaxLines(1);
        timeTextView.setSingleLine(true);
        timeTextView.setEllipsize(TextUtils.TruncateAt.END);
        timeTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
        addView(timeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 18 : 0, 11, LocaleController.isRTL ? 0 : 18, 0));

        checkBox = new CheckBox(context, R.drawable.round_check2);
        checkBox.setVisibility(VISIBLE);
        checkBox.setColor(Theme.getColor(Theme.key_musicPicker_checkbox), Theme.getColor(Theme.key_musicPicker_checkboxCheck));
        addView(checkBox, LayoutHelper.createFrame(22, 22, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 18 : 0, 39, LocaleController.isRTL ? 0 : 18, 0));
    }

    private void setPlayDrawable(boolean play) {
        Drawable circle = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(46), Theme.getColor(Theme.key_musicPicker_buttonBackground), Theme.getColor(Theme.key_musicPicker_buttonBackground));
        Drawable drawable = getResources().getDrawable(play ? R.drawable.audiosend_pause : R.drawable.audiosend_play);
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_musicPicker_buttonIcon), PorterDuff.Mode.MULTIPLY));
        CombinedDrawable combinedDrawable = new CombinedDrawable(circle, drawable);
        combinedDrawable.setCustomSize(AndroidUtilities.dp(46), AndroidUtilities.dp(46));
        playButton.setBackgroundDrawable(combinedDrawable);
    }

    public ImageView getPlayButton() {
        return playButton;
    }

    public TextView getTitleTextView() {
        return titleTextView;
    }

    public TextView getGenreTextView() {
        return genreTextView;
    }

    public TextView getTimeTextView() {
        return timeTextView;
    }

    public TextView getAuthorTextView() {
        return authorTextView;
    }

    public CheckBox getCheckBox() {
        return checkBox;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(72) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setAudio(MediaController.AudioEntry entry, boolean divider, boolean checked) {
        audioEntry = entry;

        titleTextView.setText(audioEntry.title);
        genreTextView.setText(audioEntry.genre);
        authorTextView.setText(audioEntry.author);
        timeTextView.setText(String.format("%d:%02d", audioEntry.duration / 60, audioEntry.duration % 60));
        setPlayDrawable(MediaController.getInstance().isPlayingMessage(audioEntry.messageObject) && !MediaController.getInstance().isMessagePaused());

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
            canvas.drawLine(AndroidUtilities.dp(72), getHeight() - 1, getWidth(), getHeight() - 1, Theme.dividerPaint);
        }
    }
}
