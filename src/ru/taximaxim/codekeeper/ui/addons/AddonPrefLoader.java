 
package ru.taximaxim.codekeeper.ui.addons;

import java.io.IOException;

import javax.annotation.PostConstruct;

import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.preference.IPreferenceStore;

import ru.taximaxim.codekeeper.ui.ExceptionNotifier;
import ru.taximaxim.codekeeper.ui.UIConsts;
import ru.taximaxim.codekeeper.ui.prefs.UIScopedPreferenceStore;

public class AddonPrefLoader {
        
    @PostConstruct
    private void start(IEclipseContext ctx) {
        ctx.set(UIConsts.PREF_STORE, new UIScopedPreferenceStore());
    }
    
    public static void savePreference(IPreferenceStore mainPrefs, String preference, String value){
        mainPrefs.setValue(preference, value);
      if(mainPrefs.needsSaving() && mainPrefs instanceof IPersistentPreferenceStore) {
          try {
              ((IPersistentPreferenceStore) mainPrefs).save();
          } catch (IOException ex) {
              ExceptionNotifier.notify(ex, "Unexpected error while saving preferences!",
                      null, true, false);
          }
      }
    } 
}