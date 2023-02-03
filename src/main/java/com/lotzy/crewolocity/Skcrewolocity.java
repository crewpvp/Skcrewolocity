package com.lotzy.crewolocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;
import com.lotzy.sockets.ServerInfo;
import com.lotzy.sockets.SocketPacket;
import com.lotzy.sockets.SocketServer;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.ServerConnection;

@Plugin(id = "skcrewolocity",
        name = "Skcrewolocity",
        version = "1.0",
        url = "https://crewpvp.xyz",
        description = "Provide socket server for Skcrew to communicate servers",
        authors = {"Lotzy"})

public class Skcrewolocity {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private SocketServer socketServer;
    private static Skcrewolocity instance;
    public Logger getLogger() {
        return this.logger;
    }
    public static Skcrewolocity getInstance() {
        return instance;
    }
    
    @Inject
    public Skcrewolocity(ProxyServer server, Logger logger,@DataDirectory Path dataDirectory) {
        instance = this;
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.logger.info("Enabled");
    }
    
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) throws IOException, URISyntaxException {
        if (!Files.exists(dataDirectory.resolve("config.yml"))) {
            if (!Files.exists(dataDirectory)) Files.createDirectory(dataDirectory);
            Files.copy(getClass().getClassLoader().getResourceAsStream("config.yml"), dataDirectory.resolve("config.yml"), StandardCopyOption.REPLACE_EXISTING);
        }
        Map<String, Object> data = new Yaml().load(new FileInputStream(dataDirectory.resolve("config.yml").toFile()));  
        if (data.containsKey("socket-port")) {
            openSocketServer((int)data.get("socket-port"));
        } else {
            openSocketServer(1337);
        }     
    }
    
    public void openSocketServer(int port) throws IOException {
        HashMap<String,ServerInfo> clients = new HashMap();
        for(RegisteredServer rs : this.server.getAllServers()) {
            ServerInfo server = new ServerInfo(rs.getServerInfo().getName());
            for (Player p : rs.getPlayersConnected())
                server.addPlayer(p.getUsername());
            InetSocketAddress address = rs.getServerInfo().getAddress();
            if (address.getAddress().getHostAddress().matches("((^127\\.)|(^10\\.)|(^172\\.1[6-9]\\.)|(^172\\.2[0-9]\\.)|(^172\\.3[0-1]\\.)|(^192\\.168\\.)).*")) {
                clients.put("127.0.0.1:"+address.getPort(), server);
            } else {
                clients.put(address.getAddress().getHostAddress()+":"+address.getPort(), server);
            }
        }
        socketServer = new SocketServer(port,clients);
        this.logger.info("Opened server socket on 127.0.0.1:"+port);
    }
    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        ServerConnection con = event.getPlayer().getCurrentServer().get();
        if (con!=null)
            socketServer.sendPacket(SocketPacket.PlayerLeavePacket(event.getPlayer().getUsername(),con.getServerInfo().getName()));
    }
    
    public void onPlayerConnect(LoginEvent event) {
        ServerConnection con = event.getPlayer().getCurrentServer().get();
         if (con!=null)
            socketServer.sendPacket(SocketPacket.PlayerJoinPacket(event.getPlayer().getUsername(),con.getServerInfo().getName()));
    }
    
}