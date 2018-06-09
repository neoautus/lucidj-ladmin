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

package org.lucidj.ladmin.shared;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class TinyLog
{
    public final static int LOG_OFF   = 0;
    public final static int LOG_ERROR = 1;
    public final static int LOG_WARN  = 2;
    public final static int LOG_INFO  = 3;
    public final static int LOG_DEBUG = 4;
    public final static int LOG_TRACE = 5;

    private final static int default_logger_level = LOG_ERROR;
    private final static long complain_interval = 60 * 5 * 1000;    // Complain every 5 minutes
    private final static long complain_window = 5 * 1000;           // Complain during 5 seconds

    private Map<Object, Long> whiners = new HashMap<> ();           // Who complained when?

    public final static String[] LOG_LEVELS =
    {
        "OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE"
    };

    private SimpleDateFormat timestamp_format_info = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss ");
    private SimpleDateFormat timestamp_format_debug = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSS ");
    private int logger_level = 4;
    private String logger_name = "";

    public TinyLog (Class log_class)
    {
        logger_name = log_class.getSimpleName();
        logger_level = getConfiguredLogLevel (logger_name);
    }

    public void setLogLevel (int level)
    {
        logger_level = level;
    }

    public int getLogLevel ()
    {
        return (logger_level);
    }

    public boolean isDebug ()
    {
        return (logger_level >= LOG_DEBUG);
    }

    public static int getConfiguredLogLevel (String logger_name)
    {
        String tinylog_env_default = System.getenv ("TINYLOG");
        String tinylog_default = System.getProperty ("tinylog",
            tinylog_env_default == null? LOG_LEVELS[default_logger_level]: tinylog_env_default);
        String tinylog_property = System.getProperty ("tinylog_" + logger_name, tinylog_default).toUpperCase ();

        if (logger_name == null)
        {
            return (default_logger_level);
        }

        for (int level = 0; level < LOG_LEVELS.length; level++)
        {
            if (LOG_LEVELS [level].equals (tinylog_property))
            {
                return (level);
            }
        }
        return (default_logger_level);
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

    public StackTraceElement get_location ()
    {
        final String logger_class = TinyLog.class.getName ();
        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();

        StackTraceElement last = null;

        for (int i = stackTrace.length - 1; i > 0; i--)
        {
            if (logger_class.equals (stackTrace [i].getClassName()))
            {
                return (last);
            }
            last = stackTrace [i];
        }
        return (null);
    }

    public void log (int level, String msg, Object... args)
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

        if (logger_level > LOG_INFO)
        {
            sb.append (timestamp_format_debug.format (new Date ()));
        }
        else
        {
            sb.append (timestamp_format_info.format (new Date ()));
        }
        sb.append (LOG_LEVELS[level]);
        sb.append (' ');

        StackTraceElement loc = get_location ();

        if (logger_level > LOG_INFO && loc != null && loc.getFileName () != null)
        {
            sb.append (loc.getClassName ().substring (loc.getClassName ().lastIndexOf ('.') + 1));

            if (loc.getLineNumber () > 0)
            {
                sb.append (':');
                sb.append (loc.getLineNumber ());
            }
        }
        else
        {
            sb.append (logger_name);
        }

        if (logger_level > LOG_INFO && loc != null && loc.getMethodName () != null)
        {
            sb.append (' ');
            sb.append (loc.getMethodName ());
        }
        sb.append (": ");
        sb.append (msg);

        // Let's assume it's always and the end of the list
        if (args.length > 0 && args [args.length - 1] instanceof Throwable)
        {
            sb.append (" - ");
            sb.append (args [args.length - 1].toString ());
        }
        System.out.println (sb.toString ());
        System.out.flush ();
    }

    public void complain (Object whiner, String msg, Object... args)
    {
        // Start complaining if time enough has passed (default 5 minutes)
        if (whiners.getOrDefault (whiner, -1L) + complain_interval < System.currentTimeMillis ())
        {
            whiners.put (whiner, System.currentTimeMillis ());
            warn (msg, (Object)args);
        }

        // Keep complaining for a time window (default 5 seconds).
        // This allow useful complain messages to be caught up,
        // instead of printing just one and stopping.
        if (whiners.get (whiner) + complain_window > System.currentTimeMillis())
        {
            warn (msg, (Object)args);
        }
    }

    public void trace (String msg, Object... args)
    {
        log (LOG_TRACE, msg, args);
    }

    public void debug (String msg, Object... args)
    {
        log (LOG_DEBUG, msg, args);
    }

    public void info (String msg, Object... args)
    {
        log (LOG_INFO, msg, args);
    }

    public void warn (String msg, Object... args)
    {
        log (LOG_WARN, msg, args);
    }

    public void error (String msg, Object... args)
    {
        log (LOG_ERROR, msg, args);
    }
}

// EOF
