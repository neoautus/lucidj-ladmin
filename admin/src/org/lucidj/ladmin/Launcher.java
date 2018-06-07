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

package org.lucidj.ladmin;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DaemonExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Launcher implements ExecuteResultHandler
{
    static String file_separator = System.getProperty ("file.separator");
    static String path_separator = System.getProperty ("path.separator");
    static String exe_suffix = System.getProperty("os.name").startsWith("Win")? ".exe": "";
    static String bin_dir = file_separator + "bin" + file_separator;

    private String main_class = "org.apache.karaf.main.Main";
    private boolean daemon_mode = true;
    private static boolean verbose = false;

//    static String system_home;
    static String jdk_home;
    static String java_exe;

    private LauncherWatchdog watchdog;

    public static void setVerbose (boolean flag)
    {
        verbose = flag;
    }

    private static String find_apache_karaf (String runtime_dir)
    {
        File[] file_array = new File (runtime_dir + "/Apache-Karaf").listFiles ();

        if (file_array == null)
        {
            return (null);
        }

        // Not yet. Lets search inside runtime dir
        List<File> file_list = Arrays.asList (file_array);

        Collections.sort (file_list, Collections.reverseOrder (new AlphanumComparator()));

        for (File file: file_list)
        {
            if (file.isDirectory())
            {
                // Embedded jdk_home
                return (file.getPath ());
            }
        }

        return (null);
    }


    public static void configure (String jdk_home_path, Path user_config)
    {
        jdk_home = jdk_home_path;

        // Setup base properties
        System.setProperty ("jdk.home", jdk_home);

        if (user_config != null)
        {
            System.setProperty ("user.conf", user_config.toString ());
        }

        // Java executable
        java_exe = jdk_home + bin_dir + "java" + exe_suffix;

        // Launcher JAR
//        System.setProperty ("app.launcher.jar",
//            Launcher.class.getProtectionDomain().getCodeSource().getLocation().getPath ());
    }

    public static Launcher newLauncher ()
    {
        return (new Launcher ());
    }

    //=================================================================================================================
    // LOGIN TOKEN
    //=================================================================================================================

    public String getLoginToken ()
    {
//        try
//        {
//            Path token_file = Paths.get (system_home, "cache/login-token.txt");
//            return (new String (Files.readAllBytes (token_file), StandardCharsets.UTF_8));
//        }
//        catch (IOException e)
//        {
//            return (null);
//        }
        return ("OPENSESAME");
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

    private boolean launch_cmdline (CommandLine cmdline)
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
            if (daemon_mode)
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

    // TODO: THROW ALL THIS CONFIGS INTO SOME XML/WHATEVER ON CONF DIR
    private void launch (String[] args)
    {
        System.out.println ("Exec: " + java_exe);

        CommandLine cmdline = new CommandLine (java_exe);

        //----------
        // JVM args
        //----------

        cmdline.addArgument ("-server");
        cmdline.addArgument ("-Xms128M");
        cmdline.addArgument ("-Xmx1024M");
        cmdline.addArgument ("-XX:+UnlockDiagnosticVMOptions");
        cmdline.addArgument ("-XX:+UnsyncloadClass");
        cmdline.addArgument ("-Djava.awt.headless=true");
        cmdline.addArgument ("-Dcom.sun.management.jmxremote");

        //-------------
        // LucidJ args
        //-------------

        if (System.getProperty ("user.conf") != null)
        {
            addArgument (cmdline, "user.conf", System.getProperty ("user.conf"));
        }

        // Class to exec
        cmdline.addArgument ("-jar Kernel.jar");

        // Add class arguments
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

        if (verbose)
        {
            String[] argv = cmdline.toStrings ();

            for (int i = 0; i < argv.length; i++)
            {
                System.out.println ("argv[" + i + "] = '" + argv [i] + "'");
            }
        }

        launch_cmdline (cmdline);
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

    public void start (String[] args)
    {
        //main_class = karaf_properties.getProperty (".main.class.start");
        daemon_mode = true;
        launch (args);
    }

    public void stop (String[] args)
    {
        //main_class = karaf_properties.getProperty (".main.class.stop");
        daemon_mode = false;
        launch (args);
    }

    public void status (String[] args)
    {
        //main_class = karaf_properties.getProperty (".main.class.status");
        daemon_mode = false;
        launch (args);
    }

//    public boolean launch_gui ()
//    {
//        try
//        {
//            CommandLine cmdline = new CommandLine (java_exe);
//            cmdline.addArgument ("-jar");
//            cmdline.addArgument (System.getProperty ("app.launcher.jar"));
//            launch_cmdline (cmdline);
//            return (true);
//        }
//        catch (Exception ignore) {};
//
//        return (false);
//    }
}

// EOF
