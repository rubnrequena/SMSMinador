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

/**
 * Created by Ruben Requena on 23/02/2019.
 */

public class SMSService extends Service {
    public static String LOG = "SMSService";
    public static String SMS_ACTION = "SMS_ACTION";
    public static String SMS_LOG = "SMS_LOG";
    public static String SMS_GENERIC_ERROR = "SMS_GENERIC_ERROR";

    int _startID;

    String SMS_SENT = "SMS_SENT";
    String SMS_DELIVERED = "SMS_DELIVERED";
    BroadcastReceiver sentRcv;
    BroadcastReceiver dlvRcv;

    String prodUrl = "http://srq-hermes.herokuapp.com/sms/minero";
    String devUrl = "http://192.168.0.104:3000/sms/minero/5c6ac5b06bce272a5c086f17";
    JSONObject cSMS;

    int _delay;
    String deviceID = "unknow";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG,"iniciando servicio");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        _startID = startId;
        _delay = intent.getIntExtra("delay",60) * 1000;
        Log.d("service",String.format("Iniciando servicio cada %s ml",_delay));

        initReceivers();
        runTask();

        return START_STICKY;
    }
    @Override
    public void onDestroy() {
        Log.d(LOG,"destruyendo servicio");
        unregisterReceiver(sentRcv);
        unregisterReceiver(dlvRcv);
        super.onDestroy();
    }

    private void runTask() {
        try {
            String json = HttpRequest.get(prodUrl).body();
            cSMS = new JSONObject(json);
            final String code = cSMS.getString("code");
            if (code.equals("ok")) {
                cSMS = cSMS.getJSONObject("sms");
                sendSMSMessage(cSMS);
            } else {
                Intent smsIntent = new Intent();
                smsIntent.setAction(SMS_LOG);
                smsIntent.putExtra("text","Sin mensajes");

                smsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                sendBroadcast(smsIntent);

                Thread.sleep(5000);
                runTask();
            }
        } catch (JSONException jsonErr) {
            Log.d("Error",jsonErr.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected void sendSMSMessage(JSONObject sms) throws JSONException {


        SmsManager smsMan = SmsManager.getDefault();
        PendingIntent sentPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(SMS_SENT), 0);

        smsMan.sendTextMessage(sms.getString("numero"), null, sms.getString("texto"), sentPendingIntent, null);

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
                Log.d(LOG,state);

                Intent smsIntent = new Intent();
                smsIntent.setAction(SMS_ACTION);
                smsIntent.putExtra("code",getResultCode());
                smsIntent.putExtra("state",state);

                smsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                sendBroadcast(smsIntent);

                if (getResultCode()==Activity.RESULT_OK) {
                    smsIntent.putExtra("sms",cSMS.toString());
                    (new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(_delay);
                                runTask();
                            } catch (InterruptedException e) {
                                Log.d(LOG,e.getMessage());
                            }
                        }
                    })).start();
                } else if (getResultCode()==SmsManager.RESULT_ERROR_GENERIC_FAILURE) {
                    //reset
                    Intent resetIntent = new Intent();
                    resetIntent.setAction(SMS_GENERIC_ERROR);
                    resetIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    sendBroadcast(resetIntent);
                    //stopSelf(_startID); // stop service if your task completed
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
            }
        };

        registerReceiver(sentRcv,new IntentFilter(SMS_SENT));
        registerReceiver(dlvRcv, new IntentFilter(SMS_DELIVERED));
    }

    public static class smsWork implements Runnable {
        private static int delay = 60000;
        private static JSONObject sms;
        public void run() {
            try {
                System.out.println("Hello from a thread! "+sms.getString("numero"));
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (JSONException jsonE) {
                jsonE.printStackTrace();
            }
            System.out.println("Bye from a thread!");
        }

        public static void main (JSONObject _sms, int _delay) {
            sms = _sms;
            delay = _delay;
            (new Thread(new Runnable() {
                @Override
                public void run() {

                }
            })).start();
        }

    }
}
