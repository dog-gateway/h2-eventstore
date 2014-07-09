/**
 * 
 */
package it.polito.elite.dog.addons.h2eventstore.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import javax.measure.DecimalMeasure;
import javax.measure.Measure;
import javax.measure.quantity.Quantity;

import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

import it.polito.elite.dog.addons.h2eventstore.db.H2Storage;
import it.polito.elite.dog.addons.storage.EventDataPoint;
import it.polito.elite.dog.addons.storage.EventDataStream;
import it.polito.elite.dog.addons.storage.EventDataStreamSet;
import it.polito.elite.dog.core.library.util.LogHelper;

/**
 * @author bonino
 * 
 */
public class NotificationDao
{

	// OSGi logger
	private LogHelper logger;

	// the device dao
	private DeviceDao devDao;

	// The storage layer
	private H2Storage storage;

	// ---- TABLE NAMES
	private final String continuousNotificationTableName = "ContinuousNotification";
	private final String discreteNotificationTableName = "DiscreteNotification";

	// ---- TABLE CREATION QUERIES
	private final String continuousNotificationTableCreateQuery = "CREATE TABLE "
			+ this.continuousNotificationTableName
			+ "(id int(11) NOT NULL AUTO_INCREMENT, timestamp TIMESTAMP, unit VARCHAR(5), "
			+ "value DOUBLE, name VARCHAR(100), params VARCHAR(255), deviceuri VARCHAR(255), "
			+ "PRIMARY KEY(id), FOREIGN KEY (deviceuri) REFERENCES Device(uri) ON DELETE CASCADE);";
	private final String discreteNotificationTableCreateQuery = "CREATE TABLE "
			+ this.discreteNotificationTableName
			+ "(id int(11) NOT NULL AUTO_INCREMENT, timestamp TIMESTAMP, value VARCHAR(100), "
			+ "name VARCHAR(100), deviceuri VARCHAR(255), PRIMARY KEY(id), "
			+ "FOREIGN KEY (deviceuri) REFERENCES Device(uri) ON DELETE CASCADE);";

	// --------- commonly used statements ------------
	private PreparedStatement insertContinuousNotificationStmt;
	private PreparedStatement insertDiscreteNotificationStmt;

	// ---- INSERTION QUERIES
	private final String insertContinuousNotificationQuery = "INSERT INTO "
			+ this.continuousNotificationTableName
			+ "(timestamp, unit, value, name, params, deviceuri) VALUES (?,?,?,?,?,?)";
	private final String insertDiscreteNotificationsQuery = "INSERT INTO "
			+ this.discreteNotificationTableName
			+ "(timestamp, value, name, deviceuri) VALUES (?,?,?,?);";

	/**
	 * 
	 */
	public NotificationDao(final DeviceDao devDao, final H2Storage storage,
			final BundleContext context)
	{
		// init logger
		this.logger = new LogHelper(context);

		// store the connection
		this.storage = storage;

		// store the device DAO instance
		this.devDao = devDao;

		// check and create tables if needed
		this.checkAndCreateTables();

		// prepare the commonly executed statements
		this.prepareCommonStatements();
	}

	private void checkAndCreateTables()
	{
		try
		{
			// check if the Event table exist
			ResultSet tableSet = this.storage
					.getConnection()
					.getMetaData()
					.getTables(this.storage.getConnection().getCatalog(), null,
							this.discreteNotificationTableName.toUpperCase(),
							null);

			if (!tableSet.next())
			{
				// missing event table: create it
				this.storage
						.getConnection()
						.prepareStatement(
								this.discreteNotificationTableCreateQuery)
						.executeUpdate();
				this.logger.log(LogService.LOG_INFO,
						"Schema creation has been successful!");
			}

			tableSet.close();

			// check if the RealEvent table exist
			tableSet = this.storage
					.getConnection()
					.getMetaData()
					.getTables(this.storage.getConnection().getCatalog(), null,
							this.continuousNotificationTableName.toUpperCase(),
							null);

			if (!tableSet.next())
			{
				// missing event table: create it
				this.storage
						.getConnection()
						.prepareStatement(
								this.continuousNotificationTableCreateQuery)
						.executeUpdate();
				this.logger.log(LogService.LOG_INFO,
						"Schema creation has been successful!");
			}

			tableSet.close();
		}
		catch (SQLException e)
		{
			// Log the error
			this.logger.log(LogService.LOG_ERROR,
					"Unable to check / create state db tables");
		}
	}

	/**
	 * Prepares commonly used statements to improve db operation performance.
	 */
	private void prepareCommonStatements()
	{
		// prepare commonly used statements to improve statement execution
		// performance
		try
		{
			this.insertContinuousNotificationStmt = this.storage
					.getConnection().prepareStatement(
							this.insertContinuousNotificationQuery);

			this.insertDiscreteNotificationStmt = this.storage.getConnection()
					.prepareStatement(this.insertDiscreteNotificationsQuery);

		}
		catch (SQLException e)
		{
			// Log the error
			this.logger.log(LogService.LOG_ERROR,
					"Unable to create commonly used prepared statements");
		}

	}
	
	public boolean close()
	{
		boolean isClosed = false;
		// close db connection
		try
		{
			this.insertDiscreteNotificationStmt.close();
			this.insertContinuousNotificationStmt.close();
			isClosed = true;
		}
		catch (SQLException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return isClosed;
	}


	public boolean insertParametricNotification(String deviceURI, Date eventTimestamp,
			Measure<?, ?> eventValue, String notificationName,
			String notificationParams)
	{
		boolean inserted = false;

		try
		{
			

			if (!this.devDao.isDevicePresent(deviceURI))
				this.devDao.insertDevice(deviceURI);

			// Insert the real event in the right table

			// fill the prepared statement
			this.insertContinuousNotificationStmt.setTimestamp(1,
					new Timestamp(eventTimestamp.getTime()));
			DecimalMeasure<? extends Quantity> measure = DecimalMeasure
					.valueOf(eventValue.toString());
			this.insertContinuousNotificationStmt
					.setString(2, eventValue.getUnit().toString());
			this.insertContinuousNotificationStmt.setDouble(3, measure.getValue()
					.doubleValue());
			this.insertContinuousNotificationStmt.setString(4, notificationName);
			this.insertContinuousNotificationStmt.setString(5, notificationParams);
			this.insertContinuousNotificationStmt.setString(6, deviceURI);

			// execute the insert query
			this.insertContinuousNotificationStmt.executeUpdate();
			this.storage.getConnection().commit();

			// turn the insertion flag to true
			inserted = true;
		}
		catch (SQLException e)
		{
			// log the error
			this.logger.log(LogService.LOG_ERROR,
					"Error while storing event data", e);
		}

		return inserted;
	}


	public boolean insertNonParametricNotification(String deviceURI, Date eventTimestamp,
			String eventValue, String name)
	{
		boolean inserted = false;

		try
		{
			// check if the device is already available
			if (!this.devDao.isDevicePresent(deviceURI))
				this.devDao.insertDevice(deviceURI);

			// Insert the real event in the right table

			// fill the prepared statement
			this.insertDiscreteNotificationStmt.setTimestamp(1,
					new Timestamp(eventTimestamp.getTime()));
			this.insertDiscreteNotificationStmt.setString(2, eventValue);
			this.insertDiscreteNotificationStmt.setString(3, name);
			this.insertDiscreteNotificationStmt.setString(4, deviceURI);

			// execute the insert query
			this.insertDiscreteNotificationStmt.executeUpdate();
			this.storage.getConnection().commit();

			// turn the insertion flag to true
			inserted = true;
		}
		catch (SQLException e)
		{
			// log the error
			this.logger.log(LogService.LOG_ERROR,
					"Error while storing event data", e);
		}

		return inserted;
	}

	/**
	 * Gets all the events carrying a measure generated by a given device in the
	 * time frame between startDate and endDate using pagination
	 * 
	 * @param deviceURI
	 *            the deviceURI as a{@link String}
	 * @param startDate
	 *            the start date
	 * @param endDate
	 *            the end date
	 * @param startCount
	 *            the starting count
	 * @param nResults
	 *            the number of results to provide back
	 */

	public EventDataStreamSet getAllDeviceContinuousNotifications(
			String deviceUri, Date startDate, Date endDate, int startCount,
			int nResults)
	{
		// the event data stream set to return
		EventDataStreamSet streamSet = new EventDataStreamSet();

		// the select query
		String allRealEventsQuery = "SELECT * FROM "
				+ this.continuousNotificationTableName
				+ " WHERE deviceuri=? AND timestamp>=? and timestamp<=? ORDER BY name,params ASC LIMIT ? OFFSET ?;";

		// the select statement
		try
		{
			// prepare the select statement
			PreparedStatement allRealEventsStmt = this.storage.getConnection()
					.prepareStatement(allRealEventsQuery);

			// fill the statement data
			allRealEventsStmt.setString(1, deviceUri);
			Timestamp startTimestamp = new Timestamp(startDate.getTime());
			allRealEventsStmt.setTimestamp(2, startTimestamp);
			Timestamp endTimestamp = new Timestamp(endDate.getTime());
			allRealEventsStmt.setTimestamp(3, endTimestamp);
			allRealEventsStmt.setInt(4, nResults);
			allRealEventsStmt.setInt(5, startCount);

			// exec the query
			ResultSet result = allRealEventsStmt.executeQuery();

			// compose the EventStreamDataSet
			String currentName = "";
			String currentParams = "";
			String previousName = "";
			String previousParams = "";

			EventDataStream currentStream = null;
			EventDataPoint currentPoint = null;

			while (result.next())
			{
				currentName = result.getString("name");
				currentParams = result.getString("params");

				// check if a new event stream should be created
				if (((currentName.equals(previousName)) && (!currentParams
						.equals(previousParams)))
						|| (!currentName.equals(previousName)))
				{
					// check if stream exists
					currentStream = new EventDataStream(currentName,
							currentParams, deviceUri);

					// add the stream to the event set
					streamSet.addEventDataStream(currentStream);

					// update the previous values
					previousName = currentName;
					previousParams = currentParams;
				}

				// create the single event data
				currentPoint = new EventDataPoint(new Date(result.getTimestamp(
						"timestamp").getTime()),
						"" + result.getDouble("value"),
						result.getString("unit"));

				// store the event data
				currentStream.addDatapoint(currentPoint);
			}
		}
		catch (SQLException e)
		{
			// log the error
			this.logger.log(LogService.LOG_ERROR,
					"Unable to retrieve sensor events carrying measures", e);
		}

		return streamSet;

	}

	/**
	 * Gets all the events carrying a discrete value generated by a given device
	 * in the time frame between startDate and endDate using pagination
	 * 
	 * @param deviceURI
	 *            the deviceURI as a{@link String}
	 * @param startDate
	 *            the start date
	 * @param endDate
	 *            the end date
	 * @param startCount
	 *            the starting count
	 * @param nResults
	 *            the number of results to provide back
	 */

	public EventDataStreamSet getAllDeviceDiscreteNotifications(
			String deviceUri, Date startDate, Date endDate, int startCount,
			int nResults, boolean aggregated)
	{

		// the event data stream set to return
		EventDataStreamSet streamSet = new EventDataStreamSet();

		// the select query
		String allRealEventsQuery = "SELECT * FROM "+this.discreteNotificationTableName+" WHERE deviceuri=? AND timestamp>=? and timestamp<=?";
		if (aggregated)
			allRealEventsQuery = allRealEventsQuery
					+ " ORDER BY timestamp ASC LIMIT ? OFFSET ?;";
		else
			allRealEventsQuery = allRealEventsQuery
					+ " ORDER BY name,timestamp ASC LIMIT ? OFFSET ?;";

		// the select statement
		try
		{
			// prepare the select statement
			PreparedStatement allRealEventsStmt = this.storage.getConnection()
					.prepareStatement(allRealEventsQuery);

			// fill the statement data
			allRealEventsStmt.setString(1, deviceUri);
			Timestamp startTimestamp = new Timestamp(startDate.getTime());
			allRealEventsStmt.setTimestamp(2, startTimestamp);
			Timestamp endTimestamp = new Timestamp(endDate.getTime());
			allRealEventsStmt.setTimestamp(3, endTimestamp);
			allRealEventsStmt.setInt(4, nResults);
			allRealEventsStmt.setInt(5, startCount);

			// exec the query
			ResultSet result = allRealEventsStmt.executeQuery();

			// compose the EventStreamDataSet
			String currentName = "";
			String previousName = "";

			EventDataStream currentStream = null;
			EventDataPoint currentPoint = null;

			while (result.next())
			{
				if (aggregated)
					currentName = "events";
				else
					currentName = result.getString("name");

				// check if a new event stream should be created
				if (!currentName.equals(previousName))
				{
					// check if stream exists
					currentStream = new EventDataStream(currentName, "",
							deviceUri);

					// add the stream to the event set
					streamSet.addEventDataStream(currentStream);

					// update the previous values only if not aggregated
					previousName = currentName;
				}

				// create the single event data
				currentPoint = new EventDataPoint(new Date(result.getTimestamp(
						"timestamp").getTime()), result.getString("value"), "");

				// store the event data
				currentStream.addDatapoint(currentPoint);
			}
		}
		catch (SQLException e)
		{
			// log the error
			this.logger.log(LogService.LOG_ERROR,
					"Unable to retrieve sensor events", e);
		}

		return streamSet;

	}

	/**
	 * Gets all the events corresponding to the given notification (continuous)
	 * including any restricting parameter, e.g., phaseId=1, in the time frame
	 * between startDate and endDate, using pagination.
	 * 
	 * @param deviceURI
	 *            The deviceURI as a{@link String}
	 * @param notificationName
	 *            The name of the notification for which events must be
	 *            retrieved
	 * @param notificationParams
	 *            The parameter values needed to further specify which
	 *            notification must be matched, in a post-like encoding
	 *            <code>name1=value1&name2=value2&...</code>;
	 * @param startDate
	 *            The start date.
	 * @param endDate
	 *            The end date.
	 * @param startCount
	 *            The starting count
	 * @param nResults
	 *            The number of results to provide back
	 */

	public EventDataStream getSpecificDeviceContinuousNotifications(
			String deviceURI, String notificationName,
			String notificationParams, Date startDate, Date endDate,
			int startCount, int nResults)
	{
		// The event stream to return
		EventDataStream stream = new EventDataStream(notificationName,
				notificationParams, deviceURI);

		// the select query
		// the select query
		String realEventsQuery = "SELECT * FROM "+this.continuousNotificationTableName+" WHERE deviceuri=? AND name=? AND params=? AND timestamp>=? and timestamp<=? ORDER BY name,params LIMIT ? OFFSET ?;";

		// the select statement
		try
		{
			// prepare the select statement
			PreparedStatement realEventsStmt = this.storage.getConnection()
					.prepareStatement(realEventsQuery);

			// fill the statement data
			realEventsStmt.setString(1, deviceURI);
			realEventsStmt.setString(2, notificationName);
			realEventsStmt.setString(3, notificationParams);
			Timestamp startTimestamp = new Timestamp(startDate.getTime());
			realEventsStmt.setTimestamp(4, startTimestamp);
			Timestamp endTimestamp = new Timestamp(endDate.getTime());
			realEventsStmt.setTimestamp(5, endTimestamp);
			realEventsStmt.setInt(6, nResults);
			realEventsStmt.setInt(7, startCount);

			// exec the query
			ResultSet result = realEventsStmt.executeQuery();

			// the current data point
			EventDataPoint currentPoint = null;

			while (result.next())
			{
				currentPoint = new EventDataPoint(new Date(result.getTimestamp(
						"timestamp").getTime()),
						"" + result.getDouble("value"),
						result.getString("unit"));

				stream.addDatapoint(currentPoint);
			}

		}
		catch (SQLException e)
		{
			// log the error
			this.logger.log(LogService.LOG_ERROR,
					"Unable to retrieve sensor data", e);
		}

		return stream;
	}

	/**
	 * Gets all the events corresponding to the given notification (discrete)
	 * with no parameters in the time frame between startDate and endDate, using
	 * pagination.
	 * 
	 * @param deviceURI
	 *            The deviceURI as a{@link String}
	 * @param notificationName
	 *            The name of the notification for which events must be
	 *            retrieved
	 * @param notificationParams
	 *            The parameter values needed to further specify which
	 *            notification must be matched, in a post-like encoding
	 *            <code>name1=value1&name2=value2&...</code>;
	 * @param startDate
	 *            The start date.
	 * @param endDate
	 *            The end date.
	 * @param startCount
	 *            The starting count
	 * @param nResults
	 *            The number of results to provide back
	 */

	public EventDataStream getSpecificDeviceDiscreteNotifications(
			String deviceURI, String notificationName, Date startDate,
			Date endDate, int startCount, int nResults)
	{
		EventDataStream stream = new EventDataStream(notificationName, "",
				deviceURI);

		// the select query
		// the select query
		String realEventsQuery = "SELECT * FROM "+this.discreteNotificationTableName+" WHERE deviceuri=? AND name=? AND timestamp>=? and timestamp<=? ORDER BY name LIMIT ? OFFSET ?;";

		// the select statement
		try
		{
			// prepare the select statement
			PreparedStatement realEventsStmt = this.storage.getConnection()
					.prepareStatement(realEventsQuery);

			// fill the statement data
			realEventsStmt.setString(1, deviceURI);
			realEventsStmt.setString(2, notificationName);
			Timestamp startTimestamp = new Timestamp(startDate.getTime());
			realEventsStmt.setTimestamp(3, startTimestamp);
			Timestamp endTimestamp = new Timestamp(endDate.getTime());
			realEventsStmt.setTimestamp(4, endTimestamp);
			realEventsStmt.setInt(5, nResults);
			realEventsStmt.setInt(6, startCount);

			// exec the query
			ResultSet result = realEventsStmt.executeQuery();

			// the current data point
			EventDataPoint currentPoint = null;

			while (result.next())
			{
				currentPoint = new EventDataPoint(new Date(result.getTimestamp(
						"timestamp").getTime()), result.getString("value"), "");

				stream.addDatapoint(currentPoint);
			}

		}
		catch (SQLException e)
		{
			// log the error
			this.logger.log(LogService.LOG_ERROR,
					"Unable to retrieve sensor data", e);
		}

		return stream;
	}


	public EventDataStream getSpecificDeviceDiscreteNotifications(
			String deviceURI, Set<String> notificationNames,
			String eventStreamName, Date startDate, Date endDate,
			int startCount, int nResults)
	{
		EventDataStream stream = new EventDataStream(eventStreamName, "",
				deviceURI);

		// the select query
		// the select query
		StringBuffer realEventsQueryBuffer = new StringBuffer();
		realEventsQueryBuffer
				.append("SELECT * FROM "+this.discreteNotificationTableName+" WHERE deviceuri=? AND name IN (");

		boolean first = true;
		for (int i = 0; i < notificationNames.size(); i++)
		{
			if (!first)
				realEventsQueryBuffer.append(",");
			else
				first = false;

			realEventsQueryBuffer.append("?");
		}
		realEventsQueryBuffer
				.append(") AND timestamp>=? and timestamp<=? ORDER BY name LIMIT ? OFFSET ?;");

		String realEventsQuery = realEventsQueryBuffer.toString();

		// the select statement
		try
		{
			// prepare the select statement
			PreparedStatement realEventsStmt = this.storage.getConnection()
					.prepareStatement(realEventsQuery);

			int i = 1;
			// fill the statement data
			realEventsStmt.setString(i++, deviceURI);

			// compose the stream name sequence
			for (String notificationName : notificationNames)
			{
				realEventsStmt.setString(i, notificationName);
				i++;
			}

			Timestamp startTimestamp = new Timestamp(startDate.getTime());
			realEventsStmt.setTimestamp(i++, startTimestamp);
			Timestamp endTimestamp = new Timestamp(endDate.getTime());
			realEventsStmt.setTimestamp(i++, endTimestamp);
			realEventsStmt.setInt(i++, nResults);
			realEventsStmt.setInt(i++, startCount);

			// exec the query
			ResultSet result = realEventsStmt.executeQuery();

			// the current data point
			EventDataPoint currentPoint = null;

			while (result.next())
			{
				currentPoint = new EventDataPoint(new Date(result.getTimestamp(
						"timestamp").getTime()), result.getString("value"), "");

				stream.addDatapoint(currentPoint);
			}

		}
		catch (SQLException e)
		{
			// log the error
			this.logger.log(LogService.LOG_ERROR,
					"Unable to retrieve sensor data", e);
		}

		return stream;
	}


	public EventDataStreamSet getSpecificDeviceDiscreteNotifications(
			String deviceURI, Map<String, Set<String>> notificationNames,
			Date startDate, Date endDate, int startCount, int nResults)
	{
		// the data stream set to create
		EventDataStreamSet streamSet = new EventDataStreamSet(deviceURI);

		// iterate over all streams
		for (String streamName : notificationNames.keySet())
		{
			streamSet.addEventDataStream(this
					.getSpecificDeviceDiscreteNotifications(deviceURI,
							notificationNames.get(streamName), streamName,
							startDate, endDate, startCount, nResults));
		}
		return streamSet;
	}
}