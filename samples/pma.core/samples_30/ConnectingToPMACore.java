package samples_30;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.pathomation.Core;

import configuration.Config;

public class ConnectingToPMACore extends HttpServlet {

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
			out.println("Unable to connect to PMA.core at specified location (" + pmaCoreServer + ")");
		} else {
			out.println("Successfully connected to PMA.core; sessionID = " + sessionID);
			// not always needed; depends on whether the client (e.g. browser) still needs to SessionID as well
			Core.disconnect();
		}
		out.println("</html>");	
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		this.doGet(request, response);
	}

}

