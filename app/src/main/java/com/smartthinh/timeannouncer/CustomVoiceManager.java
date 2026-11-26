package com.smartthinh.timeannouncer;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CustomVoiceManager {

    private static final String TAG = "CustomVoiceManager";
    public static final String[] SUPPORTED_AUDIO_EXTENSIONS = {".mp3", ".wav", ".ogg", ".m4a", ".aac", ".flac"};

    public static class VoicePack {
        public String id;
        public String name;
        public String language;
        public String author;
        public File directory;

        public VoicePack(String id, String name, String language, String author, File directory) {
            this.id = id;
            this.name = name;
            this.language = language;
            this.author = author;
            this.directory = directory;
        }
    }

    public static File getCustomVoicesDir(Context context) {
        File dir = new File(context.getFilesDir(), "custom_voices");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static List<VoicePack> getInstalledPacks(Context context) {
        List<VoicePack> list = new ArrayList<>();
        File rootDir = getCustomVoicesDir(context);
        File[] subDirs = rootDir.listFiles(File::isDirectory);
        if (subDirs == null) {
            return list;
        }

        for (File dir : subDirs) {
            VoicePack pack = loadPackFromDirectory(dir);
            if (pack != null) {
                list.add(pack);
            }
        }

        Collections.sort(list, (o1, o2) -> o1.name.compareToIgnoreCase(o2.name));
        return list;
    }

    public static VoicePack getPackById(Context context, String packId) {
        if (packId == null || packId.isEmpty()) {
            return null;
        }
        File dir = new File(getCustomVoicesDir(context), packId);
        if (dir.exists() && dir.isDirectory()) {
            return loadPackFromDirectory(dir);
        }
        return null;
    }

    private static VoicePack loadPackFromDirectory(File dir) {
        if (!isValidVoicePackDirectory(dir)) {
            return null;
        }

        String name = dir.getName();
        String language = "vi";
        String author = "";

        // Check for pack.json or info.json or any .json file
        File[] jsonFiles = dir.listFiles((d, fName) -> fName.toLowerCase().endsWith(".json"));
        if (jsonFiles != null && jsonFiles.length > 0) {
            File jsonFile = jsonFiles[0];
            for (File jf : jsonFiles) {
                if (jf.getName().equalsIgnoreCase("pack.json")) {
                    jsonFile = jf;
                    break;
                }
            }
            try (FileInputStream fis = new FileInputStream(jsonFile);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                JSONObject obj = new JSONObject(sb.toString());
                if (obj.has("name") && !obj.optString("name").trim().isEmpty()) {
                    name = obj.optString("name").trim();
                }
                if (obj.has("language")) {
                    language = obj.optString("language");
                }
                if (obj.has("author")) {
                    author = obj.optString("author");
                }
            } catch (Exception e) {
                Log.w(TAG, "Error reading json for pack " + dir.getName(), e);
            }
        }

        return new VoicePack(dir.getName(), name, language, author, dir);
    }

    public static boolean isValidVoicePackDirectory(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return false;
        }

        boolean hasHour = getSubDir(dir, "hour") != null || getSubDir(dir, "hours") != null;
        boolean hasHourly = getSubDir(dir, "hourly") != null || getSubDir(dir, "exact_hours") != null;
        boolean hasMinutes = getSubDir(dir, "minutes") != null || getSubDir(dir, "minute") != null;

        return hasHour && (hasHourly || hasHour) && hasMinutes;
    }

    private static File getSubDir(File parent, String name) {
        File[] files = parent.listFiles(File::isDirectory);
        if (files == null) return null;
        for (File f : files) {
            if (f.getName().equalsIgnoreCase(name)) {
                return f;
            }
        }
        return null;
    }

    public static VoicePack importZip(Context context, Uri zipUri, String originalFileName) throws Exception {
        File tempDir = new File(context.getCacheDir(), "import_voice_" + System.currentTimeMillis());
        if (tempDir.exists()) {
            deleteRecursively(tempDir);
        }
        tempDir.mkdirs();

        try {
            // Unzip safely
            try (InputStream is = context.getContentResolver().openInputStream(zipUri);
                 ZipInputStream zis = new ZipInputStream(is)) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    // Prevent Zip Slip
                    File newFile = new File(tempDir, entryName);
                    String canonicalDest = tempDir.getCanonicalPath();
                    String canonicalFile = newFile.getCanonicalPath();
                    if (!canonicalFile.startsWith(canonicalDest + File.separator) && !canonicalFile.equals(canonicalDest)) {
                        throw new SecurityException("Zip entry is outside target dir: " + entryName);
                    }

                    // Ignore macOS hidden folders
                    if (entryName.startsWith("__MACOSX") || entryName.contains("/__MACOSX") || entryName.endsWith(".DS_Store")) {
                        zis.closeEntry();
                        continue;
                    }

                    if (entry.isDirectory()) {
                        newFile.mkdirs();
                    } else {
                        File parent = newFile.getParentFile();
                        if (parent != null && !parent.exists()) {
                            parent.mkdirs();
                        }
                        try (FileOutputStream fos = new FileOutputStream(newFile)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                    zis.closeEntry();
                }
            }

            // Find effective root directory (in case zip has a single root folder)
            File effectiveRoot = findEffectiveVoiceRoot(tempDir);
            if (effectiveRoot == null || !isValidVoicePackDirectory(effectiveRoot)) {
                throw new IllegalArgumentException("Invalid voice pack structure: missing hour, hourly, or minutes folder.");
            }

            // Find JSON to extract name
            String packName = null;
            String packId = null;
            String packLanguage = "vi";
            String packAuthor = "";

            File[] jsonFiles = effectiveRoot.listFiles((d, fName) -> fName.toLowerCase().endsWith(".json"));
            if (jsonFiles != null && jsonFiles.length > 0) {
                File jsonFile = jsonFiles[0];
                for (File jf : jsonFiles) {
                    if (jf.getName().equalsIgnoreCase("pack.json")) {
                        jsonFile = jf;
                        break;
                    }
                }
                try (FileInputStream fis = new FileInputStream(jsonFile);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    JSONObject obj = new JSONObject(sb.toString());
                    if (obj.has("name") && !obj.optString("name").trim().isEmpty()) {
                        packName = obj.optString("name").trim();
                    }
                    if (obj.has("id") && !obj.optString("id").trim().isEmpty()) {
                        packId = obj.optString("id").trim();
                    }
                    if (obj.has("language")) {
                        packLanguage = obj.optString("language");
                    }
                    if (obj.has("author")) {
                        packAuthor = obj.optString("author");
                    }
                } catch (Exception ignored) {}
            }

            if (packName == null || packName.isEmpty()) {
                if (originalFileName != null && !originalFileName.trim().isEmpty()) {
                    String baseName = originalFileName.trim();
                    if (baseName.toLowerCase().endsWith(".zip")) {
                        baseName = baseName.substring(0, baseName.length() - 4).trim();
                    }
                    if (!baseName.isEmpty()) {
                        packName = baseName;
                    }
                }
                if (packName == null || packName.isEmpty()) {
                    packName = "Custom Voice " + System.currentTimeMillis();
                }
            }

            if (packId == null || packId.isEmpty()) {
                String sanitized = packName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
                if (sanitized.isEmpty() || sanitized.replace("_", "").isEmpty()) {
                    packId = "voice_" + System.currentTimeMillis();
                } else {
                    packId = sanitized;
                }
            } else {
                packId = packId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            }

            // Destination directory
            File destDir = new File(getCustomVoicesDir(context), packId);
            if (destDir.exists()) {
                deleteRecursively(destDir);
            }
            destDir.mkdirs();

            // Copy contents from effectiveRoot to destDir
            copyDirectory(effectiveRoot, destDir);

            // If no json file exists in the pack, generate a pack.json so the name (from ZIP) is permanently preserved
            File[] existingJson = destDir.listFiles((d, fName) -> fName.toLowerCase().endsWith(".json"));
            if (existingJson == null || existingJson.length == 0) {
                File packJson = new File(destDir, "pack.json");
                try (FileOutputStream fos = new FileOutputStream(packJson);
                     OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                    JSONObject obj = new JSONObject();
                    obj.put("name", packName);
                    obj.put("id", packId);
                    obj.put("language", packLanguage != null ? packLanguage : "vi");
                    obj.put("author", packAuthor != null ? packAuthor : "");
                    writer.write(obj.toString(2));
                } catch (Exception e) {
                    Log.w(TAG, "Failed to write pack.json fallback", e);
                }
            }

            // Return new pack
            return new VoicePack(packId, packName, packLanguage, packAuthor, destDir);

        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static File findEffectiveVoiceRoot(File dir) {
        if (isValidVoicePackDirectory(dir)) {
            return dir;
        }
        File[] subDirs = dir.listFiles(File::isDirectory);
        if (subDirs != null) {
            for (File sub : subDirs) {
                if (isValidVoicePackDirectory(sub)) {
                    return sub;
                }
            }
        }
        return null;
    }

    public static boolean deletePack(Context context, String packId) {
        if (packId == null || packId.isEmpty()) return false;
        File dir = new File(getCustomVoicesDir(context), packId);
        if (dir.exists()) {
            return deleteRecursively(dir);
        }
        return false;
    }

    public static boolean deleteRecursively(File fileOrDir) {
        if (fileOrDir == null || !fileOrDir.exists()) return true;
        if (fileOrDir.isDirectory()) {
            File[] children = fileOrDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return fileOrDir.delete();
    }

    private static void copyDirectory(File source, File target) throws Exception {
        if (source.isDirectory()) {
            if (!target.exists()) {
                target.mkdirs();
            }
            String[] children = source.list();
            if (children != null) {
                for (String child : children) {
                    copyDirectory(new File(source, child), new File(target, child));
                }
            }
        } else {
            try (InputStream in = new FileInputStream(source);
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
    }

    public static File findAudioFile(File folder, String baseName) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return null;
        }
        for (String ext : SUPPORTED_AUDIO_EXTENSIONS) {
            File f = new File(folder, baseName + ext);
            if (f.exists() && f.isFile()) {
                return f;
            }
        }
        return null;
    }

    public static File getHourlyAudioFile(File packDir, int hour) {
        File hourlyDir = getSubDir(packDir, "hourly");
        if (hourlyDir == null) {
            hourlyDir = getSubDir(packDir, "exact_hours");
        }
        if (hourlyDir != null) {
            File f = findAudioFile(hourlyDir, String.valueOf(hour));
            if (f != null) return f;
        }
        return getHourAudioFile(packDir, hour);
    }

    public static File getHourAudioFile(File packDir, int hour) {
        File hourDir = getSubDir(packDir, "hour");
        if (hourDir == null) {
            hourDir = getSubDir(packDir, "hours");
        }
        if (hourDir != null) {
            return findAudioFile(hourDir, String.valueOf(hour));
        }
        return null;
    }

    public static File getMinuteAudioFile(File packDir, int minute) {
        File minDir = getSubDir(packDir, "minutes");
        if (minDir == null) {
            minDir = getSubDir(packDir, "minute");
        }
        if (minDir != null) {
            return findAudioFile(minDir, String.valueOf(minute));
        }
        return null;
    }
}
