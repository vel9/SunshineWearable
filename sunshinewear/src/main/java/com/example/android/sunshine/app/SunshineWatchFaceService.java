/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.example.android.sunshine.app.constants.AppConstants;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFaceService extends CanvasWatchFaceService {

    private static final String LOG_TAG = SunshineWatchFaceService.class.getSimpleName();
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    // request weather data every 30 minutes
    private static final long WEATHER_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(30);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    private static final int MSG_UPDATE_WEATHER = 1;


    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);

        private String mHighTemperature;
        private String mLowTemperature;
        private Bitmap mWeatherIcon;

        boolean mRegisteredTimeZoneReceiver = false;
        boolean mRegisteredWeatherReceiver = false;

        //Paint objects
        Paint mBackgroundPaint;
        Paint mTimeHoursTextPaint;
        Paint mTimeMinutesTextPaint;
        Paint mTimeSecondsTextPaint;
        Paint mTemperatureHighTextPaint;
        Paint mTemperatureLowTextPaint;

        String mLogoText;
        Paint mLogoTextPaint;

        boolean mAmbient;

        Time mTime;

        float mWeatherTextYOffset;
        float mWeatherIconYOffset;

        float mTextHoursXOffset;
        float mTextTimeYOffset;
        float mTextHighTemperatureXOffset;

        float mLogoXOffset;
        float mLogoYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        private GoogleApiClient mGoogleApiClient;
        private String nodeId;
        private static final long CONNECTION_TIME_OUT_MS = 500;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        final BroadcastReceiver mWeatherReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Reference 1: http://android-wear-docs.readthedocs.org
                // Reference 2: http://stackoverflow.com/a/8875292
                String highTemperature = intent.getStringExtra(AppConstants.KEY_HIGH_TEMPERATURE);
                String lowTemperature = intent.getStringExtra(AppConstants.KEY_LOW_TEMPERATURE);
                if (highTemperature != null && lowTemperature != null){
                    mHighTemperature = highTemperature;
                    mLowTemperature = lowTemperature;
                }

                if (intent.hasExtra(AppConstants.KEY_WEATHER_ICON)) {
                    Bitmap weatherIconByteArray = intent.getParcelableExtra(AppConstants.KEY_WEATHER_ICON);
                    if (weatherIconByteArray != null)
                        mWeatherIcon = weatherIconByteArray;
                }
                // redraw watchface with updated data
                invalidate();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SunshineWatchFaceService.this.getResources();

            mWeatherTextYOffset = resources.getDimension(R.dimen.weather_text_y_offset);
            mWeatherIconYOffset = resources.getDimension(R.dimen.weather_icon_y_offset);

            // background color
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.digital_background));

            // time display
            mTimeHoursTextPaint = createTimeHoursTextPaint(resources);
            mTimeMinutesTextPaint = createTimeMinutesTextPaint(resources);
            mTimeSecondsTextPaint = createTimeSecondsTextPaint(resources);

            // logo
            mLogoTextPaint = createLogoPaint(resources);
            mLogoText = resources.getString(R.string.vel9_logo);
            mLogoYOffset = resources.getDimension(R.dimen.vel9_logo_y_offset);

            // temperature display
            mTemperatureHighTextPaint = createTemperatureHighTextPaint(resources);
            mTemperatureLowTextPaint = createTemperatureLowTextPaint(resources);

            // In order to make text in the center, we need adjust its position
            mTextTimeYOffset = (mTimeHoursTextPaint.ascent() + mTimeHoursTextPaint.descent()) / 2;

            mTime = new Time();

            requestWeatherData();
            //sMSP_UPDATE_WEATHER describes process which requests weather data every n time period
            mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_WEATHER);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_WEATHER);
            super.onDestroy();
        }

        private Paint createTimeHoursTextPaint(Resources resources) {
            Paint paint = new Paint();
            paint.setColor(resources.getColor(R.color.time_hours_text));
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            paint.setTextSize(resources.getDimension(R.dimen.time_text_size));
            return paint;
        }

        private Paint createTimeMinutesTextPaint(Resources resources) {
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            paint.setTextSize(resources.getDimension(R.dimen.time_text_size));
            return paint;
        }

        private Paint createTimeSecondsTextPaint(Resources resources) {
            Paint paint = new Paint();
            paint.setColor(resources.getColor(R.color.digital_text));
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            paint.setTextSize(resources.getDimension(R.dimen.time_text_size));
            return paint;
        }

        private Paint createTemperatureHighTextPaint(Resources resources) {
            Paint paint = new Paint();
            paint.setColor(resources.getColor(R.color.temperature_high));
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            paint.setTextSize(resources.getDimension(R.dimen.temperature_text_size));
            return paint;
        }

        private Paint createTemperatureLowTextPaint(Resources resources) {
            Paint paint = new Paint();
            paint.setColor(resources.getColor(R.color.temperature_low));
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            paint.setTextSize(resources.getDimension(R.dimen.temperature_text_size));
            return paint;
        }

        private Paint createLogoPaint(Resources resources) {
            Paint paint = new Paint();
            paint.setColor(resources.getColor(R.color.logo));
            paint.setTypeface(BOLD_TYPEFACE);
            paint.setAntiAlias(true);
            paint.setTextSize(resources.getDimension(R.dimen.temperature_text_size));
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                //if we don't have weather data yet, otherwise rely on Handler to periodically request data
                if (mHighTemperature == null || mLowTemperature == null)
                    requestWeatherData();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                mRegisteredTimeZoneReceiver = true;
                IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
                SunshineWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
            }

            if (!mRegisteredWeatherReceiver) {
                mRegisteredWeatherReceiver = true;
                LocalBroadcastManager.getInstance(SunshineWatchFaceService.this).registerReceiver(mWeatherReceiver,
                        new IntentFilter(AppConstants.WEATHER_UPDATE_BROADCAST));
            }
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                mRegisteredTimeZoneReceiver = false;
                SunshineWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
            }

            // Reference: http://codingfury.com/creating-an-android-wear-watch-face/
            if (!mRegisteredWeatherReceiver) {
                mRegisteredWeatherReceiver = false;
                // Unregister since the activity is about to be closed.
                LocalBroadcastManager.getInstance(SunshineWatchFaceService.this).unregisterReceiver(mWeatherReceiver);
            }
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            mTextHoursXOffset = mTimeHoursTextPaint.measureText(AppConstants.TIME_TEXT_PLACEHOLDER);
            //Reference: http://stackoverflow.com/a/13578847
            mTextHighTemperatureXOffset = mTemperatureHighTextPaint.measureText(AppConstants.TEMPERATURE_TEXT_PLACEHOLDER);
            mLogoXOffset = mLogoTextPaint.measureText(mLogoText)/2;
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    setAntiAlias(!inAmbientMode);
                }
                //if we don't have weather data yet, otherwise rely on Handler to periodically request data
                if (mHighTemperature == null || mLowTemperature == null)
                    requestWeatherData();

                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void setAntiAlias(boolean antiAlias) {
            mTimeHoursTextPaint.setAntiAlias(antiAlias);
            mTimeHoursTextPaint.setAntiAlias(antiAlias);
            mTimeMinutesTextPaint.setAntiAlias(antiAlias);
            mTimeSecondsTextPaint.setAntiAlias(antiAlias);
            mTemperatureHighTextPaint.setAntiAlias(antiAlias);
            mTemperatureLowTextPaint.setAntiAlias(antiAlias);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // get current dimensions
            float width = bounds.width();
            float height = bounds.height();
            float centerX = bounds.width() / 2f;
            float centerY = bounds.height() / 2f;
            // draw background
            canvas.drawRect(0, 0, width, height, mBackgroundPaint);

            /** Time **/
            mTime.setToNow();
            String hours = formatHours(mTime.hour);
            String minutes = String.format("%02d", mTime.minute);

            float timeYOffset = centerY - mTextTimeYOffset;
            float hoursXOffset = centerX - mTextHoursXOffset;
            // set color based on context
            Resources resources = getResources();
            int hoursColor = mAmbient? resources.getColor(R.color.time_hours_text_ambient) : resources.getColor(R.color.time_hours_text);
            mTimeHoursTextPaint.setColor(hoursColor);
            // draw hours
            canvas.drawText(hours,
                            hoursXOffset,
                            timeYOffset,
                            mTimeHoursTextPaint);
            // draw minutes
            canvas.drawText(minutes,
                            centerX,
                            timeYOffset,
                            mTimeMinutesTextPaint);

            /** Logo **/
            canvas.drawText(mLogoText, centerX - mLogoXOffset, mLogoYOffset, mLogoTextPaint);

            /** Temperature **/
            if (mHighTemperature != null && mLowTemperature != null){

                float highTemperatureXOffset = centerX - mTextHighTemperatureXOffset;
                //draw high temperature
                canvas.drawText(mHighTemperature,
                        highTemperatureXOffset,
                        mWeatherTextYOffset,
                        mTemperatureHighTextPaint);
                //draw low temperature
                canvas.drawText(mLowTemperature,
                                centerX,
                        mWeatherTextYOffset,
                                mTemperatureLowTextPaint);
                //draw weatherIcon
                if (mWeatherIcon != null){
                    float weatherIconXOffset = centerX - mWeatherIcon.getWidth()/2;
                    canvas.drawBitmap(mWeatherIcon,
                            weatherIconXOffset,
                            mWeatherIconYOffset,
                            null);
                }
            }
        }

        private String formatHours(int hour){
            if (hour == 12)
                return Integer.toString(hour);
            else
                return String.format("%02d", hour % 12);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private void handleUpdateWeatherMessage() {

            Log.d(LOG_TAG, "in handleUpdateWeatherMessage");
            requestWeatherData();

            long timeMs = System.currentTimeMillis();
            long delayMs = WEATHER_UPDATE_RATE_MS
                        - (timeMs % WEATHER_UPDATE_RATE_MS);
            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_WEATHER, delayMs);
        }

        private void retrieveDeviceNode() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mGoogleApiClient.blockingConnect(CONNECTION_TIME_OUT_MS, TimeUnit.MILLISECONDS);
                    NodeApi.GetConnectedNodesResult result =
                            Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
                    List<Node> nodes = result.getNodes();
                    if (nodes.size() > 0) {
                        nodeId = nodes.get(0).getId();
                    }
                    mGoogleApiClient.disconnect();
                }
            }).start();
        }

        /**
         * Reference: https://github.com/twotoasters/Wear-MessageApiDemo/blob/master/wear/src/main/java/com/twotoasters/messageapidemo/MyActivity.java
         * Sends a message to the connected mobile device.
         */
        private void requestWeatherData() {
            Log.d(LOG_TAG, "requesting weather data");
            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFaceService.this)
                    .addApi(Wearable.API)
                    .build();

            retrieveDeviceNode();

            if (nodeId != null) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mGoogleApiClient.blockingConnect(CONNECTION_TIME_OUT_MS, TimeUnit.MILLISECONDS);
                        Wearable.MessageApi.sendMessage(mGoogleApiClient, nodeId, AppConstants.PATH_REQUEST_WEATHER, null);
                        mGoogleApiClient.disconnect();
                    }
                }).start();
            }
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFaceService.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                    case MSG_UPDATE_WEATHER:
                        engine.handleUpdateWeatherMessage();
                }
            }
        }
    }
}
