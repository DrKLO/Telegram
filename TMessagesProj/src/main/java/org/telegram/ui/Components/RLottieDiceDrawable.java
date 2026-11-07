package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.readRes;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.text.TextUtils;

import androidx.annotation.WorkerThread;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueuePoolBackground;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

import java.io.File;

public class RLottieDiceDrawable extends RLottieDrawable {
    protected volatile RLottieNative secondNativePtr;
    protected boolean secondLoadingInBackground;
    protected boolean destroyAfterLoading;
    protected volatile boolean setLastFrame;
    protected boolean loadingInBackground;

    private int diceSwitchFramesCount = -1;
    private int secondFramesCount;

    public RLottieDiceDrawable(String diceEmoji, int w, int h) {
        super(w, h);

        isDice = 1;
        String jsonString;
        if ("\uD83C\uDFB2".equals(diceEmoji)) {
            jsonString = readRes(R.raw.diceloop);
            diceSwitchFramesCount = 60;
        } else if ("\uD83C\uDFAF".equals(diceEmoji)) {
            jsonString = readRes(R.raw.dartloop);
        } else {
            jsonString = null;
        }
        getPaint().setFlags(Paint.FILTER_BITMAP_FLAG);
        if (TextUtils.isEmpty(jsonString)) {
            return;
        }
        nativePtr = RLottieNative.createFromRawJson(jsonString, metaData);
    }

    public boolean hasBaseDice() {
        return nativePtr != null || loadingInBackground;
    }

    public boolean setDiceNumber(File path, boolean instant) {
        if (secondNativePtr != null || secondLoadingInBackground) {
            return true;
        }
        String jsonString = readRes(path);
        if (TextUtils.isEmpty(jsonString)) {
            return false;
        }
        if (instant && bothRenderingBitmapsAreNull() && loadFrameTask == null) {
            isDice = 2;
            setLastFrame = true;
        }
        secondLoadingInBackground = true;
        Utilities.globalQueue.postRunnable(() -> {
            if (destroyAfterLoading) {
                AndroidUtilities.runOnUIThread(() -> {
                    secondLoadingInBackground = false;
                    if (!loadingInBackground && destroyAfterLoading) {
                        recycle(true);
                    }
                });
                return;
            }
            secondNativePtr = RLottieNative.createFromRawJson(jsonString);
            final int framesCountToSet = secondNativePtr != null ? secondNativePtr.getFrameCount() : 0;
            final int fpsCountToSet = secondNativePtr != null ? secondNativePtr.getFps() : 0;
            AndroidUtilities.runOnUIThread(() -> {
                secondLoadingInBackground = false;
                if (destroyAfterLoading) {
                    recycle(true);
                    return;
                }
                secondFramesCount = framesCountToSet;
                scheduleNextGetFrame();
                invalidateInternal();
            });
        });
        return true;
    }

    public boolean isDiceRevealed() {
        if (isDice == 1) {
            return false;
        }
        if (isDice == 2) {
            if (setLastFrame) return true;
            float p = getProgress();
            if (secondNativePtr != null) {
                p = currentFrame / (float) secondFramesCount;
            }
            return p > 0.95f;
        }
        return false;
    }

    public boolean setBaseDice(File path) {
        if (nativePtr != null || loadingInBackground) {
            return true;
        }
        String jsonString = readRes(path);
        if (TextUtils.isEmpty(jsonString)) {
            return false;
        }
        loadingInBackground = true;
        Utilities.globalQueue.postRunnable(() -> {
            nativePtr = RLottieNative.createFromRawJson(jsonString, metaData);
            AndroidUtilities.runOnUIThread(() -> {
                loadingInBackground = false;
                if (!secondLoadingInBackground && destroyAfterLoading) {
                    recycle(true);
                    return;
                }
                checkChoreographer();
                scheduleNextGetFrame();
                invalidateInternal();
            });
        });

        return true;
    }

    @Override
    public boolean isHeavyDrawable() {
        return false;
    }

    @Override
    @WorkerThread
    protected int beforeLoadFrameImpl() {
        if (isRecycled) {
            return LOAD_FRAME_RESULT_RECYCLED;
        }
        if (nativePtr == null || isDice == 2 && secondNativePtr == null) {
            return LOAD_FRAME_RESULT_ERROR;
        }
        return LOAD_FRAME_RESULT_OK;
    }

    @Override
    @WorkerThread
    protected int loadFrameRunnableImpl(Bitmap bitmap, boolean needClearBitmap) {
        final RLottieNative ptrToUse;
        if (isDice == 1) {
            ptrToUse = nativePtr;
        } else if (isDice == 2) {
            ptrToUse = secondNativePtr;
            if (setLastFrame) {
                currentFrame = secondFramesCount - 1;
            }
        } else {
            ptrToUse = nativePtr;
        }

        final int result = ptrToUse.getFrame(currentFrame, bitmap, needClearBitmap);
        if (result < 0) {
            return LOAD_FRAME_RESULT_ERROR;
        }

        return LOAD_FRAME_RESULT_OK;
    }

    @Override
    @WorkerThread
    protected void afterLoadFrameImpl() {
        final int framesPerUpdates = 1;
        if (isDice == 1) {
            if (currentFrame + framesPerUpdates < (diceSwitchFramesCount == -1 ? metaData[0] : diceSwitchFramesCount)) {
                currentFrame += framesPerUpdates;
            } else {
                currentFrame = 0;
                nextFrameIsLast = false;
                if (secondNativePtr != null) {
                    isDice = 2;
                }
                if (resetVibrationAfterRestart) {
                    vibrationPattern = null;
                    resetVibrationAfterRestart = false;
                }
            }
        } else if (isDice == 2) {
            if (currentFrame + framesPerUpdates < secondFramesCount) {
                currentFrame += framesPerUpdates;
            } else {
                nextFrameIsLast = true;
                autoRepeatPlayCount++;
            }
        }
    }

    @Override
    public void recycle(boolean uiThread) {
        isRunning = false;
        isRecycled = true;
        checkRunningTasks();
        checkChoreographer();
        if (loadingInBackground || secondLoadingInBackground) {
            destroyAfterLoading = true;
        } else if (loadFrameTask == null && !generatingCache) {
            recycleNativePtr(uiThread);
            if (bitmapsCache != null) {
                bitmapsCache.recycle();
                bitmapsCache = null;
            }
            recycleResources();
        } else {
            destroyWhenDone = true;
        }
    }

    @Override
    protected void decodeFrameFinishedInternal() {
        if (destroyWhenDone) {
            checkRunningTasks();
            if (loadFrameTask == null && nativePtr != null) {
                recycleNativePtr(true);
            }
        }
        if ((nativePtr == null) && secondNativePtr == null && bitmapsCache == null) {
            recycleResources();
            return;
        }
        waitingForNextTask = true;
        if (!hasParentView()) {
            stop();
        }
        if (isRunning) {
            scheduleNextGetFrame();
        }
    }

    @Override
    protected void recycleNativePtr(boolean uiThread) {
        RLottieNative nativePtrFinal = nativePtr;
        RLottieNative secondNativePtrFinal = secondNativePtr;

        nativePtr = null;
        secondNativePtr = null;
        if (nativePtrFinal != null || secondNativePtrFinal != null) {
            final Runnable recycleImpl = () -> {
                if (nativePtrFinal != null) {
                    nativePtrFinal.recycle();
                }
                if (secondNativePtrFinal != null) {
                    secondNativePtrFinal.recycle();
                }
            };

            if (uiThread) {
                DispatchQueuePoolBackground.execute(recycleImpl);
            } else {
                Utilities.globalQueue.postRunnable(recycleImpl);
            }
        }
    }

    @Override
    protected boolean ignoreScheduleNextGetFrame() {
        return loadingInBackground;
    }
}
