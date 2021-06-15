package com.zerolive.cloudradio

import android.os.AsyncTask
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URL

enum class AFN_STATIONS {
    OSAN {
        override fun getStationName(): String = "AFNP_OSN"
    },
    VOCIE {
        override fun getStationName(): String = "AFN_VCE"
    },
    JOE {
        override fun getStationName(): String = "AFN_JOEP"
    },
    LEGACY {
        override fun getStationName(): String = "AFN_LGYP"
    };
    abstract fun getStationName(): String
}

object AFNRadioResource : AsyncCallback{
    val afnTag = "CR_AFN_Resources"
    val APIURL = "https://playerservices.streamtheworld.com/api/livestream?transports=http,hls&version=1.8&station="

    fun init() {
        Log.d(onairTag, "AFNRadioResources init")
        val iter = AFN_STATIONS.values().iterator()
        while( iter.hasNext() ) {
            val station = iter.next().getStationName()
            val REQURL = APIURL + station

            // request
            CRLog.d("AFN REQ: ${REQURL}")
            GetAFNAddress(this).execute(station, REQURL)
        }
    }

    override fun onTaskDone(vararg string: String?) {

        val stationName = string[0]
        val ip = string[1]
        val mount = string[2]
        val mountSuffix = string[3]

//        CRLog.d("onTaskDone. stationName: ${stationName}  ip: ${ip}  mount: ${mount}  mountSuffix: ${mountSuffix}")

        val url = "https://" + ip + ":443/" + mount + mountSuffix

        CRLog.d("STREAM URL: ${url}")

        for(i in RadioChannelResources.channelList.indices ) {
//            CRLog.d(" title check - ${RadioChannelResources.channelList.get(i).filename}")
            if ( RadioChannelResources.channelList.get(i).filename.equals(stationName+".pls") ) {
                CRLog.d(" REPLACE for ${stationName} ")

                val map = RadioCompletionMap(
                    RadioChannelResources.channelList.get(i).defaultButtonText,
                    RadioChannelResources.channelList.get(i).title,
                    RadioChannelResources.channelList.get(i).id,
                    RadioChannelResources.channelList.get(i).filename,
                    RadioChannelResources.channelList.get(i).fileaddress,
                    url,
                    MEDIATYPE.RADIO
                )
                synchronized(RadioChannelResources.mContext) {
                    RadioChannelResources.channelList.removeAt(i)
                    RadioChannelResources.channelList.add(i, map)
                }
                break
            }
        }
    }

    class GetAFNAddress(context: AFNRadioResource) : AsyncTask<String, String, String>() {
        val callback: AsyncCallback = context

        override fun doInBackground(vararg param: String?): String? {

            val stationName = param[0]
            val apiurl = param[1]

            var bMount = false
            var bShoutcast_v1 = false
            var bShoutcast_v2 = false
            var bIp = false

            var ip: String = "unknown"
            var mount: String = "unknown"
            var mountSuffix: String = "unknown"

            Log.d(afnTag, "Req url: ${apiurl}")

            try {
                val url = URL(apiurl)
                val input = url.openStream()
                val parsers = XmlPullParserFactory.newInstance()
                val parser = parsers.newPullParser()
                parser.setInput(input, "UTF-8")

                while( parser.eventType != XmlPullParser.END_DOCUMENT ) {
                    //파싱한 데이터의 타입 변수를 저장한다. 시작태그, 텍스트태그, 종료태그를 구분한다.
                    var type = parser.getEventType()
                    if ( bMount && mount.equals("unknown") ) {
                        Log.d(afnTag, "mount: ${parser.text}")
                        bMount = false
                        mount = parser.text
                    } else if ( bShoutcast_v1 && mountSuffix.equals("unknown") ) {
                        Log.d(afnTag, "shoutcast-v1: ${parser.getAttributeName(0)}: ${parser.getAttributeValue(0)}  ${parser.getAttributeName(1)}: ${parser.getAttributeValue(1)}")
                        bShoutcast_v1 = false
                        if ( parser.getAttributeValue(0).equals("true") ) {
                            mountSuffix = parser.getAttributeValue(1)
                        }
                    } else if ( bShoutcast_v2 && mountSuffix.equals("unknown") ) {
                        Log.d(afnTag, "shoutcast-v2: ${parser.getAttributeName(0)}: ${parser.getAttributeValue(0)}  ${parser.getAttributeName(1)}: ${parser.getAttributeValue(1)}")
                        bShoutcast_v2 = false
                        if ( parser.getAttributeValue(0).equals("true") ) {
                            mountSuffix = parser.getAttributeValue(1)
                        }
                    } else if ( bIp && ip.equals("unknown") ) {
                        Log.d(afnTag, "ip: ${parser.text}")
                        bIp = false
                        ip = parser.text
                    }

                    if ( type == XmlPullParser.START_TAG ) {
                        when( parser.name ) {
                            "mount" -> bMount = true
                            "shoutcast-v1" -> bShoutcast_v1 = true
                            "shoutcast-v2" -> bShoutcast_v2 = true
                            "ip" -> bIp = true
                        }
                    }

                    if ( ip.equals("unknown") || mount.equals("unknown") || mountSuffix.equals("unknown") ) {
                        type = parser.next()
                    } else {
                        break
                    }
                }
                callback.onTaskDone(stationName, ip, mount, mountSuffix)

            } catch (e: Exception) {
                Log.d(afnTag,"GetAFNAddress Error: " + e.message)
            }

            return "success";
        }
    }
}