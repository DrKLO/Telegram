package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Trace;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.utils.DebugRecordingCanvas;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

public class DebugRecordingCanvasReplayFragment extends BaseFragment {
    private final DebugRecordingCanvas debugRecordingCanvas;
    private FrameLayout contentView;
    private int currentFrame = 0;
    private View replayView;
    private SeekBarView seekBarView;
    private ImageButton playButton;
    private int framesCount;

    private boolean isPlaying = false;
    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!isPlaying) return;
            currentFrame++;
            if (currentFrame > framesCount) {
                currentFrame = 0;
                isPlaying = false;
            }
            seekBarView.setProgress((float) currentFrame / framesCount);
            replayView.invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public DebugRecordingCanvasReplayFragment(DebugRecordingCanvas debugRecordingCanvas) {
        this.debugRecordingCanvas = debugRecordingCanvas;
        framesCount = debugRecordingCanvas.getCommandCount();
        currentFrame = 0;
        hasOwnBackground = true;
    }

    @Override
    public ActionBar createActionBar(Context context) {
        ActionBar actionBar = super.createActionBar(context);
        actionBar.setAddToContainer(false);
        return actionBar;
    }

    @Override
    public View createView(Context context) {
        fragmentView = contentView = new FrameLayout(context);
        replayView = new View(context) {
            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                Trace.beginSection("render_" + currentFrame + "_" + framesCount);
                super.onDraw(canvas);
                if (!isPlaying) {
                    invalidate();
                }

                if (currentFrame == framesCount) {
                    debugRecordingCanvas.replayAll(canvas);
                } else {
                    debugRecordingCanvas.replayCommands(canvas, currentFrame);
                }
                Trace.endSection();
            }
        };

        contentView.addView(replayView, LayoutHelper.createFrameMatchParent());

        // Play button
        playButton = new ImageButton(context);
        updatePlayButtonIcon();
        playButton.setBackgroundColor(Color.TRANSPARENT);
        playButton.setOnClickListener(v -> togglePlayback());

        // SeekBar
        seekBarView = new SeekBarView(context);
        seekBarView.setReportChanges(true);
        seekBarView.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                stopPlayback();
                currentFrame = Math.round(framesCount * progress);
                replayView.invalidate();
            }
        });
        seekBarView.setProgress((float) currentFrame / framesCount);

        // Bottom bar: [Play] [SeekBar]
        FrameLayout bottomBar = new FrameLayout(context);
        bottomBar.addView(playButton, LayoutHelper.createFrame(38, 38, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
        bottomBar.addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.CENTER_VERTICAL, 46, 0, 0, 0));

        contentView.addView(bottomBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.BOTTOM, 16, 0, 16, 16));
        bottomBar.setTranslationY(-AndroidUtilities.navigationBarHeight);

        return fragmentView;
    }

    private void togglePlayback() {
        if (isPlaying) {
            stopPlayback();
        } else {
            startPlayback();
        }
    }

    private void startPlayback() {
        isPlaying = true;
        currentFrame = 0;
        updatePlayButtonIcon();
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    private void stopPlayback() {
        isPlaying = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        updatePlayButtonIcon();
    }

    private void updatePlayButtonIcon() {
        if (playButton == null) return;
        playButton.setImageResource(isPlaying
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        stopPlayback();
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }
}