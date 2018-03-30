package thilanka.org.companion;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.google.android.things.pio.PeripheralManagerService;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.thilanka.messaging.domain.Message;
import org.thilanka.messaging.domain.Payload;
import org.thilanka.messaging.domain.PeripheralIO;
import org.thilanka.messaging.domain.Topic;

import java.io.IOException;
import java.util.UUID;

/**
 * Entry Point for the MIT App Inventor clients connecting to the Android Things Hardware Devices.
 *
 * @author Thilanka Munasinghe (thilankawillbe@gmail.com)
 */
@SuppressWarnings("ALL")
public class AndroidThingsActivity extends Activity implements MqttCallback {

    /**
     * QoS value set to 2. (QoS property of MQTT).
     */
    public static final int QOS = 2;

    /**
     * Thread sleep time set to 500 miliseconds.
     */
    public static final long SLEEP_TIME = 500;

    /**
     * Log Tag for this class.
     */
    private static final String TAG = AndroidThingsActivity.class.getSimpleName();

    /**
     * Messaging Host used for sending the MQTT messages back and forth.
     */
    private static final String SERVER = "iot.eclipse.org";

    /**
     * Messaging Port used for sending the MQTT messages back and forth.
     */
    private static final String PORT = "1883";

    /**
     * MQTT Client Id used for sending the MQTT messages back and forth.
     */
    private static final String CLIENT_ID = "AndroidThingSubscribingClient";

    /**
     * Shared Preference File for the Board.
     */
    private static final String PROPERTIES_FILE_NAME = "board.properties";

    /**
     * Key used for the board identifier.
     */
    private static final String BOARD_IDENTIFIER = "BOARD_IDENTFIER";

    /**
     * The Unique Identifier that identifies the board. Only the messages sent to this topic will
     * be intercepted.
     */
    private static String sBoardIdentifier;

    /**
     * The MQTT Client.
     */
    private MqttClient mMqttClient;

    /**
     * Client connection options that includes auto reconnect.
     */
    private MqttConnectOptions mMQTTConnectOptions;

    /**
     * Android Things Peripheral Manager Service Instance.
     */
    private PeripheralManagerService mPeripheralManagerService;

    /**
     * The GPIO Handler.
     */
    private GpioHandler mGpioHandler;

    /**
     * The PWM Handler.
     */
    private PwmHandler mPwmHandler;

    /**
     * The Temperature Sensor Handler.
     */
    private TemperatureSensorHandler mTemperatureSensorHandler;

    /**
     * The Constructor.
     * @throws MqttException
     */
    public AndroidThingsActivity() throws MqttException {
        mMQTTConnectOptions = new MqttConnectOptions();
        mPeripheralManagerService = new PeripheralManagerService();
        String serverUrl = "tcp://" + SERVER + ":" + PORT;
        mMqttClient = new MqttClient(serverUrl, CLIENT_ID, new MemoryPersistence());
    }

    /**
     * Set up the necessary properties for the communication protocol.
     */
    private void setup() {
        mMQTTConnectOptions.setCleanSession(true);
        mMQTTConnectOptions.setAutomaticReconnect(true);

        /* Instantiate the Sharedpreference instance. */
        SharedPreferences sharedPrefs = getApplicationContext().getSharedPreferences
                (PROPERTIES_FILE_NAME, Context.MODE_PRIVATE);

        String existingBoardIdentifier = sharedPrefs.getString(BOARD_IDENTIFIER, null);

        if (existingBoardIdentifier == null) {
            /* Instantiate the editor instance. */
            SharedPreferences.Editor editor = sharedPrefs.edit();

            sBoardIdentifier = UUID.randomUUID().toString();

            /* Add values to the preferences file. */
            editor.putString(BOARD_IDENTIFIER, sBoardIdentifier);

            /*
            This step is very important and it ensures that the values added to the file will
            actually persist. Commit the above data into the preference file.
            */
            editor.apply();

            Log.d(TAG, "Generated a new Board Identifier and upated the shared preferences. The " +
                    "board identifier : " + sBoardIdentifier);

        } else {
            sBoardIdentifier = existingBoardIdentifier;
            Log.d(TAG, "Retrieved the Board Identifier from shared preferences : " +
                    sBoardIdentifier);
        }

        mGpioHandler = new GpioHandler(this, mMqttClient, mPeripheralManagerService);
        mPwmHandler = new PwmHandler(this, mMqttClient, mPeripheralManagerService);
        mTemperatureSensorHandler = new TemperatureSensorHandler(this, mMqttClient);

        Log.i(TAG, "*******************************************");
        Log.i(TAG, "Please use the following values when configuring your MIT App Inventor App.");
        Log.i(TAG, "Board Identifier = " + sBoardIdentifier);
        Log.i(TAG, "Hardware Platform Board = " + Build.MODEL);
        Log.i(TAG, "Messaging Host = " + SERVER);
        Log.i(TAG, "Messaging Port = " + PORT);
        Log.i(TAG, "*******************************************");

    }

    /**
     * Retrieve the board identifier, so that the handlers will have access.
     * @return the board identifier.
     */
    public static String getBoardIdentfier() {
        return sBoardIdentifier;
    }

    @Override
    protected void onStart() {
        super.onStart();

        /* Begin listening for interrupt events */
        try {
            mGpioHandler.registerGpioCallback();
        } catch (IOException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }

    @Override
    protected void onCreate(Bundle pSavedInstanceState) {
        super.onCreate(pSavedInstanceState);
        setContentView(R.layout.activity_main);
        setup();
        connectMQTTClient();
    }

    /**
     * Connect the MQTT client by setting this class to handle the callback events.
     */
    private void connectMQTTClient() {
        try {
            mMqttClient.setCallback(this);
            mMqttClient.connect(mMQTTConnectOptions);
            try {
                Thread.sleep(SLEEP_TIME);
            } catch (InterruptedException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }

            Log.d(TAG, "Listening to MIT App Inventor messages on " + getSubscribeTopic() );
            mMqttClient.subscribe(getSubscribeTopic(), QOS);

        } catch (MqttException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }

    @Override
    public void connectionLost(Throwable pCause) {
        Log.e(TAG, "Connection with the MIT App Inventor Client Lost!");
        connectMQTTClient();
        Log.d(TAG, "Successfully reconnected.");
    }

    /**
     * The logic on what to do when a message has arrived.
     * The payload is constructed from the android-things-messages library.
     * It may look like this:
     * {"mDirection":"OUT","mName":"GPIO_34","mProperty":"PIN_STATE","mValue":"LOW"}
     * @param pTopic
     * @param pMessage
     */
    @Override
    public void messageArrived(String pTopic, MqttMessage pMessage) {
        Log.d(TAG, "Message " + pMessage + " on topic " + pTopic + " arrived.");

        if (!pTopic.equals(getSubscribeTopic())) {
            /* No need to take any action if this is not the topic we want. */
            return;
        }

        String payloadStr = new String(pMessage.getPayload());

        Payload payload = Message.deconstrctMessage(payloadStr);
        PeripheralIO peripheralIOType = payload.getPeripheralIO();

        try {
            switch(peripheralIOType){
                case GPIO:
                    mGpioHandler.handleMessage(payload);
                    break;
                case PWM:
                    mPwmHandler.handleMessage(payload);
                    break;
                case TEMPERATURE_SENSOR:
                    mTemperatureSensorHandler.handleMessage(payload);
                    break;
                default:
                    Log.d(TAG, "Message not supported!");
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken pToken) {
        Log.d(TAG, "Delivery of the message has completed. Delievery complete = " + pToken
                .isComplete());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGpioHandler.closeOpenGpioPins();
    }

    @Override
    protected void onStop() {
        super.onStop();
        /* Interrupt events no longer necessary */
        mGpioHandler.unregisterGpioCallback();
    }

    /**
     * The Topic the MQTTClient should publish to.
     * @return the topic
     */
    public static String getPublishTopic() {
        return AndroidThingsActivity.getBoardIdentfier() + Topic.APP_INVENTOR;
    }

    /**
     * The Topic the MQTTClient should subscribe to.
     * @return the topic
     */
    public static String getSubscribeTopic() {
        return AndroidThingsActivity.getBoardIdentfier() + Topic.ANDROID_THINGS;
    }

}
