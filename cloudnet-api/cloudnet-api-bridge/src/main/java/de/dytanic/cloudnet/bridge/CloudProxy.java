/*
 * Copyright (c) Tarek Hosni El Alaoui 2017
 */

package de.dytanic.cloudnet.bridge;

import com.google.gson.reflect.TypeToken;
import de.dytanic.cloudnet.api.CloudAPI;
import de.dytanic.cloudnet.api.ICloudService;
import de.dytanic.cloudnet.api.handlers.NetworkHandler;
import de.dytanic.cloudnet.bridge.event.proxied.*;
import de.dytanic.cloudnet.lib.CloudNetwork;
import de.dytanic.cloudnet.lib.NetworkUtils;
import de.dytanic.cloudnet.lib.MultiValue;
import de.dytanic.cloudnet.lib.player.CloudPlayer;
import de.dytanic.cloudnet.lib.player.permission.GroupEntityData;
import de.dytanic.cloudnet.lib.player.permission.PermissionGroup;
import de.dytanic.cloudnet.lib.proxylayout.ServerFallback;
import de.dytanic.cloudnet.lib.server.ProxyGroup;
import de.dytanic.cloudnet.lib.server.ProxyProcessMeta;
import de.dytanic.cloudnet.lib.server.info.ProxyInfo;
import de.dytanic.cloudnet.lib.server.info.ServerInfo;
import de.dytanic.cloudnet.lib.utility.Acceptable;
import de.dytanic.cloudnet.lib.utility.CollectionWrapper;
import de.dytanic.cloudnet.lib.utility.Catcher;
import de.dytanic.cloudnet.lib.utility.MapWrapper;
import de.dytanic.cloudnet.lib.utility.document.Document;
import de.dytanic.cloudnet.lib.utility.threading.Runnabled;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * This Class represents the Proxy Instance on based on cloudnet
 */
public class CloudProxy implements ICloudService {

    private static CloudProxy instance;

    private ProxiedBootstrap proxiedBootstrap;
    private ProxyProcessMeta proxyProcessMeta;

    private Map<String, ServerInfo> cachedServers = new ConcurrentHashMap<>();
    private Map<UUID, CloudPlayer> cloudPlayers = new ConcurrentHashMap<>();

    public CloudProxy(ProxiedBootstrap proxiedBootstrap, CloudAPI cloudAPI)
    {
        instance = this;

        this.proxiedBootstrap = proxiedBootstrap;
        this.proxyProcessMeta = cloudAPI.getConfig().getObject("proxyProcess", new TypeToken<ProxyProcessMeta>() {
        }.getType());
        cloudAPI.getNetworkHandlerProvider().registerHandler(new NetworkHandlerImpl());
        ProxyServer.getInstance().getScheduler().schedule(proxiedBootstrap, new Runnable() {
            @Override
            public void run()
            {
                NetworkUtils.addAll(cachedServers, MapWrapper.collectionCatcherHashMap(cloudAPI.getServers(), new Catcher<String, ServerInfo>() {
                    @Override
                    public String doCatch(ServerInfo key)
                    {
                        ProxyServer.getInstance().getServers().put(
                                key.getServiceId().getServerId(),
                                ProxyServer.getInstance().constructServerInfo(key.getServiceId().getServerId(), new InetSocketAddress(key.getHost(), key.getPort()), "CloudNet2 Game-Server", false)
                        );

                        if(key.getServiceId().getGroup().equalsIgnoreCase(getProxyGroup().getProxyConfig().getDynamicFallback().getDefaultFallback()))
                            CollectionWrapper.iterator(ProxyServer.getInstance().getConfig().getListeners(), new Runnabled<ListenerInfo>() {
                                @Override
                                public void run(ListenerInfo obj)
                                {
                                    obj.getServerPriority().add(key.getServiceId().getServerId());
                                }
                            });
                        return key.getServiceId().getServerId();
                    }
                }));

                cloudAPI.setCloudService(CloudProxy.this);

            }
        }, 250, TimeUnit.MILLISECONDS);
    }

    public List<String> getServers(String group)
    {
        List<String> x = new ArrayList<>();
        for (ServerInfo server : this.getCachedServers().values())
        {
            if (server.getServiceId().getGroup().equalsIgnoreCase(group))
            {
                x.add(server.getServiceId().getServerId());
            }
        }
        return x;
    }

    public String fallback(ProxiedPlayer cloudPlayer)
    {

        for (ServerFallback serverFallback : CloudProxy.getInstance().getProxyGroup().getProxyConfig().getDynamicFallback().getFallbacks())
        {
            if(serverFallback.getGroup().equals(CloudProxy.getInstance().getProxyGroup().getProxyConfig().getDynamicFallback().getDefaultFallback())) continue;

            if(serverFallback.getPermission() != null)
            {
                if(!cloudPlayer.hasPermission(serverFallback.getPermission())) continue;

                List<String> servers = CloudProxy.getInstance().getServers(serverFallback.getGroup());
                if(servers.size() != 0)
                {
                    return servers.get(NetworkUtils.RANDOM.nextInt(servers.size()));
                }
            }
        }

        String fallback = getProxyGroup().getProxyConfig().getDynamicFallback().getDefaultFallback();
        List<String> liste = new ArrayList<>(MapWrapper.filter(cachedServers, new Acceptable<ServerInfo>() {
            @Override
            public boolean isAccepted(ServerInfo value)
            {
                return value.getServiceId().getGroup().equalsIgnoreCase(fallback);
            }
        }).keySet());

        if (liste.size() == 0)
            return null;
        else
            return liste.get(NetworkUtils.RANDOM.nextInt(liste.size()));
    }

    public String fallback(ProxiedPlayer cloudPlayer, String kickedFrom)
    {

        for (ServerFallback serverFallback : CloudProxy.getInstance().getProxyGroup().getProxyConfig().getDynamicFallback().getFallbacks())
        {
            if(serverFallback.getGroup().equals(CloudProxy.getInstance().getProxyGroup().getProxyConfig().getDynamicFallback().getDefaultFallback())) continue;

            if(serverFallback.getPermission() != null)
            {
                if(!cloudPlayer.hasPermission(serverFallback.getPermission())) continue;

                List<String> servers = CloudProxy.getInstance().getServers(serverFallback.getGroup());
                servers.remove(kickedFrom);
                if(servers.size() != 0)
                {
                    return servers.get(NetworkUtils.RANDOM.nextInt(servers.size()));
                }
            }
        }

        String fallback = getProxyGroup().getProxyConfig().getDynamicFallback().getDefaultFallback();
        List<String> liste = new ArrayList<>(MapWrapper.filter(cachedServers, new Acceptable<ServerInfo>() {
            @Override
            public boolean isAccepted(ServerInfo value)
            {
                return value.getServiceId().getGroup().equalsIgnoreCase(fallback);
            }
        }).keySet());
        liste.remove(kickedFrom);

        if (liste.size() == 0)
            return null;
        else
            return liste.get(NetworkUtils.RANDOM.nextInt(liste.size()));
    }

    public String fallbackOnEnabledKick(ProxiedPlayer cloudPlayer, String group, String kickedFrom)
    {

        for (ServerFallback serverFallback : CloudProxy.getInstance().getProxyGroup().getProxyConfig().getDynamicFallback().getFallbacks())
        {
            if(serverFallback.getGroup().equals(CloudProxy.getInstance().getProxyGroup().getProxyConfig().getDynamicFallback().getDefaultFallback())) continue;

            if(serverFallback.getPermission() != null)
            {
                if(!cloudPlayer.hasPermission(serverFallback.getPermission())) continue;

                List<String> servers = CloudProxy.getInstance().getServers(serverFallback.getGroup());
                servers.remove(kickedFrom);
                if(servers.size() != 0)
                {
                    return servers.get(NetworkUtils.RANDOM.nextInt(servers.size()));
                }
            }
        }

        {
            List<String> liste = new ArrayList<>(MapWrapper.filter(cachedServers, new Acceptable<ServerInfo>() {
                @Override
                public boolean isAccepted(ServerInfo value)
                {
                    return value.getServiceId().getGroup().equalsIgnoreCase(group);
                }
            }).keySet());
            liste.remove(kickedFrom);
            if (liste.size() != 0)
            {
                return liste.get(NetworkUtils.RANDOM.nextInt(liste.size()));
            }
        }

        String fallback = getProxyGroup().getProxyConfig().getDynamicFallback().getDefaultFallback();
        List<String> liste = new ArrayList<>(MapWrapper.filter(cachedServers, new Acceptable<ServerInfo>() {
            @Override
            public boolean isAccepted(ServerInfo value)
            {
                return value.getServiceId().getGroup().equalsIgnoreCase(fallback);
            }
        }).keySet());
        liste.remove(kickedFrom);
        if (liste.size() == 0)
            return null;
        else
            return liste.get(NetworkUtils.RANDOM.nextInt(liste.size()));
    }

    public ProxyGroup getProxyGroup()
    {
        return CloudAPI.getInstance().getProxyGroupData(CloudAPI.getInstance().getServiceId().getGroup());
    }

    public void update()
    {
        ProxyInfo proxyInfo = new ProxyInfo(CloudAPI.getInstance().getServiceId(), "localhost", 0, true,
                new ArrayList<>(CollectionWrapper.transform(ProxyServer.getInstance().getPlayers(), new Catcher<MultiValue<UUID, String>, ProxiedPlayer>() {
                    @Override
                    public MultiValue<UUID, String> doCatch(ProxiedPlayer key)
                    {
                        return new MultiValue<>(key.getUniqueId(), key.getName());
                    }
                })), proxyProcessMeta.getMemory(), ProxyServer.getInstance().getOnlineCount());
        CloudAPI.getInstance().update(proxyInfo);
    }

    public void updateAsync()
    {
        proxiedBootstrap.getProxy().getScheduler().runAsync(proxiedBootstrap, new Runnable() {
            @Override
            public void run()
            {
                update();
            }
        });
    }

    /**
     * Returns the instance which respens the api
     *
     * @return
     */
    public static CloudProxy getInstance()
    {
        return instance;
    }

    /**
     * Returns the Servers on cloudnet
     *
     * @return
     */
    public Map<String, ServerInfo> getCachedServers()
    {
        return cachedServers;
    }

    /**
     * Returns the cloudPlayers online
     *
     * @return
     */
    public Map<UUID, CloudPlayer> getCloudPlayers()
    {
        return cloudPlayers;
    }

    /**
     * Returns the API of the plugin instance
     *
     * @return
     */
    public Plugin getPlugin()
    {
        return proxiedBootstrap;
    }

    @Override
    public CloudPlayer getCachedPlayer(UUID uniqueId)
    {
        return cloudPlayers.get(uniqueId);
    }

    public CloudPlayer getCachedPlayer(String name)
    {
        return CollectionWrapper.filter(this.cloudPlayers.values(), new Acceptable<CloudPlayer>() {
            @Override
            public boolean isAccepted(CloudPlayer cloudPlayer)
            {
                return cloudPlayer.getName().equalsIgnoreCase(name);
            }
        });
    }

    @Override
    public boolean isProxyInstance()
    {
        return true;
    }

    @Override
    public Map<String, ServerInfo> getServers()
    {
        return this.getCachedServers();
    }

    private class NetworkHandlerImpl implements NetworkHandler {
        @Override
        public void onServerAdd(ServerInfo serverInfo)
        {
            ProxyServer.getInstance().getPluginManager().callEvent(new ProxiedServerAddEvent(serverInfo));
            ProxyServer.getInstance().getServers().put(
                    serverInfo.getServiceId().getServerId(),
                    ProxyServer.getInstance().constructServerInfo(serverInfo.getServiceId().getServerId(), new InetSocketAddress(serverInfo.getHost(), serverInfo.getPort()), "CloudNet2 Game-Server", false)
            );
            if (serverInfo.getServiceId().getGroup().equalsIgnoreCase(getProxyGroup().getProxyConfig().getDynamicFallback().getDefaultFallback()))
                CollectionWrapper.iterator(ProxyServer.getInstance().getConfig().getListeners(), new Runnabled<ListenerInfo>() {
                    @Override
                    public void run(ListenerInfo obj)
                    {
                        obj.getServerPriority().add(serverInfo.getServiceId().getServerId());
                    }
                });
            cachedServers.put(serverInfo.getServiceId().getServerId(), serverInfo);

            if(CloudAPI.getInstance().getModuleProperties().contains("notifyService") && CloudAPI.getInstance().getModuleProperties().getBoolean("notifyService"))
            for(ProxiedPlayer proxiedPlayer : ProxyServer.getInstance().getPlayers())
            {
                if(proxiedPlayer.hasPermission("cloudnet.notify"))
                {
                    proxiedPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', CloudAPI.getInstance().getCloudNetwork().getMessages().getString("notify-message-server-add").replace("%server%", serverInfo.getServiceId().getServerId())));
                }
            }

        }

        @Override
        public void onServerInfoUpdate(ServerInfo serverInfo)
        {
            ProxyServer.getInstance().getPluginManager().callEvent(new ProxiedServerInfoUpdateEvent(serverInfo));
            cachedServers.put(serverInfo.getServiceId().getServerId(), serverInfo);
        }

        @Override
        public void onServerRemove(ServerInfo serverInfo)
        {
            ProxyServer.getInstance().getPluginManager().callEvent(new ProxiedServerRemoveEvent(serverInfo));
            ProxyServer.getInstance().getServers().remove(serverInfo.getServiceId().getServerId());
            cachedServers.remove(serverInfo.getServiceId().getServerId());

            if (serverInfo.getServiceId().getGroup().equalsIgnoreCase(getProxyGroup().getProxyConfig().getDynamicFallback().getDefaultFallback()))
                CollectionWrapper.iterator(ProxyServer.getInstance().getConfig().getListeners(), new Runnabled<ListenerInfo>() {
                    @Override
                    public void run(ListenerInfo obj)
                    {
                        obj.getServerPriority().remove(serverInfo.getServiceId().getServerId());
                    }
                });

            if(CloudAPI.getInstance().getModuleProperties().contains("notifyService") && CloudAPI.getInstance().getModuleProperties().getBoolean("notifyService"))
            for(ProxiedPlayer proxiedPlayer : ProxyServer.getInstance().getPlayers())
            {
                if(proxiedPlayer.hasPermission("cloudnet.notify"))
                {
                    proxiedPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', CloudAPI.getInstance().getCloudNetwork().getMessages().getString("notify-message-server-remove").replace("%server%", serverInfo.getServiceId().getServerId())));
                }
            }
        }

        @Override
        public void onProxyAdd(ProxyInfo proxyInfo)
        {
            ProxyServer.getInstance().getPluginManager().callEvent(new ProxiedProxyAddEvent(proxyInfo));
        }

        @Override
        public void onProxyInfoUpdate(ProxyInfo proxyInfo)
        {
            ProxyServer.getInstance().getPluginManager().callEvent(new ProxiedProxyInfoUpdateEvent(proxyInfo));
        }

        @Override
        public void onProxyRemove(ProxyInfo proxyInfo)
        {
            ProxyServer.getInstance().getPluginManager().callEvent(new ProxiedProxyRemoveEvent(proxyInfo));
        }

        @Override
        public void onCloudNetworkUpdate(CloudNetwork cloudNetwork)
        {
            ProxyServer.getInstance().getPluginManager().callEvent(new ProxiedCloudNetworkUpdateEvent(cloudNetwork));

            if(cloudNetwork.getProxyGroups().containsKey(CloudAPI.getInstance().getGroup()))
            {
                ProxyGroup proxyGroup = cloudNetwork.getProxyGroups().get(CloudAPI.getInstance().getGroup());
                if(proxyGroup.getProxyConfig().isEnabled() && proxyGroup.getProxyConfig().isMaintenance())
                {
                    for(ProxiedPlayer proxiedPlayer : ProxyServer.getInstance().getPlayers())
                    {
                        if(!proxyGroup.getProxyConfig().getWhitelist().contains(proxiedPlayer.getName()) && !proxiedPlayer.hasPermission("cloudnet.maintenance"))
                        proxiedPlayer.disconnect(ChatColor.translateAlternateColorCodes('&', CloudAPI.getInstance().getCloudNetwork().getMessages().getString("kick-maintenance")));
                    }
                }
            }
        }

        @Override
        public void onCustomChannelMessageReceive(String channel, String message, Document document)
        {
            if(handle(channel, message, document)) return;
            ProxyServer.getInstance().getPluginManager().callEvent(new ProxiedCustomChannelMessageReceiveEvent(channel, message, document));
        }

        @Override
        public void onCustomSubChannelMessageReceive(String channel, String message, Document document)
        {
            if(handle(channel, message, document)) return;
            ProxyServer.getInstance().getPluginManager().callEvent(new ProxiedSubChannelMessageEvent(channel, message, document));
        }

        @Override
        public void onPlayerLoginNetwork(CloudPlayer cloudPlayer)
        {
            ProxyServer.getInstance().getPluginManager().callEvent(new ProxiedPlayerLoginEvent(cloudPlayer));
        }

        @Override
        public void onPlayerDisconnectNetwork(CloudPlayer cloudPlayer)
        {
            ProxyServer.getInstance().getPluginManager().callEvent(new ProxiedPlayerLogoutEvent(cloudPlayer));
            cloudPlayers.remove(cloudPlayer.getUniqueId());
        }

        @Override
        public void onPlayerDisconnectNetwork(UUID uniqueId)
        {
            ProxyServer.getInstance().getPluginManager().callEvent(new ProxiedPlayerLogoutUniqueEvent(uniqueId));
            cloudPlayers.remove(uniqueId);
        }

        @Override
        public void onPlayerUpdate(CloudPlayer cloudPlayer)
        {
            if(cloudPlayers.containsKey(cloudPlayer.getUniqueId()))
            {
                cloudPlayers.put(cloudPlayer.getUniqueId(), cloudPlayer);
            }
            ProxyServer.getInstance().getPluginManager().callEvent(new ProxiedPlayerUpdateEvent(cloudPlayer));
        }

        @Override
        public void onUpdateOnlineCount(int onlineCount)
        {
            ProxyServer.getInstance().getPluginManager().callEvent(new ProxiedOnlineCountUpdateEvent(onlineCount));
        }

        private boolean handle(String channel, String message, Document document)
        {
            if (channel.equalsIgnoreCase("cloudnet_internal"))
            {
                if (message.equalsIgnoreCase("kickPlayer"))
                {
                    ProxiedPlayer proxiedPlayer = ProxyServer.getInstance().getPlayer(document.getObject("uniqueId", UUID.class));
                    if (proxiedPlayer != null)
                        proxiedPlayer.disconnect(document.getString("reason"));
                }

                if (message.equalsIgnoreCase("sendPlayer"))
                {
                    net.md_5.bungee.api.config.ServerInfo serverInfo = ProxyServer.getInstance().getServerInfo(document.getString("server"));
                    if (serverInfo != null)
                    {
                        ProxiedPlayer proxiedPlayer = ProxyServer.getInstance().getPlayer(document.getObject("uniqueId", UUID.class));
                        if (proxiedPlayer != null)
                            proxiedPlayer.connect(serverInfo);
                    }
                }

                if (message.equalsIgnoreCase("sendMessage"))
                {
                    ProxiedPlayer proxiedPlayer = ProxyServer.getInstance().getPlayer(document.getObject("uniqueId", UUID.class));
                    if (proxiedPlayer != null)
                        proxiedPlayer.sendMessage(document.getString("message"));
                }
                return true;
            }
            else return false;
        }

    }
}