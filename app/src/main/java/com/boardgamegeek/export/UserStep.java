package com.boardgamegeek.export;

import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import com.boardgamegeek.R;
import com.boardgamegeek.export.model.PlayerColor;
import com.boardgamegeek.export.model.User;
import com.boardgamegeek.provider.BggContract.Buddies;
import com.boardgamegeek.provider.BggContract.PlayerColors;
import com.boardgamegeek.util.ResolverUtils;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.util.ArrayList;

public class UserStep implements Step {
	@Override
	public String getFileName() {
		return "users.json";
	}

	@Override
	public String getDescription(Context context) {
		return context.getString(R.string.backup_type_user);
	}

	@Override
	public Cursor getCursor(Context context) {
		return context.getContentResolver().query(
			Buddies.CONTENT_URI,
			User.PROJECTION,
			null, null, null);
	}

	@Override
	public void writeJsonRecord(Context context, Cursor cursor, Gson gson, JsonWriter writer) {
		User user = User.fromCursor(cursor);
		user.addColors(context);
		if (user.hasColors()) {
			gson.toJson(user, User.class, writer);
		}
	}

	@Override
	public void initializeImport(Context context) {
	}

	@Override
	public void importRecord(Context context, Gson gson, JsonReader reader) {
		User user = gson.fromJson(reader, User.class);

		ContentResolver resolver = context.getContentResolver();

		String name = user.getName();
		if (ResolverUtils.rowExists(resolver, Buddies.buildBuddyUri(name))) {

			ArrayList<ContentProviderOperation> batch = new ArrayList<>();
			for (PlayerColor color : user.getColors()) {
				if (!TextUtils.isEmpty(color.getColor())) {

					Builder builder;
					final int sortOrder = color.getSort();
					if (ResolverUtils.rowExists(resolver, PlayerColors.buildUserUri(name, sortOrder))) {
						builder = ContentProviderOperation.newUpdate(PlayerColors.buildUserUri(name, sortOrder));
					} else {
						builder = ContentProviderOperation.newInsert(PlayerColors.buildUserUri(name))
							.withValue(PlayerColors.PLAYER_COLOR_SORT_ORDER, sortOrder);
					}
					batch.add(builder.withValue(PlayerColors.PLAYER_COLOR, color.getColor()).build());
				}
			}

			ResolverUtils.applyBatch(context, batch);
		}
	}
}