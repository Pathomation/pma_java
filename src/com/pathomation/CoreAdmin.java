package com.pathomation;

import org.json.JSONArray;
import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import static com.pathomation.Core.pmaUrl;
import static java.lang.System.out;

/**
 * Intended for PMA.core interactions related to administrative operations. Does
 * NOT apply to PMA.start / PMA.core.lite
 *
 * @author Yassine Iddaoui
 *
 */
public class CoreAdmin {

	private static int DEFAULT_CHUNK_SIZE = 1048576;

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
	 * @param sessionID sessionID : First optional argument(String), default
	 *	                value(null), session's ID
	 * @param xml       default value(false), define if method will return XML
	 *                    or Json content
	 * @return Adds sequence to the url to specify which content to be received (XML
	 *         or Json)
	 */
	private static String adminUrl(String sessionID, Boolean xml) {
		// let's get the base URL first for the specified session
		xml = xml == null ? false : xml;
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

	private static String adminUrl(String sessionID) {
		return adminUrl(sessionID, false);
	}

	private static String adminUrl() {
		return adminUrl(null, false);
	}

	/**
	 * This method is under construction
	 *
	 * @param method  default value(""),
	 * @param url     default value(null),
	 * @param session  default value(null), Session ID
	 * @throws Exception If something goes wrong
	 */
	public static void checkForPMAStart(String method, String url, String session) throws Exception {
		method = method == null ? "" : method;
		if (session != null && Core.getPmaCoreLiteSessionID().equals(session)) {
			if (PMA.logger != null) {
				PMA.logger.severe("PMA.start doesn't support " + method);
			}
			throw new Exception("PMA.start doesn't support " + method);
		} else if (url != null && url.equals(Core.getPmaCoreLiteSessionID())) {
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

	public static void checkForPMAStart(String method, String url) throws Exception {
		checkForPMAStart(method, url, null);
	}

	public static void checkForPMAStart(String method) throws Exception {
		checkForPMAStart(method, null, null);
	}

	public static void checkForPMAStart() throws Exception {
		checkForPMAStart(null, null, null);
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
			con.setUseCaches(true);
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
	 * @param pmaCoreURL url of PMA.core instance
	 *@param pmaCoreAdmUsername default value(""), username for PMA.core
	 *                             instance
	 @param pmaCoreAdmPassword default value(""), password for PMA.core instance
	  * @return session's ID if session was created successfully, otherwise null
	 * @throws Exception If target instance isn't a PMA.core instance
	 */
	public static String adminConnect(String pmaCoreURL,
									  String pmaCoreAdmUsername,
									  String pmaCoreAdmPassword) throws Exception {
		// setting the default values when arguments' values are omitted
		pmaCoreURL = pmaCoreURL == null ? Core.getPmaCoreLiteURL() : pmaCoreURL;
		pmaCoreAdmUsername = pmaCoreAdmUsername == null ? "" : pmaCoreAdmUsername;
		pmaCoreAdmPassword = pmaCoreAdmPassword == null ? "" : pmaCoreAdmPassword;
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
		if (PMA.debug) {
			System.out.println(url);
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


	public static String adminConnect(String pmaCoreURL,
									  String pmaCoreAdmUsername) throws Exception {
		return adminConnect(pmaCoreURL, pmaCoreAdmUsername, "" );
	}

	public static String adminConnect(String pmaCoreURL) throws Exception {
		return adminConnect(pmaCoreURL, "", "");
	}

	public static String adminConnect() throws Exception {
		return adminConnect(null, "", "");
	}

	/**
	 * This method is used to disconnect an admin session from a PMA.core instance
	 *
	 * @param adminSessionID First optional argument(String), default
	 * @return True if successfully disconnected, false otherwise
	 */
	public static boolean adminDisconnect(String adminSessionID) {
		return Core.disconnect(adminSessionID);
	}

	public static boolean adminDisconnect() {
		return Core.disconnect(null);
	}

	/**
	 * This method is used to send out an email reminder to the address associated
	 * with user login
	 *
	 * @param admSessionID admin session ID
	 * @param login        user login
	 * @param subject      First argument(String) - email subject.
	 * @return Empty string if operation successful, an error message otherwise
	 */
	public static String sendEmailReminder(String admSessionID, String login,
										   String subject) {
		// setting the default value when argument's value is omitted
		subject = subject == null ? "PMA.core password reminder" : subject;
		try {
			String url = adminUrl(admSessionID, false) + "EmailPassword";
			String reminderParams = "{\"username\": \"" + login + "\", \"subject\": \"" + subject
					+ "\",\"messageTemplate\": \"\"" + "}";
			String reminderResponse = httpPost(url, reminderParams);
			return reminderResponse;
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

	public static String sendEmailReminder(String admSessionID, String login) {
		return sendEmailReminder(admSessionID, login, null);
	}

	/**
	 * This method is used to create a new user on PMA.core
	 *
	 * @param admSessionID admin session ID
	 * @param login        user login
	 * @param firstName    user's first name
	 * @param lastName     user's last name
	 * @param email        user's email
	 * @param pwd          user's password
	 * @param canAnnotate  Defines whether the user can annotate slides or not
	 * @param isAdmin      Defines whether the user is an administrator or not
	 * @param isSuspended  Defines whether the user is suspended or not
	 * @return True if the creation has succeeded, false otherwise
	 */
	public static boolean addUser(String admSessionID, String login, String firstName, String lastName, String email,
								  String pwd, Boolean canAnnotate, Boolean isAdmin, Boolean isSuspended) {
		// setting the default value when argument's value is omitted

		System.out.println("Using credentials from " + admSessionID);

		try {
			String url = adminUrl(admSessionID, false) + "CreateUser";
			String input = "{" + "\"sessionID\": \"" + admSessionID + "\"," + "\"user\": {" + "\"Login\": \"" + login
					+ "\"," + "\"FirstName\": \"" + firstName + "\"," + "\"LastName\": \"" + lastName + "\","
					+ "\"Password\": \"" + pwd + "\"," + "\"Email\": \"" + email + "\"," + "\"Administrator\": "
					+ isAdmin + "," + "\"isSuspended\": " + isSuspended + "," + "\"CanAnnotate\": " + canAnnotate + "}"
					+ "}";

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

	public static boolean addUser(String admSessionID, String login, String firstName, String lastName, String email,
								  String pwd) {
		return addUser(admSessionID, login, firstName, lastName, email, pwd,
				false,false, false);
	}

	public static boolean addUser(String admSessionID, String login, String firstName, String lastName, String email,
								  String pwd, Boolean canAnnotate) {
		return addUser(admSessionID, login, firstName, lastName, email, pwd,
				canAnnotate,false, false);
	}

	public static boolean addUser(String admSessionID, String login, String firstName, String lastName, String email,
								  String pwd, Boolean canAnnotate, Boolean isAdmin) {
		return addUser(admSessionID, login, firstName, lastName, email, pwd,
				canAnnotate,isAdmin, false);
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
	 * @param sessionID an admin session ID
	 * @param path         path to create the new directory in
	 * @return true if directory was created successfully, false otherwise
	 */
	public static boolean createDirectory(String sessionID, String path) throws IOException {
		out.println();
		String urlS = null;
		try {
			urlS = pmaUrl(sessionID) + "admin/json/CreateDirectory";
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		out.println(urlS + "   urlS");
		String data = "{ \"sessionID\": \"" + sessionID + "\", \"path\": \"" + path + "\" }";
		out.println(data + "   data");
		String jsonString = httpPost(urlS, data);

		URL url = null;
		try {
			url = new URL(urlS);
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
		URLConnection con = null;
		try {
			con = url.openConnection();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		HttpURLConnection http = (HttpURLConnection) con;
		try {
			http.setRequestMethod("POST"); // PUT is another valid option
		} catch (ProtocolException e) {
			throw new RuntimeException(e);
		}
		http.setDoOutput(true);
		out.println(con);
		out.println(http);

		byte[] out = data.toString().getBytes(StandardCharsets.UTF_8);
		int length = out.length;
		System.out.println(length);

		http.setFixedLengthStreamingMode(length);
		http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
		try {
			http.connect();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		try (OutputStream os = http.getOutputStream()) {
			os.write(out);
			System.out.println(os);
		}
		return true;
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
	 * This method is used to lookup the reverse path of a UID for a specific slide
	 *
	 * @param admSessionID an admin session ID
	 * @param slideRefUid  slide UID
	 * @return The reverse path of the slide
	 * @throws Exception If something goes wrong
	 */
	public static String reverseUID(String admSessionID, String slideRefUid) throws Exception {
		if (admSessionID.equals(Core.getPmaCoreLiteSessionID())) {
			if (Core.isLite()) {
				throw new Exception(
						"PMA.core.lite found running, but doesn't support UIDs. For advanced anonymization, please upgrade to PMA.core.");
			} else {
				throw new Exception(
						"PMA.core.lite not found, and besides; it doesn't support UIDs. For advanced anonymization, please upgrade to PMA.core.");
			}
		}
		try {
			String url = adminUrl(admSessionID, false) + "ReverseLookupUID?sessionID=" + PMA.pmaQ(admSessionID) + "&uid="
					+ PMA.pmaQ(slideRefUid);
			String path;
			if (PMA.debug) {
				System.out.print(url);
			}
			String jsonString = PMA.httpGet(url, "application/json");
			if (PMA.isJSONObject(jsonString) && PMA.getJSONObjectResponse(jsonString).has("Code")) {
				if (PMA.debug) {
					System.out.println(jsonString);
				}
				throw new Exception("reverseUID() on  " + slideRefUid + " resulted in: " + PMA.getJSONObjectResponse(jsonString).getString("Message"));
			} else {
				path = jsonString;
			}
			return path;
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
	 * This method is used to lookup the reverse path of a root-directory
	 *
	 * @param admSessionID an admin session ID
	 * @param alias  root directory alias
	 * @return The reverse path of the root directory it's a file system one, empty string if it's a S3 one
	 * @throws Exception If something goes wrong
	 */
	public static String reverseRootDirectory(String admSessionID, String alias) throws Exception {
		if (admSessionID.equals(Core.getPmaCoreLiteSessionID())) {
			if (Core.isLite()) {
				throw new Exception(
						"PMA.core.lite found running, but doesn't support UIDs. For advanced anonymization, please upgrade to PMA.core.");
			} else {
				throw new Exception(
						"PMA.core.lite not found, and besides; it doesn't support UIDs. For advanced anonymization, please upgrade to PMA.core.");
			}
		}
		try {
			String url = adminUrl(admSessionID) + "ReverseLookupRootDirectory?sessionID=" + PMA.pmaQ(admSessionID) + "&alias="
					+ PMA.pmaQ(alias);
			String path;
			if (PMA.debug) {
				System.out.print(url);
			}
			String jsonString = PMA.httpGet(url, "application/json");
			if (PMA.isJSONObject(jsonString) && PMA.getJSONObjectResponse(jsonString).has("Code")) {
				if (PMA.debug) {
					System.out.println(jsonString);
				}
				throw new Exception("reverseRootDirectory() on  " + alias + " resulted in: " + PMA.getJSONObjectResponse(jsonString).getString("Message"));
			} else {
				path = jsonString;
			}
			return path;
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
	 * @param chunkSize  default value(1048576), chunk size
	 * @param serviceUrl  default value(null), service URL
	 * @return Amazon S3 mounting point
	 */
	public static String createAmazons3MountingPoint(String accessKey, String secretKey, String path,
													 Integer instanceId, Integer chunkSize, String serviceUrl) {
		// setting the default values when arguments' values are omitted

		chunkSize = chunkSize == null ? DEFAULT_CHUNK_SIZE : chunkSize;
		String s3MountingPoint = "{" + "\"AccessKey\": \"" + accessKey + "\"," + "\"SecretKey\": \"" + secretKey + "\","
				+ (chunkSize == DEFAULT_CHUNK_SIZE ? "" : "\"ChunkSize\": " + chunkSize + ",")
				+ (serviceUrl == null ? "" : "\"ServiceUrl\": \"" + serviceUrl + "\",") + "\"Path\": \"" + path + "\","
				+ "\"InstanceId\": " + instanceId + "}";
		return s3MountingPoint;
	}

	public static String createAmazons3MountingPoint(String accessKey, String secretKey, String path,
													 Integer instanceId,
													 Integer chunkSize) {
		return createAmazons3MountingPoint(accessKey, secretKey, path,
				instanceId, chunkSize, null);
	}

	public static String createAmazons3MountingPoint(String accessKey, String secretKey, String path,
													 Integer instanceId) {
		return createAmazons3MountingPoint(accessKey, secretKey, path,
				instanceId, null, null);
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
	 * @param amazonS3MountingPoints   List of amazon S3 mounting points
	 * @param fileSystemMountingPoints List of file system mounting points
	 * @param description  (String), default
	 *                     value("Root dir created through pma_java"), Root
	 *                     directory description
	 * @param isPublic     (Boolean) defines whether the root directory is
	 *                     public or not
	 * @param isOffline    (Boolean) defines whether the root directory is
	 *                     offline or not
	 * @return Response code of the corresponding API call
	 */
	@SuppressWarnings("unchecked")
	public static String createRootDirectory(String admSessionID,
											 String alias,
											 List<String> amazonS3MountingPoints,
											 List<String> fileSystemMountingPoints,
											 String description,
											 Boolean isPublic,
											 Boolean isOffline
	) {
		description = description == null ? "Root dir created through " +
				"pma_java" : description;
		isPublic = isPublic == null ? false : isPublic;
		isOffline = isOffline == null ? false : isOffline;
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
			con.setUseCaches(true);
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

	public static String createRootDirectory(String admSessionID,
											 String alias) {
		return createRootDirectory(admSessionID, alias,
				null,
				null,
				null,
				false,
				false);
	}

	public static String createRootDirectory(String admSessionID,
											 String alias,
											 List<String> amazonS3MountingPoints
	) {
		return createRootDirectory(admSessionID, alias, amazonS3MountingPoints,
				null, null,
				false,
				false);
	}

	public static String createRootDirectory(String admSessionID,
											 String alias,
											 List<String> amazonS3MountingPoints,
											 List<String> fileSystemMountingPoints) {
		return createRootDirectory(admSessionID, alias,
				amazonS3MountingPoints, fileSystemMountingPoints,
				null,
				false,
				false);
	}

	public static String createRootDirectory(String admSessionID,
											 String alias,
											 List<String> amazonS3MountingPoints,
											 List<String> fileSystemMountingPoints,
											 String description) {
		return createRootDirectory(admSessionID, alias,
				amazonS3MountingPoints, fileSystemMountingPoints,
				description,
				false,
				false);
	}

	public static String createRootDirectory(String admSessionID,
											 String alias,
											 List<String> amazonS3MountingPoints,
											 List<String> fileSystemMountingPoints,
											 String description,
											 Boolean isPublic) {
		return createRootDirectory(admSessionID, alias,
				amazonS3MountingPoints, fileSystemMountingPoints,
				description,
				isPublic,
				false);
	}
}
