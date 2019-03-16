package com.srq.smstest.intents;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import com.srq.smstest.HttpRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

/**
 * Created by Ruben Requena on 23/02/2019.
 */

public class SMSService extends Service {
    public static String LOG = "SMSService";
    public static String SMS_ACTION = "SMS_ACTION";
    public static String SMS_DELIVERED = "SMS_DELIVERED";
    public static String SMS_LOG = "SMS_LOG";
    public static String SMS_GENERIC_ERROR = "SMS_GENERIC_ERROR";

    int _startID;
    Boolean mustDie=false;

    String SMS_SENT = "SMS_SENT";
    BroadcastReceiver sentRcv;
    BroadcastReceiver dlvRcv;

    String prodUrl = "http://srq-hermes.herokuapp.com";
    String devUrl = "http://192.168.0.100:3000";
    String mineroUrl = "/sms/minero/";
    String rewardUrl = "/sms/minado/";
    JSONObject cSMS;

    int _delay;
    private String _device;
    private boolean _dev;
    private Thread reconectarHilo;
    private boolean reconectando=false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        _startID = startId;
        _delay = intent.getIntExtra("delay",60) * 1000;
        _device = intent.getStringExtra("uid");
        _dev = intent.getBooleanExtra("dev",false);

        sendIntent(SMS_LOG,"START SERVICE");

        initReceivers();
        try {
            runTask();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return START_STICKY;
    }
    @Override
    public void onDestroy() {
        sendIntent(SMS_LOG,"destruyendo servicio");
        unregisterReceiver(sentRcv);
        unregisterReceiver(dlvRcv);
        mustDie=true;
        super.onDestroy();
    }

    private void runTask() throws InterruptedException {
        if (mustDie) stopSelf(_startID);
        String url = (_dev ? devUrl : prodUrl) + mineroUrl + _device;
        try {
            Log.d(LOG, "conectando a " + url);
            String json = HttpRequest.get(url).body();
            cSMS = new JSONObject(json);
            final String code = cSMS.getString("code");
            if (code.equals("ok")) {
                cSMS = cSMS.getJSONObject("sms");
                sendIntent(SMS_LOG,"GET "+cSMS.getString("_id"));
                sendSMSMessage(cSMS);
            } else waitRun(5000);
        } catch (JSONException jsonErr) {
            Log.d("Error", jsonErr.getMessage());
        } catch (HttpRequest.HttpRequestException err) {
            sendIntent(SMS_LOG, "404: RECONECTANDO");
            waitRun(_delay / 2);
        }
    }
    private void waitRun (final int delay) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(delay);
                    runTask();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
    private void sendIntent(String action,String state, Properties extra) {
        Intent smsIntent = new Intent();
        smsIntent.setAction(action);
        smsIntent.putExtra("state",state);

        Set<String> keys = extra.stringPropertyNames();
        for (String key : keys) {
            smsIntent.putExtra(key,extra.getProperty(key));
        }
        smsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        sendBroadcast(smsIntent);
    }
    private void sendIntent(String action,String state) {
        Intent smsIntent = new Intent();
        smsIntent.setAction(action);
        smsIntent.putExtra("state",state);
        smsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        sendBroadcast(smsIntent);
    }
    protected void sendSMSMessage(JSONObject sms) throws JSONException {

        SmsManager smsMan = SmsManager.getDefault();
        PendingIntent sentPI = PendingIntent.getBroadcast(this, 0, new Intent(SMS_SENT), PendingIntent.FLAG_CANCEL_CURRENT);
        PendingIntent devPI = PendingIntent.getBroadcast(this, 0, new Intent(SMS_DELIVERED), 0);

        String message = sms.getString("texto");
        if (message.length()>160) {
            ArrayList<String> messages = smsMan.divideMessage(sms.getString("texto"));
            ArrayList<PendingIntent> pendIntents = new ArrayList<>();
            ArrayList<PendingIntent> devIntents = new ArrayList<>();
            double smsLen = Math.ceil(message.length()/160);
            for (int i=0; i<smsLen; i++ ) {
                pendIntents.add(sentPI);
                devIntents.add(devPI);
            }
            smsMan.sendMultipartTextMessage(sms.getString("numero"), null, messages, pendIntents,null);
        } else {
            smsMan.sendTextMessage(sms.getString("numero"), null, message, sentPI,null);
        }


        sendIntent(SMS_LOG,"SEND "+sms.getString("_id"));
        Log.d(LOG,"Enviando mensaje...");
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

                if (getResultCode()==Activity.RESULT_OK) {
                    Intent smsIntent = new Intent();
                    smsIntent.setAction(SMS_ACTION);
                    smsIntent.putExtra("code",getResultCode());
                    smsIntent.putExtra("state",state);
                    try {
                        smsIntent.putExtra("smsID", cSMS.getString("_id"));
                    } catch (JSONException err) { }

                    smsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    sendBroadcast(smsIntent);

                    try {
                        String u = (_dev?devUrl:prodUrl)+rewardUrl+_device+"/"+cSMS.getString("_id");
                        String json = HttpRequest.get(u).body();
                        JSONObject mn = new JSONObject(json);
                        sendIntent(SMS_LOG,"RW "+mn.getString("_id"));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    waitRun(_delay);

                } else if (getResultCode()==SmsManager.RESULT_ERROR_GENERIC_FAILURE) {
                    //reset
                    Intent resetIntent = new Intent();
                    resetIntent.setAction(SMS_GENERIC_ERROR);
                    resetIntent.putExtra("state",state);
                    resetIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    sendBroadcast(resetIntent);
                    stopSelf(_startID); // stop service if your task completed
                } else {
                    Toast.makeText(context, "Reintentando envio", Toast.LENGTH_SHORT).show();
                    (new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(_delay / 2);
                                sendSMSMessage(cSMS);
                            } catch (InterruptedException e) {
                                Log.d(LOG,e.getMessage());
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    })).start();
                }
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
                //sendIntent(SMS_DELIVERED,state);
            }
        };

        registerReceiver(sentRcv,new IntentFilter(SMS_SENT));
        registerReceiver(dlvRcv, new IntentFilter(SMS_DELIVERED));
    }
}
