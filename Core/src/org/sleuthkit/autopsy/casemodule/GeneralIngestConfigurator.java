/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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

package org.sleuthkit.autopsy.casemodule;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.ingest.IngestDialogPanel;
import static org.sleuthkit.autopsy.ingest.IngestDialogPanel.DISABLED_MOD;
import static org.sleuthkit.autopsy.ingest.IngestDialogPanel.PARSE_UNALLOC;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstract;
import org.sleuthkit.datamodel.Image;

/**
 *
 */
public class GeneralIngestConfigurator implements IngestConfigurator {
    
    private Image image;
    private IngestManager manager;
    private IngestDialogPanel ingestDialogPanel;
    private String moduleContext;

    public GeneralIngestConfigurator(String moduleContext) {
        this.moduleContext = moduleContext;
        ingestDialogPanel = new IngestDialogPanel();
        manager = IngestManager.getDefault();
        reload();
    }

    @Override
    public JPanel getIngestConfigPanel() {
        return ingestDialogPanel;
    }

    @Override
    public void setImage(Image image) {
        this.image = image;
    }

    @Override
    public void start() {
        
        //pick the modules
        List<IngestModuleAbstract> modulesToStart = ingestDialogPanel.getModulesToStart();

        if (!modulesToStart.isEmpty()) {
            manager.execute(modulesToStart, image);
        }

        //update ingest proc. unalloc space
        manager.setProcessUnallocSpace(ingestDialogPanel.processUnallocSpaceEnabled());
    }

    @Override
    public void save() {
        
        // Save the current module
        IngestModuleAbstract currentModule = ingestDialogPanel.getCurrentIngestModule();
        if (currentModule != null && currentModule.hasSimpleConfiguration()) {
            currentModule.saveSimpleConfiguration();
        }
        
        // create a list of disabled modules
        List<IngestModuleAbstract> disabledModules = IngestManager.getDefault().enumerateAllModules();
        disabledModules.removeAll(ingestDialogPanel.getModulesToStart());
        
        // create a csv list
        String disabledModulesCsv = moduleListToCsv(disabledModules);
        
        ModuleSettings.setConfigSetting(moduleContext, DISABLED_MOD, disabledModulesCsv);
        String processUnalloc = Boolean.toString(ingestDialogPanel.processUnallocSpaceEnabled());
        ModuleSettings.setConfigSetting(moduleContext, PARSE_UNALLOC, processUnalloc);
    }
    
    public static String moduleListToCsv(List<IngestModuleAbstract> lst) {
        
        if (lst == null || lst.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lst.size() - 1; ++i) {
            sb.append(lst.get(i).getName()).append(", ");
        }
        
        // and the last one
        sb.append(lst.get(lst.size() - 1).getName());
        
        return sb.toString();
    }
    
    public static List<IngestModuleAbstract> csvToModuleList(String csv) {
        List<IngestModuleAbstract> modules = new ArrayList<>();
        
        if (csv == null || csv.isEmpty()) {
            return modules;
        }
        
        String[] moduleNames = csv.split(", ");
        List<IngestModuleAbstract> allModules = IngestManager.getDefault().enumerateAllModules();
        for (String moduleName : moduleNames) {
            for (IngestModuleAbstract module : allModules) {
                if (moduleName.equals(module.getName())) {
                    modules.add(module);
                    break;
                }
            }
        }
        
        return modules;
    }

    @Override
    public void reload() {
        
        // get the csv list of disabled modules
        String disabledModulesCsv = ModuleSettings.getConfigSetting(moduleContext, DISABLED_MOD);
        
        // create a list of modules from it
        List<IngestModuleAbstract> disabledModules = csvToModuleList(disabledModulesCsv);
        
        // tell th ingestDialogPanel to unselect these modules
        ingestDialogPanel.setDisabledModules(disabledModules);
        
        boolean processUnalloc = Boolean.parseBoolean(ModuleSettings.getConfigSetting(moduleContext, PARSE_UNALLOC));
        ingestDialogPanel.setProcessUnallocSpaceEnabled(processUnalloc);
    }

    @Override
    public boolean isIngestRunning() {
        return manager.isIngestRunning();
    }
}
