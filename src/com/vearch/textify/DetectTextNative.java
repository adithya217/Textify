package com.vearch.textify;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.android.Utils;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

public class DetectTextNative {
	
    static { 
    	System.loadLibrary("opencv_java"); //load opencv_java lib
		System.loadLibrary("run_text_detection");
	}
	
	private long detectPtr = 0;
	
    public DetectTextNative(AssetManager am) {
    	detectPtr = create();
    }

	private native long create();
	private native void destroy(long detectPtr);
	private native int[] getBoundingBoxes(long detectPtr, long matAddress);

	@Override
	protected void finalize() throws Throwable {
		if(detectPtr != 0) {
			destroy(detectPtr);
		}
		super.finalize();
	}
	
	public int[] getBoundingBoxes(long matAddress) {
		return getBoundingBoxes(detectPtr, matAddress);
	}
	
	public Mat getImageMatFromBitmap(Bitmap bmap) {
		Mat image = new Mat();
		Utils.bitmapToMat(bmap, image);
		return image;
	}
	
	public int[] processImage(Bitmap bmap) {
		Mat image = getImageMatFromBitmap(bmap);
		return getBoundingBoxes(image.getNativeObjAddr());
	}
	
	public Bitmap getPreviewImage(Bitmap bmap, int[] boundingBoxes) {
		Mat image = getImageMatFromBitmap(bmap);
		for(int index=0; index<boundingBoxes.length; index+=4)
		{
			int startX = boundingBoxes[index];
			int startY = boundingBoxes[index+1];
			int endX = startX + boundingBoxes[index+2];
			int endY = startY + boundingBoxes[index+3];
			Core.rectangle(image, new Point(startX,startY), new Point(endX,endY), new Scalar(0,255,0), 1);
		}
		
		Bitmap preview = Bitmap.createBitmap(bmap.getWidth(), bmap.getHeight(), Bitmap.Config.ARGB_8888);
		Utils.matToBitmap(image, preview);
		
		return preview;
	}
}