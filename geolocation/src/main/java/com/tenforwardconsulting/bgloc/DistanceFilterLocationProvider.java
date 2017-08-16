/*
According to apache license

This is fork of christocracy cordova-plugin-background-geolocation plugin
https://github.com/christocracy/cordova-plugin-background-geolocation

Differences to original version:

1. location is not persisted to db anymore, but broadcasted using intents instead
*/

package com.tenforwardconsulting.bgloc;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.widget.Toast;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marianhello.bgloc.AbstractLocationProvider;
import com.marianhello.bgloc.LocationService;
import com.marianhello.logging.LoggerManager;

import java.io.IOException;
import java.util.List;

import static java.lang.Math.abs;
import static java.lang.Math.pow;
import static java.lang.Math.round;


public class DistanceFilterLocationProvider extends AbstractLocationProvider implements LocationListener {
    private static final String TAG = DistanceFilterLocationProvider.class.getSimpleName();
    private static final String P_NAME = "com.tenforwardconsulting.cordova.bgloc";

    private static final String GET_LOCATION_ACTION             = P_NAME + ".GET_LOCATION_ACTION";
    private static final String STATIONARY_REGION_ACTION        = P_NAME + ".STATIONARY_REGION_ACTION";
    private static final String STATIONARY_ALARM_ACTION         = P_NAME + ".STATIONARY_ALARM_ACTION";
    private static final String SINGLE_LOCATION_UPDATE_ACTION   = P_NAME + ".SINGLE_LOCATION_UPDATE_ACTION";
    private static final String STATIONARY_LOCATION_MONITOR_ACTION = P_NAME + ".STATIONARY_LOCATION_MONITOR_ACTION";

    private static final long STATIONARY_TIMEOUT                                = 5 * 1000 * 60;    // 5 minutes.
    private static final long STATIONARY_LOCATION_POLLING_INTERVAL_LAZY         = 3 * 1000 * 60;    // 3 minutes.
    private static final long STATIONARY_LOCATION_POLLING_INTERVAL_AGGRESSIVE   = 1 * 1000 * 60;    // 1 minute.
    private static final Integer MAX_STATIONARY_ACQUISITION_ATTEMPTS = 5;
    private static final Integer MAX_SPEED_ACQUISITION_ATTEMPTS = 3;

    private Boolean isMoving = false;
    private Boolean isAcquiringStationaryLocation = false;
    private Boolean isAcquiringSpeed = false;
    private Integer locationAcquisitionAttempts = 0;

    // private PowerManager.WakeLock wakeLock;

    private Location stationaryLocation;
    private long stationaryLocationPollingInterval;
    private Integer scaledDistanceFilter;
    private PendingIntent getLocationPI;

    private String activity;
    private Criteria criteria;

    private LocationManager locationManager;
    private AlarmManager alarmManager;
    private NotificationManager notificationManager;

    private org.slf4j.Logger log;

    public DistanceFilterLocationProvider(LocationService context) {
        super(context);
        PROVIDER_ID = 0;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        log = LoggerManager.getLogger(DistanceFilterLocationProvider.class);
        log.info("Creating DistanceFilterLocationProvider");

        locationManager = (LocationManager) locationService.getSystemService(Context.LOCATION_SERVICE);
        alarmManager = (AlarmManager) locationService.getSystemService(Context.ALARM_SERVICE);

        // get location PI
        getLocationPI = PendingIntent.getBroadcast(locationService, 0, new Intent(GET_LOCATION_ACTION), 0);
        registerReceiver(getLocationReceiver, new IntentFilter(GET_LOCATION_ACTION));

        // PowerManager pm = (PowerManager) locationService.getSystemService(Context.POWER_SERVICE);
        // wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        // wakeLock.acquire();
    }

    public void startRecording() {
        log.info("Start recording");
        scaledDistanceFilter = config.getDistanceFilter();
        setPace(false);
    }

    public void stopRecording() {
        log.info("stopRecording not implemented yet");
    }

    /**
     *
     * @param value set true to engage "aggressive", battery-consuming tracking, false for stationary-region tracking
     */
    private void setPace(Boolean value) {
        log.info("Setting pace: {}", value);

        try {
            locationManager.removeUpdates(this);

            // Turn on each provider aggressively for a short period of time
            List<String> matchingProviders = locationManager.getAllProviders();
            for (String provider: matchingProviders) {
                if (!provider.equals(LocationManager.PASSIVE_PROVIDER)) {
                    locationManager.requestLocationUpdates(provider, config.getInterval() / 2, 0, this);
                }
            }
            alarmManager.cancel(getLocationPI);
            alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + config.getInterval(), getLocationPI);

        } catch (SecurityException e) {
            log.error("Security exception: {}", e.getMessage());
            this.handleSecurityException(e);
        }
    }

    /**
    * Translates a number representing desired accuracy of Geolocation system from set [0, 10, 100, 1000].
    * 0:  most aggressive, most accurate, worst battery drain
    * 1000:  least aggressive, least accurate, best for battery.
    */
    private Integer translateDesiredAccuracy(Integer accuracy) {
        switch (accuracy) {
            case 1000:
                accuracy = Criteria.ACCURACY_LOW;
                break;
            case 100:
                accuracy = Criteria.ACCURACY_MEDIUM;
                break;
            case 10:
                accuracy = Criteria.ACCURACY_HIGH;
                break;
            case 0:
                accuracy = Criteria.ACCURACY_HIGH;
                break;
            default:
                accuracy = Criteria.ACCURACY_MEDIUM;
        }
        return accuracy;
    }

    /**
     * Returns the most accurate and timely previously detected location.
     * Where the last result is beyond the specified maximum distance or
     * latency a one-off location update is returned via the {@link LocationListener}
     * specified in {@link setChangedLocationListener}.
     * @param minTime Minimum time required between location updates.
     * @return The most accurate and / or timely previously detected location.
     */
    public Location getLastBestLocation() {
        Location bestResult = null;
        String bestProvider = null;
        float bestAccuracy = Float.MAX_VALUE;
        long bestTime = Long.MIN_VALUE;
        long minTime = System.currentTimeMillis() - config.getInterval();

        log.info("Fetching last best location: radius={} minTime={}", config.getStationaryRadius(), minTime);
        if (config.isDebugging()) {
            Toast.makeText(locationService, "11-Fetching last best location: ", Toast.LENGTH_LONG).show();
        }
        try {
            // Iterate through all the providers on the system, keeping
            // note of the most accurate result within the acceptable time limit.
            // If no result is found within maxTime, return the newest Location.
            List<String> matchingProviders = locationManager.getAllProviders();
            for (String provider: matchingProviders) {
                if (provider.equals(LocationManager.PASSIVE_PROVIDER))
                    continue;
                Location location = locationManager.getLastKnownLocation(provider);
                if (location != null) {
                    
                    log.debug("Test provider={} lat={} lon={} acy={} v={}m/s time={}", provider, location.getLatitude(), location.getLongitude(), location.getAccuracy(), location.getSpeed(), location.getTime());
                    float accuracy = location.getAccuracy();
                    long time = location.getTime();
                    if (config.isDebugging()) {
                        Toast.makeText(locationService, "Provider is: " + provider + ", time is: " + (time - minTime) + ", accuracy is: " + (accuracy) + ", bestAccuracy is: " + bestAccuracy, Toast.LENGTH_LONG).show();
                    }
                    if ((time > minTime && accuracy < bestAccuracy)) {
                        bestProvider = provider;
                        bestResult = location;
                        bestAccuracy = accuracy;
                        bestTime = time;
                    }
                }
            }

            if (bestResult != null) {
                log.debug("Best result found provider={} lat={} lon={} acy={} v={}m/s time={}", bestProvider, bestResult.getLatitude(), bestResult.getLongitude(), bestResult.getAccuracy(), bestResult.getSpeed(), bestResult.getTime());
            }
        } catch (SecurityException e) {
            log.error("Security exception: {}", e.getMessage());
            this.handleSecurityException(e);
        }

        return bestResult;
    }

    public void onLocationChanged(Location location) {
        log.debug("location changed: ", location);
    }


    private BroadcastReceiver getLocationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            log.info("get location action fired");
            if (config.isDebugging()) {
                Toast.makeText(locationService, "get location action fired", Toast.LENGTH_LONG).show();
            }
            Location location = getLastBestLocation();
            if(location != null) {
                //handleLocation(location);
                ObjectMapper objectMapper = new ObjectMapper();
                String json = null;
                try {
                    json = objectMapper.writeValueAsString(location);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
                try {
                    PostHandler.post("http://67.209.181.134:3000/position", json);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                if (config.isDebugging()) {
                    Toast.makeText(locationService, "get no location!", Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub
        log.debug("Provider {} was disabled", provider);
    }

    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub
        log.debug("Provider {} was enabled", provider);
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub
        log.debug("Provider {} status changed: {}", provider, status);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        log.info("Destroying DistanceFilterLocationProvider");

        try {
            locationManager.removeUpdates(this);
            // locationManager.removeProximityAlert(stationaryRegionPI);
        } catch (SecurityException e) {
            //noop
        }
        alarmManager.cancel(getLocationPI);

        unregisterReceiver(getLocationReceiver);

        // wakeLock.release();
    }
}
