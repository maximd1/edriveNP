package com.ya0ne.np.NMEA;

import static com.ya0ne.core.constants.Constants.EMPTY;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.ya0ne.core.constants.WSConstants;
import com.ya0ne.core.exceptions.nmea.TrackServiceException;
import com.ya0ne.core.utilities.DateUtilities;
import com.ya0ne.core.utilities.http.HttpUtilities;
import com.ya0ne.core.utilities.http.rest.RESTUtilities;
import com.ya0ne.np.NMEAProcessor;

@Component
public class NMEAMonitor implements Runnable {
    private static Logger logger = Logger.getLogger(NMEAMonitor.class);

    @Autowired private RestTemplate restTemplate;

    @Value("${wsURL}") String SERVER_URI;
    @Value("${wsUser}") String wsUser;
    @Value("${wsPassword}") String wsPassword;
    @Value("${car}") String car;
    @Value("${nmeaConnectionRetryTimeout}") int nmeaConnectionRetryTimeout;

    private HttpHeaders headers;

    @Override
    public void run() {
        String WS_URI = SERVER_URI+WSConstants.WS_NMEA;
        logger.info("NMEA monitor is running");

        try {
            while( NMEAProcessor.trackId.get() == null && NMEAProcessor.keepSubscriberRunning.get() ) {
                try {
                    // get a trackId
                    NMEAProcessor.trackId.set(getTrack( WS_URI ));
                } catch(  TrackServiceException e ) {
                    logger.info("/getTrack error: " + e.getMessage() + ", will retry in " + nmeaConnectionRetryTimeout + " msecs." );
                }
                Thread.sleep(nmeaConnectionRetryTimeout);
            }
        } catch( InterruptedException e ) {
            e.printStackTrace();
        }
        logger.info("NMEA monitor stopped");
    }

    public Long getTrack( String URI ) throws TrackServiceException {
        HttpEntity<MultiValueMap<String, String>> request = prepareRequest();
        return getTrack( URI, request );
    }
    
    /**
     * Sends a request to WS and receives trackId.
     * @param request
     * @param URI
     * @throws BadCredentialsException
     * @throws ResourceAccessException
     * @throws HttpClientErrorException
     */
    private Long getTrack( String URI, HttpEntity<MultiValueMap<String, String>> request ) throws TrackServiceException {
        Long trackId = RESTUtilities.getTrack( request, URI, restTemplate ); 
        if( trackId == null ) {
            throw new TrackServiceException( "Track error" );
        }
        logger.info("TrackID: " + trackId );
        return trackId;
    }

    private HttpEntity<MultiValueMap<String, String>> prepareRequest() {
        headers = HttpUtilities.initHeaders(wsUser,wsPassword);
        HttpEntity<MultiValueMap<String, String>> request;

        MultiValueMap<String, String> parametersMap = new LinkedMultiValueMap<String, String>();
        parametersMap.add("car", this.car);
        parametersMap.add("trackDate", (NMEAProcessor.bulkUploadFile.get().equals(EMPTY) ? DateUtilities.trackDateTime(getTodayWithUTC()) : NMEAProcessor.bulkUploadFile.get()) );
        request = new HttpEntity<MultiValueMap<String, String>>(parametersMap, headers);
        return request;
    }

    private Timestamp getTodayWithUTC() {
        return Timestamp.valueOf(LocalDateTime.now(Clock.systemUTC()));
    }

    public HttpHeaders getHeaders() {
        return this.headers;
    }
}
