package thilanka.org.companion;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManagerService;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.thilanka.messaging.domain.Topic;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.UUID;

/**
 * Entry Point for the MIT App Inventor clients connecting to the Android Things Hardware Devices.
 *
 * @author Thilanka Munasinghe (thilankawillbe@gmail.com)
 */
public class AndroidThingsActivity extends Activity implements MqttCallback {

    private static final String TAG = "AndroidThingsActivity";
    private static final String SERVERURI = "tcp://iot.eclipse.org:1883";
    private static final java.lang.String CLIENT_ID = "AndroidThingSubscribingClient";
    private static final String PROPERTIES_FILE_NAME = "board.properties";

    private String mBoardIdentifier;

    private PeripheralManagerService service;

    private MqttClient client;

    private Map<Gpio, String> inputPinsMap;

    private GpioCallback mGpioCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            // Read the active low pin state
            try {
                String pinName = inputPinsMap.get(gpio);
                if (gpio.getValue()) {
                    // Pin is LOW
                    Log.d(TAG, "Pin "+pinName+" is Low/OFF.");
                } else {
                    // Pin is HIGH
                    Log.d(TAG, "Pin "+pinName+" is High/ON.");
                }

                String pubMsg = "EVENT" + ":" + pinName + ":" + "FALSE" + ":" + (gpio.getValue() ? "OFF" : "ON") ;
                int pubQoS = 0;
                MqttMessage message = new MqttMessage(pubMsg.getBytes());
                message.setQos(pubQoS);
                message.setRetained(false);

                // Publish the message
                System.out.println("Publishing to topic \"" + getInternalTopic() + "\" qos " + pubQoS);
                MqttDeliveryToken token = null;
                try {
                    // publish message to broker
                    MqttTopic topic = client.getTopic(getInternalTopic());
                    token = topic.publish(message);
                    // Wait until the message has been delivered to the broker
                    token.waitForCompletion();
                    Thread.sleep(100);
                } catch (Exception e) {
                    e.printStackTrace();
                }


            } catch (IOException e) {
                e.printStackTrace();
            }

            // Continue listening for more interrupts
            return true;
        }

        @Override
        public void onGpioError(Gpio gpio, int error) {
            Log.w(TAG, gpio + ": Error event " + error);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBoardIdentifier = UUID.randomUUID().toString();

        Log.i(TAG, "The Board Identifier is "+ mBoardIdentifier + ". Please use this same identifier on App Inventor.");

        inputPinsMap = new HashMap<>();

        service = new PeripheralManagerService();
        Log.d(TAG, "Available GPIO: " + service.getGpioList());

        try {
            client = new MqttClient(SERVERURI, CLIENT_ID, new MemoryPersistence());

            client.setCallback(this);
            client.connect();

            Log.d(TAG, "Subscribing to " + getInternalTopic());
            client.subscribe(getInternalTopic());
            Log.d(TAG, "Subscribed to " + getInternalTopic());

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

        if (!topic.equals(getInternalTopic())) {
            /**
             * No need to take any action if this is not the topic we want.
             */
            return;
        }

        /**
         * The payload is of this format
         * <Message Type>:<GPIO pin name>:<Is output?>:<[Initial] Pin state>
         * For example the following message payload "EVENT:GPIO_34:TRUE:ON" symbolizes that the "GPIO_34" pin should be turned on.
         */
        String payload = new String(message.getPayload());
        StringTokenizer stringTokenizer = new StringTokenizer(payload, ":");

        if (stringTokenizer.countTokens() != 4) {
            String errorMessage = "Invalid Message :" + payload + ". The message should include the Pin Name and the desired Pin State.";
            Log.e(TAG, errorMessage);
            throw new RuntimeException(errorMessage);
        }

        String messageType = stringTokenizer.nextToken();
        String pinName = stringTokenizer.nextToken();
        boolean isPinOutput = stringTokenizer.nextToken().equals("TRUE");
        String pinState = stringTokenizer.nextToken();

        switch (messageType) {
            case "REGISTER":
                Log.d(TAG, "Received a Pin Registration triggered from App Inventor.");
                if (!isPinOutput) {
                    Log.d(TAG, "Registering pin " + pinName + " as an input " + " with initial state " + pinState);
                    Gpio inputPin = service.openGpio(pinName);
                    // Initialize the pin as an input
                    inputPin.setDirection(Gpio.DIRECTION_IN);
                    // High voltage is considered active
                    inputPin.setActiveType(Gpio.ACTIVE_HIGH);
                    // Set the initial value for the pin
                    inputPin.setValue(pinState.equals("ON"));
                    // Register for all state changes
                    inputPin.setEdgeTriggerType(Gpio.EDGE_BOTH);
                    inputPin.registerGpioCallback(mGpioCallback);
                    inputPinsMap.put(inputPin, pinName);
                } else {
                    Log.d(TAG, "The pin " + pinName + " is an output pin. Nothing to do here.");
                }
                break;
            case "EVENT":
                Log.d(TAG, "Received a Pin Event triggered from App Inventor.");
                // Create GPIO connection for the pin.
                Gpio pin = service.openGpio(pinName);
                if (isPinOutput) {
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
                } else {
                    Log.d(TAG, "The designated pin " + pinName + " is not an output. Nothing to do here!");
                }
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

    private String getInternalTopic() {
        StringBuilder topicBuilder = new StringBuilder();
        topicBuilder.append(Topic.INTERNAL.toString());
        topicBuilder.append(mBoardIdentifier);
        return topicBuilder.toString();
    }
}
