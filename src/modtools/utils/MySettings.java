package modtools.utils;

import arc.files.Fi;
import arc.struct.*;
import arc.util.Log;
import arc.util.serialization.Jval;
import arc.util.serialization.Jval.JsonMap;
import mindustry.Vars;
import rhino.ScriptRuntime;

import java.util.Objects;

public class MySettings {
	public static final Fi dataDirectory = Vars.dataDirectory.child("b0kkihope");

	static {
		try {
			Fi fi = Vars.dataDirectory.child("mods(I hope...)");
			if (fi.exists() && fi.isDirectory()) {
				fi.copyFilesTo(dataDirectory);
				fi.deleteDirectory();
			}
		} catch (Throwable ignored) {}
	}

	static Fi config = dataDirectory.child("mod-tools-config.hjson");

	public static final Data
			SETTINGS      = new Data(config),
			D_JSFUNC_EDIT = (Data) SETTINGS.get("JsfuncEdit", () -> new Data(SETTINGS, new JsonMap())),
			D_JSFUNC      = (Data) SETTINGS.get("Jsfunc", () -> new Data(SETTINGS, new JsonMap())),
			D_BLUR        = (Data) SETTINGS.get("BLUR", () -> new Data(SETTINGS, new JsonMap()));

	public static class Data extends OrderedMap<String, Object> {
		public Data parent;

		public Data(Data parent, JsonMap jsonMap) {
			this.parent = parent;
			loadJval(jsonMap);
		}
		public Data(Fi fi) {
			loadFi(fi);
		}

		public Object put(String key, Object value) {
			Object old = super.put(key, value);
			if (!Objects.equals(old, value)) {
				write();
			}
			return old;
		}
		public void write() {
			if (parent == null) config.writeString("" + this);
			else parent.write();
		}

		public Object get(String key, Object defaultValue) {
			return get(key, () -> defaultValue);
		}

		public void loadFi(Fi fi) {
			if (!fi.exists()) {
				fi.writeString("");
				return;
			}
			try {
				loadJval(Jval.read(fi.readString()).asObject());
			} catch (Exception e) {
				Log.err(e);
			}
		}
		public void loadJval(JsonMap jsonMap) {
			for (var entry : jsonMap) {
				super.put(entry.key, entry.value.isObject() ? new Data(this, entry.value.asObject()) :
						entry.value.isBoolean() ? entry.value.asBool() : entry.value);
			}
		}

		public boolean toBool(Object v) {
			if (v instanceof Jval) {
				if (((Jval) v).isBoolean()) return ((Jval) v).asBool();
				return v.toString().equals("true");
			}
			if (v instanceof Boolean) return (Boolean) v;
			if (v == null) return false;
			return ScriptRuntime.toBoolean("" + v);
		}
		public boolean getBool(String name) {
			return toBool(get(name, false));
		}
		public boolean getBool(String name, Object def) {
			return toBool(get(name, def));
		}

		public String toString() {
			return toString(new StringBuilder());
		}
		public String toString(StringBuilder tab) {
			StringBuilder builder = new StringBuilder();
			builder.append("{\n");
			tab.append("	");
			each((k, v) -> {
				builder.append(tab).append(k).append(": ")
						.append(v instanceof Data ? ((Data) v).toString(tab) : v)
						.append("\n");
			});
			tab.deleteCharAt(tab.length() - 1);
			builder.append('\n').append(tab).append('}');
			return builder.toString();
		}

		public float getFloat(String name, float def) {
			Object v = get(name, def);
			if (v instanceof Jval) {
				if (((Jval) v).isNumber()) return ((Jval) v).asFloat();
				v = v.toString();
			}
			return Float.parseFloat("" + v);
		}
		public int getInt(String name, int def) {
			Object v = get(name, def);
			if (v instanceof Jval) {
				if (((Jval) v).isNumber()) return ((Jval) v).asInt();
				v = v.toString();
			}
			return Jval.read("" + v).asInt();
		}
	}
}
