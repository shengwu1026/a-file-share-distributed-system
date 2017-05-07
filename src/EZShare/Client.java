/**
 * This is the main class of client. A client is used to instruct the server to share the files.
 * For example, clients can request a shared file to be downloaded to them. Communications are via 
 * TCP. All messages are in JSON format, except file contents, one JSON message per line. File 
 * contents are transmitted as byte sequences, mixed between JSON messages. Interactions are 
 * synchronous request-reply, with a single request per connection.
 * @author Sheng Wu
 * @version 1.0 29/04/2017
 *
 */

package EZShare;

import java.io.DataInputStream;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException; 
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger; 
import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;
import org.apache.wink.json4j.OrderedJSONObject; 

public class Client {
	// a logger is used to print out information of this class
	private static Logger logger = Logger.getLogger(Client.class);
	// targetServer is the server the client wants to connect
	private ServerBean targetServer;

	/**
	 * The main method will create a command line parser to process the
	 * arguments the user enter in terminal.
	 * @param args the user enter in terminal
	 * @throws invalid command
	 */
	public static void main(String[] args) { 
		Options options = new Options();
		options.addOption("channel", true, "channel");
		options.addOption("debug", false, "print debug information");
		options.addOption("description", true, "resource description");
		options.addOption("exchange", false, "exchange server list with server");
		options.addOption("fetch", false, "fetch resources from server");
		options.addOption("host", true, "server host, a domain name or IP address");
		options.addOption("name", true, "resource name");
		options.addOption("owner", true, "owner");
		options.addOption("port", true, "server port, an integer");
		options.addOption("publish", false, "publish resource on server");
		options.addOption("query", false, "query for resources from server");
		options.addOption("remove", false, "remove resource from server");
		options.addOption("secret", true, "secret");
		options.addOption("servers", true, "server list, host1:port1,host2:port2,...");
		options.addOption("share", false, "share resource on server");
		options.addOption("tags", true, "resource tags, tag1,tag2,tag3,...");
		options.addOption("uri", true, "resource URI"); 

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;

		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) { 
			logger.error("invalid command");
			return;
		} 
		Client client = new Client();
		client.processCommand(cmd);
	}

	/**
	 * The method processes the command and pass it to the corresponding methods.  
	 * @param cmd
	 * @throws invalid port number
	 */
	public void processCommand(CommandLine cmd) {
		// if the user sets debug mode on, it will set the logger level to DEBUG
		// and print out all debug information
		if (cmd.hasOption("debug")) {
			logger.info("setting debug on");
			Level level = Level.toLevel(Level.DEBUG_INT);
			LogManager.getRootLogger().setLevel(level);
		}
		// check if the user entered host and port number
		if (!cmd.hasOption("host") || !cmd.hasOption("port")) {
			logger.error("require host and port");
			return;
		}
		targetServer = null;
		try {
			// check if the port number is valid
			int port = Integer.valueOf(cmd.getOptionValue("port"));
			if (port<0 || port>65535) {
				logger.error("port number not in range (0, 65535)");
				return;
			}
			// create a new server with port and hostname
			targetServer = new ServerBean(cmd.getOptionValue("host"), port);
		} catch (Exception e) {
			logger.error("port number not in range (0, 65535)");
			return;
		}
		// pass the command to the corresponding methods
		if (cmd.hasOption("publish")) {
			publish(cmd); 
		} else if (cmd.hasOption("remove")) {
			remove(cmd);
		} else if (cmd.hasOption("share")) {
			share(cmd);
		} else if (cmd.hasOption("query")) {
			query(cmd);
		} else if (cmd.hasOption("fetch")) {
			fetch(cmd);
		} else if (cmd.hasOption("exchange")) {
			exchange(cmd);
		} else {
			// if the user doesn't enter any command above, issue an error message
			logger.error("missing or incorrect type for command");
			return;
		}
	}

	/**
	 * The method parses command line arguments to a Resource object.
	 * @param cmd
	 * @param requireURI if true, the cmd has to have a uri field
	 * @return a resource object
	 * @throws URI not known exception
	 */
	private Resource parseResourceCmd(CommandLine cmd, boolean requireURI) {
		Resource resource = new Resource();
		// if not require uri 
		// set it to "" if doesn't have a uri field
		// set it to the value if has a uri field
		if(!requireURI) {
			URI uri = null;
			if (cmd.hasOption("uri")) {
				try {
					uri = new URI(cmd.getOptionValue("uri").trim());
					resource.setUri(uri);
				} catch (URISyntaxException e) { 
					e.printStackTrace();
				}				
			} else {
				resource.setUri(uri);
			}
		} else if (requireURI && (!cmd.hasOption("uri") || cmd.getOptionValue("uri").equals(""))) {
			// if require uri but doesn't have one, issue an error message
			logger.error("require uri");
			return null;
		} else { 
			// set the Resource.URI = the value user enters
			URI uri = null;
			try {
				uri = new URI(cmd.getOptionValue("uri").trim());
				resource.setUri(uri);
			} catch (URISyntaxException e) { 
				e.printStackTrace();
			}
		}
		if (cmd.hasOption("owner")) {
			// owner cannot be "*"
			if (cmd.getOptionValue("owner").trim().equals("*")) {
				logger.error("owner cannot be \"*\"");
				return null;
			}
			resource.setOwner(cmd.getOptionValue("owner").trim());
		} else {
			resource.setOwner("");
		}
		if (cmd.hasOption("name")) {
			resource.setName(cmd.getOptionValue("name").trim());
		} else {
			resource.setName("");
		}
		if (cmd.hasOption("channel")) {
			resource.setChannel(cmd.getOptionValue("channel").trim());
		} else {
			resource.setChannel("");
		}
		if (cmd.hasOption("description")) {
			resource.setDescription(cmd.getOptionValue("description").trim());
		} else {
			resource.setDescription("");
		}
		List<String> tagList = new ArrayList<>();
		if (cmd.hasOption("tags")) {
			String[] tags = cmd.getOptionValue("tags").split(",");
			for (int i = 0; i < tags.length; i++) {
				tagList.add(tags[i].trim());
			}
		}
		resource.setTags(tagList);
		return resource;
	}

	/**
	 * The method is to issue a publish command. The publish command is to publish a resource to the server.
	 * Receive response (error or success) from the server and print the message out.
	 * @param cmd
	 */
	private void publish(CommandLine cmd) {
		Resource resource = parseResourceCmd(cmd, true);
		if (resource == null) return;
		OrderedJSONObject jsonObject = new OrderedJSONObject();
		try {
			jsonObject.put("command", "PUBLISH");
			jsonObject.put("resource", Resource.toJson(resource));
		} catch (org.apache.wink.json4j.JSONException e) { 
			e.printStackTrace();
		} 
		String publishCommand = "publishing to " + cmd.getOptionValue("host") + ":" + cmd.getOptionValue("port");
		logger.info(publishCommand); 
		logger.info("SENT: " + jsonObject.toString());
		List<Message> messages = ClientConnection.establishConnection(targetServer, new Message(jsonObject.toString()));
		if (messages != null) {
			for (Message message : messages) { 
				logger.info("RECEIVED: " + message.getMessage());
			} 
		}		
	}

	/**
	 * The method is to issue a remove command. The remove command will remove the resource on the server.
	 * Receive response (error or success) from the server and print the message out.
	 * @param cmd
	 */
	private void remove(CommandLine cmd) {
		Resource resource = parseResourceCmd(cmd, true);
		if (resource == null) return;
		OrderedJSONObject jsonObject = new OrderedJSONObject();
		try {
			jsonObject.put("command", "REMOVE");
			jsonObject.put("resource", Resource.toJson(resource));
		} catch (org.apache.wink.json4j.JSONException e) { 
			e.printStackTrace();
		}  
		String msg = "Removing " + jsonObject.toString();
		logger.info(msg);
		List<Message> messages = ClientConnection.establishConnection(targetServer, new Message(jsonObject.toString()));
		if (messages != null) {
			for (Message message : messages) {
				String revmsg = "RECEIVED: " + message.getMessage();
				logger.info(revmsg);
			} 
		}		
	}

	/**
	 * The method is to issue a share command. The share command will share a file resource to the server.
	 * Receive response (error or success) from the server and print the message out.
	 * @param cmd
	 */
	private void share(CommandLine cmd) {
		if (!cmd.hasOption("secret")) {
			logger.error("require secret");
			return;
		}
		Resource resource = parseResourceCmd(cmd, true);
		if (resource == null) return;
		OrderedJSONObject jsonObject = new OrderedJSONObject();
		try {
			jsonObject.put("command", "SHARE");
			jsonObject.put("secret", cmd.getOptionValue("secret"));
			jsonObject.put("resource", Resource.toJson(resource));
		} catch (org.apache.wink.json4j.JSONException e) { 
			e.printStackTrace();
		}
		String shareCommand = "sharing to " + cmd.getOptionValue("host") + ":" + cmd.getOptionValue("port");
		logger.info(shareCommand);
		String sentmsg = "SENT: " + jsonObject.toString();
		logger.info(sentmsg);
		List<Message> messages = ClientConnection.establishConnection(targetServer, new Message(jsonObject.toString()));
		if (messages != null) {
			for (Message message : messages) {
				String revmsg = "RECEIVED: " + message.getMessage();
				logger.info(revmsg);
			} 
		}		
	}

	/**
	 * The method is to issue a fetch command. The fetch command will download a file from the server.
	 * Receive response (error or success) from the server and print the message out.
	 * @param cmd
	 */
	private void fetch(CommandLine cmd) {
		Resource resource = parseResourceCmd(cmd, true);
		if (resource == null) return;
		OrderedJSONObject jsonObject = new OrderedJSONObject();
		try {
			jsonObject.put("command", "FETCH"); 
			jsonObject.put("resourceTemplate", Resource.toJson(resource));  
		} catch (org.apache.wink.json4j.JSONException e1) { 
			e1.printStackTrace();
		}  
		String fetchCommand = "downloading ";
		logger.info(fetchCommand);
		String sentmsg = "SENT: " + jsonObject.toString();
		logger.info(sentmsg);
		Socket socket = null;
		try {
			socket = new Socket(targetServer.getHostname(), targetServer.getPort());
			DataInputStream inputStream = new DataInputStream(socket.getInputStream());
			DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
			outputStream.writeUTF(jsonObject.toString());
			outputStream.flush();
			if (inputStream.available() > -1) {
				String response = "RECEIVED: " + inputStream.readUTF(); 
				logger.info(response);
				if (response.contains("error"))
					return;
				Long size = (long) 0;  
				String resourceInfoStr = inputStream.readUTF();
				String resourceInfoMsg = "RECEIVED: " + resourceInfoStr;
				logger.info(resourceInfoMsg);
				JSONObject resourceInfo;
				try {
					resourceInfo = new JSONObject(resourceInfoStr); 
					size = resourceInfo.getLong("resourceSize");
				} catch (JSONException e) {	 
					logger.error("no resource existed");
					return;
				}
				String fileName = resource.getUri().getPath().split("/")[resource.getUri().getPath().split("/").length - 1];
				RandomAccessFile file = new RandomAccessFile(fileName, "rw");
				int chunkSize = setChunkSize(size);
				byte[] buffer = new byte[chunkSize];
				int number;
				while ((number = inputStream.read(buffer)) > 0){
					file.write(Arrays.copyOf(buffer,number));
					size -= number;
					chunkSize = setChunkSize(size);
					buffer = new byte[chunkSize];
					if (size==0) {
						file.close();
						break;
					}
				} 
				if (inputStream.available() > 0) {
					String msg = "RECEIVED: " + inputStream.readUTF();
					logger.info(msg);
				}
				inputStream.close();
				outputStream.close();
			}  
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * The method is to issue a query command. The query command is to match the template against
	 * existing resources using some rules. Receive response (error or success) from the server 
	 * and print the message out.
	 * @param cmd
	 */
	private void query(CommandLine cmd) {
		Resource resource = parseResourceCmd(cmd, false); 
		if (resource == null) return;
		OrderedJSONObject jsonObject = new OrderedJSONObject();
		try {
			jsonObject.put("command", "QUERY");
			jsonObject.put("relay", true);
			jsonObject.put("resourceTemplate", Resource.toJson(resource));
		} catch (org.apache.wink.json4j.JSONException e) { 
			e.printStackTrace();
		}
		String queryCommand = "querying ";
		logger.info(queryCommand);
		String sentmsg = "SENT: " + jsonObject.toString();
		logger.info(sentmsg);
		List<Message> messages = ClientConnection.establishConnection(targetServer, new Message(jsonObject.toString()));
		if (messages != null) {
			for (Message message : messages) {
				String revmsg = "RECEIVED: " + message.getMessage();
				logger.info(revmsg);
			}
		}		
	}

	/**
	 * The method is to issue an exchange command. The exchange command will tell a server about a 
	 * list of other servers.And the server can process any valid server and ignore others.
	 * Receive response (error or success) from the server and print the message out.
	 * @param cmd
	 */
	private void exchange(CommandLine cmd) {
		if (!cmd.hasOption("servers")) {
			logger.error("require servers");
			return;
		}
		String[] serverList = cmd.getOptionValue("servers").split(",");
		OrderedJSONObject jsonObject = new OrderedJSONObject();
		try {
			jsonObject.put("command", "EXCHANGE");
		} catch (org.apache.wink.json4j.JSONException e1) { 
			e1.printStackTrace();
		}
		JSONArray serverArray = new JSONArray();
		for (int i = 0; i < serverList.length; i++) {
			OrderedJSONObject serverObject = new OrderedJSONObject();
			String hostname = serverList[i].split(":")[0].trim();
			int port = Integer.valueOf(serverList[i].split(":")[1].trim());
			try {
				serverObject.put("hostname", hostname);
				serverObject.put("port", port);
				serverArray.put(serverObject);
			} catch (org.apache.wink.json4j.JSONException e) { 
				e.printStackTrace();
			}		
		} 
		try {
			jsonObject.put("serverList", serverArray);
		} catch (org.apache.wink.json4j.JSONException e) { 
			e.printStackTrace();
		}
		String sentmsg = "SENT: " + jsonObject.toString();
		logger.info(sentmsg);
		List<Message> messages = ClientConnection.establishConnection(targetServer, new Message(jsonObject.toString()));
		if (messages != null) {
			for (Message message : messages) {
				String revmsg = "RECEIVED: " + message.getMessage();
				logger.info(revmsg);
			} 
		}
	}

	/**
	 * This method is to set chunk size for receiving files.
	 * @param fileSizeRemaining
	 * @return chunksize
	 */
	private static int setChunkSize(long fileSizeRemaining) {
		int chunkSize = 1024 * 1024;
		if (fileSizeRemaining < chunkSize) {
			chunkSize = (int) fileSizeRemaining;
		}
		return chunkSize;
	}
}	
