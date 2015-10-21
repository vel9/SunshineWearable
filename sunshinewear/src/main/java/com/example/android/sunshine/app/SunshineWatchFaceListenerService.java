package com.example.android.sunshine.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.example.android.sunshine.app.constants.AppConstants;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SunshineWatchFaceListenerService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    GoogleApiClient mGoogleApiClient;
    //https://www.binpress.com/tutorial/a-guide-to-the-android-wear-message-api/152
    private static final String LOG_TAG = "WatchFaceListener";
    private static final int TIMEOUT_S = 30;

    @Override
    public void onCreate() {
        super.onCreate();

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if(!messageEvent.getPath().equalsIgnoreCase(AppConstants.PATH_WEATHER_UPDATE))
            return;

        byte[] weatherData = messageEvent.getData();

        if (weatherData != null && weatherData.length > 0) {

            byte[] rawData = messageEvent.getData();
            DataMap currentWeatherData = DataMap.fromByteArray(rawData);
            String highTemperature = currentWeatherData.getString(AppConstants.KEY_HIGH_TEMPERATURE);
            String lowTemperature = currentWeatherData.getString(AppConstants.KEY_LOW_TEMPERATURE);

            // Broadcast message to wearable activity for display
            Intent messageIntent = new Intent(AppConstants.WEATHER_UPDATE_BROADCAST);
            messageIntent.putExtra(AppConstants.KEY_HIGH_TEMPERATURE, highTemperature);
            messageIntent.putExtra(AppConstants.KEY_LOW_TEMPERATURE, lowTemperature);
            LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
        } else {
            Log.d(LOG_TAG, "No data in message");
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {

        final List<DataEvent> events = FreezableUtils
                .freezeIterable(dataEvents);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        ConnectionResult connectionResult =
                mGoogleApiClient.blockingConnect(TIMEOUT_S, TimeUnit.SECONDS);

        if (!connectionResult.isSuccess()) {
            Log.e(LOG_TAG, "Failed to connect to GoogleApiClient.");
            return;
        }

        //https://developer.android.com/training/wearables/data-layer/data-items.html
        for (DataEvent event : events) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                // DataItem changed
                DataItem item = event.getDataItem();
                DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                Intent messageIntent = null;
                if (item.getUri().getPath().compareTo(AppConstants.PATH_WEATHER_UPDATE) == 0) {

                    String highTemperature = dataMap.getString(AppConstants.KEY_HIGH_TEMPERATURE);
                    String lowTemperature = dataMap.getString(AppConstants.KEY_LOW_TEMPERATURE);

                    Log.d(LOG_TAG, "high: " + highTemperature);
                    Log.d(LOG_TAG, "low: " + lowTemperature);

                    // Broadcast message to wearable activity for display
                    messageIntent = new Intent(AppConstants.WEATHER_UPDATE_BROADCAST);
                    messageIntent.putExtra(AppConstants.KEY_HIGH_TEMPERATURE, highTemperature);
                    messageIntent.putExtra(AppConstants.KEY_LOW_TEMPERATURE, lowTemperature);
                    
                } else if (item.getUri().getPath().compareTo("/image") == 0){

                    DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                    Asset weatherIconAsset = dataMapItem.getDataMap()
                            .getAsset("weatherIcon");
                    Log.d(LOG_TAG, "decoding image");
                    //Asset weatherIconAsset = dataMap.getAsset("weatherIcon");

                    Bitmap weatherIconByteStream = null;
                    try{
                        weatherIconByteStream = loadByteArrayFromInputStream(weatherIconAsset);
                    } catch (IOException ioe){
                        Log.d(LOG_TAG, "Error decoding asset: ");
                    }

                    Log.d(LOG_TAG, "decoding image, is bytestream null: " + (weatherIconByteStream == null));

                    messageIntent = new Intent(AppConstants.WEATHER_UPDATE_BROADCAST);
                    if (weatherIconByteStream != null)
                        messageIntent.putExtra(AppConstants.KEY_WEATHER_ICON, weatherIconByteStream);
                }

                if (messageIntent != null)
                    LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
            }
        }
    }

    //http://developer.android.com/training/wearables/data-layer/assets.html
    public Bitmap loadByteArrayFromInputStream(Asset asset) throws IOException {
        if (asset == null) {
            Log.d(LOG_TAG, "Asset is null");
            return null;
        }

        // convert asset into a file descriptor and block until it's ready
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                mGoogleApiClient, asset).await().getInputStream();
        mGoogleApiClient.disconnect();

        if (assetInputStream == null) {
            Log.w(LOG_TAG, "Requested an unknown Asset.");
            return null;
        }
        // decode the stream into a bitmap
        return BitmapFactory.decodeStream(assetInputStream);
    }
}
