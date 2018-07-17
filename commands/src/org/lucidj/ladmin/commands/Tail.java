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

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.lucidj.admind.shared.AdmindUtil;
import org.lucidj.libladmin.shared.Ansify;

import static org.lucidj.libladmin.shared.Ansify.*;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class Tail
{
    private final static int TAIL_COLORS = FG_BLUE;
    private final static int INFO_COLORS = 0;
    private final static int DEBUG_COLORS = FG_GRAY;
    private final static int WARN_COLORS = FG_LIGHTBLUE | FG_BOLD;
    private final static int ERROR_COLORS = FG_RED | FG_BOLD;

    private static String admind;
    private static Properties admind_properties;

    private static String system_log;
    private static long system_log_inode;
    private static byte[] buffer = new byte [8192];

    private static Terminal terminal;
    private static Attributes saved_attributes = null;
    private static boolean logout_terminal;

    private static DataInputStream remote_in;

    private static void println (String message, int ansi_colors)
    {
        terminal.writer ().println (Ansify.toAnsi (message, ansi_colors));
        terminal.flush ();
    }

    public static void main (String[] args)
    {
        // Default server to tail
        String def_server_name = AdmindUtil.getServerName ();
        String server_jvmid;
        String server_jvmid_str = null;
        long silence_on_the_library = 0;

        try
        {
            //---------------------------
            // Init terminal and session
            //---------------------------

            terminal = TerminalBuilder.builder ()
                .name ("gogo")
                .system (true)
                .nativeSignals (true)
                .signalHandler (Terminal.SignalHandler.SIG_IGN)
                .build ();
            //println ("Lines on terminal: " + terminal.getHeight (), TAIL_COLORS);

            // We allow here to exit the terminal by using Ctrl+Z. The traditional method
            // Ctrl+D still works. The Ctrl+Z is interesting because it avoids closing the
            // shell window by accident when hitting Ctrl+D twice, or if you fail to notice
            // that gogo is already closed (like I do sometimes).
            terminal.handle (Terminal.Signal.TSTP, new Terminal.SignalHandler ()
            {
                @Override
                public void handle (Terminal.Signal signal)
                {
                    // Say to Gogo that's the end
                    logout_terminal = true;
                }
            });

            terminal.handle(Terminal.Signal.INT, new Terminal.SignalHandler()
            {
                @Override
                public void handle(Terminal.Signal signal)
                {
                    // Ctrl+C
                    logout_terminal = true;
                }
            });

            // Get our stdin/stdout and enter raw mode
            Reader terminal_in = terminal.reader ();
            OutputStream terminal_out = terminal.output ();
            //saved_attributes = terminal.enterRawMode ();

            for (;;)
            {
                //-----------------
                // AdminD tracking
                //-----------------

                // Validate and get the current admind directory
                String valid_admind = AdmindUtil.getAdmindDir ();

                // Falling edge
                if (admind != null && valid_admind == null)
                {
                    admind = null;
                }

                // Try to reconnect
                if (admind == null)
                {
                    // TODO: TRACK BY SERVER NAME
                    valid_admind = AdmindUtil.initAdmindDir ();

                    if (silence_on_the_library != 0
                        && (silence_on_the_library + 150 < System.currentTimeMillis ()
                            || valid_admind != null))
                    {
                        println ("Disconnected to '" + def_server_name + "' " + server_jvmid_str, TAIL_COLORS);
                        silence_on_the_library = 0;
                    }
                }

                // Rising edge
                if (admind == null && valid_admind != null)
                {
                    admind = valid_admind;
                    admind_properties = AdmindUtil.getServerProperties ();
                    server_jvmid = admind_properties.getProperty ("server.jvmid");
                    server_jvmid_str = (server_jvmid == null)? "": "(" + server_jvmid + ")";
                    println ("Connected to '" + def_server_name + "' " + server_jvmid_str, TAIL_COLORS);

                    //-----------------------------------
                    // Update system_log from new server
                    //-----------------------------------

                    system_log = admind_properties.getProperty ("system.log.file");

                    if (system_log == null)
                    {
                        println ("Property 'system.log.file' is not set -- will not tail", ERROR_COLORS);
                    }

                    //----------------------
                    // Same file, new boot?
                    //----------------------

                    Path system_log_path = Paths.get (system_log);
                    long new_inode = (Long)Files.getAttribute (system_log_path, "unix:ino");

                    if (new_inode != system_log_inode)
                    {
                        //-----------
                        // New file!
                        //-----------

                        // Close the old log file (if some)
                        if (remote_in != null)
                        {
                            try
                            {
                                remote_in.close ();
                            }
                            catch (IOException ignore) {};
                        }

                        // Open the new log file
                        remote_in = new DataInputStream (Files.newInputStream (Paths.get (system_log)));
                        system_log_inode = new_inode;
                    }
                }

                if (logout_terminal) // Using Ctrl+C or Ctrl+Z
                {
                    terminal.writer ().println ();
                    terminal.flush ();
                    break;
                }

                //----------
                // Tail it!
                //----------

                int bytes_exchanged = 0;

                if (remote_in != null)
                {
                    int bytes_available = remote_in.available ();

                    if (bytes_available > 0)
                    {
                        if (bytes_available > buffer.length)
                        {
                            bytes_available = buffer.length;
                        }
                        bytes_exchanged = remote_in.read (buffer, 0, bytes_available);
                    }

                    if (bytes_exchanged > 0)
                    {
                        terminal_out.write (buffer, 0, bytes_exchanged);
                        silence_on_the_library = System.currentTimeMillis ();
                    }
                }

                // Only yield if the console is idle
                if (bytes_exchanged == 0)
                {
                    Thread.sleep (20);
                }
            }
        }
        catch (Exception wtf)
        {
            terminal.writer ().println ("\n" + wtf.toString());
            terminal.flush ();
        }
        finally
        {
            if (terminal != null)
            {
                // Restore normal terminal operation
                if (saved_attributes != null)
                {
                    terminal.setAttributes (saved_attributes);
                }

                // Fix unfortunate lack of LineReader cleanup
                // with BRACKETED PASTE OFF (Solving the annoying 0~ 1~)
                terminal.writer ().write ("\033[?2004l");
                terminal.flush ();

                try
                {
                    terminal.close ();
                }
                catch (IOException ignore) {};

                try
                {
                    remote_in.close ();
                }
                catch (IOException ignore) {};
            }
        }
    }
}

// EOF
