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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Configuration
{
    public static final String FILE_SEPARATOR = System.getProperty ("file.separator");
    public static final String PATH_SEPARATOR = System.getProperty ("path.separator");
    public static final String EXE_SUFFIX = System.getProperty("os.name").startsWith("Win")? ".exe": "";

    // Order:
    // Check $INSTALL/etc  -- defaults, low priority
    // Check /etc/lucidj   -- customizations, medium priority
    // Check $HOME/.lucidj -- user, high priority

    static private Path user_config_path;

    private static boolean path_exists (String path)
    {
        if (path != null)
        {
            return (Files.exists (Paths.get (path.trim ())));
        }
        return (false);
    }

    private static Path validate_config_path (String path, String appname)
    {
        // The path alone must exist
        if (!path_exists (path))
        {
            return (null);
        }

        // The appname dir requested may be created if it doesn't exists
        Path full_path = Paths.get (path.trim (), appname);

        if (!Files.exists (full_path))
        {
            try
            {
                // Create the missing App config directory
                Files.createDirectory (full_path);
            }
            catch (IOException e)
            {
                return (null);
            }
        }
        return (full_path);
    }

    private static Path find_config_dir (String appname)
    {
        String os_name = System.getProperty ("os.name").toLowerCase ();
        String user_home = System.getProperty ("user.home");
        Path config_path;

        if (os_name.startsWith ("win"))
        {
            // C:\Users\<username>\AppData\Local
            String config_dir = System.getenv ("LOCALAPPDATA");

            if (!path_exists (config_dir))
            {
                // Sensible default: C:\Documents and Settings\<username>
                config_dir = user_home;
            }

            // C:/Users/<username>/AppData/Local/<appname>
            config_path = validate_config_path (config_dir.replace ('\\', '/'), appname);
        }
        else if (os_name.startsWith ("mac"))
        {
            // /Users/<username>/Library/Application Support/<appname>
            config_path = validate_config_path (user_home + "/Library/Application Support", appname);
        }
        else // *nix
        {
            // /home/<username>/.config (... or whatever was set to be)
            String config_dir = System.getenv ("XDG_CONFIG_HOME");

            if (path_exists (config_dir))
            {
                // /home/<username>/.config/<appname>
                config_path = validate_config_path (config_dir, appname);
            }
            else // no XDG_CONFIG_HOME, let's fall back to defaults
            {
                // First let's try the location recommended by Freedesktop
                config_dir = user_home + "/.config";

                if (path_exists (config_dir))
                {
                    // /home/<username>/.config/<appname>
                    config_path = validate_config_path (config_dir, appname);
                }
                else // Something fishy is going on, let's get old-fashion
                {
                    // /home/<username>/.<appname>
                    config_path = validate_config_path (user_home, "." + appname);
                }
            }
        }
        return (config_path);
    }

    public static Path getConfigPath ()
    {
        if (user_config_path == null)
        {
            user_config_path = find_config_dir ("LucidJ");
        }
        return (user_config_path);
    }
}

// EOF
