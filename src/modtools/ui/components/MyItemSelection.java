
package modtools.ui.components;

import arc.func.Cons;
import arc.func.Prov;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import modtools.IntVars;
import modtools.ui.IntStyles;

public class MyItemSelection {
	public MyItemSelection() {
	}

	public static <T extends UnlockableContent> void buildTable(Table table, Seq<T> items, Prov<T> holder, Cons<T> consumer) {
		buildTable(table, items, holder, consumer, 4);
	}

	public static <T extends UnlockableContent> void buildTable(Table table, Seq<T> items, Prov<T> holder, Cons<T> consumer, int cols) {
		IntVars.async(() -> buildTable0(table, items, holder, consumer, cols), () -> {});
	}

	private static <T extends UnlockableContent> void buildTable0(Table table, Seq<T> items, Prov<T> holder, Cons<T> consumer, int cols) {
		ButtonGroup<ImageButton> group = new ButtonGroup<>();
		group.setMinCheckCount(0);
		Table cont = new Table();
		cont.defaults().size(40);
		int i = 0;

		for (T item : items) {
			if (item == null) continue;
			try {
				ImageButton button = cont.button(Tex.whiteui, /*Styles.clearNoneTogglei*/Styles.clearTogglei, 24, () -> {
				}).group(group).get();
				button.changed(() -> {
					consumer.get(button.isChecked() ? item : null);
				});
				button.getStyle().imageUp = new TextureRegionDrawable(item.uiIcon);
				if (item == holder.get()) button.setChecked(true);
			/*button.update(() -> {
				button.setChecked(holder.get() == item);
			});*/

			} catch (Exception ignored) {}
			if (++i % cols == 0) {
				cont.row();
			}
		}

		ScrollPane pane = new ScrollPane(cont, IntStyles.noBarPane);
		pane.setScrollingDisabled(true, false);
		pane.setOverscroll(false, false);
		table.add(pane).maxHeight(40 * 5).grow();
	}
}
