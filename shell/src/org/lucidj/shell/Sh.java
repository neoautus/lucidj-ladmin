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
import org.lucidj.ext.admind.AdmindUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;

public class Sh
{
    private static Terminal terminal = null;
    private static Attributes saved_attributes = null;

    private static DataInputStream remote_in;
    private static DataOutputStream remote_out;

    private static byte[] buffer = new byte [8192];

    public static void main (String[] args)
        throws Exception
    {
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
        }
        catch (IOException e)
        {
            System.out.println ("Exception opening terminal: " + e.toString());
            System.exit (1);
        }

        System.out.println ("TERMINAL TYPE = '" + terminal.getType() + "'");
        System.out.println ("TERMINAL DIMENSIONS = " + terminal.getWidth() + " x " + terminal.getHeight());

        //----------------
        // AdminD request
        //----------------

        String def_server_name = AdmindUtil.getDefaultServerName ();
        String admind = AdmindUtil.initAdmindDir ();

        if (admind == null)
        {
            System.out.println ("Unable to find '" + def_server_name + "'");
            System.exit (1);
        }

        String request = AdmindUtil.asyncInvoke ("shell", "true");

        if (!AdmindUtil.asyncWait (request, 5000, AdmindUtil.ASYNC_RUNNING))
        {
            String error = AdmindUtil.asyncError (request);
            System.out.println ("Error requesting shutdown for '" + def_server_name + "': " + error);
            System.exit (1);
        }

        System.out.println ("Connected to '" + def_server_name + "': " + request);

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
//                send_terminal_size ();
                terminal.writer ().println ("<SIZE=" + terminal.getWidth() + "x" + terminal.getHeight() + ">");
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
                terminal.writer ().println ();
//                logout_terminal = true;
                terminal.writer ().println ("<QUIT!>");
            }
        });

        Reader terminal_in = terminal.reader ();
        OutputStream terminal_out = terminal.output ();
        saved_attributes = terminal.enterRawMode ();

        // Init terminal information
        remote_out.write ((byte)0xf0);
        remote_out.write (terminal.getType ().getBytes ());
        remote_out.write ((byte)0xf1);
        remote_out.write (Integer.toString (terminal.getWidth ()).getBytes ());
        remote_out.write (';');
        remote_out.write (Integer.toString (terminal.getHeight ()).getBytes ());
        remote_out.write ((byte)0xff);
        remote_out.write ('\r');
        remote_out.flush ();

        //-----------
        // Shell it!
        //-----------

        for (;;)
        {
            int bytes_read = 0;
            int bytes_available = remote_in.available ();

            if (bytes_available > 0)
            {
                if (bytes_available > buffer.length)
                {
                    bytes_available = buffer.length;
                }
                bytes_read = remote_in.read (buffer, 0, bytes_available);
            }

            if (bytes_read == -1)
            {
                terminal.writer ().println ("\nConnection closed by server");
                terminal.flush ();
                break;
            }

            if (bytes_read > 0)
            {
                for (int i = 0; i < bytes_read; i++)
                {
                    terminal_out.write (buffer[i] & 0x00ff);
                }
            }

            if (terminal_in.ready ())
            {
                int ch = terminal_in.read ();

                if (ch >= 0)
                {
                    remote_out.write (ch);
                    remote_out.flush ();

                    if (ch == 4)
                    {
                        break;
                    }
                }
            }

            if (bytes_read == 0)
            {
                Thread.sleep (20);
            }
        }

        // TODO: Restore attributes...
    }
}

// EOF
