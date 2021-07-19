package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;

import java.io.File;

public class SlotsDrawable extends RLottieDrawable {

    enum ReelValue {
        bar,
        berries,
        lemon,
        seven,
        sevenWin
    }

    private ReelValue left;
    private ReelValue center;
    private ReelValue right;

    private long[] nativePtrs = new long[5];
    private int[] frameCounts = new int[5];
    private int[] frameNums = new int[5];
    private long[] secondNativePtrs = new long[3];
    private int[] secondFrameCounts = new int[3];
    private int[] secondFrameNums = new int[3];

    private boolean playWinAnimation;

    public SlotsDrawable(String diceEmoji, int w, int h) {
        super(diceEmoji, w, h);

        loadFrameRunnable = () -> {
            if (isRecycled) {
                return;
            }
            if (nativePtr == 0 || isDice == 2 && secondNativePtr == 0) {
                if (frameWaitSync != null) {
                    frameWaitSync.countDown();
                }
                uiHandler.post(uiRunnableNoFrame);
                return;
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
                        for (int a = 0; a < nativePtrs.length; a++) {
                            result = getFrame(nativePtrs[a], frameNums[a], backgroundBitmap, width, height, backgroundBitmap.getRowBytes(), a == 0);
                            if (a == 0) {
                                continue;
                            }
                            if (frameNums[a] + 1 < frameCounts[a]) {
                                frameNums[a]++;
                            } else if (a != 4) {
                                frameNums[a] = 0;
                                nextFrameIsLast = false;
                                if (secondNativePtr != 0) {
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
                        getFrame(nativePtrs[0], Math.max(frameNums[0], 0), backgroundBitmap, width, height, backgroundBitmap.getRowBytes(), true);
                        for (int a = 0; a < secondNativePtrs.length; a++) {
                            getFrame(secondNativePtrs[a], secondFrameNums[a] >= 0 ? secondFrameNums[a] : (secondFrameCounts[a] - 1), backgroundBitmap, width, height, backgroundBitmap.getRowBytes(), false);
                            if (!nextFrameIsLast) {
                                if (secondFrameNums[a] + 1 < secondFrameCounts[a]) {
                                    secondFrameNums[a]++;
                                } else {
                                    secondFrameNums[a] = -1;
                                }
                            }
                        }
                        result = getFrame(nativePtrs[4], frameNums[4], backgroundBitmap, width, height, backgroundBitmap.getRowBytes(), false);
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
                                    Runnable runnable = onFinishCallback.get();
                                    if (runnable != null) {
                                        AndroidUtilities.runOnUIThread(runnable);
                                    }
                                }
                            }
                        } else {
                            frameNums[0] = -1;
                        }
                    }
                    if (result == -1) {
                        uiHandler.post(uiRunnableNoFrame);
                        if (frameWaitSync != null) {
                            frameWaitSync.countDown();
                        }
                        return;
                    }
                    nextRenderingBitmap = backgroundBitmap;
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            uiHandler.post(uiRunnable);
            if (frameWaitSync != null) {
                frameWaitSync.countDown();
            }
        };
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
        if (nativePtr != 0 || loadingInBackground) {
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
                        recycle();
                    }
                });
                return;
            }
            boolean loading = false;
            for (int a = 0; a < nativePtrs.length; a++) {
                if (nativePtrs[a] != 0) {
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
                TLRPC.Document document = stickerSet.documents.get(num);
                File path = FileLoader.getPathToAttach(document, true);
                String json = readRes(path, 0);
                if (TextUtils.isEmpty(json)) {
                    loading = true;
                    AndroidUtilities.runOnUIThread(() -> {
                        String fileName = FileLoader.getAttachFileName(document);
                        DownloadController.getInstance(account).addLoadingFileObserver(fileName, currentMessageObject, messageCell);
                        FileLoader.getInstance(account).loadFile(document, stickerSet, 1, 1);
                    });
                } else {
                    nativePtrs[a] = createWithJson(json, "dice", metaData, null);
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
                    recycle();
                    return;
                }
                nativePtr = nativePtrs[0];
                DownloadController.getInstance(account).removeLoadingFileObserver(messageCell);
                timeBetweenFrames = Math.max(16, (int) (1000.0f / metaData[1]));
                scheduleNextGetFrame();
                invalidateInternal();
            });
        });

        return true;
    }

    public boolean setDiceNumber(ChatMessageCell messageCell, int number, TLRPC.TL_messages_stickerSet stickerSet, boolean instant) {
        if (secondNativePtr != 0 || secondLoadingInBackground) {
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
                        recycle();
                    }
                });
                return;
            }

            boolean loading = false;
            for (int a = 0; a < secondNativePtrs.length + 2; a++) {
                int num;
                if (a <= 2) {
                    if (secondNativePtrs[a] != 0) {
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
                    if (nativePtrs[a] != 0) {
                        continue;
                    }
                    if (a == 3) {
                        num = 1;
                    } else {
                        num = 2;
                    }
                }
                TLRPC.Document document = stickerSet.documents.get(num);
                File path = FileLoader.getPathToAttach(document, true);
                String json = readRes(path, 0);
                if (TextUtils.isEmpty(json)) {
                    loading = true;
                    AndroidUtilities.runOnUIThread(() -> {
                        String fileName = FileLoader.getAttachFileName(document);
                        DownloadController.getInstance(account).addLoadingFileObserver(fileName, currentMessageObject, messageCell);
                        FileLoader.getInstance(account).loadFile(document, stickerSet, 1, 1);
                    });
                } else {
                    if (a <= 2) {
                        secondNativePtrs[a] = createWithJson(json, "dice", metaData, null);
                        secondFrameCounts[a] = metaData[0];
                    } else {
                        nativePtrs[a == 3 ? 0 : 4] = createWithJson(json, "dice", metaData, null);
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
                    recycle();
                    return;
                }
                secondNativePtr = secondNativePtrs[0];
                DownloadController.getInstance(account).removeLoadingFileObserver(messageCell);
                timeBetweenFrames = Math.max(16, (int) (1000.0f / metaData[1]));
                scheduleNextGetFrame();
                invalidateInternal();
            });
        });
        return true;
    }

    @Override
    public void recycle() {
        isRunning = false;
        isRecycled = true;
        checkRunningTasks();
        if (loadingInBackground || secondLoadingInBackground) {
            destroyAfterLoading = true;
        } else if (loadFrameTask == null && cacheGenerateTask == null) {
            for (int a = 0; a < nativePtrs.length; a++) {
                if (nativePtrs[a] != 0) {
                    if (nativePtrs[a] == nativePtr) {
                        nativePtr = 0;
                    }
                    destroy(nativePtrs[a]);
                    nativePtrs[a] = 0;
                }
            }
            for (int a = 0; a < secondNativePtrs.length; a++) {
                if (secondNativePtrs[a] != 0) {
                    if (secondNativePtrs[a] == secondNativePtr) {
                        secondNativePtr = 0;
                    }
                    destroy(secondNativePtrs[a]);
                    secondNativePtrs[a] = 0;
                }
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
            if (loadFrameTask == null && cacheGenerateTask == null) {
                for (int a = 0; a < nativePtrs.length; a++) {
                    if (nativePtrs[a] != 0) {
                        destroy(nativePtrs[a]);
                        nativePtrs[a] = 0;
                    }
                }
                for (int a = 0; a < secondNativePtrs.length; a++) {
                    if (secondNativePtrs[a] != 0) {
                        destroy(secondNativePtrs[a]);
                        secondNativePtrs[a] = 0;
                    }
                }
            }
        }
        if (nativePtr == 0 && secondNativePtr == 0) {
            recycleResources();
            return;
        }
        waitingForNextTask = true;
        if (!hasParentView()) {
            stop();
        }
        scheduleNextGetFrame();
    }
}
