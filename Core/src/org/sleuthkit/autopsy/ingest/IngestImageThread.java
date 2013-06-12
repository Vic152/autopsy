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

//ingester worker for image queue
import java.awt.EventQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.StopWatch;
import org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;

/**
 * worker for every ingest image module there is a separate instance per image /
 * module pair
 */
public class IngestImageThread extends SwingWorker<Void, Void> {

    private final Logger logger = Logger.getLogger(IngestImageThread.class.getName());
    private ProgressHandle progress;
    private final PipelineContext<IngestModuleImage>pipelineContext;
    private final Image image;
    private final IngestModuleImage module;
    private IngestImageWorkerController controller;
    private final IngestManager manager;
    private final IngestModuleInit init;
    //current method of enqueuing image ingest modules with locks and internal lock queue
    //allows to init, run and complete a single image ingest module at time
    //uses fairness policy to run them in order enqueued
    //TODO  use a real queue and manager to allow multiple different modules to run in parallel
    private static final Lock imageIngestModuleLock = new ReentrantReadWriteLock(true).writeLock();

    IngestImageThread(IngestManager manager, PipelineContext<IngestModuleImage>pipelineContext, Image image, IngestModuleImage module, IngestModuleInit init) {
        this.manager = manager;
        this.pipelineContext = pipelineContext;
        this.image = image;
        this.module = module;
        this.init = init;
    }

    PipelineContext<IngestModuleImage>getContext() {
        return pipelineContext;
    }
    
    Content getContent() {
        return pipelineContext.getScheduledTask().getContent();
    }

    IngestModuleImage getModule() {
        return module;
    }

    @Override
    protected Void doInBackground() throws Exception {

        logger.log(Level.INFO, "Pending module: " + module.getName());
        
        final String displayName = module.getName() + " image id:" + image.getId();
        progress = ProgressHandleFactory.createHandle(displayName + " (Pending...)", new Cancellable() {
            @Override
            public boolean cancel() {
                logger.log(Level.INFO, "Image ingest module " + module.getName() + " cancelled by user.");
                if (progress != null) {
                    progress.setDisplayName(displayName + " (Cancelling...)");
                }
                return IngestImageThread.this.cancel(true);
            }
        });
        progress.start();
        progress.switchToIndeterminate();

        imageIngestModuleLock.lock();
        try {
            if (this.isCancelled()) {
                logger.log(Level.INFO, "Cancelled while pending, module: " + module.getName());
                return Void.TYPE.newInstance();
            }
            logger.log(Level.INFO, "Starting module: " + module.getName());
            logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());
            progress.setDisplayName(displayName);

            logger.log(Level.INFO, "Initializing module: " + module.getName());
            try {
                module.init(init);
            } catch (Exception e) {
                logger.log(Level.INFO, "Failed initializing module: " + module.getName() + ", will not run.");
                return Void.TYPE.newInstance();
                //will not run
            }

            logger.log(Level.INFO, "Starting processing of module: " + module.getName());

            controller = new IngestImageWorkerController(this, progress);

            if (isCancelled()) {
                logger.log(Level.INFO, "Terminating image ingest module " + module.getName() + " due to cancellation.");
                return Void.TYPE.newInstance();
            }
            final StopWatch timer = new StopWatch();
            timer.start();
            try {
                module.process(pipelineContext, image, controller);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Exception in module: " + module.getName() + " image: " + image.getName(), e);
            } finally {
                timer.stop();
                logger.log(Level.INFO, "Done processing of module: " + module.getName()
                        + " took " + timer.getElapsedTimeSecs() + " secs. to process()");


                //cleanup queues (worker and image/module)
                manager.removeImageIngestWorker(this);

                if (!this.isCancelled()) {
                    logger.log(Level.INFO, "Module " + module.getName() + " completed");
                    try {
                        module.complete();
                    } catch (Exception e) {
                        logger.log(Level.INFO, "Error completing the module " + module.getName(), e);
                    }
                    IngestManager.fireModuleEvent(IngestModuleEvent.COMPLETED.toString(), module.getName());
                } else {
                    logger.log(Level.INFO, "Module " + module.getName() + " stopped");
                    try {
                        module.stop();
                    } catch (Exception e) {
                        logger.log(Level.INFO, "Error stopping the module" + module.getName(), e);
                    }
                    IngestManager.fireModuleEvent(IngestModuleEvent.STOPPED.toString(), module.getName());
                }

            }
            return Void.TYPE.newInstance();
        } finally {
            //release the lock so next module can run
            imageIngestModuleLock.unlock();
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    progress.finish();
                }
            });
            logger.log(Level.INFO, "Done running module: " + module.getName());
            logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());
        }
    }
}
