package ch.cyberduck.core.s3;

/*
 * Copyright (c) 2002-2013 David Kocher. All rights reserved.
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
 * Bug fixes, suggestions and comments should be sent to feedback@cyberduck.ch
 */

import ch.cyberduck.core.Credentials;
import ch.cyberduck.core.DisabledCancelCallback;
import ch.cyberduck.core.DisabledHostKeyCallback;
import ch.cyberduck.core.DisabledListProgressListener;
import ch.cyberduck.core.DisabledLoginCallback;
import ch.cyberduck.core.DisabledPasswordStore;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathCache;
import ch.cyberduck.core.exception.NotfoundException;
import ch.cyberduck.core.features.Delete;
import ch.cyberduck.core.transfer.TransferStatus;
import ch.cyberduck.test.IntegrationTest;

import org.jets3t.service.model.S3Object;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

@Category(IntegrationTest.class)
public class S3StorageClassFeatureTest {

    @Test
    public void testGetClasses() throws Exception {
        assertEquals(Arrays.asList(S3Object.STORAGE_CLASS_STANDARD, "STANDARD_IA", S3Object.STORAGE_CLASS_REDUCED_REDUNDANCY, S3Object.STORAGE_CLASS_GLACIER),
                new S3StorageClassFeature(new S3Session(new Host(new S3Protocol()))).getClasses());
    }

    @Test(expected = NotfoundException.class)
    public void testNotFound() throws Exception {
        final S3Session session = new S3Session(
                new Host(new S3Protocol(), new S3Protocol().getDefaultHostname(),
                        new Credentials(
                                System.getProperties().getProperty("s3.key"), System.getProperties().getProperty("s3.secret")
                        )));
        session.open(new DisabledHostKeyCallback());
        session.login(new DisabledPasswordStore(), new DisabledLoginCallback(), new DisabledCancelCallback(), PathCache.empty());
        final Path container = new Path("test-us-east-1-cyberduck", EnumSet.of(Path.Type.volume));
        final Path test = new Path(container, UUID.randomUUID().toString(), EnumSet.of(Path.Type.file));
        final S3StorageClassFeature feature = new S3StorageClassFeature(session);
        feature.getClass(test);
    }

    @Test
    public void testSetClassFile() throws Exception {
        final S3Session session = new S3Session(
                new Host(new S3Protocol(), new S3Protocol().getDefaultHostname(),
                        new Credentials(
                                System.getProperties().getProperty("s3.key"), System.getProperties().getProperty("s3.secret")
                        )));
        session.open(new DisabledHostKeyCallback());
        session.login(new DisabledPasswordStore(), new DisabledLoginCallback(), new DisabledCancelCallback(), PathCache.empty());
        final Path container = new Path("test-us-east-1-cyberduck", EnumSet.of(Path.Type.volume));
        final Path test = new Path(container, UUID.randomUUID().toString(), EnumSet.of(Path.Type.file));
        new S3TouchFeature(session).touch(test, new TransferStatus());
        final S3StorageClassFeature feature = new S3StorageClassFeature(session);
        assertEquals(S3Object.STORAGE_CLASS_STANDARD, feature.getClass(test));
        feature.setClass(test, "STANDARD_IA");
        assertEquals("STANDARD_IA", feature.getClass(test));
        feature.setClass(test, S3Object.STORAGE_CLASS_REDUCED_REDUNDANCY);
        assertEquals(S3Object.STORAGE_CLASS_REDUCED_REDUNDANCY, feature.getClass(test));
        assertEquals(S3Object.STORAGE_CLASS_REDUCED_REDUNDANCY, session.list(container,
                new DisabledListProgressListener()).get(test).attributes().getStorageClass());
        new S3DefaultDeleteFeature(session).delete(Collections.singletonList(test), new DisabledLoginCallback(), new Delete.DisabledCallback());
        session.close();
    }

    @Test
    public void testSetClassPlaceholder() throws Exception {
        final S3Session session = new S3Session(
                new Host(new S3Protocol(), new S3Protocol().getDefaultHostname(),
                        new Credentials(
                                System.getProperties().getProperty("s3.key"), System.getProperties().getProperty("s3.secret")
                        )));
        session.open(new DisabledHostKeyCallback());
        session.login(new DisabledPasswordStore(), new DisabledLoginCallback(), new DisabledCancelCallback(), PathCache.empty());
        final Path container = new Path("test-us-east-1-cyberduck", EnumSet.of(Path.Type.volume));
        final Path test = new S3DirectoryFeature(session, new S3WriteFeature(session, new S3DisabledMultipartService())).mkdir(
                new Path(container, UUID.randomUUID().toString(), EnumSet.of(Path.Type.directory)), null, new TransferStatus());
        final S3StorageClassFeature feature = new S3StorageClassFeature(session);
        assertEquals(S3Object.STORAGE_CLASS_STANDARD, feature.getClass(test));
        feature.setClass(test, "STANDARD_IA");
        assertEquals("STANDARD_IA", feature.getClass(test));
        feature.setClass(test, S3Object.STORAGE_CLASS_REDUCED_REDUNDANCY);
        assertEquals(S3Object.STORAGE_CLASS_REDUCED_REDUNDANCY, feature.getClass(test));
        new S3DefaultDeleteFeature(session).delete(Collections.singletonList(test), new DisabledLoginCallback(), new Delete.DisabledCallback());
        session.close();
    }
}
