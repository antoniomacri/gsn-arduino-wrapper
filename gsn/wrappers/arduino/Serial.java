/*
  Serial communication

  The original code of this library is part of the Processing
  project (see http://processing.org). You can obtain it from
    https://github.com/processing/processing
  (java/libraries/serial/src/processing/serial/Serial.java).

  Here, it has been detached from Processing and adapted to a
  more general usage.

  Copyright (c) 2013 Antonio Macrì <ing.antonio.macri@gmail.com>

  Copyright (c) 2004-05 Ben Fry & Casey Reas

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

import java.io.*;
import java.util.*;

import javax.sound.sampled.LineUnavailableException;

/**
 * @generate Serial.xml
 * @webref net
 * @usage application
 *
 *        DEFAULT: 9600 baud, 8 data bits (SerialPort.DATABITS_8), 1 stop bit (SerialPort.STOPBITS_1), no parity
 *        (SerialPort.PARITY_NONE)
 */
public class Serial implements SerialPortEventListener
{
    private static final int DEFAULT_RATE = 9600;
    private static final int DEFAULT_PARITY = SerialPort.PARITY_NONE;
    private static final int DEFAULT_DATABITS = SerialPort.DATABITS_8;
    private static final int DEFAULT_STOPBITS = SerialPort.STOPBITS_1;

    private SerialPortEventListener listener;

    private SerialPort port;

    private InputStream input;
    private OutputStream output;

    private byte buffer[] = new byte[32768];
    private int bufferIndex;
    private int bufferLast;

    private int bufferSize = 1; // how big before reset or event firing
    private boolean bufferUntil;
    private byte bufferUntilByte;

    /**
     * Creates an instance of the Serial class bound to the specified serial port, using the given
     * object as a listener.
     *
     * @param listener
     *            the SerialPortEventListener which gets notified whenever new data is available
     * @param iname
     *            the name of the serial port to use (for example, "COM1" or "/dev/ttyACM0")
     * @throws NoSuchPortException
     *             the specified port does not exist
     * @throws PortInUseException
     *             the specified port is already in use by some other application -or- the specified
     *             port cannot be locked (insufficient permissions on /var/lock)
     * @throws IOException
     *             couldn't retrieve the input or output stream associated with the port
     * @throws LineUnavailableException
     *             the specified port does not support receiving or sending data
     * @throws UnsupportedCommOperationException
     *             the specified parameters are not valid
     * @throws TooManyListenersException
     *             the serial port already has a registered listener
     */
    public Serial(SerialPortEventListener listener, String iname) throws NoSuchPortException, PortInUseException,
            IOException, LineUnavailableException, UnsupportedCommOperationException, TooManyListenersException
    {
        this(listener, iname, DEFAULT_RATE, DEFAULT_PARITY, DEFAULT_DATABITS, DEFAULT_STOPBITS);
    }

    /**
     * Creates an instance of the Serial class bound to the specified serial port, using the given
     * object as a listener.
     *
     * @param listener
     *            the SerialPortEventListener which gets notified whenever new data is available
     * @param iname
     *            the name of the serial port to use (for example, "COM1" or "/dev/ttyACM0")
     * @param baudrate
     *            the baud rate to use (for example, 9600 or 115200)
     * @throws NoSuchPortException
     *             the specified port does not exist
     * @throws PortInUseException
     *             the specified port is already in use by some other application -or- the specified
     *             port cannot be locked (insufficient permissions on /var/lock)
     * @throws IOException
     *             couldn't retrieve the input or output stream associated with the port
     * @throws LineUnavailableException
     *             the specified port does not support receiving or sending data
     * @throws UnsupportedCommOperationException
     *             the specified parameters are not valid
     * @throws TooManyListenersException
     *             the serial port already has a registered listener
     */
    public Serial(SerialPortEventListener listener, String iname, int baudrate) throws NoSuchPortException,
            PortInUseException, IOException, LineUnavailableException, UnsupportedCommOperationException,
            TooManyListenersException
    {
        this(listener, iname, baudrate, DEFAULT_DATABITS, DEFAULT_STOPBITS, DEFAULT_PARITY);
    }

    /**
     * Creates an instance of the Serial class bound to the specified serial port, using the given
     * object as a listener.
     *
     * @param listener
     *            the SerialPortEventListener which gets notified whenever new data is available
     * @param iname
     *            the name of the serial port to use (for example, "COM1" or "/dev/ttyACM0")
     * @param baudrate
     *            the baud rate to use (for example, 9600 or 115200)
     * @param dataBits
     *            the amount of bits transferred at a time (for example, SerialPort.DATABITS_8)
     * @param stopBits
     *            the type of stop bits to use (for example, SerialPort.STOPBITS_1)
     * @param parity
     *            the parity method to use (for example, SerialPort.PARITY_NONE)
     * @throws NoSuchPortException
     *             the specified port does not exist
     * @throws PortInUseException
     *             the specified port is already in use by some other application -or- the specified
     *             port cannot be locked (insufficient permissions on /var/lock)
     * @throws IOException
     *             couldn't retrieve the input or output stream associated with the port
     * @throws LineUnavailableException
     *             the specified port does not support receiving or sending data
     * @throws UnsupportedCommOperationException
     *             the specified parameters are not valid
     * @throws TooManyListenersException
     *             the serial port already has a registered listener
     */
    public Serial(SerialPortEventListener listener, String iname, int baudrate, int dataBits, int stopBits, int parity)
            throws NoSuchPortException, PortInUseException, IOException, LineUnavailableException,
            UnsupportedCommOperationException, TooManyListenersException
    {
        // The following calls may throw (in turn) NoSuchPortException or PortInUseException:
        // just rethrow
        CommPortIdentifier portId = CommPortIdentifier.getPortIdentifier(iname);
        port = (SerialPort) portId.open(this.getClass().getName(), 2000);

        try {
            // getInputStream() and getOutputStream() may throw an IOException
            input = port.getInputStream();
            output = port.getOutputStream();
            if (input == null) {
                throw new LineUnavailableException(
                        "The specified port is unidirectional and doesn't support receiving data.");
            }
            if (output == null) {
                throw new LineUnavailableException(
                        "The specified port is unidirectional and doesn't support sending data.");
            }

            // setSerialPortParams() may throw UnsupportedCommOperationException
            port.setSerialPortParams(baudrate, dataBits, stopBits, parity);

            // addEventListener() may throw TooManyListenersException
            this.listener = listener; // Better to set the listener before addEventListener()
            port.addEventListener(this);
            port.notifyOnDataAvailable(true);
        }
        catch (Exception e) {
            if (port != null) {
                port.close();
                port = null;
            }
            input = null;
            output = null;
            throw e;
        }
    }

    /**
     * @generate Serial_stop.xml
     * @webref serial:serial
     * @usage web_application
     */
    public void stop()
    {
        dispose();
    }

    /**
     * Used by PApplet to shut things down.
     */
    public void dispose()
    {
        try {
            if (input != null) {
                input.close();
                input = null;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        try {
            if (output != null) {
                output.close();
                output = null;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        try {
            if (port != null) {
                port.close();
                port = null;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getName()
    {
        return port.getName();
    }

    /**
     * Set the DTR line. Addition from Tom Hulbert.
     */
    public void setDTR(boolean state)
    {
        port.setDTR(state);
    }

    /**
     * @generate serialEvent.xml
     * @webref serial:events
     * @usage web_application
     * @param serialEvent
     *            the port where new data is available
     */
    synchronized public void serialEvent(SerialPortEvent serialEvent)
    {
        if (serialEvent.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
            try {
                while (input.available() > 0) {
                    synchronized (buffer) {
                        if (bufferLast == buffer.length) {
                            byte temp[] = new byte[bufferLast << 1];
                            System.arraycopy(buffer, 0, temp, 0, bufferLast);
                            buffer = temp;
                        }
                        buffer[bufferLast++] = (byte) input.read();
                        if (listener != null) {
                            if ((bufferUntil && (buffer[bufferLast - 1] == bufferUntilByte))
                                    || (!bufferUntil && ((bufferLast - bufferIndex) >= bufferSize))) {
                                listener.serialEvent(serialEvent);
                            }
                        }
                    }
                }

            }
            catch (IOException e) {
                errorMessage("serialEvent", e);
            }
        }
    }

    /**
     * @generate Serial_buffer.xml
     * @webref serial:serial
     * @usage web_application
     * @param count
     *            number of bytes to buffer
     */
    public void buffer(int count)
    {
        bufferUntil = false;
        bufferSize = count;
    }

    /**
     * @generate Serial_bufferUntil.xml
     * @webref serial:serial
     * @usage web_application
     * @param what
     *            the value to buffer until
     */
    public void bufferUntil(int what)
    {
        bufferUntil = true;
        bufferUntilByte = (byte) what;
    }

    /**
     * @generate Serial_available.xml
     * @webref serial:serial
     * @usage web_application
     */
    public int available()
    {
        return (bufferLast - bufferIndex);
    }

    /**
     * @generate Serial_clear.xml
     * @webref serial:serial
     * @usage web_application
     */
    public void clear()
    {
        bufferLast = 0;
        bufferIndex = 0;
    }

    /**
     * @generate Serial_read.xml
     * @webref serial:serial
     * @usage web_application
     */
    public int read()
    {
        if (bufferIndex == bufferLast) return -1;

        synchronized (buffer) {
            int outgoing = buffer[bufferIndex++] & 0xff;
            if (bufferIndex == bufferLast) { // rewind
                bufferIndex = 0;
                bufferLast = 0;
            }
            return outgoing;
        }
    }

    /**
     * @generate Serial_last.xml <h3>Advanced</h3> Same as read() but returns the very last value received and clears
     *           the buffer. Useful when you just want the most recent value sent over the port.
     * @webref serial:serial
     * @usage web_application
     */
    public int last()
    {
        if (bufferIndex == bufferLast) return -1;
        synchronized (buffer) {
            int outgoing = buffer[bufferLast - 1];
            bufferIndex = 0;
            bufferLast = 0;
            return outgoing;
        }
    }

    /**
     * @generate Serial_readChar.xml
     * @webref serial:serial
     * @usage web_application
     */
    public char readChar()
    {
        if (bufferIndex == bufferLast) return (char) (-1);
        return (char) read();
    }

    /**
     * @generate Serial_lastChar.xml
     * @webref serial:serial
     * @usage web_application
     */
    public char lastChar()
    {
        if (bufferIndex == bufferLast) return (char) (-1);
        return (char) last();
    }

    /**
     * @generate Serial_readBytes.xml
     * @webref serial:serial
     * @usage web_application
     */
    public byte[] readBytes()
    {
        if (bufferIndex == bufferLast) return null;

        synchronized (buffer) {
            int length = bufferLast - bufferIndex;
            byte outgoing[] = new byte[length];
            System.arraycopy(buffer, bufferIndex, outgoing, 0, length);

            bufferIndex = 0; // rewind
            bufferLast = 0;
            return outgoing;
        }
    }

    /**
     * <h3>Advanced</h3> Grab whatever is in the serial buffer, and stuff it into a byte buffer passed in by the user.
     * This is more memory/time efficient than readBytes() returning a byte[] array.
     * 
     * Returns an int for how many bytes were read. If more bytes are available than can fit into the byte array, only
     * those that will fit are read.
     */
    public int readBytes(byte outgoing[])
    {
        if (bufferIndex == bufferLast) return 0;

        synchronized (buffer) {
            int length = bufferLast - bufferIndex;
            if (length > outgoing.length) length = outgoing.length;
            System.arraycopy(buffer, bufferIndex, outgoing, 0, length);

            bufferIndex += length;
            if (bufferIndex == bufferLast) {
                bufferIndex = 0; // rewind
                bufferLast = 0;
            }
            return length;
        }
    }

    /**
     * @generate Serial_readBytesUntil.xml
     * @webref serial:serial
     * @usage web_application
     * @param interesting
     *            character designated to mark the end of the data
     */
    public byte[] readBytesUntil(int interesting)
    {
        if (bufferIndex == bufferLast) return null;
        byte what = (byte) interesting;

        synchronized (buffer) {
            int found = -1;
            for (int k = bufferIndex; k < bufferLast; k++) {
                if (buffer[k] == what) {
                    found = k;
                    break;
                }
            }
            if (found == -1) return null;

            int length = found - bufferIndex + 1;
            byte outgoing[] = new byte[length];
            System.arraycopy(buffer, bufferIndex, outgoing, 0, length);

            bufferIndex += length;
            if (bufferIndex == bufferLast) {
                bufferIndex = 0; // rewind
                bufferLast = 0;
            }
            return outgoing;
        }
    }

    /**
     * <h3>Advanced</h3> If outgoing[] is not big enough, then -1 is returned, and an error message is printed on the
     * console. If nothing is in the buffer, zero is returned. If 'interesting' byte is not in the buffer, then 0 is
     * returned.
     * 
     * @param outgoing
     *            passed in byte array to be altered
     */
    public int readBytesUntil(int interesting, byte outgoing[])
    {
        if (bufferIndex == bufferLast) return 0;
        byte what = (byte) interesting;

        synchronized (buffer) {
            int found = -1;
            for (int k = bufferIndex; k < bufferLast; k++) {
                if (buffer[k] == what) {
                    found = k;
                    break;
                }
            }
            if (found == -1) return 0;

            int length = found - bufferIndex + 1;
            if (length > outgoing.length) {
                System.err.println("readBytesUntil() byte buffer is" + " too small for the " + length
                        + " bytes up to and including char " + interesting);
                return -1;
            }
            System.arraycopy(buffer, bufferIndex, outgoing, 0, length);

            bufferIndex += length;
            if (bufferIndex == bufferLast) {
                bufferIndex = 0; // rewind
                bufferLast = 0;
            }
            return length;
        }
    }

    /**
     * @generate Serial_readString.xml
     * @webref serial:serial
     * @usage web_application
     */
    public String readString()
    {
        if (bufferIndex == bufferLast) return null;
        return new String(readBytes());
    }

    /**
     * @generate Serial_readStringUntil.xml <h3>Advanced</h3> If you want to move Unicode data, you can first convert
     *           the String to a byte stream in the representation of your choice (i.e. UTF8 or two-byte Unicode data),
     *           and send it as a byte array.
     * 
     * @webref serial:serial
     * @usage web_application
     * @param interesting
     *            character designated to mark the end of the data
     */
    public String readStringUntil(int interesting)
    {
        byte b[] = readBytesUntil(interesting);
        if (b == null) return null;
        return new String(b);
    }

    /**
     * <h3>Advanced</h3> This will handle both ints, bytes and chars transparently.
     * 
     * @param what
     *            data to write
     */
    public void write(int what)
    { // will also cover char
        try {
            output.write(what & 0xff); // for good measure do the &
            output.flush(); // hmm, not sure if a good idea

        }
        catch (Exception e) { // null pointer or serial port dead
            errorMessage("write", e);
        }
    }

    /**
     * @param bytes
     *            [] data to write
     */
    public void write(byte bytes[])
    {
        try {
            output.write(bytes);
            output.flush(); // hmm, not sure if a good idea

        }
        catch (Exception e) { // null pointer or serial port dead
            // errorMessage("write", e);
            e.printStackTrace();
        }
    }

    /**
     * @generate Serial_write.xml <h3>Advanced</h3> Write a String to the output. Note that this doesn't account for
     *           Unicode (two bytes per char), nor will it send UTF8 characters.. It assumes that you mean to send a
     *           byte buffer (most often the case for networking and serial i/o) and will only use the bottom 8 bits of
     *           each char in the string. (Meaning that internally it uses String.getBytes)
     * 
     *           If you want to move Unicode data, you can first convert the String to a byte stream in the
     *           representation of your choice (i.e. UTF8 or two-byte Unicode data), and send it as a byte array.
     * 
     * @webref serial:serial
     * @usage web_application
     * @param what
     *            data to write
     */
    public void write(String what)
    {
        write(what.getBytes());
    }

    /**
     * @generate Serial_list.xml <h3>Advanced</h3> If this just hangs and never completes on Windows, it may be because
     *           the DLL doesn't have its exec bit set. Why the hell that'd be the case, who knows.
     * 
     * @webref serial
     * @usage web_application
     */
    static public String[] list()
    {
        Vector<String> list = new Vector<String>();
        try {
            Enumeration<?> portList = CommPortIdentifier.getPortIdentifiers();
            while (portList.hasMoreElements()) {
                CommPortIdentifier portId = (CommPortIdentifier) portList.nextElement();

                if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                    String name = portId.getName();
                    list.addElement(name);
                }
            }

        }
        catch (UnsatisfiedLinkError e) {
            errorMessage("ports", e);

        }
        catch (Exception e) {
            errorMessage("ports", e);
        }
        String outgoing[] = new String[list.size()];
        list.copyInto(outgoing);
        return outgoing;
    }

    /**
     * General error reporting, all corraled here just in case I think of something slightly more intelligent to do.
     */
    static public void errorMessage(String where, Throwable e)
    {
        e.printStackTrace();
        throw new RuntimeException("Error inside Serial." + where + "()");
    }
}
