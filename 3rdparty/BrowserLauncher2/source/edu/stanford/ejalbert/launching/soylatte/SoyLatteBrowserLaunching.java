/************************************************
    Copyright 2007,2008 Jeff Chapman

    This file is part of BrowserLauncher2.

    BrowserLauncher2 is free software; you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    BrowserLauncher2 is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with BrowserLauncher2; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

 ************************************************/
// $Id: SoyLatteBrowserLaunching.java,v 1.2 2008/11/12 21:11:00 jchapman0 Exp $
package edu.stanford.ejalbert.launching.soylatte;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import edu.stanford.ejalbert.exception.BrowserLaunchingExecutionException;
import edu.stanford.ejalbert.exception.BrowserLaunchingInitializingException;
import edu.stanford.ejalbert.exception.UnsupportedOperatingSystemException;
import edu.stanford.ejalbert.launching.IBrowserLaunching;
import net.sf.wraplog.AbstractLogger;
import edu.stanford.ejalbert.launching.IBrowserEventCallBack;


public class SoyLatteBrowserLaunching
        implements IBrowserLaunching {
    /**
     * config file for linux/unix
     */
    public static final String CONFIGFILE_SOYLATTE =
            "/edu/stanford/ejalbert/launching/soylatte/soylatteConfig.properties";
    /**
     * map of supported browsers. The map contains
     * displayName => SoyLatteBrowser mappings.
     */
    private Map soylatteBrowsers = new TreeMap(String.CASE_INSENSITIVE_ORDER);

    protected final AbstractLogger logger; // in ctor
    /**
     * name of config file passed into constructor
     */
    private final String configFileName; // in ctor
    /**
     * new window policy to apply when opening a url. If true,
     * try to force url into a new browser instance/window.
     */
    private boolean forceNewWindow = false;

    /**
     * Sets the logger and config file name.
     *
     * @param logger AbstractLogger
     * @param configFile String
     */
    public SoyLatteBrowserLaunching(AbstractLogger logger) {
        this.logger = logger;
        this.configFileName = CONFIGFILE_SOYLATTE;
    }

    /**
     * Provides access the browsers map for extending classes.
     *
     * @param key String
     * @return SoyLatteBrowser
     */
    protected SoyLatteBrowser getBrowser(String key) {
        return (SoyLatteBrowser) soylatteBrowsers.get(key);
    }

    /**
     * Attempts to open a url with the specified browser. This is
     * a utility method called by the openUrl methods.
     *
     * @param slBrowser SoyLatteBrowser
     * @param urlString String
     * @return boolean
     * @throws BrowserLaunchingExecutionException
     */
    protected boolean openUrlWithBrowser(SoyLatteBrowser slBrowser,
                                         String urlString)
            throws BrowserLaunchingExecutionException {
        boolean success = false;
        logger.info(slBrowser.getBrowserDisplayName());
        logger.info(urlString);
        try {
            int exitCode = -1;
            Process process = null;
            String[] args;
            // try to open in a new tab/current instance
            // skip this attempt if force new window is set to true
            if (!forceNewWindow) {
                args = slBrowser.getArgsForOpenBrowser(urlString);
                if (logger.isDebugEnabled()) {
                    logger.debug(Arrays.asList(args).toString());
                }
                process = Runtime.getRuntime().exec(args);
                exitCode = process.waitFor();
            }
            // try call to force a new window if requested
            if (forceNewWindow && exitCode != 0) {
                args = slBrowser.getArgsForForcingNewBrowserWindow(urlString);
                if (logger.isDebugEnabled()) {
                    logger.debug(Arrays.asList(args).toString());
                }
                process = Runtime.getRuntime().exec(args);
                exitCode = process.waitFor();
            }
            // open in a new window
            if (exitCode != 0) {
                args = slBrowser.getArgsForStartingBrowser(urlString);
                if (logger.isDebugEnabled()) {
                    logger.debug(Arrays.asList(args).toString());
                }
                process = Runtime.getRuntime().exec(args);
                exitCode = process.waitFor();
            }
            if (exitCode == 0) {
                success = true;
            }
        }
        // Runtimes may throw InterruptedException
        // want to catch every possible exception and wrap it
        catch (Exception e) {
            throw new BrowserLaunchingExecutionException(e);
        }
        return success;
    }

    /* ---------------------- from IBrowserLaunching ----------------------- */

    /**
     * Registers the browser event call back with the launcher object.
     *
     * @param callback IBrowserEventCallBack
     */
    public void setBrowserEventCallBack(IBrowserEventCallBack callback) {
        //browserEventCallback = callback;
    }

    /**
     * Uses the which command to find out which browsers are available.
     * The available browsers are put into the slBrowsers map
     * using displayName => SoyLatteBrowser mappings.
     *
     * @todo what do we do if there are no browsers available?
     * @throws BrowserLaunchingInitializingException
     */
    public void initialize()
            throws BrowserLaunchingInitializingException {
        try {
            URL configUrl = getClass().getResource(configFileName);
            if (configUrl == null) {
                throw new BrowserLaunchingInitializingException(
                        "unable to find config file: " + configFileName);
            }
            StringBuffer potentialBrowserNames = new StringBuffer();
            Properties configProps = new Properties();
            configProps.load(configUrl.openStream());
            String sepChar = configProps.getProperty(PROP_KEY_DELIMITER);
            Iterator keysIter = configProps.keySet().iterator();
            while (keysIter.hasNext()) {
                String key = (String) keysIter.next();
                if (key.startsWith(PROP_KEY_BROWSER_PREFIX)) {
                    SoyLatteBrowserImpl browser = new SoyLatteBrowserImpl(
                            sepChar,
                            configProps.getProperty(key));
                    if (browser.isBrowserAvailable(logger)) {
                        soylatteBrowsers.put(browser.getBrowserDisplayName(),
                                             browser);
                    }
                    else {
                        if (potentialBrowserNames.length() > 0) {
                            potentialBrowserNames.append("; ");
                        }
                        potentialBrowserNames.append(
                                browser.getBrowserDisplayName());
                    }
                }
            }
            if (soylatteBrowsers.size() == 0) {
                // no browser installed
                throw new BrowserLaunchingInitializingException(
                        "one of the supported browsers must be installed: "
                        + potentialBrowserNames);
            }
            logger.info(soylatteBrowsers.keySet().toString());
            soylatteBrowsers = Collections.unmodifiableMap(soylatteBrowsers);
        }
        catch (IOException ioex) {
            throw new BrowserLaunchingInitializingException(ioex);
        }
    }

    /**
     * Opens a url in one of the available browsers.
     *
     * @param urlString String
     * @throws BrowserLaunchingExecutionException
     */
    public void openUrl(String urlString)
            throws UnsupportedOperatingSystemException,
            BrowserLaunchingExecutionException,
            BrowserLaunchingInitializingException {
        try {
            logger.info(urlString);
            boolean success = false;
            // get list of browsers to try
            List slBrowsersList = new ArrayList(soylatteBrowsers.values());
            // check system property which may contain user's preferred browser
            String browserId = System.getProperty(
                    IBrowserLaunching.BROWSER_SYSTEM_PROPERTY,
                    null);
            if (browserId != null) {
                SoyLatteBrowser slBrowser =
                        (SoyLatteBrowser) soylatteBrowsers.get(browserId);
                if (slBrowser != null) {
                    // if user has preferred browser, place at start of list
                    slBrowsersList.add(0, slBrowser);
                }
            }
            // iterate over browsers until one works
            Iterator iter = slBrowsersList.iterator();
            SoyLatteBrowser browser;
            Process process;
            while (iter.hasNext() && !success) {
                browser = (SoyLatteBrowser) iter.next();
                success = openUrlWithBrowser(browser,
                                             urlString);
            }
        }
        catch (Exception e) {
            throw new BrowserLaunchingExecutionException(e);
        }
    }

    /**
     * Opens a url in the specified browser. If the call to the
     * specified browser fails, the method falls through to the
     * non-targetted version.
     *
     * @param browser String
     * @param urlString String
     * @throws UnsupportedOperatingSystemException
     * @throws BrowserLaunchingExecutionException
     * @throws BrowserLaunchingInitializingException
     */
    public void openUrl(String browser,
                        String urlString)
            throws UnsupportedOperatingSystemException,
            BrowserLaunchingExecutionException,
            BrowserLaunchingInitializingException {
        SoyLatteBrowser slBrowser = (SoyLatteBrowser) soylatteBrowsers.get(browser);
        if (slBrowser == null ||
            IBrowserLaunching.BROWSER_DEFAULT.equals(browser)) {
            logger.debug("falling through to non-targetted openUrl");
            openUrl(urlString);
        }
        else {
            boolean success = openUrlWithBrowser(slBrowser,
                                                 urlString);
            if (!success) {
                logger.debug(
                        "open browser failure, trying non-targetted openUrl");
                openUrl(urlString);
            }
        }
    }

    /**
     * Allows user to target several browsers. The names of
     * potential browsers can be accessed via the
     * {@link #getBrowserList() getBrowserList} method.
     * <p>
     * The browsers from the list will be tried in order
     * (first to last) until one of the calls succeeds. If
     * all the calls to the requested browsers fail, the code
     * will fail over to the default browser.
     *
     * @param browsers List
     * @param urlString String
     * @throws UnsupportedOperatingSystemException
     * @throws BrowserLaunchingExecutionException
     * @throws BrowserLaunchingInitializingException
     */
    public void openUrl(List browsers,
                        String urlString)
            throws UnsupportedOperatingSystemException,
            BrowserLaunchingExecutionException,
            BrowserLaunchingInitializingException {
        if (browsers == null || browsers.isEmpty()) {
            logger.debug("falling through to non-targetted openUrl");
            openUrl(urlString);
        }
        else {
            boolean success = false;
            Iterator iter = browsers.iterator();
            while (iter.hasNext() && !success) {
                SoyLatteBrowser slBrowser = (SoyLatteBrowser) soylatteBrowsers.get(
                        iter.next());
                if (slBrowser != null) {
                    success = openUrlWithBrowser(slBrowser,
                                                 urlString);
                }
            }
            if (!success) {
                logger.debug(
                        "none of listed browsers succeeded; falling through to non-targetted openUrl");
                openUrl(urlString);
            }
        }
    }

    /**
     * Returns a list of browsers to be used for browser
     * targetting. This list will always contain at least
     * one item--the BROWSER_DEFAULT.
     *
     * @return List
     */
    public List getBrowserList() {
        List browsers = new ArrayList();
        // add Default if not present
        if (!soylatteBrowsers.containsKey(IBrowserLaunching.BROWSER_DEFAULT)) {
            browsers.add(IBrowserLaunching.BROWSER_DEFAULT);
        }
        browsers.addAll(soylatteBrowsers.keySet());
        return browsers;
    }

    /**
     * Returns the policy used for opening a url in a browser.
     * <p>
     * If the policy is true, an attempt will be made to force the
     * url to be opened in a new instance (window) of the
     * browser.
     * <p>
     * If the policy is false, the url may open in a new window or
     * a new tab.
     * <p>
     * Most browsers on Unix/Linux systems have command line options to
     * support this feature.
     *
     * @return boolean
     */
    public boolean getNewWindowPolicy() {
        return forceNewWindow;
    }

    /**
     * Sets the policy used for opening a url in a browser.
     *
     * @param forceNewWindow boolean
     */
    public void setNewWindowPolicy(boolean forceNewWindow) {
        this.forceNewWindow = forceNewWindow;
    }
}
