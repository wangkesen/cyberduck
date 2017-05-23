package ch.cyberduck.binding;

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

import ch.cyberduck.binding.application.NSApplication;
import ch.cyberduck.binding.application.NSWindow;
import ch.cyberduck.binding.application.SheetCallback;
import ch.cyberduck.binding.foundation.NSThread;
import ch.cyberduck.core.threading.DefaultMainAction;

import org.apache.log4j.Logger;
import org.rococoa.Foundation;
import org.rococoa.ID;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class SheetInvoker extends ProxyController {
    private static final Logger log = Logger.getLogger(SheetInvoker.class);

    /**
     * Keep a reference to the sheet to protect it from being
     * deallocated as a weak reference before the callback from the runtime
     */
    protected static final Set<SheetInvoker> registry
            = new HashSet<SheetInvoker>();

    private final SheetCallback callback;

    /**
     * The controller of the parent window
     */
    private final NSWindow parent;

    private NSWindow window;

    private final NSApplication application = NSApplication.sharedApplication();

    private final WindowController controller;

    /**
     * Dismiss button clicked
     */
    private int returncode = SheetCallback.CANCEL_OPTION;

    private final CountDownLatch signal
            = new CountDownLatch(1);

    {
        registry.add(this);
    }

    /**
     * The sheet window must be provided later with #setWindow (usually called when loading the NIB file)
     *
     * @param callback Callback
     * @param parent   The controller of the parent window
     * @param sheet    Sheet
     */
    public SheetInvoker(final SheetCallback callback, final WindowController parent, final NSWindow sheet) {
        this.callback = callback;
        this.parent = parent.window();
        this.window = sheet;
        this.controller = null;
    }

    public SheetInvoker(final SheetCallback callback, final WindowController parent, final WindowController controller) {
        this.callback = callback;
        this.parent = parent.window();
        this.controller = controller;
    }

    public SheetInvoker(final SheetCallback callback, final NSWindow parent, final NSWindow sheet) {
        this.callback = callback;
        this.parent = parent;
        this.window = sheet;
        this.controller = null;
    }

    /**
     * @return The tag of the button this sheet was dismissed with
     */
    public int getSelectedOption() {
        return returncode;
    }

    public int beginSheet() {
        if(NSThread.isMainThread()) {
            // No need to call invoke on main thread
            if(controller != null) {
                controller.loadBundle();
                return this.beginSheet(controller.window());
            }
            else {
                return this.beginSheet(window);
            }
        }
        else {
            final SheetInvoker invoker = this;
            invoke(new DefaultMainAction() {
                @Override
                public void run() {
                    //Invoke again on main thread
                    if(controller != null) {
                        controller.loadBundle();
                        invoker.beginSheet(controller.window());
                    }
                    else {
                        invoker.beginSheet(window);
                    }
                }
            }, true);
            if(log.isDebugEnabled()) {
                log.debug("Await sheet dismiss");
            }
            // Synchronize on parent controller. Only display one sheet at once.
            try {
                signal.await();
            }
            catch(InterruptedException e) {
                log.error("Error waiting for sheet dismiss", e);
                callback.callback(SheetCallback.CANCEL_OPTION);
            }
            return returncode;
        }
    }

    protected int beginSheet(final NSWindow sheet) {
        parent.makeKeyAndOrderFront(null);
        application.beginSheet(sheet, //sheet
                this.parentWindow(), // modalForWindow
                this.id(), // modalDelegate
                Foundation.selector("sheetDidClose:returnCode:contextInfo:"),
                null); //context
        return returncode;
    }

    /**
     * Called by the runtime after a sheet has been dismissed. Ends any modal session and
     * sends the returncode to the callback implementation. Also invalidates this controller to be
     * garbage collected and notifies the lock object
     *
     * @param sheet       Sheet window
     * @param returncode  Identifier for the button clicked by the user
     * @param contextInfo Not used
     */
    public void sheetDidClose_returnCode_contextInfo(final NSWindow sheet, final int returncode, final ID contextInfo) {
        sheet.endEditingFor(null);
        sheet.orderOut(contextInfo);
        this.returncode = returncode;
        callback.callback(returncode);
        signal.countDown();
    }

    private NSWindow parentWindow() {
        NSWindow window = parent;
        while(window.attachedSheet() != null) {
            window = window.attachedSheet();
            log.warn(String.format("Window %s has already sheet %s attached", parent, window));
        }
        return window;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        registry.remove(this);
    }
}