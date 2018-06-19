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

package org.lucidj.ladmin.gui;

import org.lucidj.libladmin.Configuration;
import org.lucidj.libladmin.JdkLocator;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;

public class Main
{
    static String file_separator = System.getProperty ("file.separator");
    static String path_separator = System.getProperty ("path.separator");
    static String exe_suffix = System.getProperty ("os.name").startsWith ("Win")? ".exe": "";
    static String bin_dir = file_separator + "bin" + file_separator;
    static String cache_launcher_dir = file_separator + "cache" + file_separator + "launcher" + file_separator;

    static String sys_home = null;

    private static boolean check_path (String path)
    {
        int bin_pos = path.lastIndexOf (bin_dir);

        if (bin_pos != -1)
        {
            // Are we close to home?
            String probable_home = path.substring (0, bin_pos);

            File conf_dir = new File (probable_home + file_separator + "conf");
            File runtime_dir = new File (probable_home + file_separator + "runtime");

            if (conf_dir.exists () && conf_dir.isDirectory () &&
                    runtime_dir.exists () && runtime_dir.isDirectory ())
            {
                // It looks pretty much like home :)
                sys_home = probable_home;
                return (true);
            }
        }

        return (false);
    }

    private static boolean get_system_home ()
    {
        if (sys_home != null)
        {
            if (!check_path (sys_home))
            {
                System.err.println ("Error: Invalid system home '" + sys_home + "'");
                System.exit (1);
            }
            return (true);
        }

        String[] java_class_path = System.getProperty ("java.class.path").split (path_separator);
        String user_dir = System.getProperty ("user.dir") + file_separator;

        for (int i = 0; i < java_class_path.length; i++)
        {
            String classpath_element = java_class_path [i];

            if (!new File (classpath_element).isAbsolute ())
            {
                classpath_element = user_dir + classpath_element;
            }

            if (check_path (classpath_element))
            {
                return (true);
            }
        }

        // We may be using launcher.jar for development stage
        if (check_path (user_dir + "stage" + bin_dir))
        {
            return (true);
        }
        else if (check_path (user_dir.substring (0, user_dir.indexOf (cache_launcher_dir) + 1) + "stage" + bin_dir))
        {
            return (true);
        }

        // Not yet? One last try....
        return (check_path (user_dir));
    }

    private enum Operations
    {
        START_GUI, START_SINGLE, START_SERVER, STOP, STATUS
    };

    private static Operations arg_operation = Operations.START_GUI;
    private static boolean arg_verbose = false;
    private static boolean arg_dry_run = false;

    public static void main (String[] args)
    {
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
//                    TinyLog..Launcher.setVerbose (true);
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
                    JdkLocator.setJDKHome (param);
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

//        if (!get_system_home ())
//        {
//            System.err.println ("Error: Missing system home");
//            System.exit (1);
//        }

        if (!JdkLocator.configure (null))
        {
            System.err.println ("Error: Couldn't find a valid JDK");
            System.exit (1);
        }

        // No config path means Server Mode
        Path config_path = null;

        if (arg_operation == Operations.START_SINGLE || arg_operation == Operations.START_GUI)
        {
            // On Single-User Mode, the configuration dir belongs to the $USER (like $HOME/.config/LucidJ)
            config_path = Configuration.getConfigPath ();

            if (config_path == null)
            {
                System.err.println ("Error: Unable to access any configuration directory");
                System.err.println ("Is the configuration dir owned by the user?");
                System.exit (1);
            }
        }

        System.out.println ("System Home: " + sys_home);
        System.out.println ("JDK Home: " + JdkLocator.getJDKHome ());

        if (config_path != null)
        {
            System.out.println ("Config: Single-User Mode (" + config_path.toString () + ")");
        }
        else
        {
            System.out.println ("Config: Server Mode");
        }

        switch (arg_operation)
        {
            case START_SINGLE:
            case START_SERVER:
            {
                if (arg_dry_run)
                {
                    String mode = (arg_operation == Operations.START_SINGLE)? "single mode": "server mode";
                    System.out.println ("dry-run: Start system (" + mode + ")");
                }
                else
                {
//                    Launcher.newLauncher ().start (args);             ---
                }
                break;
            }
            case STOP:
            {
                if (arg_dry_run)
                {
                    System.out.println ("dry-run: Stop system");
                }
                else
                {
//                    Launcher.newLauncher ().stop (args);              ---
                }
                break;
            }
            case STATUS:
            {
                if (arg_dry_run)
                {
                    System.out.println ("dry-run: Show system status");
                }
                else
                {
//                    Launcher.newLauncher ().status (args);            ---
                }
                break;
            }
            case START_GUI:
            {
                if (arg_dry_run)
                {
                    System.out.println ("dry-run: Launch GUI");
                }
                else if (GraphicsEnvironment.isHeadless ())
                {
                    String bundled_jdk;

                    // Ok, we got a headless java install. Let's try to remedy this by
                    // using our own bundled java, if it's available
//                    if ((bundled_jdk = find_embedded_jdk ()) != null)
//                    {
//                        System.out.println ("Warning: Headless mode detected, trying to use bundled JDK");
//                        Launcher.configure (bundled_jdk, config_path);
//                        Launcher.newLauncher ().launch_gui ();
//                    }
//                    else
//                    {
//                        // TODO: ALSO WRITE A LOG SOMEWHERE
                        System.err.println ("Error: Headless mode detected");
                        System.err.println ("Please run Launcher GUI using a compatible JDK");
//                    }
                }
                else // Graphics Environment available
                {
                    // Launch the UI
                    java.awt.EventQueue.invokeLater (new Runnable()
                    {
                        public void run()
                        {
                            new LauncherUI ().setVisible (true);
                        }
                    });
                }
                break;
            }
            default:
            {
                System.err.println ("Warning: Unknown internal operation");
                break;
            }
        }
    }
}

// EOF
