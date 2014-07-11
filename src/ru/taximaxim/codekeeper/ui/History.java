package ru.taximaxim.codekeeper.ui;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.LinkedList;

import javax.xml.transform.TransformerException;

import org.eclipse.core.runtime.Platform;
import org.xml.sax.SAXException;

import ru.taximaxim.codekeeper.ui.localizations.Messages;

public class History {
    
    private int maxCapacity;
    private String fileName;
    private String rootTagName;
    private String elementTagName;
    
    public History(int maxCapacity,
    String fileName,
    String rootTagName,
    String elementTagName) {
        this.maxCapacity = maxCapacity;
        this.fileName = fileName;
        this.rootTagName = rootTagName;
        this.elementTagName = elementTagName;
    }

    public LinkedList<String> getHistory() {
        LinkedList<String> comments;
        try (Reader xmlReader = new FileReader(getHistoryXmlFile())) {
            XmlStringList xml = new XmlStringList(rootTagName, elementTagName);
            comments = xml.deserialize(xmlReader);
        } catch (FileNotFoundException ex) {
            comments = null;
        } catch (IOException | SAXException ex) {
            throw new IllegalStateException(
                    Messages.history_history_file_read_error,
                    ex);
        }
        return comments;
    }

    private File getHistoryXmlFile() {
        File fileComments;
        try {
            fileComments = new File(Platform.getInstanceLocation().getURL().toURI());
        } catch(URISyntaxException ex) {
            throw new IllegalStateException(ex);
        }
        fileComments = new File(fileComments, ".metadata"); //$NON-NLS-1$
        fileComments = new File(fileComments, ".plugins"); //$NON-NLS-1$
        fileComments = new File(fileComments, UIConsts.PLUGIN_ID);
        fileComments = new File(fileComments, fileName);
        return fileComments;
    }

    public void updatePrevHistory(String scriptComment, String errMsg) {
            LinkedList<String> scripts = getHistory();
            if (scripts == null) {
                scripts = new LinkedList<>();
            }
            if (!scriptComment.isEmpty()) {
                
                scripts.remove(scriptComment);
                scripts.add(0, scriptComment);
    
                while (scripts.size() > maxCapacity) {
                    scripts.removeLast();
                }
                try (Writer xmlWriter = new FileWriter(getHistoryXmlFile())) {
                    XmlStringList xml = new XmlStringList(rootTagName,
                            elementTagName);
                    xml.serialize(scripts, false, xmlWriter);
                } catch (IOException | TransformerException ex) {
                    throw new IllegalStateException(errMsg, ex);
                }
            }
        }
        
    
}
