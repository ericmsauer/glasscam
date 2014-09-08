package com.sauer.android.glass.glassCam;

import java.util.ArrayList;
import java.util.List;

import com.google.android.glass.app.Card;
import com.google.android.glass.media.Sounds;
import com.google.android.glass.sample.apidemo.card.CardAdapter;
import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.google.android.glass.touchpad.GestureDetector.*;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;
import com.sauer.android.glassCam.camera.R;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.widget.AdapterView;
import android.widget.TextView;

public class CameraService
		extends Activity
        implements BaseListener, FingerListener, Callback, ScrollListener {
	
	private static final String LIVE_CARD_TAG = CameraService.class.getSimpleName();
	
    //UI
	private int currentSetting = 0;
	private int currentView = 0;
	private CardScrollAdapter mAdapter;
	private CardScrollView mCardScroller;
    private SurfaceView cameraView;
    private SurfaceHolder cameraHolder;
    private TextView valueText;
    
    //Hardware Elements
    private Camera mCamera;
    private GestureDetector mGestureDetector;
    private SoundPool mSoundPool;
    private int mShutterSoundId;
    
    //MER
    private int currentRotation = 0;
    private int currentColor = 30;
    private int currentZoom = 0;

    //Called when the app is ran
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mAdapter = new CardAdapter(createCards(this));
        mCardScroller = new CardScrollView(this);
        mCardScroller.setAdapter(mAdapter);
        setCardScrollerListener();
        
    	setUpCamera();

        // Initialize the gesture detector and set the activity to listen to discrete gestures.
        mGestureDetector = new GestureDetector(this).setBaseListener(this).setFingerListener(this).setScrollListener(this);
        
        //Set up sound
        mSoundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
        mShutterSoundId = mSoundPool.load(this, R.raw.start, 1);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
    
    @Override
    protected void onStop(){
    	super.onStop();
    }
    
    @Override
    protected void onDestroy(){
    	releaseCamera();
    	super.onDestroy();
    }

    /**
     * Key Press Methods
     *--------------------------------------------
     */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return mGestureDetector.onMotionEvent(event);
    }

    @Override
    public boolean onGesture(Gesture gesture) {
    	if(gesture == Gesture.TWO_TAP){
    		mCardScroller.activate();
    		currentView = 1;
            setContentView(mCardScroller);
            releaseCamera();
    		return true;
    	}
        return false;
    }
    
    @Override
    public boolean onScroll(float displacement, float delta, float velocity) {
    	if(currentView == 0)
    	{
	    	Parameters parameters = mCamera.getParameters();
	    	if(currentSetting == 0)
	    	{
	    		if(velocity > 0)
	    			currentZoom += 2;
	    		else
	    			currentZoom -= 2;
		    	if(currentZoom < 0)
		    		currentZoom = 0;
		    	if(currentZoom > parameters.getMaxZoom())
		    		currentZoom = parameters.getMaxZoom();
		    	valueText.setText(String.format("%01d", currentZoom));
		    	parameters.setZoom(currentZoom);
	    	}
	    	if(currentSetting == 1)
	    	{
	    		if(velocity > 0)
	    			currentRotation += 1;
	    		else
	    			currentRotation -= 1;
		    	if(currentRotation < 0)
		    		currentRotation = 0;
		    	if(currentRotation > 2)
		    		currentRotation = 2;
		    	valueText.setText(String.format("%01d", currentRotation*90));
		    	parameters.setExposureCompensation(currentRotation*90);	
	    	}
	    	if(currentSetting == 2)
	    	{
	    		if(displacement > 0)
	    			currentColor += 1;
	    		else
	    			currentColor -= 1;
		    	if(currentColor < 20)
		    		currentColor = 20;
		    	if(currentColor > 30)
		    		currentColor = 30;
		    	parameters.setPreviewFpsRange(currentColor*1000, 30000);
		    	valueText.setText(String.format("%01d", currentColor));
	    	}
	    	mCamera.setParameters(parameters);
	    	return true;
    	}
    	return false;
    }

    @Override
    public void onFingerCountChanged(int previousCount, int currentCount) {
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_CAMERA) {
        	takePicture();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
        	if(currentView == 1)
        	{
        		setUpCamera();
        		return true;
        	}
        	if(currentView == 0)
        	{
        		releaseCamera();
        		return false;
        	}
        }
        return false;
    }

    private void setCardScrollerListener() {
        mCardScroller.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                    	currentSetting = 0;
                    	setUpCamera();
                        break;

                    case 1:
                    	currentSetting = 1;
                    	setUpCamera();
                        break;

                    case 2:
                    	currentSetting = 2;
                    	setUpCamera();
                        break;

                    default:
                    	break;
                }
            }
        });
    }
    
    /**
     * Surface Methods
     *--------------------------------------------
     */
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		initCamera();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		if (mCamera != null)
		{
			mCamera.startPreview();
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		//releaseCamera();
	}
	
    /**
     * Menu Methods
     *--------------------------------------------
     */
    private List<Card> createCards(Context context) {
        ArrayList<Card> cards = new ArrayList<Card>();
        cards.add(0, new Card(context).setText("Zoom"));
        cards.add(1, new Card(context).setText("Rotation"));
        cards.add(2, new Card(context).setText("FrameRate"));
        return cards;
    }
	
    /**
     * Camera Methods
     *--------------------------------------------
     */
    public void setUpCamera()
    {
		currentView = 0;
		mCardScroller.deactivate();
    	setContentView(R.layout.card_glasscam);
    	cameraView = (SurfaceView) findViewById(R.id.cameraview);
 		cameraHolder = cameraView.getHolder();
 		cameraHolder.addCallback(this);
 		cameraHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
 		
    	valueText = (TextView) findViewById(R.id.valueText);
    	if(currentSetting == 2)
    		valueText.setText("30");
    	else
    		valueText.setText("0");
    }
    
	public void initCamera()
	{
		//Reset the camera Settings
		releaseCamera();
		
		//Set up camera and its ettings
		mCamera = Camera.open();
		Parameters parameters = mCamera.getParameters();
		parameters.setPreviewFpsRange(30000, 30000);
		mCamera.setParameters(parameters);	
		
		try {
			mCamera.setPreviewDisplay(cameraHolder);
		} 
		catch (Exception e) {
			this.releaseCamera();
		}
	}
	
	private void takePicture() {
		if(mCamera != null)
		{
			mCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
			releaseCamera();
		}
	}
	
	ShutterCallback shutterCallback = new ShutterCallback() {
		public void onShutter() {
			playSound(mShutterSoundId);
		}
	};
	PictureCallback rawCallback = new PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
			// TODO Auto-generated method stub
		}
	};

	PictureCallback jpegCallback = new PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
			// TODO Auto-generated method stub
		}
	};
	
	public void releaseCamera() 
	{
		if (mCamera != null) 
		{
			mCamera.stopPreview();
	        mCamera.release();
	        mCamera = null;
		}
	}
	
    /**
     * Sound Methods
     *--------------------------------------------
     */
    protected void playSound(int soundId) {
        mSoundPool.play(soundId,
                        1 /* leftVolume */,
                        1 /* rightVolume */,
                        1,
                        0 /* loop */,
                        1 /* rate */);
    }
}
