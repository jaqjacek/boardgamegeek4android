package com.boardgamegeek.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.boardgamegeek.db.DesignerDao
import com.boardgamegeek.entities.PersonEntity
import com.boardgamegeek.extensions.firstChar
import com.boardgamegeek.extensions.orderOfMagnitude
import com.boardgamegeek.repository.DesignerRepository

class DesignsViewModel(application: Application) : AndroidViewModel(application) {
    enum class SortType {
        NAME, ITEM_COUNT, WHITMORE_SCORE
    }

    private val designerRepository = DesignerRepository(getApplication())

    private val _sort = MutableLiveData<DesignersSort>()
    val sort: LiveData<DesignersSort>
        get() = _sort

    init {
        sort(SortType.ITEM_COUNT)
    }

    val designers: LiveData<List<PersonEntity>> = Transformations.switchMap(sort) {
        designerRepository.loadDesigners(it.sortBy)
    }

    fun sort(sortType: SortType) {
        _sort.value = when (sortType) {
            SortType.NAME -> DesignersSortByName()
            SortType.ITEM_COUNT -> DesignersSortByItemCount()
            SortType.WHITMORE_SCORE -> DesignersSortByWhitmoreScore()
        }
    }

    fun getSectionHeader(designer: PersonEntity?): String {
        return sort.value?.getSectionHeader(designer) ?: ""
    }
}

sealed class DesignersSort {
    abstract val sortType: DesignsViewModel.SortType
    abstract val sortBy: DesignerDao.SortType
    abstract fun getSectionHeader(designer: PersonEntity?): String
}

class DesignersSortByName : DesignersSort() {
    override val sortType = DesignsViewModel.SortType.NAME
    override val sortBy = DesignerDao.SortType.NAME
    override fun getSectionHeader(designer: PersonEntity?): String {
        return if (designer?.name == "(Uncredited)") "-"
        else designer?.name.firstChar()
    }
}

class DesignersSortByItemCount : DesignersSort() {
    override val sortType = DesignsViewModel.SortType.ITEM_COUNT
    override val sortBy = DesignerDao.SortType.ITEM_COUNT
    override fun getSectionHeader(designer: PersonEntity?): String {
        return (designer?.itemCount ?: 0).orderOfMagnitude()
    }
}

class DesignersSortByWhitmoreScore : DesignersSort() {
    override val sortType = DesignsViewModel.SortType.WHITMORE_SCORE
    override val sortBy = DesignerDao.SortType.WHITMORE_SCORE
    override fun getSectionHeader(designer: PersonEntity?): String {
        return (designer?.whitmoreScore ?: 0).orderOfMagnitude()
    }
}
