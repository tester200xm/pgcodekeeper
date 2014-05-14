
package ru.taximaxim.codekeeper.ui.handlers;

import java.util.List;
import java.util.Map;

import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

import ru.taximaxim.codekeeper.ui.Activator;
import ru.taximaxim.codekeeper.ui.AddonExternalTools;
import ru.taximaxim.codekeeper.ui.UIConsts;

public class About {
    @Execute
    private void execute(Shell parentShell) {
        Map<String, List<String>> versions = Activator.getPluginVersions();
        
        String message = String.format(
                "pgCodeKeeper version: %s\n\n"
                + "%s version: %s\n"
                + "%s version: %s\n\n"
                + "%s version: %s\n\n"
                + "pg_dump version: %s",
                
                versions.get(UIConsts.MAINAPP_PLUGIN_ID).get(0),
                
                UIConsts.PLUGIN_ID,
                versions.get(UIConsts.PLUGIN_ID).get(0),
                
                UIConsts.APGDIFF_PLUGIN_ID,
                versions.get(UIConsts.APGDIFF_PLUGIN_ID).get(0),
                
                UIConsts.JGIT_PLUGIN_ID,
                versions.get(UIConsts.JGIT_PLUGIN_ID).get(0),
                
                AddonExternalTools.getPgdumpVersion());
        
        MessageDialog.open(MessageDialog.INFORMATION, parentShell,
                "About pgCodeKeeper...", message, SWT.RESIZE);
    }
}