package com.ya0ne.np;

import static com.ya0ne.core.constants.Constants.EMPTY;

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.ya0ne.core.domain.Country;
import com.ya0ne.core.domain.dto.BoundariesData;
import com.ya0ne.core.domain.dto.TZData;
import com.ya0ne.core.domain.track.TrackPoint;
import com.ya0ne.core.exceptions.LoadDataException;
import com.ya0ne.core.utilities.PropertyUtil;
import com.ya0ne.core.utilities.context.ApplicationContextProvider;
import com.ya0ne.np.NMEA.NMEAReceiver;
import com.ya0ne.np.NMEA.NMEASubscriber;
import com.ya0ne.np.NMEA.stubs.ReceiverStub;
import com.ya0ne.np.NMEA.utils.NMEAFileUtilities;
import com.ya0ne.np.misc.GPXProcessor;
import com.ya0ne.np.misc.TZDataService;
import com.ya0ne.np.service.NMEAService;

@Component("nmeaProcessor")
public class NMEAProcessor {
    private static Logger logger = Logger.getLogger(NMEAProcessor.class);

    private static String serialPort;
    private static int nmeaQueueLength;
    private static int nmeaTerminationTimeout;
    public static Integer coldStartTimeout;

    public static Map<Long,Country> countryFactory = new HashMap<>();

    public static AtomicBoolean keepReceiverRunning = new AtomicBoolean(true);
	public static AtomicBoolean keepSubscriberRunning = new AtomicBoolean(true);
	public static AtomicReference<String> bulkUploadFile = new AtomicReference<>(EMPTY);
	public static AtomicReference<Long> trackId = new AtomicReference<>(null);
	public static TZData tzData;
	public static BoundariesData boundariesData;
	public static AtomicInteger currentCountryId = new AtomicInteger();
	public static AtomicReference<TrackPoint> currentPosition = new AtomicReference<>(null);
	public static AtomicReference<Map<Long,TimeZone>> timeZones = new AtomicReference<>(null);
	public static AtomicReference<TimeZone> currentTimeZone = new AtomicReference<>(null);

	// init block
	@Value("${nmeaQueueLength}")
	public void setNmeaQueueLength( int nmeaQueueLength ) {
		NMEAProcessor.nmeaQueueLength = nmeaQueueLength;
	}

	@Value("${nmeaSerialPort}")
	public void setSerialPort( String serialPort ) {
		NMEAProcessor.serialPort = serialPort;
	}

	@Value("${nmeaTerminationTimeout}")
	public void setNmeaTerminationTimeout( int nmeaTerminationTimeout) {
		NMEAProcessor.nmeaTerminationTimeout = nmeaTerminationTimeout;
	}

	@Value("${coldStartTimeout}")
    public void setColdStartTimeout( Integer coldStartTimeout ) {
        NMEAProcessor.coldStartTimeout = coldStartTimeout;
    }

    private static NMEAService nmeaService;
    public static NMEAFileUtilities nmeaFileUtilities;

    @Autowired private NMEAService nmeaServiceAutowired;
    @Autowired private NMEAFileUtilities nmeaFileUtilitiesAutowired;

	/**
	 * Adds shutdown hook for correct shutdown
	 * @throws Exception
	 */
	public NMEAProcessor() throws Exception {
		Runtime.getRuntime().addShutdownHook(new Thread() {
		    public void run() {
		    	logger.info("Terminating NMEA Processor threads.");
		    	keepReceiverRunning.set(false);
		    	try {
		    		Thread.sleep(nmeaTerminationTimeout);
		    		keepSubscriberRunning.set(false);
		    		Thread.sleep(nmeaTerminationTimeout);
		    	} catch( InterruptedException e ) {
		    		e.printStackTrace();
		    		System.exit(-1);
		    	}
			    logger.info("NMEA Processor stopped.");
		    }
	    });
	}

    public static void main(String[] args) throws LoadDataException {
		System.out.println( "NMEA Processor v1.0" );
    	ApplicationContext context = ApplicationContextProvider.initContext("edriveApplicationContext.xml");

    	logger.info("Preparation of static data: start");
    	nmeaService.prepareStaticData();
        logger.info("Preparation of static data: done");

        if( PropertyUtil.getProperty("inputGPX", null) != null ) {
           	logger.info("Processing GPX input");
           	final TZDataService nmeaData = context.getBean(TZDataService.class);
           	nmeaData.setTZData();
           	GPXProcessor gpxProcessor = (GPXProcessor)context.getBean(GPXProcessor.class);
        	int exitCode = gpxProcessor.saveGPX();
        	logger.info("Exit code: " + exitCode);
        	System.exit(exitCode);
        }

        // Initialization of NMEA subscriber
		ArrayBlockingQueue<Object> queue = new ArrayBlockingQueue<>(nmeaQueueLength);

		NMEASubscriber nmeaSubscriber = (NMEASubscriber)context.getBean("nmeaSubscriber", new Object[]{queue});
        Thread subscriber = new Thread(nmeaSubscriber);
        subscriber.setName("SUBSCRIBER");

        // if bulkUpload is set to true, then we have to load backlog file
		if( PropertyUtil.getProperty("backlogFile", null) != null ) {
			ReceiverStub backlogReceiver = new ReceiverStub(queue,PropertyUtil.getProperty("backlogFile", null));
    		Thread receiver = new Thread(backlogReceiver);
    		receiver.start();
		} else { // else - start NMEA receiver
			if( PropertyUtil.getProperty("serialPort", null) != null ) {
				serialPort = PropertyUtil.getProperty("serialPort", null);
			}
	        try {
    			NMEAReceiver.readFromPort(serialPort,queue);
        	} catch(Exception e) {
        		e.printStackTrace();
        	}
		}

		// start of subscriber
		subscriber.start();
	}

    @PostConstruct
    public void init() {
        nmeaService = this.nmeaServiceAutowired;
        nmeaFileUtilities = this.nmeaFileUtilitiesAutowired;
    }
}
