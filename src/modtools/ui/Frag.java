package modtools.ui;

import arc.Core;
import arc.graphics.Color;
import arc.scene.Group;
import arc.scene.ui.Image;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import mindustry.ui.Styles;
import modtools.ui.components.MoveListener;
import modtools.ui.content.Content;

import static modtools.IntVars.modName;

public class Frag extends Table {
	public boolean keepFrag = false, hideCont = false;
	public int baseHeight = 0;
	public Table contTable;
	public Cell<Table> cell;

	public void load() {
		name = modName + "-frag";;
		Image top = image().color(Color.sky).margin(0).pad(0)
				.padBottom(-4).fillX().minWidth(40).height(40).get();
		row();
		new MoveListener(top, this);
		IntUI.doubleClick(top, () -> {}, () -> {
			hideCont = !hideCont;
			cell.setElement(hideCont ? null : contTable);
		});
		Contents.load();
		cell = table().get().table(table -> {
			contTable = table;
			Content.all.forEach(cont -> {
				if (cont == null || !cont.loadable()) return;
				String localizedName = cont.localizedName();
				var style = Styles.flatt;
//				var style = Styles.cleart;
//				Objects.requireNonNull(cont);
				cont.btn = table.button(localizedName, style, cont::build).size(120, 40).get();
				baseHeight += 40;
				cont.load();
				table.row();
			});
		});
		left().bottom();
		Core.scene.add(this);
		Group root = Core.scene.root;
		Log.info(this);

		update(() -> {
			if (!keepFrag || root == null || root.getChildren().peek() == this) return;
			root.removeChild(this);
			root.addChild(this);
		});
	}
}
