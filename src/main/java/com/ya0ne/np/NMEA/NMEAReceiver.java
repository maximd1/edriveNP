package com.ya0ne.np.NMEA;

import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.TooManyListenersException;
import java.util.concurrent.ArrayBlockingQueue;

import static com.ya0ne.core.constants.WSConstants.GPGGA;
import static com.ya0ne.core.constants.WSConstants.GPRMC;

import org.apache.log4j.Logger;

import com.ya0ne.np.NMEAProcessor;

public class NMEAReceiver implements Runnable, SerialPortEventListener {
    private static Logger logger = Logger.getLogger(NMEAReceiver.class);
    
    StringBuffer serialString = new StringBuffer();

    static CommPortIdentifier portId;  
    static CommPortIdentifier saveportId;  
    static Enumeration<?>        portList;  
    InputStream           inputStream;  
    SerialPort           serialPort;  
    Thread           readThread;

    static String messageString = null;
    static SerialPort defaultPort;
    static OutputStream outputStream;
    
    private static ArrayBlockingQueue<Object> queue;
    
    public NMEAReceiver() {
        logger.info("Initialisation of NMEA receiver...");
        // initalize serial port  
        try {  
           serialPort = (SerialPort) portId.open("NMEAReceiver", 2000);  
        } catch (PortInUseException e) {
            logger.error("Port " + defaultPort + " is already in use.");
            NMEAProcessor.keepReceiverRunning.set(false);
        }  
        
        try {  
            inputStream = serialPort.getInputStream();  
        } catch (IOException e) {}  
        
        try {  
           serialPort.addEventListener(this);  
        } catch (TooManyListenersException e) {}  
        
        // activate the DATA_AVAILABLE notifier  
        serialPort.notifyOnDataAvailable(true);  
        
        try {  
            // set port parameters  
            serialPort.setSerialPortParams(4800, 
                                          SerialPort.DATABITS_8,   
                                          SerialPort.STOPBITS_1,   
                                          SerialPort.PARITY_NONE);  
        } catch (UnsupportedCommOperationException e) {}  
          
        // start the read thread  
        readThread = new Thread(this);
        readThread.setName("RECEIVER");
        readThread.start();
    }

    public static void readFromPort( String defaultPort, ArrayBlockingQueue<Object> queue1 ) throws InterruptedException {
        boolean portFound = false;
        portList = CommPortIdentifier.getPortIdentifiers();  
        while (portList.hasMoreElements()) {  
           portId = (CommPortIdentifier) portList.nextElement();  
           if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {  
              if (portId.getName().equals(defaultPort)) {  
                 portFound = true;  
                 // init reader thread  
                 queue = queue1;
                 logger.info("Cold Start");
                 Thread.sleep(Integer.valueOf(NMEAProcessor.coldStartTimeout)); // "Cold Start" timeout
                 logger.info("NMEA receiver is running");
                 new NMEAReceiver();  
              }
           }
        }   
        if (!portFound) {
           logger.error("Serial port " + defaultPort + " not found or GPS receiver is not attached.");
           NMEAProcessor.keepReceiverRunning.set(false);
           NMEAProcessor.keepSubscriberRunning.set(false);
           System.exit(-1);
        }   
    }

    public void serialEvent(SerialPortEvent event) {
        switch (event.getEventType()) {
	        case SerialPortEvent.BI:
	        case SerialPortEvent.OE:
	        case SerialPortEvent.FE:
	        case SerialPortEvent.PE:
	        case SerialPortEvent.CD:
	        case SerialPortEvent.CTS:
	        case SerialPortEvent.DSR:
	        case SerialPortEvent.RI:
            case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
                System.out.println("OUTPUT_BUFFER_EMPTY");
            break;
            case SerialPortEvent.DATA_AVAILABLE:
                // we get here if data has been received
                byte[] readBuffer = new byte[20];
                try {
                    int availableBytes = inputStream.available();
                    if (availableBytes > 0) {
                        // Read the serial port
                        inputStream.read(readBuffer);
                        String s = new String(readBuffer, 0, availableBytes);
                        if( s.startsWith("$") ) { // new character sequence from the port
                        	// splitting to array because NMEA sentence can contain crlf and so be multiline
                        	// and - put to queue!
                        	String[] parts = serialString.toString().split("\\r\\n");
                        	for( String str : parts ) {
                        		if( str.startsWith(GPRMC) || str.startsWith(GPGGA) ) {
                        		    logger.info("NMEA sentence to queue: " + str);
                        		    queue.put(str);
                        		}
                        	}
                            serialString = new StringBuffer(); // ready for new line
                        }
                        serialString.append(s); // collecting all incoming data
                    }
                } catch (IOException | InterruptedException e) {
                }
            break;
        }
    }

    @Override
    public void run() {
    	logger.info("NMEA Receiver started.");
        while(NMEAProcessor.keepReceiverRunning.get()) {}
        close();
        logger.info("NMEA Receiver stopped.");
    }
    
    /**
     * Closes all communications with serial port
     */
    public synchronized void close() {
        if( serialPort != null ) {
            serialPort.removeEventListener();
            serialPort.close();
        }
    }
}
