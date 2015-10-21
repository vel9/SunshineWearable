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

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;


    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);

        private String mHighTemperature;
        private String mLowTemperature;
        private Bitmap mWeatherIcon;

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

                Log.d(LOG_TAG, "has bytestream extra? " + intent.hasExtra(AppConstants.KEY_WEATHER_ICON));
                if (intent.hasExtra(AppConstants.KEY_WEATHER_ICON)) {
                    Bitmap weatherIconByteArray = intent.getParcelableExtra(AppConstants.KEY_WEATHER_ICON);
                    Log.d(LOG_TAG, "is iconByteArray null " + (weatherIconByteArray == null));
                    if (weatherIconByteArray != null)
                        mWeatherIcon = weatherIconByteArray;
                }
            }
        };

        boolean mRegisteredTimeZoneReceiver = false;
        boolean mRegisteredWeatherReceiver = false;

        //Paint objects
        Paint mBackgroundPaint;
        Paint mTimeHoursTextPaint;
        Paint mTimeMinutesTextPaint;
        Paint mTimeSecondsTextPaint;
        Paint mTemperatureHighTextPaint;
        Paint mTemperatureLowTextPaint;

        boolean mAmbient;

        Time mTime;

        float mXOffset;
        float mDigitalTimeDisplayYOffset;
        float mXOffsetHighTemperature;
        float mXOffsetLowTemperature;
        float mXOffsetIcon;
        float mTemperatureDisplayYOffset;
        float mWeatherIconDisplayYOffset;

        float mTextHoursXOffset;
        float mTextTimeYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        private GoogleApiClient mGoogleApiClient;
        private String nodeId;
        private static final long CONNECTION_TIME_OUT_MS = 500;

        private GoogleApiClient getGoogleApiClient(Context context) {
            return new GoogleApiClient.Builder(context)
                    .addApi(Wearable.API)
                    .build();
        }

        /**
         * Initializes the GoogleApiClient and gets the Node ID of the connected device.
         */
        private void initApi() {
            mGoogleApiClient = getGoogleApiClient(SunshineWatchFaceService.this);
            retrieveDeviceNode();
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
         * Sends a message to the connected mobile device, telling it to show a Toast.
         */
        private void requestWeatherData() {

            initApi();

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

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SunshineWatchFaceService.this.getResources();
            mDigitalTimeDisplayYOffset = resources.getDimension(R.dimen.time_display_digital_y_offset);
            mTemperatureDisplayYOffset = resources.getDimension(R.dimen.weather_display_digital_y_offset);

            mXOffsetHighTemperature = resources.getDimension(R.dimen.digital_x_offset_high_temperature_round);
            mXOffsetLowTemperature = resources.getDimension(R.dimen.digital_x_offset_low_temperature_round);
            mXOffsetIcon = resources.getDimension(R.dimen.digital_x_offset_icon_round);

            // background color
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.digital_background));

            // time display
            mTimeHoursTextPaint = createTimeHoursTextPaint(resources);
            mTimeMinutesTextPaint = createTimeMinutesTextPaint(resources);
            mTimeSecondsTextPaint = createTimeSecondsTextPaint(resources);

            // temperature display
            mTemperatureHighTextPaint = createTemperatureHighTextPaint(resources);
            mTemperatureLowTextPaint = createTemperatureLowTextPaint(resources);

            // In order to make text in the center, we need adjust its position
            mTextTimeYOffset = (mTimeHoursTextPaint.ascent() + mTimeHoursTextPaint.descent()) / 2;

            mTemperatureDisplayYOffset = mTextTimeYOffset + mTimeHoursTextPaint.ascent();
            mWeatherIconDisplayYOffset = mTemperatureDisplayYOffset - (mTimeHoursTextPaint.ascent() + mTimeHoursTextPaint.descent());

            mTime = new Time();

            requestWeatherData();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
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

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

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

            //http://codingfury.com/creating-an-android-wear-watch-face/
            if (!mRegisteredWeatherReceiver) {
                mRegisteredWeatherReceiver = false;
                // Unregister since the activity is about to be closed.
                LocalBroadcastManager.getInstance(SunshineWatchFaceService.this).unregisterReceiver(mWeatherReceiver);
            }
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mTextHoursXOffset = mTimeHoursTextPaint.measureText("00");

//            mXOffset = resources.getDimension(isRound
//                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
//            float textSize = resources.getDimension(isRound
//                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
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
                    mTimeHoursTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            float width = bounds.width();
            float height = bounds.height();
            float centerX = width / 2f;
            float centerY = height / 2f;

            canvas.drawRect(0, 0, width, height, mBackgroundPaint);

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%02d:%02d", mTime.hour, mTime.minute)
                    : String.format("%02d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);

            String hours = text.substring(0,2);
            String minutes = text.substring(3,5);
            float timeYOffset = centerY - mTextTimeYOffset;

            canvas.drawText(hours,
                            centerX - mTextHoursXOffset,
                            timeYOffset,
                            mTimeHoursTextPaint);

            canvas.drawText(minutes,
                            centerX,
                            timeYOffset,
                            mTimeMinutesTextPaint);

            float temperatureY = 0;
            if (mHighTemperature != null && mLowTemperature != null){

                Log.d(LOG_TAG, "centerY: " + centerY);
                Log.d(LOG_TAG, "mTemperatureHighTextPaint.ascent: " + mTemperatureHighTextPaint.ascent());
                Log.d(LOG_TAG, "mTemperatureHighTextPaint.descent: " + mTemperatureHighTextPaint.descent());

                float temperatureHeight = (mTemperatureHighTextPaint.descent() - mTemperatureHighTextPaint.ascent());
                temperatureY = bounds.top + temperatureHeight + 16;

                //draw high temperature
                canvas.drawText(mHighTemperature,
                        centerX - mTemperatureHighTextPaint.measureText(mHighTemperature),
                        temperatureY,
                        mTemperatureHighTextPaint);
                //draw low temperature
                canvas.drawText(mLowTemperature,
                                centerX,
                                temperatureY,
                                mTemperatureLowTextPaint);
            }

            if (mWeatherIcon != null)
                canvas.drawBitmap(mWeatherIcon,
                        centerX - mWeatherIcon.getWidth()/2,
                        temperatureY + 4,
                        null);
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
                }
            }
        }
    }
}
