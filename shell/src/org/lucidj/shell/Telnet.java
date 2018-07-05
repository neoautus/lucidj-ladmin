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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class Telnet
{
    enum TelnetState
    {
        LOOK_FOR_IAC,
        DISCARD_IF_NUL,
        WAIT_FOR_COMMAND,
        WAIT_FOR_OPTION
    };

    static final int SKIP_CHAR = -1;
    static final int CR_CHAR  = 0x0d;
    static final int IAC_CHAR = 0xff;  // 255
    static final int CMD_DONT = 0xfe;  // 254
    static final int CMD_DO   = 0xfd;  // 253
    static final int CMD_WONT = 0xfc;  // 252
    static final int CMD_WILL = 0xfb;  // 251
    static final int CMD_SB   = 0xfa;  // 250
    static final int CMD_SE   = 0xf0;  // 240
    static final int CMD_IS   = 0x00;  // 0

    static TelnetState telnet_state = TelnetState.LOOK_FOR_IAC;
    static int telnet_command;
    static boolean logout_terminal;

    private static DataInputStream socket_in;
    private static DataOutputStream socket_out;
    private static Terminal terminal = null;
    private static Attributes saved_attributes = null;
    private static byte[] buffer = new byte [1024];

    private static void send_terminal_size ()
    {
        try
        {
            socket_out.write (IAC_CHAR);
            socket_out.write (CMD_SB);
            socket_out.write (31);
            int term_width = terminal.getWidth ();
            socket_out.write ((byte)(term_width >> 8));   // width hi
            socket_out.write ((byte)term_width);          // width lo
            int term_height = terminal.getHeight ();
            socket_out.write ((byte)(term_height >> 8));  // height hi
            socket_out.write ((byte)term_height);         // height lo
            socket_out.write (IAC_CHAR);
            socket_out.write (CMD_SE);
            socket_out.flush ();
        }
        catch (IOException ignore) {};
    }

    private static void send_terminal_type ()
    {
        try
        {
            socket_out.write (IAC_CHAR);
            socket_out.write (CMD_SB);
            socket_out.write (24);
            socket_out.write (('\0' + terminal.getType ().toLowerCase ()).getBytes ());
            socket_out.write (IAC_CHAR);
            socket_out.write (CMD_SE);
            socket_out.flush ();
        }
        catch (IOException ignore) {};
    }

    private static void trick_telnet_server ()
        throws IOException
    {
        // Ugly but effective(tm)

        // [1] Server -> Client
        byte[] server_req1 =
        {
            (byte)0xFF, (byte)0xFB, 0x01,   // SERVER IAC WILL 1 (ECHO)
            (byte)0xFF, (byte)0xFE, 0x01,   // SERVER IAC DONT 1 (ECHO)
            (byte)0xFF, (byte)0xFD, 0x1F,   // SERVER IAC DO 31 (NAWS)
            (byte)0xFF, (byte)0xFB, 0x03,   // SERVER IAC WILL 3 (SGA)
            (byte)0xFF, (byte)0xFD, 0x03,   // SERVER IAC DO 3 (SGA)
            (byte)0xFF, (byte)0xFD, 0x18,   // SERVER IAC DO 24 (TTYPE)
            (byte)0xFF, (byte)0xFD, 0x27    // SERVER IAC DO 39 (NEW-ENVIRON)
        };
        socket_in.readFully (buffer, 0, server_req1.length);

        // [2] Client -> Server
        byte[] client_resp1 =
        {
            (byte)0xFF, (byte)0xFD, 0x01,   // CLIENT IAC DO 1 (ECHO)
            (byte)0xFF, (byte)0xFB, 0x1F,   // CLIENT IAC WILL 31 (NAWS)
            (byte)0xFF, (byte)0xFD, 0x03,   // CLIENT IAC DO 3 (SGA)
            (byte)0xFF, (byte)0xFB, 0x03,   // CLIENT IAC WILL 3 (SGA)
            (byte)0xFF, (byte)0xFB, 0x18,   // CLIENT IAC WILL 24 (TTYPE)
            (byte)0xFF, (byte)0xFB, 0x27    // CLIENT IAC WILL 39 (NEW-ENVIRON)
        };
        socket_out.write (client_resp1, 0, client_resp1.length);
        socket_out.flush ();
        send_terminal_size();               // CLIENT SUB 31 (NAWS) [4 bytes]

        // [3] Server -> Client
        byte[] server_req2 =
        {
            // SERVER SUB 24 (TTYPE) [1 bytes]:  [<0x01>]
            (byte)0xFF, (byte)0xFA, 0x18, 0x01, (byte)0xFF, (byte)0xF0,

            // SERVER SUB 39 (NEW-ENVIRON) [3 bytes]:  [<0x01><0x00><0x03>]
            (byte)0xFF, (byte)0xFA, 0x27, 0x01, 0x00, 0x03, (byte)0xFF, (byte)0xF0
        };
        socket_in.readFully (buffer, 0, server_req2.length);

        // [4] Client -> Server
        byte[] client_resp2 =
        {
            // CLIENT SUB 39 (NEW-ENVIRON) [1 bytes]:  [<0x00>]
            (byte)0xFF, (byte)0xFA, 0x27, 0x00, (byte)0xFF, (byte)0xF0
        };
        socket_out.write (client_resp2, 0, client_resp2.length);
        socket_out.flush ();
        send_terminal_type ();              // CLIENT SUB 24 (TTYPE) [variable]
    }

    public static int telnet_filter (int ch)
    {
        switch (telnet_state)
        {
            case DISCARD_IF_NUL:
            {
                if (ch == 0)
                {
                    // Discard the NUL and get back to normal processing
                    telnet_state = TelnetState.LOOK_FOR_IAC;
                    ch = SKIP_CHAR;
                    break;
                }
                // Fall through
            }
            case LOOK_FOR_IAC:
            {
                if (ch == IAC_CHAR)
                {
                    // Discard IAC char and wait for the command
                    telnet_state = TelnetState.WAIT_FOR_COMMAND;
                    ch = SKIP_CHAR;
                }
                else if (ch == CR_CHAR)
                {
                    // We should discard a NUL following a CR
                    telnet_state = TelnetState.DISCARD_IF_NUL;
                }
                break;
            }
            case WAIT_FOR_COMMAND:
            {
                if (ch == IAC_CHAR)
                {
                    // Return the 0xff character and get back to normal processing
                    telnet_state = TelnetState.LOOK_FOR_IAC;
                }
                else
                {
                    if (ch == CMD_DO || ch == CMD_DONT)
                    {
                        telnet_command = ch;
                    }

                    // Discard the command char and wait for the option
                    telnet_state = TelnetState.WAIT_FOR_OPTION;
                    ch = SKIP_CHAR;
                }
                break;
            }
            case WAIT_FOR_OPTION:
            {
                // Will set the option to true (IAC DO) or false (IAC DONT)
                boolean do_flag = (telnet_command == CMD_DO);

                switch (ch)
                {
                    case 0x12: // LOGOUT
                    {
                        logout_terminal = do_flag;
                        break;
                    }
                }

                // Discard the option char and get back to normal processing
                telnet_state = TelnetState.LOOK_FOR_IAC;
                ch = SKIP_CHAR;
                break;
            }
        }
        return (ch);
    }

    public static void main (String[] args)
    {
        Socket clientSocket;

        try
        {
            clientSocket = new Socket ("localhost", 6523);
            clientSocket.setSoTimeout (100);
            socket_in = new DataInputStream (clientSocket.getInputStream ());
            socket_out = new DataOutputStream (clientSocket.getOutputStream ());
        }
        catch (IOException e)
        {
            System.err.println ("Exception connecting to server: " + e.getMessage());
            return;
        }

        try
        {
            terminal = TerminalBuilder.builder ()
                .name ("gogo")
                .system (true)
                .nativeSignals (true)
                .signalHandler (Terminal.SignalHandler.SIG_IGN)
                .build ();

            terminal.handle (Terminal.Signal.WINCH, new Terminal.SignalHandler ()
            {
                @Override
                public void handle (Terminal.Signal signal)
                {
                    send_terminal_size ();
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
                    logout_terminal = true;
                }
            });

            trick_telnet_server ();

            Reader terminal_in = terminal.reader ();
            OutputStream terminal_out = terminal.output ();
            saved_attributes = terminal.enterRawMode ();

            while (!clientSocket.isClosed ())
            {
                int bytes_read;

                try
                {
                    // Not very elegant, but reacts quickly to connection closed by host
                    bytes_read = socket_in.read (buffer);
                }
                catch (SocketTimeoutException ignore)
                {
                    bytes_read = 0;
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
                        int ch = telnet_filter (buffer[i] & 0x00ff);

                        if (ch != -1)
                        {
                            terminal_out.write (ch);
                        }
                    }
                }

                if (logout_terminal)
                {
                    terminal.writer ().println ("\rLogout");
                    terminal.flush ();
                    break;
                }

                if (terminal_in.ready ())
                {
                    int ch = terminal_in.read ();

                    if (ch >= 0)
                    {
                        socket_out.write (ch);

                        if (ch == '\r')
                        {
                            // Make clear the CR intent by sending CR NUL
                            socket_out.write (0);
                        }
                        socket_out.flush ();
                    }
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
            }
        }
    }
}

// EOF
