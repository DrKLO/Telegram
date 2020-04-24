package org.telegram.ui.Components;

import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.util.Property;
import android.view.animation.OvershootInterpolator;

import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.PhotoViewer;

public class AnimationProperties {

    public static OvershootInterpolator overshootInterpolator = new OvershootInterpolator(1.9f);

    public static abstract class FloatProperty<T> extends Property<T, Float> {

        public FloatProperty(String name) {
            super(Float.class, name);
        }

        public abstract void setValue(T object, float value);

        @Override
        final public void set(T object, Float value) {
            setValue(object, value);
        }
    }

    public static abstract class IntProperty<T> extends Property<T, Integer> {

        public IntProperty(String name) {
            super(Integer.class, name);
        }

        public abstract void setValue(T object, int value);

        @Override
        final public void set(T object, Integer value) {
            setValue(object, value);
        }
    }

    public static final Property<Paint, Integer> PAINT_ALPHA = new IntProperty<Paint>("alpha") {
        @Override
        public void setValue(Paint object, int value) {
            object.setAlpha(value);
        }

        @Override
        public Integer get(Paint object) {
            return object.getAlpha();
        }
    };

    public static final Property<ColorDrawable, Integer> COLOR_DRAWABLE_ALPHA = new IntProperty<ColorDrawable>("alpha") {
        @Override
        public void setValue(ColorDrawable object, int value) {
            object.setAlpha(value);
        }

        @Override
        public Integer get(ColorDrawable object) {
            return object.getAlpha();
        }
    };

    public static final Property<ShapeDrawable, Integer> SHAPE_DRAWABLE_ALPHA = new IntProperty<ShapeDrawable>("alpha") {
        @Override
        public void setValue(ShapeDrawable object, int value) {
            object.getPaint().setAlpha(value);
        }

        @Override
        public Integer get(ShapeDrawable object) {
            return object.getPaint().getAlpha();
        }
    };

    public static final Property<ClippingImageView, Float> CLIPPING_IMAGE_VIEW_PROGRESS = new FloatProperty<ClippingImageView>("animationProgress") {
        @Override
        public void setValue(ClippingImageView object, float value) {
            object.setAnimationProgress(value);
        }

        @Override
        public Float get(ClippingImageView object) {
            return object.getAnimationProgress();
        }
    };

    public static final Property<PhotoViewer, Float> PHOTO_VIEWER_ANIMATION_VALUE = new FloatProperty<PhotoViewer>("animationValue") {
        @Override
        public void setValue(PhotoViewer object, float value) {
            object.setAnimationValue(value);
        }

        @Override
        public Float get(PhotoViewer object) {
            return object.getAnimationValue();
        }
    };

    public static final Property<DialogCell, Float> CLIP_DIALOG_CELL_PROGRESS = new FloatProperty<DialogCell>("clipProgress") {
        @Override
        public void setValue(DialogCell object, float value) {
            object.setClipProgress(value);
        }

        @Override
        public Float get(DialogCell object) {
            return object.getClipProgress();
        }
    };
}
