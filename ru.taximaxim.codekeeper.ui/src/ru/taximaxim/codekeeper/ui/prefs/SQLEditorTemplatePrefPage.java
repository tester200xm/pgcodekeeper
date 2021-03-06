package ru.taximaxim.codekeeper.ui.prefs;

import org.eclipse.ui.texteditor.templates.TemplatePreferencePage;

import ru.taximaxim.codekeeper.ui.Activator;
import ru.taximaxim.codekeeper.ui.Log;
import ru.taximaxim.codekeeper.ui.sqledit.SQLEditorTemplateManager;

public class SQLEditorTemplatePrefPage extends TemplatePreferencePage {

    public SQLEditorTemplatePrefPage() {
        try {
            setPreferenceStore(Activator.getDefault().getPreferenceStore());
            setTemplateStore(SQLEditorTemplateManager.getInstance()
                    .getTemplateStore());
            setContextTypeRegistry(SQLEditorTemplateManager.getInstance()
                    .getContextTypeRegistry());
        } catch (Exception ex) {
            Log.log(Log.LOG_ERROR, "Cannot get sql templates", ex); //$NON-NLS-1$
        }
    }

    @Override
    protected boolean isShowFormatterSetting() {
        return false;
    }
}
