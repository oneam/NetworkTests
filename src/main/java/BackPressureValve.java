import rx.Producer;
import rx.functions.Action0;

/**
 * Helper for implementing custom back pressure.
 */
public class BackPressureValve implements Producer {
    private long remaining = 0;
    private boolean open = false;
    private final Action0 onNextAction;

    /**
     * @param onNextAction Called when this valve is ready for the next value.
     */
    public BackPressureValve(Action0 onNextAction) {
        if (onNextAction == null) {
            throw new NullPointerException("onNextAction must not be null");
        }

        this.onNextAction = onNextAction;
    }

    @Override
    public void request(long n) {
        if (n == Long.MAX_VALUE) {
            disableBackpressure();
        } else {
            if (backpressureDisabled()) {
                enableBackpressure();
            }

            remaining += n;
        }

        if (!open) {
            open = true;
            onNextAction.call();
        }
    }

    /**
     * Call to determine if another item should be produced.
     * When the valve is open, the onNextAction will be called.
     * When the valve is closed, nothing will happen until request(n) is called.
     */
    public void attemptOnNext() {
        if (backpressureDisabled()) {
            onNextAction.call();
            return;
        }

        if (remaining > 0) {
            --remaining;
            onNextAction.call();
        } else {
            open = false;
        }
    }

    private void enableBackpressure() {
        remaining = 0;
    }

    private void disableBackpressure() {
        remaining = Long.MAX_VALUE;
    }

    private boolean backpressureDisabled() {
        return remaining == Long.MAX_VALUE;
    }
}
