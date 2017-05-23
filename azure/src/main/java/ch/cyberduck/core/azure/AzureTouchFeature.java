package ch.cyberduck.core.azure;

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

import ch.cyberduck.core.DisabledConnectionCallback;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.features.Touch;
import ch.cyberduck.core.features.Write;
import ch.cyberduck.core.io.Checksum;
import ch.cyberduck.core.io.DefaultStreamCloser;
import ch.cyberduck.core.transfer.TransferStatus;

import org.apache.commons.io.input.NullInputStream;

import com.microsoft.azure.storage.OperationContext;

public class AzureTouchFeature implements Touch<Void> {

    private Write<Void> writer;

    public AzureTouchFeature(final AzureSession session, final OperationContext context) {
        this(new AzureWriteFeature(session, context));
    }

    public AzureTouchFeature(final Write<Void> write) {
        this.writer = write;
    }

    @Override
    public boolean isSupported(final Path workdir) {
        return !workdir.isRoot();
    }

    @Override
    public Path touch(final Path file, final TransferStatus status) throws BackgroundException {
        if(Checksum.NONE == status.getChecksum()) {
            status.setChecksum(writer.checksum().compute(new NullInputStream(0L), status));
        }
        new DefaultStreamCloser().close(writer.write(file, status, new DisabledConnectionCallback()));
        return file;
    }

    @Override
    public AzureTouchFeature withWriter(final Write<Void> writer) {
        this.writer = writer;
        return this;
    }
}
