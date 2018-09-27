package samples_20;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.pathomation.Core;

import configuration.Config;

public class GettingVersionInformationPMACore extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
			
		String pmaCoreServer = Config.pmaCoreServer;
		
		ServletOutputStream out = response.getOutputStream();
		out.println("<html>");
		out.println("You are running PMA.core version " + Core.getVersionInfo(pmaCoreServer) + " at " + pmaCoreServer + "<br/>");
		
		// what happens when we run it against a bogus URL?
		String version = Core.getVersionInfo("http://nowhere/");
		
		if (version == null) {
			out.println("Unable to detect PMA.core at specified location (http://nowhere/)");
		} else {
			out.println("You are running PMA.core version " + version);
		}
		out.println("</html>");
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		this.doGet(request, response);
	}

}
