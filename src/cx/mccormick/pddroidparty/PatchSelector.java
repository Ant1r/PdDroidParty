package cx.mccormick.pddroidparty;

/* Based on SceneSelection code by Peter Brinkmann (thanks!) */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.nio.file.Files;
import static java.nio.file.StandardCopyOption.*;

import org.puredata.core.PdBase;
import org.puredata.core.utils.IoUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import androidx.core.content.ContextCompat;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import android.Manifest;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.larvalabs.svgandroid.SVG;
import com.larvalabs.svgandroid.SVGParser;

public class PatchSelector extends Activity {

	Resources res = null;
	Handler handler;
	private static long SPLASHTIME = 2000;
	private int READ_EXTERNAL_STORAGE_PERMISSION_CODE = 54035940;
	private boolean isFirstRun = false;
	private boolean isNewInstall = false;
	Intent alreadyProcessedIntent = null;
	static final String TAG = "PatchSelector";
	String patchesPath;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		patchesPath = getExternalFilesDir(null).getPath() + "/";
		res = getResources();
		handler = new Handler();
		checkIsFirstRun();
		Log.d(TAG, "Pd version: " + PdVersionString.get());
		// if we have a baked patch, jump straight into Pd initialisation
		if (testForBakedPatch()) {
			runBackedPatch();
		} else {
			if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 34 && ContextCompat.checkSelfPermission(PatchSelector.this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
				// ask for permission to access it; then runSelectorGui()
				requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, READ_EXTERNAL_STORAGE_PERMISSION_CODE);
			} else {
				runSelectorGui();
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(final int requestCode, final String[] permissions, final int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == READ_EXTERNAL_STORAGE_PERMISSION_CODE) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				// nothing to do
			}
			runSelectorGui();
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		Log.d(TAG, "onStart");
		final Intent intent = getIntent();
		setIntent(null);
		if (intent != null && intent.getAction().equals(Intent.ACTION_VIEW)) {
			Log.d(TAG, "> Got intent : " + intent);
			if(intent == alreadyProcessedIntent) {
				alreadyProcessedIntent = null;
				Log.d(TAG, "skipping Intent already processed");
				return;
			}
			alreadyProcessedIntent = intent;
			if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) {
				Log.d(TAG, "skipping Intent coming from history");
				return;
			}
			final Uri data = intent.getData();
			if (data != null) {
				Log.d(TAG, "> Got data   : " + data);
				if(data.toString().contains("http")) {
					new PatchUtils.DownloadFileFromURL(this, patchesPath + "temp.pdz") {
						@Override
						protected void onPostExecute(String zipFile) {
							super.onPostExecute(zipFile);
							processZip(zipFile);
						}
					}.execute(data.toString());
					return;
				}
				String scheme = data.getScheme();
				if (ContentResolver.SCHEME_CONTENT.equals(scheme)) { // if the URI is of type content://
					// handle content uri to get sdcardPath
					processContentUri(data);
				} else {
					// handle as file uri
					Log.d(TAG, "> Open file  : " + data.getPath());
					processZip(data.getPath());
				}
			}
		}
		return;
	}

	private void processContentUri(Uri data) {
		try
		{
			InputStream attachment = getContentResolver().openInputStream(data);
			if (attachment == null) {
				Log.e(TAG, "processContentUri() cannot access mail attachment");
				return;
			}
			String tempPdzPath = patchesPath + "temp.pdz";
			FileOutputStream tmp = new FileOutputStream(tempPdzPath);
			byte []buffer = new byte[1024];
			while (attachment.read(buffer) > 0)
				tmp.write(buffer);
			tmp.close();
			attachment.close();
			processZip(tempPdzPath);
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void processZip(String zipPath) {
		String tempPath = patchesPath + "pdTemp/";
		PatchUtils.deleteDirectory(new File(tempPath));
		String patchName = PatchUtils.extractZipTo(zipPath, tempPath);
		new File(zipPath).delete();
		if(patchName == null) return;
		String installedVersion = PatchUtils.getPatchVersion(patchesPath + patchName);
		if (installedVersion == null) { // patch isn't installed yet
			try {
				Files.move(new File(tempPath + patchName).toPath(), new File(patchesPath + patchName).toPath(), REPLACE_EXISTING);
				launchDroidParty(patchesPath + patchName, true);
			} catch(Exception e) {}
			finish();
			return;
		}

		String zipVersion = PatchUtils.getPatchVersion(tempPath + patchName);
		if(zipVersion == null) {
			finish();
			return;
		}

		new AlertDialog.Builder(PatchSelector.this)
			.setTitle("New .pdz File")
			.setMessage("Would you like to replace installed version " + installedVersion
				+ " with version " + zipVersion
				+ " ?")
			.setCancelable(false)
			.setPositiveButton("Okay", new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					// move temp to final location
					try {
						PatchUtils.deleteDirectory(new File(patchesPath + patchName));
						Files.move(new File(tempPath + patchName).toPath(), new File(patchesPath + patchName).toPath(), REPLACE_EXISTING);
						launchDroidParty(patchesPath + patchName, true);
					} catch(Exception e) {}
					finish();
				}
			})
			.setNegativeButton("Cancel", new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					launchDroidParty(patchesPath + patchName, true);
					finish();
				}
			})
			.show();
	}

	private void closePd() {
		// TODO Auto-generated method stub
		Toast.makeText(PatchSelector.this, "PdDroidParty: File Format not Supported, or bad file", Toast.LENGTH_LONG).show();
		finish();
	}

	@Override
	public void finish() {
		Log.d(TAG, "finish()");
		super.finish();
	}

	private void launchDroidParty(String path, boolean fromPatchSelector) {
		String pdMain = PatchUtils.getMainPatchPath(path).getPath();
		if(pdMain == null) return;
		Intent intent = new Intent(this, PdDroidParty.class);
		intent.putExtra(PdDroidParty.PATCH, pdMain);
		intent.putExtra(PdDroidParty.FROM_SELECTOR, fromPatchSelector);
		startActivity(intent);
	}

	private boolean testForBakedPatch() {
		return res.getIdentifier("patch", "raw", getPackageName()) != 0;
	}

	private String unpackBakedPatch() {
		// if we have a patch zip
		if (! testForBakedPatch()) return null;
		// where we will be storing the patch on the sdcard
		String basedir = patchesPath;
		// version file for the existing patch
		File version = new File(basedir + "patch/VERSION-" + res.getInteger(res.getIdentifier("revno", "integer", getPackageName())));
		// if the version file does not exist
		if (!version.exists()) {
			try {
				IoUtils.extractZipResource(getResources().openRawResource(res.getIdentifier("patch", "raw", getPackageName())), new File(basedir), false);
			} catch (IOException e) {
				// do nothing
				return null;
			}
		}
		PdBase.addToSearchPath(basedir + "patch");
		// also add the recordings directory
		(new File(basedir, "recordings")).mkdirs();
		// also add the safefiles directory
		(new File(basedir, "savefiles")).mkdirs();
		// return the patch path to launch Pd
		return basedir;
	}

	private void addBundledAbstractions() {
		Resources res = getResources();
		File libDir = getFilesDir();
		try {
			IoUtils.extractZipResource(res.openRawResource(R.raw.abstractions), libDir, true);
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		}
		PdBase.addToSearchPath(libDir.getAbsolutePath());
	}

	private void searchPath(String path, Map<String, String> patches) {
		Log.e(TAG, "search path:" + path);
		List<File> list = IoUtils.find(new File(path), ".*droidparty_main\\.pd$");

		//Log.e(TAG, "patches list: " + list.toString());
		for (File f : list) {
			String[] parts = f.getParent().split("/");
			// exclude generic patch directories found in apps based on PdDroidParty
			if (!parts[parts.length - 1].equals("patch")) {
				patches.put(parts[parts.length - 1], f.getParent());
				//Log.d(TAG, "AbsPath: " + f.getParent());
			}
		}
	}

	private void checkIsFirstRun() {
		if(isFirstRun || isNewInstall) return;
		try {
			SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
			long lastInstalled = preferences.getLong("installed", 0);
			PackageManager pm = getPackageManager();
			ApplicationInfo appInfo = pm.getApplicationInfo(getPackageName(), 0);
			String appFile = appInfo.sourceDir;
			long installed = new File(appFile).lastModified();
			if(installed > lastInstalled){
				Editor editor = preferences.edit();
				editor.putLong("installed", installed);
				editor.apply();
				isNewInstall = true;
				Log.d(TAG, "new installation");
			}
			if(lastInstalled == 0) {
				isFirstRun = true;
				Log.d(TAG, "very first run!");
			}
		} catch(Exception e) {}
	}

	private void copyDemos() {
		List<File> demosFiles = null;
		try {
			Log.d(TAG, "copyDemos() to: " + patchesPath);
			InputStream demos = getResources().openRawResource(R.raw.demos);
			demosFiles = IoUtils.extractZipResource(demos, new File(patchesPath), false);
		} catch (Exception e) {
			// do nothing
			Log.d(TAG, "error extracting demos: " + e.toString());
			return;
		}
		/*if (demosFiles != null && demosFiles.size() != 0) {
			for (File file : demosFiles) {
				Log.d(TAG, "extracted: " + file.getAbsolutePath());
			}
		}*/
	}

	private void runBackedPatch() {
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		// Create a new ImageView
		ImageView imageView = new ImageView(this);
		// Set the background color to white
		imageView.setBackgroundColor(Color.WHITE);
		int splashres = res.getIdentifier("splash", "raw", getPackageName());
		if (splashres != 0) {
			// Parse the SVG file from the resource
			SVG svg = SVGParser.getSVGFromResource(getResources(), splashres);
			// Get a drawable from the parsed SVG and set it as the drawable for the ImageView
			imageView.setImageDrawable(svg.createPictureDrawable());
			// Set the ImageView as the content view for the Activity
			setContentView(imageView);
		}
		// Unpack the patch in a separate thread, then run it
		ExecutorService executor = Executors.newSingleThreadExecutor();
		executor.execute(() -> {
			long start = System.currentTimeMillis();
			String bakedpatch = unpackBakedPatch();
			long elapsed = System.currentTimeMillis() - start;
			if (elapsed < SPLASHTIME) {
				try {
					Thread.sleep(SPLASHTIME - elapsed);
				} catch (Exception e) {}
			}
			if (bakedpatch != null) {
				launchDroidParty(bakedpatch, false);
			}
			finish();
		});
	}

	private void runSelectorGui() {
		setContentView(R.layout.patch_selector);
		ListView patchList = (ListView) findViewById(R.id.patch_selector);
		/*
		Map<String, String> patches = new HashMap<String, String>();

		patchList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View v, int position, long id) {
				TextView item = (TextView) v;
				String name = item.getText().toString();
				launchDroidParty(patches.get(name), true);
			}
		});
		patchList.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> arg0, View v, int position, long id) {
				TextView item = (TextView) v;
				String name = item.getText().toString();
				Log.d(TAG, "long click on: " + name);
				return true;
			}
		});
		ProgressDialog progress = new ProgressDialog(PatchSelector.this);
		progress.setMessage("Finding patches...");
		progress.setCancelable(false);
		progress.setIndeterminate(true);
		progress.show();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		executor.execute(() -> {
			addBundledAbstractions();
			if(isFirstRun || isNewInstall) copyDemos();
			searchPath(patchesPath, patches);
			ArrayList<String> keyList = new ArrayList<String>(patches.keySet());
			Collections.sort(keyList, new Comparator<String>() {
				public int compare(String a, String b) {
					return a.toLowerCase().compareTo(b.toLowerCase());
				}
			});
			final ArrayAdapter<String> adapter = new ArrayAdapter<String>(PatchSelector.this, android.R.layout.simple_list_item_1, keyList);

			handler.post(() -> {
				patchList.setAdapter(adapter);
				try {
					progress.dismiss();
				} catch (Exception e) {
					// nothing
				}
			});
		});*/
		Catalog.setCacheDir(getCacheDir());
		CatalogView catalog = new CatalogView(patchesPath){
			@Override
			public void clikedOn(String patchDir) {
				launchDroidParty(patchDir, true);
			}
		};
		catalog.scan();
		catalog.print();
		catalog.setup(PatchSelector.this, patchList);
	}
}
