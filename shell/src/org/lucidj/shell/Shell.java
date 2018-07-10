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

package org.lucidj.shell;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.lucidj.admind.shared.AdmindUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;

public class Shell
{
    private static int UPDATE_TERMINAL_DELAY_MS = 500;

    private static Terminal terminal = null;
    private static Attributes saved_attributes = null;
    private static boolean logout_terminal;
    private static long update_size_timestamp = -1;

    private static DataInputStream remote_in;
    private static DataOutputStream remote_out;

    private static byte[] buffer = new byte [8192];

    private static void send_terminal_size ()
    {
        try
        {
            remote_out.write ((byte)0xf1);
            remote_out.write (Integer.toString (terminal.getWidth ()).getBytes ());
            remote_out.write (';');
            remote_out.write (Integer.toString (terminal.getHeight ()).getBytes ());
            remote_out.write ((byte)0xff);
            remote_out.flush ();
        }
        catch (IOException ignore) {};
    }

    private static void send_terminal_type ()
    {
        try
        {
            remote_out.write ((byte)0xf0);
            remote_out.write (terminal.getType ().getBytes ());
            remote_out.write ((byte)0xff);
            remote_out.flush ();
        }
        catch (IOException ignore) {};
    }

    private static void send_terminfo_done ()
    {
        try
        {
            // Says that all initialization info was sent
            remote_out.write ('\r');
            remote_out.flush ();
        }
        catch (IOException ignore) {};
    }

    public static void main (String[] args)
    {
        //-------------------------
        // AdminD init and connect
        //-------------------------

        String def_server_name = AdmindUtil.getServerName ();
        String admind = AdmindUtil.initAdmindDir ();

        if (admind == null)
        {
            System.out.println ("Unable to find '" + def_server_name + "'");
            System.exit (1);
        }

        final String request = AdmindUtil.asyncInvoke ("shell", "true");
        int status = AdmindUtil.asyncWait (request, 5000, AdmindUtil.ASYNC_RUNNING);

        if (status == AdmindUtil.ASYNC_GONE)
        {
            System.out.println ("Server '" + def_server_name + "' is gone");
        }
        else if (status == AdmindUtil.ASYNC_ERROR)
        {
            String error = AdmindUtil.asyncError (request);
            System.out.println ("Error opening console for '" + def_server_name + "': " + error);
            System.exit (1);
        }

        // Cleanup the shell transaction at exit
        Runtime.getRuntime ().addShutdownHook (new Thread ()
        {
            public void run ()
            {
                // Check for error forces transaction discard
                AdmindUtil.asyncError (request);
            }
        });

        //---------------
        // Init terminal
        //---------------

        try
        {
            terminal = TerminalBuilder.builder ()
                .name ("gogo")
                .system (true)
                .nativeSignals (true)
                .signalHandler (Terminal.SignalHandler.SIG_IGN)
                .build ();

            String jvmid = AdmindUtil.getServerProperties().getProperty ("server.jvmid");
            String jvmstr = (jvmid == null)? "": "(" + jvmid + ")";
            terminal.writer ().println ("Connected to '" + def_server_name + "' " + jvmstr);
            terminal.writer ().flush ();

            //----------------------
            // Init interconnection
            //----------------------

            remote_in = new DataInputStream (new FileInputStream (AdmindUtil.responseFile (request)));
            remote_out = new DataOutputStream (new FileOutputStream (AdmindUtil.requestFile (request), true));

            //---------------------------
            // Init terminal and session
            //---------------------------

            terminal.handle (Terminal.Signal.WINCH, new Terminal.SignalHandler ()
            {
                @Override
                public void handle (Terminal.Signal signal)
                {
                    // Set up to update the terminal size
                    update_size_timestamp = System.currentTimeMillis() + UPDATE_TERMINAL_DELAY_MS;
                }
            });

            // We allow here to exit the terminal by using Ctrl+Z. The traditional method
            // Ctrl+D still works. The Ctrl+Z is interesting because it avoids closing the
            // shell window by accident when hitting Ctrl+D twice, or if you fail to notice
            // that gogo is already closed (like I do sometimes).
            terminal.handle (Terminal.Signal.TSTP, new Terminal.SignalHandler ()
            {
                @Override
                public void handle (Terminal.Signal signal)
                {
                    try
                    {
                        // Say to Gogo that's the end
                        logout_terminal = true;
                        remote_out.write (4);
                        remote_out.flush ();
                    }
                    catch (IOException ignore) {};
                }
            });

            // Get our stdin/stdout and enter raw mode
            Reader terminal_in = terminal.reader ();
            OutputStream terminal_out = terminal.output ();
            saved_attributes = terminal.enterRawMode ();

            // Init terminal information
            send_terminal_type ();
            send_terminal_size ();
            send_terminfo_done ();

            //-----------
            // Shell it!
            //-----------

            for (;;)
            {
                int shell_status = AdmindUtil.asyncPoll (request);

                if (shell_status == AdmindUtil.ASYNC_GONE)
                {
                    terminal.writer ().println ("\nConnection closed by server");
                    terminal.flush ();
                    break;
                }
                else if (shell_status == AdmindUtil.ASYNC_ERROR)
                {
                    AttributedStringBuilder sb = new AttributedStringBuilder ();
                    sb.style (sb.style ().foreground (AttributedStyle.RED));
                    sb.append ("\n");
                    sb.append (AdmindUtil.asyncError (request));
                    sb.style (sb.style ().foregroundDefault ());
                    terminal.writer().println (sb.toAnsi (terminal));
                    terminal.flush ();
                    break;
                }

                // Instead of updating the terminal size every time it changes,
                // we wait a little until the user settles with the desired size
                if (update_size_timestamp != -1
                    && update_size_timestamp < System.currentTimeMillis ())
                {
                    update_size_timestamp = -1;
                    send_terminal_size();
                }

                if (logout_terminal) // Using Ctrl+D or Ctrl+Z
                {
                    terminal.writer ().println ("\nLogout");
                    terminal.flush ();
                    break;
                }

                int bytes_exchanged = 0;
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
                }

                if (terminal_in.ready ())
                {
                    int ch = terminal_in.read ();

                    if (ch >= 0)
                    {
                        remote_out.write (ch);
                        remote_out.flush ();
                        bytes_exchanged++;

                        if (ch == 4) // Ctrl+D
                        {
                            logout_terminal = true;
                        }
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

                try
                {
                    remote_out.close ();
                }
                catch (IOException ignore) {};
            }
        }
    }
}

// EOF
