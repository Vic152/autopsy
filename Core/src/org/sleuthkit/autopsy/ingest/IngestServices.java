/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2013 Basis Technology Corp.
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


package org.sleuthkit.autopsy.ingest;

import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;


/**
 * Services available to ingest modules via singleton instance,
 * for:
 * logging, interacting with the ingest manager
 * sending data events notifications, sending ingest inbox messages,
 * getting and setting module configurations
 * 
 */
public class IngestServices {
    
    private IngestManager manager;
    
    private Logger logger = Logger.getLogger(IngestServices.class.getName());
    
    private static IngestServices instance;
    
    private IngestServices() {
        this.manager = IngestManager.getDefault();
    }
    
    /**
     * Get handle to module services
     * @return the services handle
     */
    public static synchronized IngestServices getDefault() {
        if (instance == null) {
            instance = new IngestServices();
        }
        return instance;
    }
    
    /**
     * Get access to the current Case handle.
     * Note: When storing the Case database handle as a member variable, 
     * this method needs to be called within module init() method 
     * and the handle member variable needs to be updated,
     * to ensure the correct Case handle is being used if the Case is changed.
     * 
     * @return current Case
     */
    public Case getCurrentCase() {
        return Case.getCurrentCase();
    }
    
     /**
     * Get access to the current Case database handle for using the blackboard.
     * Note: When storing the Case database handle as a member variable, 
     * this method needs to be called within module init() method 
     * and the handle member variable needs to be updated,
     * to ensure the correct Case database handle is being used if the Case is changed.
     * 
     * @return current Case database 
     */
    public SleuthkitCase getCurrentSleuthkitCaseDb() {
        return Case.getCurrentCase().getSleuthkitCase();
    }
    
    /**
     * Get a logger to be used by the module to log messages to log files
     * @param module module to get the logger for
     * @return logger object
     */
    public Logger getLogger(IngestModuleAbstract module) {
        return Logger.getLogger(module.getName());
    }
    
    /**
     * Post ingest message
     * @param message ingest message to be posted by ingest module
     */
    public void postMessage(final IngestMessage message) {
        manager.postMessage(message);
    }
    
    /**
     * Fire module event to notify registered module event listeners
     * @param eventType the event type, defined in IngestManager.IngestManagerEvents
     * @param moduleName the module name
     */
    public void fireModuleEvent(String eventType, String moduleName) {
        IngestManager.fireModuleEvent(eventType, moduleName);
    }

    
    /**
     * Fire module data event to notify registered module data event listeners
     * @param moduleDataEvent module data event, encapsulating blackboard artifact data
     */
    public void fireModuleDataEvent(ModuleDataEvent moduleDataEvent) {
        IngestManager.fireModuleDataEvent(moduleDataEvent);
    }
    
    
     /**
     * Fire module content event to notify registered module content event listeners
     * @param moduleContentEvent module content event, encapsulating content changed
     */
    public void fireModuleContentEvent(ModuleContentEvent moduleContentEvent) {
        IngestManager.fireModuleContentEvent(moduleContentEvent);
    }
    
    /**
     * Schedule a file for ingest.  
     * The file is usually a product of a recently ran ingest.  
     * Now we want to process this file with the same ingest context.
     * 
     * @param file file to be scheduled
     * @param pipelineContext the ingest context for the file ingest pipeline
     */
    public void scheduleFile(AbstractFile file, PipelineContext pipelineContext)  {
        logger.log(Level.INFO, "Scheduling file: " + file.getName());
        manager.scheduleFile(file, pipelineContext);
    }
    
    
     /**
     * Get free disk space of a drive where ingest data are written to
     * That drive is being monitored by IngestMonitor thread when ingest is running.
     * Use this method to get amount of free disk space anytime.
     * 
     * @return amount of disk space, -1 if unknown
     */
    public long getFreeDiskSpace() {
        return manager.getFreeDiskSpace();
    }
    
    
    
    /**
     * Facility for a file ingest module to check a return value from another file ingest module
     * that executed for the same file earlier in the file ingest pipeline
     * The module return value can be used as a guideline to skip processing the file
     * 
     * @param moduleName registered module name of the module to check the return value of
     * @return the return value of the previously executed module for the currently processed file in the file ingest pipeline
     */
    public IngestModuleAbstractFile.ProcessResult getAbstractFileModuleResult(String moduleName) {
        return manager.getAbstractFileModuleResult(moduleName);
    }
    
    /**
     * Gets a configuration setting for a module
     * @param moduleName moduleName identifier unique to that module
     * @param settingName setting name to retrieve
     * @return setting value for the module / setting name, or null if not found
     */
    public String getConfigSetting(String moduleName, String settingName) {
        return new ModuleSettings(moduleName).getConfigSetting(settingName);
    }
    
    /**
     * Sets a configuration setting for a module
     * @param moduleName moduleName identifier unique to that module
     * @param settingName setting name to set
     * @param settingVal setting value to set
     */
    public void setConfigSetting(String moduleName, String settingName, String settingVal) {
        new ModuleSettings(moduleName).setConfigSetting(settingName, settingVal);
    }
    
    /**
     * Gets configuration settings for a module
     * @param moduleName moduleName identifier unique to that module
     * @return settings for the module / setting name
     */
    public Map<String,String> getConfigSettings(String moduleName) {
        return new ModuleSettings(moduleName).getConfigSettings();
    }
    
   /**
     * Sets configuration settings for a module, while preserving the module settings not specified
     * to be set.
     * @param moduleName moduleName identifier unique to that module
     * @param settings settings to set and replace old settings, keeping settings not specified in the map.
     * 
     */
    public void setConfigSettings(String moduleName, Map<String,String>settings) {
        new ModuleSettings(moduleName).setConfigSettings(settings);
    }
    
    
    
    
    
}
