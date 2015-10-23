package com.example.android.sunshine.app.wearsupport;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.constants.AppConstants;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class SunshineListenerService extends WearableListenerService
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    // Reference: https://www.binpress.com/tutorial/a-guide-to-the-android-wear-message-api/152
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
            // Send Data Item with weather data
            sendWeatherData(weatherData, context);
        } else {
            Log.d(LOG_TAG, "No weather data");
        }
    }

    private void sendWeatherData(Cursor weatherData, Context context){
        // get high temperature string
        double high = weatherData.getDouble(COL_WEATHER_MAX_TEMP);
        String highString = Utility.formatTemperature(context, high);
        // get low temperature string
        double low = weatherData.getDouble(COL_WEATHER_MIN_TEMP);
        String lowString = Utility.formatTemperature(context, low);
        // get icon asset
        int weatherId = weatherData.getInt(COL_WEATHER_CONDITION_ID);
        int defaultImage = Utility.getIconResourceForWeatherCondition(weatherId);

        Bitmap weatherIcon = BitmapFactory.decodeResource(context.getResources(), defaultImage);
        Bitmap scaleBitmap = WearUtils.scaleBitmap(weatherIcon, 22, 22);
        Bitmap greyScaleIcon = WearUtils.toGrayscale(scaleBitmap);
        // https://developer.android.com/training/wearables/data-layer/assets.html
        sendWeatherDataItem(greyScaleIcon, highString, lowString);
    }

    private void sendWeatherDataItem(Bitmap weatherIcon, String highString, String lowString){

        Asset iconAsset = WearUtils.toAsset(weatherIcon);
        PutDataMapRequest dataMap = PutDataMapRequest.create(AppConstants.PATH_WEATHER_UPDATE);
        dataMap.getDataMap().putAsset(AppConstants.KEY_WEATHER_ICON, iconAsset);
        dataMap.getDataMap().putString(AppConstants.KEY_HIGH_TEMPERATURE, highString);
        dataMap.getDataMap().putString(AppConstants.KEY_LOW_TEMPERATURE, lowString);
        dataMap.getDataMap().putLong(AppConstants.KEY_TIMESTAMP, new Date().getTime());
        PutDataRequest request = dataMap.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        Log.d(LOG_TAG, "Sending image was successful: " + dataItemResult.getStatus()
                                .isSuccess());
                    }
                });
    }

    /**
     * This method uses existing code from the sunshine app to query for weather data that already
     * exists and is being displayed to the user's in the mobile app.
     *
     * @param context used for retrieving shared perferences
     * @return Cursor containing data for Today's weather
     */
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
}
