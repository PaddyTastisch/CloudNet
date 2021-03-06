/*
 * Copyright (c) Tarek Hosni El Alaoui 2017
 */

package de.dytanic.cloudnet.bridge.event.proxied;

import de.dytanic.cloudnet.lib.server.info.ServerInfo;
import lombok.AllArgsConstructor;
import net.md_5.bungee.api.plugin.Event;

/**
 * Calls if one server was removed from the network
 */
@AllArgsConstructor
public class ProxiedServerRemoveEvent extends ProxiedCloudEvent {

    private ServerInfo serverInfo;

    public ServerInfo getServerInfo()
    {
        return serverInfo;
    }
}