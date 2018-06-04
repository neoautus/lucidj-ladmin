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

package org.lucidj.ladmin.main;

import org.lucidj.ladmin.TinyLog;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.*;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

public class Main
{
    private final static TinyLog log = new TinyLog (Main.class);

    private final static String HANDLER_PREFIX = "embedded";
    private static URL root_jar_url;
    private static List<URL> jar_libraries_list;
    private static List<URL> jar_commands_list;

    static
    {
        //----------------------------------------------------------------------------
        // When we embed jars, the embedded url have a '!' section which is also used
        // on URLClassLoader for other purposes. This conflict breaks our references,
        // so we need a way to reference the embedded jars without the '!' trick.
        // We use the bare-bones protocol handler below, which allows us to reference
        // embedded jars using an URL like:
        //
        //     embedded:/<directory>/<file>.jar
        //
        // This to allows URLClassLoader to work properly :)
        //----------------------------------------------------------------------------

        root_jar_url = Main.class.getProtectionDomain ().getCodeSource ().getLocation ();

        URL.setURLStreamHandlerFactory (new URLStreamHandlerFactory()
        {
            public URLStreamHandler createURLStreamHandler (String protocol)
            {
                if (!HANDLER_PREFIX.equals (protocol))
                {
                    return (null);
                }
                return (new URLStreamHandler ()
                {
                    protected URLConnection openConnection (URL url)
                        throws IOException
                    {
                        log.trace ("URLStreamHandler->openConnection: {}", url);
                        return (new URL ("jar:" + root_jar_url + "!" + url.getPath ()).openConnection ());
                    }
                });
            }
        });
    }

    static List<URL> locate_jars (String dir)
    {
        // These jars are NOT optional
        return (locate_jars (dir, false));
    }

    static List<URL> locate_jars (String dir, boolean optional)
    {
        URL embedded_dir = Main.class.getResource (dir);
        List<URL> found_jars = new ArrayList<> ();
        int log_level = optional? TinyLog.LOG_INFO: TinyLog.LOG_ERROR;

        //---------------------------
        // Locate dir inside our jar
        //---------------------------

        if (embedded_dir == null)
        {
            // We return an empty list on error
            log.log (log_level, "Embedded directory {} not found", dir);
            return (optional? found_jars: null);
        }

        try (FileSystem jar_fs = FileSystems.newFileSystem (embedded_dir.toURI (), Collections.EMPTY_MAP))
        {
            Path embedded_dir_path = jar_fs.getPath (dir);

            if (embedded_dir_path == null)
            {
                // We return an empty list on error
                log.log (log_level, "Embedded directory {} not available", dir);
                return (optional? found_jars: null);
            }
            Stream<Path> walk = Files.walk (embedded_dir_path, 1);

            //------------------
            // List dir entries
            //------------------

            for (Iterator<Path> it = walk.iterator(); it.hasNext();)
            {
                Path embedded_jar = it.next ();
                String embedded_jar_filename = embedded_jar.getFileName ().toString ();

                if (embedded_jar_filename.endsWith (".jar"))
                {
                    // Install and start the embedded bundles
                    try
                    {
                        // I'm starting to think I like ugly tricks...
                        String jar_embedded_uri = HANDLER_PREFIX + ":" + dir + "/" + embedded_jar.getFileName();
                        URL jar_embedded_url = new URL (jar_embedded_uri);
                        found_jars.add (jar_embedded_url);
                    }
                    catch (MalformedURLException e)
                    {
                        log.warn ("Exception mapping {}: {}", embedded_jar.getFileName(), e.toString());
                    }
                }
            }
        }
        catch (URISyntaxException | IOException e)
        {
            // Here we may have a serious issue, so we return null
            log.log (log_level, "Exception searching bundles on {}: {}", embedded_dir, e.toString());
            return (optional? found_jars: null);
        }
        return (found_jars);
    }

    private static URL get_jar_by_name (String name)
    {
        for (URL url: jar_commands_list)
        {
            String jar_file_name = url.getFile ().toLowerCase ();
            int dot_jar_index = jar_file_name.lastIndexOf (".jar");

            if (dot_jar_index == -1)
            {
                continue;
            }

            String jar_name = jar_file_name.substring (jar_file_name.lastIndexOf ('/') + 1, dot_jar_index);

            log.debug ("url file={} jar_name={}", url.getFile(), jar_name);

            if (jar_name.equals (name))
            {
                log.debug ("name={} found {}", name, url);
                return (url);
            }
        }
        return (null);
    }

    private static String search_main_class_by_name (ClassLoader classloader, URL jar_url, String class_name)
    {
        log.debug ("jar_url={} class_name={}", jar_url, class_name);

        try (JarInputStream jar_is = new JarInputStream (jar_url.openStream()))
        {
            JarEntry jar_entry;

            while ((jar_entry = jar_is.getNextJarEntry()) != null)
            {
                String entry_name = jar_entry.getName ();

                int dot_class_index = entry_name.lastIndexOf (".class");

                if (dot_class_index == -1)
                {
                    // Skip non-class files
                    continue;
                }

                String short_class_name = entry_name.substring (entry_name.lastIndexOf ('/') + 1, dot_class_index);
                String full_class_name = entry_name.substring (0, dot_class_index).replace ('/', '.');
                log.debug ("Verifying {} -> {} / {}",
                    entry_name, short_class_name, full_class_name);

                if (!short_class_name.toLowerCase ().equals (class_name)
                    && !full_class_name.toLowerCase ().equals (class_name))
                {
                    continue;
                }

                // We make great effor to keep embedded jars isolated, but we DON'T TRY
                // to support multiple jars with the _same_ main class name. We would need
                // to isolate the command jars during the search phase, which is overkill.
                // The condition below will be true only if the class have a valid main().
                if (get_jar_entry_point (classloader, full_class_name) != null)
                {
                    log.debug ("Found {}", full_class_name);
                    return (full_class_name);
                }
            }
        }
        catch (IOException e)
        {
            log.warn ("Exception on {}: {}", jar_url.toString(), e.toString());
        }
        return (null);
    }

    private static Method get_jar_entry_point (ClassLoader classloader, String class_name)
    {
        Class cls;
        Method method;

        try
        {
            cls = classloader.loadClass (class_name);
        }
        catch (ClassNotFoundException e)
        {
            return (null);
        }

        log.debug ("Loaded {}", cls);

        try
        {
            method = cls.getMethod ("main", String[].class);
        }
        catch (NoSuchMethodException e)
        {
            return (null);
        }

        method.setAccessible(true);
        int mods = method.getModifiers();

        if (method.getReturnType () == void.class && Modifier.isStatic (mods) && Modifier.isPublic (mods))
        {
            log.debug ("Valid main() found: {}", method);
            return (method);
        }
        return (null);
    }

    private static String get_jar_main_class (ClassLoader classloader, URL jar_url)
    {
        try (JarInputStream jar_is = new JarInputStream (jar_url.openStream ()))
        {
            Manifest jar_mf = jar_is.getManifest ();

            if (jar_mf != null)
            {
                Attributes attrs = jar_mf.getMainAttributes ();
                String main_class = attrs.getValue ("Main-Class");

                if (get_jar_entry_point (classloader, main_class) != null)
                {
                    log.debug ("jar_url={} main_class={}", jar_url, main_class);
                    return (main_class);
                }
            }
        }
        catch (IOException ignore) {};
        return (null);
    }

    public static void main (String[] args)
    {
        Instant start_timestamp = Instant.now ();

        jar_libraries_list = locate_jars ("/libraries");
        List<URL> plugins_cmd_list = locate_jars ("/plugins", true);
        List<URL> internal_cmd_list = locate_jars ("/commands");

        // Ensure no serious errors around
        if (jar_libraries_list == null || plugins_cmd_list == null || internal_cmd_list == null)
        {
            // Abort - the error message was already printed
            System.exit (1);
        }

        // Command plugins have search order precedence over the internal commands
        jar_commands_list = new ArrayList<> (plugins_cmd_list);
        jar_commands_list.addAll (internal_cmd_list);

        // We create a classloader with all the jars for command lookup
        List<URL> jar_full_list = new ArrayList<> (jar_libraries_list);
        jar_full_list.addAll (jar_commands_list);
        URL[] jar_full_array = jar_full_list.toArray (new URL [jar_full_list.size ()]);
        ClassLoader full_classloader = new URLClassLoader (jar_full_array);

        if (args.length == 0)
        {
            // We need to scan all command jars, looking for commands and print them
            System.out.println ("TODO: Print command list and help");
            System.exit (0);
        }

        // Extract the command we should run and shift the arguments
        String command = args [0];
        String[] command_args = Arrays.copyOfRange (args, 1, args.length);
        log.debug ("Locating command '{}' inside {}", command, root_jar_url);

        // Heuristics
        //
        // 1) Try to find a self-executable jar with the same name as the command and NOT from LucidJ
        //    -> Call the class specified in the manifest.mf
        // 2) Try to find a self-executable jar with the same name as the command and from LucidJ
        //    -> Call the class specified in the manifest.mf
        // 3) Try to find any jar with class short name equal to command, with main(), and NOT from LucidJ
        //    -> Call the found class main() directly
        // 4) Try to find any jar with class short name equal to command, with main() and from LucidJ
        //    -> Call the found class main() directly
        //

        //--------------------
        // Search by jar name
        //--------------------

        URL command_jar_url = get_jar_by_name (command);
        String command_main_class = null;

        if (command_jar_url != null)
        {
            command_main_class = get_jar_main_class (full_classloader, command_jar_url);
        }

        //----------------------
        // Search by class name
        //----------------------

        if (command_main_class == null)
        {
            for (URL jar_url: jar_commands_list)
            {
                if ((command_main_class = search_main_class_by_name (full_classloader, jar_url, command)) != null)
                {
                    command_jar_url = jar_url;
                }
            }
        }

        log.debug ("command_jar_url = {}", command_jar_url);
        log.debug ("command_main_class = {}", command_main_class);

        if (command_jar_url == null)
        {
            System.err.println ("Error: Command '" + command + "' not found");
            return;
        }

        //------------------------------------------------------------------------
        // Run main() with a classpath composed only of libraries and command jar
        //------------------------------------------------------------------------

        List<URL> jar_run_list = new ArrayList<> (jar_libraries_list);
        jar_run_list.add (0, command_jar_url);
        URL[] jar_run_array = jar_run_list.toArray (new URL [jar_run_list.size ()]);
        URLClassLoader run_classloader = new URLClassLoader (jar_run_array);
        Method main = get_jar_entry_point (run_classloader, command_main_class);
        log.debug ("Will invoke method: {}", main);

        if (main == null)
        {
            System.err.println ("Error: Valid main() not found on: " + command_main_class);
            System.exit (1);
        }

        long loading_time = Duration.between (start_timestamp, Instant.now ()).toMillis ();
        log.info ("Loading time: {}ms", String.format ("%d.%03d", loading_time / 1000, loading_time % 1000));

        try
        {
            main.invoke (null, new Object[] { command_args });
        }
        catch (InvocationTargetException | IllegalAccessException e)
        {
            System.err.println ("Exception calling main(): " + e.toString());
            System.exit (1);
        }
    }
}

// EOF
