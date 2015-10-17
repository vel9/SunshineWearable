package com.example.android.sunshine.app;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.example.android.sunshine.app.constants.AppConstants;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class SunshineWatchFaceListenerService extends WearableListenerService {

    //https://www.binpress.com/tutorial/a-guide-to-the-android-wear-message-api/152
    private static final String LOG_TAG = "WatchFaceListener";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if( !messageEvent.getPath().equalsIgnoreCase(AppConstants.PATH_WEATHER_UPDATE) )
            return;

        byte[] rawData = messageEvent.getData();
        DataMap currentWeatherData = DataMap.fromByteArray(rawData);
        String highTemperature = currentWeatherData.getString(AppConstants.KEY_HIGH_TEMPERATURE);
        String lowTemperature = currentWeatherData.getString(AppConstants.KEY_LOW_TEMPERATURE);

        Log.d(LOG_TAG, "high: " + highTemperature);
        Log.d(LOG_TAG, "low: " + lowTemperature);

        // Broadcast message to wearable activity for display
        Intent messageIntent = new Intent(AppConstants.WEATHER_UPDATE_BROADCAST);
        messageIntent.putExtra(AppConstants.KEY_HIGH_TEMPERATURE, highTemperature);
        messageIntent.putExtra(AppConstants.KEY_LOW_TEMPERATURE, lowTemperature);
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
                if (item.getUri().getPath().compareTo(AppConstants.PATH_WEATHER_UPDATE) == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();

                    highTemperature = dataMap.getString(AppConstants.KEY_HIGH_TEMPERATURE);
                    lowTemperature = dataMap.getString(AppConstants.KEY_LOW_TEMPERATURE);

                    Log.d(LOG_TAG, "In Listener Service High: " + highTemperature);
                    Log.d(LOG_TAG, "In Listener Service Low: " + lowTemperature);

                }
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                // DataItem deleted
            }
        }

        // Broadcast message to wearable activity for display
        Intent messageIntent = new Intent(AppConstants.WEATHER_UPDATE_BROADCAST);
        messageIntent.putExtra(AppConstants.KEY_HIGH_TEMPERATURE, highTemperature);
        messageIntent.putExtra(AppConstants.KEY_LOW_TEMPERATURE, lowTemperature);
        LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
    }
}
