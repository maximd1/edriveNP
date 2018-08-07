package com.ya0ne.np.NMEA.utils;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ya0ne.core.domain.track.TrackPoint;
import com.ya0ne.np.NMEAProcessor;

@Component("nmeaCurrentPositionWriter")
public class NMEACurrentPositionWriter implements Runnable {
    private static Logger logger = Logger.getLogger(NMEACurrentPositionWriter.class);

    @Autowired
    private NMEAFileUtilities fileUtilities;

    private TrackPoint trackPoint;

    public NMEACurrentPositionWriter() {
    }

    public NMEACurrentPositionWriter(TrackPoint trackPoint) {
        this.trackPoint = trackPoint;
    }

    @Override
    public void run() {
        if( NMEAProcessor.keepSubscriberRunning.get() && trackPoint != null ) {
            logger.info("Current position: " + trackPoint.toString());
            fileUtilities.writeCurrentPosition(trackPoint);
        }
    }

    public void setTrackPoint(TrackPoint trackPoint) {
        this.trackPoint = trackPoint;
    }
}
