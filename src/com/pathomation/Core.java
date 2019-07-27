package com.pathomation;

import java.awt.Image;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.io.FilenameUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <h1>Java SDK</h1>
 * <p>
 * Java wrapper library for PMA.start, a universal viewer for whole slide
 * imaging and microscopy
 * </p>
 * 
 * @author Yassine Iddaoui
 * @version 2.0.0.76
 */
public class Core {
	/**
	 * So afterwards we can look up what username actually belongs to a sessions
	 */
	private static Map<String, Object> pmaSessions = new HashMap<String, Object>();
	/**
	 * So afterwards we can determine the PMA.core URL to connect to for a given
	 * SessionID
	 */
	private static Map<String, String> pmaUsernames = new HashMap<>();
	/**
	 * A caching mechanism for slide information; obsolete and should be improved
	 * through httpGet()
	 */
	private static Map<String, Object> pmaSlideInfos = new HashMap<String, Object>();
	private static final String pmaCoreLiteURL = "http://localhost:54001/";
	private static final String pmaCoreLiteSessionID = "SDK.Java";
	private static Boolean pmaUseCacheWhenRetrievingTiles = true;
	/**
	 * Keep track of how much data was downloaded
	 */
	@SuppressWarnings("serial")
	private static Map<String, Integer> pmaAmountOfDataDownloaded = new HashMap<String, Integer>() {
		{
			put(pmaCoreLiteSessionID, 0);
		}
	};

	/**
	 * Object Mapper for Jackson library
	 */
	private static ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * @return the pmaSessions
	 */
	public static Map<String, Object> getPmaSessions() {
		return pmaSessions;
	}

	/**
	 * @return the pmaUsernames
	 */
	public static Map<String, String> getPmaUsernames() {
		return pmaUsernames;
	}

	/**
	 * @return the pmaSlideInfos
	 */
	public static Map<String, Object> getPmaSlideInfos() {
		return pmaSlideInfos;
	}

	/**
	 * @return the pmaCoreLiteURL
	 */
	public static String getPmaCoreLiteURL() {
		return pmaCoreLiteURL;
	}

	/**
	 * @return the pmaCoreLiteSessionID
	 */
	public static String getPmaCoreLiteSessionID() {
		return pmaCoreLiteSessionID;
	}

	/**
	 * @return the pmaAmountOfDataDownloaded
	 */
	public static Map<String, Integer> getPmaAmountOfDataDownloaded() {
		return pmaAmountOfDataDownloaded;
	}

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
	 * This method is used to get the session's ID
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return The same sessionID if explicited, otherwise it recovers a session's
	 *         ID
	 */
	private static String sessionId(String... varargs) {
		// setting the default value when argument's value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		if (sessionID == null) {
			// if the sessionID isn't specified, maybe we can still recover it somehow
			return firstSessionId();
		} else {
			// nothing to do in this case; a SessionID WAS passed along, so just continue
			// using it
			return sessionID;
		}
	}

	/**
	 * This method is used to get PMA.core active session
	 * 
	 * @return PMA.core active session
	 */
	private static String firstSessionId() {
		// do we have any stored sessions from earlier login events?
		if (pmaSessions.size() > 0) {
			// yes we do! This means that when there's a PMA.core active session AND
			// PMA.core.lite version running,
			// the PMA.core active will be selected and returned
			return pmaSessions.keySet().toArray()[0].toString();
		} else {
			// ok, we don't have stored sessions; not a problem per se...
			if (pmaIsLite()) {
				if (!pmaSlideInfos.containsKey(pmaCoreLiteSessionID)) {
					pmaSlideInfos.put(pmaCoreLiteSessionID, new HashMap<String, Object>());
				}
				if (!pmaAmountOfDataDownloaded.containsKey(pmaCoreLiteSessionID)) {
					pmaAmountOfDataDownloaded.put(pmaCoreLiteSessionID, 0);
				}
				return pmaCoreLiteSessionID;
			} else {
				// no stored PMA.core sessions found NOR PMA.core.lite
				return null;
			}
		}
	}

	/**
	 * This method is used to get the url related to the session's ID
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return Url related to the session's ID
	 * @throws Exception if sessionID is invalid
	 */
	public static String pmaUrl(String... varargs) throws Exception {
		// setting the default value when argument's value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		sessionID = sessionId(sessionID);
		if (sessionID == null) {
			// sort of a hopeless situation; there is no URL to refer to
			return null;
		} else if (sessionID.equals(pmaCoreLiteSessionID)) {
			return pmaCoreLiteURL;
		} else {
			// assume sessionID is a valid session; otherwise the following will generate an
			// error
			if (pmaSessions.containsKey(sessionID)) {
				String url = pmaSessions.get(sessionID).toString();
				if (!url.endsWith("/")) {
					url = url + "/";
				}
				return url;
			} else {
				if (PMA.logger != null) {
					PMA.logger.severe("Invalid sessionID:" + sessionID);
				}
				throw new Exception("Invalid sessionID:" + sessionID);
			}
		}
	}

	/**
	 * This method is used to check to see if PMA.core.lite (server component of
	 * PMA.start) is running at a given endpoint. if pmaCoreURL is omitted, default
	 * check is to see if PMA.start is effectively running at localhost (defined by
	 * pmaCoreLiteURL). note that PMA.start may not be running, while it is actually
	 * installed. This method doesn't detect whether PMA.start is installed; merely
	 * whether it's running! if pmaCoreURL is specified, then the method checks if
	 * there's an instance of PMA.start (results in True), PMA.core (results in
	 * False) or nothing (at least not a Pathomation software platform component) at
	 * all (results in None)
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                pmaCoreURL : First optional argument(String), default
	 *                value(Class field pmaCoreLiteURL), url of PMA.core instance
	 *                </p>
	 * @return True if an instance of PMA.core is running, false otherwise
	 */
	private static Boolean pmaIsLite(String... varargs) {
		// setting the default value when argument's value is omitted
		String pmaCoreURL = varargs.length > 0 ? varargs[0] : pmaCoreLiteURL;
		String url = PMA.join(pmaCoreURL, "api/xml/IsLite");
		String contents = "";
		try {
			contents = PMA.urlReader(url);
			return PMA.domParser(contents).getChildNodes().item(0).getChildNodes().item(0).getNodeValue().toLowerCase()
					.toString().equals("true");
		} catch (Exception e) {
			// this happens when NO instance of PMA.core is detected
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
	 * This method is used to define which content will be received "XML" or "Json"
	 * for "API" Web service calls
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
	 * @return Add a sequence to the url to specify which content to be received
	 *         (XML or Json)
	 */
	private static String apiUrl(Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		Boolean xml = true;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("apiUrl() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Boolean) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("apiUrl() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			xml = (Boolean) varargs[1];
		}
		// let's get the base URL first for the specified session
		String url;
		try {
			url = pmaUrl(sessionID);
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
			return PMA.join(url, "api/xml/");
		} else {
			return PMA.join(url, "api/json/");
		}
	}

	/**
	 * This method is used to create the query URL for a session ID
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return Query URL
	 */
	public static String queryUrl(String... varargs) {
		// setting the default value when argument's value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// let's get the base URL first for the specified session
		try {
			String url = pmaUrl(sessionID);
			if (url == null) {
				// sort of a hopeless situation; there is no URL to refer to
				return null;
			}
			// remember, pmaUrl is guaranteed to return a URL that ends with "/"
			return PMA.join(url, "query/json/");
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
	 * checks to see if PMA.core.lite (server component of PMA.start) is running at
	 * a given endpoint. if pmaCoreURL is omitted, default check is to see if
	 * PMA.start is effectively running at localhost (defined by pmaCoreLiteURL).
	 * note that PMA.start may not be running, while it is actually installed. This
	 * method doesn't detect whether PMA.start is installed; merely whether it's
	 * running! if pmaCoreURL is specified, then the method checks if there's an
	 * instance of PMA.start (results in True), PMA.core (results in False) or
	 * nothing (at least not a Pathomation software platform component) at all
	 * (results in None)
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                pmaCoreURL : First optional argument(String), default
	 *                value(Class field pmaCoreLiteURL), url of PMA.core instance
	 *                </p>
	 * @return Checks if there is a PMA.core.lite or PMA.core instance running
	 */
	public static Boolean isLite(String... varargs) {
		// setting the default value when argument's value is omitted
		String pmaCoreURL = varargs.length > 0 ? varargs[0] : pmaCoreLiteURL;
		// See if there's a PMA.core.lite or PMA.core instance running at pmacoreURL
		return pmaIsLite(pmaCoreURL);
	}

	/**
	 * This method is used to get the version number
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                pmaCoreURL : First optional argument(String), default
	 *                value(Class field pmaCoreLiteURL), url of PMA.core instance
	 *                </p>
	 * @return Version number
	 */
	public static String getVersionInfo(String... varargs) {
		// setting the default value when argument's value is omitted
		String pmaCoreURL = varargs.length > 0 ? varargs[0] : pmaCoreLiteURL;
		// Get version info from PMA.core instance running at pmacoreURL.
		// Return null if PMA.core not found running at pmacoreURL endpoint
		// purposefully DON'T use helper function apiUrl() here:
		// why? because GetVersionInfo can be invoked WITHOUT a valid SessionID;
		// apiUrl() takes session information into account
		String url = PMA.join(pmaCoreURL, "api/json/GetVersionInfo");
		String version = null;
		if (PMA.debug) {
			System.out.println(url);
		}
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("getVersionInfo failed : " + jsonResponse.get("Message"));
					}
					throw new Exception("getVersionInfo failed : " + jsonResponse.get("Message"));
				} else if (jsonResponse.has("d")) {
					version = jsonResponse.getString("d");
				} else {
					return null;
				}
			} else {
				version = jsonString;
			}
			return version;
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
	 * This method is used to get the API version in a list fashion
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                pmacoreURL : First optional argument(String), default
	 *                value(Class field pmaCoreLiteURL), url of PMA.core instance
	 *                </p>
	 * @return API version in a list fashion
	 * @throws Exception If GetAPIVersion isn't available on the API
	 */
	public static List<Integer> getAPIVersion(String... varargs) throws Exception {
		// setting the default values when arguments' values are omitted
		String pmaCoreURL = varargs.length > 0 ? varargs[0] : pmaCoreLiteURL;
		String url = PMA.join(pmaCoreURL, "api/json/GetAPIVersion");
		if (PMA.debug) {
			System.out.println(url);
		}

		String jsonString = null;
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			jsonString = PMA.getJSONAsStringBuffer(con).toString();
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
		List<Integer> version = null;
		try {
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("get_api_version resulted in: " + jsonResponse.get("Message"));
					}
					throw new Exception("get_api_version resulted in: " + jsonResponse.get("Message"));
				} else if (jsonResponse.has("d")) {
					JSONArray array = jsonResponse.getJSONArray("d");
					version = new ArrayList<>();
					for (int i = 0; i < array.length(); i++) {
						version.add(array.optInt(i));
					}
				} else {
					return null;
				}
			} else {
				JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
				version = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					version.add(jsonResponse.optInt(i));
				}
			}
			return version;
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
				PMA.logger.severe("GetAPIVersion method not available at " + pmaCoreURL);
			}
			throw new Exception("GetAPIVersion method not available at " + pmaCoreURL);
		}
	}

	/**
	 * This method is used to get the API version in a single string
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                pmacoreURL : First optional argument(String), default
	 *                value(Class field pmaCoreLiteURL), url of PMA.core instance
	 *                </p>
	 * @return API version in a single string
	 * @throws Exception If GetAPIVersion isn't available on the API
	 * 
	 */
	public static String getAPIVersionString(String... varargs) throws Exception {
		// setting the default values when arguments' values are omitted
		String pmaCoreURL = varargs.length > 0 ? varargs[0] : pmaCoreLiteURL;
		List<Integer> version = getAPIVersion(pmaCoreURL);
		String versionString = version.stream().map(n -> (n + ".")).collect(Collectors.joining("", "", ""));
		return versionString.substring(0, versionString.length() - 1);
	}

	/**
	 * This method is used to authenticate &amp; connect to a PMA.core instance
	 * using credentials
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
	 */
	public static String connect(String... varargs) {
		// setting the default values when arguments' values are omitted
		String pmaCoreURL = varargs.length > 0 ? varargs[0] : pmaCoreLiteURL;
		String pmaCoreUsername = varargs.length > 1 ? varargs[1] : "";
		String pmaCorePassword = varargs.length > 2 ? varargs[2] : "";
		// Attempt to connect to PMA.core instance; success results in a SessionID
		if (pmaCoreURL.equals(pmaCoreLiteURL)) {
			if (isLite()) {
				// no point authenticating localhost / PMA.core.lite
				return pmaCoreLiteSessionID;
			} else {
				return null;
			}
		}
		// purposefully DON'T use helper function apiUrl() here:
		// why? Because apiUrl() takes session information into account (which we
		// don't have yet)
		String url = PMA.join(pmaCoreURL, "api/json/authenticate?caller=SDK.Java");
		if (!pmaCoreUsername.equals("")) {
			url = url.concat("&username=").concat(PMA.pmaQ(pmaCoreUsername));
		}
		if (!pmaCorePassword.equals("")) {
			url = url.concat("&password=").concat(PMA.pmaQ(pmaCorePassword));
		}
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			String sessionID = null;
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				if (!jsonResponse.get("Success").toString().toLowerCase().equals("true")) {
					return null;
				} else {
					sessionID = jsonResponse.getString("SessionId");
					pmaUsernames.put(sessionID, pmaCoreUsername);
					pmaSessions.put(sessionID, pmaCoreURL);
					if (!pmaSlideInfos.containsKey(sessionID)) {
						pmaSlideInfos.put(sessionID, new HashMap<String, Object>());
					}
					pmaAmountOfDataDownloaded.put(sessionID, jsonResponse.length());
					return sessionID;
				}
			} else {
				return null;
			}
		} catch (Exception e) {
			// Something went wrong; unable to communicate with specified endpoint
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
	 * This method is used to disconnect from a running PMA.core instance
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return true if there was a PMA.core instance running to disconnect from,
	 *         false otherwise
	 */
	public static Boolean disconnect(String... varargs) {
		// setting the default value when argument's value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Disconnect from a PMA.core instance; return True if session exists; return
		// False if session didn't exist (anymore)
		sessionID = sessionId(sessionID);
		String url = apiUrl(sessionID) + "DeAuthenticate?sessionID=" + PMA.pmaQ((sessionID));
		String contents = PMA.urlReader(url);
		pmaAmountOfDataDownloaded.put(sessionID, pmaAmountOfDataDownloaded.get(sessionID) + contents.length());
		if (pmaSessions.size() > 0) {
			// yes we do! This means that when there's a PMA.core active session AND
			// PMA.core.lite version running,
			// the PMA.core active will be selected and returned
			pmaSessions.remove(sessionID);
			pmaSlideInfos.remove(sessionID);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * This method is used to test if sessionID is valid and the server is online
	 * and reachable This method works only for PMA.core, don't use it for PMA.start
	 * for it will return always false
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return true if sessionID is valid and the server is online and reachable,
	 *         false otherwise
	 */
	public static boolean ping(String... varargs) {
		// setting the default value when argument's value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		sessionID = sessionId(sessionID);
		String url = apiUrl(sessionID, false) + "Ping?sessionID=" + PMA.pmaQ(sessionID);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			con.setRequestProperty("Accept", "application/json");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			return jsonString.equals("true") ? true : false;
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
	 * This method is used to get root-directories available for a sessionID
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return Array of root-directories available to a session's ID
	 */
	public static List<String> getRootDirectories(String... varargs) {
		// setting the default value when argument's value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Return a list of root-directories available to sessionID
		sessionID = sessionId(sessionID);
		String url = apiUrl(sessionID) + "GetRootDirectories?sessionID=" + PMA.pmaQ(sessionID);
		String contents = PMA.urlReader(url);
		pmaAmountOfDataDownloaded.put(sessionID, pmaAmountOfDataDownloaded.get(sessionID) + contents.length());
		Document dom = PMA.domParser(contents);
		return PMA.xmlToStringArray((Element) dom.getFirstChild());
	}

	/**
	 * This method is used to get sub-directories available to sessionID in the
	 * start directory following a recursive (or not) approach
	 * 
	 * @param startDir Start directory
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 recursivity : Second optional argument(Boolean or Integer),
	 *                 default value(Boolean, false), if it's a Boolean if defines
	 *                 either no recursivity or a limitless recursivity, if it's an
	 *                 Integer it defines a limited in depth recursivity or no
	 *                 recursivity at all if this Integer equals 0
	 *                 </p>
	 * @return Sub-directories available to a session's ID in a start directory
	 */
	public static List<String> getDirectories(String startDir, Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		// we can either choose to have a non recursive call, a complete recursive call
		// or a recursive call to a certain depth, in the last case we use an integer to
		// define
		// depth
		// the following three variables intend to implement this
		Boolean recursive = false;
		Integer integerRecursive = 0;
		String booleanOrInteger = "";
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getDirectories() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if ((!(varargs[1] instanceof Integer) && !(varargs[1] instanceof Boolean)) && (varargs[1] != null)) {
				if (PMA.logger != null) {
					PMA.logger.severe("getDirectories() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			if (varargs[1] instanceof Boolean) {
				recursive = (Boolean) varargs[1];
				booleanOrInteger = "boolean";
			}
			if (varargs[1] instanceof Integer) {
				integerRecursive = (Integer) varargs[1];
				recursive = ((Integer) varargs[1]) > 0 ? true : false;
				booleanOrInteger = "integer";
			}
		}

		// Return a list of sub-directories available to sessionID in the startDir
		// directory
		sessionID = sessionId(sessionID);
		String url = apiUrl(sessionID, false) + "GetDirectories?sessionID=" + PMA.pmaQ(sessionID) + "&path="
				+ PMA.pmaQ(startDir);
		if (PMA.debug) {
			System.out.println(url);
		}
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			List<String> dirs;
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("get_directories to " + startDir + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that startDir is case sensitive!)");
					}
					throw new Exception("get_directories to " + startDir + " resulted in: "
							+ jsonResponse.get("Message") + " (keep in mind that startDir is case sensitive!)");
				} else if (jsonResponse.has("d")) {
					JSONArray array = jsonResponse.getJSONArray("d");
					dirs = new ArrayList<>();
					for (int i = 0; i < array.length(); i++) {
						dirs.add(array.optString(i));
					}
					// return dirs;
				} else {
					return null;
				}
			} else {
				JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				dirs = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					dirs.add(jsonResponse.optString(i));
				}
				// return dirs;
			}

			// we test if call is recursive, and if yes to which depth
			if (recursive) {
				for (String dir : getDirectories(startDir, sessionID)) {
					if (booleanOrInteger.equals("boolean")) {
						dirs.addAll(getDirectories(dir, sessionID, recursive));
					}
					if (booleanOrInteger.equals("integer")) {
						dirs.addAll(getDirectories(dir, sessionID, integerRecursive - 1));
					}
				}
			}
			return dirs;

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
	 * This method is used to get the first non empty directory
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                startDir : First optional argument(String), default
	 *                value(null), start directory
	 *                </p>
	 *                <p>
	 *                sessionID : Second optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return Path to the first non empty directory found
	 */
	public static String getFirstNonEmptyDirectory(String... varargs) {
		// setting the default values when arguments' values are omitted
		String startDir = varargs.length > 0 ? varargs[0] : null;
		String sessionID = varargs.length > 1 ? varargs[1] : null;
		if ((startDir == null) || (startDir.equals(""))) {
			startDir = "/";
		}
		List<String> slides = null;
		try {
			slides = getSlides(startDir, sessionID);
		} catch (Exception e) {
			if (PMA.debug) {
				System.out.println("Unable to examine " + startDir);
				if (PMA.logger != null) {
					PMA.logger.severe("Unable to examine " + startDir);
				}
			}
			if (!startDir.equals("/")) {
				return null;
			}
		}
		if ((slides != null) && (slides.size() > 0)) {
			return startDir;
		} else {
			if (startDir.equals("/")) {
				for (String dir : getRootDirectories(sessionID)) {
					String nonEmptyDir = getFirstNonEmptyDirectory(dir, sessionID);
					if (nonEmptyDir != null) {
						return nonEmptyDir;
					}
				}
			} else {
				boolean success = true;
				List<String> dirs = null;
				try {
					dirs = getDirectories(startDir, sessionID);
				} catch (Exception e) {
					System.out.println("Unable to examine " + startDir);
					if (PMA.logger != null) {
						PMA.logger.severe(
								"Debug flag enabled. You will receive extra feedback and messages from the Java SDK (like this one)");
					}
					success = false;
				}
				if (success) {
					for (String dir : dirs) {
						String nonEmptyDir = getFirstNonEmptyDirectory(dir, sessionID);
						if (nonEmptyDir != null) {
							return nonEmptyDir;
						}
					}
				}
			}
			return null;
		}
	}

	/**
	 * This method is used to get a list of slides available to sessionID in the
	 * start directory following a recursive (or not) approach
	 * 
	 * @param startDir Start directory
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 recursivity : Second optional argument(Boolean or Integer),
	 *                 default value(Boolean, false), if it's a Boolean if defines
	 *                 either no recursivity or a limitless recursivity, if it's an
	 *                 Integer it defines a limited in depth recursivity or no
	 *                 recursivity at all if this Integer equals 0
	 *                 </p>
	 * @return List of slides available to a session's ID in a start directory
	 */
	public static List<String> getSlides(String startDir, Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		// we can either choose to have a non recursive call, a complete recursive call
		// or a recursive call to a certain depth, in the last case we use an integer to
		// define
		// depth
		// the following three variables intend to implement this
		String booleanOrInteger = "";
		Boolean recursive = false;
		Integer integerRecursive = 0;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getSlides() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if ((!(varargs[1] instanceof Integer) && !(varargs[1] instanceof Boolean)) && (varargs[1] != null)) {
				if (PMA.logger != null) {
					PMA.logger.severe("getSlides() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			if (varargs[1] instanceof Boolean) {
				recursive = (Boolean) varargs[1];
				booleanOrInteger = "boolean";
			}
			if (varargs[1] instanceof Integer) {
				integerRecursive = (Integer) varargs[1];
				recursive = ((Integer) varargs[1]) > 0 ? true : false;
				booleanOrInteger = "integer";
			}
		}

		// Return a list of slides available to sessionID in the startDir directory
		sessionID = sessionId(sessionID);
		if (startDir.startsWith("/")) {
			startDir = startDir.substring(1);
		}
		String url = apiUrl(sessionID, false) + "GetFiles?sessionID=" + PMA.pmaQ(sessionID) + "&path="
				+ PMA.pmaQ(startDir);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			List<String> slides;
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("get_slides from " + startDir + " resulted in: " + jsonResponse.get("Message")
								+ " (keep in mind that startDir is case sensitive!)");
					}
					throw new Exception("get_slides from " + startDir + " resulted in: " + jsonResponse.get("Message")
							+ " (keep in mind that startDir is case sensitive!)");
				} else if (jsonResponse.has("d")) {
					JSONArray array = jsonResponse.getJSONArray("d");
					slides = new ArrayList<>();
					for (int i = 0; i < array.length(); i++) {
						slides.add(array.optString(i));
					}
					// return slides;
				} else {
					return null;
				}
			} else {
				JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				slides = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					slides.add(jsonResponse.optString(i));
				}
				// return slides;
			}

			// we test if call is recursive, and if yes to which depth
			if (recursive) {
				for (String dir : getDirectories(startDir, sessionID)) {
					if (booleanOrInteger.equals("boolean")) {
						slides.addAll(getSlides(dir, sessionID, recursive));
					}
					if (booleanOrInteger.equals("integer")) {
						slides.addAll(getSlides(dir, sessionID, integerRecursive - 1));
					}
				}
			}
			return slides;
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
	 * This method is used to determine the file extension for a slide's path
	 * 
	 * @param slideRef slide's path
	 * @return File extension extracted from a slide's path
	 */
	public static String getSlideFileExtension(String slideRef) {
		// Determine the file extension for this slide
		return FilenameUtils.getExtension(slideRef);
	}

	/**
	 * This method is used to determine file name (with extension) for a slide's
	 * path
	 * 
	 * @param slideRef slide's path
	 * @return File name extracted from a slide's path
	 */
	public static String getSlideFileName(String slideRef) {
		// Determine the file name (with extension) for this slide
		return FilenameUtils.getName(slideRef);
	}

	/**
	 * This method is used to get the UID for a defined slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return UID for a defined slide's path
	 * @throws Exception if PMA.core not found
	 */
	public static String getUid(String slideRef, String... varargs) throws Exception {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the UID for a specific slide
		sessionID = sessionId(sessionID);
		if (sessionID.equals(pmaCoreLiteSessionID)) {
			if (isLite()) {
				if (PMA.logger != null) {
					PMA.logger.severe(
							"PMA.core.lite found running, but doesn't support UID generation. For advanced anonymization, please upgrade to PMA.core.");
				}
				throw new Exception(
						"PMA.core.lite found running, but doesn't support UID generation. For advanced anonymization, please upgrade to PMA.core.");

			} else {
				if (PMA.logger != null) {
					PMA.logger.severe(
							"PMA.core.lite not found, and besides; it doesn't support UID generation. For advanced anonymization, please upgrade to PMA.core.");
				}
				throw new Exception(
						"PMA.core.lite not found, and besides; it doesn't support UID generation. For advanced anonymization, please upgrade to PMA.core.");
			}
		}
		String url = apiUrl(sessionID) + "GetUID?sessionID=" + PMA.pmaQ(sessionID) + "&path=" + PMA.pmaQ(slideRef);
		String contents = PMA.urlReader(url);
		pmaAmountOfDataDownloaded.put(sessionID, pmaAmountOfDataDownloaded.get(sessionID) + contents.length());
		Document dom = PMA.domParser(contents);
		return PMA.xmlToStringArray(dom).get(0);
	}

	/**
	 * This method is used to get the fingerprint for a specific slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Fingerprint of the slide
	 */
	public static String getFingerPrint(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the fingerprint for a specific slide
		sessionID = sessionId(sessionID);
		String fingerprint;
		String url = apiUrl(sessionID, false) + "GetFingerprint?sessionID=" + PMA.pmaQ(sessionID) + "&pathOrUid="
				+ PMA.pmaQ(slideRef);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("get_fingerprint on " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					}
					throw new Exception("get_fingerprint on " + slideRef + " resulted in: "
							+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
				} else {
					return jsonResponse.getString("d");
				}
			} else {
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonString.length());
				fingerprint = jsonString.replace("\"", "");
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
		return fingerprint;
	}

	/**
	 * This method is used to get information about a session
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return Information about a session
	 */
	public static Map<String, String> whoAmI(String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Getting information about your Session
		sessionID = sessionId(sessionID);
		Map<String, String> retval = null;
		if (sessionID.equals(pmaCoreLiteSessionID)) {
			retval = new HashMap<>();
			retval.put("sessionID", pmaCoreLiteSessionID);
			retval.put("username", null);
			retval.put("url", pmaCoreLiteURL);
			retval.put("amountOfDataDownloaded", pmaAmountOfDataDownloaded.get(pmaCoreLiteSessionID).toString());
		} else if (sessionID != null) {
			retval = new HashMap<>();
			retval.put("sessionID", sessionID);
			retval.put("username", pmaUsernames.get(sessionID));
			retval.put("amountOfDataDownloaded", pmaAmountOfDataDownloaded.get(sessionID).toString());
			try {
				retval.put("url", pmaUrl(sessionID));
			} catch (Exception e) {
				if (PMA.logger != null) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					PMA.logger.severe(sw.toString());
				}
			}
		}

		return retval;
	}

	/**
	 * This method is used to get tile size information for sessionID
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return A list of two items (duplicated) relative to the tile size
	 *         information for a session's ID
	 */
	@SuppressWarnings("unchecked")
	public static List<Integer> getTileSize(String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		sessionID = sessionId(sessionID);
		Map<String, Object> info;
		if (((Map<String, Object>) pmaSlideInfos.get(sessionID)).size() < 1) {
			String dir = getFirstNonEmptyDirectory(sessionID);
			List<String> slides = getSlides(dir, sessionID);
			info = getSlideInfo(slides.get(0), sessionID);
		} else {
			int getLength = ((Map<String, Object>) pmaSlideInfos.get(sessionID)).values().toArray().length;
			info = (Map<String, Object>) ((Map<String, Object>) pmaSlideInfos.get(sessionID)).values()
					.toArray()[new Random().nextInt(getLength)];
		}
		List<Integer> result = new ArrayList<>();
		result.add(Integer.parseInt(info.get("TileSize").toString()));
		result.add(Integer.parseInt(info.get("TileSize").toString()));
		return result;
	}

	/**
	 * This method is used to get a raw image in the form of nested maps
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Nested maps forming a raw image
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> getSlideInfo(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Return raw image information in the form of nested maps
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		if (!((Map<String, Object>) pmaSlideInfos.get(sessionID)).containsKey(slideRef)) {
			try {
				String url = apiUrl(sessionID, false) + "GetImageInfo?SessionID=" + PMA.pmaQ(sessionID) + "&pathOrUid="
						+ PMA.pmaQ(slideRef);
				URL urlResource = new URL(url);
				HttpURLConnection con;
				if (url.startsWith("https")) {
					con = (HttpsURLConnection) urlResource.openConnection();
				} else {
					con = (HttpURLConnection) urlResource.openConnection();
				}
				con.setRequestMethod("GET");
				String jsonString = PMA.getJSONAsStringBuffer(con).toString();
				if (PMA.isJSONObject(jsonString)) {
					JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (PMA.logger != null) {
							PMA.logger.severe("ImageInfo to " + slideRef + " resulted in: "
									+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
						}
						throw new Exception("ImageInfo to " + slideRef + " resulted in: " + jsonResponse.get("Message")
								+ " (keep in mind that slideRef is case sensitive!)");
					} else if (jsonResponse.has("d")) {
						// we convert the Json object to a Map<String, Object>
						Map<String, Object> jsonMap = objectMapper.readerFor(new TypeReference<Map<String, Object>>() {
						}).with(DeserializationFeature.USE_LONG_FOR_INTS).readValue(jsonResponse.get("d").toString());
						// we store the map created for both the slide name & the UID
						((Map<String, Object>) pmaSlideInfos.get(sessionID))
								.put(jsonResponse.getJSONObject("d").optString("Filename"), jsonMap);
//						((Map<String, Object>) pmaSlideInfos.get(sessionID))
//								.put(jsonResponse.getJSONObject("d").optString("UID"), jsonMap);
					} else {
						// we convert the Json object to a Map<String, Object>
						Map<String, Object> jsonMap = objectMapper.readerFor(new TypeReference<Map<String, Object>>() {
						}).with(DeserializationFeature.USE_LONG_FOR_INTS).readValue(jsonResponse.toString());
						// we store the map created for both the slide name & the UID
						((Map<String, Object>) pmaSlideInfos.get(sessionID)).put(jsonResponse.getString("Filename"),
								jsonMap);
						if (!sessionID.equals(pmaCoreLiteSessionID)) {
							((Map<String, Object>) pmaSlideInfos.get(sessionID)).put(jsonResponse.getString("UID"),
									jsonMap);
						}
					}
				} else {
					// JSONArray jsonResponse = getJSONArrayResponse(jsonString);
					// pmaAmountOfDataDownloaded.put(sessionID,
					// pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					// ((Map<String, Object>) pmaSlideInfos.get(sessionID)).put(slideRef,
					// jsonResponse);
					return null;
				}
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
		return (Map<String, Object>) ((Map<String, Object>) pmaSlideInfos.get(sessionID)).get(slideRef);
	}

	/**
	 * This method is used to get raw images in the form of nested maps
	 * 
	 * @param slideRefs List of slides' path or UID
	 * @param varargs   Array of optional arguments
	 *                  <p>
	 *                  sessionID : First optional argument(String), default
	 *                  value(null), session's ID
	 *                  </p>
	 * @return Nested maps forming raw images
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Map<String, Object>> getSlidesInfo(List<String> slideRefs, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Return raw image information in the form of nested maps
		sessionID = sessionId(sessionID);
		List<String> slideRefsNew = new ArrayList<>();
		for (String slideRef : slideRefs) {
			if (slideRef.startsWith("/")) {
				slideRef = slideRef.substring(1);
			}
			if (!((Map<String, Object>) pmaSlideInfos.get(sessionID)).containsKey(slideRef)) {
				slideRefsNew.add(slideRef);
			}
		}
		if (slideRefsNew.size() > 0) {
			try {
				String url = apiUrl(sessionID, false) + "GetImagesInfo";
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
				// we convert the list of slide to a string of this fashion :
				// ["slide1","slide2"....]
				String slideRefsNewForJson = slideRefsNew.stream().map(n -> ("\"" + n + "\""))
						.collect(Collectors.joining(",", "[", "]"));
				String input = "{ \"sessionID\": \"" + sessionID + "\", \"pathOrUids\": " + slideRefsNewForJson + "}";
				OutputStream os = con.getOutputStream();
				os.write(input.getBytes("UTF-8"));
				os.close();
				String jsonString = PMA.getJSONAsStringBuffer(con).toString();
				if (PMA.isJSONObject(jsonString)) {
					JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (PMA.logger != null) {
							PMA.logger.severe("ImageInfos to " + slideRefs.toString() + " resulted in: "
									+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
						}
						throw new Exception("ImageInfos to " + slideRefs.toString() + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					} else if (jsonResponse.has("d")) {
						JSONArray jsonArrayResponse = jsonResponse.getJSONArray("d");
						for (int i = 0; i < jsonArrayResponse.length(); i++) {
							// we convert the Json object to a Map<String, Object>
							Map<String, Object> jsonMap = objectMapper
									.readerFor(new TypeReference<Map<String, Object>>() {
									}).with(DeserializationFeature.USE_LONG_FOR_INTS)
									.readValue(jsonArrayResponse.getJSONObject(i).toString());
							// we store the map created for both the slide name & the UID
							((Map<String, Object>) pmaSlideInfos.get(sessionID))
									.put(jsonArrayResponse.getJSONObject(i).getString("Filename"), jsonMap);
//							((Map<String, Object>) pmaSlideInfos.get(sessionID))
//									.put(jsonArrayResponse.getJSONObject(i).getString("UID"), jsonMap);
						}
					} else {
						return null;
					}
				} else {
					JSONArray jsonArrayResponse = PMA.getJSONArrayResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonArrayResponse.length());
					for (int i = 0; i < jsonArrayResponse.length(); i++) {
						// we convert the Json object to a Map<String, Object>
						Map<String, Object> jsonMap = objectMapper.readerFor(new TypeReference<Map<String, Object>>() {
						}).with(DeserializationFeature.USE_LONG_FOR_INTS)
								.readValue(jsonArrayResponse.getJSONObject(i).toString());
						// we store the map created for both the slide name & the UID
						((Map<String, Object>) pmaSlideInfos.get(sessionID))
								.put(jsonArrayResponse.getJSONObject(i).getString("Filename"), jsonMap);
						if (!sessionID.equals(pmaCoreLiteSessionID)) {
							((Map<String, Object>) pmaSlideInfos.get(sessionID))
									.put(jsonArrayResponse.getJSONObject(i).getString("UID"), jsonMap);
						}
					}
				}
				Map<String, Map<String, Object>> results = new HashMap<String, Map<String, Object>>();
				for (String slide : slideRefs) {
					results.put(slide,
							(Map<String, Object>) ((Map<String, Object>) pmaSlideInfos.get(sessionID)).get(slide));
				}
				return results;
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
		// if for all the slides, the image info data has been already stored on
		// pmaSlideInfos
		Map<String, Map<String, Object>> results = new HashMap<String, Map<String, Object>>();
		for (String slide : slideRefs) {
			results.put(slide, (Map<String, Object>) ((Map<String, Object>) pmaSlideInfos.get(sessionID)).get(slide));
		}
		return results;
	}

	/**
	 * This method is used to determine the maximum zoom level that still represents
	 * an optical magnification
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Max zoom level that still represents an optical magnification
	 */
	public static int getMaxZoomLevel(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Determine the maximum zoomlevel that still represents an optical
		// magnification
		Map<String, Object> info = getSlideInfo(slideRef, sessionID);
		if (info == null) {
			System.out.print("Unable to get information for " + slideRef + " from " + sessionID);
			return 0;
		} else if (info.containsKey("MaxZoomLevel")) {
			try {
				return Integer.parseInt(info.get("MaxZoomLevel").toString());
			} catch (Exception e) {
				System.out.print("Something went wrong consulting the MaxZoomLevel key in info Map; value ="
						+ info.get("MaxZoomLevel").toString());
				e.printStackTrace();
				if (PMA.logger != null) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					PMA.logger.severe(sw.toString());
					PMA.logger.severe("Something went wrong consulting the MaxZoomLevel key in info Map; value ="
							+ info.get("MaxZoomLevel").toString());
				}
				return 0;
			}
		} else {
			try {
				return Integer.parseInt(info.get("NumberOfZoomLevels").toString());
			} catch (Exception e) {
				System.out.print("Something went wrong consulting the NumberOfZoomLevels key in info Map; value ="
						+ info.get("NumberOfZoomLevels").toString());
				e.printStackTrace();
				if (PMA.logger != null) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					PMA.logger.severe(sw.toString());
					PMA.logger.severe("Something went wrong consulting the NumberOfZoomLevels key in info Map; value ="
							+ info.get("NumberOfZoomLevels").toString());
				}
				return 0;
			}
		}
	}

	/**
	 * This method is used to get list with all zoom levels, from 0 to max zoom
	 * level
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 minNumberOfTiles : Second optional argument(Integer), default
	 *                 value(0), minimal number of tiles used to specify that you're
	 *                 only interested in zoom levels that include at least a given
	 *                 number of tiles
	 *                 </p>
	 * @return List with all zoom levels, from 0 to max zoom level
	 */
	public static List<Integer> getZoomLevelsList(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		Integer minNumberOfTiles = 0;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getZoomLevelsList() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getZoomLevelsList() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			minNumberOfTiles = (Integer) varargs[1];
		}
		// Obtain a list with all zoom levels, starting with 0 and up to and including
		// max zoom level
		// Use min_number_of_tiles argument to specify that you're only interested in
		// zoom levels that include at lease a given number of tiles
		List<Integer> result = new ArrayList<>();
		Set<Integer> set = getZoomLevelsDict(slideRef, sessionID, minNumberOfTiles).keySet();
		for (Integer i : set) {
			result.add(i);
		}
		Collections.sort(result);
		return result;
	}

	/**
	 * This method is used to get a map with the number of tiles per zoom level
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 minNumberOfTiles : Second optional argument(Integer), default
	 *                 value(0), minimal number of tiles used to specify that you're
	 *                 only interested in zoom levels that include at least a given
	 *                 number of tiles
	 *                 </p>
	 * @return Map with the number of tiles per zoom level
	 */
	public static Map<Integer, List<Integer>> getZoomLevelsDict(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		Integer minNumberOfTiles = 0;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getZoomLevelsDict() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getZoomLevelsDict() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			minNumberOfTiles = (Integer) varargs[1];
		}
		// Obtain a map with the number of tiles per zoom level.
		// Information is returned as (x, y, n) lists per zoom level, with
		// x = number of horizontal tiles,
		// y = number of vertical tiles,
		// n = total number of tiles at specified zoom level (x * y)
		// Use min_number_of_tiles argument to specify that you're only interested in
		// zoom levels that include at least a given number of tiles
		List<Integer> zoomLevels = new ArrayList<Integer>();
		IntStream.range(0, getMaxZoomLevel(slideRef, sessionID) + 1).forEach(n -> {
			zoomLevels.add(n);
		});
		List<List<Integer>> dimensions = new ArrayList<>();
		for (int z : zoomLevels) {
			if (getNumberOfTiles(slideRef, z, sessionID).get(2) > minNumberOfTiles) {
				dimensions.add(getNumberOfTiles(slideRef, z, sessionID));
			}
		}
		List<Integer> zoomLevelsSubList = (List<Integer>) zoomLevels.subList(zoomLevels.size() - dimensions.size(),
				zoomLevels.size());
		Map<Integer, List<Integer>> d = new HashMap<>();
		for (int i = 0; i < zoomLevelsSubList.size() && i < dimensions.size(); i++) {
			d.put(zoomLevelsSubList.get(i), dimensions.get(i));
		}
		return d;
	}

	/**
	 * This method is used to get the physical dimension in terms of pixels per
	 * micrometer of a slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 zoomLevel : First optional argument(Integer), default
	 *                 value(null), zoom level
	 *                 </p>
	 *                 <p>
	 *                 sessionID : Second optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Two items list containing the physical dimension in terms of pixels
	 *         per micrometer of a slide
	 */
	public static List<Float> getPixelsPerMicrometer(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		Integer zoomLevel = null;
		String sessionID = null;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getZoomLevelsDict() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof String) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getZoomLevelsDict() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[1];
		}
		// Retrieve the physical dimension in terms of pixels per micrometer.
		// When zoom level is left to its default value of None, dimensions at the
		// highest zoom level are returned
		// (in effect returning the "native" resolution at which the slide was
		// registered)
		int maxZoomLevel = getMaxZoomLevel(slideRef, sessionID);
		Map<String, Object> info = getSlideInfo(slideRef, sessionID);
		float xppm = (float) info.get("MicrometresPerPixelX");
		float yppm = (float) info.get("MicrometresPerPixelY");
		List<Float> result = new ArrayList<>();
		if ((zoomLevel == null) || (zoomLevel == maxZoomLevel)) {
			result.add(xppm);
			result.add(yppm);
			return result;
		} else {
			double factor = Math.pow(2, zoomLevel - maxZoomLevel);
			result.add((float) (xppm / factor));
			result.add((float) (yppm / factor));
			return result;
		}
	}

	/**
	 * This method is used to get the total dimensions of a slide image at a given
	 * zoom level
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 zoomLevel : First optional argument(Integer), default
	 *                 value(null), zoom level
	 *                 </p>
	 *                 <p>
	 *                 sessionID : Second optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Two items list with the total dimensions of a slide image at a given
	 *         zoom level
	 */
	public static List<Integer> getPixelDimensions(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		Integer zoomLevel = null;
		String sessionID = null;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getPixelDimensions() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof String) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getPixelDimensions() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[1];
		}
		// Get the total dimensions of a slide image at a given zoom level
		int maxZoomLevel = getMaxZoomLevel(slideRef, sessionID);
		Map<String, Object> info = getSlideInfo(slideRef, sessionID);
		List<Integer> result = new ArrayList<>();
		if (zoomLevel == null || zoomLevel == maxZoomLevel) {
			result.add(Integer.parseInt(info.get("Width").toString()));
			result.add(Integer.parseInt(info.get("Height").toString()));
			return result;
		} else {
			double factor = Math.pow(2, zoomLevel - maxZoomLevel);
			result.add((int) (Integer.parseInt(info.get("Width").toString()) * factor));
			result.add((int) (Integer.parseInt(info.get("Height").toString()) * factor));
			return result;
		}
	}

	/**
	 * This method is used to determine the number of tiles needed to reconstitute a
	 * slide at a given zoom level
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 zoomLevel : First optional argument(Integer), default
	 *                 value(null), zoom level
	 *                 </p>
	 *                 <p>
	 *                 sessionID : Second optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Three items list to determine the number of tiles needed to
	 *         reconstitute a slide at a given zoom level
	 */
	public static List<Integer> getNumberOfTiles(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		Integer zoomLevel = null;
		String sessionID = null;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getNumberOfTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof String) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getNumberOfTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[1];
		}
		// Determine the number of tiles needed to reconstitute a slide at a given
		// zoomlevel
		List<Integer> pixels = getPixelDimensions(slideRef, zoomLevel, sessionID);
		List<Integer> sz = getTileSize(sessionID);
		int xTiles = (int) Math.ceil((double) pixels.get(0) / (double) sz.get(0));
		int yTiles = (int) Math.ceil((double) pixels.get(1) / (double) sz.get(0));
		int nTiles = xTiles * yTiles;
		List<Integer> result = new ArrayList<>();
		result.add(xTiles);
		result.add(yTiles);
		result.add(nTiles);
		return result;
	}

	/**
	 * This method is used to Determine the physical dimensions of the sample
	 * represented by the slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Two items list to determine the physical dimensions of the sample
	 *         represented by the slide
	 */
	public static List<Float> getPhysicalDimensions(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Determine the physical dimensions of the sample represented by the slide.
		// This is independent of the zoom level: the physical properties don't change
		// because the magnification changes
		List<Float> ppmData = getPixelsPerMicrometer(slideRef, sessionID);
		List<Integer> pixelSz = getPixelDimensions(slideRef, sessionID);
		List<Float> result = new ArrayList<>();
		result.add(pixelSz.get(0) * ppmData.get(0));
		result.add(pixelSz.get(1) * ppmData.get(1));
		return result;
	}

	/**
	 * This method is used to get the number of channels for a slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Number of channels for a slide (1 when slide is brightfield)
	 */
	@SuppressWarnings("unchecked")
	public static int getNumberOfChannels(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Number of fluorescent channels for a slide (when slide is brightfield, return
		// is always 1)
		Map<String, Object> info = getSlideInfo(slideRef, sessionID);
		return ((List<Object>) ((List<Map<String, Object>>) ((List<Map<String, Object>>) info.get("TimeFrames")).get(0)
				.get("Layers")).get(0).get("Channels")).size();
	}

	/**
	 * This method is used to get the number of (z-stacked) layers for a slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Number of layers for a slide
	 */
	@SuppressWarnings("unchecked")
	public static int getNumberOfLayers(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Number of (z-stacked) layers for a slide
		Map<String, Object> info = getSlideInfo(slideRef, sessionID);
		return ((List<Object>) ((List<Map<String, Object>>) info.get("TimeFrames")).get(0).get("Layers")).size();
	}

	/**
	 * This method is used to get the number of (z-stacked) layers for a slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Number of Z-Stack layers for a slide
	 */
	public static int getNumberOfZStackLayers(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		return getNumberOfLayers(slideRef, sessionID);
	}

	/**
	 * This method is used to determine whether a slide is a fluorescent image or
	 * not
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return True if slide is a fluorescent image, false otherwise
	 */
	public static Boolean isFluorescent(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Determine whether a slide is a fluorescent image or not
		return getNumberOfChannels(slideRef, sessionID) > 1;
	}

	/**
	 * This method is used to determine whether a slide contains multiple (stacked)
	 * layers or not
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return True if slide contains multiple (stacked) layers, false otherwise
	 */
	public static Boolean isMultiLayer(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Determine whether a slide contains multiple (stacked) layers or not
		return getNumberOfLayers(slideRef, sessionID) > 1;
	}

	/**
	 * This method is used to determine whether a slide is a z-stack or not
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return True if slide is a z-stack, false otherwise
	 */
	public static Boolean isZStack(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Determine whether a slide is a z-stack or not
		return isMultiLayer(slideRef, sessionID);
	}

	/**
	 * This method is used to get the magnification represented at a certain zoom
	 * level
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 zoomLevel : First optional argument(Integer), default
	 *                 value(false), zoom level
	 *                 </p>
	 *                 <p>
	 *                 exact : Second optional argument(Boolean), default
	 *                 value(null), defines if exact or not
	 *                 </p>
	 *                 <p>
	 *                 sessionID : Third optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Magnification represented at a certain zoom level
	 */
	public static int getMagnification(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		Integer zoomLevel = null;
		Boolean exact = false;
		String sessionID = null;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getMagnification() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Boolean) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getMagnification() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			exact = (Boolean) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof String) && varargs[2] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getMagnification() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[2];
		}
		// Get the magnification represented at a certain zoom level
		float ppm = getPixelsPerMicrometer(slideRef, zoomLevel, sessionID).get(0);
		if (ppm > 0) {
			if (exact == true) {
				return (int) (40 / (ppm / 0.25));
			} else {
				return (int) (40 / ((int) (ppm / 0.25)));
			}

		} else {
			return 0;
		}
	}

	/**
	 * This method is used to get the URL that points to the barcode (alias for
	 * "label") for a slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return URL that points to the barcode (alias for "label") for a slide
	 */
	public static String getBarcodeUrl(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the URL that points to the barcode (alias for "label") for a slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String url;
		try {
			url = pmaUrl(sessionID) + "barcode" + "?SessionID=" + PMA.pmaQ(sessionID) + "&pathOrUid="
					+ PMA.pmaQ(slideRef);
			return url;
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
	 * This method is used to get the barcode (alias for "label") image for a slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Barcode (alias for "label") image for a slide
	 */
	public static Image getBarcodeImage(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the barcode (alias for "label") image for a slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		try {
			URL urlResource = new URL(getBarcodeUrl(slideRef, sessionID));
			URLConnection con = urlResource.openConnection();
			Image img = ImageIO.read(con.getInputStream());
			pmaAmountOfDataDownloaded.put(sessionID,
					pmaAmountOfDataDownloaded.get(sessionID) + con.getInputStream().toString().length());
			return img;
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
	 * This method is used to get the text encoded by the barcode
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return The barcode text
	 */
	public static String getBarcodeText(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the text encoded by the barcode (if there IS a barcode on the slide to
		// begin with)
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String barcode;
		String url = apiUrl(sessionID, false) + "GetBarcodeText?sessionID=" + PMA.pmaQ(sessionID) + "&pathOrUid="
				+ PMA.pmaQ(slideRef);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("get_barcode_text on " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					}
					throw new Exception("get_barcode_text on " + slideRef + " resulted in: "
							+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
				} else {
					return jsonResponse.getString("d").equals("null") ? null : jsonResponse.getString("d");
				}
			} else {
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonString.length());
				barcode = jsonString.replace("\"", "");
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
		return barcode;
	}

	/**
	 * This method is used to get the URL that points to the label for a slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Url that points to the label for a slide
	 */
	public static String getLabelUrl(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the URL that points to the label for a slide
		return getBarcodeUrl(slideRef, sessionID);
	}

	/**
	 * This method is used to get the label image for a slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Image label for a slide
	 */
	public static Image getLabelImage(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the label image for a slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		try {
			URL urlResource = new URL(getLabelUrl(slideRef, sessionID));
			URLConnection con = urlResource.openConnection();
			Image img = ImageIO.read(con.getInputStream());
			pmaAmountOfDataDownloaded.put(sessionID,
					pmaAmountOfDataDownloaded.get(sessionID) + con.getInputStream().toString().length());
			return img;
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
	 * This method is used to get the URL that points to the thumbnail for a slide
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 height : Second optional argument(Integer), default value(0),
	 *                 height of the requested thumbnail, if value set to 0 it will
	 *                 be ignored
	 *                 </p>
	 *                 <p>
	 *                 width : Third optional argument(Integer), default value(0),
	 *                 width of the requested thumbnail, if value set to 0 it will
	 *                 be ignored
	 *                 </p>
	 * @return URL that points to the thumbnail for a slide
	 */
	public static String getThumbnailUrl(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		Integer height = 0;
		Integer width = 0;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getThumbnailUrl() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getThumbnailUrl() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			height = (Integer) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof Integer) && varargs[2] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getThumbnailUrl() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			width = (Integer) varargs[2];
		}
		// Get the URL that points to the thumbnail for a slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String url;
		try {
			url = pmaUrl(sessionID) + "thumbnail" + "?SessionID=" + PMA.pmaQ(sessionID) + "&pathOrUid="
					+ PMA.pmaQ(slideRef) + ((height > 0) ? "&h=" + height.toString() : "")
					+ ((width > 0) ? "&w=" + width.toString() : "");
			return url;
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
	 * This method is used to get the thumbnail image for a slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 height : Second optional argument(Integer), default value(0),
	 *                 height of the requested thumbnail, if value set to 0 it will
	 *                 be ignored
	 *                 </p>
	 *                 <p>
	 *                 width : Third optional argument(Integer), default value(0),
	 *                 width of the requested thumbnail, if value set to 0 it will
	 *                 be ignored
	 *                 </p>
	 * @return Image thumbnail for a slide
	 */
	public static Image getThumbnailImage(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		Integer height = 0;
		Integer width = 0;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getThumbnailImage() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getThumbnailImage() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			height = (Integer) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof Integer) && varargs[2] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getThumbnailImage() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			width = (Integer) varargs[2];
		}
		// Get the thumbnail image for a slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		try {
			String url = getThumbnailUrl(slideRef, sessionID, height, width);
			URL urlResource = new URL(url);
			URLConnection con = urlResource.openConnection();
			Image img = ImageIO.read(con.getInputStream());
			pmaAmountOfDataDownloaded.put(sessionID,
					pmaAmountOfDataDownloaded.get(sessionID) + con.getInputStream().toString().length());
			return img;
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
	 * This method is used to get a single tile at position (x, y)
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 x : First optional argument(Integer), default value(0), x
	 *                 position
	 *                 </p>
	 *                 <p>
	 *                 y : Second optional argument(Integer), default value(0), y
	 *                 position
	 *                 </p>
	 *                 <p>
	 *                 zoomLevel : Third optional argument(Integer), default
	 *                 value(null), zoom level
	 *                 </p>
	 *                 <p>
	 *                 zStack : Fourth optional argument(Integer), default value(0),
	 *                 Number of z stacks
	 *                 </p>
	 *                 <p>
	 *                 sessionID : Fifth optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 format : Sixth optional argument(String), default value(jpg),
	 *                 image format
	 *                 </p>
	 *                 <p>
	 *                 quality : Seventh optional argument(Integer), default
	 *                 value(100), quality
	 *                 </p>
	 * @return Single tile at position (x, y)
	 * @throws Exception if unable to determine the PMA.core instance the session ID
	 *                   belong to
	 */
	public static Image getTile(String slideRef, Object... varargs) throws Exception {
		// setting the default values when arguments' values are omitted
		Integer x = 0;
		Integer y = 0;
		Integer zoomLevel = null;
		Integer zStack = 0;
		String sessionID = null;
		String format = "jpg";
		Integer quality = 100;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			x = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			y = (Integer) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof Integer) && varargs[2] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[2];
		}
		if (varargs.length > 3) {
			if (!(varargs[3] instanceof Integer) && varargs[3] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			zStack = (Integer) varargs[3];
		}
		if (varargs.length > 4) {
			if (!(varargs[4] instanceof String) && varargs[4] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[4];
		}
		if (varargs.length > 5) {
			if (!(varargs[5] instanceof String) && varargs[5] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			format = (String) varargs[5];
		}
		if (varargs.length > 6) {
			if (!(varargs[6] instanceof Integer) && varargs[6] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			quality = (Integer) varargs[6];
		}
		// Get a single tile at position (x, y)
		// Format can be 'jpg' or 'png'
		// Quality is an integer value and varies from 0
		// (as much compression as possible; not recommended) to 100 (100%, no
		// compression)
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		if (zoomLevel == null) {
			zoomLevel = 0;
		}
		String url;
		url = pmaUrl(sessionID);
		if (url == null) {
			if (PMA.logger != null) {
				PMA.logger.severe("Unable to determine the PMA.core instance belonging to " + sessionID);
			}
			throw new Exception("Unable to determine the PMA.core instance belonging to " + sessionID);
		}
		url = "tile" + "?SessionID=" + PMA.pmaQ(sessionID) + "&channels=" + PMA.pmaQ("0") + "&layer="
				+ zStack.toString() + "&timeframe=" + PMA.pmaQ("0") + "&layer=" + PMA.pmaQ("0") + "&pathOrUid="
				+ PMA.pmaQ(slideRef) + "&x=" + x.toString() + "&y=" + y.toString() + "&z=" + zoomLevel.toString()
				+ "&format=" + PMA.pmaQ(format) + "&quality=" + PMA.pmaQ(quality.toString()) + "&cache="
				+ pmaUseCacheWhenRetrievingTiles.toString().toLowerCase();
		try {
			URL urlResource = new URL(url);
			URLConnection con = urlResource.openConnection();
			Image img = ImageIO.read(con.getInputStream());
			pmaAmountOfDataDownloaded.put(sessionID,
					pmaAmountOfDataDownloaded.get(sessionID) + con.getInputStream().toString().length());
			return img;
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
	 * This method is used to get all tiles with a (fromX, fromY, toX, toY)
	 * rectangle
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 fromX : First optional argument(Integer), default value(0),
	 *                 starting x position
	 *                 </p>
	 *                 <p>
	 *                 fromY : Second optional argument(Integer), default value(0),
	 *                 starting y position
	 *                 </p>
	 *                 <p>
	 *                 toX : Third optional argument(Integer), default value(0),
	 *                 ending x position
	 *                 </p>
	 *                 <p>
	 *                 toY : Fourth optional argument(Integer), default value(0),
	 *                 ending y position
	 *                 </p>
	 *                 <p>
	 *                 zoomLevel : Fifth optional argument(Integer), default
	 *                 value(null), zoom level
	 *                 </p>
	 *                 <p>
	 *                 zStack : Sixth optional argument(Integer), default value(0),
	 *                 Number of z stacks
	 *                 </p>
	 *                 <p>
	 *                 sessionID : Seventh optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 format : Eigth optional argument(String), default value(jpg),
	 *                 image format
	 *                 </p>
	 *                 <p>
	 *                 quality : Ninth optional argument(Integer), default
	 *                 value(100), quality
	 *                 </p>
	 * @return All tiles with a (fromX, fromY, toX, toY) rectangle
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Stream getTiles(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		Integer fromX = 0;
		Integer fromY = 0;
		Integer toX = null;
		Integer toY = null;
		Integer zoomLevel = null;
		Integer zStack = 0;
		String sessionID = null;
		String format = "jpg";
		Integer quality = 100;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			fromX = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			fromY = (Integer) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof Integer) && varargs[2] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			toX = (Integer) varargs[2];
		}
		if (varargs.length > 3) {
			if (!(varargs[3] instanceof Integer) && varargs[3] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			toY = (Integer) varargs[3];
		}
		if (varargs.length > 4) {
			if (!(varargs[4] instanceof Integer) && varargs[4] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[4];
		}
		if (varargs.length > 5) {
			if (!(varargs[5] instanceof Integer) && varargs[5] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			zStack = (Integer) varargs[5];
		}
		if (varargs.length > 6) {
			if (!(varargs[6] instanceof String) && varargs[6] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[6];
		}
		if (varargs.length > 7) {
			if (!(varargs[7] instanceof String) && varargs[7] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			format = (String) varargs[7];
		}
		if (varargs.length > 8) {
			if (!(varargs[8] instanceof Integer) && varargs[8] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			quality = (Integer) varargs[8];
		}
		// Get all tiles with a (fromX, fromY, toX, toY) rectangle. Navigate left to
		// right, top to bottom
		// Format can be 'jpg' or 'png'
		// Quality is an integer value and varies from 0
		// (as much compression as possible; not recommended) to 100 (100%, no
		// compression)
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		if (zoomLevel == null) {
			zoomLevel = 0;
		}
		if (toX == null) {
			toX = getNumberOfTiles(slideRef, zoomLevel, sessionID).get((0));
		}
		if (toY == null) {
			toY = getNumberOfTiles(slideRef, zoomLevel, sessionID).get((1));
		}
		// we declare final variable to use them in the enclosing scope for new
		// Supplier()
		final int varFromX = fromX;
		final int varToX = toX;
		final int varFromY = fromY;
		final int varToY = toY;
		final int varZoomLevel = zoomLevel;
		final int varZStack = zStack;
		final String varSessionID = sessionID;
		final String varSlideRef = slideRef;
		final String varFormat = format;
		final Integer varQualty = quality;
		// we use Stream to simulate the behavior of "yield" in Python
		return Stream.generate(new Supplier() {
			int x = varFromX;
			int y = varFromY - 1;

			@Override
			public Image get() {
				if (x <= varToX) {
					if (y < varToY) {
						y++;
					} else {
						y = varFromY - 1;
						x++;
					}
				}
				try {
					return getTile(varSlideRef, x, y, varZoomLevel, varZStack, varSessionID, varFormat, varQualty);
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
		}).limit((varToX - varFromX + 1) * (varToY - varFromY + 1));
	}

	/**
	 * This method is used to find out what forms where submitted for a specific
	 * slide
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Map of forms submitted for a defined slide
	 */
	public static Map<String, String> getSubmittedForms(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Find out what forms where submitted for a specific slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String url = apiUrl(sessionID, false) + "GetFormSubmissions?sessionID=" + PMA.pmaQ(sessionID) + "&pathOrUids="
				+ PMA.pmaQ(slideRef);
		Map<String, String> forms = new HashMap<>();
		Map<String, String> allForms = getAvailableForms(slideRef, sessionID);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (jsonString != null && jsonString.length() > 0) {
				if (PMA.isJSONObject(jsonString)) {
					JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (PMA.logger != null) {
							PMA.logger.severe("getSubmittedForms on  " + slideRef + " resulted in: "
									+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
						}
						throw new Exception("getSubmittedForms on  " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					} else {
						forms = null;
					}
				} else {
					JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					for (int i = 0; i < jsonResponse.length(); i++) {
						if (!forms.containsKey(jsonResponse.optJSONObject(i).get("FormID").toString())
								&& allForms != null) {
							forms.put(jsonResponse.optJSONObject(i).get("FormID").toString(),
									allForms.get(jsonResponse.optJSONObject(i).get("FormID").toString()));
						}
					}
					// should probably do some post-processing here, but unsure what that would
					// actually be??

				}
			} else {
				forms = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
		return forms;
	}

	/**
	 * This method is used to get submitted forms data in json Array format
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Submitted forms data in json Array format
	 */
	public static JSONArray getSubmittedFormData(String slideRef, String... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get all submitted form data associated with a specific slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		JSONArray data; // new HashMap<>();
		String url = apiUrl(sessionID, false) + "GetFormSubmissions?sessionID=" + PMA.pmaQ(sessionID) + "&pathOrUids="
				+ PMA.pmaQ(slideRef);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (jsonString != null && jsonString.length() > 0) {
				if (PMA.isJSONObject(jsonString)) {
					JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (PMA.logger != null) {
							PMA.logger.severe("getSubmittedFormData on  " + slideRef + " resulted in: "
									+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
						}
						throw new Exception("getSubmittedFormData on  " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					} else {
						data = null;
					}
				} else {
					JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					data = jsonResponse;
				}
				// should probably do some post-processing here, but unsure what that would
				// actually be??
			} else {
				data = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
		return data;
	}

	/**
	 * This method is used to prepare a form-dictionary that can be used later on to
	 * submit new form data for a slide
	 * 
	 * @param formID  Form's ID
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return Form-map that can be used later on to submit new form data for a
	 *         slide
	 */
	public static Map<String, String> prepareFormMap(String formID, String... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Prepare a form-dictionary that can be used later on to submit new form data
		// for a slide
		if (formID == null) {
			return null;
		}
		sessionID = sessionId(sessionID);
		Map<String, String> formDef = new HashMap<>();
		String url = apiUrl(sessionID, false) + "GetFormDefinitions?sessionID=" + PMA.pmaQ(sessionID);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (jsonString != null && jsonString.length() > 0) {
				if (PMA.isJSONObject(jsonString)) {
					JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (PMA.logger != null) {
							PMA.logger.severe("" + jsonResponse.get("Message") + "");
						}
						throw new Exception("" + jsonResponse.get("Message") + "");
					} else {
						formDef = null;
					}
				} else {
					JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					for (int i = 0; i < jsonResponse.length(); i++) {
						if ((jsonResponse.optJSONObject(i).get("FormID").toString().equals(formID))
								|| (jsonResponse.optJSONObject(i).get("FormName").toString().equals(formID))) {
							for (int j = 0; j < jsonResponse.optJSONObject(i).getJSONArray("FormFields")
									.length(); j++) {
								formDef.put(jsonResponse.optJSONObject(i).getJSONArray("FormFields").getJSONObject(j)
										.getString("Label"), null);
							}
						}

					}
				}
			} else {
				formDef = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
		return formDef;
	}

	/**
	 * This method is used to get a Map of the forms available to fill out, either
	 * system-wide (leave slideref to "null"), or for a particular slide
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                slideRef : First optional argument(String), default
	 *                value(null), slide's path
	 *                </p>
	 *                <p>
	 *                sessionID : Second optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return Map of the forms available to fill out, either system-wide (leave
	 *         slideref to "null"), or for a particular slide
	 */
	public static Map<String, String> getAvailableForms(String... varargs) {
		// setting the default values when arguments' values are omitted
		String slideRef = varargs.length > 0 ? varargs[0] : null;
		String sessionID = varargs.length > 0 ? varargs[1] : null;
		// See what forms are available to fill out, either system-wide (leave slideref
		// to None), or for a particular slide
		sessionID = sessionId(sessionID);
		String url;
		Map<String, String> forms = new HashMap<>();
		if (slideRef != null) {
			if (slideRef.startsWith("/")) {
				slideRef = slideRef.substring(1);
			}
			String dir = FilenameUtils.getFullPath(slideRef).substring(0,
					FilenameUtils.getFullPath(slideRef).length() - 1);
			url = apiUrl(sessionID, false) + "GetForms?sessionID=" + PMA.pmaQ(sessionID) + "&path=" + PMA.pmaQ(dir);
		} else {
			url = apiUrl(sessionID, false) + "GetForms?sessionID=" + PMA.pmaQ(sessionID);
		}
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (jsonString != null && jsonString.length() > 0) {
				if (PMA.isJSONObject(jsonString)) {
					JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (PMA.logger != null) {
							PMA.logger.severe("getAvailableForms on  " + slideRef + " resulted in: "
									+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
						}
						throw new Exception("getAvailableForms on  " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					} else {
						forms = null;
					}
				} else {
					JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					for (int i = 0; i < jsonResponse.length(); i++) {
						forms.put(jsonResponse.optJSONObject(i).get("Key").toString(),
								jsonResponse.optJSONObject(i).getString("Value"));
					}
				}
			} else {
				forms = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
		return forms;
	}

	/**
	 * To be elaborated later
	 * 
	 * @param slideRef To be elaborated later
	 * @param formID   To be elaborated later
	 * @param formMap  To be elaborated later
	 * @param varargs  To be elaborated later
	 * @return To be elaborated later
	 */
	public static String submitFormData(String slideRef, String formID, String formMap, String... varargs) {
		// setting the default value when argument' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Not implemented yet
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		return null;
	}

	/**
	 * This method is used to retrieve the annotations for slide slideRef
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Annotations for a slide in a json Array format
	 */
	public static JSONArray getAnnotations(String slideRef, String... varargs) {
		// setting the default value when argument' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Retrieve the annotations for slide slideRef
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		// String dir = FilenameUtils.getFullPath(slideRef).substring(0,
		// FilenameUtils.getFullPath(slideRef).length() - 1);
		JSONArray data;
		String url = apiUrl(sessionID, false) + "GetAnnotations?sessionID=" + PMA.pmaQ(sessionID) + "&pathOrUid="
				+ PMA.pmaQ(slideRef);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (jsonString != null && jsonString.length() > 0) {
				if (PMA.isJSONObject(jsonString)) {
					JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (PMA.logger != null) {
							PMA.logger.severe("getAnnotations() on  " + slideRef + " resulted in: "
									+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
						}
						throw new Exception("getAnnotations() on  " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					} else {
						data = null;
					}
				} else {
					JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					data = jsonResponse;
				}
			} else {
				data = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
		return data;
	}

	/**
	 * This method is used to launch the default web browser and load a web-based
	 * viewer for the slide
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @throws Exception if unable to determine the PMA.core instance the session ID
	 *                   belongs to
	 */
	public static void showSlide(String slideRef, String... varargs) throws Exception {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Launch the default web browser and load a web-based viewer for the slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String osCmd;
		if (System.getProperty("os.name").toLowerCase().equals("posix")) {
			osCmd = "open ";
		} else {
			osCmd = "start ";
		}
		String url;
		if (sessionID == pmaCoreLiteSessionID) {
			url = "http://free.pathomation.com/pma-view-lite/?path=" + PMA.pmaQ(slideRef);
		} else {
			url = pmaUrl(sessionID);
			if (url == null) {
				if (PMA.logger != null) {
					PMA.logger.severe("Unable to determine the PMA.core instance belonging to " + sessionID);
				}
				throw new Exception("Unable to determine the PMA.core instance belonging to " + sessionID);
			}
			url = "viewer/index.htm" + "?sessionID=" + PMA.pmaQ(sessionID) + "^&pathOrUid=" + PMA.pmaQ(slideRef); // note
																													// the
																													// ^&
			// to escape
			// a regular
			// &
			if (PMA.debug) {
				System.out.println(url);
			}
		}
		try {
			Runtime.getRuntime().exec(osCmd + url);
		} catch (Exception e) {
			e.printStackTrace();
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
		}
	}

	/**
	 * This method is used to map of files related to a slide
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Map of all files related to a slide
	 */
	@SuppressWarnings("serial")
	public static Map<String, Map<String, String>> getFilesForSlide(String slideRef, String... varargs) {
		// setting the default value when argument's value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Obtain all files actually associated with a specific slide
		// This is most relevant with slides that are defined by multiple files, like
		// MRXS or VSI
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String url;
		if (sessionID == pmaCoreLiteSessionID) {
			url = apiUrl(sessionID, false) + "EnumerateAllFilesForSlide?sessionID=" + PMA.pmaQ(sessionID)
					+ "&pathOrUid=" + PMA.pmaQ(slideRef);
		} else {
			url = apiUrl(sessionID, false) + "GetFilenames?sessionID=" + PMA.pmaQ(sessionID) + "&pathOrUid="
					+ PMA.pmaQ(slideRef);
		}
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			JSONArray resultsArray;
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("getFilesForSlide on " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					}
					throw new Exception("getFilesForSlide on " + slideRef + " resulted in: "
							+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
				} else if (jsonResponse.has("d")) {
					resultsArray = jsonResponse.getJSONArray("d");
				} else {
					return null;
				}
			} else {
				resultsArray = PMA.getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + resultsArray.length());
			}
			Map<String, Map<String, String>> result = new HashMap<>();
			for (int i = 0; i < resultsArray.length(); i++) {
				final int finalI = i;
				if (sessionID == pmaCoreLiteSessionID) {
					result.put(resultsArray.getString(i), new HashMap<String, String>() {
						{
							put("Size", "0");
							put("LastModified", null);
						}
					});
				} else {
					result.put(resultsArray.getJSONObject(finalI).getString("Path"), new HashMap<String, String>() {
						{
							put("Size", String.valueOf(resultsArray.getJSONObject(finalI).getLong("Size")));
							put("LastModified", resultsArray.getJSONObject(finalI).getString("LastModified"));
						}
					});
				}
			}
			return result;
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
	 * This method is used to get list of files related to a slide for PMA.start
	 * ONLY
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return List of all files related to a selected slide for PMA.start ONLY
	 */
	public static List<String> enumerateFilesForSlide(String slideRef, String... varargs) {
		// setting the default value when argument's value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Obtain all files actually associated with a specific slide
		// This is most relevant with slides that are defined by multiple files, like
		// MRXS or VSI
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String url = apiUrl(sessionID, false) + "EnumerateAllFilesForSlide?sessionID=" + PMA.pmaQ(sessionID)
				+ "&pathOrUid=" + PMA.pmaQ(slideRef);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("enumerateFilesForSlide on " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					}
					throw new Exception("enumerateFilesForSlide on " + slideRef + " resulted in: "
							+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
				} else if (jsonResponse.has("d")) {
					JSONArray array = jsonResponse.getJSONArray("d");
					List<String> files = new ArrayList<>();
					for (int i = 0; i < array.length(); i++) {
						files.add(array.optString(i));
					}
					return files;
				} else {
					return null;
				}
			} else {
				JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				List<String> files = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					files.add(jsonResponse.optString(i));
				}
				return files;
			}

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
	 * This method is used to get list of files related to a slide for PMA.core ONLY
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return List of all files related to a selected slide for PMA.core ONLY
	 */
	@SuppressWarnings("serial")
	public static List<Map<String, String>> enumerateFilesForSlidePMACore(String slideRef, String... varargs) {
		// setting the default value when argument's value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Obtain all files actually associated with a specific slide
		// This is most relevant with slides that are defined by multiple files, like
		// MRXS or VSI
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String url = apiUrl(sessionID, false) + "GetFilenames?sessionID=" + PMA.pmaQ(sessionID) + "&pathOrUid="
				+ PMA.pmaQ(slideRef);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			if (PMA.isJSONArray(jsonString)) {
				JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				List<Map<String, String>> result = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					final int finalI = i;
					result.add(new HashMap<String, String>() {
						{
							put("LastModified", jsonResponse.getJSONObject(finalI).getString("LastModified"));
							put("Path", jsonResponse.getJSONObject(finalI).getString("Path"));
							put("Size", String.valueOf(jsonResponse.getJSONObject(finalI).getLong("Size")));
						}
					});
				}
				return result;
			} else {
				if (PMA.logger != null) {
					PMA.logger.severe("enumerateFilesForSlidePMACore() : Failure to get related files");
				}
				return null;
			}

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
	 * This method is used to search for slides in a directory that satisfy a
	 * certain search pattern
	 * 
	 * @param startDir Start directory
	 * @param pattern  Search pattern
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return List of slides in a directory that satisfy a certain search pattern
	 * @throws Exception If called on PMA.start
	 */
	public static List<String> searchSlides(String startDir, String pattern, String... varargs) throws Exception {
		// setting the default value when argument's value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		sessionID = sessionId(sessionID);
		if (sessionID.equals(pmaCoreLiteSessionID)) {
			if (isLite()) {
				throw new Exception("PMA.core.lite found running, but doesn't support searching.");
			} else {
				throw new Exception("PMA.core.lite not found, and besides; it doesn't support searching.");
			}
		}

		if (startDir.startsWith("/")) {
			startDir = startDir.substring(1);
		}
		String url = queryUrl(sessionID) + "Filename?sessionID=" + PMA.pmaQ(sessionID) + "&path=" + PMA.pmaQ(startDir)
				+ "&pattern=" + PMA.pmaQ(pattern);
		if (PMA.debug) {
			System.out.println("url = " + url);
		}
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			List<String> files = null;
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("searchSlides on " + pattern + " in " + startDir + "resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that startDir is case sensitive!)");
					}
					throw new Exception("searchSlides on " + pattern + " in " + startDir + "resulted in: "
							+ jsonResponse.get("Message") + " (keep in mind that startDir is case sensitive!)");
				} else if (jsonResponse.has("d")) {
					JSONArray array = jsonResponse.getJSONArray("d");
					files = new ArrayList<>();
					for (int i = 0; i < array.length(); i++) {
						files.add(array.optString(i));
					}
				} else {
					files = null;
				}
			} else {
				JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				files = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					files.add(jsonResponse.optString(i));
				}
			}
			return files;
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
