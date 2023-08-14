package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.location.Location;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.util.Consumer;

import com.huawei.hms.maps.CameraUpdate;
import com.huawei.hms.maps.CameraUpdateFactory;
import com.huawei.hms.maps.HuaweiMap;
import com.huawei.hms.maps.MapView;
import com.huawei.hms.maps.MapsInitializer;
import com.huawei.hms.maps.Projection;
import com.huawei.hms.maps.UiSettings;
import com.huawei.hms.maps.model.BitmapDescriptorFactory;
import com.huawei.hms.maps.model.Circle;
import com.huawei.hms.maps.model.CircleOptions;
import com.huawei.hms.maps.model.Dash;
import com.huawei.hms.maps.model.Gap;
import com.huawei.hms.maps.model.LatLngBounds;
import com.huawei.hms.maps.model.MapStyleOptions;
import com.huawei.hms.maps.model.Marker;
import com.huawei.hms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HuaweiMapsProvider implements IMapsProvider {
    public HuaweiMapsProvider() {
        initializeMaps(ApplicationLoader.applicationContext);
    }

    @Override
    public void initializeMaps(Context context) {
        MapsInitializer.initialize(context);
    }

    @Override
    public IMapView onCreateMapView(Context context) {
        return new HuaweiMapView(context);
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLng(LatLng latLng) {
        return new HuaweiCameraUpdate(CameraUpdateFactory.newLatLng(new com.huawei.hms.maps.model.LatLng(latLng.latitude, latLng.longitude)));
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLngZoom(LatLng latLng, float zoom) {
        return new HuaweiCameraUpdate(CameraUpdateFactory.newLatLngZoom(new com.huawei.hms.maps.model.LatLng(latLng.latitude, latLng.longitude), zoom));
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLngBounds(ILatLngBounds bounds, int padding) {
        return new HuaweiCameraUpdate(CameraUpdateFactory.newLatLngBounds(((HuaweiLatLngBounds) bounds).bounds, padding));
    }

    @Override
    public ILatLngBoundsBuilder onCreateLatLngBoundsBuilder() {
        return new HuaweiLatLngBoundsBuilder();
    }

    @Override
    public IMapStyleOptions loadRawResourceStyle(Context context, int resId) {
        return new HuaweiMapStyleOptions(MapStyleOptions.loadRawResourceStyle(context, resId));
    }

    @Override
    public IMarkerOptions onCreateMarkerOptions() {
        return new HuaweiMarkerOptions();
    }

    @Override
    public ICircleOptions onCreateCircleOptions() {
        return new HuaweiCircleOptions();
    }

    @Override
    public String getMapsAppPackageName() {
        return "com.huawei.maps.app";
    }

    @Override
    public int getInstallMapsString() {
        return R.string.InstallHuaweiMaps;
    }

    public final static class HuaweiMapImpl implements IMap {
        private HuaweiMap huaweiMap;

        private Map<Marker, HuaweiMarker> implToAbsMarkerMap = new HashMap<>();
        private Map<Circle, HuaweiCircle> implToAbsCircleMap = new HashMap<>();

        private HuaweiMapImpl(HuaweiMap huaweiMap) {
            this.huaweiMap = huaweiMap;
        }

        @Override
        public void setMapType(int mapType) {
            switch (mapType) {
                case MAP_TYPE_NORMAL:
                    huaweiMap.setMapType(HuaweiMap.MAP_TYPE_NORMAL);
                    break;
                case MAP_TYPE_SATELLITE:
                    huaweiMap.setMapType(HuaweiMap.MAP_TYPE_SATELLITE);
                    break;
                case MAP_TYPE_HYBRID:
                    huaweiMap.setMapType(HuaweiMap.MAP_TYPE_HYBRID);
                    break;
            }
        }

        @Override
        public float getMaxZoomLevel() {
            return huaweiMap.getMaxZoomLevel();
        }

        @Override
        public float getMinZoomLevel() {
            return huaweiMap.getMinZoomLevel();
        }

        @SuppressLint("MissingPermission")
        @Override
        public void setMyLocationEnabled(boolean enabled) {
            huaweiMap.setMyLocationEnabled(enabled);
        }

        @Override
        public IUISettings getUiSettings() {
            return new HuaweiUISettings(huaweiMap.getUiSettings());
        }

        @Override
        public void setOnCameraMoveStartedListener(OnCameraMoveStartedListener onCameraMoveStartedListener) {
            huaweiMap.setOnCameraMoveStartedListener(reason -> {
                int outReason;
                switch (reason) {
                    default:
                    case HuaweiMap.OnCameraMoveStartedListener.REASON_GESTURE:
                        outReason = OnCameraMoveStartedListener.REASON_GESTURE;
                        break;
                    case HuaweiMap.OnCameraMoveStartedListener.REASON_API_ANIMATION:
                        outReason = OnCameraMoveStartedListener.REASON_API_ANIMATION;
                        break;
                    case HuaweiMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION:
                        outReason = OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION;
                        break;
                }
                onCameraMoveStartedListener.onCameraMoveStarted(outReason);
            });
        }

        @Override
        public void setOnCameraIdleListener(Runnable callback) {
            huaweiMap.setOnCameraIdleListener(callback::run);
        }

        @Override
        public CameraPosition getCameraPosition() {
            com.huawei.hms.maps.model.CameraPosition pos = huaweiMap.getCameraPosition();
            return new CameraPosition(new LatLng(pos.target.latitude, pos.target.longitude), pos.zoom);
        }

        @Override
        public void setOnMapLoadedCallback(Runnable callback) {
            huaweiMap.setOnMapLoadedCallback(callback::run);
        }

        @Override
        public IProjection getProjection() {
            return new HuaweiProjection(huaweiMap.getProjection());
        }

        @Override
        public void setPadding(int left, int top, int right, int bottom) {
            huaweiMap.setPadding(left, top, right, bottom);
        }

        @Override
        public void setMapStyle(IMapStyleOptions style) {
            huaweiMap.setMapStyle(style == null ? null : ((HuaweiMapStyleOptions) style).mapStyleOptions);
        }

        @Override
        public IMarker addMarker(IMarkerOptions markerOptions) {
            Marker impl = huaweiMap.addMarker(((HuaweiMarkerOptions) markerOptions).markerOptions);
            HuaweiMarker abs = new HuaweiMarker(impl);
            implToAbsMarkerMap.put(impl, abs);
            return abs;
        }

        @Override
        public ICircle addCircle(ICircleOptions circleOptions) {
            Circle impl = huaweiMap.addCircle(((HuaweiCircleOptions) circleOptions).circleOptions);
            HuaweiCircle abs = new HuaweiCircle(impl);
            implToAbsCircleMap.put(impl, abs);
            return abs;
        }

        @Override
        public void setOnMyLocationChangeListener(Consumer<Location> callback) {}

        @Override
        public void setOnMarkerClickListener(OnMarkerClickListener markerClickListener) {
            huaweiMap.setOnMarkerClickListener(marker -> {
                HuaweiMarker abs = implToAbsMarkerMap.get(marker);
                if (abs == null) {
                    abs = new HuaweiMarker(marker);
                    implToAbsMarkerMap.put(marker, abs);
                }
                return markerClickListener.onClick(abs);
            });
        }

        @Override
        public void setOnCameraMoveListener(Runnable callback) {
            huaweiMap.setOnCameraMoveListener(callback::run);
        }

        @Override
        public void animateCamera(ICameraUpdate update) {
            huaweiMap.animateCamera(((HuaweiCameraUpdate) update).cameraUpdate);
        }

        @Override
        public void animateCamera(ICameraUpdate update, ICancelableCallback callback) {
            huaweiMap.animateCamera(((HuaweiCameraUpdate) update).cameraUpdate, callback == null ? null : new HuaweiMap.CancelableCallback() {
                @Override
                public void onFinish() {
                    callback.onFinish();
                }

                @Override
                public void onCancel() {
                    callback.onCancel();
                }
            });
        }

        @Override
        public void animateCamera(ICameraUpdate update, int duration, ICancelableCallback callback) {
            huaweiMap.animateCamera(((HuaweiCameraUpdate) update).cameraUpdate, duration, callback == null ? null : new HuaweiMap.CancelableCallback() {
                @Override
                public void onFinish() {
                    callback.onFinish();
                }

                @Override
                public void onCancel() {
                    callback.onCancel();
                }
            });
        }

        @Override
        public void moveCamera(ICameraUpdate update) {
            huaweiMap.moveCamera(((HuaweiCameraUpdate) update).cameraUpdate);
        }

        public final class HuaweiMarker implements IMarker {
            private Marker marker;

            private HuaweiMarker(Marker marker) {
                this.marker = marker;
            }

            @Override
            public Object getTag() {
                return marker.getTag();
            }

            @Override
            public void setTag(Object tag) {
                marker.setTag(tag);
            }

            @Override
            public LatLng getPosition() {
                com.huawei.hms.maps.model.LatLng latLng = marker.getPosition();
                return new LatLng(latLng.latitude, latLng.longitude);
            }

            @Override
            public void setPosition(LatLng latLng) {
                marker.setPosition(new com.huawei.hms.maps.model.LatLng(latLng.latitude, latLng.longitude));
            }

            @Override
            public void setRotation(int rotation) {
                marker.setRotation(rotation);
            }

            @Override
            public void setIcon(Bitmap bitmap) {
                marker.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap));
            }

            @Override
            public void setIcon(int resId) {
                marker.setIcon(BitmapDescriptorFactory.fromResource(resId));
            }

            @Override
            public void remove() {
                marker.remove();
                implToAbsMarkerMap.remove(marker);
            }
        }

        public final class HuaweiCircle implements ICircle {
            private Circle circle;

            private HuaweiCircle(Circle circle) {
                this.circle = circle;
            }

            @Override
            public void setStrokeColor(int color) {
                circle.setStrokeColor(color);
            }

            @Override
            public void setFillColor(int color) {
                circle.setFillColor(color);
            }

            @Override
            public void setRadius(double radius) {
                circle.setRadius(radius);
            }

            @Override
            public double getRadius() {
                return circle.getRadius();
            }

            @Override
            public void setCenter(LatLng latLng) {
                circle.setCenter(new com.huawei.hms.maps.model.LatLng(latLng.latitude, latLng.longitude));
            }

            @Override
            public void remove() {
                circle.remove();
                implToAbsCircleMap.remove(circle);
            }
        }
    }

    public final static class HuaweiProjection implements IProjection {
        private Projection projection;

        private HuaweiProjection(Projection projection) {
            this.projection = projection;
        }

        @Override
        public Point toScreenLocation(LatLng latLng) {
            return projection.toScreenLocation(new com.huawei.hms.maps.model.LatLng(latLng.latitude, latLng.longitude));
        }
    }

    public final static class HuaweiUISettings implements IUISettings {
        private UiSettings uiSettings;

        private HuaweiUISettings(UiSettings settings) {
            uiSettings = settings;
        }

        @Override
        public void setMyLocationButtonEnabled(boolean enabled) {
            uiSettings.setMyLocationButtonEnabled(enabled);
        }

        @Override
        public void setZoomControlsEnabled(boolean enabled) {
            uiSettings.setZoomControlsEnabled(enabled);
        }

        @Override
        public void setCompassEnabled(boolean enabled) {
            uiSettings.setCompassEnabled(enabled);
        }
    }

    public final static class HuaweiCircleOptions implements ICircleOptions {
        private CircleOptions circleOptions;

        private HuaweiCircleOptions() {
            circleOptions = new CircleOptions();
        }

        @Override
        public ICircleOptions center(LatLng latLng) {
            circleOptions.center(new com.huawei.hms.maps.model.LatLng(latLng.latitude, latLng.longitude));
            return this;
        }

        @Override
        public ICircleOptions radius(double radius) {
            circleOptions.radius(radius);
            return this;
        }

        @Override
        public ICircleOptions strokeColor(int color) {
            circleOptions.strokeColor(color);
            return this;
        }

        @Override
        public ICircleOptions fillColor(int color) {
            circleOptions.fillColor(color);
            return this;
        }

        @Override
        public ICircleOptions strokePattern(List<PatternItem> patternItems) {
            List<com.huawei.hms.maps.model.PatternItem> pattern = new ArrayList<>();
            for (PatternItem item : patternItems) {
                if (item instanceof PatternItem.Gap) {
                    pattern.add(new Gap(((PatternItem.Gap) item).length));
                } else if (item instanceof PatternItem.Dash) {
                    pattern.add(new Dash(((PatternItem.Dash) item).length));
                }
            }
            circleOptions.strokePattern(pattern);
            return this;
        }

        @Override
        public ICircleOptions strokeWidth(int width) {
            circleOptions.strokeWidth(width);
            return this;
        }
    }

    public final static class HuaweiMarkerOptions implements IMarkerOptions {
        private MarkerOptions markerOptions;

        private HuaweiMarkerOptions() {
            this.markerOptions = new MarkerOptions();
        }

        @Override
        public IMarkerOptions position(LatLng latLng) {
            markerOptions.position(new com.huawei.hms.maps.model.LatLng(latLng.latitude, latLng.longitude));
            return this;
        }

        @Override
        public IMarkerOptions icon(Bitmap bitmap) {
            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(bitmap));
            return this;
        }

        @Override
        public IMarkerOptions icon(int resId) {
            markerOptions.icon(BitmapDescriptorFactory.fromResource(resId));
            return this;
        }

        @Override
        public IMarkerOptions anchor(float lat, float lng) {
            markerOptions.anchor(lat, lng);
            return this;
        }

        @Override
        public IMarkerOptions title(String title) {
            markerOptions.title(title);
            return this;
        }

        @Override
        public IMarkerOptions snippet(String snippet) {
            markerOptions.snippet(snippet);
            return this;
        }

        @Override
        public IMarkerOptions flat(boolean flat) {
            markerOptions.flat(flat);
            return this;
        }
    }

    public final static class HuaweiLatLngBoundsBuilder implements ILatLngBoundsBuilder {
        private LatLngBounds.Builder builder;

        private HuaweiLatLngBoundsBuilder() {
            builder = new LatLngBounds.Builder();
        }

        @Override
        public ILatLngBoundsBuilder include(LatLng latLng) {
            builder.include(new com.huawei.hms.maps.model.LatLng(latLng.latitude, latLng.longitude));
            return this;
        }

        @Override
        public ILatLngBounds build() {
            return new HuaweiLatLngBounds(builder.build());
        }
    }

    public final static class HuaweiLatLngBounds implements ILatLngBounds {
        private LatLngBounds bounds;

        private HuaweiLatLngBounds(LatLngBounds bounds) {
            this.bounds = bounds;
        }

        @Override
        public LatLng getCenter() {
            com.huawei.hms.maps.model.LatLng latLng = bounds.getCenter();
            return new LatLng(latLng.latitude, latLng.longitude);
        }
    }

    public final static class HuaweiMapView implements IMapView {
        private MapView mapView;

        private ITouchInterceptor dispatchInterceptor;
        private ITouchInterceptor interceptInterceptor;
        private Runnable onLayoutListener;

        private GLSurfaceView glSurfaceView;

        private HuaweiMapView(Context context) {
            mapView = new MapView(context) {
                @Override
                public boolean dispatchTouchEvent(MotionEvent ev) {
                    if (dispatchInterceptor != null) {
                        return dispatchInterceptor.onInterceptTouchEvent(ev, super::dispatchTouchEvent);
                    }
                    return super.dispatchTouchEvent(ev);
                }

                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    if (interceptInterceptor != null) {
                        return interceptInterceptor.onInterceptTouchEvent(ev, super::onInterceptTouchEvent);
                    }
                    return super.onInterceptTouchEvent(ev);
                }

                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    super.onLayout(changed, left, top, right, bottom);
                    if (onLayoutListener != null) {
                        onLayoutListener.run();
                    }
                }
            };
        }

        @Override
        public void setOnDispatchTouchEventInterceptor(ITouchInterceptor touchInterceptor) {
            dispatchInterceptor = touchInterceptor;
        }

        @Override
        public void setOnInterceptTouchEventInterceptor(ITouchInterceptor touchInterceptor) {
            interceptInterceptor = touchInterceptor;
        }

        @Override
        public void setOnLayoutListener(Runnable callback) {
            onLayoutListener = callback;
        }

        @Override
        public View getView() {
            return mapView;
        }

        @Override
        public void getMapAsync(Consumer<IMap> callback) {
            mapView.getMapAsync(huaweiMap -> {
                callback.accept(new HuaweiMapImpl(huaweiMap));
                findGlSurfaceView(mapView);
            });
        }

        @Override
        public GLSurfaceView getGlSurfaceView() {
            return glSurfaceView;
        }

        private void findGlSurfaceView(View v) {
            if (v instanceof GLSurfaceView) {
                glSurfaceView = (GLSurfaceView) v;
            }

            if (v instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) v;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    findGlSurfaceView(vg.getChildAt(i));
                }
            }
        }

        @Override
        public void onPause() {
            mapView.onPause();
        }

        @Override
        public void onResume() {
            mapView.onResume();
        }

        @Override
        public void onCreate(Bundle savedInstance) {
            mapView.onCreate(savedInstance);
        }

        @Override
        public void onDestroy() {
            mapView.onDestroy();
        }

        @Override
        public void onLowMemory() {
            mapView.onLowMemory();
        }
    }

    public final static class HuaweiCameraUpdate implements ICameraUpdate {
        private CameraUpdate cameraUpdate;

        private HuaweiCameraUpdate(CameraUpdate update) {
            this.cameraUpdate = update;
        }
    }

    public final static class HuaweiMapStyleOptions implements IMapStyleOptions {
        private MapStyleOptions mapStyleOptions;

        private HuaweiMapStyleOptions(MapStyleOptions mapStyleOptions) {
            this.mapStyleOptions = mapStyleOptions;
        }
    }
}
