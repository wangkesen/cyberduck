package ch.cyberduck.core.synchronization;

/*
 * Copyright (c) 2012 David Kocher. All rights reserved.
 * http://cyberduck.ch/
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
 * dkocher@cyberduck.ch
 */

import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathAttributes;
import ch.cyberduck.core.exception.BackgroundException;

import org.apache.log4j.Logger;

/**
 * @version $Id$
 */
public class SizeComparisonService implements ComparisonService {
    private static final Logger log = Logger.getLogger(ComparisonService.class);

    @Override
    public Comparison compare(final Path file) throws BackgroundException {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Compare size for %s", file.getAbsolute()));
        }
        final PathAttributes attributes = file.attributes();
        if(attributes.isDirectory()) {
            return Comparison.notequal;
        }
        else {
            //fist make sure both files are larger than 0 bytes
            if(attributes.getSize() == 0 && file.getLocal().attributes().getSize() == 0) {
                return Comparison.equal;
            }
            if(attributes.getSize() == 0) {
                return Comparison.local;
            }
            if(file.getLocal().attributes().getSize() == 0) {
                return Comparison.remote;
            }
            if(attributes.getSize() == file.getLocal().attributes().getSize()) {
                return Comparison.equal;
            }
            // Different file size
            return Comparison.notequal;
        }
    }
}
