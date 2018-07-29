package thilanka.org.companion;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.SensorManager.DynamicSensorCallback;
import android.os.Bundle;
import android.util.Log;

import com.google.android.things.contrib.driver.bmx280.Bmx280;
import com.google.android.things.contrib.driver.bmx280.Bmx280SensorDriver;

import java.io.IOException;

/**
 * TemperatureActivity is an example that use the driver for the BMP280 temperature sensor.
 */
public class TemperatureActivity extends Activity implements SensorEventListener {
    private static final String TAG = TemperatureActivity.class.getSimpleName();

    private Bmx280SensorDriver mTemperatureSensorDriver;
    private SensorManager mSensorManager;

    private DynamicSensorCallback mDynamicSensorCallback = new DynamicSensorCallback() {
        @Override
        public void onDynamicSensorConnected(Sensor sensor) {
            if (sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                Log.i(TAG, "Temperature sensor connected");
                mSensorManager.registerListener(TemperatureActivity.this,
                        sensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Starting TemperatureActivity");

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
 //       mSensorManager.registerDynamicSensorCallback(mDynamicSensorCallback);

        try {
//            mTemperatureSensorDriver = new Bmx280SensorDriver(BoardDefaults.getI2CPort());
//            mTemperatureSensorDriver.registerTemperatureSensor();

            Bmx280 bmx280 = new Bmx280(BoardDefaults.getI2CPort());
            bmx280.setTemperatureOversampling(Bmx280.OVERSAMPLING_1X);

            float temperature = bmx280.readTemperature();

            Log.d(TAG, "temperature = "+ temperature);

        } catch (IOException e) {
            Log.e(TAG, "Error configuring sensor", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Closing sensor");
        if (mTemperatureSensorDriver != null) {
            mSensorManager.unregisterDynamicSensorCallback(mDynamicSensorCallback);
            mSensorManager.unregisterListener(this);
            mTemperatureSensorDriver.unregisterTemperatureSensor();
            try {
                mTemperatureSensorDriver.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing sensor", e);
            } finally {
                mTemperatureSensorDriver = null;
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.i(TAG, "sensor changed: " + event.values[0] + " : " + event.values[1] + " : " +
                event.values[2]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.i(TAG, "sensor accuracy changed: " + accuracy);
    }
}