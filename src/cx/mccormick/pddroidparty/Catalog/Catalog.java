package cx.mccormick.pddroidparty;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import android.util.Log;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.URL;

import org.json.JSONObject;
import org.json.JSONArray;

public class Catalog {
	public static final String TAG = "Catalog";
	protected static File cacheDir;
	public static void setCacheDir(File dir) {
		cacheDir = dir;
	}

	// ----------------------------------------------------------//

	public static class Link {
		public String name;
		public URL url = null;
		Link(String name, URL url) {
			this.name = name;
			this.url = url;
		}
		public void scan(Group group) {}
	}

	public static class GhLink extends Link {
		GhLink(String name, URL url) {
			super(name, url);
		}
		@Override
		public void scan(Group group) {
			new StringDownloader(){
				@Override
				protected void onPostExecute(String downloadedString) {
					try {
						Log.d(TAG, "json of link " + group.getPath() + " downloaded, parsing it:");
						JSONArray json = new JSONArray(downloadedString);
						for(int i = 0; i < json.length(); i++) {
							JSONObject object = json.getJSONObject(i);
							String objType = object.optString("type", "?");
							String objName = object.getString("name");
							if(objType.equals("dir")) {
								Group n = group.getGroup(objName);
								n.link = new GhLink(name, new URL(object.getString("url")));
								n.link.scan(n);
							} else {
								Log.d(TAG, objName + " type:" + objType);
								if(objName.endsWith(".pdz")) {
									String patchName = objName.substring(0, objName.length() - 4);
									Item n = group.get(patchName);
									n.link = new Link(patchName, new URL(object.getString("download_url")));
								}
							}
						}
					} catch(Exception e) {}
				}
			}.execute(url);
		}
	}

	public static Link buildLink(String linksLine) {
		String[] words = linksLine.split(" ");
		if(words[0].equals("gh")) try {
			URL url = new URL(words[2]);
			return new GhLink(words[1], url);
		} catch(Exception e) { return null;}
		return null;
	}

	public static void processLinkLine(String linksLine, Group parent) {
		Link link = buildLink(linksLine);
		if(link != null) {
			Log.d(TAG, "found link: " + link.name);
			Group n = parent.getGroup(link.name);
			n.link = link;
			link.scan(n);
		}
	}
	// ----------------------------------------------------------//

	public static class Node {
		public String name;
		public Group parent;
		public File file = null;
		public Link link = null;

		public String getPath(String separator) {
			if(parent == null) return name;
			else return parent.getPath(separator) + separator + name;
		}

		public String getPath() {
			return getPath("/");
		}

		public String prefix() {
			String p = "*  ";
			if(link != null) {
				if(file != null) p = "+  ";
				else p = "-  ";
			}
			return p;
		}

		Node(String name, Group parent) {
			this.name = name;
			this.parent = parent;
		}
	}

	public static class Item extends Node {
		Item(String name, Group parent) {
			super(name, parent);
			Log.d(TAG, "creating item: " + name + " in group: " + parent.getPath());
		}
	}

	public static class Group extends Node {
		public List<Item> items = new ArrayList<Item>();
		public List<Group> groups = new ArrayList<Group>();

		Group(String name, Group parent) {
			super(name, parent);
			if(parent == null) Log.d(TAG, "creating root group");
			else Log.d(TAG, "creating group: " + name + " in group: " + parent.getPath());
		}

		@Override
		public String prefix() {
			if(link != null && file == null) {
				return "=> ";
			}
			return "-> ";
		}

		private static boolean isPatchDir(File dir) {
			File[] files = dir.listFiles();
			for (File f: files) {
				if(f.getName().equals("droidparty_main.pd")) return true;
			}
			return false;
		}

		public Item find(String name) {
			for(Item n: items) {
				if(name.equals(n.name)) return n;
			}
			return null;
		}

		public Item get(String name) {
			Item n = find(name);
			if(n == null) {
				n = new Item(name, this);
				items.add(n);
			}
			return n;
		}

		public Node findWithPrefix(String name) {
			for(Node n: items) {
				if(name.equals(n.prefix() + n.name)) return n;
			}
			for(Node n: groups) {
				if(name.equals(n.prefix() + n.name)) return n;
			}
			return null;
		}

		public Group findGroup(String name) {
			for(Group n: groups) {
				if(name.equals(n.name)) return n;
			}
			return null;
		}

		public Group getGroup(String name) {
			Group n = findGroup(name);
			if(n == null) {
				n = new Group(name, this);
				groups.add(n);
			}
			return n;
		}

		private void processLinksFile(File f) {
			Log.d(TAG, "in " + getPath() + "/, found links.txt, parsing it...");
			try {
				List<String> lines = Files.readAllLines(Paths.get(f.getPath()));
				for(String line: lines) {
					processLinkLine(line, this);
				}
			} catch(Exception e) {}
		}

		public void scan() {
			if(file == null) return;
			if(!file.isDirectory()) return;
			File[] files = file.listFiles();
			for (File f: files) {
				if(f.isDirectory()) {
					if(isPatchDir(f)) {
						Item n = get(f.getName());
						n.file = f;
					} else {
						Group n = findGroup(f.getName());
						boolean alreadyExists = (n != null);
						if(! alreadyExists) n = new Group(f.getName(), this);
						n.file = f;
						n.scan();
						if(! alreadyExists) {
							if(n.items.size() != 0 || n.groups.size() != 0) groups.add(n);
						}
					}
				} else if(f.getName().equals("links.txt")) {
					processLinksFile(f);
				}
			}
		}

		public String print(String prefix) {
			String s = "";
			for(Item n: items) {
				s += prefix + n.prefix() + n.name + "\n";
			}
			for(Group n: groups) {
				s += prefix + n.prefix() + n.name + "\n";
				s += n.print(prefix + "    ");
			}
			return s;
		}
	}
}
