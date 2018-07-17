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

package org.lucidj.libladmin.shared;

public class Ansify
{
    public static final int FG_BOLD         = 0x0100;
    public static final int FG_BLACK        = 0x0200;
    public static final int FG_RED          = 0x0201;
    public static final int FG_GREEN        = 0x0202;
    public static final int FG_BROWN        = 0x0203;
    public static final int FG_BLUE         = 0x0204;
    public static final int FG_MAGENTA      = 0x0205;
    public static final int FG_CYAN         = 0x0206;
    public static final int FG_LIGHTGRAY    = 0x0207;
    public static final int FG_GRAY         = 0x0208;
    public static final int FG_LIGHTRED     = 0x0209;
    public static final int FG_LIGHTGREEN   = 0x020a;
    public static final int FG_YELLOW       = 0x020b;
    public static final int FG_LIGHTBLUE    = 0x020c;
    public static final int FG_LIGHTMAGENTA = 0x020d;
    public static final int FG_LIGHTCYAN    = 0x020e;
    public static final int FG_WHITE        = 0x020f;

    public static final int BG_BLACK        = 0x0400;
    public static final int BG_RED          = 0x0410;
    public static final int BG_GREEN        = 0x0420;
    public static final int BG_BROWN        = 0x0430;
    public static final int BG_BLUE         = 0x0440;
    public static final int BG_MAGENTA      = 0x0450;
    public static final int BG_CYAN         = 0x0460;
    public static final int BG_LIGHTGRAY    = 0x0470;
    public static final int BG_GRAY         = 0x0480;
    public static final int BG_LIGHTRED     = 0x0490;
    public static final int BG_LIGHTGREEN   = 0x04a0;
    public static final int BG_YELLOW       = 0x04b0;
    public static final int BG_LIGHTBLUE    = 0x04c0;
    public static final int BG_LIGHTMAGENTA = 0x04d0;
    public static final int BG_LIGHTCYAN    = 0x04e0;
    public static final int BG_WHITE        = 0x04f0;

    public static final int NO_RESET        = 0x0800;

    private static final int _FG_SET         = 0x0200;
    private static final int _BG_SET         = 0x0400;

    public static String toAnsi (String str, int bg_fg_colors)
    {
        StringBuilder sb = new StringBuilder ();

        // Start foreground
        sb.append ("\033[");

        if ((bg_fg_colors & FG_BOLD) != 0)
        {
            sb.append ("1;");
        }

        if ((bg_fg_colors & _FG_SET) != 0)
        {
            sb.append ("38;5;" + (bg_fg_colors & 0x000f));
        }
        else // Foreground was NOT set
        {
            sb.append ("39");
        }
        sb.append ("m");

        // Start background
        sb.append ("\033[");

        if ((bg_fg_colors & _BG_SET) != 0)
        {
            sb.append ("48;5;" + ((bg_fg_colors & 0x00f0) >> 4));
        }
        else // Background was NOT set
        {
            sb.append ("49");
        }
        sb.append ("m");
        sb.append (str);

        if ((bg_fg_colors & NO_RESET) == 0)
        {
            // Reset attributes to normal
            sb.append ("\033[0m");
        }
        return (sb.toString ());
    }
}

// EOF
