package ru.taximaxim.codekeeper.ui.fileutils;

import java.io.File;
import java.io.IOException;

public class Dir {

    /**
     * Deletes folder and its contents recursively. FOLLOWS SYMLINKS!
     * 
     * @param f Directory
     * @throws IOException
     */
    public static void deleteRecursive(File f) throws IOException {
        if(f.isDirectory()) {
            for(File sub : f.listFiles()) {
                deleteRecursive(sub);
            }
        }
        ReadOnlyFileRemover.remove(f.toPath());
    }
}