package com.pathomation;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.pathomation.Core.ping;
import static com.pathomation.Core.pmaUrl;
import static java.lang.System.out;
import static org.junit.Assert.*;

/**
 * Before starting jUnit test fills in your parameters:
 * URL, name, password, and paths to local and Core slides.
 */
public class CoreTest {

    String pathomationURL = "https://www.pathomation.com/";
    static String core3 = "https://devtest.pathomation.com/test/pma.core.3/";
    static String name = "name";
    static String passwordCore = "password";
    public static String coreSessionId = Core.connect(core3, name, passwordCore);
    String pmaCoreLiteURL = Core.getPmaCoreLiteURL();

    public String localFile1 = "C:\\Slides\\test.png";
    public String localFile2 = "C:\\Slides\\14.svs";

    public String localFile3 = "C:\\Slides\\CMU-1.mrxs";
    public String nameLocalFile1 = "test.png";
    public String nameLocalFile2 = "14.svs";
    public String nameLocalFile3 = "CMU-1.mrxs";

    public String extentionlocalFile1 = "png";
    public String extentionlocalFile2 = "svs";
    public String extentionlocalFile3 = "mrxs";
    public String coreFile1 = "_sys_aws_s3/CMU-2.mrxs";
    public String coreFile2 = "_sys_aws_s3/Philips02_UZBSet0213.tiff";
    public String coreFile3 = "_sys_aws_s3/2DollarBill.zif";

    public String coreZstack = "_sys_aws_s3/zstack/Olympus/Image_2018-005-KB 20x VZ.vsi";
    public String nameCoreFile1 = "CMU-2.mrxs";
    public String nameCoreFile2 = "Philips02_UZBSet0213.tiff";
    public String nameCoreFile3 = "2DollarBill.zif";
    public String extentionCoreFile1 = "mrxs";
    public String extentionCoreFile2 = "tiff";
    public String extentionCoreFile3 = "zif";
    public String firstNonEmptyDir = "_sys_aws_s3/(TO DELETE) test PMATransfer Mehdi";
    public String saveDirectory = "C:\\Slides\\a";
    String pmaCoreLiteSessionID = "SDK.Java";
    private static Map<String, Object> pmaSlideInfos = new HashMap<String, Object>();


    /**
     * Core
     */
    @Test
    public void setDebugFlagCore() {
        Core.setDebugFlag(true);
    }

    @Test
    public void connectCore() {
        coreSessionId = Core.connect(core3, name, passwordCore);
        assertEquals(coreSessionId, Core.connect("url", "name", "password"), Core.connect(core3, name, passwordCore));
        System.out.println("***connectCore()***");
        System.out.println(coreSessionId);
        System.out.println();
        out.println(localFile3 );
    }

    @Test
    public void getPmaSessionsCore() {
        Object sessionUrl = Core.getPmaSessions().values();
        String sessionIdFromSessions = String.valueOf(Core.getPmaSessions().keySet());
        assertEquals(sessionUrl.toString(), "[" + core3 + "]", Core.getPmaSessions().values().toString());
        System.out.println("***getPmaSessions()***");
        System.out.println(sessionIdFromSessions.replace("[", "").replace("]", ""));
        System.out.println(Core.getPmaSessions().keySet());
        System.out.println(Core.getPmaSessions());
        System.out.println();
    }

    @Test
    public void getPmaUsernamesCore() {
        String userName = String.valueOf(Core.getPmaUsernames().values());
        assertEquals(userName, "[" + name + "]", String.valueOf(Core.getPmaUsernames().values()));
        System.out.println("***getPmaUsernames()***");
        System.out.println(Core.getPmaUsernames());
        System.out.println();
    }

    @Test
    public void getPmaSlideInfosCore() {
        System.out.println();
        Map<String, Object> pmaSlideInfos = Core.getPmaSlideInfos();

        for (Map.Entry<String, Object> entry : pmaSlideInfos.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            System.out.println(value.toString());
        }
    }


    @Test
    public void getPmaCoreLiteURLCore() {
        String pmaCoreLiteUrl = Core.getPmaCoreLiteURL();
        String url = "http://localhost:54001/";
        assertEquals(pmaCoreLiteUrl, pmaCoreLiteURL, url);
        System.out.println("***Core.getPmaCoreLiteURL()***");
        System.out.println(Core.getPmaCoreLiteURL());
        System.out.println();
    }

    @Test
    public void getPmaCoreLiteSessionIDCore() {
        String exp = "SDK.Java";
        pmaCoreLiteSessionID = Core.getPmaCoreLiteSessionID();
        assertEquals(exp, pmaCoreLiteSessionID);
        System.out.println("***getPmaCoreLiteSessionID()***");
        System.out.println(Core.getPmaCoreLiteSessionID());
        System.out.println();

    }

    @Test
    public void getSessionIdsCore() {
        JSONArray fullSessions = Core.getSessions(core3, coreSessionId);
        if (fullSessions == null) {
            System.out.println("fullSessions is null!");
        } else {
            for (int i = 0; i < fullSessions.length(); i++) {
                final JSONObject annotations = fullSessions.getJSONObject(i);
                System.out.println(annotations);
            }
        }
    }

    @Test
    public void queryUrlCore() {
        String queryURL = "https://devtest.pathomation.com/test/pma.core.3/query/json/";
        assertEquals(queryURL, Core.queryUrl());
        System.out.println("***queryUrlCore()***");
        System.out.println(Core.queryUrl());
        System.out.println();
    }

    @Test
    public void isLiteCore() {
        String exp = "true";
        assertTrue(exp, Core.isLite());
        System.out.println("***isLite***");
        System.out.println(Core.isLite());
        System.out.println();
    }


    @Test
    public void getVersionInfoCore() {
        String exp = "2.1.1.2027";
        assertEquals(exp, Core.getVersionInfo());
        System.out.println("***getVersionInfo()***");
        System.out.println(Core.getVersionInfo());
        System.out.println();
    }

    @Test
    public void getBuildRevisionCore() {
        String exp = Core.urlReader(pmaCoreLiteURL);
        String buildVersion = "7494e016";
        assertEquals(exp, Core.getBuildRevision(pmaCoreLiteURL));
        assertEquals(buildVersion, Core.getBuildRevision(core3));
        System.out.println("***getBuildRevisionCore()***");
        System.out.println(pmaCoreLiteURL);
        System.out.println(Core.getBuildRevision(core3));
        System.out.println();
    }

    @Test
    public void getAPIVersionCore() throws Exception {
        List<Integer> exp = new ArrayList<>();
        exp.add(3);
        exp.add(0);
        exp.add(1);
        assertEquals(exp, Core.getAPIVersion(core3));
        System.out.println("***getAPIVersion*");
        System.out.println(Core.getAPIVersion(core3));
        System.out.println();
    }

    @Test
    public void getAPIVersionStringCore() throws Exception {
        String exp = "3.0.1";
        assertEquals(exp, Core.getAPIVersionString(core3));
        System.out.println("***getAPIVersionString*");
        System.out.println(Core.getAPIVersionString(core3));
        System.out.println();
    }

    @Test
    public void setGetSessionCore() {
        JSONArray controlSession = Core.getSessions(core3, coreSessionId);
        assertNull(String.valueOf(controlSession), null);
        System.out.println("***setGetSessionCore()***");
        System.out.println(controlSession);
        System.out.println();
    }

    @Test
    public void pingCore() {
        boolean res = true;
        boolean exp = ping(coreSessionId);
        assertEquals(exp, res);
        assertTrue(exp);
        System.out.println("***ping(coreSessionId)***");
        System.out.println("Core ping: " + ping(coreSessionId));
        System.out.println();
    }

    @Test
    public void pmaUrlCore() throws Exception {
        String url = "https://devtest.pathomation.com/test/pma.core.3/";
        assertEquals(url, pmaUrl());
        assertEquals(core3, pmaUrl());
        System.out.println("***pmaUrl()***");
        System.out.println("pmaUrl: " + pmaUrl());
        System.out.println();
    }

    @Test
    public void urlReaderCore() {
        System.out.println("***urlReader(https://www.pathomation.com/***");
        System.out.println(Core.urlReader(pathomationURL));
        System.out.println();
    }

    @Test
    public void domParserCore() {
        System.out.println(Core.domParser(Core.urlReader(pathomationURL)));
    }

    @Test
    public void pmaGetLiteSessionIdCore() {
        String liteSessionID = Core.getPmaCoreLiteSessionID();
        String exp = "SDK.Java";
        assertEquals(liteSessionID, exp);
        System.out.println("***pmaGetLiteSessionIdCore()***");
        System.out.println(liteSessionID);
        System.out.println();
    }

    @Test
    public void pmaUrlSdkJava() throws Exception {
        String maCoreLiteSessionID = "SDK.Java";
        String maCoreLiteURL = "http://localhost:54001/";
        assertEquals(pmaUrl(), core3);
        assertEquals(pmaUrl(maCoreLiteSessionID), maCoreLiteURL);
        System.out.println("***pmaUrlSdkJava()***");
        System.out.println(maCoreLiteSessionID);
        System.out.println(maCoreLiteURL);
        System.out.println(pmaUrl());
        System.out.println(pmaUrl(maCoreLiteSessionID));
        System.out.println();
    }

    @Test
    public void getRootDirectoriesCore() {
        List<String> directories = Core.getRootDirectories(coreSessionId);
        int res = 12;
        assertEquals(directories.size(), res);
    }

    @Test
    public void getFirstNonEmptyDirectoryCore() {
        out.println(coreSessionId);
        String res = Core.getFirstNonEmptyDirectory(null, coreSessionId);
        assertEquals(firstNonEmptyDir, res);
        System.out.println("***getFirstNonEmptyDirectory()***");
        System.out.println(Core.getFirstNonEmptyDirectory(null, coreSessionId));
        System.out.println();
    }

    @Test
    public void getDirectoriesCore() {
        List<String> expectedCoreDirectories = Core.getDirectories(firstNonEmptyDir, coreSessionId);
        int sizeOfDirectories = expectedCoreDirectories.size();
        assertTrue(expectedCoreDirectories.get(0), true);
        assertTrue(expectedCoreDirectories.get(41), true);
        assertEquals(sizeOfDirectories, 46);
        System.out.println("***getDirectories()***");
        for (String dir : expectedCoreDirectories) {
            System.out.println(dir);
        }
        System.out.println();
    }

    @Test
    public void getSlidesCore() {
        List<String> expectedCoreSlidesS3 = Core.getSlides(firstNonEmptyDir, coreSessionId);

        assertNotNull(expectedCoreSlidesS3);
        System.out.println("***getSlidesS3()***");
        System.out.println(expectedCoreSlidesS3);
        System.out.println();
    }

    @Test
    public void getSlideFileExtensionCore() {
        String res1 = extentionCoreFile1;
        String exp1 = Core.getSlideFileExtension(coreFile1);
        String res2 = extentionCoreFile2;
        String exp2 = Core.getSlideFileExtension(coreFile2);
        String res3 = extentionlocalFile1;
        String exp3 = Core.getSlideFileExtension(localFile1);
        String res4 = extentionlocalFile2;
        String exp4 = Core.getSlideFileExtension(localFile2);
        assertEquals(exp1, res1);
        assertEquals(exp2, res2);
        assertEquals(exp3, res3);
        assertEquals(exp4, res4);
        System.out.println("***getSlideFileExtension()***");
        System.out.println(exp1);
        System.out.println(exp2);
        System.out.println(exp3);
        System.out.println(exp4);
        System.out.println();
    }

    @Test
    public void getSlideFileNameCore() {
        String res1 = nameCoreFile1;
        String exp1 = Core.getSlideFileName(coreFile1);
        String res2 = nameCoreFile2;
        String exp2 = Core.getSlideFileName(coreFile2);
        String res3Local = nameLocalFile1;
        String exp3Local = Core.getSlideFileName(localFile1);
        String res4Local = nameLocalFile2;
        String exp4Local = Core.getSlideFileName(localFile2);
        assertEquals(exp1, res1);
        assertEquals(exp2, res2);
        assertEquals(exp3Local, res3Local);
        assertEquals(res4Local, exp4Local);
        System.out.println("***getSlideFileName()***");
        System.out.println(exp1);
        System.out.println(exp2);
        System.out.println(res3Local + " / " + res3Local);
        System.out.println(res4Local + " / " + res4Local);
        System.out.println();
    }

    @Test
    public void getUidCore() throws Exception {
        String exptest123_scn = "\"25HOFIDH87\"";
        String expCMU_1_mrxs = "\"D9SY4Z83F0\"";
        assertEquals(exptest123_scn, Core.getUid(coreFile1, coreSessionId));
        assertEquals(expCMU_1_mrxs, Core.getUid(coreFile2, coreSessionId));
        System.out.println("***getUid()***");
        System.out.println(coreFile1 + ": " + exptest123_scn);
        System.out.println(coreFile2 + ": " + expCMU_1_mrxs);
        System.out.println();
    }


    @Test
    public void getFingerPrintCore() {
        String exp1 = "YrbDCBwr7NlIk50AGFzGODC5IAQ1";
        String exp2 = "uA0qj319zwu-yDhj3a-cQ6e2ZjQ1";
        String res1 = Core.getFingerPrint(coreFile1, coreSessionId);
        String res2 = Core.getFingerPrint(coreFile2, coreSessionId);
        assertEquals(exp1, res1);
        assertEquals(exp2, res2);
        System.out.println("***getFingerPrint()***");
        System.out.println(res1);
        System.out.println(res2);
        System.out.println();
    }

    @Test
    public void whoAmICore() {
        Map<String, String> fullAmi = Core.whoAmI();
        String amountOfDataDownloaded = Core.whoAmI().get("amountOfDataDownloaded");
        String sessionID = Core.whoAmI().get("sessionID");
        String url = Core.whoAmI().get("url");
        String username = Core.whoAmI().get("username");
        assertEquals(coreSessionId, sessionID);
        assertEquals(core3, url);
        assertEquals(name, username);
        System.out.println("***whoAmI()***");
        System.out.println(fullAmi);
        System.out.println(amountOfDataDownloaded);
        System.out.println(sessionID);
        System.out.println(url);
        System.out.println(username);
        System.out.println();

    }

    @Test
    public void getTileSizeCore() {
        List<Integer> exp = new ArrayList<>();
        exp.add(512);
        assertEquals(exp, Core.getTileSize(coreSessionId));
        System.out.println("***getTileSize()***");
        System.out.println(Core.getTileSize(coreSessionId));
        System.out.println();
    }

    @Test
    public void lastModifiedAndPhysicalSizeCore() {
        List<String> listSlides = null;
        List<String> listDirectories = Core.getDirectories(firstNonEmptyDir, coreSessionId);
        for (String directory : listDirectories) {
            assertNotNull(directory);
            listSlides = Core.getSlides(directory, coreSessionId, true);
            assertNotNull(listSlides);
        }
        for (String slide : listSlides) {
            Object lastModified = Core.getSlideInfo(slide, coreSessionId).get("LastModified");
            Object physicalSize = Core.getSlideInfo(slide, coreSessionId).get("PhysicalSize");
            Object baseUrl = Core.getSlideInfo(slide, coreSessionId).get("BaseUrl");
            assertNotNull(listDirectories);
            assertNotNull(slide);
            assertNotNull(lastModified);
            assertNotNull(physicalSize);
            System.out.println("***lastModifiedAndPhysicalSize***");
            System.out.println(lastModified);
            System.out.println(physicalSize);
            System.out.println(baseUrl);
            System.out.println();
        }
    }

    @Test
    public void getZoomLevelListCore() {
        List<Integer> zoomLevel = Core.getZoomLevelsList(coreFile3,
                coreSessionId);
        System.out.println("***getZoomLevelList***");
        int exp = 0;
        int zoomZeroLevel = zoomLevel.get(0);
        for (int zoom : zoomLevel) {
            assertNotNull(zoomLevel);
            assertNotNull(zoom);
            System.out.println(zoom);
        }
        assertEquals(exp, zoomZeroLevel);
        System.out.println();
    }

    @Test
    public void getMaxZoomLevelCore() {
        int coreFile1ZoomLevel = 8;
        int coreFile2ZoomLevel = 7;
        int coreFile3ZoomLevel = 4;
        assertEquals(coreFile1ZoomLevel, Core.getMaxZoomLevel(coreFile1));
        assertEquals(coreFile2ZoomLevel, Core.getMaxZoomLevel(coreFile2));
        assertEquals(coreFile3ZoomLevel, Core.getMaxZoomLevel(coreFile3));
        System.out.println("***getMaxZoomLevel()***");
        System.out.println(Core.getMaxZoomLevel(coreFile1));
        System.out.println(Core.getMaxZoomLevel(coreFile2));
        System.out.println(Core.getMaxZoomLevel(coreFile3));
        System.out.println();
    }

    @Test
    public void getMaxZoomLevelListCore() {
        List<Integer> coreFile1ZoomLevel = new ArrayList<>();
        coreFile1ZoomLevel.add(0);
        coreFile1ZoomLevel.add(1);
        coreFile1ZoomLevel.add(2);
        coreFile1ZoomLevel.add(3);
        coreFile1ZoomLevel.add(4);
        coreFile1ZoomLevel.add(5);
        coreFile1ZoomLevel.add(6);
        coreFile1ZoomLevel.add(7);
        coreFile1ZoomLevel.add(8);
        assertEquals(coreFile1ZoomLevel, Core.getZoomLevelsList(coreFile1));
        System.out.println("***getMaxZoomLevel()***");
        System.out.println(Core.getZoomLevelsList(coreFile1));
        System.out.println();
    }

    @Test
    public void getZoomLevelsDictCore() {
        System.out.println("***getZoomLevelsDict()***");
        System.out.println(Core.getZoomLevelsDict(coreFile1));
        System.out.println(Core.getZoomLevelsDict(coreFile2));
        System.out.println(Core.getZoomLevelsDict(coreFile3));
        System.out.println();
    }

    @Test
    public void getPixelsPerMicrometerCore() {
        System.out.println(Core.getPixelsPerMicrometer(coreFile1));
    }

    @Test
    public void getPixelDimensionsCore() {
        System.out.println("***getPixelDimensions()***");
        System.out.println(Core.getPixelDimensions(coreFile1));
        System.out.println(Core.getPixelDimensions(coreFile2));
        System.out.println(Core.getPixelDimensions(coreFile3));
        System.out.println();
    }

    @Test
    public void getNumberOfTilesCore() {
        System.out.println("***getNumberOfTiles()***");
        System.out.println(Core.getNumberOfTiles(coreFile1));
        System.out.println(Core.getNumberOfTiles(coreFile2));
        System.out.println(Core.getNumberOfTiles(coreFile3));
        System.out.println();
    }

    @Test
    public void getPhysicalDimensionsCore() {
        System.out.println("***getPhysicalDimensions()***");
        System.out.println(Core.getPhysicalDimensions(coreFile1));
        System.out.println(Core.getPhysicalDimensions(coreFile2));
        System.out.println(Core.getPhysicalDimensions(coreFile3));
        System.out.println();
    }

    @Test
    public void getNumberOfChannelsCore() {
        System.out.println("***etNumberOfChannels()***");
        System.out.println(Core.getNumberOfChannels(coreFile1));
        System.out.println(Core.getNumberOfChannels(coreFile2));
        System.out.println(Core.getNumberOfChannels(coreFile3));
        System.out.println(Core.getNumberOfChannels(coreZstack));
        System.out.println();
    }

    @Test
    public void getNumberOfLayersCore() {
        System.out.println("***etNumberOfChannels()***");
        System.out.println(Core.getNumberOfLayers(coreFile1));
        System.out.println(Core.getNumberOfLayers(coreFile2));
        System.out.println(Core.getNumberOfLayers(coreFile3));
        System.out.println(Core.getNumberOfLayers(coreZstack));
        System.out.println();
    }

    @Test
    public void isFluorescentCore() {
        System.out.println("***etNumberOfChannels()***");
        System.out.println(Core.isFluorescent(coreFile1));
        System.out.println(Core.isFluorescent(coreFile2));
        System.out.println(Core.isFluorescent(coreFile3));
        System.out.println(Core.isFluorescent(coreZstack));
        System.out.println();
    }

    @Test
    public void isMultiLayerCore() {
        System.out.println("***etNumberOfChannels()***");
        System.out.println(Core.isMultiLayer(coreFile1));
        System.out.println(Core.isMultiLayer(coreFile2));
        System.out.println(Core.isMultiLayer(coreFile3));
        System.out.println(Core.isMultiLayer(coreZstack));
        System.out.println();
    }

    @Test
    public void getLastModifiedDateCore() {
        System.out.println("***etNumberOfChannels()***");
        System.out.println(Core.getLastModifiedDate(coreFile1));
        System.out.println(Core.getLastModifiedDate(coreFile2));
        System.out.println(Core.getLastModifiedDate(coreFile3));
        System.out.println(Core.getLastModifiedDate(coreZstack));
        System.out.println();
    }

    @Test
    public void isZStackCore() {
        assertEquals(false, Core.isZStack(coreFile1, coreSessionId));
        assertEquals(false, Core.isZStack(coreFile2, coreSessionId));
        assertEquals(false, Core.isZStack(coreFile3, coreSessionId));
        assertEquals(true, Core.isZStack(coreZstack, coreSessionId));
        System.out.println("***isZStack()***");
        System.out.println(Core.isZStack(coreFile1, coreSessionId));
        System.out.println(Core.isZStack(coreFile2, coreSessionId));
        System.out.println(Core.isZStack(coreFile3, coreSessionId));
        System.out.println(Core.isZStack(coreZstack, coreSessionId));
        System.out.println();
    }

    @Test
    public void getMagnificationCore() {
        int exp1 = 2147483647;
        int exp2 = 0;
        assertEquals(exp1, Core.getMagnification(coreFile1, 50, true, coreSessionId));
        assertEquals(exp1, Core.getMagnification(coreFile2, 50, true, coreSessionId));
        assertEquals(exp2, Core.getMagnification(coreFile3, 50, true, coreSessionId));
        assertEquals(exp1, Core.getMagnification(coreZstack, 50, true, coreSessionId));
        System.out.println("***getMagnification()***");
        System.out.println(Core.getMagnification(coreFile1, 50, true, coreSessionId));
        System.out.println(Core.getMagnification(coreFile2, 50, true, coreSessionId));
        System.out.println(Core.getMagnification(coreFile3, 50, true, coreSessionId));
        System.out.println(Core.getMagnification(coreZstack, 50, true, coreSessionId));
        System.out.println();
    }

    @Test
    public void getAssociatedImageTypesCore() {
        List<String> exp1 = Arrays.asList("Thumbnail", "Barcode", "Macro");
        List<String> exp2 = Arrays.asList("Thumbnail", "Barcode");
        List<String> exp3 = Arrays.asList("Thumbnail");
        List<String> exp4 = Arrays.asList("Thumbnail", "Macro", "Barcode");
        assertEquals(exp1, Core.getAssociatedImageTypes(coreFile1, coreSessionId));
        assertEquals(exp2, Core.getAssociatedImageTypes(coreFile2, coreSessionId));
        assertEquals(exp3, Core.getAssociatedImageTypes(coreFile3, coreSessionId));
        assertEquals(exp4, Core.getAssociatedImageTypes(coreZstack, coreSessionId));

        System.out.println("***getAssociatedImageTypes()***");
        System.out.println(Core.getAssociatedImageTypes(coreFile1, coreSessionId));
        System.out.println(Core.getAssociatedImageTypes(coreFile2, coreSessionId));
        System.out.println(Core.getAssociatedImageTypes(coreFile3, coreSessionId));
        System.out.println(Core.getAssociatedImageTypes(coreZstack, coreSessionId));
        System.out.println();
    }

    @Test
    public void getBarcodeUrlCore() {
        System.out.println("***getBarcodeUrl()***");
        System.out.println(Core.getBarcodeUrl(coreFile1, coreSessionId));
        System.out.println(Core.getBarcodeUrl(coreFile2, coreSessionId));
        System.out.println(Core.getBarcodeUrl(coreFile3, coreSessionId));
        System.out.println(Core.getBarcodeUrl(coreZstack, coreSessionId));
        System.out.println();
    }

    @Test
    public void getBarcodeImageCore() {
        System.out.println("***getBarcodeImage()***");
        System.out.println(Core.getBarcodeImage(coreFile1, coreSessionId));
        System.out.println(Core.getBarcodeImage(coreFile2, coreSessionId));
        System.out.println(Core.getBarcodeImage(coreZstack, coreSessionId));
        System.out.println();
    }

    @Test
    public void getBarcodeTextCore() {
        System.out.println("***getBarcodeText()***");
        System.out.println(Core.getBarcodeText(coreFile1, coreSessionId));
        System.out.println(Core.getBarcodeText(coreFile2, coreSessionId));
        System.out.println(Core.getBarcodeText(coreFile3, coreSessionId));
        System.out.println(Core.getBarcodeText(coreZstack, coreSessionId));
        System.out.println();
    }

    @Test
    public void getLabelUrlCore() {
        System.out.println("***getLabelUrl()***");
        System.out.println(Core.getLabelUrl(coreFile1, coreSessionId));
        System.out.println(Core.getLabelUrl(coreFile2, coreSessionId));
        System.out.println(Core.getLabelUrl(coreFile2, coreSessionId));
        System.out.println(Core.getLabelUrl(coreZstack, coreSessionId));
        System.out.println();
    }

    @Test
    public void getThumbnailUrlCore() {
        System.out.println("***getThumbnailUrl()***");
        System.out.println(Core.getThumbnailUrl(coreFile1, coreSessionId, 600, 400));
        System.out.println(Core.getThumbnailUrl(coreFile2, coreSessionId, 600, 400));
        System.out.println(Core.getThumbnailUrl(coreFile3, coreSessionId, 600, 800));
        System.out.println(Core.getThumbnailUrl(coreZstack, coreSessionId, 2000, 2000));
        System.out.println();
    }

    @Test
    public void getThumbnailImageCore() {
        System.out.println("***getThumbnailImage()***");
        System.out.println(Core.getThumbnailImage(coreFile1));
        System.out.println(Core.getThumbnailImage(coreFile1));
        System.out.println(Core.getThumbnailImage(coreFile1));
        System.out.println(Core.getThumbnailImage(coreFile1));
        System.out.println();
    }

    @Test
    public void getTileUrlCore() throws Exception {
        System.out.println("***getTileUrl()***");
        System.out.println(Core.getTileUrl(coreFile1, 0, 0, 0, 0, coreSessionId, Core.getSlideFileExtension(coreFile1), 100));
        System.out.println(Core.getTileUrl(coreFile2, 0, 0, 0, 0, coreSessionId, Core.getSlideFileExtension(coreFile2), 100));
        System.out.println(Core.getTileUrl(coreFile3, 0, 0, 0, 0, coreSessionId, Core.getSlideFileExtension(coreFile3), 100));
        System.out.println(Core.getTileUrl(coreZstack, 0, 0, 0, 0, coreSessionId, Core.getSlideFileExtension(coreZstack), 100));
        System.out.println();
    }

    @Test
    public void getTileCore() throws Exception {
        System.out.println("***getTile()***");
        System.out.println(Core.getTile(coreFile1, 0, 0, 0, 0, coreSessionId, Core.getSlideFileExtension(coreFile1), 100));
        System.out.println(Core.getTile(coreFile2, 0, 0, 0, 0, coreSessionId, Core.getSlideFileExtension(coreFile2), 100));
        System.out.println(Core.getTile(coreFile3, 0, 0, 0, 0, coreSessionId, Core.getSlideFileExtension(coreFile3), 100));
        System.out.println(Core.getTile(coreZstack, 0, 0, 0, 0, coreSessionId, Core.getSlideFileExtension(coreZstack), 100));
        System.out.println();
    }

    @Test
    public void getRegionUrlCore() throws Exception {
        List<String> list1 = new ArrayList<>();
        list1.add("good");
        list1.add("bad");
        List<String> list2 = new ArrayList<>();
        list2.add("nice");
        list2.add("twice");
        System.out.println(coreSessionId);
        System.out.println(Core.getRegionUrl(coreFile1, 0, 0, 0, 0, 1, coreSessionId,
                Core.getSlideFileExtension(coreFile1),
                100, 300, 0, 0, 0, 300, false, false, null, 0, false, false, list1, list2));
        System.out.println(Core.getRegionUrl(coreFile2, 0, 0, 0, 0, 1, coreSessionId,
                Core.getSlideFileExtension(coreFile2),
                100, 300, 0, 0, 0, 300, false, false, null, 0, false, false, list1, list2));
        System.out.println(Core.getRegionUrl(coreFile3, 0, 0, 0, 0, 1, coreSessionId,
                Core.getSlideFileExtension(coreFile3),
                100, 300, 0, 0, 0, 300, false, false, null, 0, false, false, list1, list2));
        System.out.println(Core.getRegionUrl(coreZstack, 0, 0, 0, 0, 1, coreSessionId,
                Core.getSlideFileExtension(coreZstack),
                100, 300, 0, 0, 0, 300, false, false, null, 0, false, false, list1, list2));
    }

    @Test
    public void getTilesCore() {
        System.out.println("***getTiles()***");
        List<Object> list1 = (List<Object>) Core.getTiles(coreFile1, 0, 0, 0, 0, 0, 0, coreSessionId,
                Core.getSlideFileExtension(coreFile1), 300).collect(Collectors.toList());
        for (Object o : list1) {
            System.out.println(o);
        }

        List<Object> list2 = (List<Object>) Core.getTiles(coreFile2, 0, 0, 0, 0, 0, 0, coreSessionId,
                Core.getSlideFileExtension(coreFile2), 300).collect(Collectors.toList());
        for (Object o : list2) {
            System.out.println(o);
        }

        List<Object> list3 = (List<Object>) Core.getTiles(coreFile3, 0, 0, 0, 0, 0, 0, coreSessionId,
                Core.getSlideFileExtension(coreFile3), 300).collect(Collectors.toList());
        for (Object o : list3) {
            System.out.println(o);
        }

        List<Object> list4 = (List<Object>) Core.getTiles(coreZstack, 0, 0, 0, 0, 0, 0, coreSessionId,
                Core.getSlideFileExtension(coreZstack), 300).collect(Collectors.toList());
        for (Object o : list4) {
            System.out.println(o);
        }
        System.out.println();
    }

    @Test
    public void getSubmittedFormsCore() {
        System.out.println("***getSubmittedForms()***");
        Core.getSubmittedForms(coreFile1, coreSessionId);
        Core.getSubmittedForms(coreFile2, coreSessionId);
        Core.getSubmittedForms(coreFile3, coreSessionId);
        Core.getSubmittedForms(coreZstack, coreSessionId);
        System.out.println();
    }

    @Test
    public void getSubmittedFormDataCore() {
        System.out.println(Core.getSubmittedFormData(coreFile1, coreSessionId));
        System.out.println(Core.getSubmittedFormData(coreFile2, coreSessionId));
        System.out.println(Core.getSubmittedFormData(coreFile3, coreSessionId));
        System.out.println(Core.getSubmittedFormData(coreZstack, coreSessionId));
    }

    @Test
    public void prepareFormMapCore() {
        System.out.println("***prepareFormMap()***");
        System.out.println(Core.prepareFormMap("pathomation", coreSessionId));
        System.out.println();
    }

    @Test
    public void getNumberOfZStackLayersCore() {
        System.out.println("***etNumberOfChannels()***");
        System.out.println(Core.getNumberOfZStackLayers(coreFile1));
        System.out.println(Core.getNumberOfZStackLayers(coreFile2));
        System.out.println(Core.getNumberOfZStackLayers(coreFile3));
        System.out.println(Core.getNumberOfZStackLayers(coreZstack));
        System.out.println();
    }

    @Test
    public void getSlideInfoCore() {
        Map<String, Object> info = Core.getSlideInfo(coreFile1,
                coreSessionId);
        List<String> exp = new ArrayList<>();
        System.out.println("***getSlideInfo***");
        out.println();
        for (Map.Entry entry : info.entrySet()) {
            exp.add(entry.toString());
            assertNotNull(info);
            assertNotNull(entry);
            System.out.println(entry);
        }
        System.out.println();
    }

    @Test
    public void getSlidesInfoCore() {
        List<String> exp = new ArrayList<>();
        exp.add(coreFile1);
        exp.add(coreFile2);
        exp.add(coreFile3);
        System.out.println("***getSlidesInfoCore()***");
        System.out.println(Core.getSlidesInfo(exp, coreSessionId));
        System.out.println();
    }

    @Test
    public void download() {
        ProgressHttpEntityWrapper.ProgressCallback progressCallback = new ProgressHttpEntityWrapper.ProgressCallback() {
            @Override
            public void progress(long bytesRead, long transferred, long totalBytes, String filename) {
                float progress = ((float) transferred / totalBytes) * 100;
                System.out.println(filename + ": " + transferred + " (" + progress + " %)");
                System.out.println("bytesRead :" + bytesRead);
                System.out.println("totalBytes: " + totalBytes);
            }
        };
        String vsi = "_sys_aws_s3/brightfield/Olympus/Alfred_Olympus_VS120_brightfield_all-in-one.vsi";
        String jpg = "_sys_aws_s3/(TO DELETE) test PMATransfer Mehdi/a/dog-2.jpg";
        String mrxs ="_sys_aws_s3/(TO DELETE) test PMATransfer Mehdi/renaming MRXS file.mrxs";
        String tif = "_sys_aws_s3/(TO DELETE) test PMATransfer Mehdi/test2.tif";
        String vsiGent = "_sys_aws_s3/(TO DELETE) test PMATransfer Mehdi/UGent.vsi";
        String ets = "UGent/stack1/frame_t.ets";
        String dat = "renaming MRXS file/Data0010.dat";
        String datLocal = "C:\\Slides\\a\\renaming MRXS file\\Data0010.dat";
        String etsLocal = "C:\\Slides\\a\\UGent\\stack1\\frame_t.ets";
        String local = "C:\\Slides\\a";
        try {
            Core.download(jpg, local,
                    Core.connect("https://devtest.pathomation.com/test/pma.core.3/", "vitalij", "PMI9GH9I"), progressCallback);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void uploadWithOneClickAndresCore() throws Exception {
        String localSourceSlidePng = "C:\\Slides\\download_check\\down\\test.png";
        String localSourceSlideZif = "C:\\Slides\\download_check\\down\\2DollarBill.zif";
        String localSourceSlideSvs = "C:\\Slides\\download_check\\down\\CMU-11.svs";
        String localSourceSlideNdpi = "C:\\Slides\\download_check\\down\\indp.ndpi";
        String localSourceSlideJpg = "C:\\Slides\\download_check\\down\\dog.jpg";
        String localSourceSlideTiff = "C:\\Slides\\download_check\\down\\testTif.tiff";
        String localSourceSlideSzi = "C:\\Slides\\download_check\\down\\2DollarBill2.szi";
        String localSourceSlideVsi = "C:\\Slides\\a\\UGent.vsi";
        String localSourceSlideMrxs = "C:\\Slides\\download_check\\down\\CMU-1.mrxs";

        String targetFolder = "_sys_ref/Test_Slides";

    /**
     * png
     */
        ProgressHttpEntityWrapper.ProgressCallback progressCallbackPng = new ProgressHttpEntityWrapper.ProgressCallback() {
            @Override
            public void progress(long bytesRead, long transferred, long totalBytes, String filename) {
                System.out.println(bytesRead + "  bytesRead-----");
                System.out.println("totalBytes: " + totalBytes);
                float progress = ((float) transferred / totalBytes) * 100;
                System.out.println(filename + ": " + transferred + " (" + progress + " %)");
            }
        };
        Core.upload(localSourceSlidePng, targetFolder, coreSessionId, progressCallbackPng, null);

    /**
     * zif
     */
        ProgressHttpEntityWrapper.ProgressCallback progressCallbackZif = new ProgressHttpEntityWrapper.ProgressCallback() {
            public void progress(long bytesRead, long transferred, long totalBytes, String filename) {
                System.out.println(bytesRead + "  bytesRead-----");
                System.out.println("totalBytes: " + totalBytes);
                float progress = ((float) transferred / totalBytes) * 100;
                System.out.println(filename + ": " + transferred + " (" + progress + " %)");
            }
        };
        Core.upload(localSourceSlideZif, targetFolder, coreSessionId, progressCallbackZif, null);
        /**
         * svs
         */
        ProgressHttpEntityWrapper.ProgressCallback progressCallbackSvs = new ProgressHttpEntityWrapper.ProgressCallback() {
            public void progress(long bytesRead, long transferred, long totalBytes, String filename) {
                System.out.println(bytesRead + "  bytesRead-----");
                System.out.println("totalBytes: " + totalBytes);
                float progress = ((float) transferred / totalBytes) * 100;
                System.out.println(filename + ": " + transferred + " (" + progress + " %)");
            }
        };
        Core.upload(localSourceSlideSvs, targetFolder, coreSessionId, progressCallbackSvs, null);
        /**
         * Ndpi
         */
        ProgressHttpEntityWrapper.ProgressCallback progressCallbackNdpi = new ProgressHttpEntityWrapper.ProgressCallback() {
            public void progress(long bytesRead, long transferred, long totalBytes, String filename) {
                System.out.println(bytesRead + "  bytesRead-----");
                System.out.println("totalBytes: " + totalBytes);
                float progress = ((float) transferred / totalBytes) * 100;
                System.out.println(filename + ": " + transferred + " (" + progress + " %)");
            }
        };
        Core.upload(localSourceSlideNdpi, targetFolder, coreSessionId, progressCallbackNdpi, null);

        /**
         * jpg
         */
        ProgressHttpEntityWrapper.ProgressCallback progressCallbackJpg = new ProgressHttpEntityWrapper.ProgressCallback() {
            @Override
            public void progress(long bytesRead, long transferred, long totalBytes, String filename) {
                System.out.println(bytesRead + "  bytesRead-----");
                System.out.println("totalBytes: " + totalBytes);
                float progress = ((float) transferred / totalBytes) * 100;
                System.out.println(filename + ": " + transferred + " (" + progress + " %)");
            }
        };
        Core.upload(localSourceSlideJpg, targetFolder, coreSessionId, progressCallbackJpg, null);

        /**
         * tiff
         */
        ProgressHttpEntityWrapper.ProgressCallback progressCallbackTiff = new ProgressHttpEntityWrapper.ProgressCallback() {
            @Override
            public void progress(long bytesRead, long transferred, long totalBytes, String filename) {
                float progress = ((float) transferred / totalBytes) * 100;
                System.out.println(filename + ": " + transferred + " (" + progress + " %)");
                System.out.println(bytesRead + "  bytesRead-----");
            }
        };
        Core.upload(localSourceSlideTiff, targetFolder, coreSessionId, progressCallbackTiff, null);

        /**
         * szi
         */
        ProgressHttpEntityWrapper.ProgressCallback progressCallbackSzi = new ProgressHttpEntityWrapper.ProgressCallback() {
            @Override
            public void progress(long bytesRead, long transferred, long totalBytes, String filename) {
                float progress = ((float) transferred / totalBytes) * 100;
                System.out.println(filename + ": " + transferred + " (" + progress + " %)");
                System.out.println(bytesRead + "  bytesRead-----");
            }
        };
        Core.upload(localSourceSlideSzi, targetFolder, coreSessionId, progressCallbackSzi, null);
        /**
         * vsi
         */
        ProgressHttpEntityWrapper.ProgressCallback progressCallbackVsi = new ProgressHttpEntityWrapper.ProgressCallback() {
            public void progress(long bytesRead, long transferred, long totalBytes, String filename) {
                System.out.println(bytesRead + "  bytesRead-----");
                System.out.println("totalBytes: " + totalBytes);
                float progress = ((float) transferred / totalBytes) * 100;
                System.out.println(filename + ": " + transferred + " (" + progress + " %)");
            }
        };
        Core.upload(localSourceSlideVsi, targetFolder, coreSessionId, progressCallbackVsi, null);
//
    /**
     * mrxs
     */
        ProgressHttpEntityWrapper.ProgressCallback progressCallbackMrxs = new ProgressHttpEntityWrapper.ProgressCallback() {
            @Override
            public void progress(long bytesRead, long transferred, long totalBytes, String filename) {
                System.out.println(bytesRead + "  bytesRead-----");
                float progress = ((float) transferred / totalBytes) * 100;
                System.out.println(filename + ": " + transferred + " (" + progress + " %)");
                System.out.println("totalBytes: " + totalBytes);
            }
        };
        Core.upload(localSourceSlideMrxs, targetFolder, coreSessionId, progressCallbackMrxs, null);
        /**
         *
         */
    }

    String localSourceSlidePng = "C:\\Slides\\download_check\\down\\test.png";
    String localSourceSlideZif = "C:\\Slides\\download_check\\down\\2DollarBill.zif";
    String localSourceSlideSvs = "C:\\Slides\\download_check\\down\\CMU-11.svs";
    String localSourceSlideNdpi = "C:\\Slides\\download_check\\down\\indp.ndpi";
    String localSourceSlideJpg = "C:\\Slides\\download_check\\down\\dog.jpg";
    String localSourceSlideTiff = "C:\\Slides\\download_check\\down\\testTif.tiff";
    String localSourceSlideSzi = "C:\\Slides\\download_check\\down\\2DollarBill2.szi";
    String localSourceSlideVsi = "C:\\Slides\\download_check\\down\\Alfred_Olympus_VS120_brightfield_all-in-one.vsi";
    String localSourceSlideMrxs = "C:\\Slides\\a\\CMU-1.mrxs";
    String targetFolder = "_sys_aws_s3/(TO DELETE) test PMATransfer Mehdi/a";

    @Test
    public void uploadOneSlideWithSingleFilesCore() {

        String localSourceSlide = localSourceSlideNdpi;

        List<String> relativePathsList = new ArrayList<>();
        for (HashMap<String, String> filesList : Core._pma_filesToUpload(localSourceSlide, targetFolder, coreSessionId)) {
            for (Map.Entry<String, String> hm : filesList.entrySet()) {
                out.println("key: " + hm.getKey());
                if (hm.getKey().equals("Path") && !hm.getValue().contains("/")) {
                    out.println("!/value: " + hm.getValue());
                    relativePathsList.add(hm.getValue());
                }
            }
        }
        for (HashMap<String, String> filesList : Core._pma_filesToUpload(localSourceSlide, targetFolder, coreSessionId)) {
            for (Map.Entry<String, String> hm : filesList.entrySet()) {
                out.println("key: " + hm.getKey());
                if (hm.getKey().equals("Path") && hm.getValue().contains("/")) {
                    out.println("value: " + hm.getValue());
                    relativePathsList.add(hm.getValue());
                }
            }
        }

        JSONObject jsonObject = Core._pma_get_DataIdTypeConString_forUpload(localSourceSlide, targetFolder, coreSessionId);
        out.println(jsonObject.get("Id"));
        out.println(jsonObject.get("UploadType"));
        out.println(jsonObject.get("Urls"));
        String uploadUrl;
        JSONArray jsonArray = new JSONArray();
        for (int i = 0; i < relativePathsList.size(); i++) {
            if (!jsonObject.get("Urls").toString().equals("null")) {
                jsonArray = jsonObject.getJSONArray("Urls");
            }
            if (jsonObject.get("Urls").toString().equals("null")) {
                try {
                    uploadUrl = pmaUrl(coreSessionId) + "transfer/Upload/" + jsonObject.get("Id").toString()
                            + "?sessionID="
                            + PMA.pmaQ((coreSessionId)) + "&path=" +
                            PMA.pmaQ(relativePathsList.get(i));

                    jsonArray.put(uploadUrl);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            String filePath = localSourceSlide.substring(localSourceSlide.indexOf(""), localSourceSlide.lastIndexOf("\\")) + "\\" + relativePathsList.get(i);
            out.println("filePath: " + filePath.replace("/", "\\"));
            File file = new File(filePath);
            out.println("relativePath: " + relativePathsList.get(i));
            ProgressHttpEntityWrapper.ProgressCallback progressCallback = new ProgressHttpEntityWrapper.ProgressCallback() {
                @Override
                public void progress(long bytesRead, long transferred, long totalBytes, String filename) {
                    System.out.println(bytesRead + "  bytesRead-----");
                    System.out.println("totalBytes: " + totalBytes);
                    float progress = ((float) transferred / totalBytes) * 100;
                    System.out.println(filename + ": " + transferred + " (" + progress + " %)");
                }
            };
            String s = (String) jsonArray.get(i);
            out.println("s: " +  jsonArray.get(i));
            out.println();
            try {
                Core.upload(localSourceSlide, targetFolder, coreSessionId, progressCallback, relativePathsList.get(i),
                        jsonArray.get(i).toString(), jsonObject.get("Id").toString(), jsonObject.get("UploadType").toString(), file);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void _pma_filesToUploadCore() {
        String localSourceSlide = localSourceSlideVsi;
        List<String> relativePathsList = new ArrayList<>();
        for (HashMap<String, String> filesList : Core._pma_filesToUpload(localSourceSlide, targetFolder, coreSessionId)) {
            for (Map.Entry<String, String> hm : filesList.entrySet()) {
//                out.println("key: " + hm.getKey());
                if (hm.getKey().equals("Path") && !hm.getValue().contains("/")) {
                    out.println(" isMain file value: " + hm.getValue());
                    relativePathsList.add(hm.getValue());
                }
            }
        }
        for (HashMap<String, String> filesList : Core._pma_filesToUpload(localSourceSlide, targetFolder, coreSessionId)) {
            for (Map.Entry<String, String> hm : filesList.entrySet()) {
//                out.println("key: " + hm.getKey());
                if (hm.getKey().equals("Path") && hm.getValue().contains("/")) {
                    out.println("!isMain file value: " + hm.getValue());
                    relativePathsList.add(hm.getValue());
                }
            }
        }
    }

    @Test
    public void _pma_get_DataIdTypeConString_forUpload() {
        JSONObject jsonObject = Core._pma_get_DataIdTypeConString_forUpload(localSourceSlideMrxs, targetFolder, coreSessionId);
        out.println("upload ID: " + jsonObject.get("Id"));
        out.println("upload type: "  + jsonObject.get("UploadType"));
        for (Object o : jsonObject.getJSONArray("Urls")) {
            out.println("URLs: " + o);
        }
    }


    @Test
    public void uploadTransferCore() {
        String localSourceSlidePng = "C:\\Slides\\download_check\\down\\test.png";
        String localSourceSlideZif = "C:\\Slides\\download_check\\down\\2DollarBill.zif";
        String localSourceSlideSvs = "C:\\Slides\\download_check\\down\\CMU-11.svs";
        String localSourceSlideNdpi = "C:\\Slides\\download_check\\down\\indp.ndpi";
        String localSourceSlideJpg = "C:\\Slides\\download_check\\down\\dog.jpg";
        String localSourceSlideTiff = "C:\\Slides\\download_check\\down\\testTif.tiff";
        String localSourceSlideSzi = "C:\\Slides\\download_check\\down\\2DollarBill2.szi";
        String localSourceSlideVsi = "C:\\Slides\\a\\UGent.vsi";
        String localSourceSlideMrxs = "C:\\Slides\\download_check\\down\\CMU-1.mrxs";

        String targetFolder = "_sys_aws_s3/A_to_delete";

        String[] urls = Core._pma_get_DataIdTypeConString_forUpload(localSourceSlideMrxs, targetFolder, coreSessionId).get("Urls").toString().split(",");
        Map<Map<String, String>, List<String>> uploadIdAndType = new HashMap<>();
        out.println(Core._pma_get_DataIdTypeConString_forUpload(localSourceSlideMrxs, targetFolder, coreSessionId).get("Id").toString());
        out.println(Core._pma_get_DataIdTypeConString_forUpload(localSourceSlideMrxs, targetFolder, coreSessionId).get("UploadType").toString());
        for (int i = 0; i < urls.length; i++) {
            out.println(urls[i] + " ulls ");

        }
    }

    @Test
    public void getFilesForSlideCore() {
        Map<String, Map<String, String>> files = Core.getFilesForSlide("_sys_aws_s3/brightfield/Olympus/Alfred_Olympus_VS120_brightfield_all-in-one.vsi", coreSessionId);
        Map<String, Map<String, String>> filesEnum = Core.getFilesForSlide("_sys_aws_s3/CMU-2.mrxs", coreSessionId);
        for (String file : files.keySet()) {
            System.out.println(file + "   file");
        }
        System.out.println();
        for (String fEnum : filesEnum.keySet()) {
            System.out.println(fEnum + "   fEnum");
        }

        Map<String, Map<String, String>> filesLite1 = Core.getFilesForSlide("C:\\Slides\\a\\error2.jpg", pmaCoreLiteSessionID);
        Map<String, Map<String, String>> filesLite2 = Core.getFilesForSlide("C:\\Slides\\a\\U AND ME CMU-2.mrxs", pmaCoreLiteSessionID);
        for (String file : filesLite1.keySet()) {
            System.out.println(file + "   fileLite1");
        }
        System.out.println();
        for (String fileLite : filesLite2.keySet()) {
            System.out.println(fileLite + "   fileLite2");
        }
    }

    @Test
    public void testProps() {
        System.out.println(PmaClient.loadProps());
    }

    @Test
    public void getSidesCore() {
        System.out.println(Core.getSlides("C:\\Slides\\a\\", "SDK.Java", false));
        List<String> list = Core.getSlides("C:\\Slides\\a\\", "SDK.Java", false);
        for (String slide : list) {
            System.out.println(slide);
            System.out.println(Core.getSlideFileName(slide));
        }
    }
    @Test

    public void disconnectCore() {
        boolean res = Core.ping(coreSessionId);
        boolean expectedTrue = Core.disconnect(coreSessionId);
        assertEquals(expectedTrue, true);
        assertEquals(res, true);
        System.out.println("***disconnectCore()***");
        System.out.println("disconnectCore: " + res);
        System.out.println();
    }
}