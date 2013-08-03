/*
  ArduinoWrapper.java - A GSN wrapper for Arduino

  Copyright (c) 2013 Antonio Macrì <ing.antonio.macri@gmail.com>

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU General Public License
  as published by the Free Software Foundation; either version 2
  of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
  02110-1301, USA.
*/

package gsn.wrappers.arduino;

import java.io.IOException;
import java.io.Serializable;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import gsn.beans.AddressBean;
import gsn.beans.DataField;
import gsn.beans.StreamElement;
import gsn.wrappers.AbstractWrapper;
import gsn.wrappers.arduino.Arduino.ArduinoConnectionException;

/**
 * ArduinoWrapper allows to read data from various sensors on an Arduino board.
 *
 * This wrapper produces a sequence of StreamElements containing a single integer value, using a
 * configurable rate. Each value is retrieved from the attached Arduino board reading from a given
 * pin with the specified mode (analog or digital). All parameters (pin, mode, rate) are obtained
 * from the associated virtual sensor.
 */
public class ArduinoWrapper extends AbstractWrapper
{
    private static Object lock = new Object();
    private static Arduino arduino;
    private static int totalThreads;
    private static int activeThreads;

    private enum Mode {
        ANALOG, DIGITAL
    }

    private final transient Logger logger = Logger.getLogger(ArduinoWrapper.class);

    private DataField[] collection;

    private int rate = 1000;
    private Mode mode;
    private int pin;

    /**
     * Initializes this instance of the wrapper reading parameters from the associated virtual
     * sensor, creating an Arduino instance (if not already done), and configuring the specified
     * pin.
     */
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
            // Notice that initialization will be retried every 3 seconds
            return false;
        }

        // Instantiate Arduino
        synchronized (lock) {
            if (activeThreads == 0) {
                String[] list = Arduino.list();
                if (list.length <= 0) {
                    logger.error("No Arduino connected.");
                    return false;
                }
                logger.debug("Found " + list.length + " Arduino(s) connected.");
                try {
                    arduino = new Arduino(list[0], 57600);
                }
                catch (ArduinoConnectionException e) {
                    logger.error("An error occurred while instantiating Arduino.", e);
                    return false;
                }
                logger.debug(String.format("Arduino (Firmata v%s) instantiated on port %s.",
                        arduino.getProcotolVersionString(1000), arduino.getSerialName()));
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

        String value = params.getPredicateValue("rate");
        if (value != null) {
            rate = Integer.parseInt(value);
        }

        value = params.getPredicateValueWithDefault("mode", Mode.ANALOG.name());
        mode = getEnumFromString(Mode.class, value, "mode");

        return true;
    }

    /**
     * Periodically reads a value from the Arduino and posts it to GSN.
     */
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

            // Read a value from the specified pin
            int value;
            if (arduino.isReporting()) {
                if (mode == Mode.DIGITAL) {
                    value = arduino.digitalRead(pin);
                }
                else {
                    value = arduino.analogRead(pin);
                }
            }
            else {
                logger.error("Arduino is not connected.");
                return;
            }

            // Post the data to GSN
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
