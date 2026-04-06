package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

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
    private int framesCount;

    public DebugRecordingCanvasReplayFragment(DebugRecordingCanvas debugRecordingCanvas) {
        this.debugRecordingCanvas = debugRecordingCanvas;
        framesCount = debugRecordingCanvas.getCommandCount();
        currentFrame = framesCount / 3;
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
                super.onDraw(canvas);
                invalidate();

                if (currentFrame == framesCount) {
                    debugRecordingCanvas.replayAll(canvas);
                } else {
                    debugRecordingCanvas.replayCommands(canvas, currentFrame);
                }
            }
        };

        contentView.addView(replayView, LayoutHelper.createFrameMatchParent());

        seekBarView = new SeekBarView(context);
        seekBarView.setReportChanges(true);
        seekBarView.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                currentFrame = Math.round(framesCount * progress);
                replayView.invalidate();
            }
        });
        seekBarView.setProgress((float) currentFrame / framesCount);
        contentView.addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.BOTTOM, 16, 0, 16, 16));
        seekBarView.setTranslationY(-AndroidUtilities.navigationBarHeight);

        return fragmentView;
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
