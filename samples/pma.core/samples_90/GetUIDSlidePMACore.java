package samples_90;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.pathomation.Core;

import configuration.Config;

public class GetUIDSlidePMACore extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		String pmaCoreServer = Config.pmaCoreServer;
		String pmaCoreUser = Config.pmaCoreUser;
		String pmaCorePass = Config.pmaCorePass;
		
		ServletOutputStream out = response.getOutputStream();
		String sessionID = Core.connect(pmaCoreServer, pmaCoreUser, pmaCorePass);
		out.println("<html>");
		if (sessionID == null) {
			out.println("Unable to connect to PMA.core at specified location (" + pmaCoreServer + ")" + "<br/>");
		} else {
			out.println("Successfully connected to PMA.core; sessionID = " + sessionID + "<br/>");
			String dir = Core.getFirstNonEmptyDirectory("/", sessionID);
			out.println("Looking for slides in " + dir + "<br/>");
			for (String slide : Core.getSlides(dir, sessionID)) {
				try {
					out.println(slide + " - " + Core.getUid(slide, sessionID) + "<br/>");
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			// not always needed; depends on whether the client (e.g. browser) still needs to SessionID as well
			Core.disconnect(sessionID);
		}
		out.println("</html>");		
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		this.doGet(request, response);
	}

}


