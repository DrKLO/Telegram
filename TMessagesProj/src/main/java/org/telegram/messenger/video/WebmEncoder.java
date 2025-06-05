package org.telegram.messenger.video;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.Paint.Views.EditTextOutline;
import org.telegram.ui.Components.Paint.Views.PaintTextOptionsView;
import org.telegram.ui.Components.RLottieDrawable;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class WebmEncoder {

    private static native long createEncoder(
        String outputPath,
        int width, int height,
        int fps, long bitrate
    );
    private static native boolean writeFrame(
        long ptr,
        ByteBuffer argbPixels,
        int width, int height
    );
    public static native void stop(long ptr);


    public static boolean convert(MediaCodecVideoConvertor.ConvertVideoParams params, int triesLeft) {
        final int W = params.resultWidth;
        final int H = params.resultHeight;

        final long maxFileSize = 255 * 1024;

        final long ptr = createEncoder(params.cacheFile.getAbsolutePath(), W, H, params.framerate, params.bitrate);
        if (ptr == 0) {
            return true;
        }

        boolean error = false;
        Bitmap bitmap = null;
        try {

            bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            ByteBuffer buffer = ByteBuffer.allocateDirect(bitmap.getByteCount());

            Canvas canvas = new Canvas(bitmap);
            FrameDrawer frameDrawer = new FrameDrawer(params);

            final int framesCount = (int) Math.ceil(params.framerate * (params.duration / 1000.0));
            for (int frame = 0; frame < framesCount; ++frame) {
                frameDrawer.draw(canvas, frame);

                bitmap.copyPixelsToBuffer(buffer);
                buffer.flip();

                if (!writeFrame(ptr, buffer, W, H)) {
                    FileLog.d("webm writeFile error at " + frame + "/" + framesCount);
                    return true;
                }

                if (params.callback != null) {
                    params.callback.didWriteData(Math.min(maxFileSize, params.cacheFile.length()), (float) frame / framesCount);
                }

                if (frame % 3 == 0 && params.callback != null) {
                    params.callback.checkConversionCanceled();
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            error = true;
        } finally {
            stop(ptr);
            if (bitmap != null) {
                bitmap.recycle();
            }
        }

        long fileSize = params.cacheFile.length();
        if (triesLeft > 0 && fileSize > maxFileSize) {
            int oldBitrate = params.bitrate;
            params.bitrate *= ((float) maxFileSize / fileSize) * .9f;
            params.cacheFile.delete();
            FileLog.d("webm encoded too much, got " + fileSize + ", old bitrate = " + oldBitrate + " new bitrate = " + params.bitrate);
            return convert(params, triesLeft - 1);
        }

        if (params.callback != null) {
            params.callback.didWriteData(fileSize, 1f);
        }

        FileLog.d("webm encoded to " + params.cacheFile + " with size=" + fileSize + " triesLeft=" + triesLeft);

        return error;
    }

    public static class FrameDrawer {

        private final int W, H;
        private final int fps;

        private final Bitmap photo;
        private final ArrayList<VideoEditedInfo.MediaEntity> mediaEntities = new ArrayList<>();

        private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

        private final Path clipPath;

        public FrameDrawer(MediaCodecVideoConvertor.ConvertVideoParams params) {
            this.W = params.resultWidth;
            this.H = params.resultHeight;
            this.fps = params.framerate;

            clipPath = new Path();
            RectF bounds = new RectF(0, 0, W, H);
            clipPath.addRoundRect(bounds, W * .125f, H * .125f, Path.Direction.CW);

            photo = BitmapFactory.decodeFile(params.videoPath);

            mediaEntities.addAll(params.mediaEntities);
            for (int a = 0, N = mediaEntities.size(); a < N; a++) {
                VideoEditedInfo.MediaEntity entity = mediaEntities.get(a);
                if (
                    entity.type == VideoEditedInfo.MediaEntity.TYPE_STICKER ||
                    entity.type == VideoEditedInfo.MediaEntity.TYPE_PHOTO ||
                    entity.type == VideoEditedInfo.MediaEntity.TYPE_ROUND
                ) {
                    initStickerEntity(entity);
                } else if (entity.type == VideoEditedInfo.MediaEntity.TYPE_TEXT) {
                    initTextEntity(entity);
                }
            }

            clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        public void draw(Canvas canvas, int frame) {
            canvas.drawPaint(clearPaint);
            canvas.save();
            canvas.clipPath(clipPath);
            if (photo != null) {
                canvas.drawBitmap(photo, 0, 0, null);
            }
            final long time = frame * (1_000_000_000L / fps);
            for (int a = 0, N = mediaEntities.size(); a < N; a++) {
                VideoEditedInfo.MediaEntity entity = mediaEntities.get(a);
                drawEntity(canvas, entity, entity.color, time);
            }
            canvas.restore();
        }

        private void drawEntity(Canvas canvas, VideoEditedInfo.MediaEntity entity, int textColor, long time) {
            if (entity.ptr != 0) {
                if (entity.bitmap == null || entity.W <= 0 || entity.H <= 0) {
                    return;
                }
                RLottieDrawable.getFrame(entity.ptr, (int) entity.currentFrame, entity.bitmap, entity.W, entity.H, entity.bitmap.getRowBytes(), true);
                applyRoundRadius(entity, entity.bitmap, (entity.subType & 8) != 0 ? textColor : 0);

                canvas.drawBitmap(entity.bitmap, entity.matrix, bitmapPaint);

                entity.currentFrame += entity.framesPerDraw;
                if (entity.currentFrame >= entity.metadata[0]) {
                    entity.currentFrame = 0;
                }
            } else if (entity.animatedFileDrawable != null) {
                int lastFrame = (int) entity.currentFrame;
                float scale = 1f;
                entity.currentFrame += entity.framesPerDraw;
                int currentFrame = (int) entity.currentFrame;
                while (lastFrame != currentFrame) {
                    entity.animatedFileDrawable.getNextFrame(true);
                    currentFrame--;
                }
                Bitmap frameBitmap = entity.animatedFileDrawable.getBackgroundBitmap();
                if (frameBitmap != null) {
                    canvas.drawBitmap(frameBitmap, entity.matrix, bitmapPaint);
                }
            } else {
                canvas.drawBitmap(entity.bitmap, entity.matrix, bitmapPaint);
                if (entity.entities != null && !entity.entities.isEmpty()) {
                    for (int i = 0; i < entity.entities.size(); ++i) {
                        VideoEditedInfo.EmojiEntity e = entity.entities.get(i);
                        if (e == null) {
                            continue;
                        }
                        VideoEditedInfo.MediaEntity entity1 = e.entity;
                        if (entity1 == null) {
                            continue;
                        }
                        drawEntity(canvas, entity1, entity.color, time);
                    }
                }
            }
        }

        private void initTextEntity(VideoEditedInfo.MediaEntity entity) {
            EditTextOutline editText = new EditTextOutline(ApplicationLoader.applicationContext);
            editText.getPaint().setAntiAlias(true);
            editText.drawAnimatedEmojiDrawables = false;
            editText.setBackgroundColor(Color.TRANSPARENT);
            editText.setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7));
            Typeface typeface;
            if (entity.textTypeface != null && (typeface = entity.textTypeface.getTypeface()) != null) {
                editText.setTypeface(typeface);
            }
            editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, entity.fontSize);
            CharSequence text = new SpannableString(entity.text);
            for (VideoEditedInfo.EmojiEntity e : entity.entities) {
                if (e.documentAbsolutePath == null) {
                    continue;
                }
                e.entity = new VideoEditedInfo.MediaEntity();
                e.entity.text = e.documentAbsolutePath;
                e.entity.subType = e.subType;
                AnimatedEmojiSpan span = new AnimatedEmojiSpan(0L, 1f, editText.getPaint().getFontMetricsInt()) {
                    @Override
                    public void draw(@NonNull Canvas canvas, CharSequence charSequence, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
                        super.draw(canvas, charSequence, start, end, x, top, y, bottom, paint);

                        float tcx = entity.x + (editText.getPaddingLeft() + x + measuredSize / 2f) / entity.viewWidth * entity.width;
                        float tcy = entity.y + (editText.getPaddingTop() + top + (bottom - top) / 2f) / entity.viewHeight * entity.height;

                        if (entity.rotation != 0) {
                            float mx = entity.x + entity.width / 2f;
                            float my = entity.y + entity.height / 2f;
                            float ratio = W / (float) H;
                            float x1 = tcx - mx;
                            float y1 = (tcy - my) / ratio;
                            tcx = (float) (x1 * Math.cos(-entity.rotation) - y1 * Math.sin(-entity.rotation)) + mx;
                            tcy = (float) (x1 * Math.sin(-entity.rotation) + y1 * Math.cos(-entity.rotation)) * ratio + my;
                        }

                        e.entity.width =  (float) measuredSize / entity.viewWidth * entity.width;
                        e.entity.height = (float) measuredSize / entity.viewHeight * entity.height;
                        e.entity.x = tcx - e.entity.width / 2f;
                        e.entity.y = tcy - e.entity.height / 2f;
                        e.entity.rotation = entity.rotation;

                        if (e.entity.bitmap == null)
                            initStickerEntity(e.entity);
                    }
                };
                ((Spannable) text).setSpan(span, e.offset, e.offset + e.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            text = Emoji.replaceEmoji(text, editText.getPaint().getFontMetricsInt(), false);
            if (text instanceof Spanned) {
                Emoji.EmojiSpan[] spans = ((Spanned) text).getSpans(0, text.length(), Emoji.EmojiSpan.class);
                if (spans != null) {
                    for (int i = 0; i < spans.length; ++i) {
                        spans[i].scale = .85f;
                    }
                }
            }
            editText.setText(text);
            editText.setTextColor(entity.color);


            int gravity;
            switch (entity.textAlign) {
                default:
                case PaintTextOptionsView.ALIGN_LEFT:
                    gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
                    break;
                case PaintTextOptionsView.ALIGN_CENTER:
                    gravity = Gravity.CENTER;
                    break;
                case PaintTextOptionsView.ALIGN_RIGHT:
                    gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
                    break;
            }

            editText.setGravity(gravity);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                int textAlign;
                switch (entity.textAlign) {
                    default:
                    case PaintTextOptionsView.ALIGN_LEFT:
                        textAlign = LocaleController.isRTL ? View.TEXT_ALIGNMENT_TEXT_END : View.TEXT_ALIGNMENT_TEXT_START;
                        break;
                    case PaintTextOptionsView.ALIGN_CENTER:
                        textAlign = View.TEXT_ALIGNMENT_CENTER;
                        break;
                    case PaintTextOptionsView.ALIGN_RIGHT:
                        textAlign = LocaleController.isRTL ? View.TEXT_ALIGNMENT_TEXT_START : View.TEXT_ALIGNMENT_TEXT_END;
                        break;
                }
                editText.setTextAlignment(textAlign);
            }

            editText.setHorizontallyScrolling(false);
            editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            editText.setFocusableInTouchMode(true);
            editText.setInputType(editText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
            if (Build.VERSION.SDK_INT >= 23) {
                setBreakStrategy(editText);
            }
            if (entity.subType == 0) {
                editText.setFrameColor(entity.color);
                editText.setTextColor(AndroidUtilities.computePerceivedBrightness(entity.color) >= .721f ? Color.BLACK : Color.WHITE);
            } else if (entity.subType == 1) {
                editText.setFrameColor(AndroidUtilities.computePerceivedBrightness(entity.color) >= .25f ? 0x99000000 : 0x99ffffff);
                editText.setTextColor(entity.color);
            } else if (entity.subType == 2) {
                editText.setFrameColor(AndroidUtilities.computePerceivedBrightness(entity.color) >= .25f ? Color.BLACK : Color.WHITE);
                editText.setTextColor(entity.color);
            } else if (entity.subType == 3) {
                editText.setFrameColor(0);
                editText.setTextColor(entity.color);
            }

            editText.measure(View.MeasureSpec.makeMeasureSpec(entity.viewWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(entity.viewHeight, View.MeasureSpec.EXACTLY));
            editText.layout(0, 0, entity.viewWidth, entity.viewHeight);
            entity.bitmap = Bitmap.createBitmap(entity.viewWidth, entity.viewHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(entity.bitmap);
            editText.draw(canvas);

            setupMatrix(entity);
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        public void setBreakStrategy(EditTextOutline editText) {
            editText.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
        }

        private void initStickerEntity(VideoEditedInfo.MediaEntity entity) {
            entity.W = (int) (entity.width * W);
            entity.H = (int) (entity.height * H);
            if (entity.W > 512) {
                entity.H = (int) (entity.H / (float) entity.W * 512);
                entity.W = 512;
            }
            if (entity.H > 512) {
                entity.W = (int) (entity.W / (float) entity.H * 512);
                entity.H = 512;
            }
            if ((entity.subType & 1) != 0) {
                if (entity.W <= 0 || entity.H <= 0) {
                    return;
                }
                entity.bitmap = Bitmap.createBitmap(entity.W, entity.H, Bitmap.Config.ARGB_8888);
                entity.metadata = new int[3];
                entity.ptr = RLottieDrawable.create(entity.text, null, entity.W, entity.H, entity.metadata, false, null, false, 0);
                entity.framesPerDraw = (float) entity.metadata[1] / fps;
            } else if ((entity.subType & 4) != 0) {
                entity.looped = false;
                entity.animatedFileDrawable = new AnimatedFileDrawable(new File(entity.text), true, 0, 0, null, null, null, 0, UserConfig.selectedAccount, true, 512, 512, null);
                entity.framesPerDraw = (float) entity.animatedFileDrawable.getFps() / fps;
                entity.currentFrame = 1;
                entity.animatedFileDrawable.getNextFrame(true);
                if (entity.type == VideoEditedInfo.MediaEntity.TYPE_ROUND) {
                    entity.firstSeek = true;
                }
            } else {
                String path = entity.text;
                if (!TextUtils.isEmpty(entity.segmentedPath) && (entity.subType & 16) != 0) {
                    path = entity.segmentedPath;
                }
                BitmapFactory.Options opts = new BitmapFactory.Options();
                if (entity.type == VideoEditedInfo.MediaEntity.TYPE_PHOTO) {
                    opts.inMutable = true;
                }
                entity.bitmap = BitmapFactory.decodeFile(path, opts);
                if (entity.type == VideoEditedInfo.MediaEntity.TYPE_PHOTO && entity.bitmap != null) {
                    entity.roundRadius = AndroidUtilities.dp(12) / (float) Math.min(entity.viewWidth, entity.viewHeight);
                    Pair<Integer, Integer> orientation = AndroidUtilities.getImageOrientation(entity.text);
                    entity.rotation -= Math.toRadians(orientation.first);
                    if ((orientation.first / 90 % 2) == 1) {
                        float cx = entity.x + entity.width / 2f, cy = entity.y + entity.height / 2f;

                        float w = entity.width * W / H;
                        entity.width = entity.height * H / W;
                        entity.height = w;

                        entity.x = cx - entity.width / 2f;
                        entity.y = cy - entity.height / 2f;
                    }
                    applyRoundRadius(entity, entity.bitmap, 0);
                } else if (entity.bitmap != null) {
                    float aspect = entity.bitmap.getWidth() / (float) entity.bitmap.getHeight();
                    if (aspect > 1) {
                        float h = entity.height / aspect;
                        entity.y += (entity.height - h) / 2;
                        entity.height = h;
                    } else if (aspect < 1) {
                        float w = entity.width * aspect;
                        entity.x += (entity.width - w) / 2;
                        entity.width = w;
                    }
                }
            }

            setupMatrix(entity);
        }

        private void setupMatrix(VideoEditedInfo.MediaEntity entity) {
            entity.matrix = new Matrix();
            Bitmap bitmap = entity.bitmap;
            if (bitmap == null && entity.animatedFileDrawable != null) {
                bitmap = entity.animatedFileDrawable.getBackgroundBitmap();
            }
            if (bitmap != null) {
                entity.matrix.postScale(1f / bitmap.getWidth(), 1f / bitmap.getHeight());
            }
            if (entity.type != VideoEditedInfo.MediaEntity.TYPE_TEXT && (entity.subType & 2) != 0) {
                entity.matrix.postScale(-1, 1, .5f, .5f);
            }
            entity.matrix.postScale(entity.width * W, entity.height * H);
            entity.matrix.postTranslate(entity.x * W, entity.y * H);
            entity.matrix.postRotate((float) (-entity.rotation / Math.PI * 180), (entity.x + entity.width / 2f) * W, (entity.y + entity.height / 2f) * H);
        }

        Path path;
        Paint xRefPaint;
        Paint textColorPaint;
        private void applyRoundRadius(VideoEditedInfo.MediaEntity entity, Bitmap stickerBitmap, int color) {
            if (stickerBitmap == null || entity == null || entity.roundRadius == 0 && color == 0) {
                return;
            }
            if (entity.roundRadiusCanvas == null) {
                entity.roundRadiusCanvas = new Canvas(stickerBitmap);
            }
            if (entity.roundRadius != 0) {
                if (path == null) {
                    path = new Path();
                }
                if (xRefPaint == null) {
                    xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    xRefPaint.setColor(0xff000000);
                    xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                }
                float rad = Math.min(stickerBitmap.getWidth(), stickerBitmap.getHeight()) * entity.roundRadius;
                path.rewind();
                RectF rect = new RectF(0, 0, stickerBitmap.getWidth(), stickerBitmap.getHeight());
                path.addRoundRect(rect, rad, rad, Path.Direction.CCW);
                path.toggleInverseFillType();
                entity.roundRadiusCanvas.drawPath(path, xRefPaint);
            }
            if (color != 0) {
                if (textColorPaint == null) {
                    textColorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    textColorPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                }
                textColorPaint.setColor(color);
                entity.roundRadiusCanvas.drawRect(0, 0, stickerBitmap.getWidth(), stickerBitmap.getHeight(), textColorPaint);
            }
        }
    }

}
