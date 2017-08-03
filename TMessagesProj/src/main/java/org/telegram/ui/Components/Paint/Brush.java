package org.telegram.ui.Components.Paint;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public interface Brush {

    float getSpacing();
    float getAlpha();
    float getAngle();
    float getScale();
    boolean isLightSaber();
    Bitmap getStamp();

    class Radial implements Brush {

        @Override
        public float getSpacing() {
            return 0.15f;
        }

        @Override
        public float getAlpha() {
            return 0.85f;
        }

        @Override
        public float getAngle() {
            return 0.0f;
        }

        @Override
        public float getScale() {
            return 1.0f;
        }

        @Override
        public boolean isLightSaber() {
            return false;
        }

        @Override
        public Bitmap getStamp() {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            return BitmapFactory.decodeResource(ApplicationLoader.applicationContext.getResources(), R.drawable.paint_radial_brush, options);
        }
    }

    class Elliptical implements Brush {

        @Override
        public float getSpacing() {
            return 0.04f;
        }

        @Override
        public float getAlpha() {
            return 0.3f;
        }

        @Override
        public float getAngle() {
            return (float) Math.toRadians(125.0);
        }

        @Override
        public float getScale() {
            return 1.5f;
        }

        @Override
        public boolean isLightSaber() {
            return false;
        }

        @Override
        public Bitmap getStamp() {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            return BitmapFactory.decodeResource(ApplicationLoader.applicationContext.getResources(), R.drawable.paint_elliptical_brush, options);
        }
    }

    class Neon implements Brush {

        @Override
        public float getSpacing() {
            return 0.07f;
        }

        @Override
        public float getAlpha() {
            return 0.7f;
        }

        @Override
        public float getAngle() {
            return 0.0f;
        }

        @Override
        public float getScale() {
            return 1.45f;
        }

        @Override
        public boolean isLightSaber() {
            return true;
        }

        @Override
        public Bitmap getStamp() {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            return BitmapFactory.decodeResource(ApplicationLoader.applicationContext.getResources(), R.drawable.paint_neon_brush, options);
        }
    }
}