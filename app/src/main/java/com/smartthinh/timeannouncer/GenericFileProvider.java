package com.smartthinh.timeannouncer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

public class GenericFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.smartthinh.timeannouncer.fileprovider";

    public static Uri getUriForFile(File file) {
        return Uri.parse("content://" + AUTHORITY + "/" + file.getName());
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context context = getContext();
        if (context == null) {
            throw new FileNotFoundException("Context is null");
        }
        String fileName = uri.getLastPathSegment();
        if (fileName == null) {
            throw new FileNotFoundException("Invalid URI: " + uri);
        }

        File file = new File(context.getCacheDir(), fileName);
        if (!file.exists()) {
            file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
        }
        if (!file.exists()) {
            file = new File(context.getFilesDir(), fileName);
        }
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + fileName);
        }

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
