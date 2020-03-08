package com.boardgamegeek.ui;

import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.text.TextUtils;
import android.transition.AutoTransition;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;

import com.boardgamegeek.R;
import com.boardgamegeek.auth.AccountUtils;
import com.boardgamegeek.extensions.DoubleUtils;
import com.boardgamegeek.provider.BggContract;
import com.boardgamegeek.provider.BggContract.Collection;
import com.boardgamegeek.provider.BggContract.Games;
import com.boardgamegeek.provider.BggContract.PlayPlayers;
import com.boardgamegeek.provider.BggContract.Plays;
import com.boardgamegeek.ui.widget.PlayStatRow;
import com.boardgamegeek.ui.widget.PlayerStatView;
import com.boardgamegeek.ui.widget.ScoreGraphView;
import com.boardgamegeek.util.AnimationUtils;
import com.boardgamegeek.util.CursorUtils;
import com.boardgamegeek.util.DateTimeUtils;
import com.boardgamegeek.util.PreferencesUtils;
import com.boardgamegeek.util.SelectionBuilder;
import com.boardgamegeek.util.StringUtils;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.collection.ArrayMap;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import butterknife.BindView;
import butterknife.BindViews;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import timber.log.Timber;

public class GamePlayStatsFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
	private static final String KEY_GAME_ID = "GAME_ID";
	private static final String KEY_HEADER_COLOR = "HEADER_COLOR";
	private static final DecimalFormat SCORE_FORMAT = new DecimalFormat("0.##");
	private static final DateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

	private int gameId;
	private int playingTime;
	private double personalRating;
	private boolean gameOwned;
	private Stats stats;
	private final SparseBooleanArray selectedItems = new SparseBooleanArray();

	private Unbinder unbinder;
	@BindView(R.id.progress) ContentLoadingProgressBar progressView;
	@BindView(R.id.empty) View emptyView;
	@BindView(R.id.data) View dataView;
	@BindView(R.id.table_play_count) TableLayout playCountTable;
	@BindView(R.id.chart_play_count) HorizontalBarChart playCountChart;
	@BindView(R.id.card_score) View scoresCard;
	@BindView(R.id.low_score) TextView lowScoreView;
	@BindView(R.id.average_score) TextView averageScoreView;
	@BindView(R.id.average_win_score) TextView averageWinScoreView;
	@BindView(R.id.score_graph) ScoreGraphView scoreGraphView;
	@BindView(R.id.high_score) TextView highScoreView;
	@BindView(R.id.card_players) View playersCard;
	@BindView(R.id.list_players) LinearLayout playersList;
	@BindView(R.id.table_dates) TableLayout datesTable;
	@BindView(R.id.table_play_time) TableLayout playTimeTable;
	@BindView(R.id.card_locations) View locationsCard;
	@BindView(R.id.table_locations) TableLayout locationsTable;
	@BindView(R.id.table_advanced) TableLayout advancedTable;
	@BindViews({
		R.id.header_play_count,
		R.id.header_scores,
		R.id.header_players,
		R.id.header_dates,
		R.id.header_play_time,
		R.id.header_locations,
		R.id.header_advanced
	}) List<TextView> colorizedHeaders;
	@BindViews({
		R.id.score_help,
		R.id.players_skill_help
	}) List<ImageView> colorizedIcons;

	private Transition playerTransition;
	@ColorInt private int headerColor;
	@ColorInt private int[] playCountColors;
	private int[] bggColors;

	public static GamePlayStatsFragment newInstance(int gameId, @ColorInt int headerColor) {
		Bundle args = new Bundle();
		args.putInt(KEY_GAME_ID, gameId);
		args.putInt(KEY_HEADER_COLOR, headerColor);
		GamePlayStatsFragment fragment = new GamePlayStatsFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		readBundle(getArguments());

		View rootView = inflater.inflate(R.layout.fragment_game_play_stats, container, false);
		unbinder = ButterKnife.bind(this, rootView);

		if (headerColor != Color.TRANSPARENT) {
			for (TextView view : colorizedHeaders) {
				view.setTextColor(headerColor);
			}
			for (ImageView view : colorizedIcons) {
				view.setColorFilter(headerColor);
			}
		}

		if (getContext() != null) {
			bggColors = new int[] {
				ContextCompat.getColor(getContext(), R.color.orange),
				ContextCompat.getColor(getContext(), R.color.dark_blue),
				ContextCompat.getColor(getContext(), R.color.light_blue)
			};
		}

		playCountChart.setDescription(null);
		playCountChart.setDrawGridBackground(false);
		playCountChart.getAxisLeft().setEnabled(false);

		YAxis yAxis = playCountChart.getAxisRight();
		yAxis.setGranularity(1.0f);

		XAxis xAxis = playCountChart.getXAxis();
		xAxis.setGranularity(1.0f);
		xAxis.setDrawGridLines(false);

		if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
			playerTransition = new AutoTransition();
			playerTransition.setDuration(150);
			AnimationUtils.setInterpolator(getContext(), playerTransition);
		}

		return rootView;
	}

	private void readBundle(@Nullable Bundle bundle) {
		if (bundle == null) return;
		gameId = bundle.getInt(KEY_GAME_ID, BggContract.INVALID_ID);
		headerColor = bundle.getInt(KEY_HEADER_COLOR, getResources().getColor(R.color.accent));
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		LoaderManager.getInstance(this).restartLoader(GameQuery._TOKEN, null, this);
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		if (unbinder != null) unbinder.unbind();
	}

	@NonNull
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle data) {
		CursorLoader loader = null;
		String playSelection = Plays.OBJECT_ID + "=? AND " + SelectionBuilder.whereZeroOrNull(Plays.DELETE_TIMESTAMP);
		String[] selectionArgs = { String.valueOf(gameId) };
		switch (id) {
			case GameQuery._TOKEN:
				loader = new CursorLoader(getContext(), Collection.CONTENT_URI, GameQuery.PROJECTION, "collection." + Collection.GAME_ID + "=?", selectionArgs, null);
				loader.setUpdateThrottle(5000);
				break;
			case PlayQuery._TOKEN:
				loader = new CursorLoader(getContext(), Plays.CONTENT_URI, PlayQuery.PROJECTION, playSelection, selectionArgs, Plays.DATE + " ASC");
				loader.setUpdateThrottle(5000);
				break;
			case PlayerQuery._TOKEN:
				loader = new CursorLoader(getContext(), Plays.buildPlayersUri(), PlayerQuery.PROJECTION, playSelection, selectionArgs, null);
				loader.setUpdateThrottle(5000);
				break;
		}
		return loader;
	}

	@Override
	public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
		if (getActivity() == null) return;

		int token = loader.getId();
		switch (token) {
			case GameQuery._TOKEN:
				playCountColors = bggColors;
				if (cursor == null || !cursor.moveToFirst()) {
					playingTime = 0;
					personalRating = 0.0;
					gameOwned = false;
				} else {
					final int winsColor = cursor.getInt(GameQuery.WINS_COLOR);
					final int winnablePlaysColor = cursor.getInt(GameQuery.WINNABLE_PLAYS_COLOR);
					final int allPlaysColor = cursor.getInt(GameQuery.ALL_PLAYS_COLOR);
					playCountColors = new int[] {
						winsColor == Color.TRANSPARENT ? bggColors[0] : winsColor,
						winnablePlaysColor,
						allPlaysColor
					};

					playingTime = cursor.getInt(GameQuery.PLAYING_TIME);
					gameOwned = cursor.getInt(GameQuery.STATUS_OWN) > 0;
					double ratingSum = 0;
					int ratingCount = 0;
					do {
						double rating = cursor.getDouble(GameQuery.RATING);
						if (rating > 0) {
							ratingSum += rating;
							ratingCount++;
						}
					} while (cursor.moveToNext());
					if (ratingCount == 0) {
						personalRating = 0.0;
					} else {
						personalRating = ratingSum / ratingCount;
					}
				}
				LoaderManager.getInstance(this).restartLoader(PlayQuery._TOKEN, null, this);
				break;
			case PlayQuery._TOKEN:
				if (cursor == null || !cursor.moveToFirst()) {
					showEmpty();
					return;
				}
				stats = new Stats(cursor, personalRating);
				LoaderManager.getInstance(this).restartLoader(PlayerQuery._TOKEN, null, this);
				break;
			case PlayerQuery._TOKEN:
				if (cursor != null && cursor.moveToFirst()) {
					stats.addPlayerData(cursor);
				}
				stats.calculate();
				bindUi(stats);
				showData();
				break;
			default:
				cursor.close();
				break;
		}
	}

	private void bindUi(Stats stats) {
		playCountTable.removeAllViews();
		datesTable.removeAllViews();
		playTimeTable.removeAllViews();
		advancedTable.removeAllViews();

		final PlayStatRow playStatRow = new PlayStatRow(getContext());
		if (!TextUtils.isEmpty(stats.getDollarDate())) {
			playStatRow.setValue(getString(R.string.play_stat_dollar));
		} else if (!TextUtils.isEmpty(stats.getHalfDollarDate())) {
			playStatRow.setValue(getString(R.string.play_stat_half_dollar));
		} else if (!TextUtils.isEmpty(stats.getQuarterDate())) {
			playStatRow.setValue(getString(R.string.play_stat_quarter));
		} else if (!TextUtils.isEmpty(stats.getDimeDate())) {
			playStatRow.setValue(getString(R.string.play_stat_dime));
		} else if (!TextUtils.isEmpty(stats.getNickelDate())) {
			playStatRow.setValue(getString(R.string.play_stat_nickel));
		}
		playCountTable.addView(playStatRow);

		addPlayStat(playCountTable, stats.getPlayCount(), R.string.play_stat_play_count);

		if (stats.getPlayCountIncomplete() > 0) {
			addPlayStat(playCountTable, stats.getPlayCountIncomplete(), R.string.play_stat_play_count_incomplete);
		}
		addPlayStat(playCountTable, stats.getMonthsPlayed(), R.string.play_stat_months_played);
		if (stats.getPlayRate() > 0.0) {
			addPlayStat(playCountTable, stats.getPlayRate(), R.string.play_stat_play_rate);
		}

		ArrayList<BarEntry> playCountValues = new ArrayList<>();
		for (int i = stats.getMinPlayerCount(); i <= stats.getMaxPlayerCount(); i++) {
			final int winnablePlayCount = stats.getWinnablePlayCount(i);
			final int wins = stats.getWinCount(i);
			final int playCount = stats.getPlayCount(i);
			playCountValues.add(new BarEntry(i, new float[] { wins, winnablePlayCount - wins, playCount - winnablePlayCount }));
		}
		if (playCountValues.size() > 0) {
			BarDataSet playCountDataSet = new BarDataSet(playCountValues, getString(R.string.title_plays));
			playCountDataSet.setDrawValues(false);
			playCountDataSet.setHighlightEnabled(false);
			playCountDataSet.setColors(playCountColors == null ? bggColors : playCountColors);
			playCountDataSet.setStackLabels(new String[] { getString(R.string.title_wins), getString(R.string.winnable), getString(R.string.all) });

			ArrayList<IBarDataSet> dataSets = new ArrayList<>();
			dataSets.add(playCountDataSet);

			BarData data = new BarData(dataSets);
			playCountChart.setData(data);
			playCountChart.animateY(1000, Easing.EaseInOutBack);
			playCountChart.setVisibility(View.VISIBLE);
		} else {
			playCountChart.setVisibility(View.GONE);
		}

		if (stats.hasScores()) {
			lowScoreView.setText(SCORE_FORMAT.format(stats.getLowScore()));
			averageScoreView.setText(SCORE_FORMAT.format(stats.getAverageScore()));
			averageWinScoreView.setText(SCORE_FORMAT.format(stats.getAverageWinningScore()));
			highScoreView.setText(SCORE_FORMAT.format(stats.getHighScore()));
			if (stats.getHighScore() > stats.getLowScore()) {
				scoreGraphView.setLowScore(stats.getLowScore());
				scoreGraphView.setAverageScore(stats.getAverageScore());
				scoreGraphView.setAverageWinScore(stats.getAverageWinningScore());
				scoreGraphView.setHighScore(stats.getHighScore());
				scoreGraphView.setVisibility(View.VISIBLE);
			}
			scoresCard.setVisibility(View.VISIBLE);
		} else {
			scoresCard.setVisibility(View.GONE);
		}

		addStatRowMaybe(datesTable, stats.getFirstPlayDate()).setLabel(R.string.play_stat_first_play);
		addStatRowMaybe(datesTable, stats.getNickelDate()).setLabel(R.string.play_stat_nickel);
		addStatRowMaybe(datesTable, stats.getDimeDate()).setLabel(R.string.play_stat_dime);
		addStatRowMaybe(datesTable, stats.getQuarterDate()).setLabel(R.string.play_stat_quarter);
		addStatRowMaybe(datesTable, stats.getHalfDollarDate()).setLabel(R.string.play_stat_half_dollar);
		addStatRowMaybe(datesTable, stats.getDollarDate()).setLabel(R.string.play_stat_dollar);
		addStatRowMaybe(datesTable, stats.getLastPlayDate()).setLabel(R.string.play_stat_last_play);

		final PlayStatRow playTimeView = addStatRow(playTimeTable);
		playTimeView.setLabel(R.string.play_stat_hours_played);
		playTimeView.setValue((int) stats.getHoursPlayed());

		addPlayStatMinutes(playTimeTable,stats.getPlayerHoursPlayed(), R.string.play_stat_total_player_hours_played);

		int average = stats.getAveragePlayTime();
		if (average > 0) {
			addPlayStatMinutes(playTimeTable, average, R.string.play_stat_average_play_time);
			if (playingTime > 0) {
				if (average > playingTime) {
					addPlayStatMinutes(playTimeTable, average - playingTime, R.string.play_stat_average_play_time_slower);
				} else if (playingTime > average) {
					addPlayStatMinutes(playTimeTable, playingTime - average, R.string.play_stat_average_play_time_faster);
				}
				// don't display anything if the average is exactly as expected
			}
		}
		int averagePerPlayer = stats.getAveragePlayTimePerPlayer();
		if (averagePerPlayer > 0) {
			addPlayStatMinutes(playTimeTable, averagePerPlayer, R.string.play_stat_average_play_time_per_player);
		}

		locationsTable.removeAllViews();
		for (Entry<String, Integer> location : stats.getPlaysPerLocation()) {
			locationsCard.setVisibility(View.VISIBLE);
			addPlayStat(locationsTable, location.getValue(), location.getKey());
		}

		playersList.removeAllViews();
		int position = 0;
		for (Entry<String, PlayerStats> playerStats : stats.getPlayerStats()) {
			position++;
			playersCard.setVisibility(View.VISIBLE);
			PlayerStats ps = playerStats.getValue();

			final PlayerStatView view = new PlayerStatView(getActivity());
			view.setName(playerStats.getKey());
			view.setWinInfo(ps.wins, ps.winnableGames);
			view.setWinSkill(ps.getWinSkill());

			view.setOverallLowScore(stats.getLowScore());
			view.setOverallAverageScore(stats.getAverageScore());
			view.setOverallAverageWinScore(stats.getAverageWinningScore());
			view.setOverallHighScore(stats.getHighScore());
			view.setLowScore(ps.getLowScore());
			view.setAverageScore(ps.getAverageScore());
			view.setAverageWinScore(ps.getAverageWinScore());
			view.setHighScore(ps.getHighScore());

			view.showScores(selectedItems.get(position, false));
			if (stats.hasScores()) {
				final int finalPosition = position;
				view.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
							TransitionManager.beginDelayedTransition(playersList, playerTransition);
						}

						if (selectedItems.get(finalPosition, false)) {
							selectedItems.delete(finalPosition);
							view.showScores(false);
						} else {
							selectedItems.put(finalPosition, true);
							view.showScores(true);
						}
					}
				});
			}
			playersList.addView(view);
		}

		if (personalRating > 0) {
			PlayStatRow view = addPlayStat(advancedTable, stats.calculateFhm(), R.string.play_stat_fhm);
			view.setInfoText(R.string.play_stat_fhm_info);

			view = addPlayStat(advancedTable, stats.calculateHhm(), R.string.play_stat_hhm);
			view.setInfoText(R.string.play_stat_hhm_info);

			view = addPlayStat(advancedTable, stats.calculateRuhm(), R.string.play_stat_ruhm);
			view.setInfoText(R.string.play_stat_ruhm_info);
		}

		if (gameOwned) {
			PlayStatRow view = addPlayStat(advancedTable, DoubleUtils.asPercentage(stats.calculateUtilization()), R.string.play_stat_utilization);
			view.setInfoText(R.string.play_stat_utilization_info);
		}

		int hIndexOffset = stats.getHIndexOffset();
		if (hIndexOffset == -1) {
			addStatRow(advancedTable).setLabel(R.string.play_stat_game_h_index_offset_in);
		} else {
			final PlayStatRow hIndexView = addStatRow(advancedTable);
			hIndexView.setLabel(R.string.play_stat_game_h_index_offset_out);
			hIndexView.setValue(hIndexOffset);
		}
	}

	private PlayStatRow addPlayStat(TableLayout table, int value, @StringRes int label) {
		final PlayStatRow view = new PlayStatRow(getContext());
		table.addView(view);
		view.setValue(value);
		view.setLabel(label);
		return view;
	}

	private PlayStatRow addPlayStat(TableLayout table, int value, String label) {
		final PlayStatRow view = new PlayStatRow(getContext());
		table.addView(view);
		view.setValue(value);
		view.setLabel(label);
		return view;
	}

	private PlayStatRow addPlayStat(TableLayout table, Double value, @StringRes int label) {
		final PlayStatRow view = new PlayStatRow(getContext());
		table.addView(view);
		view.setValue(value);
		view.setLabel(label);
		return view;
	}

	private PlayStatRow addPlayStat(TableLayout table, String value, @StringRes int label) {
		final PlayStatRow view = new PlayStatRow(getContext());
		table.addView(view);
		view.setValue(value);
		view.setLabel(label);
		return view;
	}

	private PlayStatRow addPlayStatMinutes(TableLayout table, int minutes, @StringRes int label) {
		final PlayStatRow view = new PlayStatRow(getContext());
		table.addView(view);
		view.setValue(DateTimeUtils.formatMinutes(minutes));
		view.setLabel(label);
		return view;
	}

	private void showEmpty() {
		progressView.hide();
		AnimationUtils.fadeOut(dataView);
		AnimationUtils.fadeIn(emptyView);
	}

	private void showData() {
		progressView.hide();
		AnimationUtils.fadeOut(emptyView);
		AnimationUtils.fadeIn(dataView);
	}

	private PlayStatRow addStatRowMaybe(ViewGroup container, String date) {
		PlayStatRow view = new PlayStatRow(getContext());
		if (!TextUtils.isEmpty(date)) {
			container.addView(view);
			view.setValueAsDate(date, getContext());
		}
		return view;
	}

	private PlayStatRow addStatRow(ViewGroup container) {
		PlayStatRow view = new PlayStatRow(getContext());
		container.addView(view);
		return view;
	}

	@Override
	public void onLoaderReset(@NonNull Loader<Cursor> loader) {
	}

	@OnClick(R.id.score_help)
	public void onScoreHelpClick() {
		if (getContext() == null) return;
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.title_scores).setView(R.layout.dialog_help_score);
		builder.show();
	}

	@OnClick(R.id.low_score)
	public void onLowScoreClick() {
		if (getContext() == null) return;
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.title_low_scorers).setMessage(stats.getLowScorers());
		builder.show();
	}

	@OnClick(R.id.high_score)
	public void onHighScoreClick() {
		if (getContext() == null) return;
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.title_high_scorers).setMessage(stats.getHighScorers());
		builder.show();
	}

	@OnClick(R.id.players_skill_help)
	public void onPlayersClick() {
		if (getContext() == null) return;
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.title_players_skill).setMessage(R.string.player_skill_info);
		builder.show();
	}

	private class PlayerStats {
		private String username;
		private int playCount;
		private int wins;
		private int winsWithScore;
		private int winnableGames;
		private int winsTimesPlayers;
		private final SparseIntArray winsByPlayerCount = new SparseIntArray();
		private final SparseIntArray playsByPlayerCount = new SparseIntArray();
		private final SparseIntArray winnablePlaysByPlayerCount = new SparseIntArray();
		private double totalScore;
		private double winningScore;
		private int totalScoreCount;
		private double highScore;
		private double lowScore;

		public PlayerStats() {
			username = "";
			playCount = 0;
			wins = 0;
			winsWithScore = 0;
			winnableGames = 0;
			winsTimesPlayers = 0;
			winsByPlayerCount.clear();
			totalScore = 0.0;
			winningScore = 0.0;
			totalScoreCount = 0;
			highScore = Integer.MIN_VALUE;
			lowScore = Integer.MAX_VALUE;
		}

		public void add(PlayModel play, PlayerModel player) {
			username = player.username;
			playCount += play.quantity;
			addByPlayerCount(playsByPlayerCount, play.playerCount, play.quantity);

			if (play.isWinnable()) {
				winnableGames += play.quantity;
				addByPlayerCount(winnablePlaysByPlayerCount, play.playerCount, play.quantity);
				if (player.win) {
					wins += play.quantity;
					winsTimesPlayers += play.quantity * play.playerCount;
					if (StringUtils.isNumeric(player.score)) winsWithScore += play.quantity;
					addByPlayerCount(winsByPlayerCount, play.playerCount, play.quantity);
				}
			}
			if (StringUtils.isNumeric(player.score)) {
				final double score = StringUtils.parseDouble(player.score);
				totalScore += score * play.quantity;
				totalScoreCount += play.quantity;
				if (score < lowScore) lowScore = score;
				if (score > highScore) highScore = score;
				if (play.isWinnable() && player.win) {
					winningScore += score * play.quantity;
				}
			}
		}

		private void addByPlayerCount(SparseIntArray playerCountMap, int playerCount, int quantity) {
			playerCountMap.put(playerCount, playerCountMap.get(playerCount) + quantity);
		}

		public String getUsername() {
			return username;
		}

		public int getWinCountByPlayerCount(int playerCount) {
			return winsByPlayerCount.get(playerCount);
		}

		public int getWinnablePlayCountByPlayerCount(int playerCount) {
			return winnablePlaysByPlayerCount.get(playerCount);
		}

		public int getPlayCountByPlayerCount(int playerCount) {
			return playsByPlayerCount.get(playerCount);
		}

		public int getWinSkill() {
			return (int) (((double) winsTimesPlayers / (double) winnableGames) * 100);
		}

		public double getAverageScore() {
			if (totalScoreCount == 0) return Integer.MIN_VALUE;
			return totalScore / totalScoreCount;
		}

		public double getAverageWinScore() {
			if (totalScoreCount == 0) return Integer.MIN_VALUE;
			if (winsWithScore == 0) return Integer.MIN_VALUE;
			return winningScore / winsWithScore;
		}

		public double getHighScore() {
			return highScore;
		}

		public double getLowScore() {
			return lowScore;
		}
	}

	private class Stats {
		private final double lambda = Math.log(0.1) / -10;
		private final String currentYear = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));

		private final Map<Integer, PlayModel> plays = new LinkedHashMap<>();
		private final Map<String, PlayerStats> playerStats = new HashMap<>();

		private final double personalRating;

		private String firstPlayDate;
		private String lastPlayDate;
		private String nickelDate;
		private String dimeDate;
		private String quarterDate;
		private String halfDollarDate;
		private String dollarDate;
		private int playCount;
		private int playCountIncomplete;
		private int playCountWithLength;
		private int playCountThisYear;
		private int playerCountSumWithLength;
		private Map<Integer, Integer> playCountPerPlayerCount;
		private int realMinutesPlayed;
		private int estimatedMinutesPlayed;
		private int playerHoursPlayed;
		private int numberOfWinnableGames;
		private double scoreSum;
		private int scoreCount;
		private double highScore;
		private double lowScore;
		private int winningScoreCount;
		private double winningScoreSum;
		private Map<String, Integer> playCountByLocation;
		private final Set<String> monthsPlayed = new HashSet<>();

		public Stats(Cursor cursor, double personalRating) {
			init();
			this.personalRating = personalRating;
			do {
				PlayModel model = new PlayModel(cursor);
				plays.put(model.playId, model);
			} while (cursor.moveToNext());
		}

		private void init() {
			plays.clear();

			// dates
			firstPlayDate = null;
			lastPlayDate = null;
			nickelDate = null;
			dimeDate = null;
			quarterDate = null;
			halfDollarDate = null;
			dollarDate = null;
			monthsPlayed.clear();

			playCount = 0;
			playCountIncomplete = 0;
			playCountWithLength = 0;
			playCountThisYear = 0;
			playerCountSumWithLength = 0;
			playCountPerPlayerCount = new ArrayMap<>();
			playCountByLocation = new HashMap<>();
			numberOfWinnableGames = 0;

			realMinutesPlayed = 0;
			estimatedMinutesPlayed = 0;
			playerHoursPlayed = 0;

			scoreSum = 0;
			scoreCount = 0;
			highScore = Integer.MIN_VALUE;
			lowScore = Integer.MAX_VALUE;
			winningScoreCount = 0;
			winningScoreSum = 0;
		}

		public void calculate() {
			boolean includeIncomplete = PreferencesUtils.logPlayStatsIncomplete(getActivity());
			for (PlayModel play : plays.values()) {
				if (!includeIncomplete && play.incomplete) {
					playCountIncomplete += play.quantity;
					continue;
				}

				if (firstPlayDate == null) {
					firstPlayDate = play.date;
				}
				lastPlayDate = play.date;

				if (playCount < 5 && (playCount + play.quantity) >= 5) {
					nickelDate = play.date;
				}
				if (playCount < 10 && (playCount + play.quantity) >= 10) {
					dimeDate = play.date;
				}
				if (playCount < 25 && (playCount + play.quantity) >= 25) {
					quarterDate = play.date;
				}
				if (playCount < 50 && (playCount + play.quantity) >= 50) {
					halfDollarDate = play.date;
				}
				if (playCount < 100 && (playCount + play.quantity) >= 100) {
					dollarDate = play.date;
				}
				playCount += play.quantity;
				if (play.getYear().equals(currentYear)) {
					playCountThisYear += play.quantity;
				}

				if (play.length == 0) {
					estimatedMinutesPlayed += playingTime * play.quantity;
				} else {
					playerHoursPlayed += play.length* play.playerCount;
					realMinutesPlayed += play.length;
					playCountWithLength += play.quantity;
					playerCountSumWithLength += play.playerCount * play.quantity;
				}

				if (play.playerCount > 0) {
					int previousQuantity = 0;
					if (playCountPerPlayerCount.containsKey(play.playerCount)) {
						previousQuantity = playCountPerPlayerCount.get(play.playerCount);
					}
					playCountPerPlayerCount.put(play.playerCount, previousQuantity + play.quantity);
				}

				if (play.isWinnable()) {
					numberOfWinnableGames += play.quantity;
				}

				if (!TextUtils.isEmpty(play.location)) {
					int previousPlays = 0;
					if (playCountByLocation.containsKey(play.location)) {
						previousPlays = playCountByLocation.get(play.location);
					}
					playCountByLocation.put(play.location, previousPlays + play.quantity);
				}

				for (PlayerModel player : play.getPlayers()) {
					if (!TextUtils.isEmpty(player.getUniqueName())) {
						PlayerStats playerStats = this.playerStats.get(player.getUniqueName());
						if (playerStats == null) playerStats = new PlayerStats();
						playerStats.add(play, player);
						this.playerStats.put(player.getUniqueName(), playerStats);
					}

					if (StringUtils.isNumeric(player.score)) {
						double score = StringUtils.parseDouble(player.score);

						scoreCount += play.quantity;
						scoreSum += score * play.quantity;

						if (player.win) {
							winningScoreCount += play.quantity;
							winningScoreSum += score * play.quantity;
						}

						if (score > highScore) highScore = score;
						if (score < lowScore) lowScore = score;
					}
				}

				monthsPlayed.add(play.getYearAndMonth());
			}
		}

		public void addPlayerData(Cursor cursor) {
			do {
				PlayerModel playerModel = new PlayerModel(cursor);
				if (plays.containsKey(playerModel.playId)) {
					plays.get(playerModel.playId).addPlayer(playerModel);
				} else {
					Timber.w("Play %s not found in the play map!", playerModel.playId);
				}
			} while (cursor.moveToNext());
		}

		public int getPlayCount() {
			return playCount;
		}

		public int getPlayCountIncomplete() {
			return playCountIncomplete;
		}

		public String getFirstPlayDate() {
			return firstPlayDate;
		}

		private String getNickelDate() {
			return nickelDate;
		}

		private String getDimeDate() {
			return dimeDate;
		}

		private String getQuarterDate() {
			return quarterDate;
		}

		private String getHalfDollarDate() {
			return halfDollarDate;
		}

		private String getDollarDate() {
			return dollarDate;
		}

		public String getLastPlayDate() {
			if (playCount > 0) {
				return lastPlayDate;
			}
			return null;
		}

		public double getHoursPlayed() {
			return (realMinutesPlayed + estimatedMinutesPlayed) / 60;
		}

		public int getPlayerHoursPlayed() {
			return playerHoursPlayed;
		}

		/* plays per month, only counting the active period) */
		public double getPlayRate() {
			long flash = calculateFlash();
			if (flash > 0) {
				double rate = ((double) (playCount * 365) / flash) / 12;
				return Math.min(rate, playCount);
			}
			return 0;
		}

		public int getAveragePlayTime() {
			if (playCountWithLength > 0) {
				return realMinutesPlayed / playCountWithLength;
			}
			return 0;
		}

		public int getAveragePlayTimePerPlayer() {
			if (playerCountSumWithLength > 0) {
				return realMinutesPlayed / playerCountSumWithLength;
			}
			return 0;
		}

		public int getMonthsPlayed() {
			return monthsPlayed.size();
		}

		public int getMinPlayerCount() {
			int min = Integer.MAX_VALUE;
			for (Integer playerCount : playCountPerPlayerCount.keySet()) {
				if (playerCount < min) {
					min = playerCount;
				}
			}
			return min;
		}

		public int getMaxPlayerCount() {
			int max = 0;
			for (Integer playerCount : playCountPerPlayerCount.keySet()) {
				if (playerCount > max) {
					max = playerCount;
				}
			}
			return max;
		}

		public int getWinCount(int playerCount) {
			PlayerStats ps = getPersonalStats();
			if (ps != null) {
				return ps.getWinCountByPlayerCount(playerCount);
			}
			return 0;
		}

		public int getWinnablePlayCount(int playerCount) {
			PlayerStats ps = getPersonalStats();
			if (ps != null) {
				return ps.getWinnablePlayCountByPlayerCount(playerCount);
			}
			return 0;
		}

		public int getPlayCount(int playerCount) {
			if (playCountPerPlayerCount.containsKey(playerCount)) {
				return playCountPerPlayerCount.get(playerCount);
			} else {
				return 0;
			}
		}

		private PlayerStats getPersonalStats() {
			String username = AccountUtils.getUsername(getActivity());
			for (Entry<String, PlayerStats> ps : stats.getPlayerStats()) {
				if (username != null && username.equalsIgnoreCase(ps.getValue().getUsername())) {
					return ps.getValue();
				}
			}
			return null;
		}

		public boolean hasScores() {
			return scoreCount > 0;
		}

		public double getAverageScore() {
			return scoreSum / scoreCount;
		}

		public double getHighScore() {
			return highScore;
		}

		public String getHighScorers() {
			if (highScore == Integer.MIN_VALUE) return "";
			List<String> players = new ArrayList<>();
			for (Entry<String, PlayerStats> ps : playerStats.entrySet()) {
				if (ps.getValue().highScore == highScore) {
					players.add(ps.getKey());
				}
			}
			return StringUtils.formatList(players);
		}

		public double getLowScore() {
			return lowScore;
		}

		public String getLowScorers() {
			if (lowScore == Integer.MAX_VALUE) return "";
			List<String> players = new ArrayList<>();
			for (Entry<String, PlayerStats> ps : playerStats.entrySet()) {
				if (ps.getValue().lowScore == lowScore) {
					players.add(ps.getKey());
				}
			}
			return StringUtils.formatList(players);
		}

		public double getAverageWinningScore() {
			return winningScoreSum / winningScoreCount;
		}

		public List<Entry<String, PlayerStats>> getPlayerStats() {
			Set<Entry<String, PlayerStats>> set = playerStats.entrySet();
			List<Entry<String, PlayerStats>> list = new ArrayList(set);
			Collections.sort(list, new Comparator<Entry<String, PlayerStats>>() {
				@Override
				public int compare(Entry<String, PlayerStats> lhs, Entry<String, PlayerStats> rhs) {
					if (lhs.getValue().playCount > rhs.getValue().playCount) {
						return -1;
					} else if (lhs.getValue().playCount < rhs.getValue().playCount) {
						return 1;
					} else {
						return lhs.getKey().compareTo(rhs.getKey());
					}
				}
			});
			return list;
		}

		public List<Entry<String, Integer>> getPlaysPerLocation() {
			Set<Entry<String, Integer>> set = playCountByLocation.entrySet();
			List<Entry<String, Integer>> list = new ArrayList(set);
			Collections.sort(list, new Comparator<Entry<String, Integer>>() {
				@Override
				public int compare(Entry<String, Integer> lhs, Entry<String, Integer> rhs) {
					if (lhs.getValue() > rhs.getValue()) {
						return -1;
					} else if (lhs.getValue() < rhs.getValue()) {
						return 1;
					} else {
						return lhs.getKey().compareTo(rhs.getKey());
					}
				}
			});
			return list;
		}

		public double calculateUtilization() {
			return DoubleUtils.cdf(playCount, lambda);
		}

		public int calculateFhm() {
			return (int) ((personalRating * 5) + playCount + (4 * getMonthsPlayed()) + getHoursPlayed());
		}

		public int calculateHhm() {
			return (int) ((personalRating - 5) * getHoursPlayed());
		}

		public double calculateRuhm() {
			double raw = (((double) calculateFlash()) / calculateLag()) * getMonthsPlayed() * personalRating;
			if (raw == 0) {
				return 0;
			}
			return Math.log(raw);
		}

		public int getHIndexOffset() {
			int hIndex = PreferencesUtils.getGameHIndex(getActivity());
			if (playCount >= hIndex) {
				return -1;
			} else {
				return hIndex - playCount;
			}
		}

		// public int getMonthsPerPlay() {
		// long days = calculateSpan();
		// int months = (int) (days / 365.25 * 12);
		// return months / playCount;
		// }

		public double calculateGrayHotness(int intervalPlayCount) {
			// http://matthew.gray.org/2005/10/games_16.html
			double S = 1 + (intervalPlayCount / playCount);
			// TODO: need to get HHM for the interval _only_
			return S * S * Math.sqrt(intervalPlayCount) * calculateHhm();
		}

		public int calculatWhitemoreScore() {
			// http://www.boardgamegeek.com/geeklist/37832/my-favorite-designers
			int score = (int) (personalRating * 2 - 13);
			if (score < 0) {
				return 0;
			}
			return score;
		}

		public double calculateZefquaaviusScore() {
			// http://boardgamegeek.com/user/zefquaavius
			double neutralRating = 5.5;
			double abs = (personalRating - neutralRating);
			double squared = abs * abs;
			if (personalRating < neutralRating) {
				squared *= -1;
			}
			return squared / 2.025;
		}

		public double calculateZefquaaviusHotness(int intervalPlayCount) {
			return calculateGrayHotness(intervalPlayCount) * calculateZefquaaviusScore();
		}

		private long calculateFlash() {
			return daysBetweenDates(firstPlayDate, lastPlayDate);
		}

		private long calculateLag() {
			return daysBetweenDates(lastPlayDate, null);
		}

		private long calculateSpan() {
			return daysBetweenDates(firstPlayDate, null);
		}

		private long daysBetweenDates(String first, String second) {
			try {
				long f = System.currentTimeMillis();
				long s = System.currentTimeMillis();
				if (!TextUtils.isEmpty(first)) {
					f = FORMAT.parse(first).getTime();
				}
				if (!TextUtils.isEmpty(second)) {
					s = FORMAT.parse(second).getTime();
				}
				long days = TimeUnit.DAYS.convert(s - f, TimeUnit.MILLISECONDS);
				if (days < 1) {
					return 1;
				}
				return days;
			} catch (ParseException e) {
				return 1;
			}
		}
	}

	private class PlayModel {
		final int playId;
		final String date;
		final int length;
		final int quantity;
		final boolean incomplete;
		final int playerCount;
		final boolean noWinStats;
		final String location;
		final long deleteTimestamp;
		final long updateTimestamp;
		final List<PlayerModel> players = new ArrayList<>();

		PlayModel(Cursor cursor) {
			playId = cursor.getInt(PlayQuery.PLAY_ID);
			date = cursor.getString(PlayQuery.DATE);
			length = cursor.getInt(PlayQuery.LENGTH);
			quantity = cursor.getInt(PlayQuery.QUANTITY);
			incomplete = CursorUtils.getBoolean(cursor, PlayQuery.INCOMPLETE);
			playerCount = cursor.getInt(PlayQuery.PLAYER_COUNT);
			noWinStats = CursorUtils.getBoolean(cursor, PlayQuery.NO_WIN_STATS);
			location = cursor.getString(PlayQuery.LOCATION);
			deleteTimestamp = cursor.getLong(PlayQuery.DELETE_TIMESTAMP);
			updateTimestamp = cursor.getLong(PlayQuery.UPDATE_TIMESTAMP);
			players.clear();
		}

		public List<PlayerModel> getPlayers() {
			return players;
		}

		public String getYear() {
			return date.substring(0, 4);
		}

		public String getYearAndMonth() {
			return date.substring(0, 7);
		}

		public void addPlayer(PlayerModel player) {
			players.add(player);
		}

		public boolean isWinnable() {
			if (noWinStats) {
				return false;
			}
			if (players == null || players.isEmpty()) {
				return false;
			}
			if (updateTimestamp > 0) {
				return true;
			}
			if (playId > 0 && deleteTimestamp == 0) {
				return true;
			}
			return false;
		}
	}

	private class PlayerModel {
		final int playId;
		final String username;
		final String name;
		final boolean win;
		final String score;

		PlayerModel(Cursor cursor) {
			playId = cursor.getInt(PlayerQuery.PLAY_ID);
			username = cursor.getString(PlayerQuery.USER_NAME);
			name = cursor.getString(PlayerQuery.NAME);
			win = CursorUtils.getBoolean(cursor, PlayerQuery.WIN);
			score = cursor.getString(PlayerQuery.SCORE);
		}

		public String getUniqueName() {
			if (TextUtils.isEmpty(username)) {
				return name;
			}
			return name + " (" + username + ")";
		}
	}

	private interface PlayQuery {
		int _TOKEN = 0x01;
		String[] PROJECTION = { Plays._ID, Plays.PLAY_ID, Plays.DATE, Plays.ITEM_NAME, Plays.OBJECT_ID,
			Plays.LOCATION, Plays.QUANTITY, Plays.LENGTH, Plays.PLAYER_COUNT, Games.THUMBNAIL_URL,
			Plays.INCOMPLETE, Plays.NO_WIN_STATS, Plays.DELETE_TIMESTAMP, Plays.UPDATE_TIMESTAMP };
		int PLAY_ID = 1;
		int DATE = 2;
		int LOCATION = 5;
		int QUANTITY = 6;
		int LENGTH = 7;
		int PLAYER_COUNT = 8;
		int INCOMPLETE = 10;
		int NO_WIN_STATS = 11;
		int DELETE_TIMESTAMP = 12;
		int UPDATE_TIMESTAMP = 13;
	}

	private interface PlayerQuery {
		int _TOKEN = 0x03;
		String[] PROJECTION = { PlayPlayers._ID, PlayPlayers.PLAY_ID, PlayPlayers.USER_NAME, PlayPlayers.WIN,
			PlayPlayers.SCORE, PlayPlayers.NAME };
		int PLAY_ID = 1;
		int USER_NAME = 2;
		int WIN = 3;
		int SCORE = 4;
		int NAME = 5;
	}

	private interface GameQuery {
		int _TOKEN = 0x02;
		String[] PROJECTION = {
			Games._ID,
			Collection.RATING,
			Games.PLAYING_TIME,
			Collection.STATUS_OWN,
			Games.WINS_COLOR,
			Games.WINNABLE_PLAYS_COLOR,
			Games.ALL_PLAYS_COLOR
		};
		int RATING = 1;
		int PLAYING_TIME = 2;
		int STATUS_OWN = 3;
		int WINS_COLOR = 4;
		int WINNABLE_PLAYS_COLOR = 5;
		int ALL_PLAYS_COLOR = 6;
	}
}