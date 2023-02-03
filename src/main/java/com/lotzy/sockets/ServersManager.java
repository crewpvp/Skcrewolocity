package com.lotzy.sockets;

import java.io.Serializable;
import java.util.HashMap;

public class ServersManager implements Serializable {
    public ServerInfo[] servers;
    private final String[] addresses;
    public ServersManager(HashMap<String,ServerInfo> servers) {
        this.servers = servers.values().toArray(new ServerInfo[0]);
        this.addresses = servers.keySet().toArray(new String[0]);
    }
    public Integer getIdByAddress(String address) {
        for(int i = 0; i<addresses.length; i++)
            if (addresses[i].equals(address))
                return i;
        return null;
    }
}
