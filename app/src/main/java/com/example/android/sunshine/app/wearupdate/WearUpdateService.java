package com.example.android.sunshine.app.wearupdate;

import android.app.IntentService;
import android.content.Intent;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Wearable;

/**
 * This class extends a service, whose whole purpose is to update
 * wearable device's weather data
 *
 * Reference :
 * http://catinean.com/2015/03/28/creating-a-watch-face-with-android-wear-api-part-2/
 *
 * @author pabhinav
 */
public class WearUpdateService extends IntentService  {

    /**
     * Google Api Client required for connecting to wearable device.
     */
    private GoogleApiClient googleApiClient;

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public WearUpdateService(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        /** if wearable device update is required, connect to google api client **/
        if (intent != null && intent.getAction() != null && intent.getAction().equals("WearUpdateAction")) {

            GoogleApiClientConnectionHelper googleApiClientConnectionHelper = new GoogleApiClientConnectionHelper(this);
            googleApiClient = new GoogleApiClient.Builder(WearUpdateService.this)
                    .addConnectionCallbacks(googleApiClientConnectionHelper)
                    .addOnConnectionFailedListener(googleApiClientConnectionHelper)
                    .addApi(Wearable.API)
                    .build();

            googleApiClientConnectionHelper.setGoogleApiClient(googleApiClient);
            googleApiClient.connect();
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        googleApiClient.disconnect();
    }
}
