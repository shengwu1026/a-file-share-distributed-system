/**
 * This class is for start a socket for server and create a thread pool for execution.
 * Server socket doesn't close in a normal situation.
 * @author Sheng Wu
 * @version 1.0 29/04/2017
 */

package EZShare;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger; 

public class ServerConnection {
	Logger logger = Logger.getLogger(ServerConnection.class);
	
	private ThreadPoolExecutor executor;
	private Map<String,Long> connectionIntevalInfo;
	
	public ServerConnection() {
		executor = new ThreadPoolExecutor(20, 20, ServerInfo.timeout, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		connectionIntevalInfo = new ConcurrentHashMap<>();
	}
	
	/**
	 * The method handles connection from the client. The server will ensure the time between successive 
	 * connections from any IP addresss be no less than a limit (1 sec by default). If satisfies the condition,
	 * the server puts the thread to the thread pool.
	 * @param serverBean
	 */
	public void handleConnection(ServerBean serverBean) {
		try {
			ServerSocket serverSocket = new ServerSocket(serverBean.getPort());
			while (true) {
				Socket clientSocket = serverSocket.accept();
				clientSocket.setSoTimeout(ServerInfo.timeout * 1000);
				logger.debug("connected to: " + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());
				String ipAddress = clientSocket.getInetAddress().getHostAddress();
				if (!connectionIntevalInfo.containsKey(ipAddress)) {
					connectionIntevalInfo.put(ipAddress,System.currentTimeMillis());
				} else {
					if(System.currentTimeMillis() - connectionIntevalInfo.get(ipAddress) < ServerInfo.connectionInterval * 1000) {
						clientSocket.close();
						logger.debug("Client: " + clientSocket.getInetAddress().getHostAddress() + " violates the connection interval");
						continue;
					}
				}
				connectionIntevalInfo.put(ipAddress, System.currentTimeMillis());
				executor.execute(new Communication(clientSocket));      // receive cmd msg and send reply
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
 
	/**
	 * The method is to establish a connection with a specific server. Send the message and
	 * receive the messages from the server and return them.
	 * @param serverBean an object with attributes: hostname, address, port
	 * @param message a json string describing what the user enters in terminal
	 * @return messages a list of messages from the server
	 */
	public List<Message> establishConnection(ServerBean serverBean, Message message) {
		Socket socket = null;
		Message response = null;
		List<Message> messages = new ArrayList<>();
		try {
			socket = new Socket(serverBean.getAddress(), serverBean.getPort());
			socket.setSoTimeout(ServerInfo.timeout * 1000);
			DataInputStream inputStream = new DataInputStream(socket.getInputStream());
			DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
			outputStream.writeUTF(message.getMessage());
			outputStream.flush();
			String data = null; 
			while (inputStream.available() > -1) { 
				data = inputStream.readUTF(); 
				logger.debug("Received: " + data);
				response = new Message(MessageType.STRING, data, null, null);
				messages.add(response);
			} 
		} catch (IOException e) {
			
		} finally { 
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) { 
					logger.error(e.getMessage());
				}
			}
		}  
		return messages;
	}
}














