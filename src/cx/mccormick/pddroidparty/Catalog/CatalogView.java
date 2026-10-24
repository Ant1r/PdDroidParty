package cx.mccormick.pddroidparty;

import java.io.IOException;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import android.util.Log;
import android.content.Context;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;

import cx.mccormick.pddroidparty.Catalog.*;

public class CatalogView {
	public static final String TAG = "CatalogView";
	private DirectoryNode root;
	private DirectoryNode current;
	CatalogView(String rootPath) {
		current = root = new DirectoryNode(null, rootPath);
	}
	public void scan() {
		root.scan();
	}
	public void print() {
		Log.d(TAG, "catalog: \n" + root.print(""));
	}
	private void update(Context context, ListView view) {
		final ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, current.getDecoratedNameList());
		view.setAdapter(adapter);
	}
	public void setup(Context context, ListView view) {
		view.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View v, int position, long id) {
				TextView item = (TextView) v;
				String name = item.getText().toString();
				Log.d(TAG, "click on: " + name);
				if(name.startsWith("<--")) {
					current = (DirectoryNode)current.getParent();
					update(context, view);
					return;
				}
				Node n = current.find(name);
				if(n == null) return;
				if(n instanceof DirectoryNode) {
					current = (DirectoryNode)n;
					update(context, view);
					return;
				}
				//launchDroidParty(patches.get(name), true);
				if(n instanceof PatchDirNode) {
					clikedOn(((PatchDirNode)n).getFile().getPath());
					return;
				}
			}
		});
		view.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> arg0, View v, int position, long id) {
				TextView item = (TextView) v;
				String name = item.getText().toString();
				Log.d(TAG, "long click on: " + name);
				return true;
			}
		});
		update(context, view);
	}
	public void clikedOn(String patchDir) {}
}

