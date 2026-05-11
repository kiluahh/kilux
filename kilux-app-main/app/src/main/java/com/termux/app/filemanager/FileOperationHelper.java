package com.termux.app.filemanager;

import android.content.Context;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class FileOperationHelper {

    public static boolean deleteRecursive(File fileOrDir) {
        if (fileOrDir.isDirectory()) {
            File[] children = fileOrDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return fileOrDir.delete();
    }

    public static boolean rename(File file, String newName) {
        File dest = new File(file.getParent(), newName);
        return file.renameTo(dest);
    }

    public static File createFolder(File parent, String name) {
        File dir = new File(parent, name);
        if (dir.mkdir()) {
            return dir;
        }
        return null;
    }

    public static List<File> copyFiles(Context context, List<File> sources, File destDir) {
        List<File> copied = new ArrayList<>();
        for (File src : sources) {
            File target = new File(destDir, src.getName());
            if (src.isDirectory()) {
                if (target.mkdir()) {
                    copyDirectoryContents(src, target, copied);
                    copied.add(target);
                }
            } else {
                if (copyFile(src, target)) {
                    copied.add(target);
                } else {
                    Toast.makeText(context, "Copy failed: " + src.getName(), Toast.LENGTH_SHORT).show();
                }
            }
        }
        return copied;
    }

    public static List<File> moveFiles(Context context, List<File> sources, File destDir) {
        List<File> moved = new ArrayList<>();
        for (File src : sources) {
            File target = new File(destDir, src.getName());
            if (src.renameTo(target)) {
                moved.add(target);
            } else {
                // Try copy+delete as fallback
                if (src.isDirectory()) {
                    if (target.mkdir()) {
                        copyDirectoryContents(src, target, moved);
                        deleteRecursive(src);
                        moved.add(target);
                    }
                } else {
                    if (copyFile(src, target)) {
                        src.delete();
                        moved.add(target);
                    } else {
                        Toast.makeText(context, "Move failed: " + src.getName(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
        return moved;
    }

    private static void copyDirectoryContents(File srcDir, File destDir, List<File> log) {
        File[] children = srcDir.listFiles();
        if (children == null) return;
        for (File child : children) {
            File target = new File(destDir, child.getName());
            if (child.isDirectory()) {
                if (target.mkdir()) {
                    copyDirectoryContents(child, target, log);
                    log.add(target);
                }
            } else {
                if (copyFile(child, target)) {
                    log.add(target);
                }
            }
        }
    }

    private static boolean copyFile(File src, File dest) {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public static String formatDate(long millis) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(millis));
    }
}
