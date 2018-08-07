package com.ya0ne.np.service;

import static com.ya0ne.core.constants.Constants.DIR_TZDATA;
import static com.ya0ne.core.constants.Constants.FILE_BOUNDARIES_MAP;
import static com.ya0ne.core.constants.Constants.FILE_COUNTRIES_LIST;
import static com.ya0ne.core.constants.Constants.FILE_TIMEZONES;
import static com.ya0ne.core.constants.Constants.FILE_CURRENT_POSITION;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ya0ne.core.domain.Country;
import com.ya0ne.core.domain.dto.BoundariesData;
import com.ya0ne.core.domain.dto.GeoTimeZoneId;
import com.ya0ne.core.domain.dto.TZData;
import com.ya0ne.core.domain.track.TrackPoint;
import com.ya0ne.core.exceptions.LoadDataException;
import com.ya0ne.core.utilities.CommonUtilities;
import com.ya0ne.np.NMEAProcessor;
import com.ya0ne.np.NMEA.utils.NMEAFileUtilities;

@Service
public class DefaultNMEAService implements NMEAService {
    private static Logger logger = Logger.getLogger(DefaultNMEAService.class);

    private String dataDir;
    private static Map<TrackPoint, GeoTimeZoneId> tzMap = new HashMap<>();

    @Autowired private NMEAFileUtilities nmeaFileUtilities;

    @Override
    public void prepareStaticData() throws LoadDataException {
        logger.info("Deserialisation of mandatory static data");
        loadMandatoryData();
        logger.info("Deserialisation of countries");
        if( NMEAProcessor.boundariesData != null ) {
            Country currentCountry = nmeaFileUtilities.readCurrentCountry();
            reloadStaticTZData(currentCountry);
        }
    }

    /**
     * Loads mandatory static data (current country, position, boundaries, etc) into the processor 
     * @throws LoadDataException
     */
    @SuppressWarnings("unchecked")
    private void loadMandatoryData() throws LoadDataException {
        NMEAProcessor.countryFactory.putAll((Map<Long, Country>)deserialize(FILE_COUNTRIES_LIST));
        NMEAProcessor.timeZones.set((Map<Long,TimeZone>)deserialize(FILE_TIMEZONES));

        BoundariesData bd = new BoundariesData();
        bd.setBoundariesMap((Map<Country, Set<Country>>)deserialize(FILE_BOUNDARIES_MAP));
        NMEAProcessor.boundariesData = bd;

        try {
            NMEAProcessor.currentPosition.set((TrackPoint)deserialize(FILE_CURRENT_POSITION));
            NMEAProcessor.currentTimeZone.set(NMEAProcessor.currentPosition.get().getTimeZone());
        } catch( LoadDataException e ) {
            logger.warn("No current position found, using UTC");
            NMEAProcessor.currentPosition.set(setDefaultCurrentPosition());
            NMEAProcessor.currentTimeZone.set(TimeZone.getTimeZone(System.getProperty("user.timezone")));
        }
    }

    @Override
    public void reloadStaticTZData(Country currentCountry) {
        TZData tzData = new TZData();
        logger.info("Current country is: " + currentCountry.getIsoCode2());
        Set<Country> countriesSet = NMEAProcessor.boundariesData.getBoundariesMap().get(currentCountry);
        ExecutorService es = Executors.newCachedThreadPool();
        countriesSet.forEach(country->{
            if( country != null ) {
                es.execute(new Runnable() {
                    @Override
                    public void run() {
                        try( FileInputStream fis = new FileInputStream(dataDir + File.separator + DIR_TZDATA + File.separator + country.getIsoCode2() + ".tz") ) {
                            TZData tzd = (TZData)CommonUtilities.deserialize(IOUtils.toByteArray(fis));
                            tzMap.putAll( tzd.getMap() );
                            logger.info("Time zone data for " + country.getIsoCode2() + " loaded");
                        } catch (ClassNotFoundException | IOException e) {
                            logger.warn("Deserialization of data for " + country.getIsoCode2() + " failed - skipping. Error was: " + e.getMessage());
                        }
                    }
                });
            }
        });
        es.shutdown();
        try {
            es.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        tzData.setMap(tzMap);
        NMEAProcessor.tzData = tzData;
    }

    private Object deserialize( String file ) throws LoadDataException {
        String pathToFile = dataDir + File.separator + file;
        try (FileInputStream fin = new FileInputStream(pathToFile)) {
            return CommonUtilities.deserialize(IOUtils.toByteArray(fin));
        } catch( Exception e ) {
            throw new LoadDataException("No " + pathToFile + " loaded, exiting");
        }
    }

    /**
     * Sets default current position (usually, for the new car), with default UTC time zone
     * @return TrackPoint
     */
    private static TrackPoint setDefaultCurrentPosition() {
        TrackPoint trackPoint = new TrackPoint();
        trackPoint.setTimeZone(TimeZone.getTimeZone("UTC"));
        return trackPoint;
    }

    @PostConstruct
    public void init() {
        NMEAProcessor.nmeaFileUtilities = nmeaFileUtilities;
        dataDir = nmeaFileUtilities.getCloudLocation();
    }
}
