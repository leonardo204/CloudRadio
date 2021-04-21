package com.zerolive.cloudradio

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.zerolive.cloudradio.R
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.*
import java.nio.charset.Charset
import java.util.HashMap

var programTag = "CR_Program"

data class FavoriteItem(
    val filename: String,
    val title: String
)


@SuppressLint("StaticFieldLeak")
object Program : Fragment() {

    var bInitilized = false
    lateinit var mContext: Context
    var program_btnList = HashMap<String, Button>()

    // program 버튼을 누른 순서대로 favList 에 title 으로 저장
    // 먼저 들어간 program 이 1순위가 된다.
    var favList = ArrayList<String>()

    lateinit var btn_save_setting: Button
    lateinit var btn_reset_setting: Button

    lateinit var layout_programs: LinearLayout

    var DEFAULT_FILE_PATH: String? = null
    var FAVORITE_CHANNEL_JSON = "savedFavoriteChannels.json"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        CRLog.d("Program onCreateView")

        if ( container != null ) {
            mContext = container.context
        }
        DEFAULT_FILE_PATH = mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/"

        var view: ViewGroup = inflater.inflate(R.layout.fragment_program, container, false) as ViewGroup

        layout_programs = view.findViewById(R.id.layout_grid_programs)
        btn_save_setting = view.findViewById(R.id.btn_save_setting)
        btn_reset_setting = view.findViewById(R.id.btn_reset_setting)
        btn_save_setting.setOnClickListener { onRadioButton("SAVE") }
        btn_reset_setting.setOnClickListener { onRadioButton("RESET") }

        bInitilized = true

        initProgramButtons()

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
        if ( favList.size > 0 ) {
            for(i in favList.indices) {
                updateProgramButtonText(
                    RadioChannelResources.getFilenameByTitle(favList.get(i)), RadioChannelResources.getDefaultTextByTitle(
                        favList.get(i)), true, true)
            }
        }
        CRLog.d("2 updateProgramButtons() btnList: " + program_btnList.size)
    }

    private fun initProgramButtons() {
        layout_programs.removeAllViews()

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

        if ( favList.size > 0 ) {
            for(i in favList.indices) {
                updateProgramButtonText(
                    RadioChannelResources.getFilenameByTitle(favList.get(i)), RadioChannelResources.getDefaultTextByFilename(
                        favList.get(i)), true, true)
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
            "RESET" -> resetAction()
            else -> {
                if ( favList.contains(title) ) {
                    CRLog.d("remove favList: ${title}")
                    favList.remove(title)
                    updateProgramButtonText(RadioChannelResources.getFilenameByTitle(title), RadioChannelResources.getDefaultTextByTitle(title), true, false)
                } else {
                    CRLog.d("add favList: ${title}")
                    favList.add(title)
                    updateProgramButtonText(RadioChannelResources.getFilenameByTitle(title), "즐겨찾기 추가, "+ RadioChannelResources.getDefaultTextByTitle(title), true, true)
                }
            }
        }
    }

    private fun dumpFavList() {
        val iter = favList.iterator()
        var idx = 0
        CRLog.d(" - dumpFavList - ")
        while( iter.hasNext() ) {
            var title = iter.next()
            CRLog.d("[${idx++}] ${title}")
        }
        CRLog.d(" ")
    }

    private fun saveAction() {
        if ( favList.size > 0 ) {
            dumpFavList()
            OnAir.makePrograms(favList)
            saveFavList()
        } else {
            removeFavList()
        }
    }

    fun resetAction() {
        resetAllButtonText()
        OnAir.resetPrograms()
        favList.clear()
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
            //CRLog.d( "updateButtonText: "+obj.key )
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

    private fun saveFavList() {
        val iter = favList.iterator()
        var itemList: List<FavoriteItem> = listOf()
        while( iter.hasNext() ) {
            val title = iter.next()
            val filename = RadioChannelResources.getFilenameByTitle(title)
            val item = FavoriteItem(filename, title)
            itemList += item

        }
        CRLog.d("saveFavList itemList: ${itemList}")

        val gson = GsonBuilder().create()
        val listType: TypeToken<List<FavoriteItem>> = object: TypeToken<List<FavoriteItem>>() {}

        val arr = gson.toJson(itemList, listType.type)
        CRLog.d("saveFavList json: ${arr}")

        WriteFile().execute(DEFAULT_FILE_PATH + FAVORITE_CHANNEL_JSON, arr.toString())
    }

    fun updatePrograms(list: ArrayList<String>) {
        CRLog.d("updatePrograms size: ${list.size}")
        val iter = list.iterator()
        while( iter.hasNext() ) {
            val title = iter.next()
            CRLog.d("updatePrograms ${title}")
            favList.add(title)
            updateProgramButtonText(RadioChannelResources.getFilenameByTitle(title), "즐겨찾기 추가, "+ RadioChannelResources.getDefaultTextByTitle(title), true, true)
        }
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