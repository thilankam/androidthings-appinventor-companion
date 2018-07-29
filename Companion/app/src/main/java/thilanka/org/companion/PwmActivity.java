package thilanka.org.companion;

import android.app.Activity;
import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.Pwm;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;

/**
 * Sample usage of the PWM API that changes the PWM pulse width at a fixed interval defined in
 * {@link #INTERVAL_BETWEEN_STEPS_MS}.
 *
 */
public class PwmActivity extends Activity {
    private static final String TAG = PwmActivity.class.getSimpleName();

    // Parameters of the servo PWM
    private static final double MIN_ACTIVE_PULSE_DURATION_MS = 1;
    private static final double MAX_ACTIVE_PULSE_DURATION_MS = 2;
    private static final double PULSE_PERIOD_MS = 20;  // Frequency of 50Hz (1000/20)

    // Parameters for the servo movement over time
    private static final double PULSE_CHANGE_PER_STEP_MS = 0.2;
    private static final int INTERVAL_BETWEEN_STEPS_MS = 1000;

    private Handler mHandler = new Handler();
    private Pwm mPwm;
    private boolean mIsPulseIncreasing = true;
    private double mActivePulseDuration;

    private PeripheralManager mPeripheralManager = PeripheralManager.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Starting PWMActivity");

        try {
            String pinName = "PWM1";
            mActivePulseDuration = MIN_ACTIVE_PULSE_DURATION_MS;

            mPwm = mPeripheralManager.openPwm(pinName);

            // Always set frequency and initial duty cycle before enabling PWM
            mPwm.setPwmFrequencyHz(1000 / PULSE_PERIOD_MS);
            //mPwm.setPwmDutyCycle(mActivePulseDuration);
            mPwm.setEnabled(true);

            // Post a Runnable that continuously change PWM pulse width, effectively changing the
            // servo position
            Log.d(TAG, "Start changing PWM pulse");
    //        mHandler.post(mChangePWMRunnable);

            for (int i = 0; i< 100; i++){

                // Change the duration of the active PWM pulse, but keep it between the minimum and
                // maximum limits.
                // The direction of the change depends on the mIsPulseIncreasing variable, so the pulse
                // will bounce from MIN to MAX.
                if (mIsPulseIncreasing) {
                    mActivePulseDuration += PULSE_CHANGE_PER_STEP_MS;
                } else {
                    mActivePulseDuration -= PULSE_CHANGE_PER_STEP_MS;
                }

                // Bounce mActivePulseDuration back from the limits
                if (mActivePulseDuration > MAX_ACTIVE_PULSE_DURATION_MS) {
                    mActivePulseDuration = MAX_ACTIVE_PULSE_DURATION_MS;
                    mIsPulseIncreasing = !mIsPulseIncreasing;
                } else if (mActivePulseDuration < MIN_ACTIVE_PULSE_DURATION_MS) {
                    mActivePulseDuration = MIN_ACTIVE_PULSE_DURATION_MS;
                    mIsPulseIncreasing = !mIsPulseIncreasing;
                }
                double n = 100 * mActivePulseDuration / PULSE_PERIOD_MS;
                Log.d(TAG, "Start changing PWM pulse i = " + i + ", n = " + n);
                Thread.sleep(INTERVAL_BETWEEN_STEPS_MS);
                mPwm.setPwmDutyCycle(n);

            }

        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove pending Runnable from the handler.
        mHandler.removeCallbacks(mChangePWMRunnable);
        // Close the PWM port.
        Log.i(TAG, "Closing port");
        try {
            mPwm.close();
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        } finally {
            mPwm = null;
        }
    }

    private Runnable mChangePWMRunnable = new Runnable() {
        @Override
        public void run() {
            // Exit Runnable if the port is already closed
            if (mPwm == null) {
                Log.w(TAG, "Stopping runnable since mPwm is null");
                return;
            }

            // Change the duration of the active PWM pulse, but keep it between the minimum and
            // maximum limits.
            // The direction of the change depends on the mIsPulseIncreasing variable, so the pulse
            // will bounce from MIN to MAX.
            if (mIsPulseIncreasing) {
                mActivePulseDuration += PULSE_CHANGE_PER_STEP_MS;
            } else {
                mActivePulseDuration -= PULSE_CHANGE_PER_STEP_MS;
            }

            // Bounce mActivePulseDuration back from the limits
            if (mActivePulseDuration > MAX_ACTIVE_PULSE_DURATION_MS) {
                mActivePulseDuration = MAX_ACTIVE_PULSE_DURATION_MS;
                mIsPulseIncreasing = !mIsPulseIncreasing;
            } else if (mActivePulseDuration < MIN_ACTIVE_PULSE_DURATION_MS) {
                mActivePulseDuration = MIN_ACTIVE_PULSE_DURATION_MS;
                mIsPulseIncreasing = !mIsPulseIncreasing;
            }

            Log.d(TAG, "Changing PWM active pulse duration to " + mActivePulseDuration + " ms");

            try {

                // Duty cycle is the percentage of active (on) pulse over the total duration of the
                // PWM pulse
                double n = 100 * mActivePulseDuration / PULSE_PERIOD_MS;
                mPwm.setPwmDutyCycle(n);

                // Reschedule the same runnable in {@link #INTERVAL_BETWEEN_STEPS_MS} milliseconds
                mHandler.postDelayed(this, INTERVAL_BETWEEN_STEPS_MS);
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
        }
    };

}