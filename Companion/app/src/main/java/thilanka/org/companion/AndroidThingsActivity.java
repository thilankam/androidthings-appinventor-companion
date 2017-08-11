package thilanka.org.companion;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
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
import org.thilanka.messaging.domain.Topic;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
    private static final String PROPERTIES_FILE_NAME = "board.properties";
    private static final int QOS = 2;
    private static final long SLEEP_TIME = 500;
    private static final String BOARD_IDENTFIER = "BOARD_IDENTFIER";

    private String mBoardIdentifier;

    private PeripheralManagerService mPeripheralManagerService = new PeripheralManagerService();

    private MqttClient mMqttClient;

    private Map<Gpio, String> mInputPinsMap;

    private MqttConnectOptions mMQTTConnectOptions;

    private BiMap<String, Gpio> mOpenPinMap = HashBiMap.create();

    private GpioCallback mGpioCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            // Read the active low pin state
            try {
                String pinName = mInputPinsMap.get(gpio);
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
                System.out.println("Publishing to topic \"" + getInternalTopic() + "\" qos " + QOS);
                try {
                    // publish message to broker
                    mMqttClient.publish(getInternalTopic(), message);
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

    private void setup(){
        mMQTTConnectOptions = new MqttConnectOptions();
        mMQTTConnectOptions.setCleanSession(true);
        mMQTTConnectOptions.setAutomaticReconnect(true);

        // INSTANTIATE THE SHAREDPREFERENCE INSTANCE
        SharedPreferences sharedPrefs = getApplicationContext().getSharedPreferences(PROPERTIES_FILE_NAME, Context.MODE_PRIVATE);

        String existingBoardIdentifier = sharedPrefs.getString(BOARD_IDENTFIER, null);

        if (existingBoardIdentifier == null){
            // INSTANTIATE THE EDITOR INSTANCE
            SharedPreferences.Editor editor = sharedPrefs.edit();

            mBoardIdentifier = UUID.randomUUID().toString();

            // ADD VALUES TO THE PREFERENCES FILE
            editor.putString(BOARD_IDENTFIER, mBoardIdentifier);

            // THIS STEP IS VERY IMPORTANT. THIS ENSURES THAT THE VALUES ADDED TO THE FILE WILL ACTUALLY PERSIST
            // COMMIT THE ABOVE DATA TO THE PREFERENCE FILE
            editor.commit();

            Log.d(TAG, "Generated a new Board Identifier and upated the shared preferences : " + mBoardIdentifier);

        } else {
            mBoardIdentifier = existingBoardIdentifier;
            Log.d(TAG, "Retrieved the Board Identifier from shared preferences : " + mBoardIdentifier);
        }

        Log.i(TAG, "The Board Identifier is "+ mBoardIdentifier + ". Please use this same identifier on App Inventor.");

        mInputPinsMap = new HashMap<>();

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

            mMqttClient.setCallback(new AndroidThingsActivity());

            mMqttClient.connect(mMQTTConnectOptions);

            try {
                Thread.sleep(SLEEP_TIME);
            } catch (InterruptedException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }

            Log.d(TAG, "Subscribing to " + getInternalTopic());
            mMqttClient.subscribe(getInternalTopic(),QOS);
            Log.d(TAG, "Subscribed to " + getInternalTopic());

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

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        Log.d(TAG, "The following message " + message + " on topic " + topic + " arrived.");

        Log.d(TAG,"getInternalTopic()=" +getInternalTopic());

        //TODO: Remove the comment
//        if (!topic.equals(getInternalTopic())) {
//            /**
//             * No need to take any action if this is not the topic we want.
//             */
//            Log.d(TAG, "BAD");
//            return;
//        }

        /**
         * The payload is constructed from the android-things-messages library.
         * It may look like this:
         * {"mDirection":"OUT","mName":"GPIO_34","mProperty":"PIN_STATE","mValue":"LOW"}
         */
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
                    mInputPinsMap.put(inputPin, pinName);
                } else {
                    Log.d(TAG, "The pin " + pinName + " is an output pin. Nothing to do here.");
                }
                break;
            case EVENT:
                Log.d(TAG, "Received a Pin Event triggered from App Inventor.");
                // Create GPIO connection for the pin.
                if (pinDirection == PinDirection.OUT) {
                    Log.d(TAG, "Inside PinDirection Conditional.");
                    Gpio gpioPin = openPin(pinName);

                    switch (pinValue) {
                        case HIGH:
                            Log.d(TAG, "PIN ON");
                            gpioPin.setValue(true);
                            break;
                        case LOW:
                            Log.d(TAG, "PIN OFF");
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
        Gpio gpioPin = null;
        if (mOpenPinMap.containsKey(pinName)){
            gpioPin = mOpenPinMap.get(pinName);
            try {
                Log.d(TAG, "Closing existing pin " + pinName);
                gpioPin.close();
                gpioPin = null;
                mOpenPinMap.remove(pinName);
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
            mOpenPinMap.put(pinName, gpioPin);
        } catch (IOException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        return gpioPin;
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

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Set<Gpio> openGpioPins = mOpenPinMap.values();
        Iterator<Gpio> iterator = openGpioPins.iterator();
        while (iterator.hasNext()){
            Gpio pin = iterator.next();
            if (pin != null) {
                try {
                    pin.close();
                    mOpenPinMap.remove(pin);
                    pin = null;
                } catch (IOException e) {
                    Log.w(TAG, "Unable to close GPIO " + pin.toString(), e);
                }
            }
        }
    }
}
