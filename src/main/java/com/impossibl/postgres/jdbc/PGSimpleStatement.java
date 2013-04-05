package com.impossibl.postgres.jdbc;

import static com.impossibl.postgres.jdbc.ErrorUtils.chainWarnings;
import static com.impossibl.postgres.jdbc.Exceptions.INVALID_COMMAND_FOR_GENERATED_KEYS;
import static com.impossibl.postgres.jdbc.Exceptions.NOT_SUPPORTED;
import static com.impossibl.postgres.jdbc.Exceptions.NO_RESULT_COUNT_AVAILABLE;
import static com.impossibl.postgres.jdbc.Exceptions.NO_RESULT_SET_AVAILABLE;
import static com.impossibl.postgres.jdbc.SQLTextUtils.appendReturningClause;
import static java.util.Arrays.asList;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.Collections;

import com.impossibl.postgres.protocol.PrepareCommand;
import com.impossibl.postgres.protocol.ResultField;
import com.impossibl.postgres.types.Type;

class PGSimpleStatement extends PGStatement {
	
	SQLText batchCommands;

	public PGSimpleStatement(PGConnection connection, int type, int concurrency, int holdability) {
		super(connection, type, concurrency, holdability, null, Collections.<ResultField>emptyList());
	}

	SQLWarning prepare(SQLText sqlText) throws SQLException {

		PrepareCommand prep = connection.getProtocol().createPrepare(null, sqlText.toString(), Collections.<Type>emptyList());
		
		SQLWarning warningChain = connection.execute(prep, true);
		
		resultFields = prep.getDescribedResultFields();
		
		return warningChain;
	}
	
	boolean execute(SQLText sqlText) throws SQLException {
		
		if(processEscapes) {
			SQLTextEscapes.processEscapes(sqlText, connection);
		}
		
		if(sqlText.getStatementCount() > 1) {
			
			return executeSimple(sqlText.toString());
			
		}
		else {
						
			SQLWarning prepWarningChain = prepare(sqlText);
				
			boolean res = executeStatement(null, Collections.<Type>emptyList(), Collections.<Object>emptyList());
		
			warningChain = chainWarnings(prepWarningChain, warningChain);
		
			return res;
			
		}
	}

	@Override
	public boolean execute(String sql) throws SQLException {
		checkClosed();

		SQLText sqlText = connection.parseSQL(sql);
		
		return execute(sqlText);
	}

	@Override
	public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
		checkClosed();
		
		SQLText sqlText = connection.parseSQL(sql);
		
		if(autoGeneratedKeys != RETURN_GENERATED_KEYS) {
			return execute(sqlText);
		}
		
		if(appendReturningClause(sqlText) == false) {
			throw INVALID_COMMAND_FOR_GENERATED_KEYS;
		}
		
		execute(sqlText);
		
		generatedKeysResultSet = getResultSet();
		
		return false;
	}

	@Override
	public boolean execute(String sql, int[] columnIndexes) throws SQLException {
		checkClosed();

		throw NOT_SUPPORTED;
	}

	@Override
	public boolean execute(String sql, String[] columnNames) throws SQLException {
		checkClosed();
		
		SQLText sqlText = connection.parseSQL(sql);
		
		if(appendReturningClause(sqlText, asList(columnNames)) == false) {
			throw INVALID_COMMAND_FOR_GENERATED_KEYS;
		}
		
		execute(sqlText);
		
		generatedKeysResultSet = getResultSet();
		
		return false;
	}

	@Override
	public ResultSet executeQuery(String sql) throws SQLException {

		if(execute(sql) == false) {
			throw NO_RESULT_SET_AVAILABLE;
		}

		return getResultSet();
	}

	@Override
	public int executeUpdate(String sql) throws SQLException {
		
		if(execute(sql) == true) {
			throw NO_RESULT_COUNT_AVAILABLE;
		}
		
		return getUpdateCount();
	}

	@Override
	public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
		
		if(execute(sql, autoGeneratedKeys) == true) {
			throw NO_RESULT_COUNT_AVAILABLE;
		}
		
		return getUpdateCount();
	}

	@Override
	public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {

		if(execute(sql, columnIndexes) == true) {
			throw NO_RESULT_COUNT_AVAILABLE;
		}
		
		return getUpdateCount();
	}

	@Override
	public int executeUpdate(String sql, String[] columnNames) throws SQLException {

		if(execute(sql, columnNames) == true) {
			throw NO_RESULT_COUNT_AVAILABLE;
		}
		
		return getUpdateCount();
	}

	@Override
	public void addBatch(String sql) throws SQLException {
		
		SQLText sqlText = connection.parseSQL(sql);

		if(batchCommands == null) {
			batchCommands = sqlText;
		}
		else {
			batchCommands.addStatements(sqlText);
		}
	}

	@Override
	public void clearBatch() throws SQLException {
		
		batchCommands = null;
	}

	@Override
	public int[] executeBatch() throws SQLException {
		
		execute(batchCommands);
		
		int[] counts = new int[resultBatches.size()];
		
		for(int c=0; c < resultBatches.size(); ++c) {
			
			if(resultBatches.get(c).rowsAffected != null) {
				counts[c] = (int)(long)resultBatches.get(c).rowsAffected;
			}
			else {
				counts[c] = Statement.SUCCESS_NO_INFO;
			}
		}
		
		command = null;
		resultBatches = null;
		
		return counts;
	}

}
