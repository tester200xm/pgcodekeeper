package ru.taximaxim.codekeeper.ui.sqledit;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateCompletionProcessor;
import org.eclipse.jface.text.templates.TemplateContext;
import org.eclipse.jface.text.templates.TemplateContextType;
import org.eclipse.jface.text.templates.TemplateException;
import org.eclipse.swt.graphics.Image;

import cz.startnet.utils.pgdiff.PgDiffUtils;

public class SQLEditorTemplateAssistProcessor extends TemplateCompletionProcessor {

    @Override
    protected Template[] getTemplates(String contextTypeId) {
        SQLEditorTemplateManager manager = SQLEditorTemplateManager
                .getInstance();
        return manager.getTemplateStore().getTemplates();
    }

    @Override
    protected TemplateContextType getContextType(ITextViewer viewer,
            IRegion region) {
        SQLEditorTemplateManager manager = SQLEditorTemplateManager
                .getInstance();
        return manager.getContextTypeRegistry().getContextType(
                SQLEditorTemplateContextType.CONTEXT_TYPE);
    }

    @Override
    protected Image getImage(Template template) {
        return null;
    }

    @Override
    protected String extractPrefix(ITextViewer viewer, int offset) {
        int i = offset;
        IDocument document = viewer.getDocument();
        if (i > document.getLength()) {
            return ""; //$NON-NLS-1$
        }
        try {
            while (i > 0) {
                char ch = document.getChar(i - 1);
                if (!PgDiffUtils.isValidIdChar(ch)) {
                    break;
                }
                i--;
            }
            if (i > 0) {
                int j = i;
                if (document.getChar(j - 1) == '<') {
                    i--;
                }
            }
            return document.get(i, offset - i);
        } catch (BadLocationException e) {
            return ""; //$NON-NLS-1$
        }
    }

    @Override
    public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer,
            int offset) {
        ITextSelection selection = (ITextSelection) viewer
                .getSelectionProvider().getSelection();
        // adjust offset to end of normalized selection
        if (selection.getOffset() == offset) {
            offset = selection.getOffset() + selection.getLength();
        }
        String prefix = extractPrefix(viewer, offset);
        Region region = new Region(offset - prefix.length(), prefix.length());
        TemplateContext context = createContext(viewer, region);
        if (context == null) {
            return new ICompletionProposal[0];
        }
        context.setVariable("selection", selection.getText()); // name of the selection variables {line, word_selection //$NON-NLS-1$
        Template[] templates = getTemplates(context.getContextType().getId());
        List<ICompletionProposal> matches = new ArrayList<>();
        for (Template template : templates) {
            try {
                context.getContextType().validate(template.getPattern());
            } catch (TemplateException e) {
                continue;
            }
            if (!prefix.equals("") && prefix.charAt(0) == '<') { //$NON-NLS-1$
                prefix = prefix.substring(1);
            }
            if (!prefix.equals("") //$NON-NLS-1$
                    && (template.getName().startsWith(prefix) && template
                            .matches(prefix, context.getContextType().getId()))) {
                matches.add(createProposal(template, context, (IRegion) region,
                        getRelevance(template, prefix)));
            }
        }
        return matches.toArray(new ICompletionProposal[matches.size()]);
    }

    public List<ICompletionProposal> getAllTemplates(ITextViewer viewer,
            int offset) {
        List<ICompletionProposal> result = new ArrayList<>();
        String prefix = extractPrefix(viewer, offset);
        Region region = new Region(offset - prefix.length(), prefix.length());
        TemplateContext context = createContext(viewer, region);
        Template[] templates = getTemplates(context.getContextType().getId());
        for (Template template : templates) {
            result.add(createProposal(template, context, (IRegion) region,
                    getRelevance(template, prefix)));
        }
        return result;
    }
}