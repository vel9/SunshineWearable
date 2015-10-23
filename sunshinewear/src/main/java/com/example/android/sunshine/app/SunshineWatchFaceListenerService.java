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
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SunshineWatchFaceListenerService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    private GoogleApiClient mGoogleApiClient;
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
    public void onDataChanged(DataEventBuffer dataEvents) {
        //Reference: https://developer.android.com/training/wearables/data-layer/data-items.html
        final List<DataEvent> events = FreezableUtils.freezeIterable(dataEvents);
        for (DataEvent event : events) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                processDataEvent(event);
            }
        }
    }

    private void processDataEvent(DataEvent event){
        //Reference: https://www.binpress.com/tutorial/a-guide-to-the-android-wear-message-api/152
        DataItem item = event.getDataItem();
        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
        if (item.getUri().getPath().compareTo(AppConstants.PATH_WEATHER_UPDATE) == 0) {
            // get updated weather data from dataMap
            String highTemperature = dataMap.getString(AppConstants.KEY_HIGH_TEMPERATURE);
            String lowTemperature = dataMap.getString(AppConstants.KEY_LOW_TEMPERATURE);
            Asset weatherIconAsset = dataMap.getAsset(AppConstants.KEY_WEATHER_ICON);
            Bitmap weatherIcon = assetToBitmap(weatherIconAsset);
            // Broadcast message to wearable activity for display
            Intent messageIntent = new Intent(AppConstants.WEATHER_UPDATE_BROADCAST);
            // set icon if available
            if (weatherIcon != null)
                messageIntent.putExtra(AppConstants.KEY_WEATHER_ICON, weatherIcon);
            // set high and low temperatures
            messageIntent.putExtra(AppConstants.KEY_HIGH_TEMPERATURE, highTemperature);
            messageIntent.putExtra(AppConstants.KEY_LOW_TEMPERATURE, lowTemperature);
            // broadcast message, the data will be received by the watchface service
            LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
        }
    }

    private Bitmap assetToBitmap(Asset weatherIconAsset){
        Bitmap weatherIcon = null;
        try{
            weatherIcon = loadByteArrayFromInputStream(weatherIconAsset);
        } catch (InterruptedException ie){
            Log.d(LOG_TAG, "Interrupted " + ie.getMessage());
        }
        return weatherIcon;
    }

    private Bitmap loadByteArrayFromInputStream(Asset asset) throws InterruptedException {
        //Reference: http://stackoverflow.com/a/9148954
        BitmapWorker bitmapWorker = new BitmapWorker(asset);
        Thread thread = new Thread(bitmapWorker);
        thread.start();
        //wait until thread executed
        thread.join();
        return bitmapWorker.getBitmap();
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

    /**
     *  The following class combines ideas from a couple of resources (cited in code). Ultimately it uses a callback
     *  to deliver the result of the worker thread tasked with converting Asset to Bitmap.
     */
    public class BitmapWorker implements Runnable {
        private volatile Bitmap bitmap;
        private Asset asset;

        BitmapWorker(Asset asset){
            this.asset = asset;
        }

        @Override
        public void run(){

            if (asset == null) {
                Log.d(LOG_TAG, "Asset is null");
                return;
            }
            // Reference: http://developer.android.com/training/wearables/data-layer/assets.html
            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFaceListenerService.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(SunshineWatchFaceListenerService.this)
                    .addOnConnectionFailedListener(SunshineWatchFaceListenerService.this)
                    .build();

            ConnectionResult connectionResult =
                    mGoogleApiClient.blockingConnect(TIMEOUT_S, TimeUnit.SECONDS);

            if (!connectionResult.isSuccess()) {
                Log.e(LOG_TAG, "Failed to connect to GoogleApiClient.");
                return;
            }
            // convert asset into a file descriptor and block until it's ready
            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                    mGoogleApiClient, asset).await().getInputStream();

            mGoogleApiClient.disconnect();

            if (assetInputStream == null) {
                Log.w(LOG_TAG, "Requested an unknown Asset.");
                return;
            }
            // decode the stream into a bitmap
            bitmap =  BitmapFactory.decodeStream(assetInputStream);
        }

        public Bitmap getBitmap() {
            return bitmap;
        }
    }
}
