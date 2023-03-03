package com.lotzy.webserver;

import com.lotzy.crewolocity.Skcrewolocity;
import com.lotzy.sockets.ServerInfo;
import com.lotzy.sockets.SocketPacket;
import com.lotzy.sockets.SocketServer;
import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WebServer {
    private final String password;
    private final String login;
    private final BasicAuthenticator ba;
    public final HttpServer webserver;
    public WebServer(int port, String user, String password, SocketServer server) throws IOException {
        this.login = user;
        this.password = password;
        this.ba = new BasicAuthenticator("skrewolocity") {
            @Override
            public boolean checkCredentials(String user, String pwd) {
                return user.equals(login) && pwd.equals(password);
            }
        };
        
        this.webserver = HttpServer.create(new InetSocketAddress(port), 0);
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor)Executors.newFixedThreadPool(10);

        webserver.createContext("/", (exchange -> {
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF_8");
                
                OutputStream os; 
                byte[] rawResponseBody;
                String resp;
                Map<String, List<String>> params;
                
                if (!exchange.getRequestMethod().toUpperCase().equals("GET")) {
                    resp = "{\"error\":\"Wrong request method\"}";
                    rawResponseBody = resp.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(405, rawResponseBody.length);
                    os = exchange.getResponseBody();
                    os.write(rawResponseBody);
                    os.flush();
                    exchange.close();
                    return;
                }

                String[] parts = exchange.getRequestURI().getRawPath().split("/");
                int pathEntries = parts.length;

                if (pathEntries<2 || parts[1].isBlank()) {
                    resp = "{\"error\":\"Wrong request path\"}";
                    rawResponseBody = resp.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(400, rawResponseBody.length);
                    os = exchange.getResponseBody();
                    os.write(rawResponseBody);
                    os.flush();
                    exchange.close();
                    return;
                }
                String[] players;
                String[] servers;
                switch(parts[1].toLowerCase()) {
                    case "players":
                        params = splitQuery(exchange.getRequestURI().getRawQuery());
                        
                        List<String> serversParam = params.getOrDefault("server", null);
                        servers = serversParam!=null ? serversParam.toArray(String[]::new) : null;
                        players = Skcrewolocity.getInstance().getPlayers(servers);
                        resp = players.length!=0 ? "[\""+String.join("\",\"",players)+"\"]" : "[]";
                        rawResponseBody = resp.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, rawResponseBody.length);
                        os = exchange.getResponseBody();
                        os.write(rawResponseBody);
                        os.flush();
                        exchange.close();
                        return;
                        
                    case "servers":
                        ArrayList<String> srvs = new ArrayList();
                        String query = exchange.getRequestURI().getRawQuery();
                        query = query!=null ? query.toLowerCase() : query;
                        params = splitQuery(query);
                        Boolean online = Boolean.parseBoolean(params.getOrDefault("online", List.of("false")).stream().findFirst().orElse("false"));
                        for(ServerInfo info : server.clients.servers) {
                            if(!(!online || info.isOnline())) continue;
                            srvs.add(info.getName());
                        }
                        resp = !srvs.isEmpty() ? "[\""+String.join("\",\"",srvs)+"\"]" : "[]";
                        rawResponseBody = resp.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, rawResponseBody.length);
                        os = exchange.getResponseBody();
                        os.write(rawResponseBody);
                        os.flush();
                        exchange.close();
                        return;
                        
                    case "player":
                        if (pathEntries < 3 || parts[2].isBlank()) {
                            resp = "{\"error\":\"Specify nickname of player\"}";
                            rawResponseBody = resp.getBytes(StandardCharsets.UTF_8);
                            exchange.sendResponseHeaders(400, rawResponseBody.length);
                            os = exchange.getResponseBody();
                            os.write(rawResponseBody);
                            os.flush();
                            exchange.close();
                            return;
                        }
                        String nick = decode(parts[2]);
                        resp = Skcrewolocity.getInstance().getServer(nick);
                        if (resp != null) {
                            resp = "\""+resp+"\"";
                            rawResponseBody = resp.getBytes(StandardCharsets.UTF_8);
                            exchange.sendResponseHeaders(200, rawResponseBody.length);
                        } else {
                            resp = "{\"error\":\"Player not found\"}";
                            rawResponseBody = resp.getBytes(StandardCharsets.UTF_8);
                            exchange.sendResponseHeaders(404, rawResponseBody.length);
                        }
                        os = exchange.getResponseBody();
                        os.write(rawResponseBody);
                        os.flush();
                        exchange.close();
                        return;
                    case "server":
                        if (pathEntries<4 || parts[2].isBlank() || parts[3].isBlank()) {
                            resp = "{\"error\":\"Wrong request\"}";
                            rawResponseBody = resp.getBytes(StandardCharsets.UTF_8);
                            exchange.sendResponseHeaders(400, rawResponseBody.length);
                            os = exchange.getResponseBody();
                            os.write(rawResponseBody);
                            os.flush();
                            exchange.close();
                            return;
                        }
                        loop: for(ServerInfo info : server.clients.servers) {
                            if (!info.getName().equals(parts[2])) continue;
                            switch(parts[3].toLowerCase()) {
                                case "players":
                                    players = Skcrewolocity.getInstance().getPlayers(new String[] {info.getName()});
                                    resp = players.length!=0 ? "[\""+String.join("\",\"",players)+"\"]" : "[]";
                                    rawResponseBody = resp.getBytes(StandardCharsets.UTF_8);
                                    exchange.sendResponseHeaders(200, rawResponseBody.length);
                                    os = exchange.getResponseBody();
                                    os.write(rawResponseBody);
                                    os.flush();
                                    exchange.close();
                                    return;
                                case "online":
                                    resp = info.isOnline().toString();
                                    rawResponseBody = resp.getBytes(StandardCharsets.UTF_8);
                                    exchange.sendResponseHeaders(200, rawResponseBody.length);
                                    os = exchange.getResponseBody();
                                    os.write(rawResponseBody);
                                    os.flush();
                                    exchange.close();
                                    return;
                                case "signal":
                                    params = splitQuery(exchange.getRequestURI().getRawQuery());
                                    if (info.isOnline() && params.containsKey("key")) {
                                        String key = params.get("key").stream().findFirst().get();
                                        Object value = params.containsKey("value") ? params.get("value").stream().findFirst().get() : null;
                                        server.sendPacket(SocketPacket.SignalPacket(info.getName(), key, value), info.getName());
                                        resp = "{\"response\":\"Signal sended\"}";
                                        rawResponseBody = resp.getBytes(StandardCharsets.UTF_8);
                                        exchange.sendResponseHeaders(200, rawResponseBody.length);
                                    } else {
                                        resp = "{\"error\":\"Not found key field\"}";
                                        rawResponseBody = resp.getBytes(StandardCharsets.UTF_8);
                                        exchange.sendResponseHeaders(400, rawResponseBody.length);
                                    }
                                    os = exchange.getResponseBody();
                                    os.write(rawResponseBody);
                                    os.flush();
                                    exchange.close();
                                    return;
                                case "command":
                                    String command = decode(exchange.getRequestURI().getRawQuery());
                                    if (info.isOnline() && !command.isBlank()) {
                                        server.sendPacket(SocketPacket.CommandPacket(info.getName(), new String[] {command}), info.getName());
                                        resp = "{\"response\":\"Command sended\"}";
                                        rawResponseBody = resp.getBytes(StandardCharsets.UTF_8);
                                        exchange.sendResponseHeaders(200, rawResponseBody.length);
                                    } else {
                                        resp = "{\"error\":\"Empty command is not allowed\"}";
                                        rawResponseBody = resp.getBytes(StandardCharsets.UTF_8);
                                        exchange.sendResponseHeaders(400, rawResponseBody.length);
                                    }
                                    os = exchange.getResponseBody();
                                    os.write(rawResponseBody);
                                    os.flush();
                                    exchange.close();
                                    return;
                                default:
                                    break loop;
                            }
                        } 
                        resp = "{\"error\":\"Requested server doesnt exists\"}";
                        rawResponseBody = resp.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(404, rawResponseBody.length);
                        os = exchange.getResponseBody();
                        os.write(rawResponseBody);
                        os.flush();
                        exchange.close();
                        return;
                }
                resp = "{\"error\":\"Request path doesnt exists\"}";
                rawResponseBody = resp.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, rawResponseBody.length);
                os = exchange.getResponseBody();
                os.write(rawResponseBody);
                os.flush();
                exchange.close();
            } catch(Exception e) {
                e.printStackTrace();
            }
        })).setAuthenticator(this.ba);
        
        
        this.webserver.setExecutor(threadPoolExecutor); // creates a default executor
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
        return Base64.getEncoder().encodeToString((this.login+":"+this.password).getBytes());
    }
} 
