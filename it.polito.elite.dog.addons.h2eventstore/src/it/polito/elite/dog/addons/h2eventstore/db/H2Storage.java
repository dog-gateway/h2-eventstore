/*
 * Dog - Addons
 * 
 * Copyright (c) 2013-2014 Claudio Degioanni, Luigi De Russis, Dario Bonino
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package it.polito.elite.dog.addons.h2eventstore.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * @author bonino
 * 
 */
public class H2Storage
{

	// ---- The max insertion batch size
	public static final int MAX_BATCH_SIZE = 1000;

	// the jdbc connection object
	private Connection connection;
	
	// the user name
	private String user;
	
	// the pwd
	private String password;
	
	//the db url
	private String url;

	// TODO: comment this
	public H2Storage(String url, String user, String password)
			throws SQLException
	{
		//store data
		this.url = url;
		this.user = user;
		this.password = password;
		
		// open database connection
		this.connection = DriverManager.getConnection(url, user, password);
	}

	public Connection getConnection() throws SQLException
	{
		//re-create the connection if it was closed by some "external event", e.g., errors on the db, etc.
		if(this.connection.isClosed())
			this.connection = DriverManager.getConnection(this.url, this.user, this.password);
		
		//return the connection
		return this.connection;
	}

	public void close() throws SQLException
	{
		Statement query = this.connection.createStatement();
		query.execute("SHUTDOWN COMPACT");
		
		if(!this.connection.isClosed())
			this.connection.close();
	}

}
