package mattymatty.redstone;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class BetterRedstoneWire implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("matty-redstone");

    @Override
    public void onInitialize() {
        RedstoneWireBlockEntity.init();
        BetterRedstoneWire.LOGGER.info("Matty's Better Redstone Loaded!");
    }
}
