package com.boardgamegeek.ui;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import com.boardgamegeek.R;
import com.boardgamegeek.events.LocationSelectedEvent;
import com.boardgamegeek.events.LocationSortChangedEvent;
import com.boardgamegeek.events.LocationsCountChangedEvent;
import com.boardgamegeek.provider.BggContract.Plays;
import com.boardgamegeek.sorter.LocationsSorter;
import com.boardgamegeek.sorter.LocationsSorterFactory;
import com.boardgamegeek.ui.model.Location;
import com.boardgamegeek.ui.widget.ContentLoadingProgressBar;
import com.boardgamegeek.ui.widget.RecyclerSectionItemDecoration;
import com.boardgamegeek.util.AnimationUtils;
import com.boardgamegeek.util.fabric.SortEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import timber.log.Timber;

public class LocationsFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
	private static final int TOKEN = 0;
	private LocationsAdapter adapter;
	private LocationsSorter sorter;
	private boolean isListShown = false;
	private final List<Location> locations = new ArrayList<>();

	Unbinder unbinder;
	@BindView(R.id.empty_container) ViewGroup emptyContainer;
	@BindView(R.id.progress) ContentLoadingProgressBar progressBar;
	@BindView(R.id.list_container) View listContainer;
	@BindView(android.R.id.list) RecyclerView listView;

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		setSort(LocationsSorterFactory.TYPE_DEFAULT);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_locations, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		unbinder = ButterKnife.bind(this, view);

		listView.setLayoutManager(new LinearLayoutManager(getContext()));
		listView.setHasFixedSize(true);
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		if (unbinder != null) unbinder.unbind();
	}

	@Override
	public void onStart() {
		super.onStart();
		EventBus.getDefault().register(this);
	}

	@Override
	public void onStop() {
		super.onStop();
		EventBus.getDefault().unregister(this);
	}

	@SuppressWarnings("unused")
	@Subscribe
	public void onEvent(LocationSortChangedEvent event) {
		setSort(event.getSortType());
	}

	public void setSort(int sortType) {
		if (sorter == null || sorter.getType() != sortType) {
			SortEvent.log("Locations", String.valueOf(sortType));
			sorter = LocationsSorterFactory.create(getContext(), sortType);
			LoaderManager.getInstance(this).restartLoader(TOKEN, getArguments(), this);
		}
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle data) {
		return new CursorLoader(getContext(), Plays.buildLocationsUri(), Location.PROJECTION, null, null, sorter.getOrderByClause());
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
		if (getActivity() == null) return;

		int token = loader.getId();
		if (token == TOKEN) {
			setListAdapter(new LocationsAdapter(getActivity()));

			locations.clear();
			if (cursor.moveToFirst()) {
				do {
					locations.add(Location.fromCursor(cursor));
				} while (cursor.moveToNext());
			}

			adapter.changeData(locations);

			RecyclerSectionItemDecoration sectionItemDecoration =
				new RecyclerSectionItemDecoration(
					getResources().getDimensionPixelSize(R.dimen.recycler_section_header_height),
					true,
					getSectionCallback(locations, sorter));
			while (listView.getItemDecorationCount() > 0) {
				listView.removeItemDecorationAt(0);
			}
			listView.addItemDecoration(sectionItemDecoration);

			EventBus.getDefault().postSticky(new LocationsCountChangedEvent(cursor.getCount()));
			progressBar.hide();
		} else {
			Timber.d("Query complete, Not Actionable: %s", token);
			cursor.close();
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		adapter.changeData(null);
	}

	public void setListAdapter(LocationsAdapter adapter) {
		boolean hadAdapter = this.adapter != null;
		this.adapter = adapter;
		if (listView != null) {
			listView.setAdapter(adapter);
			if (!isListShown && !hadAdapter) {
				// The list was hidden, and previously didn't have an adapter. It is now time to show it.
				setListShown(listView.getWindowToken() != null);
			}
		}
	}

	private void setListShown(boolean animate) {
		emptyContainer.setVisibility(View.GONE);
		if (isListShown) return;
		isListShown = true;
		AnimationUtils.fadeIn(listContainer, animate);
	}

	public class LocationsAdapter extends RecyclerView.Adapter<LocationsAdapter.LocationsViewHolder> {
		private final LayoutInflater inflater;
		private List<Location> locations;

		public LocationsAdapter(Context context) {
			inflater = LayoutInflater.from(context);
		}

		public void changeData(List<Location> locations) {
			this.locations = locations;
			notifyDataSetChanged();
		}

		@Override
		public int getItemCount() {
			return locations == null ? 0 : locations.size();
		}

		@NonNull
		@Override
		public LocationsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View row = inflater.inflate(R.layout.row_text_2, parent, false);
			return new LocationsViewHolder(row);
		}

		@Override
		public void onBindViewHolder(LocationsViewHolder holder, int position) {
			if (locations == null) return;

			final Location location = locations.get(position);

			if (TextUtils.isEmpty(location.getName())) {
				holder.name.setText(R.string.no_location);
			} else {
				holder.name.setText(location.getName());
			}
			holder.quantity.setText(getResources().getQuantityString(R.plurals.plays_suffix, location.getPlayCount(), location.getPlayCount()));

			holder.itemView.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					EventBus.getDefault().postSticky(new LocationSelectedEvent(location.getName()));
				}
			});
		}

		class LocationsViewHolder extends RecyclerView.ViewHolder {
			@BindView(android.R.id.title) TextView name;
			@BindView(android.R.id.text1) TextView quantity;

			public LocationsViewHolder(View view) {
				super(view);
				ButterKnife.bind(this, view);
			}
		}
	}

	private RecyclerSectionItemDecoration.SectionCallback getSectionCallback(final List<Location> locations, final LocationsSorter sorter) {
		return new RecyclerSectionItemDecoration.SectionCallback() {
			@Override
			public boolean isSection(int position) {
				if (locations == null || locations.size() == 0) return false;
				if (position == 0) return true;
				String thisLetter = sorter.getSectionText(locations.get(position));
				String lastLetter = sorter.getSectionText(locations.get(position - 1));
				return !thisLetter.equals(lastLetter);
			}

			@Override
			public CharSequence getSectionHeader(int position) {
				if (locations == null || locations.size() == 0) return "-";
				return sorter.getSectionText(locations.get(position));
			}
		};
	}
}
