package unimelb.bitbox;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;
import unimelb.bitbox.util.FileSystemObserver;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Logger;

public class ServerMain implements FileSystemObserver {

    private static Logger log = Logger.getLogger(ServerMain.class.getName());
    protected FileSystemManager manager;
    public static List<Socket> peerMap;
    public static int port;
    public static String host;
    public static long blockSize;
    public static int maximumIncomingConnections;
    private int syncInterval;

    /**
     * Check socket already in peers
     * @param ip ip
     * @return true/false
     */
    public static boolean checkAlreadyConnect(String ip) {
        for (Socket socket: peerMap) {
            String socketIp = SocketUtil.getSocketIp(socket);
            if (Objects.equals(socketIp, ip)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remove socket with ip, if not exists, do nothing
     * @param ip ip
     */
    public static void removeWithIp(String ip) {
        int idx = -1;
        for (int i = 0; i < peerMap.size(); i ++) {
            String socketIp = SocketUtil.getSocketIp(peerMap.get(i));
            if (Objects.equals(socketIp, ip)) {
                idx = i;
            }
        }
        if (idx >= 0) {
            peerMap.remove(idx);
        }
    }

    public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
        manager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);
        host = Configuration.getConfigurationValue("advertisedName");
        port = Integer.parseInt(Configuration.getConfigurationValue("port"));
        blockSize = Long.parseLong(Configuration.getConfigurationValue("blockSize"));
        syncInterval = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
        maximumIncomingConnections = Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
        peerMap = new ArrayList<>(maximumIncomingConnections);
        new ServerListener(port, manager);
        connectToPeers();
        randomGenerateEvents();
    }

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
     * Connect to all peers, asy
     */
    private void connectToPeers() {
        log.info("connect to peer");
        String[] ips = Configuration.getConfigurationValue("peers").split(",");
        for (int i = 0; i < ips.length; i++) {
            String ip = ips[i];
            log.info(ip);
            // try to connect to peers
            String[] parts = ip.split(":");
            try {
                Socket socket = new Socket(parts[0], Integer.parseInt(parts[1]));
                log.info("socket: " + ip);
                // peerMap.put(ip, socket);
                new Thread(new SocketHandler(socket, manager, true)).start();
            } catch (IOException ignored) {
            }
        }
    }


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
        for (Socket socket : peerMap) {
            System.out.print(socket.getPort() + "  ");
            try {
                SocketUtil.print(req, new DataOutputStream(socket.getOutputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println();

    }

}
