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

import javax.net.ssl.HttpsURLConnection;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
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

	public enum PmaTrainingSessionRole {
		SUPERVISOR, TRAINEE, OBSERVER
	}

//	public enum PmaInteractionMode {
//		LOCKED, TEST_ACTIVE, REVIEW, CONSENSUS_VIEW, BROWSE, BOARD, CONSENSUS_SCORE_EDIT, SELF_REVIEW, SELF_TEST,
//		HIDDEN, CLINICAL_INFORMATION_EDIT
//	}

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
		String url = PMA.join(pmaControlURL, "api/version");
		try {
			String jsonString = PMA.httpGet(url, "application/json");
			// we remove ""
			return jsonString.substring(1, jsonString.length() - 1);
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
	 * This method is used to retrieve a list of currently defined training sessions
	 * in PMA.control
	 * 
	 * @param pmaControlURL    URL for PMA.Control
	 * @param pmaCoreSessionID PMA.core session ID
	 * @return List of registred sessions
	 */
	private static JSONArray pmaGetTrainingSessions(String pmaControlURL, String pmaCoreSessionID) {
		String url = PMA.join(pmaControlURL, "api/Sessions?sessionID=" + PMA.pmaQ(pmaCoreSessionID));
		try {
			String jsonString = PMA.httpGet(url, "application/json");
			JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
			return jsonResponse;
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
	 * This method is used to retrieve a list of currently defined training sessions
	 * in PMA.control
	 * 
	 * @param pmaControlURL    URL for PMA.Control
	 * @param pmaCoreSessionID PMA.core session ID
	 * @param pmaControlProjectID Project ID
	 * @return List of registred sessions
	 */
	private static JSONArray pmaGetTrainingSessionsViaProject(String pmaControlURL, String pmaCoreSessionID,
			Integer pmaControlProjectID) {
		String url = PMA.join(pmaControlURL,
				"api/Projects/" + pmaControlProjectID + "/Sessions?sessionID=" + PMA.pmaQ(pmaCoreSessionID));
		try {
			String jsonString = PMA.httpGet(url, "application/json");
			JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
			return jsonResponse;
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
	 * Helper method to convert a JSON representation of a PMA.control training
	 * session to a proper Java-esque structure
	 * 
	 * @param session Session details
	 * @return Map of formatted session information
	 */
	@SuppressWarnings({ "unchecked", "serial" })
	public static Map<String, Object> formatTrainingSessionProperly(JSONObject session) {
		Map<String, Object> sessionData = new HashMap<>();
		sessionData.put("Id", session.optInt("Id"));
		sessionData.put("Title", session.optString("Title"));
		sessionData.put("LogoPath", session.optString("LogoPath"));
		sessionData.put("StartsOn", session.optString("StartsOn"));
		sessionData.put("EndsOn", session.optString("EndsOn"));
		sessionData.put("ProjectId", session.optInt("ProjectId"));
		sessionData.put("State", session.optString("State"));
		sessionData.put("CaseCollections", new HashMap<Integer, Map<String, String>>());
		sessionData.put("NumberOfParticipants", session.optJSONObject("Participants").length());
		JSONArray collections = session.getJSONArray("CaseCollections");
		for (int i = 0; i < collections.length(); i++) {
			JSONObject collection = collections.optJSONObject(i);
			((Map<Integer, Map<String, String>>) sessionData.get("CaseCollections"))
					.put(collection.optInt("CaseCollectionId"), new HashMap<String, String>() {
						{
							put("Title", collection.optString("Title"));
							put("Url", collection.optString("Url"));
						}
					});
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

//	/**
//	 * This method is used to retrieve a dictionary with currently defined training
//	 * sessions in PMA.control. The resulting dictionary use the training session's
//	 * identifier as the dictionary key, and therefore this method is easier to
//	 * quickly retrieve and represent session-related data.
//	 * 
//	 * @param pmaControlURL       URL for PMA.Control
//	 * @param pmaControlProjectID Project ID
//	 * @param pmaCoreSessionID    PMA.core session ID
//	 * @return Map of data related to registred session IDs
//	 */
//	public static Map<Integer, Map<String, Object>> getTrainingSessionsOld(String pmaControlURL,
//			Integer pmaControlProjectID, String pmaCoreSessionID) {
//		JSONArray fullTrainingSessions = pmaGetTrainingSessions(pmaControlURL, pmaCoreSessionID);
//		Map<Integer, Map<String, Object>> newTrainingSessionMap = new HashMap<>();
//		for (int i = 0; i < fullTrainingSessions.length(); i++) {
//			JSONObject session = fullTrainingSessions.optJSONObject(i);
//			if ((pmaControlProjectID != null) || (pmaControlProjectID == session.optInt("ProjectId"))) {
//				newTrainingSessionMap.put(session.optInt("Id"), formatTrainingSessionProperly(session));
//			}
//		}
//		return newTrainingSessionMap;
//	}

	/**
	 * This method is used to get sessions for a certain participant
	 * 
	 * @param pmaControlURL       URL for PMA.Control
	 * @param participantUsername PMA.core username
	 * @param pmaCoreSessionID    PMA.core session ID
	 * @return Map of sessions for a certain participant
	 */
	public static Map<Integer, Map<String, Object>> getTrainingSessionsForParticipant(String pmaControlURL,
			String participantUsername, String pmaCoreSessionID) {
		JSONArray fullTrainingSessions = pmaGetTrainingSessions(pmaControlURL, pmaCoreSessionID);
		Map<Integer, Map<String, Object>> newTrainingSessionMap = new HashMap<>();
		for (int i = 0; i < fullTrainingSessions.length(); i++) {
			JSONObject session = fullTrainingSessions.optJSONObject(i);
			JSONObject participants = session.optJSONObject("Participants");
			for (String key : participants.keySet()) {
				if (key.toLowerCase().equals(participantUsername.toLowerCase())) {
					Map<String, Object> sMap = formatTrainingSessionProperly(session);
					sMap.put("Role", participants.get(key));
					newTrainingSessionMap.put(session.getInt("Id"), sMap);
				}
			}
		}
		return newTrainingSessionMap;
	}

	/**
	 * This method is used to extract the participants in a particular session
	 * 
	 * @param pmaControlURL               URL for PMA.Control
	 * @param pmaControlTrainingSessionID Training session ID
	 * @param pmaCoreSessionID            PMA.core session ID
	 * @return Map of participants in a particular session
	 */
	public static Map<String, JSONObject> getTrainingSessionParticipants(String pmaControlURL,
			Integer pmaControlTrainingSessionID, String pmaCoreSessionID) {
		String url = PMA.join(pmaControlURL, "api/Sessions/" + pmaControlTrainingSessionID + "/Participants?sessionID="
				+ PMA.pmaQ(pmaCoreSessionID));
		try {
			String jsonString = PMA.httpGet(url, "application/json");
			JSONArray sessionParticipants = PMA.getJSONArrayResponse(jsonString);
			Map<String, JSONObject> participants = new HashMap<>();
			for (int i = 0; i < sessionParticipants.length(); i++) {
				JSONObject sessionParticipant = sessionParticipants.optJSONObject(i);
				participants.put(sessionParticipant.optString("User"), sessionParticipant);
			}
			return participants;
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
	 * This method is used to check if a specific user participates in a specific
	 * session
	 * 
	 * @param pmaControlURL               URL for PMA.Control
	 * @param participantUsername         PMA.core username
	 * @param pmaControlTrainingSessionID Training session ID
	 * @param pmaCoreSessionID            PMA.core session ID
	 * @return True if a specific user participates in a specific session, false
	 *         otherwise.
	 */
	public static Boolean isParticipantInTrainingSession(String pmaControlURL, String participantUsername,
			Integer pmaControlTrainingSessionID, String pmaCoreSessionID) {
		Map<String, JSONObject> allParticipants = getTrainingSessionParticipants(pmaControlURL,
				pmaControlTrainingSessionID, pmaCoreSessionID);
		for (Entry<String, JSONObject> entry : allParticipants.entrySet()) {
			if (entry.getKey().toLowerCase().equals(participantUsername.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * This method is used to construct the session URL
	 * 
	 * @param pmaControlCase Case name
	 * @throws Exception
	 */

	/**
	 * 
	 * @param pmaControlURL               URL for PMA.Control
	 * @param participantSessionID        The participant's session ID to allow for
	 *                                    eventual impersonation
	 * @param participantUsername         participant username
	 * @param pmaControlTrainingSessionID Training session ID
	 * @param pmaControlCaseCollectionID  Case collection ID
	 * @param pmaCoreSessionID            PMA.core session ID, the administrative
	 *                                    session ID to verify that the participant
	 *                                    is registered for the training session
	 * @return Session's URL
	 * @throws Exception If participantUsername isn't registred for this session
	 */
	@SuppressWarnings("unchecked")
	public static String getTrainingSessionURL(String pmaControlURL, String participantSessionID,
			String participantUsername, Integer pmaControlTrainingSessionID, Integer pmaControlCaseCollectionID,
			String pmaCoreSessionID) throws Exception {
		if (isParticipantInTrainingSession(pmaControlURL, participantUsername, pmaControlTrainingSessionID,
				pmaCoreSessionID)) {
			for (Entry<Integer, Map<String, String>> entry : ((Map<Integer, Map<String, String>>) getTrainingSession(
					pmaControlURL, pmaControlTrainingSessionID, pmaCoreSessionID).get("CaseCollections")).entrySet()) {
				if (entry.getKey().equals(pmaControlCaseCollectionID)) {
					return entry.getValue().get("Url").toString() + "?sessionID=" + participantSessionID;
				}
			}
		} else {
			throw new Exception("Participant " + participantUsername + " is not registered for this session");
		}
		return null;
	}

	/**
	 * This method is used to get all participants registered across all sessions
	 * 
	 * @param pmaControlURL    PMA.control URL
	 * @param pmaCoreSessionID PMA.core session ID
	 * @return Map of all participants registered across all sessions
	 */
	public static Map<String, Map<Integer, String>> getAllParticipants(String pmaControlURL, String pmaCoreSessionID) {
		JSONArray fullTrainingSessions = pmaGetTrainingSessions(pmaControlURL, pmaCoreSessionID);
		Map<String, Map<Integer, String>> userMap = new HashMap<>();
		for (int i = 0; i < fullTrainingSessions.length(); i++) {
			JSONObject session = fullTrainingSessions.optJSONObject(i);
			Map<String, Object> sMap = formatTrainingSessionProperly(session);
			JSONArray participants = session.optJSONArray("Participants");
			for (int j = 0; j < participants.length(); j++) {
				String participant = participants.optString(j);
				if (!userMap.containsKey(participant)) {
					userMap.put(participant, new HashMap<Integer, String>());
				}
				((Map<Integer, String>) userMap.get(participant)).put((int) sMap.get("Id"),
						sMap.get("Title").toString());
			}
		}
		return userMap;
	}

	/**
	 * This method is used to register a participant for a session, assign a
	 * specific role
	 * 
	 * @param pmaControlURL               PMA.control URL
	 * @param participantUsername         PMA.core username
	 * @param pmaControlTrainingSessionID Training session ID
	 * @param pmaControlRole              Role
	 * @param pmaCoreSessionID            PMA.core session ID
	 * @return URL connection output in JSON format
	 */
//	 * @throws Exception If user is ALREADY registered in the provided PMA.control
//	 *                   training session	
	public static String registerParticipantForTrainingSession(String pmaControlURL, String participantUsername,
			Integer pmaControlTrainingSessionID, PmaTrainingSessionRole pmaControlRole, String pmaCoreSessionID) {
//			throws Exception {
//		if (isParticipantInTrainingSession(pmaControlURL, participantUsername, pmaControlSessionID, pmaCoreSessionID)) {
//			throw new Exception("PMA.core user " + participantUsername
//					+ " is ALREADY registered in PMA.control training session " + pmaControlSessionID);
//		}
		try {
			String url = PMA.join(pmaControlURL, "api/Sessions/") + pmaControlTrainingSessionID.toString()
					+ "/AddParticipant?SessionID=" + pmaCoreSessionID;
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
			// default interaction mode = Locked
			String data = "{ \"UserName\": \"" + participantUsername + "\", \"Role\": \"" + pmaControlRole + "\" }";
			// + ", \"InteractionMode\": \"" +
			// String.valueOf(pmacontrolInteractionMode.ordinal() + 1) + "\" }";
			OutputStream os = con.getOutputStream();
			os.write(data.getBytes("UTF-8"));
			os.close();
			if (PMA.debug) {
				System.out.println("Posting to " + url);
				System.out.println("with payload " + data);
			}
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			PMA.clearURLCache();
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
	 * This method is used to register a participant for all sessions in a given
	 * project, assigning a specific role
	 * 
	 * @param pmaControlURL             PMA.control URL
	 * @param participantUsername       PMA.core username
	 * @param pmaControlProjectID       Project's ID
	 * @param pmaControlRole            Role
	 * @param pmaCoreSessionID          PMA.core session ID
	 * @param pmaControlInteractionMode The interaction mode to use
	 * @return List of all sessions in a project that the participant was registered
	 *         to
	 */
	public static String registerParticipantsForProject(String pmaControlURL, String participantUsername,
			Integer pmaControlProjectID, PmaTrainingSessionRole pmaControlRole, String pmaCoreSessionID,
			Integer pmaControlInteractionMode) {

		try {
			String url = PMA.join(pmaControlURL, "api/Projects/") + pmaControlProjectID + "/AddParticipant?SessionID="
					+ pmaCoreSessionID;
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
			// default interaction mode = Locked
			String data = "{ \"UserName\": \"" + participantUsername + "\", \"Role\": \"" + pmaControlRole
					+ "\", \"InteractionMode\": \"" + pmaControlInteractionMode + "\" }";
			OutputStream os = con.getOutputStream();
			os.write(data.getBytes("UTF-8"));
			os.close();
			if (PMA.debug) {
				System.out.println("Posting to " + url);
				System.out.println("with payload " + data);
			}
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			PMA.clearURLCache();
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
	 * This method is a helper to obtain internal technical keys that link training
	 * sessions with case collections
	 * 
	 * @param pmaControlURL               PMA.control URL
	 * @param pmaControlTrainingSessionID Training session ID
	 * @param pmaControlCaseCollectionID  Case collection ID
	 * @param pmaCoreSessionID            PMA.core session ID
	 * @return Identifier caseCollectionTrainingSessionID
	 */
	public static Integer getCaseCollectionTrainingSessionID(String pmaControlURL, Integer pmaControlTrainingSessionID,
			Integer pmaControlCaseCollectionID, String pmaCoreSessionID) {
		JSONArray fullTrainingSessions = pmaGetTrainingSessions(pmaControlURL, pmaCoreSessionID);
		for (int i = 0; i < fullTrainingSessions.length(); i++) {
			JSONObject sess = fullTrainingSessions.optJSONObject(i);
			if (sess.optInt("Id") == pmaControlTrainingSessionID) {
				JSONArray sessCaseCollections = sess.optJSONArray("CaseCollections");
				for (int j = 0; j < sessCaseCollections.length(); j++) {
					JSONObject coll = sessCaseCollections.optJSONObject(j);
					if (coll.optInt("CaseCollectionId") == pmaControlCaseCollectionID) {
						return coll.optInt("Id");
					}
				}
			}
		}
		return null;
	}

//	/**
//	 * This method is used to a ssign an interaction mode to a particpant for a
//	 * given Case Collection within a training session
//	 * 
//	 * @param pmaControlURL               PMA.control URL
//	 * @param participantUsername         PMA.core username
//	 * @param pmaControlTrainingSessionID Training session ID
//	 * @param pmaControlCaseCollectionID  Case collection ID
//	 * @param pmaControlInteractionMode   Interaction mode
//	 * @param pmaCoreSessionID            PMA.core session ID
//	 * @return URL connection output in JSON format
//	 * @throws Exception If user is NOT registered in the provided PMA.control
//	 *                   training session
//	 */
//	public static String setParticipantInteractionMode(String pmaControlURL, String participantUsername,
//			Integer pmaControlTrainingSessionID, Integer pmaControlCaseCollectionID,
//			PmaInteractionMode pmaControlInteractionMode, String pmaCoreSessionID) throws Exception {
//
//		if (!isParticipantInTrainingSession(pmaControlURL, participantUsername, pmaControlTrainingSessionID,
//				pmaCoreSessionID)) {
//			throw new Exception("PMA.core user " + participantUsername
//					+ " is NOT registered in PMA.control training session " + pmaControlTrainingSessionID);
//		}
//		try {
//			String url = PMA.join(pmaControlURL, "api/Sessions/") + pmaControlTrainingSessionID
//					+ "/InteractionMode?SessionID=" + pmaCoreSessionID;
//			URL urlResource = new URL(url);
//			HttpURLConnection con;
//			if (url.startsWith("https")) {
//				con = (HttpsURLConnection) urlResource.openConnection();
//			} else {
//				con = (HttpURLConnection) urlResource.openConnection();
//			}
//			con.setRequestMethod("POST");
//			con.setRequestProperty("Content-Type", "application/json");
//			con.setUseCaches(false);
//			con.setDoOutput(true);
//			String data = "{ \"UserName\": \"" + participantUsername + "\", " + " \"CaseCollectionId\": \""
//					+ pmaControlCaseCollectionID + "\", " + "\"InteractionMode\": \"" + pmaControlInteractionMode
//					+ "\" }"; // default interaction mode = Locked
//			// + ", \"InteractionMode\": \"" +
//			// String.valueOf(pmacontrolInteractionMode.ordinal() + 1) + "\" }";
//			OutputStream os = con.getOutputStream();
//			os.write(data.getBytes("UTF-8"));
//			os.close();
//			if (PMA.debug) {
//				System.out.println("Posting to " + url);
//				System.out.println("with payload " + data);
//			}
//			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
//			PMA.clearURLCache();
//			return jsonString;
//		} catch (Exception e) {
//			e.printStackTrace();
//			if (PMA.logger != null) {
//				StringWriter sw = new StringWriter();
//				e.printStackTrace(new PrintWriter(sw));
//				PMA.logger.severe(sw.toString());
//			}
//			return null;
//		}
//	}

	/**
	 * This method is an overload of previous one. We created to add the possibility
	 * to assign an interaction mode outside the defined enum values
	 * 
	 * @param pmaControlURL               PMA.control URL
	 * @param participantUsername         PMA.core username
	 * @param pmaControlTrainingSessionID Training session ID
	 * @param pmaControlCaseCollectionID  Case collection ID
	 * @param pmaControlInteractionMode   Interaction mode
	 * @param pmaCoreSessionID            PMA.core session ID
	 * @return URL connection output in JSON format
	 * @throws Exception If user is NOT registered in the provided PMA.control
	 *                   training session
	 */
	public static String setParticipantInteractionMode(String pmaControlURL, String participantUsername,
			Integer pmaControlTrainingSessionID, Integer pmaControlCaseCollectionID, Integer pmaControlInteractionMode,
			String pmaCoreSessionID) throws Exception {

		if (!isParticipantInTrainingSession(pmaControlURL, participantUsername, pmaControlTrainingSessionID,
				pmaCoreSessionID)) {
			throw new Exception("PMA.core user " + participantUsername
					+ " is NOT registered in PMA.control training session " + pmaControlTrainingSessionID);
		}
		try {
			String url = PMA.join(pmaControlURL, "api/Sessions/") + pmaControlTrainingSessionID
					+ "/InteractionMode?SessionID=" + pmaCoreSessionID;
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
			String data = "{ \"UserName\": \"" + participantUsername + "\", " + " \"CaseCollectionId\": \""
					+ pmaControlCaseCollectionID + "\", " + "\"InteractionMode\": \"" + pmaControlInteractionMode
					+ "\" }"; // default interaction mode = Locked
			// + ", \"InteractionMode\": \"" +
			// String.valueOf(pmacontrolInteractionMode.ordinal() + 1) + "\" }";
			OutputStream os = con.getOutputStream();
			os.write(data.getBytes("UTF-8"));
			os.close();
			if (PMA.debug) {
				System.out.println("Posting to " + url);
				System.out.println("with payload " + data);
			}
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			PMA.clearURLCache();
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
	 * This method is used to retrieve sessions (possibly filtered by project ID),
	 * titles only
	 * 
	 * @param pmaControlURL       URL for PMA.Control
	 * @param pmaControlProjectID Project's ID
	 * @param pmaCoreSessionID    PMA.core session ID
	 * @return List of session titles
	 */
	public static List<String> getTrainingSessionTitles(String pmaControlURL, Integer pmaControlProjectID,
			String pmaCoreSessionID) {
		try {
			return new ArrayList<String>(
					getTrainingSessionTitlesDict(pmaControlURL, pmaControlProjectID, pmaCoreSessionID).values());
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
	 * This method is used to retrieve (training) sessions (possibly filtered by
	 * project ID), returns a map of session IDs and titles
	 * 
	 * @param pmaControlURL       URL for PMA.Control
	 * @param pmaControlProjectID Project's ID
	 * @param pmaCoreSessionID    PMA.core session ID
	 * @return Map of session IDs and titles
	 */
	public static Map<Integer, String> getTrainingSessionTitlesDict(String pmaControlURL, Integer pmaControlProjectID,
			String pmaCoreSessionID) {
		Map<Integer, String> map = new HashMap<>();
		try {
			JSONArray all = pmaGetTrainingSessions(pmaControlURL, pmaCoreSessionID);
			for (int i = 0; i < all.length(); i++) {
				JSONObject session = all.optJSONObject(i);
				if (pmaControlProjectID == null) {
					map.put(session.optInt("Id"), session.optString("Title"));
				} else if (pmaControlProjectID == session.optInt("ProjectId")) {
					map.put(session.optInt("Id"), session.optString("Title"));
				}
			}
			return map;
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
	 * This method is used to retrieve (training) sessions (possibly filtered by
	 * project ID)
	 * 
	 * @param pmaControlURL       URL for PMA.Control
	 * @param pmaControlProjectID Project's ID
	 * @param pmaCoreSessionID    PMA.core session ID
	 * @return Map of session IDs and titles
	 */
	public static Map<Integer, Map<String, Object>> getTrainingSessions(String pmaControlURL,
			Integer pmaControlProjectID, String pmaCoreSessionID) {
		Map<Integer, Map<String, Object>> map = new HashMap<>();
		JSONArray all = pmaGetTrainingSessions(pmaControlURL, pmaCoreSessionID);
		for (int i = 0; i < all.length(); i++) {
			JSONObject sess = all.optJSONObject(i);
			if (pmaControlProjectID == null) {
				map.put(sess.optInt("Id"), formatTrainingSessionProperly(sess));
			} else if (pmaControlProjectID == sess.optInt("ProjectId")) {
				map.put(sess.optInt("Id"), formatTrainingSessionProperly(sess));
			}
		}
		return map;
	}

	/**
	 * This method is used to retrieve (training) sessions (possibly filtered by
	 * project ID)
	 * 
	 * @param pmaControlURL       URL for PMA.Control
	 * @param pmaControlProjectID Project's ID
	 * @param pmaCoreSessionID    PMA.core session ID
	 * @return Map of session IDs and titles
	 */
	public static Map<Integer, Map<String, Object>> getTrainingSessionsViaProject(String pmaControlURL,
			Integer pmaControlProjectID, String pmaCoreSessionID) {
		Map<Integer, Map<String, Object>> map = new HashMap<>();
		JSONArray all = pmaGetTrainingSessionsViaProject(pmaControlURL, pmaCoreSessionID, pmaControlProjectID);
		for (int i = 0; i < all.length(); i++) {
			JSONObject sess = all.optJSONObject(i);
			if (pmaControlProjectID == null) {
				map.put(sess.optInt("Id"), formatTrainingSessionProperly(sess));
			} else if (pmaControlProjectID == sess.optInt("ProjectId")) {
				map.put(sess.optInt("Id"), formatTrainingSessionProperly(sess));
			}
		}
		return map;
	}

	/**
	 * This method is used to get the first (training) session with ID =
	 * pmaControlTrainingSessionID
	 * 
	 * @param pmaControlURL               URL for PMA.Control
	 * @param pmaControlTrainingSessionID Training session's ID
	 * @param pmaCoreSessionID            PMA.core session ID
	 * @return First training session with ID = pmacontrolTrainingSessionID
	 */
	public static Map<String, Object> getTrainingSession(String pmaControlURL, Integer pmaControlTrainingSessionID,
			String pmaCoreSessionID) {
		JSONArray allTrainingSessions = pmaGetTrainingSessions(pmaControlURL, pmaCoreSessionID);
		for (int i = 0; i < allTrainingSessions.length(); i++) {
			JSONObject el = allTrainingSessions.optJSONObject(i);
			if (el.optInt("Id") == pmaControlTrainingSessionID) {
				// summarize training session-related information so that it makes sense
				return formatTrainingSessionProperly(el);
			}
		}
		return null;
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
	public static Map<String, Object> searchTrainingSession(String pmaControlURL, String keyword,
			String pmaCoreSessionID) {
		JSONArray all = pmaGetTrainingSessions(pmaControlURL, pmaCoreSessionID);

		for (int i = 0; i < all.length(); i++) {
			JSONObject el = all.optJSONObject(i);
			if (el.optString("Title").toLowerCase().contains(keyword.toLowerCase())) {
				return formatTrainingSessionProperly(el);
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
	private static JSONArray pmaGetCaseCollections(String pmaControlURL, String pmaCoreSessionID, String... varargs) {
		// setting the default value when argument's value is omitted
		String project = ((varargs.length > 0) && (varargs[0] != null)) ? varargs[0] : "";
		String url = PMA.join(pmaControlURL, "api/CaseCollections?sessionID=" + PMA.pmaQ(pmaCoreSessionID)
				+ ((project.length() > 0) ? ("&project=" + PMA.pmaQ(project)) : ""));
		try {
			String jsonString = PMA.httpGet(url, "application/json");
			JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
			return jsonResponse;
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
	 * This method is used to retrieve case collection details that belong to a
	 * specific project
	 * 
	 * @param pmaControlURL       URL for PMA.Control
	 * @param pmaControlProjectID Project's ID
	 * @param pmaCoreSessionID    PMA.core session ID
	 * @return Map of case collection details
	 */
	public static Map<Integer, JSONObject> getCaseCollections(String pmaControlURL, Integer pmaControlProjectID,
			String pmaCoreSessionID) {
		Map<Integer, JSONObject> colls = new HashMap<>();
		JSONArray allColls = pmaGetCaseCollections(pmaControlURL, pmaCoreSessionID);
		for (int i = 0; i < allColls.length(); i++) {
			JSONObject coll = allColls.optJSONObject(i);
			if ((pmaControlProjectID == coll.optInt("ProjectId")) && !colls.keySet().contains(coll.optInt("Id"))) {
				colls.put(coll.optInt("Id"), coll);
			}
		}
		return colls;
	}

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
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to get a case collection by its title
	 * 
	 * @param pmaControlURL       URL for PMA.Control
	 * @param pmaCoreSessionID    PMA.core session ID
	 * @param caseCollectionTitle The case collection's title
	 * @return The case collection in json object format
	 */
	public static JSONObject getCaseCollectionByTitle(String pmaControlURL, String pmaCoreSessionID,
			String caseCollectionTitle) {
		JSONArray caseCollections = pmaGetCaseCollections(pmaControlURL, pmaCoreSessionID);
		if (caseCollections == null) {
			return null;
		} else {
			for (int i = 0; i < caseCollections.length(); i++) {
				if (caseCollections.optJSONObject(i).get("Title").toString().toLowerCase()
						.equals(caseCollectionTitle.toLowerCase())) {
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
			JSONArray allColletions = pmaGetCaseCollections(pmaControlURL, pmaCoreSessionID);
			for (int i = 0; i < allColletions.length(); i++) {
				JSONObject collection = allColletions.optJSONObject(i);
				if (pmaControlProjectID == null) {
					map.put(collection.optInt("Id"), collection.optString("Title"));
				} else if (pmaControlProjectID == collection.optInt("ProjectId")) {
					map.put(collection.optInt("Id"), collection.optString("Title"));
				}
			}
			return map;
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
	 * This method is used to get a case collection details (by ID)
	 * 
	 * @param pmaControlURL              URL for PMA.Control
	 * @param pmaControlCaseCollectionID Case collection's ID
	 * @param pmaCoreSessionID           PMA.core session ID
	 * @return The case collection in json object format
	 */
	public static JSONObject getCaseCollection(String pmaControlURL, Integer pmaControlCaseCollectionID,
			String pmaCoreSessionID) {
		JSONArray allCollections = pmaGetCaseCollections(pmaControlURL, pmaCoreSessionID);
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
	 * This method is used to retrieve cases for a specific collection
	 * 
	 * @param pmaControlURL              PMA.control URL
	 * @param pmaControlCaseCollectionID Case collection ID
	 * @param pmaCoreSessionID           PMA.core session ID
	 * @return List of cases for a specific collection
	 */
	public static JSONArray getCaseForCaseCollection(String pmaControlURL, Integer pmaControlCaseCollectionID,
			String pmaCoreSessionID) {
		return getCaseCollection(pmaControlURL, pmaControlCaseCollectionID, pmaCoreSessionID).optJSONArray("Cases");
	}

	/**
	 * This method is used to return the first collection that has keyword as part
	 * of its string; search is case insensitive
	 *
	 * @param pmaControlURL    URL for PMA.Control
	 * @param keyword          Keyword to seach collections against
	 * @param pmaCoreSessionID PMA.core session ID
	 * @return The first collection that matches the search criteria
	 */
	public static JSONObject searchCaseCollection(String pmaControlURL, String keyword, String pmaCoreSessionID) {
		JSONArray allCollections = pmaGetCaseCollections(pmaControlURL, pmaCoreSessionID);
		for (int i = 0; i < allCollections.length(); i++) {
			JSONObject collection = allCollections.optJSONObject(i);
			if (collection.getString("Title").toLowerCase().contains(keyword.toLowerCase())) {
				// summary session-related information so that it makes sense
				return collection;
			}
		}
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
	private static List<Map<Integer, String>> formatProjectEmbeddedTrainingSessionsProperly(
			JSONArray originalProjectSessions) {
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
	private static JSONArray pmaGetProjects(String pmaControlURL, String pmaCoreSessionID) {
		String url = PMA.join(pmaControlURL, "api/Projects?sessionID=" + PMA.pmaQ(pmaCoreSessionID));
		try {
			String jsonString = PMA.httpGet(url, "application/json");
			JSONArray jsonResponse = PMA.getJSONArrayResponse(jsonString);
			return jsonResponse;
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
	 * This method is used to retrieve project details for all projects
	 * 
	 * @param pmaControlURL    URL for PMA.Control
	 * @param pmaCoreSessionID PMA.core session ID
	 * @return Map of project details for all projects
	 */
	@SuppressWarnings("unchecked")
	public static Map<Integer, JSONObject> getProjects(String pmaControlURL, String pmaCoreSessionID) {
		JSONArray allProjects = pmaGetProjects(pmaControlURL, pmaCoreSessionID);
		Map<Integer, JSONObject> projects = new HashMap<>();
		for (int i = 0; i < allProjects.length(); i++) {
			JSONObject prj = allProjects.optJSONObject(i);
			// summary session-related information so that it makes sense
			prj.put("Sessions", formatProjectEmbeddedTrainingSessionsProperly(prj.optJSONArray("Sessions")));

			// now integrate case collection information
			JSONArray colls = pmaGetCaseCollections(pmaControlURL, pmaCoreSessionID);
			prj.put("CaseCollections", new HashMap<Integer, String>());
			for (int j = 0; j < colls.length(); j++) {
				JSONObject col = colls.optJSONObject(j);
				if (col.getInt("ProjectId") == prj.getInt("Id")) {
					((Map<Integer, String>) prj.get("CaseCollections")).put(col.optInt("Id"), col.optString("Title"));
				}
			}
			projects.put(prj.optInt("Id"), prj);
		}
		return projects;
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
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
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
		JSONArray allProjects = pmaGetProjects(pmaControlURL, pmaCoreSessionID);
		try {
			for (int i = 0; i < allProjects.length(); i++) {
				map.put(allProjects.optJSONObject(i).getInt("Id"), allProjects.optJSONObject(i).getString("Title"));
			}
			return map;
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
			JSONArray allProjects = pmaGetProjects(pmaControlURL, pmaCoreSessionID);
			for (int i = 0; i < allProjects.length(); i++) {
				JSONObject prj = allProjects.optJSONObject(i);
				if (prj.optInt("Id") == pmaControlProjectID) {
					// summary session-related information so that it makes sense
					Map<String, Object> project = new ObjectMapper()
							.readerFor(new TypeReference<Map<String, Object>>() {
							}).readValue(prj.toString());
					project.put("Sessions",
							formatProjectEmbeddedTrainingSessionsProperly(prj.optJSONArray("Sessions")));
					// now integrate case collection information
					// we get the case collections belonging to the project (via the title)
					JSONArray collections = pmaGetCaseCollections(pmaControlURL, pmaCoreSessionID,
							prj.getString("Title"));
					project.put("CaseCollections", new HashMap<Integer, String>());
					for (int j = 0; j < collections.length(); j++) {
						JSONObject collection = collections.optJSONObject(j);
						if (collection.optInt("ProjectId") == prj.optInt("Id")) {
							((Map<Integer, String>) project.get("CaseCollections")).put(collection.getInt("Id"),
									collection.optString("Title"));
						}
					}
					return project;
				}
			}
			// Project ID not found
			return null;
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
	 * This method is used to retrieve project based on the case ID
	 * 
	 * @param pmaControlURL    URL for PMA.Control
	 * @param pmacontrolCaseID Case's ID
	 * @param pmaCoreSessionID PMA.core session ID
	 * @return Map of the project details
	 */
	public static Map<String, Object> getProjectByCaseID(String pmaControlURL, Integer pmacontrolCaseID,
			String pmaCoreSessionID) {
		JSONArray allCollections = pmaGetCaseCollections(pmaControlURL, pmaCoreSessionID);
		for (int i = 0; i < allCollections.length(); i++) {
			JSONObject collection = allCollections.optJSONObject(i);
			JSONArray cases = collection.optJSONArray("Cases");
			for (int j = 0; j < cases.length(); j++) {
				JSONObject currentCase = cases.optJSONObject(j);
				if (currentCase.optInt("Id") == pmacontrolCaseID) {
					return getProject(pmaControlURL, collection.optInt("ProjectId"), pmaCoreSessionID);
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
		JSONArray allCollections = pmaGetCaseCollections(pmaControlURL, pmaCoreSessionID);
		for (int i = 0; i < allCollections.length(); i++) {
			JSONObject collection = allCollections.optJSONObject(i);
			if (collection.optInt("Id") == pmacontrolCaseCollectionID) {
				return getProject(pmaControlURL, collection.optInt("ProjectId"), pmaCoreSessionID);
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
			if (entry.getValue().toLowerCase().contains(keyword.toLowerCase())) {
				lstProjects.add(getProject(pmaControlURL, entry.getKey(), pmaCoreSessionID));
			}
		}
		if (lstProjects.size() > 0) {
			return lstProjects;
		} else {
			return null;
		}
	}

	/**
	 * This method is used to a ssign an interaction mode to a particpant for an
	 * array of Case Collections within a training session
	 * 
	 * @param pmaControlURL               PMA.control URL
	 * @param participantUsername         PMA.core username
	 * @param pmaControlTrainingSessionID Training session ID
	 * @param pmaControlCaseCollectionIDs Array of Case collection IDs
	 * @param pmaControlInteractionMode   Interaction mode
	 * @param pmaCoreSessionID            PMA.core session ID
	 * @return URL connection output in JSON format
	 * @throws Exception If user is NOT registered in the provided PMA.control
	 *                   training session
	 */
	public static String setParticipantInteractionModeCaseCollection(String pmaControlURL, String participantUsername,
			Integer pmaControlTrainingSessionID, int[] pmaControlCaseCollectionIDs, Integer pmaControlInteractionMode,
			String pmaCoreSessionID) throws Exception {
		try {
			String url = PMA.join(pmaControlURL, "api/Sessions/") + pmaControlTrainingSessionID
					+ "/InteractionModeCaseCollections?SessionID=" + pmaCoreSessionID;
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
			String data = "{ \"UserName\": \"" + participantUsername + "\", " + " \"CaseCollectionIds\": ["
					+ StringUtils.join(ArrayUtils.toObject(pmaControlCaseCollectionIDs), ", ") + "], "
					+ "\"InteractionMode\": \"" + pmaControlInteractionMode + "\" }"; // default interaction mode =
																						// Locked
			// + ", \"InteractionMode\": \"" +
			// String.valueOf(pmacontrolInteractionMode.ordinal() + 1) + "\" }";
			OutputStream os = con.getOutputStream();
			os.write(data.getBytes("UTF-8"));
			os.close();
			if (PMA.debug) {
				System.out.println("Posting to " + url);
				System.out.println("with payload " + data);
			}
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			PMA.clearURLCache();
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
	 * This method is used to assign an interaction mode to a particpant for
	 * training sessions in batch mode
	 * 
	 * @param pmaControlURL               PMA.control URL
	 * @param participantUsername         PMA.core username
	 * @param trainingSessions			  Training sessions
	 * @param pmaControlInteractionMode   Interaction mode
	 * @param pmaCoreSessionID            PMA.core session ID
	 * @return URL connection output in JSON format
	 * @throws Exception If user is NOT registered in the provided PMA.control
	 *                   training session
	 */
	public static String setParticipantInteractionModeInBatch(String pmaControlURL, String participantUsername,
			List<Map<String, Object>> trainingSessions, Integer pmaControlInteractionMode, String pmaCoreSessionID)
			throws Exception {
		try {
			String url = PMA.join(pmaControlURL, "api/Sessions/InteractionMode?sessionID=") + pmaCoreSessionID;
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
			String jsonContentForTrainingSessions = "";
			for (Map<String, Object> trainingSession : trainingSessions) {
				jsonContentForTrainingSessions += "{\"SessionId\":\"" + trainingSession.get("Id")
						+ "\", \"CaseCollectionIds\":["
						+ StringUtils.join(ArrayUtils.toObject((int[]) trainingSession.get("CaseCollections")), ", ")
						+ "]},";
			}
			String data = "{\"UserName\":\"" + participantUsername + "\", " + "\"Sessions\":["
					+ jsonContentForTrainingSessions + "]," + "\"InteractionMode\":\"" + pmaControlInteractionMode
					+ "\"}"; // default interaction mode = // Locked
			OutputStream os = con.getOutputStream();
			os.write(data.getBytes("UTF-8"));
			os.close();
			if (PMA.debug) {
				System.out.println("Posting to " + url);
				System.out.println("with payload " + data);
			}
			String jsonString = PMA.getJSONAsStringBuffer(con).toString();
			PMA.clearURLCache();
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
