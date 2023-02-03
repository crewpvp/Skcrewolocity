package com.lotzy.sockets;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map.Entry;

public class SocketServer {
    private Socket socket = null;
    private ServerSocket server = null;
    private SocketServer instance = null;
    public ServersManager clients;
    public HashMap<String,ClientThread> clientThreads = new HashMap();
    
    public SocketServer(int port, HashMap<String,ServerInfo> clients) {
        instance = this;
        this.clients = new ServersManager(clients);
        for(ServerInfo info : this.clients.servers)
            clientThreads.put(info.getName(), null);

        try {
            server = new ServerSocket(port);
            (new Thread() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            socket = server.accept();
                        } catch (IOException e) {
                        }
                        new ClientThread(instance,socket).start();
                    } 
                }
            }).start();
        } catch (IOException e) {
        }      
    }
    
    public void sendPacket(SocketPacket packet, String server) {
        ClientThread c = clientThreads.get(server);
        if (c!=null) {
            c.send(packet);
        }
    }
    public void sendPacket(SocketPacket packet) {
        for (Entry<String, ClientThread> entry : this.clientThreads.entrySet())
            if(entry.getValue()!=null)
                entry.getValue().send(packet);
    }
    public void sendPacketInstead(SocketPacket packet, String server) {
         for (Entry<String, ClientThread> entry : this.clientThreads.entrySet())
            if(entry.getValue()!=null && !entry.getKey().equals(server))
                entry.getValue().send(packet);
    }
}
