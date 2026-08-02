package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.text.TextUtils;

import androidx.annotation.WorkerThread;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;

import java.io.File;

public final class SlotsDrawable extends RLottieDiceDrawable {

    private enum ReelValue {
        bar,
        berries,
        lemon,
        seven,
        sevenWin
    }

    private ReelValue left;
    private ReelValue center;
    private ReelValue right;

    private final RLottieNative[] lottieNatives = new RLottieNative[5];
    private final int[] frameCounts = new int[5];
    private final int[] frameNums = new int[5];

    private final RLottieNative[] secondLottieNatives = new RLottieNative[3];
    private final int[] secondFrameCounts = new int[3];
    private final int[] secondFrameNums = new int[3];

    private boolean playWinAnimation;

    public SlotsDrawable(String diceEmoji, int w, int h) {
        super(diceEmoji, w, h);
    }

    @Override
    @WorkerThread
    protected int loadFrameRunnableImpl() {
        if (isRecycled) {
            return LOAD_FRAME_RESULT_RECYCLED;
        }
        if (nativePtr == null || isDice == 2 && secondNativePtr == null) {
            return LOAD_FRAME_RESULT_ERROR;
        }
        if (backgroundBitmap == null) {
            try {
                backgroundBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        if (backgroundBitmap != null) {
            try {
                int result;
                if (isDice == 1) {
                    result = -1;
                    for (int a = 0; a < lottieNatives.length; a++) {
                        result = lottieNatives[a].getFrame(frameNums[a], backgroundBitmap, a == 0);
                        if (a == 0) {
                            continue;
                        }
                        if (frameNums[a] + 1 < frameCounts[a]) {
                            frameNums[a]++;
                        } else if (a != 4) {
                            frameNums[a] = 0;
                            nextFrameIsLast = false;
                            if (secondNativePtr != null) {
                                isDice = 2;
                            }
                        }
                    }
                } else {
                    if (setLastFrame) {
                        for (int a = 0; a < secondFrameNums.length; a++) {
                            secondFrameNums[a] = secondFrameCounts[a] - 1;
                        }
                    }
                    if (playWinAnimation) {
                        if (frameNums[0] + 1 < frameCounts[0]) {
                            frameNums[0]++;
                        } else {
                            frameNums[0] = -1;
                        }
                    }

                    lottieNatives[0].getFrame(Math.max(frameNums[0], 0), backgroundBitmap, true);
                    for (int a = 0; a < secondLottieNatives.length; a++) {
                        secondLottieNatives[a].getFrame(secondFrameNums[a] >= 0 ? secondFrameNums[a] : (secondFrameCounts[a] - 1), backgroundBitmap, false);
                        if (!nextFrameIsLast) {
                            if (secondFrameNums[a] + 1 < secondFrameCounts[a]) {
                                secondFrameNums[a]++;
                            } else {
                                secondFrameNums[a] = -1;
                            }
                        }
                    }
                    result = lottieNatives[4].getFrame(frameNums[4], backgroundBitmap, false);
                    if (frameNums[4] + 1 < frameCounts[4]) {
                        frameNums[4]++;
                    }
                    if (secondFrameNums[0] == -1 && secondFrameNums[1] == -1 && secondFrameNums[2] == -1) {
                        nextFrameIsLast = true;
                        autoRepeatPlayCount++;
                    }
                    if (left == right && right == center) {
                        if (secondFrameNums[0] == secondFrameCounts[0] - 100) {
                            playWinAnimation = true;
                            if (left == ReelValue.sevenWin) {
                                Runnable runnable = onFinishCallback == null ? null : onFinishCallback.get();
                                if (runnable != null) {
                                    AndroidUtilities.runOnUIThread(runnable);
                                }
                            }
                        }
                    } else {
                        frameNums[0] = -1;
                    }
                }
                if (result < 0) {
                    return LOAD_FRAME_RESULT_ERROR;
                }
                nextRenderingBitmap = backgroundBitmap;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return LOAD_FRAME_RESULT_OK;
    }

    private ReelValue reelValue(int rawValue) {
        switch (rawValue) {
            case 0:
                return ReelValue.bar;
            case 1:
                return ReelValue.berries;
            case 2:
                return ReelValue.lemon;
            case 3:
            default:
                return ReelValue.seven;
        }
    }

    private void init(int rawValue) {
        rawValue--;

        int leftRawValue = rawValue & 3;
        int centerRawValue = rawValue >> 2 & 3;
        int rightRawValue = rawValue >> 4;

        ReelValue leftReelValue = reelValue(leftRawValue);
        ReelValue centerReelValue = reelValue(centerRawValue);
        ReelValue rightReelValue = reelValue(rightRawValue);

        if (leftReelValue == ReelValue.seven && centerReelValue == ReelValue.seven && rightReelValue == ReelValue.seven) {
            leftReelValue = ReelValue.sevenWin;
            centerReelValue = ReelValue.sevenWin;
            rightReelValue = ReelValue.sevenWin;
        }

        left = leftReelValue;
        center = centerReelValue;
        right = rightReelValue;
    }

    private boolean is777() {
        return left == ReelValue.sevenWin && center == ReelValue.sevenWin && right == ReelValue.sevenWin;
    }

    public boolean setBaseDice(ChatMessageCell messageCell, TLRPC.TL_messages_stickerSet stickerSet) {
        if (nativePtr != null || loadingInBackground) {
            return true;
        }
        loadingInBackground = true;
        MessageObject currentMessageObject = messageCell.getMessageObject();
        int account = messageCell.getMessageObject().currentAccount;
        Utilities.globalQueue.postRunnable(() -> {
            if (destroyAfterLoading) {
                AndroidUtilities.runOnUIThread(() -> {
                    loadingInBackground = false;
                    if (!secondLoadingInBackground && destroyAfterLoading) {
                        recycle(true);
                    }
                });
                return;
            }
            boolean loading = false;
            for (int a = 0; a < lottieNatives.length; a++) {
                if (lottieNatives[a] != null) {
                    continue;
                }
                int num;
                if (a == 0) {
                    num = 1;
                } else if (a == 1) {
                    num = 8;
                } else if (a == 2) {
                    num = 14;
                } else if (a == 3) {
                    num = 20;
                } else {
                    num = 2;
                }
                if (num >= stickerSet.documents.size()) {
                    continue;
                }
                TLRPC.Document document = stickerSet.documents.get(num);
                File path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(document, true);
                String json = AndroidUtilities.readRes(path, 0);
                if (TextUtils.isEmpty(json)) {
                    loading = true;
                    AndroidUtilities.runOnUIThread(() -> {
                        String fileName = FileLoader.getAttachFileName(document);
                        DownloadController.getInstance(account).addLoadingFileObserver(fileName, currentMessageObject, messageCell);
                        FileLoader.getInstance(account).loadFile(document, stickerSet, FileLoader.PRIORITY_NORMAL, 1);
                    });
                } else {
                    final RLottieNative lottieNative = RLottieNative.createFromRawJson(json, "dice", metaData, null);
                    lottieNatives[a] = lottieNative;
                    frameCounts[a] = metaData[0];
                }
            }
            if (loading) {
                AndroidUtilities.runOnUIThread(() -> loadingInBackground = false);
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                loadingInBackground = false;
                if (!secondLoadingInBackground && destroyAfterLoading) {
                    recycle(true);
                    return;
                }
                nativePtr = lottieNatives[0];
                checkChoreographer();
                DownloadController.getInstance(account).removeLoadingFileObserver(messageCell);
                scheduleNextGetFrame();
                invalidateInternal();
            });
        });

        return true;
    }

    public boolean setDiceNumber(ChatMessageCell messageCell, int number, TLRPC.TL_messages_stickerSet stickerSet, boolean instant) {
        if (secondNativePtr != null || secondLoadingInBackground) {
            return true;
        }
        init(number);
        MessageObject currentMessageObject = messageCell.getMessageObject();
        int account = messageCell.getMessageObject().currentAccount;

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

            boolean loading = false;
            for (int a = 0; a < secondLottieNatives.length + 2; a++) {
                int num;
                if (a <= 2) {
                    if (secondLottieNatives[a] != null) {
                        continue;
                    }
                    if (a == 0) {
                        if (left == ReelValue.bar) {
                            num = 5;
                        } else if (left == ReelValue.berries) {
                            num = 6;
                        } else if (left == ReelValue.lemon) {
                            num = 7;
                        } else if (left == ReelValue.seven) {
                            num = 4;
                        } else {
                            num = 3;
                        }
                    } else if (a == 1) {
                        if (center == ReelValue.bar) {
                            num = 11;
                        } else if (center == ReelValue.berries) {
                            num = 12;
                        } else if (center == ReelValue.lemon) {
                            num = 13;
                        } else if (center == ReelValue.seven) {
                            num = 10;
                        } else {
                            num = 9;
                        }
                    } else {
                        if (right == ReelValue.bar) {
                            num = 17;
                        } else if (right == ReelValue.berries) {
                            num = 18;
                        } else if (right == ReelValue.lemon) {
                            num = 19;
                        } else if (right == ReelValue.seven) {
                            num = 16;
                        } else {
                            num = 15;
                        }
                    }
                } else {
                    if (lottieNatives[a] != null) {
                        continue;
                    }
                    if (a == 3) {
                        num = 1;
                    } else {
                        num = 2;
                    }
                }
                TLRPC.Document document = stickerSet.documents.get(num);
                File path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(document, true);
                String json = AndroidUtilities.readRes(path, 0);
                if (TextUtils.isEmpty(json)) {
                    loading = true;
                    AndroidUtilities.runOnUIThread(() -> {
                        String fileName = FileLoader.getAttachFileName(document);
                        DownloadController.getInstance(account).addLoadingFileObserver(fileName, currentMessageObject, messageCell);
                        FileLoader.getInstance(account).loadFile(document, stickerSet, FileLoader.PRIORITY_NORMAL, 1);
                    });
                } else {
                    final RLottieNative lottieNative = RLottieNative.createFromRawJson(json, "dice", metaData, null);
                    if (a <= 2) {
                        secondLottieNatives[a] = lottieNative;
                        secondFrameCounts[a] = metaData[0];
                    } else {
                        lottieNatives[a == 3 ? 0 : 4] = lottieNative;
                        frameCounts[a == 3 ? 0 : 4] = metaData[0];
                    }
                }
            }
            if (loading) {
                AndroidUtilities.runOnUIThread(() -> secondLoadingInBackground = false);
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (instant && nextRenderingBitmap == null && renderingBitmap == null && loadFrameTask == null) {
                    isDice = 2;
                    setLastFrame = true;
                }
                secondLoadingInBackground = false;
                if (!loadingInBackground && destroyAfterLoading) {
                    recycle(true);
                    return;
                }
                secondNativePtr = secondLottieNatives[0];
                DownloadController.getInstance(account).removeLoadingFileObserver(messageCell);
                scheduleNextGetFrame();
                invalidateInternal();
            });
        });
        return true;
    }

    @Override
    public void recycle(boolean uiThread) {
        isRunning = false;
        isRecycled = true;
        checkRunningTasks();
        checkChoreographer();
        if (loadingInBackground || secondLoadingInBackground) {
            destroyAfterLoading = true;
        } else if (loadFrameTask == null) {
            recycleInternal(true);
            recycleResources();
        } else {
            destroyWhenDone = true;
        }
    }

    @Override
    protected void decodeFrameFinishedInternal() {
        if (destroyWhenDone) {
            checkRunningTasks();
            if (loadFrameTask == null) {
                recycleInternal(false);
            }
        }
        if (nativePtr == null && secondNativePtr == null) {
            recycleResources();
            return;
        }
        waitingForNextTask = true;
        if (!hasParentView()) {
            stop();
        }
        scheduleNextGetFrame();
    }

    private void recycleInternal(boolean resetParent) {
        for (int a = 0; a < lottieNatives.length; a++) {
            if (lottieNatives[a] != null) {
                if (resetParent && lottieNatives[a] == nativePtr) {
                    nativePtr = null;
                }
                lottieNatives[a].recycle();
                lottieNatives[a] = null;
            }
        }
        for (int a = 0; a < secondLottieNatives.length; a++) {
            if (secondLottieNatives[a] != null) {
                if (resetParent && secondLottieNatives[a] == secondNativePtr) {
                    secondNativePtr = null;
                }
                secondLottieNatives[a].recycle();
                secondLottieNatives[a] = null;
            }
        }
    }
}
