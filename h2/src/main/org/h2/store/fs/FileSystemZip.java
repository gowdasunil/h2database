/*
 * Copyright 2004-2011 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store.fs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.h2.message.DbException;
import org.h2.util.New;

/**
 * This is a read-only file system that allows
 * to access databases stored in a .zip or .jar file.
 */
public class FileSystemZip extends FileSystem {

    private static final String PREFIX = "zip:";

    static {
        FileSystem.register(new FileSystemZip());
    }

    public boolean canWrite(String fileName) {
        return false;
    }

    public void createDirectory(String directoryName) {
        // ignore
    }

    public boolean createFile(String fileName) {
        throw DbException.getUnsupportedException("write");
    }

    public String createTempFile(String prefix, String suffix, boolean deleteOnExit, boolean inTempDir) throws IOException {
        if (!inTempDir) {
            throw new IOException("File system is read-only");
        }
        return FileSystemDisk.getInstance().createTempFile(prefix, suffix, deleteOnExit, true);
    }

    public void delete(String fileName) {
        throw DbException.getUnsupportedException("write");
    }

    public boolean exists(String fileName) {
        try {
            String entryName = getEntryName(fileName);
            if (entryName.length() == 0) {
                return true;
            }
            ZipFile file = openZipFile(fileName);
            return file.getEntry(entryName) != null;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean fileStartsWith(String fileName, String prefix) {
        return fileName.startsWith(prefix);
    }

    public String getName(String name) {
        name = getEntryName(name);
        if (name.endsWith("/")) {
            name = name.substring(0, name.length() - 1);
        }
        int idx = name.lastIndexOf('/');
        if (idx >= 0) {
            name = name.substring(idx + 1);
        }
        return name;
    }

    public long lastModified(String fileName) {
        return 0;
    }

    public String getParent(String fileName) {
        int idx = fileName.lastIndexOf('/');
        if (idx > 0) {
            fileName = fileName.substring(0, idx);
        }
        return fileName;
    }

    public boolean isAbsolute(String fileName) {
        return true;
    }

    public boolean isDirectory(String fileName) {
        try {
            String entryName = getEntryName(fileName);
            if (entryName.length() == 0) {
                return true;
            }
            ZipFile file = openZipFile(fileName);
            Enumeration<? extends ZipEntry> en = file.entries();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                String n = entry.getName();
                if (n.equals(entryName)) {
                    return entry.isDirectory();
                } else  if (n.startsWith(entryName)) {
                    if (n.length() == entryName.length() + 1) {
                        if (n.equals(entryName + "/")) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean isReadOnly(String fileName) {
        return true;
    }

    public boolean setReadOnly(String fileName) {
        return true;
    }

    public long size(String fileName) {
        try {
            ZipFile file = openZipFile(fileName);
            ZipEntry entry = file.getEntry(getEntryName(fileName));
            return entry == null ? 0 : entry.getSize();
        } catch (IOException e) {
            return 0;
        }
    }

    public String[] listFiles(String path) {
        try {
            if (path.indexOf('!') < 0) {
                path += "!";
            }
            if (!path.endsWith("/")) {
                path += "/";
            }
            ZipFile file = openZipFile(path);
            String dirName = getEntryName(path);
            String prefix = path.substring(0, path.length() - dirName.length());
            Enumeration<? extends ZipEntry> en = file.entries();
            ArrayList<String> list = New.arrayList();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                String name = entry.getName();
                if (!name.startsWith(dirName)) {
                    continue;
                }
                if (name.length() <= dirName.length()) {
                    continue;
                }
                int idx = name.indexOf('/', dirName.length());
                if (idx < 0 || idx >= name.length() - 1) {
                    list.add(prefix + name);
                }
            }
            String[] result = new String[list.size()];
            list.toArray(result);
            return result;
        } catch (IOException e) {
            throw DbException.convertIOException(e, "listFiles " + path);
        }
    }

    public String getCanonicalPath(String fileName) {
        return fileName;
    }

    public InputStream newInputStream(String fileName) throws IOException {
        FileObject file = openFileObject(fileName, "r");
        return new FileObjectInputStream(file);
    }

    public FileObject openFileObject(String fileName, String mode) throws IOException {
        ZipFile file = openZipFile(translateFileName(fileName));
        ZipEntry entry = file.getEntry(getEntryName(fileName));
        if (entry == null) {
            throw new FileNotFoundException(fileName);
        }
        return new FileObjectZip(file, entry);
    }

    public OutputStream newOutputStream(String fileName, boolean append) {
        throw DbException.getUnsupportedException("write");
    }

    public void moveTo(String oldName, String newName) {
        throw DbException.getUnsupportedException("write");
    }

    public boolean tryDelete(String fileName) {
        return false;
    }

    private static String translateFileName(String fileName) {
        if (fileName.startsWith(PREFIX)) {
            fileName = fileName.substring(PREFIX.length());
        }
        int idx = fileName.indexOf('!');
        if (idx >= 0) {
            fileName = fileName.substring(0, idx);
        }
        return FileSystemDisk.expandUserHomeDirectory(fileName);
    }

    private static String getEntryName(String fileName) {
        int idx = fileName.indexOf('!');
        if (idx <= 0) {
            fileName = "";
        } else {
            fileName = fileName.substring(idx + 1);
        }
        fileName = fileName.replace('\\', '/');
        if (fileName.startsWith("/")) {
            fileName = fileName.substring(1);
        }
        return fileName;
    }

    private static ZipFile openZipFile(String fileName) throws IOException {
        fileName = translateFileName(fileName);
        return new ZipFile(fileName);
    }

    protected boolean accepts(String fileName) {
        return fileName.startsWith(PREFIX);
    }

    public String unwrap(String fileName) {
        return fileName;
    }

}
