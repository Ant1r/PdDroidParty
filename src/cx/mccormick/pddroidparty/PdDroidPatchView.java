package cx.mccormick.pddroidparty;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

public class PdDroidPatchView extends View implements OnTouchListener {
	private static final String TAG = "PdDroidPatchView";
	
	Paint paint = new Paint();
	public int patchwidth;
	public int patchheight;
	public int fontsize;
	ArrayList<Widget> widgets = new ArrayList<Widget>();
	public PdDroidParty app;
	
	public PdDroidPatchView(Context context, PdDroidParty parent) {
		super(context);
		
		app = parent;
		
		setFocusable(true);
		setFocusableInTouchMode(true);
		
		this.setOnTouchListener(this);
		
		paint.setColor(Color.WHITE);
		paint.setAntiAlias(true);
	}
	
	@Override
	public void onDraw(Canvas canvas) {
		canvas.drawPaint(paint);
		// draw all widgets
		if (widgets != null) {
			for (Widget widget: widgets) {
				widget.draw(canvas);
			}
		}
	}
	
	public boolean onTouch(View view, MotionEvent event) {
		// if(event.getAction() != MotionEvent.ACTION_DOWN)
		// return super.onTouchEvent(event);
		if (widgets != null) {
			for (Widget widget: widgets) {
				widget.touch(event);
			}
		}
		invalidate();
		//Log.d(TAG, "touch: " + event.getX() + " " + event.getY());
		return true;
	}
	
	/** Lets us invalidate this view from the audio thread */
	public void threadSafeInvalidate() {
		final PdDroidPatchView me = this;
		app.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				me.invalidate();
			}
		});
	}
	
	/** build a user interface using the lines of atoms found in the patch by the pd file parser */
	public void buildUI(PdParser p, ArrayList<String[]> atomlines) {
		//ArrayList<String> canvases = new ArrayList<String>();
		int level = 0;
		
		for (String[] line: atomlines) {
			if (line.length >= 4) {
				// find canvas begin and end lines
				if (line[1].equals("canvas")) {
					/*if (canvases.length == 0) {
						canvases.add(0, "self");
					} else {
						canvases.add(0, line[6]);
					}*/
					level += 1;
					if (level == 1) {
						patchwidth = Integer.parseInt(line[4]);
						patchheight = Integer.parseInt(line[5]);
						fontsize = Integer.parseInt(line[6]);
					}
				} else if (line[1].equals("restore")) {
					//canvases.remove(0);
					level -= 1;
				// find different types of UI element in the top level patch
				} else if (level == 1) {
					if (line.length >= 5) {
						if (line[4].equals("vsl")) {
							widgets.add(new Slider(this, line, false));
						} else if (line[4].equals("hsl")) {
							widgets.add(new Slider(this, line, true));
						} else if (line[4].equals("tgl")) {
							widgets.add(new Toggle(this, line));
						} else if (line[4].equals("bng")) {
							widgets.add(new Bang(this, line));
						} else if (line[4].equals("nbx")) {
							widgets.add(new Numberbox2(this, line));
						}
					} else if (line.length >= 2) {
						if (line[1].equals("text")) {
							widgets.add(new Comment(this, line));
						} else if (line[1].equals("floatatom")) {
							widgets.add(new Numberbox(this, line));
						}
					}
				}
			}
		}
		threadSafeInvalidate();
	}
}
