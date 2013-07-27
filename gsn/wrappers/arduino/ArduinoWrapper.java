package gsn.wrappers.arduino;

import java.io.IOException;
import java.io.Serializable;
import java.util.TooManyListenersException;

import javax.sound.sampled.LineUnavailableException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.shigeodayo.javarduino.Arduino;

import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;
import gsn.beans.AddressBean;
import gsn.beans.DataField;
import gsn.beans.StreamElement;
import gsn.wrappers.AbstractWrapper;

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
        TIMER, DATA
    }

    private final transient Logger logger = Logger.getLogger(ArduinoWrapper.class);

    private DataField[] collection;

    private int rate = 1000;
    private Mode mode;
    private Trigger trigger;
    private int pin;

    @Override
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
                try {
                    arduino = new Arduino(list[0], 57600);
                }
                catch (NoSuchPortException | PortInUseException | IOException | LineUnavailableException
                        | UnsupportedCommOperationException | TooManyListenersException e) {
                    logger.error("An error occurred while instantiating Arduino.", e);
                    return false;
                }
                logger.debug("Arduino instantiated on port " + list[0] + ".");
            }

            totalThreads++;
            activeThreads++;
            setName("ArduinoWrapper" + totalThreads);
            logger.debug(getName() + ": initialize()");
        }

        // Configure Arduino for this pin/sensor
        try {
            arduino.pinMode(pin, Arduino.INPUT);
        }
        catch (IOException e) {
            logger.error("Cannot write data through the serial port.", e);
            this.dispose();
            return false;
        }

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

        String value = params.getPredicateValueWithDefault("trigger", Trigger.TIMER.name());
        trigger = getEnumFromString(Trigger.class, value, "trigger");

        value = params.getPredicateValue("rate");
        if (value != null) {
            if (trigger == Trigger.DATA) {
                logger.warn("Parameter 'rate' is ignored if 'trigger' is 'data'.");
            }
            else {
                rate = Integer.parseInt(value);
            }
        }

        value = params.getPredicateValueWithDefault("mode",
                (trigger == Trigger.DATA ? Mode.DIGITAL : Mode.ANALOG).name());
        mode = getEnumFromString(Mode.class, value, "mode");

        if (trigger == Trigger.DATA && mode != Mode.DIGITAL) {
            logger.error("Invalid parameter: 'trigger=data' is supported only with 'mode=digital'.");
            return false;
        }

        return true;
    }

    @Override
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
            if (mode == Mode.DIGITAL) {
                value = arduino.digitalRead(pin);
            }
            else {
                value = arduino.analogRead(pin);
            }

            // post the data to GSN
            StreamElement se = new StreamElement(getOutputFormat(), new Serializable[] {
                value
            }, System.currentTimeMillis());
            postStreamElement(se);
            // Note that checking isActive() does not prevent from reading data from a sensor
            // after the associated wrapper instance is marked as inactive (postStreamElement
            // might return false).
            // We do not use any lock, since analogRead/digitalRead just read values from
            // memory
        }
    }

    @Override
    public DataField[] getOutputFormat()
    {
        return collection;
    }

    @Override
    public String getWrapperName()
    {
        return "Arduino Wrapper";
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
                // Do not set arduino=null: it may happen that the run method will try to read
                // data after the dispose method is called. We avoid using any lock while
                // reading: it will be just read an old value from memory
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
