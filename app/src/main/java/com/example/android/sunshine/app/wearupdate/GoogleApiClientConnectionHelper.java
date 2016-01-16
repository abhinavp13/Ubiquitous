package com.example.android.sunshine.app.wearupdate;

import android.app.IntentService;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * This class has callbacks for Google Api Client connection.
 * Also, failure callbacks registered.
 * The main function of this class is to send data using
 * DataApi to wearable device. But first it needs to extract
 * the saved weather data.
 *
 * @author pabhinav.
 */
public class GoogleApiClientConnectionHelper implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private IntentService intentService;
    private GoogleApiClient googleApiClient;
    private int weatherId;
    private String maxTemp;
    private String minTemp;

    public GoogleApiClientConnectionHelper(IntentService intentService){
        this.intentService = intentService;
    }

    @Override
    public void onConnected(Bundle bundle) {

        Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(Utility.getPreferredLocation(intentService), System.currentTimeMillis());

        Cursor c = intentService.getContentResolver().query(
                weatherUri,
                new String[]{WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
                        WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
                        WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
                }, null, null, null);

        if (c != null) {
            fetchDataWithCursor(c);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        // Nothing required to be done here ...
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // Nothing required to be done here ...
    }

    public void setGoogleApiClient(GoogleApiClient googleApiClient) {
        this.googleApiClient = googleApiClient;
    }

    public void fetchDataWithCursor(@NonNull Cursor cursor){
        if (cursor.moveToFirst()) {
            weatherId = cursor.getInt(cursor.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID));
            maxTemp = Utility.formatTemperature(intentService, cursor.getDouble(cursor.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP)));
            minTemp = Utility.formatTemperature(intentService, cursor.getDouble(cursor.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP)));

            putDataForWearable();
        }
        cursor.close();
    }

    public void putDataForWearable(){

        final PutDataMapRequest mapRequest = PutDataMapRequest.create("/wearable/weather");
        mapRequest.getDataMap().putInt("SUNSHINE_WEATHER_ID", weatherId);
        mapRequest.getDataMap().putString("SUNSHINE_MAX_TEMP", maxTemp);
        mapRequest.getDataMap().putString("SUNSHINE_MIN_TEMP", minTemp);

        DataApi.DataItemResult result = Wearable.DataApi.putDataItem(googleApiClient, mapRequest.asPutDataRequest()).await();

        if (result.getStatus().isSuccess()) {
            Log.v("myTag", "Weather data sent successfully to data layer ");
        }
        else {
            // Log an error
            Log.v("myTag", "ERROR: failed to send Weather data to data layer");
        }
    }
}
