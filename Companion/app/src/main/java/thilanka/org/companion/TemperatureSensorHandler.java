package thilanka.org.companion;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import com.google.android.things.contrib.driver.bmx280.Bmx280;
import com.google.android.things.contrib.driver.bmx280.Bmx280SensorDriver;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.thilanka.device.pin.PinProperty;
import org.thilanka.messaging.domain.Action;
import org.thilanka.messaging.domain.Message;
import org.thilanka.messaging.domain.Payload;
import org.thilanka.messaging.domain.PeripheralIO;

import java.io.IOException;

/**
 * The logic that handles Temperature Sensor events.
 *
 * @author Thilanka Munasinghe (thilankawillbe@gmail.com)
 */
public class TemperatureSensorHandler implements SensorEventListener {

    /* The Log Tag*/
    private static final String TAG = GpioHandler.class.getSimpleName();

    /* The MQTT Client*/
    private final MqttClient mMqttClient;

    /* The static reference to the parent activity to run things in the foreground */
    private static AndroidThingsActivity sParent;

    /**
     * Third Party Sensor Driver.
     */
    private Bmx280SensorDriver mTemperatureSensorDriver;

    /**
     * Android Sensor Manager.
     */
    private SensorManager mSensorManager;

    /**
     * The Dynamic Sensor Callback.
     */
    private SensorManager.DynamicSensorCallback mDynamicSensorCallback = new SensorManager.DynamicSensorCallback() {
        @Override
        public void onDynamicSensorConnected(Sensor sensor) {
            if (sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                Log.i(TAG, "Temperature sensor connected");
                mSensorManager.registerListener(TemperatureSensorHandler.this,
                        sensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    };

    /**
     * The Constructor.
     * @param pAndroidThingsActivity
     * @param pMqttClient
     */
    public TemperatureSensorHandler(AndroidThingsActivity pAndroidThingsActivity, MqttClient pMqttClient) {
        mMqttClient = pMqttClient;
        sParent = pAndroidThingsActivity;
    }

    /**
     * Handle the messages intended for GPIO.
     * @param pPayload
     * @throws IOException
     */
    public void handleMessage(Payload pPayload) throws IOException {
        Action messageType = pPayload.getAction();
        switch (messageType) {
            case REGISTER:
                handleRegister();
                break;
            case MONITOR:
                handleMonitor();
                break;
            default:
                Log.d(TAG, "Message not supported!");
                break;
        }
    }

    /**
     * Handle Temperature Monitor Requests.
     * @throws IOException
     */
    private void handleMonitor() throws IOException {
        mSensorManager = (SensorManager) sParent.getSystemService(Context.SENSOR_SERVICE);
        mSensorManager.registerDynamicSensorCallback(mDynamicSensorCallback);
        mTemperatureSensorDriver = new Bmx280SensorDriver(BoardDefaults.getI2CPort());
        mTemperatureSensorDriver.registerTemperatureSensor();
   }

    /**
     * Handle one time temperature poll requests.
     * @throws IOException
     */
    private void handleRegister() throws IOException {
        Bmx280 bmx280 = new Bmx280(BoardDefaults.getI2CPort());
        bmx280.setTemperatureOversampling(Bmx280.OVERSAMPLING_1X);
        publishTemperature(bmx280.readTemperature());
    }

    /**
     * Publish the obtained temperature to App Inventor.
     * @param pTemperature
     */
    private void publishTemperature(double pTemperature) {
        Payload payload = new Payload();
        payload.setPeripheralIO(PeripheralIO.GPIO);
        payload.setName(PeripheralIO.TEMPERATURE_SENSOR.getName());
        payload.setProperty(PinProperty.TEMPERATURE);
        payload.setDoubleValue(pTemperature);

        String messageStr = Message.constructMessage(payload);

        MqttMessage message = new MqttMessage(messageStr.getBytes());
        message.setQos(AndroidThingsActivity.QOS);
        message.setRetained(false);

        // Publish the message
        Log.d(TAG,"Publishing to topic \"" + AndroidThingsActivity.getPublishTopic()
                + "\" qos " + AndroidThingsActivity.QOS + ", the message = " + messageStr);
        try {
            mMqttClient.publish(AndroidThingsActivity.getPublishTopic(),
                    message);
            Thread.sleep(AndroidThingsActivity.SLEEP_TIME);
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
        }

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.i(TAG, "sensor changed: " + event.values[0] + " : " + event.values[1] + " : " +
                event.values[2]);
        publishTemperature(event.values[0]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.i(TAG, "sensor accuracy changed: " + accuracy);
    }
}
