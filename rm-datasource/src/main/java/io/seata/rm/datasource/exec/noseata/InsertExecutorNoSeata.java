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
package io.seata.rm.datasource.exec.noseata;

import io.seata.rm.datasource.StatementProxy;
import io.seata.rm.datasource.exec.InsertExecutor;
import io.seata.rm.datasource.exec.StatementCallback;
import io.seata.rm.datasource.sql.SQLRecognizer;
import io.seata.rm.datasource.sql.struct.TableRecords;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The type Insert executor.
 *
 * @param <T> the type parameter
 * @param <S> the type parameter
 * @author yuanguoyao
 */
public class InsertExecutorNoSeata<T, S extends Statement> extends InsertExecutor<T, S> implements NoSeata {

    private StatementProxy statementProxy;

    /**
     * Instantiates a new Insert executor.
     *
     * @param statementProxy the statement proxy
     * @param statementCallback the statement callback
     * @param sqlRecognizer the sql recognizer
     */
    public InsertExecutorNoSeata(StatementProxy statementProxy, StatementCallback statementCallback,
        SQLRecognizer sqlRecognizer) {
        super(statementProxy, statementCallback, sqlRecognizer);
        this.statementProxy = statementProxy;
    }

    @Override
    public TableRecords beforeImage() throws SQLException {
        return super.beforeImage();
    }

    @Override
    public TableRecords afterImage(TableRecords beforeImage) throws SQLException {
        return super.afterImage(beforeImage);
    }
}
