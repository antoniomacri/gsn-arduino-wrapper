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

/**
 * Class for sending and receiving data using the serial communication protocol.
 *
 * Default parameters used are: 9600 baud, 8 data bits (SerialPort.DATABITS_8), 1 stop bit
 * (SerialPort.STOPBITS_1), no parity (SerialPort.PARITY_NONE).
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
     *
     * @throws NoSuchPortException
     *             the specified port does not exist or it is not a serial port
     * @throws PortInUseException
     *             the specified port is already in use by some other application -or- the specified
     *             port cannot be locked (insufficient permissions on /var/lock)
     * @throws IOException
     *             couldn't retrieve the input or output stream associated with the port -or- the
     *             specified port does not support receiving or sending data
     * @throws UnsupportedCommOperationException
     *             the specified parameters are not valid
     * @throws TooManyListenersException
     *             the serial port already has a registered listener
     */
    public Serial(SerialPortEventListener listener, String iname) throws NoSuchPortException, PortInUseException,
            IOException, UnsupportedCommOperationException, TooManyListenersException
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
     *
     * @throws NoSuchPortException
     *             the specified port does not exist or it is not a serial port
     * @throws PortInUseException
     *             the specified port is already in use by some other application -or- the specified
     *             port cannot be locked (insufficient permissions on /var/lock)
     * @throws IOException
     *             couldn't retrieve the input or output stream associated with the port -or- the
     *             specified port does not support receiving or sending data
     * @throws UnsupportedCommOperationException
     *             the specified parameters are not valid
     * @throws TooManyListenersException
     *             the serial port already has a registered listener
     */
    public Serial(SerialPortEventListener listener, String iname, int baudrate) throws NoSuchPortException,
            PortInUseException, IOException, UnsupportedCommOperationException, TooManyListenersException
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
     *
     * @throws NoSuchPortException
     *             the specified port does not exist or it is not a serial port
     * @throws PortInUseException
     *             the specified port is already in use by some other application -or- the specified
     *             port cannot be locked (insufficient permissions on /var/lock)
     * @throws IOException
     *             couldn't retrieve the input or output stream associated with the port -or- the
     *             specified port does not support receiving or sending data
     * @throws UnsupportedCommOperationException
     *             the specified parameters are not valid
     * @throws TooManyListenersException
     *             the serial port already has a registered listener
     */
    public Serial(SerialPortEventListener listener, String iname, int baudrate, int dataBits, int stopBits, int parity)
            throws NoSuchPortException, PortInUseException, IOException, UnsupportedCommOperationException,
            TooManyListenersException
    {
        // The following calls may throw (in turn) NoSuchPortException or PortInUseException:
        // just rethrow
        CommPortIdentifier portId = CommPortIdentifier.getPortIdentifier(iname);
        if (portId.getPortType() != CommPortIdentifier.PORT_SERIAL) {
            throw new NoSuchPortException();
        }
        port = (SerialPort) portId.open(this.getClass().getName(), 2000);

        try {
            // getInputStream() and getOutputStream() may throw an IOException
            input = port.getInputStream();
            output = port.getOutputStream();
            if (input == null) {
                throw new IOException("The specified port is unidirectional and doesn't support receiving data.");
            }
            if (output == null) {
                throw new IOException("The specified port is unidirectional and doesn't support sending data.");
            }

            // setSerialPortParams() may throw UnsupportedCommOperationException
            port.setSerialPortParams(baudrate, dataBits, stopBits, parity);

            // addEventListener() may throw TooManyListenersException
            this.listener = listener; // Better to set the listener before addEventListener()
            port.addEventListener(this);
            port.notifyOnDataAvailable(true);
        }
        catch (Exception e) {
            // Close both streams as well as the port (see dispose())
            try {
                if (input != null) {
                    input.close();
                    input = null;
                }
                if (output != null) {
                    output.close();
                    output = null;
                }
                if (port != null) {
                    port.close();
                    port = null;
                }
            }
            catch (Exception e2) {
                // Don't do anything.
                // Printing a stack trace here may confuse about the original source of exception.
            }
            throw e;
        }
    }

    /**
     * Closes the connection to the serial port.
     *
     * It just calls dispose().
     */
    public void stop()
    {
        dispose();
    }

    /**
     * Closes the connection to the serial port.
     */
    // Since dispose() closes the input stream and sets it to null, to prevent errors
    // we declare both dispose() and serialEvent() as synchronized. (Also suggested on
    // http://playground.arduino.cc/Interfacing/Java, see "Proper Port Handling".)
    synchronized public void dispose()
    {
        // It is very important to close input and output streams as well as the port.
        // Otherwise Java, driver and OS resources are not released. See:
        // http://en.wikibooks.org/wiki/Serial_Programming/Serial_Java#Initialize_a_Serial_Port

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

    /**
     * Gets the name of the serial port used or null if the connection has been closed.
     */
    public String getName()
    {
        return port == null ? null : port.getName();
    }

    /**
     * Sets or clears the DTR (Data Terminal Ready) bit in the UART, if supported by the underlying
     * implementation.
     *
     * @param state
     *            true to set the DTR bit or false to clear it
     */
    // Addition from Tom Hulbert
    public void setDTR(boolean state)
    {
        port.setDTR(state);
    }

    /**
     * This method is called whenever a new event from the serial port occurs.
     *
     * It is not intended to be called directly. It basically comes into play when new data is
     * available from the port: first, the received bytes are copied to the internal buffer, and
     * then the serialEvent of the listener is invoked.
     *
     * @param serialEvent
     *            the serial event object specifying the type of event occurred
     */
    // Declared as synchronized to avoid errors when an event is being handled
    // after the dispose() is called. See also dispose() for more info.
    @Override
    synchronized public void serialEvent(SerialPortEvent serialEvent)
    {
        if (input == null) {
            return;
        }
        if (serialEvent.getEventType() != SerialPortEvent.DATA_AVAILABLE) {
            return;
        }
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
            e.printStackTrace();
        }
    }

    /**
     * Sets number of bytes to buffer before calling listener's serialEvent().
     *
     * @param count
     *            number of bytes to buffer
     */
    public void buffer(int count)
    {
        bufferUntil = false;
        bufferSize = count;
    }

    /**
     * Sets a specific byte to buffer until before calling listener's serialEvent().
     *
     * @param what
     *            the value to buffer until
     */
    public void bufferUntil(int what)
    {
        bufferUntil = true;
        bufferUntilByte = (byte) what;
    }

    /**
     * Returns the number of bytes that have been read from the serial port and are waiting to be
     * dealt with by the user.
     */
    public int available()
    {
        return (bufferLast - bufferIndex);
    }

    /**
     * Ignore all the bytes read so far and empty the buffer.
     */
    public void clear()
    {
        bufferLast = 0;
        bufferIndex = 0;
    }

    /**
     * Reads a byte from the serial buffer.
     *
     * @return a number between 0 and 255 representing the byte read from the serial buffer or -1 if
     *         the buffer is empty
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
     * Reads the very last byte received and clears the serial buffer. Useful to retrieve just the
     * most recent value sent over the port.
     *
     * @return a number between 0 and 255 representing the byte read from the serial buffer or -1 if
     *         the buffer is empty
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
     * Reads a byte from the serial buffer as a char.
     *
     * @return the char read from the serial buffer or (char)(-1) if the buffer is empty
     */
    public char readChar()
    {
        if (bufferIndex == bufferLast) return (char) (-1);
        return (char) read();
    }

    /**
     * Reads as a char the very last byte received and clears the serial buffer. Useful to retrieve
     * just the most recent value sent over the port.
     *
     * @return the char read from the serial buffer or (char)(-1) if the buffer is empty
     */
    public char lastChar()
    {
        if (bufferIndex == bufferLast) return (char) (-1);
        return (char) last();
    }

    /**
     * Returns a new byte array holding the whole content of the serial buffer.
     *
     * This method is not particularly memory/speed efficient, because it creates a byte array on
     * each read. Prefer readBytes(byte b[]).
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
     * Grabs the whole content of the serial buffer and copies it into the given buffer. If the
     * serial buffer contains more bytes than the given buffer's length, only those that will fit
     * are read and copied.
     *
     * @param outgoing
     *            a buffer to copy the data into
     *
     * @return an integer specifying how many bytes were read
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
     * Reads an array of bytes from the serial buffer until (and including) a particular byte is
     * encountered. If the specified byte isn't in the serial buffer, then 'null' is returned.
     *
     * @param interesting
     *            the byte designated to mark the end of the data
     *
     * @return a buffer containing the bytes read or null if the specified byte was not found
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
     * Reads an array of bytes from the serial buffer until (and including) a particular byte is
     * encountered.
     *
     * @param interesting
     *            the byte designated to mark the end of the data
     * @param outgoing
     *            a buffer to copy the data into
     *
     * @return the number of bytes read, or -1 if the given buffer's capacity is insufficient, or
     *         zero if the buffer is empty or the specified byte was not found
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
     * Returns the whole content of the serial buffer as a String, assuming that the incoming
     * characters are ASCII.
     */
    public String readString()
    {
        if (bufferIndex == bufferLast) return null;
        return new String(readBytes());
    }

    /**
     * Reads a string from the serial buffer until (and including) a particular byte is encountered.
     * If the specified byte isn't in the serial buffer, then 'null' is returned.
     *
     * @param interesting
     *            the byte designated to mark the end of the data
     *
     * @return a String containing the bytes read or null if the specified byte was not found
     */
    public String readStringUntil(int interesting)
    {
        byte b[] = readBytesUntil(interesting);
        if (b == null) return null;
        return new String(b);
    }

    /**
     * Writes the specified byte to the serial port. The byte to be written is the eight low-order
     * bits of the argument (the 24 high-order bits are ignored).
     *
     * @param what
     *            the byte to write
     *
     * @throws IOException
     *             if an I/O error occurs (for example if the output stream has been closed)
     */
    public void write(int what) throws IOException
    {
        // will also cover char
        output.write(what & 0xff); // for good measure do the &
        output.flush(); // it is a good idea to flush the stream?
    }

    /**
     * Writes the whole content of the given byte array to the serial port.
     *
     * @param bytes
     *            the data to write
     *
     * @throws IOException
     *             if an I/O error occurs (for example if the output stream has been closed)
     */
    public void write(byte bytes[]) throws IOException
    {
        output.write(bytes);
        output.flush(); // it is a good idea to flush the stream?
    }

    /**
     * Writes a string to the serial port.
     *
     * Notice that this doesn't account for Unicode (two bytes per char), nor will it send UTF8
     * characters. It assumes that you mean to send a byte buffer (most often the case for
     * networking and serial i/o) and will only use the bottom 8 bits of each char in the string.
     * (Meaning that internally it uses String.getBytes)
     *
     * If you want to move Unicode data, you can first convert the String to a byte stream in the
     * representation of your choice (i.e. UTF8 or two-byte Unicode data), and send it as a byte
     * array.
     *
     * @param what
     *            the String to write
     *
     * @throws IOException
     *             if an I/O error occurs (for example if the output stream has been closed)
     */
    public void write(String what) throws IOException
    {
        write(what.getBytes());
    }

    /**
     * Retrieves the list of available serial ports.
     *
     * If this just hangs and never completes on Windows, it may be because the DLL doesn't have its
     * exec bit set. Why the hell that'd be the case, who knows.
     */
    static public String[] list()
    {
        Vector<String> list = new Vector<String>();

        Enumeration<?> portList = CommPortIdentifier.getPortIdentifiers();
        while (portList.hasMoreElements()) {
            CommPortIdentifier portId = (CommPortIdentifier) portList.nextElement();

            if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                String name = portId.getName();
                list.addElement(name);
            }
        }

        String outgoing[] = new String[list.size()];
        list.copyInto(outgoing);
        return outgoing;
    }
}
