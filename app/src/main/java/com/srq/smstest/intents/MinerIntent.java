package com.srq.smstest.intents;

import android.app.Activity;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.Nullable;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.srq.smstest.HttpRequest;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Ruben Requena on 13/02/2019.
 */

public class MinerIntent extends IntentService {
    public static Boolean RUNNING = false;
    public static String LOG = "minerIntent";
    public static String SMS_ACTION = "SMS_ACTION";

    String SMS_SENT = "SMS_SENT";
    String SMS_DELIVERED = "SMS_DELIVERED";
    BroadcastReceiver sentRcv;
    BroadcastReceiver dlvRcv;

    String prodUrl = "http://srq-hermes.herokuapp.com/sms/minero";
    String devUrl = "http://192.168.0.104:3000/sms/minero/5c6ac5b06bce272a5c086f17";
    JSONObject cSMS;

    int _delay;
    String deviceID = "unknow";

    public MinerIntent() {
        super("MinerIntent");
        Log.d("service","iniciando servicio");

        /*TelephonyManager telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        try { deviceID = telephonyManager.getDeviceId();
        } catch (SecurityException err) { deviceID = "unknown"; }*/
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        _delay = intent.getIntExtra("delay",60) * 1000;
        Log.d("service",String.format("Iniciando servicio cada %s sec",_delay));

        if (!MinerIntent.RUNNING) runTask();
        MinerIntent.RUNNING = true;
    }

    @Override
    public void onCreate () {
        Log.d("service","creando intent");
        super.onCreate();
        initReceivers();
    }

    @Override
    public void onDestroy () {
        unregisterReceiver(sentRcv);
        unregisterReceiver(dlvRcv);
        super.onDestroy();
        Log.d("service","destruyendo intent");
        //MinerIntent.RUNNING = false;
    }
    private void runTask() {
        Log.d("MinerIntent","tick tack");
        try {
            String json = HttpRequest.get(devUrl).body();
            cSMS = new JSONObject(json);
            final String code = cSMS.getString("code");
            if (code.equals("ok")) {
                cSMS = cSMS.getJSONObject("sms");
                sendSMSMessage(cSMS);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void sendSMSMessage(JSONObject sms) throws Exception {
        registerReceiver(sentRcv,new IntentFilter(SMS_SENT));
        registerReceiver(dlvRcv, new IntentFilter(SMS_DELIVERED));

        SmsManager smsMan = SmsManager.getDefault();
        PendingIntent sentPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(SMS_SENT), 0);

        smsMan.sendTextMessage(sms.getString("numero"), null, sms.getString("texto"), sentPendingIntent, null);

        Log.d(LOG,"Mensaje enviado...");
    }

    protected void initReceivers() {
        sentRcv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String state="";
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        state = "SMS sent successfully";
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        state = "Generic failure cause";
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        state = "Service is currently unavailable";
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        state = "No PDU provided";
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        state = "Radio was explicitly turned off";
                        break;
                }
                Toast.makeText(context, state, Toast.LENGTH_LONG).show();

                Intent smsIntent = new Intent();
                smsIntent.setAction(SMS_ACTION);
                smsIntent.putExtra("code",getResultCode());
                smsIntent.putExtra("state",state);
                if (getResultCode()==Activity.RESULT_OK) {
                    smsIntent.putExtra("sms",cSMS.toString());
                    try {
                        Thread.sleep(_delay);
                        runTask();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        runTask();
                    }
                }
                smsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                sendBroadcast(smsIntent);
            }
        };

        dlvRcv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String state="";
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        state = "SMS delivered";
                        break;
                    case Activity.RESULT_CANCELED:
                        state = "SMS not delivered";
                        break;
                }
                Toast.makeText(context, state, Toast.LENGTH_SHORT).show();
            }
        };
    }
}
