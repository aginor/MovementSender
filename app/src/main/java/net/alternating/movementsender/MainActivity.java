package net.alternating.movementsender;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
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
    private Button logButton;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private Sensor accelSensor;
    private final String TAG = "MainActivity";
    private boolean loggingEnabled = false;
    private PrintStream rotationStream;
    private PrintStream accelerationStream;

    private static final String rotationFilename = "rotationFile.txt";
    private static final String accelerationFilename = "accelerationFile.txt";

    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        hostView = (TextView) findViewById(R.id.host);
        portView = (TextView) findViewById(R.id.port);
        logButton = (Button) findViewById(R.id.log_button);

        logButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!loggingEnabled) {
                    Log.d(TAG, "Starting Logging");
                    loggingEnabled = true;
                    logButton.setText("Stop Logging");
                    try {
                        if (isExternalStorageWritable()) {
                            Log.d(TAG,"External storage for logging");
                            File rotationFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), rotationFilename);
                            File accelerationFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), accelerationFilename);
                            if (rotationFile.exists())
                                rotationFile.delete();
                            rotationFile.createNewFile();
                            if (accelerationFile.exists())
                                accelerationFile.delete();
                            accelerationFile.createNewFile();
                            Log.d(TAG,rotationFile.getAbsolutePath());
                            rotationStream = new PrintStream(new FileOutputStream(rotationFile));
                            accelerationStream = new PrintStream(new FileOutputStream(accelerationFile));

                            rotationFile.setReadable(true,false);
                            accelerationFile.setReadable(true,false);
                        }
                        else {
                            Log.d(TAG, "Internal storage for logging");
                            rotationStream = new PrintStream(openFileOutput(rotationFilename, Context.MODE_WORLD_READABLE));
                            accelerationStream = new PrintStream(openFileOutput(accelerationFilename, Context.MODE_WORLD_READABLE));
                        }
                    } catch (Exception e) {
                        loggingEnabled = false;
                        Log.e(TAG, "Failed to start Logging!!");
                        Log.e(TAG,e.getMessage());
                        e.printStackTrace();
                        logButton.setText("Logging broke");
                        logButton.setClickable(false);
                    }
                }
                else {
                    try {
                        Log.d(TAG, "Stopping Logging");
                        rotationStream.flush();
                        rotationStream.close();
                        rotationStream = null;
                        accelerationStream.flush();
                        accelerationStream.close();
                        accelerationStream = null;
                        loggingEnabled = false;
                        logButton.setText("Start Logging");
                    } catch (Exception e) {
                        Log.e(TAG, "Error while stopping logging!!");
                        Log.e(TAG,e.getMessage());
                        e.printStackTrace();
                    }
                }
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


    private long old_ts = -1;

    @Override
    public void onSensorChanged(SensorEvent event) {
        //Log.d("SENSOR", Arrays.toString(event.values));
        float[] fs = new float[4];
        if (event.sensor == rotationSensor) {
            SensorManager.getQuaternionFromVector(fs, event.values);
            new SendRotationTask().execute(fs[0],
                    fs[1],
                    fs[2],
                    fs[3]);
            if (loggingEnabled) {
                try {
                    rotationStream.println(System.currentTimeMillis() + "," + fs[0] + "," + fs[1] + "," + fs[2] + "," + fs[3]);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to write rotation log data!!");
                    Log.e(TAG, e.getMessage());
                    e.printStackTrace();
                    loggingEnabled = false;
                    logButton.setText("Logging broke");
                    logButton.setClickable(false);
                }
            }
        }
        else {
            if (old_ts == -1)
                old_ts = System.currentTimeMillis();
            long diff = System.currentTimeMillis() - old_ts;
            old_ts = System.currentTimeMillis();
            double diff_sec = diff / 1000d;

            new SendAccelTask().execute(
                    new Float(diff_sec),
                    event.values[0],
                    event.values[1],
                    event.values[2]
            );
            if (loggingEnabled) {
                try {
                    accelerationStream.println(System.currentTimeMillis() + "," +
                            event.values[0] + "," +
                            event.values[1] + "," +
                            event.values[2]);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to write acceleration log data!!");
                    Log.e(TAG, e.getMessage());
                    e.printStackTrace();
                    loggingEnabled = false;
                    logButton.setText("Logging broke");
                    logButton.setClickable(false);
                }
            }
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
