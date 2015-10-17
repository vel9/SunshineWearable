package com.example.android.sunshine.app;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class SunshineWatchFaceListenerService extends WearableListenerService {

    //https://www.binpress.com/tutorial/a-guide-to-the-android-wear-message-api/152
    private static final String SEND_WEATHER_UPDATE_PATH = "/weather-update";
    private static final String LOG_TAG = "LISTENER";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if( !messageEvent.getPath().equalsIgnoreCase(SEND_WEATHER_UPDATE_PATH) )
            return;

        byte[] rawData = messageEvent.getData();
        DataMap currentWeatherData = DataMap.fromByteArray(rawData);
        String highTemperature = currentWeatherData.getString("highTemperature");
        String lowTemperature = currentWeatherData.getString("lowTemperature");

        Log.d(LOG_TAG, "message received" + highTemperature + " - " + lowTemperature);

        // Broadcast message to wearable activity for display
        Intent messageIntent = new Intent("weather-update");
        messageIntent.putExtra("highTemperature", highTemperature);
        messageIntent.putExtra("lowTemperature", lowTemperature);
        LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        //https://developer.android.com/training/wearables/data-layer/data-items.html
        String highTemperature = "";
        String lowTemperature = "";
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                // DataItem changed
                DataItem item = event.getDataItem();
                if (item.getUri().getPath().compareTo("/weather-update") == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();

                    highTemperature = dataMap.getString("highTemperature");
                    lowTemperature = dataMap.getString("lowTemperature");
                    Asset weatherIcon = dataMap.getAsset("weatherIcon");

                    Log.d(LOG_TAG, "In Listener Service High: " + highTemperature);
                    Log.d(LOG_TAG, "In Listener Service Low: " + lowTemperature);

                }
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                // DataItem deleted
            }
        }

        // Broadcast message to wearable activity for display
        Intent messageIntent = new Intent("weather-update");
        messageIntent.putExtra("highTemperature", highTemperature);
        messageIntent.putExtra("lowTemperature", lowTemperature);
        LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
    }
}
