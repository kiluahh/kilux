package com.termux.app.filemanager;

import java.io.File;

public class FileItem {
    public final File file;
    public final String name;
    public final boolean isDirectory;
    public final long size;
    public final long lastModified;

    public FileItem(File file) {
        this.file = file;
        this.name = file.getName();
        this.isDirectory = file.isDirectory();
        this.size = file.isDirectory() ? 0 : file.length();
        this.lastModified = file.lastModified();
    }

    public int getChildCount() {
        if (!isDirectory) return 0;
        String[] list = file.list();
        return list != null ? list.length : 0;
    }
}
