/*
 * Copyright 2018,2019, Regents of the University of Lancaster
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the
 *    distribution.
 * 
 *  * Neither the name of the University of Lancaster nor the names of
 *    its contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 * Author: Steven Simpson <s.simpson@lancaster.ac.uk>
 */
package uk.ac.lancs.networks.persist;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

class FilterConnection implements Connection {
    protected final Connection base;

    public FilterConnection(Connection base) {
        this.base = base;
    }

    @Override
    public boolean isWrapperFor(Class<?> arg0) throws SQLException {
        return base.isWrapperFor(arg0);
    }

    @Override
    public <T> T unwrap(Class<T> arg0) throws SQLException {
        return base.unwrap(arg0);
    }

    @Override
    public void abort(Executor arg0) throws SQLException {
        base.abort(arg0);
    }

    @Override
    public void clearWarnings() throws SQLException {
        base.clearWarnings();
    }

    @Override
    public void close() throws SQLException {
        base.close();
    }

    @Override
    public void commit() throws SQLException {
        base.commit();
    }

    @Override
    public Array createArrayOf(String arg0, Object[] arg1)
        throws SQLException {
        return base.createArrayOf(arg0, arg1);
    }

    @Override
    public Blob createBlob() throws SQLException {
        return base.createBlob();
    }

    @Override
    public Clob createClob() throws SQLException {
        return base.createClob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        return base.createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return base.createSQLXML();
    }

    @Override
    public Statement createStatement() throws SQLException {
        return base.createStatement();
    }

    @Override
    public Statement createStatement(int arg0, int arg1) throws SQLException {
        return base.createStatement(arg0, arg1);
    }

    @Override
    public Statement createStatement(int arg0, int arg1, int arg2)
        throws SQLException {
        return createStatement(arg0, arg1, arg2);
    }

    @Override
    public Struct createStruct(String arg0, Object[] arg1)
        throws SQLException {
        return base.createStruct(arg0, arg1);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return base.getAutoCommit();
    }

    @Override
    public String getCatalog() throws SQLException {
        return base.getCatalog();
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return base.getClientInfo();
    }

    @Override
    public String getClientInfo(String arg0) throws SQLException {
        return base.getClientInfo(arg0);
    }

    @Override
    public int getHoldability() throws SQLException {
        return base.getHoldability();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return base.getMetaData();
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return base.getNetworkTimeout();
    }

    @Override
    public String getSchema() throws SQLException {
        return base.getSchema();
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return base.getTransactionIsolation();
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return base.getTypeMap();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return base.getWarnings();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return base.isClosed();
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return base.isReadOnly();
    }

    @Override
    public boolean isValid(int arg0) throws SQLException {
        return base.isValid(arg0);
    }

    @Override
    public String nativeSQL(String arg0) throws SQLException {
        return base.nativeSQL(arg0);
    }

    @Override
    public CallableStatement prepareCall(String arg0) throws SQLException {
        return base.prepareCall(arg0);
    }

    @Override
    public CallableStatement prepareCall(String arg0, int arg1, int arg2)
        throws SQLException {
        return base.prepareCall(arg0, arg1, arg2);
    }

    @Override
    public CallableStatement prepareCall(String arg0, int arg1, int arg2,
                                         int arg3)
        throws SQLException {
        return base.prepareCall(arg0, arg1, arg2, arg3);
    }

    @Override
    public PreparedStatement prepareStatement(String arg0)
        throws SQLException {
        return base.prepareStatement(arg0);
    }

    @Override
    public PreparedStatement prepareStatement(String arg0, int arg1)
        throws SQLException {
        return base.prepareStatement(arg0, arg1);
    }

    @Override
    public PreparedStatement prepareStatement(String arg0, int[] arg1)
        throws SQLException {
        return base.prepareStatement(arg0, arg1);
    }

    @Override
    public PreparedStatement prepareStatement(String arg0, String[] arg1)
        throws SQLException {
        return base.prepareStatement(arg0, arg1);
    }

    @Override
    public PreparedStatement prepareStatement(String arg0, int arg1, int arg2)
        throws SQLException {
        return base.prepareStatement(arg0, arg1, arg2);
    }

    @Override
    public PreparedStatement prepareStatement(String arg0, int arg1, int arg2,
                                              int arg3)
        throws SQLException {
        return base.prepareStatement(arg0, arg1, arg2, arg3);
    }

    @Override
    public void releaseSavepoint(Savepoint arg0) throws SQLException {
        base.releaseSavepoint(arg0);
    }

    @Override
    public void rollback() throws SQLException {
        base.rollback();
    }

    @Override
    public void rollback(Savepoint arg0) throws SQLException {
        base.rollback(arg0);
    }

    @Override
    public void setAutoCommit(boolean arg0) throws SQLException {
        base.setAutoCommit(arg0);
    }

    @Override
    public void setCatalog(String arg0) throws SQLException {
        base.setCatalog(arg0);
    }

    @Override
    public void setClientInfo(Properties arg0) throws SQLClientInfoException {
        base.setClientInfo(arg0);
    }

    @Override
    public void setClientInfo(String arg0, String arg1)
        throws SQLClientInfoException {
        base.setClientInfo(arg0, arg1);
    }

    @Override
    public void setHoldability(int arg0) throws SQLException {
        base.setHoldability(arg0);
    }

    @Override
    public void setNetworkTimeout(Executor arg0, int arg1)
        throws SQLException {
        base.setNetworkTimeout(arg0, arg1);
    }

    @Override
    public void setReadOnly(boolean arg0) throws SQLException {
        base.setReadOnly(arg0);
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        return base.setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(String arg0) throws SQLException {
        return base.setSavepoint(arg0);
    }

    @Override
    public void setSchema(String arg0) throws SQLException {
        base.setSchema(arg0);
    }

    @Override
    public void setTransactionIsolation(int arg0) throws SQLException {
        base.setTransactionIsolation(arg0);
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> arg0) throws SQLException {
        base.setTypeMap(arg0);
    }
}
