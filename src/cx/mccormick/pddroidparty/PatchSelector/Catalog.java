package cx.mccormick.pddroidparty.PatchSelector;

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
	public static final int MAX_DEPTH = 100;

	protected static File cacheDir;
	public static void setCacheDir(File dir) {
		cacheDir = dir;
	}

	// ----------------------------------------------------------//

	public static class Link {
		public String name;
		public URL url = null;
		boolean fromLinksTxt = false;
		boolean isPdz = false;
		boolean scanned = false;
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

		private void parseJSONArray(JSONArray json, Group group) {
				for(int i = 0; i < json.length(); i++) {
				try {
						JSONObject object = json.getJSONObject(i);
						String objType = object.optString("type", "?");
						String objName = object.getString("name");
						if(objType.equals("dir")) {
							Group n = group.getGroup(objName);
							n.link = new GhLink(name, new URL(object.getString("url")));
						} else {
							String downloadUrl = object.optString("download_url", object.optString("browser_download_url", null));
							if(downloadUrl == null) continue;
							URL objUrl = new URL(downloadUrl);
							if(objName.endsWith(".pdz")) {
								String patchName = objName.substring(0, objName.length() - 4);
								Item n = group.get(patchName);
								n.link = new Link(patchName, objUrl);
							} else if(objName.equals("links.txt")) {
								processLinksFileUrl(objUrl, group);
							}
						}
					} catch(Exception e) {
					Log.d(TAG, "GhLink.parseJSONArray() exception: " + e.toString());
				}
			}
		}

		@Override
		public void scan(Group group) {
			if(scanned) return;
			scanned = true;
			if(group.depth() >= MAX_DEPTH) return;
			new StringDownloader(){
				@Override
				protected void onPostExecute(String downloadedString) {
					Log.d(TAG, "json of link " + group.getPath() + " downloaded, parsing it.");
					try {
						JSONArray json = new JSONArray(downloadedString);
						parseJSONArray(json, group);
					} catch(Exception e) {}
					try {
						JSONObject json = new JSONObject(downloadedString);
						JSONArray assetsJson = json.optJSONArray("assets");
						if(assetsJson != null) {
							//Log.d(TAG, "GhLink.scan() got assets");
							parseJSONArray(assetsJson, group);
						}
						String body = json.optString("body", null);
						if(body != null) {
							//Log.d(TAG, "GhLink.scan() got body: " + body);
							processLinksString(body, group);
						}
					} catch(Exception e) {}
				}
			}.execute(url);
		}

	}

	public static class LinkLink extends Link {
		LinkLink(String name, URL url) {
			super(name, url);
		}

		@Override
		public void scan(Group group) {
			if(scanned) return;
			scanned = true;
			if(group.depth() >= MAX_DEPTH) return;
			processLinksFileUrl(url, group);
		}
	}

	private static Link buildLink(String linksLine) {
		String[] words = linksLine.split(" ");
		if(words.length < 3) return null;
		try {
			if(words[0].equals("gh")) {
				URL url = new URL(words[2]);
				return new GhLink(words[1], url);
			} else if(words[0].equals("pdz")) {
				URL url = new URL(words[2]);
				Link link = new Link(words[1], url);
				link.isPdz = true;
				return link;
			} else if(words[0].equals("links")) {
				URL url = new URL(words[2]);
				return new LinkLink(words[1], url);
			}
		} catch(Exception e) { return null;}
		return null;
	}

	private static void processLinkLine(String linksLine, Group parent) {
		Link link = buildLink(linksLine);
		if(link != null) {
			link.fromLinksTxt = true;
			Node n;
			if(link.isPdz) n = parent.get(link.name);
			else n = parent.getGroup(link.name);
			if(n.link == null) {
				n.link = link;
				String linktype = "none";
				if(link.isPdz) linktype = "pdz";
				else if(link instanceof GhLink) linktype = "gh";
				else if(link instanceof LinkLink) linktype = "link";
				Log.d(TAG, "add '" + linktype + "' link: '" + link.name + "' to " + parent.getPath());
				parent.getRoot().refresh();
			}
		}
	}

	private static void processLinksString(String linksString, Group group) {
		String[] lines = linksString.split("[\\r\\n]+");
		int firstLine = 0;
		int lastLine = lines.length - 1;
		for(int i = 0; i < lines.length; i++) {
			if(lines[i].startsWith("# LINKS")) {
				firstLine = i + 1;
			}
			else if(lines[i].startsWith("# ENDLINKS")) {
				lastLine = i - 1;
			}
		}
		if(firstLine < lines.length && lastLine >= 0 && firstLine <= lastLine)
			for(int i = firstLine; i <= lastLine; i++) {
				processLinkLine(lines[i], group);
			}
	}

	private static void processLinksFileUrl(URL url, Group group) {
		new StringDownloader(){
			@Override
			protected void onPostExecute(String downloadedString) {
				processLinksString(downloadedString, group);
			}
		}.execute(url);
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
			if(link != null && link.fromLinksTxt == true) {
				return "=> ";
			}
			return "-> ";
		}

		public boolean containsItems() {
			if(link != null && ! link.scanned) return true;
			if(items.size() != 0) return true;
			else for(Group n: groups) {
				if(n.containsItems()) return true;
			}
			return false;
		}

		public int depth() {
			int i = 0;
			Group g = this;
			while(g.parent != null) {
				g = g.parent;
				i++;
			}
			return i;
		}

		public Group getRoot() {
			Group g = this;
			while(g.parent != null) g = g.parent;
			return g;
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
				getRoot().refresh();
			}
			return n;
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
				getRoot().refresh();
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

		public void scan(boolean recursive) {
			if(link != null && ! recursive) link.scan(this);
			if(file == null) return;
			if(!file.isDirectory()) return;
			if(depth() >= MAX_DEPTH) return;
			File[] files = file.listFiles();
			for (File f: files) {
				if(f.isDirectory()) {
					if(isPatchDir(f)) {
						Item n = get(f.getName());
						n.file = f;
					} else {
						Group n = getGroup(f.getName());
						n.file = f;
						if(recursive) n.scan(true);
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

		public void refresh() {}
	}
}
