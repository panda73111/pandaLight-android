package com.blackwhitesoftware.pandalight;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends ActionBarActivity
{
    private enum CONNECTION_STATE
    {
        CONNECTION_STATE_UNCONNECTED,
        CONNECTION_STATE_DISCOVERY,
        CONNECTION_STATE_CONNECTED
    }

    private              CONNECTION_STATE           connectionState      = CONNECTION_STATE.CONNECTION_STATE_UNCONNECTED;
    private final static String                     TAG                  = "MainActivity";
    private final static int                        REQUEST_ENABLE_BT    = 1;
    private static       BluetoothBroadcastReceiver btBroadcastReceiver  = null;
    private static       BluetoothAdapter           btAdapter            = null;
    private static       BluetoothSocket            btSocket             = null;
    private final        String                     pandaLightDeviceName = "pandaLight";
    private final        UUID                       pandaLightUuid       = UUID.fromString("56F46190-A07D-11E4-BCD8-0800200C9A66");

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btBroadcastReceiver = new BluetoothBroadcastReceiver();
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        final Button connectButton = (Button) findViewById(R.id.button_connect);
        connectButton.setOnClickListener(
                new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v)
                    {
                        startBluetoothDiscovery();
                    }
                }
        );

        final Button disconnectButton = (Button) findViewById(R.id.button_disconnect);
        disconnectButton.setOnClickListener(
                new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v)
                    {
                        if (btSocket == null || !btSocket.isConnected())
                            return;

                        try
                        {
                            btSocket.close();
                        }
                        catch (IOException ex)
                        { }
                        connectionState = CONNECTION_STATE.CONNECTION_STATE_UNCONNECTED;
                        onConnectionStateChanged();
                    }
                }
        );

        final Button sendDataButton = (Button) findViewById(R.id.button_sendData);
        sendDataButton.setOnClickListener(
                new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v)
                    {
                        if (btSocket == null || !btSocket.isConnected())
                            return;

                        try
                        {
                            OutputStream stream = btSocket.getOutputStream();
                            byte buffer[] = new byte[1024];
                            new Random().nextBytes(buffer);
                            stream.write(buffer);
                            stream.flush();
                        }
                        catch (IOException ex)
                        { }
                    }
                }
        );

        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(btBroadcastReceiver, filter);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings)
        {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void startBluetoothDiscovery()
    {
        if (!btAdapter.isEnabled())
        {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }
        if (btAdapter.isDiscovering())
            return;

        listPairedDevices();
        if (connectionState == CONNECTION_STATE.CONNECTION_STATE_UNCONNECTED)
        {
            btAdapter.startDiscovery();
            connectionState = CONNECTION_STATE.CONNECTION_STATE_DISCOVERY;
            onConnectionStateChanged();
        }
    }

    private void listPairedDevices()
    {
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0)
        {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices)
                handleBluetoothDevice(device);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode)
        {
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK)
                    startBluetoothDiscovery();
                break;
        }
    }

    private void handleBluetoothDevice(BluetoothDevice device)
    {
        final String deviceName = device.getName();
        Log.d(
                TAG, "Got BT device: " +
                        deviceName + " - " + device.getAddress()
        );
        if (deviceName.equals(pandaLightDeviceName))
            initiateBluetoothConnection(device, pandaLightUuid);
    }

    private class BluetoothBroadcastReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            Log.d(TAG, "Received BT broadcast action: " + action);
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action))
            {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                handleBluetoothDevice(device);
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
            {
                if (connectionState == CONNECTION_STATE.CONNECTION_STATE_DISCOVERY)
                {
                    connectionState = CONNECTION_STATE.CONNECTION_STATE_UNCONNECTED;
                    onConnectionStateChanged();
                }
            }
        }
    }

    private void initiateBluetoothConnection(BluetoothDevice device, UUID uuid)
    {
        try
        {
            if (btSocket != null && btSocket.isConnected())
                btSocket.close();
        }
        catch (IOException ex)
        { }
        try
        {
            btSocket = device.createRfcommSocketToServiceRecord(uuid);
            btSocket.connect();
            Log.d(TAG, "Successfully opened a connection");
            if (btAdapter.isDiscovering())
                btAdapter.cancelDiscovery();
            connectionState = CONNECTION_STATE.CONNECTION_STATE_CONNECTED;
            onConnectionStateChanged();
        }
        catch (IOException ex)
        {
            Log.e(TAG, "Establishing a connection to device " + device.getName() + " failed!");
            connectionState = CONNECTION_STATE.CONNECTION_STATE_UNCONNECTED;
            onConnectionStateChanged();
        }
    }

    private void onConnectionStateChanged()
    {
        final TextView connectionStateTextView = (TextView) findViewById(R.id.textView_connectionState);
        final Button sendDataButton = (Button) findViewById(R.id.button_sendData);
        final Button connectButton = (Button) findViewById(R.id.button_connect);
        final Button disconnectButton = (Button) findViewById(R.id.button_disconnect);
        String connectionStateString = "";
        boolean sendDataButtonEnabled = false;
        int connectButtonVisibility = Button.VISIBLE;
        int disconnectButtonVisibility = Button.INVISIBLE;
        boolean connectButtonEnabled = true;
        switch (connectionState)
        {
            case CONNECTION_STATE_CONNECTED:
                connectionStateString = "connected";
                sendDataButtonEnabled = true;
                connectButtonVisibility = Button.INVISIBLE;
                disconnectButtonVisibility = Button.VISIBLE;
                break;
            case CONNECTION_STATE_DISCOVERY:
                connectionStateString = "searching...";
                connectButtonEnabled = false;
                break;
            case CONNECTION_STATE_UNCONNECTED:
                connectionStateString = "unconnected";
                break;
        }
        connectionStateTextView.setText(connectionStateString);
        connectButton.setVisibility(connectButtonVisibility);
        connectButton.setEnabled(connectButtonEnabled);
        disconnectButton.setVisibility(disconnectButtonVisibility);
        sendDataButton.setEnabled(sendDataButtonEnabled);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        onConnectionStateChanged();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if (btSocket == null || !btSocket.isConnected())
            return;
        try
        {
            btSocket.close();
        }
        catch (IOException ex)
        { }
    }
}
