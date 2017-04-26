package ch.cyberduck.core.s3;

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

import ch.cyberduck.core.AlphanumericRandomStringService;
import ch.cyberduck.core.Credentials;
import ch.cyberduck.core.DisabledCancelCallback;
import ch.cyberduck.core.DisabledHostKeyCallback;
import ch.cyberduck.core.DisabledLoginCallback;
import ch.cyberduck.core.DisabledPasswordCallback;
import ch.cyberduck.core.DisabledPasswordStore;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.LoginOptions;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathCache;
import ch.cyberduck.core.cryptomator.CryptoDeleteFeature;
import ch.cyberduck.core.cryptomator.CryptoDirectoryFeature;
import ch.cyberduck.core.cryptomator.CryptoFindFeature;
import ch.cyberduck.core.cryptomator.CryptoMoveFeature;
import ch.cyberduck.core.cryptomator.CryptoTouchFeature;
import ch.cyberduck.core.cryptomator.CryptoVault;
import ch.cyberduck.core.exception.LoginCanceledException;
import ch.cyberduck.core.features.Delete;
import ch.cyberduck.core.shared.DefaultFindFeature;
import ch.cyberduck.core.transfer.TransferStatus;
import ch.cyberduck.core.vault.DefaultVaultRegistry;
import ch.cyberduck.test.IntegrationTest;

import org.jets3t.service.model.StorageObject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Arrays;
import java.util.EnumSet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
public class S3MoveFeatureTest {

    @Test
    public void testMove() throws Exception {
        final Host host = new Host(new S3Protocol(), new S3Protocol().getDefaultHostname(), new Credentials(
                System.getProperties().getProperty("s3.key"), System.getProperties().getProperty("s3.secret")
        ));
        final S3Session session = new S3Session(host);
        session.open(new DisabledHostKeyCallback());
        session.login(new DisabledPasswordStore(), new DisabledLoginCallback(), new DisabledCancelCallback(), PathCache.empty());
        final Path home = new Path("test-us-east-1-cyberduck", EnumSet.of(Path.Type.directory, Path.Type.volume));
        final Path vault = new Path(home, new AlphanumericRandomStringService().random(), EnumSet.of(Path.Type.directory));
        final Path folder = new Path(vault, new AlphanumericRandomStringService().random(), EnumSet.of(Path.Type.directory));
        final Path file = new Path(folder, new AlphanumericRandomStringService().random(), EnumSet.of(Path.Type.file));
        final CryptoVault cryptomator = new CryptoVault(vault, new DisabledPasswordStore()).create(session, null, new DisabledPasswordCallback() {
            @Override
            public void prompt(final Credentials credentials, final String title, final String reason, final LoginOptions options) throws LoginCanceledException {
                credentials.setPassword("vault");
            }
        });
        session.withRegistry(new DefaultVaultRegistry(new DisabledPasswordStore(), new DisabledPasswordCallback(), cryptomator));
        new CryptoDirectoryFeature<StorageObject>(session, new S3DirectoryFeature(session, new S3WriteFeature(session)), new S3WriteFeature(session), cryptomator).mkdir(folder, null, new TransferStatus());
        new CryptoTouchFeature<StorageObject>(session, new S3TouchFeature(session), new S3WriteFeature(session), cryptomator).touch(file, new TransferStatus());
        assertTrue(new CryptoFindFeature(session, new DefaultFindFeature(session), cryptomator).find(file));
        final CryptoMoveFeature move = new CryptoMoveFeature(session, new S3MoveFeature(session, new S3AccessControlListFeature(session)), new S3DefaultDeleteFeature(session), new S3ObjectListService(session), cryptomator);
        // rename file
        final Path fileRenamed = new Path(folder, new AlphanumericRandomStringService().random(), EnumSet.of(Path.Type.file));
        move.move(file, fileRenamed, false, new Delete.DisabledCallback());
        assertFalse(new CryptoFindFeature(session, new S3FindFeature(session), cryptomator).find(file));
        assertTrue(new CryptoFindFeature(session, new S3FindFeature(session), cryptomator).find(fileRenamed));
        // rename folder
        final Path folderRenamed = new Path(vault, new AlphanumericRandomStringService().random(), EnumSet.of(Path.Type.directory));
        move.move(folder, folderRenamed, false, new Delete.DisabledCallback());
        assertFalse(new CryptoFindFeature(session, new S3FindFeature(session), cryptomator).find(folder));
        assertTrue(new CryptoFindFeature(session, new S3FindFeature(session), cryptomator).find(folderRenamed));
        new CryptoDeleteFeature(session, new S3DefaultDeleteFeature(session), cryptomator).delete(Arrays.asList(folderRenamed, fileRenamed), new DisabledLoginCallback(), new Delete.DisabledCallback());
        session.close();
    }
}