package com.example.android.sunshine.app.wearsupport;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.constants.AppConstants;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
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
            // Send Message
            //DataMap currentWeatherData = getWearableWeatherDataMap(weatherData, context);
            //sendWearableWeatherData(currentWeatherData);

            // Send Data Item
            PutDataMapRequest putDataMapReq = getWearableWeatherDataItem(weatherData, context);
            sendWeatherDataItem(putDataMapReq);
        } else {
            Log.d(LOG_TAG, "No weather data");
        }

    }

    private DataMap getWearableWeatherDataMap(Cursor weatherData, Context context){
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
        http://stackoverflow.com/a/4837803
        Bitmap.createScaledBitmap(weatherIcon, 120, 120, false);
        // https://developer.android.com/training/wearables/data-layer/assets.html
        Asset iconAsset = createAssetFromBitmap(weatherIcon);
        // put data into DataMap for shipping to wearable
        DataMap currentWeatherData = new DataMap();
        //currentWeatherData.putAsset(AppConstants.KEY_WEATHER_ICON, iconAsset);
        currentWeatherData.putString(AppConstants.KEY_HIGH_TEMPERATURE, highString);
        currentWeatherData.putString(AppConstants.KEY_LOW_TEMPERATURE, lowString);
        currentWeatherData.putString(AppConstants.KEY_TIMESTAMP, Long.toString(System.currentTimeMillis()));

        return currentWeatherData;
    }

    private PutDataMapRequest getWearableWeatherDataItem(Cursor weatherData, Context context){
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
        Bitmap scaleBitmap = scaleBitmap(weatherIcon, 22, 22);
        Bitmap greyScaleIcon = toGrayscale(scaleBitmap);
        // https://developer.android.com/training/wearables/data-layer/assets.html
        sendWeatherIconDataItem(greyScaleIcon);

        PutDataMapRequest putDataMapReq = PutDataMapRequest.create(AppConstants.PATH_WEATHER_UPDATE);
        DataMap putDataMap = putDataMapReq.getDataMap();
        putDataMap.putString(AppConstants.KEY_HIGH_TEMPERATURE, highString);
        putDataMap.putString(AppConstants.KEY_LOW_TEMPERATURE, lowString);
        putDataMap.putString(AppConstants.KEY_TIMESTAMP, Long.toString(System.currentTimeMillis()));

        return putDataMapReq;
    }

    public static Bitmap scaleBitmap(Bitmap bitmap, int wantedWidth, int wantedHeight) {
        Bitmap output = Bitmap.createBitmap(wantedWidth, wantedHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Matrix m = new Matrix();
        m.setScale((float) wantedWidth / bitmap.getWidth(), (float) wantedHeight / bitmap.getHeight());
        canvas.drawBitmap(bitmap, m, new Paint());

        return output;
    }

    // http://stackoverflow.com/a/3391061
    public static Bitmap toGrayscale(Bitmap bmpOriginal)
    {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }

    /**
     * Builds an {@link com.google.android.gms.wearable.Asset} from a bitmap. The image that we get
     * back from the camera in "data" is a thumbnail size. Typically, your image should not exceed
     * 320x320 and if you want to have zoom and parallax effect in your app, limit the size of your
     * image to 640x400. Resize your image before transferring to your wearable device.
     */
    private static Asset toAsset(Bitmap bitmap) {
        ByteArrayOutputStream byteStream = null;
        try {
            byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
            return Asset.createFromBytes(byteStream.toByteArray());
        } finally {
            if (null != byteStream) {
                try {
                    byteStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }

        }
    }

    private void sendWeatherIconDataItem(Bitmap weatherIcon){

        Asset iconAsset = toAsset(weatherIcon);


        PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/image");

        Log.d(LOG_TAG, "sending weather icon, Asset size: " + (iconAsset.getData().length));
//        putDataMapReq.getDataMap().putAsset("weatherIcon", iconAsset);
//        putDataMapReq.getDataMap().putString(AppConstants.KEY_TIMESTAMP, Long.toString(System.currentTimeMillis()));
//        sendWeatherDataItem(putDataMapReq);

        PutDataMapRequest dataMap = PutDataMapRequest.create("/image");
        dataMap.getDataMap().putAsset("weatherIcon", iconAsset);
        dataMap.getDataMap().putLong("time", new Date().getTime());
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

    private Asset createAssetFromBitmap(Bitmap bitmap) {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
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

    public void sendWeatherDataItem(PutDataMapRequest putDataMapReq){
        Log.d(LOG_TAG, "Sending Weather Data Item");
        new StartWearableWeatherDataItemSync().execute(putDataMapReq);
    }

    private class StartWearableWeatherDataItemSync extends AsyncTask<PutDataMapRequest, Void, Void> {

        @Override
        protected Void doInBackground(PutDataMapRequest... args) {

            PutDataMapRequest putDataMapReq = args[0];
            PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
            PendingResult<DataApi.DataItemResult> pendingResult =
                    Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
            return null;
        }
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
