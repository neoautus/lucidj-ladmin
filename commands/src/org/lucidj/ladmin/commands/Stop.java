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

import org.lucidj.ext.admind.AdmindUtil;

public class Stop
{
    public static void main (String[] args)
    {
        String def_server_name = AdmindUtil.getServerName ();
        String admind = AdmindUtil.initAdmindDir ();

        if (admind == null)
        {
            System.out.println ("Unable to find '" + def_server_name + "'");
            System.exit (1);
        }

        String request = AdmindUtil.asyncInvoke ("shutdown", "true");

        if (AdmindUtil.asyncWait (request))
        {
            String response = AdmindUtil.asyncResponse (request);
            System.out.println ("Shutdown '" + def_server_name + "': " + response.trim ());
        }
        else
        {
            String error = AdmindUtil.asyncError (request);
            System.out.println ("Error requesting shutdown for '" + def_server_name + "': " + error);
        }
    }
}

// EOF
