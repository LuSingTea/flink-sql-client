/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.mxb.flink.sql.parser;

import org.apache.calcite.sql.SqlNode;

/**
 * Information about a sql node.
 */
public class SqlNodeInfo {
	private SqlNode sqlNode;
	private String originSql;

	public SqlNodeInfo(SqlNode sqlNode, String originSql) {
		this.sqlNode = sqlNode;
		this.originSql = originSql;
	}

	public SqlNode getSqlNode() {
		return sqlNode;
	}

	public void setSqlNode(SqlNode sqlNode) {
		this.sqlNode = sqlNode;
	}

	public String getOriginSql() {
		return originSql;
	}

	public void setOriginSql(String originSql) {
		this.originSql = originSql;
	}

	@Override
	public String toString() {
		return "SqlNodeInfo{" +
				"sqlNode=" + sqlNode +
				", originSql='" + originSql + '\'' +
				'}';
	}
}
