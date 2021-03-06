package ru.taximaxim.codekeeper.ui.dialogs;

import java.util.List;
import java.util.Set;

import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import ru.taximaxim.codekeeper.apgdiff.model.difftree.TreeElement;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.TreeFlattener;
import ru.taximaxim.codekeeper.ui.UIConsts.PREF;
import ru.taximaxim.codekeeper.ui.differ.DbSource;
import ru.taximaxim.codekeeper.ui.differ.DiffTableViewer;
import ru.taximaxim.codekeeper.ui.localizations.Messages;

public class CommitDialog extends TrayDialog {

    private final IPreferenceStore prefs;
    private final boolean egitCommitAvailable;

    private final DbSource dbProject;
    private final DbSource dbRemote;
    private final TreeElement diffTree;
    private final Set<TreeElement> depcyElementsSet;
    private DiffTableViewer dtvBottom;
    private Button btnAutocommit;
    private Label warningLbl;

    public CommitDialog(Shell parentShell, Set<TreeElement> depcyElementsSet,
            DbSource dbProject, DbSource dbRemote, TreeElement diffTree,
            IPreferenceStore mainPrefs, boolean egitCommitAvailable) {
        super(parentShell);
        this.depcyElementsSet = depcyElementsSet;
        this.dbProject = dbProject;
        this.dbRemote = dbRemote;
        this.diffTree = diffTree;
        this.prefs = mainPrefs;
        this.egitCommitAvailable = egitCommitAvailable;

        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(Messages.commitPartDescr_commit_confirmation);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);

        Composite container = new Composite(area, SWT.NONE);
        GridLayout gl = new GridLayout();
        gl.marginHeight = gl.marginWidth = 0;
        container.setLayout(gl);
        container.setLayoutData(new GridData(GridData.FILL_BOTH));

        new Label(container, SWT.NONE).setText(
                Messages.commitPartDescr_the_following_changes_be_included_in_commit);

        Group gTop = new Group(container, SWT.NONE);
        gTop.setLayout(new GridLayout());
        GridData gd = new GridData(GridData.FILL_BOTH);
        gTop.setLayoutData(gd);
        gTop.setText(Messages.commitDialog_user_selected_elements);

        DiffTableViewer dtvTop = new DiffTableViewer(gTop, true, null, null);
        gd = new GridData(GridData.FILL_BOTH);
        gd.heightHint = 300;
        gd.widthHint = 1000;
        dtvTop.setLayoutData(gd);

        dtvTop.setAutoExpand(true);
        List<TreeElement> result = new TreeFlattener().onlySelected().flatten(diffTree);
        dtvTop.setInputCollection(result, dbProject, dbRemote);

        if (depcyElementsSet != null){
            Group gBottom = new Group(container, SWT.NONE);
            gBottom.setLayout(new GridLayout());

            gd = new GridData(GridData.FILL_BOTH);
            gBottom.setLayoutData(gd);
            gBottom.setText(Messages.commitDialog_depcy_elements);

            dtvBottom = new DiffTableViewer(gBottom, false, null, null);
            gd = new GridData(GridData.FILL_BOTH);
            gd.heightHint = 300;
            gd.widthHint = 1000;
            dtvBottom.setLayoutData(gd);

            // выбрать все зависимые элементы для наката
            for (TreeElement el : depcyElementsSet) {
                el.setSelected(true);
            }
            dtvTop.setAutoExpand(true);
            dtvBottom.setInputCollection(depcyElementsSet, dbProject, dbRemote);

            dtvBottom.addCheckStateListener(new ValidationCheckStateListener());
            warningLbl = new Label(gBottom, SWT.NONE);
            warningLbl.setText(Messages.CommitDialog_unchecked_objects_can_occur_unexpected_errors);
            warningLbl.setForeground(getShell().getDisplay().getSystemColor(SWT.COLOR_RED));
            warningLbl.setVisible(false);
        }

        btnAutocommit = new Button(container, SWT.CHECK);
        btnAutocommit.setText(Messages.commitPartDescr_show_commit_window);
        btnAutocommit.setSelection(prefs.getBoolean(PREF.CALL_COMMIT_COMMAND_AFTER_UPDATE));
        btnAutocommit.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                prefs.setValue(PREF.CALL_COMMIT_COMMAND_AFTER_UPDATE, btnAutocommit.getSelection());
            }
        });
        btnAutocommit.setEnabled(egitCommitAvailable);
        return area;
    }

    public DiffTableViewer getBottomTableViewer(){
        return dtvBottom;
    }

    @Override
    public int open() {
        int res = super.open();
        // Если пользователь нажал отмену - снять выделения с зависимых элементов
        if (res == CANCEL && depcyElementsSet != null) {
            for (TreeElement el : depcyElementsSet) {
                el.setSelected(false);
            }
        }
        return res;
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    /**
     * Задача этого класса - смотреть на снятие галочки с элемента и если
     * галочка снимается то проверить следующее: <br>
     * 1. Элемент удаляется: если есть выбранные родители - показать предупреждение; <br>
     * 2. Элемент создается: если есть выбранные дети - показать предупреждение
     * @author botov_av
     */
    private class ValidationCheckStateListener implements ICheckStateListener {

        @Override
        public void checkStateChanged(CheckStateChangedEvent event) {
            boolean showWarning = false;
            for (TreeElement el : depcyElementsSet) {
                if (!el.isSelected()) {
                    switch (el.getSide()) {
                    // удаляется
                    case LEFT:
                        showWarning = hasSelectedParent(el);
                        break;
                        // создается
                    case RIGHT:
                        showWarning = el.isSubTreeSelected();
                        break;
                    default:
                        break;
                    }
                    if (showWarning) {
                        break;
                    }
                }
            }
            warningLbl.setVisible(showWarning);
            getButton(OK).setEnabled(!showWarning);
        }

        private boolean hasSelectedParent(TreeElement el) {
            TreeElement parent = el.getParent();
            while (parent != null) {
                if (parent.isSelected()) {
                    return true;
                }
                parent = parent.getParent();
            }
            return false;
        }
    }
}