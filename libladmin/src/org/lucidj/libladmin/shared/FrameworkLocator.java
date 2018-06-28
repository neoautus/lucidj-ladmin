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

package org.lucidj.libladmin.shared;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.jar.JarFile;

public class FrameworkLocator
{
    public static File[] locateFrameworks (File jar_dir)
    {
        File[] file_array = jar_dir.listFiles (new FileFilter()
        {
            @Override
            public boolean accept (File file)
            {
                if (!file.getName().toLowerCase ().endsWith (".jar"))
                {
                    return (false);
                }

                try
                {
                    // We want only jars with OSGi framework available
                    JarFile jar = new JarFile (file);
                    return (jar.getJarEntry ("META-INF/services/org.osgi.framework.launch.FrameworkFactory") != null);
                }
                catch (IOException e)
                {
                    //log.debug ("Exception reading {}: {}", file, e.toString());
                };
                return (false);
            }
        });

        if (file_array == null || file_array.length == 0)
        {
            return (null);
        }

        // Order latest version first
        Arrays.sort (file_array, Collections.reverseOrder (new AlphanumComparator ()));
        return (file_array);
    }
}

// EOF
