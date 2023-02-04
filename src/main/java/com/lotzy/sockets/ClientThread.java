package com.lotzy.sockets;

import com.lotzy.crewolocity.Skcrewolocity;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientThread extends Thread {
    protected Socket socket;
    protected SocketServer server;
    private ObjectInputStream in = null;
    private ObjectOutputStream out = null;
    private boolean alive = true;
    
    private String host = null;
    
    public ClientThread(SocketServer server,Socket client) {
        this.server = server;
        this.socket = client;
    }

    @Override
    public void run() {
        try {
            in = new ObjectInputStream(socket.getInputStream());
            out = new ObjectOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            return;
        }
        SocketPacket packet;
        while (alive) {
            try {
                packet = (SocketPacket) in.readObject();
                switch(packet.type) {
                    case CONNECTION: 
                        onClientConnection(packet);
                        break;
                    
                    default:
                        onUniversalPacket(packet);
                        break;
                }
            } catch (IOException e) {
                onClientDisconnection();
                return;
            } catch (ClassNotFoundException ex) {
                int id = server.clients.getIdByAddress(host);
                Skcrewolocity.getInstance().getLogger().info("Invalid packet from "+server.clients.servers[id].getName());
            }
        }
    }
    
    public void closeConnection() {
        try {
            in.close();
            out.close();
            socket.close();
        } catch (IOException ex) {}
    }
    
    public void onClientDisconnection() {
        int id = server.clients.getIdByAddress(host);
        server.clients.servers[id].setOffline();
        Skcrewolocity.getInstance().getLogger().info("Server "+server.clients.servers[id].getName()+" disconnected");
        server.clientThreads.put(server.clients.servers[id].getName(), null);
        server.sendPacketInstead(SocketPacket.ServerDisconnected(server.clients.servers[id].getName()), server.clients.servers[id].getName());
        closeConnection();
    }

    public void onUniversalPacket(SocketPacket packet) throws IOException {
        if (packet.receiver!=null) {
            this.server.sendPacket(packet, packet.receiver);
        } else {
            this.server.sendPacket(packet);
        }
    }
    
    public void onClientConnection(SocketPacket packet) {
        if (socket.getLocalAddress().getHostAddress().matches("((^127\\.)|(^10\\.)|(^172\\.1[6-9]\\.)|(^172\\.2[0-9]\\.)|(^172\\.3[0-1]\\.)|(^192\\.168\\.)).*")) {
            this.host = "127.0.0.1:"+(int)packet.body;
        } else {
            this.host = socket.getLocalAddress().getHostAddress()+":"+(int)packet.body;
        }
        Integer id = server.clients.getIdByAddress(host);
        Skcrewolocity.getInstance().getLogger().info("Trying connect "+host);
        if (id!=null) {
            Skcrewolocity.getInstance().getLogger().info("Server "+server.clients.servers[id].getName()+" connected");
            server.clients.servers[id].setOnline();
            server.clientThreads.put(server.clients.servers[id].getName(), this);
            send(SocketPacket.ConnectionInfoPacket(server.clients.servers[id].getName(), server.clients.servers));
            server.sendPacketInstead(SocketPacket.ServerConnected(server.clients.servers[id].getName()),server.clients.servers[id].getName());
        } else {
            alive = false;
            closeConnection();
        }
    }
    
    public void send(SocketPacket packet) {
        try {
            out.writeObject(packet);
            out.flush();
        } catch (IOException ex) { }
    }
}