package org.telegram.messenger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.location.Location;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Keep;
import androidx.core.util.Consumer;

import java.util.List;

@Keep
public interface IMapsProvider {
    int MAP_TYPE_NORMAL = 0,
        MAP_TYPE_SATELLITE = 1,
        MAP_TYPE_HYBRID = 2;

    void initializeMaps(Context context);
    IMapView onCreateMapView(Context context);
    IMarkerOptions onCreateMarkerOptions();
    ICircleOptions onCreateCircleOptions();
    ILatLngBoundsBuilder onCreateLatLngBoundsBuilder();
    ICameraUpdate newCameraUpdateLatLng(LatLng latLng);
    ICameraUpdate newCameraUpdateLatLngZoom(LatLng latLng, float zoom);
    ICameraUpdate newCameraUpdateLatLngBounds(ILatLngBounds bounds, int padding);
    IMapStyleOptions loadRawResourceStyle(Context context, int resId);
    String getMapsAppPackageName();
    int getInstallMapsString();

    interface IMap {
        void setMapType(int mapType);
        void animateCamera(ICameraUpdate update);
        void animateCamera(ICameraUpdate update, ICancelableCallback callback);
        void animateCamera(ICameraUpdate update, int duration, ICancelableCallback callback);
        void moveCamera(ICameraUpdate update);
        float getMaxZoomLevel();
        float getMinZoomLevel();
        void setMyLocationEnabled(boolean enabled);
        IUISettings getUiSettings();
        void setOnCameraIdleListener(Runnable callback);
        void setOnCameraMoveStartedListener(OnCameraMoveStartedListener onCameraMoveStartedListener);
        CameraPosition getCameraPosition();
        void setOnMapLoadedCallback(Runnable callback);
        IProjection getProjection();
        void setPadding(int left, int top, int right, int bottom);
        void setMapStyle(IMapStyleOptions style);
        IMarker addMarker(IMarkerOptions markerOptions);
        void setOnMyLocationChangeListener(Consumer<Location> callback);
        void setOnMarkerClickListener(OnMarkerClickListener markerClickListener);
        void setOnCameraMoveListener(Runnable callback);
        ICircle addCircle(ICircleOptions circleOptions);
    }

    interface IMapView {
        View getView();
        void getMapAsync(Consumer<IMap> callback);
        void onResume();
        void onPause();
        void onCreate(Bundle savedInstance);
        void onDestroy();
        void onLowMemory();
        void setOnDispatchTouchEventInterceptor(ITouchInterceptor touchInterceptor);
        void setOnInterceptTouchEventInterceptor(ITouchInterceptor touchInterceptor);
        void setOnLayoutListener(Runnable callback);
        default GLSurfaceView getGlSurfaceView() {
            return null;
        }
    }

    interface IUISettings {
        void setZoomControlsEnabled(boolean enabled);
        void setMyLocationButtonEnabled(boolean enabled);
        void setCompassEnabled(boolean enabled);
    }

    interface IMarker {
        Object getTag();
        void setTag(Object tag);
        LatLng getPosition();
        void setPosition(LatLng latLng);
        void setRotation(int rotation);
        void setIcon(Bitmap bitmap);
        void setIcon(int resId);
        void remove();
    }

    interface IMarkerOptions {
        IMarkerOptions position(LatLng latLng);
        IMarkerOptions icon(Bitmap bitmap);
        IMarkerOptions icon(int resId);
        IMarkerOptions anchor(float lat, float lng);
        IMarkerOptions title(String title);
        IMarkerOptions snippet(String snippet);
        IMarkerOptions flat(boolean flat);
    }

    interface ICircle {
        void setStrokeColor(int color);
        void setFillColor(int color);
        void setRadius(double radius);
        double getRadius();
        void setCenter(LatLng latLng);
        void remove();
    }

    interface ICircleOptions {
        ICircleOptions center(LatLng latLng);
        ICircleOptions radius(double radius);
        ICircleOptions strokeColor(int color);
        ICircleOptions fillColor(int color);
        ICircleOptions strokePattern(List<PatternItem> patternItems);
        ICircleOptions strokeWidth(int width);
    }

    interface ILatLngBoundsBuilder {
        ILatLngBoundsBuilder include(LatLng latLng);
        ILatLngBounds build();
    }

    interface ILatLngBounds {
        LatLng getCenter();
    }

    interface IMapStyleOptions {}

    interface ICameraUpdate {}

    interface IProjection {
        Point toScreenLocation(LatLng latLng);
    }

    interface ITouchInterceptor {
        boolean onInterceptTouchEvent(MotionEvent ev, ICallableMethod<Boolean, MotionEvent> origMethod);
    }

    interface ICallableMethod<R, A> {
        R call(A arg);
    }

    interface ICancelableCallback {
        void onFinish();
        void onCancel();
    }

    interface OnCameraMoveStartedListener {
        int REASON_GESTURE = 1,
            REASON_API_ANIMATION = 2,
            REASON_DEVELOPER_ANIMATION = 3;

        void onCameraMoveStarted(int reason);
    }

    interface OnMarkerClickListener {
        boolean onClick(IMarker marker);
    }

    class PatternItem {
        public final static class Gap extends PatternItem {
            public final int length;

            public Gap(int length) {
                this.length = length;
            }
        }

        public final static class Dash extends PatternItem {
            public final int length;

            public Dash(int length) {
                this.length = length;
            }
        }
    }

    final class CameraPosition {
        public final LatLng target;
        public final float zoom;

        public CameraPosition(LatLng target, float zoom) {
            this.target = target;
            this.zoom = zoom;
        }
    }

    final class LatLng {
        public final double latitude;
        public final double longitude;

        public LatLng(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
