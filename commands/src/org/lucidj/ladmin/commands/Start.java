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

package org.lucidj.ladmin.commands;

import org.lucidj.admind.shared.AdmindUtil;
import org.lucidj.libladmin.JdkLocator;
import org.lucidj.libladmin.Launcher;
import org.lucidj.libladmin.shared.FrameworkLocator;
import org.lucidj.libladmin.shared.TinyLog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class Start
{
    private final static TinyLog log = new TinyLog (Start.class);

    private enum Operations
    {
        START_SINGLE, START_SERVER, STOP, STATUS
    };

    public static void main (String[] args)
    {
        String sys_home = null;
        String jdk_home = null;
        Operations arg_operation = Operations.STATUS;
        boolean arg_dry_run = false;
        boolean arg_verbose = false;

        for (int pos = 0; pos < args.length; pos++)
        {
            String arg = args [pos];
            String param = null;

            if (arg.contains ("="))
            {
                // the option is inline with the argument
                int eq_pos = arg.indexOf ("=");
                param = arg.substring (eq_pos + 1);
                arg = arg.substring (0, eq_pos);
            }

            //------------------------------
            // Arguments WITHOUT parameters
            //------------------------------
            switch (arg)
            {
                case "single":
                {
                    arg_operation = Operations.START_SINGLE;
                    arg = null;
                    break;
                }
                case "start":
                case "server":
                {
                    arg_operation = Operations.START_SERVER;
                    arg = null;
                    break;
                }
                case "stop":
                {
                    arg_operation = Operations.STOP;
                    arg = null;
                    break;
                }
                case "status":
                {
                    arg_operation = Operations.STOP;
                    arg = null;
                    break;
                }
                case "--dry":
                {
                    arg_dry_run = true;
                    arg = null;
                    break;
                }
                case "--verbose":
                case "-v":
                {
                    arg_verbose = true;
                    log.setLogLevel (TinyLog.LOG_DEBUG);
                    arg = null;
                    break;
                }
            }

            if (arg == null)
            {
                if (param != null)
                {
                    System.err.println ("Error: Argument doesn't requires parameter in '" + args [pos] + "'");
                    System.exit (1);
                }
                // The argument was parsed, proceed to next
                continue;
            }

            if (param == null)
            {
                if (pos + 1 == args.length)
                {
                    System.err.println ("Error: Argument needs parameter in '" + arg + "'");
                    System.exit (1);
                }
                // We'll need a parameter for the next arguments
                pos++;
                param = args [pos];
            }

            //---------------------------
            // Arguments WITH parameters
            //---------------------------
            switch (arg)
            {
                case "--jdk":
                {
                    jdk_home = param;
                    arg = null;
                    break;
                }
                case "--home":
                {
                    sys_home = param;
                    arg = null;
                    break;
                }
            }

            // If arg is not null, it was NOT recognized as single option nor option with parameters
            if (arg != null)
            {
                System.err.println ("Error: Unknown argument '" + arg + "'");
                System.exit (1);
            }
        }

        String def_server_name = AdmindUtil.getServerName ();
        String admind = AdmindUtil.initAdmindDir ();

        if (admind != null)
        {
            // TODO: SHOULD WE HAVE AN "ENSURE RUNNING"?
            System.out.println ("Error: Server '" + def_server_name + "' is already running");
            System.exit (1);
        }

        // For java -jar it is the jar file itself
        File jar_file = Paths.get (System.getProperty ("java.class.path")).toFile ();
        File jar_dir = jar_file.getParentFile ();
        File[] file_array = FrameworkLocator.locateFrameworks (jar_dir);

        if (file_array == null)
        {
            System.out.println ("Error: Couldn't find OSGi framework jar on " + jar_dir);
            System.exit (1);
        }

        // Get latest version
        File framework = file_array [0];

        System.out.println ("Selected framework: " + framework);

        if (!JdkLocator.configure (jar_dir.getAbsolutePath ()))
        {
            System.out.println ("Error: Couldn't find a proper JDK on " + jar_dir);
            System.exit (1);
        }

        Launcher launcher = Launcher.newLauncher (JdkLocator.getJavaCommand ());

        try
        {
            launcher.launchJavaJar (framework.getCanonicalPath (), args, true);
        }
        catch (IOException e)
        {
            System.out.println ("Error: Exception launching jar " + framework.toString() + ": " + e.toString ());
            System.exit (1);
        }
    }
}

// EOF
