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

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.*;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class Main
{
    private final static String HANDLER_PREFIX = "embedded";
    private static URL root_jar_url;

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
                        //System.out.println ("--- openConnection: " + url);
                        return (new URL ("jar:" + root_jar_url + "!" + url.getPath ()).openConnection ());
                    }
                });
            }
        });
    }

    static List<URL> locate_jars (String dir)
    {
        URL embedded_dir = Main.class.getResource (dir);
        List<URL> found_jars = new ArrayList<> ();

        //---------------------------
        // Locate dir inside our jar
        //---------------------------

        if (embedded_dir == null)
        {
            System.err.println ("Embedded " + dir + " not found");
            return (null);
        }

        try (FileSystem jar_fs = FileSystems.newFileSystem (embedded_dir.toURI (), Collections.EMPTY_MAP))
        {
            Path embedded_dir_path = jar_fs.getPath (dir);

            if (embedded_dir_path == null)
            {
                System.out.println ("Embedded " + dir + " not available");
                return (null);
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
                        System.err.println ("Exception mapping " + embedded_jar.getFileName() + ":" + e.toString());
                    }
                }
            }
        }
        catch (URISyntaxException | IOException e)
        {
            System.err.println ("Exception searching bundles on " + embedded_dir + ":" + e.toString());
            return (null);
        }
        return (found_jars);
    }

    @SuppressWarnings ("unchecked")
    public static void main (String[] args)
    {
        List<URL> jar_lib_list = locate_jars ("/libraries");
        List<URL> jar_url_list = locate_jars ("/commands");
        jar_url_list.addAll (jar_lib_list);
        URL[] jar_url_array = jar_url_list.toArray (new URL [jar_url_list.size ()]);

        for (URL url: jar_url_array)
        {
            System.out.println ("===> " + url.toString());
        }

        URLClassLoader url_cld = new URLClassLoader (jar_url_array);
        Class cls;

        try
        {
            cls = url_cld.loadClass ("org.lucidj.shell.Shell");
            System.out.println ("SHELL = " + cls);
            Method method = cls.getMethod ("main", args.getClass());
            method.setAccessible(true);
            int mods = method.getModifiers();

            if (method.getReturnType() != void.class || !Modifier.isStatic(mods) || !Modifier.isPublic(mods))
            {
                System.out.println ("Main entrypoint not found.");
                return;
            }

            method.invoke (null, new Object[] { args });
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}

// EOF
