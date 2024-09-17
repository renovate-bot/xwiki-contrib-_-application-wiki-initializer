/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.wikiinitializer.internal;

import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.bridge.event.WikiReadyEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.wikiinitializer.WikiInitializationException;
import org.xwiki.contrib.wikiinitializer.WikiInitializationManager;
import org.xwiki.contrib.wikiinitializer.WikiInitializerConfiguration;
import org.xwiki.job.DefaultRequest;
import org.xwiki.job.JobException;
import org.xwiki.job.JobExecutor;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.ApplicationStartedEvent;
import org.xwiki.observation.event.Event;
import org.xwiki.wiki.descriptor.WikiDescriptor;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;
import org.xwiki.wiki.manager.WikiManagerException;

import com.xpn.xwiki.XWiki;

/**
 * Listener that will automatically start the wiki initialization job.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
@Named(WikiInitializationListener.LISTENER_NAME)
public class WikiInitializationListener extends AbstractEventListener
{
    /**
     * The listener name.
     */
    public static final String LISTENER_NAME = "WikiInitializationListener";

    @Inject
    private Logger logger;

    @Inject
    private JobExecutor jobExecutor;

    @Inject
    private WikiInitializerConfiguration configuration;

    @Inject
    private WikiDescriptorManager wikiDescriptorManager;

    @Inject
    private WikiInitializationManager wikiInitializationManager;

    /**
     * Create a new {@link WikiInitializationListener}.
     */
    public WikiInitializationListener()
    {
        super(LISTENER_NAME, new ApplicationStartedEvent(), new WikiReadyEvent());
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        if (event instanceof ApplicationStartedEvent && configuration.initializeMainWiki()) {
            try {
                jobExecutor.execute(MainWikiInitializationJob.JOB_TYPE, new DefaultRequest());
            } catch (JobException e) {
                logger.error("Failed to initialize main wiki", e);
            }
        } else if (event instanceof WikiReadyEvent && XWiki.DEFAULT_MAIN_WIKI.equals(source)) {
            try {
                Collection<WikiDescriptor> wikisToInitialize = (configuration.initializeAllSubWikis())
                    ? wikiDescriptorManager.getAll()
                    : configuration.getInitializableWikis();

                for (WikiDescriptor descriptor : wikisToInitialize) {
                    wikiInitializationManager.initialize(descriptor);
                }
            } catch (WikiManagerException | WikiInitializationException e) {
                logger.error("Failed to initialize sub-wikis", e);
            }
        }
    }
}
