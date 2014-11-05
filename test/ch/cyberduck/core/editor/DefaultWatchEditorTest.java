package ch.cyberduck.core.editor;

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

import ch.cyberduck.core.Host;
import ch.cyberduck.core.NullSession;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.local.DefaultLocalTouchFeature;
import ch.cyberduck.core.local.DisabledApplicationQuitCallback;
import ch.cyberduck.core.local.WorkspaceApplicationLauncher;
import ch.cyberduck.core.threading.MainAction;
import ch.cyberduck.ui.AbstractController;

import org.junit.Test;

import java.io.IOException;
import java.util.EnumSet;

public class DefaultWatchEditorTest {

    @Test(expected = IOException.class)
    public void testEditNullApplicationNoFile() throws Exception {
        final DefaultWatchEditor editor = new DefaultWatchEditor(new AbstractController() {
            @Override
            public void invoke(final MainAction runnable, final boolean wait) {
                //
            }
        }, new NullSession(new Host("h")), new WorkspaceApplicationLauncher(), null, new Path("/remote", EnumSet.of(Path.Type.file)));
        editor.edit(new DisabledApplicationQuitCallback());
    }

    @Test
    public void testEditNullApplication() throws Exception {
        final DefaultWatchEditor editor = new DefaultWatchEditor(new AbstractController() {
            @Override
            public void invoke(final MainAction runnable, final boolean wait) {
                //
            }
        }, new NullSession(new Host("h")), new WorkspaceApplicationLauncher(), null, new Path("/remote.txt", EnumSet.of(Path.Type.file)));
        new DefaultLocalTouchFeature().touch(editor.getLocal());
        editor.edit(new DisabledApplicationQuitCallback());
    }
}