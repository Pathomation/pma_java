package com.pathomation;

import java.awt.Image;
import java.io.*;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import static java.lang.System.out;
import static java.util.Comparator.comparingInt;
import static java.util.Map.Entry.comparingByValue;

/**
 * <h1>Java SDK</h1>
 * <p>
 * Java wrapper library for PMA.start, a universal viewer for whole slide
 * imaging and microscopy
 * </p>
 * 
 * @author Yassine Iddaoui
 * @version 2.0.0.116
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
	 * Readable bytes for upload and download methods. To integrate into the progress bar.
	 */
	public static BlockingQueue<Long> bytes = new LinkedBlockingQueue<>();

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
	 * This method is used to retrieve HTML Code from URL
	 *
	 * @param url
	 *            to get HTML code from
	 * @return String HTML code generated from the url argument
	 */
	public static String urlReader(String url) {
		try {
			URL urlResource = new URL(url);
			URLConnection con = urlResource.openConnection();
			InputStream in = con.getInputStream();
			String encoding = con.getContentEncoding();
			encoding = encoding == null ? "UTF-8" : encoding;
			return IOUtils.toString(in, encoding);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * This method is used to parse a XML content
	 *
	 * @param s
	 *          XML content to parse
	 * @return Document parsed XML
	 */
	public static Document domParser(String s) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = factory.newDocumentBuilder();
			InputSource inputStream = new InputSource();
			inputStream.setCharacterStream(new StringReader(s));
			return documentBuilder.parse(inputStream);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * This method is used to encode a String to be compatible as a url
	 *
	 * @param arg
	 *            string to be encoded
	 * @return String encoded String to be compatible as a url
	 */
	public static String pmaQ(String arg) {
		if (arg == null) {
			return "";
		} else {
			try {
				return URLEncoder.encode(arg, "UTF-8").replace("+", "%20");
			} catch (Exception e) {
				return "";
			}
		}
	}

	/**
	 * This method is used to get the list of sessions
	 *
	 * @param pmaControlURL
	 *                         URL for PMA.Control
	 * @param pmaCoreSessionID
	 *                         PMA.core session ID
	 * @return JSONArray containing the list of sessions
	 */
	public static JSONArray getSessions(String pmaControlURL, String pmaCoreSessionID) {
		String url = join(pmaControlURL, "api/Sessions?sessionID=" + pmaQ(pmaCoreSessionID));
		System.out.println(url);
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
			String jsonString = getJSONAsStringBuffer(con).toString();
			JSONArray jsonResponse = getJSONArrayResponse(jsonString);
			return jsonResponse;
		} catch (Exception e) {
			System.out.print(e.getMessage());
			return null;
		}
	}

	/**
	 *
	 * This method is used to get the list of sessions' IDs
	 *
	 * @param pmaControlURL
	 *                         URL for PMA.Control
	 * @param pmaCoreSessionID
	 *                         PMA.core session ID
	 * @return newSession containing the sessions' IDs
	 */
	public static Map<String, Map<String, String>> getSessionIds(String pmaControlURL, String pmaCoreSessionID) {
		JSONArray fullSessions = getSessions(pmaControlURL, pmaCoreSessionID);
		Map<String, Map<String, String>> newSession = new HashMap<>();
		for (int i = 0; i < fullSessions.length(); i++) {
			Map<String, String> sessData = new HashMap<String, String>();
			try {
				sessData.put("LogoPath", fullSessions.getJSONObject(i).getString("LogoPath"));
				sessData.put("StartsOn", fullSessions.getJSONObject(i).getString("StartsOn"));
				sessData.put("EndsOn", fullSessions.getJSONObject(i).getString("EndsOn"));
				sessData.put("ModuleId", fullSessions.getJSONObject(i).getString("ModuleId"));
				sessData.put("State", fullSessions.getJSONObject(i).getString("State"));
				newSession.put(fullSessions.getJSONObject(i).getString("Id"), sessData);
			} catch (JSONException e) {
				// ignore
			}
		}
		return newSession;
	}

	/**
	 * This method is used to get case collections
	 *
	 * @param pmaControlURL
	 *                         URL for PMA.Control
	 * @param pmaCoreSessionID
	 *                         PMA.core session ID
	 * @return JSONArray containing the list of case sessions
	 */
	public static JSONArray getCaseCollections(String pmaControlURL, String pmaCoreSessionID) {
		String url = join(pmaControlURL, "api/CaseCollections?sessionID=" + pmaQ(pmaCoreSessionID));
		System.out.println(url);
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
			String jsonString = getJSONAsStringBuffer(con).toString();
			JSONArray jsonResponse = getJSONArrayResponse(jsonString);
			return jsonResponse;
		} catch (Exception e) {
			System.out.print(e.getMessage());
			return null;
		}
	}

	/**
	 * This method is used to get the list of projects
	 *
	 * @param pmaControlURL
	 *                         URL for PMA.Control
	 * @param pmaCoreSessionID
	 *                         PMA.core session ID
	 * @return JSONArray containing the list of projects
	 */
	public static JSONArray getProjects(String pmaControlURL, String pmaCoreSessionID) {
		String url = join(pmaControlURL, "api/Projects?sessionID=" + pmaQ(pmaCoreSessionID));
		System.out.println(url);
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
			String jsonString = getJSONAsStringBuffer(con).toString();
			JSONArray jsonResponse = getJSONArrayResponse(jsonString);
			return jsonResponse;
		} catch (Exception e) {
			System.out.print(e.getMessage());
			return null;
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
	 * @return True if an instance of PMA.core.lite is running, false otherwise
	 */
	private static Boolean pmaIsLite(String... varargs) {
		// setting the default value when argument's value is omitted
		String pmaCoreURL = varargs.length > 0 ? varargs[0] : pmaCoreLiteURL;
		String url = PMA.join(pmaCoreURL, "api/json/IsLite");
		try {
			String jsonString = PMA.httpGet(url, "application/json");
			return jsonString.equals("true");
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
		Boolean xml = false;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("apiUrl() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Boolean) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("apiUrl() : Invalid argument");
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
	 * This method concatenates strings with "/"
	 * 
	 * @param s
	 * @return
	 */
	private static String join(String... s) {
		String joinString = "";
		for (String ss : s) {
			if (!joinString.endsWith("/") && (!joinString.equals(""))) {
				joinString = joinString.concat("/");
			}
			if (ss != null) {
				joinString = joinString.concat(ss);
			}
		}
		return joinString;
	}

	/**
	 * This method is used to get a list of the values of "String" tags of a XML
	 * document
	 *
	 * @param root
	 *              XML document
	 * @param varargs limit it's an optional argument (int), default value set to "0"
	 * @return List{@literal <}String{@literal >} a list of the values of "String"
	 *         tags of a XML document
	 */
	public static List<String> xmlToStringArray(Document root, Integer... varargs) {
		// setting the default value when argument's value is omitted
		int limit = varargs.length > 0 ? varargs[0] : 0;
		NodeList eLs = root.getElementsByTagName("string");
		List<String> l = new ArrayList<>();
		if (limit > 0) {
			for (int i = 0; i < limit; i++) {
				l.add(eLs.item(i).getFirstChild().getNodeValue());
			}
		} else {
			for (int i = 0; i < eLs.getLength(); i++) {
				l.add(eLs.item(i).getFirstChild().getNodeValue());
			}
		}
		return l;
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
				version = jsonString.replaceAll("\"$|^\"", "");
			}

			if (version.startsWith("3.0")) {
				String revision = getBuildRevision(pmaCoreURL);
				if (!revision.isEmpty()) {
					version += "." + revision;
				}
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
	 * This method is used to get version info from PMA.control instance running at
	 * pmacontrolURL
	 *
	 * @param pmaControlURL
	 *                      PMA Control's URL
	 * @return JSONObject containing the version info
	 */
	public static JSONObject getVersionInfoPmaControl(String pmaControlURL) {
		// Get version info from PMA.control instance running at pmacontrolURL
		// why? because GetVersionInfo can be invoked WITHOUT a valid SessionID;
		// _pma_api_url() takes session information into account
		String url = join(pmaControlURL, "api/version");
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
			String jsonString = getJSONAsStringBuffer(con).toString();
			JSONObject jsonResponse = getJSONResponse(jsonString);
			return jsonResponse;
		} catch (Exception e) {
			System.out.print(e.getMessage());
			return null;
		}
	}

	/**
	 * This method checks the spelling of the server version, namely the length.
	 * 
	 * @param serverVersion
	 * @return
	 */
	private static boolean doesServerSupportPostAuth(String serverVersion) {
		if (serverVersion == null || serverVersion.length() == 0) {
			return false;
		}

		String[] version = serverVersion.split("\\.");
		if (version.length != 4) {
			return false;
		}
		try {
			int major = Integer.parseInt(version[0], 10);
			int build = Integer.parseInt(version[3], 10);

			return major > 2 || (major == 2 && build >= 1836);
		} catch (Exception e) {
			return false;
		}
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
	public static String getBuildRevision(String... varargs) {
		// setting the default value when argument's value is omitted
		String pmaCoreURL = varargs.length > 0 ? varargs[0] : pmaCoreLiteURL;
		String url = PMA.join(pmaCoreURL, "api/json/GetBuildRevision");
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
						PMA.logger.severe("getBuildRevision failed : " + jsonResponse.get("Message"));
					}
					throw new Exception("getBuildRevision failed : " + jsonResponse.get("Message"));
				} else if (jsonResponse.has("d")) {
					version = jsonResponse.getString("d");
				} else {
					return null;
				}
			} else {
				version = jsonString.replaceAll("\"$|^\"", "");
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
				JSONArray jsonResponse = getJSONArrayResponse(jsonString);
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
		String url = apiUrl(sessionID, false) + "DeAuthenticate?sessionID=" + PMA.pmaQ((sessionID));
		String contents = PMA.httpGet(url, "application/json");
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
		try {
			String url = apiUrl(sessionID, false) + "GetRootDirectories?sessionID=" + PMA.pmaQ(sessionID);
			String jsonString = PMA.httpGet(url, "application/json");
			List<String> rootDirs;
			if (PMA.isJSONArray(jsonString)) {
				JSONArray jsonResponse = getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				rootDirs = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					rootDirs.add(jsonResponse.optString(i));
				}
				// return dirs;
			} else {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("getrootdirectories() failed with error " + jsonResponse.get("Message"));
					}
					// throw new Exception("getrootdirectories() failed with error " +
					// jsonResponse.get("Message"));
				}
				return null;
			}
			return rootDirs;
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
					PMA.logger.severe("getDirectories() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if ((!(varargs[1] instanceof Integer) && !(varargs[1] instanceof Boolean)) && (varargs[1] != null)) {
				if (PMA.logger != null) {
					PMA.logger.severe("getDirectories() : Invalid argument");
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
				JSONArray jsonResponse = getJSONArrayResponse(jsonString);
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
					PMA.logger.severe("getSlides() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if ((!(varargs[1] instanceof Integer) && !(varargs[1] instanceof Boolean)) && (varargs[1] != null)) {
				if (PMA.logger != null) {
					PMA.logger.severe("getSlides() : Invalid argument");
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
				JSONArray jsonResponse = getJSONArrayResponse(jsonString);
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
		String url = apiUrl(sessionID, false) + "GetUID?sessionID=" + PMA.pmaQ(sessionID) + "&path="
				+ PMA.pmaQ(slideRef);
		try {
			String jsonString = PMA.httpGet(url, "application/json");
			pmaAmountOfDataDownloaded.put(sessionID, pmaAmountOfDataDownloaded.get(sessionID) + jsonString.length());
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				if (jsonResponse.has("Code")) {
					if (PMA.logger != null) {
						PMA.logger.severe("getUid() on  " + slideRef + " resulted in: " + jsonResponse.get("Message"));
					}
					// throw new Exception("getUid() on " + slideRef + " resulted in: " +
					// jsonResponse.get("Message"));
				}
				return null;
			} else {
				return jsonString;
			}
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
	public static List<Integer> getTileSize(String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		sessionID = sessionId(sessionID);
		Map<String, Object> info;
		if (((Map<String, Object>) pmaSlideInfos.get(sessionID)).size() < 1) {
			String dir = getFirstNonEmptyDirectory(null, sessionID);
			List<String> slides = getSlides(dir, sessionID);
			info = getSlideInfo(slides.get(0), sessionID);
		} else {
			int getLength = ((Map<String, Object>) pmaSlideInfos.get(sessionID)).values().toArray().length;
			info = (Map<String, Object>) ((Map<String, Object>) pmaSlideInfos.get(sessionID)).values()
					.toArray()[new Random().nextInt(getLength)];
		}
		List<Integer> result = new ArrayList<>();
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
				if (PMA.debug) {
					System.out.println(url);
				}
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
						// ((Map<String, Object>) pmaSlideInfos.get(sessionID))
						// .put(jsonResponse.getJSONObject("d").optString("UID"), jsonMap);
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
				con.setUseCaches(true);
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
							// ((Map<String, Object>) pmaSlideInfos.get(sessionID))
							// .put(jsonArrayResponse.getJSONObject(i).getString("UID"), jsonMap);
						}
					} else {
						return null;
					}
				} else {
					JSONArray jsonArrayResponse = getJSONArrayResponse(jsonString);
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
					PMA.logger.severe("getZoomLevelsList() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getZoomLevelsList() : Invalid argument");
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
					PMA.logger.severe("getZoomLevelsDict() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getZoomLevelsDict() : Invalid argument");
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
					PMA.logger.severe("getZoomLevelsDict() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof String) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getZoomLevelsDict() : Invalid argument");
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
		Object x =  info.get("MicrometresPerPixelX");
		float xppm = Float.parseFloat(x.toString());
		Object y = info.get("MicrometresPerPixelY");
		float yppm = Float.parseFloat(y.toString());
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
					PMA.logger.severe("getPixelDimensions() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof String) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getPixelDimensions() : Invalid argument");
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
					PMA.logger.severe("getNumberOfTiles() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof String) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getNumberOfTiles() : Invalid argument");
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
		return Core.getNumberOfLayers(slideRef, sessionID);
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
	 * This method is used to convert the slide last modified time stamp into a
	 * human readable format
	 *
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Slide's last modification date
	 */
	public static String getLastModifiedDate(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		String modificationDate = null;
		modificationDate = String.valueOf(Core.getSlideInfo(slideRef, sessionID).get("LastModified"));
		out.println(modificationDate);
//		modificationDate = modificationDate.substring(6, modificationDate.length() - 2);
		// Convert the time stamp to a date
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		modificationDate = simpleDateFormat.format(new Date(new Timestamp(Long.parseLong(modificationDate)).getTime()));
		return modificationDate;
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
					PMA.logger.severe("getMagnification() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Boolean) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getMagnification() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			exact = (Boolean) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof String) && varargs[2] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getMagnification() : Invalid argument");
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
	 * This method is used to return the list of image types associated with a slide
	 * (thumbnail, barcode...)
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return List of associated image types
	 */
	@SuppressWarnings("unchecked")
	public static List<String> getAssociatedImageTypes(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Determine the maximum zoomlevel that still represents an optical
		// magnification
		Map<String, Object> info = getSlideInfo(slideRef, sessionID);
		if (info == null) {
			return null;
		} else if (info.containsKey("AssociatedImageTypes")) {
			List<String> result = new ArrayList<>();
			List<String> associatedImageTypes = ((List<String>) info.get("AssociatedImageTypes"));
			for (String associatedImage : associatedImageTypes) {
				result.add(associatedImage);
			}
			return result;
		} else {
			return null;
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
					PMA.logger.severe("getThumbnailUrl() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getThumbnailUrl() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			height = (Integer) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof Integer) && varargs[2] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getThumbnailUrl() : Invalid argument");
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
					PMA.logger.severe("getThumbnailImage() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getThumbnailImage() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			height = (Integer) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof Integer) && varargs[2] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getThumbnailImage() : Invalid argument");
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
	 * This method is used to create the url to retrieve a single tile at position
	 * (x, y)
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
	 * @return Url to retrieve a single tile at position (x, y)
	 * @throws Exception if unable to determine the PMA.core instance the session ID
	 *                   belong to
	 */
	public static String getTileUrl(String slideRef, Object... varargs) throws Exception {
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
					PMA.logger.severe("getTile() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			x = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			y = (Integer) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof Integer) && varargs[2] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[2];
		}
		if (varargs.length > 3) {
			if (!(varargs[3] instanceof Integer) && varargs[3] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			zStack = (Integer) varargs[3];
		}
		if (varargs.length > 4) {
			if (!(varargs[4] instanceof String) && varargs[4] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[4];
		}
		if (varargs.length > 5) {
			if (!(varargs[5] instanceof String) && varargs[5] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			format = (String) varargs[5];
		}
		if (varargs.length > 6) {
			if (!(varargs[6] instanceof Integer) && varargs[6] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Invalid argument");
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
		try {
			url += "tile" + "?SessionID=" + PMA.pmaQ(sessionID) + "&channels=" + PMA.pmaQ("0") + "&layer="
					+ zStack.toString() + "&timeframe=" + PMA.pmaQ("0") + "&layer=" + PMA.pmaQ("0") + "&pathOrUid="
					+ PMA.pmaQ(slideRef) + "&x=" + x.toString() + "&y=" + y.toString() + "&z=" + zoomLevel.toString()
					+ "&format=" + PMA.pmaQ(format) + "&quality=" + PMA.pmaQ(quality.toString()) + "&cache="
					+ pmaUseCacheWhenRetrievingTiles.toString().toLowerCase();
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
					PMA.logger.severe("getTile() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			x = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			y = (Integer) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof Integer) && varargs[2] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[2];
		}
		if (varargs.length > 3) {
			if (!(varargs[3] instanceof Integer) && varargs[3] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			zStack = (Integer) varargs[3];
		}
		if (varargs.length > 4) {
			if (!(varargs[4] instanceof String) && varargs[4] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[4];
		}
		if (varargs.length > 5) {
			if (!(varargs[5] instanceof String) && varargs[5] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			format = (String) varargs[5];
		}
		if (varargs.length > 6) {
			if (!(varargs[6] instanceof Integer) && varargs[6] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTile() : Invalid argument");
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
		try {
			String url = getTileUrl(slideRef, x, y, zoomLevel, zStack, sessionID, format, quality);
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
	 * Gets a region of the slide at the specified scale Format can be 'jpg' or
	 * 'png' Quality is an integer value and varies from 0 (as much compression as
	 * possible; not recommended) to 100 (100%, no compression) x,y,width,height is
	 * the region to get rotation is the rotation in degrees of the slide to get
	 *
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 x : First optional argument(Integer), default value(0),
	 *                 starting x position
	 *                 </p>
	 *                 <p>
	 *                 y : Second optional argument(Integer), default value(0),
	 *                 starting y position
	 *                 </p>
	 *                 <p>
	 *                 width : Third optional argument(Integer), default value(0),
	 *                 ending width position
	 *                 </p>
	 *                 <p>
	 *                 height : Fourth optional argument(Integer), default value(0),
	 *                 height
	 *                 </p>
	 *                 <p>
	 *                 scale : Fifth optional argument(Integer), default value(1),
	 *                 scale
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
	 *                 format : Eighth optional argument(String), default
	 *                 value(jpg), image format
	 *                 </p>
	 *                 <p>
	 *                 quality : Ninth optional argument(Integer), default
	 *                 value(100), quality
	 *                 </p>
	 *                 <p>
	 *                 rotation : Tenth optional argument(Integer), default
	 *                 value(0), rotation
	 *                 </p>
	 *                 <p>
	 *                 contrast : Eleventh optional argument(Integer), default
	 *                 value(null), contrast
	 *                 </p>
	 *                 <p>
	 *                 brightness : Twelfth optional argument(Integer), default
	 *                 value(null), brightness
	 *                 </p>
	 *                 <p>
	 *                 dpi : Thirteenth optional argument(Integer), default
	 *                 value(300), dpi
	 *                 </p>
	 *                 <p>
	 *                 flipVertical : Fourteenth optional argument(Boolean), default
	 *                 value(false), flip vertical
	 *                 </p>
	 *                 <p>
	 *                 flipHorizontal : Fifteenth optional argument(Boolean),
	 *                 default value(false), flip horizontal
	 *                 </p>
	 *                 <p>
	 *                 annotationsLayerType : Sixteenth optional argument(String),
	 *                 default value(null), annotations layer type
	 *                 </p>
	 *                 <p>
	 *                 drawFilename : Seventeenth optional argument(Integer),
	 *                 default value(0), draw filename
	 *                 </p>
	 *                 <p>
	 *                 downloadInsteadOfDisplay : Eighteenth optional
	 *                 argument(Boolean), default value(false), download instead of
	 *                 display
	 *                 </p>
	 *                 <p>
	 *                 drawScaleBar : Nineteenth optional argument(Boolean), default
	 *                 value(false), draw scale bar
	 *                 </p>
	 *                 <p>
	 *                 gamma : Twentieth optional argument(ArrayList), default
	 *                 value([]), gamma
	 *                 </p>
	 *                 <p>
	 *                 channelClipping : Twenty-first optional argument(ArrayList),
	 *                 default value([]), channel clipping
	 *                 </p>
	 * @return Gets a region of the slide at the specified scale
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Image getRegion(String slideRef, Object... varargs) {
		String sessionID = null;
		if (varargs.length > 5) {
			if (!(varargs[5] instanceof String) && varargs[5] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion() : Invalid argument");
				}
				throw new IllegalArgumentException("Invalid sessionID");
			}
			sessionID = (String) varargs[5];
		}

		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}

		try {
			String url = getRegionUrl(slideRef, varargs);
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
	 * This method is used to create the url to retrieve a region of the slide at
	 * the specified scale (x,y,width,height)
	 *
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 x : First optional argument(Integer), default value(0),
	 *                 starting x position
	 *                 </p>
	 *                 <p>
	 *                 y : Second optional argument(Integer), default value(0),
	 *                 starting y position
	 *                 </p>
	 *                 <p>
	 *                 width : Thtional argument(Integer), default value(0),
	 *                 ending width position
	 *                 </p>
	 *                 <p>
	 *                 height : Fourth optional argument(Integer), default value(0),
	 *                 height
	 *                 </p>
	 *                 <p>
	 *                 scale : Fifth optional argument(Integer), default value(1),
	 *                 scale
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
	 *                 format : Eighth optional argument(String), default
	 *                 value(jpg), image format
	 *                 </p>
	 *                 <p>
	 *                 quality : Ninth optional argument(Integer), default
	 *                 value(100), quality
	 *                 </p>
	 *                 <p>
	 *                 rotation : Tenth optional argument(Integer), default
	 *                 value(0), rotation
	 *                 </p>
	 *                 <p>
	 *                 contrast : Eleventh optional argument(Integer), default
	 *                 value(null), contrast
	 *                 </p>
	 *                 <p>
	 *                 brightness : Twelfth optional argument(Integer), default
	 *                 value(null), brightness
	 *                 </p>
	 *                 <p>
	 *                 dpi : Thirteenth optional argument(Integer), default
	 *                 value(300), dpi
	 *                 </p>
	 *                 <p>
	 *                 flipVertical : Fourteenth optional argument(Boolean), default
	 *                 value(false), flip vertical
	 *                 </p>
	 *                 <p>
	 *                 flipHorizontal : Fifteenth optional argument(Boolean),
	 *                 default value(false), flip horizontal
	 *                 </p>
	 *                 <p>
	 *                 annotationsLayerType : Sixteenth optional argument(String),
	 *                 default value(null), annotations layer type
	 *                 </p>
	 *                 <p>
	 *                 drawFilename : Seventeenth optional argument(Integer),
	 *                 default value(0), draw filename
	 *                 </p>
	 *                 <p>
	 *                 downloadInsteadOfDisplay : Eighteenth optional
	 *                 argument(Boolean), default value(false), download instead of
	 *                 display
	 *                 </p>
	 *                 <p>
	 *                 drawScaleBar : Nineteenth optional argument(Boolean), default
	 *                 value(false), draw scale bar
	 *                 </p>
	 *                 <p>
	 *                 gamma : Twentieth optional argument(ArrayList), default
	 *                 value([]), gamma
	 *                 </p>
	 *                 <p>
	 *                 channelClipping : Twenty-first optional argument(ArrayList),
	 *                 default value([]), channel clipping
	 *                 </p>
	 * @return Url to retrieve a region at position (x, y, width, height)
	 * @throws Exception if unable to determine the PMA.core instance the session ID
	 *                   belong to
	 */
	@SuppressWarnings({ "unchecked" })
	public static String getRegionUrl(String slideRef, Object... varargs) throws Exception {
		// setting the default values when arguments' values are omitted
		Integer x = 0;
		Integer y = 0;
		Integer width = 0;
		Integer height = 0;
		Integer scale = 1;
		Integer zStack = 0;
		String sessionID = null;
		String format = "jpg";
		Integer quality = 100;
		Integer rotation = 0;
		Integer contrast = null;
		Integer brightness = null;
		Integer postGamma = null;
		Integer dpi = 300;
		Boolean flipVertical = false;
		Boolean flipHorizontal = false;
		String annotationsLayerType = null;
		Integer drawFilename = 0;
		Boolean downloadInsteadOfDisplay = false;
		Boolean drawScaleBar = false;
		String gamma = null;
		String channelClipping = null;

		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument, X is required");
				}
				throw new IllegalArgumentException("X is required");
			}
			x = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion():  Invalid argument, Y is required");
				}
				throw new IllegalArgumentException("Y is required");
			}
			y = (Integer) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof Integer) && varargs[2] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument, width is required");
				}
				throw new IllegalArgumentException("Width is required");
			}
			width = (Integer) varargs[2];
		}
		if (varargs.length > 3) {
			if (!(varargs[3] instanceof Integer) && varargs[3] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument, height is required");
				}
				throw new IllegalArgumentException("Height is required");
			}
			height = (Integer) varargs[3];
		}
		if (varargs.length > 4) {
			if (!(varargs[4] instanceof Integer) && varargs[4] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument, zStack is required");
				}
				throw new IllegalArgumentException("ZStack is required");
			}
			zStack = (Integer) varargs[4];
		}
		if (varargs.length > 5) {
			if (!(varargs[5] instanceof String) && varargs[5] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument, invalid sessionId");
				}
				throw new IllegalArgumentException("Invalid session id");
			}
			sessionID = (String) varargs[5];
		}
		if (varargs.length > 6) {
			if (!(varargs[6] instanceof String) && varargs[6] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument, invalid format parameter");
				}
				throw new IllegalArgumentException("Invalid format parameter");
			}
			format = (String) varargs[6];
		}
		if (varargs.length > 7) {
			if (!(varargs[7] instanceof Integer) && varargs[7] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument, invalid quality parameter");
				}
				throw new IllegalArgumentException("Invalid quality parameter");
			}
			quality = (Integer) varargs[7];
		}
		if (varargs.length > 8) {
			if (!(varargs[8] instanceof Integer) && varargs[8] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument, invalid rotation parameter");
				}
				throw new IllegalArgumentException("Invalid rotation parameter");
			}
			rotation = (Integer) varargs[8];
		}
		if (varargs.length > 9) {
			if (!(varargs[9] instanceof Integer) && varargs[9] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument constrast");
				}
				throw new IllegalArgumentException("Invalid contrast parameter");
			}
			contrast = (Integer) varargs[9];
		}
		if (varargs.length > 10) {
			if (!(varargs[10] instanceof Integer) && varargs[10] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument brightness");
				}
				throw new IllegalArgumentException("Invalid brightness parameter");
			}
			brightness = (Integer) varargs[10];
		}
		if (varargs.length > 11) {
			if (!(varargs[11] instanceof Integer) && varargs[11] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument postGamma");
				}
				throw new IllegalArgumentException("Invalid post gamma parameter.");
			}
			postGamma = (Integer) varargs[11];
		}
		if (varargs.length > 12) {
			if (!(varargs[12] instanceof Integer) && varargs[12] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument dpi");
				}
				throw new IllegalArgumentException("Invalid dpi parameter");
			}
			dpi = (Integer) varargs[12];
		}
		if (varargs.length > 13) {
			if (!(varargs[13] instanceof Boolean) && varargs[13] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument flipVertical");
				}
				throw new IllegalArgumentException(" Invalid paramter flipVertical");
			}
			flipVertical = (Boolean) varargs[13];
		}
		if (varargs.length > 14) {
			if (!(varargs[14] instanceof Boolean) && varargs[14] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument flipHorizontal");
				}
				throw new IllegalArgumentException("Invalid argument flipHorizontal");
			}
			flipHorizontal = (Boolean) varargs[14];
		}
		if (varargs.length > 15) {
			if (!(varargs[15] instanceof String) && varargs[15] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument annotationsLayerType");
				}
				throw new IllegalArgumentException("Invalid argument annotationsLayerType");
			}
			annotationsLayerType = (String) varargs[15];
		}
		if (varargs.length > 16) {
			if (!(varargs[16] instanceof Integer) && varargs[16] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument drawFilename");
				}
				throw new IllegalArgumentException("Invalid argument drawFilename");
			}
			drawFilename = (Integer) varargs[16];
		}
		if (varargs.length > 17) {
			if (!(varargs[17] instanceof Boolean) && varargs[17] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument downloadInsteadOfDisplay");
				}
				throw new IllegalArgumentException("Invalid argument downloadInsteadOfDisplay");
			}
			downloadInsteadOfDisplay = (Boolean) varargs[17];
		}
		if (varargs.length > 18) {
			if (!(varargs[18] instanceof Boolean) && varargs[18] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument drawScaleBar");
				}
				throw new IllegalArgumentException("Invalid argument drawScaleBar");
			}
			drawScaleBar = (Boolean) varargs[18];
		}
		if (varargs.length > 19) {
			if (!(varargs[19] instanceof ArrayList) && varargs[19] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument gamma");
				}
				throw new IllegalArgumentException("Invalid argument gamma");
			}

			gamma = String.join(",", (List<String>) varargs[19]);
		}
		if (varargs.length > 20) {
			if (!(varargs[20] instanceof ArrayList) && varargs[20] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getRegion(): Invalid argument channelClipping");
				}
				throw new IllegalArgumentException("Invalid argument channelClipping");
			}
			channelClipping = String.join(",", (List<String>) varargs[19]);
		}

		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}

		String url;
		url = pmaUrl(sessionID);
		if (url == null) {
			if (PMA.logger != null) {
				PMA.logger.severe("Unable to determine the PMA.core instance belonging to " + sessionID);
			}
			throw new Exception("Unable to determine the PMA.core instance belonging to " + sessionID);
		}
		try {
			url += "region" + "?SessionID=" + PMA.pmaQ(sessionID) + "&channels=" + PMA.pmaQ("0") + "&layer="
					+ zStack.toString() + "&timeframe=" + PMA.pmaQ("0") + "&layer=" + PMA.pmaQ("0") + "&pathOrUid="
					+ PMA.pmaQ(slideRef) + "&x=" + x.toString() + "&y=" + y.toString() + "&width=" + width.toString()
					+ "&height=" + height.toString() + "&scale=" + scale.toString() + "&format=" + PMA.pmaQ(format)
					+ "&quality=" + PMA.pmaQ(quality.toString()) + "&rotation=" + rotation.toString() + "&contrast="
					+ contrast.toString() + "&brightness=" + brightness.toString() + "&postGamma="
					+ postGamma.toString() + "&dpi=" + dpi.toString() + "&flipVertical=" + flipVertical.toString()
					+ "&flipHorizontal=" + flipHorizontal.toString() + "&annotationsLayerType="
					+ PMA.pmaQ(annotationsLayerType) + "&drawFilename=" + drawFilename.toString()
					+ "&downloadInsteadOfDisplay=" + downloadInsteadOfDisplay.toString() + "&drawScaleBar="
					+ drawScaleBar.toString() + "&gamma=" + PMA.pmaQ(gamma) + "&channelClipping="
					+ PMA.pmaQ(channelClipping) + "&cache=" + pmaUseCacheWhenRetrievingTiles.toString().toLowerCase();
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
		String format = "";
		Integer quality = 100;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTiles() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			fromX = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTiles() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			fromY = (Integer) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof Integer) && varargs[2] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTiles() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			toX = (Integer) varargs[2];
		}
		if (varargs.length > 3) {
			if (!(varargs[3] instanceof Integer) && varargs[3] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTiles() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			toY = (Integer) varargs[3];
		}
		if (varargs.length > 4) {
			if (!(varargs[4] instanceof Integer) && varargs[4] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTiles() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[4];
		}
		if (varargs.length > 5) {
			if (!(varargs[5] instanceof Integer) && varargs[5] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTiles() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			zStack = (Integer) varargs[5];
		}
		if (varargs.length > 6) {
			if (!(varargs[6] instanceof String) && varargs[6] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTiles() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[6];
		}
		if (varargs.length > 7) {
			if (!(varargs[7] instanceof String) && varargs[7] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTiles() : Invalid argument");
				}
				throw new IllegalArgumentException("...");
			}
			format = (String) varargs[7];
		}
		if (varargs.length > 8) {
			if (!(varargs[8] instanceof Integer) && varargs[8] != null) {
				if (PMA.logger != null) {
					PMA.logger.severe("getTiles() : Invalid argument");
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
			out.println(jsonString);
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
					JSONArray jsonResponse = getJSONArrayResponse(jsonString);
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
					JSONArray jsonResponse = getJSONArrayResponse(jsonString);
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
					JSONArray jsonResponse = getJSONArrayResponse(jsonString);
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
					JSONArray jsonResponse = getJSONArrayResponse(jsonString);
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
	 * This method is used to add the annotations for slide
	 *
	 * @param sessionID      session's ID
	 * @param pathOrUid      slide's path
	 * @param classification slide's classification
	 * @param layerID        slide's layer id
	 * @param notes          "Annotation" + number or app name.
	 * @param geometry       annotation's geometry
	 * @param color          annotation's color
	 */
	public static JSONArray add_Annotations(
			String sessionID,
			String pathOrUid,
			String classification,
			int layerID,
			String notes,
			String geometry,
			String color)
			throws Exception {
		JSONObject data = new JSONObject();
		String urlS;
		sessionID = sessionId(sessionID);
		if (sessionID == pmaCoreLiteSessionID) {
			if (isLite()) {
				throw new Exception("PMA.core.lite found running, but doesn't support adding annotations.");
			} else {
				throw new Exception("PMA.core.lite not found, and besides; it doesn't support adding annotations.");
			}
		}
		urlS = pmaUrl(sessionID) + "api/json/AddAnnotation";
		URL url = new URL(urlS);
		data.put("sessionID", sessionID);
		data.put("pathOrUid", pathOrUid);
		data.put("classification", classification);
		data.put("layerID", layerID);
		data.put("notes", notes);
		data.put("geometry", geometry);
		data.put("color", color);

		URLConnection con = url.openConnection();
		HttpURLConnection http = (HttpURLConnection) con;
		http.setRequestMethod("POST"); // PUT is another valid option
		http.setDoOutput(true);
		out.println(con);
		out.println(http);

		byte[] out = data.toString().getBytes(StandardCharsets.UTF_8);
		int length = out.length;
		System.out.println(length);

		http.setFixedLengthStreamingMode(length);
		http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
		http.connect();
		try (OutputStream os = http.getOutputStream()) {
			os.write(out);
			System.out.println(os);
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
					JSONArray jsonResponse = getJSONArrayResponse(jsonString);
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
	 * This method is used to delete all the annotations for slide
	 *
	 * @param sessionID session's ID
	 * @param slideRef  slide's path
	 * @param layerID   slide's layer id
	 */
	public static boolean clear_annotations(String slideRef, int layerID, String sessionID) throws Exception {
		sessionID = sessionId(sessionID);
		JSONObject data = new JSONObject();
		String urlS = pmaUrl(sessionID) + "api/json/DeleteAnnotations";
		URL url = new URL(urlS);
		if (sessionID == pmaCoreLiteSessionID) {
			if (isLite()) {
				throw new Exception("PMA.core.lite found running, but doesn't support adding annotations.");
			} else {
				throw new Exception("PMA.core.lite not found, and besides; it doesn't support adding annotations.");
			}
		}

		data.put("sessionID", sessionID);
		data.put("pathOrUid", slideRef);
		data.put("layerID", layerID);

		URLConnection con = url.openConnection();
		HttpURLConnection http = (HttpURLConnection) con;
		http.setRequestMethod("POST"); // PUT is another valid option
		http.setDoOutput(true);
		out.println(con);
		out.println(http);

		byte[] out = data.toString().getBytes(StandardCharsets.UTF_8);
		int length = out.length;
		System.out.println(length);

		http.setFixedLengthStreamingMode(length);
		http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
		http.connect();
		try (OutputStream os = http.getOutputStream()) {
			os.write(out);
			System.out.println(os);
		}
		return true;
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
				resultsArray = getJSONArrayResponse(jsonString);
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
		} catch (

		Exception e) {
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
				JSONArray jsonResponse = getJSONArrayResponse(jsonString);
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
				JSONArray jsonResponse = getJSONArrayResponse(jsonString);
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
				JSONArray jsonResponse = getJSONArrayResponse(jsonString);
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

	/**
	 * Uploads a slide to a PMA.core server. Requires a PMA.start installation
	 * :param str localSourceSlide: The local PMA.start relative file to upload
	 * :param str targetFolder: The root directory and path to upload to the
	 * PMA.core server
	 * :param str sessionID: A valid session id for a PMA.core
	 * :param function callback: If True a default progress will be printed.
	 * If a function is passed it will be called for progress on each file upload.
	 * The function has the following signature:
	 * `callback(float progress)`
	 *
	 *
	 * @param localSourceSlide
	 * @param targetFolder
	 * @param sessionID
	 * @param progressCallback
	 * @return true if upload is done
	 */

	public static boolean uploadX(String localSourceSlide, String targetFolder, String sessionID,
								 ProgressHttpEntityWrapper.ProgressCallback progressCallback)
			throws Exception {
		sessionID = sessionId(sessionID);
		if (!pmaIsLite()) {
			throw new RuntimeException("No PMA.start found on localhost. Are you sure it is running?");
		}
		if (localSourceSlide == null) {
			throw new RuntimeException("Slide name is empty");
		}
		if (targetFolder == null) {
			throw new RuntimeException("Target destination  cannot be empty");
		}
		if (targetFolder.startsWith("/")) {
			targetFolder = targetFolder.substring(1);
		}
		for (String slide : Core.getSlides(targetFolder, sessionID)) {
			if (slide.equals(targetFolder + Core.getSlideFileName(localSourceSlide))) {
				throw new Exception("The file: ===" +  Core.getSlideFileName(localSourceSlide) +"=== with the same name and extension already exists in the target folder: " + targetFolder);
			}
		}
		Map<String, Map<String, String>> files = Core.getFilesForSlide(localSourceSlide, pmaCoreLiteSessionID);

		String mainDirectory = "";
		for (Map.Entry<String, Map<String, String>> entry : files.entrySet()) {
			String key = entry.getKey();
			Map<String, String> value = entry.getValue();
			File file = new File(key);
			String md = file.getParent();

			if (mainDirectory.length() == 0 || md.length() < mainDirectory.length()) {
				mainDirectory = md;
			}
		}

		mainDirectory = mainDirectory.replace("\\", "/");

		List<HashMap<String, String>> uploadFiles = new ArrayList<HashMap<String, String>>();
		for (Map.Entry<String, Map<String, String>> entry : files.entrySet()) {
			String key = entry.getKey();
			Map<String, String> value = entry.getValue();
			long s = (new File(key)).length();
			if (s <= 0) {
				continue;
			}
			String path = key.replace(mainDirectory, "").replaceAll("^\\+|\\+$/g", "")
					.replaceAll("^/+|/+$/g", "");
			HashMap<String, String> fileObj = new HashMap<String, String>();
			fileObj.put("Path", path);
			fileObj.put("Length", Long.toString(s));
			fileObj.put("IsMain", Boolean.toString(path.equals(Core.getSlideFileName(localSourceSlide))));
			fileObj.put("FullPath", key);
			uploadFiles.add(fileObj);
		}

		JSONObject data = new JSONObject();
		data.put("Path", targetFolder);
		data.put("Files", new JSONArray(uploadFiles));
		String url = pmaUrl(sessionID) + "transfer/Upload?sessionID=" + PMA.pmaQ((sessionID));

		URL urlResource = new URL(url);
		URLConnection con = urlResource.openConnection();
		HttpURLConnection http = (HttpURLConnection) con;
		http.setRequestMethod("POST");
		http.setDoOutput(true);

		byte[] out = data.toString().getBytes(StandardCharsets.UTF_8);
		int length = out.length;

		http.setFixedLengthStreamingMode(length);
		http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
		http.connect();
		try (OutputStream os = http.getOutputStream()) {
			os.write(out);
		}

		int rCode = http.getResponseCode();
		if (rCode != HttpURLConnection.HTTP_OK) {
			throw new Exception("Error uploading file to PMA.core");
		}

		String jsonString = PMA.getJSONAsStringBuffer(http).toString();
		if (!PMA.isJSONObject(jsonString)) {
			throw new Exception("Error uploading " + localSourceSlide + " to PMA.core");
		}

		JSONObject uploadHeader = PMA.getJSONObjectResponse(jsonString);
		int i = 0;
		for (HashMap<String, String> entry : uploadFiles) {
			File file = new File(entry.get("FullPath"));
			String fileName = FilenameUtils.getName(entry.get("Path"));

			List<FormBodyPart> list = new ArrayList<FormBodyPart>();
			list.add(new FormBodyPart(fileName, new FileBody(file)));
			HttpCustomMultipart form = new HttpCustomMultipart("form-data", null, "----", list);
			HttpEntity entity = new MultipartCustomEntity(form, "", form.getTotalLength());

			String uploadUrl = pmaUrl(sessionID) + "transfer/Upload/" + uploadHeader.get("Id").toString()
					+ "?sessionID="
					+ PMA.pmaQ((sessionID)) + "&path=" +
					PMA.pmaQ(entry.get("Path"));

			HttpRequestBase request = null;

			if (uploadHeader.getInt("UploadType") == 1) {
				// Amazon upload
				JSONArray jsonarray = uploadHeader.getJSONArray("Urls");
				uploadUrl = jsonarray.getString(i);
				request = (HttpRequestBase) new HttpPut(uploadUrl);
				request.setHeader("Content-Type", "multipart/form-data");
				if (progressCallback == null) {
					((HttpPut) request).setEntity(entity);
				} else {
					((HttpPut) request).setEntity(new ProgressHttpEntityWrapper(entity, entry.get("Path").toString(), progressCallback));
				}
			} else if (uploadHeader.getInt("UploadType") == 2) {
				// Azure upload
				JSONArray jsonarray = uploadHeader.getJSONArray("Urls");
				uploadUrl = jsonarray.getString(i);
				request = (HttpRequestBase) new HttpPut(uploadUrl);
				if (progressCallback == null) {
					((HttpPut) request).setEntity(entity);
				} else {
					((HttpPut) request).setEntity(new ProgressHttpEntityWrapper(entity, entry.get("Path").toString(), progressCallback));
				}
				request.setHeader("x-ms-blob-type", "BlockBlob");
				request.setHeader("Content-Type", "multipart/form-data");
			} else {
				entity = MultipartEntityBuilder.create()
						.setMode(HttpMultipartMode.STRICT)
						.addPart(fileName, new FileBody(file))
						.build();

				request = (HttpRequestBase) new HttpPost(uploadUrl);
				if (progressCallback == null) {
					((HttpPost) request).setEntity(entity);
				} else {
					((HttpPost) request).setEntity(new ProgressHttpEntityWrapper(entity, entry.get("Path").toString(), progressCallback));
				}

			}

			HttpRequestInterceptor requestInterceptor = new HttpRequestInterceptor() {
				@Override
				public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
					if (uploadHeader.getInt("UploadType") == 0){
						return;
					}

					if (request.containsHeader("Content-Length")) {
						request.removeHeaders("Content-Length");
					}

					request.addHeader("Content-Length", entry.get("Length").toString());
				}
			};

			HttpClient client = HttpClientBuilder.create()
					.addInterceptorLast(requestInterceptor)
					.disableContentCompression()
					.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE).disableAutomaticRetries().build();

			HttpResponse response = client.execute(request);
			String uploadResult = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
			i++;
		}

		String finalizeUrl = pmaUrl(sessionID) + "transfer/Upload/" + uploadHeader.get("Id").toString() + "?sessionID="
				+ PMA.pmaQ(sessionID);
		HttpGet finalizeRequest = new HttpGet(finalizeUrl);
		HttpClient client = HttpClientBuilder.create().setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE).build();
		HttpResponse response = client.execute(finalizeRequest);

		int responseCode = response.getStatusLine().getStatusCode();
		if (responseCode < 200 || responseCode >= 300) {
			String result = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
			throw new Exception("Error uploading" + result);
		}
		return true;
	}

	/**
	 *
	 */
	public static boolean upload(String localSourceSlide, String targetFolder, String sessionID,
								 ProgressHttpEntityWrapper.ProgressCallback progressCallback, Object... varargs)
			throws Exception {
		sessionID = sessionId(sessionID);
			if (!pmaIsLite()) {
				throw new RuntimeException("No PMA.start found on localhost. Are you sure it is running?");
			}
			else if (localSourceSlide == null) {
				throw new RuntimeException("Slide name is empty");
			}
			else if (targetFolder == null) {
				throw new RuntimeException("Target destination  cannot be empty");
			}
			else if (targetFolder.startsWith("/")) {
				targetFolder = targetFolder.substring(1);
			}
		if (varargs == null) {
			for (String slide : Core.getSlides(targetFolder, sessionID)) {
				if (slide.equals(targetFolder + Core.getSlideFileName(localSourceSlide))) {
					throw new Exception("The file: ===" +  Core.getSlideFileName(localSourceSlide) +"=== with the same name and extension already exists in the target folder: " + targetFolder);
				}
			}

			Map<String, Map<String, String>> files = Core.getFilesForSlide(localSourceSlide, pmaCoreLiteSessionID);

			String mainDirectory = "";
			for (Map.Entry<String, Map<String, String>> entry : files.entrySet()) {
				String key = entry.getKey();
				Map<String, String> value = entry.getValue();
				File file = new File(key);
				String md = file.getParent();

				if (mainDirectory.length() == 0 || md.length() < mainDirectory.length()) {
					mainDirectory = md;
				}
			}

			mainDirectory = mainDirectory.replace("\\", "/");
			List<HashMap<String, String>> uploadFiles = new ArrayList<HashMap<String, String>>();
			Map<String,Long> hm = new HashMap<>();
			LinkedHashMap<String, Long> reverseSortedMap = new LinkedHashMap<>();
			for (Map.Entry<String, Map<String, String>> entry : files.entrySet()) {
				String key = entry.getKey();
				Map<String, String> value = entry.getValue();
				long s = (new File(key)).length();
				if (s <= 0) {
					continue;
				}
				hm.put(key, s);
			}

			hm.entrySet()
					.stream()
					.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
					.forEachOrdered(x -> reverseSortedMap.put(x.getKey(), x.getValue()));
			for (Map.Entry<String, Long> revers : reverseSortedMap.entrySet()) {
				out.println(revers);
				String key = revers.getKey();
				long s = (new File(key)).length();
				if (s <= 0) {
					continue;
				}
				String path = key.replace(mainDirectory, "").replaceAll("^\\+|\\+$/g", "")
						.replaceAll("^/+|/+$/g", "");
				HashMap<String, String> fileObj = new HashMap<String, String>();
				fileObj = new HashMap<String, String>();
				fileObj.put("Path", path);
				fileObj.put("Length", Long.toString(s));
				fileObj.put("IsMain", Boolean.toString(path.equals(Core.getSlideFileName(localSourceSlide))));
				fileObj.put("FullPath", key);
				uploadFiles.add(fileObj);
				out.println(fileObj + "   fileObj");
			}
			out.println(uploadFiles + "   uploadFiles");
			JSONObject data = new JSONObject();
			data.put("Path", targetFolder);
			data.put("Files", new JSONArray(uploadFiles));
			out.println(data + "   data");
			String url = pmaUrl(sessionID) + "transfer/Upload?sessionID=" + PMA.pmaQ((sessionID));
			URL urlResource = new URL(url);
			URLConnection con = urlResource.openConnection();
			HttpURLConnection http = (HttpURLConnection) con;
			http.setRequestMethod("POST");
			http.setDoOutput(true);

			byte[] out = data.toString().getBytes(StandardCharsets.UTF_8);
			int length = out.length;

			http.setFixedLengthStreamingMode(length);
			http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			http.connect();
			try (OutputStream os = http.getOutputStream()) {
				os.write(out);
			}

			int rCode = http.getResponseCode();
			if (rCode != HttpURLConnection.HTTP_OK) {
				throw new Exception("Error uploading file to PMA.core");
			}

			String jsonString = PMA.getJSONAsStringBuffer(http).toString();
			System.out.println(jsonString + "    jsonString");
			if (!PMA.isJSONObject(jsonString)) {
				throw new Exception("Error uploading " + localSourceSlide + " to PMA.core");
			}

			JSONObject uploadHeader = PMA.getJSONObjectResponse(jsonString);
			System.out.println(uploadHeader + "   uploadHeader");
		int i = 0;
		String uploadUrl;
		JSONArray jsonarray;
			for (HashMap<String, String> entry : uploadFiles) {
				File file = new File(entry.get("FullPath"));
				String fileName = FilenameUtils.getName(entry.get("Path"));

				List<FormBodyPart> list = new ArrayList<FormBodyPart>();
				list.add(new FormBodyPart(fileName, new FileBody(file)));
				HttpCustomMultipart form = new HttpCustomMultipart("form-data", null, "----", list);
				HttpEntity entity = new MultipartCustomEntity(form, "", form.getTotalLength());

				uploadUrl = pmaUrl(sessionID) + "transfer/Upload/" + uploadHeader.get("Id").toString()
						+ "?sessionID="
						+ PMA.pmaQ((sessionID)) + "&path=" +
						PMA.pmaQ(entry.get("Path"));
				HttpRequestBase request = null;
				if (uploadHeader.getInt("UploadType") == 1) {
					// Amazon upload
					jsonarray = uploadHeader.getJSONArray("Urls");
					uploadUrl = jsonarray.getString(i);
					request = (HttpRequestBase) new HttpPut(uploadUrl);
					request.setHeader("Content-Type", "multipart/form-data");
					if (progressCallback == null) {
						((HttpPut) request).setEntity(entity);
					} else {
						((HttpPut) request).setEntity(new ProgressHttpEntityWrapper(entity, entry.get("Path").toString(), progressCallback));
					}
				} else if (uploadHeader.getInt("UploadType") == 2) {
					// Azure upload
					jsonarray = uploadHeader.getJSONArray("Urls");
					uploadUrl = jsonarray.getString(i);
					request = (HttpRequestBase) new HttpPut(uploadUrl);
					if (progressCallback == null) {
						((HttpPut) request).setEntity(entity);
					} else {
						((HttpPut) request).setEntity(new ProgressHttpEntityWrapper(entity, entry.get("Path").toString(), progressCallback));
					}
					request.setHeader("x-ms-blob-type", "BlockBlob");
					request.setHeader("Content-Type", "multipart/form-data");
				} else {
					entity = MultipartEntityBuilder.create()
							.setMode(HttpMultipartMode.STRICT)
							.addPart(fileName, new FileBody(file))
							.build();
					request = (HttpRequestBase) new HttpPost(uploadUrl);
					System.out.println(uploadUrl + "   uploadUrl");
					if (progressCallback == null) {
						((HttpPost) request).setEntity(entity);
					} else {
						((HttpPost) request).setEntity(new ProgressHttpEntityWrapper(entity, entry.get("Path").toString(), progressCallback));
					}
				}

				HttpRequestInterceptor requestInterceptor = new HttpRequestInterceptor() {
					@Override
					public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
						if (uploadHeader.getInt("UploadType") == 0){
							return;
						}

						if (request.containsHeader("Content-Length")) {
							request.removeHeaders("Content-Length");
						}

						request.addHeader("Content-Length", entry.get("Length").toString());
					}
				};

				HttpClient client = HttpClientBuilder.create()
						.addInterceptorLast(requestInterceptor)
						.disableContentCompression()
						.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE).disableAutomaticRetries().build();

				HttpResponse response = client.execute(request);

				String uploadResult = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
				i++;
			}
			String finalizeUrl = pmaUrl(sessionID) + "transfer/Upload/" + uploadHeader.get("Id").toString() + "?sessionID="
					+ PMA.pmaQ(sessionID);
			HttpGet finalizeRequest = new HttpGet(finalizeUrl);
			HttpClient client = HttpClientBuilder.create().setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE).build();
			HttpResponse response = client.execute(finalizeRequest);
			int responseCode = response.getStatusLine().getStatusCode();
			if (responseCode < 200 || responseCode >= 300) {
				String result = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
				throw new Exception("Error uploading" + result);
			}
			return true;
		}
		else {
			String relativePath = varargs.length > 0? (String) varargs[0] : null;
			String URL = varargs.length > 1 ? (String) varargs[1] : null;
			String uploadID = varargs.length > 2 ? (String) varargs[2] : null;
			String uploadType = varargs.length > 3 ? (String) varargs[3] : null;
			File uploadFile = varargs.length > 4 ? (File) varargs[4] : null;
			if (relativePath == null) {
				throw new RuntimeException("relativePath is empty");
			}
			if (URL == null) {
				throw new RuntimeException("URL is empty");
			}
			if (uploadID == null) {
				throw new RuntimeException("uploadID is empty");
			}
			if (uploadType == null) {
				throw new RuntimeException("uploadType is empty");
			}
			if (uploadFile == null) {
				throw new RuntimeException("uploadFile is empty");
			}
			OutputStream outputStreamToRequestBody = null;
			FileInputStream inputStreamToLogFile = null;
			HttpURLConnection conToSend = null;
			String boundary = "*****";
			String crlf = "\r\n";
			String twoHyphens = "--";
			int BUFFER_SIZE = 4 * 1024;
			byte[] buffer = new byte[BUFFER_SIZE];
			int bytesRead = -1;
			long totalBytesRead = 0;
			URL urlResource = null;
				if (uploadType.equals("0")) {
					try {
						if (URL.startsWith("https")) {
							urlResource = new URL(URL);
							conToSend = (HttpsURLConnection) urlResource.openConnection();
						} else {
							conToSend = (HttpURLConnection) urlResource.openConnection();
						}
						conToSend.setConnectTimeout(0); // infinite timeout
						conToSend.setReadTimeout(0);
						conToSend.setRequestMethod("POST");
						conToSend.setRequestProperty("Connection", "Keep-Alive");
						conToSend.setRequestProperty("Cache-Control", "no-cache");
						conToSend.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
//						conToSend.setRequestProperty("Content-Length", String.valueOf(uploadFile.length()));
						conToSend.setUseCaches(false);
						conToSend.setDoOutput(true);
						conToSend.setDoInput(true);
						conToSend.setChunkedStreamingMode(4096);
						conToSend.connect();
						outputStreamToRequestBody = new DataOutputStream(conToSend.getOutputStream());
						((DataOutputStream) outputStreamToRequestBody).writeBytes(twoHyphens + boundary + crlf);
						((DataOutputStream) outputStreamToRequestBody).writeBytes("Content-Disposition: form-data; name=\"" + "file" + "\";filename=\""
								+ URLEncoder.encode(uploadFile.getName(), "UTF-8").replace("+", "%20") + "\"" + crlf);
						((DataOutputStream) outputStreamToRequestBody).writeBytes(crlf);
						outputStreamToRequestBody = progressCallback != null
								? new ProgressHttpEntityWrapper.ProgressFilterOutputStream(
								outputStreamToRequestBody, progressCallback, uploadFile.length(), relativePath)
								: outputStreamToRequestBody;
						inputStreamToLogFile = new FileInputStream(uploadFile);
						while ((bytesRead = inputStreamToLogFile.read(buffer)) != -1) {
							bytes.put((long) bytesRead);
							outputStreamToRequestBody.write(buffer, 0, bytesRead);
							totalBytesRead += bytesRead;
							System.out.println(relativePath + " " + totalBytesRead);
						}
						((DataOutputStream) outputStreamToRequestBody).writeBytes(crlf);
						((DataOutputStream) outputStreamToRequestBody).writeBytes(twoHyphens + boundary + twoHyphens + crlf);
						outputStreamToRequestBody.flush();
						outputStreamToRequestBody.close();
						inputStreamToLogFile.close();
						conToSend.getResponseCode();
						return _pma_checkUploadedFile(sessionID, uploadID, relativePath);
					} catch (Exception e) {
						e.printStackTrace();
						if (outputStreamToRequestBody != null) {
							outputStreamToRequestBody.flush();
							((DataOutputStream) outputStreamToRequestBody).writeBytes(crlf);
							((DataOutputStream) outputStreamToRequestBody).writeBytes(twoHyphens + boundary + twoHyphens + crlf);
							outputStreamToRequestBody.flush();
							// Close the stream
							outputStreamToRequestBody.close();
						}
						if (inputStreamToLogFile != null) {
							inputStreamToLogFile.close();
						}
						conToSend.getResponseCode();
						return false;
					}
				}
				if (uploadType.equals("1")) {
					try {
						urlResource = new URL(URL);
						if (URL.startsWith("https")) {
							conToSend = (HttpsURLConnection) urlResource.openConnection();
						} else {
							conToSend = (HttpURLConnection) urlResource.openConnection();
						}
						conToSend.setRequestMethod("PUT");
						conToSend.setConnectTimeout(0); // infinite timeout
						conToSend.setReadTimeout(0); // infinite timeout
						conToSend.setRequestProperty("Connection", "Keep-Alive");
						conToSend.setRequestProperty("Cache-Control", "no-cache");
						conToSend.setRequestProperty("Content-Length", String.valueOf(uploadFile.length()));
						conToSend.setDoOutput(true);
						conToSend.setUseCaches(false);
						conToSend.setFixedLengthStreamingMode(uploadFile.length());
						conToSend.connect();
						outputStreamToRequestBody = new DataOutputStream(conToSend.getOutputStream());
						outputStreamToRequestBody = progressCallback != null
								? new ProgressHttpEntityWrapper.ProgressFilterOutputStream(
								outputStreamToRequestBody, progressCallback, uploadFile.length(), relativePath)
								: outputStreamToRequestBody;
						inputStreamToLogFile = new FileInputStream(uploadFile);
						while ((bytesRead = inputStreamToLogFile.read(buffer)) != -1) {
							bytes.put((long) bytesRead);
							outputStreamToRequestBody.write(buffer, 0, bytesRead);
							totalBytesRead += bytesRead;
							System.out.println(relativePath + " " + totalBytesRead);
						}
						outputStreamToRequestBody.flush();
						outputStreamToRequestBody.close();
						inputStreamToLogFile.close();
						conToSend.getResponseMessage();
						conToSend.disconnect();
						return _pma_checkUploadedFile(sessionID, uploadID, relativePath);
					}  catch (Exception e) {
						e.printStackTrace();
						if (outputStreamToRequestBody != null) {
							outputStreamToRequestBody.flush();
							// Close the stream
							outputStreamToRequestBody.close();
						}
						if (inputStreamToLogFile != null) {
							inputStreamToLogFile.close();
						}
						conToSend.getResponseCode();
						return false;
					}
				}
				if (uploadType.equals("2")) {
					try {
						urlResource = new URL(URL);
						if (URL.startsWith("https")) {
							conToSend = (HttpsURLConnection) urlResource.openConnection();
						} else {
							conToSend = (HttpURLConnection) urlResource.openConnection();
						}
						conToSend.setRequestMethod("PUT");
						conToSend.setConnectTimeout(0); // infinite timeout
						conToSend.setReadTimeout(0); // infinite timeout
						conToSend.setRequestProperty("Connection", "Keep-Alive");
						conToSend.setRequestProperty("Cache-Control", "no-cache");
						conToSend.setRequestProperty("x-ms-blob-type", "BlockBlob");
						conToSend.setRequestProperty("Content-Length", String.valueOf(uploadFile.length()));
						conToSend.setDoOutput(true);
						conToSend.setUseCaches(false);
						conToSend.setFixedLengthStreamingMode(uploadFile.length());
						conToSend.connect();
						outputStreamToRequestBody = new DataOutputStream(conToSend.getOutputStream());
						outputStreamToRequestBody = progressCallback != null
								? new ProgressHttpEntityWrapper.ProgressFilterOutputStream(
								outputStreamToRequestBody, progressCallback, uploadFile.length(), relativePath)
								: outputStreamToRequestBody;
						inputStreamToLogFile = new FileInputStream(uploadFile);
						while ((bytesRead = inputStreamToLogFile.read(buffer)) != -1) {
							bytes.put((long) bytesRead);
							outputStreamToRequestBody.write(buffer, 0, bytesRead);
							totalBytesRead += bytesRead;
							System.out.println(relativePath + " " + totalBytesRead);
						}
						outputStreamToRequestBody.flush();
						outputStreamToRequestBody.close();
						inputStreamToLogFile.close();
						conToSend.getResponseMessage();
						conToSend.disconnect();
						return _pma_checkUploadedFile(sessionID, uploadID, relativePath);
					}  catch (Exception e) {
						e.printStackTrace();
						if (outputStreamToRequestBody != null) {
							outputStreamToRequestBody.flush();
							// Close the stream
							outputStreamToRequestBody.close();
						}
						if (inputStreamToLogFile != null) {
							inputStreamToLogFile.close();
						}
						conToSend.getResponseCode();
						return false;
					}
				}
			}
		return true;
	}

	public static List<HashMap<String, String>> _pma_filesToUpload(String localSourceSlide, String targetFolder, String sessionID) {
		if (localSourceSlide == null) {
			throw new RuntimeException("Slide name is empty");
		}
		for (String slide : Core.getSlides(targetFolder, sessionID)) {
			if (slide.equals(targetFolder + Core.getSlideFileName(localSourceSlide))) {
				try {
					throw new Exception("The file: ===" +  Core.getSlideFileName(localSourceSlide) +"=== with the same name and extension already exists in the target folder: " + targetFolder);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		Map<String, Map<String, String>> files = Core.getFilesForSlide(localSourceSlide, pmaCoreLiteSessionID);

		String mainDirectory = "";
		for (Map.Entry<String, Map<String, String>> entry : files.entrySet()) {
			String key = entry.getKey();
			Map<String, String> value = entry.getValue();
			File file = new File(key);
			String md = file.getParent();

			if (mainDirectory.length() == 0 || md.length() < mainDirectory.length()) {
				mainDirectory = md;
			}
		}

		mainDirectory = mainDirectory.replace("\\", "/");
		List<HashMap<String, String>> uploadFiles = new ArrayList<HashMap<String, String>>();
		Map<String,Long> hm = new HashMap<>();
		LinkedHashMap<String, Long> reverseSortedMap = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, String>> entry : files.entrySet()) {
			String key = entry.getKey();
			Map<String, String> value = entry.getValue();
			long s = (new File(key)).length();
			if (s <= 0) {
				continue;
			}
			hm.put(key, s);
		}

		hm.entrySet()
				.stream()
				.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
				.forEachOrdered(x -> reverseSortedMap.put(x.getKey(), x.getValue()));
		for (Map.Entry<String, Long> revers : reverseSortedMap.entrySet()) {
			String key = revers.getKey();
			long s = (new File(key)).length();
			if (s <= 0) {
				continue;
			}
			String path = key.replace(mainDirectory, "").replaceAll("^\\+|\\+$/g", "")
					.replaceAll("^/+|/+$/g", "");
			HashMap<String, String> fileObj = new HashMap<String, String>();
			fileObj = new HashMap<String, String>();
			fileObj.put("Path", path);
			fileObj.put("Length", Long.toString(s));
			fileObj.put("IsMain", Boolean.toString(path.equals(Core.getSlideFileName(localSourceSlide))));
			fileObj.put("FullPath", key);
			uploadFiles.add(fileObj);
		}
		return uploadFiles;
	}

	public static JSONObject _pma_get_DataIdTypeConString_forUpload(String localSourceSlide, String targetFolder, String sessionID) {
		JSONObject data = new JSONObject();
		data.put("Path", targetFolder);
		data.put("Files", new JSONArray(_pma_filesToUpload(localSourceSlide, targetFolder, sessionID)));
		JSONObject jsonResponse = PMA.getJSONObjectResponse(_pma_generateUploadID(sessionID, data));
		return jsonResponse;
	}

	public static boolean _pma_uploadTransfer(String uploadID, String uploadType, File uploadFile, String relativePath,
										 String URL, String sessionID,
										 ProgressHttpEntityWrapper.ProgressCallback progressCallback) throws Exception {
		out.println(uploadID);
		out.println(uploadFile.getName());
		out.println(relativePath);
		out.println(URL);
		OutputStream outputStreamToRequestBody = null;
		FileInputStream inputStreamToLogFile = null;
		HttpURLConnection con = null;
		try {
			URL urlResource = new URL(URL);
			if (URL.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("PUT");
			con.setConnectTimeout(0); // infinite timeout
			con.setReadTimeout(0); // infinite timeout
			con.setRequestProperty("Connection", "Keep-Alive");
			con.setRequestProperty("Cache-Control", "no-cache");
			con.setRequestProperty("Content-Length", String.valueOf(uploadFile.length()));
			con.setDoOutput(true);
			con.setUseCaches(false);
			con.setFixedLengthStreamingMode(uploadFile.length());
			con.connect();
			outputStreamToRequestBody = new DataOutputStream(con.getOutputStream());
			outputStreamToRequestBody = progressCallback != null
					? new ProgressHttpEntityWrapper.ProgressFilterOutputStream(
					outputStreamToRequestBody, progressCallback, uploadFile.length(), relativePath)
					: outputStreamToRequestBody;
			inputStreamToLogFile = new FileInputStream(uploadFile);
			byte[] buffer = new byte[4 * 1024];
			int bytesRead = -1;
			long totalBytesRead = 0;
			while ((bytesRead = inputStreamToLogFile.read(buffer)) != -1) {
				bytes.put((long) bytesRead);
				outputStreamToRequestBody.write(buffer, 0, bytesRead);
				totalBytesRead += bytesRead;
				System.out.println(relativePath + " " + totalBytesRead);
			}
			outputStreamToRequestBody.flush();
			outputStreamToRequestBody.close();
			inputStreamToLogFile.close();
			con.getResponseMessage();
			con.disconnect();
			return _pma_checkUploadedFile(sessionID, uploadID, relativePath);
		} catch (Exception e) {
			e.printStackTrace();
			if (outputStreamToRequestBody != null) {
				outputStreamToRequestBody.flush();
				outputStreamToRequestBody.close();
			}
			if (inputStreamToLogFile != null) {
				inputStreamToLogFile.close();
			}
			con.getResponseCode();
			return false;
		}
	}

	private static boolean _pma_checkUploadedFile(String sessionID, String uploadID, String relativePath) {
		try {
			String url = (pmaUrl(sessionID).endsWith("/") ? pmaUrl(sessionID) : pmaUrl(sessionID) + "/") + "transfer/Upload/" + uploadID + "?sessionID=" + sessionID;
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
			if (PMA.isJSONObject(jsonString)) {
				JSONObject jsonResponse = PMA.getJSONObjectResponse(jsonString);
				JSONArray jsonArray = jsonResponse.optJSONArray("Files");
				for (int i = 0; i < jsonArray.length(); i++) {
					if (jsonArray.getJSONObject(i).optString("Path").equals(relativePath)) {
						return jsonArray.getJSONObject(i).optBoolean("Complete");
					}
				}
				return false;
			} else {
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	private static String _pma_generateUploadID(String sessionID, JSONObject jsonResponse) {
		String json = String.valueOf(jsonResponse);
		try {
			String url = (pmaUrl(sessionID).endsWith("/") ? pmaUrl(sessionID) : pmaUrl(sessionID) + "/") + "transfer/Upload?sessionID=" + sessionID;
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestProperty("Content-Length", String.valueOf(json.getBytes("UTF-8")));
			con.setRequestMethod("POST");
			con.setRequestProperty("Accept", "application/json");
			con.setRequestProperty("Content-Type", "application/json");
			con.setUseCaches(false);
			con.setDoOutput(true);
			// we set the json string as the request body
			OutputStream os = con.getOutputStream();
			os.write(json.getBytes("UTF-8"));
			os.close();
			return PMA.getJSONAsStringBuffer(con).toString();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * * This method downloads single slides and multi-assembled slides.
	 *
	 * Downloads a slide from a PMA.core server. Requires a PMA.start installation
	 * @param varargs 0:  mainSlideFile is full path of the slide. Is a required income parameter!
	 * @param varargs 1: saveDirectory  is a directory where slide will be saved. Is a required income parameter!
	 * @param varargs 2: sessionID Is a required income parameter!
	 * @param varargs 3: is the callback to return readable bytes.
	 *                   Is NOT a required income parameter if there is no fourth relativePath parameter after it.
	 *                   If relativePath is present then the callback is required or null.
	 * @param varargs 4: is relativePath used when downloading files that include a folder and subfolders with compound files one by one.
	 *                In this case, the folders and subfolders must be prepared in advance before the download method,
	 *                and the saveDirectory parameter must be the full path including the folders, subfolders, file, and extension.
	 *                Is NOT a required income parameter!
	 * @throws Exception if incoming parameters are not valid
	 * @return true if download is done.
	 *
	 * Callback usage model:
	 * ProgressHttpEntityWrapper.ProgressCallback progressCallback = new ProgressHttpEntityWrapper.ProgressCallback() {
	 *             @Override
	 *             public void progress(long bytesRead, long transferred, long totalBytes, String filename) {
	 *                 /* bytesRead - readable bytes
	 *                 /* transferred - total in %
	 *                 /* totalBytes - += bytesRead
	 *                 /* filename - name of downloaded file
	 *             }
	 *         };
	 *		Core.download(***);
	 */
	public static boolean download(Object... varargs) throws Exception {
		String mainSlideFile = varargs.length > 0 ? (String) varargs[0] : null;
		String saveDirectory = varargs.length > 1 ? (String) varargs[1] : null;
		String sessionID = varargs.length > 2 ? (String) varargs[2] : null;
		ProgressHttpEntityWrapper.ProgressCallback progressCallback = varargs.length > 3
				? (ProgressHttpEntityWrapper.ProgressCallback) varargs[3] : null;
		if (Objects.equals(mainSlideFile, "") || mainSlideFile == null) {
			throw new NullPointerException("mainSlideFile is null");
		}
		if (Objects.equals(saveDirectory, "") || saveDirectory == null) {
			throw new NullPointerException("saveDirectory is null");
		}
		if (Objects.equals(sessionID, "") || sessionID == null || !ping(sessionID)) {
			throw new NullPointerException("sessionID is null");
		}
		saveDirectory = (saveDirectory.endsWith("\\") ? saveDirectory : saveDirectory + "\\");
		if (new File(saveDirectory + Core.getSlideFileName(mainSlideFile)).exists()) {
			throw new Exception(" A file with the same name: " + Core.getSlideFileName(mainSlideFile) + " already exists in the selected folder.");
		}
		else {
			DataInputStream inputStreamToRequestBody = null;
			OutputStream outputStreamToLogFile = null;
			HttpURLConnection con = null;
			sessionID = sessionId(sessionID);
			String server = pmaUrl(sessionID);
			File downloadFile = null;
			long size = 0;
			String mainFile = null;
			String rootPath;
			boolean relPathAndSizeChecked = false;
			List<Map<String, String>> slideRelatedFiles = Core.enumerateFilesForSlidePMACore(mainSlideFile, sessionID);
			for (Map<String, String> element : slideRelatedFiles) {
				String relativePath = varargs.length > 4 ? (String) varargs[4] : null;
				mainFile = slideRelatedFiles.get(slideRelatedFiles.size() - 1).get("Path").toString();
				rootPath = mainFile.substring(0, mainFile.lastIndexOf("/") + 1);
				if (relativePath == null) {
				relativePath = element.get("Path").replace(rootPath, "");
				if (relativePath.contains("/")) {
						String relativePathFrBnTillLtSlash = relativePath.substring(relativePath.indexOf(""), relativePath.lastIndexOf("/"));
						String[] folderToCreate = relativePathFrBnTillLtSlash.split("/");
						new File(saveDirectory + folderToCreate[0]).mkdir();
						for (int i = 0; i < folderToCreate.length; i++) {
							if (!folderToCreate[i].contains(folderToCreate[0])) {
								new File(saveDirectory + folderToCreate[0] + "/" + folderToCreate[i]).mkdir();
							}
						}
					}
					size = Long.parseLong(element.get("Size"));
					downloadFile = new File(saveDirectory + relativePath);
				} else if (!relPathAndSizeChecked) {
					for (Map<String, String> pathAndSize : slideRelatedFiles) {
						if (pathAndSize.get("Path").equals(rootPath + relativePath)) {
							size = Long.parseLong(pathAndSize.get("Size"));
							downloadFile = new File(saveDirectory);
							relPathAndSizeChecked = true;
						}
					}
				}
				try {
					String url = server + "transfer/Download" + "?sessionId="
							+ sessionID + "&image=" + PMA.pmaQ(mainSlideFile) + "&path=" + PMA.pmaQ(relativePath);
					URL urlResource = new URL(url);
					if (url.startsWith("https")) {
						con = (HttpsURLConnection) urlResource.openConnection();
					} else {
						con = (HttpURLConnection) urlResource.openConnection();
					}

					con.setConnectTimeout(0); // infinite timeout
					con.setReadTimeout(0); // infinite timeout
					con.setRequestMethod("GET");
					con.setRequestProperty("Connection", "Keep-Alive");
					con.setRequestProperty("Cache-Control", "no-cache");
					con.setDoOutput(true);
					con.setUseCaches(false);
					con.connect();
					if (con.getResponseCode() == 303) {
						url = con.getHeaderField("Location");
						urlResource = new URL(url);
						con.disconnect();
						con = null;
						if (url.startsWith("https")) {
							con = (HttpsURLConnection) urlResource.openConnection();
						} else {
							con = (HttpURLConnection) urlResource.openConnection();
						}
						con.setConnectTimeout(0); // infinite timeout
						con.setReadTimeout(0); // infinite timeout
						con.setRequestMethod("GET");
						con.setRequestProperty("Connection", "Keep-Alive");
						con.setRequestProperty("Cache-Control", "no-cache");
						con.setDoOutput(true);
						con.setUseCaches(false);
						con.connect();
					}
					inputStreamToRequestBody = new DataInputStream(con.getInputStream());
					outputStreamToLogFile = new FileOutputStream(downloadFile);
					outputStreamToLogFile = progressCallback != null
							? new ProgressHttpEntityWrapper.ProgressFilterOutputStream(
							outputStreamToLogFile, progressCallback, size, relativePath)
							: outputStreamToLogFile;
					byte[] buffer = new byte[4 * 1024];
					int bytesRead = -1;
					long totalBytesRead = 0;
					while ((bytesRead = inputStreamToRequestBody.read(buffer)) != -1) {
						bytes.put((long) bytesRead);
						System.out.println(relativePath + " " + totalBytesRead);
						outputStreamToLogFile.write(buffer, 0, bytesRead);
						totalBytesRead += bytesRead;
					}
				} catch (Exception e) {
					e.printStackTrace();
					if (inputStreamToRequestBody != null) {
						inputStreamToRequestBody.close();
					}
					if (outputStreamToLogFile != null) {
						outputStreamToLogFile.flush();
						outputStreamToLogFile.close();
					}
					con.getResponseCode();
					return false;
				}
				if (relPathAndSizeChecked) {
					break;
				}
			}
			inputStreamToRequestBody.close();
			outputStreamToLogFile.flush();
			outputStreamToLogFile.close();
			con.getResponseCode();
		}
		return true;
	}

	/**
	 * This method is for reading bytes in real-time for download and upload methods.
	 * @return bytes
	 */
	public static Long readBytes() {
		try {
			return bytes.take();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * This method returns a boolean result based on the input parameters.
	 * 
	 * @param sessionId
	 * @param url
	 * @param length
	 * @return true
	 */
	public static Boolean addServer(String sessionId, String url, int length) {
		pmaSessions.put(sessionId, url);
		pmaSlideInfos.put(sessionId, new HashMap<String, Object>());
		pmaAmountOfDataDownloaded.put(sessionId, length);

		return true;
	}

	/**
	 * This method returns pmaSessions.
	 * 
	 * @return pmaSessions
	 */
	public static Map<String, Object> sessions() {
		return pmaSessions;
	}

	/**
	 * This method connect to cloud with json data.
	 * 
	 * @param username
	 * @param password
	 * @return cloudServerData
	 */
	public static CloudServerData connectToCloud(String username, String password) {
		String url = "https://myapi.pathomation.com/oauth/token";
		String payload = "{" +
				"\"grant_type\": \"password\", " +
				"\"client_id\": 5, " +
				"\"client_secret\": \"GCUwTDdIuy2zqay70ZqraOPEzjXCcFFy8TjJjoid\", " +
				"\"username\": \"" + username + "\", " +
				"\"password\": \"" + password + "\", " +
				"\"caller\": \"Plugin Histoj\", " +
				"\"scope\": \"*\" " +
				"}";
		String response = postItem(url, payload);
		if (response == null) {
			return null;
		}
		try {
			JSONObject obj = new JSONObject(response);
			String accessToken = obj.getString("access_token");
			response = getCloudAuth(accessToken);
			obj = new JSONObject(response);
			String status = obj.getString("status");
			String reason = obj.optString("reason");
			if ("fail".equals(status)) {
				return new CloudServerData(null, null, null, response.length(), false, reason);
			}

			String sessionId = obj.getString("session_id");
			String serverUrl = obj.getJSONArray("selected_nodes").getJSONObject(0).getString("Uri");
			String folder = obj.getString("folder");
			pmaUsernames.put(sessionId, username);
			pmaSessions.put(sessionId, serverUrl);
			if (!pmaSlideInfos.containsKey(sessionId)) {
				pmaSlideInfos.put(sessionId, new HashMap<String, Object>());
			}
			pmaAmountOfDataDownloaded.put(sessionId, obj.length());
			return new CloudServerData(serverUrl, sessionId, folder, response.length(), true, null);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * This method makes DataOutputStream connection to send data to cloud.
	 * 
	 * @param url
	 * @param payload
	 * @return ResponseString
	 */
	public static String postItem(String url, String payload) {
		try {
			URL urlResource = new URL(url);
			URLConnection conn = urlResource.openConnection();
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("charset", "utf-8");
			conn.setUseCaches(false);
			try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
				wr.write(payload.getBytes());
			}

			return getResponseString(conn);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * This method make authorization with token to connect cloud
	 * 
	 * @param accessToken
	 * @return ResponseString from cloud
	 */
	public static String getCloudAuth(String accessToken) {
		try {
			URL urlResource = new URL("https://myapi.pathomation.com/api/v1/authenticate");
			URLConnection conn = urlResource.openConnection();
			conn.setRequestProperty("Authorization", "Bearer " + accessToken);
			conn.setUseCaches(false);
			return getResponseString(conn);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * This method reads and returns the input string data
	 * 
	 * @param conn
	 * @return
	 * @throws IOException
	 */
	private static String getResponseString(URLConnection conn) throws IOException {
		Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
		StringBuilder sb = new StringBuilder();
		for (int c; (c = in.read()) >= 0;)
			sb.append((char) c);
		return sb.toString();
	}

	/**
	 * This method reads and returns the input json data
	 * 
	 * @param value
	 * @return jsonResponse
	 */
	public static JSONObject getJSONResponse(String value) {
		JSONObject jsonResponse;
		try {
			jsonResponse = new JSONObject(value.toString());
		} catch (JSONException e) {
			return null;
		}

		return jsonResponse;
	}

	/**
	 *
	 *
	 * @param con
	 *            url to retrieve JSON from
	 * @return StringBuffer Json result
	 */
	public static StringBuffer getJSONAsStringBuffer(HttpURLConnection con) {
		try {
			BufferedReader in;
			if (Integer.toString(con.getResponseCode()).startsWith("2")) {
				in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
			} else {
				in = new BufferedReader(new InputStreamReader(con.getErrorStream(), StandardCharsets.UTF_8));
			}
			String inputline;
			StringBuffer response = new StringBuffer();
			while ((inputline = in.readLine()) != null) {
				response.append(inputline);
			}
			in.close();
			return response;

		} catch (Exception e) {
			return null;
		}
	}

	/**
	 *
	 * @param value
	 *              json returned as String
	 * @return Boolean true if it's a JSONObject, false if it's an Array
	 */
	public static Boolean isJSONObject(String value) {
		if (value.startsWith("{")) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * This method is used to get a JSONArray from a String argument
	 *
	 * @param value
	 *              String argument
	 * @return JSONArray converts String argument to JSONArray
	 */
	public static JSONArray getJSONArrayResponse(String value) {
		JSONArray jsonResponse;
		try {
			jsonResponse = new JSONArray(value);
		} catch (JSONException e) {
			return null;
		}
		return jsonResponse;
	}
}

