/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;

public class DrawerPlayerView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private ImageView playButton;
    private TextView titleTextView;
    private ListView parentListView;
    private MessageObject lastMessageObject;

    public DrawerPlayerView(Context context, ListView listView) {
        super(context);
        parentListView = listView;

        View shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
        addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3));

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(0xffffffff);
        addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 3, 0, 0));

        playButton = new ImageView(context);
        playButton.setScaleType(ImageView.ScaleType.CENTER);
        addView(playButton, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.LEFT, 2, 3, 0, 0));
        playButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (MediaController.getInstance().isAudioPaused()) {
                    MediaController.getInstance().playAudio(MediaController.getInstance().getPlayingMessageObject());
                } else {
                    MediaController.getInstance().pauseAudio(MediaController.getInstance().getPlayingMessageObject());
                }
            }
        });

        titleTextView = new TextView(context);
        titleTextView.setTextColor(0xff15a5ed);
        titleTextView.setMaxLines(1);
        titleTextView.setLines(1);
        titleTextView.setSingleLine(true);
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setGravity(Gravity.CENTER_VERTICAL);
        addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP, 72, 3, 8, 0));

        checkPlayer();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioDidReset);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioPlayStateChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioDidStarted);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioDidReset);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioPlayStateChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioDidStarted);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, AndroidUtilities.dp(51));
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.audioDidStarted || id == NotificationCenter.audioPlayStateChanged || id == NotificationCenter.audioDidReset) {
            checkPlayer();
        }
    }

    private void checkPlayer() {
        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject == null || !messageObject.isMusic()) {
            lastMessageObject = null;
            if (getVisibility() != GONE) {
                setVisibility(GONE);
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) parentListView.getLayoutParams();
                layoutParams.bottomMargin = 0;
                parentListView.setLayoutParams(layoutParams);
            }
        } else {
            if (getVisibility() != VISIBLE) {
                setVisibility(VISIBLE);
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) parentListView.getLayoutParams();
                layoutParams.bottomMargin = AndroidUtilities.dp(48);
                parentListView.setLayoutParams(layoutParams);
            }
            if (MediaController.getInstance().isAudioPaused()) {
                playButton.setImageResource(R.drawable.menu_play);
            } else {
                playButton.setImageResource(R.drawable.menu_pause);
            }
            if (lastMessageObject != messageObject) {
                lastMessageObject = messageObject;
                titleTextView.setText(String.format("%s - %s", messageObject.getMusicAuthor(), messageObject.getMusicTitle()));
            }
        }
    }
}
