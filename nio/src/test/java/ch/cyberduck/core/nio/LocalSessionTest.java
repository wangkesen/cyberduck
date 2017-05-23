package ch.cyberduck.core.nio;

/*
 * Copyright (c) 2002-2017 iterate GmbH. All rights reserved.
 * https://cyberduck.io/
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
 */

import ch.cyberduck.core.DisabledHostKeyCallback;
import ch.cyberduck.core.Host;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LocalSessionTest {

    @Test
    public void toPath() throws Exception {
        final LocalSession session = new LocalSession(new Host(new LocalProtocol(), new LocalProtocol().getDefaultHostname()));
        session.open(new DisabledHostKeyCallback());
        assertNotNull(session.toPath("/Users/username"));
        assertNotNull(session.toPath("/C:\\Users\\Administrator"));
        assertEquals("C:\\Users\\Administrator", "/C:\\Users\\Administrator".replaceFirst("^/(.:[/\\\\])", "$1"));
        assertEquals("C:/Users/Administrator", "/C:/Users/Administrator".replaceFirst("^/(.:[/\\\\])", "$1"));
        session.close();
    }
}