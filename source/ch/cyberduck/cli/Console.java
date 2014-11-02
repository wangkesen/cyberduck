package ch.cyberduck.cli;

/*
 * Copyright (c) 2002-2014 David Kocher. All rights reserved.
 * http://cyberduck.io/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Bug fixes, suggestions and comments should be sent to:
 * feedback@cyberduck.io
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

/**
 * @version $Id$
 */
public class Console {

    private final java.io.Console console = System.console();

    public String readLine(String format, Object... args) {
        if(console != null) {
            return console.readLine(format, args);
        }
        System.out.print(String.format(format, args));
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            return reader.readLine();
        }
        catch(IOException e) {
            return null;
        }
    }

    public char[] readPassword(String format, Object... args) {
        if(console != null) {
            return console.readPassword(format, args);
        }
        return this.readLine(format, args).toCharArray();
    }

    public void printf(final String format, String... args) {
        if(console != null) {
            if(Arrays.asList(args).isEmpty()) {
                console.writer().print(format);
            }
            else {
                console.writer().printf(format, args);
            }
        }
        else {
            System.out.printf(format, args);
        }
    }
}