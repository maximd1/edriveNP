package com.ya0ne.np.misc;

import com.ya0ne.core.domain.dto.GeoTimeZoneId;
import com.ya0ne.core.domain.track.TrackPoint;

public interface TZDataService {
	/**
	 * Returns nearest time zone as set of IDs (GeoTimeZone+LocalTimeZone+Country)
	 * @param trkPt
	 * @return GeoTimeZoneId
	 */
	public GeoTimeZoneId findNearestTZ( TrackPoint trkPt );
    public void setTZData();
}
