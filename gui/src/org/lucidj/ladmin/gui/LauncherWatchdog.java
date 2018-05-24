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

import org.apache.commons.exec.ExecuteWatchdog;

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

// EOF
