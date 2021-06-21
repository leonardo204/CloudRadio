package com.zerolive.cloudradio

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.*
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.ArrayList


var programTag = "CR_Program"

data class FavoriteItem(
    val filename: String,
    val title: String
)

data class YtbListItem(
    val filename: String,
    val title: String,
    val url: String,
    val random: String
)


@SuppressLint("StaticFieldLeak")
object Program : Fragment(), ListViewAdaptor.ListBtnClickListener {

    var bInitilized = false
    lateinit var mContext: Context
    var program_btnList = HashMap<String, ListViewItem>()

    // program 버튼을 누른 순서대로 favList 에 title 으로 저장
    // 먼저 들어간 program 이 1순위가 된다.
    var mCurFavList = ArrayList<String>()
    var mBackFavList = ArrayList<String>()

    lateinit var favListView: ListView
    lateinit var listViewAdaptor: ListViewAdaptor

    lateinit var btn_save_setting: Button
    lateinit var btn_reset_setting: Button

    var DEFAULT_FILE_PATH: String? = null
    var FAVORITE_CHANNEL_JSON = "savedFavoriteChannels.json"

    var mNumOfPlsFiles = 0

    var prefixFavoriteText = "< 즐겨찾기 추가! > "

    lateinit var popupMenu: PopupMenu

    override fun onListBtnClick(position: Int, view: View?) {
        var listViewitem = listViewAdaptor.getItem(position) as ListViewItem
        CRLog.d("onListBtnClick(idx: ${position}): ${listViewitem.defaultText}")
        popupMenu = PopupMenu(mContext, view)
        var inflater = MenuInflater(mContext)
        inflater.inflate(R.menu.fav_delete_popup, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener ( object: PopupMenu.OnMenuItemClickListener {
            override fun onMenuItemClick(item: MenuItem?): Boolean {
                removePlayList(listViewitem)
                return false
            }
        })
        popupMenu.show()
    }

    private fun removePlayList(item: ListViewItem) {
        val title = RadioChannelResources.getTitleByDefaultText("ytbpls_" + item.defaultText)

        CRLog.d("removePlayList [ title: ${title} defaultText: ${item.defaultText} ]")
        var bRemoved = false

        if ( mCurFavList.contains(title) ) {
            CRLog.d("remove mCurFavList  [ ${title} ]")
            mCurFavList.remove(title)
            bRemoved = true
        }
        if ( mBackFavList.contains(title) ) {
            CRLog.d("remove mBackFavList  [ ${title} ]")
            mBackFavList.remove(title)
            bRemoved = true
        }
        // program_btnList 는 ytbpls_ 를 빼고 관리 -_-;;
        if ( program_btnList.containsKey(item.defaultText) ) {
            CRLog.d("remove program_btnList  [ ${item.defaultText} ]")
            program_btnList.remove(item.defaultText)
            bRemoved = true
        }

        if ( bRemoved ) {
            resetAllButtonText()

            for(i in mCurFavList.indices) {
                val title2 = mCurFavList.get(i)
                val filename = RadioChannelResources.getFilenameByTitle(title2)
                CRLog.d("update ready [ title: ${title2} filename: ${filename} ]")

                updateProgramButtonText(filename, true)
            }

            var iter = RadioChannelResources.channelList.iterator()
            while( iter.hasNext() ) {
                val item2 = iter.next()
                if (item2.title == title) {
                    CRLog.d("remove RadioChannelResources.channelList  [ ${title} ]")

                    synchronized(RadioChannelResources.mContext) {
                        RadioChannelResources.channelList.remove(item2)
                        RadioChannelResources.channelSize--
                    }
                    break
                }
            }

            saveAction()

            listViewAdaptor.removeItem(item.defaultText)
            listViewAdaptor.notifyDataSetChanged()

            // write json
            var list: List<YtbListItem> = listOf()
            val fileobj = File(OnAir.DEFAULT_FILE_PATH + "ytbpls.json")
            if ( fileobj.exists() && fileobj.canRead() ) {
                val ins: InputStream = fileobj.inputStream()
                val content = ins.readBytes().toString(Charset.defaultCharset())
                val items = Json.parseToJsonElement(content)
                for(i in items.jsonArray.indices) {
                    val tt = Json.parseToJsonElement(items.jsonArray[i].jsonObject["title"].toString()).toString().replace("\"", "")
                    val uu = Json.parseToJsonElement(items.jsonArray[i].jsonObject["url"].toString()).toString().replace("\"", "")
                    val rr = Json.parseToJsonElement(items.jsonArray[i].jsonObject["random"].toString()).toString().replace("\"","")
                    if ( tt.contains(title) || tt.contains(item.defaultText) ) {
                        CRLog.d("skip remove item - title: ${tt}")
                        continue
                    }
                    CRLog.d("save item - title: ${tt}, random: ${rr}, url: ${uu}")
                    var oriItem = YtbListItem(tt+".json", tt, uu, rr)
                    list += oriItem
                }
            }

            val gson = GsonBuilder().disableHtmlEscaping().create()
            val listType: TypeToken<List<YtbListItem>> = object : TypeToken<List<YtbListItem>>() {}
            val arr = gson.toJson(list, listType.type)

            Log.d(programTag, "write json: ${arr}")
            WriteFile().execute(DEFAULT_FILE_PATH + "ytbpls.json", arr.toString())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        CRLog.d("Program onCreateView ${bInitilized}")

        val view: ViewGroup = inflater.inflate(R.layout.fragment_program, container, false) as ViewGroup

        if ( container != null ) {
            mContext = container.context
        }
        DEFAULT_FILE_PATH = mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/"

        btn_save_setting = view.findViewById(R.id.btn_save_setting)
        btn_reset_setting = view.findViewById(R.id.btn_reset_setting)
        btn_save_setting.isEnabled = false
        btn_save_setting.setOnClickListener { onRadioButton("SAVE", null) }
        btn_reset_setting.setOnClickListener { onRadioButton("RESET", null) }

        listViewAdaptor = ListViewAdaptor()
        listViewAdaptor.setListener(Program)
        favListView = view.findViewById(R.id.listview_favorite)
        favListView.adapter = listViewAdaptor
        favListView.setOnItemClickListener { parent, view, position, id ->
            val item = parent.getItemAtPosition(position) as ListViewItem
            CRLog.d("clicked item  ${item.defaultText}")
            onRadioButton(item.defaultText, item.type)
        }

        setHasOptionsMenu(true)

        initProgramButtons()

        bInitilized = true

        // Inflate the layout for this fragment
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        CRLog.d("Program onViewCreated")

    }

    fun resetProgramButtons() {
        CRLog.d("resetProgramButtons")

        listViewAdaptor.removeAll()

        program_btnList.clear()
    }

    private fun readPlsFileCount() {
        val fileobj = File(DEFAULT_FILE_PATH + "ytbpls.json")
        if ( fileobj.exists() && fileobj.canRead() ) {
            val ins: InputStream = fileobj.inputStream()
            val content = ins.readBytes().toString(Charset.defaultCharset())
            CRLog.d("readPlsFileCount1(${content})")
            val items = Json.parseToJsonElement(content)
            mNumOfPlsFiles = items.jsonArray.size
        } else {
            mNumOfPlsFiles = 0
        }
        CRLog.d("readPlsFileCount: ${mNumOfPlsFiles}")
    }

    private fun getYtbPlsListFromFile(title: String, url: String, random: String): List<YtbListItem> {
        // add title
        var list: List<YtbListItem> = listOf()
        var oriItem = YtbListItem(title+".json", title, url, random)
        list += oriItem

        // add extra from json file
        val fileobj = File(DEFAULT_FILE_PATH + "ytbpls.json")
        if ( fileobj.exists() && fileobj.canRead() ) {
            val ins: InputStream = fileobj.inputStream()
            val content = ins.readBytes().toString(Charset.defaultCharset())
            val items = Json.parseToJsonElement(content)
            for(i in items.jsonArray.indices) {
                val ff = Json.parseToJsonElement(items.jsonArray[i].jsonObject["filename"].toString())
                val tt = Json.parseToJsonElement(items.jsonArray[i].jsonObject["title"].toString())
                val uu = Json.parseToJsonElement(items.jsonArray[i].jsonObject["url"].toString())
                val rr = Json.parseToJsonElement(items.jsonArray[i].jsonObject["random"].toString())

                if ( ff.toString().replace("\"","").equals(title + ".json") ) {
                    Log.d(programTag, "skip filename: ${ff.toString().replace("\"","")}")
                    continue
                }

                val item = YtbListItem(ff.toString().replace("\"",""),
                    tt.toString().replace("\"",""),
                    uu.toString().replace("\"",""),
                    rr.toString().replace("\"",""))
                list += item
            }
        }

        return list
    }

    // youtube playlist 로 추가한 프로그램
    fun addProgramButtons(title: String, url: String, random: String) {
        Log.d(programTag, "addProgramButtons: ${title} - random: ${random} - ${url}")

        if (program_btnList.containsKey(title)) {
            Log.d(programTag, "skip title: ${title}   reason. duplication")
            return
        }

        val prefix = "ytbpls_"
        var defaultText = title
        if ( defaultText.startsWith(prefix) ) {
            defaultText = title.substring(defaultText.indexOf(prefix) + prefix.length)
        }

        addFavoriteItem(MEDIATYPE.YOUTUBE_PLAYLIST, defaultText)

        val map = RadioCompletionMap(
            defaultText,
            title,
            RadioChannelResources.channelList.size,
            title,
            title,
            null,
            MEDIATYPE.YOUTUBE_PLAYLIST
        )
        synchronized(RadioChannelResources.mContext) {
            RadioChannelResources.channelList.add(map)
            CRLog.d( "channelSize ${RadioChannelResources.channelSize} -> ${RadioChannelResources.channelSize +1}")
            RadioChannelResources.channelSize++
        }
        updateProgramButtonText(RadioChannelResources.getTitleByFilename(title),  false)

        // write json
        val list = getYtbPlsListFromFile(title, url, random)
        val gson = GsonBuilder().disableHtmlEscaping().create()
        val listType: TypeToken<List<YtbListItem>> = object : TypeToken<List<YtbListItem>>() {}
        val arr = gson.toJson(list, listType.type)

        Log.d(programTag, "write json: ${arr}")
        WriteFile().execute(DEFAULT_FILE_PATH + "ytbpls.json", arr.toString())
    }

    fun updateProgramButtons() {
        CRLog.d("1 updateProgramButtons() btnList: ${program_btnList.size}/${RadioChannelResources.channelList.size}")

        if ( program_btnList.size < RadioChannelResources.channelList.size ) {
            var idx = program_btnList.size
            var max = RadioChannelResources.channelList.size-1
            while ( idx <= max ) {
                val filename = RadioChannelResources.channelList.get(idx).filename
                val title = RadioChannelResources.channelList.get(idx).title
                CRLog.d("make program buttons for [${idx}] title:${title}   default: ${RadioChannelResources.channelList.get(idx).defaultButtonText}   filename:${filename}")

                val prefix = "ytbpls_"
                var defaultText = RadioChannelResources.channelList.get(idx).defaultButtonText
                if ( defaultText.startsWith(prefix) ) {
                    defaultText = defaultText.substring(defaultText.indexOf(prefix) + prefix.length)
                }

                addFavoriteItem(RadioChannelResources.channelList.get(idx).mediaType, defaultText)

                updateProgramButtonText(RadioChannelResources.channelList.get(idx).filename,   false)
                idx++
            }
        }
        if ( mCurFavList.size > 0 ) {
            for(i in mCurFavList.indices) {
                updateProgramButtonText( RadioChannelResources.getFilenameByTitle(mCurFavList.get(i)),  true)
            }
        }
        CRLog.d("2 updateProgramButtons() btnList: " + program_btnList.size)
    }

    private fun initProgramButtons() {
        listViewAdaptor.removeAll()
        program_btnList.clear()

        CRLog.d("initProgramButtons channel size: ${RadioChannelResources.channelList.size}")
        for(i in RadioChannelResources.channelList.indices) {
            val title = RadioChannelResources.channelList.get(i).title
            CRLog.d("make program buttons for [${i}] ${title}")

            val prefix = "ytbpls_"
            var defaultText = RadioChannelResources.channelList.get(i).defaultButtonText
            if ( defaultText.startsWith(prefix) ) {
                defaultText = title.substring(defaultText.indexOf(prefix) + prefix.length)
            }

            addFavoriteItem(RadioChannelResources.channelList.get(i).mediaType, defaultText)

            updateProgramButtonText(RadioChannelResources.channelList.get(i).filename,   false)
        }

        if ( mCurFavList.size > 0 ) {
            for(i in mCurFavList.indices) {
                updateProgramButtonText( RadioChannelResources.getFilenameByTitle(mCurFavList.get(i)),  true)
            }
        }
        CRLog.d("initProgramButtons() btnList: " + program_btnList.size)
    }

    // 라디오 버튼 누르면 모두 여기서 처리
    // 선호채널 상태
    //   - 선호채널 리스트 제거
    //   - 색상 표시 리셋
    // 선호채널 상태가 아니라면
    //   - 선호채널 리스트 등록
    //   - 선호채널 색상 표시
    fun onRadioButton(defaultText: String, type: String?) {
        CRLog.d("onRadioButton type:${type}  text:${defaultText}")
        var text = defaultText
        var title = RadioChannelResources.getTitleByDefaultText(text)
        var filename = RadioChannelResources.getFilenameByTitle(title)

        when(text) {
            "SAVE" -> saveAction()
            "RESET" -> {
                btn_save_setting.isEnabled = true
                resetAction()
            }
            else -> {
                type?.let {
                    if ( type.equals(MEDIATYPE.YOUTUBE_PLAYLIST.toString()) ) {
                        text = "ytbpls_" + defaultText
                    }
                    title = RadioChannelResources.getTitleByDefaultText(text)
                    filename = RadioChannelResources.getFilenameByTitle(title)
                }

                if ( mCurFavList.contains(title) ) {
                    CRLog.d("remove favList: ${title}")
                    mCurFavList.remove(title)
                    btn_save_setting.isEnabled = !isSameFavoriteList()
                    updateProgramButtonText(filename,   false)
                } else {
                    CRLog.d("add favList: ${title}")
                    mCurFavList.add(title)
                    btn_save_setting.isEnabled = !isSameFavoriteList()
                    updateProgramButtonText(filename,  true)
                }
            }
        }
    }

    private fun dumpFavList() {
        val iter = mCurFavList.iterator()
        var idx = 0
        CRLog.d(" - dumpFavList - ")
        while( iter.hasNext() ) {
            val title = iter.next()
            CRLog.d("[${idx++}] ${title}")
        }
        CRLog.d(" ")
    }

    private fun cloneFavroiteList() {
        mBackFavList.clear()
        for(i in mCurFavList.indices) {
            val title = mCurFavList.get(i)
            mBackFavList.add(title)
        }
        CRLog.d("doBackupFavroiteList size: ${mBackFavList.size}")
    }

    private fun isSameFavoriteList(): Boolean {
        CRLog.d("isSameFavoriteList backsize: ${mBackFavList.size} - curSize: ${mCurFavList.size}")
        if ( mBackFavList.size != mCurFavList.size ) {
            CRLog.d("isSameFavoriteList : false")
            return false
        } else {
            if ( mBackFavList.containsAll(mCurFavList) && mCurFavList.containsAll(mBackFavList) ) {
                CRLog.d("isSameFavoriteList : true")
                return true
            }
        }
        CRLog.d("isSameFavoriteList : false")
        return false
    }

    private fun saveAction() {
        if ( mCurFavList.size > 0 ) {
            dumpFavList()
            OnAir.updateOnAirPrograms(mCurFavList)
            saveFavList()
            cloneFavroiteList()
        } else {
            resetAction()
        }
        btn_save_setting.isEnabled = false
    }

    fun resetAction() {
        resetAllButtonText()
        OnAir.resetPrograms()
        mCurFavList.clear()
        mBackFavList.clear()
        dumpFavList()
    }

    private fun addFavoriteItem(type: MEDIATYPE, title: String) {
        CRLog.d("addFavoriteItem. title: ${title}")
        if ( !program_btnList.containsKey(title) ) {
            var ic_type: Drawable? = null
            var ic_delete: Drawable? = null
            when(type) {
                MEDIATYPE.UNKNOWN,
                MEDIATYPE.RADIO -> ic_type = ResourcesCompat.getDrawable(resources, R.drawable.ic_fav_radio, null)!!
                MEDIATYPE.YOUTUBE_LIVE -> ic_type = ResourcesCompat.getDrawable(resources, R.drawable.ic_fav_live, null)!!
                MEDIATYPE.YOUTUBE_NORMAL -> ic_type = ResourcesCompat.getDrawable(resources, R.drawable.ic_fav_youtube, null)!!
                MEDIATYPE.YOUTUBE_PLAYLIST -> {
                    ic_type = ResourcesCompat.getDrawable(resources, R.drawable.ic_fav_playlist, null)!!
                    ic_delete = ResourcesCompat.getDrawable(resources, R.drawable.ic_fav_delete, null)!!
                }
            }

            val item = ListViewItem(null, type.toString(), ic_type, title, ic_delete)
            program_btnList.put(title, item)
            listViewAdaptor.addItem(item)
            listViewAdaptor.notifyDataSetChanged()
        }
    }

    fun resetAllButtonText() {
        CRLog.d("resetAllButtonText()")
        val iter = program_btnList.iterator()
        while( iter.hasNext() ) {
            val obj = iter.next()
            val title = obj.key
            CRLog.d("resetAllButtonText() title: ${title}")
            var filename = RadioChannelResources.getFilenameByTitle(title)
            if ( filename.equals("Unknown Filename") ) {
                CRLog.d("resetAllButtonText() title: ${title} retry defaultText")
                filename = RadioChannelResources.getFilenameByDefaultText(title)
                if ( filename.equals("Unknown Filename") ) {
                    CRLog.d("resetAllButtonText() title: ${title} retry ytbpls")
                    filename = RadioChannelResources.getFilenameByTitle("ytbpls_"+title)
                }
            }
            updateProgramButtonText(filename,   false)
        }
    }

    private fun updateProgramButtonText(filename: String, bFavorite: Boolean) {
        CRLog.d( "updateProgramButtonText($bFavorite)  filename: ${filename} ")

        val prefix = "ytbpls_"
        var defaultText = RadioChannelResources.getDefaultTextByFilename(filename)
        if ( defaultText.startsWith(prefix) ) {
            defaultText = defaultText.substring(defaultText.indexOf(prefix) + prefix.length)
        }

        val ic_fav = ResourcesCompat.getDrawable(resources, R.drawable.ic_star, null)!!
        val item = listViewAdaptor.getItem(defaultText)

        item?.let {
            if (bFavorite) {
                it.iconFavorite = ic_fav
            } else {
                it.iconFavorite = null
            }
            CRLog.d( "updateProgramButtonText make title to: ${it.defaultText} ")
        }

        listViewAdaptor.notifyDataSetChanged()
    }

    private fun removeFavList() {
        CRLog.d("removeFavList()")
        var fileObj = File(DEFAULT_FILE_PATH + FAVORITE_CHANNEL_JSON)
        if ( fileObj.exists() ) {
            fileObj.delete()
            CRLog.d("removeFavList() success")
        }
    }

    fun saveFavList() {
        val iter = mCurFavList.iterator()
        var itemList: List<FavoriteItem> = listOf()
        var tempList = HashMap<String, String>()
        while( iter.hasNext() ) {
            val title = iter.next()
            val filename = RadioChannelResources.getFilenameByTitle(title)
            val item = FavoriteItem(filename, title)

            // check duplication
            if ( tempList.containsKey(title) || tempList.containsValue(filename) ) {
                CRLog.d("skip duplication: ${title} - ${filename}")
                continue
            }
            tempList.put(title, filename)
            itemList += item
        }
        CRLog.d("saveFavList itemList: ${itemList}")

        val gson = GsonBuilder().disableHtmlEscaping().create()
        val listType: TypeToken<List<FavoriteItem>> = object: TypeToken<List<FavoriteItem>>() {}

        val arr = gson.toJson(itemList, listType.type)
        CRLog.d("saveFavList json: ${arr}")

        WriteFile().execute(DEFAULT_FILE_PATH + FAVORITE_CHANNEL_JSON, arr.toString())
    }

    // initial time 에 OnAir 에서 즐겨찾기 파일 로딩 후 호출됨
    fun updatePrograms(list: ArrayList<String>) {
        CRLog.d("updatePrograms size: ${list.size}")
        val iter = list.iterator()
        while( iter.hasNext() ) {
            val title = iter.next()
            CRLog.d("updatePrograms ${title}")
            mCurFavList.add(title)
            updateProgramButtonText(RadioChannelResources.getFilenameByTitle(title),  true)
        }
        // favoriate list 파일 재작성
        saveFavList()

        // backup
        cloneFavroiteList()
    }

    @SuppressLint("StaticFieldLeak")
    internal class WriteFile() : AsyncTask<String?, String?, String?>() {

        override fun onPreExecute() {
            super.onPreExecute()
        }

        override fun doInBackground(vararg param: String?): String? {
            CRLog.d("WriteFile.doInBackground")
            var filename = param[0]
            var data = param[1]

            try {
                var fileObj = File(filename)
                if ( fileObj.exists() ) {
                    CRLog.d("remove previous ${fileObj}")
                    fileObj.delete()
                }

                if (data != null) {
                    CRLog.d("write ${data}")
                    fileObj.writeText(data, Charset.defaultCharset())
                }
            } catch (e: IOException) {
                CRLog.d("WriteFile Error: " + e.message)
            } catch (e: Exception) {
                CRLog.d("WriteFile Error: " + e.message)
            } finally {

            }
            CRLog.d("WriteFile.doInBackground end")

            return null
        }

        /**
         * Updating progress bar
         */
        override fun onProgressUpdate(vararg values: String?) {
        }

        /**
         * After completing background task Dismiss the progress dialog
         */
        override fun onPostExecute(file_url: String?) {
            CRLog.d("WriteFile finished: " + file_url)
        }
    }
}