package com.zysj.standby;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SongImages {
    private SongImages() {
    }

    public static List<File> find(Context context) {
        File pictures = new File(Environment.getExternalStorageDirectory(), "Pictures");
        File[] roots = new File[] {
                new File(pictures, "Song"),
                new File("/sdcard/Pictures/Song"),
                context.getFilesDir()
        };
        List<File> files = new ArrayList<File>();
        Set<String> seenRoots = new HashSet<String>();
        for (File root : roots) {
            File canonical = canonicalFile(root);
            if (canonical != null && seenRoots.add(canonical.getAbsolutePath())) {
                collect(canonical, files);
            }
        }
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getName().compareTo(right.getName());
            }
        });
        return files;
    }

    private static File canonicalFile(File file) {
        if (file == null) {
            return null;
        }
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }

    private static void collect(File dir, List<File> out) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collect(file, out);
            } else if (isImage(file.getName())) {
                out.add(file);
            }
        }
    }

    private static boolean isImage(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp");
    }
}
