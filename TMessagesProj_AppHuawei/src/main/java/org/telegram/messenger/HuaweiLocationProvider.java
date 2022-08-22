package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import com.huawei.hms.api.HuaweiApiClient;
import com.huawei.hms.common.ApiException;
import com.huawei.hms.location.FusedLocationProviderClient;
import com.huawei.hms.location.LocationCallback;
import com.huawei.hms.location.LocationRequest;
import com.huawei.hms.location.LocationResult;
import com.huawei.hms.location.LocationServices;
import com.huawei.hms.location.LocationSettingsRequest;
import com.huawei.hms.location.LocationSettingsStatusCodes;
import com.huawei.hms.location.SettingsClient;

@SuppressLint("MissingPermission")
public class HuaweiLocationProvider implements ILocationServiceProvider {
    private FusedLocationProviderClient locationProviderClient;
    private SettingsClient settingsClient;

    @Override
    public void init(Context context) {
        locationProviderClient = LocationServices.getFusedLocationProviderClient(context);
        settingsClient = new SettingsClient(context);
    }

    @Override
    public ILocationRequest onCreateLocationRequest() {
        return new HuaweiLocationRequest(LocationRequest.create());
    }

    @Override
    public void getLastLocation(Consumer<Location> callback) {
        locationProviderClient.getLastLocation().addOnCompleteListener(task -> {
            if (task.getException() != null) {
                return;
            }
            callback.accept(task.getResult());
        });
    }

    @Override
    public void requestLocationUpdates(ILocationRequest request, ILocationListener locationListener) {
        locationProviderClient.requestLocationUpdates(((HuaweiLocationRequest) request).request, new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                locationListener.onLocationChanged(locationResult.getLastLocation());
            }
        }, Looper.getMainLooper());
    }

    @Override
    public void removeLocationUpdates(ILocationListener locationListener) {
        locationProviderClient.removeLocationUpdates(new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                locationListener.onLocationChanged(locationResult.getLastLocation());
            }
        });
    }

    @Override
    public void checkLocationSettings(ILocationRequest request, Consumer<Integer> callback) {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(((HuaweiLocationRequest) request).request);

        settingsClient.checkLocationSettings(builder.build()).addOnCompleteListener(task -> {
            try {
                task.getResultThrowException(ApiException.class);
                callback.accept(STATUS_SUCCESS);
            } catch (ApiException exception) {
                switch (exception.getStatusCode()) {
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        callback.accept(STATUS_RESOLUTION_REQUIRED);
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        callback.accept(STATUS_SETTINGS_CHANGE_UNAVAILABLE);
                        break;
                }
            }
        });
    }

    @Override
    public IMapApiClient onCreateLocationServicesAPI(Context context, IAPIConnectionCallbacks connectionCallbacks, IAPIOnConnectionFailedListener failedListener) {
        return new HuaweiApiClientImpl(new HuaweiApiClient.Builder(ApplicationLoader.applicationContext)
                .addConnectionCallbacks(new HuaweiApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected() {
                        connectionCallbacks.onConnected(null);
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        connectionCallbacks.onConnectionSuspended(i);
                    }
                })
                .addOnConnectionFailedListener(connectionResult -> failedListener.onConnectionFailed())
                .build());
    }

    @Override
    public boolean checkServices() {
        return HuaweiPushListenerProvider.INSTANCE.hasServices();
    }

    public final static class HuaweiLocationRequest implements ILocationRequest {
        private LocationRequest request;

        private HuaweiLocationRequest(LocationRequest request) {
            this.request = request;
        }

        @Override
        public void setPriority(int priority) {
            int outPriority;
            switch (priority) {
                default:
                case PRIORITY_HIGH_ACCURACY:
                    outPriority = LocationRequest.PRIORITY_HIGH_ACCURACY;
                    break;
                case PRIORITY_BALANCED_POWER_ACCURACY:
                    outPriority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
                    break;
                case PRIORITY_LOW_POWER:
                    outPriority = LocationRequest.PRIORITY_LOW_POWER;
                    break;
                case PRIORITY_NO_POWER:
                    outPriority = LocationRequest.PRIORITY_NO_POWER;
                    break;
            }
            request.setPriority(outPriority);
        }

        @Override
        public void setInterval(long interval) {
            request.setInterval(interval);
        }

        @Override
        public void setFastestInterval(long interval) {
            request.setFastestInterval(interval);
        }
    }

    public final static class HuaweiApiClientImpl implements IMapApiClient {
        private HuaweiApiClient apiClient;

        private HuaweiApiClientImpl(HuaweiApiClient apiClient) {
            this.apiClient = apiClient;
        }

        @Override
        public void connect() {
            apiClient.connect(null);
        }

        @Override
        public void disconnect() {
            apiClient.disconnect();
        }
    }
}
