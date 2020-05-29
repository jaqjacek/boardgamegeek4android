package com.boardgamegeek.repository

import androidx.lifecycle.LiveData
import com.boardgamegeek.BggApplication
import com.boardgamegeek.R
import com.boardgamegeek.entities.ArticleEntity
import com.boardgamegeek.entities.RefreshableResource
import com.boardgamegeek.entities.ThreadArticlesEntity
import com.boardgamegeek.extensions.toMillis
import com.boardgamegeek.io.Adapter
import com.boardgamegeek.io.model.ThreadResponse
import com.boardgamegeek.livedata.NetworkLoader
import retrofit2.Call
import java.text.SimpleDateFormat
import java.util.*

class ThreadRepository(val application: BggApplication) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz", Locale.US)

    fun getThread(threadId: Int): LiveData<RefreshableResource<ThreadArticlesEntity>> {
        return object : NetworkLoader<ThreadArticlesEntity, ThreadResponse>(application) {
            override val typeDescriptionResId: Int
                get() = R.string.title_forums

            override fun createCall(): Call<ThreadResponse> {
                return Adapter.createForXml().thread(threadId)
            }

            override fun parseResult(result: ThreadResponse): ThreadArticlesEntity {
                return mapForums(result)
            }

        }.asLiveData()
    }

    private fun mapForums(result: ThreadResponse): ThreadArticlesEntity {
        val articles = mutableListOf<ArticleEntity>()
        result.articles.forEach {
            articles.add(ArticleEntity(
                    it.id,
                    it.username.orEmpty(),
                    it.link,
                    it.postdate.toMillis(dateFormat),
                    it.editdate.toMillis(dateFormat),
                    it.body?.trim().orEmpty(),
                    it.numedits
            ))
        }
        return ThreadArticlesEntity(
                result.id,
                result.subject,
                articles
        )
    }
}