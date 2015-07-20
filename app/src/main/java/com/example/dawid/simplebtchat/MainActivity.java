package com.example.dawid.simplebtchat;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Scanner;
import java.util.UUID;
import java.util.logging.LogRecord;


public class MainActivity extends Activity {

    private static final String DISCONNECT_CODE = "@@$NI@UYBISOQUI@HI!GUQ@)(@U(UB@UG#@";
    private UUID BT_UUID = UUID.fromString("44f33588-6b91-4ba5-b388-e79a7375edea");
    private BluetoothAdapter localAdapter;

    private TextView tvPhoneName;
    private BluetoothServerSocket serverSocket;
    private BluetoothSocket communicationSocket;
    private BluetoothDevice communicationDevice;

    private Button btServer;
    private Button btClient;
    private Button btDConnect;
    private Button btSend;
    private EditText serverNameET;
    private TextView conversationTV;
    private EditText msgET;

    private PrintWriter output;
    private Scanner input;

    private Handler mHandler;

    private boolean conversationExist;
    private boolean isClient;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(MainActivity.this, "Looking for device to connect", Toast.LENGTH_LONG).show();
            String action = intent.getAction();
            if(action.equals(BluetoothDevice.ACTION_FOUND)){
                BluetoothDevice tmpDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(tmpDevice.getName().equals(serverNameET.getText().toString())) {
                    Toast.makeText(MainActivity.this, "Connected to :" + serverNameET.getText(), Toast.LENGTH_LONG).show();
                    communicationDevice = tmpDevice;

                    try {
                        communicationSocket = communicationDevice.createRfcommSocketToServiceRecord(BT_UUID);
                        communicationSocket.connect();

                        input = new Scanner(communicationSocket.getInputStream());
                        output = new PrintWriter(communicationSocket.getOutputStream());

                        conversationExist = true;
                        onStartListening();

                        Toast.makeText(MainActivity.this, "Client connected", Toast.LENGTH_LONG).show();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    localAdapter.cancelDiscovery();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        conversationExist = false;
        isClient = false;
        mHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if(msg.getData().getString("message").equals(DISCONNECT_CODE))
                    disconnect();
                else
                    conversationTV.setText(conversationTV.getText() + communicationSocket.getRemoteDevice().getName() + " : " +  msg.getData().getString("message") + '\n');
            }
        };

        tvPhoneName = (TextView)findViewById(R.id.tvPhoneName);
        serverNameET = (EditText)findViewById(R.id.serverNameEt);
        conversationTV = (TextView)findViewById(R.id.tvConversation);

        btSend = (Button)findViewById(R.id.btSend);
        btClient = (Button)findViewById(R.id.btClient);
        btDConnect = (Button)findViewById(R.id.btDisconnect);
        btServer = (Button)findViewById(R.id.btServer);
        msgET = (EditText)findViewById(R.id.msgET);

        localAdapter = BluetoothAdapter.getDefaultAdapter();
        if(localAdapter == null)
            Toast.makeText(this, "This device does not suppor bluetooth connection", Toast.LENGTH_LONG).show();
        else{
            tvPhoneName.setText("Your device's name: " + localAdapter.getName());
        }
    }


    public void buttonSendClicked(View view) {
        String tmpMsg = msgET.getText().toString();
        String tmpMsgToSend = tmpMsg.replace('\n', ' ');
        output.println(tmpMsgToSend);
        output.flush();
        conversationTV.setText(conversationTV.getText() + localAdapter.getName() + " : " + msgET.getText().toString() + '\n');
    }

    public void buttonDisconnectClicked(View view) {
        disconnect();
    }

    public void buttonClientClicked(View view) {
        isClient = true;

        enableBT();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);
        localAdapter.startDiscovery();

        enableUI();
    }

    public void buttonServerClicked(View view) {
        enableBT();
        enableUI();

        Toast.makeText(this, "Waiting for client to connect", Toast.LENGTH_LONG).show();
        try {

            serverSocket = localAdapter.listenUsingInsecureRfcommWithServiceRecord("BTCHATAPP", BT_UUID);
            new AsyncTask<Integer, Integer, Integer>(){

                @Override
                protected Integer doInBackground(Integer... params) {
                    try {
                        communicationSocket = serverSocket.accept();
                        input = new Scanner(communicationSocket.getInputStream());
                        output = new PrintWriter(communicationSocket.getOutputStream());
                        serverSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return 0;
                }

                @Override
                protected void onPostExecute(Integer integer) {
                    super.onPostExecute(integer);
                    Toast.makeText(MainActivity.this, "Phone: " + communicationSocket.getRemoteDevice().getName() + " connected.", Toast.LENGTH_LONG).show();
                    conversationExist = true;
                    onStartListening();
                }
            }.execute();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void onStartListening() {
            new  AsyncTask<Integer, Integer, Integer>(){
                @Override
                protected Integer doInBackground(Integer... params) {
                    while(conversationExist){
                        if(!input.hasNext())
                            continue;
                        String msg = input.nextLine();
                        Message convMessage = Message.obtain();
                        Bundle tmpBundle = new Bundle();
                        tmpBundle.putString("message", msg);
                        convMessage.setData(tmpBundle);
                        mHandler.sendMessage(convMessage);
                    }
                    return 0;
                }
            }.execute();
    }

    private void disconnect() {
        output.println(DISCONNECT_CODE);
        output.flush();


        isClient = false;
        conversationExist = false;
        try {
            if(communicationSocket != null)
                communicationSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(isClient)
            unregisterReceiver(receiver);
        resetUI();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(receiver);
    }

    private void enableBT() {
        Intent discoverIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 600);
        startActivity(discoverIntent);
    }

    private void enableUI() {
        btSend.setEnabled(true);
        btDConnect.setEnabled(true);
        btServer.setEnabled(false);
        btClient.setEnabled(false);
        conversationTV.setEnabled(true);
        serverNameET.setEnabled(false);
    }

    private void resetUI(){
        btSend.setEnabled(false);
        btDConnect.setEnabled(false);
        btServer.setEnabled(true);
        btClient.setEnabled(true);
        conversationTV.setEnabled(false);
        serverNameET.setEnabled(true);
    }
}
