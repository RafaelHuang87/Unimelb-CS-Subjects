package unimelb.bitbox;

import unimelb.bitbox.util.*;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import static unimelb.bitbox.SocketHandler.generateHandshakeReq;
import static unimelb.bitbox.SocketUtil.printPeer;

/**
 * Usage:
 * For ServerMain
 *
 * @author Junjie Huang
 * create time: 30/04/2019
 */
public class ServerMain implements FileSystemObserver {

    private static Logger log = Logger.getLogger(ServerMain.class.getName());
    protected FileSystemManager manager;

    /**
     * Peers in tcp
     */
    public static List<Socket> peers;
    public static int port;
    public static String host;
    public static int blockSize;
    public static int maximumIncomingConnections;
    private int syncInterval;

    private int clientPort;

    public static List<HostPort> udpList;
    public static boolean mode;
    public static int udpPort;
    public static int maxRetry;
    public static int timeout;
    public static DatagramSocket udpSocket;

    /**
     * Usage:
     * Check socket already in peers
     *
     * @param ip ip
     * @return true/false
     */
    public static boolean checkAlreadyConnect(String ip) {
        for (Socket socket : peers) {
            String socketIp = SocketUtil.getSocketIp(socket);
            if (Objects.equals(socketIp, ip)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Usage:
     * Remove socket with ip, if not exists, do nothing
     *
     * @param ip ip
     */
    public static void removeWithIp(String ip) {
        if (ServerMain.mode) {
            int idx = -1;

            for (int i = 0; i < peers.size(); i++) {
                String socketIp = SocketUtil.getSocketIp(peers.get(i));
                if (Objects.equals(socketIp, ip)) {
                    idx = i;
                }
            }
            if (idx >= 0) {
                Socket socket = peers.remove(idx);
                if (!socket.isClosed()) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            System.out.println(ip);
            System.out.println(udpList);
            udpList.remove(new HostPort(ip));
            System.out.println(udpList);
        }
    }

    /**
     * Usage:
     * new a ServerListener
     *
     * @throws NumberFormatException
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
        manager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);

        maximumIncomingConnections = Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
        clientPort = Integer.parseInt(Configuration.getConfigurationValue("clientPort"));
        mode = Configuration.getConfigurationValue("mode").equals("tcp");
        host = Configuration.getConfigurationValue("advertisedName").replace("localhost", "127.0.0.1");
        syncInterval = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
        blockSize = Integer.parseInt(Configuration.getConfigurationValue("blockSize"));

        // tcp-listener to client
        new ServerListener(clientPort, manager, true);

        if (mode) {
            port = Integer.parseInt(Configuration.getConfigurationValue("port"));
            // Define the maximum length of the peerlist
            peers = new ArrayList<>(maximumIncomingConnections);
            new ServerListener(port, manager).start();
            connectToPeers();
        } else {
            udpList = new ArrayList<>(maximumIncomingConnections);
            udpPort = Integer.parseInt(Configuration.getConfigurationValue("udpPort"));
            maxRetry = Integer.parseInt(Configuration.getConfigurationValue("maxRetry"));
            timeout = Integer.parseInt(Configuration.getConfigurationValue("timeout"));
            udpSocket = new DatagramSocket(ServerMain.udpPort);
            udpSocket.setSoTimeout(timeout);
            // listener for itself
            connectToPeers();
            new SocketHandler(manager, false).start();
        }
        randomGenerateEvents();

        // check for missing thread
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10 * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                SocketUtil.check();
            }
        }).run();
    }

    /**
     * Usage:
     * Generate events in syncInterval * 1000
     */
    private void randomGenerateEvents() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(syncInterval * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                List<FileSystemEvent> events = manager.generateSyncEvents();
                for (int i = 0; i < events.size(); i++) {
                    processFileSystemEvent(events.get(i));
                }
            }
        }).start();
    }

    /**
     * Usage:
     * Connect to all peers
     */
    private void connectToPeers() {
        log.info("connect to peer");
        String peerStr = Configuration.getConfigurationValue("peers");
        peerStr = peerStr == null ? "" : peerStr;
        String[] ips = peerStr.split(",");
        for (int i = 0; i < ips.length; i++) {
            String ip = ips[i];
            if (ip.isEmpty()) {
                continue;
            }
            ip = ip.replace("localhost", "127.0.0.1");
            log.info(ip);
            // try to connect to peers
            String[] parts = ip.split(":");
            // Socket socket = new Socket(parts[0], Integer.parseInt(parts[1]));
            if (ServerMain.mode) {
                new SocketHandler(parts[0], parts[1], manager, true).start();
            } else {
                // unable to connect in socket handler
                Document handShakeReq = generateHandshakeReq();

                int tryCount = 0;
                byte[] rec;
                while (tryCount < maxRetry) {
                    rec = new byte[ServerMain.blockSize];
                    System.out.println("try to accept from remote");
                    try {
                        printPeer(handShakeReq, ip);
                        System.out.println("after in jump");
                        // response is required
                        while (true) {
                            DatagramPacket result = new DatagramPacket(rec, rec.length);
                            udpSocket.receive(result);
                            // we block all other request when try to connect
                            String data = new String(result.getData(), 0, result.getLength());
                            System.out.println("Receive: " + data);
                            Document command = Document.parse(data);
                            if (command.getString("command").equals(Operation.HANDSHAKE_RESPONSE.name())) {
                                break;
                            } else if (command.getString("command").equals(Operation.HANDSHAKE_REQUEST.name())) {
                                // we receive the handshake request from target. so directly send res to it
                                Document document = (Document) command.get("hostPort");
                                String host = document.getString("host");
                                long port = document.getLong("port");
                                if (host.equals(parts[0]) && port == Long.parseLong(parts[1])) {
                                    // System.out.println("Same");
                                    Document re = new Document();
                                    re.append("command", Operation.HANDSHAKE_RESPONSE.name());
                                    Document hostPort = new Document();
                                    hostPort.append("host", ServerMain.host);
                                    hostPort.append("port", ServerMain.mode ? ServerMain.port : ServerMain.udpPort);
                                    re.append("hostPort", hostPort);
                                    printPeer(re, ip);
                                }
                            } else if (command.getString("command").equals(Operation.INVALID_PROTOCOL.name())) {
                                if (command.getString("message").contains("already in peer, close")
                                || command.getString("message").contains("don't repeat to join the network")) {
                                    // we receive the handshake request from target. so directly send res to it
                                    Document document = (Document) command.get("hostPort");
                                    String host = document.getString("host");
                                    long port = document.getLong("port");
                                    if (host.equals(parts[0]) && port == Long.parseLong(parts[1])) {
                                        break;
                                    }
                                }
                            }
                            // message time out exception will also lead to retry in outer loop
                        }
                        // only need to record ports, thread is no need to create
                        if (!ServerMain.udpList.contains(new HostPort(ip))) {
                            ServerMain.udpList.add(new HostPort(ip));
                        }
                        break;
                    } catch (Exception e) {
                        tryCount++;
                        log.info("retry " + tryCount + " times");
                    }
                }
            }
        }
    }


    /**
     * Usage:
     * Catch the event
     *
     * @param fileSystemEvent
     */
    @Override
    public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
        // TODO: process events
        // establish basic request
        Document req = new Document();
        try {
            Document fileDescriptor = new Document();
            fileDescriptor.append("md5", fileSystemEvent.fileDescriptor.md5);
            fileDescriptor.append("lastModified", fileSystemEvent.fileDescriptor.lastModified);
            fileDescriptor.append("fileSize", fileSystemEvent.fileDescriptor.fileSize);
            req.append("fileDescriptor", fileDescriptor);
        } catch (Exception e) {
            // may encounter NullPointerException when modify dir. not a mistake
        }
        req.append("pathName", fileSystemEvent.pathName);

        // check operation
        FileSystemManager.EVENT event = fileSystemEvent.event;
        switch (event) {
            case FILE_CREATE:
                req.append("command", Operation.FILE_CREATE_REQUEST.name());
                break;
            case FILE_DELETE:
                req.append("command", Operation.FILE_DELETE_REQUEST.name());
                break;
            case FILE_MODIFY:
                req.append("command", Operation.FILE_MODIFY_REQUEST.name());
                break;
            case DIRECTORY_CREATE:
                req.append("command", Operation.DIRECTORY_CREATE_REQUEST.name());
                req.append("fileDescriptor", new Document(null));
                break;
            case DIRECTORY_DELETE:
                req.append("command", Operation.DIRECTORY_DELETE_REQUEST.name());
                req.append("fileDescriptor", new Document(null));
                break;
        }
        System.out.print("Monitor req: ");
        if (mode) {
            for (Socket socket : peers) {
                System.out.print(socket.getPort() + "  ");
                try {
                    SocketUtil.print(req, new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8")));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            for (HostPort socket : udpList) {
                System.out.print(socket.toString() + "  ");
                try {
                    SocketUtil.print(req, socket.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println();

    }
}
