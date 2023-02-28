package com.pathomation;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;

public class PmaClient {

    private static String pmaCoreLiteSessionID = "pma.core.lite";

//    public static boolean selectFileFromStart() {
//        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
//        // It's mandatory to have old sessions deleted
//        // to avoid conflicts between PMA.Start & PMA.core sessions
//        if (Core.sessions().size() > 0) {
//            Core.disconnect();
//        }
//        String pmaCoreLiteURL = "http://127.0.0.1:54001/";
//        boolean result = Core.isLite();
//        if (!result) {
//            FrameManager.showError("HistoQu Connection error", "PMA.Start not found. If you are running PMA.Start, make sure you have the latest version.");
//            return false;
//        }
//
//        return doNextStep(pmaCoreLiteSessionID, pmaCoreLiteURL, null, PmaServerTypes.Start);
//    }
//
//    public static boolean selectFileFromCore(String url, String username, String password) {
//        // It's mandatory to have old sessions deleted
//        // to avoid conflicts between PMA.Start & PMA.core sessions
//        if (Core.sessions().size() > 0) {
//            Core.disconnect();
//        }
//
//
//
//        String sessionID = Core.connect(url, username, password);
//        if (sessionID == null) {
//            return false;
//        }
//
//        try {
//            Properties prop = loadProps();
//            prop.put("com.pathomation.url", encode(url));
//            prop.put("com.pathomation.username", encode(username));
//            prop.put("com.pathomation.password", encode(password));
//            storeProps(prop);
//        } catch (Exception ignored) {
//        }
//
//        return doNextStep(sessionID, url, null, PmaServerTypes.Core);
//    }
//
    public static CloudServerData selectFileFromCloud(String username, String password) {
        CloudServerData result = connectToCloud(username, password);
        if (result == null || !result.isSuccess()) {
            return result;
        }

        String url = result.getServerUrl();
        String sessionId = result.getSessionId();
        Core.addServer(sessionId, url, result.getResponseSize());
        if (sessionId == null) {
            return null;
        }

        try {
            Properties prop = loadProps();
            prop.put("com.pathomation.cloudUsername", encode(username));
            prop.put("com.pathomation.cloudPassword", encode(password));
            storeProps(prop);
        } catch (Exception ignored) {
        }
//        doNextStep(sessionId, url, result.getFolder(),PmaServerTypes.Cloud);
        return result;
    }

    final static String key = "Sl9zq2^+g;LoB`aGh{3.H R=-pe'AD&@r60i%:?jOWtF/ZXs\"v*N[#w|xJIu4fbK)dMET><,Qc\\C1ymU8]kYV}(!7P$n5_";

    public static String encode(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        String encoded = Base64.getEncoder().encodeToString(s.getBytes());
        for (char c : encoded.toCharArray())
            sb.append(key.charAt((int) c - 32));

        return sb.toString();
    }

    public static String decode(String s) {
        StringBuilder sb = new StringBuilder(s.length());

        for (char c : s.toCharArray())
            sb.append((char) (key.indexOf(c) + 32));

        return new String(Base64.getDecoder().decode(sb.toString()));
    }


    public static Properties loadProps() {
        try {
            Properties prop = new Properties();
            String currentDir = System.getProperty("user.dir");
            FileInputStream inStream = new FileInputStream(new File(currentDir, "properties.prop"));
            prop.load(inStream);

            return prop;
        } catch (Exception ignored) {
            return new Properties();
        }
    }

    public static void storeProps(Properties prop) {
        try {
            String currentDir = System.getProperty("user.dir");
            FileOutputStream outStream = new FileOutputStream(new File(currentDir, "properties.prop"));
            prop.store(outStream, "Pathomation Plugin default values");
        } catch (Exception ignored) {
        }
    }

//    public static boolean doNextStep(String sessionId, String serverUrl, String startDir, PmaServerTypes serverType) {
//        ViewState state = new ViewState();
//        state.serverUrl = serverUrl;
//        state.sessionId = sessionId;
//        state.startDir = startDir;
//        state.serverType = serverType;
//        FrameManager.showTreeView(state);
//
//        return true;
//    }

//    public static void openRoiSelector(ViewState state, String path) {
//        String escapedPath = path.replace("\\", "/");
//
//        ImageInfo slideInfo = getSlideInfo(state.sessionId, state.serverType == PmaServerTypes.Start, escapedPath);
//        if (slideInfo == null) {
//            return;
//        }
//        state.imageInfo = slideInfo;
//
//        FrameManager.showRoiSelectorView(state);
//    }
//
//    private static ImageInfo getSlideInfo(String sessionId, boolean isLite, String escapedPath) {
//        Map<String, Object> slideInfo = Core.getSlideInfo(escapedPath, isLite ? null : sessionId);
//        if (slideInfo == null) {
//            return null;
//        }
//
//        return new ImageInfo(slideInfo);
//    }
//
//    public static void continueAfterRoi(ViewState state) {
//        FrameManager.showCropSettingsView(state);
//    }
//
//    private static String joinToStr(Integer[] arr, String separator) {
//        if (null == arr || 0 == arr.length) return "";
//
//        StringBuilder sb = new StringBuilder(256);
//        sb.append(arr[0]);
//
//        for (int i = 1; i < arr.length; i++) sb.append(separator).append(arr[i]);
//
//        return sb.toString();
//    }
//
//    public static void continueAfterScale(QuPathGUI qupath, RoiRectangle[] rectangles, Integer[] channels, boolean drawScaleBar, double finalScale, PmaServerTypes serverType, String path, String sessionId, ImageInfo imageInfo, boolean openInDifferentWindows, int layer, int timeFrame) {
//        try {
//            openInQupath(rectangles, imageInfo.getBaseUrl(), false, finalScale, serverType, path, sessionId, imageInfo, channels, openInDifferentWindows, layer, timeFrame);
//        } catch (URISyntaxException e) {
//            e.printStackTrace();
//        } catch (UnsupportedEncodingException e) {
//            throw new RuntimeException(e);
//        }
//        FrameManager.hide();
//    }
//
//    private static void initImagePlus(RoiRectangle[] rectangles, boolean drawScaleBar, double finalScale, PmaServerTypes serverType, String path, String sessionId, ImageInfo imageInfo, Integer[] channels, boolean openInDifferentWindows, int layer, int timeFrame) {
//        FrameManager.setVisible(false);
//    }
//
//    public static void openInQupath(RoiRectangle[] rectangles,
//                                    String serverUrl,
//                                    boolean drawScaleBar,
//                                    double finalScale,
//                                    PmaServerTypes serverType,
//                                    String path,
//                                    String sessionId,
//                                    ImageInfo imageInfo,
//                                    Integer[] channels,
//                                    boolean openInDifferentWindows,
//                                    int layer,
//                                    int timeFrame) throws URISyntaxException, UnsupportedEncodingException {
//        ArrayList<String> channelsString = new ArrayList<>();
//        for (int channel : channels) {
//            channelsString.add(String.valueOf(channel));
//        }
//        if (rectangles.length == 1 && (!openInDifferentWindows || channels.length == 1)) {
//            var rect = rectangles[0];
//            var channelsParam = String.join(",", channelsString);
//            String url = getSlideUrl(serverUrl, finalScale, serverType, path, sessionId, layer, timeFrame, rect, channelsParam);
//            FrameManager.hide();
//            FrameManager.getQupath().openImage(url, false, false);
//        } else {
//            if (FrameManager.getQupath().getProject() == null) {
//                FrameManager.showError("HistoQu importing error", "You have to create or open project to import multiple tiles");
//                return;
//            }
//            FrameManager.hide();
//            var urls = new ArrayList<String>();
//            for (var rect : rectangles) {
//                if (openInDifferentWindows) {
//                    for (var channel : channels) {
//                        String url = getSlideUrl(serverUrl, finalScale, serverType, path, sessionId, layer, timeFrame, rect, channel.toString());
//                        urls.add(url);
//                    }
//                } else {
//                    String url = getSlideUrl(serverUrl, finalScale, serverType, path, sessionId, layer, timeFrame, rect, String.join(",", channelsString));
//                    urls.add(url);
//                }
//            }
//            ProjectCommands.promptToImportImages(FrameManager.getQupath(), urls.toArray(new String[0]));
//        }
//    }
//
//    private static String getSlideUrl(String serverUrl, double finalScale, PmaServerTypes serverType, String path, String sessionId, int layer, int timeFrame, RoiRectangle rect, String channelsParam) throws UnsupportedEncodingException {
//        return "http://histoqu.slide/?serverUrl=" + URLEncoder.encode(serverUrl, String.valueOf(StandardCharsets.UTF_8))
//                + "&sessionId=" + URLEncoder.encode(sessionId, String.valueOf(StandardCharsets.UTF_8))
//                + "&path=" + URLEncoder.encode(path, String.valueOf(StandardCharsets.UTF_8))
//                + "&x=" + String.valueOf(rect.x)
//                + "&y=" + String.valueOf(rect.y)
//                + "&width=" + String.valueOf(rect.width)
//                + "&height=" + String.valueOf(rect.height)
//                + "&scale=" + String.valueOf(finalScale)
//                + "&type=" + (serverType == PmaServerTypes.Cloud ? "cloud" : "core")
//                + "&channels=" + channelsParam
//                + "&layer=" + URLEncoder.encode(String.valueOf(layer), String.valueOf(StandardCharsets.UTF_8))
//                + "&timeframe=" + URLEncoder.encode(String.valueOf(timeFrame), String.valueOf(StandardCharsets.UTF_8));
//    }
//
//    public static void openImportedFile(File file) {
//
//        try
//        {
//            JSONObject obj = new JSONObject(FileUtils.readFileToString(file, StandardCharsets.UTF_8.name()));
//            String slide = obj.getString("slide");
//            String serverUrl = obj.getString("serverUrl");
//            JSONArray channelsValue = obj.getJSONArray("channels");
//            ArrayList<Integer> channels = new ArrayList<Integer>();
//            for (int i = 0; i < channelsValue.length(); i++) {
//                channels.add(channelsValue.getInt(i));
//            }
//            boolean openInDifferentWindows = obj.getBoolean("openInDifferentWindows");
//            int width = obj.getInt("width");
//            int height = obj.getInt("height");
//            int x = obj.getInt("x");
//            int y = obj.getInt("y");
//            int layer = obj.getInt("layer");
//            int timeFrame = obj.getInt("timeFrame");
//            String sessionId = obj.getString("sessionId");
//            double scale = obj.getDouble("scale");
//            boolean isCloud = obj.getBoolean("isCloud");
//            boolean isLite = obj.getBoolean("isLite");
//            if (isNullOrEmpty(slide) || isNullOrEmpty(serverUrl) || channels.size() == 0) {
//                FrameManager.showError("HistoQu", "Error during importing slide from PMA.Studio");
//                return;
//            }
//
//            RoiRectangle[] rectangles = new RoiRectangle[] {new RoiRectangle(x, y, width, height)};
//            if (!isLite) {
//                if (Core.ping(serverUrl, sessionId)) {
//                    Core.addServer(sessionId, serverUrl, 0);
//                } else {
//                    FrameManager.showLoginCloudViewRenew(rectangles, scale, isCloud, slide, serverUrl, channels.toArray(new Integer[0]), openInDifferentWindows, layer, timeFrame);
//                    return;
//                }
//
//            }
//            ImageInfo slideInfo = getSlideInfo(sessionId, isLite, slide);
//            PmaServerTypes serverType = isLite ? PmaServerTypes.Start : (isCloud ? PmaServerTypes.Cloud : PmaServerTypes.Cloud);
//            initImagePlus(rectangles, false, scale, serverType, slide, sessionId, slideInfo, channels.toArray(new Integer[0]), openInDifferentWindows, layer, timeFrame);
//
//        } catch (IOException e) {
//            FrameManager.showError("HistoQu", "Error during importing slide from PMA.Studio");
//        } catch (JSONException e) {
//            // ignore
//        }
//    }
//
//    private static boolean isNullOrEmpty(String str) {
//        if (str == null) {
//            return true;
//        }
//
//        return str.trim().equals("");
//    }
//
//    public static boolean continueAfterSessionRenew(String username, String password, RoiRectangle[] rectangles, boolean isCloud, String serverUrl, String path, double scale, Integer[] channels, boolean openInDifferentWindows, int layer, int timeFrame) {
//        if (Core.sessions().size() > 0) {
//            Core.disconnect();
//        }
//
//        String sessionId;
//        if (isCloud) {
//            CloudServerData item = connectToCloud(username, password);
//            if (item == null || !item.isSuccess()) {
//                return false;
//            };
//            sessionId = item.getSessionId();
//        } else {
//            sessionId = Core.connect(serverUrl, username, password);
//            if (sessionId == null) {
//                return false;
//            }
//        }
//        ImageInfo slideInfo = getSlideInfo(sessionId, false, path);
//        if (slideInfo == null) {
//            FrameManager.showError("HistoQu", "Error during importing slide from PMA.Studio");
//            FrameManager.setVisible(false);
//        }
//        try {
//            openInQupath(rectangles, serverUrl, false, scale, isCloud ? PmaServerTypes.Cloud : PmaServerTypes.Core, path, sessionId, slideInfo, channels, openInDifferentWindows, layer, timeFrame);
//        } catch (URISyntaxException e) {
//            e.printStackTrace();
//        } catch (UnsupportedEncodingException e) {
//            throw new RuntimeException(e);
//        }
//        FrameManager.hide();
//
//        return true;
//    }

    private static CloudServerData connectToCloud(String username, String password) {
        if (Core.sessions().size() > 0) {
            Core.disconnect();
        }
        CloudServerData result = Core.connectToCloud(username, password);
        if (result == null) {
            return null;
        }

        Core.addServer(result.getSessionId(), result.getServerUrl(), result.getResponseSize());
        return result;
    }


    public static String postItem(String url, String payload) {
        try {
            URL urlResource = new URL(url);
            URLConnection conn = urlResource.openConnection();
            conn.setDoOutput(true);
            conn.setRequestProperty( "Content-Type", "application/json");
            conn.setRequestProperty( "charset", "utf-8");
            conn.setUseCaches( false );
            try(DataOutputStream wr = new DataOutputStream( conn.getOutputStream())) {
                wr.write(payload.getBytes());
            }

            return getResponseString(conn);
        } catch (Exception e) {
            return null;
        }
    }

    public static String getCloudAuth(String accessToken) {
        try {
            URL urlResource = new URL("https://myapi.pathomation.com/api/v1/authenticate");
            URLConnection conn = urlResource.openConnection();
            conn.setRequestProperty( "Authorization", "Bearer " + accessToken);
            conn.setUseCaches( false );
            return getResponseString(conn);
        } catch (Exception e) {
            return null;
        }
    }

    private static String getResponseString(URLConnection conn) throws IOException {
        Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (int c; (c = in.read()) >= 0; )
            sb.append((char) c);
        return sb.toString();
    }
}
