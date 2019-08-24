package unimelb.bitbox;


import unimelb.bitbox.util.*;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.logging.Logger;


import static unimelb.bitbox.SocketUtil.*;


/**
 * ClientHandler
 * Usage:
 * The options of sever
 * @author Junjie Huang
 * create time 2019/5/16 - 11:56 AM
 */
public class ClientHandler extends Thread {

    private static Logger log = Logger.getLogger(ClientHandler.class.getName());

    private Socket socket;

    private FileSystemManager manager;

    public static Map<String, String> authorizedKeys = new HashMap<>();

    private BufferedWriter output;
    public static SecretKey secretKey;
    /**
     * Usage:
     * Operation Table
     */
    private Map<Operation, OperationMonitor> monitorTable = new HashMap<>();

    private static RSAPublicKey publicKey;

    public ClientHandler(Socket socket, FileSystemManager manager) {
        this.socket = socket;
        this.manager = manager;
        monitorTable.put(Operation.AUTH_REQUEST, authReq);
        monitorTable.put(Operation.LIST_PEERS_REQUEST, listPeersReq);
        monitorTable.put(Operation.CONNECT_PEER_REQUEST, connectPeerReq);
        monitorTable.put(Operation.DISCONNECT_PEER_REQUEST, disconnectPeerReq);

        // table driven
        ArrayList<String> keyList = new ArrayList<>();
        String keys = Configuration.getConfigurationValue("authorized_keys");
        for (String id : keys.split(",")) {
            String parts[] = id.split("\\s+");
            System.out.println(Arrays.toString(parts));
            authorizedKeys.put(parts[2], id);
        }
    }

    @Override
    public void run() {
        // establish stream
        if (socket == null) {
            return;
        }
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            while (true) {
                String data;
                try {
                    data = input.readLine();
                    if (data == null || data.isEmpty()) {
                        continue;
                    }
                } catch (Exception e) {
                    // connection closed or EOF
                    e.printStackTrace();
                    break;
                }
                System.out.println("Client Data: " + data);
                Document request = Document.parse(data);
                //Decrypt by using secretKey
                if (request.getString("payload") != null) {
                    try{
                        Cipher cipher = Cipher.getInstance("AES");
                        cipher.init(Cipher.DECRYPT_MODE, secretKey);
                        String AES = request.getString("payload");
                        byte[] temp_sec = Base64.getDecoder().decode(AES.getBytes("UTF-8"));
                        request = Document.parse(new String(cipher.doFinal(temp_sec)));
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    } catch (NoSuchPaddingException e) {
                        e.printStackTrace();
                    } catch (InvalidKeyException e) {
                        e.printStackTrace();
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }

                }
                String command = request.getString("command");
                // empty request, skip
                if (command == null) {
                    continue;
                }
                log.info(data);

                // map string to specific operation
                Operation operation = Operation.valueOf(command);
                OperationMonitor monitor = monitorTable.get(operation);
                monitor.run(request);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Usage:
     * For authorize request
     */
    private OperationMonitor authReq = request -> {
        Document re = new Document();
        re.append("command", Operation.AUTH_RESPONSE.name());
        String identity = request.getString("identity");
        //Find identity
        System.out.println("Client: Check Identity");
        if (!authorizedKeys.containsKey(identity)) {
            appendStatusToResult(re, false, "public key not found");
            print(re, output);
            return;
        }
        publicKey = CertificateUtils.parseSSHPublicKey(authorizedKeys.get(identity));
        //Create a random AES128
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(128);
        secretKey = keyGen.generateKey();
        //Encrypt by using publicKey
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        byte[] wrapped = secretKey.getEncoded();
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        String encryptedKey = Base64.getEncoder().encodeToString(cipher.doFinal(wrapped));
        re.append("AES128", encryptedKey);
        appendStatusToResult(re, true, "public key found");
        print(re, output);
    };

    /**
     * Usage:
     * For list_peer request
     */
    private OperationMonitor listPeersReq = request -> {
        Document re = new Document();
        re.append("command", Operation.LIST_PEERS_RESPONSE.name());
        ArrayList<HostPort> peers;
        if (unimelb.bitbox.ServerMain.mode) {
            peers = new ArrayList<>();
            for (Socket sc : unimelb.bitbox.ServerMain.peers) {
                peers.add(new HostPort(SocketUtil.getSocketHost(sc), sc.getPort()));
            }
        } else {
            peers = new ArrayList<>(unimelb.bitbox.ServerMain.udpList);
        }
        re.append("peers", peers);
        print(encryptDoc(re), output);
    };

    /**
     * Usage:
     * For connect_peer request
     */
    private OperationMonitor connectPeerReq = request -> {
        String host = request.getString("host");
        long port = request.getLong("port");
        HostPort hostPort = new HostPort(host, Math.toIntExact(port));

        Document re = new Document();
        re.append("command", Operation.CONNECT_PEER_RESPONSE.name());

        appendStatusToResult(re, true, "connected to peer");
        if ((unimelb.bitbox.ServerMain.mode && unimelb.bitbox.ServerMain.checkAlreadyConnect(host + ":" + port)) || (!unimelb.bitbox.ServerMain.mode && unimelb.bitbox.ServerMain.udpList.contains(hostPort))) {
            // already in
            print(re, output);
            return;
        }
        Document handshakeReq = SocketHandler.generateHandshakeReq();
        if (unimelb.bitbox.ServerMain.mode) {
            // unable to start new thread to listen it, only synchronize is avail for result
            Socket peer = new Socket(host, Math.toIntExact(port));
            BufferedReader input = new BufferedReader(new InputStreamReader(peer.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter peerOutput = new BufferedWriter(new OutputStreamWriter(peer.getOutputStream(), StandardCharsets.UTF_8));
            print(handshakeReq, peerOutput);
            Document response = waitForRes(input);
            if (response == null || response.getBoolean("status")) {
                appendStatusToResult(re, false, "failed");
            } else {
                new SocketHandler(peer, manager, false).start();
            }
        } else {
            unimelb.bitbox.ServerMain.udpList.add(hostPort);
            print(handshakeReq, host + ":" + port);
        }
        print(encryptDoc(re), output);
    };

    /**
     * Usage:
     * For disconnect_peer request
     */
    private OperationMonitor disconnectPeerReq = request -> {
        String host = request.getString("host");
        int port = Math.toIntExact(request.getLong("port"));
        HostPort hostPort = new HostPort(host, port);

        Document re = new Document();
        re.append("command", Operation.DISCONNECT_PEER_RESPONSE.name());
        appendStatusToResult(re, true, "disconnected from peer");

        Document disconnectRequest = new Document();
        disconnectRequest.append("command", Operation.DISCONNECT_PEER_REQUEST.name());
        disconnectRequest.append("host", unimelb.bitbox.ServerMain.host);
        disconnectRequest.append("port", unimelb.bitbox.ServerMain.mode ? unimelb.bitbox.ServerMain.port : unimelb.bitbox.ServerMain.udpPort);

        if (unimelb.bitbox.ServerMain.mode) {
            if (unimelb.bitbox.ServerMain.checkAlreadyConnect(host + ":" + port)) {
                Socket peer = new Socket(host, port);
                BufferedWriter peerOutput = new BufferedWriter(new OutputStreamWriter(peer.getOutputStream(), StandardCharsets.UTF_8));
                print(disconnectRequest, peerOutput);
                // don't care about response
                unimelb.bitbox.ServerMain.removeWithIp(host + ":" + port);
            } else {
                appendStatusToResult(re, false, "connection not active");
            }
        } else {
            if (unimelb.bitbox.ServerMain.udpList.contains(hostPort)) {
                print(disconnectRequest, host + ":" + port);
                unimelb.bitbox.ServerMain.udpList.remove(hostPort);
            } else {
                appendStatusToResult(re, false, "connection not active");
            }
        }
        print(encryptDoc(re), output);
    };

    /**
     * Usage:
     * For encrypting the Document
     * @param re
     * @return
     */
    private Document encryptDoc(Document re) {
        Document enc = new Document();
        try {
            Cipher en_cipher = Cipher.getInstance("AES");
            byte[] wrapped = re.toJson().getBytes();
            en_cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            String encryptedKey = Base64.getEncoder().encodeToString(en_cipher.doFinal(wrapped));
            enc.append("payload", encryptedKey);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return enc;
    }

}
