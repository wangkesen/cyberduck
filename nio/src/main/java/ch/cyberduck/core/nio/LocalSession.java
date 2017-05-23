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

import ch.cyberduck.core.AttributedList;
import ch.cyberduck.core.Attributes;
import ch.cyberduck.core.Cache;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.HostKeyCallback;
import ch.cyberduck.core.HostPasswordStore;
import ch.cyberduck.core.ListProgressListener;
import ch.cyberduck.core.LocalFactory;
import ch.cyberduck.core.LoginCallback;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.Session;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.exception.LocalAccessDeniedException;
import ch.cyberduck.core.features.Delete;
import ch.cyberduck.core.features.Directory;
import ch.cyberduck.core.features.Find;
import ch.cyberduck.core.features.Home;
import ch.cyberduck.core.features.Move;
import ch.cyberduck.core.features.Read;
import ch.cyberduck.core.features.Symlink;
import ch.cyberduck.core.features.Touch;
import ch.cyberduck.core.features.UnixPermission;
import ch.cyberduck.core.features.Write;
import ch.cyberduck.core.ssl.X509KeyManager;
import ch.cyberduck.core.ssl.X509TrustManager;
import ch.cyberduck.core.threading.CancelCallback;

import org.apache.log4j.Logger;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;

public class LocalSession extends Session<FileSystem> {
    private static final Logger log = Logger.getLogger(LocalSession.class);

    private Object lock;

    protected LocalSession(final Host h) {
        super(h);
    }

    public LocalSession(final Host h, final X509TrustManager trust, final X509KeyManager key) {
        super(h);
    }

    public java.nio.file.Path toPath(final Path file) throws LocalAccessDeniedException {
        return this.toPath(file.getAbsolute());
    }

    public java.nio.file.Path toPath(final String path) throws LocalAccessDeniedException {
        try {
            return client.getPath(path.replaceFirst("^/(.:[/\\\\])", "$1"));
        }
        catch(InvalidPathException e) {
            throw new LocalAccessDeniedException(e.getReason(), e);
        }
    }

    @Override
    protected FileSystem connect(final HostKeyCallback key) throws BackgroundException {
        return FileSystems.getDefault();
    }

    @Override
    public AttributedList<Path> list(final Path directory, final ListProgressListener listener) throws BackgroundException {
        return new LocalListService(this).list(directory, listener);
    }

    @Override
    public void login(final HostPasswordStore keychain, final LoginCallback prompt, final CancelCallback cancel, final Cache cache) throws BackgroundException {
        final Path home = new LocalHomeFinderFeature(this).find();
        try {
            lock = LocalFactory.get(this.toPath(home).toString()).lock(true);
        }
        catch(LocalAccessDeniedException e) {
            log.debug(String.format("Ignore failure obtaining lock for %s", home));
        }
    }

    @Override
    protected void logout() throws BackgroundException {
        final Path home = new LocalHomeFinderFeature(this).find();
        LocalFactory.get(this.toPath(home).toString()).release(lock);
    }

    protected boolean isPosixFilesystem() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T _getFeature(final Class<T> type) {
        if(type == Touch.class) {
            return (T) new LocalTouchFeature(this);
        }
        if(type == Find.class) {
            return (T) new LocalFindFeature(this);
        }
        if(type == Attributes.class) {
            return (T) new LocalAttributesFinderFeature(this);
        }
        if(type == Read.class) {
            return (T) new LocalReadFeature(this);
        }
        if(type == Write.class) {
            return (T) new LocalWriteFeature(this);
        }
        if(type == Delete.class) {
            return (T) new LocalDeleteFeature(this);
        }
        if(type == Move.class) {
            return (T) new LocalMoveFeature(this);
        }
        if(type == Directory.class) {
            return (T) new LocalDirectoryFeature(this);
        }
        if(type == Symlink.class) {
            if(this.isPosixFilesystem()) {
                return (T) new LocalSymlinkFeature(this);
            }
        }
        if(type == UnixPermission.class) {
            if(this.isPosixFilesystem()) {
                return (T) new LocalUnixPermissionFeature(this);
            }
        }
        if(type == Home.class) {
            return (T) new LocalHomeFinderFeature(this);
        }
        return super._getFeature(type);
    }
}
