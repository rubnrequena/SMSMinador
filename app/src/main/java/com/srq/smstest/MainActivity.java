package com.srq.smstest;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import android.os.StrictMode;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;

import android.os.Bundle;

import android.telephony.SmsManager;

import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;

import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.srq.smstest.intents.MinerIntent;
import com.srq.smstest.intents.SMSService;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    String SMS_SENT = "SMS_SENT";
    String SMS_DELIVERED = "SMS_DELIVERED";
    Integer MY_PERMISSIONS_REQUEST_SEND_SMS = 1;

    Switch testSw;
    Button sendBtn;
    TextView codeLabel;
    TextView smsSent;
    EditText delaySec;
    ListView smsLog;

    Integer mSent=0;
    ArrayList<String> arrayList;
    ArrayAdapter adaptador;

    Intent miner;
    ProgressReceiver rcv;
    Boolean isRunning =false;

    PowerManager pm;
    PowerManager.WakeLock wl;
    private String deviceID ="unknown";
    private TelephonyManager tel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        setContentView(R.layout.activity_main);

        sendBtn = findViewById(R.id.btnSendSMS);
        sendBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (isRunning ==true) stopMiner();
                else startMiner();
            }
        });
        if (isRunning) sendBtn.setText("Detener");
        else sendBtn.setText("Iniciar");

        codeLabel = findViewById(R.id.codeLabel);
        delaySec = findViewById(R.id.delaySeconds);
        smsSent = findViewById(R.id.smsSent);
        smsLog = findViewById(R.id.smsLog);
        testSw = findViewById(R.id.switch1);

        arrayList = new ArrayList<>();
        arrayList.add("Minero esperando inicio de tareas...");
        adaptador = new ArrayAdapter(this,R.layout.simple_list_item1,arrayList);
        smsLog.setAdapter(adaptador);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MinerIntent.SMS_ACTION);
        filter.addAction(SMSService.SMS_GENERIC_ERROR);
        filter.addAction(SMSService.SMS_LOG);
        filter.addAction(SMSService.SMS_DELIVERED);


        rcv = new ProgressReceiver();
        registerReceiver(rcv, filter);

        checkForSmsPermission();

        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Tag");
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String permissions[],
            int[] grantResults) {
        switch (requestCode) {
            case 99:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "Permission Granted!", Toast.LENGTH_SHORT).show();
                    startMiner();
                } else {
                    Toast.makeText(MainActivity.this, "Permission Denied!", Toast.LENGTH_SHORT).show();
                }
        }
    }

    @Override
    public void onDestroy () {
        unregisterReceiver(rcv);
        super.onDestroy();
    }
    private void startMiner() {
        tel = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.READ_PHONE_STATE},99);
        } else {
            deviceID = tel.getDeviceId();
            codeLabel.setText("Dispositivo: "+deviceID);

            isRunning =true;
            sendBtn.setText("Detener");

            miner = new Intent(MainActivity.this, SMSService.class);
            miner.putExtra("delay",Integer.parseInt(delaySec.getText().toString()));
            miner.putExtra("uid",deviceID);
            miner.putExtra("dev",testSw.isChecked());
            startService(miner);

            wl.acquire();
            arrayList.add(0,"Minero inicia sesion");
            adaptador.notifyDataSetChanged();
            Log.d("main","iniciando servicio desde "+deviceID);
        }
    }
    private void stopMiner() {
        isRunning = false;
        sendBtn.setText("Iniciar");
        stopService(miner);
        wl.release();
        arrayList.add(0,"Minero termina sesion");
        adaptador.notifyDataSetChanged();
    }

    private void checkForSmsPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS},
                    MY_PERMISSIONS_REQUEST_SEND_SMS);
        } else {
            sendBtn.setEnabled(true);
        }
    }

    public class ProgressReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String fnow = new SimpleDateFormat("HH:mm:ss").format(new Date());

            if(intent.getAction().equals(SMSService.SMS_ACTION)) {
                Integer acc =  intent.getIntExtra("code",-2);
                String smsID = intent.getStringExtra("smsID");
                if (acc==Activity.RESULT_OK) {
                    mSent = mSent+1;
                    smsSent.setText(String.format("Mensajes Enviados: %s",mSent.toString()));
                }
                arrayList.add(0,String.format("%s: %s",fnow,"OK "+smsID));
            }
            if(intent.getAction().equals(SMSService.SMS_DELIVERED)) {
                String smsID = intent.getStringExtra("state");
                arrayList.add(0,String.format("%s: %s",fnow,"DEV "+smsID));
            }
            if(intent.getAction().equals(SMSService.SMS_GENERIC_ERROR)) {
                (new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(10000);
                            startService(miner);
                        } catch (InterruptedException e) {
                            Log.d("Main",e.getMessage());
                        }
                    }
                })).start();
                //stopService(miner);
                arrayList.add(0,String.format("%s: %s",fnow,"Reiniciando servicio"));
            }
            if(intent.getAction().equals(SMSService.SMS_LOG)) {
                String state = intent.getStringExtra("state");
                arrayList.add(0,String.format("%s: %s",fnow,state));
            }
            adaptador.notifyDataSetChanged();
        }
    }
}
