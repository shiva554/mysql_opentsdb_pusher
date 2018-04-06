package com.cox.sweng.clienttest;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.apache.log4j.BasicConfigurator;

import com.cox.sweng.client.PoolingHttpClient;
import com.cox.sweng.client.builder.MetricBuilder;
import com.cox.sweng.client.response.SimpleHttpResponse;

public class ClientTest {

	public static void main(String[] args) throws Exception {

		Logger logger = Logger.getLogger(ClientTest.class.getName());

		String csvFile = args[0];

		FileHandler logFile = new FileHandler(args[1], true);
		logger.addHandler(logFile);

		String line = "";
		String cvsSplitBy = ",";
		float llg;
		String avgofn = args[2];
		int i = 0;
		long lastTimeStamp = -1;
		int startingMin = -1;
		int endMin = 0;
		// tsdb
		MetricBuilder builder = MetricBuilder.getInstance();
		PoolingHttpClient client = new PoolingHttpClient();
		Properties prop = new Properties();
		// mysql
		Connection con = null;
		Statement stmt = null;
		try {

			Map<String, Integer> latencyData = new Hashtable<String, Integer>();
			Map<String, Integer> jitterData = new Hashtable<String, Integer>();
			Map<String, Integer> lossData = new Hashtable<String, Integer>();
			Map<String, Integer> counter = new Hashtable<String, Integer>();
			Map<String, Long> timestampData = new Hashtable<String, Long>();

			InputStream input = null;
		//	input = new FileInputStream("/etc/cox/pnh/cox-pnh-mysql-tsdb-service/config.properties");
			input = new FileInputStream("src/deb/cfg/config.properties");
			prop.load(input);
			if (prop.getProperty("db").equals("mysql")) {
				/*Class.forName(prop.getProperty("jdbcdrivers"));
				con = DriverManager.getConnection(prop.getProperty("url"), prop.getProperty("uname"),
						prop.getProperty("pwd"));
				stmt = con.createStatement();*/
				System.out.println("mysql connection created");
			}

			BufferedReader br = new BufferedReader(new FileReader(csvFile));

			while ((line = br.readLine()) != null) {
				// use comma as separator
				String[] Column = line.split(cvsSplitBy);

				if (i == 0) {
					/*
					 * ignore header.
					 */

				} else {
					String src = Column[2].substring(0, 4);
					String Column3 = Column[3].replaceAll("\"", "");
					String service = Column3.substring(Column3.length() - 2).toLowerCase();
					String path = Column[5].substring(Column[5].length() - 1).toLowerCase();
					String dstn = Column[8].substring(0, 4);
					String latency1 = Column[9] != null && !Column[9].trim().isEmpty() ? Column[9].trim() : "0";
					String jitterAvg = Column[10] != null && !Column[10].trim().isEmpty() ? Column[10].trim() : "0";
					String loss = Column[11] != null && !Column[11].trim().isEmpty() ? Column[11].trim() : "0";

					String ts = Column[0];

					String parsedDate = ts.replaceAll("\"", "");

					DateFormat formatter = new SimpleDateFormat("MM/dd/yy hh:mm");
					Date date = formatter.parse(parsedDate);

					DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("MM/dd/yy HH:mm:ss");
					LocalDateTime dateTime = LocalDateTime.parse(parsedDate, formatter1);

					lastTimeStamp = date.getTime(); // it taken first record

					if (startingMin == -1) {

						startingMin = dateTime.getHour() * 60 + dateTime.getMinute();

					}

					if ((dateTime.getHour() * 60 + dateTime.getMinute()) >= startingMin + Integer.parseInt(avgofn)) { // here
																														// we
																														// compare

						if (prop.getProperty("db") == "mysql") {
							sqlupdateAverage(stmt, logger, latencyData, jitterData, lossData, counter, timestampData,
									logFile);
							latencyData.clear();
							jitterData.clear();
							lossData.clear();
							counter.clear();

						} else {
							tsdbupdateAverage(client, prop, builder, logger, latencyData, jitterData, lossData, counter,
									timestampData, logFile);
							latencyData.clear();
							jitterData.clear();
							lossData.clear();
							counter.clear();

						}
					}
						

						timestampData.put("TIMESTAMP", lastTimeStamp);

						String uniqueKey = src + "-" + dstn + "-" + service + "-" + path;

						if (latencyData.containsKey(uniqueKey)) {

							latencyData.put(uniqueKey, latencyData.get(uniqueKey) + Integer.parseInt(latency1));
							jitterData.put(uniqueKey, jitterData.get(uniqueKey) + Integer.parseInt(jitterAvg));
							lossData.put(uniqueKey, lossData.get(uniqueKey) + Integer.parseInt(loss));
							counter.put(uniqueKey, counter.get(uniqueKey) + 1);

						} else {
							latencyData.put(uniqueKey, Integer.parseInt(latency1));
							jitterData.put(uniqueKey, Integer.parseInt(jitterAvg));
							lossData.put(uniqueKey, Integer.parseInt(loss));
							counter.put(uniqueKey, 1);

						}

					}
					i++;

				}
			
			if (prop.getProperty("db").equals("mysql")) {
				sqlupdateAverage(stmt, logger, latencyData, jitterData, lossData, counter, timestampData, logFile);
			} else {
				tsdbupdateAverage(client, prop, builder, logger, latencyData, jitterData, lossData, counter,
						timestampData, logFile);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (logFile != null) {
				try {
					logFile.close();
				} catch (Exception e) {
					/* ignored */}
			}

			if (con != null) {
				try {
					con.close();
				} catch (SQLException e) {
					/* ignored */}
			}

		}

		System.exit(0);

	}

	public static void tsdbupdateAverage(PoolingHttpClient client, Properties prop, MetricBuilder builder,
			Logger logger, Map<String, Integer> latencyData, Map<String, Integer> jitterData,
			Map<String, Integer> lossData, Map<String, Integer> counter, Map<String, Long> timeStampData,
			FileHandler logFile) throws Exception {

		Integer j = 0;
		for (Map.Entry m : latencyData.entrySet()) {
			j++;
			String key = (String) m.getKey();
			Float averageLatency = (float) (latencyData.get(key) / counter.get(key));
			Float averageJitter = (float) (jitterData.get(key) / counter.get(key));
			Float averageloss = (float) (lossData.get(key) / counter.get(key));
			Long lastTimeStamp = (long) timeStampData.get("TIMESTAMP");

			String[] cols = key.split("-");
			Long tstamp = lastTimeStamp / 1000;

			if (averageLatency > 0) {
				Float avg = averageLatency / 1000;
				System.out.println("timestamp" + tstamp + " avg" + avg);
				builder.addMetric("performance.latency.test1").setDataPoint(tstamp, avg).addTag("src", cols[0])
						.addTag("service", cols[2]).addTag("path", cols[3]).addTag("dst", cols[1]);
				//httpRes(builder, client, prop);

			} else

			{
				logger.setLevel(Level.WARNING);
				SimpleFormatter formatter2 = new SimpleFormatter();
				logFile.setFormatter(formatter2);
				logger.warning("This test instanceid : " + tstamp + " src : " + cols[0] + " dest : " + cols[1]
						+ " path : " + cols[3] + " not pushed to openTSDB");
			}

			builder.addMetric("performance.jitter.test").setDataPoint(tstamp, averageJitter).addTag("src", cols[0])
					.addTag("service", cols[2]).addTag("path", cols[3]).addTag("dst", cols[1]);
			//httpRes(builder, client, prop);

			builder.addMetric("performance.loss.test").setDataPoint(tstamp, averageloss).addTag("src", cols[0])
					.addTag("service", cols[2]).addTag("path", cols[3]).addTag("dst", cols[1]);
			//httpRes(builder, client, prop);
		}
	}

	public static void httpRes(MetricBuilder build, PoolingHttpClient client, Properties prop) {
		try {
			SimpleHttpResponse response = client.doPost(prop.getProperty("opentsdb"), build.build());
			build.getMetrics().clear();
		} catch (IOException e) {
			e.printStackTrace();
			build.getMetrics().clear();
		}
	}

	public static void sqlupdateAverage(Statement stmt, Logger logger, Map<String, Integer> latencyData,
			Map<String, Integer> jitterData, Map<String, Integer> lossData, Map<String, Integer> counter,
			Map<String, Long> timeStampData, FileHandler logFile) throws Exception {

		Integer j = 0;
		for (Map.Entry m : latencyData.entrySet()) {
			j++;
			String key = (String) m.getKey();
			Float averageLatency = (float) (latencyData.get(key) / counter.get(key));
			Float averageJitter = (float) (jitterData.get(key) / counter.get(key));
			Float averageloss = (float) (lossData.get(key) / counter.get(key));
			Long lastTimeStamp = (long) timeStampData.get("TIMESTAMP");

			String[] cols = key.split("-");

			if (averageLatency > 0) {
				try {
					long timestamp = lastTimeStamp / 1000;
					String tstamp = Long.toString(timestamp);
					Float avgLatency = averageLatency / 1000;
					System.out.println("" + tstamp + "," + cols[0] + "," + cols[1] + "," + cols[2] + "," + cols[3] + ","
							+ avgLatency);
					/*stmt.executeUpdate(
							"INSERT INTO PERFORMANCE_LATENCY(timestamp,verifierid,peerverifier,slaName,pathid,endEndDelayAvg) VALUES ('"
									+ tstamp + "','" + cols[0] + "," + cols[1] + "," + cols[2] + "," + cols[3] + "',"
									+ avgLatency + ")");*/

					logger.setLevel(Level.INFO);
					SimpleFormatter logformatter = new SimpleFormatter();
					logFile.setFormatter(logformatter);
					logger.info("" + tstamp + "," + cols[0] + "," + cols[1] + "," + cols[2] + "," + cols[3] + ","
							+ avgLatency);
				} catch (Exception e) {
					System.out.println(e);
				}

			} else

			{

				logger.setLevel(Level.WARNING);
				SimpleFormatter logformatter = new SimpleFormatter();
				logFile.setFormatter(logformatter);
				logger.warning("This test instanceid : " + lastTimeStamp + " src : " + cols[0] + " dest : " + cols[1]
						+ " not pushed to mysql");
			}

			try {
				long timestamp = lastTimeStamp / 1000;
				String tstamp = Long.toString(timestamp);
				/*stmt.executeUpdate(
						"INSERT INTO PERFORMANCE_JITTER(timestamp,verifierid,peerverifier,slaName,pathid,jitterAverageToResponder) VALUES ('"
								+ tstamp + "','" + cols[0] + "," + cols[1] + "," + cols[2] + "," + cols[3] + "',"
								+ averageJitter + ")");*/
				logger.setLevel(Level.INFO);
				SimpleFormatter logformatter = new SimpleFormatter();
				logFile.setFormatter(logformatter);
				logger.info("" + tstamp + "," + cols[0] + "," + cols[1] + "," + cols[2] + "," + cols[3] + ","
						+ averageJitter);
			} catch (Exception e) {
				System.out.println(e);

			}

			try {
				long timestamp = lastTimeStamp / 1000;
				String tstamp = Long.toString(timestamp);
				/*stmt.executeUpdate(
						"INSERT INTO PERFORMANCE_LOSS(timestamp,verifierid,peerverifier,slaName,pathid,lostPacketsToResponder) VALUES ('"
								+ tstamp + "','" + cols[0] + "," + cols[1] + "," + cols[2] + "," + cols[3] + "',"
								+ averageloss + ")");
*/
				logger.setLevel(Level.INFO);
				SimpleFormatter logformatter = new SimpleFormatter();
				logFile.setFormatter(logformatter);
				logger.info("" + timestamp + "," + cols[0] + "," + cols[1] + "," + cols[2] + "," + cols[3] + ","
						+ averageloss);

			} catch (Exception e) {
				System.out.println(e);
			}

		}

	}

}
