 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2013 Basis Technology Corp.
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.recentactivity;

//IO imports
import java.io.BufferedReader;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;

//Util Imports
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.Collection;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TSK Imports
import org.openide.modules.InstalledFileLocator;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.JLNK;
import org.sleuthkit.autopsy.coreutils.JLnkParser;
import org.sleuthkit.autopsy.coreutils.JLnkParserException;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestDataSourceWorkerController;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.autopsy.ingest.IngestModuleDataSource;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.datamodel.*;

public class ExtractIE extends Extract {
    private static final Logger logger = Logger.getLogger(ExtractIE.class.getName());
    private IngestServices services;
    
    //paths set in init()
    private String moduleTempResultsDir;
    private String PASCO_LIB_PATH;
    private String JAVA_PATH;
    
    final public static String MODULE_VERSION = "1.0";
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    
    private  ExecUtil execPasco;

    //hide public constructor to prevent from instantiation by ingest module loader
    ExtractIE() {
        moduleName = "Internet Explorer";
        moduleTempResultsDir = RAImageIngestModule.getRATempPath(Case.getCurrentCase(), "IE") + File.separator + "results";
        JAVA_PATH = PlatformUtil.getJavaPath();
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }


    @Override
    public void process(PipelineContext<IngestModuleDataSource>pipelineContext, Content dataSource, IngestDataSourceWorkerController controller) {
        dataFound = false;
        this.getBookmark(dataSource, controller);
        this.getCookie(dataSource, controller);
        this.getRecentDocuments(dataSource, controller);
        this.getHistory(dataSource, controller);
    }

    /**
     * Finds the files storing bookmarks and creates artifacts
     * @param dataSource
     * @param controller 
     */
    private void getBookmark(Content dataSource, IngestDataSourceWorkerController controller) {       
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> favoritesFiles = null;
        try {
            favoritesFiles = fileManager.findFiles(dataSource, "%.url", "Favorites");
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error fetching 'url' files for Internet Explorer bookmarks.", ex);
            this.addErrorMessage(this.getName() + ": Error getting Internet Explorer Bookmarks.");
            return;
        }

        if (favoritesFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any IE bookmark files.");
            return;
        }
        
        dataFound = true;
        for (AbstractFile fav : favoritesFiles) {
            if (fav.getSize() == 0) {
                continue;
            }
         
            if (controller.isCancelled()) {
                break;
            }
            
            String url = getURLFromIEBookmarkFile(fav);

            String name = fav.getName();
            Long datetime = fav.getCrtime();
            String Tempdate = datetime.toString();
            datetime = Long.valueOf(Tempdate);
            String domain = Util.extractDomain(url);

            Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", url));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TITLE.getTypeID(), "RecentActivity", name));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID(), "RecentActivity", datetime));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "Internet Explorer"));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", domain));
            this.addArtifact(ARTIFACT_TYPE.TSK_WEB_BOOKMARK, fav, bbattributes);
        }
        services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_BOOKMARK));
    }
    
    private String getURLFromIEBookmarkFile(AbstractFile fav) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new ReadContentInputStream(fav)));
        String line, url = "";
        try {
            while ((line = reader.readLine()) != null) {
                // The actual shortcut line we are interested in is of the
                // form URL=http://path/to/website
                if (line.startsWith("URL")) {
                    url = line.substring(line.indexOf("=") + 1);
                    break;
                }
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed to read from content: " + fav.getName(), ex);
            this.addErrorMessage(this.getName() + ": Error parsing IE bookmark File " + fav.getName());
        } catch (IndexOutOfBoundsException ex) {
            logger.log(Level.WARNING, "Failed while getting URL of IE bookmark. Unexpected format of the bookmark file: " + fav.getName(), ex);
            this.addErrorMessage(this.getName() + ": Error parsing IE bookmark File " + fav.getName());
        } finally {
            try {
                reader.close();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed to close reader.", ex);
            }
        }
        
        return url;
    }

    /**
     * Finds files that store cookies and adds artifacts for them. 
     * @param dataSource
     * @param controller 
     */
    private void getCookie(Content dataSource, IngestDataSourceWorkerController controller) { 
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> cookiesFiles = null;
        try {
            cookiesFiles = fileManager.findFiles(dataSource, "%.txt", "Cookies");
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error getting cookie files for IE");
            this.addErrorMessage(this.getName() + ": " + "Error getting Internet Explorer cookie files.");
            return;
        }

        if (cookiesFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any IE cookies files.");
            return;
        }
        
        dataFound = true;
        for (AbstractFile cookiesFile : cookiesFiles) {
            if (controller.isCancelled()) {
                break;
            }
            if (cookiesFile.getSize() == 0) {
                continue;
            }

            byte[] t = new byte[(int) cookiesFile.getSize()];
            try {
                final int bytesRead = cookiesFile.read(t, 0, cookiesFile.getSize());
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error reading bytes of Internet Explorer cookie.", ex);
                this.addErrorMessage(this.getName() + ": Error reading Internet Explorer cookie " + cookiesFile.getName());
                continue;
            }
            String cookieString = new String(t);
            String[] values = cookieString.split("\n");
            String url = values.length > 2 ? values[2] : "";
            String value = values.length > 1 ? values[1] : "";
            String name = values.length > 0 ? values[0] : "";
            Long datetime = cookiesFile.getCrtime();
            String tempDate = datetime.toString();
            datetime = Long.valueOf(tempDate);
            String domain = Util.extractDomain(url);

            Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", url));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", datetime));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), "RecentActivity", (name != null) ? name : ""));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID(), "RecentActivity", value));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "Internet Explorer"));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", domain));
            this.addArtifact(ARTIFACT_TYPE.TSK_WEB_COOKIE, cookiesFile, bbattributes);
        }
        services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_COOKIE));
    }

    /**
     * Find the documents that Windows stores about recent documents and make artifacts.
     * @param dataSource
     * @param controller 
     */
    private void getRecentDocuments(Content dataSource, IngestDataSourceWorkerController controller) {
        
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> recentFiles = null;
        try {
            recentFiles = fileManager.findFiles(dataSource, "%.lnk", "Recent");
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error searching for .lnk files.");
            this.addErrorMessage(this.getName() + ": Error getting lnk Files.");
            return;
        }

        if (recentFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any IE recent files.");
            return;
        }
        
        dataFound = true;
        for (AbstractFile recentFile : recentFiles) {
            if (controller.isCancelled()) {
                break;
            }
            
            if (recentFile.getSize() == 0) {
                continue;
            }
            JLNK lnk = null;
            JLnkParser lnkParser = new JLnkParser(new ReadContentInputStream(recentFile), (int) recentFile.getSize());
            try {
                lnk = lnkParser.parse();
            } catch (JLnkParserException e) {
                //TODO should throw a specific checked exception
                boolean unalloc = recentFile.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.UNALLOC) 
                        || recentFile.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC);
                if (unalloc == false) {
                    logger.log(Level.SEVERE, "Error lnk parsing the file to get recent files" + recentFile, e);
                    this.addErrorMessage(this.getName() + ": Error parsing Recent File " + recentFile.getName());
                }
                continue;
            }
           
            Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
            String path = lnk.getBestPath();
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH.getTypeID(), "RecentActivity", path));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID(), "RecentActivity", Util.findID(dataSource, path)));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(), "RecentActivity", recentFile.getCrtime()));
            this.addArtifact(ARTIFACT_TYPE.TSK_RECENT_OBJECT, recentFile, bbattributes);
        }
        services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_RECENT_OBJECT));
    }
            
    /**
     * Locates index.dat files, runs Pasco on them, and creates artifacts. 
     * @param dataSource
     * @param controller 
     */
    private void getHistory(Content dataSource, IngestDataSourceWorkerController controller) {
        logger.log(Level.INFO, "Pasco results path: " + moduleTempResultsDir);
        boolean foundHistory = false;

        final File pascoRoot = InstalledFileLocator.getDefault().locate("pasco2", ExtractIE.class.getPackage().getName(), false);
        if (pascoRoot == null) {
            this.addErrorMessage(this.getName() + ": Unable to get IE History: pasco not found");
            logger.log(Level.SEVERE, "Error finding pasco program ");
            return;
        } 
       
        final String pascoHome = pascoRoot.getAbsolutePath();
        logger.log(Level.INFO, "Pasco2 home: " + pascoHome);

        PASCO_LIB_PATH = pascoHome + File.separator + "pasco2.jar" + File.pathSeparator
                + pascoHome + File.separator + "*";

        File resultsDir = new File(moduleTempResultsDir);
        resultsDir.mkdirs();
     
        // get index.dat files
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> indexFiles = null;
        try {
            indexFiles = fileManager.findFiles(dataSource, "index.dat");
        } catch (TskCoreException ex) {
            this.addErrorMessage(this.getName() + ": Error getting Internet Explorer history files");
            logger.log(Level.WARNING, "Error fetching 'index.data' files for Internet Explorer history.");
            return;
        }

        if (indexFiles.isEmpty()) {
            String msg = "No InternetExplorer history files found.";
            logger.log(Level.INFO, msg);
            return;
        }
        
        dataFound = true;
        String temps;
        String indexFileName;
        for (AbstractFile indexFile : indexFiles) {
            // Since each result represent an index.dat file,
            // just create these files with the following notation:
            // index<Number>.dat (i.e. index0.dat, index1.dat,..., indexN.dat)
            // Write each index.dat file to a temp directory.
            //BlackboardArtifact bbart = fsc.newArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY);
            indexFileName = "index" + Integer.toString((int) indexFile.getId()) + ".dat";
            //indexFileName = "index" + Long.toString(bbart.getArtifactID()) + ".dat";
            temps = RAImageIngestModule.getRATempPath(currentCase, "IE") + File.separator + indexFileName;
            File datFile = new File(temps);
            if (controller.isCancelled()) {
                break;
            }
            try {
                ContentUtils.writeToFile(indexFile, datFile);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error while trying to write index.dat file " + datFile.getAbsolutePath(), e);
                this.addErrorMessage(this.getName() + ": Error while trying to write file:" + datFile.getAbsolutePath());
                continue;
            }

            String filename = "pasco2Result." + indexFile.getId() + ".txt";
            boolean bPascProcSuccess = executePasco(temps, filename);

            //At this point pasco2 proccessed the index files.
            //Now fetch the results, parse them and the delete the files.
            if (bPascProcSuccess) {
                parsePascoOutput(indexFile, filename);
                foundHistory = true;
                
                //Delete index<n>.dat file since it was succcessfully by Pasco
                datFile.delete();
            } else {
                logger.log(Level.WARNING, "pasco execution failed on: " + this.getName());
                this.addErrorMessage(this.getName() + ": Error processing Internet Explorer history.");
            }
        }
        
        if (foundHistory) {
            services.fireModuleDataEvent(new ModuleDataEvent("Recent Activity", BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY));
        }
    }

    /**
     * Execute pasco on a single file that has been saved to disk. 
     * @param indexFilePath Path to local index.dat file to analyze
     * @param outputFileName Name of file to save output to
     * @return false on error
     */
    private boolean executePasco(String indexFilePath, String outputFileName) {
        boolean success = true;

        Writer writer = null;
        try {
            final String outputFileFullPath = moduleTempResultsDir + File.separator + outputFileName;
            logger.log(Level.INFO, "Writing pasco results to: " + outputFileFullPath);
            writer = new FileWriter(outputFileFullPath);
            execPasco = new ExecUtil();
            execPasco.execute(writer, JAVA_PATH, 
                    "-cp", PASCO_LIB_PATH, 
                    "isi.pasco2.Main", "-T", "history", indexFilePath );
            // @@@ Investigate use of history versus cache as type.
        } catch (IOException ex) {
            success = false;
            logger.log(Level.SEVERE, "Unable to execute Pasco to process Internet Explorer web history.", ex);
        } catch (InterruptedException ex) {
            success = false;
            logger.log(Level.SEVERE, "Pasco has been interrupted, failed to extract some web history from Internet Explorer.", ex);
        }
        finally {
            if (writer != null) {
                try {
                    writer.flush();
                    writer.close();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Error closing writer stream after for Pasco result", ex);
                }
            }
        }
        return success;
    }

    /**
     * parse Pasco output and create artifacts
     * @param origFile Original index.dat file that was analyzed to get this output
     * @param pascoOutputFileName name of pasco output file
     */
    private void parsePascoOutput(AbstractFile origFile, String pascoOutputFileName) {
        
        String fnAbs = moduleTempResultsDir + File.separator + pascoOutputFileName;

        File file = new File(fnAbs);
        if (file.exists() == false) {
            this.addErrorMessage(this.getName() + ": Pasco output not found: " + file.getName());
            logger.log(Level.WARNING, "Pasco Output not found: " + file.getPath());
            return;
        }

        // Make sure the file the is not empty or the Scanner will
        // throw a "No Line found" Exception
        if (file.length() == 0) {
            return;
        }

        Scanner fileScanner;
        try {
            fileScanner = new Scanner(new FileInputStream(file.toString()));
        } catch (FileNotFoundException ex) {
            this.addErrorMessage(this.getName() + ": Error parsing IE history entry " + file.getName());
            logger.log(Level.WARNING, "Unable to find the Pasco file at " + file.getPath(), ex);
            return;
        }

        while (fileScanner.hasNext()) {
            String line = fileScanner.nextLine();
            if (!line.startsWith("URL")) {  
                continue;
            }

            String[] lineBuff = line.split("\\t");

            if (lineBuff.length < 4) {
                logger.log(Level.INFO, "Found unrecognized IE history format.");
                continue;
            }

            String ddtime = lineBuff[2];
            String actime = lineBuff[3];
            Long ftime = (long) 0;
            String user = "";
            String realurl = "";
            String domain = "";

            /* We've seen two types of lines: 
             * URL  http://XYZ.com ....
             * URL  Visited: Joe@http://XYZ.com ....
             */
            if (lineBuff[1].contains("@")) {
                String url[] = lineBuff[1].split("@", 2);
                user = url[0];
                user = user.replace("Visited:", "");
                user = user.replace(":Host:", "");
                user = user.replaceAll("(:)(.*?)(:)", "");
                user = user.trim();
                realurl = url[1];
                realurl = realurl.replace("Visited:", "");
                realurl = realurl.replaceAll(":(.*?):", "");
                realurl = realurl.replace(":Host:", "");
                realurl = realurl.trim();
            } else {
                user = "";
                realurl = lineBuff[1].trim();
            }

            domain = Util.extractDomain(realurl);

            if (!ddtime.isEmpty()) {
                ddtime = ddtime.replace("T", " ");
                ddtime = ddtime.substring(ddtime.length() - 5);
            }

            if (!actime.isEmpty()) {
                try {
                    Long epochtime = dateFormatter.parse(actime).getTime();
                    ftime = epochtime.longValue();
                    ftime = ftime / 1000;
                } catch (ParseException e) {
                    this.addErrorMessage(this.getName() + ": Error parsing Internet Explorer History entry.");
                    logger.log(Level.SEVERE, "Error parsing Pasco results.", e);
                }
            }

            try {
                BlackboardArtifact bbart = origFile.newArtifact(ARTIFACT_TYPE.TSK_WEB_HISTORY);
                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL.getTypeID(), "RecentActivity", realurl));
                //bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL_DECODED.getTypeID(), "RecentActivity", EscapeUtil.decodeURL(realurl)));

                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID(), "RecentActivity", ftime));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID(), "RecentActivity", ""));
                // @@@ NOte that other browser modules are adding TITLE in hre for the title
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID(), "RecentActivity", "Internet Explorer"));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID(), "RecentActivity", domain));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_USER_NAME.getTypeID(), "RecentActivity", user));
                bbart.addAttributes(bbattributes);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error writing Internet Explorer web history artifact to the blackboard.", ex);
            }                                    
        }
        fileScanner.close();        
    }

    @Override
    public void init(IngestModuleInit initContext) {
        services = IngestServices.getDefault();
    }

    @Override
    public void complete() {
    }

    @Override
    public void stop() {
        if (execPasco != null) {
            execPasco.stop();
            execPasco = null;
        }
        
        //call regular cleanup from complete() method
        complete();
    }

    @Override
    public String getDescription() {
        return "Extracts activity from Internet Explorer browser, as well as recent documents in windows.";
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
}
