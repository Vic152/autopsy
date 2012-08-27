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
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.ingest.IngestManagerProxy;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestServiceAbstract;
import org.sleuthkit.autopsy.ingest.IngestServiceAbstractFile;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;

/**
 * Ingest module to parse image Exif metadata. Currently only supports JPEG files.
 * Ingests an image file and, if available, adds it's date, latitude, longitude,
 * altitude, device model, and device make to a blackboard artifact.
 */
public final class ExifParserFileIngestService implements IngestServiceAbstractFile {

    final String MODULE_NAME = "Exif Parser";
    private static final Logger logger = Logger.getLogger(ExifParserFileIngestService.class.getName());
    private static ExifParserFileIngestService defaultInstance = null;
    private IngestManagerProxy managerProxy;
    private static int messageId = 0;

    //file ingest services require a private constructor
    //to ensure singleton instances
    private ExifParserFileIngestService() {
    }

    //default instance used for service registration
    public static synchronized ExifParserFileIngestService getDefault() {
        if (defaultInstance == null) {
            defaultInstance = new ExifParserFileIngestService();
        }
        return defaultInstance;
    }

    @Override
    public IngestServiceAbstractFile.ProcessResult process(AbstractFile content) {
        if(content.getType().equals(TSK_DB_FILES_TYPE_ENUM.FS)) {
            FsContent fsContent = (FsContent) content;
            if(fsContent.isFile()) {
                if(parsableFormat(fsContent)) {
                    return processFile(fsContent);
                }
            }
        }
        
        return IngestServiceAbstractFile.ProcessResult.UNKNOWN;
    }
    
    public IngestServiceAbstractFile.ProcessResult processFile(FsContent f) {
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
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), MODULE_NAME, "", date.toString()));
                }
            }
            
            // GPS Stuff
            GpsDirectory gpsDir = metadata.getDirectory(GpsDirectory.class);
            if(gpsDir != null) {
                String latitude = gpsDir.getString(GpsDirectory.TAG_GPS_LATITUDE);
                String latRef = gpsDir.getString(GpsDirectory.TAG_GPS_LATITUDE_REF);
                String longitude = gpsDir.getString(GpsDirectory.TAG_GPS_LONGITUDE);
                String longRef = gpsDir.getString(GpsDirectory.TAG_GPS_LONGITUDE_REF);
                String altitude = gpsDir.getString(GpsDirectory.TAG_GPS_ALTITUDE);
                
                if(latitude!= null && latRef!=null) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID(), MODULE_NAME, "", latitude + " " +  latRef));
                } if(longitude!=null && longRef!=null) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID(), MODULE_NAME, "", longitude + " " + longRef));
                } if(altitude!=null) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE.getTypeID(), MODULE_NAME, "", altitude));
                }
            }
            
            // Device info
            ExifIFD0Directory devDir = metadata.getDirectory(ExifIFD0Directory.class);
            if(devDir != null) {
                String model = devDir.getString(ExifIFD0Directory.TAG_MODEL);
                String make = devDir.getString(ExifIFD0Directory.TAG_MAKE);
                
                if(model!=null) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID(), MODULE_NAME, "", model));
                } if(make!=null) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE.getTypeID(), MODULE_NAME, "", make));
                }
            }
            
            // Add the attributes, if there are any, to a new artifact
            if(!attributes.isEmpty()) {
                BlackboardArtifact bba = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GEN_INFO);
                bba.addAttributes(attributes);
            }
            
            return IngestServiceAbstractFile.ProcessResult.OK;
            
        } catch (TskCoreException ex) {
            Logger.getLogger(ExifParserFileIngestService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ImageProcessingException ex) {
            logger.log(Level.WARNING, "Failed to process the image.", ex);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "IOException when parsing image file.", ex);
        } finally {
            try {
                if(in!=null) { in.close(); }
                if(bin!=null) { bin.close(); }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed to close InputStream.", ex);
            }
        }
        
        // If we got here, there was an error
        return IngestServiceAbstractFile.ProcessResult.ERROR;
    }
    
    private boolean parsableFormat(FsContent f) {
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
        
        return false;
    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "completed exif parsing " + this.toString());

        final IngestMessage msg = IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Complete");
        managerProxy.postMessage(msg);

        //service specific cleanup due to completion here
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
    public void init(IngestManagerProxy managerProxy) {
        logger.log(Level.INFO, "init() " + this.toString());
        this.managerProxy = managerProxy;

    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");
        managerProxy.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Stopped"));

        //service specific cleanup due to interruption here
    }

    @Override
    public IngestServiceAbstract.ServiceType getType() {
        return IngestServiceAbstract.ServiceType.AbstractFile;
    }

     @Override
    public boolean hasSimpleConfiguration() {
        return false;
    }
    
    @Override
    public boolean hasAdvancedConfiguration() {
        return false;
    }

    @Override
    public javax.swing.JPanel getSimpleConfiguration() {
        return null;
    }
    
    @Override
    public javax.swing.JPanel getAdvancedConfiguration() {
        return null;
    }
    
    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
    
    @Override
    public void saveAdvancedConfiguration() {
    }
    
    @Override
    public void saveSimpleConfiguration() {
    }
}