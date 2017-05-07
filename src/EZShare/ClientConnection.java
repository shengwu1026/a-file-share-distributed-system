/**
 * This class in responsible for open the client socket and establish a connection with the server.
 * Once the connection is done, close the socket.
 * @author Sheng Wu
 * @version 1.0 29/04/2017
 */
package EZShare; 

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ClientConnection { 
	/**
	 * The method is to establish a connection with a specific server. Send the message and
	 * receive the messages from the server and return them.
	 * @param serverBean an object with attributes: hostname, address, port
	 * @param message a json string describing what the user enters in terminal
	 * @return messages a list of messages from the server
	 */
	public static List<Message> establishConnection(ServerBean serverBean, Message message) {
		Socket socket = null;
		Message response = null;
		List<Message> messages = new ArrayList<>();
		try {
			socket = new Socket(serverBean.getAddress(), serverBean.getPort());
			socket.setSoTimeout(ServerInfo.timeout * 1000);
			DataInputStream inputStream = new DataInputStream(socket.getInputStream());
			DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
			outputStream.writeUTF(message.getMessage().replaceAll("\0","").trim()); 
			outputStream.flush();
			String data = null;
			while ((data = inputStream.readUTF()) != null) {
				response = new Message(MessageType.STRING, data, null, null);
				messages.add(response);
			}
		} catch (IOException e) {
			// logger.debug("Socket timeout(60s). Lost connection to: " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
		} finally { 
				if (socket != null) {
					try {
						socket.close();
						//logger.debug("Close connection: " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
					} catch (IOException e) { 
						e.printStackTrace();
					}
				}
			}  
			return messages;
		}
}