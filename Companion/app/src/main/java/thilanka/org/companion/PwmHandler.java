package thilanka.org.companion;

import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.thilanka.messaging.domain.Payload;

import java.io.IOException;

/**
 * The logic that handles PWM related activities.
 *
 * @author Thilanka Munasinghe (thilankawillbe@gmail.com)
 */
public class PwmHandler {

    /* The Log Tag*/
    private static final String TAG = PwmHandler.class.getSimpleName();

    /* The MQTT Client*/
    private final MqttClient mMqttClient;

    /* The Android Things Peripheral Manager Service*/
    private final PeripheralManagerService mPeripheralManagerService;

    /* The input pins */
    private BiMap<String, Gpio> mGpioInputPinsMap;

    /* The output pins */
    private BiMap<String, Gpio> mGpioOutputPinsMap;

    /**
     * The Constructor.
     * @param pMqttClient
     * @param pPeripheralManagerService
     */
    public PwmHandler(MqttClient pMqttClient, PeripheralManagerService pPeripheralManagerService) {
        mGpioInputPinsMap = HashBiMap.create();
        mGpioOutputPinsMap = HashBiMap.create();
        mMqttClient = pMqttClient;
        mPeripheralManagerService = pPeripheralManagerService;

        Log.d(TAG, "Available PWM: " + mPeripheralManagerService.getPwmList());
    }

    /**
     * Handle the messages intended for PWM.
     * @param pPayload
     * @throws IOException
     */
    public void handleMessage(Payload pPayload) throws IOException {
    }
}
