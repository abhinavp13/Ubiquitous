package com.pabhinav.sp.wear;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.view.GestureDetectorCompat;
import android.support.wearable.view.DismissOverlayView;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import org.w3c.dom.Text;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class MainActivity extends Activity implements DataApi.DataListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    /**
     * Root Layout for rectangular wearables.
     */
    private LinearLayout mRectBackground;

    /**
     * Root Layout for round wearables
     */
    private LinearLayout mRoundBackground;

    /**
     * Gesture detection library.
     */
    private GestureDetectorCompat mGestureDetector;

    /**
     * Dismiss overlay which will appear when user presses this app for a long time, on the wearable.
     */
    private DismissOverlayView mDismissOverlayView;

    /**
     * For all the listed below intent filters,
     * a callback receiver will be registered and used
     * to update the UI element denoting time.
     *
     * Reference :
     * https://github.com/kentarosu/androidwear-myfirstwatchface
     */
    private final static IntentFilter INTENT_FILTER;
    static {
        INTENT_FILTER = new IntentFilter();
        INTENT_FILTER.addAction(Intent.ACTION_TIME_TICK);
        INTENT_FILTER.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        INTENT_FILTER.addAction(Intent.ACTION_TIME_CHANGED);
    }

    /**
     * Time related UI elements
     */
    private TextView timeTextView;
    private TextView amPmTextView;

    /**
     * Broadcast Receiver used for receiving any changes mentioned in
     * intent filters listed above.
     * Callback function onReceive has the code for updating current ui time.
     */
    private BroadcastReceiver timeChangeBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            /** Get current time **/
            String completeTime = new SimpleDateFormat("KK:mm a").format(Calendar.getInstance().getTime());

            /** Set fetched time **/
            timeTextView.setText(completeTime.substring(0,5));
            amPmTextView.setText(completeTime.substring(6));
        }
    };

    /**
     * Google Api Client for listening to nearby device.
     */
    private GoogleApiClient googleApiClient;

    /**
     * Weather related UI Elements
     */
    private ImageView weatherIcon;
    private TextView weatherType;
    private TextView maxTemp;
    private TextView minTemp;

    private Bitmap mWeatherImage;
    private String mWeatherMaxTemp;
    private String mWeatherMinTemp;
    private String mWeatherType;

    /**
     * Toast used to display little remarks.
     */
    Toast makeToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        googleApiClient = new GoogleApiClient.Builder(MainActivity.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {

                mRectBackground = (LinearLayout) findViewById(R.id.rect_activity_main);
                mRoundBackground = (LinearLayout) findViewById(R.id.round_activity_main);

                /** Time related UI elements **/
                timeTextView = (TextView)findViewById(R.id.time_text_view);
                amPmTextView = (TextView)findViewById(R.id.am_pm_text_view);

                /** Weather related UI Elements **/
                weatherIcon = (ImageView)findViewById(R.id.weather_icon_image_view);
                weatherType = (TextView)findViewById(R.id.weather_type_text_view);
                maxTemp = (TextView)findViewById(R.id.upper_temperature);
                minTemp = (TextView)findViewById(R.id.lower_temperature);

                /** Set some initial rendering **/
                setInitialWeatherRendering();

                /** Initial call for updating time **/
                timeChangeBroadcastReceiver.onReceive(MainActivity.this, null);

                /** Register time change receiver **/
                registerReceiver(timeChangeBroadcastReceiver, INTENT_FILTER);
            }
        });

        /**
         * Added dismiss overlay,
         * which will be shown when user
         * long presses the wearable app.
         * Swipe dismiss is not override,
         * so user can swipe and dismiss app also.
         */
        mDismissOverlayView = (DismissOverlayView) findViewById(R.id.dismiss_overlay);
        mGestureDetector = new GestureDetectorCompat(this, new LongPressListener());
    }

    /**
     * Animates the layout when clicked. The animation used depends on whether the
     * device is round or rectangular.
     * (Reference :  android samples 'WatchViewStub')
     */
    public void onLayoutClicked(View view) {
        if (mRectBackground != null) {
            ScaleAnimation scaleAnimation = new ScaleAnimation(1.0f, 0.7f, 1.0f, 0.7f,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            scaleAnimation.setDuration(300);
            scaleAnimation.setRepeatCount(1);
            scaleAnimation.setRepeatMode(Animation.REVERSE);
            mRectBackground.startAnimation(scaleAnimation);
        }
        if (mRoundBackground != null) {
            mRoundBackground.animate().rotationBy(360).setDuration(300).start();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return mGestureDetector.onTouchEvent(event) || super.dispatchTouchEvent(event);
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
                    mWeatherType = getStringForWeatherCondition(MainActivity.this,weatherId);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(checkNonNull(mWeatherImage, mWeatherType, mWeatherMaxTemp, mWeatherMinTemp)) {
                                weatherIcon.setImageBitmap(mWeatherImage);
                                maxTemp.setText(mWeatherMaxTemp);
                                minTemp.setText(mWeatherMinTemp);
                                weatherType.setText(mWeatherType);
                            } else {
                                setInitialWeatherRendering();
                            }
                        }
                    });

                }
            }
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Wearable.DataApi.addListener(googleApiClient, MainActivity.this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        // Nothing to be done
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // Nothing to be done
    }

    private class LongPressListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public void onLongPress(MotionEvent event) {
            mDismissOverlayView.show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        /** Un-Register time change receiver **/
        unregisterReceiver(timeChangeBroadcastReceiver);
    }

    /**
     * Few null checks on weather ui elements.
     *
     * @param weatherIcon if this is null, then no need for further checking, this means something wrong with weatherId.
     * @param weatherType if this is null, set some dummy value.
     * @param maxTemp if this is null, set some dummy value.
     * @param minTemp if this is null, set some dummy value.
     * @return true, if we can render on weather ui elements, using the given input elements, else false.
     */
    private boolean checkNonNull(Bitmap weatherIcon, String weatherType, String maxTemp, String minTemp){
        if(weatherIcon == null) return false;
        if(maxTemp == null) {
            mWeatherMaxTemp = "--";
        }
        if(minTemp == null){
            mWeatherMinTemp = "--";
        }
        if(weatherType == null){
            mWeatherType = "Unknown";
        }
        return true;
    }

    public void setInitialWeatherRendering(){
        weatherIcon.setImageDrawable(getResources().getDrawable(R.mipmap.ic_launcher));
        maxTemp.setText("--");
        minTemp.setText("--");
        weatherType.setText("Unknown");

        if(makeToast == null || makeToast.getView() ==null || !makeToast.getView().isShown()) {
            makeToast = Toast.makeText(MainActivity.this, "Keep close to device for weather update", Toast.LENGTH_SHORT);
            makeToast.show();
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

    /**
     * Helper method to provide the string according to the weather
     * condition id returned by the OpenWeatherMap call.
     * @param context Android context
     * @param weatherId from OpenWeatherMap API response
     * @return string for the weather condition. null if no relation is found.
     */
    public String getStringForWeatherCondition(Context context, int weatherId) {
        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        int stringId;
        if (weatherId >= 200 && weatherId <= 232) {
            stringId = R.string.condition_2xx;
        } else if (weatherId >= 300 && weatherId <= 321) {
            stringId = R.string.condition_3xx;
        } else switch (weatherId) {
            case 500:
                stringId = R.string.condition_500;
                break;
            case 501:
                stringId = R.string.condition_501;
                break;
            case 502:
                stringId = R.string.condition_502;
                break;
            case 503:
                stringId = R.string.condition_503;
                break;
            case 504:
                stringId = R.string.condition_504;
                break;
            case 511:
                stringId = R.string.condition_511;
                break;
            case 520:
                stringId = R.string.condition_520;
                break;
            case 531:
                stringId = R.string.condition_531;
                break;
            case 600:
                stringId = R.string.condition_600;
                break;
            case 601:
                stringId = R.string.condition_601;
                break;
            case 602:
                stringId = R.string.condition_602;
                break;
            case 611:
                stringId = R.string.condition_611;
                break;
            case 612:
                stringId = R.string.condition_612;
                break;
            case 615:
                stringId = R.string.condition_615;
                break;
            case 616:
                stringId = R.string.condition_616;
                break;
            case 620:
                stringId = R.string.condition_620;
                break;
            case 621:
                stringId = R.string.condition_621;
                break;
            case 622:
                stringId = R.string.condition_622;
                break;
            case 701:
                stringId = R.string.condition_701;
                break;
            case 711:
                stringId = R.string.condition_711;
                break;
            case 721:
                stringId = R.string.condition_721;
                break;
            case 731:
                stringId = R.string.condition_731;
                break;
            case 741:
                stringId = R.string.condition_741;
                break;
            case 751:
                stringId = R.string.condition_751;
                break;
            case 761:
                stringId = R.string.condition_761;
                break;
            case 762:
                stringId = R.string.condition_762;
                break;
            case 771:
                stringId = R.string.condition_771;
                break;
            case 781:
                stringId = R.string.condition_781;
                break;
            case 800:
                stringId = R.string.condition_800;
                break;
            case 801:
                stringId = R.string.condition_801;
                break;
            case 802:
                stringId = R.string.condition_802;
                break;
            case 803:
                stringId = R.string.condition_803;
                break;
            case 804:
                stringId = R.string.condition_804;
                break;
            case 900:
                stringId = R.string.condition_900;
                break;
            case 901:
                stringId = R.string.condition_901;
                break;
            case 902:
                stringId = R.string.condition_902;
                break;
            case 903:
                stringId = R.string.condition_903;
                break;
            case 904:
                stringId = R.string.condition_904;
                break;
            case 905:
                stringId = R.string.condition_905;
                break;
            case 906:
                stringId = R.string.condition_906;
                break;
            case 951:
                stringId = R.string.condition_951;
                break;
            case 952:
                stringId = R.string.condition_952;
                break;
            case 953:
                stringId = R.string.condition_953;
                break;
            case 954:
                stringId = R.string.condition_954;
                break;
            case 955:
                stringId = R.string.condition_955;
                break;
            case 956:
                stringId = R.string.condition_956;
                break;
            case 957:
                stringId = R.string.condition_957;
                break;
            case 958:
                stringId = R.string.condition_958;
                break;
            case 959:
                stringId = R.string.condition_959;
                break;
            case 960:
                stringId = R.string.condition_960;
                break;
            case 961:
                stringId = R.string.condition_961;
                break;
            case 962:
                stringId = R.string.condition_962;
                break;
            default:
                return context.getString(R.string.condition_unknown, weatherId);
        }
        return context.getString(stringId);
    }
}
