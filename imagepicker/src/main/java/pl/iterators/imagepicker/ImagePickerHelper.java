package pl.iterators.imagepicker;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by michalkisiel on 27.01.2016.
 */
public class ImagePickerHelper {

    private final static String TAG = "ImagePickerHelper";

    public interface Transformer {
        Bitmap transform(Bitmap bitmap);
    }

    public static File saveImage(Context context, Uri uri, String fileName) {
        String path = getRealPathFromURI(context, uri);
        if(path == null) {
            ContentResolver cR = context.getContentResolver();
            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(cR.getType(uri));
            path = saveImageCache(context, fileName + "." + extension, uri);
        }

        if(path != null) {
            return new File(path);
        }
        return null;
    }

    public static boolean normalize(File input, File output, int maxSize) {
        if(maxSize == 0)
            throw new RuntimeException("maxSize cannot be 0!");

        Bitmap b = loadBitmap(input, maxSize);
        b = rotate(input, b);
        boolean saved = saveImage(output, b);
        b.recycle();
        return saved;
    }

    public static boolean normalizeAndTransform(File input, File output, int maxSize, Transformer transformer) {
        if(maxSize == 0)
            throw new RuntimeException("maxSize cannot be 0!");

        Bitmap b = loadBitmap(input, maxSize);
        b = rotate(input, b);
        b = transformer.transform(b);
        boolean saved = saveImage(output, b);
        b.recycle();
        return saved;
    }

    private static Bitmap rotate(File input, Bitmap bitmap) {
        int rotation = getExifRotation(input);
        if(rotation > 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                    bitmap.getHeight(), matrix, true);
            if(rotatedBitmap != bitmap) {
                bitmap.recycle();
                bitmap = rotatedBitmap;
            }

        }
        return bitmap;
    }

    private static Bitmap loadBitmap(File input, int maxSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(input.getAbsolutePath(), options);
        int smallerSize = Math.min(options.outWidth, options.outHeight);
        int sampleSize = 1;
        while(smallerSize > maxSize) {//TODO power of 2
            ++sampleSize;
            smallerSize /= 2;
        }

        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;
        return BitmapFactory.decodeFile(input.getAbsolutePath(), options);
    }

    private static boolean saveImage(File output, Bitmap bitmap) {
        output.delete();
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(output);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static int getExifRotation(File f) {
        int rotate = 0;
        try {
            ExifInterface exif = new ExifInterface(f.getAbsolutePath());

            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rotate;
    }
    public static String getRealPathFromURI(Context context, Uri contentURI) {
        String result = null;
        Cursor cursor = context.getContentResolver().query(contentURI, null, null, null, null);
        if (cursor != null) {
            if(cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
                if(idx != -1)
                    result = cursor.getString(idx);
            }
            cursor.close();
        }
        return result;
    }

    public static String saveImageCache(Context context, String fileName, final Uri uri) {
        String path = context.getApplicationInfo().dataDir + File.separator + fileName;
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            ParcelFileDescriptor parcelFileDescriptor = context.getContentResolver().openFileDescriptor(uri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            inputStream = new FileInputStream(fileDescriptor);
            outputStream = new FileOutputStream(new File(path));
            int read = 0;
            byte[] bytes = new byte[1024];
            while ((read = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
            }
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File write failed: " + e.toString());
        } catch (IOException e) {
            Log.d(TAG, "File write failed: " + e.toString());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (outputStream != null) {
                try {
                    // outputStream.flush();
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
        return path;
    }

    public static void saveImage(Bitmap bitmap, String filename) {
        FileOutputStream out = null;
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), filename);
        try {
            out = new FileOutputStream(file.getPath());
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
            // PNG is a lossless format, the compression factor (100) is ignored
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void copyImageFromUri(Context context, final Uri uri, File output) {
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            ParcelFileDescriptor parcelFileDescriptor = context.getContentResolver().openFileDescriptor(uri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            inputStream = new FileInputStream(fileDescriptor);
            outputStream = new FileOutputStream(output);
            int read = 0;
            byte[] bytes = new byte[1024];
            while ((read = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
            }
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File write failed: " + e.toString());
        } catch (IOException e) {
            Log.d(TAG, "File write failed: " + e.toString());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (outputStream != null) {
                try {
                    // outputStream.flush();
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    public static void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

}
