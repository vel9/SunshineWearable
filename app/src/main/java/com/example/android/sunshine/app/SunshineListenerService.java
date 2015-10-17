package com.example.android.sunshine.app;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.constants.AppConstants;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

public class SunshineListenerService extends WearableListenerService
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    //https://www.binpress.com/tutorial/a-guide-to-the-android-wear-message-api/152
    private static final String LOG_TAG = SunshineListenerService.class.getSimpleName();
    private GoogleApiClient mGoogleApiClient;

    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.TABLE_NAME + "." + WeatherContract.WeatherEntry._ID,
            WeatherContract.WeatherEntry.COLUMN_DATE,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
    };

    static final int COL_WEATHER_ID = 0;
    static final int COL_WEATHER_DATE = 1;
    static final int COL_WEATHER_DESC = 2;
    static final int COL_WEATHER_MAX_TEMP = 3;
    static final int COL_WEATHER_MIN_TEMP = 4;
    static final int COL_LOCATION_SETTING = 5;
    static final int COL_WEATHER_CONDITION_ID = 6;

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

        if(!messageEvent.getPath().equalsIgnoreCase(AppConstants.PATH_REQUEST_WEATHER))
            return;

        Log.d(LOG_TAG, "Message Received from Wearable");

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
        }

        if (!mGoogleApiClient.isConnected()) {
            ConnectionResult connectionResult =
                    mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);

            if (!connectionResult.isSuccess()) {
                Log.e(LOG_TAG, "Failed to connect to GoogleApiClient.");
                return;
            }
        }

        Context context = getApplicationContext();
        Cursor weatherData = getWearableWeatherData(context);
        if (weatherData != null && weatherData.moveToFirst()){
            DataMap currentWeatherData = getWearableWeatherDataMap(weatherData, context);
            sendWearableWeatherData(currentWeatherData);
        } else {
            Log.d(LOG_TAG, "No weather data");
        }

    }

    private DataMap getWearableWeatherDataMap(Cursor weatherData, Context context){

        double high = weatherData.getDouble(COL_WEATHER_MAX_TEMP);
        String highString = Utility.formatTemperature(context, high);

        double low = weatherData.getDouble(COL_WEATHER_MIN_TEMP);
        String lowString = Utility.formatTemperature(context, low);

        DataMap currentWeatherData = new DataMap();
        currentWeatherData.putString(AppConstants.KEY_HIGH_TEMPERATURE, highString);
        currentWeatherData.putString(AppConstants.KEY_LOW_TEMPERATURE, lowString);

        return currentWeatherData;
    }

    private Cursor getWearableWeatherData(Context context){
        // Sort order:  Ascending, by date.
        String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";

        String locationSetting = Utility.getPreferredLocation(context);
        Uri locationUri = WeatherContract.WeatherEntry.buildWearableWeatherLocation(locationSetting, System.currentTimeMillis());
        return getContentResolver().query(locationUri, FORECAST_COLUMNS, null, null,
                sortOrder);
    }

    @Override
    public void onConnected(Bundle bundle) {
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        //Empty
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        //Empty
    }

    private Collection<String> getNodes() {
        HashSet<String> nodeIdSet = new HashSet<>();
        NodeApi.GetConnectedNodesResult nodes =
                Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

        for (Node node : nodes.getNodes()) {
            nodeIdSet.add(node.getId());
        }

        return nodeIdSet;
    }

    private void sendWeatherUpdateMessage(String nodeId, DataMap currentWeatherData) {

        //http://stackoverflow.com/a/18571348
        byte[] rawData = currentWeatherData.toByteArray();

        Wearable.MessageApi.sendMessage(mGoogleApiClient, nodeId, AppConstants.PATH_WEATHER_UPDATE, rawData);
    }

    private class StartWearableWeatherSync extends AsyncTask<DataMap, Void, Void> {

        @Override
        protected Void doInBackground(DataMap... args) {

            DataMap currentWeatherData = args[0];
            Collection<String> nodes = getNodes();
            for (String node : nodes) {
                sendWeatherUpdateMessage(node, currentWeatherData);
            }
            return null;
        }
    }

    public void sendWearableWeatherData(DataMap currentWeatherData) {
        Log.d(LOG_TAG, "Sending updated weather data to Wearable");
        new StartWearableWeatherSync().execute(currentWeatherData);
    }
}
