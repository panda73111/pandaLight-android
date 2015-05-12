package com.blackwhitesoftware.pandalight;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Random;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends ActionBarActivity
{
    private final static String                     TAG                  = "MainActivity";
    private final static int                        REQUEST_ENABLE_BT    = 1;
    private static       BluetoothBroadcastReceiver btBroadcastReceiver  = null;
    private static       BluetoothAdapter           btAdapter            = null;
    private static       BluetoothSocket            btSocket             = null;
    private final        String                     pandaLightDeviceName = "pandaLight";
    private final        UUID                       pandaLightUuid       = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private              CONNECTION_STATE           connectionState      = CONNECTION_STATE.CONNECTION_STATE_UNCONNECTED;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btBroadcastReceiver = new BluetoothBroadcastReceiver();

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
                        {
                        }
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
                        {
                        }
                    }
                }
        );

        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_UUID);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(btBroadcastReceiver, filter);

        btAdapter = BluetoothAdapter.getDefaultAdapter();
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

    private void initiateBluetoothConnection(BluetoothDevice device, UUID uuid)
    {
        try
        {
            if (btSocket != null && btSocket.isConnected())
                btSocket.close();
        }
        catch (IOException ex)
        {
        }
        try
        {
            if (btAdapter.isDiscovering())
                btAdapter.cancelDiscovery();

            if (device.getBondState() == BluetoothDevice.BOND_NONE)
                device.createBond();
            else
                device.fetchUuidsWithSdp();

            BluetoothSocket socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
            socket.connect();

            //socket.getOutputStream().write(new byte[]{0x65, 0x00, 0x00, 0x23, (byte) 0x88});
            //Log.d(TAG, "wrote data");

            Log.d(TAG, "Successfully opened a connection");
            connectionState = CONNECTION_STATE.CONNECTION_STATE_CONNECTED;
            onConnectionStateChanged();
        }
        catch (IOException connectEx)
        {
            if (btSocket != null)
            {
                try
                {
                    btSocket.close();
                }
                catch (IOException closeEx)
                {
                }
            }

            try
            {
                btSocket = (BluetoothSocket) device.getClass()
                                                   .getMethod("createRfcommSocket", new Class[]{int.class})
                                                   .invoke(device, 1);
                btSocket.connect();

                Log.d(TAG, "Successfully opened a connection via fallback socket");
                connectionState = CONNECTION_STATE.CONNECTION_STATE_CONNECTED;
                onConnectionStateChanged();
            }
            catch (NoSuchMethodException invokeEx)
            {
            }
            catch (IllegalAccessException invokeEx)
            {
            }
            catch (InvocationTargetException invokeEx)
            {
            }
            catch (IOException connectEx2)
            {
                if (btSocket != null)
                {
                    try
                    {
                        btSocket.close();
                    }
                    catch (IOException closeEx)
                    {
                    }
                }
                Log.e(TAG, "Establishing a connection to device " + device.getName() + " failed!");
                Log.e(TAG, " reason: " + connectEx.getMessage());
                connectionState = CONNECTION_STATE.CONNECTION_STATE_UNCONNECTED;
                onConnectionStateChanged();
            }
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
        {
        }
    }

    private enum CONNECTION_STATE
    {
        CONNECTION_STATE_UNCONNECTED,
        CONNECTION_STATE_DISCOVERY,
        CONNECTION_STATE_CONNECTED
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
            else if (BluetoothDevice.ACTION_UUID.equals(action))
            {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Parcelable[] uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                if (uuids == null)
                {
                    Parcelable uuid = intent.getParcelableExtra(BluetoothDevice.EXTRA_UUID);
                    if (uuid == null)
                        Log.d(TAG, "found no UUIDs!");
                    else
                        Log.d(TAG, "found UUID of device " + device.getName() + ": " + uuid.toString());
                }
                else
                {
                    for (Parcelable uuid : uuids)
                        Log.d(TAG, "found UUID of device " + device.getName() + ": " + uuid.toString());
                }
            }
            else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action))
            {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                switch (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1))
                {
                    case BluetoothDevice.BOND_NONE:
                        Log.d(TAG, "no bond with device " + device.getName());
                        break;
                    case BluetoothDevice.BOND_BONDING:
                        Log.d(TAG, "bonding with device " + device.getName());
                        break;
                    case BluetoothDevice.BOND_BONDED:
                        Log.d(TAG, "successfully bonded with device " + device.getName());
                        device.fetchUuidsWithSdp();
                        break;
                }
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
}
