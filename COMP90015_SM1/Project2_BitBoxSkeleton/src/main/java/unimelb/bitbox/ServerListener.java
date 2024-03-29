package unimelb.bitbox;

import unimelb.bitbox.util.FileSystemManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * ServerListener
 * Usage:
 * start a listener from other socket
 *
 * @author Junjie Huang
 * create time 30/04/2019
 */
public class ServerListener extends Thread {

    private static Logger log = Logger.getLogger(ServerListener.class.getName());
    private int port;
    private FileSystemManager manager;
    private boolean forControl;

    public ServerListener(int port, FileSystemManager manager) {
        this(port, manager, false);
    }

    public ServerListener(int port, FileSystemManager manager, boolean forControl) {
        this.port = port;
        this.manager = manager;
        this.forControl = forControl;
//        start();
    }

    @Override
    public void run() {
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            // begin to listen to request
            while (true) {
                Socket clientSocket = serverSocket.accept();
                log.info("receive socket: " + clientSocket.getPort());
                // begin to handle the requirement in asy
                if (forControl) {
                    new ClientHandler(clientSocket, manager).start();
                } else {
                    new SocketHandler(clientSocket, manager, false).start();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
