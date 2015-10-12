package it.unibz.krdb.sql;

/*
* #%L
* ontop-obdalib-core
* %%
* Copyright (C) 2009 - 2014 Free University of Bozen-Bolzano
* %%
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* 
*      http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
* #L%
*/


import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retrieves the database metadata (table schema and database constraints) 
 * 
 * @author Roman Kontchakov
 *
 */

/**
 * 
 * HSQLDB
 * 
 * http://www.hsqldb.org/doc/1.8/src/org/hsqldb/jdbc/jdbcDatabaseMetaData.html
 * 
 * HSQLDB treats unquoted identifiers as case insensitive in SQL but stores them in upper case; 
 * it treats quoted identifiers as case sensitive and stores them verbatim. All jdbcDatabaseMetaData 
 * methods perform case-sensitive comparison between name (pattern) arguments and the corresponding 
 * identifier values as they are stored in the database.
 *
 * HSQLDB uses the standard SQL identifier quote character (the double quote character); 
 * getIdentifierQuoteString() always returns ".
 * 
 * 
 * 
 * PostgreSQL
 * ----------
 * 
 * http://www.postgresql.org/docs/9.1/static/sql-syntax-lexical.html
 * 
 * Unquoted names are always folded to lower (!) case.
 * 
 * Quoted identifier is formed by enclosing an arbitrary sequence of characters in double-quotes ("). 
 * (To include a double quote, write two double quotes.) 
 * 
 * A variant of quoted identifiers allows including escaped Unicode characters identified by their code points. 
 * This variant starts with U& (upper or lower case U followed by ampersand) immediately before the opening 
 * double quote, without any spaces in between, for example U&"foo".
 * 
 * 
 * H2 
 * --
 * 
 * http://h2database.com/html/grammar.html
 * 
 * Names are not case sensitive (but it appears that the upper-case is the canonical form).
 * 
 * Quoted names are case sensitive, and can contain spaces. 
 * Two double quotes can be used to create a single double quote inside an identifier.
 * 
 * 
 * MS SQL Server
 * -------------
 * 
 * https://msdn.microsoft.com/en-us/library/ms378535(v=sql.110).aspx
 * 
 * When using the Microsoft JDBC Driver with a SQL Server database, 
 * getIdentifierQuoteString returns double quotation marks ("").
 * 
 * 
 * MySQL
 * -----
 * 
 * http://dev.mysql.com/doc/refman/5.0/en/identifier-case-sensitivity.html
 * 
 * How table and database names are stored on disk and used in MySQL is affected 
 * by the lower_case_table_names system variable, which you can set when starting mysqld.
 * 
 * Column, index, and stored routine names are not case sensitive on any platform, nor are column aliases. 
 * 
 */

public class DBMetadataExtractor {

	private static final boolean printouts = false;
	
	private static Logger log = LoggerFactory.getLogger(DBMetadataExtractor.class);

	
	public static DBMetadata createDummyMetadata() {
		return createDummyMetadata("dummy class");
	}
	
	public static DBMetadata createDummyMetadata(String driver_class) {
		return new DBMetadata(driver_class, null, null, new QuotedIDFactoryStandardSQL());
	}
	
	
	/**
	 * Creates database metadata description (but does not load metadata) 
	 * 
	 * @return The database metadata object.
	 * @throws SQLException 
	 */

	public static DBMetadata createMetadata(Connection conn) throws SQLException  {
		
		final DatabaseMetaData md = conn.getMetaData();
		String productName = md.getDatabaseProductName();
		
		// ROMAN (7 Oct 2015): these ifs are to be replaced by checking boolean flags in md
		
		QuotedIDFactory idfac;
		if (productName.contains("Oracle")) 
			idfac = new QuotedIDFactoryStandardSQL();
		else if (productName.contains("DB2")) 
			idfac = new QuotedIDFactoryStandardSQL();
		else if (productName.contains("H2") || productName.contains("HSQL")) 
			idfac = new QuotedIDFactoryStandardSQL();
		else if (productName.contains("PostgreSQL")) 
			// Postgres treats unquoted identifiers as lower-case
			idfac = new QuotedIDFactoryLowerCase();
		else if (productName.contains("SQL Server"))  // MS SQL Server
			idfac = new QuotedIDFactoryIdentity();
		else if (productName.contains("MySQL"))  //  MySQL
			idfac = new QuotedIDFactoryMySQL(); 
		else // For other database engines
			idfac = new QuotedIDFactoryIdentity();
		
		DBMetadata metadata = new DBMetadata(md.getDriverName(), md.getDriverVersion(), productName, idfac);
		
		return metadata;	
	}
	
	/**
	 * Retrieves the database metadata (table schema and database constraints) 
	 * 
	 * This method either uses the given list of tables or 
	 *    if it is null then it retrieves all the complete list of tables from 
	 *    the connection metadata
	 * 
	 * @return The database metadata object.
	 */

	public static void loadMetadata(DBMetadata metadata, Connection conn, Set<RelationID> realTables) throws SQLException {
		
		if (printouts)
			System.out.println("GETTING METADATA WITH " + conn + " ON " + realTables);
		
		final DatabaseMetaData md = conn.getMetaData();
		List<RelationID> tableList;
		DatatypeNormalizer dt = DefaultTypeFixer;
		
		if (md.getDatabaseProductName().contains("Oracle")) {
			String defaultSchema = getOracleDefaultOwner(conn);
			if (realTables == null || realTables.isEmpty())
				tableList = getTableList(conn, new OracleRelationListProvider(defaultSchema), metadata.getQuotedIDFactory());
			else
				tableList = getTableList(defaultSchema, realTables, metadata.getQuotedIDFactory());
			
			dt = OracleTypeFixer;
		} 
		else if (md.getDatabaseProductName().contains("DB2")) {
			if (realTables == null || realTables.isEmpty()) 
				tableList = getTableList(conn, DB2RelationListProvider, metadata.getQuotedIDFactory());
			else 
				tableList = getTableList(null, realTables, metadata.getQuotedIDFactory());
		}  
		else if (md.getDatabaseProductName().contains("SQL Server")) { // MS SQL Server
			if (realTables == null || realTables.isEmpty()) 
				tableList = getTableList(conn, MSSQLServerRelationListProvider, metadata.getQuotedIDFactory());
			else
				tableList = getTableList(null, realTables, metadata.getQuotedIDFactory());
 		} 
		else if (md.getDatabaseProductName().contains("H2") || 
				md.getDatabaseProductName().contains("HSQL") || 
				md.getDatabaseProductName().contains("PostgreSQL")) {
			if (realTables == null || realTables.isEmpty()) 
				tableList = getTableListDefault(md);
			else 
				tableList = getTableList(null, realTables, metadata.getQuotedIDFactory());
		}
		else {
			// For other database engines, i.e. MySQL
			if (realTables == null || realTables.isEmpty()) 
				tableList = getTableListDefault(md);
			else
				tableList = getTableList(null, realTables, metadata.getQuotedIDFactory());
			
			dt = MySQLTypeFixer;
		}
		
		for (RelationID id : tableList) {
			TableDefinition table = metadata.createTable(id);
			getTableColumns(md, table, dt);
			getPrimaryKey(md, table);
			getUniqueAttributes(md, table);
			if (printouts)
				System.out.println("  T " + table.getID() + ": " + table);
		}	
		// FKs are processed separately because they are not local 
		// (refer to two relations), which requires all relations 
		// to have been constructed 
		for (RelationID id : tableList) { 
			TableDefinition table = metadata.getTable(id);
			getForeignKeys(md, table, metadata);
		}
	}
	
	
	/**
	 * Retrieve the normalized list of tables from a given list of RelationJSQL
	 */

	private static List<RelationID> getTableList(String defaultTableSchema, Set<RelationID> realTables, QuotedIDFactory idfac) throws SQLException {

		List<RelationID> fks = new LinkedList<>();
		for (RelationID table : realTables) {
			// defaultTableSchema is non-empty only for Oracle and DUAL is a special Oracle table 
			if (table.hasSchema() || (defaultTableSchema == null) || table.getTableName().equals("DUAL"))
				fks.add(table);
			else {
				RelationID qualifiedTableId = idfac.createRelationFromString(defaultTableSchema, table.getTableNameSQLRendering());
				fks.add(qualifiedTableId);
			}
		}
		return fks;
	}
	
	
	
	/**
	 * Retrieve the table and view list from the JDBC driver (works for most database engines, e.g., MySQL and PostgreSQL)
	 */
	private static List<RelationID> getTableListDefault(DatabaseMetaData md) throws SQLException {
		List<RelationID> tables = new LinkedList<>();
		try (ResultSet rsTables = md.getTables(null, null, null, new String[] { "TABLE", "VIEW" })) {	
			while (rsTables.next()) {
				//String tblCatalog = rsTables.getString("TABLE_CAT");
				RelationID id = RelationID.createRelationIdFromDatabaseRecord(rsTables.getString("TABLE_SCHEM"), rsTables.getString("TABLE_NAME"));
				tables.add(id);
			}
		} 
		return tables;
	}
	
	/**
	 * Retrieve metadata for a specific database engine
	 */
	private static List<RelationID> getTableList(Connection conn, RelationListProvider relationListProvider, QuotedIDFactory idfac) throws SQLException {
		
		List<RelationID> fks = new LinkedList<>();
		try (Statement stmt = conn.createStatement()) {
			// Obtain the relational objects (i.e., tables and views) 
			try (ResultSet rs = stmt.executeQuery(relationListProvider.getQuery())) {
				while (rs.next()) 
					fks.add(relationListProvider.getTableDefinition(rs));
			}
		}
		return fks; 
	}
	
	
	
	
	
	private static String getOracleDefaultOwner(Connection conn) throws SQLException {
		// Obtain the table owner (i.e., schema name) 
		String loggedUser = "SYSTEM"; // default value
		try (Statement stmt = conn.createStatement()) {
			try (ResultSet resultSet = stmt.executeQuery("SELECT user FROM dual")) {
				if (resultSet.next()) {
					loggedUser = resultSet.getString("user");
				}
			}
		}
		return loggedUser.toUpperCase();
	}
	

	
	private interface RelationListProvider {
		String getQuery();
		RelationID getTableDefinition(ResultSet rs) throws SQLException;
	}
	
	
	/**
	 * Table list for Oracle
	 */
	
	private static final class OracleRelationListProvider implements RelationListProvider {
		
		private final String defaultTableOwner; 
		
		public OracleRelationListProvider(String defaultTableOwner) {
			this.defaultTableOwner = defaultTableOwner;
		}
		
		@Override
		public String getQuery() {
			// ROMAN (19 Sep 2015): not clear why the outer query is needed
			return "SELECT object_name FROM ( " +
				   "SELECT table_name as object_name FROM user_tables WHERE " +
			       "   NOT table_name LIKE 'MVIEW$_%' AND " +
			       "   NOT table_name LIKE 'LOGMNR_%' AND " +
			       "   NOT table_name LIKE 'AQ$_%' AND " +
			       "   NOT table_name LIKE 'DEF$_%' AND " +
			       "   NOT table_name LIKE 'REPCAT$_%' AND " +
			       "   NOT table_name LIKE 'LOGSTDBY$%' AND " +
			       "   NOT table_name LIKE 'OL$%' " +
			       "UNION " +
			       "SELECT view_name as object_name FROM user_views WHERE " +
			       "   NOT view_name LIKE 'MVIEW_%' AND " +
			       "   NOT view_name LIKE 'LOGMNR_%' AND " +
			       "   NOT view_name LIKE 'AQ$_%')";
		}

		@Override
		public RelationID getTableDefinition(ResultSet rs) throws SQLException {
			return RelationID.createRelationIdFromDatabaseRecord(defaultTableOwner, rs.getString("object_name"));
		}
	};
	
	/**
	 * Table list for DB2 database engine
	 */
	
	private static final RelationListProvider DB2RelationListProvider = new RelationListProvider() {
		@Override
		public String getQuery() {
			return "SELECT TABSCHEMA, TABNAME " +
			       "FROM SYSCAT.TABLES " +
			       "WHERE OWNERTYPE='U' AND (TYPE='T' OR TYPE='V') " +
			       "     AND TBSPACEID IN (SELECT TBSPACEID FROM SYSCAT.TABLESPACES WHERE TBSPACE LIKE 'USERSPACE%')";
		}

		@Override
		public RelationID getTableDefinition(ResultSet rs) throws SQLException {
			return RelationID.createRelationIdFromDatabaseRecord(rs.getString("TABSCHEMA"), rs.getString("TABNAME"));
		}
	};

	
	/**
	 * Table list for MS SQL Server database engine
	 */

	private static final RelationListProvider MSSQLServerRelationListProvider = new RelationListProvider() {
		@Override
		public String getQuery() {
			return "SELECT TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME " +
					"FROM INFORMATION_SCHEMA.TABLES " +
					"WHERE TABLE_TYPE='BASE TABLE' OR TABLE_TYPE='VIEW'";
		}

		@Override
		public RelationID getTableDefinition(ResultSet rs) throws SQLException {
			//String tblCatalog = rs.getString("TABLE_CATALOG");
			return RelationID.createRelationIdFromDatabaseRecord(rs.getString("TABLE_SCHEMA"), rs.getString("TABLE_NAME"));
		}
	};
	
	
	
	/**
	 * A method of fixing discrepancies in datatype correspondence
	 */
	
	private interface DatatypeNormalizer {
		int getCorrectedDatatype(int dataType, String typeName);
	}
	
	private static final DatatypeNormalizer DefaultTypeFixer = new DatatypeNormalizer() {
		@Override
		public int getCorrectedDatatype(int dataType, String typeName) {					
			return dataType;
		}};
		
	private static final DatatypeNormalizer MySQLTypeFixer = new DatatypeNormalizer() {
		@Override
		public int getCorrectedDatatype(int dataType, String typeName) {					
			// Fix for MySQL YEAR (see Table 5.2 at 
			//        http://dev.mysql.com/doc/connector-j/en/connector-j-reference-type-conversions.html)
			if (dataType ==  Types.DATE && typeName.equals("YEAR")) 
				return -10000;
			return dataType;
		}};	
			
	private static final DatatypeNormalizer OracleTypeFixer = new DatatypeNormalizer() {
		@Override
		public int getCorrectedDatatype(int dataType, String typeName) {					
			
			//TODO 
			// Oracle bug here - wrong automatic typing - Date vs DATETIME - driver ojdbc16-11.2.0.3
			// Oracle returns 93 for DATE SQL types, but this corresponds to 
			// TIMESTAMP. This causes a wrong typing to xsd:dateTime and later
			// parsing errors. To avoid this bug manually type the column in the
			// mapping. This may be a problem of the driver, try with other version
			// I tried oracle thin driver ojdbc16-11.2.0.3
			//
			// ROMAN (19 Sep 2015): see 
			//    http://www.oracle.com/technetwork/database/enterprise-edition/jdbc-faq-090281.html#08_01
			
			if (dataType == Types.TIMESTAMP && typeName.equals("DATE")) 
				dataType = Types.DATE;
			return dataType;
		}};
	
	
	
	private static void getTableColumns(DatabaseMetaData md, RelationDefinition table, DatatypeNormalizer dt) throws SQLException {
		// needed for checking uniqueness of lower-case versions of columns names
		//  (only in getOtherMetadata)
		//Set<String> tableColumns = new HashSet<>();
		
		RelationID id = table.getID();
		
		try (ResultSet rs = md.getColumns(/*table.getCatalog()*/null, id.getSchemaName(), id.getTableName(), null)) {
			//if (rsColumns == null) 
			//	return;			
			while (rs.next()) {
				if (printouts)
					System.out.println("         " + rs.getString("TABLE_CAT") + "." + rs.getString("TABLE_SCHEM") + "." + 
								rs.getString("TABLE_NAME") + "." + rs.getString("COLUMN_NAME"));
				// ROMAN (21 Sep 2015): very careful with columns of the same name in tables in different schemas
				QuotedID columnName = QuotedID.createFromDatabaseRecord(rs.getString("COLUMN_NAME"));
				// columnNoNulls, columnNullable, columnNullableUnknown 
				boolean isNullable = rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
				
				String typeName = rs.getString("TYPE_NAME");
				int dataType = dt.getCorrectedDatatype(rs.getInt("DATA_TYPE"), typeName);
				
				table.addAttribute(columnName, dataType, typeName, isNullable);
				// Check if the columns are unique regardless their letter cases
				//if (!tableColumns.add(columnName.toLowerCase())) {
				//	throw new RuntimeException("The system cannot process duplicate table columns!");
				//}
			}
		}
	}
	
	/**
	 * Prints column names of a given table.
     *
	 */
	private static void displayColumnNames(DatabaseMetaData dbMetadata, 
			Connection connection, ResultSet rsColumns, 
			String tableSchema, String tableName) throws SQLException {

		log.debug("=============== COLUMN METADATA ========================");
		
		if (dbMetadata.getDatabaseProductName().contains("DB2")) {
			 // Alternative solution for DB2 to print column names
		     // Queries directly the system table SysCat.Columns
			//  ROMAN (20 Sep 2015): use PreparedStatement instead?
			try (Statement st = connection.createStatement()) {
		        String sqlQuery = String.format("SELECT colname, typename \n FROM SysCat.Columns \n" + 
		        								"WHERE tabname = '%s' AND tabschema = '%s'", tableName, tableSchema);
		        
		        try (ResultSet results = st.executeQuery(sqlQuery)) {
			        while (results.next()) {
			            log.debug("Column={} Type={}", results.getString("colname"), results.getString("typename"));
			        }
		        }
			}
		}
		else {
			 // Generic procedure based on JDBC
			ResultSetMetaData columnMetadata = rsColumns.getMetaData();
			int count = columnMetadata.getColumnCount();
			for (int j = 0; j < count; j++) {
			    String columnName = columnMetadata.getColumnName(j + 1);
			    String value = rsColumns.getString(columnName);
			    log.debug("Column={} Type={}", columnName, value);
			}				
		}
	}
	
	
	

	/** 
	 * Retrieves the primary key for the table 
	 * 
	 */
	private static void getPrimaryKey(DatabaseMetaData md, RelationDefinition table) throws SQLException {
		UniqueConstraint.Builder pk = UniqueConstraint.builder(table);
		String pkName = "";
		RelationID id = table.getID();
		try (ResultSet rsPrimaryKeys = md.getPrimaryKeys(null, id.getSchemaName(), id.getTableName())) {
			while (rsPrimaryKeys.next()) {
				pkName = rsPrimaryKeys.getString("PK_NAME");
				QuotedID colName = QuotedID.createFromDatabaseRecord(rsPrimaryKeys.getString("COLUMN_NAME"));
				pk.add(table.getAttribute(colName));
			}
		} 
		table.setPrimaryKey(pk.build(pkName));
	}
	
	/**
	 * Retrieves the unique attributes(s) 
	 * @param md
	 * @return
	 * @throws SQLException 
	 */
	private static void getUniqueAttributes(DatabaseMetaData md, RelationDefinition table) throws SQLException {

		RelationID id = table.getID();
		// extracting unique 
		try (ResultSet rsUnique = md.getIndexInfo(null, id.getSchemaName(), id.getTableName(), true, true)) {
			System.out.println("UNIQUENESS " + id);
			while (rsUnique.next()) {
				String colName = rsUnique.getString("COLUMN_NAME");
				String tableName = rsUnique.getString("TABLE_NAME");
				String tableSchema = rsUnique.getString("TABLE_SCHEM");
				String indexName = rsUnique.getString("INDEX_NAME");
				if (printouts)
					System.out.println("  " + tableSchema + "." + tableName + ":"  + indexName + " with " + colName);
				// ROMAN (12 Oct 2015): this is unfinished 
			}
		}
	}
	
	/** 
	 * Retrieves the foreign keys for the table 
	 * 
	 */
	private static void getForeignKeys(DatabaseMetaData md, RelationDefinition table, DBMetadata metadata) throws SQLException {
		
		RelationID id = table.getID();
		
		try (ResultSet rsForeignKeys = md.getImportedKeys(null, id.getSchemaName(), id.getTableName())) {
			ForeignKeyConstraint.Builder builder = null;
			String currentName = "";
			while (rsForeignKeys.next()) {
				String pkSchemaName = rsForeignKeys.getString("PKTABLE_SCHEM");
				String pkTableName = rsForeignKeys.getString("PKTABLE_NAME");
				RelationID pkTable = RelationID.createRelationIdFromDatabaseRecord(pkSchemaName, pkTableName);
				TableDefinition ref = metadata.getTable(pkTable);
				String name = rsForeignKeys.getString("FK_NAME");
				if (!currentName.equals(name)) {
					if (builder != null) 
						table.addForeignKeyConstraint(builder.build(currentName));
					
					builder = new ForeignKeyConstraint.Builder(table, ref);
					currentName = name;
				}
				QuotedID colName = QuotedID.createFromDatabaseRecord(rsForeignKeys.getString("FKCOLUMN_NAME"));
				QuotedID pkColumnName = QuotedID.createFromDatabaseRecord(rsForeignKeys.getString("PKCOLUMN_NAME"));
				if (ref != null)
					builder.add(table.getAttribute(colName), ref.getAttribute(pkColumnName));
				else {
					System.err.println("Cannot find table: " + pkTableName + " for " + name);
					builder = null;
				}
			}
			if (builder != null)
				table.addForeignKeyConstraint(builder.build(currentName));
		} 
	}
}
