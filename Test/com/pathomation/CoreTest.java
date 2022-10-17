package com.pathomation;

import org.json.JSONArray;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static com.pathomation.Core.ping;
import static com.pathomation.Core.pmaUrl;
import static org.junit.Assert.*;

public class CoreTest {

    String core3 = "https://devtest.pathomation.com/test/pma.core.3/";
    String pmaCoreLiteURL = Core.getPmaCoreLiteURL();
    String name = "vitalij";

    String password = "PMI9GH9I";
    String coreSessionId = Core.connect(core3, name, password);

    Map<String, Object> pmaSlideInfos = Core.getPmaSlideInfos();

    String pmaCoreLiteSessionID;

    Map<String, Integer> pmaAmountOfDataDownloaded;

    @Test
    public void connectCore() {
        assertEquals(coreSessionId, Core.connect("https://devtest.pathomation.com/test/pma.core.3/", "vitalij", "PMI9GH9I"), Core.connect(core3, name, password));
        System.out.println("***connectCore()***");
        System.out.println(coreSessionId);
        System.out.println();
    }

    @Test
    public void getPmaSessionsCore() {
        Object sessionUrl = Core.getPmaSessions().values();
        assertEquals(sessionUrl.toString(), "[" + core3 + "]", Core.getPmaSessions().values().toString());
        System.out.println("***getPmaSessions()***");
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
    public void setGetSessionCore() {
        JSONArray controlSession = Core.getSessions(core3, coreSessionId);
        assertNull(String.valueOf(controlSession), null);
        System.out.println("***setGetSessionCore()***");
        System.out.println(controlSession);
        System.out.println();
    }

    @Test
    public  void pingCore() {
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
        assertEquals(pmaUrl(), "https://devtest.pathomation.com/test/pma.core.3/");
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
        String firstNonEmptyDir = "_sys_aws_s3";
        String res = Core.getFirstNonEmptyDirectory(null, coreSessionId);
        assertEquals(firstNonEmptyDir, res);
        System.out.println("***getFirstNonEmptyDirectory()***");
        System.out.println(Core.getFirstNonEmptyDirectory(null, coreSessionId));
        System.out.println();
    }
    @Test
    public void getDirectoriesCore() {
        List<String> expectedCoreDirectories = Core.getDirectories("_sys_aws_s3/", coreSessionId);
        int sizeOfDirectories = expectedCoreDirectories.size();
        assertTrue(expectedCoreDirectories.get(0), true);
        assertTrue(expectedCoreDirectories.get(41), true);
        assertEquals(sizeOfDirectories, 43);
    }

    @Test
    public void getSlidesCoreS3() {
        List<String> expectedCoreSlidesS3 = Core.getSlides("_sys_aws_s3/", coreSessionId);
        assertNotNull(expectedCoreSlidesS3);
        System.out.println("***getSlidesS3()***");
        System.out.println(expectedCoreSlidesS3);
        System.out.println();
    }

    @Test
    public void getSlideFileExtensionCore() {
        String res1 = "mrxs";
        String exp1 = Core.getSlideFileExtension("_sys_aws_s3/CMU-2.mrxs");
        String res2 = "szi";
        String exp2 = Core.getSlideFileExtension("_sys_aws_s3/2DollarBill.szi");
        assertEquals(exp1, res1);
        assertEquals(exp2, res2);
        System.out.println("***getSlideFileExtension()***");
        System.out.println(exp1);
        System.out.println(exp2);
        System.out.println();
    }

    @Test
    public void getSlideFileNameCore() {
        String res1 = "CMU-2.mrxs";
        String exp1 = Core.getSlideFileName("_sys_aws_s3/CMU-2.mrxs");
        String res2 = "2DollarBill.szi";
        String exp2 = Core.getSlideFileName("_sys_aws_s3/2DollarBill.szi");
        assertEquals(exp1, res1);
        assertEquals(exp2, res2);
        System.out.println("***getSlideFileName()***");
        System.out.println(exp1);
        System.out.println(exp2);
        System.out.println();
    }

    @Test
    public void getFingerPrintCore() {
        String exp1 = "YrbDCBwr7NlIk50AGFzGODC5IAQ1";
        String exp2 = "PZX3v0NVng0dVEi2ag2ydfBk5rQ1";
        String res1 = Core.getFingerPrint("_sys_aws_s3/CMU-2.mrxs", coreSessionId);
        String res2 = Core.getFingerPrint("_sys_aws_s3/2DollarBill.szi", coreSessionId);
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
    public void lastModifiedAndPhysicalSizeCore() {
        List<String> listSlides = null;
        List<String> listDirectories = Core.getDirectories("_sys_aws_s3/(TO DELETE) test PMATransfer Mehdi", coreSessionId);
        for (String directory : listDirectories) {
            assertNotNull(directory);
            listSlides = Core.getSlides(directory, coreSessionId, true);
            assertNotNull(listSlides);
        }
        for (String slide : listSlides) {
            Object lastModified = Core.getSlideInfo(slide, coreSessionId).get("LastModified");
            Object physicalSize = Core.getSlideInfo(slide, coreSessionId).get("PhysicalSize");
            assertNotNull(listDirectories);
            assertNotNull(slide);
            assertNotNull(lastModified);
            assertNotNull(physicalSize);
            System.out.println("***lastModifiedAndPhysicalSize***");
            System.out.println(lastModified);
            System.out.println(physicalSize);
            System.out.println();
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