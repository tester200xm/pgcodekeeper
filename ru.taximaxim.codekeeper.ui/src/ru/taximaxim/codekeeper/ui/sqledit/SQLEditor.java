package ru.taximaxim.codekeeper.ui.sqledit;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.ResourceBundle;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.DefaultCharacterPairMatcher;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ICharacterPairMatcher;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.ide.ResourceUtil;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditor;
import org.eclipse.ui.texteditor.ChainedPreferenceStore;
import org.eclipse.ui.texteditor.ContentAssistAction;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.texteditor.SourceViewerDecorationSupport;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.osgi.service.prefs.BackingStoreException;

import cz.startnet.utils.pgdiff.DangerStatement;
import cz.startnet.utils.pgdiff.loader.JdbcConnector;
import cz.startnet.utils.pgdiff.loader.JdbcMsConnector;
import cz.startnet.utils.pgdiff.loader.JdbcRunner;
import cz.startnet.utils.pgdiff.parsers.antlr.ScriptParser;
import cz.startnet.utils.pgdiff.schema.PgObjLocation;
import cz.startnet.utils.pgdiff.schema.StatementActions;
import ru.taximaxim.codekeeper.apgdiff.ApgdiffConsts;
import ru.taximaxim.codekeeper.apgdiff.ApgdiffConsts.JDBC_CONSTS;
import ru.taximaxim.codekeeper.apgdiff.fileutils.TempFile;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;
import ru.taximaxim.codekeeper.ui.Activator;
import ru.taximaxim.codekeeper.ui.IPartAdapter2;
import ru.taximaxim.codekeeper.ui.Log;
import ru.taximaxim.codekeeper.ui.UIConsts.CONTEXT;
import ru.taximaxim.codekeeper.ui.UIConsts.DB_UPDATE_PREF;
import ru.taximaxim.codekeeper.ui.UIConsts.MARKER;
import ru.taximaxim.codekeeper.ui.UIConsts.NATURE;
import ru.taximaxim.codekeeper.ui.UIConsts.PLUGIN_ID;
import ru.taximaxim.codekeeper.ui.UIConsts.PROJ_PATH;
import ru.taximaxim.codekeeper.ui.UIConsts.PROJ_PREF;
import ru.taximaxim.codekeeper.ui.UIConsts.SQL_EDITOR_PREF;
import ru.taximaxim.codekeeper.ui.UIConsts.TEMP_DIR_PATH;
import ru.taximaxim.codekeeper.ui.UiSync;
import ru.taximaxim.codekeeper.ui.consoles.ConsoleFactory;
import ru.taximaxim.codekeeper.ui.dbstore.DbInfo;
import ru.taximaxim.codekeeper.ui.dialogs.ExceptionNotifier;
import ru.taximaxim.codekeeper.ui.editors.ProjectEditorDiffer;
import ru.taximaxim.codekeeper.ui.externalcalls.utils.StdStreamRedirector;
import ru.taximaxim.codekeeper.ui.handlers.AddBuilder;
import ru.taximaxim.codekeeper.ui.job.SingletonEditorJob;
import ru.taximaxim.codekeeper.ui.localizations.Messages;
import ru.taximaxim.codekeeper.ui.pgdbproject.PgDbProject;
import ru.taximaxim.codekeeper.ui.pgdbproject.parser.PgDbParser;
import ru.taximaxim.codekeeper.ui.pgdbproject.parser.UIProjectLoader;
import ru.taximaxim.codekeeper.ui.propertytests.UpdateDdlJobTester;

public class SQLEditor extends AbstractDecoratedTextEditor implements IResourceChangeListener {

    public static final String SCRIPT_PLACEHOLDER = "%script"; //$NON-NLS-1$
    public static final String DB_HOST_PLACEHOLDER = "%host"; //$NON-NLS-1$
    public static final String DB_PORT_PLACEHOLDER = "%port"; //$NON-NLS-1$
    public static final String DB_NAME_PLACEHOLDER = "%db"; //$NON-NLS-1$
    public static final String DB_USER_PLACEHOLDER = "%user"; //$NON-NLS-1$
    public static final String DB_PASS_PLACEHOLDER = "%pass"; //$NON-NLS-1$

    static final String CONTENT_ASSIST = "ContentAssist"; //$NON-NLS-1$

    private final IPreferenceStore mainPrefs = Activator.getDefault().getPreferenceStore();
    private SqlEditorPartListener partListener;

    private DbInfo currentDB;

    private Composite parentComposite;
    private SQLEditorContentOutlinePage fOutlinePage;
    private Image errorTitleImage;
    private PgDbParser parser;

    private ScriptThreadJobWrapper scriptThreadJobWrapper;

    private boolean isMsSqlExternalFile;

    private final Listener parserListener = e -> {
        if (parentComposite == null) {
            return;
        }
        UiSync.exec(parentComposite, () -> {
            if (fOutlinePage != null) {
                Control c = fOutlinePage.getControl();
                if (c != null && !c.isDisposed()) {
                    fOutlinePage.externalRefresh();
                }
            }
            if (getSourceViewer() != null && getSourceViewer().getTextWidget() != null
                    && !getSourceViewer().getTextWidget().isDisposed()) {
                setLineBackground();
            }
        });
    };

    public void saveLastDb(DbInfo lastDb) {
        saveLastDb(lastDb, getEditorInput());
    }

    public static void saveLastDb(DbInfo lastDb, IEditorInput inputForProject) {
        IResource res = ResourceUtil.getResource(inputForProject);
        if (res != null) {
            IEclipsePreferences prefs = PgDbProject.getPrefs(res.getProject());
            if (prefs != null) {
                prefs.put(PROJ_PREF.LAST_DB_STORE_EDITOR, lastDb.getName());
                try {
                    prefs.flush();
                } catch (BackingStoreException ex) {
                    Log.log(ex);
                }
            }
        }
    }

    public void setCurrentDb(DbInfo currentDB) {
        this.currentDB = currentDB;
    }

    public DbInfo getCurrentDb() {
        if (currentDB != null) {
            return currentDB;
        }

        IResource res = ResourceUtil.getResource(getEditorInput());
        if (res != null) {
            IEclipsePreferences prefs = PgDbProject.getPrefs(res.getProject());
            if (prefs != null) {
                return DbInfo.getLastDb(prefs.get(PROJ_PREF.LAST_DB_STORE_EDITOR, "")); //$NON-NLS-1$
            }
        }
        return null;
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        if (IContentOutlinePage.class.isAssignableFrom(adapter) && fOutlinePage != null) {
            return adapter.cast(fOutlinePage);
        }
        return super.getAdapter(adapter);
    }

    @Override
    public void createPartControl(Composite parent) {
        parentComposite = parent;
        super.createPartControl(parent);
        setLineBackground();
        getSite().getService(IContextService.class).activateContext(CONTEXT.EDITOR);
    }

    public void setLineBackground() {
        // TODO who deletes stale annotations after editor refresh?
        List<PgObjLocation> refs = getParser().getObjsForEditor(getEditorInput());
        IAnnotationModel model = getSourceViewer().getAnnotationModel();
        for (PgObjLocation loc : refs) {
            String annotationMsg = null;
            if (loc.getAction() == StatementActions.UPDATE) {
                annotationMsg = "UPDATE statement"; //$NON-NLS-1$
            } else if (loc.getAction() == StatementActions.DROP && loc.getObjType() == DbObjType.TABLE){
                annotationMsg = "DROP TABLE statement"; //$NON-NLS-1$
            } else if (loc.getAction() == StatementActions.ALTER){
                String text = loc.getText();
                if (loc.getObjType() == DbObjType.TABLE) {
                    if (DangerStatement.ALTER_COLUMN.getRegex().matcher(text).matches()) {
                        annotationMsg = "ALTER COLUMN ... TYPE statement"; //$NON-NLS-1$
                    } else if (DangerStatement.DROP_COLUMN.getRegex().matcher(text).matches()) {
                        annotationMsg = "DROP COLUMN statement"; //$NON-NLS-1$
                    }
                } else if (loc.getObjType() == DbObjType.SEQUENCE &&
                        DangerStatement.RESTART_WITH.getRegex().matcher(text).matches()) {
                    annotationMsg = "ALTER SEQUENCE ... RESTART WITH statement"; //$NON-NLS-1$
                }
            }
            if (annotationMsg != null) {
                model.addAnnotation(new Annotation(MARKER.DANGER_ANNOTATION, false, annotationMsg),
                        new Position(loc.getOffset(), loc.getObjLength()));
            }
        }
    }

    @Override
    public void doSave(IProgressMonitor progressMonitor) {
        super.doSave(progressMonitor);
        IResource res = ResourceUtil.getResource(getEditorInput());
        try {
            if (res == null || !UIProjectLoader.isInProject(res)) {
                refreshParser(getParser(), res, progressMonitor);
            }
        } catch (Exception ex) {
            Log.log(ex);
        }
    }

    @Override
    protected void createActions() {
        super.createActions();
        ResourceBundle bundle = ResourceBundle.getBundle(Messages.getBundleName());
        ContentAssistAction action = new ContentAssistAction(bundle, "contentAssist.", this); //$NON-NLS-1$
        action.setActionDefinitionId(ITextEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS);
        setAction(CONTENT_ASSIST, action);
    }

    @Override
    public void init(IEditorSite site, IEditorInput input) throws PartInitException {
        setDocumentProvider(new SQLEditorCommonDocumentProvider());

        super.init(site, input);

        try {
            checkBuilder();
            parser = initParser();
        } catch (PartInitException ex) {
            throw ex;
        } catch (InterruptedException | IOException | CoreException ex) {
            throw new PartInitException(ex.getLocalizedMessage(), ex);
        } catch (Exception ex) {
            // do not destroy UI and create empty parser, if have unexpected error
            Log.log(ex);
            parser = new PgDbParser();
        }
        parser.addListener(parserListener);

        fOutlinePage = new SQLEditorContentOutlinePage(this);
        ResourcesPlugin.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_CHANGE);

        partListener = new SqlEditorPartListener();
        getSite().getPage().addPartListener(partListener);
    }

    @Override
    protected void doSetInput(IEditorInput input) throws CoreException {
        setPreferenceStore(createCombinedPreferenceStore());
        super.doSetInput(input);
    }

    private IPreferenceStore createCombinedPreferenceStore() {
        List<IPreferenceStore> stores = new ArrayList<>(2);
        stores.add(mainPrefs);
        stores.add(EditorsUI.getPreferenceStore());
        return new ChainedPreferenceStore(stores.toArray(new IPreferenceStore[stores.size()]));
    }

    @Override
    protected void initializeEditor() {
        // already initialize in this moment
    }

    @Override
    protected void setPreferenceStore(IPreferenceStore store) {
        super.setPreferenceStore(store);
        if (getSourceViewerConfiguration() == null) {
            setSourceViewerConfiguration(new SQLEditorSourceViewerConfiguration(
                    getSharedColors(), store, this));
        }
    }

    @Override
    protected void configureSourceViewerDecorationSupport(SourceViewerDecorationSupport support) {
        char[] brackets = {'(', ')', '[', ']'};
        ICharacterPairMatcher matcher = new DefaultCharacterPairMatcher(brackets,
                SQLEditorCommonDocumentProvider.SQL_PARTITIONING);
        support.setCharacterPairMatcher(matcher);
        support.setMatchingCharacterPainterPreferenceKeys(SQL_EDITOR_PREF.MATCHING_BRACKETS,
                SQL_EDITOR_PREF.MATCHING_BRACKETS_COLOR,
                SQL_EDITOR_PREF.HIGHLIGHT_BRACKET_AT_CARET_LOCATION,
                SQL_EDITOR_PREF.ENCLOSING_BRACKETS);

        super.configureSourceViewerDecorationSupport(support);
    }

    private void checkBuilder() throws CoreException {
        IResource resource = ResourceUtil.getResource(getEditorInput());
        IProject proj = resource == null ? null : resource.getProject();
        if (proj != null && proj.hasNature(NATURE.ID) && !AddBuilder.hasBuilder(proj)) {
            MessageBox mb = new MessageBox(getEditorSite().getShell(), SWT.ICON_WARNING | SWT.YES | SWT.NO);
            mb.setText(Messages.SqlEditor_absent_builder_title);
            mb.setMessage(Messages.SqlEditor_absent_builder_message);
            if (mb.open() == SWT.YES) {
                AddBuilder.addBuilder(proj);
            }
        }
    }

    private PgDbParser initParser() throws InterruptedException, IOException, CoreException {
        IEditorInput in = getEditorInput();

        IResource res = ResourceUtil.getResource(in);
        if (res != null && UIProjectLoader.isInProject(res)) {
            return PgDbParser.getParser(res.getProject());
        }

        PgDbParser parser = new PgDbParser();
        if (refreshParser(parser, res, null)) {
            return parser;
        }

        throw new PartInitException("Unknown editor input: " + in); //$NON-NLS-1$
    }

    /**
     * Use only for non-project parsers
     * @param {@link IFileEditorInput} {@link IResource} or null
     * @return true if refresh was triggered successfully
     */
    private boolean refreshParser(PgDbParser parser, IResource res, IProgressMonitor monitor)
            throws InterruptedException, IOException, CoreException {
        if (res instanceof IFile) {
            IFile file = (IFile) res;
            IProject proj = file.getProject();
            IEclipsePreferences prefs = PgDbProject.getPrefs(proj);

            if (proj.hasNature(NATURE.MS) || (prefs != null
                    && prefs.getBoolean(PROJ_PREF.DISABLE_PARSER_IN_EXTERNAL_FILES, false))) {
                return true;
            } else if (proj.hasNature(NATURE.ID)) {
                parser.getObjFromProjFile(file, monitor);
                return true;
            }
        }

        IEditorInput in = getEditorInput();
        if (in instanceof IURIEditorInput) {
            IURIEditorInput uri = (IURIEditorInput) in;
            Path externalTmpFile = Paths.get(uri.getURI());
            isMsSqlExternalFile = externalTmpFile.getParent()
                    .equals(Paths.get(System.getProperty("java.io.tmpdir"), TEMP_DIR_PATH.MS)); //$NON-NLS-1$
            IDocument document = getDocumentProvider().getDocument(getEditorInput());
            InputStream stream = new ByteArrayInputStream(document.get().getBytes(StandardCharsets.UTF_8));
            parser.fillRefsFromInputStream(stream, externalTmpFile.toString(),
                    isMsSqlExternalFile, monitor);
            return true;
        }
        return false;
    }

    PgDbParser getParser() {
        return parser;
    }

    @Override
    public void dispose() {
        if (partListener != null) {
            getSite().getPage().removePartListener(partListener);
        }
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
        if (parser != null) {
            parser.removeListener(parserListener);
        }
        if (errorTitleImage != null) {
            errorTitleImage.dispose();
        }
        super.dispose();
    }

    private String getReplacedCmd(String cmd, DbInfo externalDbInfo) {
        String s = cmd;
        if (externalDbInfo != null) {
            if (externalDbInfo.getDbHost() != null) {
                s = s.replace(DB_HOST_PLACEHOLDER, externalDbInfo.getDbHost());
            }
            if (externalDbInfo.getDbName() != null) {
                s = s.replace(DB_NAME_PLACEHOLDER, externalDbInfo.getDbName());
            }
            if (externalDbInfo.getDbUser() != null) {
                s = s.replace(DB_USER_PLACEHOLDER, externalDbInfo.getDbUser());
            }
            if (externalDbInfo.getDbPass() != null) {
                s = s.replace(DB_PASS_PLACEHOLDER, externalDbInfo.getDbPass());
            }
            int port = externalDbInfo.getDbPort();
            if (port == 0) {
                port = JDBC_CONSTS.JDBC_DEFAULT_PORT;
            }
            s = s.replace(DB_PORT_PLACEHOLDER, "" + port); //$NON-NLS-1$
        }
        return s;
    }

    private void afterScriptFinished(final String scriptOutput) {
        UiSync.exec(parentComposite, () -> {
            if (!parentComposite.isDisposed()) {
                parentComposite.setCursor(null);

                if (mainPrefs.getBoolean(DB_UPDATE_PREF.SHOW_SCRIPT_OUTPUT_SEPARATELY)) {
                    new ScriptRunResultDialog(parentComposite.getShell(), scriptOutput).open();
                }
            }
        });
    }

    public String getEditorText() {
        return getSourceViewer().getTextWidget().getText();
    }

    public void cancelDdl() {
        scriptThreadJobWrapper.cancel();
    }

    public void updateDdl() {
        DbInfo dbInfo = currentDB;
        if (dbInfo == null){
            ExceptionNotifier.notifyDefault(Messages.sqlScriptDialog_script_select_storage, null);
            return;
        }

        final String textRetrieved;
        Point point = getSourceViewer().getSelectedRange();
        IDocument document = getSourceViewer().getDocument();
        if (point.y == 0) {
            textRetrieved = document.get();
        } else {
            try {
                textRetrieved = document.get(point.x, point.y);
            } catch (BadLocationException ble){
                Log.log(Log.LOG_WARNING, ble.getMessage());
                ExceptionNotifier.notifyDefault(Messages.SqlEditor_selected_text_error, ble);
                return;
            }
        }

        scriptThreadJobWrapper = new ScriptThreadJobWrapper(dbInfo, textRetrieved);
        scriptThreadJobWrapper.setUser(true);
        scriptThreadJobWrapper.schedule();

        saveLastDb(dbInfo);
        parentComposite.setCursor(parentComposite.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
    }

    private class ScriptThreadJobWrapper extends SingletonEditorJob {

        private final DbInfo dbInfo;
        private final String script;

        public ScriptThreadJobWrapper(DbInfo dbInfo, String script) {
            super(Messages.SqlEditor_update_ddl + getEditorInput().getName(),
                    SQLEditor.this, UpdateDdlJobTester.EVAL_PROP);
            this.dbInfo = dbInfo;
            this.script = script;
        }

        @Override
        protected IStatus run(IProgressMonitor monitor) {
            SubMonitor.convert(monitor).setTaskName(Messages.SqlEditor_update_ddl);
            return mainPrefs.getBoolean(DB_UPDATE_PREF.COMMAND_LINE_DDL_UPDATE) ?
                    runExternal(monitor) : runInternal(monitor);
        }

        private IStatus runInternal(IProgressMonitor monitor) {
            String output = Messages.sqlScriptDialog_script_has_not_been_run_yet;

            Log.log(Log.LOG_INFO, "Running DDL update using JDBC"); //$NON-NLS-1$

            JdbcConnector connector;
            if (dbInfo.isMsSql()) {
                connector = new JdbcMsConnector(
                        dbInfo.getDbHost(), dbInfo.getDbPort(), dbInfo.getDbUser(),
                        dbInfo.getDbPass(), dbInfo.getDbName(), dbInfo.getProperties(),
                        dbInfo.isReadOnly(), dbInfo.isWinAuth());
            } else {
                connector = new JdbcConnector(
                        dbInfo.getDbHost(), dbInfo.getDbPort(), dbInfo.getDbUser(),
                        dbInfo.getDbPass(), dbInfo.getDbName(), dbInfo.getProperties(),
                        dbInfo.isReadOnly(), ApgdiffConsts.UTC);
            }

            try {
                ScriptParser parser = new ScriptParser(getEditorInput().getName());
                List<List<String>> batches = parser.parse(script, dbInfo.isMsSql());
                String error = parser.getErrorMessage();
                if (dbInfo.isMsSql() && error != null) {
                    output = error;
                    return new Status(IStatus.WARNING, PLUGIN_ID.THIS,
                            Messages.sqlScriptDialog_exception_during_script_execution);
                }

                new JdbcRunner(monitor).runBatches(connector, batches);
                output = Messages.SqlEditor_jdbc_success;
                ProjectEditorDiffer.notifyDbChanged(dbInfo);
                return Status.OK_STATUS;
            } catch (InterruptedException ex) {
                output = ex.getLocalizedMessage();
                return Status.CANCEL_STATUS;
            } catch (SQLException | IOException e) {
                output = e.getLocalizedMessage();
                return new Status(IStatus.WARNING, PLUGIN_ID.THIS,
                        Messages.sqlScriptDialog_exception_during_script_execution, e);
            } finally {
                afterScriptFinished(output);
            }
        }

        private IStatus runExternal(IProgressMonitor monitor) {
            Log.log(Log.LOG_INFO, "Running DDL update using external command"); //$NON-NLS-1$

            Thread scriptThread = new Thread(new RunScriptExternal(script, new ArrayList<>(Arrays.asList(
                    getReplacedCmd(mainPrefs.getString(DB_UPDATE_PREF.MIGRATION_COMMAND), dbInfo).split(" "))))); //$NON-NLS-1$
            scriptThread.setUncaughtExceptionHandler((t, e) ->  ExceptionNotifier.notifyDefault(
                    Messages.sqlScriptDialog_exception_during_script_execution, e));
            scriptThread.start();

            try {
                while (scriptThread.isAlive()) {
                    Thread.sleep(20);
                    if (monitor.isCanceled()) {
                        ConsoleFactory.write(Messages.sqlScriptDialog_script_execution_interrupted);
                        Log.log(Log.LOG_INFO, "Script execution interrupted by user"); //$NON-NLS-1$

                        scriptThread.interrupt();
                        return Status.CANCEL_STATUS;
                    }
                }
                return Status.OK_STATUS;
            } catch (InterruptedException ex) {
                scriptThread.interrupt();
                Thread.currentThread().interrupt();
                return Status.CANCEL_STATUS;
            } finally {
                monitor.done();
            }
        }
    }

    @Override
    public void resourceChanged(IResourceChangeEvent event) {
        IResource file = ResourceUtil.getResource(getEditorInput());
        IResourceDelta delta = event.getDelta();
        if (delta != null && file != null) {
            IResourceDelta child = delta.findMember(file.getFullPath());
            if (child != null && (child.getFlags() & IResourceDelta.MARKERS) != 0) {
                UiSync.exec(parentComposite, () -> {
                    if (!parentComposite.isDisposed()) {
                        firePropertyChange(IWorkbenchPart.PROP_TITLE);
                    }
                });
            }
        }
    }

    @Override
    public Image getTitleImage() {
        Image image = super.getTitleImage();
        try {
            IEditorInput input = getEditorInput();
            IResource file = ResourceUtil.getResource(input);
            if (input.exists() && file != null
                    && file.findMarkers(MARKER.ERROR, false, IResource.DEPTH_ZERO).length > 0) {
                if (errorTitleImage == null) {
                    errorTitleImage = new DecorationOverlayIcon(image,
                            PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(
                                    ISharedImages.IMG_DEC_FIELD_ERROR), IDecoration.BOTTOM_LEFT)
                            .createImage();
                }
                return errorTitleImage;
            }
        } catch (CoreException e) {
            Log.log(e);
        }
        return image;
    }

    private class RunScriptExternal implements Runnable {
        private final String textRetrieved;
        private final List<String> command;

        RunScriptExternal(String textRetrieved, List<String> command) {
            this.textRetrieved = textRetrieved;
            this.command = command;
        }

        @Override
        public void run() {
            final StdStreamRedirector sr = new StdStreamRedirector();
            try (TempFile tempFile = new TempFile("tmp_migration_", ".sql")) { //$NON-NLS-1$ //$NON-NLS-2$
                File outFile = tempFile.get().toFile();
                try (PrintWriter writer = new PrintWriter(outFile, ApgdiffConsts.UTF_8)) {
                    writer.write(textRetrieved);
                }

                String filepath = outFile.getAbsolutePath();
                ListIterator<String> it = command.listIterator();
                while (it.hasNext()) {
                    it.set(it.next().replace(SCRIPT_PLACEHOLDER, filepath));
                }

                ProcessBuilder pb = new ProcessBuilder(command);
                sr.launchAndRedirect(pb);
            } catch (IOException ex) {
                throw new IllegalStateException(ex.getLocalizedMessage(), ex);
            } finally {
                // request UI change: button label changed
                afterScriptFinished(sr.getStorage());
            }
        }
    }

    private static class ScriptRunResultDialog extends TrayDialog {
        private final String text;

        ScriptRunResultDialog(Shell shell, String text) {
            super(shell);
            this.text = text;
            setShellStyle(getShellStyle() | SWT.RESIZE);
        }

        @Override
        protected void configureShell(Shell newShell) {
            super.configureShell(newShell);
            newShell.setText(Messages.sqlScriptDialog_script_output);
        }

        @Override
        protected Control createDialogArea(Composite parent) {
            Composite comp = (Composite) super.createDialogArea(parent);
            Text filed = new Text(comp, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL
                    | SWT.READ_ONLY | SWT.MULTI);
            filed.setText(text);
            filed.setBackground(getShell().getDisplay().getSystemColor(
                    SWT.COLOR_LIST_BACKGROUND));
            filed.setFont(JFaceResources.getTextFont());
            PixelConverter pc = new PixelConverter(filed);
            GridData gd = new GridData(GridData.FILL_BOTH);
            gd.widthHint = pc.convertWidthInCharsToPixels(80);
            gd.heightHint = pc.convertHeightInCharsToPixels(30);
            filed.setLayoutData(gd);
            return comp;
        }

        @Override
        protected void createButtonsForButtonBar(Composite parent) {
            createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
        }
    }

    private class SqlEditorPartListener extends IPartAdapter2 {

        @Override
        public void partClosed(IWorkbenchPartReference partRef) {
            if (partRef.getPart(false) == SQLEditor.this && !PlatformUI.getWorkbench().isClosing()
                    && getEditorInput() instanceof IFileEditorInput) {
                IFile f = ((IFileEditorInput) getEditorInput()).getFile();
                if (PROJ_PATH.MIGRATION_DIR.equals(f.getProjectRelativePath().segment(0))) {
                    askDeleteScript(f);
                }
            }
        }
    }

    public void askDeleteScript(IFile f) {
        String mode = mainPrefs.getString(DB_UPDATE_PREF.DELETE_SCRIPT_AFTER_CLOSE);
        // if select "YES" with toggle
        if (mode.equals(MessageDialogWithToggle.ALWAYS)){
            deleteFile(f);
            // if not select "NO" with toggle, show choice message dialog
        } else if (!mode.equals(MessageDialogWithToggle.NEVER)){
            MessageDialogWithToggle dialog = MessageDialogWithToggle.openYesNoQuestion(getSite().getShell(),
                    Messages.SqlEditor_script_delete_dialog_title, MessageFormat.format(
                            Messages.SqlEditor_script_delete_dialog_message, f.getName()),
                    Messages.remember_choice_toggle, false, mainPrefs, DB_UPDATE_PREF.DELETE_SCRIPT_AFTER_CLOSE);
            if(dialog.getReturnCode() == IDialogConstants.YES_ID){
                deleteFile(f);
            }
        }
    }

    private void deleteFile(IFile f) {
        try {
            Log.log(Log.LOG_INFO, "Deleting file " + f.getName()); //$NON-NLS-1$
            f.delete(true, null);
        } catch (CoreException ex) {
            Log.log(ex);
        }
    }
}