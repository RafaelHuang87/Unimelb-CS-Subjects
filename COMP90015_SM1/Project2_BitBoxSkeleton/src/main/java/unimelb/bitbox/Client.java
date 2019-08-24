package unimelb.bitbox;

import org.apache.commons.cli.*;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import unimelb.bitbox.util.Document;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.util.*;
import java.util.logging.Logger;

/**
 * Client
 * Usage:
 * Create the client
 * @author Junjie Huang
 * create time 2019/5/16 - 10:36 AM
 */
public class Client {

    private static Logger log = Logger.getLogger(Client.class.getName());

    private static ClientCommand command;
    private static String serverIp;
    private static String peerIp = null;
    private static String identity;
    private static SecretKey secretKey;

    public static void main(String[] args) throws Exception {
        final Options options = new Options();
        final Option commandOption = new Option("c", true, "command");
        final Option severOption = new Option("s", true, "severIp");
        final Option peerOption = new Option("p", true, "peerIp");
        final Option identityOption = new Option("i", true, "identity");
        //Set the peerIp as optional requirement
        peerOption.setRequired(false);
        options.addOption(commandOption);
        options.addOption(severOption);
        options.addOption(peerOption);
        options.addOption(identityOption);
        final CommandLineParser parser = new PosixParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            throw new Exception("command line error", e);
        }
        if(cmd.hasOption("c") && cmd.hasOption("s") && cmd.hasOption("i")) {
            command = ClientCommand.valueOf(cmd.getOptionValue("c"));
            serverIp = cmd.getOptionValue("s").replace("localhost", "127.0.0.1");
            identity = cmd.getOptionValue("i");
            if(cmd.hasOption("p")) {
                peerIp = cmd.getOptionValue("p");
                if(peerIp != null){
                    peerIp.replace("localhost", "127.0.0.1");
                }
            }
        }else {
            System.out.println("Please input correct command");
            System.exit(1);
        }

        monitor();
    }


    /**
     * Monitor client
     */
    private static void monitor() {
        System.out.println("Begin Monitor Client");
        String[] serverAddress = serverIp.split(":");
        try {
            Socket socket = new Socket(serverAddress[0], Integer.parseInt(serverAddress[1]));
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            System.out.println("Input ready");
            Document authRequest = new Document();
            authRequest.append("command", Operation.AUTH_REQUEST.name());
            authRequest.append("identity", identity);
            print(authRequest, output);
            System.out.println("Wait for auth res");
            Document authResponse = waitForRes(input);
            if (authResponse == null) {
               throw new RuntimeException("Invalid response");
            }

            if (!authResponse.getBoolean("status")) {
               log.severe(authResponse.getString("message"));
               return;
            }
            // begin to parse the command -c
            Document commandReq = new Document();
            commandReq.append("command", command.reqName);

            switch (command) {
                case list_peers:
                    break;
                case connect_peer:
                case disconnect_peer:
                    String[] peerAddress = peerIp.split(":");
                    commandReq.append("host", peerAddress[0]);
                    commandReq.append("port", Integer.parseInt(peerAddress[1]));
            }
            File findPrivateKey = new File("bitboxclient_rsa");
            PEMParser pemParser = new PEMParser(new FileReader(findPrivateKey));
            PEMKeyPair pemKeyPair = (PEMKeyPair) pemParser.readObject();
            KeyPair keyPair = new JcaPEMKeyConverter().getKeyPair(pemKeyPair);
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            String AES = authResponse.getString("AES128");
            byte[] temp_sec = Base64.getDecoder().decode(AES.getBytes("UTF-8"));
            secretKey = new SecretKeySpec(cipher.doFinal(temp_sec), "AES");

            // encrypt command
            Document encryptReq = new Document();
            Cipher en_cipher = Cipher.getInstance("AES");
            byte[] wrapped = commandReq.toJson().getBytes();
            en_cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            String encryptedKey = Base64.getEncoder().encodeToString(en_cipher.doFinal(wrapped));
            encryptReq.append("payload", encryptedKey);
            print(encryptReq, output);

            Document encryptRes = waitForRes(input);
            if (encryptRes == null) {
                throw new RuntimeException("Invalid command response");
            }
            String information = null;
            try{
                Cipher de_cipher = Cipher.getInstance("AES");
                de_cipher.init(Cipher.DECRYPT_MODE, secretKey);
                String de_AES = encryptRes.getString("payload");
                if (!de_AES.isEmpty()) {
                    byte[] sec = Base64.getDecoder().decode(de_AES.getBytes("UTF-8"));
                    information = new String(de_cipher.doFinal(sec));
                }
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            log.info(information);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private enum ClientCommand {
        list_peers("LIST_PEERS_REQUEST"),
        connect_peer("CONNECT_PEER_REQUEST"),
        disconnect_peer("DISCONNECT_PEER_REQUEST");

        ClientCommand(String reqName) {
            this.reqName = reqName;
        }

        String reqName;
    }


    public static void print(Document re, BufferedWriter out) throws IOException {
        String data = re.toJson();
        out.write(data);
        out.newLine();
        out.flush();
    }

    /**
     * Wait for response, time-block
     *
     * @param in input
     * @return res/null
     */
    public static Document waitForRes(BufferedReader in) {
        while (true) {
            String data;
            try {
                data = in.readLine();
                System.out.println(data);
                if (data.isEmpty()) {
                    continue;
                }
            } catch (Exception e) {
                // connection closed or EOF
                e.printStackTrace();
                break;
            }

            Document request = Document.parse(data);
            if (request.getString("payload") != null || request.getString("command") != null) {
                return request;
            }
        }
        return null;
    }
}
