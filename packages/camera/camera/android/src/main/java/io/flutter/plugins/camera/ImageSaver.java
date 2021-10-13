// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.camera2.CameraMetadata;
import android.media.Image;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static java.lang.Math.max;

/** Saves a JPEG {@link Image} into the specified {@link File}. */
public class ImageSaver implements Runnable {

  /** The JPEG image */
  private final Image image;

  /** The file we save the image into. */
  private final File file;

  /** Used to report the status of the save action. */
  private final Callback callback;

  private final double aspectRatio;

  private final int lensFacing;

  /**
   * Creates an instance of the ImageSaver runnable
   *
   * @param image - The image to save
   * @param file - The file to save the image to
   * @param callback - The callback that is run on completion, or when an error is encountered.
   */
  ImageSaver(@NonNull Image image, @NonNull File file, double aspectRatio, int lensFacing, @NonNull Callback callback) {
    this.image = image;
    this.file = file;
    this.callback = callback;
    this.aspectRatio = aspectRatio;
    this.lensFacing = lensFacing;
  }

  @Override
  public void run() {
    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);

    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    final int imageWidth = bitmap.getHeight();
    final int imageHeight = bitmap.getWidth();
    int realWidth = imageWidth;
    int realHeight = (int) (realWidth / aspectRatio);
    final int changedHeightDelta = realHeight - imageHeight;
    int verticalCutOff = 0;
    int horizontalCutOff = 0;
    float zoom = 1;
    if (changedHeightDelta <= 0) {
      verticalCutOff = -changedHeightDelta / 2;
    } else {
      zoom = (float) realHeight / imageHeight;
      realWidth = (int) (imageWidth * zoom);
      final int changedWidthDelta = realWidth - imageWidth;
      horizontalCutOff = max(0, changedWidthDelta / 2);
    }

    Matrix matrix = new Matrix();
    if (lensFacing == CameraMetadata.LENS_FACING_BACK) {
      matrix.postRotate(90);
    } else {
      matrix.postRotate(-90);
      matrix.postScale(-1, 1);
    }

    bitmap = Bitmap.createBitmap(
        bitmap,
        verticalCutOff,
        horizontalCutOff,
        bitmap.getWidth() - verticalCutOff * 2,
        bitmap.getHeight() - horizontalCutOff * 2,
        matrix,
        true
    );
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
    bytes = bos.toByteArray();

    FileOutputStream output = null;
    try {
      output = FileOutputStreamFactory.create(file);
      output.write(bytes);

      callback.onComplete(file.getAbsolutePath());

    } catch (IOException e) {
      callback.onError("IOError", "Failed saving image");
    } finally {
      image.close();
      if (null != output) {
        try {
          output.close();
        } catch (IOException e) {
          callback.onError("cameraAccess", e.getMessage());
        }
      }
    }
  }

  /**
   * The interface for the callback that is passed to ImageSaver, for detecting completion or
   * failure of the image saving task.
   */
  public interface Callback {
    /**
     * Called when the image file has been saved successfully.
     *
     * @param absolutePath - The absolute path of the file that was saved.
     */
    void onComplete(String absolutePath);

    /**
     * Called when an error is encountered while saving the image file.
     *
     * @param errorCode - The error code.
     * @param errorMessage - The human readable error message.
     */
    void onError(String errorCode, String errorMessage);
  }

  /** Factory class that assists in creating a {@link FileOutputStream} instance. */
  static class FileOutputStreamFactory {
    /**
     * Creates a new instance of the {@link FileOutputStream} class.
     *
     * <p>This method is visible for testing purposes only and should never be used outside this *
     * class.
     *
     * @param file - The file to create the output stream for
     * @return new instance of the {@link FileOutputStream} class.
     * @throws FileNotFoundException when the supplied file could not be found.
     */
    @VisibleForTesting
    public static FileOutputStream create(File file) throws FileNotFoundException {
      return new FileOutputStream(file);
    }
  }
}
