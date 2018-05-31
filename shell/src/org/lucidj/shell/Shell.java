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

import java.io.*;
import java.net.Socket;

public class Shell
{
    public static void trick_telnet_server (Terminal terminal, DataInputStream socket_in, DataOutputStream socket_out)
        throws IOException
    {
        // Ugly but effective(tm)

        // [1] Server -> Client
        byte[] server_req1 =
        {
            (byte)0xFF, (byte)0xFB, 0x01,
            (byte)0xFF, (byte)0xFE, 0x01,
            (byte)0xFF, (byte)0xFD, 0x1F,
            (byte)0xFF, (byte)0xFB, 0x03,
            (byte)0xFF, (byte)0xFD, 0x03,
            (byte)0xFF, (byte)0xFD, 0x18,
            (byte)0xFF, (byte)0xFD, 0x27
        };

        byte[] buffer = new byte [80];
        socket_in.readFully (buffer, 0, server_req1.length);

        int term_width = terminal.getWidth();
        byte width_hi = (byte)(term_width >> 8);
        byte width_lo = (byte)term_width;
        int term_height = terminal.getHeight();
        byte height_hi = (byte)(term_height >> 8);
        byte height_lo = (byte)term_height;

        // [2] Client -> Server
        byte[] client_resp1 =
        {
            (byte)0xFF, (byte)0xFD, 0x01,
            (byte)0xFF, (byte)0xFB, 0x1F,
            (byte)0xFF, (byte)0xFA, 0x1F, width_hi, width_lo, height_hi, height_lo, (byte)0xFF, (byte)0xF0,
            (byte)0xFF, (byte)0xFD, 0x03,
            (byte)0xFF, (byte)0xFB, 0x03,
            (byte)0xFF, (byte)0xFB, 0x18,
            (byte)0xFF, (byte)0xFB, 0x27
        };

        socket_out.write (client_resp1, 0, client_resp1.length);
        socket_out.flush ();

        // [3] Server -> Client
        byte[] server_req2 =
        {
            (byte)0xFF, (byte)0xFA, 0x18, 0x01, (byte)0xFF, (byte)0xF0,
            (byte)0xFF, (byte)0xFA, 0x27, 0x01, 0x00, 0x03, (byte)0xFF, (byte)0xF0
        };

        socket_in.readFully (buffer, 0, server_req2.length);

        // [4] Client -> Server
        byte[] client_resp2 =
        {                                      /* x     t     e     r     m */
            (byte)0xFF, (byte)0xFA, 0x18, 0x00, 0x78, 0x74, 0x65, 0x72, 0x6D, (byte)0xFF, (byte)0xF0,
            (byte)0xFF, (byte)0xFA, 0x27, 0x00, (byte)0xFF, (byte)0xF0
        };

        socket_out.write (client_resp2, 0, client_resp2.length);
        socket_out.flush ();
    }

    public static void main (String[] args)
    {
        try
        {
            Socket clientSocket = new Socket ("localhost", 6523);

            DataInputStream socket_in = new DataInputStream (clientSocket.getInputStream ());
            DataOutputStream socket_out = new DataOutputStream (clientSocket.getOutputStream ());
            Attributes saved_attributes = null;
            Terminal terminal = null;

            try
            {
                terminal = TerminalBuilder.builder ()
                    .name("gogo")
                    .system(true)
                    .nativeSignals(true)
                    //.signalHandler(Terminal.SignalHandler.SIG_IGN)
                    .build();

                //System.out.println ("Terminal type: " + terminal.getType ());

                trick_telnet_server(terminal, socket_in, socket_out);

                Reader terminal_in = terminal.reader ();
                OutputStream terminal_out = terminal.output ();

//                CommandSession session = processor.createSession(terminal_in, terminal_out, terminal_out);
//                session.put(Shell.VAR_CONTEXT, context);
//                session.put(Shell.VAR_TERMINAL, terminal);

                saved_attributes = terminal.enterRawMode();

                byte[] buffer = new byte [256];

                while (!clientSocket.isClosed ())
                {
                    int avail = socket_in.available ();

                    if (avail > 0)
                    {
                        int count = socket_in.read (buffer);
                        for (int i = 0; i < count; i++)
                        {
                            terminal_out.write (buffer [i]);
                        }
                    }

                    try
                    {
                        if (terminal_in.ready())
                        {
                            int ch = terminal_in.read ();

                            if (ch >= 0)
                            {
                                socket_out.write (ch);

                                if (ch == '\r')
                                {
                                    socket_out.write (0);
                                }

                                socket_out.flush ();
                            }
                        }
                    }
                    catch (IOException e)
                    {
                        if ("Stream closed".equals(e.getMessage()))
                        {
                            break;
                        }
                        throw (e);
                    }
                }
            }
            finally
            {
                if (terminal != null && saved_attributes != null)
                {
                    terminal.setAttributes (saved_attributes);
                }
            }
        }
        catch (Exception wtf)
        {
            wtf.printStackTrace();
        }
    }
}

// EOF
