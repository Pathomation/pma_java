package com.pathomation;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

import javax.net.ssl.HttpsURLConnection;

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
		Boolean xml = true;
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
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                method : First optional argument(String), default
	 *                value(""), method
	 *                </p>
	 *                <p>
	 *                url : Second optional argument(String), default value(null),
	 *                url
	 *                </p>
	 *                <p>
	 *                session : Third optional argument(String), default value(null),
	 *                Session ID
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

	public static String addUser(String admSessionID, String login, String firstName, String lastName, String email, String pwd, Boolean... varargs) {
		// setting the default value when argument's value is omitted
		Boolean canAnnotate = varargs.length > 0 ? varargs[0] : false;
		Boolean isAdmin = varargs.length > 1 ? varargs[1] : false;
		Boolean isSuspended = varargs.length > 2 ? varargs[2] : false;
		System.out.println("Using credentials from " + admSessionID);
		
		try {
			String url = adminUrl(admSessionID, false) + "CreateUser";
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
			String input = "{" 
					+ "\"sessionID\": " + admSessionID + ","
					+ "\"user\": {"
					+ "\"Login\": " + login + ","
					+ "\"FirstName\": " + firstName + ","
					+ "\"LastName\": " + lastName + ","
					+ "\"Password\": " + pwd + ","
					+ "\"Email\": " + email + ","
					+ "\"Administrator\": " + isAdmin + ","
					+ "\"CanAnnotate\": " + canAnnotate
					+ "}"
					+ "}";
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
	
	/**
	 * This method is used to create a new directory on PMA.core
	 * 
	 * @param sessionID a session ID
	 * @param path      path to create the new directory in
	 * @return true if directory was created successfully, false otherwise
	 */
	public static boolean createDirectory(String sessionID, String path) {
		try {
			// we only create folders on PMA.core
			if (Core.isLite(Core.pmaUrl(sessionID))) {
				return false;
			} else {
				String url = adminUrl(sessionID, false) + "CreateDirectory";
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
				String input = "{ \"sessionID\": \"" + sessionID + "\", \"path\": \"" + path + "\" }";
				OutputStream os = con.getOutputStream();
				os.write(input.getBytes("UTF-8"));
				os.close();
				String jsonString = PMA.getJSONAsStringBuffer(con).toString();
				return jsonString.equals("true") ? true : false;
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
}
