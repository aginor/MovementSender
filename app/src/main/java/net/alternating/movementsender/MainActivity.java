package net.alternating.movementsender;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class MainActivity extends ActionBarActivity implements SensorEventListener {

    private TextView hostView;
    private TextView portView;
    private DatagramSocket socket;
    private String host = "goddess";
    private int port = 22222;
    private Button sendButton;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private Sensor accelSensor;
    private final String TAG = "net.alternating.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        hostView = (TextView) findViewById(R.id.host);
        portView = (TextView) findViewById(R.id.port);
        sendButton = (Button) findViewById(R.id.send_button);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("BUTTON", "Trying to send data");
                new SendRotationTask().execute(0f, 0f, 0f, 0f, 0f);
            }
        });


        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorManager.registerListener(this,rotationSensor,SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, accelSensor,SensorManager.SENSOR_DELAY_GAME);
    }


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

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private float[] fs = new float[4];
    private long old_ts = -1;

    @Override
    public void onSensorChanged(SensorEvent event) {
        //Log.d("SENSOR", Arrays.toString(event.values));

        if (event.sensor == rotationSensor) {
            SensorManager.getQuaternionFromVector(fs, event.values);
            new SendRotationTask().execute(fs[0],
                    fs[1],
                    fs[2],
                    fs[3]);
        }
        else {
            if (old_ts == -1)
                old_ts = System.currentTimeMillis();
            long diff = System.currentTimeMillis() - old_ts;
            old_ts = System.currentTimeMillis();
            double diff_sec = diff / 1000d;
            Log.w("agi", "diff: "+ diff + " sdiff:" + diff_sec);
            new SendAccelTask().execute(
                    new Float(diff_sec),
                    event.values[0],
                    event.values[1],
                    event.values[2]
            );
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    class SendAccelTask extends AsyncTask<Float, Void,Void> {

        @Override
        protected Void doInBackground(Float... params) {
            ByteBuffer data = ByteBuffer.allocate(4*4*2+1);
            data.order(ByteOrder.BIG_ENDIAN);
            int mul = 10000000;
            data.put((byte) 2);

            for (int i = 0; i < params.length; i++) {
                float f = params[i].floatValue();
                data.putInt(((int) (f * mul)));
                data.putInt((int) f);
            }

            InetAddress addr = null;
            try {
                addr = InetAddress.getByName(host);
                DatagramPacket packet = new DatagramPacket(data.array(),data.array().length, addr, port);
                socket.send(packet);
            } catch (Exception e) {
                Log.d("NETWORK", e.getMessage());
                e.printStackTrace();
            }


            return null;
        }
    }

    class SendRotationTask extends AsyncTask<Float, Void,Void> {

        @Override
        protected Void doInBackground(Float... params) {
            ByteBuffer data = ByteBuffer.allocate(4*4*2+1);
            data.order(ByteOrder.BIG_ENDIAN);
            int mul = 10000000;
            data.put((byte) 1);

            for (int i = 0; i < params.length; i++) {
                float f = params[i].floatValue();
                data.putInt(((int) (f * mul)));
                data.putInt((int) f);
            }

            InetAddress addr = null;
            try {
                addr = InetAddress.getByName(host);
                DatagramPacket packet = new DatagramPacket(data.array(),data.array().length, addr, port);
                socket.send(packet);
            } catch (Exception e) {
                Log.d("NETWORK", e.getMessage());
                e.printStackTrace();
            }


            return null;
        }
    }
}
