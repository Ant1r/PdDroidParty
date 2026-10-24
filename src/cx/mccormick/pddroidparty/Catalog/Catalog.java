package cx.mccormick.pddroidparty;

import java.io.IOException;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import android.util.Log;

public class Catalog {
	public static final String TAG = "Catalog";
	public static abstract class Node {
		public abstract void scan();
		public abstract String getName(boolean decorated);
		public List<Node> children = new ArrayList<Node>();
		protected Node parent = null;
		public Node getParent() {
			if(parent != null) return parent;
			return this;
		}
		Node(Node parent) {
			this.parent = parent;
		}
	}

	public static abstract class FileNode extends Node {
		protected File file;

		@Override
		public String getName(boolean decorated) {
			return file.getName();
		}
		public FileNode(Node parent, File file) {
			super(parent);
			this.file = file;
		}
		File getFile() {
			return file;
		}
	}

	public static class PatchDirNode extends FileNode {
		@Override
		public void scan(){}
		@Override
		public String getName(boolean decorated) {
			return (decorated ? "* " : "") + file.getName();
		}
		public PatchDirNode(Node parent, File file) { super(parent, file); }
	}

	public static class DirectoryNode extends FileNode {
		private boolean isPatchDir(File dir) {
			File[] files = dir.listFiles();
			for (File f: files) {
				if(f.getName().equals("droidparty_main.pd")) return true;
			}
			return false;
		}
		@Override
		public void scan(){
			children.clear();
			if(!file.isDirectory()) return;
			File[] files = file.listFiles();
			for (File f: files) {
				if(f.isDirectory()) {
					if(isPatchDir(f)) children.add(new PatchDirNode(this, f));
					else {
						DirectoryNode dirNode = new DirectoryNode(this, f);
						dirNode.scan();
						if(dirNode.children.size() != 0) children.add(dirNode);
					}
				}
			}
		}
		public String getName(boolean decorated) {
			return (decorated ? "-> " : "") + file.getName();
		}
		public String getFullName() {
			if(parent == null) return getName(false);
			else return ((DirectoryNode)parent).getFullName() + "/" + getName(false);
		}
		public String print(String prefix) {
			String s = "";
			for(Node n: children) {
				boolean isDir = n instanceof DirectoryNode;
				s += prefix + n.getName(true) + "\n";
				if(isDir) {
					s += ((DirectoryNode)n).print(prefix + "    ");
				}
			}
			return s;
		}
		public ArrayList<String> getDecoratedNameList() {
			ArrayList<String> list = new ArrayList<String>();
			if(parent != null) list.add("<-- " + getFullName());
			// patches first
			for(Node n: children) {
				if(n instanceof PatchDirNode) list.add(n.getName(true));
			}
			// then dirs
			for(Node n: children) {
				if(n instanceof DirectoryNode) list.add(n.getName(true));
			}
			return list;
		}
		public Node find(String decoratedName) {
			for(Node n: children) {
				if(decoratedName.equals(n.getName(true))) return n;
			}
			return null;
		}
		public DirectoryNode(Node parent, File file) { super(parent, file); }
		public DirectoryNode(Node parent, String name) { this(parent, new File(name)); }
	}
	
	//------------------------//
}

