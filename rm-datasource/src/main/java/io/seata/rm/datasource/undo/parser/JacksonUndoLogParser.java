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
package io.seata.rm.datasource.undo.parser;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.databind.ser.std.UUIDSerializer;
import io.seata.common.Constants;
import io.seata.common.loader.LoadLevel;
import io.seata.rm.datasource.undo.BranchUndoLog;
import io.seata.rm.datasource.undo.UndoLogParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * The type Json based undo log parser.
 *
 * @author jsbxyyx
 */
@LoadLevel(name = JacksonUndoLogParser.NAME)
public class JacksonUndoLogParser implements UndoLogParser {

    public static final String NAME = "jackson";

    private static final Logger LOGGER = LoggerFactory.getLogger(JacksonUndoLogParser.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    static {
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MAPPER.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public byte[] getDefaultContent() {
        return "{}".getBytes(Constants.DEFAULT_CHARSET);
    }

    @Override
    public byte[] encode(BranchUndoLog branchUndoLog) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(branchUndoLog);
            return bytes;
        } catch (JsonProcessingException e) {
            LOGGER.error("json encode exception, {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public BranchUndoLog decode(byte[] bytes) {
        try {
            BranchUndoLog branchUndoLog = MAPPER.readValue(bytes, BranchUndoLog.class);
            return branchUndoLog;
        } catch (IOException e) {
            LOGGER.error("json decode exception, {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String writeValueAsString(BranchUndoLog branchUndoLog) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            //类为空时，不要抛异常
            objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
//            objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
            objectMapper.getSerializerProvider().setNullValueSerializer(new JsonSerializer<Object>() {
                @Override
                public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
                     jgen.writeObject("");// 输出孔字符串
                }
            });
            objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
            SimpleModule simpleModule = new SimpleModule();
            simpleModule.addSerializer(Number.class, ToStringSerializer.instance);
            simpleModule.addSerializer(Date.class, ToStringSerializer.instance);
            objectMapper.registerModule(simpleModule);

            String context = objectMapper.writeValueAsString(branchUndoLog);
            return context;
        } catch (JsonProcessingException e) {
            LOGGER.error("json encode exception, {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {

    }

}
