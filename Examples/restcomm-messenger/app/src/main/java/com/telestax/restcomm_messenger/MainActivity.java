package com.telestax.restcomm_messenger;

//import android.support.v7.app.ActionBarActivity;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.view.View.OnClickListener;
import android.widget.TextView;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.client.sdk.RCConnection;
import org.mobicents.restcomm.android.client.sdk.RCConnectionListener;
import org.mobicents.restcomm.android.client.sdk.RCDevice;
import org.mobicents.restcomm.android.client.sdk.RCDeviceListener;
import org.mobicents.restcomm.android.client.sdk.RCPresenceEvent;

import java.util.HashMap;


public class MainActivity extends Activity implements RCDeviceListener, RCConnectionListener,
        OnClickListener, SharedPreferences.OnSharedPreferenceChangeListener, OnCheckedChangeListener,
        MediaPlayer.OnPreparedListener {

    SharedPreferences prefs;
    private RCDevice device;
    private RCConnection connection, pendingConnection;
    private HashMap<String, String> params;
    private static final String TAG = "MainActivity";
    MediaPlayer ringingPlayer;
    MediaPlayer callingPlayer;
    MediaPlayer messagePlayer;

    // UI elements
    Button btnRegister;
    Button btnDial;
    Button btnHangup;
    Button btnAnswer;
    Button btnDecline;
    Button btnCancel;
    Button btnSend;
    EditText txtUri;
    EditText txtMessage;
    EditText txtWall;
    CheckBox cbMuted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // initialize UI
        btnRegister = (Button)findViewById(R.id.button_register);
        btnRegister.setOnClickListener(this);
        btnDial = (Button)findViewById(R.id.button_dial);
        btnDial.setOnClickListener(this);
        btnHangup = (Button)findViewById(R.id.button_hangup);
        btnHangup.setOnClickListener(this);
        btnAnswer = (Button)findViewById(R.id.button_answer);
        btnAnswer.setOnClickListener(this);
        btnDecline = (Button)findViewById(R.id.button_decline);
        btnDecline.setOnClickListener(this);
        btnCancel = (Button)findViewById(R.id.button_cancel);
        btnCancel.setOnClickListener(this);
        btnSend = (Button)findViewById(R.id.button_send);
        btnSend.setOnClickListener(this);
        txtUri = (EditText)findViewById(R.id.text_uri);
        txtMessage = (EditText)findViewById(R.id.text_message);
        txtWall = (EditText)findViewById(R.id.text_wall);
        cbMuted = (CheckBox)findViewById(R.id.checkbox_muted);
        cbMuted.setOnCheckedChangeListener(this);

        RCClient.initialize(getApplicationContext(), new RCClient.RCInitListener()
        {
            public void onInitialized()
            {
                Log.i(TAG, "RCClient initialized");
            }

            public void onError(Exception exception)
            {
                Log.e(TAG, "RCClient initialization error");
            }
        });

        // TODO: we don't support capability tokens yet so let's use an empty string
        device = RCClient.createDevice("", this);
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        device.setIncomingIntent(intent);

        connection = null;
        params = new HashMap<String, String>();

        // preferences
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        initializeSipFromPreferences();

        txtUri.setText("sip:1234@192.168.2.32:5080");
        txtMessage.setText("Hello there!");

        cbMuted.setEnabled(false);

        // Setup Media (notice that I'm not preparing the media as create does that implicitly plus
        // I'm not ever stopping a player -instead I'm pausing so no additional preparation is needed
        // there either. We might need to revisit this at some point though
        ringingPlayer = MediaPlayer.create(getApplicationContext(), R.raw.ringing);
        ringingPlayer.setLooping(true);
        callingPlayer = MediaPlayer.create(getApplicationContext(), R.raw.calling);
        callingPlayer.setLooping(true);
        messagePlayer = MediaPlayer.create(getApplicationContext(), R.raw.message);
    }

    @Override
    public void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onPrepared(MediaPlayer mp)
    {
        Log.i(TAG, "Media Player prepared");
    }

    // UI Events
    public void onClick(View view) {
        if (view.getId() == R.id.button_dial) {
            if (connection != null) {
                Log.e(TAG, "Error: already connected");
                return;
            }
            HashMap<String, String> connectParams = new HashMap<String, String>();
            connectParams.put("username", txtUri.getText().toString());
            connection = device.connect(connectParams, this);
            if (connection == null) {
                Log.e(TAG, "Error: error connecting");
                return;
            }
        } else if (view.getId() == R.id.button_hangup) {
            if (connection == null) {
                Log.e(TAG, "Error: not connected");
            }
            else {
                connection.disconnect();
                connection = null;
                pendingConnection = null;
            }
        } else if (view.getId() == R.id.button_register) {
            device.updateParams(params);
        } else if (view.getId() == R.id.button_answer) {
            if (pendingConnection != null) {
                pendingConnection.accept();
                connection = this.pendingConnection;
                ringingPlayer.pause();
            }
        } else if (view.getId() == R.id.button_decline) {
            if (pendingConnection != null) {
                pendingConnection.reject();
                pendingConnection = null;
                ringingPlayer.pause();
            }
        } else if (view.getId() == R.id.button_cancel) {
            if (connection == null) {
                Log.e(TAG, "Error: not connected");
            }
            else {
                connection.disconnect();
                connection = null;
                pendingConnection = null;
                callingPlayer.pause();
            }
        } else if (view.getId() == R.id.button_send) {
            HashMap<String, String> sendParams = new HashMap<String, String>();
            sendParams.put("username", txtUri.getText().toString());
            if (device.sendMessage(txtMessage.getText().toString(), sendParams)) {
                // also output the message in the wall
                String text = txtWall.getText().toString();
                String newText = "Me: " + txtMessage.getText().toString() + "\n" + text;
                txtWall.setText(newText, TextView.BufferType.EDITABLE);
                messagePlayer.start();
            }
        }
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    {
        if (buttonView.getId() == R.id.checkbox_muted) {
            if (connection != null) {
                connection.setMuted(isChecked);
            }
        }
    }

    // RCDevice Listeners
    public void onStartListening(RCDevice device)
    {

    }

    public void onStopListening(RCDevice device)
    {

    }

    public void onStopListening(RCDevice device, int errorCode, String errorText)
    {
        if (errorCode == RCClient.ErrorCodes.NO_CONNECTIVITY.ordinal()) {
            showOkAlert("No Connectivity", errorText);
        }
        else if (errorCode == RCClient.ErrorCodes.GENERIC_ERROR.ordinal()) {
            showOkAlert("Generic Error", errorText);
        }
        else {
            showOkAlert("Unknown Error", "Unknown Restcomm Client error");
        }
    }

    public boolean receivePresenceEvents(RCDevice device)
    {
        return false;
    }

    public void onPresenceChanged(RCDevice device, RCPresenceEvent presenceEvent)
    {

    }

    public void onIncomingConnection(RCDevice device, RCConnection connection)
    {
        Log.i(TAG, "Connection arrived");
        ringingPlayer.start();
        pendingConnection = connection;
    }

    public void onIncomingMessage(RCDevice device, String message, HashMap<String, String> parameters)
    {
        Log.i(TAG, "Message arrived: message");
        String text = txtWall.getText().toString();
        String newText = parameters.get("username") + ": " + message + "\n" + text;
        txtWall.setText(newText, TextView.BufferType.EDITABLE);
        messagePlayer.start();
    }

    // RCConnection Listeners
    public void onConnecting(RCConnection connection)
    {
        Log.i(TAG, "RCConnection connecting");
        callingPlayer.start();
    }

    public void onConnected(RCConnection connection) {
        Log.i(TAG, "RCConnection connected");
        cbMuted.setEnabled(true);
        if (!connection.isIncoming()) {
            callingPlayer.pause();
        }
    }

    public void onDisconnected(RCConnection connection) {
        Log.i(TAG, "RCConnection disconnected");
        cbMuted.setEnabled(false);

        this.connection = null;
        pendingConnection = null;
    }

    public void onCancelled(RCConnection connection) {
        Log.i(TAG, "RCConnection cancelled");
        if (connection.isIncoming() == true) {
            ringingPlayer.pause();
        }
        else {
            callingPlayer.pause();
        }

        this.connection = null;
        pendingConnection = null;
    }

    public void onDeclined(RCConnection connection) {
        Log.i(TAG, "RCConnection declined");
        callingPlayer.pause();

        this.connection = null;
        pendingConnection = null;
    }

    public void onDisconnected(RCConnection connection, int errorCode, String errorText) {
        if (errorCode == RCClient.ErrorCodes.NO_CONNECTIVITY.ordinal()) {
            showOkAlert("No Connectivity", errorText);
        } else if (errorCode == RCClient.ErrorCodes.GENERIC_ERROR.ordinal()) {
            showOkAlert("Generic Error", errorText);
        } else {
            showOkAlert("Unknown Error", "Unknown Restcomm Client error");
        }
        this.connection = null;
        pendingConnection = null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        /*
        // Checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Toast.makeText(this, "landscape", Toast.LENGTH_SHORT).show();
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
            Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show();
        }
        */
    }

    // menu stuff
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivity(i);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        boolean updated = false;
        if (key.equals("pref_proxy_ip")) {
            params.put("pref_proxy_ip", prefs.getString("pref_proxy_ip", "192.168.2.32"));
            updated = true;
        } else if (key.equals("pref_proxy_port")) {
            params.put("pref_proxy_port", prefs.getString("pref_proxy_port", "5060"));
            updated = true;
        } else if (key.equals("pref_sip_user")) {
            params.put("pref_sip_user", prefs.getString("pref_sip_user", "bob"));
            updated = true;
        } else if (key.equals("pref_sip_password")) {
            params.put("pref_sip_password", prefs.getString("pref_sip_password", "1234"));
            updated = true;
        }
        if (updated) {
            device.updateParams(params);
        }
    }

    @SuppressWarnings("static-access")
    private void initializeSipFromPreferences() {
        params.put("pref_proxy_ip", prefs.getString("pref_proxy_ip", "192.168.2.32"));
        params.put("pref_proxy_port", prefs.getString("pref_proxy_port", "5080"));
        params.put("pref_sip_user", prefs.getString("pref_sip_user", "bob"));
        params.put("pref_sip_password", prefs.getString("pref_sip_password", "1234"));
        device.updateParams(params);
    }

    // Helpers
    private void showOkAlert(final String title, final String detail) {
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle(title);
        alertDialog.setMessage(detail);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }

    // Activity lifetime
    @Override
    protected void onStart() {
        super.onStart();
        // The activity is about to become visible.
        Log.i(TAG, "%% onStart");
    }
    @Override
    protected void onResume() {
        super.onResume();
        // The activity has become visible (it is now "resumed").
        Log.i(TAG, "%% onResume");
    }
    @Override
    protected void onPause() {
        super.onPause();
        // Another activity is taking focus (this activity is about to be "paused").
        Log.i(TAG, "%% onPause");
    }
    @Override
    protected void onStop() {
        super.onStop();
        // The activity is no longer visible (it is now "stopped")
        Log.i(TAG, "%% onStop");
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // The activity is about to be destroyed.
        Log.i(TAG, "%% onDestroy");
    }
}
