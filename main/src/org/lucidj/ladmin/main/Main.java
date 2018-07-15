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

import org.lucidj.libladmin.shared.FrameworkLocator;
import org.lucidj.libladmin.shared.TinyLog;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.*;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

public class Main
{
    private final static TinyLog log = new TinyLog (Main.class);

    private final static String DEFAULT_PROG_NAME = "ladmin";
    private final static Map<String, URI> handler_to_uri = new ConcurrentHashMap<>();
    private static URL root_jar_url;
    private static List<URL> jar_libraries_list;
    private static List<URL> jar_commands_list;
    private static URL command_jar_url;
    private static String command_main_class;

    private final static boolean RUNNING_ON_WINDOWS = System.getProperty ("os.name").startsWith ("Win");

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
            public URLStreamHandler createURLStreamHandler (final String protocol)
            {
                if (!handler_to_uri.containsKey (protocol))
                {
                    return (null);
                }
                return (new URLStreamHandler ()
                {
                    protected URLConnection openConnection (URL url)
                        throws IOException
                    {
                        log.trace ("URLStreamHandler->openConnection: {}", url);
                        return (new URL ("jar:" + handler_to_uri.get (protocol) + "!" + url.getPath ()).openConnection ());
                    }
                });
            }
        });
    }

    static List<URL> locate_jars (URI source_jar, String dir)
    {
        // These jars are NOT optional
        return (locate_jars (source_jar, dir, false));
    }

    static List<URL> locate_jars (URI source_jar, String dir, boolean optional)
    {
        URI embedded_dir;
        List<URL> found_jars = new ArrayList<> ();
        int log_level = optional? TinyLog.LOG_INFO: TinyLog.LOG_ERROR;

        try
        {
            embedded_dir = new URI ("jar:" + source_jar + "!" + dir);
        }
        catch (URISyntaxException e)
        {
            // We return an empty list on error
            log.log (log_level, "Embedded directory {} not found: {}", dir, e.toString());
            return (optional? found_jars: null);
        }

        // Build fake protocol handler using a prefix plus jar hash code
        String handler = "jarjar-" + Integer.toHexString (source_jar.hashCode ());
        handler_to_uri.put (handler, source_jar);
        log.debug ("Protocol '{}' -> {}", handler, source_jar);

        try (FileSystem jar_fs = FileSystems.newFileSystem (embedded_dir, Collections.emptyMap ()))
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
                        String jar_embedded_uri = handler + ":" + dir + "/" + embedded_jar.getFileName();
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
        catch (IOException e)
        {
            // Here we may have a serious issue, so we return null
            log.log (log_level, "Exception searching bundles on {}: {}", embedded_dir, e.toString());
            return (optional? found_jars: null);
        }

        for (int i = 0; i < found_jars.size (); i++)
        {
            log.debug ("Directory {} URL[{}]: {}", dir, i, found_jars.get (i));
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

                if (!short_class_name.toLowerCase ().equals (class_name.toLowerCase())  // Short class names are case independent
                    && !full_class_name.equals (class_name))                            // Full class names may be mixed case
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
        Class<?> cls;
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

    private static boolean locate_command_on_jar (String command, URI source_jar, boolean optional)
    {
        jar_libraries_list = locate_jars (source_jar, "/libraries", optional);
        List<URL> plugins_cmd_list = locate_jars (source_jar, "/plugins", true);
        List<URL> internal_cmd_list = locate_jars (source_jar, "/commands", optional);

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

        command_jar_url = get_jar_by_name (command);
        command_main_class = null;

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
                    break;
                }
            }
        }

        log.debug ("Found command_jar_url => {}", command_jar_url);
        log.debug ("Found command_main_class => {}", command_main_class);
        return (command_jar_url != null);
    }

    public static void main (String[] args)
    {
        long start_timestamp = System.currentTimeMillis ();

        String command;
        String[] command_args;
        URI root_jar_uri = null;

        try
        {
            root_jar_uri = root_jar_url.toURI ();
        }
        catch (URISyntaxException e)
        {
            System.err.println ("Error: Unable to get '" + root_jar_url + "' URI location");
            System.exit (1);
        }

        if (args.length == 0)
        {
            String root_filename = root_jar_url.getFile ();
            String prog_name = root_filename.substring (root_filename.lastIndexOf ('/') + 1);

            // No args anyway
            command_args = args;

            // Strip .exe if needed
            if (RUNNING_ON_WINDOWS)
            {
                if (prog_name.substring (prog_name.length () - 4).equalsIgnoreCase (".exe"))
                {
                    prog_name = prog_name.substring (0, prog_name.length () - 4);
                }
            }

            if (!prog_name.equals (DEFAULT_PROG_NAME))
            {
                // The program name is not the default, use it as a command
                command = prog_name;
            }
            else if (!GraphicsEnvironment.isHeadless () || RUNNING_ON_WINDOWS)
            {
                // We should have a GUI available
                command = "gui";
            }
            else
            {
                // We need to scan all command jars, looking for commands and print them
                System.out.println ("TODO: Print command list and help");
                return;
            }
        }
        else
        {
            // Extract the command we should run and shift the arguments
            command = args [0];
            command_args = Arrays.copyOfRange (args, 1, args.length);
        }

        //-----------------------------------------------------------------
        // Locate the desired command inside this jar or the framework jar
        //-----------------------------------------------------------------

        // We assume all frameworks located alongside ladmin
        File jar_file = new File (root_jar_uri);
        File jar_dir = jar_file.getParentFile ();
        File[] available_framework_jars = FrameworkLocator.locateFrameworks (jar_dir);
        boolean command_found_inside_framework = false;

        if (available_framework_jars != null)
        {
            // We have at least 1 framework jar available, try the latest
            URI framework_jar = available_framework_jars [0].toURI();
            log.debug ("Locating command '{}' inside {}", command, framework_jar);
            command_found_inside_framework = locate_command_on_jar (command, framework_jar, true);
        }

        // The commands found inside the framework jar have precedence over the built-in commands
        if (!command_found_inside_framework)
        {
            log.debug ("Locating command '{}' inside {}", command, root_jar_uri);

            if (!locate_command_on_jar (command, root_jar_uri, false))
            {
                System.err.println ("Error: Command '" + command + "' not found");
                System.exit (1);
            }
        }

        //------------------------------------------------------------------------
        // Run main() with a classpath composed only of libraries and command jar
        //------------------------------------------------------------------------

        // Search class path: command jar first, followed by libraries
        List<URL> jar_run_list = new ArrayList<> (jar_libraries_list);
        jar_run_list.add (0, command_jar_url);

        // If the command is inside the framework, it takes search precedence
        if (command_found_inside_framework)
        {
            try
            {
                jar_run_list.add (0, available_framework_jars [0].toURI ().toURL ());
            }
            catch (MalformedURLException e)
            {
                System.err.println ("Error: Framework '" + available_framework_jars [0] + "' generates " + e.toString());
                System.exit (1);
            }
        }

        // Create the classloader
        URL[] jar_run_array = jar_run_list.toArray (new URL [jar_run_list.size ()]);
        URLClassLoader run_classloader = new URLClassLoader (jar_run_array);
        Method main = get_jar_entry_point (run_classloader, command_main_class);

        log.debug ("Will invoke method: {}", main);
        log.debug ("Using classloader: {}", run_classloader);
        for (int i = 0; i < jar_run_array.length; i++)
        {
            log.debug ("Classpath URL[{}]: {}", i, jar_run_array [i]);
        }

        if (main == null)
        {
            System.err.println ("Error: Valid main() not found on: " + command_main_class);
            System.exit (1);
        }

        long loading_time = System.currentTimeMillis () - start_timestamp;
        log.info ("Loading time: {}ms", String.format ("%d.%03d", loading_time / 1000, loading_time % 1000));

        try
        {
            main.invoke (null, new Object[] { command_args });
        }
        catch (IllegalAccessException e)
        {
            System.err.println ("Error: Unable to invoke main(): " + e.toString ());
            System.exit (1);
        }
        catch (InvocationTargetException e)
        {
            // TODO: STORE LAST EXCEPTION INFO TO BE ABLE TO 'ladmin report'
            Throwable cause = e.getCause ();
            System.err.println ("---");
            System.err.println ("Exception thrown by " + command + ": " + cause.toString ());
            System.err.println ("Module : " + command_jar_url);
            System.err.println ("Class  : " + command_main_class);
            System.err.print   ("Args   :");
            if (command_args.length == 0)
            {
                System.err.println (" <none>");
            }
            else
            {
                for (int i = 0; i < command_args.length; i++)
                {
                    System.err.print (" " + i + "='" + command_args [i] + "'");
                }
                System.err.println ();
            }
            cause.printStackTrace (System.err);
            System.exit (1);
        }
    }
}

// EOF
