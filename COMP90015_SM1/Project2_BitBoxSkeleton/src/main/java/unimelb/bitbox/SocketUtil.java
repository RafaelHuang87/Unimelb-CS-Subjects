package unimelb.bitbox;

import unimelb.bitbox.util.Document;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;

/**
 * SocketUtil
 * Usage:
 * Define the different functions
 *
 * @author Junjie Huang
 * create time 2019/5/2 - 9:57 PM
 */
public class SocketUtil {
    // ip -> request
    private static Map<String, List<Document>> map = new HashMap<>();

    public static String getSocketHost(Socket socket) {
        return socket.getInetAddress().getHostName();
    }

    public static String getSocketIp(Socket socket) {
        return getSocketHost(socket) + ":" + socket.getPort();
    }

    /**
     * Usage:
     * Print out the peer information
     * @param re
     * @param out
     * @param <T>
     * @throws IOException
     */
    public static <T> void printPeer(Document re, T out) throws IOException {
        String data = re.toJson();
        System.out.println("Print: " + data);

        if (out instanceof BufferedWriter) {
            ((BufferedWriter) out).write(data);
            ((BufferedWriter) out).newLine();
            ((BufferedWriter) out).flush();

        } else if (out instanceof String) {
            String[] ip = ((String) out).split(":");
            ServerMain.udpSocket.send(new DatagramPacket(data.getBytes(), data.getBytes().length, InetAddress.getByName(ip[0]), Integer.parseInt(ip[1])));
        }
    }

    /**
     * Usage:
     * Check for retrying if losing package
     */
    public static void check(){
        Map<String, List<Document>> preMap = map;
        map = new HashMap<>();
        for(Map.Entry<String, List<Document>> entry : preMap.entrySet()){
            for(Document d : entry.getValue()){
                if(System.nanoTime() - d.getLong("time") < 11 * 1000 * 1000){
                    d.remove("time");
                    try {
                        print(d, entry.getKey());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Usage:
     * BufferWriter or put event into out
     * @param re
     * @param out
     * @param <T>
     * @throws IOException
     */
    public static <T> void print(Document re, T out) throws IOException {
        String data = re.toJson();
        System.out.println("Print: " + data);


        if (out instanceof BufferedWriter) {
            ((BufferedWriter) out).write(data);
            ((BufferedWriter) out).newLine();
            ((BufferedWriter) out).flush();

        } else if (out instanceof String) {
            String[] ip = ((String) out).split(":");
            // add to map
            if(re.getString("command").endsWith("REQUEST")){
                List<Document> event = new ArrayList<>();
                if(!map.containsKey(out)){
                    map.put((String) out, event);
                }
                else{
                    event = map.get(out);
                }
                re.append("time", System.nanoTime());
                event.add(re);
            }
            else{
                int len = re.getString("command").length();
                String prefixCommand = re.getString("command").substring(len - 9);
                if(map.containsKey(out)){
                    List<Document> event = map.get(out);
                    for (int i = 0; i < event.size(); i++) {
                        Document cur = event.get(i);
                        if(cur.getString("command").startsWith(prefixCommand)){
                            event.remove(i);
                            break;
                        }
                    }
                }
            }
            ServerMain.udpSocket.send(new DatagramPacket(data.getBytes(), data.getBytes().length, InetAddress.getByName(ip[0]), Integer.parseInt(ip[1])));
        }
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
                if (data.isEmpty()) {
                    break;
                }
            } catch (Exception e) {
                // connection closed or EOF
                e.printStackTrace();
                break;
            }

            Document request = Document.parse(data);
            System.out.println("wait for res: " + request.toJson());
            String command = request.getString("command");
            // empty request, skip
            if (command == null) {
                continue;
            }
            return request;
        }
        return null;
    }

    /**
     * Usage:
     * Append the status to final result
     *
     * @param re
     * @param status
     * @param message
     */
    public static void appendStatusToResult(Document re, boolean status, String message) {
        re.append("status", status);
        re.append("message", message);
    }

}
