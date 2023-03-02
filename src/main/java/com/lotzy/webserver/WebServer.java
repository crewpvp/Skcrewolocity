package com.lotzy.webserver;

import com.lotzy.sockets.ServerInfo;
import com.lotzy.sockets.SocketPacket;
import com.lotzy.sockets.SocketServer;
import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WebServer {
    private final String password;
    private final String user;
    private final BasicAuthenticator ba;
    public final HttpServer webserver;
    private SocketServer server;
    
    
    public WebServer(int port, String user, String password, SocketServer server) throws IOException {
        this.user = user;
        this.password = password;
        this.ba = new BasicAuthenticator("skrewolocity") {
            @Override
            public boolean checkCredentials(String user, String pwd) {
                return user.equals(password) && pwd.equals(user);
            }
        };
                
        this.webserver = HttpServer.create(new InetSocketAddress(port), 0);
        HttpContext context;
        
        context = webserver.createContext("/servers", (exchange -> {
            ArrayList<String> srvs = new ArrayList();
            
            Map<String, List<String>> params = splitQuery(exchange.getRequestURI().getRawQuery());
            Boolean online = Boolean.parseBoolean(params.getOrDefault("online", List.of("false")).stream().findFirst().orElse("false"));
            
            for(ServerInfo info : server.clients.servers)
                if(!online || info.isOnline())
                    srvs.add(info.getName());
            
            String resp = !srvs.isEmpty() ? "[\""+String.join("\",\"",srvs)+"\"]" : "[]";
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            OutputStream output = exchange.getResponseBody();
            output.write(resp.getBytes());
            output.flush();
            exchange.close();
        }));
        context.setAuthenticator(this.ba);
        
        context = webserver.createContext("/players", (exchange -> {
            ArrayList<String> players = new ArrayList();
            Map<String, List<String>> params = splitQuery(exchange.getRequestURI().getRawQuery());
            List<String> servers = params.get("server");
            for(ServerInfo info : server.clients.servers)
                if(servers.contains(info.getName()))
                    for(String p : info.getPlayers())
                        players.add(p);
            
            String resp = !players.isEmpty() ? "[\""+String.join("\",\"",players)+"\"]" : "[]";
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            OutputStream output = exchange.getResponseBody();
            output.write(resp.getBytes());
            output.flush();
            exchange.close();
        }));
        context.setAuthenticator(this.ba);
        
        context = webserver.createContext("/player", (exchange -> {
            String[] parts = exchange.getRequestURI().getRawPath().split("/");
            OutputStream output = exchange.getResponseBody();
            if (parts.length < 3 || parts[2].isEmpty()) {
                String error = "{\"error\":\"Specify player's nickname\"}";
                exchange.sendResponseHeaders(400, error.getBytes().length);
                output.write(error.getBytes());
                output.flush();
                exchange.close();
                return;
            }
            String nick = decode(parts[2]);
            String resp = null;
            for(ServerInfo info : server.clients.servers)
                if(info.isOnline())
                    for(String p : info.getPlayers())
                        if (p.equals(nick))
                            resp = "\""+info.getName()+"\"";
            if (resp != null) {
                exchange.sendResponseHeaders(200, resp.getBytes().length);
                output.write(resp.getBytes());
            } else {
                String error = "{\"error\":\"Player not found\"}";
                exchange.sendResponseHeaders(404, error.getBytes().length);
                output.write(resp.getBytes());
            }
            output.flush();
            exchange.close();
        }));
        context.setAuthenticator(this.ba);
        
        for(ServerInfo info : server.clients.servers) {
            context = webserver.createContext("/server/"+info.getName()+"/players", (exchange -> {
                String resp = info.getPlayers().length!=0 ? "[\""+String.join("\",\"",info.getPlayers())+"\"]" : "[]";
                exchange.sendResponseHeaders(200, resp.getBytes().length);
                OutputStream output = exchange.getResponseBody();
                output.write(resp.getBytes());
                output.flush();
                exchange.close();
            }));
            context.setAuthenticator(this.ba);
            
            context = webserver.createContext("/server/"+info.getName()+"/online", (exchange -> {
                String resp = info.isOnline().toString();
                exchange.sendResponseHeaders(200, resp.getBytes().length);
                OutputStream output = exchange.getResponseBody();
                output.write(resp.getBytes());
                output.flush();
                exchange.close();
            }));
            context.setAuthenticator(this.ba);
            
            context = webserver.createContext("/server/"+info.getName()+"/signal", (exchange -> {
                Map<String, List<String>> params = splitQuery(exchange.getRequestURI().getRawQuery());
                if (info.isOnline() && params.containsKey("key")) {
                    String key = params.get("key").stream().findFirst().get();
                    Object value = params.containsKey("value") ? params.get("value").stream().findFirst().get() : null;
                    server.sendPacket(SocketPacket.SignalPacket(info.getName(), key, value), info.getName());
                    exchange.sendResponseHeaders(200, 0);
                } else {
                    String error = "{\"error\":\"Key param not found\"}";
                    exchange.sendResponseHeaders(400, error.getBytes().length);
                    OutputStream output = exchange.getResponseBody();
                    output.write(error.getBytes());
                    output.flush();
                }
                exchange.close();
            }));
            context.setAuthenticator(this.ba);
            
            context = webserver.createContext("/server/"+info.getName()+"/command", (exchange -> {
                String command = decode(exchange.getRequestURI().getRawQuery());
                if (info.isOnline() && !command.isEmpty()) {
                    server.sendPacket(SocketPacket.CommandPacket(info.getName(), new String[] {command}), info.getName());
                    exchange.sendResponseHeaders(200, 0);
                } else {
                    String error = "{\"error\":\"Empty command is not allowed\"}";
                    exchange.sendResponseHeaders(400, error.getBytes().length);
                    OutputStream output = exchange.getResponseBody();
                    output.write(error.getBytes());
                    output.flush();
                }
                exchange.close();
            }));
            context.setAuthenticator(this.ba);
        }
        
        
        webserver.setExecutor(null); // creates a default executor
        webserver.start();
    }
    
    public static Map<String, List<String>> splitQuery(String query) {
        if (query == null || "".equals(query)) {
            return Collections.emptyMap();
        }

        return Pattern.compile("&").splitAsStream(query)
           .map(s -> Arrays.copyOf(s.split("="), 2))
           .collect(Collectors.groupingBy(s -> decode(s[0]), Collectors.mapping(s -> decode(s[1]), Collectors.toList())));
    }
   
    
    private static String decode(final String encoded) {
        try {
            return encoded == null ? null : URLDecoder.decode(encoded, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 is a required encoding", e);
        }
    }
    
    public String getAuthToken() {
        return Base64.getEncoder().encodeToString((this.user+":"+this.password).getBytes());
    }
} 
