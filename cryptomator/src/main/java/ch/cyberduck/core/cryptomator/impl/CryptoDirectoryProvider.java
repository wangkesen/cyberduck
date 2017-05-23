package ch.cyberduck.core.cryptomator.impl;

/*
 * Copyright (c) 2002-2016 iterate GmbH. All rights reserved.
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

import ch.cyberduck.core.AbstractPath;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathAttributes;
import ch.cyberduck.core.RandomStringService;
import ch.cyberduck.core.Session;
import ch.cyberduck.core.UUIDRandomStringService;
import ch.cyberduck.core.cryptomator.ContentReader;
import ch.cyberduck.core.cryptomator.CryptoVault;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.exception.NotfoundException;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

public class CryptoDirectoryProvider {
    private static final Logger log = Logger.getLogger(CryptoDirectoryProvider.class);

    private static final String DATA_DIR_NAME = "d";
    private static final String ROOT_DIR_ID = StringUtils.EMPTY;

    private final Path dataRoot;
    private final CryptoVault cryptomator;

    private final RandomStringService random
            = new UUIDRandomStringService();

    public CryptoDirectoryProvider(final Path vault, final CryptoVault cryptomator) {
        this.dataRoot = new Path(vault, DATA_DIR_NAME, vault.getType());
        this.cryptomator = cryptomator;
    }

    /**
     * Get encrypted filename for given clear text filename with id of parent encrypted directory.
     *
     * @param session     Connection
     * @param directoryId Directory id
     * @param filename    Clear text filename
     * @param type        File type
     * @return Encrypted filename
     */
    public String toEncrypted(final Session<?> session, final String directoryId, final String filename, final EnumSet<AbstractPath.Type> type) throws BackgroundException {
        final String prefix = type.contains(Path.Type.directory) ? CryptoVault.DIR_PREFIX : "";
        final String ciphertextName = String.format("%s%s", prefix,
                cryptomator.getCryptor().fileNameCryptor().encryptFilename(filename, directoryId.getBytes(StandardCharsets.UTF_8)));
        if(log.isDebugEnabled()) {
            log.debug(String.format("Encrypted filename %s to %s", filename, ciphertextName));
        }
        return cryptomator.getFilenameProvider().deflate(session, ciphertextName);
    }

    /**
     * Get encrypted reference for clear text directory path.
     *
     * @param session   Connection
     * @param directory Clear text
     */
    public Path toEncrypted(final Session<?> session, final String directoryId, final Path directory) throws BackgroundException {
        if(directory.getType().contains(Path.Type.directory)) {
            final PathAttributes attributes = new PathAttributes(directory.attributes());
            attributes.setVersionId(null);
            // Remember random directory id for use in vault
            final String id = this.toDirectoryId(session, directory, directoryId);
            if(log.isDebugEnabled()) {
                log.debug(String.format("Set directory ID %s for folder %s", id, directory));
            }
            attributes.setDirectoryId(id);
            attributes.setDecrypted(directory);
            final String directoryIdHash = cryptomator.getCryptor().fileNameCryptor().hashDirectoryId(id);
            // Intermediate directory
            final Path intermediate = new Path(dataRoot, directoryIdHash.substring(0, 2), dataRoot.getType());
            // Add encrypted type
            final EnumSet<AbstractPath.Type> type = EnumSet.copyOf(directory.getType());
            type.add(Path.Type.encrypted);
            type.remove(Path.Type.decrypted);
            return new Path(intermediate, directoryIdHash.substring(2), type, attributes);
        }
        throw new NotfoundException(directory.getAbsolute());
    }

    private String toDirectoryId(final Session<?> session, final Path directory, final String directoryId) throws BackgroundException {
        if(dataRoot.getParent().equals(directory)) {
            return ROOT_DIR_ID;
        }
        if(StringUtils.isBlank(directoryId)) {
            final Path parent = this.toEncrypted(session, directory.getParent().attributes().getDirectoryId(), directory.getParent());
            final String cleartextName = directory.getName();
            final String ciphertextName = this.toEncrypted(session, parent.attributes().getDirectoryId(), cleartextName, EnumSet.of(Path.Type.directory));
            // Read directory id from file
            try {
                if(log.isDebugEnabled()) {
                    log.debug(String.format("Read directory ID for folder %s from %s", directory, ciphertextName));
                }
                final Path metadataFile = new Path(parent, ciphertextName, EnumSet.of(Path.Type.file, Path.Type.encrypted));
                return new ContentReader(session).read(metadataFile);
            }
            catch(NotfoundException e) {
                log.warn(String.format("Missing directory ID for folder %s", directory));
                return random.random();
            }
        }
        return directoryId;
    }
}
