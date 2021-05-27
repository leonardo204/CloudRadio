package com.zerolive.cloudradio

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.*
import java.nio.charset.Charset
import java.util.HashMap

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
object Program : Fragment() {

    var bInitilized = false
    lateinit var mContext: Context
    var program_btnList = HashMap<String, Button>()
    var ytbpls_btnList = HashMap<String, Button>()

    // program 버튼을 누른 순서대로 favList 에 title 으로 저장
    // 먼저 들어간 program 이 1순위가 된다.
    var mCurFavList = ArrayList<String>()
    var mBackFavList = ArrayList<String>()

    lateinit var btn_save_setting: Button
    lateinit var btn_reset_setting: Button

    lateinit var layout_programs: LinearLayout

    var DEFAULT_FILE_PATH: String? = null
    var FAVORITE_CHANNEL_JSON = "savedFavoriteChannels.json"

    var mNumOfPlsFiles = 0

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

        layout_programs = view.findViewById(R.id.layout_grid_programs)
        btn_save_setting = view.findViewById(R.id.btn_save_setting)
        btn_reset_setting = view.findViewById(R.id.btn_reset_setting)
        btn_save_setting.isEnabled = false
        btn_save_setting.setOnClickListener { onRadioButton("SAVE") }
        btn_reset_setting.setOnClickListener { onRadioButton("RESET") }

//        readPlsFileCount()

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
//        var iter = program_btnList.iterator()
//        while( iter.hasNext() ) {
//            var filename = iter.next()
//            CRLog.d( "remove: ${filename}")
//            var button = program_btnList.get(filename)
//            var parent = button?.parent as ViewGroup?
//            button?.let { parent?.removeView(it) }
//            program_btnList.remove(filename)
//        }
        layout_programs.removeAllViews()

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

        if ( program_btnList.containsKey(title) ) {
            Log.d(programTag, "skip title: ${title}   reason. duplication")
            return
        }

        val button = Button(mContext)
        button.setText(title)
        button.setOnClickListener { onRadioButton(title) }
        program_btnList.put(title, button)
        layout_programs.addView(button)
        val map = RadioCompletionMap(title, title, RadioChannelResources.channelList.size, title, title, null, MEDIATYPE.YOUTUBE_PLAYLIST)
        RadioChannelResources.channelList.add(map)
        RadioChannelResources.channelSize++
        updateProgramButtonText(title, title, true, false)

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
                CRLog.d("make program buttons for [${idx}] ${title} ${filename}")
                val button = Button(mContext)
                button.setText(RadioChannelResources.channelList.get(idx).defaultButtonText)
                button.setOnClickListener { onRadioButton(title) }
                program_btnList.put(title, button)
                layout_programs.addView(button)

                var message = RadioChannelResources.getDefaultTextByFilename(filename)
                updateProgramButtonText(filename, message, true, false)
                idx++
            }
        }
        if ( mCurFavList.size > 0 ) {
            for(i in mCurFavList.indices) {
                updateProgramButtonText(
                    RadioChannelResources.getFilenameByTitle(mCurFavList.get(i)), RadioChannelResources.getDefaultTextByTitle(
                        mCurFavList.get(i)), true, true)
            }
        }
        CRLog.d("2 updateProgramButtons() btnList: " + program_btnList.size)
    }

    private fun initProgramButtons() {
        layout_programs.removeAllViews()
        program_btnList.clear()

        CRLog.d("initProgramButtons channel size: ${RadioChannelResources.channelList.size}")
        for(i in RadioChannelResources.channelList.indices) {
            val title = RadioChannelResources.channelList.get(i).title
            CRLog.d("make program buttons for [${i}] ${title}")
            val button = Button(mContext)
            button.setText( RadioChannelResources.channelList.get(i).defaultButtonText )
            button.setOnClickListener { onRadioButton(title) }
            program_btnList.put(title, button)
//            var layoutParams: GridLayout.LayoutParams =  GridLayout.LayoutParams()
//            layoutParams.setGravity(Gravity.FILL_HORIZONTAL)
//            layout_grid_programs.addView(button, layoutParams)
            layout_programs.addView(button)

            val filename = RadioChannelResources.getFilenameByTitle(title)
            var message = RadioChannelResources.getDefaultTextByTitle(title)
            updateProgramButtonText(filename, message, true, false)
        }

        if ( mCurFavList.size > 0 ) {
            for(i in mCurFavList.indices) {
                updateProgramButtonText(
                    RadioChannelResources.getFilenameByTitle(mCurFavList.get(i)), RadioChannelResources.getDefaultTextByFilename(
                        mCurFavList.get(i)), true, true)
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
    fun onRadioButton(title: String) {
        when(title) {
            "SAVE" -> saveAction()
            "RESET" -> {
                btn_save_setting.isEnabled = true
                resetAction()
            }
            else -> {
                if ( mCurFavList.contains(title) ) {
                    CRLog.d("remove favList: ${title}")
                    mCurFavList.remove(title)
                    btn_save_setting.isEnabled = !isSameFavoriteList()
                    updateProgramButtonText(RadioChannelResources.getFilenameByTitle(title), RadioChannelResources.getDefaultTextByTitle(title), true, false)
                } else {
                    CRLog.d("add favList: ${title}")
                    mCurFavList.add(title)
                    btn_save_setting.isEnabled = !isSameFavoriteList()
                    updateProgramButtonText(RadioChannelResources.getFilenameByTitle(title), "즐겨찾기 추가, "+ RadioChannelResources.getDefaultTextByTitle(title), true, true)
                }
            }
        }
    }

    private fun dumpFavList() {
        val iter = mCurFavList.iterator()
        var idx = 0
        CRLog.d(" - dumpFavList - ")
        while( iter.hasNext() ) {
            var title = iter.next()
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
            removeFavList()
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

    fun resetAllButtonText() {
        CRLog.d("resetButtons()")
        val iter = program_btnList.iterator()
        while( iter.hasNext() ) {
            val obj = iter.next()
            val title = obj.key
            val message = RadioChannelResources.getDefaultTextByTitle(title)
            val filename = RadioChannelResources.getFilenameByTitle(title)
            updateProgramButtonText(filename, message, true, false)
        }
    }

    @SuppressLint("SetTextI18n")
    fun updateProgramButtonText(filename: String, text: String, enable: Boolean, bFavorite: Boolean) {
        //CRLog.d( "updateProgramButtonText $filename  $text  $enable")

        val iter = program_btnList.iterator()
        while( iter.hasNext() ) {
            val obj = iter.next()
            val title = obj.key
            //CRLog.d( "updateButtonText title: ${title} - ${filename}" )
            if ( RadioChannelResources.getFilenameByTitle(title).equals(filename) ) {
                val button = obj.value
                if (bFavorite) {
                    button.setBackgroundColor(Color.CYAN)
                } else {
                    button.setBackgroundColor(Color.WHITE)
                }

                button.isEnabled = enable
                button.setText(text)
                break
            }
        }
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
            updateProgramButtonText(RadioChannelResources.getFilenameByTitle(title), "즐겨찾기 추가, "+ RadioChannelResources.getDefaultTextByTitle(title), true, true)
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
                    CRLog.d("remove previous savedChannel.json")
                    fileObj.delete()
                }

                if (data != null) {
                    CRLog.d("write savedChannel.json")
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