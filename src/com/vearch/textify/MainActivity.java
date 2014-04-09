package com.vearch.textify;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.googlecode.tesseract.android.TessBaseAPI;

public class MainActivity extends Activity {
	public static final String PACKAGE_NAME = "com.vearch.textify";
	public static final String DATA_PATH = Environment
			.getExternalStorageDirectory().toString() + "/Textify/";
	
	// You should have the trained data file in assets folder
	// You can get them at:
	// http://code.google.com/p/tesseract-ocr/downloads/list
	public static final String lang = "eng";

	private static final String TAG = "MainActivity.java";

	protected Button _button;
	protected ImageView _image;
	protected EditText _field;
	protected String _path;
	protected boolean _taken;
	protected Button _textifyButton;
	protected Button _resetButton;

	protected static final String PHOTO_TAKEN = "photo_taken";
	
	protected AssetManager assetManager;
	protected DetectTextNative dtNative;
	
	protected Bitmap originalImageBitmap;
	protected int[] boundingBoxesData = new int[0];

	@Override
	public void onCreate(Bundle savedInstanceState) {

		assetManager = getAssets();
		dtNative = new DetectTextNative(assetManager);
		String[] paths = new String[] { DATA_PATH, DATA_PATH + "tessdata/" };

		for (String path : paths) {
			File dir = new File(path);
			if (!dir.exists()) {
				if (!dir.mkdirs()) {
					Log.v(TAG, "ERROR: Creation of directory " + path + " on sdcard failed");
					return;
				} else {
					Log.v(TAG, "Created directory " + path + " on sdcard");
				}
			}
		}
		
		// lang.traineddata file with the app (in assets folder)
		// You can get them at:
		// http://code.google.com/p/tesseract-ocr/downloads/list
		// This area needs work and optimization
		if (!(new File(DATA_PATH + "tessdata/" + lang + ".traineddata")).exists()) {
			try {
				InputStream in = assetManager.open("tessdata/" + lang + ".traineddata");
				//GZIPInputStream gin = new GZIPInputStream(in);
				OutputStream out = new FileOutputStream(DATA_PATH
						+ "tessdata/" + lang + ".traineddata");

				// Transfer bytes from in to out
				byte[] buf = new byte[1024];
				int len;
				//while ((lenf = gin.read(buff)) > 0) {
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
				in.close();
				//gin.close();
				out.close();
				
				Log.v(TAG, "Copied " + lang + " traineddata");
			} catch (IOException e) {
				Log.e(TAG, "Was unable to copy " + lang + " traineddata " + e.toString());
			}
		}

		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		_image = (ImageView) findViewById(R.id.PreviewImage);
		_field = (EditText) findViewById(R.id.ResultText);
		_button = (Button) findViewById(R.id.TakePictureButton);
		_textifyButton = (Button) findViewById(R.id.TextifyButton);
		_resetButton = (Button) findViewById(R.id.ResetButton);
		
		_button.setOnClickListener(new TakePicture());
		_textifyButton.setOnClickListener(new PerformRecognition());
		_resetButton.setOnClickListener(new ResetScreen());

		_path = DATA_PATH + "/ocr.jpg";
	}

	public class TakePicture implements View.OnClickListener {
		public void onClick(View view) {
			Log.v(TAG, "Starting Camera app");
			startCameraActivity();
		}
	}
	
	public class PerformRecognition implements View.OnClickListener {
		public void onClick(View view) {
			Log.v(TAG, "Pre-Textify");
			_button.setVisibility(View.GONE);
			processBitmap(originalImageBitmap, boundingBoxesData);
			_textifyButton.setVisibility(View.GONE);
			_resetButton.setVisibility(View.VISIBLE);
			Log.v(TAG, "Post-Textify");
		}
	}
	
	public class ResetScreen implements View.OnClickListener {
		public void onClick(View view) {
			_button.setVisibility(View.VISIBLE);
			_image.setVisibility(View.GONE);
			_textifyButton.setVisibility(View.GONE);
			_field.setVisibility(View.GONE);
			_field.setText("");
			_resetButton.setVisibility(View.GONE);
		}
	}

	// Simple android photo capture:
	// http://labs.makemachine.net/2010/03/simple-android-photo-capture/

	protected void startCameraActivity() {
		File file = new File(_path);
		Uri outputFileUri = Uri.fromFile(file);

		final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);

		startActivityForResult(intent, 0);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		Log.i(TAG, "resultCode: " + resultCode);

		if (resultCode == -1) {
			onPhotoTaken();
		} else {
			Log.v(TAG, "User cancelled");
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putBoolean(MainActivity.PHOTO_TAKEN, _taken);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		Log.i(TAG, "onRestoreInstanceState()");
		if (savedInstanceState.getBoolean(MainActivity.PHOTO_TAKEN)) {
			onPhotoTaken();
		}
	}

	protected void onPhotoTaken() {
		_taken = true;

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inSampleSize = 4;

		originalImageBitmap = BitmapFactory.decodeFile(_path, options);

		try {
			ExifInterface exif = new ExifInterface(_path);
			int exifOrientation = exif.getAttributeInt(
					ExifInterface.TAG_ORIENTATION,
					ExifInterface.ORIENTATION_NORMAL);

			Log.v(TAG, "Orient: " + exifOrientation);

			int rotate = 0;

			switch (exifOrientation) {
			case ExifInterface.ORIENTATION_ROTATE_90:
				rotate = 90;
				break;
			case ExifInterface.ORIENTATION_ROTATE_180:
				rotate = 180;
				break;
			case ExifInterface.ORIENTATION_ROTATE_270:
				rotate = 270;
				break;
			}

			Log.v(TAG, "Rotation: " + rotate);

			if (rotate != 0) {

				// Getting width & height of the given image.
				int w = originalImageBitmap.getWidth();
				int h = originalImageBitmap.getHeight();

				// Setting pre rotate
				Matrix mtx = new Matrix();
				mtx.preRotate(rotate);

				// Rotating Bitmap
				originalImageBitmap = Bitmap.createBitmap(originalImageBitmap, 0, 0, w, h, mtx, false);
			}

			// Convert to ARGB_8888, required by tess
			originalImageBitmap = originalImageBitmap.copy(Bitmap.Config.ARGB_8888, true);
			
		} catch (IOException e) {
			Log.e(TAG, "Couldn't correct orientation: " + e.toString());
		}

		showProcessedImagePreview(originalImageBitmap);
	}
	
	protected void showProcessedImagePreview(Bitmap bitmap)
	{
		boundingBoxesData = dtNative.processImage(bitmap);
		
		Bitmap preview;
		
		if(boundingBoxesData.length > 0)
		{
			preview = dtNative.getPreviewImage(bitmap, boundingBoxesData);
		}
		else
		{
			preview = bitmap;
		}
		
		_image.setImageBitmap(preview);
		_image.setVisibility(View.VISIBLE);
		_textifyButton.setVisibility(View.VISIBLE);
	}
	
	protected void processBitmap(Bitmap bitmap, int[] boundingBoxes)
	{
		Log.v(TAG, "Bounding boxes are:"+Arrays.toString(boundingBoxes));
		
		if(boundingBoxes.length > 0)
		{
			Log.v(TAG, "Using individual textRegions for OCR");
			for(int index=0; index<boundingBoxes.length; index+=4)
			{
				int startX = boundingBoxes[index];
				int startY = boundingBoxes[index+1];
				int endX = boundingBoxes[index+2];
				int endY = boundingBoxes[index+3];
				Bitmap textRegion = Bitmap.createBitmap(bitmap, startX, startY, endX, endY);
				useTesseract(textRegion);
			}
		}
		else
		{
			Log.v(TAG, "Using entire image for OCR");
			useTesseract(bitmap);
		}
		
		_field.setVisibility(View.VISIBLE);
	}
	
	protected void useTesseract(Bitmap bitmap)
	{
		Log.v(TAG, "Before baseApi");

		TessBaseAPI baseApi = new TessBaseAPI();
		baseApi.setDebug(true);
		baseApi.init(DATA_PATH, lang);
		baseApi.setImage(bitmap);
		
		String recognizedText = baseApi.getUTF8Text();
		
		baseApi.end();

		// You now have the text in recognizedText var, you can do anything with it.
		// We will display a stripped out trimmed alpha-numeric version of it (if lang is eng)
		// so that garbage doesn't make it to the display.

		Log.v(TAG, "OCRED TEXT: " + recognizedText);

		if ( lang.equalsIgnoreCase("eng") ) {
			recognizedText = recognizedText.replaceAll("[^a-zA-Z0-9]+", " ");
		}
		
		recognizedText = recognizedText.trim();

		if ( recognizedText.length() != 0 ) {
			_field.setText(_field.getText().toString().length() == 0 ? recognizedText : _field.getText() + " " + recognizedText);
			_field.setSelection(_field.getText().toString().length());
		}
		
		// Cycle done.
	}
}
