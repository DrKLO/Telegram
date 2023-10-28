package org.telegram.ui.Stories.recorder;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.provider.MediaStore;
import android.text.SpannableString;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FileRefController;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.video.MediaCodecVideoConvertor;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.PhotoFilterView;
import org.telegram.ui.Components.RLottieDrawable;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class StoryEntry extends IStoryPart {

    public final int currentAccount = UserConfig.selectedAccount;

    public long draftId;
    public boolean isDraft;
    public long draftDate;

    public long editStoryPeerId;
    public int editStoryId;
    public boolean isEdit;
    public boolean isEditSaved;
    public double fileDuration = -1;
    public boolean editedMedia, editedCaption, editedPrivacy;
    public ArrayList<TL_stories.MediaArea> editedMediaAreas;

    public boolean isError;
    public TLRPC.TL_error error;

    public String audioPath;
    public String audioAuthor, audioTitle;
    public long audioDuration;
    public long audioOffset;
    public float audioLeft, audioRight = 1;
    public float audioVolume = 1;

    public long editDocumentId;
    public long editPhotoId;
    public long editExpireDate;

    public boolean isVideo;
    public File file;
    public boolean fileDeletable;
    public String thumbPath;
    public Bitmap thumbPathBitmap;

    public boolean muted;
    public float left, right = 1;

    public int orientation;
    public int invert;

//    public int width, height;
    public long duration;

    public int resultWidth = 720;
    public int resultHeight = 1280;

    public int partsMaxId = 1;
    public final ArrayList<Part> parts = new ArrayList<>();

    public TLRPC.InputPeer peer;

    public static class Part extends IStoryPart {
        public File file;
        public boolean fileDeletable;
        public int orientantion, invert;

        public void readParams(AbstractSerializedData stream, boolean exception) {
            width = stream.readInt32(exception);
            height = stream.readInt32(exception);
            file = new File(stream.readString(exception));
            fileDeletable = stream.readBool(exception);
            orientantion = stream.readInt32(exception);
            invert = stream.readInt32(exception);
            float[] values = new float[9];
            for (int i = 0; i < 9; ++i) {
                values[i] = stream.readFloat(exception);
            }
            matrix.setValues(values);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(width);
            stream.writeInt32(height);
            stream.writeString(file == null ? "" : file.getAbsolutePath());
            stream.writeBool(fileDeletable);
            stream.writeInt32(orientantion);
            stream.writeInt32(invert);
            float[] values = new float[9];
            matrix.getValues(values);
            for (int i = 0; i < 9; ++i) {
                stream.writeFloat(values[i]);
            }
        }
    }

    // matrix describes transformations from width x height to resultWidth x resultHeight
//    public final Matrix matrix = new Matrix();
    public int gradientTopColor, gradientBottomColor;

    public CharSequence caption;
    public boolean captionEntitiesAllowed = true;
    public StoryPrivacyBottomSheet.StoryPrivacy privacy;
    public final ArrayList<TLRPC.InputPrivacyRule> privacyRules = new ArrayList<>();

    public boolean pinned = true;
    public boolean allowScreenshots;

    public int period = 86400;

    // share as message (postponed)
    public ArrayList<Long> shareUserIds;
    public boolean silent;
    public int scheduleDate;

    public Bitmap blurredVideoThumb;
    public File uploadThumbFile;
    public File draftThumbFile;

    // paint
    public File paintFile;
    public File paintBlurFile;
    public File paintEntitiesFile;
    public long averageDuration = 5000;
    public ArrayList<VideoEditedInfo.MediaEntity> mediaEntities;
    public List<TLRPC.InputDocument> stickers;
    public List<TLRPC.InputDocument> editStickers;

    // filter
    public File filterFile;
    public MediaController.SavedFilterState filterState;

    public Bitmap thumbBitmap;
    private boolean fromCamera;

    public boolean wouldBeVideo() {
        if (isVideo) {
            return true;
        }
        if (audioPath != null) {
            return true;
        }
        if (mediaEntities != null && !mediaEntities.isEmpty()) {
            for (int i = 0; i < mediaEntities.size(); ++i) {
                VideoEditedInfo.MediaEntity entity = mediaEntities.get(i);
                if (entity.type == VideoEditedInfo.MediaEntity.TYPE_STICKER) {
                    if (isAnimated(entity.document, entity.text)) {
                        return true;
                    }
                } else if ((entity.type == VideoEditedInfo.MediaEntity.TYPE_TEXT/* || entity.type == VideoEditedInfo.MediaEntity.TYPE_LOCATION*/) && entity.entities != null && !entity.entities.isEmpty()) {
                    for (int j = 0; j < entity.entities.size(); ++j) {
                        VideoEditedInfo.EmojiEntity e = entity.entities.get(j);
                        if (isAnimated(e.document, e.documentAbsolutePath)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isAnimated(TLRPC.Document document, String path) {
        return document != null && (
            "video/webm".equals(document.mime_type) || "video/mp4".equals(document.mime_type) ||
            MessageObject.isAnimatedStickerDocument(document, true) && RLottieDrawable.getFramesCount(path, null) > 1
        );
    }

    public Bitmap buildBitmap(float scale, Bitmap mainFileBitmap) {
        Matrix tempMatrix = new Matrix();

        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        final int w = (int) (resultWidth * scale), h = (int) (resultHeight * scale);
        Bitmap finalBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(finalBitmap);

        Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradientPaint.setShader(new LinearGradient(0, 0, 0, canvas.getHeight(), new int[] { gradientTopColor, gradientBottomColor }, new float[] {0, 1}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), gradientPaint);

        tempMatrix.set(matrix);
        if (mainFileBitmap != null) {
            final float s = (float) width / mainFileBitmap.getWidth();
            tempMatrix.preScale(s, s);
            tempMatrix.postScale(scale, scale);
            canvas.drawBitmap(mainFileBitmap, tempMatrix, bitmapPaint);
        } else {
            File file = filterFile != null ? filterFile : this.file;
            if (file != null) {
                try {
                    Bitmap fileBitmap = getScaledBitmap(opts -> BitmapFactory.decodeFile(file.getPath(), opts), w, h, true);
                    final float s = (float) width / fileBitmap.getWidth();
                    tempMatrix.preScale(s, s);
                    tempMatrix.postScale(scale, scale);
                    canvas.drawBitmap(fileBitmap, tempMatrix, bitmapPaint);
                    fileBitmap.recycle();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            for (int i = 0; i < parts.size(); ++i) {
                try {
                    final Part part = parts.get(i);
                    Bitmap fileBitmap = getScaledBitmap(opts -> BitmapFactory.decodeFile(part.file.getPath(), opts), w, h, false);
                    final float s = (float) part.width / fileBitmap.getWidth();
                    tempMatrix.set(part.matrix);
                    tempMatrix.preScale(s, s);
                    tempMatrix.postScale(scale, scale);
                    canvas.drawBitmap(fileBitmap, tempMatrix, bitmapPaint);
                    fileBitmap.recycle();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            if (paintFile != null) {
                try {
                    Bitmap paintBitmap = getScaledBitmap(opts -> BitmapFactory.decodeFile(paintFile.getPath(), opts), w, h, false);
                    canvas.save();
                    float s = resultWidth / (float) paintBitmap.getWidth();
                    canvas.scale(s, s);
                    tempMatrix.postScale(scale, scale);
                    canvas.drawBitmap(paintBitmap, 0, 0, bitmapPaint);
                    canvas.restore();
                    paintBitmap.recycle();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            if (paintEntitiesFile != null) {
                try {
                    Bitmap paintBitmap = getScaledBitmap(opts -> BitmapFactory.decodeFile(paintEntitiesFile.getPath(), opts), w, h, false);
                    canvas.save();
                    float s = resultWidth / (float) paintBitmap.getWidth();
                    canvas.scale(s, s);
                    tempMatrix.postScale(scale, scale);
                    canvas.drawBitmap(paintBitmap, 0, 0, bitmapPaint);
                    canvas.restore();
                    paintBitmap.recycle();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }

        return finalBitmap;
    }

    public void buildPhoto(File dest) {
        final Bitmap finalBitmap = buildBitmap(1f, null);
        if (thumbBitmap != null) {
            thumbBitmap.recycle();
            thumbBitmap = null;
        }
        thumbBitmap = Bitmap.createScaledBitmap(finalBitmap, 40, 22, true);
        try {
            FileOutputStream stream = new FileOutputStream(dest);
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream);
            stream.close();
        } catch (Exception e) {
            FileLog.e(e);
        }
        finalBitmap.recycle();
    }

    public static interface DecodeBitmap {
        public Bitmap decode(BitmapFactory.Options options);
    }

    public static Bitmap getScaledBitmap(DecodeBitmap decode, int maxWidth, int maxHeight, boolean allowBlur) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        decode.decode(opts);

        opts.inJustDecodeBounds = false;
        opts.inScaled = false;

        final Runtime runtime = Runtime.getRuntime();
        final long availableMemory = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
        final boolean enoughMemory = (opts.outWidth * opts.outHeight * 4L + maxWidth * maxHeight * 4L) * 1.1 <= availableMemory;

        if (opts.outWidth <= maxWidth && opts.outHeight <= maxHeight) {
            return decode.decode(opts);
        }

        if (enoughMemory && SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE) {
            Bitmap bitmap = decode.decode(opts);

            final float scaleX = maxWidth / (float) bitmap.getWidth(), scaleY = maxHeight / (float) bitmap.getHeight();
            float scale = Math.max(scaleX, scaleY);
//            if (SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH) {
//                scale = Math.min(scale * 2, 1);
//            }
            final int w = (int) (bitmap.getWidth() * scale), h = (int) (bitmap.getHeight() * scale);

            Bitmap scaledBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(scaledBitmap);

            final Matrix matrix = new Matrix();
            final BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            paint.setShader(shader);

            int blurRadius = Utilities.clamp(Math.round(1f / scale), 8, 0);

            matrix.reset();
            matrix.postScale(scale, scale);
            shader.setLocalMatrix(matrix);
            canvas.drawRect(0, 0, w, h, paint);

//            if (allowBlur && blurRadius > 0) {
//                Utilities.stackBlurBitmap(scaledBitmap, blurRadius);
//            }

            return scaledBitmap;
        } else {
            opts.inScaled = true;
            opts.inDensity = opts.outWidth;
            opts.inTargetDensity = maxWidth;
            return decode.decode(opts);
        }
    }

    public File getOriginalFile() {
        if (filterFile != null) {
            return filterFile;
        }
        return file;
    }

    private String ext(File file) {
        if (file == null) {
            return null;
        }
        String s = file.getPath();
        int i;
        if ((i = s.lastIndexOf('.')) > 0)
            return s.substring(i + 1);
        return null;
    }

    public void updateFilter(PhotoFilterView filterView, Runnable whenDone) {
        clearFilter();

        filterState = filterView.getSavedFilterState();
        if (!isVideo) {
            if (filterState.isEmpty()) {
                if (whenDone != null) {
                    whenDone.run();
                }
                return;
            }

            Bitmap bitmap = filterView.getBitmap();
            if (bitmap == null) {
                if (whenDone != null) {
                    whenDone.run();
                }
                return;
            }

            final Matrix matrix = new Matrix();
            matrix.postScale(invert == 1 ? -1.0f : 1.0f, invert == 2 ? -1.0f : 1.0f,  width / 2f, height / 2f);
            matrix.postRotate(-orientation);
            final Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            this.matrix.preScale((float) width / rotatedBitmap.getWidth(), (float) height / rotatedBitmap.getHeight());
            width = rotatedBitmap.getWidth();
            height = rotatedBitmap.getHeight();

            bitmap.recycle();

            if (filterFile != null && filterFile.exists()) {
                filterFile.delete();
            }
            String ext = ext(file);
            final boolean supportTransparent = "png".equals(ext) || "webp".equals(ext);
            filterFile = makeCacheFile(currentAccount, supportTransparent ? "webp" : "jpg");
            if (whenDone == null) {
                try {
                    FileOutputStream stream = new FileOutputStream(filterFile);
                    rotatedBitmap.compress(supportTransparent ? Bitmap.CompressFormat.WEBP : Bitmap.CompressFormat.JPEG, 90, stream);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                rotatedBitmap.recycle();
            } else {
                Utilities.themeQueue.postRunnable(() -> {
                    try {
                        FileOutputStream stream = new FileOutputStream(filterFile);
                        rotatedBitmap.compress(supportTransparent ? Bitmap.CompressFormat.WEBP : Bitmap.CompressFormat.JPEG, 90, stream);
                    } catch (Exception e) {
                        FileLog.e(e, false);
                        if (supportTransparent) {
                            try {
                                FileOutputStream stream = new FileOutputStream(filterFile);
                                rotatedBitmap.compress(Bitmap.CompressFormat.PNG, 90, stream);
                            } catch (Exception e2) {
                                FileLog.e(e2, false);
                            }
                        }
                    }
                    rotatedBitmap.recycle();
                    AndroidUtilities.runOnUIThread(whenDone);
                });
            }
        } else {
            if (whenDone != null) {
                whenDone.run();
            }
        }
    }

    public void clearFilter() {
        if (filterFile != null) {
            filterFile.delete();
            filterFile = null;
        }
    }

    public void clearPaint() {
        if (paintFile != null) {
            paintFile.delete();
            paintFile = null;
        }
        if (paintEntitiesFile != null) {
            paintEntitiesFile.delete();
            paintEntitiesFile = null;
        }
    }

    public void destroy(boolean draft) {
        if (blurredVideoThumb != null && !blurredVideoThumb.isRecycled()) {
            blurredVideoThumb.recycle();
            blurredVideoThumb = null;
        }
        if (uploadThumbFile != null) {
            uploadThumbFile.delete();
            uploadThumbFile = null;
        }
        if (!draft) {
            clearPaint();
            clearFilter();
            if (file != null) {
                if (fileDeletable && (!isEdit || editedMedia)) {
                       file.delete();
                }
                file = null;
            }
            if (thumbPath != null) {
                if (fileDeletable) {
                    new File(thumbPath).delete();
                }
                thumbPath = null;
            }
            for (Part part : parts) {
                if (part.fileDeletable) {
                    part.file.delete();
                }
                part.file = null;
            }
        }
        if (thumbPathBitmap != null) {
            thumbPathBitmap.recycle();
            thumbPathBitmap = null;
        }
        cancelCheckStickers();
    }

    public static StoryEntry fromStoryItem(File file, TL_stories.StoryItem storyItem) {
        StoryEntry entry = new StoryEntry();
        entry.isEdit = true;
        entry.editStoryId = storyItem.id;
        entry.file = file;
        entry.fileDeletable = false;
        entry.width = 720;
        entry.height = 1280;
        if (storyItem.media instanceof TLRPC.TL_messageMediaPhoto) {
            entry.isVideo = false;
            if (file != null) {
                entry.decodeBounds(file.getAbsolutePath());
            }
        } else if (storyItem.media instanceof TLRPC.TL_messageMediaDocument) {
            entry.isVideo = true;
            if (storyItem.media.document != null && storyItem.media.document.attributes != null) {
                for (int i = 0; i < storyItem.media.document.attributes.size(); ++i) {
                    TLRPC.DocumentAttribute attr = storyItem.media.document.attributes.get(i);
                    if (attr instanceof TLRPC.TL_documentAttributeVideo) {
                        entry.width = attr.w;
                        entry.height = attr.h;
                        entry.fileDuration = attr.duration;
                        break;
                    }
                }
            }
            if (storyItem.media.document != null) {
                if (storyItem.firstFramePath != null) {
                    entry.thumbPath = storyItem.firstFramePath;
                } else if (storyItem.media.document.thumbs != null) {
                    for (int i = 0; i < storyItem.media.document.thumbs.size(); ++i) {
                        TLRPC.PhotoSize photoSize = storyItem.media.document.thumbs.get(i);
                        if (photoSize instanceof TLRPC.TL_photoStrippedSize) {
                            entry.thumbPathBitmap = ImageLoader.getStrippedPhotoBitmap(photoSize.bytes, null);
                            break;
                        }
                        File path = FileLoader.getInstance(entry.currentAccount).getPathToAttach(photoSize, true);
                        if (path != null && path.exists()) {
                            entry.thumbPath = path.getAbsolutePath();
                            break;
                        }
                    }
                }
            }
        }
        entry.privacyRules.clear();
        entry.privacyRules.addAll(StoryPrivacyBottomSheet.StoryPrivacy.toInput(entry.currentAccount, storyItem.privacy));
        entry.period = storyItem.expire_date - storyItem.date;
        try {
            CharSequence caption = new SpannableString(storyItem.caption);
            caption = Emoji.replaceEmoji(caption, Theme.chat_msgTextPaint.getFontMetricsInt(), true);
            MessageObject.addEntitiesToText(caption, storyItem.entities, true, false, true, false);
            caption = MessageObject.replaceAnimatedEmoji(caption, storyItem.entities, Theme.chat_msgTextPaint.getFontMetricsInt());
            entry.caption = caption;
        } catch (Exception ignore) {}
        entry.setupMatrix();
        entry.checkStickers(storyItem);
        entry.editedMediaAreas = storyItem.media_areas;
        entry.peer = MessagesController.getInstance(entry.currentAccount).getInputPeer(storyItem.dialogId);
        return entry;
    }

    public static StoryEntry fromPhotoEntry(MediaController.PhotoEntry photoEntry) {
        StoryEntry entry = new StoryEntry();
        entry.file = new File(photoEntry.path);
        entry.orientation = photoEntry.orientation;
        entry.invert = photoEntry.invert;
        entry.isVideo = photoEntry.isVideo;
        entry.thumbPath = photoEntry.thumbPath;
        entry.duration = photoEntry.duration * 1000L;
        entry.left = 0;
        entry.right = Math.min(1, 59_500f / entry.duration);
        if (entry.isVideo && entry.thumbPath == null) {
            entry.thumbPath = "vthumb://" + photoEntry.imageId;
        }
        entry.gradientTopColor = photoEntry.gradientTopColor;
        entry.gradientBottomColor = photoEntry.gradientBottomColor;
        entry.decodeBounds(entry.file.getAbsolutePath());
        entry.setupMatrix();
        return entry;
    }

    public static StoryEntry fromPhotoShoot(File file, int rotate) {
        StoryEntry entry = new StoryEntry();
        entry.file = file;
        entry.fileDeletable = true;
        entry.orientation = rotate;
        entry.invert = 0;
        entry.isVideo = false;
        if (file != null) {
            entry.decodeBounds(file.getAbsolutePath());
        }
        entry.setupMatrix();
        return entry;
    }

    public void decodeBounds(String path) {
        if (path != null) {
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, options);
                width = options.outWidth;
                height = options.outHeight;
            } catch (Exception ignore) {}
        }
        if (!isVideo) {
            int side = (int) Math.max(width, height / 16f * 9f);
//            if (side <= (480 + 720) / 2) {
//                resultWidth = 480;
//                resultHeight = 853;
//            } else
            if (side <= (720 + 1080) / 2) {
                resultWidth = 720;
                resultHeight = 1280;
            } else {
                resultWidth = 1080;
                resultHeight = 1920;
            }
        }
    }

    public void setupMatrix() {
        setupMatrix(matrix, 0);
    }

    public void setupMatrix(Matrix matrix, int rotate) {
        matrix.reset();
        int width = this.width, height = this.height;
        int or = orientation + rotate;
        matrix.postScale(invert == 1 ? -1.0f : 1.0f, invert == 2 ? -1.0f : 1.0f, width / 2f, height / 2f);
        if (or != 0) {
            matrix.postTranslate(-width / 2f, -height / 2f);
            matrix.postRotate(or);
            if (or == 90 || or == 270) {
                final int swap = height;
                height = width;
                width = swap;
            }
            matrix.postTranslate(width / 2f, height / 2f);
        }
        float scale = (float) resultWidth / width;
        if ((float) height / (float) width > 1.29f) {
            scale = Math.max(scale, (float) resultHeight / height);
        }
        matrix.postScale(scale, scale);
        matrix.postTranslate((resultWidth - width * scale) / 2f, (resultHeight - height * scale) / 2f);
    }

    public void setupGradient(Runnable done) {
        if (isVideo && gradientTopColor == 0 && gradientBottomColor == 0) {
            if (thumbPath != null) {
                Bitmap bitmap = null;
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    if (thumbPath.startsWith("vthumb://")) {
                        long id = Integer.parseInt(thumbPath.substring(9));
                        options.inJustDecodeBounds = true;
                        MediaStore.Video.Thumbnails.getThumbnail(ApplicationLoader.applicationContext.getContentResolver(), id, MediaStore.Video.Thumbnails.MINI_KIND, options);

                        options.inSampleSize = calculateInSampleSize(options, 240, 240);
                        options.inJustDecodeBounds = false;
                        options.inPreferredConfig = Bitmap.Config.RGB_565;
                        options.inDither = true;
                        bitmap = MediaStore.Video.Thumbnails.getThumbnail(ApplicationLoader.applicationContext.getContentResolver(), id, MediaStore.Video.Thumbnails.MINI_KIND, options);
                    } else {
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(thumbPath);

                        options.inSampleSize = calculateInSampleSize(options, 240, 240);
                        options.inJustDecodeBounds = false;
                        options.inPreferredConfig = Bitmap.Config.RGB_565;
                        options.inDither = true;
                        bitmap = BitmapFactory.decodeFile(thumbPath);
                    }
                } catch (Exception ignore) {}
                if (bitmap != null) {
                    final Bitmap finalBitmap = bitmap;
                    DominantColors.getColors(true, finalBitmap, true, colors -> {
                        gradientTopColor = colors[0];
                        gradientBottomColor = colors[1];
                        finalBitmap.recycle();

                        if (done != null) {
                            done.run();
                        }
                    });
                }
            } else if (thumbPathBitmap != null) {
                DominantColors.getColors(true, thumbPathBitmap, true, colors -> {
                    gradientTopColor = colors[0];
                    gradientBottomColor = colors[1];
                    if (done != null) {
                        done.run();
                    }
                });
            }
        }
    }

    public static StoryEntry fromVideoShoot(File file, String thumbPath, long duration) {
        StoryEntry entry = new StoryEntry();
        entry.fromCamera = true;
        entry.file = file;
        entry.fileDeletable = true;
        entry.orientation = 0;
        entry.invert = 0;
        entry.isVideo = true;
        entry.duration = duration;
        entry.thumbPath = thumbPath;
        entry.left = 0;
        entry.right = Math.min(1, 59_500f / entry.duration);
        return entry;
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int heightRatio = (int) Math.ceil((float) height / (float) reqHeight);
            final int widthRatio = (int) Math.ceil((float) width / (float) reqWidth);
            inSampleSize = Math.min(heightRatio, widthRatio);
        }
        return Math.max(1, (int) Math.pow(inSampleSize, Math.floor(Math.log(inSampleSize) / Math.log(2))));
    }

    public static void setupScale(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long availableMemory = maxMemory - usedMemory;
        final boolean enoughMemory = options.outWidth * options.outHeight * 4L * 2L <= availableMemory;
        if (!enoughMemory || Math.max(options.outWidth, options.outHeight) > 4200 || SharedConfig.getDevicePerformanceClass() <= SharedConfig.PERFORMANCE_CLASS_LOW) {
//            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inScaled = true;
            options.inDensity = options.outWidth;
            options.inTargetDensity = reqWidth;
        }
    }

    public void getVideoEditedInfo(@NonNull Utilities.Callback<VideoEditedInfo> whenDone) {
        if (!wouldBeVideo()) {
            whenDone.run(null);
            return;
        }
        if (!isVideo && (resultWidth > 720 || resultHeight > 1280)) {
            float s = 720f / resultWidth;
            matrix.postScale(s, s, 0, 0);
            resultWidth = 720;
            resultHeight = 1280;
        }
        final String videoPath = file.getAbsolutePath();
        Utilities.globalQueue.postRunnable(() -> {
            final int[] params = new int[AnimatedFileDrawable.PARAM_NUM_COUNT];
            AnimatedFileDrawable.getVideoInfo(videoPath, params);
            AndroidUtilities.runOnUIThread(() -> {
                VideoEditedInfo info = new VideoEditedInfo();

                info.isStory = true;
                info.fromCamera = fromCamera;
                info.originalWidth = width;
                info.originalHeight = height;
                info.resultWidth = resultWidth;
                info.resultHeight = resultHeight;
                info.paintPath = paintFile == null ? null : paintFile.getPath();

                final int encoderBitrate = MediaController.extractRealEncoderBitrate(info.resultWidth, info.resultHeight, info.bitrate, true);
                if (isVideo) {
                    info.originalPath = videoPath;
                    info.isPhoto = false;
                    info.framerate = Math.min(59, params[AnimatedFileDrawable.PARAM_NUM_FRAMERATE]);
                    int videoBitrate = MediaController.getVideoBitrate(videoPath);
                    info.originalBitrate = videoBitrate == -1 ? params[AnimatedFileDrawable.PARAM_NUM_BITRATE] : videoBitrate;
                    if (info.originalBitrate < 1_000_000 && (mediaEntities != null && !mediaEntities.isEmpty())) {
                        info.bitrate = 2_000_000;
                        info.originalBitrate = -1;
                    } else if (info.originalBitrate < 500_000) {
                        info.bitrate = 2_500_000;
                        info.originalBitrate = -1;
                    } else {
                        info.bitrate = Utilities.clamp(info.originalBitrate, 3_000_000, 500_000);
                    }
                    FileLog.d("story bitrate, original = " + info.originalBitrate + " => " + info.bitrate);
                    info.originalDuration = (duration = params[AnimatedFileDrawable.PARAM_NUM_DURATION]) * 1000L;
                    info.startTime = (long) (left * duration) * 1000L;
                    info.endTime = (long) (right * duration) * 1000L;
                    info.estimatedDuration = info.endTime - info.startTime;
                    info.muted = muted;
                    info.estimatedSize = (long) (params[AnimatedFileDrawable.PARAM_NUM_AUDIO_FRAME_SIZE] + params[AnimatedFileDrawable.PARAM_NUM_DURATION] / 1000.0f * encoderBitrate / 8);
                    info.estimatedSize = Math.max(file.length(), info.estimatedSize);
                    info.filterState = filterState;
                    info.blurPath = paintBlurFile == null ? null : paintBlurFile.getPath();
                } else {
                    if (filterFile != null) {
                        info.originalPath = filterFile.getAbsolutePath();
                    } else {
                        info.originalPath = videoPath;
                    }
                    info.isPhoto = true;
                    if (audioPath != null) {
                        info.estimatedDuration = info.originalDuration = duration = (long) ((audioRight - audioLeft) * audioDuration);
                    } else {
                        info.estimatedDuration = info.originalDuration = duration = averageDuration;
                    }
                    info.startTime = -1;
                    info.endTime = -1;
                    info.muted = true;
                    info.originalBitrate = -1;
                    info.bitrate = -1;
                    info.framerate = 30;
                    info.estimatedSize = (long) (duration / 1000.0f * encoderBitrate / 8);
                    info.filterState = null;
                }
                info.avatarStartTime = -1;

                info.cropState = new MediaController.CropState();
                info.cropState.useMatrix = new Matrix();
                info.cropState.useMatrix.set(matrix);

                info.mediaEntities = mediaEntities;

                info.gradientTopColor = gradientTopColor;
                info.gradientBottomColor = gradientBottomColor;
                info.forceFragmenting = true;

                info.hdrInfo = hdrInfo;
                info.parts = parts;

                info.mixedSoundInfos.clear();
                if (audioPath != null) {
                    final MediaCodecVideoConvertor.MixedSoundInfo soundInfo = new MediaCodecVideoConvertor.MixedSoundInfo(audioPath);
                    soundInfo.volume = audioVolume;
                    soundInfo.audioOffset = (long) (audioLeft * audioDuration) * 1000L;
                    if (isVideo) {
                        soundInfo.startTime = (long) (audioOffset - left * duration) * 1000L;
                    } else {
                        soundInfo.startTime = 0;
                    }
                    soundInfo.duration = (long) ((audioRight - audioLeft) * audioDuration) * 1000L;
                    info.mixedSoundInfos.add(soundInfo);
                }

                whenDone.run(info);
            });
        });
    }

    public static File makeCacheFile(final int account, boolean video) {
        return makeCacheFile(account, video ? "mp4" : "jpg");
    }

    public static File makeCacheFile(final int account, String ext) {
        TLRPC.TL_fileLocationToBeDeprecated location = new TLRPC.TL_fileLocationToBeDeprecated();
        location.volume_id = Integer.MIN_VALUE;
        location.dc_id = Integer.MIN_VALUE;
        location.local_id = SharedConfig.getLastLocalId();
        location.file_reference = new byte[0];

        TLObject object;
        if ("mp4".equals(ext)) {
            TLRPC.VideoSize videoSize = new TLRPC.TL_videoSize_layer127();
            videoSize.location = location;
            object = videoSize;
        } else {
            TLRPC.PhotoSize photoSize = new TLRPC.TL_photoSize_layer127();
            photoSize.location = location;
            object = photoSize;
        }

        return FileLoader.getInstance(account).getPathToAttach(object, ext, true);
    }

    public static class HDRInfo {

        public int colorStandard;
        public int colorRange;
        public int colorTransfer;

        public float maxlum;
        public float minlum;

        public int getHDRType() {
//            if (maxlum <= 0 && minlum <= 0) {
//                return 0;
//            } else
            if (colorStandard == MediaFormat.COLOR_STANDARD_BT2020) {
                if (colorTransfer == MediaFormat.COLOR_TRANSFER_HLG) {
                    return 1;
                } else if (colorTransfer == MediaFormat.COLOR_TRANSFER_ST2084) {
                    return 2;
                }
            }
            return 0;
        }
    }

    public HDRInfo hdrInfo;

    public void detectHDR(Utilities.Callback<HDRInfo> whenDetected) {
        if (whenDetected == null) {
            return;
        }
        if (hdrInfo != null) {
            whenDetected.run(hdrInfo);
            return;
        }
        if (!isVideo || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            whenDetected.run(hdrInfo = new HDRInfo());
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                HDRInfo hdrInfo;
                if (this.hdrInfo == null) {
                    hdrInfo = this.hdrInfo = new HDRInfo();
                    hdrInfo.maxlum = 1000f;
                    hdrInfo.minlum = 0.001f;
                } else {
                    hdrInfo = this.hdrInfo;
                }
                MediaExtractor extractor = new MediaExtractor();
                extractor.setDataSource(file.getAbsolutePath());
                int videoIndex = MediaController.findTrack(extractor, false);
                extractor.selectTrack(videoIndex);
                MediaFormat videoFormat = extractor.getTrackFormat(videoIndex);
                if (videoFormat.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                    hdrInfo.colorTransfer = videoFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER);
                }
                if (videoFormat.containsKey(MediaFormat.KEY_COLOR_STANDARD)) {
                    hdrInfo.colorStandard = videoFormat.getInteger(MediaFormat.KEY_COLOR_STANDARD);
                }
                if (videoFormat.containsKey(MediaFormat.KEY_COLOR_RANGE)) {
                    hdrInfo.colorRange = videoFormat.getInteger(MediaFormat.KEY_COLOR_RANGE);
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                this.hdrInfo = hdrInfo;
                AndroidUtilities.runOnUIThread(() -> whenDetected.run(hdrInfo));
            }
        });
    }

    public void checkStickers(TL_stories.StoryItem storyItem) {
        if (storyItem == null || storyItem.media == null) {
            return;
        }
        final TLRPC.TL_messages_getAttachedStickers req = new TLRPC.TL_messages_getAttachedStickers();
        if (storyItem.media.photo != null) {
            TLRPC.Photo photo = (TLRPC.Photo) storyItem.media.photo;
            if (!photo.has_stickers) {
                return;
            }
            TLRPC.TL_inputStickeredMediaPhoto inputStickeredMediaPhoto = new TLRPC.TL_inputStickeredMediaPhoto();
            inputStickeredMediaPhoto.id = new TLRPC.TL_inputPhoto();
            inputStickeredMediaPhoto.id.id = photo.id;
            inputStickeredMediaPhoto.id.access_hash = photo.access_hash;
            inputStickeredMediaPhoto.id.file_reference = photo.file_reference;
            if (inputStickeredMediaPhoto.id.file_reference == null) {
                inputStickeredMediaPhoto.id.file_reference = new byte[0];
            }
            req.media = inputStickeredMediaPhoto;
        } else if (storyItem.media.document != null) {
            TLRPC.Document document = (TLRPC.Document) storyItem.media.document;
            if (!MessageObject.isDocumentHasAttachedStickers(document)) {
                return;
            }
            TLRPC.TL_inputStickeredMediaDocument inputStickeredMediaDocument = new TLRPC.TL_inputStickeredMediaDocument();
            inputStickeredMediaDocument.id = new TLRPC.TL_inputDocument();
            inputStickeredMediaDocument.id.id = document.id;
            inputStickeredMediaDocument.id.access_hash = document.access_hash;
            inputStickeredMediaDocument.id.file_reference = document.file_reference;
            if (inputStickeredMediaDocument.id.file_reference == null) {
                inputStickeredMediaDocument.id.file_reference = new byte[0];
            }
            req.media = inputStickeredMediaDocument;
        } else {
            return;
        }
        final RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            checkStickersReqId = 0;
            if (response instanceof TLRPC.Vector) {
                editStickers = new ArrayList<>();
                TLRPC.Vector vector = (TLRPC.Vector) response;
                for (int i = 0; i < vector.objects.size(); ++i) {
                    TLRPC.StickerSetCovered setCovered = (TLRPC.StickerSetCovered) vector.objects.get(i);
                    TLRPC.Document document = setCovered.cover;
                    if (document == null && !setCovered.covers.isEmpty()) {
                        document = setCovered.covers.get(0);
                    }
                    if (document == null && setCovered instanceof TLRPC.TL_stickerSetFullCovered) {
                        TLRPC.TL_stickerSetFullCovered fullCovered = ((TLRPC.TL_stickerSetFullCovered) setCovered);
                        if (!fullCovered.documents.isEmpty()) {
                            document = fullCovered.documents.get(0);
                        }
                    }
                    if (document != null) {
                        TLRPC.InputDocument inputDocument = new TLRPC.TL_inputDocument();
                        inputDocument.id = document.id;
                        inputDocument.access_hash = document.access_hash;
                        inputDocument.file_reference = document.file_reference;
                        editStickers.add(inputDocument);
                    }
                }
            }
        });
        checkStickersReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error != null && FileRefController.isFileRefError(error.text) && storyItem != null) {
                FileRefController.getInstance(currentAccount).requestReference(storyItem, req, requestDelegate);
                return;
            }
            requestDelegate.run(response, error);
        });
    }

    private int checkStickersReqId = 0;
    public void cancelCheckStickers() {
        if (checkStickersReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(checkStickersReqId, true);
        }
    }

    public StoryEntry copy() {
        StoryEntry newEntry = new StoryEntry();
        newEntry.draftId = draftId;
        newEntry.isDraft = isDraft;
        newEntry.draftDate = draftDate;
        newEntry.editStoryPeerId = editStoryPeerId;
        newEntry.editStoryId = editStoryId;
        newEntry.isEdit = isEdit;
        newEntry.isEditSaved = isEditSaved;
        newEntry.fileDuration = fileDuration;
        newEntry.editedMedia = editedMedia;
        newEntry.editedCaption = editedCaption;
        newEntry.editedPrivacy = editedPrivacy;
        newEntry.editedMediaAreas = editedMediaAreas;
        newEntry.isError = isError;
        newEntry.error = error;
        newEntry.audioPath = audioPath;
        newEntry.audioAuthor = audioAuthor;
        newEntry.audioTitle = audioTitle;
        newEntry.audioDuration = audioDuration;
        newEntry.audioOffset = audioOffset;
        newEntry.audioLeft = audioLeft;
        newEntry.audioRight = audioRight;
        newEntry.audioVolume = audioVolume;
        newEntry.editDocumentId = editDocumentId;
        newEntry.editPhotoId = editPhotoId;
        newEntry.editExpireDate = editExpireDate;
        newEntry.isVideo = isVideo;
        newEntry.file = file;
        newEntry.fileDeletable = fileDeletable;
        newEntry.thumbPath = thumbPath;
        newEntry.muted = muted;
        newEntry.left = left;
        newEntry.right = right;
        newEntry.duration = duration;
        newEntry.width = width;
        newEntry.height = height;
        newEntry.resultWidth = resultWidth;
        newEntry.resultHeight = resultHeight;
        newEntry.partsMaxId = partsMaxId;
        newEntry.parts.clear();
        newEntry.parts.addAll(parts);
        newEntry.peer = peer;
        newEntry.invert = invert;
        newEntry.matrix.set(matrix);
        newEntry.gradientTopColor = gradientTopColor;
        newEntry.gradientBottomColor = gradientBottomColor;
        newEntry.caption = caption;
        newEntry.captionEntitiesAllowed = captionEntitiesAllowed;
        newEntry.privacy = privacy;
        newEntry.privacyRules.clear();
        newEntry.privacyRules.addAll(privacyRules);
        newEntry.pinned = pinned;
        newEntry.allowScreenshots = allowScreenshots;
        newEntry.period = period;
        newEntry.shareUserIds = shareUserIds;
        newEntry.silent = silent;
        newEntry.scheduleDate = scheduleDate;
        newEntry.blurredVideoThumb = blurredVideoThumb;
        newEntry.uploadThumbFile = uploadThumbFile;
        newEntry.draftThumbFile = draftThumbFile;
        newEntry.paintFile = paintFile;
        newEntry.paintBlurFile = paintBlurFile;
        newEntry.paintEntitiesFile = paintEntitiesFile;
        newEntry.averageDuration = averageDuration;
        newEntry.mediaEntities = new ArrayList<>();
        if (mediaEntities != null) {
            for (int i = 0; i < mediaEntities.size(); ++i) {
                newEntry.mediaEntities.add(mediaEntities.get(i).copy());
            }
        }
        newEntry.stickers = stickers;
        newEntry.editStickers = editStickers;
        newEntry.filterFile = filterFile;
        newEntry.filterState = filterState;
        newEntry.thumbBitmap = thumbBitmap;
        newEntry.fromCamera = fromCamera;
        newEntry.thumbPathBitmap = thumbPathBitmap;
        return newEntry;
    }
}
