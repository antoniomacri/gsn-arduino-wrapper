/*
  Arduino.java - Arduino/Firmata library, not for Processing

  The original code of this library is distributed to be used in
  combination with Processing (http://processing.org). You can
  obtain it from
    https://github.com/firmata/processing
  (/src/Arduino.java).

  Here, it has been detached from Processing and adapted to a
  more general usage.

  Copyright (C) 2006-08 David A. Mellis
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

import gnu.io.*;

import java.io.IOException;

/**
 * Together with the Firmata 2 firmware (an Arduino sketch uploaded to the Arduino board), this
 * class allows you to control the Arduino board from Processing: reading from and writing to the
 * digital pins and reading the analog inputs.
 */
public class Arduino
{
    /** In a call to pinMode(), sets a pin to input mode. */
    public static final int INPUT = 0;
    /** In a call to pinMode(), sets a pin to output mode. */
    public static final int OUTPUT = 1;
    /** In a call to pinMode(), sets a pin to analog mode. */
    public static final int ANALOG = 2;
    /** In a call to pinMode(), sets a pin to PWM mode. */
    public static final int PWM = 3;
    /** In a call to pinMode(), sets a pin to servo mode. */
    public static final int SERVO = 4;
    /** In a call to pinMode(), sets a pin to shiftIn/shiftOut mode. */
    public static final int SHIFT = 5;
    /** In a call to pinMode(), sets a pin to I2C mode. */
    public static final int I2C = 6;

    /** In a call to digitalWrite(), writes a low value (0 volts) to a pin. */
    public static final int LOW = 0;
    /** In a call to digitalWrite(), writes a high value (+5 volts) to a pin. */
    public static final int HIGH = 1;

    /**
     * Send/receive data for a digital port. The MIDI channel contains the port. Two byte-arguments
     * are provided, containing respectively the LSB (bits 0-6) and the MSB (bits 7-13) of the
     * value.
     */
    private static final int DIGITAL_MESSAGE = 0x90;
    /**
     * Receive data for an analog pin or sent data to a PWM pin. The MIDI channel contains the pin
     * number. Two byte-arguments are provided, containing respectively the LSB (bits 0-6) and the
     * MSB (bits 7-13) of the value.
     */
    private static final int ANALOG_MESSAGE = 0xE0;
    /**
     * Enable/disable analog input from a pin. The MIDI channel contains the pin number. The
     * additional one-byte argument indicates whether to disable (0) or enable (1).
     */
    private static final int REPORT_ANALOG = 0xC0;
    /**
     * Enable/disable digital input from a port. The MIDI channel contains the port. The additional
     * one-byte argument indicates whether to disable (0) or enable (1).
     */
    private static final int REPORT_DIGITAL = 0xD0;
    /**
     * Used to set a pin to a specific mode. The MIDI channel is not present. Two one-byte arguments
     * are provided, containing respectively the pin number and the pin mode.
     */
    private static final int SET_PIN_MODE = 0xF4;
    /**
     * Reports the firmware version. Two one-byte arguments are provided, containing respectively
     * the major and the minor version numbers.
     */
    private static final int REPORT_VERSION = 0xF9;
    /**
     * Issues a soft reset. No MIDI channel and no arguments.
     */
    @SuppressWarnings("unused")
    private static final int SYSTEM_RESET = 0xFF;
    /**
     * Starts a MIDI SysEx message. No MIDI channel and no arguments.
     */
    @SuppressWarnings("unused")
    private static final int START_SYSEX = 0xF0;
    /**
     * Ends a MIDI SysEx message. No MIDI channel and no arguments.
     */
    private static final int END_SYSEX = 0xF7;

    private Serial serial = null;
    private SerialProxy serialProxy = null;

    private final int MAX_DATA_BYTES = 32;

    private int waitForData = 0;
    private int executeMultiByteCommand = 0;
    private int multiByteChannel = 0;
    private int[] storedInputData = new int[MAX_DATA_BYTES];
    private boolean parsingSysex = false;
    private int sysexBytesRead = 0;

    private int[] digitalOutputData = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };
    private int[] digitalInputData = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };
    private int[] analogInputData = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };

    // Firmata protocol version implemented in Arduino's sketch
    private int protocolMajorVersion = 0;
    private int protocolMinorVersion = 0;

    // Use an inner class just to avoid having serialEvent() as a public method.
    private class SerialProxy implements SerialPortEventListener
    {
        @Override
        public void serialEvent(SerialPortEvent arg)
        {
            // Notify the Arduino class that there's serial data for it to process.
            while (available() > 0)
                processInput();
        }
    }

    public void dispose()
    {
        this.serial.dispose();
    }

    /**
     * Get a list of the available Arduino boards; currently all serial devices (i.e. the same as
     * Serial.list()). In theory, this should figure out what's an Arduino board and what's not.
     */
    public static String[] list()
    {
        return Serial.list();
    }

    /**
     * Create a proxy to an Arduino board running the Firmata 2 firmware at the default baud rate of
     * 57600.
     *
     * @param parent
     *            the Processing sketch creating this Arduino board (i.e. "this").
     * @param iname
     *            the name of the serial device associated with the Arduino board (e.g. one the
     *            elements of the array returned by Arduino.list())
     * @throws UnsupportedCommOperationException
     * @throws IOException
     * @throws PortInUseException
     * @throws NoSuchPortException
     */
    public Arduino(String iname) throws NoSuchPortException, PortInUseException, IOException,
            UnsupportedCommOperationException
    {
        this(iname, 57600);
    }

    /**
     * Create a proxy to an Arduino board running the Firmata 2 firmware.
     *
     * @param parent
     *            the Processing sketch creating this Arduino board (i.e. "this").
     * @param iname
     *            the name of the serial device associated with the Arduino board (e.g. one the
     *            elements of the array returned by Arduino.list())
     * @param irate
     *            the baud rate to use to communicate with the Arduino board (the firmata library
     *            defaults to 57600, and the examples use this rate, but other firmwares may
     *            override it)
     * @throws UnsupportedCommOperationException
     * @throws IOException
     * @throws PortInUseException
     * @throws NoSuchPortException
     */
    public Arduino(String iname, int irate) throws NoSuchPortException, PortInUseException, IOException,
            UnsupportedCommOperationException
    {
        this.serialProxy = new SerialProxy();
        this.serial = new Serial(serialProxy, iname, irate);

        try {
            Thread.sleep(3000);
        }
        catch (InterruptedException e) {
        }

        for (int i = 0; i < 6; i++) {
            serial.write(REPORT_ANALOG | i);
            serial.write(1);
        }

        for (int i = 0; i < 2; i++) {
            serial.write(REPORT_DIGITAL | i);
            serial.write(1);
        }
    }

    /**
     * Gets the major version number of the Firmata protocol implemented in the sketch.
     */
    public int getProtocolMajorVersion()
    {
        return protocolMajorVersion;
    }

    /**
     * Gets the minor version number of the Firmata protocol implemented in the sketch.
     */
    public int getProtocolMinorVersion()
    {
        return protocolMinorVersion;
    }

    public String getSerialName()
    {
        return serial == null ? null : serial.getName();
    }

    /**
     * Returns the last known value read from the digital pin: HIGH or LOW.
     *
     * @param pin
     *            the digital pin whose value should be returned (from 2 to 13, since pins 0 and 1
     *            are used for serial communication)
     */
    public int digitalRead(int pin)
    {
        return (digitalInputData[pin >> 3] >> (pin & 0x07)) & 0x01;
    }

    /**
     * Returns the last known value read from the analog pin: 0 (0 volts) to 1023 (5 volts).
     *
     * @param pin
     *            the analog pin whose value should be returned (from 0 to 5)
     */
    public int analogRead(int pin)
    {
        return analogInputData[pin];
    }

    /**
     * Set a digital pin to input or output mode.
     *
     * @param pin
     *            the pin whose mode to set (from 2 to 13)
     * @param mode
     *            either Arduino.INPUT or Arduino.OUTPUT
     * @throws IOException
     */
    public void pinMode(int pin, int mode) throws IOException
    {
        serial.write(SET_PIN_MODE);
        serial.write(pin);
        serial.write(mode);
    }

    /**
     * Write to a digital pin (the pin must have been put into output mode with pinMode()).
     *
     * @param pin
     *            the pin to write to (from 2 to 13)
     * @param value
     *            the value to write: Arduino.LOW (0 volts) or Arduino.HIGH (5 volts)
     * @throws IOException
     */
    public void digitalWrite(int pin, int value) throws IOException
    {
        int portNumber = (pin >> 3) & 0x0F;

        if (value == 0)
            digitalOutputData[portNumber] &= ~(1 << (pin & 0x07));
        else
            digitalOutputData[portNumber] |= (1 << (pin & 0x07));

        serial.write(DIGITAL_MESSAGE | portNumber);
        serial.write(digitalOutputData[portNumber] & 0x7F);
        serial.write(digitalOutputData[portNumber] >> 7);
    }

    /**
     * Write an analog value (PWM-wave) to a digital pin.
     *
     * @param pin
     *            the pin to write to (must be 9, 10, or 11, as those are they only ones which
     *            support hardware pwm)
     * @param the
     *            value: 0 being the lowest (always off), and 255 the highest (always on)
     * @throws IOException
     */
    public void analogWrite(int pin, int value) throws IOException
    {
        pinMode(pin, PWM);
        serial.write(ANALOG_MESSAGE | (pin & 0x0F));
        serial.write(value & 0x7F);
        serial.write(value >> 7);
    }

    private void setDigitalInputs(int portNumber, int portData)
    {
        digitalInputData[portNumber] = portData;
    }

    private void setAnalogInput(int pin, int value)
    {
        analogInputData[pin] = value;
    }

    private void setVersion(int majorVersion, int minorVersion)
    {
        this.protocolMajorVersion = majorVersion;
        this.protocolMinorVersion = minorVersion;
    }

    private int available()
    {
        return serial.available();
    }

    private void processInput()
    {
        int inputData = serial.read();
        int command;

        if (parsingSysex) {
            if (inputData == END_SYSEX) {
                parsingSysex = false;
                // processSysexMessage();
            }
            else {
                storedInputData[sysexBytesRead] = inputData;
                sysexBytesRead++;
            }
        }
        else if (waitForData > 0 && inputData < 128) {
            waitForData--;
            storedInputData[waitForData] = inputData;

            if (executeMultiByteCommand != 0 && waitForData == 0) {
                // we got everything
                switch (executeMultiByteCommand) {
                case DIGITAL_MESSAGE:
                    setDigitalInputs(multiByteChannel, (storedInputData[0] << 7) + storedInputData[1]);
                    break;
                case ANALOG_MESSAGE:
                    setAnalogInput(multiByteChannel, (storedInputData[0] << 7) + storedInputData[1]);
                    break;
                case REPORT_VERSION:
                    setVersion(storedInputData[1], storedInputData[0]);
                    break;
                }
            }
        }
        else {
            if (inputData < 0xF0) {
                command = inputData & 0xF0;
                multiByteChannel = inputData & 0x0F;
            }
            else {
                command = inputData;
                // commands in the 0xF* range don't use channel data
            }
            switch (command) {
            case DIGITAL_MESSAGE:
            case ANALOG_MESSAGE:
            case REPORT_VERSION:
                waitForData = 2;
                executeMultiByteCommand = command;
                break;
            }
        }
    }
}
