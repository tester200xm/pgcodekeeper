package ru.taximaxim.codekeeper.ui.parts;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.core.runtime.Assert;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.core.di.extensions.EventTopic;
import org.eclipse.e4.core.di.extensions.Preference;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;

import ru.taximaxim.codekeeper.apgdiff.model.difftree.TreeElement;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.TreeElement.DiffSide;
import ru.taximaxim.codekeeper.ui.Log;
import ru.taximaxim.codekeeper.ui.PgCodekeeperUIException;
import ru.taximaxim.codekeeper.ui.UIConsts;
import ru.taximaxim.codekeeper.ui.UIConsts.DB_UPDATE_PREF;
import ru.taximaxim.codekeeper.ui.UIConsts.EVENT;
import ru.taximaxim.codekeeper.ui.UIConsts.PART;
import ru.taximaxim.codekeeper.ui.UIConsts.PREF;
import ru.taximaxim.codekeeper.ui.UIConsts.PROJ_PREF;
import ru.taximaxim.codekeeper.ui.dbstore.DbPicker;
import ru.taximaxim.codekeeper.ui.dialogs.ExceptionNotifier;
import ru.taximaxim.codekeeper.ui.dialogs.ManualDepciesDialog;
import ru.taximaxim.codekeeper.ui.differ.DbSource;
import ru.taximaxim.codekeeper.ui.differ.DiffPaneViewer;
import ru.taximaxim.codekeeper.ui.differ.DiffTableViewer;
import ru.taximaxim.codekeeper.ui.differ.Differ;
import ru.taximaxim.codekeeper.ui.differ.TreeDiffer;
import ru.taximaxim.codekeeper.ui.localizations.Messages;
import ru.taximaxim.codekeeper.ui.pgdbproject.PgDbProject;
import ru.taximaxim.codekeeper.ui.sqledit.SqlScriptDialog;
import cz.startnet.utils.pgdiff.PgDiffUtils;
import cz.startnet.utils.pgdiff.schema.PgStatement;

public class DiffPartDescr extends DynamicE4View {
    
    // initializer
    private static String s_ProjectPath;

    @Inject
    @Preference(PREF.PGDUMP_EXE_PATH)
    private String exePgdump;
    @Inject
    @Preference(PREF.PGDUMP_CUSTOM_PARAMS)
    private String pgdumpCustom;
    @Inject
    private MPart part;
    @Inject
    private UISynchronize sync;
    @Inject
    private IWorkbenchPage page;
    @Inject
    private IViewPart viewPart;

    private Button btnGetLatest, btnAddDepcy;
    private DiffTableViewer diffTable;
    private Button btnNone, btnDump, btnDb;
    private Button btnGetChanges;
    private Composite containerSrc;
    private DbPicker dbSrc;
    private DiffPaneViewer diffPane;
    /**
     * Remote DB.
     */
    private DbSource dbSource;
    /**
     * Local repo cache.
     */
    private DbSource dbTarget;
    
    public void setDbSource(DbSource dbSource) {
        this.dbSource = dbSource;
        if (diffPane != null) {
            diffPane.setDbTarget(dbSource);
        }
    }

    public void setDbTarget(DbSource dbTarget) {
        this.dbTarget = dbTarget;
        if (diffPane != null) {
            diffPane.setDbSource(dbTarget);
        }
    }

    /**
     * A collection of manually added object dependencies.
     * Keys are dependants, values are lists of dependencies.
     */
    private List<Entry<PgStatement, PgStatement>> manualDepciesSource = new LinkedList<>();
    private List<Entry<PgStatement, PgStatement>> manualDepciesTarget = new LinkedList<>();

    @Inject
    public DiffPartDescr(MPart part, IWorkbenchPage page) {
        super(part, page);
        
        Assert.isNotNull(s_ProjectPath);
        
        part.getPersistedState().put(PART.DIFF_ID, s_ProjectPath);
    }
    
    @PostConstruct
    private void postConstruct(Composite parent, final PgDbProject proj,
            @Named(UIConsts.PREF_STORE) final IPreferenceStore mainPrefs) {
        final Shell shell = parent.getShell();
        
        parent.setLayout(new GridLayout());
        // upper container
        Composite containerUpper = new Composite(parent, SWT.NONE);
        GridLayout gl = new GridLayout(2, false);
        gl.marginHeight = gl.marginWidth = 0;
        containerUpper.setLayout(gl);
        containerUpper.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        btnGetLatest = new Button(containerUpper, SWT.PUSH);
        btnGetLatest.setText(Messages.diffPartDescr_get_latest);
        btnGetLatest.setEnabled(false);
        btnGetLatest.addSelectionListener(new SelectionAdapter() {
            
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (diffTable.getCheckedElementsCount() < 1){
                    MessageBox mb = new MessageBox(shell, SWT.ICON_INFORMATION);
                    mb.setMessage(Messages.please_check_at_least_one_row);
                    mb.setText(Messages.empty_selection);
                    mb.open();
                    return;
                }
                final TreeElement filtered = diffTable.filterDiffTree();

                Differ differ = new Differ(
                        DbSource.fromFilter(dbSource, filtered, DiffSide.LEFT),
                        DbSource.fromFilter(dbTarget,filtered, DiffSide.RIGHT),
                        false);
                differ.setFullDbs(dbSource.getDbObject(), dbTarget.getDbObject());
                differ.setAdditionalDepciesSource(manualDepciesSource);
                differ.setAdditionalDepciesTarget(manualDepciesTarget);
                try {
                    differ.runProgressMonitorDiffer(shell);
                } catch (PgCodekeeperUIException e1) {
                    ExceptionNotifier.showErrorDialog("Cannot get differ", e1);
                    return;
                }

                SqlScriptDialog dialog = new SqlScriptDialog(shell,
                        SqlScriptDialog.INFORMATION, Messages.diffPartDescr_diff_script,
                        Messages.diffPartDescr_this_will_apply_selected_changes_to_your_database,
                        differ, dbSource.getDbObject().flatten(), 
                        mainPrefs.getBoolean(DB_UPDATE_PREF.USE_PSQL_DEPCY));
                dialog.setDangerStatements(
                        mainPrefs.getBoolean(DB_UPDATE_PREF.DROP_TABLE_STATEMENT), 
                        mainPrefs.getBoolean(DB_UPDATE_PREF.ALTER_COLUMN_STATEMENT),
                        mainPrefs.getBoolean(DB_UPDATE_PREF.DROP_COLUMN_STATEMENT));
                if (btnDb.getSelection()) {
                    dialog.setDbParams(dbSrc.txtDbHost.getText(),
                            dbSrc.txtDbPort.getText(), dbSrc.txtDbName.getText(),
                            dbSrc.txtDbUser.getText(), dbSrc.txtDbPass.getText());
                }
                dialog.open();
            }
        });
        
        btnAddDepcy = new Button(containerUpper, SWT.PUSH);
        btnAddDepcy.setText(Messages.diffPartDescr_add_dependencies);
        btnAddDepcy.setEnabled(false);
        btnAddDepcy.addSelectionListener(new SelectionAdapter() {
            
            @Override
            public void widgetSelected(SelectionEvent e) {
                ManualDepciesDialog dialog = new ManualDepciesDialog(shell,
                        manualDepciesSource, manualDepciesTarget,
                        dbSource.getDbObject().flatten(),
                        dbTarget.getDbObject().flatten(),
                        Messages.database, proj.getPrefs().get(PROJ_PREF.REPO_TYPE, ""));
                if (dialog.open() == Dialog.OK) {
                    manualDepciesSource = dialog.getDepciesSourceList();
                    manualDepciesTarget = dialog.getDepciesTargetList();
                }
            }
        });
        // end upper commit comment container

        SashForm sashOuter = new SashForm(parent, SWT.VERTICAL | SWT.SMOOTH);
        sashOuter.setLayoutData(new GridData(GridData.FILL_BOTH));

        // middle container
        final Composite containerDb = new Composite(sashOuter, SWT.NONE);
        gl = new GridLayout(3, false);
        gl.marginHeight = gl.marginWidth = 0;
        gl.horizontalSpacing = gl.verticalSpacing = 2;
        containerDb.setLayout(gl);
        
        diffTable = new DiffTableViewer(containerDb, SWT.FILL, mainPrefs, false);
        diffTable.setLayoutData(new GridData(GridData.FILL_BOTH));
        diffTable.viewer.addSelectionChangedListener(new ISelectionChangedListener() {

                    @Override
                    public void selectionChanged(SelectionChangedEvent event) {
                        StructuredSelection selection = ((StructuredSelection) event
                                .getSelection());
                        
                        if (selection.size() != 1) {
                            diffPane.setInput(null);
                        } else {
                            TreeElement el = (TreeElement) selection.getFirstElement();
                            diffPane.setInput(el);
                        }
                    }
                });
        
        // flip button set up
        final Button btnFlipDbPicker = new Button(containerDb, SWT.PUSH | SWT.FLAT);
        btnFlipDbPicker.setText("\u25B8"); //$NON-NLS-1$
        GridData gd = new GridData(GridData.FILL_VERTICAL);
        gd.widthHint = 20;
        btnFlipDbPicker.setLayoutData(gd);
        btnFlipDbPicker.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                boolean open = containerSrc.getVisible();
                
                containerSrc.setVisible(!open);
                ((GridData) containerSrc.getLayoutData()).exclude = open;
                containerDb.layout();
                
                btnFlipDbPicker.setText(open ? "\u25C2" // ◂ //$NON-NLS-1$
                        : "\u25B8"); // ▸ //$NON-NLS-1$
            }
        });
        
        // middle right container
        containerSrc = new Composite(containerDb, SWT.NONE);
        gl = new GridLayout(2, false);
        gl.marginHeight = gl.marginWidth = 0;
        containerSrc.setLayout(gl);
        
        gd = new GridData(SWT.FILL, SWT.FILL, false, true);
        gd.minimumWidth = 300;
        containerSrc.setLayoutData(gd);
        
        Group grpSrc = new Group(containerSrc, SWT.NONE);
        grpSrc.setText(Messages.diffPartDescr_get_changes_for);
        grpSrc.setLayout(new GridLayout(3, false));

        btnNone = new Button(grpSrc, SWT.RADIO);
        btnNone.setText(Messages.none);
        btnNone.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showDbPicker(false);
                btnGetChanges.setEnabled(false);
            }
        });

        btnDump = new Button(grpSrc, SWT.RADIO);
        btnDump.setText(Messages.dump);
        btnDump.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showDbPicker(false);
                btnGetChanges.setEnabled(true);
            }
        });

        btnDb = new Button(grpSrc, SWT.RADIO);
        btnDb.setText(Messages.db);
        btnDb.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showDbPicker(true);
                btnGetChanges.setEnabled(true);
            }
        });

        btnGetChanges = new Button(containerSrc, SWT.PUSH);
        btnGetChanges.setText(Messages.get_changes);
        btnGetChanges.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true,
                false));
        btnGetChanges.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                setDbTarget(DbSource.fromProject(proj));
                if (btnDump.getSelection()) {
                    FileDialog dialog = new FileDialog(shell);
                    dialog.setText(Messages.choose_dump_file_with_changes);
                    String dumpfile = dialog.open();
                    if (dumpfile != null) {
                        setDbSource(DbSource.fromFile(dumpfile,
                                proj.getPrefs().get(PROJ_PREF.ENCODING, "")));
                    } else {
                        return;
                    }
                } else if (btnDb.getSelection()) {
                    int port;
                    try {
                        String sPort = dbSrc.txtDbPort.getText();
                        port = sPort.isEmpty()? 0 : Integer.parseInt(sPort);
                    } catch (NumberFormatException ex) {
                        MessageBox mb = new MessageBox(shell, SWT.ICON_ERROR);
                        mb.setText(Messages.bad_port);
                        mb.setMessage(Messages.port_must_be_a_number);
                        mb.open();
                        return;
                    }

                    setDbSource(DbSource.fromDb(exePgdump, pgdumpCustom,
                            dbSrc.txtDbHost.getText(), port,
                            dbSrc.txtDbUser.getText(),
                            dbSrc.txtDbPass.getText(),
                            dbSrc.txtDbName.getText(),
                            proj.getPrefs().get(PROJ_PREF.ENCODING, "")));
                } else {
                    ExceptionNotifier.showErrorDialog(Messages.undefined_source_for_db_changes, null);
                    return;
                }
                
                Log.log(Log.LOG_INFO, "Getting changes to generate script"); //$NON-NLS-1$
                TreeDiffer treediffer = new TreeDiffer(dbSource, dbTarget);
                try {
                    new ProgressMonitorDialog(shell).run(true, false, treediffer);
                } catch (InvocationTargetException ex) {
                    ExceptionNotifier.showErrorDialog(Messages.error_in_differ_thread, ex);
                    return;
                } catch (InterruptedException ex) {
                    // assume run() was called as non cancelable
                    ExceptionNotifier.showErrorDialog(
                            Messages.differ_thread_cancelled_shouldnt_happen, ex);
                    return;
                }

                diffTable.setInput(treediffer, true);
                diffPane.setInput(null);
                btnGetLatest.setEnabled(true);
                btnAddDepcy.setEnabled(true);
                manualDepciesSource.clear();
                manualDepciesTarget.clear();
            }
        });

        dbSrc = new DbPicker(containerSrc, SWT.NONE, mainPrefs, false);
        dbSrc.setText(Messages.db_source);
        dbSrc.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false, 2,
                1));

        boolean useDbPicker = false;
        String src = proj.getPrefs().get(PROJ_PREF.SOURCE, "");
        if (src.equals(PROJ_PREF.SOURCE_TYPE_NONE)) {
            btnNone.setSelection(true);
            btnGetChanges.setEnabled(false);
        } else if (src.equals(PROJ_PREF.SOURCE_TYPE_DUMP)) {
            btnDump.setSelection(true);
        } else {
            btnDb.setSelection(true);
            useDbPicker = true;
        }
        showDbPicker(useDbPicker);

        if (useDbPicker) {
            dbSrc.txtDbName.setText(proj.getPrefs().get(PROJ_PREF.DB_NAME, ""));
            dbSrc.txtDbUser.setText(proj.getPrefs().get(PROJ_PREF.DB_USER, ""));
            dbSrc.txtDbPass.setText(proj.getPrefs().get(PROJ_PREF.DB_PASS, ""));
            dbSrc.txtDbHost.setText(proj.getPrefs().get(PROJ_PREF.DB_HOST, ""));
            dbSrc.txtDbPort.setText(String.valueOf(proj
                    .getPrefs().getInt(PROJ_PREF.DB_PORT, 0)));
        }
        // end middle right container
        // end middle container
        
        diffPane = new DiffPaneViewer(sashOuter, SWT.NONE, 
                dbTarget, dbSource, true);
    }

    private void showDbPicker(boolean show) {
        ((GridData) dbSrc.getLayoutData()).exclude = !show;
        dbSrc.setVisible(show);

        dbSrc.getParent().layout();
    }

    @Inject
    private void changeProject(
            PgDbProject proj,
            @Optional
            @EventTopic(EVENT.REOPEN_PROJECT)
            PgDbProject proj2) {
        if (proj == null
                || !proj.getPathToProject().toString().equals(
                        part.getPersistedState().get(PART.DIFF_ID))) {
            sync.asyncExec(new Runnable() {
                
                @Override
                public void run() {
                    page.hideView(viewPart);
                }
            });
        } else if (proj2 != null) {
            sync.asyncExec(new Runnable() {
                
                @Override
                public void run() {
                    diffTable.setInput(null, true);
                    diffPane.setInput(null);
                    btnGetLatest.setEnabled(false);
                    btnAddDepcy.setEnabled(false);
                    manualDepciesSource.clear();
                    manualDepciesTarget.clear();
                }
            });
        }
    }

    public static void openNew(String projectPath, IWorkbenchPage page) {
        Assert.isTrue(s_ProjectPath == null);
        
        try {
            s_ProjectPath = projectPath;
            
            page.showView(PART.DIFF, PgDiffUtils.md5(projectPath),
                    IWorkbenchPage.VIEW_CREATE);
        } catch (PartInitException ex) {
            ExceptionNotifier.showErrorDialog("", ex);
            return;
        } finally {
            s_ProjectPath = null;
        }
    }
}
