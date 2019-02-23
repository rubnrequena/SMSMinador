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

    ArrayList<JSONObject> queue = new ArrayList<>();



    Timer _timer;
    Long _delay;
    String deviceID;

    public MinerIntent() {
        super("MinerIntent");
        Log.d("service","iniciando servicio");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        _delay = intent.getLongExtra("delay",60L) * 1000L;
        Log.d("service",String.format("Iniciando servicio cada %s sec",_delay.toString()));

        if (!MinerIntent.RUNNING) {
            _timer = new Timer();
            _timer.schedule(timertask,1000L,_delay);

            TelephonyManager telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
            try {
                deviceID = telephonyManager.getDeviceId();
            } catch (SecurityException err) {
                deviceID = "unknown";
            }
        }
        MinerIntent.RUNNING = true;
    }

    @Override
    public void onCreate () {
        Log.d("service","creando intent");
        super.onCreate();
        initReceivers();
    }

    @Override
    public  void onDestroy () {
        Log.d("service","destruyendo intent");
        MinerIntent.RUNNING = false;
    }
    private TimerTask timertask = new TimerTask() {
        @Override
        public void run() {
            Log.d("MinerIntent","tick tack");
            try {
                String prodUrl = "http://srq-hermes.herokuapp.com/sms/minero";
                String devUrl = "http://192.168.0.104:3000/sms/add/sms7/dev";
                String json = HttpRequest.get(devUrl).body();
                JSONObject res = new JSONObject(json);
                final String code = res.getString("code");
                if (code.equals("ok")) {
                    queue.add(res);
                    sendSMSMessage();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    protected void sendSMSMessage() throws Exception {
        Log.d(MinerIntent.LOG,String.format("Mensajes en cola: %s",queue.size()));
        JSONObject qsms = queue.get(0);

        JSONObject sms = qsms.getJSONObject("sms");

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
                unregisterReceiver(sentRcv);
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
                    String sms = queue.remove(0).toString();
                    smsIntent.putExtra("sms",sms);
                }
                smsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                sendBroadcast(smsIntent);

            }
        };

        dlvRcv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                unregisterReceiver(dlvRcv);
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
