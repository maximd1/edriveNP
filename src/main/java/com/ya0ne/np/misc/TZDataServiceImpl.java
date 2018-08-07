package com.ya0ne.np.misc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ya0ne.core.domain.dto.GeoTimeZoneId;
import com.ya0ne.core.domain.track.TrackPoint;
import com.ya0ne.core.geo.GeoProcessor;
import com.ya0ne.core.kdtree.KDTree;
import com.ya0ne.np.NMEAProcessor;

@Component("nmeaData")
public class TZDataServiceImpl implements TZDataService {
	@Autowired private GeoProcessor<?, ?> geoProcessor;

	public TZDataServiceImpl() {
	}

    /**
     * Searches tree for the nearest way point
     * @param coordinates
     * @return TimeZone
     */
    public GeoTimeZoneId findNearestTZ( TrackPoint trkPt ) {
        if( NMEAProcessor.tzData != null ) {
            return geoProcessor.findNearestTZ(trkPt); // getting LocalTimeZone for coordinate
        } else {
            return new GeoTimeZoneId();
        }
    }

    public void setTZData() {
        if( NMEAProcessor.tzData != null ) {
            geoProcessor.setTree(new KDTree());
            geoProcessor.createTZTree(NMEAProcessor.tzData.getMap());
        }
    }
}
