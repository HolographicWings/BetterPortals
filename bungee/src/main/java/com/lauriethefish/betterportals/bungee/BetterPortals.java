package com.lauriethefish.betterportals.bungee;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.lauriethefish.betterportals.proxy.net.IPortalServer;
import com.lauriethefish.betterportals.proxy.net.PortalServer;
import com.lauriethefish.betterportals.shared.logging.Logger;
import com.lauriethefish.betterportals.shared.net.DisconnectNotice;
import net.md_5.bungee.api.plugin.Plugin;

public class BetterPortals extends Plugin {
    @Inject private Logger logger;
    @Inject private Config config;
    private IPortalServer portalServer;
    private boolean didEnableFail = false;

    @Override
    public void onEnable() {

        Injector injector = Guice.createInjector(new MainModule(this));
        DisconnectNotice forceClassLoad = new DisconnectNotice();
        logger.finest(forceClassLoad.toString());

        try {
            config.load();
        }   catch(Exception ex) {
            logger.severe("Failed to load the config file");
            logger.severe("Please check that your YAML syntax is correct");
            ex.printStackTrace();
            didEnableFail = true;
            return;
        }

        portalServer = injector.getInstance(PortalServer.class);
        injector.getInstance(ServerSwitch.class);
        portalServer.startUp();
    }

    @Override
    public void onDisable() {
        if(didEnableFail) {return;}

        portalServer.shutDown();
    }
}
