package com.boardgamegeek.provider;

import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.boardgamegeek.provider.BggContract.CollectionViews;
import com.boardgamegeek.provider.BggDatabase.Tables;
import com.boardgamegeek.util.SelectionBuilder;

public class CollectionFiltersIdProvider extends BaseProvider {

	@Override
	protected SelectionBuilder buildSimpleSelection(Uri uri) {
		long filterId = CollectionViews.getViewId(uri);
		return new SelectionBuilder().table(Tables.COLLECTION_VIEWS).where(CollectionViews._ID + "=?",
				String.valueOf(filterId));
	}

	@Override
	protected void deleteChildren(SQLiteDatabase db, SelectionBuilder builder) {
		new CollectionFiltersProvider().deleteChildren(db, builder);
	}

	@Override
	protected String getPath() {
		return "collectionviews/#";
	}

	@Override
	protected String getType(Uri uri) {
		return CollectionViews.CONTENT_ITEM_TYPE;
	}
}
