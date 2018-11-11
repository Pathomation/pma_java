package samples_10;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.pathomation.*;

import configuration.Config;

public class IdentifyingPMACore extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		
		String pmaCoreServer = Config.pmaCoreServer;

		ServletOutputStream out = response.getOutputStream();
		out.println("<html>");
		// testing actual "full" PMA.core instance that may or may not be out there
		out.println("Are you running PMA.start(PMA.core.lite) at " + pmaCoreServer + " ? " + ((Core.isLite(pmaCoreServer) != null && 
				(Core.isLite(pmaCoreServer) == true))  ? "Yes!" : "no :-(") + "<br />");
		out.println(
				"Are you running PMA.start(PMA.core.lite) at http://nowhere ? "
						+ ((Core.isLite("http://nowhere") != null && (Core.isLite("http://nowhere") == true)) ? "Yes!" : "no :-("));
		out.println("</html>");

	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		this.doGet(request, response);
	}

}
