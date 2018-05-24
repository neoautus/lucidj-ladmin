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

import edu.stanford.ejalbert.BrowserLauncher;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LauncherUI extends JFrame
{
    private final LauncherUI self = this;
    private SystemTray system_tray;

    private SimpleDateFormat timestamp_format = new SimpleDateFormat ("HH:mm:ss.SSS");
    private String logger_text;
    private boolean prepend_timestamp;

    private final String app_title = "LucidJ Launcher"; // It will become LucidJ Monitor :)
    private final String app_version = "version 1.2.0";
    private Image app_icon = Toolkit.getDefaultToolkit ().getImage (getClass().getResource("/images/felix-tray.png"));

    private final int STATUS_UNKNOWN = 0;
    private final int STATUS_STOPPED = 1;
    private final int STATUS_STARTING = 2;
    private final int STATUS_RUNNING = 3;
    private final int STATUS_STOPPING = 4;
    private final int STATUS_ATTENTION = 5;
    private final int STATUS_FATAL = 6;
    private volatile int current_status = STATUS_UNKNOWN;

    // Variables declaration - do not modify
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTextPane jTextPane1;
    // End of variables declaration

    public LauncherUI ()
    {
        create_gui ();
        setup_frame ();
        fix_startstop_button_size ();
        setup_systemtray ();
        setup_actions ();
        setup_logger ();
        show_status (current_status);
        set_error (null);
        start_monitor ();
    }

    public void setup_frame ()
    {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment ().getDefaultScreenDevice ();
        int screen_width = gd.getDisplayMode ().getWidth ();
        int screen_height = gd.getDisplayMode ().getHeight ();
        Dimension frame = getSize ();
        setLocation (screen_width - frame.width - 48, screen_height - frame.height - 48);

        setIconImage (app_icon);
        setTitle (app_title);
    }

    public void create_gui ()
    {
        jPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jLabel6 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jButton3 = new javax.swing.JButton();
        jButton4 = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTextPane1 = new javax.swing.JTextPane();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new java.awt.Dimension(600, 380));
        setPreferredSize(new java.awt.Dimension(600, 380));
        setResizable(false);
        setSize(new java.awt.Dimension(600, 380));

        jPanel1.setBackground(new java.awt.Color(255, 255, 255));

        jLabel1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/lucidj-logo.png"))); // NOI18N +++

        jLabel2.setFont(new java.awt.Font("Dialog", 1, 14)); // NOI18N
        jLabel2.setText(app_title); //+++
        jLabel2.setFocusable(false);

        jLabel3.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        jLabel3.setText(app_version); //+++
        jLabel3.setFocusable(false);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
                jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(jLabel1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jLabel2, javax.swing.GroupLayout.Alignment.TRAILING)
                                        .addComponent(jLabel3, javax.swing.GroupLayout.Alignment.TRAILING))
                                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
                jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                .addComponent(jLabel2)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(jLabel3))
                                        .addComponent(jLabel1))
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jButton1.setText("Start");

        jButton2.setText("Launch browser for LucidJ Console");

        jLabel6.setForeground(new java.awt.Color(208, 0, 0));
        jLabel6.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        jLabel6.setText("jLabel6");

        jLabel4.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/locating.gif"))); // NOI18N +++
        jLabel4.setText("Loading LucidJ container...");
        jLabel4.setIconTextGap(8);

        jButton3.setText("Hide");

        jButton4.setText("Options...");

        jScrollPane2.setBorder(null);

        jTextPane1.setBackground(new java.awt.Color(238, 238, 238));
        jTextPane1.setBorder(null);
        jTextPane1.setText("The quick brown fox jumped over the lazy dogs");
        jScrollPane2.setViewportView(jTextPane1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jLabel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addGroup(layout.createSequentialGroup()
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addComponent(jScrollPane2)
                                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                                                .addComponent(jButton1)
                                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                                .addComponent(jButton4)
                                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 111, Short.MAX_VALUE)
                                                                .addComponent(jButton2))
                                                        .addGroup(layout.createSequentialGroup()
                                                                .addComponent(jLabel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                                .addComponent(jButton3)))
                                                .addContainerGap())))
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 186, Short.MAX_VALUE)
                                .addGap(18, 18, 18)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(jLabel4)
                                        .addComponent(jButton3))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel6)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(jButton2)
                                        .addComponent(jButton4)
                                        .addComponent(jButton1))
                                .addContainerGap())
        );

        pack();
    }

    private void fix_startstop_button_size ()
    {
        jButton1.setText ("Checking...");
        Dimension size = jButton1.getPreferredSize ();
        jButton1.setSize (size);
        jButton1.setMinimumSize (size);
    }

    public void setup_logger ()
    {
        DefaultCaret caret = (DefaultCaret)jTextPane1.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        jTextPane1.setContentType ("text/html");
        jTextPane1.setEditable (false);
        jTextPane1.setCursor (null);
        jTextPane1.setFont (UIManager.getFont("Label.font").deriveFont (Font.PLAIN));
        jTextPane1.putClientProperty (JEditorPane.HONOR_DISPLAY_PROPERTIES, true);

        String hostname = "localhost";
        try
        {
            // Informative -- do not try too hard
            hostname = InetAddress.getLocalHost().getHostName();
        }
        catch (Exception ignore) {};

        String java_vm_name = System.getProperty ("java.vm.name");
        String java_vm_name_first = java_vm_name.split (" ")[0];
        logger_text = "<html>";
        logger_text = "<table border=0 cellpadding=0 cellspacing=0>";
        logger_text += "<tr><td><b>Java:&nbsp;</b></td><td>" + java_vm_name_first +
                       " version " + System.getProperty ("java.version") + "</td></tr>";
        logger_text += "<tr><td><b>Vendor:&nbsp;</b></td><td>" + System.getProperty ("java.vendor") +
                       " " + System.getProperty ("java.vendor.url") + "</td></tr>";
//        logger_text += "<tr><td><b>Runtime:&nbsp;</b></td><td>" + System.getProperty ("java.runtime.name") +
//                       " (build " + System.getProperty ("java.runtime.version") + ")" + "</td></tr>";
        logger_text += "<tr><td><b>JVM:&nbsp;</b></td><td>" + java_vm_name +
                       "(build " + System.getProperty ("java.vm.version") +
                       ", " + System.getProperty ("java.vm.info") + ")" + "</td></tr>";
        logger_text += "<tr><td><b>JDK home:&nbsp;</b></td><td>" + System.getProperty ("java.home") + "</td></tr>";
        logger_text += "<tr><td><b>Host:&nbsp;</b></td><td>" + hostname +
                       " (" + System.getProperty ("os.name") + " " + System.getProperty ("os.version") +
                       " " + System.getProperty ("os.arch") + ")</td></tr>";
        logger_text += "<tr><td><b>Sys home:&nbsp;</b></td><td>" + System.getProperty ("system.home") + "</td></tr>";
        logger_text += "</table>";
        jTextPane1.setText (logger_text);
        prepend_timestamp = true;
    }

    private void add_timestamp_if_needed ()
    {
        if (!prepend_timestamp)
        {
            return;
        }

        logger_text += timestamp_format.format (new Date ()) + "  ";
        prepend_timestamp = false;
    }

    private void print (String text)
    {
        add_timestamp_if_needed ();
        logger_text += "<b>" + text + "</b>";
        jTextPane1.setText(logger_text);
    }

    private void println (String text)
    {
        add_timestamp_if_needed ();
        logger_text += "<b>" + text + "</b><br>";
        prepend_timestamp = true;
        jTextPane1.setText(logger_text);
    }

    private void set_start_stop (boolean start)
    {
        jButton1.setText (start? "Start": "Stop");
        jButton1.setEnabled (true);
    }

    private void show_status (int status)
    {
        String icon;
        String message;

        switch (status)
        {
            case STATUS_ATTENTION:
            {
                icon = "attention.png";
                message = "The container requires attention, see log files";
                jButton1.setEnabled (true);
                jButton2.setEnabled (true);
                break;
            }
            case STATUS_FATAL:
            {
                icon = "fatal.png";
                message = "Fatal error ocurred, see log files";
                set_start_stop (true);
                jButton2.setEnabled (false);
                break;
            }
            case STATUS_RUNNING:
            {
                icon = "running.png";
                message = "LucidJ Container started";
                set_start_stop (false);
                jButton2.setEnabled (true);
                break;
            }
            case STATUS_STARTING:
            {
                icon = "starting.gif";
                message = "LucidJ Container is starting...";
                jButton1.setEnabled (false);
                jButton2.setEnabled (false);
                break;
            }
            case STATUS_STOPPING:
            {
                icon = "starting.gif";
                message = "LucidJ Container is stopping...";
                jButton1.setEnabled (false);
                jButton2.setEnabled (false);
                break;
            }
            case STATUS_STOPPED:
            {
                icon = "stopped.png";
                message = "LucidJ Container is stopped";
                set_start_stop (true);
                jButton2.setEnabled (false);
                break;
            }
            case STATUS_UNKNOWN:
            default:
            {
                //icon = "unknown.png";
                icon = "locating.gif";
                message = "Checking container status...";
                jButton1.setEnabled (false);
                jButton2.setEnabled (false);
                break;
            }
        }

        jLabel4.setIcon (new javax.swing.ImageIcon(getClass().getResource("/images/" + icon)));
        jLabel4.setText (message);
        jLabel4.setIconTextGap (8);
    }

    public void set_error (String error_text)
    {
        jLabel6.setText(error_text != null? error_text: " ");
    }

    private void setup_systemtray ()
    {
        if (SystemTray.isSupported ())
        {
            system_tray = SystemTray.getSystemTray ();
            Image image = Toolkit.getDefaultToolkit ().getImage (getClass().getResource("/images/felix-tray.png"));
            TrayIcon trayIcon = new TrayIcon (image, app_title);
            trayIcon.setImageAutoSize (true);
            trayIcon.addMouseListener (new MouseListener ()
            {
                @Override
                public void mouseClicked (MouseEvent e)
                {
                    // Show/hide control window
                    self.setVisible (!self.isVisible ());
                }

                @Override
                public void mousePressed (MouseEvent e)
                {
                    // Ignore
                }

                @Override
                public void mouseReleased (MouseEvent e)
                {
                    // Ignore
                }

                @Override
                public void mouseEntered (MouseEvent e)
                {
                    // Ignore
                }

                @Override
                public void mouseExited (MouseEvent e)
                {
                    // Ignore
                }
            });

            try
            {
                system_tray.add (trayIcon);
            }
            catch (AWTException e)
            {
                System.err.println ("Cannot add icon to system tray, please use the control window as you like.");
            }
        }
    }

    private void setup_actions ()
    {
        jButton1.addActionListener (new ActionListener ()
        {
            @Override
            public void actionPerformed (ActionEvent e)
            {
                set_error (null);

                if (jButton1.getText ().equalsIgnoreCase ("start"))
                {
                    print ("Starting Karaf... ");
                    Launcher.newLauncher ().start (new String[] { "single" });
                    show_status (current_status = STATUS_STARTING);
                }
                else
                {
                    print ("Stopping Karaf... ");
                    Launcher.newLauncher ().stop (null);
                    show_status (current_status = STATUS_STOPPING);
                }
            }
        });

        jButton2.addActionListener (new ActionListener ()
        {
            @Override
            public void actionPerformed (ActionEvent e)
            {
                launch_browser ("http://localhost:8181");
            }
        });

        jButton3.addActionListener (new ActionListener ()
        {
            @Override
            public void actionPerformed (ActionEvent e)
            {
                setVisible (false);
            }
        });

        jButton4.addActionListener (new ActionListener ()
        {
            @Override
            public void actionPerformed (ActionEvent e)
            {
                JPopupMenu popup = new JPopupMenu("Popup");
                popup.add (new JMenuItem("Select alternative JDK..."));
                popup.add (new JPopupMenu.Separator ());
                popup.add (new JCheckBoxMenuItem ("Start automatically", false));
                popup.add (new JCheckBoxMenuItem ("Launch browser when started", false));
                popup.add (new JCheckBoxMenuItem ("Auto-hide after launch browser", false));

                // Show popup above button
                Dimension popup_size = popup.getPreferredSize();
                popup.show ((Component)e.getSource(), 0, -popup_size.height);
            }
        });

        getRootPane ().setDefaultButton (jButton2);
        jButton2.grabFocus ();
    }

    private void launch_browser (final String url)
    {
        try
        {
            final BrowserLauncher launcher = new BrowserLauncher ();

            Thread thread = new Thread()
            {
                public void run()
                {
                    String login_token = new Launcher ().getLoginToken ();
                    String launch_url = (login_token == null)? url: url + "?token=" + login_token;

                    println ("Launching browser: <a href='" + url + "'>" + url + "</a>");
                    launcher.openURLinBrowser (launch_url);
                }
            };
            thread.start();
        }
        catch (Exception e)
        {
            e.printStackTrace ();
        }
    }

    public boolean isHttpRunning ()
    {
        Socket s = null;

        try
        {
            s = new Socket(InetAddress.getLoopbackAddress (), 8181);
            return (s.isBound());
        }
        catch (Exception uhoh)
        {
            return (false);
        }
        finally
        {
            if (s != null)
            {
                try
                {
                    s.close();
                }
                catch (Exception ignore) {};
            }
        }
    }

    private String http_request (String request_url)
    {
        StringBuilder result = new StringBuilder();

        try
        {
            URL url = new URL (request_url);

            HttpURLConnection conn = (HttpURLConnection)url.openConnection ();
            conn.setRequestMethod ("GET");

            BufferedReader rd = new BufferedReader (new InputStreamReader (conn.getInputStream ()));
            String line;

            while ((line = rd.readLine ()) != null)
            {
                result.append (line);
            }
            rd.close();
        }
        catch (Exception ignore)
        {
            return ("");
        }

        return (result.toString());
    }

    public boolean isBootstrapFinished ()
    {
        // TODO: MAKE PORT/HOST/ETC RECONFIGURABLE
        String status = http_request ("http://localhost:8181/~localsvc");
        return (status.contains ("bootstrap_finished=true"));
    }

    // TODO: HANDLE CTRL+C ON DAEMON
    private void start_monitor ()
    {
        Thread thread = new Thread()
        {
            public void run()
            {
                long system_start_time = 0;

                for (;;)
                {
                    int new_status = current_status;

                    // When running, test http first to avoid "Invalid command '' received"
                    boolean http_running = isHttpRunning ();

                    // If Karaf http is available, check our bootstrap
                    if (current_status == STATUS_STARTING)
                    {
                        if (http_running)
                        {
                            if (system_start_time == 0)
                            {
                                system_start_time = System.currentTimeMillis ();
                                println ("Karaf ready");
                                print ("Starting system components... ");
                            }

                            if (isBootstrapFinished ())
                            {
                                println ("System ready");
                                new_status = STATUS_RUNNING;
                            }
                        }
                    }
                    else if (current_status == STATUS_UNKNOWN)
                    {
                        if (http_running && isBootstrapFinished ())
                        {
                            new_status = STATUS_RUNNING;
                        }
                        else
                        {
                            new_status = STATUS_STOPPED;
                        }
                    }
                    else
                    {
                        if (http_running)
                        {
                            if (current_status != STATUS_STOPPING)
                            {
                                new_status = STATUS_RUNNING;
                            }
                        }
                        else
                        {
                            if (current_status == STATUS_STOPPING)
                            {
                                println ("stopped");
                            }
                            new_status = STATUS_STOPPED;
                        }
                    }

                    if (new_status != current_status)
                    {
                        current_status = new_status;

                        java.awt.EventQueue.invokeLater (new Runnable()
                        {
                            public void run()
                            {
                                show_status (current_status);
                            }
                        });
                    }

                    try
                    {
                        Thread.sleep (1000);
                    }
                    catch (Exception ignore) {};
                }
            }
        };

        thread.start();
    }
}

// EOF
