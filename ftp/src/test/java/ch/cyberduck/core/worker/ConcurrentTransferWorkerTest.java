package ch.cyberduck.core.worker;

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

import ch.cyberduck.core.*;
import ch.cyberduck.core.exception.LoginCanceledException;
import ch.cyberduck.core.ftp.FTPProtocol;
import ch.cyberduck.core.io.DisabledStreamListener;
import ch.cyberduck.core.pool.DefaultSessionPool;
import ch.cyberduck.core.pool.SessionPool;
import ch.cyberduck.core.preferences.TemporaryApplicationResourcesFinder;
import ch.cyberduck.core.ssl.DefaultX509KeyManager;
import ch.cyberduck.core.ssl.DisabledX509TrustManager;
import ch.cyberduck.core.transfer.DisabledTransferErrorCallback;
import ch.cyberduck.core.transfer.DisabledTransferPrompt;
import ch.cyberduck.core.transfer.Transfer;
import ch.cyberduck.core.transfer.TransferAction;
import ch.cyberduck.core.transfer.TransferItem;
import ch.cyberduck.core.transfer.TransferOptions;
import ch.cyberduck.core.transfer.TransferSpeedometer;
import ch.cyberduck.core.transfer.UploadTransfer;
import ch.cyberduck.core.vault.DefaultVaultRegistry;

import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConcurrentTransferWorkerTest {

    private static int PORT_NUMBER = ThreadLocalRandom.current().nextInt(2000, 3000);

    private final Protocol protocol = new AbstractProtocol() {
        public String getIdentifier() {
            return this.getScheme().name();
        }

        @Override
        public int getDefaultPort() {
            return PORT_NUMBER;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public String getProvider() {
            return "embedded";
        }

        public Scheme getScheme() {
            return Scheme.ftp;
        }

        @Override
        public String getPrefix() {
            return new FTPProtocol().getPrefix();
        }
    };

    private static FtpServer server;

    @BeforeClass
    public static void start() throws Exception {
        final FtpServerFactory serverFactory = new FtpServerFactory();
        final PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
        userManagerFactory.setUrl(ConcurrentTransferWorkerTest.class.getResource("/ftpserver-user.properties"));
        final UserManager userManager = userManagerFactory.createUserManager();
        BaseUser user = new BaseUser();
        user.setName("test");
        user.setPassword("test");
        user.setHomeDirectory(new TemporaryApplicationResourcesFinder().find().getAbsolute());
        List<Authority> authorities = new ArrayList<Authority>();
        authorities.add(new WritePermission());
        authorities.add(new ConcurrentLoginPermission(2, 2));
        user.setAuthorities(authorities);
        userManager.save(user);
        serverFactory.setUserManager(userManager);
        final ListenerFactory factory = new ListenerFactory();
        factory.setPort(PORT_NUMBER);
        serverFactory.addListener("default", factory.createListener());
        server = serverFactory.createServer();
        server.start();
    }

    @AfterClass
    public static void stop() throws FtpException {
        server.stop();
    }

    @Test
    public void testConcurrentSessions() throws Exception {
        final int files = 20;
        final int connections = 2;
        final List<TransferItem> list = new ArrayList<TransferItem>();
        final Local file = new Local(File.createTempFile(UUID.randomUUID().toString(), "t").getAbsolutePath());
        for(int i = 1; i <= files; i++) {
            list.add(new TransferItem(new Path(String.format("/t%d", i), EnumSet.of(Path.Type.file)), file));
        }
        final Host host = new Host(protocol, "localhost", PORT_NUMBER, new Credentials("test", "test"));
        final Transfer transfer = new UploadTransfer(host, list);
        final DefaultSessionPool pool = new DefaultSessionPool(
                new LoginConnectionService(new DisabledLoginCallback() {
                    @Override
                    public void prompt(final Host bookmark, final Credentials credentials, final String title, final String reason, final LoginOptions options) throws LoginCanceledException {
                        credentials.setPassword("test");
                    }

                    @Override
                    public void warn(final Protocol protocol, final String title, final String message, final String continueButton, final String disconnectButton, final String preference) throws LoginCanceledException {
                        //
                    }
                }, new DisabledHostKeyCallback(), new DisabledPasswordStore(),
                        new DisabledProgressListener()),
                new DisabledX509TrustManager(), new DefaultX509KeyManager(),
                new DefaultVaultRegistry(new DisabledPasswordCallback()), PathCache.empty(), new DisabledTranscriptListener(), host);
        final ConcurrentTransferWorker worker = new ConcurrentTransferWorker(
                pool.withMaxTotal(connections), SessionPool.DISCONNECTED,
                transfer, new TransferOptions(), new TransferSpeedometer(transfer), new DisabledTransferPrompt() {
            @Override
            public TransferAction prompt(final TransferItem file) {
                return TransferAction.overwrite;
            }
        }, new DisabledTransferErrorCallback(),
                new DisabledLoginCallback(), new DisabledProgressListener(), new DisabledStreamListener()
        );
        pool.withMaxTotal(connections);
        final Session<?> s = worker.borrow(ConcurrentTransferWorker.Connection.source);
        final Session<?> session = worker.borrow(ConcurrentTransferWorker.Connection.source);
        assertTrue(worker.run(session, session));
        worker.release(session, ConcurrentTransferWorker.Connection.source);
        worker.release(s, ConcurrentTransferWorker.Connection.source);
        assertEquals(0L, transfer.getTransferred(), 0L);
    }
}
