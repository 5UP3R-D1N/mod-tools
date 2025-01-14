
package modtools.ui.content.debug;

import arc.*;
import arc.files.Fi;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.event.*;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton.TextButtonStyle;
import arc.scene.ui.layout.*;
import arc.util.*;
import ihope_lib.MyReflect;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.gen.*;
import mindustry.mod.Scripts;
import mindustry.ui.Styles;
import modtools.rhino.ForRhino;
import modtools.ui.*;
import modtools.ui.components.*;
import modtools.ui.components.input.MyLabel;
import modtools.ui.components.input.area.TextAreaTable;
import modtools.ui.components.input.area.TextAreaTable.MyTextArea;
import modtools.ui.components.input.highlight.JSSyntax;
import modtools.ui.components.linstener.SclLisetener;
import modtools.ui.content.Content;
import modtools.ui.windows.NameWindow;
import modtools.utils.*;
import rhino.*;

import java.lang.reflect.*;

import static ihope_lib.MyReflect.unsafe;
import static modtools.ui.components.ListDialog.fileUnfair;
import static modtools.utils.Tools.*;

public class Tester extends Content {
	String     log = "";
	MyTextArea area;
	public boolean loop = false;
	public Object  res;

	public static boolean catchOutsizeError = false;
	private       boolean
	                      wrap              = false,
			error                           = false,
			ignorePopUpError                = false,
			wrapRef                         = true,
			multiWindows                    = false;

	public static final float  w = Core.graphics.isPortrait() ? 440 : 540;
	public              Window ui;
	ListDialog history, bookmark;
	public Scripts    scripts;
	public Scriptable scope;
	public Context    cx;
	public Script     script = null;
	public boolean    stopIfOvertime;

	public Tester() {
		super("tester");
	}

	private static float sort(Fi f) {
		try {
			return -f.lastModified();
			// return -Long.parseLong(f.nameWithoutExtension());
		} catch (Exception e) {
			return Long.MAX_VALUE;
		}
	}

	/**
	 * 用于回滚历史
	 */
	public              int     historyIndex         = -1;
	public static final boolean reincarnationHistory = false;

	public ScrollPane pane;
	public void build(Table table) {
		if (ui == null) _load();

		TextAreaTable textarea = new TextAreaTable("");
		Table cont = new Table() {
			public Element hit(float x, float y, boolean touchable) {
				Element element = super.hit(x, y, touchable);
				if (element == null) return null;
				if (element.isDescendantOf(this)) textarea.focus();
				return element;
			}
		};
		Runnable invalidate = () -> {
			// cont.invalidate();
			textarea.getArea().invalidateHierarchy();
			textarea.layout();
		};
		ui.maximized(isMax -> Time.runTask(0, invalidate));
		ui.sclLisetener.listener = invalidate;

		textarea.syntax = new JSSyntax(textarea);
		// JSSyntax.apply(textarea);
		area = textarea.getArea();
		boolean[] execed = {false};
		textarea.keyDonwB = (event, keycode) -> {
			if (rollAndExec(execed, keycode) || detailsListener(keycode)) return false;
			// Core.input.ctrl() && keycode == KeyCode.rightBracket
			if (keycode == KeyCode.tab) {
				area.insert("  ");
				area.setCursorPosition(area.getCursorPosition() + 2);
				area.updateDisplayText();
			}
			return true;
		};
		textarea.keyTypedB = (event, character) -> !execed[0];
		textarea.keyUpB = (event, keycode) -> {
			execed[0] = false;
			return true;
		};

		Cell<?> areaCell = cont.add(textarea).grow().minHeight(100).maxHeight(ui.cont.getHeight());
		areaCell.row();
		cont.update(() -> areaCell.maxHeight(ui.cont.getHeight()));

		cont.table(t -> {
			t.defaults().padRight(8f);
			t.button(Icon.left, area::left);
			t.button("@ok", () -> {
				error = false;
				// area.setText(getMessage().replaceAll("\\r", "\\n"));
				complieAndExec(() -> {});
			}).disabled(__ -> !finished);
			t.button(Icon.right, area::right);
			t.button(Icon.copy, area::copy).padLeft(8f);
			t.button(Icon.paste, () ->
					area.paste(Core.app.getClipboardText(), true)
			).padLeft(8f);
		}).growX().row();
		Cell<?> cell = cont.table(Tex.sliderBack, t -> t.pane(p -> {
			p.add(new MyLabel(() -> log)).style(IntStyles.MOMO_Label).wrap()
					.growX().labelAlign(Align.center, Align.left);
		}).growX()).growX().height(100).with(t -> t.touchable = Touchable.enabled);

		new SclLisetener(cell.get(), 0, 100) {
			public boolean valid() {
				return !left && !right && !bottom && top;
			}
			public void touchDragged(InputEvent event, float x, float y, int pointer) {
				super.touchDragged(event, x, y, pointer);

				cell.height(Mathf.clamp(bind.getHeight(), 100, cont.getHeight() - 150));
				cont.invalidate();
				pane.setScrollingDisabled(false, false);
				// cont.layout();
				// cell[0].height(logTable.getHeight());
				// cont.pack();
			}

			{
				cell.height(Mathf.clamp(bind.getHeight() + 1, 100, cont.getHeight() - 150));
				cont.invalidate();
			}

			public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
				super.touchUp(event, x, y, pointer, button);
				pane.setScrollingDisabled(false, true);
			}
		};
		table.add(cont).grow().maxHeight(Core.graphics.getHeight()).row();
		table.pane(p -> {
			p.button(Icon.star, IntStyles.clearNonei, this::star).size(42).padRight(6f);
			p.defaults().size(100, 60);
			p.button(b -> {
				b.label(() -> loop ? "@tester.loop" : "@tester.notloop");
			}, Styles.defaultb, () -> {
				loop = !loop;
			});
			p.button(b -> {
				b.label(() -> wrap ? "@tester.strict" : "@tester.notstrict");
			}, Styles.defaultb, () -> wrap = !wrap);
			p.button(b -> {
				b.label(() -> textarea.enableHighlighting ? "@tester.highlighting" : "@tester.nothighlighting");
			}, Styles.defaultb, () -> textarea.enableHighlighting = !textarea.enableHighlighting);
			p.button("@details", this::showDetails);

			p.button("@historymessage", history::show);
			p.button("@bookmark", bookmark::show);
			// p.button("@startup", bookmark::show);
			p.check("@stopIfOvertime", stopIfOvertime, b -> stopIfOvertime = b).width(120);
		}).height(60).width(w).growX();

		buildEditTable();
	}
	private void showDetails() {
		if (res instanceof Class) JSFunc.showInfo((Class<?>) res);
		else JSFunc.showInfo(res);
	}
	public boolean detailsListener(KeyCode keycode) {
		if (keycode == KeyCode.d && Core.input.ctrl() && Core.input.shift()) {
			showDetails();
			return true;
		}
		return false;
	}
	private void buildEditTable() {
		var editTable = new Table(Styles.black5, p -> {
			p.fillParent = true;
			Runnable hide = () -> {
				p.remove();
				ui.noButtons(false);
			};
			p.table(Tex.pane, t -> {
				TextButtonStyle style = IntStyles.cleart;
				t.defaults().size(280, 60).left();
				t.row();
				t.button("@schematic.copy.import", Icon.download, style, () -> {
					hide.run();
					area.setText(Core.app.getClipboardText());
				}).marginLeft(12);
				t.row();
				t.button("@schematic.copy", Icon.copy, style, () -> {
					hide.run();
					JSFunc.copyText(getMessage().replaceAll("\r", "\n"));
				}).marginLeft(12);
				t.row();
				t.button("@back", Icon.left, style, hide).marginLeft(12);
			});
		});
		ui.buttons.button("@edit", Icon.edit, () -> {
			ui.cont.addChild(editTable);
			editTable.setPosition(0, 0);
			ui.noButtons(true);
		}).size(210, 64);
	}
	private void star() {
		new NameWindow(res -> {
			Fi fi = bookmark.file.child(res);
			bookmark.list.insert(0, fi);
			fi.writeString(getMessage());
			bookmark.build();
		}, t -> {
			try {
				return !t.isBlank() && !fileUnfair.matcher(t).find()
				       && !bookmark.file.child(t).exists();
			} catch (Throwable e) {
				return false;
			}
		}, "").show();
	}
	private boolean rollAndExec(boolean[] execed, KeyCode keycode) {
		if (Core.input.ctrl() && Core.input.shift()) {
			if (keycode == KeyCode.enter) {
				complieAndExec(() -> {});
				execed[0] = true;
			} else if (keycode == KeyCode.up) {
				int i = ++historyIndex;
				if (!reincarnationHistory && historyIndex >= history.list.size) {
					historyIndex = history.list.size - 1;
					return true;
				}
				if (i >= history.list.size) {
					if (reincarnationHistory) {
						i -= history.list.size;
						historyIndex -= history.list.size;
					}
				}

				Fi dir = history.list.get(i);
				area.setText(dir.child("message.txt").readString());
				log = dir.child("log.txt").readString();
			} else if (keycode == KeyCode.down) {
				int i = --historyIndex;
				if (!reincarnationHistory && historyIndex < 0) {
					historyIndex = 0;
					return true;
				}
				if (i < 0) {
					if (reincarnationHistory) {
						i += history.list.size;
						historyIndex += history.list.size;
					}
				}
				Fi dir = history.list.get(i);
				area.setText(dir.child("message.txt").readString());
				log = dir.child("log.txt").readString();
			} else return false;
			return true;
		}
		return false;
	}
	public void build() {
		if (ui == null) _load();
		if (multiWindows) {
			var newTester = new Tester();
			newTester.load();
			newTester.build();
		} else ui.show();
		//		ui.show();
		/*if (ui.isShown()) {
			ui.setZIndex(Integer.MAX_VALUE);
		} else ui.show();*/
	}
	void setup() {
		//		ui.cont.clear();
		//		ui.buttons.clear();
		ui.cont.pane(this::build).grow().update(pane -> {
			this.pane = pane;
			pane.setOverscroll(false, false);
		});
	}

	boolean finished = true;
	public long lastTime = Long.MAX_VALUE;

	/*static Method nativeInterrupt;

	static {
		if (OS.isAndroid) try {
			Method method = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
			method.setAccessible(true);
			nativeInterrupt = (Method) method.invoke(Thread.class, "parkFor$", new Class[]{long.class});
			nativeInterrupt.setAccessible(true);
		} catch (Throwable thr) {
			Log.err(thr);
		}
	}

	Thread currentThread;
	Task task = Timer.schedule(() -> {
		if (stopIfOvertime && Time.millis() - lastTime >= 2_000) {
			// currentThread.interrupt();p
			lastTime = Long.MAX_VALUE;
			if (OS.isAndroid) {
				try {
					nativeInterrupt.invoke(currentThread, Long.MAX_VALUE);
				} catch (Throwable e) {
					Log.err(e);
				}
			} else {
				currentThread.stop();
			}
		}
	}, 0, 0.1f, -1);*/
	public void complieAndExec(Runnable callback) {
		if (Context.getCurrentContext() == null) Context.enter();
		lastTime = Time.millis();
		Time.runTask(0, () -> {
			complieScript();
			execScript();

			historyIndex = 0;
			// 保存历史记录
			Fi d = history.file.child(String.valueOf(Time.millis()));
			d.child("message.txt").writeString(getMessage());
			d.child("log.txt").writeString(log);
			history.list.insert(0, d);
			if (history.isShown()) {
				history.build();
			}
			//	history.build(d).with(b -> b.setZIndex(0));

			int max = history.list.size - 1;
			int min = 30;
			for (int i = max; i >= min; i--) {
				history.list.get(i).deleteDirectory();
				history.list.remove(i);
			}
			callback.run();
		});
		/*Object helper = VMBridge.getThreadContextHelper();
		new Thread(() -> {
			while (true) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
				if (finished || Time.millis() - time >= 1_000) {
					Log.info("break");
					VMBridge.setContext(helper, null);
					Method m = null;
					try {
						m = ContextFactory.class.getDeclaredMethod("onContextReleased", Context.class);
						m.setAccessible(true);
						m.invoke(cx.getFactory(), cx);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
					break;
				}
			}
		}).start();*/
	}
	public void complieScript() {
		error = false;
		String def    = getMessage();
		String source = wrap ? "(function(){\"use strict\";" + def + "\n})();" : def;
		try {
			script = cx.compileString(source, "console.js", 1);
		} catch (Throwable ex) {
			makeError(ex);
		}
	}

	public void makeError(Throwable ex) {
		error = true;
		loop = false;
		if (MySettings.SETTINGS.getBool("outputToLog")) Log.err("tester", ex);
		if (!ignorePopUpError) IntUI.showException(Core.bundle.get("error_in_execution"), ex);
		log = Strings.neatError(ex);
	}


	// 执行脚本
	public void execScript() {
		if (!finished) return;
		finished = false;
		if (error) {
			finished = true;
			return;
		}
		/*V8 runtime = V8.createV8Runtime();
			Log.debug(runtime.executeIntegerScript("let x=1;x*2"));*/
		if (Context.getCurrentContext() != cx) {
			cx = Context.getCurrentContext();
		}
		// currentThread = new Thread(() -> {
		try {
			Object o = script.exec(cx, scope);
			res = o = JSFunc.unwrap(o);

			log = String.valueOf(o);
			if (log == null) log = "null";
			if (MySettings.SETTINGS.getBool("outputToLog")) Log.info("tester: " + log);

			// log = log.replaceAll("\\[(\\w*?)]", "[[$1]");
			finished = true;
			lastTime = Long.MAX_VALUE;
			// });
			// currentThread.setPriority(1);
			// currentThread.setUncaughtExceptionHandler((__, e) -> {
		} catch (Throwable e) {
			handleError(e);
		}
		// });
		// currentThread.start();
	}
	public void handleError(Throwable ex) {
		makeError(ex);
		finished = true;
		lastTime = Long.MAX_VALUE;
	}

	public void _load() {
		ui = new Window(localizedName(), w, 400, true, false);
		// JSFunc.watch("times", () -> ui.times);
		/*ui.update(() -> {
			ui.setZIndex(frag.getZIndex() - 1);
		});*/
		//		ui.addCloseListener();
		history = new ListDialog("history", MySettings.dataDirectory.child("historical record"),
				f -> f.child("message.txt"), f -> {
			area.setText(f.child("message.txt").readString());
			log = f.child("log.txt").readString();
		}, (f, p) -> {
			p.add(new MyLabel(f.child("message.txt").readString())).row();
			p.image().color(JSFunc.underline).growX().padTop(6f).padBottom(6f).row();
			p.add(new MyLabel(f.child("log.txt").readString())).row();
		}, Tester::sort);
		history.hide();
		bookmark = new ListDialog("bookmark", MySettings.dataDirectory.child("bookmarks"),
				f -> f, f -> area.setText(f.readString()),
				(f, p) -> {
					p.add(new MyLabel(f.readString())).row();
				}, Tester::sort);
		bookmark.hide();

		setup();

		Events.run(Trigger.update, () -> {
			//			Log.info("update");
			if (loop && script != null) {
				execScript();
			}
		});
	}
	public void load() {
		if (!init) loadSettings();
		try {
			Class.forName("modtools.rhino.ForRhino", true, Tester.class.getClassLoader());
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		init = true;

		scripts = Vars.mods.getScripts();
		if (Context.getCurrentContext() == null) Context.enter();
		cx = scripts.context;
		scope = scripts.scope;

		try {
			Object obj1 = new NativeJavaClass(scope, JSFunc.class, true);
			ScriptableObject.putProperty(scope, "IntFunc", obj1);
			Object obj2 = new NativeJavaClass(scope, MyReflect.class, false);
			ScriptableObject.putProperty(scope, "MyReflect", obj2);
			ScriptableObject.putProperty(scope, "unsafe", unsafe);
			Field f = Context.class.getDeclaredField("factory");
			f.setAccessible(true);
			f.set(cx, ForRhino.factory);
			cx.setGenerateObserverCount(true);
			// cx.setInstructionObserverThreshold(0);
			// ScriptableObject.putProperty(scope, "Window", new NativeJavaClass(scope, Window.class, true));
		} catch (Exception ex) {
			if (ignorePopUpError) {
				Log.err(ex);
			} else {
				Vars.ui.showException("IntFunc出错", ex);
			}
		}

		// 启动脚本
		Fi dir = MySettings.dataDirectory.child("startup");
		if (dir.exists() && dir.isDirectory()) {
			dir.walk(f -> {
				if (!f.extEquals("js")) return;
				try {
					cx.compileString(f.readString(), f.name(), 1).exec(cx, scope);
				} catch (Throwable e) {
					Log.err(e);
				}
			});
		} else {
			Log.info("Loaded startup directory.");
			dir.delete();
			dir.child("README.txt").writeString("这是一个用于启动脚本（js）的文件夹\n\n所有的js文件都会执行");
		}
	}

	public static boolean init = false;
	public void loadSettings() {
		Table table = new Table();
		table.defaults().growX();
		table.table(t -> {
			t.left().defaults().left();
			t.check("@settings.ignorePopUpError", ignorePopUpError = MySettings.SETTINGS.getBool("ignorePopUpError", ignorePopUpError), b -> {
				MySettings.SETTINGS.put("ignorePopUpError", ignorePopUpError = b);
			}).row();
			t.check("@settings.catchOutsizeError", catchOutsizeError = MySettings.SETTINGS.getBool("catchOutsizeError", catchOutsizeError), b -> {
				MySettings.SETTINGS.put("catchOutsizeError", catchOutsizeError = b);
			}).row();
			t.check("@settings.wrapRef", wrapRef, b -> wrapRef = b);
		}).row();
		table.table(t -> {
			t.left().defaults().left();
			t.check("@settings.multiWindows", multiWindows, b -> multiWindows = b).row();
			t.check("@settings.outputToLog", MySettings.SETTINGS.getBool("outputToLog"), b -> {
				MySettings.SETTINGS.put("outputToLog", b);
			});
		});

		Contents.settingsUI.add(localizedName(), table);
	}

	public String getMessage() {
		return area.getText();
	}
	public Object getWrap(Object val) {
		try {
			if (val instanceof Class) return new NativeJavaClass(scope, (Class<?>) val);
			if (val instanceof Method) return new NativeJavaMethod((Method) val, ((Method) val).getName());
			return Context.javaToJS(val, scope);
		} catch (Throwable e) {
			return val;
		}
	}

	public void put(String name, Object val) {
		if (wrapRef) {
			val = getWrap(val);
			//			else if (val instanceof Field) val = new NativeJavaObject(scope, val, Field.class);
		}
		ScriptableObject.putProperty(scope, name, val);
	}
	public final String prefix = "tmp";
	public String put(Object val) {
		int i = 0;
		// 从0开始直到找到没有被定义的变量
		while (ScriptableObject.hasProperty(scope, prefix + i)) i++;
		String key = prefix + i;
		put(key, val);
		return key;
	}
	public void put(Element element, Object val) {
		put(getAbsPos(element), val);
	}
	public void put(Vec2 vec2, Object val) {
		IntUI.showInfoFade(Core.bundle.format("jsfunc.saved", put(val)))
				.setPosition(vec2);
	}

}