package thilanka.org.companion;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.IOException;
import java.util.StringTokenizer;

import static android.content.ContentValues.TAG;

/**
 * Entry Point for the MIT App Inventor clients connecting to the Android Things Hardware Devices.
 *
 * @author Thilanka Munasinghe (thilankawillbe@gmail.com)
 */
public class AndroidThingsActivity extends Activity implements MqttCallback {

    private static final String TAG = "AndroidThingsActivity";
    /*
    TODO: this topic has to be unique to App Inventor and also for the specific board the companion is installed and is being used
    */
    private static final String TOPIC = "AppInventor";

    private PeripheralManagerService service;

    private MqttClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        service = new PeripheralManagerService();
        Log.d(TAG, "Available GPIO: " + service.getGpioList());

        try {
            client = new MqttClient("tcp://iot.eclipse.org:1883", "AndroidThingSub", new MemoryPersistence());

            client.setCallback(this);
            client.connect();

            client.subscribe(TOPIC);
            Log.d(TAG, "Subscribed to " + TOPIC);

        } catch (MqttException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void connectionLost(Throwable cause) {
        Log.e(TAG, "Connection with the MIT App Inventor Client Lost!");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        Log.d(TAG, "The following message " + message + " on topic " + topic + " arrived.");

        if (!topic.equals(TOPIC)){
            /**
             * No need to take any action if this is not the topic we want.
             */
            return;
        }

        /**
         * The payload is of this format: GPIO_34:ON symbolizing that the "GPIO_34" pin should be turned on.
         */
        String payload = new String(message.getPayload());
        StringTokenizer stringTokenizer = new StringTokenizer(payload, ":");

        if (stringTokenizer.countTokens() != 2){
            String errorMessage = "Invalid Message :" +payload +". The message should include the Pin Name and the desired Pin State.";
            Log.e(TAG, errorMessage);
            throw new RuntimeException(errorMessage);
        }

        String pinName = stringTokenizer.nextToken();
        String pinState = stringTokenizer.nextToken();

        // Create GPIO connection for LED.
        Gpio pin = service.openGpio(pinName);

        switch (pinState) {
            case "ON":
                Log.d(TAG, "PIN ON");
                pin.setValue(true);
                break;
            case "OFF":
                Log.d(TAG, "PIN OFF");
                pin.setValue(false);
                break;
            default:
                Log.d(TAG, "Message not supported!");
                break;
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        Log.d(TAG, "Delivery of the message has completed. Received token " + token);
    }
}
