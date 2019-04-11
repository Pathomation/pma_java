package com.pathomation;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Wrapper class around PMA.control API
 * 
 * @author Yassine Iddaoui
 *
 */
public class Control {

	enum PmaSessionRole {
		SUPERVISOR, TRAINEE, OBSERVER
	}

	enum PmaInteractionMode {
		LOCKED, TEST_ACTIVE, REVIEW, CONSENSUS_VIEW, BROWSE, BOARD, CONSENSUS_SCORE_EDIT, SELF_REVIEW, SELF_TEST,
		HIDDEN, CLINICAL_INFORMATION_EDIT
	}

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
			String jsonString = Core.httpGet(url, "application/json");
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
	private static JSONArray getSessions(String pmaControlURL, String pmaCoreSessionID) {
		String url = Core.join(pmaControlURL, "api/Sessions?sessionID=" + Core.pmaQ(pmaCoreSessionID));
		try {
			String jsonString = Core.httpGet(url, "application/json");
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
	 * Helper method to convert a JSON representation of a PMA.control training
	 * session to a proper Java-esque structure
	 * 
	 * @param session Session details
	 * @return Map of formatted session information
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> formatSessionProperly(JSONObject session) {
		Map<String, Object> sessionData = new HashMap<>();
		sessionData.put("Id", session.getString("Id"));
		sessionData.put("Title", session.getString("Title"));
		sessionData.put("LogoPath", session.getString("LogoPath"));
		sessionData.put("StartsOn", session.getString("StartsOn"));
		sessionData.put("EndsOn", session.getString("EndsOn"));
		sessionData.put("ModuleId", session.getString("ModuleId"));
		sessionData.put("State", session.getString("State"));
		sessionData.put("CaseCollections", new HashMap<Integer, String>());
		sessionData.put("NumberOfParticipants", session.optJSONArray("Participants").length());
		JSONArray collections = session.getJSONArray("CaseCollections");
		for (int i = 0; i <= collections.length(); i++) {
			JSONObject collection = collections.optJSONObject(i);
			((Map<Integer, String>) sessionData.get("CaseCollections")).put(collection.optInt("Id"),
					collection.optString("Title"));
		}
		return sessionData;
	}

//	/**
//	 * This method is used to Retrieve a dictionary with currently defined training
//	 * sessions in PMA.control. The resulting dictionary use the session's
//	 * identifier as the dictionary key, and therefore this method is easier to
//	 * quickly retrieve and represent session-related data. However, this method
//	 * returns less verbose data than getSessions()
//	 * 
//	 * @param pmaControlURL    URL for PMA.Control
//	 * @param pmaCoreSessionID PMA.core session ID
//	 * @return Map of data related to registred session IDs
//	 */
//	public static Map<String, Map<String, String>> getSessionIds(String pmaControlURL, String pmaCoreSessionID) {
//		JSONArray fullSessions = getSessions(pmaControlURL, pmaCoreSessionID);
//		Map<String, Map<String, String>> newSession = new HashMap<>();
//		for (int i = 0; i < fullSessions.length(); i++) {
//			Map<String, String> sessData = new HashMap<String, String>();
//			sessData.put("LogoPath", fullSessions.getJSONObject(i).getString("LogoPath"));
//			sessData.put("StartsOn", fullSessions.getJSONObject(i).getString("StartsOn"));
//			sessData.put("EndsOn", fullSessions.getJSONObject(i).getString("EndsOn"));
//			sessData.put("ModuleId", fullSessions.getJSONObject(i).getString("ModuleId"));
//			sessData.put("State", fullSessions.getJSONObject(i).getString("State"));
//			newSession.put(fullSessions.getJSONObject(i).getString("Id"), sessData);
//		}
//		return newSession;
//	}

	/**
	 * This method is used to Retrieve a dictionary with currently defined training
	 * sessions in PMA.control. The resulting dictionary use the session's
	 * identifier as the dictionary key, and therefore this method is easier to
	 * quickly retrieve and represent session-related data.
	 * 
	 * @param pmaControlURL       URL for PMA.Control
	 * @param pmaControlProjectID Project ID
	 * @param pmaCoreSessionID    PMA.core session ID
	 * @return Map of data related to registred session IDs
	 */
	public static Map<Integer, Map<String, Object>> getSessions(String pmaControlURL, Integer pmaControlProjectID,
			String pmaCoreSessionID) {
		JSONArray fullSessions = getSessions(pmaControlURL, pmaCoreSessionID);
		Map<Integer, Map<String, Object>> newSessionMap = new HashMap<>();
		for (int i = 0; i < fullSessions.length(); i++) {
			JSONObject session = fullSessions.optJSONObject(i);
			if ((pmaControlProjectID != null) || (pmaControlProjectID == session.optInt("ModuleId"))) {
				newSessionMap.put(session.optInt("Id"), formatSessionProperly(session));
			}
		}
		return newSessionMap;
	}

	/**
	 * This method is used to get sessions for a certain participant
	 * 
	 * @param pmaControlURL    URL for PMA.Control
	 * @param pmaCoreUsername  PMA.core username
	 * @param pmaCoreSessionID PMA.core session ID
	 * @return Map of sessions for a certain participant
	 */
	public static Map<Integer, Map<String, Object>> getSessionsForParticipant(String pmaControlURL,
			String pmaCoreUsername, String pmaCoreSessionID) {
		JSONArray fullSessions = getSessions(pmaControlURL, pmaCoreSessionID);
		Map<Integer, Map<String, Object>> newSessionMap = new HashMap<>();
		for (int i = 0; i < fullSessions.length(); i++) {
			JSONObject session = fullSessions.optJSONObject(i);
			JSONArray participants = session.optJSONArray("Participants");
			for (int j = 0; j < participants.length(); j++) {
				JSONObject participant = participants.optJSONObject(j);
				if (participant.getString("User").toLowerCase().equals(pmaCoreUsername.toLowerCase())) {
					Map<String, Object> sMap = formatSessionProperly(session);
					sMap.put("Role", participant.getString("Role"));
					newSessionMap.put(session.getInt("Id"), sMap);
				}
			}
		}
		return newSessionMap;
	}

	/**
	 * This method is used to extract the participants in a particular session
	 * 
	 * @param pmaControlURL       URL for PMA.Control
	 * @param pmaControlSessionID PMA.control session ID
	 * @param pmaCoreSessionID    PMA.core session ID
	 * @return Map of participants in a particular session
	 */
	public static Map<String, String> getSessionParticipants(String pmaControlURL, Integer pmaControlSessionID,
			String pmaCoreSessionID) {
		JSONArray fullSessions = getSessions(pmaControlURL, pmaCoreSessionID);
		Map<String, String> userMap = new HashMap<>();
		for (int i = 0; i < fullSessions.length(); i++) {
			JSONObject session = fullSessions.optJSONObject(i);
			if (session.optInt("Id") == pmaControlSessionID) {
				JSONArray participants = session.optJSONArray("Participants");
				for (int j = 0; j < participants.length(); j++) {
					JSONObject participant = participants.optJSONObject(j);
					userMap.put(participant.optString("User"), participant.optString("Role"));
				}
			}
		}
		return userMap;
	}

	/**
	 * This method is used to check if a specific user participates in a specific
	 * session
	 * 
	 * @param pmaControlURL       URL for PMA.Control
	 * @param pmaCoreUsername     PMA.core username
	 * @param pmaControlSessionID PMA.control session ID
	 * @param pmaCoreSessionID    PMA.core session ID
	 * @return True if a specific user participates in a specific session, false
	 *         otherwise.
	 */
	public static Boolean isParticipantInSession(String pmaControlURL, String pmaCoreUsername,
			Integer pmaControlSessionID, String pmaCoreSessionID) {
		Map<String, String> allParticipants = getSessionParticipants(pmaControlURL, pmaControlSessionID,
				pmaCoreSessionID);
		return allParticipants.containsKey(pmaCoreUsername);
	}

	/**
	 * This method is used to construct the session URL
	 * 
	 * @param pmaControlURL       URL for PMA.Control
	 * @param pmaControlSessionID PMA.control session ID
	 * @param pmaControlCase      Case name
	 * @param pmaCoreSessionID    PMA.core session ID
	 * @return Session's URL
	 */
	public static String getSessionURL(String pmaControlURL, Integer pmaControlSessionID, String pmaControlCase,
			String pmaCoreSessionID) {
		if (pmaControlCase == null) {
			return Core.join(pmaControlURL, "training/training/") + pmaControlSessionID + "?SessionID="
					+ pmaCoreSessionID;
		} else if (pmaControlCase.matches("^[0-9]*$")) {
			return Core.join(pmaControlURL, "training/training/") + pmaControlSessionID + "?SessionID="
					+ pmaCoreSessionID;
		} else {
			return Core.join(pmaControlURL, "training/training/") + pmaControlSessionID + "?SessionID="
					+ pmaCoreSessionID;
		}
	}

	/**
	 * This method is used to register a participant for a session
	 * 
	 * @param pmaControlURL             PMA.control URL
	 * @param pmacoreUsername           PMA.core username
	 * @param pmacontrolSessionID       PMA.control session ID
	 * @param pmacontrolRole            Role
	 * @param pmacontrolInteractionMode Interaction mode
	 * @param pmacoreSessionID          PMA.core session ID
	 */
	public static void registerParticipantForSession(String pmaControlURL, String pmacoreUsername,
			Integer pmacontrolSessionID, PmaSessionRole pmacontrolRole, PmaInteractionMode pmacontrolInteractionMode,
			String pmacoreSessionID) {
		String url = Core.join(pmaControlURL, "api/Sessions/") + pmacontrolSessionID.toString()
				+ "/Participants?SessionID=" + pmacoreSessionID;
//		Map<String, String> data = new HashMap<String, String>() {{
//			put("UserName", pmacoreUsername);
//			put("Role", String.valueOf(pmacontrolRole.ordinal()));
//			put("InteractionMode", String.valueOf(pmacontrolInteractionMode.ordinal() + 1));
//		}};
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
			con.setUseCaches(false);
			con.setDoOutput(true);
			String data = "{ \"UserName\": \"" + pmacoreUsername + "\", \"Role\": \""
					+ String.valueOf(pmacontrolRole.ordinal()) + "\",  \"InteractionMode\": \""
					+ String.valueOf(pmacontrolInteractionMode.ordinal() + 1) + "\" }";
			OutputStream os = con.getOutputStream();
			os.write(data.getBytes("UTF-8"));
			os.close();
			String jsonString = Core.getJSONAsStringBuffer(con).toString();
			System.out.println(jsonString);
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
		}
	}

	/**
	 * This method is used to retrieve sessions (possibly filtered by project ID),
	 * titles only
	 * 
	 * @param pmaControlURL       URL for PMA.Control
	 * @param pmaControlProjectID Project's ID
	 * @param pmaCoreSessionID    PMA.core session ID
	 * @return List of session titles
	 */
	public static List<String> getSessionTitles(String pmaControlURL, Integer pmaControlProjectID,
			String pmaCoreSessionID) {
		try {
			return new ArrayList<String>(
					getSessionTitlesDict(pmaControlURL, pmaControlProjectID, pmaCoreSessionID).values());
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
	 * This method is used to retrieve (training) sessions (possibly filtered by
	 * project ID), returns a map of session IDs and titles
	 * 
	 * @param pmaControlURL       URL for PMA.Control
	 * @param pmaControlProjectID Project's ID
	 * @param pmaCoreSessionID    PMA.core session ID
	 * @return Map of session IDs and titles
	 */
	public static Map<Integer, String> getSessionTitlesDict(String pmaControlURL, Integer pmaControlProjectID,
			String pmaCoreSessionID) {
		Map<Integer, String> map = new HashMap<>();
		try {
			JSONArray all = getSessions(pmaControlURL, pmaCoreSessionID);
			for (int i = 0; i < all.length(); i++) {
				JSONObject session = all.optJSONObject(i);
				if (pmaControlProjectID == null) {
					map.put(session.optInt("Id"), session.optString("Title"));
				} else if (pmaControlProjectID == session.optInt("ModuleId")) {
					map.put(session.optInt("Id"), session.optString("Title"));
				}
			}
			return map;
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
	 * This method is used to return the first (training) session that has keyword
	 * as part of its string; search is case insensitive
	 * 
	 * @param pmaControlURL    URL for PMA.Control
	 * @param keyword          key word to search against
	 * @param pmaCoreSessionID PMA.core session ID
	 * @return Map of the first (training) session whose title matches the search
	 *         criteria
	 */
	public static Map<String, Object> searchSession(String pmaControlURL, String keyword, String pmaCoreSessionID) {
		JSONArray all = getSessions(pmaControlURL, pmaCoreSessionID);

		for (int i = 0; i < all.length(); i++) {
			JSONObject el = all.optJSONObject(i);
			if (keyword.toLowerCase().equals(el.getString("Title").toLowerCase())) {
				return formatSessionProperly(el);
			}
		}
		return null;
	}

	/**
	 * This method is used to retrieve all the data for all the defined case
	 * collections in PMA.control (RAW JSON data; not suited for human consumption)
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
	private static JSONArray getCaseCollections(String pmaControlURL, String pmaCoreSessionID, String... varargs) {
		// setting the default value when argument's value is omitted
		String project = ((varargs.length > 0) && (varargs[0] != null)) ? varargs[0] : "";
		String url = Core.join(pmaControlURL, "api/CaseCollections?sessionID=" + Core.pmaQ(pmaCoreSessionID)
				+ ((project.length() > 0) ? ("&project=" + Core.pmaQ(project)) : ""));
		try {
//			if (!caseCollectionsJson.containsKey(url)) {
//				URL urlResource = new URL(url);
//				HttpURLConnection con;
//				if (url.startsWith("https")) {
//					con = (HttpsURLConnection) urlResource.openConnection();
//				} else {
//					con = (HttpURLConnection) urlResource.openConnection();
//				}
//				con.setRequestMethod("GET");
//				con.setRequestProperty("Accept", "application/json");
//				String jsonString = Core.getJSONAsStringBuffer(con).toString();
//				JSONArray jsonResponse = Core.getJSONArrayResponse(jsonString);
//				caseCollectionsJson.put(url, jsonResponse);
//			}
//			return caseCollectionsJson.get(url);
			String jsonString = Core.httpGet(url, "application/json");
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

//	/**
//	 * This method is used to get list of titles of all defined case collections in
//	 * PMA.control
//	 * 
//	 * @param pmaControlURL    URL for PMA.Control
//	 * @param pmaCoreSessionID PMA.core session ID
//	 * @param varargs          Array of optional arguments
//	 *                         <p>
//	 *                         project : First optional argument(String), default
//	 *                         value(null), project case collections belong to
//	 *                         </p>
//	 * @return List of case collections titles
//	 */
//	public static List<String> getCaseCollectionTitles(String pmaControlURL, String pmaCoreSessionID,
//			String... varargs) {
//		// setting the default value when argument's value is omitted
//		String project = ((varargs.length > 0) && (varargs[0] != null)) ? varargs[0] : "";
//		JSONArray caseCollections = getCaseCollections(pmaControlURL, pmaCoreSessionID, project);
//		List<String> resutls = new ArrayList<>();
//		for (int i = 0; i < caseCollections.length(); i++) {
//			resutls.add(caseCollections.optJSONObject(i).optString("Title"));
//		}
//		return resutls;
//	}

	/**
	 * This method is used to retrieve case collections (possibly filtered by
	 * project ID), titles only
	 * 
	 * @param pmaControlURL       URL for PMA.Control
	 * @param pmaControlProjectID Project's ID
	 * @param pmaCoreSessionID    PMA.core session ID
	 * @return List of case collections titles
	 */
	public static List<String> getCaseCollectionTitles(String pmaControlURL, Integer pmaControlProjectID,
			String pmaCoreSessionID) {
		try {
			return new ArrayList<String>(
					getCaseCollectionTitlesDict(pmaControlURL, pmaControlProjectID, pmaCoreSessionID).values());
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
	 * This method is used to retrieve case collections (possibly filtered by
	 * project ID), returns a map of case collection IDs and titles
	 * 
	 * @param pmaControlURL       URL for PMA.Control
	 * @param pmaControlProjectID Project's ID
	 * @param pmaCoreSessionID    PMA.core session ID
	 * @return Map of case collection IDs and titles
	 */
	public static Map<Integer, String> getCaseCollectionTitlesDict(String pmaControlURL, Integer pmaControlProjectID,
			String pmaCoreSessionID) {
		Map<Integer, String> map = new HashMap<>();
		try {
			JSONArray allColletions = getCaseCollections(pmaControlURL, pmaCoreSessionID);
			for (int i = 0; i < allColletions.length(); i++) {
				JSONObject collection = allColletions.optJSONObject(i);
				if (pmaControlProjectID == null) {
					map.put(collection.optInt("Id"), collection.optString("Title"));
				} else if (pmaControlProjectID == collection.optInt("ModuleId")) {
					map.put(collection.optInt("Id"), collection.optString("Title"));
				}
			}
			return map;
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
	 * This method is used to get a case collection details (by ID)
	 * 
	 * @param pmaControlURL              URL for PMA.Control
	 * @param pmaControlCaseCollectionID Case collection's ID
	 * @param pmaCoreSessionID           PMA.core session ID
	 * @return The case collection in json object format
	 */
	public static JSONObject getCaseCollection(String pmaControlURL, Integer pmaControlCaseCollectionID,
			String pmaCoreSessionID) {
		JSONArray allCollections = getCaseCollections(pmaControlURL, pmaCoreSessionID);
		for (int i = 0; i < allCollections.length(); i++) {
			JSONObject collection = allCollections.optJSONObject(i);
			if (collection.optInt("Id") == pmaControlCaseCollectionID) {
				return collection;
			}
		}
		// Case collection not found
		return null;
	}

	/**
	 * Helper method to convert a list of sessions with default arguments into a
	 * summarized dictionary
	 * 
	 * @param originalProjectSessions The original project sessions prior to
	 *                                formatting
	 * @return List of summarized maps of the project's sessions in ID, Title format
	 */
	@SuppressWarnings("serial")
	private static List<Map<Integer, String>> formatProjectEmbeddedSessionsProperly(JSONArray originalProjectSessions) {
		List<Map<Integer, String>> listMap = new ArrayList<>();
		for (int i = 0; i < originalProjectSessions.length(); i++) {
			JSONObject jsonObject = originalProjectSessions.optJSONObject(i);
			listMap.add(new HashMap<Integer, String>() {
				{
					put(jsonObject.optInt("Id"), jsonObject.optString("Title"));
				}
			});
		}
		return listMap;
	}

	/**
	 * This method is used to retrieve all projects and their data in PMA.control
	 * (RAW JSON data; not suited for human consumption)
	 * 
	 * @param pmaControlURL    URL for PMA.Control
	 * @param pmaCoreSessionID PMA.core session ID
	 * @return Array of projects
	 */
	private static JSONArray getProjects(String pmaControlURL, String pmaCoreSessionID) {
		String url = Core.join(pmaControlURL, "api/Projects?sessionID=" + Core.pmaQ(pmaCoreSessionID));
		try {
//			if (!projectsJson.containsKey(url)) {
//				URL urlResource = new URL(url);
//				HttpURLConnection con;
//				if (url.startsWith("https")) {
//					con = (HttpsURLConnection) urlResource.openConnection();
//				} else {
//					con = (HttpURLConnection) urlResource.openConnection();
//				}
//				con.setRequestMethod("GET");
//				con.setRequestProperty("Accept", "application/json");
//				String jsonString = Core.getJSONAsStringBuffer(con).toString();
//				JSONArray jsonResponse = Core.getJSONArrayResponse(jsonString);
//				projectsJson.put(url, jsonResponse);
//			}
//			return projectsJson.get(url);
			String jsonString = Core.httpGet(url, "application/json");
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
	 * This method is used to retrieve the projects then returns exclusively their
	 * titles
	 * 
	 * @param pmaControlURL    URL for PMA.Control
	 * @param pmaCoreSessionID PMA.core session ID
	 * @return List of projects' titles
	 */
	public static List<String> getProjectTitles(String pmaControlURL, String pmaCoreSessionID) {
		try {
			return new ArrayList<String>(getProjectTitlesDict(pmaControlURL, pmaCoreSessionID).values());
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
	 * This method is used to retrieve projects then returns a map of project-IDS
	 * and titles
	 * 
	 * @param pmaControlURL    URL for PMA.Control
	 * @param pmaCoreSessionID PMA.core session ID
	 * @return Map of project-IDs and titles
	 */
	public static Map<Integer, String> getProjectTitlesDict(String pmaControlURL, String pmaCoreSessionID) {
		Map<Integer, String> map = new HashMap<>();
		JSONArray allProjects = getProjects(pmaControlURL, pmaCoreSessionID);
		try {
			for (int i = 0; i < allProjects.length(); i++) {
				map.put(allProjects.optJSONObject(i).getInt("Id"), allProjects.optJSONObject(i).getString("Title"));
			}
			return map;
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
	 * This method is used to get a project details (project defined by its ID)
	 * 
	 * @param pmaControlURL       URL for PMA.Control
	 * @param pmaControlProjectID Project's ID
	 * @param pmaCoreSessionID    PMA.core session ID
	 * @return Map of the project's details
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> getProject(String pmaControlURL, Integer pmaControlProjectID,
			String pmaCoreSessionID) {
		try {
			JSONArray allProjects = getProjects(pmaControlURL, pmaCoreSessionID);
			for (int i = 0; i < allProjects.length(); i++) {
				JSONObject prj = allProjects.optJSONObject(i);
				if (prj.optInt("Id") == pmaControlProjectID) {
					// summary session-related information so that it makes sense
					Map<String, Object> jsonMap = new ObjectMapper()
							.readerFor(new TypeReference<Map<String, Object>>() {
							}).readValue(prj.toString());
					jsonMap.put("Sessions", formatProjectEmbeddedSessionsProperly(prj.optJSONArray("Sessions")));
					// now integrate case collection information
					// we get the case collections belonging to the project (via the title)
					JSONArray colls = getCaseCollections(pmaControlURL, pmaCoreSessionID, prj.getString("Title"));
					jsonMap.put("CaseCollections", new HashMap<Integer, String>());
					for (int j = 0; j < colls.length(); j++) {
						JSONObject jsonObject = colls.optJSONObject(j);
						((Map<Integer, String>) jsonMap.get("CaseCollections")).put(jsonObject.getInt("Id"),
								jsonObject.getString("Title"));
					}
					return jsonMap;
				}
			}
			// Project ID not found
			return null;
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
	 * This method is used to retrieve project based on the case ID
	 * 
	 * @param pmaControlURL    URL for PMA.Control
	 * @param pmacontrolCaseID Case's ID
	 * @param pmaCoreSessionID PMA.core session ID
	 * @return Map of the project details
	 */
	public static Map<String, Object> getProjectByCaseID(String pmaControlURL, Integer pmacontrolCaseID,
			String pmaCoreSessionID) {
		JSONArray allCollections = getCaseCollections(pmaControlURL, pmaCoreSessionID);
		for (int i = 0; i < allCollections.length(); i++) {
			JSONObject collection = allCollections.optJSONObject(i);
			JSONArray cases = collection.optJSONArray("Cases");
			for (int j = 0; j < cases.length(); j++) {
				JSONObject currentCase = cases.optJSONObject(j);
				if (currentCase.optInt("Id") == pmacontrolCaseID) {
					return getProject(pmaControlURL, collection.optInt("ModuleId"), pmaCoreSessionID);
				}
			}
		}
		return null;
	}

	/**
	 * This method is used to retrieve project based on the case collection ID
	 * 
	 * @param pmaControlURL              URL for PMA.Control
	 * @param pmacontrolCaseCollectionID Case collection's ID
	 * @param pmaCoreSessionID           PMA.core session ID
	 * @return Map of the project details
	 */
	public static Map<String, Object> getProjectByCaseCollectionID(String pmaControlURL,
			Integer pmacontrolCaseCollectionID, String pmaCoreSessionID) {
		JSONArray allCollections = getCaseCollections(pmaControlURL, pmaCoreSessionID);
		for (int i = 0; i < allCollections.length(); i++) {
			JSONObject collection = allCollections.optJSONObject(i);
			if (collection.optInt("Id") == pmacontrolCaseCollectionID) {
				return getProject(pmaControlURL, collection.optInt("ModuleId"), pmaCoreSessionID);
			}
		}
		return null;
	}

	/**
	 * This method is used to get the projects whose title matches a certain keyword
	 * search pattern
	 * 
	 * @param pmaControlURL    URL for PMA.Control
	 * @param keyword          Keyword to seach projects against
	 * @param pmaCoreSessionID PMA.core session ID
	 * @return List of Maps of projects whose title matches the search criteria
	 */
	public static List<Map<String, Object>> searchProjects(String pmaControlURL, String keyword,
			String pmaCoreSessionID) {
		Map<Integer, String> mapProjectTitles = getProjectTitlesDict(pmaControlURL, pmaCoreSessionID);
		List<Map<String, Object>> lstProjects = new ArrayList<>();
		for (Entry<Integer, String> entry : mapProjectTitles.entrySet()) {
			if (entry.getValue().contains(keyword)) {
				lstProjects.add(getProject(pmaControlURL, entry.getKey(), pmaCoreSessionID));
			}
		}
		if (lstProjects.size() > 0) {
			return lstProjects;
		} else {
			return null;
		}
	}
}
