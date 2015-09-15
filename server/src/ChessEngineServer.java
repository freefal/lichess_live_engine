import java.io.*;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.json.*;

public class ChessEngineServer {
	public static final String ENGINE = "stockfish";
	public static final String UCI_SETUP = "position fen ";
	public static final int MOVE_TIME = 15000;
	public static final String UCI_START = "go movetime " + MOVE_TIME;
	public static final long THREAD_KILL_PERIOD = 1000*60*1; // 1 minute in milliseconds
	public static HashMap<Integer,ChessEngineThread> threadMap;

	public static void main(String[] args) throws Exception {
		threadMap = new HashMap<Integer,ChessEngineThread>();
		MapCleanerThread mct = new MapCleanerThread();
		mct.start();

		Server server = new Server(8080);
		ServletHandler handler = new ServletHandler();
		handler.addServletWithMapping(StartEvalServlet.class, "/stockfish/evaluate"); //Set the servlet to run.
		handler.addServletWithMapping(GetEvalServlet.class, "/stockfish/geteval"); //Set the servlet to run.
		server.setHandler(handler);    
		server.start();
		server.join();
	}

	@SuppressWarnings("serial")
		public static class StartEvalServlet extends HttpServlet {
			@Override
				protected void doPost (HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
					int clientID = Integer.parseInt(request.getParameter("clientid"));
					String fen = request.getParameter("fen");
					System.out.println("Checking eval position ("+ fen + ") for client (" + clientID + ")");
					ChessEngineThread newThread = new ChessEngineThread(clientID, fen);
					newThread.start();
					ChessEngineThread curThread = threadMap.get(clientID);
					if (curThread != null)
						curThread.die = true;
					threadMap.put(clientID,newThread);

					JSONObject output = new JSONObject();

					try {
						output.put("status", "ok");
					} catch (Exception e) { e.printStackTrace(); }

					response.addHeader("Access-Control-Allow-Origin", "*");
					response.setContentType("application/json");
					response.setStatus(HttpServletResponse.SC_OK);
					PrintWriter writer = response.getWriter();
					writer.println(output.toString());
					writer.flush();
					writer.close();
				}
		}

	@SuppressWarnings("serial")
		public static class GetEvalServlet extends HttpServlet {
			@Override
				protected void doGet (HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
					int clientID = Integer.parseInt(request.getParameter("clientid"));
					System.out.println("Checking eval for client(" + clientID + ")");
					ChessEngineThread cet = threadMap.get(clientID);
					JSONObject output = new JSONObject();

					try {
						if (cet == null) {
							output.put("status", "not found");
						}
						else {
							output.put("status", "found");
							output.put("running", cet.running);
							output.put("bestMove", cet.bestMove);
							output.put("eval", cet.eval);
							output.put("depth", cet.depth);
						}
					} catch (Exception e) { e.printStackTrace(); }
					response.addHeader("Access-Control-Allow-Origin", "*");
					response.setContentType("application/json");
					response.setStatus(HttpServletResponse.SC_OK);
					PrintWriter writer = response.getWriter();
					writer.println(output.toString());
					writer.flush();
					writer.close();
				}
		}
	
	
	
	public static class ChessEngineThread extends Thread {
		public int clientID;
		public String fen;
		public String bestMove;
		public String eval;
		public int depth;
		public long startTime;
		public boolean running = true;
		public int whiteMove = 1; // 1 if white to move and -1 if black to move
		public boolean die = false;

		public ChessEngineThread (int clientID, String fen) {
			this.clientID = clientID;
			this.fen = fen;
			bestMove = null;
			eval = "0";
			depth = 0;
			whiteMove = fen.split(" ")[1].equals("w") ? 1 : -1;
		}

		public void run () {
			running = true;
			startTime = System.currentTimeMillis();
			Process p;
			try {
				p = Runtime.getRuntime().exec(ENGINE);

				PrintWriter writer = new PrintWriter(p.getOutputStream());
				BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

				String line = reader.readLine(); // Throw away the first engine info line
				writer.println(UCI_SETUP + fen);
				writer.println(UCI_START);
				writer.flush();

				while ((line = reader.readLine())!= null) {
					if (die)
						break;
					if (line.indexOf("bestmove") >= 0)
						break;
					if(line.indexOf("seldepth") < 0)
						continue;
					
					int depthLocation = line.indexOf(" depth ");
					int depthStart = depthLocation + 7;
					int depthEnd = line.indexOf(" ", depthStart);
					int depth = Integer.parseInt(line.substring(depthStart, depthEnd));
				
					String eval = "";
					int mateLocation = line.indexOf(" mate ");
					if (mateLocation >= 0) {
						int mateStart = mateLocation + 6;
						int mateEnd = line.indexOf(" ", mateStart);
						int evalInt = Integer.parseInt(line.substring(mateStart, mateEnd));
						eval = "#" + evalInt;
					}
					else {
						int cpLocation = line.indexOf(" cp ");
						int cpStart = cpLocation + 4;
						int cpEnd = line.indexOf(" ", cpStart);
						int evalInt = Integer.parseInt(line.substring(cpStart, cpEnd));
						evalInt *= whiteMove;
						eval = (((double)evalInt)/100.0) + "";
					}
					int pvLocation = line.indexOf(" pv ");
					int pvStart = pvLocation + 4;
					int pvEnd = line.indexOf(" ", pvStart);
					if (pvEnd < 0)
						pvEnd = line.length();
					String bestMove = line.substring(pvStart, pvEnd);

					this.depth = depth;
					this.eval = eval;
					this.bestMove = bestMove;
				}
				writer.println("quit");
				writer.close();
				reader.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			running = false;
		}
	}

	public static class MapCleanerThread extends Thread {
		public void run () {
			while (true) {
				for (ChessEngineThread cet : threadMap.values()) {
					long startTime = cet.startTime;
					long endTime = startTime + THREAD_KILL_PERIOD;
					long curTime = System.currentTimeMillis();
					if (curTime > endTime) {
						threadMap.remove(cet.clientID);
						System.out.println("Killed client (" + cet.clientID + ")");
					}
				}
				try {
					Thread.sleep(THREAD_KILL_PERIOD);
				} catch (Exception e) { e.printStackTrace(); }
			}
		}
	}
}
