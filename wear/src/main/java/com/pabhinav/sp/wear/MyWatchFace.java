package com.pabhinav.sp.wear;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
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

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener {

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint, mMaxTempPaint, mMinTempPaint;
        boolean mAmbient;
        Time mTime;
        GoogleApiClient googleApiClient;

        private Bitmap mWeatherImage;
        private String mWeatherMaxTemp;
        private String mWeatherMinTemp;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = MyWatchFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mMaxTempPaint = createTextPaint(resources.getColor(R.color.digital_max_temp));
            mMinTempPaint = createTextPaint(resources.getColor(R.color.digital_min_temp));

            mTime = new Time();

            googleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();

            setInitialWeatherRendering();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

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
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();

            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            float highTempSize = resources.getDimension(isRound
                    ? R.dimen.digital_high_temp_size_round : R.dimen.digital_high_temp_size);

            float lowTempSize = resources.getDimension(isRound
                    ? R.dimen.digital_low_temp_size_round : R.dimen.digital_low_temp_size);

            mTextPaint.setTextSize(textSize);
            mMaxTempPaint.setTextSize(highTempSize);
            mMinTempPaint.setTextSize(lowTempSize);

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
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = MyWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }


            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = mAmbient ? String.format("%d:%02d", mTime.hour, mTime.minute) : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);

            Rect textBounds = new Rect();
            mTextPaint.getTextBounds(text,0,text.length(),textBounds);

            /** Center the digital time, if in ambient mode **/
            float xTime = bounds.width()/2  - textBounds.width()/2;
            float yTime = mAmbient ? (bounds.height()/2 - textBounds.height()/2)  : (bounds.height()/4 - textBounds.height()/2);

            canvas.drawText(text, xTime, yTime, mTextPaint);


            /**
             * If not in ambient mode,
             * draw weather image, min and max temp for today.
             */
            if(!mAmbient){

                Bitmap resizedBitmap = Bitmap.createScaledBitmap(mWeatherImage, 50, 50, true);
                canvas.drawBitmap(resizedBitmap, bounds.width() / 2 - resizedBitmap.getWidth() - 16, bounds.height() / 2 + 8, new Paint());

                String maxTempString = mWeatherMaxTemp + "\u00b0";
                Rect maxTempTextBounds = new Rect();
                mMaxTempPaint.getTextBounds(maxTempString, 0, maxTempString.length(), maxTempTextBounds);
                canvas.drawText(maxTempString, bounds.width()/2 + 16, bounds.height()/2 + 8, mMaxTempPaint);

                String minTempString = mWeatherMinTemp + "\u00b0";
                canvas.drawText(minTempString, bounds.width()/2 + 16, bounds.height()/2 + 8 + maxTempTextBounds.height() + 8, mMinTempPaint);
            }
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

        @Override
        public void onConnected(Bundle bundle) {
            Wearable.DataApi.addListener(googleApiClient, Engine.this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            // Nothing to be done
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            // Nothing to be done
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {

            /** Since this method runs on background thread, need to update ui on main thread.  **/
            for (DataEvent dataEvent : dataEventBuffer) {

                DataItem dataItem = dataEvent.getDataItem();

                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    DataItem item = dataEvent.getDataItem();
                    if (dataItem.getUri().getPath().equals("/wearable/weather")) {

                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                        int weatherId = dataMap.getInt("SUNSHINE_WEATHER_ID");
                        if (weatherId != 0) {
                            mWeatherImage = BitmapFactory.decodeResource(getResources(), getArtResourceForWeatherCondition(weatherId));
                        }
                        if (mWeatherImage == null) {
                            mWeatherImage = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
                        }
                        mWeatherMaxTemp = dataMap.getString("SUNSHINE_MAX_TEMP");
                        mWeatherMinTemp = dataMap.getString("SUNSHINE_MIN_TEMP");

                        /** Context switching **/
                        /** Running on ui thread **/
                        ((Activity)MyWatchFace.this.getBaseContext()).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (checkNonNull(mWeatherImage, mWeatherMaxTemp, mWeatherMinTemp)) {
                                    invalidate();
                                } else {
                                    setInitialWeatherRendering();
                                    invalidate();
                                }
                            }
                        });

                    }
                }
            }
        }


        /**
         * Few null checks on weather ui elements.
         *
         * @param weatherIcon if this is null, then no need for further checking, this means something wrong with weatherId.
         * @param maxTemp if this is null, set some dummy value.
         * @param minTemp if this is null, set some dummy value.
         * @return true, if we can render on weather ui elements, using the given input elements, else false.
         */
        private boolean checkNonNull(Bitmap weatherIcon, String maxTemp, String minTemp){
            if(weatherIcon == null) {
                mWeatherImage = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
            }
            if(maxTemp == null) {
                mWeatherMaxTemp = "--";
            }
            if(minTemp == null){
                mWeatherMinTemp = "--";
            }
            return true;
        }

        public void setInitialWeatherRendering(){
            if(mWeatherImage == null){
                mWeatherImage = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
                mWeatherMaxTemp = "--";
                mWeatherMinTemp = "--";
            }
        }

        /**
         * Helper method to provide the art resource id according to the weather condition id returned
         * by the OpenWeatherMap call.
         *
         * @param weatherId from OpenWeatherMap API response
         * @return resource id for the corresponding icon. -1 if no relation is found.
         */
        public int getArtResourceForWeatherCondition(int weatherId) {
            // Based on weather code data found at:
            // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
            if (weatherId >= 200 && weatherId <= 232) {
                return R.drawable.art_storm;
            } else if (weatherId >= 300 && weatherId <= 321) {
                return R.drawable.art_light_rain;
            } else if (weatherId >= 500 && weatherId <= 504) {
                return R.drawable.art_rain;
            } else if (weatherId == 511) {
                return R.drawable.art_snow;
            } else if (weatherId >= 520 && weatherId <= 531) {
                return R.drawable.art_rain;
            } else if (weatherId >= 600 && weatherId <= 622) {
                return R.drawable.art_snow;
            } else if (weatherId >= 701 && weatherId <= 761) {
                return R.drawable.art_fog;
            } else if (weatherId == 761 || weatherId == 781) {
                return R.drawable.art_storm;
            } else if (weatherId == 800) {
                return R.drawable.art_clear;
            } else if (weatherId == 801) {
                return R.drawable.art_light_clouds;
            } else if (weatherId >= 802 && weatherId <= 804) {
                return R.drawable.art_clouds;
            }
            return -1;
        }

    }
}
