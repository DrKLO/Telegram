package org.telegram.messenger;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.core.util.Consumer;

@Keep
public interface ILocationServiceProvider {
    int PRIORITY_HIGH_ACCURACY = 0,
            PRIORITY_BALANCED_POWER_ACCURACY = 1,
            PRIORITY_LOW_POWER = 2,
            PRIORITY_NO_POWER = 3;

    int STATUS_SUCCESS = 0,
        STATUS_RESOLUTION_REQUIRED = 1,
        STATUS_SETTINGS_CHANGE_UNAVAILABLE = 2;

    void init(Context context);
    ILocationRequest onCreateLocationRequest();
    IMapApiClient onCreateLocationServicesAPI(Context context, IAPIConnectionCallbacks connectionCallbacks, IAPIOnConnectionFailedListener failedListener);
    boolean checkServices();
    void getLastLocation(Consumer<Location> callback);
    void requestLocationUpdates(ILocationRequest request, ILocationListener locationListener);
    void removeLocationUpdates(ILocationListener locationListener);
    void checkLocationSettings(ILocationRequest request, Consumer<Integer> callback);

    interface ILocationRequest {
        void setPriority(int priority);
        void setInterval(long interval);
        void setFastestInterval(long interval);
    }

    interface IMapApiClient {
        void connect();
        void disconnect();
    }

    interface IAPIConnectionCallbacks {
        void onConnected(Bundle bundle);
        void onConnectionSuspended(int i);
    }

    interface IAPIOnConnectionFailedListener {
        void onConnectionFailed();
    }

    interface ILocationListener {
        void onLocationChanged(Location location);
    }
}
