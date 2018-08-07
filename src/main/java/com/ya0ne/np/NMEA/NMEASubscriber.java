package com.ya0ne.np.NMEA;

import static com.ya0ne.core.constants.Constants.DIR_BACKLOG;
import static com.ya0ne.core.constants.Constants.EMPTY;
import static com.ya0ne.core.constants.WSConstants.GPGGA;
import static com.ya0ne.core.domain.converters.GPXConverter.toNmeaSentenceDto;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ya0ne.core.constants.WSConstants;
import com.ya0ne.core.domain.Country;
import com.ya0ne.core.domain.NMEASentence;
import com.ya0ne.core.domain.dto.GeoTimeZoneId;
import com.ya0ne.core.domain.track.TrackPoint;
import com.ya0ne.core.exceptions.nmea.IllegalNMEACharacterException;
import com.ya0ne.core.exceptions.nmea.InvalidContentException;
import com.ya0ne.core.exceptions.nmea.StartNotFoundException;
import com.ya0ne.core.exceptions.nmea.TrackServiceException;
import com.ya0ne.core.generated.NmeaSentenceDto;
import com.ya0ne.core.utilities.http.rest.RESTUtilities;
import com.ya0ne.core.utilities.nmea.NMEAUtilities;
import com.ya0ne.np.NMEAProcessor;
import com.ya0ne.np.NMEA.utils.NMEACurrentPositionWriter;
import com.ya0ne.np.NMEA.utils.NMEAFileUtilities;
import com.ya0ne.np.misc.TZDataService;
import com.ya0ne.np.service.NMEAService;

@Component("nmeaSubscriber")
@Scope("prototype")
public class NMEASubscriber implements Runnable {
	private static Logger logger = Logger.getLogger(NMEASubscriber.class);

	@Autowired private NMEAMonitor nmeaMonitor;
	@Autowired private TZDataService nmeaData;
	@Autowired private NMEAUtilities nmeaUtilities;
	@Autowired private ApplicationContext applicationContext;
	@Autowired private NMEAService nmeaService;

	private MultiValueMap<String, String> parametersMap;
	private ArrayBlockingQueue<Object> queue;
	private Country currentCountry;
	private Country country;
	private NMEASentence nmeaSentence;
	private NMEAFileUtilities nmeaFileUtilities;

	private Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();

	@Value("${wsURL}") private String SERVER_URI;
	@Value("${wsUser}") private String wsUser;
	@Value("${wsPassword}") private String wsPassword;
	@Value("${nmeaCloudDir}") private String nmeaCloudDir;
	@Value("${nmeaBacklogQueueSize}") private int nmeaBacklogQueueSize;
	@Value("${nmeaSaveCurrentPositionInterval}") private int nmeaSaveCurrentPositionInterval;

	public NMEASubscriber( ArrayBlockingQueue<Object> queue ) {
		logger.info("Initialisation of NMEA subscriber...");
		this.queue = queue;
		this.parametersMap = new LinkedMultiValueMap<>();
	}

    @Override
	public void run() {
		String WS_URI = SERVER_URI+WSConstants.WS_NMEA;
    	logger.info("NMEA subscriber is running");

    	Map<String,String> pair = new LinkedHashMap<>();
    	Queue<Map<String, String>> backlogQueue = new LinkedList<>();

    	String nmeaStr = null;
    	boolean wsFlag = true;
    	nmeaFileUtilities = (NMEAFileUtilities)applicationContext.getBean("nmeaFileUtilities", new Object[]{nmeaCloudDir + File.separator + DIR_BACKLOG});

    	nmeaData.setTZData();
    	country = (Country) nmeaFileUtilities.readCurrentCountry();

        try {
            // First we need to create a new track - send a request to /rest/nmea/getTrack and obtain a new trackId.
            // get a trackId
            NMEAProcessor.trackId.set(nmeaMonitor.getTrack( WS_URI ));
            logger.info("TrackID: " + NMEAProcessor.trackId.get());
        } catch(  TrackServiceException e ) {
            logger.info("/getTrack error: " + e.getMessage() + ", will try to reach WS in background" );
        }

        // start monitor to retrieve new track
        // and periodically check connection
        Thread monitor = new Thread(nmeaMonitor);
        monitor.start();

        try {

            TrackPoint trackPoint = null;
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            NMEACurrentPositionWriter currentPositionWriter = (NMEACurrentPositionWriter)applicationContext.getBean("nmeaCurrentPositionWriter");
            scheduler.scheduleWithFixedDelay(currentPositionWriter, 0, nmeaSaveCurrentPositionInterval, TimeUnit.SECONDS);
            // working with a queue
			do {
    			while(!queue.isEmpty()) {
    			    Object obj = queue.take();
    				if( obj instanceof String ) {
    				    nmeaStr = (String) obj;

				        try{
				            logger.info("NMEA sentence from queue: " + nmeaStr);
				            nmeaSentence = new NMEASentence(nmeaStr);
				            if( nmeaStr.startsWith(GPGGA) ) {
				                trackPoint = new TrackPoint();
				            } else if( trackPoint == null ) {
			                    continue;
				            }
				        } catch (InvalidContentException | StartNotFoundException | IllegalNMEACharacterException e) {
				            logger.error("Invalid NMEA sentence: " + nmeaStr + ", ignoring. Error message: " + e.getMessage());
                            continue;
                        }

   						// composing GPS data from RMC & GGA sentences
   						nmeaUtilities.composePair( trackPoint, nmeaSentence );
   						pair.put(nmeaSentence.getType(), nmeaStr);
   						// send only if GPS container is full
   						if( nmeaUtilities.isComposed( trackPoint ) ) {
   						    trackPoint.setMiscMetaData( nmeaData.findNearestTZ(trackPoint) );
   						    trackPoint.setTrackId( NMEAProcessor.trackId.get() );
					        checkIfCountryChanged(trackPoint);

					        parametersMap.add("nmeaStr", gson.toJson(new NmeaSentenceDto[]{toNmeaSentenceDto( trackPoint )}));
    						if( wsFlag ) {
		    				    try {
		    				    	// send sentence to WS
			    					RESTUtilities.send(WS_URI, nmeaMonitor.getHeaders(), parametersMap);
		    				    } catch( TrackServiceException e ) { // push data to a queue in case of connection problems
		    				        pushToBacklog(backlogQueue,pair);
		    				    }
	    				    } else { // pushing to a queue in case of WS problems
	    				        pushToBacklog(backlogQueue,pair);
	    				    }

	    					// flushing backlog to the file
	    					if( backlogQueue.size() == nmeaBacklogQueueSize ) {
	    					    flushBacklog(backlogQueue);
	    					}
	    					// cleanup for the next GGA & RMC
	    				    pair = cleanUp();
	    				    currentPositionWriter.setTrackPoint(trackPoint);
    					}
    				}
    			}
    			if( queue.isEmpty() && !NMEAProcessor.bulkUploadFile.get().equals(EMPTY) ) { // bulkUploadFile is responsible for bulk upload and contains a track date
    				NMEAProcessor.keepSubscriberRunning.set(false);
    			}
    		} while( NMEAProcessor.keepSubscriberRunning.get() );
			flushBacklog(backlogQueue);
			scheduler.shutdown();
			logger.info("NMEA Subscriber stopped.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

	/**
	 * Backlog writer
	 * @param fileWriter
	 */
	private void flushBacklog( Queue<Map<String,String>> backlogQueue ) {
	    backlogQueue.forEach(pair->{
	        for (Object value : pair.values()) {
	            nmeaFileUtilities.write((String)value);
	        }
	    });
	    backlogQueue.clear();
	}

	/**
	 * Pushes pair of NMEA sentences into the queue
	 * @param pair
	 */
	private void pushToBacklog( Queue<Map<String,String>> backlogQueue, Map<String,String> pair ) {
	    backlogQueue.add(pair);
	}

	/**
	 * Total cleanup after send
	 */
	private Map<String,String> cleanUp() {
	    nmeaSentence = null;
	    parametersMap.clear();
        parametersMap = new LinkedMultiValueMap<String, String>();
	    return new LinkedHashMap<>();
	}

    private void checkIfCountryChanged( TrackPoint trackPoint ) {
        currentCountry = NMEAProcessor.countryFactory.get(((GeoTimeZoneId)trackPoint.getMiscMetaData()).getCountryId());
        if( !country.equals(currentCountry) ) {
            nmeaFileUtilities.writeCurrentCountry(currentCountry);
            nmeaService.reloadStaticTZData(currentCountry);
            nmeaData.setTZData();
            country = currentCountry;
        }
    }
}
