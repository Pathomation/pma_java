package com.pathomation;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Intended for PMA.core interactions related to administrative operations. Does
 * NOT apply to PMA.start / PMA.core.lite
 * 
 * @author Yassine Iddaoui
 *
 */
public class CoreAdmin {

	/**
	 * This method is used to determine whether the Java SDK runs in debugging mode
	 * or not. When in debugging mode (flag = true), extra output is produced when
	 * certain conditions in the code are not met
	 * 
	 * @param flag Debugging mode (activated or deactivated)
	 */
	public static void setDebugFlag(boolean flag) {
		PMA.setDebugFlag(flag);
		if (flag) {
			System.out.println(
					"Debug flag enabled. You will receive extra feedback and messages from the Java SDK (like this one)");
			if (PMA.logger != null) {
				PMA.logger.severe(
						"Debug flag enabled. You will receive extra feedback and messages from the Java SDK (like this one)");
			}
		}
	}

	/**
	 * This method is used to define which content will be received "XML" or "Json"
	 * for "Admin" Web service calls
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 *                <p>
	 *                xml : Second optional argument(Boolean), default value(true),
	 *                define if method will return XML or Json content
	 *                </p>
	 * @return Adds sequence to the url to specify which content to be received (XML
	 *         or Json)
	 */
	private static String adminUrl(Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		Boolean xml = false;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("adminUrl() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Boolean) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("adminUrl() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			xml = (Boolean) varargs[1];
		}
		// let's get the base URL first for the specified session
		String url;
		try {
			url = Core.pmaUrl(sessionID);
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			url = null;
		}
		if (url == null) {
			// sort of a hopeless situation; there is no URL to refer to
			return null;
		}
		// remember, _pma_url is guaranteed to return a URL that ends with "/"
		if (xml) {
			return PMA.join(url, "admin/xml/");
		} else {
			return PMA.join(url, "admin/json/");
		}
	}

	/**
	 * This method is under construction
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                method : First optional argument(String), default value(""),
	 *                method
	 *                </p>
	 *                <p>
	 *                url : Second optional argument(String), default value(null),
	 *                url
	 *                </p>
	 *                <p>
	 *                session : Third optional argument(String), default
	 *                value(null), Session ID
	 *                </p>
	 * @throws Exception If something goes wrong
	 */
	public static void checkForPMAStart(String... varargs) throws Exception {
		// setting the default values when arguments' values are omitted
		String method = varargs.length > 0 ? varargs[0] : "";
		String url = varargs.length > 1 ? varargs[1] : null;
		String session = varargs.length > 2 ? varargs[2] : null;
		if (Core.getPmaCoreLiteSessionID().equals(session)) {
			if (PMA.logger != null) {
				PMA.logger.severe("PMA.start doesn't support " + method);
			}
			throw new Exception("PMA.start doesn't support " + method);
		} else if (url.equals(Core.getPmaCoreLiteSessionID())) {
			if (Core.isLite()) {
				if (PMA.logger != null) {
					PMA.logger.severe("PMA.core.lite found running, but doesn't support an administrative back-end");
				}
				throw new Exception("PMA.core.lite found running, but doesn't support an administrative back-end");
			} else {
				if (PMA.logger != null) {
					PMA.logger.severe(
							"PMA.core.lite not found, and besides; it doesn't support an administrative back-end anyway");
				}
				throw new Exception(
						"PMA.core.lite not found, and besides; it doesn't support an administrative back-end anyway");
			}
		}
	}

	/**
	 * This method is used to cache results from requested URLs (POST method)
	 * 
	 * @param url  URL to request
	 * @param data JSON input
	 * @return Data returned following a request to a specific URL
	 */
	public static String httpPost(String url, String data) {
		if (PMA.debug) {
			System.out.println("Posting to " + url);
			System.out.println("with paylod " + data);
		}
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("POST");
			con.setRequestProperty("Content-Type", "application/json");
			con.setRequestProperty("Accept", "application/json");
			con.setUseCaches(false);
			con.setDoOutput(true);
			OutputStream os = con.getOutputStream();
			os.write(data.getBytes("UTF-8"));
			os.close();
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (PMA.debug && jsonString.contains("Code")) {
				System.out.println(jsonString);
			} else {
				PMA.clearURLCache();
			}
			return jsonString;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to authenticate &amp; connect as an admin to a PMA.core
	 * instance using admin credentials
	 *
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                pmacoreURL : First optional argument(String), default
	 *                value(Class field pmaCoreLiteURL), url of PMA.core instance
	 *                </p>
	 *                <p>
	 *                pmacoreUsername : Second optional argument(String), default
	 *                value(""), username for PMA.core instance
	 *                </p>
	 *                <p>
	 *                pmacorePassword : Third optional argument(String), default
	 *                value(""), password for PMA.core instance
	 *                </p>
	 * @return session's ID if session was created successfully, otherwise null
	 * @throws Exception If target instance isn't a PMA.core instance
	 */
	public static String adminConnect(String... varargs) throws Exception {
		// setting the default values when arguments' values are omitted
		String pmaCoreURL = varargs.length > 0 ? varargs[0] : Core.getPmaCoreLiteURL();
		String pmaCoreAdmUsername = varargs.length > 1 ? varargs[1] : "";
		String pmaCoreAdmPassword = varargs.length > 2 ? varargs[2] : "";
		// Attempt to connect to PMA.core instance; success results in a SessionID
		// only success if the user has administrative status
		checkForPMAStart("adminConnect", pmaCoreURL);

		// purposefully DON'T use helper function apiUrl() here:
		// why? Because apiUrl() takes session information into account (which we
		// don't have yet)
		String url = PMA.join(pmaCoreURL, "admin/json/AdminAuthenticate?caller=SDK.Java");
		if (!pmaCoreAdmUsername.equals("")) {
			url = url.concat("&username=").concat(PMA.pmaQ(pmaCoreAdmUsername));
		}
		if (!pmaCoreAdmPassword.equals("")) {
			url = url.concat("&password=").concat(PMA.pmaQ(pmaCoreAdmPassword));
		}
		try {
			String jsonString = PMA.httpGet(url, "application/json");
			String admSessionID;
			if (PMA.isJSONObject(jsonString)) {
				JSONObject loginResult = PMA.getJSONObjectResponse(jsonString);
				if (loginResult.get("Success").toString().toLowerCase().equals("true")) {
					admSessionID = loginResult.getString("SessionId");
					Core.getPmaSessions().put(admSessionID, pmaCoreURL);
					Core.getPmaUsernames().put(admSessionID, pmaCoreAdmUsername);
					if (!Core.getPmaSlideInfos().containsKey(admSessionID)) {
						Core.getPmaSlideInfos().put(admSessionID, new HashMap<String, Object>());
					}
					Core.getPmaAmountOfDataDownloaded().put(admSessionID, loginResult.length());
				} else {
					admSessionID = null;
				}
			} else {
				admSessionID = null;
			}
			return admSessionID;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to disconnect an admin session from a PMA.core instance
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return True if successfully disconnected, false otherwise
	 */
	public static boolean adminDisconnect(String... varargs) {
		// setting the default value when argument's value is omitted
		String admsessionID = varargs.length > 0 ? varargs[0] : null;
		return Core.disconnect(admsessionID);
	}

	/**
	 * This method is used to create a new user on PMA.core
	 * 
	 * @param admSessionID admin session ID
	 * @param login        login
	 * @param firstName    user's first name
	 * @param lastName     user's last name
	 * @param email        user's email
	 * @param pwd          user's password
	 * @param varargs      Array of optional arguments
	 *                     <p>
	 *                     canAnnotate : First optional argument(Boolean), default
	 *                     value(false), Defines whether the user can annotate
	 *                     slides or not
	 *                     </p>
	 *                     <p>
	 *                     isAdmin : Second optional argument(Boolean), default
	 *                     value(false), Defines whether the user is an
	 *                     administrator or not
	 *                     </p>
	 *                     <p>
	 *                     isSuspended : Second optional argument(Boolean), default
	 *                     value(false), Defines whether the user is suspended or
	 *                     not
	 *                     </p>
	 * @return True if the creation has succeeded, false otherwise
	 */
	public static boolean addUser(String admSessionID, String login, String firstName, String lastName, String email,
			String pwd, Boolean... varargs) {
		// setting the default value when argument's value is omitted
		Boolean canAnnotate = varargs.length > 0 ? varargs[0] : false;
		Boolean isAdmin = varargs.length > 1 ? varargs[1] : false;
		Boolean isSuspended = varargs.length > 2 ? varargs[2] : false;
		System.out.println("Using credentials from " + admSessionID);

		try {
			String url = adminUrl(admSessionID, false) + "CreateUser";
			String input = "{" + "\"sessionID\": \"" + admSessionID + "\"," + "\"user\": {" + "\"Login\": \"" + login + "\","
					+ "\"FirstName\": \"" + firstName + "\"," + "\"LastName\": \"" + lastName + "\"," + "\"Password\": \"" + pwd
					+ "\"," + "\"Email\": \"" + email + "\"," + "\"Administrator\": " + isAdmin + "," + "\"isSuspended\": "
					+ isSuspended + "," + "\"CanAnnotate\": " + canAnnotate + "}" + "}";
			
			String jsonString = httpPost(url, input);
			if (PMA.isJSONObject(jsonString) && PMA.getJSONObjectResponse(jsonString).has("Code")) {
				if (PMA.debug) {
					System.out.println(jsonString);
				}
				return false;
			}
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return false;
		}
	}

	/**
	 * This method is used to check if a user exists
	 * 
	 * @param admSessionID admin session ID
	 * @param query        keyword for the User to search for
	 * @return True if the user exists, false otherwise
	 */
	public static boolean userExists(String admSessionID, String query) {
		String url = adminUrl(admSessionID, false) + "SearchUsers?source=Local" + "&SessionID=" + PMA.pmaQ(admSessionID)
				+ "&query=" + PMA.pmaQ(query);
		try {
			String jsonString = PMA.httpGet(url, "application/json");
			if (PMA.isJSONArray(jsonString)) {
				JSONArray results = PMA.getJSONArrayResponse(jsonString);
				for (int i = 0; i < results.length(); i++) {
					if (results.optJSONObject(i).optString("Login").toLowerCase().equals(query.toLowerCase())) {
						return true;
					}
				}
				return false;
			} else {
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return false;
		}
	}

	/**
	 * This method is used to reset a user's password
	 * 
	 * @param sessionID   session ID
	 * @param username    username to update
	 * @param newPassword new password
	 * @return True if password reset successfully, false otherwise
	 */
	public static boolean resetPassword(String sessionID, String username, String newPassword) {
		String url = adminUrl(sessionID, false) + "ResetPassword?sessionID=" + sessionID + "&username=" + username
				+ "&newPassword=" + newPassword;
		try {
			String jsonString = PMA.httpGet(url, "application/json");
			if (PMA.isJSONObject(jsonString) && PMA.getJSONObjectResponse(jsonString).has("Code")) {
				if (PMA.debug) {
					System.out.println(jsonString);
				}
				return false;
			}
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return false;
		}
	}

	/**
	 * This method is used to create a new directory on PMA.core
	 * 
	 * @param admSessionID an admin session ID
	 * @param path         path to create the new directory in
	 * @return true if directory was created successfully, false otherwise
	 */
	public static boolean createDirectory(String admSessionID, String path) {
		try {
			// we create folders on PMA.core only
			if (Core.isLite(Core.pmaUrl(admSessionID))) {
				return false;
			} else if (Core.getSlides(path, admSessionID) != null) {
				String url = adminUrl(admSessionID, false) + "CreateDirectory";
				String input = "{ \"sessionID\": \"" + admSessionID + "\", \"path\": \"" + path + "\" }";
				String jsonString = httpPost(url, input);
				return jsonString.equals("true") ? true : false;
			} else {
				if (PMA.debug) {
					System.out.println("Directory already exists");
				}
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return false;
		}
	}

	/**
	 * This method is used to rename a directory on PMA.core
	 * 
	 * @param admSessionID an admin session ID
	 * @param originalPath Old path
	 * @param newName      New name
	 * @return true if directory was renamed successfully, false otherwise
	 */
	public static boolean renameDirectory(String admSessionID, String originalPath, String newName) {
		try {
			// we rename folders on PMA.core only
			if (Core.isLite(Core.pmaUrl(admSessionID))) {
				return false;
			}
			String url = adminUrl(admSessionID, false) + "RenameDirectory";
			String payload = "{ \"sessionID\": \"" + admSessionID + "\", \"path\": \"" + originalPath
					+ "\", \"newName\":\"" + newName + "\" }";
			String jsonString = httpPost(url, payload);
			if (PMA.isJSONObject(jsonString) && PMA.getJSONObjectResponse(jsonString).has("Code")) {
				if (PMA.debug) {
					System.out.println(jsonString);
				}
				return false;
			}
			// Sanity check : no slides should be found anymore in the original directory
			if (Core.getSlides(originalPath, admSessionID) == null) {
				// This means the original directory is no longer available. So that's GOOD :-)
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return false;
		}
	}

	/**
	 * This method is used to delete a directory on PMA.core
	 * 
	 * @param admSessionID an admin session ID
	 * @param path         path of the directory to delete
	 * @return true if directory was successfully deleted, false otherwise
	 */
	public static boolean deleteDirectory(String admSessionID, String path) {
		try {
			// we delete folders on PMA.core only
			if (Core.isLite(Core.pmaUrl(admSessionID))) {
				return false;
			}
			String url = adminUrl(admSessionID, false) + "DeleteDirectory";
			String payload = "{ \"sessionID\": \"" + admSessionID + "\", \"path\": \"" + path + "\" }";
			httpPost(url, payload);
			String jsonString = httpPost(url, payload);
			if (PMA.isJSONObject(jsonString) && PMA.getJSONObjectResponse(jsonString).has("Code")) {
				if (PMA.debug) {
					System.out.println(jsonString);
				}
				return false;
			}
			// Sanity check : no slides should be found anymore in the original directory
			if (Core.getSlides(path, admSessionID) == null) {
				// This means the original directory is no longer available. So that's GOOD :-)
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return false;
		}
	}
	
	/**
	 * This method is used to rename a slide on PMA.core
	 * 
	 * @param admSessionID an admin session ID
	 * @param originalPath Old path
	 * @param newName      New name
	 * @return true if slide was renamed successfully, false otherwise
	 */
	public static boolean renameSlide(String admSessionID, String originalPath, String newName) {
		try {
			// we rename slides on PMA.core only
			if (Core.isLite(Core.pmaUrl(admSessionID))) {
				return false;
			}
			String url = adminUrl(admSessionID, false) + "RenameSlide";
			String payload = "{ \"sessionID\": \"" + admSessionID + "\", \"path\": \"" + originalPath
					+ "\", \"newName\":\"" + newName + "\" }";
			String jsonString = httpPost(url, payload);
			if (PMA.isJSONObject(jsonString) && PMA.getJSONObjectResponse(jsonString).has("Code")) {
				if (PMA.debug) {
					System.out.println(jsonString);
				}
				return false;
			} else if (jsonString.equals("true")) {
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return false;
		}
	}
	

	/**
	 * This method is used to delete a slide on PMA.core
	 * 
	 * @param admSessionID an admin session ID
	 * @param path         path of the slide to delete
	 * @return true if slide was successfully deleted, false otherwise
	 */
	public static boolean deleteSlide(String admSessionID, String path) {
		try {
			// we rename folders on PMA.core only
			if (Core.isLite(Core.pmaUrl(admSessionID))) {
				return false;
			}
			String url = adminUrl(admSessionID, false) + "DeleteSlide";
			String payload = "{ \"sessionID\": \"" + admSessionID + "\", \"path\": \"" + path + "\" }";
			String jsonString = httpPost(url, payload);
			if (PMA.isJSONObject(jsonString) && PMA.getJSONObjectResponse(jsonString).has("Code")) {
				if (PMA.debug) {
					System.out.println(jsonString);
				}
				return false;
			}
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return false;
		}
	}	
	
	/**
	 * This method is used to create an Amazon S3 mounting point. A list of these is
	 * to be used to supply method create_root_directory()
	 * 
	 * @param accessKey  AWS Access key
	 * @param secretKey  AWS secret key
	 * @param path       path
	 * @param instanceId instance ID
	 * @param varargs    Array of optional arguments
	 *                   <p>
	 *                   chunkSize : First optional argument(Integer), default
	 *                   value(1048576), chunk size
	 *                   </p>
	 *                   <p>
	 *                   serviceUrl : Second optional argument(String), default
	 *                   value(null), service URL
	 *                   </p>
	 * @return Amazon S3 mounting point
	 */
	public static String createAmazons3MountingPoint(String accessKey, String secretKey, String path,
			Integer instanceId, Object... varargs) {
		// setting the default values when arguments' values are omitted
		Integer chunkSize = 1048576;
		String serviceUrl = null;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("createAmazons3MountingPoint() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			chunkSize = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof String) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("createAmazons3MountingPoint() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			serviceUrl = (String) varargs[1];
		}
		String s3MountingPoint = "{" + "\"AccessKey\": \"" + accessKey + "\"," + "\"SecretKey\": \"" + secretKey + "\","
				+ (chunkSize == 1048576 ? "" : "\"ChunkSize\": " + chunkSize + ",")
				+ (serviceUrl == null ? "" : "\"ServiceUrl\": \"" + serviceUrl + "\",") + "\"Path\": \"" + path + "\","
				+ "\"InstanceId\": " + instanceId + "}";
		return s3MountingPoint;
	}

	/**
	 * This method is used to create an FileSystem mounting point. A list of these
	 * is to be used to supply method create_root_directory()
	 * 
	 * @param username   user name
	 * @param password   password
	 * @param domainName domain name
	 * @param path       path
	 * @param instanceId instance ID
	 * @return Filesystem mounting point
	 */
	public static String createFileSystemMountingPoint(String username, String password, String domainName, String path,
			Integer instanceId) {
		String fileSystemMountingPoint = "{" + "\"Username\": \"" + username + "\"," + "\"Password\": \"" + password
				+ "\"," + "\"DomainName\": \"" + domainName + "\"," + "\"Path\": \"" + path + "\"," + "\"InstanceId\": "
				+ instanceId + "}";
		return fileSystemMountingPoint;
	}

	/**
	 * 
	 * @param admSessionID Admin session ID
	 * @param alias        Root directory Alias
	 * @param varargs      Array of optional arguments
	 *                     <p>
	 *                     amazonS3MountingPoints : First optional
	 *                     argument(List{@literal <}String{@literal >}), default
	 *                     value(null), List of amazon S3 mounting points
	 *                     </p>
	 *                     <p>
	 *                     fileSystemMountingPoints : Second optional
	 *                     argument(List{@literal <}String{@literal >}), default
	 *                     value(null), List of file system mounting points
	 *                     </p>
	 *                     <p>
	 *                     description : Third optional argument(String), default
	 *                     value("Root dir created through pma_java"), Root
	 *                     directory description
	 *                     </p>
	 *                     <p>
	 *                     isPublic : Fourth optional argument(Boolean), default
	 *                     value(false), defines whether the root directory is
	 *                     public or not
	 *                     </p>
	 *                     <p>
	 *                     isOffline : Fifth optional argument(Boolean), default
	 *                     value(null), defines whether the root directory is
	 *                     offline or not
	 *                     </p>
	 * @return Response code of the corresponding API call
	 */
	@SuppressWarnings("unchecked")
	public static String createRootDirectory(String admSessionID, String alias, Object... varargs) {
		// setting the default values when arguments' values are omitted
		List<String> amazonS3MountingPoints = null;
		List<String> fileSystemMountingPoints = null;
		String description = "Root dir created through pma_java";
		Boolean isPublic = false;
		Boolean isOffline = false;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof List<?>) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("createRootDirectory() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			amazonS3MountingPoints = (List<String>) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof List<?>) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("createRootDirectory() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			fileSystemMountingPoints = (List<String>) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof String) && varargs[2] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("createRootDirectory() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			description = (String) varargs[2];
		}
		if (varargs.length > 3) {
			if (!(varargs[3] instanceof Boolean) && varargs[3] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("createRootDirectory() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			isPublic = (Boolean) varargs[3];
		}
		if (varargs.length > 4) {
			if (!(varargs[4] instanceof Boolean) && varargs[4] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("createRootDirectory() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			isOffline = (Boolean) varargs[4];
		}
		try {
			String url = adminUrl(admSessionID, false) + "CreateRootDirectory";
			System.out.println(url);
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("POST");
			con.setRequestProperty("Content-Type", "application/json");
			con.setUseCaches(false);
			con.setDoOutput(true);
			String input = "{" + "\"sessionID\": \"" + admSessionID + "\"," + "\"rootDirectory\": {" + "\"Alias\": \""
					+ alias + "\"," + "\"Description\": \"" + description + "\"," + "\"Offline\": " + isOffline + ","
					+ "\"Public\": " + isPublic;

			if (amazonS3MountingPoints != null) {
				String amazonS3MountingPointsForJson = amazonS3MountingPoints.stream()
						.collect(Collectors.joining(",", "[", "]"));
				input += ", \"AmazonS3MountingPoints\" :" + amazonS3MountingPointsForJson;
			}
			if (fileSystemMountingPoints != null) {
				String fileSystemMountingPointsForJson = fileSystemMountingPoints.stream()
						.collect(Collectors.joining(",", "[", "]"));
				input += ", \"FileSystemMountingPoints\" :" + fileSystemMountingPointsForJson;
			}
			input += "}" + "}";
			OutputStream os = con.getOutputStream();
			os.write(input.getBytes("UTF-8"));
			os.close();
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			return jsonString;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}
}
