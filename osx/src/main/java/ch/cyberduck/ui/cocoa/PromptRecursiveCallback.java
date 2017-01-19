package ch.cyberduck.ui.cocoa;

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

import ch.cyberduck.binding.AlertController;
import ch.cyberduck.binding.WindowController;
import ch.cyberduck.binding.application.NSAlert;
import ch.cyberduck.binding.application.SheetCallback;
import ch.cyberduck.core.LocaleFactory;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.worker.Worker;

import java.text.MessageFormat;

public class PromptRecursiveCallback<T> implements Worker.RecursiveCallback<T> {

    private final WindowController controller;

    private boolean suppressed;

    private boolean option;

    public PromptRecursiveCallback(final WindowController controller) {
        this.controller = controller;
    }

    @Override
    public boolean recurse(final Path directory, final T value) {
        if(suppressed) {
            return option;
        }
        final AlertController alert = new AlertController() {
            @Override
            public void loadBundle() {
                final NSAlert alert = NSAlert.alert();
                alert.setAlertStyle(NSAlert.NSWarningAlertStyle);
                alert.setMessageText(LocaleFactory.localizedString("Apply changes recursively"));
                alert.setInformativeText(MessageFormat.format(LocaleFactory.localizedString("Do you want to set {0} on {1} recursively for all contained files?"),
                        value, directory.getName()));
                alert.addButtonWithTitle(LocaleFactory.localizedString("Continue", "Credentials"));
                alert.addButtonWithTitle(LocaleFactory.localizedString("Cancel"));
                alert.setShowsSuppressionButton(true);
                alert.suppressionButton().setTitle(LocaleFactory.localizedString("Always"));
                super.loadBundle(alert);
            }
        };
        option = alert.beginSheet(controller) == SheetCallback.DEFAULT_OPTION;
        if(alert.isSuppressed()) {
            suppressed = true;
        }
        return option;
    }
}