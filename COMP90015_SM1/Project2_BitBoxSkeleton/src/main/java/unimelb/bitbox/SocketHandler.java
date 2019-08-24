package unimelb.bitbox;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;

import java.io.*;
import java.net.DatagramPacket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import static unimelb.bitbox.ServerMain.*;
import static unimelb.bitbox.SocketUtil.*;

/**
 * SocketHandler
 * Usage:
 * socket client listener & the handler of all kinds of Operation {@link Operation}
 *
 * @author Junjie Huang
 * create time 30/04/2019
 */
public class SocketHandler extends Thread {

    private static Logger log = Logger.getLogger(SocketHandler.class.getName());

    private Socket socket;

    private String ip;

    private BufferedWriter output;

    private Set<String> pathName = new HashSet<>();

    private FileSystemManager manager;

    private boolean needHandShake;

    /**
     * Usage:
     * Operation Table
     */
    private OperationMonitor[] monitorTable;

    private Document lastReq;


    public SocketHandler(FileSystemManager manager, boolean needHandShake) {
        this.manager = manager;
        this.needHandShake = needHandShake;
        // table driven
        monitorTable = new OperationMonitor[]{
                optConnectionRefused,
                optHandshakeReq,
                stubOperationMonitor,
                optFileCreateReq,
                stubOperationMonitor,
                optFileBytesReq,
                optFileBytesRes,
                optFileDeleteReq,
                stubOperationMonitor,
                optFileModifyReq,
                stubOperationMonitor,
                optDirectoryCreateReq,
                stubOperationMonitor,
                optDirectoryDeleteReq,
                stubOperationMonitor,
                stubOperationMonitor};
    }

    public SocketHandler(Socket socket, FileSystemManager manager, boolean needHandShake) {
        this(manager, needHandShake);
        this.socket = socket;
        // normal, establish peer connection
        ip = getSocketIp(socket);
    }

    public SocketHandler(String host, String port, FileSystemManager manager, boolean needHandShake) {
        this(manager, needHandShake);
        ip = host + ":" + port;
        try {
            if (ServerMain.mode) {
                socket = new Socket(host, Integer.parseInt(port));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Document generateHandshakeReq() {
        Document handShakeReq = new Document();
        handShakeReq.append("command", Operation.HANDSHAKE_REQUEST.name());
        Document hostPort = new Document();
        hostPort.append("host", ServerMain.host);
        if (ServerMain.mode) {
            hostPort.append("port", ServerMain.port);
        } else {
            hostPort.append("port", ServerMain.udpPort);
        }
        handShakeReq.append("hostPort", hostPort);
        return handShakeReq;
    }


    public void performHandShake(String ip) throws IOException {
        Document handShakeReq = generateHandshakeReq();
        if (ServerMain.mode) {
            ServerMain.peers.add(socket);
        } else {
            ServerMain.udpList.add(new HostPort(ip));
        }
        print(handShakeReq, ServerMain.mode ? output : ip);
    }

    @Override
    public void run() {
        if (ServerMain.mode) {
            monitorTCP();
        } else {
            monitorUDP();
        }
    }

    /**
     * Usage:
     * Mode of UDP
     */
    private void monitorUDP() {
        System.out.println("Mode udp, begin receive");
        try {
            udpSocket.setSoTimeout(ServerMain.timeout);
            byte[] rec;
            while (true) {
                rec = new byte[ServerMain.blockSize * 2];
                DatagramPacket result = new DatagramPacket(rec, rec.length);
                try {
                    udpSocket.receive(result);
                } catch (SocketTimeoutException e) {
                    continue;
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }
                System.out.println("UDP-Data: " + new String(result.getData(), 0, result.getLength()));
                // ip in this request
                ip = result.getAddress().getHostAddress() + ":" + result.getPort();

                Document request = Document.parse(new String(result.getData(), 0, result.getLength()));

                String command = request.getString("command");
                System.out.println("UDP " + command);
                // empty request, skip
                if (command == null) {
                    continue;
                }

                // map string to specific operation
                Operation operation = Operation.valueOf(command);
                System.out.println(command + " " + operation + " " + operation.ordinal());
                if (operation == Operation.DISCONNECT_PEER_REQUEST) {
                    String host = request.getString("host");
                    long port = request.getLong("port");
                    ServerMain.removeWithIp(host + ":" + port);
                    if (ServerMain.mode) {
                        socket.close();
                    }
                    break;
                } else {
                    OperationMonitor monitor = monitorTable[operation.ordinal()];
                    monitor.run(request);
                    // handle operation
                    if (operation == Operation.CONNECTION_REFUSED || operation == Operation.INVALID_PROTOCOL) {
                        removeWithIp(ip);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Usage:
     * Mode of TCP
     */
    private void monitorTCP() {
        // establish stream
        if (socket == null) {
            return;
        }
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            int count = 0;
            while (!input.ready() && count < 60) {
                // wait till input is ready
                try {
                    Thread.sleep(1000);
                } catch (Exception ignored) {
                }
                count++;
            }

            if (needHandShake) {
                performHandShake(ip);
            }
            log.info("begin listen to socket " + socket.getPort());
            while (true) {
                String data;
                try {

                    data = input.readLine();
                    if (data.isEmpty()) {
                        break;
                    }
                } catch (Exception e) {
                    // connection closed or EOF
                    e.printStackTrace();
                    break;
                }

                Document request = Document.parse(data);
                String command = request.getString("command");
                // empty request, skip
                System.out.println("SocketHandler " + command);
                if (command == null) {
                    continue;
                }
                log.info(data);

                // map string to specific operation
                Operation operation = Operation.valueOf(command);
                if (operation == Operation.DISCONNECT_PEER_REQUEST) {
                    String host = request.getString("host");
                    int port = request.getInteger("port");
                    ServerMain.removeWithIp(host + ":" + port);
                    // disconnect then close
                    break;
                } else {
                    System.out.println("TCP-Data: " + operation);
                    OperationMonitor monitor = monitorTable[operation.ordinal()];
                    monitor.run(request);
                    // handle operation
                    if (operation == Operation.CONNECTION_REFUSED || operation == Operation.INVALID_PROTOCOL) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        removeWithIp(ip);

        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            for (String path : pathName) {
                manager.cancelFileLoader(path);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private OperationMonitor stubOperationMonitor = (request) -> {
    };

    /**
     * Usage
     * Try to connect to each peer in the peerlist
     */
    private OperationMonitor optConnectionRefused = (request) -> {
        ArrayList<Document> peers = (ArrayList<Document>) request.get("peers");
        for (Document eachPeer : peers) {
            String host = eachPeer.getString("host");
            int port = eachPeer.getInteger("port");
            // try to connect to peers
            // Socket socket = new Socket(host, port);
            log.info("try to connect to next peer: " + ip);
            if (ServerMain.mode) {
                new SocketHandler(host, String.valueOf(port), manager, true).start();
            } else {
                // unable to handler its response here
                print(lastReq, ip);
            }
        }
    };

    /**
     * Usage
     * Class to create output for directory delete
     */
    private OperationMonitor optDirectoryDeleteReq = (request) -> {
        String pathName = request.getString("pathName");
        boolean suc = manager.deleteDirectory(pathName);
        Document re = Document.parse(request.toJson());
        re.append("command", Operation.DIRECTORY_DELETE_RESPONSE.name());
        appendStatusToResult(re, suc, suc ? "directory deleted" : "there was a problem deleting the directory");
        print(re, ServerMain.mode ? output : ip);
    };

    /**
     * Usage
     * Class to create output for directory create
     */
    private OperationMonitor optDirectoryCreateReq = (request) -> {
        String pathName = request.getString("pathName");
        Document re = Document.parse(request.toJson());
        re.append("command", Operation.DIRECTORY_CREATE_RESPONSE.name());

        if (manager.isSafePathName(pathName)) {
            if (manager.dirNameExists(pathName)) {
                appendStatusToResult(re, false, "pathname already exists");
            } else {
                if (manager.makeDirectory(pathName)) {
                    appendStatusToResult(re, true, "directory created");
                } else {
                    appendStatusToResult(re, false, "there was a problem creating the directory");
                }
            }
        } else {
            appendStatusToResult(re, false, "unsafe pathname given");
        }
        print(re, ServerMain.mode ? output : ip);
    };

    /**
     * Usage
     * Class to create output for file modify
     */
    private OperationMonitor optFileModifyReq = (request) -> {
        Document fileDescriptor = (Document) request.get("fileDescriptor");
        String md5 = fileDescriptor.getString("md5");
        long lastModified = fileDescriptor.getLong("lastModified");
        long fileSize = fileDescriptor.getLong("fileSize");
        String pathName = request.getString("pathName");

        Document re = Document.parse(request.toJson());
        re.append("command", Operation.FILE_MODIFY_RESPONSE.name());
        try {
            boolean canCreate = manager.isSafePathName(pathName) &&
                    manager.fileNameExists(pathName) &&
                    manager.modifyFileLoader(pathName, md5, lastModified);

            if (canCreate) {
                // handle the create-option
                // we have shortcut, no need to create
                appendStatusToResult(re, true, "file loader ready");
                print(re, ServerMain.mode ? output : ip);
                if (!manager.checkShortcut(pathName)) {
                    if (!manager.checkWriteComplete(pathName)) {
                        // begin to read file
                        Document reqFile = buildFileBytesRequest(md5, lastModified, fileSize, pathName, 0, Math.min(fileSize, ServerMain.blockSize));
                        print(reqFile, ServerMain.mode ? output : ip);
                    }
                }
                return;
            }
        } catch (Exception ex) {
            // error encounter when create
            ex.printStackTrace();
        }

        appendStatusToResult(re, false, "invalid parameters");
        print(re, ServerMain.mode ? output : ip);
    };

    /**
     * Usage
     * Class for file delete
     */
    private OperationMonitor optFileDeleteReq = ((request) -> {
        Document fileDescriptor = (Document) request.get("fileDescriptor");
        String md5 = fileDescriptor.getString("md5");
        long lastModified = fileDescriptor.getLong("lastModified");
        String pathName = request.getString("pathName");
        // no need to do more check, file system will help us
        // Success is God’s will
        boolean suc = manager.deleteFile(pathName, lastModified, md5);
        Document res = Document.parse(request.toJson());
        res.append("command", Operation.FILE_DELETE_RESPONSE.name());
        appendStatusToResult(res, suc, suc ? "file deleted" : "there was a problem deleting the file");
        print(res, ServerMain.mode ? output : ip);
    });

    /**
     * Usage
     * Class for write file
     */
    private OperationMonitor optFileBytesRes = (request) -> {
        System.out.println("opt file request " + request.toJson());
        Document fileDescriptor = (Document) request.get("fileDescriptor");
        String md5 = fileDescriptor.getString("md5");
        long lastModified = fileDescriptor.getLong("lastModified");
        long fileSize = fileDescriptor.getLong("fileSize");
        String pathName = request.getString("pathName");
        long position = request.getLong("position");
        long length = request.getLong("length");
        byte[] content = Base64.getDecoder().decode(request.getString("content"));
        boolean status = request.getBoolean("status");
        if (status) {
            System.out.println("Write to file re: ");
            // save to file
            boolean suc = manager.writeFile(pathName, ByteBuffer.wrap(content), position);

            System.out.println("Write file re: " +  suc);
            if (suc) {
                // more page
                System.out.println("Remain, " + position + " " + length + " " + fileSize);
                long remain = fileSize - position - length;
                if (!manager.checkWriteComplete(pathName)) {

                    Document morePageReq = buildFileBytesRequest(md5, lastModified, fileSize, pathName, position + length, Math.min(remain, ServerMain.blockSize));
                    print(morePageReq, ServerMain.mode ? output : ip);
                } else {
                    this.pathName.remove(pathName);
                }
            } else {
                throw new IOException("fail to write file");
            }
        }
    };

    /**
     * Usage
     * Class to create output for getting content
     */
    private OperationMonitor optFileBytesReq = (request) -> {
        Document fileDescriptor = (Document) request.get("fileDescriptor");
        String md5 = fileDescriptor.getString("md5");
        long position = request.getLong("position");
        long length = request.getLong("length");

        ByteBuffer byteBuffer = manager.readFile(md5, position, length);
        Document re = Document.parse(request.toJson());
        re.append("command", Operation.FILE_BYTES_RESPONSE.name());
        if (byteBuffer != null) {
            // encode
            re.append("content", Base64.getEncoder().encodeToString(byteBuffer.array()));
            appendStatusToResult(re, true, "successful read");
        } else {
            // no such file
            appendStatusToResult(re, false, "unsuccessful read");
        }

        print(re, ServerMain.mode ? output : ip);
    };


    /**
     * Usage
     * Class to create output for file create
     */
    private OperationMonitor optFileCreateReq = (request) -> {
        Document fileDescriptor = (Document) request.get("fileDescriptor");
        String md5 = fileDescriptor.getString("md5");
        long lastModified = fileDescriptor.getLong("lastModified");
        long fileSize = fileDescriptor.getLong("fileSize");
        String pathName = request.getString("pathName");

        // we simply jsonfy the request and then un-jsonfy to copy the object
        Document re = Document.parse(request.toJson());
        re.append("command", Operation.FILE_CREATE_RESPONSE.name());
        // determine if can be created or not
        boolean canCreate;
        try {
            canCreate = manager.isSafePathName(pathName) &&
                    !manager.fileNameExists(pathName, md5) &&
                    manager.createFileLoader(pathName, md5, fileSize, lastModified);

            if (canCreate) {
                // handle the create-option
                // we have shortcut, no need to create
                if(manager.fileNameExists(pathName)){
//                if (manager.checkShortcut(pathName)) {
                    appendStatusToResult(re, true, "create with short-cut");
                    print(re, ServerMain.mode ? output : ip);

                    if (!manager.checkWriteComplete(pathName)) {
                        // begin to read file
                        Document reqFile = buildFileBytesRequest(md5, lastModified, fileSize, pathName, 0, Math.min(fileSize, ServerMain.blockSize));
                        print(reqFile, ServerMain.mode ? output : ip);
                        return;
                    }

                } else {
                    appendStatusToResult(re, true, "file loader ready");
                    print(re, ServerMain.mode ? output : ip);
                    // begin to read file
                    Document reqFile = buildFileBytesRequest(md5, lastModified, fileSize, pathName, 0, Math.min(fileSize, ServerMain.blockSize));
                    print(reqFile, ServerMain.mode ? output : ip);
                    return;
                }
            }
        } catch (Exception ex) {
            // error encounter when create
//            ex.printStackTrace();
            canCreate = false;
        }

        if (!canCreate) {
            appendStatusToResult(re, false, "invalid parameters");
        }
        print(re, ServerMain.mode ? output : ip);
    };

    /**
     * Usage
     * Class to create output for hand shake
     */
    private OperationMonitor optHandshakeReq = (request) -> {
        Document document = (Document) request.get("hostPort");
        String host = document.getString("host");
        int port = (int) document.getLong("port");

        System.out.println(document.toJson());

        Document re = new Document();
        int currentSize = ServerMain.mode ? peers.size() : udpList.size();
        // check pool size
        if (currentSize + 1 > ServerMain.maximumIncomingConnections) {
            // full, close
            re.append("command", Operation.CONNECTION_REFUSED.name());
            re.append("message", "connection limit reached");
            ArrayList<Document> documents = new ArrayList<>(ServerMain.maximumIncomingConnections);

            if (ServerMain.mode) {
                for (Socket socket : peers) {
                    Document socketDoc = new Document();
                    socketDoc.append("host", getSocketHost(socket));
                    socketDoc.append("port", socket.getPort());
                    documents.add(socketDoc);
                }
            } else {
                for (HostPort udp : ServerMain.udpList) {
                    documents.add(udp.toDoc());
                }
            }
            re.append("peers", documents);
        } else {
            re.append("command", Operation.HANDSHAKE_RESPONSE.name());
            Document hostPort = new Document();
            hostPort.append("host", ServerMain.host);
            hostPort.append("port", ServerMain.mode ? ServerMain.port : ServerMain.udpPort);
            re.append("hostPort", hostPort);


            if (ServerMain.mode) {
                // if established already, return directly
                log.info(ServerMain.peers + " " + ip);
                if (!ServerMain.checkAlreadyConnect(ip)) {
                    Socket newPeer = new Socket(host, port);
                    peers.add(newPeer);
                    new SocketHandler(newPeer, manager, false).start();
                } else {
                    log.info("already in peer, close");
                    // close
                    re.append("command", Operation.INVALID_PROTOCOL.name());
                    re.append("message", "don't repeat to join the network");
                    print(re, output);
                    socket.close();
                    return;
                }
            } else {
                log.info(ServerMain.udpList + " " + ip);
                HostPort udp = new HostPort(ip);
                if (!ServerMain.udpList.contains(udp)) {
                    ServerMain.udpList.add(udp);
                } else {
                    log.info("already in peer, close");
                    // close
                    re.append("command", Operation.INVALID_PROTOCOL.name());
                    re.append("message", "don't repeat to join the network");
                    print(re, ip);
                    return;
                }
            }
        }
        System.out.println("Handshake: " + ip + re.toJson());
        print(re, ServerMain.mode ? output : ip);
    };

    /**
     * Usage
     * build the file bytes request to send
     *
     * @param md5
     * @param lastModified
     * @param fileSize
     * @param pathName
     * @param position
     * @param length
     * @return
     */
    private Document buildFileBytesRequest(String md5, long lastModified, long fileSize, String pathName, long position, long length) {
        this.pathName.add(pathName);
        Document req = new Document();
        req.append("command", Operation.FILE_BYTES_REQUEST.name());
        Document fileDescriptor = new Document();
        fileDescriptor.append("md5", md5);
        fileDescriptor.append("lastModified", lastModified);
        fileDescriptor.append("fileSize", fileSize);
        req.append("fileDescriptor", fileDescriptor);
        req.append("pathName", pathName);
        req.append("position", position);
        req.append("length", length);
        return req;
    }


}