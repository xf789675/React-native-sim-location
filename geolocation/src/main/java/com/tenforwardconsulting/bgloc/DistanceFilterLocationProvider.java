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
import android.database.SQLException;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marianhello.bgloc.AbstractLocationProvider;
import com.marianhello.bgloc.LocationService;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.data.DAOFactory;
import com.marianhello.bgloc.data.LocationDAO;
import com.marianhello.logging.LoggerManager;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;


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

    private UploadLocationInfo uploadLocationInfo;
    private boolean hasConnectivity = true;

    private LocationDAO dao;

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

        try {
            TelephonyManager telManager = (TelephonyManager) locationService.getSystemService(Context.TELEPHONY_SERVICE);

            SubscriptionManager manager = (SubscriptionManager) locationService.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

            List<SubscriptionInfo> subscriptionInfos = manager.getActiveSubscriptionInfoList();

            List<UploadLocationInfo> list = new ArrayList<UploadLocationInfo>();
            for (SubscriptionInfo subInfo : subscriptionInfos) {
                CharSequence carrierName = subInfo.getCarrierName();
                String countryIso = subInfo.getCountryIso();
                int dataRoaming = subInfo.getDataRoaming();  // 1 is enabled ; 0 is disabled
                CharSequence displayName = subInfo.getDisplayName();
                String iccId = subInfo.getIccId();
                int mcc = subInfo.getMcc();
                int mnc = subInfo.getMnc();
                String number = subInfo.getNumber();
                int simSlotIndex = subInfo.getSimSlotIndex();
                int subscriptionId = subInfo.getSubscriptionId();
                boolean networkRoaming = telManager.isNetworkRoaming();
                String deviceId = telManager.getDeviceId(simSlotIndex);

                UploadLocationInfo tInfo = new UploadLocationInfo();
                tInfo.setCarrierName(carrierName.toString());
                tInfo.setCountryIso(countryIso);
                tInfo.setDataRoaming(dataRoaming);
                tInfo.setDeviceId(deviceId);
                tInfo.setDisplayName(displayName.toString());
                tInfo.setIccId(iccId);
                tInfo.setMcc(mcc);
                tInfo.setMnc(mnc);
                tInfo.setNetworkRoaming(networkRoaming);
                tInfo.setNumber(number);
                tInfo.setSimSlotIndex(simSlotIndex);
                tInfo.setSubscriptionId(subscriptionId);
                list.add(tInfo);
            }
            if (list.size() > 0)
                uploadLocationInfo = list.get(0);
            // PowerManager pm = (PowerManager) locationService.getSystemService(Context.POWER_SERVICE);
            // wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            // wakeLock.acquire();

        } catch(Exception e) {
            e.printStackTrace();
        }
        dao = (DAOFactory.createLocationDAO(locationService));

        registerReceiver(connectivityChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    public void startRecording() {
        log.info("Start recording");
        if (config.isDebugging()) {
            Toast.makeText(locationService, "Service running.....", Toast.LENGTH_LONG).show();
        }
        try {
            locationManager.removeUpdates(this);
            // Turn on each provider aggressively for a short period of time
            List<String> matchingProviders = locationManager.getAllProviders();
            for (String provider: matchingProviders) {
                if (!provider.equals(LocationManager.PASSIVE_PROVIDER)) {
                    locationManager.requestLocationUpdates(provider, config.getInterval() / 2, 0, this);
                }
            }
        } catch (SecurityException e) {
            if (config.isDebugging()) {
                Toast.makeText(locationService, "Security exception: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
            log.error("Security exception: {}", e.getMessage());
            this.handleSecurityException(e);
        }
        setPace(false);
    }

    public void stopRecording() {
        log.info("stopRecording not implemented yet");
    }


    private void setPace(boolean isSend) {
        log.info("Setting pace: {}");
        long sendTime = System.currentTimeMillis();
        if(isSend)
            sendTime += config.getInterval();
        try {
            alarmManager.cancel(getLocationPI);
            alarmManager.set(AlarmManager.RTC_WAKEUP, sendTime, getLocationPI);

        } catch (SecurityException e) {
            if (config.isDebugging()) {
                Toast.makeText(locationService, "Security exception: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
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
     * specified
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
//                handleLocation(location);
                AdaptedLocation adaptedLocation = new AdaptedLocation();
                adaptedLocation.setLatitude(location.getLatitude());
                adaptedLocation.setLongitude(location.getLongitude());
                adaptedLocation.setAccuracy(location.getAccuracy());
                adaptedLocation.setAltitude(location.getAltitude());
                adaptedLocation.setBearing(location.getBearing());
                adaptedLocation.setProvider(location.getProvider());
                adaptedLocation.setTime(location.getTime());
                adaptedLocation.setSpeed(location.getSpeed());
                adaptedLocation.setFromMockProvider(location.isFromMockProvider());

                uploadLocationInfo.setLocation(adaptedLocation);
                uploadLocationInfo.setTime(System.currentTimeMillis());
                sendLocation();
            } else {
                if (config.isDebugging()) {
                    Toast.makeText(locationService, "get no location!", Toast.LENGTH_LONG).show();
                }
            }
            setPace(true);
        }
    };

    private class PostLocationTask extends AsyncTask<UploadLocationInfo, Integer, Boolean> {
        @Override
        protected Boolean doInBackground(UploadLocationInfo... locations) {
            ObjectMapper objectMapper = new ObjectMapper();
            for(UploadLocationInfo location : locations) {
                boolean uploadSuccess = true;
                String json = null;
                try {
                    json = objectMapper.writeValueAsString(location);

                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }

                if (hasConnectivity) {
                    int responseCode = 0;
                    try {
                        responseCode = PostHandler.post("http://67.209.181.134:3000/position", json);
                        log.info("Upload position result: " + responseCode);
                    } catch (IOException e) {
                        e.printStackTrace();
                        log.error("Upload position failed: " + e.getMessage());
                        hasConnectivity = isNetworkAvailable();
                        uploadSuccess = false;
                    }

                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        log.info("Upload position failed: " + responseCode);
                        uploadSuccess = false;
                    }
                } else {
                    log.warn("Network connection has lost!");
                    uploadSuccess = false;
                }

                if (!uploadSuccess) {
                    persistLocation(location);
                } else {
                    Long locationId = location.getId();
                    if (locationId != null) {
                        log.info("Delete location: " + locationId);
                        dao.deleteLocation(locationId);
                    }
                }
            }

            return true;
        }
    }

    private void persistLocation (UploadLocationInfo location) {
        try {
            long id = dao.persistLocation(location);
            log.info("Persist location id: " + id);
        } catch (SQLException e) {
            log.error("Failed to persist location: {} error: {}", location.toString(), e.getMessage());
        }
    }

    private void sendLocation() {
        PostLocationTask task = new DistanceFilterLocationProvider.PostLocationTask();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, uploadLocationInfo);
        }
        else {
            task.execute(uploadLocationInfo);
        }
    }

    // resend location which persisted and if success delete it
    private void batchSendLocations() {
        log.info("Batch send location begin!");
        List<BackgroundLocation> bgList = (List<BackgroundLocation>) dao.getAllLocations();
        if(bgList != null && bgList.size() > 0) {
            log.info("Batch send location num" + bgList.size());
            ObjectMapper objectMapper = new ObjectMapper();
            List<UploadLocationInfo> list = new ArrayList<>();
            for(BackgroundLocation location : bgList) {
                String message = location.getMessage();
                if(message != null && message.length() > 0) {
                    try {
                        UploadLocationInfo upLocation = objectMapper.readValue(message, UploadLocationInfo.class);
                        if(upLocation != null) {
                            upLocation.setId(location.getLocationId());
                            list.add(upLocation);
                        }
                    } catch (IOException e) {
                        log.error("Parse string to UploadLocationInfo faild: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            if(list.size() > 0) {
                UploadLocationInfo[] locationArray = new UploadLocationInfo[]{};
                locationArray = list.toArray(locationArray);
                PostLocationTask task = new DistanceFilterLocationProvider.PostLocationTask();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, locationArray);
                }
                else {
                    task.execute(locationArray);
                }
            }
            log.info("Batch send location end!");
        }
    }

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

    private BroadcastReceiver connectivityChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            log.info("status changed");
            hasConnectivity = isNetworkAvailable();
            if(hasConnectivity) {
                batchSendLocations();
            }
            log.warn("Network status changed: " + hasConnectivity);
        }
    };

    private boolean isNetworkAvailable() {
        ConnectivityManager cm =
          (ConnectivityManager) locationService.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }
}
