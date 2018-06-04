/*
 * Copyright 2017 NEOautus Ltd. (http://neoautus.com)
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

package org.lucidj.ladmin;

public class TinyLog
{
    public final static int LOG_OFF   = 0;
    public final static int LOG_ERROR = 1;
    public final static int LOG_WARN  = 2;
    public final static int LOG_INFO  = 3;
    public final static int LOG_DEBUG = 4;

    public final static String[] LOG_LEVELS =
    {
        "OFF", "ERROR", "WARN", "INFO", "DEBUG"
    };

    private int logger_level = 4;
    private String logger_name = "";

    public TinyLog (Class log_class)
    {
        logger_name = log_class.getSimpleName();
        logger_level = getConfiguredLogLevel (logger_name);
    }

    public static int getConfiguredLogLevel (String logger_name)
    {
        String tinylog_env_default = System.getenv ("TINYLOG");
        String tinylog_default = System.getProperty ("tinylog",
            tinylog_env_default == null? LOG_LEVELS[LOG_INFO]: tinylog_env_default);
        String tinylog_property = System.getProperty ("tinylog_" + logger_name, tinylog_default).toUpperCase();

        if (logger_name == null)
        {
            return (LOG_INFO);
        }

        for (int level = 0; level < LOG_LEVELS.length; level++)
        {
            if (LOG_LEVELS[level].equals (tinylog_property))
            {
                return (level);
            }
        }

        // The log is disabled by default
        return (LOG_OFF);
    }

    private String conv_str (Object obj)
    {
        if (obj == null)
        {
            return ("null");
        }
        else if (obj instanceof Object[])
        {
            Object[] obj_list = (Object[])obj;
            StringBuilder sb = new StringBuilder ("[");

            for (int i = 0; i < obj_list.length; i++)
            {
                if (sb.length() > 0)
                {
                    sb.append (",");
                }
                sb.append (conv_str (obj_list [i]));
            }
            return (sb.append ("]").toString ());
        }
        return (obj.toString ());
    }

    private void write_log (int level, String msg, Object... args)
    {
        if (level > logger_level)
        {
            return;
        }

        for (int pos, i = 0; i < args.length && (pos = msg.indexOf ("{}")) != -1; i++)
        {
            msg = msg.substring (0, pos) + conv_str (args [i]) + msg.substring (pos + 2);
        }

        StringBuilder sb = new StringBuilder ();
        sb.append ("[");
        sb.append (logger_name);
        sb.append ("] ");
        sb.append (LOG_LEVELS[level]);
        sb.append (": ");
        sb.append (msg);

        // Let's assume it's always and the end of the list
        if (args.length > 0 && args [args.length - 1] instanceof Throwable)
        {
            sb.append (" - ");
            sb.append (args [args.length - 1].toString ());
        }
        System.out.println (sb.toString ());
    }

    public void debug (String msg, Object... args)
    {
        write_log (LOG_DEBUG, msg, args);
    }

    public void info (String msg, Object... args)
    {
        write_log (LOG_INFO, msg, args);
    }

    public void warn (String msg, Object... args)
    {
        write_log (LOG_WARN, msg, args);
    }

    public void error (String msg, Object... args)
    {
        write_log (LOG_ERROR, msg, args);
    }
}

// EOF
