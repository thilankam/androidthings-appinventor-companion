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
import org.thilanka.messaging.domain.Action;
import org.thilanka.messaging.domain.HeaderPin;
import org.thilanka.messaging.domain.Message;

import java.util.UUID;

/**
 * Entry Point for the MIT App Inventor clients connecting to the Android Things Hardware Devices.
 *
 * @author Thilanka Munasinghe (thilankawillbe@gmail.com)
 */
public class AndroidThingsActivity extends Activity implements MqttCallback {

    public static final int QOS = 2; // QoS value set to 2. (QoS property of MQTT).
    public static final long SLEEP_TIME = 500; // Thread sleep time set to 500 miliseconds.

    private static final String TAG = "AndroidThingsActivity";
    private static final String SERVER = "iot.eclipse.org";
    private static final String PORT = "1883";
    private static final String CLIENT_ID = "AndroidThingSubscribingClient";
    private static final String PROPERTIES_FILE_NAME = "board.properties"; // companion board.
    private static final String BOARD_IDENTFIER = "BOARD_IDENTFIER";

    private static String sBoardIdentifier;

    private MqttClient mMqttClient;
    private MqttConnectOptions mMQTTConnectOptions;
    private PeripheralManagerService mPeripheralManagerService;
    private GpioHandler mGpioHandler;

    public AndroidThingsActivity() throws MqttException {
        mMQTTConnectOptions = new MqttConnectOptions();
        mPeripheralManagerService = new PeripheralManagerService();
        String serverUrl = "tcp://" + SERVER  + ":" + "1883";
        mMqttClient = new MqttClient(serverUrl, CLIENT_ID, new MemoryPersistence());
        mGpioHandler = new GpioHandler(mMqttClient, mPeripheralManagerService);
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

        Log.i(TAG, "*******************************************");
        Log.i(TAG, "Please use the following values when configuring your MIT App Inventor App.");
        Log.i(TAG, "Board Identifier = "+ sBoardIdentifier );
        Log.i(TAG, "Hardware Platform Board = "+ Build.MODEL );
        Log.i(TAG, "Messaging Host = "+ SERVER );
        Log.i(TAG, "Messaging Port = "+ PORT );
        Log.i(TAG, "*******************************************");
    }

    public static String getBoardIdentfier(){
        return sBoardIdentifier;
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

        switch (messageType) {
            case REGISTER:
                mGpioHandler.handlerRegisterPin(pin);
                break;
            case EVENT:
                mGpioHandler.handlePinEvent(pin);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGpioHandler.closeOpenGpioPins();
    }
}
