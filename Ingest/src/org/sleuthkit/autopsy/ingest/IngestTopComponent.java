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
package org.sleuthkit.autopsy.ingest;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataExplorer;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskException;

/**
 * Top component explorer for the Ingest module.
 */
public final class IngestTopComponent extends TopComponent implements DataExplorer {

    private static IngestTopComponent instance;
    private static final Logger logger = Logger.getLogger(IngestTopComponent.class.getName());
    private IngestManager manager = null;
    private Collection<IngestServiceAbstract> services;
    private Map<String, Boolean> serviceStates;
    private IngestMessagePanel messagePanel;
    private ActionListener serviceSelListener = new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent ev) {
            JCheckBox box = (JCheckBox) ev.getSource();
            serviceStates.put(box.getName(), box.isSelected());
        }
    };

    private IngestTopComponent() {
        services = new ArrayList<IngestServiceAbstract>();
        serviceStates = new HashMap<String, Boolean>();
        initComponents();
        customizeComponents();
        setName(NbBundle.getMessage(IngestTopComponent.class, "CTL_IngestTopComponent"));
        setToolTipText(NbBundle.getMessage(IngestTopComponent.class, "HINT_IngestTopComponent"));
        //putClientProperty(TopComponent.PROP_UNDOCKING_DISABLED, Boolean.TRUE);
        putClientProperty(TopComponent.PROP_CLOSING_DISABLED, Boolean.TRUE);

    }

    public static synchronized IngestTopComponent getDefault() {
        if (instance == null) {
            instance = new IngestTopComponent();
        }
        return instance;
    }

    @Override
    public TopComponent getTopComponent() {
        return this;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        logger.log(Level.INFO, "Unhandled property change: " + evt.getPropertyName());
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_NEVER;
    }

    private void customizeComponents() {
        //custom GUI setup not done by builder
        freqSlider.setToolTipText("Lower update frequency can optimize performance of certain ingest services, but also reduce real time status feedback");

        JScrollPane scrollPane = new JScrollPane(servicesPanel);
        scrollPane.setPreferredSize(this.getSize());
        this.add(scrollPane, BorderLayout.CENTER);

        servicesPanel.setLayout(new BoxLayout(servicesPanel, BoxLayout.Y_AXIS));

        
        freqSlider.setEnabled(false);
        
        messagePanel = new IngestMessagePanel();
        
        messagePanel.setOpaque(false);
        messageFrame.setOpaque(false);
        //this.setComponentZOrder(messageFrame, 0);
        this.setComponentZOrder(controlPanel, 2);
        messageFrame.setContentPane(messagePanel);
        messageFrame.pack();
        messageFrame.setVisible(true);
        
       
        
        Collection<IngestServiceImage> imageServices = IngestManager.enumerateImageServices();
        for (IngestServiceImage service : imageServices) {
            final String serviceName = service.getName();
            services.add(service);
            JCheckBox checkbox = new JCheckBox(serviceName, true);
            checkbox.setName(serviceName);
            checkbox.addActionListener(serviceSelListener);
            servicesPanel.add(checkbox);
            serviceStates.put(serviceName, true);
        }

        Collection<IngestServiceFsContent> fsServices = IngestManager.enumerateFsContentServices();
        for (IngestServiceFsContent service : fsServices) {
            final String serviceName = service.getName();
            services.add(service);
            JCheckBox checkbox = new JCheckBox(serviceName, true);
            checkbox.setName(serviceName);
            checkbox.addActionListener(serviceSelListener);
            servicesPanel.add(checkbox);
            serviceStates.put(serviceName, true);
        }
        
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainScrollPane = new javax.swing.JScrollPane();
        mainPanel = new javax.swing.JPanel();
        messageFrame = new javax.swing.JInternalFrame();
        controlPanel = new javax.swing.JPanel();
        topLable = new javax.swing.JLabel();
        servicesPanel = new javax.swing.JPanel();
        freqSlider = new javax.swing.JSlider();
        refreshFreqLabel = new javax.swing.JLabel();
        startButton = new javax.swing.JButton();

        mainScrollPane.setPreferredSize(new java.awt.Dimension(322, 732));

        mainPanel.setPreferredSize(new java.awt.Dimension(322, 732));

        messageFrame.setBorder(javax.swing.BorderFactory.createMatteBorder(2, 2, 2, 2, new java.awt.Color(204, 204, 255)));
        messageFrame.setMaximizable(true);
        messageFrame.setResizable(true);
        messageFrame.setTitle(org.openide.util.NbBundle.getMessage(IngestTopComponent.class, "IngestTopComponent.messageFrame.title")); // NOI18N
        messageFrame.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        messageFrame.setFrameIcon(null);
        messageFrame.setVisible(true);

        javax.swing.GroupLayout messageFrameLayout = new javax.swing.GroupLayout(messageFrame.getContentPane());
        messageFrame.getContentPane().setLayout(messageFrameLayout);
        messageFrameLayout.setHorizontalGroup(
            messageFrameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 237, Short.MAX_VALUE)
        );
        messageFrameLayout.setVerticalGroup(
            messageFrameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 227, Short.MAX_VALUE)
        );

        topLable.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(topLable, org.openide.util.NbBundle.getMessage(IngestTopComponent.class, "IngestTopComponent.topLable.text")); // NOI18N

        servicesPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        servicesPanel.setMinimumSize(new java.awt.Dimension(200, 150));
        servicesPanel.setPreferredSize(new java.awt.Dimension(200, 150));

        javax.swing.GroupLayout servicesPanelLayout = new javax.swing.GroupLayout(servicesPanel);
        servicesPanel.setLayout(servicesPanelLayout);
        servicesPanelLayout.setHorizontalGroup(
            servicesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 198, Short.MAX_VALUE)
        );
        servicesPanelLayout.setVerticalGroup(
            servicesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 148, Short.MAX_VALUE)
        );

        freqSlider.setMajorTickSpacing(1);
        freqSlider.setMaximum(10);
        freqSlider.setMinimum(1);
        freqSlider.setPaintLabels(true);
        freqSlider.setPaintTicks(true);
        freqSlider.setSnapToTicks(true);
        freqSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                freqSliderStateChanged(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(refreshFreqLabel, org.openide.util.NbBundle.getMessage(IngestTopComponent.class, "IngestTopComponent.refreshFreqLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(startButton, org.openide.util.NbBundle.getMessage(IngestTopComponent.class, "IngestTopComponent.startButton.text")); // NOI18N
        startButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout controlPanelLayout = new javax.swing.GroupLayout(controlPanel);
        controlPanel.setLayout(controlPanelLayout);
        controlPanelLayout.setHorizontalGroup(
            controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(controlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(controlPanelLayout.createSequentialGroup()
                        .addComponent(topLable)
                        .addContainerGap(114, Short.MAX_VALUE))
                    .addGroup(controlPanelLayout.createSequentialGroup()
                        .addComponent(servicesPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(31, 31, 31))
                    .addGroup(controlPanelLayout.createSequentialGroup()
                        .addComponent(startButton)
                        .addContainerGap(174, Short.MAX_VALUE))
                    .addGroup(controlPanelLayout.createSequentialGroup()
                        .addComponent(freqSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(31, Short.MAX_VALUE))))
            .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(controlPanelLayout.createSequentialGroup()
                    .addGap(74, 74, 74)
                    .addComponent(refreshFreqLabel)
                    .addContainerGap(77, Short.MAX_VALUE)))
        );
        controlPanelLayout.setVerticalGroup(
            controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(controlPanelLayout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addComponent(topLable)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(servicesPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(startButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(freqSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(288, Short.MAX_VALUE))
            .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(controlPanelLayout.createSequentialGroup()
                    .addGap(555, 555, 555)
                    .addComponent(refreshFreqLabel)
                    .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );

        javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(controlPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(71, Short.MAX_VALUE))
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(messageFrame, javax.swing.GroupLayout.DEFAULT_SIZE, 241, Short.MAX_VALUE)
                        .addGap(71, 71, 71))))
        );
        mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(controlPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 297, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(40, 40, 40)
                .addComponent(messageFrame)
                .addGap(129, 129, 129))
        );

        mainScrollPane.setViewportView(mainPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 322, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 732, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void startButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startButtonActionPerformed
        
        if (manager == null)
            return;
        
        //pick the services
        List<IngestServiceAbstract> servicesToStart = new ArrayList<IngestServiceAbstract>();
        for (IngestServiceAbstract service : services) {
            boolean serviceEnabled = serviceStates.get(service.getName());
            if (serviceEnabled) {
                servicesToStart.add(service);
            }
        }

        //pick the image
        //TODO which image ? 
        //for now enqueue all, and manager will skip already enqueued image
        //if image has been processed, it will be enqueued again
        int[] imageIds = Case.getCurrentCase().getImageIDs();
        SleuthkitCase sc = Case.getCurrentCase().getSleuthkitCase();
        List<Image> images = new ArrayList<Image>();
        for (int imageId : imageIds) {
            try {
                final Image image = sc.getImageById(imageId);
                images.add(image);
            } catch (TskException e) {
                logger.log(Level.SEVERE, "Error ingesting image, can't retrieve image id: " + Integer.toString(imageId), e);

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error ingesting image, can't retrieve image id: " + Integer.toString(imageId), e);
            }
        }
        
        manager.execute(servicesToStart, images);
    }//GEN-LAST:event_startButtonActionPerformed

    private void freqSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_freqSliderStateChanged
        JSlider source = (JSlider) evt.getSource();
        if (!source.getValueIsAdjusting()) {
            final int refresh = (int) source.getValue();
            manager.setUpdateFrequency(refresh);

        }
    }//GEN-LAST:event_freqSliderStateChanged
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel controlPanel;
    private javax.swing.JSlider freqSlider;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JScrollPane mainScrollPane;
    private javax.swing.JInternalFrame messageFrame;
    private javax.swing.JLabel refreshFreqLabel;
    private javax.swing.JPanel servicesPanel;
    private javax.swing.JButton startButton;
    private javax.swing.JLabel topLable;
    // End of variables declaration//GEN-END:variables

    @Override
    public void componentOpened() {
        logger.log(Level.INFO, "IngestTopComponent opened()");
        manager = new IngestManager(this);
    }

    @Override
    public void componentClosed() {
        logger.log(Level.INFO, "IngestTopComponent closed()");
    }
    
    void enableStartButton(boolean enable) {
        startButton.setEnabled(enable);
    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");

    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");

    }

    /**
     * Display ingest summary report in some dialog
     */
    void displayReport(String ingestReport) {
        //TODO widget
        logger.log(Level.INFO, "INGEST REPORT: " + ingestReport);
    }

    /**
     * Display IngestMessage from service (forwarded by IngestManager)
     */
    void displayMessage(IngestMessage ingestMessage) {
        //TODO widget
        logger.log(Level.INFO, "INGEST MESSAGE: " + ingestMessage.toString());
        messagePanel.addMessage(ingestMessage);
    }
}