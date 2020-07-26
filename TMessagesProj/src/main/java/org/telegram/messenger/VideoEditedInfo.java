/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.graphics.Bitmap;
import android.view.View;

import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.PhotoFilterView;
import org.telegram.ui.Components.Point;

import java.util.ArrayList;
import java.util.Locale;

public class VideoEditedInfo {

    public long startTime;
    public long endTime;
    public long avatarStartTime = -1;
    public float start;
    public float end;
    public int rotationValue;
    public int originalWidth;
    public int originalHeight;
    public int originalBitrate;
    public int resultWidth;
    public int resultHeight;
    public int bitrate;
    public int framerate = 24;
    public String originalPath;
    public long estimatedSize;
    public long estimatedDuration;
    public boolean roundVideo;
    public boolean muted;
    public long originalDuration;
    public TLRPC.InputFile file;
    public TLRPC.InputEncryptedFile encryptedFile;
    public byte[] key;
    public byte[] iv;
    public MediaController.SavedFilterState filterState;
    public String paintPath;
    public ArrayList<MediaEntity> mediaEntities;
    public MediaController.CropState cropState;
    public boolean isPhoto;

    public boolean canceled;
    public boolean videoConvertFirstWrite;
    public boolean needUpdateProgress = false;

    public static class MediaEntity {
        public byte type;
        public byte subType;
        public float x;
        public float y;
        public float rotation;
        public float width;
        public float height;
        public String text;
        public int color;
        public int fontSize;
        public int viewWidth;
        public int viewHeight;

        public float scale;
        public float textViewWidth;
        public float textViewHeight;
        public float textViewX;
        public float textViewY;

        public TLRPC.Document document;
        public Object parentObject;

        public int[] metadata;
        public long ptr;
        public float currentFrame;
        public float framesPerDraw;
        public Bitmap bitmap;

        public View view;

        public MediaEntity() {

        }

        private MediaEntity(SerializedData data) {
            type = data.readByte(false);
            subType = data.readByte(false);
            x = data.readFloat(false);
            y = data.readFloat(false);
            rotation = data.readFloat(false);
            width = data.readFloat(false);
            height = data.readFloat(false);
            text = data.readString(false);
            color = data.readInt32(false);
            fontSize = data.readInt32(false);
            viewWidth = data.readInt32(false);
            viewHeight = data.readInt32(false);
        }

        private void serializeTo(SerializedData data) {
            data.writeByte(type);
            data.writeByte(subType);
            data.writeFloat(x);
            data.writeFloat(y);
            data.writeFloat(rotation);
            data.writeFloat(width);
            data.writeFloat(height);
            data.writeString(text);
            data.writeInt32(color);
            data.writeInt32(fontSize);
            data.writeInt32(viewWidth);
            data.writeInt32(viewHeight);
        }

        public MediaEntity copy() {
            MediaEntity entity = new MediaEntity();
            entity.type = type;
            entity.subType = subType;
            entity.x = x;
            entity.y = y;
            entity.rotation = rotation;
            entity.width = width;
            entity.height = height;
            entity.text = text;
            entity.color = color;
            entity.fontSize = fontSize;
            entity.viewWidth = viewWidth;
            entity.viewHeight = viewHeight;
            entity.scale = scale;
            entity.textViewWidth = textViewWidth;
            entity.textViewHeight = textViewHeight;
            entity.textViewX = textViewX;
            entity.textViewY = textViewY;
            return entity;
        }
    }

    public String getString() {
        String filters;
        if (avatarStartTime != -1 || filterState != null || paintPath != null || mediaEntities != null && !mediaEntities.isEmpty() || cropState != null) {
            int len = 10;
            if (filterState != null) {
                len += 160;
            }
            byte[] paintPathBytes;
            if (paintPath != null) {
                paintPathBytes = paintPath.getBytes();
                len += paintPathBytes.length;
            } else {
                paintPathBytes = null;
            }
            SerializedData serializedData = new SerializedData(len);
            serializedData.writeInt32(5);
            serializedData.writeInt64(avatarStartTime);
            serializedData.writeInt32(originalBitrate);
            if (filterState != null) {
                serializedData.writeByte(1);
                serializedData.writeFloat(filterState.enhanceValue);
                serializedData.writeFloat(filterState.softenSkinValue);
                serializedData.writeFloat(filterState.exposureValue);
                serializedData.writeFloat(filterState.contrastValue);
                serializedData.writeFloat(filterState.warmthValue);
                serializedData.writeFloat(filterState.saturationValue);
                serializedData.writeFloat(filterState.fadeValue);
                serializedData.writeInt32(filterState.tintShadowsColor);
                serializedData.writeInt32(filterState.tintHighlightsColor);
                serializedData.writeFloat(filterState.highlightsValue);
                serializedData.writeFloat(filterState.shadowsValue);
                serializedData.writeFloat(filterState.vignetteValue);
                serializedData.writeFloat(filterState.grainValue);
                serializedData.writeInt32(filterState.blurType);
                serializedData.writeFloat(filterState.sharpenValue);
                serializedData.writeFloat(filterState.blurExcludeSize);
                if (filterState.blurExcludePoint != null) {
                    serializedData.writeFloat(filterState.blurExcludePoint.x);
                    serializedData.writeFloat(filterState.blurExcludePoint.y);
                } else {
                    serializedData.writeFloat(0);
                    serializedData.writeFloat(0);
                }
                serializedData.writeFloat(filterState.blurExcludeBlurSize);
                serializedData.writeFloat(filterState.blurAngle);

                for (int a = 0; a < 4; a++) {
                    PhotoFilterView.CurvesValue curvesValue;
                    if (a == 0) {
                        curvesValue = filterState.curvesToolValue.luminanceCurve;
                    } else if (a == 1) {
                        curvesValue = filterState.curvesToolValue.redCurve;
                    } else if (a == 2) {
                        curvesValue = filterState.curvesToolValue.greenCurve;
                    } else {
                        curvesValue = filterState.curvesToolValue.blueCurve;
                    }
                    serializedData.writeFloat(curvesValue.blacksLevel);
                    serializedData.writeFloat(curvesValue.shadowsLevel);
                    serializedData.writeFloat(curvesValue.midtonesLevel);
                    serializedData.writeFloat(curvesValue.highlightsLevel);
                    serializedData.writeFloat(curvesValue.whitesLevel);
                }
            } else {
                serializedData.writeByte(0);
            }
            if (paintPathBytes != null) {
                serializedData.writeByte(1);
                serializedData.writeByteArray(paintPathBytes);
            } else {
                serializedData.writeByte(0);
            }
            if (mediaEntities != null && !mediaEntities.isEmpty()) {
                serializedData.writeByte(1);
                serializedData.writeInt32(mediaEntities.size());
                for (int a = 0, N = mediaEntities.size(); a < N; a++) {
                    mediaEntities.get(a).serializeTo(serializedData);
                }
                serializedData.writeByte(isPhoto ? 1 : 0);
            } else {
                serializedData.writeByte(0);
            }
            if (cropState != null) {
                serializedData.writeByte(1);
                serializedData.writeFloat(cropState.cropPx);
                serializedData.writeFloat(cropState.cropPy);
                serializedData.writeFloat(cropState.cropPw);
                serializedData.writeFloat(cropState.cropPh);
                serializedData.writeFloat(cropState.cropScale);
                serializedData.writeFloat(cropState.cropRotate);
                serializedData.writeInt32(cropState.transformWidth);
                serializedData.writeInt32(cropState.transformHeight);
                serializedData.writeInt32(cropState.transformRotation);
                serializedData.writeBool(cropState.mirrored);
            } else {
                serializedData.writeByte(0);
            }
            filters = Utilities.bytesToHex(serializedData.toByteArray());
            serializedData.cleanup();
        } else {
            filters = "";
        }
        return String.format(Locale.US, "-1_%d_%d_%d_%d_%d_%d_%d_%d_%d_%d_-%s_%s", startTime, endTime, rotationValue, originalWidth, originalHeight, bitrate, resultWidth, resultHeight, originalDuration, framerate, filters, originalPath);
    }

    public boolean parseString(String string) {
        if (string.length() < 6) {
            return false;
        }
        try {
            String[] args = string.split("_");
            if (args.length >= 11) {
                startTime = Long.parseLong(args[1]);
                endTime = Long.parseLong(args[2]);
                rotationValue = Integer.parseInt(args[3]);
                originalWidth = Integer.parseInt(args[4]);
                originalHeight = Integer.parseInt(args[5]);
                bitrate = Integer.parseInt(args[6]);
                resultWidth = Integer.parseInt(args[7]);
                resultHeight = Integer.parseInt(args[8]);
                originalDuration = Long.parseLong(args[9]);
                framerate = Integer.parseInt(args[10]);
                muted = bitrate == -1;
                int start;
                if (args[11].startsWith("-")) {
                    start = 12;
                    String s = args[11].substring(1);
                    if (s.length() > 0) {
                        SerializedData serializedData = new SerializedData(Utilities.hexToBytes(s));
                        int version = serializedData.readInt32(false);
                        if (version >= 3) {
                            avatarStartTime = serializedData.readInt64(false);
                            originalBitrate = serializedData.readInt32(false);
                        }
                        byte has = serializedData.readByte(false);
                        if (has != 0) {
                            filterState = new MediaController.SavedFilterState();
                            filterState.enhanceValue = serializedData.readFloat(false);
                            if (version >= 5) {
                                filterState.softenSkinValue = serializedData.readFloat(false);
                            }
                            filterState.exposureValue = serializedData.readFloat(false);
                            filterState.contrastValue = serializedData.readFloat(false);
                            filterState.warmthValue = serializedData.readFloat(false);
                            filterState.saturationValue = serializedData.readFloat(false);
                            filterState.fadeValue = serializedData.readFloat(false);
                            filterState.tintShadowsColor = serializedData.readInt32(false);
                            filterState.tintHighlightsColor = serializedData.readInt32(false);
                            filterState.highlightsValue = serializedData.readFloat(false);
                            filterState.shadowsValue = serializedData.readFloat(false);
                            filterState.vignetteValue = serializedData.readFloat(false);
                            filterState.grainValue = serializedData.readFloat(false);
                            filterState.blurType = serializedData.readInt32(false);
                            filterState.sharpenValue = serializedData.readFloat(false);
                            filterState.blurExcludeSize = serializedData.readFloat(false);
                            filterState.blurExcludePoint = new Point(serializedData.readFloat(false), serializedData.readFloat(false));
                            filterState.blurExcludeBlurSize = serializedData.readFloat(false);
                            filterState.blurAngle = serializedData.readFloat(false);

                            for (int a = 0; a < 4; a++) {
                                PhotoFilterView.CurvesValue curvesValue;
                                if (a == 0) {
                                    curvesValue = filterState.curvesToolValue.luminanceCurve;
                                } else if (a == 1) {
                                    curvesValue = filterState.curvesToolValue.redCurve;
                                } else if (a == 2) {
                                    curvesValue = filterState.curvesToolValue.greenCurve;
                                } else {
                                    curvesValue = filterState.curvesToolValue.blueCurve;
                                }
                                curvesValue.blacksLevel = serializedData.readFloat(false);
                                curvesValue.shadowsLevel = serializedData.readFloat(false);
                                curvesValue.midtonesLevel = serializedData.readFloat(false);
                                curvesValue.highlightsLevel = serializedData.readFloat(false);
                                curvesValue.whitesLevel = serializedData.readFloat(false);
                            }
                        }
                        has = serializedData.readByte(false);
                        if (has != 0) {
                            byte[] bytes = serializedData.readByteArray(false);
                            paintPath = new String(bytes);
                        }

                        has = serializedData.readByte(false);
                        if (has != 0) {
                            int count = serializedData.readInt32(false);
                            mediaEntities = new ArrayList<>(count);
                            for (int a = 0; a < count; a++) {
                                mediaEntities.add(new MediaEntity(serializedData));
                            }
                            isPhoto = serializedData.readByte(false) == 1;
                        }
                        if (version >= 2) {
                            has = serializedData.readByte(false);
                            if (has != 0) {
                                cropState = new MediaController.CropState();
                                cropState.cropPx = serializedData.readFloat(false);
                                cropState.cropPy = serializedData.readFloat(false);
                                cropState.cropPw = serializedData.readFloat(false);
                                cropState.cropPh = serializedData.readFloat(false);
                                cropState.cropScale = serializedData.readFloat(false);
                                cropState.cropRotate = serializedData.readFloat(false);
                                cropState.transformWidth = serializedData.readInt32(false);
                                cropState.transformHeight = serializedData.readInt32(false);
                                cropState.transformRotation = serializedData.readInt32(false);
                                if (version >= 4) {
                                    cropState.mirrored = serializedData.readBool(false);
                                }
                            }
                        }
                        serializedData.cleanup();
                    }
                } else {
                    start = 11;
                }

                for (int a = start; a < args.length; a++) {
                    if (originalPath == null) {
                        originalPath = args[a];
                    } else {
                        originalPath += "_" + args[a];
                    }
                }
            }
            return true;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public boolean needConvert() {
        return mediaEntities != null || paintPath != null || filterState != null || cropState != null || !roundVideo || roundVideo && (startTime > 0 || endTime != -1 && endTime != estimatedDuration);
    }

    public boolean canAutoPlaySourceVideo() {
        return roundVideo;
    }
}
