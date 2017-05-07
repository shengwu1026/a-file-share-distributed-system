/**
 * This class is for process the command client sends. According to different commands (publish, fetch, query, share, exchange),
 * take different actions. And sends all messages (error or success) to the Communication class.
 * @author Sheng Wu
 * @version 1.0 29/04/2017
 */
package EZShare;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONException; 
import org.apache.wink.json4j.JSONObject;

public class ServerCommandProcessor { 
	private ServerCore core;
	private static ServerCommandProcessor processor;
	private static Logger logger = Logger.getLogger(ServerCommandProcessor.class);
	
	/**
	 * The method is the construction method and gets the server list and resource list.
	 */
	private ServerCommandProcessor() {
		core = ServerCore.getInstance();
	}
	
	/**
	 * This method creates a new resource list and server list if they are not existed.
	 * @return processor a new resource list and server list
	 */
	public static ServerCommandProcessor getInstance() {
		if (processor == null) {
			synchronized (ServerCommandProcessor.class) {
				if (processor == null) {
					processor = new ServerCommandProcessor();
				}			 
			}
		}
		return processor;
	}
	
	/**
	 * The method process the command the client sends to a json object.
	 * And pass the command to different methods
	 * according to different commands.
	 * @param command a json string
	 * @return messages List<Message> 
	 */
	public List<Message> processCommand(String command) {
		List<Message> messages = new ArrayList<Message>();
		try {
			JSONObject jsonObject = new JSONObject(command); 
			String cmd = jsonObject.getString("command");
			switch (cmd) {
			case "PUBLISH": 
				messages.addAll(publish(jsonObject));
				break;
			case "REMOVE": 
				messages.addAll(remove(jsonObject));
				break;
			case "SHARE":
				messages.addAll(share(jsonObject));
				break;
			case "QUERY":
				messages.addAll(query(jsonObject));
				break;
			case "FETCH":
				messages.addAll(fetch(jsonObject));
				break;
			case "EXCHANGE":
				messages.addAll(exchange(jsonObject));
				break;
			default:
				messages.addAll(sendErrorMessage("Invalid Command"));
			}
		} catch (JSONException e) { 
			return sendErrorMessage("missing or incorrect type for command");
		} finally {
			return messages;
		}	
	}

	/**
	 * The method deal with the publish command and sends back the response.
	 * @param jsonObject
	 * @return messages List<Message>
	 */
	private synchronized List<Message> publish(JSONObject jsonObject) {
		if (!jsonObject.has("resource")) 
			return sendErrorMessage("missing resource");
		JSONObject resourceObject = null;
		try {
			resourceObject = jsonObject.getJSONObject("resource");
		} catch (JSONException e) { 
			e.printStackTrace();
		}
		if (!Resource.checkValidity(resourceObject)) 
			return sendErrorMessage("missing resource");
		Resource resource = Resource.parseJson(resourceObject);
		if (resource==null|| resource.getOwner().equals("*"))
			return sendErrorMessage("invalid resource");
		if (!resource.getUri().isAbsolute() || resource.getUri().getScheme().equals("file"))
			return sendErrorMessage("cannot publish resource");
		List<Resource> resources = core.getResources();
		if(resources.stream().anyMatch(re->re.getChannel().equals(resource.getChannel())&&re.getUri().equals(resource.getUri())&&!re.getOwner().equals(resource.getOwner())))
			return sendErrorMessage("cannot publish resource");
		List<Resource> sameResource = resources.stream().filter(re -> re.getChannel().equals(resource.getChannel()) && re.getUri().equals(resource.getUri()) && re.getOwner().equals(resource.getOwner())).collect(Collectors.toList());
		if (sameResource.size() > 0){
			resources.set(resources.indexOf(sameResource.get(0)), resource);
		} else {
			resources.add(resource);
		}
		resources.forEach(re-> logger.debug("Published resource: " + Resource.toJson(re).toString()));  
		return sendSuccessMessage();
	}
	
	/**
	 * The method deal with the remove command and sends back the response.
	 * @param jsonObject
	 * @return messages List<Message>
	 */
	private synchronized List<Message> remove(JSONObject jsonObject) {
		if (!jsonObject.has("resource"))
			return sendErrorMessage("missing resource");
		JSONObject resourceObject = null;
		try {
			resourceObject = jsonObject.getJSONObject("resource");
		} catch (JSONException e) { 
			e.printStackTrace();
		}
		if (!Resource.checkValidity(resourceObject))
			return sendErrorMessage("missing resource");
		Resource resource = Resource.parseJson(resourceObject);
		if (resource == null || resource.getOwner().equals("*"))
			return sendErrorMessage("invalid resource");
		if ( !resource.getUri().isAbsolute())
			return sendErrorMessage("cannot remove resource");
		List<Resource> resources = core.getResources();
		List<Resource> targetList = resources.stream().filter(re -> re.getOwner().equals(resource.getOwner()) && re.getChannel().equals(resource.getChannel()) && re.getUri().equals(resource.getUri())).collect(Collectors.toList());
		if (targetList.size()==0)
			return sendErrorMessage("cannot remove resource");
		resources.remove(targetList.get(0));
		resources.forEach(re -> logger.debug("Remove" + Resource.toJson(re).toString()));
		return sendSuccessMessage();
	}

	/**
	 * The method deal with the share command and sends back the response.
	 * @param jsonObject
	 * @return messages List<Message>
	 */
	private synchronized List<Message> share(JSONObject jsonObject) {
		if (!jsonObject.has("resource")||!jsonObject.has("secret"))
			return sendErrorMessage("missing resource and/or secret");
		try {
			if(!jsonObject.getString("secret").equals(ServerInfo.secret))
				return sendErrorMessage("incorrect secret");
		} catch (JSONException e) {
			e.printStackTrace();
		}
		JSONObject resourceObject = null;
		try {
			resourceObject = jsonObject.getJSONObject("resource");
		} catch (JSONException e) { 
			e.printStackTrace();
		}
		if (!Resource.checkValidity(resourceObject))
			return sendErrorMessage("missing resource");
		Resource resource = Resource.parseJson(resourceObject);
		resource.setServerBean(core.getMyServer());
		if (resource == null || resource.getOwner().equals("*"))
			return sendErrorMessage("invalid resource");
		if (!resource.getUri().isAbsolute() || !resource.getUri().getScheme().equals("file")||resource.getUri().getAuthority()!=null)
			return sendErrorMessage("cannot share resource");
		File file = new File(resource.getUri().getPath());
		if (!file.exists()||!file.isFile()) 
			return sendErrorMessage("cannot share resource");
		List<Resource> resources = core.getResources();
		if (resources.stream().anyMatch(re -> re.getChannel().equals(resource.getChannel()) && re.getUri().equals(resource.getUri()) && !re.getOwner().equals(resource.getOwner())))
			return sendErrorMessage("cannot share resource");
		List<Resource> sameResource = resources.stream().filter(re -> re.getChannel().equals(resource.getChannel()) && re.getUri().equals(resource.getUri()) && re.getOwner().equals(resource.getOwner())).collect(Collectors.toList());
		if (sameResource.size() > 0) {
			resources.set(resources.indexOf(sameResource.get(0)), resource);
		} else {
			resources.add(resource);
		}
		resources.forEach(re -> logger.debug("Shared rescource" + Resource.toJson(re).toString()));
		return sendSuccessMessage();
	}

	/**
	 * The method deal with the query command (if relay == true, the server will propagate the command to other servers and 
	 * set relay field to false, owner and channel to "") then sends back the response.
	 * @param jsonObject
	 * @return messages List<Message>
	 */
	private synchronized List<Message> query(JSONObject jsonObject){
		List<Message> messages = new ArrayList<>();
		if (!jsonObject.has("resourceTemplate")||!jsonObject.has("relay"))
			return sendErrorMessage("missing resourceTemplate");
		boolean relay = true;
		try {
			relay = jsonObject.getBoolean("relay");
		} catch (JSONException e1) { 
			e1.printStackTrace();
		} 
		JSONObject resourceObject = null;
		try {
			resourceObject = jsonObject.getJSONObject("resourceTemplate");
		} catch (JSONException e1) { 
			e1.printStackTrace();
		}
		if (!Resource.checkValidity(resourceObject)) 
			return sendErrorMessage("missing resourceTemplate");
		Resource resource = Resource.parseJson(resourceObject);
		if (resource==null|| resource.getOwner().equals("*"))
			return sendErrorMessage("invalid resourceTemplate");
		List<Resource> resources = core.getResources();
		List<Resource> candidates = new ArrayList<>();
		synchronized (resources){ 
			for (Resource re : resources){
				List<String> queryTags = new ArrayList<>();
				for (String tag : re.getTags()) {
					tag = tag.toLowerCase();
					queryTags.add(tag);
				}
				if (resource.getChannel().equals(re.getChannel()) && 
						((resource.getOwner().equals("") || resource.getOwner().equals(re.getOwner()))) &&
						((resource.getTags().size() == 0 || resource.getTags().stream().map(s -> s.toLowerCase()).allMatch(tag -> queryTags.contains(tag)))) &&
						((resource.getUri().toString().equals("") || resource.getUri().equals(re.getUri()))) &&
						( (resource.getName().equals("") && resource.getDescription().equals("")) ||
								(!resource.getName().equals("") && re.getName().contains(resource.getName()) ) ||
								(!resource.getDescription().equals("") && re.getDescription().contains(resource.getDescription())) )) {
							try {
								Resource candidateResource = re.clone();
								if (!candidateResource.getOwner().equals(""))
									candidateResource.setOwner("*");
								candidateResource.setServerBean(core.getMyServer());
								candidates.add(candidateResource);
							} catch (CloneNotSupportedException e) {
								e.printStackTrace();
							}
						}
			}
		}
		if (relay) {
			List<ServerBean> serverBeans = core.getServerList();
			for (ServerBean serverBean : serverBeans){
				if (serverBean.equals(core.getMyServer())) continue; 
				try {
					jsonObject.put("relay", false);
					JSONObject templateObject = (JSONObject)jsonObject.get("resourceTemplate");
					templateObject.put("owner","");
					templateObject.put("channel","");
				} catch (JSONException e1) { 
					e1.printStackTrace();
				} 
				List<Message> results = core.getServerConnection().establishConnection(serverBean, new Message(MessageType.STRING, jsonObject.toString(), null, null));
				if (results == null || results.size() == 0) continue;
				results.forEach(result -> {
					JSONObject resultObject = null;
					try {
						resultObject = new JSONObject(result.getMessage());
					} catch (JSONException e) { 
						e.printStackTrace();
					}
					if (Resource.checkValidity(resultObject)) {
						Resource externalResource = Resource.parseJson(resultObject);
						candidates.add(externalResource);
					}
				});
			}
		}
		messages.addAll(sendSuccessMessage());
		candidates.forEach(candidate->{
			messages.add(new Message(MessageType.STRING, Resource.toJson(candidate).toString(),null,null));
		});
		messages.add(new Message(MessageType.STRING, "{\"resultSize\":" + candidates.size() + "}",null,null));
		return messages;
	}

	/**
	 * The method deal with the fetch command and sends back the response and file if existed.
	 * @param jsonObject
	 * @return messages List<Message>
	 */
	private List<Message> fetch(JSONObject jsonObject){
		List<Message> messages = new ArrayList<>();
		if (!jsonObject.has("resourceTemplate")) 
			return sendErrorMessage("missing resourceTemplate");
		JSONObject resourceObject = null;
		try {
			resourceObject = jsonObject.getJSONObject("resourceTemplate");
		} catch (JSONException e) { 
			e.printStackTrace();
		}
		if (!Resource.checkValidity(resourceObject)) 
			return sendErrorMessage("missing resourceTemplate");
		Resource resource=Resource.parseJson(resourceObject);
		if (resource == null || !resource.getUri().isAbsolute() || !resource.getUri().getScheme().equals("file") || resource.getUri().getAuthority() != null || resource.getOwner().equals("*"))
			return sendErrorMessage("invalid resourceTemplate");
		List<Resource> resources = core.getResources();
		List<Resource> targetList = resources.stream().filter(re -> re.getChannel().equals(resource.getChannel()) && re.getUri().equals(resource.getUri())).collect(Collectors.toList());
		if (targetList.size() == 0)
			return sendErrorMessage("uri or channel doesn't correspond");
		File file = new File(resource.getUri().getPath());
		if (!file.exists()||!file.isFile())
			return sendErrorMessage("resource doesn't exist");
		resource.setOwner("*");
		resource.setSize(file.length());
		resource.setServerBean(core.getMyServer());
		resourceObject = Resource.toJson(resource);
		messages.addAll(sendSuccessMessage());
		messages.add(new Message(MessageType.STRING,Resource.toJson(resource).toString(),null,null));
		messages.add(new Message(MessageType.FILE,null,null,file));
		messages.add(new Message(MessageType.STRING,"{\"resultSize\":1}",null,null));
		return messages;
	}

	/**
	 * The method deal with the exchange command and sends back the response.
	 * @param jsonObject
	 * @return messages List<Message>
	 */
	private synchronized List<Message> exchange(JSONObject jsonObject) {
		if (!jsonObject.has("serverList"))
			return sendErrorMessage("missing or invalid server list");
		JSONArray serverArray = null;
		try {
			serverArray = jsonObject.getJSONArray("serverList");
		} catch (JSONException e) { 
			e.printStackTrace();
		}
		for (int i = 0; i < serverArray.length(); i++) {
			JSONObject serverObject = null;
			try {
				serverObject = serverArray.getJSONObject(i);
			} catch (JSONException e) { 
				e.printStackTrace();
			}
			if (!serverObject.has("hostname")||!serverObject.has("port")) continue;
			String hostname = "";
			int port = 3000;
			try {
				hostname = serverObject.getString("hostname");
				port = serverObject.getInt("port");
			} catch (JSONException e) {
				e.printStackTrace();
			}
			ServerBean serverBean = new ServerBean(hostname, port);
			if (!core.getServerList().contains(serverBean) && !serverBean.equals(core.getMyServer())) {
				core.getServerList().add(serverBean);
			}
		}
		logger.debug("RECEIVED : " + serverArray.toString());
		return sendSuccessMessage();
	}

	/**
	 * The method adds all error messages into a list. 
	 * @param message String
	 * @return messages List<Message>
	 */	
	private static List<Message> sendErrorMessage(String message){
		List<Message> messages =new ArrayList<>();
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put("response","error");
			jsonObject.put("errorMessage",message);
		} catch (JSONException e) { 
			e.printStackTrace();
		}
		Message response = new Message(jsonObject.toString());
		messages.add(response);
		return messages;
	}

	/**
	 * The method adds all successful messages into a list. 
	 * @return messages List<Message>
	 */	
	private static List<Message> sendSuccessMessage(){
		List<Message> messages = new ArrayList<>();
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put("response", "success");
		} catch (JSONException e) { 
			e.printStackTrace();
		}
		Message message = new Message(jsonObject.toString());
		messages.add(message);
		return messages;
	}

}
