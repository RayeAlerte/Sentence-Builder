import java.sql.*;

public class Main {
	public static void main(String[] args) {
		if (args.length == 0) {
			/* TODO: Make instantiate DBMan once and pass it to GUI or CLI */
			DBMan dbMan = new DBMan();
			try {
				dbMan.connect();
				Reporter reporter = new Reporter(dbMan);
				SentenceBuilder app = new SentenceBuilder(dbMan, reporter);
				app.loadDatabaseIntoMemory();
				app.startCLI();
			} catch (SQLException e) {
				e.printStackTrace();
			} finally {
				try {
					dbMan.disconnect();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		} else if (args[0].equals("-gui")) {
			SentenceBuilderApp.launch(SentenceBuilderApp.class, args);
		}
	}
}
