/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.exifparser;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.lang.Rational;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;

/**
 * Ingest module to parse image Exif metadata. Currently only supports JPEG files.
 * Ingests an image file and, if available, adds it's date, latitude, longitude,
 * altitude, device model, and device make to a blackboard artifact.
 */
public final class ExifParserFileIngestModule extends IngestModuleAbstractFile {

    private IngestServices services;
    
    final public static String MODULE_NAME = "Exif Parser";
    final public static String MODULE_VERSION = "1.0";
    
    private static final int readHeaderSize = 2;
    private final byte[] fileHeaderBuffer = new byte[readHeaderSize];
    private static final char JPEG_SIGNATURE_BE = 0xFFD8;
    
    private static final Logger logger = Logger.getLogger(ExifParserFileIngestModule.class.getName());
    private static ExifParserFileIngestModule defaultInstance = null;
    private static int messageId = 0;
    
    private int filesProcessed = 0;

    //file ingest modules require a private constructor
    //to ensure singleton instances
    private ExifParserFileIngestModule() {
    }

    //default instance used for module registration
    public static synchronized ExifParserFileIngestModule getDefault() {
        if (defaultInstance == null) {
            defaultInstance = new ExifParserFileIngestModule();
        }
        return defaultInstance;
    }

    @Override
    public IngestModuleAbstractFile.ProcessResult process(PipelineContext<IngestModuleAbstractFile>pipelineContext, AbstractFile content) {
        
        //skip unalloc
        if(content.getType().equals(TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)) {
            return IngestModuleAbstractFile.ProcessResult.OK;
        }
        
        //skip unsupported
        if (! parsableFormat(content)) {
            return IngestModuleAbstractFile.ProcessResult.OK;
        }
        
        return processFile(content);
    }
    
    public IngestModuleAbstractFile.ProcessResult processFile(AbstractFile f) {
        InputStream in = null;
        BufferedInputStream bin = null;
        
        try {
            in = new ReadContentInputStream(f);
            bin = new BufferedInputStream(in);
            
            Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();
            Metadata metadata = ImageMetadataReader.readMetadata(bin, true);
            
            // Date
            ExifSubIFDDirectory exifDir = metadata.getDirectory(ExifSubIFDDirectory.class);
            if(exifDir != null) {
                Date date = exifDir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                if(date != null) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), MODULE_NAME, date.getTime()/1000));
                }
            }
            
            // GPS Stuff
            GpsDirectory gpsDir = metadata.getDirectory(GpsDirectory.class);
            
            if(gpsDir != null) {
                Rational altitude = gpsDir.getRational(GpsDirectory.TAG_GPS_ALTITUDE);
                GeoLocation loc = gpsDir.getGeoLocation();
                if(loc!=null) {
                    double latitude = loc.getLatitude();
                    double longitude = loc.getLongitude();
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID(), MODULE_NAME, latitude));
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID(), MODULE_NAME, longitude));
                }
                if(altitude!=null) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE.getTypeID(), MODULE_NAME, altitude.doubleValue()));
                }
            }
           
            
            // Device info
            ExifIFD0Directory devDir = metadata.getDirectory(ExifIFD0Directory.class);
            if(devDir != null) {
                String model = devDir.getString(ExifIFD0Directory.TAG_MODEL);
                String make = devDir.getString(ExifIFD0Directory.TAG_MAKE);
                
                if(model!=null && !model.isEmpty()) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID(), MODULE_NAME, model));
                } if(make!=null && !make.isEmpty()) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE.getTypeID(), MODULE_NAME, make));
                }
            }
            
            // Add the attributes, if there are any, to a new artifact
            if(!attributes.isEmpty()) {
                BlackboardArtifact bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF);
                bba.addAttributes(attributes);
                ++filesProcessed;
                if (filesProcessed %100 == 0) {
                    services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF));
                }
            }
            
            return IngestModuleAbstractFile.ProcessResult.OK;
            
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to create blackboard artifact for exif metadata (" + ex.getLocalizedMessage() + ").");
        } catch (ImageProcessingException ex) {
            logger.log(Level.WARNING, "Failed to process the image file: " + f.getName() + "(" + ex.getLocalizedMessage() + ")");
        } catch (IOException ex) {
            logger.log(Level.WARNING, "IOException when parsing image file: " +  f.getName(), ex);
        } finally {
            try {
                if(in!=null) { in.close(); }
                if(bin!=null) { bin.close(); }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed to close InputStream.", ex);
            }
        }
        
        // If we got here, there was an error
        return IngestModuleAbstractFile.ProcessResult.ERROR;
    }
    
    /**
     * Checks if should try to attempt to extract exif.
     * Currently checks if JPEG image, first by extension, then by signature (if extension fails)
     * @param f file to be checked 
     * @return true if to be processed 
     */
    private boolean parsableFormat(AbstractFile f) {
        // Get the name, extension
        String name = f.getName();
        int dotIndex = name.lastIndexOf(".");
        if (dotIndex == -1) {
            return false;
        }
        String ext = name.substring(dotIndex).toLowerCase();
        if(ext.equals(".jpeg") || ext.equals(".jpg")) {
            return true;
        }
        
        return isJpegFileHeader(f);
        
    }
    
        /**
     * Check if is jpeg file based on header
     * @param file
     * @return true if jpeg file, false otherwise
     */
    private boolean isJpegFileHeader(AbstractFile file) {
        if (file.getSize() < readHeaderSize) {
            return false;
        }
        
        int bytesRead = 0;
        try {
            bytesRead = file.read(fileHeaderBuffer, 0, readHeaderSize);
        } catch (TskCoreException ex) {
            //ignore if can't read the first few bytes, not a JPEG
            return false;
        }
        if (bytesRead != readHeaderSize) {
            return false;
        }
        
        ByteBuffer bytes = ByteBuffer.wrap(fileHeaderBuffer);
        char signature = bytes.getChar();
        
        return signature == JPEG_SIGNATURE_BE;
        
    }
    

    @Override
    public void complete() {
        logger.log(Level.INFO, "completed exif parsing " + this.toString());

        if (filesProcessed > 0) {
            //send the final new data event
            services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF));
        }
        //module specific cleanup due to completion here
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }

    
    @Override
    public String getName() {
        return "Exif Image Parser";
    }
    
    @Override
    public String getDescription() {
        return "Ingests .jpg and .jpeg files and retrieves their metadata.";
    }

    @Override
    public void init(IngestModuleInit initContext) {
        services = IngestServices.getDefault();
        logger.log(Level.INFO, "init() " + this.toString());
        
        filesProcessed = 0;
    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");

        //module specific cleanup due to interruption here
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
}