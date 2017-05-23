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

import ch.cyberduck.core.ConnectionCallback;
import ch.cyberduck.core.Local;
import ch.cyberduck.core.LocaleFactory;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathContainerService;
import ch.cyberduck.core.exception.AccessDeniedException;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.exception.ChecksumException;
import ch.cyberduck.core.exception.ConnectionCanceledException;
import ch.cyberduck.core.exception.InteroperabilityException;
import ch.cyberduck.core.features.Upload;
import ch.cyberduck.core.features.Write;
import ch.cyberduck.core.http.HttpUploadFeature;
import ch.cyberduck.core.io.BandwidthThrottle;
import ch.cyberduck.core.io.MD5ChecksumCompute;
import ch.cyberduck.core.io.StreamCopier;
import ch.cyberduck.core.io.StreamListener;
import ch.cyberduck.core.io.StreamProgress;
import ch.cyberduck.core.preferences.Preferences;
import ch.cyberduck.core.preferences.PreferencesFactory;
import ch.cyberduck.core.threading.BackgroundExceptionCallable;
import ch.cyberduck.core.threading.DefaultRetryCallable;
import ch.cyberduck.core.threading.DefaultThreadPool;
import ch.cyberduck.core.threading.ThreadPool;
import ch.cyberduck.core.transfer.TransferStatus;

import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jets3t.service.ServiceException;
import org.jets3t.service.model.MultipartCompleted;
import org.jets3t.service.model.MultipartPart;
import org.jets3t.service.model.MultipartUpload;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.model.StorageObject;

import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class S3MultipartUploadService extends HttpUploadFeature<StorageObject, MessageDigest> {
    private static final Logger log = Logger.getLogger(S3MultipartUploadService.class);

    private final Preferences preferences
            = PreferencesFactory.get();

    private final S3Session session;

    private final PathContainerService containerService
            = new S3PathContainerService();

    private final S3DefaultMultipartService multipartService;

    private Write<StorageObject> writer;
    /**
     * A split smaller than 5M is not allowed
     */
    private final Long partsize;

    private final Integer concurrency;

    public S3MultipartUploadService(final S3Session session, final Write<StorageObject> writer) {
        this(session, writer, PreferencesFactory.get().getLong("s3.upload.multipart.size"),
                PreferencesFactory.get().getInteger("s3.upload.multipart.concurrency"));
    }

    public S3MultipartUploadService(final S3Session session, final Write<StorageObject> writer, final Long partsize, final Integer concurrency) {
        super(writer);
        this.session = session;
        this.multipartService = new S3DefaultMultipartService(session);
        this.writer = writer;
        this.partsize = partsize;
        this.concurrency = concurrency;
    }

    @Override
    public StorageObject upload(final Path file, final Local local, final BandwidthThrottle throttle, final StreamListener listener,
                                final TransferStatus status, final ConnectionCallback callback) throws BackgroundException {
        final DefaultThreadPool pool = new DefaultThreadPool("multipart", concurrency);
        try {
            MultipartUpload multipart = null;
            try {
                if(status.isAppend() || status.isRetry()) {
                    final List<MultipartUpload> list = multipartService.find(file);
                    if(!list.isEmpty()) {
                        multipart = list.iterator().next();
                    }
                }
            }
            catch(AccessDeniedException | InteroperabilityException e) {
                log.warn(String.format("Ignore failure listing incomplete multipart uploads. %s", e.getDetail()));
            }
            final List<MultipartPart> completed = new ArrayList<MultipartPart>();
            // Not found or new upload
            if(null == multipart) {
                if(log.isInfoEnabled()) {
                    log.info("No pending multipart upload found");
                }
                final S3Object object = new S3WriteFeature(session, new S3DisabledMultipartService())
                        .getDetails(containerService.getKey(file), status);
                // ID for the initiated multipart upload.
                multipart = session.getClient().multipartStartUpload(containerService.getContainer(file).getName(), object);
                if(log.isDebugEnabled()) {
                    log.debug(String.format("Multipart upload started for %s with ID %s", multipart.getObjectKey(), multipart.getUploadId()));
                }
            }
            else {
                if(status.isAppend() || status.isRetry()) {
                    // Add already completed parts
                    completed.addAll(multipartService.list(multipart));
                }
            }
            try {
                final List<Future<MultipartPart>> parts = new ArrayList<Future<MultipartPart>>();
                long remaining = status.getLength();
                long offset = 0;
                for(int partNumber = 1; remaining > 0; partNumber++) {
                    boolean skip = false;
                    if(status.isAppend() || status.isRetry()) {
                        if(log.isInfoEnabled()) {
                            log.info(String.format("Determine if part number %d can be skipped", partNumber));
                        }
                        for(MultipartPart c : completed) {
                            if(c.getPartNumber().equals(partNumber)) {
                                if(log.isInfoEnabled()) {
                                    log.info(String.format("Skip completed part number %d", partNumber));
                                }
                                skip = true;
                                offset += c.getSize();
                                break;
                            }
                        }
                    }
                    if(!skip) {
                        // Last part can be less than 5 MB. Adjust part size.
                        final Long length = Math.min(Math.max(((status.getLength() + status.getOffset()) / S3DefaultMultipartService.MAXIMUM_UPLOAD_PARTS), partsize), remaining);
                        // Submit to queue
                        parts.add(this.submit(pool, file, local, throttle, listener, status, multipart, partNumber, offset, length, callback));
                        remaining -= length;
                        offset += length;
                    }
                }
                for(Future<MultipartPart> future : parts) {
                    try {
                        completed.add(future.get());
                    }
                    catch(InterruptedException e) {
                        log.error("Part upload failed with interrupt failure");
                        status.setCanceled();
                        throw new ConnectionCanceledException(e);
                    }
                    catch(ExecutionException e) {
                        log.warn(String.format("Part upload failed with execution failure %s", e.getMessage()));
                        if(e.getCause() instanceof BackgroundException) {
                            throw (BackgroundException) e.getCause();
                        }
                        throw new BackgroundException(e.getCause());
                    }
                }
                // Combining all the given parts into the final object. Processing of a Complete Multipart Upload request
                // could take several minutes to complete. Because a request could fail after the initial 200 OK response
                // has been sent, it is important that you check the response body to determine whether the request succeeded.
                final MultipartCompleted complete = session.getClient().multipartCompleteUpload(multipart, completed);
                if(log.isInfoEnabled()) {
                    log.info(String.format("Completed multipart upload for %s with %d parts and checksum %s",
                            complete.getObjectKey(), completed.size(), complete.getEtag()));
                }
                if(file.getType().contains(Path.Type.encrypted)) {
                    log.warn(String.format("Skip checksum verification for %s with client side encryption enabled", file));
                }
                else {
                    final StringBuilder concat = new StringBuilder();
                    for(MultipartPart part : completed) {
                        concat.append(part.getEtag());
                    }
                    final String expected = String.format("%s-%d",
                            new MD5ChecksumCompute().compute(concat.toString(), status), completed.size());
                    final String reference;
                    if(complete.getEtag().startsWith("\"") && complete.getEtag().endsWith("\"")) {
                        reference = complete.getEtag().substring(1, complete.getEtag().length() - 1);
                    }
                    else {
                        reference = complete.getEtag();
                    }
                    if(!expected.equals(reference)) {
                        if(session.getHost().getHostname().endsWith(preferences.getProperty("s3.hostname.default"))) {
                            throw new ChecksumException(MessageFormat.format(LocaleFactory.localizedString("Upload {0} failed", "Error"), file.getName()),
                                    MessageFormat.format("Mismatch between MD5 hash {0} of uploaded data and ETag {1} returned by the server",
                                            expected, reference));
                        }
                        else {
                            log.warn(String.format("Mismatch between MD5 hash %s of uploaded data and ETag %s returned by the server", expected, reference));
                        }
                    }
                }
                // Mark parent status as complete
                status.setComplete();
                final StorageObject object = new StorageObject(containerService.getKey(file));
                object.setETag(complete.getEtag());
                return object;
            }
            finally {
                // Cancel future tasks
                pool.shutdown(false);
            }
        }
        catch(ServiceException e) {
            throw new S3ExceptionMappingService().map("Upload {0} failed", e, file);
        }
    }

    private Future<MultipartPart> submit(final ThreadPool pool, final Path file, final Local local,
                                         final BandwidthThrottle throttle, final StreamListener listener,
                                         final TransferStatus overall, final MultipartUpload multipart,
                                         final int partNumber, final long offset, final long length, final ConnectionCallback callback) throws BackgroundException {
        if(log.isInfoEnabled()) {
            log.info(String.format("Submit part %d of %s to queue with offset %d and length %d", partNumber, file, offset, length));
        }
        return pool.execute(new DefaultRetryCallable<MultipartPart>(new BackgroundExceptionCallable<MultipartPart>() {
            @Override
            public MultipartPart call() throws BackgroundException {
                if(overall.isCanceled()) {
                    throw new ConnectionCanceledException();
                }
                final Map<String, String> requestParameters = new HashMap<String, String>();
                requestParameters.put("uploadId", multipart.getUploadId());
                requestParameters.put("partNumber", String.valueOf(partNumber));
                final TransferStatus status = new TransferStatus()
                        .length(length)
                        .skip(offset)
                        .withParameters(requestParameters);
                status.setHeader(overall.getHeader());
                status.setNonces(overall.getNonces());
                switch(session.getSignatureVersion()) {
                    case AWS4HMACSHA256:
                        status.setChecksum(writer.checksum()
                                .compute(StreamCopier.skip(new BoundedInputStream(local.getInputStream(), offset + length), offset), status)
                        );
                        break;
                }
                status.setSegment(true);
                final StorageObject part = S3MultipartUploadService.super.upload(
                        file, local, throttle, listener, status, overall, new StreamProgress() {
                            @Override
                            public void progress(final long bytes) {
                                status.progress(bytes);
                                // Discard sent bytes in overall progress if there is an error reply for segment.
                                overall.progress(bytes);
                            }

                            @Override
                            public void setComplete() {
                                status.setComplete();
                            }
                        }, callback);
                if(log.isInfoEnabled()) {
                    log.info(String.format("Received response %s for part number %d", part, partNumber));
                }
                // Populate part with response data that is accessible via the object's metadata
                return new MultipartPart(partNumber,
                        null == part.getLastModifiedDate() ? new Date(System.currentTimeMillis()) : part.getLastModifiedDate(),
                        null == part.getETag() ? StringUtils.EMPTY : part.getETag(),
                        part.getContentLength());

            }
        }, overall));
    }

    @Override
    public Upload<StorageObject> withWriter(final Write<StorageObject> writer) {
        this.writer = writer;
        return super.withWriter(writer);
    }
}
