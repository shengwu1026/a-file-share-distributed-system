/**
 * This class is responsible for the core functionality of the server.
 * It will create a resource list and a server list and maintain it for the server.
 * And it is in charge of creating listening and exchanging threads.
 * Server will exchange the server list with a random server every X minutes (default 10min).
 * @author Sheng Wu
 * @version 1.0 29/04/2017
 */

package EZShare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;
import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;
import org.apache.wink.json4j.OrderedJSONObject;

public class ServerCore {
	private int status;
	private ServerBean myServer;
	private ServerConnection serverConnection;
	private List<Resource> resources;
	private List<ServerBean> serverList;
	private static ServerCore serverCore;
	Logger logger = Logger.getLogger(ServerCore.class); 
	
	private ServerCore() {
		resources= Collections.synchronizedList(new ArrayList<>());
		serverList = Collections.synchronizedList(new ArrayList<>());
	}
	
	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public ServerBean getMyServer() {
		return myServer;
	}

	public void setMyServer(ServerBean myServer) {
		this.myServer = myServer;
	}
	
	public ServerConnection getServerConnection() {
		return serverConnection;
	}

	public void setServerConnection(ServerConnection serverConnection) {
		this.serverConnection = serverConnection;
	}

	public List<Resource> getResources() {
		return resources;
	}

	public void setResources(List<Resource> resources) {
		this.resources = resources;
	}

	public List<ServerBean> getServerList() {
		return serverList;
	}

	public void setServerList(List<ServerBean> serverList) {
		this.serverList = serverList;
	}
	
	public static ServerCore getInstance() {
		if (serverCore == null) {
			synchronized (ServerCore.class) {
				if (serverCore == null) {
					serverCore = new ServerCore();	
				}
			}
		}
		return serverCore;
	}

	/**
	 * The method initiates the server and print out the server information in terminal.
	 * It adds its information to the server list for exchanging and creates a thread pool.
	 */
	public void initServer() {
		this.myServer = new ServerBean(ServerInfo.hostName, ServerInfo.port);
		serverList.add(myServer);
		logger.info("Starting the EZShare Server");
		logger.info("using secret: " + ServerInfo.secret);
		logger.info("using advertised hostname: " + ServerInfo.hostName);
		logger.info("bound to port: " + ServerInfo.port);
		logger.info("started ");
		serverConnection = new ServerConnection(); // create a thread pool
	} 
	
	/**
	 * The method opens the server socket and creates two threads, one for listening incoming 
	 * client sockets and one for exchanging the server list with a random server.
	 */
	public void startServer() {
		Thread listenThread = new Thread(new Runnable() { 
			public void run() {
				logger.debug("Server socket is open");
				serverConnection.handleConnection(myServer);    // open a socket and start to connect
			}
		});
		
		Thread exchangeThread = new Thread(new Runnable()  { 
			public void run() {
				exchangeServers();
			}		
		});
		listenThread.start();  // calls the run method
		exchangeThread.start();
	}
	
	/**
	 * The method issues an exchange command with a random server and provides it with a copy
	 * of its entire server records. If the selected server is not reachable or a communication 
	 * error occurs, then the selected server is removed from the server list and no further 
	 * action is taken in this round.
	 */
	private void exchangeServers() {
		logger.debug("start exchange servers");
		while(true) {
			try {
				Thread.sleep(ServerInfo.exchangeInterval * 1000);   //milliseconds
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (serverList.size() == 0) continue;
			Random random = new Random();
			List<ServerBean> diedServer = new ArrayList<>();
			JSONArray serverArray = new JSONArray();
			synchronized (serverList) {
				serverList.forEach(server -> {
					JSONObject serverObject = new JSONObject();
					try {
						serverObject.put("hostname", server.getHostname());
						serverObject.put("port", server.getPort());
					} catch (JSONException e) { 
						e.printStackTrace();
					}
					serverArray.add(serverObject);
				});
			} 
			int r = random.nextInt(serverList.size()); 
			OrderedJSONObject messageObject = new OrderedJSONObject();
			try { 
				messageObject.put("command", "EXCHANGE");
				messageObject.put("serverList", serverArray);
			} catch (JSONException e) { 
				e.printStackTrace();
			}
			Message message = new Message(MessageType.STRING, messageObject.toString(), null, null); // a copy of server list
			List<Message> messages = serverConnection.establishConnection(serverList.get(r), message);  // issue an exchange cmd
			if (messages.size() == 0) {
				diedServer.add(serverList.get(r));
			} else {
				OrderedJSONObject resultObject;
				try {
					resultObject = new OrderedJSONObject(messages.get(0).getMessage());
					if (!resultObject.containsKey("response") ||
							(resultObject.containsKey("response") && !resultObject.get("response").equals("success")))
						diedServer.add(serverList.get(r));
				} catch (JSONException e) {
					e.printStackTrace();
				}				
			} 
			
			synchronized (serverList) {
				serverList.removeAll(diedServer);
			}
			logger.debug("current servers:" + serverList);
		}
	}
}












