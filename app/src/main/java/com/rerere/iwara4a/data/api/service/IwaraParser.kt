package com.rerere.iwara4a.data.api.service

import android.util.Log
import androidx.annotation.IntRange
import com.google.gson.Gson
import com.rerere.iwara4a.data.api.Response
import com.rerere.iwara4a.data.model.comment.Comment
import com.rerere.iwara4a.data.model.comment.CommentList
import com.rerere.iwara4a.data.model.comment.CommentPostParam
import com.rerere.iwara4a.data.model.comment.CommentPosterType
import com.rerere.iwara4a.data.model.detail.image.ImageDetail
import com.rerere.iwara4a.data.model.detail.video.MoreVideo
import com.rerere.iwara4a.data.model.detail.video.VideoDetail
import com.rerere.iwara4a.data.model.flag.FollowResponse
import com.rerere.iwara4a.data.model.flag.LikeResponse
import com.rerere.iwara4a.data.model.index.MediaList
import com.rerere.iwara4a.data.model.index.MediaPreview
import com.rerere.iwara4a.data.model.index.MediaType
import com.rerere.iwara4a.data.model.index.SubscriptionList
import com.rerere.iwara4a.data.model.session.Session
import com.rerere.iwara4a.data.model.user.Self
import com.rerere.iwara4a.data.model.user.UserData
import com.rerere.iwara4a.data.model.user.UserFriendState
import com.rerere.iwara4a.ui.component.SortType
import com.rerere.iwara4a.util.logError
import com.rerere.iwara4a.util.okhttp.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.URLDecoder

private const val TAG = "IwaraParser"

/**
 * 使用Jsoup来解析出网页上的资源
 *
 * 某些资源无法通过 restful api 直接获取，因此需要
 * 通过jsoup来解析
 *
 * @author RE
 */
class IwaraParser(
    private val okHttpClient: OkHttpClient
) {
    private val gson = Gson()
    private val mediaHttpClient = OkHttpClient.Builder()
        .dns(SmartDns)
        .build()
    suspend fun login(username: String, password: String): Response<Session> =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "login: 开始登录")
            try {
                Log.i(TAG, "login: 开始发起请求: $username/$password")
                //把用户名和密码放到map中转成JSON字符串
                val map = mapOf("email" to username, "password" to password)
                val json = gson.toJson(map)
                val loginRequest = Request.Builder()
                    .url("https://api.iwara.tv/user/login")
                    .post(RequestBody.create("application/json; charset=utf-8".toMediaType(), json))
                    .build()
                val loginResponse = okHttpClient.newCall(loginRequest).await()
                if (loginResponse.code == 400) {
                    return@withContext Response.failed("错误的密码")
                }
                require(loginResponse.isSuccessful)
                //获取登录后的token组成 用于后续的请求
                val token = JSONObject(loginResponse.body.string()).get("token").toString()
                Response.success(Session(token))
            } catch (exception: Exception) {
                exception.printStackTrace()
                Response.failed(if (exception is IOException) "网络连接错误(${exception.javaClass.simpleName})" else exception.javaClass.simpleName)
            }
        }

    suspend fun getSelf(session: Session): Response<Self> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "getSelf: Start...")
            session.getToken()

            val request = Request.Builder()
                .url("https://ecchi.iwara.tv/user")
                .get()
                .build()
            val response = okHttpClient.newCall(request).await()
            require(response.isSuccessful)
            val body = Jsoup.parse(response.body?.string() ?: error("null body")).body()
            val nickname =
                body.getElementsByClass("views-field views-field-name").first()?.text() ?: error(
                    "null nickname"
                )
            val numId = try {
                body
                    .select("div[class=menu-bar]")
                    .select("ul[class=dropdown-menu]")[1]
                    .select("li")
                    .first()!!
                    .select("a")
                    .first()!!
                    .attr("href")
                    .let {
                        it.split("/")[2].toInt()
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                
                0
            }
            val profilePic = "https:" + body.getElementsByClass("views-field views-field-picture")
                .first()
                ?.child(0)
                ?.child(0)
                ?.attr("src")
            val about = body.select("div[class=views-field views-field-field-about]")?.text()
            val userId = body.select("div[id=block-mainblocks-user-connect]")
                .select("ul[class=list-unstyled]").select("a").first()!!.attr("href").let {
                    it.substring(it.indexOf("new?user=") + "new?user=".length)
                }
            val friendRequest = try {
                body.select("div[id=user-links]")
                    .first()
                    ?.select("a")
                    ?.get(2)
                    ?.takeIf {
                        it.attr("href").startsWith("/user/friends")
                    }
                    ?.text()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.toInt() ?: 0
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }
            val messages = try {
                body.select("div[id=user-links]")
                    .first()
                    ?.select("a")
                    ?.get(1)
                    ?.text()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.toInt() ?: 0
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }

            Log.i(
                TAG,
                "getSelf: (id=$userId, nickname=$nickname, profilePic=$profilePic, friend=$friendRequest)"
            )

            Response.success(
                Self(
                    id = userId,
                    numId = numId,
                    nickname = nickname,
                    profilePic = profilePic,
                    about = about,
                    friendRequest = friendRequest,
                    messages = messages
                )
            )
        } catch (exception: Exception) {
            exception.printStackTrace()
            
            Response.failed(exception.javaClass.name)
        }
    }

    suspend fun getSubscriptionList(session: Session, page: Int): Response<SubscriptionList> =
        withContext(Dispatchers.IO) {
            try {
                session.getToken()
                val request = Request.Builder()
                    .url("https://ecchi.iwara.tv/subscriptions?page=$page")
                    .get()
                    .build()
                val response = okHttpClient.newCall(request).await()
                require(response.isSuccessful)
                val body = Jsoup.parse(response.body?.string() ?: error("empty body")).body()
                val elements = body.select("div[id~=^node-[A-Za-z0-9]+\$]")

                val previewList: List<MediaPreview> = elements.map {
                    val title = it.getElementsByClass("title").text()
                    val author = it.getElementsByClass("username").text()
                    val pic =
                        "https:" + it.select("div[class=field-item even]")
                            .select("img")
                            .attr("src")
                    val likes = it.getElementsByClass("right-icon").text()
                    val watchs = it.getElementsByClass("left-icon").text()
                    val link = it.select("div[class=field-item even]")
                        .select("a")
                        .first()
                        ?.attr("href") ?: it.select("a").attr("href")
                    val mediaId = link.substring(link.lastIndexOf("/") + 1)
                    val type = if (link.startsWith("/video")) MediaType.VIDEO else MediaType.IMAGE
                    val private = it.select("div[class=private-video]").any()

                    MediaPreview(
                        title = title,
                        author = author,
                        previewPic = pic,
                        likes = likes,
                        watchs = watchs,
                        mediaId = mediaId,
                        type = type,
                        private = private
                    )
                }

                val hasNextPage =
                    body.select("ul[class=pager]").select("li[class~=^pager-next .+\$]").any()

                Response.success(
                    SubscriptionList(
                        page,
                        hasNextPage,
                        previewList
                    )
                )
            } catch (ex: Exception) {
                ex.printStackTrace()
                Response.failed(ex.javaClass.name)
            }
        }

    suspend fun getImagePageDetail(session: Session, imageId: String): Response<ImageDetail> =
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "getImagePageDetail: start load image detail: $imageId")

                session.getToken()

                val request = Request.Builder()
                    .url("https://ecchi.iwara.tv/images/$imageId")
                    .get()
                    .build()
                val response = okHttpClient.newCall(request).await()
                require(response.isSuccessful)
                val body = Jsoup.parse(response.body?.string() ?: error("empty body")).body()

                val title = body.getElementsByClass("title").first()?.text() ?: error("empty title")
                val imageLinks =
                    body.getElementsByClass("field field-name-field-images field-type-file field-label-hidden")
                        .select("a").map {
                            "https:${it.attr("href")}"
                        }
                val description =
                    body.select("div[class=field field-name-body field-type-text-with-summary field-label-hidden]")
                        .first()?.getPlainText() ?: ""
                val (authorId, authorName) = body.select("a[class=username]").first()?.let {
                    it.attr("href").let { href -> href.substring(href.lastIndexOf("/") + 1) } to
                            it.text()
                } ?: error("empty author")
                val authorPic =
                    "https:" + body.getElementsByClass("user-picture").first()!!.select("img")
                        .attr("src")
                val watchs = body.getElementsByClass("node-views").first()!!.text().trim()

                Response.success(
                    ImageDetail(
                        id = imageId,
                        title = title,
                        imageLinks = imageLinks,
                        authorId = authorId,
                        authorName = authorName,
                        authorProfilePic = authorPic,
                        watchs = watchs,
                        description = description
                    )
                )
            } catch (exception: Exception) {
                exception.printStackTrace()
                Response.failed(exception.javaClass.name)
            }
        }

    suspend fun getVideoPageDetail(session: Session, videoId: String): Response<VideoDetail> =
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "getVideoPageDetail: Start load video detail (id:$videoId)")

                session.getToken()

                val request = Request.Builder()
                    .url("https://ecchi.iwara.tv/videos/$videoId?language=zh-hans")
                    .get()
                    .build()
                val response = okHttpClient.newCall(request).await()
                require(response.isSuccessful)
                val responseStr = response.body?.string() ?: error("empty body")
                val body = Jsoup.parse(responseStr)

                if (body.select("section[id=content]").select("div[class=content]").text()
                        .contains("has chosen to restrict this video to users on their friends")
                ) {
                    return@withContext Response.success(VideoDetail.PRIVATE)
                }

                if (body.title().trim() == "Iwara") {
                    return@withContext Response.success(VideoDetail.DELETED)
                }

                val nid = try {
                    responseStr.let {
                        it.substring(
                            it.indexOf("\"nid\":") + 6,
                            it.indexOf(',', it.indexOf("\"nid\":") + 6)
                        ).toInt()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.i(TAG, "getVideoPageDetail: Failed to parse video nid")
                    0
                }
                Log.i(TAG, "getVideoPageDetail: NID = $nid")

                val title = body.getElementsByClass("title").first()?.text() ?: error("empty title")
                val viewDiv =
                    body.getElementsByClass("node-views").first()?.text()!!.trim().split(" ")
                val likes = viewDiv[0]
                val watchs = viewDiv[1]

                // 解析出上传日期
                val postDate = body
                    .select("div[class=submitted]")
                    .first()
                    ?.textNodes()
                    ?.first { it.text().contains("-") }
                    ?.text()
                    ?.split(" ")
                    ?.filter { it.matches(Regex(".*[0-9]+.*")) }
                    ?.joinToString(separator = " ")
                    ?: "null"

                // 视频描述
                val description =
                    body.select("div[class=field field-name-body field-type-text-with-summary field-label-hidden]")
                        .first()?.getPlainText() ?: ""
                val authorId = body.select("a[class=username]").first()!!.attr("href")
                    .let { it.substring(it.lastIndexOf("/") + 1) }
                val authorName = body.getElementsByClass("username").first()!!.text().trim()
                val authorPic =
                    "https:" + body.getElementsByClass("user-picture").first()!!.select("img")
                        .attr("src")

                // 更多视频
                val moreVideo = body
                    .select("div[id=block-views-videos-block-1]")
                    .select("div[class=view-content]")
                    .select("div[id~=^node-[A-Za-z0-9]+\$]")
                    .filter {
                        it.select("a").first() != null
                    }
                    .map {
                        val id = it
                            .select("a")
                            .first()
                            ?.attr("href")
                            ?.let { str ->
                                str.substring(str.lastIndexOf("/") + 1)
                            }
                        val title = it.select("img").first()?.attr("title")
                        val pic = "https:" + it.select("img").first()?.attr("src")
                        val likes =
                            it.select("div[class=right-icon likes-icon]").first()?.text() ?: "?"
                        val watchs =
                            it.select("div[class=left-icon likes-icon]").first()?.text() ?: "?"
                        MoreVideo(
                            id = id ?: "",
                            title = title ?: "",
                            pic = pic,
                            likes = likes,
                            watchs = watchs
                        )
                    }

                // 相似视频
                val recommendVideo = body
                    .select("div[id=block-views-search-block-1]")
                    .select("div[class=view-content]")
                    .select("div[id~=^node-[A-Za-z0-9]+\$]")
                    .filter {
                        it.select("a").first() != null
                    }
                    .map {
                        val id = it.select("a").first()!!.attr("href").let { str ->
                            str.substring(str.lastIndexOf("/") + 1)
                        }
                        val title = it.select("img").first()!!.attr("title")
                        val pic = "https:" + it.select("img").first()!!.attr("src")
                        val likes =
                            it.select("div[class=right-icon likes-icon]").first()?.text() ?: "0"
                        val watchs = it.select("div[class=left-icon likes-icon]").first()!!.text()
                        MoreVideo(
                            id = id,
                            title = title,
                            pic = pic,
                            likes = likes,
                            watchs = watchs
                        )
                    }

                val preview = "https:" + body.select("video[id=video-player]")
                    .first()
                    ?.attr("poster")

                // 收藏
                val likeFlag = body.select("a[href~=^/flag/.+/like/.+\$]").first()
                val isLike = likeFlag?.attr("href")?.startsWith("/flag/unflag/")
                val likeLink = URLDecoder.decode(
                    likeFlag?.attr("href").let { it?.substring(it.indexOf("/like/") + 6) },
                    "UTF-8"
                )

                println("Link = $likeLink")

                // 关注UP主
                val followFlag = body.select("a[href~=^/flag/.+/follow/.+\$\$]").first()
                val isFollow = followFlag?.attr("href")?.startsWith("/flag/unflag/")
                val followLink =
                    followFlag?.attr("href").let { it?.substring(it.indexOf("/follow/") + 8) }

                // 评论数量
                val comments =
                    (body.select("div[id=comments]").select("h2[class=title]").first()?.text()
                        .also { println(it) }
                        ?: " 0 评论 ").trim().replace("[^0-9]".toRegex(), "").toInt()

                // 评论
                val headElement = body.head().html()
                val startIndex = headElement.indexOf("key\":\"") + 6
                val endIndex = headElement.indexOf("\"", startIndex)
                val antiBotKey = headElement.substring(startIndex until endIndex)
                val form = body.select("form[class=comment-form antibot]").first()
                val formBuildId = form
                    ?.select("input[name=form_build_id]")
                    ?.attr("value")
                    ?: error("form_build_id not found")
                val formToken = form
                    .select("input[name=form_token]")
                    .attr("value")
                    ?: error("failed to get form_token")
                val formId = form
                    .select("input[name=form_id]")
                    .attr("value")
                    ?: error("failed to get form_id")
                val honeypotTime = form
                    .select("input[name=honeypot_time]")
                    .attr("value")
                    ?: error("failed to get honeypot_time")

                Log.i(TAG, "getVideoPageDetail: Result(title=$title, author=$authorName)")
                Log.i(TAG, "getVideoPageDetail: Like: $isLike LikeAPI: $likeLink")
                Log.i(TAG, "getVideoPageDetail: Follow: $isFollow FollowAPI: $followLink")
                Log.i(TAG, "getVideoPageDetail: Preview: $preview")

                Response.success(
                    VideoDetail(
                        id = videoId,
                        nid = nid,
                        title = title,
                        likes = likes,
                        watchs = watchs,
                        postDate = postDate,
                        description = description,
                        authorPic = authorPic,
                        authorName = authorName,
                        authorId = authorId,
                        comments = comments,
                        moreVideo = moreVideo,
                        recommendVideo = recommendVideo,
                        preview = preview,

                        isLike = isLike ?: false,
                        likeLink = likeLink,

                        follow = isFollow ?: false,
                        followLink = followLink ?: "",

                        commentPostParam = CommentPostParam(
                            antiBotKey = antiBotKey,
                            formId = formId,
                            formToken = formToken,
                            honeypotTime = honeypotTime,
                            formBuildId = formBuildId
                        )
                    )
                )
            } catch (exception: Exception) {
                exception.printStackTrace()
                
                Log.i(TAG, "getVideoPageDetail: Failed to load video detail")
                Response.failed(exception.javaClass.name)
            }
        }

    suspend fun like(
        session: Session,
        like: Boolean,
        likeLink: String
    ): Response<LikeResponse> =
        withContext(Dispatchers.IO) {
            try {
                session.getToken()

                val request = Request.Builder()
                    .url("https://ecchi.iwara.tv/flag/${if (like) "flag" else "unflag"}/like/$likeLink")
                    .post(FormBody.Builder().add("js", "true").build())
                    .build()
                val response = okHttpClient.newCall(request).await()
                require(response.isSuccessful)
                val likeResponse = gson.fromJson(
                    response.body?.string() ?: error("empty response"),
                    LikeResponse::class.java
                )
                Response.success(likeResponse)
            } catch (e: Exception) {
                e.printStackTrace()
                
                Response.failed(e.javaClass.name)
            }
        }

    suspend fun follow(
        session: Session,
        follow: Boolean,
        followLink: String
    ): Response<FollowResponse> = withContext(Dispatchers.IO) {
        try {
            session.getToken()

            val request = Request.Builder()
                .url("https://ecchi.iwara.tv/flag/${if (follow) "flag" else "unflag"}/follow/$followLink")
                .post(FormBody.Builder().add("js", "true").build())
                .build()
            val response = okHttpClient.newCall(request).await()
            require(response.isSuccessful)
            val followResponse = gson.fromJson(
                response.body?.string() ?: error("empty response"),
                FollowResponse::class.java
            )
            Response.success(followResponse)
        } catch (e: Exception) {
            e.printStackTrace()
            
            Response.failed(e.javaClass.name)
        }
    }

    suspend fun getCommentList(
        session: Session,
        mediaType: MediaType,
        mediaId: String,
        page: Int
    ): Response<CommentList> = withContext(Dispatchers.IO) {
        try {
            session.getToken()

            Log.i(TAG, "getCommentList: Loading comments of: $mediaId (${mediaType.value})")

            val request = Request.Builder()
                .url("https://ecchi.iwara.tv/${mediaType.value}/$mediaId?page=$page")
                .get()
                .build()
            val response = okHttpClient.newCall(request).await()
            val body = Jsoup.parse(response.body?.string() ?: error("empty body"))
            val commentDocu = body.select("div[id=comments]").first()

            // ###########################################################################
            // 内部函数，用于递归解析评论
            fun parseAsComments(document: Element): List<Comment> {
                val commentList = ArrayList<Comment>()
                for (docu in document.children()) {
                    // 此条为评论
                    if (docu.`is`("div[class~=^comment .+\$]")) {
                        val authorId = docu.select("a[class=username]").first()!!.attr("href")
                            .let { it.substring(it.lastIndexOf("/") + 1) }
                        val nid =
                            docu.select("li[class~=^comment-reply[A-Za-z0-9 ]+\$]").select("a")
                                .attr("href").split("/").let {
                                    it[it.size - 2].toInt()
                                }
                        val commentId =
                            docu.select("li[class~=^comment-reply[A-Za-z0-9 ]+\$]").select("a")
                                .attr("href").split("/").last().toInt()
                        val authorName = docu.select("a[class=username]").first()?.text()
                            ?: error("empty author name")
                        val authorPic =
                            "https:" + docu.select("div[class=user-picture]").first()
                                ?.select("img")?.first()?.attr("src")
                        val posterTypeValue = docu.attr("class")
                        var posterType = CommentPosterType.NORMAL
                        if (posterTypeValue.contains("by-node-author")) {
                            posterType = CommentPosterType.OWNER
                        }
                        if (posterTypeValue.contains("by-viewer")) {
                            posterType = CommentPosterType.SELF
                        }
                        val isFromIwara4a = docu
                            .select("div[class=content]")
                            .select("abbr[title=iwara4a]")
                            .isNotEmpty()
                        val content = docu.select("div[class=content]").first()?.getPlainText()
                            ?: error("empty content")
                        val date = docu.select("div[class=submitted]").first()?.ownText()
                            ?: error("empty date")

                        val comment = Comment(
                            authorId = authorId,
                            authorName = authorName,
                            authorPic = authorPic,
                            posterType = posterType,
                            nid = nid,
                            commentId = commentId,
                            content = content,
                            date = date,
                            reply = emptyList(),
                            fromIwara4a = isFromIwara4a
                        )

                        // 有回复
                        if (docu.nextElementSibling() != null && docu.nextElementSibling()!!
                                .`is`("div[class=indented]")
                        ) {
                            val reply = docu.nextElementSibling()!!
                            // 递归解析
                            comment.reply = parseAsComments(reply)
                        }

                        commentList.add(comment)
                    }
                }
                return commentList
            }
            // ###########################################################################
            // (用JSOUP解析网页真痛苦)

            val total =
                (commentDocu!!.select("h2[class=title]").first()?.text() ?: " 0 评论 ").trim()
                    .replace("[^0-9]".toRegex(), "").toInt()
            val hasNext =
                commentDocu.select("ul[class=pager]").select("li[class=pager-next]").any()
            val comments = parseAsComments(commentDocu)

            val headElement = body.head().html()
            val startIndex = headElement.indexOf("key\":\"") + 6
            val endIndex = headElement.indexOf("\"", startIndex)
            val antiBotKey = headElement.substring(startIndex until endIndex)
            val form = body.select("form[class=comment-form antibot]").first()
            val formId = form!!.select("input[name=form_id]").attr("value")

            Log.i(
                TAG,
                "getCommentList: Comment Result(total: $total, hasNext: $hasNext, abk: $antiBotKey, formId: $formId)"
            )

            Response.success(
                CommentList(
                    total = total,
                    page = page,
                    hasNext = hasNext,
                    comments = comments
                )
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            Response.failed(ex.javaClass.name)
        }
    }

    suspend fun getMediaList(
        session: Session,
        mediaType: MediaType,
        page: Int,
        sort: SortType,
        filter: Set<String>
    ): Response<MediaList> = withContext(Dispatchers.IO) {
        try {
            Log.i(
                TAG,
                "getMediaList: Start loading media list (type:${mediaType.value}, page: $page, sort: $sort)"
            )
            session.getToken()

            fun collectFilters(): String {
                var index = 0
                return filter.joinToString(separator = "&") {
                    "f[${index++}]=$it"
                }
            }

            val filters = collectFilters()

            val request = Request.Builder()
                .url("https://ecchi.iwara.tv/${mediaType.value}?page=$page&sort=${sort.value}" + if (filter.isNotEmpty()) "&${filters}" else "")
                .get()
                .build()
            val response = mediaHttpClient.newCall(request).await()
            require(response.isSuccessful)

            val body = Jsoup.parse(response.body?.string() ?: error("empty body")).body()
            val elements = body.select("div[id~=^node-[A-Za-z0-9]+\$]")

            val previewList: List<MediaPreview> = elements.map {
                val title = it.getElementsByClass("title").text()
                val author = it.getElementsByClass("username").text()
                val pic =
                    "https:" + (it.getElementsByClass("field-item even").select("img")
                        .first()?.attr("src")
                        ?: "//ecchi.iwara.tv/sites/all/themes/main/img/logo.png")
                val likes = it.getElementsByClass("right-icon").text()
                val watchs = it.getElementsByClass("left-icon").text()
                val link = it.select("a").first()!!.attr("href")
                val mediaId = link.substring(link.lastIndexOf("/") + 1)
                val type =
                    if (link.startsWith("/video")) MediaType.VIDEO else MediaType.IMAGE
                val private = it.select("div[class=private-video]").any()

                MediaPreview(
                    title = title,
                    author = author,
                    previewPic = pic,
                    likes = likes,
                    watchs = watchs,
                    mediaId = mediaId,
                    type = type,
                    private = private
                )
            }


            val hasNextPage =
                body.select("ul[class=pager]").first()?.select("li[class=pager-next]")?.any()
                    ?: false

            Response.success(
                MediaList(
                    currentPage = page,
                    hasNext = hasNextPage,
                    mediaList = previewList
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Response.failed(e.javaClass.name)
        }
    }

    suspend fun getUser(session: Session, userId: String): Response<UserData> =
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "getUser: Start load user data: $userId")
                session.getToken()

                val request = Request.Builder()
                    .url("https://ecchi.iwara.tv/users/$userId")
                    .get()
                    .build()
                val response = okHttpClient.newCall(request).await()
                require(response.isSuccessful)
                val body = Jsoup.parse(response.body?.string() ?: error("null body"))

                val nickname =
                    body.getElementsByClass("views-field views-field-name").first()!!.text()
                val profilePic =
                    "https:" + body.getElementsByClass("views-field views-field-picture")
                        .first()!!
                        .child(0)
                        .child(0)
                        .attr("src")
                val follow = body.select("div[id=block-mainblocks-user-connect]")
                    .select("span[class~=^flag-wrapper.*\$]").select("a").attr("href")
                    .startsWith("/flag/unflag/")
                val followLink = body.select("div[id=block-mainblocks-user-connect]")
                    .select("span[class~=^flag-wrapper.*\$]").select("a").attr("href")
                    .let { it.substring(it.indexOf("/follow/") + 8) }
                val joinDate =
                    body.select("div[class=views-field views-field-created]").first()!!
                        .child(1).text()
                val lastSeen =
                    body.select("div[class=views-field views-field-login]").first()!!.child(1)
                        .text()
                val about =
                    body.select("div[class=views-field views-field-field-about]").first()
                        ?.text() ?: ""

                val userIdOnMedia = body.select("div[id=block-mainblocks-user-connect]")
                    .select("ul[class=list-unstyled]").select("a").first()!!.attr("href").let {
                        it.substring(it.indexOf("/new?user=") + 10)
                    }

                val friendState = body
                    .select("div[id=block-mainblocks-user-connect]")
                    .select("ul")
                    .first()!!
                    .select("li")[2]
                    ?.text()
                    ?.let {
                        when {
                            it.contains("pending") -> UserFriendState.PENDING
                            it.equals("Friend", true) -> UserFriendState.NOT
                            else -> UserFriendState.ALREADY
                        }
                    } ?: UserFriendState.NOT

                val id = body
                    .select("div[id=block-mainblocks-user-connect]")
                    .select("span[class~=^flag-wrapper.*\$]").select("a").attr("href")
                    .let { it.substring(it.indexOf("/follow/") + 8) }
                    .let {
                        it.substring(0 until it.indexOf("?"))
                    }.toInt()

                Log.i(TAG, "getUser: Loaded UserData(user: $nickname, id = $id) - $userIdOnMedia")

                // 评论
                val headElement = body.head().html()
                val startIndex = headElement.indexOf("key\":\"") + 6
                val endIndex = headElement.indexOf("\"", startIndex)
                val antiBotKey = headElement.substring(startIndex until endIndex)
                val form =
                    body.select("form[class=comment-form antibot]").first() ?: error("empty form")
                val formBuildId = form.select("input[name=form_build_id]").attr("value")
                val formToken = form.select("input[name=form_token]").attr("value")
                val formId = form.select("input[name=form_id]").attr("value")
                val honeypotTime = form.select("input[name=honeypot_time]").attr("value")
                val commentId = headElement.substringAfter("\"action\":\"\\/comment\\/reply\\/")
                    .substringBefore("\"").toInt()

                Log.i(
                    TAG,
                    "getUser: Loaded CommentForm(id: $commentId | form: $antiBotKey, $formBuildId, $formToken, $formId, $honeypotTime)"
                )

                Response.success(
                    UserData(
                        userId = userId,
                        username = nickname,
                        userIdMedia = userIdOnMedia,
                        follow = follow,
                        followLink = followLink,
                        friend = friendState,
                        id = id,
                        pic = profilePic,
                        joinDate = joinDate,
                        lastSeen = lastSeen,
                        about = about,
                        commentId = commentId,
                        commentPostParam = CommentPostParam(
                            antiBotKey = antiBotKey,
                            formBuildId = formBuildId,
                            formToken = formToken,
                            formId = formId,
                            honeypotTime = honeypotTime
                        )
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Response.failed(e.javaClass.name)
            }
        }

    suspend fun getUserMediaList(
        session: Session,
        userIdOnVideo: String,
        mediaType: MediaType,
        page: Int
    ): Response<MediaList> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "getUserVideoList: $userIdOnVideo // $page")

            session.getToken()

            val request = Request.Builder()
                .url("https://ecchi.iwara.tv/users/$userIdOnVideo/${mediaType.value}?page=$page")
                .get()
                .build()

            val response = mediaHttpClient.newCall(request).await()
            require(response.isSuccessful)

            val body = Jsoup.parse(response.body?.string() ?: error("empty body")).body()
            val elements = body.select("div[id~=^node-[A-Za-z0-9]+\$]")

            val previewList: List<MediaPreview> = elements.map {
                val title = it.getElementsByClass("title").text()
                val author = it.getElementsByClass("username").text()
                val pic =
                    "https:" + (it.getElementsByClass("field-item even").select("img")
                        .first()?.attr("src")
                        ?: "//ecchi.iwara.tv/sites/all/themes/main/img/logo.png")
                val likes = it.getElementsByClass("right-icon").text()
                val watchs = it.getElementsByClass("left-icon").text()
                val link = it.select("a").first()!!.attr("href")
                val mediaId = link.substring(link.lastIndexOf("/") + 1)
                val type =
                    if (link.startsWith("/video")) MediaType.VIDEO else MediaType.IMAGE

                MediaPreview(
                    title = title,
                    author = author,
                    previewPic = pic,
                    likes = likes,
                    watchs = watchs,
                    mediaId = mediaId,
                    type = type
                )
            }


            val hasNextPage =
                body.select("ul[class=pager]").first()?.select("li[class=pager-next]")?.any()
                    ?: false

            Response.success(
                MediaList(
                    currentPage = page,
                    hasNext = hasNextPage,
                    mediaList = previewList
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            
            Response.failed(e.javaClass.simpleName)
        }
    }

    suspend fun getUserPageComment(
        session: Session,
        userId: String,
        @IntRange(from = 0) page: Int
    ): Response<CommentList> = withContext(Dispatchers.IO) {
        try {
            session.getToken()

            Log.i(TAG, "getUserPageComment: user = $userId, page = $page")

            val request = Request.Builder()
                .url("https://ecchi.iwara.tv/users/$userId?page=$page")
                .get()
                .build()

            val response = okHttpClient.newCall(request).await()
            val body = Jsoup.parse(response.body?.string() ?: error("empty body"))
            val commentDocu = body.select("div[id=comments]").first() ?: error("empty comment")

            // ###########################################################################
            // 内部函数，用于递归解析评论
            fun parseAsComments(document: Element): List<Comment> {
                val commentList = ArrayList<Comment>()
                for (docu in document.children()) {
                    // 此条为评论
                    if (docu.`is`("div[class~=^comment .+\$]")) {
                        val authorId = docu.select("a[class~=^username.*\$]").first()!!.attr("href")
                            .let { it.substring(it.lastIndexOf("/") + 1) }

                        val nid =
                            docu.select("li[class~=^comment-reply[A-Za-z0-9 ]+\$]").select("a")
                                .attr("href").split("/").let {
                                    it[it.size - 2].toInt()
                                }
                        val commentId =
                            docu.select("li[class~=^comment-reply[A-Za-z0-9 ]+\$]").select("a")
                                .attr("href").split("/").last().toInt()
                        val authorName = docu.select("a[class~=^username.*\$]").first()!!.text()
                        val authorPic =
                            "https:" + docu.select("div[class=user-picture]")
                                .first()
                                ?.select("img")
                                ?.first()
                                ?.attr("src")
                        val posterTypeValue = docu.attr("class")
                        var posterType = CommentPosterType.NORMAL
                        if (posterTypeValue.contains("by-node-author")) {
                            posterType = CommentPosterType.OWNER
                        }
                        if (posterTypeValue.contains("by-viewer")) {
                            posterType = CommentPosterType.SELF
                        }
                        val content = docu.select("div[class=content]").first()!!.text()
                        val isFromIwara4a = docu.select("div[class=content]")
                            .select("abbr[title=iwara4a]").isNotEmpty()
                        val date = docu.select("div[class=submitted]").first()!!.ownText()

                        val comment = Comment(
                            authorId = authorId,
                            authorName = authorName,
                            authorPic = authorPic,
                            posterType = posterType,
                            nid = nid,
                            commentId = commentId,
                            content = content,
                            date = date,
                            reply = emptyList(),
                            fromIwara4a = isFromIwara4a
                        )

                        // 有回复
                        if (docu.nextElementSibling() != null && docu.nextElementSibling()
                            !!.`is`("div[class=indented]")
                        ) {
                            val reply = docu.nextElementSibling()
                            // 递归解析
                            comment.reply = parseAsComments(reply!!)
                        }

                        commentList.add(comment)
                    }
                }
                return commentList
            }
            // ###########################################################################
            // (用JSOUP解析网页真痛苦)

            val total =
                (commentDocu.select("h2[class=title]").first()?.text() ?: " 0 评论 ").trim()
                    .split(" ")[0].toInt()
            val hasNext =
                commentDocu.select("ul[class=pager]").select("li[class=pager-next]").any()
            val comments = parseAsComments(commentDocu)

            val headElement = body.head().html()
            val startIndex = headElement.indexOf("key\":\"") + 6
            val endIndex = headElement.indexOf("\"", startIndex)
            val antiBotKey = headElement.substring(startIndex until endIndex)
            val form =
                body.select("form[class=comment-form antibot]").first() ?: error("empty form")
            val formBuildId = form.select("input[name=form_build_id]").attr("value")
            val formToken = form.select("input[name=form_token]").attr("value")
            val formId = form.select("input[name=form_id]").attr("value")
            val honeypotTime = form.select("input[name=honeypot_time]").attr("value")

            Log.i(TAG, "getUserComment: Comment Result(total: $total, hasNext: $hasNext)")

            Response.success(
                CommentList(
                    total = total,
                    page = page,
                    hasNext = hasNext,
                    comments = comments,
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            
            Response.failed(e.javaClass.simpleName)
        }
    }

    suspend fun search(
        session: Session,
        query: String,
        page: Int,
        sort: SortType,
        filter: Set<String>
    ): Response<MediaList> = withContext(Dispatchers.IO) {
        try {
            Log.i(
                TAG,
                "search: Start searching (query=$query, page=$page, sort=${sort.name})"
            )
            session.getToken()

            fun collectFilters(): String {
                var index = 0
                return filter.joinToString(separator = "&") {
                    "f[${index++}]=$it"
                }
            }

            val filters = collectFilters()

            val request = Request.Builder()
                .url("https://ecchi.iwara.tv/search?query=$query&sort=${sort.value}&page=$page" + if (filter.isNotEmpty()) "&${filters}" else "")
                .get()
                .build()
            val response = okHttpClient.newCall(request).await()
            val body = Jsoup.parse(response.body?.string() ?: error("empty body")).body()


            val mediaList: List<MediaPreview> =
                body.select("div[class~=^views-column .+\$]").map {
                    val type = if (it.select("h3[class=title]")
                            .any()
                    ) MediaType.VIDEO else MediaType.IMAGE
                    val title = it.select("h3[class=title]").first()?.text()
                        ?: it.select("h1[class=title]").first()!!.text()
                    val author =
                        if (type == MediaType.VIDEO) it.select("div[class=submitted]")
                            .select("a").first()!!
                            .text() else it.select("div[class=submitted]").select("a")
                            .last()!!
                            .text()
                    val pic = "https:" + it.select("div[class=field-item even]").first()!!
                        .select("img").attr("src")
                    val videoInfo =
                        if (type == MediaType.VIDEO)
                            it.select("div[class=video-info]")
                                .first()!!.text()
                        else it
                            .select("div[class=node-views]")
                            .first()!!
                            .text()
                    val watchs =
                        if (type == MediaType.VIDEO) videoInfo.split(" ")[0] else videoInfo
                    val likes = if (type == MediaType.VIDEO) videoInfo.split(" ")[1] else ""

                    val link =
                        if (type == MediaType.VIDEO) {
                            it.select("h3[class=title]").first()!!.select("a").attr("href")
                        } else {
                            it.select("div[class=share-icons]")
                                .first()!!
                                .select("a[class=symbol]")
                                .first()!!
                                .attr("href")
                                .let {
                                    it.substring(it.lastIndexOf("%2F") + 3)
                                }
                        }
                    val id = link.substring(link.lastIndexOf("/") + 1)

                    MediaPreview(
                        title = title,
                        author = author,
                        previewPic = pic,
                        likes = likes,
                        watchs = watchs,
                        type = type,
                        mediaId = id
                    )
                }

            val hasNext =
                body.select("ul[class=pager]").select("li[class=pager-next]").any()

            Response.success(
                MediaList(
                    currentPage = page,
                    hasNext = hasNext,
                    mediaList = mediaList
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            
            Response.failed(e.javaClass.name)
        }
    }

    suspend fun getLikePage(session: Session, page: Int): Response<MediaList> =
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "getLikePage: $page")
                session.getToken()

                val request = Request.Builder()
                    .url("https://ecchi.iwara.tv/user/liked?page=$page")
                    .get()
                    .build()
                val response = okHttpClient.newCall(request).await()
                require(response.isSuccessful)
                val body = Jsoup.parse(response.body!!.string())
                val elements = body.select("div[id~=^node-[A-Za-z0-9]+\$]")

                val previewList: List<MediaPreview> = elements.map {
                    val title = it.getElementsByClass("title").text()
                    val author = it.getElementsByClass("username").text()
                    val pic =
                        "https:" + (it.getElementsByClass("field-item even").select("img")
                            .first()?.attr("src")
                            ?: "//ecchi.iwara.tv/sites/all/themes/main/img/logo.png")
                    val likes = it.getElementsByClass("right-icon").text()
                    val watchs = it.getElementsByClass("left-icon").text()
                    val link = it.select("a").first()!!.attr("href")
                    val mediaId = link.substring(link.lastIndexOf("/") + 1)
                    val type =
                        if (link.startsWith("/video")) MediaType.VIDEO else MediaType.IMAGE

                    MediaPreview(
                        title = title,
                        author = author,
                        previewPic = pic,
                        likes = likes,
                        watchs = watchs,
                        mediaId = mediaId,
                        type = type
                    )
                }


                val hasNextPage =
                    body.select("ul[class=pager]")
                        .first()
                        ?.select("li[class=pager-next]")
                        ?.any() ?: false

                Response.success(
                    MediaList(
                        currentPage = page,
                        hasNext = hasNextPage,
                        mediaList = previewList
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
                logError("Failed to get like list", e)
                
                Response.failed(e.javaClass.simpleName)
            }
        }

    suspend fun postComment(
        session: Session,
        nid: Int,
        commentId: Int?,
        content: String,
        commentPostParam: CommentPostParam
    ) {
        withContext(Dispatchers.IO) {
            try {
                session.getToken()

                Log.i(TAG, "postComment: $nid | $commentId | $content")

                val request = Request.Builder()
                    .url("https://ecchi.iwara.tv/comment/reply/$nid" + if (commentId != null) "/$commentId" else "")
                    .post(
                        FormBody.Builder()
                            .add("op", "添加评论")
                            .add("comment_body[und][0][value]", "$content[abbr=iwara4a][/abbr]")
                            .add("form_build_id", commentPostParam.formBuildId)
                            .add("form_token", commentPostParam.formToken)
                            .add("antibot_key", commentPostParam.antiBotKey)
                            .add("form_id", commentPostParam.formId)
                            .add("honeypot_time", commentPostParam.honeypotTime)
                            .build()
                    )
                    .build()

                val response = okHttpClient.newCall(request).await()
                Log.i(TAG, "postComment: 已提交评论请求！(${response.code}})")
            } catch (e: Exception) {
                e.printStackTrace()
                
            }
        }
    }
}
