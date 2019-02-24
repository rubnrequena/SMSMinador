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

import android.util.Log;
import android.view.View;

import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.srq.smstest.intents.MinerIntent;
import com.srq.smstest.intents.SMSService;

import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    String SMS_SENT = "SMS_SENT";
    String SMS_DELIVERED = "SMS_DELIVERED";
    Integer MY_PERMISSIONS_REQUEST_SEND_SMS = 1;

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

        arrayList = new ArrayList<>();
        arrayList.add("Minero esperando inicio de tareas...");
        adaptador = new ArrayAdapter(this,R.layout.simple_list_item1,arrayList);
        smsLog.setAdapter(adaptador);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MinerIntent.SMS_ACTION);

        rcv = new ProgressReceiver();
        registerReceiver(rcv, filter);

        checkForSmsPermission();

        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Tag");
    }
    @Override
    public void onDestroy () {
        unregisterReceiver(rcv);
        super.onDestroy();
    }
    /*@Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("BUTTON_STATE_TEXT", (String) sendBtn.getText());
        super.onSaveInstanceState(outState);
    }
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        System.out.println("onRestoreInstanceState");
        final String btnStateText = savedInstanceState.getString("BUTTON_STATE_TEXT");
        sendBtn.setText(btnStateText);
    }*/
    private void startMiner() {
        isRunning =true;
        sendBtn.setText("Detener");

        miner = new Intent(MainActivity.this, SMSService.class);
        miner.putExtra("delay",Integer.parseInt(delaySec.getText().toString()));
        startService(miner);

        wl.acquire();
        arrayList.add(0,"Minero inicia sesion");
        adaptador.notifyDataSetChanged();
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
            // Permission already granted. Enable the SMS button.
            sendBtn.setEnabled(true);
        }
    }

    public class ProgressReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            DateFormat fnow = new SimpleDateFormat("HH:mm:ss");
            if(intent.getAction().equals(MinerIntent.SMS_ACTION)) {
                Integer acc =  intent.getIntExtra("code",-2);
                String state = intent.getStringExtra("state");
                if (acc==Activity.RESULT_OK) {
                    mSent = mSent+1;
                    smsSent.setText(String.format("Mensajes Enviados: %s",mSent.toString()));
                    codeLabel.setText("Mensaje enviado: "+fnow.format(new Date()));
                }
                else if (acc==Activity.RESULT_CANCELED) codeLabel.setText("Mensaje cancelado: "+fnow.format(new Date()));

                arrayList.add(0,String.format("%s: %s",fnow.format(new Date()),state));
                adaptador.notifyDataSetChanged();
            }
            if(intent.getAction().equals(SMSService.SMS_LOG)) {
                String state = intent.getStringExtra("texto");
                arrayList.add(0,String.format("%s: %s",fnow.format(new Date()),state));
                adaptador.notifyDataSetChanged();
            }
            if(intent.getAction().equals(SMSService.SMS_GENERIC_ERROR)) {
                stopService(miner);
                arrayList.add(0,String.format("%s: %s",fnow.format(new Date()),"Reiniciando servicio"));
                adaptador.notifyDataSetChanged();
                startService(miner);
            }
        }
    }
}
