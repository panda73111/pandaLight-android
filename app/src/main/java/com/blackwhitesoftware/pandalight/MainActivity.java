package com.blackwhitesoftware.pandalight;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends ActionBarActivity
{
    private final static String TAG               = "MainActivity";
    private final static int    REQUEST_ENABLE_BT = 1;
    private static BluetoothBroadcastReceiver btBroadcastReceiver = null;
    private static BluetoothAdapter           btAdapter = null;
    private static BluetoothSocket btSocket = null;
    private final UUID pandaLightUuid = UUID.fromString("56F46190-A07D-11E4-BCD8-0800200C9A66");

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btBroadcastReceiver = new BluetoothBroadcastReceiver();
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        final Button button = (Button) findViewById(R.id.button_connect);
        button.setOnClickListener(
                new View.OnClickListener()
                {
                    public void onClick(View v)
                    {
                        startBluetoothDiscovery();
                    }
                }
        );

        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
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
        btAdapter.startDiscovery();
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
        Log.d(
                TAG, "Got BT device: " +
                        device.getName() + " - " + device.getAddress()
        );
        ParcelUuid[] uuidList = device.getUuids();
        if (uuidList == null)
        {
            if (device.fetchUuidsWithSdp())
                Log.d(TAG, "Starting search for service UUIDs");
            else
                Log.w(TAG, "Fetching UUIDs of device " + device.getName() + " failed!");
            return;
        }
        for (ParcelUuid uuid : uuidList)
            handleServiceUuid(device, uuid.getUuid());
    }

    private void handleServiceUuid(BluetoothDevice device, UUID uuid)
    {
        Log.d(TAG, "Service: " + uuid.toString());
        if (uuid.equals(pandaLightUuid))
        {
            Log.d(TAG, "Found pandaLight service");
        }
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
                ParcelUuid uuid = intent.getParcelableExtra(BluetoothDevice.EXTRA_UUID);
                handleServiceUuid(device, uuid.getUuid());
            }
        }
    }

    private void initiateBluetoothConnection(BluetoothDevice device, UUID uuid)
    {
        try
        {
            btSocket = device.createRfcommSocketToServiceRecord(uuid);
            btSocket.connect();
            Log.d(TAG, "Successfully opened a connection");
        }
        catch (IOException ex)
        {
            Log.e(TAG, "Establishing a connection to device " + device.getName() + " failed!");
        }
    }
}
