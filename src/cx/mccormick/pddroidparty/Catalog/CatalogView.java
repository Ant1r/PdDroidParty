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
	private Group root;
	private Group current;

	CatalogView(String rootPath) {
		root = new Group("", null);
		root.file = new File(rootPath);
		current = root;
	}

	public void scan() {
		root.scan();
	}

	public void print() {
		Log.d(TAG, "catalog: \n" + root.print(""));
	}

	private void update(Context context, ListView view) {
		ArrayList<String> list = new ArrayList<String>();
		if(current.parent != null) list.add("<-- " + current.getPath("/"));
		// patches first
		for(Node n: current.items) {
			list.add(n.prefix() + n.name);
		}
		// then dirs
		for(Group n: current.groups) {
			list.add(n.prefix() + n.name);
		}
		final ArrayAdapter<String> adapter = 
			new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, list);
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
					if(current.parent != null) current = current.parent;
					update(context, view);
					return;
				}
				Node n = current.findWithPrefix(name);
				if(n == null) return;
				if(n instanceof Group) {
					current = (Group)n;
					update(context, view);
					return;
				}
				else {
					//launchDroidParty(n.file);
					if(n.file != null) {
						clikedOn(n.file.getPath());
					}
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

