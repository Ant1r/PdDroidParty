package cx.mccormick.pddroidparty.PatchSelector;

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
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

import cx.mccormick.pddroidparty.R;
import cx.mccormick.pddroidparty.PatchSelector.Catalog.*;

public class CatalogView {
	private static final String TAG = "CatalogView";
	private Group root;
	private Group current;
	private Context context;
	private ListView view;
	private List<Node> displayedList = new ArrayList<Node>();

	private final int REFRESH_DELAY_MS = 200;

	CatalogView(AppCompatActivity activity, String rootPath) {
		root = new Group("", null) {
			@Override
			public void refresh() {
				scheduleRefresh();
			}
		};
		root.file = new File(rootPath);
		current = root;
		context = activity;

		view = (ListView) activity.findViewById(R.id.patch_selector);
		view.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View v, int position, long id) {
				Node n = displayedList.get(position);
				if(n == null) return;
				// return to parent directory
				if(n == current) {
					if(current.parent != null) current = current.parent;
					refresh();
					return;
				}
				// enter directory
				if(n instanceof Group) {
					current = (Group)n;
					refresh();
					return;
				}
				else {
					// run or install
					if(n.file != null) {
						runPatchDir(n.file.getPath());
					} else {
						if(n.link != null && n.parent != null) {
							installPdz(n.name, n.parent.getPath(), n.link.url.toString());
						}
					}
					return;
				}
			}
		});
		view.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> arg0, View v, int position, long id) {
				Node n = displayedList.get(position);
				Log.d(TAG, "long click on: " + n.getPath());
				showMenu(n);
				return true;
			}
		});

		activity.getOnBackPressedDispatcher().addCallback(backPressedCallback);

		root.scan(true);
		refresh();
	}

	private OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(false) {
		@Override
		public void handleOnBackPressed() {
			if(current.parent != null) current = current.parent;
			refresh();
		}
	};

	public void scan() {
		current.scan(false);
	}

	public void print() {
		Log.d(TAG, "catalog: \n" + root.print(""));
	}

	private class CatalogAdapter extends ArrayAdapter<Node> {

		private int resourceLayout;
		private Context mContext;

		public CatalogAdapter(int resource, List<Node> items) {
			super(context, resource, items);
			this.resourceLayout = resource;
			this.mContext = context;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if(convertView == null) {
				convertView = LayoutInflater.from(getContext()).inflate(resourceLayout, parent, false);
			}
			Node n = getItem(position);

			TextView textView = (TextView) convertView.findViewById(R.id.name);
			if (textView != null) {
				textView.setText(n.name);
			}
			ImageView imageView = (ImageView) convertView.findViewById(R.id.icon);
			if (imageView != null) {
				if(n == current) {
					imageView.setImageResource(R.drawable.ic_menu_back);
					TextView pathText = (TextView) convertView.findViewById(R.id.path);
					if(pathText != null && n.parent != null) {
						pathText.setText(n.parent.getPath() + "/");
						pathText.setVisibility(1);
					}
				} else {
					if(n instanceof Group) {
						if(n.link != null && n.link.fromLinksTxt == true)
							imageView.setImageResource(R.drawable.ic_menu_archive_online);
						else imageView.setImageResource(R.drawable.ic_menu_archive);
					}
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

	private void refresh() {
		Log.d(TAG, "refreshing " + current.getPath());
		current.scan(false);
		displayedList.clear();
		// if not root, add 'go back' item
		if(current.parent != null) displayedList.add(current);
		// patches first
		sortNodeList(current.items);
		displayedList.addAll(current.items);
		// then non-empty dirs
		sortNodeList(current.groups);
		for(Group n: current.groups) {
			if(n.containsItems()) displayedList.add(n);
		}
		final CatalogAdapter adapter = 
			new CatalogAdapter(R.layout.catalog_item, displayedList);
		view.setAdapter(adapter);
		backPressedCallback.setEnabled(current.parent != null);
		if(current.parent == null) print();
	}

	private class RefreshScheduler {
		private Object refreshToken = new Object();
		private Handler handler = new Handler();
		public void refresh() {
			handler.removeCallbacksAndMessages(refreshToken);
			handler.postDelayed(() -> CatalogView.this.refresh(), refreshToken, REFRESH_DELAY_MS);
		}
	}
	private RefreshScheduler refreshScheduler = new RefreshScheduler();

	public void scheduleRefresh() {
		refreshScheduler.refresh();
	}

	private void showMenu(Node node) {
		PopupMenu popup = new PopupMenu(context, view);
		popup.getMenuInflater().inflate(R.menu.catalog_context, popup.getMenu());
		if(node instanceof Item) {
			if(node.file != null) {
				popup.getMenu().findItem(R.id.delete).setVisible(true);
				popup.getMenu().findItem(R.id.check_updates).setVisible(true);
			} else {
				popup.getMenu().findItem(R.id.install).setVisible(true);
			}
		}
		popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				int id = item.getItemId();
				if(id == R.id.install) {
					if(node instanceof Item && node.link != null) {
						Log.d(TAG, "install " + node.getPath());
						installPdz(node.name, node.parent.getPath(), node.link.url.toString());
					}
					return true;
				} else if(id == R.id.check_updates) {
					if(node instanceof Item && node.link != null) {
						Log.d(TAG, "check updates " + node.getPath());
						installPdz(node.name, node.parent.getPath(), node.link.url.toString());
					}
					return true;
				} else if(id == R.id.delete) {
					Log.d(TAG, "delete " + node.getPath());
					if(node instanceof Item && node.file != null) {
						PatchUtils.deleteDirectory(node.file);
						node.file = null;
						refresh();
					}
					return true;
				}
				return false;
			}
		});
		if(popup.getMenu().hasVisibleItems()) popup.show();
	}

	public void runPatchDir(String patchDir) {}
	public void installPdz(String name, String path, String url) {}
}

