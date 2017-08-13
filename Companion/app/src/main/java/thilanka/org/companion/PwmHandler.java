package thilanka.org.companion;

import android.util.Log;

import com.google.android.things.pio.PeripheralManagerService;
import com.google.android.things.pio.Pwm;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.thilanka.device.pin.PinProperty;
import org.thilanka.device.pin.PinValue;
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

    /* The static reference to the parent activity to run things in the foreground */
    private final AndroidThingsActivity sParent;

    /* The PWM output pins */
    private BiMap<String, Pwm> mPwmPinsMap;

    /**
     * The Constructor.
     * @param pAndroidThingsActivity
     * @param pMqttClient
     * @param pPeripheralManagerService
     */
    public PwmHandler(AndroidThingsActivity pAndroidThingsActivity, MqttClient pMqttClient,
                      PeripheralManagerService pPeripheralManagerService) {
        mPwmPinsMap = HashBiMap.create();
        mMqttClient = pMqttClient;
        mPeripheralManagerService = pPeripheralManagerService;
        sParent = pAndroidThingsActivity;

        Log.d(TAG, "Available PWM: " + mPeripheralManagerService.getPwmList());
    }

    /**
     * Handle the messages intended for PWM.
     * @param pPayload
     * @throws IOException
     */
    public void handleMessage(Payload pPayload) throws IOException {
        Log.d(TAG, "Received a PWM Event triggered from App Inventor with payload " + pPayload);
        String pwmName = pPayload.getName();
        Pwm pwm = openPwm(pwmName);
        PinProperty property = pPayload.getProperty();
        switch (property){
            case PIN_STATE:
                PinValue value = pPayload.getValue();
                boolean enabled = (value == PinValue.HIGH);
                pwm.setEnabled(enabled);
                Log.d(TAG, "Set PWM " + pwmName + " enabled = " + enabled );
                break;
            case FREQUENCY:
                double frequencyValue = pPayload.getDoubleValue();
                pwm.setPwmFrequencyHz(frequencyValue);
                Log.d(TAG, "Set PWM " + pwmName + " frequency = " + frequencyValue );
                break;
            case DUTY_CYCLE:
                double dutyCycleValue = pPayload.getDoubleValue();
                pwm.setPwmDutyCycle(dutyCycleValue);
                Log.d(TAG, "Set PWM " + pwmName + " duty cycle = " + dutyCycleValue );
                break;
            default:
                Log.d(TAG, "Unknown PWM case : " + property);
        }
    }

    /**
     * Open the PWM by the given name.
     * @param pPwmName
     * @return the PWM that was just opened.
     * @throws IOException
     */
    private Pwm openPwm(String pPwmName) throws IOException {
        Pwm pwm;
        if (mPwmPinsMap.containsKey(pPwmName)) {
            pwm = mPwmPinsMap.get(pPwmName);
        } else {
            pwm = mPeripheralManagerService.openPwm(pPwmName);
            mPwmPinsMap.put(pPwmName, pwm);
        }
        return pwm;
    }
}
