package com.example.cloudradio

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
import java.io.*
import java.nio.charset.Charset
import java.util.HashMap

var programTag = "CR_Program"

data class FavoriteItem(val filename: String)


@SuppressLint("StaticFieldLeak")
object Program : Fragment() {

    var bInitilized = false
    lateinit var mContext: Context
    var program_btnList = HashMap<String, Button>()

    // program 버튼을 누른 순서대로 favList 에 filename 으로 저장
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
        Log.d(programTag, "Program onCreateView")

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
        Log.d(programTag, "Program onViewCreated")

    }

    fun updateProgramButtons() {
        var idx = program_btnList.size
        for(i in idx..(RadioChannelResources.channelList.size-1)) {
            val filename = RadioChannelResources.channelList.get(i).filename
            Log.d(programTag, "make program buttons for ${filename}")
            val button = Button(mContext)
            button.setText( RadioChannelResources.channelList.get(i).defaultButtonText )
            button.setOnClickListener { onRadioButton(filename) }
            program_btnList.put(filename, button)
            layout_programs.addView(button)

            var message = getDefaultTextByFilename(filename)
            updateButtonText(filename, message, true, false)
        }
        if ( favList.size > 0 ) {
            for(i in favList.indices) {
                updateButtonText(favList.get(i), getDefaultTextByFilename(favList.get(i)), true, true)
            }
        }
        Log.d(programTag, "updateProgramButtons() btnList: "+ program_btnList.size)
    }

    private fun initProgramButtons() {
        layout_programs.removeAllViews()

        Log.d(programTag, "initProgramButtons channel size: ${RadioChannelResources.channelList.size}")
        for(i in RadioChannelResources.channelList.indices) {
            val filename = RadioChannelResources.channelList.get(i).filename
            Log.d(programTag, "make program buttons for ${filename}")
            val button = Button(mContext)
            button.setText( RadioChannelResources.channelList.get(i).defaultButtonText )
            button.setOnClickListener { onRadioButton(filename) }
            program_btnList.put(filename, button)
//            var layoutParams: GridLayout.LayoutParams =  GridLayout.LayoutParams()
//            layoutParams.setGravity(Gravity.FILL_HORIZONTAL)
//            layout_grid_programs.addView(button, layoutParams)
            layout_programs.addView(button)

            var message = getDefaultTextByFilename(filename)
            updateButtonText(filename, message, true, false)
        }

        if ( favList.size > 0 ) {
            for(i in favList.indices) {
                updateButtonText(favList.get(i), getDefaultTextByFilename(favList.get(i)), true, true)
            }
        }
        Log.d(programTag, "initProgramButtons() btnList: "+ program_btnList.size)
    }

    // 라디오 버튼 누르면 모두 여기서 처리
    // 선호채널 상태
    //   - 선호채널 리스트 제거
    //   - 색상 표시 리셋
    // 선호채널 상태가 아니라면
    //   - 선호채널 리스트 등록
    //   - 선호채널 색상 표시
    fun onRadioButton(filename: String) {
        when(filename) {
            "SAVE" -> saveAction()
            "RESET" -> resetAction()
            else -> {
                if ( favList.contains(filename) ) {
                    favList.remove(filename)
                    updateButtonText(filename, getDefaultTextByFilename(filename), true, false)
                } else {
                    favList.add(filename)
                    updateButtonText(filename, "즐겨찾기 추가, "+getDefaultTextByFilename(filename), true, true)
                }
            }
        }
    }

    private fun saveAction() {
        if ( favList.size > 0 ) {
            OnAir.makePrograms(favList)
            saveFavList()
        }
    }

    fun resetAction() {
        resetAllButtonText()
        OnAir.resetPrograms()
        favList.clear()
    }

    fun resetAllButtonText() {
        Log.d(programTag, "resetButtons()")
        var iter = program_btnList.iterator()
        while( iter.hasNext() ) {
            var obj = iter.next()
            var message = getDefaultTextByFilename(obj.key)
            updateButtonText(obj.key, message, true, false)
        }
    }

    fun getDefaultTextByFilename(filename: String): String {

        for(i in RadioChannelResources.channelList.indices) {
//            Log.d(programTag, " RadioChannelResources.channelList.get(i).filename: ${RadioChannelResources.channelList.get(i).filename} - $filename")
            if ( RadioChannelResources.channelList.get(i).filename.equals(filename) ) {
                return RadioChannelResources.channelList.get(i).defaultButtonText
            }
        }
        return "Unknown Channel"
    }

    @SuppressLint("SetTextI18n")
    fun updateButtonText(filename: String, text: String, enable: Boolean, bFavorite: Boolean) {
        Log.d(programTag, "updateButtonText $filename  $text  $enable")

        var iter = program_btnList.iterator()
        while( iter.hasNext() ) {
            var obj = iter.next()
            //Log.d(programTag, "updateButtonText: "+obj.key )
            if ( obj.key.equals(filename) ) {
                var button = obj.value
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

    private fun saveFavList() {
        var iter = favList.iterator()
        var itemList: List<FavoriteItem> = listOf()
        while( iter.hasNext() ) {
            var filename = iter.next()
            var item = FavoriteItem(filename)
            itemList += item

        }
        Log.d(programTag, "itemList: ${itemList}")

        var gson = GsonBuilder().create()
        var listType: TypeToken<List<FavoriteItem>> = object: TypeToken<List<FavoriteItem>>() {}

        var arr = gson.toJson(itemList, listType.type)
        Log.d(programTag, "arr: ${arr}")

        WriteFile().execute(DEFAULT_FILE_PATH+FAVORITE_CHANNEL_JSON, arr.toString())
    }

    fun updatePrograms(list: ArrayList<String>) {
        Log.d(programTag, "updatePrograms size: ${list.size}")
        var iter = list.iterator()
        while( iter.hasNext() ) {
            var filename = iter.next()
            Log.d(programTag, "updatePrograms ${filename}")
            favList.add(filename)
            updateButtonText(filename, "즐겨찾기 추가, "+getDefaultTextByFilename(filename), true, true)
        }
    }

    @SuppressLint("StaticFieldLeak")
    internal class WriteFile() : AsyncTask<String?, String?, String?>() {

        override fun onPreExecute() {
            super.onPreExecute()
        }

        override fun doInBackground(vararg param: String?): String? {
            Log.d(programTag, "WriteFile.doInBackground")
            var filename = param[0]
            var data = param[1]

            try {
                var fileObj = File(filename)
                if ( fileObj.exists() ) {
                    Log.d(programTag, "remove previous savedChannel.json")
                    fileObj.delete()
                }

                if (data != null) {
                    Log.d(programTag, "write savedChannel.json")
                    fileObj.writeText(data, Charset.defaultCharset())
                }
            } catch (e: IOException) {
                Log.e(programTag,"WriteFile Error: "+ e.message)
            } catch (e: Exception) {
                Log.e(programTag,"WriteFile Error: "+ e.message)
            } finally {

            }
            Log.d(programTag, "WriteFile.doInBackground end")

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
            Log.d(programTag, "WriteFile finished: " + file_url)
        }
    }
}