/**
 * The class is the main class for server. It is responsible for config the server and maintain a resource list and a server list.
 * Server will receive the message client sends and according to different commands (publish, fetch, query, share, exchange),
 * take different actions. The server will issue an exchange command with a random server every X minutes (default 10min) and
 * remove the died (not reachable or a communication error happens) servers from the server list. 
 * then sends back the reply to the client.
 * @author Sheng Wu
 * @version 1.0 29/04/2017
 */

package EZShare; 

import org.apache.commons.cli.*;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class Server {
	private static Logger logger = Logger.getLogger(Server.class);
	
	/**
	 * The main method will create a command line parser to process the
	 * arguments the user enter in terminal.
	 * @param args the user enter in terminal
	 * @throws invalid command
	 */
	public static void main(String[] args) {
		Options options = new Options();
		options.addOption("advertisedhostname", true, "advertised hostname");
		options.addOption("connectionintervallimit", true, "connection interval limit in seconds");
		options.addOption("exchangeinterval", true, "exchange interval in seconds");
		options.addOption("port", true, "server port, an integer");
		options.addOption("secret", true, "secret");
		options.addOption("debug", false, "print debug information");
		
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;
		
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			e.printStackTrace();
			return;
		}
		
		if (cmd.hasOption("advertisedhostname")) {
			try {
				ServerInfo.hostName = cmd.getOptionValue("advertisedhostname");
			} catch (Exception e) {
				logger.error("Host name should be the operating system supplied hostname");
			}	
		}
		
		if (cmd.hasOption("connectionintervallimit")) {
			try {
				ServerInfo.connectionInterval = Integer.parseInt(cmd.getOptionValue("connectionintervallimit")); 
			} catch (Exception e) {
				logger.error("Connection interval should be an integer. Using default connection interval(sec): " + ServerInfo.connectionInterval);
			}	
		} 
		
		if (cmd.hasOption("exchangeinterval")) {
			try {
				ServerInfo.exchangeInterval = Integer.parseInt(cmd.getOptionValue("exchangeinterval"));
			} catch (Exception e) {
				logger.error("Exchange interval should be an integer. Using default exchange interval(sec): " + ServerInfo.exchangeInterval);
			}	
		}
		
		if (cmd.hasOption("port")) {
			int port = Integer.parseInt(cmd.getOptionValue("port"));
			if (port < 0 || port > 65535) {
				logger.error("Port number exceeds range (0, 65535). Using default port number: " + ServerInfo.port);
			} else {
				ServerInfo.port = port;
			}		 
		}
		
		if (cmd.hasOption("secret")) {
			ServerInfo.secret = cmd.getOptionValue("secret");
		}
		// if the user sets debug mode on, it will set the logger level to DEBUG
		// and print out all debug information
		if (cmd.hasOption("debug")) {
			ServerInfo.debug = true;
			logger.info("setting debug on");
			Level level = Level.toLevel(Level.DEBUG_INT);
			LogManager.getRootLogger().setLevel(level);
		}
	
		ServerCore core = ServerCore.getInstance(); 	// create a resource list and a server list
		core.initServer();      //create a thread pool
		core.startServer();	    //open the server socket
	} 
}
