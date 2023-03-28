/*
Modified by Nikolai Kudashov
Changed drawing to Bitmap instead of Picture
Added styles support
Fixed some float parsing issues
Removed gradients support

Copyright Larva Labs, LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.telegram.messenger;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.SparseArray;

import androidx.core.graphics.ColorUtils;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.DrawingInBackgroundThreadDrawable;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class SvgHelper {

    private static class Line {
        float x1, y1, x2, y2;

        public Line(float x1, float y1, float x2, float y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    public static class Circle {
        float x1, y1, rad;

        public Circle(float x1, float y1, float rad) {
            this.x1 = x1;
            this.y1 = y1;
            this.rad = rad;
        }
    }

    private static class Oval {
        RectF rect;

        public Oval(RectF rect) {
            this.rect = rect;
        }
    }

    private static class RoundRect {
        RectF rect;
        float rx;
        public RoundRect(RectF rect, float rx) {
            this.rect = rect;
            this.rx = rx;
        }
    }

    public static class SvgDrawable extends Drawable {

        protected ArrayList<Object> commands = new ArrayList<>();
        protected HashMap<Object, Paint> paints = new HashMap<>();
        private Paint overridePaint;
        private Paint backgroundPaint;
        protected int width;
        protected int height;
        private static int[] parentPosition = new int[2];

        private Bitmap[] backgroundBitmap = new Bitmap[1 + DrawingInBackgroundThreadDrawable.THREAD_COUNT];
        private Canvas[] backgroundCanvas = new Canvas[1 + DrawingInBackgroundThreadDrawable.THREAD_COUNT];
        private LinearGradient[] placeholderGradient = new LinearGradient[1 + DrawingInBackgroundThreadDrawable.THREAD_COUNT];
        private Matrix[] placeholderMatrix = new Matrix[1 + DrawingInBackgroundThreadDrawable.THREAD_COUNT];
        private static float totalTranslation;
        private static float gradientWidth;
        private static long lastUpdateTime;
        private static Runnable shiftRunnable;
        private static WeakReference<Drawable> shiftDrawable;
        private ImageReceiver parentImageReceiver;
        private int[] currentColor = new int[2];
        private String currentColorKey;
        private Integer overrideColor;
        private Theme.ResourcesProvider currentResourcesProvider;
        private float colorAlpha;
        private float crossfadeAlpha = 1.0f;
        SparseArray<Paint> overridePaintByPosition = new SparseArray<>();

        private boolean aspectFill = true;
        private boolean aspectCenter = false;

        @Override
        public int getIntrinsicHeight() {
            return width;
        }

        @Override
        public int getIntrinsicWidth() {
            return height;
        }

        public void setAspectFill(boolean value) {
            aspectFill = value;
        }

        public void setAspectCenter(boolean value) {
            aspectCenter = value;
        }

        public void overrideWidthAndHeight(int w, int h) {
            width = w;
            height = h;
        }

        @Override
        public void draw(Canvas canvas) {
            drawInternal(canvas, false, 0, System.currentTimeMillis(), getBounds().left, getBounds().top, getBounds().width(), getBounds().height());
        }

        public void drawInternal(Canvas canvas, boolean drawInBackground, int threadIndex, long time, float x, float y, float w, float h) {
            if (currentColorKey != null) {
                setupGradient(currentColorKey, currentResourcesProvider, colorAlpha, drawInBackground);
            }

            float scale = getScale((int) w, (int) h);
            if (placeholderGradient[threadIndex] != null && gradientWidth > 0 && LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND)) {
                if (drawInBackground) {
                    long dt = time - lastUpdateTime;
                    if (dt > 64) {
                        dt = 64;
                    }
                    if (dt > 0) {
                        lastUpdateTime = time;
                        totalTranslation += dt * gradientWidth / 1800.0f;
                        while (totalTranslation >= gradientWidth * 2) {
                            totalTranslation -= gradientWidth * 2;
                        }
                    }
                } else {
                    if (shiftRunnable == null || shiftDrawable.get() == this) {
                        long dt = time - lastUpdateTime;
                        if (dt > 64) {
                            dt = 64;
                        }
                        if (dt < 0) {
                            dt = 0;
                        }
                        lastUpdateTime = time;
                        totalTranslation += dt * gradientWidth / 1800.0f;
                        while (totalTranslation >= gradientWidth / 2) {
                            totalTranslation -= gradientWidth;
                        }
                        shiftDrawable = new WeakReference<>(this);
                        if (shiftRunnable != null) {
                            AndroidUtilities.cancelRunOnUIThread(shiftRunnable);
                        }
                        AndroidUtilities.runOnUIThread(shiftRunnable = () -> shiftRunnable = null, (int) (1000 / AndroidUtilities.screenRefreshRate) - 1);
                    }
                }
                int offset;
                if (parentImageReceiver != null && !drawInBackground) {
                    parentImageReceiver.getParentPosition(parentPosition);
                    offset = parentPosition[0];
                } else {
                    offset = 0;
                }

                int index = drawInBackground ? 1 + threadIndex : 0;
                if (placeholderMatrix[index] != null) {
                    placeholderMatrix[index].reset();
                    if (drawInBackground) {
                        placeholderMatrix[index].postTranslate(-offset + totalTranslation - x, 0);
                    } else {
                        placeholderMatrix[index].postTranslate(-offset + totalTranslation - x, 0);
                    }

                    placeholderMatrix[index].postScale(1.0f / scale, 1.0f / scale);
                    placeholderGradient[index].setLocalMatrix(placeholderMatrix[index]);

                    if (parentImageReceiver != null && !drawInBackground) {
                        parentImageReceiver.invalidate();
                    }
                }
            }

            canvas.save();
            canvas.translate(x, y);
            if (!aspectFill || aspectCenter) {
                canvas.translate((w - width * scale) / 2, (h - height * scale) / 2);
            }
            canvas.scale(scale, scale);
            for (int a = 0, N = commands.size(); a < N; a++) {
                Object object = commands.get(a);
                if (object instanceof Matrix) {
                    canvas.save();
                    canvas.concat((Matrix) object);
                } else if (object == null) {
                    canvas.restore();
                } else {
                    Paint paint;
                    Paint overridePaint = overridePaintByPosition.get(a);
                    if (overridePaint == null) {
                        overridePaint = this.overridePaint;
                    }
                    if (drawInBackground) {
                        paint = backgroundPaint;
                    } else if (overridePaint != null) {
                        paint = overridePaint;
                    } else {
                        paint = paints.get(object);
                    }
                    int originalAlpha = paint.getAlpha();
                    paint.setAlpha((int) (crossfadeAlpha * originalAlpha));
                    if (object instanceof Path) {
                        canvas.drawPath((Path) object, paint);
                    } else if (object instanceof Rect) {
                        canvas.drawRect((Rect) object, paint);
                    } else if (object instanceof RectF) {
                        canvas.drawRect((RectF) object, paint);
                    } else if (object instanceof Line) {
                        Line line = (Line) object;
                        canvas.drawLine(line.x1, line.y1, line.x2, line.y2, paint);
                    } else if (object instanceof Circle) {
                        Circle circle = (Circle) object;
                        canvas.drawCircle(circle.x1, circle.y1, circle.rad, paint);
                    } else if (object instanceof Oval) {
                        Oval oval = (Oval) object;
                        canvas.drawOval(oval.rect, paint);
                    } else if (object instanceof RoundRect) {
                        RoundRect rect = (RoundRect) object;
                        canvas.drawRoundRect(rect.rect, rect.rx, rect.rx, paint);
                    }
                    paint.setAlpha(originalAlpha);
                }
            }
            canvas.restore();
        }

        public float getScale(int viewWidth, int viewHeight) {
            float scaleX = viewWidth / (float) width;
            float scaleY = viewHeight / (float) height;
            return aspectFill ? Math.max(scaleX, scaleY) : Math.min(scaleX, scaleY);
        }

        @Override
        public void setAlpha(int alpha) {
            crossfadeAlpha = alpha / 255.0f;
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        private void addCommand(Object command, Paint paint) {
            commands.add(command);
            paints.put(command, new Paint(paint));
        }

        private void addCommand(Object command) {
            commands.add(command);
        }

        public void setParent(ImageReceiver imageReceiver) {
            parentImageReceiver = imageReceiver;
        }

        public void setupGradient(String colorKey, float alpha, boolean drawInBackground) {
            setupGradient(colorKey, null, alpha, drawInBackground);
        }

        public void setupGradient(String colorKey, Theme.ResourcesProvider resourcesProvider, float alpha, boolean drawInBackground) {
            int color = overrideColor == null ? Theme.getColor(colorKey, resourcesProvider) : overrideColor;
            int index = drawInBackground ? 1 : 0;
            currentResourcesProvider = resourcesProvider;
            if (currentColor[index] != color) {
                colorAlpha = alpha;
                currentColorKey = colorKey;
                currentColor[index] = color;
                gradientWidth = AndroidUtilities.displaySize.x * 2;
                if (!LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND)) {
                    int color2 = ColorUtils.setAlphaComponent(currentColor[index], 70);
                    if (drawInBackground) {
                        if (backgroundPaint == null) {
                            backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        }
                        backgroundPaint.setShader(null);
                        backgroundPaint.setColor(color2);
                    } else {
                        for (Paint paint : paints.values()) {
                            paint.setShader(null);
                            paint.setColor(color2);
                        }
                    }
                    return;
                }
                float w = AndroidUtilities.dp(180) / gradientWidth;
                color = Color.argb((int) (Color.alpha(color) / 2 * colorAlpha), Color.red(color), Color.green(color), Color.blue(color));
                float centerX = (1.0f - w) / 2;
                placeholderGradient[index] = new LinearGradient(0, 0, gradientWidth, 0, new int[]{0x00000000, 0x00000000, color, 0x00000000, 0x00000000}, new float[]{0.0f, centerX - w / 2.0f, centerX, centerX + w / 2.0f, 1.0f}, Shader.TileMode.REPEAT);
                Shader backgroundGradient;
                if (Build.VERSION.SDK_INT >= 28) {
                    backgroundGradient = new LinearGradient(0, 0, gradientWidth, 0, new int[]{color, color}, null, Shader.TileMode.REPEAT);
                } else {
                    if (backgroundBitmap[index] == null) {
                        backgroundBitmap[index] = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                        backgroundCanvas[index] = new Canvas(backgroundBitmap[index]);
                    }
                    backgroundCanvas[index].drawColor(color);
                    backgroundGradient = new BitmapShader(backgroundBitmap[index], Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                }
                placeholderMatrix[index] = new Matrix();
                placeholderGradient[index].setLocalMatrix(placeholderMatrix[index]);
                if (drawInBackground) {
                    if (backgroundPaint == null) {
                        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    }
                    if (Build.VERSION.SDK_INT <= 22) {
                        backgroundPaint.setShader(backgroundGradient);
                    } else {
                        backgroundPaint.setShader(new ComposeShader(placeholderGradient[index], backgroundGradient, PorterDuff.Mode.ADD));
                    }
                } else {
                    for (Paint paint : paints.values()) {
                        if (Build.VERSION.SDK_INT <= 22) {
                            paint.setShader(backgroundGradient);
                        } else {
                            paint.setShader(new ComposeShader(placeholderGradient[index], backgroundGradient, PorterDuff.Mode.ADD));
                        }
                    }
                }
            }
        }

        public void setColorKey(String colorKey) {
            currentColorKey = colorKey;
        }

        public void setColorKey(String colorKey, Theme.ResourcesProvider resourcesProvider) {
            currentColorKey = colorKey;
            currentResourcesProvider = resourcesProvider;
        }

        public void setColor(int color) {
            overrideColor = color;
        }

        public void setPaint(Paint paint) {
            overridePaint = paint;
        }

        public void setPaint(Paint paint, int position) {
            overridePaintByPosition.put(position, paint);
        }

        public void copyCommandFromPosition(int position) {
            commands.add(commands.get(position));
        }
    }

    public static Bitmap getBitmap(int res, int width, int height, int color) {
        return getBitmap(res, width, height, color, 1f);
    }

    public static Bitmap getBitmap(int res, int width, int height, int color, float scale) {
        try (InputStream stream = ApplicationLoader.applicationContext.getResources().openRawResource(res)) {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            SAXParser sp = spf.newSAXParser();
            XMLReader xr = sp.getXMLReader();
            SVGHandler handler = new SVGHandler(width, height, color, false, scale);
            xr.setContentHandler(handler);
            xr.parse(new InputSource(stream));
            return handler.getBitmap();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static Bitmap getBitmap(File file, int width, int height, boolean white) {
        try (FileInputStream stream = new FileInputStream(file)) {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            SAXParser sp = spf.newSAXParser();
            XMLReader xr = sp.getXMLReader();
            SVGHandler handler = new SVGHandler(width, height, white ? 0xffffffff : null, false, 1f);
            xr.setContentHandler(handler);
            xr.parse(new InputSource(stream));
            return handler.getBitmap();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static Bitmap getBitmap(String xml, int width, int height, boolean white) {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            SAXParser sp = spf.newSAXParser();
            XMLReader xr = sp.getXMLReader();
            SVGHandler handler = new SVGHandler(width, height, white ? 0xffffffff : null, false, 1f);
            xr.setContentHandler(handler);
            xr.parse(new InputSource(new StringReader(xml)));
            return handler.getBitmap();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static SvgDrawable getDrawable(String xml) {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            SAXParser sp = spf.newSAXParser();
            XMLReader xr = sp.getXMLReader();
            SVGHandler handler = new SVGHandler(0, 0, null, true, 1f);
            xr.setContentHandler(handler);
            xr.parse(new InputSource(new StringReader(xml)));
            return handler.getDrawable();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static SvgDrawable getDrawable(int resId, Integer color) {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            SAXParser sp = spf.newSAXParser();
            XMLReader xr = sp.getXMLReader();
            SVGHandler handler = new SVGHandler(0, 0, color, true, 1f);
            xr.setContentHandler(handler);
            xr.parse(new InputSource(ApplicationLoader.applicationContext.getResources().openRawResource(resId)));
            return handler.getDrawable();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static SvgDrawable getDrawableByPath(String pathString, int w, int h) {
        try {
            Path path = doPath(pathString);
            SvgDrawable drawable = new SvgDrawable();
            drawable.commands.add(path);
            drawable.paints.put(path, new Paint(Paint.ANTI_ALIAS_FLAG));
            drawable.width = w;
            drawable.height = h;
            return drawable;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static SvgDrawable getDrawableByPath(Path path, int w, int h) {
        try {
            SvgDrawable drawable = new SvgDrawable();
            drawable.commands.add(path);
            drawable.paints.put(path, new Paint(Paint.ANTI_ALIAS_FLAG));
            drawable.width = w;
            drawable.height = h;
            return drawable;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static Bitmap getBitmapByPathOnly(String pathString, int svgWidth, int svgHeight, int width, int height) {
        try {
            Path path = doPath(pathString);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(width / (float) svgWidth, height / (float) svgHeight);
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            canvas.drawPath(path,paint);
            return bitmap;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static NumberParse parseNumbers(String s) {
        int n = s.length();
        int p = 0;
        ArrayList<Float> numbers = new ArrayList<>();
        boolean skipChar = false;
        for (int i = 1; i < n; i++) {
            if (skipChar) {
                skipChar = false;
                continue;
            }
            char c = s.charAt(i);
            switch (c) {
                case 'M':
                case 'm':
                case 'Z':
                case 'z':
                case 'L':
                case 'l':
                case 'H':
                case 'h':
                case 'V':
                case 'v':
                case 'C':
                case 'c':
                case 'S':
                case 's':
                case 'Q':
                case 'q':
                case 'T':
                case 't':
                case 'a':
                case 'A':
                case ')': {
                    String str = s.substring(p, i);
                    if (str.trim().length() > 0) {
                        Float f = Float.parseFloat(str);
                        numbers.add(f);
                    }
                    p = i;
                    return new NumberParse(numbers, p);
                }
                case '\n':
                case '\t':
                case ' ':
                case ',':
                case '-': {
                    if (c == '-' && s.charAt(i - 1) == 'e') {
                        continue;
                    }
                    String str = s.substring(p, i);
                    if (str.trim().length() > 0) {
                        Float f = Float.parseFloat(str);
                        numbers.add(f);
                        if (c == '-') {
                            p = i;
                        } else {
                            p = i + 1;
                            skipChar = true;
                        }
                    } else {
                        p++;
                    }
                    break;
                }
            }
        }
        String last = s.substring(p);
        if (last.length() > 0) {
            try {
                numbers.add(Float.parseFloat(last));
            } catch (NumberFormatException ignore) {

            }
            p = s.length();
        }
        return new NumberParse(numbers, p);
    }

    private static Matrix parseTransform(String s) {
        if (s.startsWith("matrix(")) {
            NumberParse np = parseNumbers(s.substring("matrix(".length()));
            if (np.numbers.size() == 6) {
                Matrix matrix = new Matrix();
                matrix.setValues(new float[]{
                        np.numbers.get(0), np.numbers.get(2), np.numbers.get(4),
                        np.numbers.get(1), np.numbers.get(3), np.numbers.get(5),
                        0, 0, 1,
                });
                return matrix;
            }
        } else if (s.startsWith("translate(")) {
            NumberParse np = parseNumbers(s.substring("translate(".length()));
            if (np.numbers.size() > 0) {
                float tx = np.numbers.get(0);
                float ty = 0;
                if (np.numbers.size() > 1) {
                    ty = np.numbers.get(1);
                }
                Matrix matrix = new Matrix();
                matrix.postTranslate(tx, ty);
                return matrix;
            }
        } else if (s.startsWith("scale(")) {
            NumberParse np = parseNumbers(s.substring("scale(".length()));
            if (np.numbers.size() > 0) {
                float sx = np.numbers.get(0);
                float sy = 0;
                if (np.numbers.size() > 1) {
                    sy = np.numbers.get(1);
                }
                Matrix matrix = new Matrix();
                matrix.postScale(sx, sy);
                return matrix;
            }
        } else if (s.startsWith("skewX(")) {
            NumberParse np = parseNumbers(s.substring("skewX(".length()));
            if (np.numbers.size() > 0) {
                float angle = np.numbers.get(0);
                Matrix matrix = new Matrix();
                matrix.postSkew((float) Math.tan(angle), 0);
                return matrix;
            }
        } else if (s.startsWith("skewY(")) {
            NumberParse np = parseNumbers(s.substring("skewY(".length()));
            if (np.numbers.size() > 0) {
                float angle = np.numbers.get(0);
                Matrix matrix = new Matrix();
                matrix.postSkew(0, (float) Math.tan(angle));
                return matrix;
            }
        } else if (s.startsWith("rotate(")) {
            NumberParse np = parseNumbers(s.substring("rotate(".length()));
            if (np.numbers.size() > 0) {
                float angle = np.numbers.get(0);
                float cx = 0;
                float cy = 0;
                if (np.numbers.size() > 2) {
                    cx = np.numbers.get(1);
                    cy = np.numbers.get(2);
                }
                Matrix matrix = new Matrix();
                matrix.postTranslate(cx, cy);
                matrix.postRotate(angle);
                matrix.postTranslate(-cx, -cy);
                return matrix;
            }
        }
        return null;
    }

    public static Path doPath(String s) {
        int n = s.length();
        ParserHelper ph = new ParserHelper(s, 0);
        ph.skipWhitespace();
        Path p = new Path();
        float lastX = 0;
        float lastY = 0;
        float lastX1 = 0;
        float lastY1 = 0;
        float subPathStartX = 0;
        float subPathStartY = 0;
        char prevCmd = 0;
        while (ph.pos < n) {
            char cmd = s.charAt(ph.pos);
            switch (cmd) {
                case '-':
                case '+':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    if (prevCmd == 'm' || prevCmd == 'M') {
                        cmd = (char) (((int) prevCmd) - 1);
                        break;
                    } else if (prevCmd == 'c' || prevCmd == 'C') {
                        cmd = prevCmd;
                        break;
                    } else if (prevCmd == 'l' || prevCmd == 'L') {
                        cmd = prevCmd;
                        break;
                    } else if (prevCmd == 's' || prevCmd == 'S') {
                        cmd = prevCmd;
                        break;
                    } else if (prevCmd == 'h' || prevCmd == 'H') {
                        cmd = prevCmd;
                        break;
                    } else if (prevCmd == 'v' || prevCmd == 'V') {
                        cmd = prevCmd;
                        break;
                    }
                default: {
                    ph.advance();
                    prevCmd = cmd;
                }
            }

            boolean wasCurve = false;
            switch (cmd) {
                case 'M':
                case 'm': {
                    float x = ph.nextFloat();
                    float y = ph.nextFloat();
                    if (cmd == 'm') {
                        subPathStartX += x;
                        subPathStartY += y;
                        p.rMoveTo(x, y);
                        lastX += x;
                        lastY += y;
                    } else {
                        subPathStartX = x;
                        subPathStartY = y;
                        p.moveTo(x, y);
                        lastX = x;
                        lastY = y;
                    }
                    break;
                }
                case 'Z':
                case 'z': {
                    p.close();
                    p.moveTo(subPathStartX, subPathStartY);
                    lastX = subPathStartX;
                    lastY = subPathStartY;
                    lastX1 = subPathStartX;
                    lastY1 = subPathStartY;
                    wasCurve = true;
                    break;
                }
                case 'L':
                case 'l': {
                    float x = ph.nextFloat();
                    float y = ph.nextFloat();
                    if (cmd == 'l') {
                        p.rLineTo(x, y);
                        lastX += x;
                        lastY += y;
                    } else {
                        p.lineTo(x, y);
                        lastX = x;
                        lastY = y;
                    }
                    break;
                }
                case 'H':
                case 'h': {
                    float x = ph.nextFloat();
                    if (cmd == 'h') {
                        p.rLineTo(x, 0);
                        lastX += x;
                    } else {
                        p.lineTo(x, lastY);
                        lastX = x;
                    }
                    break;
                }
                case 'V':
                case 'v': {
                    float y = ph.nextFloat();
                    if (cmd == 'v') {
                        p.rLineTo(0, y);
                        lastY += y;
                    } else {
                        p.lineTo(lastX, y);
                        lastY = y;
                    }
                    break;
                }
                case 'C':
                case 'c': {
                    wasCurve = true;
                    float x1 = ph.nextFloat();
                    float y1 = ph.nextFloat();
                    float x2 = ph.nextFloat();
                    float y2 = ph.nextFloat();
                    float x = ph.nextFloat();
                    float y = ph.nextFloat();
                    if (cmd == 'c') {
                        x1 += lastX;
                        x2 += lastX;
                        x += lastX;
                        y1 += lastY;
                        y2 += lastY;
                        y += lastY;
                    }
                    p.cubicTo(x1, y1, x2, y2, x, y);
                    lastX1 = x2;
                    lastY1 = y2;
                    lastX = x;
                    lastY = y;
                    break;
                }
                case 'S':
                case 's': {
                    wasCurve = true;
                    float x2 = ph.nextFloat();
                    float y2 = ph.nextFloat();
                    float x = ph.nextFloat();
                    float y = ph.nextFloat();
                    if (cmd == 's') {
                        x2 += lastX;
                        x += lastX;
                        y2 += lastY;
                        y += lastY;
                    }
                    float x1 = 2 * lastX - lastX1;
                    float y1 = 2 * lastY - lastY1;
                    p.cubicTo(x1, y1, x2, y2, x, y);
                    lastX1 = x2;
                    lastY1 = y2;
                    lastX = x;
                    lastY = y;
                    break;
                }
                case 'A':
                case 'a': {
                    float rx = ph.nextFloat();
                    float ry = ph.nextFloat();
                    float theta = ph.nextFloat();
                    int largeArc = (int) ph.nextFloat();
                    int sweepArc = (int) ph.nextFloat();
                    float x = ph.nextFloat();
                    float y = ph.nextFloat();
                    drawArc(p, lastX, lastY, x, y, rx, ry, theta, largeArc, sweepArc);
                    lastX = x;
                    lastY = y;
                    break;
                }
            }
            if (!wasCurve) {
                lastX1 = lastX;
                lastY1 = lastY;
            }
            ph.skipWhitespace();
        }
        return p;
    }

    private static void drawArc(Path p, float lastX, float lastY, float x, float y, float rx, float ry, float theta, int largeArc, int sweepArc) {
        // todo - not implemented yet, may be very hard to do using Android drawing facilities.
    }

    private static NumberParse getNumberParseAttr(String name, Attributes attributes) {
        int n = attributes.getLength();
        for (int i = 0; i < n; i++) {
            if (attributes.getLocalName(i).equals(name)) {
                return parseNumbers(attributes.getValue(i));
            }
        }
        return null;
    }

    private static String getStringAttr(String name, Attributes attributes) {
        int n = attributes.getLength();
        for (int i = 0; i < n; i++) {
            if (attributes.getLocalName(i).equals(name)) {
                return attributes.getValue(i);
            }
        }
        return null;
    }

    private static Float getFloatAttr(String name, Attributes attributes) {
        return getFloatAttr(name, attributes, null);
    }

    private static Float getFloatAttr(String name, Attributes attributes, Float defaultValue) {
        String v = getStringAttr(name, attributes);
        if (v == null) {
            return defaultValue;
        } else {
            if (v.endsWith("px")) {
                v = v.substring(0, v.length() - 2);
            } else if (v.endsWith("mm")) {
                return null;
            }
            return Float.parseFloat(v);
        }
    }

    private static Integer getHexAttr(String name, Attributes attributes) {
        String v = getStringAttr(name, attributes);
        if (v == null) {
            return null;
        } else {
            try {
                return Integer.parseInt(v.substring(1), 16);
            } catch (NumberFormatException nfe) {
                return getColorByName(v);
            }
        }
    }

    private static Integer getColorByName(String name) {
        switch (name.toLowerCase()) {
            case "black":
                return Color.BLACK;
            case "gray":
                return Color.GRAY;
            case "red":
                return Color.RED;
            case "green":
                return Color.GREEN;
            case "blue":
                return Color.BLUE;
            case "yellow":
                return Color.YELLOW;
            case "cyan":
                return Color.CYAN;
            case "magenta":
                return Color.MAGENTA;
            case "white":
                return Color.WHITE;
        }
        return null;
    }

    private static class NumberParse {
        private ArrayList<Float> numbers;
        private int nextCmd;

        public NumberParse(ArrayList<Float> numbers, int nextCmd) {
            this.numbers = numbers;
            this.nextCmd = nextCmd;
        }

        public int getNextCmd() {
            return nextCmd;
        }

        public float getNumber(int index) {
            return numbers.get(index);
        }

    }

    private static class StyleSet {
        HashMap<String, String> styleMap = new HashMap<>();

        private StyleSet(StyleSet styleSet) {
            styleMap.putAll(styleSet.styleMap);
        }

        private StyleSet(String string) {
            String[] styles = string.split(";");
            for (String s : styles) {
                String[] style = s.split(":");
                if (style.length == 2) {
                    styleMap.put(style[0].trim(), style[1].trim());
                }
            }
        }

        public String getStyle(String name) {
            return styleMap.get(name);
        }
    }

    private static class Properties {
        ArrayList<StyleSet> styles;
        Attributes atts;

        private Properties(Attributes atts, HashMap<String, StyleSet> globalStyles) {
            this.atts = atts;
            String styleAttr = getStringAttr("style", atts);
            if (styleAttr != null) {
                styles = new ArrayList<>();
                styles.add(new StyleSet(styleAttr));
            } else {
                String classAttr = getStringAttr("class", atts);
                if (classAttr != null) {
                    styles = new ArrayList<>();
                    String[] args = classAttr.split(" ");
                    for (int a = 0; a < args.length; a++) {
                        StyleSet set = globalStyles.get(args[a].trim());
                        if (set != null) {
                            styles.add(set);
                        }
                    }
                }
            }
        }

        public String getAttr(String name) {
            String v = null;
            if (styles != null && !styles.isEmpty()) {
                for (int a = 0, N = styles.size(); a < N; a++) {
                    v = styles.get(a).getStyle(name);
                    if (v != null) {
                        break;
                    }
                }
            }
            if (v == null) {
                v = getStringAttr(name, atts);
            }
            return v;
        }

        public String getString(String name) {
            return getAttr(name);
        }

        public Integer getHex(String name) {
            String v = getAttr(name);
            if (v == null) {
                return null;
            } else {
                try {
                    return Integer.parseInt(v.substring(1), 16);
                } catch (NumberFormatException nfe) {
                    return getColorByName(v);
                }
            }
        }

        public Float getFloat(String name, float defaultValue) {
            Float v = getFloat(name);
            if (v == null) {
                return defaultValue;
            } else {
                return v;
            }
        }

        public Float getFloat(String name) {
            String v = getAttr(name);
            if (v == null) {
                return null;
            } else {
                try {
                    return Float.parseFloat(v);
                } catch (NumberFormatException nfe) {
                    return null;
                }
            }
        }
    }

    private static class SVGHandler extends DefaultHandler {

        private Canvas canvas;
        private Bitmap bitmap;
        private SvgDrawable drawable;
        private int desiredWidth;
        private int desiredHeight;
        private float scale = 1.0f;
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RectF rect = new RectF();
        private RectF rectTmp = new RectF();
        private Integer paintColor;
        private float globalScale = 1f;

        boolean pushed = false;

        private HashMap<String, StyleSet> globalStyles = new HashMap<>();

        private SVGHandler(int dw, int dh, Integer color, boolean asDrawable, float scale) {
            globalScale = scale;
            desiredWidth = dw;
            desiredHeight = dh;
            paintColor = color;
            if (asDrawable) {
                drawable = new SvgDrawable();
            }
        }

        @Override
        public void startDocument() {

        }

        @Override
        public void endDocument() {

        }

        private boolean doFill(Properties atts) {
            if ("none".equals(atts.getString("display"))) {
                return false;
            }
            String fillString = atts.getString("fill");
            if (fillString != null && fillString.startsWith("url(#")) {
                String id = fillString.substring("url(#".length(), fillString.length() - 1);
                return false;
            } else {
                Integer color = atts.getHex("fill");
                if (color != null) {
                    doColor(atts, color, true);
                    paint.setStyle(Paint.Style.FILL);
                    return true;
                } else if (atts.getString("fill") == null && atts.getString("stroke") == null) {
                    paint.setStyle(Paint.Style.FILL);
                    if (paintColor != null) {
                        paint.setColor(paintColor);
                    } else {
                        paint.setColor(0xff000000);
                    }
                    return true;
                }
            }
            return false;
        }

        private boolean doStroke(Properties atts) {
            if ("none".equals(atts.getString("display"))) {
                return false;
            }
            Integer color = atts.getHex("stroke");
            if (color != null) {
                doColor(atts, color, false);
                Float width = atts.getFloat("stroke-width");

                if (width != null) {
                    paint.setStrokeWidth(width);
                }
                String linecap = atts.getString("stroke-linecap");
                if ("round".equals(linecap)) {
                    paint.setStrokeCap(Paint.Cap.ROUND);
                } else if ("square".equals(linecap)) {
                    paint.setStrokeCap(Paint.Cap.SQUARE);
                } else if ("butt".equals(linecap)) {
                    paint.setStrokeCap(Paint.Cap.BUTT);
                }
                String linejoin = atts.getString("stroke-linejoin");
                if ("miter".equals(linejoin)) {
                    paint.setStrokeJoin(Paint.Join.MITER);
                } else if ("round".equals(linejoin)) {
                    paint.setStrokeJoin(Paint.Join.ROUND);
                } else if ("bevel".equals(linejoin)) {
                    paint.setStrokeJoin(Paint.Join.BEVEL);
                }
                paint.setStyle(Paint.Style.STROKE);
                return true;
            }
            return false;
        }

        private void doColor(Properties atts, Integer color, boolean fillMode) {
            if (paintColor != null) {
                paint.setColor(paintColor);
            } else {
                int c = (0xFFFFFF & color) | 0xFF000000;
                paint.setColor(c);
            }
            Float opacity = atts.getFloat("opacity");
            if (opacity == null) {
                opacity = atts.getFloat(fillMode ? "fill-opacity" : "stroke-opacity");
            }
            if (opacity == null) {
                paint.setAlpha(255);
            } else {
                paint.setAlpha((int) (255 * opacity));
            }
        }

        private boolean boundsMode;
        private StringBuilder styles;

        private void pushTransform(Attributes atts) {
            final String transform = getStringAttr("transform", atts);
            pushed = transform != null;
            if (pushed) {
                final Matrix matrix = parseTransform(transform);
                if (drawable != null) {
                    drawable.addCommand(matrix);
                } else {
                    canvas.save();
                    canvas.concat(matrix);
                }
            }
        }

        private void popTransform() {
            if (pushed) {
                if (drawable != null) {
                    drawable.addCommand(null);
                } else {
                    canvas.restore();
                }
            }
        }

        @Override
        public void startElement(String namespaceURI, String localName, String qName, Attributes atts) {
            if (boundsMode && !localName.equals("style")) {
                return;
            }
            switch (localName) {
                case "svg": {
                    Float w = getFloatAttr("width", atts);
                    Float h = getFloatAttr("height", atts);
                    if (w == null || h == null) {
                        String viewBox = getStringAttr("viewBox", atts);
                        if (viewBox != null) {
                            String[] args = viewBox.split(" ");
                            w = Float.parseFloat(args[2]);
                            h = Float.parseFloat(args[3]);
                        }
                    }
                    if (w == null || h == null) {
                        w = (float) desiredWidth;
                        h = (float) desiredHeight;
                    }
                    int width = (int) Math.ceil(w);
                    int height = (int) Math.ceil(h);
                    if (width == 0 || height == 0) {
                        width = desiredWidth;
                        height = desiredHeight;
                    } else if (desiredWidth != 0 && desiredHeight != 0) {
                        scale = Math.min(desiredWidth / (float) width, desiredHeight / (float) height);
                        width *= scale;
                        height *= scale;
                    }
                    if (drawable == null) {
                        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(0);
                        canvas = new Canvas(bitmap);
                        if (scale != 0) {
                            canvas.scale(globalScale * scale, globalScale * scale);
                        }
                    } else {
                        drawable.width = width;
                        drawable.height = height;
                    }
                    break;
                }
                case "defs":
                case "clipPath":
                    boundsMode = true;
                    break;
                case "style":
                    styles = new StringBuilder();
                    break;
                case "g":
                    if ("bounds".equalsIgnoreCase(getStringAttr("id", atts))) {
                        boundsMode = true;
                    }
                    break;
                case "rect": {
                    Float x = getFloatAttr("x", atts);
                    if (x == null) {
                        x = 0f;
                    }
                    Float y = getFloatAttr("y", atts);
                    if (y == null) {
                        y = 0f;
                    }
                    Float width = getFloatAttr("width", atts);
                    Float height = getFloatAttr("height", atts);
                    Float rx = getFloatAttr("rx", atts, null);
                    pushTransform(atts);
                    Properties props = new Properties(atts, globalStyles);
                    if (doFill(props)) {
                        if (drawable != null) {
                            if (rx != null) {
                                drawable.addCommand(new RoundRect(new RectF(x, y, x + width, y + height), rx), paint);
                            } else {
                                drawable.addCommand(new RectF(x, y, x + width, y + height), paint);
                            }
                        } else {
                            if (rx != null) {
                                rectTmp.set(x, y, x + width, y + height);
                                canvas.drawRoundRect(rectTmp, rx, rx, paint);
                            } else {
                                canvas.drawRect(x, y, x + width, y + height, paint);
                            }
                        }
                    }
                    if (doStroke(props)) {
                        if (drawable != null) {
                            if (rx != null) {
                                drawable.addCommand(new RoundRect(new RectF(x, y, x + width, y + height), rx), paint);
                            } else {
                                drawable.addCommand(new RectF(x, y, x + width, y + height), paint);
                            }
                        } else {
                            if (rx != null) {
                                rectTmp.set(x, y, x + width, y + height);
                                canvas.drawRoundRect(rectTmp, rx, rx, paint);
                            } else {
                                canvas.drawRect(x, y, x + width, y + height, paint);
                            }
                        }
                    }
                    popTransform();
                    break;
                }
                case "line": {
                    Float x1 = getFloatAttr("x1", atts);
                    Float x2 = getFloatAttr("x2", atts);
                    Float y1 = getFloatAttr("y1", atts);
                    Float y2 = getFloatAttr("y2", atts);
                    Properties props = new Properties(atts, globalStyles);
                    if (doStroke(props)) {
                        pushTransform(atts);
                        if (drawable != null) {
                            drawable.addCommand(new Line(x1, y1, x2, y2), paint);
                        } else {
                            canvas.drawLine(x1, y1, x2, y2, paint);
                        }
                        popTransform();
                    }
                    break;
                }
                case "circle": {
                    Float centerX = getFloatAttr("cx", atts);
                    Float centerY = getFloatAttr("cy", atts);
                    Float radius = getFloatAttr("r", atts);
                    if (centerX != null && centerY != null && radius != null) {
                        pushTransform(atts);
                        Properties props = new Properties(atts, globalStyles);
                        if (doFill(props)) {
                            if (drawable != null) {
                                drawable.addCommand(new Circle(centerX, centerY, radius), paint);
                            } else {
                                canvas.drawCircle(centerX, centerY, radius, paint);
                            }
                        }
                        if (doStroke(props)) {
                            if (drawable != null) {
                                drawable.addCommand(new Circle(centerX, centerY, radius), paint);
                            } else {
                                canvas.drawCircle(centerX, centerY, radius, paint);
                            }
                        }
                        popTransform();
                    }
                    break;
                }
                case "ellipse": {
                    Float centerX = getFloatAttr("cx", atts);
                    Float centerY = getFloatAttr("cy", atts);
                    Float radiusX = getFloatAttr("rx", atts);
                    Float radiusY = getFloatAttr("ry", atts);
                    if (centerX != null && centerY != null && radiusX != null && radiusY != null) {
                        pushTransform(atts);
                        Properties props = new Properties(atts, globalStyles);
                        rect.set(centerX - radiusX, centerY - radiusY, centerX + radiusX, centerY + radiusY);
                        if (doFill(props)) {
                            if (drawable != null) {
                                drawable.addCommand(new Oval(rect), paint);
                            } else {
                                canvas.drawOval(rect, paint);
                            }
                        }
                        if (doStroke(props)) {
                            if (drawable != null) {
                                drawable.addCommand(new Oval(rect), paint);
                            } else {
                                canvas.drawOval(rect, paint);
                            }
                        }
                        popTransform();
                    }
                    break;
                }
                case "polygon":
                case "polyline":
                    NumberParse numbers = getNumberParseAttr("points", atts);
                    if (numbers != null) {
                        Path p = new Path();
                        ArrayList<Float> points = numbers.numbers;
                        if (points.size() > 1) {
                            pushTransform(atts);
                            Properties props = new Properties(atts, globalStyles);
                            p.moveTo(points.get(0), points.get(1));
                            for (int i = 2; i < points.size(); i += 2) {
                                float x = points.get(i);
                                float y = points.get(i + 1);
                                p.lineTo(x, y);
                            }
                            if (localName.equals("polygon")) {
                                p.close();
                            }
                            if (doFill(props)) {
                                if (drawable != null) {
                                    drawable.addCommand(p, paint);
                                } else {
                                    canvas.drawPath(p, paint);
                                }
                            }
                            if (doStroke(props)) {
                                if (drawable != null) {
                                    drawable.addCommand(p, paint);
                                } else {
                                    canvas.drawPath(p, paint);
                                }
                            }
                            popTransform();
                        }
                    }
                    break;
                case "path": {
                    Path p = doPath(getStringAttr("d", atts));
                    pushTransform(atts);
                    Properties props = new Properties(atts, globalStyles);
                    if (doFill(props)) {
                        if (drawable != null) {
                            drawable.addCommand(p, paint);
                        } else {
                            canvas.drawPath(p, paint);
                        }
                    }
                    if (doStroke(props)) {
                        if (drawable != null) {
                            drawable.addCommand(p, paint);
                        } else {
                            canvas.drawPath(p, paint);
                        }
                    }
                    popTransform();
                    break;
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (styles != null) {
                styles.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String namespaceURI, String localName, String qName) {
            switch (localName) {
                case "style":
                    if (styles != null) {
                        String[] args = styles.toString().split("\\}");
                        for (int a = 0; a < args.length; a++) {
                            args[a] = args[a].trim().replace("\t", "").replace("\n", "");
                            if (args[a].length() == 0 || args[a].charAt(0) != '.') {
                                continue;
                            }
                            int idx1 = args[a].indexOf('{');
                            if (idx1 < 0) {
                                continue;
                            }
                            String name = args[a].substring(1, idx1).trim();
                            String style = args[a].substring(idx1 + 1);
                            globalStyles.put(name, new StyleSet(style));
                        }
                        styles = null;
                    }
                    break;
                case "svg":
                    break;
                case "g":
                case "defs":
                case "clipPath":
                    boundsMode = false;
                    break;
            }
        }

        public Bitmap getBitmap() {
            return bitmap;
        }

        public SvgDrawable getDrawable() {
            return drawable;
        }
    }

    private static final double[] pow10 = new double[128];

    static {
        for (int i = 0; i < pow10.length; i++) {
            pow10[i] = Math.pow(10, i);
        }
    }

    public static class ParserHelper {

        private char current;
        private CharSequence s;
        public int pos;
        private int n;

        public ParserHelper(CharSequence s, int pos) {
            this.s = s;
            this.pos = pos;
            n = s.length();
            current = s.charAt(pos);
        }

        private char read() {
            if (pos < n) {
                pos++;
            }
            if (pos == n) {
                return '\0';
            } else {
                return s.charAt(pos);
            }
        }

        public void skipWhitespace() {
            while (pos < n) {
                if (Character.isWhitespace(s.charAt(pos))) {
                    advance();
                } else {
                    break;
                }
            }
        }

        public void skipNumberSeparator() {
            while (pos < n) {
                char c = s.charAt(pos);
                switch (c) {
                    case ' ':
                    case ',':
                    case '\n':
                    case '\t':
                        advance();
                        break;
                    default:
                        return;
                }
            }
        }

        public void advance() {
            current = read();
        }

        public float parseFloat() {
            int mant = 0;
            int mantDig = 0;
            boolean mantPos = true;
            boolean mantRead = false;

            int exp = 0;
            int expDig = 0;
            int expAdj = 0;
            boolean expPos = true;

            switch (current) {
                case '-':
                    mantPos = false;
                    // fallthrough
                case '+':
                    current = read();
            }

            m1:
            switch (current) {
                default:
                    return Float.NaN;

                case '.':
                    break;

                case '0':
                    mantRead = true;
                    l:
                    for (; ; ) {
                        current = read();
                        switch (current) {
                            case '1':
                            case '2':
                            case '3':
                            case '4':
                            case '5':
                            case '6':
                            case '7':
                            case '8':
                            case '9':
                                break l;
                            case '.':
                            case 'e':
                            case 'E':
                                break m1;
                            default:
                                return 0.0f;
                            case '0':
                        }
                    }

                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    mantRead = true;
                    l:
                    for (; ; ) {
                        if (mantDig < 9) {
                            mantDig++;
                            mant = mant * 10 + (current - '0');
                        } else {
                            expAdj++;
                        }
                        current = read();
                        switch (current) {
                            default:
                                break l;
                            case '0':
                            case '1':
                            case '2':
                            case '3':
                            case '4':
                            case '5':
                            case '6':
                            case '7':
                            case '8':
                            case '9':
                        }
                    }
            }

            if (current == '.') {
                current = read();
                m2:
                switch (current) {
                    default:
                    case 'e':
                    case 'E':
                        if (!mantRead) {
                            reportUnexpectedCharacterError(current);
                            return 0.0f;
                        }
                        break;

                    case '0':
                        if (mantDig == 0) {
                            l:
                            for (; ; ) {
                                current = read();
                                expAdj--;
                                switch (current) {
                                    case '1':
                                    case '2':
                                    case '3':
                                    case '4':
                                    case '5':
                                    case '6':
                                    case '7':
                                    case '8':
                                    case '9':
                                        break l;
                                    default:
                                        if (!mantRead) {
                                            return 0.0f;
                                        }
                                        break m2;
                                    case '0':
                                }
                            }
                        }
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                        l:
                        for (; ; ) {
                            if (mantDig < 9) {
                                mantDig++;
                                mant = mant * 10 + (current - '0');
                                expAdj--;
                            }
                            current = read();
                            switch (current) {
                                default:
                                    break l;
                                case '0':
                                case '1':
                                case '2':
                                case '3':
                                case '4':
                                case '5':
                                case '6':
                                case '7':
                                case '8':
                                case '9':
                            }
                        }
                }
            }

            switch (current) {
                case 'e':
                case 'E':
                    current = read();
                    switch (current) {
                        default:
                            reportUnexpectedCharacterError(current);
                            return 0f;
                        case '-':
                            expPos = false;
                        case '+':
                            current = read();
                            switch (current) {
                                default:
                                    reportUnexpectedCharacterError(current);
                                    return 0f;
                                case '0':
                                case '1':
                                case '2':
                                case '3':
                                case '4':
                                case '5':
                                case '6':
                                case '7':
                                case '8':
                                case '9':
                            }
                        case '0':
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                        case '8':
                        case '9':
                    }

                    en:
                    switch (current) {
                        case '0':
                            l:
                            for (; ; ) {
                                current = read();
                                switch (current) {
                                    case '1':
                                    case '2':
                                    case '3':
                                    case '4':
                                    case '5':
                                    case '6':
                                    case '7':
                                    case '8':
                                    case '9':
                                        break l;
                                    default:
                                        break en;
                                    case '0':
                                }
                            }

                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                        case '8':
                        case '9':
                            l:
                            for (; ; ) {
                                if (expDig < 3) {
                                    expDig++;
                                    exp = exp * 10 + (current - '0');
                                }
                                current = read();
                                switch (current) {
                                    default:
                                        break l;
                                    case '0':
                                    case '1':
                                    case '2':
                                    case '3':
                                    case '4':
                                    case '5':
                                    case '6':
                                    case '7':
                                    case '8':
                                    case '9':
                                }
                            }
                    }
                default:
            }

            if (!expPos) {
                exp = -exp;
            }
            exp += expAdj;
            if (!mantPos) {
                mant = -mant;
            }

            return buildFloat(mant, exp);
        }

        private void reportUnexpectedCharacterError(char c) {
            throw new RuntimeException("Unexpected char '" + c + "'.");
        }

        public float buildFloat(int mant, int exp) {
            if (exp < -125 || mant == 0) {
                return 0.0f;
            }

            if (exp >= 128) {
                return (mant > 0) ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
            }

            if (exp == 0) {
                return mant;
            }

            if (mant >= (1 << 26)) {
                mant++;
            }

            return (float) ((exp > 0) ? mant * pow10[exp] : mant / pow10[-exp]);
        }

        public float nextFloat() {
            skipWhitespace();
            float f = parseFloat();
            skipNumberSeparator();
            return f;
        }
    }

    public static String decompress(byte[] encoded) {
        try {
            StringBuilder path = new StringBuilder(encoded.length * 2);
            path.append('M');
            for (int i = 0; i < encoded.length; i++) {
                int num = encoded[i] & 0xff;
                if (num >= 128 + 64) {
                    int start = num - 128 - 64;
                    path.append("AACAAAAHAAALMAAAQASTAVAAAZaacaaaahaaalmaaaqastava.az0123456789-,".charAt(start));
                } else {
                    if (num >= 128) {
                        path.append(',');
                    } else if (num >= 64) {
                        path.append('-');
                    }
                    path.append(num & 63);
                }
            }
            path.append('z');
            return path.toString();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "";
    }
}
