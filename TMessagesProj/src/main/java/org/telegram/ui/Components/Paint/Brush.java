package org.telegram.ui.Components.Paint;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.util.Arrays;
import java.util.List;

public abstract class Brush {
    public static List<Brush> BRUSHES_LIST = Arrays.asList(
        new Radial(),
        new Arrow(),
        new Elliptical(),
        new Neon(),
        new Blurer(),
        new Eraser()
    );

    public static final int PAINT_TYPE_BLIT = 0;
    public static final int PAINT_TYPE_COMPOSITE = 1;
    public static final int PAINT_TYPE_BRUSH = 2;

    public float getSpacing() {
        return 0.15f;
    }

    public float getAlpha() {
        return 0.85f;
    }

    public float getOverrideAlpha() {
        return 1f;
    }

    public float getAngle() {
        return 0.0f;
    }

    public float getScale() {
        return 1.0f;
    }

    public float getPreviewScale() {
        return 0.4f;
    }

    public float getDefaultWeight() {
        return 0.25f;
    }

    public boolean isEraser() {
        return false;
    }

    public String getShaderName(int paintType) {
        switch (paintType) {
            case PAINT_TYPE_BLIT:
                return "blitWithMask";
            case PAINT_TYPE_COMPOSITE:
                return "compositeWithMask";
            case PAINT_TYPE_BRUSH:
                return "brush";
        }
        return null;
    }

    public int getStampResId() {
        return R.drawable.paint_radial_brush;
    }

    public Bitmap getStamp() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        return BitmapFactory.decodeResource(ApplicationLoader.applicationContext.getResources(), getStampResId(), options);
    }

    public float getSmoothThicknessRate() {
        return 1f;
    }

    public int getIconRes() {
        return 0;
    }

    public int getDefaultColor() {
        return PersistColorPalette.COLOR_BLACK;
    }

    public static class Radial extends Brush {

        @Override
        public int getIconRes() {
            return R.raw.photo_pen;
        }

        @Override
        public int getDefaultColor() {
            return PersistColorPalette.COLOR_RED;
        }
    }

    public static class Elliptical extends Brush {

        @Override
        public float getSpacing() {
            return 0.04f;
        }

        @Override
        public float getAlpha() {
            return 0.3f;
        }

        @Override
        public float getOverrideAlpha() {
            return 0.45f;
        }

        @Override
        public float getAngle() {
            return (float) Math.toRadians(0.0);
        }

        @Override
        public float getScale() {
            return 1.5f;
        }

        @Override
        public float getPreviewScale() {
            return 0.4f;
        }

        @Override
        public int getStampResId() {
            return R.drawable.paint_elliptical_brush;
        }

        @Override
        public int getIconRes() {
            return R.raw.photo_marker;
        }

        @Override
        public float getDefaultWeight() {
            return 0.5f;
        }

        @Override
        public int getDefaultColor() {
            return PersistColorPalette.COLOR_YELLOW;
        }
    }

    public static class Neon extends Brush {

        @Override
        public float getSpacing() {
            return 0.07f;
        }

        @Override
        public float getAlpha() {
            return 0.7f;
        }

        @Override
        public float getScale() {
            return 1.45f;
        }

        @Override
        public float getPreviewScale() {
            return 0.2f;
        }

        @Override
        public String getShaderName(int paintType) {
            switch (paintType) {
                case PAINT_TYPE_BLIT:
                    return "blitWithMaskLight";
                case PAINT_TYPE_COMPOSITE:
                    return "compositeWithMaskLight";
                case PAINT_TYPE_BRUSH:
                    return "brushLight";
            }
            return null;
        }

        @Override
        public int getStampResId() {
            return R.drawable.paint_neon_brush;
        }

        @Override
        public int getIconRes() {
            return R.raw.photo_neon;
        }

        @Override
        public float getDefaultWeight() {
            return 0.5f;
        }

        @Override
        public int getDefaultColor() {
            return PersistColorPalette.COLOR_GREEN;
        }
    }

    public static class Arrow extends Brush {

        @Override
        public float getSmoothThicknessRate() {
            return .25f;
        }

        @Override
        public int getIconRes() {
            return R.raw.photo_arrow;
        }

        @Override
        public float getDefaultWeight() {
            return 0.25f;
        }

        @Override
        public int getDefaultColor() {
            return PersistColorPalette.COLOR_ORANGE;
        }
    }

    public static class Eraser extends Brush {

        @Override
        public float getAlpha() {
            return 1f;
        }

        @Override
        public float getPreviewScale() {
            return 0.35f;
        }

        @Override
        public float getDefaultWeight() {
            return 1.0f;
        }

        @Override
        public String getShaderName(int paintType) {
            switch (paintType) {
                case PAINT_TYPE_BLIT:
                    return "blitWithMaskEraser";
                case PAINT_TYPE_COMPOSITE:
                    return "compositeWithMaskEraser";
                case PAINT_TYPE_BRUSH:
                    return "brush";
            }
            return null;
        }

        @Override
        public int getIconRes() {
            return R.raw.photo_eraser;
        }

        @Override
        public boolean isEraser() {
            return true;
        }
    }

    public static class Blurer extends Brush {

        @Override
        public float getAlpha() {
            return 1f;
        }

        @Override
        public float getPreviewScale() {
            return 0.35f;
        }

        @Override
        public float getDefaultWeight() {
            return 1.0f;
        }

        @Override
        public String getShaderName(int paintType) {
            switch (paintType) {
                case PAINT_TYPE_BLIT:
                    return "blitWithMaskBlurer";
                case PAINT_TYPE_COMPOSITE:
                    return "compositeWithMaskBlurer";
                case PAINT_TYPE_BRUSH:
                    return "brush";
            }
            return null;
        }

        @Override
        public int getIconRes() {
            return R.raw.photo_blur;
        }
    }

    public static abstract class Shape extends Brush {

        public static final int SHAPE_TYPE_CIRCLE = 0;
        public static final int SHAPE_TYPE_RECTANGLE = 1;
        public static final int SHAPE_TYPE_STAR = 2;
        public static final int SHAPE_TYPE_BUBBLE = 3;
        public static final int SHAPE_TYPE_ARROW = 4;

        public static List<Shape> SHAPES_LIST = Arrays.asList(
            new Circle(),
            new Rectangle(),
            new Star(),
            new Bubble(),
            new Arrow()
        );

        public static Brush.Shape make(int type) {
            if (type < 0 || type > SHAPES_LIST.size()) {
                throw new IndexOutOfBoundsException("Shape type must be in range from 0 to " + (SHAPES_LIST.size() - 1) + ", but got " + type);
            }
            return SHAPES_LIST.get(type);
        }

        @Override
        public String getShaderName(int paintType) {
            switch (paintType) {
                case PAINT_TYPE_BLIT:
                case PAINT_TYPE_COMPOSITE:
                    return "shape";
                case PAINT_TYPE_BRUSH:
                    return "brush";
            }
            return null;
        }

        public String getShapeName() {
            return null;
        }

        public int getShapeShaderType() {
            return 0;
        }

        public int getFilledIconRes() {
            return 0;
        }

        @Override
        public float getAlpha() {
            return 1f;
        }

        public static class Circle extends Shape {
            @Override
            public int getShapeShaderType() {
                return SHAPE_TYPE_CIRCLE;
            }

            @Override
            public String getShapeName() {
                return LocaleController.getString(R.string.PaintCircle);
            }

            @Override
            public int getIconRes() {
                return R.drawable.photo_circle;
            }

            @Override
            public int getFilledIconRes() {
                return R.drawable.photo_circle_fill;
            }
        }

        public static class Rectangle extends Shape {
            @Override
            public int getShapeShaderType() {
                return SHAPE_TYPE_RECTANGLE;
            }

            @Override
            public String getShapeName() {
                return LocaleController.getString(R.string.PaintRectangle);
            }

            @Override
            public int getIconRes() {
                return R.drawable.photo_rectangle;
            }

            @Override
            public int getFilledIconRes() {
                return R.drawable.photo_rectangle_fill;
            }
        }

        public static class Star extends Shape {
            @Override
            public int getShapeShaderType() {
                return SHAPE_TYPE_STAR;
            }

            @Override
            public String getShapeName() {
                return LocaleController.getString(R.string.PaintStar);
            }

            @Override
            public int getIconRes() {
                return R.drawable.photo_star;
            }

            @Override
            public int getFilledIconRes() {
                return R.drawable.photo_star_fill;
            }
        }

        public static class Bubble extends Shape {
            @Override
            public int getShapeShaderType() {
                return SHAPE_TYPE_BUBBLE;
            }

            @Override
            public String getShapeName() {
                return LocaleController.getString(R.string.PaintBubble);
            }

            @Override
            public int getIconRes() {
                return R.drawable.msg_msgbubble;
            }

            @Override
            public int getFilledIconRes() {
                return R.drawable.msg_msgbubble2;
            }
        }

        public static class Arrow extends Shape {
            @Override
            public int getShapeShaderType() {
                return SHAPE_TYPE_ARROW;
            }

            @Override
            public String getShapeName() {
                return LocaleController.getString(R.string.PaintArrow);
            }

            @Override
            public int getIconRes() {
                return R.drawable.photo_arrowshape;
            }

            @Override
            public int getFilledIconRes() {
                return R.drawable.photo_arrowshape;
            }
        }
    }
}