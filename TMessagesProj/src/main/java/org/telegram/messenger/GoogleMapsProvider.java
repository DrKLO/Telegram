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

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.Dash;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GoogleMapsProvider implements IMapsProvider {

    @Override
    public void initializeMaps(Context context) {
        MapsInitializer.initialize(context);
    }

    @Override
    public IMapView onCreateMapView(Context context) {
        return new GoogleMapView(context);
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLng(LatLng latLng) {
        return new GoogleCameraUpdate(CameraUpdateFactory.newLatLng(new com.google.android.gms.maps.model.LatLng(latLng.latitude, latLng.longitude)));
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLngZoom(LatLng latLng, float zoom) {
        return new GoogleCameraUpdate(CameraUpdateFactory.newLatLngZoom(new com.google.android.gms.maps.model.LatLng(latLng.latitude, latLng.longitude), zoom));
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLngBounds(ILatLngBounds bounds, int padding) {
        return new GoogleCameraUpdate(CameraUpdateFactory.newLatLngBounds(((GoogleLatLngBounds) bounds).bounds, padding));
    }

    @Override
    public ILatLngBoundsBuilder onCreateLatLngBoundsBuilder() {
        return new GoogleLatLngBoundsBuilder();
    }

    @Override
    public IMapStyleOptions loadRawResourceStyle(Context context, int resId) {
        return new GoogleMapStyleOptions(MapStyleOptions.loadRawResourceStyle(context, resId));
    }

    @Override
    public String getMapsAppPackageName() {
        return "com.google.android.apps.maps";
    }

    @Override
    public int getInstallMapsString() {
        return R.string.InstallGoogleMaps;
    }

    @Override
    public IMarkerOptions onCreateMarkerOptions() {
        return new GoogleMarkerOptions();
    }

    @Override
    public ICircleOptions onCreateCircleOptions() {
        return new GoogleCircleOptions();
    }

    public final static class GoogleMapImpl implements IMap {
        private GoogleMap googleMap;

        private Map<Marker, GoogleMarker> implToAbsMarkerMap = new HashMap<>();
        private Map<Circle, GoogleCircle> implToAbsCircleMap = new HashMap<>();

        private GoogleMapImpl(GoogleMap googleMap) {
            this.googleMap = googleMap;
        }

        @Override
        public void setMapType(int mapType) {
            switch (mapType) {
                case MAP_TYPE_NORMAL:
                    googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    break;
                case MAP_TYPE_SATELLITE:
                    googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                    break;
                case MAP_TYPE_HYBRID:
                    googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                    break;
            }
        }

        @Override
        public float getMaxZoomLevel() {
            return googleMap.getMaxZoomLevel();
        }

        @Override
        public float getMinZoomLevel() {
            return googleMap.getMinZoomLevel();
        }

        @SuppressLint("MissingPermission")
        @Override
        public void setMyLocationEnabled(boolean enabled) {
            googleMap.setMyLocationEnabled(enabled);
        }

        @Override
        public IUISettings getUiSettings() {
            return new GoogleUISettings(googleMap.getUiSettings());
        }

        @Override
        public void setOnCameraMoveStartedListener(OnCameraMoveStartedListener onCameraMoveStartedListener) {
            googleMap.setOnCameraMoveStartedListener(reason -> {
                int outReason;
                switch (reason) {
                    default:
                    case GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE:
                        outReason = OnCameraMoveStartedListener.REASON_GESTURE;
                        break;
                    case GoogleMap.OnCameraMoveStartedListener.REASON_API_ANIMATION:
                        outReason = OnCameraMoveStartedListener.REASON_API_ANIMATION;
                        break;
                    case GoogleMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION:
                        outReason = OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION;
                        break;
                }
                onCameraMoveStartedListener.onCameraMoveStarted(outReason);
            });
        }

        @Override
        public void setOnCameraIdleListener(Runnable callback) {
            googleMap.setOnCameraIdleListener(callback::run);
        }

        @Override
        public CameraPosition getCameraPosition() {
            com.google.android.gms.maps.model.CameraPosition pos = googleMap.getCameraPosition();
            return new CameraPosition(new LatLng(pos.target.latitude, pos.target.longitude), pos.zoom);
        }

        @Override
        public void setOnMapLoadedCallback(Runnable callback) {
            googleMap.setOnMapLoadedCallback(callback::run);
        }

        @Override
        public IProjection getProjection() {
            return new GoogleProjection(googleMap.getProjection());
        }

        @Override
        public void setPadding(int left, int top, int right, int bottom) {
            googleMap.setPadding(left, top, right, bottom);
        }

        @Override
        public void setMapStyle(IMapStyleOptions style) {
            googleMap.setMapStyle(style == null ? null : ((GoogleMapStyleOptions) style).mapStyleOptions);
        }

        @Override
        public IMarker addMarker(IMarkerOptions markerOptions) {
            Marker impl = googleMap.addMarker(((GoogleMarkerOptions) markerOptions).markerOptions);
            GoogleMarker abs = new GoogleMarker(impl);
            implToAbsMarkerMap.put(impl, abs);
            return abs;
        }

        @Override
        public ICircle addCircle(ICircleOptions circleOptions) {
            Circle impl = googleMap.addCircle(((GoogleCircleOptions) circleOptions).circleOptions);
            GoogleCircle abs = new GoogleCircle(impl);
            implToAbsCircleMap.put(impl, abs);
            return abs;
        }

        @Override
        public void setOnMyLocationChangeListener(Consumer<Location> callback) {
            googleMap.setOnMyLocationChangeListener(callback::accept);
        }

        @Override
        public void setOnMarkerClickListener(OnMarkerClickListener markerClickListener) {
            googleMap.setOnMarkerClickListener(marker -> {
                GoogleMarker abs = implToAbsMarkerMap.get(marker);
                if (abs == null) {
                    abs = new GoogleMarker(marker);
                    implToAbsMarkerMap.put(marker, abs);
                }
                return markerClickListener.onClick(abs);
            });
        }

        @Override
        public void setOnCameraMoveListener(Runnable callback) {
            googleMap.setOnCameraMoveListener(callback::run);
        }

        @Override
        public void animateCamera(ICameraUpdate update) {
            googleMap.animateCamera(((GoogleCameraUpdate) update).cameraUpdate);
        }

        @Override
        public void animateCamera(ICameraUpdate update, ICancelableCallback callback) {
            googleMap.animateCamera(((GoogleCameraUpdate) update).cameraUpdate, callback == null ? null : new GoogleMap.CancelableCallback() {
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
            googleMap.animateCamera(((GoogleCameraUpdate) update).cameraUpdate, duration, callback == null ? null : new GoogleMap.CancelableCallback() {
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
            googleMap.moveCamera(((GoogleCameraUpdate) update).cameraUpdate);
        }

        public final class GoogleMarker implements IMarker {
            private Marker marker;

            private GoogleMarker(Marker marker) {
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
                com.google.android.gms.maps.model.LatLng latLng = marker.getPosition();
                return new LatLng(latLng.latitude, latLng.longitude);
            }

            @Override
            public void setPosition(LatLng latLng) {
                marker.setPosition(new com.google.android.gms.maps.model.LatLng(latLng.latitude, latLng.longitude));
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

        public final class GoogleCircle implements ICircle {
            private Circle circle;

            private GoogleCircle(Circle circle) {
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
                circle.setCenter(new com.google.android.gms.maps.model.LatLng(latLng.latitude, latLng.longitude));
            }

            @Override
            public void remove() {
                circle.remove();
                implToAbsCircleMap.remove(circle);
            }
        }
    }

    public final static class GoogleProjection implements IProjection {
        private Projection projection;

        private GoogleProjection(Projection projection) {
            this.projection = projection;
        }

        @Override
        public Point toScreenLocation(LatLng latLng) {
            return projection.toScreenLocation(new com.google.android.gms.maps.model.LatLng(latLng.latitude, latLng.longitude));
        }
    }

    public final static class GoogleUISettings implements IUISettings {
        private UiSettings uiSettings;

        private GoogleUISettings(UiSettings settings) {
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

    public final static class GoogleCircleOptions implements ICircleOptions {
        private CircleOptions circleOptions;

        private GoogleCircleOptions() {
            circleOptions = new CircleOptions();
        }

        @Override
        public ICircleOptions center(LatLng latLng) {
            circleOptions.center(new com.google.android.gms.maps.model.LatLng(latLng.latitude, latLng.longitude));
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
            List<com.google.android.gms.maps.model.PatternItem> pattern = new ArrayList<>();
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

    public final static class GoogleMarkerOptions implements IMarkerOptions {
        private MarkerOptions markerOptions;

        private GoogleMarkerOptions() {
            this.markerOptions = new MarkerOptions();
        }

        @Override
        public IMarkerOptions position(LatLng latLng) {
            markerOptions.position(new com.google.android.gms.maps.model.LatLng(latLng.latitude, latLng.longitude));
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

    public final static class GoogleLatLngBoundsBuilder implements ILatLngBoundsBuilder {
        private LatLngBounds.Builder builder;

        private GoogleLatLngBoundsBuilder() {
            builder = new LatLngBounds.Builder();
        }

        @Override
        public ILatLngBoundsBuilder include(LatLng latLng) {
            builder.include(new com.google.android.gms.maps.model.LatLng(latLng.latitude, latLng.longitude));
            return this;
        }

        @Override
        public ILatLngBounds build() {
            return new GoogleLatLngBounds(builder.build());
        }
    }

    public final static class GoogleLatLngBounds implements ILatLngBounds {
        private LatLngBounds bounds;

        private GoogleLatLngBounds(LatLngBounds bounds) {
            this.bounds = bounds;
        }

        @Override
        public LatLng getCenter() {
            com.google.android.gms.maps.model.LatLng latLng = bounds.getCenter();
            return new LatLng(latLng.latitude, latLng.longitude);
        }
    }

    public final static class GoogleMapView implements IMapView {
        private MapView mapView;

        private ITouchInterceptor dispatchInterceptor;
        private ITouchInterceptor interceptInterceptor;
        private Runnable onLayoutListener;

        private GLSurfaceView glSurfaceView;

        private GoogleMapView(Context context) {
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
            mapView.getMapAsync(googleMap -> {
                callback.accept(new GoogleMapImpl(googleMap));
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

    public final static class GoogleCameraUpdate implements ICameraUpdate {
        private CameraUpdate cameraUpdate;

        private GoogleCameraUpdate(CameraUpdate update) {
            this.cameraUpdate = update;
        }
    }

    public final static class GoogleMapStyleOptions implements IMapStyleOptions {
        private MapStyleOptions mapStyleOptions;

        private GoogleMapStyleOptions(MapStyleOptions mapStyleOptions) {
            this.mapStyleOptions = mapStyleOptions;
        }
    }
}
