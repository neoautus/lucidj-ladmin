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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JdkLocator
{
    private static String jdk_home;
    private static File jdk_bin_java;

    private static boolean javac_exists (String possible_jdk_home)
    {
        Path javac_path = Paths.get (possible_jdk_home, "bin", "javac" + Configuration.EXE_SUFFIX);
        File javac_file = javac_path.toFile ();

        return (javac_file.exists () && !javac_file.isDirectory ());
    }

    private static boolean get_embedded_jdk_home (String basedir)
    {
        if (basedir == null)
        {
            if ((basedir = System.getProperty ("user.home")) == null)
            {
                return (false);
            }
        }

        // Not yet. Lets search inside runtime dir
        File[] file_list = new File (basedir).listFiles ();

        if (file_list != null)
        {
            for (File file: file_list)
            {
                if (file.isDirectory() && javac_exists (file.getAbsolutePath ()))
                {
                    // Embedded jdk_home
                    jdk_home = file.getAbsolutePath ();
                    return (true);
                }
            }
        }
        return (false);
    }

    private static boolean get_system_jdk_home ()
    {
        if (jdk_home != null)
        {
            if (!javac_exists (jdk_home))
            {
                System.err.println ("Error: Invalid JDK home '" + jdk_home + "'");
                System.exit (1);
            }
            return (true);
        }

        // We ignore a possible JAVA_HOME env, since we'll launch Karaf from
        // within this very java process.
        String java_home = System.getProperty ("java.home");

        if (javac_exists (java_home))
        {
            // We found jdk_home
            jdk_home = java_home;
            return (true);
        }
        else // JRE inside JDK?
        {
            int possible_jre_dir_pos = java_home.lastIndexOf (Configuration.FILE_SEPARATOR);

            if (possible_jre_dir_pos != -1)
            {
                java_home = java_home.substring (0, possible_jre_dir_pos);

                if (javac_exists (java_home))
                {
                    // We found jdk_home
                    jdk_home = java_home;
                    return (true);
                }
            }
        }
        return (false);
    }

    public static String getJDKHome ()
    {
        return (jdk_home);
    }

    public static void setJDKHome (String jdk_home)
    {
        JdkLocator.jdk_home = jdk_home;
    }

    public static File getJavaCommand ()
    {
        if (jdk_bin_java == null)
        {
            Path javac_path = Paths.get (jdk_home, "bin", "java" + Configuration.EXE_SUFFIX);
            jdk_bin_java = javac_path.toFile ();
        }
        return (jdk_bin_java);
    }

    public static boolean configure (String basedir)
    {
        return (get_system_jdk_home() || get_embedded_jdk_home (basedir));
    }
}

// EOF
