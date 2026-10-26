package cx.mccormick.pddroidparty;

import java.io.IOException;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.util.Log;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import androidx.databinding.DataBindingUtil;

import cx.mccormick.pddroidparty.Catalog.*;
import cx.mccormick.pddroidparty.databinding.CatalogItemBinding;

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

	public class CatalogAdapter extends ArrayAdapter<Node> {

		private int resourceLayout;
		private Context mContext;

		public CatalogAdapter(Context context, int resource, List<Node> items) {
			super(context, resource, items);
			this.resourceLayout = resource;
			this.mContext = context;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			CatalogItemBinding binding;
			if(convertView == null) {
				binding = CatalogItemBinding.inflate(LayoutInflater.from(getContext()), parent, false);
				convertView = binding.getRoot();
			}
			else {
				binding = (CatalogItemBinding) convertView.getTag();
			}
			Node n = getItem(position);
			binding.setNode(n);
			convertView.setTag(binding);

			TextView textView = (TextView) convertView.findViewById(R.id.name);
			if (textView != null) {
				textView.setText(n.name);
			}
			ImageView imageView = (ImageView) convertView.findViewById(R.id.icon);
			if (imageView != null) {
				if(n == current) imageView.setImageResource(R.drawable.ic_menu_back);
				else {
					if(n instanceof Group) imageView.setImageResource(R.drawable.ic_menu_archive);
					else if(n.file == null) imageView.setImageResource(R.drawable.download);
					else imageView.setImageResource(R.drawable.icon);
				}
			}
			return convertView;
		}
	}

	private static void sortNodeList(List<? extends Node> list) {
		Collections.sort(list, new Comparator<Node>() {
			public int compare(Node a, Node b) {
				return a.name.toLowerCase().compareTo(b.name.toLowerCase());
			}
		});
	}

	private void update(Context context, ListView view) {
		List<Node> list = new ArrayList<Node>();
		if(current.parent != null) list.add(current);
		// patches first
		sortNodeList(current.items);
		list.addAll(current.items);
		// then dirs
		sortNodeList(current.groups);
		list.addAll(current.groups);
		final CatalogAdapter adapter = 
			new CatalogAdapter(context, R.layout.catalog_item, list);
		view.setAdapter(adapter);
	}

	private void showMenu(Context context, View view, Node node) {
		PopupMenu popup = new PopupMenu(context, view);
		popup.getMenuInflater().inflate(R.menu.catalog_context, popup.getMenu());
		popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				int id = item.getItemId();
				if(id == R.id.install) {
					Log.d(TAG, "install " + node.getPath());
					return true;
				} else if(id == R.id.check_updates) {
					Log.d(TAG, "check updates " + node.getPath());
					return true;
				} else if(id == R.id.delete) {
					Log.d(TAG, "delete " + node.getPath());
					return true;
				}
				return false;
			}
		});
		popup.show();
	}

	public void setup(Context context, ListView view) {
		view.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View v, int position, long id) {
				Node n = ((CatalogItemBinding)DataBindingUtil.getBinding(v)).getNode();
				if(n == null) return;
				if(n == current) {
					if(current.parent != null) current = current.parent;
					update(context, view);
					return;
				}
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
				Node n = ((CatalogItemBinding)DataBindingUtil.getBinding(v)).getNode();
				Log.d(TAG, "long click on: " + n.getPath());
				showMenu(context, view, n);
				return true;
			}
		});
		update(context, view);
	}

	public void clikedOn(String patchDir) {}
}

