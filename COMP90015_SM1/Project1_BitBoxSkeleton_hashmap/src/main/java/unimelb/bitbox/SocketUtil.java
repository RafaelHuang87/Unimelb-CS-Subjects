package unimelb.bitbox;

import unimelb.bitbox.util.Document;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * SocketUtil
 * Usage:
 *
 * @author heleninsa
 * create time 2019/5/2 - 9:57 PM
 */
public class SocketUtil {

    public static Document generateFaultDoc() {
        Document re = new Document();
        re.append("error", true);
        return re;
    }


    public static String getSocketHost(Socket socket) {
        // return socket.getInetAddress().getHostAddress();
        return socket.getInetAddress().getHostName();
    }

    public static String getSocketIp(Socket socket) {
        // return socket.getInetAddress().getHostAddress();
        return getSocketHost(socket) + ":" + socket.getPort();
    }

    public static void print(Document re, DataOutputStream out) throws IOException {
        out.writeUTF(re.toJson());
        out.flush();
    }

}
