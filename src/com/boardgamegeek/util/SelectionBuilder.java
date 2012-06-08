/*
 * Copyright (C) 2010 The Android Open Source Project
 *
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
 */

// FROM com.google.android.apps.iosched.util;

package com.boardgamegeek.util;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper for building selection clauses for {@link SQLiteDatabase}. Each appended clause is combined using {@code AND}.
 * This class is <em>not</em> thread safe.
 */
public class SelectionBuilder {
	private static final String TAG = "SelectionBuilder";
	private static final boolean LOGV = false;

	private String mTable = null;
	private Map<String, String> mProjectionMap = new HashMap<String, String>();
	private StringBuilder mSelection = new StringBuilder();
	private ArrayList<String> mSelectionArgs = new ArrayList<String>();
	private String mGroupBy = null;
	private String mHaving = null;

	/**
	 * Reset any internal state, allowing this builder to be recycled.
	 */
	public SelectionBuilder reset() {
		mTable = null;
		mSelection.setLength(0);
		mSelectionArgs.clear();
		mGroupBy = null;
		return this;
	}

	public SelectionBuilder whereEquals(String selection, String selectionArg) {
		return where(selection + "=?", selectionArg);
	}

	public SelectionBuilder whereEquals(String selection, int selectionArg) {
		return where(selection + "=?", String.valueOf(selectionArg));
	}

	public SelectionBuilder whereEquals(String selection, long selectionArg) {
		return where(selection + "=?", String.valueOf(selectionArg));
	}

	/**
	 * Append the given selection clause to the internal state. Each clause is surrounded with parenthesis and combined
	 * using {@code AND}.
	 */
	public SelectionBuilder where(String selection, String... selectionArgs) {
		if (TextUtils.isEmpty(selection)) {
			if (selectionArgs != null && selectionArgs.length > 0) {
				throw new IllegalArgumentException("Valid selection required when including arguments=");
			}

			// Shortcut when clause is empty
			return this;
		}

		if (mSelection.length() > 0) {
			mSelection.append(" AND ");
		}
		mSelection.append("(").append(selection).append(")");

		if (selectionArgs != null) {
			for (String arg : selectionArgs) {
				mSelectionArgs.add(arg);
			}
		}

		return this;
	}

	public SelectionBuilder table(String table) {
		mTable = table;
		return this;
	}

	private void assertTable() {
		if (mTable == null) {
			throw new IllegalStateException("Table not specified");
		}
	}

	private void assertHaving() {
		if (!TextUtils.isEmpty(mHaving) && TextUtils.isEmpty(mGroupBy)) {
			throw new IllegalStateException("Group by must be specified for Having clause");
		}
	}

	public SelectionBuilder mapToTable(String column, String table) {
		if (column.equals(BaseColumns._ID)) {
			mapToTable(column, table, column);
		} else {
			mProjectionMap.put(column, table + "." + column);
		}
		return this;
	}

	public SelectionBuilder mapToTable(String column, String table, String fromColumn) {
		mProjectionMap.put(column, table + "." + column + " AS " + fromColumn);
		return this;
	}

	public SelectionBuilder map(String fromColumn, String toClause) {
		mProjectionMap.put(fromColumn, toClause + " AS " + fromColumn);
		return this;
	}

	public SelectionBuilder groupBy(String... groupArgs) {
		if (groupArgs != null) {
			// mapColumns(groupArgs);
			mGroupBy = new String();
			for (String arg : groupArgs) {
				if (mGroupBy.length() > 0) {
					mGroupBy += ", ";
				}
				mGroupBy += arg;
			}
		}

		return this;
	}

	public SelectionBuilder having(String having) {
		mHaving = having;
		return this;
	}

	/**
	 * Return selection string for current internal state.
	 * 
	 * @see #getSelectionArgs()
	 */
	public String getSelection() {
		return mSelection.toString();
	}

	/**
	 * Return selection arguments for current internal state.
	 * 
	 * @see #getSelection()
	 */
	public String[] getSelectionArgs() {
		return mSelectionArgs.toArray(new String[mSelectionArgs.size()]);
	}

	private void mapColumns(String[] columns) {
		for (int i = 0; i < columns.length; i++) {
			final String target = mProjectionMap.get(columns[i]);
			if (target != null) {
				columns[i] = target;
			}
		}
	}

	@Override
	public String toString() {
		return "SelectionBuilder[table=" + mTable + ", selection=" + getSelection() + ", selectionArgs="
				+ Arrays.toString(getSelectionArgs()) + ", groupBy=" + mGroupBy + ", having=" + mHaving + "]";
	}

	/**
	 * Execute query using the current internal state as {@code WHERE} clause.
	 */
	public Cursor query(SQLiteDatabase db, String[] columns, String orderBy) {
		assertHaving();
		return query(db, columns, mGroupBy, mHaving, orderBy, null);
	}

	/**
	 * Execute query using the current internal state as {@code WHERE} clause.
	 */
	public Cursor query(SQLiteDatabase db, String[] columns, String groupBy, String having, String orderBy, String limit) {
		assertTable();
		if (columns != null) {
			mapColumns(columns);
		}
		if (LOGV) {
			Log.v(TAG, "query(columns=" + Arrays.toString(columns) + ") " + this);
		}
		return db.query(mTable, columns, getSelection(), getSelectionArgs(), groupBy, having, orderBy, limit);
	}

	/**
	 * Execute update using the current internal state as {@code WHERE} clause.
	 */
	public int update(SQLiteDatabase db, ContentValues values) {
		assertTable();
		if (LOGV) {
			Log.v(TAG, "update() " + this);
		}
		return db.update(mTable, values, getSelection(), getSelectionArgs());
	}

	/**
	 * Execute delete using the current internal state as {@code WHERE} clause.
	 */
	public int delete(SQLiteDatabase db) {
		assertTable();
		if (LOGV) {
			Log.v(TAG, "delete() " + this);
		}
		String selection = getSelection();
		if (TextUtils.isEmpty(selection)) {
			// this forces delete to return the count
			selection = "1";
		}
		return db.delete(mTable, selection, getSelectionArgs());
	}
}
