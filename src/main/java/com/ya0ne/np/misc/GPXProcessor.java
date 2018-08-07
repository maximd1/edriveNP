package com.ya0ne.np.misc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;

import javax.annotation.PostConstruct;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.lang.time.DateUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.google.gson.Gson;
import com.ya0ne.core.constants.WSConstants;
import com.ya0ne.core.domain.converters.GPXConverter;
import com.ya0ne.core.domain.track.Track;
import com.ya0ne.core.domain.track.TrackPoint;
import com.ya0ne.core.exceptions.nmea.TrackServiceException;
import com.ya0ne.core.generated.GpxType;
import com.ya0ne.core.generated.NmeaSentenceDto;
import com.ya0ne.core.generated.RteType;
import com.ya0ne.core.generated.TrkType;
import com.ya0ne.core.generated.TrksegType;
import com.ya0ne.core.generated.WptType;
import com.ya0ne.core.utilities.CommonUtilities;
import com.ya0ne.core.utilities.DateUtilities;
import com.ya0ne.core.utilities.context.ApplicationContextProvider;
import com.ya0ne.core.utilities.http.HttpUtilities;
import com.ya0ne.core.utilities.http.rest.RESTUtilities;
import com.ya0ne.np.NMEAProcessor;
import com.ya0ne.np.NMEA.NMEAMonitor;

import static com.ya0ne.core.constants.Constants.gpxCreator;
import static com.ya0ne.core.constants.Constants.gpxVersion;
import static com.ya0ne.core.constants.Constants.gpxDefaultNamespace;
import static com.ya0ne.core.constants.Constants.fileGpx;

import static com.ya0ne.core.constants.WSConstants.GPGGA;
import static com.ya0ne.core.constants.WSConstants.GPRMC;
import static com.ya0ne.core.domain.converters.GPXConverter.toNmeaSentenceDto;
import static com.ya0ne.core.utilities.DateUtilities.toDate;

@Component("gpxProcessor")
@Scope("prototype")
public class GPXProcessor {
	private static Logger logger = Logger.getLogger(GPXProcessor.class);

	private ApplicationContext context;
	private File gpxFile;

	@Value("${car}") private String car;
	@Value("#{systemProperties['inputGPX']}") private String inputGPX;
	@Value("${wsURL}") private String SERVER_URI;
	@Value("${wsUser}") private String wsUser;
	@Value("${wsPassword}") private String wsPassword;

	@Autowired private NMEAMonitor nmeaMonitor;
	//private SimpleDateFormat df = new SimpleDateFormat(WSConstants.NMEA_DATE_FORMAT);
	private Track track;
    private MultiValueMap<String, String> parametersMap = new LinkedMultiValueMap<String, String>();
    private Gson gson = new Gson();

	public GPXProcessor() {
	}

	public GPXProcessor( String inputFile ) {
		this.gpxFile = new File(inputFile);
	}

	@Autowired ApplicationContextProvider contextProvider;
	@Autowired TZDataService tzDataService;

	/**
	 * Stub for TrackServiceImpl
	 * @return
	 */
	public Track createTrack() {
	    return createTrack( -1L, DateUtilities.today() );
	}

	/**
	 * Parses GPX input and creates tracks' structure
	 * @return Track
	 */
  	public Track createTrack( long trackId, Timestamp date ) {
  	    try( FileInputStream fis = new FileInputStream(gpxFile) ) {
  	        JAXBContext jaxbContext = JAXBContext.newInstance(GpxType.class);
  	        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
  	        JAXBElement<GpxType> root = jaxbUnmarshaller.unmarshal(new StreamSource(fis), GpxType.class);
  	        GpxType gpx = root.getValue();
  	        fis.close(); // close to avoid potential resource leak
  	        Track track = context.getBean(Track.class);
  	        track.setId(trackId);

  	        // We need to check all types of tracks: WptType, RteType, TrkType.
  	        // Then, if anyone is not empty, we will work with it
	        List<WptType> wptList = gpx.getWpt();
	        List<RteType> rteList = gpx.getRte();
	        List<TrkType> trkList = gpx.getTrk();

	        if( wptList.size() > 0 ) {
	            // work with wpt
	            if( wptList.get(0).getTime() != null ) {
	                setTrackDate(track,DateUtilities.toTimestamp(wptList.get(0).getTime()));
	            } else {
	                setTrackDate(track,date);
	            }
	            track = addWpt(track, wptList);
	        } else if( rteList.size() > 0 ) {
	            // work with rte
                if( rteList.get(0).getRtept().get(0).getTime() != null ) {
                    setTrackDate(track,DateUtilities.toTimestamp(rteList.get(0).getRtept().get(0).getTime()));
                } else {
                    setTrackDate(track,date);
                }
	            for( Iterator<RteType> it1 = rteList.iterator(); it1.hasNext(); ) {
	                wptList = it1.next().getRtept();
	                track = addWpt(track, wptList);
	            }
	        } else if( trkList.size() > 0 ) {
	            if( trkList.get(0).getTrkseg().get(0).getTrkpt().get(0).getTime() != null ) {
	                setTrackDate(track,DateUtilities.toTimestamp(trkList.get(0).getTrkseg().get(0).getTrkpt().get(0).getTime()));
	            } else {
	                setTrackDate(track,date);
	            }
	            for( Iterator<TrkType> it1 = trkList.iterator(); it1.hasNext(); ) {
	                List<TrksegType> segList = it1.next().getTrkseg();
	                for( Iterator<TrksegType> it2 = segList.iterator(); it2.hasNext(); ) {
	                    wptList = it2.next().getTrkpt();
	                    track = addWpt(track, wptList);
	                }
	            }
	        }
	        track.normalize();
			logger.info("Track size: " + track.getTrack().size());
			return track;
	    } catch (ParseException | JAXBException | IOException e ) {
	    	e.printStackTrace();
	    }
		return null;
  	}

  	/**
  	 * Exports Track to GPX format
  	 * @param track
  	 * @return XML with track's data
  	 */
  	public byte[] exportTrack( Track track ) {
  	    JAXBContext jaxbContext;
  	    GpxType gpx = new GpxType();
  	    gpx.setCreator(gpxCreator);
  	    gpx.setVersion(gpxVersion);
  	    gpx.getTrk().add(GPXConverter.toGpx(track));
        try( ByteArrayOutputStream out = new ByteArrayOutputStream() ) {
            XMLOutputFactory xof =  XMLOutputFactory.newInstance();
            XMLStreamWriter writer = xof.createXMLStreamWriter(out);
            writer.setNamespaceContext(new NamespaceContext() {
                public Iterator<?> getPrefixes(String namespaceURI) {
                    return null;
                }

                public String getPrefix(String namespaceURI) {
                    return "";
                }

                public String getNamespaceURI(String prefix) {
                    return null;
                }
            });
            writer.setDefaultNamespace(gpxDefaultNamespace);

            jaxbContext = JAXBContext.newInstance(GpxType.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            jaxbMarshaller.setProperty(Marshaller.JAXB_ENCODING, StandardCharsets.UTF_8.displayName());
            jaxbMarshaller.marshal( new JAXBElement<GpxType>(new QName(gpxDefaultNamespace, fileGpx), GpxType.class, gpx ), writer);
            return out.toByteArray();
        } catch (JAXBException | IOException | XMLStreamException e) {
            e.printStackTrace();
        }
        return null;
  	}

    /**
    * Exports Track to NMEA format
    * @param track
    * @return file with NMEA data
    */
   public byte[] exportTrackToNmea( Track track ) {
       StringBuffer outString = new StringBuffer();
       track.getTrack().forEach(trackPoint -> {
           // GGA
           StringBuffer ggaString = new StringBuffer();
           StringBuffer rmcString = new StringBuffer();
           String latitude = CommonUtilities.convertDecimalLatitudeToNMEA(trackPoint.getLatitude());
           String longitude = CommonUtilities.convertDecimalLongitudeToNMEA(trackPoint.getLongitude());
           ggaString.append(GPGGA).
               append(",").
               append(DateUtilities.nmeaTime(trackPoint.getTimestamp()) + ",").
               append(latitude.toString() + "," + (CommonUtilities.isNegative(trackPoint.getLatitude()) ? "S" : "N") + ",").
               append(longitude.toString() + "," + (CommonUtilities.isNegative(trackPoint.getLongitude()) ? "W" : "E") + ",").
               append("1,10,0.8,").
               append(trackPoint.getAltitude().toString() + ",M,").
               append("27.8,M,,0000*").
               append(CommonUtilities.getNMEASum(ggaString.toString())).
               append(System.getProperty("line.separator"));
           // RMC
           rmcString.append(GPRMC).
               append(",").
               append(DateUtilities.nmeaTime(trackPoint.getTimestamp()) + ",A,").
               append(latitude.toString() + "," + (CommonUtilities.isNegative(trackPoint.getLatitude()) ? "S" : "N") + ",").
               append(longitude.toString() + "," + (CommonUtilities.isNegative(trackPoint.getLongitude()) ? "W" : "E") + ",").
               append(trackPoint.getSpeed().toString() + ",1.0,").
               append(DateUtilities.nmeaDate(trackPoint.getTimestamp()) + ",,,A*").
               append(CommonUtilities.getNMEASum(rmcString.toString())).
               append(System.getProperty("line.separator"));
           outString.append(ggaString).append(rmcString);
       });
       return String.valueOf(outString).getBytes();
   }

  	/**
  	 * Adds waypoint to the track 
  	 * @param trk
  	 * @param wptList
  	 * @throws ParseException
  	 */
    private Track addWpt(Track track, List<WptType> wptList)
            throws ParseException {
        Object[] tpd = new Object[7]; // trackPointData
        Track trk = track;
        long trackId = trk.getId();
        int i = 0;
        Calendar now = Calendar.getInstance();

        for( Iterator<WptType> it = wptList.iterator(); it.hasNext(); ) {
            WptType wpt = it.next();
            tpd[0] = wpt.getLat().doubleValue();
            tpd[1] = wpt.getLon().doubleValue();
            tpd[2] = wpt.getEle() == null ? 0.0 : wpt.getEle().doubleValue(); // altitude
            tpd[3] = 0.0; // speed
            Date tpDate = null;
            if( wpt.getTime() == null ) {
                now.add(Calendar.SECOND, i);
                tpDate = now.getTime();
            } else {
                tpDate = toDate(wpt.getTime());
            }
            long millis = DateUtils.truncate(tpDate, Calendar.MILLISECOND).getTime();
            tpd[4] = new Timestamp(millis);
            tpd[5] = null;
            tpd[6] = trackId;
            TrackPoint trkpt = (TrackPoint)context.getBean("trackPoint",tpd);
            trkpt.setMiscMetaData(tzDataService.findNearestTZ(trkpt));
            trkpt.setTrackId(trackId);
            trk.addTrackPoint(trkpt);
            i++;
        }
        return trk;
    }

    /**
     * This method saves GPX file, prepared from other NAVI programs, to database
     */
    public int saveGPX() {
        //return trackDao.saveTrack(track);
        ApplicationContext context = contextProvider.getApplicationContext();
        GPXProcessor trackGpx = (GPXProcessor)context.getBean("gpxProcessor", new Object[]{this.inputGPX});
        logger.info("Working with " + this.inputGPX);

        String WS_URI = SERVER_URI+WSConstants.WS_NMEA;

        try {
            // First we need to create a new track - send a request to /rest/nmea/getTrack and obtain a new trackId.
            HttpHeaders headers = HttpUtilities.initHeaders(wsUser,wsPassword);
            try {
                // get a trackId
                NMEAProcessor.trackId.set(nmeaMonitor.getTrack( WS_URI ));
            } catch(  TrackServiceException e ) {
                logger.info("/getTrack error: " + e.getMessage() );
            }
            if( NMEAProcessor.trackId.get() == null ) {
                logger.error("Track has not been created, exiting.");
                System.exit(-1);
            } else {
                logger.info("TrackID: " + NMEAProcessor.trackId.get());
            }

            // then create a track from XML
            track = trackGpx.createTrack( NMEAProcessor.trackId.get(), DateUtilities.today() );
            NmeaSentenceDto[] trk = new NmeaSentenceDto[track.getTrack().size()];

            // working with a track
            IntStream.range(0, track.getTrack().size()).forEach(idx -> { trk[idx] = toNmeaSentenceDto(track.getTrack().get(idx)); } );

            parametersMap.add("nmeaStr", gson.toJson(trk));
            RESTUtilities.send(WS_URI, headers, parametersMap);
            logger.info(trk.length + " track points were saved.");
            //parametersMap = new LinkedMultiValueMap<String, String>();
        } catch ( Exception e) {
            e.printStackTrace();
        }
        return WSConstants.WS_OK;
    }

  	/**
  	 * Sets tracks' date
  	 * @param trk
  	 * @param date
  	 */
  	private void setTrackDate( Track track, Timestamp date ) {
  	    track.setTrackDate(date);
  	}

	public void setGpxFile(File gpxFile) {
		this.gpxFile = gpxFile;
	}

	@PostConstruct
	public void init() {
		this.context = contextProvider.getApplicationContext();
	}
}
