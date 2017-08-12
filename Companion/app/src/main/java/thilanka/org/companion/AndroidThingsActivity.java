package thilanka.org.companion;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.thilanka.device.pin.PinDirection;
import org.thilanka.device.pin.PinValue;
import org.thilanka.messaging.domain.Action;
import org.thilanka.messaging.domain.HeaderPin;
import org.thilanka.messaging.domain.Message;

import java.io.IOException;
import java.util.Set;
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
    private static final String PROPERTIES_FILE_NAME = "board.properties"; // companion board.
    private static final int QOS = 2; // QoS value set to 2. (QoS property of MQTT).
    private static final long SLEEP_TIME = 500; // Thread sleep time set to 500 miliseconds.
    private static final String BOARD_IDENTFIER = "BOARD_IDENTFIER";

    private static String sBoardIdentifier;

    private MqttClient mMqttClient;
    private MqttConnectOptions mMQTTConnectOptions;
    private BiMap<String, Gpio> mInputPinsMap;
    private BiMap<String, Gpio> mOutputPinsMap;
    private PeripheralManagerService mPeripheralManagerService;

    private GpioCallback mGpioCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            // Read the active low pin state
            try {
                String pinName = mInputPinsMap.inverse().get(gpio);
                if (gpio.getValue()) {
                    // Pin is LOW
                    Log.d(TAG, "Pin "+pinName+" is Low/OFF.");
                } else {
                    // Pin is HIGH
                    Log.d(TAG, "Pin "+pinName+" is High/ON.");
                }

                String pubMsg = "EVENT" + ":" + pinName + ":" + "FALSE" + ":" + (gpio.getValue() ? "OFF" : "ON") ;
                MqttMessage message = new MqttMessage(pubMsg.getBytes());
                message.setQos(QOS);
                message.setRetained(false);

                // Publish the message
                System.out.println("Publishing to topic \"" + sBoardIdentifier + "\" qos " + QOS);
                try {
                    // publish message to broker
                    mMqttClient.publish(sBoardIdentifier, message);
                    Thread.sleep(SLEEP_TIME);
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

    public AndroidThingsActivity() {
        mMQTTConnectOptions = new MqttConnectOptions();
        mInputPinsMap = HashBiMap.create();
        mOutputPinsMap = HashBiMap.create();
        mPeripheralManagerService = new PeripheralManagerService();
    }

    private void setup(){
        mMQTTConnectOptions.setCleanSession(true);
        mMQTTConnectOptions.setAutomaticReconnect(true);

        /* Instantiate the Sharedpreference instance. */
        SharedPreferences sharedPrefs = getApplicationContext().getSharedPreferences(PROPERTIES_FILE_NAME, Context.MODE_PRIVATE);

        String existingBoardIdentifier = sharedPrefs.getString(BOARD_IDENTFIER, null);

        if (existingBoardIdentifier == null){
            // Instantiate the editor instance.
            SharedPreferences.Editor editor = sharedPrefs.edit();

            sBoardIdentifier = UUID.randomUUID().toString();

            // Add values to the preferences file.
            editor.putString(BOARD_IDENTFIER, sBoardIdentifier);

            // This step is very important and it ensures that the values added to the file will actually persist.
            // commit the above data into the preference file.
            editor.apply();

            Log.d(TAG, "Generated a new Board Identifier and upated the shared preferences. The board identifier : " + sBoardIdentifier);

        } else {
            sBoardIdentifier = existingBoardIdentifier;
            Log.d(TAG, "Retrieved the Board Identifier from shared preferences : " + sBoardIdentifier);
        }

        Log.i(TAG, "The Board Identifier is "+ sBoardIdentifier + ". Please use this same identifier on App Inventor.");
        Log.d(TAG, "Available GPIO: " + mPeripheralManagerService.getGpioList());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setup();
        createMQTTClient();
    }

    private void createMQTTClient() {
        try {
            mMqttClient = new MqttClient(SERVERURI, CLIENT_ID, new MemoryPersistence());

            mMqttClient.setCallback(this);

            mMqttClient.connect(mMQTTConnectOptions);

            try {
                Thread.sleep(SLEEP_TIME);
            } catch (InterruptedException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }

            Log.d(TAG, "Listening to MIT App Inventor messages on " + sBoardIdentifier);
            mMqttClient.subscribe(sBoardIdentifier, QOS);

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        Log.e(TAG, "Connection with the MIT App Inventor Client Lost!");
        createMQTTClient();
        Log.d(TAG, "Successfully reconnected.");
    }

    /**
     * The payload is constructed from the android-things-messages library.
     * It may look like this:
     * {"mDirection":"OUT","mName":"GPIO_34","mProperty":"PIN_STATE","mValue":"LOW"}
     */
    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        Log.d(TAG, "The following message " + message + " on topic " + topic + " arrived.");

        if (!topic.equals(sBoardIdentifier)) {
            /* No need to take any action if this is not the topic we want. */
            return;
        }

        String payload = new String(message.getPayload());

        HeaderPin pin = Message.deconstrctPinMessage(payload);
        Action messageType = pin.getAction();
        String pinName = pin.getName();
        PinDirection pinDirection = pin.getDirection();
        PinValue pinValue = pin.getValue();

        switch (messageType) {
            case REGISTER:
                Log.d(TAG, "Received a Pin Registration triggered from App Inventor.");
                if (pinDirection == PinDirection.IN) {
                    Log.d(TAG, "Registering pin " + pinName + " as an input " + " with initial state " + pinValue);
                    Gpio inputPin = openPin(pinName);
                    // Initialize the pin as an input
                    inputPin.setDirection(Gpio.DIRECTION_IN);
                    // High voltage is considered active
                    inputPin.setActiveType(Gpio.ACTIVE_HIGH);
                    // Set the initial value for the pin
                    inputPin.setValue(pinValue.equals("ON"));
                    // Register for all state changes
                    inputPin.setEdgeTriggerType(Gpio.EDGE_BOTH);
                    inputPin.registerGpioCallback(mGpioCallback);
                    mInputPinsMap.put(pinName, inputPin);
                } else {
                    Log.d(TAG, "The pin " + pinName + " is an output pin. Nothing to do here.");
                }
                break;
            case EVENT:
                Log.d(TAG, "Received a Pin Event triggered from App Inventor.");
                // Create GPIO connection for the pin.
                if (pinDirection == PinDirection.OUT) {
                    Gpio gpioPin = openPin(pinName);

                    switch (pinValue) {
                        case HIGH:
                            Log.d(TAG, "Turning " + pinName +" ON.");
                            gpioPin.setValue(true);
                            break;
                        case LOW:
                            Log.d(TAG, "Turning " + pinName +" OFF.");
                            gpioPin.setValue(false);
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

    private Gpio openPin(String pinName) {
        Gpio gpioPin;
        if (mOutputPinsMap.containsKey(pinName)){
            gpioPin = mOutputPinsMap.get(pinName);
            try {
                Log.d(TAG, "Closing existing pin " + pinName + ".");
                gpioPin.close();
                mOutputPinsMap.remove(pinName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        gpioPin = createNewPin(pinName);
        return gpioPin;
    }

    private Gpio createNewPin(String pinName) {
        Gpio gpioPin = null;
        try {
            gpioPin = mPeripheralManagerService.openGpio(pinName);
            //TODO: Revisit having initially low. We are certain about the direction being OUT here.
            gpioPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mOutputPinsMap.put(pinName, gpioPin);
        } catch (IOException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        return gpioPin;
    }


    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        Log.d(TAG, "Delivery of the message has completed. Received token " + token);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Set<Gpio> openGpioPins = mOutputPinsMap.values();
        for (Gpio pin : openGpioPins) {
            if (pin != null) {
                try {
                    pin.close();
                    mOutputPinsMap.inverse().remove(pin);
                    pin = null;
                } catch (IOException e) {
                    Log.w(TAG, "Unable to close GPIO " + pin.toString(), e);
                }
            }
        }
    }
}
