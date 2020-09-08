/*
 *  Copyright 1999-2019 Seata.io Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.seata.rm.datasource.exec;

import co.faao.plugin.starter.dubbo.util.ThreadLocalTools;
import co.faao.plugin.starter.seata.util.ElasticsearchUtil;
import co.faao.plugin.starter.seata.util.SeataXidWorker;
import io.seata.core.constants.Seata;
import io.seata.common.loader.EnhancedServiceLoader;
import io.seata.common.util.StringUtils;
import io.seata.common.util.CollectionUtils;
import io.seata.core.context.RootContext;
import io.seata.core.model.BranchType;
import io.seata.rm.datasource.StatementProxy;
import io.seata.rm.datasource.exec.noseata.DeleteExecutorNoSeata;
import io.seata.rm.datasource.exec.noseata.InsertExecutorNoSeata;
import io.seata.rm.datasource.exec.noseata.NoSeata;
import io.seata.rm.datasource.exec.noseata.UpdateExecutorNoSeata;
import io.seata.sqlparser.SQLRecognizer;
import io.seata.sqlparser.SQLType;
import io.seata.rm.datasource.sql.SQLVisitorFactory;
import io.seata.rm.datasource.sql.struct.TableRecords;
import io.seata.rm.datasource.undo.BranchUndoLog;
import io.seata.rm.datasource.undo.SQLUndoLog;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.commons.lang.time.DateFormatUtils;
import java.util.List;

/**
 * The type Execute template.
 *
 * @author sharajava
 */
public class ExecuteTemplate {

    /**
     * Execute t.
     *
     * @param <T>               the type parameter
     * @param <S>               the type parameter
     * @param statementProxy    the statement proxy
     * @param statementCallback the statement callback
     * @param args              the args
     * @return the t
     * @throws SQLException the sql exception
     */
    public static <T, S extends Statement> T execute(StatementProxy<S> statementProxy,
                                                     StatementCallback<T, S> statementCallback,
                                                     Object... args) throws SQLException {
        return execute(null, statementProxy, statementCallback, args);
    }

    /**
     * Execute t.
     *
     * @param <T>               the type parameter
     * @param <S>               the type parameter
     * @param sqlRecognizers    the sql recognizer list
     * @param statementProxy    the statement proxy
     * @param statementCallback the statement callback
     * @param args              the args
     * @return the t
     * @throws SQLException the sql exception
     */
    public static <T, S extends Statement> T execute(List<SQLRecognizer> sqlRecognizers,
                                                     StatementProxy<S> statementProxy,
                                                     StatementCallback<T, S> statementCallback,
                                                     Object... args) throws SQLException {

        if (!ElasticsearchUtil.isStarted) {//在启动过程中执行的sql,直接执行。类似flyway执行
            // Just work as original statement
            return statementCallback.execute(statementProxy.getTargetStatement(), args);
        }

        //如果配置是seata.enabled=false则关闭分布式事务
        if (!Seata.EWELL_SEATA_STATE_IS_ON || (!RootContext.requireGlobalLock()&& !StringUtils.equals(BranchType.AT.name(), RootContext.getBranchType()))) {
            String dbType = statementProxy.getConnectionProxy().getDbType();
            if (CollectionUtils.isEmpty(sqlRecognizers)) {
                sqlRecognizers = SQLVisitorFactory.get(
                    statementProxy.getTargetSQL(),
                    dbType);
            }
            if (sqlRecognizer != null && "true".equals(System.getProperty("dataTrace"))) {
                NoSeata executor = null;
                if (sqlRecognizers.size() == 1) {
                    SQLRecognizer sqlRecognizer = sqlRecognizers.get(0);
                    switch (sqlRecognizer.getSQLType()) {
                        case INSERT:
                            executor = EnhancedServiceLoader.load(InsertExecutor.class, dbType,
                                new Class[]{StatementProxy.class, StatementCallback.class, SQLRecognizer.class},
                                new Object[]{statementProxy, statementCallback, sqlRecognizer});
                            break;
                        case UPDATE:
                            executor = new UpdateExecutor<>(statementProxy, statementCallback, sqlRecognizer);
                            break;
                        case DELETE:
                            executor = new DeleteExecutor<>(statementProxy, statementCallback, sqlRecognizer);
                            break;
                        case SELECT_FOR_UPDATE:
                            executor = new SelectForUpdateExecutor<>(statementProxy, statementCallback, sqlRecognizer);
                            break;
                        default:
                            executor = new PlainExecutor<>(statementProxy, statementCallback);
                            break;
                    }
                } else {
                    executor = new MultiExecutor<>(statementProxy, statementCallback, sqlRecognizers);
                }
                if (executor != null) {
                    //保存数据前后镜像
                    TableRecords beforeImage = null;
                    try {
                        beforeImage = executor.beforeImage();
                    } catch (Throwable e) {

                    }
                    T result = statementCallback.execute(statementProxy.getTargetStatement(), args);

                    try {
                        TableRecords afterImage = executor.afterImage(beforeImage);
                        SQLUndoLog sQLUndoLog = buildUndoItem(sqlRecognizer, beforeImage, afterImage);
                        //业务数据操作前后插入到es数据库
                        String xid = String.valueOf(SeataXidWorker.xidWorker.getId());
                        BranchUndoLog branchUndoLog = new BranchUndoLog();
                        branchUndoLog.setXid(xid);
                        branchUndoLog.setSqlUndoLogs(new ArrayList<SQLUndoLog>(Arrays.asList(sQLUndoLog)));
                        branchUndoLog.setUserName(ThreadLocalTools.stringThreadLocal.get());
                        branchUndoLog.setExecuteDate(DateFormatUtils.format(new java.util.Date(), "yyyy-MM-dd HH:mm:ss"));
                        ElasticsearchUtil.addData(branchUndoLog);

                        if (statementProxy.getConnection().getAutoCommit()) {// 如果不是事务直接提交
                            ElasticsearchUtil.commitData();
                        }
                    } catch (Throwable e) {

                    }
                    return result;
                } else {
                    // Just work as original statement
                    return statementCallback.execute(statementProxy.getTargetStatement(), args);
                }
            } else {
                // Just work as original statement
                return statementCallback.execute(statementProxy.getTargetStatement(), args);
            }
        }

        String dbType = statementProxy.getConnectionProxy().getDbType();
        if (CollectionUtils.isEmpty(sqlRecognizers)) {
            sqlRecognizers = SQLVisitorFactory.get(
                statementProxy.getTargetSQL(),
                dbType);
        }
        Executor<T> executor;
        if (CollectionUtils.isEmpty(sqlRecognizers)) {
            executor = new PlainExecutor<>(statementProxy, statementCallback);
        } else {
            if (sqlRecognizers.size() == 1) {
                SQLRecognizer sqlRecognizer = sqlRecognizers.get(0);
                switch (sqlRecognizer.getSQLType()) {
                    case INSERT:
                        executor = EnhancedServiceLoader.load(InsertExecutor.class, dbType,
                            new Class[]{StatementProxy.class, StatementCallback.class, SQLRecognizer.class},
                            new Object[]{statementProxy, statementCallback, sqlRecognizer});
                        break;
                    case UPDATE:
                        executor = new UpdateExecutor<>(statementProxy, statementCallback, sqlRecognizer);
                        break;
                    case DELETE:
                        executor = new DeleteExecutor<>(statementProxy, statementCallback, sqlRecognizer);
                        break;
                    case SELECT_FOR_UPDATE:
                        executor = new SelectForUpdateExecutor<>(statementProxy, statementCallback, sqlRecognizer);
                        break;
                    default:
                        executor = new PlainExecutor<>(statementProxy, statementCallback);
                        break;
                }
            } else {
                executor = new MultiExecutor<>(statementProxy, statementCallback, sqlRecognizers);
            }
        }
        T rs;
        try {
            rs = executor.execute(args);
        } catch (Throwable ex) {
            if (!(ex instanceof SQLException)) {
                // Turn other exception into SQLException
                ex = new SQLException(ex);
            }
            throw (SQLException) ex;
        }
        return rs;
    }

    protected static SQLUndoLog buildUndoItem(SQLRecognizer sqlRecognizer, TableRecords beforeImage,
        TableRecords afterImage) {
        SQLType sqlType = sqlRecognizer.getSQLType();
        String tableName = sqlRecognizer.getTableName();
        SQLUndoLog sqlUndoLog = new SQLUndoLog();
        sqlUndoLog.setSqlType(sqlType);
        sqlUndoLog.setTableName(tableName);
        sqlUndoLog.setBeforeImage(beforeImage);
        sqlUndoLog.setAfterImage(afterImage);
        return sqlUndoLog;
    }
}
