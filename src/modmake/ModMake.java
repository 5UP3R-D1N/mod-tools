
package modmake;

import arc.Core;
import arc.Events;
import arc.util.Log;
import arc.util.Time;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.BaseDialog;
import modmake.ui.Background;

public class ModMake extends Mod {

	public ModMake() {
		Log.info("Loaded ModMake constructor.");
		// listen for game load event
		Events.on(ClientLoadEvent.class, e -> {
			// show dialog upon startup
			Time.runTask(10f, () -> {
				BaseDialog dialog = new BaseDialog("frog");
				dialog.cont.add("behold").row();
				/*
				 * mod sprites are prefixed with the mod name (this mod is called
				 * 'example-java-mod' in its config)
				 */
				dialog.cont.image(Core.atlas.find(IntVars.modName + "-frog")).pad(20f).row();
				IntVars.load();
				dialog.addCloseListener();
				dialog.cont.button("I see", dialog::hide).size(100f, 50f);
				dialog.show();
			});

			Background.main();
		});
	}

}
