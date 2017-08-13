package thilanka.org.companion;

import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.thilanka.device.pin.PinDirection;
import org.thilanka.device.pin.PinProperty;
import org.thilanka.device.pin.PinValue;
import org.thilanka.messaging.domain.Action;
import org.thilanka.messaging.domain.Message;
import org.thilanka.messaging.domain.Payload;
import org.thilanka.messaging.domain.PeripheralIO;

import java.io.IOException;
import java.util.Set;

/**
 * The logic that handles GPIO related activities.
 *
 * @author Thilanka Munasinghe (thilankawillbe@gmail.com)
 */
@SuppressWarnings("ALL")
public class GpioHandler {

    /* The Log Tag*/
    private static final String TAG = GpioHandler.class.getSimpleName();

    /* The MQTT Client*/
    private final MqttClient mMqttClient;

    /* The Android Things Peripheral Manager Service*/
    private final PeripheralManagerService mPeripheralManagerService;

    /* The input pins */
    private BiMap<String, Gpio> mGpioInputPinsMap;

    /* The output pins */
    private BiMap<String, Gpio> mGpioOutputPinsMap;

    /* The static reference to the parent activity to run things in the foreground */
    private static AndroidThingsActivity sParent;

    /* The callback that handles any input events to the GPIO pins that are registered. */
    private GpioCallback mGpioCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio pGpio) {
            super.onGpioEdge(pGpio);
            Log.d(TAG, "Receive GPIO change.");
            // Read the active low pin state
            try {
                String pinName = mGpioInputPinsMap.inverse().get(pGpio);
                boolean gpioValue = pGpio.getValue();
                if (gpioValue) {
                    // Pin is High
                    Log.d(TAG, "Pin " + pinName + " is High/ON.");
                } else {
                    // Pin is LOW
                    Log.d(TAG, "Pin " + pinName + " is Low/OFF.");
                }

                Payload payload = new Payload();
                payload.setPeripheralIO(PeripheralIO.GPIO);
                payload.setAction(Action.EVENT);
                payload.setName(pinName);
                payload.setProperty(PinProperty.PIN_STATE);
                payload.setValue(gpioValue ? PinValue.HIGH : PinValue.LOW);
                payload.setDirection(PinDirection.IN);

                String messageStr = Message.constructMessage(payload);

                MqttMessage message = new MqttMessage(messageStr.getBytes());
                message.setQos(AndroidThingsActivity.QOS);
                message.setRetained(false);

                // Publish the message
                Log.d(TAG,"Publishing to topic \"" + AndroidThingsActivity.getPublishTopic()
                        + "\" qos " + AndroidThingsActivity.QOS);
                try {
                    // publish message to broker
                    mMqttClient.publish(AndroidThingsActivity.getPublishTopic(),
                            message);
                    Thread.sleep(AndroidThingsActivity.SLEEP_TIME);
                } catch (Exception e) {
                    Log.e(TAG, e.getLocalizedMessage());
                }

            } catch (IOException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }

            // Continue listening for more interrupts
            return true;
        }

        @Override
        public void onGpioError(Gpio gpio, int error) {
            super.onGpioError(gpio, error);
            Log.w(TAG, gpio + ": Error event " + error);
        }
    };

    /**
     * The Constructor.
     * @param androidThingsActivity
     * @param pMqttClient
     * @param pPeripheralManagerService
     */
    public GpioHandler(AndroidThingsActivity pAndroidThingsActivity, MqttClient pMqttClient,
                       PeripheralManagerService
            pPeripheralManagerService) {
        mGpioInputPinsMap = HashBiMap.create();
        mGpioOutputPinsMap = HashBiMap.create();
        mMqttClient = pMqttClient;
        mPeripheralManagerService = pPeripheralManagerService;
        sParent = pAndroidThingsActivity;
        Log.d(TAG, "Available GPIO: " + mPeripheralManagerService.getGpioList());
    }

    /**
     * Handle a message that is requesting to register a pin as an input.
     * @param pPayload
     * @throws IOException
     */
    private void handleRegisterPin(Payload pPayload) throws IOException {
        String pinName = pPayload.getName();
        PinDirection pinDirection = pPayload.getDirection();
        PinValue pinValue = pPayload.getValue();
        Log.d(TAG, "Received a Pin Registration triggered from App Inventor.");
        if (pinDirection == PinDirection.IN) {
            Log.d(TAG, "Registering pin " + pinName + " as an input.");
            Gpio inputPin = openInputPin(pinName);
            mGpioInputPinsMap.put(pinName, inputPin);
        } else {
            Log.d(TAG, "The pin " + pinName + " is an output pin. Nothing to do here.");
        }
    }

    /**
     * Handle a message that needs to trigger an event on the GPIO pin.
     * @param pPayload
     * @throws IOException
     */
    private void handlePinEvent(Payload pPayload) throws IOException {
        Log.d(TAG, "Received a Pin Event triggered from App Inventor.");
        String pinName = pPayload.getName();
        PinDirection pinDirection = pPayload.getDirection();
        PinValue pinValue = pPayload.getValue();
        // Create GPIO connection for the pin.
        if (pinDirection == PinDirection.OUT) {
            Gpio gpioPin = openOutputPin(pinName);

            switch (pinValue) {
                case HIGH:
                    Log.d(TAG, "Turning " + pinName + " ON.");
                    gpioPin.setValue(true);
                    break;
                case LOW:
                    Log.d(TAG, "Turning " + pinName + " OFF.");
                    gpioPin.setValue(false);
                    break;
                default:
                    Log.d(TAG, "Message not supported!");
                    break;
            }
        } else {
            Log.d(TAG, "The designated pin " + pinName + " is not an output. Nothing to do here!");
        }
   }

    /**
     * Close any open input and output GPIO pins.
     */
    public void closeOpenGpioPins() {
        close(mGpioInputPinsMap);
        close(mGpioOutputPinsMap);
    }

    /**
     * Close the given GPIO pins.
     * @param pGpioPins
     */
    private void close(BiMap<String, Gpio> pGpioPins) {
        Set<Gpio> openGpioPins = pGpioPins.values();

        for (Gpio pin : openGpioPins) {
            if (pin != null) {
                try {
                    pin.close();
                    mGpioOutputPinsMap.inverse().remove(pin);
                    pin = null;
                } catch (IOException e) {
                    Log.w(TAG, "Unable to close GPIO " + pin.toString(), e);
                }
            }
        }
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
                handleRegisterPin(pPayload);
                break;
            case EVENT:
                handlePinEvent(pPayload);
                break;
            default:
                Log.d(TAG, "Message not supported!");
                break;
        }
    }

    /**
     * Open the Input GPIO pin by the given name.
     * @param pPinName
     * @return the GPIO pin that was just opened.
     */
    private Gpio openInputPin(String pPinName) {
        Gpio gpioPin;
        if (mGpioInputPinsMap.containsKey(pPinName)) {
            gpioPin = mGpioInputPinsMap.get(pPinName);
            try {
                Log.d(TAG, "Closing existing pin " + pPinName + ".");
                gpioPin.close();
                mGpioInputPinsMap.remove(pPinName);
            } catch (IOException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }
        gpioPin = createNewInputPin(pPinName);
        Log.d(TAG, "Created a new GPIO pin object for " + pPinName);
        return gpioPin;
    }

    /**
     * Open the Output GPIO pin by the given name.
     * @param pPinName
     * @return the GPIO pin that was just opened.
     */
    private Gpio openOutputPin(String pPinName) {
        Gpio gpioPin;
        if (mGpioOutputPinsMap.containsKey(pPinName)) {
            gpioPin = mGpioOutputPinsMap.get(pPinName);
            try {
                Log.d(TAG, "Closing existing pin " + pPinName + ".");
                gpioPin.close();
                mGpioOutputPinsMap.remove(pPinName);
            } catch (IOException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }
        gpioPin = createNewOutputPin(pPinName);
        return gpioPin;
    }

    /**
     * Create a new GPIO pin by the given name for input.
     * @param pPinName
     * @return
     */
    private Gpio createNewInputPin(String pPinName) {
        try {
            Log.d(TAG, "Creating new pin " + pPinName);
            final Gpio gpioPin = mPeripheralManagerService.openGpio(pPinName);
            gpioPin.setDirection(Gpio.DIRECTION_IN);
            // High voltage is considered active
            gpioPin.setActiveType(Gpio.ACTIVE_HIGH);
            // Register for all state changes
            gpioPin.setEdgeTriggerType(Gpio.EDGE_BOTH);

            sParent.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        gpioPin.registerGpioCallback(mGpioCallback);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            return gpioPin;
        } catch (IOException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        return null;
    }

    /**
     * Create a new GPIO pin by the given name for output.
     * @param pPinName
     * @return
     */
    private Gpio createNewOutputPin(String pPinName) {
        Gpio gpioPin = null;
        try {
            gpioPin = mPeripheralManagerService.openGpio(pPinName);
            gpioPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mGpioOutputPinsMap.put(pPinName, gpioPin);
        } catch (IOException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        return gpioPin;
    }

    public void registerGpioCallback() throws IOException {
        for (Gpio inputGpio : mGpioInputPinsMap.values()){
            inputGpio.registerGpioCallback(mGpioCallback);
        }
    }

    public void unregisterGpioCallback() {
        for (Gpio inputGpio : mGpioInputPinsMap.values()){
            inputGpio.unregisterGpioCallback(mGpioCallback);
        }
    }
}
