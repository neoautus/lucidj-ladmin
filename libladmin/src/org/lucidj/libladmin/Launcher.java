/*
 * Copyright 2018 NEOautus Ltd. (http://neoautus.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.lucidj.libladmin;

import org.apache.commons.exec.*;
import org.lucidj.libladmin.shared.TinyLog;

import java.io.File;
import java.util.List;

public class Launcher implements ExecuteResultHandler
{
    private final static TinyLog log = new TinyLog (Launcher.class);

    private LauncherWatchdog watchdog;
    private File command_file;

    public Launcher (File command_file)
    {
        this.command_file = command_file;
    }

    public static Launcher newLauncher (File command_file)
    {
        if (!command_file.exists () || !command_file.canExecute())
        {
            return (null);
        }

        return (new Launcher (command_file));
    }

    //=================================================================================================================
    // PROCESS LAUNCHER
    //=================================================================================================================

    private void addArgument (CommandLine cmdline, String name, String value)
    {
        cmdline.addArgument ("-D" + name + "=" + value, false);
    }

    public static String string_join (String delim, String[] elements)
    {
        StringBuilder sbStr = new StringBuilder();

        for (int i = 0; i < elements.length; i++)
        {
            if (i > 0)
            {
                sbStr.append (delim);
            }
            sbStr.append (elements [i]);
        }
        return sbStr.toString();
    }

    public static String string_join (String delim, List<String> elements)
    {
        String[] elements_array = new String [elements.size ()];
        return (string_join (delim, elements.toArray (elements_array)));
    }

    private boolean launch_cmdline (CommandLine cmdline, boolean daemon)
    {
        // TODO: HANDLE CTRL+C SHUTDOWN BUG
        DaemonExecutor executor = new DaemonExecutor ();

        // Do NOT destroy processes on VM exit
        executor.setProcessDestroyer (null);

        // Wait a resonable amount of time for process start
        watchdog = new LauncherWatchdog (15000);
        executor.setWatchdog (watchdog);

        try
        {
            // TODO: DUMP stdout/stderr
            if (daemon)
            {
                // Launch and waits until the process becomes alive
                executor.execute (cmdline, this);
                watchdog.waitForProcessStarted ();
            }
            else
            {
                // Synchronous run
                executor.execute (cmdline);
            }
        }
        catch (Exception e)
        {
            System.out.println ("Exception on exec: " + e.toString ());
        }

        if (watchdog.failureReason () != null)
        {
            System.out.println ("Launcher: Failed: " + watchdog.failureReason ().toString ());
            return (false);
        }

        System.out.println ("Launcher: Successful");
        return (true);
    }

    // TODO: USE ProcessBuilder
    public boolean launchJavaJar (String jar_file, String[] args, boolean daemon)
    {
        System.out.println ("Exec: " + command_file);

        CommandLine cmdline = new CommandLine (command_file);

        //----------
        // JVM args
        //----------

        // TODO: THROW ALL THIS CONFIGS INTO SOME XML/WHATEVER ON CONF DIR
        cmdline.addArgument ("-server");
        cmdline.addArgument ("-Xms128M");
        cmdline.addArgument ("-Xmx1024M");
        cmdline.addArgument ("-XX:+UnlockDiagnosticVMOptions");
        cmdline.addArgument ("-XX:+UnsyncloadClass");
        cmdline.addArgument ("-Djava.awt.headless=true");
        //cmdline.addArgument ("-Dcom.sun.management.jmxremote");

        //-------------
        // LucidJ args
        //-------------

        if (System.getProperty ("user.conf") != null)
        {
            addArgument (cmdline, "user.conf", System.getProperty ("user.conf"));
        }

        // Jar to exec
        cmdline.addArgument ("-jar");
        cmdline.addArgument (jar_file, false);

        // Add jar arguments
        if (args != null)
        {
            for (String arg: args)
            {
                cmdline.addArgument (arg);
            }
        }

        //------------------
        // Ready to launch!
        //------------------

        String[] argv = cmdline.toStrings ();

        for (int i = 0; i < argv.length; i++)
        {
            log.debug ("argv[{}] = '{}'", i, argv [i]);
        }
        return (launch_cmdline (cmdline, daemon));
    }

    @Override // ExecuteResultHandler
    public void onProcessComplete (int i)
    {
        // Nothing needed
    }

    @Override // ExecuteResultHandler
    public void onProcessFailed (ExecuteException e)
    {
        watchdog.fail (e);
    }

    class LauncherWatchdog extends ExecuteWatchdog
    {
        private volatile boolean starting = true;
        private volatile Throwable throwable;

        LauncherWatchdog (long timeout)
        {
            super (timeout);
        }

        @Override
        public synchronized void start (Process process)
        {
            starting = false;
            super.start (process);
        }

        public Throwable failureReason ()
        {
            return (throwable);
        }

        public void fail (Throwable throwable)
        {
            this.throwable = throwable;
            starting = false;
            stop ();
        }

        public void reset ()
        {
            starting = true;
        }

        public boolean waitForProcessStarted ()
        {
            while (starting)
            {
                try
                {
                    Thread.sleep (50);
                }
                catch (InterruptedException ignore)
                {
                    return (false);
                }
            }

            // Stop the watchdog to prevent it from killing our new process
            stop ();

            // Process started
            return (true);
        }
    }
}

// EOF
