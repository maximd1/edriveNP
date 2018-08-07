package com.ya0ne.np.NMEA.utils;

import static com.ya0ne.core.constants.Constants.FILE_CURRENT_COUNTRY;
import static com.ya0ne.core.constants.Constants.FILE_CURRENT_POSITION;
import static com.ya0ne.core.constants.Constants.fileNmea;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.ya0ne.core.domain.Country;
import com.ya0ne.core.domain.track.TrackPoint;
import com.ya0ne.core.utilities.CommonUtilities;
import com.ya0ne.core.utilities.DateUtilities;

@Component("nmeaFileUtilities")
@Scope("prototype")
public class NMEAFileUtilities {
    private static Logger logger = Logger.getLogger(NMEAFileUtilities.class);

    @Value("${nmeaCloudDataDir}")
    private String cloudDataLocation;

    private File currentCountryFile;
    private File currentPositionFile;
    private BufferedWriter writer = null;
    private File logFile = null;
    private String timeLog = null;

    public NMEAFileUtilities() {
    }

    public NMEAFileUtilities( String nmeaBacklogDir ) {
        this();
        timeLog = DateTimeFormatter.ofPattern(DateUtilities.backlogFilenameFormat).format(LocalDateTime.now(Clock.systemUTC()));
        logFile = new File( nmeaBacklogDir + File.separator + timeLog + "." + fileNmea);
        try {
            logger.info("Output file: " + logFile.getCanonicalPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Write nmea string to a file in case of WS error
     * @param nmeaStr
     */
    public void write( String nmeaStr ) {
        try {
            open();
            writer.append(nmeaStr);
            writer.newLine();
            writer.flush();
            close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getFile() {
        try {
            return logFile.getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void open() {
        try {
            writer = new BufferedWriter(new FileWriter(logFile,true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads current country saved on the previous session
     * @return Country
     */
    public Country readCurrentCountry() {
        Country currentCountry;
        try( FileInputStream fis = new FileInputStream(currentCountryFile) ) {
            currentCountry = (Country)CommonUtilities.deserialize(IOUtils.toByteArray(fis));
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        return currentCountry;
    }

    /**
     * Writes current country ID before close of program
     */
    public void writeCurrentCountry( Country currentCountry ) {
        writeFile( currentCountryFile, currentCountry );
    }

    /**
     * Writes current position to a file
     * @param trackPoint
     */
    public void writeCurrentPosition( TrackPoint trackPoint ) {
        writeFile( currentPositionFile, trackPoint );
    }

    private void writeFile( File fileToWrite, Object objectToWrite ) {
        try {
            FileUtils.writeByteArrayToFile(fileToWrite, CommonUtilities.serialize(objectToWrite));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setCurrentCountryFile(File currentCountryFile) {
        this.currentCountryFile = currentCountryFile;
    }

    public void setCurrentPositionFile(File currentPositionFile) {
        this.currentPositionFile = currentPositionFile;
    }

    public String getCloudLocation() {
        return this.cloudDataLocation;
    }

    @PostConstruct
    public void init() {
        setCurrentCountryFile(new File(cloudDataLocation + File.separator + FILE_CURRENT_COUNTRY));
        setCurrentPositionFile(new File(cloudDataLocation + File.separator + FILE_CURRENT_POSITION));
    }
}
