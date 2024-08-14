/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.video.MediaCodecVideoConvertor;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.Paint.PaintTypeface;
import org.telegram.ui.Components.Paint.Views.LinkPreview;
import org.telegram.ui.Components.PhotoFilterView;
import org.telegram.ui.Components.Point;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Stories.recorder.StoryEntry;
import org.telegram.ui.Stories.recorder.Weather;

import java.util.ArrayList;
import java.util.Locale;

public class VideoEditedInfo {

    public long startTime;
    public long endTime;
    public long avatarStartTime = -1;
    public float start;
    public float end;
    public int compressQuality;
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
    public float volume = 1f;
    public long originalDuration;
    public TLRPC.InputFile file;
    public TLRPC.InputEncryptedFile encryptedFile;
    public byte[] key;
    public byte[] iv;
    public MediaController.SavedFilterState filterState;
    public String paintPath, blurPath, messagePath, messageVideoMaskPath, backgroundPath;
    public ArrayList<MediaEntity> mediaEntities;
    public MediaController.CropState cropState;
    public boolean isPhoto;
    public boolean isStory;
    public StoryEntry.HDRInfo hdrInfo;

    public boolean isSticker;

    public Bitmap thumb;
    public boolean notReadyYet;

    public Integer gradientTopColor, gradientBottomColor;
    public int account;
    public boolean isDark;
    public long wallpaperPeerId = Long.MIN_VALUE;
    public boolean forceFragmenting;

    public boolean alreadyScheduledConverting;

    public boolean canceled;
    public boolean videoConvertFirstWrite;
    public boolean needUpdateProgress = false;
    public boolean shouldLimitFps = true;
    public boolean tryUseHevc = false;
    public boolean fromCamera;

    public ArrayList<MediaCodecVideoConvertor.MixedSoundInfo> mixedSoundInfos = new ArrayList<>();

    public static class EmojiEntity extends TLRPC.TL_messageEntityCustomEmoji {

        public String documentAbsolutePath;
        public MediaEntity entity;
        public byte subType;

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            super.readParams(stream, exception);
            subType = stream.readByte(exception);
            boolean hasPath = stream.readBool(exception);
            if (hasPath) {
                documentAbsolutePath = stream.readString(exception);
            }
            if (TextUtils.isEmpty(documentAbsolutePath)) {
                documentAbsolutePath = null;
            }
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            super.serializeToStream(stream);
            stream.writeByte(subType);
            stream.writeBool(!TextUtils.isEmpty(documentAbsolutePath));
            if (!TextUtils.isEmpty(documentAbsolutePath)) {
                stream.writeString(documentAbsolutePath);
            }
        }
    }

    public static class MediaEntity {

        public static final byte TYPE_STICKER = 0;
        public static final byte TYPE_TEXT = 1;
        public static final byte TYPE_PHOTO = 2;
        public static final byte TYPE_LOCATION = 3;
        public static final byte TYPE_REACTION = 4;
        public static final byte TYPE_ROUND = 5;
        public static final byte TYPE_MESSAGE = 6;
        public static final byte TYPE_LINK = 7;
        public static final byte TYPE_WEATHER = 8;

        public byte type;
        public byte subType;
        public float x;
        public float y;
        public float rotation;
        public float width;
        public float height;
        public float additionalWidth, additionalHeight;
        public String text = "";
        public ArrayList<EmojiEntity> entities = new ArrayList<>();
        public int color;
        public int fontSize;
        public PaintTypeface textTypeface;
        public String textTypefaceKey;
        public int textAlign;
        public int viewWidth;
        public int viewHeight;
        public float roundRadius;

        public String segmentedPath = "";

        public float scale = 1.0f;
        public float textViewWidth;
        public float textViewHeight;
        public float textViewX;
        public float textViewY;
        public boolean customTextView;

        public TLRPC.Document document;
        public Object parentObject;

        public int[] metadata;
        public long ptr;
        public float currentFrame;
        public float framesPerDraw;
        public Bitmap bitmap;
        public Matrix matrix;

        public View view;
        public Canvas canvas;
        public AnimatedFileDrawable animatedFileDrawable;
        public boolean looped;
        public Canvas roundRadiusCanvas;
        public boolean firstSeek;

        public TL_stories.MediaArea mediaArea;
        public TLRPC.MessageMedia media;
        public Weather.State weather;
        public float density;

        public long roundOffset;
        public long roundLeft;
        public long roundRight;
        public long roundDuration;

        public int W, H;
        public ReactionsLayoutInBubble.VisibleReaction visibleReaction;

        public LinkPreview.WebPagePreview linkSettings;

        public MediaEntity() {

        }
        public MediaEntity(AbstractSerializedData data, boolean full) {
            this(data, full, false);
        }

        public MediaEntity(AbstractSerializedData data, boolean full, boolean exception) {
            type = data.readByte(exception);
            subType = data.readByte(exception);
            x = data.readFloat(exception);
            y = data.readFloat(exception);
            rotation = data.readFloat(exception);
            width = data.readFloat(exception);
            height = data.readFloat(exception);
            text = data.readString(exception);
            int count = data.readInt32(exception);
            for (int i = 0; i < count; ++i) {
                EmojiEntity entity = new EmojiEntity();
                data.readInt32(exception);
                entity.readParams(data, exception);
                entities.add(entity);
            }
            color = data.readInt32(exception);
            fontSize = data.readInt32(exception);
            viewWidth = data.readInt32(exception);
            viewHeight = data.readInt32(exception);
            textAlign = data.readInt32(exception);
            textTypeface = PaintTypeface.find(textTypefaceKey = data.readString(exception));
            scale = data.readFloat(exception);
            textViewWidth = data.readFloat(exception);
            textViewHeight = data.readFloat(exception);
            textViewX = data.readFloat(exception);
            textViewY = data.readFloat(exception);
            if (full) {
                int magic = data.readInt32(exception);
                if (magic == TLRPC.TL_null.constructor) {
                    document = null;
                } else {
                    document = TLRPC.Document.TLdeserialize(data, magic, exception);
                }
            }
            if (type == TYPE_LOCATION) {
                density = data.readFloat(exception);
                mediaArea = TL_stories.MediaArea.TLdeserialize(data, data.readInt32(exception), exception);
                media = TLRPC.MessageMedia.TLdeserialize(data, data.readInt32(exception), exception);
                if (data.remaining() > 0) {
                    int magic = data.readInt32(exception);
                    if (magic == 0xdeadbeef) {
                        String emoji = data.readString(exception);
                        if (media instanceof TLRPC.TL_messageMediaVenue) {
                            ((TLRPC.TL_messageMediaVenue) media).emoji = emoji;
                        }
                    }
                }
            } else if (type == TYPE_LINK) {
                density = data.readFloat(exception);
                mediaArea = TL_stories.MediaArea.TLdeserialize(data, data.readInt32(exception), exception);
                linkSettings = LinkPreview.WebPagePreview.TLdeserialize(data, data.readInt32(exception), exception);
            } else if (type == TYPE_REACTION) {
                mediaArea = TL_stories.MediaArea.TLdeserialize(data, data.readInt32(exception), exception);
            } else if (type == TYPE_ROUND) {
                roundOffset = data.readInt64(exception);
                roundLeft = data.readInt64(exception);
                roundRight = data.readInt64(exception);
                roundDuration = data.readInt64(exception);
            } else if (type == TYPE_PHOTO) {
                segmentedPath = data.readString(exception);
            } else if (type == TYPE_WEATHER) {
                int magic = data.readInt32(exception);
                if (magic == 0x7EA7539) {
                    weather = Weather.State.TLdeserialize(data);
                }
            }
        }

        public void serializeTo(AbstractSerializedData data, boolean full) {
            data.writeByte(type);
            data.writeByte(subType);
            data.writeFloat(x);
            data.writeFloat(y);
            data.writeFloat(rotation);
            data.writeFloat(width);
            data.writeFloat(height);
            data.writeString(text);
            data.writeInt32(entities.size());
            for (int i = 0; i < entities.size(); ++i) {
                entities.get(i).serializeToStream(data);
            }
            data.writeInt32(color);
            data.writeInt32(fontSize);
            data.writeInt32(viewWidth);
            data.writeInt32(viewHeight);
            data.writeInt32(textAlign);
            data.writeString(textTypeface == null ? (textTypefaceKey == null ? "" : textTypefaceKey) : textTypeface.getKey());
            data.writeFloat(scale);
            data.writeFloat(textViewWidth);
            data.writeFloat(textViewHeight);
            data.writeFloat(textViewX);
            data.writeFloat(textViewY);
            if (full) {
                if (document == null) {
                    data.writeInt32(TLRPC.TL_null.constructor);
                } else {
                    document.serializeToStream(data);
                }
            }
            if (type == TYPE_LOCATION) {
                data.writeFloat(density);
                mediaArea.serializeToStream(data);
                if (media.provider == null) {
                    media.provider = "";
                }
                if (media.venue_id == null) {
                    media.venue_id = "";
                }
                if (media.venue_type == null) {
                    media.venue_type = "";
                }
                media.serializeToStream(data);
                if (media instanceof TLRPC.TL_messageMediaVenue && ((TLRPC.TL_messageMediaVenue) media).emoji != null) {
                    data.writeInt32(0xdeadbeef);
                    data.writeString(((TLRPC.TL_messageMediaVenue) media).emoji);
                } else {
                    data.writeInt32(TLRPC.TL_null.constructor);
                }
            } else if (type == TYPE_LINK) {
                data.writeFloat(density);
                mediaArea.serializeToStream(data);
                linkSettings.serializeToStream(data);
            } else if (type == TYPE_REACTION) {
                mediaArea.serializeToStream(data);
            } else if (type == TYPE_ROUND) {
                data.writeInt64(roundOffset);
                data.writeInt64(roundLeft);
                data.writeInt64(roundRight);
                data.writeInt64(roundDuration);
            } else if (type == TYPE_PHOTO) {
                data.writeString(segmentedPath);
            } else if (type == TYPE_WEATHER) {
                if (weather == null) {
                    data.writeInt32(0xdeadbeef);
                } else {
                    data.writeInt32(0x7EA7539);
                    weather.serializeToStream(data);
                }
            }
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
            entity.additionalHeight = additionalHeight;
            entity.text = text;
            if (entities != null) {
                entity.entities = new ArrayList<>();
                entity.entities.addAll(entities);
            }
            entity.color = color;
            entity.fontSize = fontSize;
            entity.textTypeface = textTypeface;
            entity.textTypefaceKey = textTypefaceKey;
            entity.textAlign = textAlign;
            entity.viewWidth = viewWidth;
            entity.viewHeight = viewHeight;
            entity.roundRadius = roundRadius;
            entity.scale = scale;
            entity.textViewWidth = textViewWidth;
            entity.textViewHeight = textViewHeight;
            entity.textViewX = textViewX;
            entity.textViewY = textViewY;
            entity.document = document;
            entity.parentObject = parentObject;
            entity.metadata = metadata;
            entity.ptr = ptr;
            entity.currentFrame = currentFrame;
            entity.framesPerDraw = framesPerDraw;
            entity.bitmap = bitmap;
            entity.view = view;
            entity.canvas = canvas;
            entity.animatedFileDrawable = animatedFileDrawable;
            entity.roundRadiusCanvas = roundRadiusCanvas;
            entity.mediaArea = mediaArea;
            entity.media = media;
            entity.density = density;
            entity.W = W;
            entity.H = H;
            entity.visibleReaction = visibleReaction;
            entity.roundOffset = roundOffset;
            entity.roundDuration = roundDuration;
            entity.roundLeft = roundLeft;
            entity.roundRight = roundRight;
            entity.linkSettings = linkSettings;
            entity.weather = weather;
            return entity;
        }
    }

    public String getString() {
        String filters;
        if (avatarStartTime != -1 || filterState != null || paintPath != null || blurPath != null || mediaEntities != null && !mediaEntities.isEmpty() || cropState != null) {
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
            byte[] blurPathBytes;
            if (blurPath != null) {
                blurPathBytes = blurPath.getBytes();
                len += blurPathBytes.length;
            } else {
                blurPathBytes = null;
            }
            SerializedData serializedData = new SerializedData(len);
            serializedData.writeInt32(10);
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
                    mediaEntities.get(a).serializeTo(serializedData, false);
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
            serializedData.writeInt32(0);
            serializedData.writeBool(isStory);
            serializedData.writeBool(fromCamera);
            if (blurPathBytes != null) {
                serializedData.writeByte(1);
                serializedData.writeByteArray(blurPathBytes);
            } else {
                serializedData.writeByte(0);
            }
            serializedData.writeFloat(volume);
            serializedData.writeBool(isSticker);
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
                                mediaEntities.add(new MediaEntity(serializedData, false));
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
                        if (version >= 6) {
                            serializedData.readInt32(false);
                        }
                        if (version >= 7) {
                            isStory = serializedData.readBool(false);
                            fromCamera = serializedData.readBool(false);
                        }
                        if (version >= 8) {
                            has = serializedData.readByte(false);
                            if (has != 0) {
                                byte[] bytes = serializedData.readByteArray(false);
                                blurPath = new String(bytes);
                            }
                        }
                        if (version >= 9) {
                            volume = serializedData.readFloat(false);
                        }
                        if (version >= 10) {
                            isSticker = serializedData.readBool(false);
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
        if (isStory) {
            if (!fromCamera) {
                return true;
            }
            return !mixedSoundInfos.isEmpty() || mediaEntities != null || paintPath != null || blurPath != null || filterState != null || (cropState != null && !cropState.isEmpty()) || startTime > 0 || endTime != -1 && endTime != estimatedDuration || originalHeight != resultHeight || originalWidth != resultWidth;
        }
        return !mixedSoundInfos.isEmpty() || mediaEntities != null || paintPath != null || blurPath != null || filterState != null || cropState != null || !roundVideo || startTime > 0 || endTime != -1 && endTime != estimatedDuration;
    }

    public boolean canAutoPlaySourceVideo() {
        return roundVideo;
    }
}
