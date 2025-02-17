package modtools.ui.components;

import arc.*;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.input.KeyCode;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.actions.Actions;
import arc.scene.event.*;
import arc.scene.style.Drawable;
import arc.scene.ui.*;
import arc.scene.ui.ImageButton.ImageButtonStyle;
import arc.scene.ui.layout.*;
import arc.struct.ObjectSet;
import arc.util.*;
import arc.util.Timer.Task;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.gen.*;
import mindustry.ui.Styles;
import modtools.IntVars;
import modtools.ui.*;
import modtools.ui.components.linstener.*;
import modtools.ui.effect.MyDraw;
import modtools.utils.*;

import static arc.Core.graphics;
import static modtools.ui.Contents.windowManager;
import static modtools.ui.IntUI.*;

/**
 * 浮动的窗口，可以缩放，最小化，最大化
 *
 * @author I hope...
 **/
public class Window extends Table {
	public static final MySet<Window> all = new MySet<>() {
		public boolean add(Window value) {
			boolean b = super.add(value);
			if (windowManager != null && windowManager.ui != null && windowManager.ui.isShown())
				windowManager.rebuild();
			return b;
		}

		public boolean remove(Window value) {
			boolean ok = super.remove(value);
			if (windowManager != null && windowManager.ui != null && windowManager.ui.isShown())
				windowManager.rebuild();
			return ok;
		}
	};

	public static Window focusWindow;

	static {
		IntVars.addResizeListener(() -> {
			all.each(Window::display);
		});

		Events.run(Trigger.update, () -> {
			var children = topGroup.shownWindows;
			focusWindow = children.isEmpty() ? null : children.get(children.size() - 1);
			/*while (focus != null) {
				if (focus instanceof Window) {
					focusWindow = (Window) focus;
					break;
				}
				focus = focus.parent;
			}*/
		});
	}

	public static       Drawable myPane    = Tex.pane;
	public static final Cell     emptyCell = new Cell<>();

	public Table
			top     = new Table(myPane) {
		public Cell<ImageButton> button(Drawable icon, ImageButtonStyle style, float isize, Runnable listener) {
			var cell = super.button(icon, style, isize, listener);
			cell.get().tapped(() -> {
				moveListener.disabled = true;
				Time.runTask(0, () -> moveListener.disabled = false);
			});
			return cell;
		}
	},
			cont    = new Table(myPane),
			buttons = new Table(myPane);
	public float minWidth, minHeight;
	// 用于最小化时的最小宽度
	private static final float topHeight = 45;

	public Label title;

	public boolean
			// 是否置顶
			sticky = false,
			full, noButtons;
	public MoveListener       moveListener;
	public ObjectSet<Element> fireMoveElems = ObjectSet.with(this, top);
	public SclLisetener       sclLisetener;

	public Window(String title, float minWidth, float minHeight, boolean full, boolean noButtons) {
		super(Styles.none);

		cont.setClip(true);
		tapped(this::toFront);
		touchable = top.touchable/* = cont.touchable */ = Touchable.enabled;
		top.margin(0);
		if (OS.isWindows) IntUI.doubleClick(top, () -> {}, this::toggleMaximize);
		cont.margin(8f);
		buttons.margin(0);
		this.minHeight = minHeight;
		this.full = full;
		this.noButtons = noButtons;

		left().defaults().left();

		// top.fillParent = true;
		// top.top();
		add(top).growX().height(topHeight);
		row();
		moveListener = new MoveListener(top, this);
		this.title = top.add(title).grow().touchable(Touchable.disabled)
				.padLeft(10f).padRight(10f)
				.update(l -> {
					// var children = Core.scene.root.getChildren();
					// l.setColor(children.peek() == this || (children.size >= 2 && children.get(children.size - 2) == this) ? Color.white : Color.lightGray);
					l.setColor(focusWindow == this ? Color.white : Color.lightGray);
				}).get();
		if (full) {
			top.button(icons.get("sticky"), IntStyles.clearNoneTogglei, 32, () -> {
				sticky = !sticky;
			}).checked(b -> sticky).padLeft(4f).name("sticky");
			top.button(Icon.down, IntStyles.clearNoneTogglei, 32, this::toggleMinimize).update(b -> {
				b.setChecked(isMinimize);
			}).padLeft(4f);
			ImageButton button = top.button(Tex.whiteui, IntStyles.clearNonei, 32, this::toggleMaximize).disabled(b -> !isShown()).padLeft(4f).get();
			button.update(() -> {
				button.getStyle().imageUp = isMaximize ? icons.get("normal") : icons.get("maximize");
			});
		}
		top.button(Icon.cancel, IntStyles.clearNonei, 32, this::hide).padLeft(4f).padRight(4f);
		//		cont.defaults().height(winHeight);
		setup();

		sclLisetener = new SclLisetener(this, this.minWidth, minHeight);
		moveListener.fire = () -> {
			if (isMaximize && !isMinimize) {
				float mulxw = moveListener.lastMouse.x / width;
				float mulxh = moveListener.lastMouse.y / height;
				toggleMaximize();
				// 修复移动侦听器的位置
				RunListener listener = new RunListener() {
					public void fire(boolean status) {
						moveListener.lastMain.x = x;
						moveListener.lastMain.y = y;
						moveListener.lastMouse.x = x + width * mulxw;
						moveListener.lastMouse.y = y + height * mulxh;
						maxlisteners.remove(this);
					}
				};
				maximized(listener);
				// moveListener.bx = width * mulx;
				// moveListener.by = Core.scene.getHeight() - moveListener.by;
			}
		};
		keyDown(k -> {
			if (k == KeyCode.f4 && Core.input.ctrl()) {
				hide();
			}
		});

		Time.runTask(1, () -> {
			// 默认最小宽度为pref宽度
			this.minWidth = Math.max(minWidth, getMinWidth());
			sclLisetener.set(this.minWidth, minHeight);
		});
		super.update(() -> {
			for (Runnable r : runs) r.run();
			if (sticky) toFront();
			sclLisetener.disabled0 = isMaximize;
			moveListener.disabled = sclLisetener.scling;
		});

		all.add(this);
	}

	ObjectSet<Runnable> runs = new ObjectSet<>();
	public Element update(Runnable r) {
		runs.add(r);
		return this;
	}

	public Window(String title, float width, float height, boolean full) {
		this(title, width, height, full, true);
	}

	public Window(String title, float width, float height) {
		this(title, width, height, false);
	}

	public Window(String title) {
		this(title, 120, 80, false);
	}

	/** 用于当hit元素不是想要的，取消MoveListener */
	public Element hit(float x, float y, boolean touchable) {
		Element element = super.hit(x, y, touchable);
		if (!moveListener.isFiring) moveListener.disabled = element == null || !fireMoveElems.contains(element);
		/*if (element instanceof ScrollPane) {
			boolean f = !moveListener.disabled;
			((ScrollPane) element).setScrollingDisabled(f, f);
		}*/
		return element;
	}
	public void setup() {
		add(cont).grow().row();
		if (!noButtons) add(buttons).growX().row();
	}

	public void noButtons(boolean val) {
		if (noButtons == val) return;
		noButtons = val;
		if (noButtons) {
			buttons.remove();
			//			buttons.clear();
			//			buttons = null;
		} else {
			if (buttons.parent != null) {
				buttons.remove();
			}
			add(buttons).growX().row();
		}
	}

	public void display() {
		float mainWidth  = getWidth(), mainHeight = getHeight();
		float touchWidth = top.getWidth(), touchHeight = top.getHeight();
		super.setPosition(Mathf.clamp(x, -touchWidth / 3f, graphics.getWidth() - mainWidth + touchWidth / 2f),
				Mathf.clamp(y, -mainHeight + touchHeight / 3f * 2f, graphics.getHeight() - mainHeight));
		if (lastMaximize) {
			// false取反为true
			isMaximize = false;
			toggleMaximize();
		}
	}


	public static boolean Modible_Disabled = true;
	// public TextureRegion bakRegion;
	/** 截图 */
	public TextureRegion screenshot() {
		// return getFrameBufferTexture((int) x, (int) y, (int) width, (int) height);
		// return new TextureRegion(bufferCapture(this));
		if (Vars.mobile && Modible_Disabled) return null;
		return cache = isMinimize ? cache : Tools.screenshot(cont, true, null);
	}

	public float getPrefWidth() {
		// 默认最小宽度为顶部的最小宽度
		return Mathf.clamp(super.getPrefWidth(), minWidth, graphics.getWidth());
	}

	public float getPrefHeight() {
		return Mathf.clamp(super.getPrefHeight(), minHeight, graphics.getHeight());
	}

	private static final Prov<Action>
			defaultShowAction = () -> Actions.sequence(Actions.alpha(0), Actions.fadeIn(0.2f, Interp.fade)),
			defaultHideAction = () -> Actions.fadeOut(0.2f, Interp.fade);
	protected InputListener ignoreTouchDown = new InputListener() {
		public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
			event.cancel();
			return false;
		}
	};
	Element previousKeyboardFocus, previousScrollFocus;
	FocusListener focusListener = new FocusListener() {
		public void keyboardFocusChanged(FocusEvent event, Element actor, boolean focused) {
			if (!focused) focusChanged(event);
		}

		public void scrollFocusChanged(FocusEvent event, Element actor, boolean focused) {
			if (!focused) focusChanged(event);
		}

		private void focusChanged(FocusEvent event) {
			Scene stage = getScene();
			if (stage != null && stage.root.getChildren().size > 0
			    && stage.root.getChildren().peek() == Window.this) { // Dialog is top most actor.
				Element newFocusedActor = event.relatedActor;
				if (newFocusedActor != null && !newFocusedActor.isDescendantOf(Window.this) &&
				    !(newFocusedActor.equals(previousKeyboardFocus) || newFocusedActor.equals(previousScrollFocus)))
					event.cancel();
			}
		}
	};

	public void clear() {
		super.clear();
		all.remove(this);
	}

	@Override
	protected void setScene(Scene stage) {
		if (stage == null) {
			addListener(focusListener);
		} else {
			removeListener(focusListener);
		}
		super.setScene(stage);
	}

	public boolean isShown() {
		return getScene() != null;
	}

	/**
	 * {@link #pack() Packs} the dialog and adds it to the stage with custom action which can be null for instant show
	 */
	public Window show(Scene stage, Action action) {
		setOrigin(Align.center);
		setClip(false);
		setTransform(true);

		this.fire(new VisibilityEvent(false));

		clearActions();
		removeCaptureListener(ignoreTouchDown);

		previousKeyboardFocus = null;
		Element actor = stage.getKeyboardFocus();
		if (actor != null && !actor.isDescendantOf(this)) previousKeyboardFocus = actor;

		previousScrollFocus = null;
		actor = stage.getScrollFocus();
		if (actor != null && !actor.isDescendantOf(this)) previousScrollFocus = actor;

		pack();
		topGroup.addChild(this);
		stage.setKeyboardFocus(this);
		stage.setScrollFocus(this);

		if (action != null) addAction(action);
		pack();

		if (!Window.all.contains(this)) {
			Window.all.add(this);
		}

		Core.scene.unfocusAll();
		invalidate();

		return this;
	}

	/**
	 * Shows this dialog if it was hidden, and vice versa.
	 */
	public void toggle() {
		if (isShown()) {
			hide();
		} else {
			show();
		}
	}

	public Window show() {
		display();
		if (isShown()) {
			setZIndex(Integer.MAX_VALUE);
			if (isMinimize) toggleMinimize();
			return this;
		}
		return show(Core.scene);
	}

	/**
	 * {@link #pack() Packs} the dialog and adds it to the stage, centered with default fadeIn action
	 */
	public Window show(Scene stage) {
		show(stage, defaultShowAction.get());
		return this;
	}

	/**
	 * Hides the dialog with the given action and then removes it from the stage.
	 */
	public void hide(Action action) {
		// bakRegion = screenshot();
		this.fire(new VisibilityEvent(true));

		Scene stage = getScene();
		if (stage != null) {
			removeListener(focusListener);
			if (previousKeyboardFocus != null && previousKeyboardFocus.getScene() == null) previousKeyboardFocus = null;
			Element actor = stage.getKeyboardFocus();
			if (actor == null || actor.isDescendantOf(this)) stage.setKeyboardFocus(previousKeyboardFocus);

			if (previousScrollFocus != null && previousScrollFocus.getScene() == null) previousScrollFocus = null;
			actor = stage.getScrollFocus();
			if (actor == null || actor.isDescendantOf(this)) stage.setScrollFocus(previousScrollFocus);
		}
		if (action != null) {
			addCaptureListener(ignoreTouchDown);
			addAction(Actions.sequence(action, Actions.removeListener(ignoreTouchDown, true), Actions.remove()));
		} else
			remove();

		if (this instanceof DisposableInterface) {
			all.remove(this);
		}
	}

	public TextureRegion cache = null;
	/**
	 * Hides the dialog. Called automatically when a button is clicked. The default implementation fades out the dialog over 400
	 * milliseconds.
	 */
	public void hide() {
		if (!isShown()) return;
		screenshot();
		setOrigin(Align.center);
		setClip(false);
		setTransform(true);

		hide(defaultHideAction.get());
	}

	// 用于存储最小/大化前的位置和大小
	public  Rect    lastRect        = new Rect();
	private boolean disabledActions = true;


	public ObjectSet<RunListener> maxlisteners = new ObjectSet<>();
	public boolean                isMaximize   = false, lastMaximize = false;

	/**
	 * Adds a toggleMaximize() listener.
	 */
	public void maximized(RunListener run) {
		maxlisteners.add(run);
	}

	public void toggleMaximize() {
		boolean lastMin = isMinimize;
		if (isMinimize) {
			boolean l = disabledActions;
			disabledActions = true;
			toggleMinimize();
			disabledActions = l;
		}
		isMaximize = !isMaximize;
		if (isMaximize) {
			if (!lastMin) {
				lastRect.set(x, y, width, height);
			}
			actions(Actions.sizeTo(graphics.getWidth(), graphics.getHeight(), disabledActions ? 0 : 0.06f),
					Actions.moveTo(0, 0, disabledActions ? 0 : 0.01f));
			// setSize(Core.graphics.getWidth(), Core.graphics.getHeight());
			// setPosition(0, 0);
		} else {
			actions(Actions.sizeTo(lastRect.width, lastRect.height, disabledActions ? 0 : 0.06f),
					Actions.moveTo(lastRect.x, lastRect.y, disabledActions ? 0 : 0.01f));
		}

		Timer.schedule(new Task() {
			@Override
			public void run() {
				if (!hasActions()) {
					maxlisteners.each(r -> r.fire(isMaximize));
					lastMaximize = isMaximize;
					cancel();
				}
			}
		}, 0, 0.01f, -1);
		// Time.runTask(0, () -> cont.invalidate());
	}


	public ObjectSet<RunListener> minlisteners = new ObjectSet<>();
	public boolean                isMinimize   = false;

	public void toggleMinimize() {
		if (!isMinimize) screenshot();
		isMinimize = !isMinimize;
		if (isMinimize) {
			if (!isMaximize) {
				lastRect.setSize(getWidth(), getHeight());
			}

			actions(Actions.sizeTo(getMinWidth(), topHeight, disabledActions ? 0 : 0.01f),
					Actions.moveTo(Math.max(x, lastRect.width / 2f),
							y + lastRect.height - topHeight, disabledActions ? 0 : 0.01f));

			getCell(cont).set(emptyCell);
			cont.remove();
			if (!noButtons) {
				try {
					getCell(buttons).set(emptyCell);
					buttons.remove();
				} catch (Throwable ignored) {}
			}
			removeListener(sclLisetener);
			sizeChanged();
		} else {
			setup();

			left().top();
			if (isMaximize) {
				actions(Actions.sizeTo(graphics.getWidth(), graphics.getHeight(), disabledActions ? 0 : 0.1f),
						Actions.moveTo(0, 0, disabledActions ? 0 : 0.01f));
			} else {
				actions(Actions.sizeTo(lastRect.width, lastRect.height, disabledActions ? 0 : 0.01f),
						Actions.moveTo(lastRect.x = x,
								lastRect.y = y - lastRect.height + topHeight,
								disabledActions ? 0 : 0.01f));
				// y -= height - topHeight;
			}
			addListener(sclLisetener);
		}
		Timer.schedule(new Task() {
			@Override
			public void run() {
				if (!hasActions()) {
					if (!isMaximize) display();
					minlisteners.each(r -> r.fire(isMinimize));
					cancel();
				}
			}
		}, 0, 0.01f, -1);
	}

	/**
	 * Adds a toggleMinimize() listener.
	 */
	public void minimized(RunListener run) {
		minlisteners.add(run);
	}

	/**
	 * Adds a show() listener.
	 */
	public void shown(Runnable run) {
		addListener(new VisibilityListener() {
			@Override
			public boolean shown() {
				run.run();
				return false;
			}
		});
	}

	/**
	 * Adds a hide() listener.
	 */
	public void hidden(Runnable run) {
		addListener(new VisibilityListener() {
			@Override
			public boolean hidden() {
				run.run();
				return false;
			}
		});
	}

	public void setPosition(Position pos) {
		setPosition(pos.getX(), pos.getY());
		display();
	}

	public void draw() {
		MyDraw.blur(() -> {
			Fill.crect(x, y, width, height);
		});
		super.draw();
	}
	public interface RunListener {
		void fire(boolean status);
	}

	/** just a flag */
	public interface DisposableInterface {}

	public static class DisWindow extends Window implements DisposableInterface {
		public DisWindow(String title, float minWidth, float minHeight, boolean full, boolean noButtons) {
			super(title, minWidth, minHeight, full, noButtons);
		}
		public DisWindow(String title, float width, float height, boolean full) {
			super(title, width, height, full);
		}
		public DisWindow(String title, float width, float height) {
			super(title, width, height);
		}
		public DisWindow(String title) {
			super(title);
		}
	}

	public static class NoTopWindow extends Window {

		public NoTopWindow(String title, float minWidth, float minHeight, boolean full, boolean noButtons) {
			super(title, minWidth, minHeight, full, noButtons);
		}
		public NoTopWindow(String title, float width, float height, boolean full) {
			super(title, width, height, full);
		}
		public NoTopWindow(String title, float width, float height) {
			super(title, width, height);
		}
		public NoTopWindow(String title) {
			super(title);
		}

		{
			moveListener.remove();
			moveListener = new MoveListener(this, this);
			getCells().remove(getCell(top), true);
			top.remove();
			Table table = new Table();
			table.setFillParent(true);
			addChild(table);
			table.top().add(top).growX().height(topHeight).padTop(-topHeight);
			// table.translation.y = topHeight;
			table.setClip(true);
			// top.translation.y = -topHeight;

			/*addListener(new InputListener() {
				public void exit(InputEvent event, float x, float y, int pointer, Element toActor) {
					top.translation.y = 0;
				}
			});*/
		}

		@Override
		public Element hit(float x, float y, boolean touchable) {
			Element element = super.hit(x, y, touchable);
			float   toValue;
			if (height - topHeight <= y && element != null) {
				toValue = -topHeight;
			} else {
				toValue = 0;
			}
			top.translation.y = Mathf.lerp(top.translation.y, toValue, 0.1f);
			return element;
		}
	}
}