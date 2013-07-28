/*
 * Arduino firmata library for Java, not for Processing
  Copyright (c) 20011 Shigeo Yoshida

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package com.shigeodayo.javarduino;

import java.io.IOException;
import java.util.TooManyListenersException;

import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;
import gsn.wrappers.arduino.Serial;


public class Arduino {

    public static final int INPUT=0;
    public static final int OUTPUT=1;
    public static final int PWM=3;
    public static final int SERVO=4;
    public static final int SHIFT=5;
    public static final int I2C=6;
    public static final int LOW=0;
    public static final int HIGH=1;

    private final int MAX_DATA_BYTES=32;

    private final int DIGITAL_MESSAGE=0x90;
    private final int ANALOG_MESSAGE=0xE0;
    private final int REPORT_ANALOG=0xC0;
    private final int REPORT_DIGITAL=0xD0;
    private final int SET_PIN_MODE=0xF4;
    private final int REPORT_VERSION=0xF9;
    @SuppressWarnings("unused")
    private final int SYSTEM_RESET=0xFF;
    @SuppressWarnings("unused")
    private final int START_SYSEX=0xF0;
    private final int END_SYSEX=0xF7;

    private SerialProxy serialProxy=null;
    private Serial serial=null;

    private int waitForData=0;
    private int executeMultiByteCommand=0;
    private int multiByteChannel=0;
    private int[] storedInputData=new int[MAX_DATA_BYTES];
    private boolean parsingSysex=false;
    private int sysexBytesRead=0;

    private int[] digitalOutputData={0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private int[] digitalInputData={0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private int[] analogInputData={0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    @SuppressWarnings("unused")
    private int majorVersion=0;
    @SuppressWarnings("unused")
    private int minorVersion=0;


    public class SerialProxy implements SerialPortEventListener {
        @Override
        public void serialEvent(SerialPortEvent arg) {
            while(available()>0)
                processInput();
        }
    }

    public Arduino(String iname)
        throws NoSuchPortException, PortInUseException, IOException,
        UnsupportedCommOperationException, TooManyListenersException
    {
        this(iname, 57600);
    }
    public Arduino(String iname, int irate)
        throws NoSuchPortException, PortInUseException, IOException,
        UnsupportedCommOperationException, TooManyListenersException
    {
        serialProxy=new SerialProxy();
        serial=new Serial(serialProxy, iname, irate);


        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for(int i=0; i<6; i++){
            serial.write(REPORT_ANALOG | i);
            serial.write(1);
        }

        for(int i=0; i<2; i++){
            serial.write(REPORT_DIGITAL | i);
            serial.write(1);
        }
    }

    public int digitalRead(int pin){
        return (digitalInputData[pin>>3] >> (pin & 0x07)) & 0x01;
    }

    public int analogRead(int pin){
        return analogInputData[pin];
    }

    public void pinMode(int pin, int mode) throws IOException{
        serial.write(SET_PIN_MODE);
        serial.write(pin);
        serial.write(mode);
    }

    public void digitalWrite(int pin, int value) throws IOException{
        int portNumber=(pin>>3) & 0x0F;

        if(value==0)
            digitalOutputData[portNumber]&=~(1<<(pin & 0x07));
        else
            digitalOutputData[portNumber]|=(1<<(pin & 0x07));
        serial.write(DIGITAL_MESSAGE | portNumber);
        serial.write(digitalOutputData[portNumber] & 0x7F);
        serial.write(digitalOutputData[portNumber] >> 7);
    }

    public void analogWrite(int pin, int value) throws IOException{
        pinMode(pin, PWM);
        serial.write(ANALOG_MESSAGE | (pin & 0x0F));
        serial.write(value & 0x7F);
        serial.write(value >> 7);
    }

    public void setDigitalInputs(int portNumber, int portData){
        digitalInputData[portNumber]=portData;
    }

    public void setAnalogInput(int pin, int value){
        analogInputData[pin]=value;
    }


    public void dispose(){
        this.serial.dispose();
    }

    public static String[] list(){
        return Serial.list();
    }
    
    public String getSerialName(){
        return serial.getName();
    }


    private void setVersion(int majorVersion, int minorVersion){
        this.majorVersion=majorVersion;
        this.minorVersion=minorVersion;
    }

    private int available(){
        return serial.available();
    }

    private void processInput() {
        int inputData=serial.read();
        int command;

        if(parsingSysex) {
            if(inputData==END_SYSEX) {
                parsingSysex=false;
            }else {
                storedInputData[sysexBytesRead]=inputData;
                sysexBytesRead++;
            }
        }else if (waitForData>0 && inputData<128) {
            waitForData--;
            storedInputData[waitForData]=inputData;

            if(executeMultiByteCommand!=0 && waitForData==0) {
                switch(executeMultiByteCommand) {
                    case DIGITAL_MESSAGE:
                        setDigitalInputs(multiByteChannel, (storedInputData[0] << 7)+storedInputData[1]);
                        break;
                    case ANALOG_MESSAGE:
                        setAnalogInput(multiByteChannel, (storedInputData[0] << 7)+storedInputData[1]);
                        break;
                    case REPORT_VERSION:
                        setVersion(storedInputData[1], storedInputData[0]);
                        break;
                }
            }
        }else{
            if(inputData<0xF0) {
                command=inputData & 0xF0;
                multiByteChannel=inputData & 0x0F;
            } else {
                command=inputData;
            }
            switch(command){
                case DIGITAL_MESSAGE:
                case ANALOG_MESSAGE:
                case REPORT_VERSION:
                    waitForData = 2;
                    executeMultiByteCommand=command;
                    break;      
            }
        }
    }
}
