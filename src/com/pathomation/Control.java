package com.pathomation;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Wrapper class around PMA.control API
 * 
 * @author Yassine Iddaoui
 *
 */
public class Control {

	// for logging purposes
	public static Logger logger = null;

	/**
	 * This method is used to get version info from PMA.control instance running at
	 * pmacontrolURL
	 * 
	 * @param pmaControlURL PMA Control's URL
	 * @return Version information
	 */
	public static String getVersionInfo(String pmaControlURL) {
		// Get version info from PMA.control instance running at pmacontrolURL
		// why? because GetVersionInfo can be invoked WITHOUT a valid SessionID;
		// apiUrl() takes session information into account
		String url = Core.join(pmaControlURL, "api/version");
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
			String jsonString = Core.getJSONAsStringBuffer(con).toString();
			// JSONObject jsonResponse = Core.getJSONResponse(jsonString);
			// we remove ""
			return jsonString.substring(1, jsonString.length() - 1);
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to retrieve a list of currently defined training sessions
	 * in PMA.control
	 * 
	 * @param pmaControlURL    URL for PMA.Control
	 * @param pmaCoreSessionID PMA.core session ID
	 * @return List of registred sessions
	 */
	public static JSONArray getSessions(String pmaControlURL, String pmaCoreSessionID) {
		String url = Core.join(pmaControlURL, "api/Sessions?sessionID=" + Core.pmaQ(pmaCoreSessionID));
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
			String jsonString = Core.getJSONAsStringBuffer(con).toString();
			JSONArray jsonResponse = Core.getJSONArrayResponse(jsonString);
			return jsonResponse;
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to Retrieve a dictionary with currently defined training
	 * sessions in PMA.control The resulting dictionary use the session's identifier
	 * as the dictionary key, and therefore this method is easier to quickly
	 * retrieve and represent session-related data. However, this method returns
	 * less verbose data than get_sessions()
	 * 
	 * @param pmaControlURL    URL for PMA.Control
	 * @param pmaCoreSessionID PMA.core session ID
	 * @return Map of data related to registred session IDs
	 */
	public static Map<String, Map<String, String>> getSessionIds(String pmaControlURL, String pmaCoreSessionID) {
		JSONArray fullSessions = getSessions(pmaControlURL, pmaCoreSessionID);
		Map<String, Map<String, String>> newSession = new HashMap<>();
		for (int i = 0; i < fullSessions.length(); i++) {
			Map<String, String> sessData = new HashMap<String, String>();
			sessData.put("LogoPath", fullSessions.getJSONObject(i).getString("LogoPath"));
			sessData.put("StartsOn", fullSessions.getJSONObject(i).getString("StartsOn"));
			sessData.put("EndsOn", fullSessions.getJSONObject(i).getString("EndsOn"));
			sessData.put("ModuleId", fullSessions.getJSONObject(i).getString("ModuleId"));
			sessData.put("State", fullSessions.getJSONObject(i).getString("State"));
			newSession.put(fullSessions.getJSONObject(i).getString("Id"), sessData);
		}
		return newSession;
	}

	/**
	 * This method is used to retrieve all the data for all the defined case
	 * collections in PMA.control
	 * 
	 * @param pmaControlURL    URL for PMA.Control
	 * @param pmaCoreSessionID PMA.core session ID
	 * @param varargs          Array of optional arguments
	 *                         <p>
	 *                         project : First optional argument(String), default
	 *                         value(null), project case collections belong to
	 *                         </p>
	 * @return Array of case collections
	 */
	public static JSONArray getCaseCollections(String pmaControlURL, String pmaCoreSessionID, String... varargs) {
		// setting the default value when argument's value is omitted
		String project = ((varargs.length > 0) && (varargs[0] != null)) ? varargs[0] : "";
		String url = Core.join(pmaControlURL, "api/CaseCollections?sessionID=" + Core.pmaQ(pmaCoreSessionID)
				+ ((project.length() > 0) ? ("&project=" + Core.pmaQ(project)) : ""));
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
			String jsonString = Core.getJSONAsStringBuffer(con).toString();
			JSONArray jsonResponse = Core.getJSONArrayResponse(jsonString);
			return jsonResponse;
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to get a case collection by its title
	 * 
	 * @param pmaControlURL    URL for PMA.Control
	 * @param pmaCoreSessionID PMA.core session ID
	 * @param caseCollection   The case collection's title
	 * @return The case collection in json object format
	 */
	public static JSONObject getCaseCollectionByTitle(String pmaControlURL, String pmaCoreSessionID,
			String caseCollection) {
		JSONArray caseCollections = getCaseCollections(pmaControlURL, pmaCoreSessionID);
		if (caseCollections == null) {
			return null;
		} else {
			for (int i = 0; i < caseCollections.length(); i++) {
				if (caseCollections.optJSONObject(i).get("Title").toString().equals(caseCollection)) {
					return caseCollections.optJSONObject(i);
				}
			}
			// The case collection wasn't found!
			return null;
		}
	}

	/**
	 * This method is used to retrieve all projects and their data in PMA.control
	 * 
	 * @param pmaControlURL    URL for PMA.Control
	 * @param pmaCoreSessionID PMA.core session ID
	 * @return Array of projects
	 */
	public static JSONArray getProjects(String pmaControlURL, String pmaCoreSessionID) {
		String url = Core.join(pmaControlURL, "api/Projects?sessionID=" + Core.pmaQ(pmaCoreSessionID));
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
			String jsonString = Core.getJSONAsStringBuffer(con).toString();
			JSONArray jsonResponse = Core.getJSONArrayResponse(jsonString);
			return jsonResponse;
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

}
