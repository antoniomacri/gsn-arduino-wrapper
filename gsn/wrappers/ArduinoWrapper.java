package gsn.wrappers;

import java.io.Serializable;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.shigeodayo.javarduino.Arduino;

import gsn.beans.AddressBean;
import gsn.beans.DataField;
import gsn.beans.StreamElement;

/**
 * This wrapper allows to read data from various sensors on an Arduino board.
 */
public class ArduinoWrapper extends AbstractWrapper
{
    private static Object lock = new Object();
    private static Arduino arduino; // TODO: volatile?
    private static int totalThreads;
    private static int activeThreads;

    private enum Mode {
        ANALOG, DIGITAL
    }

    private enum Trigger {
        TIME, DATA
    }

    private final transient Logger logger = Logger.getLogger(ArduinoWrapper.class);

    private DataField[] collection;

    private int rate = 1000;
    private Mode mode;
    private Trigger trigger;
    private int pin;

    public boolean initialize()
    {
        logger.setLevel(Level.ALL);

        AddressBean params = getActiveAddressBean();

        // We must catch exceptions when reading and parsing predicates,
        // otherwise it seems that GSN tries to reinitialize the wrapper
        // in a loop, filling the GSN log very quickly
        try {
            if (readParams(params) == false) {
                return false;
            }
        }
        catch (Throwable e) {
            logger.error("Cannot read parameters from VSD file.", e);
            // Note that initialization will be retried every 3 seconds
            return false;
        }

        // Instantiate Arduino
        synchronized (lock) {
            if (arduino == null) {
                String[] list = Arduino.list();
                if (list.length <= 0) {
                    logger.error("No Arduino connected.");
                    return false;
                }
                logger.debug("Found " + list.length + " Arduino(s) connected.");
                arduino = new Arduino(list[0], 57600);
                logger.debug("Arduino instantiated on port " + list[0] + ".");
            }

            totalThreads++;
            activeThreads++;
            setName("ArduinoWrapper" + totalThreads);
            logger.debug(getName() + ": initialize()");
        }

        // Configure Arduino for this pin/sensor
        arduino.pinMode(pin, Arduino.INPUT);

        collection = new DataField[] {
            // We always provide an integer value, as read from the sensors.
            // Post-processing can be done by implementing a custom processing class
            // or using gsn.processor.ScriptletProcessor in the VSD file.
            // (Don't set any description, it is almost useless.)
            new DataField("value", "int", "")
        };

        return true;
    }

    private boolean readParams(AddressBean params)
    {
        pin = params.getPredicateValueAsIntWithException("pin");
        logger.info("Pin: " + pin + ".");

        String value = params.getPredicateValueWithDefault("trigger", "time");
        trigger = getEnumFromString(Trigger.class, value, "trigger");
        logger.info("Trigger: " + trigger + ".");

        value = params.getPredicateValue("rate");
        if (value != null) {
            if (trigger == Trigger.DATA) {
                logger.warn("Parameter 'rate' is ignored if 'trigger' is 'data'.");
            }
            else {
                rate = Integer.parseInt(value);
                logger.info("Sampling rate: " + value + " msec.");
            }
        }

        value = params.getPredicateValueWithDefault("mode", trigger == Trigger.DATA ? "digital" : "analog");
        mode = getEnumFromString(Mode.class, value, "mode");
        logger.info("Mode: " + mode + ".");

        if (trigger == Trigger.DATA && mode != Mode.DIGITAL) {
            logger.error("Invalid parameter: 'trigger=data' is supported only with 'mode=digital'.");
            return false;
        }

        return true;
    }

    public void run()
    {
        while (isActive()) {
            try {
                Thread.sleep(rate);
            }
            catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }

            // read value from the specified pin
            int value;
            synchronized (lock) {
                if (arduino == null) {
                    // isActive() is set to return false after dispose(), but checking
                    // it doesn't guarantee this code being called before disposition.
                    // We must use a lock and check if arduino is set to null.
                    logger.debug("Tried to read data after Arduino disposition.");
                    // Note that this does not prevent the wrapper from reading data
                    // from a sensor after the associated wrapper instance is marked as
                    // inactive (postStreamElement below might return false).
                    return;
                }

                if (mode == Mode.DIGITAL) {
                    value = arduino.digitalRead(pin);
                }
                else {
                    value = arduino.analogRead(pin);
                }
            }

            // post the data to GSN
            StreamElement se = new StreamElement(getOutputFormat(), new Serializable[] {
                value
            }, System.currentTimeMillis());
            if (postStreamElement(se) == false) {
                logger.debug("Data discarded (vsensor unloaded?).");
            }
        }
    }

    public DataField[] getOutputFormat()
    {
        return collection;
    }

    public String getWrapperName()
    {
        return "Arduino Wrapper";
    }

    public void finalize()
    {
        logger.debug("finalize() ignored.");
    }

    @Override
    public void dispose()
    {
        // Release the serial
        synchronized (lock) {
            logger.debug(getName() + ": dispose()");
            activeThreads--;
            if (activeThreads == 0) {
                String iname = arduino.getSerialName();
                arduino.dispose();
                arduino = null;
                logger.debug("Arduino disposed on port " + iname + ".");
            }
            else {
                logger.debug("Still " + activeThreads + " active thread(s).");
            }
        }
    }

    @SuppressWarnings({
            "rawtypes", "unchecked"
    })
    public static <T extends Enum<T>> T getEnumFromString(Class<T> enumeration, String value, String param)
    {
        for (Enum enumValue : enumeration.getEnumConstants()) {
            if (enumValue.name().equalsIgnoreCase(value)) {
                return (T) enumValue;
            }
        }
        throw new RuntimeException("Specified value '" + value + "' is not valid for parameter '" + param + "'.");
    }
}
