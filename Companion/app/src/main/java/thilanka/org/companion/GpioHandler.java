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
import org.thilanka.device.pin.PinValue;
import org.thilanka.messaging.domain.HeaderPin;

import java.io.IOException;
import java.util.Set;

/**
 * Created by thilanka1 on 8/12/17.
 */

public class GpioHandler {

    private static final String TAG = "GpioHandler";
    private final MqttClient mMqttClient;
    private final PeripheralManagerService mPeripheralManagerService;

    private BiMap<String, Gpio> mGpioInputPinsMap;
    private BiMap<String, Gpio> mGpioOutputPinsMap;

    private GpioCallback mGpioCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            // Read the active low pin state
            try {
                String pinName = mGpioInputPinsMap.inverse().get(gpio);
                if (gpio.getValue()) {
                    // Pin is LOW
                    Log.d(TAG, "Pin "+pinName+" is Low/OFF.");
                } else {
                    // Pin is HIGH
                    Log.d(TAG, "Pin "+pinName+" is High/ON.");
                }

                String pubMsg = "EVENT" + ":" + pinName + ":" + "FALSE" + ":" + (gpio.getValue() ? "OFF" : "ON") ;
                MqttMessage message = new MqttMessage(pubMsg.getBytes());
                message.setQos(AndroidThingsActivity.QOS);
                message.setRetained(false);

                // Publish the message
                System.out.println("Publishing to topic \"" + AndroidThingsActivity.getBoardIdentfier() + "\" qos " + AndroidThingsActivity.QOS);
                try {
                    // publish message to broker
                    mMqttClient.publish(AndroidThingsActivity.getBoardIdentfier(), message);
                    Thread.sleep(AndroidThingsActivity.SLEEP_TIME);
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

    public GpioHandler(MqttClient pMqttClient, PeripheralManagerService pPeripheralManagerService) {
        mGpioInputPinsMap = HashBiMap.create();
        mGpioOutputPinsMap = HashBiMap.create();
        mMqttClient = pMqttClient;
        mPeripheralManagerService = pPeripheralManagerService;

        Log.d(TAG, "Available GPIO: " + mPeripheralManagerService.getGpioList());
    }

    public void handlerRegisterPin(HeaderPin pin) throws IOException{
        String pinName = pin.getName();
        PinDirection pinDirection = pin.getDirection();
        PinValue pinValue = pin.getValue();
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
            mGpioInputPinsMap.put(pinName, inputPin);
        } else {
            Log.d(TAG, "The pin " + pinName + " is an output pin. Nothing to do here.");
        }

    }

    private Gpio openPin(String pinName) {
        Gpio gpioPin;
        if (mGpioOutputPinsMap.containsKey(pinName)){
            gpioPin = mGpioOutputPinsMap.get(pinName);
            try {
                Log.d(TAG, "Closing existing pin " + pinName + ".");
                gpioPin.close();
                mGpioOutputPinsMap.remove(pinName);
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
            mGpioOutputPinsMap.put(pinName, gpioPin);
        } catch (IOException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        return gpioPin;
    }

    public void handlePinEvent(HeaderPin pin) throws IOException {
        Log.d(TAG, "Received a Pin Event triggered from App Inventor.");
        String pinName = pin.getName();
        PinDirection pinDirection = pin.getDirection();
        PinValue pinValue = pin.getValue();
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

    }

    public void closeOpenGpioPins() {
        Set<Gpio> openGpioPins = mGpioOutputPinsMap.values();
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
}
